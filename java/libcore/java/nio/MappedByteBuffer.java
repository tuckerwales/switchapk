package java.nio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;

/**
 * A file region read into memory. There is no mmap here: FileChannel.map copies
 * the region into a heap buffer, and {@link #force} writes a READ_WRITE mapping
 * back. Slices and duplicates are plain heap buffers that do not write back.
 */
public abstract class MappedByteBuffer extends ByteBuffer {
    private FileChannel channel;
    private long filePosition;

    MappedByteBuffer(int mark, int pos, int lim, int cap, byte[] hb, int offset) {
        super(mark, pos, lim, cap, hb, offset);
    }

    /** framework-internal: makes force() write this buffer back to the file at the given offset. */
    public static void attachToFile(MappedByteBuffer buffer, FileChannel channel, long filePosition) {
        buffer.channel = channel;
        buffer.filePosition = filePosition;
    }

    public final boolean isLoaded() {
        return true;
    }

    public final MappedByteBuffer load() {
        return this;
    }

    public final MappedByteBuffer force() {
        return force(0, capacity());
    }

    public final MappedByteBuffer force(int index, int length) {
        if (channel == null || length <= 0) return this;
        try {
            ByteBuffer region = duplicate();
            region.limit(index + length);
            region.position(index);
            while (region.hasRemaining()) {
                channel.write(region, filePosition + region.position());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }
}
