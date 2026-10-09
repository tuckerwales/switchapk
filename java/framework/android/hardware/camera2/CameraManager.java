package android.hardware.camera2;

import android.os.Handler;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Camera2 entry point with no cameras: the id list is empty and per-camera calls reject every id, as AOSP does for an
 * unknown one (WS15).
 */
public final class CameraManager {
    /** framework-internal: the instance behind Context.CAMERA_SERVICE. */
    public CameraManager() {}

    public String[] getCameraIdList() throws CameraAccessException { return new String[0]; }

    public Set<Set<String>> getConcurrentCameraIds() throws CameraAccessException { return new HashSet<Set<String>>(); }

    public void registerAvailabilityCallback(AvailabilityCallback callback, Handler handler) {}

    public void registerAvailabilityCallback(Executor executor, AvailabilityCallback callback) {
        if (executor == null) throw new IllegalArgumentException("executor was null");
    }

    public void unregisterAvailabilityCallback(AvailabilityCallback callback) {}

    public void registerTorchCallback(TorchCallback callback, Handler handler) {}

    public void registerTorchCallback(Executor executor, TorchCallback callback) {
        if (executor == null) throw new IllegalArgumentException("executor was null");
    }

    public void unregisterTorchCallback(TorchCallback callback) {}

    public void setTorchMode(String cameraId, boolean enabled) throws CameraAccessException {
        if (cameraId == null) throw new IllegalArgumentException("cameraId was null");
        throw new IllegalArgumentException("Camera id " + cameraId + " not found");
    }

    public void turnOnTorchWithStrengthLevel(String cameraId, int torchStrength) throws CameraAccessException {
        setTorchMode(cameraId, true);
    }

    public int getTorchStrengthLevel(String cameraId) throws CameraAccessException {
        setTorchMode(cameraId, false);
        return 0;
    }

    public boolean isCameraDeviceSetupSupported(String cameraId) throws CameraAccessException {
        if (cameraId == null) throw new IllegalArgumentException("cameraId was null");
        throw new IllegalArgumentException("Camera id " + cameraId + " not found");
    }

    public abstract static class AvailabilityCallback {
        public AvailabilityCallback() {}

        public void onCameraAvailable(String cameraId) {}
        public void onCameraUnavailable(String cameraId) {}
        public void onCameraAccessPrioritiesChanged() {}
        public void onPhysicalCameraAvailable(String cameraId, String physicalCameraId) {}
        public void onPhysicalCameraUnavailable(String cameraId, String physicalCameraId) {}
    }

    public abstract static class TorchCallback {
        public TorchCallback() {}

        public void onTorchModeUnavailable(String cameraId) {}
        public void onTorchModeChanged(String cameraId, boolean enabled) {}
        public void onTorchStrengthLevelChanged(String cameraId, int newStrengthLevel) {}
    }
}
