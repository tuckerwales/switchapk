package android.app;

/**
 * Legacy framework fragment transaction (AOSP API). TODO(WS4): the full API
 * lands with Fragment and FragmentManager.
 */
@Deprecated
public abstract class FragmentTransaction {
    public FragmentTransaction() {}

    public abstract int commit();

    public abstract int commitAllowingStateLoss();

    public abstract void commitNow();

    public abstract void commitNowAllowingStateLoss();

    public abstract boolean isEmpty();
}
