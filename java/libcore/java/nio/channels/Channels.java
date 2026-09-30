package java.nio.channels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public final class Channels {
    private Channels() {
    }

    public static ReadableByteChannel newChannel(final InputStream in) {
        return new ReadableByteChannel() {
            boolean open = true;

            public int read(ByteBuffer dst) throws IOException {
                byte[] b = new byte[dst.remaining()];
                int n = in.read(b);
                if (n > 0) {
                    dst.put(b, 0, n);
                }
                return n;
            }

            public boolean isOpen() {
                return open;
            }

            public void close() throws IOException {
                open = false;
                in.close();
            }
        };
    }

    public static WritableByteChannel newChannel(final OutputStream out) {
        return new WritableByteChannel() {
            boolean open = true;

            public int write(ByteBuffer src) throws IOException {
                byte[] b = new byte[src.remaining()];
                src.get(b);
                out.write(b);
                return b.length;
            }

            public boolean isOpen() {
                return open;
            }

            public void close() throws IOException {
                open = false;
                out.close();
            }
        };
    }

    public static InputStream newInputStream(final ReadableByteChannel ch) {
        return new InputStream() {
            public int read() throws IOException {
                byte[] b = new byte[1];
                return read(b, 0, 1) <= 0 ? -1 : b[0] & 0xff;
            }

            public int read(byte[] b, int off, int len) throws IOException {
                return ch.read(ByteBuffer.wrap(b, off, len));
            }
        };
    }
}
