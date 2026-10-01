package com.example.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Started service: stops itself on the latest start id only. */
public class CountService extends Service {
    @Override
    public void onCreate() { T.ev("count onCreate"); }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        T.ev("count start " + intent.getIntExtra("n", -1) + " id=" + startId);
        if (intent.getBooleanExtra("stop", false)) {
            T.ev("count stopSelfResult(1)=" + stopSelfResult(1));
            T.ev("count stopSelfResult(" + startId + ")=" + stopSelfResult(startId));
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() { T.ev("count onDestroy"); }
}
