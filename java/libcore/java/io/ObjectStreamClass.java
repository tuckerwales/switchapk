package java.io;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Class descriptors for serialization. A local descriptor describes a class in this VM; a stream descriptor is read
 * from an ObjectInputStream and bound (initNonProxy/initProxy) to the matching local class, if there is one. The
 * streams call the package-private methods here for fields, the serialization methods and instantiation.
 */
public final class ObjectStreamClass implements Serializable {
    public static final ObjectStreamField[] NO_FIELDS = new ObjectStreamField[0];

    private static final long serialVersionUID = -6120832682080437368L;
    private static final ObjectStreamField[] serialPersistentFields = NO_FIELDS;

    private static final HashMap<Class<?>, ObjectStreamClass> CACHE = new HashMap<Class<?>, ObjectStreamClass>();

    /** An InvalidClassException to throw later: descriptors record problems and the streams report them on use. */
    private static final class ExceptionInfo {
        final String className;
        final String message;

        ExceptionInfo(String cn, String msg) {
            className = cn;
            message = msg;
        }

        InvalidClassException newInvalidClassException() {
            return new InvalidClassException(className, message);
        }
    }

    /** One class's share of an object's serial data, from the topmost serializable superclass down. */
    static final class ClassDataSlot {
        final ObjectStreamClass desc;
        final boolean hasData;

        ClassDataSlot(ObjectStreamClass desc, boolean hasData) {
            this.desc = desc;
            this.hasData = hasData;
        }
    }

    private Class<?> cl;
    private String name;
    private volatile Long suid;
    private boolean isProxy;
    private boolean isEnum;
    private boolean serializable;
    private boolean externalizable;
    private boolean hasWriteObjectData;
    private boolean hasBlockExternalData = true;
    private ClassNotFoundException resolveEx;
    private ExceptionInfo deserializeEx;
    private ExceptionInfo serializeEx;
    private ExceptionInfo defaultSerializeEx;
    private ObjectStreamField[] fields;
    private int primDataSize;
    private int numObjFields;
    private ObjectStreamClass localDesc;
    private ObjectStreamClass superDesc;
    private Constructor<?> cons;
    private Method writeObjectMethod;
    private Method readObjectMethod;
    private Method readObjectNoDataMethod;
    private Method writeReplaceMethod;
    private Method readResolveMethod;
    private volatile ClassDataSlot[] dataLayout;
    private boolean initialized;

    public static ObjectStreamClass lookup(Class<?> cl) {
        return lookup(cl, false);
    }

    public static ObjectStreamClass lookupAny(Class<?> cl) {
        return lookup(cl, true);
    }

    static ObjectStreamClass lookup(Class<?> cl, boolean all) {
        if (cl == null || !(all || Serializable.class.isAssignableFrom(cl))) {
            return null;
        }
        synchronized (CACHE) {
            ObjectStreamClass d = CACHE.get(cl);
            if (d != null) {
                return d;
            }
        }
        // Built outside the lock: the constructor looks up superclasses, and a cycle through another thread's
        // lookup must not deadlock. Two threads may build the same descriptor; the first one stored wins.
        ObjectStreamClass d = new ObjectStreamClass(cl);
        synchronized (CACHE) {
            ObjectStreamClass prev = CACHE.get(cl);
            if (prev != null) {
                return prev;
            }
            CACHE.put(cl, d);
            return d;
        }
    }

    /** A local descriptor. */
    private ObjectStreamClass(final Class<?> cl) {
        this.cl = cl;
        name = cl.getName();
        isProxy = Proxy.isProxyClass(cl);
        isEnum = Enum.class.isAssignableFrom(cl);
        serializable = Serializable.class.isAssignableFrom(cl);
        externalizable = Externalizable.class.isAssignableFrom(cl);
        Class<?> superCl = cl.getSuperclass();
        superDesc = (superCl != null) ? lookup(superCl, false) : null;
        localDesc = this;

        if (serializable) {
            if (isEnum) {
                suid = Long.valueOf(0L);
                fields = NO_FIELDS;
            } else if (cl.isArray()) {
                fields = NO_FIELDS;
            } else {
                suid = getDeclaredSUID(cl);
                try {
                    fields = getSerialFields(cl);
                    computeFieldOffsets();
                } catch (InvalidClassException e) {
                    serializeEx = deserializeEx = new ExceptionInfo(e.classname, e.getMessage());
                    fields = NO_FIELDS;
                }
                if (externalizable) {
                    cons = getExternalizableConstructor(cl);
                } else {
                    cons = getSerializableConstructor(cl);
                    writeObjectMethod = getPrivateMethod(cl, "writeObject", new Class<?>[] {ObjectOutputStream.class},
                            Void.TYPE);
                    readObjectMethod = getPrivateMethod(cl, "readObject", new Class<?>[] {ObjectInputStream.class},
                            Void.TYPE);
                    readObjectNoDataMethod = getPrivateMethod(cl, "readObjectNoData", null, Void.TYPE);
                    hasWriteObjectData = (writeObjectMethod != null);
                }
                writeReplaceMethod = getInheritableMethod(cl, "writeReplace", null, Object.class);
                readResolveMethod = getInheritableMethod(cl, "readResolve", null, Object.class);
            }
        } else {
            suid = Long.valueOf(0L);
            fields = NO_FIELDS;
        }

        for (ObjectStreamField f : fields) {
            if (f.getField() == null) {
                defaultSerializeEx = new ExceptionInfo(name, "unmatched serializable field(s) declared");
            }
        }
        if (deserializeEx == null) {
            if (isEnum) {
                deserializeEx = new ExceptionInfo(name, "enum type");
            } else if (cons == null) {
                deserializeEx = new ExceptionInfo(name, "no valid constructor");
            }
        }
        initialized = true;
    }

    /** An empty descriptor, filled in by readNonProxy and initNonProxy or initProxy. */
    ObjectStreamClass() {
    }

    void initProxy(Class<?> cl, ClassNotFoundException resolveEx, ObjectStreamClass superDesc)
            throws InvalidClassException {
        ObjectStreamClass osc = null;
        if (cl != null) {
            osc = lookup(cl, true);
            if (!osc.isProxy) {
                throw new InvalidClassException("cannot bind proxy descriptor to a non-proxy class");
            }
        }
        this.cl = cl;
        this.resolveEx = resolveEx;
        this.superDesc = superDesc;
        isProxy = true;
        serializable = true;
        suid = Long.valueOf(0L);
        fields = NO_FIELDS;
        if (osc != null) {
            localDesc = osc;
            name = localDesc.name;
            externalizable = localDesc.externalizable;
            writeReplaceMethod = localDesc.writeReplaceMethod;
            readResolveMethod = localDesc.readResolveMethod;
            deserializeEx = localDesc.deserializeEx;
            cons = localDesc.cons;
        }
        initialized = true;
    }

    void initNonProxy(ObjectStreamClass model, Class<?> cl, ClassNotFoundException resolveEx,
            ObjectStreamClass superDesc) throws InvalidClassException {
        long suid = model.getSerialVersionUID();
        ObjectStreamClass osc = null;
        if (cl != null) {
            osc = lookup(cl, true);
            if (osc.isProxy) {
                throw new InvalidClassException("cannot bind non-proxy descriptor to a proxy class");
            }
            if (model.isEnum != osc.isEnum) {
                throw new InvalidClassException(model.isEnum ? "cannot bind enum descriptor to a non-enum class"
                        : "cannot bind non-enum descriptor to an enum class");
            }
            if (model.serializable == osc.serializable && !cl.isArray() && suid != osc.getSerialVersionUID()) {
                throw new InvalidClassException(osc.name, "local class incompatible: stream classdesc "
                        + "serialVersionUID = " + suid + ", local class serialVersionUID = "
                        + osc.getSerialVersionUID());
            }
            if (!classNamesEqual(model.name, osc.name)) {
                throw new InvalidClassException(osc.name,
                        "local class name incompatible with stream class name \"" + model.name + "\"");
            }
            if (!model.isEnum) {
                if (model.serializable == osc.serializable && model.externalizable != osc.externalizable) {
                    throw new InvalidClassException(osc.name, "Serializable incompatible with Externalizable");
                }
                if (model.serializable != osc.serializable || model.externalizable != osc.externalizable
                        || !(model.serializable || model.externalizable)) {
                    deserializeEx = new ExceptionInfo(osc.name, "class invalid for deserialization");
                }
            }
        }

        this.cl = cl;
        this.resolveEx = resolveEx;
        this.superDesc = superDesc;
        name = model.name;
        this.suid = Long.valueOf(suid);
        isProxy = false;
        isEnum = model.isEnum;
        serializable = model.serializable;
        externalizable = model.externalizable;
        hasBlockExternalData = model.hasBlockExternalData;
        hasWriteObjectData = model.hasWriteObjectData;
        fields = model.fields;
        primDataSize = model.primDataSize;
        numObjFields = model.numObjFields;

        if (osc != null) {
            localDesc = osc;
            writeObjectMethod = localDesc.writeObjectMethod;
            readObjectMethod = localDesc.readObjectMethod;
            readObjectNoDataMethod = localDesc.readObjectNoDataMethod;
            writeReplaceMethod = localDesc.writeReplaceMethod;
            readResolveMethod = localDesc.readResolveMethod;
            if (deserializeEx == null) {
                deserializeEx = localDesc.deserializeEx;
            }
            cons = localDesc.cons;
        }
        fields = getMatchingFields(fields, localDesc);
        initialized = true;
    }

    /** Reads a non-proxy class descriptor's body (after TC_CLASSDESC, up to the class annotations). */
    void readNonProxy(ObjectInputStream in) throws IOException, ClassNotFoundException {
        name = in.readUTF();
        suid = Long.valueOf(in.readLong());
        isProxy = false;

        byte flags = in.readByte();
        hasWriteObjectData = (flags & ObjectStreamConstants.SC_WRITE_METHOD) != 0;
        hasBlockExternalData = (flags & ObjectStreamConstants.SC_BLOCK_DATA) != 0;
        externalizable = (flags & ObjectStreamConstants.SC_EXTERNALIZABLE) != 0;
        boolean sflag = (flags & ObjectStreamConstants.SC_SERIALIZABLE) != 0;
        if (externalizable && sflag) {
            throw new InvalidClassException(name, "serializable and externalizable flags conflict");
        }
        serializable = externalizable || sflag;
        isEnum = (flags & ObjectStreamConstants.SC_ENUM) != 0;
        if (isEnum && suid.longValue() != 0L) {
            throw new InvalidClassException(name, "enum descriptor has non-zero serialVersionUID: " + suid);
        }

        int numFields = in.readShort();
        if (isEnum && numFields != 0) {
            throw new InvalidClassException(name, "enum descriptor has non-zero field count: " + numFields);
        }
        fields = (numFields > 0) ? new ObjectStreamField[numFields] : NO_FIELDS;
        for (int i = 0; i < numFields; i++) {
            char tcode = (char) in.readByte();
            String fname = in.readUTF();
            String signature = (tcode == 'L' || tcode == '[') ? in.readTypeString() : String.valueOf(tcode);
            try {
                fields[i] = new ObjectStreamField(fname, signature, false);
            } catch (RuntimeException e) {
                throw (IOException) new InvalidClassException(name, "invalid descriptor for field " + fname)
                        .initCause(e);
            }
        }
        computeFieldOffsets();
    }

    /** Writes a non-proxy class descriptor's body. */
    void writeNonProxy(ObjectOutputStream out) throws IOException {
        out.writeUTF(name);
        out.writeLong(getSerialVersionUID());

        byte flags = 0;
        if (externalizable) {
            flags |= ObjectStreamConstants.SC_EXTERNALIZABLE;
            if (out.getProtocolVersion() != ObjectStreamConstants.PROTOCOL_VERSION_1) {
                flags |= ObjectStreamConstants.SC_BLOCK_DATA;
            }
        } else if (serializable) {
            flags |= ObjectStreamConstants.SC_SERIALIZABLE;
        }
        if (hasWriteObjectData) {
            flags |= ObjectStreamConstants.SC_WRITE_METHOD;
        }
        if (isEnum) {
            flags |= ObjectStreamConstants.SC_ENUM;
        }
        out.writeByte(flags);

        out.writeShort(fields.length);
        for (ObjectStreamField f : fields) {
            out.writeByte(f.getTypeCode());
            out.writeUTF(f.getName());
            if (!f.isPrimitive()) {
                out.writeTypeString(f.getTypeString());
            }
        }
    }

    public String getName() {
        return name;
    }

    public long getSerialVersionUID() {
        if (suid == null) {
            suid = Long.valueOf(computeDefaultSUID(cl));
        }
        return suid.longValue();
    }

    public Class<?> forClass() {
        return cl;
    }

    public ObjectStreamField[] getFields() {
        return getFields(true);
    }

    ObjectStreamField[] getFields(boolean copy) {
        return copy ? fields.clone() : fields;
    }

    public ObjectStreamField getField(String name) {
        return getField(name, null);
    }

    /** The field called name whose type fits type (any type for null, any reference type for Object.class). */
    ObjectStreamField getField(String name, Class<?> type) {
        for (ObjectStreamField f : fields) {
            if (f.getName().equals(name)) {
                if (type == null || (type == Object.class && !f.isPrimitive())) {
                    return f;
                }
                Class<?> ftype = f.getType();
                if (ftype != null && type.isAssignableFrom(ftype)) {
                    return f;
                }
            }
        }
        return null;
    }

    public String toString() {
        return name + ": static final long serialVersionUID = " + getSerialVersionUID() + "L;";
    }

    ClassNotFoundException getResolveException() {
        return resolveEx;
    }

    ObjectStreamClass getSuperDesc() {
        return superDesc;
    }

    ObjectStreamClass getLocalDesc() {
        return localDesc;
    }

    boolean isProxy() {
        return isProxy;
    }

    boolean isEnum() {
        return isEnum;
    }

    boolean isExternalizable() {
        return externalizable;
    }

    boolean isSerializable() {
        return serializable;
    }

    boolean hasBlockExternalData() {
        return hasBlockExternalData;
    }

    boolean hasWriteObjectData() {
        return hasWriteObjectData;
    }

    boolean isInstantiable() {
        return cons != null;
    }

    boolean hasWriteObjectMethod() {
        return writeObjectMethod != null;
    }

    boolean hasReadObjectMethod() {
        return readObjectMethod != null;
    }

    boolean hasReadObjectNoDataMethod() {
        return readObjectNoDataMethod != null;
    }

    boolean hasWriteReplaceMethod() {
        return writeReplaceMethod != null;
    }

    boolean hasReadResolveMethod() {
        return readResolveMethod != null;
    }

    int getPrimDataSize() {
        return primDataSize;
    }

    int getNumObjFields() {
        return numObjFields;
    }

    void checkInitialized() throws InvalidClassException {
        if (!initialized) {
            throw new InvalidClassException("Class descriptor should be initialized");
        }
    }

    void checkDeserialize() throws InvalidClassException {
        if (deserializeEx != null) {
            throw deserializeEx.newInvalidClassException();
        }
    }

    void checkSerialize() throws InvalidClassException {
        if (serializeEx != null) {
            throw serializeEx.newInvalidClassException();
        }
    }

    void checkDefaultSerialize() throws InvalidClassException {
        if (defaultSerializeEx != null) {
            throw defaultSerializeEx.newInvalidClassException();
        }
    }

    /** A new instance with only the constructor serialization calls run on it. */
    Object newInstance() throws InstantiationException, InvocationTargetException {
        if (cons == null) {
            throw new UnsupportedOperationException();
        }
        try {
            return newInstanceNative(cl, cons);
        } catch (InstantiationException e) {
            throw e;
        } catch (Throwable t) {
            throw new InvocationTargetException(t);
        }
    }

    private static native Object newInstanceNative(Class<?> cl, Constructor<?> cons) throws InstantiationException;

    private static native boolean hasStaticInitializer(Class<?> cl);

    void invokeWriteObject(Object obj, ObjectOutputStream out) throws IOException {
        if (writeObjectMethod == null) {
            throw new UnsupportedOperationException();
        }
        try {
            writeObjectMethod.invoke(obj, out);
        } catch (InvocationTargetException ex) {
            Throwable th = ex.getCause();
            if (th instanceof IOException) {
                throw (IOException) th;
            }
            throwMiscException(th);
        } catch (IllegalAccessException ex) {
            throw new InternalError(ex);
        }
    }

    void invokeReadObject(Object obj, ObjectInputStream in) throws ClassNotFoundException, IOException {
        if (readObjectMethod == null) {
            throw new UnsupportedOperationException();
        }
        try {
            readObjectMethod.invoke(obj, in);
        } catch (InvocationTargetException ex) {
            Throwable th = ex.getCause();
            if (th instanceof ClassNotFoundException) {
                throw (ClassNotFoundException) th;
            } else if (th instanceof IOException) {
                throw (IOException) th;
            }
            throwMiscException(th);
        } catch (IllegalAccessException ex) {
            throw new InternalError(ex);
        }
    }

    void invokeReadObjectNoData(Object obj) throws IOException {
        if (readObjectNoDataMethod == null) {
            throw new UnsupportedOperationException();
        }
        try {
            readObjectNoDataMethod.invoke(obj);
        } catch (InvocationTargetException ex) {
            Throwable th = ex.getCause();
            if (th instanceof ObjectStreamException) {
                throw (ObjectStreamException) th;
            }
            throwMiscException(th);
        } catch (IllegalAccessException ex) {
            throw new InternalError(ex);
        }
    }

    Object invokeWriteReplace(Object obj) throws IOException {
        if (writeReplaceMethod == null) {
            throw new UnsupportedOperationException();
        }
        try {
            return writeReplaceMethod.invoke(obj);
        } catch (InvocationTargetException ex) {
            Throwable th = ex.getCause();
            if (th instanceof ObjectStreamException) {
                throw (ObjectStreamException) th;
            }
            throwMiscException(th);
            throw new InternalError(th);
        } catch (IllegalAccessException ex) {
            throw new InternalError(ex);
        }
    }

    Object invokeReadResolve(Object obj) throws IOException {
        if (readResolveMethod == null) {
            throw new UnsupportedOperationException();
        }
        try {
            return readResolveMethod.invoke(obj);
        } catch (InvocationTargetException ex) {
            Throwable th = ex.getCause();
            if (th instanceof ObjectStreamException) {
                throw (ObjectStreamException) th;
            }
            throwMiscException(th);
            throw new InternalError(th);
        } catch (IllegalAccessException ex) {
            throw new InternalError(ex);
        }
    }

    private static void throwMiscException(Throwable th) throws IOException {
        if (th instanceof RuntimeException) {
            throw (RuntimeException) th;
        } else if (th instanceof Error) {
            throw (Error) th;
        }
        IOException ex = new IOException("unexpected exception type");
        ex.initCause(th);
        throw ex;
    }

    /** The slots for an object of this class: one per serializable class, superclasses first. */
    ClassDataSlot[] getClassDataLayout() throws InvalidClassException {
        ClassDataSlot[] layout = dataLayout;
        if (layout == null) {
            layout = dataLayout = getClassDataLayout0();
        }
        return layout;
    }

    private ClassDataSlot[] getClassDataLayout0() throws InvalidClassException {
        ArrayList<ClassDataSlot> slots = new ArrayList<ClassDataSlot>();
        Class<?> start = cl;
        Class<?> end = cl;
        while (end != null && Serializable.class.isAssignableFrom(end)) {
            end = end.getSuperclass();
        }
        HashSet<String> oscNames = new HashSet<String>(3);
        for (ObjectStreamClass d = this; d != null; d = d.superDesc) {
            if (!oscNames.add(d.name)) {
                throw new InvalidClassException("Circular reference.");
            }
            // The local class this descriptor's data belongs to, and "no data" slots for the classes below it
            // that the stream has no data for (classes added to the hierarchy since it was written).
            String searchName = (d.cl != null) ? d.cl.getName() : d.name;
            Class<?> match = null;
            for (Class<?> c = start; c != end; c = c.getSuperclass()) {
                if (searchName.equals(c.getName())) {
                    match = c;
                    break;
                }
            }
            if (match != null) {
                for (Class<?> c = start; c != match; c = c.getSuperclass()) {
                    slots.add(new ClassDataSlot(lookup(c, true), false));
                }
                start = match.getSuperclass();
            }
            slots.add(new ClassDataSlot(d.getVariantFor(match), true));
        }
        for (Class<?> c = start; c != end; c = c.getSuperclass()) {
            slots.add(new ClassDataSlot(lookup(c, true), false));
        }
        Collections.reverse(slots);
        return slots.toArray(new ClassDataSlot[slots.size()]);
    }

    private ObjectStreamClass getVariantFor(Class<?> cl) throws InvalidClassException {
        if (this.cl == cl) {
            return this;
        }
        ObjectStreamClass desc = new ObjectStreamClass();
        if (isProxy) {
            desc.initProxy(cl, null, superDesc);
        } else {
            desc.initNonProxy(this, cl, null, superDesc);
        }
        return desc;
    }

    private void computeFieldOffsets() throws InvalidClassException {
        primDataSize = 0;
        numObjFields = 0;
        int firstObjIndex = -1;
        for (int i = 0; i < fields.length; i++) {
            ObjectStreamField f = fields[i];
            switch (f.getTypeCode()) {
                case 'Z':
                case 'B':
                    f.setOffset(primDataSize++);
                    break;
                case 'C':
                case 'S':
                    f.setOffset(primDataSize);
                    primDataSize += 2;
                    break;
                case 'I':
                case 'F':
                    f.setOffset(primDataSize);
                    primDataSize += 4;
                    break;
                case 'J':
                case 'D':
                    f.setOffset(primDataSize);
                    primDataSize += 8;
                    break;
                case '[':
                case 'L':
                    f.setOffset(numObjFields++);
                    if (firstObjIndex == -1) {
                        firstObjIndex = i;
                    }
                    break;
                default:
                    throw new InternalError();
            }
        }
        if (firstObjIndex != -1 && firstObjIndex + numObjFields != fields.length) {
            throw new InvalidClassException(name, "illegal field order");
        }
    }

    /** The stream's fields, each tied to the local field of the same name (if any) and keeping its offset. */
    private static ObjectStreamField[] getMatchingFields(ObjectStreamField[] fields, ObjectStreamClass localDesc)
            throws InvalidClassException {
        ObjectStreamField[] localFields = (localDesc != null) ? localDesc.fields : NO_FIELDS;
        ObjectStreamField[] matches = new ObjectStreamField[fields.length];
        for (int i = 0; i < fields.length; i++) {
            ObjectStreamField f = fields[i];
            ObjectStreamField m = null;
            for (ObjectStreamField lf : localFields) {
                if (f.getName().equals(lf.getName())) {
                    if ((f.isPrimitive() || lf.isPrimitive()) && f.getTypeCode() != lf.getTypeCode()) {
                        throw new InvalidClassException(localDesc.name, "incompatible types for field " + f.getName());
                    }
                    if (lf.getField() != null) {
                        m = new ObjectStreamField(lf.getField(), lf.isUnshared(), false);
                    } else {
                        m = new ObjectStreamField(lf.getName(), lf.getSignature(), lf.isUnshared());
                    }
                }
            }
            if (m == null) {
                m = new ObjectStreamField(f.getName(), f.getSignature(), false);
            }
            m.setOffset(f.getOffset());
            matches[i] = m;
        }
        return matches;
    }

    /** Copies obj's primitive field values into buf at their offsets, big-endian as in the stream. */
    void getPrimFieldValues(Object obj, byte[] buf) {
        try {
            for (ObjectStreamField f : fields) {
                if (!f.isPrimitive()) {
                    break;
                }
                Field rf = f.getField();
                if (rf == null) {
                    continue;
                }
                int off = f.getOffset();
                switch (f.getTypeCode()) {
                    case 'Z': buf[off] = (byte) (rf.getBoolean(obj) ? 1 : 0); break;
                    case 'B': buf[off] = rf.getByte(obj); break;
                    case 'C': putShort(buf, off, rf.getChar(obj)); break;
                    case 'S': putShort(buf, off, rf.getShort(obj)); break;
                    case 'I': putInt(buf, off, rf.getInt(obj)); break;
                    case 'F': putInt(buf, off, Float.floatToIntBits(rf.getFloat(obj))); break;
                    case 'J': putLong(buf, off, rf.getLong(obj)); break;
                    case 'D': putLong(buf, off, Double.doubleToLongBits(rf.getDouble(obj))); break;
                    default: throw new InternalError();
                }
            }
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    void setPrimFieldValues(Object obj, byte[] buf) {
        try {
            for (ObjectStreamField f : fields) {
                if (!f.isPrimitive()) {
                    break;
                }
                Field rf = f.getField();
                if (rf == null) {
                    continue;
                }
                int off = f.getOffset();
                switch (f.getTypeCode()) {
                    case 'Z': rf.setBoolean(obj, buf[off] != 0); break;
                    case 'B': rf.setByte(obj, buf[off]); break;
                    case 'C': rf.setChar(obj, (char) getShort(buf, off)); break;
                    case 'S': rf.setShort(obj, getShort(buf, off)); break;
                    case 'I': rf.setInt(obj, getInt(buf, off)); break;
                    case 'F': rf.setFloat(obj, Float.intBitsToFloat(getInt(buf, off))); break;
                    case 'J': rf.setLong(obj, getLong(buf, off)); break;
                    case 'D': rf.setDouble(obj, Double.longBitsToDouble(getLong(buf, off))); break;
                    default: throw new InternalError();
                }
            }
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    void getObjFieldValues(Object obj, Object[] vals) {
        int numPrim = fields.length - numObjFields;
        try {
            for (int i = 0; i < numObjFields; i++) {
                Field rf = fields[numPrim + i].getField();
                vals[i] = (rf != null) ? rf.get(obj) : null;
            }
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    void setObjFieldValues(Object obj, Object[] vals) {
        int numPrim = fields.length - numObjFields;
        try {
            for (int i = 0; i < numObjFields; i++) {
                Field rf = fields[numPrim + i].getField();
                if (rf == null) {
                    continue;
                }
                Object val = vals[i];
                if (val != null && !rf.getType().isInstance(val)) {
                    throw new ClassCastException("cannot assign instance of " + val.getClass().getName()
                            + " to field " + rf.getDeclaringClass().getName() + "." + rf.getName() + " of type "
                            + rf.getType().getName() + " in instance of " + obj.getClass().getName());
                }
                rf.set(obj, val);
            }
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    static void putShort(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 8);
        b[off + 1] = (byte) v;
    }

    static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }

    static void putLong(byte[] b, int off, long v) {
        putInt(b, off, (int) (v >>> 32));
        putInt(b, off + 4, (int) v);
    }

    static short getShort(byte[] b, int off) {
        return (short) (((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF));
    }

    static int getInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16) | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    static long getLong(byte[] b, int off) {
        return ((long) getInt(b, off) << 32) | (getInt(b, off + 4) & 0xFFFFFFFFL);
    }

    private static boolean classNamesEqual(String name1, String name2) {
        name1 = name1.substring(name1.lastIndexOf('.') + 1);
        name2 = name2.substring(name2.lastIndexOf('.') + 1);
        return name1.equals(name2);
    }

    private static boolean packageEquals(Class<?> cl1, Class<?> cl2) {
        return packageName(cl1).equals(packageName(cl2)) && cl1.getClassLoader() == cl2.getClassLoader();
    }

    private static String packageName(Class<?> cl) {
        String s = cl.getName();
        int i = s.lastIndexOf('[');
        if (i >= 0) {
            s = s.substring(i + 2);
        }
        i = s.lastIndexOf('.');
        return (i >= 0) ? s.substring(0, i) : "";
    }

    private static Long getDeclaredSUID(Class<?> cl) {
        try {
            Field f = cl.getDeclaredField("serialVersionUID");
            int mask = Modifier.STATIC | Modifier.FINAL;
            if ((f.getModifiers() & mask) == mask && f.getType() == Long.TYPE) {
                f.setAccessible(true);
                return Long.valueOf(f.getLong(null));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static ObjectStreamField[] getSerialFields(Class<?> cl) throws InvalidClassException {
        ObjectStreamField[] fields;
        if (Serializable.class.isAssignableFrom(cl) && !Externalizable.class.isAssignableFrom(cl)
                && !Proxy.isProxyClass(cl) && !cl.isInterface()) {
            if ((fields = getDeclaredSerialFields(cl)) == null) {
                fields = getDefaultSerialFields(cl);
            }
            Arrays.sort(fields);
        } else {
            fields = NO_FIELDS;
        }
        return fields;
    }

    /** serialPersistentFields, tied to the class's own fields where name and type agree; null if not declared. */
    private static ObjectStreamField[] getDeclaredSerialFields(Class<?> cl) throws InvalidClassException {
        ObjectStreamField[] serialPersistentFields = null;
        try {
            Field f = cl.getDeclaredField("serialPersistentFields");
            int mask = Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL;
            if ((f.getModifiers() & mask) == mask) {
                f.setAccessible(true);
                serialPersistentFields = (ObjectStreamField[]) f.get(null);
            }
        } catch (Exception ignored) {
        }
        if (serialPersistentFields == null) {
            return null;
        } else if (serialPersistentFields.length == 0) {
            return NO_FIELDS;
        }

        ObjectStreamField[] boundFields = new ObjectStreamField[serialPersistentFields.length];
        HashSet<String> fieldNames = new HashSet<String>(serialPersistentFields.length);
        for (int i = 0; i < serialPersistentFields.length; i++) {
            ObjectStreamField spf = serialPersistentFields[i];
            String fname = spf.getName();
            if (!fieldNames.add(fname)) {
                throw new InvalidClassException("multiple serializable fields named " + fname);
            }
            try {
                Field f = cl.getDeclaredField(fname);
                if (f.getType() == spf.getType() && (f.getModifiers() & Modifier.STATIC) == 0) {
                    f.setAccessible(true);
                    boundFields[i] = new ObjectStreamField(f, spf.isUnshared(), true);
                }
            } catch (NoSuchFieldException ignored) {
            }
            if (boundFields[i] == null) {
                boundFields[i] = new ObjectStreamField(fname, spf.getType(), spf.isUnshared());
            }
        }
        return boundFields;
    }

    private static ObjectStreamField[] getDefaultSerialFields(Class<?> cl) {
        ArrayList<ObjectStreamField> list = new ArrayList<ObjectStreamField>();
        int mask = Modifier.STATIC | Modifier.TRANSIENT;
        for (Field f : cl.getDeclaredFields()) {
            if ((f.getModifiers() & mask) == 0) {
                f.setAccessible(true);
                list.add(new ObjectStreamField(f, false, true));
            }
        }
        int size = list.size();
        return (size == 0) ? NO_FIELDS : list.toArray(new ObjectStreamField[size]);
    }

    /** The public no-arg constructor an Externalizable class needs, or null. */
    private static Constructor<?> getExternalizableConstructor(Class<?> cl) {
        try {
            Constructor<?> c = cl.getDeclaredConstructor();
            if ((c.getModifiers() & Modifier.PUBLIC) == 0) {
                return null;
            }
            c.setAccessible(true);
            return c;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /** The no-arg constructor of cl's first non-serializable superclass, if cl may call it; otherwise null. */
    private static Constructor<?> getSerializableConstructor(Class<?> cl) {
        Class<?> initCl = cl;
        while (Serializable.class.isAssignableFrom(initCl)) {
            initCl = initCl.getSuperclass();
            if (initCl == null) {
                return null;
            }
        }
        try {
            Constructor<?> c = initCl.getDeclaredConstructor();
            int mods = c.getModifiers();
            if ((mods & Modifier.PRIVATE) != 0
                    || ((mods & (Modifier.PUBLIC | Modifier.PROTECTED)) == 0 && !packageEquals(cl, initCl))) {
                return null;
            }
            c.setAccessible(true);
            return c;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /** cl's own non-static private method with this signature, or null. */
    private static Method getPrivateMethod(Class<?> cl, String name, Class<?>[] argTypes, Class<?> returnType) {
        try {
            Method meth = cl.getDeclaredMethod(name, argTypes);
            meth.setAccessible(true);
            int mods = meth.getModifiers();
            return (meth.getReturnType() == returnType && (mods & Modifier.STATIC) == 0
                    && (mods & Modifier.PRIVATE) != 0) ? meth : null;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /** A non-static, non-abstract method of cl or a superclass that cl can see (for writeReplace and readResolve). */
    private static Method getInheritableMethod(Class<?> cl, String name, Class<?>[] argTypes, Class<?> returnType) {
        Method meth = null;
        Class<?> defCl = cl;
        while (defCl != null) {
            try {
                meth = defCl.getDeclaredMethod(name, argTypes);
                break;
            } catch (NoSuchMethodException ex) {
                defCl = defCl.getSuperclass();
            }
        }
        if (meth == null || meth.getReturnType() != returnType) {
            return null;
        }
        meth.setAccessible(true);
        int mods = meth.getModifiers();
        if ((mods & (Modifier.STATIC | Modifier.ABSTRACT)) != 0) {
            return null;
        } else if ((mods & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0) {
            return meth;
        } else if ((mods & Modifier.PRIVATE) != 0) {
            return (cl == defCl) ? meth : null;
        } else {
            return packageEquals(cl, defCl) ? meth : null;
        }
    }

    private static final class MemberSignature {
        final String name;
        final String signature;
        final int modifiers;

        MemberSignature(String name, String signature, int modifiers) {
            this.name = name;
            this.signature = signature;
            this.modifiers = modifiers;
        }
    }

    private static String methodSignature(Class<?>[] params, Class<?> ret) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : params) {
            sb.append(ObjectStreamField.signatureOf(p));
        }
        sb.append(')').append(ObjectStreamField.signatureOf(ret));
        return sb.toString();
    }

    /** The serialVersionUID Java computes for a class that declares none: a SHA-1 over its shape. */
    private static long computeDefaultSUID(Class<?> cl) {
        if (!Serializable.class.isAssignableFrom(cl) || Proxy.isProxyClass(cl)) {
            return 0L;
        }
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(bout);

            dout.writeUTF(cl.getName());

            int classMods = cl.getModifiers()
                    & (Modifier.PUBLIC | Modifier.FINAL | Modifier.INTERFACE | Modifier.ABSTRACT);
            Method[] methods = cl.getDeclaredMethods();
            if ((classMods & Modifier.INTERFACE) != 0) {
                classMods = (methods.length > 0) ? (classMods | Modifier.ABSTRACT) : (classMods & ~Modifier.ABSTRACT);
            }
            dout.writeInt(classMods);

            if (!cl.isArray()) {
                Class<?>[] interfaces = cl.getInterfaces();
                String[] intfNames = new String[interfaces.length];
                for (int i = 0; i < interfaces.length; i++) {
                    intfNames[i] = interfaces[i].getName();
                }
                Arrays.sort(intfNames);
                for (String n : intfNames) {
                    dout.writeUTF(n);
                }
            }

            Field[] fields = cl.getDeclaredFields();
            MemberSignature[] fieldSigs = new MemberSignature[fields.length];
            for (int i = 0; i < fields.length; i++) {
                fieldSigs[i] = new MemberSignature(fields[i].getName(),
                        ObjectStreamField.signatureOf(fields[i].getType()), fields[i].getModifiers());
            }
            Arrays.sort(fieldSigs, new Comparator<MemberSignature>() {
                public int compare(MemberSignature a, MemberSignature b) {
                    return a.name.compareTo(b.name);
                }
            });
            for (MemberSignature sig : fieldSigs) {
                int mods = sig.modifiers & (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED | Modifier.STATIC
                        | Modifier.FINAL | Modifier.VOLATILE | Modifier.TRANSIENT);
                if ((mods & Modifier.PRIVATE) == 0 || (mods & (Modifier.STATIC | Modifier.TRANSIENT)) == 0) {
                    dout.writeUTF(sig.name);
                    dout.writeInt(mods);
                    dout.writeUTF(sig.signature);
                }
            }

            if (hasStaticInitializer(cl)) {
                dout.writeUTF("<clinit>");
                dout.writeInt(Modifier.STATIC);
                dout.writeUTF("()V");
            }

            int memberMask = Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED | Modifier.STATIC
                    | Modifier.FINAL | Modifier.SYNCHRONIZED | Modifier.NATIVE | Modifier.ABSTRACT | Modifier.STRICT;

            Constructor<?>[] cons = cl.getDeclaredConstructors();
            MemberSignature[] consSigs = new MemberSignature[cons.length];
            for (int i = 0; i < cons.length; i++) {
                consSigs[i] = new MemberSignature("<init>", methodSignature(cons[i].getParameterTypes(), Void.TYPE),
                        cons[i].getModifiers());
            }
            Arrays.sort(consSigs, new Comparator<MemberSignature>() {
                public int compare(MemberSignature a, MemberSignature b) {
                    return a.signature.compareTo(b.signature);
                }
            });
            for (MemberSignature sig : consSigs) {
                int mods = sig.modifiers & memberMask;
                if ((mods & Modifier.PRIVATE) == 0) {
                    dout.writeUTF("<init>");
                    dout.writeInt(mods);
                    dout.writeUTF(sig.signature.replace('/', '.'));
                }
            }

            MemberSignature[] methSigs = new MemberSignature[methods.length];
            for (int i = 0; i < methods.length; i++) {
                methSigs[i] = new MemberSignature(methods[i].getName(),
                        methodSignature(methods[i].getParameterTypes(), methods[i].getReturnType()),
                        methods[i].getModifiers());
            }
            Arrays.sort(methSigs, new Comparator<MemberSignature>() {
                public int compare(MemberSignature a, MemberSignature b) {
                    int comp = a.name.compareTo(b.name);
                    return (comp != 0) ? comp : a.signature.compareTo(b.signature);
                }
            });
            for (MemberSignature sig : methSigs) {
                int mods = sig.modifiers & memberMask;
                if ((mods & Modifier.PRIVATE) == 0) {
                    dout.writeUTF(sig.name);
                    dout.writeInt(mods);
                    dout.writeUTF(sig.signature.replace('/', '.'));
                }
            }

            dout.flush();
            byte[] hashBytes = MessageDigest.getInstance("SHA").digest(bout.toByteArray());
            long hash = 0;
            for (int i = Math.min(hashBytes.length, 8) - 1; i >= 0; i--) {
                hash = (hash << 8) | (hashBytes[i] & 0xFF);
            }
            return hash;
        } catch (IOException ex) {
            throw new InternalError(ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new SecurityException(ex.getMessage());
        }
    }
}
