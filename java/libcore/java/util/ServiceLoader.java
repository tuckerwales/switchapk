package java.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Providers listed in META-INF/services/&lt;service name&gt; (read from the APK's Java resources), instantiated
 * lazily with their public no-argument constructors, in file order and without duplicates.
 */
public final class ServiceLoader<S> implements Iterable<S> {
    public interface Provider<S> extends Supplier<S> {
        Class<? extends S> type();

        S get();
    }

    private final Class<S> service;
    private final ClassLoader loader;
    private List<String> names;
    private final LinkedHashMap<String, S> providers = new LinkedHashMap<String, S>();

    private ServiceLoader(Class<S> service, ClassLoader loader) {
        this.service = Objects.requireNonNull(service, "service interface cannot be null");
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
        providers.clear();
        names = readNames();
    }

    private List<String> readNames() {
        ArrayList<String> out = new ArrayList<String>();
        String resource = "META-INF/services/" + service.getName();
        InputStream in = loader.getResourceAsStream(resource);
        if (in == null) {
            return out;
        }
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            int lineNumber = 0;
            String line;
            while ((line = r.readLine()) != null) {
                lineNumber++;
                int hash = line.indexOf('#');
                if (hash >= 0) {
                    line = line.substring(0, hash);
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.indexOf(' ') >= 0 || line.indexOf('\t') >= 0) {
                    throw new ServiceConfigurationError(service.getName() + ": " + resource + ":" + lineNumber
                            + ": Illegal configuration-file syntax");
                }
                if (!out.contains(line)) {
                    out.add(line);
                }
            }
        } catch (IOException e) {
            throw new ServiceConfigurationError(service.getName() + ": Error reading configuration file", e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
        return out;
    }

    private Class<? extends S> providerClass(String name) {
        Class<?> c;
        try {
            c = Class.forName(name, false, loader);
        } catch (ClassNotFoundException e) {
            throw new ServiceConfigurationError(service.getName() + ": Provider " + name + " not found", e);
        }
        if (!service.isAssignableFrom(c)) {
            throw new ServiceConfigurationError(service.getName() + ": Provider " + name + " not a subtype");
        }
        return c.asSubclass(service);
    }

    private S instantiate(String name) {
        synchronized (providers) {
            S cached = providers.get(name);
            if (cached != null) {
                return cached;
            }
        }
        Class<? extends S> c = providerClass(name);
        S p;
        try {
            p = service.cast(c.getDeclaredConstructor().newInstance());
        } catch (Throwable x) {
            throw new ServiceConfigurationError(service.getName() + ": Provider " + name + " could not be instantiated",
                    x);
        }
        synchronized (providers) {
            providers.put(name, p);
        }
        return p;
    }

    public Iterator<S> iterator() {
        final List<String> snapshot = names;
        return new Iterator<S>() {
            private int index;

            public boolean hasNext() {
                return index < snapshot.size();
            }

            public S next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return instantiate(snapshot.get(index++));
            }
        };
    }

    public Stream<Provider<S>> stream() {
        ArrayList<Provider<S>> list = new ArrayList<Provider<S>>();
        for (final String name : names) {
            final Class<? extends S> type = providerClass(name);
            list.add(new Provider<S>() {
                public Class<? extends S> type() {
                    return type;
                }

                public S get() {
                    return instantiate(name);
                }
            });
        }
        return list.stream();
    }

    public Optional<S> findFirst() {
        Iterator<S> it = iterator();
        return it.hasNext() ? Optional.of(it.next()) : Optional.<S>empty();
    }

    public String toString() {
        return "java.util.ServiceLoader[" + service.getName() + "]";
    }
}
