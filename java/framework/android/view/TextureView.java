package android.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;

/**
 * Displays a content stream (AOSP TextureView API). On switchapk the
 * SurfaceTexture is backed by a software buffer queue, so lockCanvas and
 * Surfaces created from the texture work without GL; GL producers are TODO(WS8).
 */
public class TextureView extends View {
    private static final String LOG_TAG = "TextureView";

    private SurfaceTexture mSurface;
    private Surface.BufferQueue mQueue;
    private Surface mCanvasSurface;
    private SurfaceTextureListener mListener;
    private boolean mOpaque = true;
    private final Matrix mMatrix = new Matrix();
    private boolean mMatrixChanged;
    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private boolean mHadSurface;
    private final Runnable mUpdate = new Runnable() {
        public void run() {
            invalidate();
            if (mListener != null && mSurface != null) mListener.onSurfaceTextureUpdated(mSurface);
        }
    };

    public interface SurfaceTextureListener {
        void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height);
        void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height);
        boolean onSurfaceTextureDestroyed(SurfaceTexture surface);
        void onSurfaceTextureUpdated(SurfaceTexture surface);
    }

    public TextureView(Context context) { super(context); }

    public TextureView(Context context, AttributeSet attrs) { super(context, attrs); }

    public TextureView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    public TextureView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean isOpaque() { return mOpaque; }

    public void setOpaque(boolean opaque) {
        if (opaque != mOpaque) {
            mOpaque = opaque;
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() { super.onAttachedToWindow(); }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        destroySurface();
    }

    private void destroySurface() {
        if (mSurface != null) {
            boolean shouldRelease = true;
            if (mListener != null) shouldRelease = mListener.onSurfaceTextureDestroyed(mSurface);
            if (shouldRelease) mSurface.release();
            mSurface = null;
            mQueue = null;
            mCanvasSurface = null;
            mHadSurface = false;
        }
    }

    @Override
    public void setLayerType(int layerType, Paint paint) { setLayerPaint(paint); }

    @Override
    public void setLayerPaint(Paint paint) {
        if (paint != null) mPaint.set(paint);
        invalidate();
    }

    @Override
    public int getLayerType() { return LAYER_TYPE_HARDWARE; }

    @Override
    public void buildLayer() {}

    @Override
    public void setForeground(Drawable foreground) {
        if (foreground != null) Log.w(LOG_TAG, "TextureView does not support displaying a foreground drawable");
    }

    @Override
    public void setBackgroundDrawable(Drawable background) {
        if (background != null) Log.w(LOG_TAG, "TextureView does not support displaying a background drawable");
    }

    private void ensureSurface() {
        if (mSurface == null && getWidth() > 0 && getHeight() > 0 && isAttachedToWindow()) {
            mSurface = new SurfaceTexture(false);
            mSurface.setDefaultBufferSize(getWidth(), getHeight());
            attachQueue();
            if (mListener != null) mListener.onSurfaceTextureAvailable(mSurface, getWidth(), getHeight());
            mHadSurface = true;
        }
    }

    private void attachQueue() {
        mQueue = (Surface.BufferQueue) mSurface.getSoftwareBufferQueue();
        final Runnable previous = mUpdate;
        mQueue.setOnFramePosted(new Runnable() {
            public void run() {
                android.os.Handler h = getHandler();
                if (h != null) h.post(previous);
            }
        });
    }

    @Override
    public final void draw(Canvas canvas) {
        mPrivateFlags = (mPrivateFlags & ~PFLAG_DIRTY) | PFLAG_DRAWN;
        ensureSurface();
        if (mQueue == null) return;
        Bitmap front = mQueue.acquireFront();
        if (front == null) return;
        canvas.save();
        if (!mMatrix.isIdentity()) canvas.concat(mMatrix);
        Rect src = new Rect(0, 0, front.getWidth(), front.getHeight());
        Rect dst = new Rect(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(front, src, dst, mPaint);
        canvas.restore();
    }

    @Override
    protected final void onDraw(Canvas canvas) {}

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mSurface != null) {
            mSurface.setDefaultBufferSize(getWidth(), getHeight());
            if (mListener != null) mListener.onSurfaceTextureSizeChanged(mSurface, getWidth(), getHeight());
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
    }

    public void setTransform(Matrix transform) {
        mMatrix.set(transform);
        mMatrixChanged = true;
        invalidate();
    }

    public Matrix getTransform(Matrix transform) {
        if (transform == null) transform = new Matrix();
        transform.set(mMatrix);
        return transform;
    }

    public Bitmap getBitmap() { return getBitmap(getWidth(), getHeight()); }

    public Bitmap getBitmap(int width, int height) {
        if (isAvailable() && width > 0 && height > 0) {
            return getBitmap(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888));
        }
        return null;
    }

    public Bitmap getBitmap(Bitmap bitmap) {
        if (bitmap != null && isAvailable() && mQueue != null) {
            Bitmap front = mQueue.acquireFront();
            Canvas c = new Canvas(bitmap);
            c.drawBitmap(front, new Rect(0, 0, front.getWidth(), front.getHeight()),
                    new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()), mPaint);
        }
        return bitmap;
    }

    public boolean isAvailable() { return mSurface != null; }

    public Canvas lockCanvas() { return lockCanvas(null); }

    public Canvas lockCanvas(Rect dirty) {
        if (!isAvailable()) return null;
        if (mCanvasSurface == null) mCanvasSurface = new Surface(mSurface);
        return mCanvasSurface.lockCanvas(dirty);
    }

    public void unlockCanvasAndPost(Canvas canvas) {
        if (mCanvasSurface != null) mCanvasSurface.unlockCanvasAndPost(canvas);
    }

    public SurfaceTexture getSurfaceTexture() { return mSurface; }

    public void setSurfaceTexture(SurfaceTexture surfaceTexture) {
        if (surfaceTexture == null) throw new NullPointerException("surfaceTexture must not be null");
        if (surfaceTexture == mSurface) {
            throw new IllegalArgumentException("Trying to setSurfaceTexture to the same SurfaceTexture that's "
                    + "already set.");
        }
        if (surfaceTexture.isReleased()) {
            throw new IllegalArgumentException("Cannot setSurfaceTexture to a released SurfaceTexture");
        }
        if (mSurface != null) mSurface.release();
        mSurface = surfaceTexture;
        mCanvasSurface = null;
        attachQueue();
        invalidate();
    }

    public SurfaceTextureListener getSurfaceTextureListener() { return mListener; }

    public void setSurfaceTextureListener(SurfaceTextureListener listener) { mListener = listener; }
}
