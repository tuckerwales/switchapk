/*
 * ANativeWindow (WS9). Lock returns RGBA_8888 bytes (R, G, B, A), which is
 * what NDK code writes. The Surface queue stores ARGB ints, so unlock
 * converts and posts through Surface.lockGlBuffer / unlockGlBufferAndPost,
 * the same consumer path as canvas and EGL.
 */
#include "native_window.h"
#include "nativeloader.h"

#include <errno.h>

#define LOG_TAG "nativewindow"

#define ANW_MAGIC 0x53414e57u /* 'SANW' */

struct ANativeWindow {
    uint32_t magic;
    int refs;
    Object *surface; /* vm_add_root */
    int width, height, format;
    bool opaque;
    uint8_t *bits; /* RGBA, present while locked */
    int locked;
    uint32_t *last; /* last posted frame, ARGB */
    int last_w, last_h;
    pthread_mutex_t mu;
};

bool anw_is(const void *p) {
    if (!p || ((uintptr_t)p & (sizeof(void *) - 1)) != 0) return false;
    return ((const ANativeWindow *)p)->magic == ANW_MAGIC;
}

static int surface_dim(VMThread *t, Object *surface, const char *which) {
    JValue q = vm_call_virtual(t, surface, "getBufferQueue", "()Landroid/view/Surface$BufferQueue;");
    if (t->exception || !q.l) {
        t->exception = NULL;
        return 0;
    }
    JValue v = vm_call_virtual(t, q.l, which, "()I");
    if (t->exception) {
        t->exception = NULL;
        return 0;
    }
    return v.i;
}

ANativeWindow *anw_create(VMThread *t, Object *surface) {
    if (!surface) return NULL;
    ANativeWindow *w = sa_calloc(1, sizeof *w);
    w->magic = ANW_MAGIC;
    w->refs = 1;
    w->surface = surface;
    vm_add_root(&w->surface);
    w->width = surface_dim(t, surface, "getWidth");
    w->height = surface_dim(t, surface, "getHeight");
    if (w->width < 1) w->width = 1;
    if (w->height < 1) w->height = 1;
    w->format = WINDOW_FORMAT_RGBA_8888;
    JValue op = vm_call_virtual(t, surface, "isOpaqueBuffer", "()Z");
    if (t->exception) t->exception = NULL;
    else w->opaque = op.i != 0;
    pthread_mutex_init(&w->mu, NULL);
    return w;
}

void anw_acquire(ANativeWindow *w) {
    if (!anw_is(w)) return;
    pthread_mutex_lock(&w->mu);
    w->refs++;
    pthread_mutex_unlock(&w->mu);
}

void anw_release(ANativeWindow *w) {
    if (!anw_is(w)) return;
    pthread_mutex_lock(&w->mu);
    int refs = --w->refs;
    pthread_mutex_unlock(&w->mu);
    if (refs > 0) return;
    VMThread *t = vm_current_thread();
    if (t) vm_remove_root(&w->surface);
    free(w->bits);
    free(w->last);
    pthread_mutex_destroy(&w->mu);
    w->magic = 0;
    free(w);
}

int anw_width(ANativeWindow *w) { return anw_is(w) ? w->width : 0; }
int anw_height(ANativeWindow *w) { return anw_is(w) ? w->height : 0; }
bool anw_opaque(ANativeWindow *w) { return anw_is(w) && w->opaque; }

void anw_note_size(ANativeWindow *w, int width, int height) {
    if (!anw_is(w) || width < 1 || height < 1) return;
    pthread_mutex_lock(&w->mu);
    if (w->width != width || w->height != height) {
        free(w->last);
        w->last = NULL;
        w->last_w = w->last_h = 0;
        free(w->bits);
        w->bits = NULL;
    }
    w->width = width;
    w->height = height;
    pthread_mutex_unlock(&w->mu);
}

static void argb_to_rgba(const uint32_t *src, uint8_t *dst, int n) {
    for (int i = 0; i < n; i++) {
        uint32_t p = src[i];
        dst[0] = (uint8_t)((p >> 16) & 255);
        dst[1] = (uint8_t)((p >> 8) & 255);
        dst[2] = (uint8_t)(p & 255);
        dst[3] = (uint8_t)(p >> 24);
        dst += 4;
    }
}

static void rgba_to_argb(const uint8_t *src, uint32_t *dst, int n, bool opaque) {
    for (int i = 0; i < n; i++, src += 4) {
        uint32_t a = opaque ? 255u : src[3];
        dst[i] = (a << 24) | ((uint32_t)src[0] << 16) | ((uint32_t)src[1] << 8) | src[2];
    }
}

bool anw_post_argb(ANativeWindow *w, const uint32_t *argb, int width, int height) {
    if (!anw_is(w) || !argb || width < 1 || height < 1) return false;
    VMThread *t = vm_current_thread();
    if (!t || !t->has_gil) {
        LOGE("posting a native window frame requires the VM thread");
        return false;
    }
    size_t n = (size_t)width * (size_t)height;
    uint32_t *keep = malloc(n * 4);
    if (!keep) return false;
    memcpy(keep, argb, n * 4);

    JValue pix = vm_call_virtual(t, w->surface, "lockGlBuffer", "(II)[I", width, height);
    if (t->exception || !pix.l) {
        if (t->exception) {
            vm_print_exception(t, t->exception);
            t->exception = NULL;
        }
        free(keep);
        LOGW("Surface.lockGlBuffer(%d,%d) failed", width, height);
        return false;
    }
    ArrayObject *arr = (ArrayObject *)pix.l;
    if (arr->length < (int32_t)n) {
        free(keep);
        return false;
    }
    memcpy(ARRAY_DATA(arr, uint32_t), argb, n * 4);
    vm_call_virtual(t, w->surface, "unlockGlBufferAndPost", "()V");
    if (t->exception) {
        vm_print_exception(t, t->exception);
        t->exception = NULL;
        free(keep);
        return false;
    }
    pthread_mutex_lock(&w->mu);
    free(w->last);
    w->last = keep;
    w->last_w = width;
    w->last_h = height;
    pthread_mutex_unlock(&w->mu);
    return true;
}

void ANativeWindow_acquire(ANativeWindow *window) { anw_acquire(window); }
void ANativeWindow_release(ANativeWindow *window) { anw_release(window); }
int32_t ANativeWindow_getWidth(ANativeWindow *window) { return anw_width(window); }
int32_t ANativeWindow_getHeight(ANativeWindow *window) { return anw_height(window); }
int32_t ANativeWindow_getFormat(ANativeWindow *window) { return anw_is(window) ? window->format : 0; }

int32_t ANativeWindow_setBuffersGeometry(ANativeWindow *window, int32_t width, int32_t height, int32_t format) {
    if (!anw_is(window)) return -EINVAL;
    if (format != 0 && format != WINDOW_FORMAT_RGBA_8888 && format != WINDOW_FORMAT_RGBX_8888) {
        LOGW("ANativeWindow format %d is not supported; using RGBA_8888", format);
        format = WINDOW_FORMAT_RGBA_8888;
    }
    pthread_mutex_lock(&window->mu);
    if (format != 0) window->format = format;
    pthread_mutex_unlock(&window->mu);
    if (width > 0 && height > 0) anw_note_size(window, width, height);
    return 0;
}

int32_t ANativeWindow_lock(ANativeWindow *window, ANativeWindow_Buffer *out, ARect *dirty) {
    if (!anw_is(window) || !out) return -EINVAL;
    pthread_mutex_lock(&window->mu);
    if (window->locked) {
        pthread_mutex_unlock(&window->mu);
        return -EBUSY;
    }
    int w = window->width, h = window->height;
    size_t n = (size_t)w * (size_t)h;
    if (!window->bits) window->bits = calloc(n, 4);
    if (!window->bits) {
        pthread_mutex_unlock(&window->mu);
        return -ENOMEM;
    }
    if (window->last && window->last_w == w && window->last_h == h) argb_to_rgba(window->last, window->bits, (int)n);
    else memset(window->bits, 0, n * 4);
    window->locked = 1;
    pthread_mutex_unlock(&window->mu);
    out->bits = window->bits;
    out->width = w;
    out->height = h;
    out->stride = w;
    out->format = window->format;
    if (dirty) {
        dirty->left = 0;
        dirty->top = 0;
        dirty->right = w;
        dirty->bottom = h;
    }
    return 0;
}

int32_t ANativeWindow_unlockAndPost(ANativeWindow *window) {
    if (!anw_is(window)) return -EINVAL;
    pthread_mutex_lock(&window->mu);
    if (!window->locked || !window->bits) {
        pthread_mutex_unlock(&window->mu);
        return -EINVAL;
    }
    int w = window->width, h = window->height;
    bool opaque = window->opaque || window->format == WINDOW_FORMAT_RGBX_8888;
    size_t n = (size_t)w * (size_t)h;
    uint32_t *argb = malloc(n * 4);
    if (!argb) {
        pthread_mutex_unlock(&window->mu);
        return -ENOMEM;
    }
    rgba_to_argb(window->bits, argb, (int)n, opaque);
    window->locked = 0;
    pthread_mutex_unlock(&window->mu);
    bool ok = anw_post_argb(window, argb, w, h);
    free(argb);
    return ok ? 0 : -EIO;
}

ANativeWindow *ANativeWindow_fromSurface(void *env, void *surface) {
    SA_UNUSED(env);
    VMThread *t = vm_current_thread();
    if (!t) return NULL;
    Object *o = vm_jni_decode(t, surface);
    if (!o) return NULL;
    return anw_create(t, o);
}
