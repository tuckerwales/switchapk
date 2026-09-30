package java.util.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
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

/** Sequential, lazily evaluated stream built on iterators. */
final class IterStream<T> implements Stream<T> {
    private final Iterator<T> it;
    private Runnable closeHandler;

    @SuppressWarnings("unchecked")
    IterStream(Iterator<? extends T> it) {
        this.it = (Iterator<T>) it;
    }

    public Iterator<T> iterator() {
        return it;
    }

    public Spliterator<T> spliterator() {
        return Spliterators.spliteratorUnknownSize(it, 0);
    }

    public boolean isParallel() {
        return false;
    }

    public Stream<T> sequential() {
        return this;
    }

    public Stream<T> parallel() {
        return this;
    }

    public Stream<T> unordered() {
        return this;
    }

    public Stream<T> onClose(Runnable closeHandler) {
        this.closeHandler = closeHandler;
        return this;
    }

    public void close() {
        if (closeHandler != null) {
            closeHandler.run();
        }
    }

    public Stream<T> filter(final Predicate<? super T> predicate) {
        return new IterStream<T>(new Iterator<T>() {
            T next;
            boolean ready;

            public boolean hasNext() {
                while (!ready && it.hasNext()) {
                    T t = it.next();
                    if (predicate.test(t)) {
                        next = t;
                        ready = true;
                    }
                }
                return ready;
            }

            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                ready = false;
                return next;
            }
        });
    }

    public <R> Stream<R> map(final Function<? super T, ? extends R> mapper) {
        return new IterStream<R>(new Iterator<R>() {
            public boolean hasNext() {
                return it.hasNext();
            }

            public R next() {
                return mapper.apply(it.next());
            }
        });
    }

    public IntStream mapToInt(final ToIntFunction<? super T> mapper) {
        return new IntIterStream(new Iterator<Integer>() {
            public boolean hasNext() {
                return it.hasNext();
            }

            public Integer next() {
                return mapper.applyAsInt(it.next());
            }
        });
    }

    public LongStream mapToLong(final ToLongFunction<? super T> mapper) {
        return new LongIterStream(new Iterator<Long>() {
            public boolean hasNext() {
                return it.hasNext();
            }

            public Long next() {
                return mapper.applyAsLong(it.next());
            }
        });
    }

    public DoubleStream mapToDouble(final ToDoubleFunction<? super T> mapper) {
        return new DoubleIterStream(new Iterator<Double>() {
            public boolean hasNext() {
                return it.hasNext();
            }

            public Double next() {
                return mapper.applyAsDouble(it.next());
            }
        });
    }

    public <R> Stream<R> flatMap(final Function<? super T, ? extends Stream<? extends R>> mapper) {
        return new IterStream<R>(new Iterator<R>() {
            Iterator<? extends R> cur;

            public boolean hasNext() {
                while ((cur == null || !cur.hasNext()) && it.hasNext()) {
                    Stream<? extends R> s = mapper.apply(it.next());
                    cur = s == null ? null : s.iterator();
                }
                return cur != null && cur.hasNext();
            }

            public R next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return cur.next();
            }
        });
    }

    private ArrayList<T> drain() {
        ArrayList<T> l = new ArrayList<T>();
        while (it.hasNext()) {
            l.add(it.next());
        }
        return l;
    }

    public Stream<T> distinct() {
        return new IterStream<T>(new LinkedHashSet<T>(drain()).iterator());
    }

    public Stream<T> sorted() {
        return sorted(null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Stream<T> sorted(Comparator<? super T> comparator) {
        ArrayList<T> l = drain();
        Collections.sort(l, (Comparator) comparator);
        return new IterStream<T>(l.iterator());
    }

    public Stream<T> peek(final Consumer<? super T> action) {
        return new IterStream<T>(new Iterator<T>() {
            public boolean hasNext() {
                return it.hasNext();
            }

            public T next() {
                T t = it.next();
                action.accept(t);
                return t;
            }
        });
    }

    public Stream<T> limit(final long maxSize) {
        return new IterStream<T>(new Iterator<T>() {
            long n;

            public boolean hasNext() {
                return n < maxSize && it.hasNext();
            }

            public T next() {
                if (n >= maxSize) {
                    throw new NoSuchElementException();
                }
                n++;
                return it.next();
            }
        });
    }

    public Stream<T> skip(long n) {
        for (long i = 0; i < n && it.hasNext(); i++) {
            it.next();
        }
        return this;
    }

    public Stream<T> takeWhile(final Predicate<? super T> predicate) {
        return new IterStream<T>(new Iterator<T>() {
            T next;
            boolean ready;
            boolean done;

            public boolean hasNext() {
                if (!ready && !done && it.hasNext()) {
                    T t = it.next();
                    if (predicate.test(t)) {
                        next = t;
                        ready = true;
                    } else {
                        done = true;
                    }
                }
                return ready;
            }

            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                ready = false;
                return next;
            }
        });
    }

    public Stream<T> dropWhile(final Predicate<? super T> predicate) {
        ArrayList<T> rest = new ArrayList<T>();
        boolean dropping = true;
        while (it.hasNext()) {
            T t = it.next();
            if (dropping && predicate.test(t)) {
                continue;
            }
            dropping = false;
            rest.add(t);
        }
        return new IterStream<T>(rest.iterator());
    }

    public void forEach(Consumer<? super T> action) {
        while (it.hasNext()) {
            action.accept(it.next());
        }
    }

    public void forEachOrdered(Consumer<? super T> action) {
        forEach(action);
    }

    public Object[] toArray() {
        return drain().toArray();
    }

    public <A> A[] toArray(IntFunction<A[]> generator) {
        ArrayList<T> l = drain();
        return l.toArray(generator.apply(l.size()));
    }

    public T reduce(T identity, BinaryOperator<T> accumulator) {
        T result = identity;
        while (it.hasNext()) {
            result = accumulator.apply(result, it.next());
        }
        return result;
    }

    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        if (!it.hasNext()) {
            return Optional.empty();
        }
        T result = it.next();
        while (it.hasNext()) {
            result = accumulator.apply(result, it.next());
        }
        return Optional.of(result);
    }

    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        U result = identity;
        while (it.hasNext()) {
            result = accumulator.apply(result, it.next());
        }
        return result;
    }

    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
        R r = supplier.get();
        while (it.hasNext()) {
            accumulator.accept(r, it.next());
        }
        return r;
    }

    public <R, A> R collect(Collector<? super T, A, R> collector) {
        A container = collector.supplier().get();
        BiConsumer<A, ? super T> acc = collector.accumulator();
        while (it.hasNext()) {
            @SuppressWarnings("unchecked")
            BiConsumer<A, T> a = (BiConsumer<A, T>) acc;
            a.accept(container, it.next());
        }
        return collector.finisher().apply(container);
    }

    public List<T> toList() {
        return Collections.unmodifiableList(drain());
    }

    public Optional<T> min(Comparator<? super T> comparator) {
        if (!it.hasNext()) {
            return Optional.empty();
        }
        T best = it.next();
        while (it.hasNext()) {
            T t = it.next();
            if (comparator.compare(t, best) < 0) {
                best = t;
            }
        }
        return Optional.of(best);
    }

    public Optional<T> max(Comparator<? super T> comparator) {
        if (!it.hasNext()) {
            return Optional.empty();
        }
        T best = it.next();
        while (it.hasNext()) {
            T t = it.next();
            if (comparator.compare(t, best) > 0) {
                best = t;
            }
        }
        return Optional.of(best);
    }

    public long count() {
        long n = 0;
        while (it.hasNext()) {
            it.next();
            n++;
        }
        return n;
    }

    public boolean anyMatch(Predicate<? super T> predicate) {
        while (it.hasNext()) {
            if (predicate.test(it.next())) {
                return true;
            }
        }
        return false;
    }

    public boolean allMatch(Predicate<? super T> predicate) {
        while (it.hasNext()) {
            if (!predicate.test(it.next())) {
                return false;
            }
        }
        return true;
    }

    public boolean noneMatch(Predicate<? super T> predicate) {
        return !anyMatch(predicate);
    }

    public Optional<T> findFirst() {
        return it.hasNext() ? Optional.ofNullable(it.next()) : Optional.<T>empty();
    }

    public Optional<T> findAny() {
        return findFirst();
    }
}
