/*
 * EGL and GLUtils natives, and the GL ES entry point loader (WS8).
 *
 * android.opengl.EGL14 and the javax.microedition.khronos.egl EGL10
 * implementation are Java classes over android.opengl.EGLNative, whose
 * natives here wrap the real EGL. Handles cross into Java as longs: real
 * EGLDisplay/EGLConfig/EGLContext values, and for surfaces a pointer to an
 * SaSurf record.
 *
 * Window surfaces: Android hands EGL a Surface backed by our software
 * buffer queue (ARCHITECTURE 6.6). Each window surface is a pbuffer of the
 * queue's size; on eglSwapBuffers the Java side asks nReadWindow to read the
 * finished frame back into the queue's back buffer and posts it, so GL
 * content goes through the same consumer path as lockCanvas (SurfaceView,
 * TextureView). This works with surfaceless Mesa on the host (screenshots)
 * and on the Switch; a direct NWindow path for fullscreen GL is a later step.
 */
#include "android_gl.h"

#if defined(__SWITCH__) && defined(SA_HAVE_EGL)
#include <EGL/egl.h>
#elif !defined(__SWITCH__)
#include <dlfcn.h>
#endif

#define LOG_TAG "egl"

SaGl sa_gl;
SaEgl sa_egl;

#define EGL_SURFACE_TYPE 0x3033
#define EGL_PBUFFER_BIT 0x0001
#define EGL_WINDOW_BIT 0x0004
#define EGL_NONE 0x3038
#define EGL_WIDTH 0x3057
#define EGL_HEIGHT 0x3056
#define EGL_DRAW 0x3059
#define EGL_READ 0x305A
#define EGL_CONTEXT_CLIENT_VERSION 0x3098
#define EGL_OPENGL_ES_API 0x30A0
#define EGL_RECORDABLE_ANDROID 0x3142
#define EGL_FRAMEBUFFER_TARGET_ANDROID 0x3147
#define EGL_PLATFORM_SURFACELESS_MESA 0x31DD

#define GL_FRAMEBUFFER 0x8D40
#define GL_FRAMEBUFFER_BINDING 0x8CA6
#define GL_PACK_ALIGNMENT 0x0D05
#define GL_PIXEL_PACK_BUFFER 0x88EB
#define GL_PIXEL_PACK_BUFFER_BINDING 0x88ED
#define GL_RGBA 0x1908
#define GL_UNSIGNED_BYTE 0x1401

typedef struct {
    SaEGLSurface real;
    SaEGLDisplay dpy;
    SaEGLConfig cfg;
    int w, h;
    bool window;
} SaSurf;

static _Thread_local SaSurf *tl_draw, *tl_read;
static pthread_mutex_t g_load_lock = PTHREAD_MUTEX_INITIALIZER;
static int g_loaded; /* 0 not tried, 1 ok, -1 failed */

/* ---- loading ---------------------------------------------------------------------------------------------- */

#ifndef __SWITCH__
static void *g_egl_lib, *g_gles2_lib, *g_gles1_lib;

static void *open_first(const char *const *names) {
    for (int i = 0; names[i]; i++) {
        void *h = dlopen(names[i], RTLD_NOW | RTLD_LOCAL);
        if (h) return h;
    }
    return NULL;
}
#endif

void *sa_gl_proc(const char *name) {
    void *p = NULL;
#ifndef __SWITCH__
    if (g_gles2_lib) p = dlsym(g_gles2_lib, name);
    if (!p && g_gles1_lib) p = dlsym(g_gles1_lib, name);
    if (!p && g_egl_lib) p = dlsym(g_egl_lib, name);
#endif
    if (!p && sa_egl.eglGetProcAddress) p = sa_egl.eglGetProcAddress(name);
    return p;
}

bool sa_gl_load(void) {
    pthread_mutex_lock(&g_load_lock);
    if (g_loaded) {
        pthread_mutex_unlock(&g_load_lock);
        return g_loaded > 0;
    }
#if defined(__SWITCH__) && !defined(SA_HAVE_EGL)
    LOGW("built without Mesa (switch-mesa): OpenGL ES is unavailable");
    g_loaded = -1;
    pthread_mutex_unlock(&g_load_lock);
    return false;
#elif defined(__SWITCH__)
    /* Mesa is linked statically; its eglGetProcAddress resolves core EGL and GL ES functions too */
    sa_egl.eglGetProcAddress = (void *(*)(const char *))eglGetProcAddress;
#define SA_EGL_PROC(ret, name, params) sa_egl.name = (ret(*) params)eglGetProcAddress(#name);
    SA_EGL_FUNCS(SA_EGL_PROC)
#undef SA_EGL_PROC
#else
    static const char *const egl_names[] = {"libEGL.so.1", "libEGL.so", NULL};
    static const char *const gles2_names[] = {"libGLESv2.so.2", "libGLESv2.so", NULL};
    static const char *const gles1_names[] = {"libGLESv1_CM.so.1", "libGLESv1_CM.so", NULL};
    g_egl_lib = open_first(egl_names);
    if (!g_egl_lib) {
        LOGW("libEGL not found (%s): OpenGL ES is unavailable", dlerror());
        g_loaded = -1;
        pthread_mutex_unlock(&g_load_lock);
        return false;
    }
    g_gles2_lib = open_first(gles2_names);
    g_gles1_lib = open_first(gles1_names);
#define SA_EGL_SYM(ret, name, params) sa_egl.name = (ret(*) params)dlsym(g_egl_lib, #name);
    SA_EGL_FUNCS(SA_EGL_SYM)
#undef SA_EGL_SYM
    if (!sa_egl.eglGetPlatformDisplayEXT && sa_egl.eglGetProcAddress)
        sa_egl.eglGetPlatformDisplayEXT =
            (SaEGLDisplay(*)(uint32_t, void *, const int32_t *))sa_egl.eglGetProcAddress("eglGetPlatformDisplayEXT");
#endif
    if (!sa_egl.eglGetDisplay || !sa_egl.eglInitialize || !sa_egl.eglCreateContext) {
        LOGW("EGL entry points missing: OpenGL ES is unavailable");
        g_loaded = -1;
        pthread_mutex_unlock(&g_load_lock);
        return false;
    }
#define SA_GL_SYM(ret, name, params) sa_gl.name = (ret(*) params)sa_gl_proc(#name);
    SA_GL_FUNCS(SA_GL_SYM)
#undef SA_GL_SYM
    g_loaded = 1;
    pthread_mutex_unlock(&g_load_lock);
    LOGI("EGL loaded");
    return true;
}

/* ---- helpers shared with the GLES bindings ------------------------------------------------------------- */

void *gles_buffer(Object *buf) {
    if (!buf) return NULL;
    uint8_t *base = vm_buffer_address(buf);
    if (!base) return NULL;
    int32_t pos = vm_get_int_by_name(buf, "position");
    int32_t shift = vm_get_int_by_name(buf, "elementSizeShift");
    return base + ((size_t)pos << shift);
}

int32_t gles_buffer_remaining_bytes(Object *buf) {
    if (!buf) return 0;
    int32_t pos = vm_get_int_by_name(buf, "position");
    int32_t lim = vm_get_int_by_name(buf, "limit");
    int32_t shift = vm_get_int_by_name(buf, "elementSizeShift");
    return (lim - pos) << shift;
}

static SaMap g_logged;
static pthread_mutex_t g_logged_lock = PTHREAD_MUTEX_INITIALIZER;

static bool first_time(const char *key) {
    pthread_mutex_lock(&g_logged_lock);
    bool first = sa_map_get(&g_logged, key) == NULL;
    if (first) sa_map_put(&g_logged, key, (void *)1);
    pthread_mutex_unlock(&g_logged_lock);
    return first;
}

void gles_missing(const char *name) {
    if (first_time(name)) LOGW("%s: no GL ES implementation (no EGL display yet, or not exported)", name);
}

void gles_unsupported(const char *what) {
    if (first_time(what)) LOGW("%s is not implemented", what);
}

/* ---- EGLNative ---------------------------------------------------------------------------------------------- */

#define H(p) ((int64_t)(intptr_t)(p))
#define P(v) ((void *)(intptr_t)(v))

static int32_t *int_array(ArrayObject *a) { return a ? ARRAY_DATA(a, int32_t) : NULL; }

NATIVE(EGLNative_nLoad) {
    UNUSED_ARGS();
    R_BOOL(sa_gl_load());
}

NATIVE(EGLNative_nGetDisplay) {
    UNUSED_ARGS();
    if (!sa_gl_load()) {
        R_LONG(0);
        return;
    }
    SaEGLDisplay d = NULL;
#ifndef __SWITCH__
    /* no window system on the host: render offscreen with surfaceless Mesa */
    if (sa_egl.eglGetPlatformDisplayEXT) d = sa_egl.eglGetPlatformDisplayEXT(EGL_PLATFORM_SURFACELESS_MESA, NULL, NULL);
#endif
    if (!d) d = sa_egl.eglGetDisplay(NULL);
    R_LONG(H(d));
}

NATIVE(EGLNative_nInitialize) {
    UNUSED_ARGS();
    int32_t major = 0, minor = 0;
    bool ok = sa_egl.eglInitialize && sa_egl.eglInitialize(P(A_LONG(0)), &major, &minor);
    ArrayObject *ver = A_ARR(2);
    if (ok && ver && ver->length >= 2) {
        /* report EGL 1.4 like Android */
        ARRAY_DATA(ver, int32_t)[0] = 1;
        ARRAY_DATA(ver, int32_t)[1] = 4;
    }
    R_BOOL(ok);
}

NATIVE(EGLNative_nTerminate) {
    UNUSED_ARGS();
    R_BOOL(sa_egl.eglTerminate && sa_egl.eglTerminate(P(A_LONG(0))));
}

NATIVE(EGLNative_nQueryString) {
    UNUSED_ARGS();
    const char *s = sa_egl.eglQueryString ? sa_egl.eglQueryString(P(A_LONG(0)), A_INT(2)) : NULL;
    R_OBJ(s ? vm_new_string_utf8(t, s) : NULL);
}

NATIVE(EGLNative_nGetError) {
    UNUSED_ARGS();
    R_INT(sa_egl.eglGetError ? sa_egl.eglGetError() : 0x3001 /* EGL_NOT_INITIALIZED */);
}

/* Copies an attribute list, asking for pbuffers instead of windows and dropping Android-only attributes. */
static int32_t *config_attribs(ArrayObject *a) {
    int n = a ? a->length : 0;
    int32_t *src = int_array(a);
    int32_t *out = sa_malloc(sizeof(int32_t) * (size_t)(n + 3));
    int o = 0;
    bool have_type = false;
    for (int i = 0; i + 1 < n && src[i] != EGL_NONE; i += 2) {
        int32_t k = src[i], v = src[i + 1];
        if (k == EGL_RECORDABLE_ANDROID || k == EGL_FRAMEBUFFER_TARGET_ANDROID) continue;
        if (k == EGL_SURFACE_TYPE) {
            have_type = true;
            if (v != -1 /* EGL_DONT_CARE */ && (v & EGL_WINDOW_BIT)) v = (v & ~EGL_WINDOW_BIT) | EGL_PBUFFER_BIT;
        }
        out[o++] = k;
        out[o++] = v;
    }
    if (!have_type) {
        out[o++] = EGL_SURFACE_TYPE;
        out[o++] = EGL_PBUFFER_BIT;
    }
    out[o] = EGL_NONE;
    return out;
}

NATIVE(EGLNative_nChooseConfig) {
    UNUSED_ARGS();
    SaEGLDisplay dpy = P(A_LONG(0));
    int32_t *attribs = config_attribs(A_ARR(2));
    ArrayObject *configs = A_ARR(3);
    int32_t size = A_INT(4);
    ArrayObject *num = A_ARR(5);
    if (configs && size > configs->length) size = configs->length;
    SaEGLConfig *tmp = configs && size > 0 ? sa_calloc((size_t)size, sizeof(SaEGLConfig)) : NULL;
    int32_t n = 0;
    bool ok = sa_egl.eglChooseConfig(dpy, attribs, tmp, tmp ? size : 0, &n);
    if (ok && tmp)
        for (int i = 0; i < n && i < size; i++) ARRAY_DATA(configs, int64_t)[i] = H(tmp[i]);
    if (num && num->length > 0) ARRAY_DATA(num, int32_t)[0] = ok ? n : 0;
    free(tmp);
    free(attribs);
    R_BOOL(ok);
}

NATIVE(EGLNative_nGetConfigs) {
    UNUSED_ARGS();
    SaEGLDisplay dpy = P(A_LONG(0));
    /* only configs that can back our window surfaces */
    int32_t attribs[] = {EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_NONE};
    ArrayObject *configs = A_ARR(2);
    int32_t size = A_INT(3);
    ArrayObject *num = A_ARR(4);
    if (configs && size > configs->length) size = configs->length;
    SaEGLConfig *tmp = configs && size > 0 ? sa_calloc((size_t)size, sizeof(SaEGLConfig)) : NULL;
    int32_t n = 0;
    bool ok = sa_egl.eglChooseConfig(dpy, attribs, tmp, tmp ? size : 0, &n);
    if (ok && tmp)
        for (int i = 0; i < n && i < size; i++) ARRAY_DATA(configs, int64_t)[i] = H(tmp[i]);
    if (num && num->length > 0) ARRAY_DATA(num, int32_t)[0] = ok ? n : 0;
    free(tmp);
    R_BOOL(ok);
}

NATIVE(EGLNative_nGetConfigAttrib) {
    UNUSED_ARGS();
    int32_t attr = A_INT(4);
    int32_t v = 0;
    bool ok;
    if (attr == EGL_RECORDABLE_ANDROID || attr == EGL_FRAMEBUFFER_TARGET_ANDROID) {
        v = 1;
        ok = true;
    } else {
        ok = sa_egl.eglGetConfigAttrib(P(A_LONG(0)), P(A_LONG(2)), attr, &v);
        /* pbuffer-capable configs back window surfaces too */
        if (ok && attr == EGL_SURFACE_TYPE && (v & EGL_PBUFFER_BIT)) v |= EGL_WINDOW_BIT;
    }
    ArrayObject *out = A_ARR(5);
    if (ok && out && out->length > 0) ARRAY_DATA(out, int32_t)[0] = v;
    R_BOOL(ok);
}

NATIVE(EGLNative_nCreateContext) {
    UNUSED_ARGS();
    if (sa_egl.eglBindAPI) sa_egl.eglBindAPI(EGL_OPENGL_ES_API);
    int32_t none = EGL_NONE;
    int32_t *attribs = A_ARR(6) ? int_array(A_ARR(6)) : &none;
    SaEGLContext c = sa_egl.eglCreateContext(P(A_LONG(0)), P(A_LONG(2)), P(A_LONG(4)), attribs);
    R_LONG(H(c));
}

NATIVE(EGLNative_nDestroyContext) {
    UNUSED_ARGS();
    R_BOOL(sa_egl.eglDestroyContext(P(A_LONG(0)), P(A_LONG(2))));
}

static SaEGLSurface make_pbuffer(SaEGLDisplay dpy, SaEGLConfig cfg, int w, int h) {
    int32_t attribs[] = {EGL_WIDTH, w, EGL_HEIGHT, h, EGL_NONE};
    return sa_egl.eglCreatePbufferSurface(dpy, cfg, attribs);
}

/* nCreateSurface(long dpy, long cfg, int[] attribs, boolean window, int w, int h): window surfaces ignore attribs. */
NATIVE(EGLNative_nCreateSurface) {
    UNUSED_ARGS();
    SaEGLDisplay dpy = P(A_LONG(0));
    SaEGLConfig cfg = P(A_LONG(2));
    ArrayObject *attribs = A_ARR(4);
    bool window = A_BOOL(5);
    int w = A_INT(6), h = A_INT(7);
    SaEGLSurface real;
    if (window) {
        real = make_pbuffer(dpy, cfg, w > 0 ? w : 1, h > 0 ? h : 1);
    } else {
        int32_t none = EGL_NONE;
        real = sa_egl.eglCreatePbufferSurface(dpy, cfg, attribs ? int_array(attribs) : &none);
    }
    if (!real) {
        R_LONG(0);
        return;
    }
    SaSurf *s = sa_calloc(1, sizeof *s);
    s->real = real;
    s->dpy = dpy;
    s->cfg = cfg;
    s->window = window;
    int32_t v = 0;
    s->w = sa_egl.eglQuerySurface(dpy, real, EGL_WIDTH, &v) ? v : w;
    s->h = sa_egl.eglQuerySurface(dpy, real, EGL_HEIGHT, &v) ? v : h;
    R_LONG(H(s));
}

NATIVE(EGLNative_nDestroySurface) {
    UNUSED_ARGS();
    SaSurf *s = P(A_LONG(2));
    if (!s) {
        R_BOOL(false);
        return;
    }
    bool ok = sa_egl.eglDestroySurface(P(A_LONG(0)), s->real);
    if (tl_draw == s) tl_draw = NULL;
    if (tl_read == s) tl_read = NULL;
    /* EGL defers destruction of a current surface; the record is small, so a current one is leaked */
    if (tl_draw != s && tl_read != s) free(s);
    R_BOOL(ok);
}

/* Window surfaces follow their buffer queue's size: replaces the pbuffer, keeping it current. */
NATIVE(EGLNative_nResizeWindow) {
    UNUSED_ARGS();
    SaSurf *s = P(A_LONG(0));
    int w = A_INT(2), h = A_INT(3);
    if (!s || !s->window || (s->w == w && s->h == h) || w <= 0 || h <= 0) {
        R_BOOL(false);
        return;
    }
    SaEGLSurface real = make_pbuffer(s->dpy, s->cfg, w, h);
    if (!real) {
        R_BOOL(false);
        return;
    }
    SaEGLSurface old = s->real;
    s->real = real;
    s->w = w;
    s->h = h;
    if (tl_draw == s || tl_read == s) {
        SaEGLContext ctx = sa_egl.eglGetCurrentContext();
        sa_egl.eglMakeCurrent(s->dpy, tl_draw ? tl_draw->real : NULL, tl_read ? tl_read->real : NULL, ctx);
    }
    sa_egl.eglDestroySurface(s->dpy, old);
    R_BOOL(true);
}

NATIVE(EGLNative_nMakeCurrent) {
    UNUSED_ARGS();
    SaSurf *draw = P(A_LONG(2));
    SaSurf *read = P(A_LONG(4));
    bool ok = sa_egl.eglMakeCurrent(P(A_LONG(0)), draw ? draw->real : NULL, read ? read->real : NULL, P(A_LONG(6)));
    if (ok) {
        tl_draw = draw;
        tl_read = read;
    }
    R_BOOL(ok);
}

NATIVE(EGLNative_nGetCurrentContext) {
    UNUSED_ARGS();
    R_LONG(sa_egl.eglGetCurrentContext ? H(sa_egl.eglGetCurrentContext()) : 0);
}

NATIVE(EGLNative_nGetCurrentDisplay) {
    UNUSED_ARGS();
    R_LONG(sa_egl.eglGetCurrentDisplay ? H(sa_egl.eglGetCurrentDisplay()) : 0);
}

NATIVE(EGLNative_nGetCurrentSurface) {
    UNUSED_ARGS();
    R_LONG(H(A_INT(0) == EGL_READ ? tl_read : tl_draw));
}

NATIVE(EGLNative_nQuerySurface) {
    UNUSED_ARGS();
    SaSurf *s = P(A_LONG(2));
    int32_t attr = A_INT(4);
    int32_t v = 0;
    bool ok;
    if (!s) {
        ok = false;
    } else if (attr == EGL_WIDTH || attr == EGL_HEIGHT) {
        v = attr == EGL_WIDTH ? s->w : s->h;
        ok = true;
    } else {
        ok = sa_egl.eglQuerySurface(P(A_LONG(0)), s->real, attr, &v);
    }
    ArrayObject *out = A_ARR(5);
    if (ok && out && out->length > 0) ARRAY_DATA(out, int32_t)[0] = v;
    R_BOOL(ok);
}

NATIVE(EGLNative_nQueryContext) {
    UNUSED_ARGS();
    int32_t v = 0;
    bool ok = sa_egl.eglQueryContext(P(A_LONG(0)), P(A_LONG(2)), A_INT(4), &v);
    ArrayObject *out = A_ARR(5);
    if (ok && out && out->length > 0) ARRAY_DATA(out, int32_t)[0] = v;
    R_BOOL(ok);
}

NATIVE(EGLNative_nSwapBuffers) {
    UNUSED_ARGS();
    SaSurf *s = P(A_LONG(2));
    if (!s) {
        R_BOOL(false);
        return;
    }
    vm_gil_release(t);
    bool ok = sa_egl.eglSwapBuffers(P(A_LONG(0)), s->real);
    vm_gil_acquire(t);
    R_BOOL(ok);
}

static inline uint32_t unpremul(uint32_t a, uint32_t c) { return a ? (c * 255 + a / 2) / a : 0; }

/*
 * nReadWindow(long surf, int[] dst, boolean opaque): reads the current frame of a window surface (which must be
 * the current draw surface) into dst as unpremultiplied ARGB rows, top row first.
 */
NATIVE(EGLNative_nReadWindow) {
    UNUSED_ARGS();
    SaSurf *s = P(A_LONG(0));
    ArrayObject *dst = A_ARR(2);
    bool opaque = A_BOOL(3);
    if (!s || !dst || !sa_gl.glReadPixels || tl_draw != s || dst->length < s->w * s->h) {
        R_BOOL(false);
        return;
    }
    int w = s->w, h = s->h;
    uint32_t *out = ARRAY_DATA(dst, uint32_t);
    uint8_t *rgba = malloc((size_t)w * (size_t)h * 4);
    if (!rgba) {
        R_BOOL(false);
        return;
    }
    int32_t version = 1;
    sa_egl.eglQueryContext(s->dpy, sa_egl.eglGetCurrentContext(), EGL_CONTEXT_CLIENT_VERSION, &version);
    vm_gil_release(t);
    int32_t fbo = 0, pack = 4, pbo = 0;
    if (version >= 2 && sa_gl.glBindFramebuffer) {
        sa_gl.glGetIntegerv(GL_FRAMEBUFFER_BINDING, &fbo);
        if (fbo) sa_gl.glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
    if (version >= 3 && sa_gl.glBindBuffer) {
        sa_gl.glGetIntegerv(GL_PIXEL_PACK_BUFFER_BINDING, &pbo);
        if (pbo) sa_gl.glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
    }
    sa_gl.glGetIntegerv(GL_PACK_ALIGNMENT, &pack);
    if (pack != 4) sa_gl.glPixelStorei(GL_PACK_ALIGNMENT, 4);
    sa_gl.glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
    if (pack != 4) sa_gl.glPixelStorei(GL_PACK_ALIGNMENT, pack);
    if (pbo) sa_gl.glBindBuffer(GL_PIXEL_PACK_BUFFER, (uint32_t)pbo);
    if (fbo) sa_gl.glBindFramebuffer(GL_FRAMEBUFFER, (uint32_t)fbo);
    for (int y = 0; y < h; y++) {
        const uint8_t *src = rgba + (size_t)(h - 1 - y) * (size_t)w * 4;
        uint32_t *row = out + (size_t)y * (size_t)w;
        for (int x = 0; x < w; x++, src += 4) {
            uint32_t r = src[0], g = src[1], b = src[2], a = src[3];
            if (opaque) {
                a = 255;
            } else if (a != 255) {
                r = unpremul(a, r);
                g = unpremul(a, g);
                b = unpremul(a, b);
                if (r > 255) r = 255;
                if (g > 255) g = 255;
                if (b > 255) b = 255;
            }
            row[x] = a << 24 | r << 16 | g << 8 | b;
        }
    }
    vm_gil_acquire(t);
    free(rgba);
    R_BOOL(true);
}

NATIVE(EGLNative_nBindAPI) {
    UNUSED_ARGS();
    R_BOOL(sa_egl.eglBindAPI && sa_egl.eglBindAPI((uint32_t)A_INT(0)));
}

NATIVE(EGLNative_nQueryAPI) {
    UNUSED_ARGS();
    R_INT(sa_egl.eglQueryAPI ? sa_egl.eglQueryAPI() : EGL_OPENGL_ES_API);
}

NATIVE(EGLNative_nWaitClient) {
    UNUSED_ARGS();
    R_BOOL(sa_egl.eglWaitClient && sa_egl.eglWaitClient());
}

NATIVE(EGLNative_nWaitGL) {
    UNUSED_ARGS();
    R_BOOL(sa_egl.eglWaitGL && sa_egl.eglWaitGL());
}

NATIVE(EGLNative_nWaitNative) {
    UNUSED_ARGS();
    R_BOOL(sa_egl.eglWaitNative && sa_egl.eglWaitNative(A_INT(0)));
}

NATIVE(EGLNative_nReleaseThread) {
    UNUSED_ARGS();
    tl_draw = tl_read = NULL;
    R_BOOL(sa_egl.eglReleaseThread && sa_egl.eglReleaseThread());
}

NATIVE(EGLNative_nSwapInterval) {
    UNUSED_ARGS();
    R_BOOL(sa_egl.eglSwapInterval && sa_egl.eglSwapInterval(P(A_LONG(0)), A_INT(2)));
}

NATIVE(EGLNative_nSurfaceAttrib) {
    UNUSED_ARGS();
    SaSurf *s = P(A_LONG(2));
    R_BOOL(s && sa_egl.eglSurfaceAttrib && sa_egl.eglSurfaceAttrib(P(A_LONG(0)), s->real, A_INT(4), A_INT(5)));
}

/* ---- GLUtils ------------------------------------------------------------------------------------------------ */

#define GL_ALPHA 0x1906
#define GL_RGB 0x1907
#define GL_LUMINANCE 0x1909
#define GL_LUMINANCE_ALPHA 0x190A
#define GL_UNSIGNED_SHORT_4_4_4_4 0x8033
#define GL_UNSIGNED_SHORT_5_5_5_1 0x8034
#define GL_UNSIGNED_SHORT_5_6_5 0x8363
#define GL_UNPACK_ALIGNMENT 0x0CF5

/*
 * nUpload(boolean sub, int target, int level, int internalformat, int xoffset, int yoffset, int[] argb, int width,
 *         int height, int format, int type, int border): converts unpremultiplied ARGB to the premultiplied layout
 * Android's GLUtils uploads (Android bitmaps are premultiplied) and calls glTexImage2D or glTexSubImage2D.
 * Returns 0 or -1 for an unsupported format/type pair (GLUtils throws).
 */
NATIVE(GLUtils_nUpload) {
    UNUSED_ARGS();
    bool sub = A_BOOL(0);
    int32_t target = A_INT(1), level = A_INT(2), ifmt = A_INT(3), xoff = A_INT(4), yoff = A_INT(5);
    ArrayObject *px = A_ARR(6);
    int32_t w = A_INT(7), h = A_INT(8), format = A_INT(9), type = A_INT(10), border = A_INT(11);
    if (!sa_gl.glTexImage2D || !sa_gl.glTexSubImage2D) {
        gles_missing("glTexImage2D");
        R_INT(0);
        return;
    }
    if (!px || px->length < w * h) {
        R_INT(-1);
        return;
    }
    int bpp;
    if (type == GL_UNSIGNED_BYTE) {
        bpp = format == GL_RGBA ? 4 : format == GL_RGB ? 3 : format == GL_LUMINANCE_ALPHA ? 2 : 1;
        if (format != GL_RGBA && format != GL_RGB && format != GL_ALPHA && format != GL_LUMINANCE &&
            format != GL_LUMINANCE_ALPHA) {
            R_INT(-1);
            return;
        }
    } else if (type == GL_UNSIGNED_SHORT_5_6_5 || type == GL_UNSIGNED_SHORT_4_4_4_4 ||
               type == GL_UNSIGNED_SHORT_5_5_5_1) {
        bpp = 2;
    } else {
        R_INT(-1);
        return;
    }
    size_t n = (size_t)w * (size_t)h;
    uint8_t *buf = malloc(n * (size_t)bpp + 1);
    if (!buf) {
        vm_throw_oom(t);
        return;
    }
    const uint32_t *src = ARRAY_DATA(px, uint32_t);
    for (size_t i = 0; i < n; i++) {
        uint32_t c = src[i];
        uint32_t a = c >> 24, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        if (a != 255) {
            r = (r * a + 127) / 255;
            g = (g * a + 127) / 255;
            b = (b * a + 127) / 255;
        }
        uint8_t *d = buf + i * (size_t)bpp;
        if (type == GL_UNSIGNED_BYTE) {
            if (format == GL_RGBA) {
                d[0] = (uint8_t)r, d[1] = (uint8_t)g, d[2] = (uint8_t)b, d[3] = (uint8_t)a;
            } else if (format == GL_RGB) {
                d[0] = (uint8_t)r, d[1] = (uint8_t)g, d[2] = (uint8_t)b;
            } else if (format == GL_ALPHA) {
                d[0] = (uint8_t)a;
            } else if (format == GL_LUMINANCE) {
                d[0] = (uint8_t)((r * 77 + g * 150 + b * 29) >> 8);
            } else {
                d[0] = (uint8_t)((r * 77 + g * 150 + b * 29) >> 8), d[1] = (uint8_t)a;
            }
        } else {
            uint16_t v;
            if (type == GL_UNSIGNED_SHORT_5_6_5)
                v = (uint16_t)((r >> 3) << 11 | (g >> 2) << 5 | (b >> 3));
            else if (type == GL_UNSIGNED_SHORT_4_4_4_4)
                v = (uint16_t)((r >> 4) << 12 | (g >> 4) << 8 | (b >> 4) << 4 | (a >> 4));
            else
                v = (uint16_t)((r >> 3) << 11 | (g >> 3) << 6 | (b >> 3) << 1 | (a >> 7));
            memcpy(d, &v, 2);
        }
    }
    int32_t align = 4;
    sa_gl.glGetIntegerv(GL_UNPACK_ALIGNMENT, &align);
    sa_gl.glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    if (sub)
        sa_gl.glTexSubImage2D((uint32_t)target, level, xoff, yoff, w, h, (uint32_t)format, (uint32_t)type, buf);
    else
        sa_gl.glTexImage2D((uint32_t)target, level, ifmt, w, h, border, (uint32_t)format, (uint32_t)type, buf);
    sa_gl.glPixelStorei(GL_UNPACK_ALIGNMENT, align);
    free(buf);
    R_INT(0);
}

static const NativeMethodReg g_regs[] = {
    {"Landroid/opengl/EGLNative;", "nLoad", "()Z", EGLNative_nLoad},
    {"Landroid/opengl/EGLNative;", "nGetDisplay", "()J", EGLNative_nGetDisplay},
    {"Landroid/opengl/EGLNative;", "nInitialize", "(J[I)Z", EGLNative_nInitialize},
    {"Landroid/opengl/EGLNative;", "nTerminate", "(J)Z", EGLNative_nTerminate},
    {"Landroid/opengl/EGLNative;", "nQueryString", "(JI)Ljava/lang/String;", EGLNative_nQueryString},
    {"Landroid/opengl/EGLNative;", "nGetError", "()I", EGLNative_nGetError},
    {"Landroid/opengl/EGLNative;", "nChooseConfig", "(J[I[JI[I)Z", EGLNative_nChooseConfig},
    {"Landroid/opengl/EGLNative;", "nGetConfigs", "(J[JI[I)Z", EGLNative_nGetConfigs},
    {"Landroid/opengl/EGLNative;", "nGetConfigAttrib", "(JJI[I)Z", EGLNative_nGetConfigAttrib},
    {"Landroid/opengl/EGLNative;", "nCreateContext", "(JJJ[I)J", EGLNative_nCreateContext},
    {"Landroid/opengl/EGLNative;", "nDestroyContext", "(JJ)Z", EGLNative_nDestroyContext},
    {"Landroid/opengl/EGLNative;", "nCreateSurface", "(JJ[IZII)J", EGLNative_nCreateSurface},
    {"Landroid/opengl/EGLNative;", "nDestroySurface", "(JJ)Z", EGLNative_nDestroySurface},
    {"Landroid/opengl/EGLNative;", "nResizeWindow", "(JII)Z", EGLNative_nResizeWindow},
    {"Landroid/opengl/EGLNative;", "nMakeCurrent", "(JJJJ)Z", EGLNative_nMakeCurrent},
    {"Landroid/opengl/EGLNative;", "nGetCurrentContext", "()J", EGLNative_nGetCurrentContext},
    {"Landroid/opengl/EGLNative;", "nGetCurrentDisplay", "()J", EGLNative_nGetCurrentDisplay},
    {"Landroid/opengl/EGLNative;", "nGetCurrentSurface", "(I)J", EGLNative_nGetCurrentSurface},
    {"Landroid/opengl/EGLNative;", "nQuerySurface", "(JJI[I)Z", EGLNative_nQuerySurface},
    {"Landroid/opengl/EGLNative;", "nQueryContext", "(JJI[I)Z", EGLNative_nQueryContext},
    {"Landroid/opengl/EGLNative;", "nSwapBuffers", "(JJ)Z", EGLNative_nSwapBuffers},
    {"Landroid/opengl/EGLNative;", "nReadWindow", "(J[IZ)Z", EGLNative_nReadWindow},
    {"Landroid/opengl/EGLNative;", "nBindAPI", "(I)Z", EGLNative_nBindAPI},
    {"Landroid/opengl/EGLNative;", "nQueryAPI", "()I", EGLNative_nQueryAPI},
    {"Landroid/opengl/EGLNative;", "nWaitClient", "()Z", EGLNative_nWaitClient},
    {"Landroid/opengl/EGLNative;", "nWaitGL", "()Z", EGLNative_nWaitGL},
    {"Landroid/opengl/EGLNative;", "nWaitNative", "(I)Z", EGLNative_nWaitNative},
    {"Landroid/opengl/EGLNative;", "nReleaseThread", "()Z", EGLNative_nReleaseThread},
    {"Landroid/opengl/EGLNative;", "nSwapInterval", "(JI)Z", EGLNative_nSwapInterval},
    {"Landroid/opengl/EGLNative;", "nSurfaceAttrib", "(JJII)Z", EGLNative_nSurfaceAttrib},
    {"Landroid/opengl/GLUtils;", "nUpload", "(ZIIIII[IIIIII)I", GLUtils_nUpload},
};

void android_opengl_register(void) {
    vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs));
    android_gles_gen_register();
    android_gles_special_register();
}
