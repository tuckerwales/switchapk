package libcore.ssl;

import java.net.Socket;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

/**
 * KeyManagerFactory "PKIX": an X509KeyManager over a KeyStore's private key entries. Sessions do not send client
 * certificates yet, so the key manager is only there for apps that build one.
 */
public final class KeyManagerFactoryImpl extends KeyManagerFactorySpi {
    private KeyManagerImpl keyManager;

    public KeyManagerFactoryImpl() {
    }

    protected void engineInit(KeyStore ks, char[] password)
            throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        keyManager = new KeyManagerImpl(ks, password);
    }

    protected void engineInit(ManagerFactoryParameters spec) throws InvalidAlgorithmParameterException {
        throw new InvalidAlgorithmParameterException("ManagerFactoryParameters not supported");
    }

    protected KeyManager[] engineGetKeyManagers() {
        if (keyManager == null) throw new IllegalStateException("KeyManagerFactory is not initialized");
        return new KeyManager[] {keyManager};
    }

    static final class KeyManagerImpl extends X509ExtendedKeyManager {
        private final Map<String, PrivateKey> keys = new HashMap<String, PrivateKey>();
        private final Map<String, X509Certificate[]> chains = new HashMap<String, X509Certificate[]>();

        KeyManagerImpl(KeyStore ks, char[] password)
                throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
            if (ks == null) return;
            for (Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
                String alias = e.nextElement();
                if (!ks.isKeyEntry(alias)) continue;
                Key k = ks.getKey(alias, password);
                Certificate[] c = ks.getCertificateChain(alias);
                if (!(k instanceof PrivateKey) || c == null) continue;
                X509Certificate[] xc = new X509Certificate[c.length];
                for (int i = 0; i < c.length; i++) xc[i] = (X509Certificate) c[i];
                keys.put(alias, (PrivateKey) k);
                chains.put(alias, xc);
            }
        }

        private String[] aliases(String keyType) {
            List<String> out = new ArrayList<String>();
            for (Map.Entry<String, PrivateKey> e : keys.entrySet()) {
                if (keyType == null || keyType.equals(e.getValue().getAlgorithm())) out.add(e.getKey());
            }
            return out.isEmpty() ? null : out.toArray(new String[0]);
        }

        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return aliases(keyType);
        }

        public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
            if (keyTypes == null) return null;
            for (String t : keyTypes) {
                String[] a = aliases(t);
                if (a != null) return a[0];
            }
            return null;
        }

        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return aliases(keyType);
        }

        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            String[] a = aliases(keyType);
            return a == null ? null : a[0];
        }

        public X509Certificate[] getCertificateChain(String alias) {
            X509Certificate[] c = chains.get(alias);
            return c == null ? null : c.clone();
        }

        public PrivateKey getPrivateKey(String alias) {
            return keys.get(alias);
        }

        public String chooseEngineClientAlias(String[] keyTypes, Principal[] issuers, SSLEngine engine) {
            return chooseClientAlias(keyTypes, issuers, null);
        }

        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
            return chooseServerAlias(keyType, issuers, null);
        }
    }
}
