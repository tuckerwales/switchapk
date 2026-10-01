package android.app;

import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Port of AOSP Person (a participant in a MessagingStyle conversation). */
public final class Person implements Parcelable {
    private String mKey;
    private CharSequence mName;
    private Icon mIcon;
    private String mUri;
    private boolean mIsImportant;
    private boolean mIsBot;

    Person() {}

    private Person(Builder builder) {
        mName = builder.mName;
        mIcon = builder.mIcon;
        mUri = builder.mUri;
        mKey = builder.mKey;
        mIsBot = builder.mIsBot;
        mIsImportant = builder.mIsImportant;
    }

    public Builder toBuilder() { return new Builder(this); }

    public String getUri() { return mUri; }

    public CharSequence getName() { return mName; }

    public Icon getIcon() { return mIcon; }

    public String getKey() { return mKey; }

    public boolean isBot() { return mIsBot; }

    public boolean isImportant() { return mIsImportant; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Person)) return false;
        Person other = (Person) obj;
        return Objects.equals(mName, other.mName) && Objects.equals(mUri, other.mUri)
                && Objects.equals(mKey, other.mKey) && mIsBot == other.mIsBot && mIsImportant == other.mIsImportant;
    }

    @Override
    public int hashCode() { return Objects.hash(mName, mUri, mKey, mIsBot, mIsImportant); }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { dest.writeValue(this); }

    public static class Builder {
        private String mKey;
        private CharSequence mName;
        private Icon mIcon;
        private String mUri;
        private boolean mIsImportant;
        private boolean mIsBot;

        public Builder() {}

        private Builder(Person person) {
            mName = person.mName;
            mIcon = person.mIcon;
            mUri = person.mUri;
            mKey = person.mKey;
            mIsBot = person.mIsBot;
            mIsImportant = person.mIsImportant;
        }

        public Builder setName(CharSequence name) { mName = name; return this; }

        public Builder setIcon(Icon icon) { mIcon = icon; return this; }

        public Builder setUri(String uri) { mUri = uri; return this; }

        public Builder setKey(String key) { mKey = key; return this; }

        public Builder setImportant(boolean isImportant) { mIsImportant = isImportant; return this; }

        public Builder setBot(boolean isBot) { mIsBot = isBot; return this; }

        public Person build() { return new Person(this); }
    }

    public static final Parcelable.Creator<Person> CREATOR = new Parcelable.Creator<Person>() {
        public Person createFromParcel(Parcel in) { return (Person) in.readValue(null); }
        public Person[] newArray(int size) { return new Person[size]; }
    };
}
