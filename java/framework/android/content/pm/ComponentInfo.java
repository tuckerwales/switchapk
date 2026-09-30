package android.content.pm;

public class ComponentInfo extends PackageItemInfo {
    public ApplicationInfo applicationInfo;
    public String processName;
    public String splitName;
    public int descriptionRes;
    public boolean enabled = true;
    public boolean exported = false;
    public boolean directBootAware = false;

    public ComponentInfo() {}

    public ComponentInfo(ComponentInfo orig) {
        super(orig);
        applicationInfo = orig.applicationInfo;
        processName = orig.processName;
        descriptionRes = orig.descriptionRes;
        enabled = orig.enabled;
        exported = orig.exported;
    }

    public boolean isEnabled() { return enabled && applicationInfo.enabled; }
    public final int getIconResource() { return icon != 0 ? icon : applicationInfo.icon; }
    public final int getLogoResource() { return logo != 0 ? logo : applicationInfo.logo; }
    public final int getBannerResource() { return banner != 0 ? banner : applicationInfo.banner; }

    @Override
    public CharSequence loadLabel(PackageManager pm) {
        if (nonLocalizedLabel != null || labelRes != 0) return super.loadLabel(pm);
        return applicationInfo != null ? applicationInfo.loadLabel(pm) : name;
    }

    @Override
    protected ApplicationInfo getApplicationInfo() { return applicationInfo; }
}
