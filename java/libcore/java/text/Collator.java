package java.text;

import java.util.Comparator;
import java.util.Locale;

public abstract class Collator implements Comparator<Object>, Cloneable {
    public static final int PRIMARY = 0;
    public static final int SECONDARY = 1;
    public static final int TERTIARY = 2;
    public static final int IDENTICAL = 3;
    public static final int NO_DECOMPOSITION = 0;
    public static final int CANONICAL_DECOMPOSITION = 1;
    public static final int FULL_DECOMPOSITION = 2;

    private int strength = TERTIARY;

    protected Collator() {
    }

    public static synchronized Collator getInstance() {
        return new Collator() {
            public int compare(String a, String b) {
                int c = a.compareToIgnoreCase(b);
                if (c != 0 || getStrength() < TERTIARY) {
                    return c;
                }
                return a.compareTo(b);
            }
        };
    }

    public static Collator getInstance(Locale desiredLocale) {
        return getInstance();
    }

    public abstract int compare(String source, String target);

    public int compare(Object o1, Object o2) {
        return compare((String) o1, (String) o2);
    }

    public boolean equals(String source, String target) {
        return compare(source, target) == 0;
    }

    public synchronized int getStrength() {
        return strength;
    }

    public synchronized void setStrength(int newStrength) {
        strength = newStrength;
    }

    public synchronized int getDecomposition() {
        return NO_DECOMPOSITION;
    }

    public synchronized void setDecomposition(int decompositionMode) {
    }

    public CollationKey getCollationKey(String source) {
        return new CollationKey(source);
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
}
