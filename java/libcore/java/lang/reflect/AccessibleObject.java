package java.lang.reflect;

import java.lang.annotation.Annotation;

public class AccessibleObject implements AnnotatedElement {
    boolean accessible;

    protected AccessibleObject() {
    }

    public static void setAccessible(AccessibleObject[] array, boolean flag) {
        for (AccessibleObject o : array) {
            o.accessible = flag;
        }
    }

    public void setAccessible(boolean flag) {
        accessible = flag;
    }

    public boolean isAccessible() {
        return accessible;
    }

    public boolean trySetAccessible() {
        accessible = true;
        return true;
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return null;
    }

    public Annotation[] getAnnotations() {
        return new Annotation[0];
    }

    public Annotation[] getDeclaredAnnotations() {
        return new Annotation[0];
    }
}
