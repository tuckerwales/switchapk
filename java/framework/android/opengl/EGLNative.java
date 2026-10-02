package android.opengl;

import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.HashMap;
import java.util.HashSet;

/**
 * framework-internal. The EGL implementation shared by {@link EGL14} and the
 * javax.microedition.khronos.egl EGL10 implementation: thin natives over the
 * real EGL (src/android/android_gl.c) plus window surface handling.
 *
 * A window surface is an offscreen pbuffer the size of its Surface's buffer
 * queue. eglSwapBuffers reads the frame back into the queue's back buffer and
 * posts it, so SurfaceView/TextureView consume GL frames like canvas frames.
 * When the queue changes size the pbuffer follows after the swap (Android
 * resizes window surfaces at the next dequeue too).
 */
public final class EGLNative {
    private static final String TAG = "EGL";

    static final int EGL_SUCCESS = 0x3000;
    static final int EGL_BAD_NATIVE_WINDOW = 0x300B;
    static final int EGL_BAD_SURFACE = 0x300D;
    static final int EGL_BAD_MATCH = 0x3009;
    static final int EGL_BAD_PARAMETER = 0x300C;

    private static final class Window {
        final Surface surface;

        Window(Surface surface) {
            this.surface = surface;
        }
    }

    private static final HashMap<Long, Window> sWindows = new HashMap<Long, Window>();
    private static final ThreadLocal<Integer> sError = new ThreadLocal<Integer>();
    private static final HashSet<String> sLogged = new HashSet<String>();

    private EGLNative() {}

    /** framework-internal. Logs once that a GL entry point is not implemented. */
    public static void unsupported(String what) {
        synchronized (sLogged) {
            if (!sLogged.add(what)) return;
        }
        Log.w(TAG, what + " is not implemented");
    }

    static void setError(int error) {
        sError.set(error);
    }

    public static int getError() {
        Integer e = sError.get();
        if (e != null && e != EGL_SUCCESS) {
            sError.set(EGL_SUCCESS);
            return e;
        }
        return nGetError();
    }

    /** The surface a window object stands for, or null (AOSP accepts these four kinds). */
    public static Surface windowSurface(Object win) {
        if (win instanceof Surface) return (Surface) win;
        if (win instanceof SurfaceView) return ((SurfaceView) win).getHolder().getSurface();
        if (win instanceof SurfaceHolder) return ((SurfaceHolder) win).getSurface();
        if (win instanceof SurfaceTexture) return new Surface((SurfaceTexture) win);
        return null;
    }

    /** Creates a window surface for a Surface; returns 0 and sets the error on failure. */
    public static long createWindowSurface(long dpy, long config, Object win) {
        Surface surface = windowSurface(win);
        if (surface == null) {
            throw new UnsupportedOperationException("eglCreateWindowSurface() can only be called with an instance of "
                    + "Surface, SurfaceView, SurfaceTexture or SurfaceHolder at the moment, this will be fixed later.");
        }
        if (!surface.isValid()) {
            setError(EGL_BAD_NATIVE_WINDOW);
            return 0;
        }
        Surface.BufferQueue q = surface.getBufferQueue();
        long handle = nCreateSurface(dpy, config, null, true, q.getWidth(), q.getHeight());
        if (handle != 0) {
            synchronized (sWindows) {
                sWindows.put(handle, new Window(surface));
            }
        }
        return handle;
    }

    public static long createPbufferSurface(long dpy, long config, int[] attribs) {
        return nCreateSurface(dpy, config, attribs, false, 0, 0);
    }

    public static boolean destroySurface(long dpy, long surface) {
        synchronized (sWindows) {
            sWindows.remove(surface);
        }
        return nDestroySurface(dpy, surface);
    }

    public static boolean swapBuffers(long dpy, long surface) {
        Window w;
        synchronized (sWindows) {
            w = sWindows.get(surface);
        }
        if (w == null) return nSwapBuffers(dpy, surface);
        Surface s = w.surface;
        if (!s.isValid()) {
            setError(EGL_BAD_SURFACE);
            return false;
        }
        int[] size = new int[1];
        nQuerySurface(dpy, surface, 0x3057 /* EGL_WIDTH */, size);
        int width = size[0];
        nQuerySurface(dpy, surface, 0x3056 /* EGL_HEIGHT */, size);
        int height = size[0];
        int[] pixels = s.lockGlBuffer(width, height);
        if (pixels != null) {
            if (!nReadWindow(surface, pixels, s.isOpaqueBuffer())) {
                setError(EGL_BAD_SURFACE);
                return false;
            }
            s.unlockGlBufferAndPost();
        }
        Surface.BufferQueue q = s.getBufferQueue();
        if (q.getWidth() != width || q.getHeight() != height) {
            nResizeWindow(surface, q.getWidth(), q.getHeight());
        }
        return true;
    }

    // ---- natives (src/android/android_gl.c); handles are real EGL handles, surfaces are SaSurf pointers ----

    public static native boolean nLoad();

    public static native long nGetDisplay();

    public static native boolean nInitialize(long dpy, int[] version);

    public static native boolean nTerminate(long dpy);

    public static native String nQueryString(long dpy, int name);

    static native int nGetError();

    public static native boolean nChooseConfig(long dpy, int[] attribs, long[] configs, int size, int[] num);

    public static native boolean nGetConfigs(long dpy, long[] configs, int size, int[] num);

    public static native boolean nGetConfigAttrib(long dpy, long config, int attr, int[] value);

    public static native long nCreateContext(long dpy, long config, long share, int[] attribs);

    public static native boolean nDestroyContext(long dpy, long ctx);

    static native long nCreateSurface(long dpy, long config, int[] attribs, boolean window, int width, int height);

    static native boolean nDestroySurface(long dpy, long surface);

    static native boolean nResizeWindow(long surface, int width, int height);

    public static native boolean nMakeCurrent(long dpy, long draw, long read, long ctx);

    public static native long nGetCurrentContext();

    public static native long nGetCurrentDisplay();

    public static native long nGetCurrentSurface(int which);

    public static native boolean nQuerySurface(long dpy, long surface, int attr, int[] value);

    public static native boolean nQueryContext(long dpy, long ctx, int attr, int[] value);

    static native boolean nSwapBuffers(long dpy, long surface);

    static native boolean nReadWindow(long surface, int[] argb, boolean opaque);

    public static native boolean nBindAPI(int api);

    public static native int nQueryAPI();

    public static native boolean nWaitClient();

    public static native boolean nWaitGL();

    public static native boolean nWaitNative(int engine);

    public static native boolean nReleaseThread();

    public static native boolean nSwapInterval(long dpy, int interval);

    public static native boolean nSurfaceAttrib(long dpy, long surface, int attr, int value);
}
