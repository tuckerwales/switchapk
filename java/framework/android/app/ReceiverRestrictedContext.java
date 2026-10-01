package android.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ReceiverCallNotAllowedException;
import android.content.ServiceConnection;
import android.os.Handler;
import java.util.concurrent.Executor;

/** framework-internal. As in AOSP: manifest receivers may read sticky broadcasts but not register or bind. */
class ReceiverRestrictedContext extends ContextWrapper {
    ReceiverRestrictedContext(Context base) { super(base); }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        return registerReceiver(receiver, filter, null, null);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) {
        return registerReceiver(receiver, filter, null, null, flags);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String broadcastPermission,
            Handler scheduler) {
        return registerReceiver(receiver, filter, broadcastPermission, scheduler, 0);
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String broadcastPermission,
            Handler scheduler, int flags) {
        if (receiver == null) return super.registerReceiver(null, filter, broadcastPermission, scheduler, flags);
        throw new ReceiverCallNotAllowedException(
                "BroadcastReceiver components are not allowed to register to receive intents");
    }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        throw new ReceiverCallNotAllowedException("BroadcastReceiver components are not allowed to bind to services");
    }

    @Override
    public boolean bindService(Intent service, int flags, Executor executor, ServiceConnection conn) {
        throw new ReceiverCallNotAllowedException("BroadcastReceiver components are not allowed to bind to services");
    }
}
