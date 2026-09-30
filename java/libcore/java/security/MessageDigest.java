package java.security;

import java.nio.ByteBuffer;

public abstract class MessageDigest {
    private final String algorithm;

    protected MessageDigest(String algorithm) {
        this.algorithm = algorithm;
    }

    public static MessageDigest getInstance(String algorithm) throws NoSuchAlgorithmException {
        String a = algorithm.toUpperCase();
        if (a.equals("MD5")) {
            return new Impl("MD5", 0);
        }
        if (a.equals("SHA-1") || a.equals("SHA1") || a.equals("SHA")) {
            return new Impl("SHA-1", 1);
        }
        if (a.equals("SHA-256") || a.equals("SHA256")) {
            return new Impl("SHA-256", 2);
        }
        throw new NoSuchAlgorithmException(algorithm + " MessageDigest not available");
    }

    public static MessageDigest getInstance(String algorithm, String provider) throws NoSuchAlgorithmException {
        return getInstance(algorithm);
    }

    public final String getAlgorithm() {
        return algorithm;
    }

    public abstract void update(byte input);

    public abstract void update(byte[] input, int offset, int len);

    public void update(byte[] input) {
        update(input, 0, input.length);
    }

    public final void update(ByteBuffer input) {
        byte[] b = new byte[input.remaining()];
        input.get(b);
        update(b);
    }

    public abstract byte[] digest();

    public int digest(byte[] buf, int offset, int len) throws DigestException {
        byte[] d = digest();
        System.arraycopy(d, 0, buf, offset, Math.min(len, d.length));
        return d.length;
    }

    public byte[] digest(byte[] input) {
        update(input);
        return digest();
    }

    public abstract void reset();

    public abstract int getDigestLength();

    public static boolean isEqual(byte[] a, byte[] b) {
        return java.util.Arrays.equals(a, b);
    }

    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    static final class Impl extends MessageDigest {
        private final int kind;
        private java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();

        Impl(String name, int kind) {
            super(name);
            this.kind = kind;
        }

        public void update(byte input) {
            buf.write(input);
        }

        public void update(byte[] input, int offset, int len) {
            buf.write(input, offset, len);
        }

        public void reset() {
            buf.reset();
        }

        public int getDigestLength() {
            return kind == 0 ? 16 : kind == 1 ? 20 : 32;
        }

        public byte[] digest() {
            byte[] data = buf.toByteArray();
            buf.reset();
            switch (kind) {
                case 0: return Digests.md5(data);
                case 1: return Digests.sha1(data);
                default: return Digests.sha256(data);
            }
        }
    }
}
