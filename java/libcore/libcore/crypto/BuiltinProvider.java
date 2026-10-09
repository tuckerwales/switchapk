package libcore.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.util.Arrays;
import java.util.List;

/**
 * switchapk's built-in crypto, all in Java. It is registered as
 * "AndroidOpenSSL" because Android apps name Conscrypt's provider in
 * getInstance calls; the algorithms are the subset of Conscrypt's that apps
 * use most. Services are created directly, without reflection.
 */
public final class BuiltinProvider extends Provider {
    public BuiltinProvider() {
        super("AndroidOpenSSL", "1.0", "switchapk built-in provider (pure Java digests, HMAC, AES, PBKDF2, SecureRandom)");

        digest("MD5", 64, null);
        digest("SHA-1", 64, list("SHA1", "SHA"));
        digest("SHA-224", 64, list("SHA224"));
        digest("SHA-256", 64, list("SHA256"));
        digest("SHA-384", 128, list("SHA384"));
        digest("SHA-512", 128, list("SHA512"));
        digest("SHA-512/224", 128, list("SHA512/224"));
        digest("SHA-512/256", 128, list("SHA512/256"));

        hmac("HmacMD5", "MD5", 64, 128, null);
        hmac("HmacSHA1", "SHA-1", 64, 160, null);
        hmac("HmacSHA224", "SHA-224", 64, 224, null);
        hmac("HmacSHA256", "SHA-256", 64, 256, null);
        hmac("HmacSHA384", "SHA-384", 128, 384, null);
        hmac("HmacSHA512", "SHA-512", 128, 512, null);

        add(new Svc(this, "Cipher", "AES", AesCipher.class, null, "ECB|CBC|CTR|GCM", "NOPADDING|PKCS5PADDING|PKCS7PADDING") {
            Object create() {
                return new AesCipher();
            }
        });
        fixedAes("AES/ECB/NoPadding", AesCipher.ECB, false);
        fixedAes("AES/ECB/PKCS5Padding", AesCipher.ECB, true);
        fixedAes("AES/CBC/NoPadding", AesCipher.CBC, false);
        fixedAes("AES/CBC/PKCS5Padding", AesCipher.CBC, true);
        fixedAes("AES/CTR/NoPadding", AesCipher.CTR, false);
        fixedAes("AES/GCM/NoPadding", AesCipher.GCM, false);

        add(new Svc(this, "KeyGenerator", "AES", SecretKeyGen.class, null, null, null) {
            Object create() {
                // 192 bits by default, as Android's BouncyCastle AES KeyGenerator.
                return new SecretKeyGen("AES", 192, true);
            }
        });

        pbkdf2("PBKDF2WithHmacSHA1", "SHA-1", 64, false);
        pbkdf2("PBKDF2WithHmacSHA1And8bit", "SHA-1", 64, true);
        pbkdf2("PBKDF2WithHmacSHA224", "SHA-224", 64, false);
        pbkdf2("PBKDF2WithHmacSHA256", "SHA-256", 64, false);
        pbkdf2("PBKDF2WithHmacSHA384", "SHA-384", 128, false);
        pbkdf2("PBKDF2WithHmacSHA512", "SHA-512", 128, false);

        add(new Svc(this, "AlgorithmParameters", "AES", AesParameters.class, null, null, null) {
            Object create() {
                return new AesParameters(false);
            }
        });
        add(new Svc(this, "AlgorithmParameters", "GCM", AesParameters.class, list("AES/GCM"), null, null) {
            Object create() {
                return new AesParameters(true);
            }
        });

        add(new Svc(this, "SecureRandom", "NativePRNG", NativePrng.class, list("SHA1PRNG", "DEFAULT"), null, null) {
            Object create() {
                return new NativePrng();
            }
        });
    }

    private static List<String> list(String... s) {
        return Arrays.asList(s);
    }

    private void add(Svc s) {
        putService(s);
    }

    /** A new instance of a built-in digest, by canonical name. */
    static MessageDigest newDigest(String name) {
        if (name.equals("MD5")) return new Md5();
        if (name.equals("SHA-1")) return new Sha1();
        if (name.equals("SHA-224")) return new Sha256(true);
        if (name.equals("SHA-256")) return new Sha256(false);
        if (name.equals("SHA-384")) return new Sha512("SHA-384", Sha512.IV384, 48);
        if (name.equals("SHA-512")) return new Sha512("SHA-512", Sha512.IV512, 64);
        if (name.equals("SHA-512/224")) return new Sha512("SHA-512/224", Sha512.IV512_224, 28);
        if (name.equals("SHA-512/256")) return new Sha512("SHA-512/256", Sha512.IV512_256, 32);
        throw new IllegalArgumentException(name);
    }

    private void digest(final String name, int blockSize, List<String> aliases) {
        add(new Svc(this, "MessageDigest", name, BlockDigest.class, aliases, null, null) {
            Object create() {
                return newDigest(name);
            }
        });
    }

    private void hmac(final String name, final String digest, final int blockSize, final int bits, List<String> aliases) {
        add(new Svc(this, "Mac", name, Hmac.class, aliases, null, null) {
            Object create() {
                return new Hmac(newDigest(digest), blockSize);
            }
        });
        add(new Svc(this, "KeyGenerator", name, SecretKeyGen.class, null, null, null) {
            Object create() {
                return new SecretKeyGen(name, bits, false);
            }
        });
    }

    private void fixedAes(String name, final int mode, final boolean padding) {
        add(new Svc(this, "Cipher", name, AesCipher.class, null, null, null) {
            Object create() {
                return new AesCipher(mode, padding);
            }
        });
    }

    private void pbkdf2(final String name, final String digest, final int blockSize, final boolean eightBit) {
        add(new Svc(this, "SecretKeyFactory", name, Pbkdf2.class, null, null, null) {
            Object create() {
                return new Pbkdf2(name, new Pbkdf2.Provider() {
                    public MessageDigest digest() {
                        return newDigest(digest);
                    }

                    public int blockSize() {
                        return blockSize;
                    }
                }, eightBit);
            }
        });
    }

    /** A service that constructs its implementation directly. */
    abstract static class Svc extends Provider.Service {
        Svc(Provider p, String type, String algorithm, Class<?> impl, List<String> aliases, String modes, String paddings) {
            super(p, type, algorithm, impl.getName(), aliases, attributes(modes, paddings));
        }

        private static java.util.Map<String, String> attributes(String modes, String paddings) {
            java.util.HashMap<String, String> m = new java.util.HashMap<String, String>();
            if (modes != null) m.put("SupportedModes", modes);
            if (paddings != null) m.put("SupportedPaddings", paddings);
            m.put("ImplementedIn", "Software");
            return m;
        }

        abstract Object create();

        public Object newInstance(Object constructorParameter) throws NoSuchAlgorithmException {
            return create();
        }
    }
}
