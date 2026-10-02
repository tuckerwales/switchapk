package android.opengl;

/** Wrapper class for a native EGLConfig object. */
public class EGLConfig extends EGLObjectHandle {
    EGLConfig(long handle) {
        super(handle);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EGLConfig)) return false;
        EGLConfig that = (EGLConfig) o;
        return getNativeHandle() == that.getNativeHandle();
    }
}
