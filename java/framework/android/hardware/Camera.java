package android.hardware;

/**
 * The legacy camera API. The console has no camera: getNumberOfCameras() is 0, open() returns null as AOSP does when
 * there is no back camera, and open(int) fails as it does for a camera that cannot be reached (WS15). No Camera
 * object can exist, so the instance API is not provided.
 */
@Deprecated
public class Camera {
    public static final String ACTION_NEW_PICTURE = "android.hardware.action.NEW_PICTURE";
    public static final String ACTION_NEW_VIDEO = "android.hardware.action.NEW_VIDEO";
    public static final int CAMERA_ERROR_UNKNOWN = 1;
    public static final int CAMERA_ERROR_EVICTED = 2;
    public static final int CAMERA_ERROR_SERVER_DIED = 100;

    private Camera() {}

    public static int getNumberOfCameras() { return 0; }

    public static void getCameraInfo(int cameraId, CameraInfo cameraInfo) {
        throw new RuntimeException("Unknown camera ID");
    }

    public static Camera open(int cameraId) {
        throw new RuntimeException("Fail to connect to camera service");
    }

    public static Camera open() { return null; }

    public static class CameraInfo {
        public static final int CAMERA_FACING_BACK = 0;
        public static final int CAMERA_FACING_FRONT = 1;
        public int facing;
        public int orientation;
        public boolean canDisableShutterSound;

        public CameraInfo() {}
    }

    public interface PreviewCallback {
        void onPreviewFrame(byte[] data, Camera camera);
    }

    public interface PictureCallback {
        void onPictureTaken(byte[] data, Camera camera);
    }

    public interface ShutterCallback {
        void onShutter();
    }

    public interface AutoFocusCallback {
        void onAutoFocus(boolean success, Camera camera);
    }

    public interface ErrorCallback {
        void onError(int error, Camera camera);
    }
}
