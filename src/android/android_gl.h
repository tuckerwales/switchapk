/*
 * OpenGL ES and EGL natives (WS8): the entry point tables and helpers shared
 * by the generated GLES bindings (android_gles_gen.c), the hand-written ones
 * (android_gles_special.c) and EGL/GLUtils (android_gl.c).
 *
 * Every GL ES and EGL function is reached through a function pointer. The
 * host loads libEGL/libGLESv2/libGLESv1_CM with dlopen when the app first
 * asks for an EGL display, so the binary does not link against GL and a
 * machine without Mesa just reports EGL_NO_DISPLAY. The Switch links Mesa
 * statically and fills the same tables.
 */
#ifndef SWITCHAPK_ANDROID_GL_H
#define SWITCHAPK_ANDROID_GL_H

#include "android.h"
#include "gles_funcs.h"

#define SA_GL_FIELD(ret, name, params) ret(*name) params;
typedef struct {
    SA_GL_FUNCS(SA_GL_FIELD)
} SaGl;
#undef SA_GL_FIELD

/* GL ES entry points; a NULL member means the function is unavailable. */
extern SaGl sa_gl;

/* EGL 1.4 entry points used by the bindings (opaque handle types). */
typedef void *SaEGLDisplay;
typedef void *SaEGLConfig;
typedef void *SaEGLSurface;
typedef void *SaEGLContext;

#define SA_EGL_FUNCS(X)                                                                                           \
    X(int32_t, eglGetError, (void))                                                                               \
    X(SaEGLDisplay, eglGetDisplay, (void *native_display))                                                       \
    X(SaEGLDisplay, eglGetPlatformDisplayEXT, (uint32_t platform, void *native_display, const int32_t *attribs))  \
    X(uint32_t, eglInitialize, (SaEGLDisplay dpy, int32_t * major, int32_t * minor))                             \
    X(uint32_t, eglTerminate, (SaEGLDisplay dpy))                                                                 \
    X(const char *, eglQueryString, (SaEGLDisplay dpy, int32_t name))                                             \
    X(uint32_t, eglGetConfigs, (SaEGLDisplay dpy, SaEGLConfig * configs, int32_t size, int32_t * num))            \
    X(uint32_t, eglChooseConfig,                                                                                  \
      (SaEGLDisplay dpy, const int32_t *attribs, SaEGLConfig *configs, int32_t size, int32_t *num))               \
    X(uint32_t, eglGetConfigAttrib, (SaEGLDisplay dpy, SaEGLConfig config, int32_t attr, int32_t * value))       \
    X(SaEGLSurface, eglCreatePbufferSurface, (SaEGLDisplay dpy, SaEGLConfig config, const int32_t *attribs))     \
    X(SaEGLSurface, eglCreateWindowSurface,                                                                       \
      (SaEGLDisplay dpy, SaEGLConfig config, void *win, const int32_t *attribs))                                 \
    X(uint32_t, eglDestroySurface, (SaEGLDisplay dpy, SaEGLSurface surface))                                     \
    X(uint32_t, eglQuerySurface, (SaEGLDisplay dpy, SaEGLSurface surface, int32_t attr, int32_t * value))        \
    X(uint32_t, eglBindAPI, (uint32_t api))                                                                       \
    X(uint32_t, eglQueryAPI, (void))                                                                              \
    X(uint32_t, eglWaitClient, (void))                                                                            \
    X(uint32_t, eglReleaseThread, (void))                                                                         \
    X(uint32_t, eglSurfaceAttrib, (SaEGLDisplay dpy, SaEGLSurface surface, int32_t attr, int32_t value))         \
    X(uint32_t, eglSwapInterval, (SaEGLDisplay dpy, int32_t interval))                                           \
    X(SaEGLContext, eglCreateContext,                                                                             \
      (SaEGLDisplay dpy, SaEGLConfig config, SaEGLContext share, const int32_t *attribs))                         \
    X(uint32_t, eglDestroyContext, (SaEGLDisplay dpy, SaEGLContext ctx))                                         \
    X(uint32_t, eglMakeCurrent, (SaEGLDisplay dpy, SaEGLSurface draw, SaEGLSurface read, SaEGLContext ctx))      \
    X(SaEGLContext, eglGetCurrentContext, (void))                                                                 \
    X(SaEGLSurface, eglGetCurrentSurface, (int32_t which))                                                        \
    X(SaEGLDisplay, eglGetCurrentDisplay, (void))                                                                 \
    X(uint32_t, eglQueryContext, (SaEGLDisplay dpy, SaEGLContext ctx, int32_t attr, int32_t * value))            \
    X(uint32_t, eglWaitGL, (void))                                                                                \
    X(uint32_t, eglWaitNative, (int32_t engine))                                                                  \
    X(uint32_t, eglSwapBuffers, (SaEGLDisplay dpy, SaEGLSurface surface))                                        \
    X(void *, eglGetProcAddress, (const char *name))

#define SA_EGL_FIELD(ret, name, params) ret(*name) params;
typedef struct {
    SA_EGL_FUNCS(SA_EGL_FIELD)
} SaEgl;
#undef SA_EGL_FIELD

extern SaEgl sa_egl;

/* Loads EGL and the GL ES entry points once. Returns false if EGL is unavailable. */
bool sa_gl_load(void);
/* Resolves any GL ES or EGL entry point by name (for the NDK shim). */
void *sa_gl_proc(const char *name);

/* Address of element 0 of an array argument plus offset, with AOSP's checks (IllegalArgumentException). */
static inline void *gles_array(VMThread *t, ArrayObject *a, int32_t offset, int elem, const char *what) {
    if (!a) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "%s == null", what);
        return NULL;
    }
    if (offset < 0) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "offset < 0");
        return NULL;
    }
    if (offset > a->length) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "length - offset < 0");
        return NULL;
    }
    return (uint8_t *)a->data + (size_t)offset * (size_t)elem;
}

/* Address of a java.nio buffer's current position (NULL for a null buffer). */
void *gles_buffer(Object *buf);
/* Remaining bytes of a java.nio buffer (0 for null). */
int32_t gles_buffer_remaining_bytes(Object *buf);

/* Logs once that a GL function could not be resolved (no context yet, or not exported). */
void gles_missing(const char *name);
/* Logs once that a binding is not implemented. */
void gles_unsupported(const char *what);

void android_gles_gen_register(void);
void android_gles_special_register(void);

#endif
