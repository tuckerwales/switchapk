package libcore.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

/** Service lookup shared by the engine classes (MessageDigest, Cipher, Mac and the rest). */
public final class Services {
    private Services() {
    }

    /** Every installed service of this type and algorithm, in provider preference order. */
    public static List<Provider.Service> all(String type, String algorithm) {
        if (algorithm == null) throw new NullPointerException("null algorithm name");
        ArrayList<Provider.Service> out = new ArrayList<Provider.Service>();
        for (Provider p : Security.getProviders()) {
            Provider.Service s = p.getService(type, algorithm);
            if (s != null) out.add(s);
        }
        return out;
    }

    public static Provider.Service first(String type, String algorithm) throws NoSuchAlgorithmException {
        List<Provider.Service> l = all(type, algorithm);
        if (l.isEmpty()) throw new NoSuchAlgorithmException(algorithm + " " + type + " not available");
        return l.get(0);
    }

    public static Provider.Service in(String type, String algorithm, String provider)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        if (algorithm == null) throw new NullPointerException("null algorithm name");
        if (provider == null || provider.isEmpty()) throw new IllegalArgumentException("Missing provider");
        Provider p = Security.getProvider(provider);
        if (p == null) throw new NoSuchProviderException("no such provider: " + provider);
        return in(type, algorithm, p);
    }

    public static Provider.Service in(String type, String algorithm, Provider provider) throws NoSuchAlgorithmException {
        if (algorithm == null) throw new NullPointerException("null algorithm name");
        if (provider == null) throw new IllegalArgumentException("Missing provider");
        Provider.Service s = provider.getService(type, algorithm);
        if (s == null) {
            throw new NoSuchAlgorithmException("no such algorithm: " + algorithm + " for provider " + provider.getName());
        }
        return s;
    }

    /** Instantiates the service, checking that it is the SPI type the engine expects. */
    public static <T> T newSpi(Provider.Service s, Class<T> spiClass, Object param) throws NoSuchAlgorithmException {
        Object o = s.newInstance(param);
        if (!spiClass.isInstance(o)) {
            throw new NoSuchAlgorithmException("class configured for " + s.getType() + " (provider: "
                    + s.getProvider().getName() + ") is not a " + spiClass.getName() + ": " + o.getClass().getName());
        }
        return spiClass.cast(o);
    }
}
