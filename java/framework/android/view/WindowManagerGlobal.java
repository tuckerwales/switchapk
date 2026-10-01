package android.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseIntArray;
import java.util.ArrayList;

/**
 * framework-internal. The window list of the process (there is no window
 * server): windows are z-ordered by type, input is routed to them (touch to
 * the window under the finger or the topmost touch-modal one, keys to the
 * topmost focusable one) and their pixels are composited into one screen
 * buffer that is presented once per frame.
 */
public final class WindowManagerGlobal {
    private static final String TAG = "WindowManagerGlobal";
    private static final WindowManagerGlobal sInstance = new WindowManagerGlobal();

    private final ArrayList<ViewRootImpl> mRoots = new ArrayList<ViewRootImpl>();
    private ViewRootImpl mFocusedRoot;
    private ViewRootImpl mTouchRoot;
    private final SparseIntArray mFallbacks = new SparseIntArray();
    private boolean mCommitScheduled;
    private Bitmap mScreen;
    private Canvas mScreenCanvas;
    private final Paint mDimPaint = new Paint();
    private final Runnable mCommit = new Runnable() {
        public void run() {
            mCommitScheduled = false;
            composeAndPresent();
        }
    };

    private WindowManagerGlobal() {}

    public static WindowManagerGlobal getInstance() { return sInstance; }

    // ---------------------------------------------------------------- window list

    public void addView(View view, ViewGroup.LayoutParams params) {
        if (view == null) throw new IllegalArgumentException("view must not be null");
        if (!(params instanceof WindowManager.LayoutParams)) {
            throw new IllegalArgumentException("Params must be WindowManager.LayoutParams");
        }
        if (find(view) != null) {
            throw new IllegalStateException("View " + view + " has already been added to the window manager.");
        }
        final WindowManager.LayoutParams wparams = (WindowManager.LayoutParams) params;
        view.setLayoutParams(wparams);
        ViewRootImpl root = new ViewRootImpl(view.getContext());
        int layer = layerOf(wparams.type);
        int index = mRoots.size();
        while (index > 0 && layerOf(mRoots.get(index - 1).getWindowAttributes().type) > layer) index--;
        mRoots.add(index, root);
        root.setView(view, wparams);
        updateFocusedWindow();
    }

    public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
        if (!(params instanceof WindowManager.LayoutParams)) {
            throw new IllegalArgumentException("Params must be WindowManager.LayoutParams");
        }
        ViewRootImpl root = find(view);
        if (root == null) throw new IllegalArgumentException("View=" + view + " not attached to window manager");
        view.setLayoutParams(params);
        root.setLayoutParams((WindowManager.LayoutParams) params);
        updateFocusedWindow();
        scheduleCommit();
    }

    public void removeView(View view, boolean immediate) { removeView(view); }

    public void removeView(View view) {
        ViewRootImpl root = find(view);
        if (root == null) throw new IllegalArgumentException("View=" + view + " not attached to window manager");
        mRoots.remove(root);
        if (mTouchRoot == root) mTouchRoot = null;
        if (mFocusedRoot == root) mFocusedRoot = null;
        root.die();
        updateFocusedWindow();
        scheduleCommit();
    }

    /** framework-internal. True when the view is the root of a window. */
    public boolean isAdded(View view) { return find(view) != null; }

    /** framework-internal. Number of windows (tests and debugging). */
    public int getWindowCount() { return mRoots.size(); }

    private static int layerOf(int type) {
        if (type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                && type <= WindowManager.LayoutParams.LAST_APPLICATION_WINDOW) {
            return 2;
        }
        if (type >= WindowManager.LayoutParams.FIRST_SUB_WINDOW && type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
            return 3;
        }
        switch (type) {
            case WindowManager.LayoutParams.TYPE_WALLPAPER: return 1;
            case WindowManager.LayoutParams.TYPE_INPUT_METHOD:
            case WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG: return 15;
            case WindowManager.LayoutParams.TYPE_TOAST: return 20;
            case WindowManager.LayoutParams.TYPE_SYSTEM_ERROR: return 25;
            default: return 10;
        }
    }

    private ViewRootImpl find(View view) {
        for (int i = 0; i < mRoots.size(); i++) {
            if (mRoots.get(i).getView() == view) return mRoots.get(i);
        }
        return null;
    }

    /** framework-internal. Focused window: topmost window that accepts focus. */
    void updateFocusedWindow() {
        ViewRootImpl newFocus = null;
        for (int i = mRoots.size() - 1; i >= 0; i--) {
            ViewRootImpl r = mRoots.get(i);
            int flags = r.getWindowAttributes().flags;
            if ((flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) {
                newFocus = r;
                break;
            }
        }
        if (newFocus == mFocusedRoot) return;
        ViewRootImpl old = mFocusedRoot;
        mFocusedRoot = newFocus;
        if (old != null) old.windowFocusChanged(false);
        if (newFocus != null && mPlatformFocus) newFocus.windowFocusChanged(true);
    }

    private boolean mPlatformFocus = true;

    /** framework-internal. The application lost or regained focus (HOME, applet suspend). */
    public void setPlatformFocus(boolean focused) {
        if (mPlatformFocus == focused) return;
        mPlatformFocus = focused;
        if (mFocusedRoot != null) mFocusedRoot.windowFocusChanged(focused);
    }

    void setTouchModeAll(boolean inTouchMode) {
        for (int i = 0; i < mRoots.size(); i++) mRoots.get(i).applyTouchMode(inTouchMode);
    }

    /** framework-internal. Display size or density changed. */
    public void onDisplayChanged() {
        mScreen = null;
        for (int i = 0; i < mRoots.size(); i++) mRoots.get(i).onDisplayChanged();
    }

    /** framework-internal. Kept for ActivityThread: relayout and redraw all windows. */
    public void scheduleAll() { onDisplayChanged(); }

    boolean showContextMenuForChild(ViewRootImpl root, View originalView, float x, float y) {
        // TODO(WS4): context menus need dialogs (ContextMenuBuilder); not shown yet.
        return false;
    }

    // ---------------------------------------------------------------- input routing

    private static boolean contains(ViewRootImpl r, float x, float y) {
        return x >= r.mWinX && y >= r.mWinY && x < r.mWinX + r.mWidth && y < r.mWinY + r.mHeight;
    }

    /** framework-internal. Touch event in screen coordinates. */
    public boolean dispatchTouch(MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mTouchRoot = null;
            float x = event.getX();
            float y = event.getY();
            for (int i = mRoots.size() - 1; i >= 0; i--) {
                ViewRootImpl r = mRoots.get(i);
                int flags = r.getWindowAttributes().flags;
                if ((flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0 || r.mWidth <= 0) continue;
                boolean inside = contains(r, x, y);
                boolean modal = (flags & (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)) == 0;
                if (inside || modal) {
                    mTouchRoot = r;
                    break;
                }
                if ((flags & WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH) != 0) {
                    MotionEvent outside = MotionEvent.obtain(event);
                    outside.setAction(MotionEvent.ACTION_OUTSIDE);
                    outside.offsetLocation(-r.mWinX, -r.mWinY);
                    r.dispatchPointer(outside);
                }
            }
        }
        ViewRootImpl r = mTouchRoot;
        if (r == null) return false;
        MotionEvent local = MotionEvent.obtain(event);
        local.offsetLocation(-r.mWinX, -r.mWinY);
        boolean handled = r.dispatchPointer(local);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) mTouchRoot = null;
        return handled;
    }

    /** framework-internal. Joystick motion goes to the focused window. */
    public boolean dispatchGenericMotion(MotionEvent event) {
        ViewRootImpl r = mFocusedRoot;
        return r != null && r.dispatchGenericMotion(event);
    }

    /** framework-internal. Key from the platform, delivered to the focused window. */
    public boolean dispatchKey(KeyEvent event) {
        ViewRootImpl r = mFocusedRoot;
        if (r == null) return false;
        return dispatchKeyToWindow(r, event);
    }

    /**
     * framework-internal. Dispatches a key, then (like Android's Generic.kcm fallbacks) re-dispatches
     * an unhandled BUTTON_A as DPAD_CENTER, an unhandled BUTTON_B as BACK and an unhandled
     * BUTTON_START (the + button) as MENU.
     */
    boolean dispatchKeyToWindow(ViewRootImpl r, KeyEvent event) {
        final int keyCode = event.getKeyCode();
        final int action = event.getAction();
        boolean handled = r.dispatchKey(event);
        int fallback = fallbackFor(keyCode);
        if (fallback == 0) return handled;
        if (action == KeyEvent.ACTION_DOWN) {
            if (handled) {
                cancelFallback(r, event, keyCode);
                return true;
            }
            if (mFallbacks.indexOfKey(keyCode) < 0 || event.getRepeatCount() == 0) mFallbacks.put(keyCode, fallback);
            KeyEvent fb = new KeyEvent(event.getDownTime(), event.getEventTime(), action, fallback,
                    event.getRepeatCount(), event.getMetaState(), event.getDeviceId(), event.getScanCode(),
                    event.getFlags() | KeyEvent.FLAG_FALLBACK, event.getSource());
            return r.dispatchKey(fb);
        } else if (action == KeyEvent.ACTION_UP) {
            int idx = mFallbacks.indexOfKey(keyCode);
            if (idx < 0) return handled;
            int fb = mFallbacks.valueAt(idx);
            mFallbacks.removeAt(idx);
            int flags = event.getFlags() | KeyEvent.FLAG_FALLBACK;
            if (handled) flags |= KeyEvent.FLAG_CANCELED;
            KeyEvent up = new KeyEvent(event.getDownTime(), event.getEventTime(), action, fb, 0,
                    event.getMetaState(), event.getDeviceId(), event.getScanCode(), flags, event.getSource());
            return r.dispatchKey(up) || handled;
        }
        return handled;
    }

    private void cancelFallback(ViewRootImpl r, KeyEvent event, int keyCode) {
        int idx = mFallbacks.indexOfKey(keyCode);
        if (idx < 0) return;
        int fb = mFallbacks.valueAt(idx);
        mFallbacks.removeAt(idx);
        KeyEvent up = new KeyEvent(event.getDownTime(), event.getEventTime(), KeyEvent.ACTION_UP, fb, 0,
                event.getMetaState(), event.getDeviceId(), event.getScanCode(),
                KeyEvent.FLAG_FALLBACK | KeyEvent.FLAG_CANCELED, event.getSource());
        r.dispatchKey(up);
    }

    private static int fallbackFor(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return KeyEvent.KEYCODE_DPAD_CENTER;
            case KeyEvent.KEYCODE_BUTTON_B: return KeyEvent.KEYCODE_BACK;
            case KeyEvent.KEYCODE_BUTTON_START: return KeyEvent.KEYCODE_MENU;
            default: return 0;
        }
    }

    // ---------------------------------------------------------------- composition

    void windowDrawn(ViewRootImpl root) { scheduleCommit(); }

    private void scheduleCommit() {
        if (mCommitScheduled) return;
        mCommitScheduled = true;
        Choreographer.getInstance().postCallback(Choreographer.CALLBACK_COMMIT, mCommit, null);
    }

    private void composeAndPresent() {
        Display display = Display.defaultDisplay();
        final int w = display.getWidth();
        final int h = display.getHeight();
        if (w <= 0 || h <= 0) return;
        // a single opaque full-screen window is presented without copying
        if (mRoots.size() == 1) {
            ViewRootImpl only = mRoots.get(0);
            if (only.mSurface != null && only.mDrawnOnce && only.mWinX == 0 && only.mWinY == 0 && only.mWidth == w
                    && only.mHeight == h && only.isOpaque()) {
                present(only.mSurface.getPixelArray(), w, h);
                return;
            }
        }
        if (mScreen == null || mScreen.getWidth() != w || mScreen.getHeight() != h) {
            mScreen = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            mScreenCanvas = new Canvas(mScreen);
        }
        Canvas c = mScreenCanvas;
        c.drawColor(0xff000000, android.graphics.PorterDuff.Mode.SRC);
        for (int i = 0; i < mRoots.size(); i++) {
            ViewRootImpl r = mRoots.get(i);
            if (r.mSurface == null || !r.mDrawnOnce) continue;
            WindowManager.LayoutParams lp = r.getWindowAttributes();
            if ((lp.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
                int a = (int) (Math.max(0f, Math.min(1f, lp.dimAmount)) * 255);
                mDimPaint.setColor(a << 24);
                c.drawRect(0, 0, w, h, mDimPaint);
            }
            Paint p = null;
            if (lp.alpha < 1f) {
                p = new Paint();
                p.setAlpha((int) (lp.alpha * 255));
            }
            c.drawBitmap(r.mSurface, r.mWinX, r.mWinY, p);
        }
        present(mScreen.getPixelArray(), w, h);
    }

    /** framework-internal. Presents a screen-sized ARGB buffer. */
    static void present(int[] pixels, int w, int h) {
        if (pixels == null || w <= 0 || h <= 0) return;
        nPresent(pixels, w, h);
    }

    private static native void nPresent(int[] pixels, int w, int h);
}
