package java.util.logging;

import java.util.ArrayList;

public class Level implements java.io.Serializable {
    private static final long serialVersionUID = -8176160795706313070L;
    private static final ArrayList<Level> KNOWN = new ArrayList<Level>();

    public static final Level OFF = new Level("OFF", Integer.MAX_VALUE);
    public static final Level SEVERE = new Level("SEVERE", 1000);
    public static final Level WARNING = new Level("WARNING", 900);
    public static final Level INFO = new Level("INFO", 800);
    public static final Level CONFIG = new Level("CONFIG", 700);
    public static final Level FINE = new Level("FINE", 500);
    public static final Level FINER = new Level("FINER", 400);
    public static final Level FINEST = new Level("FINEST", 300);
    public static final Level ALL = new Level("ALL", Integer.MIN_VALUE);

    private final String name;
    private final int value;
    private final String resourceBundleName;

    protected Level(String name, int value) {
        this(name, value, null);
    }

    protected Level(String name, int value, String resourceBundleName) {
        if (name == null) {
            throw new NullPointerException();
        }
        this.name = name;
        this.value = value;
        this.resourceBundleName = resourceBundleName;
        synchronized (KNOWN) {
            KNOWN.add(this);
        }
    }

    public String getResourceBundleName() {
        return resourceBundleName;
    }

    public String getName() {
        return name;
    }

    public String getLocalizedName() {
        return name;
    }

    public final String toString() {
        return name;
    }

    public final int intValue() {
        return value;
    }

    public static synchronized Level parse(String name) throws IllegalArgumentException {
        name.length();
        synchronized (KNOWN) {
            for (Level l : KNOWN) {
                if (l.name.equals(name)) {
                    return l;
                }
            }
        }
        try {
            int x = Integer.parseInt(name);
            synchronized (KNOWN) {
                for (Level l : KNOWN) {
                    if (l.value == x) {
                        return l;
                    }
                }
            }
            return new Level(name, x);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Bad level \"" + name + "\"");
        }
    }

    public boolean equals(Object ox) {
        return ox instanceof Level && ((Level) ox).value == value;
    }

    public int hashCode() {
        return value;
    }
}
