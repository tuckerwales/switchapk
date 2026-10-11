package libcore.reflect;

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

public final class WildcardTypeImpl implements WildcardType {
    private static final Type[] OBJECT = {Object.class};
    private static final Type[] NONE = {};

    private final Type[] upper;
    private final Type[] lower;

    public WildcardTypeImpl(Type[] upper, Type[] lower) {
        this.upper = upper.length == 0 ? OBJECT : upper;
        this.lower = lower;
    }

    public static WildcardTypeImpl unbounded() {
        return new WildcardTypeImpl(OBJECT, NONE);
    }

    public Type[] getUpperBounds() {
        return upper.clone();
    }

    public Type[] getLowerBounds() {
        return lower.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WildcardType)) {
            return false;
        }
        WildcardType that = (WildcardType) o;
        return Arrays.equals(lower, that.getLowerBounds()) && Arrays.equals(upper, that.getUpperBounds());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(lower) ^ Arrays.hashCode(upper);
    }

    @Override
    public String toString() {
        Type[] bounds = lower;
        StringBuilder sb = new StringBuilder();
        if (lower.length > 0) {
            sb.append("? super ");
        } else {
            if (upper.length > 0 && !upper[0].equals(Object.class)) {
                bounds = upper;
                sb.append("? extends ");
            } else {
                return "?";
            }
        }
        for (int i = 0; i < bounds.length; i++) {
            if (i > 0) {
                sb.append(" & ");
            }
            sb.append(bounds[i].getTypeName());
        }
        return sb.toString();
    }
}
