package android.app.job;

import android.content.ClipData;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Port of AOSP JobInfo with its Builder validation. */
public class JobInfo implements Parcelable {
    private static final String TAG = "JobInfo";

    public static final int NETWORK_TYPE_NONE = 0;
    public static final int NETWORK_TYPE_ANY = 1;
    public static final int NETWORK_TYPE_UNMETERED = 2;
    public static final int NETWORK_TYPE_NOT_ROAMING = 3;
    public static final int NETWORK_TYPE_CELLULAR = 4;
    @Deprecated
    public static final int NETWORK_TYPE_METERED = NETWORK_TYPE_CELLULAR;
    public static final int NETWORK_BYTES_UNKNOWN = -1;
    public static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 30000L;
    public static final long MAX_BACKOFF_DELAY_MILLIS = 5 * 60 * 60 * 1000L;
    public static final int BACKOFF_POLICY_LINEAR = 0;
    public static final int BACKOFF_POLICY_EXPONENTIAL = 1;
    public static final int PRIORITY_MIN = 100;
    public static final int PRIORITY_LOW = 200;
    public static final int PRIORITY_DEFAULT = 300;
    public static final int PRIORITY_HIGH = 400;
    public static final int PRIORITY_MAX = 500;

    private static final long MIN_PERIOD_MILLIS = 15 * 60 * 1000L;
    private static final long MIN_FLEX_MILLIS = 5 * 60 * 1000L;
    private static final long MIN_BACKOFF_MILLIS = 10 * 1000L;

    private int jobId;
    private PersistableBundle extras;
    private Bundle transientExtras;
    private ClipData clipData;
    private int clipGrantFlags;
    private ComponentName service;
    private int priority;
    private boolean requireCharging;
    private boolean requireBatteryNotLow;
    private boolean requireDeviceIdle;
    private boolean requireStorageNotLow;
    private TriggerContentUri[] triggerContentUris;
    private long triggerContentUpdateDelay;
    private long triggerContentMaxDelay;
    private int networkType;
    private long networkDownloadBytes;
    private long networkUploadBytes;
    private long minimumNetworkChunkBytes;
    private long minLatencyMillis;
    private long maxExecutionDelayMillis;
    private boolean isPeriodic;
    private boolean isPersisted;
    private long intervalMillis;
    private long flexMillis;
    private long initialBackoffMillis;
    private int backoffPolicy;
    private boolean hasEarlyConstraint;
    private boolean hasLateConstraint;
    private boolean expedited;
    private boolean userInitiated;
    private boolean importantWhileForeground;
    private boolean prefetch;
    private Set<String> debugTags;
    private String traceTag;

    JobInfo() {}

    public static final long getMinPeriodMillis() { return MIN_PERIOD_MILLIS; }

    public static final long getMinFlexMillis() { return MIN_FLEX_MILLIS; }

    public int getId() { return jobId; }

    public PersistableBundle getExtras() { return extras; }

    public Bundle getTransientExtras() { return transientExtras; }

    public ClipData getClipData() { return clipData; }

    public int getClipGrantFlags() { return clipGrantFlags; }

    public ComponentName getService() { return service; }

    public int getPriority() { return priority; }

    public boolean isRequireCharging() { return requireCharging; }

    public boolean isRequireBatteryNotLow() { return requireBatteryNotLow; }

    public boolean isRequireDeviceIdle() { return requireDeviceIdle; }

    public boolean isRequireStorageNotLow() { return requireStorageNotLow; }

    public TriggerContentUri[] getTriggerContentUris() { return triggerContentUris; }

    public long getTriggerContentUpdateDelay() { return triggerContentUpdateDelay; }

    public long getTriggerContentMaxDelay() { return triggerContentMaxDelay; }

    public int getNetworkType() { return networkType; }

    public long getEstimatedNetworkDownloadBytes() { return networkDownloadBytes; }

    public long getEstimatedNetworkUploadBytes() { return networkUploadBytes; }

    public long getMinimumNetworkChunkBytes() { return minimumNetworkChunkBytes; }

    public long getMinLatencyMillis() { return minLatencyMillis; }

    public long getMaxExecutionDelayMillis() { return maxExecutionDelayMillis; }

    public boolean isPeriodic() { return isPeriodic; }

    public boolean isPersisted() { return isPersisted; }

    public long getIntervalMillis() { return intervalMillis; }

    public long getFlexMillis() { return flexMillis; }

    public long getInitialBackoffMillis() { return initialBackoffMillis; }

    public int getBackoffPolicy() { return backoffPolicy; }

    public Set<String> getDebugTags() { return debugTags; }

    public String getTraceTag() { return traceTag; }

    public boolean isExpedited() { return expedited; }

    public boolean isUserInitiated() { return userInitiated; }

    public boolean isImportantWhileForeground() { return importantWhileForeground; }

    public boolean isPrefetch() { return prefetch; }

    /** framework-internal. */
    boolean hasEarlyConstraint() { return hasEarlyConstraint; }

    /** framework-internal. */
    boolean hasLateConstraint() { return hasLateConstraint; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JobInfo)) return false;
        JobInfo j = (JobInfo) o;
        return jobId == j.jobId && Objects.equals(extras != null ? extras.toString() : null,
                j.extras != null ? j.extras.toString() : null)
                && Objects.equals(service, j.service) && priority == j.priority
                && requireCharging == j.requireCharging && requireBatteryNotLow == j.requireBatteryNotLow
                && requireDeviceIdle == j.requireDeviceIdle && requireStorageNotLow == j.requireStorageNotLow
                && Arrays.equals(triggerContentUris, j.triggerContentUris) && networkType == j.networkType
                && minLatencyMillis == j.minLatencyMillis && maxExecutionDelayMillis == j.maxExecutionDelayMillis
                && isPeriodic == j.isPeriodic && isPersisted == j.isPersisted && intervalMillis == j.intervalMillis
                && flexMillis == j.flexMillis && initialBackoffMillis == j.initialBackoffMillis
                && backoffPolicy == j.backoffPolicy && expedited == j.expedited && prefetch == j.prefetch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, service, priority, requireCharging, requireDeviceIdle, networkType,
                minLatencyMillis, maxExecutionDelayMillis, isPeriodic, intervalMillis, flexMillis);
    }

    @Override
    public String toString() { return "(job:" + jobId + "/" + service.flattenToShortString() + ")"; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel out, int flags) { out.writeValue(this); }

    public static final Parcelable.Creator<JobInfo> CREATOR = new Parcelable.Creator<JobInfo>() {
        public JobInfo createFromParcel(Parcel in) { return (JobInfo) in.readValue(null); }
        public JobInfo[] newArray(int size) { return new JobInfo[size]; }
    };

    public static final class TriggerContentUri implements Parcelable {
        public static final int FLAG_NOTIFY_FOR_DESCENDANTS = 1 << 0;

        private final Uri mUri;
        private final int mFlags;

        public TriggerContentUri(Uri uri, int flags) {
            mUri = Objects.requireNonNull(uri);
            mFlags = flags;
        }

        public Uri getUri() { return mUri; }

        public int getFlags() { return mFlags; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TriggerContentUri)) return false;
            TriggerContentUri t = (TriggerContentUri) o;
            return Objects.equals(t.mUri, mUri) && t.mFlags == mFlags;
        }

        @Override
        public int hashCode() { return (mUri == null ? 0 : mUri.hashCode()) ^ mFlags; }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel out, int flags) { out.writeValue(this); }

        public static final Parcelable.Creator<TriggerContentUri> CREATOR = new Parcelable.Creator<TriggerContentUri>() {
            public TriggerContentUri createFromParcel(Parcel in) { return (TriggerContentUri) in.readValue(null); }
            public TriggerContentUri[] newArray(int size) { return new TriggerContentUri[size]; }
        };
    }

    public static final class Builder {
        private final int mJobId;
        private final ComponentName mJobService;
        private PersistableBundle mExtras = new PersistableBundle();
        private Bundle mTransientExtras = new Bundle();
        private ClipData mClipData;
        private int mClipGrantFlags;
        private int mPriority = PRIORITY_DEFAULT;
        private boolean mRequiresCharging;
        private boolean mRequiresBatteryNotLow;
        private boolean mRequiresDeviceIdle;
        private boolean mRequiresStorageNotLow;
        private ArrayList<TriggerContentUri> mTriggerContentUris;
        private long mTriggerContentUpdateDelay = -1;
        private long mTriggerContentMaxDelay = -1;
        private int mNetworkType = NETWORK_TYPE_NONE;
        private long mNetworkDownloadBytes = NETWORK_BYTES_UNKNOWN;
        private long mNetworkUploadBytes = NETWORK_BYTES_UNKNOWN;
        private long mMinimumNetworkChunkBytes = NETWORK_BYTES_UNKNOWN;
        private boolean mIsPersisted;
        private long mMinLatencyMillis;
        private long mMaxExecutionDelayMillis;
        private boolean mIsPeriodic;
        private boolean mHasEarlyConstraint;
        private boolean mHasLateConstraint;
        private long mIntervalMillis;
        private long mFlexMillis;
        private long mInitialBackoffMillis = DEFAULT_INITIAL_BACKOFF_MILLIS;
        private int mBackoffPolicy = BACKOFF_POLICY_EXPONENTIAL;
        private boolean mBackoffPolicySet;
        private boolean mExpedited;
        private boolean mUserInitiated;
        private boolean mImportantWhileForeground;
        private boolean mPrefetch;
        private final HashSet<String> mDebugTags = new HashSet<String>();
        private String mTraceTag;

        public Builder(int jobId, ComponentName jobService) {
            mJobService = jobService;
            mJobId = jobId;
        }

        public Builder addDebugTag(String tag) { mDebugTags.add(tag); return this; }

        public Builder removeDebugTag(String tag) { mDebugTags.remove(tag); return this; }

        public Builder setPriority(int priority) { mPriority = priority; return this; }

        public Builder setExtras(PersistableBundle extras) { mExtras = extras; return this; }

        public Builder setTransientExtras(Bundle extras) { mTransientExtras = extras; return this; }

        public Builder setClipData(ClipData clip, int grantFlags) {
            mClipData = clip;
            mClipGrantFlags = grantFlags;
            return this;
        }

        public Builder setRequiredNetworkType(int networkType) { mNetworkType = networkType; return this; }

        public Builder setEstimatedNetworkBytes(long downloadBytes, long uploadBytes) {
            mNetworkDownloadBytes = downloadBytes;
            mNetworkUploadBytes = uploadBytes;
            return this;
        }

        public Builder setMinimumNetworkChunkBytes(long chunkSizeBytes) {
            if (chunkSizeBytes != NETWORK_BYTES_UNKNOWN && chunkSizeBytes <= 0) {
                throw new IllegalArgumentException("Minimum chunk size must be positive");
            }
            mMinimumNetworkChunkBytes = chunkSizeBytes;
            return this;
        }

        public Builder setRequiresCharging(boolean requiresCharging) { mRequiresCharging = requiresCharging; return this; }

        public Builder setRequiresBatteryNotLow(boolean batteryNotLow) { mRequiresBatteryNotLow = batteryNotLow; return this; }

        public Builder setRequiresDeviceIdle(boolean requiresDeviceIdle) {
            mRequiresDeviceIdle = requiresDeviceIdle;
            return this;
        }

        public Builder setRequiresStorageNotLow(boolean storageNotLow) { mRequiresStorageNotLow = storageNotLow; return this; }

        public Builder addTriggerContentUri(TriggerContentUri uri) {
            if (mTriggerContentUris == null) mTriggerContentUris = new ArrayList<TriggerContentUri>();
            mTriggerContentUris.add(uri);
            return this;
        }

        public Builder setTriggerContentUpdateDelay(long durationMs) { mTriggerContentUpdateDelay = durationMs; return this; }

        public Builder setTriggerContentMaxDelay(long durationMs) { mTriggerContentMaxDelay = durationMs; return this; }

        public Builder setPeriodic(long intervalMillis) { return setPeriodic(intervalMillis, intervalMillis); }

        public Builder setPeriodic(long intervalMillis, long flexMillis) {
            if (intervalMillis < MIN_PERIOD_MILLIS) {
                Log.w(TAG, "Requested interval " + intervalMillis + "ms for job " + mJobId
                        + " is too small; raising to " + MIN_PERIOD_MILLIS + "ms");
                intervalMillis = MIN_PERIOD_MILLIS;
            }
            long percentClamp = 5 * intervalMillis / 100;
            long minFlex = Math.max(percentClamp, MIN_FLEX_MILLIS);
            if (flexMillis < minFlex) {
                Log.w(TAG, "Requested flex " + flexMillis + "ms for job " + mJobId + " is too small; raising to "
                        + minFlex + "ms");
                flexMillis = minFlex;
            }
            mIsPeriodic = true;
            mIntervalMillis = intervalMillis;
            mFlexMillis = Math.min(flexMillis, intervalMillis);
            mHasEarlyConstraint = mHasLateConstraint = true;
            return this;
        }

        public Builder setMinimumLatency(long minLatencyMillis) {
            mMinLatencyMillis = minLatencyMillis;
            mHasEarlyConstraint = true;
            return this;
        }

        public Builder setOverrideDeadline(long maxExecutionDelayMillis) {
            mMaxExecutionDelayMillis = maxExecutionDelayMillis;
            mHasLateConstraint = true;
            return this;
        }

        public Builder setBackoffCriteria(long initialBackoffMillis, int backoffPolicy) {
            if (initialBackoffMillis < MIN_BACKOFF_MILLIS) {
                Log.w(TAG, "Requested backoff " + initialBackoffMillis + "ms for job " + mJobId
                        + " is too small; raising to " + MIN_BACKOFF_MILLIS + "ms");
                initialBackoffMillis = MIN_BACKOFF_MILLIS;
            }
            mBackoffPolicySet = true;
            mInitialBackoffMillis = initialBackoffMillis;
            mBackoffPolicy = backoffPolicy;
            return this;
        }

        public Builder setExpedited(boolean expedited) { mExpedited = expedited; return this; }

        public Builder setUserInitiated(boolean userInitiated) { mUserInitiated = userInitiated; return this; }

        @Deprecated
        public Builder setImportantWhileForeground(boolean importantWhileForeground) {
            mImportantWhileForeground = importantWhileForeground;
            return this;
        }

        public Builder setPrefetch(boolean prefetch) { mPrefetch = prefetch; return this; }

        public Builder setPersisted(boolean isPersisted) { mIsPersisted = isPersisted; return this; }

        public Builder setTraceTag(String traceTag) { mTraceTag = traceTag; return this; }

        public JobInfo build() {
            if (mBackoffPolicySet && mRequiresDeviceIdle) {
                throw new IllegalArgumentException("An idle mode job will not respect any back-off policy, so calling"
                        + " setBackoffCriteria with setRequiresDeviceIdle is an error.");
            }
            if (mIsPeriodic) {
                if (mMaxExecutionDelayMillis != 0L) {
                    throw new IllegalArgumentException("Can't call setOverrideDeadline() on a periodic job.");
                }
                if (mMinLatencyMillis != 0L) {
                    throw new IllegalArgumentException("Can't call setMinimumLatency() on a periodic job");
                }
                if (mTriggerContentUris != null) {
                    throw new IllegalArgumentException("Can't call addTriggerContentUri() on a periodic job");
                }
            }
            if (mIsPersisted && mTriggerContentUris != null) {
                throw new IllegalArgumentException("Can't call addTriggerContentUri() on a persisted job");
            }
            if (mExpedited) {
                if (mHasEarlyConstraint) throw new IllegalArgumentException("An expedited job cannot have a time delay");
                if (mHasLateConstraint) throw new IllegalArgumentException("An expedited job cannot have a deadline");
                if (mIsPeriodic) throw new IllegalArgumentException("An expedited job cannot be periodic");
                if (mRequiresCharging || mRequiresDeviceIdle || mRequiresBatteryNotLow || mRequiresStorageNotLow
                        || mTriggerContentUris != null) {
                    throw new IllegalArgumentException("An expedited job can only have network and storage-not-low"
                            + " constraints");
                }
            }
            boolean hasConstraint = mRequiresCharging || mRequiresBatteryNotLow || mRequiresDeviceIdle
                    || mRequiresStorageNotLow || mTriggerContentUris != null || mNetworkType != NETWORK_TYPE_NONE
                    || mHasEarlyConstraint || mHasLateConstraint || mExpedited;
            if (!hasConstraint) {
                throw new IllegalArgumentException("You're trying to build a job with no constraints, this is not"
                        + " allowed.");
            }
            JobInfo job = new JobInfo();
            job.jobId = mJobId;
            job.extras = mExtras;
            job.transientExtras = mTransientExtras;
            job.clipData = mClipData;
            job.clipGrantFlags = mClipGrantFlags;
            job.service = mJobService;
            job.priority = mPriority;
            job.requireCharging = mRequiresCharging;
            job.requireBatteryNotLow = mRequiresBatteryNotLow;
            job.requireDeviceIdle = mRequiresDeviceIdle;
            job.requireStorageNotLow = mRequiresStorageNotLow;
            job.triggerContentUris = mTriggerContentUris != null
                    ? mTriggerContentUris.toArray(new TriggerContentUri[0]) : null;
            job.triggerContentUpdateDelay = mTriggerContentUpdateDelay;
            job.triggerContentMaxDelay = mTriggerContentMaxDelay;
            job.networkType = mNetworkType;
            job.networkDownloadBytes = mNetworkDownloadBytes;
            job.networkUploadBytes = mNetworkUploadBytes;
            job.minimumNetworkChunkBytes = mMinimumNetworkChunkBytes;
            job.minLatencyMillis = mMinLatencyMillis;
            job.maxExecutionDelayMillis = mMaxExecutionDelayMillis;
            job.isPeriodic = mIsPeriodic;
            job.isPersisted = mIsPersisted;
            job.intervalMillis = mIntervalMillis;
            job.flexMillis = mFlexMillis;
            job.initialBackoffMillis = mInitialBackoffMillis;
            job.backoffPolicy = mBackoffPolicy;
            job.hasEarlyConstraint = mHasEarlyConstraint;
            job.hasLateConstraint = mHasLateConstraint;
            job.expedited = mExpedited;
            job.userInitiated = mUserInitiated;
            job.importantWhileForeground = mImportantWhileForeground;
            job.prefetch = mPrefetch;
            job.debugTags = Collections.unmodifiableSet(new HashSet<String>(mDebugTags));
            job.traceTag = mTraceTag;
            return job;
        }
    }
}
