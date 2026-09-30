package android.content.pm;

public class FeatureInfo extends PackageItemInfo {
    public int flags;
    public String group;
    public int protectionLevel;
    public int reqGlEsVersion = 0x00020000;
    public FeatureInfo() {}
    public String getGlEsVersion() { return (reqGlEsVersion >> 16) + "." + (reqGlEsVersion & 0xffff); }
}
