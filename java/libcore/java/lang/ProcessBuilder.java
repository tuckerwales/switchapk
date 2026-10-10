package java.lang;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds processes the way the JDK does, but the console cannot start programs: start() fails with the
 * IOException a failed exec gives on Android (here error=38, ENOSYS). Apps that run a helper binary
 * (chess engines, ffmpeg) get the same error path as on a device that forbids it.
 */
public final class ProcessBuilder {
    private List<String> command;
    private File directory;
    private Map<String, String> environment;
    private boolean redirectErrorStream;
    private Redirect[] redirects;

    public ProcessBuilder(List<String> command) {
        if (command == null) {
            throw new NullPointerException();
        }
        this.command = command;
    }

    public ProcessBuilder(String... command) {
        this.command = new ArrayList<String>(command.length);
        for (String arg : command) {
            this.command.add(arg);
        }
    }

    public ProcessBuilder command(List<String> command) {
        if (command == null) {
            throw new NullPointerException();
        }
        this.command = command;
        return this;
    }

    public ProcessBuilder command(String... command) {
        this.command = new ArrayList<String>(command.length);
        for (String arg : command) {
            this.command.add(arg);
        }
        return this;
    }

    public List<String> command() {
        return command;
    }

    public Map<String, String> environment() {
        if (environment == null) {
            environment = new HashMap<String, String>(System.getenv());
        }
        return environment;
    }

    public File directory() {
        return directory;
    }

    public ProcessBuilder directory(File directory) {
        this.directory = directory;
        return this;
    }

    private Redirect[] redirects() {
        if (redirects == null) {
            redirects = new Redirect[] {Redirect.PIPE, Redirect.PIPE, Redirect.PIPE};
        }
        return redirects;
    }

    public ProcessBuilder redirectInput(Redirect source) {
        if (source.type() == Redirect.Type.WRITE || source.type() == Redirect.Type.APPEND) {
            throw new IllegalArgumentException("Redirect invalid for reading: " + source);
        }
        redirects()[0] = source;
        return this;
    }

    public ProcessBuilder redirectOutput(Redirect destination) {
        if (destination.type() == Redirect.Type.READ) {
            throw new IllegalArgumentException("Redirect invalid for writing: " + destination);
        }
        redirects()[1] = destination;
        return this;
    }

    public ProcessBuilder redirectError(Redirect destination) {
        if (destination.type() == Redirect.Type.READ) {
            throw new IllegalArgumentException("Redirect invalid for writing: " + destination);
        }
        redirects()[2] = destination;
        return this;
    }

    public ProcessBuilder redirectInput(File file) {
        return redirectInput(Redirect.from(file));
    }

    public ProcessBuilder redirectOutput(File file) {
        return redirectOutput(Redirect.to(file));
    }

    public ProcessBuilder redirectError(File file) {
        return redirectError(Redirect.to(file));
    }

    public Redirect redirectInput() {
        return redirects == null ? Redirect.PIPE : redirects[0];
    }

    public Redirect redirectOutput() {
        return redirects == null ? Redirect.PIPE : redirects[1];
    }

    public Redirect redirectError() {
        return redirects == null ? Redirect.PIPE : redirects[2];
    }

    public ProcessBuilder inheritIO() {
        Arrays.fill(redirects(), Redirect.INHERIT);
        return this;
    }

    public boolean redirectErrorStream() {
        return redirectErrorStream;
    }

    public ProcessBuilder redirectErrorStream(boolean redirectErrorStream) {
        this.redirectErrorStream = redirectErrorStream;
        return this;
    }

    public Process start() throws IOException {
        String[] cmdarray = command.toArray(new String[0]);
        for (String arg : cmdarray) {
            if (arg == null) {
                throw new NullPointerException();
            }
        }
        if (cmdarray.length == 0) {
            throw new IndexOutOfBoundsException();
        }
        throw new IOException("Cannot run program \"" + cmdarray[0] + "\""
                + (directory == null ? "" : " (in directory \"" + directory + "\")")
                + ": error=38, Function not implemented");
    }

    public static List<Process> startPipeline(List<ProcessBuilder> builders) throws IOException {
        List<Process> processes = new ArrayList<Process>(builders.size());
        for (ProcessBuilder builder : builders) {
            processes.add(builder.start());
        }
        return processes;
    }

    public abstract static class Redirect {
        public enum Type {
            PIPE, INHERIT, READ, WRITE, APPEND
        }

        public static final Redirect PIPE = new Redirect() {
            public Type type() {
                return Type.PIPE;
            }

            public String toString() {
                return type().toString();
            }
        };

        public static final Redirect INHERIT = new Redirect() {
            public Type type() {
                return Type.INHERIT;
            }

            public String toString() {
                return type().toString();
            }
        };

        public static final Redirect DISCARD = to(new File("/dev/null"));

        public abstract Type type();

        public File file() {
            return null;
        }

        boolean append() {
            throw new UnsupportedOperationException();
        }

        public static Redirect from(final File file) {
            if (file == null) {
                throw new NullPointerException();
            }
            return new FileRedirect(Type.READ, file, "redirect to read from file \"");
        }

        public static Redirect to(final File file) {
            if (file == null) {
                throw new NullPointerException();
            }
            return new FileRedirect(Type.WRITE, file, "redirect to write to file \"");
        }

        public static Redirect appendTo(final File file) {
            if (file == null) {
                throw new NullPointerException();
            }
            return new FileRedirect(Type.APPEND, file, "redirect to append to file \"");
        }

        private static final class FileRedirect extends Redirect {
            private final Type type;
            private final File file;
            private final String label;

            FileRedirect(Type type, File file, String label) {
                this.type = type;
                this.file = file;
                this.label = label;
            }

            public Type type() {
                return type;
            }

            public File file() {
                return file;
            }

            boolean append() {
                return type == Type.APPEND;
            }

            public String toString() {
                return label + file + "\"";
            }
        }

        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Redirect)) {
                return false;
            }
            Redirect r = (Redirect) obj;
            if (r.type() != type()) {
                return false;
            }
            return file() != null && file().equals(r.file());
        }

        public int hashCode() {
            File file = file();
            return file == null ? super.hashCode() : file.hashCode();
        }

        private Redirect() {
        }
    }
}
