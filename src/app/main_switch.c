/*
 * Nintendo Switch entry point (switchapk.nro).
 *
 * Without arguments it opens the home screen (launcher.c): a carousel of the
 * .apk files in sdmc:/switch/switchapk/apks with their own icons and names,
 * sorted by last played. With an APK path as argv[1] (nxlink, forwarders,
 * "Try again") it runs that APK directly.
 * The chosen APK runs on a VM thread with a large stack while this thread
 * pumps the applet loop, input and the software keyboard. When the app ends
 * the NRO reloads itself (through hbloader) so the user returns to the list.
 * If the app fails, the "app stopped" screen shows the last log lines and
 * offers to go back or try again; the full log is in
 * sdmc:/switch/switchapk/log.txt.
 */
#include "../vm/vm.h"
#include "../gfx/gfx.h"
#include "../platform/platform.h"
#include "launcher.h"

#include <switch.h>
#include <pthread.h>
#include <strings.h>
#include <sys/stat.h>
#include <time.h>

#define LOG_TAG "main"

#define ROOT_DIR "sdmc:/switch/switchapk"
#define APK_DIR ROOT_DIR "/apks"
#define DATA_DIR ROOT_DIR "/data"
#define LAUNCHER_DIR ROOT_DIR "/launcher"
#define LOG_PATH ROOT_DIR "/log.txt"

bool platform_switch_pump(void);
u64 platform_switch_buttons_down(void);
u64 platform_switch_buttons_held(void);
void platform_set_data_root(const char *root, const char *package);
int app_run_apk(const char *path, const char *data_dir, void *stack_hi);

static uint32_t *g_screen;

static void present_screen(void) { platform_present(g_screen, UI_W, UI_H, UI_W); }

/* ---- home screen, splash and error screen driver ------------------------------------ */

static const struct {
    u64 mask;
    uint32_t btn;
} g_ui_buttons[] = {
    {HidNpadButton_A, UI_BTN_A},         {HidNpadButton_B, UI_BTN_B},
    {HidNpadButton_X, UI_BTN_X},         {HidNpadButton_Y, UI_BTN_Y},
    {HidNpadButton_L, UI_BTN_L},         {HidNpadButton_R, UI_BTN_R},
    {HidNpadButton_ZL, UI_BTN_ZL},       {HidNpadButton_ZR, UI_BTN_ZR},
    {HidNpadButton_Plus, UI_BTN_PLUS},   {HidNpadButton_Minus, UI_BTN_MINUS},
    {HidNpadButton_AnyUp, UI_BTN_UP},    {HidNpadButton_AnyDown, UI_BTN_DOWN},
    {HidNpadButton_AnyLeft, UI_BTN_LEFT}, {HidNpadButton_AnyRight, UI_BTN_RIGHT},
};

static uint32_t ui_buttons(u64 hid) {
    uint32_t out = 0;
    for (size_t i = 0; i < SA_ARRAY_LEN(g_ui_buttons); i++)
        if (hid & g_ui_buttons[i].mask) out |= g_ui_buttons[i].btn;
    return out;
}

static UiInput read_input(void) {
    UiInput in;
    memset(&in, 0, sizeof in);
    in.down = ui_buttons(platform_switch_buttons_down());
    in.held = ui_buttons(platform_switch_buttons_held());
    HidTouchScreenState ts = {0};
    if (hidGetTouchScreenStates(&ts, 1) && ts.count > 0) {
        in.touching = true;
        in.touch_x = (int)ts.touches[0].x;
        in.touch_y = (int)ts.touches[0].y;
    }
    in.now_ns = (int64_t)sa_time_ns();
    return in;
}

/* Clock every frame; battery and Wi-Fi every two seconds (each is an IPC call). */
static void read_status(UiStatus *st) {
    static UiStatus cached;
    static int64_t next_poll;
    int64_t now = (int64_t)sa_time_ns();
    if (now >= next_poll) {
        next_poll = now + 2000000000LL;
        PlatformBattery bat;
        platform_battery_state(&bat);
        cached.has_battery = true;
        cached.battery = bat.level;
        cached.charging = bat.charging;
        PlatformNetwork net;
        platform_network_state(&net);
        cached.wifi = net.connected ? (net.signal >= 0 ? net.signal : 3) : -1;
    }
    *st = cached;
    time_t t = time(NULL);
    struct tm tm;
    if (t > 0 && localtime_r(&t, &tm)) snprintf(st->clock, sizeof st->clock, "%02d:%02d", tm.tm_hour, tm.tm_min);
    else st->clock[0] = 0;
}

typedef int (*UiFrameFn)(void *ctx, uint32_t *screen, const UiInput *in, const UiStatus *st);

static int frame_launcher(void *ctx, uint32_t *screen, const UiInput *in, const UiStatus *st) {
    return launcher_frame(ctx, screen, in, st);
}

static int frame_error(void *ctx, uint32_t *screen, const UiInput *in, const UiStatus *st) {
    return error_screen_frame(ctx, screen, in, st);
}

/* Runs a screen until it returns a result. Frames that change nothing are not presented. */
static int ui_loop(UiFrameFn fn, void *ctx) {
    while (appletMainLoop()) {
        UiInput in = read_input();
        UiStatus st;
        read_status(&st);
        int rc = fn(ctx, g_screen, &in, &st);
        if (rc == UI_REDRAW) present_screen(); /* waits for vsync */
        else if (rc == UI_IDLE) svcSleepThread(16000000ll);
        else return rc;
    }
    return UI_EXIT;
}

/* ---- VM thread ------------------------------------------------------------------- */

typedef struct {
    const char *apk;
    volatile bool done;
    int rc;
} RunArgs;

static void *vm_thread(void *arg) {
    RunArgs *ra = arg;
    volatile int stack_marker = 0;
    /* libnx starts every thread on the main thread's core; give the app its own (core 1). */
    svcSetThreadCoreMask(CUR_THREAD_HANDLE, 1, 1u << 1);
    size_t len = 0;
    uint8_t *fw = sa_read_file("romfs:/framework.dex", &len);
    if (!fw) {
        LOGE("romfs:/framework.dex is missing");
        ra->rc = 1;
        ra->done = true;
        return NULL;
    }
    DexFile *d = dex_open(fw, len, false, "framework.dex");
    if (!d) {
        LOGE("cannot open framework.dex");
        ra->rc = 1;
        ra->done = true;
        return NULL;
    }
    d->owned = true;
    vm_add_boot_dex(d);
    LOGI("running %s", ra->apk);
    ra->rc = app_run_apk(ra->apk, DATA_DIR, (void *)&stack_marker);
    LOGI("app finished with %d", ra->rc);
    ra->done = true;
    return NULL;
}

static int run_apk(const char *apk) {
    RunArgs ra = {apk, false, 0};
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, 16u * 1024 * 1024);
    pthread_t th;
    if (pthread_create(&th, &attr, vm_thread, &ra) != 0) {
        LOGE("cannot start the VM thread");
        return 1;
    }
    pthread_attr_destroy(&attr);
    for (int frame = 0; !ra.done; frame++) {
        platform_switch_pump();
        if (frame % 64 == 0) sa_log_flush(); /* about twice a second */
        svcSleepThread(8000000ll);
    }
    pthread_join(th, NULL);
    return ra.rc;
}

/* ---- main ----------------------------------------------------------------------- */

static void make_dirs(void) {
    mkdir("sdmc:/switch", 0777);
    mkdir(ROOT_DIR, 0777);
    mkdir(APK_DIR, 0777);
    mkdir(DATA_DIR, 0777);
    mkdir(LAUNCHER_DIR, 0777);
}

/* Makes hbloader start this NRO again once we exit: the home screen, or straight into apk ("Try again"). */
static void relaunch(int argc, char **argv, const char *apk) {
    if (argc < 1 || !argv[0] || !envHasNextLoad()) return;
    if (!apk) {
        envSetNextLoad(argv[0], argv[0]);
        return;
    }
    /* libnx splits argv on spaces and honours double quotes */
    char *args = sa_sprintf("\"%s\" \"%s\"", argv[0], apk);
    envSetNextLoad(argv[0], args);
    free(args);
}

int main(int argc, char **argv) {
    romfsInit();
    plInitialize(PlServiceType_User);
    socketInitializeDefault();
    bool nxlink = nxlinkStdio() >= 0; /* stdout/stderr to the nxlink host when launched with nxlink -s */
    make_dirs();
    FILE *log = fopen(LOG_PATH, "w");
    if (log) sa_log_set_file(log);
    LOGI("switchapk starting (%s)", nxlink ? "nxlink" : "no nxlink");

    vm_init();
    platform_init(argc, argv);
    g_screen = sa_calloc((size_t)UI_W * UI_H, sizeof *g_screen);
    gfx_font_init_default();

    char *apk = NULL;
    if (argc > 1) {
        size_t n = strlen(argv[1]);
        if (n > 4 && !strcasecmp(argv[1] + n - 4, ".apk")) apk = sa_strdup(argv[1]);
    }
    if (!apk) {
        Launcher *l = launcher_create(APK_DIR, LAUNCHER_DIR);
        if (ui_loop(frame_launcher, l) == UI_RUN) apk = sa_strdup(launcher_chosen(l));
        launcher_destroy(l);
    }

    if (apk) {
        ui_draw_splash(g_screen, apk);
        present_screen();
        int rc = run_apk(apk);
        if (rc == 0) {
            relaunch(argc, argv, NULL);
        } else {
            const char *lines[64];
            int n = sa_log_recent(lines, 64);
            ErrorScreen *e = error_screen_create(apk, rc, lines, n, LOG_PATH);
            int choice = ui_loop(frame_error, e);
            error_screen_destroy(e);
            if (choice == UI_BACK) relaunch(argc, argv, NULL);
            else if (choice == UI_RETRY) relaunch(argc, argv, apk);
        }
        free(apk);
    }

    LOGI("switchapk exiting");
    if (log) {
        sa_log_set_file(NULL);
        fclose(log);
    }
    platform_shutdown();
    socketExit();
    plExit();
    romfsExit();
    return 0;
}
