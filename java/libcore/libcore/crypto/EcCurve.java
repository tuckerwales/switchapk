package libcore.crypto;

import java.math.BigInteger;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.util.Arrays;
import java.util.Locale;

/**
 * The NIST prime curves P-256, P-384 and P-521 (FIPS 186-4, SEC 2) and point
 * arithmetic on them in Jacobian coordinates over BigInteger (whose
 * multiply and mod are native). Scalar multiplication uses a 4-bit window;
 * it is not constant time.
 */
final class EcCurve {
    final String name;
    final String oid;
    final String[] aliases;
    final BigInteger p, a, b, n;
    final ECPoint g;
    final int fieldBytes;
    final ECParameterSpec spec;

    private EcCurve(String name, String oid, String[] aliases, String p, String a, String b, String gx, String gy, String n) {
        this.name = name;
        this.oid = oid;
        this.aliases = aliases;
        this.p = new BigInteger(p, 16);
        this.a = new BigInteger(a, 16);
        this.b = new BigInteger(b, 16);
        this.n = new BigInteger(n, 16);
        this.g = new ECPoint(new BigInteger(gx, 16), new BigInteger(gy, 16));
        this.fieldBytes = (this.p.bitLength() + 7) / 8;
        this.spec = new Named(this);
    }

    /** An ECParameterSpec that knows its curve, as the JDK's NamedCurve does (toString gives the name). */
    static final class Named extends ECParameterSpec {
        final EcCurve curve;

        Named(EcCurve c) {
            super(new EllipticCurve(new ECFieldFp(c.p), c.a, c.b), c.g, c.n, 1);
            this.curve = c;
        }

        public String toString() {
            return curve.name + " (" + curve.oid + ")";
        }
    }

    static final EcCurve P256 = new EcCurve("secp256r1", "1.2.840.10045.3.1.7",
            new String[] {"secp256r1", "prime256v1", "NIST P-256", "P-256", "1.2.840.10045.3.1.7"},
            "ffffffff00000001000000000000000000000000ffffffffffffffffffffffff",
            "ffffffff00000001000000000000000000000000fffffffffffffffffffffffc",
            "5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b",
            "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296",
            "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5",
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551");
    static final EcCurve P384 = new EcCurve("secp384r1", "1.3.132.0.34",
            new String[] {"secp384r1", "NIST P-384", "P-384", "1.3.132.0.34"},
            "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffeffffffff0000000000000000ffffffff",
            "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffeffffffff0000000000000000fffffffc",
            "b3312fa7e23ee7e4988e056be3f82d19181d9c6efe8141120314088f5013875ac656398d8a2ed19d2a85c8edd3ec2aef",
            "aa87ca22be8b05378eb1c71ef320ad746e1d3b628ba79b9859f741e082542a385502f25dbf55296c3a545e3872760ab7",
            "3617de4a96262c6f5d9e98bf9292dc29f8f41dbd289a147ce9da3113b5f0b8c00a60b1ce1d7e819d7a431d7c90ea0e5f",
            "ffffffffffffffffffffffffffffffffffffffffffffffffc7634d81f4372ddf581a0db248b0a77aecec196accc52973");
    static final EcCurve P521 = new EcCurve("secp521r1", "1.3.132.0.35",
            new String[] {"secp521r1", "NIST P-521", "P-521", "1.3.132.0.35"},
            "01ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            "01fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffc",
            "0051953eb9618e1c9a1f929a21a0b68540eea2da725b99b315f3b8b489918ef109e156193951ec7e937b1652c0bd3bb1bf073573df883d2c34f1ef451fd46b503f00",
            "00c6858e06b70404e9cd9e3ecb662395b4429c648139053fb521f828af606b4d3dbaa14b5e77efe75928fe1dc127a2ffa8de3348b3c1856a429bf97e7e31c2e5bd66",
            "011839296a789a3bc0045c8a5fb42c7d1bd998f54449579b446817afbd17273e662c97ee72995ef42640c550b9013fad0761353c7086a272c24088be94769fd16650",
            "01fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffa51868783bf2f966b7fcc0148f709a5d03bb5c9b8899c47aebb6fb71e91386409");

    static final EcCurve[] ALL = {P256, P384, P521};

    static EcCurve byName(String s) {
        if (s == null) return null;
        String u = s.trim().toLowerCase(Locale.ENGLISH);
        for (EcCurve c : ALL) {
            for (String a : c.aliases) {
                if (a.toLowerCase(Locale.ENGLISH).equals(u)) return c;
            }
        }
        return null;
    }

    static EcCurve bySize(int bits) {
        for (EcCurve c : ALL) {
            if (c.p.bitLength() == bits) return c;
        }
        return null;
    }

    static EcCurve of(ECParameterSpec s) {
        if (s == null) return null;
        if (s instanceof Named) return ((Named) s).curve;
        for (EcCurve c : ALL) {
            if (!(s.getCurve().getField() instanceof ECFieldFp)) continue;
            if (((ECFieldFp) s.getCurve().getField()).getP().equals(c.p) && s.getCurve().getA().equals(c.a)
                    && s.getCurve().getB().equals(c.b) && s.getGenerator().equals(c.g) && s.getOrder().equals(c.n)) {
                return c;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- arithmetic

    /** Jacobian point (X, Y, Z); Z = 0 is infinity. */
    static final class Jp {
        final BigInteger x, y, z;

        Jp(BigInteger x, BigInteger y, BigInteger z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        boolean infinity() {
            return z.signum() == 0;
        }
    }

    private static final Jp INF = new Jp(BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO);

    private BigInteger m(BigInteger v) {
        return v.mod(p);
    }

    Jp dbl(Jp q) {
        if (q.infinity() || q.y.signum() == 0) return INF;
        BigInteger y2 = m(q.y.multiply(q.y));
        BigInteger s = m(q.x.multiply(y2).shiftLeft(2));
        BigInteger z2 = m(q.z.multiply(q.z));
        BigInteger z4 = m(z2.multiply(z2));
        BigInteger mm = m(q.x.multiply(q.x).multiply(BigInteger.valueOf(3)).add(a.multiply(z4)));
        BigInteger x3 = m(mm.multiply(mm).subtract(s.shiftLeft(1)));
        BigInteger y3 = m(mm.multiply(s.subtract(x3)).subtract(y2.multiply(y2).shiftLeft(3)));
        BigInteger z3 = m(q.y.multiply(q.z).shiftLeft(1));
        return new Jp(x3, y3, z3);
    }

    Jp add(Jp q1, Jp q2) {
        if (q1.infinity()) return q2;
        if (q2.infinity()) return q1;
        BigInteger z1s = m(q1.z.multiply(q1.z)), z2s = m(q2.z.multiply(q2.z));
        BigInteger u1 = m(q1.x.multiply(z2s)), u2 = m(q2.x.multiply(z1s));
        BigInteger s1 = m(q1.y.multiply(z2s).multiply(q2.z)), s2 = m(q2.y.multiply(z1s).multiply(q1.z));
        if (u1.equals(u2)) return s1.equals(s2) ? dbl(q1) : INF;
        BigInteger h = m(u2.subtract(u1)), r = m(s2.subtract(s1));
        BigInteger h2 = m(h.multiply(h)), h3 = m(h2.multiply(h));
        BigInteger u1h2 = m(u1.multiply(h2));
        BigInteger x3 = m(r.multiply(r).subtract(h3).subtract(u1h2.shiftLeft(1)));
        BigInteger y3 = m(r.multiply(u1h2.subtract(x3)).subtract(s1.multiply(h3)));
        BigInteger z3 = m(h.multiply(q1.z).multiply(q2.z));
        return new Jp(x3, y3, z3);
    }

    Jp jacobian(ECPoint pt) {
        if (pt == ECPoint.POINT_INFINITY) return INF;
        return new Jp(pt.getAffineX(), pt.getAffineY(), BigInteger.ONE);
    }

    ECPoint affine(Jp q) {
        if (q.infinity()) return ECPoint.POINT_INFINITY;
        BigInteger zi = q.z.modInverse(p);
        BigInteger zi2 = m(zi.multiply(zi));
        return new ECPoint(m(q.x.multiply(zi2)), m(q.y.multiply(zi2).multiply(zi)));
    }

    /** k * pt, k >= 0. */
    ECPoint multiply(ECPoint pt, BigInteger k) {
        return affine(multiplyJ(jacobian(pt), k));
    }

    Jp multiplyJ(Jp base, BigInteger k) {
        Jp[] table = new Jp[16];
        table[0] = INF;
        table[1] = base;
        for (int i = 2; i < 16; i++) table[i] = (i & 1) == 0 ? dbl(table[i / 2]) : add(table[i - 1], base);
        Jp acc = INF;
        for (int i = (k.bitLength() + 3) / 4 * 4 - 4; i >= 0; i -= 4) {
            for (int j = 0; j < 4; j++) acc = dbl(acc);
            int w = (k.testBit(i) ? 1 : 0) | (k.testBit(i + 1) ? 2 : 0) | (k.testBit(i + 2) ? 4 : 0) | (k.testBit(i + 3) ? 8 : 0);
            if (w != 0) acc = add(acc, table[w]);
        }
        return acc;
    }

    /** u1 * G + u2 * Q (for ECDSA verification). */
    ECPoint multiplyAdd(BigInteger u1, ECPoint q, BigInteger u2) {
        return affine(add(multiplyJ(jacobian(g), u1), multiplyJ(jacobian(q), u2)));
    }

    boolean onCurve(ECPoint pt) {
        if (pt == ECPoint.POINT_INFINITY) return false;
        BigInteger x = pt.getAffineX(), y = pt.getAffineY();
        if (x.signum() < 0 || x.compareTo(p) >= 0 || y.signum() < 0 || y.compareTo(p) >= 0) return false;
        BigInteger lhs = m(y.multiply(y));
        BigInteger rhs = m(x.multiply(x).multiply(x).add(a.multiply(x)).add(b));
        return lhs.equals(rhs);
    }

    /** Public key checks (SP 800-56A 5.6.2.3.3): on the curve; the cofactor is 1, so n * Q = O holds too. */
    void validate(ECPoint pt) throws java.security.InvalidKeyException {
        if (!onCurve(pt)) throw new java.security.InvalidKeyException("Point is not on the curve");
    }

    byte[] encodePoint(ECPoint pt) {
        byte[] out = new byte[1 + 2 * fieldBytes];
        out[0] = 4;
        System.arraycopy(Der.unsigned(pt.getAffineX(), fieldBytes), 0, out, 1, fieldBytes);
        System.arraycopy(Der.unsigned(pt.getAffineY(), fieldBytes), 0, out, 1 + fieldBytes, fieldBytes);
        return out;
    }

    ECPoint decodePoint(byte[] enc) throws java.security.InvalidKeyException {
        if (enc.length == 1 + 2 * fieldBytes && enc[0] == 4) {
            ECPoint pt = new ECPoint(new BigInteger(1, Arrays.copyOfRange(enc, 1, 1 + fieldBytes)),
                    new BigInteger(1, Arrays.copyOfRange(enc, 1 + fieldBytes, enc.length)));
            validate(pt);
            return pt;
        }
        if (enc.length == 1 + fieldBytes && (enc[0] == 2 || enc[0] == 3)) {
            BigInteger x = new BigInteger(1, Arrays.copyOfRange(enc, 1, enc.length));
            BigInteger rhs = m(x.multiply(x).multiply(x).add(a.multiply(x)).add(b));
            BigInteger y = rhs.modPow(p.add(BigInteger.ONE).shiftRight(2), p);
            if (!m(y.multiply(y)).equals(rhs)) throw new java.security.InvalidKeyException("Point is not on the curve");
            if (y.testBit(0) != (enc[0] == 3)) y = p.subtract(y);
            ECPoint pt = new ECPoint(x, y);
            validate(pt);
            return pt;
        }
        throw new java.security.InvalidKeyException("Unsupported point encoding");
    }
}
