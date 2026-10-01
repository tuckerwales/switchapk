package android.app;

import android.content.ComponentCallbacks;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Process;
import java.util.ArrayList;

/** Base class for the app's global state (port of AOSP Application). */
public class Application extends ContextWrapper implements ComponentCallbacks2 {
    private final ArrayList<ActivityLifecycleCallbacks> mActivityLifecycleCallbacks =
            new ArrayList<ActivityLifecycleCallbacks>();
    private final ArrayList<ComponentCallbacks> mComponentCallbacks = new ArrayList<ComponentCallbacks>();
    private final ArrayList<OnProvideAssistDataListener> mAssistCallbacks = new ArrayList<OnProvideAssistDataListener>();

    public interface ActivityLifecycleCallbacks {
        default void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {}

        void onActivityCreated(Activity activity, Bundle savedInstanceState);

        default void onActivityPostCreated(Activity activity, Bundle savedInstanceState) {}

        default void onActivityPreStarted(Activity activity) {}

        void onActivityStarted(Activity activity);

        default void onActivityPostStarted(Activity activity) {}

        default void onActivityPreResumed(Activity activity) {}

        void onActivityResumed(Activity activity);

        default void onActivityPostResumed(Activity activity) {}

        default void onActivityPrePaused(Activity activity) {}

        void onActivityPaused(Activity activity);

        default void onActivityPostPaused(Activity activity) {}

        default void onActivityPreStopped(Activity activity) {}

        void onActivityStopped(Activity activity);

        default void onActivityPostStopped(Activity activity) {}

        default void onActivityPreSaveInstanceState(Activity activity, Bundle outState) {}

        void onActivitySaveInstanceState(Activity activity, Bundle outState);

        default void onActivityPostSaveInstanceState(Activity activity, Bundle outState) {}

        default void onActivityPreDestroyed(Activity activity) {}

        void onActivityDestroyed(Activity activity);

        default void onActivityPostDestroyed(Activity activity) {}

        /** Hidden AOSP callback. */
        default void onActivityConfigurationChanged(Activity activity) {}
    }

    public interface OnProvideAssistDataListener {
        void onProvideAssistData(Activity activity, Bundle data);
    }

    public Application() { super(null); }

    /** framework-internal. Called by ActivityThread before onCreate. */
    final void attach(Context context) { attachBaseContext(context); }

    public void onCreate() {}

    public void onTerminate() {}

    public void onConfigurationChanged(Configuration newConfig) {
        for (ComponentCallbacks cb : collectComponentCallbacks()) cb.onConfigurationChanged(newConfig);
    }

    public void onLowMemory() {
        for (ComponentCallbacks cb : collectComponentCallbacks()) cb.onLowMemory();
    }

    public void onTrimMemory(int level) {
        for (ComponentCallbacks cb : collectComponentCallbacks()) {
            if (cb instanceof ComponentCallbacks2) ((ComponentCallbacks2) cb).onTrimMemory(level);
        }
    }

    public static String getProcessName() { return Process.myProcessName(); }

    @Override
    public void registerComponentCallbacks(ComponentCallbacks callback) {
        synchronized (mComponentCallbacks) {
            if (callback != null) mComponentCallbacks.add(callback);
        }
    }

    @Override
    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        synchronized (mComponentCallbacks) {
            mComponentCallbacks.remove(callback);
        }
    }

    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        synchronized (mActivityLifecycleCallbacks) {
            if (callback != null) mActivityLifecycleCallbacks.add(callback);
        }
    }

    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        synchronized (mActivityLifecycleCallbacks) {
            mActivityLifecycleCallbacks.remove(callback);
        }
    }

    public void registerOnProvideAssistDataListener(OnProvideAssistDataListener callback) {
        synchronized (this) {
            mAssistCallbacks.add(callback);
        }
    }

    public void unregisterOnProvideAssistDataListener(OnProvideAssistDataListener callback) {
        synchronized (this) {
            mAssistCallbacks.remove(callback);
        }
    }

    private ComponentCallbacks[] collectComponentCallbacks() {
        synchronized (mComponentCallbacks) {
            return mComponentCallbacks.toArray(new ComponentCallbacks[mComponentCallbacks.size()]);
        }
    }

    private ActivityLifecycleCallbacks[] collectActivityLifecycleCallbacks() {
        synchronized (mActivityLifecycleCallbacks) {
            return mActivityLifecycleCallbacks.toArray(new ActivityLifecycleCallbacks[mActivityLifecycleCallbacks.size()]);
        }
    }

    // Dispatch helpers called by Activity (AOSP Application.dispatchActivity*).

    void dispatchActivityPreCreated(Activity activity, Bundle savedInstanceState) {
        for (ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityPreCreated(activity, savedInstanceState);
    }

    void dispatchActivityCreated(Activity activity, Bundle savedInstanceState) {
        for (ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityCreated(activity, savedInstanceState);
    }

    void dispatchActivityPostCreated(Activity activity, Bundle savedInstanceState) {
        for (ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityPostCreated(activity, savedInstanceState);
    }

    void dispatchActivityPreStarted(Activity activity) {
        for (ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityPreStarted(activity);
    }

    void dispatchActivityStarted(Activity activity) {
        for (ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityStarted(activity);
    }

    void dispatchActivityPostStarted(Activity activity) {
        for (ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityPostStarted(activity);
    }

    void dispatchActivityPreResumed(Activity activity) {
        for (ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityPreResumed(activity);
    }

    void dispatchActivityResumed(Activity activity) {
        for (ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityResumed(activity);
    }

    void dispatchActivityPostResumed(Activity activity) {
        for (ActivityLifecycleCallbacks cb : collectActivityLifecycleCallbacks()) cb.onActivityPostResumed(activity);
    }

    void dispatchActivityPrePaused(Activity activity) {
        ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPrePaused(activity);
    }

    void dispatchActivityPaused(Activity activity) {
        ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPaused(activity);
    }

    void dispatchActivityPostPaused(Activity activity) {
        ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPostPaused(activity);
    }

    void dispatchActivityPreStopped(Activity activity) {
        ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPreStopped(activity);
    }

    void dispatchActivityStopped(Activity activity) {
        ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityStopped(activity);
    }

    void dispatchActivityPostStopped(Activity activity) {
        ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPostStopped(activity);
    }

    void dispatchActivityPreSaveInstanceState(Activity activity, Bundle outState) {
        ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPreSaveInstanceState(activity, outState);
    }

    void dispatchActivitySaveInstanceState(Activity activity, Bundle outState) {
        ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivitySaveInstanceState(activity, outState);
    }

    void dispatchActivityPostSaveInstanceState(Activity activity, Bundle outState) {
        ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPostSaveInstanceState(activity, outState);
    }

    void dispatchActivityPreDestroyed(Activity activity) {
        ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPreDestroyed(activity);
    }

    void dispatchActivityDestroyed(Activity activity) {
        ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityDestroyed(activity);
    }

    void dispatchActivityPostDestroyed(Activity activity) {
        ActivityLifecycleCallbacks[] cbs = collectActivityLifecycleCallbacks();
        for (int i = cbs.length - 1; i >= 0; i--) cbs[i].onActivityPostDestroyed(activity);
    }

    void dispatchOnProvideAssistData(Activity activity, Bundle data) {
        Object[] callbacks;
        synchronized (this) {
            callbacks = mAssistCallbacks.toArray();
        }
        for (Object cb : callbacks) ((OnProvideAssistDataListener) cb).onProvideAssistData(activity, data);
    }
}
