package android.opengl;

/** EGL 1.4 bindings over {@link EGLNative}. */
public class EGL14 {
    public static final int EGL_ALPHA_MASK_SIZE = 12350;
    public static final int EGL_ALPHA_SIZE = 12321;
    public static final int EGL_BACK_BUFFER = 12420;
    public static final int EGL_BAD_ACCESS = 12290;
    public static final int EGL_BAD_ALLOC = 12291;
    public static final int EGL_BAD_ATTRIBUTE = 12292;
    public static final int EGL_BAD_CONFIG = 12293;
    public static final int EGL_BAD_CONTEXT = 12294;
    public static final int EGL_BAD_CURRENT_SURFACE = 12295;
    public static final int EGL_BAD_DISPLAY = 12296;
    public static final int EGL_BAD_MATCH = 12297;
    public static final int EGL_BAD_NATIVE_PIXMAP = 12298;
    public static final int EGL_BAD_NATIVE_WINDOW = 12299;
    public static final int EGL_BAD_PARAMETER = 12300;
    public static final int EGL_BAD_SURFACE = 12301;
    public static final int EGL_BIND_TO_TEXTURE_RGB = 12345;
    public static final int EGL_BIND_TO_TEXTURE_RGBA = 12346;
    public static final int EGL_BLUE_SIZE = 12322;
    public static final int EGL_BUFFER_DESTROYED = 12437;
    public static final int EGL_BUFFER_PRESERVED = 12436;
    public static final int EGL_BUFFER_SIZE = 12320;
    public static final int EGL_CLIENT_APIS = 12429;
    public static final int EGL_COLOR_BUFFER_TYPE = 12351;
    public static final int EGL_CONFIG_CAVEAT = 12327;
    public static final int EGL_CONFIG_ID = 12328;
    public static final int EGL_CONFORMANT = 12354;
    public static final int EGL_CONTEXT_CLIENT_TYPE = 12439;
    public static final int EGL_CONTEXT_CLIENT_VERSION = 12440;
    public static final int EGL_CONTEXT_LOST = 12302;
    public static final int EGL_CORE_NATIVE_ENGINE = 12379;
    public static final int EGL_DEFAULT_DISPLAY = 0;
    public static final int EGL_DEPTH_SIZE = 12325;
    public static final int EGL_DISPLAY_SCALING = 10000;
    public static final int EGL_DRAW = 12377;
    public static final int EGL_EXTENSIONS = 12373;
    public static final int EGL_FALSE = 0;
    public static final int EGL_GREEN_SIZE = 12323;
    public static final int EGL_HEIGHT = 12374;
    public static final int EGL_HORIZONTAL_RESOLUTION = 12432;
    public static final int EGL_LARGEST_PBUFFER = 12376;
    public static final int EGL_LEVEL = 12329;
    public static final int EGL_LUMINANCE_BUFFER = 12431;
    public static final int EGL_LUMINANCE_SIZE = 12349;
    public static final int EGL_MATCH_NATIVE_PIXMAP = 12353;
    public static final int EGL_MAX_PBUFFER_HEIGHT = 12330;
    public static final int EGL_MAX_PBUFFER_PIXELS = 12331;
    public static final int EGL_MAX_PBUFFER_WIDTH = 12332;
    public static final int EGL_MAX_SWAP_INTERVAL = 12348;
    public static final int EGL_MIN_SWAP_INTERVAL = 12347;
    public static final int EGL_MIPMAP_LEVEL = 12419;
    public static final int EGL_MIPMAP_TEXTURE = 12418;
    public static final int EGL_MULTISAMPLE_RESOLVE = 12441;
    public static final int EGL_MULTISAMPLE_RESOLVE_BOX = 12443;
    public static final int EGL_MULTISAMPLE_RESOLVE_BOX_BIT = 512;
    public static final int EGL_MULTISAMPLE_RESOLVE_DEFAULT = 12442;
    public static final int EGL_NATIVE_RENDERABLE = 12333;
    public static final int EGL_NATIVE_VISUAL_ID = 12334;
    public static final int EGL_NATIVE_VISUAL_TYPE = 12335;
    public static final int EGL_NONE = 12344;
    public static final int EGL_NON_CONFORMANT_CONFIG = 12369;
    public static final int EGL_NOT_INITIALIZED = 12289;
    public static final int EGL_NO_TEXTURE = 12380;
    public static final int EGL_OPENGL_API = 12450;
    public static final int EGL_OPENGL_BIT = 8;
    public static final int EGL_OPENGL_ES2_BIT = 4;
    public static final int EGL_OPENGL_ES_API = 12448;
    public static final int EGL_OPENGL_ES_BIT = 1;
    public static final int EGL_OPENVG_API = 12449;
    public static final int EGL_OPENVG_BIT = 2;
    public static final int EGL_OPENVG_IMAGE = 12438;
    public static final int EGL_PBUFFER_BIT = 1;
    public static final int EGL_PIXEL_ASPECT_RATIO = 12434;
    public static final int EGL_PIXMAP_BIT = 2;
    public static final int EGL_READ = 12378;
    public static final int EGL_RED_SIZE = 12324;
    public static final int EGL_RENDERABLE_TYPE = 12352;
    public static final int EGL_RENDER_BUFFER = 12422;
    public static final int EGL_RGB_BUFFER = 12430;
    public static final int EGL_SAMPLES = 12337;
    public static final int EGL_SAMPLE_BUFFERS = 12338;
    public static final int EGL_SINGLE_BUFFER = 12421;
    public static final int EGL_SLOW_CONFIG = 12368;
    public static final int EGL_STENCIL_SIZE = 12326;
    public static final int EGL_SUCCESS = 12288;
    public static final int EGL_SURFACE_TYPE = 12339;
    public static final int EGL_SWAP_BEHAVIOR = 12435;
    public static final int EGL_SWAP_BEHAVIOR_PRESERVED_BIT = 1024;
    public static final int EGL_TEXTURE_2D = 12383;
    public static final int EGL_TEXTURE_FORMAT = 12416;
    public static final int EGL_TEXTURE_RGB = 12381;
    public static final int EGL_TEXTURE_RGBA = 12382;
    public static final int EGL_TEXTURE_TARGET = 12417;
    public static final int EGL_TRANSPARENT_BLUE_VALUE = 12341;
    public static final int EGL_TRANSPARENT_GREEN_VALUE = 12342;
    public static final int EGL_TRANSPARENT_RED_VALUE = 12343;
    public static final int EGL_TRANSPARENT_RGB = 12370;
    public static final int EGL_TRANSPARENT_TYPE = 12340;
    public static final int EGL_TRUE = 1;
    public static final int EGL_VENDOR = 12371;
    public static final int EGL_VERSION = 12372;
    public static final int EGL_VERTICAL_RESOLUTION = 12433;
    public static final int EGL_VG_ALPHA_FORMAT = 12424;
    public static final int EGL_VG_ALPHA_FORMAT_NONPRE = 12427;
    public static final int EGL_VG_ALPHA_FORMAT_PRE = 12428;
    public static final int EGL_VG_ALPHA_FORMAT_PRE_BIT = 64;
    public static final int EGL_VG_COLORSPACE = 12423;
    public static final int EGL_VG_COLORSPACE_LINEAR = 12426;
    public static final int EGL_VG_COLORSPACE_LINEAR_BIT = 32;
    public static final int EGL_VG_COLORSPACE_sRGB = 12425;
    public static final int EGL_WIDTH = 12375;
    public static final int EGL_WINDOW_BIT = 4;

    public static EGLContext EGL_NO_CONTEXT = new EGLContext(0);
    public static EGLDisplay EGL_NO_DISPLAY = new EGLDisplay(0);
    public static EGLSurface EGL_NO_SURFACE = new EGLSurface(0);

    public EGL14() {
    }

    private static long h(EGLObjectHandle o) {
        return o == null ? 0 : o.getNativeHandle();
    }

    private static int[] attribs(int[] list, int offset, String what) {
        if (list == null) return null;
        if (offset < 0) throw new IllegalArgumentException("offset < 0");
        if (offset > list.length) throw new IllegalArgumentException("length - offset < 0");
        boolean terminated = false;
        for (int i = offset; i < list.length; i += 2) {
            if (list[i] == EGL_NONE) {
                terminated = true;
                break;
            }
        }
        if (!terminated) throw new IllegalArgumentException(what + " must contain EGL_NONE!");
        int[] out = new int[list.length - offset];
        System.arraycopy(list, offset, out, 0, out.length);
        return out;
    }

    private static int[] outArray(int[] a, int offset, int need, String what) {
        if (a == null) throw new IllegalArgumentException(what + " == null");
        if (offset < 0) throw new IllegalArgumentException("offset < 0");
        if (a.length - offset < need) throw new IllegalArgumentException("length - offset < " + need + " < needed");
        return new int[need];
    }

    public static int eglGetError() {
        return EGLNative.getError();
    }

    public static EGLDisplay eglGetDisplay(int display_id) {
        return new EGLDisplay(display_id == EGL_DEFAULT_DISPLAY ? EGLNative.nGetDisplay() : 0);
    }

    /** @hide */
    public static EGLDisplay eglGetDisplay(long display_id) {
        return eglGetDisplay((int) display_id);
    }

    public static boolean eglInitialize(EGLDisplay dpy, int[] major, int majorOffset, int[] minor, int minorOffset) {
        int[] v = new int[2];
        boolean ok = EGLNative.nInitialize(h(dpy), v);
        if (ok) {
            if (major != null) major[majorOffset] = v[0];
            if (minor != null) minor[minorOffset] = v[1];
        }
        return ok;
    }

    public static boolean eglTerminate(EGLDisplay dpy) {
        return EGLNative.nTerminate(h(dpy));
    }

    public static String eglQueryString(EGLDisplay dpy, int name) {
        return EGLNative.nQueryString(h(dpy), name);
    }

    private static boolean configs(EGLDisplay dpy, int[] attribs, EGLConfig[] configs, int configsOffset,
            int configSize, int[] num_config, int num_configOffset) {
        outArray(num_config, num_configOffset, 1, "num_config");
        if (configs != null) {
            if (configsOffset < 0) throw new IllegalArgumentException("configsOffset < 0");
            if (configs.length - configsOffset < configSize) {
                throw new IllegalArgumentException("length - configsOffset < config_size < needed");
            }
        }
        long[] handles = configs != null ? new long[configSize] : null;
        int[] n = new int[1];
        boolean ok = attribs != null
                ? EGLNative.nChooseConfig(h(dpy), attribs, handles, configs != null ? configSize : 0, n)
                : EGLNative.nGetConfigs(h(dpy), handles, configs != null ? configSize : 0, n);
        if (ok) {
            num_config[num_configOffset] = n[0];
            if (configs != null) {
                for (int i = 0; i < n[0] && i < configSize; i++) configs[configsOffset + i] = new EGLConfig(handles[i]);
            }
        }
        return ok;
    }

    public static boolean eglGetConfigs(EGLDisplay dpy, EGLConfig[] configs, int configsOffset, int config_size,
            int[] num_config, int num_configOffset) {
        return configs(dpy, null, configs, configsOffset, config_size, num_config, num_configOffset);
    }

    public static boolean eglChooseConfig(EGLDisplay dpy, int[] attrib_list, int attrib_listOffset, EGLConfig[] configs,
            int configsOffset, int config_size, int[] num_config, int num_configOffset) {
        int[] a = attribs(attrib_list, attrib_listOffset, "attrib_list");
        return configs(dpy, a != null ? a : new int[] {EGL_NONE}, configs, configsOffset, config_size, num_config,
                num_configOffset);
    }

    public static boolean eglGetConfigAttrib(EGLDisplay dpy, EGLConfig config, int attribute, int[] value,
            int offset) {
        int[] v = outArray(value, offset, 1, "value");
        boolean ok = EGLNative.nGetConfigAttrib(h(dpy), h(config), attribute, v);
        if (ok) value[offset] = v[0];
        return ok;
    }

    public static EGLSurface eglCreateWindowSurface(EGLDisplay dpy, EGLConfig config, Object win, int[] attrib_list,
            int offset) {
        attribs(attrib_list, offset, "attrib_list");
        return new EGLSurface(EGLNative.createWindowSurface(h(dpy), h(config), win));
    }

    public static EGLSurface eglCreatePbufferSurface(EGLDisplay dpy, EGLConfig config, int[] attrib_list, int offset) {
        return new EGLSurface(EGLNative.createPbufferSurface(h(dpy), h(config), attribs(attrib_list, offset,
                "attrib_list")));
    }

    @Deprecated
    public static EGLSurface eglCreatePixmapSurface(EGLDisplay dpy, EGLConfig config, int pixmap, int[] attrib_list,
            int offset) {
        EGLNative.setError(0x300A /* EGL_BAD_NATIVE_PIXMAP */);
        return EGL_NO_SURFACE;
    }

    public static boolean eglDestroySurface(EGLDisplay dpy, EGLSurface surface) {
        return EGLNative.destroySurface(h(dpy), h(surface));
    }

    public static boolean eglQuerySurface(EGLDisplay dpy, EGLSurface surface, int attribute, int[] value, int offset) {
        int[] v = outArray(value, offset, 1, "value");
        boolean ok = EGLNative.nQuerySurface(h(dpy), h(surface), attribute, v);
        if (ok) value[offset] = v[0];
        return ok;
    }

    public static boolean eglBindAPI(int api) {
        return EGLNative.nBindAPI(api);
    }

    public static int eglQueryAPI() {
        return EGLNative.nQueryAPI();
    }

    public static boolean eglWaitClient() {
        return EGLNative.nWaitClient();
    }

    public static boolean eglReleaseThread() {
        return EGLNative.nReleaseThread();
    }

    public static EGLSurface eglCreatePbufferFromClientBuffer(EGLDisplay dpy, int buftype, int buffer,
            EGLConfig config, int[] attrib_list, int offset) {
        EGLNative.setError(0x300C /* EGL_BAD_PARAMETER */);
        return EGL_NO_SURFACE;
    }

    /** @hide */
    public static EGLSurface eglCreatePbufferFromClientBuffer(EGLDisplay dpy, int buftype, long buffer,
            EGLConfig config, int[] attrib_list, int offset) {
        return eglCreatePbufferFromClientBuffer(dpy, buftype, (int) buffer, config, attrib_list, offset);
    }

    public static boolean eglSurfaceAttrib(EGLDisplay dpy, EGLSurface surface, int attribute, int value) {
        return EGLNative.nSurfaceAttrib(h(dpy), h(surface), attribute, value);
    }

    public static boolean eglBindTexImage(EGLDisplay dpy, EGLSurface surface, int buffer) {
        EGLNative.unsupported("eglBindTexImage");
        return false;
    }

    public static boolean eglReleaseTexImage(EGLDisplay dpy, EGLSurface surface, int buffer) {
        EGLNative.unsupported("eglReleaseTexImage");
        return false;
    }

    public static boolean eglSwapInterval(EGLDisplay dpy, int interval) {
        return EGLNative.nSwapInterval(h(dpy), interval);
    }

    public static EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig config, EGLContext share_context,
            int[] attrib_list, int offset) {
        return new EGLContext(EGLNative.nCreateContext(h(dpy), h(config), h(share_context), attribs(attrib_list,
                offset, "attrib_list")));
    }

    public static boolean eglDestroyContext(EGLDisplay dpy, EGLContext ctx) {
        return EGLNative.nDestroyContext(h(dpy), h(ctx));
    }

    public static boolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
        return EGLNative.nMakeCurrent(h(dpy), h(draw), h(read), h(ctx));
    }

    public static EGLContext eglGetCurrentContext() {
        return new EGLContext(EGLNative.nGetCurrentContext());
    }

    public static EGLSurface eglGetCurrentSurface(int readdraw) {
        return new EGLSurface(EGLNative.nGetCurrentSurface(readdraw));
    }

    public static EGLDisplay eglGetCurrentDisplay() {
        return new EGLDisplay(EGLNative.nGetCurrentDisplay());
    }

    public static boolean eglQueryContext(EGLDisplay dpy, EGLContext ctx, int attribute, int[] value, int offset) {
        int[] v = outArray(value, offset, 1, "value");
        boolean ok = EGLNative.nQueryContext(h(dpy), h(ctx), attribute, v);
        if (ok) value[offset] = v[0];
        return ok;
    }

    public static boolean eglWaitGL() {
        return EGLNative.nWaitGL();
    }

    public static boolean eglWaitNative(int engine) {
        return EGLNative.nWaitNative(engine);
    }

    public static boolean eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) {
        return EGLNative.swapBuffers(h(dpy), h(surface));
    }

    public static boolean eglCopyBuffers(EGLDisplay dpy, EGLSurface surface, int target) {
        EGLNative.setError(0x300A /* EGL_BAD_NATIVE_PIXMAP */);
        return false;
    }
}
