import java.util.*;

/**
 * TreeMap and TreeSet: navigation, live sub-map / descending / key set views, write-through, range checks and
 * fail-fast iterators. Run on OpenJDK and on switchapk by tests/run_dex_test.sh, output must match.
 */
public class TreeMapTest {
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

    public static void main(String[] args) {
        // Randomized operations against a reference built from a sorted list.
        Random rnd = new Random(42);
        TreeMap<Integer, Integer> m = new TreeMap<Integer, Integer>();
        ArrayList<Integer> ref = new ArrayList<Integer>();
        int checks = 0;
        for (int i = 0; i < 20000; i++) {
            int k = rnd.nextInt(2000);
            int op = rnd.nextInt(10);
            if (op < 6) {
                m.put(k, i);
                int at = Collections.binarySearch(ref, k);
                if (at < 0) ref.add(-at - 1, k);
            } else if (op < 9) {
                m.remove(k);
                int at = Collections.binarySearch(ref, k);
                if (at >= 0) ref.remove(at);
            } else {
                Iterator<Integer> it = m.keySet().iterator();
                int skip = rnd.nextInt(5);
                while (it.hasNext()) {
                    Integer x = it.next();
                    if (skip-- == 0) {
                        it.remove();
                        ref.remove(Collections.binarySearch(ref, x));
                        break;
                    }
                }
            }
            if (i % 500 == 0) {
                if (!new ArrayList<Integer>(m.keySet()).equals(ref)) throw new AssertionError("diverged at " + i);
                checks++;
            }
        }
        p("random ops: size=" + m.size() + " checks=" + checks + " first=" + m.firstKey() + " last=" + m.lastKey());
        p("navigation: " + m.lowerKey(1000) + " " + m.floorKey(1000) + " " + m.ceilingKey(1000) + " "
                + m.higherKey(1000) + " " + m.lowerKey(-1) + " " + m.higherKey(5000));

        TreeMap<String, Integer> t = new TreeMap<String, Integer>();
        for (String s : "kiwi apple fig date banana cherry grape lemon mango".split(" ")) t.put(s, s.length());
        p("map: " + t);

        // sub-maps are live views in both directions
        SortedMap<String, Integer> sub = t.subMap("c", "h");
        p("subMap c..h: " + sub);
        t.put("elder", 5);
        p("after put on base: " + sub);
        sub.put("cranberry", 9);
        p("after put on view: " + t.containsKey("cranberry") + " size=" + t.size());
        p("put out of range: " + tryIt(() -> sub.put("zebra", 1)));
        sub.remove("fig");
        p("after remove on view: " + t.containsKey("fig"));
        NavigableMap<String, Integer> head = t.headMap("date", true);
        p("headMap date inclusive: " + head + " size=" + head.size());
        NavigableMap<String, Integer> tail = t.tailMap("grape", false);
        p("tailMap grape exclusive: " + tail + " first=" + tail.firstKey() + " last=" + tail.lastKey());
        p("tail navigation: " + tail.ceilingKey("a") + " " + tail.floorKey("z") + " " + tail.lowerKey("kiwi")
                + " " + tail.higherKey("lemon"));
        p("sub of sub: " + t.subMap("b", true, "m", false).subMap("c", true, "f", true));
        p("sub of sub out of range: " + tryIt(() -> t.subMap("c", "f").subMap("a", "d")));
        p("bad range: " + tryIt(() -> t.subMap("m", "c")));

        // descending views
        NavigableMap<String, Integer> desc = t.descendingMap();
        p("descending: " + desc.keySet());
        p("descending first/last: " + desc.firstKey() + " " + desc.lastKey());
        p("descending navigation: " + desc.ceilingKey("d") + " " + desc.higherKey("d") + " " + desc.floorKey("d")
                + " " + desc.lowerKey("d"));
        p("descending headMap(fig): " + desc.headMap("fig"));
        p("descending tailMap(fig): " + desc.tailMap("fig"));
        p("descending subMap(lemon, cherry): " + desc.subMap("lemon", "cherry"));
        p("descending descending: " + desc.descendingMap().keySet());
        p("descending comparator: " + desc.comparator().compare("a", "b"));
        p("descendingKeySet: " + t.descendingKeySet());
        NavigableMap<String, Integer> subDesc = t.subMap("b", true, "l", true).descendingMap();
        p("sub descending: " + subDesc + " poll=" + subDesc.pollFirstEntry() + " now " + t.containsKey("kiwi"));

        // entries
        Map.Entry<String, Integer> fe = t.firstEntry();
        p("firstEntry setValue: " + tryIt(() -> fe.setValue(0)));
        for (Map.Entry<String, Integer> e : t.entrySet()) e.setValue(e.getValue() * 10);
        p("entry setValue writes through: " + t);
        p("entrySet contains: " + t.entrySet().contains(new AbstractMap.SimpleEntry<String, Integer>("apple", 50))
                + " " + t.entrySet().contains(new AbstractMap.SimpleEntry<String, Integer>("apple", 5)));
        p("poll: " + t.pollFirstEntry() + " " + t.pollLastEntry() + " -> " + t.keySet());

        // fail-fast
        p("CME: " + tryIt(() -> {
            for (String k : t.keySet()) t.remove(k);
        }));
        p("CME on view: " + tryIt(() -> {
            for (String k : t.tailMap("c").keySet()) t.put("zz" + k, 1);
        }));
        p("next past end: " + tryIt(() -> new TreeMap<String, String>().keySet().iterator().next()));
        p("firstKey empty: " + tryIt(() -> new TreeMap<String, String>().firstKey()));
        p("null key: " + tryIt(() -> new TreeMap<String, String>().put(null, "x")));
        p("null key, comparator: " + tryIt(() -> new TreeMap<String, String>(
                Comparator.nullsFirst(Comparator.<String>naturalOrder())).put(null, "x")));

        // views clear only their range
        TreeMap<Integer, String> nums = new TreeMap<Integer, String>();
        for (int i = 0; i < 20; i++) nums.put(i, "v" + i);
        nums.subMap(5, 15).clear();
        nums.headMap(2).keySet().clear();
        nums.tailMap(18, true).values().clear();
        p("cleared ranges: " + nums.keySet());
        nums.values().remove("v16");
        nums.keySet().removeIf(k -> k == 3);
        p("values/keySet removal: " + nums.keySet() + " values=" + nums.values());
        p("equals/hash: " + nums.equals(new HashMap<Integer, String>(nums)) + " " + (nums.hashCode() == new HashMap<Integer, String>(nums).hashCode()));
        @SuppressWarnings("unchecked")
        TreeMap<Integer, String> copy = (TreeMap<Integer, String>) nums.clone();
        copy.put(100, "x");
        p("clone independent: " + nums.containsKey(100) + " " + copy.lastKey());

        // TreeSet views
        TreeSet<Integer> set = new TreeSet<Integer>();
        for (int i = 0; i < 30; i += 3) set.add(i);
        NavigableSet<Integer> view = set.subSet(5, true, 20, false);
        set.add(7);
        view.add(8);
        p("set view: " + view + " set=" + set);
        p("set view add out of range: " + tryIt(() -> view.add(25)));
        view.pollFirst();
        p("set after view poll: " + set);
        p("set descending: " + set.descendingSet() + " headSet(9)=" + set.headSet(9) + " tailSet(20,false)="
                + set.tailSet(20, false));
        Iterator<Integer> di = set.descendingIterator();
        di.next();
        di.remove();
        p("descending iterator remove: " + set);
        p("navigable keySet view: " + nums.navigableKeySet().headSet(17) + " " + nums.navigableKeySet().descendingSet());
        @SuppressWarnings("unchecked")
        TreeSet<Integer> sc = (TreeSet<Integer>) set.clone();
        sc.add(1000);
        p("set clone independent: " + set.contains(1000));

        // A big map stays fast: 300k ascending and random inserts (the old array map was quadratic here).
        long t0 = System.nanoTime();
        TreeMap<Integer, Integer> big = new TreeMap<Integer, Integer>();
        for (int i = 0; i < 150000; i++) big.put(i, i);
        Random r2 = new Random(7);
        for (int i = 0; i < 150000; i++) big.put(r2.nextInt(), i);
        for (int i = 0; i < 100000; i++) big.remove(i);
        long ms = (System.nanoTime() - t0) / 1000000;
        p("big map size " + big.size() + (ms < 20000 ? " in time" : " too slow: " + ms + " ms"));
    }
}
