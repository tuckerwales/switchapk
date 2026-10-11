package libcore.reflect;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;

/** A generic class with arguments, as the JDK models it (equality and toString included). */
public final class ParameterizedTypeImpl implements ParameterizedType {
    private final Type ownerType;
    private final Class<?> rawType;
    private final Type[] args;

    public ParameterizedTypeImpl(Type ownerType, Class<?> rawType, Type[] args) {
        this.rawType = rawType;
        this.args = args;
        this.ownerType = ownerType != null ? ownerType : rawType.getDeclaringClass();
    }

    public Type[] getActualTypeArguments() {
        return args.clone();
    }

    public Type getRawType() {
        return rawType;
    }

    public Type getOwnerType() {
        return ownerType;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ParameterizedType)) {
            return false;
        }
        if (this == o) {
            return true;
        }
        ParameterizedType that = (ParameterizedType) o;
        return Objects.equals(ownerType, that.getOwnerType()) && Objects.equals(rawType, that.getRawType())
                && Arrays.equals(args, that.getActualTypeArguments());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(args) ^ Objects.hashCode(ownerType) ^ Objects.hashCode(rawType);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (ownerType != null) {
            sb.append(ownerType.getTypeName());
            sb.append('$');
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
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(args[i].getTypeName());
            }
            sb.append('>');
        }
        return sb.toString();
    }
}
