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
 * TextureView). Native eglCreateWindowSurface takes an ANativeWindow and
 * posts the same way (sa_egl_native_proc). This works with surfaceless Mesa
 * on the host (screenshots). The Switch's Mesa has no pbuffers, so there
 * every surface is a framebuffer object instead ("FBO surfaces" below). A
 * direct NWindow path for fullscreen GL is a later step.
 */
#include "android_gl.h"
#include "nativeloader/native_window.h"

#if defined(__SWITCH__) && defined(SA_HAVE_EGL)
#include <EGL/egl.h>
#elif !defined(__SWITCH__)
#include <dlfcn.h>
#endif

#define LOG_TAG "egl"

SaGl sa_gl;
SaEgl sa_egl;

#define EGL_SUCCESS 0x3000
#define EGL_NOT_INITIALIZED 0x3001
#define EGL_BAD_ACCESS 0x3002
#define EGL_BAD_ALLOC 0x3003
#define EGL_BAD_NATIVE_WINDOW 0x300B
#define EGL_BAD_SURFACE 0x300D
#define EGL_BUFFER_SIZE 0x3020
#define EGL_ALPHA_SIZE 0x3021
#define EGL_DEPTH_SIZE 0x3025
#define EGL_STENCIL_SIZE 0x3026
#define EGL_CONFIG_ID 0x3028
#define EGL_SURFACE_TYPE 0x3033
#define EGL_PBUFFER_BIT 0x0001
#define EGL_WINDOW_BIT 0x0004
#define EGL_NONE 0x3038
#define EGL_WIDTH 0x3057
#define EGL_HEIGHT 0x3056
#define EGL_DRAW 0x3059
#define EGL_READ 0x305A
#define EGL_BACK_BUFFER 0x3084
#define EGL_RENDER_BUFFER 0x3086
#define EGL_SWAP_BEHAVIOR 0x3093
#define EGL_BUFFER_DESTROYED 0x3095
#define EGL_CONTEXT_CLIENT_VERSION 0x3098
#define EGL_OPENGL_ES_API 0x30A0
#define EGL_RECORDABLE_ANDROID 0x3142
#define EGL_FRAMEBUFFER_TARGET_ANDROID 0x3147
#define EGL_PLATFORM_SURFACELESS_MESA 0x31DD

#define GL_FRAMEBUFFER 0x8D40
#define GL_READ_FRAMEBUFFER 0x8CA8
#define GL_FRAMEBUFFER_BINDING 0x8CA6
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#define GL_RENDERBUFFER 0x8D41
#define GL_RENDERBUFFER_BINDING 0x8CA7
#define GL_COLOR_ATTACHMENT0 0x8CE0
#define GL_DEPTH_ATTACHMENT 0x8D00
#define GL_STENCIL_ATTACHMENT 0x8D20
#define GL_RGB8_OES 0x8051
#define GL_RGBA8_OES 0x8058
#define GL_DEPTH_COMPONENT16 0x81A5
#define GL_DEPTH_COMPONENT24_OES 0x81A6
#define GL_DEPTH24_STENCIL8_OES 0x88F0
#define GL_STENCIL_INDEX8 0x8D48
#define GL_PACK_ALIGNMENT 0x0D05
#define GL_PIXEL_PACK_BUFFER 0x88EB
#define GL_PIXEL_PACK_BUFFER_BINDING 0x88ED
#define GL_RGBA 0x1908
#define GL_UNSIGNED_BYTE 0x1401

#define SA_SURF_MAGIC 0x53415355u /* 'SASU' */

typedef struct SaSurf {
    uint32_t magic; /* first, so a native pointer can be told from a Mesa surface */
    SaEGLSurface real; /* NULL for FBO surfaces */
    SaEGLDisplay dpy;
    SaEGLConfig cfg;
    int w, h;
    bool window;
    ANativeWindow *anw; /* set for surfaces created from native code; Java path is NULL */
    /* FBO surfaces: the framebuffer and its renderbuffers, names in fbo_ctx (0 until first made current) */
    struct SaSurf *next; /* g_surfs */
    SaEGLContext fbo_ctx;
    uint32_t fbo, color_rb, ds_rb, color_format, ds_format;
    int fbo_w, fbo_h; /* renderbuffer size */
} SaSurf;

static SaSurf *as_surf(const void *p) {
    if (!p || ((uintptr_t)p & (sizeof(void *) - 1)) != 0) return NULL;
    const SaSurf *s = p;
    return s->magic == SA_SURF_MAGIC ? (SaSurf *)s : NULL;
}

static _Thread_local SaSurf *tl_draw, *tl_read;
static pthread_mutex_t g_load_lock = PTHREAD_MUTEX_INITIALIZER;
static int g_loaded; /* 0 not tried, 1 ok, -1 failed */

/* ---- FBO surfaces ------------------------------------------------------------------------------------------- */

/*
 * The Switch's Mesa (devkitPro switch-mesa) offers only EGL_WINDOW_BIT configs and cannot create pbuffers, but it
 * has EGL_KHR_surfaceless_context. On a display without pbuffer configs every surface (window or pbuffer) is a
 * framebuffer object: the context is made current with EGL_NO_SURFACE, the surface's FBO is created in it on first
 * use and bound, and binding framebuffer 0 binds the current surface's FBO. Frames are read back the same way.
 * SWITCHAPK_EGL_FBO=1 forces this mode, so the host can test it.
 */
static bool g_fbo;

/*
 * Android devices always offer RGB888 configs without alpha, and GLSurfaceView's default chooser wants exactly
 * that; switch-mesa only has RGBA8888. With FBO surfaces we own the colour buffer, so every config with alpha also
 * appears as an alpha-free variant: the driver config pointer with the low bit set (an RGB8 renderbuffer backs it).
 */
#define SA_CFG_NOALPHA ((uintptr_t)1)
#define SA_CFG_ID_NOALPHA 0x10000 /* added to the EGL_CONFIG_ID of the variant */

static SaEGLConfig cfg_real(SaEGLConfig c) { return (SaEGLConfig)((uintptr_t)c & ~SA_CFG_NOALPHA); }

static bool cfg_noalpha(SaEGLConfig c) { return ((uintptr_t)c & SA_CFG_NOALPHA) != 0; }

static SaSurf *g_surfs; /* every SaSurf, so a destroyed context can disown their FBOs */
static pthread_mutex_t g_surfs_lock = PTHREAD_MUTEX_INITIALIZER;

/* FBO names to delete next time their context is current (surfaces destroyed while it was not) */
typedef struct FboGarbage {
    struct FboGarbage *next;
    SaEGLDisplay dpy;
    SaEGLContext ctx;
    uint32_t fbo, rb[2];
} FboGarbage;
static FboGarbage *g_garbage;

/* the driver's glBindFramebuffer(OES); sa_gl and native code get the redirecting wrappers */
static void (*g_bind_fb)(uint32_t target, uint32_t fb);
static void (*g_bind_fb_oes)(uint32_t target, uint32_t fb);

static void bind_fb_redirect(void (*real)(uint32_t, uint32_t), uint32_t target, uint32_t fb) {
    if (!real) return;
    if (fb == 0 && g_fbo) {
        SaSurf *s = target == GL_READ_FRAMEBUFFER ? tl_read : tl_draw;
        if (s) fb = s->fbo;
    }
    real(target, fb);
}

static void wrap_glBindFramebuffer(uint32_t target, uint32_t fb) { bind_fb_redirect(g_bind_fb, target, fb); }

static void wrap_glBindFramebufferOES(uint32_t target, uint32_t fb) { bind_fb_redirect(g_bind_fb_oes, target, fb); }

typedef struct {
    void (*gen_fb)(int32_t n, void *names);
    void (*del_fb)(int32_t n, const void *names);
    void (*bind_fb)(uint32_t target, uint32_t fb);
    void (*gen_rb)(int32_t n, void *names);
    void (*del_rb)(int32_t n, const void *names);
    void (*bind_rb)(uint32_t target, uint32_t rb);
    void (*storage)(uint32_t target, uint32_t format, int32_t w, int32_t h);
    void (*attach)(uint32_t target, uint32_t attachment, uint32_t rbtarget, uint32_t rb);
    uint32_t (*status)(uint32_t target);
} FboFns;

static int32_t current_client_version(SaEGLDisplay dpy) {
    int32_t version = 1;
    SaEGLContext ctx = sa_egl.eglGetCurrentContext ? sa_egl.eglGetCurrentContext() : NULL;
    if (ctx && sa_egl.eglQueryContext) sa_egl.eglQueryContext(dpy, ctx, EGL_CONTEXT_CLIENT_VERSION, &version);
    return version;
}

/* The framebuffer object entry points for the current context: core for ES2+, OES_framebuffer_object for ES1. */
static bool fbo_fns(FboFns *f, SaEGLDisplay dpy) {
    bool oes = current_client_version(dpy) < 2 && sa_gl.glGenFramebuffersOES;
#define PICK(core, ext) (oes ? sa_gl.ext : sa_gl.core)
    f->gen_fb = PICK(glGenFramebuffers, glGenFramebuffersOES);
    f->del_fb = PICK(glDeleteFramebuffers, glDeleteFramebuffersOES);
    f->bind_fb = oes ? g_bind_fb_oes : g_bind_fb;
    f->gen_rb = PICK(glGenRenderbuffers, glGenRenderbuffersOES);
    f->del_rb = PICK(glDeleteRenderbuffers, glDeleteRenderbuffersOES);
    f->bind_rb = PICK(glBindRenderbuffer, glBindRenderbufferOES);
    f->storage = PICK(glRenderbufferStorage, glRenderbufferStorageOES);
    f->attach = PICK(glFramebufferRenderbuffer, glFramebufferRenderbufferOES);
    f->status = PICK(glCheckFramebufferStatus, glCheckFramebufferStatusOES);
#undef PICK
    return f->gen_fb && f->del_fb && f->bind_fb && f->gen_rb && f->del_rb && f->bind_rb && f->storage &&
           f->attach && f->status && sa_gl.glGetIntegerv;
}

static void fbo_delete(const FboFns *f, uint32_t fbo, const uint32_t rb[2]) {
    if (fbo) f->del_fb(1, &fbo);
    for (int i = 0; i < 2; i++)
        if (rb[i]) f->del_rb(1, &rb[i]);
}

/* Drops a surface's FBO: deleted now if its context is current, else when that context is next made current. */
static void fbo_release(SaSurf *s) {
    if (!s->fbo) return;
    uint32_t rb[2] = {s->color_rb, s->ds_rb};
    FboFns f;
    SaEGLContext cur = sa_egl.eglGetCurrentContext ? sa_egl.eglGetCurrentContext() : NULL;
    if (cur == s->fbo_ctx && fbo_fns(&f, s->dpy)) {
        fbo_delete(&f, s->fbo, rb);
    } else {
        FboGarbage *g = sa_calloc(1, sizeof *g);
        g->dpy = s->dpy;
        g->ctx = s->fbo_ctx;
        g->fbo = s->fbo;
        g->rb[0] = rb[0];
        g->rb[1] = rb[1];
        pthread_mutex_lock(&g_surfs_lock);
        g->next = g_garbage;
        g_garbage = g;
        pthread_mutex_unlock(&g_surfs_lock);
    }
    s->fbo = s->color_rb = s->ds_rb = 0;
    s->fbo_ctx = NULL;
}

/* Deletes the garbage of ctx, which is current. */
static void fbo_collect(SaEGLContext ctx, const FboFns *f) {
    pthread_mutex_lock(&g_surfs_lock);
    FboGarbage **pp = &g_garbage, *mine = NULL;
    while (*pp) {
        FboGarbage *g = *pp;
        if (g->ctx == ctx) {
            *pp = g->next;
            g->next = mine;
            mine = g;
        } else {
            pp = &g->next;
        }
    }
    pthread_mutex_unlock(&g_surfs_lock);
    while (mine) {
        FboGarbage *g = mine;
        mine = g->next;
        fbo_delete(f, g->fbo, g->rb);
        free(g);
    }
}

/* A destroyed context took its FBO names with it: forget them, so a new context at the same address starts over. */
static void fbo_context_destroyed(SaEGLContext ctx) {
    if (!ctx) return;
    pthread_mutex_lock(&g_surfs_lock);
    for (FboGarbage **pp = &g_garbage; *pp;) {
        FboGarbage *g = *pp;
        if (g->ctx == ctx) {
            *pp = g->next;
            free(g);
        } else {
            pp = &g->next;
        }
    }
    for (SaSurf *s = g_surfs; s; s = s->next) {
        if (s->fbo_ctx != ctx) continue;
        s->fbo = s->color_rb = s->ds_rb = 0;
        s->fbo_ctx = NULL;
    }
    pthread_mutex_unlock(&g_surfs_lock);
}

/* Sizes the renderbuffers to the surface, keeping the app's renderbuffer binding. */
static void fbo_storage(SaSurf *s, const FboFns *f) {
    int32_t prev = 0;
    sa_gl.glGetIntegerv(GL_RENDERBUFFER_BINDING, &prev);
    f->bind_rb(GL_RENDERBUFFER, s->color_rb);
    f->storage(GL_RENDERBUFFER, s->color_format, s->w, s->h);
    if (s->ds_rb) {
        f->bind_rb(GL_RENDERBUFFER, s->ds_rb);
        f->storage(GL_RENDERBUFFER, s->ds_format, s->w, s->h);
    }
    f->bind_rb(GL_RENDERBUFFER, (uint32_t)prev);
    s->fbo_w = s->w;
    s->fbo_h = s->h;
}

/* Creates the surface's FBO in ctx (current) with the config's colour, depth and stencil, and leaves it bound. */
static void fbo_create(SaSurf *s, SaEGLContext ctx, const FboFns *f) {
    int32_t depth = 0, stencil = 0;
    sa_egl.eglGetConfigAttrib(s->dpy, cfg_real(s->cfg), EGL_DEPTH_SIZE, &depth);
    sa_egl.eglGetConfigAttrib(s->dpy, cfg_real(s->cfg), EGL_STENCIL_SIZE, &stencil);
    s->color_format = cfg_noalpha(s->cfg) ? GL_RGB8_OES : GL_RGBA8_OES;
    s->ds_format = depth && stencil ? GL_DEPTH24_STENCIL8_OES
                   : depth > 16     ? GL_DEPTH_COMPONENT24_OES
                   : depth          ? GL_DEPTH_COMPONENT16
                   : stencil        ? GL_STENCIL_INDEX8
                                    : 0;
    f->gen_fb(1, &s->fbo);
    f->gen_rb(1, &s->color_rb);
    if (s->ds_format) f->gen_rb(1, &s->ds_rb);
    fbo_storage(s, f);
    f->bind_fb(GL_FRAMEBUFFER, s->fbo);
    f->attach(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, s->color_rb);
    if (depth) f->attach(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, s->ds_rb);
    if (stencil) f->attach(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, s->ds_rb);
    uint32_t st = f->status(GL_FRAMEBUFFER);
    if (st != GL_FRAMEBUFFER_COMPLETE)
        LOGW("surface framebuffer incomplete (0x%x): %dx%d depth %d stencil %d", st, s->w, s->h, depth, stencil);
    s->fbo_ctx = ctx;
}

/*
 * After eglMakeCurrent(dpy, NO_SURFACE, NO_SURFACE, ctx) succeeded for FBO surfaces draw and read: makes their
 * FBOs exist in ctx at the right size and binds draw's in place of the default framebuffer. prev_fbo is the FBO
 * of the thread's previous draw surface: an app binding that is 0 or that one meant the default framebuffer.
 */
static void fbo_make_current(SaEGLDisplay dpy, SaSurf *draw, SaSurf *read, SaEGLContext ctx, uint32_t prev_fbo) {
    FboFns f;
    if (!ctx || !fbo_fns(&f, dpy)) return;
    fbo_collect(ctx, &f);
    int32_t bound = 0;
    sa_gl.glGetIntegerv(GL_FRAMEBUFFER_BINDING, &bound);
    bool fresh = false;
    SaSurf *both[2] = {draw, read != draw ? read : NULL};
    for (int i = 0; i < 2; i++) {
        SaSurf *s = both[i];
        if (!s) continue;
        if (s->fbo && s->fbo_ctx != ctx) fbo_release(s);
        if (!s->fbo) {
            fbo_create(s, ctx, &f);
            if (s == draw) fresh = true;
        } else if (s->fbo_w != s->w || s->fbo_h != s->h) {
            fbo_storage(s, &f);
        }
    }
    if (!draw) {
        f.bind_fb(GL_FRAMEBUFFER, (uint32_t)bound); /* creating read's FBO bound it */
        return;
    }
    if (fresh || bound == 0 || (uint32_t)bound == prev_fbo || (uint32_t)bound == draw->fbo) {
        f.bind_fb(GL_FRAMEBUFFER, draw->fbo);
    } else {
        f.bind_fb(GL_FRAMEBUFFER, (uint32_t)bound);
    }
    /* a window surface sets the viewport when first current; a surfaceless context leaves it empty */
    if (fresh && sa_gl.glViewport && sa_gl.glScissor) {
        sa_gl.glViewport(0, 0, draw->w, draw->h);
        sa_gl.glScissor(0, 0, draw->w, draw->h);
    }
}

/* eglMakeCurrent for SaSurf surfaces (raw_* for driver surfaces native code passes in pbuffer mode). */
static bool surf_make_current(SaEGLDisplay dpy, SaSurf *draw, SaSurf *read, void *raw_draw, void *raw_read,
                              SaEGLContext ctx) {
    if (!sa_egl.eglMakeCurrent) return false;
    if (!g_fbo) {
        bool ok = sa_egl.eglMakeCurrent(dpy, draw ? draw->real : raw_draw, read ? read->real : raw_read, ctx);
        if (ok) {
            tl_draw = draw;
            tl_read = read;
        }
        return ok;
    }
    uint32_t prev_fbo = tl_draw ? tl_draw->fbo : 0;
    if (!sa_egl.eglMakeCurrent(dpy, NULL, NULL, ctx)) return false;
    tl_draw = ctx ? draw : NULL;
    tl_read = ctx ? read : NULL;
    fbo_make_current(dpy, tl_draw, tl_read, ctx, prev_fbo);
    return true;
}

/* Picks FBO surfaces when the display has no pbuffer configs. Call after a successful eglInitialize. */
static void detect_fbo(SaEGLDisplay dpy) {
    const char *force = getenv("SWITCHAPK_EGL_FBO");
    bool want = force && *force && strcmp(force, "0") != 0;
    if (!want && sa_egl.eglChooseConfig) {
        int32_t attribs[] = {EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_NONE};
        int32_t n = -1;
        want = sa_egl.eglChooseConfig(dpy, attribs, NULL, 0, &n) && n == 0;
    }
    if (want && !g_fbo) LOGI("EGL surfaces are framebuffer objects (%s)", force ? "SWITCHAPK_EGL_FBO" : "no pbuffers");
    g_fbo = want;
}

/* The EGL_SURFACE_TYPE asked of the driver for what the app asked: pbuffers back window surfaces, or anything. */
static int32_t driver_surface_type(int32_t v) {
    if (g_fbo) return 0;
    if (v != -1 /* EGL_DONT_CARE */ && (v & EGL_WINDOW_BIT)) v = (v & ~EGL_WINDOW_BIT) | EGL_PBUFFER_BIT;
    return v;
}

/* EGL_SURFACE_TYPE as the app sees it: our surfaces back windows (and with FBOs, pbuffers) on any config. */
static int32_t app_surface_type(int32_t v) {
    if (g_fbo) return v | EGL_WINDOW_BIT | EGL_PBUFFER_BIT;
    return (v & EGL_PBUFFER_BIT) ? v | EGL_WINDOW_BIT : v;
}

/* eglGetConfigAttrib as the app sees it (alpha-free variants, our surface types, Android-only attributes). */
static bool config_attrib(SaEGLDisplay dpy, SaEGLConfig cfg, int32_t attr, int32_t *v) {
    if (attr == EGL_RECORDABLE_ANDROID || attr == EGL_FRAMEBUFFER_TARGET_ANDROID) {
        *v = 1;
        return true;
    }
    if (!sa_egl.eglGetConfigAttrib) return false;
    if (cfg_noalpha(cfg) && attr == EGL_ALPHA_SIZE) {
        *v = 0;
        return true;
    }
    if (cfg_noalpha(cfg) && attr == EGL_BUFFER_SIZE) {
        int32_t buf = 0, alpha = 0;
        if (!sa_egl.eglGetConfigAttrib(dpy, cfg_real(cfg), EGL_BUFFER_SIZE, &buf) ||
            !sa_egl.eglGetConfigAttrib(dpy, cfg_real(cfg), EGL_ALPHA_SIZE, &alpha))
            return false;
        *v = buf - alpha;
        return true;
    }
    if (!sa_egl.eglGetConfigAttrib(dpy, cfg_real(cfg), attr, v)) return false;
    if (attr == EGL_SURFACE_TYPE) *v = app_surface_type(*v);
    if (attr == EGL_CONFIG_ID && cfg_noalpha(cfg)) *v += SA_CFG_ID_NOALPHA;
    return true;
}

/* Whether a (rewritten) attribute list can match an alpha-free variant. */
static bool wants_noalpha_variants(const int32_t *attribs) {
    if (!g_fbo) return false;
    for (int i = 0; attribs[i] != EGL_NONE && i < 256; i += 2) {
        int32_t k = attribs[i], v = attribs[i + 1];
        if (k == EGL_ALPHA_SIZE && v > 0) return false;
        if (k == EGL_BUFFER_SIZE && v > 24) return false;
        if (k == EGL_CONFIG_ID) return false;
    }
    return true;
}

/* eglChooseConfig over rewritten attributes, adding the alpha-free variants after the driver's configs. */
static bool choose_configs(SaEGLDisplay dpy, const int32_t *attribs, SaEGLConfig *out, int32_t size, int32_t *num) {
    if (!sa_egl.eglChooseConfig) return false;
    if (!wants_noalpha_variants(attribs)) return sa_egl.eglChooseConfig(dpy, attribs, out, size, num);
    int32_t n = 0;
    if (!sa_egl.eglChooseConfig(dpy, attribs, NULL, 0, &n)) return false;
    SaEGLConfig *all = sa_calloc((size_t)n * 2 + 1, sizeof *all);
    if (n > 0 && !sa_egl.eglChooseConfig(dpy, attribs, all, n, &n)) {
        free(all);
        return false;
    }
    int32_t total = n;
    for (int32_t i = 0; i < n; i++) {
        int32_t alpha = 0;
        if (sa_egl.eglGetConfigAttrib(dpy, all[i], EGL_ALPHA_SIZE, &alpha) && alpha > 0)
            all[total++] = (SaEGLConfig)((uintptr_t)all[i] | SA_CFG_NOALPHA);
    }
    if (out) {
        if (total > size) total = size > 0 ? size : 0;
        memcpy(out, all, sizeof *all * (size_t)total);
    }
    if (num) *num = total;
    free(all);
    return true;
}

/* eglQuerySurface for an FBO surface (no driver surface), width and height aside. */
static bool fbo_query_surface(SaSurf *s, int32_t attr, int32_t *v) {
    switch (attr) {
    case EGL_CONFIG_ID:
        return config_attrib(s->dpy, s->cfg, EGL_CONFIG_ID, v);
    case EGL_RENDER_BUFFER:
        *v = EGL_BACK_BUFFER;
        return true;
    case EGL_SWAP_BEHAVIOR:
        *v = EGL_BUFFER_DESTROYED;
        return true;
    default:
        return false;
    }
}

static void pbuffer_size(const int32_t *attribs, int *w, int *h) {
    *w = *h = 0;
    for (int i = 0; attribs && attribs[i] != EGL_NONE && i < 128; i += 2) {
        if (attribs[i] == EGL_WIDTH) *w = attribs[i + 1];
        if (attribs[i] == EGL_HEIGHT) *h = attribs[i + 1];
    }
}

/* Wraps a driver surface (or, with FBO surfaces, none) of size w x h; NULL if real is NULL in pbuffer mode. */
static SaSurf *surf_wrap(void *dpy, void *cfg, SaEGLSurface real, bool window, int w, int h, ANativeWindow *win) {
    if (!real && !g_fbo) return NULL;
    SaSurf *s = sa_calloc(1, sizeof *s);
    s->magic = SA_SURF_MAGIC;
    s->real = real;
    s->dpy = dpy;
    s->cfg = cfg;
    s->window = window;
    int32_t v = 0;
    s->w = real && sa_egl.eglQuerySurface && sa_egl.eglQuerySurface(dpy, real, EGL_WIDTH, &v) ? v : w;
    s->h = real && sa_egl.eglQuerySurface && sa_egl.eglQuerySurface(dpy, real, EGL_HEIGHT, &v) ? v : h;
    if (s->w < 1) s->w = 1;
    if (s->h < 1) s->h = 1;
    if (win) {
        s->anw = win;
        anw_acquire(win);
    }
    pthread_mutex_lock(&g_surfs_lock);
    s->next = g_surfs;
    g_surfs = s;
    pthread_mutex_unlock(&g_surfs_lock);
    return s;
}

/* eglDestroySurface for an SaSurf, freeing the record (a current one stops being current for us). */
static bool surf_destroy(SaSurf *s) {
    bool ok = s->real ? sa_egl.eglDestroySurface && sa_egl.eglDestroySurface(s->dpy, s->real) : true;
    fbo_release(s);
    if (s->anw) {
        anw_release(s->anw);
        s->anw = NULL;
    }
    if (tl_draw == s) tl_draw = NULL;
    if (tl_read == s) tl_read = NULL;
    pthread_mutex_lock(&g_surfs_lock);
    for (SaSurf **pp = &g_surfs; *pp; pp = &(*pp)->next) {
        if (*pp == s) {
            *pp = s->next;
            break;
        }
    }
    pthread_mutex_unlock(&g_surfs_lock);
    s->magic = 0;
    free(s);
    return ok;
}

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
    g_bind_fb = sa_gl.glBindFramebuffer;
    g_bind_fb_oes = sa_gl.glBindFramebufferOES;
    if (g_bind_fb) sa_gl.glBindFramebuffer = wrap_glBindFramebuffer;
    if (g_bind_fb_oes) sa_gl.glBindFramebufferOES = wrap_glBindFramebufferOES;
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
    if (ok) detect_fbo(P(A_LONG(0)));
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

/* Copies an attribute list, asking for what backs our window surfaces and dropping Android-only attributes. */
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
            v = driver_surface_type(v);
        }
        out[o++] = k;
        out[o++] = v;
    }
    if (!have_type) {
        out[o++] = EGL_SURFACE_TYPE;
        out[o++] = driver_surface_type(EGL_WINDOW_BIT);
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
    bool ok = choose_configs(dpy, attribs, tmp, tmp ? size : 0, &n);
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
    int32_t attribs[] = {EGL_SURFACE_TYPE, driver_surface_type(EGL_WINDOW_BIT), EGL_NONE};
    ArrayObject *configs = A_ARR(2);
    int32_t size = A_INT(3);
    ArrayObject *num = A_ARR(4);
    if (configs && size > configs->length) size = configs->length;
    SaEGLConfig *tmp = configs && size > 0 ? sa_calloc((size_t)size, sizeof(SaEGLConfig)) : NULL;
    int32_t n = 0;
    bool ok = choose_configs(dpy, attribs, tmp, tmp ? size : 0, &n);
    if (ok && tmp)
        for (int i = 0; i < n && i < size; i++) ARRAY_DATA(configs, int64_t)[i] = H(tmp[i]);
    if (num && num->length > 0) ARRAY_DATA(num, int32_t)[0] = ok ? n : 0;
    free(tmp);
    R_BOOL(ok);
}

NATIVE(EGLNative_nGetConfigAttrib) {
    UNUSED_ARGS();
    int32_t v = 0;
    bool ok = config_attrib(P(A_LONG(0)), P(A_LONG(2)), A_INT(4), &v);
    ArrayObject *out = A_ARR(5);
    if (ok && out && out->length > 0) ARRAY_DATA(out, int32_t)[0] = v;
    R_BOOL(ok);
}

NATIVE(EGLNative_nCreateContext) {
    UNUSED_ARGS();
    if (sa_egl.eglBindAPI) sa_egl.eglBindAPI(EGL_OPENGL_ES_API);
    int32_t none = EGL_NONE;
    int32_t *attribs = A_ARR(6) ? int_array(A_ARR(6)) : &none;
    SaEGLContext c = sa_egl.eglCreateContext(P(A_LONG(0)), cfg_real(P(A_LONG(2))), P(A_LONG(4)), attribs);
    R_LONG(H(c));
}

NATIVE(EGLNative_nDestroyContext) {
    UNUSED_ARGS();
    bool ok = sa_egl.eglDestroyContext(P(A_LONG(0)), P(A_LONG(2)));
    if (ok) fbo_context_destroyed(P(A_LONG(2)));
    R_BOOL(ok);
}

/* The driver surface behind a window surface: a pbuffer, or none with FBO surfaces. */
static SaEGLSurface make_pbuffer(SaEGLDisplay dpy, SaEGLConfig cfg, int w, int h) {
    if (g_fbo || !sa_egl.eglCreatePbufferSurface) return NULL;
    int32_t attribs[] = {EGL_WIDTH, w, EGL_HEIGHT, h, EGL_NONE};
    return sa_egl.eglCreatePbufferSurface(dpy, cfg, attribs);
}

/* An app pbuffer: the driver's, or with FBO surfaces just its size. */
static SaSurf *make_pbuffer_surface(SaEGLDisplay dpy, SaEGLConfig cfg, const int32_t *attribs) {
    int32_t none = EGL_NONE;
    const int32_t *list = attribs ? attribs : &none;
    int w, h;
    pbuffer_size(list, &w, &h);
    SaEGLSurface real = NULL;
    if (!g_fbo && sa_egl.eglCreatePbufferSurface) real = sa_egl.eglCreatePbufferSurface(dpy, cfg, list);
    return surf_wrap(dpy, cfg, real, false, w, h, NULL);
}

/* nCreateSurface(long dpy, long cfg, int[] attribs, boolean window, int w, int h): window surfaces ignore attribs. */
NATIVE(EGLNative_nCreateSurface) {
    UNUSED_ARGS();
    SaEGLDisplay dpy = P(A_LONG(0));
    SaEGLConfig cfg = P(A_LONG(2));
    ArrayObject *attribs = A_ARR(4);
    bool window = A_BOOL(5);
    int w = A_INT(6) > 0 ? A_INT(6) : 1, h = A_INT(7) > 0 ? A_INT(7) : 1;
    SaSurf *s = window ? surf_wrap(dpy, cfg, make_pbuffer(dpy, cfg, w, h), true, w, h, NULL)
                       : make_pbuffer_surface(dpy, cfg, attribs ? int_array(attribs) : NULL);
    R_LONG(H(s));
}

NATIVE(EGLNative_nDestroySurface) {
    UNUSED_ARGS();
    SaSurf *s = P(A_LONG(2));
    R_BOOL(s && surf_destroy(s));
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
    if (g_fbo) {
        /* resized now if current in the FBO's context, else when next made current */
        s->w = w;
        s->h = h;
        FboFns f;
        if (s->fbo && sa_egl.eglGetCurrentContext && sa_egl.eglGetCurrentContext() == s->fbo_ctx &&
            fbo_fns(&f, s->dpy))
            fbo_storage(s, &f);
        R_BOOL(true);
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
    R_BOOL(surf_make_current(P(A_LONG(0)), P(A_LONG(2)), P(A_LONG(4)), NULL, NULL, P(A_LONG(6))));
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
    } else if (!s->real) {
        ok = fbo_query_surface(s, attr, &v);
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
    if (!s->real) {
        /* an FBO pbuffer: nothing to present */
        R_BOOL(true);
        return;
    }
    vm_gil_release(t);
    bool ok = sa_egl.eglSwapBuffers(P(A_LONG(0)), s->real);
    vm_gil_acquire(t);
    R_BOOL(ok);
}

static inline uint32_t unpremul(uint32_t a, uint32_t c) { return a ? (c * 255 + a / 2) / a : 0; }

/* Reads the current draw surface into out as unpremultiplied ARGB, top row first. Caller holds the GIL. */
static bool read_surf_argb(VMThread *t, SaSurf *s, uint32_t *out, bool opaque) {
    if (!s || !out || !sa_gl.glReadPixels || tl_draw != s || s->w < 1 || s->h < 1) return false;
    if (g_fbo && !s->fbo) return false;
    int w = s->w, h = s->h;
    uint8_t *rgba = malloc((size_t)w * (size_t)h * 4);
    if (!rgba) return false;
    int32_t version = 1;
    if (sa_egl.eglQueryContext && sa_egl.eglGetCurrentContext)
        sa_egl.eglQueryContext(s->dpy, sa_egl.eglGetCurrentContext(), EGL_CONTEXT_CLIENT_VERSION, &version);
    vm_gil_release(t);
    int32_t fbo = 0, pack = 4, pbo = 0;
    /* read the surface's framebuffer: the default one, or its FBO */
    void (*bind)(uint32_t, uint32_t) = version >= 2 || !g_bind_fb_oes ? g_bind_fb : g_bind_fb_oes;
    uint32_t want = g_fbo ? s->fbo : 0;
    bool rebind = false;
    if ((version >= 2 || g_fbo) && bind) {
        sa_gl.glGetIntegerv(GL_FRAMEBUFFER_BINDING, &fbo);
        rebind = (uint32_t)fbo != want;
        if (rebind) bind(GL_FRAMEBUFFER, want);
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
    if (rebind) bind(GL_FRAMEBUFFER, (uint32_t)fbo);
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
            row[x] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }
    vm_gil_acquire(t);
    free(rgba);
    return true;
}

/*
 * nReadWindow(long surf, int[] dst, boolean opaque): reads the current frame of a window surface (which must be
 * the current draw surface) into dst as unpremultiplied ARGB rows, top row first.
 */
NATIVE(EGLNative_nReadWindow) {
    UNUSED_ARGS();
    SaSurf *s = P(A_LONG(0));
    ArrayObject *dst = A_ARR(2);
    bool opaque = A_BOOL(3);
    if (!s || !dst || dst->length < s->w * s->h) {
        R_BOOL(false);
        return;
    }
    R_BOOL(read_surf_argb(t, s, ARRAY_DATA(dst, uint32_t), opaque));
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
    R_BOOL(g_fbo || (sa_egl.eglSwapInterval && sa_egl.eglSwapInterval(P(A_LONG(0)), A_INT(2))));
}

NATIVE(EGLNative_nSurfaceAttrib) {
    UNUSED_ARGS();
    SaSurf *s = P(A_LONG(2));
    if (s && !s->real) {
        R_BOOL(true); /* FBO surfaces ignore surface attributes */
        return;
    }
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

/* ---- native EGL (NDK eglCreateWindowSurface takes an ANativeWindow) ----------------------------------------- */

static _Thread_local int32_t tl_egl_error;

static void set_egl_error(int32_t err) { tl_egl_error = err; }

static int32_t wrap_eglGetError(void) {
    int32_t err = tl_egl_error;
    tl_egl_error = 0;
    if (err) {
        if (sa_egl.eglGetError) sa_egl.eglGetError();
        return err;
    }
    return sa_egl.eglGetError ? sa_egl.eglGetError() : EGL_NOT_INITIALIZED;
}

static void *wrap_eglGetDisplay(void *native_display) {
    SA_UNUSED(native_display);
    SaEGLDisplay d = NULL;
#ifndef __SWITCH__
    if (sa_egl.eglGetPlatformDisplayEXT) d = sa_egl.eglGetPlatformDisplayEXT(EGL_PLATFORM_SURFACELESS_MESA, NULL, NULL);
#endif
    if (!d && sa_egl.eglGetDisplay) d = sa_egl.eglGetDisplay(NULL);
    return d;
}

static uint32_t wrap_eglInitialize(void *dpy, int32_t *major, int32_t *minor) {
    uint32_t ok = sa_egl.eglInitialize ? sa_egl.eglInitialize(dpy, major, minor) : 0;
    if (ok) detect_fbo(dpy);
    return ok;
}

static uint32_t wrap_eglTerminate(void *dpy) { return sa_egl.eglTerminate ? sa_egl.eglTerminate(dpy) : 0; }

static const char *wrap_eglQueryString(void *dpy, int32_t name) {
    return sa_egl.eglQueryString ? sa_egl.eglQueryString(dpy, name) : NULL;
}

static uint32_t wrap_eglBindAPI(uint32_t api) { return sa_egl.eglBindAPI ? sa_egl.eglBindAPI(api) : 0; }

static uint32_t wrap_eglSwapInterval(void *dpy, int32_t interval) {
    if (g_fbo) return 1; /* frames are posted through the buffer queue; the driver has no surface to pace */
    return sa_egl.eglSwapInterval ? sa_egl.eglSwapInterval(dpy, interval) : 0;
}

/* Copies an attribute list, asking for what backs our window surfaces and dropping Android-only attributes. */
static int32_t *native_config_attribs(const int32_t *src) {
    int n = 0;
    if (src)
        while (src[n] != EGL_NONE && n < 128) n++;
    int32_t *out = sa_malloc(sizeof(int32_t) * (size_t)(n + 4));
    int o = 0;
    bool have_type = false;
    for (int i = 0; i + 1 < n; i += 2) {
        int32_t k = src[i], v = src[i + 1];
        if (k == EGL_RECORDABLE_ANDROID || k == EGL_FRAMEBUFFER_TARGET_ANDROID) continue;
        if (k == EGL_SURFACE_TYPE) {
            have_type = true;
            v = driver_surface_type(v);
        }
        out[o++] = k;
        out[o++] = v;
    }
    if (!have_type) {
        out[o++] = EGL_SURFACE_TYPE;
        out[o++] = driver_surface_type(EGL_WINDOW_BIT);
    }
    out[o] = EGL_NONE;
    return out;
}

static uint32_t wrap_eglChooseConfig(void *dpy, const int32_t *attribs, void **configs, int32_t size, int32_t *num) {
    int32_t *rewritten = native_config_attribs(attribs);
    uint32_t ok = 0;
    ok = choose_configs(dpy, rewritten, (SaEGLConfig *)configs, size, num);
    free(rewritten);
    return ok;
}

static uint32_t wrap_eglGetConfigAttrib(void *dpy, void *config, int32_t attr, int32_t *value) {
    return value && config_attrib(dpy, config, attr, value);
}

static void *wrap_eglCreateWindowSurface(void *dpy, void *config, void *win, const int32_t *attribs) {
    SA_UNUSED(attribs);
    if (!anw_is(win)) {
        set_egl_error(EGL_BAD_NATIVE_WINDOW);
        return NULL;
    }
    int w = anw_width(win);
    int h = anw_height(win);
    if (w < 1) w = 1;
    if (h < 1) h = 1;
    return surf_wrap(dpy, config, make_pbuffer(dpy, config, w, h), true, w, h, win);
}

static void *wrap_eglCreatePbufferSurface(void *dpy, void *config, const int32_t *attribs) {
    return make_pbuffer_surface(dpy, config, attribs);
}

static uint32_t wrap_eglDestroySurface(void *dpy, void *surface) {
    SaSurf *s = as_surf(surface);
    if (!s) return sa_egl.eglDestroySurface ? sa_egl.eglDestroySurface(dpy, surface) : 0;
    return surf_destroy(s);
}

static uint32_t wrap_eglQuerySurface(void *dpy, void *surface, int32_t attr, int32_t *value) {
    SaSurf *s = as_surf(surface);
    if (s && (attr == EGL_WIDTH || attr == EGL_HEIGHT)) {
        if (value) *value = attr == EGL_WIDTH ? s->w : s->h;
        return 1;
    }
    if (s && !s->real) return value && fbo_query_surface(s, attr, value);
    void *real = s ? s->real : surface;
    return sa_egl.eglQuerySurface ? sa_egl.eglQuerySurface(dpy, real, attr, value) : 0;
}

static void *wrap_eglCreateContext(void *dpy, void *config, void *share, const int32_t *attribs) {
    if (sa_egl.eglBindAPI) sa_egl.eglBindAPI(EGL_OPENGL_ES_API);
    return sa_egl.eglCreateContext ? sa_egl.eglCreateContext(dpy, cfg_real(config), share, attribs) : NULL;
}

static uint32_t wrap_eglDestroyContext(void *dpy, void *ctx) {
    uint32_t ok = sa_egl.eglDestroyContext ? sa_egl.eglDestroyContext(dpy, ctx) : 0;
    if (ok) fbo_context_destroyed(ctx);
    return ok;
}

static uint32_t wrap_eglMakeCurrent(void *dpy, void *draw, void *read, void *ctx) {
    return surf_make_current(dpy, as_surf(draw), as_surf(read), draw, read, ctx);
}

static void *wrap_eglGetCurrentSurface(int32_t which) { return which == EGL_READ ? (void *)tl_read : (void *)tl_draw; }

static uint32_t wrap_eglSwapBuffers(void *dpy, void *surface) {
    SaSurf *s = as_surf(surface);
    if (!s) return sa_egl.eglSwapBuffers ? sa_egl.eglSwapBuffers(dpy, surface) : 0;
    if (!s->anw) return !s->real || (sa_egl.eglSwapBuffers && sa_egl.eglSwapBuffers(dpy, s->real));
    VMThread *t = vm_current_thread();
    if (!t || !t->has_gil) {
        LOGE("posting a native window frame requires the VM thread");
        set_egl_error(EGL_BAD_ACCESS);
        return 0;
    }
    size_t n = (size_t)s->w * (size_t)s->h;
    uint32_t *argb = malloc(n * 4);
    if (!argb) {
        set_egl_error(EGL_BAD_ALLOC);
        return 0;
    }
    bool ok = read_surf_argb(t, s, argb, anw_opaque(s->anw));
    if (ok) ok = anw_post_argb(s->anw, argb, s->w, s->h);
    free(argb);
    if (!ok) {
        set_egl_error(EGL_BAD_SURFACE);
        return 0;
    }
    return 1;
}

static void *wrap_eglGetProcAddress(const char *name);

static const struct {
    const char *name;
    void *fn;
} g_egl_wrap[] = {
    {"eglGetError", wrap_eglGetError},
    {"eglGetDisplay", wrap_eglGetDisplay},
    {"eglInitialize", wrap_eglInitialize},
    {"eglTerminate", wrap_eglTerminate},
    {"eglQueryString", wrap_eglQueryString},
    {"eglBindAPI", wrap_eglBindAPI},
    {"eglSwapInterval", wrap_eglSwapInterval},
    {"eglChooseConfig", wrap_eglChooseConfig},
    {"eglGetConfigAttrib", wrap_eglGetConfigAttrib},
    {"eglCreateWindowSurface", wrap_eglCreateWindowSurface},
    {"eglCreatePbufferSurface", wrap_eglCreatePbufferSurface},
    {"eglDestroySurface", wrap_eglDestroySurface},
    {"eglQuerySurface", wrap_eglQuerySurface},
    {"eglCreateContext", wrap_eglCreateContext},
    {"eglDestroyContext", wrap_eglDestroyContext},
    {"eglMakeCurrent", wrap_eglMakeCurrent},
    {"eglGetCurrentSurface", wrap_eglGetCurrentSurface},
    {"eglSwapBuffers", wrap_eglSwapBuffers},
    {"eglGetProcAddress", wrap_eglGetProcAddress},
    {"glBindFramebuffer", wrap_glBindFramebuffer},
    {"glBindFramebufferOES", wrap_glBindFramebufferOES},
};

void *sa_egl_native_proc(const char *name) {
    if (!name) return NULL;
    for (size_t i = 0; i < SA_ARRAY_LEN(g_egl_wrap); i++)
        if (strcmp(g_egl_wrap[i].name, name) == 0) return g_egl_wrap[i].fn;
    return NULL;
}

static void *wrap_eglGetProcAddress(const char *name) {
    void *ours = sa_egl_native_proc(name);
    if (ours) return ours;
    return sa_egl.eglGetProcAddress ? sa_egl.eglGetProcAddress(name) : NULL;
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
