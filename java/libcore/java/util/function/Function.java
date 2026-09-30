package java.util.function;

@FunctionalInterface
public interface Function<T, R> {
    R apply(T t);

    default <V> Function<V, R> compose(final Function<? super V, ? extends T> before) {
        final Function<T, R> self = this;
        return new Function<V, R>() {
            public R apply(V v) {
                return self.apply(before.apply(v));
            }
        };
    }

    default <V> Function<T, V> andThen(final Function<? super R, ? extends V> after) {
        final Function<T, R> self = this;
        return new Function<T, V>() {
            public V apply(T t) {
                return after.apply(self.apply(t));
            }
        };
    }

    static <T> Function<T, T> identity() {
        return new Function<T, T>() {
            public T apply(T t) {
                return t;
            }
        };
    }
}
