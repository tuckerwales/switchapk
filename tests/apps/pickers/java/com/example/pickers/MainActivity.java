package com.example.pickers;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Chronometer;
import android.widget.NumberPicker;
import android.widget.TextView;

/**
 * Three selector-wheel NumberPickers (0..20 wrapping, months as displayed values, 0..2 which
 * cannot wrap), a Chronometer, a TextClock and a status line.
 */
public class MainActivity extends Activity {
    static final String[] MONTHS = {
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    };

    private TextView mStatus;

    void status(String s) {
        mStatus.setText(s);
        Log.i("PICK", s);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        mStatus = (TextView) findViewById(R.id.status);
        PickerChecks.run(this);

        NumberPicker num = (NumberPicker) findViewById(R.id.num);
        num.setMinValue(0);
        num.setMaxValue(20);
        num.setValue(5);
        num.setOnValueChangedListener((picker, oldVal, newVal) -> status("num " + oldVal + "->" + newVal));

        NumberPicker month = (NumberPicker) findViewById(R.id.month);
        month.setMinValue(0);
        month.setMaxValue(MONTHS.length - 1);
        month.setDisplayedValues(MONTHS);
        month.setOnValueChangedListener((picker, oldVal, newVal) ->
                status("month " + MONTHS[oldVal] + "->" + MONTHS[newVal]));
        month.setOnScrollListener((picker, scrollState) -> {
            if (scrollState == NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) status("month idle");
        });

        NumberPicker small = (NumberPicker) findViewById(R.id.small);
        small.setMinValue(0);
        small.setMaxValue(2);
        small.setFormatter(value -> "#" + value);
        small.setOnValueChangedListener((picker, oldVal, newVal) -> status("small " + oldVal + "->" + newVal));

        Chronometer chrono = (Chronometer) findViewById(R.id.chrono);
        chrono.setFormat("Elapsed %s");
        // Not started: a ticking chronometer redraws every second and the script waits for quiet frames.
        chrono.setBase(SystemClock.elapsedRealtime() - 65000);
    }
}
