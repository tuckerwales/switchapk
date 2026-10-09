// javac-release: 21
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * java.lang additions from the WS16 gap sweep: run on OpenJDK and on switchapk by tests/run_dex_test.sh, output
 * must match.
 */
public class LangTest {
    static void p(Object o) {
        System.out.println(o);
    }

    static String name(Throwable t) {
        return t.getClass().getName();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(Tags.class)
    @interface Tag {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Tags {
        Tag[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @interface Marker {
    }

    @Tag("a")
    @Tag("b")
    @Marker
    static class Base {
    }

    @Tag("only")
    static class Child extends Base {
        Child() {
        }

        @Deprecated
        void m(int x, String y) {
        }
    }

    interface Shape {
    }

    static Object local;
    static Object ctorLocal;

    LangTest() {
        class InCtor {
        }
        ctorLocal = new InCtor();
    }

    public static void main(String[] args) throws Exception {
        math();
        strings();
        builders();
        characters();
        numbers();
        reflection();
        exceptions();
        blocks();
        p("done");
    }

    static void math() {
        p("-- math");
        p(Math.floorDiv(-7L, 2) + " " + Math.floorMod(-7L, 2) + " " + Math.ceilDiv(7, 2) + " " + Math.ceilDiv(-7, 2)
                + " " + Math.ceilMod(7, 2) + " " + Math.ceilMod(-7, 2) + " " + Math.ceilDiv(7L, 2L) + " "
                + Math.ceilMod(7L, 3) + " " + Math.ceilDiv(-7L, 3));
        p(Math.multiplyHigh(Long.MAX_VALUE, Long.MAX_VALUE) + " " + Math.multiplyHigh(-5L, 3L) + " "
                + Math.unsignedMultiplyHigh(-1L, -1L) + " " + Math.multiplyFull(Integer.MAX_VALUE, -2) + " "
                + Math.multiplyExact(1L << 40, 3));
        p(Math.incrementExact(5L) + " " + Math.decrementExact(5L) + " " + Math.negateExact(5L) + " "
                + Math.absExact(-9) + " " + Math.absExact(-9L) + " " + Math.divideExact(9, 2) + " "
                + Math.floorDivExact(-9, 2) + " " + Math.ceilDivExact(9L, 2L));
        String[] names = {"incrementExact", "decrementExact", "negateExact", "absExact", "divideExact",
            "floorDivExact", "ceilDivExact"};
        Runnable[] overflow = {
            () -> Math.incrementExact(Long.MAX_VALUE),
            () -> Math.decrementExact(Long.MIN_VALUE),
            () -> Math.negateExact(Long.MIN_VALUE),
            () -> Math.absExact(Integer.MIN_VALUE),
            () -> Math.divideExact(Integer.MIN_VALUE, -1),
            () -> Math.floorDivExact(Long.MIN_VALUE, -1L),
            () -> Math.ceilDivExact(Integer.MIN_VALUE, -1),
        };
        for (int i = 0; i < overflow.length; i++) {
            try {
                overflow[i].run();
                p(names[i] + " no overflow?");
            } catch (ArithmeticException e) {
                // d8 backports some of these (absExact) with other messages, so print the type only
                p(names[i] + " " + name(e));
            }
        }
        p(Math.clamp(15L, 1, 10) + " " + Math.clamp(-3L, -2L, 2L) + " " + Math.clamp(0.5, 1.0, 2.0) + " "
                + Math.clamp(3f, 1f, 2f) + " " + StrictMath.clamp(7L, 1, 9));
        try {
            Math.clamp(1, 5, 2);
        } catch (IllegalArgumentException e) {
            p("clamp " + e.getMessage());
        }
        p(Math.nextDown(1.0) + " " + Math.nextDown(0.0f) + " " + Math.nextDown(-0.0) + " "
                + Math.nextAfter(1.0f, 2.0) + " " + Math.nextAfter(1.0f, 0.0) + " " + Math.TAU + " "
                + StrictMath.nextDown(Double.POSITIVE_INFINITY));
        p(Double.PRECISION + " " + Float.PRECISION + " " + Byte.compareUnsigned((byte) -1, (byte) 1) + " "
                + Short.compareUnsigned((short) 1, (short) -1));
    }

    static void strings() {
        p("-- strings");
        String s = "a😀b";
        p(Arrays.toString(s.codePoints().toArray()) + " " + Arrays.toString("xy".codePoints().toArray()));
        p("[" + "one\ntwo\r\nthree\rfour\n".indent(2).replace("\n", "|") + "]");
        p("[" + "    one\n  two\n".indent(-3).replace("\n", "|") + "]");
        p("[" + "".indent(4) + "]" + "[" + "x".indent(0).replace("\n", "|") + "]");
        p("[" + "   a\n     b\n   c".stripIndent().replace("\n", "|") + "]");
        p("[" + "   a\n     b  \n   ".stripIndent().replace("\n", "|") + "]");
        p("[" + "  a\n    b\n".stripIndent().replace("\n", "|") + "]");
        p("[" + "\\tx\\n\\101\\u0041\\s\\\\".replace("\\u0041", "").translateEscapes() + "]");
        try {
            "\\q".translateEscapes();
        } catch (IllegalArgumentException e) {
            p(e.getMessage());
        }
        p("abc".transform(String::length));
        p(Arrays.toString("a\nb\r\nc\rd\n".lines().toArray()) + " " + "\n\nx".lines().count() + " "
                + "".lines().count());
        byte[] ascii = {65, 66, 67, 68};
        @SuppressWarnings("deprecation")
        String hb = new String(ascii, 0, 1, 2);
        p(hb);
        CharSequence cs = new StringBuilder("q😀");
        p(cs.codePoints().count());
    }

    static void builders() {
        p("-- builders");
        StringBuilder sb = new StringBuilder("a😀bc");
        p(sb.codePointBefore(3) + " " + sb.codePointCount(0, sb.length()) + " " + sb.offsetByCodePoints(0, 2) + " "
                + sb.codePoints().count());
        sb.insert(1, "XYZ", 1, 3);
        p(sb);
        StringBuffer sf = new StringBuffer("hello");
        sf.insert(0, (CharSequence) "--abc--", 2, 5);
        p(sf + " " + sf.codePointCount(1, 4) + " " + sf.codePointBefore(1));
        try {
            sb.insert(0, "abc", 2, 1);
        } catch (IndexOutOfBoundsException e) {
            p("insert range " + name(e));
        }
    }

    @SuppressWarnings("deprecation")
    static void characters() {
        p("-- characters");
        char[] a = "x😀y".toCharArray();
        p(Character.codePointCount(a, 0, a.length) + " " + Character.codePointCount("x😀y", 0, 4) + " "
                + Character.offsetByCodePoints("x😀y", 0, 2) + " "
                + Character.offsetByCodePoints(a, 0, a.length, 4, -2) + " " + Character.codePointBefore(a, 3, 1)
                + " " + Character.codePointBefore(a, 2, 1));
        p(Character.isSpace(' ') + " " + Character.isSpace(' ') + " " + Character.isJavaLetter('x') + " "
                + Character.isJavaLetterOrDigit('5') + " " + Character.isSpaceChar(0x2003) + " "
                + Character.isTitleCase(0x1c5) + " " + Character.isMirrored((int) '(') + " "
                + Character.isIdentifierIgnorable(0) + " " + Character.isUnicodeIdentifierStart((int) 'q') + " "
                + Character.isUnicodeIdentifierPart((int) '_'));
        try {
            Character.getName(-5);
        } catch (IllegalArgumentException e) {
            p("getName " + name(e));
        }
    }

    static void numbers() {
        p("-- numbers");
        p(Integer.parseInt("xx123yy", 2, 5, 10) + " " + Integer.parseUnsignedInt("ffffffff", 0, 8, 16) + " "
                + Long.parseLong("--77", 2, 4, 8) + " " + Long.parseUnsignedLong("18446744073709551615", 0, 20, 10));
        p(Long.toUnsignedString(-1L, 16) + " " + Long.toUnsignedString(-1L, 7) + " " + Long.toUnsignedString(-2L, 36)
                + " " + Long.toUnsignedString(Long.MIN_VALUE, 3) + " " + Long.toUnsignedString(12345L, 5));
        try {
            Integer.parseInt("abc", 1, 5, 10);
        } catch (IndexOutOfBoundsException e) {
            p("parse range IndexOutOfBoundsException");
        }
    }

    static void reflection() throws Exception {
        p("-- reflection");
        StringBuilder tags = new StringBuilder();
        for (Tag t : Base.class.getAnnotationsByType(Tag.class)) {
            tags.append(t.value());
        }
        p(tags);
        p(Child.class.getDeclaredAnnotation(Marker.class) + " " + (Child.class.getAnnotation(Marker.class) != null)
                + " " + Child.class.getAnnotationsByType(Marker.class).length + " "
                + Child.class.getDeclaredAnnotationsByType(Tag.class).length + " "
                + Child.class.getDeclaredAnnotationsByType(Marker.class).length);
        Method m = Child.class.getDeclaredMethod("m", int.class, String.class);
        p(m.isAnnotationPresent(Deprecated.class) + " " + m.getAnnotationsByType(Deprecated.class).length + " "
                + m.getDeclaredAnnotation(Deprecated.class).forRemoval() + " "
                + m.getDeclaredAnnotation(Deprecated.class).since().isEmpty());
        Parameter[] ps = m.getParameters();
        p(ps.length + " " + ps[0].getType() + " " + ps[1].getType().getSimpleName() + " " + ps[1].isVarArgs() + " "
                + ps[0].getDeclaringExecutable().getName() + " " + m.getGenericExceptionTypes().length);
        p(String.class.descriptorString() + " " + int[][].class.descriptorString() + " " + void.class.descriptorString()
                + " " + int.class.arrayType() + " " + String[].class.componentType() + " "
                + String.class.componentType());
        p(Runnable.class.toGenericString() + " | " + Base.class.toGenericString() + " | "
                + Thread.State.class.toGenericString() + " | " + Tag.class.toGenericString() + " | "
                + int.class.toGenericString() + " | " + Object[][].class.toGenericString());
        p(Base.class.isRecord() + " " + Base.class.isSealed() + " " + Base.class.getNestHost().getSimpleName() + " "
                + Base.class.isNestmateOf(Child.class) + " " + Base.class.isNestmateOf(String.class) + " "
                + Base.class.getSigners());
        Runnable r = new Runnable() {
            public void run() {
            }
        };
        p(r.getClass().getEnclosingMethod().getName() + " " + r.getClass().getEnclosingConstructor());
        class Local {
        }
        local = new Local();
        p(local.getClass().getEnclosingMethod().getName() + " " + Base.class.getEnclosingMethod());
        new LangTest();
        p(ctorLocal.getClass().getEnclosingConstructor().getParameterCount() + " "
                + ctorLocal.getClass().getEnclosingMethod());
        p(Modifier.classModifiers() + " " + Modifier.interfaceModifiers() + " " + Modifier.methodModifiers() + " "
                + Modifier.fieldModifiers() + " " + Modifier.constructorModifiers() + " "
                + Modifier.parameterModifiers());
    }

    static void exceptions() {
        p("-- exceptions");
        p(new IndexOutOfBoundsException(5).getMessage() + " | " + new IndexOutOfBoundsException(7L).getMessage());
        EnumConstantNotPresentException e = new EnumConstantNotPresentException(Thread.State.class, "NOPE");
        p(e.enumType().getSimpleName() + " " + e.constantName() + " " + e.getMessage());
        Throwable cause = new RuntimeException("why");
        p(new ClassNotFoundException("x", cause).getException().getMessage());
        RuntimeException quiet = new RuntimeException("quiet", null, false, false) {
        };
        quiet.addSuppressed(new Exception("ignored"));
        p(quiet.getSuppressed().length + " " + quiet.getStackTrace().length);
    }

    static void blocks() {
        p("-- blocks");
        int[] cps = {'A', 0xe9, 0x3b1, 0x416, 0x5d0, 0x627, 0x915, 0xe01, 0x1100, 0x3042, 0x30a2, 0x4e2d, 0xac00,
            0xd800, 0xdc00, 0xe000, 0xff21, 0x1f600, 0x20000, 0xe0001, 0x10ffff, 0x2fff0};
        StringBuilder sb = new StringBuilder();
        for (int cp : cps) {
            sb.append(Character.UnicodeBlock.of(cp)).append(' ');
        }
        p(sb.toString().trim());
        p(Character.UnicodeBlock.forName("BASIC_LATIN") + " " + Character.UnicodeBlock.forName("Basic Latin") + " "
                + Character.UnicodeBlock.forName("cjkunifiedideographs") + " "
                + (Character.UnicodeBlock.of('x') == Character.UnicodeBlock.BASIC_LATIN));
        try {
            Character.UnicodeBlock.forName("Klingon");
        } catch (IllegalArgumentException e) {
            p("forName " + name(e));
        }
        try {
            Character.UnicodeBlock.of(0x110000);
        } catch (IllegalArgumentException e) {
            p("of " + name(e));
        }
    }
}
