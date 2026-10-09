package java.lang.reflect;

import java.lang.annotation.Annotation;

public interface AnnotatedElement {
    default boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getAnnotation(annotationClass) != null;
    }

    <T extends Annotation> T getAnnotation(Class<T> annotationClass);

    Annotation[] getAnnotations();

    Annotation[] getDeclaredAnnotations();

    default <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        return AnnotationParser.byType(getAnnotations(), annotationClass);
    }

    default <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
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

    default <T extends Annotation> T[] getDeclaredAnnotationsByType(Class<T> annotationClass) {
        return AnnotationParser.byType(getDeclaredAnnotations(), annotationClass);
    }
}
