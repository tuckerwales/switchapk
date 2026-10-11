// Generic reflection from signatures: fields, methods, constructors, classes, type variables, wildcards, arrays.
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;

public class GenericsTest {
    static class Base<K, V> {}
    interface Shape<T extends Comparable<T>> {}

    static class Holder<T extends Number & Comparable<T>> extends Base<String, List<T>> implements Shape<Integer>, Serializable {
        List<String> names;
        Map<String, List<Integer>> table;
        List<? extends Number> ups;
        List<? super Integer> downs;
        List<?> any;
        T value;
        T[] values;
        List<String>[] lists;
        int plain;
        String[] strings;
        Holder<T>.Inner inner;
        Map.Entry<String, T> entry;

        Holder(List<T> init, int n) throws IOException {}

        <E extends Exception> List<T> pick(Map<? super String, T[]> m, E[] errs, int i) throws E, IllegalStateException {
            return null;
        }

        static <A, B extends A> B cast(A a) { return null; }

        void raw(List l) {}

        class Inner {}
    }

    static class Chain extends ArrayList<Map<String, Integer>> {}

    static void p(Object o) { System.out.println(o); }

    static String describe(Type t) {
        if (t instanceof Class) return "C:" + ((Class<?>) t).getName();
        if (t instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) t;
            StringBuilder sb = new StringBuilder("P:" + describe(pt.getRawType()) + "[owner=" + (pt.getOwnerType() == null ? "-" : describe(pt.getOwnerType())) + "](");
            for (Type a : pt.getActualTypeArguments()) sb.append(describe(a)).append(',');
            return sb.append(')').toString();
        }
        if (t instanceof WildcardType) {
            WildcardType w = (WildcardType) t;
            return "W:" + Arrays.toString(w.getUpperBounds()) + "/" + Arrays.toString(w.getLowerBounds());
        }
        if (t instanceof GenericArrayType) return "A:" + describe(((GenericArrayType) t).getGenericComponentType());
        if (t instanceof TypeVariable) {
            TypeVariable<?> v = (TypeVariable<?>) t;
            Object d = v.getGenericDeclaration();
            String dn = d instanceof Class ? ((Class<?>) d).getSimpleName() : d instanceof Method ? ((Method) d).getName() : "ctor";
            return "V:" + v.getName() + "@" + dn + Arrays.toString(v.getBounds());
        }
        return "?" + t;
    }

    public static void main(String[] args) throws Exception {
        String[] fields = {"names", "table", "ups", "downs", "any", "value", "values", "lists", "plain", "strings", "inner", "entry"};
        for (String n : fields) {
            Field f = Holder.class.getDeclaredField(n);
            Type t = f.getGenericType();
            p(n + " " + t + " | " + t.getTypeName() + " | " + describe(t));
        }
        Method pick = null, cast = null, raw = null;
        for (Method m : Holder.class.getDeclaredMethods()) {
            if (m.getName().equals("pick")) pick = m;
            if (m.getName().equals("cast")) cast = m;
            if (m.getName().equals("raw")) raw = m;
        }
        p("pick ret " + describe(pick.getGenericReturnType()));
        for (Type t : pick.getGenericParameterTypes()) p("pick param " + describe(t));
        for (Type t : pick.getGenericExceptionTypes()) p("pick throws " + describe(t));
        p("pick exceptions " + Arrays.toString(pick.getExceptionTypes()));
        p("pick tparams " + Arrays.toString(pick.getTypeParameters()) + " " + describe(pick.getTypeParameters()[0]));
        p("cast " + describe(cast.getGenericReturnType()) + " " + Arrays.toString(cast.getTypeParameters()));
        p("raw " + Arrays.toString(raw.getGenericParameterTypes()));
        Constructor<?> c = Holder.class.getDeclaredConstructors()[0];
        p("ctor " + Arrays.toString(c.getGenericParameterTypes()) + " throws " + Arrays.toString(c.getExceptionTypes()));
        p("holder super " + describe(Holder.class.getGenericSuperclass()));
        for (Type t : Holder.class.getGenericInterfaces()) p("holder iface " + describe(t));
        TypeVariable<?>[] tv = Holder.class.getTypeParameters();
        p("holder tparams " + tv.length + " " + describe(tv[0]));
        p("shape tparams " + describe(Shape.class.getTypeParameters()[0]));
        p("shape super " + Shape.class.getGenericSuperclass());
        p("chain " + Chain.class.getGenericSuperclass());
        p("plain " + String.class.getGenericSuperclass() + " " + Arrays.toString(Object.class.getTypeParameters())
                + " " + int[].class.getGenericSuperclass() + " " + Arrays.toString(Chain.class.getGenericInterfaces()));
        Type a = Holder.class.getDeclaredField("names").getGenericType();
        Type b = Holder.class.getDeclaredField("names").getGenericType();
        p("equal " + a.equals(b) + " " + (a.hashCode() == b.hashCode()) + " "
                + tv[0].equals(Holder.class.getTypeParameters()[0])
                + " " + Holder.class.getDeclaredField("value").getGenericType().equals(tv[0]));
        Type w1 = ((ParameterizedType) Holder.class.getDeclaredField("ups").getGenericType()).getActualTypeArguments()[0];
        Type w2 = ((ParameterizedType) Holder.class.getDeclaredField("ups").getGenericType()).getActualTypeArguments()[0];
        p("wild equal " + w1.equals(w2) + " " + (w1.hashCode() == w2.hashCode()));
    }
}
