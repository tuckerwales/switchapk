package java.util;

import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

public interface Comparator<T> {
    int compare(T o1, T o2);

    boolean equals(Object obj);

    default Comparator<T> reversed() {
        return Collections.reverseOrder(this);
    }

    default Comparator<T> thenComparing(final Comparator<? super T> other) {
        final Comparator<T> self = this;
        return new Comparator<T>() {
            public int compare(T c1, T c2) {
                int res = self.compare(c1, c2);
                return (res != 0) ? res : other.compare(c1, c2);
            }
        };
    }

    default <U> Comparator<T> thenComparing(Function<? super T, ? extends U> keyExtractor,
            Comparator<? super U> keyComparator) {
        return thenComparing(comparing(keyExtractor, keyComparator));
    }

    default <U extends Comparable<? super U>> Comparator<T> thenComparing(Function<? super T, ? extends U> keyExtractor) {
        return thenComparing(comparing(keyExtractor));
    }

    default Comparator<T> thenComparingInt(ToIntFunction<? super T> keyExtractor) {
        return thenComparing(comparingInt(keyExtractor));
    }

    default Comparator<T> thenComparingLong(ToLongFunction<? super T> keyExtractor) {
        return thenComparing(comparingLong(keyExtractor));
    }

    default Comparator<T> thenComparingDouble(ToDoubleFunction<? super T> keyExtractor) {
        return thenComparing(comparingDouble(keyExtractor));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <T extends Comparable<? super T>> Comparator<T> reverseOrder() {
        return (Comparator) Collections.REVERSE_ORDER;
    }

    @SuppressWarnings("unchecked")
    static <T extends Comparable<? super T>> Comparator<T> naturalOrder() {
        return (Comparator<T>) Collections.NATURAL_ORDER;
    }

    static <T> Comparator<T> nullsFirst(final Comparator<? super T> comparator) {
        return new Comparator<T>() {
            public int compare(T a, T b) {
                if (a == null) {
                    return (b == null) ? 0 : -1;
                } else if (b == null) {
                    return 1;
                }
                return (comparator == null) ? 0 : comparator.compare(a, b);
            }
        };
    }

    static <T> Comparator<T> nullsLast(final Comparator<? super T> comparator) {
        return new Comparator<T>() {
            public int compare(T a, T b) {
                if (a == null) {
                    return (b == null) ? 0 : 1;
                } else if (b == null) {
                    return -1;
                }
                return (comparator == null) ? 0 : comparator.compare(a, b);
            }
        };
    }

    static <T, U> Comparator<T> comparing(final Function<? super T, ? extends U> keyExtractor,
            final Comparator<? super U> keyComparator) {
        return new Comparator<T>() {
            public int compare(T c1, T c2) {
                return keyComparator.compare(keyExtractor.apply(c1), keyExtractor.apply(c2));
            }
        };
    }

    static <T, U extends Comparable<? super U>> Comparator<T> comparing(final Function<? super T, ? extends U> keyExtractor) {
        return new Comparator<T>() {
            public int compare(T c1, T c2) {
                return keyExtractor.apply(c1).compareTo(keyExtractor.apply(c2));
            }
        };
    }

    static <T> Comparator<T> comparingInt(final ToIntFunction<? super T> keyExtractor) {
        return new Comparator<T>() {
            public int compare(T c1, T c2) {
                return Integer.compare(keyExtractor.applyAsInt(c1), keyExtractor.applyAsInt(c2));
            }
        };
    }

    static <T> Comparator<T> comparingLong(final ToLongFunction<? super T> keyExtractor) {
        return new Comparator<T>() {
            public int compare(T c1, T c2) {
                return Long.compare(keyExtractor.applyAsLong(c1), keyExtractor.applyAsLong(c2));
            }
        };
    }

    static <T> Comparator<T> comparingDouble(final ToDoubleFunction<? super T> keyExtractor) {
        return new Comparator<T>() {
            public int compare(T c1, T c2) {
                return Double.compare(keyExtractor.applyAsDouble(c1), keyExtractor.applyAsDouble(c2));
            }
        };
    }
}
