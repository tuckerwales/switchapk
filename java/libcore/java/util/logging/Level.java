package java.util.logging;

import java.util.ArrayList;

public class Level implements java.io.Serializable {
    private static final ArrayList<Level> known = new ArrayList<Level>();

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
        if (name == null) throw new NullPointerException();
        this.name = name;
        this.value = value;
        this.resourceBundleName = resourceBundleName;
        synchronized (known) {
            known.add(this);
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
        synchronized (known) {
            for (Level l : known) {
                if (l.name.equals(name)) return l;
            }
        }
        try {
            int v = Integer.parseInt(name);
            synchronized (known) {
                for (Level l : known) {
                    if (l.value == v) return l;
                }
            }
            return new Level(name, v);
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Bad level \"" + name + "\"");
    }

    public boolean equals(Object o) {
        return o instanceof Level && ((Level) o).value == value;
    }

    public int hashCode() {
        return value;
    }
}
