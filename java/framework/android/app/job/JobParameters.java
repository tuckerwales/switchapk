package android.app.job;

import android.content.ClipData;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;

/** What a JobService gets for one run of a job. */
public class JobParameters implements Parcelable {
    public static final int STOP_REASON_UNDEFINED = 0;
    public static final int STOP_REASON_CANCELLED_BY_APP = 1;
    public static final int STOP_REASON_PREEMPT = 2;
    public static final int STOP_REASON_TIMEOUT = 3;
    public static final int STOP_REASON_DEVICE_STATE = 4;
    public static final int STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW = 5;
    public static final int STOP_REASON_CONSTRAINT_CHARGING = 6;
    public static final int STOP_REASON_CONSTRAINT_CONNECTIVITY = 7;
    public static final int STOP_REASON_CONSTRAINT_DEVICE_IDLE = 8;
    public static final int STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW = 9;
    public static final int STOP_REASON_QUOTA = 10;
    public static final int STOP_REASON_BACKGROUND_RESTRICTION = 11;
    public static final int STOP_REASON_APP_STANDBY = 12;
    public static final int STOP_REASON_USER = 13;
    public static final int STOP_REASON_SYSTEM_PROCESSING = 14;
    public static final int STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED = 15;

    private final int jobId;
    private final PersistableBundle extras;
    private final Bundle transientExtras;
    private final ClipData clipData;
    private final int clipGrantFlags;
    private final boolean overrideDeadlineExpired;
    private final boolean expedited;
    private final boolean userInitiated;
    private volatile int stopReason = STOP_REASON_UNDEFINED;
    /** The scheduler's record of this run; dequeueWork and completeWork go to it. */
    final JobSchedulerImpl.JobStatus status;

    JobParameters(JobSchedulerImpl.JobStatus status, JobInfo job, boolean overrideDeadlineExpired) {
        this.status = status;
        this.jobId = job.getId();
        this.extras = job.getExtras();
        this.transientExtras = job.getTransientExtras();
        this.clipData = job.getClipData();
        this.clipGrantFlags = job.getClipGrantFlags();
        this.overrideDeadlineExpired = overrideDeadlineExpired;
        this.expedited = job.isExpedited();
        this.userInitiated = job.isUserInitiated();
    }

    public int getJobId() { return jobId; }

    public String getJobNamespace() { return null; }

    public int getStopReason() { return stopReason; }

    void setStopReason(int reason) { stopReason = reason; }

    public PersistableBundle getExtras() { return extras; }

    public Bundle getTransientExtras() { return transientExtras; }

    public ClipData getClipData() { return clipData; }

    public int getClipGrantFlags() { return clipGrantFlags; }

    public boolean isExpeditedJob() { return expedited; }

    public boolean isUserInitiatedJob() { return userInitiated; }

    public boolean isOverrideDeadlineExpired() { return overrideDeadlineExpired; }

    public Uri[] getTriggeredContentUris() { return null; }

    public String[] getTriggeredContentAuthorities() { return null; }

    /** Next queued work item, or null; a null with nothing in progress finishes the job (as in AOSP). */
    public JobWorkItem dequeueWork() { return status.dequeueWork(this); }

    public void completeWork(JobWorkItem work) {
        if (!status.completeWork(this, work)) throw new IllegalArgumentException("Given work is not active: " + work);
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) { dest.writeValue(this); }

    public static final Parcelable.Creator<JobParameters> CREATOR = new Parcelable.Creator<JobParameters>() {
        public JobParameters createFromParcel(Parcel in) { return (JobParameters) in.readValue(null); }
        public JobParameters[] newArray(int size) { return new JobParameters[size]; }
    };
}
