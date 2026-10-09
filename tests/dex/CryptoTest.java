import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * JCA conformance: run on OpenJDK and on switchapk by tests/run_dex_test.sh, output must match.
 * Prints digests, MACs, ciphertexts and derived keys for fixed inputs (published test vectors where
 * there are some), round trips, and the exception classes thrown for misuse. Provider names differ
 * between the two and are not printed.
 */
public class CryptoTest {
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

    static byte[] pattern(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) (i * 7 + 3);
        return b;
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
        digests();
        hmacs();
        aes();
        gcm();
        pbkdf2();
        params();
        keygen();
        random();
        providers();
        p("done");
    }

    static void digests() throws Exception {
        p("== digests");
        String[] algs = {"MD5", "SHA-1", "SHA-224", "SHA-256", "SHA-384", "SHA-512", "SHA-512/224", "SHA-512/256", "SHA", "sha256"};
        byte[][] inputs = {ascii(""), ascii("abc"), ascii("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"), pattern(1000)};
        for (String a : algs) {
            MessageDigest md = MessageDigest.getInstance(a);
            p(a + " len=" + md.getDigestLength());
            for (byte[] in : inputs) p("  " + hex(md.digest(in)));
            // Incremental, odd chunk sizes, ByteBuffer, clone mid-stream.
            byte[] big = pattern(777);
            md.update(big, 0, 13);
            md.update(big[13]);
            MessageDigest copy = (MessageDigest) md.clone();
            md.update(ByteBuffer.wrap(big, 14, 500));
            md.update(big, 514, 263);
            copy.update(big, 14, 763);
            p("  incr=" + hex(md.digest()) + " clone=" + hex(copy.digest()) + " one=" + hex(MessageDigest.getInstance(a).digest(big)));
        }
        byte[] million = new byte[1000000];
        Arrays.fill(million, (byte) 'a');
        p("SHA-256 million a: " + hex(MessageDigest.getInstance("SHA-256").digest(million)));
        p("isEqual " + MessageDigest.isEqual(ascii("ab"), ascii("ab")) + " " + MessageDigest.isEqual(ascii("ab"), ascii("ac")));
        tryIt("bogus digest", () -> MessageDigest.getInstance("NOPE"));
    }

    static void hmacs() throws Exception {
        p("== hmac");
        // RFC 4231 test cases 1, 2, 6 (key longer than the block) and 7.
        byte[][] keys = {unhex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"), ascii("Jefe"), new byte[131], new byte[131]};
        Arrays.fill(keys[2], (byte) 0xaa);
        Arrays.fill(keys[3], (byte) 0xaa);
        byte[][] data = {ascii("Hi There"), ascii("what do ya want for nothing?"),
            ascii("Test Using Larger Than Block-Size Key - Hash Key First"),
            ascii("This is a test using a larger than block-size key and a larger than block-size data. The key needs to be hashed before being used by the HMAC algorithm.")};
        String[] algs = {"HmacMD5", "HmacSHA1", "HmacSHA224", "HmacSHA256", "HmacSHA384", "HmacSHA512"};
        for (String a : algs) {
            Mac mac = Mac.getInstance(a);
            p(a + " len=" + mac.getMacLength());
            for (int i = 0; i < keys.length; i++) {
                mac.init(new SecretKeySpec(keys[i], a));
                p("  " + hex(mac.doFinal(data[i])));
            }
            mac.update(data[1], 0, 5);
            Mac c = (Mac) mac.clone();
            mac.update(data[1], 5, data[1].length - 5);
            c.update(data[1], 5, data[1].length - 5);
            p("  clone " + hex(mac.doFinal()).equals(hex(c.doFinal())));
        }
        tryIt("mac before init", () -> Mac.getInstance("HmacSHA256").doFinal());
    }

    static SecretKeySpec aesKey(int bytes) {
        return new SecretKeySpec(pattern(bytes), "AES");
    }

    static void aes() throws Exception {
        p("== aes");
        // FIPS 197 appendix C.
        byte[] pt = unhex("00112233445566778899aabbccddeeff");
        for (String k : new String[] {"000102030405060708090a0b0c0d0e0f", "000102030405060708090a0b0c0d0e0f1011121314151617",
                "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"}) {
            Cipher c = Cipher.getInstance("AES/ECB/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(unhex(k), "AES"));
            byte[] ct = c.doFinal(pt);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(unhex(k), "AES"));
            p("fips197 " + hex(ct) + " back=" + hex(c.doFinal(ct)));
        }
        IvParameterSpec iv = new IvParameterSpec(pattern(16));
        String[] transforms = {"AES/ECB/PKCS5Padding", "AES/CBC/PKCS5Padding", "AES/CBC/NoPadding", "AES/CTR/NoPadding", "AES"};
        int[] lens = {0, 1, 15, 16, 17, 31, 32, 100};
        for (String t : transforms) {
            for (int ks : new int[] {16, 24, 32}) {
                for (int n : lens) {
                    boolean noPad = t.endsWith("NoPadding") && !t.contains("CTR");
                    if (noPad && n % 16 != 0) continue;
                    Cipher c = Cipher.getInstance(t);
                    boolean ecb = t.contains("ECB") || t.equals("AES");
                    if (ecb) c.init(Cipher.ENCRYPT_MODE, aesKey(ks));
                    else c.init(Cipher.ENCRYPT_MODE, aesKey(ks), iv);
                    byte[] msg = pattern(n);
                    byte[] ct = c.doFinal(msg);
                    // Streamed in uneven pieces.
                    if (ecb) c.init(Cipher.DECRYPT_MODE, aesKey(ks));
                    else c.init(Cipher.DECRYPT_MODE, aesKey(ks), iv);
                    ByteArrayOut out = new ByteArrayOut();
                    for (int i = 0; i < ct.length; i += 7) out.add(c.update(ct, i, Math.min(7, ct.length - i)));
                    out.add(c.doFinal());
                    p(t + " k" + ks * 8 + " n" + n + " " + hex(ct) + " ok=" + Arrays.equals(out.bytes(), msg));
                }
            }
        }
        // Output size, buffer variants, ByteBuffer variant.
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, aesKey(16), iv);
        p("outsize " + c.getOutputSize(0) + " " + c.getOutputSize(15) + " " + c.getOutputSize(16) + " block=" + c.getBlockSize());
        byte[] buf = new byte[64];
        int n1 = c.update(pattern(20), 0, 20, buf, 0);
        int n2 = c.doFinal(buf, n1);
        p("buffers " + n1 + " " + n2 + " " + hex(Arrays.copyOf(buf, n1 + n2)));
        c.init(Cipher.ENCRYPT_MODE, aesKey(16), iv);
        ByteBuffer dst = ByteBuffer.allocate(64);
        c.doFinal(ByteBuffer.wrap(pattern(20)), dst);
        p("bytebuffer " + dst.position() + " iv=" + hex(c.getIV()));
        // CTR counter carries across the whole block.
        Cipher ctr = Cipher.getInstance("AES/CTR/NoPadding");
        ctr.init(Cipher.ENCRYPT_MODE, aesKey(16), new IvParameterSpec(unhex("ffffffffffffffffffffffffffffffff")));
        p("ctr wrap " + hex(ctr.doFinal(new byte[48])));
        // Errors.
        tryIt("bad padding", () -> {
            Cipher d = Cipher.getInstance("AES/CBC/PKCS5Padding");
            d.init(Cipher.DECRYPT_MODE, aesKey(16), iv);
            return d.doFinal(pattern(32));
        });
        tryIt("bad block size", () -> {
            Cipher d = Cipher.getInstance("AES/CBC/NoPadding");
            d.init(Cipher.ENCRYPT_MODE, aesKey(16), iv);
            return d.doFinal(pattern(17));
        });
        tryIt("decrypt no iv", () -> {
            Cipher d = Cipher.getInstance("AES/CBC/PKCS5Padding");
            d.init(Cipher.DECRYPT_MODE, aesKey(16));
            return "init";
        });
        tryIt("ecb with iv", () -> {
            Cipher d = Cipher.getInstance("AES/ECB/PKCS5Padding");
            d.init(Cipher.ENCRYPT_MODE, aesKey(16), iv);
            return "init";
        });
        tryIt("short iv", () -> {
            Cipher d = Cipher.getInstance("AES/CBC/PKCS5Padding");
            d.init(Cipher.ENCRYPT_MODE, aesKey(16), new IvParameterSpec(new byte[8]));
            return "init";
        });
        tryIt("bad key size", () -> {
            Cipher d = Cipher.getInstance("AES/CBC/PKCS5Padding");
            d.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(new byte[10], "AES"), iv);
            return "init";
        });
        tryIt("uninitialized", () -> Cipher.getInstance("AES/CBC/PKCS5Padding").doFinal(new byte[1]));
        tryIt("bad mode", () -> Cipher.getInstance("AES/XYZ/NoPadding"));
        tryIt("bad padding name", () -> Cipher.getInstance("AES/CBC/XYZPadding"));
        tryIt("bad transformation", () -> Cipher.getInstance("AES/CBC"));
        tryIt("ctr with padding", () -> Cipher.getInstance("AES/CTR/PKCS5Padding"));
        tryIt("random iv length", () -> {
            Cipher d = Cipher.getInstance("AES/CBC/PKCS5Padding");
            d.init(Cipher.ENCRYPT_MODE, aesKey(16));
            return d.getIV().length;
        });
        // Streams.
        Cipher e = Cipher.getInstance("AES/CBC/PKCS5Padding");
        e.init(Cipher.ENCRYPT_MODE, aesKey(32), iv);
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        CipherOutputStream cos = new CipherOutputStream(sink, e);
        cos.write(pattern(1000), 0, 333);
        cos.write(pattern(1000), 333, 667);
        cos.close();
        Cipher d = Cipher.getInstance("AES/CBC/PKCS5Padding");
        d.init(Cipher.DECRYPT_MODE, aesKey(32), iv);
        CipherInputStream cis = new CipherInputStream(new java.io.ByteArrayInputStream(sink.toByteArray()), d);
        ByteArrayOut back = new ByteArrayOut();
        byte[] chunk = new byte[100];
        int r;
        while ((r = cis.read(chunk)) > 0) back.add(Arrays.copyOf(chunk, r));
        cis.close();
        p("streams " + sink.size() + " ok=" + Arrays.equals(back.bytes(), pattern(1000)));
    }

    static void gcm() throws Exception {
        p("== gcm");
        // NIST GCM spec (McGrew/Viega) test cases 2, 3, 4 and 6 (60-byte IV).
        String k3 = "feffe9928665731c6d6a8f9467308308";
        String p4 = "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39";
        String a4 = "feedfacedeadbeeffeedfacedeadbeefabaddad2";
        String[][] cases = {
            {"00000000000000000000000000000000", "000000000000000000000000", "00000000000000000000000000000000", ""},
            {k3, "cafebabefacedbaddecaf888", p4 + "1aafd255", ""},
            {k3, "cafebabefacedbaddecaf888", p4, a4},
            {k3, "9313225df88406e555909c5aff5269aa6a7a9538534f7da1e4c303d2a318a728c3c0c95156809539fcf0e2429a6b525416aedbf5a0de6a57a637b39b", p4, a4},
            {k3 + "feffe9928665731c", "cafebabefacedbaddecaf888", p4, a4},
            {k3 + k3, "cafebabefacedbaddecaf888", p4, a4},
        };
        for (String[] t : cases) {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec key = new SecretKeySpec(unhex(t[0]), "AES");
            GCMParameterSpec spec = new GCMParameterSpec(128, unhex(t[1]));
            c.init(Cipher.ENCRYPT_MODE, key, spec);
            if (!t[3].isEmpty()) c.updateAAD(unhex(t[3]));
            byte[] ct = c.doFinal(unhex(t[2]));
            p("gcm " + hex(ct));
            c.init(Cipher.DECRYPT_MODE, key, spec);
            if (!t[3].isEmpty()) c.updateAAD(unhex(t[3]));
            byte[] partial = c.update(ct, 0, 10);
            p("  update during decrypt=" + (partial == null ? 0 : partial.length) + " back=" + hex(c.doFinal(ct, 10, ct.length - 10)).equals(t[2]));
        }
        SecretKeySpec key = aesKey(32);
        GCMParameterSpec spec = new GCMParameterSpec(96, pattern(12));
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, spec);
        c.updateAAD(ascii("header"));
        byte[] part1 = c.update(pattern(50));
        byte[] part2 = c.doFinal(pattern(70), 50, 20);
        p("streamed tag96 " + hex(part1) + hex(part2));
        tryIt("reuse after doFinal", () -> c.update(new byte[1]));
        tryIt("reinit same iv", () -> {
            c.init(Cipher.ENCRYPT_MODE, key, spec);
            return "init";
        });
        byte[] ct = concat(part1, part2);
        tryIt("tampered", () -> {
            Cipher d = Cipher.getInstance("AES/GCM/NoPadding");
            d.init(Cipher.DECRYPT_MODE, key, spec);
            d.updateAAD(ascii("header"));
            byte[] bad = ct.clone();
            bad[3] ^= 1;
            return d.doFinal(bad);
        });
        tryIt("wrong aad", () -> {
            Cipher d = Cipher.getInstance("AES/GCM/NoPadding");
            d.init(Cipher.DECRYPT_MODE, key, spec);
            d.updateAAD(ascii("headex"));
            return d.doFinal(ct);
        });
        tryIt("too short", () -> {
            Cipher d = Cipher.getInstance("AES/GCM/NoPadding");
            d.init(Cipher.DECRYPT_MODE, key, spec);
            return d.doFinal(new byte[5]);
        });
        tryIt("aad after data", () -> {
            Cipher d = Cipher.getInstance("AES/GCM/NoPadding");
            d.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, pattern(13)));
            d.update(new byte[3]);
            d.updateAAD(new byte[3]);
            return "aad";
        });
        tryIt("bad tag length", () -> {
            Cipher d = Cipher.getInstance("AES/GCM/NoPadding");
            d.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(64, pattern(12)));
            return "init";
        });
        tryIt("decrypt no params", () -> {
            Cipher d = Cipher.getInstance("AES/GCM/NoPadding");
            d.init(Cipher.DECRYPT_MODE, key);
            return "init";
        });
        // The SecretBox pattern: random IV from the cipher, then decrypt with it.
        Cipher e = Cipher.getInstance("AES/GCM/NoPadding");
        e.init(Cipher.ENCRYPT_MODE, key);
        byte[] rivm = e.getIV();
        byte[] sealed = e.doFinal(ascii("secret"));
        Cipher d = Cipher.getInstance("AES/GCM/NoPadding");
        d.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, rivm));
        p("secretbox iv=" + rivm.length + " ct=" + sealed.length + " back=" + new String(d.doFinal(sealed), StandardCharsets.US_ASCII));
        AlgorithmParameters ap = e.getParameters();
        GCMParameterSpec back = ap.getParameterSpec(GCMParameterSpec.class);
        p("params tlen=" + back.getTLen() + " ivmatch=" + Arrays.equals(back.getIV(), rivm));
    }

    static byte[] concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    static void pbkdf2() throws Exception {
        p("== pbkdf2");
        // RFC 6070 vectors (HMAC-SHA1), and SHA-256 / SHA-512 values from the same inputs.
        Object[][] v = {{"password", "salt", 1, 20}, {"password", "salt", 2, 20}, {"password", "salt", 4096, 20},
            {"passwordPASSWORDpassword", "saltSALTsaltSALTsaltSALTsaltSALTsalt", 4096, 25}, {"pass\0word", "sa\0lt", 4096, 16}};
        for (String alg : new String[] {"PBKDF2WithHmacSHA1", "PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA512"}) {
            SecretKeyFactory f = SecretKeyFactory.getInstance(alg);
            for (Object[] t : v) {
                PBEKeySpec spec = new PBEKeySpec(((String) t[0]).toCharArray(), ascii((String) t[1]), (Integer) t[2], (Integer) t[3] * 8);
                SecretKey k = f.generateSecret(spec);
                p(alg + " " + hex(k.getEncoded()) + " " + k.getFormat() + " " + k.getAlgorithm());
            }
        }
        SecretKey uni = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec("pässwörd✓".toCharArray(), ascii("salt"), 10, 256));
        p("utf8 " + hex(uni.getEncoded()));
        tryIt("no key length", () -> SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(new PBEKeySpec("pw".toCharArray(), ascii("salt"), 1)));
        tryIt("wrong spec", () -> SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(new SecretKeySpec(new byte[4], "x")));
    }

    static void params() throws Exception {
        p("== params");
        AlgorithmParameters aes = AlgorithmParameters.getInstance("AES");
        aes.init(new IvParameterSpec(pattern(16)));
        p("aes " + hex(aes.getEncoded()));
        AlgorithmParameters aes2 = AlgorithmParameters.getInstance("AES");
        aes2.init(aes.getEncoded());
        p("aes back " + hex(aes2.getParameterSpec(IvParameterSpec.class).getIV()));
        AlgorithmParameters gcm = AlgorithmParameters.getInstance("GCM");
        gcm.init(new GCMParameterSpec(112, pattern(12)));
        p("gcm " + hex(gcm.getEncoded()));
        AlgorithmParameters gcm2 = AlgorithmParameters.getInstance("GCM");
        gcm2.init(gcm.getEncoded());
        GCMParameterSpec g = gcm2.getParameterSpec(GCMParameterSpec.class);
        p("gcm back " + g.getTLen() + " " + hex(g.getIV()));
        tryIt("wrong spec class", () -> aes.getParameterSpec(GCMParameterSpec.class));
        tryIt("uninitialized", () -> AlgorithmParameters.getInstance("AES").getEncoded());
    }

    static void keygen() throws Exception {
        p("== keygen");
        KeyGenerator g = KeyGenerator.getInstance("AES");
        // Default sizes differ by design (OpenJDK: AES 256, HmacSHA1 64 bytes; Android/BouncyCastle: 192, 20).
        p("aes default alg=" + g.generateKey().getAlgorithm() + " fmt=" + g.generateKey().getFormat());
        g.init(256);
        SecretKey a = g.generateKey(), b = g.generateKey();
        p("aes 256 " + a.getEncoded().length + " distinct=" + !Arrays.equals(a.getEncoded(), b.getEncoded()));
        tryIt("aes 100", () -> {
            KeyGenerator.getInstance("AES").init(100);
            return "init";
        });
        for (String h : new String[] {"HmacSHA256", "HmacSHA512"}) {
            p(h + " " + KeyGenerator.getInstance(h).generateKey().getEncoded().length);
        }
        SecretKeySpec s1 = new SecretKeySpec(pattern(16), "AES"), s2 = new SecretKeySpec(pattern(16), "aes");
        p("keyspec equals=" + s1.equals(s2) + " hash=" + (s1.hashCode() == s2.hashCode()));
        tryIt("empty key", () -> new SecretKeySpec(new byte[0], "AES"));
    }

    static void random() throws Exception {
        p("== random");
        SecureRandom r = new SecureRandom();
        byte[] a = new byte[32], b = new byte[32];
        r.nextBytes(a);
        r.nextBytes(b);
        p("distinct=" + !Arrays.equals(a, b) + " seed=" + r.generateSeed(16).length + " static=" + SecureRandom.getSeed(8).length);
        r.setSeed(42L);
        r.setSeed(new byte[] {1, 2, 3});
        int[] counts = new int[4];
        for (int i = 0; i < 4000; i++) counts[r.nextInt(4)]++;
        boolean spread = true;
        for (int c : counts) spread &= c > 800;
        p("spread=" + spread);
        SecureRandom s = SecureRandom.getInstance("SHA1PRNG");
        p("sha1prng " + (s.nextLong() != s.nextLong()));
        tryIt("bogus random", () -> SecureRandom.getInstance("NOPE"));
    }

    /** A provider registered the way BouncyCastle does it: legacy put() entries naming classes. */
    public static final class TestProvider extends Provider {
        public TestProvider() {
            super("CryptoTestProvider", 1.0, "test");
            put("MessageDigest.XOR8", XorDigest.class.getName());
            put("Alg.Alias.MessageDigest.X8", "XOR8");
            put("MessageDigest.XOR8 ImplementedIn", "Software");
        }
    }

    public static final class XorDigest extends MessageDigestSpi {
        private byte x;

        protected void engineUpdate(byte input) {
            x ^= input;
        }

        protected void engineUpdate(byte[] input, int offset, int len) {
            for (int i = 0; i < len; i++) x ^= input[offset + i];
        }

        protected byte[] engineDigest() {
            byte[] r = {x};
            x = 0;
            return r;
        }

        protected void engineReset() {
            x = 0;
        }
    }

    static void providers() throws Exception {
        p("== providers");
        tryIt("before add", () -> MessageDigest.getInstance("XOR8"));
        Provider tp = new TestProvider();
        p("add " + (Security.addProvider(tp) > 0) + " again=" + Security.addProvider(tp));
        MessageDigest md = MessageDigest.getInstance("X8");
        p("alias " + md.getAlgorithm() + " " + hex(md.digest(new byte[] {1, 2, 4})) + " len=" + md.getDigestLength()
                + " provider=" + md.getProvider().getName());
        Provider.Service s = tp.getService("MessageDigest", "xor8");
        p("service " + s.getType() + " " + s.getAlgorithm() + " " + s.getAttribute("ImplementedIn"));
        p("by name " + MessageDigest.getInstance("XOR8", "CryptoTestProvider").getAlgorithm());
        p("algorithms has XOR8=" + Security.getAlgorithms("MessageDigest").contains("XOR8") + " SHA-256=" + Security.getAlgorithms("MessageDigest").contains("SHA-256"));
        Security.removeProvider("CryptoTestProvider");
        tryIt("after remove", () -> MessageDigest.getInstance("XOR8"));
        tryIt("no such provider", () -> MessageDigest.getInstance("SHA-256", "NoSuchProvider"));
        tryIt("cipher no such provider", () -> Cipher.getInstance("AES", "NoSuchProvider"));
        p("filter " + (Security.getProviders("Cipher.AES") != null) + " " + (Security.getProviders("Cipher.NOPE") == null));
    }

    static final class ByteArrayOut {
        private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        void add(byte[] b) {
            if (b != null) out.write(b, 0, b.length);
        }

        byte[] bytes() {
            return out.toByteArray();
        }
    }
}
