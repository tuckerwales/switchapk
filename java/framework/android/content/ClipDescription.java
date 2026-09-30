package android.content;

import android.os.Parcel;
import android.os.Parcelable;

public class ClipDescription implements Parcelable {
    public static final String MIMETYPE_TEXT_PLAIN = "text/plain";
    public static final String MIMETYPE_TEXT_HTML = "text/html";
    public static final String MIMETYPE_TEXT_URILIST = "text/uri-list";
    public static final String MIMETYPE_TEXT_INTENT = "text/vnd.android.intent";
    public static final String MIMETYPE_UNKNOWN = "application/octet-stream";

    final CharSequence mLabel;
    final String[] mMimeTypes;

    public ClipDescription(CharSequence label, String[] mimeTypes) {
        mLabel = label;
        mMimeTypes = mimeTypes;
    }

    public ClipDescription(ClipDescription o) {
        mLabel = o.mLabel;
        mMimeTypes = o.mMimeTypes;
    }

    public static boolean compareMimeTypes(String concreteType, String desiredType) {
        if (desiredType.equals("*/*") || concreteType.equals(desiredType)) return true;
        if (desiredType.endsWith("/*")) return concreteType.startsWith(desiredType.substring(0, desiredType.length() - 1));
        return false;
    }

    public CharSequence getLabel() { return mLabel; }
    public boolean hasMimeType(String mimeType) {
        for (String m : mMimeTypes) if (compareMimeTypes(m, mimeType)) return true;
        return false;
    }
    public String[] filterMimeTypes(String mimeType) { return hasMimeType(mimeType) ? mMimeTypes : null; }
    public int getMimeTypeCount() { return mMimeTypes.length; }
    public String getMimeType(int index) { return mMimeTypes[index]; }
    public long getTimestamp() { return 0; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int flags) { dest.writeValue(this); }

    public static final Parcelable.Creator<ClipDescription> CREATOR = new Parcelable.Creator<ClipDescription>() {
        public ClipDescription createFromParcel(Parcel source) { return (ClipDescription) source.readValue(null); }
        public ClipDescription[] newArray(int size) { return new ClipDescription[size]; }
    };
}
