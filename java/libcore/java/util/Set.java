package java.util;

public interface Set<E> extends Collection<E> {
    @SuppressWarnings("unchecked")
    static <E> Set<E> of() {
        return ofArray(new Object[0]);
    }

    @SuppressWarnings("unchecked")
    static <E> Set<E> of(E e1) {
        return ofArray(new Object[] {e1});
    }

    @SuppressWarnings("unchecked")
    static <E> Set<E> of(E e1, E e2) {
        return ofArray(new Object[] {e1, e2});
    }

    @SuppressWarnings("unchecked")
    static <E> Set<E> of(E e1, E e2, E e3) {
        return ofArray(new Object[] {e1, e2, e3});
    }

    @SuppressWarnings("unchecked")
    static <E> Set<E> of(E e1, E e2, E e3, E e4) {
        return ofArray(new Object[] {e1, e2, e3, e4});
    }

    @SuppressWarnings("unchecked")
    static <E> Set<E> of(E e1, E e2, E e3, E e4, E e5) {
        return ofArray(new Object[] {e1, e2, e3, e4, e5});
    }

    @SuppressWarnings("unchecked")
    static <E> Set<E> of(E e1, E e2, E e3, E e4, E e5, E e6) {
        return ofArray(new Object[] {e1, e2, e3, e4, e5, e6});
    }

    @SuppressWarnings("unchecked")
    static <E> Set<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
        return ofArray(new Object[] {e1, e2, e3, e4, e5, e6, e7});
    }

    @SuppressWarnings("unchecked")
    static <E> Set<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
        return ofArray(new Object[] {e1, e2, e3, e4, e5, e6, e7, e8});
    }

    @SuppressWarnings("unchecked")
    static <E> Set<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) {
        return ofArray(new Object[] {e1, e2, e3, e4, e5, e6, e7, e8, e9});
    }

    @SuppressWarnings("unchecked")
    static <E> Set<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10) {
        return ofArray(new Object[] {e1, e2, e3, e4, e5, e6, e7, e8, e9, e10});
    }

    @SafeVarargs
    static <E> Set<E> of(E... elements) {
        return ofArray(elements);
    }

    /* Framework-internal: an unmodifiable set rejecting nulls and duplicates like Set.of. */
    @SuppressWarnings("unchecked")
    static <E> Set<E> ofArray(Object[] elements) {
        LinkedHashSet<E> s = new LinkedHashSet<E>();
        for (Object e : elements) {
            if (e == null) {
                throw new NullPointerException();
            }
            if (!s.add((E) e)) {
                throw new IllegalArgumentException("duplicate element: " + e);
            }
        }
        return Collections.unmodifiableSet(s);
    }

    static <E> Set<E> copyOf(Collection<? extends E> coll) {
        LinkedHashSet<E> s = new LinkedHashSet<E>();
        for (E e : coll) {
            s.add(java.util.Objects.requireNonNull(e));
        }
        return Collections.unmodifiableSet(s);
    }
}
