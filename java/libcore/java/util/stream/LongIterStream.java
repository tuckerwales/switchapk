package java.util.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.OptionalLong;
import java.util.OptionalDouble;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.LongSummaryStatistics;
import java.util.function.*;

final class LongIterStream implements LongStream {
    private final Iterator<Long> it;

    @SuppressWarnings("unchecked")
    LongIterStream(Iterator<? extends Long> it) {
        this.it = (Iterator<Long>) it;
    }

    public PrimitiveIterator.OfLong iterator() {
        return new PrimitiveIterator.OfLong() {
            public boolean hasNext() {
                return it.hasNext();
            }

            public long nextLong() {
                return it.next();
            }
        };
    }

    @SuppressWarnings("unchecked")
    public Spliterator<Long> spliterator() {
        return Spliterators.spliteratorUnknownSize(it, 0);
    }

    public boolean isParallel() {
        return false;
    }

    public LongStream sequential() {
        return this;
    }

    public LongStream parallel() {
        return this;
    }

    public LongStream unordered() {
        return this;
    }

    public LongStream onClose(Runnable closeHandler) {
        return this;
    }

    public void close() {
    }

    private Stream<Long> s() {
        return new IterStream<Long>(it);
    }

    public Stream<Long> boxed() {
        return s();
    }

    public LongStream filter(final LongPredicate predicate) {
        return new LongIterStream(s().filter(new Predicate<Long>() {
            public boolean test(Long v) {
                return predicate.test(v);
            }
        }).iterator());
    }

    public LongStream map(final LongUnaryOperator mapper) {
        return new LongIterStream(s().map(new Function<Long, Long>() {
            public Long apply(Long v) {
                return mapper.applyAsLong(v);
            }
        }).iterator());
    }

    public <U> Stream<U> mapToObj(final LongFunction<? extends U> mapper) {
        return s().map(new Function<Long, U>() {
            public U apply(Long v) {
                return mapper.apply(v);
            }
        });
    }

    public LongStream flatMap(final LongFunction<? extends LongStream> mapper) {
        return new LongIterStream(s().flatMap(new Function<Long, Stream<Long>>() {
            public Stream<Long> apply(Long v) {
                return mapper.apply(v).boxed();
            }
        }).iterator());
    }

    public LongStream distinct() {
        return new LongIterStream(s().distinct().iterator());
    }

    public LongStream sorted() {
        return new LongIterStream(s().sorted().iterator());
    }

    public LongStream peek(final LongConsumer action) {
        return new LongIterStream(s().peek(new Consumer<Long>() {
            public void accept(Long v) {
                action.accept(v);
            }
        }).iterator());
    }

    public LongStream limit(long maxSize) {
        return new LongIterStream(s().limit(maxSize).iterator());
    }

    public LongStream skip(long n) {
        return new LongIterStream(s().skip(n).iterator());
    }

    public void forEach(LongConsumer action) {
        while (it.hasNext()) {
            action.accept(it.next());
        }
    }

    public void forEachOrdered(LongConsumer action) {
        forEach(action);
    }

    public long[] toArray() {
        ArrayList<Long> l = new ArrayList<Long>();
        while (it.hasNext()) {
            l.add(it.next());
        }
        long[] a = new long[l.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = l.get(i);
        }
        return a;
    }

    public long reduce(long identity, LongBinaryOperator op) {
        long r = identity;
        while (it.hasNext()) {
            r = op.applyAsLong(r, it.next());
        }
        return r;
    }

    public OptionalLong reduce(LongBinaryOperator op) {
        if (!it.hasNext()) {
            return OptionalLong.empty();
        }
        long r = it.next();
        while (it.hasNext()) {
            r = op.applyAsLong(r, it.next());
        }
        return OptionalLong.of(r);
    }

    public <R> R collect(Supplier<R> supplier, ObjLongConsumer<R> accumulator, BiConsumer<R, R> combiner) {
        R r = supplier.get();
        while (it.hasNext()) {
            accumulator.accept(r, it.next());
        }
        return r;
    }

    public long sum() {
        long s = 0;
        while (it.hasNext()) {
            s += it.next();
        }
        return s;
    }

    public OptionalLong min() {
        if (!it.hasNext()) {
            return OptionalLong.empty();
        }
        long m = it.next();
        while (it.hasNext()) {
            m = Math.min(m, it.next());
        }
        return OptionalLong.of(m);
    }

    public OptionalLong max() {
        if (!it.hasNext()) {
            return OptionalLong.empty();
        }
        long m = it.next();
        while (it.hasNext()) {
            m = Math.max(m, it.next());
        }
        return OptionalLong.of(m);
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
        LongSummaryStatistics st = summaryStatistics();
        return st.getCount() == 0 ? OptionalDouble.empty() : OptionalDouble.of(st.getAverage());
    }

    public LongSummaryStatistics summaryStatistics() {
        LongSummaryStatistics st = new LongSummaryStatistics();
        while (it.hasNext()) {
            st.accept(it.next());
        }
        return st;
    }

    public boolean anyMatch(LongPredicate predicate) {
        while (it.hasNext()) {
            if (predicate.test(it.next())) {
                return true;
            }
        }
        return false;
    }

    public boolean allMatch(LongPredicate predicate) {
        while (it.hasNext()) {
            if (!predicate.test(it.next())) {
                return false;
            }
        }
        return true;
    }

    public boolean noneMatch(LongPredicate predicate) {
        return !anyMatch(predicate);
    }

    public OptionalLong findFirst() {
        return it.hasNext() ? OptionalLong.of(it.next()) : OptionalLong.empty();
    }

    public OptionalLong findAny() {
        return findFirst();
    }

    public DoubleStream asDoubleStream() {
        return new DoubleIterStream(s().map(new Function<Long, Double>() {
            public Double apply(Long v) {
                return (double) v;
            }
        }).iterator());
    }

    public IntStream mapToInt(final LongToIntFunction mapper) {
        return new IntIterStream(s().map(new Function<Long, Integer>() {
            public Integer apply(Long v) {
                return mapper.applyAsInt(v);
            }
        }).iterator());
    }
}
