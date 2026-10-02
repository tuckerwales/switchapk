package com.example.dates;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CalendarView;
import android.widget.DatePicker;
import android.widget.TextView;
import java.util.Calendar;

/**
 * A calendar-mode DatePicker (February 2024), a spinner-mode DatePicker, a button opening a
 * DatePickerDialog and a button swapping the page for a CalendarView. Every change is logged as
 * "DATE <source> y-m-d" (month 0-based).
 */
public class MainActivity extends Activity {
    private TextView mStatus;

    void status(String s) {
        mStatus.setText(s);
        Log.i("DATE", s);
    }

    static String ymd(int y, int m, int d) {
        return y + "-" + m + "-" + d;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        mStatus = (TextView) findViewById(R.id.status);
        DateChecks.run(this);

        DatePicker cal = (DatePicker) findViewById(R.id.cal);
        cal.init(2024, Calendar.FEBRUARY, 14, (view, y, m, d) -> status("cal " + ymd(y, m, d)));

        DatePicker spin = (DatePicker) findViewById(R.id.spin);
        spin.init(2024, Calendar.JANUARY, 31, (view, y, m, d) -> status("spin " + ymd(y, m, d)));

        findViewById(R.id.open).setOnClickListener(v -> {
            DatePickerDialog dialog = new DatePickerDialog(this,
                    (view, y, m, d) -> status("dialog " + ymd(y, m, d)), 2024, Calendar.JUNE, 15);
            dialog.show();
        });

        CalendarView calview = (CalendarView) findViewById(R.id.calview);
        // Hidden in code: CalendarView passes its AttributeSet to the DayPickerView inside it, so an
        // android:visibility="gone" in the layout would hide that inner view for good (as on Android).
        calview.setVisibility(View.GONE);
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2024, Calendar.AUGUST, 8);
        calview.setDate(c.getTimeInMillis(), false, false);
        calview.setOnDateChangeListener((view, y, m, d) -> status("calview " + ymd(y, m, d)));
        findViewById(R.id.showcal).setOnClickListener(v -> {
            findViewById(R.id.page).setVisibility(View.GONE);
            calview.setVisibility(View.VISIBLE);
            status("calendar shown");
        });
    }
}
