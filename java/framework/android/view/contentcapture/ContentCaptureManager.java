package android.view.contentcapture;

import android.content.ComponentName;

import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Content capture manager as seen on a device without a content capture service: capture is
 * disabled, there are no conditions and data share or removal requests are dropped.
 */
public final class ContentCaptureManager {
    public static final int DATA_SHARE_ERROR_UNKNOWN = 1;
    public static final int DATA_SHARE_ERROR_CONCURRENT_REQUEST = 2;
    public static final int DATA_SHARE_ERROR_TIMEOUT_INTERRUPTED = 3;

    private static ContentCaptureManager sInstance;

    private ContentCaptureManager() {}

    /** framework-internal: the process-wide instance returned by Context.getSystemService. */
    public static synchronized ContentCaptureManager getInstance() {
        if (sInstance == null) sInstance = new ContentCaptureManager();
        return sInstance;
    }

    public ComponentName getServiceComponentName() { return null; }

    public boolean isContentCaptureEnabled() { return false; }

    public Set<ContentCaptureCondition> getContentCaptureConditions() { return null; }

    public void setContentCaptureEnabled(boolean enabled) {}

    public void removeData(DataRemovalRequest request) {
        if (request == null) throw new NullPointerException("request");
    }

    public void shareData(DataShareRequest request, Executor executor, DataShareWriteAdapter dataShareWriteAdapter) {
        if (request == null) throw new NullPointerException("request");
        if (dataShareWriteAdapter == null) throw new NullPointerException("dataShareWriteAdapter");
        if (executor == null) throw new NullPointerException("executor");
    }
}
