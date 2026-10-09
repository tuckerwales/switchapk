package java.util;

import java.util.function.UnaryOperator;

public interface List<E> extends Collection<E> {
    E get(int index);

    E set(int index, E element);

    void add(int index, E element);

    E remove(int index);

    int indexOf(Object o);

    int lastIndexOf(Object o);

    ListIterator<E> listIterator();

    ListIterator<E> listIterator(int index);

    List<E> subList(int fromIndex, int toIndex);

    boolean addAll(int index, Collection<? extends E> c);

    default void replaceAll(UnaryOperator<E> operator) {
        ListIterator<E> li = listIterator();
        while (li.hasNext()) {
            li.set(operator.apply(li.next()));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    default void sort(Comparator<? super E> c) {
        Object[] a = toArray();
        Arrays.sort(a, (Comparator) c);
        ListIterator<E> i = listIterator();
        for (Object e : a) {
            i.next();
            i.set((E) e);
        }
    }

    @SuppressWarnings("unchecked")
    static <E> List<E> of() {
        return ofArray(new Object[0]);
    }

    @SuppressWarnings("unchecked")
    static <E> List<E> of(E e1) {
        return ofArray(new Object[] {e1});
    }

    @SuppressWarnings("unchecked")
    static <E> List<E> of(E e1, E e2) {
        return ofArray(new Object[] {e1, e2});
    }

    @SuppressWarnings("unchecked")
    static <E> List<E> of(E e1, E e2, E e3) {
        return ofArray(new Object[] {e1, e2, e3});
    }

    @SuppressWarnings("unchecked")
    static <E> List<E> of(E e1, E e2, E e3, E e4) {
        return ofArray(new Object[] {e1, e2, e3, e4});
    }

    @SuppressWarnings("unchecked")
    static <E> List<E> of(E e1, E e2, E e3, E e4, E e5) {
        return ofArray(new Object[] {e1, e2, e3, e4, e5});
    }

    @SuppressWarnings("unchecked")
    static <E> List<E> of(E e1, E e2, E e3, E e4, E e5, E e6) {
        return ofArray(new Object[] {e1, e2, e3, e4, e5, e6});
    }

    @SuppressWarnings("unchecked")
    static <E> List<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
        return ofArray(new Object[] {e1, e2, e3, e4, e5, e6, e7});
    }

    @SuppressWarnings("unchecked")
    static <E> List<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
        return ofArray(new Object[] {e1, e2, e3, e4, e5, e6, e7, e8});
    }

    @SuppressWarnings("unchecked")
    static <E> List<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) {
        return ofArray(new Object[] {e1, e2, e3, e4, e5, e6, e7, e8, e9});
    }

    @SuppressWarnings("unchecked")
    static <E> List<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10) {
        return ofArray(new Object[] {e1, e2, e3, e4, e5, e6, e7, e8, e9, e10});
    }

    @SafeVarargs
    static <E> List<E> of(E... elements) {
        return ofArray(elements.clone());
    }

    /* Framework-internal: an unmodifiable list over a private array, rejecting nulls like List.of. */
    @SuppressWarnings("unchecked")
    static <E> List<E> ofArray(Object[] a) {
        for (Object e : a) {
            if (e == null) {
                throw new NullPointerException();
            }
        }
        return Collections.unmodifiableList((List<E>) Arrays.asList(a));
    }

    static <E> List<E> copyOf(Collection<? extends E> coll) {
        return ofArray(coll.toArray());
    }
}
