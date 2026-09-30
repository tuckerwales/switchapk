package java.util.stream;

import java.util.OptionalLong;
import java.util.OptionalDouble;
import java.util.PrimitiveIterator;
import java.util.LongSummaryStatistics;
import java.util.function.*;

public interface LongStream extends BaseStream<Long, LongStream> {
    LongStream filter(LongPredicate predicate);

    LongStream map(LongUnaryOperator mapper);

    <U> Stream<U> mapToObj(LongFunction<? extends U> mapper);

    LongStream flatMap(LongFunction<? extends LongStream> mapper);

    LongStream distinct();

    LongStream sorted();

    LongStream peek(LongConsumer action);

    LongStream limit(long maxSize);

    LongStream skip(long n);

    void forEach(LongConsumer action);

    void forEachOrdered(LongConsumer action);

    long[] toArray();

    long reduce(long identity, LongBinaryOperator op);

    OptionalLong reduce(LongBinaryOperator op);

    <R> R collect(Supplier<R> supplier, ObjLongConsumer<R> accumulator, BiConsumer<R, R> combiner);

    long sum();

    OptionalLong min();

    OptionalLong max();

    long count();

    OptionalDouble average();

    LongSummaryStatistics summaryStatistics();

    boolean anyMatch(LongPredicate predicate);

    boolean allMatch(LongPredicate predicate);

    boolean noneMatch(LongPredicate predicate);

    OptionalLong findFirst();

    OptionalLong findAny();

    Stream<Long> boxed();

    PrimitiveIterator.OfLong iterator();

    DoubleStream asDoubleStream();

    IntStream mapToInt(LongToIntFunction mapper);

    static LongStream range(long startInclusive, long endExclusive) {
        int n = (int) Math.max(0, endExclusive - startInclusive);
        long[] a = new long[n];
        for (int i = 0; i < n; i++) {
            a[i] = startInclusive + i;
        }
        return of(a);
    }

    static LongStream rangeClosed(long startInclusive, long endInclusive) {
        return range(startInclusive, endInclusive + 1);
    }

    static LongStream empty() {
        return new LongIterStream(new java.util.ArrayList<Long>().iterator());
    }

    static LongStream of(long t) {
        return of(new long[] {t});
    }

    static LongStream of(final long... values) {
        return new LongIterStream(new java.util.Iterator<Long>() {
            int i;

            public boolean hasNext() {
                return i < values.length;
            }

            public Long next() {
                return values[i++];
            }
        });
    }

    static LongStream iterate(final long seed, final LongUnaryOperator f) {
        return new LongIterStream(new java.util.Iterator<Long>() {
            long t;
            boolean started;

            public boolean hasNext() {
                return true;
            }

            public Long next() {
                t = started ? f.applyAsLong(t) : seed;
                started = true;
                return t;
            }
        });
    }

    static LongStream generate(final LongSupplier s) {
        return new LongIterStream(new java.util.Iterator<Long>() {
            public boolean hasNext() {
                return true;
            }

            public Long next() {
                return s.getAsLong();
            }
        });
    }

    static LongStream concat(LongStream a, LongStream b) {
        return new LongIterStream(Stream.concat(a.boxed(), b.boxed()).iterator());
    }
}
