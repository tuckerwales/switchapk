/*
 * Host (Linux/macOS) driver: runs dex programs or APKs headlessly for testing.
 */
#include "../vm/vm.h"
#include "../core/zip.h"
#include "../native/natives.h"
#include "../platform/platform.h"
#include "apk_info.h"
#include "launcher.h"
#include "../gfx/gfx.h"
#include "../nativeloader/nativeloader.h"

#include <pthread.h>

#define LOG_TAG "main"

void platform_set_data_root(const char *root, const char *package);

typedef struct {
    int argc;
    char **argv;
    int rc;
} MainArgs;

static void usage(void) {
    fprintf(stderr,
            "usage: switchapk-host [options] <program.dex|app.apk> [MainClass] [args...]\n"
            "  --framework <file>   framework dex (default build/java/framework.dex)\n"
            "  --data <dir>         data directory (default ./build/data)\n"
            "  --script <file>      headless input script (APK runs only)\n"
            "  --screenshots <dir>  directory for script screenshots\n"
            "  --screen WxH@dpi     display size (default 1280x720@240)\n"
            "  --trace              trace every instruction\n"
            "  --raw-stdio          write System.out/err directly to stdout/stderr\n"
            "  --apk-info           print the APK label and icon at 240 dpi, then exit\n"
            "  --launcher           run the Switch home screen on the APK folder given as the program;\n"
            "                       prints launch=<path> or exit (state in <data>/launcher)\n"
            "  --splash             show the launch splash for the APK until the script ends\n"
            "  --nro-icon           write the 256x256 NRO icon as a binary PPM to the program path\n"
            "  --icon-source <img>  --nro-icon: scale this image (its central square) instead of drawing\n"
            "  --icon-crop <f>      --nro-icon: keep this fraction of the source's shorter side (default 1)\n"
            "  --error-screen       show the \"app stopped\" screen for the APK with the recent log lines\n"
            "  --shim-symbols       print the names the native shim provides, then exit\n"
            "  -v / -vv             verbose logging\n");
}

static DexFile *load_dex_file(const char *path) {
    size_t len;
    uint8_t *data = sa_read_file(path, &len);
    if (!data) {
        LOGE("cannot read %s", path);
        return NULL;
    }
    DexFile *d = dex_open(data, len, false, path);
    if (d) d->owned = true;
    return d;
}

/* ---- launcher screens (shared with the Switch build, see launcher.h) ---- */

static uint32_t ui_button(int keycode) {
    switch (keycode) {
    case 19: return UI_BTN_UP;      /* DPAD_UP */
    case 20: return UI_BTN_DOWN;    /* DPAD_DOWN */
    case 21: return UI_BTN_LEFT;    /* DPAD_LEFT */
    case 22: return UI_BTN_RIGHT;   /* DPAD_RIGHT */
    case 23: case 66: case 96: return UI_BTN_A; /* DPAD_CENTER, ENTER, BUTTON_A */
    case 4: case 97: return UI_BTN_B;           /* BACK, BUTTON_B */
    case 99: return UI_BTN_X;
    case 100: return UI_BTN_Y;
    case 102: return UI_BTN_L;
    case 103: return UI_BTN_R;
    case 104: return UI_BTN_ZL;
    case 105: return UI_BTN_ZR;
    case 108: return UI_BTN_PLUS;   /* BUTTON_START */
    case 109: return UI_BTN_MINUS;  /* BUTTON_SELECT */
    default: return 0;
    }
}

typedef int (*UiFrameFn)(void *ctx, uint32_t *screen, const UiInput *in, const UiStatus *st);

static int frame_launcher(void *ctx, uint32_t *screen, const UiInput *in, const UiStatus *st) {
    return launcher_frame(ctx, screen, in, st);
}

static int frame_error(void *ctx, uint32_t *screen, const UiInput *in, const UiStatus *st) {
    return error_screen_frame(ctx, screen, in, st);
}

static int frame_static(void *ctx, uint32_t *screen, const UiInput *in, const UiStatus *st) {
    bool *drawn = ctx;
    if (*drawn) return UI_IDLE;
    *drawn = true;
    return UI_REDRAW;
}

/* Feeds script input to a screen one event per frame and presents only the frames that changed, so the script's
 * idle command works. Returns the screen's result, or UI_EXIT when the script ends. */
static int host_ui_loop(UiFrameFn fn, void *ctx, uint32_t *screen) {
    uint32_t held = 0;
    bool touching = false;
    int tx = 0, ty = 0;
    for (;;) {
        PlatformEvent ev;
        uint32_t down = 0;
        if (platform_wait_event(&ev, 16)) {
            if (ev.kind == PEV_QUIT) return UI_EXIT;
            if (ev.kind == PEV_KEY) {
                uint32_t b = ui_button(ev.b);
                if (ev.a == 0) {
                    down |= b;
                    held |= b;
                } else {
                    held &= ~b;
                }
            } else if (ev.kind == PEV_TOUCH && ev.b == 0) {
                touching = ev.a != 1;
                tx = (int)ev.f[0];
                ty = (int)ev.f[1];
            }
            free(ev.text);
        }
        PlatformBattery bat;
        platform_battery_state(&bat);
        PlatformNetwork net;
        platform_network_state(&net);
        UiStatus st;
        memset(&st, 0, sizeof st);
        snprintf(st.clock, sizeof st.clock, "12:34"); /* fixed, so screenshots repeat */
        st.has_battery = true;
        st.battery = bat.level;
        st.charging = bat.charging;
        st.wifi = net.connected ? (net.signal >= 0 ? net.signal : 3) : -1;
        UiInput in = {down, held, touching, tx, ty, (int64_t)sa_time_ns()};
        int rc = fn(ctx, screen, &in, &st);
        if (rc == UI_REDRAW) platform_present(screen, UI_W, UI_H, UI_W);
        else if (rc != UI_IDLE) return rc;
    }
}

static int run_ui(const char *mode, const char *target, const char *data_dir) {
    gfx_font_init_default();
    uint32_t *screen = sa_calloc((size_t)UI_W * UI_H, sizeof *screen);
    int rc = 0;
    if (!strcmp(mode, "launcher")) {
        char *state = sa_sprintf("%s/launcher", data_dir);
        Launcher *l = launcher_create(target, state);
        int r = host_ui_loop(frame_launcher, l, screen);
        if (r == UI_RUN) printf("launch=%s\n", launcher_chosen(l));
        else printf("exit\n");
        launcher_destroy(l);
        free(state);
    } else if (!strcmp(mode, "splash")) {
        ui_draw_splash(screen, target);
        bool drawn = false;
        host_ui_loop(frame_static, &drawn, screen);
    } else {
        LOGE("demo: the app stopped");
        const char *lines[48];
        int n = sa_log_recent(lines, 48);
        ErrorScreen *e = error_screen_create(target, 1, lines, n, "build/data/log.txt");
        int r = host_ui_loop(frame_error, e, screen);
        printf("%s\n", r == UI_BACK ? "back" : r == UI_RETRY ? "retry" : "exit");
        error_screen_destroy(e);
    }
    free(screen);
    return rc;
}

static void *vm_main(void *arg) {
    MainArgs *ma = arg;
    volatile int stack_marker = 0;
    const char *framework = "build/java/framework.dex";
    const char *data_dir = "build/data";
    const char *script = NULL;
    const char *shots = NULL;
    bool apk_info = false;
    const char *ui_mode = NULL;
    const char *icon_source = NULL;
    float icon_crop = 1;
    int i = 1;
    for (; i < ma->argc && ma->argv[i][0] == '-'; i++) {
        const char *a = ma->argv[i];
        if (!strcmp(a, "--framework") && i + 1 < ma->argc) framework = ma->argv[++i];
        else if (!strcmp(a, "--data") && i + 1 < ma->argc) data_dir = ma->argv[++i];
        else if (!strcmp(a, "--script") && i + 1 < ma->argc) script = ma->argv[++i];
        else if (!strcmp(a, "--screenshots") && i + 1 < ma->argc) shots = ma->argv[++i];
        else if (!strcmp(a, "--screen") && i + 1 < ma->argc) i++; /* platform_init reads it */
        else if (!strcmp(a, "--apk-info")) apk_info = true;
        else if (!strcmp(a, "--launcher")) ui_mode = "launcher";
        else if (!strcmp(a, "--splash")) ui_mode = "splash";
        else if (!strcmp(a, "--error-screen")) ui_mode = "error";
        else if (!strcmp(a, "--nro-icon")) ui_mode = "icon";
        else if (!strcmp(a, "--icon-source") && i + 1 < ma->argc) icon_source = ma->argv[++i];
        else if (!strcmp(a, "--icon-crop") && i + 1 < ma->argc) icon_crop = (float)atof(ma->argv[++i]);
        else if (!strcmp(a, "--shim-symbols")) {
            /* For tools/corpus.py: imports missing here (except GL and EGL, from the driver) bind to logging stubs. */
            const ShimSym *(*tables[])(size_t *) = {shim_libc_symbols, shim_android_symbols, shim_runtime_symbols};
            for (size_t k = 0; k < SA_ARRAY_LEN(tables); k++) {
                size_t n;
                const ShimSym *syms = tables[k](&n);
                for (size_t j = 0; j < n; j++) printf("%s\n", syms[j].name);
            }
            ma->rc = 0;
            return NULL;
        }
        else if (!strcmp(a, "--trace")) g_vm.trace = true;
        else if (!strcmp(a, "--raw-stdio")) {
            extern bool g_raw_stdio;
            g_raw_stdio = true;
        }
        else if (!strcmp(a, "-v")) sa_log_level = SA_LOG_DEBUG;
        else if (!strcmp(a, "-vv")) sa_log_level = SA_LOG_VERBOSE;
        else {
            usage();
            ma->rc = 2;
            return NULL;
        }
    }
    if (i >= ma->argc) {
        usage();
        ma->rc = 2;
        return NULL;
    }
    const char *program = ma->argv[i++];
    if (ui_mode && !strcmp(ui_mode, "icon")) {
        gfx_font_init_default();
        enum { N = 256 };
        uint32_t *px = sa_calloc(N * N, sizeof *px);
        uint32_t *src = NULL;
        int sw = 0, sh = 0;
        if (icon_source) {
            size_t len = 0;
            uint8_t *bytes = sa_read_file(icon_source, &len);
            src = bytes ? gfx_decode_image(bytes, len, &sw, &sh, NULL) : NULL;
            free(bytes);
            if (!src) LOGW("cannot decode %s; drawing the icon instead", icon_source);
        }
        ui_draw_app_icon(px, N, src, sw, sh, icon_crop);
        free(src);
        FILE *f = fopen(program, "wb");
        if (f) {
            fprintf(f, "P6\n%d %d\n255\n", N, N);
            for (int k = 0; k < N * N; k++) {
                uint8_t rgb[3] = {(uint8_t)(px[k] >> 16), (uint8_t)(px[k] >> 8), (uint8_t)px[k]};
                fwrite(rgb, 1, 3, f);
            }
            fclose(f);
        }
        free(px);
        ma->rc = f ? 0 : 1;
        return NULL;
    }
    if (ui_mode) {
        if (script) platform_set_headless_script(script);
        if (shots) platform_set_screenshot_dir(shots);
        platform_init(ma->argc, ma->argv);
        ma->rc = run_ui(ui_mode, program, data_dir);
        return NULL;
    }
    if (apk_info) {
        ApkIdentity id;
        if (!apk_read_identity(program, APK_ICON_DENSITY, &id)) {
            fprintf(stderr, "cannot read %s\n", program);
            apk_identity_free(&id);
            ma->rc = 1;
            return NULL;
        }
        printf("label=%s\n", id.label ? id.label : "");
        printf("package=%s\n", id.package ? id.package : "");
        printf("version=%s\n", id.version ? id.version : "");
        if (id.icon && id.icon_w > 0 && id.icon_h > 0) {
            printf("icon=%dx%d\n", id.icon_w, id.icon_h);
            printf("px0=%08X\n", (unsigned)id.icon[0]);
            printf("px1=%08X\n", (unsigned)id.icon[id.icon_w - 1]);
        } else {
            printf("icon=none\n");
        }
        apk_identity_free(&id);
        ma->rc = 0;
        return NULL;
    }
    DexFile *fw = load_dex_file(framework);
    if (!fw) {
        ma->rc = 1;
        return NULL;
    }
    vm_add_boot_dex(fw);
    size_t plen = strlen(program);
    bool is_apk = plen > 4 && !strcmp(program + plen - 4, ".apk");
    if (is_apk) {
        extern int app_run_apk(const char *path, const char *data_dir, void *stack_hi);
        if (script) platform_set_headless_script(script);
        if (shots) platform_set_screenshot_dir(shots);
        /* Once, and before the app, so --screen applies and the script can race the first frame. */
        platform_init(ma->argc, ma->argv);
        ma->rc = app_run_apk(program, data_dir, (void *)&stack_marker);
        return NULL;
    }
    platform_set_data_root(data_dir, NULL);
    DexFile *app = load_dex_file(program);
    if (!app) {
        ma->rc = 1;
        return NULL;
    }
    vm_add_app_dex(app);
    VMThread *t = vm_attach_main_thread((void *)&stack_marker);
    if (!vm_boot(t)) {
        ma->rc = 1;
        return NULL;
    }
    const char *main_class = i < ma->argc ? ma->argv[i++] : "Main";
    Class *c = vm_class_from_name(t, main_class, true);
    if (!c) {
        if (t->exception) vm_print_exception(t, t->exception);
        ma->rc = 1;
        return NULL;
    }
    Method *m = vm_find_method(c, "main", "([Ljava/lang/String;)V");
    if (!m) {
        LOGE("%s has no main(String[])", main_class);
        ma->rc = 1;
        return NULL;
    }
    ArrayObject *jargs = vm_alloc_array(t, g_vm.wk.arr_String, ma->argc - i);
    for (int k = 0; i + k < ma->argc; k++) ARRAY_DATA(jargs, Object *)[k] = vm_new_string_utf8(t, ma->argv[i + k]);
    JValue a;
    a.l = (Object *)jargs;
    vm_callv(t, m, &a);
    if (t->exception) {
        if (g_vm.exiting) {
            ma->rc = g_vm.exit_code;
        } else {
            vm_print_exception(t, t->exception);
            ma->rc = 1;
        }
        t->exception = NULL;
    } else {
        ma->rc = 0;
    }
    /* flush System.out */
    vm_call_static(t, "Ljava/lang/System;", "exit", "(I)V", ma->rc);
    t->exception = NULL;
    return NULL;
}

int main(int argc, char **argv) {
    vm_init();
    MainArgs ma = {argc, argv, 0};
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, 16u * 1024 * 1024);
    pthread_t th;
    pthread_create(&th, &attr, vm_main, &ma);
    pthread_join(th, NULL);
    return ma.rc;
}
