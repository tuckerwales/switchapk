/*
 * android.app.NativeActivity natives (WS9). The Java class loads the
 * library and calls ANativeActivity_onCreate; this file fills the
 * ANativeActivity the NDK header describes and forwards lifecycle and
 * surface callbacks. The window is an ANativeWindow over the activity's
 * SurfaceView (native_window.c). Input queues are not delivered.
 */
#include "native_window.h"
#include "nativeloader.h"
#include "../android/android.h"

#include <stddef.h>

#define LOG_TAG "nativeactivity"

_Static_assert(sizeof(void *) == 8, "ANativeActivity layout is for 64-bit ABIs");
_Static_assert(offsetof(ANativeActivity, sdkVersion) == 48, "ANativeActivity.sdkVersion");
_Static_assert(offsetof(ANativeActivity, instance) == 56, "ANativeActivity.instance");
_Static_assert(sizeof(ANativeActivity) == 80, "ANativeActivity size");

extern void *nativeloader_find_symbol(const char *name);
extern const char *nativeloader_load_library(VMThread *t, const char *name, bool is_libname);

typedef void (*NaOnCreate)(ANativeActivity *activity, void *saved_state, size_t saved_state_size);

typedef struct NaHandle {
    ANativeActivity activity;
    ANativeActivityCallbacks callbacks;
    Object *java_activity; /* vm_add_root; activity.clazz points here */
    ANativeWindow *window;
    char *internal_path;
    char *external_path;
    char *obb_path;
    int last_w, last_h;
} NaHandle;

static char g_dlerror[512];

static void set_err(const char *msg) {
    snprintf(g_dlerror, sizeof g_dlerror, "%s", msg ? msg : "");
}

static NaHandle *na_of(uint64_t *args) { return (NaHandle *)(uintptr_t)A_LONG(1); }

static void na_free(NaHandle *h) {
    if (!h) return;
    if (h->window) {
        anw_release(h->window);
        h->window = NULL;
    }
    if (h->java_activity) vm_remove_root(&h->java_activity);
    free(h->internal_path);
    free(h->external_path);
    free(h->obb_path);
    free(h);
}

static void clear_exc(VMThread *t) {
    if (!t->exception) return;
    vm_print_exception(t, t->exception);
    t->exception = NULL;
}

/* A long argument occupies two Dalvik slots. Surface and the ints follow the pad. */
#define NA_SURF 3
#define NA_INT0 3

static VMThread *na_thread(void) {
    VMThread *t = vm_current_thread();
    if (!t) LOGE("ANativeActivity call requires the VM thread");
    return t;
}

void ANativeActivity_finish(ANativeActivity *activity) {
    VMThread *t = na_thread();
    if (!t || !activity || !activity->clazz) return;
    bool had = t->has_gil;
    if (!had) vm_gil_acquire(t);
    vm_call_virtual(t, (Object *)activity->clazz, "finish", "()V");
    clear_exc(t);
    if (!had) vm_gil_release(t);
}

void ANativeActivity_setWindowFormat(ANativeActivity *activity, int32_t format) {
    VMThread *t = na_thread();
    if (!t || !activity || !activity->clazz) return;
    bool had = t->has_gil;
    if (!had) vm_gil_acquire(t);
    vm_call_virtual(t, (Object *)activity->clazz, "setWindowFormat", "(I)V", format);
    clear_exc(t);
    if (!had) vm_gil_release(t);
}

void ANativeActivity_setWindowFlags(ANativeActivity *activity, uint32_t add_flags, uint32_t remove_flags) {
    VMThread *t = na_thread();
    if (!t || !activity || !activity->clazz) return;
    bool had = t->has_gil;
    if (!had) vm_gil_acquire(t);
    int32_t mask = (int32_t)(add_flags | remove_flags);
    vm_call_virtual(t, (Object *)activity->clazz, "setWindowFlags", "(II)V", (int32_t)add_flags, mask);
    clear_exc(t);
    if (!had) vm_gil_release(t);
}

void ANativeActivity_showSoftInput(ANativeActivity *activity, uint32_t flags) {
    VMThread *t = na_thread();
    if (!t || !activity || !activity->clazz) return;
    bool had = t->has_gil;
    if (!had) vm_gil_acquire(t);
    vm_call_virtual(t, (Object *)activity->clazz, "showIme", "(I)V", (int32_t)flags);
    clear_exc(t);
    if (!had) vm_gil_release(t);
}

void ANativeActivity_hideSoftInput(ANativeActivity *activity, uint32_t flags) {
    VMThread *t = na_thread();
    if (!t || !activity || !activity->clazz) return;
    bool had = t->has_gil;
    if (!had) vm_gil_acquire(t);
    vm_call_virtual(t, (Object *)activity->clazz, "hideIme", "(I)V", (int32_t)flags);
    clear_exc(t);
    if (!had) vm_gil_release(t);
}

/* loadNativeCode(path, func, queue, internal, obb, external, sdk, assets, saved, loader, libPath) */
NATIVE(NativeActivity_loadNativeCode) {
    char *path = nat_str(A_OBJ(1));
    char *func = nat_str(A_OBJ(2));
    if (!path || !func) {
        set_err("dlopen failed: missing library path or entry point");
        free(path);
        free(func);
        R_LONG(0);
        return;
    }
    const char *err = nativeloader_load_library(t, path, false);
    if (t->exception) {
        free(path);
        free(func);
        return;
    }
    if (err) {
        set_err(err);
        free(path);
        free(func);
        R_LONG(0);
        return;
    }
    NaOnCreate entry = (NaOnCreate)nativeloader_find_symbol(func);
    if (!entry) {
        snprintf(g_dlerror, sizeof g_dlerror, "undefined symbol: %s", func);
        free(path);
        free(func);
        R_LONG(0);
        return;
    }
    free(func);

    NaHandle *h = sa_calloc(1, sizeof *h);
    h->activity.callbacks = &h->callbacks;
    h->activity.vm = vm_java_vm();
    h->activity.env = vm_jni_env(t);
    h->java_activity = A_OBJ(0);
    vm_add_root(&h->java_activity);
    h->activity.clazz = h->java_activity;
    h->internal_path = nat_str(A_OBJ(4));
    h->obb_path = nat_str(A_OBJ(5));
    h->external_path = nat_str(A_OBJ(6));
    h->activity.internalDataPath = h->internal_path;
    h->activity.obbPath = h->obb_path;
    h->activity.externalDataPath = h->external_path;
    h->activity.sdkVersion = A_INT(7);
    typedef void *(*FromJavaFn)(void *env, void *asset_manager);
    FromJavaFn from_java = (FromJavaFn)shim_lookup("AAssetManager_fromJava");
    if (from_java) h->activity.assetManager = from_java(h->activity.env, A_OBJ(8));

    ArrayObject *saved = A_ARR(9);
    void *saved_ptr = NULL;
    size_t saved_len = 0;
    if (saved && saved->length > 0) {
        saved_ptr = ARRAY_DATA(saved, uint8_t);
        saved_len = (size_t)saved->length;
    }
    entry(&h->activity, saved_ptr, saved_len);
    free(path);
    if (t->exception) {
        if (h->callbacks.onDestroy) h->callbacks.onDestroy(&h->activity);
        na_free(h);
        return;
    }
    g_dlerror[0] = 0;
    R_LONG((int64_t)(uintptr_t)h);
}

NATIVE(NativeActivity_getDlError) {
    UNUSED_ARGS();
    R_OBJ(g_dlerror[0] ? vm_new_string_utf8(t, g_dlerror) : NULL);
}

NATIVE(NativeActivity_unloadNativeCode) {
    NaHandle *h = na_of(args);
    if (!h) return;
    if (h->callbacks.onDestroy) h->callbacks.onDestroy(&h->activity);
    na_free(h);
}

NATIVE(NativeActivity_onStart) {
    NaHandle *h = na_of(args);
    if (h && h->callbacks.onStart) h->callbacks.onStart(&h->activity);
}

NATIVE(NativeActivity_onResume) {
    NaHandle *h = na_of(args);
    if (h && h->callbacks.onResume) h->callbacks.onResume(&h->activity);
}

NATIVE(NativeActivity_onSaveInstanceState) {
    NaHandle *h = na_of(args);
    if (!h || !h->callbacks.onSaveInstanceState) {
        R_OBJ(NULL);
        return;
    }
    size_t n = 0;
    void *p = h->callbacks.onSaveInstanceState(&h->activity, &n);
    if (!p || n == 0) {
        free(p);
        R_OBJ(NULL);
        return;
    }
    ArrayObject *a = vm_alloc_prim_array(t, 'B', (int32_t)n);
    if (a) memcpy(ARRAY_DATA(a, uint8_t), p, n);
    free(p);
    R_OBJ(a);
}

NATIVE(NativeActivity_onPause) {
    NaHandle *h = na_of(args);
    if (h && h->callbacks.onPause) h->callbacks.onPause(&h->activity);
}

NATIVE(NativeActivity_onStop) {
    NaHandle *h = na_of(args);
    if (h && h->callbacks.onStop) h->callbacks.onStop(&h->activity);
}

NATIVE(NativeActivity_onConfigurationChanged) {
    NaHandle *h = na_of(args);
    if (h && h->callbacks.onConfigurationChanged) h->callbacks.onConfigurationChanged(&h->activity);
}

NATIVE(NativeActivity_onLowMemory) {
    NaHandle *h = na_of(args);
    if (h && h->callbacks.onLowMemory) h->callbacks.onLowMemory(&h->activity);
}

NATIVE(NativeActivity_onWindowFocusChanged) {
    NaHandle *h = na_of(args);
    if (h && h->callbacks.onWindowFocusChanged)
        h->callbacks.onWindowFocusChanged(&h->activity, A_BOOL(NA_INT0) ? 1 : 0);
}

NATIVE(NativeActivity_onSurfaceCreated) {
    NaHandle *h = na_of(args);
    if (!h) return;
    Object *surface = A_OBJ(NA_SURF);
    ANativeWindow *w = anw_create(t, surface);
    if (!w) {
        LOGW("ANativeWindow_fromSurface failed");
        return;
    }
    if (h->window) anw_release(h->window);
    h->window = w;
    h->last_w = anw_width(w);
    h->last_h = anw_height(w);
    if (h->callbacks.onNativeWindowCreated) h->callbacks.onNativeWindowCreated(&h->activity, w);
}

NATIVE(NativeActivity_onSurfaceChanged) {
    NaHandle *h = na_of(args);
    if (!h || !h->window) return;
    int width = A_INT(5);
    int height = A_INT(6);
    anw_note_size(h->window, width, height);
    bool resized = width != h->last_w || height != h->last_h;
    h->last_w = width;
    h->last_h = height;
    if (resized && h->callbacks.onNativeWindowResized)
        h->callbacks.onNativeWindowResized(&h->activity, h->window);
}

NATIVE(NativeActivity_onSurfaceRedrawNeeded) {
    NaHandle *h = na_of(args);
    if (h && h->window && h->callbacks.onNativeWindowRedrawNeeded)
        h->callbacks.onNativeWindowRedrawNeeded(&h->activity, h->window);
}

NATIVE(NativeActivity_onSurfaceDestroyed) {
    NaHandle *h = na_of(args);
    if (!h || !h->window) return;
    if (h->callbacks.onNativeWindowDestroyed) h->callbacks.onNativeWindowDestroyed(&h->activity, h->window);
    anw_release(h->window);
    h->window = NULL;
}

NATIVE(NativeActivity_onContentRectChanged) {
    NaHandle *h = na_of(args);
    if (!h || !h->callbacks.onContentRectChanged) return;
    ARect rect;
    rect.left = A_INT(NA_INT0);
    rect.top = A_INT(4);
    rect.right = rect.left + A_INT(5);
    rect.bottom = rect.top + A_INT(6);
    h->callbacks.onContentRectChanged(&h->activity, &rect);
}

NATIVE(NativeActivity_onInputQueueCreated) { UNUSED_ARGS(); }
NATIVE(NativeActivity_onInputQueueDestroyed) { UNUSED_ARGS(); }

static const NativeMethodReg g_regs[] = {
    {"Landroid/app/NativeActivity;", "loadNativeCode",
     "(Ljava/lang/String;Ljava/lang/String;Landroid/os/MessageQueue;Ljava/lang/String;Ljava/lang/String;"
     "Ljava/lang/String;ILandroid/content/res/AssetManager;[BLjava/lang/ClassLoader;Ljava/lang/String;)J",
     NativeActivity_loadNativeCode},
    {"Landroid/app/NativeActivity;", "getDlError", "()Ljava/lang/String;", NativeActivity_getDlError},
    {"Landroid/app/NativeActivity;", "unloadNativeCode", "(J)V", NativeActivity_unloadNativeCode},
    {"Landroid/app/NativeActivity;", "onStartNative", "(J)V", NativeActivity_onStart},
    {"Landroid/app/NativeActivity;", "onResumeNative", "(J)V", NativeActivity_onResume},
    {"Landroid/app/NativeActivity;", "onSaveInstanceStateNative", "(J)[B", NativeActivity_onSaveInstanceState},
    {"Landroid/app/NativeActivity;", "onPauseNative", "(J)V", NativeActivity_onPause},
    {"Landroid/app/NativeActivity;", "onStopNative", "(J)V", NativeActivity_onStop},
    {"Landroid/app/NativeActivity;", "onConfigurationChangedNative", "(J)V", NativeActivity_onConfigurationChanged},
    {"Landroid/app/NativeActivity;", "onLowMemoryNative", "(J)V", NativeActivity_onLowMemory},
    {"Landroid/app/NativeActivity;", "onWindowFocusChangedNative", "(JZ)V", NativeActivity_onWindowFocusChanged},
    {"Landroid/app/NativeActivity;", "onSurfaceCreatedNative", "(JLandroid/view/Surface;)V",
     NativeActivity_onSurfaceCreated},
    {"Landroid/app/NativeActivity;", "onSurfaceChangedNative", "(JLandroid/view/Surface;III)V",
     NativeActivity_onSurfaceChanged},
    {"Landroid/app/NativeActivity;", "onSurfaceRedrawNeededNative", "(JLandroid/view/Surface;)V",
     NativeActivity_onSurfaceRedrawNeeded},
    {"Landroid/app/NativeActivity;", "onSurfaceDestroyedNative", "(J)V", NativeActivity_onSurfaceDestroyed},
    {"Landroid/app/NativeActivity;", "onInputQueueCreatedNative", "(JJ)V", NativeActivity_onInputQueueCreated},
    {"Landroid/app/NativeActivity;", "onInputQueueDestroyedNative", "(JJ)V", NativeActivity_onInputQueueDestroyed},
    {"Landroid/app/NativeActivity;", "onContentRectChangedNative", "(JIIII)V", NativeActivity_onContentRectChanged},
};

void native_activity_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
