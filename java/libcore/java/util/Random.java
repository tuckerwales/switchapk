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
        // RandomSupport.boundedNextInt, as Android's OpenJDK 17 based Random (differs from nextInt(n) + origin
        // when the range is a power of two)
        int r = nextInt();
        int n = bound - origin;
        int m = n - 1;
        if ((n & m) == 0) {
            r = (r & m) + origin;
        } else if (n > 0) {
            for (int u = r >>> 1; u + m - (r = u % n) < 0; u = nextInt() >>> 1) {
            }
            r += origin;
        } else {
            while (r < origin || r >= bound) {
                r = nextInt();
            }
        }
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

    /* Bounded values, as RandomSupport.boundedNextLong/Double in the JDK (framework-internal). */
    long internalNextLong(long origin, long bound) {
        long r = nextLong();
        if (origin < bound) {
            long n = bound - origin;
            long m = n - 1;
            if ((n & m) == 0L) {
                r = (r & m) + origin;
            } else if (n > 0L) {
                for (long u = r >>> 1; u + m - (r = u % n) < 0L; u = nextLong() >>> 1) {
                }
                r += origin;
            } else {
                while (r < origin || r >= bound) {
                    r = nextLong();
                }
            }
        }
        return r;
    }

    double internalNextDouble(double origin, double bound) {
        double r = nextDouble();
        if (origin < bound) {
            r = r * (bound - origin) + origin;
            if (r >= bound) {
                r = Double.longBitsToDouble(Double.doubleToLongBits(bound) - 1);
            }
        }
        return r;
    }

    private static void checkSize(long streamSize) {
        if (streamSize < 0L) {
            throw new IllegalArgumentException("size must be non-negative");
        }
    }

    private static void checkRange(double origin, double bound) {
        if (!(origin < bound)) {
            throw new IllegalArgumentException("bound must be greater than origin");
        }
    }

    public java.util.stream.IntStream ints(long streamSize) {
        checkSize(streamSize);
        return ints().limit(streamSize);
    }

    public java.util.stream.IntStream ints() {
        return java.util.stream.IntStream.generate(new java.util.function.IntSupplier() {
            public int getAsInt() {
                return nextInt();
            }
        });
    }

    public java.util.stream.IntStream ints(long streamSize, int origin, int bound) {
        checkSize(streamSize);
        return ints(origin, bound).limit(streamSize);
    }

    public java.util.stream.IntStream ints(final int origin, final int bound) {
        checkRange(origin, bound);
        return java.util.stream.IntStream.generate(new java.util.function.IntSupplier() {
            public int getAsInt() {
                return nextInt(origin, bound);
            }
        });
    }

    public java.util.stream.LongStream longs(long streamSize) {
        checkSize(streamSize);
        return longs().limit(streamSize);
    }

    public java.util.stream.LongStream longs() {
        return java.util.stream.LongStream.generate(new java.util.function.LongSupplier() {
            public long getAsLong() {
                return nextLong();
            }
        });
    }

    public java.util.stream.LongStream longs(long streamSize, long origin, long bound) {
        checkSize(streamSize);
        return longs(origin, bound).limit(streamSize);
    }

    public java.util.stream.LongStream longs(final long origin, final long bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException("bound must be greater than origin");
        }
        return java.util.stream.LongStream.generate(new java.util.function.LongSupplier() {
            public long getAsLong() {
                return internalNextLong(origin, bound);
            }
        });
    }

    public java.util.stream.DoubleStream doubles(long streamSize) {
        checkSize(streamSize);
        return doubles().limit(streamSize);
    }

    public java.util.stream.DoubleStream doubles() {
        return java.util.stream.DoubleStream.generate(new java.util.function.DoubleSupplier() {
            public double getAsDouble() {
                return nextDouble();
            }
        });
    }

    public java.util.stream.DoubleStream doubles(long streamSize, double origin, double bound) {
        checkSize(streamSize);
        return doubles(origin, bound).limit(streamSize);
    }

    public java.util.stream.DoubleStream doubles(final double origin, final double bound) {
        checkRange(origin, bound);
        return java.util.stream.DoubleStream.generate(new java.util.function.DoubleSupplier() {
            public double getAsDouble() {
                return internalNextDouble(origin, bound);
            }
        });
    }
}
