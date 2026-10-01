package com.example.text;

import android.app.Activity;
import android.os.Bundle;

public class TextActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SelfTest.run();
        setContentView(new LayoutsView(this));
    }
}
