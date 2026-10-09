package java.lang.reflect;

import java.lang.annotation.Annotation;

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

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return AnnotationParser.findMember(AnnotationParser.KIND_METHOD, vmMethod, annotationClass);
    }

    public Annotation[] getDeclaredAnnotations() {
        return AnnotationParser.allMember(AnnotationParser.KIND_METHOD, vmMethod);
    }

    public Annotation[] getAnnotations() {
        return getDeclaredAnnotations();
    }

    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getAnnotation(annotationClass) != null;
    }

    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        return AnnotationParser.byType(getAnnotations(), annotationClass);
    }

    public Type[] getGenericExceptionTypes() {
        return getExceptionTypes();
    }

    /* Generic signatures are not read, so this is toString(). */
    public abstract String toGenericString();

    /* Dex files built without -parameters carry no names, so parameters are arg0, arg1, ... as on Android. */
    public Parameter[] getParameters() {
        Parameter[] out = new Parameter[parameterTypes.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = new Parameter("arg" + i, 0, this, i);
        }
        return out;
    }
}
