package java.io;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Reads the Java serialization stream format. Objects are created without running their own constructors (only the
 * no-arg constructor of the first non-serializable superclass), fields are set by reflection, and the class's
 * readObject, readObjectNoData and readResolve methods are honoured. A class that cannot be found does not stop the
 * read: its data is skipped and the ClassNotFoundException is thrown once the enclosing top-level object is done.
 */
public class ObjectInputStream extends InputStream implements ObjectInput, ObjectStreamConstants {

    public static abstract class GetField {
        public GetField() {
        }

        public abstract ObjectStreamClass getObjectStreamClass();

        public abstract boolean defaulted(String name) throws IOException;

        public abstract boolean get(String name, boolean val) throws IOException;

        public abstract byte get(String name, byte val) throws IOException;

        public abstract char get(String name, char val) throws IOException;

        public abstract short get(String name, short val) throws IOException;

        public abstract int get(String name, int val) throws IOException;

        public abstract long get(String name, long val) throws IOException;

        public abstract float get(String name, float val) throws IOException;

        public abstract double get(String name, double val) throws IOException;

        public abstract Object get(String name, Object val) throws IOException, ClassNotFoundException;
    }

    private static final int NULL_HANDLE = -1;
    /** Stands in the handle table for objects read unshared, which later back references may not name. */
    private static final Object unsharedMarker = new Object();

    private static final HashMap<String, Class<?>> primClasses = new HashMap<String, Class<?>>(8, 1.0F);

    static {
        primClasses.put("boolean", boolean.class);
        primClasses.put("byte", byte.class);
        primClasses.put("char", char.class);
        primClasses.put("short", short.class);
        primClasses.put("int", int.class);
        primClasses.put("long", long.class);
        primClasses.put("float", float.class);
        primClasses.put("double", double.class);
        primClasses.put("void", void.class);
    }

    private final BlockDataInputStream bin;
    private final HandleTable handles;
    private final ValidationList vlist;
    private long depth;
    private long totalObjectRefs;
    private boolean closed;
    private int passHandle = NULL_HANDLE;
    /** True after defaultReadObject for a class written without writeObject: its data ends without a marker. */
    private boolean defaultDataEnd;
    private final boolean enableOverride;
    private boolean enableResolve;
    private ObjectOutputStream.SerialCallbackContext curContext;

    public ObjectInputStream(InputStream in) throws IOException {
        bin = new BlockDataInputStream(in);
        handles = new HandleTable();
        vlist = new ValidationList();
        enableOverride = false;
        readStreamHeader();
        bin.setBlockDataMode(true);
    }

    protected ObjectInputStream() throws IOException, SecurityException {
        bin = null;
        handles = null;
        vlist = null;
        enableOverride = true;
    }

    public final Object readObject() throws IOException, ClassNotFoundException {
        if (enableOverride) {
            return readObjectOverride();
        }
        int outerHandle = passHandle;
        try {
            Object obj = readObject0(false);
            handles.markDependency(outerHandle, passHandle);
            ClassNotFoundException ex = handles.lookupException(passHandle);
            if (ex != null) {
                throw ex;
            }
            if (depth == 0) {
                vlist.doCallbacks();
            }
            return obj;
        } finally {
            passHandle = outerHandle;
            if (depth == 0) {
                vlist.clear();
                if (closed) {
                    clear();
                }
            }
        }
    }

    protected Object readObjectOverride() throws IOException, ClassNotFoundException {
        return null;
    }

    public Object readUnshared() throws IOException, ClassNotFoundException {
        int outerHandle = passHandle;
        try {
            Object obj = readObject0(true);
            handles.markDependency(outerHandle, passHandle);
            ClassNotFoundException ex = handles.lookupException(passHandle);
            if (ex != null) {
                throw ex;
            }
            if (depth == 0) {
                vlist.doCallbacks();
            }
            return obj;
        } finally {
            passHandle = outerHandle;
            if (depth == 0) {
                vlist.clear();
                if (closed) {
                    clear();
                }
            }
        }
    }

    public void defaultReadObject() throws IOException, ClassNotFoundException {
        ObjectOutputStream.SerialCallbackContext ctx = curContext;
        if (ctx == null) {
            throw new NotActiveException("not in call to readObject");
        }
        Object curObj = ctx.getObj();
        ObjectStreamClass curDesc = ctx.getDesc();
        bin.setBlockDataMode(false);
        defaultReadFields(curObj, curDesc);
        bin.setBlockDataMode(true);
        if (!curDesc.hasWriteObjectData()) {
            defaultDataEnd = true;
        }
        ClassNotFoundException ex = handles.lookupException(passHandle);
        if (ex != null) {
            throw ex;
        }
    }

    public ObjectInputStream.GetField readFields() throws IOException, ClassNotFoundException {
        ObjectOutputStream.SerialCallbackContext ctx = curContext;
        if (ctx == null) {
            throw new NotActiveException("not in call to readObject");
        }
        ctx.getObj();
        ObjectStreamClass curDesc = ctx.getDesc();
        bin.setBlockDataMode(false);
        GetFieldImpl getField = new GetFieldImpl(curDesc);
        getField.readFields();
        bin.setBlockDataMode(true);
        if (!curDesc.hasWriteObjectData()) {
            defaultDataEnd = true;
        }
        return getField;
    }

    public void registerValidation(ObjectInputValidation obj, int prio)
            throws NotActiveException, InvalidObjectException {
        if (depth == 0) {
            throw new NotActiveException("stream inactive");
        }
        vlist.register(obj, prio);
    }

    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        String name = desc.getName();
        try {
            return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException ex) {
            Class<?> cl = primClasses.get(name);
            if (cl != null) {
                return cl;
            }
            throw ex;
        }
    }

    protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?>[] classObjs = new Class<?>[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            classObjs[i] = Class.forName(interfaces[i], false, loader);
        }
        try {
            return Proxy.getProxyClass(loader, classObjs);
        } catch (IllegalArgumentException e) {
            throw new ClassNotFoundException(null, e);
        }
    }

    protected Object resolveObject(Object obj) throws IOException {
        return obj;
    }

    protected boolean enableResolveObject(boolean enable) throws SecurityException {
        if (enable == enableResolve) {
            return enable;
        }
        enableResolve = enable;
        return !enableResolve;
    }

    protected void readStreamHeader() throws IOException, StreamCorruptedException {
        short s0 = bin.readShort();
        short s1 = bin.readShort();
        if (s0 != STREAM_MAGIC || s1 != STREAM_VERSION) {
            throw new StreamCorruptedException(String.format("invalid stream header: %04X%04X", s0, s1));
        }
    }

    protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
        ObjectStreamClass desc = new ObjectStreamClass();
        desc.readNonProxy(this);
        return desc;
    }

    public int read() throws IOException {
        return bin.read();
    }

    public int read(byte[] buf, int off, int len) throws IOException {
        if (buf == null) {
            throw new NullPointerException();
        }
        int endoff = off + len;
        if (off < 0 || len < 0 || endoff > buf.length || endoff < 0) {
            throw new IndexOutOfBoundsException();
        }
        return bin.read(buf, off, len);
    }

    public int available() throws IOException {
        return bin.available();
    }

    public void close() throws IOException {
        closed = true;
        if (depth == 0) {
            clear();
        }
        bin.close();
    }

    public boolean readBoolean() throws IOException {
        return bin.readBoolean();
    }

    public byte readByte() throws IOException {
        return bin.readByte();
    }

    public int readUnsignedByte() throws IOException {
        return bin.readUnsignedByte();
    }

    public char readChar() throws IOException {
        return bin.readChar();
    }

    public short readShort() throws IOException {
        return bin.readShort();
    }

    public int readUnsignedShort() throws IOException {
        return bin.readUnsignedShort();
    }

    public int readInt() throws IOException {
        return bin.readInt();
    }

    public long readLong() throws IOException {
        return bin.readLong();
    }

    public float readFloat() throws IOException {
        return bin.readFloat();
    }

    public double readDouble() throws IOException {
        return bin.readDouble();
    }

    public void readFully(byte[] buf) throws IOException {
        bin.readFully(buf, 0, buf.length);
    }

    public void readFully(byte[] buf, int off, int len) throws IOException {
        int endoff = off + len;
        if (off < 0 || len < 0 || endoff > buf.length || endoff < 0) {
            throw new IndexOutOfBoundsException();
        }
        bin.readFully(buf, off, len);
    }

    public int skipBytes(int len) throws IOException {
        return bin.skipBytes(len);
    }

    @Deprecated
    public String readLine() throws IOException {
        return bin.readLine();
    }

    public String readUTF() throws IOException {
        return bin.readUTF();
    }

    /** A field type string from a class descriptor: a string, or a back reference to one. */
    String readTypeString() throws IOException {
        int oldHandle = passHandle;
        try {
            byte tc = bin.peekByte();
            switch (tc) {
                case TC_NULL:
                    return (String) readNull();
                case TC_REFERENCE:
                    return (String) readHandle(false);
                case TC_STRING:
                case TC_LONGSTRING:
                    return readString(false);
                default:
                    throw new StreamCorruptedException(String.format("invalid type code: %02X", tc));
            }
        } finally {
            passHandle = oldHandle;
        }
    }

    private void clear() {
        handles.clear();
        vlist.clear();
    }

    private void handleReset() throws StreamCorruptedException {
        if (depth > 0) {
            throw new StreamCorruptedException("unexpected reset; recursion depth: " + depth);
        }
        clear();
    }

    private Object readObject0(boolean unshared) throws IOException {
        boolean oldMode = bin.getBlockDataMode();
        if (oldMode) {
            int remain = bin.currentBlockRemaining();
            if (remain > 0) {
                throw new OptionalDataException(remain);
            } else if (defaultDataEnd) {
                // The class was written without writeObject, so its data has no end marker: this is its end.
                throw new OptionalDataException(true);
            }
            bin.setBlockDataMode(false);
        }

        byte tc;
        while ((tc = bin.peekByte()) == TC_RESET) {
            bin.readByte();
            handleReset();
        }

        depth++;
        totalObjectRefs++;
        try {
            switch (tc) {
                case TC_NULL:
                    return readNull();
                case TC_REFERENCE:
                    return readHandle(unshared);
                case TC_CLASS:
                    return readClass(unshared);
                case TC_CLASSDESC:
                case TC_PROXYCLASSDESC:
                    return readClassDesc(unshared);
                case TC_STRING:
                case TC_LONGSTRING:
                    return checkResolve(readString(unshared));
                case TC_ARRAY:
                    return checkResolve(readArray(unshared));
                case TC_ENUM:
                    return checkResolve(readEnum(unshared));
                case TC_OBJECT:
                    return checkResolve(readOrdinaryObject(unshared));
                case TC_EXCEPTION:
                    IOException ex = readFatalException();
                    throw new WriteAbortedException("writing aborted", ex);
                case TC_BLOCKDATA:
                case TC_BLOCKDATALONG:
                    if (oldMode) {
                        bin.setBlockDataMode(true);
                        bin.peek();
                        throw new OptionalDataException(bin.currentBlockRemaining());
                    } else {
                        throw new StreamCorruptedException("unexpected block data");
                    }
                case TC_ENDBLOCKDATA:
                    if (oldMode) {
                        throw new OptionalDataException(true);
                    } else {
                        throw new StreamCorruptedException("unexpected end of block data");
                    }
                default:
                    throw new StreamCorruptedException(String.format("invalid type code: %02X", tc));
            }
        } finally {
            depth--;
            bin.setBlockDataMode(oldMode);
        }
    }

    private Object checkResolve(Object obj) throws IOException {
        if (!enableResolve || handles.lookupException(passHandle) != null) {
            return obj;
        }
        Object rep = resolveObject(obj);
        if (rep != obj) {
            handles.setObject(passHandle, rep);
        }
        return rep;
    }

    private Object readNull() throws IOException {
        if (bin.readByte() != TC_NULL) {
            throw new InternalError();
        }
        passHandle = NULL_HANDLE;
        return null;
    }

    private Object readHandle(boolean unshared) throws IOException {
        if (bin.readByte() != TC_REFERENCE) {
            throw new InternalError();
        }
        passHandle = bin.readInt() - baseWireHandle;
        if (passHandle < 0 || passHandle >= handles.size()) {
            throw new StreamCorruptedException(
                    String.format("invalid handle value: %08X", passHandle + baseWireHandle));
        }
        if (unshared) {
            throw new InvalidObjectException("cannot read back reference as unshared");
        }
        Object rep = handles.lookupObject(passHandle);
        if (rep == unsharedMarker) {
            throw new InvalidObjectException("cannot read back reference to unshared object");
        }
        return rep;
    }

    private Class<?> readClass(boolean unshared) throws IOException {
        if (bin.readByte() != TC_CLASS) {
            throw new InternalError();
        }
        ObjectStreamClass desc = readClassDesc(false);
        Class<?> cl = desc.forClass();
        passHandle = handles.assign(unshared ? unsharedMarker : cl);
        ClassNotFoundException resolveEx = desc.getResolveException();
        if (resolveEx != null) {
            handles.markException(passHandle, resolveEx);
        }
        handles.finish(passHandle);
        return cl;
    }

    private ObjectStreamClass readClassDesc(boolean unshared) throws IOException {
        byte tc = bin.peekByte();
        switch (tc) {
            case TC_NULL:
                return (ObjectStreamClass) readNull();
            case TC_PROXYCLASSDESC:
                return readProxyDesc(unshared);
            case TC_CLASSDESC:
                return readNonProxyDesc(unshared);
            case TC_REFERENCE:
                Object d = readHandle(unshared);
                if (!(d instanceof ObjectStreamClass)) {
                    throw new StreamCorruptedException("reference to a non-class descriptor");
                }
                ((ObjectStreamClass) d).checkInitialized();
                return (ObjectStreamClass) d;
            default:
                throw new StreamCorruptedException(String.format("invalid type code: %02X", tc));
        }
    }

    private ObjectStreamClass readProxyDesc(boolean unshared) throws IOException {
        if (bin.readByte() != TC_PROXYCLASSDESC) {
            throw new InternalError();
        }
        ObjectStreamClass desc = new ObjectStreamClass();
        int descHandle = handles.assign(unshared ? unsharedMarker : desc);
        passHandle = NULL_HANDLE;

        int numIfaces = bin.readInt();
        if (numIfaces > 65535) {
            throw new InvalidObjectException("interface limit exceeded: " + numIfaces);
        }
        String[] ifaces = new String[numIfaces];
        for (int i = 0; i < numIfaces; i++) {
            ifaces[i] = bin.readUTF();
        }

        Class<?> cl = null;
        ClassNotFoundException resolveEx = null;
        bin.setBlockDataMode(true);
        try {
            if ((cl = resolveProxyClass(ifaces)) == null) {
                resolveEx = new ClassNotFoundException("null class");
            } else if (!Proxy.isProxyClass(cl)) {
                throw new InvalidClassException("Not a proxy");
            }
        } catch (ClassNotFoundException ex) {
            resolveEx = ex;
        }
        skipCustomData();

        try {
            totalObjectRefs++;
            depth++;
            desc.initProxy(cl, resolveEx, readClassDesc(false));
        } finally {
            depth--;
        }
        handles.finish(descHandle);
        passHandle = descHandle;
        return desc;
    }

    private ObjectStreamClass readNonProxyDesc(boolean unshared) throws IOException {
        if (bin.readByte() != TC_CLASSDESC) {
            throw new InternalError();
        }
        ObjectStreamClass desc = new ObjectStreamClass();
        int descHandle = handles.assign(unshared ? unsharedMarker : desc);
        passHandle = NULL_HANDLE;

        ObjectStreamClass readDesc;
        try {
            readDesc = readClassDescriptor();
        } catch (ClassNotFoundException ex) {
            throw (IOException) new InvalidClassException("failed to read class descriptor").initCause(ex);
        }

        Class<?> cl = null;
        ClassNotFoundException resolveEx = null;
        bin.setBlockDataMode(true);
        try {
            if ((cl = resolveClass(readDesc)) == null) {
                resolveEx = new ClassNotFoundException("null class");
            }
        } catch (ClassNotFoundException ex) {
            resolveEx = ex;
        }
        skipCustomData();

        try {
            totalObjectRefs++;
            depth++;
            desc.initNonProxy(readDesc, cl, resolveEx, readClassDesc(false));
        } finally {
            depth--;
        }
        handles.finish(descHandle);
        passHandle = descHandle;
        return desc;
    }

    private String readString(boolean unshared) throws IOException {
        String str;
        byte tc = bin.readByte();
        switch (tc) {
            case TC_STRING:
                str = bin.readUTF();
                break;
            case TC_LONGSTRING:
                str = bin.readLongUTF();
                break;
            default:
                throw new StreamCorruptedException(String.format("invalid type code: %02X", tc));
        }
        passHandle = handles.assign(unshared ? unsharedMarker : str);
        handles.finish(passHandle);
        return str;
    }

    private Object readArray(boolean unshared) throws IOException {
        if (bin.readByte() != TC_ARRAY) {
            throw new InternalError();
        }
        ObjectStreamClass desc = readClassDesc(false);
        int len = bin.readInt();
        if (len < 0) {
            throw new StreamCorruptedException("Array length is negative");
        }

        Object array = null;
        Class<?> cl;
        Class<?> ccl = null;
        if ((cl = desc.forClass()) != null) {
            ccl = cl.getComponentType();
            array = Array.newInstance(ccl, len);
        }

        int arrayHandle = handles.assign(unshared ? unsharedMarker : array);
        ClassNotFoundException resolveEx = desc.getResolveException();
        if (resolveEx != null) {
            handles.markException(arrayHandle, resolveEx);
        }

        if (ccl == null) {
            for (int i = 0; i < len; i++) {
                readObject0(false);
            }
        } else if (ccl.isPrimitive()) {
            if (ccl == Integer.TYPE) {
                int[] a = (int[]) array;
                for (int i = 0; i < len; i++) {
                    a[i] = bin.readInt();
                }
            } else if (ccl == Byte.TYPE) {
                bin.readFully((byte[]) array, 0, len);
            } else if (ccl == Long.TYPE) {
                long[] a = (long[]) array;
                for (int i = 0; i < len; i++) {
                    a[i] = bin.readLong();
                }
            } else if (ccl == Float.TYPE) {
                float[] a = (float[]) array;
                for (int i = 0; i < len; i++) {
                    a[i] = bin.readFloat();
                }
            } else if (ccl == Double.TYPE) {
                double[] a = (double[]) array;
                for (int i = 0; i < len; i++) {
                    a[i] = bin.readDouble();
                }
            } else if (ccl == Short.TYPE) {
                short[] a = (short[]) array;
                for (int i = 0; i < len; i++) {
                    a[i] = bin.readShort();
                }
            } else if (ccl == Character.TYPE) {
                char[] a = (char[]) array;
                for (int i = 0; i < len; i++) {
                    a[i] = bin.readChar();
                }
            } else if (ccl == Boolean.TYPE) {
                boolean[] a = (boolean[]) array;
                for (int i = 0; i < len; i++) {
                    a[i] = bin.readBoolean();
                }
            } else {
                throw new InternalError();
            }
        } else {
            Object[] oa = (Object[]) array;
            for (int i = 0; i < len; i++) {
                Object v = readObject0(false);
                handles.markDependency(arrayHandle, passHandle);
                if (v != null && !ccl.isInstance(v)) {
                    throw new ClassCastException("cannot store " + v.getClass().getName() + " in an array of "
                            + ccl.getName());
                }
                oa[i] = v;
            }
        }

        handles.finish(arrayHandle);
        passHandle = arrayHandle;
        return array;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Enum<?> readEnum(boolean unshared) throws IOException {
        if (bin.readByte() != TC_ENUM) {
            throw new InternalError();
        }
        ObjectStreamClass desc = readClassDesc(false);
        if (!desc.isEnum()) {
            throw new InvalidClassException("non-enum class: " + desc);
        }

        int enumHandle = handles.assign(unshared ? unsharedMarker : null);
        ClassNotFoundException resolveEx = desc.getResolveException();
        if (resolveEx != null) {
            handles.markException(enumHandle, resolveEx);
        }

        String name = readString(false);
        Enum<?> result = null;
        Class<?> cl = desc.forClass();
        if (cl != null) {
            try {
                result = Enum.valueOf((Class) cl, name);
            } catch (IllegalArgumentException ex) {
                throw (IOException) new InvalidObjectException("enum constant " + name + " does not exist in " + cl)
                        .initCause(ex);
            }
            if (!unshared) {
                handles.setObject(enumHandle, result);
            }
        }

        handles.finish(enumHandle);
        passHandle = enumHandle;
        return result;
    }

    private Object readOrdinaryObject(boolean unshared) throws IOException {
        if (bin.readByte() != TC_OBJECT) {
            throw new InternalError();
        }
        ObjectStreamClass desc = readClassDesc(false);
        desc.checkDeserialize();

        Class<?> cl = desc.forClass();
        if (cl == String.class || cl == Class.class || cl == ObjectStreamClass.class) {
            throw new InvalidClassException("invalid class descriptor");
        }

        Object obj;
        try {
            obj = desc.isInstantiable() ? desc.newInstance() : null;
        } catch (InstantiationException ex) {
            throw (IOException) new InvalidClassException(desc.forClass().getName(), "unable to create instance")
                    .initCause(ex);
        } catch (InvocationTargetException ex) {
            throw (IOException) new InvalidClassException(desc.forClass().getName(), "unable to create instance")
                    .initCause(ex.getCause());
        }

        passHandle = handles.assign(unshared ? unsharedMarker : obj);
        ClassNotFoundException resolveEx = desc.getResolveException();
        if (resolveEx != null) {
            handles.markException(passHandle, resolveEx);
        }

        if (desc.isExternalizable()) {
            readExternalData((Externalizable) obj, desc);
        } else {
            readSerialData(obj, desc);
        }

        handles.finish(passHandle);

        if (obj != null && handles.lookupException(passHandle) == null && desc.hasReadResolveMethod()) {
            Object rep = desc.invokeReadResolve(obj);
            if (unshared && rep != null && rep.getClass().isArray()) {
                rep = cloneArray(rep);
            }
            if (rep != obj) {
                handles.setObject(passHandle, obj = rep);
            }
        }
        return obj;
    }

    private void readExternalData(Externalizable obj, ObjectStreamClass desc) throws IOException {
        ObjectOutputStream.SerialCallbackContext oldContext = curContext;
        if (oldContext != null) {
            oldContext.check();
        }
        curContext = null;
        try {
            boolean blocked = desc.hasBlockExternalData();
            if (blocked) {
                bin.setBlockDataMode(true);
            }
            if (obj != null) {
                try {
                    obj.readExternal(this);
                } catch (ClassNotFoundException ex) {
                    handles.markException(passHandle, ex);
                }
            }
            if (blocked) {
                skipCustomData();
            }
        } finally {
            if (oldContext != null) {
                oldContext.check();
            }
            curContext = oldContext;
        }
    }

    private void readSerialData(Object obj, ObjectStreamClass desc) throws IOException {
        ObjectStreamClass.ClassDataSlot[] slots = desc.getClassDataLayout();
        for (ObjectStreamClass.ClassDataSlot slot : slots) {
            ObjectStreamClass slotDesc = slot.desc;
            if (slot.hasData) {
                if (obj == null || handles.lookupException(passHandle) != null) {
                    // Nothing to read into: consume the default fields, and skipCustomData drops the rest.
                    defaultReadFields(null, slotDesc);
                } else if (slotDesc.hasReadObjectMethod()) {
                    ObjectOutputStream.SerialCallbackContext oldContext = curContext;
                    if (oldContext != null) {
                        oldContext.check();
                    }
                    try {
                        curContext = new ObjectOutputStream.SerialCallbackContext(obj, slotDesc);
                        bin.setBlockDataMode(true);
                        slotDesc.invokeReadObject(obj, this);
                    } catch (ClassNotFoundException ex) {
                        handles.markException(passHandle, ex);
                    } finally {
                        curContext.setUsed();
                        if (oldContext != null) {
                            oldContext.check();
                        }
                        curContext = oldContext;
                    }
                    defaultDataEnd = false;
                } else {
                    defaultReadFields(obj, slotDesc);
                }

                if (slotDesc.hasWriteObjectData()) {
                    skipCustomData();
                } else {
                    bin.setBlockDataMode(false);
                }
            } else if (obj != null && slotDesc.hasReadObjectNoDataMethod()
                    && handles.lookupException(passHandle) == null) {
                slotDesc.invokeReadObjectNoData(obj);
            }
        }
    }

    /** Skips the rest of a class's custom data (blocks and objects) through its TC_ENDBLOCKDATA. */
    private void skipCustomData() throws IOException {
        int oldHandle = passHandle;
        for (;;) {
            if (bin.getBlockDataMode()) {
                bin.skipBlockData();
                bin.setBlockDataMode(false);
            }
            switch (bin.peekByte()) {
                case TC_BLOCKDATA:
                case TC_BLOCKDATALONG:
                    bin.setBlockDataMode(true);
                    break;
                case TC_ENDBLOCKDATA:
                    bin.readByte();
                    passHandle = oldHandle;
                    return;
                default:
                    readObject0(false);
                    break;
            }
        }
    }

    private void defaultReadFields(Object obj, ObjectStreamClass desc) throws IOException {
        Class<?> cl = desc.forClass();
        if (cl != null && obj != null && !cl.isInstance(obj)) {
            throw new ClassCastException();
        }

        int primDataSize = desc.getPrimDataSize();
        if (primDataSize > 0) {
            byte[] primVals = new byte[primDataSize];
            bin.readFully(primVals, 0, primDataSize);
            if (obj != null) {
                desc.setPrimFieldValues(obj, primVals);
            }
        }

        int objHandle = passHandle;
        ObjectStreamField[] fields = desc.getFields(false);
        Object[] objVals = new Object[desc.getNumObjFields()];
        int numPrimFields = fields.length - objVals.length;
        for (int i = 0; i < objVals.length; i++) {
            ObjectStreamField f = fields[numPrimFields + i];
            objVals[i] = readObject0(f.isUnshared());
            if (f.getField() != null) {
                handles.markDependency(objHandle, passHandle);
            }
        }
        if (obj != null) {
            desc.setObjFieldValues(obj, objVals);
        }
        passHandle = objHandle;
    }

    private IOException readFatalException() throws IOException {
        if (bin.readByte() != TC_EXCEPTION) {
            throw new InternalError();
        }
        clear();
        Object ex = readObject0(false);
        clear();
        if (!(ex instanceof IOException)) {
            throw new StreamCorruptedException("exception in stream is not an IOException");
        }
        return (IOException) ex;
    }

    private static Object cloneArray(Object array) {
        if (array instanceof Object[]) {
            return ((Object[]) array).clone();
        } else if (array instanceof boolean[]) {
            return ((boolean[]) array).clone();
        } else if (array instanceof byte[]) {
            return ((byte[]) array).clone();
        } else if (array instanceof char[]) {
            return ((char[]) array).clone();
        } else if (array instanceof double[]) {
            return ((double[]) array).clone();
        } else if (array instanceof float[]) {
            return ((float[]) array).clone();
        } else if (array instanceof int[]) {
            return ((int[]) array).clone();
        } else if (array instanceof long[]) {
            return ((long[]) array).clone();
        } else if (array instanceof short[]) {
            return ((short[]) array).clone();
        }
        throw new AssertionError();
    }

    private class GetFieldImpl extends GetField {
        private final ObjectStreamClass desc;
        private final byte[] primVals;
        private final Object[] objVals;
        private final int[] objHandles;

        GetFieldImpl(ObjectStreamClass desc) {
            this.desc = desc;
            primVals = new byte[desc.getPrimDataSize()];
            objVals = new Object[desc.getNumObjFields()];
            objHandles = new int[objVals.length];
        }

        public ObjectStreamClass getObjectStreamClass() {
            return desc;
        }

        public boolean defaulted(String name) throws IOException {
            return getFieldOffset(name, null) < 0;
        }

        public boolean get(String name, boolean val) throws IOException {
            int off = getFieldOffset(name, Boolean.TYPE);
            return (off >= 0) ? (primVals[off] != 0) : val;
        }

        public byte get(String name, byte val) throws IOException {
            int off = getFieldOffset(name, Byte.TYPE);
            return (off >= 0) ? primVals[off] : val;
        }

        public char get(String name, char val) throws IOException {
            int off = getFieldOffset(name, Character.TYPE);
            return (off >= 0) ? (char) ObjectStreamClass.getShort(primVals, off) : val;
        }

        public short get(String name, short val) throws IOException {
            int off = getFieldOffset(name, Short.TYPE);
            return (off >= 0) ? ObjectStreamClass.getShort(primVals, off) : val;
        }

        public int get(String name, int val) throws IOException {
            int off = getFieldOffset(name, Integer.TYPE);
            return (off >= 0) ? ObjectStreamClass.getInt(primVals, off) : val;
        }

        public float get(String name, float val) throws IOException {
            int off = getFieldOffset(name, Float.TYPE);
            return (off >= 0) ? Float.intBitsToFloat(ObjectStreamClass.getInt(primVals, off)) : val;
        }

        public long get(String name, long val) throws IOException {
            int off = getFieldOffset(name, Long.TYPE);
            return (off >= 0) ? ObjectStreamClass.getLong(primVals, off) : val;
        }

        public double get(String name, double val) throws IOException {
            int off = getFieldOffset(name, Double.TYPE);
            return (off >= 0) ? Double.longBitsToDouble(ObjectStreamClass.getLong(primVals, off)) : val;
        }

        public Object get(String name, Object val) throws IOException, ClassNotFoundException {
            int off = getFieldOffset(name, Object.class);
            if (off >= 0) {
                int objHandle = objHandles[off];
                handles.markDependency(passHandle, objHandle);
                ClassNotFoundException ex = handles.lookupException(objHandle);
                if (ex != null) {
                    throw ex;
                }
                return objVals[off];
            }
            return val;
        }

        void readFields() throws IOException {
            bin.readFully(primVals, 0, primVals.length);

            int oldHandle = passHandle;
            ObjectStreamField[] fields = desc.getFields(false);
            int numPrimFields = fields.length - objVals.length;
            for (int i = 0; i < objVals.length; i++) {
                objVals[i] = readObject0(fields[numPrimFields + i].isUnshared());
                objHandles[i] = passHandle;
            }
            passHandle = oldHandle;
        }

        /** The field's offset; -1 if the stream has no such field but the local class does. */
        private int getFieldOffset(String name, Class<?> type) {
            ObjectStreamField field = desc.getField(name, type);
            if (field != null) {
                return field.getOffset();
            } else if (desc.getLocalDesc() != null && desc.getLocalDesc().getField(name, type) != null) {
                return -1;
            }
            throw new IllegalArgumentException("no such field " + name + " with type " + type);
        }
    }

    /** Validation callbacks, run once the top-level readObject has finished: higher priority first. */
    private static final class ValidationList {
        private static final class Callback {
            final ObjectInputValidation obj;
            final int priority;

            Callback(ObjectInputValidation obj, int priority) {
                this.obj = obj;
                this.priority = priority;
            }
        }

        private final ArrayList<Callback> list = new ArrayList<Callback>();

        void register(ObjectInputValidation obj, int priority) throws InvalidObjectException {
            if (obj == null) {
                throw new InvalidObjectException("null callback");
            }
            int i = 0;
            while (i < list.size() && priority < list.get(i).priority) {
                i++;
            }
            list.add(i, new Callback(obj, priority));
        }

        void doCallbacks() throws InvalidObjectException {
            try {
                while (!list.isEmpty()) {
                    list.remove(0).obj.validateObject();
                }
            } catch (InvalidObjectException ex) {
                list.clear();
                throw ex;
            }
        }

        void clear() {
            list.clear();
        }
    }

    /**
     * Objects by wire handle, each with a status: still being read, done, or failed with a ClassNotFoundException
     * that also fails every object that refers to it.
     */
    private static final class HandleTable {
        private static final byte STATUS_OK = 1;
        private static final byte STATUS_UNKNOWN = 2;
        private static final byte STATUS_EXCEPTION = 3;

        private byte[] status = new byte[16];
        private Object[] entries = new Object[16];
        private int size;

        int assign(Object obj) {
            if (size >= entries.length) {
                int n = entries.length << 1;
                Object[] e = new Object[n];
                System.arraycopy(entries, 0, e, 0, size);
                entries = e;
                byte[] s = new byte[n];
                System.arraycopy(status, 0, s, 0, size);
                status = s;
            }
            status[size] = STATUS_UNKNOWN;
            entries[size] = obj;
            return size++;
        }

        void markDependency(int dependent, int target) {
            if (dependent == NULL_HANDLE || target == NULL_HANDLE) {
                return;
            }
            if (status[target] == STATUS_EXCEPTION && status[dependent] != STATUS_EXCEPTION) {
                markException(dependent, (ClassNotFoundException) entries[target]);
            }
        }

        void markException(int handle, ClassNotFoundException ex) {
            if (status[handle] != STATUS_EXCEPTION) {
                status[handle] = STATUS_EXCEPTION;
                entries[handle] = ex;
            }
        }

        void finish(int handle) {
            if (status[handle] == STATUS_UNKNOWN) {
                status[handle] = STATUS_OK;
            }
        }

        void setObject(int handle, Object obj) {
            if (status[handle] != STATUS_EXCEPTION) {
                entries[handle] = obj;
            }
        }

        Object lookupObject(int handle) {
            return (handle != NULL_HANDLE && status[handle] != STATUS_EXCEPTION) ? entries[handle] : null;
        }

        ClassNotFoundException lookupException(int handle) {
            return (handle != NULL_HANDLE && status[handle] == STATUS_EXCEPTION)
                    ? (ClassNotFoundException) entries[handle] : null;
        }

        void clear() {
            java.util.Arrays.fill(entries, 0, size, null);
            size = 0;
        }

        int size() {
            return size;
        }
    }

    /**
     * The input side of block data mode. In block mode, reads come from TC_BLOCKDATA/TC_BLOCKDATALONG blocks and
     * stop (return -1) where the custom data ends; outside it, bytes come straight from the stream.
     */
    private final class BlockDataInputStream extends InputStream {
        private final InputStream in;
        private int peekb = -1;
        private long totalBytesRead;
        private boolean blkmode;
        /** Bytes left in the current block; -1 at the end of the block data. */
        private int unread;

        BlockDataInputStream(InputStream in) {
            this.in = in;
        }

        long getBytesRead() {
            return totalBytesRead;
        }

        boolean getBlockDataMode() {
            return blkmode;
        }

        /** Sets the mode and returns the previous one. Leaving block mode with block bytes unread is a bug. */
        boolean setBlockDataMode(boolean newmode) throws IOException {
            if (blkmode == newmode) {
                return blkmode;
            }
            if (newmode) {
                unread = 0;
            } else if (unread > 0) {
                throw new IllegalStateException("unread block data");
            }
            blkmode = newmode;
            return !blkmode;
        }

        private int rawPeek() throws IOException {
            if (peekb < 0) {
                peekb = in.read();
                if (peekb >= 0) {
                    totalBytesRead++;
                }
            }
            return peekb;
        }

        private int rawRead() throws IOException {
            if (peekb >= 0) {
                int v = peekb;
                peekb = -1;
                return v;
            }
            int v = in.read();
            if (v >= 0) {
                totalBytesRead++;
            }
            return v;
        }

        private int rawRead(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (peekb >= 0) {
                b[off] = (byte) peekb;
                peekb = -1;
                return 1;
            }
            int n = in.read(b, off, len);
            if (n > 0) {
                totalBytesRead += n;
            }
            return n;
        }

        private int rawReadByte() throws IOException {
            int v = rawRead();
            if (v < 0) {
                throw new EOFException();
            }
            return v;
        }

        /** Moves to the next block if the current one is used up; false at the end of the block data. */
        private boolean nextBlock(boolean canBlock) throws IOException {
            while (unread == 0) {
                if (defaultDataEnd) {
                    unread = -1;
                    break;
                }
                if (!canBlock && peekb < 0 && in.available() == 0) {
                    return false;
                }
                int tc = rawPeek();
                if (tc == TC_BLOCKDATA) {
                    rawRead();
                    unread = rawReadByte();
                } else if (tc == TC_BLOCKDATALONG) {
                    rawRead();
                    int len = (rawReadByte() << 24) | (rawReadByte() << 16) | (rawReadByte() << 8) | rawReadByte();
                    if (len < 0) {
                        throw new StreamCorruptedException("illegal block data header length: " + len);
                    }
                    unread = len;
                } else if (tc == TC_RESET) {
                    rawRead();
                    handleReset();
                } else {
                    if (tc >= 0 && (tc < TC_BASE || tc > TC_MAX)) {
                        throw new StreamCorruptedException(String.format("invalid type code: %02X", tc));
                    }
                    unread = -1;
                }
            }
            return unread > 0;
        }

        /** Bytes left in the current block, or 0 outside block mode or at the end of the block data. */
        int currentBlockRemaining() {
            return (blkmode && unread > 0) ? unread : 0;
        }

        /** Skips the rest of the block data, up to the next non-block item. */
        void skipBlockData() throws IOException {
            if (!blkmode) {
                throw new IllegalStateException("not in block data mode");
            }
            for (;;) {
                if (unread < 0) {
                    return;
                }
                while (unread > 0) {
                    if (rawRead() < 0) {
                        throw new StreamCorruptedException("unexpected EOF in middle of data block");
                    }
                    unread--;
                }
                nextBlock(true);
            }
        }

        int peek() throws IOException {
            if (blkmode) {
                return nextBlock(true) ? rawPeek() : -1;
            }
            return rawPeek();
        }

        byte peekByte() throws IOException {
            int val = peek();
            if (val < 0) {
                throw new EOFException();
            }
            return (byte) val;
        }

        public int read() throws IOException {
            if (blkmode) {
                if (!nextBlock(true)) {
                    return -1;
                }
                int v = rawRead();
                if (v < 0) {
                    throw new StreamCorruptedException("unexpected EOF in middle of data block");
                }
                unread--;
                return v;
            }
            return rawRead();
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (blkmode) {
                if (!nextBlock(true)) {
                    return -1;
                }
                int n = rawRead(b, off, Math.min(len, unread));
                if (n < 0) {
                    throw new StreamCorruptedException("unexpected EOF in middle of data block");
                }
                unread -= n;
                return n;
            }
            return rawRead(b, off, len);
        }

        public long skip(long len) throws IOException {
            long remain = len;
            byte[] tmp = new byte[512];
            while (remain > 0) {
                int n = read(tmp, 0, (int) Math.min(remain, tmp.length));
                if (n < 0) {
                    break;
                }
                remain -= n;
            }
            return len - remain;
        }

        public int available() throws IOException {
            if (blkmode) {
                if (unread == 0) {
                    nextBlock(false);
                }
                return (unread > 0) ? Math.min(in.available() + (peekb >= 0 ? 1 : 0), unread) : 0;
            }
            return in.available() + (peekb >= 0 ? 1 : 0);
        }

        public void close() throws IOException {
            if (blkmode) {
                unread = -1;
            }
            in.close();
        }

        void readFully(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                int n = read(b, off, len);
                if (n < 0) {
                    throw new EOFException();
                }
                off += n;
                len -= n;
            }
        }

        int skipBytes(int n) throws IOException {
            return (int) skip(n);
        }

        boolean readBoolean() throws IOException {
            return readUnsignedByte() != 0;
        }

        byte readByte() throws IOException {
            return (byte) readUnsignedByte();
        }

        int readUnsignedByte() throws IOException {
            int v = read();
            if (v < 0) {
                throw new EOFException();
            }
            return v;
        }

        char readChar() throws IOException {
            return (char) readUnsignedShort();
        }

        short readShort() throws IOException {
            return (short) readUnsignedShort();
        }

        int readUnsignedShort() throws IOException {
            int a = readUnsignedByte();
            int b = readUnsignedByte();
            return (a << 8) | b;
        }

        int readInt() throws IOException {
            return (readUnsignedShort() << 16) | readUnsignedShort();
        }

        float readFloat() throws IOException {
            return Float.intBitsToFloat(readInt());
        }

        long readLong() throws IOException {
            return ((long) readInt() << 32) | (readInt() & 0xFFFFFFFFL);
        }

        double readDouble() throws IOException {
            return Double.longBitsToDouble(readLong());
        }

        String readUTF() throws IOException {
            return readUTFBody(readUnsignedShort());
        }

        String readLongUTF() throws IOException {
            return readUTFBody(readLong());
        }

        private String readUTFBody(long utflen) throws IOException {
            if (utflen > Integer.MAX_VALUE) {
                throw new UTFDataFormatException("string too long: " + utflen);
            }
            int len = (int) utflen;
            byte[] bytes = new byte[Math.min(len, 1 << 16)];
            StringBuilder sb = new StringBuilder(Math.min(len, 1 << 16));
            int pending = 0;
            int pendingLen = 0;
            while (len > 0) {
                int n = Math.min(len, bytes.length);
                readFully(bytes, 0, n);
                len -= n;
                for (int i = 0; i < n; i++) {
                    int b = bytes[i] & 0xFF;
                    if (pendingLen > 0) {
                        if ((b & 0xC0) != 0x80) {
                            throw new UTFDataFormatException();
                        }
                        pending = (pending << 6) | (b & 0x3F);
                        if (--pendingLen == 0) {
                            sb.append((char) pending);
                        }
                    } else if (b < 0x80) {
                        sb.append((char) b);
                    } else if ((b & 0xE0) == 0xC0) {
                        pending = b & 0x1F;
                        pendingLen = 1;
                    } else if ((b & 0xF0) == 0xE0) {
                        pending = b & 0x0F;
                        pendingLen = 2;
                    } else {
                        throw new UTFDataFormatException();
                    }
                }
            }
            if (pendingLen > 0) {
                throw new UTFDataFormatException();
            }
            return sb.toString();
        }

        String readLine() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (;;) {
                int v = read();
                if (v < 0) {
                    return (sb.length() > 0) ? sb.toString() : null;
                } else if (v == '\n') {
                    return sb.toString();
                } else if (v == '\r') {
                    if (peek() == '\n') {
                        read();
                    }
                    return sb.toString();
                }
                sb.append((char) v);
            }
        }
    }
}
