package android.view;

import android.view.inputmethod.InputMethodManager;

/**
 * framework-internal. Drains one platform wake into touch, key, text and lifecycle.
 * Kinds match platform.h: 0 none, 1 touch, 2 key, 3 joystick, 4 quit, 5 focus, 6 resize, 7 sensor, 8 text.
 */
public final class PlatformInput {
    public static final int NONE = 0;
    public static final int TOUCH = 1;
    public static final int KEY = 2;
    public static final int JOYSTICK = 3;
    public static final int QUIT = 4;
    public static final int FOCUS = 5;
    public static final int RESIZE = 6;
    public static final int SENSOR = 7;
    public static final int TEXT = 8;

    public interface Sink {
        void onQuit();
        void onFocus(boolean gained);
        void onResize(int width, int height, int dpi);
    }

    private PlatformInput() {}

    public static void drain(Sink sink) {
        int[] iv = new int[4];
        float[] fv = new float[8];
        long[] tv = new long[1];
        for (;;) {
            int kind = nNextEvent(iv, fv, tv);
            if (kind == NONE) return;
            long ms = tv[0] / 1000000L;
            switch (kind) {
                case TOUCH: {
                    MotionEvent ev = MotionEvent.obtain(ms, ms, iv[0], fv[0], fv[1], 0, iv[1]);
                    WindowManagerGlobal.getInstance().dispatchTouch(ev);
                    break;
                }
                case KEY: {
                    KeyEvent ev = new KeyEvent(ms, ms, iv[0], iv[1], iv[3], iv[2]);
                    WindowManagerGlobal.getInstance().dispatchKey(ev);
                    break;
                }
                case QUIT:
                    if (sink != null) sink.onQuit();
                    return;
                case FOCUS:
                    if (sink != null) sink.onFocus(iv[0] != 0);
                    break;
                case RESIZE:
                    if (sink != null) sink.onResize(iv[0], iv[1], iv[2]);
                    break;
                case TEXT:
                    InputMethodManager.deliverTextResult(iv[0], nTakeText());
                    break;
                default:
                    break;
            }
        }
    }

    private static native int nNextEvent(int[] ints, float[] floats, long[] timeNs);
    private static native String nTakeText();
}
