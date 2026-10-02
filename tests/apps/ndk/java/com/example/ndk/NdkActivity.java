package com.example.ndk;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Runs JNI checks against libndktest.so (loaded from the APK's lib/<abi>/ with its dependency libndkdep.so) and
 * shows a green panel when all pass, red otherwise. Each check is logged as "NDK CHECK".
 */
public class NdkActivity extends Activity {
    private static final String TAG = "ndk";
    private int mFailed;
    private final StringBuilder mReport = new StringBuilder();

    private void check(String name, boolean ok, Object got) {
        Log.i(TAG, "NDK CHECK " + (ok ? "ok   " : "FAIL ") + name + ": " + got);
        if (!ok) {
            mFailed++;
            mReport.append("FAIL ").append(name).append(": ").append(got).append('\n');
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            runChecks();
        } catch (Throwable t) {
            Log.e(TAG, "NDK CHECK FAIL: exception", t);
            mFailed++;
            mReport.append("exception: ").append(t).append('\n');
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(mFailed == 0 ? 0xFF2E7D32 : 0xFFC62828);
        TextView text = new TextView(this);
        text.setTextColor(Color.WHITE);
        text.setTextSize(20);
        text.setGravity(Gravity.CENTER);
        text.setText(mFailed == 0 ? "All NDK checks passed" : mFailed + " NDK checks failed\n" + mReport);
        root.addView(text, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        Log.i(TAG, "NDK CHECKS DONE, failed " + mFailed);
    }

    private static boolean loadByPath(String path) {
        try {
            System.load(path); // already loaded through DT_NEEDED: found by name
            return true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "System.load", e);
            return false;
        }
    }

    private void runChecks() {
        String hello = Native.hello("Wörld");
        check("strings", "Hello, Wörld from C".equals(hello), hello);
        check("int array", Native.sum(new int[] {1, 2, 3, 4, 5}) == 15, Native.sum(new int[] {1, 2, 3, 4, 5}));
        byte[] b = Native.bytes(10);
        check("byte array", b.length == 10 && b[9] == 27, b.length + "/" + b[9]);
        int cb = Native.callback(new Native.Callback() {
            public int onValue(int x) {
                return x + 1;
            }
        }, 20);
        check("call into Java", cb == 42, cb);
        String msg = null;
        try {
            Native.fail("from native");
        } catch (IllegalStateException e) {
            msg = e.getMessage();
        }
        check("ThrowNew", "from native".equals(msg), msg);
        String asset = Native.asset(getAssets(), "hello.txt");
        check("AAssetManager", asset != null && asset.startsWith("asset text from the APK"), asset);
        int threads = Native.threads(4);
        check("pthreads + static mutex", threads == 4000, threads);
        check("RegisterNatives in JNI_OnLoad", Native.registered(6) == 42, Native.registered(6));
        int dep = Native.depValue();
        check("DT_NEEDED dependency, its constructor, JNI_OnLoad", dep == 1142, dep);
        check("unresolved import returns 0", Native.missing() == 0, Native.missing());
        String f = Native.format(3.14159);
        check("varargs snprintf", "3.142|str|-12|ff".equals(f), f);
        check("libc (qsort callback, strtod, sinf)", Native.libc() == 1, Native.libc());
        int ft = Native.fromThread(new Native.Callback() {
            public int onValue(int x) {
                return x * 2;
            }
        });
        check("AttachCurrentThread from a pthread", ft == 42, ft);
        check("dlopen + dlsym", Native.dlsymDep() == 42, Native.dlsymDep());
        Native.Point p = new Native.Point();
        int fs = Native.fieldSum(p);
        check("fields", fs == 102 && p.x == 100, fs + "/" + p.x);
        String libDir = getApplicationInfo().nativeLibraryDir;
        String abiDir = "x86_64".equals(android.os.Build.SUPPORTED_ABIS[0]) ? "x86_64" : "arm64";
        check("nativeLibraryDir", ("/data/app/com.example.ndk/lib/" + abiDir).equals(libDir)
                && System.getProperty("os.arch").equals("x86_64".equals(abiDir) ? "x86_64" : "aarch64"), libDir);
        check("System.load(nativeLibraryDir path)", loadByPath(libDir + "/libndkdep.so"), libDir);
        boolean again = true;
        try {
            System.loadLibrary("ndktest"); // already loaded: no-op, JNI_OnLoad not run again
        } catch (Throwable t) {
            again = false;
        }
        check("loadLibrary twice", again, again);
        String err = null;
        try {
            System.loadLibrary("doesnotexist");
        } catch (UnsatisfiedLinkError e) {
            err = e.getMessage();
        }
        check("missing library throws UnsatisfiedLinkError", err != null && err.contains("libdoesnotexist.so"), err);
    }
}
