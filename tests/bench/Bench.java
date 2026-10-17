// javac-release: 17
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interpreter micro-benchmarks for WS12: each workload prints its time in ms and a checksum (so the work cannot be
 * skipped). Run with tests/bench/run_bench.sh; compare numbers before and after an interpreter change.
 */
public class Bench {
    interface Shape {
        int area();
    }

    static final class Sq implements Shape {
        final int s;

        Sq(int s) {
            this.s = s;
        }

        public int area() {
            return s * s;
        }
    }

    static final class Rect implements Shape {
        final int w, h;

        Rect(int w, int h) {
            this.w = w;
            this.h = h;
        }

        public int area() {
            return w * h;
        }
    }

    static final class Tri implements Shape {
        final int b, h;

        Tri(int b, int h) {
            this.b = b;
            this.h = h;
        }

        public int area() {
            return b * h / 2;
        }
    }

    static abstract class Animal {
        abstract int legs();
    }

    static final class Dog extends Animal {
        int legs() {
            return 4;
        }
    }

    static final class Bird extends Animal {
        int legs() {
            return 2;
        }
    }

    int counter;
    static int sCounter;

    static int add(int a, int b) {
        return a + b;
    }

    static long time(String name, long t0, long check) {
        long ms = (System.nanoTime() - t0) / 1000000;
        System.out.println(name + ": " + ms + " ms (check " + check + ")");
        return ms;
    }

    public static void main(String[] args) {
        int scale = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        long total = 0;

        long t0 = System.nanoTime();
        long acc = 0;
        for (int i = 0; i < 5000000 * scale; i++) acc += (i * i) % 7 ^ (i >> 3);
        total += time("arith", t0, acc);

        t0 = System.nanoTime();
        acc = 0;
        for (int i = 0; i < 2000000 * scale; i++) acc = add((int) acc, i) & 0xffffff;
        total += time("static calls", t0, acc);

        Animal[] animals = {new Dog(), new Bird()};
        t0 = System.nanoTime();
        acc = 0;
        for (int i = 0; i < 2000000 * scale; i++) acc += animals[i & 1].legs();
        total += time("virtual calls", t0, acc);

        Shape[] shapes = {new Sq(3), new Rect(2, 5), new Tri(4, 6)};
        t0 = System.nanoTime();
        acc = 0;
        for (int i = 0; i < 2000000 * scale; i++) acc += shapes[i % 3].area();
        total += time("interface calls", t0, acc);

        Bench b = new Bench();
        t0 = System.nanoTime();
        for (int i = 0; i < 3000000 * scale; i++) {
            b.counter += i & 3;
            sCounter += b.counter & 1;
        }
        total += time("fields", t0, b.counter + sCounter);

        int[] arr = new int[4096];
        t0 = System.nanoTime();
        acc = 0;
        for (int r = 0; r < 500 * scale; r++) {
            for (int i = 0; i < arr.length; i++) arr[i] = i * r;
            for (int i = 0; i < arr.length; i++) acc += arr[i];
        }
        total += time("arrays", t0, acc);

        t0 = System.nanoTime();
        acc = 0;
        for (int i = 0; i < 500000 * scale; i++) {
            Sq s = new Sq(i);
            acc += s.s & 7;
        }
        total += time("allocation", t0, acc);

        t0 = System.nanoTime();
        acc = 0;
        for (int i = 0; i < 20000 * scale; i++) {
            StringBuilder sb = new StringBuilder();
            sb.append("item").append(i).append(':').append(i * 2);
            acc += sb.toString().hashCode() & 0xff;
        }
        total += time("strings", t0, acc);

        t0 = System.nanoTime();
        Map<String, Integer> map = new HashMap<String, Integer>();
        for (int i = 0; i < 50000 * scale; i++) map.put("k" + (i % 5000), i);
        acc = 0;
        for (int i = 0; i < 50000 * scale; i++) acc += map.get("k" + (i % 5000));
        total += time("hashmap", t0, acc);

        t0 = System.nanoTime();
        List<Integer> list = new ArrayList<Integer>();
        for (int i = 0; i < 200000 * scale; i++) list.add(i);
        acc = 0;
        for (int v : list) acc += v;
        total += time("boxed list", t0, acc);

        System.out.println("total: " + total + " ms");
    }
}
