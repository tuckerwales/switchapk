package android.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import com.android.internal.util.InternalRes;

/**
 * For apps developing against Honeycomb or later, the tab host of an activity. Ported from AOSP.
 */
@Deprecated
public class TabActivity extends ActivityGroup {
    private TabHost mTabHost;
    private String mDefaultTab = null;
    private int mDefaultTabIndex = -1;

    public TabActivity() {
    }

    /** Sets the default tab that is the first tab highlighted. */
    public void setDefaultTab(String tag) {
        mDefaultTab = tag;
        mDefaultTabIndex = -1;
    }

    /** Sets the default tab that is the first tab highlighted. */
    public void setDefaultTab(int index) {
        mDefaultTab = null;
        mDefaultTabIndex = index;
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        ensureTabHost();
        String cur = state.getString("currentTab");
        if (cur != null) {
            mTabHost.setCurrentTabByTag(cur);
        }
        if (mTabHost.getCurrentTab() < 0) {
            if (mDefaultTab != null) {
                mTabHost.setCurrentTabByTag(mDefaultTab);
            } else if (mDefaultTabIndex >= 0) {
                mTabHost.setCurrentTab(mDefaultTabIndex);
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle icicle) {
        super.onPostCreate(icicle);

        ensureTabHost();

        if (mTabHost.getCurrentTab() == -1) {
            mTabHost.setCurrentTab(0);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        String currentTabTag = mTabHost.getCurrentTabTag();
        if (currentTabTag != null) {
            outState.putString("currentTab", currentTabTag);
        }
    }

    /**
     * Updates the screen state (current list and other views) when the
     * content changes.
     */
    @Override
    public void onContentChanged() {
        super.onContentChanged();
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);

        if (mTabHost == null) {
            throw new RuntimeException(
                    "Your content must have a TabHost whose id attribute is "
                            + "'android.R.id.tabhost'");
        }
        mTabHost.setup(getLocalActivityManager());
    }

    private void ensureTabHost() {
        if (mTabHost == null) {
            this.setContentView(InternalRes.layout("tab_content"));
        }
    }

    @Override
    protected void onChildTitleChanged(Activity childActivity, CharSequence title) {
        // Dorky implementation until we can have multiple activities running.
        if (getLocalActivityManager().getCurrentActivity() == childActivity) {
            View tabView = mTabHost.getCurrentTabView();
            if (tabView != null && tabView instanceof TextView) {
                ((TextView) tabView).setText(title);
            }
        }
    }

    /** Returns the {@link TabHost} the activity is using to host its tabs. */
    public TabHost getTabHost() {
        ensureTabHost();
        return mTabHost;
    }

    /** Returns the {@link TabWidget} the activity is using to draw the actual tabs. */
    public TabWidget getTabWidget() {
        return mTabHost.getTabWidget();
    }
}
