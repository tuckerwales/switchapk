package java.util.stream;

import java.util.OptionalDouble;
import java.util.OptionalDouble;
import java.util.PrimitiveIterator;
import java.util.DoubleSummaryStatistics;
import java.util.function.*;

public interface DoubleStream extends BaseStream<Double, DoubleStream> {
    DoubleStream filter(DoublePredicate predicate);

    DoubleStream map(DoubleUnaryOperator mapper);

    <U> Stream<U> mapToObj(DoubleFunction<? extends U> mapper);

    DoubleStream flatMap(DoubleFunction<? extends DoubleStream> mapper);

    DoubleStream distinct();

    DoubleStream sorted();

    DoubleStream peek(DoubleConsumer action);

    DoubleStream limit(long maxSize);

    DoubleStream skip(long n);

    void forEach(DoubleConsumer action);

    void forEachOrdered(DoubleConsumer action);

    double[] toArray();

    double reduce(double identity, DoubleBinaryOperator op);

    OptionalDouble reduce(DoubleBinaryOperator op);

    <R> R collect(Supplier<R> supplier, ObjDoubleConsumer<R> accumulator, BiConsumer<R, R> combiner);

    double sum();

    OptionalDouble min();

    OptionalDouble max();

    long count();

    OptionalDouble average();

    DoubleSummaryStatistics summaryStatistics();

    boolean anyMatch(DoublePredicate predicate);

    boolean allMatch(DoublePredicate predicate);

    boolean noneMatch(DoublePredicate predicate);

    OptionalDouble findFirst();

    OptionalDouble findAny();

    Stream<Double> boxed();

    PrimitiveIterator.OfDouble iterator();

    IntStream mapToInt(DoubleToIntFunction mapper);

    static DoubleStream empty() {
        return new DoubleIterStream(new java.util.ArrayList<Double>().iterator());
    }

    static DoubleStream of(double t) {
        return of(new double[] {t});
    }

    static DoubleStream of(final double... values) {
        return new DoubleIterStream(new java.util.Iterator<Double>() {
            int i;

            public boolean hasNext() {
                return i < values.length;
            }

            public Double next() {
                return values[i++];
            }
        });
    }

    static DoubleStream iterate(final double seed, final DoubleUnaryOperator f) {
        return new DoubleIterStream(new java.util.Iterator<Double>() {
            double t;
            boolean started;

            public boolean hasNext() {
                return true;
            }

            public Double next() {
                t = started ? f.applyAsDouble(t) : seed;
                started = true;
                return t;
            }
        });
    }

    static DoubleStream generate(final DoubleSupplier s) {
        return new DoubleIterStream(new java.util.Iterator<Double>() {
            public boolean hasNext() {
                return true;
            }

            public Double next() {
                return s.getAsDouble();
            }
        });
    }

    static DoubleStream concat(DoubleStream a, DoubleStream b) {
        return new DoubleIterStream(Stream.concat(a.boxed(), b.boxed()).iterator());
    }
}
