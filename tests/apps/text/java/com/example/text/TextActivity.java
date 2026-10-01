package com.example.text;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class TextActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SelfTest.run();
        setContentView(R.layout.main);
        WidgetChecks.run(this);
        SpannableString s = new SpannableString("Red bold bg");
        s.setSpan(new ForegroundColorSpan(Color.RED), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new StyleSpan(Typeface.BOLD), 4, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new BackgroundColorSpan(0xFF00FF00), 9, 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView) findViewById(R.id.spans)).setText(s);
        final View typed = findViewById(R.id.typed);
        final boolean[] sawLetter = new boolean[1];
        EditText edit = (EditText) findViewById(R.id.edit);
        edit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            public void afterTextChanged(Editable s) {
                String t = s.toString();
                if ("helloa".equals(t)) sawLetter[0] = true;
                if ("hello".equals(t) && sawLetter[0]) typed.setBackgroundColor(0xFF4CAF50);
                else if (t.length() > 0) typed.setBackgroundColor(0xFFF44336);
            }
        });
        View status = findViewById(R.id.status);
        status.setBackgroundColor(SelfTest.failures == 0 ? 0xFF4CAF50 : 0xFFF44336);
    }
}
