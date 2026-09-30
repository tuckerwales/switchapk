package java.io;

import libcore.io.Os;

public class RandomAccessFile implements DataOutput, DataInput, Closeable {
    private final FileDescriptor fd;
    private boolean closed;

    public RandomAccessFile(String name, String mode) throws FileNotFoundException {
        this(new File(name), mode);
    }

    public RandomAccessFile(File file, String mode) throws FileNotFoundException {
        int flags;
        if (mode.equals("r")) {
            flags = Os.O_RDONLY;
        } else if (mode.startsWith("rw")) {
            flags = Os.O_RDWR | Os.O_CREAT;
        } else {
            throw new IllegalArgumentException("Invalid mode: " + mode);
        }
        fd = new FileDescriptor(Os.open(file.getPath(), flags, 0666));
    }

    public final FileDescriptor getFD() throws IOException {
        return fd;
    }

    public java.nio.channels.FileChannel getChannel() {
        return java.nio.channels.FileChannel.forFd(fd.fd, true, true);
    }

    public int read() throws IOException {
        byte[] b = new byte[1];
        return read(b, 0, 1) <= 0 ? -1 : b[0] & 0xff;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        return Os.read(fd.fd, b, off, len);
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public final void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    public final void readFully(byte[] b, int off, int len) throws IOException {
        int n = 0;
        do {
            int count = this.read(b, off + n, len - n);
            if (count < 0) {
                throw new EOFException();
            }
            n += count;
        } while (n < len);
    }

    public int skipBytes(int n) throws IOException {
        long pos = getFilePointer();
        long len = length();
        long newpos = Math.min(pos + n, len);
        seek(newpos);
        return (int) (newpos - pos);
    }

    public void write(int b) throws IOException {
        write(new byte[] {(byte) b}, 0, 1);
    }

    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        Os.write(fd.fd, b, off, len);
    }

    public long getFilePointer() throws IOException {
        return Os.seek(fd.fd, 0, Os.SEEK_CUR);
    }

    public void seek(long pos) throws IOException {
        if (pos < 0) {
            throw new IOException("Negative seek offset");
        }
        Os.seek(fd.fd, pos, Os.SEEK_SET);
    }

    public long length() throws IOException {
        long cur = getFilePointer();
        long end = Os.seek(fd.fd, 0, Os.SEEK_END);
        Os.seek(fd.fd, cur, Os.SEEK_SET);
        return end;
    }

    public void setLength(long newLength) throws IOException {
        Os.ftruncate(fd.fd, newLength);
    }

    public void close() throws IOException {
        if (!closed) {
            closed = true;
            Os.close(fd.fd);
        }
    }

    private final DataInputStream din = new DataInputStream(new InputStream() {
        public int read() throws IOException {
            return RandomAccessFile.this.read();
        }

        public int read(byte[] b, int off, int len) throws IOException {
            return RandomAccessFile.this.read(b, off, len);
        }
    });
    private final DataOutputStream dout = new DataOutputStream(new OutputStream() {
        public void write(int b) throws IOException {
            RandomAccessFile.this.write(b);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            RandomAccessFile.this.write(b, off, len);
        }
    });

    public final boolean readBoolean() throws IOException {
        return din.readBoolean();
    }

    public final byte readByte() throws IOException {
        return din.readByte();
    }

    public final int readUnsignedByte() throws IOException {
        return din.readUnsignedByte();
    }

    public final short readShort() throws IOException {
        return din.readShort();
    }

    public final int readUnsignedShort() throws IOException {
        return din.readUnsignedShort();
    }

    public final char readChar() throws IOException {
        return din.readChar();
    }

    public final int readInt() throws IOException {
        return din.readInt();
    }

    public final long readLong() throws IOException {
        return din.readLong();
    }

    public final float readFloat() throws IOException {
        return din.readFloat();
    }

    public final double readDouble() throws IOException {
        return din.readDouble();
    }

    @SuppressWarnings("deprecation")
    public final String readLine() throws IOException {
        StringBuilder input = new StringBuilder();
        int c = -1;
        boolean eol = false;
        while (!eol) {
            switch (c = read()) {
                case -1:
                case '\n':
                    eol = true;
                    break;
                case '\r':
                    eol = true;
                    long cur = getFilePointer();
                    if ((read()) != '\n') {
                        seek(cur);
                    }
                    break;
                default:
                    input.append((char) c);
                    break;
            }
        }
        if ((c == -1) && (input.length() == 0)) {
            return null;
        }
        return input.toString();
    }

    public final String readUTF() throws IOException {
        return din.readUTF();
    }

    public final void writeBoolean(boolean v) throws IOException {
        dout.writeBoolean(v);
    }

    public final void writeByte(int v) throws IOException {
        dout.writeByte(v);
    }

    public final void writeShort(int v) throws IOException {
        dout.writeShort(v);
    }

    public final void writeChar(int v) throws IOException {
        dout.writeChar(v);
    }

    public final void writeInt(int v) throws IOException {
        dout.writeInt(v);
    }

    public final void writeLong(long v) throws IOException {
        dout.writeLong(v);
    }

    public final void writeFloat(float v) throws IOException {
        dout.writeFloat(v);
    }

    public final void writeDouble(double v) throws IOException {
        dout.writeDouble(v);
    }

    public final void writeBytes(String s) throws IOException {
        dout.writeBytes(s);
    }

    public final void writeChars(String s) throws IOException {
        dout.writeChars(s);
    }

    public final void writeUTF(String str) throws IOException {
        dout.writeUTF(str);
    }
}
