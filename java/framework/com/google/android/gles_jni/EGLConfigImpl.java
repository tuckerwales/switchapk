package com.google.android.gles_jni;

/** framework-internal. An EGL handle (see android.opengl.EGLNative). */
public class EGLConfigImpl extends javax.microedition.khronos.egl.EGLConfig {
    final long mHandle;

    public EGLConfigImpl(long handle) {
        mHandle = handle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return mHandle == ((EGLConfigImpl) o).mHandle;
    }

    @Override
    public int hashCode() {
        return 31 * 17 + (int) (mHandle ^ (mHandle >>> 32));
    }
}
