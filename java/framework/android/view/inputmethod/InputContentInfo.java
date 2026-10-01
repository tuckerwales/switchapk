package android.view.inputmethod;

import android.content.ClipDescription;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/** Rich content (image, gif) an input method commits. Nothing produces it on this platform. */
public final class InputContentInfo implements Parcelable {
    private final Uri mContentUri;
    private final ClipDescription mDescription;
    private final Uri mLinkUri;

    public InputContentInfo(Uri contentUri, ClipDescription description) { this(contentUri, description, null); }

    public InputContentInfo(Uri contentUri, ClipDescription description, Uri linkUri) {
        mContentUri = contentUri;
        mDescription = description;
        mLinkUri = linkUri;
    }

    public Uri getContentUri() { return mContentUri; }

    public ClipDescription getDescription() { return mDescription; }

    public Uri getLinkUri() { return mLinkUri; }

    public void requestPermission() {}

    public void releasePermission() {}

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(mContentUri);
        dest.writeValue(mDescription);
        dest.writeValue(mLinkUri);
    }

    public static final Parcelable.Creator<InputContentInfo> CREATOR = new Parcelable.Creator<InputContentInfo>() {
        public InputContentInfo createFromParcel(Parcel source) {
            return new InputContentInfo((Uri) source.readValue(null), (ClipDescription) source.readValue(null),
                    (Uri) source.readValue(null));
        }

        public InputContentInfo[] newArray(int size) { return new InputContentInfo[size]; }
    };

    public int describeContents() { return 0; }
}
