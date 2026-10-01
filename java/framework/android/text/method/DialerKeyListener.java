package android.text.method;

import android.text.InputType;

/** Accepts characters used when dialing (port of AOSP DialerKeyListener). */
public class DialerKeyListener extends NumberKeyListener {
    public static final char[] CHARACTERS = new char[] {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '#', '*', '+', '-', '(', ')', ',', '/', 'N', '.', ' ', ';'
    };
    private static DialerKeyListener sInstance;

    public DialerKeyListener() {}

    protected char[] getAcceptedChars() { return CHARACTERS; }

    public static DialerKeyListener getInstance() {
        if (sInstance != null) return sInstance;
        sInstance = new DialerKeyListener();
        return sInstance;
    }

    public int getInputType() { return InputType.TYPE_CLASS_PHONE; }
}
