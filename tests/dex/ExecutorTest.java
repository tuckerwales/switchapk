import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** ThreadPoolExecutor policy (core, queue, max, reject), lifecycle and SynchronousQueue handoff; compared with OpenJDK. */
public class ExecutorTest {
    static void p(Object o) { System.out.println(o); }

    static String tail(Object o) {
        String s = String.valueOf(o);
        int i = s.indexOf('[');
        return i >= 0 ? s.substring(i) : s;
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    public static void main(String[] args) throws Exception {
        // core 1, max 4, unbounded queue: everything runs serially on one thread
        ThreadPoolExecutor serial = new ThreadPoolExecutor(1, 4, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        final StringBuffer order = new StringBuffer();
        final Set<String> names = Collections.synchronizedSet(new HashSet<String>());
        for (int i = 0; i < 20; i++) {
            final int k = i;
            serial.execute(() -> { order.append(k).append(','); names.add(Thread.currentThread().getName()); sleep(1); });
        }
        serial.shutdown();
        p("serial await " + serial.awaitTermination(10, TimeUnit.SECONDS) + " " + order + " threads=" + names.size()
                + " largest=" + serial.getLargestPoolSize() + " completed=" + serial.getCompletedTaskCount()
                + " tasks=" + serial.getTaskCount() + " " + serial.isTerminated() + " " + tail(serial));

        // bounded queue: core, then queue, then extra threads, then rejection
        final CountDownLatch gate = new CountDownLatch(1);
        ThreadPoolExecutor bounded = new ThreadPoolExecutor(1, 2, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1));
        Runnable block = () -> { try { gate.await(); } catch (InterruptedException e) { } };
        bounded.execute(block);
        p("after 1: pool=" + bounded.getPoolSize() + " queued=" + bounded.getQueue().size());
        bounded.execute(block);
        p("after 2: pool=" + bounded.getPoolSize() + " queued=" + bounded.getQueue().size());
        bounded.execute(block);
        p("after 3: pool=" + bounded.getPoolSize() + " queued=" + bounded.getQueue().size());
        try {
            bounded.execute(block);
        } catch (RejectedExecutionException e) {
            p("rejected " + e.getMessage().startsWith("Task ") + " " + e.getMessage().contains(" rejected from ")
                    + " " + tail(e.getMessage()));
        }
        sleep(50);
        p("active=" + bounded.getActiveCount() + " " + tail(bounded));
        gate.countDown();
        bounded.shutdown();
        p("bounded await " + bounded.awaitTermination(10, TimeUnit.SECONDS) + " completed=" + bounded.getCompletedTaskCount());
        try { bounded.execute(block); } catch (RejectedExecutionException e) { p("rejected after shutdown"); }

        // other policies
        ThreadPoolExecutor caller = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        final CountDownLatch g2 = new CountDownLatch(1);
        caller.execute(() -> { try { g2.await(); } catch (InterruptedException e) { } });
        final String[] ran = new String[1];
        caller.execute(() -> ran[0] = Thread.currentThread().getName());
        p("callerRuns on main " + "main".equals(ran[0]));
        g2.countDown();
        caller.shutdown();
        caller.awaitTermination(5, TimeUnit.SECONDS);

        // cached pool reuses an idle thread
        ThreadPoolExecutor cached = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        for (int i = 0; i < 5; i++) {
            cached.submit(() -> sleep(1)).get();
            sleep(20);
        }
        p("cached largest=" + cached.getLargestPoolSize() + " pool=" + cached.getPoolSize());
        List<Future<Integer>> fs = new ArrayList<Future<Integer>>();
        for (int i = 0; i < 8; i++) {
            final int k = i;
            fs.add(cached.submit(() -> { sleep(30); return k; }));
        }
        int sum = 0;
        for (Future<Integer> f : fs) sum += f.get();
        p("cached parallel sum=" + sum + " largest>=8 " + (cached.getLargestPoolSize() >= 8));
        cached.shutdownNow();
        p("cached terminated " + cached.awaitTermination(5, TimeUnit.SECONDS));

        // core timeout and prestart
        ThreadPoolExecutor timeout = new ThreadPoolExecutor(2, 2, 30, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        p("prestart " + timeout.prestartAllCoreThreads() + " " + timeout.getPoolSize() + " " + timeout.prestartCoreThread());
        timeout.allowCoreThreadTimeOut(true);
        sleep(300);
        p("after timeout pool=" + timeout.getPoolSize() + " allows=" + timeout.allowsCoreThreadTimeOut()
                + " keepAlive=" + timeout.getKeepAliveTime(TimeUnit.MILLISECONDS));
        timeout.execute(() -> { });
        timeout.shutdown();
        p("timeout await " + timeout.awaitTermination(5, TimeUnit.SECONDS));

        // shutdownNow returns queued tasks and interrupts running ones
        ThreadPoolExecutor now = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        final AtomicBoolean interrupted = new AtomicBoolean();
        final CountDownLatch started = new CountDownLatch(1);
        now.execute(() -> { started.countDown(); try { Thread.sleep(10000); } catch (InterruptedException e) { interrupted.set(true); } });
        now.execute(() -> { });
        now.execute(() -> { });
        started.await();
        List<Runnable> pending = now.shutdownNow();
        p("shutdownNow pending=" + pending.size() + " await=" + now.awaitTermination(5, TimeUnit.SECONDS)
                + " interrupted=" + interrupted.get() + " " + now.isShutdown() + " " + now.isTerminating());

        // a throwing task kills its worker; a replacement takes the next task
        final List<String> uncaught = Collections.synchronizedList(new ArrayList<String>());
        ThreadFactory tf = r -> {
            Thread t = new Thread(r);
            t.setUncaughtExceptionHandler((th, e) -> uncaught.add(e.getMessage()));
            return t;
        };
        final List<String> after = Collections.synchronizedList(new ArrayList<String>());
        ThreadPoolExecutor thrower = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), tf) {
            protected void afterExecute(Runnable r, Throwable t) {
                after.add(t == null ? "ok" : t.getMessage());
            }
        };
        thrower.execute(() -> { throw new IllegalStateException("task failed"); });
        final CountDownLatch next = new CountDownLatch(1);
        thrower.execute(next::countDown);
        p("replacement ran " + next.await(5, TimeUnit.SECONDS));
        thrower.shutdown();
        thrower.awaitTermination(5, TimeUnit.SECONDS);
        sleep(50);
        p("uncaught " + uncaught + " after " + after + " completed=" + thrower.getCompletedTaskCount());

        // argument checks
        String[] bad = new String[5];
        try { new ThreadPoolExecutor(-1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>()); } catch (IllegalArgumentException e) { bad[0] = "iae"; }
        try { new ThreadPoolExecutor(2, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>()); } catch (IllegalArgumentException e) { bad[1] = "iae"; }
        try { new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, null); } catch (NullPointerException e) { bad[2] = "npe"; }
        ThreadPoolExecutor zero = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        try { zero.allowCoreThreadTimeOut(true); } catch (IllegalArgumentException e) { bad[3] = e.getMessage(); }
        try { zero.setMaximumPoolSize(0); } catch (IllegalArgumentException e) { bad[4] = "iae"; }
        p("checks " + Arrays.toString(bad));
        zero.setCorePoolSize(0);
        zero.shutdown();

        // SynchronousQueue handoff
        final SynchronousQueue<String> q = new SynchronousQueue<String>();
        p("sq offer no taker " + q.offer("a") + " poll " + q.poll() + " timed " + q.poll(10, TimeUnit.MILLISECONDS)
                + " size=" + q.size() + " empty=" + q.isEmpty() + " cap=" + q.remainingCapacity() + " " + q
                + " peek=" + q.peek() + " " + (q instanceof AbstractQueue) + " " + q.offer("x", 10, TimeUnit.MILLISECONDS));
        final String[] got = new String[1];
        Thread taker = new Thread(() -> { try { got[0] = q.take(); } catch (InterruptedException e) { } });
        taker.start();
        boolean offered = false;
        for (int i = 0; i < 200 && !offered; i++) {
            offered = q.offer("hand");
            if (!offered) sleep(5);
        }
        taker.join();
        p("sq handoff " + offered + " " + got[0]);
        Thread putter = new Thread(() -> { try { q.put("put"); } catch (InterruptedException e) { } });
        putter.start();
        p("sq take " + q.poll(5, TimeUnit.SECONDS));
        putter.join();
        p("done");
    }
}
