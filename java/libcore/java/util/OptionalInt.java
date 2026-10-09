package java.util;

public final class OptionalInt {
    private static final OptionalInt EMPTY = new OptionalInt();
    private final boolean isPresent;
    private final int value;

    private OptionalInt() {
        this.isPresent = false;
        this.value = 0;
    }

    private OptionalInt(int value) {
        this.isPresent = true;
        this.value = value;
    }

    public static OptionalInt empty() {
        return EMPTY;
    }

    public static OptionalInt of(int value) {
        return new OptionalInt(value);
    }

    public int getAsInt() {
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

    public void ifPresent(java.util.function.IntConsumer consumer) {
        if (isPresent) {
            consumer.accept(value);
        }
    }

    public int orElse(int other) {
        return isPresent ? value : other;
    }

    public int orElseGet(java.util.function.IntSupplier other) {
        return isPresent ? value : other.getAsInt();
    }

    public int orElseThrow() {
        return getAsInt();
    }

    public <X extends Throwable> int orElseThrow(java.util.function.Supplier<? extends X> exceptionSupplier) throws X {
        if (isPresent) {
            return value;
        }
        throw exceptionSupplier.get();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof OptionalInt)) {
            return false;
        }
        OptionalInt other = (OptionalInt) obj;
        return (isPresent && other.isPresent) ? Integer.compare(value, other.value) == 0 : isPresent == other.isPresent;
    }

    public int hashCode() {
        return isPresent ? Integer.hashCode(value) : 0;
    }

    public String toString() {
        return isPresent ? "OptionalInt[" + value + "]" : "OptionalInt.empty";
    }

    public void ifPresentOrElse(java.util.function.IntConsumer action, Runnable emptyAction) {
        if (isPresent()) {
            action.accept(getAsInt());
        } else {
            emptyAction.run();
        }
    }

    public java.util.stream.IntStream stream() {
        return isPresent() ? java.util.stream.IntStream.of(getAsInt()) : java.util.stream.IntStream.of(new int[0]);
    }
}
