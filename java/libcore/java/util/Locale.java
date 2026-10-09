package java.util;

/**
 * Locales with language, script, region, variant and BCP 47 extensions, following the JDK's behaviour for tags,
 * toString and the legacy constructor forms. Display names come from English tables (LocaleData), whatever the
 * requested display locale: there is no CLDR data. Variant and extension display names are the raw subtags.
 */
public final class Locale implements Cloneable, java.io.Serializable {
    private static final long serialVersionUID = 9149081749638150636L;

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
    public static final Locale PRC = SIMPLIFIED_CHINESE;
    public static final Locale TAIWAN = TRADITIONAL_CHINESE;
    public static final Locale CANADA = new Locale("en", "CA");
    public static final Locale CANADA_FRENCH = new Locale("fr", "CA");

    public static final char PRIVATE_USE_EXTENSION = 'x';
    public static final char UNICODE_LOCALE_EXTENSION = 'u';

    private static volatile Locale defaultLocale = US;
    private static volatile Locale defaultDisplayLocale;
    private static volatile Locale defaultFormatLocale;

    private final String language;
    private final String script;
    private final String country;
    private final String variant;
    /* extension key -> canonical value (lower case); the 'u' value is attributes then sorted keywords */
    private final TreeMap<Character, String> extensions;

    public Locale(String language, String country, String variant) {
        if (language == null || country == null || variant == null) {
            throw new NullPointerException();
        }
        String lang = canonicalLanguage(lower(language));
        String cty = upper(country);
        TreeMap<Character, String> ext = new TreeMap<Character, String>();
        // The JDK maps two legacy variants onto Unicode extensions.
        if (lang.equals("ja") && cty.equals("JP") && variant.equals("JP")) {
            ext.put('u', "ca-japanese");
        } else if (lang.equals("th") && cty.equals("TH") && variant.equals("TH")) {
            ext.put('u', "nu-thai");
        }
        this.language = lang;
        this.script = "";
        this.country = cty;
        this.variant = variant;
        this.extensions = ext;
    }

    public Locale(String language, String country) {
        this(language, country, "");
    }

    public Locale(String language) {
        this(language, "", "");
    }

    private Locale(String language, String script, String country, String variant, TreeMap<Character, String> ext) {
        this.language = language;
        this.script = script;
        this.country = country;
        this.variant = variant;
        this.extensions = ext;
    }

    /* ASCII case mapping: locale fields never depend on the default locale's case rules. */
    private static String lower(String s) {
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) {
            if (c[i] >= 'A' && c[i] <= 'Z') {
                c[i] += 'a' - 'A';
            }
        }
        return new String(c);
    }

    private static String upper(String s) {
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) {
            if (c[i] >= 'a' && c[i] <= 'z') {
                c[i] -= 'a' - 'A';
            }
        }
        return new String(c);
    }

    private static String canonicalLanguage(String lang) {
        switch (lang) {
            case "iw":
                return "he";
            case "ji":
                return "yi";
            case "in":
                return "id";
            default:
                return lang;
        }
    }

    // ---- defaults ----------------------------------------------------------------------------------------------

    public static Locale getDefault() {
        return defaultLocale;
    }

    public static Locale getDefault(Category category) {
        Objects.requireNonNull(category);
        Locale l = category == Category.DISPLAY ? defaultDisplayLocale : defaultFormatLocale;
        return l != null ? l : defaultLocale;
    }

    public static synchronized void setDefault(Locale newLocale) {
        if (newLocale == null) {
            throw new NullPointerException("Can't set default locale to NULL");
        }
        defaultLocale = newLocale;
        defaultDisplayLocale = newLocale;
        defaultFormatLocale = newLocale;
    }

    public static synchronized void setDefault(Category category, Locale newLocale) {
        if (category == null) {
            throw new NullPointerException("Category cannot be NULL");
        }
        if (newLocale == null) {
            throw new NullPointerException("Can't set default locale to NULL");
        }
        if (category == Category.DISPLAY) {
            defaultDisplayLocale = newLocale;
        } else {
            defaultFormatLocale = newLocale;
        }
    }

    // ---- tables ------------------------------------------------------------------------------------------------

    private static HashMap<String, String[]> languageTable;
    private static HashMap<String, String[]> countryTable;
    private static HashMap<String, String> scriptTable;

    private static HashMap<String, String[]> table(String data) {
        HashMap<String, String[]> m = new HashMap<String, String[]>();
        for (String row : data.split("\\|")) {
            if (!row.isEmpty()) {
                String[] f = row.split(":", 3);
                m.put(f[0], f);
            }
        }
        return m;
    }

    private static synchronized HashMap<String, String[]> languages() {
        if (languageTable == null) {
            languageTable = table(LocaleData.LANGUAGES);
        }
        return languageTable;
    }

    private static synchronized HashMap<String, String[]> countries() {
        if (countryTable == null) {
            countryTable = table(LocaleData.COUNTRIES);
        }
        return countryTable;
    }

    private static synchronized HashMap<String, String> scripts() {
        if (scriptTable == null) {
            scriptTable = new HashMap<String, String>();
            for (String row : LocaleData.SCRIPTS.split("\\|")) {
                if (!row.isEmpty()) {
                    int c = row.indexOf(':');
                    scriptTable.put(row.substring(0, c), row.substring(c + 1));
                }
            }
        }
        return scriptTable;
    }

    private static final String[] REGION_NAMES = {
        "001", "World", "002", "Africa", "019", "Americas", "142", "Asia", "150", "Europe", "419", "Latin America",
        "009", "Oceania", "005", "South America", "021", "Northern America"
    };

    public static Locale[] getAvailableLocales() {
        String[] names = LocaleData.AVAILABLE.split("\\|");
        ArrayList<Locale> out = new ArrayList<Locale>(names.length + 1);
        out.add(ROOT);
        for (String n : names) {
            if (n.isEmpty()) {
                continue;
            }
            int u = n.indexOf('_');
            out.add(u < 0 ? new Locale(n) : new Locale(n.substring(0, u), n.substring(u + 1)));
        }
        return out.toArray(new Locale[0]);
    }

    public static String[] getISOCountries() {
        ArrayList<String> out = new ArrayList<String>();
        for (String row : LocaleData.COUNTRIES.split("\\|")) {
            if (!row.isEmpty()) {
                out.add(row.substring(0, row.indexOf(':')));
            }
        }
        return out.toArray(new String[0]);
    }

    public static Set<String> getISOCountries(IsoCountryCode type) {
        Objects.requireNonNull(type);
        TreeSet<String> out = new TreeSet<String>();
        switch (type) {
            case PART1_ALPHA2:
                out.addAll(Arrays.asList(getISOCountries()));
                break;
            case PART1_ALPHA3:
                for (String[] f : countries().values()) {
                    if (!f[1].isEmpty()) {
                        out.add(f[1]);
                    }
                }
                break;
            default:
                for (String c : LocaleData.PART3_COUNTRIES.split("\\|")) {
                    if (!c.isEmpty()) {
                        out.add(c);
                    }
                }
        }
        return Collections.unmodifiableSet(out);
    }

    public static String[] getISOLanguages() {
        ArrayList<String> out = new ArrayList<String>();
        for (String row : LocaleData.LANGUAGES.split("\\|")) {
            if (!row.isEmpty()) {
                out.add(row.substring(0, row.indexOf(':')));
            }
        }
        return out.toArray(new String[0]);
    }

    // ---- accessors ---------------------------------------------------------------------------------------------

    public String getLanguage() {
        return language;
    }

    public String getScript() {
        return script;
    }

    public String getCountry() {
        return country;
    }

    public String getVariant() {
        return variant;
    }

    public boolean hasExtensions() {
        return !extensions.isEmpty();
    }

    public Locale stripExtensions() {
        return extensions.isEmpty() ? this : new Locale(language, script, country, variant, new TreeMap<>());
    }

    public String getExtension(char key) {
        if (!isExtensionKey(key)) {
            throw new IllegalArgumentException("Ill-formed extension key: " + key);
        }
        return extensions.get(Character.toLowerCase(key));
    }

    public Set<Character> getExtensionKeys() {
        return Collections.unmodifiableSet(new TreeSet<Character>(extensions.keySet()));
    }

    public Set<String> getUnicodeLocaleAttributes() {
        TreeSet<String> out = new TreeSet<String>();
        String u = extensions.get('u');
        if (u != null) {
            for (String s : u.split("-")) {
                if (s.length() == 2) {
                    break;
                }
                out.add(s);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    public String getUnicodeLocaleType(String key) {
        if (key == null) {
            throw new NullPointerException();
        }
        if (!isUnicodeKey(key)) {
            throw new IllegalArgumentException("Ill-formed Unicode locale key: " + key);
        }
        return unicodeKeywords().get(lower(key));
    }

    public Set<String> getUnicodeLocaleKeys() {
        return Collections.unmodifiableSet(unicodeKeywords().keySet());
    }

    private TreeMap<String, String> unicodeKeywords() {
        TreeMap<String, String> out = new TreeMap<String, String>();
        String u = extensions.get('u');
        if (u != null) {
            String key = null;
            StringBuilder type = new StringBuilder();
            for (String s : u.split("-")) {
                if (s.length() == 2) {
                    if (key != null) {
                        out.put(key, type.toString());
                    }
                    key = s;
                    type.setLength(0);
                } else if (key != null) {
                    if (type.length() > 0) {
                        type.append('-');
                    }
                    type.append(s);
                }
            }
            if (key != null) {
                out.put(key, type.toString());
            }
        }
        return out;
    }

    public String getISO3Language() {
        if (language.length() == 3) {
            return language;
        }
        if (language.isEmpty()) {
            return "";
        }
        String[] f = languages().get(language);
        if (f == null || f[1].isEmpty()) {
            throw new MissingResourceException("Couldn't find 3-letter language code for " + language,
                    "FormatData_" + toString(), "ShortLanguage");
        }
        return f[1];
    }

    public String getISO3Country() {
        if (country.isEmpty()) {
            return "";
        }
        String[] f = countries().get(country);
        if (f == null || f[1].isEmpty()) {
            throw new MissingResourceException("Couldn't find 3-letter country code for " + country,
                    "FormatData_" + toString(), "ShortCountry");
        }
        return f[1];
    }

    // ---- display names (English only) --------------------------------------------------------------------------

    public String getDisplayLanguage() {
        return getDisplayLanguage(getDefault(Category.DISPLAY));
    }

    public String getDisplayLanguage(Locale inLocale) {
        Objects.requireNonNull(inLocale);
        if (language.isEmpty()) {
            return "";
        }
        String[] f = languages().get(language);
        if (f != null) {
            return f[2];
        }
        String[] extra = LocaleData.EXTRA_LANGUAGE_NAMES.split("\\|");
        for (String row : extra) {
            if (row.startsWith(language + ":")) {
                return row.substring(language.length() + 1);
            }
        }
        return language;
    }

    public String getDisplayScript() {
        return getDisplayScript(getDefault(Category.DISPLAY));
    }

    public String getDisplayScript(Locale inLocale) {
        Objects.requireNonNull(inLocale);
        if (script.isEmpty()) {
            return "";
        }
        String name = scripts().get(script);
        return name != null ? name : script;
    }

    public String getDisplayCountry() {
        return getDisplayCountry(getDefault(Category.DISPLAY));
    }

    public String getDisplayCountry(Locale inLocale) {
        Objects.requireNonNull(inLocale);
        if (country.isEmpty()) {
            return "";
        }
        String[] f = countries().get(country);
        if (f != null) {
            return f[2];
        }
        for (int i = 0; i < REGION_NAMES.length; i += 2) {
            if (REGION_NAMES[i].equals(country)) {
                return REGION_NAMES[i + 1];
            }
        }
        return country;
    }

    public String getDisplayVariant() {
        return getDisplayVariant(getDefault(Category.DISPLAY));
    }

    public String getDisplayVariant(Locale inLocale) {
        Objects.requireNonNull(inLocale);
        if (variant.isEmpty()) {
            return "";
        }
        return String.join(", ", variant.split("[_-]"));
    }

    public final String getDisplayName() {
        return getDisplayName(getDefault(Category.DISPLAY));
    }

    public String getDisplayName(Locale inLocale) {
        Objects.requireNonNull(inLocale);
        ArrayList<String> qualifiers = new ArrayList<String>();
        if (!script.isEmpty()) {
            qualifiers.add(getDisplayScript(inLocale));
        }
        if (!country.isEmpty()) {
            qualifiers.add(getDisplayCountry(inLocale));
        }
        if (!variant.isEmpty()) {
            qualifiers.addAll(Arrays.asList(variant.split("[_-]")));
        }
        String lang = getDisplayLanguage(inLocale);
        if (lang.isEmpty()) {
            return String.join(", ", qualifiers);
        }
        return qualifiers.isEmpty() ? lang : lang + " (" + String.join(", ", qualifiers) + ")";
    }

    // ---- string forms ------------------------------------------------------------------------------------------

    private String extensionString() {
        StringBuilder sb = new StringBuilder();
        String priv = null;
        for (Map.Entry<Character, String> e : extensions.entrySet()) {
            if (e.getKey() == 'x') {
                priv = e.getValue();
                continue;
            }
            if (sb.length() > 0) {
                sb.append('-');
            }
            sb.append(e.getKey()).append('-').append(e.getValue());
        }
        if (priv != null) {
            if (sb.length() > 0) {
                sb.append('-');
            }
            sb.append("x-").append(priv);
        }
        return sb.toString();
    }

    public final String toString() {
        boolean l = !language.isEmpty();
        boolean s = !script.isEmpty();
        boolean r = !country.isEmpty();
        boolean v = !variant.isEmpty();
        boolean e = !extensions.isEmpty();
        StringBuilder result = new StringBuilder(language);
        if (r || (l && (v || s || e))) {
            result.append('_').append(country);
        }
        if (v && (l || r)) {
            result.append('_').append(variant);
        }
        if (s && (l || r)) {
            result.append("_#").append(script);
        }
        if (e && (l || r)) {
            result.append('_');
            if (!s) {
                result.append('#');
            }
            result.append(extensionString());
        }
        return result.toString();
    }

    public String toLanguageTag() {
        if (language.isEmpty() && script.isEmpty() && country.isEmpty() && variant.isEmpty()
                && extensions.size() == 1 && extensions.containsKey('x')) {
            return "x-" + extensions.get('x');
        }
        StringBuilder sb = new StringBuilder();
        String lang = language;
        String var = variant;
        if (lang.equals("no") && country.equals("NO") && var.equals("NY")) {
            lang = "nn";
            var = "";
        }
        sb.append(isLanguage(lang) ? lang : "und");
        if (isScript(script)) {
            sb.append('-').append(script);
        }
        if (isRegion(country)) {
            sb.append('-').append(country);
        }
        StringBuilder lvariant = new StringBuilder();
        if (!var.isEmpty()) {
            String[] parts = var.split("[_-]");
            int i = 0;
            for (; i < parts.length && isVariant(parts[i]); i++) {
                sb.append('-').append(parts[i]);
            }
            for (; i < parts.length; i++) {
                if (lvariant.length() > 0) {
                    lvariant.append('-');
                }
                lvariant.append(parts[i]);
            }
        }
        String priv = null;
        for (Map.Entry<Character, String> e : extensions.entrySet()) {
            if (e.getKey() == 'x') {
                priv = e.getValue();
            } else {
                sb.append('-').append(e.getKey()).append('-').append(e.getValue());
            }
        }
        if (priv != null || lvariant.length() > 0) {
            sb.append("-x");
            if (priv != null) {
                sb.append('-').append(priv);
            }
            if (lvariant.length() > 0) {
                sb.append("-lvariant-").append(lvariant);
            }
        }
        return sb.toString();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Locale)) {
            return false;
        }
        Locale o = (Locale) obj;
        return language.equals(o.language) && script.equals(o.script) && country.equals(o.country)
                && variant.equals(o.variant) && extensions.equals(o.extensions);
    }

    public int hashCode() {
        return ((language.hashCode() * 31 + script.hashCode()) * 31 + country.hashCode()) * 31 + variant.hashCode()
                ^ extensions.hashCode();
    }

    public Object clone() {
        return new Locale(language, script, country, variant, new TreeMap<Character, String>(extensions));
    }

    // ---- BCP 47 ------------------------------------------------------------------------------------------------

    private static boolean isAlpha(String s, int min, int max) {
        int n = s.length();
        if (n < min || n > max) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAlnum(String s, int min, int max) {
        int n = s.length();
        if (n < min || n > max) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDigits(String s, int n) {
        if (s.length() != n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean isLanguage(String s) {
        return isAlpha(s, 2, 8);
    }

    private static boolean isExtlang(String s) {
        return isAlpha(s, 3, 3);
    }

    private static boolean isScript(String s) {
        return isAlpha(s, 4, 4);
    }

    private static boolean isRegion(String s) {
        return isAlpha(s, 2, 2) || isDigits(s, 3);
    }

    private static boolean isVariant(String s) {
        int n = s.length();
        if (n >= 5 && n <= 8) {
            return isAlnum(s, 5, 8);
        }
        return n == 4 && s.charAt(0) >= '0' && s.charAt(0) <= '9' && isAlnum(s, 4, 4);
    }

    private static boolean isExtensionKey(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isExtensionSingleton(String s) {
        return s.length() == 1 && isExtensionKey(s.charAt(0)) && Character.toLowerCase(s.charAt(0)) != 'x';
    }

    private static boolean isUnicodeKey(String s) {
        return s.length() == 2 && isAlnum(s, 2, 2);
    }

    private static final String[][] GRANDFATHERED = {
        {"art-lojban", "jbo"}, {"cel-gaulish", "xtg-x-cel-gaulish"}, {"en-gb-oed", "en-GB-x-oed"},
        {"i-ami", "ami"}, {"i-bnn", "bnn"}, {"i-default", "en-x-i-default"}, {"i-enochian", "und-x-i-enochian"},
        {"i-hak", "hak"}, {"i-klingon", "tlh"}, {"i-lux", "lb"}, {"i-mingo", "see-x-i-mingo"}, {"i-navajo", "nv"},
        {"i-pwn", "pwn"}, {"i-tao", "tao"}, {"i-tay", "tay"}, {"i-tsu", "tsu"}, {"no-bok", "nb"}, {"no-nyn", "nn"},
        {"sgn-be-fr", "sfb"}, {"sgn-be-nl", "vgt"}, {"sgn-ch-de", "sgg"}, {"zh-guoyu", "cmn"}, {"zh-hakka", "hak"},
        {"zh-min", "nan-x-zh-min"}, {"zh-min-nan", "nan"}, {"zh-xiang", "hsn"}
    };

    public static Locale forLanguageTag(String languageTag) {
        String tag = lower(languageTag);
        for (String[] g : GRANDFATHERED) {
            if (tag.equals(g[0])) {
                tag = lower(g[1]);
                break;
            }
        }
        String[] st = tag.split("-", -1);
        int i = 0;
        String lang = "";
        String scr = "";
        String reg = "";
        StringBuilder var = new StringBuilder();
        TreeMap<Character, String> ext = new TreeMap<Character, String>();
        if (i < st.length && isLanguage(st[i])) {
            lang = st[i++];
            if (lang.length() <= 3) {
                for (int k = 0; k < 3 && i < st.length && isExtlang(st[i]); k++) {
                    if (k == 0) {
                        lang = st[i];
                    }
                    i++;
                }
            }
            if (i < st.length && isScript(st[i])) {
                scr = Character.toUpperCase(st[i].charAt(0)) + st[i].substring(1);
                i++;
            }
            if (i < st.length && isRegion(st[i])) {
                reg = upper(st[i]);
                i++;
            }
            while (i < st.length && isVariant(st[i])) {
                if (var.length() > 0) {
                    var.append('_');
                }
                var.append(languageTag.split("-", -1)[i]);
                i++;
            }
            while (i < st.length && isExtensionSingleton(st[i])) {
                int j = i + 1;
                StringBuilder v = new StringBuilder();
                while (j < st.length && isAlnum(st[j], 2, 8)) {
                    if (v.length() > 0) {
                        v.append('-');
                    }
                    v.append(st[j]);
                    j++;
                }
                if (v.length() == 0) {
                    break;
                }
                char key = st[i].charAt(0);
                if (!ext.containsKey(key)) {
                    ext.put(key, key == 'u' ? canonicalUnicode(v.toString()) : v.toString());
                }
                i = j;
            }
        }
        if (i < st.length && st[i].equals("x") && (i == 0 || !lang.isEmpty())) {
            int j = i + 1;
            StringBuilder v = new StringBuilder();
            String[] orig = languageTag.split("-", -1);
            boolean lvariant = false;
            StringBuilder lv = new StringBuilder();
            while (j < st.length && isAlnum(st[j], 1, 8)) {
                if (!lvariant && st[j].equals("lvariant")) {
                    lvariant = true;
                } else if (lvariant) {
                    if (lv.length() > 0) {
                        lv.append('_');
                    }
                    lv.append(orig[j]);
                } else {
                    if (v.length() > 0) {
                        v.append('-');
                    }
                    v.append(st[j]);
                }
                j++;
            }
            if (v.length() > 0) {
                ext.put('x', v.toString());
            }
            if (lv.length() > 0) {
                if (var.length() > 0) {
                    var.append('_');
                }
                var.append(lv);
            }
        }
        if (lang.equals("und")) {
            lang = "";
        }
        return new Locale(canonicalLanguage(lang), scr, reg, var.toString(), ext);
    }

    /* Unicode extension value: attributes sorted, then keywords sorted by key, each "key[-type]". */
    private static String canonicalUnicode(String value) {
        TreeSet<String> attrs = new TreeSet<String>();
        TreeMap<String, String> keywords = new TreeMap<String, String>();
        String key = null;
        StringBuilder type = new StringBuilder();
        for (String s : value.split("-")) {
            if (s.length() == 2) {
                if (key != null && !keywords.containsKey(key)) {
                    keywords.put(key, type.toString());
                }
                key = s;
                type.setLength(0);
            } else if (key == null) {
                attrs.add(s);
            } else {
                if (type.length() > 0) {
                    type.append('-');
                }
                type.append(s);
            }
        }
        if (key != null && !keywords.containsKey(key)) {
            keywords.put(key, type.toString());
        }
        return unicodeValue(attrs, keywords);
    }

    private static String unicodeValue(Set<String> attrs, Map<String, String> keywords) {
        StringBuilder sb = new StringBuilder();
        for (String a : attrs) {
            if (sb.length() > 0) {
                sb.append('-');
            }
            sb.append(a);
        }
        for (Map.Entry<String, String> e : keywords.entrySet()) {
            if (sb.length() > 0) {
                sb.append('-');
            }
            sb.append(e.getKey());
            if (!e.getValue().isEmpty()) {
                sb.append('-').append(e.getValue());
            }
        }
        return sb.toString();
    }

    // ---- language ranges and matching (RFC 4647) ---------------------------------------------------------------

    public static List<Locale> filter(List<LanguageRange> priorityList, Collection<Locale> locales) {
        return filter(priorityList, locales, FilteringMode.AUTOSELECT_FILTERING);
    }

    public static List<Locale> filter(List<LanguageRange> priorityList, Collection<Locale> locales,
            FilteringMode mode) {
        ArrayList<String> tags = new ArrayList<String>();
        for (Locale l : locales) {
            tags.add(l.toLanguageTag());
        }
        ArrayList<Locale> out = new ArrayList<Locale>();
        for (String t : filterTags(priorityList, tags, mode)) {
            out.add(forLanguageTag(t));
        }
        return out;
    }

    public static List<String> filterTags(List<LanguageRange> priorityList, Collection<String> tags) {
        return filterTags(priorityList, tags, FilteringMode.AUTOSELECT_FILTERING);
    }

    public static List<String> filterTags(List<LanguageRange> priorityList, Collection<String> tags,
            FilteringMode mode) {
        ArrayList<String> out = new ArrayList<String>();
        for (LanguageRange lr : priorityList) {
            if (lr.getWeight() == LanguageRange.MIN_WEIGHT) {
                continue;
            }
            String range = lr.getRange();
            boolean extended = mode == FilteringMode.EXTENDED_FILTERING
                    || (mode == FilteringMode.AUTOSELECT_FILTERING && range.indexOf("-*") >= 0)
                    || mode == FilteringMode.MAP_EXTENDED_RANGES;
            for (String tag : tags) {
                String lt = lower(tag);
                if (out.contains(lt)) {
                    continue;
                }
                if (extended ? extendedMatch(range, lt) : basicMatch(range, lt)) {
                    out.add(lt);
                }
            }
        }
        return out;
    }

    private static boolean basicMatch(String range, String tag) {
        return range.equals("*") || tag.equals(range) || tag.startsWith(range + "-");
    }

    private static boolean extendedMatch(String range, String tag) {
        String[] r = range.split("-");
        String[] t = tag.split("-");
        if (!r[0].equals("*") && !r[0].equals(t[0])) {
            return false;
        }
        int ri = 1;
        int ti = 1;
        while (ri < r.length) {
            if (r[ri].equals("*")) {
                ri++;
            } else if (ti >= t.length) {
                return false;
            } else if (r[ri].equals(t[ti])) {
                ri++;
                ti++;
            } else if (t[ti].length() == 1) {
                return false;
            } else {
                ti++;
            }
        }
        return true;
    }

    public static Locale lookup(List<LanguageRange> priorityList, Collection<Locale> locales) {
        ArrayList<String> tags = new ArrayList<String>();
        for (Locale l : locales) {
            tags.add(l.toLanguageTag());
        }
        String t = lookupTag(priorityList, tags);
        return t == null ? null : forLanguageTag(t);
    }

    public static String lookupTag(List<LanguageRange> priorityList, Collection<String> tags) {
        for (LanguageRange lr : priorityList) {
            if (lr.getWeight() == LanguageRange.MIN_WEIGHT || lr.getRange().equals("*")) {
                continue;
            }
            String range = lr.getRange().replace("*", "");
            while (!range.isEmpty()) {
                for (String tag : tags) {
                    if (tag.equalsIgnoreCase(range)) {
                        return tag;
                    }
                }
                int cut = range.lastIndexOf('-');
                if (cut < 0) {
                    break;
                }
                range = range.substring(0, cut);
                if (range.length() >= 2 && range.charAt(range.length() - 2) == '-') {
                    range = range.substring(0, range.length() - 2);
                }
            }
        }
        return null;
    }

    public enum Category {
        DISPLAY, FORMAT
    }

    public enum FilteringMode {
        AUTOSELECT_FILTERING, EXTENDED_FILTERING, IGNORE_EXTENDED_RANGES, MAP_EXTENDED_RANGES,
        REJECT_EXTENDED_RANGES
    }

    public enum IsoCountryCode {
        PART1_ALPHA2, PART1_ALPHA3, PART3
    }

    public static final class LanguageRange {
        public static final double MAX_WEIGHT = 1.0;
        public static final double MIN_WEIGHT = 0.0;

        private final String range;
        private final double weight;

        public LanguageRange(String range) {
            this(range, MAX_WEIGHT);
        }

        public LanguageRange(String range, double weight) {
            if (range == null) {
                throw new NullPointerException();
            }
            if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
                throw new IllegalArgumentException("weight=" + weight);
            }
            range = lower(range);
            String[] subtags = range.split("-", -1);
            boolean ok = subtags.length > 0 && (subtags[0].equals("*") || isAlpha(subtags[0], 1, 8));
            for (int i = 1; ok && i < subtags.length; i++) {
                ok = subtags[i].equals("*") || isAlnum(subtags[i], 1, 8);
            }
            if (!ok) {
                throw new IllegalArgumentException("range=" + range);
            }
            this.range = range;
            this.weight = weight;
        }

        public String getRange() {
            return range;
        }

        public double getWeight() {
            return weight;
        }

        public static List<LanguageRange> parse(String ranges) {
            ArrayList<LanguageRange> out = new ArrayList<LanguageRange>();
            for (String item : ranges.replace(" ", "").split(",")) {
                if (item.isEmpty()) {
                    continue;
                }
                double w = MAX_WEIGHT;
                String r = item;
                int q = item.indexOf(";q=");
                if (q >= 0) {
                    r = item.substring(0, q);
                    try {
                        w = Double.parseDouble(item.substring(q + 3));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("weight=\"" + item.substring(q + 3) + "\" for language range \""
                                + r + "\"");
                    }
                }
                LanguageRange lr = new LanguageRange(r, w);
                boolean dup = false;
                for (LanguageRange o : out) {
                    if (o.range.equals(lr.range)) {
                        dup = true;
                    }
                }
                if (!dup) {
                    int at = out.size();
                    while (at > 0 && out.get(at - 1).weight < w) {
                        at--;
                    }
                    out.add(at, lr);
                }
            }
            return out;
        }

        public static List<LanguageRange> parse(String ranges, Map<String, List<String>> map) {
            return mapEquivalents(parse(ranges), map);
        }

        public static List<LanguageRange> mapEquivalents(List<LanguageRange> priorityList,
                Map<String, List<String>> map) {
            if (map == null || map.isEmpty()) {
                return new ArrayList<LanguageRange>(priorityList);
            }
            ArrayList<LanguageRange> out = new ArrayList<LanguageRange>();
            for (LanguageRange lr : priorityList) {
                List<String> eq = map.get(lr.range);
                if (eq == null) {
                    out.add(lr);
                } else {
                    for (String e : eq) {
                        out.add(new LanguageRange(e, lr.weight));
                    }
                }
            }
            return out;
        }

        public int hashCode() {
            long bits = Double.doubleToLongBits(weight);
            return 31 * range.hashCode() + (int) (bits ^ (bits >>> 32));
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof LanguageRange)) {
                return false;
            }
            LanguageRange o = (LanguageRange) obj;
            return range.equals(o.range) && weight == o.weight;
        }

        public String toString() {
            return weight == MAX_WEIGHT ? range : range + ";q=" + weight;
        }
    }

    public static final class Builder {
        private String language = "";
        private String script = "";
        private String region = "";
        private String variant = "";
        private final TreeMap<Character, String> extensions = new TreeMap<Character, String>();
        private final TreeSet<String> uattrs = new TreeSet<String>();
        private final TreeMap<String, String> ukeywords = new TreeMap<String, String>();

        public Builder() {
        }

        public Builder setLocale(Locale locale) {
            if (locale == null) {
                throw new NullPointerException();
            }
            clear();
            language = locale.language;
            script = locale.script;
            region = locale.country;
            variant = locale.variant;
            for (Map.Entry<Character, String> e : locale.extensions.entrySet()) {
                if (e.getKey() == 'u') {
                    uattrs.addAll(locale.getUnicodeLocaleAttributes());
                    ukeywords.putAll(locale.unicodeKeywords());
                } else {
                    extensions.put(e.getKey(), e.getValue());
                }
            }
            return this;
        }

        public Builder setLanguageTag(String languageTag) {
            return setLocale(forLanguageTag(languageTag));
        }

        public Builder setLanguage(String language) {
            if (language == null || language.isEmpty()) {
                this.language = "";
            } else if (!isLanguage(language)) {
                throw new IllformedLocaleException("Ill-formed language: " + language, 0);
            } else {
                this.language = lower(language);
            }
            return this;
        }

        public Builder setScript(String script) {
            if (script == null || script.isEmpty()) {
                this.script = "";
            } else if (!isScript(script)) {
                throw new IllformedLocaleException("Ill-formed script: " + script, 0);
            } else {
                this.script = upper(script.substring(0, 1)) + lower(script.substring(1));
            }
            return this;
        }

        public Builder setRegion(String region) {
            if (region == null || region.isEmpty()) {
                this.region = "";
            } else if (!isRegion(region)) {
                throw new IllformedLocaleException("Ill-formed region: " + region, 0);
            } else {
                this.region = upper(region);
            }
            return this;
        }

        public Builder setVariant(String variant) {
            if (variant == null || variant.isEmpty()) {
                this.variant = "";
                return this;
            }
            String[] parts = variant.split("[_-]", -1);
            int index = 0;
            for (String p : parts) {
                if (!isVariant(p)) {
                    throw new IllformedLocaleException("Ill-formed variant: " + variant, index);
                }
                index += p.length() + 1;
            }
            this.variant = String.join("_", parts);
            return this;
        }

        public Builder setExtension(char key, String value) {
            if (!isExtensionKey(key)) {
                throw new IllformedLocaleException("Ill-formed extension key: " + key);
            }
            char k = Character.toLowerCase(key);
            if (value == null || value.isEmpty()) {
                if (k == 'u') {
                    uattrs.clear();
                    ukeywords.clear();
                } else {
                    extensions.remove(k);
                }
                return this;
            }
            String v = lower(value.replace('_', '-'));
            int index = 0;
            for (String s : v.split("-", -1)) {
                if (!(k == 'x' ? isAlnum(s, 1, 8) : isAlnum(s, 2, 8))) {
                    throw new IllformedLocaleException("Ill-formed extension value: " + s, index);
                }
                index += s.length() + 1;
            }
            if (k == 'u') {
                uattrs.clear();
                ukeywords.clear();
                Locale tmp = forLanguageTag("und-u-" + v);
                uattrs.addAll(tmp.getUnicodeLocaleAttributes());
                ukeywords.putAll(tmp.unicodeKeywords());
            } else {
                extensions.put(k, v);
            }
            return this;
        }

        public Builder setUnicodeLocaleKeyword(String key, String type) {
            if (key == null) {
                throw new NullPointerException("Null key");
            }
            if (!isUnicodeKey(key)) {
                throw new IllformedLocaleException("Ill-formed Unicode locale keyword key: " + key);
            }
            String k = lower(key);
            if (type == null) {
                ukeywords.remove(k);
            } else {
                String t = lower(type.replace('_', '-'));
                if (!t.isEmpty()) {
                    for (String s : t.split("-", -1)) {
                        if (!isAlnum(s, 3, 8)) {
                            throw new IllformedLocaleException("Ill-formed Unicode locale keyword type: " + type);
                        }
                    }
                }
                ukeywords.put(k, t);
            }
            return this;
        }

        public Builder addUnicodeLocaleAttribute(String attribute) {
            if (attribute == null) {
                throw new NullPointerException("Null attribute");
            }
            if (!isAlnum(attribute, 3, 8)) {
                throw new IllformedLocaleException("Ill-formed Unicode locale attribute: " + attribute);
            }
            uattrs.add(lower(attribute));
            return this;
        }

        public Builder removeUnicodeLocaleAttribute(String attribute) {
            if (attribute == null) {
                throw new NullPointerException("Null attribute");
            }
            if (!isAlnum(attribute, 3, 8)) {
                throw new IllformedLocaleException("Ill-formed Unicode locale attribute: " + attribute);
            }
            uattrs.remove(lower(attribute));
            return this;
        }

        public Builder clear() {
            language = "";
            script = "";
            region = "";
            variant = "";
            clearExtensions();
            return this;
        }

        public Builder clearExtensions() {
            extensions.clear();
            uattrs.clear();
            ukeywords.clear();
            return this;
        }

        public Locale build() {
            TreeMap<Character, String> ext = new TreeMap<Character, String>(extensions);
            if (!uattrs.isEmpty() || !ukeywords.isEmpty()) {
                ext.put('u', unicodeValue(uattrs, ukeywords));
            }
            return new Locale(canonicalLanguage(language), script, region, variant, ext);
        }
    }
}
