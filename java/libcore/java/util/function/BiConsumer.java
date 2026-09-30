package java.util.function;

@FunctionalInterface
public interface BiConsumer<T, U> {
    void accept(T t, U u);

    default BiConsumer<T, U> andThen(final BiConsumer<? super T, ? super U> after) {
        final BiConsumer<T, U> self = this;
        return new BiConsumer<T, U>() {
            public void accept(T t, U u) {
                self.accept(t, u);
                after.accept(t, u);
            }
        };
    }
}
