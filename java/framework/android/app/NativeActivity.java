package android.app;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.MessageQueue;
import android.view.InputQueue;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import java.io.File;

/**
 * An activity implemented in native code (AOSP android.app.NativeActivity).
 * Declare it in the manifest; there is no need to subclass it.
 *
 * <p>PhoneWindow.takeSurface and takeInputQueue do not install a callback, so
 * the content view is a full-bleed {@link SurfaceView}. Its buffer queue is
 * the same one EGL window surfaces post into. The native entry
 * (ANativeActivity_onCreate by default) runs from onCreate, before the
 * surface exists; surface callbacks follow the first draw.
 *
 * <p>Input queues are not delivered yet (ALooper and AInputQueue are still
 * open). The callback methods exist so the class matches the platform API.
 */
public class NativeActivity extends Activity implements SurfaceHolder.Callback2,
        InputQueue.Callback, OnGlobalLayoutListener {
    /**
     * Optional meta-data naming the native shared library to load. If absent,
     * "main" is used.
     */
    public static final String META_DATA_LIB_NAME = "android.app.lib_name";

    /**
     * Optional meta-data naming the entry point in that library. If absent,
     * "ANativeActivity_onCreate" is used.
     */
    public static final String META_DATA_FUNC_NAME = "android.app.func_name";

    private static final String KEY_NATIVE_SAVED_STATE = "android:native_state";

    private SurfaceView mSurfaceView;
    private InputMethodManager mIMM;
    private long mNativeHandle;
    private SurfaceHolder mCurSurfaceHolder;
    private boolean mDestroyed;

    final int[] mLocation = new int[2];
    int mLastContentX;
    int mLastContentY;
    int mLastContentWidth;
    int mLastContentHeight;

    private native long loadNativeCode(String path, String funcname, MessageQueue queue,
            String internalDataPath, String obbPath, String externalDataPath, int sdkVersion,
            AssetManager assetMgr, byte[] savedState, ClassLoader classLoader, String libraryPath);
    private native String getDlError();
    private native void unloadNativeCode(long handle);
    private native void onStartNative(long handle);
    private native void onResumeNative(long handle);
    private native byte[] onSaveInstanceStateNative(long handle);
    private native void onPauseNative(long handle);
    private native void onStopNative(long handle);
    private native void onConfigurationChangedNative(long handle);
    private native void onLowMemoryNative(long handle);
    private native void onWindowFocusChangedNative(long handle, boolean focused);
    private native void onSurfaceCreatedNative(long handle, Surface surface);
    private native void onSurfaceChangedNative(long handle, Surface surface, int format, int width, int height);
    private native void onSurfaceRedrawNeededNative(long handle, Surface surface);
    private native void onSurfaceDestroyedNative(long handle);
    private native void onInputQueueCreatedNative(long handle, long queuePtr);
    private native void onInputQueueDestroyedNative(long handle, long queuePtr);
    private native void onContentRectChangedNative(long handle, int x, int y, int w, int h);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String libname = "main";
        String funcname = "ANativeActivity_onCreate";

        mIMM = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        getWindow().setFormat(PixelFormat.RGBA_8888);
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        mSurfaceView = new SurfaceView(this);
        SurfaceHolder holder = mSurfaceView.getHolder();
        /* RGBA_8888 before the surface exists: the default is RGB_565, and the
         * first updateSurface reads mRequestedFormat. */
        holder.setFormat(PixelFormat.RGBA_8888);
        holder.addCallback(this);
        setContentView(mSurfaceView);
        mSurfaceView.requestFocus();
        mSurfaceView.getViewTreeObserver().addOnGlobalLayoutListener(this);

        try {
            ActivityInfo ai = getPackageManager().getActivityInfo(
                    getIntent().getComponent(), PackageManager.GET_META_DATA);
            if (ai.metaData != null) {
                String ln = ai.metaData.getString(META_DATA_LIB_NAME);
                if (ln != null) libname = ln;
                ln = ai.metaData.getString(META_DATA_FUNC_NAME);
                if (ln != null) funcname = ln;
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Error getting activity info", e);
        }

        ApplicationInfo appInfo = getApplicationInfo();
        String dir = appInfo.nativeLibraryDir;
        if (dir == null) {
            throw new IllegalArgumentException("Unable to find native library " + libname);
        }
        String path = dir + "/lib" + libname + ".so";
        byte[] nativeSavedState = savedInstanceState != null
                ? savedInstanceState.getByteArray(KEY_NATIVE_SAVED_STATE) : null;

        mNativeHandle = loadNativeCode(path, funcname, Looper.myQueue(),
                getAbsolutePath(getFilesDir()), getAbsolutePath(getObbDir()),
                getAbsolutePath(getExternalFilesDir(null)),
                Build.VERSION.SDK_INT, getAssets(), nativeSavedState,
                getClassLoader(), dir);

        if (mNativeHandle == 0) {
            throw new UnsatisfiedLinkError("Unable to load native library \"" + path + "\": " + getDlError());
        }
        super.onCreate(savedInstanceState);
    }

    private static String getAbsolutePath(File file) {
        return file != null ? file.getAbsolutePath() : null;
    }

    @Override
    protected void onDestroy() {
        mDestroyed = true;
        if (mCurSurfaceHolder != null) {
            onSurfaceDestroyedNative(mNativeHandle);
            mCurSurfaceHolder = null;
        }
        unloadNativeCode(mNativeHandle);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        onPauseNative(mNativeHandle);
    }

    @Override
    protected void onResume() {
        super.onResume();
        onResumeNative(mNativeHandle);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        byte[] state = onSaveInstanceStateNative(mNativeHandle);
        if (state != null) outState.putByteArray(KEY_NATIVE_SAVED_STATE, state);
    }

    @Override
    protected void onStart() {
        super.onStart();
        onStartNative(mNativeHandle);
    }

    @Override
    protected void onStop() {
        super.onStop();
        onStopNative(mNativeHandle);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!mDestroyed) onConfigurationChangedNative(mNativeHandle);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (!mDestroyed) onLowMemoryNative(mNativeHandle);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!mDestroyed) onWindowFocusChangedNative(mNativeHandle, hasFocus);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        if (!mDestroyed) {
            mCurSurfaceHolder = holder;
            onSurfaceCreatedNative(mNativeHandle, holder.getSurface());
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (!mDestroyed) {
            mCurSurfaceHolder = holder;
            onSurfaceChangedNative(mNativeHandle, holder.getSurface(), format, width, height);
        }
    }

    public void surfaceRedrawNeeded(SurfaceHolder holder) {
        if (!mDestroyed) {
            mCurSurfaceHolder = holder;
            onSurfaceRedrawNeededNative(mNativeHandle, holder.getSurface());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        mCurSurfaceHolder = null;
        if (!mDestroyed) onSurfaceDestroyedNative(mNativeHandle);
    }

    public void onInputQueueCreated(InputQueue queue) {
        /* ALooper / AInputQueue are not implemented. A queue handed out here
         * would be attached by the app and then crash. */
    }

    public void onInputQueueDestroyed(InputQueue queue) {}

    public void onGlobalLayout() {
        mSurfaceView.getLocationInWindow(mLocation);
        int w = mSurfaceView.getWidth();
        int h = mSurfaceView.getHeight();
        if (mLocation[0] != mLastContentX || mLocation[1] != mLastContentY
                || w != mLastContentWidth || h != mLastContentHeight) {
            mLastContentX = mLocation[0];
            mLastContentY = mLocation[1];
            mLastContentWidth = w;
            mLastContentHeight = h;
            if (!mDestroyed) {
                onContentRectChangedNative(mNativeHandle, mLastContentX,
                        mLastContentY, mLastContentWidth, mLastContentHeight);
            }
        }
    }

    /** framework-internal (hidden in AOSP). Called from ANativeActivity_setWindowFlags. */
    void setWindowFlags(int flags, int mask) {
        getWindow().setFlags(flags, mask);
    }

    /** framework-internal (hidden in AOSP). Called from ANativeActivity_setWindowFormat. */
    void setWindowFormat(int format) {
        getWindow().setFormat(format);
        if (mSurfaceView != null) mSurfaceView.getHolder().setFormat(format);
    }

    /** framework-internal (hidden in AOSP). Called from ANativeActivity_showSoftInput. */
    void showIme(int mode) {
        if (mIMM != null && mSurfaceView != null) mIMM.showSoftInput(mSurfaceView, mode);
    }

    /** framework-internal (hidden in AOSP). Called from ANativeActivity_hideSoftInput. */
    void hideIme(int mode) {
        if (mIMM != null && mSurfaceView != null) {
            mIMM.hideSoftInputFromWindow(mSurfaceView.getWindowToken(), mode);
        }
    }
}
