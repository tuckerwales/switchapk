package java.util;

public final class Locale implements Cloneable, java.io.Serializable {
    public static final Locale ENGLISH = new Locale("en", "");
    public static final Locale US = new Locale("en", "US");
    public static final Locale UK = new Locale("en", "GB");
    public static final Locale ROOT = new Locale("", "");
    public static final Locale FRENCH = new Locale("fr", "");
    public static final Locale GERMAN = new Locale("de", "");
    public static final Locale ITALIAN = new Locale("it", "");
    public static final Locale JAPANESE = new Locale("ja", "");
    public static final Locale KOREAN = new Locale("ko", "");
    public static final Locale CHINESE = new Locale("zh", "");
    public static final Locale SIMPLIFIED_CHINESE = new Locale("zh", "CN");
    public static final Locale TRADITIONAL_CHINESE = new Locale("zh", "TW");
    public static final Locale FRANCE = new Locale("fr", "FR");
    public static final Locale GERMANY = new Locale("de", "DE");
    public static final Locale ITALY = new Locale("it", "IT");
    public static final Locale JAPAN = new Locale("ja", "JP");
    public static final Locale KOREA = new Locale("ko", "KR");
    public static final Locale CHINA = SIMPLIFIED_CHINESE;
    public static final Locale CANADA = new Locale("en", "CA");
    public static final Locale CANADA_FRENCH = new Locale("fr", "CA");

    private static Locale defaultLocale = US;

    private final String language;
    private final String country;
    private final String variant;

    public Locale(String language, String country, String variant) {
        this.language = language.toLowerCase();
        this.country = country.toUpperCase();
        this.variant = variant;
    }

    public Locale(String language, String country) {
        this(language, country, "");
    }

    public Locale(String language) {
        this(language, "", "");
    }

    public static Locale getDefault() {
        return defaultLocale;
    }

    public static Locale getDefault(Category category) {
        return defaultLocale;
    }

    public static synchronized void setDefault(Locale newLocale) {
        if (newLocale == null) {
            throw new NullPointerException("Can't set default locale to NULL");
        }
        defaultLocale = newLocale;
    }

    public static Locale[] getAvailableLocales() {
        return new Locale[] {US, ENGLISH, UK};
    }

    public static Locale forLanguageTag(String languageTag) {
        String[] parts = languageTag.split("[-_]");
        return new Locale(parts[0], parts.length > 1 ? parts[1] : "");
    }

    public String getLanguage() {
        return language;
    }

    public String getCountry() {
        return country;
    }

    public String getVariant() {
        return variant;
    }

    public String getScript() {
        return "";
    }

    public String toLanguageTag() {
        return country.isEmpty() ? language : language + "-" + country;
    }

    public String getISO3Language() {
        return language.equals("en") ? "eng" : language;
    }

    public String getISO3Country() {
        return country.equals("US") ? "USA" : country;
    }

    public String getDisplayLanguage() {
        return language.equals("en") ? "English" : language;
    }

    public String getDisplayLanguage(Locale inLocale) {
        return getDisplayLanguage();
    }

    public String getDisplayCountry() {
        return country.equals("US") ? "United States" : country;
    }

    public String getDisplayCountry(Locale inLocale) {
        return getDisplayCountry();
    }

    public String getDisplayName() {
        return country.isEmpty() ? getDisplayLanguage() : getDisplayLanguage() + " (" + getDisplayCountry() + ")";
    }

    public String getDisplayName(Locale inLocale) {
        return getDisplayName();
    }

    public String toString() {
        if (country.isEmpty() && variant.isEmpty()) {
            return language;
        }
        return language + "_" + country + (variant.isEmpty() ? "" : "_" + variant);
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof Locale)) {
            return false;
        }
        Locale o = (Locale) obj;
        return language.equals(o.language) && country.equals(o.country) && variant.equals(o.variant);
    }

    public int hashCode() {
        return language.hashCode() * 31 + country.hashCode();
    }

    public Object clone() {
        return this;
    }

    public enum Category {
        DISPLAY, FORMAT
    }

    public static final class Builder {
        private String language = "";
        private String region = "";

        public Builder setLanguage(String language) {
            this.language = language;
            return this;
        }

        public Builder setRegion(String region) {
            this.region = region;
            return this;
        }

        public Builder setLocale(Locale l) {
            language = l.language;
            region = l.country;
            return this;
        }

        public Builder setLanguageTag(String tag) {
            return setLocale(forLanguageTag(tag));
        }

        public Locale build() {
            return new Locale(language, region);
        }
    }
}
