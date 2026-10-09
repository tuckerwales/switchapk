package java.util.zip;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

/**
 * zlib deflater. Like Inflater, the stream lives in native memory until end(); there are no finalizers.
 */
public class Deflater {
    public static final int DEFLATED = 8;
    public static final int NO_COMPRESSION = 0;
    public static final int BEST_SPEED = 1;
    public static final int BEST_COMPRESSION = 9;
    public static final int DEFAULT_COMPRESSION = -1;
    public static final int FILTERED = 1;
    public static final int HUFFMAN_ONLY = 2;
    public static final int DEFAULT_STRATEGY = 0;
    public static final int NO_FLUSH = 0;
    public static final int SYNC_FLUSH = 2;
    public static final int FULL_FLUSH = 3;

    private static final int Z_FINISH = 4;
    private static final byte[] EMPTY = new byte[0];

    private long address;
    private int level;
    private int strategy;
    private boolean setParams;
    private boolean finish;
    private boolean finished;
    private byte[] input = EMPTY;
    private int inputPos;
    private int inputLim;
    private ByteBuffer inputBuffer;
    private long bytesRead;
    private long bytesWritten;

    public Deflater(int level, boolean nowrap) {
        if ((level < 0 || level > 9) && level != DEFAULT_COMPRESSION) {
            throw new IllegalArgumentException("invalid compression level");
        }
        this.level = level;
        this.strategy = DEFAULT_STRATEGY;
        this.address = init(level, DEFAULT_STRATEGY, nowrap);
    }

    public Deflater(int level) {
        this(level, false);
    }

    public Deflater() {
        this(DEFAULT_COMPRESSION, false);
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

    public synchronized void setStrategy(int strategy) {
        switch (strategy) {
            case DEFAULT_STRATEGY:
            case FILTERED:
            case HUFFMAN_ONLY:
                break;
            default:
                throw new IllegalArgumentException();
        }
        if (this.strategy != strategy) {
            this.strategy = strategy;
            setParams = true;
        }
    }

    public synchronized void setLevel(int level) {
        if ((level < 0 || level > 9) && level != DEFAULT_COMPRESSION) {
            throw new IllegalArgumentException("invalid compression level");
        }
        if (this.level != level) {
            this.level = level;
            setParams = true;
        }
    }

    public synchronized boolean needsInput() {
        return inputLim - inputPos <= 0;
    }

    public synchronized void finish() {
        finish = true;
    }

    public synchronized boolean finished() {
        return finished;
    }

    public int deflate(byte[] output, int off, int len) {
        return deflate(output, off, len, NO_FLUSH);
    }

    public int deflate(byte[] output) {
        return deflate(output, 0, output.length, NO_FLUSH);
    }

    public int deflate(ByteBuffer output) {
        return deflate(output, NO_FLUSH);
    }

    public int deflate(byte[] output, int off, int len, int flush) {
        if (off < 0 || len < 0 || off > output.length - len) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (flush != NO_FLUSH && flush != SYNC_FLUSH && flush != FULL_FLUSH) {
            throw new IllegalArgumentException();
        }
        synchronized (this) {
            ensureOpen();
            if (finished) {
                return 0;
            }
            int params = setParams ? ((level & 0xff) << 8) | strategy : -1;
            long r = deflateBytes(address, input, inputPos, inputLim - inputPos, output, off, len,
                    finish ? Z_FINISH : flush, params);
            int written = (int) (r & 0x7fffffff);
            int read = (int) ((r >>> 31) & 0x7fffffff);
            inputPos += read;
            if (inputBuffer != null) {
                inputBuffer.position(inputBuffer.position() + read);
            }
            bytesRead += read;
            bytesWritten += written;
            if ((r & (1L << 62)) != 0) {
                if (params >= 0) {
                    setParams = false;
                } else {
                    finished = true;
                }
            }
            return written;
        }
    }

    public int deflate(ByteBuffer output, int flush) {
        if (output.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        int rem = Math.max(output.limit() - output.position(), 0);
        if (output.hasArray()) {
            int n = deflate(output.array(), output.arrayOffset() + output.position(), rem, flush);
            output.position(output.position() + n);
            return n;
        }
        byte[] b = new byte[rem];
        int n = deflate(b, 0, rem, flush);
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
        finish = false;
        finished = false;
        input = EMPTY;
        inputPos = inputLim = 0;
        inputBuffer = null;
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
            throw new NullPointerException("Deflater has been closed");
        }
    }

    private static native long init(int level, int strategy, boolean nowrap);

    private static native long deflateBytes(long addr, byte[] in, int inOff, int inLen, byte[] out, int off,
            int len, int flush, int params);

    private static native void setDictionary(long addr, byte[] b, int off, int len);

    private static native int getAdler(long addr);

    private static native void reset(long addr);

    private static native void end(long addr);
}
