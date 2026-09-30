/*
 * Platform abstraction: display, input, text entry, audio and lifecycle.
 *
 * Implementations: platform_headless.c (host tests, scripted input and PNG
 * screenshots) and platform_switch.c (libnx).
 */
#ifndef SWITCHAPK_PLATFORM_H
#define SWITCHAPK_PLATFORM_H

#include "../core/common.h"

/* Event kinds delivered to the framework (android.view.InputQueue). */
enum {
    PEV_NONE = 0,
    PEV_TOUCH = 1,     /* action, pointer id, x, y (MotionEvent ACTION_DOWN/MOVE/UP) */
    PEV_KEY = 2,       /* action (0 down, 1 up), Android keycode, meta, repeat */
    PEV_JOYSTICK = 3,  /* axes: lx, ly, rx, ry, lt, rt, hat x, hat y */
    PEV_QUIT = 4,      /* user asked to leave (HOME + exit) */
    PEV_FOCUS = 5,     /* a = 1 gained, 0 lost (applet focus / suspend) */
    PEV_RESIZE = 6,    /* docked/handheld switch: new width, height, dpi in a, b, c */
    PEV_SENSOR = 7,    /* a = sensor type, f[0..2] values */
    PEV_TEXT = 8,      /* result of text input: text (malloc'd UTF-8), a = request id */
};

typedef struct {
    int kind;
    int a, b, c, d;       /* integer payload (see above) */
    float f[8];           /* float payload */
    int64_t time_ns;      /* event time, sa_time_ns() base */
    char *text;           /* PEV_TEXT only; owned by the receiver */
} PlatformEvent;

typedef struct {
    int width, height; /* pixels */
    int dpi;           /* Android density */
    float refresh_hz;
    bool touch;        /* has a touch screen right now (handheld) */
} PlatformDisplay;

bool platform_init(int argc, char **argv);
void platform_shutdown(void);
void platform_get_display(PlatformDisplay *d);

/* Presents a frame of ARGB (0xAARRGGBB) pixels. Blocks for vsync where the
 * platform supports it. Must be called without holding the VM lock. */
void platform_present(const uint32_t *argb, int w, int h, int stride);

/* Waits up to timeout_ms (-1 = forever) for an event or platform_wake().
 * Returns true if an event was stored. */
bool platform_wait_event(PlatformEvent *ev, int timeout_ms);
void platform_wake(void);
/* Pushes an event from any thread. */
void platform_push_event(const PlatformEvent *ev);

/* Shows the system keyboard. Result arrives later as PEV_TEXT with a = id. */
void platform_request_text(int id, const char *initial, const char *hint, int input_type, int max_len);

/* ---- audio: a single float stereo mixer stream ---- */
typedef void (*PlatformAudioCallback)(float *out, int frames, void *user);
bool platform_audio_start(int sample_rate, PlatformAudioCallback cb, void *user);
void platform_audio_stop(void);

/* ---- misc ---- */
void platform_vibrate(int ms);
/* Root of the writable data tree and the location of bundled files. */
const char *platform_data_root(void);
const char *platform_framework_path(void); /* directory holding framework.dex / framework-res.apk */
/* Called from the host driver / Switch main before the app starts. */
void platform_set_headless_script(const char *path);
void platform_set_screenshot_dir(const char *dir);
/* Headless: dumps the last presented frame to a PNG file. */
bool platform_screenshot(const char *path);
/* For GL: native window handle and EGL display (NULL when unsupported). */
void *platform_native_window(void);
bool platform_is_headless(void);

#endif
