package com.example.times;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.TimePicker;

/**
 * A clock-mode TimePicker (9:41 AM), a spinner-mode TimePicker (11:59 AM) and a button opening a
 * 24-hour TimePickerDialog. Every change is logged as "TIME <source> h:m".
 */
public class MainActivity extends Activity {
    private TextView mStatus;

    void status(String s) {
        mStatus.setText(s);
        Log.i("TIME", s);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        mStatus = (TextView) findViewById(R.id.status);
        TimeChecks.run(this);

        TimePicker clock = (TimePicker) findViewById(R.id.clock);
        clock.setHour(9);
        clock.setMinute(41);
        clock.setOnTimeChangedListener((view, h, m) -> status("clock " + h + ":" + m));

        TimePicker spin = (TimePicker) findViewById(R.id.spin);
        spin.setHour(11);
        spin.setMinute(59);
        spin.setOnTimeChangedListener((view, h, m) -> status("spin " + h + ":" + m));

        findViewById(R.id.open).setOnClickListener(v -> new TimePickerDialog(this,
                (view, h, m) -> status("dialog " + h + ":" + m), 18, 30, true).show());
    }
}
