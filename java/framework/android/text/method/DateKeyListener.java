package android.text.method;

import android.text.InputType;
import java.util.Locale;

/** Accepts characters used in date entry (simplified port of AOSP DateKeyListener). */
public class DateKeyListener extends NumberKeyListener {
    @Deprecated
    public static final char[] CHARACTERS = new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '/', '-', '.'};
    private static DateKeyListener sInstance;

    @Deprecated
    public DateKeyListener() {}

    public DateKeyListener(Locale locale) {}

    protected char[] getAcceptedChars() { return CHARACTERS; }

    @Deprecated
    public static DateKeyListener getInstance() {
        if (sInstance == null) sInstance = new DateKeyListener();
        return sInstance;
    }

    public static DateKeyListener getInstance(Locale locale) { return getInstance(); }

    public int getInputType() { return InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE; }
}
