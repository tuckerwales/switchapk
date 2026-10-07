/*
 * AConfiguration for native code (WS9): android/configuration.h over a plain
 * struct. android_native_app_glue creates one, fills it with
 * AConfiguration_fromAssetManager and re-reads it when the display changes.
 * Values follow the framework Configuration for the current display (the
 * same numbers android.content.res.Configuration reports); resource
 * matching (match, isBetterThan) is not implemented because the APK's
 * resources are resolved in Java.
 */
#include "ndk_android.h"
#include "../core/common.h"
#include "../platform/platform.h"

struct AConfiguration {
    int32_t mcc, mnc;
    char language[2], country[2];
    int32_t orientation, touchscreen, density, keyboard, navigation, keys_hidden, nav_hidden;
    int32_t sdk_version, screen_size, screen_long, ui_mode_type, ui_mode_night;
    int32_t screen_width_dp, screen_height_dp, smallest_screen_width_dp, layout_direction;
};

/* ACONFIGURATION_* values (android/configuration.h) */
enum {
    NI_ORIENTATION_PORT = 1,
    NI_ORIENTATION_LAND = 2,
    NI_TOUCHSCREEN_NOTOUCH = 1,
    NI_TOUCHSCREEN_FINGER = 3,
    NI_KEYBOARD_NOKEYS = 1,
    NI_NAVIGATION_DPAD = 2,
    NI_KEYSHIDDEN_NO = 1,
    NI_NAVHIDDEN_NO = 1,
    NI_SCREENSIZE_NORMAL = 2,
    NI_SCREENSIZE_LARGE = 3,
    NI_SCREENSIZE_XLARGE = 4,
    NI_SCREENLONG_YES = 2,
    NI_SCREENLONG_NO = 1,
    NI_UI_MODE_TYPE_NORMAL = 1,
    NI_UI_MODE_NIGHT_NO = 1,
    NI_LAYOUTDIR_LTR = 1,
};

AConfiguration *AConfiguration_new(void) { return sa_calloc(1, sizeof(AConfiguration)); }

void AConfiguration_delete(AConfiguration *c) { free(c); }

void AConfiguration_fromAssetManager(AConfiguration *out, void *am) {
    SA_UNUSED(am);
    PlatformDisplay d;
    memset(&d, 0, sizeof d);
    platform_get_display(&d);
    int dpi = d.dpi > 0 ? d.dpi : 240;
    int wdp = d.width * 160 / dpi, hdp = d.height * 160 / dpi;
    memset(out, 0, sizeof *out);
    out->language[0] = 'e';
    out->language[1] = 'n';
    out->country[0] = 'U';
    out->country[1] = 'S';
    out->orientation = d.width > d.height ? NI_ORIENTATION_LAND : NI_ORIENTATION_PORT;
    out->touchscreen = d.touch ? NI_TOUCHSCREEN_FINGER : NI_TOUCHSCREEN_NOTOUCH;
    out->density = dpi;
    out->keyboard = NI_KEYBOARD_NOKEYS;
    out->navigation = NI_NAVIGATION_DPAD;
    out->keys_hidden = NI_KEYSHIDDEN_NO;
    out->nav_hidden = NI_NAVHIDDEN_NO;
    out->sdk_version = 29;
    int longer = wdp > hdp ? wdp : hdp, shorter = wdp > hdp ? hdp : wdp;
    out->screen_size = (longer >= 960 && shorter >= 720) ? NI_SCREENSIZE_XLARGE
                       : (longer >= 640 && shorter >= 480)  ? NI_SCREENSIZE_LARGE
                                                            : NI_SCREENSIZE_NORMAL;
    out->screen_long = longer * 3 >= shorter * 4 + shorter / 2 ? NI_SCREENLONG_YES : NI_SCREENLONG_NO;
    out->ui_mode_type = NI_UI_MODE_TYPE_NORMAL;
    out->ui_mode_night = NI_UI_MODE_NIGHT_NO;
    out->screen_width_dp = wdp;
    out->screen_height_dp = hdp;
    out->smallest_screen_width_dp = shorter;
    out->layout_direction = NI_LAYOUTDIR_LTR;
}

void AConfiguration_copy(AConfiguration *dest, AConfiguration *src) { *dest = *src; }

/* ACONFIGURATION_* change bits */
int32_t AConfiguration_diff(AConfiguration *a, AConfiguration *b) {
    int32_t d = 0;
    if (a->mcc != b->mcc) d |= 0x0001;
    if (a->mnc != b->mnc) d |= 0x0002;
    if (memcmp(a->language, b->language, 2) || memcmp(a->country, b->country, 2)) d |= 0x0004;
    if (a->touchscreen != b->touchscreen) d |= 0x0008;
    if (a->keyboard != b->keyboard) d |= 0x0010;
    if (a->keys_hidden != b->keys_hidden || a->nav_hidden != b->nav_hidden) d |= 0x0020;
    if (a->navigation != b->navigation) d |= 0x0040;
    if (a->orientation != b->orientation) d |= 0x0080;
    if (a->density != b->density) d |= 0x0100;
    if (a->screen_size != b->screen_size || a->screen_width_dp != b->screen_width_dp ||
        a->screen_height_dp != b->screen_height_dp)
        d |= 0x0200;
    if (a->sdk_version != b->sdk_version) d |= 0x0400;
    if (a->screen_long != b->screen_long) d |= 0x0800;
    if (a->ui_mode_type != b->ui_mode_type || a->ui_mode_night != b->ui_mode_night) d |= 0x1000;
    if (a->smallest_screen_width_dp != b->smallest_screen_width_dp) d |= 0x2000;
    if (a->layout_direction != b->layout_direction) d |= 0x4000;
    return d;
}

int32_t AConfiguration_match(AConfiguration *base, AConfiguration *requested) {
    SA_UNUSED(base);
    SA_UNUSED(requested);
    return 1;
}

int32_t AConfiguration_isBetterThan(AConfiguration *base, AConfiguration *test, AConfiguration *requested) {
    SA_UNUSED(base);
    SA_UNUSED(test);
    SA_UNUSED(requested);
    return 0;
}

void AConfiguration_getLanguage(AConfiguration *c, char *out) {
    out[0] = c->language[0];
    out[1] = c->language[1];
}

void AConfiguration_getCountry(AConfiguration *c, char *out) {
    out[0] = c->country[0];
    out[1] = c->country[1];
}

void AConfiguration_setLanguage(AConfiguration *c, const char *language) {
    c->language[0] = language[0];
    c->language[1] = language[0] ? language[1] : 0;
}

void AConfiguration_setCountry(AConfiguration *c, const char *country) {
    c->country[0] = country[0];
    c->country[1] = country[0] ? country[1] : 0;
}

#define NI_CONFIG_ACCESSOR(Name, field)                                          \
    int32_t AConfiguration_get##Name(AConfiguration *c) { return c->field; }

NI_CONFIG_ACCESSOR(Mcc, mcc)
NI_CONFIG_ACCESSOR(Mnc, mnc)
NI_CONFIG_ACCESSOR(Orientation, orientation)
NI_CONFIG_ACCESSOR(Touchscreen, touchscreen)
NI_CONFIG_ACCESSOR(Density, density)
NI_CONFIG_ACCESSOR(Keyboard, keyboard)
NI_CONFIG_ACCESSOR(Navigation, navigation)
NI_CONFIG_ACCESSOR(KeysHidden, keys_hidden)
NI_CONFIG_ACCESSOR(NavHidden, nav_hidden)
NI_CONFIG_ACCESSOR(SdkVersion, sdk_version)
NI_CONFIG_ACCESSOR(ScreenSize, screen_size)
NI_CONFIG_ACCESSOR(ScreenLong, screen_long)
NI_CONFIG_ACCESSOR(UiModeType, ui_mode_type)
NI_CONFIG_ACCESSOR(UiModeNight, ui_mode_night)
NI_CONFIG_ACCESSOR(ScreenWidthDp, screen_width_dp)
NI_CONFIG_ACCESSOR(ScreenHeightDp, screen_height_dp)
NI_CONFIG_ACCESSOR(SmallestScreenWidthDp, smallest_screen_width_dp)
NI_CONFIG_ACCESSOR(LayoutDirection, layout_direction)

/* setters for the same fields (the NDK has them; apps use them in tests and tools) */
#define NI_CONFIG_SETTER(Name, field) \
    void AConfiguration_set##Name(AConfiguration *c, int32_t v) { c->field = v; }

NI_CONFIG_SETTER(Mcc, mcc)
NI_CONFIG_SETTER(Mnc, mnc)
NI_CONFIG_SETTER(Orientation, orientation)
NI_CONFIG_SETTER(Touchscreen, touchscreen)
NI_CONFIG_SETTER(Density, density)
NI_CONFIG_SETTER(Keyboard, keyboard)
NI_CONFIG_SETTER(Navigation, navigation)
NI_CONFIG_SETTER(KeysHidden, keys_hidden)
NI_CONFIG_SETTER(NavHidden, nav_hidden)
NI_CONFIG_SETTER(SdkVersion, sdk_version)
NI_CONFIG_SETTER(ScreenSize, screen_size)
NI_CONFIG_SETTER(ScreenLong, screen_long)
NI_CONFIG_SETTER(UiModeType, ui_mode_type)
NI_CONFIG_SETTER(UiModeNight, ui_mode_night)
NI_CONFIG_SETTER(ScreenWidthDp, screen_width_dp)
NI_CONFIG_SETTER(ScreenHeightDp, screen_height_dp)
NI_CONFIG_SETTER(SmallestScreenWidthDp, smallest_screen_width_dp)
NI_CONFIG_SETTER(LayoutDirection, layout_direction)
