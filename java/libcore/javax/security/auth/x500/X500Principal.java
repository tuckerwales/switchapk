package javax.security.auth.x500;

import java.security.Principal;

/**
 * An X.500 distinguished name. Kept as the string it was given in; parsing and
 * DER encoding arrive with certificates (WS11 TLS).
 */
public final class X500Principal implements Principal, java.io.Serializable {
    public static final String RFC1779 = "RFC1779";
    public static final String RFC2253 = "RFC2253";
    public static final String CANONICAL = "CANONICAL";

    private final String name;

    public X500Principal(String name) {
        if (name == null) throw new NullPointerException("provided null name");
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getName(String format) {
        if (CANONICAL.equals(format)) return name.trim().toLowerCase(java.util.Locale.ENGLISH);
        return name;
    }

    public String toString() {
        return name;
    }

    public boolean equals(Object o) {
        return o instanceof X500Principal && getName(CANONICAL).equals(((X500Principal) o).getName(CANONICAL));
    }

    public int hashCode() {
        return getName(CANONICAL).hashCode();
    }
}
