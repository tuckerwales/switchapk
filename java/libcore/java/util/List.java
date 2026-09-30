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

    @SafeVarargs
    @SuppressWarnings("unchecked")
    static <E> List<E> of(E... elements) {
        for (E e : elements) {
            if (e == null) {
                throw new NullPointerException();
            }
        }
        return Collections.unmodifiableList(new ArrayList<E>(Arrays.asList(elements)));
    }

    static <E> List<E> copyOf(Collection<? extends E> coll) {
        return Collections.unmodifiableList(new ArrayList<E>(coll));
    }
}
