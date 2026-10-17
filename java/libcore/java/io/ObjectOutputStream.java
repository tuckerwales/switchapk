package java.io;

import java.util.IdentityHashMap;

/**
 * Writes the Java serialization stream format (protocol version 2 by default), byte for byte what OpenJDK and
 * Android write for the same objects and class shapes.
 */
public class ObjectOutputStream extends OutputStream implements ObjectOutput, ObjectStreamConstants {

    /** Callback state for one writeObject or readObject call: the object and class, usable once. */
    static final class SerialCallbackContext {
        private final Object obj;
        private final ObjectStreamClass desc;
        private Thread thread;

        SerialCallbackContext(Object obj, ObjectStreamClass desc) {
            this.obj = obj;
            this.desc = desc;
            this.thread = Thread.currentThread();
        }

        Object getObj() throws NotActiveException {
            if (thread != Thread.currentThread()) {
                throw new NotActiveException("not in readObject invocation or fields already read");
            }
            thread = null;
            return obj;
        }

        ObjectStreamClass getDesc() {
            return desc;
        }

        void check() throws NotActiveException {
            if (thread != null && thread != Thread.currentThread()) {
                throw new NotActiveException(
                        "expected thread: " + thread + ", but got: " + Thread.currentThread());
            }
        }

        void setUsed() {
            thread = null;
        }
    }

    public static abstract class PutField {
        public PutField() {
        }

        public abstract void put(String name, boolean val);

        public abstract void put(String name, byte val);

        public abstract void put(String name, char val);

        public abstract void put(String name, short val);

        public abstract void put(String name, int val);

        public abstract void put(String name, long val);

        public abstract void put(String name, float val);

        public abstract void put(String name, double val);

        public abstract void put(String name, Object val);

        @Deprecated
        public abstract void write(ObjectOutput out) throws IOException;
    }

    private final BlockDataOutputStream bout;
    private final IdentityHashMap<Object, Integer> handles;
    private int nextHandle;
    private final IdentityHashMap<Object, Object> subs;
    private int protocol = PROTOCOL_VERSION_2;
    private int depth;
    private final boolean enableOverride;
    private boolean enableReplace;
    private SerialCallbackContext curContext;
    private PutFieldImpl curPut;

    public ObjectOutputStream(OutputStream out) throws IOException {
        bout = new BlockDataOutputStream(out);
        handles = new IdentityHashMap<Object, Integer>();
        subs = new IdentityHashMap<Object, Object>();
        enableOverride = false;
        writeStreamHeader();
        bout.setBlockDataMode(true);
    }

    protected ObjectOutputStream() throws IOException, SecurityException {
        bout = null;
        handles = null;
        subs = null;
        enableOverride = true;
    }

    public void useProtocolVersion(int version) throws IOException {
        if (nextHandle != 0) {
            throw new IllegalStateException("stream non-empty");
        }
        switch (version) {
            case PROTOCOL_VERSION_1:
            case PROTOCOL_VERSION_2:
                protocol = version;
                break;
            default:
                throw new IllegalArgumentException("unknown version: " + version);
        }
    }

    int getProtocolVersion() {
        return protocol;
    }

    public final void writeObject(Object obj) throws IOException {
        if (enableOverride) {
            writeObjectOverride(obj);
            return;
        }
        try {
            writeObject0(obj, false);
        } catch (IOException ex) {
            if (depth == 0) {
                writeFatalException(ex);
            }
            throw ex;
        }
    }

    protected void writeObjectOverride(Object obj) throws IOException {
    }

    public void writeUnshared(Object obj) throws IOException {
        try {
            writeObject0(obj, true);
        } catch (IOException ex) {
            if (depth == 0) {
                writeFatalException(ex);
            }
            throw ex;
        }
    }

    public void defaultWriteObject() throws IOException {
        SerialCallbackContext ctx = curContext;
        if (ctx == null) {
            throw new NotActiveException("not in call to writeObject");
        }
        Object curObj = ctx.getObj();
        ObjectStreamClass curDesc = ctx.getDesc();
        bout.setBlockDataMode(false);
        defaultWriteFields(curObj, curDesc);
        bout.setBlockDataMode(true);
    }

    public ObjectOutputStream.PutField putFields() throws IOException {
        if (curPut == null) {
            SerialCallbackContext ctx = curContext;
            if (ctx == null) {
                throw new NotActiveException("not in call to writeObject");
            }
            ctx.getObj();
            curPut = new PutFieldImpl(ctx.getDesc());
        }
        return curPut;
    }

    public void writeFields() throws IOException {
        if (curPut == null) {
            throw new NotActiveException("no current PutField object");
        }
        bout.setBlockDataMode(false);
        curPut.writeFields();
        bout.setBlockDataMode(true);
    }

    public void reset() throws IOException {
        if (depth != 0) {
            throw new IOException("stream active");
        }
        bout.setBlockDataMode(false);
        bout.writeByte(TC_RESET);
        clear();
        bout.setBlockDataMode(true);
    }

    protected void annotateClass(Class<?> cl) throws IOException {
    }

    protected void annotateProxyClass(Class<?> cl) throws IOException {
    }

    protected Object replaceObject(Object obj) throws IOException {
        return obj;
    }

    protected boolean enableReplaceObject(boolean enable) throws SecurityException {
        if (enable == enableReplace) {
            return enable;
        }
        enableReplace = enable;
        return !enableReplace;
    }

    protected void writeStreamHeader() throws IOException {
        bout.writeShort(STREAM_MAGIC);
        bout.writeShort(STREAM_VERSION);
    }

    protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
        desc.writeNonProxy(this);
    }

    public void write(int val) throws IOException {
        bout.write(val);
    }

    public void write(byte[] buf) throws IOException {
        bout.write(buf, 0, buf.length);
    }

    public void write(byte[] buf, int off, int len) throws IOException {
        if (buf == null) {
            throw new NullPointerException();
        }
        int endoff = off + len;
        if (off < 0 || len < 0 || endoff > buf.length || endoff < 0) {
            throw new IndexOutOfBoundsException();
        }
        bout.write(buf, off, len);
    }

    public void flush() throws IOException {
        bout.flush();
    }

    protected void drain() throws IOException {
        bout.drain();
    }

    public void close() throws IOException {
        flush();
        clear();
        bout.close();
    }

    public void writeBoolean(boolean val) throws IOException {
        bout.writeBoolean(val);
    }

    public void writeByte(int val) throws IOException {
        bout.writeByte(val);
    }

    public void writeShort(int val) throws IOException {
        bout.writeShort(val);
    }

    public void writeChar(int val) throws IOException {
        bout.writeChar(val);
    }

    public void writeInt(int val) throws IOException {
        bout.writeInt(val);
    }

    public void writeLong(long val) throws IOException {
        bout.writeLong(val);
    }

    public void writeFloat(float val) throws IOException {
        bout.writeFloat(val);
    }

    public void writeDouble(double val) throws IOException {
        bout.writeDouble(val);
    }

    public void writeBytes(String str) throws IOException {
        bout.writeBytes(str);
    }

    public void writeChars(String str) throws IOException {
        bout.writeChars(str);
    }

    public void writeUTF(String str) throws IOException {
        bout.writeUTF(str);
    }

    /** A class descriptor's field type string: shared through the handle table like any string. */
    void writeTypeString(String str) throws IOException {
        int handle;
        if (str == null) {
            writeNull();
        } else if ((handle = lookupHandle(str)) != -1) {
            writeHandle(handle);
        } else {
            writeString(str, false);
        }
    }

    private void clear() {
        subs.clear();
        handles.clear();
        nextHandle = 0;
    }

    private int assignHandle(Object obj) {
        int h = nextHandle++;
        if (obj != null) {
            handles.put(obj, Integer.valueOf(h));
        }
        return h;
    }

    private int lookupHandle(Object obj) {
        Integer h = handles.get(obj);
        return (h != null) ? h.intValue() : -1;
    }

    private void writeObject0(Object obj, boolean unshared) throws IOException {
        boolean oldMode = bout.setBlockDataMode(false);
        depth++;
        try {
            int h;
            if (obj != null && subs.containsKey(obj)) {
                obj = subs.get(obj);
            }
            if (obj == null) {
                writeNull();
                return;
            } else if (!unshared && (h = lookupHandle(obj)) != -1) {
                writeHandle(h);
                return;
            } else if (obj instanceof Class) {
                writeClass((Class<?>) obj, unshared);
                return;
            } else if (obj instanceof ObjectStreamClass) {
                writeClassDesc((ObjectStreamClass) obj, unshared);
                return;
            }

            Object orig = obj;
            Class<?> cl = obj.getClass();
            ObjectStreamClass desc;
            for (;;) {
                Class<?> repCl;
                desc = ObjectStreamClass.lookup(cl, true);
                if (!desc.hasWriteReplaceMethod() || (obj = desc.invokeWriteReplace(obj)) == null
                        || (repCl = obj.getClass()) == cl) {
                    break;
                }
                cl = repCl;
            }
            if (enableReplace) {
                Object rep = replaceObject(obj);
                if (rep != obj && rep != null) {
                    cl = rep.getClass();
                    desc = ObjectStreamClass.lookup(cl, true);
                }
                obj = rep;
            }

            if (obj != orig) {
                subs.put(orig, obj);
                if (obj == null) {
                    writeNull();
                    return;
                } else if (!unshared && (h = lookupHandle(obj)) != -1) {
                    writeHandle(h);
                    return;
                } else if (obj instanceof Class) {
                    writeClass((Class<?>) obj, unshared);
                    return;
                } else if (obj instanceof ObjectStreamClass) {
                    writeClassDesc((ObjectStreamClass) obj, unshared);
                    return;
                }
            }

            if (obj instanceof String) {
                writeString((String) obj, unshared);
            } else if (cl.isArray()) {
                writeArray(obj, desc, unshared);
            } else if (obj instanceof Enum) {
                writeEnum((Enum<?>) obj, desc, unshared);
            } else if (obj instanceof Serializable) {
                writeOrdinaryObject(obj, desc, unshared);
            } else {
                throw new NotSerializableException(cl.getName());
            }
        } finally {
            depth--;
            bout.setBlockDataMode(oldMode);
        }
    }

    private void writeNull() throws IOException {
        bout.writeByte(TC_NULL);
    }

    private void writeHandle(int handle) throws IOException {
        bout.writeByte(TC_REFERENCE);
        bout.writeInt(baseWireHandle + handle);
    }

    private void writeClass(Class<?> cl, boolean unshared) throws IOException {
        bout.writeByte(TC_CLASS);
        writeClassDesc(ObjectStreamClass.lookup(cl, true), false);
        assignHandle(unshared ? null : cl);
    }

    private void writeClassDesc(ObjectStreamClass desc, boolean unshared) throws IOException {
        int handle;
        if (desc == null) {
            writeNull();
        } else if (!unshared && (handle = lookupHandle(desc)) != -1) {
            writeHandle(handle);
        } else if (desc.isProxy()) {
            writeProxyDesc(desc, unshared);
        } else {
            writeNonProxyDesc(desc, unshared);
        }
    }

    private void writeProxyDesc(ObjectStreamClass desc, boolean unshared) throws IOException {
        bout.writeByte(TC_PROXYCLASSDESC);
        assignHandle(unshared ? null : desc);

        Class<?> cl = desc.forClass();
        Class<?>[] ifaces = cl.getInterfaces();
        bout.writeInt(ifaces.length);
        for (Class<?> iface : ifaces) {
            bout.writeUTF(iface.getName());
        }

        bout.setBlockDataMode(true);
        annotateProxyClass(cl);
        bout.setBlockDataMode(false);
        bout.writeByte(TC_ENDBLOCKDATA);

        writeClassDesc(desc.getSuperDesc(), false);
    }

    private void writeNonProxyDesc(ObjectStreamClass desc, boolean unshared) throws IOException {
        bout.writeByte(TC_CLASSDESC);
        assignHandle(unshared ? null : desc);

        if (protocol == PROTOCOL_VERSION_1) {
            desc.writeNonProxy(this);
        } else {
            writeClassDescriptor(desc);
        }

        Class<?> cl = desc.forClass();
        bout.setBlockDataMode(true);
        annotateClass(cl);
        bout.setBlockDataMode(false);
        bout.writeByte(TC_ENDBLOCKDATA);

        writeClassDesc(desc.getSuperDesc(), false);
    }

    private void writeString(String str, boolean unshared) throws IOException {
        assignHandle(unshared ? null : str);
        long utflen = BlockDataOutputStream.getUTFLength(str);
        if (utflen <= 0xFFFF) {
            bout.writeByte(TC_STRING);
            bout.writeShort((int) utflen);
        } else {
            bout.writeByte(TC_LONGSTRING);
            bout.writeLong(utflen);
        }
        bout.writeUTFBody(str);
    }

    private void writeArray(Object array, ObjectStreamClass desc, boolean unshared) throws IOException {
        bout.writeByte(TC_ARRAY);
        writeClassDesc(desc, false);
        assignHandle(unshared ? null : array);

        Class<?> ccl = desc.forClass().getComponentType();
        if (ccl.isPrimitive()) {
            if (ccl == Integer.TYPE) {
                int[] ia = (int[]) array;
                bout.writeInt(ia.length);
                for (int v : ia) {
                    bout.writeInt(v);
                }
            } else if (ccl == Byte.TYPE) {
                byte[] ba = (byte[]) array;
                bout.writeInt(ba.length);
                bout.write(ba, 0, ba.length);
            } else if (ccl == Long.TYPE) {
                long[] ja = (long[]) array;
                bout.writeInt(ja.length);
                for (long v : ja) {
                    bout.writeLong(v);
                }
            } else if (ccl == Float.TYPE) {
                float[] fa = (float[]) array;
                bout.writeInt(fa.length);
                for (float v : fa) {
                    bout.writeFloat(v);
                }
            } else if (ccl == Double.TYPE) {
                double[] da = (double[]) array;
                bout.writeInt(da.length);
                for (double v : da) {
                    bout.writeDouble(v);
                }
            } else if (ccl == Short.TYPE) {
                short[] sa = (short[]) array;
                bout.writeInt(sa.length);
                for (short v : sa) {
                    bout.writeShort(v);
                }
            } else if (ccl == Character.TYPE) {
                char[] ca = (char[]) array;
                bout.writeInt(ca.length);
                for (char v : ca) {
                    bout.writeChar(v);
                }
            } else if (ccl == Boolean.TYPE) {
                boolean[] za = (boolean[]) array;
                bout.writeInt(za.length);
                for (boolean v : za) {
                    bout.writeBoolean(v);
                }
            } else {
                throw new InternalError();
            }
        } else {
            Object[] objs = (Object[]) array;
            int len = objs.length;
            bout.writeInt(len);
            for (int i = 0; i < len; i++) {
                writeObject0(objs[i], false);
            }
        }
    }

    private void writeEnum(Enum<?> en, ObjectStreamClass desc, boolean unshared) throws IOException {
        bout.writeByte(TC_ENUM);
        ObjectStreamClass sdesc = desc.getSuperDesc();
        writeClassDesc((sdesc.forClass() == Enum.class) ? desc : sdesc, false);
        assignHandle(unshared ? null : en);
        writeString(en.name(), false);
    }

    private void writeOrdinaryObject(Object obj, ObjectStreamClass desc, boolean unshared) throws IOException {
        desc.checkSerialize();
        bout.writeByte(TC_OBJECT);
        writeClassDesc(desc, false);
        assignHandle(unshared ? null : obj);
        if (desc.isExternalizable() && !desc.isProxy()) {
            writeExternalData((Externalizable) obj);
        } else {
            writeSerialData(obj, desc);
        }
    }

    private void writeExternalData(Externalizable obj) throws IOException {
        PutFieldImpl oldPut = curPut;
        curPut = null;
        SerialCallbackContext oldContext = curContext;
        try {
            curContext = null;
            if (protocol == PROTOCOL_VERSION_1) {
                obj.writeExternal(this);
            } else {
                bout.setBlockDataMode(true);
                obj.writeExternal(this);
                bout.setBlockDataMode(false);
                bout.writeByte(TC_ENDBLOCKDATA);
            }
        } finally {
            curContext = oldContext;
        }
        curPut = oldPut;
    }

    private void writeSerialData(Object obj, ObjectStreamClass desc) throws IOException {
        ObjectStreamClass.ClassDataSlot[] slots = desc.getClassDataLayout();
        for (ObjectStreamClass.ClassDataSlot slot : slots) {
            ObjectStreamClass slotDesc = slot.desc;
            if (slotDesc.hasWriteObjectMethod()) {
                PutFieldImpl oldPut = curPut;
                curPut = null;
                SerialCallbackContext oldContext = curContext;
                try {
                    curContext = new SerialCallbackContext(obj, slotDesc);
                    bout.setBlockDataMode(true);
                    slotDesc.invokeWriteObject(obj, this);
                    bout.setBlockDataMode(false);
                    bout.writeByte(TC_ENDBLOCKDATA);
                } finally {
                    curContext.setUsed();
                    curContext = oldContext;
                }
                curPut = oldPut;
            } else {
                defaultWriteFields(obj, slotDesc);
            }
        }
    }

    private void defaultWriteFields(Object obj, ObjectStreamClass desc) throws IOException {
        Class<?> cl = desc.forClass();
        if (cl != null && obj != null && !cl.isInstance(obj)) {
            throw new ClassCastException();
        }
        desc.checkDefaultSerialize();

        int primDataSize = desc.getPrimDataSize();
        if (primDataSize > 0) {
            byte[] primVals = new byte[primDataSize];
            desc.getPrimFieldValues(obj, primVals);
            bout.write(primVals, 0, primDataSize);
        }

        ObjectStreamField[] fields = desc.getFields(false);
        Object[] objVals = new Object[desc.getNumObjFields()];
        int numPrimFields = fields.length - objVals.length;
        desc.getObjFieldValues(obj, objVals);
        for (int i = 0; i < objVals.length; i++) {
            writeObject0(objVals[i], fields[numPrimFields + i].isUnshared());
        }
    }

    private void writeFatalException(IOException ex) throws IOException {
        clear();
        boolean oldMode = bout.setBlockDataMode(false);
        try {
            bout.writeByte(TC_EXCEPTION);
            writeObject0(ex, false);
            clear();
        } finally {
            bout.setBlockDataMode(oldMode);
        }
    }

    private class PutFieldImpl extends PutField {
        private final ObjectStreamClass desc;
        private final byte[] primVals;
        private final Object[] objVals;

        PutFieldImpl(ObjectStreamClass desc) {
            this.desc = desc;
            primVals = new byte[desc.getPrimDataSize()];
            objVals = new Object[desc.getNumObjFields()];
        }

        public void put(String name, boolean val) {
            primVals[getFieldOffset(name, Boolean.TYPE)] = (byte) (val ? 1 : 0);
        }

        public void put(String name, byte val) {
            primVals[getFieldOffset(name, Byte.TYPE)] = val;
        }

        public void put(String name, char val) {
            ObjectStreamClass.putShort(primVals, getFieldOffset(name, Character.TYPE), val);
        }

        public void put(String name, short val) {
            ObjectStreamClass.putShort(primVals, getFieldOffset(name, Short.TYPE), val);
        }

        public void put(String name, int val) {
            ObjectStreamClass.putInt(primVals, getFieldOffset(name, Integer.TYPE), val);
        }

        public void put(String name, float val) {
            ObjectStreamClass.putInt(primVals, getFieldOffset(name, Float.TYPE), Float.floatToIntBits(val));
        }

        public void put(String name, long val) {
            ObjectStreamClass.putLong(primVals, getFieldOffset(name, Long.TYPE), val);
        }

        public void put(String name, double val) {
            ObjectStreamClass.putLong(primVals, getFieldOffset(name, Double.TYPE), Double.doubleToLongBits(val));
        }

        public void put(String name, Object val) {
            objVals[getFieldOffset(name, Object.class)] = val;
        }

        @Deprecated
        public void write(ObjectOutput out) throws IOException {
            if (ObjectOutputStream.this != out) {
                throw new IllegalArgumentException("wrong stream");
            }
            out.write(primVals, 0, primVals.length);

            ObjectStreamField[] fields = desc.getFields(false);
            int numPrimFields = fields.length - objVals.length;
            for (int i = 0; i < objVals.length; i++) {
                if (fields[numPrimFields + i].isUnshared()) {
                    throw new IOException("cannot write unshared object");
                }
                out.writeObject(objVals[i]);
            }
        }

        void writeFields() throws IOException {
            bout.write(primVals, 0, primVals.length);

            ObjectStreamField[] fields = desc.getFields(false);
            int numPrimFields = fields.length - objVals.length;
            for (int i = 0; i < objVals.length; i++) {
                writeObject0(objVals[i], fields[numPrimFields + i].isUnshared());
            }
        }

        private int getFieldOffset(String name, Class<?> type) {
            ObjectStreamField field = desc.getField(name, type);
            if (field == null) {
                throw new IllegalArgumentException("no such field " + name + " with type " + type);
            }
            return field.getOffset();
        }
    }

    /**
     * Buffers output and, in block data mode, frames it as TC_BLOCKDATA/TC_BLOCKDATALONG blocks of up to 1024 bytes.
     * A block is written when the buffer fills and the next byte arrives, and on mode changes and flushes, which is
     * where Java's writer cuts blocks too.
     */
    private static final class BlockDataOutputStream extends OutputStream {
        private static final int MAX_BLOCK_SIZE = 1024;

        private final byte[] buf = new byte[MAX_BLOCK_SIZE];
        private final byte[] hbuf = new byte[5];
        private int pos;
        private final OutputStream out;
        private boolean blkmode;

        BlockDataOutputStream(OutputStream out) {
            this.out = out;
        }

        /** Sets the mode and returns the previous one. */
        boolean setBlockDataMode(boolean mode) throws IOException {
            if (blkmode == mode) {
                return blkmode;
            }
            drain();
            blkmode = mode;
            return !blkmode;
        }

        public void write(int b) throws IOException {
            if (pos >= MAX_BLOCK_SIZE) {
                drain();
            }
            buf[pos++] = (byte) b;
        }

        public void write(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                if (pos >= MAX_BLOCK_SIZE) {
                    drain();
                }
                int wlen = Math.min(len, MAX_BLOCK_SIZE - pos);
                System.arraycopy(b, off, buf, pos, wlen);
                pos += wlen;
                off += wlen;
                len -= wlen;
            }
        }

        public void flush() throws IOException {
            drain();
            out.flush();
        }

        public void close() throws IOException {
            flush();
            out.close();
        }

        void drain() throws IOException {
            if (pos == 0) {
                return;
            }
            if (blkmode) {
                if (pos <= 0xFF) {
                    hbuf[0] = TC_BLOCKDATA;
                    hbuf[1] = (byte) pos;
                    out.write(hbuf, 0, 2);
                } else {
                    hbuf[0] = TC_BLOCKDATALONG;
                    ObjectStreamClass.putInt(hbuf, 1, pos);
                    out.write(hbuf, 0, 5);
                }
            }
            out.write(buf, 0, pos);
            pos = 0;
        }

        void writeBoolean(boolean v) throws IOException {
            write(v ? 1 : 0);
        }

        void writeByte(int v) throws IOException {
            write(v);
        }

        void writeShort(int v) throws IOException {
            if (pos + 2 <= MAX_BLOCK_SIZE) {
                ObjectStreamClass.putShort(buf, pos, v);
                pos += 2;
            } else {
                write(v >>> 8);
                write(v);
            }
        }

        void writeChar(int v) throws IOException {
            writeShort(v);
        }

        void writeInt(int v) throws IOException {
            if (pos + 4 <= MAX_BLOCK_SIZE) {
                ObjectStreamClass.putInt(buf, pos, v);
                pos += 4;
            } else {
                writeShort(v >>> 16);
                writeShort(v);
            }
        }

        void writeFloat(float v) throws IOException {
            writeInt(Float.floatToIntBits(v));
        }

        void writeLong(long v) throws IOException {
            writeInt((int) (v >>> 32));
            writeInt((int) v);
        }

        void writeDouble(double v) throws IOException {
            writeLong(Double.doubleToLongBits(v));
        }

        void writeBytes(String s) throws IOException {
            int len = s.length();
            for (int i = 0; i < len; i++) {
                write(s.charAt(i));
            }
        }

        void writeChars(String s) throws IOException {
            int len = s.length();
            for (int i = 0; i < len; i++) {
                writeShort(s.charAt(i));
            }
        }

        void writeUTF(String s) throws IOException {
            long utflen = getUTFLength(s);
            if (utflen > 0xFFFFL) {
                throw new UTFDataFormatException();
            }
            writeShort((int) utflen);
            writeUTFBody(s);
        }

        static long getUTFLength(String s) {
            int len = s.length();
            long utflen = 0;
            for (int i = 0; i < len; i++) {
                char c = s.charAt(i);
                if (c >= 0x0001 && c <= 0x007F) {
                    utflen++;
                } else if (c > 0x07FF) {
                    utflen += 3;
                } else {
                    utflen += 2;
                }
            }
            return utflen;
        }

        /** Modified UTF-8, as DataOutput.writeUTF writes it, without the length. */
        void writeUTFBody(String s) throws IOException {
            int len = s.length();
            for (int i = 0; i < len; i++) {
                char c = s.charAt(i);
                if (c >= 0x0001 && c <= 0x007F) {
                    write(c);
                } else if (c > 0x07FF) {
                    write(0xE0 | ((c >> 12) & 0x0F));
                    write(0x80 | ((c >> 6) & 0x3F));
                    write(0x80 | (c & 0x3F));
                } else {
                    write(0xC0 | ((c >> 6) & 0x1F));
                    write(0x80 | (c & 0x3F));
                }
            }
        }
    }
}
