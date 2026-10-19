import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Fork/join (RecursiveTask, RecursiveAction, CountedCompleter, ForkJoinPool), the timed ExecutorService
 * methods, ExecutorCompletionService and ThreadLocalRandom. Run on OpenJDK and on switchapk by
 * tests/run_dex_test.sh, output must match.
 */
public class ForkJoinTest {
    static void p(Object o) {
        System.out.println(o);
    }

    static final class Fib extends RecursiveTask<Integer> {
        final int n;

        Fib(int n) {
            this.n = n;
        }

        protected Integer compute() {
            if (n <= 1) return n;
            Fib f1 = new Fib(n - 1);
            f1.fork();
            Fib f2 = new Fib(n - 2);
            return f2.compute() + f1.join();
        }
    }

    static final class SortAction extends RecursiveAction {
        final long[] a;
        final int lo, hi;

        SortAction(long[] a, int lo, int hi) {
            this.a = a;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            if (hi - lo < 1000) {
                Arrays.sort(a, lo, hi);
                return;
            }
            int mid = (lo + hi) >>> 1;
            invokeAll(new SortAction(a, lo, mid), new SortAction(a, mid, hi));
            long[] buf = Arrays.copyOfRange(a, lo, mid);
            for (int i = 0, j = mid, k = lo; i < buf.length; k++) {
                a[k] = (j == hi || buf[i] < a[j]) ? buf[i++] : a[j++];
            }
        }
    }

    /** The JDK documentation's map-reduce CountedCompleter. */
    static final class MapReducer extends CountedCompleter<Long> {
        final int[] array;
        final int lo, hi;
        MapReducer sibling;
        long result;

        MapReducer(CountedCompleter<?> p, int[] array, int lo, int hi) {
            super(p);
            this.array = array;
            this.lo = lo;
            this.hi = hi;
        }

        public void compute() {
            if (hi - lo >= 2) {
                int mid = (lo + hi) >>> 1;
                MapReducer left = new MapReducer(this, array, lo, mid);
                MapReducer right = new MapReducer(this, array, mid, hi);
                left.sibling = right;
                right.sibling = left;
                setPendingCount(1);
                right.fork();
                left.compute();
            } else {
                if (hi > lo) result = (long) array[lo] * array[lo];
                tryComplete();
            }
        }

        public void onCompletion(CountedCompleter<?> caller) {
            if (caller != this) {
                MapReducer child = (MapReducer) caller;
                MapReducer sib = child.sibling;
                result = child.result + (sib == null ? 0 : sib.result);
            }
        }

        public Long getRawResult() {
            return result;
        }
    }

    static final class Boom extends RecursiveTask<Integer> {
        protected Integer compute() {
            throw new IllegalStateException("boom");
        }
    }

    public static void main(String[] args) throws Exception {
        p("common fib(22) = " + ForkJoinPool.commonPool().invoke(new Fib(22)));
        ForkJoinPool one = new ForkJoinPool(1);
        p("parallelism 1 fib(20) = " + one.invoke(new Fib(20)) + " (nested joins do not deadlock)");
        ForkJoinPool four = new ForkJoinPool(4);
        p("pool 4 fib(23) = " + four.invoke(new Fib(23)) + " parallelism=" + four.getParallelism());
        p("invoke outside a pool: fib(15) = " + new Fib(15).invoke() + " inForkJoinPool=" + ForkJoinTask.inForkJoinPool());

        long[] data = new long[50000];
        Random r = new Random(5);
        for (int i = 0; i < data.length; i++) data[i] = r.nextLong();
        long[] expect = data.clone();
        Arrays.sort(expect);
        four.invoke(new SortAction(data, 0, data.length));
        p("parallel sort: " + Arrays.equals(data, expect));

        int[] nums = new int[10000];
        long sq = 0;
        for (int i = 0; i < nums.length; i++) {
            nums[i] = i % 100;
            sq += (long) nums[i] * nums[i];
        }
        p("counted completer sum of squares: " + four.invoke(new MapReducer(null, nums, 0, nums.length)) + " expected " + sq);

        ForkJoinTask<Integer> boom = four.submit(new Boom());
        try {
            boom.join();
        } catch (IllegalStateException e) {
            p("join rethrows: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
        try {
            boom.get();
        } catch (ExecutionException e) {
            p("get wraps: " + e.getCause().getClass().getSimpleName() + " abnormal=" + boom.isCompletedAbnormally());
        }
        ForkJoinTask<Integer> c = new Fib(5);
        p("cancel: " + c.cancel(true) + " " + c.isCancelled() + " " + (c.getException() instanceof CancellationException));
        ForkJoinTask<String> adapted = ForkJoinTask.adapt(() -> "called");
        p("adapt callable: " + four.invoke(adapted) + " normal=" + adapted.isCompletedNormally());
        Fib done = new Fib(1);
        done.complete(99);
        p("complete: " + done.join());

        AtomicInteger ran = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(100);
        for (int i = 0; i < 100; i++) four.execute(() -> {
            ran.incrementAndGet();
            latch.countDown();
        });
        latch.await();
        p("execute runnables: " + ran.get());
        List<Callable<Integer>> calls = new ArrayList<Callable<Integer>>();
        for (int i = 0; i < 5; i++) {
            final int v = i;
            calls.add(() -> v * v);
        }
        int total = 0;
        for (Future<Integer> f : four.invokeAll(calls)) total += f.get();
        p("pool invokeAll: " + total);
        p("awaitQuiescence: " + four.awaitQuiescence(5, TimeUnit.SECONDS) + " quiescent=" + four.isQuiescent());
        four.shutdown();
        p("shutdown: " + four.awaitTermination(10, TimeUnit.SECONDS) + " terminated=" + four.isTerminated());
        try {
            four.execute(() -> { });
        } catch (RejectedExecutionException e) {
            p("rejected after shutdown");
        }
        one.shutdownNow();
        ForkJoinPool.commonPool().shutdown();
        p("common pool ignores shutdown: " + !ForkJoinPool.commonPool().isShutdown());

        // ExecutorService timed methods and completion service
        ExecutorService ex = Executors.newFixedThreadPool(3);
        List<Callable<String>> mixed = new ArrayList<Callable<String>>();
        mixed.add(() -> {
            Thread.sleep(5000);
            return "slow";
        });
        mixed.add(() -> "fast");
        List<Future<String>> fs = ex.invokeAll(mixed, 300, TimeUnit.MILLISECONDS);
        p("timed invokeAll: cancelled=" + fs.get(0).isCancelled() + " second=" + fs.get(1).get());
        p("invokeAny: " + ex.invokeAny(mixed));
        List<Callable<String>> failing = new ArrayList<Callable<String>>();
        failing.add(() -> {
            throw new java.io.IOException("x");
        });
        try {
            ex.invokeAny(failing);
        } catch (ExecutionException e) {
            p("invokeAny all fail: " + e.getCause().getClass().getSimpleName());
        }
        try {
            ex.invokeAny(Collections.<Callable<String>>emptyList());
        } catch (IllegalArgumentException e) {
            p("invokeAny empty: IllegalArgumentException");
        }
        try {
            ex.invokeAny(Collections.singletonList(mixed.get(0)), 200, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            p("timed invokeAny: TimeoutException");
        }
        CompletionService<Integer> cs = new ExecutorCompletionService<Integer>(ex);
        cs.submit(() -> {
            Thread.sleep(300);
            return 1;
        });
        cs.submit(() -> 2);
        p("completion order: " + cs.take().get() + " " + cs.take().get() + " then " + cs.poll());
        ex.shutdownNow();

        // ThreadLocalRandom
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        boolean ok = true;
        for (int i = 0; i < 10000; i++) {
            int a = tlr.nextInt(5, 10);
            long b = tlr.nextLong(-3, 3);
            double d = tlr.nextDouble(1.5, 2.5);
            int e = tlr.nextInt(7);
            ok &= a >= 5 && a < 10 && b >= -3 && b < 3 && d >= 1.5 && d < 2.5 && e >= 0 && e < 7;
        }
        p("ThreadLocalRandom ranges: " + ok + " same instance per thread: " + (tlr == ThreadLocalRandom.current()));
        final ThreadLocalRandom[] other = new ThreadLocalRandom[1];
        Thread t = new Thread(() -> other[0] = ThreadLocalRandom.current());
        t.start();
        t.join();
        try {
            tlr.setSeed(1);
            p("setSeed allowed");
        } catch (UnsupportedOperationException e) {
            p("setSeed: UnsupportedOperationException");
        }
        p("streams: " + tlr.ints(5, 0, 3).allMatch(v -> v >= 0 && v < 3) + " " + tlr.doubles(3).count());
        Set<Integer> seen = new HashSet<Integer>();
        for (int i = 0; i < 1000; i++) seen.add(tlr.nextInt(10));
        p("covers range: " + seen.size());
    }
}
