package android.app;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for managing multiple running embedded activities in the same
 * process. Ported from AOSP; the ActivityThread transactions it used become
 * direct perform* calls on the embedded activities.
 */
@Deprecated
public class LocalActivityManager {
    private static final String TAG = "LocalActivityManager";

    // Internal token for an Activity being managed by LocalActivityManager.
    private static class LocalActivityRecord {
        LocalActivityRecord(String _id, Intent _intent) {
            id = _id;
            intent = _intent;
        }

        final String id;                // Unique name of this record.
        Intent intent;                  // Which activity to run here.
        ActivityInfo activityInfo;      // Package manager info about activity.
        Activity activity;              // Currently instantiated activity.
        Window window;                  // Activity's top-level window.
        Bundle instanceState;           // Last retrieved freeze state.
        int curState = RESTORED;        // Current state the activity is in.
    }

    static final int RESTORED = 0;      // State restored, but no startActivity().
    static final int INITIALIZING = 1;  // Ready to launch (after startActivity()).
    static final int CREATED = 2;       // Created, not started or resumed.
    static final int STARTED = 3;       // Created and started, not resumed.
    static final int RESUMED = 4;       // Created started and resumed.
    static final int DESTROYED = 5;     // No longer with us.

    /** The containing activity that owns the activities we create. */
    private final Activity mParent;
    /** The activity that is currently resumed. */
    private LocalActivityRecord mResumed;
    /** id -> record of all known activities. */
    private final Map<String, LocalActivityRecord> mActivities = new HashMap<String, LocalActivityRecord>();
    /** array of all known activities for easy iterating. */
    private final ArrayList<LocalActivityRecord> mActivityArray = new ArrayList<LocalActivityRecord>();

    /** True if only one activity can be resumed at a time */
    private boolean mSingleMode;

    /** Set to true once we find out the container is finishing. */
    private boolean mFinishing;

    /** Current state the owner (ActivityGroup) is in */
    private int mCurState = INITIALIZING;

    public LocalActivityManager(Activity parent, boolean singleMode) {
        mParent = parent;
        mSingleMode = singleMode;
    }

    private void moveToState(LocalActivityRecord r, int desiredState) {
        if (r.curState == RESTORED || r.curState == DESTROYED) {
            // startActivity() has not yet been called, so nothing to do.
            return;
        }

        if (r.curState == INITIALIZING) {
            // Get the lastNonConfigurationInstance for the activity
            HashMap<String, Object> lastNonConfigurationInstances = mParent.getLastNonConfigurationChildInstances();
            Object instanceObj = null;
            if (lastNonConfigurationInstances != null) {
                instanceObj = lastNonConfigurationInstances.get(r.id);
            }
            Activity.NonConfigurationInstances instance = null;
            if (instanceObj != null) {
                instance = new Activity.NonConfigurationInstances();
                instance.activity = instanceObj;
            }

            // We need to have always created the activity.
            if (r.activityInfo == null) {
                r.activityInfo = ActivityThread.resolveActivityInfo(r.intent);
            }
            final Bundle state = r.instanceState;
            r.activity = ActivityThread.startActivityNow(mParent, r.id, r.intent, r.activityInfo, state, instance);
            if (r.activity == null) {
                return;
            }
            r.window = r.activity.getWindow();
            r.instanceState = null;
            final Activity a = r.activity;
            a.performStart();
            if (!a.mFinished) {
                if (state != null) a.performRestoreInstanceState(state);
                a.performPostCreate(state);
            }
            r.curState = STARTED;
            if (desiredState == RESUMED) {
                a.performResume();
                r.curState = RESUMED;
            }

            // Don't do anything more here.  There is an important case:
            // if this is being done as part of onCreate() of the group, then
            // the launching of the activity gets its state a little ahead
            // of our own (it is now STARTED, while we are only CREATED).
            // If we just leave things as-is, we'll deal with it as the
            // group's state catches up.
            return;
        }

        switch (r.curState) {
            case CREATED:
                if (desiredState == STARTED) {
                    r.activity.performRestart();
                    r.curState = STARTED;
                }
                if (desiredState == RESUMED) {
                    r.activity.performRestart();
                    r.activity.performResume();
                    r.curState = RESUMED;
                }
                return;

            case STARTED:
                if (desiredState == RESUMED) {
                    // Need to resume it...
                    r.activity.performResume();
                    r.instanceState = null;
                    r.curState = RESUMED;
                }
                if (desiredState == CREATED) {
                    r.activity.performStop();
                    r.curState = CREATED;
                }
                return;

            case RESUMED:
                if (desiredState == STARTED) {
                    performPause(r, mFinishing);
                    r.curState = STARTED;
                }
                if (desiredState == CREATED) {
                    performPause(r, mFinishing);
                    r.activity.performStop();
                    r.curState = CREATED;
                }
                return;
        }
    }

    private void performPause(LocalActivityRecord r, boolean finishing) {
        // Only pre-Honeycomb apps had their state saved on pause; nothing to keep here.
        if (finishing) r.activity.mFinished = true;
        r.activity.performPause();
    }

    /**
     * Start a new activity running in the group.  Every activity you start
     * must have a unique string ID associated with it -- this is used to keep
     * track of the activity, so that if you later call startActivity() again
     * on it the same activity object will be retained.
     */
    public Window startActivity(String id, Intent intent) {
        if (mCurState == INITIALIZING) {
            throw new IllegalStateException(
                    "Activities can't be added until the containing group has been created.");
        }

        boolean adding = false;
        boolean sameIntent = false;

        ActivityInfo aInfo = null;

        // Already have information about the new activity id?
        LocalActivityRecord r = mActivities.get(id);
        if (r == null) {
            // Need to create it...
            r = new LocalActivityRecord(id, intent);
            adding = true;
        } else if (r.intent != null) {
            sameIntent = r.intent.filterEquals(intent);
            if (sameIntent) {
                // We are starting the same activity.
                aInfo = r.activityInfo;
            }
        }
        if (aInfo == null) {
            aInfo = ActivityThread.resolveActivityInfo(intent);
        }

        // Pause the currently running activity if there is one and only a single
        // activity is allowed to be running at a time.
        if (mSingleMode) {
            LocalActivityRecord old = mResumed;

            // If there was a previous activity, and it is not the current
            // activity, we need to stop it.
            if (old != null && old != r && mCurState == RESUMED) {
                moveToState(old, STARTED);
            }
        }

        if (adding) {
            // It's a brand new world.
            mActivities.put(id, r);
            mActivityArray.add(r);
        } else if (r.activityInfo != null) {
            // If the new activity is the same as the current one, then
            // we may be able to reuse it.
            if (aInfo == r.activityInfo
                    || (aInfo.name.equals(r.activityInfo.name)
                            && aInfo.packageName.equals(r.activityInfo.packageName))) {
                if (aInfo.launchMode != ActivityInfo.LAUNCH_MULTIPLE
                        || (intent.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0) {
                    // The activity wants onNewIntent() called.
                    if (r.activity != null) {
                        final boolean resumed = r.activity.mResumed;
                        if (resumed) r.activity.performPause();
                        r.activity.performNewIntent(intent);
                        if (resumed) r.activity.performResume();
                    }
                    r.intent = intent;
                    moveToState(r, mCurState);
                    if (mSingleMode) {
                        mResumed = r;
                    }
                    return r.window;
                }
                if (sameIntent && (intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) == 0) {
                    // We are showing the same thing, so this activity is
                    // just resumed and stays as-is.
                    r.intent = intent;
                    moveToState(r, mCurState);
                    if (mSingleMode) {
                        mResumed = r;
                    }
                    return r.window;
                }
            }

            // The new activity is different than the current one, or it
            // is a multiple launch activity, so we need to destroy what
            // is currently there.
            performDestroy(r, true);
        }

        r.intent = intent;
        r.curState = INITIALIZING;
        r.activityInfo = aInfo;

        moveToState(r, mCurState);

        // When in single mode, we always switch to the new activity.
        if (mSingleMode) {
            mResumed = r;
        }
        return r.window;
    }

    private Window performDestroy(LocalActivityRecord r, boolean finish) {
        Window win;
        win = r.window;
        if (r.curState == RESUMED && !finish) {
            performPause(r, finish);
        }
        ActivityThread.destroyEmbeddedActivity(r.activity, finish);
        r.activity = null;
        r.window = null;
        if (finish) {
            r.instanceState = null;
        }
        r.curState = DESTROYED;
        return win;
    }

    /**
     * Destroy the activity associated with a particular id.  This activity
     * will go through the normal lifecycle events and fine onDestroy(), and
     * then the id removed from the group.
     */
    public Window destroyActivity(String id, boolean finish) {
        LocalActivityRecord r = mActivities.get(id);
        Window win = null;
        if (r != null) {
            win = performDestroy(r, finish);
            if (finish) {
                mActivities.remove(id);
                mActivityArray.remove(r);
            }
        }
        return win;
    }

    /** Retrieve the Activity that is currently running. */
    public Activity getCurrentActivity() {
        return mResumed != null ? mResumed.activity : null;
    }

    /** Retrieve the ID of the activity that is currently running. */
    public String getCurrentId() {
        return mResumed != null ? mResumed.id : null;
    }

    /** Return the Activity object associated with a string ID. */
    public Activity getActivity(String id) {
        LocalActivityRecord r = mActivities.get(id);
        return r != null ? r.activity : null;
    }

    /**
     * Restore a state that was previously returned by {@link #saveInstanceState}.  This
     * adds to the activity group information about all activity IDs that had
     * previously been saved, even if they have not been started yet, so if the
     * user later navigates to them the correct state will be restored.
     */
    public void dispatchCreate(Bundle state) {
        if (state != null) {
            for (String id : state.keySet()) {
                try {
                    final Bundle astate = state.getBundle(id);
                    LocalActivityRecord r = mActivities.get(id);
                    if (r != null) {
                        r.instanceState = astate;
                    } else {
                        r = new LocalActivityRecord(id, null);
                        r.instanceState = astate;
                        mActivities.put(id, r);
                        mActivityArray.add(r);
                    }
                } catch (Exception e) {
                    // Recover from -all- app errors.
                    Log.e(TAG, "Exception thrown when restoring LocalActivityManager state", e);
                }
            }
        }

        mCurState = CREATED;
    }

    /**
     * Retrieve the state of all activities known by the group.  For
     * activities that have previously run and are now stopped or finished, the
     * last saved state is used.  For the current running activity, its
     * {@link Activity#onSaveInstanceState} is called to retrieve its current state.
     */
    public Bundle saveInstanceState() {
        Bundle state = null;

        // FIXME: child activities will freeze as part of onPaused. Do we
        // need to do this here?
        final int N = mActivityArray.size();
        for (int i = 0; i < N; i++) {
            final LocalActivityRecord r = mActivityArray.get(i);
            if (state == null) {
                state = new Bundle();
            }
            if ((r.instanceState != null || r.curState == RESUMED) && r.activity != null) {
                // We need to save the state now, if we don't currently
                // already have it or the activity is currently resumed.
                final Bundle childState = new Bundle();
                r.activity.performSaveInstanceState(childState);
                r.instanceState = childState;
            }
            if (r.instanceState != null) {
                state.putBundle(r.id, r.instanceState);
            }
        }

        return state;
    }

    /** Called by the container activity in its {@link Activity#onResume} so that LocalActivityManager can perform the corresponding action on the activities it holds. */
    public void dispatchResume() {
        mCurState = RESUMED;
        if (mSingleMode) {
            if (mResumed != null) {
                moveToState(mResumed, RESUMED);
            }
        } else {
            final int N = mActivityArray.size();
            for (int i = 0; i < N; i++) {
                moveToState(mActivityArray.get(i), RESUMED);
            }
        }
    }

    /** Called by the container activity in its {@link Activity#onPause} so that LocalActivityManager can perform the corresponding action on the activities it holds. */
    public void dispatchPause(boolean finishing) {
        if (finishing) {
            mFinishing = true;
        }
        mCurState = STARTED;
        if (mSingleMode) {
            if (mResumed != null) {
                moveToState(mResumed, STARTED);
            }
        } else {
            final int N = mActivityArray.size();
            for (int i = 0; i < N; i++) {
                LocalActivityRecord r = mActivityArray.get(i);
                if (r.curState == RESUMED) {
                    moveToState(r, STARTED);
                }
            }
        }
    }

    /** Called by the container activity in its {@link Activity#onStop} so that LocalActivityManager can perform the corresponding action on the activities it holds. */
    public void dispatchStop() {
        mCurState = CREATED;
        final int N = mActivityArray.size();
        for (int i = 0; i < N; i++) {
            LocalActivityRecord r = mActivityArray.get(i);
            moveToState(r, CREATED);
        }
    }

    /**
     * framework-internal (hidden in AOSP). Call onRetainNonConfigurationInstance on each child
     * activity and store the results in a HashMap keyed by the child's id.
     */
    HashMap<String, Object> dispatchRetainNonConfigurationInstance() {
        HashMap<String, Object> instanceMap = null;

        final int N = mActivityArray.size();
        for (int i = 0; i < N; i++) {
            LocalActivityRecord r = mActivityArray.get(i);
            if ((r != null) && (r.activity != null)) {
                Object instance = r.activity.onRetainNonConfigurationInstance();
                if (instance != null) {
                    if (instanceMap == null) {
                        instanceMap = new HashMap<String, Object>();
                    }
                    instanceMap.put(r.id, instance);
                }
            }
        }
        return instanceMap;
    }

    /** Remove all activities from this LocalActivityManager, performing an {@link Activity#onDestroy} on any that are currently instantiated. */
    public void removeAllActivities() {
        dispatchDestroy(true);
    }

    /** Called by the container activity in its {@link Activity#onDestroy} so that LocalActivityManager can perform the corresponding action on the activities it holds. */
    public void dispatchDestroy(boolean finishing) {
        final int N = mActivityArray.size();
        for (int i = 0; i < N; i++) {
            LocalActivityRecord r = mActivityArray.get(i);
            ActivityThread.destroyEmbeddedActivity(r.activity, finishing);
        }
        mActivities.clear();
        mActivityArray.clear();
    }
}
