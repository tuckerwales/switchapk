package java.nio;

/**
 * @hide Not SDK API: lets sun.nio.ch.FileChannelImpl make mapped buffers.
 */
public final class NIOAccess {
    public interface WriteBack {
        void write(byte[] data, int offset, int length, long filePosition);
    }

    private NIOAccess() {
    }

    /* sink is null for PRIVATE and READ_ONLY mappings. */
    public static MappedByteBuffer newMappedByteBuffer(byte[] data, long filePosition, boolean readOnly,
            WriteBack sink) {
        HeapMappedByteBuffer.Region r = new HeapMappedByteBuffer.Region(data, filePosition, sink);
        return new HeapMappedByteBuffer(r, -1, 0, data.length, data.length, 0, readOnly);
    }
}
