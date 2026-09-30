package java.util.function;

@FunctionalInterface
public interface Consumer<T> {
    void accept(T t);

    default Consumer<T> andThen(final Consumer<? super T> after) {
        final Consumer<T> self = this;
        return new Consumer<T>() {
            public void accept(T t) {
                self.accept(t);
                after.accept(t);
            }
        };
    }
}
