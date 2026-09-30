package android.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;

/**
 * Minimal view used by the first activity loop. Measure, layout, draw and
 * input dispatch are real; the rest of the widget system is TODO(WS1).
 */
public class View implements Drawable.Callback {
    public static final int NO_ID = -1;
    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;
    public static final int NOT_FOCUSABLE = 0;
    public static final int FOCUSABLE = 1;
    public static final int FOCUSABLE_AUTO = 16;
    public static final int LAYOUT_DIRECTION_LTR = 0;
    public static final int LAYOUT_DIRECTION_RTL = 1;
    public static final int LAYOUT_DIRECTION_INHERIT = 2;
    public static final int LAYOUT_DIRECTION_LOCALE = 3;

    static final int PFLAG_FORCE_LAYOUT = 0x1;

    protected Context mContext;
    int mId = NO_ID;
    int mLeft, mTop, mRight, mBottom;
    int mMeasuredWidth, mMeasuredHeight;
    int mPrivateFlags;
    int mVisibility = VISIBLE;
    int mPaddingLeft, mPaddingTop, mPaddingRight, mPaddingBottom;
    int mMinWidth, mMinHeight;
    boolean mEnabled = true;
    boolean mClickable;
    Object mTag;
    Drawable mBackground;
    ViewGroup.LayoutParams mLayoutParams;
    ViewParent mParent;
    View mParentView;
    ViewRootImpl mViewRoot;
    OnClickListener mOnClickListener;
    OnKeyListener mOnKeyListener;

    private static Handler sHandler;

    public interface OnClickListener {
        void onClick(View v);
    }

    public interface OnKeyListener {
        boolean onKey(View v, int keyCode, KeyEvent event);
    }

    public View(Context context) { this(context, null, 0, 0); }
    public View(Context context, AttributeSet attrs) { this(context, attrs, 0, 0); }
    public View(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public View(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        mContext = context;
    }

    public final Context getContext() { return mContext; }
    public void setId(int id) { mId = id; }
    public int getId() { return mId; }
    public Object getTag() { return mTag; }
    public void setTag(Object tag) { mTag = tag; }
    public void setEnabled(boolean enabled) { mEnabled = enabled; }
    public boolean isEnabled() { return mEnabled; }
    public void setClickable(boolean clickable) { mClickable = clickable; }
    public boolean isClickable() { return mClickable; }
    public int getVisibility() { return mVisibility; }

    public void setVisibility(int visibility) {
        if (mVisibility == visibility) return;
        mVisibility = visibility;
        if (mParentView != null) mParentView.requestLayout();
        invalidate();
    }

    public void setPadding(int left, int top, int right, int bottom) {
        mPaddingLeft = left;
        mPaddingTop = top;
        mPaddingRight = right;
        mPaddingBottom = bottom;
        requestLayout();
    }

    public int getPaddingLeft() { return mPaddingLeft; }
    public int getPaddingTop() { return mPaddingTop; }
    public int getPaddingRight() { return mPaddingRight; }
    public int getPaddingBottom() { return mPaddingBottom; }
    public void setMinimumWidth(int minWidth) { mMinWidth = minWidth; }
    public void setMinimumHeight(int minHeight) { mMinHeight = minHeight; }

    public ViewGroup.LayoutParams getLayoutParams() { return mLayoutParams; }

    public void setLayoutParams(ViewGroup.LayoutParams params) {
        mLayoutParams = params;
        requestLayout();
    }

    public final int getLeft() { return mLeft; }
    public final int getTop() { return mTop; }
    public final int getRight() { return mRight; }
    public final int getBottom() { return mBottom; }
    public final int getWidth() { return mRight - mLeft; }
    public final int getHeight() { return mBottom - mTop; }
    public final int getMeasuredWidth() { return mMeasuredWidth & MEASURED_SIZE_MASK; }
    public final int getMeasuredHeight() { return mMeasuredHeight & MEASURED_SIZE_MASK; }
    public final int getMeasuredWidthAndState() { return mMeasuredWidth; }
    public final int getMeasuredHeightAndState() { return mMeasuredHeight; }
    public final int getMeasuredState() {
        return (mMeasuredWidth & MEASURED_STATE_MASK)
                | ((mMeasuredHeight >> MEASURED_HEIGHT_STATE_SHIFT) & MEASURED_STATE_MASK);
    }

    public static final int MEASURED_SIZE_MASK = 0x00ffffff;
    public static final int MEASURED_STATE_MASK = 0xff000000;
    public static final int MEASURED_HEIGHT_STATE_SHIFT = 16;
    public static final int MEASURED_STATE_TOO_SMALL = 0x01000000;

    public final void measure(int widthMeasureSpec, int heightMeasureSpec) {
        onMeasure(widthMeasureSpec, heightMeasureSpec);
        mPrivateFlags &= ~PFLAG_FORCE_LAYOUT;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    }

    protected final void setMeasuredDimension(int measuredWidth, int measuredHeight) {
        mMeasuredWidth = measuredWidth;
        mMeasuredHeight = measuredHeight;
    }

    public static int getDefaultSize(int size, int measureSpec) {
        int result = size;
        int mode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        if (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY) result = specSize;
        return result;
    }

    protected int getSuggestedMinimumWidth() {
        return mBackground == null ? mMinWidth : Math.max(mMinWidth, mBackground.getMinimumWidth());
    }

    protected int getSuggestedMinimumHeight() {
        return mBackground == null ? mMinHeight : Math.max(mMinHeight, mBackground.getMinimumHeight());
    }

    public void layout(int l, int t, int r, int b) {
        mLeft = l;
        mTop = t;
        mRight = r;
        mBottom = b;
        onLayout(true, l, t, r, b);
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {}

    public void draw(Canvas canvas) {
        if (mVisibility != VISIBLE) return;
        int save = canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        if (mBackground != null) {
            mBackground.setBounds(0, 0, getWidth(), getHeight());
            mBackground.draw(canvas);
        }
        onDraw(canvas);
        dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    protected void onDraw(Canvas canvas) {}
    protected void dispatchDraw(Canvas canvas) {}

    public void setBackground(Drawable background) {
        if (mBackground == background) return;
        if (mBackground != null) mBackground.setCallback(null);
        mBackground = background;
        if (background != null) background.setCallback(this);
        invalidate();
    }

    public void setBackgroundDrawable(Drawable background) { setBackground(background); }
    public void setBackgroundColor(int color) { setBackground(new ColorDrawable(color)); }
    public Drawable getBackground() { return mBackground; }

    public void invalidate() { invalidate(true); }
    public void invalidate(Rect dirty) { invalidate(); }
    public void invalidate(int l, int t, int r, int b) { invalidate(); }

    private void invalidate(boolean fromDraw) {
        ViewRootImpl root = getViewRootImpl();
        if (root != null) root.markDirty();
    }

    public void requestLayout() {
        mPrivateFlags |= PFLAG_FORCE_LAYOUT;
        if (mParent != null && !mParent.isLayoutRequested()) mParent.requestLayout();
        else {
            ViewRootImpl root = getViewRootImpl();
            if (root != null) root.requestLayout();
        }
    }

    public ViewParent getParent() { return mParent; }
    public boolean isLayoutRequested() { return (mPrivateFlags & PFLAG_FORCE_LAYOUT) != 0; }

    void assignParent(ViewParent parent) {
        mParent = parent;
        mParentView = parent instanceof View ? (View) parent : null;
    }

    ViewRootImpl getViewRootImpl() {
        View v = this;
        while (v != null) {
            if (v.mViewRoot != null) return v.mViewRoot;
            v = v.mParentView;
        }
        return null;
    }

    public void setOnClickListener(OnClickListener l) {
        mOnClickListener = l;
        if (l != null) mClickable = true;
    }

    public void setOnKeyListener(OnKeyListener l) { mOnKeyListener = l; }

    public boolean performClick() {
        if (mOnClickListener == null) return false;
        mOnClickListener.onClick(this);
        return true;
    }

    public boolean dispatchTouchEvent(MotionEvent event) { return onTouchEvent(event); }

    public boolean onTouchEvent(MotionEvent event) {
        if (!mClickable) return false;
        if (event.getAction() == MotionEvent.ACTION_UP) performClick();
        return true;
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mOnKeyListener != null && mOnKeyListener.onKey(this, event.getKeyCode(), event)) return true;
        if (event.getAction() == KeyEvent.ACTION_DOWN) return onKeyDown(event.getKeyCode(), event);
        if (event.getAction() == KeyEvent.ACTION_UP) return onKeyUp(event.getKeyCode(), event);
        return false;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) { return false; }
    public boolean onKeyUp(int keyCode, KeyEvent event) { return false; }

    @SuppressWarnings("unchecked")
    public final <T extends View> T findViewById(int id) { return (T) findViewTraversal(id); }

    View findViewTraversal(int id) { return id == mId && id != NO_ID ? this : null; }

    public boolean post(Runnable action) {
        Handler h = handler();
        return h != null && h.post(action);
    }

    public boolean postDelayed(Runnable action, long delayMillis) {
        Handler h = handler();
        return h != null && h.postDelayed(action, delayMillis);
    }

    public boolean removeCallbacks(Runnable action) {
        Handler h = handler();
        if (h != null) h.removeCallbacks(action);
        return true;
    }

    private static Handler handler() {
        if (sHandler == null) {
            Looper looper = Looper.getMainLooper();
            if (looper == null) return null;
            sHandler = new Handler(looper);
        }
        return sHandler;
    }

    public void invalidateDrawable(Drawable who) { invalidate(); }

    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        postDelayed(what, when - SystemClock.uptimeMillis());
    }

    public void unscheduleDrawable(Drawable who, Runnable what) { removeCallbacks(what); }

    public static class MeasureSpec {
        public static final int UNSPECIFIED = 0;
        public static final int EXACTLY = 1073741824;
        public static final int AT_MOST = -2147483648;
        private static final int MODE_SHIFT = 30;
        private static final int MODE_MASK = 0x3 << MODE_SHIFT;

        public MeasureSpec() {}

        public static int makeMeasureSpec(int size, int mode) {
            return (size & ~MODE_MASK) | (mode & MODE_MASK);
        }

        public static int getMode(int measureSpec) { return measureSpec & MODE_MASK; }
        public static int getSize(int measureSpec) { return measureSpec & ~MODE_MASK; }
    }
}
