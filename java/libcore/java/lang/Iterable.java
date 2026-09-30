package java.lang;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

public interface Iterable<T> {
    Iterator<T> iterator();

    default void forEach(Consumer<? super T> action) {
        for (T t : this) {
            action.accept(t);
        }
    }

    default Spliterator<T> spliterator() {
        return java.util.Spliterators.spliteratorUnknownSize(iterator(), 0);
    }
}
