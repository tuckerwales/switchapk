/*
 * Headless platform for host testing.
 *
 * Frames are kept in memory and can be written as PNG screenshots. Input
 * comes from a script file (see the .script files under tests/apps):
 *
 *   wait <ms>            sleep
 *   idle [ms]            wait until input is consumed and no frame was presented for ms (default 300)
 *   tap <x> <y>          touch down + up
 *   down|move|up <x> <y> raw touch events (pointer 0)
 *   swipe x0 y0 x1 y1 [ms]
 *   key <NAME|code>      key press (down + up), e.g. key BACK, key DPAD_DOWN
 *   keydown|keyup <NAME|code>
 *   text <string>        answer to the next soft keyboard request
 *   screen <W>x<H>@<dpi> change the display and send PEV_RESIZE (docked/handheld switch)
 *   sensor accel|gyro <x> <y> <z>  the value the sensor reports from now on (m/s^2 or rad/s, device axes)
 *   battery <level> [none|ac|usb]  battery percentage and charger
 *   screenshot <file>    write the last frame as PNG
 *   log <message>
 *   quit
 *
 * When the script ends the app receives PEV_QUIT.
 */
#include "platform.h"
#include "../gfx/gfx.h"

#include <pthread.h>
#include <time.h>
#include <errno.h>

#define LOG_TAG "headless"

static int g_width = 1280, g_height = 720, g_dpi = 240;
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_cond = PTHREAD_COND_INITIALIZER;
static PlatformEvent *g_queue;
static int g_qlen, g_qcap;
static bool g_woken;

static uint32_t *g_frame;
static int g_frame_w, g_frame_h;
static int g_win_x, g_win_y; /* where the last window frame was placed */
static int64_t g_last_present_ns;
static uint64_t g_frames;

static char *g_script_path;
static char *g_shot_dir;
static char *g_pending_text;
static int g_pending_text_id = -1;
static bool g_text_requested;

bool platform_is_headless(void) { return true; }

void platform_set_headless_script(const char *path) {
    free(g_script_path);
    g_script_path = path ? sa_strdup(path) : NULL;
}

void platform_set_screenshot_dir(const char *dir) {
    free(g_shot_dir);
    g_shot_dir = dir ? sa_strdup(dir) : NULL;
}

void platform_get_display(PlatformDisplay *d) {
    d->width = g_width;
    d->height = g_height;
    d->dpi = g_dpi;
    d->refresh_hz = 60.0f;
    d->touch = true;
}

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

void platform_present(const uint32_t *argb, int w, int h, int stride) {
    pthread_mutex_lock(&g_lock);
    if (g_frame && (g_frame_w != g_width || g_frame_h != g_height)) {
        /* the screen command changed the display size */
        free(g_frame);
        g_frame = NULL;
    }
    if (!g_frame) g_frame = sa_calloc((size_t)g_width * (size_t)g_height, 4);
    g_frame_w = g_width;
    g_frame_h = g_height;
    /* letterbox the window in the middle of the screen */
    int ox = (g_width - w) / 2, oy = (g_height - h) / 2;
    if (ox < 0) ox = 0;
    if (oy < 0) oy = 0;
    if (w != g_width || h != g_height)
        for (size_t i = 0; i < (size_t)g_width * (size_t)g_height; i++) g_frame[i] = 0xFF000000u;
    for (int y = 0; y < h && y + oy < g_height; y++) {
        int n = w;
        if (ox + n > g_width) n = g_width - ox;
        uint32_t *d = g_frame + (size_t)(y + oy) * (size_t)g_width + ox;
        const uint32_t *s = argb + (size_t)y * (size_t)stride;
        for (int x = 0; x < n; x++) d[x] = s[x] | 0xFF000000u;
    }
    g_win_x = ox;
    g_win_y = oy;
    g_last_present_ns = (int64_t)sa_time_ns();
    g_frames++;
    pthread_cond_broadcast(&g_cond);
    pthread_mutex_unlock(&g_lock);
    /* pace like a 60 Hz display so animations advance at real speed */
    sa_sleep_ns(4000000);
}

bool platform_screenshot(const char *path) {
    pthread_mutex_lock(&g_lock);
    if (!g_frame) {
        pthread_mutex_unlock(&g_lock);
        LOGW("screenshot %s: nothing presented yet", path);
        return false;
    }
    size_t len;
    uint8_t *png = gfx_encode_png(g_frame, g_frame_w, g_frame_h, g_frame_w, &len);
    pthread_mutex_unlock(&g_lock);
    bool ok = png && sa_write_file(path, png, len);
    free(png);
    LOGI("screenshot %s%s", path, ok ? "" : " FAILED");
    return ok;
}

void platform_request_text(int id, const char *initial, const char *hint, int input_type, int max_len) {
    SA_UNUSED(hint);
    SA_UNUSED(input_type);
    SA_UNUSED(max_len);
    pthread_mutex_lock(&g_lock);
    char *text = g_pending_text;
    g_pending_text = NULL;
    if (!text) {
        g_pending_text_id = id;
        g_text_requested = true;
    }
    pthread_mutex_unlock(&g_lock);
    if (text) {
        PlatformEvent ev = {0};
        ev.kind = PEV_TEXT;
        ev.a = id;
        ev.text = text;
        platform_push_event(&ev);
    } else {
        (void)initial;
    }
}

/* ---- script runner ---------------------------------------------------------------- */

static const struct {
    const char *name;
    int code;
} g_keys[] = {
    {"HOME", 3},        {"BACK", 4},          {"DPAD_UP", 19},     {"DPAD_DOWN", 20},    {"DPAD_LEFT", 21},
    {"DPAD_RIGHT", 22}, {"DPAD_CENTER", 23},  {"VOLUME_UP", 24},   {"VOLUME_DOWN", 25},  {"SPACE", 62},
    {"TAB", 61},        {"ENTER", 66},        {"DEL", 67},         {"MENU", 82},         {"ESCAPE", 111},
    {"BUTTON_A", 96},   {"BUTTON_B", 97},     {"BUTTON_X", 99},    {"BUTTON_Y", 100},    {"BUTTON_L1", 102},
    {"BUTTON_R1", 103}, {"BUTTON_L2", 104},   {"BUTTON_R2", 105},  {"BUTTON_THUMBL", 106}, {"BUTTON_THUMBR", 107},
    {"BUTTON_START", 108}, {"BUTTON_SELECT", 109},
};

static int key_code(const char *s) {
    for (size_t i = 0; i < SA_ARRAY_LEN(g_keys); i++)
        if (!strcmp(g_keys[i].name, s)) return g_keys[i].code;
    if (strlen(s) == 1 && s[0] >= 'A' && s[0] <= 'Z') return 29 + (s[0] - 'A');
    if (strlen(s) == 1 && s[0] >= '0' && s[0] <= '9') return 7 + (s[0] - '0');
    return atoi(s);
}

static void push_touch(int action, float x, float y) {
    PlatformEvent ev = {0};
    ev.kind = PEV_TOUCH;
    ev.a = action;
    ev.b = 0;
    ev.f[0] = x - (float)g_win_x;
    ev.f[1] = y - (float)g_win_y;
    platform_push_event(&ev);
}

static void push_key(int action, int code) {
    PlatformEvent ev = {0};
    ev.kind = PEV_KEY;
    ev.a = action;
    ev.b = code;
    platform_push_event(&ev);
}

static void wait_idle(int quiet_ms) {
    int64_t start = (int64_t)sa_time_ns();
    const int64_t t0 = start;
    for (;;) {
        pthread_mutex_lock(&g_lock);
        int64_t last = g_last_present_ns;
        bool has_frame = g_frame != NULL;
        bool pending = g_qlen > 0;
        pthread_mutex_unlock(&g_lock);
        int64_t now = (int64_t)sa_time_ns();
        /* quiet means: queued input consumed and no frame for quiet_ms since the later of the
         * last present and the start of the idle command */
        if (pending) start = now;
        int64_t ref = last > start ? last : start;
        if (has_frame && !pending && now - ref >= (int64_t)quiet_ms * 1000000) break;
        if (now - t0 > 20000000000LL) {
            LOGW("idle: app still drawing after 20s");
            break;
        }
        sa_sleep_ns(10000000);
    }
}

/* ---- sensors and battery: values set by the script, sampled by a thread while a sensor is on ---- */

static pthread_mutex_t g_sensor_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_sensor_cond = PTHREAD_COND_INITIALIZER;
static bool g_sensor_thread;
/* index 0 accelerometer, 1 gyroscope; the device lies flat, screen up */
static float g_sensor_value[2][3] = {{0.0f, 0.0f, 9.80665f}, {0.0f, 0.0f, 0.0f}};
static int g_sensor_period_us[2];
static int64_t g_sensor_due[2];
static PlatformBattery g_battery = {100, PLATFORM_PLUGGED_NONE, false, 4200, 250};

static int sensor_slot(int type) {
    return type == PLATFORM_SENSOR_ACCELEROMETER ? 0 : type == PLATFORM_SENSOR_GYROSCOPE ? 1 : -1;
}

static void set_sensor_value(int type, const float v[3]) {
    pthread_mutex_lock(&g_sensor_lock);
    memcpy(g_sensor_value[sensor_slot(type)], v, sizeof g_sensor_value[0]);
    pthread_mutex_unlock(&g_sensor_lock);
}

static void *sensor_thread(void *arg) {
    static const int types[2] = {PLATFORM_SENSOR_ACCELEROMETER, PLATFORM_SENSOR_GYROSCOPE};
    pthread_mutex_lock(&g_sensor_lock);
    for (;;) {
        int64_t now = (int64_t)sa_time_ns();
        int64_t next = -1;
        for (int i = 0; i < 2; i++) {
            if (g_sensor_period_us[i] <= 0) continue;
            if (now >= g_sensor_due[i]) {
                PlatformEvent ev = {0};
                ev.kind = PEV_SENSOR;
                ev.a = types[i];
                memcpy(ev.f, g_sensor_value[i], sizeof g_sensor_value[i]);
                ev.time_ns = now;
                platform_push_event(&ev);
                g_sensor_due[i] += (int64_t)g_sensor_period_us[i] * 1000;
                if (g_sensor_due[i] <= now) g_sensor_due[i] = now + (int64_t)g_sensor_period_us[i] * 1000;
            }
            if (next < 0 || g_sensor_due[i] < next) next = g_sensor_due[i];
        }
        if (next < 0) {
            pthread_cond_wait(&g_sensor_cond, &g_sensor_lock);
        } else {
            pthread_mutex_unlock(&g_sensor_lock);
            sa_sleep_ns((uint64_t)(next - now));
            pthread_mutex_lock(&g_sensor_lock);
        }
    }
    return arg;
}

unsigned platform_sensor_mask(void) {
    return (1u << PLATFORM_SENSOR_ACCELEROMETER) | (1u << PLATFORM_SENSOR_GYROSCOPE);
}

/* Host samples are paced by a thread at no faster than 200 Hz. */
void platform_sensor_set_rate(int type, int period_us) {
    int i = sensor_slot(type);
    if (i < 0) return;
    if (period_us > 0 && period_us < 5000) period_us = 5000;
    pthread_mutex_lock(&g_sensor_lock);
    if (period_us > 0 && g_sensor_period_us[i] <= 0) g_sensor_due[i] = (int64_t)sa_time_ns();
    g_sensor_period_us[i] = period_us > 0 ? period_us : 0;
    if (!g_sensor_thread) {
        g_sensor_thread = true;
        pthread_t th;
        pthread_create(&th, NULL, sensor_thread, NULL);
        pthread_detach(th);
    }
    pthread_cond_broadcast(&g_sensor_cond);
    pthread_mutex_unlock(&g_sensor_lock);
}

void platform_battery_state(PlatformBattery *b) {
    pthread_mutex_lock(&g_sensor_lock);
    *b = g_battery;
    pthread_mutex_unlock(&g_sensor_lock);
}

static void *script_thread(void *arg) {
    FILE *f = fopen(g_script_path, "r");
    if (!f) {
        LOGE("cannot open script %s", g_script_path);
    } else {
        char line[1024];
        int lineno = 0;
        while (fgets(line, sizeof line, f)) {
            lineno++;
            char *p = line;
            while (*p == ' ' || *p == '\t') p++;
            size_t n = strlen(p);
            while (n && (p[n - 1] == '\n' || p[n - 1] == '\r' || p[n - 1] == ' ')) p[--n] = 0;
            if (!*p || *p == '#') continue;
            char cmd[32] = {0};
            int off = 0;
            sscanf(p, "%31s %n", cmd, &off);
            const char *rest = p + off;
            float x0, y0, x1, y1;
            int ms;
            if (!strcmp(cmd, "wait")) {
                sa_sleep_ns((uint64_t)atoi(rest) * 1000000ull);
            } else if (!strcmp(cmd, "idle")) {
                wait_idle(*rest ? atoi(rest) : 300);
            } else if (!strcmp(cmd, "tap") && sscanf(rest, "%f %f", &x0, &y0) == 2) {
                push_touch(0, x0, y0);
                sa_sleep_ns(50000000);
                push_touch(1, x0, y0);
            } else if (!strcmp(cmd, "down") && sscanf(rest, "%f %f", &x0, &y0) == 2) {
                push_touch(0, x0, y0);
            } else if (!strcmp(cmd, "move") && sscanf(rest, "%f %f", &x0, &y0) == 2) {
                push_touch(2, x0, y0);
            } else if (!strcmp(cmd, "up") && sscanf(rest, "%f %f", &x0, &y0) == 2) {
                push_touch(1, x0, y0);
            } else if (!strcmp(cmd, "swipe") && sscanf(rest, "%f %f %f %f", &x0, &y0, &x1, &y1) == 4) {
                ms = 300;
                sscanf(rest, "%*f %*f %*f %*f %d", &ms);
                int steps = ms / 16 < 2 ? 2 : ms / 16;
                push_touch(0, x0, y0);
                for (int i = 1; i <= steps; i++) {
                    sa_sleep_ns(16000000);
                    push_touch(2, x0 + (x1 - x0) * (float)i / (float)steps, y0 + (y1 - y0) * (float)i / (float)steps);
                }
                push_touch(1, x1, y1);
            } else if (!strcmp(cmd, "key")) {
                int code = key_code(rest);
                push_key(0, code);
                sa_sleep_ns(30000000);
                push_key(1, code);
            } else if (!strcmp(cmd, "keydown")) {
                push_key(0, key_code(rest));
            } else if (!strcmp(cmd, "keyup")) {
                push_key(1, key_code(rest));
            } else if (!strcmp(cmd, "text")) {
                pthread_mutex_lock(&g_lock);
                bool requested = g_text_requested;
                int id = g_pending_text_id;
                g_text_requested = false;
                if (!requested) {
                    free(g_pending_text);
                    g_pending_text = sa_strdup(rest);
                }
                pthread_mutex_unlock(&g_lock);
                if (requested) {
                    PlatformEvent ev = {0};
                    ev.kind = PEV_TEXT;
                    ev.a = id;
                    ev.text = sa_strdup(rest);
                    platform_push_event(&ev);
                }
            } else if (!strcmp(cmd, "screen")) {
                int w, h, dpi;
                if (sscanf(rest, "%dx%d@%d", &w, &h, &dpi) == 3 && w > 0 && h > 0 && dpi > 0) {
                    pthread_mutex_lock(&g_lock);
                    g_width = w;
                    g_height = h;
                    g_dpi = dpi;
                    pthread_mutex_unlock(&g_lock);
                    PlatformEvent ev = {0};
                    ev.kind = PEV_RESIZE;
                    ev.a = w;
                    ev.b = h;
                    ev.c = dpi;
                    platform_push_event(&ev);
                } else {
                    LOGW("script line %d: screen wants WxH@dpi", lineno);
                }
            } else if (!strcmp(cmd, "sensor")) {
                char which[16];
                float v[3];
                if (sscanf(rest, "%15s %f %f %f", which, &v[0], &v[1], &v[2]) == 4 &&
                    (!strcmp(which, "accel") || !strcmp(which, "gyro"))) {
                    set_sensor_value(!strcmp(which, "accel") ? PLATFORM_SENSOR_ACCELEROMETER : PLATFORM_SENSOR_GYROSCOPE,
                                     v);
                } else {
                    LOGW("script line %d: sensor wants accel|gyro x y z", lineno);
                }
            } else if (!strcmp(cmd, "battery")) {
                int level;
                char plug[16] = "none";
                if (sscanf(rest, "%d %15s", &level, plug) >= 1 && level >= 0 && level <= 100) {
                    pthread_mutex_lock(&g_sensor_lock);
                    g_battery.level = level;
                    g_battery.plugged = !strcmp(plug, "ac") ? PLATFORM_PLUGGED_AC
                                        : !strcmp(plug, "usb") ? PLATFORM_PLUGGED_USB : PLATFORM_PLUGGED_NONE;
                    g_battery.charging = g_battery.plugged != PLATFORM_PLUGGED_NONE && level < 100;
                    pthread_mutex_unlock(&g_sensor_lock);
                } else {
                    LOGW("script line %d: battery wants <level 0..100> [none|ac|usb]", lineno);
                }
            } else if (!strcmp(cmd, "screenshot")) {
                char *path = (g_shot_dir && rest[0] != '/') ? sa_sprintf("%s/%s", g_shot_dir, rest) : sa_strdup(rest);
                platform_screenshot(path);
                free(path);
            } else if (!strcmp(cmd, "log")) {
                LOGI("script: %s", rest);
            } else if (!strcmp(cmd, "quit")) {
                break;
            } else {
                LOGW("script line %d: unknown command '%s'", lineno, p);
            }
        }
        fclose(f);
    }
    PlatformEvent ev = {0};
    ev.kind = PEV_QUIT;
    platform_push_event(&ev);
    return arg;
}

bool platform_init(int argc, char **argv) {
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--screen") && i + 1 < argc) {
            sscanf(argv[i + 1], "%dx%d@%d", &g_width, &g_height, &g_dpi);
        }
    }
    if (g_script_path) {
        pthread_t th;
        pthread_create(&th, NULL, script_thread, NULL);
        pthread_detach(th);
    }
    return true;
}

void platform_shutdown(void) {}

/* ---- audio: consume samples in real time and discard them ---- */

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
    g_audio_rate = sample_rate;
    g_audio_run = true;
    pthread_t th;
    pthread_create(&th, NULL, audio_thread, NULL);
    pthread_detach(th);
    return true;
}

void platform_audio_stop(void) { g_audio_run = false; }

void platform_vibrate(int ms, int amplitude) { LOGI("vibrate %d ms amplitude %d", ms, amplitude); }

/* The host reports Wi-Fi with full signal. "none", "wifi" or "ethernet" in <data root>/tmp/network (Android path
 * /data/local/tmp/network) or else in SWITCHAPK_NETWORK overrides it. Read on every call, so a test can change it
 * while the app runs. */
void platform_network_state(PlatformNetwork *n) {
    char mode[16] = "";
    char *path = sa_sprintf("%s/tmp/network", platform_data_root());
    FILE *f = fopen(path, "r");
    free(path);
    if (f) {
        if (!fgets(mode, sizeof mode, f)) mode[0] = 0;
        fclose(f);
        mode[strcspn(mode, " \r\n")] = 0;
    } else if (getenv("SWITCHAPK_NETWORK")) {
        snprintf(mode, sizeof mode, "%s", getenv("SWITCHAPK_NETWORK"));
    }
    n->connected = true;
    n->transport = PLATFORM_NET_WIFI;
    n->signal = 3;
    if (!strcmp(mode, "none")) {
        n->connected = false;
        n->transport = PLATFORM_NET_NONE;
        n->signal = -1;
    } else if (!strcmp(mode, "ethernet")) {
        n->transport = PLATFORM_NET_ETHERNET;
        n->signal = -1;
    }
}

const char *platform_framework_path(void) { return "build/java"; }

void *platform_native_window(void) { return NULL; }
