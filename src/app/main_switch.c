/*
 * Nintendo Switch entry point (switchapk.nro).
 *
 * Without arguments it shows a launcher listing the .apk files in sdmc:/switch/switchapk/apks;
 * with an APK path as argv[1] (nxlink, forwarders) it runs that APK directly.
 * The chosen APK runs on a VM thread with a large stack while this thread
 * pumps the applet loop, input and the software keyboard. When the app ends
 * the NRO reloads itself (through hbloader) so the user returns to the list.
 * If the app fails, an error screen shows the last log lines; the full log is
 * in sdmc:/switch/switchapk/log.txt.
 */
#include "../vm/vm.h"
#include "../gfx/gfx.h"
#include "../platform/platform.h"

#include <switch.h>
#include <dirent.h>
#include <pthread.h>
#include <sys/stat.h>

#define LOG_TAG "main"

#define ROOT_DIR "sdmc:/switch/switchapk"
#define APK_DIR ROOT_DIR "/apks"
#define DATA_DIR ROOT_DIR "/data"
#define LOG_PATH ROOT_DIR "/log.txt"

#define SCREEN_W 1280
#define SCREEN_H 720

bool platform_switch_pump(void);
u64 platform_switch_buttons_down(void);
void platform_set_data_root(const char *root, const char *package);
int app_run_apk(const char *path, const char *data_dir, void *stack_hi);

/* ---- tiny immediate-mode drawing on a 1280x720 ARGB buffer ------------------------ */

static uint32_t *g_screen;

static GfxTarget screen_target(void) {
    GfxTarget t = {g_screen, SCREEN_W, SCREEN_H, SCREEN_W};
    return t;
}

static GfxClip screen_clip(void) {
    GfxClip c = {0, 0, SCREEN_W, SCREEN_H, NULL, 0};
    return c;
}

static void fill_rect(int l, int t, int r, int b, uint32_t color) {
    GfxTarget tg = screen_target();
    GfxClip clip = screen_clip();
    GfxMatrix m;
    gfx_matrix_identity(&m);
    GfxPaint p;
    memset(&p, 0, sizeof p);
    p.color = color;
    p.style = GFX_FILL;
    p.aa = true;
    p.xfer = GFX_XFER_SRC_OVER;
    gfx_draw_rect(&tg, &m, &clip, (float)l, (float)t, (float)r, (float)b, &p);
}

static void draw_text(const char *utf8, float x, float baseline, float size, uint32_t color, bool bold) {
    GfxFont *f = gfx_font_default(bold);
    if (!f || !utf8) return;
    size_t n = strlen(utf8);
    uint16_t *u = sa_malloc((n + 1) * sizeof *u);
    size_t len = 0;
    /* ASCII / Latin-1 is enough for file names and log lines; other bytes show as '?'. */
    for (size_t i = 0; i < n; i++) {
        unsigned char ch = (unsigned char)utf8[i];
        u[len++] = ch < 0x80 ? ch : '?';
        if (ch >= 0xC0) {
            while (i + 1 < n && ((unsigned char)utf8[i + 1] & 0xC0) == 0x80) i++;
        }
    }
    GfxTarget tg = screen_target();
    GfxClip clip = screen_clip();
    GfxMatrix m;
    gfx_matrix_identity(&m);
    GfxPaint p;
    memset(&p, 0, sizeof p);
    p.color = color;
    p.style = GFX_FILL;
    p.aa = true;
    p.xfer = GFX_XFER_SRC_OVER;
    gfx_draw_text(&tg, &m, &clip, f, size, u, (int)len, x, baseline, 0, false, &p);
    free(u);
}

static void present_screen(void) { platform_present(g_screen, SCREEN_W, SCREEN_H, SCREEN_W); }

/* ---- launcher ------------------------------------------------------------------ */

typedef struct {
    char **names;
    int count;
} ApkList;

static int cmp_names(const void *a, const void *b) { return strcasecmp(*(char *const *)a, *(char *const *)b); }

static ApkList list_apks(void) {
    ApkList l = {NULL, 0};
    DIR *d = opendir(APK_DIR);
    if (!d) return l;
    struct dirent *e;
    while ((e = readdir(d)) != NULL) {
        size_t n = strlen(e->d_name);
        if (n > 4 && !strcasecmp(e->d_name + n - 4, ".apk")) {
            l.names = sa_realloc(l.names, (size_t)(l.count + 1) * sizeof *l.names);
            l.names[l.count++] = sa_strdup(e->d_name);
        }
    }
    closedir(d);
    if (l.count > 1) qsort(l.names, (size_t)l.count, sizeof *l.names, cmp_names);
    return l;
}

#define ROW_H 64
#define LIST_TOP 120
#define VISIBLE_ROWS 8

static void draw_launcher(const ApkList *l, int sel, int top) {
    fill_rect(0, 0, SCREEN_W, SCREEN_H, 0xFF202124);
    fill_rect(0, 0, SCREEN_W, 96, 0xFF00796B);
    draw_text("switchapk", 48, 62, 36, 0xFFFFFFFF, true);
    draw_text("A: run   +: exit   Up/Down or touch: choose", 640, 60, 22, 0xFFE0F2F1, false);
    if (l->count == 0) {
        draw_text("No APKs found.", 48, 200, 30, 0xFFFFFFFF, true);
        draw_text("Copy .apk files to sdmc:/switch/switchapk/apks/ and start switchapk again.", 48, 250, 24,
                  0xFFBDBDBD, false);
        draw_text("Apps written in Java run; apps that need native (.so) libraries do not yet.", 48, 290, 24,
                  0xFFBDBDBD, false);
        return;
    }
    for (int row = 0; row < VISIBLE_ROWS && top + row < l->count; row++) {
        const int i = top + row;
        const int y = LIST_TOP + row * ROW_H;
        if (i == sel) fill_rect(32, y, SCREEN_W - 32, y + ROW_H - 8, 0xFF37474F);
        char label[256];
        snprintf(label, sizeof label, "%s", l->names[i]);
        size_t n = strlen(label);
        if (n > 4) label[n - 4] = 0; /* drop .apk */
        draw_text(label, 56, (float)(y + 38), 28, 0xFFFFFFFF, i == sel);
    }
    char footer[64];
    snprintf(footer, sizeof footer, "%d of %d", sel + 1, l->count);
    draw_text(footer, 48, SCREEN_H - 28, 22, 0xFF9E9E9E, false);
}

/* Returns the chosen APK's full path, or NULL to exit. */
static char *run_launcher(void) {
    ApkList l = list_apks();
    int sel = 0, top = 0;
    bool was_touching = false;
    int touch_row = -1;
    char *chosen = NULL;
    while (appletMainLoop()) {
        u64 down = platform_switch_buttons_down();
        if (down & HidNpadButton_Plus) break;
        if (l.count > 0) {
            if (down & (HidNpadButton_Down | HidNpadButton_StickLDown)) sel = (sel + 1) % l.count;
            if (down & (HidNpadButton_Up | HidNpadButton_StickLUp)) sel = (sel + l.count - 1) % l.count;
            HidTouchScreenState ts = {0};
            bool touching = hidGetTouchScreenStates(&ts, 1) && ts.count > 0;
            if (touching && !was_touching) {
                int row = ((int)ts.touches[0].y - LIST_TOP) / ROW_H;
                touch_row = (ts.touches[0].y >= LIST_TOP && row >= 0 && row < VISIBLE_ROWS && top + row < l.count)
                        ? top + row : -1;
                if (touch_row >= 0) sel = touch_row;
            }
            bool tapped = was_touching && !touching && touch_row == sel && touch_row >= 0;
            was_touching = touching;
            if (sel < top) top = sel;
            if (sel >= top + VISIBLE_ROWS) top = sel - VISIBLE_ROWS + 1;
            if ((down & HidNpadButton_A) || tapped) {
                chosen = sa_sprintf("%s/%s", APK_DIR, l.names[sel]);
                break;
            }
        }
        draw_launcher(&l, sel, top);
        present_screen();
    }
    for (int i = 0; i < l.count; i++) free(l.names[i]);
    free(l.names);
    return chosen;
}

/* ---- error screen ----------------------------------------------------------------- */

/* Returns true to go back to the launcher, false to exit. */
static bool show_error(const char *apk, int rc) {
    const char *lines[48];
    int n = sa_log_recent(lines, 48);
    while (appletMainLoop()) {
        u64 down = platform_switch_buttons_down();
        if (down & HidNpadButton_A) return true;
        if (down & HidNpadButton_Plus) return false;
        fill_rect(0, 0, SCREEN_W, SCREEN_H, 0xFF202124);
        fill_rect(0, 0, SCREEN_W, 96, 0xFFB71C1C);
        char title[300];
        const char *base = strrchr(apk, '/');
        snprintf(title, sizeof title, "%s stopped (exit code %d)", base ? base + 1 : apk, rc);
        draw_text(title, 48, 62, 32, 0xFFFFFFFF, true);
        /* the newest lines that fit, oldest at the top */
        const int max_rows = 24;
        int first = n > max_rows ? n - max_rows : 0;
        for (int i = first; i < n; i++) {
            const char *s = lines[i];
            uint32_t color = (s[0] == 'E' || s[0] == 'F') ? 0xFFFF8A80 : (s[0] == 'W' ? 0xFFFFE082 : 0xFFBDBDBD);
            draw_text(s, 24, (float)(124 + (i - first) * 22), 17, color, false);
        }
        draw_text("A: back to the list   +: exit   Full log: sdmc:/switch/switchapk/log.txt", 24, SCREEN_H - 20, 20,
                  0xFFFFFFFF, false);
        present_screen();
    }
    return false;
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
    while (!ra.done) {
        platform_switch_pump();
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
    g_screen = sa_calloc((size_t)SCREEN_W * SCREEN_H, sizeof *g_screen);
    gfx_font_init_default();

    char *apk = NULL;
    if (argc > 1) {
        size_t n = strlen(argv[1]);
        if (n > 4 && !strcasecmp(argv[1] + n - 4, ".apk")) apk = sa_strdup(argv[1]);
    }
    if (!apk) apk = run_launcher();

    bool relaunch = false;
    if (apk) {
        int rc = run_apk(apk);
        if (rc != 0) relaunch = show_error(apk, rc);
        else relaunch = true;
        free(apk);
    }
    /* Back to the launcher: hbloader starts this NRO again once we exit. */
    if (relaunch && argc > 0 && argv[0] && envHasNextLoad()) envSetNextLoad(argv[0], argv[0]);

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
