package com.example.text;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
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
        View status = findViewById(R.id.status);
        status.setBackgroundColor(SelfTest.failures == 0 ? 0xFF4CAF50 : 0xFFF44336);
    }
}
