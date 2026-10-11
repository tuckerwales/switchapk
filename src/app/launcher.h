/*
 * The switchapk home screen: a carousel of the APKs in one folder, a details
 * panel for the selected app, a launch splash and the "app stopped" screen.
 *
 * Everything here draws into a caller-owned 1280x720 ARGB buffer and reads
 * input through UiInput, so the Switch entry point (main_switch.c) and the
 * host driver (main_host.c --launcher) share it. The host uses it for
 * scripted screenshots and tests.
 */
#ifndef SWITCHAPK_LAUNCHER_H
#define SWITCHAPK_LAUNCHER_H

#include "../core/common.h"

#define UI_W 1280
#define UI_H 720

/* Buttons, independent of libnx. The Switch maps HidNpadButton_*, the host maps Android keycodes. */
enum {
    UI_BTN_A = 1u << 0,
    UI_BTN_B = 1u << 1,
    UI_BTN_X = 1u << 2,
    UI_BTN_Y = 1u << 3,
    UI_BTN_L = 1u << 4,
    UI_BTN_R = 1u << 5,
    UI_BTN_ZL = 1u << 6,
    UI_BTN_ZR = 1u << 7,
    UI_BTN_PLUS = 1u << 8,
    UI_BTN_MINUS = 1u << 9,
    UI_BTN_UP = 1u << 10,
    UI_BTN_DOWN = 1u << 11,
    UI_BTN_LEFT = 1u << 12,
    UI_BTN_RIGHT = 1u << 13,
};

typedef struct {
    uint32_t down; /* pressed since the previous frame */
    uint32_t held; /* held now (drives key repeat) */
    bool touching;
    int touch_x, touch_y; /* screen pixels, valid while touching */
    int64_t now_ns;       /* monotonic clock (sa_time_ns) */
} UiInput;

typedef struct {
    char clock[16];   /* "14:05", or empty to hide */
    bool has_battery;
    int battery;      /* percent */
    bool charging;
    int wifi;         /* bars 0..3, or -1 when offline */
} UiStatus;

/* Frame results. IDLE: nothing changed and the buffer was not touched, so the caller may skip presenting. */
enum { UI_IDLE = 0, UI_REDRAW = 1, UI_RUN = 2, UI_BACK = 3, UI_RETRY = 4, UI_EXIT = 5 };

typedef struct Launcher Launcher;

/* Lists apk_dir and loads the saved sort order, last app and play times from state_dir/launcher.ini. APK labels
 * and icons come from the cache in state_dir/icons, or are read over the first frames while a progress screen
 * shows. state_dir is created when missing. */
Launcher *launcher_create(const char *apk_dir, const char *state_dir);
void launcher_destroy(Launcher *l);
/* One frame: handles input, animates and draws into screen when needed. Returns UI_IDLE, UI_REDRAW,
 * UI_RUN (an app was chosen, see launcher_chosen) or UI_EXIT. */
int launcher_frame(Launcher *l, uint32_t *screen, const UiInput *in, const UiStatus *st);
/* After UI_RUN: the chosen APK's full path (owned by the launcher). The launch is already recorded in the
 * state file, so the next launcher opens on this app and sorts it first under "Recently played". */
const char *launcher_chosen(const Launcher *l);

/* "Starting <label>" while the VM boots. Reads the APK's label and icon itself. */
void ui_draw_splash(uint32_t *screen, const char *apk_path);

/* The NRO icon on a size x size buffer, rendered at build time: the central square of src (crop is the fraction
 * of its shorter side to keep) when given, else the launcher's own mark. */
void ui_draw_app_icon(uint32_t *px, int size, const uint32_t *src, int src_w, int src_h, float crop);

typedef struct ErrorScreen ErrorScreen;

/* lines: the last log lines, oldest first (copied). log_path is shown so the user can find the full log. */
ErrorScreen *error_screen_create(const char *apk_path, int rc, const char *const *lines, int nlines,
                                 const char *log_path);
void error_screen_destroy(ErrorScreen *e);
/* Returns UI_IDLE, UI_REDRAW, UI_BACK (to the app list), UI_RETRY (run the app again) or UI_EXIT. */
int error_screen_frame(ErrorScreen *e, uint32_t *screen, const UiInput *in, const UiStatus *st);

#endif
