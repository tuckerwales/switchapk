package java.lang.reflect;

public abstract class Executable extends AccessibleObject implements Member, GenericDeclaration {
    long vmMethod;
    Class<?> declaringClass;
    String name;
    Class<?>[] parameterTypes;
    Class<?> returnType;
    int modifiers;

    Executable() {
    }

    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    public String getName() {
        return name;
    }

    public int getModifiers() {
        return modifiers & 0xffff;
    }

    public boolean isSynthetic() {
        return (modifiers & 0x1000) != 0;
    }

    public boolean isVarArgs() {
        return (modifiers & 0x80) != 0;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes.clone();
    }

    public int getParameterCount() {
        return parameterTypes.length;
    }

    public Type[] getGenericParameterTypes() {
        return getParameterTypes();
    }

    public Class<?>[] getExceptionTypes() {
        return new Class<?>[0];
    }

    public java.lang.annotation.Annotation[][] getParameterAnnotations() {
        return new java.lang.annotation.Annotation[parameterTypes.length][0];
    }

    public TypeVariable<?>[] getTypeParameters() {
        return new TypeVariable<?>[0];
    }

    String getSignatureKey() {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> c : parameterTypes) {
            sb.append(c.getName()).append(';');
        }
        return sb.append(')').toString();
    }

    static String typeName(Class<?> c) {
        return c.getTypeName();
    }
}
