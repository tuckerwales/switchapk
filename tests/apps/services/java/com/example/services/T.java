package com.example.services;

import android.util.Log;
import java.util.ArrayList;

/** Event recorder shared by the components under test. */
final class T {
    static final ArrayList<String> events = new ArrayList<String>();

    static synchronized void ev(String e) {
        events.add(e);
        Log.i("SVC", "ev " + e);
    }

    static synchronized ArrayList<String> take() {
        ArrayList<String> out = new ArrayList<String>(events);
        events.clear();
        return out;
    }

    private T() {}
}
