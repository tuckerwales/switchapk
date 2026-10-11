package java.lang.reflect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * Generic types from the dalvik.annotation.Signature system annotations d8 writes for every
 * generic class, field, method and constructor (the JVM signature grammar, split into
 * strings). Field.getGenericType, the generic accessors of Method, Constructor and Class
 * and getTypeParameters use it; declared exceptions come from dalvik.annotation.Throws.
 * Type variables resolve against the method, then the declaring class and its enclosing
 * classes; one no declaration names (a local class inside a generic method) resolves to a
 * variable of the innermost class with bound Object. Classes are loaded without
 * initialization; a missing one throws TypeNotPresentException, as the JDK does.
 * framework-internal: not part of the SDK.
 */
public final class GenericSignatures {
    private static final String SIGNATURE = "Ldalvik/annotation/Signature;";
    private static final String THROWS = "Ldalvik/annotation/Throws;";
    private static final Type[] NO_TYPES = new Type[0];
    private static final TypeVariable<?>[] NO_VARS = new TypeVariable<?>[0];

    private GenericSignatures() {}

    private static native Object readSystemNative(Class<?> owner, int kind, long token, String annotation,
            String expect);

    private static String join(Object parts) {
        if (!(parts instanceof String[])) return null;
        StringBuilder sb = new StringBuilder();
        for (String s : (String[]) parts) sb.append(s);
        return sb.length() == 0 ? null : sb.toString();
    }

    static String of(Class<?> c) {
        return join(readSystemNative(c, AnnotationParser.KIND_CLASS, 0, SIGNATURE, "[Ljava/lang/String;"));
    }

    static String of(Field f) {
        return join(readSystemNative(null, AnnotationParser.KIND_FIELD, f.vmFieldToken(), SIGNATURE,
                "[Ljava/lang/String;"));
    }

    static String of(Executable e) {
        return join(readSystemNative(null, AnnotationParser.KIND_METHOD, e.vmMethod, SIGNATURE,
                "[Ljava/lang/String;"));
    }

    /** dalvik.annotation.Throws of a method or constructor. */
    static Class<?>[] exceptionTypes(Executable e) {
        Object v = readSystemNative(null, AnnotationParser.KIND_METHOD, e.vmMethod, THROWS, "[Ljava/lang/Class;");
        return v instanceof Class<?>[] ? (Class<?>[]) v : new Class<?>[0];
    }

    // ---------------------------------------------------------------- public entry points

    public static Type fieldType(Field f) {
        String sig = of(f);
        if (sig == null) return f.getType();
        return new Parser(sig, f.getDeclaringClass()).fieldTypeSignature();
    }

    public static Type returnType(Method m) {
        String sig = of(m);
        if (sig == null) return m.getReturnType();
        Parser p = new Parser(sig, m);
        p.skipTypeParameters();
        p.expect('(');
        while (p.peek() != ')') p.typeSignature();
        p.expect(')');
        return p.typeSignature();
    }

    public static Type[] parameterTypes(Executable e) {
        String sig = of(e);
        if (sig == null) return e.getParameterTypes();
        Parser p = new Parser(sig, e);
        p.skipTypeParameters();
        p.expect('(');
        ArrayList<Type> out = new ArrayList<Type>();
        while (p.peek() != ')') out.add(p.typeSignature());
        return out.toArray(NO_TYPES);
    }

    public static Type[] genericExceptionTypes(Executable e) {
        String sig = of(e);
        if (sig != null && sig.indexOf('^') >= 0) {
            Parser p = new Parser(sig, e);
            p.skipTypeParameters();
            p.expect('(');
            while (p.peek() != ')') p.typeSignature();
            p.expect(')');
            p.typeSignature();
            ArrayList<Type> out = new ArrayList<Type>();
            while (p.pos < p.sig.length() && p.peek() == '^') {
                p.pos++;
                out.add(p.fieldTypeSignature());
            }
            return out.toArray(NO_TYPES);
        }
        return exceptionTypes(e);
    }

    public static Class<?>[] declaredExceptions(Executable e) {
        return exceptionTypes(e);
    }

    @SuppressWarnings("unchecked")
    public static <D extends GenericDeclaration> TypeVariable<D>[] typeParameters(D decl) {
        String sig = decl instanceof Class ? of((Class<?>) decl)
                : decl instanceof Executable ? of((Executable) decl) : null;
        if (sig == null || !sig.startsWith("<")) return (TypeVariable<D>[]) NO_VARS;
        Parser p = new Parser(sig, decl);
        ArrayList<String> names = p.typeParameterNames();
        TypeVariable<D>[] out = new TypeVariable[names.size()];
        for (int i = 0; i < out.length; i++) out[i] = new TypeVariableImpl<D>(decl, names.get(i));
        return out;
    }

    public static Type superclass(Class<?> c) {
        Class<?> raw = c.getSuperclass();
        if (raw == null) return null;
        String sig = of(c);
        if (sig == null) return raw;
        Parser p = new Parser(sig, c);
        p.skipTypeParameters();
        return p.fieldTypeSignature();
    }

    public static Type[] interfaces(Class<?> c) {
        String sig = c.isArray() || c.isPrimitive() ? null : of(c);
        if (sig == null) return c.getInterfaces();
        Parser p = new Parser(sig, c);
        p.skipTypeParameters();
        p.fieldTypeSignature();
        ArrayList<Type> out = new ArrayList<Type>();
        while (p.pos < p.sig.length()) out.add(p.fieldTypeSignature());
        return out.toArray(NO_TYPES);
    }

    // ---------------------------------------------------------------- resolution

    /** The bounds of type parameter name declared by decl. */
    static Type[] boundsOf(GenericDeclaration decl, String name) {
        String sig = decl instanceof Class ? of((Class<?>) decl)
                : decl instanceof Executable ? of((Executable) decl) : null;
        if (sig == null || !sig.startsWith("<")) return new Type[] {Object.class};
        Parser p = new Parser(sig, decl);
        Type[] b = p.boundsOf(name);
        return b == null || b.length == 0 ? new Type[] {Object.class} : b;
    }

    private static boolean declares(GenericDeclaration decl, String name) {
        String sig = decl instanceof Class ? of((Class<?>) decl)
                : decl instanceof Executable ? of((Executable) decl) : null;
        if (sig == null || !sig.startsWith("<")) return false;
        return new Parser(sig, decl).typeParameterNames().contains(name);
    }

    static Type resolveVariable(GenericDeclaration ctx, String name) {
        GenericDeclaration d = ctx;
        Class<?> first = null;
        while (d != null) {
            if (declares(d, name)) return new TypeVariableImpl<GenericDeclaration>(d, name);
            Class<?> c;
            if (d instanceof Executable) {
                c = ((Executable) d).getDeclaringClass();
            } else {
                Class<?> k = (Class<?>) d;
                if (first == null) first = k;
                c = k.getEnclosingClass();
            }
            if (first == null && c != null) first = c;
            d = c;
        }
        return new TypeVariableImpl<GenericDeclaration>(first != null ? first : ctx, name);
    }

    static Class<?> loaderClass(GenericDeclaration ctx) {
        if (ctx instanceof Class) return (Class<?>) ctx;
        if (ctx instanceof Executable) return ((Executable) ctx).getDeclaringClass();
        return Object.class;
    }

    // ---------------------------------------------------------------- parser

    static final class Parser {
        final String sig;
        final GenericDeclaration ctx;
        int pos;

        Parser(String sig, GenericDeclaration ctx) {
            this.sig = sig;
            this.ctx = ctx;
        }

        char peek() {
            if (pos >= sig.length()) throw new GenericSignatureFormatError("Signature ended early: " + sig);
            return sig.charAt(pos);
        }

        void expect(char c) {
            if (peek() != c) throw new GenericSignatureFormatError("Expected " + c + " at " + pos + " in " + sig);
            pos++;
        }

        String identifier() {
            int start = pos;
            while (pos < sig.length()) {
                char c = sig.charAt(pos);
                if (c == ';' || c == '<' || c == '.' || c == ':' || c == '>' || c == '/') break;
                pos++;
            }
            return sig.substring(start, pos);
        }

        /** Names of the formal type parameters at pos (leaves pos after the closing '>'). */
        ArrayList<String> typeParameterNames() {
            ArrayList<String> names = new ArrayList<String>();
            if (pos >= sig.length() || peek() != '<') return names;
            pos++;
            while (peek() != '>') {
                names.add(identifier());
                skipBounds();
            }
            pos++;
            return names;
        }

        void skipTypeParameters() { typeParameterNames(); }

        private void skipBounds() {
            while (peek() == ':') {
                pos++;
                char c = peek();
                if (c == 'L' || c == 'T' || c == '[') skipFieldType();
            }
        }

        private void skipFieldType() {
            int depth = 0;
            char c = sig.charAt(pos);
            if (c == '[') {
                pos++;
                char n = peek();
                if ("BCDFIJSZ".indexOf(n) >= 0) pos++;
                else skipFieldType();
                return;
            }
            while (true) {
                c = sig.charAt(pos++);
                if (c == '<') depth++;
                else if (c == '>') depth--;
                else if (c == ';' && depth == 0) return;
            }
        }

        Type[] boundsOf(String name) {
            if (pos >= sig.length() || peek() != '<') return null;
            pos++;
            while (peek() != '>') {
                String n = identifier();
                if (!n.equals(name)) {
                    skipBounds();
                    continue;
                }
                ArrayList<Type> bounds = new ArrayList<Type>();
                while (peek() == ':') {
                    pos++;
                    char c = peek();
                    if (c == 'L' || c == 'T' || c == '[') bounds.add(fieldTypeSignature());
                }
                return bounds.toArray(NO_TYPES);
            }
            return null;
        }

        Type typeSignature() {
            char c = peek();
            switch (c) {
                case 'B': pos++; return byte.class;
                case 'C': pos++; return char.class;
                case 'D': pos++; return double.class;
                case 'F': pos++; return float.class;
                case 'I': pos++; return int.class;
                case 'J': pos++; return long.class;
                case 'S': pos++; return short.class;
                case 'Z': pos++; return boolean.class;
                case 'V': pos++; return void.class;
                default: return fieldTypeSignature();
            }
        }

        Type fieldTypeSignature() {
            char c = peek();
            if (c == 'L') return classTypeSignature();
            if (c == 'T') {
                pos++;
                String name = identifier();
                expect(';');
                return resolveVariable(ctx, name);
            }
            if (c == '[') {
                pos++;
                Type component = typeSignature();
                if (component instanceof Class) return java.lang.reflect.Array.newInstance((Class<?>) component, 0).getClass();
                return new GenericArrayTypeImpl(component);
            }
            throw new GenericSignatureFormatError("Unexpected " + c + " at " + pos + " in " + sig);
        }

        Type classTypeSignature() {
            expect('L');
            StringBuilder binary = new StringBuilder();
            Type owner = null;
            while (true) {
                int start = pos;
                while (pos < sig.length() && "<;.".indexOf(sig.charAt(pos)) < 0) pos++;
                binary.append(sig.substring(start, pos).replace('/', '.'));
                Type[] args = null;
                if (peek() == '<') {
                    pos++;
                    ArrayList<Type> list = new ArrayList<Type>();
                    while (peek() != '>') list.add(typeArgument());
                    pos++;
                    args = list.toArray(NO_TYPES);
                }
                Class<?> raw = load(binary.toString());
                char c = peek();
                Type here;
                if (args != null) {
                    here = new ParameterizedTypeImpl(raw, args, owner != null ? owner : raw.getDeclaringClass());
                } else if (owner instanceof ParameterizedType) {
                    // a non-generic member of a parameterized owner keeps the owner's arguments visible
                    here = new ParameterizedTypeImpl(raw, NO_TYPES, owner);
                } else {
                    here = raw;
                }
                if (c == ';') {
                    pos++;
                    return here;
                }
                expect('.');
                owner = here;
                binary.append('$');
            }
        }

        Type typeArgument() {
            char c = peek();
            if (c == '*') {
                pos++;
                return new WildcardTypeImpl(new Type[] {Object.class}, NO_TYPES);
            }
            if (c == '+') {
                pos++;
                return new WildcardTypeImpl(new Type[] {fieldTypeSignature()}, NO_TYPES);
            }
            if (c == '-') {
                pos++;
                return new WildcardTypeImpl(new Type[] {Object.class}, new Type[] {fieldTypeSignature()});
            }
            return fieldTypeSignature();
        }

        Class<?> load(String name) {
            try {
                return Class.forName(name, false, loaderClass(ctx).getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new TypeNotPresentException(name, e);
            } catch (LinkageError e) {
                throw new TypeNotPresentException(name, e);
            }
        }
    }

    // ---------------------------------------------------------------- Type implementations (JDK semantics)

    static final class ParameterizedTypeImpl implements ParameterizedType {
        private final Class<?> rawType;
        private final Type[] args;
        private final Type ownerType;

        ParameterizedTypeImpl(Class<?> rawType, Type[] args, Type ownerType) {
            this.rawType = rawType;
            this.args = args;
            this.ownerType = ownerType;
        }

        public Type[] getActualTypeArguments() { return args.clone(); }
        public Type getRawType() { return rawType; }
        public Type getOwnerType() { return ownerType; }

        public boolean equals(Object o) {
            if (!(o instanceof ParameterizedType)) return false;
            if (this == o) return true;
            ParameterizedType that = (ParameterizedType) o;
            return Objects.equals(ownerType, that.getOwnerType()) && Objects.equals(rawType, that.getRawType())
                    && Arrays.equals(args, that.getActualTypeArguments());
        }

        public int hashCode() {
            return Arrays.hashCode(args) ^ Objects.hashCode(ownerType) ^ Objects.hashCode(rawType);
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (ownerType != null) {
                sb.append(ownerType.getTypeName()).append('$');
                if (ownerType instanceof ParameterizedTypeImpl) {
                    sb.append(rawType.getName().replace(((ParameterizedTypeImpl) ownerType).rawType.getName() + "$", ""));
                } else {
                    sb.append(rawType.getSimpleName());
                }
            } else {
                sb.append(rawType.getName());
            }
            if (args.length > 0) {
                sb.append('<');
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(args[i].getTypeName());
                }
                sb.append('>');
            }
            return sb.toString();
        }
    }

    static final class GenericArrayTypeImpl implements GenericArrayType {
        private final Type component;

        GenericArrayTypeImpl(Type component) { this.component = component; }

        public Type getGenericComponentType() { return component; }

        public boolean equals(Object o) {
            return o instanceof GenericArrayType && Objects.equals(component, ((GenericArrayType) o).getGenericComponentType());
        }

        public int hashCode() { return Objects.hashCode(component); }

        public String toString() { return component.getTypeName() + "[]"; }
    }

    static final class WildcardTypeImpl implements WildcardType {
        private final Type[] upper;
        private final Type[] lower;

        WildcardTypeImpl(Type[] upper, Type[] lower) {
            this.upper = upper;
            this.lower = lower;
        }

        public Type[] getUpperBounds() { return upper.clone(); }
        public Type[] getLowerBounds() { return lower.clone(); }

        public boolean equals(Object o) {
            if (!(o instanceof WildcardType)) return false;
            WildcardType that = (WildcardType) o;
            return Arrays.equals(lower, that.getLowerBounds()) && Arrays.equals(upper, that.getUpperBounds());
        }

        public int hashCode() { return Arrays.hashCode(lower) ^ Arrays.hashCode(upper); }

        public String toString() {
            Type[] bounds = lower;
            StringBuilder sb = new StringBuilder();
            if (lower.length > 0) {
                sb.append("? super ");
            } else if (upper.length > 0 && !upper[0].equals(Object.class)) {
                bounds = upper;
                sb.append("? extends ");
            } else {
                return "?";
            }
            for (int i = 0; i < bounds.length; i++) {
                if (i > 0) sb.append(" & ");
                sb.append(bounds[i].getTypeName());
            }
            return sb.toString();
        }
    }

    static final class TypeVariableImpl<D extends GenericDeclaration> implements TypeVariable<D> {
        private final D decl;
        private final String name;
        private volatile Type[] bounds;

        TypeVariableImpl(D decl, String name) {
            this.decl = decl;
            this.name = name;
        }

        public Type[] getBounds() {
            Type[] b = bounds;
            if (b == null) bounds = b = boundsOf(decl, name);
            return b.clone();
        }

        public D getGenericDeclaration() { return decl; }
        public String getName() { return name; }

        public boolean equals(Object o) {
            if (!(o instanceof TypeVariable)) return false;
            TypeVariable<?> that = (TypeVariable<?>) o;
            return Objects.equals(decl, that.getGenericDeclaration()) && Objects.equals(name, that.getName());
        }

        public int hashCode() { return decl.hashCode() ^ name.hashCode(); }

        public String toString() { return name; }
    }
}
