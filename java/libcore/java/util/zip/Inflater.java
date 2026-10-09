package java.util.zip;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

/**
 * zlib inflater. The stream lives in native memory (`address`, freed by end()); there are no finalizers, so a
 * stream that is never ended leaks its zlib state. Input stays in the caller's array between calls.
 */
public class Inflater {
    private long address;
    private byte[] input = EMPTY;
    private int inputPos;
    private int inputLim;
    private ByteBuffer inputBuffer;
    private boolean finished;
    private boolean needDict;
    private long bytesRead;
    private long bytesWritten;

    private static final byte[] EMPTY = new byte[0];

    public Inflater(boolean nowrap) {
        address = init(nowrap);
    }

    public Inflater() {
        this(false);
    }

    public void setInput(byte[] input, int off, int len) {
        if (off < 0 || len < 0 || off > input.length - len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        synchronized (this) {
            this.input = input;
            this.inputPos = off;
            this.inputLim = off + len;
            this.inputBuffer = null;
        }
    }

    public void setInput(byte[] input) {
        setInput(input, 0, input.length);
    }

    public void setInput(ByteBuffer input) {
        if (input == null) {
            throw new NullPointerException();
        }
        synchronized (this) {
            int rem = Math.max(input.limit() - input.position(), 0);
            if (input.hasArray() && !input.isReadOnly()) {
                this.input = input.array();
                this.inputPos = input.arrayOffset() + input.position();
                this.inputLim = this.inputPos + rem;
                this.inputBuffer = input;
            } else {
                byte[] copy = new byte[rem];
                input.get(copy);
                this.input = copy;
                this.inputPos = 0;
                this.inputLim = rem;
                this.inputBuffer = null;
            }
        }
    }

    public void setDictionary(byte[] dictionary, int off, int len) {
        if (off < 0 || len < 0 || off > dictionary.length - len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        synchronized (this) {
            ensureOpen();
            setDictionary(address, dictionary, off, len);
            needDict = false;
        }
    }

    public void setDictionary(byte[] dictionary) {
        setDictionary(dictionary, 0, dictionary.length);
    }

    public void setDictionary(ByteBuffer dictionary) {
        byte[] b = new byte[dictionary.remaining()];
        dictionary.get(b);
        setDictionary(b, 0, b.length);
    }

    public synchronized int getRemaining() {
        return inputLim - inputPos;
    }

    public synchronized boolean needsInput() {
        return inputLim - inputPos <= 0;
    }

    public synchronized boolean needsDictionary() {
        return needDict;
    }

    public synchronized boolean finished() {
        return finished;
    }

    public int inflate(byte[] output, int off, int len) throws DataFormatException {
        if (off < 0 || len < 0 || off > output.length - len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        synchronized (this) {
            ensureOpen();
            if (finished || needDict) {
                return 0;
            }
            long r = inflateBytes(address, input, inputPos, inputLim - inputPos, output, off, len);
            int written = (int) (r & 0x7fffffff);
            int read = (int) ((r >>> 31) & 0x7fffffff);
            inputPos += read;
            if (inputBuffer != null) {
                inputBuffer.position(inputBuffer.position() + read);
            }
            bytesRead += read;
            bytesWritten += written;
            if ((r & (1L << 62)) != 0) {
                finished = true;
            }
            if ((r & (1L << 63)) != 0) {
                needDict = true;
            }
            return written;
        }
    }

    public int inflate(byte[] output) throws DataFormatException {
        return inflate(output, 0, output.length);
    }

    public int inflate(ByteBuffer output) throws DataFormatException {
        if (output.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        int rem = Math.max(output.limit() - output.position(), 0);
        if (output.hasArray()) {
            int n = inflate(output.array(), output.arrayOffset() + output.position(), rem);
            output.position(output.position() + n);
            return n;
        }
        byte[] b = new byte[rem];
        int n = inflate(b, 0, rem);
        output.put(b, 0, n);
        return n;
    }

    public synchronized int getAdler() {
        ensureOpen();
        return getAdler(address);
    }

    public int getTotalIn() {
        return (int) getBytesRead();
    }

    public synchronized long getBytesRead() {
        ensureOpen();
        return bytesRead;
    }

    public int getTotalOut() {
        return (int) getBytesWritten();
    }

    public synchronized long getBytesWritten() {
        ensureOpen();
        return bytesWritten;
    }

    public synchronized void reset() {
        ensureOpen();
        reset(address);
        input = EMPTY;
        inputPos = inputLim = 0;
        inputBuffer = null;
        finished = false;
        needDict = false;
        bytesRead = bytesWritten = 0;
    }

    public synchronized void end() {
        if (address != 0) {
            end(address);
            address = 0;
            input = EMPTY;
            inputPos = inputLim = 0;
            inputBuffer = null;
        }
    }

    private void ensureOpen() {
        if (address == 0) {
            throw new NullPointerException("Inflater has been closed");
        }
    }

    private static native long init(boolean nowrap);

    private static native long inflateBytes(long addr, byte[] in, int inOff, int inLen, byte[] out, int off,
            int len) throws DataFormatException;

    private static native void setDictionary(long addr, byte[] b, int off, int len);

    private static native int getAdler(long addr);

    private static native void reset(long addr);

    private static native void end(long addr);
}
