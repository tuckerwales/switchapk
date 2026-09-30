/*
 * Host (Linux/macOS) driver: runs dex programs or APKs headlessly for testing.
 */
#include "../vm/vm.h"
#include "../core/zip.h"
#include "../native/natives.h"

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
            "  --trace              trace every instruction\n"
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

static void *vm_main(void *arg) {
    MainArgs *ma = arg;
    volatile int stack_marker = 0;
    const char *framework = "build/java/framework.dex";
    const char *data_dir = "build/data";
    int i = 1;
    for (; i < ma->argc && ma->argv[i][0] == '-'; i++) {
        const char *a = ma->argv[i];
        if (!strcmp(a, "--framework") && i + 1 < ma->argc) framework = ma->argv[++i];
        else if (!strcmp(a, "--data") && i + 1 < ma->argc) data_dir = ma->argv[++i];
        else if (!strcmp(a, "--trace")) g_vm.trace = true;
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
