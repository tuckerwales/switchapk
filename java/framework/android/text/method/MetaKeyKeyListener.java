package android.text.method;

import android.text.Editable;
import android.text.NoCopySpan;
import android.text.Spannable;
import android.view.KeyEvent;
import android.view.View;

/**
 * Tracks shift, alt and sym on an {@link Editable} (AOSP MetaKeyKeyListener).
 * The long form keeps the same low 16 bits as {@link #getMetaState(CharSequence)}.
 */
public abstract class MetaKeyKeyListener {
    public static final int META_SHIFT_ON = 1;
    public static final int META_ALT_ON = 2;
    public static final int META_SYM_ON = 4;
    public static final int META_CAP_LOCKED = 0x100;
    public static final int META_ALT_LOCKED = 0x200;
    public static final int META_SYM_LOCKED = 0x400;

    private static final int PRESSED_SHIFT = 16;
    private static final Object META = new MetaSpan();

    private static final class MetaSpan implements NoCopySpan {
        int state;
    }

    public MetaKeyKeyListener() {}

    public static void resetMetaState(Spannable text) { setState(text, 0); }

    public static final int getMetaState(CharSequence text) { return stateOf(text); }

    public static final int getMetaState(CharSequence text, KeyEvent event) {
        int state = getMetaState(text);
        if (event == null) return state;
        int meta = event.getMetaState();
        if ((meta & KeyEvent.META_SHIFT_ON) != 0) state |= META_SHIFT_ON;
        if ((meta & KeyEvent.META_ALT_ON) != 0) state |= META_ALT_ON;
        if ((meta & KeyEvent.META_SYM_ON) != 0) state |= META_SYM_ON;
        return state;
    }

    public static final int getMetaState(CharSequence text, int meta) { return getMetaState(text) & meta; }

    public static final int getMetaState(CharSequence text, int meta, KeyEvent event) {
        return getMetaState(text, event) & meta;
    }

    /** Clears one-shot (not locked) modifiers after a character is inserted. */
    public static void adjustMetaAfterKeypress(Spannable text) {
        int state = stateOf(text);
        if ((state & META_CAP_LOCKED) == 0) state &= ~META_SHIFT_ON;
        if ((state & META_ALT_LOCKED) == 0) state &= ~META_ALT_ON;
        if ((state & META_SYM_LOCKED) == 0) state &= ~META_SYM_ON;
        state &= ~(META_SHIFT_ON << PRESSED_SHIFT);
        state &= ~(META_ALT_ON << PRESSED_SHIFT);
        state &= ~(META_SYM_ON << PRESSED_SHIFT);
        setState(text, state);
    }

    public static boolean isMetaTracker(CharSequence text, Object what) { return what instanceof MetaSpan; }

    public static boolean isSelectingMetaTracker(CharSequence text, Object what) { return false; }

    protected static void resetLockedMeta(Spannable text) {
        setState(text, stateOf(text) & ~(META_CAP_LOCKED | META_ALT_LOCKED | META_SYM_LOCKED));
    }

    public boolean onKeyDown(View view, Editable content, int keyCode, KeyEvent event) {
        int bit = bitFor(keyCode);
        if (bit == 0) return false;
        int state = stateOf(content);
        int pressed = bit << PRESSED_SHIFT;
        if ((state & pressed) == 0) {
            if ((state & bit) != 0) state |= lockFor(bit);
            state |= bit | pressed;
        }
        setState(content, state);
        return true;
    }

    public boolean onKeyUp(View view, Editable content, int keyCode, KeyEvent event) {
        int bit = bitFor(keyCode);
        if (bit == 0) return false;
        int state = stateOf(content);
        state &= ~(bit << PRESSED_SHIFT);
        if ((state & lockFor(bit)) == 0) state &= ~bit;
        setState(content, state);
        return true;
    }

    public void clearMetaKeyState(View view, Editable content, int states) { clearMetaKeyState(content, states); }

    public static void clearMetaKeyState(Editable content, int states) {
        setState(content, stateOf(content) & ~states);
    }

    public static long resetLockedMeta(long state) {
        return state & ~(META_CAP_LOCKED | META_ALT_LOCKED | META_SYM_LOCKED);
    }

    public static final int getMetaState(long state) { return (int) state & 0xffff; }

    public static final int getMetaState(long state, int meta) { return getMetaState(state) & meta; }

    public static long adjustMetaAfterKeypress(long state) {
        if ((state & META_CAP_LOCKED) == 0) state &= ~META_SHIFT_ON;
        if ((state & META_ALT_LOCKED) == 0) state &= ~META_ALT_ON;
        if ((state & META_SYM_LOCKED) == 0) state &= ~META_SYM_ON;
        return state;
    }

    public static long handleKeyDown(long state, int keyCode, KeyEvent event) {
        int bit = bitFor(keyCode);
        if (bit == 0) return state;
        if ((state & bit) != 0) state |= lockFor(bit);
        return state | bit;
    }

    public static long handleKeyUp(long state, int keyCode, KeyEvent event) {
        int bit = bitFor(keyCode);
        if (bit == 0) return state;
        if ((state & lockFor(bit)) == 0) state &= ~bit;
        return state;
    }

    public long clearMetaKeyState(long state, int which) { return state & ~which; }

    private static int bitFor(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return META_SHIFT_ON;
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
                return META_ALT_ON;
            case KeyEvent.KEYCODE_SYM:
                return META_SYM_ON;
            default:
                return 0;
        }
    }

    private static int lockFor(int bit) {
        if (bit == META_SHIFT_ON) return META_CAP_LOCKED;
        if (bit == META_ALT_ON) return META_ALT_LOCKED;
        if (bit == META_SYM_ON) return META_SYM_LOCKED;
        return 0;
    }

    private static int stateOf(CharSequence text) {
        if (!(text instanceof Spannable)) return 0;
        MetaSpan span = span((Spannable) text);
        return span == null ? 0 : span.state;
    }

    private static void setState(Spannable text, int state) {
        MetaSpan span = span(text);
        if (span == null) {
            span = new MetaSpan();
            text.setSpan(span, 0, 0, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }
        span.state = state;
    }

    private static MetaSpan span(Spannable text) {
        MetaSpan[] spans = text.getSpans(0, text.length(), MetaSpan.class);
        return spans.length == 0 ? null : spans[0];
    }
}
