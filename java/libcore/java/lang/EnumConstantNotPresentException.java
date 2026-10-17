package java.lang;

public class EnumConstantNotPresentException extends RuntimeException {
    private static final long serialVersionUID = -6046998521960521108L;

    @SuppressWarnings("rawtypes")
    public EnumConstantNotPresentException(Class<? extends Enum> enumType, String constantName) {
        super(enumType.getName() + "." + constantName);
        this.enumType = enumType;
        this.constantName = constantName;
    }

    @SuppressWarnings("rawtypes")
    private final Class<? extends Enum> enumType;
    private final String constantName;

    @SuppressWarnings("rawtypes")
    public Class<? extends Enum> enumType() {
        return enumType;
    }

    public String constantName() {
        return constantName;
    }
}
