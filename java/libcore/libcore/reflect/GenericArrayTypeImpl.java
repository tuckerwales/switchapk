package libcore.reflect;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.Objects;

public final class GenericArrayTypeImpl implements GenericArrayType {
    private final Type componentType;

    public GenericArrayTypeImpl(Type componentType) {
        this.componentType = componentType;
    }

    public Type getGenericComponentType() {
        return componentType;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GenericArrayType
                && Objects.equals(componentType, ((GenericArrayType) o).getGenericComponentType());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(componentType);
    }

    @Override
    public String toString() {
        return componentType.getTypeName() + "[]";
    }
}
