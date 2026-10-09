package java.util;

import java.util.function.Consumer;

public final class Spliterators {
    private Spliterators() {
    }

    public static <T> Spliterator<T> spliterator(Collection<? extends T> c, int characteristics) {
        return new IteratorSpliterator<T>(c.iterator(), c.size(), characteristics | Spliterator.SIZED);
    }

    public static <T> Spliterator<T> spliteratorUnknownSize(Iterator<? extends T> iterator, int characteristics) {
        return new IteratorSpliterator<T>(iterator, Long.MAX_VALUE, characteristics);
    }

    public static <T> Spliterator<T> spliterator(Iterator<? extends T> iterator, long size, int characteristics) {
        return new IteratorSpliterator<T>(iterator, size, characteristics | Spliterator.SIZED);
    }

    public static <T> Iterator<T> iterator(final Spliterator<? extends T> spliterator) {
        Objects.requireNonNull(spliterator);
        return new Iterator<T>() {
            boolean ready;
            T next;

            public boolean hasNext() {
                if (!ready) {
                    spliterator.tryAdvance(new Consumer<T>() {
                        public void accept(T t) {
                            ready = true;
                            next = t;
                        }
                    });
                }
                return ready;
            }

            public T next() {
                if (!ready && !hasNext()) {
                    throw new NoSuchElementException();
                }
                ready = false;
                T t = next;
                next = null;
                return t;
            }
        };
    }

    public static PrimitiveIterator.OfInt iterator(final Spliterator.OfInt spliterator) {
        Objects.requireNonNull(spliterator);
        return new PrimitiveIterator.OfInt() {
            boolean ready;
            int next;

            public boolean hasNext() {
                if (!ready) {
                    spliterator.tryAdvance(new java.util.function.IntConsumer() {
                        public void accept(int t) {
                            ready = true;
                            next = t;
                        }
                    });
                }
                return ready;
            }

            public int nextInt() {
                if (!ready && !hasNext()) {
                    throw new NoSuchElementException();
                }
                ready = false;
                return next;
            }
        };
    }

    public static PrimitiveIterator.OfLong iterator(final Spliterator.OfLong spliterator) {
        Objects.requireNonNull(spliterator);
        return new PrimitiveIterator.OfLong() {
            boolean ready;
            long next;

            public boolean hasNext() {
                if (!ready) {
                    spliterator.tryAdvance(new java.util.function.LongConsumer() {
                        public void accept(long t) {
                            ready = true;
                            next = t;
                        }
                    });
                }
                return ready;
            }

            public long nextLong() {
                if (!ready && !hasNext()) {
                    throw new NoSuchElementException();
                }
                ready = false;
                return next;
            }
        };
    }

    public static PrimitiveIterator.OfDouble iterator(final Spliterator.OfDouble spliterator) {
        Objects.requireNonNull(spliterator);
        return new PrimitiveIterator.OfDouble() {
            boolean ready;
            double next;

            public boolean hasNext() {
                if (!ready) {
                    spliterator.tryAdvance(new java.util.function.DoubleConsumer() {
                        public void accept(double t) {
                            ready = true;
                            next = t;
                        }
                    });
                }
                return ready;
            }

            public double nextDouble() {
                if (!ready && !hasNext()) {
                    throw new NoSuchElementException();
                }
                ready = false;
                return next;
            }
        };
    }

    public static <T> Spliterator<T> spliterator(Object[] array, int additionalCharacteristics) {
        return spliterator(array, 0, array.length, additionalCharacteristics);
    }

    @SuppressWarnings("unchecked")
    public static <T> Spliterator<T> spliterator(Object[] array, int fromIndex, int toIndex,
            int additionalCharacteristics) {
        Arrays.rangeCheck(array.length, fromIndex, toIndex);
        return new IteratorSpliterator<T>((Iterator<T>) Arrays.asList(array).subList(fromIndex, toIndex).iterator(),
                toIndex - fromIndex, additionalCharacteristics | Spliterator.SIZED | Spliterator.SUBSIZED);
    }

    public static Spliterator.OfInt spliterator(int[] array, int additionalCharacteristics) {
        return spliterator(array, 0, array.length, additionalCharacteristics);
    }

    public static Spliterator.OfInt spliterator(final int[] array, int fromIndex, final int toIndex,
            int additionalCharacteristics) {
        Arrays.rangeCheck(array.length, fromIndex, toIndex);
        final int c = additionalCharacteristics | Spliterator.SIZED | Spliterator.SUBSIZED;
        final int[] pos = {fromIndex};
        return new Spliterator.OfInt() {
            public boolean tryAdvance(java.util.function.IntConsumer action) {
                Objects.requireNonNull(action);
                if (pos[0] < toIndex) {
                    action.accept(array[pos[0]++]);
                    return true;
                }
                return false;
            }

            public Spliterator.OfInt trySplit() {
                return null;
            }

            public long estimateSize() {
                return toIndex - pos[0];
            }

            public int characteristics() {
                return c;
            }
        };
    }

    public static Spliterator.OfLong spliterator(long[] array, int additionalCharacteristics) {
        return spliterator(array, 0, array.length, additionalCharacteristics);
    }

    public static Spliterator.OfLong spliterator(final long[] array, int fromIndex, final int toIndex,
            int additionalCharacteristics) {
        Arrays.rangeCheck(array.length, fromIndex, toIndex);
        final int c = additionalCharacteristics | Spliterator.SIZED | Spliterator.SUBSIZED;
        final int[] pos = {fromIndex};
        return new Spliterator.OfLong() {
            public boolean tryAdvance(java.util.function.LongConsumer action) {
                Objects.requireNonNull(action);
                if (pos[0] < toIndex) {
                    action.accept(array[pos[0]++]);
                    return true;
                }
                return false;
            }

            public Spliterator.OfLong trySplit() {
                return null;
            }

            public long estimateSize() {
                return toIndex - pos[0];
            }

            public int characteristics() {
                return c;
            }
        };
    }

    public static Spliterator.OfDouble spliterator(double[] array, int additionalCharacteristics) {
        return spliterator(array, 0, array.length, additionalCharacteristics);
    }

    public static Spliterator.OfDouble spliterator(final double[] array, int fromIndex, final int toIndex,
            int additionalCharacteristics) {
        Arrays.rangeCheck(array.length, fromIndex, toIndex);
        final int c = additionalCharacteristics | Spliterator.SIZED | Spliterator.SUBSIZED;
        final int[] pos = {fromIndex};
        return new Spliterator.OfDouble() {
            public boolean tryAdvance(java.util.function.DoubleConsumer action) {
                Objects.requireNonNull(action);
                if (pos[0] < toIndex) {
                    action.accept(array[pos[0]++]);
                    return true;
                }
                return false;
            }

            public Spliterator.OfDouble trySplit() {
                return null;
            }

            public long estimateSize() {
                return toIndex - pos[0];
            }

            public int characteristics() {
                return c;
            }
        };
    }

    public static Spliterator.OfInt spliterator(final PrimitiveIterator.OfInt it, final long size,
            int characteristics) {
        return primitive(it, size, characteristics | Spliterator.SIZED);
    }

    public static Spliterator.OfInt spliteratorUnknownSize(PrimitiveIterator.OfInt it, int characteristics) {
        return primitive(it, Long.MAX_VALUE, characteristics);
    }

    private static Spliterator.OfInt primitive(final PrimitiveIterator.OfInt it, final long size, final int c) {
        Objects.requireNonNull(it);
        return new Spliterator.OfInt() {
            public boolean tryAdvance(java.util.function.IntConsumer action) {
                if (it.hasNext()) {
                    action.accept(it.nextInt());
                    return true;
                }
                return false;
            }

            public Spliterator.OfInt trySplit() {
                return null;
            }

            public long estimateSize() {
                return size;
            }

            public int characteristics() {
                return c;
            }
        };
    }

    public static Spliterator.OfLong spliterator(final PrimitiveIterator.OfLong it, final long size,
            int characteristics) {
        return primitive(it, size, characteristics | Spliterator.SIZED);
    }

    public static Spliterator.OfLong spliteratorUnknownSize(PrimitiveIterator.OfLong it, int characteristics) {
        return primitive(it, Long.MAX_VALUE, characteristics);
    }

    private static Spliterator.OfLong primitive(final PrimitiveIterator.OfLong it, final long size, final int c) {
        Objects.requireNonNull(it);
        return new Spliterator.OfLong() {
            public boolean tryAdvance(java.util.function.LongConsumer action) {
                if (it.hasNext()) {
                    action.accept(it.nextLong());
                    return true;
                }
                return false;
            }

            public Spliterator.OfLong trySplit() {
                return null;
            }

            public long estimateSize() {
                return size;
            }

            public int characteristics() {
                return c;
            }
        };
    }

    public static Spliterator.OfDouble spliterator(final PrimitiveIterator.OfDouble it, final long size,
            int characteristics) {
        return primitive(it, size, characteristics | Spliterator.SIZED);
    }

    public static Spliterator.OfDouble spliteratorUnknownSize(PrimitiveIterator.OfDouble it, int characteristics) {
        return primitive(it, Long.MAX_VALUE, characteristics);
    }

    private static Spliterator.OfDouble primitive(final PrimitiveIterator.OfDouble it, final long size,
            final int c) {
        Objects.requireNonNull(it);
        return new Spliterator.OfDouble() {
            public boolean tryAdvance(java.util.function.DoubleConsumer action) {
                if (it.hasNext()) {
                    action.accept(it.nextDouble());
                    return true;
                }
                return false;
            }

            public Spliterator.OfDouble trySplit() {
                return null;
            }

            public long estimateSize() {
                return size;
            }

            public int characteristics() {
                return c;
            }
        };
    }

    public static Spliterator.OfInt emptyIntSpliterator() {
        return spliterator(new int[0], 0);
    }

    public static Spliterator.OfLong emptyLongSpliterator() {
        return spliterator(new long[0], 0);
    }

    public static Spliterator.OfDouble emptyDoubleSpliterator() {
        return spliterator(new double[0], 0);
    }

    public static <T> Spliterator<T> emptySpliterator() {
        return new IteratorSpliterator<T>(Collections.<T>emptyIterator(), 0, Spliterator.SIZED);
    }

    static final class IteratorSpliterator<T> implements Spliterator<T> {
        private final Iterator<? extends T> it;
        private final long size;
        private final int characteristics;

        IteratorSpliterator(Iterator<? extends T> it, long size, int characteristics) {
            this.it = it;
            this.size = size;
            this.characteristics = characteristics;
        }

        public boolean tryAdvance(Consumer<? super T> action) {
            if (it.hasNext()) {
                action.accept(it.next());
                return true;
            }
            return false;
        }

        public Spliterator<T> trySplit() {
            return null;
        }

        public long estimateSize() {
            return size;
        }

        public int characteristics() {
            return characteristics;
        }
    }
}
