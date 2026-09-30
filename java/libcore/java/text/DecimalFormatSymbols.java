package java.text;

import java.util.Locale;

public class DecimalFormatSymbols implements Cloneable, java.io.Serializable {
    private char zeroDigit = '0';
    private char groupingSeparator = ',';
    private char decimalSeparator = '.';
    private char perMill = '‰';
    private char percent = '%';
    private char digit = '#';
    private char patternSeparator = ';';
    private String infinity = "∞";
    private String NaN = "NaN";
    private char minusSign = '-';
    private String currencySymbol = "$";
    private String intlCurrencySymbol = "USD";
    private String exponentSeparator = "E";

    public DecimalFormatSymbols() {
    }

    public DecimalFormatSymbols(Locale locale) {
    }

    public static DecimalFormatSymbols getInstance() {
        return new DecimalFormatSymbols();
    }

    public static DecimalFormatSymbols getInstance(Locale locale) {
        return new DecimalFormatSymbols();
    }

    public char getZeroDigit() {
        return zeroDigit;
    }

    public void setZeroDigit(char c) {
        zeroDigit = c;
    }

    public char getGroupingSeparator() {
        return groupingSeparator;
    }

    public void setGroupingSeparator(char c) {
        groupingSeparator = c;
    }

    public char getDecimalSeparator() {
        return decimalSeparator;
    }

    public void setDecimalSeparator(char c) {
        decimalSeparator = c;
    }

    public char getPerMill() {
        return perMill;
    }

    public char getPercent() {
        return percent;
    }

    public void setPercent(char c) {
        percent = c;
    }

    public char getDigit() {
        return digit;
    }

    public char getPatternSeparator() {
        return patternSeparator;
    }

    public String getInfinity() {
        return infinity;
    }

    public String getNaN() {
        return NaN;
    }

    public char getMinusSign() {
        return minusSign;
    }

    public void setMinusSign(char c) {
        minusSign = c;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String s) {
        currencySymbol = s;
    }

    public String getInternationalCurrencySymbol() {
        return intlCurrencySymbol;
    }

    public char getMonetaryDecimalSeparator() {
        return decimalSeparator;
    }

    public String getExponentSeparator() {
        return exponentSeparator;
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
}
