/*
 * Loads one APK and enters ActivityThread.main. The zip stays open for the
 * process: assets and resources read it for the rest of the run.
 */
#include "../vm/vm.h"
#include "../android/android.h"
#include "../platform/platform.h"

#include <unistd.h>

#define LOG_TAG "app"

char *g_app_apk_path;

static bool readable(const char *path) { return path && access(path, R_OK) == 0; }

/* framework-res.apk: toolchain copy, then the java build, then the platform dir. */
static const char *framework_res_path(void) {
    static char buf[512];
    const char *toolchain = "build/toolchains/framework-res.apk";
    const char *built = "build/java/framework-res.apk";
    if (readable(toolchain)) return toolchain;
    if (readable(built)) return built;
    snprintf(buf, sizeof buf, "%s/framework-res.apk", platform_framework_path());
    if (readable(buf)) return buf;
    return toolchain;
}

static bool load_dexes(ZipArchive *zip) {
    for (int i = 1; i < 64; i++) {
        char name[32];
        if (i == 1) snprintf(name, sizeof name, "classes.dex");
        else snprintf(name, sizeof name, "classes%d.dex", i);
        size_t len = 0;
        uint8_t *data = zip_extract_name(zip, name, &len);
        if (!data) return i > 1;
        DexFile *d = dex_open(data, len, false, name);
        if (!d) {
            free(data);
            LOGE("cannot open %s", name);
            return false;
        }
        d->owned = true;
        vm_add_app_dex(d);
    }
    return true;
}

int app_run_apk(const char *path, const char *data_dir, void *stack_hi) {
    ZipArchive *zip = zip_open(path);
    if (!zip) {
        LOGE("cannot open %s", path);
        return 1;
    }
    g_app_zip = zip;
    free(g_app_apk_path);
    g_app_apk_path = sa_strdup(path);
    android_res_init(framework_res_path(), zip);
    if (!load_dexes(zip)) return 1;
    extern void platform_set_data_root(const char *root, const char *package);
    platform_set_data_root(data_dir, NULL);
    VMThread *t = vm_attach_main_thread(stack_hi);
    if (!vm_boot(t)) return 1;
    ArrayObject *args = vm_alloc_array(t, g_vm.wk.arr_String, 1);
    if (!args) return 1;
    Object *root = (Object *)args;
    vm_add_root(&root);
    ARRAY_DATA((ArrayObject *)root, Object *)[0] = vm_new_string_utf8(t, path);
    vm_call_static(t, "Landroid/app/ActivityThread;", "main", "([Ljava/lang/String;)V", root);
    vm_remove_root(&root);
    if (!t->exception) return 0;
    if (g_vm.exiting) {
        int code = g_vm.exit_code;
        t->exception = NULL;
        return code;
    }
    vm_print_exception(t, t->exception);
    t->exception = NULL;
    return 1;
}
