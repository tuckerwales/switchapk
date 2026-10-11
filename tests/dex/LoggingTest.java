import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;

/**
 * java.util.logging: run on OpenJDK and on switchapk by tests/run_dex_test.sh, output must match. A capturing
 * handler stands in for the root handler (the JDK prints to stderr, Android to logcat).
 */
public class LoggingTest {
    static final List<String> seen = new ArrayList<>();

    static class Capture extends Handler {
        public void publish(LogRecord r) {
            if (!isLoggable(r)) {
                return;
            }
            String msg = getFormatter() != null ? getFormatter().formatMessage(r) : r.getMessage();
            seen.add(r.getLoggerName() + " " + r.getLevel() + " " + msg
                    + (r.getThrown() != null ? " thrown=" + r.getThrown().getMessage() : "")
                    + (r.getSourceMethodName() != null && r.getSourceClassName() != null
                            && r.getSourceClassName().equals("Src") ? " src=" + r.getSourceMethodName() : ""));
        }

        public void flush() {
        }

        public void close() {
        }
    }

    static void dump(String label) {
        System.out.println(label + ": " + seen);
        seen.clear();
    }

    public static void main(String[] args) {
        Logger root = Logger.getLogger("");
        System.out.println("root level " + root.getLevel() + " parent " + root.getParent());
        Logger a = Logger.getLogger("com.example");
        Logger ab = Logger.getLogger("com.example.app.Main");
        System.out.println("same " + (a == Logger.getLogger("com.example")) + " parent " + ab.getParent().getName()
                + " root parent " + (a.getParent() == root));
        Logger mid = Logger.getLogger("com.example.app");
        System.out.println("reparented " + ab.getParent().getName() + " " + mid.getParent().getName());

        Capture cap = new Capture();
        cap.setFormatter(new SimpleFormatter());
        a.addHandler(cap);
        a.setUseParentHandlers(false);

        ab.info("hello");
        ab.fine("hidden");
        ab.warning("careful");
        dump("default INFO");

        a.setLevel(Level.FINE);
        ab.fine("now shown");
        ab.finer("still hidden");
        ab.log(Level.INFO, "params {0} and {1}", new Object[] {"x", 42});
        ab.log(Level.SEVERE, "boom", new RuntimeException("bad"));
        ab.log(Level.INFO, () -> "supplied");
        ab.logp(Level.INFO, "Src", "method", "with source");
        ab.log(Level.INFO, "braces {not a param}", "p");
        dump("FINE");

        ab.setLevel(Level.OFF);
        ab.severe("off");
        ab.setLevel(null);
        mid.setLevel(Level.WARNING);
        ab.info("blocked by mid");
        ab.severe("passes mid");
        dump("levels");

        cap.setLevel(Level.SEVERE);
        mid.setLevel(Level.ALL);
        ab.warning("handler filters");
        ab.severe("handler passes");
        cap.setLevel(Level.ALL);
        a.setFilter(r -> !r.getMessage().contains("drop"));
        ab.info("drop me");
        a.info("keep me");
        mid.setFilter(r -> false);
        ab.info("filter only on mid");
        dump("filters");

        ab.entering("Src", "go");
        ab.entering("Src", "go", new Object[] {1, "two"});
        ab.exiting("Src", "go", "res");
        ab.throwing("Src", "go", new IllegalStateException("t"));
        dump("tracing");

        System.out.println("parse " + Level.parse("WARNING") + " " + Level.parse("800") + " " + Level.parse("850").intValue()
                + " " + Level.INFO.equals(Level.parse("INFO")) + " " + Level.SEVERE.intValue());
        try {
            Level.parse("NOPE");
        } catch (IllegalArgumentException e) {
            System.out.println("bad level " + e.getClass().getSimpleName());
        }
        Logger anon = Logger.getAnonymousLogger();
        System.out.println("anon " + anon.getName() + " parent root " + (anon.getParent() == root));
        System.out.println("global " + Logger.getGlobal().getName() + " " + (Logger.getGlobal() == Logger.getLogger("global")));
        System.out.println("handlers " + a.getHandlers().length + " " + ab.getHandlers().length);
        a.removeHandler(cap);
        System.out.println("removed " + a.getHandlers().length);
        LogRecord r = new LogRecord(Level.CONFIG, "m");
        System.out.println("record " + r.getLevel() + " " + r.getMessage() + " " + (r.getMillis() > 0) + " "
                + (r.getSequenceNumber() >= 0));
        System.out.println("names has com.example " + java.util.Collections.list(LogManager.getLogManager().getLoggerNames())
                .contains("com.example"));
    }
}
