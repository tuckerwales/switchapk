package android.app;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import java.util.ArrayList;

/**
 * framework-internal. In-process broadcasts, standing in for the AMS broadcast
 * queue and LoadedApk's receiver dispatchers.
 *
 * As on Android: delivery is always asynchronous (posted to the receiver's
 * handler, the main looper by default). A normal broadcast reaches registered
 * receivers in parallel, then manifest receivers one at a time. An ordered
 * broadcast reaches every receiver one at a time by priority (registered before
 * manifest at equal priority), carrying the result and stopping on abort, then
 * the result receiver. Apps targeting O and later get implicit broadcasts only
 * through registered receivers. A receiver registered through an activity or
 * service is dropped, with a leak warning, when that component is destroyed.
 */
final class BroadcastQueue {
    private static final String TAG = "BroadcastQueue";

    private static final ArrayList<Registration> sRegistered = new ArrayList<Registration>();
    private static final ArrayList<Intent> sSticky = new ArrayList<Intent>();
    private static boolean sStickyInit;

    private BroadcastQueue() {}

    private static final class Registration {
        final Context context;
        final BroadcastReceiver receiver;
        final IntentFilter filter;
        final Handler handler;

        Registration(Context context, BroadcastReceiver receiver, IntentFilter filter, Handler handler) {
            this.context = context;
            this.receiver = receiver;
            this.filter = filter;
            this.handler = handler;
        }
    }

    /** One receiver of one broadcast: a registration or a manifest receiver class. */
    private static final class Target {
        final Registration reg;
        final ActivityThread.ParsedActivity parsed;
        final int priority;

        Target(Registration reg) {
            this.reg = reg;
            this.parsed = null;
            this.priority = reg.filter.getPriority();
        }

        Target(ActivityThread.ParsedActivity parsed, int priority) {
            this.reg = null;
            this.parsed = parsed;
            this.priority = priority;
        }
    }

    /** A broadcast being delivered to its serial receivers, one at a time. */
    private static final class Serial {
        final Intent intent;
        final boolean ordered;
        final ArrayList<Target> targets;
        final BroadcastReceiver resultTo;
        final Handler resultHandler;
        final Context resultContext;
        int next;
        int resultCode;
        String resultData;
        Bundle resultExtras;

        Serial(Intent intent, boolean ordered, ArrayList<Target> targets, BroadcastReceiver resultTo,
                Handler resultHandler, int code, String data, Bundle extras) {
            this.intent = intent;
            this.ordered = ordered;
            this.targets = targets;
            this.resultTo = resultTo;
            this.resultHandler = resultHandler;
            this.resultContext = ActivityThread.sApplication;
            this.resultCode = code;
            this.resultData = data;
            this.resultExtras = extras;
        }
    }

    // ---------------------------------------------------------------- registration

    static Intent register(Context context, BroadcastReceiver receiver, IntentFilter filter, Handler scheduler,
            int flags) {
        if (filter == null) throw new NullPointerException("filter");
        ArrayList<Intent> sticky = new ArrayList<Intent>();
        synchronized (sRegistered) {
            initSticky();
            if (filter.hasAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION)) {
                // the current state, kept up to date from now on by ConnectivityManager (WS11)
                Intent connectivity = android.net.ConnectivityManager.stickyConnectivityIntent(context);
                removeStickyLocked(connectivity);
                sSticky.add(connectivity);
            }
            if (watchesBattery(filter)) {
                // the current level, kept up to date while a receiver listens (WS15)
                Intent battery = BatteryManager.stickyBatteryIntent(receiver != null ? context : null);
                removeStickyLocked(battery);
                sSticky.add(battery);
            }
            for (int i = 0; i < sSticky.size(); i++) {
                Intent s = sSticky.get(i);
                if (filterMatches(filter, s)) sticky.add(s);
            }
            if (receiver == null) return sticky.isEmpty() ? null : new Intent(sticky.get(0));
            sRegistered.add(new Registration(context, receiver, filter, scheduler));
        }
        // Matching sticky broadcasts are delivered to the new receiver as initial sticky ones.
        Registration reg = findRegistration(receiver, filter);
        for (int i = 0; i < sticky.size(); i++) deliverParallel(reg, new Intent(sticky.get(i)), true);
        return sticky.isEmpty() ? null : new Intent(sticky.get(0));
    }

    static void unregister(Context context, BroadcastReceiver receiver) {
        if (receiver == null) throw new IllegalArgumentException("Receiver not registered: null");
        boolean found = false;
        synchronized (sRegistered) {
            for (int i = sRegistered.size() - 1; i >= 0; i--) {
                Registration reg = sRegistered.get(i);
                if (reg.receiver == receiver && reg.context == context) {
                    sRegistered.remove(i);
                    found = true;
                }
            }
        }
        if (!found) throw new IllegalArgumentException("Receiver not registered: " + receiver);
    }

    static void removeContextRegistrations(Context context, String who, String what) {
        synchronized (sRegistered) {
            for (int i = 0; i < sRegistered.size(); i++) {
                Registration reg = sRegistered.get(i);
                if (reg.context != context) continue;
                Log.e("ActivityThread", what + " " + who + " has leaked IntentReceiver " + reg.receiver
                        + " that was originally registered here. Are you missing a call to unregisterReceiver()?");
                for (int j = sRegistered.size() - 1; j >= i; j--) {
                    if (sRegistered.get(j).receiver == reg.receiver && sRegistered.get(j).context == context) {
                        sRegistered.remove(j);
                    }
                }
                i--;
            }
        }
    }

    private static Registration findRegistration(BroadcastReceiver receiver, IntentFilter filter) {
        synchronized (sRegistered) {
            for (int i = sRegistered.size() - 1; i >= 0; i--) {
                Registration reg = sRegistered.get(i);
                if (reg.receiver == receiver && reg.filter == filter) return reg;
            }
        }
        return null;
    }

    private static boolean isRegistered(Registration reg) {
        synchronized (sRegistered) { return sRegistered.contains(reg); }
    }

    // ---------------------------------------------------------------- sticky

    static void sendSticky(Intent intent) {
        addSticky(intent);
        send(intent, false, null, null, Activity.RESULT_OK, null, null);
    }

    static void addSticky(Intent intent) {
        synchronized (sRegistered) {
            initSticky();
            removeStickyLocked(intent);
            sSticky.add(new Intent(intent));
        }
    }

    static void removeSticky(Intent intent) {
        synchronized (sRegistered) { removeStickyLocked(intent); }
    }

    private static void removeStickyLocked(Intent intent) {
        for (int i = sSticky.size() - 1; i >= 0; i--) {
            if (sSticky.get(i).filterEquals(intent)) sSticky.remove(i);
        }
    }

    /** The system's sticky broadcasts apps read with registerReceiver(null, filter). */
    private static void initSticky() {
        if (sStickyInit) return;
        sStickyInit = true;
        sSticky.add(BatteryManager.stickyBatteryIntent(null));
    }

    private static boolean watchesBattery(IntentFilter filter) {
        for (int i = 0; i < filter.countActions(); i++) {
            if (BatteryManager.isBatteryAction(filter.getAction(i))) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- sending

    static void send(Intent intent, boolean ordered, BroadcastReceiver resultTo, Handler resultHandler, int code,
            String data, Bundle extras) {
        if (intent == null) throw new NullPointerException("intent");
        final Intent copy = new Intent(intent);
        ArrayList<Target> registered = new ArrayList<Target>();
        ArrayList<Target> manifest = new ArrayList<Target>();
        ComponentName component = copy.getComponent();
        if (component == null) {
            synchronized (sRegistered) {
                for (int i = 0; i < sRegistered.size(); i++) {
                    Registration reg = sRegistered.get(i);
                    if (filterMatches(reg.filter, copy)) insertByPriority(registered, new Target(reg));
                }
            }
        }
        if ((copy.getFlags() & Intent.FLAG_RECEIVER_REGISTERED_ONLY) == 0) collectManifest(copy, manifest);
        if (!ordered) {
            for (int i = 0; i < registered.size(); i++) deliverParallel(registered.get(i).reg, new Intent(copy), false);
            registered.clear();
        }
        ArrayList<Target> serial = new ArrayList<Target>(registered);
        // Registered receivers go first at equal priority.
        for (int i = 0; i < manifest.size(); i++) {
            Target t = manifest.get(i);
            int at = serial.size();
            for (int j = 0; j < serial.size(); j++) {
                if (t.priority > serial.get(j).priority) {
                    at = j;
                    break;
                }
            }
            serial.add(at, t);
        }
        if (serial.isEmpty() && resultTo == null) return;
        final Serial s = new Serial(copy, ordered, serial, resultTo, resultHandler, code, data, extras);
        ActivityThread.post(new Runnable() {
            public void run() { next(s); }
        });
    }

    private static void collectManifest(Intent intent, ArrayList<Target> out) {
        ComponentName component = intent.getComponent();
        boolean implicit = component == null && intent.getPackage() == null;
        ApplicationInfo app = ActivityThread.sAppInfo;
        boolean blocked = implicit && app != null && app.targetSdkVersion >= 26;
        ArrayList<ActivityThread.ParsedActivity> receivers = ActivityThread.sReceivers;
        for (int i = 0; i < receivers.size(); i++) {
            ActivityThread.ParsedActivity parsed = receivers.get(i);
            int priority = 0;
            if (component != null) {
                if (!ActivityThread.sameComponent(parsed.info, component)) continue;
            } else {
                IntentFilter match = null;
                for (int j = 0; j < parsed.filters.size(); j++) {
                    if (filterMatches(parsed.filters.get(j), intent)) {
                        match = parsed.filters.get(j);
                        break;
                    }
                }
                if (match == null) continue;
                if (blocked) {
                    Log.w(TAG, "Background execution not allowed: receiving " + intent + " to "
                            + parsed.info.packageName + "/" + parsed.info.name);
                    continue;
                }
                priority = match.getPriority();
            }
            insertByPriority(out, new Target(parsed, priority));
        }
    }

    private static void insertByPriority(ArrayList<Target> list, Target t) {
        int at = list.size();
        for (int i = 0; i < list.size(); i++) {
            if (t.priority > list.get(i).priority) {
                at = i;
                break;
            }
        }
        list.add(at, t);
    }

    private static boolean filterMatches(IntentFilter filter, Intent intent) {
        if (intent.getAction() == null) return false;
        return filter.match(null, intent, false, TAG) >= 0;
    }

    private static void deliverParallel(final Registration reg, final Intent intent, final boolean sticky) {
        if (reg == null) return;
        Runnable r = new Runnable() {
            public void run() {
                if (!isRegistered(reg)) return;
                BroadcastReceiver receiver = reg.receiver;
                BroadcastReceiver.PendingResult result = new BroadcastReceiver.PendingResult(
                        Activity.RESULT_OK, null, null, false, sticky, null);
                receiver.setPendingResult(result);
                receiver.onReceive(reg.context, intent);
                receiver.setPendingResult(null);
            }
        };
        if (reg.handler != null) reg.handler.post(r);
        else ActivityThread.post(r);
    }

    /** Main thread: deliver the serial broadcast to its next receiver, or finish it. */
    private static void next(final Serial s) {
        while (s.next < s.targets.size()) {
            final Target t = s.targets.get(s.next++);
            if (t.reg != null && !isRegistered(t.reg)) continue;
            final BroadcastReceiver.PendingResult[] holder = new BroadcastReceiver.PendingResult[1];
            final BroadcastReceiver.PendingResult result = new BroadcastReceiver.PendingResult(
                    s.resultCode, s.resultData, s.resultExtras, s.ordered, false, new Runnable() {
                        public void run() {
                            ActivityThread.post(new Runnable() {
                                public void run() { finished(s, holder[0]); }
                            });
                        }
                    });
            holder[0] = result;
            if (t.reg != null) {
                Runnable r = new Runnable() {
                    public void run() { receive(t.reg.receiver, t.reg.context, s.intent, result); }
                };
                if (t.reg.handler != null) t.reg.handler.post(r);
                else r.run();
            } else {
                Object obj = ActivityThread.newComponent(t.parsed.info.name);
                if (!(obj instanceof BroadcastReceiver)) {
                    Log.e(TAG, "Not a receiver: " + t.parsed.info.name);
                    continue;
                }
                receive((BroadcastReceiver) obj, ActivityThread.sContext.getReceiverRestrictedContext(),
                        new Intent(s.intent), result);
            }
            return;
        }
        if (s.resultTo != null) {
            Runnable r = new Runnable() {
                public void run() {
                    BroadcastReceiver.PendingResult result = new BroadcastReceiver.PendingResult(
                            s.resultCode, s.resultData, s.resultExtras, false, false, null);
                    s.resultTo.setPendingResult(result);
                    s.resultTo.onReceive(s.resultContext, new Intent(s.intent));
                    s.resultTo.setPendingResult(null);
                }
            };
            if (s.resultHandler != null) s.resultHandler.post(r);
            else r.run();
        }
    }

    private static void receive(BroadcastReceiver receiver, Context context, Intent intent,
            BroadcastReceiver.PendingResult result) {
        receiver.setPendingResult(result);
        receiver.onReceive(context, intent);
        // Unless the receiver took the result with goAsync, the broadcast moves on now.
        if (receiver.getPendingResult() != null) {
            receiver.setPendingResult(null);
            result.finish();
        }
    }

    private static void finished(Serial s, BroadcastReceiver.PendingResult result) {
        if (result != null) {
            s.resultCode = result.getResultCode();
            s.resultData = result.getResultData();
            s.resultExtras = result.getResultExtras(false);
            if (s.ordered && result.getAbortBroadcast()
                    && (s.intent.getFlags() & Intent.FLAG_RECEIVER_NO_ABORT) == 0) {
                s.next = s.targets.size();
            }
        }
        next(s);
    }
}
