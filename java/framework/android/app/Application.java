package android.app;

import android.content.ComponentCallbacks;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Process;
import java.util.ArrayList;

public class Application extends ContextWrapper implements ComponentCallbacks2 {
    private final ArrayList<ActivityLifecycleCallbacks> mCallbacks = new ArrayList<ActivityLifecycleCallbacks>();
    private final ArrayList<ComponentCallbacks> mComponentCallbacks = new ArrayList<ComponentCallbacks>();

    public Application() { super(null); }

    /** framework-internal. Called by ActivityThread before onCreate. */
    void attach(Context context) { attachBaseContext(context); }

    public void onCreate() {}
    public void onTerminate() {}
    public void onConfigurationChanged(Configuration newConfig) {}
    public void onLowMemory() {}
    public void onTrimMemory(int level) {}

    public static String getProcessName() { return Process.myProcessName(); }

    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        if (callback != null) mCallbacks.add(callback);
    }

    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        mCallbacks.remove(callback);
    }

    @Override
    public void registerComponentCallbacks(ComponentCallbacks callback) {
        if (callback != null) mComponentCallbacks.add(callback);
    }

    @Override
    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        mComponentCallbacks.remove(callback);
    }

    void dispatchCreated(Activity activity, Bundle state) {
        for (ActivityLifecycleCallbacks cb : copy()) cb.onActivityCreated(activity, state);
    }

    void dispatchStarted(Activity activity) {
        for (ActivityLifecycleCallbacks cb : copy()) cb.onActivityStarted(activity);
    }

    void dispatchResumed(Activity activity) {
        for (ActivityLifecycleCallbacks cb : copy()) cb.onActivityResumed(activity);
    }

    void dispatchPaused(Activity activity) {
        for (ActivityLifecycleCallbacks cb : copy()) cb.onActivityPaused(activity);
    }

    void dispatchStopped(Activity activity) {
        for (ActivityLifecycleCallbacks cb : copy()) cb.onActivityStopped(activity);
    }

    void dispatchDestroyed(Activity activity) {
        for (ActivityLifecycleCallbacks cb : copy()) cb.onActivityDestroyed(activity);
    }

    private ActivityLifecycleCallbacks[] copy() {
        return mCallbacks.toArray(new ActivityLifecycleCallbacks[mCallbacks.size()]);
    }

    public interface ActivityLifecycleCallbacks {
        void onActivityCreated(Activity activity, Bundle savedInstanceState);
        void onActivityStarted(Activity activity);
        void onActivityResumed(Activity activity);
        void onActivityPaused(Activity activity);
        void onActivityStopped(Activity activity);
        void onActivitySaveInstanceState(Activity activity, Bundle outState);
        void onActivityDestroyed(Activity activity);
    }
}
