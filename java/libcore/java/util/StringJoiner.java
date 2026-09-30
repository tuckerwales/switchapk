package java.util;

public final class StringJoiner {
    private final String prefix;
    private final String delimiter;
    private final String suffix;
    private StringBuilder value;
    private String emptyValue;

    public StringJoiner(CharSequence delimiter) {
        this(delimiter, "", "");
    }

    public StringJoiner(CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        this.prefix = prefix.toString();
        this.delimiter = delimiter.toString();
        this.suffix = suffix.toString();
        this.emptyValue = this.prefix + this.suffix;
    }

    public StringJoiner setEmptyValue(CharSequence emptyValue) {
        this.emptyValue = emptyValue.toString();
        return this;
    }

    public String toString() {
        if (value == null) {
            return emptyValue;
        }
        return value.toString() + suffix;
    }

    public StringJoiner add(CharSequence newElement) {
        if (value != null) {
            value.append(delimiter);
        } else {
            value = new StringBuilder().append(prefix);
        }
        value.append(newElement);
        return this;
    }

    public StringJoiner merge(StringJoiner other) {
        if (other.value != null) {
            add(other.value.substring(other.prefix.length()));
        }
        return this;
    }

    public int length() {
        return value != null ? value.length() + suffix.length() : emptyValue.length();
    }
}
