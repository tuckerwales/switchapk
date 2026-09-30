package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

public final class ProviderInfo extends ComponentInfo implements Parcelable {
    public String authority;
    public String readPermission;
    public String writePermission;
    public boolean grantUriPermissions;
    public boolean forceUriPermissions;
    public boolean multiprocess;
    public int initOrder;
    public int flags;
    public boolean isSyncable;

    public ProviderInfo() {}
    public ProviderInfo(ProviderInfo orig) {
        super(orig);
        authority = orig.authority;
        readPermission = orig.readPermission;
        writePermission = orig.writePermission;
        grantUriPermissions = orig.grantUriPermissions;
        multiprocess = orig.multiprocess;
        initOrder = orig.initOrder;
        flags = orig.flags;
    }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int parcelableFlags) { out.writeValue(this); }
    public static final Parcelable.Creator<ProviderInfo> CREATOR = new Parcelable.Creator<ProviderInfo>() {
        public ProviderInfo createFromParcel(Parcel in) { return (ProviderInfo) in.readValue(null); }
        public ProviderInfo[] newArray(int size) { return new ProviderInfo[size]; }
    };
    public String toString() { return "ContentProviderInfo{name=" + authority + " className=" + name + "}"; }
}
