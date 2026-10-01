package com.android.internal.view;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import com.android.internal.util.InternalRes;

/** framework-internal. Port of AOSP ActionBarPolicy: how many action buttons fit, and such. */
public class ActionBarPolicy {
    private final Context mContext;

    public static ActionBarPolicy get(Context context) { return new ActionBarPolicy(context); }

    private ActionBarPolicy(Context context) { mContext = context; }

    public int getMaxActionButtons() {
        final Configuration config = mContext.getResources().getConfiguration();
        final int width = config.screenWidthDp;
        final int height = config.screenHeightDp;
        final int smallest = config.smallestScreenWidthDp;
        if (smallest > 600 || (width > 960 && height > 720) || (width > 720 && height > 960)) return 5;
        if (width >= 500 || (width > 640 && height > 480) || (width > 480 && height > 640)) return 4;
        if (width >= 360) return 3;
        return 2;
    }

    public boolean showsOverflowMenuButton() { return true; }

    public int getEmbeddedMenuWidthLimit() { return mContext.getResources().getDisplayMetrics().widthPixels / 2; }

    public boolean hasEmbeddedTabs() {
        int id = InternalRes.id("bool", "action_bar_embed_tabs");
        return id != 0 && mContext.getResources().getBoolean(id);
    }

    public int getTabContainerHeight() {
        android.content.res.TypedArray a = mContext.obtainStyledAttributes(null,
                new int[] {android.R.attr.height}, android.R.attr.actionBarStyle, 0);
        int height = a.getLayoutDimension(0, 0);
        a.recycle();
        final Resources r = mContext.getResources();
        if (!hasEmbeddedTabs()) {
            int stacked = InternalRes.dimen("action_bar_stacked_max_height");
            if (stacked != 0) height = Math.min(height, r.getDimensionPixelSize(stacked));
        }
        return height;
    }

    public boolean enableHomeButtonByDefault() {
        return mContext.getApplicationInfo().targetSdkVersion < 14;
    }

    public int getStackedTabMaxWidth() {
        int id = InternalRes.dimen("action_bar_stacked_tab_max_width");
        return id != 0 ? mContext.getResources().getDimensionPixelSize(id) : 0;
    }
}
