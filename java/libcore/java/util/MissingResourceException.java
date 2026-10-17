package java.util;

public class MissingResourceException extends RuntimeException {
    private static final long serialVersionUID = -4876345176062000401L;

    private final String className;
    private final String key;

    public MissingResourceException(String s, String className, String key) {
        super(s);
        this.className = className;
        this.key = key;
    }

    public String getClassName() {
        return className;
    }

    public String getKey() {
        return key;
    }
}
