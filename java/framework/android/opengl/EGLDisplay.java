package android.opengl;

/** Wrapper class for a native EGLDisplay object. */
public class EGLDisplay extends EGLObjectHandle {
    EGLDisplay(long handle) {
        super(handle);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EGLDisplay)) return false;
        EGLDisplay that = (EGLDisplay) o;
        return getNativeHandle() == that.getNativeHandle();
    }
}
