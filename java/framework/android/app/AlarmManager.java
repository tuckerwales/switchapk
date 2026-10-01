package android.app;

import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.concurrent.Executor;

/**
 * Alarms on the main looper. They live as long as the process does (nothing
 * wakes a closed app on the Switch); a PendingIntent or listener that is set
 * again replaces its earlier alarm, as on Android. Inexact and while-idle
 * variants fire at their trigger time.
 */
public class AlarmManager {
    private static final String TAG = "AlarmManager";

    public static final int RTC_WAKEUP = 0;
    public static final int RTC = 1;
    public static final int ELAPSED_REALTIME_WAKEUP = 2;
    public static final int ELAPSED_REALTIME = 3;

    public static final long INTERVAL_FIFTEEN_MINUTES = 15 * 60 * 1000;
    public static final long INTERVAL_HALF_HOUR = 2 * INTERVAL_FIFTEEN_MINUTES;
    public static final long INTERVAL_HOUR = 2 * INTERVAL_HALF_HOUR;
    public static final long INTERVAL_HALF_DAY = 12 * INTERVAL_HOUR;
    public static final long INTERVAL_DAY = 2 * INTERVAL_HALF_DAY;

    public static final String ACTION_NEXT_ALARM_CLOCK_CHANGED = "android.app.action.NEXT_ALARM_CLOCK_CHANGED";
    public static final String ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED";

    private static AlarmManager sInstance;

    private final ArrayList<Alarm> mAlarms = new ArrayList<Alarm>();
    private Handler mHandler;

    public interface OnAlarmListener {
        void onAlarm();
    }

    public static final class AlarmClockInfo implements Parcelable {
        private final long mTriggerTime;
        private final PendingIntent mShowIntent;

        public AlarmClockInfo(long triggerTime, PendingIntent showIntent) {
            mTriggerTime = triggerTime;
            mShowIntent = showIntent;
        }

        public long getTriggerTime() { return mTriggerTime; }

        public PendingIntent getShowIntent() { return mShowIntent; }

        public int describeContents() { return 0; }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(mTriggerTime);
            PendingIntent.writePendingIntentOrNullToParcel(mShowIntent, dest);
        }

        public static final Parcelable.Creator<AlarmClockInfo> CREATOR = new Parcelable.Creator<AlarmClockInfo>() {
            public AlarmClockInfo createFromParcel(Parcel in) {
                long time = in.readLong();
                return new AlarmClockInfo(time, PendingIntent.readPendingIntentOrNullFromParcel(in));
            }

            public AlarmClockInfo[] newArray(int size) { return new AlarmClockInfo[size]; }
        };
    }

    private final class Alarm implements Runnable {
        final int type;
        /** Trigger time on the elapsed realtime clock. */
        long whenElapsed;
        final long interval;
        final PendingIntent operation;
        final OnAlarmListener listener;
        final Handler listenerHandler;
        final Executor listenerExecutor;
        final AlarmClockInfo alarmClock;

        Alarm(int type, long whenElapsed, long interval, PendingIntent operation, OnAlarmListener listener,
                Handler listenerHandler, Executor listenerExecutor, AlarmClockInfo alarmClock) {
            this.type = type;
            this.whenElapsed = whenElapsed;
            this.interval = interval;
            this.operation = operation;
            this.listener = listener;
            this.listenerHandler = listenerHandler;
            this.listenerExecutor = listenerExecutor;
            this.alarmClock = alarmClock;
        }

        public void run() { fire(this); }
    }

    AlarmManager() {}

    static synchronized AlarmManager getInstance() {
        if (sInstance == null) sInstance = new AlarmManager();
        return sInstance;
    }

    public void set(int type, long triggerAtMillis, PendingIntent operation) {
        setImpl(type, triggerAtMillis, 0, operation, null, null, null, null);
    }

    public void set(int type, long triggerAtMillis, String tag, OnAlarmListener listener, Handler targetHandler) {
        setImpl(type, triggerAtMillis, 0, null, listener, targetHandler, null, null);
    }

    public void setRepeating(int type, long triggerAtMillis, long intervalMillis, PendingIntent operation) {
        setImpl(type, triggerAtMillis, intervalMillis, operation, null, null, null, null);
    }

    public void setWindow(int type, long windowStartMillis, long windowLengthMillis, PendingIntent operation) {
        setImpl(type, windowStartMillis, 0, operation, null, null, null, null);
    }

    public void setWindow(int type, long windowStartMillis, long windowLengthMillis, String tag,
            OnAlarmListener listener, Handler targetHandler) {
        setImpl(type, windowStartMillis, 0, null, listener, targetHandler, null, null);
    }

    public void setWindow(int type, long windowStartMillis, long windowLengthMillis, String tag, Executor executor,
            OnAlarmListener listener) {
        setImpl(type, windowStartMillis, 0, null, listener, null, executor, null);
    }

    public void setExact(int type, long triggerAtMillis, PendingIntent operation) {
        setImpl(type, triggerAtMillis, 0, operation, null, null, null, null);
    }

    public void setExact(int type, long triggerAtMillis, String tag, OnAlarmListener listener, Handler targetHandler) {
        setImpl(type, triggerAtMillis, 0, null, listener, targetHandler, null, null);
    }

    public void setAlarmClock(AlarmClockInfo info, PendingIntent operation) {
        setImpl(RTC_WAKEUP, info.getTriggerTime(), 0, operation, null, null, null, info);
    }

    public void setInexactRepeating(int type, long triggerAtMillis, long intervalMillis, PendingIntent operation) {
        setImpl(type, triggerAtMillis, intervalMillis, operation, null, null, null, null);
    }

    public void setAndAllowWhileIdle(int type, long triggerAtMillis, PendingIntent operation) {
        setImpl(type, triggerAtMillis, 0, operation, null, null, null, null);
    }

    public void setExactAndAllowWhileIdle(int type, long triggerAtMillis, PendingIntent operation) {
        setImpl(type, triggerAtMillis, 0, operation, null, null, null, null);
    }

    public void cancel(PendingIntent operation) {
        if (operation == null) throw new NullPointerException("cancel() called with a null PendingIntent");
        synchronized (mAlarms) { removeLocked(operation, null); }
    }

    public void cancel(OnAlarmListener listener) {
        if (listener == null) throw new NullPointerException("cancel() called with a null OnAlarmListener");
        synchronized (mAlarms) { removeLocked(null, listener); }
    }

    public void cancelAll() {
        synchronized (mAlarms) {
            for (int i = 0; i < mAlarms.size(); i++) handler().removeCallbacks(mAlarms.get(i));
            mAlarms.clear();
        }
    }

    public void setTime(long millis) { Log.w(TAG, "setTime is not allowed"); }

    public void setTimeZone(String timeZone) { Log.w(TAG, "setTimeZone is not allowed"); }

    public boolean canScheduleExactAlarms() { return true; }

    public AlarmClockInfo getNextAlarmClock() {
        synchronized (mAlarms) {
            Alarm next = null;
            for (int i = 0; i < mAlarms.size(); i++) {
                Alarm a = mAlarms.get(i);
                if (a.alarmClock != null && (next == null || a.whenElapsed < next.whenElapsed)) next = a;
            }
            return next != null ? next.alarmClock : null;
        }
    }

    private void setImpl(int type, long triggerAtMillis, long interval, PendingIntent operation,
            OnAlarmListener listener, Handler handler, Executor executor, AlarmClockInfo clock) {
        if (operation == null && listener == null) {
            Log.w(TAG, "set/setRepeating ignored because there is neither an operation nor a listener");
            return;
        }
        if (triggerAtMillis < 0) triggerAtMillis = 0;
        long nowElapsed = SystemClock.elapsedRealtime();
        long whenElapsed = type == RTC || type == RTC_WAKEUP
                ? nowElapsed + (triggerAtMillis - System.currentTimeMillis()) : triggerAtMillis;
        Alarm alarm = new Alarm(type, whenElapsed, interval, operation, listener, handler, executor, clock);
        synchronized (mAlarms) {
            removeLocked(operation, listener);
            mAlarms.add(alarm);
            schedule(alarm);
        }
    }

    private void removeLocked(PendingIntent operation, OnAlarmListener listener) {
        for (int i = mAlarms.size() - 1; i >= 0; i--) {
            Alarm a = mAlarms.get(i);
            if ((operation != null && operation.equals(a.operation)) || (listener != null && listener == a.listener)) {
                handler().removeCallbacks(a);
                mAlarms.remove(i);
            }
        }
    }

    private void schedule(Alarm alarm) {
        long delay = Math.max(0, alarm.whenElapsed - SystemClock.elapsedRealtime());
        handler().postAtTime(alarm, SystemClock.uptimeMillis() + delay);
    }

    private Handler handler() {
        if (mHandler == null) mHandler = new Handler(android.os.Looper.getMainLooper());
        return mHandler;
    }

    private void fire(Alarm alarm) {
        synchronized (mAlarms) {
            if (!mAlarms.contains(alarm)) return;
            if (alarm.interval > 0) {
                // Repeating: the next trigger after now, skipping the ones missed.
                long now = SystemClock.elapsedRealtime();
                long missed = Math.max(0, (now - alarm.whenElapsed) / alarm.interval);
                alarm.whenElapsed += (missed + 1) * alarm.interval;
                schedule(alarm);
            } else {
                mAlarms.remove(alarm);
            }
        }
        if (alarm.operation != null) {
            try {
                alarm.operation.send();
            } catch (PendingIntent.CanceledException e) {
                synchronized (mAlarms) { removeLocked(alarm.operation, null); }
            }
            return;
        }
        final OnAlarmListener listener = alarm.listener;
        Runnable r = new Runnable() {
            public void run() { listener.onAlarm(); }
        };
        if (alarm.listenerExecutor != null) alarm.listenerExecutor.execute(r);
        else if (alarm.listenerHandler != null && alarm.listenerHandler.getLooper() != handler().getLooper()) {
            alarm.listenerHandler.post(r);
        } else {
            r.run();
        }
    }
}
