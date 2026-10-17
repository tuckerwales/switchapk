import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * java.util.concurrent.ConcurrentHashMap: iteration order (which follows OpenJDK's table layout and resizes),
 * weakly consistent iteration, null rejection, compute/merge semantics, views and bulk operations. Run on OpenJDK
 * and on switchapk by tests/run_dex_test.sh; output must match.
 */
public class ChmTest {
    static void p(Object o) {
        System.out.println(o);
    }

    interface Check {
        void run() throws Exception;
    }

    static void expect(String what, Check c) {
        try {
            c.run();
            p(what + " no exception");
        } catch (Exception e) {
            p(what + " " + e.getClass().getName() + (e instanceof IllegalStateException ? " " + e.getMessage() : ""));
        }
    }

    /** Key with a chosen hash code, to build collisions and known bins. */
    static final class K {
        final int h;
        final String name;

        K(int h, String name) {
            this.h = h;
            this.name = name;
        }

        public int hashCode() {
            return h;
        }

        public boolean equals(Object o) {
            return o instanceof K && ((K) o).name.equals(name);
        }

        public String toString() {
            return name;
        }
    }

    public static void main(String[] args) throws Exception {
        ordering();
        constructors();
        collisions();
        iterationWhileModifying();
        threads();
        nulls();
        computeFamily();
        views();
        keySetViews();
        bulk();
        misc();
        p("done");
    }

    static void ordering() {
        p("== ordering");
        ConcurrentHashMap<String, Integer> m = new ConcurrentHashMap<>();
        for (int i = 0; i < 100; i++) {
            m.put("key" + (i * 7919 % 1000), i);
            if (i == 11 || i == 12 || i == 23 || i == 24 || i == 47 || i == 48 || i == 99) {
                p(i + " " + m.size() + " " + m);
            }
        }
        for (int i = 0; i < 100; i += 3) {
            m.remove("key" + (i * 7919 % 1000));
        }
        p("removed " + m.size() + " " + m);
        for (int i = 0; i < 200; i++) {
            m.put("x" + i, -i);
        }
        p("grown " + m.size() + " " + m.keySet());
        for (int i = 0; i < 200; i += 2) {
            m.remove("x" + i);
        }
        m.put("key7", 70);
        p("final " + m.size() + " " + m.values());

        ConcurrentHashMap<Integer, String> ints = new ConcurrentHashMap<>();
        for (int i = 0; i < 70; i++) {
            ints.put(i * 37 - 500, "v" + i);
        }
        for (int i = 0; i < 70; i += 4) {
            ints.remove(i * 37 - 500);
        }
        p("ints " + ints);
        ConcurrentHashMap<Long, Integer> longs = new ConcurrentHashMap<>();
        for (long i = 0; i < 40; i++) {
            longs.put(i << 32 | (i * 3), (int) i);
        }
        p("longs " + longs.keySet());
    }

    static void constructors() {
        p("== constructors");
        int[] caps = {0, 1, 2, 5, 10, 11, 12, 13, 16, 24, 25, 100};
        for (int cap : caps) {
            ConcurrentHashMap<Integer, Integer> a = new ConcurrentHashMap<>(cap);
            ConcurrentHashMap<Integer, Integer> b = new ConcurrentHashMap<>(cap, 0.5f);
            ConcurrentHashMap<Integer, Integer> c = new ConcurrentHashMap<>(cap, 0.9f, 20);
            for (int i = 0; i < 30; i++) {
                int k = i * 31 + 1;
                a.put(k, i);
                b.put(k, i);
                c.put(k, i);
                if (i == 9) {
                    p(cap + " at10 " + a.keySet() + " " + b.keySet() + " " + c.keySet());
                }
            }
            p(cap + " at30 " + a.keySet() + " " + b.keySet() + " " + c.keySet());
        }
        Map<Integer, Integer> src = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            src.put(i * 33, i);
        }
        ConcurrentHashMap<Integer, Integer> copy = new ConcurrentHashMap<>(src);
        p("copy " + copy);
        for (int i = 20; i < 40; i++) {
            copy.put(i * 33, i);
        }
        p("copy grown " + copy.keySet());
        ConcurrentHashMap<Integer, Integer> fromEmpty = new ConcurrentHashMap<>();
        fromEmpty.putAll(new HashMap<Integer, Integer>());
        for (int i = 0; i < 10; i++) {
            fromEmpty.put(i * 17, i);
        }
        p("putAll empty then puts " + fromEmpty.keySet());
        ConcurrentHashMap<Integer, Integer> bulk = new ConcurrentHashMap<>();
        bulk.put(1000, 0);
        bulk.putAll(src);
        p("putAll " + bulk.keySet());
        expect("negative capacity", () -> new ConcurrentHashMap<String, String>(-1));
        expect("zero load factor", () -> new ConcurrentHashMap<String, String>(4, 0f));
        expect("zero concurrency", () -> new ConcurrentHashMap<String, String>(4, 0.75f, 0));
        expect("null map", () -> new ConcurrentHashMap<String, String>(null));
    }

    static void collisions() {
        p("== collisions");
        // Up to 8 keys per bin: bins stay linked lists in OpenJDK too (treeified bins are out of scope).
        ConcurrentHashMap<K, Integer> m = new ConcurrentHashMap<>();
        for (int i = 0; i < 40; i++) {
            m.put(new K((i % 6) * 16 + (i / 6 % 2) * 32, "c" + i), i);
        }
        p(m);
        for (int i = 0; i < 40; i += 5) {
            m.remove(new K((i % 6) * 16 + (i / 6 % 2) * 32, "c" + i));
        }
        p(m);
        // A long chain in a small table makes OpenJDK resize instead of building a tree.
        ConcurrentHashMap<K, Integer> small = new ConcurrentHashMap<>();
        for (int i = 0; i < 12; i++) {
            small.put(new K(i < 9 ? 3 : 3 + 16 * i, "s" + i), i);
            p(i + " " + small);
        }
        // Same hash bits below the table size, different above: lo/hi split on resize.
        ConcurrentHashMap<K, Integer> split = new ConcurrentHashMap<>();
        for (int i = 0; i < 7; i++) {
            split.put(new K(5 + 16 * i, "a" + i), i);
        }
        for (int i = 0; i < 6; i++) {
            split.put(new K(i * 2, "b" + i), i);
        }
        p("before resize " + split);
        split.put(new K(99, "trigger"), 99);
        p("after resize " + split);
    }

    static void iterationWhileModifying() {
        p("== iteration while modifying");
        ConcurrentHashMap<String, Integer> m = new ConcurrentHashMap<>();
        for (int i = 0; i < 10; i++) {
            m.put("k" + i, i);
        }
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = m.keySet().iterator();
        int n = 0;
        while (it.hasNext()) {
            String k = it.next();
            sb.append(k).append(' ');
            if (n < 30) {
                m.put("new" + n, n); // forces resizes during iteration
            }
            if (n % 3 == 0) {
                m.remove("k" + (9 - n % 10));
            }
            n++;
        }
        p("seen " + n + ": " + sb);
        p("after " + m.size() + " " + m);

        // Removing the element the iterator will return next does not stop iteration.
        ConcurrentHashMap<Integer, String> m2 = new ConcurrentHashMap<>();
        for (int i = 0; i < 8; i++) {
            m2.put(i, "v" + i);
        }
        Iterator<Map.Entry<Integer, String>> eit = m2.entrySet().iterator();
        Map.Entry<Integer, String> first = eit.next();
        m2.remove(1);
        m2.remove(2);
        StringBuilder sb2 = new StringBuilder(first.toString());
        while (eit.hasNext()) {
            Map.Entry<Integer, String> e = eit.next();
            sb2.append(' ').append(e);
            if (e.getKey() % 2 == 0) {
                e.setValue("set" + e.getKey());
            } else {
                eit.remove();
            }
        }
        p(sb2 + " -> " + m2);
        expect("iterator remove twice", () -> {
            Iterator<Integer> i2 = m2.keySet().iterator();
            i2.next();
            i2.remove();
            i2.remove();
        });
        expect("iterator remove before next", () -> m2.values().iterator().remove());
        expect("next past end", () -> {
            Iterator<String> i3 = new ConcurrentHashMap<String, String>().values().iterator();
            p("hasNext " + i3.hasNext());
            i3.next();
        });

        // Values iterator removes, Enumerations while modifying.
        ConcurrentHashMap<String, Integer> m3 = new ConcurrentHashMap<>();
        for (int i = 0; i < 12; i++) {
            m3.put("e" + i, i % 4);
        }
        for (Iterator<Integer> vi = m3.values().iterator(); vi.hasNext();) {
            if (vi.next() == 0) {
                vi.remove();
            }
        }
        p("values removed " + m3);
        Enumeration<String> keys = m3.keys();
        Enumeration<Integer> elems = m3.elements();
        StringBuilder sb3 = new StringBuilder();
        int j = 0;
        while (keys.hasMoreElements()) {
            sb3.append(keys.nextElement()).append('=').append(elems.hasMoreElements() ? elems.nextElement() : null)
                    .append(' ');
            m3.put("z" + j, j);
            m3.remove("e" + (11 - j));
            j++;
        }
        p("enum " + sb3);
        p("after enum " + m3);

        // for-each over entrySet with removal of other keys, and forEach with modification.
        ConcurrentHashMap<Integer, Integer> m4 = new ConcurrentHashMap<>();
        for (int i = 0; i < 20; i++) {
            m4.put(i, i);
        }
        List<Integer> seen = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : m4.entrySet()) {
            seen.add(e.getKey());
            m4.remove(e.getKey() + 1);
            m4.put(e.getKey() + 100, 0);
        }
        p("for-each " + seen.size() + " " + seen);
        List<Integer> seen2 = new ArrayList<>();
        m4.forEach((k, v) -> {
            seen2.add(k);
            if (k < 100) {
                m4.remove(k);
            }
        });
        p("forEach " + seen2.size() + " " + m4.keySet());
        Spliterator<Integer> sp = m4.keySet().spliterator();
        List<Integer> sps = new ArrayList<>();
        sp.tryAdvance(sps::add);
        m4.clear();
        sp.forEachRemaining(sps::add);
        p("spliterator after clear " + sps.size() + " empty " + m4.isEmpty());
    }

    static void threads() throws Exception {
        p("== threads");
        final ConcurrentHashMap<Integer, Integer> m = new ConcurrentHashMap<>();
        final int writers = 3;
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger errors = new AtomicInteger();
        Thread[] ts = new Thread[writers + 1];
        for (int w = 0; w < writers; w++) {
            final int base = w * 1000;
            ts[w] = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 400; i++) {
                        m.put(base + i, i);
                        m.merge(-1, 1, Integer::sum);
                        if (i % 4 == 0) {
                            m.remove(base + i / 2);
                        }
                        if (i % 50 == 0) {
                            Thread.yield();
                        }
                    }
                } catch (Throwable t) {
                    errors.incrementAndGet();
                    t.printStackTrace();
                }
            });
        }
        ts[writers] = new Thread(() -> {
            try {
                start.await();
                for (int r = 0; r < 20; r++) {
                    long sum = 0;
                    for (Map.Entry<Integer, Integer> e : m.entrySet()) {
                        sum += e.getValue();
                    }
                    for (Integer k : m.keySet()) {
                        if (m.get(k) == null && m.containsKey(k)) {
                            errors.incrementAndGet();
                        }
                    }
                    m.toString();
                    m.values().toArray();
                    Thread.yield();
                }
            } catch (Throwable t) {
                errors.incrementAndGet();
                t.printStackTrace();
            }
        });
        for (Thread t : ts) {
            t.start();
        }
        start.countDown();
        for (Thread t : ts) {
            t.join();
        }
        int expected = 0;
        TreeSet<Integer> keys = new TreeSet<>();
        for (int w = 0; w < writers; w++) {
            for (int i = 0; i < 400; i++) {
                keys.add(w * 1000 + i);
            }
            for (int i = 0; i < 400; i += 4) {
                keys.remove(w * 1000 + i / 2);
            }
        }
        expected = keys.size() + 1;
        p("errors " + errors.get() + " size " + m.size() + " expected " + expected + " counter " + m.get(-1)
                + " keys match " + new TreeSet<>(m.keySet()).equals(addCounter(keys)));
    }

    static TreeSet<Integer> addCounter(TreeSet<Integer> keys) {
        keys.add(-1);
        return keys;
    }

    static void nulls() {
        p("== nulls");
        final ConcurrentHashMap<String, String> m = new ConcurrentHashMap<>();
        m.put("a", "1");
        expect("put null key", () -> m.put(null, "x"));
        expect("put null value", () -> m.put("x", null));
        expect("get null", () -> m.get(null));
        expect("containsKey null", () -> m.containsKey(null));
        expect("containsValue null", () -> m.containsValue(null));
        expect("contains null", () -> m.contains(null));
        expect("remove null", () -> m.remove(null));
        expect("remove null,v", () -> m.remove(null, "1"));
        expect("remove k,null", () -> p("  " + m.remove("a", null)));
        expect("putIfAbsent null", () -> m.putIfAbsent("b", null));
        expect("putIfAbsent null key", () -> m.putIfAbsent(null, "b"));
        expect("replace null", () -> m.replace("a", null));
        expect("replace3 null", () -> m.replace("a", "1", null));
        expect("replace3 null old", () -> m.replace("a", null, "2"));
        expect("getOrDefault null", () -> m.getOrDefault(null, "d"));
        expect("putAll with null", () -> m.putAll(Collections.singletonMap("n", (String) null)));
        expect("computeIfAbsent null key", () -> m.computeIfAbsent(null, k -> "v"));
        expect("computeIfAbsent null fn", () -> m.computeIfAbsent("a", null));
        expect("compute null key", () -> m.compute(null, (k, v) -> "v"));
        expect("computeIfPresent null fn", () -> m.computeIfPresent("a", null));
        expect("merge null value", () -> m.merge("a", null, (x, y) -> x));
        expect("merge null fn", () -> m.merge("a", "1", null));
        expect("forEach null", () -> m.forEach(null));
        expect("replaceAll null result", () -> m.replaceAll((k, v) -> null));
        expect("keySet(null)", () -> m.keySet(null));
        expect("keySet contains null", () -> m.keySet().contains(null));
        expect("keySet remove null", () -> m.keySet().remove(null));
        expect("newKeySet add null", () -> ConcurrentHashMap.newKeySet().add(null));
        expect("values contains null", () -> m.values().contains(null));
        expect("values remove null", () -> p("  " + m.values().remove(null)));
        expect("entrySet contains null", () -> p("  " + m.entrySet().contains(null)));
        expect("entry setValue null", () -> m.entrySet().iterator().next().setValue(null));
        expect("entrySet add null key", () -> m.entrySet().add(new AbstractMap.SimpleEntry<String, String>(null, "x")));
        expect("containsAll with null", () -> p("  " + m.keySet().containsAll(Arrays.asList("a", null))));
        expect("removeAll null", () -> m.keySet().removeAll(null));
        expect("retainAll null", () -> m.values().retainAll(null));
        expect("equals map with null", () -> {
            HashMap<String, String> h = new HashMap<>();
            h.put("a", null);
            p("  " + m.equals(h) + " " + h.equals(m));
        });
        expect("search null", () -> m.search(1, null));
        expect("reduceKeys null", () -> m.reduceKeys(1, null));
        expect("forEachValue null", () -> m.forEachValue(1, null));
        p("still " + m);
    }

    static void computeFamily() {
        p("== compute family");
        ConcurrentHashMap<String, Integer> m = new ConcurrentHashMap<>();
        p("putIfAbsent new " + m.putIfAbsent("a", 1) + " again " + m.putIfAbsent("a", 2) + " " + m);
        p("computeIfAbsent present " + m.computeIfAbsent("a", k -> 9) + " absent " + m.computeIfAbsent("b", k -> 2)
                + " null " + m.computeIfAbsent("c", k -> null) + " " + m);
        p("computeIfPresent " + m.computeIfPresent("a", (k, v) -> v + 10) + " absent "
                + m.computeIfPresent("zz", (k, v) -> 1) + " to null " + m.computeIfPresent("b", (k, v) -> null) + " "
                + m);
        p("compute new " + m.compute("c", (k, v) -> v == null ? 3 : v + 1) + " again "
                + m.compute("c", (k, v) -> v == null ? 3 : v + 1) + " remove " + m.compute("a", (k, v) -> null)
                + " absent null " + m.compute("q", (k, v) -> null) + " " + m);
        p("merge new " + m.merge("d", 5, Integer::sum) + " existing " + m.merge("d", 5, Integer::sum) + " to null "
                + m.merge("c", 1, (x, y) -> null) + " " + m);
        p("replace " + m.replace("d", 1) + " missing " + m.replace("nope", 1) + " cas " + m.replace("d", 1, 2)
                + " cas fail " + m.replace("d", 1, 3) + " " + m);
        p("remove kv wrong " + m.remove("d", 9) + " right " + m.remove("d", 2) + " " + m);
        m.put("x", 1);
        m.put("y", 2);
        m.replaceAll((k, v) -> v * 100);
        p("replaceAll " + m + " getOrDefault " + m.getOrDefault("x", -1) + " " + m.getOrDefault("w", -1));
        expect("computeIfAbsent throws", () -> m.computeIfAbsent("t", k -> {
            throw new IllegalArgumentException();
        }));
        p("after throw " + m + " containsKey t " + m.containsKey("t"));
        expect("recursive computeIfAbsent", () -> m.computeIfAbsent("rec", k -> m.put("rec", 1)));
        p("after recursive " + m.get("rec") + " " + m.size());
        expect("recursive compute", () -> m.compute("rec2", (k, v) -> m.computeIfAbsent("rec2", kk -> 5)));
        // Mapping functions that touch other bins work.
        ConcurrentHashMap<Integer, Integer> fib = new ConcurrentHashMap<>();
        fib.put(0, 0);
        fib.put(1, 1);
        for (int i = 2; i < 30; i++) {
            final int n = i;
            fib.computeIfAbsent(n, k -> fib.get(n - 1) + fib.get(n - 2));
        }
        p("fib " + fib.get(29) + " " + fib.size());
        // Word counts with merge across resizes.
        ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
        String text = "the quick brown fox jumps over the lazy dog the end of the story is near and far";
        for (int r = 0; r < 3; r++) {
            for (String w : text.split(" ")) {
                counts.merge(w + r % 2, 1, Integer::sum);
            }
        }
        p("counts " + counts);
    }

    static void views() {
        p("== views");
        ConcurrentHashMap<String, Integer> m = new ConcurrentHashMap<>();
        for (int i = 0; i < 15; i++) {
            m.put("v" + i, i);
        }
        Set<String> ks = m.keySet();
        Collection<Integer> vs = m.values();
        Set<Map.Entry<String, Integer>> es = m.entrySet();
        p("same views " + (ks == m.keySet()) + " " + (vs == m.values()) + " " + (es == m.entrySet()));
        p("sizes " + ks.size() + " " + vs.size() + " " + es.size() + " " + m.mappingCount());
        p("toArray " + Arrays.toString(ks.toArray()) + " " + Arrays.toString(vs.toArray(new Integer[0])));
        Integer[] big = new Integer[20];
        Arrays.fill(big, -1);
        p("toArray big " + Arrays.toString(vs.toArray(big)));
        p("entry toString " + es.iterator().next() + " hash " + es.iterator().next().hashCode());
        p("contains " + ks.contains("v3") + " " + vs.contains(3) + " "
                + es.contains(new AbstractMap.SimpleEntry<>("v3", 3)) + " "
                + es.contains(new AbstractMap.SimpleEntry<>("v3", 4)));
        p("remove " + ks.remove("v0") + " " + vs.remove(1) + " " + vs.remove(100) + " "
                + es.remove(new AbstractMap.SimpleEntry<>("v2", 2)) + " "
                + es.remove(new AbstractMap.SimpleEntry<>("v3", 2)) + " " + m);
        p("removeAll " + ks.removeAll(Arrays.asList("v4", "v5", "zz")) + " retainAll "
                + vs.retainAll(Arrays.asList(6, 7, 8, 9, 10, 11, 12, 13, 14)) + " " + m);
        p("removeIf values " + vs.removeIf(v -> v % 2 == 0) + " entries "
                + es.removeIf(e -> e.getKey().equals("v7")) + " keys " + ks.removeIf(k -> k.equals("v9")) + " " + m);
        p("equals " + ks.equals(new HashSet<>(Arrays.asList("v11", "v13"))) + " " + es.equals(m.entrySet()) + " "
                + m.equals(new HashMap<>(m)) + " " + new HashMap<>(m).equals(m) + " "
                + (ks.hashCode() == new HashSet<>(ks).hashCode()) + " "
                + (es.hashCode() == new HashMap<>(m).entrySet().hashCode()));
        expect("values add", () -> vs.add(1));
        expect("values addAll", () -> vs.addAll(Arrays.asList(1)));
        expect("keySet add", () -> ks.add("k"));
        p("entrySet add " + es.add(new AbstractMap.SimpleEntry<>("added", 1)) + " again "
                + es.add(new AbstractMap.SimpleEntry<>("added", 2)) + " " + m);
        p("entrySet addAll " + es.addAll(Arrays.asList(new AbstractMap.SimpleEntry<>("p", 1),
                new AbstractMap.SimpleEntry<>("q", 2))) + " " + m);
        for (Map.Entry<String, Integer> e : es) {
            e.setValue(e.getValue() + 1000);
        }
        p("setValue wrote through " + m);
        Map.Entry<String, Integer> stale = es.iterator().next();
        m.put(stale.getKey(), -5);
        p("stale entry " + stale + " setValue returns " + stale.setValue(7) + " map " + m.get(stale.getKey()));
        p("streams " + ks.stream().sorted().collect(Collectors.joining(",")) + " "
                + vs.stream().mapToInt(Integer::intValue).sum() + " "
                + es.stream().map(Map.Entry::getKey).collect(Collectors.toList()));
        Spliterator<String> sp = ks.spliterator();
        p("spliterator chars " + Integer.toHexString(sp.characteristics()) + " "
                + Integer.toHexString(vs.spliterator().characteristics()) + " "
                + Integer.toHexString(es.spliterator().characteristics()) + " est " + sp.estimateSize());
        Spliterator<String> half = sp.trySplit();
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        half.forEachRemaining(a::add);
        sp.forEachRemaining(b::add);
        p("split " + a + " " + b + " est " + half.estimateSize());
        ks.clear();
        p("cleared " + m + " " + m.isEmpty() + " " + ks + " " + vs + " " + es);
        p("view toString self " + new ConcurrentHashMap<String, Integer>().keySet());
        ConcurrentHashMap<Object, Object> self = new ConcurrentHashMap<>();
        self.put("me", self);
        p("self " + self + " " + self.values());
    }

    static void keySetViews() {
        p("== KeySetView");
        ConcurrentHashMap.KeySetView<String, Boolean> set = ConcurrentHashMap.newKeySet();
        p("mapped " + set.getMappedValue() + " add " + set.add("a") + " " + set.add("a") + " addAll "
                + set.addAll(Arrays.asList("b", "c", "a")) + " " + set.addAll(Arrays.asList("b")) + " " + set);
        p("map " + set.getMap() + " " + set.getMap().getClass().getName());
        ConcurrentHashMap.KeySetView<Integer, Boolean> sized = ConcurrentHashMap.newKeySet(100);
        for (int i = 0; i < 40; i++) {
            sized.add(i * 13);
        }
        p("sized " + sized);
        ConcurrentHashMap<String, Integer> m = new ConcurrentHashMap<>();
        m.put("x", 1);
        ConcurrentHashMap.KeySetView<String, Integer> view = m.keySet(42);
        p("view " + view.getMappedValue() + " add " + view.add("y") + " add existing " + view.add("x") + " "
                + view.addAll(Arrays.asList("z", "x")) + " " + m + " map same " + (view.getMap() == m));
        ConcurrentHashMap.KeySetView<String, Integer> plain = m.keySet();
        p("plain mapped " + plain.getMappedValue());
        expect("plain add", () -> plain.add("w"));
        expect("plain addAll", () -> plain.addAll(Arrays.asList("w")));
        p("contains " + view.contains("y") + " remove " + view.remove("y") + " " + view.remove("y") + " " + m);
        List<String> each = new ArrayList<>();
        view.forEach(each::add);
        p("forEach " + each + " hash " + (view.hashCode() == new HashSet<>(view).hashCode()) + " equals "
                + view.equals(new HashSet<>(Arrays.asList("x", "z"))));
        p("retainAll " + view.retainAll(Collections.singleton("z")) + " " + m + " isEmpty " + view.isEmpty());
        Set<String> concurrentSet = ConcurrentHashMap.newKeySet();
        for (String s : "one two three four five six seven eight nine ten".split(" ")) {
            concurrentSet.add(s);
        }
        for (String s : concurrentSet) {
            if (s.length() == 3) {
                concurrentSet.remove(s);
                concurrentSet.add(s.toUpperCase());
            }
        }
        p("modified during iteration " + concurrentSet);
    }

    static void bulk() {
        p("== bulk");
        ConcurrentHashMap<String, Integer> m = new ConcurrentHashMap<>();
        for (int i = 0; i < 50; i++) {
            m.put("b" + i, i);
        }
        long seq = Long.MAX_VALUE;
        StringBuilder sb = new StringBuilder();
        m.forEach(seq, (k, v) -> {
            if (v % 10 == 0) {
                sb.append(k).append(' ');
            }
        });
        m.forEach(seq, (k, v) -> v % 10 == 1 ? k + "!" : null, s -> sb.append(s).append(' '));
        m.forEachKey(seq, k -> {
            if (k.endsWith("2")) {
                sb.append(k).append(' ');
            }
        });
        m.forEachKey(seq, k -> k.endsWith("3") ? k.toUpperCase() : null, s -> sb.append(s).append(' '));
        m.forEachValue(seq, v -> {
            if (v > 46) {
                sb.append(v).append(' ');
            }
        });
        m.forEachValue(seq, v -> v < 3 ? -v : null, v -> sb.append(v).append(' '));
        m.forEachEntry(seq, e -> {
            if (e.getValue() == 25) {
                sb.append(e).append(' ');
            }
        });
        m.forEachEntry(seq, e -> e.getValue() == 26 ? e.getKey() : null, s -> sb.append(s).append(' '));
        p("forEach " + sb);
        p("search " + m.search(seq, (k, v) -> v > 40 ? k : null) + " " + m.search(seq, (k, v) -> null) + " "
                + m.searchKeys(seq, k -> k.length() == 2 ? k : null) + " " + m.searchValues(seq, v -> v > 45 ? v : null)
                + " " + m.searchEntries(seq, e -> e.getValue() == 7 ? e.toString() : null));
        p("search parallel " + m.search(1, (k, v) -> v == 33 ? k : null) + " "
                + m.searchKeys(1, k -> k.equals("b44") ? k : null) + " " + m.searchValues(1, v -> v == 12 ? v : null)
                + " " + m.searchEntries(1, e -> e.getKey().equals("b9") ? e.getValue() : null));
        // Parallel runs (small thresholds on OpenJDK) combine partial results in any order and apply the basis per
        // task, so the reducers here are commutative and the bases neutral except in the sequential run.
        for (long th : new long[] {seq, 1, 10}) {
            p("reduce " + th + " " + m.reduce(th, (k, v) -> v, Integer::sum) + " "
                    + m.reduce(th, (k, v) -> v > 1000 ? v : null, Integer::sum) + " "
                    + m.reduceToLong(th, (k, v) -> (long) v * v, 0L, Long::sum) + " "
                    + m.reduceToInt(th, (k, v) -> v, 0, Math::max) + " "
                    + m.reduceToDouble(th, (k, v) -> v / 2.0, 0.0, Double::sum));
            p("reduceKeys " + th + " " + m.reduceKeys(th, (a, b) -> a.compareTo(b) > 0 ? a : b) + " "
                    + m.reduceKeys(th, String::length, Integer::sum) + " "
                    + m.reduceKeysToLong(th, k -> k.length(), 0L, Long::sum) + " "
                    + m.reduceKeysToInt(th, k -> k.charAt(k.length() - 1), 0, Math::max) + " "
                    + m.reduceKeysToDouble(th, k -> k.length(), 1.0, Math::max));
            p("reduceValues " + th + " " + m.reduceValues(th, Math::min) + " "
                    + m.reduceValues(th, v -> v % 2 == 0 ? v : null, Integer::sum) + " "
                    + m.reduceValuesToLong(th, v -> v, th == seq ? 100L : 0L, Long::sum) + " "
                    + m.reduceValuesToInt(th, v -> v, Integer.MAX_VALUE, Math::min) + " "
                    + m.reduceValuesToDouble(th, v -> v, 0.0, Math::max));
            p("reduceEntries " + th + " "
                    + m.reduceEntries(th, (a, b) -> a.getValue() > b.getValue() ? a : b) + " "
                    + m.reduceEntries(th, e -> e.getKey().length(), Integer::sum) + " "
                    + m.reduceEntriesToLong(th, e -> e.getValue(), 0L, Long::sum) + " "
                    + m.reduceEntriesToInt(th, e -> e.getValue(), 0, Integer::sum) + " "
                    + m.reduceEntriesToDouble(th, e -> e.getValue(), 0.0, Double::sum));
            AtomicLong sum = new AtomicLong();
            AtomicInteger count = new AtomicInteger();
            m.forEach(th, (k, v) -> sum.addAndGet(v));
            m.forEachKey(th, k -> count.incrementAndGet());
            m.forEachValue(th, v -> sum.addAndGet(v));
            m.forEachEntry(th, e -> count.incrementAndGet());
            p("forEach " + th + " " + sum + " " + count);
        }
        ConcurrentHashMap<String, Integer> empty = new ConcurrentHashMap<>();
        p("empty " + empty.reduce(1, (k, v) -> v, Integer::sum) + " " + empty.reduceKeys(seq, (a, b) -> a) + " "
                + empty.reduceValuesToInt(seq, v -> v, 7, Integer::sum) + " " + empty.search(seq, (k, v) -> k) + " "
                + empty.reduceEntries(seq, (a, b) -> a));
        expect("bulk entry setValue", () -> m.forEachEntry(seq, e -> e.setValue(1)));
    }

    static void misc() {
        p("== misc");
        ConcurrentHashMap<String, Integer> m = new ConcurrentHashMap<>();
        for (int i = 0; i < 6; i++) {
            m.put("m" + i, i);
        }
        p("contains " + m.contains(3) + " " + m.contains(9) + " containsValue " + m.containsValue(5));
        p("hashCode " + (m.hashCode() == new HashMap<>(m).hashCode()) + " equals " + m.equals(m) + " "
                + m.equals("x") + " " + m.equals(new HashMap<String, Integer>()));
        ConcurrentMap<String, Integer> cm = m;
        p("ConcurrentMap " + cm.getOrDefault("m1", 0) + " " + (m instanceof AbstractMap) + " "
                + m.getClass().getSuperclass().getName());
        StringBuilder sb = new StringBuilder();
        for (Enumeration<String> e = m.keys(); e.hasMoreElements();) {
            sb.append(e.nextElement());
        }
        for (Enumeration<Integer> e = m.elements(); e.hasMoreElements();) {
            sb.append(e.nextElement());
        }
        p("keys/elements " + sb);
        TreeMap<String, Integer> sorted = new TreeMap<>(m);
        p("sorted " + sorted);
        m.clear();
        p("clear " + m + " " + m.size() + " " + m.mappingCount() + " " + m.keys().hasMoreElements());
        m.put("again", 1);
        p("reuse " + m);
    }
}
