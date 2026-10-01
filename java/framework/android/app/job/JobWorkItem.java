package android.app.job;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;

/** A unit of work queued on a job with JobScheduler.enqueue. */
public final class JobWorkItem implements Parcelable {
    private final Intent mIntent;
    private final long mNetworkDownloadBytes;
    private final long mNetworkUploadBytes;
    private final long mMinimumChunkBytes;
    private final PersistableBundle mExtras = new PersistableBundle();
    int mDeliveryCount;
    int mWorkId;

    public JobWorkItem(Intent intent) { this(intent, JobInfo.NETWORK_BYTES_UNKNOWN, JobInfo.NETWORK_BYTES_UNKNOWN); }

    public JobWorkItem(Intent intent, long downloadBytes, long uploadBytes) {
        this(intent, downloadBytes, uploadBytes, JobInfo.NETWORK_BYTES_UNKNOWN);
    }

    public JobWorkItem(Intent intent, long downloadBytes, long uploadBytes, long minimumChunkBytes) {
        mIntent = intent;
        mNetworkDownloadBytes = downloadBytes;
        mNetworkUploadBytes = uploadBytes;
        mMinimumChunkBytes = minimumChunkBytes;
    }

    public PersistableBundle getExtras() { return mExtras; }

    public Intent getIntent() { return mIntent; }

    public long getEstimatedNetworkDownloadBytes() { return mNetworkDownloadBytes; }

    public long getEstimatedNetworkUploadBytes() { return mNetworkUploadBytes; }

    public long getMinimumNetworkChunkBytes() { return mMinimumChunkBytes; }

    public int getDeliveryCount() { return mDeliveryCount; }

    @Override
    public String toString() {
        return "JobWorkItem{id=" + mWorkId + " intent=" + mIntent + " dcount=" + mDeliveryCount + "}";
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel out, int flags) { out.writeValue(this); }

    public static final Parcelable.Creator<JobWorkItem> CREATOR = new Parcelable.Creator<JobWorkItem>() {
        public JobWorkItem createFromParcel(Parcel in) { return (JobWorkItem) in.readValue(null); }
        public JobWorkItem[] newArray(int size) { return new JobWorkItem[size]; }
    };
}
