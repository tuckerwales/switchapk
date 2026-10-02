package com.google.android.gles_jni;

import javax.microedition.khronos.opengles.GL;

/** framework-internal. An EGL context handle (see android.opengl.EGLNative). */
public class EGLContextImpl extends javax.microedition.khronos.egl.EGLContext {
    final long mHandle;
    private GLImpl mGLContext;

    public EGLContextImpl(long handle) {
        mHandle = handle;
    }

    @Override
    public GL getGL() {
        if (mGLContext == null) mGLContext = new GLImpl();
        return mGLContext;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return mHandle == ((EGLContextImpl) o).mHandle;
    }

    @Override
    public int hashCode() {
        return 31 * 17 + (int) (mHandle ^ (mHandle >>> 32));
    }
}
