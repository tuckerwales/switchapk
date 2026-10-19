package java.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Locale-specific resources. getBundle walks the candidate locales of the requested locale (language_country_variant
 * down to the base name), loading each as a ResourceBundle class or a .properties resource through the class loader,
 * chains them child to parent, and falls back to the default locale when only the base bundle exists, as the JDK.
 */
public abstract class ResourceBundle {
    protected ResourceBundle parent = null;
    private Locale locale = null;
    private String name;

    private static final Map<String, ResourceBundle> cache = new HashMap<String, ResourceBundle>();
    private static final Object NONE_MARKER = new Object();

    public ResourceBundle() {
    }

    public String getBaseBundleName() {
        return name;
    }

    public final String getString(String key) {
        return (String) getObject(key);
    }

    public final String[] getStringArray(String key) {
        return (String[]) getObject(key);
    }

    public final Object getObject(String key) {
        Object obj = handleGetObject(key);
        if (obj == null) {
            if (parent != null) {
                obj = parent.getObject(key);
            }
            if (obj == null) {
                throw new MissingResourceException("Can't find resource for bundle " + getClass().getName() + ", key "
                        + key, getClass().getName(), key);
            }
        }
        return obj;
    }

    public Locale getLocale() {
        return locale;
    }

    protected void setParent(ResourceBundle parent) {
        this.parent = parent;
    }

    private static ClassLoader defaultLoader() {
        ClassLoader l = Thread.currentThread().getContextClassLoader();
        return l != null ? l : ClassLoader.getSystemClassLoader();
    }

    public static final ResourceBundle getBundle(String baseName) {
        return getBundleImpl(baseName, Locale.getDefault(), defaultLoader(), Control.INSTANCE);
    }

    public static final ResourceBundle getBundle(String baseName, Control control) {
        return getBundleImpl(baseName, Locale.getDefault(), defaultLoader(), control);
    }

    public static final ResourceBundle getBundle(String baseName, Locale locale) {
        return getBundleImpl(baseName, locale, defaultLoader(), Control.INSTANCE);
    }

    public static final ResourceBundle getBundle(String baseName, Locale targetLocale, Control control) {
        return getBundleImpl(baseName, targetLocale, defaultLoader(), control);
    }

    public static ResourceBundle getBundle(String baseName, Locale locale, ClassLoader loader) {
        if (loader == null) {
            throw new NullPointerException();
        }
        return getBundleImpl(baseName, locale, loader, Control.INSTANCE);
    }

    public static ResourceBundle getBundle(String baseName, Locale targetLocale, ClassLoader loader, Control control) {
        if (loader == null || control == null) {
            throw new NullPointerException();
        }
        return getBundleImpl(baseName, targetLocale, loader, control);
    }

    private static ResourceBundle getBundleImpl(String baseName, Locale locale, ClassLoader loader, Control control) {
        if (baseName == null || locale == null || control == null) {
            throw new NullPointerException();
        }
        ResourceBundle bundle = findChain(baseName, locale, loader, control);
        if (bundle == null || bundle.locale.equals(Locale.ROOT)) {
            // nothing more specific than the base bundle: the fallback (default) locale may have something
            Locale fallback = control.getFallbackLocale(baseName, locale);
            if (fallback != null && !fallback.equals(locale)) {
                ResourceBundle other = findChain(baseName, fallback, loader, control);
                if (other != null && (bundle == null || !other.locale.equals(Locale.ROOT))) {
                    bundle = other;
                }
            }
        }
        if (bundle == null) {
            throw new MissingResourceException("Can't find bundle for base name " + baseName + ", locale " + locale,
                    baseName + "_" + locale, "");
        }
        return bundle;
    }

    /** The chain for one locale's candidates, most specific first; null if none of them exists. */
    private static ResourceBundle findChain(String baseName, Locale locale, ClassLoader loader, Control control) {
        List<Locale> candidates = control.getCandidateLocales(baseName, locale);
        ResourceBundle child = null, first = null;
        for (Locale candidate : candidates) {
            ResourceBundle b = load(baseName, candidate, loader, control);
            if (b == null) {
                continue;
            }
            if (child != null && child.parent == null) {
                child.setParent(b);
            }
            if (first == null) {
                first = b;
            }
            child = b;
        }
        return first;
    }

    private static ResourceBundle load(String baseName, Locale locale, ClassLoader loader, Control control) {
        String key = baseName + '\u0000' + locale + '\u0000' + System.identityHashCode(loader) + '\u0000'
                + System.identityHashCode(control);
        synchronized (cache) {
            Object cached = cache.get(key);
            if (cached != null) {
                return cached == NONE_MARKER ? null : (ResourceBundle) cached;
            }
        }
        ResourceBundle bundle = null;
        for (String format : control.getFormats(baseName)) {
            try {
                bundle = control.newBundle(baseName, locale, format, loader, false);
            } catch (Exception e) {
                bundle = null;
            }
            if (bundle != null) {
                bundle.name = baseName;
                bundle.locale = locale;
                break;
            }
        }
        synchronized (cache) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) (Map<String, ?>) cache;
            m.put(key, bundle == null ? NONE_MARKER : bundle);
        }
        return bundle;
    }

    public static final void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }

    public static final void clearCache(ClassLoader loader) {
        if (loader == null) {
            throw new NullPointerException();
        }
        clearCache();
    }

    protected abstract Object handleGetObject(String key);

    public abstract Enumeration<String> getKeys();

    public boolean containsKey(String key) {
        if (key == null) {
            throw new NullPointerException();
        }
        for (ResourceBundle rb = this; rb != null; rb = rb.parent) {
            if (rb.handleKeySet().contains(key)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> keySet() {
        Set<String> keys = new HashSet<String>();
        for (ResourceBundle rb = this; rb != null; rb = rb.parent) {
            keys.addAll(rb.handleKeySet());
        }
        return keys;
    }

    protected Set<String> handleKeySet() {
        Set<String> keys = new HashSet<String>();
        for (Enumeration<String> e = getKeys(); e.hasMoreElements();) {
            String key = e.nextElement();
            if (handleGetObject(key) != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** Own keys, then the parent's that are not shadowed. */
    static Enumeration<String> keys(final Set<String> own, final ResourceBundle parent) {
        LinkedHashSet<String> all = new LinkedHashSet<String>(own);
        if (parent != null) {
            for (Enumeration<String> e = parent.getKeys(); e.hasMoreElements();) {
                all.add(e.nextElement());
            }
        }
        return Collections.enumeration(all);
    }

    public static class Control {
        public static final List<String> FORMAT_DEFAULT =
                Collections.unmodifiableList(Arrays.asList("java.class", "java.properties"));
        public static final List<String> FORMAT_CLASS = Collections.unmodifiableList(Arrays.asList("java.class"));
        public static final List<String> FORMAT_PROPERTIES =
                Collections.unmodifiableList(Arrays.asList("java.properties"));
        public static final long TTL_DONT_CACHE = -1;
        public static final long TTL_NO_EXPIRATION_CONTROL = -2;

        static final Control INSTANCE = new Control();
        private static final Control CLASS_ONLY = new SingleFormat(FORMAT_CLASS, true);
        private static final Control PROPERTIES_ONLY = new SingleFormat(FORMAT_PROPERTIES, true);
        private static final Control NO_FALLBACK_DEFAULT = new SingleFormat(FORMAT_DEFAULT, false);
        private static final Control NO_FALLBACK_CLASS = new SingleFormat(FORMAT_CLASS, false);
        private static final Control NO_FALLBACK_PROPERTIES = new SingleFormat(FORMAT_PROPERTIES, false);

        protected Control() {
        }

        private static final class SingleFormat extends Control {
            private final List<String> formats;
            private final boolean fallback;

            SingleFormat(List<String> formats, boolean fallback) {
                this.formats = formats;
                this.fallback = fallback;
            }

            public List<String> getFormats(String baseName) {
                if (baseName == null) {
                    throw new NullPointerException();
                }
                return formats;
            }

            public Locale getFallbackLocale(String baseName, Locale locale) {
                return fallback ? super.getFallbackLocale(baseName, locale) : null;
            }
        }

        public static final Control getControl(List<String> formats) {
            if (formats.equals(FORMAT_PROPERTIES)) {
                return PROPERTIES_ONLY;
            } else if (formats.equals(FORMAT_CLASS)) {
                return CLASS_ONLY;
            } else if (formats.equals(FORMAT_DEFAULT)) {
                return INSTANCE;
            }
            throw new IllegalArgumentException();
        }

        public static final Control getNoFallbackControl(List<String> formats) {
            if (formats.equals(FORMAT_DEFAULT)) {
                return NO_FALLBACK_DEFAULT;
            } else if (formats.equals(FORMAT_PROPERTIES)) {
                return NO_FALLBACK_PROPERTIES;
            } else if (formats.equals(FORMAT_CLASS)) {
                return NO_FALLBACK_CLASS;
            }
            throw new IllegalArgumentException();
        }

        public List<String> getFormats(String baseName) {
            if (baseName == null) {
                throw new NullPointerException();
            }
            return FORMAT_DEFAULT;
        }

        public List<Locale> getCandidateLocales(String baseName, Locale locale) {
            if (baseName == null) {
                throw new NullPointerException();
            }
            String language = locale.getLanguage(), script = locale.getScript();
            String country = locale.getCountry(), variant = locale.getVariant();
            ArrayList<Locale> list = new ArrayList<Locale>(5);
            if (!variant.isEmpty()) {
                list.add(new Locale(language, country, variant));
            }
            if (!country.isEmpty()) {
                list.add(new Locale(language, country));
            }
            if (!language.isEmpty()) {
                list.add(new Locale(language));
            }
            if (!script.isEmpty() && list.isEmpty()) {
                list.add(locale);
            }
            list.add(Locale.ROOT);
            return list;
        }

        public Locale getFallbackLocale(String baseName, Locale locale) {
            if (baseName == null) {
                throw new NullPointerException();
            }
            Locale defaultLocale = Locale.getDefault();
            return locale.equals(defaultLocale) ? null : defaultLocale;
        }

        public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader,
                                        boolean reload) throws IllegalAccessException, InstantiationException,
                IOException {
            String bundleName = toBundleName(baseName, locale);
            if (format.equals("java.class")) {
                try {
                    Class<?> c = Class.forName(bundleName, false, loader);
                    if (!ResourceBundle.class.isAssignableFrom(c)) {
                        throw new ClassCastException(c.getName() + " cannot be cast to ResourceBundle");
                    }
                    return (ResourceBundle) c.newInstance();
                } catch (ClassNotFoundException e) {
                    return null;
                }
            } else if (format.equals("java.properties")) {
                String resourceName = toResourceName(bundleName, "properties");
                InputStream stream = loader.getResourceAsStream(resourceName);
                if (stream == null) {
                    return null;
                }
                try {
                    return new PropertyResourceBundle(stream);
                } finally {
                    stream.close();
                }
            }
            throw new IllegalArgumentException("unknown format: " + format);
        }

        public long getTimeToLive(String baseName, Locale locale) {
            if (baseName == null || locale == null) {
                throw new NullPointerException();
            }
            return TTL_NO_EXPIRATION_CONTROL;
        }

        public boolean needsReload(String baseName, Locale locale, String format, ClassLoader loader,
                                   ResourceBundle bundle, long loadTime) {
            return false;
        }

        public String toBundleName(String baseName, Locale locale) {
            if (locale.equals(Locale.ROOT)) {
                return baseName;
            }
            String language = locale.getLanguage(), script = locale.getScript();
            String country = locale.getCountry(), variant = locale.getVariant();
            if (language.isEmpty() && country.isEmpty() && variant.isEmpty()) {
                return baseName;
            }
            StringBuilder sb = new StringBuilder(baseName).append('_');
            if (!script.isEmpty()) {
                if (!variant.isEmpty()) {
                    sb.append(language).append('_').append(script).append('_').append(country).append('_').append(variant);
                } else if (!country.isEmpty()) {
                    sb.append(language).append('_').append(script).append('_').append(country);
                } else {
                    sb.append(language).append('_').append(script);
                }
            } else {
                if (!variant.isEmpty()) {
                    sb.append(language).append('_').append(country).append('_').append(variant);
                } else if (!country.isEmpty()) {
                    sb.append(language).append('_').append(country);
                } else {
                    sb.append(language);
                }
            }
            return sb.toString();
        }

        public final String toResourceName(String bundleName, String suffix) {
            StringBuilder sb = new StringBuilder(bundleName.length() + 1 + suffix.length());
            sb.append(bundleName.replace('.', '/')).append('.').append(suffix);
            return sb.toString();
        }
    }
}
