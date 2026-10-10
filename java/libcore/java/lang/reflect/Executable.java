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

    /** The parsed generic signature, or null when the method or constructor is not generic. */
    libcore.reflect.GenericSignatureParser genericInfo() {
        String sig = AnnotationParser.signature(null, AnnotationParser.KIND_METHOD, vmMethod);
        if (sig == null) {
            return null;
        }
        libcore.reflect.GenericSignatureParser parser =
                new libcore.reflect.GenericSignatureParser(getDeclaringClass().getClassLoader());
        parser.parseForMethod((GenericDeclaration) this, sig);
        return parser;
    }

    public Type[] getGenericParameterTypes() {
        libcore.reflect.GenericSignatureParser p = genericInfo();
        return p != null && p.parameterTypes != null ? p.parameterTypes : getParameterTypes();
    }

    public Type[] getGenericExceptionTypes() {
        libcore.reflect.GenericSignatureParser p = genericInfo();
        return p != null && p.exceptionTypes != null ? p.exceptionTypes : getExceptionTypes();
    }

    public Class<?>[] getExceptionTypes() {
        return new Class<?>[0];
    }

    public java.lang.annotation.Annotation[][] getParameterAnnotations() {
        return new java.lang.annotation.Annotation[parameterTypes.length][0];
    }

    public TypeVariable<?>[] getTypeParameters() {
        libcore.reflect.GenericSignatureParser p = genericInfo();
        return p != null ? p.formalTypeParameters.clone() : new TypeVariable<?>[0];
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
