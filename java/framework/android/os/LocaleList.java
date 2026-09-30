package android.os;

import java.util.Locale;

public final class LocaleList implements Parcelable {
    private final Locale[] mList;
    private static final LocaleList EMPTY = new LocaleList();

    public LocaleList(Locale... list) { mList = list.clone(); }

    public Locale get(int index) { return (0 <= index && index < mList.length) ? mList[index] : null; }
    public boolean isEmpty() { return mList.length == 0; }
    public int size() { return mList.length; }
    public int indexOf(Locale locale) {
        for (int i = 0; i < mList.length; i++) if (mList[i].equals(locale)) return i;
        return -1;
    }
    public String toLanguageTags() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mList.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(mList[i].toLanguageTag());
        }
        return sb.toString();
    }
    public static LocaleList getEmptyLocaleList() { return EMPTY; }
    public static LocaleList forLanguageTags(String list) {
        if (list == null || list.isEmpty()) return EMPTY;
        String[] tags = list.split(",");
        Locale[] l = new Locale[tags.length];
        for (int i = 0; i < tags.length; i++) l[i] = Locale.forLanguageTag(tags[i]);
        return new LocaleList(l);
    }
    public static LocaleList getDefault() { return new LocaleList(Locale.getDefault()); }
    public static LocaleList getAdjustedDefault() { return getDefault(); }
    public static void setDefault(LocaleList locales) { if (locales.size() > 0) Locale.setDefault(locales.get(0)); }
    public Locale getFirstMatch(String[] supportedLocales) { return get(0); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int flags) { dest.writeString(toLanguageTags()); }
    public static final Parcelable.Creator<LocaleList> CREATOR = new Parcelable.Creator<LocaleList>() {
        public LocaleList createFromParcel(Parcel source) { return forLanguageTags(source.readString()); }
        public LocaleList[] newArray(int size) { return new LocaleList[size]; }
    };
    @Override public boolean equals(Object other) { return other instanceof LocaleList && java.util.Arrays.equals(mList, ((LocaleList) other).mList); }
    @Override public int hashCode() { return java.util.Arrays.hashCode(mList); }
    @Override public String toString() { return "[" + toLanguageTags() + "]"; }
}
