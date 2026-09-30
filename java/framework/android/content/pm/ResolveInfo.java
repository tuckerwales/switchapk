package android.content.pm;

import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;

public class ResolveInfo implements Parcelable {
    public ActivityInfo activityInfo;
    public ServiceInfo serviceInfo;
    public ProviderInfo providerInfo;
    public IntentFilter filter;
    public int priority;
    public int preferredOrder;
    public int match;
    public int specificIndex = -1;
    public boolean isDefault;
    public int labelRes;
    public CharSequence nonLocalizedLabel;
    public int icon;
    public String resolvePackageName;
    public boolean isInstantAppAvailable;

    public ResolveInfo() {}
    public ResolveInfo(ResolveInfo orig) {
        activityInfo = orig.activityInfo;
        serviceInfo = orig.serviceInfo;
        providerInfo = orig.providerInfo;
        filter = orig.filter;
        priority = orig.priority;
        match = orig.match;
        isDefault = orig.isDefault;
        labelRes = orig.labelRes;
        nonLocalizedLabel = orig.nonLocalizedLabel;
        icon = orig.icon;
    }

    private ComponentInfo getComponentInfo() {
        if (activityInfo != null) return activityInfo;
        if (serviceInfo != null) return serviceInfo;
        return providerInfo;
    }

    public CharSequence loadLabel(PackageManager pm) {
        if (nonLocalizedLabel != null) return nonLocalizedLabel;
        ComponentInfo ci = getComponentInfo();
        return ci != null ? ci.loadLabel(pm) : "";
    }

    public Drawable loadIcon(PackageManager pm) { ComponentInfo ci = getComponentInfo(); return ci != null ? ci.loadIcon(pm) : null; }
    public final int getIconResource() { ComponentInfo ci = getComponentInfo(); return icon != 0 ? icon : (ci != null ? ci.getIconResource() : 0); }
    public boolean isCrossProfileIntentForwarderActivity() { return false; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int parcelableFlags) { dest.writeValue(this); }
    public static final Parcelable.Creator<ResolveInfo> CREATOR = new Parcelable.Creator<ResolveInfo>() {
        public ResolveInfo createFromParcel(Parcel source) { return (ResolveInfo) source.readValue(null); }
        public ResolveInfo[] newArray(int size) { return new ResolveInfo[size]; }
    };
}
