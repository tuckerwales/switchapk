package android.accounts;

import android.os.Parcel;
import android.os.Parcelable;

public class Account implements Parcelable {
    public final String name;
    public final String type;

    public Account(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public boolean equals(Object o) {
        if (!(o instanceof Account)) return false;
        final Account other = (Account) o;
        return name.equals(other.name) && type.equals(other.type);
    }

    public int hashCode() { return 31 * (527 + name.hashCode()) + type.hashCode(); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int flags) { dest.writeString(name); dest.writeString(type); }

    public static final Creator<Account> CREATOR = new Creator<Account>() {
        public Account createFromParcel(Parcel source) { return new Account(source.readString(), source.readString()); }
        public Account[] newArray(int size) { return new Account[size]; }
    };

    public String toString() { return "Account {name=" + name + ", type=" + type + "}"; }
}
