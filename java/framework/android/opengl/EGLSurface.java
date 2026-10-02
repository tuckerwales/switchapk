package android.opengl;

/** Wrapper class for a native EGLSurface object. */
public class EGLSurface extends EGLObjectHandle {
    EGLSurface(long handle) {
        super(handle);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EGLSurface)) return false;
        EGLSurface that = (EGLSurface) o;
        return getNativeHandle() == that.getNativeHandle();
    }
}
