package android.app;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Port of AOSP Service; ActiveServices runs it in process on the main looper. */
public abstract class Service extends ContextWrapper implements ComponentCallbacks2 {
    public static final int START_CONTINUATION_MASK = 0xf;
    public static final int START_STICKY_COMPATIBILITY = 0;
    public static final int START_STICKY = 1;
    public static final int START_NOT_STICKY = 2;
    public static final int START_REDELIVER_INTENT = 3;
    public static final int START_FLAG_REDELIVERY = 0x0001;
    public static final int START_FLAG_RETRY = 0x0002;
    public static final int STOP_FOREGROUND_LEGACY = 0;
    public static final int STOP_FOREGROUND_REMOVE = 1 << 0;
    public static final int STOP_FOREGROUND_DETACH = 1 << 1;

    private ActiveServices.ServiceRecord mRecord;
    private Application mApplication;
    private int mForegroundId;
    private int mForegroundServiceType;

    public Service() { super(null); }

    /** framework-internal. Called by ActiveServices before onCreate. */
    final void attach(Context context, ActiveServices.ServiceRecord record, Application application) {
        attachBaseContext(context);
        mRecord = record;
        mApplication = application;
    }

    public final Application getApplication() { return mApplication; }

    public void onCreate() {}

    @Deprecated
    public void onStart(Intent intent, int startId) {}

    public int onStartCommand(Intent intent, int flags, int startId) {
        onStart(intent, startId);
        return START_STICKY;
    }

    public void onDestroy() {}

    public void onConfigurationChanged(Configuration newConfig) {}

    public void onLowMemory() {}

    public void onTrimMemory(int level) {}

    public abstract IBinder onBind(Intent intent);

    public boolean onUnbind(Intent intent) { return false; }

    public void onRebind(Intent intent) {}

    public void onTaskRemoved(Intent rootIntent) {}

    public final void stopSelf() { stopSelf(-1); }

    public final void stopSelf(int startId) { ActiveServices.stopSelf(mRecord, startId); }

    public final boolean stopSelfResult(int startId) { return ActiveServices.stopSelf(mRecord, startId); }

    public final void startForeground(int id, Notification notification) { startForeground(id, notification, 0); }

    public final void startForeground(int id, Notification notification, int foregroundServiceType) {
        if (id == 0) throw new IllegalArgumentException("Notification id must not be 0");
        mForegroundId = id;
        mForegroundServiceType = foregroundServiceType;
        NotificationManager.getInstance().notify(null, id, notification);
    }

    @Deprecated
    public final void stopForeground(boolean removeNotification) {
        stopForeground(removeNotification ? STOP_FOREGROUND_REMOVE : STOP_FOREGROUND_LEGACY);
    }

    public final void stopForeground(int notificationBehavior) {
        if (mForegroundId != 0 && (notificationBehavior & STOP_FOREGROUND_REMOVE) != 0) {
            NotificationManager.getInstance().cancel(null, mForegroundId);
        }
        mForegroundId = 0;
        mForegroundServiceType = 0;
    }

    public final int getForegroundServiceType() { return mForegroundServiceType; }

    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("nothing to dump");
    }

    @Override
    protected void attachBaseContext(Context newBase) { super.attachBaseContext(newBase); }

    public void onTimeout(int startId) {}

    public void onTimeout(int startId, int fgsType) {}
}
