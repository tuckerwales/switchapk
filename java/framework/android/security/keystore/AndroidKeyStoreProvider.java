package android.security.keystore;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;

/**
 * The "AndroidKeyStore" provider: KeyStore, and KeyGenerator and
 * SecretKeyFactory for AES and HMAC keys. ActivityThread installs it with the
 * app's data directory when the app starts, as Android's process start does.
 */
public class AndroidKeyStoreProvider extends Provider {
    public static final String PROVIDER_NAME = "AndroidKeyStore";

    public AndroidKeyStoreProvider() {
        super(PROVIDER_NAME, "1.0", "Android KeyStore security provider (switchapk, software keys)");
        add("KeyStore", "AndroidKeyStore", AndroidKeyStoreSpi.class.getName(), null, 0);
        add("KeyGenerator", "AES", AndroidKeyStoreKeyGeneratorSpi.class.getName(), null, 128);
        String[][] hmacs = {{"HmacSHA1", "SHA-1", "160"}, {"HmacSHA224", "SHA-224", "224"}, {"HmacSHA256", "SHA-256", "256"},
            {"HmacSHA384", "SHA-384", "384"}, {"HmacSHA512", "SHA-512", "512"}};
        for (String[] h : hmacs) add("KeyGenerator", h[0], AndroidKeyStoreKeyGeneratorSpi.class.getName(), h[1], Integer.parseInt(h[2]));
        add("SecretKeyFactory", "AES", AndroidKeyStoreSecretKeyFactorySpi.class.getName(), null, 0);
        for (String[] h : hmacs) add("SecretKeyFactory", h[0], AndroidKeyStoreSecretKeyFactorySpi.class.getName(), null, 0);
    }

    private void add(final String type, final String algorithm, String className, final String digest, final int keySize) {
        putService(new Service(this, type, algorithm, className, null, null) {
            public Object newInstance(Object param) throws NoSuchAlgorithmException {
                if (type.equals("KeyStore")) return new AndroidKeyStoreSpi();
                if (type.equals("KeyGenerator")) return new AndroidKeyStoreKeyGeneratorSpi(algorithm, digest, keySize);
                return new AndroidKeyStoreSecretKeyFactorySpi();
            }
        });
    }

    /** framework-internal: adds the provider (last, as on Android) and sets where keys are kept. */
    public static void install(File dataDir) {
        KeyStoreEntry.setDirectory(new File(dataDir, "keystore"));
        if (Security.getProvider(PROVIDER_NAME) == null) Security.addProvider(new AndroidKeyStoreProvider());
    }
}
