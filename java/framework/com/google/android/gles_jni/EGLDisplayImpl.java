package com.google.android.gles_jni;

/** framework-internal. An EGL handle (see android.opengl.EGLNative). */
public class EGLDisplayImpl extends javax.microedition.khronos.egl.EGLDisplay {
    final long mHandle;

    public EGLDisplayImpl(long handle) {
        mHandle = handle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return mHandle == ((EGLDisplayImpl) o).mHandle;
    }

    @Override
    public int hashCode() {
        return 31 * 17 + (int) (mHandle ^ (mHandle >>> 32));
    }
}
