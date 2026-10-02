/*
 * NativeActivity sample (WS9). Clears an EGL window surface to red, then
 * paints a gold rectangle through ANativeWindow_lock so one frame shows
 * both paths. A failure fills the window with magenta.
 */
#include "ndk_android.h"

int __android_log_print(int prio, const char *tag, const char *fmt, ...);

#define EGL_NONE 0x3038
#define EGL_SUCCESS 0x3000
#define EGL_WIDTH 0x3057
#define EGL_HEIGHT 0x3056
#define EGL_SURFACE_TYPE 0x3033
#define EGL_WINDOW_BIT 0x0004
#define EGL_RED_SIZE 0x3024
#define EGL_GREEN_SIZE 0x3023
#define EGL_BLUE_SIZE 0x3022
#define EGL_ALPHA_SIZE 0x3021
#define EGL_RENDERABLE_TYPE 0x3040
#define EGL_OPENGL_ES2_BIT 0x0004
#define EGL_CONTEXT_CLIENT_VERSION 0x3098
#define GL_COLOR_BUFFER_BIT 0x00004000

void *eglGetDisplay(void *native_display);
unsigned int eglInitialize(void *dpy, int *major, int *minor);
unsigned int eglChooseConfig(void *dpy, const int *attribs, void **configs, int size, int *num);
void *eglCreateContext(void *dpy, void *config, void *share, const int *attribs);
void *eglCreateWindowSurface(void *dpy, void *config, void *win, const int *attribs);
unsigned int eglMakeCurrent(void *dpy, void *draw, void *read, void *ctx);
unsigned int eglSwapBuffers(void *dpy, void *surface);
unsigned int eglDestroySurface(void *dpy, void *surface);
unsigned int eglDestroyContext(void *dpy, void *ctx);
int eglGetError(void);
void glClearColor(float r, float g, float b, float a);
void glClear(unsigned int mask);

static void *g_dpy, *g_surf, *g_ctx;

static void fill(ANativeWindow *win, unsigned char r, unsigned char g, unsigned char b) {
    ANativeWindow_Buffer buf;
    if (ANativeWindow_lock(win, &buf, 0) != 0) return;
    unsigned char *p = buf.bits;
    int n = buf.stride * buf.height;
    for (int i = 0; i < n; i++, p += 4) {
        p[0] = r;
        p[1] = g;
        p[2] = b;
        p[3] = 255;
    }
    ANativeWindow_unlockAndPost(win);
}

static void fail(ANativeWindow *win, const char *why) {
    __android_log_print(6, "nativeact", "%s (eglGetError=0x%x)", why, eglGetError());
    fill(win, 255, 0, 255);
}

static void draw(ANativeWindow *win) {
    int w = ANativeWindow_getWidth(win);
    int h = ANativeWindow_getHeight(win);
    if (!g_dpy) {
        g_dpy = eglGetDisplay(0);
        if (!g_dpy || !eglInitialize(g_dpy, 0, 0)) {
            fail(win, "eglInitialize failed");
            g_dpy = 0;
            return;
        }
        const int cfg_attr[] = {
            EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_NONE,
        };
        void *cfg = 0;
        int n = 0;
        if (!eglChooseConfig(g_dpy, cfg_attr, &cfg, 1, &n) || n < 1 || !cfg) {
            fail(win, "eglChooseConfig failed");
            return;
        }
        const int ctx_attr[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
        g_ctx = eglCreateContext(g_dpy, cfg, 0, ctx_attr);
        g_surf = eglCreateWindowSurface(g_dpy, cfg, win, 0);
        if (!g_ctx || !g_surf || !eglMakeCurrent(g_dpy, g_surf, g_surf, g_ctx)) {
            fail(win, "egl window surface failed");
            return;
        }
    }
    glClearColor(1.f, 0.f, 0.f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT);
    if (!eglSwapBuffers(g_dpy, g_surf)) {
        fail(win, "eglSwapBuffers failed");
        return;
    }
    ANativeWindow_Buffer buf;
    if (ANativeWindow_lock(win, &buf, 0) != 0) {
        fail(win, "ANativeWindow_lock failed");
        return;
    }
    int x0 = buf.width / 4;
    int x1 = buf.width * 3 / 4;
    int y0 = buf.height / 4;
    int y1 = buf.height * 3 / 4;
    for (int y = y0; y < y1; y++) {
        unsigned char *row = (unsigned char *)buf.bits + (size_t)y * (size_t)buf.stride * 4;
        for (int x = x0; x < x1; x++) {
            unsigned char *p = row + x * 4;
            p[0] = 255;
            p[1] = 193;
            p[2] = 7;
            p[3] = 255;
        }
    }
    if (ANativeWindow_unlockAndPost(win) != 0) __android_log_print(6, "nativeact", "unlockAndPost failed");
    (void)w;
    (void)h;
}

static void on_window_created(ANativeActivity *activity, ANativeWindow *window) {
    (void)activity;
    draw(window);
}

static void on_window_destroyed(ANativeActivity *activity, ANativeWindow *window) {
    (void)activity;
    (void)window;
    if (!g_dpy) return;
    eglMakeCurrent(g_dpy, 0, 0, 0);
    if (g_surf) eglDestroySurface(g_dpy, g_surf);
    if (g_ctx) eglDestroyContext(g_dpy, g_ctx);
    g_surf = 0;
    g_ctx = 0;
    g_dpy = 0;
}

void ANativeActivity_onCreate(ANativeActivity *activity, void *saved_state, size_t saved_state_size) {
    (void)saved_state;
    (void)saved_state_size;
    activity->callbacks->onNativeWindowCreated = on_window_created;
    activity->callbacks->onNativeWindowDestroyed = on_window_destroyed;
}
