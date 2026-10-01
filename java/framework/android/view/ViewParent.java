package android.view;

public interface ViewParent {
    void requestLayout();
    boolean isLayoutRequested();
    void requestTransparentRegion(View p0);
    default void onDescendantInvalidated(View p0, View p1) {  }
    void invalidateChild(View p0, android.graphics.Rect p1);
    ViewParent invalidateChildInParent(int[] p0, android.graphics.Rect p1);
    ViewParent getParent();
    void requestChildFocus(View p0, View p1);
    void recomputeViewAttributes(View p0);
    void clearChildFocus(View p0);
    boolean getChildVisibleRect(View p0, android.graphics.Rect p1, android.graphics.Point p2);
    View focusSearch(View p0, int p1);
    View keyboardNavigationClusterSearch(View p0, int p1);
    void bringChildToFront(View p0);
    void focusableViewAvailable(View p0);
    boolean showContextMenuForChild(View p0);
    boolean showContextMenuForChild(View p0, float p1, float p2);
    void createContextMenu(ContextMenu p0);
    ActionMode startActionModeForChild(View p0, ActionMode.Callback p1);
    ActionMode startActionModeForChild(View p0, ActionMode.Callback p1, int p2);
    void childDrawableStateChanged(View p0);
    void requestDisallowInterceptTouchEvent(boolean p0);
    boolean requestChildRectangleOnScreen(View p0, android.graphics.Rect p1, boolean p2);
    boolean requestSendAccessibilityEvent(View p0, android.view.accessibility.AccessibilityEvent p1);
    void childHasTransientStateChanged(View p0, boolean p1);
    void requestFitSystemWindows();
    ViewParent getParentForAccessibility();
    void notifySubtreeAccessibilityStateChanged(View p0, View p1, int p2);
    boolean canResolveLayoutDirection();
    boolean isLayoutDirectionResolved();
    int getLayoutDirection();
    boolean canResolveTextDirection();
    boolean isTextDirectionResolved();
    int getTextDirection();
    boolean canResolveTextAlignment();
    boolean isTextAlignmentResolved();
    int getTextAlignment();
    boolean onStartNestedScroll(View p0, View p1, int p2);
    void onNestedScrollAccepted(View p0, View p1, int p2);
    void onStopNestedScroll(View p0);
    void onNestedScroll(View p0, int p1, int p2, int p3, int p4);
    void onNestedPreScroll(View p0, int p1, int p2, int[] p3);
    boolean onNestedFling(View p0, float p1, float p2, boolean p3);
    boolean onNestedPreFling(View p0, float p1, float p2);
    boolean onNestedPrePerformAccessibilityAction(View p0, int p1, android.os.Bundle p2);
}
