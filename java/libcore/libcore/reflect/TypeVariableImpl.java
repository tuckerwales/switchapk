package libcore.reflect;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * A type variable. Declared ones (from a formal type parameter list) know their bounds; references
 * ("TT;" in a signature) find their declaration on demand along the declaration chain: the method,
 * its class, then enclosing methods and classes, as Java scoping does.
 */
public final class TypeVariableImpl<D extends GenericDeclaration> implements TypeVariable<D> {
    private final String name;
    private GenericDeclaration declaration;
    private final GenericDeclaration context;
    private Type[] bounds;
    private TypeVariable<?> target;

    /** A formal type parameter of decl; the parser sets its bounds once they are read. */
    TypeVariableImpl(GenericDeclaration decl, String name) {
        this.declaration = decl;
        this.context = decl;
        this.name = name;
    }

    /** A reference seen while parsing a signature that belongs to context. */
    static TypeVariableImpl<?> reference(GenericDeclaration context, String name) {
        TypeVariableImpl<?> v = new TypeVariableImpl<GenericDeclaration>(null, name, context);
        return v;
    }

    private TypeVariableImpl(GenericDeclaration decl, String name, GenericDeclaration context) {
        this.declaration = decl;
        this.context = context;
        this.name = name;
    }

    void setBounds(Type[] bounds) {
        this.bounds = bounds;
    }

    private static GenericDeclaration next(GenericDeclaration d) {
        if (d instanceof Method) {
            return ((Method) d).getDeclaringClass();
        }
        if (d instanceof Constructor) {
            return ((Constructor<?>) d).getDeclaringClass();
        }
        if (d instanceof Class) {
            Class<?> c = (Class<?>) d;
            Method m = c.getEnclosingMethod();
            if (m != null) {
                return m;
            }
            Constructor<?> k = c.getEnclosingConstructor();
            if (k != null) {
                return k;
            }
            return c.getEnclosingClass();
        }
        return null;
    }

    private synchronized TypeVariable<?> resolved() {
        if (target != null) {
            return target;
        }
        if (declaration != null) {
            target = this;
            return this;
        }
        for (GenericDeclaration d = context; d != null; d = next(d)) {
            for (TypeVariable<?> v : d.getTypeParameters()) {
                if (v.getName().equals(name)) {
                    declaration = d;
                    target = v;
                    return v;
                }
            }
        }
        declaration = context;
        target = this;
        return this;
    }

    @SuppressWarnings("unchecked")
    public D getGenericDeclaration() {
        resolved();
        return (D) declaration;
    }

    public Type[] getBounds() {
        TypeVariable<?> r = resolved();
        if (r != this) {
            return r.getBounds();
        }
        return bounds == null ? new Type[] {Object.class} : bounds.clone();
    }

    public String getName() {
        return name;
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return null;
    }

    public Annotation[] getAnnotations() {
        return new Annotation[0];
    }

    public Annotation[] getDeclaredAnnotations() {
        return new Annotation[0];
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TypeVariable)) {
            return false;
        }
        TypeVariable<?> that = (TypeVariable<?>) o;
        return name.equals(that.getName()) && getGenericDeclaration().equals(that.getGenericDeclaration());
    }

    @Override
    public int hashCode() {
        return getGenericDeclaration().hashCode() ^ name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
