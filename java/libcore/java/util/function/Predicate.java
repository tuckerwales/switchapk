package java.util.function;

@FunctionalInterface
public interface Predicate<T> {
    boolean test(T t);

    default Predicate<T> and(final Predicate<? super T> other) {
        final Predicate<T> self = this;
        return new Predicate<T>() {
            public boolean test(T t) {
                return self.test(t) && other.test(t);
            }
        };
    }

    default Predicate<T> negate() {
        final Predicate<T> self = this;
        return new Predicate<T>() {
            public boolean test(T t) {
                return !self.test(t);
            }
        };
    }

    default Predicate<T> or(final Predicate<? super T> other) {
        final Predicate<T> self = this;
        return new Predicate<T>() {
            public boolean test(T t) {
                return self.test(t) || other.test(t);
            }
        };
    }

    static <T> Predicate<T> isEqual(final Object targetRef) {
        return new Predicate<T>() {
            public boolean test(T t) {
                return java.util.Objects.equals(targetRef, t);
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <T> Predicate<T> not(Predicate<? super T> target) {
        return (Predicate<T>) target.negate();
    }
}
