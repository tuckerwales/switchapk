package android.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.AttributeSet;
import java.util.ArrayList;

/**
 * A view with its own drawing surface (AOSP SurfaceView API). The surface is a
 * software buffer queue: a render thread locks a Canvas, draws and posts, and
 * the view draws the latest posted frame (scaled to its size when a fixed
 * buffer size was requested). Callbacks run on the UI thread as on Android.
 */
public class SurfaceView extends View {
    public static final int SURFACE_LIFECYCLE_DEFAULT = 0;
    public static final int SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT = 2;
    public static final int SURFACE_LIFECYCLE_FOLLOWS_VISIBILITY = 1;

    final ArrayList<SurfaceHolder.Callback> mCallbacks = new ArrayList<SurfaceHolder.Callback>();
    final Surface mSurface = new Surface();
    private final Surface.BufferQueue mQueue = mSurface.getBufferQueue();
    int mRequestedWidth = -1;
    int mRequestedHeight = -1;
    int mRequestedFormat = PixelFormat.RGB_565;
    private int mFormat = -1;
    boolean mIsCreating;
    boolean mSurfaceCreated;
    private boolean mWindowVisible;
    private boolean mAttached;
    private int mSurfaceWidth = -1;
    private int mSurfaceHeight = -1;
    private boolean mZOrderOnTop;
    private final Rect mSurfaceFrame = new Rect();
    private final Paint mBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect mSrc = new Rect();
    private final Rect mDst = new Rect();

    private final ViewTreeObserver.OnPreDrawListener mDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        public boolean onPreDraw() {
            updateSurface();
            return true;
        }
    };

    private final Runnable mInvalidateRunnable = new Runnable() {
        public void run() { invalidate(); }
    };

    public SurfaceView(Context context) { this(context, null); }

    public SurfaceView(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public SurfaceView(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public SurfaceView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setWillNotDraw(true);
        mQueue.setOnFramePosted(new Runnable() {
            public void run() {
                android.os.Handler h = getHandler();
                if (h != null) h.post(mInvalidateRunnable);
            }
        });
    }

    public SurfaceHolder getHolder() { return mSurfaceHolder; }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttached = true;
        getViewTreeObserver().addOnPreDrawListener(mDrawListener);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mWindowVisible = visibility == VISIBLE;
        updateSurface();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        updateSurface();
    }

    @Override
    public void setAlpha(float alpha) { super.setAlpha(alpha); }

    @Override
    protected boolean onSetAlpha(int alpha) { return false; }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(mDrawListener);
        mAttached = false;
        mWindowVisible = false;
        updateSurface();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = mRequestedWidth >= 0 ? resolveSizeAndState(mRequestedWidth, widthMeasureSpec, 0)
                : getDefaultSize(0, widthMeasureSpec);
        int height = mRequestedHeight >= 0 ? resolveSizeAndState(mRequestedHeight, heightMeasureSpec, 0)
                : getDefaultSize(0, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    public boolean gatherTransparentRegion(Region region) { return true; }

    @Override
    public void draw(Canvas canvas) {
        drawSurface(canvas);
        super.draw(canvas);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if ((mPrivateFlags & PFLAG_SKIP_DRAW) == PFLAG_SKIP_DRAW) drawSurface(canvas);
        super.dispatchDraw(canvas);
    }

    private void drawSurface(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (!mSurfaceCreated) {
            canvas.save();
            canvas.clipRect(0, 0, w, h);
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            canvas.restore();
            return;
        }
        Bitmap front = mQueue.acquireFront();
        if (front == null) return;
        if (front.getWidth() == w && front.getHeight() == h) {
            canvas.drawBitmap(front, 0, 0, null);
        } else {
            mSrc.set(0, 0, front.getWidth(), front.getHeight());
            mDst.set(0, 0, w, h);
            canvas.drawBitmap(front, mSrc, mDst, mBitmapPaint);
        }
    }

    @Override
    public void setClipBounds(Rect clipBounds) { super.setClipBounds(clipBounds); }

    @Override
    public boolean hasOverlappingRendering() { return false; }

    public void setZOrderMediaOverlay(boolean isMediaOverlay) {}

    public void setZOrderOnTop(boolean onTop) { mZOrderOnTop = onTop; }

    public void setSecure(boolean isSecure) {}

    public void setSurfaceLifecycle(int lifecycleStrategy) {}

    public void setDesiredHdrHeadroom(float desiredHeadroom) {}

    public SurfaceControl getSurfaceControl() { return null; }

    public IBinder getHostToken() { return getWindowToken(); }

    @Override
    public int getImportantForAccessibility() { return super.getImportantForAccessibility(); }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }

    private SurfaceHolder.Callback[] getSurfaceCallbacks() {
        synchronized (mCallbacks) {
            return mCallbacks.toArray(new SurfaceHolder.Callback[mCallbacks.size()]);
        }
    }

    /** Creates, resizes or destroys the surface to match the view (AOSP updateSurface). */
    void updateSurface() {
        final boolean visible = mAttached && mWindowVisible && getVisibility() == VISIBLE && getWidth() > 0
                && getHeight() > 0;
        if (!visible) {
            if (mSurfaceCreated) {
                mSurfaceCreated = false;
                for (SurfaceHolder.Callback c : getSurfaceCallbacks()) c.surfaceDestroyed(mSurfaceHolder);
                mQueue.invalidate();
                mSurfaceWidth = -1;
                mSurfaceHeight = -1;
            }
            return;
        }
        final int w = mRequestedWidth >= 0 ? mRequestedWidth : getWidth();
        final int h = mRequestedHeight >= 0 ? mRequestedHeight : getHeight();
        final boolean creating = !mSurfaceCreated;
        final boolean sizeChanged = w != mSurfaceWidth || h != mSurfaceHeight;
        final boolean formatChanged = mFormat != mRequestedFormat;
        if (!creating && !sizeChanged && !formatChanged) return;
        mSurfaceWidth = w;
        mSurfaceHeight = h;
        mFormat = mRequestedFormat;
        mSurfaceFrame.set(0, 0, w, h);
        mQueue.setBuffers(w, h, PixelFormat.formatHasAlpha(mFormat) ? false : true);
        try {
            mIsCreating = true;
            final SurfaceHolder.Callback[] callbacks = getSurfaceCallbacks();
            if (creating) {
                mSurfaceCreated = true;
                for (SurfaceHolder.Callback c : callbacks) c.surfaceCreated(mSurfaceHolder);
            }
            for (SurfaceHolder.Callback c : callbacks) c.surfaceChanged(mSurfaceHolder, mFormat, w, h);
            for (SurfaceHolder.Callback c : callbacks) {
                if (c instanceof SurfaceHolder.Callback2) ((SurfaceHolder.Callback2) c).surfaceRedrawNeeded(mSurfaceHolder);
            }
        } finally {
            mIsCreating = false;
        }
        invalidate();
    }

    private final SurfaceHolder mSurfaceHolder = new SurfaceHolder() {
        public boolean isCreating() { return mIsCreating; }

        public void addCallback(Callback callback) {
            synchronized (mCallbacks) {
                if (!mCallbacks.contains(callback)) mCallbacks.add(callback);
            }
        }

        public void removeCallback(Callback callback) {
            synchronized (mCallbacks) {
                mCallbacks.remove(callback);
            }
        }

        public void setFixedSize(int width, int height) {
            if (mRequestedWidth != width || mRequestedHeight != height) {
                mRequestedWidth = width;
                mRequestedHeight = height;
                post(new Runnable() {
                    public void run() {
                        requestLayout();
                        updateSurface();
                    }
                });
            }
        }

        public void setSizeFromLayout() {
            if (mRequestedWidth != -1 || mRequestedHeight != -1) {
                mRequestedWidth = mRequestedHeight = -1;
                post(new Runnable() {
                    public void run() { requestLayout(); }
                });
            }
        }

        public void setFormat(int format) {
            if (format == PixelFormat.OPAQUE) format = PixelFormat.RGB_565;
            mRequestedFormat = format;
            post(new Runnable() {
                public void run() { updateSurface(); }
            });
        }

        @Deprecated
        public void setType(int type) {}

        public void setKeepScreenOn(final boolean screenOn) {
            post(new Runnable() {
                public void run() { SurfaceView.this.setKeepScreenOn(screenOn); }
            });
        }

        public Canvas lockCanvas() { return internalLockCanvas(null); }

        public Canvas lockCanvas(Rect inOutDirty) { return internalLockCanvas(inOutDirty); }

        private Canvas internalLockCanvas(Rect dirty) {
            if (!mSurfaceCreated || !mSurface.isValid()) return null;
            try {
                return mSurface.lockCanvas(dirty);
            } catch (IllegalStateException e) {
                return null;
            }
        }

        public void unlockCanvasAndPost(Canvas canvas) { mSurface.unlockCanvasAndPost(canvas); }

        public Surface getSurface() { return mSurface; }

        public Rect getSurfaceFrame() { return mSurfaceFrame; }
    };
}
