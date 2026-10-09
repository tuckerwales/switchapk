package java.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The installed providers, in preference order. The built-in provider
 * (libcore.crypto.BuiltinProvider, named "AndroidOpenSSL" as on Android) is
 * first; the framework adds "AndroidKeyStore" when an app starts.
 */
public final class Security {
    private static final ArrayList<Provider> providers = new ArrayList<Provider>();
    private static final HashMap<String, String> props = new HashMap<String, String>();
    private static int version;

    static {
        providers.add(new libcore.crypto.BuiltinProvider());
        props.put("securerandom.source", "file:/dev/urandom");
        props.put("keystore.type", "PKCS12");
    }

    private Security() {
    }

    @Deprecated
    public static String getAlgorithmProperty(String algName, String propName) {
        for (Provider p : getProviders()) {
            for (Provider.Service s : p.getServices()) {
                if (s.getAlgorithm().equalsIgnoreCase(algName)) {
                    String v = s.getAttribute(propName);
                    if (v != null) return v;
                }
            }
        }
        return null;
    }

    public static synchronized int insertProviderAt(Provider provider, int position) {
        if (provider == null) throw new NullPointerException();
        for (Provider p : providers) {
            if (p.getName().equals(provider.getName())) return -1;
        }
        int index = position < 1 || position > providers.size() ? providers.size() : position - 1;
        providers.add(index, provider);
        version++;
        return index + 1;
    }

    public static int addProvider(Provider provider) {
        return insertProviderAt(provider, 0);
    }

    public static synchronized void removeProvider(String name) {
        for (int i = 0; i < providers.size(); i++) {
            if (providers.get(i).getName().equals(name)) {
                providers.remove(i);
                version++;
                return;
            }
        }
    }

    public static synchronized Provider[] getProviders() {
        return providers.toArray(new Provider[providers.size()]);
    }

    public static synchronized Provider getProvider(String name) {
        if (name == null) return null;
        for (Provider p : providers) {
            if (p.getName().equals(name)) return p;
        }
        return null;
    }

    public static Provider[] getProviders(String filter) {
        int colon = filter.indexOf(':');
        HashMap<String, String> m = new HashMap<String, String>();
        if (colon < 0) m.put(filter, "");
        else m.put(filter.substring(0, colon), filter.substring(colon + 1));
        return getProviders(m);
    }

    public static Provider[] getProviders(Map<String, String> filter) {
        ArrayList<Provider> out = new ArrayList<Provider>();
        for (Provider p : getProviders()) {
            boolean all = true;
            for (Map.Entry<String, String> f : filter.entrySet()) {
                String k = f.getKey().trim();
                String want = f.getValue().trim();
                int sp = k.indexOf(' ');
                String typeAlg = sp < 0 ? k : k.substring(0, sp);
                int dot = typeAlg.indexOf('.');
                if (dot <= 0) throw new InvalidParameterException("Invalid filter: " + k);
                Provider.Service s = p.getService(typeAlg.substring(0, dot), typeAlg.substring(dot + 1));
                if (s == null) {
                    all = false;
                    break;
                }
                if (sp >= 0) {
                    String v = s.getAttribute(k.substring(sp + 1).trim());
                    if (v == null || !(want.isEmpty() || v.equalsIgnoreCase(want))) {
                        all = false;
                        break;
                    }
                }
            }
            if (all) out.add(p);
        }
        return out.isEmpty() ? null : out.toArray(new Provider[out.size()]);
    }

    public static synchronized String getProperty(String key) {
        if (key == null) throw new NullPointerException();
        return props.get(key);
    }

    public static synchronized void setProperty(String key, String datum) {
        props.put(key, datum);
    }

    public static Set<String> getAlgorithms(String serviceName) {
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        if (serviceName == null || serviceName.isEmpty() || serviceName.endsWith(".")) return out;
        for (Provider p : getProviders()) {
            for (Provider.Service s : p.getServices()) {
                if (s.getType().equalsIgnoreCase(serviceName)) out.add(s.getAlgorithm().toUpperCase(Locale.ENGLISH));
            }
        }
        return out;
    }

    /** framework-internal: changes whenever the provider list changes. */
    public static synchronized int getVersion() {
        return version;
    }
}
