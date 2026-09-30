package java.util;

public final class ServiceLoader<S> implements Iterable<S> {
    private ServiceLoader() {
    }

    public static <S> ServiceLoader<S> load(Class<S> service) {
        return new ServiceLoader<S>();
    }

    public static <S> ServiceLoader<S> load(Class<S> service, ClassLoader loader) {
        return new ServiceLoader<S>();
    }

    public Iterator<S> iterator() {
        return Collections.<S>emptyIterator();
    }

    public void reload() {
    }
}
