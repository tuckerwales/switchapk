package java.util.stream;

import java.util.Spliterator;
import java.util.Spliterators;

public final class StreamSupport {
    private StreamSupport() {
    }

    public static <T> Stream<T> stream(Spliterator<T> spliterator, boolean parallel) {
        return new IterStream<T>(Spliterators.iterator(spliterator));
    }
}
