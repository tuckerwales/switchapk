package com.example.services;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.app.job.JobWorkItem;
import android.os.SystemClock;

/** Job 1 finishes from a thread, job 4 drains its work queue, job 5 waits to be stopped. */
public class TestJobService extends JobService {
    @Override
    public void onCreate() { T.ev("jobsvc onCreate"); }

    @Override
    public void onDestroy() { T.ev("jobsvc onDestroy"); }

    @Override
    public boolean onStartJob(final JobParameters params) {
        T.ev("job start " + params.getJobId() + " n=" + params.getExtras().getInt("n", -1) + " deadline="
                + params.isOverrideDeadlineExpired());
        switch (params.getJobId()) {
            case 1:
                new Thread(() -> {
                    SystemClock.sleep(50);
                    jobFinished(params, false);
                }).start();
                return true;
            case 4:
                new Thread(() -> {
                    JobWorkItem work;
                    while ((work = params.dequeueWork()) != null) {
                        T.ev("work " + work.getIntent().getStringExtra("w") + " delivery=" + work.getDeliveryCount());
                        params.completeWork(work);
                    }
                }).start();
                return true;
            case 5:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        T.ev("job stop " + params.getJobId() + " reason=" + params.getStopReason());
        return false;
    }
}
