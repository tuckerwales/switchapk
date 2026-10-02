package android.opengl;

/** Wrapper class for a native EGLContext object. */
public class EGLContext extends EGLObjectHandle {
    EGLContext(long handle) {
        super(handle);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EGLContext)) return false;
        EGLContext that = (EGLContext) o;
        return getNativeHandle() == that.getNativeHandle();
    }
}
