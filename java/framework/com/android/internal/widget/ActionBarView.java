package com.android.internal.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.Toolbar;

/**
 * The Holo decor action bar that framework-res's screen_action_bar layout inflates for
 * pre-Material themes. AOSP's ActionBarView is a DecorToolbar of its own; here it is a
 * Toolbar (WindowDecorActionBar drives it through Toolbar.getWrapper(), as for Material's
 * screen_toolbar) styled from the theme's actionBarStyle: its background, height and
 * title/subtitle text styles. Holo-only details (the up-indicator layout, list and tab
 * navigation modes in the bar, the split action bar) are not reproduced.
 */
public class ActionBarView extends Toolbar {
    // sorted ascending, as obtainStyledAttributes expects
    private static final int[] ATTRS = {
        android.R.attr.background, android.R.attr.height,
        android.R.attr.titleTextStyle, android.R.attr.subtitleTextStyle,
    };

    public ActionBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, ATTRS, android.R.attr.actionBarStyle, 0);
        Drawable background = a.getDrawable(0);
        if (background != null) setBackground(background);
        int height = a.getLayoutDimension(1, 0);
        if (height > 0) setMinimumHeight(height);
        int title = a.getResourceId(2, 0);
        if (title != 0) setTitleTextAppearance(context, title);
        int subtitle = a.getResourceId(3, 0);
        if (subtitle != 0) setSubtitleTextAppearance(context, subtitle);
        a.recycle();
    }
}
