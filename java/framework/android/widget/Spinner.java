package android.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import com.android.internal.util.InternalRes;

/**
 * Port of AOSP Spinner: shows the selected item and opens a drop-down
 * (ListPopupWindow, Material default) or a single-choice dialog to change it.
 */
public class Spinner extends AbsSpinner implements OnClickListener {
    private static final int MAX_ITEMS_MEASURED = 15;
    public static final int MODE_DIALOG = 0;
    public static final int MODE_DROPDOWN = 1;
    private static final int MODE_THEME = -1;

    private static final int[] ATTRS = {
        android.R.attr.dropDownWidth, android.R.attr.popupBackground, android.R.attr.prompt,
        android.R.attr.spinnerMode, android.R.attr.gravity, android.R.attr.dropDownSelector,
        android.R.attr.popupTheme,
    };

    private final Rect mTempRect = new Rect();
    private final Context mPopupContext;
    private SpinnerAdapter mTempAdapter;
    private SpinnerPopup mPopup;
    int mDropDownWidth;
    private int mGravity;
    private boolean mDisableChildrenWhenDisabled;

    public Spinner(Context context) { this(context, null); }

    public Spinner(Context context, int mode) { this(context, null, android.R.attr.spinnerStyle, mode); }

    public Spinner(Context context, AttributeSet attrs) { this(context, attrs, android.R.attr.spinnerStyle); }

    public Spinner(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0, MODE_THEME); }

    public Spinner(Context context, AttributeSet attrs, int defStyleAttr, int mode) {
        this(context, attrs, defStyleAttr, 0, mode);
    }

    public Spinner(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes, int mode) {
        this(context, attrs, defStyleAttr, defStyleRes, mode, null);
    }

    public Spinner(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes, int mode,
            Resources.Theme popupTheme) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TypedArray a = context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes);
        if (popupTheme != null) {
            mPopupContext = new ContextThemeWrapper(context, popupTheme);
        } else {
            final int popupThemeResId = a.getResourceId(6, 0);
            mPopupContext = popupThemeResId != 0 ? new ContextThemeWrapper(context, popupThemeResId) : context;
        }
        if (mode == MODE_THEME) mode = a.getInt(3, MODE_DIALOG);
        switch (mode) {
            case MODE_DIALOG: {
                mPopup = new DialogPopup();
                mPopup.setPromptText(a.getString(2));
                break;
            }
            case MODE_DROPDOWN: {
                final DropdownPopup popup = new DropdownPopup(mPopupContext, attrs, defStyleAttr, defStyleRes);
                final TypedArray pa = mPopupContext.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes);
                mDropDownWidth = pa.getLayoutDimension(0, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (pa.hasValue(5)) popup.setListSelector(pa.getDrawable(5));
                popup.setBackgroundDrawable(pa.getDrawable(1));
                popup.setPromptText(a.getString(2));
                pa.recycle();
                mPopup = popup;
                break;
            }
            default:
                break;
        }
        mGravity = a.getInt(4, Gravity.CENTER);
        int disableAttr = InternalRes.attr("disableChildrenWhenDisabled");
        a.recycle();
        if (disableAttr != 0) {
            TypedArray d = context.obtainStyledAttributes(attrs, new int[] {disableAttr}, defStyleAttr, defStyleRes);
            mDisableChildrenWhenDisabled = d.getBoolean(0, false);
            d.recycle();
        }
        if (mTempAdapter != null) {
            setAdapter(mTempAdapter);
            mTempAdapter = null;
        }
    }

    public Context getPopupContext() { return mPopupContext; }

    public void setPopupBackgroundDrawable(Drawable background) {
        if (!(mPopup instanceof DropdownPopup)) return;
        ((DropdownPopup) mPopup).setBackgroundDrawable(background);
    }

    public void setPopupBackgroundResource(int resId) { setPopupBackgroundDrawable(getPopupContext().getDrawable(resId)); }

    public Drawable getPopupBackground() { return mPopup.getBackground(); }

    public void setDropDownVerticalOffset(int pixels) { mPopup.setVerticalOffset(pixels); }

    public int getDropDownVerticalOffset() { return mPopup.getVerticalOffset(); }

    public void setDropDownHorizontalOffset(int pixels) { mPopup.setHorizontalOffset(pixels); }

    public int getDropDownHorizontalOffset() { return mPopup.getHorizontalOffset(); }

    public void setDropDownWidth(int pixels) {
        if (!(mPopup instanceof DropdownPopup)) return;
        mDropDownWidth = pixels;
    }

    public int getDropDownWidth() { return mDropDownWidth; }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (mDisableChildrenWhenDisabled) {
            final int count = getChildCount();
            for (int i = 0; i < count; i++) getChildAt(i).setEnabled(enabled);
        }
    }

    public void setGravity(int gravity) {
        if (mGravity != gravity) {
            if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == 0) gravity |= Gravity.START;
            mGravity = gravity;
            requestLayout();
        }
    }

    public int getGravity() { return mGravity; }

    @Override
    public void setAdapter(SpinnerAdapter adapter) {
        if (mPopup == null) {
            mTempAdapter = adapter;
            return;
        }
        super.setAdapter(adapter);
        mRecycler.clear();
        final int targetSdkVersion = getContext().getApplicationInfo().targetSdkVersion;
        if (targetSdkVersion >= 21 && adapter != null && adapter.getViewTypeCount() != 1) {
            throw new IllegalArgumentException("Spinner adapter view type count must be 1");
        }
        final Context popupContext = mPopupContext == null ? getContext() : mPopupContext;
        mPopup.setAdapter(new DropDownAdapter(adapter, popupContext.getTheme()));
    }

    @Override
    public int getBaseline() {
        View child = null;
        if (getChildCount() > 0) {
            child = getChildAt(0);
        } else if (mAdapter != null && mAdapter.getCount() > 0) {
            child = makeView(0, false);
            mRecycler.put(0, child);
        }
        if (child != null) {
            final int childBaseline = child.getBaseline();
            return childBaseline >= 0 ? child.getTop() + childBaseline : -1;
        }
        return -1;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mPopup != null && mPopup.isShowing()) mPopup.dismiss();
    }

    @Override
    public void setOnItemClickListener(OnItemClickListener l) {
        throw new RuntimeException("setOnItemClickListener cannot be used with a spinner.");
    }

    /** framework-internal (hidden in AOSP). */
    public void setOnItemClickListenerInt(OnItemClickListener l) { super.setOnItemClickListener(l); }

    @Override
    public boolean onTouchEvent(MotionEvent event) { return super.onTouchEvent(event); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mPopup != null && MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
            final int measuredWidth = getMeasuredWidth();
            setMeasuredDimension(Math.min(Math.max(measuredWidth, measureContentWidth(getAdapter(), getBackground())),
                    MeasureSpec.getSize(widthMeasureSpec)), getMeasuredHeight());
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mInLayout = true;
        layout(0, false);
        mInLayout = false;
    }

    @Override
    void layout(int delta, boolean animate) {
        int childrenLeft = mSpinnerPadding.left;
        int childrenWidth = getRight() - getLeft() - mSpinnerPadding.left - mSpinnerPadding.right;
        if (mDataChanged) handleDataChanged();
        if (mItemCount == 0) {
            resetList();
            return;
        }
        if (mNextSelectedPosition >= 0) setSelectedPositionInt(mNextSelectedPosition);
        recycleAllViews();
        removeAllViewsInLayout();
        mFirstPosition = mSelectedPosition;
        if (mAdapter != null) {
            View sel = makeView(mSelectedPosition, true);
            int width = sel.getMeasuredWidth();
            int selectedOffset = childrenLeft;
            final int layoutDirection = getLayoutDirection();
            final int absoluteGravity = Gravity.getAbsoluteGravity(mGravity, layoutDirection);
            switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                case Gravity.CENTER_HORIZONTAL:
                    selectedOffset = childrenLeft + (childrenWidth / 2) - (width / 2);
                    break;
                case Gravity.RIGHT:
                    selectedOffset = childrenLeft + childrenWidth - width;
                    break;
                default:
                    break;
            }
            sel.offsetLeftAndRight(selectedOffset);
        }
        mRecycler.clear();
        invalidate();
        checkSelectionChanged();
        mDataChanged = false;
        mNeedSync = false;
        setNextSelectedPositionInt(mSelectedPosition);
    }

    private View makeView(int position, boolean addChild) {
        View child;
        if (!mDataChanged) {
            child = mRecycler.get(position);
            if (child != null) {
                setUpChild(child, addChild);
                return child;
            }
        }
        child = mAdapter.getView(position, null, this);
        setUpChild(child, addChild);
        return child;
    }

    private void setUpChild(View child, boolean addChild) {
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp == null) lp = generateDefaultLayoutParams();
        addViewInLayout(child, 0, lp);
        child.setSelected(hasFocus());
        if (mDisableChildrenWhenDisabled) child.setEnabled(isEnabled());
        int childHeightSpec = ViewGroup.getChildMeasureSpec(mHeightMeasureSpec,
                mSpinnerPadding.top + mSpinnerPadding.bottom, lp.height);
        int childWidthSpec = ViewGroup.getChildMeasureSpec(mWidthMeasureSpec,
                mSpinnerPadding.left + mSpinnerPadding.right, lp.width);
        child.measure(childWidthSpec, childHeightSpec);
        int childLeft = 0;
        int childTop = mSpinnerPadding.top + ((getMeasuredHeight() - mSpinnerPadding.bottom - mSpinnerPadding.top
                - child.getMeasuredHeight()) / 2);
        int childBottom = childTop + child.getMeasuredHeight();
        int width = child.getMeasuredWidth();
        int childRight = childLeft + width;
        child.layout(childLeft, childTop, childRight, childBottom);
        if (!addChild) removeViewInLayout(child);
    }

    @Override
    public boolean performClick() {
        boolean handled = super.performClick();
        if (!handled) {
            handled = true;
            if (!mPopup.isShowing()) mPopup.show(getTextDirection(), getTextAlignment());
        }
        return handled;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        setSelection(which);
        dialog.dismiss();
    }

    @Override
    public CharSequence getAccessibilityClassName() { return Spinner.class.getName(); }

    public void setPrompt(CharSequence prompt) { mPopup.setPromptText(prompt); }

    public void setPromptId(int promptId) { setPrompt(getContext().getText(promptId)); }

    public CharSequence getPrompt() { return mPopup.getHintText(); }

    int measureContentWidth(SpinnerAdapter adapter, Drawable background) {
        if (adapter == null) return 0;
        int width = 0;
        View itemView = null;
        int itemType = 0;
        final int widthMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.UNSPECIFIED);
        final int heightMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.UNSPECIFIED);
        int start = Math.max(0, getSelectedItemPosition());
        final int end = Math.min(adapter.getCount(), start + MAX_ITEMS_MEASURED);
        final int count = end - start;
        start = Math.max(0, start - (MAX_ITEMS_MEASURED - count));
        for (int i = start; i < end; i++) {
            final int positionType = adapter.getItemViewType(i);
            if (positionType != itemType) {
                itemType = positionType;
                itemView = null;
            }
            itemView = adapter.getView(i, itemView, this);
            if (itemView.getLayoutParams() == null) {
                itemView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            itemView.measure(widthMeasureSpec, heightMeasureSpec);
            width = Math.max(width, itemView.getMeasuredWidth());
        }
        if (background != null) {
            background.getPadding(mTempRect);
            width += mTempRect.left + mTempRect.right;
        }
        return width;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final SavedState ss = new SavedState(super.onSaveInstanceState());
        ss.showDropdown = mPopup != null && mPopup.isShowing();
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        if (ss.showDropdown) {
            ViewTreeObserver vto = getViewTreeObserver();
            if (vto != null) {
                final OnGlobalLayoutListener listener = new OnGlobalLayoutListener() {
                    public void onGlobalLayout() {
                        if (!mPopup.isShowing()) mPopup.show(getTextDirection(), getTextAlignment());
                        final ViewTreeObserver vto = getViewTreeObserver();
                        if (vto != null) vto.removeOnGlobalLayoutListener(this);
                    }
                };
                vto.addOnGlobalLayoutListener(listener);
            }
        }
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
        return super.onResolvePointerIcon(event, pointerIndex);
    }

    static class SavedState extends AbsSpinner.SavedState {
        boolean showDropdown;

        SavedState(Parcelable superState) { super(superState); }

        private SavedState(Parcel in) {
            super(in);
            showDropdown = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeByte((byte) (showDropdown ? 1 : 0));
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) { return new SavedState(in); }
            public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }

    /** Presents the adapter's drop-down views to the popup. */
    private static class DropDownAdapter implements ListAdapter, SpinnerAdapter {
        private SpinnerAdapter mAdapter;
        private ListAdapter mListAdapter;

        DropDownAdapter(SpinnerAdapter adapter, Resources.Theme dropDownTheme) {
            mAdapter = adapter;
            if (adapter instanceof ListAdapter) mListAdapter = (ListAdapter) adapter;
            if (dropDownTheme != null && adapter instanceof ThemedSpinnerAdapter) {
                final ThemedSpinnerAdapter themedAdapter = (ThemedSpinnerAdapter) adapter;
                if (themedAdapter.getDropDownViewTheme() == null) themedAdapter.setDropDownViewTheme(dropDownTheme);
            }
        }

        public int getCount() { return mAdapter == null ? 0 : mAdapter.getCount(); }

        public Object getItem(int position) { return mAdapter == null ? null : mAdapter.getItem(position); }

        public long getItemId(int position) { return mAdapter == null ? -1 : mAdapter.getItemId(position); }

        public View getView(int position, View convertView, ViewGroup parent) {
            return getDropDownView(position, convertView, parent);
        }

        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return (mAdapter == null) ? null : mAdapter.getDropDownView(position, convertView, parent);
        }

        public boolean hasStableIds() { return mAdapter != null && mAdapter.hasStableIds(); }

        public void registerDataSetObserver(DataSetObserver observer) {
            if (mAdapter != null) mAdapter.registerDataSetObserver(observer);
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (mAdapter != null) mAdapter.unregisterDataSetObserver(observer);
        }

        public boolean areAllItemsEnabled() {
            final ListAdapter adapter = mListAdapter;
            return adapter == null || adapter.areAllItemsEnabled();
        }

        public boolean isEnabled(int position) {
            final ListAdapter adapter = mListAdapter;
            return adapter == null || adapter.isEnabled(position);
        }

        public int getItemViewType(int position) { return 0; }

        public int getViewTypeCount() { return 1; }

        public boolean isEmpty() { return getCount() == 0; }
    }

    private interface SpinnerPopup {
        void setAdapter(ListAdapter adapter);

        void show(int textDirection, int textAlignment);

        void dismiss();

        boolean isShowing();

        void setPromptText(CharSequence hintText);

        CharSequence getHintText();

        void setBackgroundDrawable(Drawable bg);

        void setVerticalOffset(int px);

        void setHorizontalOffset(int px);

        Drawable getBackground();

        int getVerticalOffset();

        int getHorizontalOffset();
    }

    private class DialogPopup implements SpinnerPopup, DialogInterface.OnClickListener {
        private AlertDialog mPopup;
        private ListAdapter mListAdapter;
        private CharSequence mPrompt;

        public void dismiss() {
            if (mPopup != null) {
                mPopup.dismiss();
                mPopup = null;
            }
        }

        public boolean isShowing() { return mPopup != null && mPopup.isShowing(); }

        public void setAdapter(ListAdapter adapter) { mListAdapter = adapter; }

        public void setPromptText(CharSequence hintText) { mPrompt = hintText; }

        public CharSequence getHintText() { return mPrompt; }

        public void show(int textDirection, int textAlignment) {
            if (mListAdapter == null) return;
            AlertDialog.Builder builder = new AlertDialog.Builder(getPopupContext());
            if (mPrompt != null) builder.setTitle(mPrompt);
            mPopup = builder.setSingleChoiceItems(mListAdapter, getSelectedItemPosition(), this).create();
            mPopup.show();
        }

        public void onClick(DialogInterface dialog, int which) {
            setSelection(which);
            if (getOnItemClickListener() != null) performItemClick(null, which, mListAdapter.getItemId(which));
            dismiss();
        }

        public void setBackgroundDrawable(Drawable bg) {}

        public void setVerticalOffset(int px) {}

        public void setHorizontalOffset(int px) {}

        public Drawable getBackground() { return null; }

        public int getVerticalOffset() { return 0; }

        public int getHorizontalOffset() { return 0; }
    }

    private class DropdownPopup extends ListPopupWindow implements SpinnerPopup {
        private CharSequence mHintText;
        private ListAdapter mAdapter;

        DropdownPopup(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
            setAnchorView(Spinner.this);
            setModal(true);
            setPromptPosition(POSITION_PROMPT_ABOVE);
            setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    Spinner.this.setSelection(position);
                    if (getOnItemClickListener() != null) Spinner.this.performItemClick(v, position, mAdapter.getItemId(position));
                    dismiss();
                }
            });
        }

        @Override
        public void setAdapter(ListAdapter adapter) {
            super.setAdapter(adapter);
            mAdapter = adapter;
        }

        public CharSequence getHintText() { return mHintText; }

        public void setPromptText(CharSequence hintText) { mHintText = hintText; }

        void computeContentWidth() {
            final Drawable background = getBackground();
            int hOffset = 0;
            if (background != null) {
                background.getPadding(mTempRect);
                hOffset = isLayoutRtl() ? mTempRect.right : -mTempRect.left;
            } else {
                mTempRect.left = mTempRect.right = 0;
            }
            final int spinnerPaddingLeft = Spinner.this.getPaddingLeft();
            final int spinnerPaddingRight = Spinner.this.getPaddingRight();
            final int width = Spinner.this.getWidth();
            if (mDropDownWidth == ViewGroup.LayoutParams.WRAP_CONTENT) {
                int contentWidth = measureContentWidth((SpinnerAdapter) mAdapter, getBackground());
                final int contentWidthLimit = getContext().getResources().getDisplayMetrics().widthPixels
                        - mTempRect.left - mTempRect.right;
                if (contentWidth > contentWidthLimit) contentWidth = contentWidthLimit;
                setContentWidth(Math.max(contentWidth, width - spinnerPaddingLeft - spinnerPaddingRight));
            } else if (mDropDownWidth == ViewGroup.LayoutParams.MATCH_PARENT) {
                setContentWidth(width - spinnerPaddingLeft - spinnerPaddingRight);
            } else {
                setContentWidth(mDropDownWidth);
            }
            if (isLayoutRtl()) hOffset += width - spinnerPaddingRight - getWidth();
            else hOffset += spinnerPaddingLeft;
            setHorizontalOffset(hOffset);
        }

        public void show(int textDirection, int textAlignment) {
            final boolean wasShowing = isShowing();
            computeContentWidth();
            setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
            super.show();
            final ListView listView = getListView();
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            listView.setTextDirection(textDirection);
            listView.setTextAlignment(textAlignment);
            setSelection(Spinner.this.getSelectedItemPosition());
            if (wasShowing) return;
            // Hide when the anchor goes away; follow it when it moves.
            final ViewTreeObserver vto = getViewTreeObserver();
            if (vto != null) {
                final OnGlobalLayoutListener layoutListener = new OnGlobalLayoutListener() {
                    public void onGlobalLayout() {
                        if (!Spinner.this.isShown()) {
                            dismiss();
                        } else {
                            computeContentWidth();
                            DropdownPopup.super.show();
                        }
                    }
                };
                vto.addOnGlobalLayoutListener(layoutListener);
                setOnDismissListener(new PopupWindow.OnDismissListener() {
                    public void onDismiss() {
                        final ViewTreeObserver vto = getViewTreeObserver();
                        if (vto != null) vto.removeOnGlobalLayoutListener(layoutListener);
                    }
                });
            }
        }
    }
}
