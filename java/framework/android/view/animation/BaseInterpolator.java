package android.view.animation;

public abstract class BaseInterpolator implements Interpolator {
    private int mChangingConfiguration;

    public BaseInterpolator() {}

    /** framework-internal (hidden in AOSP). */
    public int getChangingConfiguration() { return mChangingConfiguration; }

    void setChangingConfiguration(int changingConfiguration) { mChangingConfiguration = changingConfiguration; }
}
