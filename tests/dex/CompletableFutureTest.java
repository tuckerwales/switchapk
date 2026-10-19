import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * CompletableFuture: exception encoding (raw vs CompletionException), every stage kind, async stages,
 * aggregates, timeouts and minimal stages. Run on OpenJDK and on switchapk; output must match.
 */
public class CompletableFutureTest {
    static void p(Object o) { System.out.println(o); }

    static String st(CompletableFuture<?> f) {
        String s = f.toString();
        return s.substring(s.indexOf('['));
    }

    static String desc(Throwable t) {
        if (t == null) return "null";
        return t.getClass().getSimpleName() + (t.getCause() != null ? "(" + desc(t.getCause()) + ")" : "")
                + (t.getMessage() != null && t.getCause() == null ? ":" + t.getMessage() : "");
    }

    static String join(CompletableFuture<?> f) {
        try { return "join=" + f.join(); } catch (Throwable t) { return "join!" + desc(t); }
    }

    static String get(Future<?> f) {
        try { return "get=" + f.get(); } catch (Throwable t) { return "get!" + desc(t); }
    }

    static String all(CompletableFuture<?> f) { return st(f) + " " + join(f) + " " + get(f); }

    public static void main(String[] args) throws Exception {
        IllegalStateException boom = new IllegalStateException("boom");
        CompletableFuture<Integer> ok = CompletableFuture.completedFuture(1);
        CompletableFuture<Integer> bad = CompletableFuture.failedFuture(boom);
        p("ok " + all(ok));
        p("bad " + all(bad) + " " + bad.isCompletedExceptionally());

        p("apply ok " + all(ok.thenApply(x -> x + 1)));
        p("apply bad " + all(bad.thenApply(x -> x + 1)));
        p("apply throws " + all(ok.thenApply(x -> { throw new ArithmeticException("div"); })));
        p("accept " + all(ok.thenAccept(x -> {})) + " run " + all(ok.thenRun(() -> {})));

        final List<String> seen = new ArrayList<String>();
        p("handle bad " + all(bad.handle((v, t) -> { seen.add("h:" + v + "/" + desc(t)); return 7; })));
        p("handle dep " + all(bad.thenApply(x -> x).handle((v, t) -> { seen.add("hd:" + desc(t)); return 8; })));
        p("exceptionally " + all(bad.exceptionally(t -> { seen.add("e:" + desc(t)); return 9; })));
        p("exceptionally ok " + all(ok.exceptionally(t -> 9)));
        p("exceptionally throws " + all(bad.exceptionally(t -> { throw new RuntimeException("again"); })));
        p("whenComplete ok " + all(ok.whenComplete((v, t) -> seen.add("w:" + v))));
        p("whenComplete bad " + all(bad.whenComplete((v, t) -> seen.add("wb:" + desc(t)))));
        p("whenComplete throws " + all(ok.whenComplete((v, t) -> { throw new RuntimeException("wc"); })));
        CompletableFuture<Integer> both = bad.whenComplete((v, t) -> { throw new RuntimeException("sup"); });
        try { both.join(); } catch (CompletionException e) {
            p("suppressed " + desc(e) + " " + e.getCause().getSuppressed().length);
        }
        p("compose " + all(ok.thenCompose(x -> CompletableFuture.completedFuture(x * 10))));
        p("compose inner bad " + all(ok.thenCompose(x -> bad)));
        p("compose throws " + all(ok.thenCompose(x -> { throw new IllegalArgumentException("c"); })));
        p("exceptionallyCompose " + all(bad.exceptionallyCompose(t -> CompletableFuture.completedFuture(5))));
        p("seen " + seen);

        CompletableFuture<Integer> two = CompletableFuture.completedFuture(2);
        p("combine " + all(ok.thenCombine(two, (a, b) -> a + b)));
        p("combine bad " + all(ok.thenCombine(bad, (a, b) -> a + b)) + " " + all(bad.thenCombine(ok, (a, b) -> a)));
        p("acceptBoth " + all(ok.thenAcceptBoth(two, (a, b) -> {})) + " runAfterBoth " + all(ok.runAfterBoth(two, () -> {})));
        CompletableFuture<Integer> never = new CompletableFuture<Integer>();
        p("either " + all(never.applyToEither(two, x -> x * 3)) + " " + all(ok.acceptEither(never, x -> {}))
                + " " + all(never.runAfterEither(bad, () -> {})));

        // pending dependents fire on completion, in order
        CompletableFuture<String> src = new CompletableFuture<String>();
        StringBuilder order = new StringBuilder();
        CompletableFuture<String> d1 = src.thenApply(s -> { order.append("1"); return s + "!"; });
        CompletableFuture<Void> d2 = src.thenAccept(s -> order.append("2"));
        p("pending " + st(src) + " deps=" + src.getNumberOfDependents() + " getNow=" + src.getNow("absent"));
        p("complete " + src.complete("hi") + " again " + src.complete("x") + " " + order + " " + join(d1) + " " + st(d2));

        // cancellation
        CompletableFuture<String> c = new CompletableFuture<String>();
        CompletableFuture<String> cd = c.thenApply(s -> s);
        p("cancel " + c.cancel(true) + " " + c.isCancelled() + " " + c.isCompletedExceptionally() + " " + join(c)
                + " " + get(c) + " dep " + join(cd) + " " + cd.isCancelled());
        p("cancel done " + ok.cancel(true));

        // obtrude
        CompletableFuture<Integer> o = CompletableFuture.completedFuture(3);
        o.obtrudeValue(4);
        p("obtrude " + join(o));
        o.obtrudeException(boom);
        p("obtrude ex " + join(o));

        // aggregates
        p("allOf " + all(CompletableFuture.allOf(ok, two)) + " " + all(CompletableFuture.allOf(ok, bad))
                + " " + all(CompletableFuture.allOf()));
        p("anyOf " + all(CompletableFuture.anyOf(never, two)) + " " + all(CompletableFuture.anyOf(bad, never)));

        // async
        ExecutorService ex = Executors.newFixedThreadPool(2);
        p("supplyAsync " + join(CompletableFuture.supplyAsync(() -> "s", ex)));
        p("supplyAsync throws " + join(CompletableFuture.supplyAsync(() -> { throw new IllegalStateException("x"); }, ex)));
        p("runAsync " + join(CompletableFuture.runAsync(() -> {})));
        p("applyAsync " + join(ok.thenApplyAsync(x -> Thread.currentThread() != null ? x + 100 : 0, ex)));
        p("handleAsync " + join(bad.handleAsync((v, t) -> desc(t))));
        p("whenCompleteAsync " + join(ok.whenCompleteAsync((v, t) -> {}, ex)));
        p("composeAsync " + join(ok.thenComposeAsync(x -> CompletableFuture.supplyAsync(() -> x + 1))));
        p("combineAsync " + join(ok.thenCombineAsync(two, Integer::sum, ex)));
        p("exceptionallyAsync " + join(bad.exceptionallyAsync(t -> -1)));
        p("completeAsync " + join(new CompletableFuture<String>().completeAsync(() -> "ca")));
        List<CompletableFuture<Integer>> many = new ArrayList<CompletableFuture<Integer>>();
        for (int i = 0; i < 50; i++) {
            final int k = i;
            many.add(CompletableFuture.supplyAsync(() -> k * k, ex));
        }
        CompletableFuture.allOf(many.toArray(new CompletableFuture[0])).join();
        int sum = 0;
        for (CompletableFuture<Integer> f : many) sum += f.join();
        p("many " + sum);

        // timeouts
        CompletableFuture<String> t1 = new CompletableFuture<String>().orTimeout(50, TimeUnit.MILLISECONDS);
        p("orTimeout " + get(t1));
        CompletableFuture<String> t2 = new CompletableFuture<String>().completeOnTimeout("late", 50, TimeUnit.MILLISECONDS);
        p("completeOnTimeout " + join(t2));
        CompletableFuture<String> t3 = new CompletableFuture<String>();
        try { t3.get(20, TimeUnit.MILLISECONDS); } catch (TimeoutException e) { p("get timeout " + desc(e)); }
        long t0 = System.nanoTime();
        CompletableFuture<Void> delayed = CompletableFuture.runAsync(() -> {},
                CompletableFuture.delayedExecutor(60, TimeUnit.MILLISECONDS));
        delayed.join();
        p("delayed " + ((System.nanoTime() - t0) / 1000000 >= 50));

        // copy and minimal stages
        p("copy " + all(bad.copy()) + " " + all(ok.copy()));
        CompletionStage<Integer> min = ok.minimalCompletionStage();
        try { ((CompletableFuture<Integer>) min).join(); } catch (UnsupportedOperationException e) { p("minimal join UOE"); }
        p("minimal toCF " + all(min.toCompletableFuture()) + " " + all(CompletableFuture.failedStage(boom).toCompletableFuture()));
        p("minimal then " + join(min.thenApply(x -> x + 5).toCompletableFuture()));
        CompletionStage<Integer> cs = CompletableFuture.completedStage(11);
        p("completedStage " + join(cs.toCompletableFuture()) + " " + (cs.thenApply(x -> x) instanceof CompletableFuture));
        p("newIncomplete " + st(ok.newIncompleteFuture()) + " defaultExecutor " + (ok.defaultExecutor() != null));
        p("npe " + desc(tryNpe(() -> ok.thenApply(null))) + " " + desc(tryNpe(() -> CompletableFuture.failedFuture(null))));
        ex.shutdown();
        p("done");
    }

    static Throwable tryNpe(Runnable r) {
        try { r.run(); return null; } catch (Throwable t) { return t; }
    }
}
