package java.lang;

import java.util.ArrayList;

public class Runtime {
    private static final Runtime currentRuntime = new Runtime();
    private final ArrayList<Thread> shutdownHooks = new ArrayList<Thread>();

    private Runtime() {
    }

    public static Runtime getRuntime() {
        return currentRuntime;
    }

    public void exit(int status) {
        ArrayList<Thread> hooks;
        synchronized (shutdownHooks) {
            hooks = new ArrayList<Thread>(shutdownHooks);
            shutdownHooks.clear();
        }
        for (Thread t : hooks) {
            try {
                t.run();
            } catch (Throwable e) {
            }
        }
        System.nativeExit(status);
    }

    public void halt(int status) {
        System.nativeExit(status);
    }

    public void addShutdownHook(Thread hook) {
        synchronized (shutdownHooks) {
            shutdownHooks.add(hook);
        }
    }

    public boolean removeShutdownHook(Thread hook) {
        synchronized (shutdownHooks) {
            return shutdownHooks.remove(hook);
        }
    }

    public native int availableProcessors();

    public native long freeMemory();

    public native long totalMemory();

    public native long maxMemory();

    public void gc() {
        System.gc();
    }

    public void runFinalization() {
    }

    private static native String nativeLoad(String filename, boolean isLibName);

    public void load(String filename) {
        String err = nativeLoad(filename, false);
        if (err != null) {
            throw new UnsatisfiedLinkError(err);
        }
    }

    public void loadLibrary(String libname) {
        String err = nativeLoad(libname, true);
        if (err != null) {
            throw new UnsatisfiedLinkError(err);
        }
    }

    public Process exec(String command) throws java.io.IOException {
        throw new java.io.IOException("Cannot run program \"" + command + "\": not supported");
    }

    public Process exec(String[] cmdarray) throws java.io.IOException {
        throw new java.io.IOException("Cannot run program: not supported");
    }

    public Process exec(String command, String[] envp) throws java.io.IOException {
        return exec(command);
    }

    public Process exec(String command, String[] envp, java.io.File dir) throws java.io.IOException {
        return exec(command);
    }

    public Process exec(String[] cmdarray, String[] envp) throws java.io.IOException {
        return exec(cmdarray);
    }

    public Process exec(String[] cmdarray, String[] envp, java.io.File dir) throws java.io.IOException {
        return exec(cmdarray);
    }

    @Deprecated
    public static void runFinalizersOnExit(boolean value) {
        throw new UnsupportedOperationException();
    }

    public void traceInstructions(boolean on) {
    }

    public void traceMethodCalls(boolean on) {
    }
}
