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
/* Called (without platform locks held) after every platform_wake and platform_push_event, so a main thread that
 * blocks somewhere else (the main ALooper) can be woken. NULL removes it. */
void platform_set_wake_hook(void (*hook)(void));

/* Shows the system keyboard. Result arrives later as PEV_TEXT with a = id. */
void platform_request_text(int id, const char *initial, const char *hint, int input_type, int max_len);

/* ---- audio: a single float stereo mixer stream ---- */
typedef void (*PlatformAudioCallback)(float *out, int frames, void *user);
bool platform_audio_start(int sample_rate, PlatformAudioCallback cb, void *user);
void platform_audio_stop(void);

/* ---- network ---- */
enum { PLATFORM_NET_NONE = 0, PLATFORM_NET_WIFI = 1, PLATFORM_NET_ETHERNET = 2 };
typedef struct {
    bool connected; /* internet reachable as far as the system knows */
    int transport;  /* PLATFORM_NET_* */
    int signal;     /* Wi-Fi bars 0..3, -1 when unknown */
} PlatformNetwork;
/* Cheap enough to poll every few seconds. */
void platform_network_state(PlatformNetwork *n);

/* ---- sensors (WS15) ---- */
/* Android sensor type numbers (Sensor.TYPE_*) of the motion sensors a platform may have. */
enum { PLATFORM_SENSOR_ACCELEROMETER = 1, PLATFORM_SENSOR_GYROSCOPE = 4 };
/* Bit (1 << type) for every sensor present now. */
unsigned platform_sensor_mask(void);
/* Starts sampling a sensor with about period_us between samples, or stops it when period_us <= 0. Samples arrive
 * as PEV_SENSOR with a = type and f[0..2] in Android units and device axes (m/s^2 and rad/s; x right, y up along
 * the screen in its natural landscape orientation, z out of the screen). */
void platform_sensor_set_rate(int type, int period_us);

/* ---- battery (WS15) ---- */
enum { PLATFORM_PLUGGED_NONE = 0, PLATFORM_PLUGGED_AC = 1, PLATFORM_PLUGGED_USB = 2 };
typedef struct {
    int level;       /* percent, 0..100 */
    int plugged;     /* PLATFORM_PLUGGED_* */
    bool charging;   /* plugged and gaining charge */
    int voltage_mv;  /* millivolts, 0 when unknown */
    int temperature; /* tenths of a degree Celsius */
} PlatformBattery;
/* Cheap enough to poll every few seconds. */
void platform_battery_state(PlatformBattery *b);

/* ---- misc ---- */
/* Rumble for ms milliseconds (0 stops). amplitude is 1..255, or -1 for the default strength. */
void platform_vibrate(int ms, int amplitude);
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
