package android.graphics;

public class SurfaceTexture {
    public interface OnFrameAvailableListener {
        void onFrameAvailable(SurfaceTexture surfaceTexture);
    }

    public static class OutOfResourcesException extends Exception {
        public OutOfResourcesException() {}
        public OutOfResourcesException(String name) { super(name); }
    }

    private int mWidth, mHeight;
    private final int mTexName;

    public SurfaceTexture(int texName) { mTexName = texName; }
    public SurfaceTexture(int texName, boolean singleBufferMode) { mTexName = texName; }
    public SurfaceTexture(boolean singleBufferMode) { mTexName = 0; }
    public void setOnFrameAvailableListener(OnFrameAvailableListener listener) {}
    public void setOnFrameAvailableListener(OnFrameAvailableListener listener, android.os.Handler handler) {}
    public void setDefaultBufferSize(int width, int height) { mWidth = width; mHeight = height; }
    public void updateTexImage() {}
    public void releaseTexImage() {}
    public void detachFromGLContext() {}
    public void attachToGLContext(int texName) {}
    public void getTransformMatrix(float[] mtx) { android.opengl.Matrix.setIdentityM(mtx, 0); }
    public long getTimestamp() { return System.nanoTime(); }
    public void release() {}
    public boolean isReleased() { return false; }
}
