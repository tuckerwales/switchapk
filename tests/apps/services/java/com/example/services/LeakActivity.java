package com.example.services;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

/** Registers a receiver and binds a service, then finishes without cleaning up. */
public class LeakActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) { T.ev("leaked receiver got " + intent.getStringExtra("tag")); }
        }, new IntentFilter(MainActivity.PING));
        bindService(new Intent(this, BindService.class), new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service) {
                T.ev("leak connected");
                finish();
            }

            public void onServiceDisconnected(ComponentName name) { T.ev("leak disconnected"); }
        }, BIND_AUTO_CREATE);
    }
}
