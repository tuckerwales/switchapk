package com.example.ndk;

import android.content.res.AssetManager;

/** Natives in libndktest.so (native/ndktest.c). */
final class Native {
    static {
        System.loadLibrary("ndktest");
    }

    interface Callback {
        int onValue(int x);
    }

    static final class Point {
        int x = 1;
        int y = 2;
    }

    static native String hello(String name);

    static native int sum(int[] values);

    static native byte[] bytes(int n);

    static native int callback(Callback cb, int x);

    static native void fail(String msg);

    static native String asset(AssetManager am, String name);

    static native int threads(int n);

    static native int registered(int x);

    static native int depValue();

    static native int missing();

    static native String format(double d);

    static native int libc();

    static native int fromThread(Callback cb);

    static native int dlsymDep();

    static native int fieldSum(Point p);

    private Native() {}
}
