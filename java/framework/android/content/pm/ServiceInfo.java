package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

public class ServiceInfo extends ComponentInfo implements Parcelable {
    public static final int FLAG_STOP_WITH_TASK = 0x0001;
    public static final int FLAG_ISOLATED_PROCESS = 0x0002;
    public static final int FOREGROUND_SERVICE_TYPE_NONE = 0;
    public static final int FOREGROUND_SERVICE_TYPE_DATA_SYNC = 1;
    public static final int FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK = 2;
    public String permission;
    public int flags;

    public ServiceInfo() {}
    public ServiceInfo(ServiceInfo orig) { super(orig); permission = orig.permission; flags = orig.flags; }
    public int getForegroundServiceType() { return 0; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int parcelableFlags) { dest.writeValue(this); }
    public static final Parcelable.Creator<ServiceInfo> CREATOR = new Parcelable.Creator<ServiceInfo>() {
        public ServiceInfo createFromParcel(Parcel source) { return (ServiceInfo) source.readValue(null); }
        public ServiceInfo[] newArray(int size) { return new ServiceInfo[size]; }
    };
}
