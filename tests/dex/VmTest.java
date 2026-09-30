import java.io.*;
import java.lang.reflect.*;
import java.math.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * VM conformance test. Its output is compared byte-for-byte against the
 * reference JVM, so everything printed must be deterministic.
 */
public class VmTest {
    static int failures;

    static void p(Object o) {
        System.out.println(o);
    }

    interface Shape {
        double area();

        default String describe() {
            return getClass().getSimpleName() + " area=" + String.format("%.2f", area());
        }
    }

    static abstract class Base implements Shape {
        static int instances;
        protected final String name;

        Base(String name) {
            this.name = name;
            instances++;
        }

        public String toString() {
            return name + ":" + describe();
        }
    }

    static class Circle extends Base {
        final double r;

        Circle(double r) {
            super("circle");
            this.r = r;
        }

        public double area() {
            return Math.PI * r * r;
        }
    }

    static class Rect extends Base {
        final double w, h;

        Rect(double w, double h) {
            super("rect");
            this.w = w;
            this.h = h;
        }

        public double area() {
            return w * h;
        }

        public String describe() {
            return "Rect " + w + "x" + h;
        }
    }

    enum Color {
        RED, GREEN, BLUE;

        Color next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    static class MyException extends Exception {
        final int code;

        MyException(String msg, int code) {
            super(msg);
            this.code = code;
        }
    }

    static int fib(int n) {
        return n < 2 ? n : fib(n - 1) + fib(n - 2);
    }

    static long factorial(int n) {
        long r = 1;
        for (int i = 2; i <= n; i++) r *= i;
        return r;
    }

    static void arithmetic() {
        p("== arithmetic");
        int a = 7, b = -3;
        p(a / b + " " + a % b + " " + (a >> 1) + " " + (b >> 1) + " " + (b >>> 28) + " " + (a << 30));
        p(Integer.MIN_VALUE / -1 + " " + Integer.MIN_VALUE % -1);
        long la = 123456789012345L, lb = -987654321L;
        p(la * lb + " " + la / lb + " " + la % lb + " " + (la >>> 7) + " " + (lb >> 3) + " " + (la ^ lb));
        p(Long.MIN_VALUE / -1 + " " + (Long.MAX_VALUE + 1));
        float f = 1.1f;
        double d = 2.2;
        p(f * 3 + " " + d / 3 + " " + (float) d + " " + (int) 3.99 + " " + (int) -3.99 + " " + (long) 1e19);
        p((int) Float.NaN + " " + (int) Float.POSITIVE_INFINITY + " " + (long) Double.NEGATIVE_INFINITY);
        p(5.0 % 3 + " " + -5.5f % 2 + " " + 0.1 + 0.2 + " " + (0.1 + 0.2) + " " + 1e-5 + " " + 1e21 + " " + 100.0f);
        p((byte) 200 + " " + (short) 70000 + " " + (char) 65 + " " + (int) 'z' + " " + (byte) -129);
        p(Double.compare(0.0, -0.0) + " " + (0.0 == -0.0) + " " + Double.isNaN(0.0 / 0.0) + " " + (Float.NaN < 1));
        p(Integer.toHexString(-1) + " " + Long.toBinaryString(10) + " " + Integer.parseInt("-123") + " "
                + Integer.valueOf(127).equals(127) + " " + Long.parseLong("7fffffffffffffff", 16));
        p(Math.round(2.5) + " " + Math.round(-2.5) + " " + Math.floor(-1.1) + " " + Math.ceil(1.1) + " " + Math.abs(-7)
                + " " + Math.max(3, 9) + " " + Math.sqrt(2) + " " + Math.pow(2, 10) + " " + Math.hypot(3, 4));
        p(Integer.bitCount(255) + " " + Integer.reverse(1) + " " + Long.numberOfTrailingZeros(64) + " "
                + Integer.highestOneBit(100) + " " + Character.isDigit('7') + Character.toUpperCase('q'));
        p(fib(20) + " " + factorial(20));
        p(Double.toString(1234567.0) + " " + 12345678.0 + " " + 0.001 + " " + 0.0001 + " " + Float.MIN_VALUE + " "
                + Double.MAX_VALUE + " " + (float) (1.0 / 3));
        p(Double.parseDouble("3.25e2") + " " + Float.parseFloat("  -0.5f ") + " " + Double.valueOf("1") + " "
                + Integer.decode("0x1F") + " " + Long.decode("-010"));
    }

    static void strings() {
        p("== strings");
        String s = "Hello, World";
        p(s.length() + " " + s.charAt(4) + " " + s.indexOf("World") + " " + s.lastIndexOf('o') + " " + s.substring(7)
                + " " + s.toUpperCase() + " " + s.toLowerCase() + " " + s.replace('l', 'L') + " " + s.contains("lo, "));
        p(String.join("|", "a", "b", "c") + " " + "  trim me ".trim() + "." + " x".isEmpty() + "".isEmpty());
        p(Arrays.toString("a,b,,c,,".split(",")) + " " + Arrays.toString("a1b22c333".split("\\d+")) + " "
                + Arrays.toString(" x  y ".split("\\s+")) + " " + Arrays.toString("a:b:c".split(":", 2)));
        p("abc".compareTo("abd") + " " + "B".compareToIgnoreCase("a") + " " + "Abc".equalsIgnoreCase("aBC") + " "
                + "abc".hashCode() + " " + "".hashCode() + " " + "hello".matches("h.*o"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i).append(',');
        sb.setLength(sb.length() - 1);
        sb.insert(0, "[").append(']').reverse();
        p(sb + " " + sb.indexOf(",") + " " + sb.charAt(1));
        p(String.format("%5d|%-5s|%05.1f|%x|%X|%o|%c|%b|%%|%,d|%+d|%e|%10.3e|%s", 42, "ab", 3.14159, 255, 255, 8, 'z',
                true, 1234567, 5, 12345.678, 0.000123, null));
        p(String.format("%s %S %.3s %08.3f %(d %2$s", "one", "two", "three", -1.5, -7));
        p(String.valueOf(new char[] {'x', 'y'}) + String.valueOf(3.0f) + String.valueOf(true) + 'c' + 1 + 2L);
        String u = "日本語 ünïcödé 😀";
        byte[] utf8 = u.getBytes(StandardCharsets.UTF_8);
        p(u.length() + " " + utf8.length + " " + new String(utf8, StandardCharsets.UTF_8).equals(u) + " "
                + u.codePointCount(0, u.length()) + " " + Integer.toHexString(u.codePointAt(u.length() - 2)));
        p("a-b-c".replace("-", "--") + " " + "aaa".replaceAll("a*", "X") + " " + "x1y2".replaceAll("(\\d)", "<$1>")
                + " " + "Tom and Jerry".replaceFirst("(\\w+) and (\\w+)", "$2 & $1"));
        p("interned=" + ("ab" + "c" == "abc") + " " + (new String("abc").intern() == "abc") + " "
                + "abc".repeat(2) + " " + " pad ".strip() + " " + String.valueOf((Object) null));
        char[] cs = "sort me".toCharArray();
        Arrays.sort(cs);
        p(new String(cs).trim() + " " + "x".compareTo("xyz") + " " + "%d%%".formatted(5));
    }

    static void objects() {
        p("== objects");
        List<Shape> shapes = new ArrayList<>();
        shapes.add(new Circle(1));
        shapes.add(new Rect(2, 3));
        shapes.add(new Circle(0.5));
        for (Shape sh : shapes) p(sh);
        p("instances=" + Base.instances);
        Object o = shapes.get(1);
        p((o instanceof Rect) + " " + (o instanceof Circle) + " " + (o instanceof Shape) + " " + o.getClass().getName());
        try {
            Circle c = (Circle) o;
            p("no exception " + c);
        } catch (ClassCastException e) {
            p("CCE ok");
        }
        Color c = Color.GREEN;
        p(c + " " + c.ordinal() + " " + c.next() + " " + Color.valueOf("BLUE").next() + " " + Arrays.toString(Color.values()));
        switch (c) {
            case RED: p("red"); break;
            case GREEN: p("green!"); break;
            default: p("other");
        }
        String key = "two";
        switch (key) {
            case "one": p(1); break;
            case "two": p(2); break;
            default: p(0);
        }
        int[][] grid = new int[3][4];
        grid[1][2] = 5;
        p(grid.length + " " + grid[0].length + " " + grid[1][2] + " " + Arrays.deepToString(grid));
        Object[] arr = new String[2];
        try {
            arr[0] = Integer.valueOf(1);
        } catch (ArrayStoreException e) {
            p("ASE ok");
        }
        int[] ia = {5, 3, 9, 1};
        int[] copy = ia.clone();
        Arrays.sort(copy);
        p(Arrays.toString(ia) + " " + Arrays.toString(copy) + " " + Arrays.binarySearch(copy, 5) + " " + (ia != copy));
        Integer x1 = 1000, x2 = 1000, y1 = 100, y2 = 100;
        p((x1.equals(x2)) + " " + (y1 == y2) + " " + x1.compareTo(999));
        Runnable r = () -> p("lambda ran");
        r.run();
        Function<Integer, Integer> sq = v -> v * v;
        BiFunction<Integer, Integer, Integer> add = Integer::sum;
        Supplier<List<String>> sup = ArrayList::new;
        p(sq.apply(7) + " " + add.apply(2, 3) + " " + sup.get().size() + " " + sq.andThen(v -> v + 1).apply(3));
        int[] counter = {0};
        Runnable inc = () -> counter[0]++;
        for (int i = 0; i < 3; i++) inc.run();
        p("counter=" + counter[0]);
        Object anon = new Object() {
            public String toString() {
                return "anon";
            }
        };
        p(anon + " " + anon.getClass().getSuperclass().getName() + " hashConsistent=" + (anon.hashCode() == anon.hashCode()));
    }

    static void exceptions() {
        p("== exceptions");
        try {
            int[] a = new int[2];
            a[5] = 1;
        } catch (ArrayIndexOutOfBoundsException e) {
            p("AIOOBE");
        }
        try {
            Object o = null;
            o.toString();
        } catch (NullPointerException e) {
            p("NPE");
        }
        try {
            int z = 0;
            p(10 / z);
        } catch (ArithmeticException e) {
            p("AE " + e.getMessage());
        }
        try {
            throw new MyException("custom", 42);
        } catch (MyException e) {
            p("caught " + e.getMessage() + " " + e.code + " " + (e instanceof Exception));
        }
        StringBuilder order = new StringBuilder();
        try {
            try {
                order.append("try,");
                throw new IllegalStateException("inner");
            } finally {
                order.append("finally,");
            }
        } catch (RuntimeException e) {
            order.append("catch:").append(e.getMessage());
        }
        p(order);
        p("finallyReturn=" + finallyReturn());
        try {
            recurse(0);
        } catch (StackOverflowError e) {
            p("SOE caught");
        }
        Throwable t = new RuntimeException("outer", new IOException("cause"));
        p(t.getCause().getMessage() + " " + t.toString());
        try {
            Integer.parseInt("12x");
        } catch (NumberFormatException e) {
            p(e.getMessage());
        }
        try (Resource res = new Resource("R1")) {
            p("using " + res.name);
            throw new RuntimeException("body");
        } catch (Exception e) {
            p("twr caught " + e.getMessage() + " suppressed=" + e.getSuppressed().length);
        }
        StackTraceElement[] st = new Throwable().getStackTrace();
        p("trace top=" + st[0].getMethodName() + " file=" + st[0].getFileName());
        try {
            Object s = "str";
            Integer bad = (Integer) s;
        } catch (ClassCastException e) {
            p("CCE msg ok=" + (e.getMessage() != null));
        }
        try {
            new ArrayList<Integer>().iterator().next();
        } catch (NoSuchElementException e) {
            p("NSEE");
        }
    }

    static class Resource implements AutoCloseable {
        final String name;

        Resource(String name) {
            this.name = name;
        }

        public void close() {
            p("closing " + name);
            throw new IllegalStateException("close failed");
        }
    }

    @SuppressWarnings("finally")
    static int finallyReturn() {
        try {
            return 1;
        } finally {
            return 2;
        }
    }

    static int recurse(int n) {
        return recurse(n + 1) + 1;
    }

    static void collections() {
        p("== collections");
        List<Integer> list = new ArrayList<>(Arrays.asList(5, 2, 8, 1, 9, 3));
        Collections.sort(list);
        p(list + " max=" + Collections.max(list) + " idx=" + list.indexOf(8) + " sub=" + list.subList(1, 3));
        list.removeIf(v -> v % 2 == 0);
        list.sort(Comparator.reverseOrder());
        p(list);
        Map<String, Integer> tm = new TreeMap<>();
        for (String w : "the quick brown fox jumps over the lazy dog the end".split(" ")) tm.merge(w, 1, Integer::sum);
        p(tm);
        p(((TreeMap<String, Integer>) tm).firstKey() + " " + ((TreeMap<String, Integer>) tm).headMap("fox") + " "
                + ((TreeMap<String, Integer>) tm).ceilingKey("m"));
        Map<Integer, String> hm = new HashMap<>();
        for (int i = 0; i < 100; i++) hm.put(i, "v" + i);
        hm.remove(50);
        int sum = 0;
        for (Map.Entry<Integer, String> e : hm.entrySet()) sum += e.getKey();
        p(hm.size() + " " + sum + " " + hm.get(99) + " " + hm.containsKey(50) + " " + hm.getOrDefault(50, "none"));
        LinkedHashMap<String, Integer> lru = new LinkedHashMap<String, Integer>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, Integer> e) {
                return size() > 3;
            }
        };
        lru.put("a", 1);
        lru.put("b", 2);
        lru.put("c", 3);
        lru.get("a");
        lru.put("d", 4);
        p(lru.keySet());
        Set<String> set = new TreeSet<>(Arrays.asList("pear", "apple", "fig", "apple"));
        p(set + " " + set.contains("fig"));
        Deque<Integer> dq = new ArrayDeque<>();
        dq.push(1);
        dq.addLast(2);
        dq.offerFirst(0);
        p(dq + " " + dq.pollLast() + " " + dq.peekFirst());
        PriorityQueue<Integer> pq = new PriorityQueue<>(Arrays.asList(7, 3, 9, 1));
        StringBuilder sb = new StringBuilder();
        while (!pq.isEmpty()) sb.append(pq.poll()).append(' ');
        p(sb.toString().trim());
        LinkedList<String> ll = new LinkedList<>(Arrays.asList("x", "y", "z"));
        ll.addFirst("w");
        ll.removeLast();
        ListIterator<String> it = ll.listIterator();
        while (it.hasNext()) if (it.next().equals("x")) it.set("X");
        p(ll);
        try {
            for (Integer v : list) if (v == 9) list.add(10);
        } catch (ConcurrentModificationException e) {
            p("CME");
        }
        EnumMap<Color, Integer> em = new EnumMap<>(Color.class);
        em.put(Color.BLUE, 3);
        em.put(Color.RED, 1);
        p(em + " " + EnumSet.of(Color.GREEN, Color.RED) + " " + EnumSet.complementOf(EnumSet.of(Color.RED)));
        List<String> unmod = Collections.unmodifiableList(new ArrayList<>(Arrays.asList("a")));
        try {
            unmod.add("b");
        } catch (UnsupportedOperationException e) {
            p("UOE");
        }
        BitSet bs = new BitSet();
        bs.set(3);
        bs.set(10, 13);
        p(bs + " " + bs.cardinality());
        Object[] objs = {"b", "a", "c"};
        Arrays.sort(objs);
        p(Arrays.asList(objs) + " " + Arrays.hashCode(new int[] {1, 2}) + " " + Objects.hash(1, "a") + " " + List.of(1, 2)
                + " " + Map.of("k", 1));
        Iterator<Integer> iter = new ArrayList<>(Arrays.asList(1, 2, 3)).iterator();
        iter.next();
        iter.remove();
        StringBuilder rest = new StringBuilder();
        iter.forEachRemaining(rest::append);
        p(rest);
    }

    static void streams() {
        p("== streams");
        List<String> words = Arrays.asList("delta", "alpha", "charlie", "bravo", "echo", "alpha");
        p(words.stream().filter(w -> w.length() == 5).map(String::toUpperCase).sorted().collect(Collectors.toList()));
        p(words.stream().distinct().count() + " " + words.stream().mapToInt(String::length).sum() + " "
                + IntStream.rangeClosed(1, 5).map(i -> i * i).boxed().collect(Collectors.toList()));
        p(words.stream().collect(Collectors.groupingBy(String::length, TreeMap::new, Collectors.counting())));
        p(words.stream().collect(Collectors.joining(", ", "[", "]")) + " "
                + words.stream().anyMatch(w -> w.startsWith("e")) + " " + words.stream().allMatch(w -> w.length() > 3));
        p(Stream.iterate(1, x -> x * 2).limit(10).reduce(0, Integer::sum) + " "
                + IntStream.of(3, 1, 2).sorted().boxed().map(String::valueOf).collect(Collectors.joining("")) + " "
                + words.stream().min(Comparator.naturalOrder()).get() + " "
                + Optional.ofNullable(null).map(Object::toString).orElse("empty"));
        Map<Boolean, List<Integer>> parts = IntStream.range(0, 10).boxed().collect(Collectors.partitioningBy(i -> i % 3 == 0));
        p(parts);
        p(Arrays.stream(new int[] {4, 8, 15}).average().getAsDouble() + " "
                + words.stream().collect(Collectors.toMap(w -> w, String::length, (a, b) -> a, TreeMap::new)));
    }

    static void regex() {
        p("== regex");
        Matcher m = Pattern.compile("(\\w+)@(\\w+)\\.com").matcher("mail alice@example.com and bob@test.com now");
        while (m.find()) p(m.group() + " user=" + m.group(1) + " host=" + m.group(2) + " at " + m.start());
        p(Pattern.matches("[a-f0-9]{4}-\\d+", "beef-123") + " " + Pattern.matches("^(ab|cd)*$", "abcdab") + " "
                + "AbC".matches("(?i)abc") + " " + Pattern.compile("^\\s*#").matcher("  # comment").find());
        Matcher dm = Pattern.compile("(?<year>\\d{4})-(?<mon>\\d\\d)").matcher("date: 2024-07!");
        p(dm.find() + " " + dm.group("year") + "/" + dm.group("mon"));
        p("a1b2c3".replaceAll("[0-9]", "#") + " " + Arrays.toString(Pattern.compile("[,;]\\s*").split("a, b;c ,d"))
                + " " + "aaa".replaceAll("a+?", "b") + " " + "<<x>>".replaceAll("<(.*?)>", "[$1]"));
        p("foo123bar".replaceAll("(?<=foo)\\d+(?=bar)", "-") + " " + "The Cat".replaceAll("\\bc", "X") + " "
                + "abcabc".matches("(abc)\\1") + " " + "x".matches("[^abc]") + " " + "tab\there".split("\\t").length);
    }

    static volatile int shared;

    static void threads() throws Exception {
        p("== threads");
        final Object lock = new Object();
        final int[] counter = {0};
        Thread[] ts = new Thread[4];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> {
                for (int k = 0; k < 10000; k++) {
                    synchronized (lock) {
                        counter[0]++;
                    }
                }
            }, "worker-" + i);
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        p("counter=" + counter[0]);
        final BlockingQueue<Integer> q = new LinkedBlockingQueue<>();
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= 5; i++) {
                try {
                    q.put(i);
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    return;
                }
            }
            q.add(-1);
        });
        producer.start();
        int total = 0;
        for (;;) {
            int v = q.take();
            if (v < 0) break;
            total += v;
        }
        p("consumed=" + total);
        ExecutorService ex = Executors.newFixedThreadPool(3);
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final int n = i;
            fs.add(ex.submit(() -> n * n));
        }
        int s = 0;
        for (Future<Integer> f : fs) s += f.get();
        ex.shutdown();
        p("futures=" + s + " terminated=" + ex.awaitTermination(5, TimeUnit.SECONDS));
        AtomicInteger ai = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                ai.addAndGet(5);
                latch.countDown();
            }).start();
        }
        latch.await();
        p("atomic=" + ai.get());
        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(10000);
                p("not interrupted?");
            } catch (InterruptedException e) {
                shared = 1;
            }
        });
        sleeper.start();
        Thread.sleep(20);
        sleeper.interrupt();
        sleeper.join();
        p("interrupted=" + shared);
        CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> "cf").thenApply(v -> v + "!");
        p(cf.get());
        ThreadLocal<Integer> tl = ThreadLocal.withInitial(() -> 7);
        tl.set(8);
        int[] other = new int[1];
        Thread tlt = new Thread(() -> other[0] = tl.get());
        tlt.start();
        tlt.join();
        p("threadlocal=" + tl.get() + "," + other[0]);
        ReentrantLockTest.run();
    }

    static class ReentrantLockTest {
        static void run() throws InterruptedException {
            final java.util.concurrent.locks.ReentrantLock l = new java.util.concurrent.locks.ReentrantLock();
            final java.util.concurrent.locks.Condition cond = l.newCondition();
            final boolean[] ready = {false};
            Thread t = new Thread(() -> {
                l.lock();
                try {
                    ready[0] = true;
                    cond.signalAll();
                } finally {
                    l.unlock();
                }
            });
            l.lock();
            try {
                t.start();
                while (!ready[0]) cond.await();
            } finally {
                l.unlock();
            }
            t.join();
            p("condition ok");
        }
    }

    public static class Bean {
        private int value = 3;
        public String label = "bean";

        public Bean() {
        }

        public Bean(int v) {
            value = v;
        }

        public int getValue() {
            return value;
        }

        public String greet(String who, int times) {
            return who.repeat(times);
        }

        public static long twice(long x) {
            return x * 2;
        }
    }

    interface Greeter {
        String greet(String name);

        int count();
    }

    static void reflection() throws Exception {
        p("== reflection");
        Class<?> c = Class.forName("VmTest$Bean");
        Object b = c.getConstructor(int.class).newInstance(42);
        Method m = c.getMethod("getValue");
        p(c.getName() + " " + c.getSimpleName() + " " + m.invoke(b) + " " + m.getReturnType());
        Method g = c.getMethod("greet", String.class, int.class);
        p(g.invoke(b, "ab", 3) + " " + c.getMethod("twice", long.class).invoke(null, 21L));
        Field f = c.getDeclaredField("value");
        f.setAccessible(true);
        f.setInt(b, 99);
        p(f.get(b) + " " + c.getField("label").get(b) + " " + Modifier.isPrivate(f.getModifiers()));
        List<String> names = new ArrayList<>();
        for (Method mm : c.getDeclaredMethods()) names.add(mm.getName());
        Collections.sort(names);
        p(names);
        try {
            c.getMethod("nope");
        } catch (NoSuchMethodException e) {
            p("NSME");
        }
        Greeter proxy = (Greeter) Proxy.newProxyInstance(VmTest.class.getClassLoader(), new Class<?>[] {Greeter.class},
                (px, meth, args) -> meth.getName().equals("greet") ? "hi " + args[0] : Integer.valueOf(5));
        p(proxy.greet("proxy") + " " + proxy.count());
        int[] arr = (int[]) Array.newInstance(int.class, 3);
        Array.setInt(arr, 1, 7);
        p(Array.getLength(arr) + " " + Array.get(arr, 1) + " " + int[].class.getName() + " " + String[][].class.getName()
                + " " + c.getSuperclass().getName() + " " + Color.class.isEnum() + " " + Runnable.class.isInterface());
        p(Integer.TYPE + " " + void.class + " " + new int[0].getClass().getComponentType());
    }

    static void io() throws Exception {
        p("== io");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeInt(0x12345678);
        dos.writeUTF("héllo");
        dos.writeDouble(Math.E);
        dos.writeLong(-2);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bos.toByteArray()));
        p(Integer.toHexString(dis.readInt()) + " " + dis.readUTF() + " " + dis.readDouble() + " " + dis.readLong() + " "
                + bos.size());
        File dir = new File(System.getProperty("java.io.tmpdir"), "vmtest-" + 12345);
        dir.mkdirs();
        File file = new File(dir, "data.txt");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("line one");
            pw.printf("line %d%n", 2);
            pw.print("last");
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
        }
        p(lines + " " + file.length() + " " + file.exists() + " " + file.getName() + " " + file.isFile());
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        raf.seek(5);
        raf.write('O');
        raf.seek(0);
        byte[] head = new byte[8];
        raf.readFully(head);
        raf.close();
        p(new String(head, "UTF-8"));
        p(file.delete() + " " + file.exists() + " " + dir.delete());
        StringWriter sw = new StringWriter();
        new PrintWriter(sw, true).println("sw");
        Scanner sc = null;
        p(sw.toString().trim() + " " + new BufferedReader(new StringReader("a\nb\r\nc")).lines().count());
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putInt(1).putFloat(2.5f).putShort((short) -1);
        bb.flip();
        p(bb.getInt() + " " + bb.getFloat() + " " + bb.getShort() + " " + bb.remaining());
        ByteBuffer le = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN);
        le.asIntBuffer().put(0x01020304);
        p(le.get(0) + " " + le.isDirect() + " " + le.order());
        FloatBuffer fb = ByteBuffer.allocate(12).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(new float[] {1, 2, 3}).flip();
        p(fb.get(2) + " " + fb.capacity());
        Properties props = new Properties();
        props.load(new StringReader("# c\nkey = value\na.b:c\\\n  d\nempty="));
        p(new TreeMap<>(props));
    }

    static void misc() {
        p("== misc");
        BigInteger big = BigInteger.valueOf(2).pow(100).subtract(BigInteger.ONE);
        p(big + " " + big.mod(BigInteger.valueOf(97)) + " " + big.bitLength() + " " + new BigInteger("-123456789012345678901234567890").abs()
                + " " + BigInteger.valueOf(-17).divide(BigInteger.valueOf(5)) + " " + new BigInteger("ff", 16));
        p(BigInteger.valueOf(123).multiply(BigInteger.valueOf(-456)) + " " + BigInteger.valueOf(3).modPow(BigInteger.valueOf(200), BigInteger.valueOf(1000007))
                + " " + BigInteger.valueOf(48).gcd(BigInteger.valueOf(180)) + " " + BigInteger.valueOf(97).isProbablePrime(10));
        BigDecimal bd = new BigDecimal("10.25").multiply(new BigDecimal("3"));
        p(bd + " " + bd.setScale(1, RoundingMode.HALF_UP) + " " + new BigDecimal("1").divide(new BigDecimal("8")) + " "
                + new BigDecimal("2").divide(new BigDecimal("3"), 5, RoundingMode.HALF_EVEN) + " " + new BigDecimal("1.50").stripTrailingZeros()
                + " " + new BigDecimal(0.1).toString().substring(0, 12) + " " + BigDecimal.valueOf(1e-10));
        DecimalFormat df = new DecimalFormat("#,##0.00");
        p(df.format(1234567.891) + " " + new DecimalFormat("0.###").format(3.14159) + " " + new DecimalFormat("00").format(7)
                + " " + NumberFormat.getPercentInstance().format(0.256) + " " + new DecimalFormat("#.#").format(-0.04));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS EEE MMM");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        p(sdf.format(new Date(1700000000123L)));
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(0);
        cal.add(Calendar.MONTH, 14);
        cal.add(Calendar.DAY_OF_MONTH, 45);
        p(cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH) + " dow=" + cal.get(Calendar.DAY_OF_WEEK));
        Random rnd = new Random(42);
        p(rnd.nextInt(100) + " " + rnd.nextInt(100) + " " + rnd.nextLong() + " " + rnd.nextDouble() + " " + rnd.nextBoolean() + " " + rnd.nextGaussian());
        p(UUID.nameUUIDFromBytes("x".getBytes()).version() + " " + UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        p(Base64.getEncoder().encodeToString("hello world!?".getBytes()) + " " + new String(Base64.getDecoder().decode("c3dpdGNoYXBr")));
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest("abc".getBytes());
            p(String.format("%02x%02x%02x%02x", dig[0], dig[1], dig[2], dig[3]) + " "
                    + String.format("%02x%02x", java.security.MessageDigest.getInstance("MD5").digest("abc".getBytes())[0], java.security.MessageDigest.getInstance("SHA-1").digest("abc".getBytes())[0]));
        } catch (Exception e) {
            p("digest failed " + e);
        }
        StringTokenizer st = new StringTokenizer("a b\tc");
        p(st.countTokens() + " " + st.nextToken() + " " + Objects.requireNonNullElse(null, "dflt") + " " + Objects.equals(null, null));
        long gcBefore = Runtime.getRuntime().totalMemory();
        List<int[]> garbage = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            garbage.add(new int[1000]);
            if (garbage.size() > 50) garbage.clear();
        }
        System.gc();
        p("gc survived " + (gcBefore > 0));
        java.lang.ref.WeakReference<Object> wr = new java.lang.ref.WeakReference<>(new Object());
        p("weakref type " + (wr.get() == null || wr.get() != null));
        char[] big2 = new char[100000];
        Arrays.fill(big2, 'z');
        p(new String(big2).length() + " " + String.valueOf(big2, 99990, 3));
        p(Long.MAX_VALUE + " " + Long.toString(-255, 16) + " " + Integer.toString(255, 2) + " " + Integer.parseUnsignedInt("4294967295") + " "
                + Integer.toUnsignedString(-1) + " " + Long.toUnsignedString(-1) + " " + Short.parseShort("-300") + " " + Byte.parseByte("7f", 16));
        p(Character.getNumericValue('z') + " " + Character.isLetter('é') + " " + Character.isWhitespace('\t') + " "
                + Character.toChars(0x1F600).length + " " + (int) Character.forDigit(11, 16));
    }

    public static void main(String[] args) throws Exception {
        arithmetic();
        strings();
        objects();
        exceptions();
        collections();
        streams();
        regex();
        threads();
        reflection();
        io();
        misc();
        p("== done");
    }
}
