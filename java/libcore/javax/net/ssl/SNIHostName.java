package javax.net.ssl;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SNIHostName extends SNIServerName {
    private final String hostname;

    public SNIHostName(String hostname) {
        super(StandardConstants.SNI_HOST_NAME, checked(hostname).getBytes(StandardCharsets.US_ASCII));
        this.hostname = hostname;
    }

    public SNIHostName(byte[] encoded) {
        super(StandardConstants.SNI_HOST_NAME, encoded);
        this.hostname = checked(new String(encoded, StandardCharsets.US_ASCII));
    }

    private static String checked(String h) {
        if (h == null) throw new NullPointerException("Server name value of host_name cannot be null");
        if (h.isEmpty() || h.endsWith(".")) throw new IllegalArgumentException("Server name value of host_name cannot "
                + (h.isEmpty() ? "be empty" : "have the trailing dot"));
        for (int i = 0; i < h.length(); i++) {
            if (h.charAt(i) > 0x7f) throw new IllegalArgumentException("Illegal server name value of host_name: " + h);
        }
        return h;
    }

    public String getAsciiName() {
        return hostname;
    }

    public boolean equals(Object other) {
        return other instanceof SNIHostName && hostname.equalsIgnoreCase(((SNIHostName) other).hostname);
    }

    public int hashCode() {
        return 17 * 31 + hostname.toUpperCase(Locale.ENGLISH).hashCode();
    }

    public String toString() {
        return "type=host_name (0), value=" + hostname;
    }

    public static SNIMatcher createSNIMatcher(String regex) {
        if (regex == null) throw new NullPointerException("The regular expression cannot be null");
        final Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        return new SNIMatcher(StandardConstants.SNI_HOST_NAME) {
            public boolean matches(SNIServerName name) {
                if (name == null) throw new NullPointerException("The SNIServerName argument cannot be null");
                if (name.getType() != StandardConstants.SNI_HOST_NAME) return false;
                String h = name instanceof SNIHostName ? ((SNIHostName) name).getAsciiName()
                        : new String(name.getEncoded(), StandardCharsets.US_ASCII);
                return p.matcher(h).matches();
            }
        };
    }
}
