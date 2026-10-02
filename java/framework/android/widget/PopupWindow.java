package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.view.WindowManager;
import com.android.internal.util.InternalRes;
import java.lang.ref.WeakReference;

/**
 * Port of AOSP PopupWindow: a TYPE_APPLICATION_PANEL window holding the content
 * in a background view and a decor view that dismisses on BACK and on touches
 * outside. Drop-downs are placed below the anchor, or above it when there is
 * no room, and follow the anchor when it scrolls. Enter/exit transitions and
 * window animations are not run (WS5).
 */
public class PopupWindow {
    public static final int INPUT_METHOD_FROM_FOCUSABLE = 0;
    public static final int INPUT_METHOD_NEEDED = 1;
    public static final int INPUT_METHOD_NOT_NEEDED = 2;

    private static final int DEFAULT_ANCHORED_GRAVITY = Gravity.TOP | Gravity.START;
    private static final int ANIMATION_STYLE_DEFAULT = -1;
    private static final int[] ABOVE_ANCHOR_STATE_SET = new int[] {android.R.attr.state_above_anchor};

    private final int[] mTmpDrawingLocation = new int[2];
    private final int[] mTmpScreenLocation = new int[2];
    private final int[] mTmpAppLocation = new int[2];
    private final Rect mTempRect = new Rect();

    private Context mContext;
    private WindowManager mWindowManager;

    private boolean mIsShowing;
    private boolean mIsTransitioningToDismiss;
    private boolean mIsDropdown;

    private PopupDecorView mDecorView;
    private View mBackgroundView;
    private View mContentView;

    private boolean mFocusable;
    private int mInputMethodMode = INPUT_METHOD_FROM_FOCUSABLE;
    private int mSoftInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
    private boolean mTouchable = true;
    private boolean mOutsideTouchable = false;
    private boolean mClippingEnabled = true;
    private int mSplitTouchEnabled = -1;
    private boolean mLayoutInScreen;
    private boolean mClipToScreen;
    private boolean mAllowScrollingAnchorParent = true;
    private boolean mLayoutInsetDecor = false;
    private boolean mNotTouchModal;
    private boolean mAttachedInDecor = true;
    private boolean mAttachedInDecorSet = false;

    private OnTouchListener mTouchInterceptor;

    private int mWidthMode;
    private int mWidth = ViewGroup.LayoutParams.WRAP_CONTENT;
    private int mLastWidth;
    private int mHeightMode;
    private int mHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
    private int mLastHeight;

    private float mElevation;
    private Drawable mBackground;
    private Drawable mAboveAnchorBackgroundDrawable;
    private Drawable mBelowAnchorBackgroundDrawable;

    private boolean mAboveAnchor;
    private int mWindowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;

    private OnDismissListener mOnDismissListener;
    private boolean mIgnoreCheekPress = false;

    private int mAnimationStyle = ANIMATION_STYLE_DEFAULT;
    private int mGravity = Gravity.NO_GRAVITY;
    private Rect mEpicenterBounds;

    private WeakReference<View> mAnchor;
    private WeakReference<View> mAnchorRoot;
    private boolean mIsAnchorRootAttached;
    private int mAnchorXoff;
    private int mAnchorYoff;
    private int mAnchoredGravity;
    private boolean mOverlapAnchor;
    private boolean mPopupViewInitialLayoutDirectionInherited;

    private final View.OnAttachStateChangeListener mOnAnchorDetachedListener = new View.OnAttachStateChangeListener() {
        public void onViewAttachedToWindow(View v) { alignToAnchor(); }

        public void onViewDetachedFromWindow(View v) {}
    };

    private final View.OnAttachStateChangeListener mOnAnchorRootDetachedListener =
            new View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(View v) {}

                public void onViewDetachedFromWindow(View v) {
                    mIsAnchorRootAttached = false;
                    dismiss();
                }
            };

    private final OnScrollChangedListener mOnScrollChangedListener = new OnScrollChangedListener() {
        public void onScrollChanged() { alignToAnchor(); }
    };

    private final View.OnLayoutChangeListener mOnLayoutChangeListener = new View.OnLayoutChangeListener() {
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop,
                int oldRight, int oldBottom) {
            alignToAnchor();
        }
    };

    public interface OnDismissListener {
        void onDismiss();
    }

    public PopupWindow(Context context) { this(context, null); }

    public PopupWindow(Context context, AttributeSet attrs) { this(context, attrs, android.R.attr.popupWindowStyle); }

    public PopupWindow(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public PopupWindow(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.popupBackground, android.R.attr.popupElevation, android.R.attr.overlapAnchor,
                android.R.attr.popupAnimationStyle}, defStyleAttr, defStyleRes);
        final Drawable bg = a.getDrawable(0);
        mElevation = a.getDimension(1, 0);
        mOverlapAnchor = a.getBoolean(2, false);
        if (a.hasValue(3)) {
            final int animStyle = a.getResourceId(3, 0);
            mAnimationStyle = animStyle == InternalRes.style("Animation.PopupWindow") ? ANIMATION_STYLE_DEFAULT : animStyle;
        } else {
            mAnimationStyle = ANIMATION_STYLE_DEFAULT;
        }
        a.recycle();
        setBackgroundDrawable(bg);
    }

    public PopupWindow() { this(null, 0, 0); }

    public PopupWindow(View contentView) { this(contentView, 0, 0); }

    public PopupWindow(int width, int height) { this(null, width, height); }

    public PopupWindow(View contentView, int width, int height) { this(contentView, width, height, false); }

    public PopupWindow(View contentView, int width, int height, boolean focusable) {
        if (contentView != null) {
            mContext = contentView.getContext();
            mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        }
        setContentView(contentView);
        setWidth(width);
        setHeight(height);
        setFocusable(focusable);
    }

    // Enter and exit transitions need android.transition, which is not in the tree.
    // Visibility changes immediately until then.

    public Rect getEpicenterBounds() { return mEpicenterBounds != null ? new Rect(mEpicenterBounds) : null; }

    public void setEpicenterBounds(Rect bounds) { mEpicenterBounds = bounds != null ? new Rect(bounds) : null; }

    public Drawable getBackground() { return mBackground; }

    public void setBackgroundDrawable(Drawable background) {
        mBackground = background;
        // A StateListDrawable with an above-anchor state is split into its two states, as in AOSP.
        if (mBackground instanceof android.graphics.drawable.StateListDrawable) {
            android.graphics.drawable.StateListDrawable stateList =
                    (android.graphics.drawable.StateListDrawable) mBackground;
            int aboveAnchorStateIndex = stateList.findStateDrawableIndex(ABOVE_ANCHOR_STATE_SET);
            final int count = stateList.getStateCount();
            int belowAnchorStateIndex = -1;
            for (int i = 0; i < count; i++) {
                if (i != aboveAnchorStateIndex) {
                    belowAnchorStateIndex = i;
                    break;
                }
            }
            if (aboveAnchorStateIndex != -1 && belowAnchorStateIndex != -1) {
                mAboveAnchorBackgroundDrawable = stateList.getStateDrawable(aboveAnchorStateIndex);
                mBelowAnchorBackgroundDrawable = stateList.getStateDrawable(belowAnchorStateIndex);
            } else {
                mBelowAnchorBackgroundDrawable = null;
                mAboveAnchorBackgroundDrawable = null;
            }
        }
    }

    public float getElevation() { return mElevation; }

    public void setElevation(float elevation) { mElevation = elevation; }

    public int getAnimationStyle() { return mAnimationStyle; }

    public void setIgnoreCheekPress() { mIgnoreCheekPress = true; }

    public void setAnimationStyle(int animationStyle) { mAnimationStyle = animationStyle; }

    public View getContentView() { return mContentView; }

    public void setContentView(View contentView) {
        if (isShowing()) return;
        mContentView = contentView;
        if (mContext == null && mContentView != null) mContext = mContentView.getContext();
        if (mWindowManager == null && mContentView != null) {
            mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        }
        if (mContext != null && !mAttachedInDecorSet) {
            setAttachedInDecor(mContext.getApplicationInfo().targetSdkVersion >= 22);
        }
    }

    public void setTouchInterceptor(OnTouchListener l) { mTouchInterceptor = l; }

    public boolean isFocusable() { return mFocusable; }

    public void setFocusable(boolean focusable) { mFocusable = focusable; }

    public int getInputMethodMode() { return mInputMethodMode; }

    public void setInputMethodMode(int mode) { mInputMethodMode = mode; }

    public void setSoftInputMode(int mode) { mSoftInputMode = mode; }

    public int getSoftInputMode() { return mSoftInputMode; }

    public boolean isTouchable() { return mTouchable; }

    public void setTouchable(boolean touchable) { mTouchable = touchable; }

    public boolean isOutsideTouchable() { return mOutsideTouchable; }

    public void setOutsideTouchable(boolean touchable) { mOutsideTouchable = touchable; }

    public boolean isClippingEnabled() { return mClippingEnabled; }

    public void setClippingEnabled(boolean enabled) { mClippingEnabled = enabled; }

    public boolean isClippedToScreen() { return mClipToScreen; }

    public void setIsClippedToScreen(boolean enabled) { mClipToScreen = enabled; }

    public boolean isSplitTouchEnabled() {
        if (mSplitTouchEnabled < 0 && mContext != null) {
            return mContext.getApplicationInfo().targetSdkVersion >= 11;
        }
        return mSplitTouchEnabled == 1;
    }

    public void setSplitTouchEnabled(boolean enabled) { mSplitTouchEnabled = enabled ? 1 : 0; }

    /** framework-internal (hidden in AOSP). */
    void setAllowScrollingAnchorParent(boolean enabled) { mAllowScrollingAnchorParent = enabled; }

    public boolean isLaidOutInScreen() { return mLayoutInScreen; }

    public void setIsLaidOutInScreen(boolean enabled) { mLayoutInScreen = enabled; }

    public boolean isAttachedInDecor() { return mAttachedInDecor; }

    public void setAttachedInDecor(boolean enabled) {
        mAttachedInDecor = enabled;
        mAttachedInDecorSet = true;
    }

    public void setWindowLayoutType(int layoutType) { mWindowLayoutType = layoutType; }

    public int getWindowLayoutType() { return mWindowLayoutType; }

    public boolean isTouchModal() { return !mNotTouchModal; }

    public void setTouchModal(boolean touchModal) { mNotTouchModal = !touchModal; }

    @Deprecated
    public void setWindowLayoutMode(int widthSpec, int heightSpec) {
        mWidthMode = widthSpec;
        mHeightMode = heightSpec;
    }

    public int getHeight() { return mHeight; }

    public void setHeight(int height) { mHeight = height; }

    public int getWidth() { return mWidth; }

    public void setWidth(int width) { mWidth = width; }

    public void setOverlapAnchor(boolean overlapAnchor) { mOverlapAnchor = overlapAnchor; }

    public boolean getOverlapAnchor() { return mOverlapAnchor; }

    public boolean isShowing() { return mIsShowing; }

    public void showAtLocation(View parent, int gravity, int x, int y) {
        showAtLocation(parent.getWindowToken(), gravity, x, y);
    }

    /** framework-internal (hidden in AOSP). */
    public void showAtLocation(IBinder token, int gravity, int x, int y) {
        if (isShowing() || mContentView == null) return;
        detachFromAnchor();
        mIsShowing = true;
        mIsDropdown = false;
        mGravity = gravity;
        final WindowManager.LayoutParams p = createPopupLayoutParams(token);
        preparePopup(p);
        p.x = x;
        p.y = y;
        invokePopup(p);
    }

    public void showAsDropDown(View anchor) { showAsDropDown(anchor, 0, 0); }

    public void showAsDropDown(View anchor, int xoff, int yoff) { showAsDropDown(anchor, xoff, yoff, DEFAULT_ANCHORED_GRAVITY); }

    public void showAsDropDown(View anchor, int xoff, int yoff, int gravity) {
        if (isShowing() || mContentView == null) return;
        attachToAnchor(anchor, xoff, yoff, gravity);
        mIsShowing = true;
        mIsDropdown = true;
        final WindowManager.LayoutParams p = createPopupLayoutParams(anchor.getApplicationWindowToken());
        preparePopup(p);
        final boolean aboveAnchor = findDropDownPosition(anchor, p, xoff, yoff, p.width, p.height, gravity,
                mAllowScrollingAnchorParent);
        updateAboveAnchor(aboveAnchor);
        invokePopup(p);
    }

    private void updateAboveAnchor(boolean aboveAnchor) {
        if (aboveAnchor != mAboveAnchor) {
            mAboveAnchor = aboveAnchor;
            if (mBackground != null && mBackgroundView != null) {
                if (mAboveAnchorBackgroundDrawable != null) {
                    if (mAboveAnchor) mBackgroundView.setBackground(mAboveAnchorBackgroundDrawable);
                    else mBackgroundView.setBackground(mBelowAnchorBackgroundDrawable);
                } else {
                    mBackgroundView.refreshDrawableState();
                }
            }
        }
    }

    public boolean isAboveAnchor() { return mAboveAnchor; }

    private void preparePopup(WindowManager.LayoutParams p) {
        if (mContentView == null || mContext == null || mWindowManager == null) {
            throw new IllegalStateException("You must specify a valid content view by calling setContentView()"
                    + " before attempting to show the popup.");
        }
        if (mDecorView != null) mDecorView.removeAllViews();
        if (mBackground != null) {
            mBackgroundView = createBackgroundView(mContentView);
            mBackgroundView.setBackground(mBackground);
        } else {
            mBackgroundView = mContentView;
        }
        mDecorView = createDecorView(mBackgroundView);
        mBackgroundView.setElevation(mElevation);
        mPopupViewInitialLayoutDirectionInherited =
                (mContentView.getRawLayoutDirection() == View.LAYOUT_DIRECTION_INHERIT);
    }

    private PopupBackgroundView createBackgroundView(View contentView) {
        final ViewGroup.LayoutParams layoutParams = mContentView.getLayoutParams();
        final int height = layoutParams != null && layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT
                ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
        final PopupBackgroundView backgroundView = new PopupBackgroundView(mContext);
        final FrameLayout.LayoutParams listParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        backgroundView.addView(contentView, listParams);
        return backgroundView;
    }

    private PopupDecorView createDecorView(View contentView) {
        final ViewGroup.LayoutParams layoutParams = mContentView.getLayoutParams();
        final int height = layoutParams != null && layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT
                ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
        final PopupDecorView decorView = new PopupDecorView(mContext);
        decorView.addView(contentView, ViewGroup.LayoutParams.MATCH_PARENT, height);
        decorView.setClipChildren(false);
        decorView.setClipToPadding(false);
        return decorView;
    }

    private void invokePopup(WindowManager.LayoutParams p) {
        if (mContext != null) p.packageName = mContext.getPackageName();
        final PopupDecorView decorView = mDecorView;
        decorView.setFitsSystemWindows(mLayoutInsetDecor);
        setLayoutDirectionFromAnchor();
        mWindowManager.addView(decorView, p);
    }

    private void setLayoutDirectionFromAnchor() {
        if (mAnchor != null) {
            View anchor = mAnchor.get();
            if (anchor != null && mPopupViewInitialLayoutDirectionInherited) {
                mDecorView.setLayoutDirection(anchor.getLayoutDirection());
            }
        }
    }

    private int computeGravity() {
        int gravity = mGravity == Gravity.NO_GRAVITY ? Gravity.START | Gravity.TOP : mGravity;
        if (mIsDropdown && (mClipToScreen || mClippingEnabled)) gravity |= Gravity.DISPLAY_CLIP_VERTICAL;
        return gravity;
    }

    /** framework-internal (protected in AOSP). */
    protected final WindowManager.LayoutParams createPopupLayoutParams(IBinder token) {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams();
        p.gravity = computeGravity();
        p.flags = computeFlags(p.flags);
        p.type = mWindowLayoutType;
        p.token = token;
        p.softInputMode = mSoftInputMode;
        p.windowAnimations = computeAnimationResource();
        p.format = mBackground != null ? mBackground.getOpacity() : PixelFormat.TRANSLUCENT;
        if (mHeightMode < 0) p.height = mLastHeight = mHeightMode;
        else p.height = mLastHeight = mHeight;
        if (mWidthMode < 0) p.width = mLastWidth = mWidthMode;
        else p.width = mLastWidth = mWidth;
        p.setTitle("PopupWindow:" + Integer.toHexString(hashCode()));
        return p;
    }

    private int computeFlags(int curFlags) {
        curFlags &= ~(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH);
        if (mIgnoreCheekPress) curFlags |= WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES;
        if (!mFocusable) {
            curFlags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            if (mInputMethodMode == INPUT_METHOD_NEEDED) curFlags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        } else if (mInputMethodMode == INPUT_METHOD_NOT_NEEDED) {
            curFlags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
        if (!mTouchable) curFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        if (mOutsideTouchable) curFlags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        if (!mClippingEnabled || mClipToScreen) curFlags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (isSplitTouchEnabled()) curFlags |= WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;
        if (mLayoutInScreen) curFlags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (mLayoutInsetDecor) curFlags |= WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        if (mNotTouchModal) curFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        return curFlags;
    }

    private int computeAnimationResource() {
        if (mAnimationStyle == ANIMATION_STYLE_DEFAULT) {
            if (mIsDropdown) {
                return mAboveAnchor ? InternalRes.style("Animation.DropDownUp") : InternalRes.style("Animation.DropDownDown");
            }
            return 0;
        }
        return mAnimationStyle;
    }

    /** framework-internal (protected in AOSP). Places the popup below the anchor, or above when it does not fit. */
    protected boolean findDropDownPosition(View anchor, WindowManager.LayoutParams outParams, int xOffset,
            int yOffset, int width, int height, int gravity, boolean allowScroll) {
        final int anchorHeight = anchor.getHeight();
        final int anchorWidth = anchor.getWidth();
        if (mOverlapAnchor) yOffset -= anchorHeight;

        final int[] appScreenLocation = mTmpAppLocation;
        final View appRootView = getAppRootView(anchor);
        appRootView.getLocationOnScreen(appScreenLocation);
        final int[] screenLocation = mTmpScreenLocation;
        anchor.getLocationOnScreen(screenLocation);
        final int[] drawingLocation = mTmpDrawingLocation;
        drawingLocation[0] = screenLocation[0] - appScreenLocation[0];
        drawingLocation[1] = screenLocation[1] - appScreenLocation[1];
        outParams.x = drawingLocation[0] + xOffset;
        outParams.y = drawingLocation[1] + anchorHeight + yOffset;

        final Rect displayFrame = new Rect();
        appRootView.getWindowVisibleDisplayFrame(displayFrame);
        if (width == ViewGroup.LayoutParams.MATCH_PARENT) width = displayFrame.right - displayFrame.left;
        if (height == ViewGroup.LayoutParams.MATCH_PARENT) height = displayFrame.bottom - displayFrame.top;
        outParams.gravity = computeGravity();
        outParams.width = width;
        outParams.height = height;

        final int hgrav = Gravity.getAbsoluteGravity(gravity, anchor.getLayoutDirection())
                & Gravity.HORIZONTAL_GRAVITY_MASK;
        if (hgrav == Gravity.RIGHT) outParams.x -= width - anchorWidth;

        final boolean fitsVertical = tryFitVertical(outParams, yOffset, height, anchorHeight, drawingLocation[1],
                screenLocation[1], displayFrame.top, displayFrame.bottom, false);
        final boolean fitsHorizontal = tryFitHorizontal(outParams, xOffset, width, anchorWidth, drawingLocation[0],
                screenLocation[0], displayFrame.left, displayFrame.right, false);
        if (!fitsVertical || !fitsHorizontal) {
            tryFitVertical(outParams, yOffset, height, anchorHeight, drawingLocation[1], screenLocation[1],
                    displayFrame.top, displayFrame.bottom, mClipToScreen);
            tryFitHorizontal(outParams, xOffset, width, anchorWidth, drawingLocation[0], screenLocation[0],
                    displayFrame.left, displayFrame.right, mClipToScreen);
        }
        return outParams.y < drawingLocation[1];
    }

    private boolean tryFitVertical(WindowManager.LayoutParams outParams, int yOffset, int height, int anchorHeight,
            int drawingLocationY, int screenLocationY, int displayFrameTop, int displayFrameBottom,
            boolean allowResize) {
        final int winOffsetY = screenLocationY - drawingLocationY;
        final int anchorTopInScreen = outParams.y + winOffsetY;
        final int spaceBelow = displayFrameBottom - anchorTopInScreen;
        if (anchorTopInScreen >= displayFrameTop && height <= spaceBelow) return true;
        final int spaceAbove = anchorTopInScreen - anchorHeight - displayFrameTop;
        if (height <= spaceAbove) {
            if (mOverlapAnchor) yOffset += anchorHeight;
            outParams.y = drawingLocationY - height + yOffset;
            return true;
        }
        return positionInDisplayVertical(outParams, height, drawingLocationY, screenLocationY, displayFrameTop,
                displayFrameBottom, allowResize);
    }

    private boolean positionInDisplayVertical(WindowManager.LayoutParams outParams, int height,
            int drawingLocationY, int screenLocationY, int displayFrameTop, int displayFrameBottom,
            boolean canResize) {
        boolean fitsInDisplay = true;
        final int winOffsetY = screenLocationY - drawingLocationY;
        outParams.y += winOffsetY;
        outParams.height = height;
        final int bottom = outParams.y + height;
        if (bottom > displayFrameBottom) outParams.y -= bottom - displayFrameBottom;
        if (outParams.y < displayFrameTop) {
            outParams.y = displayFrameTop;
            final int displayFrameHeight = displayFrameBottom - displayFrameTop;
            if (canResize && height > displayFrameHeight) outParams.height = displayFrameHeight;
            else fitsInDisplay = false;
        }
        outParams.y -= winOffsetY;
        return fitsInDisplay;
    }

    private boolean tryFitHorizontal(WindowManager.LayoutParams outParams, int xOffset, int width, int anchorWidth,
            int drawingLocationX, int screenLocationX, int displayFrameLeft, int displayFrameRight,
            boolean allowResize) {
        final int winOffsetX = screenLocationX - drawingLocationX;
        final int anchorLeftInScreen = outParams.x + winOffsetX;
        final int spaceRight = displayFrameRight - anchorLeftInScreen;
        if (anchorLeftInScreen >= displayFrameLeft && width <= spaceRight) return true;
        return positionInDisplayHorizontal(outParams, width, drawingLocationX, screenLocationX, displayFrameLeft,
                displayFrameRight, allowResize);
    }

    private boolean positionInDisplayHorizontal(WindowManager.LayoutParams outParams, int width,
            int drawingLocationX, int screenLocationX, int displayFrameLeft, int displayFrameRight,
            boolean canResize) {
        boolean fitsInDisplay = true;
        final int winOffsetX = screenLocationX - drawingLocationX;
        outParams.x += winOffsetX;
        final int right = outParams.x + width;
        if (right > displayFrameRight) outParams.x -= right - displayFrameRight;
        if (outParams.x < displayFrameLeft) {
            outParams.x = displayFrameLeft;
            final int displayFrameWidth = displayFrameRight - displayFrameLeft;
            if (canResize && width > displayFrameWidth) outParams.width = displayFrameWidth;
            else fitsInDisplay = false;
        }
        outParams.x -= winOffsetX;
        return fitsInDisplay;
    }

    public int getMaxAvailableHeight(View anchor) { return getMaxAvailableHeight(anchor, 0); }

    public int getMaxAvailableHeight(View anchor, int yOffset) { return getMaxAvailableHeight(anchor, yOffset, false); }

    public int getMaxAvailableHeight(View anchor, int yOffset, boolean ignoreBottomDecorations) {
        final Rect displayFrame = new Rect();
        final View appView = getAppRootView(anchor);
        appView.getWindowVisibleDisplayFrame(displayFrame);
        final int[] anchorPos = mTmpDrawingLocation;
        anchor.getLocationOnScreen(anchorPos);
        final int bottomEdge = displayFrame.bottom;
        final int distanceToBottom;
        if (mOverlapAnchor) distanceToBottom = bottomEdge - anchorPos[1] - yOffset;
        else distanceToBottom = bottomEdge - (anchorPos[1] + anchor.getHeight()) - yOffset;
        final int distanceToTop = anchorPos[1] - displayFrame.top + yOffset;
        int returnedHeight = Math.max(distanceToBottom, distanceToTop);
        if (mBackground != null) {
            mBackground.getPadding(mTempRect);
            returnedHeight -= mTempRect.top + mTempRect.bottom;
        }
        return returnedHeight;
    }

    public void dismiss() {
        if (!isShowing() || mIsTransitioningToDismiss) return;
        final PopupDecorView decorView = mDecorView;
        final View contentView = mContentView;
        final ViewGroup contentHolder;
        final android.view.ViewParent contentParent = contentView.getParent();
        contentHolder = contentParent instanceof ViewGroup ? (ViewGroup) contentParent : null;
        mIsShowing = false;
        mIsTransitioningToDismiss = true;
        detachFromAnchor();
        dismissImmediate(decorView, contentHolder, contentView);
        if (mOnDismissListener != null) mOnDismissListener.onDismiss();
    }

    private void dismissImmediate(View decorView, ViewGroup contentHolder, View contentView) {
        if (decorView.getParent() != null || mWindowManager instanceof android.view.WindowManagerImpl) {
            try {
                mWindowManager.removeViewImmediate(decorView);
            } catch (IllegalArgumentException ignored) {
                // Already removed with its activity.
            }
        }
        if (contentHolder != null) contentHolder.removeView(contentView);
        mDecorView = null;
        mBackgroundView = null;
        mIsTransitioningToDismiss = false;
    }

    public void setOnDismissListener(OnDismissListener onDismissListener) { mOnDismissListener = onDismissListener; }

    public void update() {
        if (!isShowing() || mContentView == null) return;
        final WindowManager.LayoutParams p = getDecorViewLayoutParams();
        boolean update = false;
        final int newAnim = computeAnimationResource();
        if (newAnim != p.windowAnimations) {
            p.windowAnimations = newAnim;
            update = true;
        }
        final int newFlags = computeFlags(p.flags);
        if (newFlags != p.flags) {
            p.flags = newFlags;
            update = true;
        }
        final int newGravity = computeGravity();
        if (newGravity != p.gravity) {
            p.gravity = newGravity;
            update = true;
        }
        if (update) {
            setLayoutDirectionFromAnchor();
            mWindowManager.updateViewLayout(mDecorView, p);
        }
    }

    public void update(int width, int height) {
        final WindowManager.LayoutParams p = getDecorViewLayoutParams();
        update(p.x, p.y, width, height, false);
    }

    public void update(int x, int y, int width, int height) { update(x, y, width, height, false); }

    public void update(int x, int y, int width, int height, boolean force) {
        if (width >= 0) {
            mLastWidth = width;
            setWidth(width);
        }
        if (height >= 0) {
            mLastHeight = height;
            setHeight(height);
        }
        if (!isShowing() || mContentView == null) return;
        final WindowManager.LayoutParams p = getDecorViewLayoutParams();
        boolean update = force;
        final int finalWidth = mWidthMode < 0 ? mWidthMode : mLastWidth;
        if (width != -1 && p.width != finalWidth) {
            p.width = mLastWidth = finalWidth;
            update = true;
        }
        final int finalHeight = mHeightMode < 0 ? mHeightMode : mLastHeight;
        if (height != -1 && p.height != finalHeight) {
            p.height = mLastHeight = finalHeight;
            update = true;
        }
        if (p.x != x) {
            p.x = x;
            update = true;
        }
        if (p.y != y) {
            p.y = y;
            update = true;
        }
        final int newAnim = computeAnimationResource();
        if (newAnim != p.windowAnimations) {
            p.windowAnimations = newAnim;
            update = true;
        }
        final int newFlags = computeFlags(p.flags);
        if (newFlags != p.flags) {
            p.flags = newFlags;
            update = true;
        }
        final int newGravity = computeGravity();
        if (newGravity != p.gravity) {
            p.gravity = newGravity;
            update = true;
        }
        if (update) {
            setLayoutDirectionFromAnchor();
            mWindowManager.updateViewLayout(mDecorView, p);
        }
    }

    public void update(View anchor, int width, int height) { update(anchor, false, 0, 0, width, height); }

    public void update(View anchor, int xoff, int yoff, int width, int height) {
        update(anchor, true, xoff, yoff, width, height);
    }

    private void update(View anchor, boolean updateLocation, int xoff, int yoff, int width, int height) {
        if (!isShowing() || mContentView == null) return;
        final WeakReference<View> oldAnchor = mAnchor;
        final int gravity = mAnchoredGravity;
        final boolean needsUpdate = updateLocation && (mAnchorXoff != xoff || mAnchorYoff != yoff);
        if (oldAnchor == null || oldAnchor.get() != anchor || (needsUpdate && !mIsDropdown)) {
            attachToAnchor(anchor, xoff, yoff, gravity);
        } else if (needsUpdate) {
            mAnchorXoff = xoff;
            mAnchorYoff = yoff;
        }
        final WindowManager.LayoutParams p = getDecorViewLayoutParams();
        final int oldGravity = p.gravity;
        final int oldWidth = p.width;
        final int oldHeight = p.height;
        final int oldX = p.x;
        final int oldY = p.y;
        if (width < 0) width = mWidth;
        if (height < 0) height = mHeight;
        final boolean aboveAnchor = findDropDownPosition(anchor, p, mAnchorXoff, mAnchorYoff, width, height, gravity,
                mAllowScrollingAnchorParent);
        updateAboveAnchor(aboveAnchor);
        final boolean paramsChanged = oldGravity != p.gravity || oldX != p.x || oldY != p.y || oldWidth != p.width
                || oldHeight != p.height;
        final int newWidth = width < 0 ? width : p.width;
        final int newHeight = height < 0 ? height : p.height;
        update(p.x, p.y, newWidth, newHeight, paramsChanged);
    }

    /** framework-internal (protected in AOSP). */
    protected final WindowManager.LayoutParams getDecorViewLayoutParams() {
        return (WindowManager.LayoutParams) mDecorView.getLayoutParams();
    }

    /** framework-internal (protected in AOSP). */
    protected void detachFromAnchor() {
        final View anchor = mAnchor != null ? mAnchor.get() : null;
        if (anchor != null) {
            final ViewTreeObserver vto = anchor.getViewTreeObserver();
            vto.removeOnScrollChangedListener(mOnScrollChangedListener);
            anchor.removeOnAttachStateChangeListener(mOnAnchorDetachedListener);
        }
        final View anchorRoot = mAnchorRoot != null ? mAnchorRoot.get() : null;
        if (anchorRoot != null) {
            anchorRoot.removeOnAttachStateChangeListener(mOnAnchorRootDetachedListener);
            anchorRoot.removeOnLayoutChangeListener(mOnLayoutChangeListener);
        }
        mAnchor = null;
        mAnchorRoot = null;
        mIsAnchorRootAttached = false;
    }

    /** framework-internal (protected in AOSP). */
    protected void attachToAnchor(View anchor, int xoff, int yoff, int gravity) {
        detachFromAnchor();
        final ViewTreeObserver vto = anchor.getViewTreeObserver();
        if (vto != null) vto.addOnScrollChangedListener(mOnScrollChangedListener);
        anchor.addOnAttachStateChangeListener(mOnAnchorDetachedListener);
        final View anchorRoot = anchor.getRootView();
        anchorRoot.addOnAttachStateChangeListener(mOnAnchorRootDetachedListener);
        anchorRoot.addOnLayoutChangeListener(mOnLayoutChangeListener);
        mAnchor = new WeakReference<View>(anchor);
        mAnchorRoot = new WeakReference<View>(anchorRoot);
        mIsAnchorRootAttached = anchorRoot.isAttachedToWindow();
        mAnchorXoff = xoff;
        mAnchorYoff = yoff;
        mAnchoredGravity = gravity;
    }

    private void alignToAnchor() {
        final View anchor = mAnchor != null ? mAnchor.get() : null;
        if (anchor != null && anchor.isAttachedToWindow() && hasDecorView()) {
            final WindowManager.LayoutParams p = getDecorViewLayoutParams();
            updateAboveAnchor(findDropDownPosition(anchor, p, mAnchorXoff, mAnchorYoff, p.width, p.height,
                    mAnchoredGravity, false));
            update(p.x, p.y, -1, -1, true);
        }
    }

    private boolean hasDecorView() { return mDecorView != null; }

    private View getAppRootView(View anchor) { return anchor.getRootView(); }

    private class PopupDecorView extends FrameLayout {
        PopupDecorView(Context context) { super(context); }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (getKeyDispatcherState() == null) return super.dispatchKeyEvent(event);
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    final KeyEvent.DispatcherState state = getKeyDispatcherState();
                    if (state != null) state.startTracking(event, this);
                    return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    final KeyEvent.DispatcherState state = getKeyDispatcherState();
                    if (state != null && state.isTracking(event) && !event.isCanceled()) {
                        dismiss();
                        return true;
                    }
                }
                return super.dispatchKeyEvent(event);
            }
            return super.dispatchKeyEvent(event);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (mTouchInterceptor != null && mTouchInterceptor.onTouch(this, ev)) return true;
            return super.dispatchTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int x = (int) event.getX();
            final int y = (int) event.getY();
            if ((event.getAction() == MotionEvent.ACTION_DOWN)
                    && ((x < 0) || (x >= getWidth()) || (y < 0) || (y >= getHeight()))) {
                dismiss();
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                dismiss();
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    private class PopupBackgroundView extends FrameLayout {
        PopupBackgroundView(Context context) { super(context); }

        @Override
        protected int[] onCreateDrawableState(int extraSpace) {
            if (mAboveAnchor) {
                final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
                View.mergeDrawableStates(drawableState, ABOVE_ANCHOR_STATE_SET);
                return drawableState;
            }
            return super.onCreateDrawableState(extraSpace);
        }
    }
}
