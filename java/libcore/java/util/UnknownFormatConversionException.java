package java.util;

public class UnknownFormatConversionException extends IllegalFormatException {
    private final String s;

    public UnknownFormatConversionException(String s) {
        if (s == null) {
            throw new NullPointerException();
        }
        this.s = s;
    }

    public String getConversion() {
        return s;
    }

    public String getMessage() {
        return String.format("Conversion = '%s'", s);
    }
}
