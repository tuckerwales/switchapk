package com.example.times;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.TimePicker;
import java.util.Locale;

/** Logic checks for the time widgets. Each logs "TMCHECK ok name" or a failure. */
final class TimeChecks {
    static final String TAG = "TimeTest";

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "TMCHECK ok " + name);
        else Log.e(TAG, "TMCHECK FAIL " + name + " " + detail);
    }

    static void run(Activity activity) {
        check("pattern Hm", "HH:mm".equals(DateFormat.getBestDateTimePattern(Locale.US, "Hm")),
                DateFormat.getBestDateTimePattern(Locale.US, "Hm"));
        clock(activity);
        spinner(activity);
        dialog(activity);
    }

    private static void clock(Activity activity) {
        TimePicker p = new TimePicker(activity);
        final int[] calls = new int[1];
        p.setOnTimeChangedListener((view, h, m) -> calls[0]++);
        check("clock 12-hour default", !p.is24HourView(), null);
        p.setHour(15);
        p.setMinute(70);
        check("clock set", p.getHour() == 15 && p.getMinute() == 59 && calls[0] == 2,
                p.getHour() + ":" + p.getMinute() + " " + calls[0]);
        p.setHour(15);
        check("clock same hour no callback", calls[0] == 2, calls[0]);
        p.setIs24HourView(true);
        check("clock 24-hour keeps time", p.is24HourView() && p.getHour() == 15
                && p.getCurrentHour() == 15 && p.getCurrentMinute() == 59, p.getHour());
        p.setHour(0);
        check("clock midnight", p.getHour() == 0, p.getHour());
        p.setIs24HourView(false);
        p.setHour(12);
        check("clock noon", p.getHour() == 12, p.getHour());
        check("clock no baseline", p.getBaseline() == -1, p.getBaseline());
        check("clock validates", p.validateInput(), null);
        check("clock class name", "android.widget.TimePicker".equals(p.getAccessibilityClassName().toString()),
                p.getAccessibilityClassName());
    }

    private static void spinner(Activity activity) {
        TimePicker p = (TimePicker) activity.findViewById(R.id.spin);
        p.setHour(0);
        check("spinner midnight", p.getHour() == 0, p.getHour());
        p.setHour(12);
        check("spinner noon", p.getHour() == 12, p.getHour());
        p.setHour(23);
        p.setIs24HourView(true);
        check("spinner 24-hour keeps time", p.getHour() == 23 && p.is24HourView(), p.getHour());
        p.setIs24HourView(false);
        check("spinner back to 12-hour", p.getHour() == 23 && !p.is24HourView(), p.getHour());
        p.setEnabled(false);
        check("spinner disabled", !p.isEnabled(), null);
        p.setEnabled(true);
    }

    private static void dialog(Activity activity) {
        TimePickerDialog d = new TimePickerDialog(activity, null, 13, 5, true);
        Bundle b = d.onSaveInstanceState();
        check("dialog initial", b.getInt("hour") == 13 && b.getInt("minute") == 5
                && b.getBoolean("is24hour"), b);
        d.updateTime(7, 30);
        b = d.onSaveInstanceState();
        check("dialog update", b.getInt("hour") == 7 && b.getInt("minute") == 30, b);
    }
}
