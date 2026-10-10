package javax.crypto.spec;

import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.SecretKey;

public class SecretKeySpec implements KeySpec, SecretKey {
    private static final long serialVersionUID = 6577238317307289933L;

    private final byte[] key;
    private final String algorithm;

    public SecretKeySpec(byte[] key, String algorithm) {
        if (key == null || algorithm == null) throw new IllegalArgumentException("Missing argument");
        if (key.length == 0) throw new IllegalArgumentException("Empty key");
        this.key = key.clone();
        this.algorithm = algorithm;
    }

    public SecretKeySpec(byte[] key, int offset, int len, String algorithm) {
        if (key == null || algorithm == null) throw new IllegalArgumentException("Missing argument");
        if (key.length == 0) throw new IllegalArgumentException("Empty key");
        if (key.length - offset < len) throw new IllegalArgumentException("Invalid offset/length combination");
        if (len < 0) throw new ArrayIndexOutOfBoundsException("len is negative");
        this.key = Arrays.copyOfRange(key, offset, offset + len);
        this.algorithm = algorithm;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getFormat() {
        return "RAW";
    }

    public byte[] getEncoded() {
        return key.clone();
    }

    public int hashCode() {
        int retval = 0;
        for (int i = 1; i < key.length; i++) retval += key[i] * i;
        if (algorithm.equalsIgnoreCase("TripleDES")) return retval ^ "desede".hashCode();
        return retval ^ algorithm.toLowerCase(Locale.ENGLISH).hashCode();
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SecretKey)) return false;
        String thatAlg = ((SecretKey) obj).getAlgorithm();
        if (!(thatAlg.equalsIgnoreCase(algorithm))) {
            if ((!(thatAlg.equalsIgnoreCase("DESede")) || !(algorithm.equalsIgnoreCase("TripleDES")))
                    && (!(thatAlg.equalsIgnoreCase("TripleDES")) || !(algorithm.equalsIgnoreCase("DESede")))) {
                return false;
            }
        }
        return MessageDigest.isEqual(key, ((SecretKey) obj).getEncoded());
    }
}
