package java.util.stream;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

public interface Stream<T> extends BaseStream<T, Stream<T>> {
    Stream<T> filter(Predicate<? super T> predicate);

    <R> Stream<R> map(Function<? super T, ? extends R> mapper);

    IntStream mapToInt(ToIntFunction<? super T> mapper);

    LongStream mapToLong(ToLongFunction<? super T> mapper);

    DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper);

    <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper);

    Stream<T> distinct();

    Stream<T> sorted();

    Stream<T> sorted(Comparator<? super T> comparator);

    Stream<T> peek(Consumer<? super T> action);

    Stream<T> limit(long maxSize);

    Stream<T> skip(long n);

    Stream<T> takeWhile(Predicate<? super T> predicate);

    Stream<T> dropWhile(Predicate<? super T> predicate);

    void forEach(Consumer<? super T> action);

    void forEachOrdered(Consumer<? super T> action);

    Object[] toArray();

    <A> A[] toArray(IntFunction<A[]> generator);

    T reduce(T identity, BinaryOperator<T> accumulator);

    Optional<T> reduce(BinaryOperator<T> accumulator);

    <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner);

    <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner);

    <R, A> R collect(Collector<? super T, A, R> collector);

    java.util.List<T> toList();

    Optional<T> min(Comparator<? super T> comparator);

    Optional<T> max(Comparator<? super T> comparator);

    long count();

    boolean anyMatch(Predicate<? super T> predicate);

    boolean allMatch(Predicate<? super T> predicate);

    boolean noneMatch(Predicate<? super T> predicate);

    Optional<T> findFirst();

    Optional<T> findAny();

    static <T> Stream<T> empty() {
        return new IterStream<T>(java.util.Collections.<T>emptyIterator());
    }

    static <T> Stream<T> of(T t) {
        return new IterStream<T>(java.util.Collections.singletonList(t).iterator());
    }

    static <T> Stream<T> ofNullable(T t) {
        return t == null ? Stream.<T>empty() : of(t);
    }

    @SafeVarargs
    static <T> Stream<T> of(T... values) {
        return new IterStream<T>(java.util.Arrays.asList(values).iterator());
    }

    static <T> Stream<T> iterate(final T seed, final UnaryOperator<T> f) {
        return new IterStream<T>(new Iterator<T>() {
            T t = null;
            boolean started;

            public boolean hasNext() {
                return true;
            }

            public T next() {
                t = started ? f.apply(t) : seed;
                started = true;
                return t;
            }
        });
    }

    static <T> Stream<T> iterate(final T seed, final Predicate<? super T> hasNext, final UnaryOperator<T> next) {
        return iterate(seed, next).takeWhile(hasNext);
    }

    static <T> Stream<T> generate(final Supplier<? extends T> s) {
        return new IterStream<T>(new Iterator<T>() {
            public boolean hasNext() {
                return true;
            }

            public T next() {
                return s.get();
            }
        });
    }

    static <T> Stream<T> concat(Stream<? extends T> a, Stream<? extends T> b) {
        final Iterator<? extends T> ia = a.iterator();
        final Iterator<? extends T> ib = b.iterator();
        return new IterStream<T>(new Iterator<T>() {
            public boolean hasNext() {
                return ia.hasNext() || ib.hasNext();
            }

            public T next() {
                return ia.hasNext() ? ia.next() : ib.next();
            }
        });
    }

    interface Builder<T> extends Consumer<T> {
        void accept(T t);

        default Builder<T> add(T t) {
            accept(t);
            return this;
        }

        Stream<T> build();
    }

    static <T> Builder<T> builder() {
        final java.util.ArrayList<T> list = new java.util.ArrayList<T>();
        return new Builder<T>() {
            public void accept(T t) {
                list.add(t);
            }

            public Stream<T> build() {
                return new IterStream<T>(list.iterator());
            }
        };
    }
}
