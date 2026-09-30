package java.util;

public class Random implements java.io.Serializable {
    private long seed;
    private double nextNextGaussian;
    private boolean haveNextNextGaussian = false;
    private static final long multiplier = 0x5DEECE66DL;
    private static final long addend = 0xBL;
    private static final long mask = (1L << 48) - 1;
    private static long seedUniquifier = 8682522807148012L;

    public Random() {
        this(seedUniquifier() ^ System.nanoTime());
    }

    private static synchronized long seedUniquifier() {
        seedUniquifier *= 1181783497276652981L;
        return seedUniquifier;
    }

    public Random(long seed) {
        this.seed = initialScramble(seed);
    }

    private static long initialScramble(long seed) {
        return (seed ^ multiplier) & mask;
    }

    public synchronized void setSeed(long seed) {
        this.seed = initialScramble(seed);
        haveNextNextGaussian = false;
    }

    protected synchronized int next(int bits) {
        seed = (seed * multiplier + addend) & mask;
        return (int) (seed >>> (48 - bits));
    }

    public void nextBytes(byte[] bytes) {
        for (int i = 0, len = bytes.length; i < len;) {
            for (int rnd = nextInt(), n = Math.min(len - i, Integer.SIZE / Byte.SIZE); n-- > 0; rnd >>= Byte.SIZE) {
                bytes[i++] = (byte) rnd;
            }
        }
    }

    public int nextInt() {
        return next(32);
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int r = next(31);
        int m = bound - 1;
        if ((bound & m) == 0) {
            r = (int) ((bound * (long) r) >> 31);
        } else {
            for (int u = r; u - (r = u % bound) + m < 0; u = next(31)) {
            }
        }
        return r;
    }

    public int nextInt(int origin, int bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException("bound must be greater than origin");
        }
        int n = bound - origin;
        if (n > 0) {
            return nextInt(n) + origin;
        }
        int r;
        do {
            r = nextInt();
        } while (r < origin || r >= bound);
        return r;
    }

    public long nextLong() {
        return ((long) (next(32)) << 32) + next(32);
    }

    public long nextLong(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        long r = nextLong() >>> 1;
        return r % bound;
    }

    public boolean nextBoolean() {
        return next(1) != 0;
    }

    public float nextFloat() {
        return next(24) / ((float) (1 << 24));
    }

    public double nextDouble() {
        return (((long) (next(26)) << 27) + next(27)) * 0x1.0p-53;
    }

    public synchronized double nextGaussian() {
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        }
        double v1, v2, s;
        do {
            v1 = 2 * nextDouble() - 1;
            v2 = 2 * nextDouble() - 1;
            s = v1 * v1 + v2 * v2;
        } while (s >= 1 || s == 0);
        double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s) / s);
        nextNextGaussian = v2 * multiplier;
        haveNextNextGaussian = true;
        return v1 * multiplier;
    }

    public java.util.stream.IntStream ints(long streamSize) {
        int[] a = new int[(int) streamSize];
        for (int i = 0; i < a.length; i++) {
            a[i] = nextInt();
        }
        return java.util.stream.IntStream.of(a);
    }

    public java.util.stream.IntStream ints(long streamSize, int origin, int bound) {
        int[] a = new int[(int) streamSize];
        for (int i = 0; i < a.length; i++) {
            a[i] = nextInt(origin, bound);
        }
        return java.util.stream.IntStream.of(a);
    }
}
