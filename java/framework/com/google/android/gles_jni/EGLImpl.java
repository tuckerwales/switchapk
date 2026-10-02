package com.google.android.gles_jni;

import android.opengl.EGLNative;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/** The EGL10/EGL11 implementation EGLContext.getEGL() returns, over android.opengl.EGLNative. */
public class EGLImpl implements EGL10, EGL11 {
    public EGLImpl() {
    }

    private static long h(EGLDisplay d) {
        return d instanceof EGLDisplayImpl ? ((EGLDisplayImpl) d).mHandle : 0;
    }

    private static long h(EGLConfig c) {
        return c instanceof EGLConfigImpl ? ((EGLConfigImpl) c).mHandle : 0;
    }

    private static long h(EGLContext c) {
        return c instanceof EGLContextImpl ? ((EGLContextImpl) c).mHandle : 0;
    }

    private static long h(EGLSurface s) {
        return s instanceof EGLSurfaceImpl ? ((EGLSurfaceImpl) s).mHandle : 0;
    }

    private static int[] checkAttribs(int[] list) {
        if (list == null) return new int[] {EGL_NONE};
        for (int i = 0; i < list.length; i += 2) {
            if (list[i] == EGL_NONE) return list;
        }
        throw new IllegalArgumentException("attrib_list must contain EGL_NONE!");
    }

    private static void checkOut(int[] a, int need, String what) {
        if (a == null) throw new IllegalArgumentException(what + " == null");
        if (a.length < need) throw new IllegalArgumentException(what + ".length < " + need);
    }

    public boolean eglChooseConfig(EGLDisplay display, int[] attrib_list, EGLConfig[] configs, int config_size,
            int[] num_config) {
        if (configs != null && configs.length < config_size) {
            throw new IllegalArgumentException("configs.length < config_size");
        }
        if (num_config != null) checkOut(num_config, 1, "num_config");
        long[] handles = configs != null ? new long[config_size] : null;
        int[] n = new int[1];
        boolean ok = EGLNative.nChooseConfig(h(display), checkAttribs(attrib_list), handles,
                configs != null ? config_size : 0, n);
        if (ok) {
            if (num_config != null) num_config[0] = n[0];
            if (configs != null) {
                for (int i = 0; i < n[0] && i < config_size; i++) configs[i] = new EGLConfigImpl(handles[i]);
            }
        }
        return ok;
    }

    public boolean eglCopyBuffers(EGLDisplay display, EGLSurface surface, Object native_pixmap) {
        return false;
    }

    public EGLContext eglCreateContext(EGLDisplay display, EGLConfig config, EGLContext share_context,
            int[] attrib_list) {
        long ctx = EGLNative.nCreateContext(h(display), h(config), h(share_context), checkAttribs(attrib_list));
        return ctx == 0 ? EGL10.EGL_NO_CONTEXT : new EGLContextImpl(ctx);
    }

    public EGLSurface eglCreatePbufferSurface(EGLDisplay display, EGLConfig config, int[] attrib_list) {
        long s = EGLNative.createPbufferSurface(h(display), h(config), checkAttribs(attrib_list));
        return s == 0 ? EGL10.EGL_NO_SURFACE : new EGLSurfaceImpl(s);
    }

    @Deprecated
    public EGLSurface eglCreatePixmapSurface(EGLDisplay display, EGLConfig config, Object native_pixmap,
            int[] attrib_list) {
        return EGL10.EGL_NO_SURFACE;
    }

    public EGLSurface eglCreateWindowSurface(EGLDisplay display, EGLConfig config, Object native_window,
            int[] attrib_list) {
        checkAttribs(attrib_list);
        long s = EGLNative.createWindowSurface(h(display), h(config), native_window);
        return s == 0 ? EGL10.EGL_NO_SURFACE : new EGLSurfaceImpl(s);
    }

    public boolean eglDestroyContext(EGLDisplay display, EGLContext context) {
        return EGLNative.nDestroyContext(h(display), h(context));
    }

    public boolean eglDestroySurface(EGLDisplay display, EGLSurface surface) {
        return EGLNative.destroySurface(h(display), h(surface));
    }

    public boolean eglGetConfigAttrib(EGLDisplay display, EGLConfig config, int attribute, int[] value) {
        checkOut(value, 1, "value");
        return EGLNative.nGetConfigAttrib(h(display), h(config), attribute, value);
    }

    public boolean eglGetConfigs(EGLDisplay display, EGLConfig[] configs, int config_size, int[] num_config) {
        if (configs != null && configs.length < config_size) {
            throw new IllegalArgumentException("configs.length < config_size");
        }
        checkOut(num_config, 1, "num_config");
        long[] handles = configs != null ? new long[config_size] : null;
        boolean ok = EGLNative.nGetConfigs(h(display), handles, configs != null ? config_size : 0, num_config);
        if (ok && configs != null) {
            for (int i = 0; i < num_config[0] && i < config_size; i++) configs[i] = new EGLConfigImpl(handles[i]);
        }
        return ok;
    }

    public EGLContext eglGetCurrentContext() {
        long c = EGLNative.nGetCurrentContext();
        return c == 0 ? EGL10.EGL_NO_CONTEXT : new EGLContextImpl(c);
    }

    public EGLDisplay eglGetCurrentDisplay() {
        long d = EGLNative.nGetCurrentDisplay();
        return d == 0 ? EGL10.EGL_NO_DISPLAY : new EGLDisplayImpl(d);
    }

    public EGLSurface eglGetCurrentSurface(int readdraw) {
        long s = EGLNative.nGetCurrentSurface(readdraw);
        return s == 0 ? EGL10.EGL_NO_SURFACE : new EGLSurfaceImpl(s);
    }

    public EGLDisplay eglGetDisplay(Object native_display) {
        long d = EGLNative.nGetDisplay();
        return d == 0 ? EGL10.EGL_NO_DISPLAY : new EGLDisplayImpl(d);
    }

    public int eglGetError() {
        return EGLNative.getError();
    }

    public boolean eglInitialize(EGLDisplay display, int[] major_minor) {
        if (major_minor != null && major_minor.length < 2) {
            throw new IllegalArgumentException("major_minor.length < 2");
        }
        return EGLNative.nInitialize(h(display), major_minor);
    }

    public boolean eglMakeCurrent(EGLDisplay display, EGLSurface draw, EGLSurface read, EGLContext context) {
        return EGLNative.nMakeCurrent(h(display), h(draw), h(read), h(context));
    }

    public boolean eglQueryContext(EGLDisplay display, EGLContext context, int attribute, int[] value) {
        checkOut(value, 1, "value");
        return EGLNative.nQueryContext(h(display), h(context), attribute, value);
    }

    public String eglQueryString(EGLDisplay display, int name) {
        return EGLNative.nQueryString(h(display), name);
    }

    public boolean eglQuerySurface(EGLDisplay display, EGLSurface surface, int attribute, int[] value) {
        checkOut(value, 1, "value");
        return EGLNative.nQuerySurface(h(display), h(surface), attribute, value);
    }

    public boolean eglSwapBuffers(EGLDisplay display, EGLSurface surface) {
        return EGLNative.swapBuffers(h(display), h(surface));
    }

    public boolean eglTerminate(EGLDisplay display) {
        return EGLNative.nTerminate(h(display));
    }

    public boolean eglWaitGL() {
        return EGLNative.nWaitGL();
    }

    public boolean eglWaitNative(int engine, Object bindTarget) {
        return EGLNative.nWaitNative(engine);
    }

    /** framework-internal (AOSP has it hidden): the swap interval for GLSurfaceView-like helpers. */
    public static boolean swapInterval(EGLDisplay display, int interval) {
        return EGLNative.nSwapInterval(h(display), interval);
    }
}
