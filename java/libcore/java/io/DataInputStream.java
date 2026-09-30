package java.io;

public class DataInputStream extends FilterInputStream implements DataInput {
    public DataInputStream(InputStream in) {
        super(in);
    }

    private final byte[] readBuffer = new byte[8];

    public final int read(byte[] b) throws IOException {
        return in.read(b, 0, b.length);
    }

    public final int read(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
    }

    public final void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    public final void readFully(byte[] b, int off, int len) throws IOException {
        if (len < 0) {
            throw new IndexOutOfBoundsException();
        }
        int n = 0;
        while (n < len) {
            int count = in.read(b, off + n, len - n);
            if (count < 0) {
                throw new EOFException();
            }
            n += count;
        }
    }

    public final int skipBytes(int n) throws IOException {
        int total = 0;
        int cur;
        while ((total < n) && ((cur = (int) in.skip(n - total)) > 0)) {
            total += cur;
        }
        return total;
    }

    public final boolean readBoolean() throws IOException {
        int ch = in.read();
        if (ch < 0) {
            throw new EOFException();
        }
        return (ch != 0);
    }

    public final byte readByte() throws IOException {
        int ch = in.read();
        if (ch < 0) {
            throw new EOFException();
        }
        return (byte) (ch);
    }

    public final int readUnsignedByte() throws IOException {
        int ch = in.read();
        if (ch < 0) {
            throw new EOFException();
        }
        return ch;
    }

    public final short readShort() throws IOException {
        readFully(readBuffer, 0, 2);
        return (short) (((readBuffer[0] & 0xff) << 8) | (readBuffer[1] & 0xff));
    }

    public final int readUnsignedShort() throws IOException {
        readFully(readBuffer, 0, 2);
        return ((readBuffer[0] & 0xff) << 8) | (readBuffer[1] & 0xff);
    }

    public final char readChar() throws IOException {
        return (char) readUnsignedShort();
    }

    public final int readInt() throws IOException {
        readFully(readBuffer, 0, 4);
        return ((readBuffer[0] & 0xff) << 24) | ((readBuffer[1] & 0xff) << 16) | ((readBuffer[2] & 0xff) << 8)
                | (readBuffer[3] & 0xff);
    }

    public final long readLong() throws IOException {
        readFully(readBuffer, 0, 8);
        return (((long) readBuffer[0] << 56) + ((long) (readBuffer[1] & 255) << 48) + ((long) (readBuffer[2] & 255) << 40)
                + ((long) (readBuffer[3] & 255) << 32) + ((long) (readBuffer[4] & 255) << 24) + ((readBuffer[5] & 255) << 16)
                + ((readBuffer[6] & 255) << 8) + ((readBuffer[7] & 255) << 0));
    }

    public final float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    public final double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    @Deprecated
    public final String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while (true) {
            c = in.read();
            if (c == -1) {
                return sb.length() == 0 ? null : sb.toString();
            }
            if (c == '\n') {
                return sb.toString();
            }
            if (c == '\r') {
                continue;
            }
            sb.append((char) c);
        }
    }

    public final String readUTF() throws IOException {
        return readUTF(this);
    }

    public static final String readUTF(DataInput in) throws IOException {
        int utflen = in.readUnsignedShort();
        byte[] bytearr = new byte[utflen];
        char[] chararr = new char[utflen];
        in.readFully(bytearr, 0, utflen);
        int c, char2, char3;
        int count = 0;
        int chararrCount = 0;
        while (count < utflen) {
            c = (int) bytearr[count] & 0xff;
            switch (c >> 4) {
                case 0: case 1: case 2: case 3: case 4: case 5: case 6: case 7:
                    count++;
                    chararr[chararrCount++] = (char) c;
                    break;
                case 12: case 13:
                    count += 2;
                    if (count > utflen) {
                        throw new UTFDataFormatException("malformed input: partial character at end");
                    }
                    char2 = (int) bytearr[count - 1];
                    chararr[chararrCount++] = (char) (((c & 0x1F) << 6) | (char2 & 0x3F));
                    break;
                case 14:
                    count += 3;
                    if (count > utflen) {
                        throw new UTFDataFormatException("malformed input: partial character at end");
                    }
                    char2 = (int) bytearr[count - 2];
                    char3 = (int) bytearr[count - 1];
                    chararr[chararrCount++] = (char) (((c & 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F) << 0));
                    break;
                default:
                    throw new UTFDataFormatException("malformed input around byte " + count);
            }
        }
        return new String(chararr, 0, chararrCount);
    }
}
