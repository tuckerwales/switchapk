package com.example.widgets;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Space;
import android.widget.Switch;
import android.widget.ToggleButton;

/** Logic checks for the first widget slice. Each logs "WIDGETCHECK ok name" or a failure. */
final class WidgetChecks {
    static final String TAG = "WidgetTest";
    static int failures;

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "WIDGETCHECK ok " + name);
        else {
            failures++;
            Log.e(TAG, "WIDGETCHECK FAIL " + name + " " + detail);
        }
    }

    static void prepare(Activity activity) {
        logic(activity);
        ImageView photo = (ImageView) activity.findViewById(R.id.photo);
        Bitmap bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xFFE53935);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setImageBitmap(bitmap);

        CheckBox check = (CheckBox) activity.findViewById(R.id.check);
        check.setPadding(0, 0, 0, 0);
        check.setGravity(Gravity.CENTER_VERTICAL);
        check.setButtonDrawable(null);

        RadioButton a = (RadioButton) activity.findViewById(R.id.radio_a);
        RadioButton b = (RadioButton) activity.findViewById(R.id.radio_b);
        a.setPadding(0, 0, 0, 0);
        b.setPadding(0, 0, 0, 0);
        a.setGravity(Gravity.CENTER_VERTICAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        a.setButtonDrawable(null);
        b.setButtonDrawable(null);

        float density = activity.getResources().getDisplayMetrics().density;
        Switch sw = (Switch) activity.findViewById(R.id.switcher);
        sw.setPadding(0, 0, 0, 0);
        sw.setShowText(false);
        sw.setSwitchMinWidth((int) (64f * density + 0.5f));
        sw.setTrackDrawable(new ColorDrawable(0xFFBDBDBD));
        sw.setThumbDrawable(new ColorDrawable(Color.WHITE));
    }

    private static void logic(Activity activity) {
        scaleAndMeasure(activity);
        checks(activity);
        radios(activity);
        toggleAndSwitch(activity);
        names(activity);
    }

    private static void scaleAndMeasure(Activity activity) {
        ImageView fresh = new ImageView(activity);
        check("default scale", fresh.getScaleType() == ImageView.ScaleType.FIT_CENTER, fresh.getScaleType());
        check("ordinals", ImageView.ScaleType.MATRIX.ordinal() == 0
                && ImageView.ScaleType.FIT_XY.ordinal() == 1
                && ImageView.ScaleType.FIT_CENTER.ordinal() == 3
                && ImageView.ScaleType.CENTER_CROP.ordinal() == 6
                && ImageView.ScaleType.CENTER_INSIDE.ordinal() == 7, "order");
        fresh.setScaleType(ImageView.ScaleType.CENTER_CROP);
        check("scale round trip", fresh.getScaleType() == ImageView.ScaleType.CENTER_CROP, fresh.getScaleType());

        Bitmap bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xFFE53935);
        ImageView sized = new ImageView(activity);
        sized.setAdjustViewBounds(true);
        sized.setImageBitmap(bitmap);
        sized.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.AT_MOST));
        check("adjust bounds", sized.getMeasuredWidth() == 100 && sized.getMeasuredHeight() == 50,
                sized.getMeasuredWidth() + "x" + sized.getMeasuredHeight());
    }

    private static void checks(Activity activity) {
        final CheckBox box = new CheckBox(activity);
        box.setChecked(false);
        final boolean[] seen = new boolean[1];
        box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (isChecked) seen[0] = true;
            }
        });
        box.toggle();
        check("checkbox toggle", box.isChecked() && seen[0], box.isChecked());
        box.setChecked(false);
        check("checkbox clear", !box.isChecked(), box.isChecked());
    }

    private static void radios(Activity activity) {
        RadioGroup group = new RadioGroup(activity);
        RadioButton a = new RadioButton(activity);
        RadioButton b = new RadioButton(activity);
        a.setId(1);
        b.setId(2);
        group.addView(a);
        group.addView(b);
        group.check(1);
        group.check(2);
        check("radio exclusive", !a.isChecked() && b.isChecked() && group.getCheckedRadioButtonId() == 2,
                "a=" + a.isChecked() + " b=" + b.isChecked() + " id=" + group.getCheckedRadioButtonId());
        group.clearCheck();
        check("radio clear", !a.isChecked() && !b.isChecked() && group.getCheckedRadioButtonId() == -1,
                group.getCheckedRadioButtonId());
    }

    private static void toggleAndSwitch(Activity activity) {
        ToggleButton toggle = new ToggleButton(activity);
        toggle.setTextOn("YES");
        toggle.setTextOff("NO");
        toggle.setChecked(true);
        check("toggle on", "YES".contentEquals(toggle.getText()) && toggle.isChecked(), toggle.getText());
        toggle.setChecked(false);
        check("toggle off", "NO".contentEquals(toggle.getText()) && !toggle.isChecked(), toggle.getText());

        Switch sw = new Switch(activity);
        sw.setChecked(true);
        sw.toggle();
        check("switch toggle", !sw.isChecked(), sw.isChecked());
        sw.setChecked(true);
        check("switch set", sw.isChecked(), sw.isChecked());
    }

    private static void names(Activity activity) {
        check("button class", "android.widget.Button".contentEquals(new Button(activity).getAccessibilityClassName()),
                "");
        check("image button class",
                "android.widget.ImageButton".contentEquals(new ImageButton(activity).getAccessibilityClassName()), "");
        check("space invisible", new Space(activity).getVisibility() == View.INVISIBLE, "visibility");
    }
}
