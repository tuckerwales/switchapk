package android.animation;

public abstract class TypeConverter<T, V> {
    final Class<T> mFromType;
    final Class<V> mToType;

    public TypeConverter(Class<T> fromType, Class<V> toType) {
        mFromType = fromType;
        mToType = toType;
    }

    public abstract V convert(T value);
}
