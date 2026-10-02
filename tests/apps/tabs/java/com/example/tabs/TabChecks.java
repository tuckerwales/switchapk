package com.example.tabs;

import android.app.Activity;
import android.app.ActivityGroup;
import android.app.LocalActivityManager;
import android.app.TabActivity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import java.util.ArrayList;

/** Logic checks for tabs. Each logs "TABCHECK ok name" or a failure. */
final class TabChecks {
    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(MainActivity.TAG, "TABCHECK ok " + name);
        else Log.e(MainActivity.TAG, "TABCHECK FAIL " + name + " " + detail);
    }

    static void run(TabActivity activity) {
        tabActivity(activity);
        codeBuilt(activity);
        errors(activity);
    }

    private static void tabActivity(TabActivity activity) {
        TabHost host = activity.getTabHost();
        TabWidget widget = activity.getTabWidget();
        check("tab host from the layout", host != null && host.getId() == android.R.id.tabhost, host);
        check("tab widget", widget == host.getTabWidget() && widget.getId() == android.R.id.tabs, widget);
        check("tab count", widget.getTabCount() == 4, widget.getTabCount());
        check("first tab current", host.getCurrentTab() == 0 && "text".equals(host.getCurrentTabTag()),
                host.getCurrentTabTag());
        check("current view", host.getCurrentView() == activity.findViewById(R.id.text_tab),
                host.getCurrentView());
        check("tab content view", host.getTabContentView().getId() == android.R.id.tabcontent,
                host.getTabContentView());
        check("current tab view", host.getCurrentTabView() == widget.getChildTabViewAt(0), host.getCurrentTabView());
        boolean selected = widget.getChildTabViewAt(0).isSelected();
        for (int i = 1; i < 4; i++) selected &= !widget.getChildTabViewAt(i).isSelected();
        check("only the first indicator selected", selected, null);
        TextView title = (TextView) widget.getChildTabViewAt(3).findViewById(android.R.id.title);
        check("indicator label", title != null && "Other".contentEquals(title.getText()), title);
        View icon = widget.getChildTabViewAt(2).findViewById(android.R.id.icon);
        check("label wins over icon in the material indicator", icon != null && icon.getVisibility() == View.GONE,
                icon);
        check("indicators clickable and focusable", widget.getChildTabViewAt(1).isClickable()
                && widget.getChildTabViewAt(1).isFocusable(), null);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) widget.getChildTabViewAt(1).getLayoutParams();
        check("indicators share the width", lp.weight == 1f && lp.width == 0, lp.weight + " " + lp.width);
        check("other tab content hidden", activity.findViewById(R.id.text_tab).getVisibility() == View.VISIBLE, null);
        boolean strip = widget.isStripEnabled();
        widget.setStripEnabled(!strip);
        check("strip enabled toggles", widget.isStripEnabled() == !strip, strip);
        widget.setStripEnabled(strip);
        LocalActivityManager lam = activity.getLocalActivityManager();
        check("no embedded activity yet", lam.getCurrentActivity() == null && lam.getCurrentId() == null
                && activity.getCurrentActivity() == null, lam.getCurrentId());
        check("activity group", activity instanceof ActivityGroup && !activity.isChild()
                && activity.getParent() == null, null);
    }

    private static void codeBuilt(Activity activity) {
        TabHost host = new TabHost(activity);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        TabWidget tabs = new TabWidget(activity);
        tabs.setId(android.R.id.tabs);
        FrameLayout content = new FrameLayout(activity);
        content.setId(android.R.id.tabcontent);
        column.addView(tabs);
        column.addView(content);
        host.addView(column);
        host.setup();
        final ArrayList<String> changes = new ArrayList<String>();
        host.setOnTabChangedListener(changes::add);
        final View[] made = new View[1];
        host.addTab(host.newTabSpec("a").setIndicator("Alpha").setContent(tag -> {
            made[0] = new View(activity);
            return made[0];
        }));
        host.addTab(host.newTabSpec("b").setIndicator("Beta").setContent(tag -> new TextView(activity)));
        check("code-built first tab", host.getCurrentTab() == 0 && host.getCurrentView() == made[0]
                && made[0].getParent() == content, host.getCurrentView());
        TextView label = (TextView) tabs.getChildTabViewAt(1).findViewById(android.R.id.title);
        check("code-built label", label != null && "Beta".contentEquals(label.getText()), label);
        host.setCurrentTabByTag("b");
        check("set by tag", host.getCurrentTab() == 1 && changes.size() == 2 && "b".equals(changes.get(1))
                && made[0].getVisibility() == View.GONE, changes);
        host.setCurrentTab(1);
        host.setCurrentTab(5);
        check("same or bad index ignored", host.getCurrentTab() == 1 && changes.size() == 2, changes);
        View custom = new TextView(activity);
        host.addTab(host.newTabSpec("c").setIndicator(custom).setContent(tag -> new View(activity)));
        check("custom indicator turns the strip off", tabs.getChildTabViewAt(2) == custom && !tabs.isStripEnabled(),
                tabs.isStripEnabled());
        host.clearAllTabs();
        check("clear all tabs", tabs.getTabCount() == 0 && host.getCurrentTab() == -1
                && content.getChildCount() == 0 && host.getCurrentTabTag() == null, tabs.getTabCount());
        host.addTab(host.newTabSpec("d").setIndicator("Delta").setContent(tag -> new View(activity)));
        check("tabs again after clearing", host.getCurrentTab() == 0 && "d".equals(host.getCurrentTabTag()),
                host.getCurrentTabTag());
        tabs.setEnabled(false);
        check("disabling the widget disables the tabs", !tabs.getChildTabViewAt(0).isEnabled(), null);
    }

    private static void errors(Activity activity) {
        TabHost host = new TabHost(activity);
        try {
            host.setup();
            check("setup needs tabs", false, null);
        } catch (RuntimeException e) {
            check("setup needs tabs", e.getMessage().contains("android.R.id.tabs"), e);
        }
        try {
            host.newTabSpec(null);
            check("null tag", false, null);
        } catch (IllegalArgumentException e) {
            check("null tag", true, e);
        }
        LinearLayout column = new LinearLayout(activity);
        TabWidget tabs = new TabWidget(activity);
        tabs.setId(android.R.id.tabs);
        FrameLayout content = new FrameLayout(activity);
        content.setId(android.R.id.tabcontent);
        column.addView(tabs);
        column.addView(content);
        host.addView(column);
        host.setup();
        try {
            host.addTab(host.newTabSpec("x").setContent(tag -> new View(activity)));
            check("indicator required", false, null);
        } catch (IllegalArgumentException e) {
            check("indicator required", true, e);
        }
        try {
            host.addTab(host.newTabSpec("x").setIndicator("X"));
            check("content required", false, null);
        } catch (IllegalArgumentException e) {
            check("content required", true, e);
        }
        try {
            host.addTab(host.newTabSpec("x").setIndicator("X").setContent(12345));
            check("missing content id", false, null);
        } catch (RuntimeException e) {
            check("missing content id", e.getMessage().contains("12345"), e);
        }
        try {
            host.addTab(host.newTabSpec("x").setIndicator("X")
                    .setContent(new Intent(activity, ChildActivity.class)));
            check("intent tab needs a local activity manager", false, null);
        } catch (IllegalStateException e) {
            check("intent tab needs a local activity manager", true, e);
        }
        LocalActivityManager lam = new LocalActivityManager(activity, true);
        try {
            lam.startActivity("x", new Intent(activity, ChildActivity.class));
            check("group must be created first", false, null);
        } catch (IllegalStateException e) {
            check("group must be created first", true, e);
        }
    }

    /** From the embedded activity's onCreate. */
    static void child(Activity child, String name) {
        Activity parent = child.getParent();
        check(name + " is a child", child.isChild() && parent instanceof MainActivity, parent);
        check(name + " window contained", parent != null
                && child.getWindow().getContainer() == parent.getWindow(), child.getWindow().getContainer());
        check(name + " has no action bar", child.getActionBar() == null, child.getActionBar());
    }

    /** From the embedded activity's first onResume. */
    static void childResumed(Activity child, String name) {
        ActivityGroup group = (ActivityGroup) child.getParent();
        LocalActivityManager lam = group.getLocalActivityManager();
        check(name + " is current", lam.getActivity(name) == child && lam.getCurrentActivity() == child
                && name.equals(lam.getCurrentId()) && group.getCurrentActivity() == child, lam.getCurrentId());
        View decor = child.getWindow().getDecorView();
        ViewGroup content = ((TabActivity) group).getTabHost().getTabContentView();
        check(name + " decor in the tab content", decor.getParent() == content, decor.getParent());
    }
}
