package android.app;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Parcelable;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

/** Lifecycle entry points of a host's FragmentManager (AOSP FragmentController). */
@Deprecated
public class FragmentController {
    private final FragmentHostCallback<?> mHost;

    public static final FragmentController createController(FragmentHostCallback<?> callbacks) {
        return new FragmentController(callbacks);
    }

    private FragmentController(FragmentHostCallback<?> callbacks) { mHost = callbacks; }

    public FragmentManager getFragmentManager() { return mHost.getFragmentManagerImpl(); }

    public LoaderManager getLoaderManager() { return mHost.getLoaderManagerImpl(); }

    public Fragment findFragmentByWho(String who) { return mHost.mFragmentManager.findFragmentByWho(who); }

    public void attachHost(Fragment parent) { mHost.mFragmentManager.attachController(mHost, mHost, parent); }

    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        return mHost.mFragmentManager.onCreateView(parent, name, context, attrs);
    }

    public void noteStateNotSaved() { mHost.mFragmentManager.noteStateNotSaved(); }

    public Parcelable saveAllState() { return mHost.mFragmentManager.saveAllState(); }

    @Deprecated
    public void restoreAllState(Parcelable state, List<Fragment> nonConfigList) {
        mHost.mFragmentManager.restoreAllState(state, new FragmentManagerNonConfig(nonConfigList, null));
    }

    public void restoreAllState(Parcelable state, FragmentManagerNonConfig nonConfig) {
        mHost.mFragmentManager.restoreAllState(state, nonConfig);
    }

    @Deprecated
    public List<Fragment> retainNonConfig() {
        FragmentManagerNonConfig nonconf = mHost.mFragmentManager.retainNonConfig();
        return nonconf != null ? nonconf.getFragments() : null;
    }

    public FragmentManagerNonConfig retainNestedNonConfig() { return mHost.mFragmentManager.retainNonConfig(); }

    public void dispatchCreate() { mHost.mFragmentManager.dispatchCreate(); }

    public void dispatchActivityCreated() { mHost.mFragmentManager.dispatchActivityCreated(); }

    public void dispatchStart() { mHost.mFragmentManager.dispatchStart(); }

    public void dispatchResume() { mHost.mFragmentManager.dispatchResume(); }

    public void dispatchPause() { mHost.mFragmentManager.dispatchPause(); }

    public void dispatchStop() { mHost.mFragmentManager.dispatchStop(); }

    public void dispatchDestroyView() { mHost.mFragmentManager.dispatchDestroyView(); }

    public void dispatchDestroy() { mHost.mFragmentManager.dispatchDestroy(); }

    @Deprecated
    public void dispatchMultiWindowModeChanged(boolean isInMultiWindowMode) {
        mHost.mFragmentManager.dispatchMultiWindowModeChanged(isInMultiWindowMode, null);
    }

    public void dispatchMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        mHost.mFragmentManager.dispatchMultiWindowModeChanged(isInMultiWindowMode, newConfig);
    }

    @Deprecated
    public void dispatchPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        mHost.mFragmentManager.dispatchPictureInPictureModeChanged(isInPictureInPictureMode, null);
    }

    public void dispatchPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        mHost.mFragmentManager.dispatchPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
    }

    public void dispatchConfigurationChanged(Configuration newConfig) {
        mHost.mFragmentManager.dispatchConfigurationChanged(newConfig);
    }

    public void dispatchLowMemory() { mHost.mFragmentManager.dispatchLowMemory(); }

    public void dispatchTrimMemory(int level) { mHost.mFragmentManager.dispatchTrimMemory(level); }

    public boolean dispatchCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        return mHost.mFragmentManager.dispatchCreateOptionsMenu(menu, inflater);
    }

    public boolean dispatchPrepareOptionsMenu(Menu menu) { return mHost.mFragmentManager.dispatchPrepareOptionsMenu(menu); }

    public boolean dispatchOptionsItemSelected(MenuItem item) { return mHost.mFragmentManager.dispatchOptionsItemSelected(item); }

    public boolean dispatchContextItemSelected(MenuItem item) { return mHost.mFragmentManager.dispatchContextItemSelected(item); }

    public void dispatchOptionsMenuClosed(Menu menu) { mHost.mFragmentManager.dispatchOptionsMenuClosed(menu); }

    public boolean execPendingActions() { return mHost.mFragmentManager.execPendingActions(); }

    public void doLoaderStart() { mHost.doLoaderStart(); }

    public void doLoaderStop(boolean retain) { mHost.doLoaderStop(retain); }

    public void doLoaderDestroy() { mHost.doLoaderDestroy(); }

    public void reportLoaderStart() { mHost.reportLoaderStart(); }

    public ArrayMap<String, LoaderManager> retainLoaderNonConfig() { return mHost.retainLoaderNonConfig(); }

    public void restoreLoaderNonConfig(ArrayMap<String, LoaderManager> loaderManagers) {
        mHost.restoreLoaderNonConfig(loaderManagers);
    }

    public void dumpLoaders(String prefix, FileDescriptor fd, PrintWriter pw, String[] args) {}
}
