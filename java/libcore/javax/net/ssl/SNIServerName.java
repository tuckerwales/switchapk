package javax.net.ssl;

import java.util.Arrays;

public abstract class SNIServerName {
    private final int type;
    private final byte[] encoded;

    protected SNIServerName(int type, byte[] encoded) {
        if (type < 0 || type > 255) throw new IllegalArgumentException("Server name type is not in range [0, 255]");
        if (encoded == null) throw new NullPointerException("Server name encoded value cannot be null");
        this.type = type;
        this.encoded = encoded.clone();
    }

    public final int getType() {
        return type;
    }

    public final byte[] getEncoded() {
        return encoded.clone();
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        SNIServerName o = (SNIServerName) other;
        return type == o.type && Arrays.equals(encoded, o.encoded);
    }

    public int hashCode() {
        return 17 * 31 + type + 31 * Arrays.hashCode(encoded);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (byte b : encoded) sb.append(String.format("%02X", b & 0xff));
        return (type == StandardConstants.SNI_HOST_NAME ? "type=host_name (0)" : "type=(" + type + ")")
                + ", value=" + sb;
    }
}
