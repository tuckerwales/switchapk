package java.lang;

public class EnumConstantNotPresentException extends RuntimeException {
    @SuppressWarnings("rawtypes")
    public EnumConstantNotPresentException(Class<? extends Enum> enumType, String constantName) {
        super(enumType.getName() + "." + constantName);
    }
}
