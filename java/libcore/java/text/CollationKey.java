package java.text;

public class CollationKey implements Comparable<CollationKey> {
    private final String source;

    CollationKey(String source) {
        this.source = source;
    }

    public int compareTo(CollationKey target) {
        return source.compareToIgnoreCase(target.source);
    }

    public String getSourceString() {
        return source;
    }
}
