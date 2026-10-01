package com.example.lifecycle;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

/** Logs every activity callback through ActivityLifecycleCallbacks; check_shots.py checks the order. */
public class LifeApp extends Application {
    static void log(String event) { Log.i("Life", "LIFE " + event); }

    static String name(Activity a) { return a.getClass().getSimpleName(); }

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a, Bundle state) {
                log("cb " + name(a) + ".created " + (state != null ? "restored" : "fresh"));
            }

            public void onActivityStarted(Activity a) { log("cb " + name(a) + ".started"); }

            public void onActivityResumed(Activity a) { log("cb " + name(a) + ".resumed"); }

            public void onActivityPaused(Activity a) { log("cb " + name(a) + ".paused"); }

            public void onActivityStopped(Activity a) { log("cb " + name(a) + ".stopped"); }

            public void onActivitySaveInstanceState(Activity a, Bundle out) { log("cb " + name(a) + ".saved"); }

            public void onActivityDestroyed(Activity a) { log("cb " + name(a) + ".destroyed"); }

            @Override
            public void onActivityPreCreated(Activity a, Bundle state) { log("cb " + name(a) + ".precreated"); }
        });
    }
}
