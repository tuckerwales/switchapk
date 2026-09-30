package java.util;

import java.util.function.IntConsumer;

public interface PrimitiveIterator<T, T_CONS> extends Iterator<T> {
    interface OfInt extends PrimitiveIterator<Integer, IntConsumer> {
        int nextInt();

        default Integer next() {
            return nextInt();
        }
    }

    interface OfLong extends PrimitiveIterator<Long, java.util.function.LongConsumer> {
        long nextLong();

        default Long next() {
            return nextLong();
        }
    }

    interface OfDouble extends PrimitiveIterator<Double, java.util.function.DoubleConsumer> {
        double nextDouble();

        default Double next() {
            return nextDouble();
        }
    }
}
