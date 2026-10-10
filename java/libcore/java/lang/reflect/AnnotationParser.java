package java.lang.reflect;

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationFormatError;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds annotation proxies from the dex. Framework-internal: Android
 * apps call {@link Class#getAnnotation} and {@link AnnotatedElement},
 * not this class. A runtime annotation arrives as a map whose
 * {@code @type} entry is the annotation class. Nested annotations are
 * maps, and arrays of them are object arrays of maps.
 */
public final class AnnotationParser {
    public static final int KIND_CLASS = 0;
    public static final int KIND_FIELD = 1;
    public static final int KIND_METHOD = 2;

    private static final String TYPE_KEY = "@type";

    private AnnotationParser() {}

    private static native Object[] readNative(Class<?> owner, int kind, long token);

    private static native Object readDefaultNative(long token);

    private static native String readSignature(Class<?> owner, int kind, long token);

    /** The generic signature (dalvik.annotation.Signature) of a class, field or method, or null. */
    public static String signature(Class<?> owner, int kind, long token) {
        return readSignature(owner, kind, token);
    }

    public static <T extends Annotation> T findClass(Class<?> owner, Class<T> type) {
        if (type == null) throw new NullPointerException("annotationClass");
        return findIn(readNative(owner, KIND_CLASS, 0), type);
    }

    public static <T extends Annotation> T findMember(int kind, long token, Class<T> type) {
        if (type == null) throw new NullPointerException("annotationClass");
        return findIn(readNative(null, kind, token), type);
    }

    /** True when {@code owner} declares {@code type} directly. Does not build proxies. */
    public static boolean declares(Class<?> owner, Class<? extends Annotation> type) {
        Object[] raw = readNative(owner, KIND_CLASS, 0);
        if (raw == null) return false;
        for (int i = 0; i < raw.length; i++) {
            Object ann = rawType(raw[i]);
            if (ann == type) return true;
        }
        return false;
    }

    public static Annotation[] allClass(Class<?> owner) {
        return allOf(readNative(owner, KIND_CLASS, 0));
    }

    public static Annotation[] allMember(int kind, long token) {
        return allOf(readNative(null, kind, token));
    }

    /** The default of an annotation member, or null when it has none. */
    public static Object defaultValue(long token, Class<?> expect) {
        Object raw = readDefaultNative(token);
        if (raw == null) return null;
        return realizeValue(raw, expect);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Annotation> T findIn(Object[] raw, Class<T> type) {
        if (raw == null) return null;
        for (int i = 0; i < raw.length; i++) {
            if (rawType(raw[i]) == type) return (T) realize(castMap(raw[i]));
        }
        return null;
    }

    private static Annotation[] allOf(Object[] raw) {
        if (raw == null || raw.length == 0) return new Annotation[0];
        Annotation[] out = new Annotation[raw.length];
        for (int i = 0; i < raw.length; i++) out[i] = realize(castMap(raw[i]));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object raw) {
        return (Map<String, Object>) raw;
    }

    private static Object rawType(Object raw) {
        return castMap(raw).get(TYPE_KEY);
    }

    @SuppressWarnings("unchecked")
    private static Annotation realize(Map<String, Object> raw) {
        Class<?> type = (Class<?>) raw.get(TYPE_KEY);
        if (type == null || !type.isAnnotation()) {
            throw new AnnotationFormatError("not an annotation: " + type);
        }
        LinkedHashMap<String, Object> members = new LinkedHashMap<String, Object>();
        Method[] methods = type.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (m.getParameterCount() != 0 || m.getName().charAt(0) == '<') continue;
            if (m.getReturnType() == void.class) continue;
            Object value;
            if (raw.containsKey(m.getName())) value = realizeValue(raw.get(m.getName()), m.getReturnType());
            else value = m.getDefaultValue();
            if (value == null) {
                throw new AnnotationFormatError("missing element " + type.getName() + "." + m.getName());
            }
            members.put(m.getName(), value);
        }
        return (Annotation) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                new Handler(type, members));
    }

    static Object realizeValue(Object v, Class<?> expect) {
        if (v instanceof Map) return realize(castMap(v));
        if (v != null && v.getClass().isArray() && expect != null && expect.isArray()) {
            int n = Array.getLength(v);
            boolean nested = false;
            for (int i = 0; i < n; i++) {
                if (Array.get(v, i) instanceof Map) {
                    nested = true;
                    break;
                }
            }
            Class<?> comp = expect.getComponentType();
            if (nested || comp.isAnnotation()) {
                Object arr = Array.newInstance(comp, n);
                for (int i = 0; i < n; i++) Array.set(arr, i, realizeValue(Array.get(v, i), comp));
                return arr;
            }
        }
        return v;
    }

    private static final class Handler implements InvocationHandler {
        private final Class<?> type;
        private final Map<String, Object> members;

        Handler(Class<?> type, Map<String, Object> members) {
            this.type = type;
            this.members = members;
        }

        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            Class<?>[] params = method.getParameterTypes();
            if (name.equals("equals") && params.length == 1) {
                return Boolean.valueOf(equalsImpl(args == null ? null : args[0]));
            }
            if (name.equals("hashCode") && params.length == 0) return Integer.valueOf(hashCodeImpl());
            if (name.equals("toString") && params.length == 0) return toStringImpl();
            if (name.equals("annotationType") && params.length == 0) return type;
            Object v = members.get(name);
            if (v == null) throw new AnnotationFormatError("missing element " + type.getName() + "." + name);
            return v.getClass().isArray() ? cloneArray(v) : v;
        }

        private boolean equalsImpl(Object o) {
            if (o == null || !type.isInstance(o)) return false;
            for (Map.Entry<String, Object> e : members.entrySet()) {
                Object theirs;
                try {
                    theirs = type.getDeclaredMethod(e.getKey()).invoke(o);
                } catch (ReflectiveOperationException ex) {
                    return false;
                }
                if (!memberEquals(e.getValue(), theirs)) return false;
            }
            return true;
        }

        private int hashCodeImpl() {
            int h = 0;
            for (Map.Entry<String, Object> e : members.entrySet()) {
                h += (127 * e.getKey().hashCode()) ^ memberHash(e.getValue());
            }
            return h;
        }

        private String toStringImpl() {
            StringBuilder sb = new StringBuilder();
            sb.append('@').append(type.getName()).append('(');
            ArrayList<String> keys = new ArrayList<String>(members.keySet());
            Collections.sort(keys);
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sb.append(", ");
                String k = keys.get(i);
                sb.append(k).append('=').append(memberToString(members.get(k)));
            }
            return sb.append(')').toString();
        }
    }

    private static boolean memberEquals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof Object[]) return Arrays.equals((Object[]) a, (Object[]) b);
        if (a instanceof byte[]) return Arrays.equals((byte[]) a, (byte[]) b);
        if (a instanceof short[]) return Arrays.equals((short[]) a, (short[]) b);
        if (a instanceof int[]) return Arrays.equals((int[]) a, (int[]) b);
        if (a instanceof long[]) return Arrays.equals((long[]) a, (long[]) b);
        if (a instanceof char[]) return Arrays.equals((char[]) a, (char[]) b);
        if (a instanceof float[]) return Arrays.equals((float[]) a, (float[]) b);
        if (a instanceof double[]) return Arrays.equals((double[]) a, (double[]) b);
        if (a instanceof boolean[]) return Arrays.equals((boolean[]) a, (boolean[]) b);
        return a.equals(b);
    }

    private static int memberHash(Object v) {
        if (v instanceof Object[]) return Arrays.hashCode((Object[]) v);
        if (v instanceof byte[]) return Arrays.hashCode((byte[]) v);
        if (v instanceof short[]) return Arrays.hashCode((short[]) v);
        if (v instanceof int[]) return Arrays.hashCode((int[]) v);
        if (v instanceof long[]) return Arrays.hashCode((long[]) v);
        if (v instanceof char[]) return Arrays.hashCode((char[]) v);
        if (v instanceof float[]) return Arrays.hashCode((float[]) v);
        if (v instanceof double[]) return Arrays.hashCode((double[]) v);
        if (v instanceof boolean[]) return Arrays.hashCode((boolean[]) v);
        return v.hashCode();
    }

    private static String memberToString(Object v) {
        if (v instanceof Object[]) return Arrays.toString((Object[]) v);
        if (v instanceof byte[]) return Arrays.toString((byte[]) v);
        if (v instanceof short[]) return Arrays.toString((short[]) v);
        if (v instanceof int[]) return Arrays.toString((int[]) v);
        if (v instanceof long[]) return Arrays.toString((long[]) v);
        if (v instanceof char[]) return Arrays.toString((char[]) v);
        if (v instanceof float[]) return Arrays.toString((float[]) v);
        if (v instanceof double[]) return Arrays.toString((double[]) v);
        if (v instanceof boolean[]) return Arrays.toString((boolean[]) v);
        return String.valueOf(v);
    }

    private static Object cloneArray(Object v) {
        if (v instanceof Object[]) return ((Object[]) v).clone();
        if (v instanceof byte[]) return ((byte[]) v).clone();
        if (v instanceof short[]) return ((short[]) v).clone();
        if (v instanceof int[]) return ((int[]) v).clone();
        if (v instanceof long[]) return ((long[]) v).clone();
        if (v instanceof char[]) return ((char[]) v).clone();
        if (v instanceof float[]) return ((float[]) v).clone();
        if (v instanceof double[]) return ((double[]) v).clone();
        if (v instanceof boolean[]) return ((boolean[]) v).clone();
        return v;
    }

    /*
     * The annotations of one type among `all`, looking through the container of a @Repeatable type when the
     * type itself is absent (AnnotatedElement.getAnnotationsByType semantics). Framework-internal.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T[] byType(Annotation[] all, Class<T> type) {
        if (type == null) {
            throw new NullPointerException("annotationClass");
        }
        java.util.ArrayList<T> out = new java.util.ArrayList<T>();
        for (Annotation a : all) {
            if (type.isInstance(a)) {
                out.add(type.cast(a));
            }
        }
        if (out.isEmpty()) {
            java.lang.annotation.Repeatable r = type.getAnnotation(java.lang.annotation.Repeatable.class);
            if (r != null) {
                for (Annotation a : all) {
                    if (r.value().isInstance(a)) {
                        try {
                            Method value = a.annotationType().getMethod("value");
                            Object[] items = (Object[]) value.invoke(a);
                            for (Object o : items) {
                                out.add(type.cast(o));
                            }
                        } catch (ReflectiveOperationException e) {
                            // a container without value() holds nothing we can return
                        }
                    }
                }
            }
        }
        T[] result = (T[]) Array.newInstance(type, out.size());
        return out.toArray(result);
    }
}
