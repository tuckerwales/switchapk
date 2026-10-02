package android.opengl;

/** An exception class for OpenGL errors. */
@SuppressWarnings("serial")
public class GLException extends RuntimeException {
    private final int mError;

    public GLException(final int error) {
        super(getErrorString(error));
        mError = error;
    }

    public GLException(final int error, final String string) {
        super(string);
        mError = error;
    }

    private static String getErrorString(int error) {
        String errorString = GLU.gluErrorString(error);
        if (errorString == null) errorString = "Unknown error 0x" + Integer.toHexString(error);
        return errorString;
    }

    int getError() {
        return mError;
    }
}
