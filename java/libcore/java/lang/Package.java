package java.lang;

public class Package implements java.lang.reflect.AnnotatedElement {
    private final String name;

    Package(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Package getPackage(String name) {
        return new Package(name);
    }

    public String getImplementationVersion() {
        return null;
    }

    public String getSpecificationVersion() {
        return null;
    }

    public String toString() {
        return "package " + name;
    }

    public int hashCode() {
        return name.hashCode();
    }

    public String getImplementationTitle() {
        return null;
    }

    public String getImplementationVendor() {
        return null;
    }

    public String getSpecificationTitle() {
        return null;
    }

    public String getSpecificationVendor() {
        return null;
    }

    public boolean isSealed() {
        return false;
    }

    public boolean isSealed(java.net.URL url) {
        return false;
    }

    public boolean isCompatibleWith(String desired) throws NumberFormatException {
        throw new NumberFormatException("Empty version string");
    }

    public static Package[] getPackages() {
        return new Package[0];
    }

    /* Package annotations (package-info) are not read. */
    public <A extends java.lang.annotation.Annotation> A getAnnotation(Class<A> annotationClass) {
        if (annotationClass == null) {
            throw new NullPointerException("annotationClass");
        }
        return null;
    }

    public boolean isAnnotationPresent(Class<? extends java.lang.annotation.Annotation> annotationClass) {
        return getAnnotation(annotationClass) != null;
    }

    public <A extends java.lang.annotation.Annotation> A[] getAnnotationsByType(Class<A> annotationClass) {
        return java.lang.reflect.AnnotationParser.byType(getAnnotations(), annotationClass);
    }

    public <A extends java.lang.annotation.Annotation> A getDeclaredAnnotation(Class<A> annotationClass) {
        return getAnnotation(annotationClass);
    }

    public <A extends java.lang.annotation.Annotation> A[] getDeclaredAnnotationsByType(
            Class<A> annotationClass) {
        return getAnnotationsByType(annotationClass);
    }

    public java.lang.annotation.Annotation[] getAnnotations() {
        return new java.lang.annotation.Annotation[0];
    }

    public java.lang.annotation.Annotation[] getDeclaredAnnotations() {
        return getAnnotations();
    }
}
