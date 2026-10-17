package com.example.web;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

/**
 * Behaves like AppAuth's AuthorizationManagementActivity: opens the browser from onResume, and on the next onResume
 * either has the redirect (delivered through RedirectActivity with CLEAR_TOP|SINGLE_TOP) or treats it as a cancel.
 * Runs twice: the script answers the first browser with the redirect URL and closes the second.
 */
public class AuthActivity extends Activity {
    private boolean started;
    private int round;
    private TextView label;
    private boolean ok = true;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        label = new TextView(this);
        label.setTextSize(28);
        label.setBackgroundColor(Color.GRAY);
        setContentView(label);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(MainActivity.TAG, "auth stopped");
    }

    private void openBrowser() {
        started = true;
        round++;
        Uri auth = Uri.parse("https://login.example/oauth2/auth?client_id=web&state=s" + round
                + "&redirect_uri=" + Uri.encode("com.example.web:/oauth"));
        Log.i(MainActivity.TAG, "auth round " + round + " opening browser");
        startActivity(new Intent(Intent.ACTION_VIEW, auth).addCategory(Intent.CATEGORY_BROWSABLE));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!started) {
            openBrowser();
            return;
        }
        Uri data = getIntent().getData();
        if (data != null) {
            Log.i(MainActivity.TAG, "auth round " + round + " code=" + data.getQueryParameter("code") + " state="
                    + data.getQueryParameter("state"));
            ok &= round == 1 && "abc".equals(data.getQueryParameter("code"));
        } else {
            Log.i(MainActivity.TAG, "auth round " + round + " cancelled");
            ok &= round == 2;
        }
        setIntent(new Intent());
        if (round == 1) {
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    openBrowser();
                }
            }, 200);
        } else {
            Log.i(MainActivity.TAG, ok ? "all ok" : "auth flow wrong");
            label.setBackgroundColor(ok ? 0xFF43A047 : 0xFFE53935);
            label.setText(ok ? "logged in, then cancelled" : "auth flow wrong");
        }
    }
}
