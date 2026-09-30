package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import com.android.internal.policy.PhoneWindow;

public class Activity extends ContextThemeWrapper {
    public static final int RESULT_CANCELED = 0;
    public static final int RESULT_OK = -1;
    public static final int RESULT_FIRST_USER = 1;

    private static final int ST_NONE = 0;
    private static final int ST_CREATED = 1;
    private static final int ST_STARTED = 2;
    private static final int ST_RESUMED = 3;
    private static final int ST_STOPPED = 4;
    private static final int ST_DESTROYED = 5;

    private Application mApplication;
    private Intent mIntent;
    private ActivityInfo mInfo;
    private Window mWindow;
    private boolean mWindowAdded;
    private boolean mFinished;
    private int mState;
    private int mResultCode = RESULT_CANCELED;
    private Intent mResultData;

    public Activity() { super(); }

    void attach(Context context, ActivityInfo info, Intent intent, Application application) {
        attachBaseContext(context);
        mApplication = application;
        mInfo = info;
        mIntent = intent != null ? intent : new Intent();
        mWindow = new PhoneWindow(this);
        if (info != null) {
            int theme = info.getThemeResource();
            if (theme != 0) setTheme(theme);
        }
        getWindow().getDecorView().setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    onBackPressed();
                    return true;
                }
                return false;
            }
        });
    }

    public final Application getApplication() { return mApplication; }
    public Intent getIntent() { return mIntent; }
    public Window getWindow() {
        if (mWindow == null) mWindow = new PhoneWindow(this);
        return mWindow;
    }
    public WindowManager getWindowManager() { return getWindow().getWindowManager(); }
    public boolean isFinishing() { return mFinished; }
    public ComponentName getComponentName() {
        return mInfo != null ? new ComponentName(mInfo.packageName, mInfo.name) : null;
    }

    public void setContentView(View view) { getWindow().setContentView(view); }
    public void setContentView(View view, ViewGroup.LayoutParams params) { getWindow().setContentView(view, params); }
    public void setContentView(int layoutResID) { getWindow().setContentView(layoutResID); }
    public void addContentView(View view, ViewGroup.LayoutParams params) { getWindow().addContentView(view, params); }

    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int id) { return (T) getWindow().findViewById(id); }

    public final void setResult(int resultCode) { setResult(resultCode, null); }
    public final void setResult(int resultCode, Intent data) {
        mResultCode = resultCode;
        mResultData = data;
    }

    public void finish() {
        if (mFinished) return;
        mFinished = true;
        ActivityThread.finishActivity(this, mResultCode, mResultData);
    }

    public void onBackPressed() { finish(); }

    public final void runOnUiThread(Runnable action) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) action.run();
        else new android.os.Handler(android.os.Looper.getMainLooper()).post(action);
    }

    @Override
    public void startActivity(Intent intent) { startActivity(intent, null); }

    @Override
    public void startActivity(Intent intent, Bundle options) { startActivityForResult(intent, -1, options); }

    public void startActivityForResult(Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode, null);
    }

    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        ActivityThread.startActivity(this, intent, requestCode);
    }

    protected void onCreate(Bundle savedInstanceState) { onCreate(savedInstanceState, null); }
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {}
    protected void onRestart() {}
    protected void onStart() {}
    protected void onResume() {}
    protected void onPause() {}
    protected void onStop() {}
    protected void onDestroy() {}
    protected void onNewIntent(Intent intent) { mIntent = intent; }
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {}

    @Override
    public Object getSystemService(String name) {
        if (WINDOW_SERVICE.equals(name)) return getWindowManager();
        return super.getSystemService(name);
    }

    void performCreate() {
        if (mState != ST_NONE) return;
        onCreate(null);
        if (mFinished || mState == ST_DESTROYED) return;
        if (mApplication != null) mApplication.dispatchCreated(this, null);
        mState = ST_CREATED;
    }

    void performStart() {
        if (mFinished || mState == ST_DESTROYED) return;
        if (mState != ST_CREATED && mState != ST_STOPPED) return;
        if (mState == ST_STOPPED) {
            onRestart();
            mState = ST_CREATED;
        }
        onStart();
        if (mApplication != null) mApplication.dispatchStarted(this);
        mState = ST_STARTED;
    }

    void performResume() {
        if (mFinished || mState == ST_DESTROYED) return;
        if (mState == ST_STOPPED) performStart();
        if (mState != ST_STARTED) return;
        onResume();
        if (mApplication != null) mApplication.dispatchResumed(this);
        mState = ST_RESUMED;
        makeVisible();
    }

    void performPause() {
        if (mState != ST_RESUMED) return;
        onPause();
        if (mApplication != null) mApplication.dispatchPaused(this);
        mState = ST_STARTED;
    }

    void performStop() {
        if (mState == ST_RESUMED) performPause();
        if (mState != ST_STARTED) return;
        onStop();
        if (mApplication != null) mApplication.dispatchStopped(this);
        mState = ST_STOPPED;
    }

    void performDestroy() {
        if (mState == ST_DESTROYED || mState == ST_NONE) return;
        performStop();
        makeInvisible();
        onDestroy();
        if (mApplication != null) mApplication.dispatchDestroyed(this);
        mState = ST_DESTROYED;
    }

    void deliverResult(int requestCode, int resultCode, Intent data) {
        onActivityResult(requestCode, resultCode, data);
    }

    private void makeVisible() {
        View decor = getWindow().getDecorView();
        if (!mWindowAdded) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.type = WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
            getWindowManager().addView(decor, lp);
            mWindowAdded = true;
        } else {
            decor.invalidate();
        }
    }

    private void makeInvisible() {
        if (!mWindowAdded) return;
        mWindowAdded = false;
        getWindowManager().removeView(getWindow().getDecorView());
    }
}
