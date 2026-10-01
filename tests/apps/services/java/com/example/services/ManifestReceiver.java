package com.example.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ReceiverCallNotAllowedException;

public class ManifestReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String tag = intent.getStringExtra("tag");
        T.ev("manifest " + intent.getAction() + " " + tag + (isOrderedBroadcast() ? " ordered" : ""));
        if (isOrderedBroadcast()) {
            String data = getResultData();
            setResultData((data != null ? data : "") + "m");
        }
        if ("restricted".equals(tag)) {
            try {
                context.registerReceiver(this, new IntentFilter("x"));
                T.ev("restricted register allowed");
            } catch (ReceiverCallNotAllowedException e) {
                T.ev("restricted register refused");
            }
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            T.ev("restricted sticky " + (battery != null));
        }
    }
}
