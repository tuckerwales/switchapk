package com.example.widgets;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.Switch;

public class WidgetsActivity extends Activity {
    private boolean mButton;
    private boolean mCheck;
    private boolean mRadio;
    private boolean mSwitch;
    private View mStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        WidgetChecks.prepare(this);
        mStatus = findViewById(R.id.status);

        ((Button) findViewById(R.id.button)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mButton = true;
                refresh();
            }
        });
        ((CheckBox) findViewById(R.id.check)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                mCheck = isChecked;
                refresh();
            }
        });
        ((RadioGroup) findViewById(R.id.group)).setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                mRadio = checkedId == R.id.radio_b;
                refresh();
            }
        });
        final Switch sw = (Switch) findViewById(R.id.switcher);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                sw.setTrackDrawable(new ColorDrawable(isChecked ? 0xFF4CAF50 : 0xFFBDBDBD));
                mSwitch = isChecked;
                refresh();
            }
        });
        refresh();
    }

    private void refresh() {
        int color = 0xFF9E9E9E;
        if (WidgetChecks.failures > 0) color = 0xFFF44336;
        else if (mButton && mCheck && mRadio && mSwitch) color = 0xFF4CAF50;
        mStatus.setBackgroundColor(color);
    }
}
