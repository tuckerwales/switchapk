package java.util.function;

@FunctionalInterface
public interface IntConsumer {
    void accept(int value);

    default IntConsumer andThen(final IntConsumer after) {
        final IntConsumer self = this;
        return new IntConsumer() {
            public void accept(int t) {
                self.accept(t);
                after.accept(t);
            }
        };
    }
}
