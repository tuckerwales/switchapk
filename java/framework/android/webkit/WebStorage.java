package android.webkit;

import java.util.HashMap;
import java.util.Map;

/** No web storage without a JavaScript engine: an empty, no-op store. */
public class WebStorage {
    public interface QuotaUpdater {
        void updateQuota(long newQuota);
    }

    public static class Origin {
        private final String mOrigin;
        private final long mQuota;
        private final long mUsage;

        /** Hidden on Android; package-private here. */
        Origin(String origin, long quota, long usage) {
            mOrigin = origin;
            mQuota = quota;
            mUsage = usage;
        }

        public String getOrigin() {
            return mOrigin;
        }

        public long getQuota() {
            return mQuota;
        }

        public long getUsage() {
            return mUsage;
        }
    }

    private static WebStorage sInstance;

    /** Hidden on Android; package-private here. */
    WebStorage() {
    }

    public static synchronized WebStorage getInstance() {
        if (sInstance == null) sInstance = new WebStorage();
        return sInstance;
    }

    @SuppressWarnings("rawtypes")
    public void getOrigins(ValueCallback<Map> callback) {
        CallbackUtil.post(callback, (Map) new HashMap<String, Origin>());
    }

    public void getUsageForOrigin(String origin, ValueCallback<Long> callback) {
        CallbackUtil.post(callback, 0L);
    }

    public void getQuotaForOrigin(String origin, ValueCallback<Long> callback) {
        CallbackUtil.post(callback, 0L);
    }

    @Deprecated
    public void setQuotaForOrigin(String origin, long quota) {
    }

    public void deleteOrigin(String origin) {
    }

    public void deleteAllData() {
    }
}
