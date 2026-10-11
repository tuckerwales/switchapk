import java.lang.reflect.*;
import java.util.*;

/**
 * Generic signatures through reflection (Gson TypeToken, libGDX Json, Moshi and Kotlin reflection rely on them):
 * run on OpenJDK and on switchapk by tests/run_dex_test.sh, output must match.
 */
public class GenericsTest {
    static abstract class TypeToken<T> {
        final Type type;

        TypeToken() {
            Type sup = getClass().getGenericSuperclass();
            type = ((ParameterizedType) sup).getActualTypeArguments()[0];
        }
    }

    static class Outer<O> {
        class Inner<I> {
            O o;
            I i;
        }

        static class Nested<N extends Number & Comparable<N>> {
            N n;
        }
    }

    interface Shape<S> {
    }

    static class Box<T extends Comparable<? super T>> extends ArrayList<T> implements Shape<Map<String, T[]>>, Comparable<Box<T>> {
        List<? extends Number> numbers;
        Map<String, List<int[]>> nested;
        T[] array;
        List<?> wildcard;
        Outer<String>.Inner<Integer> inner;
        Map.Entry<String, Long> entry;
        Outer.Nested<Integer> nestedStatic;
        int plain;
        String raw;

        public int compareTo(Box<T> o) {
            return 0;
        }

        <E extends Exception, R> R call(List<? super T> in, Class<R> type) throws E {
            return null;
        }

        Box(List<T> init) {
        }
    }

    static void show(String label, Type t) {
        System.out.println(label + ": " + t.getTypeName() + " [" + kind(t) + "]");
    }

    static String kind(Type t) {
        if (t instanceof Class) {
            return "Class";
        }
        if (t instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType) t;
            StringBuilder sb = new StringBuilder("Parameterized raw=" + ((Class<?>) p.getRawType()).getName() + " owner="
                    + (p.getOwnerType() == null ? "null" : p.getOwnerType().getTypeName()) + " args=");
            for (Type a : p.getActualTypeArguments()) {
                sb.append('{').append(kind(a)).append('}');
            }
            return sb.toString();
        }
        if (t instanceof GenericArrayType) {
            return "Array of " + kind(((GenericArrayType) t).getGenericComponentType());
        }
        if (t instanceof WildcardType) {
            WildcardType w = (WildcardType) t;
            return "Wildcard upper=" + Arrays.toString(w.getUpperBounds()) + " lower=" + Arrays.toString(w.getLowerBounds());
        }
        if (t instanceof TypeVariable) {
            TypeVariable<?> v = (TypeVariable<?>) t;
            Object d = v.getGenericDeclaration();
            String dn = d instanceof Class ? ((Class<?>) d).getSimpleName() : ((Member) d).getName();
            StringBuilder sb = new StringBuilder("Var " + v.getName() + " of " + dn + " bounds=");
            for (Type b : v.getBounds()) {
                sb.append(b.getTypeName()).append(';');
            }
            return sb.toString();
        }
        return t.getClass().getName();
    }

    public static void main(String[] args) throws Exception {
        show("token list", new TypeToken<List<String>>() {}.type);
        show("token map", new TypeToken<Map<String, List<Integer>>>() {}.type);
        show("token array", new TypeToken<List<String>[]>() {}.type);
        show("token class", new TypeToken<String>() {}.type);

        Class<?> box = Box.class;
        show("superclass", box.getGenericSuperclass());
        for (Type t : box.getGenericInterfaces()) {
            show("interface", t);
        }
        for (TypeVariable<?> v : box.getTypeParameters()) {
            show("type param", v);
        }
        for (String f : new String[] {"numbers", "nested", "array", "wildcard", "inner", "entry", "nestedStatic", "plain", "raw"}) {
            show("field " + f, box.getDeclaredField(f).getGenericType());
        }
        Method call = box.getDeclaredMethod("call", List.class, Class.class);
        for (TypeVariable<?> v : call.getTypeParameters()) {
            show("method param", v);
        }
        for (Type t : call.getGenericParameterTypes()) {
            show("call arg", t);
        }
        show("call returns", call.getGenericReturnType());
        for (Type t : call.getGenericExceptionTypes()) {
            show("call throws", t);
        }
        Constructor<?> ctor = box.getDeclaredConstructor(List.class);
        for (Type t : ctor.getGenericParameterTypes()) {
            show("ctor arg", t);
        }
        show("compareTo arg", box.getDeclaredMethod("compareTo", Box.class).getGenericParameterTypes()[0]);

        Type a = new TypeToken<Map<String, List<Integer>>>() {}.type;
        Type b = new TypeToken<Map<String, List<Integer>>>() {}.type;
        System.out.println("equal " + a.equals(b) + " hash " + (a.hashCode() == b.hashCode()));
        TypeVariable<?> t1 = box.getTypeParameters()[0];
        TypeVariable<?> t2 = (TypeVariable<?>) ((GenericArrayType) box.getDeclaredField("array").getGenericType())
                .getGenericComponentType();
        System.out.println("var equal " + t1.equals(t2) + " decl " + (t2.getGenericDeclaration() == Box.class));
        show("inner field o", Outer.Inner.class.getDeclaredField("o").getGenericType());
        show("list superclass", ArrayList.class.getGenericSuperclass());
        show("string superclass", String.class.getGenericSuperclass());
        System.out.println("interface super " + Shape.class.getGenericSuperclass() + " object " + Object.class.getGenericSuperclass());
        System.out.println("int[] " + int[].class.getGenericSuperclass() + " params " + Arrays.toString(int.class.getTypeParameters()));
    }
}
