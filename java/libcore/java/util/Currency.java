package java.util;

public final class Currency implements java.io.Serializable {
    private final String code;

    private Currency(String code) {
        this.code = code;
    }

    public static Currency getInstance(String currencyCode) {
        return new Currency(currencyCode);
    }

    public static Currency getInstance(Locale locale) {
        return new Currency("USD");
    }

    public String getCurrencyCode() {
        return code;
    }

    public String getSymbol() {
        return code.equals("USD") ? "$" : code;
    }

    public String getSymbol(Locale locale) {
        return getSymbol();
    }

    public int getDefaultFractionDigits() {
        return 2;
    }

    public String getDisplayName() {
        return code;
    }

    public String toString() {
        return code;
    }
}
