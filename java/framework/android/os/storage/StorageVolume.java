package android.os.storage;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;
import java.io.File;
import java.util.UUID;

/** A storage volume. switchapk has one: the SD card, as primary external storage. */
public final class StorageVolume implements Parcelable {
    public static final String EXTRA_STORAGE_VOLUME = "android.os.storage.extra.STORAGE_VOLUME";

    private final File mPath;
    private final String mDescription;
    private final boolean mPrimary;
    private final boolean mRemovable;

    StorageVolume(File path, String description, boolean primary, boolean removable) {
        mPath = path;
        mDescription = description;
        mPrimary = primary;
        mRemovable = removable;
    }

    public File getDirectory() { return mPath; }
    public String getDescription(Context context) { return mDescription; }
    public boolean isPrimary() { return mPrimary; }
    public boolean isRemovable() { return mRemovable; }
    public boolean isEmulated() { return false; }
    public UserHandle getOwner() { return UserHandle.of(0); }
    public UUID getStorageUuid() { return mPrimary ? StorageManager.UUID_DEFAULT : null; }
    public String getUuid() { return null; }
    public String getMediaStoreVolumeName() { return mPrimary ? "external_primary" : null; }
    public String getState() { return Environment.getExternalStorageState(mPath); }

    public Intent createAccessIntent(String directoryName) { return null; }

    public Intent createOpenDocumentTreeIntent() {
        return new Intent("android.intent.action.OPEN_DOCUMENT_TREE");
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StorageVolume && mPath.equals(((StorageVolume) obj).mPath);
    }

    @Override
    public int hashCode() { return mPath.hashCode(); }

    @Override
    public String toString() { return mDescription + " (" + mPath + ")"; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mPath.getPath());
        out.writeString(mDescription);
        out.writeInt(mPrimary ? 1 : 0);
        out.writeInt(mRemovable ? 1 : 0);
    }

    public static final Parcelable.Creator<StorageVolume> CREATOR = new Parcelable.Creator<StorageVolume>() {
        public StorageVolume createFromParcel(Parcel in) {
            File path = new File(in.readString());
            String description = in.readString();
            boolean primary = in.readInt() != 0;
            return new StorageVolume(path, description, primary, in.readInt() != 0);
        }
        public StorageVolume[] newArray(int size) { return new StorageVolume[size]; }
    };
}
