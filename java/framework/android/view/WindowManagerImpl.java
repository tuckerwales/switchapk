package android.view;

/** framework-internal. The single process-wide window manager. */
public class WindowManagerImpl implements WindowManager {
    private static final WindowManagerImpl sDefault = new WindowManagerImpl();

    private WindowManagerImpl() {}

    public static WindowManagerImpl getDefault() { return sDefault; }

    public Display getDefaultDisplay() { return Display.defaultDisplay(); }

    public void addView(View view, ViewGroup.LayoutParams params) {
        WindowManagerGlobal.getInstance().addView(view, params);
    }

    public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
        WindowManagerGlobal.getInstance().updateViewLayout(view, params);
    }

    public void removeView(View view) { WindowManagerGlobal.getInstance().removeView(view); }

    public void removeViewImmediate(View view) { removeView(view); }
}
