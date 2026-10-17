import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

/**
 * Java serialization: the bytes written must match OpenJDK's exactly, and reading them back must give equal objects.
 * Test classes avoid touching each other's private members, which d8 would turn into synthetic accessors that
 * change the default serialVersionUID.
 */
public class SerialTest {
    static int failures;

    static void check(String what, boolean ok) {
        if (!ok) {
            failures++;
        }
        System.out.println((ok ? "ok   " : "FAIL ") + what);
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x & 0xFF));
        }
        return sb.toString();
    }

    static void dump(String name, byte[] b) {
        System.out.println(name + ": " + b.length + " bytes");
        if (b.length > 600) {
            CRC32 crc = new CRC32();
            crc.update(b);
            System.out.println("  crc " + Long.toHexString(crc.getValue()));
            return;
        }
        String h = hex(b);
        for (int i = 0; i < h.length(); i += 120) {
            System.out.println("  " + h.substring(i, Math.min(h.length(), i + 120)));
        }
    }

    static byte[] ser(Object... objs) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ObjectOutputStream oo = new ObjectOutputStream(bo);
        for (Object o : objs) {
            oo.writeObject(o);
        }
        oo.close();
        return bo.toByteArray();
    }

    static Object deser(byte[] b) throws Exception {
        ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(b));
        return oi.readObject();
    }

    /** Writes o, prints the bytes, reads it back and prints what came back. */
    static Object round(String name, Object o) throws Exception {
        byte[] b = ser(o);
        dump(name, b);
        Object back = deser(b);
        System.out.println("  back: " + show(back));
        return back;
    }

    static String show(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof Object[]) {
            return o.getClass().getSimpleName() + Arrays.deepToString((Object[]) o);
        }
        if (o instanceof int[]) {
            return Arrays.toString((int[]) o);
        }
        if (o instanceof byte[]) {
            return Arrays.toString((byte[]) o);
        }
        if (o instanceof char[]) {
            return Arrays.toString((char[]) o);
        }
        if (o instanceof boolean[]) {
            return Arrays.toString((boolean[]) o);
        }
        if (o instanceof long[]) {
            return Arrays.toString((long[]) o);
        }
        if (o instanceof double[]) {
            return Arrays.toString((double[]) o);
        }
        return o.getClass().getName() + ":" + o;
    }

    // ---- test classes ----

    static class Base {
        int baseVal;

        Base() {
            baseVal = 42;
        }
    }

    static class Pt extends Base implements Serializable {
        private static final long serialVersionUID = 1L;
        int x;
        long y;
        boolean flag;
        char c;
        double d;
        String label;
        Pt next;
        transient int skipped = 9;

        public String toString() {
            return "Pt(" + x + "," + y + "," + flag + "," + (int) c + "," + d + "," + label + ",next="
                    + (next == null ? "null" : next == this ? "self" : next.label) + ",skipped=" + skipped
                    + ",base=" + baseVal + ")";
        }
    }

    static class Pt3 extends Pt {
        private static final long serialVersionUID = 3L;
        float z;
        Object any;

        public String toString() {
            return "Pt3(" + super.toString() + "," + z + "," + (any instanceof Object[] ? "Object[" + ((Object[]) any).length + "]" : show(any)) + ")";
        }
    }

    static class Custom implements Serializable {
        private static final long serialVersionUID = 7L;
        int a;
        transient String extra;
        transient List<String> list;

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeUTF(extra);
            out.writeInt(list.size());
            for (String s : list) {
                out.writeObject(s);
            }
            out.write(new byte[300]);
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            extra = in.readUTF();
            int n = in.readInt();
            list = new ArrayList<String>();
            for (int i = 0; i < n; i++) {
                list.add((String) in.readObject());
            }
            byte[] pad = new byte[300];
            in.readFully(pad);
        }

        public String toString() {
            return "Custom(" + a + "," + extra + "," + list + ")";
        }
    }

    /** Writes less than its reader expects: the reader sees the end of the custom data. */
    static class Short1 implements Serializable {
        private static final long serialVersionUID = 1L;
        int a = 5;
        transient String note;

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeInt(77);
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            int v = in.readInt();
            int eof = in.read();
            try {
                in.readObject();
                note = "no OptionalDataException";
            } catch (OptionalDataException e) {
                note = v + " read=" + eof + " eof=" + e.eof + " length=" + e.length;
            }
        }

        public String toString() {
            return "Short1(" + a + "," + note + ")";
        }
    }

    static class Fields implements Serializable {
        private static final long serialVersionUID = 11L;
        private static final ObjectStreamField[] serialPersistentFields = {
            new ObjectStreamField("count", Integer.TYPE),
            new ObjectStreamField("name", String.class),
            new ObjectStreamField("ratio", Double.TYPE),
        };
        transient int n;
        transient String s;

        private void writeObject(ObjectOutputStream out) throws IOException {
            ObjectOutputStream.PutField pf = out.putFields();
            pf.put("count", n);
            pf.put("name", s);
            pf.put("ratio", 0.5);
            out.writeFields();
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            ObjectInputStream.GetField gf = in.readFields();
            n = gf.get("count", -1);
            s = (String) gf.get("name", "dflt");
            double r = gf.get("ratio", 0.0);
            s = s + "/" + r + "/" + gf.defaulted("count");
            try {
                gf.get("nope", 1);
                s += "/no exception";
            } catch (IllegalArgumentException e) {
                s += "/" + e.getMessage();
            }
        }

        public String toString() {
            return "Fields(" + n + "," + s + ")";
        }
    }

    public static class Ext implements Externalizable {
        int v;
        String s;
        boolean viaCtor;

        public Ext() {
            viaCtor = true;
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeInt(v);
            out.writeObject(s);
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            v = in.readInt();
            s = (String) in.readObject();
        }

        public String toString() {
            return "Ext(" + v + "," + s + "," + viaCtor + ")";
        }
    }

    static class Single implements Serializable {
        private static final long serialVersionUID = 2L;
        static final Single INSTANCE = new Single();

        Object readResolve() {
            return INSTANCE;
        }
    }

    static class Proxied implements Serializable {
        private static final long serialVersionUID = 3L;
        int value;

        Object writeReplace() {
            Holder h = new Holder();
            h.text = "value=" + value;
            return h;
        }
    }

    static class Holder implements Serializable {
        private static final long serialVersionUID = 4L;
        String text;

        Object readResolve() {
            return "resolved " + text;
        }
    }

    enum Color { RED, GREEN, BLUE }

    enum Op {
        PLUS { int apply(int a, int b) { return a + b; } },
        MINUS { int apply(int a, int b) { return a - b; } };

        abstract int apply(int a, int b);
    }

    static class NoSer {
        int a;
    }

    static class HasNoSer implements Serializable {
        private static final long serialVersionUID = 5L;
        Object field = new NoSer();
    }

    static class Validated implements Serializable, ObjectInputValidation {
        private static final long serialVersionUID = 6L;
        static final List<String> log = new ArrayList<String>();
        String name;
        int prio;
        Validated child;

        Validated(String name, int prio) {
            this.name = name;
            this.prio = prio;
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            in.registerValidation(this, prio);
            log.add("read " + name);
        }

        public void validateObject() {
            log.add("validate " + name);
        }
    }

    static class NoData implements Serializable {
        private static final long serialVersionUID = 8L;
        int a;
    }

    // Default serialVersionUID cases: no declared one.
    static class Plain implements Serializable {
        int a;
        String b;
    }

    public static final class PubFinal implements Serializable, Comparable<PubFinal>, Cloneable {
        public int x;
        protected transient int t;
        static int counter = 3;
        volatile long v;

        public PubFinal() {
        }

        PubFinal(int x) {
            this.x = x;
        }

        public int compareTo(PubFinal o) {
            return 0;
        }

        protected synchronized void m1(String s, int[] a) {
        }

        static void m2() {
        }

        private void m3() {
        }
    }

    abstract static class Abs implements Serializable {
        abstract void f();

        public Object[] g(List<?> l, long j) {
            return null;
        }
    }

    interface SerIface extends Serializable {
        void h();
    }

    interface EmptyIface extends Serializable {
    }

    static class Unknown implements Serializable {
        private static final long serialVersionUID = 1L;
        int a = 3;
    }

    static class Wrapper implements Serializable {
        private static final long serialVersionUID = 1L;
        String tag = "w";
        Object inner;
    }

    static class Replacing extends ObjectOutputStream {
        Replacing(OutputStream out) throws IOException {
            super(out);
            enableReplaceObject(true);
        }

        protected Object replaceObject(Object obj) {
            return (obj instanceof Integer) ? Integer.valueOf(((Integer) obj).intValue() * 10) : obj;
        }
    }

    static class Resolving extends ObjectInputStream {
        Resolving(InputStream in) throws IOException {
            super(in);
            enableResolveObject(true);
        }

        protected Object resolveObject(Object obj) {
            return (obj instanceof String) ? ((String) obj).toUpperCase() : obj;
        }
    }

    static byte[] replaceBytes(byte[] b, String from, String to) throws Exception {
        byte[] f = from.getBytes("UTF-8");
        byte[] t = to.getBytes("UTF-8");
        byte[] out = b.clone();
        for (int i = 0; i + f.length <= out.length; i++) {
            boolean m = true;
            for (int j = 0; j < f.length && m; j++) {
                m = out[i + j] == f[j];
            }
            if (m) {
                System.arraycopy(t, 0, out, i, t.length);
                return out;
            }
        }
        throw new IllegalArgumentException("not found: " + from);
    }

    public static void main(String[] args) throws Exception {
        // Primitive data at the top level, in blocks.
        {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutputStream oo = new ObjectOutputStream(bo);
            oo.writeInt(0x12345678);
            oo.writeUTF("héllo €\u0000");
            oo.writeBoolean(true);
            oo.writeDouble(Math.PI);
            oo.writeChars("ab");
            oo.writeBytes("cd");
            oo.writeObject("between");
            oo.writeShort(-2);
            oo.writeLong(Long.MIN_VALUE);
            oo.writeFloat(1.5f);
            oo.flush();
            oo.close();
            byte[] b = bo.toByteArray();
            dump("primitives", b);
            ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(b));
            System.out.println("  " + Integer.toHexString(oi.readInt()) + " " + oi.readUTF().replace("\u0000", "\\0") + " " + oi.readBoolean() + " "
                    + oi.readDouble() + " " + oi.readChar() + oi.readChar() + " " + (char) oi.readByte()
                    + (char) oi.readUnsignedByte());
            System.out.println("  available " + oi.available());
            System.out.println("  " + oi.readObject() + " " + oi.readShort() + " " + oi.readLong() + " " + oi.readFloat());
            System.out.println("  read at end " + oi.read());
        }
        {
            byte[] big = new byte[3000];
            for (int i = 0; i < big.length; i++) {
                big[i] = (byte) (i * 7);
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutputStream oo = new ObjectOutputStream(bo);
            oo.write(big);
            oo.writeInt(5);
            oo.write(big, 10, 1020);
            oo.writeObject(null);
            oo.write(1);
            oo.close();
            byte[] b = bo.toByteArray();
            dump("blocks", b);
            System.out.println("  first block header " + hex(Arrays.copyOfRange(b, 4, 9)));
            ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(b));
            byte[] got = new byte[3000];
            oi.readFully(got);
            int i5 = oi.readInt();
            byte[] got2 = new byte[1020];
            oi.readFully(got2);
            System.out.println("  " + Arrays.equals(got, big) + " " + i5 + " " + Arrays.equals(got2, Arrays.copyOfRange(big, 10, 1030))
                    + " " + oi.readObject() + " " + oi.read() + " " + oi.read());
        }

        // Strings and handles.
        String s = "shared";
        round("strings", new Object[] {s, s, "", "ÿĀ￿", null});
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 70000; i++) {
                sb.append((char) ('a' + i % 26));
            }
            sb.append('ࠀ');
            byte[] b = ser(sb.toString());
            dump("long string", b);
            System.out.println("  type " + Integer.toHexString(b[4] & 0xFF) + " back equal " + sb.toString().equals(deser(b)));
        }

        // Boxes, enums, classes.
        round("boxes", new Object[] {Integer.valueOf(-7), Long.valueOf(1L << 40), Double.valueOf(-0.0), Float.valueOf(2.5f),
                Boolean.TRUE, Character.valueOf('Z'), Byte.valueOf((byte) -1), Short.valueOf((short) 300)});
        round("enums", new Object[] {Color.GREEN, Color.GREEN, Color.BLUE, Op.MINUS, TimeUnit.SECONDS});
        System.out.println("  same constant " + (((Object[]) deser(ser((Object) new Object[] {Color.RED})))[0] == Color.RED));
        round("op apply", Op.PLUS);
        round("classes", new Object[] {String.class, Pt.class, int.class, Color.class, Op.MINUS.getClass(), int[].class,
                Object.class});

        // Arrays.
        round("int[]", new int[] {1, -2, Integer.MAX_VALUE});
        round("byte[]", new byte[] {1, 2, 3, -128});
        round("char[]", new char[] {'x', 'ሴ'});
        round("boolean[]", new boolean[] {true, false});
        round("long[]", new long[] {Long.MAX_VALUE, 0});
        round("double[]", new double[] {1.0 / 3, Double.NaN});
        round("float/short", new Object[] {new float[] {1f, -0f}, new short[] {-1, 2}});
        round("String[][]", new String[][] {{"a", "b"}, {}, null});
        round("Object[] mixed", new Object[] {1, "two", new int[] {3}, Color.RED, null});

        // Plain objects with a non-serializable superclass, cycles, inheritance.
        Pt p = new Pt();
        p.x = 1;
        p.y = -2;
        p.flag = true;
        p.c = 'q';
        p.d = 2.25;
        p.label = "first";
        p.next = p;
        p.baseVal = 1000;
        p.skipped = 1;
        round("Pt self", p);
        Pt3 p3 = new Pt3();
        p3.x = 3;
        p3.label = "three";
        p3.z = 0.75f;
        p3.any = new Object[] {p3, "s"};
        p3.next = p;
        Pt3 b3 = (Pt3) round("Pt3", p3);
        System.out.println("  cycle " + (((Object[]) b3.any)[0] == b3));
        Pt[] twice = (Pt[]) round("shared Pt", new Pt[] {p, p});
        System.out.println("  same " + (twice[0] == twice[1]));

        // Custom serialization.
        Custom c = new Custom();
        c.a = 12;
        c.extra = "extra é";
        c.list = Arrays.asList("x", "y", "x");
        round("Custom", c);
        round("Short1", new Short1());
        Fields f = new Fields();
        f.n = 99;
        f.s = "named";
        round("Fields", f);
        Ext e = new Ext();
        e.v = 31;
        e.s = "ext";
        round("Ext", e);
        System.out.println("  single " + (deser(ser(new Single())) == Single.INSTANCE));
        Proxied px = new Proxied();
        px.value = 8;
        round("writeReplace", px);
        System.out.println("NoData back " + deser(ser(new NoData())).getClass().getName() + ", " + ser(new NoData()).length + " bytes");

        // Default serialVersionUIDs.
        Class<?>[] suidClasses = {Plain.class, PubFinal.class, Abs.class, SerIface.class, EmptyIface.class, Color.class,
                Op.MINUS.getClass(), Pt.class, Ext.class, int[].class, String[].class, Pt[].class, Validated.class,
                HasNoSer.class};
        for (Class<?> k : suidClasses) {
            ObjectStreamClass d = ObjectStreamClass.lookup(k);
            System.out.println("suid " + k.getName() + " = " + (d == null ? "not serializable" : d.getSerialVersionUID()));
        }
        System.out.println("lookup non-serializable " + ObjectStreamClass.lookup(NoSer.class) + " any "
                + ObjectStreamClass.lookupAny(NoSer.class));
        ObjectStreamClass pd = ObjectStreamClass.lookup(Pt.class);
        System.out.println("Pt fields " + Arrays.toString(pd.getFields()) + " label " + pd.getField("label") + " "
                + pd.getField("label").getType() + " offset x " + pd.getField("x").getOffset() + " forClass "
                + (pd.forClass() == Pt.class));
        System.out.println("Fields fields " + Arrays.toString(ObjectStreamClass.lookup(Fields.class).getFields()));
        System.out.println("desc " + pd);

        // JDK classes.
        ArrayList<Object> al = new ArrayList<Object>(Arrays.asList("a", 1, null, 2.5));
        round("ArrayList", al);
        LinkedList<String> ll = new LinkedList<String>(Arrays.asList("l1", "l2"));
        round("LinkedList", ll);
        HashMap<String, Integer> hm = new HashMap<String, Integer>();
        for (int i = 0; i < 20; i++) {
            hm.put("k" + i, i);
        }
        round("HashMap", hm);
        LinkedHashMap<String, String> lhm = new LinkedHashMap<String, String>();
        lhm.put("z", "1");
        lhm.put("a", "2");
        round("LinkedHashMap", lhm);
        round("HashSet", new HashSet<String>(Arrays.asList("p", "q", "r")));
        round("LinkedHashSet", new LinkedHashSet<String>(Arrays.asList("p", "q", "r")));
        TreeMap<String, Integer> tm = new TreeMap<String, Integer>();
        tm.put("b", 2);
        tm.put("a", 1);
        round("TreeMap", tm);
        round("TreeSet", new TreeSet<Integer>(Arrays.asList(5, 1, 3)));
        round("ArrayDeque", new ArrayDeque<String>(Arrays.asList("d1", "d2")));
        round("Vector", new Vector<String>(Arrays.asList("v")));
        Hashtable<String, String> ht = new Hashtable<String, String>();
        ht.put("h", "t");
        round("Hashtable", ht);
        round("Arrays.asList", Arrays.asList("x", "y"));
        round("emptyList", Collections.emptyList());
        round("emptyMap", Collections.emptyMap());
        round("singletonList", Collections.singletonList("one"));
        round("unmodifiableList", Collections.unmodifiableList(new ArrayList<String>(Arrays.asList("u"))));
        round("Date", new Date(1234567890123L));
        round("BigInteger", new BigInteger("-123456789012345678901234567890"));
        round("BigDecimal", new BigDecimal("3.14159"));
        round("UUID", new UUID(0x0123456789abcdefL, 0xfedcba9876543210L));
        round("AtomicInteger", new AtomicInteger(17));
        round("AtomicLong", new AtomicLong(-17));
        ConcurrentHashMap<String, Integer> chm = new ConcurrentHashMap<String, Integer>();
        chm.put("c", 3);
        round("ConcurrentHashMap", chm);
        round("Locale", Locale.CANADA_FRENCH);
        round("StringBuilder", new StringBuilder("sb"));
        round("StringBuffer", new StringBuffer("sbf"));
        IOException ioe = new IOException("outer", new IllegalStateException("inner"));
        ioe.setStackTrace(new StackTraceElement[0]);
        ioe.getCause().setStackTrace(new StackTraceElement[0]);
        Throwable te = (Throwable) round("exception", ioe);
        System.out.println("  cause " + te.getCause() + " trace " + Arrays.toString(te.getCause().getStackTrace()));

        // Errors.
        try {
            ser(new NoSer());
            System.out.println("no exception");
        } catch (NotSerializableException ex) {
            System.out.println("NotSerializableException " + ex.getMessage());
        }
        {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutputStream oo = new ObjectOutputStream(bo);
            try {
                oo.writeObject(new HasNoSer());
            } catch (NotSerializableException ex) {
                System.out.println("nested " + ex.getMessage());
            }
            oo.close();
            byte[] b = bo.toByteArray();
            System.out.println("aborted stream starts " + hex(Arrays.copyOfRange(b, 0, 64)));
            try {
                deser(b);
            } catch (WriteAbortedException ex) {
                System.out.println("WriteAbortedException " + ex.getMessage() + " detail " + ex.detail.getClass().getName());
            }
        }
        {
            byte[] b = ser(new NoData());
            byte[] bad = b.clone();
            bad[bad.length - 5] ^= 1;
            int at = -1;
            for (int i = 0; i < b.length - 8; i++) {
                if (b[i] == 0 && b[i + 1] == 0 && b[i + 2] == 0 && b[i + 3] == 0 && b[i + 4] == 0 && b[i + 5] == 0
                        && b[i + 6] == 0 && b[i + 7] == 8) {
                    at = i;
                }
            }
            bad = b.clone();
            bad[at + 7] = 9;
            try {
                deser(bad);
            } catch (InvalidClassException ex) {
                System.out.println("InvalidClassException " + ex.getMessage() + " classname " + ex.classname);
            }
            try {
                new ObjectInputStream(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));
            } catch (StreamCorruptedException ex) {
                System.out.println("StreamCorruptedException " + ex.getMessage());
            }
            try {
                deser(Arrays.copyOf(b, b.length - 3));
            } catch (EOFException ex) {
                System.out.println("EOFException on truncated stream");
            }
        }
        {
            // An unknown class inside a known one: the read goes on, then reports the missing class.
            Wrapper w = new Wrapper();
            w.inner = new Unknown();
            byte[] b = replaceBytes(ser(w, "after"), "SerialTest$Unknown", "SerialTest$Unknowx");
            ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(b));
            try {
                oi.readObject();
                System.out.println("no ClassNotFoundException");
            } catch (ClassNotFoundException ex) {
                System.out.println("ClassNotFoundException " + ex.getMessage());
            }
            System.out.println("  next object " + oi.readObject());
        }
        {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutputStream oo = new ObjectOutputStream(bo);
            oo.writeInt(3);
            oo.close();
            ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(bo.toByteArray()));
            try {
                oi.readObject();
            } catch (OptionalDataException ex) {
                System.out.println("OptionalDataException length " + ex.length + " eof " + ex.eof);
            }
            System.out.println("  then readInt " + oi.readInt());
        }
        try {
            new ObjectOutputStream(new ByteArrayOutputStream()).defaultWriteObject();
        } catch (NotActiveException ex) {
            System.out.println("NotActiveException " + ex.getMessage());
        }
        try {
            new ObjectInputStream(new ByteArrayInputStream(ser())).defaultReadObject();
        } catch (NotActiveException ex) {
            System.out.println("NotActiveException " + ex.getMessage());
        }

        // reset, unshared, protocol 1.
        {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutputStream oo = new ObjectOutputStream(bo);
            Pt q = new Pt();
            q.label = "q";
            oo.writeObject(q);
            oo.writeObject(q);
            oo.reset();
            oo.writeObject(q);
            oo.writeUnshared(q);
            oo.writeObject(q);
            oo.close();
            byte[] b = bo.toByteArray();
            dump("reset/unshared", b);
            ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(b));
            Object a1 = oi.readObject();
            Object a2 = oi.readObject();
            Object a3 = oi.readObject();
            Object a4 = oi.readUnshared();
            Object a5 = oi.readObject();
            System.out.println("  " + (a1 == a2) + " " + (a2 == a3) + " " + (a3 == a4) + " " + (a3 == a5));
        }
        {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutputStream oo = new ObjectOutputStream(bo);
            oo.useProtocolVersion(ObjectStreamConstants.PROTOCOL_VERSION_1);
            oo.writeObject(e);
            oo.close();
            dump("protocol 1", bo.toByteArray());
            System.out.println("  back " + deser(bo.toByteArray()));
            try {
                oo = new ObjectOutputStream(new ByteArrayOutputStream());
                oo.writeObject("x");
                oo.useProtocolVersion(1);
            } catch (IllegalStateException ex) {
                System.out.println("useProtocolVersion " + ex.getMessage());
            }
        }

        // Validation order, replaceObject/resolveObject.
        {
            Validated v1 = new Validated("outer", 1);
            v1.child = new Validated("inner", 5);
            v1.child.child = new Validated("leaf", 1);
            deser(ser(v1));
            System.out.println("validation " + Validated.log);
        }
        {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutputStream oo = new Replacing(bo);
            oo.writeObject(new Object[] {1, 2, "three"});
            oo.close();
            dump("replaceObject", bo.toByteArray());
            Object back = new Resolving(new ByteArrayInputStream(bo.toByteArray())).readObject();
            System.out.println("  resolved " + show(back));
        }

        System.out.println(failures == 0 ? "done" : "FAILURES " + failures);
    }
}
