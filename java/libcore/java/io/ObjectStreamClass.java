package java.io;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Class descriptors for serialization. There are no object streams yet, so this only describes:
 * the fields are serialPersistentFields or the non-static, non-transient fields, sorted as Java does.
 * TODO(WS16): the default serialVersionUID hash (it needs to know whether a class has a static
 * initializer, which reflection cannot tell); classes without a declared one report 0.
 */
public final class ObjectStreamClass implements Serializable {
    public static final ObjectStreamField[] NO_FIELDS = new ObjectStreamField[0];

    private static final HashMap<Class<?>, ObjectStreamClass> CACHE = new HashMap<Class<?>, ObjectStreamClass>();

    private final Class<?> cl;
    private final String name;
    private final boolean serializable;
    private final ObjectStreamField[] fields;
    private final long suid;

    private ObjectStreamClass(Class<?> cl) {
        this.cl = cl;
        this.name = cl.getName();
        this.serializable = Serializable.class.isAssignableFrom(cl);
        boolean externalizable = Externalizable.class.isAssignableFrom(cl);
        this.fields = (!serializable || externalizable || cl.isEnum() || cl.isArray()) ? NO_FIELDS : fieldsOf(cl);
        this.suid = (cl.isEnum() || cl.isArray()) ? 0L : declaredSuid(cl);
    }

    public static ObjectStreamClass lookup(Class<?> cl) {
        if (cl == null || !Serializable.class.isAssignableFrom(cl)) {
            return null;
        }
        return lookupAny(cl);
    }

    public static ObjectStreamClass lookupAny(Class<?> cl) {
        synchronized (CACHE) {
            ObjectStreamClass d = CACHE.get(cl);
            if (d == null) {
                d = new ObjectStreamClass(cl);
                CACHE.put(cl, d);
            }
            return d;
        }
    }

    private static long declaredSuid(Class<?> cl) {
        try {
            Field f = cl.getDeclaredField("serialVersionUID");
            int mask = Modifier.STATIC | Modifier.FINAL;
            if ((f.getModifiers() & mask) == mask && f.getType() == Long.TYPE) {
                f.setAccessible(true);
                return f.getLong(null);
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private static ObjectStreamField[] fieldsOf(Class<?> cl) {
        try {
            Field f = cl.getDeclaredField("serialPersistentFields");
            int mask = Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL;
            if ((f.getModifiers() & mask) == mask) {
                f.setAccessible(true);
                ObjectStreamField[] declared = (ObjectStreamField[]) f.get(null);
                if (declared != null) {
                    ObjectStreamField[] copy = declared.clone();
                    Arrays.sort(copy);
                    return copy;
                }
            }
        } catch (Exception ignored) {
        }
        ArrayList<ObjectStreamField> list = new ArrayList<ObjectStreamField>();
        for (Field f : cl.getDeclaredFields()) {
            int mods = f.getModifiers();
            if ((mods & (Modifier.STATIC | Modifier.TRANSIENT)) == 0) {
                list.add(new ObjectStreamField(f, false, true));
            }
        }
        ObjectStreamField[] out = list.toArray(NO_FIELDS);
        Arrays.sort(out);
        return out;
    }

    public String getName() {
        return name;
    }

    public long getSerialVersionUID() {
        return suid;
    }

    public Class<?> forClass() {
        return cl;
    }

    public ObjectStreamField[] getFields() {
        return fields.length == 0 ? NO_FIELDS : fields.clone();
    }

    public ObjectStreamField getField(String name) {
        for (ObjectStreamField f : fields) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    public String toString() {
        return name + ": static final long serialVersionUID = " + suid + "L;";
    }
}
