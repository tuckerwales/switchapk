package java.util.function;

@FunctionalInterface
public interface BiPredicate<T, U> {
    boolean test(T t, U u);

    default BiPredicate<T, U> and(final BiPredicate<? super T, ? super U> other) {
        final BiPredicate<T, U> self = this;
        return new BiPredicate<T, U>() {
            public boolean test(T t, U u) {
                return self.test(t, u) && other.test(t, u);
            }
        };
    }

    default BiPredicate<T, U> negate() {
        final BiPredicate<T, U> self = this;
        return new BiPredicate<T, U>() {
            public boolean test(T t, U u) {
                return !self.test(t, u);
            }
        };
    }

    default BiPredicate<T, U> or(final BiPredicate<? super T, ? super U> other) {
        final BiPredicate<T, U> self = this;
        return new BiPredicate<T, U>() {
            public boolean test(T t, U u) {
                return self.test(t, u) || other.test(t, u);
            }
        };
    }
}
