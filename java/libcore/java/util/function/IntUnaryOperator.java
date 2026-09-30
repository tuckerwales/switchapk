package java.util.function;

@FunctionalInterface
public interface IntUnaryOperator {
    int applyAsInt(int operand);

    default IntUnaryOperator andThen(final IntUnaryOperator after) {
        final IntUnaryOperator self = this;
        return new IntUnaryOperator() {
            public int applyAsInt(int v) {
                return after.applyAsInt(self.applyAsInt(v));
            }
        };
    }

    default IntUnaryOperator compose(final IntUnaryOperator before) {
        final IntUnaryOperator self = this;
        return new IntUnaryOperator() {
            public int applyAsInt(int v) {
                return self.applyAsInt(before.applyAsInt(v));
            }
        };
    }

    static IntUnaryOperator identity() {
        return new IntUnaryOperator() {
            public int applyAsInt(int v) {
                return v;
            }
        };
    }
}
