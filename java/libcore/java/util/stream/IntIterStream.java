package java.util.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.OptionalInt;
import java.util.OptionalDouble;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.IntSummaryStatistics;
import java.util.function.*;

final class IntIterStream implements IntStream {
    private final Iterator<Integer> it;

    @SuppressWarnings("unchecked")
    IntIterStream(Iterator<? extends Integer> it) {
        this.it = (Iterator<Integer>) it;
    }

    public PrimitiveIterator.OfInt iterator() {
        return new PrimitiveIterator.OfInt() {
            public boolean hasNext() {
                return it.hasNext();
            }

            public int nextInt() {
                return it.next();
            }
        };
    }

    @SuppressWarnings("unchecked")
    public Spliterator<Integer> spliterator() {
        return Spliterators.spliteratorUnknownSize(it, 0);
    }

    public boolean isParallel() {
        return false;
    }

    public IntStream sequential() {
        return this;
    }

    public IntStream parallel() {
        return this;
    }

    public IntStream unordered() {
        return this;
    }

    public IntStream onClose(Runnable closeHandler) {
        return this;
    }

    public void close() {
    }

    private Stream<Integer> s() {
        return new IterStream<Integer>(it);
    }

    public Stream<Integer> boxed() {
        return s();
    }

    public IntStream filter(final IntPredicate predicate) {
        return new IntIterStream(s().filter(new Predicate<Integer>() {
            public boolean test(Integer v) {
                return predicate.test(v);
            }
        }).iterator());
    }

    public IntStream map(final IntUnaryOperator mapper) {
        return new IntIterStream(s().map(new Function<Integer, Integer>() {
            public Integer apply(Integer v) {
                return mapper.applyAsInt(v);
            }
        }).iterator());
    }

    public <U> Stream<U> mapToObj(final IntFunction<? extends U> mapper) {
        return s().map(new Function<Integer, U>() {
            public U apply(Integer v) {
                return mapper.apply(v);
            }
        });
    }

    public IntStream flatMap(final IntFunction<? extends IntStream> mapper) {
        return new IntIterStream(s().flatMap(new Function<Integer, Stream<Integer>>() {
            public Stream<Integer> apply(Integer v) {
                return mapper.apply(v).boxed();
            }
        }).iterator());
    }

    public IntStream distinct() {
        return new IntIterStream(s().distinct().iterator());
    }

    public IntStream sorted() {
        return new IntIterStream(s().sorted().iterator());
    }

    public IntStream peek(final IntConsumer action) {
        return new IntIterStream(s().peek(new Consumer<Integer>() {
            public void accept(Integer v) {
                action.accept(v);
            }
        }).iterator());
    }

    public IntStream limit(long maxSize) {
        return new IntIterStream(s().limit(maxSize).iterator());
    }

    public IntStream skip(long n) {
        return new IntIterStream(s().skip(n).iterator());
    }

    public void forEach(IntConsumer action) {
        while (it.hasNext()) {
            action.accept(it.next());
        }
    }

    public void forEachOrdered(IntConsumer action) {
        forEach(action);
    }

    public int[] toArray() {
        ArrayList<Integer> l = new ArrayList<Integer>();
        while (it.hasNext()) {
            l.add(it.next());
        }
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = l.get(i);
        }
        return a;
    }

    public int reduce(int identity, IntBinaryOperator op) {
        int r = identity;
        while (it.hasNext()) {
            r = op.applyAsInt(r, it.next());
        }
        return r;
    }

    public OptionalInt reduce(IntBinaryOperator op) {
        if (!it.hasNext()) {
            return OptionalInt.empty();
        }
        int r = it.next();
        while (it.hasNext()) {
            r = op.applyAsInt(r, it.next());
        }
        return OptionalInt.of(r);
    }

    public <R> R collect(Supplier<R> supplier, ObjIntConsumer<R> accumulator, BiConsumer<R, R> combiner) {
        R r = supplier.get();
        while (it.hasNext()) {
            accumulator.accept(r, it.next());
        }
        return r;
    }

    public int sum() {
        int s = 0;
        while (it.hasNext()) {
            s += it.next();
        }
        return s;
    }

    public OptionalInt min() {
        if (!it.hasNext()) {
            return OptionalInt.empty();
        }
        int m = it.next();
        while (it.hasNext()) {
            m = Math.min(m, it.next());
        }
        return OptionalInt.of(m);
    }

    public OptionalInt max() {
        if (!it.hasNext()) {
            return OptionalInt.empty();
        }
        int m = it.next();
        while (it.hasNext()) {
            m = Math.max(m, it.next());
        }
        return OptionalInt.of(m);
    }

    public long count() {
        long n = 0;
        while (it.hasNext()) {
            it.next();
            n++;
        }
        return n;
    }

    public OptionalDouble average() {
        IntSummaryStatistics st = summaryStatistics();
        return st.getCount() == 0 ? OptionalDouble.empty() : OptionalDouble.of(st.getAverage());
    }

    public IntSummaryStatistics summaryStatistics() {
        IntSummaryStatistics st = new IntSummaryStatistics();
        while (it.hasNext()) {
            st.accept(it.next());
        }
        return st;
    }

    public boolean anyMatch(IntPredicate predicate) {
        while (it.hasNext()) {
            if (predicate.test(it.next())) {
                return true;
            }
        }
        return false;
    }

    public boolean allMatch(IntPredicate predicate) {
        while (it.hasNext()) {
            if (!predicate.test(it.next())) {
                return false;
            }
        }
        return true;
    }

    public boolean noneMatch(IntPredicate predicate) {
        return !anyMatch(predicate);
    }

    public OptionalInt findFirst() {
        return it.hasNext() ? OptionalInt.of(it.next()) : OptionalInt.empty();
    }

    public OptionalInt findAny() {
        return findFirst();
    }

    public LongStream asLongStream() {
        return new LongIterStream(s().map(new Function<Integer, Long>() {
            public Long apply(Integer v) {
                return (long) v;
            }
        }).iterator());
    }

    public DoubleStream asDoubleStream() {
        return new DoubleIterStream(s().map(new Function<Integer, Double>() {
            public Double apply(Integer v) {
                return (double) v;
            }
        }).iterator());
    }

    public LongStream mapToLong(final IntToLongFunction mapper) {
        return new LongIterStream(s().map(new Function<Integer, Long>() {
            public Long apply(Integer v) {
                return mapper.applyAsLong(v);
            }
        }).iterator());
    }

    public DoubleStream mapToDouble(final IntToDoubleFunction mapper) {
        return new DoubleIterStream(s().map(new Function<Integer, Double>() {
            public Double apply(Integer v) {
                return mapper.applyAsDouble(v);
            }
        }).iterator());
    }
}
