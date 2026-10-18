package java.text;

import java.util.Locale;

public class DecimalFormatSymbols implements Cloneable, java.io.Serializable {
    private static final long serialVersionUID = 5772796243397350300L;

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
    private char monetaryDecimalSeparator = '.';
    private char monetaryGroupingSeparator = ',';

    public DecimalFormatSymbols() {
    }

    /** Grouping and decimal separators from CLDR for the common languages; others use English. */
    public DecimalFormatSymbols(Locale locale) {
        String lang = locale.getLanguage(), country = locale.getCountry();
        switch (lang) {
            case "de":
                if (country.equals("CH") || country.equals("LI")) {
                    groupingSeparator = '\u2019';
                } else if (country.equals("AT")) {
                    groupingSeparator = '\u00a0';
                    decimalSeparator = ',';
                } else {
                    groupingSeparator = '.';
                    decimalSeparator = ',';
                }
                break;
            case "es":
            case "it":
            case "nl":
            case "id":
            case "in":
            case "tr":
            case "da":
            case "el":
            case "ro":
            case "hr":
            case "sl":
            case "sr":
            case "vi":
            case "ca":
            case "gl":
            case "eu":
                if (country.equals("MX") || country.equals("US") && lang.equals("es")) {
                    break; // es_MX and es_US use English separators
                }
                if (lang.equals("it") && country.equals("CH")) {
                    groupingSeparator = '\u2019';
                    break;
                }
                groupingSeparator = '.';
                decimalSeparator = ',';
                break;
            case "pt":
                if (country.equals("BR") || country.isEmpty()) {
                    groupingSeparator = '.';
                } else {
                    groupingSeparator = '\u00a0';
                }
                decimalSeparator = ',';
                break;
            case "fr":
                if (country.equals("CH")) {
                    groupingSeparator = '\u202f';
                    break;
                }
                groupingSeparator = '\u202f';
                decimalSeparator = ',';
                break;
            case "ru":
            case "pl":
            case "cs":
            case "sk":
            case "uk":
            case "bg":
            case "fi":
            case "sv":
            case "nb":
            case "no":
            case "nn":
            case "hu":
            case "lt":
            case "lv":
            case "et":
            case "be":
            case "kk":
                groupingSeparator = '\u00a0';
                decimalSeparator = ',';
                break;
            default:
                break;
        }
        monetaryDecimalSeparator = decimalSeparator;
        monetaryGroupingSeparator = groupingSeparator;
    }

    public static DecimalFormatSymbols getInstance() {
        return getInstance(Locale.getDefault(Locale.Category.FORMAT));
    }

    public static DecimalFormatSymbols getInstance(Locale locale) {
        return new DecimalFormatSymbols(locale);
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
        return monetaryDecimalSeparator;
    }

    public String getExponentSeparator() {
        return exponentSeparator;
    }

    public void setPerMill(char c) {
        perMill = c;
    }

    public void setDigit(char c) {
        digit = c;
    }

    public void setPatternSeparator(char c) {
        patternSeparator = c;
    }

    public void setInfinity(String s) {
        infinity = s;
    }

    public void setNaN(String s) {
        NaN = s;
    }

    public void setInternationalCurrencySymbol(String s) {
        intlCurrencySymbol = s;
    }

    public void setMonetaryDecimalSeparator(char c) {
        monetaryDecimalSeparator = c;
    }

    public char getMonetaryGroupingSeparator() {
        return monetaryGroupingSeparator;
    }

    public void setMonetaryGroupingSeparator(char c) {
        monetaryGroupingSeparator = c;
    }

    public void setExponentSeparator(String exp) {
        if (exp == null) {
            throw new NullPointerException();
        }
        exponentSeparator = exp;
    }

    public java.util.Currency getCurrency() {
        try {
            return java.util.Currency.getInstance(intlCurrencySymbol);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setCurrency(java.util.Currency currency) {
        if (currency == null) {
            throw new NullPointerException();
        }
        intlCurrencySymbol = currency.getCurrencyCode();
        currencySymbol = currency.getSymbol();
    }

    public static Locale[] getAvailableLocales() {
        return Locale.getAvailableLocales();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        DecimalFormatSymbols o = (DecimalFormatSymbols) obj;
        return zeroDigit == o.zeroDigit && groupingSeparator == o.groupingSeparator
                && decimalSeparator == o.decimalSeparator && perMill == o.perMill && percent == o.percent
                && digit == o.digit && patternSeparator == o.patternSeparator && infinity.equals(o.infinity)
                && NaN.equals(o.NaN) && minusSign == o.minusSign && currencySymbol.equals(o.currencySymbol)
                && intlCurrencySymbol.equals(o.intlCurrencySymbol) && exponentSeparator.equals(o.exponentSeparator)
                && monetaryDecimalSeparator == o.monetaryDecimalSeparator
                && monetaryGroupingSeparator == o.monetaryGroupingSeparator;
    }

    public int hashCode() {
        int result = zeroDigit;
        result = result * 37 + groupingSeparator;
        result = result * 37 + decimalSeparator;
        return result;
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
}
