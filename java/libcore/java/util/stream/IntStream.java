package java.util.stream;

import java.util.OptionalInt;
import java.util.OptionalDouble;
import java.util.PrimitiveIterator;
import java.util.IntSummaryStatistics;
import java.util.function.*;

public interface IntStream extends BaseStream<Integer, IntStream> {
    IntStream filter(IntPredicate predicate);

    IntStream map(IntUnaryOperator mapper);

    <U> Stream<U> mapToObj(IntFunction<? extends U> mapper);

    IntStream flatMap(IntFunction<? extends IntStream> mapper);

    IntStream distinct();

    IntStream sorted();

    IntStream peek(IntConsumer action);

    IntStream limit(long maxSize);

    IntStream skip(long n);

    void forEach(IntConsumer action);

    void forEachOrdered(IntConsumer action);

    int[] toArray();

    int reduce(int identity, IntBinaryOperator op);

    OptionalInt reduce(IntBinaryOperator op);

    <R> R collect(Supplier<R> supplier, ObjIntConsumer<R> accumulator, BiConsumer<R, R> combiner);

    int sum();

    OptionalInt min();

    OptionalInt max();

    long count();

    OptionalDouble average();

    IntSummaryStatistics summaryStatistics();

    boolean anyMatch(IntPredicate predicate);

    boolean allMatch(IntPredicate predicate);

    boolean noneMatch(IntPredicate predicate);

    OptionalInt findFirst();

    OptionalInt findAny();

    Stream<Integer> boxed();

    PrimitiveIterator.OfInt iterator();

    LongStream asLongStream();

    DoubleStream asDoubleStream();

    LongStream mapToLong(IntToLongFunction mapper);

    DoubleStream mapToDouble(IntToDoubleFunction mapper);

    static IntStream range(int startInclusive, int endExclusive) {
        int n = Math.max(0, endExclusive - startInclusive);
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = startInclusive + i;
        }
        return of(a);
    }

    static IntStream rangeClosed(int startInclusive, int endInclusive) {
        return range(startInclusive, endInclusive + 1);
    }

    static IntStream empty() {
        return new IntIterStream(new java.util.ArrayList<Integer>().iterator());
    }

    static IntStream of(int t) {
        return of(new int[] {t});
    }

    static IntStream of(final int... values) {
        return new IntIterStream(new java.util.Iterator<Integer>() {
            int i;

            public boolean hasNext() {
                return i < values.length;
            }

            public Integer next() {
                return values[i++];
            }
        });
    }

    static IntStream iterate(final int seed, final IntUnaryOperator f) {
        return new IntIterStream(new java.util.Iterator<Integer>() {
            int t;
            boolean started;

            public boolean hasNext() {
                return true;
            }

            public Integer next() {
                t = started ? f.applyAsInt(t) : seed;
                started = true;
                return t;
            }
        });
    }

    static IntStream generate(final IntSupplier s) {
        return new IntIterStream(new java.util.Iterator<Integer>() {
            public boolean hasNext() {
                return true;
            }

            public Integer next() {
                return s.getAsInt();
            }
        });
    }

    static IntStream concat(IntStream a, IntStream b) {
        return new IntIterStream(Stream.concat(a.boxed(), b.boxed()).iterator());
    }
}
