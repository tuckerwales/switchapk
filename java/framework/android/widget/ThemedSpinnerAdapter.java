package android.widget;

import android.content.res.Resources;

/** A {@link SpinnerAdapter} whose drop-down can use a different theme (AOSP). */
public interface ThemedSpinnerAdapter extends SpinnerAdapter {
    void setDropDownViewTheme(Resources.Theme theme);

    Resources.Theme getDropDownViewTheme();
}
