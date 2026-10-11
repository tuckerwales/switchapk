package android.view.autofill;

import android.content.ComponentName;
import android.graphics.Rect;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * There is no autofill service on switchapk: the manager exists (apps such as
 * Jetpack Compose require Context.getSystemService to return one) but reports
 * itself disabled and ignores every notification. Callbacks are stored and
 * never called.
 */
public final class AutofillManager {
    public static final String EXTRA_ASSIST_STRUCTURE = "android.view.autofill.extra.ASSIST_STRUCTURE";
    public static final String EXTRA_AUTHENTICATION_RESULT = "android.view.autofill.extra.AUTHENTICATION_RESULT";
    public static final String EXTRA_AUTHENTICATION_RESULT_EPHEMERAL_DATASET =
            "android.view.autofill.extra.AUTHENTICATION_RESULT_EPHEMERAL_DATASET";
    public static final String EXTRA_CLIENT_STATE = "android.view.autofill.extra.CLIENT_STATE";
    public static final String EXTRA_INLINE_SUGGESTIONS_REQUEST =
            "android.view.autofill.extra.INLINE_SUGGESTIONS_REQUEST";

    private static AutofillManager sInstance;
    private final ArrayList<AutofillCallback> mCallbacks = new ArrayList<AutofillCallback>();

    AutofillManager() {}

    /** framework-internal: the instance Context.getSystemService returns. */
    public static synchronized AutofillManager getInstance() {
        if (sInstance == null) sInstance = new AutofillManager();
        return sInstance;
    }

    public boolean isEnabled() { return false; }
    public void requestAutofill(View view) {}
    public void requestAutofill(View view, int virtualId, Rect absBounds) {}
    public void notifyViewEntered(View view) {}
    public void notifyViewExited(View view) {}
    public void notifyViewVisibilityChanged(View view, boolean isVisible) {}
    public void notifyViewVisibilityChanged(View view, int virtualId, boolean isVisible) {}
    public void notifyViewEntered(View view, int virtualId, Rect absBounds) {}
    public void notifyViewExited(View view, int virtualId) {}
    public void notifyValueChanged(View view) {}
    public void notifyValueChanged(View view, int virtualId, AutofillValue value) {}
    public void notifyViewClicked(View view) {}
    public void notifyViewClicked(View view, int virtualId) {}
    public void commit() {}
    public void cancel() {}
    public void disableAutofillServices() {}
    public boolean hasEnabledAutofillServices() { return false; }
    public ComponentName getAutofillServiceComponentName() { return null; }
    public String getUserDataId() { return null; }
    public boolean isFieldClassificationEnabled() { return false; }
    public String getDefaultFieldClassificationAlgorithm() { return null; }
    public List<String> getAvailableFieldClassificationAlgorithms() { return Collections.emptyList(); }
    public boolean isAutofillSupported() { return false; }
    public AutofillId getNextAutofillId() { return null; }
    public void registerCallback(AutofillCallback callback) {
        if (callback != null && !mCallbacks.contains(callback)) mCallbacks.add(callback);
    }
    public void unregisterCallback(AutofillCallback callback) { mCallbacks.remove(callback); }
    public boolean showAutofillDialog(View view) { return false; }
    public boolean showAutofillDialog(View view, int virtualId) { return false; }

    public abstract static class AutofillCallback {
        public static final int EVENT_INPUT_HIDDEN = 2;
        public static final int EVENT_INPUT_SHOWN = 1;
        public static final int EVENT_INPUT_UNAVAILABLE = 3;

        public AutofillCallback() {}
        public void onAutofillEvent(View view, int event) {}
        public void onAutofillEvent(View view, int virtualId, int event) {}
    }
}
