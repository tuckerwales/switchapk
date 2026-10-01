package android.view;

public class SoundEffectConstants {
    public static final int CLICK = 0;
    public static final int NAVIGATION_DOWN = 4;
    public static final int NAVIGATION_LEFT = 1;
    public static final int NAVIGATION_REPEAT_DOWN = 8;
    public static final int NAVIGATION_REPEAT_LEFT = 5;
    public static final int NAVIGATION_REPEAT_RIGHT = 7;
    public static final int NAVIGATION_REPEAT_UP = 6;
    public static final int NAVIGATION_RIGHT = 3;
    public static final int NAVIGATION_UP = 2;

    private SoundEffectConstants() {}

    public static int getContantForFocusDirection(int direction) { return getConstantForFocusDirection(direction, false); }

    public static int getConstantForFocusDirection(int direction, boolean repeating) {
        switch (direction) {
            case View.FOCUS_RIGHT: return repeating ? NAVIGATION_REPEAT_RIGHT : NAVIGATION_RIGHT;
            case View.FOCUS_FORWARD:
            case View.FOCUS_DOWN: return repeating ? NAVIGATION_REPEAT_DOWN : NAVIGATION_DOWN;
            case View.FOCUS_LEFT: return repeating ? NAVIGATION_REPEAT_LEFT : NAVIGATION_LEFT;
            case View.FOCUS_BACKWARD:
            case View.FOCUS_UP: return repeating ? NAVIGATION_REPEAT_UP : NAVIGATION_UP;
        }
        throw new IllegalArgumentException("direction must be one of {FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT, "
                + "FOCUS_FORWARD, FOCUS_BACKWARD}.");
    }
}
