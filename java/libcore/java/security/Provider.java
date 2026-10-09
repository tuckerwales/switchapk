package java.security;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A set of algorithm implementations. Services come from two places, as in the
 * JDK: legacy property entries ("Cipher.AES" = class name, "Alg.Alias.Cipher.X"
 * = algorithm, "Cipher.AES SupportedModes" = attribute value), which is how
 * BouncyCastle and most third-party providers register, and Service objects
 * added with {@link #putService}.
 */
public abstract class Provider extends java.util.Properties {
    private final String name;
    private final String versionStr;
    private final double version;
    private final String info;

    // Service objects added with putService, keyed by "TYPE.ALGORITHM" in upper case.
    private final LinkedHashMap<String, Service> serviceMap = new LinkedHashMap<String, Service>();
    // Services built from the legacy entries, rebuilt after the entries change.
    private transient Map<String, Service> legacyMap;

    @Deprecated
    protected Provider(String name, double version, String info) {
        this.name = name;
        this.version = version;
        this.versionStr = Double.toString(version);
        this.info = info;
        putProviderInfo();
    }

    protected Provider(String name, String versionStr, String info) {
        this.name = name;
        this.versionStr = versionStr;
        this.version = parseVersion(versionStr);
        this.info = info;
        putProviderInfo();
    }

    private static double parseVersion(String s) {
        int end = 0;
        boolean dot = false;
        while (end < s.length()) {
            char c = s.charAt(end);
            if (c == '.' && !dot) dot = true;
            else if (c < '0' || c > '9') break;
            end++;
        }
        try {
            return end == 0 ? 0 : Double.parseDouble(s.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void putProviderInfo() {
        super.put("Provider.id name", name);
        super.put("Provider.id version", versionStr);
        super.put("Provider.id info", info);
        super.put("Provider.id className", getClass().getName());
    }

    public Provider configure(String configArg) {
        throw new UnsupportedOperationException("configure is not supported");
    }

    public boolean isConfigured() {
        return true;
    }

    public String getName() {
        return name;
    }

    @Deprecated
    public double getVersion() {
        return version;
    }

    public String getVersionStr() {
        return versionStr;
    }

    public String getInfo() {
        return info;
    }

    public String toString() {
        return name + " version " + versionStr;
    }

    public synchronized void clear() {
        super.clear();
        putProviderInfo();
        changed();
    }

    public synchronized void load(InputStream inStream) throws IOException {
        super.load(inStream);
        changed();
    }

    public synchronized void putAll(Map<?, ?> t) {
        super.putAll(t);
        changed();
    }

    public synchronized Object put(Object key, Object value) {
        Object old = super.put(key, value);
        changed();
        return old;
    }

    public synchronized Object remove(Object key) {
        Object old = super.remove(key);
        changed();
        return old;
    }

    public synchronized Object putIfAbsent(Object key, Object value) {
        Object old = get(key);
        if (old == null) put(key, value);
        return old;
    }

    public synchronized boolean remove(Object key, Object value) {
        Object old = get(key);
        if (old == null || !old.equals(value)) return false;
        remove(key);
        return true;
    }

    public synchronized boolean replace(Object key, Object oldValue, Object newValue) {
        Object cur = get(key);
        if (cur == null || !cur.equals(oldValue)) return false;
        put(key, newValue);
        return true;
    }

    public synchronized Object replace(Object key, Object value) {
        return containsKey(key) ? put(key, value) : null;
    }

    public synchronized void replaceAll(java.util.function.BiFunction<? super Object, ? super Object, ? extends Object> function) {
        for (Map.Entry<Object, Object> e : new ArrayList<Map.Entry<Object, Object>>(super.entrySet())) {
            super.put(e.getKey(), function.apply(e.getKey(), e.getValue()));
        }
        changed();
    }

    public synchronized Object compute(Object key,
            java.util.function.BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
        Object v = remappingFunction.apply(key, get(key));
        if (v == null) remove(key);
        else put(key, v);
        return v;
    }

    public synchronized Object computeIfAbsent(Object key, java.util.function.Function<? super Object, ? extends Object> mappingFunction) {
        Object v = get(key);
        if (v == null) {
            v = mappingFunction.apply(key);
            if (v != null) put(key, v);
        }
        return v;
    }

    public synchronized Object computeIfPresent(Object key,
            java.util.function.BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
        Object v = get(key);
        if (v == null) return null;
        v = remappingFunction.apply(key, v);
        if (v == null) remove(key);
        else put(key, v);
        return v;
    }

    public synchronized Object merge(Object key, Object value,
            java.util.function.BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
        Object old = get(key);
        Object v = old == null ? value : remappingFunction.apply(old, value);
        if (v == null) remove(key);
        else put(key, v);
        return v;
    }

    public Object get(Object key) {
        return super.get(key);
    }

    public synchronized Object getOrDefault(Object key, Object defaultValue) {
        Object v = get(key);
        return v != null ? v : defaultValue;
    }

    public synchronized void forEach(java.util.function.BiConsumer<? super Object, ? super Object> action) {
        for (Map.Entry<Object, Object> e : super.entrySet()) action.accept(e.getKey(), e.getValue());
    }

    public synchronized Set<Map.Entry<Object, Object>> entrySet() {
        return Collections.unmodifiableSet(super.entrySet());
    }

    public Set<Object> keySet() {
        return Collections.unmodifiableSet(super.keySet());
    }

    public java.util.Collection<Object> values() {
        return Collections.unmodifiableCollection(super.values());
    }

    private void changed() {
        legacyMap = null;
    }

    private static String key(String type, String algorithm) {
        return type.toUpperCase(Locale.ENGLISH) + "." + algorithm.toUpperCase(Locale.ENGLISH);
    }

    public synchronized Service getService(String type, String algorithm) {
        if (type == null || algorithm == null) throw new NullPointerException();
        String k = key(type, algorithm);
        Service s = serviceMap.get(k);
        if (s != null) return s;
        return legacyServices().get(k);
    }

    public synchronized Set<Service> getServices() {
        LinkedHashSet<Service> all = new LinkedHashSet<Service>(serviceMap.values());
        for (Service s : legacyServices().values()) {
            if (!all.contains(s)) all.add(s);
        }
        return Collections.unmodifiableSet(all);
    }

    protected synchronized void putService(Service s) {
        if (s.getProvider() != this) throw new IllegalArgumentException("service.getProvider() must match this Provider object");
        serviceMap.put(key(s.getType(), s.getAlgorithm()), s);
        for (String alias : s.aliases) serviceMap.put(key(s.getType(), alias), s);
        super.put(s.getType() + "." + s.getAlgorithm(), s.getClassName());
        for (String alias : s.aliases) super.put("Alg.Alias." + s.getType() + "." + alias, s.getAlgorithm());
        for (Map.Entry<String, String> a : s.attributes.entrySet()) {
            super.put(s.getType() + "." + s.getAlgorithm() + " " + a.getKey(), a.getValue());
        }
    }

    protected synchronized void removeService(Service s) {
        if (s == null) throw new NullPointerException();
        serviceMap.remove(key(s.getType(), s.getAlgorithm()));
        for (String alias : s.aliases) serviceMap.remove(key(s.getType(), alias));
        super.remove(s.getType() + "." + s.getAlgorithm());
        changed();
    }

    Service getDefaultSecureRandomService() {
        for (Service s : getServices()) {
            if ("SecureRandom".equals(s.getType())) return s;
        }
        return null;
    }

    private Map<String, Service> legacyServices() {
        Map<String, Service> m = legacyMap;
        if (m != null) return m;
        m = new HashMap<String, Service>();
        // type.algorithm -> service; then attributes and aliases.
        HashMap<String, Service> byName = new HashMap<String, Service>();
        ArrayList<Object[]> attrs = new ArrayList<Object[]>();
        ArrayList<String[]> aliases = new ArrayList<String[]>();
        for (Map.Entry<Object, Object> e : super.entrySet()) {
            if (!(e.getKey() instanceof String) || !(e.getValue() instanceof String)) continue;
            String k = ((String) e.getKey()).trim();
            String v = ((String) e.getValue()).trim();
            if (k.startsWith("Provider.")) continue;
            if (k.startsWith("Alg.Alias.")) {
                String rest = k.substring(10);
                int dot = rest.indexOf('.');
                if (dot > 0) aliases.add(new String[] { rest.substring(0, dot), rest.substring(dot + 1), v });
                continue;
            }
            int dot = k.indexOf('.');
            if (dot <= 0) continue;
            String type = k.substring(0, dot);
            String rest = k.substring(dot + 1);
            int sp = rest.indexOf(' ');
            if (sp >= 0) {
                attrs.add(new Object[] { type, rest.substring(0, sp), rest.substring(sp + 1).trim(), v });
                continue;
            }
            String uk = key(type, rest);
            if (serviceMap.containsKey(uk)) continue;
            Service s = new Service(this, type, rest, v, null, null);
            byName.put(uk, s);
        }
        for (Object[] a : attrs) {
            Service s = byName.get(key((String) a[0], (String) a[1]));
            if (s != null) s.attributes.put((String) a[2], (String) a[3]);
        }
        m.putAll(byName);
        for (String[] a : aliases) {
            String target = key(a[0], a[2]);
            Service s = byName.get(target);
            if (s == null) s = serviceMap.get(target);
            if (s != null && !m.containsKey(key(a[0], a[1]))) {
                m.put(key(a[0], a[1]), s);
                s.aliases.add(a[1]);
            }
        }
        legacyMap = m;
        return m;
    }

    public static class Service {
        private final Provider provider;
        private final String type;
        private final String algorithm;
        private final String className;
        final List<String> aliases;
        final Map<String, String> attributes;

        public Service(Provider provider, String type, String algorithm, String className, List<String> aliases,
                Map<String, String> attributes) {
            if (provider == null || type == null || algorithm == null || className == null) {
                throw new NullPointerException();
            }
            this.provider = provider;
            this.type = type;
            this.algorithm = algorithm;
            this.className = className;
            this.aliases = aliases != null ? new ArrayList<String>(aliases) : new ArrayList<String>();
            this.attributes = attributes != null ? new HashMap<String, String>(attributes) : new HashMap<String, String>();
        }

        void addAttribute(String key, String value) {
            attributes.put(key, value);
        }

        void removeAttribute(String key, String value) {
            attributes.remove(key);
        }

        public final String getType() {
            return type;
        }

        public final String getAlgorithm() {
            return algorithm;
        }

        public final Provider getProvider() {
            return provider;
        }

        public final String getClassName() {
            return className;
        }

        public final String getAttribute(String name) {
            if (name == null) throw new NullPointerException();
            return attributes.get(name);
        }

        public Object newInstance(Object constructorParameter) throws NoSuchAlgorithmException {
            try {
                ClassLoader loader = provider.getClass().getClassLoader();
                Class<?> c = loader != null ? Class.forName(className, true, loader) : Class.forName(className);
                if (constructorParameter != null) {
                    for (java.lang.reflect.Constructor<?> ctor : c.getConstructors()) {
                        Class<?>[] p = ctor.getParameterTypes();
                        if (p.length == 1 && p[0].isInstance(constructorParameter)) {
                            return ctor.newInstance(constructorParameter);
                        }
                    }
                }
                return c.newInstance();
            } catch (Exception e) {
                throw new NoSuchAlgorithmException("Error constructing implementation (algorithm: " + algorithm
                        + ", provider: " + provider.getName() + ", class: " + className + ")", e);
            }
        }

        public boolean supportsParameter(Object parameter) {
            return true;
        }

        public String toString() {
            return provider.getName() + ": " + type + "." + algorithm + " -> " + className
                    + (aliases.isEmpty() ? "" : "\n  aliases: " + aliases)
                    + (attributes.isEmpty() ? "" : "\n  attributes: " + attributes) + "\n";
        }
    }
}
