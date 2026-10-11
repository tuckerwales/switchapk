import java.math.BigInteger;
import java.util.Random;

/**
 * BigInteger arithmetic against OpenJDK: products, quotients, remainders, modPow (odd and even
 * moduli, Montgomery path), modInverse, gcd and primality on random operands of many sizes and
 * signs. Prints hashes of the results so the output stays short.
 */
public class BigIntegerTest {
    static long h = 1125899906842597L;

    static void mix(BigInteger x) {
        h = 31 * h + x.toString(16).hashCode();
    }

    static void mix(boolean b) {
        h = 31 * h + (b ? 1 : 2);
    }

    static BigInteger rnd(Random r, int bits) {
        BigInteger x = new BigInteger(bits, r);
        return r.nextBoolean() ? x.negate() : x;
    }

    public static void main(String[] args) {
        Random r = new Random(7);
        int[] sizes = {1, 31, 32, 33, 63, 64, 65, 100, 255, 256, 257, 521, 1024, 2048, 3000};
        for (int a : sizes) {
            for (int b : sizes) {
                for (int k = 0; k < 3; k++) {
                    BigInteger x = rnd(r, a), y = rnd(r, b);
                    mix(x.multiply(y));
                    if (y.signum() != 0) {
                        BigInteger[] qr = x.divideAndRemainder(y);
                        mix(qr[0]);
                        mix(qr[1]);
                        mix(x.divide(y));
                        mix(x.remainder(y));
                        mix(x.mod(y.abs()));
                    }
                    mix(x.gcd(y));
                }
            }
            System.out.println("size " + a + " " + Long.toHexString(h));
        }
        // Knuth D corner cases: divisors with top word 0x80000000 and qhat corrections.
        BigInteger big = BigInteger.ONE.shiftLeft(4096).subtract(BigInteger.ONE);
        for (int i = 0; i < 64; i++) {
            BigInteger d = BigInteger.ONE.shiftLeft(31 + 32 * (i % 5)).add(BigInteger.valueOf(i));
            mix(big.divide(d));
            mix(big.mod(d));
            mix(big.subtract(BigInteger.valueOf(i)).mod(d.subtract(BigInteger.ONE)));
        }
        System.out.println("corners " + Long.toHexString(h));
        for (int bits : new int[] {64, 256, 1024, 2048}) {
            for (int k = 0; k < 4; k++) {
                BigInteger m = new BigInteger(bits, r).setBit(bits - 1);
                BigInteger odd = m.setBit(0), even = m.clearBit(0);
                BigInteger base = rnd(r, bits + 17), exp = new BigInteger(bits, r);
                mix(base.modPow(exp, odd));
                mix(base.modPow(exp, even));
                mix(base.modPow(BigInteger.ZERO, odd));
                mix(base.modPow(exp, BigInteger.ONE));
                if (base.gcd(odd).equals(BigInteger.ONE)) {
                    mix(base.modInverse(odd));
                    mix(base.modPow(exp.negate(), odd));
                }
            }
            System.out.println("modPow " + bits + " " + Long.toHexString(h));
        }
        Random pr = new Random(3);
        for (int bits : new int[] {64, 256, 512}) {
            BigInteger p = BigInteger.probablePrime(bits, pr);
            System.out.println("prime " + bits + " " + p.isProbablePrime(50) + " " + p.bitLength());
            mix(p.multiply(BigInteger.valueOf(3)).isProbablePrime(50));
        }
        BigInteger m = new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16);
        System.out.println("p256 " + BigInteger.valueOf(3).modPow(m.subtract(BigInteger.ONE), m)
                + " " + BigInteger.TWO.modInverse(m).toString(16));
        try {
            BigInteger.TEN.divide(BigInteger.ZERO);
        } catch (ArithmeticException e) {
            System.out.println("div0");
        }
        try {
            BigInteger.TEN.modPow(BigInteger.ONE, BigInteger.ZERO);
        } catch (ArithmeticException e) {
            System.out.println("mod0");
        }
        System.out.println("done " + Long.toHexString(h));
    }
}
