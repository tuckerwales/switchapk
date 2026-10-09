package java.util;

import java.util.function.Consumer;

public interface Spliterator<T> {
    int ORDERED = 0x00000010;
    int DISTINCT = 0x00000001;
    int SORTED = 0x00000004;
    int SIZED = 0x00000040;
    int NONNULL = 0x00000100;
    int IMMUTABLE = 0x00000400;
    int CONCURRENT = 0x00001000;
    int SUBSIZED = 0x00004000;

    boolean tryAdvance(Consumer<? super T> action);

    default void forEachRemaining(Consumer<? super T> action) {
        do {
        } while (tryAdvance(action));
    }

    Spliterator<T> trySplit();

    long estimateSize();

    default long getExactSizeIfKnown() {
        return (characteristics() & SIZED) == 0 ? -1L : estimateSize();
    }

    int characteristics();

    default boolean hasCharacteristics(int characteristics) {
        return (characteristics() & characteristics) == characteristics;
    }

    default Comparator<? super T> getComparator() {
        throw new IllegalStateException();
    }

    interface OfPrimitive<T, T_CONS, T_SPLITR extends Spliterator.OfPrimitive<T, T_CONS, T_SPLITR>>
            extends Spliterator<T> {
        T_SPLITR trySplit();

        boolean tryAdvance(T_CONS action);

        default void forEachRemaining(T_CONS action) {
            do {
            } while (tryAdvance(action));
        }
    }

    interface OfInt extends OfPrimitive<Integer, java.util.function.IntConsumer, OfInt> {
        OfInt trySplit();

        boolean tryAdvance(java.util.function.IntConsumer action);

        default void forEachRemaining(java.util.function.IntConsumer action) {
            do {
            } while (tryAdvance(action));
        }

        default boolean tryAdvance(Consumer<? super Integer> action) {
            if (action instanceof java.util.function.IntConsumer) {
                return tryAdvance((java.util.function.IntConsumer) action);
            }
            return tryAdvance(new java.util.function.IntConsumer() {
                public void accept(int v) {
                    action.accept(v);
                }
            });
        }

        default void forEachRemaining(Consumer<? super Integer> action) {
            if (action instanceof java.util.function.IntConsumer) {
                forEachRemaining((java.util.function.IntConsumer) action);
            } else {
                forEachRemaining(new java.util.function.IntConsumer() {
                public void accept(int v) {
                    action.accept(v);
                }
            });
            }
        }
    }

    interface OfLong extends OfPrimitive<Long, java.util.function.LongConsumer, OfLong> {
        OfLong trySplit();

        boolean tryAdvance(java.util.function.LongConsumer action);

        default void forEachRemaining(java.util.function.LongConsumer action) {
            do {
            } while (tryAdvance(action));
        }

        default boolean tryAdvance(Consumer<? super Long> action) {
            if (action instanceof java.util.function.LongConsumer) {
                return tryAdvance((java.util.function.LongConsumer) action);
            }
            return tryAdvance(new java.util.function.LongConsumer() {
                public void accept(long v) {
                    action.accept(v);
                }
            });
        }

        default void forEachRemaining(Consumer<? super Long> action) {
            if (action instanceof java.util.function.LongConsumer) {
                forEachRemaining((java.util.function.LongConsumer) action);
            } else {
                forEachRemaining(new java.util.function.LongConsumer() {
                public void accept(long v) {
                    action.accept(v);
                }
            });
            }
        }
    }

    interface OfDouble extends OfPrimitive<Double, java.util.function.DoubleConsumer, OfDouble> {
        OfDouble trySplit();

        boolean tryAdvance(java.util.function.DoubleConsumer action);

        default void forEachRemaining(java.util.function.DoubleConsumer action) {
            do {
            } while (tryAdvance(action));
        }

        default boolean tryAdvance(Consumer<? super Double> action) {
            if (action instanceof java.util.function.DoubleConsumer) {
                return tryAdvance((java.util.function.DoubleConsumer) action);
            }
            return tryAdvance(new java.util.function.DoubleConsumer() {
                public void accept(double v) {
                    action.accept(v);
                }
            });
        }

        default void forEachRemaining(Consumer<? super Double> action) {
            if (action instanceof java.util.function.DoubleConsumer) {
                forEachRemaining((java.util.function.DoubleConsumer) action);
            } else {
                forEachRemaining(new java.util.function.DoubleConsumer() {
                public void accept(double v) {
                    action.accept(v);
                }
            });
            }
        }
    }
}
