package javax.crypto.spec;

import java.security.spec.KeySpec;
import java.util.Arrays;

public class PBEKeySpec implements KeySpec {
    private char[] password;
    private byte[] salt;
    private int iterationCount;
    private int keyLength;

    public PBEKeySpec(char[] password) {
        this.password = password == null ? new char[0] : password.clone();
    }

    public PBEKeySpec(char[] password, byte[] salt, int iterationCount, int keyLength) {
        this.password = password == null ? new char[0] : password.clone();
        if (salt == null) throw new NullPointerException("the salt parameter must be non-null");
        if (salt.length == 0) throw new IllegalArgumentException("the salt parameter must be non-empty");
        this.salt = salt.clone();
        if (iterationCount <= 0) throw new IllegalArgumentException("invalid iterationCount value");
        if (keyLength <= 0) throw new IllegalArgumentException("invalid keyLength value");
        this.iterationCount = iterationCount;
        this.keyLength = keyLength;
    }

    public PBEKeySpec(char[] password, byte[] salt, int iterationCount) {
        this.password = password == null ? new char[0] : password.clone();
        if (salt == null) throw new NullPointerException("the salt parameter must be non-null");
        if (salt.length == 0) throw new IllegalArgumentException("the salt parameter must be non-empty");
        this.salt = salt.clone();
        if (iterationCount <= 0) throw new IllegalArgumentException("invalid iterationCount value");
        this.iterationCount = iterationCount;
    }

    public final synchronized void clearPassword() {
        if (password != null) {
            Arrays.fill(password, ' ');
            password = null;
        }
    }

    public final synchronized char[] getPassword() {
        if (password == null) throw new IllegalStateException("password has been cleared");
        return password.clone();
    }

    public final byte[] getSalt() {
        return salt == null ? null : salt.clone();
    }

    public final int getIterationCount() {
        return iterationCount;
    }

    public final int getKeyLength() {
        return keyLength;
    }
}
