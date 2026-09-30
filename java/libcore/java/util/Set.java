package java.util;

public interface Set<E> extends Collection<E> {
    @SafeVarargs
    static <E> Set<E> of(E... elements) {
        LinkedHashSet<E> s = new LinkedHashSet<E>();
        for (E e : elements) {
            if (e == null) {
                throw new NullPointerException();
            }
            if (!s.add(e)) {
                throw new IllegalArgumentException("duplicate element: " + e);
            }
        }
        return Collections.unmodifiableSet(s);
    }

    static <E> Set<E> copyOf(Collection<? extends E> coll) {
        return Collections.unmodifiableSet(new LinkedHashSet<E>(coll));
    }
}
