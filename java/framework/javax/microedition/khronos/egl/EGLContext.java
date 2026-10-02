package javax.microedition.khronos.egl;

import javax.microedition.khronos.opengles.GL;

public abstract class EGLContext {
    private static final EGL EGL_INSTANCE = new com.google.android.gles_jni.EGLImpl();

    public EGLContext() {
    }

    public static EGL getEGL() {
        return EGL_INSTANCE;
    }

    public abstract GL getGL();
}
