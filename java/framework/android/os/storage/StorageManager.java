package android.os.storage;

import android.net.Uri;
import android.os.Environment;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Storage volumes. The only volume is the SD card, which is primary external
 * storage and never changes while an app runs, so callbacks never fire.
 */
public class StorageManager {
    public static final String ACTION_CLEAR_APP_CACHE = "android.os.storage.action.CLEAR_APP_CACHE";
    public static final String ACTION_MANAGE_STORAGE = "android.os.storage.action.MANAGE_STORAGE";
    public static final String EXTRA_REQUESTED_BYTES = "android.os.storage.extra.REQUESTED_BYTES";
    public static final String EXTRA_UUID = "android.os.storage.extra.UUID";
    public static final UUID UUID_DEFAULT = UUID.fromString("41217664-9172-527a-b3d5-edabb50a7d69");

    private static StorageManager sInstance;
    private final StorageVolume mPrimary =
            new StorageVolume(Environment.getExternalStorageDirectory(), "SD card", true, false);

    StorageManager() {}

    /** framework-internal: the instance Context.getSystemService returns. */
    public static synchronized StorageManager getInstance() {
        if (sInstance == null) sInstance = new StorageManager();
        return sInstance;
    }

    public static class StorageVolumeCallback {
        public StorageVolumeCallback() {}
        public void onStateChanged(StorageVolume volume) {}
    }

    public void registerStorageVolumeCallback(Executor executor, StorageVolumeCallback callback) {}
    public void unregisterStorageVolumeCallback(StorageVolumeCallback callback) {}

    public boolean mountObb(String rawPath, String key, OnObbStateChangeListener listener) { return false; }
    public boolean unmountObb(String rawPath, boolean force, OnObbStateChangeListener listener) { return false; }
    public boolean isObbMounted(String rawPath) { return false; }
    public String getMountedObbPath(String rawPath) { return null; }

    public UUID getUuidForPath(File path) throws IOException { return UUID_DEFAULT; }
    public boolean isAllocationSupported(FileDescriptor fd) { return false; }

    public StorageVolume getStorageVolume(File file) {
        if (file == null) return null;
        String root = mPrimary.getDirectory().getAbsolutePath();
        String path = file.getAbsolutePath();
        return path.equals(root) || path.startsWith(root + "/") ? mPrimary : null;
    }

    public StorageVolume getStorageVolume(Uri uri) {
        if (uri != null && "file".equals(uri.getScheme()) && uri.getPath() != null) {
            return getStorageVolume(new File(uri.getPath()));
        }
        return mPrimary;
    }

    public List<StorageVolume> getStorageVolumes() {
        ArrayList<StorageVolume> list = new ArrayList<StorageVolume>();
        list.add(mPrimary);
        return list;
    }

    public List<StorageVolume> getStorageVolumesIncludingSharedProfiles() { return getStorageVolumes(); }
    public List<StorageVolume> getRecentStorageVolumes() { return getStorageVolumes(); }
    public StorageVolume getPrimaryStorageVolume() { return mPrimary; }
    public boolean isEncrypted(File file) { return false; }

    public long getCacheQuotaBytes(UUID storageUuid) throws IOException { return 64L << 20; }
    public long getCacheSizeBytes(UUID storageUuid) throws IOException { return 0; }

    public long getAllocatableBytes(UUID storageUuid) throws IOException {
        return Environment.getExternalStorageDirectory().getUsableSpace();
    }

    public void allocateBytes(UUID storageUuid, long bytes) throws IOException {
        if (bytes > getAllocatableBytes(storageUuid)) throw new IOException("Not enough free space");
    }

    public void allocateBytes(FileDescriptor fd, long bytes) throws IOException {}
    public void setCacheBehaviorGroup(File path, boolean group) throws IOException {}
    public boolean isCacheBehaviorGroup(File path) throws IOException { return false; }
    public void setCacheBehaviorTombstone(File path, boolean tombstone) throws IOException {}
    public boolean isCacheBehaviorTombstone(File path) throws IOException { return false; }
    public boolean isCheckpointSupported() { return false; }
}
