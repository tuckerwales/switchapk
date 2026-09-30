package android.content;

import android.os.Parcel;
import android.os.Parcelable;

public final class ComponentName implements Parcelable, Cloneable, Comparable<ComponentName> {
    private final String mPackage;
    private final String mClass;

    public static ComponentName createRelative(String pkg, String cls) {
        final String fullName;
        if (cls.charAt(0) == '.') fullName = pkg + cls;
        else fullName = cls;
        return new ComponentName(pkg, fullName);
    }

    public static ComponentName createRelative(Context pkg, String cls) { return createRelative(pkg.getPackageName(), cls); }

    public ComponentName(String pkg, String cls) {
        if (pkg == null) throw new NullPointerException("package name is null");
        if (cls == null) throw new NullPointerException("class name is null");
        mPackage = pkg;
        mClass = cls;
    }

    public ComponentName(Context pkg, String cls) {
        if (cls == null) throw new NullPointerException("class name is null");
        mPackage = pkg.getPackageName();
        mClass = cls;
    }

    public ComponentName(Context pkg, Class<?> cls) {
        mPackage = pkg.getPackageName();
        mClass = cls.getName();
    }

    public ComponentName(Parcel in) {
        mPackage = in.readString();
        mClass = in.readString();
    }

    public ComponentName clone() { return new ComponentName(mPackage, mClass); }
    public String getPackageName() { return mPackage; }
    public String getClassName() { return mClass; }

    public String getShortClassName() {
        if (mClass.startsWith(mPackage)) {
            int PN = mPackage.length();
            int CN = mClass.length();
            if (CN > PN && mClass.charAt(PN) == '.') return mClass.substring(PN, CN);
        }
        return mClass;
    }

    public String flattenToString() { return mPackage + "/" + mClass; }

    public String flattenToShortString() {
        StringBuilder sb = new StringBuilder(mPackage.length() + mClass.length());
        sb.append(mPackage).append('/');
        if (mClass.startsWith(mPackage) && mClass.length() > mPackage.length() && mClass.charAt(mPackage.length()) == '.') {
            sb.append(mClass, mPackage.length(), mClass.length());
        } else {
            sb.append(mClass);
        }
        return sb.toString();
    }

    public String toShortString() { return "{" + mPackage + "/" + mClass + "}"; }

    public static ComponentName unflattenFromString(String str) {
        int sep = str.indexOf('/');
        if (sep < 0 || (sep + 1) >= str.length()) return null;
        String pkg = str.substring(0, sep);
        String cls = str.substring(sep + 1);
        if (cls.length() > 0 && cls.charAt(0) == '.') cls = pkg + cls;
        return new ComponentName(pkg, cls);
    }

    @Override
    public String toString() { return "ComponentInfo{" + mPackage + "/" + mClass + "}"; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ComponentName)) return false;
        ComponentName other = (ComponentName) obj;
        return mPackage.equals(other.mPackage) && mClass.equals(other.mClass);
    }

    @Override
    public int hashCode() { return mPackage.hashCode() + mClass.hashCode(); }

    public int compareTo(ComponentName that) {
        int v = this.mPackage.compareTo(that.mPackage);
        if (v != 0) return v;
        return this.mClass.compareTo(that.mClass);
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mPackage);
        out.writeString(mClass);
    }

    public static void writeToParcel(ComponentName c, Parcel out) {
        if (c != null) c.writeToParcel(out, 0);
        else out.writeString(null);
    }

    public static ComponentName readFromParcel(Parcel in) {
        String pkg = in.readString();
        return pkg != null ? new ComponentName(pkg, in.readString()) : null;
    }

    public static final Parcelable.Creator<ComponentName> CREATOR = new Parcelable.Creator<ComponentName>() {
        public ComponentName createFromParcel(Parcel in) { return new ComponentName(in); }
        public ComponentName[] newArray(int size) { return new ComponentName[size]; }
    };
}
