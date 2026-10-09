package java.util;

import java.util.function.IntConsumer;

public interface PrimitiveIterator<T, T_CONS> extends Iterator<T> {
    void forEachRemaining(T_CONS action);

    interface OfInt extends PrimitiveIterator<Integer, IntConsumer> {
        int nextInt();

        default void forEachRemaining(IntConsumer action) {
            Objects.requireNonNull(action);
            while (hasNext()) {
                action.accept(nextInt());
            }
        }

        default Integer next() {
            return nextInt();
        }

        default void forEachRemaining(java.util.function.Consumer<? super Integer> action) {
            if (action instanceof IntConsumer) {
                forEachRemaining((IntConsumer) action);
            } else {
                Objects.requireNonNull(action);
                forEachRemaining(new java.util.function.IntConsumer() {
                public void accept(int v) {
                    action.accept(v);
                }
            });
            }
        }
    }

    interface OfLong extends PrimitiveIterator<Long, java.util.function.LongConsumer> {
        long nextLong();

        default void forEachRemaining(java.util.function.LongConsumer action) {
            Objects.requireNonNull(action);
            while (hasNext()) {
                action.accept(nextLong());
            }
        }

        default Long next() {
            return nextLong();
        }

        default void forEachRemaining(java.util.function.Consumer<? super Long> action) {
            if (action instanceof java.util.function.LongConsumer) {
                forEachRemaining((java.util.function.LongConsumer) action);
            } else {
                Objects.requireNonNull(action);
                forEachRemaining(new java.util.function.LongConsumer() {
                public void accept(long v) {
                    action.accept(v);
                }
            });
            }
        }
    }

    interface OfDouble extends PrimitiveIterator<Double, java.util.function.DoubleConsumer> {
        double nextDouble();

        default void forEachRemaining(java.util.function.DoubleConsumer action) {
            Objects.requireNonNull(action);
            while (hasNext()) {
                action.accept(nextDouble());
            }
        }

        default Double next() {
            return nextDouble();
        }

        default void forEachRemaining(java.util.function.Consumer<? super Double> action) {
            if (action instanceof java.util.function.DoubleConsumer) {
                forEachRemaining((java.util.function.DoubleConsumer) action);
            } else {
                Objects.requireNonNull(action);
                forEachRemaining(new java.util.function.DoubleConsumer() {
                public void accept(double v) {
                    action.accept(v);
                }
            });
            }
        }
    }
}
