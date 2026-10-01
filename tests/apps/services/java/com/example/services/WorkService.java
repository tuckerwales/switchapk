package com.example.services;

import android.app.IntentService;
import android.content.Intent;
import android.os.Looper;

public class WorkService extends IntentService {
    public WorkService() { super("work"); }

    @Override
    protected void onHandleIntent(Intent intent) {
        boolean main = Looper.myLooper() == Looper.getMainLooper();
        T.ev("work " + intent.getIntExtra("n", -1) + (main ? " main" : " worker"));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        T.ev("work onDestroy");
    }
}
