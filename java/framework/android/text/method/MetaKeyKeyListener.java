package android.text.method;

import android.text.Editable;
import android.text.NoCopySpan;
import android.text.Spannable;
import android.text.Spanned;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;

/** Tracks sticky meta keys (shift, alt, sym, selecting) as spans on the text (port of AOSP). */
public abstract class MetaKeyKeyListener {
    public static final int META_SHIFT_ON = KeyEvent.META_SHIFT_ON;
    public static final int META_ALT_ON = KeyEvent.META_ALT_ON;
    public static final int META_SYM_ON = KeyEvent.META_SYM_ON;
    /** Hidden AOSP constant. */
    public static final int META_SELECTING = KeyEvent.META_SELECTING;

    private static final int LOCKED_SHIFT = 8;
    public static final int META_CAP_LOCKED = KeyEvent.META_SHIFT_ON << LOCKED_SHIFT;
    public static final int META_ALT_LOCKED = KeyEvent.META_ALT_ON << LOCKED_SHIFT;
    public static final int META_SYM_LOCKED = KeyEvent.META_SYM_ON << LOCKED_SHIFT;

    private static final int USED_SHIFT = 24;
    private static final long META_CAP_USED = ((long) KeyEvent.META_SHIFT_ON) << USED_SHIFT;
    private static final long META_ALT_USED = ((long) KeyEvent.META_ALT_ON) << USED_SHIFT;
    private static final long META_SYM_USED = ((long) KeyEvent.META_SYM_ON) << USED_SHIFT;
    private static final int PRESSED_SHIFT = 32;
    private static final long META_CAP_PRESSED = ((long) KeyEvent.META_SHIFT_ON) << PRESSED_SHIFT;
    private static final long META_ALT_PRESSED = ((long) KeyEvent.META_ALT_ON) << PRESSED_SHIFT;
    private static final long META_SYM_PRESSED = ((long) KeyEvent.META_SYM_ON) << PRESSED_SHIFT;
    private static final int RELEASED_SHIFT = 40;
    private static final long META_CAP_RELEASED = ((long) KeyEvent.META_SHIFT_ON) << RELEASED_SHIFT;
    private static final long META_ALT_RELEASED = ((long) KeyEvent.META_ALT_ON) << RELEASED_SHIFT;
    private static final long META_SYM_RELEASED = ((long) KeyEvent.META_SYM_ON) << RELEASED_SHIFT;
    private static final long META_SHIFT_MASK = META_SHIFT_ON | META_CAP_LOCKED | META_CAP_USED | META_CAP_PRESSED
            | META_CAP_RELEASED;
    private static final long META_ALT_MASK = META_ALT_ON | META_ALT_LOCKED | META_ALT_USED | META_ALT_PRESSED
            | META_ALT_RELEASED;
    private static final long META_SYM_MASK = META_SYM_ON | META_SYM_LOCKED | META_SYM_USED | META_SYM_PRESSED
            | META_SYM_RELEASED;

    private static final Object CAP = new NoCopySpan.Concrete();
    private static final Object ALT = new NoCopySpan.Concrete();
    private static final Object SYM = new NoCopySpan.Concrete();
    private static final Object SELECTING = new NoCopySpan.Concrete();

    private static final int PRESSED_RETURN_VALUE = 1;
    private static final int LOCKED_RETURN_VALUE = 2;

    private static final int PRESSED = Spannable.SPAN_MARK_MARK | (1 << Spannable.SPAN_USER_SHIFT);
    private static final int RELEASED = Spannable.SPAN_MARK_MARK | (2 << Spannable.SPAN_USER_SHIFT);
    private static final int USED = Spannable.SPAN_MARK_MARK | (3 << Spannable.SPAN_USER_SHIFT);
    private static final int LOCKED = Spannable.SPAN_MARK_MARK | (4 << Spannable.SPAN_USER_SHIFT);

    public MetaKeyKeyListener() {}

    public static void resetMetaState(Spannable text) {
        text.removeSpan(CAP);
        text.removeSpan(ALT);
        text.removeSpan(SYM);
        text.removeSpan(SELECTING);
    }

    public static final int getMetaState(CharSequence text) {
        return getActive(text, CAP, META_SHIFT_ON, META_CAP_LOCKED) | getActive(text, ALT, META_ALT_ON, META_ALT_LOCKED)
                | getActive(text, SYM, META_SYM_ON, META_SYM_LOCKED)
                | getActive(text, SELECTING, META_SELECTING, META_SELECTING);
    }

    public static final int getMetaState(CharSequence text, KeyEvent event) {
        int metaState = event.getMetaState();
        if (event.getKeyCharacterMap().getModifierBehavior() == KeyCharacterMap.MODIFIER_BEHAVIOR_CHORDED_OR_TOGGLED) {
            metaState |= getMetaState(text);
        }
        return metaState;
    }

    public static final int getMetaState(CharSequence text, int meta) {
        switch (meta) {
            case META_SHIFT_ON: return getActive(text, CAP, PRESSED_RETURN_VALUE, LOCKED_RETURN_VALUE);
            case META_ALT_ON: return getActive(text, ALT, PRESSED_RETURN_VALUE, LOCKED_RETURN_VALUE);
            case META_SYM_ON: return getActive(text, SYM, PRESSED_RETURN_VALUE, LOCKED_RETURN_VALUE);
            case META_SELECTING: return getActive(text, SELECTING, PRESSED_RETURN_VALUE, LOCKED_RETURN_VALUE);
            default: return 0;
        }
    }

    public static final int getMetaState(CharSequence text, int meta, KeyEvent event) {
        int metaState = event.getMetaState();
        if (event.getKeyCharacterMap().getModifierBehavior() == KeyCharacterMap.MODIFIER_BEHAVIOR_CHORDED_OR_TOGGLED) {
            metaState |= getMetaState(text);
        }
        if (META_SELECTING == meta) {
            if ((metaState & META_SELECTING) != 0) return 1;
            return 0;
        }
        return getMetaState(metaState, meta);
    }

    private static int getActive(CharSequence text, Object meta, int on, int lock) {
        if (!(text instanceof Spanned)) return 0;
        Spanned sp = (Spanned) text;
        int flag = sp.getSpanFlags(meta);
        if (flag == LOCKED) return lock;
        else if (flag != 0) return on;
        else return 0;
    }

    public static void adjustMetaAfterKeypress(Spannable content) {
        adjust(content, CAP);
        adjust(content, ALT);
        adjust(content, SYM);
    }

    public static boolean isMetaTracker(CharSequence text, Object what) {
        return what == CAP || what == ALT || what == SYM || what == SELECTING;
    }

    public static boolean isSelectingMetaTracker(CharSequence text, Object what) { return what == SELECTING; }

    private static void adjust(Spannable content, Object what) {
        int current = content.getSpanFlags(what);
        if (current == PRESSED) content.setSpan(what, 0, 0, USED);
        else if (current == RELEASED) content.removeSpan(what);
    }

    protected static void resetLockedMeta(Spannable content) {
        resetLock(content, CAP);
        resetLock(content, ALT);
        resetLock(content, SYM);
        resetLock(content, SELECTING);
    }

    private static void resetLock(Spannable content, Object what) {
        int current = content.getSpanFlags(what);
        if (current == LOCKED) content.removeSpan(what);
    }

    public boolean onKeyDown(View view, Editable content, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            press(content, CAP);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT
                || keyCode == KeyEvent.KEYCODE_NUM) {
            press(content, ALT);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_SYM) {
            press(content, SYM);
            return true;
        }
        return false;
    }

    private void press(Editable content, Object what) {
        int state = content.getSpanFlags(what);
        if (state == PRESSED) {
            // repeat before use
        } else if (state == RELEASED) {
            content.setSpan(what, 0, 0, LOCKED);
        } else if (state == USED) {
            // repeat after use
        } else if (state == LOCKED) {
            content.removeSpan(what);
        } else {
            content.setSpan(what, 0, 0, PRESSED);
        }
    }

    /** Hidden AOSP API. */
    public static void startSelecting(View view, Spannable content) { content.setSpan(SELECTING, 0, 0, PRESSED); }

    /** Hidden AOSP API. */
    public static void stopSelecting(View view, Spannable content) { content.removeSpan(SELECTING); }

    public boolean onKeyUp(View view, Editable content, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            release(content, CAP, event);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT
                || keyCode == KeyEvent.KEYCODE_NUM) {
            release(content, ALT, event);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_SYM) {
            release(content, SYM, event);
            return true;
        }
        return false;
    }

    private void release(Editable content, Object what, KeyEvent event) {
        int current = content.getSpanFlags(what);
        switch (event.getKeyCharacterMap().getModifierBehavior()) {
            case KeyCharacterMap.MODIFIER_BEHAVIOR_CHORDED_OR_TOGGLED:
                if (current == USED) content.removeSpan(what);
                else if (current == PRESSED) content.setSpan(what, 0, 0, RELEASED);
                break;
            default:
                content.removeSpan(what);
                break;
        }
    }

    public void clearMetaKeyState(View view, Editable content, int states) { clearMetaKeyState(content, states); }

    public static void clearMetaKeyState(Editable content, int which) {
        if ((which & META_SHIFT_ON) != 0) content.removeSpan(CAP);
        if ((which & META_ALT_ON) != 0) content.removeSpan(ALT);
        if ((which & META_SYM_ON) != 0) content.removeSpan(SYM);
        if ((which & META_SELECTING) != 0) content.removeSpan(SELECTING);
    }

    public static long resetLockedMeta(long state) {
        if ((state & META_CAP_LOCKED) != 0) state &= ~META_SHIFT_MASK;
        if ((state & META_ALT_LOCKED) != 0) state &= ~META_ALT_MASK;
        if ((state & META_SYM_LOCKED) != 0) state &= ~META_SYM_MASK;
        return state;
    }

    public static final int getMetaState(long state) {
        int result = 0;
        if ((state & META_CAP_LOCKED) != 0) result |= META_CAP_LOCKED;
        else if ((state & META_SHIFT_ON) != 0) result |= META_SHIFT_ON;
        if ((state & META_ALT_LOCKED) != 0) result |= META_ALT_LOCKED;
        else if ((state & META_ALT_ON) != 0) result |= META_ALT_ON;
        if ((state & META_SYM_LOCKED) != 0) result |= META_SYM_LOCKED;
        else if ((state & META_SYM_ON) != 0) result |= META_SYM_ON;
        return result;
    }

    public static final int getMetaState(long state, int meta) {
        switch (meta) {
            case META_SHIFT_ON:
                if ((state & META_CAP_LOCKED) != 0) return LOCKED_RETURN_VALUE;
                if ((state & META_SHIFT_ON) != 0) return PRESSED_RETURN_VALUE;
                return 0;
            case META_ALT_ON:
                if ((state & META_ALT_LOCKED) != 0) return LOCKED_RETURN_VALUE;
                if ((state & META_ALT_ON) != 0) return PRESSED_RETURN_VALUE;
                return 0;
            case META_SYM_ON:
                if ((state & META_SYM_LOCKED) != 0) return LOCKED_RETURN_VALUE;
                if ((state & META_SYM_ON) != 0) return PRESSED_RETURN_VALUE;
                return 0;
            default:
                return 0;
        }
    }

    public static long adjustMetaAfterKeypress(long state) {
        if ((state & META_CAP_PRESSED) != 0) {
            state = (state & ~META_SHIFT_MASK) | META_SHIFT_ON | META_CAP_USED;
        } else if ((state & META_CAP_RELEASED) != 0) {
            state &= ~META_SHIFT_MASK;
        }
        if ((state & META_ALT_PRESSED) != 0) {
            state = (state & ~META_ALT_MASK) | META_ALT_ON | META_ALT_USED;
        } else if ((state & META_ALT_RELEASED) != 0) {
            state &= ~META_ALT_MASK;
        }
        if ((state & META_SYM_PRESSED) != 0) {
            state = (state & ~META_SYM_MASK) | META_SYM_ON | META_SYM_USED;
        } else if ((state & META_SYM_RELEASED) != 0) {
            state &= ~META_SYM_MASK;
        }
        return state;
    }

    public static long handleKeyDown(long state, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            return press(state, META_SHIFT_ON, META_SHIFT_MASK, META_CAP_LOCKED, META_CAP_PRESSED, META_CAP_RELEASED,
                    META_CAP_USED);
        }
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT
                || keyCode == KeyEvent.KEYCODE_NUM) {
            return press(state, META_ALT_ON, META_ALT_MASK, META_ALT_LOCKED, META_ALT_PRESSED, META_ALT_RELEASED,
                    META_ALT_USED);
        }
        if (keyCode == KeyEvent.KEYCODE_SYM) {
            return press(state, META_SYM_ON, META_SYM_MASK, META_SYM_LOCKED, META_SYM_PRESSED, META_SYM_RELEASED,
                    META_SYM_USED);
        }
        return state;
    }

    private static long press(long state, int what, long mask, long locked, long pressed, long released, long used) {
        if ((state & pressed) != 0) {
            // repeat before use
        } else if ((state & released) != 0) {
            state = (state & ~mask) | what | locked;
        } else if ((state & used) != 0) {
            // repeat after use
        } else if ((state & locked) != 0) {
            state &= ~mask;
        } else {
            state |= what | pressed;
        }
        return state;
    }

    public static long handleKeyUp(long state, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            return release(state, META_SHIFT_ON, META_SHIFT_MASK, META_CAP_PRESSED, META_CAP_RELEASED, META_CAP_USED,
                    event);
        }
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT
                || keyCode == KeyEvent.KEYCODE_NUM) {
            return release(state, META_ALT_ON, META_ALT_MASK, META_ALT_PRESSED, META_ALT_RELEASED, META_ALT_USED,
                    event);
        }
        if (keyCode == KeyEvent.KEYCODE_SYM) {
            return release(state, META_SYM_ON, META_SYM_MASK, META_SYM_PRESSED, META_SYM_RELEASED, META_SYM_USED,
                    event);
        }
        return state;
    }

    private static long release(long state, int what, long mask, long pressed, long released, long used,
            KeyEvent event) {
        switch (event.getKeyCharacterMap().getModifierBehavior()) {
            case KeyCharacterMap.MODIFIER_BEHAVIOR_CHORDED_OR_TOGGLED:
                if ((state & used) != 0) state &= ~mask;
                else if ((state & pressed) != 0) state |= what | released;
                break;
            default:
                state &= ~mask;
                break;
        }
        return state;
    }

    public long clearMetaKeyState(long state, int which) {
        if ((which & META_SHIFT_ON) != 0 && (state & META_CAP_LOCKED) != 0) state &= ~META_SHIFT_MASK;
        if ((which & META_ALT_ON) != 0 && (state & META_ALT_LOCKED) != 0) state &= ~META_ALT_MASK;
        if ((which & META_SYM_ON) != 0 && (state & META_SYM_LOCKED) != 0) state &= ~META_SYM_MASK;
        return state;
    }
}
