package android.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * A software surface: a double-buffered queue of ARGB bitmaps. Producers lock
 * a canvas on the back buffer and post it; the consumer (SurfaceView,
 * TextureView) draws the front buffer. A producer thread that posts waits
 * until the previous frame was consumed (or ~2 frames passed), which paces
 * game loops to the display like Android's buffer queue does. GL surfaces
 * are TODO(WS8).
 */
public class Surface implements Parcelable {
    public static final int CHANGE_FRAME_RATE_ALWAYS = 1;
    public static final int CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS = 0;
    public static final int FRAME_RATE_COMPATIBILITY_DEFAULT = 0;
    public static final int FRAME_RATE_COMPATIBILITY_FIXED_SOURCE = 1;
    public static final int ROTATION_0 = 0;
    public static final int ROTATION_180 = 2;
    public static final int ROTATION_270 = 3;
    public static final int ROTATION_90 = 1;

    /** framework-internal. Buffers shared by every Surface of one consumer. */
    public static final class BufferQueue {
        final Object mLock = new Object();
        Bitmap mFront;
        Bitmap mBack;
        int mWidth;
        int mHeight;
        boolean mOpaque;
        boolean mValid;
        boolean mFramePending;
        long mFrameNumber;
        Canvas mLocked;
        Runnable mOnFramePosted;

        /** framework-internal. (Re)allocates the buffers; content is kept where it fits. */
        public void setBuffers(int width, int height, boolean opaque) {
            synchronized (mLock) {
                width = Math.max(1, width);
                height = Math.max(1, height);
                if (mFront == null || mWidth != width || mHeight != height) {
                    Bitmap front = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    if (opaque) front.eraseColor(0xff000000);
                    mFront = front;
                    mBack = null;
                    mWidth = width;
                    mHeight = height;
                }
                mOpaque = opaque;
                mValid = true;
            }
        }

        /** framework-internal. */
        public void setOnFramePosted(Runnable r) { mOnFramePosted = r; }

        /** framework-internal. Front buffer for the consumer; marks the posted frame consumed. */
        public Bitmap acquireFront() {
            synchronized (mLock) {
                mFramePending = false;
                mLock.notifyAll();
                return mFront;
            }
        }

        /** framework-internal. */
        public long getFrameNumber() { return mFrameNumber; }

        /** framework-internal. */
        public void invalidate() {
            synchronized (mLock) {
                mValid = false;
                mFramePending = false;
                mLock.notifyAll();
            }
        }

        public int getWidth() { return mWidth; }

        public int getHeight() { return mHeight; }

        boolean isValid() { return mValid; }
    }

    private BufferQueue mQueue;
    private final SurfaceTexture mSurfaceTexture;
    private boolean mReleased;

    /** framework-internal (hidden in AOSP). */
    public Surface() {
        mQueue = new BufferQueue();
        mSurfaceTexture = null;
    }

    public Surface(SurfaceControl from) { this(); }

    public Surface(SurfaceTexture surfaceTexture) {
        if (surfaceTexture == null) throw new IllegalArgumentException("surfaceTexture must not be null");
        mSurfaceTexture = surfaceTexture;
        mQueue = (BufferQueue) surfaceTexture.getSoftwareBufferQueue();
    }

    /** framework-internal. */
    public BufferQueue getBufferQueue() { return mQueue; }

    public void release() { mReleased = true; }

    public boolean isValid() { return !mReleased && mQueue != null && mQueue.isValid(); }

    public Canvas lockCanvas(Rect inOutDirty) throws IllegalArgumentException, OutOfResourcesException {
        if (mReleased || mQueue == null) throw new IllegalStateException("Surface has already been released.");
        final BufferQueue q = mQueue;
        synchronized (q.mLock) {
            if (!q.mValid) throw new IllegalStateException("Surface is not valid.");
            if (q.mLocked != null) throw new IllegalArgumentException("Surface was already locked");
            if (q.mBack == null || q.mBack.getWidth() != q.mWidth || q.mBack.getHeight() != q.mHeight) {
                q.mBack = Bitmap.createBitmap(q.mWidth, q.mHeight, Bitmap.Config.ARGB_8888);
            }
            // keep the previous frame so partial updates work
            int[] src = q.mFront.getPixelArray();
            int[] dst = q.mBack.getPixelArray();
            if (src != null && dst != null && src.length == dst.length) System.arraycopy(src, 0, dst, 0, src.length);
            Canvas canvas = new Canvas(q.mBack);
            if (inOutDirty != null) {
                if (!inOutDirty.intersect(0, 0, q.mWidth, q.mHeight)) inOutDirty.setEmpty();
                canvas.clipRect(inOutDirty);
            }
            q.mLocked = canvas;
            return canvas;
        }
    }

    public void unlockCanvasAndPost(Canvas canvas) {
        final BufferQueue q = mQueue;
        if (q == null) return;
        Runnable notify;
        synchronized (q.mLock) {
            if (canvas != q.mLocked) {
                throw new IllegalArgumentException("canvas object must be the same instance that "
                        + "was previously returned by lockCanvas");
            }
            q.mLocked = null;
            Bitmap tmp = q.mFront;
            q.mFront = q.mBack;
            q.mBack = tmp;
            q.mFramePending = true;
            q.mFrameNumber++;
            notify = q.mOnFramePosted;
        }
        if (notify != null) notify.run();
        if (Looper.myLooper() != Looper.getMainLooper()) {
            synchronized (q.mLock) {
                long deadline = System.currentTimeMillis() + 34;
                while (q.mFramePending && q.mValid) {
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0) break;
                    try {
                        q.mLock.wait(left);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }
    }

    public Canvas lockHardwareCanvas() { return lockCanvas(null); }

    @Deprecated
    public void unlockCanvas(Canvas canvas) {
        throw new UnsupportedOperationException();
    }

    public void setFrameRate(float frameRate, int compatibility, int changeFrameRateStrategy) {}

    public void setFrameRate(float frameRate, int compatibility) {}

    public void clearFrameRate() {}

    public int describeContents() { return 0; }

    public void readFromParcel(Parcel source) {}

    public void writeToParcel(Parcel dest, int flags) {}

    @Override
    public String toString() { return "Surface(" + (mQueue != null ? mQueue.mWidth + "x" + mQueue.mHeight : "-") + ")"; }

    public static final Parcelable.Creator<Surface> CREATOR = new Parcelable.Creator<Surface>() {
        public Surface createFromParcel(Parcel source) { return new Surface(); }
        public Surface[] newArray(int size) { return new Surface[size]; }
    };

    public static class OutOfResourcesException extends RuntimeException {
        public OutOfResourcesException() {}

        public OutOfResourcesException(String name) { super(name); }
    }
}
