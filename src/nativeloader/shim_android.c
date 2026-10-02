/*
 * Android system library shim (WS9): liblog, libdl, libandroid's asset
 * manager, system properties, zlib, ANativeWindow, and the GL/EGL entry
 * points, plus the lookup that joins every shim table.
 *
 * Not yet: ALooper/AInputQueue, OpenSL ES and AAudio (WS7), AConfiguration,
 * ASensorManager. Imports of those bind to logging stubs (elf_loader.c).
 */
#include "nativeloader.h"
#include "ndk_android.h"
#include "../android/android_gl.h"
#include "../core/zip.h"

#include <stdarg.h>
#include <zlib.h>

#define LOG_TAG "shim"

/* ---- liblog ----------------------------------------------------------------------------------------------- */

static int clamp_prio(int prio) { return prio < SA_LOG_VERBOSE ? SA_LOG_VERBOSE : prio > SA_LOG_FATAL ? SA_LOG_FATAL : prio; }

static int sh_log_write(int prio, const char *tag, const char *text) {
    sa_log(clamp_prio(prio), tag ? tag : "native", "%s", text ? text : "");
    return 1;
}

static int sh_log_vprint(int prio, const char *tag, const char *fmt, va_list ap) {
    char buf[1024];
    vsnprintf(buf, sizeof buf, fmt ? fmt : "", ap);
    return sh_log_write(prio, tag, buf);
}

static int sh_log_print(int prio, const char *tag, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int r = sh_log_vprint(prio, tag, fmt, ap);
    va_end(ap);
    return r;
}

static int sh_log_buf_write(int buf, int prio, const char *tag, const char *text) {
    SA_UNUSED(buf);
    return sh_log_write(prio, tag, text);
}

static int sh_log_buf_print(int buf, int prio, const char *tag, const char *fmt, ...) {
    SA_UNUSED(buf);
    va_list ap;
    va_start(ap, fmt);
    int r = sh_log_vprint(prio, tag, fmt, ap);
    va_end(ap);
    return r;
}

static void sh_log_assert(const char *cond, const char *tag, const char *fmt, ...) {
    char buf[1024] = "";
    if (fmt) {
        va_list ap;
        va_start(ap, fmt);
        vsnprintf(buf, sizeof buf, fmt, ap);
        va_end(ap);
    }
    sa_log(SA_LOG_FATAL, tag ? tag : "native", "assertion failed: %s %s", cond ? cond : "", buf);
    abort();
}

static int sh_log_is_loggable(int prio, const char *tag, int def) {
    SA_UNUSED(tag);
    SA_UNUSED(def);
    return prio >= sa_log_level;
}

/* ---- libdl ------------------------------------------------------------------------------------------------- */

static _Thread_local char tl_dlerror[512];
static _Thread_local bool tl_dlerror_set;

static void *sh_dlopen(const char *name, int flags) {
    SA_UNUSED(flags);
    char err[512] = "";
    void *h = loader_dlopen(name, err, sizeof err);
    if (!h) {
        snprintf(tl_dlerror, sizeof tl_dlerror, "%s", err[0] ? err : "dlopen failed");
        tl_dlerror_set = true;
        LOGW("%s", tl_dlerror);
    }
    return h;
}

static void *sh_android_dlopen_ext(const char *name, int flags, const void *info) {
    SA_UNUSED(info);
    return sh_dlopen(name, flags);
}

static void *sh_dlsym(void *handle, const char *name) {
    void *p = loader_dlsym(handle, name);
    if (!p) {
        snprintf(tl_dlerror, sizeof tl_dlerror, "undefined symbol: %s", name ? name : "(null)");
        tl_dlerror_set = true;
    }
    return p;
}

static int sh_dlclose(void *handle) { return loader_dlclose(handle); }

static char *sh_dlerror(void) {
    if (!tl_dlerror_set) return NULL;
    tl_dlerror_set = false;
    return tl_dlerror;
}

typedef struct {
    const char *dli_fname;
    void *dli_fbase;
    const char *dli_sname;
    void *dli_saddr;
} ShDlInfo;

static int sh_dladdr(const void *addr, ShDlInfo *info) {
    return loader_dladdr(addr, &info->dli_fname, &info->dli_fbase, &info->dli_sname, &info->dli_saddr) ? 1 : 0;
}

/* ---- system properties ------------------------------------------------------------------------------------- */

static int sh_system_property_get(const char *name, char *value) {
    static const char *const props[][2] = {
        {"ro.build.version.sdk", "29"},       {"ro.build.version.release", "10"},
        {"ro.product.model", "switchapk"},    {"ro.product.manufacturer", "switchapk"},
        {"ro.product.brand", "switchapk"},    {"ro.product.device", "switchapk"},
        {"ro.product.cpu.abi", SA_NATIVE_ABI}, {"ro.hardware", "switchapk"},
        {"ro.kernel.qemu", "0"},              {"ro.debuggable", "0"},
    };
    for (size_t i = 0; i < SA_ARRAY_LEN(props); i++) {
        if (strcmp(name, props[i][0]) == 0) {
            snprintf(value, 92 /* PROP_VALUE_MAX */, "%s", props[i][1]);
            return (int)strlen(value);
        }
    }
    value[0] = 0;
    return 0;
}

static const void *sh_system_property_find(const char *name) {
    SA_UNUSED(name);
    return NULL;
}

static int sh_android_get_device_api_level(void) { return 29; }

/* ---- libandroid: AAssetManager over the APK's assets/ ---------------------------------------------------- */

extern ZipArchive *g_app_zip;

typedef struct {
    uint8_t *data;
    size_t len, pos;
} ShAsset;

typedef struct {
    char *dir; /* "assets/<dir>/" */
    size_t next;
    char name[256];
} ShAssetDir;

static int g_asset_manager; /* AAssetManager* is opaque: one per process */

static void *sh_AAssetManager_fromJava(void *env, void *asset_manager) {
    SA_UNUSED(env);
    SA_UNUSED(asset_manager);
    return &g_asset_manager;
}

static ShAsset *sh_AAssetManager_open(void *mgr, const char *filename, int mode) {
    SA_UNUSED(mgr);
    SA_UNUSED(mode);
    if (!g_app_zip || !filename) return NULL;
    char path[512];
    snprintf(path, sizeof path, "assets/%s", filename);
    size_t len = 0;
    uint8_t *data = zip_extract_name(g_app_zip, path, &len);
    if (!data) return NULL;
    ShAsset *a = sa_calloc(1, sizeof *a);
    a->data = data;
    a->len = len;
    return a;
}

static int sh_AAsset_read(ShAsset *a, void *buf, size_t count) {
    size_t left = a->len - a->pos;
    if (count > left) count = left;
    memcpy(buf, a->data + a->pos, count);
    a->pos += count;
    return (int)count;
}

static int64_t sh_AAsset_seek64(ShAsset *a, int64_t offset, int whence) {
    int64_t base = whence == 0 ? 0 : whence == 1 ? (int64_t)a->pos : (int64_t)a->len;
    int64_t p = base + offset;
    if (p < 0 || p > (int64_t)a->len) return -1;
    a->pos = (size_t)p;
    return p;
}

static long sh_AAsset_seek(ShAsset *a, long offset, int whence) { return (long)sh_AAsset_seek64(a, offset, whence); }
static void sh_AAsset_close(ShAsset *a) {
    if (!a) return;
    free(a->data);
    free(a);
}
static const void *sh_AAsset_getBuffer(ShAsset *a) { return a->data; }
static long sh_AAsset_getLength(ShAsset *a) { return (long)a->len; }
static int64_t sh_AAsset_getLength64(ShAsset *a) { return (int64_t)a->len; }
static long sh_AAsset_getRemainingLength(ShAsset *a) { return (long)(a->len - a->pos); }
static int64_t sh_AAsset_getRemainingLength64(ShAsset *a) { return (int64_t)(a->len - a->pos); }
static int sh_AAsset_isAllocated(ShAsset *a) {
    SA_UNUSED(a);
    return 1;
}
static int sh_AAsset_openFileDescriptor(ShAsset *a, long *start, long *len) {
    /* assets are not files on disk here; callers fall back to AAsset_read */
    SA_UNUSED(a);
    SA_UNUSED(start);
    SA_UNUSED(len);
    return -1;
}

static ShAssetDir *sh_AAssetManager_openDir(void *mgr, const char *dir) {
    SA_UNUSED(mgr);
    ShAssetDir *d = sa_calloc(1, sizeof *d);
    size_t n = strlen(dir ? dir : "");
    d->dir = sa_malloc(n + 9);
    snprintf(d->dir, n + 9, "assets/%s%s", dir ? dir : "", n && dir[n - 1] != '/' ? "/" : "");
    return d;
}

/* Next file directly in the directory (Android lists files only, not subdirectories). */
static const char *sh_AAssetDir_getNextFileName(ShAssetDir *d) {
    if (!g_app_zip) return NULL;
    size_t plen = strlen(d->dir);
    for (; d->next < zip_count(g_app_zip); d->next++) {
        const ZipEntry *e = zip_entry_at(g_app_zip, d->next);
        const char *name = e->name;
        if (strncmp(name, d->dir, plen) != 0) continue;
        const char *rest = name + plen;
        if (!*rest || strchr(rest, '/')) continue;
        snprintf(d->name, sizeof d->name, "%s", rest);
        d->next++;
        return d->name;
    }
    return NULL;
}

static void sh_AAssetDir_rewind(ShAssetDir *d) { d->next = 0; }
static void sh_AAssetDir_close(ShAssetDir *d) {
    if (!d) return;
    free(d->dir);
    free(d);
}

/* ---- tables ------------------------------------------------------------------------------------------------ */

#define S(name) {#name, (void *)name}
#define W(name, fn) {#name, (void *)fn}

static const ShimSym g_syms[] = {
    /* liblog */
    W(__android_log_write, sh_log_write), W(__android_log_print, sh_log_print),
    W(__android_log_vprint, sh_log_vprint), W(__android_log_buf_write, sh_log_buf_write),
    W(__android_log_buf_print, sh_log_buf_print), W(__android_log_assert, sh_log_assert),
    W(__android_log_is_loggable, sh_log_is_loggable),
    /* libdl */
    W(dlopen, sh_dlopen), W(android_dlopen_ext, sh_android_dlopen_ext), W(dlsym, sh_dlsym),
    W(dlclose, sh_dlclose), W(dlerror, sh_dlerror), W(dladdr, sh_dladdr),
    /* properties */
    W(__system_property_get, sh_system_property_get), W(__system_property_find, sh_system_property_find),
    W(android_get_device_api_level, sh_android_get_device_api_level),
    /* libandroid assets */
    W(AAssetManager_fromJava, sh_AAssetManager_fromJava), W(AAssetManager_open, sh_AAssetManager_open),
    W(AAssetManager_openDir, sh_AAssetManager_openDir), W(AAsset_read, sh_AAsset_read),
    W(AAsset_seek, sh_AAsset_seek), W(AAsset_seek64, sh_AAsset_seek64), W(AAsset_close, sh_AAsset_close),
    W(AAsset_getBuffer, sh_AAsset_getBuffer), W(AAsset_getLength, sh_AAsset_getLength),
    W(AAsset_getLength64, sh_AAsset_getLength64), W(AAsset_getRemainingLength, sh_AAsset_getRemainingLength),
    W(AAsset_getRemainingLength64, sh_AAsset_getRemainingLength64), W(AAsset_isAllocated, sh_AAsset_isAllocated),
    W(AAsset_openFileDescriptor, sh_AAsset_openFileDescriptor),
    W(AAsset_openFileDescriptor64, sh_AAsset_openFileDescriptor),
    W(AAssetDir_getNextFileName, sh_AAssetDir_getNextFileName), W(AAssetDir_rewind, sh_AAssetDir_rewind),
    W(AAssetDir_close, sh_AAssetDir_close),
    /* libnativewindow / libandroid */
    W(ANativeWindow_acquire, ANativeWindow_acquire), W(ANativeWindow_release, ANativeWindow_release),
    W(ANativeWindow_getWidth, ANativeWindow_getWidth), W(ANativeWindow_getHeight, ANativeWindow_getHeight),
    W(ANativeWindow_getFormat, ANativeWindow_getFormat),
    W(ANativeWindow_setBuffersGeometry, ANativeWindow_setBuffersGeometry),
    W(ANativeWindow_lock, ANativeWindow_lock), W(ANativeWindow_unlockAndPost, ANativeWindow_unlockAndPost),
    W(ANativeWindow_fromSurface, ANativeWindow_fromSurface),
    W(ANativeActivity_finish, ANativeActivity_finish),
    W(ANativeActivity_setWindowFormat, ANativeActivity_setWindowFormat),
    W(ANativeActivity_setWindowFlags, ANativeActivity_setWindowFlags),
    W(ANativeActivity_showSoftInput, ANativeActivity_showSoftInput),
    W(ANativeActivity_hideSoftInput, ANativeActivity_hideSoftInput),
    /* libz */
    S(zlibVersion), S(inflateInit_), S(inflateInit2_), S(inflate), S(inflateEnd), S(inflateReset),
    S(deflateInit_), S(deflateInit2_), S(deflate), S(deflateEnd), S(deflateReset), S(deflateBound), S(crc32),
    S(adler32), S(compress), S(compress2), S(compressBound), S(uncompress),
};

const ShimSym *shim_android_symbols(size_t *n) {
    *n = SA_ARRAY_LEN(g_syms);
    return g_syms;
}

/* System libraries native code may name in DT_NEEDED or dlopen. */
bool shim_is_library(const char *soname) {
    static const char *const libs[] = {
        "libc.so",         "libm.so",           "libdl.so",      "liblog.so",          "libandroid.so",
        "libEGL.so",       "libGLESv1_CM.so",   "libGLESv2.so",  "libGLESv3.so",       "libOpenSLES.so",
        "libz.so",         "libjnigraphics.so", "libstdc++.so",  "libaaudio.so",       "libmediandk.so",
        "libnativewindow.so", "libsync.so",     "libamidi.so",   "libandroid_runtime.so",
    };
    for (size_t i = 0; i < SA_ARRAY_LEN(libs); i++)
        if (strcmp(soname, libs[i]) == 0) return true;
    return false;
}

static SaMap g_shim;
static pthread_once_t g_shim_once = PTHREAD_ONCE_INIT;

static void build_shim(void) {
    size_t n;
    const ShimSym *t = shim_libc_symbols(&n);
    for (size_t i = 0; i < n; i++) sa_map_put(&g_shim, t[i].name, t[i].addr);
    t = shim_android_symbols(&n);
    for (size_t i = 0; i < n; i++) sa_map_put(&g_shim, t[i].name, t[i].addr);
}

void *shim_lookup(const char *name) {
    pthread_once(&g_shim_once, build_shim);
    void *p = sa_map_get(&g_shim, name);
    if (p) return p;
    /* GL ES and EGL. Window surfaces are ours; everything else is the driver. */
    if ((name[0] == 'g' && name[1] == 'l') || (name[0] == 'e' && name[1] == 'g' && name[2] == 'l')) {
        if (!sa_gl_load()) return NULL;
        void *wrap = sa_egl_native_proc(name);
        if (wrap) return wrap;
        return sa_gl_proc(name);
    }
    return NULL;
}
