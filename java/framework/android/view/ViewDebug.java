package android.view;

import android.util.Log;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * View debug annotations and the captured-view dump. Hierarchy and
 * recycler tracing are deprecated no-ops, as on current Android.
 * Hardware capture and the view-server protocol are not implemented.
 */
public class ViewDebug {
    /** @deprecated unused */
    @Deprecated
    public static final boolean TRACE_HIERARCHY = false;

    /** @deprecated unused */
    @Deprecated
    public static final boolean TRACE_RECYCLER = false;

    /**
     * Marks a field or a no-arg method for {@link #dumpCapturedView}.
     */
    @Target({ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ExportedProperty {
        boolean resolveId() default false;

        IntToString[] mapping() default {};

        IntToString[] indexMapping() default {};

        FlagToString[] flagMapping() default {};

        boolean deepExport() default false;

        String prefix() default "";

        String category() default "";

        boolean formatToHexString() default false;

        boolean hasAdjacentMapping() default false;
    }

    /** Maps an int to a string inside {@link ExportedProperty#mapping}. */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IntToString {
        int from();

        String to();
    }

    /** Maps a masked int to a string inside {@link ExportedProperty#flagMapping}. */
    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface FlagToString {
        int mask();

        int equals();

        String name();

        boolean outputIf() default true;
    }

    /** Marks a field or a no-arg method for {@link #dumpCapturedView}. */
    @Target({ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CapturedViewProperty {
        boolean retrieveReturn() default false;
    }

    /** @deprecated unused */
    @Deprecated
    public enum HierarchyTraceType {
        INVALIDATE,
        INVALIDATE_CHILD,
        INVALIDATE_CHILD_IN_PARENT,
        REQUEST_LAYOUT,
        ON_LAYOUT,
        ON_MEASURE,
        DRAW,
        BUILD_CACHE
    }

    /** @deprecated unused */
    @Deprecated
    public enum RecyclerTraceType {
        NEW_VIEW,
        BIND_VIEW,
        RECYCLE_FROM_ACTIVE_HEAP,
        RECYCLE_FROM_SCRAP_HEAP,
        MOVE_TO_SCRAP_HEAP,
        MOVE_FROM_ACTIVE_TO_SCRAP_HEAP
    }

    private static HashMap<Class<?>, Method[]> sCapturedMethods;
    private static HashMap<Class<?>, Field[]> sCapturedFields;

    /** @deprecated no-op */
    @Deprecated
    public static void trace(View view, RecyclerTraceType type, int... parameters) {}

    /** @deprecated no-op */
    @Deprecated
    public static void startRecyclerTracing(String prefix, View view) {}

    /** @deprecated no-op */
    @Deprecated
    public static void stopRecyclerTracing() {}

    /** @deprecated no-op */
    @Deprecated
    public static void trace(View view, HierarchyTraceType type) {}

    /** @deprecated no-op */
    @Deprecated
    public static void startHierarchyTracing(String prefix, View view) {}

    /** @deprecated no-op */
    @Deprecated
    public static void stopHierarchyTracing() {}

    /**
     * Logs fields and no-arg methods marked {@link CapturedViewProperty}.
     * The line is {@code className: field=value method()=value; }.
     */
    public static void dumpCapturedView(String tag, Object view) {
        Class<?> klass = view.getClass();
        StringBuilder sb = new StringBuilder(klass.getName() + ": ");
        sb.append(exportFields(view, klass, ""));
        sb.append(exportMethods(view, klass, ""));
        Log.d(tag, sb.toString());
    }

    private static Field[] capturedFields(Class<?> klass) {
        if (sCapturedFields == null) sCapturedFields = new HashMap<Class<?>, Field[]>();
        Field[] cached = sCapturedFields.get(klass);
        if (cached != null) return cached;
        ArrayList<Field> found = new ArrayList<Field>();
        Field[] fields = klass.getFields();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (field.isAnnotationPresent(CapturedViewProperty.class)) {
                field.setAccessible(true);
                found.add(field);
            }
        }
        cached = found.toArray(new Field[found.size()]);
        sCapturedFields.put(klass, cached);
        return cached;
    }

    private static Method[] capturedMethods(Class<?> klass) {
        if (sCapturedMethods == null) sCapturedMethods = new HashMap<Class<?>, Method[]>();
        Method[] cached = sCapturedMethods.get(klass);
        if (cached != null) return cached;
        ArrayList<Method> found = new ArrayList<Method>();
        Method[] methods = klass.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (method.getParameterTypes().length == 0
                    && method.isAnnotationPresent(CapturedViewProperty.class)
                    && method.getReturnType() != Void.class) {
                method.setAccessible(true);
                found.add(method);
            }
        }
        cached = found.toArray(new Method[found.size()]);
        sCapturedMethods.put(klass, cached);
        return cached;
    }

    private static String exportMethods(Object obj, Class<?> klass, String prefix) {
        if (obj == null) return "null";
        StringBuilder sb = new StringBuilder();
        Method[] methods = capturedMethods(klass);
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            try {
                Object value = method.invoke(obj, (Object[]) null);
                CapturedViewProperty property = method.getAnnotation(CapturedViewProperty.class);
                if (property.retrieveReturn()) {
                    sb.append(exportMethods(value, method.getReturnType(), method.getName() + "#"));
                } else {
                    sb.append(prefix);
                    sb.append(method.getName());
                    sb.append("()=");
                    sb.append(value == null ? "null" : value.toString().replace("\n", "\\n"));
                    sb.append("; ");
                }
            } catch (IllegalAccessException e) {
                /* skip, as AOSP does */
            } catch (InvocationTargetException e) {
                /* skip, as AOSP does */
            }
        }
        return sb.toString();
    }

    private static String exportFields(Object obj, Class<?> klass, String prefix) {
        if (obj == null) return "null";
        StringBuilder sb = new StringBuilder();
        Field[] fields = capturedFields(klass);
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            try {
                Object value = field.get(obj);
                sb.append(prefix);
                sb.append(field.getName());
                sb.append('=');
                sb.append(value == null ? "null" : value.toString().replace("\n", "\\n"));
                sb.append(' ');
            } catch (IllegalAccessException e) {
                /* skip, as AOSP does */
            }
        }
        return sb.toString();
    }
}
