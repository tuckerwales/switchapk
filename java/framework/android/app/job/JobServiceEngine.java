package android.app.job;

import android.app.Notification;
import android.app.Service;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Port of AOSP JobServiceEngine: the scheduler calls the binder from getBinder,
 * and the engine runs onStartJob/onStopJob on the service's main looper and
 * reports back. AndroidX JobIntentService subclasses this directly.
 */
public abstract class JobServiceEngine {
    private final JobInterface mBinder;
    private final Handler mHandler;

    /** The binder the scheduler drives (IJobService in AOSP). */
    static final class JobInterface extends Binder {
        final JobServiceEngine engine;

        JobInterface(JobServiceEngine engine) { this.engine = engine; }

        void startJob(final JobParameters params) {
            engine.mHandler.post(new Runnable() {
                public void run() {
                    boolean workOngoing = engine.onStartJob(params);
                    if (!workOngoing) params.status.finished(params, false);
                }
            });
        }

        void stopJob(final JobParameters params) {
            engine.mHandler.post(new Runnable() {
                public void run() {
                    boolean reschedule = engine.onStopJob(params);
                    params.status.stopAcked(params, reschedule);
                }
            });
        }
    }

    public JobServiceEngine(Service service) {
        mBinder = new JobInterface(this);
        mHandler = new Handler(service != null ? service.getMainLooper() : Looper.getMainLooper());
    }

    public final IBinder getBinder() { return mBinder; }

    public abstract boolean onStartJob(JobParameters params);

    public abstract boolean onStopJob(JobParameters params);

    public void jobFinished(JobParameters params, boolean needsReschedule) {
        if (params == null) throw new NullPointerException("params");
        params.status.finished(params, needsReschedule);
    }

    public void onNetworkChanged(JobParameters params) {}

    public void updateTransferredNetworkBytes(JobParameters params, JobWorkItem item, long downloadBytes,
            long uploadBytes) {}

    public void updateEstimatedNetworkBytes(JobParameters params, JobWorkItem item, long downloadBytes,
            long uploadBytes) {}

    public void setNotification(JobParameters params, int notificationId, Notification notification,
            int jobEndNotificationPolicy) {}
}
