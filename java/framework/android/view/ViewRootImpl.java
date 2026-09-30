package android.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;

/** framework-internal. One window: measure, layout, draw, present. */
public final class ViewRootImpl implements ViewParent {
    private View mView;
    private boolean mTraversalScheduled;
    private boolean mDirty;
    private boolean mLayoutRequested;
    private final Choreographer.FrameCallback mFrame = new Choreographer.FrameCallback() {
        public void doFrame(long frameTimeNanos) {
            mTraversalScheduled = false;
            performTraversals();
        }
    };

    public void setView(View view) {
        mView = view;
        view.mViewRoot = this;
        view.assignParent(this);
        mDirty = true;
        mLayoutRequested = true;
        scheduleTraversals();
    }

    public View getView() { return mView; }

    public void detach() {
        if (mView != null) {
            mView.mViewRoot = null;
            mView.assignParent(null);
            mView = null;
        }
    }

    public void markDirty() {
        mDirty = true;
        scheduleTraversals();
    }

    public void requestLayout() {
        mLayoutRequested = true;
        if (mView != null) mView.mPrivateFlags |= View.PFLAG_FORCE_LAYOUT;
        scheduleTraversals();
    }

    public boolean isLayoutRequested() { return mLayoutRequested; }

    public ViewParent getParent() { return null; }

    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {}

    boolean dispatchTouch(MotionEvent event) { return mView != null && mView.dispatchTouchEvent(event); }

    boolean dispatchKey(KeyEvent event) { return mView != null && mView.dispatchKeyEvent(event); }

    private void scheduleTraversals() {
        if (mTraversalScheduled || mView == null) return;
        mTraversalScheduled = true;
        Choreographer.getInstance().postFrameCallback(mFrame);
    }

    private void performTraversals() {
        if (mView == null || (!mDirty && !mLayoutRequested)) return;
        mDirty = false;
        mLayoutRequested = false;
        Display display = Display.defaultDisplay();
        int w = display.getWidth();
        int h = display.getHeight();
        if (w <= 0 || h <= 0) return;
        int ws = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY);
        int hs = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY);
        mView.measure(ws, hs);
        mView.layout(0, 0, mView.getMeasuredWidth(), mView.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        mView.draw(canvas);
        WindowManagerGlobal.present(bitmap.getPixelArray(), w, h);
    }
}
