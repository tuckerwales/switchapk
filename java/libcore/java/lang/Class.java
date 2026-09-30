package java.lang;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashMap;

public final class Class<T> implements java.io.Serializable, GenericDeclaration, Type {
    private transient long vmClass;
    private transient String name;
    private transient Method[] declaredMethods;
    private transient Constructor<?>[] declaredConstructors;
    private transient Field[] declaredFields;
    private transient HashMap<String, T> enumDirectory;

    private Class() {
    }

    public static Class<?> forName(String className) throws ClassNotFoundException {
        return classForName(className, true);
    }

    public static Class<?> forName(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        return classForName(name, initialize);
    }

    static native Class<?> classForName(String className, boolean initialize) throws ClassNotFoundException;

    static native Class<?> getPrimitiveClass(String name);

    private native String getNameNative();

    public String getName() {
        String n = name;
        if (n == null) {
            n = getNameNative();
            name = n;
        }
        return n;
    }

    public String getSimpleName() {
        if (isArray()) {
            return getComponentType().getSimpleName() + "[]";
        }
        if (isInnerClassNative()) {
            String inner = getInnerClassName();
            return inner == null ? "" : inner;
        }
        String n = getName();
        int dot = n.lastIndexOf('.');
        return dot >= 0 ? n.substring(dot + 1) : n;
    }

    public String getCanonicalName() {
        if (isArray()) {
            String c = getComponentType().getCanonicalName();
            return c == null ? null : c + "[]";
        }
        if (isAnonymousClass() || isLocalClass()) {
            return null;
        }
        return getName().replace('$', '.');
    }

    public String getTypeName() {
        if (isArray()) {
            return getComponentType().getTypeName() + "[]";
        }
        return getName();
    }

    private native String getInnerClassName();

    private native boolean isInnerClassNative();

    private native Class<?> getEnclosingClassNative();

    public boolean isAnonymousClass() {
        return isInnerClassNative() && getInnerClassName() == null;
    }

    public boolean isLocalClass() {
        return false;
    }

    public boolean isMemberClass() {
        return isInnerClassNative() && getInnerClassName() != null;
    }

    public Class<?> getEnclosingClass() {
        return getEnclosingClassNative();
    }

    public Class<?> getDeclaringClass() {
        return isMemberClass() ? getEnclosingClassNative() : null;
    }

    public native Class<? super T> getSuperclass();

    public Type getGenericSuperclass() {
        return getSuperclass();
    }

    public Type[] getGenericInterfaces() {
        return getInterfaces();
    }

    public native Class<?>[] getInterfaces();

    public native Class<?> getComponentType();

    public native boolean isArray();

    public native boolean isPrimitive();

    public native boolean isInterface();

    public boolean isEnum() {
        return (getModifiers() & 0x4000) != 0 && getSuperclass() == Enum.class;
    }

    public boolean isAnnotation() {
        return (getModifiers() & 0x2000) != 0;
    }

    public boolean isSynthetic() {
        return (getModifiers() & 0x1000) != 0;
    }

    public native int getModifiers();

    public native boolean isInstance(Object obj);

    public native boolean isAssignableFrom(Class<?> cls);

    public native T newInstance() throws InstantiationException, IllegalAccessException;

    public native boolean desiredAssertionStatus();

    @SuppressWarnings("unchecked")
    public T cast(Object obj) {
        if (obj != null && !isInstance(obj)) {
            throw new ClassCastException("Cannot cast " + obj.getClass().getName() + " to " + getName());
        }
        return (T) obj;
    }

    @SuppressWarnings("unchecked")
    public <U> Class<? extends U> asSubclass(Class<U> clazz) {
        if (clazz.isAssignableFrom(this)) {
            return (Class<? extends U>) this;
        }
        throw new ClassCastException(toString());
    }

    public ClassLoader getClassLoader() {
        return ClassLoader.getSystemClassLoader();
    }

    public Package getPackage() {
        String n = getName();
        int dot = n.lastIndexOf('.');
        return new Package(dot >= 0 ? n.substring(0, dot) : "");
    }

    public String getPackageName() {
        String n = getName();
        int dot = n.lastIndexOf('.');
        return dot >= 0 ? n.substring(0, dot) : "";
    }

    @SuppressWarnings("unchecked")
    public T[] getEnumConstants() {
        if (!isEnum()) {
            return null;
        }
        try {
            Method values = getDeclaredMethod("values");
            return (T[]) values.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    HashMap<String, T> enumConstantDirectory() {
        if (enumDirectory == null) {
            T[] values = getEnumConstants();
            if (values == null) {
                throw new IllegalArgumentException(getName() + " is not an enum type");
            }
            HashMap<String, T> m = new HashMap<String, T>();
            for (T v : values) {
                m.put(((Enum<?>) v).name(), v);
            }
            enumDirectory = m;
        }
        return enumDirectory;
    }

    public InputStream getResourceAsStream(String name) {
        return getClassLoader().getResourceAsStream(resolveName(name));
    }

    public java.net.URL getResource(String name) {
        return null;
    }

    private String resolveName(String name) {
        if (name.startsWith("/")) {
            return name.substring(1);
        }
        String pkg = getPackageName();
        return pkg.isEmpty() ? name : pkg.replace('.', '/') + "/" + name;
    }

    /* ---- reflection ---- */

    private native Method[] getDeclaredMethodsNative();

    private native Constructor<?>[] getDeclaredConstructorsNative();

    private native Field[] getDeclaredFieldsNative();

    public Method[] getDeclaredMethods() {
        if (declaredMethods == null) {
            declaredMethods = getDeclaredMethodsNative();
        }
        return declaredMethods.clone();
    }

    @SuppressWarnings("unchecked")
    public Constructor<T>[] getDeclaredConstructors() {
        if (declaredConstructors == null) {
            declaredConstructors = getDeclaredConstructorsNative();
        }
        return (Constructor<T>[]) declaredConstructors.clone();
    }

    public Field[] getDeclaredFields() {
        if (declaredFields == null) {
            declaredFields = getDeclaredFieldsNative();
        }
        return declaredFields.clone();
    }

    public Method[] getMethods() {
        ArrayList<Method> out = new ArrayList<Method>();
        HashMap<String, Boolean> seen = new HashMap<String, Boolean>();
        collectPublicMethods(this, out, seen);
        return out.toArray(new Method[out.size()]);
    }

    private static void collectPublicMethods(Class<?> c, ArrayList<Method> out, HashMap<String, Boolean> seen) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                String key = m.getName() + m.getSignatureKey();
                if (seen.containsKey(key)) {
                    continue;
                }
                seen.put(key, Boolean.TRUE);
                out.add(m);
            }
            for (Class<?> i : k.getInterfaces()) {
                collectPublicMethods(i, out, seen);
            }
        }
    }

    public Field[] getFields() {
        ArrayList<Field> out = new ArrayList<Field>();
        for (Class<?> k = this; k != null; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isPublic(f.getModifiers())) {
                    out.add(f);
                }
            }
            for (Class<?> i : k.getInterfaces()) {
                for (Field f : i.getFields()) {
                    out.add(f);
                }
            }
        }
        return out.toArray(new Field[out.size()]);
    }

    @SuppressWarnings("unchecked")
    public Constructor<T>[] getConstructors() {
        ArrayList<Constructor<T>> out = new ArrayList<Constructor<T>>();
        for (Constructor<T> c : getDeclaredConstructors()) {
            if (Modifier.isPublic(c.getModifiers())) {
                out.add(c);
            }
        }
        return out.toArray(new Constructor[out.size()]);
    }

    private static boolean paramsMatch(Class<?>[] a, Class<?>[] b) {
        if (b == null) {
            b = new Class<?>[0];
        }
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    public Method getDeclaredMethod(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method best = null;
        for (Method m : getDeclaredMethods()) {
            if (m.getName().equals(name) && paramsMatch(m.getParameterTypes(), parameterTypes)) {
                if (best == null || best.getReturnType().isAssignableFrom(m.getReturnType())) {
                    best = m;
                }
            }
        }
        if (best == null) {
            throw new NoSuchMethodException(getName() + "." + name);
        }
        return best;
    }

    public Method getMethod(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        for (Class<?> k = this; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (Modifier.isPublic(m.getModifiers()) && m.getName().equals(name)
                        && paramsMatch(m.getParameterTypes(), parameterTypes)) {
                    return m;
                }
            }
        }
        for (Method m : getMethods()) {
            if (m.getName().equals(name) && paramsMatch(m.getParameterTypes(), parameterTypes)) {
                return m;
            }
        }
        throw new NoSuchMethodException(getName() + "." + name);
    }

    public Constructor<T> getDeclaredConstructor(Class<?>... parameterTypes) throws NoSuchMethodException {
        for (Constructor<T> c : getDeclaredConstructors()) {
            if (paramsMatch(c.getParameterTypes(), parameterTypes)) {
                return c;
            }
        }
        throw new NoSuchMethodException(getName() + ".<init>");
    }

    public Constructor<T> getConstructor(Class<?>... parameterTypes) throws NoSuchMethodException {
        Constructor<T> c = getDeclaredConstructor(parameterTypes);
        if (!Modifier.isPublic(c.getModifiers())) {
            throw new NoSuchMethodException(getName() + ".<init>");
        }
        return c;
    }

    public Field getDeclaredField(String name) throws NoSuchFieldException {
        for (Field f : getDeclaredFields()) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        throw new NoSuchFieldException(name);
    }

    public Field getField(String name) throws NoSuchFieldException {
        for (Field f : getFields()) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        throw new NoSuchFieldException(name);
    }

    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        return null;
    }

    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return false;
    }

    public Annotation[] getAnnotations() {
        return new Annotation[0];
    }

    public Annotation[] getDeclaredAnnotations() {
        return new Annotation[0];
    }

    @SuppressWarnings("unchecked")
    public TypeVariable<Class<T>>[] getTypeParameters() {
        return new TypeVariable[0];
    }

    public Class<?>[] getDeclaredClasses() {
        return new Class<?>[0];
    }

    public Class<?>[] getClasses() {
        return new Class<?>[0];
    }

    public String toString() {
        if (isPrimitive()) {
            return getName();
        }
        return (isInterface() ? "interface " : "class ") + getName();
    }
}
