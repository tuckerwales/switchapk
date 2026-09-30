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
        final ArrayList<T> items = new ArrayList<T>();
        spliterator.forEachRemaining(new Consumer<T>() {
            public void accept(T t) {
                items.add(t);
            }
        });
        return items.iterator();
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
