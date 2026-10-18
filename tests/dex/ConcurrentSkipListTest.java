import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ConcurrentSkipListMap / Set: the sorted map contract, views, null refusal, atomic ConcurrentMap operations,
 * weakly consistent iteration and thread safety. Run on OpenJDK and on switchapk by tests/run_dex_test.sh,
 * output must match.
 */
public class ConcurrentSkipListTest {
    static void p(Object o) {
        System.out.println(o);
    }

    static String tryIt(Runnable r) {
        try {
            r.run();
            return "ok";
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) throws Exception {
        ConcurrentSkipListMap<String, Integer> m = new ConcurrentSkipListMap<String, Integer>();
        for (String s : "kiwi apple fig date banana cherry grape".split(" ")) m.put(s, s.length());
        p("map: " + m + " size=" + m.size());
        p("is TreeMap: " + (((Object) m) instanceof TreeMap) + ", ConcurrentNavigableMap: " + (m instanceof ConcurrentNavigableMap));
        p("navigation: " + m.lowerKey("d") + " " + m.floorKey("date") + " " + m.ceilingKey("e") + " " + m.higherKey("kiwi"));
        p("first/last: " + m.firstEntry() + " " + m.lastEntry());
        p("entry setValue: " + tryIt(() -> m.firstEntry().setValue(1)));
        p("null key: " + tryIt(() -> m.put(null, 1)) + " null value: " + tryIt(() -> m.put("x", null))
                + " get null: " + tryIt(() -> m.get(null)));
        p("putIfAbsent: " + m.putIfAbsent("fig", 99) + " " + m.putIfAbsent("lime", 4) + " -> " + m.get("lime"));
        p("replace: " + m.replace("fig", 3, 30) + " " + m.replace("fig", 3, 31) + " " + m.replace("nope", 1) + " -> " + m.get("fig"));
        p("remove(k,v): " + m.remove("lime", 5) + " " + m.remove("lime", 4) + " -> " + m.containsKey("lime"));
        p("compute: " + m.computeIfAbsent("melon", k -> k.length()) + " " + m.computeIfPresent("melon", (k, v) -> null)
                + " " + m.merge("apple", 100, Integer::sum) + " " + m.compute("zz", (k, v) -> v == null ? 1 : v + 1));
        p("getOrDefault: " + m.getOrDefault("nope", -1));

        // views share the map
        ConcurrentNavigableMap<String, Integer> sub = m.subMap("b", "g");
        p("subMap b..g: " + sub);
        sub.put("cranberry", 9);
        p("put via view: " + m.containsKey("cranberry") + " out of range: " + tryIt(() -> sub.put("zebra", 1)));
        ConcurrentNavigableMap<String, Integer> desc = m.descendingMap();
        p("descending: " + desc.keySet() + " first=" + desc.firstKey());
        p("descending head(fig): " + desc.headMap("fig") + " tail(fig): " + desc.tailMap("fig").keySet());
        p("descendingKeySet: " + m.descendingKeySet() + " headSet(d): " + m.navigableKeySet().headSet("d"));
        p("poll via view: " + sub.pollFirstEntry() + " -> " + m.containsKey("banana"));

        // weakly consistent iteration: modifying while iterating is allowed
        Iterator<String> it = m.keySet().iterator();
        int seen = 0;
        while (it.hasNext()) {
            String k = it.next();
            m.remove(k);
            m.put("~" + k, 1);
            seen++;
            if (seen > 100) break;
        }
        p("iterate while modifying: no CME, seen " + (seen > 0) + ", size " + m.size());
        Iterator<Map.Entry<String, Integer>> ei = m.entrySet().iterator();
        ei.next();
        ei.remove();
        p("iterator remove: " + m.size());
        p("equals: " + m.equals(new TreeMap<String, Integer>(m)) + " " + new TreeMap<String, Integer>(m).equals(m));
        ConcurrentSkipListMap<String, Integer> c = m.clone();
        c.put("aaa", 1);
        p("clone independent: " + m.containsKey("aaa") + " " + c.firstKey());

        // thread safety: 4 writers, 2 readers, distinct key ranges plus contended counters
        final ConcurrentSkipListMap<Integer, Integer> shared = new ConcurrentSkipListMap<Integer, Integer>();
        final AtomicInteger readerErrors = new AtomicInteger();
        Thread[] ts = new Thread[6];
        for (int t = 0; t < 4; t++) {
            final int id = t;
            ts[t] = new Thread(() -> {
                for (int i = 0; i < 5000; i++) {
                    shared.put(id * 100000 + i, i);
                    shared.merge(-1, 1, Integer::sum);
                    if (i % 2 == 0) shared.remove(id * 100000 + i);
                }
            });
        }
        for (int t = 4; t < 6; t++) {
            ts[t] = new Thread(() -> {
                try {
                    for (int r = 0; r < 50; r++) {
                        Integer prev = null;
                        for (Integer k : shared.keySet()) {
                            if (prev != null && prev >= k) readerErrors.incrementAndGet();
                            prev = k;
                        }
                        shared.headMap(150000).size();
                        shared.descendingMap().firstEntry();
                    }
                } catch (RuntimeException e) {
                    readerErrors.incrementAndGet();
                }
            });
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();
        p("threads: size=" + shared.size() + " counter=" + shared.get(-1) + " reader errors=" + readerErrors.get());

        // the set
        ConcurrentSkipListSet<Integer> set = new ConcurrentSkipListSet<Integer>(Comparator.reverseOrder());
        for (int i = 0; i < 10; i++) set.add(i * 3);
        p("set: " + set + " add dup: " + set.add(3) + " first=" + set.first() + " ceiling(10)=" + set.ceiling(10));
        NavigableSet<Integer> view = set.subSet(20, true, 5, false);
        view.remove(12);
        p("set view: " + view + " -> " + set.contains(12) + " descending: " + set.descendingSet());
        p("set null: " + tryIt(() -> set.add(null)) + " pollFirst=" + set.pollFirst() + " pollLast=" + set.pollLast());
        for (Integer x : set) set.remove(x);
        p("set remove while iterating: " + set.isEmpty());
        ConcurrentSkipListSet<String> words = new ConcurrentSkipListSet<String>(Arrays.asList("b", "a", "c"));
        ConcurrentSkipListSet<String> wc = words.clone();
        wc.add("d");
        p("set clone: " + words + " " + wc + " stream: " + words.stream().map(String::toUpperCase).reduce("", String::concat));
    }
}
