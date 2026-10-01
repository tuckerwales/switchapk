package android.text.method;

import android.text.InputType;
import java.util.Locale;

/** Accepts characters used in datetime entry (simplified port of AOSP DateTimeKeyListener). */
public class DateTimeKeyListener extends NumberKeyListener {
    @Deprecated
    public static final char[] CHARACTERS = new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'm', 'p', ':', '/', '-', ' '};
    private static DateTimeKeyListener sInstance;

    @Deprecated
    public DateTimeKeyListener() {}

    public DateTimeKeyListener(Locale locale) {}

    protected char[] getAcceptedChars() { return CHARACTERS; }

    @Deprecated
    public static DateTimeKeyListener getInstance() {
        if (sInstance == null) sInstance = new DateTimeKeyListener();
        return sInstance;
    }

    public static DateTimeKeyListener getInstance(Locale locale) { return getInstance(); }

    public int getInputType() { return InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_NORMAL; }
}
