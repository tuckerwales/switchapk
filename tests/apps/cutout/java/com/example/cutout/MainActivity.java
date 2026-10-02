package com.example.cutout;

import android.app.Activity;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.WindowInsets;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks DisplayCutout, the window insets that carry one, and the theme
 * layout mode. The yellow square only shows that the window drew. The
 * Switch itself has no cutout.
 */
public class MainActivity extends Activity {
    private static final String TAG = "Cutout";

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "CT ok " + name);
        else Log.e(TAG, "CT FAIL " + name + " " + detail);
    }

    static DisplayCutout notch() {
        return new DisplayCutout(Insets.of(0, 48, 0, 0), null, new Rect(100, 0, 200, 48), null, null,
                Insets.of(8, 0, 8, 0));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        check("constants", WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT == 0
                && WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES == 1
                && WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER == 2
                && WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS == 3, lp.layoutInDisplayCutoutMode);
        check("mode", lp.layoutInDisplayCutoutMode
                == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES, lp.layoutInDisplayCutoutMode);

        DisplayCutout cut = notch();
        check("safe", cut.getSafeInsetLeft() == 0 && cut.getSafeInsetTop() == 48 && cut.getSafeInsetRight() == 0
                && cut.getSafeInsetBottom() == 0 && cut.getWaterfallInsets().equals(Insets.of(8, 0, 8, 0)), cut);
        boolean bounds = cut.getBoundingRectTop().equals(new Rect(100, 0, 200, 48))
                && cut.getBoundingRectLeft().isEmpty() && cut.getBoundingRectRight().isEmpty()
                && cut.getBoundingRectBottom().isEmpty() && cut.getBoundingRects().size() == 1
                && cut.getCutoutPath() == null;
        check("bounds", bounds, cut.getBoundingRects().size());

        Rect top = new Rect(100, 0, 200, 48);
        DisplayCutout owned = new DisplayCutout(Insets.of(0, 48, 0, 0), null, top, null, null);
        top.right = 1;
        Rect got = owned.getBoundingRectTop();
        int before = got.right;
        got.right = 1;
        check("copy", before == 200 && owned.getBoundingRectTop().right == 200, owned.getBoundingRectTop());

        List<Rect> both = new ArrayList<Rect>();
        both.add(new Rect(10, 0, 80, 40));
        both.add(new Rect(10, 400, 80, 480));
        DisplayCutout listed = new DisplayCutout(new Rect(0, 40, 0, 12), both);
        check("list", listed.getSafeInsetTop() == 40 && listed.getSafeInsetBottom() == 12
                && listed.getBoundingRectTop().equals(new Rect(10, 0, 80, 40))
                && listed.getBoundingRectBottom().equals(new Rect(10, 400, 80, 480))
                && listed.getBoundingRects().size() == 2, listed);

        List<Rect> sides = new ArrayList<Rect>();
        sides.add(new Rect(0, 100, 30, 180));
        sides.add(new Rect(500, 100, 530, 180));
        DisplayCutout sided = new DisplayCutout(new Rect(30, 0, 0, 0), sides);
        check("sides", sided.getBoundingRectLeft().equals(new Rect(0, 100, 30, 180))
                && sided.getBoundingRectRight().equals(new Rect(500, 100, 530, 180))
                && sided.getBoundingRectTop().isEmpty(), sided);

        DisplayCutout blanks = new DisplayCutout(Insets.NONE, null, null, null, null, null);
        DisplayCutout noList = new DisplayCutout((Rect) null, (List<Rect>) null);
        check("empty", blanks.getBoundingRectTop().isEmpty() && blanks.getWaterfallInsets().equals(Insets.NONE)
                && blanks.getCutoutPath() == null && noList.getSafeInsetTop() == 0
                && noList.getBoundingRects().isEmpty(), blanks);

        DisplayCutout twin = notch();
        DisplayCutout other = new DisplayCutout(Insets.of(0, 48, 0, 0), null, new Rect(100, 0, 200, 48), null, null,
                Insets.of(1, 0, 0, 0));
        check("equals", cut.equals(twin) && cut.hashCode() == twin.hashCode() && cut.equals(cut) && !cut.equals(null)
                && !cut.equals("x") && !cut.equals(other), cut.hashCode());

        WindowInsets plain = new WindowInsets.Builder().setDisplayCutout(cut).build();
        check("attached", cut.equals(plain.getDisplayCutout()) && plain.getSystemWindowInsetTop() == 0, plain);

        WindowInsets full = new WindowInsets.Builder()
                .setInsets(WindowInsets.Type.displayCutout(), Insets.of(0, 48, 0, 0))
                .setDisplayCutout(cut).build();
        WindowInsets consumed = full.consumeDisplayCutout();
        check("consume", full.getSystemWindowInsetTop() == 48 && consumed != full && consumed.getDisplayCutout() == null
                && consumed.getSystemWindowInsetTop() == 48 && full.getDisplayCutout() != null, consumed);

        DisplayCutout moved = plain.inset(10, 20, 0, 0).getDisplayCutout();
        check("inset", moved.getSafeInsetTop() == 28 && moved.getWaterfallInsets().equals(Insets.of(0, 0, 8, 0))
                && moved.getBoundingRectTop().equals(new Rect(90, -20, 190, 28)), moved);

        WindowInsets none = new WindowInsets.Builder().build();
        check("same", none.consumeDisplayCutout() == none && none.getDisplayCutout() == null, none);

        Parcel parcel = Parcel.obtain();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        WindowManager.LayoutParams back = WindowManager.LayoutParams.CREATOR.createFromParcel(parcel);
        check("parcel", back.layoutInDisplayCutoutMode
                == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS, back.layoutInDisplayCutoutMode);

        getWindow().getDecorView().post(new Runnable() {
            public void run() {
                WindowInsets root = getWindow().getDecorView().getRootWindowInsets();
                check("root", root != null && root.getDisplayCutout() == null, root);
            }
        });
    }
}
