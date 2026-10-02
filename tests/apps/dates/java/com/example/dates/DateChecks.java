package com.example.dates;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.CalendarView;
import android.widget.DatePicker;
import java.util.Calendar;
import java.util.Locale;

/** Logic checks for the date widgets. Each logs "DTCHECK ok name" or a failure. */
final class DateChecks {
    static final String TAG = "DateTest";

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "DTCHECK ok " + name);
        else Log.e(TAG, "DTCHECK FAIL " + name + " " + detail);
    }

    static void run(Activity activity) {
        patterns();
        calendarPicker(activity);
        spinnerPicker(activity);
        calendarView(activity);
        dialog(activity);
    }

    static long millis(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m, d);
        return c.getTimeInMillis();
    }

    private static void patterns() {
        check("pattern MMMMy", "MMMM y".equals(DateFormat.getBestDateTimePattern(Locale.US, "MMMMy")),
                DateFormat.getBestDateTimePattern(Locale.US, "MMMMy"));
        check("pattern EMMMd", "EEE, MMM d".equals(DateFormat.getBestDateTimePattern(Locale.US, "EMMMd")),
                DateFormat.getBestDateTimePattern(Locale.US, "EMMMd"));
        check("pattern yyyyMMMdd", "MMM d, y".equals(DateFormat.getBestDateTimePattern(Locale.US, "yyyyMMMdd")),
                DateFormat.getBestDateTimePattern(Locale.US, "yyyyMMMdd"));
        check("pattern hm", "h:mm a".equals(DateFormat.getBestDateTimePattern(Locale.US, "hm")),
                DateFormat.getBestDateTimePattern(Locale.US, "hm"));
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2024, Calendar.FEBRUARY, 14);
        String s = DateFormat.format("EEEEE MMMMM EEEE", c).toString();
        check("narrow names", "W F Wednesday".equals(s), s);
    }

    private static void calendarPicker(Activity activity) {
        DatePicker p = new DatePicker(activity);
        final int[] calls = new int[1];
        p.init(2024, Calendar.FEBRUARY, 29, (view, y, m, d) -> calls[0]++);
        check("init no callback", calls[0] == 0 && p.getYear() == 2024 && p.getMonth() == 1
                && p.getDayOfMonth() == 29, p.getYear() + "/" + p.getMonth() + "/" + p.getDayOfMonth());
        p.updateDate(2023, Calendar.MARCH, 3);
        check("update callback", calls[0] == 1 && p.getYear() == 2023 && p.getMonth() == 2
                && p.getDayOfMonth() == 3, calls[0]);
        check("first day default", p.getFirstDayOfWeek() == Calendar.SUNDAY, p.getFirstDayOfWeek());
        p.setFirstDayOfWeek(Calendar.MONDAY);
        check("first day set", p.getFirstDayOfWeek() == Calendar.MONDAY, p.getFirstDayOfWeek());
        boolean threw = false;
        try {
            p.setFirstDayOfWeek(9);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("first day range", threw, null);
        threw = false;
        try {
            p.getCalendarView();
        } catch (UnsupportedOperationException e) {
            threw = true;
        }
        check("calendar mode has no CalendarView", threw && !p.getSpinnersShown()
                && !p.getCalendarViewShown(), threw);
        p.setMinDate(millis(2023, Calendar.JUNE, 1));
        check("min date clamps", p.getYear() == 2023 && p.getMonth() == 5 && p.getDayOfMonth() == 1
                && calls[0] == 2, p.getMonth() + "/" + p.getDayOfMonth() + " " + calls[0]);
        p.setMaxDate(millis(2023, Calendar.DECEMBER, 31));
        Calendar max = Calendar.getInstance();
        max.setTimeInMillis(p.getMaxDate());
        check("max date", max.get(Calendar.YEAR) == 2023 && max.get(Calendar.MONTH) == 11
                && max.get(Calendar.DAY_OF_MONTH) == 31, max.getTime());
        p.setEnabled(false);
        check("disabled", !p.isEnabled(), null);
        check("class name", "android.widget.DatePicker".equals(p.getAccessibilityClassName().toString()),
                p.getAccessibilityClassName());
    }

    private static void spinnerPicker(Activity activity) {
        DatePicker p = (DatePicker) activity.findViewById(R.id.spin);
        // getSpinnersShown() is View.isShown(), which is false until the picker is attached.
        check("spinner mode", !p.getSpinnersShown() && !p.getCalendarViewShown()
                && p.getCalendarView() != null, p.getSpinnersShown());
        final int[] calls = new int[1];
        p.init(2024, Calendar.JANUARY, 31, (view, y, m, d) -> calls[0]++);
        p.updateDate(2024, Calendar.JANUARY, 31);
        check("same date no callback", calls[0] == 0, calls[0]);
        p.updateDate(2024, Calendar.FEBRUARY, 30);
        check("normalized", calls[0] == 1 && p.getMonth() == Calendar.MARCH && p.getDayOfMonth() == 1,
                p.getMonth() + "/" + p.getDayOfMonth());
        p.setCalendarViewShown(true);
        check("calendar shown", p.getCalendarViewShown(), null);
        p.setCalendarViewShown(false);
        p.setMinDate(millis(2024, Calendar.APRIL, 1));
        check("spinner min clamps", p.getMonth() == Calendar.APRIL && p.getDayOfMonth() == 1,
                p.getMonth() + "/" + p.getDayOfMonth());
        p.setMinDate(millis(1900, Calendar.JANUARY, 1));
    }

    private static void calendarView(Activity activity) {
        CalendarView v = new CalendarView(activity);
        long feb = millis(2024, Calendar.FEBRUARY, 10);
        v.setDate(feb, false, false);
        check("calview date", v.getDate() == feb, v.getDate());
        v.setMinDate(millis(2024, Calendar.MARCH, 1));
        check("calview min clamps", v.getDate() == millis(2024, Calendar.MARCH, 1), v.getDate());
        v.setFirstDayOfWeek(Calendar.MONDAY);
        check("calview first day", v.getFirstDayOfWeek() == Calendar.MONDAY, v.getFirstDayOfWeek());
        check("calview legacy setters ignored", v.getShownWeekCount() == 0 && !v.getShowWeekNumber()
                && v.getSelectedDateVerticalBar() == null, v.getShownWeekCount());
        check("calview class name", "android.widget.CalendarView".equals(v.getAccessibilityClassName().toString()),
                v.getAccessibilityClassName());
    }

    private static void dialog(Activity activity) {
        DatePickerDialog d = new DatePickerDialog(activity, null, 2022, Calendar.JULY, 4);
        DatePicker p = d.getDatePicker();
        check("dialog picker", p.getYear() == 2022 && p.getMonth() == 6 && p.getDayOfMonth() == 4,
                p.getYear() + "/" + p.getMonth() + "/" + p.getDayOfMonth());
        d.updateDate(2021, Calendar.MAY, 5);
        Bundle b = d.onSaveInstanceState();
        check("dialog state", b.getInt("year") == 2021 && b.getInt("month") == 4 && b.getInt("day") == 5, b);
        check("dialog uses calendar mode", threwOnCalendarView(p), null);
    }

    private static boolean threwOnCalendarView(DatePicker p) {
        try {
            p.getCalendarView();
            return false;
        } catch (UnsupportedOperationException e) {
            return true;
        }
    }
}
