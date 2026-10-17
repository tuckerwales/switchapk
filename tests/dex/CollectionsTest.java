import java.io.*;
import java.util.*;
import java.util.function.*;

/**
 * Hashtable, Properties, Arrays.asList and the java.util.Collections wrappers: iteration order, unsupported
 * operations, write-through views, equality and the static algorithms. Run on OpenJDK and on switchapk by
 * tests/run_dex_test.sh; the output must match.
 */
public class CollectionsTest {
    static void p(Object o) {
        System.out.println(o);
    }

    interface Check {
        Object run() throws Exception;
    }

    /** Runs c and prints its result, or the exception's class (and message when withMessage). */
    static void t(String what, Check c) {
        t(what, false, c);
    }

    static void t(String what, boolean withMessage, Check c) {
        try {
            p(what + " = " + c.run());
        } catch (Exception e) {
            p(what + " ! " + e.getClass().getName() + (withMessage ? ": " + e.getMessage() : ""));
        }
    }

    static <T> String enumStr(Enumeration<T> e) {
        StringBuilder sb = new StringBuilder("[");
        while (e.hasMoreElements()) {
            sb.append(e.nextElement());
            if (e.hasMoreElements()) {
                sb.append(", ");
            }
        }
        return sb.append(']').toString();
    }

    public static void main(String[] args) throws Exception {
        hashtable();
        hashtableDefaults();
        properties();
        arraysAsList();
        empties();
        singletons();
        copies();
        unmodifiable();
        synchronizedWrappers();
        checked();
        misc();
        algorithms();
        p("done");
    }

    static void hashtable() {
        p("-- hashtable");
        Hashtable<String, Integer> h = new Hashtable<String, Integer>();
        for (int i = 0; i < 100; i++) {
            h.put("k" + i, i);
            if (i == 7 || i == 8 || i == 9 || i == 17 || i == 18 || i == 35 || i == 36 || i == 71 || i == 72) {
                p(h.size() + " " + h);
            }
        }
        for (int i = 0; i < 100; i += 2) {
            h.remove("k" + i);
        }
        p("after removes " + h);
        p("keys " + enumStr(h.keys()));
        p("elements " + enumStr(h.elements()));
        p("entrySet " + h.entrySet());
        p("keySet " + h.keySet() + " " + h.keySet().getClass().getName());
        p("values " + h.values() + " " + h.values().getClass().getName());
        StringBuilder fe = new StringBuilder();
        h.forEach((k, v) -> fe.append(k).append(' '));
        p("forEach " + fe);
        p("hashCode " + h.hashCode() + " equals HashMap " + h.equals(new HashMap<String, Integer>(h))
                + " HashMap equals " + new HashMap<String, Integer>(h).equals(h));

        @SuppressWarnings("unchecked")
        Hashtable<String, Integer> c = (Hashtable<String, Integer>) h.clone();
        c.put("new", -1);
        c.remove("k1");
        p("clone " + c);
        p("orig " + h.size() + " " + h.containsKey("new") + " " + h.containsKey("k1"));

        Hashtable<Integer, String> ints = new Hashtable<Integer, String>(5);
        for (int i : new int[] {-1, 0, 5, 10, Integer.MIN_VALUE, Integer.MAX_VALUE, 3, 8, 13, 21, 34, 55}) {
            ints.put(i, "v" + i);
        }
        p("ints " + ints);
        Hashtable<Integer, String> fromMap = new Hashtable<Integer, String>(new TreeMap<Integer, String>(ints));
        p("fromMap " + fromMap);
        Hashtable<Integer, String> lf = new Hashtable<Integer, String>(2, 2.0f);
        for (int i = 0; i < 10; i++) {
            lf.put(i * 3, "x");
        }
        p("lf2 " + lf);
        Hashtable<Integer, String> zero = new Hashtable<Integer, String>(0);
        zero.put(1, "a");
        zero.put(2, "b");
        p("cap0 " + zero);
        t("capacity -1", true, () -> new Hashtable<Object, Object>(-1));
        t("load 0", true, () -> new Hashtable<Object, Object>(1, 0f));
        t("load NaN", true, () -> new Hashtable<Object, Object>(1, Float.NaN));

        Hashtable<String, String> n = new Hashtable<String, String>();
        n.put("a", "1");
        t("put null key", () -> n.put(null, "x"));
        t("put null value", () -> n.put("x", null));
        t("get null", () -> n.get(null));
        t("containsKey null", () -> n.containsKey(null));
        t("contains null", () -> n.contains(null));
        t("containsValue null", () -> n.containsValue(null));
        t("remove null", () -> n.remove(null));
        t("putIfAbsent null value", () -> n.putIfAbsent("b", null));
        t("replace null", () -> n.replace("a", null));
        t("getOrDefault", () -> n.getOrDefault("zz", "dflt") + " " + n.getOrDefault("a", "dflt"));
        t("putIfAbsent", () -> n.putIfAbsent("a", "9") + " " + n.putIfAbsent("b", "2") + " " + n);
        t("remove(k,v)", () -> n.remove("a", "9") + " " + n.remove("a", "1") + " " + n);
        t("replace(k,o,n)", () -> n.replace("b", "x", "y") + " " + n.replace("b", "2", "3") + " " + n);
        t("replace(k,v)", () -> n.replace("b", "4") + " " + n.replace("q", "4") + " " + n);
        t("computeIfAbsent", () -> n.computeIfAbsent("c", k -> k + "!") + " " + n.computeIfAbsent("d", k -> null)
                + " " + n);
        t("computeIfPresent", () -> n.computeIfPresent("c", (k, v) -> v + "?") + " "
                + n.computeIfPresent("c", (k, v) -> null) + " " + n);
        t("compute", () -> n.compute("e", (k, v) -> v + "e") + " " + n.compute("e", (k, v) -> null) + " " + n);
        t("merge", () -> n.merge("b", "m", String::concat) + " " + n.merge("f", "m", String::concat) + " "
                + n.merge("f", "m", (a, b) -> null) + " " + n);
        t("merge null value", () -> n.merge("g", null, String::concat));
        t("replaceAll", () -> {
            n.replaceAll((k, v) -> k + v);
            return n;
        });
        t("replaceAll null", () -> {
            n.replaceAll((k, v) -> null);
            return n;
        });
        t("entry setValue null", () -> n.entrySet().iterator().next().setValue(null));
        t("entry toString", () -> n.entrySet().iterator().next() + " "
                + n.entrySet().contains(new AbstractMap.SimpleEntry<String, String>("b", "b4m")));

        // Removal through the views, and fail-fast iterators.
        Hashtable<Integer, Integer> r = new Hashtable<Integer, Integer>();
        for (int i = 0; i < 20; i++) {
            r.put(i, i * i);
        }
        for (Iterator<Integer> it = r.keySet().iterator(); it.hasNext();) {
            if (it.next() % 3 == 0) {
                it.remove();
            }
        }
        r.values().removeIf(v -> v % 2 == 0);
        for (Iterator<Map.Entry<Integer, Integer>> it = r.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, Integer> e = it.next();
            if (e.getKey() == 7) {
                it.remove();
            } else {
                e.setValue(e.getValue() + 1);
            }
        }
        p("view removes " + r);
        p("entrySet remove " + r.entrySet().remove(new AbstractMap.SimpleEntry<Integer, Integer>(1, 2)) + " "
                + r.entrySet().remove(new AbstractMap.SimpleEntry<Integer, Integer>(5, 0)) + " " + r);
        p("keySet remove " + r.keySet().remove(11) + " values remove " + r.values().remove(170) + " " + r);
        t("iterator remove twice", () -> {
            Iterator<Integer> it = r.keySet().iterator();
            it.next();
            it.remove();
            it.remove();
            return r;
        });
        t("iterator remove first", () -> {
            r.keySet().iterator().remove();
            return r;
        });
        t("enumeration remove", () -> {
            Iterator<Integer> it = r.keys().asIterator();
            it.next();
            it.remove();
            return r;
        });
        t("cme", () -> {
            for (Integer k : r.keySet()) {
                r.put(k + 100, 1);
            }
            return r;
        });
        t("enumeration no cme", () -> {
            Enumeration<Integer> e = r.keys();
            e.nextElement();
            r.put(-5, 5);
            return e.nextElement();
        });
        t("empty enumeration", () -> new Hashtable<Object, Object>().keys().getClass().getName() + " "
                + new Hashtable<Object, Object>().entrySet().iterator().hasNext());
        t("next past end", () -> {
            Hashtable<String, String> one = new Hashtable<String, String>();
            one.put("x", "y");
            Iterator<String> it = one.keySet().iterator();
            it.next();
            return it.next();
        });

        Hashtable<Object, Object> self = new Hashtable<Object, Object>();
        self.put("me", self);
        self.put(self.size(), "one");
        p("self " + self + " hash " + self.hashCode() + " equals " + self.equals(self));
        Hashtable<String, Integer> clr = new Hashtable<String, Integer>(h);
        clr.keySet().clear();
        p("clear via keySet " + clr + " " + clr.isEmpty() + " " + h.contains(99) + " " + h.containsValue(98));
    }

    static void hashtableDefaults() {
        p("-- hashtable vs map");
        Hashtable<String, Integer> h = new Hashtable<String, Integer>();
        HashMap<String, Integer> m = new HashMap<String, Integer>();
        for (String s : "the quick brown fox jumps over the lazy dog again and again".split(" ")) {
            h.merge(s, 1, Integer::sum);
            m.merge(s, 1, Integer::sum);
        }
        p(h);
        p(new TreeMap<String, Integer>(h).equals(new TreeMap<String, Integer>(m)) + " " + h.equals(m) + " "
                + (h.hashCode() == m.hashCode()));
        Map<String, Integer> sync = Collections.synchronizedMap(h);
        p(sync + " " + sync.getClass().getName());
    }

    static void properties() throws IOException {
        p("-- properties");
        Properties defs = new Properties();
        defs.setProperty("color", "red");
        defs.setProperty("size", "10");
        Properties pr = new Properties(defs);
        pr.setProperty("size", "12");
        pr.setProperty("name", "box");
        pr.put("notString", 5);
        p(pr.getProperty("color") + " " + pr.getProperty("size") + " " + pr.getProperty("name") + " "
                + pr.getProperty("notString") + " " + pr.getProperty("missing") + " "
                + pr.getProperty("missing", "dflt") + " " + pr.get("color"));
        p(new TreeSet<String>(pr.stringPropertyNames()));
        TreeSet<Object> names = new TreeSet<Object>();
        for (Enumeration<?> e = pr.propertyNames(); e.hasMoreElements();) {
            names.add(e.nextElement());
        }
        p(names + " size " + pr.size() + " containsKey color " + pr.containsKey("color"));
        Properties ld = new Properties();
        ld.load(new StringReader("# comment\n! other\na=1\nb : two\\\n   three\nc\\ d=e\\tf\nempty=\n\\u0041=\\u0042\n"));
        p(new TreeMap<Object, Object>(ld));
        t("put null", () -> ld.put("x", null));
        t("setProperty null", () -> ld.setProperty(null, "x"));
        p(ld.getClass().getSuperclass().getName() + " " + (ld instanceof Hashtable));
        Properties one = new Properties();
        one.setProperty("k", "v w=x");
        StringWriter sw = new StringWriter();
        one.store(sw, "c");
        String[] lines = sw.toString().split("\n");
        p(lines[0] + " | " + lines[2]);
        @SuppressWarnings("unchecked")
        Properties cl = (Properties) one.clone();
        cl.setProperty("k2", "v2");
        p(one.size() + " " + cl.size() + " " + cl.getClass().getName());
    }

    static void arraysAsList() {
        p("-- Arrays.asList");
        String[] arr = {"c", "a", "b"};
        List<String> l = Arrays.asList(arr);
        p(l + " " + l.getClass().getName() + " " + (l instanceof RandomAccess));
        l.set(0, "z");
        p(Arrays.toString(arr));
        arr[1] = "y";
        p(l);
        t("add", () -> l.add("q"));
        t("remove", () -> l.remove(0));
        t("clear", () -> {
            l.clear();
            return l;
        });
        t("iterator remove", () -> {
            Iterator<String> it = l.iterator();
            it.next();
            it.remove();
            return l;
        });
        t("get 5", () -> l.get(5));
        l.sort(null);
        p(l + " " + Arrays.toString(arr));
        l.replaceAll(String::toUpperCase);
        p(l + " " + l.indexOf("Y") + " " + l.indexOf(null) + " " + l.contains("B") + " " + l.hashCode());
        Object[] oa = l.toArray();
        p(oa.getClass().getSimpleName() + " " + l.toArray(new String[0]).getClass().getSimpleName() + " "
                + Arrays.toString(l.toArray(new String[5])));
        t("asList null", () -> Arrays.asList((Object[]) null));
        List<String> withNull = Arrays.asList("a", null, "b");
        p(withNull.indexOf(null) + " " + withNull.lastIndexOf(null) + " " + withNull.contains(null));
        StringBuilder sb = new StringBuilder();
        withNull.forEach(sb::append);
        p(sb + " " + withNull.equals(new ArrayList<String>(withNull)) + " " + withNull.subList(1, 3));
        Object[] deep = {new short[] {1, -2}, new float[] {0.5f}, new Object[] {null, "s"}, null, new char[] {'c'}};
        Object[] cyclic = {1, null};
        cyclic[1] = new Object[] {cyclic, deep};
        p(Arrays.deepToString(deep) + " " + Arrays.deepToString(cyclic) + " " + Arrays.deepToString(new Object[0]));
    }

    static void empties() {
        p("-- empty");
        t("emptyList", () -> Collections.emptyList() + " " + Collections.emptyList().size() + " "
                + Collections.emptyList().hashCode() + " " + Collections.emptyList().equals(new ArrayList<Object>())
                + " " + (Collections.emptyList() == Collections.EMPTY_LIST));
        t("emptyList get", true, () -> Collections.emptyList().get(0));
        t("emptyList add", () -> Collections.<String>emptyList().add("x"));
        t("emptyList clear", () -> {
            Collections.emptyList().clear();
            return "ok";
        });
        t("emptyList sort", () -> {
            Collections.<String>emptyList().sort(null);
            return "ok";
        });
        t("emptyList removeIf", () -> Collections.emptyList().removeIf(x -> true));
        t("emptyList iterator", () -> Collections.emptyList().iterator().getClass().getName());
        t("emptyList listIterator", () -> Collections.emptyList().listIterator().previousIndex());
        t("emptySet", () -> Collections.emptySet() + " " + Collections.emptySet().hashCode() + " "
                + Collections.emptySet().equals(new HashSet<Object>()) + " "
                + Collections.emptySet().getClass().getName());
        t("emptySet add", () -> Collections.<String>emptySet().add("x"));
        t("emptySet remove", () -> Collections.emptySet().remove("x"));
        t("emptySet toArray", () -> Arrays.toString(Collections.emptySet().toArray(new String[] {"a", "b"})));
        t("emptyMap", () -> Collections.emptyMap() + " " + Collections.emptyMap().hashCode() + " "
                + Collections.emptyMap().equals(new HashMap<Object, Object>()) + " "
                + Collections.emptyMap().getOrDefault("a", "d"));
        t("emptyMap put", () -> Collections.<String, String>emptyMap().put("a", "b"));
        t("emptyMap putIfAbsent", () -> Collections.<String, String>emptyMap().putIfAbsent("a", "b"));
        t("emptyMap remove", () -> Collections.emptyMap().remove("a"));
        t("emptyMap keySet", () -> Collections.emptyMap().keySet().getClass().getName());
        t("emptyMap replaceAll", () -> {
            Collections.<String, String>emptyMap().replaceAll((k, v) -> v);
            return "ok";
        });
        t("emptyIterator next", () -> Collections.emptyIterator().next());
        t("emptyIterator remove", () -> {
            Collections.emptyIterator().remove();
            return "ok";
        });
        t("emptyListIterator previous", () -> Collections.emptyListIterator().previous());
        t("emptyListIterator set", () -> {
            Collections.<String>emptyListIterator().set("x");
            return "ok";
        });
        t("emptyListIterator add", () -> {
            Collections.<String>emptyListIterator().add("x");
            return "ok";
        });
        t("emptyEnumeration", () -> Collections.emptyEnumeration().hasMoreElements() + " "
                + Collections.emptyEnumeration().getClass().getName());
        t("emptyEnumeration next", () -> Collections.emptyEnumeration().nextElement());
        t("emptySortedSet", () -> Collections.emptySortedSet() + " " + Collections.emptySortedSet().getClass().getName()
                + " " + (Collections.emptySortedSet() == Collections.emptyNavigableSet()));
        t("emptySortedSet first", () -> Collections.emptySortedSet().first());
        t("emptySortedSet add", () -> Collections.<String>emptySortedSet().add("x"));
        t("emptyNavigableSet pollFirst", () -> Collections.emptyNavigableSet().pollFirst());
        t("emptyNavigableSet headSet", () -> Collections.<String>emptyNavigableSet().headSet("m", true).getClass()
                .getName());
        t("emptySortedMap", () -> Collections.emptySortedMap() + " " + Collections.emptySortedMap().getClass().getName());
        t("emptyNavigableMap put", () -> Collections.<String, String>emptyNavigableMap().put("a", "b"));
        t("emptyNavigableMap firstKey", () -> Collections.emptyNavigableMap().firstKey());
        t("emptyNavigableMap keySet", () -> Collections.emptyNavigableMap().navigableKeySet().getClass().getName());
    }

    static void singletons() {
        p("-- singletons");
        Set<String> s = Collections.singleton("x");
        t("singleton", () -> s + " " + s.size() + " " + s.contains("x") + " " + s.hashCode() + " "
                + s.equals(new HashSet<String>(Arrays.asList("x"))) + " " + s.getClass().getName());
        t("singleton add", () -> s.add("y"));
        t("singleton remove", () -> s.remove("x"));
        t("singleton removeIf", () -> s.removeIf(x -> false));
        t("singleton iterator remove", () -> {
            Iterator<String> it = s.iterator();
            it.next();
            it.remove();
            return s;
        });
        t("singleton iterator past", () -> {
            Iterator<String> it = s.iterator();
            it.next();
            return it.next();
        });
        List<String> l = Collections.singletonList(null);
        t("singletonList", () -> l + " " + l.hashCode() + " " + l.contains(null) + " " + l.indexOf(null) + " "
                + l.getClass().getName() + " " + (l instanceof RandomAccess));
        t("singletonList get 1", true, () -> l.get(1));
        t("singletonList set", () -> l.set(0, "a"));
        t("singletonList add", () -> l.add("a"));
        t("singletonList sort", () -> {
            l.sort(null);
            return l;
        });
        t("singletonList replaceAll", () -> {
            l.replaceAll(x -> x);
            return l;
        });
        Map<String, Integer> m = Collections.singletonMap("k", 1);
        t("singletonMap", () -> m + " " + m.hashCode() + " " + m.get("k") + " " + m.get("j") + " "
                + m.getOrDefault("j", 7) + " " + m.keySet().getClass().getName() + " "
                + m.equals(Collections.singletonMap("k", 1)));
        t("singletonMap put", () -> m.put("k", 2));
        t("singletonMap entry setValue", () -> m.entrySet().iterator().next().setValue(3));
        t("singletonMap compute", () -> m.compute("k", (k, v) -> v));
        t("singletonMap clear", () -> {
            m.clear();
            return m;
        });
    }

    static void copies() {
        p("-- nCopies");
        List<String> c = Collections.nCopies(4, "ab");
        t("nCopies", () -> c + " " + c.hashCode() + " " + new ArrayList<String>(c).hashCode() + " "
                + c.equals(Arrays.asList("ab", "ab", "ab", "ab")) + " " + c.getClass().getName());
        t("nCopies index", () -> c.indexOf("ab") + " " + c.lastIndexOf("ab") + " " + c.indexOf("x") + " "
                + c.contains("ab"));
        t("nCopies subList", () -> c.subList(1, 3) + " " + c.subList(1, 3).getClass().getName());
        t("nCopies subList bad", true, () -> c.subList(3, 1));
        t("nCopies subList oob", true, () -> c.subList(0, 5));
        t("nCopies get", () -> c.get(4));
        t("nCopies set", () -> c.set(0, "x"));
        t("nCopies toArray", () -> Arrays.toString(c.toArray(new String[6])) + " " + c.toArray().length);
        t("nCopies negative", true, () -> Collections.nCopies(-1, "x"));
        t("nCopies hashes", () -> Collections.nCopies(0, "a").hashCode() + " " + Collections.nCopies(7, null).hashCode()
                + " " + Collections.nCopies(1000, 3).hashCode() + " " + Collections.nCopies(5, 2).equals(
                        Collections.nCopies(5, 2)) + " " + Collections.nCopies(0, 1).equals(Collections.nCopies(0, 2)));
    }

    static void unmodifiable() {
        p("-- unmodifiable");
        ArrayList<String> base = new ArrayList<String>(Arrays.asList("b", "a", "c"));
        List<String> ul = Collections.unmodifiableList(base);
        t("list", () -> ul + " " + ul.getClass().getName() + " " + ul.equals(base) + " " + base.equals(ul) + " "
                + (ul.hashCode() == base.hashCode()) + " " + (Collections.unmodifiableList(ul) == ul));
        t("list add", () -> ul.add("x"));
        t("list set", () -> ul.set(0, "x"));
        t("list remove", () -> ul.remove(0));
        t("list clear", () -> {
            ul.clear();
            return ul;
        });
        t("list sort", () -> {
            ul.sort(null);
            return ul;
        });
        t("list replaceAll", () -> {
            ul.replaceAll(x -> x);
            return ul;
        });
        t("list removeIf", () -> ul.removeIf(x -> false));
        t("list iterator remove", () -> {
            Iterator<String> it = ul.iterator();
            it.next();
            it.remove();
            return ul;
        });
        t("list listIterator set", () -> {
            ListIterator<String> it = ul.listIterator(1);
            p("  at " + it.nextIndex() + " " + it.previous());
            it.set("q");
            return ul;
        });
        t("list subList", () -> ul.subList(1, 3) + " " + ul.subList(1, 3).getClass().getName());
        t("list subList add", () -> ul.subList(1, 3).add("x"));
        base.add("d");
        t("list write-through", () -> ul + " " + ul.size() + " " + ul.indexOf("d"));
        LinkedList<String> linked = new LinkedList<String>(base);
        t("linked", () -> Collections.unmodifiableList(linked).getClass().getName());

        Collection<String> uc = Collections.unmodifiableCollection(base);
        t("collection", () -> uc + " " + uc.getClass().getName() + " " + uc.equals(base) + " "
                + uc.equals(uc) + " " + uc.contains("a") + " " + (Collections.unmodifiableCollection(uc) == uc));
        t("collection add", () -> uc.add("x"));
        t("collection addAll", () -> uc.addAll(Arrays.asList("x")));
        t("collection retainAll", () -> uc.retainAll(Arrays.asList("x")));

        HashSet<String> hs = new HashSet<String>(base);
        Set<String> us = Collections.unmodifiableSet(hs);
        t("set", () -> us + " " + us.equals(hs) + " " + (us.hashCode() == hs.hashCode()) + " "
                + (Collections.unmodifiableSet(us) == us));
        t("set add", () -> us.add("x"));
        t("set removeAll", () -> us.removeAll(hs));

        TreeSet<String> ts = new TreeSet<String>(base);
        SortedSet<String> uss = Collections.unmodifiableSortedSet(ts);
        t("sortedSet", () -> uss + " " + uss.getClass().getName() + " " + uss.first() + " " + uss.last() + " "
                + uss.headSet("c") + " " + uss.tailSet("c").getClass().getName() + " " + uss.comparator());
        t("sortedSet add", () -> uss.add("x"));
        t("sortedSet subSet add", () -> uss.subSet("a", "c").add("b"));
        NavigableSet<String> uns = Collections.unmodifiableNavigableSet(ts);
        t("navigableSet", () -> uns.lower("c") + " " + uns.floor("c") + " " + uns.ceiling("bb") + " "
                + uns.higher("c") + " " + uns.descendingSet() + " " + uns.headSet("c", true) + " "
                + uns.descendingSet().getClass().getName());
        t("navigableSet pollFirst", () -> uns.pollFirst());
        t("navigableSet descendingIterator remove", () -> {
            Iterator<String> it = uns.descendingIterator();
            p("  first " + it.next());
            it.remove();
            return uns;
        });
        ts.add("aa");
        t("navigableSet write-through", () -> uns + " " + uss);

        TreeMap<String, Integer> tm = new TreeMap<String, Integer>();
        for (String s : base) {
            tm.put(s, s.charAt(0) - 'a');
        }
        Map<String, Integer> um = Collections.unmodifiableMap(tm);
        t("map", () -> um + " " + um.getClass().getName() + " " + um.equals(tm) + " " + (um.hashCode() == tm.hashCode())
                + " " + um.get("b") + " " + um.getOrDefault("z", -1) + " " + (Collections.unmodifiableMap(um) == um));
        t("map put", () -> um.put("x", 1));
        t("map remove", () -> um.remove("a"));
        t("map putAll", () -> {
            um.putAll(tm);
            return um;
        });
        t("map putIfAbsent", () -> um.putIfAbsent("a", 1));
        t("map computeIfAbsent", () -> um.computeIfAbsent("zz", k -> 1));
        t("map merge", () -> um.merge("a", 1, Integer::sum));
        t("map replaceAll", () -> {
            um.replaceAll((k, v) -> v);
            return um;
        });
        t("map keySet remove", () -> um.keySet().remove("a"));
        t("map values remove", () -> um.values().remove(0));
        t("map entrySet", () -> um.entrySet() + " " + um.entrySet().getClass().getName() + " "
                + um.entrySet().equals(tm.entrySet()) + " " + tm.entrySet().equals(um.entrySet()) + " "
                + (um.entrySet().hashCode() == tm.entrySet().hashCode()));
        t("map entry setValue", () -> um.entrySet().iterator().next().setValue(9));
        t("map entry toArray setValue", () -> ((Map.Entry<String, Integer>) um.entrySet().toArray()[0]).setValue(9));
        t("map entry toArray typed", () -> {
            @SuppressWarnings("unchecked")
            Map.Entry<String, Integer>[] es = um.entrySet().toArray(new Map.Entry[0]);
            return es.length + " " + es[0] + " " + es[0].getClass().getName().contains("Unmodifiable");
        });
        t("map entry contains", () -> um.entrySet().contains(new AbstractMap.SimpleEntry<String, Integer>("a", 0))
                + " " + um.entrySet().contains("a") + " "
                + um.entrySet().containsAll(Collections.singleton(new AbstractMap.SimpleEntry<String, Integer>("b", 1))));
        t("map entrySet forEach setValue", () -> {
            um.entrySet().forEach(e -> e.setValue(0));
            return um;
        });
        t("map entrySet stream", () -> um.entrySet().stream().map(e -> e.getKey() + e.getValue())
                .reduce("", String::concat));
        t("map entrySet iterator remove", () -> {
            Iterator<Map.Entry<String, Integer>> it = um.entrySet().iterator();
            it.next();
            it.remove();
            return um;
        });
        tm.put("e", 4);
        t("map write-through", () -> um + " " + um.size() + " " + um.containsKey("e") + " " + um.containsValue(4));

        SortedMap<String, Integer> usm = Collections.unmodifiableSortedMap(tm);
        t("sortedMap", () -> usm.firstKey() + " " + usm.lastKey() + " " + usm.headMap("c") + " "
                + usm.subMap("b", "e").getClass().getName() + " " + usm.comparator());
        t("sortedMap tailMap put", () -> usm.tailMap("c").put("d", 9));
        NavigableMap<String, Integer> unm = Collections.unmodifiableNavigableMap(tm);
        t("navigableMap", () -> unm.lowerKey("c") + " " + unm.floorEntry("cc") + " " + unm.ceilingKey("cc") + " "
                + unm.higherEntry("c") + " " + unm.firstEntry() + " " + unm.lastEntry() + " " + unm.descendingMap()
                + " " + unm.navigableKeySet() + " " + unm.descendingKeySet() + " " + unm.headMap("c", true) + " "
                + unm.tailMap("c", false).getClass().getName() + " " + unm.lowerEntry("a"));
        t("navigableMap entry setValue", () -> unm.firstEntry().setValue(5));
        t("navigableMap pollFirstEntry", () -> unm.pollFirstEntry());
        t("navigableMap navigableKeySet pollLast", () -> unm.navigableKeySet().pollLast());
        t("navigableMap subMap remove", () -> unm.subMap("a", true, "c", true).remove("a"));
    }

    static void synchronizedWrappers() {
        p("-- synchronized");
        ArrayList<String> base = new ArrayList<String>(Arrays.asList("b", "a", "c"));
        List<String> sl = Collections.synchronizedList(base);
        sl.add("d");
        sl.add(0, "z");
        p(sl + " " + base + " " + sl.getClass().getName() + " " + sl.subList(1, 3).getClass().getName() + " "
                + sl.equals(base) + " " + (sl.hashCode() == base.hashCode()) + " " + sl.equals(sl));
        sl.sort(null);
        sl.replaceAll(String::toUpperCase);
        sl.removeIf(s -> s.equals("C"));
        for (Iterator<String> it = sl.iterator(); it.hasNext();) {
            if (it.next().equals("A")) {
                it.remove();
            }
        }
        p(sl + " " + sl.indexOf("D") + " " + sl.get(0) + " " + sl.set(0, "Q") + " " + sl.remove(0) + " " + base);
        sl.subList(0, 1).clear();
        p("subList clear " + sl);
        p(Collections.synchronizedList(new LinkedList<String>(base)).getClass().getName());

        Collection<String> sc = Collections.synchronizedCollection(new ArrayList<String>(Arrays.asList("x", "y")));
        sc.add("w");
        sc.remove("x");
        p(sc + " " + sc.getClass().getName() + " " + sc.contains("y") + " " + sc.size() + " " + sc.equals(sc) + " "
                + sc.equals(new ArrayList<String>(sc)) + " " + Arrays.toString(sc.toArray()));

        Set<String> ss = Collections.synchronizedSet(new TreeSet<String>(Arrays.asList("m", "n")));
        ss.add("o");
        p(ss + " " + ss.getClass().getName() + " " + ss.equals(new HashSet<String>(ss)) + " " + ss.hashCode());
        SortedSet<String> sss = Collections.synchronizedSortedSet(new TreeSet<String>(Arrays.asList("m", "n", "o")));
        sss.add("a");
        SortedSet<String> head = sss.headSet("o");
        p(sss + " " + head + " " + head.getClass().getName() + " " + sss.first() + " " + sss.last() + " "
                + sss.comparator());
        NavigableSet<String> sns = Collections.synchronizedNavigableSet(new TreeSet<String>(sss));
        p(sns.pollFirst() + " " + sns.lower("n") + " " + sns.ceiling("mm") + " " + sns.descendingSet() + " "
                + sns.subSet("m", "o").getClass().getName() + " " + sns.headSet("n") + " " + sns.tailSet("n") + " "
                + sns.headSet("n", true) + " " + sns.descendingIterator().next());

        Map<String, Integer> sm = Collections.synchronizedMap(new TreeMap<String, Integer>());
        sm.put("one", 1);
        sm.put("two", 2);
        sm.merge("one", 10, Integer::sum);
        sm.computeIfAbsent("three", k -> 3);
        sm.keySet().remove("two");
        p(sm + " " + sm.getClass().getName() + " " + sm.keySet().getClass().getName() + " "
                + sm.values().getClass().getName() + " " + sm.entrySet() + " " + sm.equals(sm) + " "
                + sm.getOrDefault("x", 0));
        SortedMap<String, Integer> ssm = Collections.synchronizedSortedMap(new TreeMap<String, Integer>(sm));
        p(ssm.firstKey() + " " + ssm.lastKey() + " " + ssm.headMap("three") + " "
                + ssm.tailMap("p").getClass().getName());
        NavigableMap<String, Integer> snm = Collections.synchronizedNavigableMap(new TreeMap<String, Integer>(sm));
        p(snm.firstEntry() + " " + snm.lowerKey("p") + " " + snm.descendingMap() + " "
                + snm.descendingMap().getClass().getName() + " " + snm.keySet().getClass().getName() + " "
                + snm.subMap("a", "p") + " " + snm.subMap("a", "p").getClass().getName() + " "
                + snm.navigableKeySet() + " " + snm.pollLastEntry() + " " + snm);
        t("null collection", () -> Collections.synchronizedList(null));
        t("null map", () -> Collections.synchronizedMap(null));
    }

    static void checked() {
        p("-- checked");
        List<Object> raw = new ArrayList<Object>(Arrays.asList("a", "b"));
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<Object> cl = (List) Collections.checkedList((List) raw, String.class);
        t("list add bad", true, () -> cl.add(1));
        t("list add null", () -> cl.add(null) + " " + cl);
        t("list set bad", true, () -> cl.set(0, 2.0));
        t("list add index bad", true, () -> {
            cl.add(0, 'c');
            return cl;
        });
        t("list addAll bad", true, () -> cl.addAll(Arrays.asList("ok", 3)));
        t("list addAll ok", () -> cl.addAll(Arrays.asList("ok", "fine")) + " " + cl);
        t("list addAll index", () -> cl.addAll(1, Arrays.asList("i")) + " " + cl);
        t("list listIterator set bad", true, () -> {
            ListIterator<Object> it = cl.listIterator();
            it.next();
            it.set(5);
            return cl;
        });
        t("list listIterator add bad", true, () -> {
            cl.listIterator().add(5L);
            return cl;
        });
        t("list replaceAll bad", true, () -> {
            cl.replaceAll(x -> 1);
            return cl;
        });
        t("list subList add bad", true, () -> cl.subList(0, 1).add(0));
        t("list class", () -> cl.getClass().getName() + " " + cl.subList(0, 1).getClass().getName() + " "
                + Collections.checkedList(new LinkedList<String>(), String.class).getClass().getName() + " "
                + cl.equals(raw) + " " + (cl.hashCode() == raw.hashCode()));
        t("list iterator remove", () -> {
            Iterator<Object> it = cl.iterator();
            it.next();
            it.remove();
            return cl + " " + raw;
        });

        @SuppressWarnings({"unchecked", "rawtypes"})
        Collection<Object> cc = (Collection) Collections.checkedCollection(new ArrayList<String>(), String.class);
        t("collection add bad", true, () -> cc.add(1));
        t("collection add", () -> cc.add("x") + " " + cc + " " + cc.getClass().getName() + " " + cc.equals(cc));

        @SuppressWarnings({"unchecked", "rawtypes"})
        Set<Object> cs = (Set) Collections.checkedSet(new HashSet<Integer>(), Integer.class);
        t("set add bad", true, () -> cs.add("s"));
        t("set add", () -> cs.add(1) + " " + cs + " " + cs.equals(Collections.singleton(1)));
        @SuppressWarnings({"unchecked", "rawtypes"})
        NavigableSet<Object> cns = (NavigableSet) Collections.checkedNavigableSet(new TreeSet<Integer>(Arrays.asList(
                1, 5, 9)), Integer.class);
        t("navigableSet", () -> cns.headSet(5) + " " + cns.headSet(5).getClass().getName() + " "
                + cns.descendingSet() + " " + cns.floor(6) + " " + cns.pollLast() + " " + cns);
        t("navigableSet headSet add bad", true, () -> cns.headSet(5).add("x"));
        t("navigableSet descending add bad", true, () -> cns.descendingSet().add(2.5));
        @SuppressWarnings({"unchecked", "rawtypes"})
        SortedSet<Object> css = (SortedSet) Collections.checkedSortedSet(new TreeSet<Integer>(), Integer.class);
        t("sortedSet add bad", true, () -> css.add("x"));
        @SuppressWarnings({"unchecked", "rawtypes"})
        Queue<Object> cq = (Queue) Collections.checkedQueue(new LinkedList<String>(), String.class);
        t("queue offer bad", true, () -> cq.offer(1));
        t("queue", () -> cq.offer("a") + " " + cq.add("b") + " " + cq.peek() + " " + cq.poll() + " " + cq + " "
                + cq.getClass().getName());

        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<Object, Object> cm = (Map) Collections.checkedMap(new HashMap<String, Integer>(), String.class,
                Integer.class);
        t("map put bad key", true, () -> cm.put(1, 1));
        t("map put bad value", true, () -> cm.put("a", "b"));
        t("map put", () -> cm.put("a", 1) + " " + cm.put("b", 2) + " " + cm);
        t("map putAll bad", true, () -> {
            Map<Object, Object> bad = new LinkedHashMap<Object, Object>();
            bad.put("c", 3);
            bad.put("d", "four");
            cm.putAll(bad);
            return cm;
        });
        t("map after putAll", () -> cm);
        t("map putIfAbsent bad", true, () -> cm.putIfAbsent("z", "z"));
        t("map compute bad", true, () -> cm.compute("a", (k, v) -> "s"));
        t("map computeIfAbsent bad", true, () -> cm.computeIfAbsent("q", k -> 1.5));
        t("map merge bad", true, () -> cm.merge("a", 1, (x, y) -> "m"));
        t("map merge", () -> cm.merge("a", 5, (x, y) -> (Integer) x + (Integer) y) + " " + cm);
        t("map replaceAll bad", true, () -> {
            cm.replaceAll((k, v) -> k);
            return cm;
        });
        t("map entry setValue bad", true, () -> {
            for (Map.Entry<Object, Object> e : cm.entrySet()) {
                e.setValue("bad");
            }
            return cm;
        });
        t("map entry toArray setValue bad", true, () -> ((Map.Entry<Object, Object>) cm.entrySet().toArray()[0])
                .setValue("bad"));
        t("map entry setValue", () -> {
            for (Map.Entry<Object, Object> e : cm.entrySet()) {
                e.setValue(100);
            }
            return cm;
        });
        t("map entrySet add", () -> cm.entrySet().add(new AbstractMap.SimpleEntry<Object, Object>("x", 1)));
        t("map entrySet", () -> cm.entrySet().contains(new AbstractMap.SimpleEntry<Object, Object>("a", 100)) + " "
                + cm.entrySet().remove(new AbstractMap.SimpleEntry<Object, Object>("a", 100)) + " " + cm + " "
                + cm.entrySet().equals(new HashMap<String, Integer>(Collections.singletonMap("b", 100)).entrySet())
                + " " + cm.getClass().getName());
        t("map entrySet retainAll", () -> cm.entrySet().retainAll(Collections.emptySet()) + " " + cm);
        @SuppressWarnings({"unchecked", "rawtypes"})
        NavigableMap<Object, Object> cnm = (NavigableMap) Collections.checkedNavigableMap(
                new TreeMap<String, Integer>(), String.class, Integer.class);
        cnm.put("a", 1);
        cnm.put("m", 2);
        cnm.put("z", 3);
        t("navigableMap", () -> cnm.firstEntry() + " " + cnm.ceilingEntry("b") + " " + cnm.headMap("m") + " "
                + cnm.headMap("m").getClass().getName() + " " + cnm.descendingMap() + " "
                + cnm.navigableKeySet().getClass().getName() + " " + cnm.keySet().getClass().getName());
        t("navigableMap firstEntry setValue bad", true, () -> cnm.firstEntry().setValue("x"));
        t("navigableMap headMap put bad", true, () -> cnm.headMap("m").put("b", "x"));
        t("navigableMap keySet add bad", true, () -> cnm.navigableKeySet().add(5));
        t("navigableMap pollFirstEntry", () -> cnm.pollFirstEntry() + " " + cnm);
        @SuppressWarnings({"unchecked", "rawtypes"})
        SortedMap<Object, Object> csm = (SortedMap) Collections.checkedSortedMap(new TreeMap<String, Integer>(),
                String.class, Integer.class);
        t("sortedMap put bad", true, () -> csm.put("a", "b"));
        t("sortedMap tailMap", () -> csm.tailMap("a").getClass().getName());
        t("checked null", () -> Collections.checkedList(new ArrayList<String>(), null));
    }

    static void misc() {
        p("-- misc");
        Comparator<String> rev = Collections.reverseOrder();
        List<String> l = new ArrayList<String>(Arrays.asList("pear", "fig", "apple", "kiwi"));
        l.sort(rev);
        p(l + " " + rev.getClass().getName() + " " + (rev.reversed() == Comparator.naturalOrder()) + " "
                + (Collections.<String>reverseOrder(null) == rev) + " " + (Comparator.<String>reverseOrder() == rev));
        Comparator<String> byLen = Comparator.comparing(String::length);
        Comparator<String> rl = Collections.reverseOrder(byLen);
        l.sort(rl);
        p(l + " " + rl.getClass().getName() + " " + (Collections.reverseOrder(rl) == byLen) + " "
                + (rl.reversed() == byLen) + " " + rl.equals(Collections.reverseOrder(byLen)) + " "
                + (rl.hashCode() == Collections.reverseOrder(byLen).hashCode()));
        p((Collections.reverseOrder(rev) == Comparator.naturalOrder()) + " "
                + (Collections.reverseOrder(Comparator.<String>naturalOrder()) == rev));

        Set<String> sfm = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>());
        p(sfm.add("b") + " " + sfm.add("a") + " " + sfm.add("b") + " " + sfm + " " + sfm.getClass().getName() + " "
                + sfm.remove("b") + " " + sfm.remove("q") + " " + sfm.contains("a") + " " + sfm.hashCode() + " "
                + sfm.equals(Collections.singleton("a")));
        Map<String, Boolean> nonEmpty = new HashMap<String, Boolean>();
        nonEmpty.put("x", true);
        t("newSetFromMap non-empty", true, () -> Collections.newSetFromMap(nonEmpty));

        Deque<Integer> dq = new ArrayDeque<Integer>();
        Queue<Integer> lifo = Collections.asLifoQueue(dq);
        lifo.add(1);
        lifo.offer(2);
        lifo.addAll(Arrays.asList(3, 4));
        p(lifo + " " + dq + " " + lifo.peek() + " " + lifo.poll() + " " + lifo.remove() + " " + lifo.element() + " "
                + lifo.size() + " " + lifo.getClass().getName());
        t("asLifoQueue null", () -> Collections.asLifoQueue(null));

        Enumeration<String> en = Collections.enumeration(Arrays.asList("e1", "e2"));
        p(enumStr(en) + " " + Collections.list(Collections.enumeration(Arrays.asList("x", "y"))));
        Iterator<String> asIt = Collections.enumeration(new ArrayList<String>(Arrays.asList("r"))).asIterator();
        asIt.next();
        t("enumeration asIterator remove", () -> {
            asIt.remove();
            return "removed";
        });
    }

    static void algorithms() {
        p("-- algorithms");
        List<Integer> al = new ArrayList<Integer>();
        LinkedList<Integer> ll = new LinkedList<Integer>();
        for (int i = 0; i < 150; i++) {
            al.add(i);
            ll.add(i);
        }
        Collections.shuffle(al, new Random(42));
        Collections.shuffle(ll, new Random(42));
        p("shuffle " + al.subList(0, 20) + " " + al.equals(ll));
        List<Integer> small = new LinkedList<Integer>(Arrays.asList(1, 2, 3, 4));
        Collections.shuffle(small, new Random(7));
        p("shuffle small " + small);
        Collections.sort(al);
        Collections.sort(ll, Collections.reverseOrder());
        p("sort " + al.subList(0, 5) + " " + ll.subList(0, 5));
        p("binarySearch " + Collections.binarySearch(al, 77) + " " + Collections.binarySearch(al, 200) + " "
                + Collections.binarySearch(ll, 77, Collections.reverseOrder()) + " "
                + Collections.binarySearch(ll, -3, Collections.reverseOrder()) + " "
                + Collections.binarySearch(al, 5, null));
        Collections.reverse(ll);
        p("reverse " + ll.subList(0, 5) + " " + ll.equals(al));
        Collections.rotate(al, 7);
        Collections.rotate(ll, -143);
        p("rotate " + al.subList(0, 10) + " " + ll.subList(0, 10) + " " + al.equals(ll));
        List<String> rs = new ArrayList<String>(Arrays.asList("a", "b", "c", "d", "e"));
        Collections.rotate(rs.subList(1, 4), -1);
        p("rotate sub " + rs);
        Collections.swap(rs, 0, 4);
        p("swap " + rs);
        List<String> filled = new LinkedList<String>(Collections.nCopies(30, "x"));
        Collections.fill(filled, "y");
        p("fill " + Collections.frequency(filled, "y") + " " + Collections.frequency(filled, null));
        List<String> dest = new ArrayList<String>(Arrays.asList("1", "2", "3", "4"));
        Collections.copy(dest, Arrays.asList("a", "b"));
        p("copy " + dest);
        t("copy too big", true, () -> {
            Collections.copy(new ArrayList<String>(), Arrays.asList("a"));
            return "ok";
        });
        p("min/max " + Collections.min(al) + " " + Collections.max(al) + " "
                + Collections.min(Arrays.asList("bb", "a", "ccc"), Comparator.comparing(String::length)) + " "
                + Collections.max(Arrays.asList("bb", "a", "ccc"), null));
        t("min empty", () -> Collections.min(new ArrayList<Integer>()));
        List<String> rep = new LinkedList<String>(Arrays.asList("a", null, "b", "a", null, "c", "a", "d", "e", "f",
                "g", "a"));
        p("replaceAll " + Collections.replaceAll(rep, "a", "A") + " " + Collections.replaceAll(rep, null, "N") + " "
                + Collections.replaceAll(rep, "zz", "q") + " " + rep);
        List<Integer> src = new LinkedList<Integer>();
        for (int i = 0; i < 60; i++) {
            src.add(i % 7);
        }
        p("indexOfSubList " + Collections.indexOfSubList(src, Arrays.asList(3, 4, 5)) + " "
                + Collections.lastIndexOfSubList(src, Arrays.asList(3, 4, 5)) + " "
                + Collections.indexOfSubList(src, Arrays.asList(6, 6)) + " "
                + Collections.lastIndexOfSubList(src, new ArrayList<Integer>()) + " "
                + Collections.indexOfSubList(new ArrayList<Integer>(src), Arrays.asList(5, 6, 0, 1)) + " "
                + Collections.lastIndexOfSubList(Arrays.asList(1, 2), Arrays.asList(1, 2, 3)));
        p("disjoint " + Collections.disjoint(Arrays.asList(1, 2), Arrays.asList(3, 4)) + " "
                + Collections.disjoint(new HashSet<Integer>(Arrays.asList(1, 2)), Arrays.asList(2)) + " "
                + Collections.disjoint(Arrays.asList(1), new ArrayList<Integer>()));
        List<String> add = new ArrayList<String>();
        p("addAll " + Collections.addAll(add, "x", "y") + " " + add + " "
                + Collections.addAll(new HashSet<String>(add), "x"));
        List<String> empty = new ArrayList<String>();
        Collections.reverse(empty);
        Collections.rotate(empty, 3);
        Collections.shuffle(empty);
        p("empty ops " + empty);
    }
}
