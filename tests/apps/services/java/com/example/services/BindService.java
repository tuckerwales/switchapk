package com.example.services;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/** Bound service with the local binder pattern; asks for onRebind. */
public class BindService extends Service {
    public class LocalBinder extends Binder {
        BindService getService() { return BindService.this; }
    }

    private final LocalBinder mBinder = new LocalBinder();

    int add(int a, int b) { return a + b; }

    @Override
    public void onCreate() { T.ev("bind onCreate"); }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        T.ev("bind start id=" + startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        T.ev("bind onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        T.ev("bind onUnbind");
        return true;
    }

    @Override
    public void onRebind(Intent intent) { T.ev("bind onRebind"); }

    @Override
    public void onDestroy() { T.ev("bind onDestroy"); }
}
