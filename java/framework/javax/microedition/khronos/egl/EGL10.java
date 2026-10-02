package javax.microedition.khronos.egl;

public interface EGL10 extends EGL {
    int EGL_ALPHA_FORMAT = 12424;
    int EGL_ALPHA_MASK_SIZE = 12350;
    int EGL_ALPHA_SIZE = 12321;
    int EGL_BAD_ACCESS = 12290;
    int EGL_BAD_ALLOC = 12291;
    int EGL_BAD_ATTRIBUTE = 12292;
    int EGL_BAD_CONFIG = 12293;
    int EGL_BAD_CONTEXT = 12294;
    int EGL_BAD_CURRENT_SURFACE = 12295;
    int EGL_BAD_DISPLAY = 12296;
    int EGL_BAD_MATCH = 12297;
    int EGL_BAD_NATIVE_PIXMAP = 12298;
    int EGL_BAD_NATIVE_WINDOW = 12299;
    int EGL_BAD_PARAMETER = 12300;
    int EGL_BAD_SURFACE = 12301;
    int EGL_BLUE_SIZE = 12322;
    int EGL_BUFFER_SIZE = 12320;
    int EGL_COLORSPACE = 12423;
    int EGL_COLOR_BUFFER_TYPE = 12351;
    int EGL_CONFIG_CAVEAT = 12327;
    int EGL_CONFIG_ID = 12328;
    int EGL_CORE_NATIVE_ENGINE = 12379;
    int EGL_DEPTH_SIZE = 12325;
    int EGL_DONT_CARE = -1;
    int EGL_DRAW = 12377;
    int EGL_EXTENSIONS = 12373;
    int EGL_GREEN_SIZE = 12323;
    int EGL_HEIGHT = 12374;
    int EGL_HORIZONTAL_RESOLUTION = 12432;
    int EGL_LARGEST_PBUFFER = 12376;
    int EGL_LEVEL = 12329;
    int EGL_LUMINANCE_BUFFER = 12431;
    int EGL_LUMINANCE_SIZE = 12349;
    int EGL_MAX_PBUFFER_HEIGHT = 12330;
    int EGL_MAX_PBUFFER_PIXELS = 12331;
    int EGL_MAX_PBUFFER_WIDTH = 12332;
    int EGL_NATIVE_RENDERABLE = 12333;
    int EGL_NATIVE_VISUAL_ID = 12334;
    int EGL_NATIVE_VISUAL_TYPE = 12335;
    int EGL_NONE = 12344;
    int EGL_NON_CONFORMANT_CONFIG = 12369;
    int EGL_NOT_INITIALIZED = 12289;
    int EGL_PBUFFER_BIT = 1;
    int EGL_PIXEL_ASPECT_RATIO = 12434;
    int EGL_PIXMAP_BIT = 2;
    int EGL_READ = 12378;
    int EGL_RED_SIZE = 12324;
    int EGL_RENDERABLE_TYPE = 12352;
    int EGL_RENDER_BUFFER = 12422;
    int EGL_RGB_BUFFER = 12430;
    int EGL_SAMPLES = 12337;
    int EGL_SAMPLE_BUFFERS = 12338;
    int EGL_SINGLE_BUFFER = 12421;
    int EGL_SLOW_CONFIG = 12368;
    int EGL_STENCIL_SIZE = 12326;
    int EGL_SUCCESS = 12288;
    int EGL_SURFACE_TYPE = 12339;
    int EGL_TRANSPARENT_BLUE_VALUE = 12341;
    int EGL_TRANSPARENT_GREEN_VALUE = 12342;
    int EGL_TRANSPARENT_RED_VALUE = 12343;
    int EGL_TRANSPARENT_RGB = 12370;
    int EGL_TRANSPARENT_TYPE = 12340;
    int EGL_VENDOR = 12371;
    int EGL_VERSION = 12372;
    int EGL_VERTICAL_RESOLUTION = 12433;
    int EGL_WIDTH = 12375;
    int EGL_WINDOW_BIT = 4;

    Object EGL_DEFAULT_DISPLAY = null;
    EGLContext EGL_NO_CONTEXT = new com.google.android.gles_jni.EGLContextImpl(0);
    EGLDisplay EGL_NO_DISPLAY = new com.google.android.gles_jni.EGLDisplayImpl(0);
    EGLSurface EGL_NO_SURFACE = new com.google.android.gles_jni.EGLSurfaceImpl(0);

    boolean eglChooseConfig(EGLDisplay display, int[] attrib_list, EGLConfig[] configs, int config_size,
            int[] num_config);

    boolean eglCopyBuffers(EGLDisplay display, EGLSurface surface, Object native_pixmap);

    EGLContext eglCreateContext(EGLDisplay display, EGLConfig config, EGLContext share_context, int[] attrib_list);

    EGLSurface eglCreatePbufferSurface(EGLDisplay display, EGLConfig config, int[] attrib_list);

    @Deprecated
    EGLSurface eglCreatePixmapSurface(EGLDisplay display, EGLConfig config, Object native_pixmap, int[] attrib_list);

    EGLSurface eglCreateWindowSurface(EGLDisplay display, EGLConfig config, Object native_window, int[] attrib_list);

    boolean eglDestroyContext(EGLDisplay display, EGLContext context);

    boolean eglDestroySurface(EGLDisplay display, EGLSurface surface);

    boolean eglGetConfigAttrib(EGLDisplay display, EGLConfig config, int attribute, int[] value);

    boolean eglGetConfigs(EGLDisplay display, EGLConfig[] configs, int config_size, int[] num_config);

    EGLContext eglGetCurrentContext();

    EGLDisplay eglGetCurrentDisplay();

    EGLSurface eglGetCurrentSurface(int readdraw);

    EGLDisplay eglGetDisplay(Object native_display);

    int eglGetError();

    boolean eglInitialize(EGLDisplay display, int[] major_minor);

    boolean eglMakeCurrent(EGLDisplay display, EGLSurface draw, EGLSurface read, EGLContext context);

    boolean eglQueryContext(EGLDisplay display, EGLContext context, int attribute, int[] value);

    String eglQueryString(EGLDisplay display, int name);

    boolean eglQuerySurface(EGLDisplay display, EGLSurface surface, int attribute, int[] value);

    boolean eglSwapBuffers(EGLDisplay display, EGLSurface surface);

    boolean eglTerminate(EGLDisplay display);

    boolean eglWaitGL();

    boolean eglWaitNative(int engine, Object bindTarget);
}
