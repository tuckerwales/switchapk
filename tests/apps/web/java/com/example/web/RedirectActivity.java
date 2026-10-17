package com.example.web;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/** AppAuth's RedirectUriReceiverActivity: hands the redirect to AuthActivity and finishes. */
public class RedirectActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Log.i(MainActivity.TAG, "redirect " + getIntent().getData());
        Intent i = new Intent(this, AuthActivity.class);
        i.setData(getIntent().getData());
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
