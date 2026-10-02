package android.animation;

public abstract class BidirectionalTypeConverter<T, V> extends TypeConverter<T, V> {
    private BidirectionalTypeConverter<V, T> mInverted;

    public BidirectionalTypeConverter(Class<T> fromType, Class<V> toType) {
        super(fromType, toType);
    }

    public abstract T convertBack(V value);

    public BidirectionalTypeConverter<V, T> invert() {
        if (mInverted == null) {
            mInverted = new InvertedConverter<V, T>(this);
        }
        return mInverted;
    }

    private static class InvertedConverter<From, To> extends BidirectionalTypeConverter<From, To> {
        private final BidirectionalTypeConverter<To, From> mSource;

        InvertedConverter(BidirectionalTypeConverter<To, From> source) {
            super(source.mToType, source.mFromType);
            mSource = source;
        }

        @Override
        public To convert(From value) { return mSource.convertBack(value); }

        @Override
        public From convertBack(To value) { return mSource.convert(value); }
    }
}
