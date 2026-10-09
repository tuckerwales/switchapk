package java.lang.reflect;

import java.lang.annotation.Annotation;

public final class Parameter implements AnnotatedElement {
    private final String name;
    private final int modifiers;
    private final Executable executable;
    private final int index;

    Parameter(String name, int modifiers, Executable executable, int index) {
        this.name = name;
        this.modifiers = modifiers;
        this.executable = executable;
        this.index = index;
    }

    public boolean equals(Object obj) {
        if (obj instanceof Parameter) {
            Parameter other = (Parameter) obj;
            return other.executable.equals(executable) && other.index == index;
        }
        return false;
    }

    public int hashCode() {
        return executable.hashCode() ^ index;
    }

    public boolean isNamePresent() {
        return false;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        String mods = Modifier.toString(getModifiers());
        if (!mods.isEmpty()) {
            sb.append(mods).append(' ');
        }
        if (isVarArgs()) {
            sb.append(getType().getComponentType().getTypeName()).append("...");
        } else {
            sb.append(getType().getTypeName());
        }
        return sb.append(' ').append(name).toString();
    }

    public Executable getDeclaringExecutable() {
        return executable;
    }

    public int getModifiers() {
        return modifiers;
    }

    public String getName() {
        return name;
    }

    public Type getParameterizedType() {
        return executable.getGenericParameterTypes()[index];
    }

    public Class<?> getType() {
        return executable.getParameterTypes()[index];
    }

    public boolean isImplicit() {
        return false;
    }

    public boolean isSynthetic() {
        return false;
    }

    public boolean isVarArgs() {
        return executable.isVarArgs() && index == executable.getParameterCount() - 1;
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (annotationClass == null) {
            throw new NullPointerException("annotationClass");
        }
        for (Annotation a : getDeclaredAnnotations()) {
            if (annotationClass.isInstance(a)) {
                return annotationClass.cast(a);
            }
        }
        return null;
    }

    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        return AnnotationParser.byType(getDeclaredAnnotations(), annotationClass);
    }

    public Annotation[] getDeclaredAnnotations() {
        return executable.getParameterAnnotations()[index];
    }

    public <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
        return getAnnotation(annotationClass);
    }

    public <T extends Annotation> T[] getDeclaredAnnotationsByType(Class<T> annotationClass) {
        return getAnnotationsByType(annotationClass);
    }

    public Annotation[] getAnnotations() {
        return getDeclaredAnnotations();
    }
}
