package android.app.job;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * framework-internal. JobScheduler in process, standing in for JobSchedulerService.
 *
 * A job is ready when its minimum latency has passed and its constraints hold,
 * or when its override deadline has passed (constraints are then ignored, as
 * on Android). A ready job's service is bound with BIND_AUTO_CREATE and driven
 * through its JobServiceEngine binder on the main looper: onStartJob, then
 * jobFinished (or onStartJob returning false, or dequeueWork returning null
 * with no work in progress) ends the run and unbinds. Runs are stopped with
 * onStopJob when cancelled, rescheduled or after 10 minutes. Rescheduling
 * backs off linearly or exponentially (capped at 5 hours); periodic jobs run
 * once per interval inside their flex window. Constraints: network counts as
 * an unmetered connection, charging and battery come from the sticky
 * ACTION_BATTERY_CHANGED, the device is never idle while an app runs, and
 * content-URI triggers never fire (TODO). Jobs live as long as the process.
 */
public final class JobSchedulerImpl extends JobScheduler {
    private static final String TAG = "JobScheduler";
    private static final long EXECUTION_TIMEOUT_MILLIS = 10 * 60 * 1000L;
    /** Re-check constraint-blocked jobs this often (battery state has no change callback here). */
    private static final long CONSTRAINT_POLL_MILLIS = 60 * 1000L;
    private static final long NO_EARLIEST = 0L;
    private static final long NO_LATEST = Long.MAX_VALUE;

    private static JobSchedulerImpl sInstance;

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Object mLock = new Object();
    private final LinkedHashMap<Integer, JobStatus> mJobs = new LinkedHashMap<Integer, JobStatus>();
    private final Runnable mCheck = new Runnable() {
        public void run() { checkJobs(); }
    };

    private JobSchedulerImpl(Context context) { mContext = context; }

    public static synchronized JobSchedulerImpl getInstance(Context appContext) {
        if (sInstance == null) sInstance = new JobSchedulerImpl(appContext);
        return sInstance;
    }

    /** One scheduled job and, while it runs, its run state (JobStatus plus JobServiceContext in AOSP). */
    final class JobStatus {
        final JobInfo job;
        long earliest;
        long latest;
        int numFailures;
        final ArrayList<JobWorkItem> pendingWork = new ArrayList<JobWorkItem>();
        final ArrayList<JobWorkItem> executingWork = new ArrayList<JobWorkItem>();
        int nextWorkId = 1;
        boolean cancelled;
        // While running:
        JobParameters params;
        ServiceConnection connection;
        JobServiceEngine.JobInterface binder;
        boolean stopping;
        Runnable timeout;

        JobStatus(JobInfo job) { this.job = job; }

        boolean running() { return params != null; }

        JobWorkItem dequeueWork(JobParameters p) {
            synchronized (mLock) {
                if (p != params) return null;
                if (pendingWork.isEmpty()) {
                    // No more work: the run ends once nothing is in progress.
                    if (executingWork.isEmpty()) finished(p, false);
                    return null;
                }
                JobWorkItem work = pendingWork.remove(0);
                work.mDeliveryCount++;
                executingWork.add(work);
                return work;
            }
        }

        boolean completeWork(JobParameters p, JobWorkItem work) {
            synchronized (mLock) {
                return p == params && executingWork.remove(work);
            }
        }

        /** jobFinished, from any thread. */
        void finished(final JobParameters p, final boolean reschedule) {
            mHandler.post(new Runnable() {
                public void run() { onRunEnded(JobStatus.this, p, reschedule); }
            });
        }

        /** onStopJob returned. */
        void stopAcked(final JobParameters p, final boolean reschedule) {
            mHandler.post(new Runnable() {
                public void run() { onRunEnded(JobStatus.this, p, reschedule); }
            });
        }
    }

    // ---------------------------------------------------------------- API

    @Override
    public int schedule(JobInfo job) { return scheduleImpl(job, null); }

    @Override
    public int enqueue(JobInfo job, JobWorkItem work) {
        if (work == null) throw new NullPointerException("work");
        if (job.isPersisted()) throw new IllegalArgumentException("Can't enqueue work for persisted jobs");
        return scheduleImpl(job, work);
    }

    private int scheduleImpl(JobInfo job, JobWorkItem work) {
        validateService(job.getService());
        synchronized (mLock) {
            JobStatus existing = mJobs.get(job.getId());
            if (work != null && existing != null && existing.job.equals(job) && !existing.cancelled) {
                // Same job: the work joins its queue (a running job picks it up with dequeueWork).
                addWork(existing, work);
                post();
                return RESULT_SUCCESS;
            }
            JobStatus status = new JobStatus(job);
            setInitialTimes(status);
            if (work != null) addWork(status, work);
            if (existing != null) {
                existing.cancelled = true;
                if (existing.running()) stop(existing, JobParameters.STOP_REASON_CANCELLED_BY_APP);
            }
            mJobs.put(job.getId(), status);
        }
        post();
        return RESULT_SUCCESS;
    }

    @Override
    public void cancel(int jobId) {
        synchronized (mLock) {
            JobStatus status = mJobs.remove(jobId);
            if (status == null) return;
            status.cancelled = true;
            if (status.running()) stop(status, JobParameters.STOP_REASON_CANCELLED_BY_APP);
        }
        post();
    }

    @Override
    public void cancelAll() {
        synchronized (mLock) {
            for (JobStatus status : new ArrayList<JobStatus>(mJobs.values())) {
                status.cancelled = true;
                if (status.running()) stop(status, JobParameters.STOP_REASON_CANCELLED_BY_APP);
            }
            mJobs.clear();
        }
        post();
    }

    @Override
    public List<JobInfo> getAllPendingJobs() {
        synchronized (mLock) {
            ArrayList<JobInfo> out = new ArrayList<JobInfo>();
            for (JobStatus status : mJobs.values()) out.add(status.job);
            return out;
        }
    }

    @Override
    public JobInfo getPendingJob(int jobId) {
        synchronized (mLock) {
            JobStatus status = mJobs.get(jobId);
            return status != null ? status.job : null;
        }
    }

    @Override
    public int getPendingJobReason(int jobId) {
        synchronized (mLock) {
            JobStatus status = mJobs.get(jobId);
            if (status == null) return PENDING_JOB_REASON_INVALID_JOB_ID;
            if (status.running()) return PENDING_JOB_REASON_EXECUTING;
            JobInfo job = status.job;
            if (SystemClock.elapsedRealtime() < status.earliest) return PENDING_JOB_REASON_CONSTRAINT_MINIMUM_LATENCY;
            Intent battery = batteryState();
            if (job.isRequireCharging() && !charging(battery)) return PENDING_JOB_REASON_CONSTRAINT_CHARGING;
            if (job.isRequireBatteryNotLow() && !batteryNotLow(battery)) {
                return PENDING_JOB_REASON_CONSTRAINT_BATTERY_NOT_LOW;
            }
            if (job.isRequireDeviceIdle()) return PENDING_JOB_REASON_CONSTRAINT_DEVICE_IDLE;
            if (job.getTriggerContentUris() != null) return PENDING_JOB_REASON_CONSTRAINT_CONTENT_TRIGGER;
            if (job.getNetworkType() == JobInfo.NETWORK_TYPE_CELLULAR) return PENDING_JOB_REASON_CONSTRAINT_CONNECTIVITY;
            return PENDING_JOB_REASON_UNDEFINED;
        }
    }

    // ---------------------------------------------------------------- scheduling

    private void validateService(ComponentName service) {
        if (service == null) throw new NullPointerException("service");
        ServiceInfo info;
        try {
            info = mContext.getPackageManager().getServiceInfo(service, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalArgumentException("No such service " + service);
        }
        if (!JobService.PERMISSION_BIND.equals(info.permission)) {
            throw new IllegalArgumentException("Scheduled service " + service + " does not require "
                    + JobService.PERMISSION_BIND + " permission");
        }
    }

    private void addWork(JobStatus status, JobWorkItem work) {
        work.mWorkId = status.nextWorkId++;
        status.pendingWork.add(work);
    }

    private static void setInitialTimes(JobStatus status) {
        JobInfo job = status.job;
        long now = SystemClock.elapsedRealtime();
        if (job.isPeriodic()) {
            status.latest = now + job.getIntervalMillis();
            status.earliest = status.latest - job.getFlexMillis();
        } else {
            status.earliest = job.hasEarlyConstraint() ? now + job.getMinLatencyMillis() : NO_EARLIEST;
            status.latest = job.hasLateConstraint() ? now + job.getMaxExecutionDelayMillis() : NO_LATEST;
        }
    }

    private void post() {
        mHandler.removeCallbacks(mCheck);
        mHandler.post(mCheck);
    }

    private Intent batteryState() {
        try {
            return mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean charging(Intent battery) {
        return battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    private static boolean batteryNotLow(Intent battery) {
        if (battery == null) return true;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 100);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return charging(battery) || scale <= 0 || level * 100 / scale > 15;
    }

    private boolean constraintsMet(JobInfo job, Intent battery) {
        if (job.isRequireCharging() && !charging(battery)) return false;
        if (job.isRequireBatteryNotLow() && !batteryNotLow(battery)) return false;
        if (job.isRequireDeviceIdle()) return false;
        if (job.getTriggerContentUris() != null) return false;
        return job.getNetworkType() != JobInfo.NETWORK_TYPE_CELLULAR;
    }

    /** Main thread: start every ready job and arm the next check. */
    private void checkJobs() {
        long now = SystemClock.elapsedRealtime();
        long next = NO_LATEST;
        boolean blocked = false;
        ArrayList<JobStatus> ready = new ArrayList<JobStatus>();
        Intent battery = batteryState();
        synchronized (mLock) {
            for (JobStatus status : mJobs.values()) {
                if (status.running()) continue;
                boolean deadline = status.latest != NO_LATEST && now >= status.latest;
                if (now >= status.earliest && (deadline || constraintsMet(status.job, battery))) {
                    ready.add(status);
                    continue;
                }
                if (now < status.earliest) next = Math.min(next, status.earliest);
                else blocked = true;
                if (status.latest != NO_LATEST) next = Math.min(next, status.latest);
            }
            for (int i = 0; i < ready.size(); i++) start(ready.get(i), now);
        }
        mHandler.removeCallbacks(mCheck);
        if (blocked) next = Math.min(next, now + CONSTRAINT_POLL_MILLIS);
        if (next != NO_LATEST) mHandler.postAtTime(mCheck, SystemClock.uptimeMillis() + Math.max(0, next - now));
    }

    private void start(final JobStatus status, long now) {
        final JobParameters params = new JobParameters(status, status.job,
                status.latest != NO_LATEST && now >= status.latest);
        status.params = params;
        status.stopping = false;
        status.connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service) {
                synchronized (mLock) {
                    if (status.params != params) return;
                    if (!(service instanceof JobServiceEngine.JobInterface)) {
                        Log.e(TAG, "Job service " + name + " did not return a JobServiceEngine binder");
                        status.finished(params, false);
                        return;
                    }
                    status.binder = (JobServiceEngine.JobInterface) service;
                }
                status.binder.startJob(params);
            }

            public void onServiceDisconnected(ComponentName name) {}
        };
        status.timeout = new Runnable() {
            public void run() {
                synchronized (mLock) {
                    if (status.params == params) stop(status, JobParameters.STOP_REASON_TIMEOUT);
                }
            }
        };
        mHandler.postDelayed(status.timeout, EXECUTION_TIMEOUT_MILLIS);
        boolean bound = mContext.bindService(new Intent().setComponent(status.job.getService()), status.connection,
                Context.BIND_AUTO_CREATE);
        if (!bound) {
            Log.e(TAG, "Unable to bind job service " + status.job.getService());
            status.finished(params, false);
        }
    }

    private void stop(JobStatus status, int reason) {
        if (status.stopping || status.params == null) return;
        status.stopping = true;
        status.params.setStopReason(reason);
        if (status.binder != null) status.binder.stopJob(status.params);
        else status.finished(status.params, false);
    }

    /** Main thread: a run ended through jobFinished, onStartJob returning false, or onStopJob. */
    private void onRunEnded(JobStatus status, JobParameters params, boolean reschedule) {
        ServiceConnection connection;
        synchronized (mLock) {
            if (status.params != params) return;
            connection = status.connection;
            status.params = null;
            status.binder = null;
            status.connection = null;
            mHandler.removeCallbacks(status.timeout);
            // Work in progress goes back to the front of the queue to be redelivered.
            status.pendingWork.addAll(0, status.executingWork);
            status.executingWork.clear();
            if (!status.cancelled && mJobs.get(status.job.getId()) == status) {
                long now = SystemClock.elapsedRealtime();
                if (reschedule) {
                    status.numFailures++;
                    status.earliest = now + backoff(status);
                    status.latest = NO_LATEST;
                } else if (status.job.isPeriodic()) {
                    status.numFailures = 0;
                    long latest = status.latest + status.job.getIntervalMillis();
                    if (latest <= now) latest = now + status.job.getIntervalMillis();
                    status.latest = latest;
                    status.earliest = latest - status.job.getFlexMillis();
                } else if (status.pendingWork.isEmpty()) {
                    mJobs.remove(status.job.getId());
                }
            }
        }
        if (connection != null) {
            try {
                mContext.unbindService(connection);
            } catch (IllegalArgumentException ignored) {
                // Never bound.
            }
        }
        post();
    }

    private static long backoff(JobStatus status) {
        JobInfo job = status.job;
        long initial = job.getInitialBackoffMillis();
        long delay;
        if (job.getBackoffPolicy() == JobInfo.BACKOFF_POLICY_LINEAR) {
            delay = initial * status.numFailures;
        } else {
            int shift = Math.min(status.numFailures - 1, 30);
            delay = initial << shift;
            if (delay < 0) delay = JobInfo.MAX_BACKOFF_DELAY_MILLIS;
        }
        return Math.min(delay, JobInfo.MAX_BACKOFF_DELAY_MILLIS);
    }
}
