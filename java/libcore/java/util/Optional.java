package java.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Optional<T> {
    private static final Optional<?> EMPTY = new Optional<Object>(null);
    private final T value;

    private Optional(T value) {
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> empty() {
        return (Optional<T>) EMPTY;
    }

    public static <T> Optional<T> of(T value) {
        return new Optional<T>(Objects.requireNonNull(value));
    }

    public static <T> Optional<T> ofNullable(T value) {
        return value == null ? Optional.<T>empty() : new Optional<T>(value);
    }

    public T get() {
        if (value == null) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    public boolean isPresent() {
        return value != null;
    }

    public boolean isEmpty() {
        return value == null;
    }

    public void ifPresent(Consumer<? super T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }

    public void ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction) {
        if (value != null) {
            action.accept(value);
        } else {
            emptyAction.run();
        }
    }

    public Optional<T> filter(Predicate<? super T> predicate) {
        if (!isPresent()) {
            return this;
        }
        return predicate.test(value) ? this : Optional.<T>empty();
    }

    public <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
        if (!isPresent()) {
            return empty();
        }
        return Optional.ofNullable(mapper.apply(value));
    }

    public <U> Optional<U> flatMap(Function<? super T, Optional<U>> mapper) {
        if (!isPresent()) {
            return empty();
        }
        return Objects.requireNonNull(mapper.apply(value));
    }

    public Optional<T> or(Supplier<? extends Optional<? extends T>> supplier) {
        if (isPresent()) {
            return this;
        }
        @SuppressWarnings("unchecked")
        Optional<T> r = (Optional<T>) supplier.get();
        return r;
    }

    public T orElse(T other) {
        return value != null ? value : other;
    }

    public T orElseGet(Supplier<? extends T> other) {
        return value != null ? value : other.get();
    }

    public T orElseThrow() {
        return get();
    }

    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (value != null) {
            return value;
        }
        throw exceptionSupplier.get();
    }

    public java.util.stream.Stream<T> stream() {
        if (value == null) {
            return java.util.stream.Stream.empty();
        }
        return java.util.stream.Stream.of(value);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Optional)) {
            return false;
        }
        return Objects.equals(value, ((Optional<?>) obj).value);
    }

    public int hashCode() {
        return Objects.hashCode(value);
    }

    public String toString() {
        return value != null ? "Optional[" + value + "]" : "Optional.empty";
    }
}
