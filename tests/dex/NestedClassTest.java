import java.lang.reflect.*;
import java.util.*;

/**
 * Member, local and anonymous class reflection (Mindustry finds each block's Building class through
 * getDeclaredClasses): run on OpenJDK and on switchapk by tests/run_dex_test.sh, output must match.
 */
public class NestedClassTest {
    static class Block {
        public class Build {
        }

        private static class Helper {
        }

        interface Marker {
        }
    }

    static class Turret extends Block {
        public class TurretBuild extends Build {
        }

        protected static class Stats {
        }
    }

    static class Plain {
    }

    static String names(Class<?>[] cs) {
        List<String> l = new ArrayList<String>();
        for (Class<?> c : cs) l.add(c.getName());
        Collections.sort(l);
        return l.toString();
    }

    static void describe(String label, Class<?> c) {
        System.out.println(label + ": member=" + c.isMemberClass() + " local=" + c.isLocalClass()
                + " anonymous=" + c.isAnonymousClass() + " declaring=" + c.getDeclaringClass()
                + " enclosing=" + c.getEnclosingClass() + " simple=" + c.getSimpleName()
                + " modifiers=" + Modifier.toString(c.getModifiers()));
    }

    /** As Mindustry's Block.initBuilding: the first declared Build subclass up the hierarchy. */
    static Class<?> findBuild(Class<?> current) {
        while (Block.class.isAssignableFrom(current)) {
            for (Class<?> t : current.getDeclaredClasses()) {
                if (Block.Build.class.isAssignableFrom(t) && !t.isInterface()) return t;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Block declared: " + names(Block.class.getDeclaredClasses()));
        System.out.println("Turret declared: " + names(Turret.class.getDeclaredClasses()));
        System.out.println("Turret classes: " + names(Turret.class.getClasses()));
        System.out.println("Plain declared: " + names(Plain.class.getDeclaredClasses()));
        System.out.println("array declared: " + names(int[].class.getDeclaredClasses()));
        System.out.println("primitive declared: " + names(int.class.getDeclaredClasses()));
        System.out.println("NestedClassTest declared: " + names(NestedClassTest.class.getDeclaredClasses()));

        Class<?> build = findBuild(Turret.class);
        System.out.println("Turret build: " + build.getName());
        Turret turret = new Turret();
        Constructor<?> cons = build.getDeclaredConstructor(build.getDeclaringClass());
        Object b = cons.newInstance(turret);
        System.out.println("built: " + b.getClass().getSimpleName());
        System.out.println("Block build: " + findBuild(Block.class).getName());

        class Local {
        }
        Object anon = new Object() {
        };
        describe("member", Turret.TurretBuild.class);
        describe("static member", Block.Helper.class);
        describe("interface member", Block.Marker.class);
        describe("local", Local.class);
        describe("anonymous", anon.getClass());
        describe("top", NestedClassTest.class);
        System.out.println("canonical: " + Local.class.getCanonicalName() + " " + anon.getClass().getCanonicalName()
                + " " + Turret.TurretBuild.class.getCanonicalName());
    }
}
