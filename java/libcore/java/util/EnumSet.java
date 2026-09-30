package java.util;

public abstract class EnumSet<E extends Enum<E>> extends AbstractSet<E> implements Cloneable, java.io.Serializable {
    final Class<E> elementType;
    final E[] universe;

    EnumSet(Class<E> elementType, E[] universe) {
        this.elementType = elementType;
        this.universe = universe;
    }

    public static <E extends Enum<E>> EnumSet<E> noneOf(Class<E> elementType) {
        E[] universe = elementType.getEnumConstants();
        if (universe == null) {
            throw new ClassCastException(elementType + " not an enum");
        }
        return new BitEnumSet<E>(elementType, universe);
    }

    public static <E extends Enum<E>> EnumSet<E> allOf(Class<E> elementType) {
        EnumSet<E> result = noneOf(elementType);
        result.addAll(Arrays.asList(result.universe));
        return result;
    }

    public static <E extends Enum<E>> EnumSet<E> copyOf(EnumSet<E> s) {
        return s.clone();
    }

    public static <E extends Enum<E>> EnumSet<E> copyOf(Collection<E> c) {
        if (c instanceof EnumSet) {
            return ((EnumSet<E>) c).clone();
        }
        if (c.isEmpty()) {
            throw new IllegalArgumentException("Collection is empty");
        }
        Iterator<E> i = c.iterator();
        E first = i.next();
        EnumSet<E> result = EnumSet.of(first);
        while (i.hasNext()) {
            result.add(i.next());
        }
        return result;
    }

    public static <E extends Enum<E>> EnumSet<E> complementOf(EnumSet<E> s) {
        EnumSet<E> result = noneOf(s.elementType);
        for (E e : s.universe) {
            if (!s.contains(e)) {
                result.add(e);
            }
        }
        return result;
    }

    public static <E extends Enum<E>> EnumSet<E> of(E e) {
        EnumSet<E> result = noneOf(e.getDeclaringClass());
        result.add(e);
        return result;
    }

    @SafeVarargs
    public static <E extends Enum<E>> EnumSet<E> of(E first, E... rest) {
        EnumSet<E> result = noneOf(first.getDeclaringClass());
        result.add(first);
        for (E e : rest) {
            result.add(e);
        }
        return result;
    }

    public static <E extends Enum<E>> EnumSet<E> of(E e1, E e2) {
        EnumSet<E> result = of(e1);
        result.add(e2);
        return result;
    }

    public static <E extends Enum<E>> EnumSet<E> of(E e1, E e2, E e3) {
        EnumSet<E> result = of(e1, e2);
        result.add(e3);
        return result;
    }

    public static <E extends Enum<E>> EnumSet<E> of(E e1, E e2, E e3, E e4) {
        EnumSet<E> result = of(e1, e2, e3);
        result.add(e4);
        return result;
    }

    public static <E extends Enum<E>> EnumSet<E> of(E e1, E e2, E e3, E e4, E e5) {
        EnumSet<E> result = of(e1, e2, e3, e4);
        result.add(e5);
        return result;
    }

    public static <E extends Enum<E>> EnumSet<E> range(E from, E to) {
        EnumSet<E> result = noneOf(from.getDeclaringClass());
        for (int i = from.ordinal(); i <= to.ordinal(); i++) {
            result.add(result.universe[i]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public EnumSet<E> clone() {
        EnumSet<E> r = noneOf(elementType);
        r.addAll(this);
        return r;
    }

    static final class BitEnumSet<E extends Enum<E>> extends EnumSet<E> {
        private long[] bits;
        private int size;

        BitEnumSet(Class<E> elementType, E[] universe) {
            super(elementType, universe);
            bits = new long[(universe.length + 63) >>> 6];
        }

        public Iterator<E> iterator() {
            return new Iterator<E>() {
                int next = advance(0);
                int last = -1;

                int advance(int i) {
                    while (i < universe.length && (bits[i >>> 6] & (1L << i)) == 0) {
                        i++;
                    }
                    return i;
                }

                public boolean hasNext() {
                    return next < universe.length;
                }

                public E next() {
                    if (next >= universe.length) {
                        throw new NoSuchElementException();
                    }
                    last = next;
                    next = advance(next + 1);
                    return universe[last];
                }

                public void remove() {
                    if (last < 0) {
                        throw new IllegalStateException();
                    }
                    BitEnumSet.this.remove(universe[last]);
                    last = -1;
                }
            };
        }

        public int size() {
            return size;
        }

        public boolean contains(Object o) {
            if (o == null || !elementType.isInstance(o)) {
                return false;
            }
            int i = ((Enum<?>) o).ordinal();
            return (bits[i >>> 6] & (1L << i)) != 0;
        }

        public boolean add(E e) {
            if (!elementType.isInstance(e)) {
                throw new ClassCastException(String.valueOf(e));
            }
            int i = e.ordinal();
            long old = bits[i >>> 6];
            bits[i >>> 6] |= 1L << i;
            if (old != bits[i >>> 6]) {
                size++;
                return true;
            }
            return false;
        }

        public boolean remove(Object o) {
            if (!contains(o)) {
                return false;
            }
            int i = ((Enum<?>) o).ordinal();
            bits[i >>> 6] &= ~(1L << i);
            size--;
            return true;
        }

        public void clear() {
            Arrays.fill(bits, 0);
            size = 0;
        }
    }
}
