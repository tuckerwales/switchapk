// java.util.logging: levels, hierarchy, effective levels, filters, handlers, formatting, configuration.
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class LoggingTest {
    static final List<String> seen = new ArrayList<>();

    static class Capture extends Handler {
        final String tag;
        Capture(String tag) { this.tag = tag; }
        public void publish(LogRecord r) {
            if (!isLoggable(r)) return;
            Formatter f = new Formatter() { public String format(LogRecord x) { return ""; } };
            seen.add(tag + " " + r.getLevel() + " " + r.getLoggerName() + " " + f.formatMessage(r)
                    + (r.getThrown() != null ? " thrown=" + r.getThrown().getMessage() : "")
                    + (r.getSourceClassName() != null ? " src=" + r.getSourceClassName() + "." + r.getSourceMethodName() : ""));
        }
        public void flush() {}
        public void close() {}
    }

    static void dump(String what) {
        System.out.println(what + ": " + seen);
        seen.clear();
    }

    public static void main(String[] args) throws Exception {
        System.out.println(Level.SEVERE.intValue() + " " + Level.INFO + " " + Level.ALL.intValue() + " " + Level.OFF.intValue());
        System.out.println(Level.parse("FINE") == Level.FINE);
        System.out.println(Level.parse("800") == Level.INFO);
        System.out.println(Level.parse("750").getName() + " " + Level.parse("750").intValue());
        try {
            Level.parse("LOUD");
        } catch (IllegalArgumentException e) {
            System.out.println("IAE " + e.getMessage());
        }

        Logger root = Logger.getLogger("");
        System.out.println("root level " + root.getLevel() + " handlers " + root.getHandlers().length + " "
                + root.getHandlers()[0].getClass().getName() + " " + root.getHandlers()[0].getLevel());
        for (Handler h : root.getHandlers()) root.removeHandler(h);
        root.addHandler(new Capture("root"));

        Logger child = Logger.getLogger("a.b.c");
        System.out.println("c parent " + child.getParent().getName().isEmpty() + " level " + child.getLevel());
        Logger mid = Logger.getLogger("a.b");
        System.out.println("c parent now " + child.getParent().getName() + ", a.b parent '" + mid.getParent().getName() + "'");
        System.out.println(Logger.getLogger("a.b.c") == child);
        System.out.println("global " + Logger.getGlobal().getName() + " " + (Logger.getGlobal().getParent() == root));

        child.info("hello");
        child.fine("hidden");
        mid.setLevel(Level.FINE);
        child.fine("now visible");
        child.finer("still hidden");
        System.out.println("loggable " + child.isLoggable(Level.FINE) + " " + child.isLoggable(Level.FINEST));
        dump("levels");

        Capture midH = new Capture("mid");
        mid.addHandler(midH);
        child.log(Level.WARNING, "x={0} y={1}", new Object[] { 1, "two" });
        child.log(Level.SEVERE, "boom", new RuntimeException("bad"));
        child.log(Level.INFO, "one {0}", "param");
        child.log(Level.INFO, "{not a pattern}", "param");
        child.logp(Level.INFO, "Cls", "meth", "sourced");
        dump("handlers");

        mid.setUseParentHandlers(false);
        child.warning("only mid");
        dump("no parent handlers");
        mid.setUseParentHandlers(true);

        child.setFilter(new Filter() {
            public boolean isLoggable(LogRecord r) { return !r.getMessage().contains("drop"); }
        });
        child.info("keep");
        child.info("drop me");
        child.setFilter(null);
        midH.setLevel(Level.WARNING);
        child.info("root only");
        dump("filters");

        child.entering("Cls", "m", new Object[] { "p", 2 });
        mid.setLevel(Level.FINEST);
        child.entering("Cls", "m", new Object[] { "p", 2 });
        child.exiting("Cls", "m", 7);
        child.throwing("Cls", "m", new IllegalStateException("t"));
        midH.setLevel(Level.ALL);
        child.severe(() -> "supplied");
        dump("entering");

        Logger anon = Logger.getAnonymousLogger();
        System.out.println("anon " + anon.getName() + " " + (anon.getParent() == root));
        anon.info("anonymous");
        dump("anon");

        mid.setLevel(Level.OFF);
        child.severe("off");
        dump("off");

        LogManager lm = LogManager.getLogManager();
        final int[] calls = { 0 };
        lm.addConfigurationListener(() -> calls[0]++);
        lm.readConfiguration(new ByteArrayInputStream(".level=WARNING\na.b.level=FINE\nfoo=bar\n".getBytes("UTF-8")));
        System.out.println("after config: root " + root.getLevel() + " mid " + mid.getLevel() + " child "
                + child.getLevel() + " handlers " + root.getHandlers().length + " " + mid.getHandlers().length
                + " foo=" + lm.getProperty("foo") + " listener " + calls[0]);
        System.out.println(lm.getLogger("a.b") == mid);
        System.out.println(lm.getLogger("nope"));
        Logger late = Logger.getLogger("late");
        System.out.println("late " + late.getLevel() + " " + late.isLoggable(Level.INFO) + " " + late.isLoggable(Level.WARNING));

        LogRecord r = new LogRecord(Level.CONFIG, "m");
        LogRecord r2 = new LogRecord(Level.CONFIG, "m");
        System.out.println("seq " + (r2.getSequenceNumber() - r.getSequenceNumber()) + " thread "
                + (r.getLongThreadID() == Thread.currentThread().getId()));
        try {
            new LogRecord(null, "x");
        } catch (NullPointerException e) {
            System.out.println("NPE");
        }
    }
}
