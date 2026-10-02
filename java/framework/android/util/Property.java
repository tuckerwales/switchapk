package android.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public abstract class Property<T, V> {
    private final String mName;
    private final Class<V> mType;

    public static <T, V> Property<T, V> of(Class<T> hostType, Class<V> valueType, String name) {
        return new ReflectiveProperty<T, V>(hostType, valueType, name);
    }

    public Property(Class<V> type, String name) {
        mName = name;
        mType = type;
    }

    public boolean isReadOnly() { return false; }
    public void set(T object, V value) { throw new UnsupportedOperationException("Property " + getName() + " is read-only"); }
    public abstract V get(T object);
    public String getName() { return mName; }
    public Class<V> getType() { return mType; }

    static class ReflectiveProperty<T, V> extends Property<T, V> {
        private Method mSetter, mGetter;
        private Field mField;

        ReflectiveProperty(Class<T> propertyHolder, Class<V> valueType, String name) {
            super(valueType, name);
            String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            try {
                mGetter = propertyHolder.getMethod("get" + cap);
            } catch (NoSuchMethodException e) {
                try {
                    mGetter = propertyHolder.getMethod("is" + cap);
                } catch (NoSuchMethodException e2) {
                    try {
                        mField = propertyHolder.getField(name);
                    } catch (NoSuchFieldException e3) {
                        throw new NoSuchPropertyException("No accessor method or field found for property with name " + name);
                    }
                }
            }
            Class<?> primitive = primitiveOf(valueType);
            try {
                mSetter = propertyHolder.getMethod("set" + cap, valueType);
            } catch (NoSuchMethodException e) {
                if (primitive != null) {
                    try {
                        mSetter = propertyHolder.getMethod("set" + cap, primitive);
                    } catch (NoSuchMethodException ignored) {}
                }
            }
        }

        private static Class<?> primitiveOf(Class<?> type) {
            if (type == Float.class) return float.class;
            if (type == Integer.class) return int.class;
            if (type == Boolean.class) return boolean.class;
            if (type == Long.class) return long.class;
            if (type == Double.class) return double.class;
            if (type == Short.class) return short.class;
            if (type == Byte.class) return byte.class;
            if (type == Character.class) return char.class;
            return null;
        }

        @Override
        public void set(T object, V value) {
            try {
                if (mSetter != null) mSetter.invoke(object, value);
                else if (mField != null) mField.set(object, value);
                else throw new UnsupportedOperationException("Property " + getName() + " is read-only");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public V get(T object) {
            try {
                if (mGetter != null) return (V) mGetter.invoke(object);
                return (V) mField.get(object);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean isReadOnly() { return mSetter == null && mField == null; }
    }
}
