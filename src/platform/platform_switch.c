/*
 * Nintendo Switch platform (libnx).
 *
 * Threads: the main thread owns the applet loop, input polling and the
 * software keyboard (platform_switch_pump, called by main_switch.c about
 * every 8 ms); the VM thread presents frames and waits for events. The
 * event queue between them is the same mutex/condvar queue as the
 * headless platform.
 *
 * Display: a 1280x720 linear RGBA framebuffer (double buffered, presented
 * at vsync). Docked mode is upscaled by the system, so the app always sees
 * 1280x720 at 240 dpi (853x480 dp). Touch is reported in handheld mode.
 *
 * Input: Joy-Con / Pro Controller buttons become Android gamepad key codes
 * (A, B, X, Y, L1, R1, L2, R2, START (+), SELECT (-), thumb clicks) and the
 * D-pad becomes DPAD_*; the left stick also drives the D-pad with repeat,
 * as Android TV remotes do. Sticks are also sent as joystick axes.
 */
#include "platform.h"
#include "../gfx/gfx.h"

#include <switch.h>
#include <pthread.h>
#include <time.h>
#include <errno.h>

#define LOG_TAG "switch"

#define FB_W 1280
#define FB_H 720

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_cond = PTHREAD_COND_INITIALIZER;
static PlatformEvent *g_queue;
static int g_qlen, g_qcap;
static bool g_woken;

static Framebuffer g_fb;
static bool g_fb_ready;
static PadState g_pad;
static bool g_handheld = true;

bool platform_is_headless(void) { return false; }
void platform_set_headless_script(const char *path) { SA_UNUSED(path); }
void platform_set_screenshot_dir(const char *dir) { SA_UNUSED(dir); }
bool platform_screenshot(const char *path) {
    SA_UNUSED(path);
    return false;
}

void platform_get_display(PlatformDisplay *d) {
    d->width = FB_W;
    d->height = FB_H;
    d->dpi = 240;
    d->refresh_hz = 60.0f;
    d->touch = g_handheld;
}

/* ---- event queue ------------------------------------------------------------------ */

void platform_push_event(const PlatformEvent *ev) {
    pthread_mutex_lock(&g_lock);
    if (g_qlen == g_qcap) {
        g_qcap = g_qcap ? g_qcap * 2 : 64;
        g_queue = sa_realloc(g_queue, (size_t)g_qcap * sizeof *g_queue);
    }
    g_queue[g_qlen] = *ev;
    if (!g_queue[g_qlen].time_ns) g_queue[g_qlen].time_ns = (int64_t)sa_time_ns();
    g_qlen++;
    pthread_cond_broadcast(&g_cond);
    pthread_mutex_unlock(&g_lock);
}

void platform_wake(void) {
    pthread_mutex_lock(&g_lock);
    g_woken = true;
    pthread_cond_broadcast(&g_cond);
    pthread_mutex_unlock(&g_lock);
}

bool platform_wait_event(PlatformEvent *ev, int timeout_ms) {
    pthread_mutex_lock(&g_lock);
    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    if (timeout_ms > 0) {
        deadline.tv_sec += timeout_ms / 1000;
        deadline.tv_nsec += (long)(timeout_ms % 1000) * 1000000L;
        if (deadline.tv_nsec >= 1000000000L) {
            deadline.tv_sec++;
            deadline.tv_nsec -= 1000000000L;
        }
    }
    while (!g_qlen && !g_woken && timeout_ms != 0) {
        if (timeout_ms < 0) {
            pthread_cond_wait(&g_cond, &g_lock);
        } else if (pthread_cond_timedwait(&g_cond, &g_lock, &deadline) == ETIMEDOUT) {
            break;
        }
    }
    bool got = false;
    if (g_qlen) {
        *ev = g_queue[0];
        memmove(g_queue, g_queue + 1, (size_t)(g_qlen - 1) * sizeof *g_queue);
        g_qlen--;
        got = true;
    } else {
        g_woken = false;
    }
    pthread_mutex_unlock(&g_lock);
    return got;
}

/* ---- display -------------------------------------------------------------------- */

static pthread_mutex_t g_fb_lock = PTHREAD_MUTEX_INITIALIZER;
/* Where the last window frame was placed (letterboxed portrait windows); touch is relative to it. */
static volatile int g_win_x, g_win_y;

void platform_present(const uint32_t *argb, int w, int h, int stride) {
    if (!g_fb_ready) return;
    pthread_mutex_lock(&g_fb_lock);
    u32 fb_stride = 0;
    uint8_t *base = framebufferBegin(&g_fb, &fb_stride); /* waits for a free buffer (vsync pacing) */
    const int pitch = (int)(fb_stride / 4);
    int ox = (FB_W - w) / 2, oy = (FB_H - h) / 2;
    if (ox < 0) ox = 0;
    if (oy < 0) oy = 0;
    g_win_x = ox;
    g_win_y = oy;
    if (w < FB_W || h < FB_H) {
        for (int y = 0; y < FB_H; y++) {
            uint32_t *d = (uint32_t *)(base + (size_t)y * fb_stride);
            for (int x = 0; x < FB_W; x++) d[x] = 0xFF000000u;
        }
    }
    for (int y = 0; y < h && y + oy < FB_H; y++) {
        int n = w;
        if (ox + n > FB_W) n = FB_W - ox;
        uint32_t *d = (uint32_t *)base + (size_t)(y + oy) * (size_t)pitch + ox;
        const uint32_t *s = argb + (size_t)y * (size_t)stride;
        for (int x = 0; x < n; x++) {
            /* 0xAARRGGBB -> RGBA8888 bytes (R in the low byte) */
            const uint32_t c = s[x];
            d[x] = 0xFF000000u | ((c & 0xFFu) << 16) | (c & 0xFF00u) | ((c >> 16) & 0xFFu);
        }
    }
    framebufferEnd(&g_fb);
    pthread_mutex_unlock(&g_fb_lock);
}

void *platform_native_window(void) { return NULL; }

/* ---- input -------------------------------------------------------------------- */

enum {
    KEY_DPAD_UP = 19, KEY_DPAD_DOWN = 20, KEY_DPAD_LEFT = 21, KEY_DPAD_RIGHT = 22,
    KEY_BUTTON_A = 96, KEY_BUTTON_B = 97, KEY_BUTTON_X = 99, KEY_BUTTON_Y = 100,
    KEY_BUTTON_L1 = 102, KEY_BUTTON_R1 = 103, KEY_BUTTON_L2 = 104, KEY_BUTTON_R2 = 105,
    KEY_BUTTON_THUMBL = 106, KEY_BUTTON_THUMBR = 107, KEY_BUTTON_START = 108, KEY_BUTTON_SELECT = 109,
};

static const struct {
    u64 mask;
    int key;
} g_button_map[] = {
    {HidNpadButton_A, KEY_BUTTON_A},          {HidNpadButton_B, KEY_BUTTON_B},
    {HidNpadButton_X, KEY_BUTTON_X},          {HidNpadButton_Y, KEY_BUTTON_Y},
    {HidNpadButton_L, KEY_BUTTON_L1},         {HidNpadButton_R, KEY_BUTTON_R1},
    {HidNpadButton_ZL, KEY_BUTTON_L2},        {HidNpadButton_ZR, KEY_BUTTON_R2},
    {HidNpadButton_StickL, KEY_BUTTON_THUMBL}, {HidNpadButton_StickR, KEY_BUTTON_THUMBR},
    {HidNpadButton_Plus, KEY_BUTTON_START},   {HidNpadButton_Minus, KEY_BUTTON_SELECT},
    {HidNpadButton_Up, KEY_DPAD_UP},          {HidNpadButton_Down, KEY_DPAD_DOWN},
    {HidNpadButton_Left, KEY_DPAD_LEFT},      {HidNpadButton_Right, KEY_DPAD_RIGHT},
};

/* Left stick as a D-pad: one press when pushed past the threshold, then repeats. */
static const struct {
    u64 mask;
    int key;
} g_stick_map[] = {
    {HidNpadButton_StickLUp, KEY_DPAD_UP},
    {HidNpadButton_StickLDown, KEY_DPAD_DOWN},
    {HidNpadButton_StickLLeft, KEY_DPAD_LEFT},
    {HidNpadButton_StickLRight, KEY_DPAD_RIGHT},
};

static uint64_t g_stick_next_repeat[4];
static int g_stick_repeat_count[4];
static uint64_t g_button_down_at[16];
static int g_button_repeat_count[16];

static void push_key(int action, int key, int repeat) {
    PlatformEvent ev = {0};
    ev.kind = PEV_KEY;
    ev.a = action;
    ev.b = key;
    ev.c = 0;
    ev.d = repeat;
    platform_push_event(&ev);
}

/* Touch: tracks fingers by id to produce down / move / up. */
#define MAX_FINGERS 16
static struct {
    bool active;
    u32 id;
    float x, y;
} g_fingers[MAX_FINGERS];

static void push_touch(int action, int pointer, float x, float y) {
    PlatformEvent ev = {0};
    ev.kind = PEV_TOUCH;
    ev.a = action;
    ev.b = pointer;
    ev.f[0] = x - (float)g_win_x;
    ev.f[1] = y - (float)g_win_y;
    platform_push_event(&ev);
}

static void poll_touch(void) {
    HidTouchScreenState st = {0};
    bool seen[MAX_FINGERS] = {false};
    if (hidGetTouchScreenStates(&st, 1)) {
        for (int i = 0; i < st.count && i < MAX_FINGERS; i++) {
            const HidTouchState *ts = &st.touches[i];
            int slot = -1, free_slot = -1;
            for (int k = 0; k < MAX_FINGERS; k++) {
                if (g_fingers[k].active && g_fingers[k].id == ts->finger_id) slot = k;
                if (!g_fingers[k].active && free_slot < 0) free_slot = k;
            }
            const float x = (float)ts->x, y = (float)ts->y;
            if (slot < 0) {
                if (free_slot < 0) continue;
                slot = free_slot;
                g_fingers[slot].active = true;
                g_fingers[slot].id = ts->finger_id;
                g_fingers[slot].x = x;
                g_fingers[slot].y = y;
                push_touch(0 /* ACTION_DOWN */, slot, x, y);
            } else if (x != g_fingers[slot].x || y != g_fingers[slot].y) {
                g_fingers[slot].x = x;
                g_fingers[slot].y = y;
                push_touch(2 /* ACTION_MOVE */, slot, x, y);
            }
            seen[slot] = true;
        }
    }
    for (int k = 0; k < MAX_FINGERS; k++) {
        if (g_fingers[k].active && !seen[k]) {
            g_fingers[k].active = false;
            push_touch(1 /* ACTION_UP */, k, g_fingers[k].x, g_fingers[k].y);
        }
    }
}

static HidAnalogStickState g_last_l, g_last_r;

static void poll_pad(void) {
    padUpdate(&g_pad);
    const u64 down = padGetButtonsDown(&g_pad);
    const u64 up = padGetButtonsUp(&g_pad);
    const u64 held = padGetButtons(&g_pad);
    const uint64_t now = sa_time_ns();
    const int n = (int)(sizeof g_button_map / sizeof g_button_map[0]);
    for (int i = 0; i < n; i++) {
        if (down & g_button_map[i].mask) {
            push_key(0, g_button_map[i].key, 0);
            g_button_down_at[i] = now;
            g_button_repeat_count[i] = 0;
        } else if (up & g_button_map[i].mask) {
            push_key(1, g_button_map[i].key, 0);
        } else if ((held & g_button_map[i].mask) && i >= 12 /* D-pad repeats */) {
            /* key repeat: 400 ms delay, then every 80 ms */
            const uint64_t due = g_button_down_at[i] + 400000000ull + (uint64_t)g_button_repeat_count[i] * 80000000ull;
            if (now >= due) push_key(0, g_button_map[i].key, ++g_button_repeat_count[i]);
        }
    }
    for (int i = 0; i < 4; i++) {
        const u64 m = g_stick_map[i].mask;
        if (down & m) {
            push_key(0, g_stick_map[i].key, 0);
            g_stick_repeat_count[i] = 0;
            g_stick_next_repeat[i] = now + 400000000ull;
        } else if (up & m) {
            push_key(1, g_stick_map[i].key, 0);
        } else if ((held & m) && now >= g_stick_next_repeat[i]) {
            push_key(0, g_stick_map[i].key, ++g_stick_repeat_count[i]);
            g_stick_next_repeat[i] = now + 120000000ull;
        }
    }
    const HidAnalogStickState l = padGetStickPos(&g_pad, 0);
    const HidAnalogStickState r = padGetStickPos(&g_pad, 1);
    if (l.x != g_last_l.x || l.y != g_last_l.y || r.x != g_last_r.x || r.y != g_last_r.y) {
        g_last_l = l;
        g_last_r = r;
        PlatformEvent ev = {0};
        ev.kind = PEV_JOYSTICK;
        /* Android axes: x right positive, y down positive, range -1..1 */
        ev.f[0] = (float)l.x / JOYSTICK_MAX;
        ev.f[1] = -(float)l.y / JOYSTICK_MAX;
        ev.f[2] = (float)r.x / JOYSTICK_MAX;
        ev.f[3] = -(float)r.y / JOYSTICK_MAX;
        ev.f[4] = (held & HidNpadButton_ZL) ? 1.0f : 0.0f;
        ev.f[5] = (held & HidNpadButton_ZR) ? 1.0f : 0.0f;
        platform_push_event(&ev);
    }
}

/* ---- software keyboard (shown by the main thread) ---------------------------- */

static pthread_mutex_t g_kbd_lock = PTHREAD_MUTEX_INITIALIZER;
static bool g_kbd_pending;
static int g_kbd_id;
static char *g_kbd_initial, *g_kbd_hint;
static int g_kbd_type, g_kbd_max;

void platform_request_text(int id, const char *initial, const char *hint, int input_type, int max_len) {
    pthread_mutex_lock(&g_kbd_lock);
    free(g_kbd_initial);
    free(g_kbd_hint);
    g_kbd_initial = sa_strdup(initial ? initial : "");
    g_kbd_hint = sa_strdup(hint ? hint : "");
    g_kbd_id = id;
    g_kbd_type = input_type;
    g_kbd_max = max_len;
    g_kbd_pending = true;
    pthread_mutex_unlock(&g_kbd_lock);
}

static void show_keyboard(void) {
    pthread_mutex_lock(&g_kbd_lock);
    if (!g_kbd_pending) {
        pthread_mutex_unlock(&g_kbd_lock);
        return;
    }
    g_kbd_pending = false;
    const int id = g_kbd_id, type = g_kbd_type, max = g_kbd_max;
    char *initial = g_kbd_initial, *hint = g_kbd_hint;
    g_kbd_initial = g_kbd_hint = NULL;
    pthread_mutex_unlock(&g_kbd_lock);

    char *result = NULL;
    SwkbdConfig kbd;
    if (R_SUCCEEDED(swkbdCreate(&kbd, 0))) {
        swkbdConfigMakePresetDefault(&kbd);
        const int cls = type & 0xF;          /* InputType.TYPE_MASK_CLASS */
        const int variation = type & 0xFF0;  /* InputType.TYPE_MASK_VARIATION */
        if (cls == 2 /* TYPE_CLASS_NUMBER */ || cls == 3 /* TYPE_CLASS_PHONE */) swkbdConfigSetType(&kbd, SwkbdType_NumPad);
        if (cls == 1 && (variation == 0x80 || variation == 0xE0 || variation == 0x90)) {
            swkbdConfigSetPasswordFlag(&kbd, 1); /* TYPE_TEXT_VARIATION_*PASSWORD */
        }
        if (max > 0) swkbdConfigSetStringLenMax(&kbd, (u32)max);
        swkbdConfigSetInitialText(&kbd, initial);
        swkbdConfigSetGuideText(&kbd, hint);
        char out[2048];
        out[0] = 0;
        if (R_SUCCEEDED(swkbdShow(&kbd, out, sizeof out))) result = sa_strdup(out);
        swkbdClose(&kbd);
    }
    free(initial);
    free(hint);
    /* A cancelled keyboard answers null: the field is left as it was. */
    PlatformEvent ev = {0};
    ev.kind = PEV_TEXT;
    ev.a = id;
    ev.text = result;
    platform_push_event(&ev);
}

/* ---- applet ----------------------------------------------------------------- */

static AppletHookCookie g_hook_cookie;
static volatile bool g_quit_sent;

static void send_quit(void) {
    if (g_quit_sent) return;
    g_quit_sent = true;
    PlatformEvent ev = {0};
    ev.kind = PEV_QUIT;
    platform_push_event(&ev);
}

static void applet_hook(AppletHookType hook, void *param) {
    SA_UNUSED(param);
    switch (hook) {
        case AppletHookType_OnFocusState: {
            PlatformEvent ev = {0};
            ev.kind = PEV_FOCUS;
            ev.a = appletGetFocusState() == AppletFocusState_InFocus ? 1 : 0;
            platform_push_event(&ev);
            break;
        }
        case AppletHookType_OnOperationMode:
            g_handheld = appletGetOperationMode() == AppletOperationMode_Handheld;
            break;
        case AppletHookType_OnExitRequest:
            send_quit();
            break;
        default:
            break;
    }
}

/* ---- motion sensors: the six-axis sensor of the controller in use (main thread) ---- */

/* Sensor periods requested by the VM thread; the main thread applies them in poll_sixaxis. */
static pthread_mutex_t g_motion_lock = PTHREAD_MUTEX_INITIALIZER;
static int g_sensor_period_us[2]; /* 0 accelerometer, 1 gyroscope */
static int64_t g_sensor_due[2];
static HidSixAxisSensorHandle g_sixaxis[2];
static int g_sixaxis_count;
static u32 g_sixaxis_style; /* style the handles belong to, 0 = none started */
static u64 g_sixaxis_sample;

unsigned platform_sensor_mask(void) {
    return (1u << PLATFORM_SENSOR_ACCELEROMETER) | (1u << PLATFORM_SENSOR_GYROSCOPE);
}

void platform_sensor_set_rate(int type, int period_us) {
    int i = type == PLATFORM_SENSOR_ACCELEROMETER ? 0 : type == PLATFORM_SENSOR_GYROSCOPE ? 1 : -1;
    if (i < 0) return;
    pthread_mutex_lock(&g_motion_lock);
    if (period_us > 0 && g_sensor_period_us[i] <= 0) g_sensor_due[i] = 0;
    g_sensor_period_us[i] = period_us > 0 ? period_us : 0;
    pthread_mutex_unlock(&g_motion_lock);
}

static void stop_sixaxis(void) {
    for (int i = 0; i < g_sixaxis_count; i++) hidStopSixAxisSensor(g_sixaxis[i]);
    g_sixaxis_count = 0;
    g_sixaxis_style = 0;
}

/* Starts the sensors of the handheld Joy-Cons, or else of player 1's controller, when that changes. */
static void start_sixaxis(void) {
    HidNpadIdType id = HidNpadIdType_Handheld;
    u32 style = hidGetNpadStyleSet(HidNpadIdType_Handheld) & HidNpadStyleTag_NpadHandheld;
    if (!style) {
        id = HidNpadIdType_No1;
        u32 set = hidGetNpadStyleSet(HidNpadIdType_No1);
        style = (set & HidNpadStyleTag_NpadFullKey)    ? HidNpadStyleTag_NpadFullKey
                : (set & HidNpadStyleTag_NpadJoyDual)  ? HidNpadStyleTag_NpadJoyDual
                : (set & HidNpadStyleTag_NpadJoyLeft)  ? HidNpadStyleTag_NpadJoyLeft
                : (set & HidNpadStyleTag_NpadJoyRight) ? HidNpadStyleTag_NpadJoyRight
                                                        : 0;
    }
    if (style == g_sixaxis_style) return;
    stop_sixaxis();
    if (!style) return;
    int n = (style == HidNpadStyleTag_NpadHandheld || style == HidNpadStyleTag_NpadJoyDual) ? 2 : 1;
    if (R_FAILED(hidGetSixAxisSensorHandles(g_sixaxis, n, id, (HidNpadStyleTag)style))) return;
    for (int i = 0; i < n; i++) hidStartSixAxisSensor(g_sixaxis[i]);
    g_sixaxis_count = n;
    g_sixaxis_style = style;
}

/*
 * The six-axis sensor reports acceleration in G and angular velocity in turns per second, in the controller's
 * frame: x right, y toward the top edge, z out of the face. Android wants m/s^2 for the force that holds the device
 * up (gravity reads +9.8 on z when it lies screen up) and rad/s. The HID values point along gravity, so the
 * acceleration is negated. Not yet checked on hardware: if tilting reads backwards, flip ACCEL_SIGN.
 */
#define ACCEL_SIGN (-9.80665f)
#define GYRO_SCALE (6.2831853f)

static void poll_sixaxis(void) {
    int period[2];
    int64_t due[2];
    pthread_mutex_lock(&g_motion_lock);
    memcpy(period, g_sensor_period_us, sizeof period);
    memcpy(due, g_sensor_due, sizeof due);
    pthread_mutex_unlock(&g_motion_lock);
    if (period[0] <= 0 && period[1] <= 0) {
        if (g_sixaxis_count) stop_sixaxis();
        return;
    }
    start_sixaxis();
    if (!g_sixaxis_count) return;
    HidSixAxisSensorState st;
    if (!hidGetSixAxisSensorStates(g_sixaxis[0], &st, 1) || st.sampling_number == g_sixaxis_sample) return;
    g_sixaxis_sample = st.sampling_number;
    const int64_t now = (int64_t)sa_time_ns();
    for (int i = 0; i < 2; i++) {
        if (period[i] <= 0 || now < due[i]) continue;
        PlatformEvent ev = {0};
        ev.kind = PEV_SENSOR;
        ev.time_ns = now;
        if (i == 0) {
            ev.a = PLATFORM_SENSOR_ACCELEROMETER;
            ev.f[0] = st.acceleration.x * ACCEL_SIGN;
            ev.f[1] = st.acceleration.y * ACCEL_SIGN;
            ev.f[2] = st.acceleration.z * ACCEL_SIGN;
        } else {
            ev.a = PLATFORM_SENSOR_GYROSCOPE;
            ev.f[0] = st.angular_velocity.x * GYRO_SCALE;
            ev.f[1] = st.angular_velocity.y * GYRO_SCALE;
            ev.f[2] = st.angular_velocity.z * GYRO_SCALE;
        }
        platform_push_event(&ev);
        due[i] = (due[i] && now - due[i] < (int64_t)period[i] * 1000 ? due[i] : now) + (int64_t)period[i] * 1000;
    }
    pthread_mutex_lock(&g_motion_lock);
    for (int i = 0; i < 2; i++) {
        if (g_sensor_period_us[i] == period[i]) g_sensor_due[i] = due[i];
    }
    pthread_mutex_unlock(&g_motion_lock);
}

/* ---- rumble: requested from any thread, sent to the controller in use by the main thread ---- */

static int64_t g_rumble_until; /* sa_time_ns deadline, 0 = off */
static float g_rumble_amp;
static bool g_rumble_on;
static HidVibrationDeviceHandle g_rumble_dev[2];
static u32 g_rumble_style;

/* libnx's randomGet is a ChaCha20 generator seeded from the kernel's entropy (svcGetInfo RandomEntropy). */
bool platform_random_bytes(void *buf, size_t len) {
    randomGet(buf, len);
    return true;
}

void platform_vibrate(int ms, int amplitude) {
    pthread_mutex_lock(&g_motion_lock);
    g_rumble_until = ms > 0 ? (int64_t)sa_time_ns() + (int64_t)ms * 1000000 : 0;
    g_rumble_amp = amplitude < 0 ? 0.5f : (float)(amplitude > 255 ? 255 : amplitude) / 255.0f;
    pthread_mutex_unlock(&g_motion_lock);
}

static void update_rumble(void) {
    pthread_mutex_lock(&g_motion_lock);
    const bool want = g_rumble_until && (int64_t)sa_time_ns() < g_rumble_until;
    const float amp = g_rumble_amp;
    if (!want) g_rumble_until = 0;
    pthread_mutex_unlock(&g_motion_lock);
    if (!want && !g_rumble_on) return;
    const bool handheld = hidGetNpadStyleSet(HidNpadIdType_Handheld) & HidNpadStyleTag_NpadHandheld;
    const u32 style = handheld ? HidNpadStyleTag_NpadHandheld : HidNpadStyleTag_NpadFullKey;
    const u32 set = handheld ? style : hidGetNpadStyleSet(HidNpadIdType_No1);
    const u32 use = handheld ? style : (set & HidNpadStyleTag_NpadFullKey) ? HidNpadStyleTag_NpadFullKey
                                                                            : HidNpadStyleTag_NpadJoyDual;
    if (use != g_rumble_style) {
        if (R_FAILED(hidInitializeVibrationDevices(g_rumble_dev, use == HidNpadStyleTag_NpadFullKey ? 1 : 2,
                                                   handheld ? HidNpadIdType_Handheld : HidNpadIdType_No1,
                                                   (HidNpadStyleTag)use))) {
            return;
        }
        g_rumble_style = use;
    }
    HidVibrationValue v[2];
    for (int i = 0; i < 2; i++) {
        v[i].amp_low = want ? amp : 0.0f;
        v[i].freq_low = 160.0f;
        v[i].amp_high = want ? amp : 0.0f;
        v[i].freq_high = 320.0f;
    }
    hidSendVibrationValues(g_rumble_dev, v, g_rumble_style == HidNpadStyleTag_NpadFullKey ? 1 : 2);
    g_rumble_on = want;
}

/* Called by main_switch.c on the main thread while the app runs. False once the system asked to exit. */
bool platform_switch_pump(void) {
    if (!appletMainLoop()) {
        send_quit();
        return false;
    }
    poll_pad();
    poll_touch();
    poll_sixaxis();
    update_rumble();
    show_keyboard();
    return true;
}

/* For the launcher and error screen, which run before or after the VM. */
u64 platform_switch_buttons_down(void) {
    padUpdate(&g_pad);
    return padGetButtonsDown(&g_pad);
}

bool platform_init(int argc, char **argv) {
    SA_UNUSED(argc);
    SA_UNUSED(argv);
    static bool done;
    if (done) return true;
    done = true;
    padConfigureInput(8, HidNpadStyleSet_NpadStandard);
    padInitializeAny(&g_pad);
    hidInitializeTouchScreen();
    g_handheld = appletGetOperationMode() == AppletOperationMode_Handheld;
    appletHook(&g_hook_cookie, applet_hook, NULL);
    if (R_SUCCEEDED(framebufferCreate(&g_fb, nwindowGetDefault(), FB_W, FB_H, PIXEL_FORMAT_RGBA_8888, 2))) {
        framebufferMakeLinear(&g_fb);
        g_fb_ready = true;
    } else {
        LOGE("framebufferCreate failed");
    }
    return g_fb_ready;
}

/* nifm and psm are opened on first use (platform_network_state, platform_battery_state) and closed here. */
static bool g_nifm;
static bool g_psm;

void platform_shutdown(void) {
    appletUnhook(&g_hook_cookie);
    if (g_nifm) {
        nifmExit();
        g_nifm = false;
    }
    if (g_psm) {
        psmExit();
        g_psm = false;
    }
    stop_sixaxis();
    platform_vibrate(0, 0);
    update_rumble();
    /* A Java thread may still be presenting: wait for it, then stop presenting for good. */
    pthread_mutex_lock(&g_fb_lock);
    if (g_fb_ready) {
        g_fb_ready = false;
        framebufferClose(&g_fb);
    }
    pthread_mutex_unlock(&g_fb_lock);
}

/* ---- audio: drained in real time until the audout backend lands ---------------- */

static PlatformAudioCallback g_audio_cb;
static void *g_audio_user;
static int g_audio_rate;
static volatile bool g_audio_run;

static void *audio_thread(void *arg) {
    float buf[2 * 1024];
    while (g_audio_run) {
        g_audio_cb(buf, 1024, g_audio_user);
        sa_sleep_ns((uint64_t)1024 * 1000000000ull / (uint64_t)g_audio_rate);
    }
    return arg;
}

bool platform_audio_start(int sample_rate, PlatformAudioCallback cb, void *user) {
    if (g_audio_run) return true;
    g_audio_cb = cb;
    g_audio_user = user;
    g_audio_rate = sample_rate > 0 ? sample_rate : 48000;
    g_audio_run = true;
    pthread_t th;
    pthread_create(&th, NULL, audio_thread, NULL);
    pthread_detach(th);
    return true;
}

void platform_audio_stop(void) { g_audio_run = false; }

void platform_battery_state(PlatformBattery *b) {
    b->level = 100;
    b->plugged = PLATFORM_PLUGGED_NONE;
    b->charging = false;
    b->voltage_mv = 0;
    b->temperature = 250;
    if (!g_psm) {
        if (R_FAILED(psmInitialize())) return;
        g_psm = true;
    }
    u32 pct = 100;
    if (R_SUCCEEDED(psmGetBatteryChargePercentage(&pct))) b->level = pct > 100 ? 100 : (int)pct;
    PsmChargerType charger = PsmChargerType_Unconnected;
    if (R_SUCCEEDED(psmGetChargerType(&charger)) && charger != PsmChargerType_Unconnected) {
        b->plugged = charger == PsmChargerType_EnoughPower ? PLATFORM_PLUGGED_AC : PLATFORM_PLUGGED_USB;
        b->charging = b->level < 100;
    }
}

void platform_network_state(PlatformNetwork *n) {
    n->connected = false;
    n->transport = PLATFORM_NET_NONE;
    n->signal = -1;
    if (!g_nifm) {
        if (R_FAILED(nifmInitialize(NifmServiceType_User))) return;
        g_nifm = true;
    }
    NifmInternetConnectionType type;
    u32 strength = 0;
    NifmInternetConnectionStatus status;
    if (R_FAILED(nifmGetInternetConnectionStatus(&type, &strength, &status))) return;
    n->connected = status == NifmInternetConnectionStatus_Connected;
    n->transport = type == NifmInternetConnectionType_Ethernet ? PLATFORM_NET_ETHERNET : PLATFORM_NET_WIFI;
    n->signal = type == NifmInternetConnectionType_Ethernet ? -1 : (int)(strength > 3 ? 3 : strength);
}

const char *platform_framework_path(void) { return "romfs:"; }
