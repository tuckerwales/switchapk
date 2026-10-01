package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executor;

/**
 * framework-internal. In-process services, standing in for AMS ActiveServices and
 * LoadedApk's service dispatchers.
 *
 * Bookkeeping is synchronous and thread-safe (an IntentService stops itself from
 * its worker thread); every call into the service or a client is posted to the
 * main looper in the order AMS would schedule it: create, then start arguments or
 * bind; onBind runs once per distinct intent (Intent.filterEquals) and its binder
 * is handed to every connection for that intent; onUnbind runs when the last
 * connection for an intent goes; a service is destroyed once it is neither started
 * nor bound with BIND_AUTO_CREATE. Connections still attached when it is destroyed
 * are told the binding died. Connections made through an activity or service are
 * dropped, with a leak warning, when that component is destroyed.
 */
final class ActiveServices {
    private static final String TAG = "ActiveServices";
    private static final Object sLock = new Object();
    private static final HashMap<String, ServiceRecord> sRecords = new HashMap<String, ServiceRecord>();
    private static final ArrayList<Dispatcher> sDispatchers = new ArrayList<Dispatcher>();

    private ActiveServices() {}

    static final class ServiceRecord {
        final ActivityThread.ParsedService parsed;
        final ComponentName name;
        /** The instance; set on the main thread when created. */
        Service service;
        /** Bookkeeping: a create has been scheduled and no destroy since. */
        boolean created;
        boolean startRequested;
        int lastStartId;
        final ArrayList<IntentBindRecord> bindings = new ArrayList<IntentBindRecord>();

        ServiceRecord(ActivityThread.ParsedService parsed) {
            this.parsed = parsed;
            this.name = new ComponentName(parsed.info.packageName, parsed.info.name);
        }
    }

    static final class IntentBindRecord {
        final Intent intent;
        final Intent.FilterComparison key;
        final ArrayList<ConnectionRecord> connections = new ArrayList<ConnectionRecord>();
        IBinder binder;
        boolean requested;
        boolean received;
        boolean hasBound;
        boolean doRebind;

        IntentBindRecord(Intent intent) {
            this.intent = intent;
            this.key = new Intent.FilterComparison(intent);
        }
    }

    static final class ConnectionRecord {
        final Dispatcher dispatcher;
        final int flags;
        ServiceRecord service;
        IntentBindRecord binding;

        ConnectionRecord(Dispatcher dispatcher, int flags) {
            this.dispatcher = dispatcher;
            this.flags = flags;
        }
    }

    /** Client side, one per (context, ServiceConnection), like LoadedApk.ServiceDispatcher. */
    static final class Dispatcher {
        final Context context;
        final ServiceConnection connection;
        final Executor executor;
        final ArrayList<ConnectionRecord> records = new ArrayList<ConnectionRecord>();
        final HashMap<ComponentName, IBinder> active = new HashMap<ComponentName, IBinder>();
        boolean forgotten;

        Dispatcher(Context context, ServiceConnection connection, Executor executor) {
            this.context = context;
            this.connection = connection;
            this.executor = executor;
        }

        /** Posts the connection change, as ServiceDispatcher.connected does. */
        void connected(final ComponentName name, final IBinder binder, final boolean dead) {
            Runnable r = new Runnable() {
                public void run() { doConnected(name, binder, dead); }
            };
            if (executor != null) executor.execute(r);
            else ActivityThread.post(r);
        }

        private void doConnected(ComponentName name, IBinder binder, boolean dead) {
            IBinder old;
            synchronized (sLock) {
                if (forgotten) return;
                old = active.get(name);
                if (old != null && old == binder) return;
                if (binder != null) active.put(name, binder);
                else active.remove(name);
            }
            if (old != null) connection.onServiceDisconnected(name);
            if (dead) connection.onBindingDied(name);
            else if (binder != null) connection.onServiceConnected(name, binder);
            else connection.onNullBinding(name);
        }
    }

    // ---------------------------------------------------------------- resolution

    private static ServiceRecord retrieve(Intent intent) {
        if (intent == null) throw new IllegalArgumentException("service intent is null");
        if (intent.getComponent() == null && intent.getPackage() == null) {
            ApplicationInfo app = ActivityThread.sAppInfo;
            if (app != null && app.targetSdkVersion >= 21) {
                throw new IllegalArgumentException("Service Intent must be explicit: " + intent);
            }
        }
        ArrayList<ActivityThread.ParsedService> services = ActivityThread.sServices;
        for (int i = 0; i < services.size(); i++) {
            ActivityThread.ParsedService parsed = services.get(i);
            if (!ActivityThread.matchesService(parsed, intent)) continue;
            ServiceRecord r = sRecords.get(parsed.info.name);
            if (r == null) {
                r = new ServiceRecord(parsed);
                sRecords.put(parsed.info.name, r);
            }
            return r;
        }
        Log.w(TAG, "Unable to start service " + intent + " U=0: not found");
        return null;
    }

    // ---------------------------------------------------------------- started

    static ComponentName startService(Intent intent) {
        synchronized (sLock) {
            ServiceRecord r = retrieve(intent);
            if (r == null) return null;
            bringUp(r);
            r.startRequested = true;
            final int startId = ++r.lastStartId;
            final ServiceRecord rec = r;
            final Intent copy = new Intent(intent);
            ActivityThread.post(new Runnable() {
                public void run() {
                    Service s = rec.service;
                    if (s == null) return;
                    s.onStartCommand(copy, 0, startId);
                }
            });
            return r.name;
        }
    }

    static boolean stopService(Intent intent) {
        synchronized (sLock) {
            ServiceRecord r = retrieve(intent);
            if (r == null || !r.created) return false;
            r.startRequested = false;
            bringDownIfNeeded(r);
            return true;
        }
    }

    /** Service.stopSelfResult: stops only when startId is the latest (or negative). */
    static boolean stopSelf(ServiceRecord r, int startId) {
        synchronized (sLock) {
            if (r == null || !r.created) return false;
            if (startId >= 0 && startId != r.lastStartId) return false;
            r.startRequested = false;
            bringDownIfNeeded(r);
            return true;
        }
    }

    // ---------------------------------------------------------------- bound

    static boolean bindService(Context context, Intent intent, ServiceConnection conn, int flags, Executor executor) {
        if (conn == null) throw new IllegalArgumentException("connection is null");
        synchronized (sLock) {
            ServiceRecord r = retrieve(intent);
            if (r == null) return false;
            Dispatcher d = findDispatcher(context, conn);
            if (d == null) {
                d = new Dispatcher(context, conn, executor);
                sDispatchers.add(d);
            }
            IntentBindRecord b = null;
            Intent.FilterComparison key = new Intent.FilterComparison(intent);
            for (int i = 0; i < r.bindings.size(); i++) {
                if (r.bindings.get(i).key.equals(key)) b = r.bindings.get(i);
            }
            if (b == null) {
                b = new IntentBindRecord(new Intent(intent));
                r.bindings.add(b);
            }
            ConnectionRecord c = new ConnectionRecord(d, flags);
            c.service = r;
            c.binding = b;
            b.connections.add(c);
            d.records.add(c);
            if ((flags & Context.BIND_AUTO_CREATE) != 0) bringUp(r);
            if (r.created) {
                if (b.received) {
                    // onBind ran once for this intent; its binder is cached until the service is destroyed.
                    d.connected(r.name, b.binder, false);
                    if (b.connections.size() == 1 && b.doRebind) requestBind(r, b, true);
                } else if (!b.requested) {
                    requestBind(r, b, false);
                }
            }
            return true;
        }
    }

    static void unbindService(Context context, ServiceConnection conn) {
        synchronized (sLock) {
            Dispatcher d = findDispatcher(context, conn);
            if (d == null) throw new IllegalArgumentException("Service not registered: " + conn);
            forget(d);
        }
    }

    /** BroadcastReceiver.peekService: the binder of a running service already bound with this intent. */
    static IBinder peekService(Intent intent) {
        synchronized (sLock) {
            ServiceRecord r = retrieve(intent);
            if (r == null || !r.created) return null;
            Intent.FilterComparison key = new Intent.FilterComparison(intent);
            for (int i = 0; i < r.bindings.size(); i++) {
                IntentBindRecord b = r.bindings.get(i);
                if (b.key.equals(key) && b.received) return b.binder;
            }
            return null;
        }
    }

    static void removeContextRegistrations(Context context, String who, String what) {
        synchronized (sLock) {
            for (int i = sDispatchers.size() - 1; i >= 0; i--) {
                Dispatcher d = sDispatchers.get(i);
                if (d.context != context) continue;
                Log.e("ActivityThread", what + " " + who + " has leaked ServiceConnection " + d.connection
                        + " that was originally bound here");
                forget(d);
            }
        }
    }

    private static Dispatcher findDispatcher(Context context, ServiceConnection conn) {
        for (int i = 0; i < sDispatchers.size(); i++) {
            Dispatcher d = sDispatchers.get(i);
            if (d.context == context && d.connection == conn) return d;
        }
        return null;
    }

    private static void forget(Dispatcher d) {
        d.forgotten = true;
        sDispatchers.remove(d);
        ArrayList<ConnectionRecord> records = new ArrayList<ConnectionRecord>(d.records);
        d.records.clear();
        d.active.clear();
        for (int i = 0; i < records.size(); i++) {
            ConnectionRecord c = records.get(i);
            ServiceRecord r = c.service;
            IntentBindRecord b = c.binding;
            if (r == null || b == null) continue;
            b.connections.remove(c);
            c.service = null;
            c.binding = null;
            if (b.connections.isEmpty() && b.hasBound && r.created) {
                b.hasBound = false;
                b.doRebind = false;
                scheduleUnbind(r, b);
            }
            bringDownIfNeeded(r);
        }
    }

    private static void requestBind(final ServiceRecord r, final IntentBindRecord b, final boolean rebind) {
        if (b.requested && !rebind) return;
        if (!rebind) b.requested = true;
        b.hasBound = true;
        b.doRebind = false;
        ActivityThread.post(new Runnable() {
            public void run() {
                Service s = r.service;
                if (s == null) return;
                if (rebind) {
                    s.onRebind(b.intent);
                    return;
                }
                IBinder binder = s.onBind(new Intent(b.intent));
                publish(r, b, binder);
            }
        });
    }

    private static void publish(ServiceRecord r, IntentBindRecord b, IBinder binder) {
        synchronized (sLock) {
            if (!r.created || !r.bindings.contains(b)) return;
            b.binder = binder;
            b.received = true;
            for (int i = 0; i < b.connections.size(); i++) {
                b.connections.get(i).dispatcher.connected(r.name, binder, false);
            }
        }
    }

    private static void scheduleUnbind(final ServiceRecord r, final IntentBindRecord b) {
        ActivityThread.post(new Runnable() {
            public void run() {
                Service s = r.service;
                if (s == null) return;
                boolean doRebind = s.onUnbind(new Intent(b.intent));
                if (!doRebind) return;
                synchronized (sLock) {
                    // Clients bound again while onUnbind was pending: rebind right away.
                    if (!b.connections.isEmpty() && r.created) requestBind(r, b, true);
                    else b.doRebind = true;
                }
            }
        });
    }

    // ---------------------------------------------------------------- lifecycle

    /** Main thread: running services hear about configuration changes, as ComponentCallbacks. */
    static void dispatchConfigurationChanged(android.content.res.Configuration config) {
        ArrayList<Service> running = new ArrayList<Service>();
        synchronized (sLock) {
            for (ServiceRecord r : sRecords.values()) {
                if (r.service != null) running.add(r.service);
            }
        }
        for (int i = 0; i < running.size(); i++) {
            running.get(i).onConfigurationChanged(new android.content.res.Configuration(config));
        }
    }

    /** Schedules the create, and binds connections that were waiting for the service to run. */
    private static void bringUp(final ServiceRecord r) {
        if (r.created) return;
        r.created = true;
        ActivityThread.post(new Runnable() {
            public void run() { create(r); }
        });
        for (int i = 0; i < r.bindings.size(); i++) {
            IntentBindRecord b = r.bindings.get(i);
            if (!b.connections.isEmpty()) requestBind(r, b, false);
        }
    }

    private static void create(ServiceRecord r) {
        Object obj = ActivityThread.newComponent(r.parsed.info.name);
        if (!(obj instanceof Service)) throw new RuntimeException("Unable to instantiate service " + r.name
                + ": not a Service");
        Service s = (Service) obj;
        ContextImpl context = ActivityThread.sContext.createComponentContext(s);
        s.attach(context, r, ActivityThread.sApplication);
        r.service = s;
        s.onCreate();
    }

    private static void bringDownIfNeeded(ServiceRecord r) {
        if (!r.created || r.startRequested) return;
        for (int i = 0; i < r.bindings.size(); i++) {
            ArrayList<ConnectionRecord> conns = r.bindings.get(i).connections;
            for (int j = 0; j < conns.size(); j++) {
                if ((conns.get(j).flags & Context.BIND_AUTO_CREATE) != 0) return;
            }
        }
        bringDown(r);
    }

    private static void bringDown(final ServiceRecord r) {
        r.created = false;
        r.lastStartId = 0;
        // Connections still attached (bound without BIND_AUTO_CREATE) learn the binding died.
        for (int i = 0; i < r.bindings.size(); i++) {
            IntentBindRecord b = r.bindings.get(i);
            for (int j = 0; j < b.connections.size(); j++) {
                ConnectionRecord c = b.connections.get(j);
                c.dispatcher.connected(r.name, null, true);
                c.dispatcher.records.remove(c);
                c.service = null;
                c.binding = null;
            }
            b.connections.clear();
            if (b.hasBound) scheduleUnbind(r, b);
        }
        r.bindings.clear();
        ActivityThread.post(new Runnable() {
            public void run() {
                Service s = r.service;
                if (s == null) return;
                r.service = null;
                s.onDestroy();
                ((ContextImpl) s.getBaseContext()).scheduleFinalCleanup(s.getClass().getName(), "Service");
            }
        });
    }
}
