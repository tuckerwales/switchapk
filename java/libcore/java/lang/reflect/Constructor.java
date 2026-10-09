package java.lang.reflect;

public final class Constructor<T> extends Executable {
    private Constructor() {
    }

    @SuppressWarnings("unchecked")
    public Class<T> getDeclaringClass() {
        return (Class<T>) declaringClass;
    }

    public String getName() {
        return declaringClass.getName();
    }

    public T newInstance(Object... args)
            throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (args == null) {
            args = new Object[0];
        }
        if (args.length != parameterTypes.length) {
            throw new IllegalArgumentException("Wrong number of arguments; expected " + parameterTypes.length
                    + ", got " + args.length);
        }
        if (Modifier.isAbstract(declaringClass.getModifiers())) {
            throw new InstantiationException("Can't instantiate abstract class " + declaringClass.getName());
        }
        return newInstanceNative(args);
    }

    private native T newInstanceNative(Object[] args) throws InvocationTargetException;

    public boolean equals(Object obj) {
        return obj instanceof Constructor && ((Constructor<?>) obj).vmMethod == vmMethod;
    }

    public int hashCode() {
        return declaringClass.getName().hashCode();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        String mods = Modifier.toString(getModifiers());
        if (!mods.isEmpty()) {
            sb.append(mods).append(' ');
        }
        sb.append(typeName(declaringClass)).append('(');
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(typeName(parameterTypes[i]));
        }
        return sb.append(')').toString();
    }

    public String toGenericString() {
        return toString();
    }
}
