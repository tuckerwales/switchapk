package android.opengl;

/** EGL 1.5 bindings. Syncs, images and platform surfaces are not supported yet. */
public final class EGL15 {
    public static final int EGL_CL_EVENT_HANDLE = 12444;
    public static final int EGL_CONDITION_SATISFIED = 12534;
    public static final int EGL_CONTEXT_MAJOR_VERSION = 12440;
    public static final int EGL_CONTEXT_MINOR_VERSION = 12539;
    public static final int EGL_CONTEXT_OPENGL_COMPATIBILITY_PROFILE_BIT = 2;
    public static final int EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT = 1;
    public static final int EGL_CONTEXT_OPENGL_DEBUG = 12720;
    public static final int EGL_CONTEXT_OPENGL_FORWARD_COMPATIBLE = 12721;
    public static final int EGL_CONTEXT_OPENGL_PROFILE_MASK = 12541;
    public static final int EGL_CONTEXT_OPENGL_RESET_NOTIFICATION_STRATEGY = 12733;
    public static final int EGL_CONTEXT_OPENGL_ROBUST_ACCESS = 12722;
    public static final long EGL_FOREVER = -1l;
    public static final int EGL_GL_COLORSPACE = 12445;
    public static final int EGL_GL_COLORSPACE_LINEAR = 12426;
    public static final int EGL_GL_COLORSPACE_SRGB = 12425;
    public static final int EGL_GL_RENDERBUFFER = 12473;
    public static final int EGL_GL_TEXTURE_2D = 12465;
    public static final int EGL_GL_TEXTURE_3D = 12466;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_NEGATIVE_X = 12468;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_NEGATIVE_Y = 12470;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_NEGATIVE_Z = 12472;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_POSITIVE_X = 12467;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_POSITIVE_Y = 12469;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_POSITIVE_Z = 12471;
    public static final int EGL_GL_TEXTURE_LEVEL = 12476;
    public static final int EGL_GL_TEXTURE_ZOFFSET = 12477;
    public static final int EGL_IMAGE_PRESERVED = 12498;
    public static final int EGL_LOSE_CONTEXT_ON_RESET = 12735;
    public static final int EGL_NO_RESET_NOTIFICATION = 12734;
    public static final int EGL_OPENGL_ES3_BIT = 64;
    public static final int EGL_PLATFORM_ANDROID_KHR = 12609;
    public static final int EGL_SIGNALED = 12530;
    public static final int EGL_SYNC_CL_EVENT = 12542;
    public static final int EGL_SYNC_CL_EVENT_COMPLETE = 12543;
    public static final int EGL_SYNC_CONDITION = 12536;
    public static final int EGL_SYNC_FENCE = 12537;
    public static final int EGL_SYNC_FLUSH_COMMANDS_BIT = 1;
    public static final int EGL_SYNC_PRIOR_COMMANDS_COMPLETE = 12528;
    public static final int EGL_SYNC_STATUS = 12529;
    public static final int EGL_SYNC_TYPE = 12535;
    public static final int EGL_TIMEOUT_EXPIRED = 12533;
    public static final int EGL_UNSIGNALED = 12531;

    public static final EGLContext EGL_NO_CONTEXT = EGL14.EGL_NO_CONTEXT;
    public static final EGLDisplay EGL_NO_DISPLAY = EGL14.EGL_NO_DISPLAY;
    public static final EGLImage EGL_NO_IMAGE = new EGLImage(0);
    public static final EGLSurface EGL_NO_SURFACE = EGL14.EGL_NO_SURFACE;
    public static final EGLSync EGL_NO_SYNC = new EGLSync(0);

    private EGL15() {
    }

    public static EGLSync eglCreateSync(EGLDisplay dpy, int type, long[] attrib_list, int offset) {
        EGLNative.unsupported("eglCreateSync");
        return EGL_NO_SYNC;
    }

    public static boolean eglGetSyncAttrib(EGLDisplay dpy, EGLSync sync, int attribute, long[] value, int offset) {
        return false;
    }

    public static boolean eglDestroySync(EGLDisplay dpy, EGLSync sync) {
        return false;
    }

    public static int eglClientWaitSync(EGLDisplay dpy, EGLSync sync, int flags, long timeout) {
        return 0x30F6; // EGL_CONDITION_SATISFIED
    }

    public static EGLDisplay eglGetPlatformDisplay(int platform, long native_display, long[] attrib_list, int offset) {
        return EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    }

    public static EGLSurface eglCreatePlatformWindowSurface(EGLDisplay dpy, EGLConfig config,
            java.nio.Buffer native_window, long[] attrib_list, int offset) {
        EGLNative.unsupported("eglCreatePlatformWindowSurface");
        return EGL_NO_SURFACE;
    }

    public static EGLSurface eglCreatePlatformPixmapSurface(EGLDisplay dpy, EGLConfig config,
            java.nio.Buffer native_pixmap, long[] attrib_list, int offset) {
        return EGL_NO_SURFACE;
    }

    public static boolean eglWaitSync(EGLDisplay dpy, EGLSync sync, int flags) {
        return true;
    }

    public static EGLImage eglCreateImage(EGLDisplay dpy, EGLContext context, int target, long buffer,
            long[] attrib_list, int offset) {
        EGLNative.unsupported("eglCreateImage");
        return EGL_NO_IMAGE;
    }

    public static boolean eglDestroyImage(EGLDisplay dpy, EGLImage image) {
        return false;
    }
}
