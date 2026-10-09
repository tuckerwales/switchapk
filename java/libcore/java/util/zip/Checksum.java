package java.util.zip;

import java.nio.ByteBuffer;

public interface Checksum {
    void update(int b);

    default void update(byte[] b) {
        update(b, 0, b.length);
    }

    void update(byte[] b, int off, int len);

    default void update(ByteBuffer buffer) {
        int pos = buffer.position();
        int limit = buffer.limit();
        if (pos > limit) {
            return;
        }
        int rem = limit - pos;
        if (buffer.hasArray()) {
            update(buffer.array(), pos + buffer.arrayOffset(), rem);
        } else {
            byte[] b = new byte[Math.min(rem, 4096)];
            while (buffer.hasRemaining()) {
                int length = Math.min(buffer.remaining(), b.length);
                buffer.get(b, 0, length);
                update(b, 0, length);
            }
        }
        buffer.position(limit);
    }

    long getValue();

    void reset();
}
