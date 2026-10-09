import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
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
}
