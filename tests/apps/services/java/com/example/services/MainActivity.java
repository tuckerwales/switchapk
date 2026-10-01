package com.example.services;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.Person;
import android.app.RemoteInput;
import android.graphics.drawable.Icon;
import android.service.notification.StatusBarNotification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs broadcast, service, PendingIntent and alarm checks one step at a time,
 * comparing the events each step produced with what Android delivers. Each step
 * adds a cell to the grid: green when it matched, red when not. The band at the
 * top turns green when every step passed.
 */
public class MainActivity extends Activity {
    static final String PING = "com.example.services.PING";
    static final String STICKY = "com.example.services.STICKY";
    static final int CELLS_PER_ROW = 10;

    interface Check {
        /** Returns null when the events are right, else what was wrong. */
        String check(List<String> events);
    }

    static final class Step {
        final String name;
        final Runnable action;
        final long wait;
        final Check check;

        Step(String name, long wait, Runnable action, Check check) {
            this.name = name;
            this.wait = wait;
            this.action = action;
            this.check = check;
        }
    }

    private final Handler mHandler = new Handler();
    private final ArrayList<Step> mSteps = new ArrayList<Step>();
    private int mNext;
    private int mFailed;
    private View mBand;
    private LinearLayout mGrid;
    private LinearLayout mRow;

    private BroadcastReceiver mR1;
    private BroadcastReceiver mR2;
    private ServiceConnection mC1;
    private ServiceConnection mC2;

    int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    static Check expect(final String... expected) {
        return events -> events.equals(Arrays.asList(expected)) ? null
                : "expected " + Arrays.asList(expected) + " got " + events;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        mBand = new View(this);
        mBand.setBackgroundColor(Color.GRAY);
        root.addView(mBand, new LinearLayout.LayoutParams(-1, dp(40)));
        TextView title = new TextView(this);
        title.setText("Services and broadcasts");
        title.setTextSize(18);
        root.addView(title);
        mGrid = new LinearLayout(this);
        mGrid.setOrientation(LinearLayout.VERTICAL);
        root.addView(mGrid);
        setContentView(root);

        mR1 = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String tag = intent.getStringExtra("tag");
                T.ev("r1 " + tag);
                if (!isOrderedBroadcast()) return;
                if ("async".equals(tag)) {
                    final PendingResult result = goAsync();
                    new Thread(() -> {
                        SystemClock.sleep(50);
                        result.setResultData(result.getResultData() + "A");
                        result.finish();
                    }).start();
                } else {
                    setResultData(getResultData() + "1");
                }
            }
        };
        mR2 = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String tag = intent.getStringExtra("tag");
                T.ev("r2 " + tag);
                if (!isOrderedBroadcast()) return;
                setResultCode(5);
                setResultData(getResultData() + "2");
                if ("abort".equals(tag)) abortBroadcast();
            }
        };
        mC1 = connection("c1");
        mC2 = connection("c2");
        buildSteps();
        mHandler.postDelayed(this::runNext, 300);
    }

    private ServiceConnection connection(final String name) {
        return new ServiceConnection() {
            public void onServiceConnected(ComponentName cn, IBinder service) {
                int sum = ((BindService.LocalBinder) service).getService().add(2, 3);
                T.ev(name + " connected " + sum);
            }

            public void onServiceDisconnected(ComponentName cn) { T.ev(name + " disconnected"); }
        };
    }

    private BroadcastReceiver finalReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) { T.ev("final " + getResultCode() + " " + getResultData()); }
        };
    }

    private Intent ping(String tag) { return new Intent(PING).putExtra("tag", tag); }

    private Intent explicitPing(String tag) {
        return new Intent(PING).setComponent(new ComponentName(this, ManifestReceiver.class)).putExtra("tag", tag);
    }

    private void step(String name, long wait, Runnable action, Check check) {
        mSteps.add(new Step(name, wait, action, check));
    }

    private void buildSteps() {
        step("parallel", 300, () -> {
            IntentFilter low = new IntentFilter(PING);
            IntentFilter high = new IntentFilter(PING);
            high.setPriority(10);
            registerReceiver(mR1, low);
            registerReceiver(mR2, high);
            sendBroadcast(ping("p"));
            T.ev("sent");
        }, expect("sent", "r2 p", "r1 p"));

        step("ordered", 300, () -> sendOrderedBroadcast(ping("o").setPackage(getPackageName()), null,
                finalReceiver(), null, 1, "", null),
                expect("r2 o", "manifest " + PING + " o ordered", "r1 o", "final 5 2m1"));

        step("abort", 300, () -> sendOrderedBroadcast(ping("abort").setPackage(getPackageName()), null,
                finalReceiver(), null, 1, "", null),
                expect("r2 abort", "final 5 2"));

        step("goAsync", 400, () -> sendOrderedBroadcast(ping("async").setPackage(getPackageName()), null,
                finalReceiver(), null, 1, "", null),
                expect("r2 async", "manifest " + PING + " async ordered", "r1 async", "final 5 2mA"));

        step("explicit", 300, () -> {
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            T.ev("battery " + (battery != null ? battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1));
            sendBroadcast(explicitPing("restricted"));
        }, expect("battery 100", "manifest " + PING + " restricted", "restricted register refused",
                "restricted sticky true"));

        step("sticky", 300, () -> {
            sendStickyBroadcast(new Intent(STICKY).putExtra("v", 3));
            BroadcastReceiver r3 = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    T.ev("r3 " + intent.getIntExtra("v", -1) + (isInitialStickyBroadcast() ? " initial" : ""));
                    context.unregisterReceiver(this);
                }
            };
            Intent ret = registerReceiver(r3, new IntentFilter(STICKY));
            T.ev("sticky ret " + (ret != null ? ret.getIntExtra("v", -1) : -1));
            removeStickyBroadcast(new Intent(STICKY));
        }, expect("sticky ret 3", "r3 3 initial"));

        step("unregister", 300, () -> {
            unregisterReceiver(mR1);
            unregisterReceiver(mR2);
            sendBroadcast(ping("gone"));
            try {
                unregisterReceiver(mR1);
                T.ev("unregister twice allowed");
            } catch (IllegalArgumentException e) {
                T.ev("unregister twice throws");
            }
        }, expect("unregister twice throws"));

        step("started", 300, () -> {
            ComponentName cn = startService(new Intent(this, CountService.class).putExtra("n", 1));
            T.ev("started " + (cn != null ? cn.getShortClassName() : null));
            startService(new Intent(this, CountService.class).putExtra("n", 2).putExtra("stop", true));
            T.ev("unknown " + startService(new Intent().setClassName(getPackageName(), getPackageName() + ".Nope")));
            try {
                startService(new Intent("com.example.services.ANY"));
                T.ev("implicit allowed");
            } catch (IllegalArgumentException e) {
                T.ev("implicit throws");
            }
        }, expect("started .CountService", "unknown null", "implicit throws", "count onCreate", "count start 1 id=1",
                "count start 2 id=2", "count stopSelfResult(1)=false", "count stopSelfResult(2)=true",
                "count onDestroy"));

        step("bind", 300, () -> {
            T.ev("bind " + bindService(new Intent(this, BindService.class), mC1, BIND_AUTO_CREATE));
            T.ev("bind " + bindService(new Intent(this, BindService.class), mC2, BIND_AUTO_CREATE));
        }, expect("bind true", "bind true", "bind onCreate", "bind onBind", "c1 connected 5", "c2 connected 5"));

        step("unbind", 300, () -> {
            unbindService(mC1);
            unbindService(mC2);
            try {
                unbindService(mC1);
                T.ev("unbind twice allowed");
            } catch (IllegalArgumentException e) {
                T.ev("unbind twice throws");
            }
        }, expect("unbind twice throws", "bind onUnbind", "bind onDestroy"));

        step("started+bound", 300, () -> {
            startService(new Intent(this, BindService.class));
            bindService(new Intent(this, BindService.class), mC1, BIND_AUTO_CREATE);
        }, expect("bind onCreate", "bind start id=1", "bind onBind", "c1 connected 5"));

        step("unbind started", 300, () -> unbindService(mC1), expect("bind onUnbind"));

        step("rebind", 300, () -> bindService(new Intent(this, BindService.class), mC1, BIND_AUTO_CREATE),
                expect("c1 connected 5", "bind onRebind"));

        step("stop bound", 300, () -> {
            T.ev("stop " + stopService(new Intent(this, BindService.class)));
            unbindService(mC1);
        }, expect("stop true", "bind onUnbind", "bind onDestroy"));

        step("IntentService", 400, () -> {
            startService(new Intent(this, WorkService.class).putExtra("n", 1));
            startService(new Intent(this, WorkService.class).putExtra("n", 2));
        }, expect("work 1 worker", "work 2 worker", "work onDestroy"));

        step("PendingIntent", 400, () -> {
            PendingIntent p1 = PendingIntent.getBroadcast(this, 1, explicitPing("pi1"), PendingIntent.FLAG_IMMUTABLE);
            PendingIntent p1b = PendingIntent.getBroadcast(this, 1, explicitPing("other extras"),
                    PendingIntent.FLAG_IMMUTABLE);
            T.ev("pi equal " + p1.equals(p1b));
            PendingIntent p2 = PendingIntent.getBroadcast(this, 2, explicitPing("pi1"), PendingIntent.FLAG_IMMUTABLE);
            T.ev("pi distinct " + !p1.equals(p2));
            PendingIntent none = PendingIntent.getBroadcast(this, 1, explicitPing("pi1"),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
            T.ev("pi nocreate " + p1.equals(none));
            PendingIntent.getBroadcast(this, 1, explicitPing("pi1u"),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            try {
                p1.send();
                p1.cancel();
                p1.send();
                T.ev("pi canceled send allowed");
            } catch (PendingIntent.CanceledException e) {
                T.ev("pi canceled");
            }
            T.ev("pi gone " + (PendingIntent.getBroadcast(this, 1, explicitPing("pi1"),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE) == null));
            try {
                PendingIntent.getBroadcast(this, 3, ping("fin").setPackage(getPackageName()),
                        PendingIntent.FLAG_IMMUTABLE).send(this, 8, null,
                                (pi, intent, code, data, extras) -> T.ev("finished " + code + " " + data), null);
                createPendingResult(9, new Intent(), PendingIntent.FLAG_IMMUTABLE).send(this, 42, null);
            } catch (PendingIntent.CanceledException e) {
                T.ev("unexpected cancel");
            }
        }, expect("pi equal true", "pi distinct true", "pi nocreate true", "pi canceled", "pi gone true",
                "manifest " + PING + " pi1u", "manifest " + PING + " fin ordered", "result 9 42", "finished 8 m"));

        step("alarms", 700, () -> {
            AlarmManager am = getSystemService(AlarmManager.class);
            long now = SystemClock.elapsedRealtime();
            PendingIntent alarm = PendingIntent.getBroadcast(this, 4, explicitPing("alarm"), PendingIntent.FLAG_IMMUTABLE);
            am.set(AlarmManager.ELAPSED_REALTIME, now + 60, alarm);
            am.set(AlarmManager.ELAPSED_REALTIME, now + 250, alarm);
            am.setExact(AlarmManager.ELAPSED_REALTIME, now + 50, "listener", () -> T.ev("listener"), null);
            AlarmManager.OnAlarmListener cancelled = () -> T.ev("cancelled listener");
            am.setExact(AlarmManager.ELAPSED_REALTIME, now + 100, "cancelled", cancelled, null);
            am.cancel(cancelled);
            am.setRepeating(AlarmManager.ELAPSED_REALTIME, now + 100, 150,
                    PendingIntent.getBroadcast(this, 5, explicitPing("rep"), PendingIntent.FLAG_IMMUTABLE));
        }, events -> {
            int reps = 0;
            ArrayList<String> rest = new ArrayList<String>();
            for (String e : events) {
                if (e.equals("manifest " + PING + " rep")) reps++;
                else rest.add(e);
            }
            if (reps < 2) return "repeating alarm fired " + reps + " times";
            return expect("listener", "manifest " + PING + " alarm").check(rest);
        });

        step("cancel repeating", 500, () -> getSystemService(AlarmManager.class).cancel(
                PendingIntent.getBroadcast(this, 5, explicitPing("rep"), PendingIntent.FLAG_IMMUTABLE)),
                events -> {
                    // One may already have been posted when cancel ran.
                    int reps = 0;
                    for (String e : events) if (e.equals("manifest " + PING + " rep")) reps++;
                    return reps <= 1 && reps == events.size() ? null : "after cancel: " + events;
                });

        step("leaks", 1200, () -> startActivity(new Intent(this, LeakActivity.class)),
                expect("bind onCreate", "bind onBind", "leak connected", "bind onUnbind", "bind onDestroy"));

        step("after leak", 300, () -> sendBroadcast(ping("afterleak")), expect());

        step("notifications", 100, () -> {
            NotificationManager nm = getSystemService(NotificationManager.class);
            // Target 29: a notification without a channel is dropped.
            nm.notify(1, new Notification.Builder(this).setContentTitle("No channel").build());
            nm.createNotificationChannelGroup(new NotificationChannelGroup("g1", "Group"));
            NotificationChannel c1 = new NotificationChannel("c1", "Chat", NotificationManager.IMPORTANCE_DEFAULT);
            c1.setGroup("g1");
            nm.createNotificationChannel(c1);
            nm.createNotificationChannel(new NotificationChannel("off", "Off", NotificationManager.IMPORTANCE_NONE));
            PendingIntent reply = PendingIntent.getBroadcast(this, 6, explicitPing("reply"), PendingIntent.FLAG_MUTABLE);
            Notification n = new Notification.Builder(this, "c1")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Hello")
                    .setContentText("World")
                    .setProgress(10, 3, false)
                    .setAutoCancel(true)
                    .setStyle(new Notification.BigTextStyle().bigText("Long text"))
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(this, android.R.drawable.ic_menu_send), "Reply", reply)
                            .addRemoteInput(new RemoteInput.Builder("reply").setLabel("Reply").build()).build())
                    .build();
            T.ev("extras " + n.extras.getCharSequence(Notification.EXTRA_TITLE) + "/"
                    + n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) + "/"
                    + n.extras.getInt(Notification.EXTRA_PROGRESS) + " template="
                    + n.extras.getString(Notification.EXTRA_TEMPLATE));
            T.ev("autocancel " + ((n.flags & Notification.FLAG_AUTO_CANCEL) != 0) + " icon "
                    + (n.getSmallIcon() != null) + " remote input " + (n.findRemoteInputActionPair(true) != null));
            nm.notify(3, n);
            nm.notify(4, new Notification.Builder(this, "off").setContentTitle("Blocked").build());
            StatusBarNotification[] active = nm.getActiveNotifications();
            T.ev("active " + active.length + " id=" + (active.length > 0 ? active[0].getId() : -1));
            Notification.Builder recovered = Notification.Builder.recoverBuilder(this, n);
            T.ev("recovered " + (recovered.getStyle() instanceof Notification.BigTextStyle));

            NotificationChannel up = new NotificationChannel("c1", "Chat 2", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(up);
            NotificationChannel stored = nm.getNotificationChannel("c1");
            T.ev("raise refused " + stored.getName() + " " + stored.getImportance());
            nm.createNotificationChannel(new NotificationChannel("c1", "Chat 2", NotificationManager.IMPORTANCE_LOW));
            T.ev("lowered " + nm.getNotificationChannel("c1").getImportance() + " group channels "
                    + nm.getNotificationChannelGroup("g1").getChannels().size());

            Notification.MessagingStyle style = new Notification.MessagingStyle(
                    new Person.Builder().setName("Me").build())
                    .addMessage("hi", 1, new Person.Builder().setName("Ann").build())
                    .addMessage("yo", 2, (Person) null);
            Notification m = new Notification.Builder(this, "c1").setStyle(style).build();
            List<Notification.MessagingStyle.Message> msgs = Notification.MessagingStyle.Message
                    .getMessagesFromBundleArray(m.extras.getParcelableArray(Notification.EXTRA_MESSAGES));
            T.ev("messages " + msgs.size() + " " + msgs.get(0).getSenderPerson().getName() + ":" + msgs.get(0).getText());

            Intent replyIntent = new Intent();
            Bundle results = new Bundle();
            results.putCharSequence("reply", "typed");
            RemoteInput.addResultsToIntent(n.actions[0].getRemoteInputs(), replyIntent, results);
            T.ev("reply " + RemoteInput.getResultsFromIntent(replyIntent).getCharSequence("reply"));

            nm.deleteNotificationChannel("c1");
            T.ev("after delete " + nm.getActiveNotifications().length);
        }, expect("extras Hello/Long text/3 template=android.app.Notification$BigTextStyle",
                "autocancel true icon true remote input true", "active 1 id=3", "recovered true",
                "raise refused Chat 2 3", "lowered 2 group channels 1", "messages 2 Ann:hi", "reply typed",
                "after delete 0"));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        T.ev("result " + requestCode + " " + resultCode);
    }

    private void runNext() {
        if (mNext >= mSteps.size()) {
            mBand.setBackgroundColor(mFailed == 0 ? 0xFF43A047 : 0xFFE53935);
            Log.i("SVC", "done steps=" + mSteps.size() + " failed=" + mFailed);
            return;
        }
        final Step step = mSteps.get(mNext++);
        T.take();
        step.action.run();
        mHandler.postDelayed(() -> {
            String error = step.check.check(T.take());
            if (error == null) {
                Log.i("SVC", "ok " + step.name);
            } else {
                mFailed++;
                Log.e("SVC", "FAIL " + step.name + ": " + error);
            }
            addCell(error == null);
            runNext();
        }, step.wait);
    }

    private void addCell(boolean ok) {
        if (mRow == null || mRow.getChildCount() == CELLS_PER_ROW) {
            mRow = new LinearLayout(this);
            mGrid.addView(mRow);
        }
        View cell = new View(this);
        cell.setBackgroundColor(ok ? 0xFF43A047 : 0xFFE53935);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        mRow.addView(cell, lp);
    }
}
