package java.lang;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * A "child process": a bionic executable run inside switchapk by the native loader
 * (src/nativeloader/spawn.c) on its own thread, with its standard streams connected to these
 * Process streams through in-memory pipes. File and INHERIT redirects are served by pump
 * threads. destroy() can only end the streams (threads cannot be killed). No shell: the
 * program must be an executable path, as Android's own exec needs.
 */
final class ProcessImpl extends Process {
    private static final int STDIN = 0;
    private static final int STDOUT = 1;
    private static final int STDERR = 2;

    private final long handle;
    private final OutputStream stdin;
    private final InputStream stdout;
    private final InputStream stderr;

    private ProcessImpl(long handle, ProcessBuilder.Redirect[] redirects, boolean mergeErr) throws IOException {
        this.handle = handle;
        ProcessBuilder.Redirect in = redirects[0], out = redirects[1], err = redirects[2];
        OutputStream rawIn = new PipeOutput();
        InputStream rawOut = new PipeInput(STDOUT);
        InputStream rawErr = mergeErr ? new NullInput() : new PipeInput(STDERR);

        if (in.type() == ProcessBuilder.Redirect.Type.PIPE) {
            stdin = rawIn;
        } else {
            stdin = new NullOutput();
            InputStream src = in.type() == ProcessBuilder.Redirect.Type.READ ? new FileInputStream(in.file()) : null;
            if (src != null) pump(src, rawIn, true, "stdin");
            else rawIn.close();
        }
        stdout = redirected(rawOut, out, System.out, "stdout");
        stderr = mergeErr ? rawErr : redirected(rawErr, err, System.err, "stderr");
    }

    private static InputStream redirected(InputStream raw, ProcessBuilder.Redirect r, OutputStream inherit, String name)
            throws IOException {
        switch (r.type()) {
            case PIPE:
                return raw;
            case INHERIT:
                pump(raw, inherit, false, name);
                return new NullInput();
            default:
                pump(raw, new FileOutputStream(r.file(), r.type() == ProcessBuilder.Redirect.Type.APPEND), true, name);
                return new NullInput();
        }
    }

    private static void pump(final InputStream from, final OutputStream to, final boolean closeTo, String name) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                byte[] buf = new byte[8192];
                try {
                    int n;
                    while ((n = from.read(buf)) > 0) {
                        to.write(buf, 0, n);
                        to.flush();
                    }
                } catch (IOException ignored) {
                    // the process or the destination went away
                } finally {
                    try { from.close(); } catch (IOException ignored) {}
                    if (closeTo) {
                        try { to.close(); } catch (IOException ignored) {}
                    }
                }
            }
        }, "process " + name);
        t.setDaemon(true);
        t.start();
    }

    static Process start(String[] cmdarray, Map<String, String> environment, File dir,
            ProcessBuilder.Redirect[] redirects, boolean redirectErrorStream) throws IOException {
        String prog = cmdarray[0];
        String[] envp;
        if (environment == null) environment = System.getenv();
        envp = new String[environment.size()];
        int i = 0;
        for (Map.Entry<String, String> e : environment.entrySet()) envp[i++] = e.getKey() + "=" + e.getValue();
        String path = prog;
        if (prog.indexOf('/') < 0) {
            // Android's exec searches PATH (/system/bin and the like): none of those programs exist here
            throw new IOException("Cannot run program \"" + prog + "\"" + inDir(dir)
                    + ": error=2, No such file or directory");
        }
        if (!path.startsWith("/") && dir != null) path = new File(dir, path).getPath();
        long h;
        try {
            h = nSpawn(path, cmdarray, envp, dir == null ? null : dir.getPath(), redirectErrorStream);
        } catch (IOException e) {
            throw new IOException("Cannot run program \"" + prog + "\"" + inDir(dir) + ": " + e.getMessage(), e);
        }
        return new ProcessImpl(h, redirects, redirectErrorStream);
    }

    private static String inDir(File dir) {
        return dir == null ? "" : " (in directory \"" + dir + "\")";
    }

    @Override public OutputStream getOutputStream() { return stdin; }
    @Override public InputStream getInputStream() { return stdout; }
    @Override public InputStream getErrorStream() { return stderr; }

    @Override
    public int waitFor() throws InterruptedException {
        return nWaitFor(handle);
    }

    @Override
    public int exitValue() {
        int v = nExitValue(handle);
        if (v < 0) throw new IllegalThreadStateException("process hasn't exited");
        return v;
    }

    @Override
    public void destroy() {
        nDestroy(handle);
    }

    @Override
    public String toString() {
        int v = nExitValue(handle);
        return "Process[handle=" + Long.toHexString(handle) + (v < 0 ? "]" : " ,hasExited=true, exitcode=" + v + "]");
    }

    private final class PipeInput extends InputStream {
        private final int which;
        private boolean closed;

        PipeInput(int which) { this.which = which; }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int n = read(b, 0, 1);
            return n <= 0 ? -1 : b[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
            if (len == 0) return 0;
            if (closed) throw new IOException("Stream closed");
            return nRead(handle, which, b, off, len);
        }

        @Override
        public int available() throws IOException {
            return closed ? 0 : nAvailable(handle, which);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            nCloseRead(handle, which);
        }
    }

    private final class PipeOutput extends OutputStream {
        private boolean closed;

        @Override
        public void write(int b) throws IOException {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
            if (closed) throw new IOException("Stream closed");
            if (len > 0) nWrite(handle, b, off, len);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            nCloseWrite(handle);
        }
    }

    private static final class NullInput extends InputStream {
        @Override public int read() { return -1; }
        @Override public int available() { return 0; }
    }

    private static final class NullOutput extends OutputStream {
        @Override public void write(int b) throws IOException { throw new IOException("Stream closed"); }
    }

    private static native long nSpawn(String path, String[] argv, String[] envp, String dir, boolean mergeErr)
            throws IOException;
    private static native int nRead(long handle, int which, byte[] b, int off, int len) throws IOException;
    private static native void nWrite(long handle, byte[] b, int off, int len) throws IOException;
    private static native int nAvailable(long handle, int which);
    private static native void nCloseRead(long handle, int which);
    private static native void nCloseWrite(long handle);
    private static native int nWaitFor(long handle);
    private static native int nExitValue(long handle);
    private static native void nDestroy(long handle);
}
