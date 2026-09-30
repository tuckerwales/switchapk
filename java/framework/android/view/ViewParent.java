package android.view;

/** Subset of the real ViewParent. Missing methods are stubbed by the VM. */
public interface ViewParent {
    void requestLayout();
    boolean isLayoutRequested();
    ViewParent getParent();
    void requestDisallowInterceptTouchEvent(boolean disallowIntercept);
}
