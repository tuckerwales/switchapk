import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.interfaces.*;
import javax.crypto.spec.*;

/**
 * Public-key JCA conformance against OpenJDK (run by tests/run_dex_test.sh): RSA, EC and DH keys and
 * their DER encodings, deterministic signatures (PKCS#1 v1.5), signature and cipher round trips, key
 * agreement with fixed keys, and published vectors. Randomized outputs (ECDSA, OAEP) are only
 * checked by round trip.
 */
public class PublicKeyTest {
    static void p(Object o) {
        System.out.println(o);
    }

    static String hex(byte[] b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    static byte[] unhex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    interface Op {
        Object run() throws Exception;
    }

    static void tryIt(String label, Op op) {
        try {
            Object r = op.run();
            p(label + ": " + (r instanceof byte[] ? hex((byte[]) r) : String.valueOf(r)));
        } catch (Exception e) {
            p(label + ": " + e.getClass().getName());
        }
    }

    public static void main(String[] args) throws Exception {
        rsa();
        ec();
        dh();
        p("done");
    }

    static RSAPrivateCrtKeySpec rsaSpec(int bits, long seed) {
        Random r = new Random(seed);
        BigInteger e = BigInteger.valueOf(65537);
        while (true) {
            BigInteger p = new BigInteger(bits / 2, r).setBit(bits / 2 - 1).setBit(bits / 2 - 2).nextProbablePrime();
            BigInteger q = new BigInteger(bits / 2, r).setBit(bits / 2 - 1).setBit(bits / 2 - 2).nextProbablePrime();
            BigInteger p1 = p.subtract(BigInteger.ONE), q1 = q.subtract(BigInteger.ONE);
            BigInteger phi = p1.multiply(q1);
            if (!e.gcd(phi).equals(BigInteger.ONE) || p.equals(q)) continue;
            if (p.compareTo(q) < 0) {
                BigInteger t = p;
                p = q;
                q = t;
                p1 = p.subtract(BigInteger.ONE);
                q1 = q.subtract(BigInteger.ONE);
            }
            BigInteger n = p.multiply(q);
            BigInteger d = e.modInverse(phi);
            return new RSAPrivateCrtKeySpec(n, e, d, p, q, d.mod(p1), d.mod(q1), q.modInverse(p));
        }
    }

    static void rsa() throws Exception {
        p("== rsa");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKeySpec spec = rsaSpec(1024, 11);
        PrivateKey priv = kf.generatePrivate(spec);
        PublicKey pub = kf.generatePublic(new RSAPublicKeySpec(spec.getModulus(), spec.getPublicExponent()));
        p("pub " + pub.getAlgorithm() + " " + pub.getFormat() + " " + hex(pub.getEncoded()));
        p("priv " + priv.getAlgorithm() + " " + priv.getFormat() + " " + hex(priv.getEncoded()));
        PublicKey pub2 = kf.generatePublic(new X509EncodedKeySpec(pub.getEncoded()));
        PrivateKey priv2 = kf.generatePrivate(new PKCS8EncodedKeySpec(priv.getEncoded()));
        p("reparse " + pub2.equals(pub) + " " + Arrays.equals(priv2.getEncoded(), priv.getEncoded())
                + " crt=" + (priv2 instanceof RSAPrivateCrtKey));
        RSAPublicKeySpec ps = kf.getKeySpec(pub, RSAPublicKeySpec.class);
        RSAPrivateCrtKeySpec cs = kf.getKeySpec(priv, RSAPrivateCrtKeySpec.class);
        p("specs " + ps.getModulus().equals(spec.getModulus()) + " " + cs.getPrimeQ().equals(spec.getPrimeQ()));
        PrivateKey plain = kf.generatePrivate(new RSAPrivateKeySpec(spec.getModulus(), spec.getPrivateExponent()));
        p("plain " + plain.getFormat() + " " + hex(plain.getEncoded()).length());
        byte[] msg = ascii("The quick brown fox jumps over the lazy dog");
        for (String alg : new String[] {"MD5withRSA", "SHA1withRSA", "SHA224withRSA", "SHA256withRSA", "SHA384withRSA", "SHA512withRSA"}) {
            Signature s = Signature.getInstance(alg);
            s.initSign(priv);
            s.update(msg);
            byte[] sig = s.sign();
            Signature plainSig = Signature.getInstance(alg);
            plainSig.initSign(plain);
            plainSig.update(msg);
            boolean same = Arrays.equals(plainSig.sign(), sig);
            Signature v = Signature.getInstance(alg);
            v.initVerify(pub);
            v.update(msg);
            boolean ok = v.verify(sig);
            v.initVerify(pub);
            v.update(ascii("tampered"));
            boolean bad = v.verify(sig);
            p(alg + " " + hex(sig) + " ok=" + ok + " bad=" + bad + " noncrt=" + same);
        }
        Signature none = Signature.getInstance("NONEwithRSA");
        none.initSign(priv);
        none.update(MessageDigest.getInstance("SHA-256").digest(msg));
        p("NONEwithRSA " + hex(none.sign()));
        tryIt("wrong length sig", () -> {
            Signature v = Signature.getInstance("SHA256withRSA");
            v.initVerify(pub);
            v.update(msg);
            return v.verify(new byte[10]);
        });
        tryIt("sign with public", () -> {
            Signature.getInstance("SHA256withRSA").initSign((PrivateKey) (Object) null);
            return "init";
        });

        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, pub);
        byte[] ct = c.doFinal(msg);
        c.init(Cipher.DECRYPT_MODE, priv);
        p("nopadding " + hex(ct) + " back=" + new String(c.doFinal(ct), StandardCharsets.US_ASCII).trim().length());
        for (String t : new String[] {"RSA", "RSA/ECB/PKCS1Padding", "RSA/ECB/OAEPPadding", "RSA/ECB/OAEPWithSHA-1AndMGF1Padding",
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"}) {
            Cipher e = Cipher.getInstance(t);
            e.init(Cipher.ENCRYPT_MODE, pub);
            byte[] a = e.doFinal(msg), b = e.doFinal(msg);
            Cipher d = Cipher.getInstance(t);
            d.init(Cipher.DECRYPT_MODE, priv);
            p(t + " len=" + a.length + " randomized=" + !Arrays.equals(a, b) + " back=" + new String(d.doFinal(a), StandardCharsets.US_ASCII)
                    + " out=" + e.getOutputSize(5));
        }
        Cipher oaep = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec os = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, new PSource.PSpecified(ascii("label")));
        oaep.init(Cipher.ENCRYPT_MODE, pub, os);
        byte[] oct = oaep.doFinal(msg);
        oaep.init(Cipher.DECRYPT_MODE, priv, os);
        p("oaep params back=" + new String(oaep.doFinal(oct), StandardCharsets.US_ASCII));
        tryIt("oaep wrong label", () -> {
            Cipher d = Cipher.getInstance("RSA/ECB/OAEPPadding");
            d.init(Cipher.DECRYPT_MODE, priv, new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, new PSource.PSpecified(ascii("other"))));
            return d.doFinal(oct);
        });
        Cipher sigMode = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        sigMode.init(Cipher.ENCRYPT_MODE, priv);
        byte[] type1 = sigMode.doFinal(msg);
        sigMode.init(Cipher.DECRYPT_MODE, pub);
        p("pkcs1 type1 " + hex(type1) + " back=" + new String(sigMode.doFinal(type1), StandardCharsets.US_ASCII));
        tryIt("too long", () -> {
            Cipher e = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            e.init(Cipher.ENCRYPT_MODE, pub);
            return e.doFinal(new byte[200]);
        });
        tryIt("bad padding", () -> {
            Cipher d = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            d.init(Cipher.DECRYPT_MODE, priv);
            return d.doFinal(ct);
        });
        // Key wrapping.
        Cipher w = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        w.init(Cipher.WRAP_MODE, pub);
        byte[] wrapped = w.wrap(new SecretKeySpec(unhex("000102030405060708090a0b0c0d0e0f"), "AES"));
        w.init(Cipher.UNWRAP_MODE, priv);
        p("unwrap " + hex(w.unwrap(wrapped, "AES", Cipher.SECRET_KEY).getEncoded()));

        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(1024);
        KeyPair kp = g.generateKeyPair();
        RSAPublicKey gp = (RSAPublicKey) kp.getPublic();
        RSAPrivateCrtKey gc = (RSAPrivateCrtKey) kp.getPrivate();
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update(msg);
        byte[] gs = s.sign();
        s.initVerify(kp.getPublic());
        s.update(msg);
        p("generated bits=" + gp.getModulus().bitLength() + " e=" + gp.getPublicExponent() + " verify=" + s.verify(gs)
                + " pq=" + gc.getPrimeP().multiply(gc.getPrimeQ()).equals(gp.getModulus()));
        g.initialize(new RSAKeyGenParameterSpec(768, RSAKeyGenParameterSpec.F0));
        p("generated f0 " + ((RSAPublicKey) g.generateKeyPair().getPublic()).getPublicExponent());
        tryIt("tiny", () -> {
            KeyPairGenerator.getInstance("RSA").initialize(100);
            return "init";
        });
    }

    static ECParameterSpec curve(String name) throws Exception {
        AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
        ap.init(new ECGenParameterSpec(name));
        return ap.getParameterSpec(ECParameterSpec.class);
    }

    /** The point with this x on the curve (either y: ECDH only uses x of the result). */
    static ECPoint lift(ECParameterSpec ps, BigInteger x) {
        BigInteger p = ((ECFieldFp) ps.getCurve().getField()).getP();
        BigInteger rhs = x.pow(3).add(ps.getCurve().getA().multiply(x)).add(ps.getCurve().getB()).mod(p);
        return new ECPoint(x, rhs.modPow(p.add(BigInteger.ONE).shiftRight(2), p));
    }

    static byte[] ecdh(PrivateKey a, PublicKey b) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(a);
        ka.doPhase(b, true);
        return ka.generateSecret();
    }

    static void ec() throws Exception {
        p("== ec");
        KeyFactory kf = KeyFactory.getInstance("EC");
        Random r = new Random(5);
        byte[] msg = ascii("sample");
        for (String name : new String[] {"secp256r1", "secp384r1", "secp521r1"}) {
            ECParameterSpec ps = curve(name);
            AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
            ap.init(ps);
            p(name + " bits=" + ps.getCurve().getField().getFieldSize() + " h=" + ps.getCofactor() + " oid=" + hex(ap.getEncoded())
                    + " order=" + ps.getOrder().toString(16));
            BigInteger d1 = new BigInteger(ps.getOrder().bitLength() - 1, r), d2 = new BigInteger(ps.getOrder().bitLength() - 1, r);
            PrivateKey k1 = kf.generatePrivate(new ECPrivateKeySpec(d1, ps));
            PrivateKey k2 = kf.generatePrivate(new ECPrivateKeySpec(d2, ps));
            PublicKey g = kf.generatePublic(new ECPublicKeySpec(ps.getGenerator(), ps));
            byte[] x1 = ecdh(k1, g), x2 = ecdh(k2, g);
            p("  d*G x " + hex(x1));
            PublicKey q1 = kf.generatePublic(new ECPublicKeySpec(lift(ps, new BigInteger(1, x1)), ps));
            PublicKey q2 = kf.generatePublic(new ECPublicKeySpec(lift(ps, new BigInteger(1, x2)), ps));
            byte[] s12 = ecdh(k1, q2), s21 = ecdh(k2, q1);
            p("  ecdh " + hex(s12) + " symmetric=" + Arrays.equals(s12, s21));
            p("  pub " + q1.getFormat() + " " + hex(q1.getEncoded()));
            p("  priv " + k1.getFormat() + " " + hex(k1.getEncoded()));
            PublicKey q1b = kf.generatePublic(new X509EncodedKeySpec(q1.getEncoded()));
            PrivateKey k1b = kf.generatePrivate(new PKCS8EncodedKeySpec(k1.getEncoded()));
            ECPublicKeySpec qs = kf.getKeySpec(q1b, ECPublicKeySpec.class);
            ECPrivateKeySpec ks = kf.getKeySpec(k1b, ECPrivateKeySpec.class);
            p("  reparse " + qs.getW().equals(((ECPublicKey) q1).getW()) + " " + ks.getS().equals(d1)
                    + " " + qs.getParams().getOrder().equals(ps.getOrder()));
            // ECDSA: sign with k1, verify with the lifted point (or its negation, which fails).
            for (String alg : new String[] {"SHA1withECDSA", "SHA256withECDSA", "SHA384withECDSA", "SHA512withECDSA"}) {
                Signature s = Signature.getInstance(alg);
                s.initSign(k1);
                s.update(msg);
                byte[] sig = s.sign();
                Signature v = Signature.getInstance(alg);
                v.initVerify(q1);
                v.update(msg);
                boolean ok = v.verify(sig);
                ECPoint w = ((ECPublicKey) q1).getW();
                BigInteger p = ((ECFieldFp) ps.getCurve().getField()).getP();
                PublicKey neg = kf.generatePublic(new ECPublicKeySpec(new ECPoint(w.getAffineX(), p.subtract(w.getAffineY())), ps));
                v.initVerify(neg);
                v.update(msg);
                boolean negOk = v.verify(sig);
                p("  " + alg + " one-of-two=" + (ok ^ negOk));
            }
        }
        // RFC 6979 A.2.5: P-256 key and the SHA-256 signature of "sample".
        ECParameterSpec p256 = curve("secp256r1");
        PublicKey rfc = kf.generatePublic(new ECPublicKeySpec(new ECPoint(
                new BigInteger("60FED4BA255A9D31C961EB74C6356D68C049B8923B61FA6CE669622E60F29FB6", 16),
                new BigInteger("7903FE1008B8BC99A41AE9E95628BC64F2F1B20C2D7E9F5177A3C294D4462299", 16)), p256));
        byte[] rs = unhex("3046022100EFD48B2AACB6A8FD1140DD9CD45E81D69D2C877B56AAF991C34D0EA84EAF3716022100F7CB1C942D657C41D436C7A1B6E29F65F3E900DBB9AFF4064DC4AB2F843ACDA8");
        Signature v = Signature.getInstance("SHA256withECDSA");
        v.initVerify(rfc);
        v.update(msg);
        p("rfc6979 " + v.verify(rs));
        PrivateKey rfcPriv = kf.generatePrivate(new ECPrivateKeySpec(
                new BigInteger("C9AFA9D845BA75166B5C215767B1D6934E50C3DB36E89B127B8A622B120F6721", 16), p256));
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(rfcPriv);
        s.update(msg);
        byte[] mine = s.sign();
        v.initVerify(rfc);
        v.update(msg);
        p("rfc6979 own " + v.verify(mine));
        Signature none = Signature.getInstance("NONEwithECDSA");
        none.initSign(rfcPriv);
        none.update(MessageDigest.getInstance("SHA-256").digest(msg));
        byte[] ns = none.sign();
        v.initVerify(rfc);
        v.update(msg);
        p("none verifies as sha256 " + v.verify(ns));
        // Generated keys.
        for (String name : new String[] {"secp256r1", "secp384r1", "secp521r1"}) {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec(name));
            KeyPair a = g.generateKeyPair(), b = g.generateKeyPair();
            Signature gs = Signature.getInstance("SHA256withECDSA");
            gs.initSign(a.getPrivate());
            gs.update(msg);
            byte[] sig = gs.sign();
            gs.initVerify(a.getPublic());
            gs.update(msg);
            p("generated " + name + " verify=" + gs.verify(sig) + " ecdh=" + Arrays.equals(ecdh(a.getPrivate(), b.getPublic()), ecdh(b.getPrivate(), a.getPublic()))
                    + " fieldsize=" + ((ECPublicKey) a.getPublic()).getParams().getCurve().getField().getFieldSize());
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(384);
        p("by size " + ((ECPublicKey) g.generateKeyPair().getPublic()).getParams().getOrder().bitLength());
        tryIt("ecdh mixed curves", () -> ecdh(kf.generatePrivate(new ECPrivateKeySpec(BigInteger.TEN, curve("secp256r1"))),
                kf.generatePublic(new ECPublicKeySpec(curve("secp384r1").getGenerator(), curve("secp384r1")))));
        tryIt("unknown curve", () -> {
            KeyPairGenerator.getInstance("EC").initialize(new ECGenParameterSpec("nonesuch"));
            return "init";
        });
        tryIt("garbage sig", () -> {
            Signature x = Signature.getInstance("SHA256withECDSA");
            x.initVerify(rfc);
            x.update(msg);
            return x.verify(new byte[] {1, 2, 3});
        });
    }

    static final BigInteger MODP2048 = new BigInteger("FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD"
            + "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
            + "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
            + "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B"
            + "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA0510"
            + "15728E5A8AACAA68FFFFFFFFFFFFFFFF", 16);

    static byte[] agree(PrivateKey a, PublicKey b) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("DH");
        ka.init(a);
        ka.doPhase(b, true);
        return ka.generateSecret();
    }

    static void dh() throws Exception {
        p("== dh");
        BigInteger g = BigInteger.TWO;
        Random r = new Random(9);
        BigInteger x1 = new BigInteger(400, r), x2 = new BigInteger(400, r);
        KeyFactory kf = KeyFactory.getInstance("DH");
        PrivateKey a = kf.generatePrivate(new DHPrivateKeySpec(x1, MODP2048, g));
        PrivateKey b = kf.generatePrivate(new DHPrivateKeySpec(x2, MODP2048, g));
        PublicKey pa = kf.generatePublic(new DHPublicKeySpec(g.modPow(x1, MODP2048), MODP2048, g));
        PublicKey pb = kf.generatePublic(new DHPublicKeySpec(g.modPow(x2, MODP2048), MODP2048, g));
        byte[] s1 = agree(a, pb), s2 = agree(b, pa);
        p("secret " + s1.length + " " + hex(s1) + " symmetric=" + Arrays.equals(s1, s2));
        p("pub " + pa.getFormat() + " " + hex(pa.getEncoded()));
        p("priv " + a.getFormat() + " " + hex(a.getEncoded()));
        PublicKey pa2 = kf.generatePublic(new X509EncodedKeySpec(pa.getEncoded()));
        PrivateKey a2 = kf.generatePrivate(new PKCS8EncodedKeySpec(a.getEncoded()));
        DHPublicKeySpec ps = kf.getKeySpec(pa2, DHPublicKeySpec.class);
        p("reparse " + ps.getY().equals(((DHPublicKey) pa).getY()) + " " + ((DHPrivateKey) a2).getX().equals(x1)
                + " p=" + ps.getP().equals(MODP2048));
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
        kpg.initialize(new DHParameterSpec(MODP2048, g));
        KeyPair k1 = kpg.generateKeyPair(), k2 = kpg.generateKeyPair();
        p("generated " + Arrays.equals(agree(k1.getPrivate(), k2.getPublic()), agree(k2.getPrivate(), k1.getPublic()))
                + " p=" + ((DHPublicKey) k1.getPublic()).getParams().getP().equals(MODP2048));
        kpg.initialize(new DHParameterSpec(MODP2048, g, 300));
        p("l=300 bits=" + ((DHPrivateKey) kpg.generateKeyPair().getPrivate()).getX().bitLength());
        kpg = KeyPairGenerator.getInstance("DH");
        kpg.initialize(2048);
        KeyPair d1 = kpg.generateKeyPair();
        KeyPairGenerator kpg2 = KeyPairGenerator.getInstance("DH");
        kpg2.initialize(((DHPublicKey) d1.getPublic()).getParams());
        KeyPair d2 = kpg2.generateKeyPair();
        p("default 2048 " + ((DHPublicKey) d1.getPublic()).getParams().getP().bitLength() + " "
                + Arrays.equals(agree(d1.getPrivate(), d2.getPublic()), agree(d2.getPrivate(), d1.getPublic())));
        tryIt("y = 1", () -> agree(a, kf.generatePublic(new DHPublicKeySpec(BigInteger.ONE, MODP2048, g))));
        tryIt("y = p - 1", () -> agree(a, kf.generatePublic(new DHPublicKeySpec(MODP2048.subtract(BigInteger.ONE), MODP2048, g))));
        tryIt("other group", () -> agree(a, kf.generatePublic(new DHPublicKeySpec(BigInteger.TEN, MODP2048.add(BigInteger.TWO), g))));
    }
}
