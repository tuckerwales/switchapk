package android.opengl;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.Writer;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL;
import javax.microedition.khronos.opengles.GL10;

/**
 * A SurfaceView that renders with OpenGL ES on a dedicated thread (port of
 * AOSP GLSurfaceView: the GLThread state machine, EglHelper and the default
 * config, context and window surface factories). Frames reach the screen
 * through the window surface path described in {@link EGLNative}.
 *
 * When EGL is unavailable (no GL driver on the host), the GL thread logs the
 * failure and the view shows a message instead of crashing the app.
 */
public class GLSurfaceView extends SurfaceView implements SurfaceHolder.Callback2 {
    private static final String TAG = "GLSurfaceView";

    public static final int RENDERMODE_WHEN_DIRTY = 0;
    public static final int RENDERMODE_CONTINUOUSLY = 1;
    public static final int DEBUG_CHECK_GL_ERROR = 1;
    public static final int DEBUG_LOG_GL_CALLS = 2;

    private static final GLThreadManager sGLThreadManager = new GLThreadManager();

    private final WeakReference<GLSurfaceView> mThisWeakRef = new WeakReference<GLSurfaceView>(this);
    private GLThread mGLThread;
    private Renderer mRenderer;
    private boolean mDetached;
    private EGLConfigChooser mEGLConfigChooser;
    private EGLContextFactory mEGLContextFactory;
    private EGLWindowSurfaceFactory mEGLWindowSurfaceFactory;
    private GLWrapper mGLWrapper;
    private int mDebugFlags;
    private int mEGLContextClientVersion;
    private boolean mPreserveEGLContextOnPause;
    private volatile String mFailure;

    public GLSurfaceView(Context context) {
        super(context);
        init();
    }

    public GLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        getHolder().addCallback(this);
    }

    public void setGLWrapper(GLWrapper glWrapper) {
        mGLWrapper = glWrapper;
    }

    public void setDebugFlags(int debugFlags) {
        mDebugFlags = debugFlags;
    }

    public int getDebugFlags() {
        return mDebugFlags;
    }

    public void setPreserveEGLContextOnPause(boolean preserveOnPause) {
        mPreserveEGLContextOnPause = preserveOnPause;
    }

    public boolean getPreserveEGLContextOnPause() {
        return mPreserveEGLContextOnPause;
    }

    public void setRenderer(Renderer renderer) {
        checkRenderThreadState();
        if (mEGLConfigChooser == null) mEGLConfigChooser = new SimpleEGLConfigChooser(true);
        if (mEGLContextFactory == null) mEGLContextFactory = new DefaultContextFactory();
        if (mEGLWindowSurfaceFactory == null) mEGLWindowSurfaceFactory = new DefaultWindowSurfaceFactory();
        mRenderer = renderer;
        mGLThread = new GLThread(mThisWeakRef);
        mGLThread.start();
    }

    public void setEGLContextFactory(EGLContextFactory factory) {
        checkRenderThreadState();
        mEGLContextFactory = factory;
    }

    public void setEGLWindowSurfaceFactory(EGLWindowSurfaceFactory factory) {
        checkRenderThreadState();
        mEGLWindowSurfaceFactory = factory;
    }

    public void setEGLConfigChooser(EGLConfigChooser configChooser) {
        checkRenderThreadState();
        mEGLConfigChooser = configChooser;
    }

    public void setEGLConfigChooser(boolean needDepth) {
        setEGLConfigChooser(new SimpleEGLConfigChooser(needDepth));
    }

    public void setEGLConfigChooser(int redSize, int greenSize, int blueSize, int alphaSize, int depthSize,
            int stencilSize) {
        setEGLConfigChooser(new ComponentSizeChooser(redSize, greenSize, blueSize, alphaSize, depthSize, stencilSize));
    }

    public void setEGLContextClientVersion(int version) {
        checkRenderThreadState();
        mEGLContextClientVersion = version;
    }

    public void setRenderMode(int renderMode) {
        mGLThread.setRenderMode(renderMode);
    }

    public int getRenderMode() {
        return mGLThread.getRenderMode();
    }

    public void requestRender() {
        mGLThread.requestRender();
    }

    public void surfaceCreated(SurfaceHolder holder) {
        mGLThread.surfaceCreated();
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        mGLThread.surfaceDestroyed();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        mGLThread.onWindowResize(w, h);
    }

    @Override
    public void surfaceRedrawNeededAsync(SurfaceHolder holder, Runnable finishDrawing) {
        if (mGLThread != null) mGLThread.requestRenderAndNotify(finishDrawing);
    }

    @Deprecated
    @Override
    public void surfaceRedrawNeeded(SurfaceHolder holder) {
        // Since we are part of the framework we know only surfaceRedrawNeededAsync will be called.
    }

    public void onPause() {
        mGLThread.onPause();
    }

    public void onResume() {
        mGLThread.onResume();
    }

    public void queueEvent(Runnable r) {
        mGLThread.queueEvent(r);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mDetached && (mRenderer != null)) {
            int renderMode = RENDERMODE_CONTINUOUSLY;
            if (mGLThread != null) renderMode = mGLThread.getRenderMode();
            mGLThread = new GLThread(mThisWeakRef);
            if (renderMode != RENDERMODE_CONTINUOUSLY) mGLThread.setRenderMode(renderMode);
            mGLThread.start();
        }
        mDetached = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mGLThread != null) mGLThread.requestExitAndWait();
        mDetached = true;
        super.onDetachedFromWindow();
    }

    @Override
    protected void dispatchDraw(android.graphics.Canvas canvas) {
        // SurfaceView skips draw(), so the frame (and any failure message) comes through here
        super.dispatchDraw(canvas);
        String failure = mFailure;
        if (failure != null) {
            // EGL is unavailable: say so instead of leaving a black hole
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            canvas.drawColor(0xff202020);
            p.setColor(0xffffffff);
            p.setTextSize(Math.max(12f, getResources().getDisplayMetrics().scaledDensity * 14f));
            p.setTextAlign(android.graphics.Paint.Align.CENTER);
            float line = p.getTextSize() * 1.4f;
            canvas.drawText("OpenGL ES unavailable", getWidth() / 2f, getHeight() / 2f - line / 2, p);
            p.setColor(0xffb0b0b0);
            canvas.drawText(failure, getWidth() / 2f, getHeight() / 2f + line / 2, p);
        }
    }

    void reportFailure(final String what) {
        mFailure = what;
        post(new Runnable() {
            public void run() {
                invalidate();
            }
        });
    }

    // ----------------------------------------------------------------------

    public interface GLWrapper {
        GL wrap(GL gl);
    }

    public interface Renderer {
        void onSurfaceCreated(GL10 gl, EGLConfig config);

        void onSurfaceChanged(GL10 gl, int width, int height);

        void onDrawFrame(GL10 gl);
    }

    public interface EGLContextFactory {
        EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig);

        void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context);
    }

    private class DefaultContextFactory implements EGLContextFactory {
        private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            int[] attribList = {EGL_CONTEXT_CLIENT_VERSION, mEGLContextClientVersion, EGL10.EGL_NONE};
            return egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT,
                    mEGLContextClientVersion != 0 ? attribList : null);
        }

        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            if (!egl.eglDestroyContext(display, context)) {
                Log.e("DefaultContextFactory", "display:" + display + " context: " + context);
                EglHelper.throwEglException("eglDestroyContex", egl.eglGetError());
            }
        }
    }

    public interface EGLWindowSurfaceFactory {
        EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display, EGLConfig config, Object nativeWindow);

        void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface);
    }

    private static class DefaultWindowSurfaceFactory implements EGLWindowSurfaceFactory {
        public EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display, EGLConfig config, Object nativeWindow) {
            EGLSurface result = null;
            try {
                result = egl.eglCreateWindowSurface(display, config, nativeWindow, null);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "eglCreateWindowSurface", e);
            }
            return result;
        }

        public void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface) {
            egl.eglDestroySurface(display, surface);
        }
    }

    public interface EGLConfigChooser {
        EGLConfig chooseConfig(EGL10 egl, EGLDisplay display);
    }

    private abstract class BaseConfigChooser implements EGLConfigChooser {
        protected int[] mConfigSpec;

        BaseConfigChooser(int[] configSpec) {
            mConfigSpec = filterConfigSpec(configSpec);
        }

        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[] numConfig = new int[1];
            if (!egl.eglChooseConfig(display, mConfigSpec, null, 0, numConfig)) {
                throw new IllegalArgumentException("eglChooseConfig failed");
            }
            int numConfigs = numConfig[0];
            if (numConfigs <= 0) throw new IllegalArgumentException("No configs match configSpec");
            EGLConfig[] configs = new EGLConfig[numConfigs];
            if (!egl.eglChooseConfig(display, mConfigSpec, configs, numConfigs, numConfig)) {
                throw new IllegalArgumentException("eglChooseConfig#2 failed");
            }
            EGLConfig config = chooseConfig(egl, display, configs);
            if (config == null) throw new IllegalArgumentException("No config chosen");
            return config;
        }

        abstract EGLConfig chooseConfig(EGL10 egl, EGLDisplay display, EGLConfig[] configs);

        private int[] filterConfigSpec(int[] configSpec) {
            if (mEGLContextClientVersion != 2 && mEGLContextClientVersion != 3) return configSpec;
            // We know none of the subclasses define EGL_RENDERABLE_TYPE. And we know the configSpec is well formed.
            int len = configSpec.length;
            int[] newConfigSpec = new int[len + 2];
            System.arraycopy(configSpec, 0, newConfigSpec, 0, len - 1);
            newConfigSpec[len - 1] = EGL10.EGL_RENDERABLE_TYPE;
            if (mEGLContextClientVersion == 2) {
                newConfigSpec[len] = EGL14.EGL_OPENGL_ES2_BIT;
            } else {
                newConfigSpec[len] = EGLExt.EGL_OPENGL_ES3_BIT_KHR;
            }
            newConfigSpec[len + 1] = EGL10.EGL_NONE;
            return newConfigSpec;
        }
    }

    private class ComponentSizeChooser extends BaseConfigChooser {
        private final int[] mValue;
        protected int mRedSize;
        protected int mGreenSize;
        protected int mBlueSize;
        protected int mAlphaSize;
        protected int mDepthSize;
        protected int mStencilSize;

        ComponentSizeChooser(int redSize, int greenSize, int blueSize, int alphaSize, int depthSize,
                int stencilSize) {
            super(new int[] {EGL10.EGL_RED_SIZE, redSize, EGL10.EGL_GREEN_SIZE, greenSize, EGL10.EGL_BLUE_SIZE,
                    blueSize, EGL10.EGL_ALPHA_SIZE, alphaSize, EGL10.EGL_DEPTH_SIZE, depthSize,
                    EGL10.EGL_STENCIL_SIZE, stencilSize, EGL10.EGL_NONE});
            mValue = new int[1];
            mRedSize = redSize;
            mGreenSize = greenSize;
            mBlueSize = blueSize;
            mAlphaSize = alphaSize;
            mDepthSize = depthSize;
            mStencilSize = stencilSize;
        }

        @Override
        EGLConfig chooseConfig(EGL10 egl, EGLDisplay display, EGLConfig[] configs) {
            for (EGLConfig config : configs) {
                int d = findConfigAttrib(egl, display, config, EGL10.EGL_DEPTH_SIZE, 0);
                int s = findConfigAttrib(egl, display, config, EGL10.EGL_STENCIL_SIZE, 0);
                if ((d >= mDepthSize) && (s >= mStencilSize)) {
                    int r = findConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE, 0);
                    int g = findConfigAttrib(egl, display, config, EGL10.EGL_GREEN_SIZE, 0);
                    int b = findConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE, 0);
                    int a = findConfigAttrib(egl, display, config, EGL10.EGL_ALPHA_SIZE, 0);
                    if ((r == mRedSize) && (g == mGreenSize) && (b == mBlueSize) && (a == mAlphaSize)) {
                        return config;
                    }
                }
            }
            return null;
        }

        private int findConfigAttrib(EGL10 egl, EGLDisplay display, EGLConfig config, int attribute,
                int defaultValue) {
            if (egl.eglGetConfigAttrib(display, config, attribute, mValue)) return mValue[0];
            return defaultValue;
        }
    }

    /** This class will choose a RGB_888 surface with or without a depth buffer. */
    private class SimpleEGLConfigChooser extends ComponentSizeChooser {
        public SimpleEGLConfigChooser(boolean withDepthBuffer) {
            super(8, 8, 8, 0, withDepthBuffer ? 16 : 0, 0);
        }
    }

    /** An EGL helper class. */
    private static class EglHelper {
        private final WeakReference<GLSurfaceView> mGLSurfaceViewWeakRef;
        EGL10 mEgl;
        EGLDisplay mEglDisplay;
        EGLSurface mEglSurface;
        EGLConfig mEglConfig;
        EGLContext mEglContext;

        EglHelper(WeakReference<GLSurfaceView> glSurfaceViewWeakRef) {
            mGLSurfaceViewWeakRef = glSurfaceViewWeakRef;
        }

        /** Initialize EGL for a given configuration spec. */
        public void start() {
            mEgl = (EGL10) EGLContext.getEGL();
            mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (mEglDisplay == EGL10.EGL_NO_DISPLAY) throw new RuntimeException("eglGetDisplay failed");
            int[] version = new int[2];
            if (!mEgl.eglInitialize(mEglDisplay, version)) throw new RuntimeException("eglInitialize failed");
            GLSurfaceView view = mGLSurfaceViewWeakRef.get();
            if (view == null) {
                mEglConfig = null;
                mEglContext = null;
            } else {
                mEglConfig = view.mEGLConfigChooser.chooseConfig(mEgl, mEglDisplay);
                mEglContext = view.mEGLContextFactory.createContext(mEgl, mEglDisplay, mEglConfig);
            }
            if (mEglContext == null || mEglContext == EGL10.EGL_NO_CONTEXT) {
                mEglContext = null;
                throwEglException("createContext");
            }
            mEglSurface = null;
        }

        /** Create an egl surface for the current SurfaceHolder surface. Returns true on success. */
        public boolean createSurface() {
            if (mEgl == null) throw new RuntimeException("egl not initialized");
            if (mEglDisplay == null) throw new RuntimeException("eglDisplay not initialized");
            if (mEglConfig == null) throw new RuntimeException("mEglConfig not initialized");
            // The window size has changed, so we need to create a new surface.
            destroySurfaceImp();
            GLSurfaceView view = mGLSurfaceViewWeakRef.get();
            if (view != null) {
                mEglSurface = view.mEGLWindowSurfaceFactory.createWindowSurface(mEgl, mEglDisplay, mEglConfig,
                        view.getHolder());
            } else {
                mEglSurface = null;
            }
            if (mEglSurface == null || mEglSurface == EGL10.EGL_NO_SURFACE) {
                int error = mEgl.eglGetError();
                if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
                    Log.e("EglHelper", "createWindowSurface returned EGL_BAD_NATIVE_WINDOW.");
                }
                return false;
            }
            // Before we can issue GL commands, we need to make sure the context is current and bound to a surface.
            if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
                logEglErrorAsWarning("EGLHelper", "eglMakeCurrent", mEgl.eglGetError());
                return false;
            }
            return true;
        }

        /** Create a GL object for the current EGL context. */
        GL createGL() {
            GL gl = mEglContext.getGL();
            GLSurfaceView view = mGLSurfaceViewWeakRef.get();
            if (view != null && view.mGLWrapper != null) gl = view.mGLWrapper.wrap(gl);
            return gl;
        }

        /** Display the current render surface. Returns the EGL error code from eglSwapBuffers. */
        public int swap() {
            if (!mEgl.eglSwapBuffers(mEglDisplay, mEglSurface)) return mEgl.eglGetError();
            return EGL10.EGL_SUCCESS;
        }

        public void destroySurface() {
            destroySurfaceImp();
        }

        private void destroySurfaceImp() {
            if (mEglSurface != null && mEglSurface != EGL10.EGL_NO_SURFACE) {
                mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                GLSurfaceView view = mGLSurfaceViewWeakRef.get();
                if (view != null) view.mEGLWindowSurfaceFactory.destroySurface(mEgl, mEglDisplay, mEglSurface);
                mEglSurface = null;
            }
        }

        public void finish() {
            if (mEglContext != null) {
                GLSurfaceView view = mGLSurfaceViewWeakRef.get();
                if (view != null) view.mEGLContextFactory.destroyContext(mEgl, mEglDisplay, mEglContext);
                mEglContext = null;
            }
            if (mEglDisplay != null) {
                mEgl.eglTerminate(mEglDisplay);
                mEglDisplay = null;
            }
        }

        private void throwEglException(String function) {
            throwEglException(function, mEgl.eglGetError());
        }

        public static void throwEglException(String function, int error) {
            String message = formatEglError(function, error);
            Log.e("EglHelper", "throwEglException tid=" + Thread.currentThread().getId() + " " + message);
            throw new RuntimeException(message);
        }

        public static void logEglErrorAsWarning(String tag, String function, int error) {
            Log.w(tag, formatEglError(function, error));
        }

        public static String formatEglError(String function, int error) {
            return function + " failed: " + GLUtils.getEGLErrorString(error);
        }
    }

    /**
     * A generic GL Thread. Takes care of initializing EGL and GL. Delegates to a Renderer instance to do the actual
     * drawing. Can be configured to render continuously or on request.
     */
    static class GLThread extends Thread {
        // Once the thread is started, all accesses to the following member variables are protected by the
        // sGLThreadManager monitor
        private boolean mShouldExit;
        private boolean mExited;
        private boolean mRequestPaused;
        private boolean mPaused;
        private boolean mHasSurface;
        private boolean mSurfaceIsBad;
        private boolean mWaitingForSurface;
        private boolean mHaveEglContext;
        private boolean mHaveEglSurface;
        private boolean mFinishedCreatingEglSurface;
        private boolean mShouldReleaseEglContext;
        private int mWidth;
        private int mHeight;
        private int mRenderMode;
        private boolean mRequestRender;
        private boolean mWantRenderNotification;
        private boolean mRenderComplete;
        private final ArrayList<Runnable> mEventQueue = new ArrayList<Runnable>();
        private boolean mSizeChanged = true;
        private Runnable mFinishDrawingRunnable = null;
        // End of member variables protected by the sGLThreadManager monitor.

        private EglHelper mEglHelper;
        private final WeakReference<GLSurfaceView> mGLSurfaceViewWeakRef;

        GLThread(WeakReference<GLSurfaceView> glSurfaceViewWeakRef) {
            super();
            mWidth = 0;
            mHeight = 0;
            mRequestRender = true;
            mRenderMode = RENDERMODE_CONTINUOUSLY;
            mWantRenderNotification = false;
            mGLSurfaceViewWeakRef = glSurfaceViewWeakRef;
        }

        @Override
        public void run() {
            setName("GLThread " + getId());
            try {
                guardedRun();
            } catch (InterruptedException e) {
                // fall thru and exit normally
            } catch (RuntimeException e) {
                // EGL could not be brought up (no GL driver): report it on the view instead of crashing
                Log.e(TAG, "GL thread failed", e);
                GLSurfaceView view = mGLSurfaceViewWeakRef.get();
                if (view != null) view.reportFailure(e.getMessage() != null ? e.getMessage() : e.toString());
            } finally {
                sGLThreadManager.threadExiting(this);
            }
        }

        /* This private method should only be called inside a synchronized(sGLThreadManager) block. */
        private void stopEglSurfaceLocked() {
            if (mHaveEglSurface) {
                mHaveEglSurface = false;
                mEglHelper.destroySurface();
            }
        }

        /* This private method should only be called inside a synchronized(sGLThreadManager) block. */
        private void stopEglContextLocked() {
            if (mHaveEglContext) {
                mEglHelper.finish();
                mHaveEglContext = false;
                sGLThreadManager.releaseEglContextLocked(this);
            }
        }

        private void guardedRun() throws InterruptedException {
            mEglHelper = new EglHelper(mGLSurfaceViewWeakRef);
            mHaveEglContext = false;
            mHaveEglSurface = false;
            mWantRenderNotification = false;
            try {
                GL10 gl = null;
                boolean createEglContext = false;
                boolean createEglSurface = false;
                boolean createGlInterface = false;
                boolean lostEglContext = false;
                boolean sizeChanged = false;
                boolean wantRenderNotification = false;
                boolean doRenderNotification = false;
                boolean askedToReleaseEglContext = false;
                int w = 0;
                int h = 0;
                Runnable event = null;
                Runnable finishDrawingRunnable = null;

                while (true) {
                    synchronized (sGLThreadManager) {
                        while (true) {
                            if (mShouldExit) return;
                            if (!mEventQueue.isEmpty()) {
                                event = mEventQueue.remove(0);
                                break;
                            }
                            // Update the pause state.
                            boolean pausing = false;
                            if (mPaused != mRequestPaused) {
                                pausing = mRequestPaused;
                                mPaused = mRequestPaused;
                                sGLThreadManager.notifyAll();
                            }
                            // Do we need to give up the EGL context?
                            if (mShouldReleaseEglContext) {
                                stopEglSurfaceLocked();
                                stopEglContextLocked();
                                mShouldReleaseEglContext = false;
                                askedToReleaseEglContext = true;
                            }
                            // Have we lost the EGL context?
                            if (lostEglContext) {
                                stopEglSurfaceLocked();
                                stopEglContextLocked();
                                lostEglContext = false;
                            }
                            // When pausing, release the EGL surface:
                            if (pausing && mHaveEglSurface) stopEglSurfaceLocked();
                            // When pausing, optionally release the EGL Context:
                            if (pausing && mHaveEglContext) {
                                GLSurfaceView view = mGLSurfaceViewWeakRef.get();
                                boolean preserveEglContextOnPause = view != null && view.mPreserveEGLContextOnPause;
                                if (!preserveEglContextOnPause) stopEglContextLocked();
                            }
                            // Have we lost the SurfaceView surface?
                            if ((!mHasSurface) && (!mWaitingForSurface)) {
                                if (mHaveEglSurface) stopEglSurfaceLocked();
                                mWaitingForSurface = true;
                                mSurfaceIsBad = false;
                                sGLThreadManager.notifyAll();
                            }
                            // Have we acquired the surface view surface?
                            if (mHasSurface && mWaitingForSurface) {
                                mWaitingForSurface = false;
                                sGLThreadManager.notifyAll();
                            }
                            if (doRenderNotification) {
                                mWantRenderNotification = false;
                                doRenderNotification = false;
                                mRenderComplete = true;
                                sGLThreadManager.notifyAll();
                            }
                            if (mFinishDrawingRunnable != null) {
                                finishDrawingRunnable = mFinishDrawingRunnable;
                                mFinishDrawingRunnable = null;
                            }
                            // Ready to draw?
                            if (readyToDraw()) {
                                // If we don't have an EGL context, try to acquire one.
                                if (!mHaveEglContext) {
                                    if (askedToReleaseEglContext) {
                                        askedToReleaseEglContext = false;
                                    } else {
                                        try {
                                            mEglHelper.start();
                                        } catch (RuntimeException t) {
                                            sGLThreadManager.releaseEglContextLocked(this);
                                            throw t;
                                        }
                                        mHaveEglContext = true;
                                        createEglContext = true;
                                        sGLThreadManager.notifyAll();
                                    }
                                }
                                if (mHaveEglContext && !mHaveEglSurface) {
                                    mHaveEglSurface = true;
                                    createEglSurface = true;
                                    createGlInterface = true;
                                    sizeChanged = true;
                                }
                                if (mHaveEglSurface) {
                                    if (mSizeChanged) {
                                        sizeChanged = true;
                                        w = mWidth;
                                        h = mHeight;
                                        mWantRenderNotification = true;
                                        // Destroy and recreate the EGL surface.
                                        createEglSurface = true;
                                        mSizeChanged = false;
                                    }
                                    mRequestRender = false;
                                    sGLThreadManager.notifyAll();
                                    if (mWantRenderNotification) wantRenderNotification = true;
                                    break;
                                }
                            } else {
                                if (finishDrawingRunnable != null) {
                                    Log.w(TAG, "Warning, !readyToDraw() but waiting for draw finished! "
                                            + "Early reporting draw finished.");
                                    finishDrawingRunnable.run();
                                    finishDrawingRunnable = null;
                                }
                            }
                            // By design, this is the only place in a GLThread thread where we wait().
                            sGLThreadManager.wait();
                        }
                    } // end of synchronized(sGLThreadManager)

                    if (event != null) {
                        event.run();
                        event = null;
                        continue;
                    }

                    if (createEglSurface) {
                        if (mEglHelper.createSurface()) {
                            synchronized (sGLThreadManager) {
                                mFinishedCreatingEglSurface = true;
                                sGLThreadManager.notifyAll();
                            }
                        } else {
                            synchronized (sGLThreadManager) {
                                mFinishedCreatingEglSurface = true;
                                mSurfaceIsBad = true;
                                sGLThreadManager.notifyAll();
                            }
                            continue;
                        }
                        createEglSurface = false;
                    }

                    if (createGlInterface) {
                        gl = (GL10) mEglHelper.createGL();
                        createGlInterface = false;
                    }

                    if (createEglContext) {
                        GLSurfaceView view = mGLSurfaceViewWeakRef.get();
                        if (view != null) view.mRenderer.onSurfaceCreated(gl, mEglHelper.mEglConfig);
                        createEglContext = false;
                    }

                    if (sizeChanged) {
                        GLSurfaceView view = mGLSurfaceViewWeakRef.get();
                        if (view != null) view.mRenderer.onSurfaceChanged(gl, w, h);
                        sizeChanged = false;
                    }

                    {
                        GLSurfaceView view = mGLSurfaceViewWeakRef.get();
                        if (view != null) {
                            view.mRenderer.onDrawFrame(gl);
                            if (finishDrawingRunnable != null) {
                                finishDrawingRunnable.run();
                                finishDrawingRunnable = null;
                            }
                        }
                    }
                    int swapError = mEglHelper.swap();
                    switch (swapError) {
                        case EGL10.EGL_SUCCESS:
                            break;
                        case EGL11.EGL_CONTEXT_LOST:
                            lostEglContext = true;
                            break;
                        default:
                            // Other errors typically mean that the current surface is bad, probably because the
                            // SurfaceView surface has been destroyed, but we haven't been notified yet.
                            EglHelper.logEglErrorAsWarning("GLThread", "eglSwapBuffers", swapError);
                            synchronized (sGLThreadManager) {
                                mSurfaceIsBad = true;
                                sGLThreadManager.notifyAll();
                            }
                            break;
                    }

                    if (wantRenderNotification) {
                        doRenderNotification = true;
                        wantRenderNotification = false;
                    }
                }
            } finally {
                // clean-up everything...
                synchronized (sGLThreadManager) {
                    stopEglSurfaceLocked();
                    stopEglContextLocked();
                }
            }
        }

        public boolean ableToDraw() {
            return mHaveEglContext && mHaveEglSurface && readyToDraw();
        }

        private boolean readyToDraw() {
            return (!mPaused) && mHasSurface && (!mSurfaceIsBad) && (mWidth > 0) && (mHeight > 0)
                    && (mRequestRender || (mRenderMode == RENDERMODE_CONTINUOUSLY));
        }

        public void setRenderMode(int renderMode) {
            if (!((RENDERMODE_WHEN_DIRTY <= renderMode) && (renderMode <= RENDERMODE_CONTINUOUSLY))) {
                throw new IllegalArgumentException("renderMode");
            }
            synchronized (sGLThreadManager) {
                mRenderMode = renderMode;
                sGLThreadManager.notifyAll();
            }
        }

        public int getRenderMode() {
            synchronized (sGLThreadManager) {
                return mRenderMode;
            }
        }

        public void requestRender() {
            synchronized (sGLThreadManager) {
                mRequestRender = true;
                sGLThreadManager.notifyAll();
            }
        }

        public void requestRenderAndNotify(Runnable finishDrawing) {
            synchronized (sGLThreadManager) {
                // If we are already on the GL thread, this means a client callback has caused reentrancy, for
                // example via updating the SurfaceView parameters. We will return to the client rendering code,
                // so here we don't need to do anything.
                if (Thread.currentThread() == this) return;
                mWantRenderNotification = true;
                mRequestRender = true;
                mRenderComplete = false;
                mFinishDrawingRunnable = finishDrawing;
                sGLThreadManager.notifyAll();
            }
        }

        public void surfaceCreated() {
            synchronized (sGLThreadManager) {
                mHasSurface = true;
                mFinishedCreatingEglSurface = false;
                sGLThreadManager.notifyAll();
                while (mWaitingForSurface && !mFinishedCreatingEglSurface && !mExited) {
                    try {
                        sGLThreadManager.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void surfaceDestroyed() {
            synchronized (sGLThreadManager) {
                mHasSurface = false;
                sGLThreadManager.notifyAll();
                while ((!mWaitingForSurface) && (!mExited)) {
                    try {
                        sGLThreadManager.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void onPause() {
            synchronized (sGLThreadManager) {
                mRequestPaused = true;
                sGLThreadManager.notifyAll();
                while ((!mExited) && (!mPaused)) {
                    try {
                        sGLThreadManager.wait();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void onResume() {
            synchronized (sGLThreadManager) {
                mRequestPaused = false;
                mRequestRender = true;
                mRenderComplete = false;
                sGLThreadManager.notifyAll();
                while ((!mExited) && mPaused && (!mRenderComplete)) {
                    try {
                        sGLThreadManager.wait();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void onWindowResize(int w, int h) {
            synchronized (sGLThreadManager) {
                mWidth = w;
                mHeight = h;
                mSizeChanged = true;
                mRequestRender = true;
                mRenderComplete = false;
                // If we are already on the GL thread, this means a client callback has caused reentrancy, for
                // example via updating the SurfaceView parameters. We need to process the size change eventually
                // though and update our EGLSurface. So we set the parameters and return so they can be processed
                // on our next iteration.
                if (Thread.currentThread() == this) return;
                sGLThreadManager.notifyAll();
                // Wait for thread to react to resize and render a frame
                while (!mExited && !mPaused && !mRenderComplete && ableToDraw()) {
                    try {
                        sGLThreadManager.wait();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void requestExitAndWait() {
            // don't call this from GLThread thread or it is a guaranteed deadlock!
            synchronized (sGLThreadManager) {
                mShouldExit = true;
                sGLThreadManager.notifyAll();
                while (!mExited) {
                    try {
                        sGLThreadManager.wait();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        public void requestReleaseEglContextLocked() {
            mShouldReleaseEglContext = true;
            sGLThreadManager.notifyAll();
        }

        /** Queue an "event" to be run on the GL rendering thread. */
        public void queueEvent(Runnable r) {
            if (r == null) throw new IllegalArgumentException("r must not be null");
            synchronized (sGLThreadManager) {
                mEventQueue.add(r);
                sGLThreadManager.notifyAll();
            }
        }
    }

    static class LogWriter extends Writer {
        private final StringBuilder mBuilder = new StringBuilder();

        @Override
        public void close() {
            flushBuilder();
        }

        @Override
        public void flush() {
            flushBuilder();
        }

        @Override
        public void write(char[] buf, int offset, int count) {
            for (int i = 0; i < count; i++) {
                char c = buf[offset + i];
                if (c == '\n') {
                    flushBuilder();
                } else {
                    mBuilder.append(c);
                }
            }
        }

        private void flushBuilder() {
            if (mBuilder.length() > 0) {
                Log.v("GLSurfaceView", mBuilder.toString());
                mBuilder.delete(0, mBuilder.length());
            }
        }
    }

    private void checkRenderThreadState() {
        if (mGLThread != null) {
            throw new IllegalStateException("setRenderer has already been called for this instance.");
        }
    }

    private static class GLThreadManager {
        public synchronized void threadExiting(GLThread thread) {
            thread.mExited = true;
            notifyAll();
        }

        /* Releases the EGL context. Requires that we are already in the sGLThreadManager monitor when this is
         * called. */
        public void releaseEglContextLocked(GLThread thread) {
            notifyAll();
        }
    }
}
