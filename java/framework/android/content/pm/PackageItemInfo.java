package android.content.pm;

import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

public class PackageItemInfo {
    public String name;
    public String packageName;
    public int labelRes;
    public CharSequence nonLocalizedLabel;
    public int icon;
    public int banner;
    public int logo;
    public Bundle metaData;

    public PackageItemInfo() {}

    public PackageItemInfo(PackageItemInfo orig) {
        name = orig.name;
        packageName = orig.packageName;
        labelRes = orig.labelRes;
        nonLocalizedLabel = orig.nonLocalizedLabel;
        icon = orig.icon;
        banner = orig.banner;
        logo = orig.logo;
        metaData = orig.metaData;
    }

    public CharSequence loadLabel(PackageManager pm) {
        if (nonLocalizedLabel != null) return nonLocalizedLabel;
        if (labelRes != 0) {
            CharSequence label = pm.getText(packageName, labelRes, getApplicationInfo());
            if (label != null) return label.toString().trim();
        }
        if (name != null) return name;
        return packageName;
    }

    public Drawable loadIcon(PackageManager pm) { return pm.loadItemIcon(this, getApplicationInfo()); }
    public Drawable loadUnbadgedIcon(PackageManager pm) { return loadIcon(pm); }
    public Drawable loadBanner(PackageManager pm) { return null; }
    public Drawable loadLogo(PackageManager pm) { return null; }
    public XmlResourceParser loadXmlMetaData(PackageManager pm, String name) {
        if (metaData != null) {
            int resid = metaData.getInt(name);
            if (resid != 0) return pm.getXml(packageName, resid, getApplicationInfo());
        }
        return null;
    }

    protected ApplicationInfo getApplicationInfo() { return null; }
}
