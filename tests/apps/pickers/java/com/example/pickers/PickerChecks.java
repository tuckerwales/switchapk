package com.example.pickers;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.NumberPicker;
import android.widget.TextClock;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.ViewFlipper;

/** Logic checks for the pickers and clocks. Each logs "PKCHECK ok name" or a failure. */
final class PickerChecks {
    static final String TAG = "PickerTest";

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "PKCHECK ok " + name);
        else Log.e(TAG, "PKCHECK FAIL " + name + " " + detail);
    }

    static void run(Activity activity) {
        numberPicker(activity);
        chronometer(activity);
        textClock(activity);
        switchers(activity);
    }

    private static void switchers(Activity activity) {
        ViewFlipper flipper = new ViewFlipper(activity);
        for (int i = 0; i < 3; i++) {
            TextView t = new TextView(activity);
            t.setText("page " + i);
            flipper.addView(t);
        }
        check("first shown", flipper.getDisplayedChild() == 0
                && flipper.getChildAt(0).getVisibility() == View.VISIBLE
                && flipper.getChildAt(1).getVisibility() == View.GONE, flipper.getDisplayedChild());
        flipper.showNext();
        flipper.showNext();
        flipper.showNext();
        check("next wraps", flipper.getDisplayedChild() == 0, flipper.getDisplayedChild());
        flipper.showPrevious();
        check("previous wraps", flipper.getDisplayedChild() == 2
                && flipper.getCurrentView().getVisibility() == View.VISIBLE, flipper.getDisplayedChild());
        flipper.removeViewAt(2);
        check("remove shown", flipper.getDisplayedChild() == 1, flipper.getDisplayedChild());
        flipper.setFlipInterval(500);
        flipper.startFlipping();
        check("flipping", flipper.isFlipping() && flipper.getFlipInterval() == 500, flipper.isFlipping());

        TextSwitcher switcher = new TextSwitcher(activity);
        switcher.setFactory(() -> new TextView(activity));
        switcher.setText("one");
        switcher.setText("two");
        check("text switcher", "two".equals(((TextView) switcher.getCurrentView()).getText().toString())
                && "one".equals(((TextView) switcher.getNextView()).getText().toString()), switcher.getDisplayedChild());
        boolean threw = false;
        try {
            switcher.addView(new TextView(activity));
        } catch (IllegalStateException e) {
            threw = true;
        }
        check("two views max", threw, "no exception");
    }

    private static void numberPicker(Activity activity) {
        NumberPicker p = new NumberPicker(activity);
        final int[] changes = new int[] {0};
        p.setOnValueChangedListener((picker, oldVal, newVal) -> changes[0]++);
        p.setMinValue(1);
        p.setMaxValue(10);
        check("min max", p.getMinValue() == 1 && p.getMaxValue() == 10, p.getMinValue() + ".." + p.getMaxValue());
        check("value raised to min", p.getValue() == 1, p.getValue());
        // With wrapping (more values than wheel slots) an out-of-range value wraps; without, it clamps.
        p.setValue(42);
        check("value wrapped", p.getValue() == 5, p.getValue());
        p.setWrapSelectorWheel(false);
        p.setValue(42);
        check("value clamped", p.getValue() == 10, p.getValue());
        p.setWrapSelectorWheel(true);
        p.setValue(4);
        check("set value", p.getValue() == 4 && changes[0] == 0, p.getValue() + " changes " + changes[0]);
        check("wraps by default", p.getWrapSelectorWheel(), "no wrap");
        p.setWrapSelectorWheel(false);
        check("wrap off", !p.getWrapSelectorWheel(), "wrap");
        p.setWrapSelectorWheel(true);
        p.setMaxValue(3);
        check("too few to wrap", !p.getWrapSelectorWheel(), "wrap with 3 values");
        p.setMaxValue(10);
        check("wrap again", p.getWrapSelectorWheel(), "no wrap with 10 values");
        p.setMaxValue(2);
        check("max lowers value", p.getValue() == 2, p.getValue());
        boolean threw = false;
        try {
            p.setMinValue(-1);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("negative min throws", threw, "no exception");
        String[] names = {"a", "b", "c"};
        p.setMinValue(0);
        p.setDisplayedValues(names);
        check("displayed values", p.getDisplayedValues() == names, p.getDisplayedValues());
        check("text size", p.getTextSize() > 0, p.getTextSize());
        p.setTextColor(0xFF112233);
        check("text color", p.getTextColor() == 0xFF112233, Integer.toHexString(p.getTextColor()));
        check("divider height", p.getSelectionDividerHeight() == 3, p.getSelectionDividerHeight());
        check("focusable", p.isFocusable() && p.isFocusableInTouchMode(), p.isFocusable());

        int ws = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST);
        p.measure(ws, ws);
        // Widget.Material.NumberPicker: internalMaxHeight 180dp, internalMinWidth 64dp (density 1.5).
        check("measured height", p.getMeasuredHeight() == 270, p.getMeasuredHeight());
        check("measured width", p.getMeasuredWidth() >= 96, p.getMeasuredWidth());
    }

    private static void chronometer(Activity activity) {
        Chronometer c = new Chronometer(activity);
        final int[] ticks = new int[] {0};
        c.setOnChronometerTickListener(ch -> ticks[0]++);
        long now = SystemClock.elapsedRealtime();
        c.setBase(now - 65400);
        check("elapsed text", "01:05".equals(c.getText().toString()), c.getText());
        check("tick on setBase", ticks[0] == 1, ticks[0]);
        c.setFormat("Time: %s");
        c.setBase(now - 3725400);
        check("format and hours", "Time: 1:02:05".equals(c.getText().toString()), c.getText());
        c.setFormat(null);
        c.setCountDown(true);
        c.setBase(SystemClock.elapsedRealtime() + 10500);
        check("count down", c.isCountDown() && "00:10".equals(c.getText().toString()), c.getText());
        c.setBase(SystemClock.elapsedRealtime() - 5500);
        check("negative", "\u221200:05".equals(c.getText().toString()), c.getText());
        check("base", c.getBase() < SystemClock.elapsedRealtime(), c.getBase());
    }

    private static void textClock(Activity activity) {
        TextClock clock = new TextClock(activity);
        check("default 12h", TextClock.DEFAULT_FORMAT_12_HOUR.equals(clock.getFormat12Hour())
                && !clock.is24HourModeEnabled(), clock.getFormat12Hour());
        clock.setTimeZone("UTC");
        clock.setFormat12Hour("HH:mm:ss");
        String text = clock.getText().toString();
        check("formatted", text.matches("\\d\\d:\\d\\d:\\d\\d"), text);
        check("time zone", "UTC".equals(clock.getTimeZone()), clock.getTimeZone());
        clock.setFormat12Hour("'at' h 'o''clock'");
        String quoted = clock.getText().toString();
        check("quoted text", quoted.matches("at \\d{1,2} o'clock"), quoted);
    }
}
