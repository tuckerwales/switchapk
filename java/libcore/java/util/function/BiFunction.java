package java.util.function;

@FunctionalInterface
public interface BiFunction<T, U, R> {
    R apply(T t, U u);

    default <V> BiFunction<T, U, V> andThen(final Function<? super R, ? extends V> after) {
        final BiFunction<T, U, R> self = this;
        return new BiFunction<T, U, V>() {
            public V apply(T t, U u) {
                return after.apply(self.apply(t, u));
            }
        };
    }
}
