package android.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SizeF;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A view hierarchy inflated from a layout resource, plus a list of changes
 * applied after inflation (port of AOSP RemoteViews, scoped to one process).
 *
 * <p>There is no notification shade and no draw-instruction renderer.
 * {@code apply} inflates {@code mLayoutId} and runs the action list.
 * {@code setLightBackgroundLayoutId} is stored and not swapped in, because
 * that switch lives on the shade path. Intent {@code setRemoteAdapter}
 * overloads call {@link AbsListView#setRemoteViewsAdapter}, which does not
 * bind a service; {@link RemoteCollectionItems} are applied directly.
 *
 * <p>AOSP only inflates classes marked {@link RemoteView}. The VM does not
 * return runtime annotations, and our widgets are not annotated, so
 * {@link #onLoadClass} allows framework {@link View} classes by package
 * name. Application classes are rejected.
 */
public class RemoteViews implements Parcelable, LayoutInflater.Filter {
    private static final String LOG_TAG = "RemoteViews";

    public static final String EXTRA_CHECKED = "android.widget.extra.CHECKED";
    /** Bounds of shared elements, a Bundle of name to Rect. */
    public static final String EXTRA_SHARED_ELEMENT_BOUNDS = "android.view.extra.SHARED_ELEMENT_BOUNDS";

    public static final int MARGIN_LEFT = 0;
    public static final int MARGIN_TOP = 1;
    public static final int MARGIN_RIGHT = 2;
    public static final int MARGIN_BOTTOM = 3;
    public static final int MARGIN_START = 4;
    public static final int MARGIN_END = 5;

    /** Framework ids, so {@link View#setTagInternal} accepts them. Not resource ids. */
    private static final int TAG_LAYOUT_ID = 0x01ff0001;
    private static final int TAG_TEMPLATE = 0x01ff0002;

    private static final int MODE_NORMAL = 0;
    private static final int MODE_ORIENTATION = 1;
    private static final int MODE_SIZED = 2;
    private static final int MODE_DRAW = 3;

    private static final String REAPPLY_ERROR =
            "Attempting to re-apply RemoteViews to a view that is not the root of the RemoteViews layout";

    private static boolean sLoggedDraw;
    private static boolean sLoggedStylus;
    private static boolean sLoggedIntentAdapter;

    private String mPackage;
    private int mLayoutId;
    private int mViewId;
    private int mLightBackgroundLayoutId;
    private int mMode;
    private final ArrayList<Action> mActions = new ArrayList<Action>();
    private RemoteViews mLandscape;
    private RemoteViews mPortrait;
    private LinkedHashMap<SizeF, RemoteViews> mSized;
    private DrawInstructions mDrawInstructions;

    /** Marks a view class RemoteViews may inflate. See the class note. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RemoteView {}

    public static class ActionException extends RuntimeException {
        public ActionException(Exception exception) { super(exception); }
        public ActionException(String message) { super(message); }
    }

    /**
     * A click or checked change. A pending intent is sent as itself.
     * A fill-in intent is sent through the template set on an ancestor.
     */
    public static class RemoteResponse {
        private PendingIntent mPendingIntent;
        private Intent mFillIntent;
        private ArrayList<Integer> mSharedIds;
        private ArrayList<String> mSharedNames;

        public RemoteResponse() {}

        public static RemoteResponse fromPendingIntent(PendingIntent pendingIntent) {
            RemoteResponse response = new RemoteResponse();
            response.mPendingIntent = pendingIntent;
            return response;
        }

        public static RemoteResponse fromFillInIntent(Intent fillIntent) {
            RemoteResponse response = new RemoteResponse();
            response.mFillIntent = fillIntent;
            return response;
        }

        public RemoteResponse addSharedElement(int viewId, String sharedElementName) {
            if (sharedElementName == null) throw new NullPointerException("sharedElementName");
            if (mSharedIds == null) {
                mSharedIds = new ArrayList<Integer>();
                mSharedNames = new ArrayList<String>();
            }
            mSharedIds.add(Integer.valueOf(viewId));
            mSharedNames.add(sharedElementName);
            return this;
        }
    }

    /** Fixed list of row hierarchies for {@link #setRemoteAdapter(int, RemoteCollectionItems)}. */
    public static final class RemoteCollectionItems implements Parcelable {
        private final long[] mIds;
        private final RemoteViews[] mViews;
        private final boolean mHasStableIds;
        private final int mViewTypeCount;

        private RemoteCollectionItems(long[] ids, RemoteViews[] views, boolean hasStableIds, int viewTypeCount) {
            mIds = ids;
            mViews = views;
            mHasStableIds = hasStableIds;
            mViewTypeCount = viewTypeCount;
        }

        public long getItemId(int position) { return mIds[position]; }
        public RemoteViews getItemView(int position) { return mViews[position]; }
        public int getItemCount() { return mViews.length; }
        public int getViewTypeCount() { return mViewTypeCount; }
        public boolean hasStableIds() { return mHasStableIds; }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel dest, int flags) { dest.writeValue(this); }

        public static final Parcelable.Creator<RemoteCollectionItems> CREATOR =
                new Parcelable.Creator<RemoteCollectionItems>() {
                    public RemoteCollectionItems createFromParcel(Parcel source) {
                        return (RemoteCollectionItems) source.readValue(null);
                    }

                    public RemoteCollectionItems[] newArray(int size) { return new RemoteCollectionItems[size]; }
                };

        public static final class Builder {
            private final ArrayList<Long> mIds = new ArrayList<Long>();
            private final ArrayList<RemoteViews> mViews = new ArrayList<RemoteViews>();
            private boolean mHasStableIds;
            private int mViewTypeCount;

            public Builder() {}

            public Builder addItem(long id, RemoteViews view) {
                if (view == null) throw new NullPointerException("view");
                mIds.add(Long.valueOf(id));
                mViews.add(view);
                return this;
            }

            public Builder setHasStableIds(boolean hasStableIds) {
                mHasStableIds = hasStableIds;
                return this;
            }

            public Builder setViewTypeCount(int viewTypeCount) {
                mViewTypeCount = viewTypeCount;
                return this;
            }

            public RemoteCollectionItems build() {
                int distinct = 0;
                HashMap<Integer, Boolean> seen = new HashMap<Integer, Boolean>();
                for (int i = 0; i < mViews.size(); i++) {
                    Integer layout = Integer.valueOf(mViews.get(i).getLayoutId());
                    if (seen.put(layout, Boolean.TRUE) == null) distinct++;
                }
                int types = mViewTypeCount;
                if (types < 1) types = Math.max(1, distinct);
                else if (distinct > types) {
                    throw new IllegalArgumentException("View type count is set to " + types
                            + ", but the collection contains " + distinct + " different layout ids");
                }
                long[] ids = new long[mIds.size()];
                RemoteViews[] views = new RemoteViews[mViews.size()];
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = mIds.get(i).longValue();
                    views[i] = mViews.get(i);
                }
                return new RemoteCollectionItems(ids, views, mHasStableIds, types);
            }
        }
    }

    /** Serialized drawing commands. Not rendered; {@link #apply} returns an empty view. */
    public static final class DrawInstructions {
        private static final long VERSION = 1L;
        private final List<byte[]> mInstructions;

        private DrawInstructions(List<byte[]> instructions) {
            mInstructions = new ArrayList<byte[]>();
            for (int i = 0; i < instructions.size(); i++) {
                byte[] src = instructions.get(i);
                if (src == null) throw new NullPointerException("instructions[" + i + "]");
                byte[] copy = new byte[src.length];
                System.arraycopy(src, 0, copy, 0, src.length);
                mInstructions.add(copy);
            }
        }

        public static long getSupportedVersion() { return VERSION; }

        public static final class Builder {
            private final List<byte[]> mInstructions;

            public Builder(List<byte[]> instructions) {
                if (instructions == null) throw new NullPointerException("instructions");
                mInstructions = instructions;
            }

            public DrawInstructions build() { return new DrawInstructions(mInstructions); }
        }
    }

    /** Outline with the radius from {@link #setViewOutlinePreferredRadius}. */
    public static final class RemoteViewOutlineProvider extends ViewOutlineProvider {
        private final float mRadius;

        public RemoteViewOutlineProvider(float radius) { mRadius = radius; }

        public float getRadius() { return mRadius; }

        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mRadius);
        }
    }

    public static final Parcelable.Creator<RemoteViews> CREATOR = new Parcelable.Creator<RemoteViews>() {
        public RemoteViews createFromParcel(Parcel source) { return new RemoteViews(source); }
        public RemoteViews[] newArray(int size) { return new RemoteViews[size]; }
    };

    public RemoteViews(String packageName, int layoutId) { this(packageName, layoutId, 0); }

    public RemoteViews(String packageName, int layoutId, int viewId) {
        if (packageName == null) throw new NullPointerException("packageName");
        mPackage = packageName;
        mLayoutId = layoutId;
        mViewId = viewId;
    }

    public RemoteViews(RemoteViews landscape, RemoteViews portrait) {
        if (landscape == null || portrait == null) {
            throw new IllegalArgumentException("Both RemoteViews must be non-null");
        }
        if (landscape.getPackage() == null || !landscape.getPackage().equals(portrait.getPackage())) {
            throw new IllegalArgumentException("Both RemoteViews must share the same package");
        }
        mMode = MODE_ORIENTATION;
        mLandscape = new RemoteViews(landscape);
        mPortrait = new RemoteViews(portrait);
        mPackage = portrait.getPackage();
        mLayoutId = portrait.getLayoutId();
        mViewId = portrait.getViewId();
    }

    public RemoteViews(Map<SizeF, RemoteViews> remoteViews) {
        if (remoteViews == null || remoteViews.isEmpty()) {
            throw new IllegalArgumentException("remoteViews must not be empty");
        }
        mMode = MODE_SIZED;
        mSized = new LinkedHashMap<SizeF, RemoteViews>();
        String pkg = null;
        for (Map.Entry<SizeF, RemoteViews> entry : remoteViews.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) throw new NullPointerException("remoteViews");
            RemoteViews value = entry.getValue();
            if (pkg == null) {
                pkg = value.getPackage();
                mPackage = pkg;
                mLayoutId = value.getLayoutId();
                mViewId = value.getViewId();
            } else if (pkg == null || !pkg.equals(value.getPackage())) {
                throw new IllegalArgumentException("All RemoteViews must share the same package");
            }
            mSized.put(entry.getKey(), new RemoteViews(value));
        }
    }

    public RemoteViews(RemoteViews src) {
        if (src == null) throw new NullPointerException("src");
        copyFrom(src);
    }

    public RemoteViews(Parcel parcel) {
        Object value = parcel.readValue(null);
        if (!(value instanceof RemoteViews)) throw new ActionException("bad RemoteViews parcel");
        copyFrom((RemoteViews) value);
    }

    public RemoteViews(DrawInstructions drawInstructions) {
        if (drawInstructions == null) throw new NullPointerException("drawInstructions");
        mMode = MODE_DRAW;
        mDrawInstructions = drawInstructions;
    }

    private void copyFrom(RemoteViews src) {
        mPackage = src.mPackage;
        mLayoutId = src.mLayoutId;
        mViewId = src.mViewId;
        mLightBackgroundLayoutId = src.mLightBackgroundLayoutId;
        mMode = src.mMode;
        mActions.addAll(src.mActions);
        mLandscape = src.mLandscape == null ? null : new RemoteViews(src.mLandscape);
        mPortrait = src.mPortrait == null ? null : new RemoteViews(src.mPortrait);
        mDrawInstructions = src.mDrawInstructions;
        if (src.mSized != null) {
            mSized = new LinkedHashMap<SizeF, RemoteViews>();
            for (Map.Entry<SizeF, RemoteViews> entry : src.mSized.entrySet()) {
                mSized.put(entry.getKey(), new RemoteViews(entry.getValue()));
            }
        }
    }

    public RemoteViews clone() { return new RemoteViews(this); }

    public String getPackage() { return mPackage; }

    public int getLayoutId() { return mLayoutId; }

    public int getViewId() { return mViewId; }

    /** Stored for the shade. {@link #apply} keeps using {@link #getLayoutId()}. */
    public void setLightBackgroundLayoutId(int layoutId) { mLightBackgroundLayoutId = layoutId; }

    private void addAction(Action action) {
        if (mMode != MODE_NORMAL) {
            throw new RuntimeException("RemoteViews defining a size or orientation cannot be modified");
        }
        mActions.add(action);
    }

    public void addView(int viewId, RemoteViews nestedView) {
        addAction(new AddViewAction(viewId, nestedView, false, 0));
    }

    public void addStableView(int viewId, RemoteViews nestedView, int stableId) {
        addAction(new AddViewAction(viewId, nestedView, true, stableId));
    }

    public void removeAllViews(int viewId) { addAction(new RemoveAction(viewId)); }

    public void showNext(int viewId) { addAction(new CallAction(viewId, "showNext", new Class<?>[0], new Object[0])); }

    public void showPrevious(int viewId) {
        addAction(new CallAction(viewId, "showPrevious", new Class<?>[0], new Object[0]));
    }

    public void setDisplayedChild(int viewId, int childIndex) { setInt(viewId, "setDisplayedChild", childIndex); }

    public void setViewVisibility(int viewId, int visibility) { setInt(viewId, "setVisibility", visibility); }

    public void setTextViewText(int viewId, CharSequence text) { setCharSequence(viewId, "setText", text); }

    public void setTextViewTextSize(int viewId, int units, float size) {
        addAction(new CallAction(viewId, "setTextSize", new Class<?>[] {int.class, float.class},
                new Object[] {Integer.valueOf(units), Float.valueOf(size)}));
    }

    public void setTextViewCompoundDrawables(int viewId, int left, int top, int right, int bottom) {
        addAction(new CallAction(viewId, "setCompoundDrawablesWithIntrinsicBounds", INT4,
                new Object[] {Integer.valueOf(left), Integer.valueOf(top), Integer.valueOf(right),
                        Integer.valueOf(bottom)}));
    }

    public void setTextViewCompoundDrawablesRelative(int viewId, int start, int top, int end, int bottom) {
        addAction(new CallAction(viewId, "setCompoundDrawablesRelativeWithIntrinsicBounds", INT4,
                new Object[] {Integer.valueOf(start), Integer.valueOf(top), Integer.valueOf(end),
                        Integer.valueOf(bottom)}));
    }

    public void setImageViewResource(int viewId, int srcId) { setInt(viewId, "setImageResource", srcId); }

    public void setImageViewUri(int viewId, Uri uri) { setUri(viewId, "setImageURI", uri); }

    public void setImageViewBitmap(int viewId, Bitmap bitmap) { setBitmap(viewId, "setImageBitmap", bitmap); }

    public void setImageViewIcon(int viewId, Icon icon) { setIcon(viewId, "setImageIcon", icon); }

    public void setEmptyView(int viewId, int emptyViewId) { addAction(new EmptyAction(viewId, emptyViewId)); }

    public void setChronometer(int viewId, long base, String format, boolean started) {
        addAction(new ChronometerAction(viewId, base, format, started));
    }

    public void setChronometerCountDown(int viewId, boolean isCountDown) {
        setBoolean(viewId, "setCountDown", isCountDown);
    }

    public void setProgressBar(int viewId, int max, int progress, boolean indeterminate) {
        addAction(new ProgressAction(viewId, max, progress, indeterminate));
    }

    public void setOnClickPendingIntent(int viewId, PendingIntent pendingIntent) {
        addAction(new ClickAction(viewId, pendingIntent, null));
    }

    public void setOnClickResponse(int viewId, RemoteResponse response) {
        addAction(new ClickAction(viewId, null, response));
    }

    public void setPendingIntentTemplate(int viewId, PendingIntent pendingIntentTemplate) {
        addAction(new TemplateAction(viewId, pendingIntentTemplate));
    }

    public void setOnClickFillInIntent(int viewId, Intent fillInIntent) {
        addAction(new ClickAction(viewId, null, RemoteResponse.fromFillInIntent(fillInIntent)));
    }

    public void setOnCheckedChangeResponse(int viewId, RemoteResponse response) {
        addAction(new CheckedAction(viewId, response));
    }

    public void setOnStylusHandwritingPendingIntent(int viewId, PendingIntent pendingIntent) {
        addAction(new StylusAction(viewId, pendingIntent));
    }

    public void setTextColor(int viewId, int color) { setInt(viewId, "setTextColor", color); }

    /** @deprecated use {@link #setRemoteAdapter(int, Intent)} */
    @Deprecated
    public void setRemoteAdapter(int appWidgetId, int viewId, Intent intent) { setRemoteAdapter(viewId, intent); }

    public void setRemoteAdapter(int viewId, Intent intent) { addAction(new IntentAdapterAction(viewId, intent)); }

    public void setRemoteAdapter(int viewId, RemoteCollectionItems items) {
        if (items == null) throw new NullPointerException("items");
        addAction(new CollectionAction(viewId, items));
    }

    public void setScrollPosition(int viewId, int position) { setInt(viewId, "smoothScrollToPosition", position); }

    public void setRelativeScrollPosition(int viewId, int offset) { setInt(viewId, "smoothScrollByOffset", offset); }

    public void setViewPadding(int viewId, int left, int top, int right, int bottom) {
        addAction(new CallAction(viewId, "setPadding", INT4, new Object[] {Integer.valueOf(left), Integer.valueOf(top),
                Integer.valueOf(right), Integer.valueOf(bottom)}));
    }

    public void setViewLayoutMarginDimen(int viewId, int type, int dimen) {
        addAction(new MarginAction(viewId, type, 0f, 0, dimen, 0, MarginAction.FROM_RES));
    }

    public void setViewLayoutMarginAttr(int viewId, int type, int attr) {
        addAction(new MarginAction(viewId, type, 0f, 0, 0, attr, MarginAction.FROM_ATTR));
    }

    public void setViewLayoutMargin(int viewId, int type, float value, int units) {
        addAction(new MarginAction(viewId, type, value, units, 0, 0, MarginAction.FROM_VALUE));
    }

    public void setViewLayoutWidth(int viewId, float width, int units) {
        addAction(new SizeAction(viewId, true, width, units, 0, 0, SizeAction.FROM_VALUE));
    }

    public void setViewLayoutWidthDimen(int viewId, int widthDimen) {
        addAction(new SizeAction(viewId, true, 0f, 0, widthDimen, 0, SizeAction.FROM_RES));
    }

    public void setViewLayoutWidthAttr(int viewId, int widthAttr) {
        addAction(new SizeAction(viewId, true, 0f, 0, 0, widthAttr, SizeAction.FROM_ATTR));
    }

    public void setViewLayoutHeight(int viewId, float height, int units) {
        addAction(new SizeAction(viewId, false, height, units, 0, 0, SizeAction.FROM_VALUE));
    }

    public void setViewLayoutHeightDimen(int viewId, int heightDimen) {
        addAction(new SizeAction(viewId, false, 0f, 0, heightDimen, 0, SizeAction.FROM_RES));
    }

    public void setViewLayoutHeightAttr(int viewId, int heightAttr) {
        addAction(new SizeAction(viewId, false, 0f, 0, 0, heightAttr, SizeAction.FROM_ATTR));
    }

    public void setViewOutlinePreferredRadius(int viewId, float radius, int units) {
        addAction(new OutlineAction(viewId, radius, units, 0, 0, OutlineAction.FROM_VALUE));
    }

    public void setViewOutlinePreferredRadiusDimen(int viewId, int resId) {
        addAction(new OutlineAction(viewId, 0f, 0, resId, 0, OutlineAction.FROM_RES));
    }

    public void setViewOutlinePreferredRadiusAttr(int viewId, int attrId) {
        addAction(new OutlineAction(viewId, 0f, 0, 0, attrId, OutlineAction.FROM_ATTR));
    }

    public void setBoolean(int viewId, String methodName, boolean value) {
        addAction(new ReflectAction(viewId, methodName, boolean.class, Boolean.valueOf(value)));
    }

    public void setByte(int viewId, String methodName, byte value) {
        addAction(new ReflectAction(viewId, methodName, byte.class, Byte.valueOf(value)));
    }

    public void setShort(int viewId, String methodName, short value) {
        addAction(new ReflectAction(viewId, methodName, short.class, Short.valueOf(value)));
    }

    public void setInt(int viewId, String methodName, int value) {
        addAction(new ReflectAction(viewId, methodName, int.class, Integer.valueOf(value)));
    }

    public void setIntDimen(int viewId, String methodName, int dimenResource) {
        addAction(new ResolvedIntAction(viewId, methodName, 0f, 0, dimenResource, 0, ResolvedIntAction.FROM_RES));
    }

    public void setIntDimen(int viewId, String methodName, float value, int unit) {
        addAction(new ResolvedIntAction(viewId, methodName, value, unit, 0, 0, ResolvedIntAction.FROM_VALUE));
    }

    public void setIntDimenAttr(int viewId, String methodName, int dimenAttr) {
        addAction(new ResolvedIntAction(viewId, methodName, 0f, 0, 0, dimenAttr, ResolvedIntAction.FROM_ATTR));
    }

    public void setColor(int viewId, String methodName, int colorResource) {
        addAction(new ColorAction(viewId, methodName, colorResource, 0, false));
    }

    public void setColorAttr(int viewId, String methodName, int colorAttr) {
        addAction(new ColorAction(viewId, methodName, 0, colorAttr, true));
    }

    /** {@code notNight} is used unless the configuration is night mode, then {@code night}. */
    public void setColorInt(int viewId, String methodName, int notNight, int night) {
        addAction(new ColorPairAction(viewId, methodName, notNight, night));
    }

    public void setColorStateList(int viewId, String methodName, ColorStateList colorStateList) {
        addAction(new ColorStateAction(viewId, methodName, colorStateList, null, 0, false));
    }

    public void setColorStateList(int viewId, String methodName, ColorStateList notNight, ColorStateList night) {
        addAction(new ColorStateAction(viewId, methodName, notNight, night, 0, false));
    }

    public void setColorStateList(int viewId, String methodName, int colorStateListRes) {
        addAction(new ColorStateAction(viewId, methodName, null, null, colorStateListRes, false));
    }

    public void setColorStateListAttr(int viewId, String methodName, int colorAttr) {
        addAction(new ColorStateAction(viewId, methodName, null, null, colorAttr, true));
    }

    public void setLong(int viewId, String methodName, long value) {
        addAction(new ReflectAction(viewId, methodName, long.class, Long.valueOf(value)));
    }

    public void setFloat(int viewId, String methodName, float value) {
        addAction(new ReflectAction(viewId, methodName, float.class, Float.valueOf(value)));
    }

    public void setFloatDimen(int viewId, String methodName, int dimenResource) {
        addAction(new ResolvedFloatAction(viewId, methodName, 0f, 0, dimenResource, 0, ResolvedFloatAction.FROM_RES));
    }

    public void setFloatDimen(int viewId, String methodName, float value, int unit) {
        addAction(new ResolvedFloatAction(viewId, methodName, value, unit, 0, 0, ResolvedFloatAction.FROM_VALUE));
    }

    public void setFloatDimenAttr(int viewId, String methodName, int dimenAttr) {
        addAction(new ResolvedFloatAction(viewId, methodName, 0f, 0, 0, dimenAttr, ResolvedFloatAction.FROM_ATTR));
    }

    public void setDouble(int viewId, String methodName, double value) {
        addAction(new ReflectAction(viewId, methodName, double.class, Double.valueOf(value)));
    }

    public void setChar(int viewId, String methodName, char value) {
        addAction(new ReflectAction(viewId, methodName, char.class, Character.valueOf(value)));
    }

    public void setString(int viewId, String methodName, String value) {
        addAction(new ReflectAction(viewId, methodName, String.class, value));
    }

    public void setCharSequence(int viewId, String methodName, CharSequence value) {
        addAction(new ReflectAction(viewId, methodName, CharSequence.class, value));
    }

    public void setCharSequence(int viewId, String methodName, int stringResource) {
        addAction(new CharSeqResAction(viewId, methodName, stringResource, false));
    }

    public void setCharSequenceAttr(int viewId, String methodName, int stringAttribute) {
        addAction(new CharSeqResAction(viewId, methodName, stringAttribute, true));
    }

    public void setUri(int viewId, String methodName, Uri value) {
        addAction(new ReflectAction(viewId, methodName, Uri.class, value));
    }

    public void setBitmap(int viewId, String methodName, Bitmap value) {
        addAction(new ReflectAction(viewId, methodName, Bitmap.class, value));
    }

    public void setBlendMode(int viewId, String methodName, BlendMode value) {
        addAction(new ReflectAction(viewId, methodName, BlendMode.class, value));
    }

    public void setBundle(int viewId, String methodName, Bundle value) {
        addAction(new ReflectAction(viewId, methodName, Bundle.class, value));
    }

    public void setIntent(int viewId, String methodName, Intent value) {
        addAction(new ReflectAction(viewId, methodName, Intent.class, value));
    }

    public void setIcon(int viewId, String methodName, Icon value) {
        addAction(new ReflectAction(viewId, methodName, Icon.class, value));
    }

    /** {@code notNight} unless the configuration is night mode, then {@code night}. */
    public void setIcon(int viewId, String methodName, Icon notNight, Icon night) {
        addAction(new IconPairAction(viewId, methodName, notNight, night));
    }

    public void setContentDescription(int viewId, CharSequence contentDescription) {
        setCharSequence(viewId, "setContentDescription", contentDescription);
    }

    public void setAccessibilityTraversalBefore(int viewId, int nextId) {
        setInt(viewId, "setAccessibilityTraversalBefore", nextId);
    }

    public void setAccessibilityTraversalAfter(int viewId, int nextId) {
        setInt(viewId, "setAccessibilityTraversalAfter", nextId);
    }

    public void setLabelFor(int viewId, int labeledId) { setInt(viewId, "setLabelFor", labeledId); }

    public void setCompoundButtonChecked(int viewId, boolean checked) { setBoolean(viewId, "setChecked", checked); }

    public void setRadioGroupChecked(int viewId, int checkedId) { setInt(viewId, "check", checkedId); }

    public View apply(Context context, ViewGroup parent) {
        if (context == null) throw new NullPointerException("context");
        RemoteViews rv = choose(context);
        if (rv.mMode == MODE_DRAW) return rv.inflateDraw(context);
        Context inflation = rv.inflationContext(context);
        LayoutInflater inflater = LayoutInflater.from(inflation).cloneInContext(inflation);
        inflater.setFilter(rv);
        View result = inflater.inflate(rv.mLayoutId, parent, false);
        if (rv.mViewId != 0) result.setId(rv.mViewId);
        result.setTagInternal(TAG_LAYOUT_ID, Integer.valueOf(rv.mLayoutId));
        rv.performApply(result, parent, inflation);
        return result;
    }

    public void reapply(Context context, View v) {
        if (context == null) throw new NullPointerException("context");
        if (v == null) throw new NullPointerException("v");
        RemoteViews rv = choose(context);
        Object tag = v.getTag(TAG_LAYOUT_ID);
        if (!(tag instanceof Integer) || ((Integer) tag).intValue() != rv.mLayoutId) {
            throw new ActionException(REAPPLY_ERROR);
        }
        ViewParent parent = v.getParent();
        rv.performApply(v, parent instanceof ViewGroup ? (ViewGroup) parent : null, rv.inflationContext(context));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean onLoadClass(Class clazz) {
        if (clazz == null || !View.class.isAssignableFrom(clazz)) return false;
        String name = clazz.getName();
        return name.startsWith("android.") || name.startsWith("com.android.internal.");
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { dest.writeValue(this); }

    private void performApply(View root, ViewGroup parent, Context context) {
        for (int i = 0; i < mActions.size(); i++) mActions.get(i).apply(root, parent, context);
    }

    private RemoteViews choose(Context context) {
        if (mMode == MODE_ORIENTATION) {
            int orientation = context.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE && mLandscape != null) return mLandscape;
            return mPortrait != null ? mPortrait : this;
        }
        if (mMode == MODE_SIZED) return chooseSized(context);
        return this;
    }

    /** Smallest width in dp that still fits the screen, else the largest. */
    private RemoteViews chooseSized(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float density = metrics.density <= 0f ? 1f : metrics.density;
        float widthDp = metrics.widthPixels / density;
        RemoteViews fit = null;
        float fitWidth = Float.MAX_VALUE;
        RemoteViews largest = null;
        float largestWidth = -1f;
        for (Map.Entry<SizeF, RemoteViews> entry : mSized.entrySet()) {
            float width = entry.getKey().getWidth();
            if (width >= widthDp && width < fitWidth) {
                fitWidth = width;
                fit = entry.getValue();
            }
            if (width > largestWidth) {
                largestWidth = width;
                largest = entry.getValue();
            }
        }
        return fit != null ? fit : largest;
    }

    private Context inflationContext(Context context) {
        if (mPackage == null || mPackage.equals(context.getPackageName())) return context;
        try {
            return context.createPackageContext(mPackage, Context.CONTEXT_RESTRICTED);
        } catch (PackageManager.NameNotFoundException e) {
            throw new ActionException(e);
        }
    }

    private View inflateDraw(Context context) {
        if (!sLoggedDraw) {
            sLoggedDraw = true;
            Log.w(LOG_TAG, "DrawInstructions are not rendered");
        }
        View view = new View(context);
        view.setTagInternal(TAG_LAYOUT_ID, Integer.valueOf(mLayoutId));
        return view;
    }

    private static View find(View root, int viewId) {
        View view = root.findViewById(viewId);
        if (view == null) Log.w(LOG_TAG, "view id " + viewId + " not found");
        return view;
    }

    private static void call(View view, String method, Class<?>[] types, Object[] args) {
        try {
            Method found = view.getClass().getMethod(method, types);
            found.invoke(view, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new ActionException(e);
        } catch (NoSuchMethodException e) {
            throw new ActionException(view.getClass().getName() + " has no method " + method);
        } catch (Exception e) {
            throw new ActionException(e);
        }
    }

    private static final Class<?>[] INT4 = new Class<?>[] {int.class, int.class, int.class, int.class};

    private static int roundPx(float value, float px) {
        int res = Math.round(px);
        if (res != 0 || value == 0f) return res;
        return value > 0f ? 1 : -1;
    }

    private static DisplayMetrics metrics(Context context) { return context.getResources().getDisplayMetrics(); }

    private static float pxFloat(Context context, float value, int unit) {
        return TypedValue.applyDimension(unit, value, metrics(context));
    }

    private static int pxInt(Context context, float value, int unit) {
        return roundPx(value, pxFloat(context, value, unit));
    }

    private static TypedValue resolveAttr(Context context, int attr) {
        TypedValue tv = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, tv, true)) {
            throw new ActionException("attribute not found: 0x" + Integer.toHexString(attr));
        }
        return tv;
    }

    private static int dimenPx(Context context, int resId) {
        try {
            return context.getResources().getDimensionPixelSize(resId);
        } catch (Resources.NotFoundException e) {
            throw new ActionException(e);
        }
    }

    private static float dimenFloat(Context context, int resId) {
        try {
            return context.getResources().getDimension(resId);
        } catch (Resources.NotFoundException e) {
            throw new ActionException(e);
        }
    }

    private static int attrDimenPx(Context context, int attr) {
        TypedValue tv = resolveAttr(context, attr);
        if (tv.type == TypedValue.TYPE_DIMENSION) {
            return TypedValue.complexToDimensionPixelSize(tv.data, metrics(context));
        }
        if (tv.resourceId != 0) return dimenPx(context, tv.resourceId);
        if (tv.type >= TypedValue.TYPE_FIRST_INT && tv.type <= TypedValue.TYPE_LAST_INT) return tv.data;
        throw new ActionException("attribute is not a dimension");
    }

    private static float attrDimenFloat(Context context, int attr) {
        TypedValue tv = resolveAttr(context, attr);
        if (tv.type == TypedValue.TYPE_DIMENSION) return TypedValue.complexToDimension(tv.data, metrics(context));
        if (tv.resourceId != 0) return dimenFloat(context, tv.resourceId);
        if (tv.type == TypedValue.TYPE_FLOAT) return tv.getFloat();
        if (tv.type >= TypedValue.TYPE_FIRST_INT && tv.type <= TypedValue.TYPE_LAST_INT) return tv.data;
        throw new ActionException("attribute is not a dimension");
    }

    private static int colorOf(Context context, int resId) {
        try {
            return context.getColor(resId);
        } catch (Resources.NotFoundException e) {
            throw new ActionException(e);
        }
    }

    private static int attrColor(Context context, int attr) {
        TypedValue tv = resolveAttr(context, attr);
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) return tv.data;
        if (tv.resourceId != 0) return colorOf(context, tv.resourceId);
        throw new ActionException("attribute is not a color");
    }

    private static boolean isNight(Context context) {
        return context.getResources().getConfiguration().isNightModeActive();
    }

    private static boolean isLayoutConstant(float value) {
        return value == ViewGroup.LayoutParams.MATCH_PARENT || value == ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    private static void launch(View view, PendingIntent pendingIntent, Intent fill) {
        if (pendingIntent == null) return;
        try {
            pendingIntent.send(view.getContext(), 0, fill);
        } catch (PendingIntent.CanceledException e) {
            Log.w(LOG_TAG, "PendingIntent canceled", e);
        }
    }

    private static PendingIntent findTemplate(View view) {
        View current = view;
        while (current != null) {
            Object tag = current.getTag(TAG_TEMPLATE);
            if (tag instanceof PendingIntent) return (PendingIntent) tag;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static Intent withSharedElements(View clicked, RemoteResponse response, Intent base) {
        if (response == null || response.mSharedIds == null || response.mSharedIds.isEmpty()) return base;
        Intent out = base == null ? new Intent() : new Intent(base);
        Bundle bounds = new Bundle();
        View root = clicked.getRootView();
        for (int i = 0; i < response.mSharedIds.size(); i++) {
            int id = response.mSharedIds.get(i).intValue();
            View shared = root.findViewById(id);
            if (shared == null) continue;
            int[] loc = new int[2];
            shared.getLocationOnScreen(loc);
            bounds.putParcelable(response.mSharedNames.get(i),
                    new Rect(loc[0], loc[1], loc[0] + shared.getWidth(), loc[1] + shared.getHeight()));
        }
        out.putExtra(EXTRA_SHARED_ELEMENT_BOUNDS, bounds);
        return out;
    }

    private static void launchResponse(View view, RemoteResponse response, Intent extraFill) {
        if (response == null) {
            view.setOnClickListener(null);
            return;
        }
        Intent fill = extraFill;
        if (response.mFillIntent != null) {
            fill = new Intent(response.mFillIntent);
            if (extraFill != null && extraFill.getExtras() != null) fill.putExtras(extraFill.getExtras());
        }
        final Intent fillBase = fill;
        if (response.mPendingIntent != null) {
            final PendingIntent pending = response.mPendingIntent;
            view.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { launch(v, pending, withSharedElements(v, response, fillBase)); }
            });
            view.setClickable(true);
            return;
        }
        if (response.mFillIntent != null || extraFill != null) {
            view.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    PendingIntent template = findTemplate(v);
                    if (template == null) {
                        Log.w(LOG_TAG, "fill-in click with no PendingIntent template");
                        return;
                    }
                    launch(v, template, withSharedElements(v, response, fillBase));
                }
            });
            view.setClickable(true);
            return;
        }
        view.setOnClickListener(null);
    }

    private interface Action {
        void apply(View root, ViewGroup parent, Context context);
    }

    private static final class ReflectAction implements Action {
        private final int mViewId;
        private final String mMethod;
        private final Class<?> mType;
        private final Object mValue;

        ReflectAction(int viewId, String method, Class<?> type, Object value) {
            mViewId = viewId;
            mMethod = method;
            mType = type;
            mValue = value;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            call(view, mMethod, new Class<?>[] {mType}, new Object[] {mValue});
        }
    }

    private static final class CallAction implements Action {
        private final int mViewId;
        private final String mMethod;
        private final Class<?>[] mTypes;
        private final Object[] mArgs;

        CallAction(int viewId, String method, Class<?>[] types, Object[] args) {
            mViewId = viewId;
            mMethod = method;
            mTypes = types;
            mArgs = args;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            call(view, mMethod, mTypes, mArgs);
        }
    }

    private static final class ResolvedIntAction implements Action {
        static final int FROM_RES = 0, FROM_VALUE = 1, FROM_ATTR = 2;
        private final int mViewId, mUnit, mResId, mAttr, mSource;
        private final String mMethod;
        private final float mValue;

        ResolvedIntAction(int viewId, String method, float value, int unit, int resId, int attr, int source) {
            mViewId = viewId;
            mMethod = method;
            mValue = value;
            mUnit = unit;
            mResId = resId;
            mAttr = attr;
            mSource = source;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            int px = mSource == FROM_RES ? dimenPx(context, mResId)
                    : mSource == FROM_ATTR ? attrDimenPx(context, mAttr) : pxInt(context, mValue, mUnit);
            call(view, mMethod, new Class<?>[] {int.class}, new Object[] {Integer.valueOf(px)});
        }
    }

    private static final class ResolvedFloatAction implements Action {
        static final int FROM_RES = 0, FROM_VALUE = 1, FROM_ATTR = 2;
        private final int mViewId, mUnit, mResId, mAttr, mSource;
        private final String mMethod;
        private final float mValue;

        ResolvedFloatAction(int viewId, String method, float value, int unit, int resId, int attr, int source) {
            mViewId = viewId;
            mMethod = method;
            mValue = value;
            mUnit = unit;
            mResId = resId;
            mAttr = attr;
            mSource = source;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            float px = mSource == FROM_RES ? dimenFloat(context, mResId)
                    : mSource == FROM_ATTR ? attrDimenFloat(context, mAttr) : pxFloat(context, mValue, mUnit);
            call(view, mMethod, new Class<?>[] {float.class}, new Object[] {Float.valueOf(px)});
        }
    }

    private static final class ColorAction implements Action {
        private final int mViewId, mResOrAttr;
        private final String mMethod;
        private final boolean mAttr;

        ColorAction(int viewId, String method, int resId, int attr, boolean attrSource) {
            mViewId = viewId;
            mMethod = method;
            mResOrAttr = attrSource ? attr : resId;
            mAttr = attrSource;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            int color = mAttr ? attrColor(context, mResOrAttr) : colorOf(context, mResOrAttr);
            call(view, mMethod, new Class<?>[] {int.class}, new Object[] {Integer.valueOf(color)});
        }
    }

    private static final class ColorPairAction implements Action {
        private final int mViewId, mNotNight, mNight;
        private final String mMethod;

        ColorPairAction(int viewId, String method, int notNight, int night) {
            mViewId = viewId;
            mMethod = method;
            mNotNight = notNight;
            mNight = night;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            int color = isNight(context) ? mNight : mNotNight;
            call(view, mMethod, new Class<?>[] {int.class}, new Object[] {Integer.valueOf(color)});
        }
    }

    private static final class ColorStateAction implements Action {
        private final int mViewId, mResOrAttr;
        private final String mMethod;
        private final ColorStateList mNotNight, mNight;
        private final boolean mAttr;

        ColorStateAction(int viewId, String method, ColorStateList notNight, ColorStateList night, int resOrAttr,
                boolean attr) {
            mViewId = viewId;
            mMethod = method;
            mNotNight = notNight;
            mNight = night;
            mResOrAttr = resOrAttr;
            mAttr = attr;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            ColorStateList list;
            if (mResOrAttr != 0) {
                if (mAttr) list = ColorStateList.valueOf(attrColor(context, mResOrAttr));
                else {
                    try {
                        list = context.getColorStateList(mResOrAttr);
                    } catch (Resources.NotFoundException e) {
                        throw new ActionException(e);
                    }
                }
            } else if (mNight != null && isNight(context)) list = mNight;
            else list = mNotNight;
            call(view, mMethod, new Class<?>[] {ColorStateList.class}, new Object[] {list});
        }
    }

    private static final class CharSeqResAction implements Action {
        private final int mViewId, mResOrAttr;
        private final String mMethod;
        private final boolean mAttr;

        CharSeqResAction(int viewId, String method, int resOrAttr, boolean attr) {
            mViewId = viewId;
            mMethod = method;
            mResOrAttr = resOrAttr;
            mAttr = attr;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            CharSequence text;
            try {
                if (mAttr) {
                    TypedValue tv = resolveAttr(context, mResOrAttr);
                    if (tv.string != null) text = tv.string;
                    else if (tv.resourceId != 0) text = context.getText(tv.resourceId);
                    else throw new ActionException("attribute is not a string");
                } else text = context.getText(mResOrAttr);
            } catch (Resources.NotFoundException e) {
                throw new ActionException(e);
            }
            call(view, mMethod, new Class<?>[] {CharSequence.class}, new Object[] {text});
        }
    }

    private static final class IconPairAction implements Action {
        private final int mViewId;
        private final String mMethod;
        private final Icon mNotNight, mNight;

        IconPairAction(int viewId, String method, Icon notNight, Icon night) {
            mViewId = viewId;
            mMethod = method;
            mNotNight = notNight;
            mNight = night;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            Icon icon = isNight(context) ? mNight : mNotNight;
            call(view, mMethod, new Class<?>[] {Icon.class}, new Object[] {icon});
        }
    }

    private static final class ProgressAction implements Action {
        private final int mViewId, mMax, mProgress;
        private final boolean mIndeterminate;

        ProgressAction(int viewId, int max, int progress, boolean indeterminate) {
            mViewId = viewId;
            mMax = max;
            mProgress = progress;
            mIndeterminate = indeterminate;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            if (!(view instanceof ProgressBar)) {
                throw new ActionException(view.getClass().getName() + " is not a ProgressBar");
            }
            ProgressBar bar = (ProgressBar) view;
            bar.setMax(mMax);
            bar.setProgress(mProgress);
            bar.setIndeterminate(mIndeterminate);
        }
    }

    private static final class ChronometerAction implements Action {
        private final int mViewId;
        private final long mBase;
        private final String mFormat;
        private final boolean mStarted;

        ChronometerAction(int viewId, long base, String format, boolean started) {
            mViewId = viewId;
            mBase = base;
            mFormat = format;
            mStarted = started;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            if (!(view instanceof Chronometer)) {
                throw new ActionException(view.getClass().getName() + " is not a Chronometer");
            }
            Chronometer chronometer = (Chronometer) view;
            chronometer.setBase(mBase);
            chronometer.setFormat(mFormat);
            chronometer.setStarted(mStarted);
        }
    }

    private static final class EmptyAction implements Action {
        private final int mViewId, mEmptyId;

        EmptyAction(int viewId, int emptyId) {
            mViewId = viewId;
            mEmptyId = emptyId;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            if (!(view instanceof AdapterView)) {
                throw new ActionException(view.getClass().getName() + " is not an AdapterView");
            }
            View empty = root.findViewById(mEmptyId);
            ((AdapterView) view).setEmptyView(empty);
        }
    }

    private static final class ClickAction implements Action {
        private final int mViewId;
        private final PendingIntent mPending;
        private final RemoteResponse mResponse;

        ClickAction(int viewId, PendingIntent pending, RemoteResponse response) {
            mViewId = viewId;
            mPending = pending;
            mResponse = response;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            if (mPending != null || mResponse == null) {
                if (mPending == null) {
                    view.setOnClickListener(null);
                    return;
                }
                view.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { launch(v, mPending, null); }
                });
                view.setClickable(true);
                return;
            }
            launchResponse(view, mResponse, null);
        }
    }

    private static final class TemplateAction implements Action {
        private final int mViewId;
        private final PendingIntent mTemplate;

        TemplateAction(int viewId, PendingIntent template) {
            mViewId = viewId;
            mTemplate = template;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            view.setTagInternal(TAG_TEMPLATE, mTemplate);
        }
    }

    private static final class CheckedAction implements Action {
        private final int mViewId;
        private final RemoteResponse mResponse;

        CheckedAction(int viewId, RemoteResponse response) {
            mViewId = viewId;
            mResponse = response;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            if (!(view instanceof CompoundButton)) {
                throw new ActionException(view.getClass().getName() + " is not a CompoundButton");
            }
            ((CompoundButton) view).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                    Intent extra = new Intent();
                    extra.putExtra(EXTRA_CHECKED, isChecked);
                    if (mResponse == null) return;
                    if (mResponse.mPendingIntent != null) {
                        launch(button, mResponse.mPendingIntent, withSharedElements(button, mResponse, extra));
                        return;
                    }
                    PendingIntent template = findTemplate(button);
                    Intent fill = mResponse.mFillIntent == null ? extra : new Intent(mResponse.mFillIntent);
                    fill.putExtra(EXTRA_CHECKED, isChecked);
                    if (template == null && mResponse.mFillIntent != null) {
                        Log.w(LOG_TAG, "checked change with no PendingIntent template");
                        return;
                    }
                    launch(button, template, withSharedElements(button, mResponse, fill));
                }
            });
        }
    }

    private static final class StylusAction implements Action {
        private final int mViewId;
        private final PendingIntent mPending;

        StylusAction(int viewId, PendingIntent pending) {
            mViewId = viewId;
            mPending = pending;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            if (find(root, mViewId) == null) return;
            if (mPending != null && !sLoggedStylus) {
                sLoggedStylus = true;
                Log.w(LOG_TAG, "stylus handwriting pending intents are not delivered");
            }
        }
    }

    private static final class IntentAdapterAction implements Action {
        private final int mViewId;
        private final Intent mIntent;

        IntentAdapterAction(int viewId, Intent intent) {
            mViewId = viewId;
            mIntent = intent;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view instanceof AbsListView) ((AbsListView) view).setRemoteViewsAdapter(mIntent);
            if (!sLoggedIntentAdapter) {
                sLoggedIntentAdapter = true;
                Log.w(LOG_TAG, "setRemoteAdapter(Intent) needs a RemoteViewsService; use RemoteCollectionItems");
            }
        }
    }

    private static final class CollectionAction implements Action {
        private final int mViewId;
        private final RemoteCollectionItems mItems;

        CollectionAction(int viewId, RemoteCollectionItems items) {
            mViewId = viewId;
            mItems = items;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            if (!(view instanceof AdapterView)) {
                throw new ActionException(view.getClass().getName() + " is not an AdapterView");
            }
            ((AdapterView) view).setAdapter(new CollectionAdapter(context, mItems));
        }
    }

    private static final class CollectionAdapter extends BaseAdapter {
        private final Context mContext;
        private final RemoteCollectionItems mItems;
        private final HashMap<Integer, Integer> mTypes = new HashMap<Integer, Integer>();

        CollectionAdapter(Context context, RemoteCollectionItems items) {
            mContext = context;
            mItems = items;
            for (int i = 0; i < items.getItemCount(); i++) {
                Integer layout = Integer.valueOf(items.getItemView(i).getLayoutId());
                if (!mTypes.containsKey(layout)) mTypes.put(layout, Integer.valueOf(mTypes.size()));
            }
        }

        public int getCount() { return mItems.getItemCount(); }
        public Object getItem(int position) { return mItems.getItemView(position); }
        public long getItemId(int position) { return mItems.hasStableIds() ? mItems.getItemId(position) : position; }
        public boolean hasStableIds() { return mItems.hasStableIds(); }

        public int getViewTypeCount() { return Math.max(1, Math.max(mItems.getViewTypeCount(), mTypes.size())); }

        public int getItemViewType(int position) {
            Integer type = mTypes.get(Integer.valueOf(mItems.getItemView(position).getLayoutId()));
            return type == null ? 0 : type.intValue();
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            RemoteViews item = mItems.getItemView(position);
            if (convertView != null) {
                try {
                    item.reapply(mContext, convertView);
                    return convertView;
                } catch (ActionException ignored) {
                    // The recycled view came from a different layout.
                }
            }
            return item.apply(mContext, parent);
        }
    }

    private static final class AddViewAction implements Action {
        private final int mViewId, mStableId;
        private final RemoteViews mNested;
        private final boolean mStable;

        AddViewAction(int viewId, RemoteViews nested, boolean stable, int stableId) {
            if (nested == null) throw new NullPointerException("nestedView");
            mViewId = viewId;
            mNested = nested;
            mStable = stable;
            mStableId = stableId;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            if (!(view instanceof ViewGroup)) {
                throw new ActionException(view.getClass().getName() + " is not a ViewGroup");
            }
            ViewGroup group = (ViewGroup) view;
            View child = mNested.apply(context, group);
            if (mStable) child.setId(mStableId);
            group.addView(child);
        }
    }

    private static final class RemoveAction implements Action {
        private final int mViewId;

        RemoveAction(int viewId) { mViewId = viewId; }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            if (!(view instanceof ViewGroup)) {
                throw new ActionException(view.getClass().getName() + " is not a ViewGroup");
            }
            ((ViewGroup) view).removeAllViews();
        }
    }

    private static final class SizeAction implements Action {
        static final int FROM_VALUE = 0, FROM_RES = 1, FROM_ATTR = 2;
        private final int mViewId, mUnit, mResId, mAttr, mSource;
        private final float mValue;
        private final boolean mWidth;

        SizeAction(int viewId, boolean width, float value, int unit, int resId, int attr, int source) {
            mViewId = viewId;
            mWidth = width;
            mValue = value;
            mUnit = unit;
            mResId = resId;
            mAttr = attr;
            mSource = source;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            int px;
            if (mSource == FROM_VALUE && isLayoutConstant(mValue)) px = (int) mValue;
            else if (mSource == FROM_RES) px = dimenPx(context, mResId);
            else if (mSource == FROM_ATTR) px = attrDimenPx(context, mAttr);
            else px = pxInt(context, mValue, mUnit);
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp == null) {
                lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            if (mWidth) lp.width = px;
            else lp.height = px;
            view.setLayoutParams(lp);
        }
    }

    private static final class MarginAction implements Action {
        static final int FROM_VALUE = 0, FROM_RES = 1, FROM_ATTR = 2;
        private final int mViewId, mType, mUnit, mResId, mAttr, mSource;
        private final float mValue;

        MarginAction(int viewId, int type, float value, int unit, int resId, int attr, int source) {
            mViewId = viewId;
            mType = type;
            mValue = value;
            mUnit = unit;
            mResId = resId;
            mAttr = attr;
            mSource = source;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (!(lp instanceof ViewGroup.MarginLayoutParams)) {
                throw new ActionException(view.getClass().getName() + " has no margin layout params");
            }
            int px = mSource == FROM_RES ? dimenPx(context, mResId)
                    : mSource == FROM_ATTR ? attrDimenPx(context, mAttr) : pxInt(context, mValue, mUnit);
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            switch (mType) {
                case MARGIN_LEFT: mlp.leftMargin = px; break;
                case MARGIN_TOP: mlp.topMargin = px; break;
                case MARGIN_RIGHT: mlp.rightMargin = px; break;
                case MARGIN_BOTTOM: mlp.bottomMargin = px; break;
                case MARGIN_START: mlp.setMarginStart(px); break;
                case MARGIN_END: mlp.setMarginEnd(px); break;
                default: throw new ActionException("unknown margin type " + mType);
            }
            view.setLayoutParams(mlp);
        }
    }

    private static final class OutlineAction implements Action {
        static final int FROM_VALUE = 0, FROM_RES = 1, FROM_ATTR = 2;
        private final int mViewId, mUnit, mResId, mAttr, mSource;
        private final float mValue;

        OutlineAction(int viewId, float value, int unit, int resId, int attr, int source) {
            mViewId = viewId;
            mValue = value;
            mUnit = unit;
            mResId = resId;
            mAttr = attr;
            mSource = source;
        }

        public void apply(View root, ViewGroup parent, Context context) {
            View view = find(root, mViewId);
            if (view == null) return;
            float px = mSource == FROM_RES ? dimenFloat(context, mResId)
                    : mSource == FROM_ATTR ? attrDimenFloat(context, mAttr) : pxFloat(context, mValue, mUnit);
            view.setOutlineProvider(new RemoteViewOutlineProvider(px));
        }
    }
}
