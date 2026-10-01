package android.view;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.AndroidRuntimeException;

/**
 * Character map for the built-in virtual keyboard: a US QWERTY layout, which
 * is what Android's Virtual.kcm / Generic.kcm provide for hardware keys.
 */
public class KeyCharacterMap implements Parcelable {
    public static final int ALPHA = 3;
    public static final int BUILT_IN_KEYBOARD = 0;
    public static final int COMBINING_ACCENT = -2147483648;
    public static final int COMBINING_ACCENT_MASK = 2147483647;
    public static final int FULL = 4;
    public static final char HEX_INPUT = '';
    public static final int MODIFIER_BEHAVIOR_CHORDED = 0;
    public static final int MODIFIER_BEHAVIOR_CHORDED_OR_TOGGLED = 1;
    public static final int NUMERIC = 1;
    public static final char PICKER_DIALOG_INPUT = '';
    public static final int PREDICTIVE = 2;
    public static final int SPECIAL_FUNCTION = 5;
    public static final int VIRTUAL_KEYBOARD = -1;

    @Deprecated
    public static class KeyData {
        public static final int META_LENGTH = 4;
        public char displayLabel;
        public char number;
        public char[] meta = new char[META_LENGTH];

        public KeyData() {}
    }

    public static class UnavailableException extends AndroidRuntimeException {
        public UnavailableException(String msg) { super(msg); }
    }

    private static final KeyCharacterMap sDefault = new KeyCharacterMap();

    // keycode -> {base, shift}
    private static final char[][] MAP = new char[KeyEvent.getMaxKeyCode() + 1][];

    static {
        for (int i = 0; i < 26; i++) {
            MAP[KeyEvent.KEYCODE_A + i] = new char[] {(char) ('a' + i), (char) ('A' + i)};
        }
        String shiftedDigits = ")!@#$%^&*(";
        for (int i = 0; i < 10; i++) {
            MAP[KeyEvent.KEYCODE_0 + i] = new char[] {(char) ('0' + i), shiftedDigits.charAt(i)};
            MAP[KeyEvent.KEYCODE_NUMPAD_0 + i] = new char[] {(char) ('0' + i), (char) ('0' + i)};
        }
        put(KeyEvent.KEYCODE_SPACE, ' ', ' ');
        put(KeyEvent.KEYCODE_COMMA, ',', '<');
        put(KeyEvent.KEYCODE_PERIOD, '.', '>');
        put(KeyEvent.KEYCODE_GRAVE, '`', '~');
        put(KeyEvent.KEYCODE_MINUS, '-', '_');
        put(KeyEvent.KEYCODE_EQUALS, '=', '+');
        put(KeyEvent.KEYCODE_LEFT_BRACKET, '[', '{');
        put(KeyEvent.KEYCODE_RIGHT_BRACKET, ']', '}');
        put(KeyEvent.KEYCODE_BACKSLASH, '\\', '|');
        put(KeyEvent.KEYCODE_SEMICOLON, ';', ':');
        put(KeyEvent.KEYCODE_APOSTROPHE, '\'', '"');
        put(KeyEvent.KEYCODE_SLASH, '/', '?');
        put(KeyEvent.KEYCODE_AT, '@', '@');
        put(KeyEvent.KEYCODE_PLUS, '+', '+');
        put(KeyEvent.KEYCODE_STAR, '*', '*');
        put(KeyEvent.KEYCODE_POUND, '#', '#');
        put(KeyEvent.KEYCODE_TAB, '\t', '\t');
        put(KeyEvent.KEYCODE_ENTER, '\n', '\n');
        put(KeyEvent.KEYCODE_NUMPAD_ENTER, '\n', '\n');
        put(KeyEvent.KEYCODE_NUMPAD_DIVIDE, '/', '/');
        put(KeyEvent.KEYCODE_NUMPAD_MULTIPLY, '*', '*');
        put(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, '-', '-');
        put(KeyEvent.KEYCODE_NUMPAD_ADD, '+', '+');
        put(KeyEvent.KEYCODE_NUMPAD_DOT, '.', '.');
        put(KeyEvent.KEYCODE_NUMPAD_COMMA, ',', ',');
        put(KeyEvent.KEYCODE_NUMPAD_EQUALS, '=', '=');
        put(KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN, '(', '(');
        put(KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN, ')', ')');
    }

    private static void put(int keyCode, char base, char shifted) { MAP[keyCode] = new char[] {base, shifted}; }

    private KeyCharacterMap() {}

    public static KeyCharacterMap load(int deviceId) { return sDefault; }

    public int get(int keyCode, int metaState) {
        if (keyCode < 0 || keyCode >= MAP.length || MAP[keyCode] == null) return 0;
        if ((metaState & (KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON | KeyEvent.META_META_ON)) != 0) return 0;
        char[] e = MAP[keyCode];
        boolean shift = (metaState & KeyEvent.META_SHIFT_ON) != 0;
        boolean caps = (metaState & KeyEvent.META_CAPS_LOCK_ON) != 0;
        if (Character.isLetter(e[0]) && caps) shift = !shift;
        return shift ? e[1] : e[0];
    }

    public char getNumber(int keyCode) {
        int c = get(keyCode, 0);
        if (c >= '0' && c <= '9') return (char) c;
        if (keyCode == KeyEvent.KEYCODE_STAR) return '*';
        if (keyCode == KeyEvent.KEYCODE_POUND) return '#';
        if (keyCode == KeyEvent.KEYCODE_PLUS) return '+';
        return 0;
    }

    public char getMatch(int keyCode, char[] chars) { return getMatch(keyCode, chars, 0); }

    public char getMatch(int keyCode, char[] chars, int metaState) {
        if (chars == null) throw new IllegalArgumentException("chars must not be null.");
        int[] metas = {metaState, metaState | KeyEvent.META_SHIFT_ON};
        for (int m : metas) {
            int c = get(keyCode, m);
            for (char ch : chars) if (ch == c) return ch;
        }
        return 0;
    }

    public char getDisplayLabel(int keyCode) {
        int c = get(keyCode, KeyEvent.META_SHIFT_ON);
        return (char) c;
    }

    public static int getDeadChar(int accent, int c) {
        if (c == accent || c == ' ') return accent;
        return 0;
    }

    @Deprecated
    public boolean getKeyData(int keyCode, KeyData results) {
        if (results.meta.length < KeyData.META_LENGTH) {
            throw new IndexOutOfBoundsException("results.meta.length must be >= " + KeyData.META_LENGTH);
        }
        char displayLabel = getDisplayLabel(keyCode);
        if (displayLabel == 0) return false;
        results.displayLabel = displayLabel;
        results.number = getNumber(keyCode);
        results.meta[0] = (char) get(keyCode, 0);
        results.meta[1] = (char) get(keyCode, KeyEvent.META_SHIFT_ON);
        results.meta[2] = (char) get(keyCode, KeyEvent.META_ALT_ON);
        results.meta[3] = (char) get(keyCode, KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);
        return true;
    }

    public KeyEvent[] getEvents(char[] chars) {
        if (chars == null) throw new IllegalArgumentException("chars must not be null.");
        java.util.ArrayList<KeyEvent> out = new java.util.ArrayList<KeyEvent>();
        long now = android.os.SystemClock.uptimeMillis();
        for (char ch : chars) {
            int code = -1;
            int meta = 0;
            for (int k = 0; k < MAP.length && code < 0; k++) {
                if (MAP[k] == null) continue;
                if (MAP[k][0] == ch) code = k;
                else if (MAP[k][1] == ch) {
                    code = k;
                    meta = KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
                }
            }
            if (code < 0) return null;
            if (meta != 0) out.add(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, meta,
                    VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD));
            out.add(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta, VIRTUAL_KEYBOARD, 0, 0,
                    InputDevice.SOURCE_KEYBOARD));
            out.add(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta, VIRTUAL_KEYBOARD, 0, 0,
                    InputDevice.SOURCE_KEYBOARD));
            if (meta != 0) out.add(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0,
                    VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD));
        }
        return out.toArray(new KeyEvent[out.size()]);
    }

    public boolean isPrintingKey(int keyCode) {
        int c = get(keyCode, 0);
        return c != 0 && !Character.isISOControl(c) || keyCode == KeyEvent.KEYCODE_SPACE;
    }

    public int getKeyboardType() { return FULL; }

    public int getModifierBehavior() { return MODIFIER_BEHAVIOR_CHORDED; }

    public static boolean deviceHasKey(int keyCode) { return true; }

    public static boolean[] deviceHasKeys(int[] keyCodes) {
        boolean[] ret = new boolean[keyCodes.length];
        for (int i = 0; i < ret.length; i++) ret[i] = true;
        return ret;
    }

    public void writeToParcel(Parcel out, int flags) {}

    public int describeContents() { return 0; }

    @Override
    public boolean equals(Object obj) { return obj instanceof KeyCharacterMap; }

    @Override
    public int hashCode() { return 1; }

    public static final Parcelable.Creator<KeyCharacterMap> CREATOR = new Parcelable.Creator<KeyCharacterMap>() {
        public KeyCharacterMap createFromParcel(Parcel in) { return sDefault; }
        public KeyCharacterMap[] newArray(int size) { return new KeyCharacterMap[size]; }
    };
}
