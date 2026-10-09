package java.util;

public final class OptionalLong {
    private static final OptionalLong EMPTY = new OptionalLong();
    private final boolean isPresent;
    private final long value;

    private OptionalLong() {
        this.isPresent = false;
        this.value = 0;
    }

    private OptionalLong(long value) {
        this.isPresent = true;
        this.value = value;
    }

    public static OptionalLong empty() {
        return EMPTY;
    }

    public static OptionalLong of(long value) {
        return new OptionalLong(value);
    }

    public long getAsLong() {
        if (!isPresent) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    public boolean isPresent() {
        return isPresent;
    }

    public boolean isEmpty() {
        return !isPresent;
    }

    public void ifPresent(java.util.function.LongConsumer consumer) {
        if (isPresent) {
            consumer.accept(value);
        }
    }

    public long orElse(long other) {
        return isPresent ? value : other;
    }

    public long orElseGet(java.util.function.LongSupplier other) {
        return isPresent ? value : other.getAsLong();
    }

    public long orElseThrow() {
        return getAsLong();
    }

    public <X extends Throwable> long orElseThrow(java.util.function.Supplier<? extends X> exceptionSupplier) throws X {
        if (isPresent) {
            return value;
        }
        throw exceptionSupplier.get();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof OptionalLong)) {
            return false;
        }
        OptionalLong other = (OptionalLong) obj;
        return (isPresent && other.isPresent) ? Long.compare(value, other.value) == 0 : isPresent == other.isPresent;
    }

    public int hashCode() {
        return isPresent ? Long.hashCode(value) : 0;
    }

    public String toString() {
        return isPresent ? "OptionalLong[" + value + "]" : "OptionalLong.empty";
    }

    public void ifPresentOrElse(java.util.function.LongConsumer action, Runnable emptyAction) {
        if (isPresent()) {
            action.accept(getAsLong());
        } else {
            emptyAction.run();
        }
    }

    public java.util.stream.LongStream stream() {
        return isPresent() ? java.util.stream.LongStream.of(getAsLong()) : java.util.stream.LongStream.of(new long[0]);
    }
}
