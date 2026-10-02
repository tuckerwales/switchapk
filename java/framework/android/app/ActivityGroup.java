package android.app;

import android.content.Intent;
import android.os.Bundle;

import java.util.HashMap;

/**
 * A screen that contains and runs multiple embedded activities. Ported from AOSP.
 */
@Deprecated
public class ActivityGroup extends Activity {
    private static final String STATES_KEY = "android:states";

    /** This field should be made private, so it is hidden from the SDK. */
    protected LocalActivityManager mLocalActivityManager;

    public ActivityGroup() {
        this(true);
    }

    public ActivityGroup(boolean singleActivityMode) {
        mLocalActivityManager = new LocalActivityManager(this, singleActivityMode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle states = savedInstanceState != null
                ? (Bundle) savedInstanceState.getBundle(STATES_KEY) : null;
        mLocalActivityManager.dispatchCreate(states);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mLocalActivityManager.dispatchResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle state = mLocalActivityManager.saveInstanceState();
        if (state != null) {
            outState.putBundle(STATES_KEY, state);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mLocalActivityManager.dispatchPause(isFinishing());
    }

    @Override
    protected void onStop() {
        super.onStop();
        mLocalActivityManager.dispatchStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLocalActivityManager.dispatchDestroy(isFinishing());
    }

    /**
     * Returns a HashMap mapping from child activity ids to the return values
     * from calls to their onRetainNonConfigurationInstance methods.
     */
    @Override
    HashMap<String, Object> onRetainNonConfigurationChildInstances() {
        return mLocalActivityManager.dispatchRetainNonConfigurationInstance();
    }

    public Activity getCurrentActivity() {
        return mLocalActivityManager.getCurrentActivity();
    }

    public final LocalActivityManager getLocalActivityManager() {
        return mLocalActivityManager;
    }

    @Override
    void dispatchActivityResult(String who, int requestCode, int resultCode, Intent data) {
        if (who != null) {
            Activity act = mLocalActivityManager.getActivity(who);
            if (act != null) {
                act.onActivityResult(requestCode, resultCode, data);
                return;
            }
        }
        super.dispatchActivityResult(who, requestCode, resultCode, data);
    }
}
