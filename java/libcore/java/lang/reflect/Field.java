package java.lang.reflect;

import java.lang.annotation.Annotation;

public final class Field extends AccessibleObject implements Member {
    private long vmField;
    private Class<?> declaringClass;
    private String name;
    private Class<?> type;
    private int modifiers;

    private Field() {
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

    public boolean isEnumConstant() {
        return (modifiers & 0x4000) != 0;
    }

    public boolean isSynthetic() {
        return (modifiers & 0x1000) != 0;
    }

    public Class<?> getType() {
        return type;
    }

    public Type getGenericType() {
        return type;
    }

    private void checkReceiver(Object obj) {
        if (!Modifier.isStatic(modifiers)) {
            if (obj == null) {
                throw new NullPointerException("null receiver");
            }
            if (!declaringClass.isInstance(obj)) {
                throw new IllegalArgumentException("Expected receiver of type " + declaringClass.getName());
            }
        }
    }

    public Object get(Object obj) throws IllegalArgumentException, IllegalAccessException {
        checkReceiver(obj);
        return getNative(obj);
    }

    private native Object getNative(Object obj);

    private native void setNative(Object obj, Object value);

    public void set(Object obj, Object value) throws IllegalArgumentException, IllegalAccessException {
        checkReceiver(obj);
        setNative(obj, value);
    }

    public boolean getBoolean(Object obj) throws IllegalAccessException {
        return ((Boolean) get(obj)).booleanValue();
    }

    public byte getByte(Object obj) throws IllegalAccessException {
        return ((Number) get(obj)).byteValue();
    }

    public char getChar(Object obj) throws IllegalAccessException {
        return ((Character) get(obj)).charValue();
    }

    public short getShort(Object obj) throws IllegalAccessException {
        return ((Number) get(obj)).shortValue();
    }

    public int getInt(Object obj) throws IllegalAccessException {
        Object v = get(obj);
        return v instanceof Character ? ((Character) v).charValue() : ((Number) v).intValue();
    }

    public long getLong(Object obj) throws IllegalAccessException {
        Object v = get(obj);
        return v instanceof Character ? ((Character) v).charValue() : ((Number) v).longValue();
    }

    public float getFloat(Object obj) throws IllegalAccessException {
        Object v = get(obj);
        return v instanceof Character ? ((Character) v).charValue() : ((Number) v).floatValue();
    }

    public double getDouble(Object obj) throws IllegalAccessException {
        Object v = get(obj);
        return v instanceof Character ? ((Character) v).charValue() : ((Number) v).doubleValue();
    }

    public void setBoolean(Object obj, boolean z) throws IllegalAccessException {
        set(obj, Boolean.valueOf(z));
    }

    public void setByte(Object obj, byte b) throws IllegalAccessException {
        set(obj, Byte.valueOf(b));
    }

    public void setChar(Object obj, char c) throws IllegalAccessException {
        set(obj, Character.valueOf(c));
    }

    public void setShort(Object obj, short s) throws IllegalAccessException {
        set(obj, Short.valueOf(s));
    }

    public void setInt(Object obj, int i) throws IllegalAccessException {
        set(obj, Integer.valueOf(i));
    }

    public void setLong(Object obj, long l) throws IllegalAccessException {
        set(obj, Long.valueOf(l));
    }

    public void setFloat(Object obj, float f) throws IllegalAccessException {
        set(obj, Float.valueOf(f));
    }

    public void setDouble(Object obj, double d) throws IllegalAccessException {
        set(obj, Double.valueOf(d));
    }

    public boolean equals(Object obj) {
        return obj instanceof Field && ((Field) obj).vmField == vmField;
    }

    public int hashCode() {
        return declaringClass.getName().hashCode() ^ name.hashCode();
    }

    public String toString() {
        String mods = Modifier.toString(getModifiers());
        return (mods.isEmpty() ? "" : mods + " ") + type.getTypeName() + " " + declaringClass.getTypeName() + "." + name;
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return AnnotationParser.findMember(AnnotationParser.KIND_FIELD, vmField, annotationClass);
    }

    public Annotation[] getDeclaredAnnotations() {
        return AnnotationParser.allMember(AnnotationParser.KIND_FIELD, vmField);
    }

    public Annotation[] getAnnotations() {
        return getDeclaredAnnotations();
    }
}
