package java.util.concurrent;

import java.util.Random;

/**
 * A per-thread random generator: no locking and no sharing between threads. current() returns the calling
 * thread's instance. The sequence is SplittableRandom's (a 64-bit Weyl sequence through a mix function), as in
 * the JDK; seeds are not settable.
 */
public class ThreadLocalRandom extends Random {
    private static final long serialVersionUID = -5851777807851030925L;
    private static final long GAMMA = 0x9e3779b97f4a7c15L;
    private static final double DOUBLE_UNIT = 0x1.0p-53;
    private static final float FLOAT_UNIT = 0x1.0p-24f;

    private static final ThreadLocal<ThreadLocalRandom> INSTANCE = new ThreadLocal<ThreadLocalRandom>() {
        protected ThreadLocalRandom initialValue() {
            return new ThreadLocalRandom(mix64(System.nanoTime()) ^ mix64(Thread.currentThread().getId() * GAMMA));
        }
    };

    private long seed;
    private final boolean initialized;

    private ThreadLocalRandom(long seed) {
        super(0);
        this.seed = seed;
        initialized = true;
    }

    public static ThreadLocalRandom current() {
        return INSTANCE.get();
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static int mix32(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        return (int) (((z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L) >>> 32);
    }

    private long nextSeed() {
        return seed += GAMMA;
    }

    /** Throws UnsupportedOperationException: the seed of a thread's generator cannot be set. */
    public void setSeed(long seed) {
        if (initialized) {
            throw new UnsupportedOperationException();
        }
    }

    protected int next(int bits) {
        return nextInt() >>> (32 - bits);
    }

    public boolean nextBoolean() {
        return nextInt() < 0;
    }

    public int nextInt() {
        return mix32(nextSeed());
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int r = nextInt();
        int m = bound - 1;
        if ((bound & m) == 0) {
            return r & m;
        }
        for (int u = r >>> 1; u + m - (r = u % bound) < 0; u = nextInt() >>> 1) {
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
        return mix64(nextSeed());
    }

    public long nextLong(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        long r = nextLong();
        long m = bound - 1;
        if ((bound & m) == 0L) {
            return r & m;
        }
        for (long u = r >>> 1; u + m - (r = u % bound) < 0L; u = nextLong() >>> 1) {
        }
        return r;
    }

    public long nextLong(long origin, long bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException("bound must be greater than origin");
        }
        long n = bound - origin;
        if (n > 0) {
            return nextLong(n) + origin;
        }
        long r;
        do {
            r = nextLong();
        } while (r < origin || r >= bound);
        return r;
    }

    public float nextFloat() {
        return (nextInt() >>> 8) * FLOAT_UNIT;
    }

    public float nextFloat(float bound) {
        if (!(bound > 0.0f && bound < Float.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("bound must be finite and positive");
        }
        float r = nextFloat() * bound;
        return r < bound ? r : Math.nextDown(bound);
    }

    public float nextFloat(float origin, float bound) {
        if (!(origin < bound && bound - origin < Float.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("bound must be greater than origin");
        }
        float r = nextFloat() * (bound - origin) + origin;
        return r < bound ? r : Math.nextDown(bound);
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * DOUBLE_UNIT;
    }

    public double nextDouble(double bound) {
        if (!(bound > 0.0 && bound < Double.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("bound must be finite and positive");
        }
        double r = nextDouble() * bound;
        return r < bound ? r : Math.nextDown(bound);
    }

    public double nextDouble(double origin, double bound) {
        if (!(origin < bound && bound - origin < Double.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("bound must be greater than origin");
        }
        double r = nextDouble() * (bound - origin) + origin;
        return r < bound ? r : Math.nextDown(bound);
    }
}
