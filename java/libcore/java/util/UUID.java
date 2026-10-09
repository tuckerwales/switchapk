package java.util;

public final class UUID implements java.io.Serializable, Comparable<UUID> {
    private final long mostSigBits;
    private final long leastSigBits;
    private static Random numberGenerator;

    public UUID(long mostSigBits, long leastSigBits) {
        this.mostSigBits = mostSigBits;
        this.leastSigBits = leastSigBits;
    }

    public static synchronized UUID randomUUID() {
        if (numberGenerator == null) {
            numberGenerator = new Random();
        }
        byte[] randomBytes = new byte[16];
        numberGenerator.nextBytes(randomBytes);
        randomBytes[6] &= 0x0f;
        randomBytes[6] |= 0x40;
        randomBytes[8] &= 0x3f;
        randomBytes[8] |= (byte) 0x80;
        return fromBytes(randomBytes);
    }

    private static UUID fromBytes(byte[] data) {
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (data[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (data[i] & 0xff);
        }
        return new UUID(msb, lsb);
    }

    public static UUID nameUUIDFromBytes(byte[] name) {
        byte[] b = new byte[16];
        for (int i = 0; i < name.length; i++) {
            b[i % 16] = (byte) (b[i % 16] * 31 + name[i]);
        }
        b[6] &= 0x0f;
        b[6] |= 0x30;
        b[8] &= 0x3f;
        b[8] |= (byte) 0x80;
        return fromBytes(b);
    }

    public static UUID fromString(String name) {
        String[] components = name.split("-");
        if (components.length != 5) {
            throw new IllegalArgumentException("Invalid UUID string: " + name);
        }
        long mostSigBits = Long.parseLong(components[0], 16);
        mostSigBits <<= 16;
        mostSigBits |= Long.parseLong(components[1], 16);
        mostSigBits <<= 16;
        mostSigBits |= Long.parseLong(components[2], 16);
        long leastSigBits = Long.parseLong(components[3], 16);
        leastSigBits <<= 48;
        leastSigBits |= Long.parseUnsignedLong(components[4], 16);
        return new UUID(mostSigBits, leastSigBits);
    }

    public long getLeastSignificantBits() {
        return leastSigBits;
    }

    public long getMostSignificantBits() {
        return mostSigBits;
    }

    public int version() {
        return (int) ((mostSigBits >> 12) & 0x0f);
    }

    public int variant() {
        return (int) ((leastSigBits >>> (64 - (leastSigBits >>> 62))) & (leastSigBits >> 63));
    }

    public String toString() {
        return digits(mostSigBits >> 32, 8) + "-" + digits(mostSigBits >> 16, 4) + "-" + digits(mostSigBits, 4) + "-"
                + digits(leastSigBits >> 48, 4) + "-" + digits(leastSigBits, 12);
    }

    private static String digits(long val, int digits) {
        long hi = 1L << (digits * 4);
        return Long.toHexString(hi | (val & (hi - 1))).substring(1);
    }

    public int hashCode() {
        long hilo = mostSigBits ^ leastSigBits;
        return ((int) (hilo >> 32)) ^ (int) hilo;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof UUID)) {
            return false;
        }
        UUID id = (UUID) obj;
        return mostSigBits == id.mostSigBits && leastSigBits == id.leastSigBits;
    }

    public int compareTo(UUID val) {
        return (this.mostSigBits < val.mostSigBits ? -1 : (this.mostSigBits > val.mostSigBits ? 1
                : (this.leastSigBits < val.leastSigBits ? -1 : (this.leastSigBits > val.leastSigBits ? 1 : 0))));
    }

    public long timestamp() {
        if (version() != 1) {
            throw new UnsupportedOperationException("Not a time-based UUID");
        }
        return (mostSigBits & 0x0FFFL) << 48 | ((mostSigBits >> 16) & 0x0FFFFL) << 32 | mostSigBits >>> 32;
    }

    public int clockSequence() {
        if (version() != 1) {
            throw new UnsupportedOperationException("Not a time-based UUID");
        }
        return (int) ((leastSigBits & 0x3FFF000000000000L) >>> 48);
    }

    public long node() {
        if (version() != 1) {
            throw new UnsupportedOperationException("Not a time-based UUID");
        }
        return leastSigBits & 0x0000FFFFFFFFFFFFL;
    }
}
