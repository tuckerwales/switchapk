package android.view;

import java.util.ArrayList;

/** framework-internal. Z-ordered list of view roots. One full-screen window is composited for now. TODO(WS1) */
public final class WindowManagerGlobal {
    private static final WindowManagerGlobal sInstance = new WindowManagerGlobal();
    private final ArrayList<ViewRootImpl> mRoots = new ArrayList<ViewRootImpl>();

    private WindowManagerGlobal() {}

    public static WindowManagerGlobal getInstance() { return sInstance; }

    public void addView(View view, ViewGroup.LayoutParams params) {
        if (!(params instanceof WindowManager.LayoutParams)) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            if (params != null) {
                lp.width = params.width;
                lp.height = params.height;
            }
            params = lp;
        }
        view.setLayoutParams(params);
        ViewRootImpl root = new ViewRootImpl();
        root.setView(view);
        mRoots.add(root);
    }

    public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
        ViewRootImpl root = find(view);
        if (root == null) return;
        view.setLayoutParams(params);
        root.requestLayout();
    }

    public void removeView(View view) {
        ViewRootImpl root = find(view);
        if (root != null) {
            root.detach();
            mRoots.remove(root);
        }
    }

    boolean dispatchTouch(MotionEvent event) {
        ViewRootImpl root = top();
        return root != null && root.dispatchTouch(event);
    }

    boolean dispatchKey(KeyEvent event) {
        ViewRootImpl root = top();
        if (root == null) return false;
        if (root.dispatchKey(event)) return true;
        int mapped = 0;
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A) mapped = KeyEvent.KEYCODE_DPAD_CENTER;
        else if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) mapped = KeyEvent.KEYCODE_BACK;
        if (mapped == 0) return false;
        KeyEvent alt = new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(), mapped,
                event.getRepeatCount(), event.getMetaState());
        return root.dispatchKey(alt);
    }

    /** framework-internal. ActivityThread asks every window to traverse again. */
    public void scheduleAll() {
        for (int i = 0; i < mRoots.size(); i++) mRoots.get(i).requestLayout();
    }

    /** framework-internal. Presents the top window. Multi-window composite is TODO(WS1). */
    static void present(int[] pixels, int w, int h) {
        if (pixels == null || w <= 0 || h <= 0) return;
        nPresent(pixels, w, h);
    }

    private ViewRootImpl top() { return mRoots.isEmpty() ? null : mRoots.get(mRoots.size() - 1); }

    private ViewRootImpl find(View view) {
        for (int i = 0; i < mRoots.size(); i++) {
            if (mRoots.get(i).getView() == view) return mRoots.get(i);
        }
        return null;
    }

    private static native void nPresent(int[] pixels, int w, int h);
}
