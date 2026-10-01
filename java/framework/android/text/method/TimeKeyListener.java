package android.text.method;

import android.text.InputType;
import java.util.Locale;

/** Accepts characters used in time entry (simplified port of AOSP TimeKeyListener). */
public class TimeKeyListener extends NumberKeyListener {
    @Deprecated
    public static final char[] CHARACTERS = new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'm', 'p', ':'};
    private static TimeKeyListener sInstance;

    @Deprecated
    public TimeKeyListener() {}

    public TimeKeyListener(Locale locale) {}

    protected char[] getAcceptedChars() { return CHARACTERS; }

    @Deprecated
    public static TimeKeyListener getInstance() {
        if (sInstance == null) sInstance = new TimeKeyListener();
        return sInstance;
    }

    public static TimeKeyListener getInstance(Locale locale) { return getInstance(); }

    public int getInputType() { return InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME; }
}
