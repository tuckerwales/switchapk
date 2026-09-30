package java.lang.reflect;

public final class Method extends Executable {
    private Method() {
    }

    public Class<?> getReturnType() {
        return returnType;
    }

    public Type getGenericReturnType() {
        return returnType;
    }

    public boolean isDefault() {
        return (modifiers & (Modifier.ABSTRACT | Modifier.STATIC | Modifier.PUBLIC)) == Modifier.PUBLIC
                && declaringClass.isInterface();
    }

    public boolean isBridge() {
        return (modifiers & 0x40) != 0;
    }

    public Object getDefaultValue() {
        return null;
    }

    public String getSignatureKey() {
        return super.getSignatureKey();
    }

    public Object invoke(Object receiver, Object... args)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (args == null) {
            args = new Object[0];
        }
        if (args.length != parameterTypes.length) {
            throw new IllegalArgumentException("Wrong number of arguments; expected " + parameterTypes.length
                    + ", got " + args.length);
        }
        if (!Modifier.isStatic(modifiers)) {
            if (receiver == null) {
                throw new NullPointerException("null receiver");
            }
            if (!declaringClass.isInstance(receiver)) {
                throw new IllegalArgumentException("Expected receiver of type " + declaringClass.getName()
                        + ", but got " + receiver.getClass().getName());
            }
        }
        return invokeNative(receiver, args);
    }

    private native Object invokeNative(Object receiver, Object[] args) throws InvocationTargetException;

    public boolean equals(Object obj) {
        return obj instanceof Method && ((Method) obj).vmMethod == vmMethod;
    }

    public int hashCode() {
        return declaringClass.getName().hashCode() ^ name.hashCode();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        String mods = Modifier.toString(getModifiers());
        if (!mods.isEmpty()) {
            sb.append(mods).append(' ');
        }
        sb.append(typeName(returnType)).append(' ').append(typeName(declaringClass)).append('.').append(name).append('(');
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(typeName(parameterTypes[i]));
        }
        return sb.append(')').toString();
    }
}
