package com.example.holocal;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.Calendar;

/**
 * Holo CalendarView (week list) on March 2024 with the 15th selected. Taps and a swipe are logged
 * as "HOLO day y-m-d" (month 0-based) only when the selected day changes. Logic checks log
 * "HLCHECK ok name".
 */
public class MainActivity extends Activity {
    private static final String TAG = "HoloCal";
    private String mLast = "";
    private TextView mStatus;

    void status(String s) {
        mStatus.setText(s);
        Log.i(TAG, s);
    }

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "HLCHECK ok " + name);
        else Log.e(TAG, "HLCHECK FAIL " + name + " " + detail);
    }

    static long midnight(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m, d);
        return c.getTimeInMillis();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        mStatus = (TextView) findViewById(R.id.status);

        CalendarView material = new CalendarView(this);
        check("material mode ignores week count", material.getShownWeekCount() == 0
                && !material.getShowWeekNumber() && material.getSelectedDateVerticalBar() == null,
                material.getShownWeekCount());

        CalendarView probe = new CalendarView(this, null, 0, android.R.style.Widget_CalendarView);
        check("holo week count", probe.getShownWeekCount() == 6, probe.getShownWeekCount());
        check("holo week numbers", probe.getShowWeekNumber(), null);
        check("holo focused color", probe.getFocusedMonthDateColor() == 0xFFFFFFFF,
                Integer.toHexString(probe.getFocusedMonthDateColor()));
        check("holo week background", probe.getSelectedWeekBackgroundColor() == 0x330099FF,
                Integer.toHexString(probe.getSelectedWeekBackgroundColor()));
        check("holo vertical bar", probe.getSelectedDateVerticalBar() != null, null);
        check("holo first day", probe.getFirstDayOfWeek() == Calendar.SUNDAY, probe.getFirstDayOfWeek());
        probe.setFirstDayOfWeek(Calendar.MONDAY);
        check("first day set", probe.getFirstDayOfWeek() == Calendar.MONDAY, probe.getFirstDayOfWeek());
        probe.setShowWeekNumber(false);
        check("week number hidden", !probe.getShowWeekNumber(), null);
        probe.setShownWeekCount(4);
        check("shown weeks set", probe.getShownWeekCount() == 4, probe.getShownWeekCount());
        probe.setWeekNumberColor(0xFF112233);
        probe.setWeekSeparatorLineColor(0xFF445566);
        probe.setUnfocusedMonthDateColor(0xFF778899);
        check("color setters", probe.getWeekNumberColor() == 0xFF112233
                && probe.getWeekSeparatorLineColor() == 0xFF445566
                && probe.getUnfocusedMonthDateColor() == 0xFF778899, null);

        final long march15 = midnight(2024, Calendar.MARCH, 15);
        final int[] calls = new int[1];
        probe.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            public void onSelectedDayChange(CalendarView view, int year, int month, int day) {
                calls[0]++;
            }
        });
        probe.setDate(march15, false, true);
        Calendar got = Calendar.getInstance();
        got.setTimeInMillis(probe.getDate());
        check("setDate", got.get(Calendar.YEAR) == 2024 && got.get(Calendar.MONTH) == Calendar.MARCH
                && got.get(Calendar.DAY_OF_MONTH) == 15 && calls[0] == 1,
                got.get(Calendar.YEAR) + "-" + got.get(Calendar.MONTH) + "-" + got.get(Calendar.DAY_OF_MONTH)
                        + " calls " + calls[0]);
        probe.setDate(march15, false, false);
        check("same date no callback", calls[0] == 1, calls[0]);
        probe.setMinDate(midnight(2024, Calendar.APRIL, 2));
        got.setTimeInMillis(probe.getDate());
        check("min clamps", got.get(Calendar.MONTH) == Calendar.APRIL && got.get(Calendar.DAY_OF_MONTH) == 2,
                got.get(Calendar.MONTH) + "-" + got.get(Calendar.DAY_OF_MONTH));
        // Restore a min below the max. April 2 is past the new max, so the selected day moves to April 1.
        probe.setMinDate(midnight(1900, Calendar.JANUARY, 1));
        probe.setMaxDate(midnight(2024, Calendar.APRIL, 1));
        boolean threw = false;
        try {
            probe.setDate(midnight(2024, Calendar.MAY, 1));
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        got.setTimeInMillis(probe.getDate());
        check("past max throws", threw && got.get(Calendar.MONTH) == Calendar.APRIL
                && got.get(Calendar.DAY_OF_MONTH) == 1, threw + " " + got.get(Calendar.DAY_OF_MONTH));

        final CalendarView cal = new CalendarView(this, null, 0, android.R.style.Widget_CalendarView);
        cal.setFocusedMonthDateColor(0xFF000000);
        cal.setUnfocusedMonthDateColor(0xFF9E9E9E);
        cal.setWeekNumberColor(0xFF008577);
        cal.setSelectedWeekBackgroundColor(0x33008577);
        cal.setWeekSeparatorLineColor(0xFFBDBDBD);
        cal.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            public void onSelectedDayChange(CalendarView view, int year, int month, int day) {
                String s = "day " + year + "-" + month + "-" + day;
                if (!s.equals(mLast)) {
                    mLast = s;
                    status(s);
                }
            }
        });
        cal.setDate(march15, false, true);
        LinearLayout root = (LinearLayout) findViewById(R.id.root);
        root.addView(cal, 0, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        cal.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    public void onGlobalLayout() {
                        cal.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        laidOut(cal);
                    }
                });
    }

    private void laidOut(CalendarView cal) {
        ListView list = (ListView) cal.findViewById(android.R.id.list);
        check("six weeks", list != null && list.getChildCount() >= 6 && list.getChildCount() <= 7,
                list == null ? "no list" : list.getChildCount());
        String month = findText(cal, "March");
        check("month title", month != null && month.contains("2024"), month);
        String days = headerDays(cal);
        check("day header", "S M T W T F S".equals(days), days);
        // March 2024 opens on the week of the 1st (a Friday). The 15th is the Friday of the
        // third row. Columns are the week number, then Sunday through Saturday.
        View row = list.getChildAt(2);
        int cell = list.getWidth() / 8;
        check("week row", row != null && cell > 40 && row.getHeight() > 40,
                "cell " + cell + " row " + (row == null ? "null" : row.getHeight()));
    }

    /** The day-name header, skipping the week-number cell, joined with spaces. */
    private static String headerDays(View root) {
        StringBuilder out = new StringBuilder();
        collectShortDays(root, out);
        return out.toString().trim();
    }

    private static void collectShortDays(View v, StringBuilder out) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.length() == 1 && v.getVisibility() == View.VISIBLE) {
                if (out.length() > 0) out.append(' ');
                out.append(t);
            }
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectShortDays(g.getChildAt(i), out);
        }
    }

    private static String findText(View v, String contains) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.toString().contains(contains)) return t.toString();
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                String found = findText(g.getChildAt(i), contains);
                if (found != null) return found;
            }
        }
        return null;
    }
}
