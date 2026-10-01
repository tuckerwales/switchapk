package android.app;

import java.util.List;

/** Retained fragments and child managers kept across a configuration change (AOSP FragmentManagerNonConfig). */
@Deprecated
public class FragmentManagerNonConfig {
    private final List<Fragment> mFragments;
    private final List<FragmentManagerNonConfig> mChildNonConfigs;

    FragmentManagerNonConfig(List<Fragment> fragments, List<FragmentManagerNonConfig> childNonConfigs) {
        mFragments = fragments;
        mChildNonConfigs = childNonConfigs;
    }

    List<Fragment> getFragments() { return mFragments; }

    List<FragmentManagerNonConfig> getChildNonConfigs() { return mChildNonConfigs; }
}
