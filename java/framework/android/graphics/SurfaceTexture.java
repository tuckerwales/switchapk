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
    private android.view.Surface.BufferQueue mQueue;
    private OnFrameAvailableListener mListener;
    private android.os.Handler mListenerHandler;
    private boolean mReleased;

    /** framework-internal. The software buffers that Surfaces created from this texture draw into. */
    public Object getSoftwareBufferQueue() {
        if (mQueue == null) {
            mQueue = new android.view.Surface.BufferQueue();
            mQueue.setBuffers(mWidth > 0 ? mWidth : 1, mHeight > 0 ? mHeight : 1, false);
            mQueue.setOnFramePosted(new Runnable() {
                public void run() { dispatchFrameAvailable(); }
            });
        }
        return mQueue;
    }

    private void dispatchFrameAvailable() {
        final OnFrameAvailableListener l = mListener;
        if (l == null) return;
        android.os.Handler h = mListenerHandler;
        if (h == null) {
            android.os.Looper looper = android.os.Looper.myLooper();
            if (looper == null) looper = android.os.Looper.getMainLooper();
            h = new android.os.Handler(looper);
        }
        h.post(new Runnable() {
            public void run() { l.onFrameAvailable(SurfaceTexture.this); }
        });
    }

    public SurfaceTexture(int texName) { mTexName = texName; }
    public SurfaceTexture(int texName, boolean singleBufferMode) { mTexName = texName; }
    public SurfaceTexture(boolean singleBufferMode) { mTexName = 0; }
    public void setOnFrameAvailableListener(OnFrameAvailableListener listener) { setOnFrameAvailableListener(listener, null); }
    public void setOnFrameAvailableListener(OnFrameAvailableListener listener, android.os.Handler handler) {
        mListener = listener;
        mListenerHandler = handler;
    }
    public void setDefaultBufferSize(int width, int height) {
        mWidth = width;
        mHeight = height;
        if (mQueue != null) mQueue.setBuffers(width, height, false);
    }
    public void updateTexImage() {}
    public void releaseTexImage() {}
    public void detachFromGLContext() {}
    public void attachToGLContext(int texName) {}
    public void getTransformMatrix(float[] mtx) { android.opengl.Matrix.setIdentityM(mtx, 0); }
    public long getTimestamp() { return System.nanoTime(); }
    public void release() {
        mReleased = true;
        if (mQueue != null) mQueue.invalidate();
    }
    public boolean isReleased() { return mReleased; }
}
