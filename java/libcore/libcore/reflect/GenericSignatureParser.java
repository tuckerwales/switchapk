package libcore.reflect;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.GenericSignatureFormatError;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;

/**
 * Parses the generic signatures dex files keep in dalvik.annotation.Signature (JVMS 4.7.9.1) into
 * java.lang.reflect types. One parser per declaration; a null signature leaves every result null,
 * and callers fall back to the erased types.
 */
public final class GenericSignatureParser {
    private static final TypeVariable<?>[] NO_VARIABLES = new TypeVariable<?>[0];
    private static final Type[] NO_TYPES = new Type[0];

    private final ClassLoader loader;
    private GenericDeclaration decl;
    private String s;
    private int pos;
    private boolean classSignature;

    public TypeVariable<?>[] formalTypeParameters = NO_VARIABLES;
    public Type superclassType;
    public Type[] interfaceTypes = NO_TYPES;
    public Type[] parameterTypes;
    public Type returnType;
    public Type[] exceptionTypes;
    public Type fieldType;

    public GenericSignatureParser(ClassLoader loader) {
        this.loader = loader;
    }

    private void start(GenericDeclaration decl, String signature) {
        this.decl = decl;
        this.s = signature;
        this.pos = 0;
    }

    public void parseForClass(GenericDeclaration cls, String signature) {
        if (signature == null) {
            return;
        }
        start(cls, signature);
        classSignature = true;
        try {
            formalTypeParameters = parseOptFormalTypeParameters();
            superclassType = parseClassTypeSignature();
            ArrayList<Type> ifaces = new ArrayList<Type>();
            while (pos < s.length()) {
                ifaces.add(parseClassTypeSignature());
            }
            interfaceTypes = ifaces.toArray(NO_TYPES);
        } catch (TypeNotPresentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw formatError(e);
        }
    }

    public void parseForMethod(GenericDeclaration method, String signature) {
        if (signature == null) {
            return;
        }
        start(method, signature);
        try {
            formalTypeParameters = parseOptFormalTypeParameters();
            expect('(');
            ArrayList<Type> params = new ArrayList<Type>();
            while (peek() != ')') {
                params.add(parseTypeSignature());
            }
            expect(')');
            parameterTypes = params.toArray(NO_TYPES);
            if (peek() == 'V') {
                pos++;
                returnType = void.class;
            } else {
                returnType = parseTypeSignature();
            }
            ArrayList<Type> throwsList = new ArrayList<Type>();
            while (pos < s.length() && peek() == '^') {
                pos++;
                throwsList.add(peek() == 'T' ? parseTypeVariableSignature() : parseClassTypeSignature());
            }
            exceptionTypes = throwsList.isEmpty() ? null : throwsList.toArray(NO_TYPES);
        } catch (TypeNotPresentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw formatError(e);
        }
    }

    public void parseForField(GenericDeclaration cls, String signature) {
        if (signature == null) {
            return;
        }
        start(cls, signature);
        try {
            fieldType = parseTypeSignature();
        } catch (TypeNotPresentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw formatError(e);
        }
    }

    private GenericSignatureFormatError formatError(RuntimeException e) {
        GenericSignatureFormatError err = new GenericSignatureFormatError(
                "Signature Parse error: " + e.getMessage() + " in " + s);
        err.initCause(e);
        return err;
    }

    private char peek() {
        if (pos >= s.length()) {
            throw new GenericSignatureFormatError("unexpected end of signature " + s);
        }
        return s.charAt(pos);
    }

    private void expect(char c) {
        if (peek() != c) {
            throw new GenericSignatureFormatError("expected '" + c + "' at " + pos + " in " + s);
        }
        pos++;
    }

    private String identifier() {
        int start = pos;
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == ';' || c == '.' || c == '/' || c == '<' || c == '>' || c == ':') {
                break;
            }
            pos++;
        }
        if (pos == start) {
            throw new GenericSignatureFormatError("identifier expected at " + start + " in " + s);
        }
        return s.substring(start, pos);
    }

    private TypeVariable<?>[] parseOptFormalTypeParameters() {
        if (pos >= s.length() || peek() != '<') {
            return NO_VARIABLES;
        }
        pos++;
        ArrayList<TypeVariableImpl<?>> vars = new ArrayList<TypeVariableImpl<?>>();
        ArrayList<Type[]> bounds = new ArrayList<Type[]>();
        // Variables first, so bounds can name any of them (<T extends Comparable<T>>).
        formalTypeParameters = NO_VARIABLES;
        while (peek() != '>') {
            String name = identifier();
            TypeVariableImpl<GenericDeclaration> v = new TypeVariableImpl<GenericDeclaration>(decl, name);
            vars.add(v);
            formalTypeParameters = vars.toArray(NO_VARIABLES);
            ArrayList<Type> b = new ArrayList<Type>();
            expect(':');
            if (peek() != ':') {
                b.add(parseFieldTypeSignature());
            }
            while (peek() == ':') {
                pos++;
                b.add(parseFieldTypeSignature());
            }
            bounds.add(b.isEmpty() ? new Type[] {Object.class} : b.toArray(NO_TYPES));
        }
        pos++;
        for (int i = 0; i < vars.size(); i++) {
            vars.get(i).setBounds(bounds.get(i));
        }
        return vars.toArray(NO_VARIABLES);
    }

    private Type parseFieldTypeSignature() {
        switch (peek()) {
            case 'L':
                return parseClassTypeSignature();
            case '[':
                return parseArrayTypeSignature();
            case 'T':
                return parseTypeVariableSignature();
            default:
                throw new GenericSignatureFormatError("bad field type at " + pos + " in " + s);
        }
    }

    private Type parseTypeSignature() {
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
            default: return parseFieldTypeSignature();
        }
    }

    private Type parseArrayTypeSignature() {
        expect('[');
        Type component = parseTypeSignature();
        if (component instanceof Class) {
            return java.lang.reflect.Array.newInstance((Class<?>) component, 0).getClass();
        }
        return new GenericArrayTypeImpl(component);
    }

    private Type parseTypeVariableSignature() {
        expect('T');
        String name = identifier();
        expect(';');
        for (TypeVariable<?> v : formalTypeParameters) {
            if (v.getName().equals(name)) {
                return v;
            }
        }
        // Declared by the class of a method or field, or an enclosing class or method: resolved on use.
        // A class signature declares the class's own variables, so its references start outside it.
        if (decl instanceof java.lang.reflect.Executable) {
            return TypeVariableImpl.reference(((java.lang.reflect.Executable) decl).getDeclaringClass(), name);
        }
        return TypeVariableImpl.reference(classSignature ? nextOuter(decl) : decl, name);
    }

    /** A class's own parameters are in formalTypeParameters, so references start at its enclosure. */
    private static GenericDeclaration nextOuter(GenericDeclaration d) {
        if (d instanceof Class) {
            Class<?> c = (Class<?>) d;
            java.lang.reflect.Method m = c.getEnclosingMethod();
            if (m != null) {
                return m;
            }
            java.lang.reflect.Constructor<?> k = c.getEnclosingConstructor();
            if (k != null) {
                return k;
            }
            Class<?> enc = c.getEnclosingClass();
            return enc != null ? enc : d;
        }
        return d;
    }

    private Type parseClassTypeSignature() {
        expect('L');
        StringBuilder name = new StringBuilder();
        String part = identifier();
        while (peek() == '/') {
            pos++;
            name.append(part).append('.');
            part = identifier();
        }
        name.append(part);
        Type owner = null;
        Class<?> raw = loadClass(name.toString());
        Type current = typeWithArgs(null, raw);
        while (peek() == '.') {
            pos++;
            owner = current;
            name.append('$').append(identifier());
            raw = loadClass(name.toString());
            current = typeWithArgs(owner, raw);
        }
        expect(';');
        return current;
    }

    /** Reads optional type arguments. A plain class stays a Class unless its owner is parameterized. */
    private Type typeWithArgs(Type owner, Class<?> raw) {
        ArrayList<Type> args = new ArrayList<Type>();
        if (pos < s.length() && peek() == '<') {
            pos++;
            while (peek() != '>') {
                char c = peek();
                if (c == '*') {
                    pos++;
                    args.add(WildcardTypeImpl.unbounded());
                } else if (c == '+') {
                    pos++;
                    args.add(new WildcardTypeImpl(new Type[] {parseFieldTypeSignature()}, NO_TYPES));
                } else if (c == '-') {
                    pos++;
                    args.add(new WildcardTypeImpl(new Type[] {Object.class}, new Type[] {parseFieldTypeSignature()}));
                } else {
                    args.add(parseFieldTypeSignature());
                }
            }
            pos++;
        }
        if (args.isEmpty() && !(owner instanceof java.lang.reflect.ParameterizedType)) {
            return raw;
        }
        return new ParameterizedTypeImpl(owner, raw, args.toArray(NO_TYPES));
    }

    private Class<?> loadClass(String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException e) {
            throw new TypeNotPresentException(name, e);
        }
    }
}
