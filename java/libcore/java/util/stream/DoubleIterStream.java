package java.util.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.OptionalDouble;
import java.util.OptionalDouble;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.DoubleSummaryStatistics;
import java.util.function.*;

final class DoubleIterStream implements DoubleStream {
    private final Iterator<Double> it;

    @SuppressWarnings("unchecked")
    DoubleIterStream(Iterator<? extends Double> it) {
        this.it = (Iterator<Double>) it;
    }

    public PrimitiveIterator.OfDouble iterator() {
        return new PrimitiveIterator.OfDouble() {
            public boolean hasNext() {
                return it.hasNext();
            }

            public double nextDouble() {
                return it.next();
            }
        };
    }

    @SuppressWarnings("unchecked")
    public Spliterator<Double> spliterator() {
        return Spliterators.spliteratorUnknownSize(it, 0);
    }

    public boolean isParallel() {
        return false;
    }

    public DoubleStream sequential() {
        return this;
    }

    public DoubleStream parallel() {
        return this;
    }

    public DoubleStream unordered() {
        return this;
    }

    public DoubleStream onClose(Runnable closeHandler) {
        return this;
    }

    public void close() {
    }

    private Stream<Double> s() {
        return new IterStream<Double>(it);
    }

    public Stream<Double> boxed() {
        return s();
    }

    public DoubleStream filter(final DoublePredicate predicate) {
        return new DoubleIterStream(s().filter(new Predicate<Double>() {
            public boolean test(Double v) {
                return predicate.test(v);
            }
        }).iterator());
    }

    public DoubleStream map(final DoubleUnaryOperator mapper) {
        return new DoubleIterStream(s().map(new Function<Double, Double>() {
            public Double apply(Double v) {
                return mapper.applyAsDouble(v);
            }
        }).iterator());
    }

    public <U> Stream<U> mapToObj(final DoubleFunction<? extends U> mapper) {
        return s().map(new Function<Double, U>() {
            public U apply(Double v) {
                return mapper.apply(v);
            }
        });
    }

    public DoubleStream flatMap(final DoubleFunction<? extends DoubleStream> mapper) {
        return new DoubleIterStream(s().flatMap(new Function<Double, Stream<Double>>() {
            public Stream<Double> apply(Double v) {
                return mapper.apply(v).boxed();
            }
        }).iterator());
    }

    public DoubleStream distinct() {
        return new DoubleIterStream(s().distinct().iterator());
    }

    public DoubleStream sorted() {
        return new DoubleIterStream(s().sorted().iterator());
    }

    public DoubleStream peek(final DoubleConsumer action) {
        return new DoubleIterStream(s().peek(new Consumer<Double>() {
            public void accept(Double v) {
                action.accept(v);
            }
        }).iterator());
    }

    public DoubleStream limit(long maxSize) {
        return new DoubleIterStream(s().limit(maxSize).iterator());
    }

    public DoubleStream skip(long n) {
        return new DoubleIterStream(s().skip(n).iterator());
    }

    public void forEach(DoubleConsumer action) {
        while (it.hasNext()) {
            action.accept(it.next());
        }
    }

    public void forEachOrdered(DoubleConsumer action) {
        forEach(action);
    }

    public double[] toArray() {
        ArrayList<Double> l = new ArrayList<Double>();
        while (it.hasNext()) {
            l.add(it.next());
        }
        double[] a = new double[l.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = l.get(i);
        }
        return a;
    }

    public double reduce(double identity, DoubleBinaryOperator op) {
        double r = identity;
        while (it.hasNext()) {
            r = op.applyAsDouble(r, it.next());
        }
        return r;
    }

    public OptionalDouble reduce(DoubleBinaryOperator op) {
        if (!it.hasNext()) {
            return OptionalDouble.empty();
        }
        double r = it.next();
        while (it.hasNext()) {
            r = op.applyAsDouble(r, it.next());
        }
        return OptionalDouble.of(r);
    }

    public <R> R collect(Supplier<R> supplier, ObjDoubleConsumer<R> accumulator, BiConsumer<R, R> combiner) {
        R r = supplier.get();
        while (it.hasNext()) {
            accumulator.accept(r, it.next());
        }
        return r;
    }

    public double sum() {
        double s = 0;
        while (it.hasNext()) {
            s += it.next();
        }
        return s;
    }

    public OptionalDouble min() {
        if (!it.hasNext()) {
            return OptionalDouble.empty();
        }
        double m = it.next();
        while (it.hasNext()) {
            m = Math.min(m, it.next());
        }
        return OptionalDouble.of(m);
    }

    public OptionalDouble max() {
        if (!it.hasNext()) {
            return OptionalDouble.empty();
        }
        double m = it.next();
        while (it.hasNext()) {
            m = Math.max(m, it.next());
        }
        return OptionalDouble.of(m);
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
        DoubleSummaryStatistics st = summaryStatistics();
        return st.getCount() == 0 ? OptionalDouble.empty() : OptionalDouble.of(st.getAverage());
    }

    public DoubleSummaryStatistics summaryStatistics() {
        DoubleSummaryStatistics st = new DoubleSummaryStatistics();
        while (it.hasNext()) {
            st.accept(it.next());
        }
        return st;
    }

    public boolean anyMatch(DoublePredicate predicate) {
        while (it.hasNext()) {
            if (predicate.test(it.next())) {
                return true;
            }
        }
        return false;
    }

    public boolean allMatch(DoublePredicate predicate) {
        while (it.hasNext()) {
            if (!predicate.test(it.next())) {
                return false;
            }
        }
        return true;
    }

    public boolean noneMatch(DoublePredicate predicate) {
        return !anyMatch(predicate);
    }

    public OptionalDouble findFirst() {
        return it.hasNext() ? OptionalDouble.of(it.next()) : OptionalDouble.empty();
    }

    public OptionalDouble findAny() {
        return findFirst();
    }

    public IntStream mapToInt(final DoubleToIntFunction mapper) {
        return new IntIterStream(s().map(new Function<Double, Integer>() {
            public Integer apply(Double v) {
                return mapper.applyAsInt(v);
            }
        }).iterator());
    }
}
