package java.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Service providers listed in META-INF/services/<service binary name> of the APK (the class
 * loader's resources): one class name per line, '#' starts a comment. Providers are created
 * lazily, in file order, without duplicates, through their public no-argument constructor.
 */
public final class ServiceLoader<S> implements Iterable<S> {
    private static final String PREFIX = "META-INF/services/";

    private final Class<S> service;
    private final ClassLoader loader;
    private final ArrayList<S> providers = new ArrayList<S>();
    private List<String> pending;

    private ServiceLoader(Class<S> service, ClassLoader loader) {
        if (service == null) throw new NullPointerException("service == null");
        this.service = service;
        this.loader = loader != null ? loader : ClassLoader.getSystemClassLoader();
        reload();
    }

    public static <S> ServiceLoader<S> load(Class<S> service, ClassLoader loader) {
        return new ServiceLoader<S>(service, loader);
    }

    public static <S> ServiceLoader<S> load(Class<S> service) {
        return new ServiceLoader<S>(service, Thread.currentThread().getContextClassLoader());
    }

    public static <S> ServiceLoader<S> loadInstalled(Class<S> service) {
        return new ServiceLoader<S>(service, ClassLoader.getSystemClassLoader());
    }

    public void reload() {
        synchronized (providers) {
            providers.clear();
            pending = names();
        }
    }

    private List<String> names() {
        ArrayList<String> out = new ArrayList<String>();
        InputStream in = loader.getResourceAsStream(PREFIX + service.getName());
        if (in == null) return out;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            int lineNo = 0;
            while ((line = r.readLine()) != null) {
                lineNo++;
                int hash = line.indexOf('#');
                if (hash >= 0) line = line.substring(0, hash);
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.indexOf(' ') >= 0 || line.indexOf('\t') >= 0) {
                    throw new ServiceConfigurationError(service.getName() + ": " + PREFIX + service.getName() + ":"
                            + lineNo + ": Illegal configuration-file syntax");
                }
                if (!out.contains(line)) out.add(line);
            }
        } catch (IOException e) {
            throw new ServiceConfigurationError(service.getName() + ": Error reading configuration file", e);
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
        return out;
    }

    private S create(String name) {
        Class<?> c;
        try {
            c = Class.forName(name, false, loader);
        } catch (ClassNotFoundException e) {
            throw new ServiceConfigurationError(service.getName() + ": Provider " + name + " not found", e);
        }
        if (!service.isAssignableFrom(c)) {
            throw new ServiceConfigurationError(service.getName() + ": Provider " + name + " not a subtype");
        }
        try {
            return service.cast(c.newInstance());
        } catch (Throwable t) {
            throw new ServiceConfigurationError(service.getName() + ": Provider " + name
                    + " could not be instantiated", t);
        }
    }

    public Iterator<S> iterator() {
        return new Iterator<S>() {
            private int index;

            public boolean hasNext() {
                synchronized (providers) {
                    return index < providers.size() + pending.size();
                }
            }

            public S next() {
                String name;
                synchronized (providers) {
                    if (index < providers.size()) return providers.get(index++);
                    if (pending.isEmpty()) throw new NoSuchElementException();
                    name = pending.remove(0);
                }
                S s = create(name);
                synchronized (providers) {
                    providers.add(s);
                    index++;
                }
                return s;
            }

            public void remove() { throw new UnsupportedOperationException(); }
        };
    }

    /** A provider class found in the configuration files; get() creates (once) and returns its instance. */
    public interface Provider<S> extends java.util.function.Supplier<S> {
        Class<? extends S> type();

        S get();
    }

    public java.util.stream.Stream<Provider<S>> stream() {
        List<String> names;
        synchronized (providers) {
            names = names();
        }
        ArrayList<Provider<S>> list = new ArrayList<Provider<S>>();
        for (final String name : names) {
            list.add(new Provider<S>() {
                private S instance;

                @SuppressWarnings("unchecked")
                public Class<? extends S> type() {
                    try {
                        return (Class<? extends S>) Class.forName(name, false, loader);
                    } catch (ClassNotFoundException e) {
                        throw new ServiceConfigurationError(service.getName() + ": Provider " + name + " not found", e);
                    }
                }

                public synchronized S get() {
                    if (instance == null) instance = create(name);
                    return instance;
                }
            });
        }
        return list.stream();
    }

    public Optional<S> findFirst() {
        Iterator<S> it = iterator();
        return it.hasNext() ? Optional.of(it.next()) : Optional.<S>empty();
    }

    public String toString() { return "java.util.ServiceLoader[" + service.getName() + "]"; }
}
