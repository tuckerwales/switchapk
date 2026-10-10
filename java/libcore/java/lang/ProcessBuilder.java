package java.lang;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Process creation. There are no child processes on the Switch (and the host
 * runtime does not spawn bionic executables), so start() fails the way a failed
 * exec does on Android: with an IOException naming the program. Apps that run
 * helper binaries (chess engines, ffmpeg) catch it and report the error.
 */
public final class ProcessBuilder {
    private List<String> command;
    private File directory;
    private Map<String, String> environment;
    private boolean redirectErrorStream;
    private Redirect[] redirects;

    public ProcessBuilder(List<String> command) {
        if (command == null) throw new NullPointerException();
        this.command = command;
    }

    public ProcessBuilder(String... command) {
        this.command = new ArrayList<String>(command.length);
        for (String arg : command) this.command.add(arg);
    }

    public ProcessBuilder command(List<String> command) {
        if (command == null) throw new NullPointerException();
        this.command = command;
        return this;
    }

    public ProcessBuilder command(String... command) {
        this.command = new ArrayList<String>(command.length);
        for (String arg : command) this.command.add(arg);
        return this;
    }

    public List<String> command() { return command; }

    public Map<String, String> environment() {
        if (environment == null) environment = new HashMap<String, String>(System.getenv());
        return environment;
    }

    ProcessBuilder environment(String[] envp) {
        if (envp != null) {
            environment = new HashMap<String, String>();
            for (String envstring : envp) {
                if (envstring.indexOf('\u0000') != -1) envstring = envstring.replaceFirst("\u0000.*", "");
                int eqlsign = envstring.indexOf('=', 1);
                if (eqlsign != -1) environment.put(envstring.substring(0, eqlsign), envstring.substring(eqlsign + 1));
            }
        }
        return this;
    }

    public File directory() { return directory; }

    public ProcessBuilder directory(File directory) {
        this.directory = directory;
        return this;
    }

    /** Where a subprocess's standard input or output goes. */
    public static abstract class Redirect {
        public enum Type { PIPE, INHERIT, READ, WRITE, APPEND }

        public static final Redirect PIPE = new Redirect() {
            public Type type() { return Type.PIPE; }
            public String toString() { return type().toString(); }
        };
        public static final Redirect INHERIT = new Redirect() {
            public Type type() { return Type.INHERIT; }
            public String toString() { return type().toString(); }
        };

        public abstract Type type();
        public File file() { return null; }
        boolean append() { throw new UnsupportedOperationException(); }

        public static Redirect from(File file) {
            if (file == null) throw new NullPointerException();
            return new FileRedirect(Type.READ, file, false);
        }

        public static Redirect to(File file) {
            if (file == null) throw new NullPointerException();
            return new FileRedirect(Type.WRITE, file, false);
        }

        public static Redirect appendTo(File file) {
            if (file == null) throw new NullPointerException();
            return new FileRedirect(Type.APPEND, file, true);
        }

        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (!(obj instanceof Redirect)) return false;
            Redirect r = (Redirect) obj;
            if (r.type() != this.type()) return false;
            return this.file() != null && this.file().equals(r.file());
        }

        public int hashCode() {
            File file = file();
            return file == null ? super.hashCode() : file.hashCode();
        }

        private Redirect() {}

        private static final class FileRedirect extends Redirect {
            private final Type type;
            private final File file;
            private final boolean append;

            FileRedirect(Type type, File file, boolean append) {
                this.type = type;
                this.file = file;
                this.append = append;
            }

            public Type type() { return type; }
            public File file() { return file; }
            boolean append() { return append; }

            public String toString() {
                switch (type) {
                    case READ: return "redirect to read from file \"" + file + "\"";
                    case WRITE: return "redirect to write to file \"" + file + "\"";
                    default: return "redirect to append to file \"" + file + "\"";
                }
            }
        }
    }

    private Redirect[] redirects() {
        if (redirects == null) redirects = new Redirect[] { Redirect.PIPE, Redirect.PIPE, Redirect.PIPE };
        return redirects;
    }

    public ProcessBuilder redirectInput(Redirect source) {
        if (source.type() == Redirect.Type.WRITE || source.type() == Redirect.Type.APPEND)
            throw new IllegalArgumentException("Redirect invalid for reading: " + source);
        redirects()[0] = source;
        return this;
    }

    public ProcessBuilder redirectOutput(Redirect destination) {
        if (destination.type() == Redirect.Type.READ)
            throw new IllegalArgumentException("Redirect invalid for writing: " + destination);
        redirects()[1] = destination;
        return this;
    }

    public ProcessBuilder redirectError(Redirect destination) {
        if (destination.type() == Redirect.Type.READ)
            throw new IllegalArgumentException("Redirect invalid for writing: " + destination);
        redirects()[2] = destination;
        return this;
    }

    public ProcessBuilder redirectInput(File file) { return redirectInput(Redirect.from(file)); }
    public ProcessBuilder redirectOutput(File file) { return redirectOutput(Redirect.to(file)); }
    public ProcessBuilder redirectError(File file) { return redirectError(Redirect.to(file)); }

    public Redirect redirectInput() { return redirects == null ? Redirect.PIPE : redirects[0]; }
    public Redirect redirectOutput() { return redirects == null ? Redirect.PIPE : redirects[1]; }
    public Redirect redirectError() { return redirects == null ? Redirect.PIPE : redirects[2]; }

    public ProcessBuilder inheritIO() {
        Arrays.fill(redirects(), Redirect.INHERIT);
        return this;
    }

    public boolean redirectErrorStream() { return redirectErrorStream; }

    public ProcessBuilder redirectErrorStream(boolean redirectErrorStream) {
        this.redirectErrorStream = redirectErrorStream;
        return this;
    }

    public Process start() throws IOException {
        String[] cmdarray = command.toArray(new String[command.size()]);
        if (cmdarray.length == 0) throw new IndexOutOfBoundsException();
        for (String arg : cmdarray) if (arg == null) throw new NullPointerException();
        String dir = directory == null ? null : directory.toString();
        throw new IOException("Cannot run program \"" + cmdarray[0] + "\""
                + (dir == null ? "" : " (in directory \"" + dir + "\")")
                + ": error=38, Function not implemented");
    }
}
