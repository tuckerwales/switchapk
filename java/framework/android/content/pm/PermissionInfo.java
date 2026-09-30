package android.content.pm;

public class PermissionInfo extends PackageItemInfo {
    public int flags;
    public String group;
    public int protectionLevel;
    public int reqGlEsVersion = 0x00020000;
    public PermissionInfo() {}
    public String getGlEsVersion() { return (reqGlEsVersion >> 16) + "." + (reqGlEsVersion & 0xffff); }
}
