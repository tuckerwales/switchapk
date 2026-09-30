package android.content.res;

import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Locale;

public final class Configuration implements Parcelable, Comparable<Configuration> {
    public static final int DENSITY_DPI_UNDEFINED = 0;
    public static final int DENSITY_DPI_ANY = 0xfffe;
    public static final int DENSITY_DPI_NONE = 0xffff;
    public static final int SCREENLAYOUT_SIZE_MASK = 0x0f;
    public static final int SCREENLAYOUT_SIZE_UNDEFINED = 0x00;
    public static final int SCREENLAYOUT_SIZE_SMALL = 0x01;
    public static final int SCREENLAYOUT_SIZE_NORMAL = 0x02;
    public static final int SCREENLAYOUT_SIZE_LARGE = 0x03;
    public static final int SCREENLAYOUT_SIZE_XLARGE = 0x04;
    public static final int SCREENLAYOUT_LONG_MASK = 0x30;
    public static final int SCREENLAYOUT_LONG_UNDEFINED = 0x00;
    public static final int SCREENLAYOUT_LONG_NO = 0x10;
    public static final int SCREENLAYOUT_LONG_YES = 0x20;
    public static final int SCREENLAYOUT_LAYOUTDIR_MASK = 0xC0;
    public static final int SCREENLAYOUT_LAYOUTDIR_SHIFT = 6;
    public static final int SCREENLAYOUT_LAYOUTDIR_UNDEFINED = 0x00;
    public static final int SCREENLAYOUT_LAYOUTDIR_LTR = 0x01 << SCREENLAYOUT_LAYOUTDIR_SHIFT;
    public static final int SCREENLAYOUT_LAYOUTDIR_RTL = 0x02 << SCREENLAYOUT_LAYOUTDIR_SHIFT;
    public static final int SCREENLAYOUT_ROUND_MASK = 0x300;
    public static final int SCREENLAYOUT_ROUND_UNDEFINED = 0x00;
    public static final int SCREENLAYOUT_ROUND_NO = 0x1 << 8;
    public static final int SCREENLAYOUT_ROUND_YES = 0x2 << 8;
    public static final int SCREENLAYOUT_UNDEFINED = 0;
    public static final int COLOR_MODE_WIDE_COLOR_GAMUT_MASK = 0x3;
    public static final int COLOR_MODE_WIDE_COLOR_GAMUT_UNDEFINED = 0x0;
    public static final int COLOR_MODE_WIDE_COLOR_GAMUT_NO = 0x1;
    public static final int COLOR_MODE_WIDE_COLOR_GAMUT_YES = 0x2;
    public static final int COLOR_MODE_HDR_MASK = 0xc;
    public static final int COLOR_MODE_HDR_NO = 0x4;
    public static final int COLOR_MODE_HDR_YES = 0x8;
    public static final int COLOR_MODE_UNDEFINED = 0;
    public static final int TOUCHSCREEN_UNDEFINED = 0;
    public static final int TOUCHSCREEN_NOTOUCH = 1;
    public static final int TOUCHSCREEN_STYLUS = 2;
    public static final int TOUCHSCREEN_FINGER = 3;
    public static final int KEYBOARD_UNDEFINED = 0;
    public static final int KEYBOARD_NOKEYS = 1;
    public static final int KEYBOARD_QWERTY = 2;
    public static final int KEYBOARD_12KEY = 3;
    public static final int KEYBOARDHIDDEN_UNDEFINED = 0;
    public static final int KEYBOARDHIDDEN_NO = 1;
    public static final int KEYBOARDHIDDEN_YES = 2;
    public static final int HARDKEYBOARDHIDDEN_UNDEFINED = 0;
    public static final int HARDKEYBOARDHIDDEN_NO = 1;
    public static final int HARDKEYBOARDHIDDEN_YES = 2;
    public static final int NAVIGATION_UNDEFINED = 0;
    public static final int NAVIGATION_NONAV = 1;
    public static final int NAVIGATION_DPAD = 2;
    public static final int NAVIGATION_TRACKBALL = 3;
    public static final int NAVIGATION_WHEEL = 4;
    public static final int NAVIGATIONHIDDEN_UNDEFINED = 0;
    public static final int NAVIGATIONHIDDEN_NO = 1;
    public static final int NAVIGATIONHIDDEN_YES = 2;
    public static final int ORIENTATION_UNDEFINED = 0;
    public static final int ORIENTATION_PORTRAIT = 1;
    public static final int ORIENTATION_LANDSCAPE = 2;
    @Deprecated public static final int ORIENTATION_SQUARE = 3;
    public static final int UI_MODE_TYPE_MASK = 0x0f;
    public static final int UI_MODE_TYPE_UNDEFINED = 0x00;
    public static final int UI_MODE_TYPE_NORMAL = 0x01;
    public static final int UI_MODE_TYPE_DESK = 0x02;
    public static final int UI_MODE_TYPE_CAR = 0x03;
    public static final int UI_MODE_TYPE_TELEVISION = 0x04;
    public static final int UI_MODE_TYPE_APPLIANCE = 0x05;
    public static final int UI_MODE_TYPE_WATCH = 0x06;
    public static final int UI_MODE_TYPE_VR_HEADSET = 0x07;
    public static final int UI_MODE_NIGHT_MASK = 0x30;
    public static final int UI_MODE_NIGHT_UNDEFINED = 0x00;
    public static final int UI_MODE_NIGHT_NO = 0x10;
    public static final int UI_MODE_NIGHT_YES = 0x20;
    public static final int SCREEN_WIDTH_DP_UNDEFINED = 0;
    public static final int SCREEN_HEIGHT_DP_UNDEFINED = 0;
    public static final int SMALLEST_SCREEN_WIDTH_DP_UNDEFINED = 0;
    public static final int FONT_WEIGHT_ADJUSTMENT_UNDEFINED = Integer.MAX_VALUE;
    public static final int MNC_ZERO = 0xffff;

    public float fontScale;
    public int mcc;
    public int mnc;
    @Deprecated public Locale locale;
    public int screenLayout;
    public int colorMode;
    public int touchscreen;
    public int keyboard;
    public int keyboardHidden;
    public int hardKeyboardHidden;
    public int navigation;
    public int navigationHidden;
    public int orientation;
    public int uiMode;
    public int screenWidthDp;
    public int screenHeightDp;
    public int smallestScreenWidthDp;
    public int densityDpi;
    public int fontWeightAdjustment;
    public int seq;
    private LocaleList mLocaleList;

    public Configuration() { unset(); }
    public Configuration(Configuration o) { setTo(o); }

    public void setTo(Configuration o) {
        fontScale = o.fontScale;
        mcc = o.mcc;
        mnc = o.mnc;
        locale = o.locale;
        mLocaleList = o.mLocaleList;
        screenLayout = o.screenLayout;
        colorMode = o.colorMode;
        touchscreen = o.touchscreen;
        keyboard = o.keyboard;
        keyboardHidden = o.keyboardHidden;
        hardKeyboardHidden = o.hardKeyboardHidden;
        navigation = o.navigation;
        navigationHidden = o.navigationHidden;
        orientation = o.orientation;
        uiMode = o.uiMode;
        screenWidthDp = o.screenWidthDp;
        screenHeightDp = o.screenHeightDp;
        smallestScreenWidthDp = o.smallestScreenWidthDp;
        densityDpi = o.densityDpi;
        fontWeightAdjustment = o.fontWeightAdjustment;
        seq = o.seq;
    }

    public void unset() {
        setToDefaults();
        fontScale = 0;
    }

    public void setToDefaults() {
        fontScale = 1;
        mcc = mnc = 0;
        mLocaleList = LocaleList.getEmptyLocaleList();
        locale = null;
        touchscreen = TOUCHSCREEN_UNDEFINED;
        keyboard = KEYBOARD_UNDEFINED;
        keyboardHidden = KEYBOARDHIDDEN_UNDEFINED;
        hardKeyboardHidden = HARDKEYBOARDHIDDEN_UNDEFINED;
        navigation = NAVIGATION_UNDEFINED;
        navigationHidden = NAVIGATIONHIDDEN_UNDEFINED;
        orientation = ORIENTATION_UNDEFINED;
        screenLayout = SCREENLAYOUT_UNDEFINED;
        colorMode = COLOR_MODE_UNDEFINED;
        uiMode = UI_MODE_TYPE_UNDEFINED;
        screenWidthDp = SCREEN_WIDTH_DP_UNDEFINED;
        screenHeightDp = SCREEN_HEIGHT_DP_UNDEFINED;
        smallestScreenWidthDp = SMALLEST_SCREEN_WIDTH_DP_UNDEFINED;
        densityDpi = DENSITY_DPI_UNDEFINED;
        fontWeightAdjustment = FONT_WEIGHT_ADJUSTMENT_UNDEFINED;
        seq = 0;
    }

    public int updateFrom(Configuration delta) {
        int changed = diff(delta);
        setTo(delta);
        return changed;
    }

    public int diff(Configuration delta) {
        int changed = 0;
        if (delta.fontScale > 0 && fontScale != delta.fontScale) changed |= android.content.pm.ActivityInfo.CONFIG_FONT_SCALE;
        if (delta.orientation != ORIENTATION_UNDEFINED && orientation != delta.orientation) changed |= android.content.pm.ActivityInfo.CONFIG_ORIENTATION;
        if (delta.screenWidthDp != 0 && screenWidthDp != delta.screenWidthDp) changed |= android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE;
        if (delta.screenHeightDp != 0 && screenHeightDp != delta.screenHeightDp) changed |= android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE;
        if (delta.smallestScreenWidthDp != 0 && smallestScreenWidthDp != delta.smallestScreenWidthDp) changed |= android.content.pm.ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE;
        if (delta.uiMode != 0 && uiMode != delta.uiMode) changed |= android.content.pm.ActivityInfo.CONFIG_UI_MODE;
        if (delta.densityDpi != 0 && densityDpi != delta.densityDpi) changed |= android.content.pm.ActivityInfo.CONFIG_DENSITY;
        if (delta.touchscreen != 0 && touchscreen != delta.touchscreen) changed |= android.content.pm.ActivityInfo.CONFIG_TOUCHSCREEN;
        if (delta.keyboard != 0 && keyboard != delta.keyboard) changed |= android.content.pm.ActivityInfo.CONFIG_KEYBOARD;
        if (delta.navigation != 0 && navigation != delta.navigation) changed |= android.content.pm.ActivityInfo.CONFIG_NAVIGATION;
        if (delta.screenLayout != 0 && screenLayout != delta.screenLayout) changed |= android.content.pm.ActivityInfo.CONFIG_SCREEN_LAYOUT;
        return changed;
    }

    public static boolean needNewResources(int configChanges, int interestingChanges) { return (configChanges & interestingChanges) != 0; }

    public boolean isLayoutSizeAtLeast(int size) {
        int cur = screenLayout & SCREENLAYOUT_SIZE_MASK;
        if (cur == SCREENLAYOUT_SIZE_UNDEFINED) return false;
        return cur >= size;
    }

    public boolean isScreenRound() { return (screenLayout & SCREENLAYOUT_ROUND_MASK) == SCREENLAYOUT_ROUND_YES; }
    public boolean isScreenWideColorGamut() { return false; }
    public boolean isScreenHdr() { return false; }
    public boolean isNightModeActive() { return (uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES; }

    public LocaleList getLocales() {
        if (mLocaleList == null || mLocaleList.isEmpty()) {
            if (locale != null) return new LocaleList(locale);
            return LocaleList.getDefault();
        }
        return mLocaleList;
    }

    public void setLocales(LocaleList locales) {
        mLocaleList = locales == null ? LocaleList.getEmptyLocaleList() : locales;
        locale = mLocaleList.get(0);
    }

    public void setLocale(Locale loc) { setLocales(loc == null ? LocaleList.getEmptyLocaleList() : new LocaleList(loc)); }

    public void clearLocales() {
        mLocaleList = LocaleList.getEmptyLocaleList();
        locale = null;
    }

    public int getLayoutDirection() {
        return (screenLayout & SCREENLAYOUT_LAYOUTDIR_MASK) == SCREENLAYOUT_LAYOUTDIR_RTL ? android.view.View.LAYOUT_DIRECTION_RTL
                : android.view.View.LAYOUT_DIRECTION_LTR;
    }

    public void setLayoutDirection(Locale loc) {
        screenLayout = (screenLayout & ~SCREENLAYOUT_LAYOUTDIR_MASK) | SCREENLAYOUT_LAYOUTDIR_LTR;
    }

    public int compareTo(Configuration that) {
        float a = this.fontScale, b = that.fontScale;
        if (a < b) return -1;
        if (a > b) return 1;
        int n;
        if ((n = this.orientation - that.orientation) != 0) return n;
        if ((n = this.screenWidthDp - that.screenWidthDp) != 0) return n;
        if ((n = this.screenHeightDp - that.screenHeightDp) != 0) return n;
        if ((n = this.uiMode - that.uiMode) != 0) return n;
        if ((n = this.densityDpi - that.densityDpi) != 0) return n;
        return this.screenLayout - that.screenLayout;
    }

    public boolean equals(Configuration that) { return that != null && (that == this || compareTo(that) == 0); }
    public boolean equals(Object that) { return that instanceof Configuration && equals((Configuration) that); }
    public int hashCode() { return 17 + Float.floatToIntBits(fontScale) * 31 + orientation * 7 + screenWidthDp * 13 + screenHeightDp * 17 + uiMode + densityDpi; }

    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int flags) { dest.writeValue(new Configuration(this)); }
    public void readFromParcel(Parcel source) { setTo((Configuration) source.readValue(null)); }

    public static final Parcelable.Creator<Configuration> CREATOR = new Parcelable.Creator<Configuration>() {
        public Configuration createFromParcel(Parcel source) { return (Configuration) source.readValue(null); }
        public Configuration[] newArray(int size) { return new Configuration[size]; }
    };

    public String toString() {
        return "{" + fontScale + " " + getLocales() + " sw" + smallestScreenWidthDp + "dp w" + screenWidthDp + "dp h" + screenHeightDp
                + "dp " + densityDpi + "dpi " + (orientation == ORIENTATION_LANDSCAPE ? "land" : "port")
                + ((uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES ? " night" : "") + "}";
    }
}
