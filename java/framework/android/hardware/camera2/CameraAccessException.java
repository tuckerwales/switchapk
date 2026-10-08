package android.hardware.camera2;

import android.util.AndroidException;

public class CameraAccessException extends AndroidException {
    public static final int CAMERA_DISABLED = 1;
    public static final int CAMERA_DISCONNECTED = 2;
    public static final int CAMERA_ERROR = 3;
    public static final int CAMERA_IN_USE = 4;
    public static final int MAX_CAMERAS_IN_USE = 5;

    private final int mReason;

    public CameraAccessException(int problem) {
        this(problem, null, null);
    }

    public CameraAccessException(int problem, String message) {
        this(problem, message, null);
    }

    public CameraAccessException(int problem, String message, Throwable cause) {
        super(message, cause);
        mReason = problem;
    }

    public CameraAccessException(int problem, Throwable cause) {
        this(problem, null, cause);
    }

    public final int getReason() { return mReason; }
}
