package java.lang;

public abstract class Enum<E extends Enum<E>> implements Comparable<E>, java.io.Serializable {
    private final String name;
    private final int ordinal;

    protected Enum(String name, int ordinal) {
        this.name = name;
        this.ordinal = ordinal;
    }

    public final String name() {
        return name;
    }

    public final int ordinal() {
        return ordinal;
    }

    public String toString() {
        return name;
    }

    public final boolean equals(Object other) {
        return this == other;
    }

    public final int hashCode() {
        return super.hashCode();
    }

    protected final Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    public final int compareTo(E o) {
        Enum<?> other = (Enum<?>) o;
        return ordinal - other.ordinal;
    }

    @SuppressWarnings("unchecked")
    public final Class<E> getDeclaringClass() {
        Class<?> clazz = getClass();
        Class<?> zuper = clazz.getSuperclass();
        return (zuper == Enum.class) ? (Class<E>) clazz : (Class<E>) zuper;
    }

    public static <T extends Enum<T>> T valueOf(Class<T> enumType, String name) {
        if (name == null) {
            throw new NullPointerException("Name is null");
        }
        T result = enumType.enumConstantDirectory().get(name);
        if (result != null) {
            return result;
        }
        throw new IllegalArgumentException("No enum constant " + enumType.getCanonicalName() + "." + name);
    }

    protected final void finalize() {
    }
}
