package com.example.procs;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Runs libechoexe.so (a bionic executable shipped in lib/<abi>, as apps ship chess engines and helpers)
 * through ProcessBuilder and logs what comes back. tests/apps/procs/check_shots.py reads the log.
 */
public class ProcsActivity extends Activity {
    private static final String TAG = "PROCS";

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8").replace("\n", "|");
    }

    private void log(String s) { Log.i(TAG, s); }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        View v = new View(this);
        v.setBackgroundColor(0xFF43A047);
        setContentView(v);
        try {
            run();
        } catch (Exception e) {
            Log.e(TAG, "failed", e);
            v.setBackgroundColor(0xFFE53935);
        }
    }

    private void run() throws Exception {
        File exe = new File(getApplicationInfo().nativeLibraryDir, "libechoexe.so");
        log("lib exists=" + exe.exists() + " canExecute=" + exe.canExecute());

        ProcessBuilder pb = new ProcessBuilder(exe.getPath(), "hello");
        pb.environment().put("PROCS_VAR", "abc");
        pb.directory(getFilesDir());
        Process p = pb.start();
        OutputStream in = p.getOutputStream();
        in.write("abc\nxyz\nquit\n".getBytes("UTF-8"));
        in.close();
        String out = readAll(p.getInputStream());
        String err = readAll(p.getErrorStream());
        int code = p.waitFor();
        log("run1 out=" + out);
        log("run1 err=" + err + " code=" + code + " exitValue=" + p.exitValue());
        File rel = new File(getFilesDir(), "rel.txt");
        byte[] relBytes = new byte[32];
        FileInputStream rin = new FileInputStream(rel);
        int rn = rin.read(relBytes);
        rin.close();
        log("rel.txt=" + new String(relBytes, 0, rn, "UTF-8"));

        // a copy in the app's files (DroidFish copies its engine and chmods it)
        File copy = new File(getFilesDir(), "engine.exe");
        FileInputStream cin = new FileInputStream(exe);
        FileOutputStream cout = new FileOutputStream(copy);
        byte[] buf = new byte[8192];
        int n;
        while ((n = cin.read(buf)) > 0) cout.write(buf, 0, n);
        cin.close();
        cout.close();
        copy.setExecutable(true, false);
        Process p2 = new ProcessBuilder(copy.getPath(), "exit").redirectErrorStream(true).start();
        p2.getOutputStream().write("again\n".getBytes("UTF-8"));
        p2.getOutputStream().close();
        String out2 = readAll(p2.getInputStream());
        log("run2 out=" + out2 + " code=" + p2.waitFor());

        Process p3 = Runtime.getRuntime().exec(new String[] {exe.getPath(), "thread-exit"});
        log("run3 out=" + readAll(p3.getInputStream()) + " code=" + p3.waitFor());

        Process p4 = new ProcessBuilder(exe.getPath()).start();
        boolean running = true;
        try {
            p4.exitValue();
            running = false;
        } catch (IllegalThreadStateException e) {
            // still waiting for stdin
        }
        p4.destroy();
        int c4 = p4.waitFor();
        log("run4 running=" + running + " destroyed code=" + c4);

        for (String prog : new String[] {"sh", "/system/bin/missing", getFilesDir() + "/rel.txt"}) {
            try {
                new ProcessBuilder(prog, "-c", "true").start();
                log("start " + prog + " ok?");
            } catch (IOException e) {
                String m = e.getMessage();
                log("start fails " + m.substring(m.indexOf("error=")));
            }
        }
        log("done");
    }
}
