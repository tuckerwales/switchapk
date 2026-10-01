package android.view;

import android.content.Context;

/** Overlay of a ViewGroup that can also hold views (AOSP ViewGroupOverlay). */
public class ViewGroupOverlay extends ViewOverlay {
    ViewGroupOverlay(Context context, View hostView) { super(context, hostView); }

    public void add(View view) { mOverlayViewGroup.add(view); }

    public void remove(View view) { mOverlayViewGroup.remove(view); }
}
