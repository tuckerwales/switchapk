package java.nio.charset;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

/** Streaming decoders and encoders for the built-in charsets (UTF-8, ISO-8859-1, US-ASCII, UTF-16*). */
final class StandardCoders {
    private StandardCoders() {
    }

    static CharsetDecoder newDecoder(Charset cs) {
        if (cs == Charset.UTF_8) {
            return new Utf8Decoder(cs);
        } else if (cs == Charset.ISO_8859_1) {
            return new SingleByteDecoder(cs, 0xff);
        } else if (cs == Charset.US_ASCII) {
            return new SingleByteDecoder(cs, 0x7f);
        }
        return new Utf16Decoder(cs, cs == Charset.UTF_16LE ? 2 : cs == Charset.UTF_16BE ? 1 : 0);
    }

    static CharsetEncoder newEncoder(Charset cs) {
        if (cs == Charset.UTF_8) {
            return new Utf8Encoder(cs);
        } else if (cs == Charset.ISO_8859_1) {
            return new SingleByteEncoder(cs, 0xff);
        } else if (cs == Charset.US_ASCII) {
            return new SingleByteEncoder(cs, 0x7f);
        }
        return new Utf16Encoder(cs, cs == Charset.UTF_16LE, cs == Charset.UTF_16);
    }

    // ---- UTF-8

    static final class Utf8Decoder extends CharsetDecoder {
        Utf8Decoder(Charset cs) {
            super(cs, 1.0f, 1.0f);
        }

        private static boolean isCont(int b) {
            return (b & 0xc0) == 0x80;
        }

        protected CoderResult decodeLoop(ByteBuffer src, CharBuffer dst) {
            int mark = src.position();
            int limit = src.limit();
            try {
                while (mark < limit) {
                    int b1 = src.get(mark) & 0xff;
                    if (b1 < 0x80) {
                        if (!dst.hasRemaining()) {
                            return CoderResult.OVERFLOW;
                        }
                        dst.put((char) b1);
                        mark++;
                    } else if (b1 >= 0xc2 && b1 <= 0xdf) {
                        if (limit - mark < 2) {
                            return CoderResult.UNDERFLOW;
                        }
                        int b2 = src.get(mark + 1) & 0xff;
                        if (!isCont(b2)) {
                            return CoderResult.malformedForLength(1);
                        }
                        if (!dst.hasRemaining()) {
                            return CoderResult.OVERFLOW;
                        }
                        dst.put((char) (((b1 & 0x1f) << 6) | (b2 & 0x3f)));
                        mark += 2;
                    } else if (b1 >= 0xe0 && b1 <= 0xef) {
                        int avail = limit - mark;
                        if (avail < 3) {
                            // report early only if what is there is already bad
                            if (avail > 1 && !validSecond3(b1, src.get(mark + 1) & 0xff)) {
                                return CoderResult.malformedForLength(1);
                            }
                            return CoderResult.UNDERFLOW;
                        }
                        int b2 = src.get(mark + 1) & 0xff, b3 = src.get(mark + 2) & 0xff;
                        if (!validSecond3(b1, b2)) {
                            return CoderResult.malformedForLength(1);
                        }
                        if (!isCont(b3)) {
                            return CoderResult.malformedForLength(2);
                        }
                        char c = (char) (((b1 & 0x0f) << 12) | ((b2 & 0x3f) << 6) | (b3 & 0x3f));
                        if (Character.isSurrogate(c)) {
                            return CoderResult.malformedForLength(3); // an encoded surrogate, as the JDK reports it
                        }
                        if (!dst.hasRemaining()) {
                            return CoderResult.OVERFLOW;
                        }
                        dst.put(c);
                        mark += 3;
                    } else if (b1 >= 0xf0 && b1 <= 0xf4) {
                        int avail = limit - mark;
                        if (avail < 4) {
                            if (avail > 1 && !validSecond4(b1, src.get(mark + 1) & 0xff)) {
                                return CoderResult.malformedForLength(1);
                            }
                            if (avail > 2 && !isCont(src.get(mark + 2) & 0xff)) {
                                return CoderResult.malformedForLength(2);
                            }
                            return CoderResult.UNDERFLOW;
                        }
                        int b2 = src.get(mark + 1) & 0xff, b3 = src.get(mark + 2) & 0xff;
                        int b4 = src.get(mark + 3) & 0xff;
                        if (!validSecond4(b1, b2)) {
                            return CoderResult.malformedForLength(1);
                        }
                        if (!isCont(b3)) {
                            return CoderResult.malformedForLength(2);
                        }
                        if (!isCont(b4)) {
                            return CoderResult.malformedForLength(3);
                        }
                        if (dst.remaining() < 2) {
                            return CoderResult.OVERFLOW;
                        }
                        int cp = ((b1 & 0x07) << 18) | ((b2 & 0x3f) << 12) | ((b3 & 0x3f) << 6) | (b4 & 0x3f);
                        dst.put(Character.highSurrogate(cp));
                        dst.put(Character.lowSurrogate(cp));
                        mark += 4;
                    } else {
                        return CoderResult.malformedForLength(1);
                    }
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(mark);
            }
        }

        /** Second byte of a 3-byte sequence: a continuation, and no overlong (E0 80-9F). Surrogates (ED A0-BF)
         * are rejected once the sequence is complete. */
        private static boolean validSecond3(int b1, int b2) {
            return isCont(b2) && (b1 != 0xe0 || b2 >= 0xa0);
        }

        /** Second byte of a 4-byte sequence: no overlongs (F0 80-8F) and nothing past U+10FFFF (F4 90+). */
        private static boolean validSecond4(int b1, int b2) {
            if (!isCont(b2)) {
                return false;
            }
            if (b1 == 0xf0) {
                return b2 >= 0x90;
            }
            if (b1 == 0xf4) {
                return b2 < 0x90;
            }
            return true;
        }
    }

    static final class Utf8Encoder extends CharsetEncoder {
        Utf8Encoder(Charset cs) {
            super(cs, 1.1f, 3.0f);
        }

        public boolean canEncode(char c) {
            return !Character.isSurrogate(c);
        }

        protected CoderResult encodeLoop(CharBuffer src, ByteBuffer dst) {
            int mark = src.position();
            int limit = src.limit();
            try {
                while (mark < limit) {
                    char c = src.get(mark);
                    if (c < 0x80) {
                        if (!dst.hasRemaining()) {
                            return CoderResult.OVERFLOW;
                        }
                        dst.put((byte) c);
                        mark++;
                    } else if (c < 0x800) {
                        if (dst.remaining() < 2) {
                            return CoderResult.OVERFLOW;
                        }
                        dst.put((byte) (0xc0 | (c >> 6)));
                        dst.put((byte) (0x80 | (c & 0x3f)));
                        mark++;
                    } else if (Character.isSurrogate(c)) {
                        if (Character.isLowSurrogate(c)) {
                            return CoderResult.malformedForLength(1);
                        }
                        if (mark + 1 >= limit) {
                            return CoderResult.UNDERFLOW; // the low half may come with more input
                        }
                        char d = src.get(mark + 1);
                        if (!Character.isLowSurrogate(d)) {
                            return CoderResult.malformedForLength(1);
                        }
                        if (dst.remaining() < 4) {
                            return CoderResult.OVERFLOW;
                        }
                        int cp = Character.toCodePoint(c, d);
                        dst.put((byte) (0xf0 | (cp >> 18)));
                        dst.put((byte) (0x80 | ((cp >> 12) & 0x3f)));
                        dst.put((byte) (0x80 | ((cp >> 6) & 0x3f)));
                        dst.put((byte) (0x80 | (cp & 0x3f)));
                        mark += 2;
                    } else {
                        if (dst.remaining() < 3) {
                            return CoderResult.OVERFLOW;
                        }
                        dst.put((byte) (0xe0 | (c >> 12)));
                        dst.put((byte) (0x80 | ((c >> 6) & 0x3f)));
                        dst.put((byte) (0x80 | (c & 0x3f)));
                        mark++;
                    }
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(mark);
            }
        }
    }

    // ---- ISO-8859-1 and US-ASCII

    static final class SingleByteDecoder extends CharsetDecoder {
        private final int max;

        SingleByteDecoder(Charset cs, int max) {
            super(cs, 1.0f, 1.0f);
            this.max = max;
        }

        protected CoderResult decodeLoop(ByteBuffer src, CharBuffer dst) {
            int mark = src.position();
            try {
                while (mark < src.limit()) {
                    int b = src.get(mark) & 0xff;
                    if (b > max) {
                        return CoderResult.malformedForLength(1);
                    }
                    if (!dst.hasRemaining()) {
                        return CoderResult.OVERFLOW;
                    }
                    dst.put((char) b);
                    mark++;
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(mark);
            }
        }
    }

    static final class SingleByteEncoder extends CharsetEncoder {
        private final int max;

        SingleByteEncoder(Charset cs, int max) {
            super(cs, 1.0f, 1.0f);
            this.max = max;
        }

        public boolean canEncode(char c) {
            return c <= max;
        }

        protected CoderResult encodeLoop(CharBuffer src, ByteBuffer dst) {
            int mark = src.position();
            int limit = src.limit();
            try {
                while (mark < limit) {
                    char c = src.get(mark);
                    if (c <= max) {
                        if (!dst.hasRemaining()) {
                            return CoderResult.OVERFLOW;
                        }
                        dst.put((byte) c);
                        mark++;
                        continue;
                    }
                    if (Character.isHighSurrogate(c)) {
                        if (mark + 1 >= limit) {
                            return CoderResult.UNDERFLOW;
                        }
                        if (Character.isLowSurrogate(src.get(mark + 1))) {
                            return CoderResult.unmappableForLength(2);
                        }
                        return CoderResult.malformedForLength(1);
                    }
                    if (Character.isLowSurrogate(c)) {
                        return CoderResult.malformedForLength(1);
                    }
                    return CoderResult.unmappableForLength(1);
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(mark);
            }
        }
    }

    // ---- UTF-16

    /** byteOrder: 0 = unknown (read a BOM, default big-endian), 1 = big-endian, 2 = little-endian. */
    static final class Utf16Decoder extends CharsetDecoder {
        private final int defaultOrder;
        private int order;

        Utf16Decoder(Charset cs, int byteOrder) {
            super(cs, 0.5f, 1.0f);
            this.defaultOrder = byteOrder;
            this.order = byteOrder;
        }

        protected void implReset() {
            order = defaultOrder;
        }

        private char read(ByteBuffer src, int at) {
            int b1 = src.get(at) & 0xff, b2 = src.get(at + 1) & 0xff;
            return (char) (order == 2 ? (b2 << 8) | b1 : (b1 << 8) | b2);
        }

        protected CoderResult decodeLoop(ByteBuffer src, CharBuffer dst) {
            int mark = src.position();
            int limit = src.limit();
            try {
                if (order == 0 && limit - mark >= 2) {
                    int b1 = src.get(mark) & 0xff, b2 = src.get(mark + 1) & 0xff;
                    if (b1 == 0xfe && b2 == 0xff) {
                        order = 1;
                        mark += 2;
                    } else if (b1 == 0xff && b2 == 0xfe) {
                        order = 2;
                        mark += 2;
                    } else {
                        order = 1;
                    }
                }
                while (limit - mark >= 2) {
                    char c = read(src, mark);
                    if (!Character.isSurrogate(c)) {
                        if (!dst.hasRemaining()) {
                            return CoderResult.OVERFLOW;
                        }
                        dst.put(c);
                        mark += 2;
                        continue;
                    }
                    if (Character.isLowSurrogate(c)) {
                        return CoderResult.malformedForLength(2);
                    }
                    if (limit - mark < 4) {
                        return CoderResult.UNDERFLOW;
                    }
                    char d = read(src, mark + 2);
                    if (!Character.isLowSurrogate(d)) {
                        return CoderResult.malformedForLength(4);
                    }
                    if (dst.remaining() < 2) {
                        return CoderResult.OVERFLOW;
                    }
                    dst.put(c);
                    dst.put(d);
                    mark += 4;
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(mark);
            }
        }
    }

    static final class Utf16Encoder extends CharsetEncoder {
        private final boolean little;
        private final boolean writeBom;
        private boolean bomDone;

        Utf16Encoder(Charset cs, boolean little, boolean writeBom) {
            super(cs, writeBom ? 4.0f : 2.0f, writeBom ? 4.0f : 2.0f,
                    little ? new byte[] {(byte) 0xfd, (byte) 0xff} : new byte[] {(byte) 0xff, (byte) 0xfd});
            this.little = little;
            this.writeBom = writeBom;
        }

        protected void implReset() {
            bomDone = false;
        }

        public boolean canEncode(char c) {
            return !Character.isSurrogate(c);
        }

        private void put(ByteBuffer dst, char c) {
            if (little) {
                dst.put((byte) c);
                dst.put((byte) (c >> 8));
            } else {
                dst.put((byte) (c >> 8));
                dst.put((byte) c);
            }
        }

        protected CoderResult encodeLoop(CharBuffer src, ByteBuffer dst) {
            int mark = src.position();
            int limit = src.limit();
            try {
                if (writeBom && !bomDone && mark < limit) {
                    if (dst.remaining() < 2) {
                        return CoderResult.OVERFLOW;
                    }
                    put(dst, '﻿');
                    bomDone = true;
                }
                while (mark < limit) {
                    char c = src.get(mark);
                    if (!Character.isSurrogate(c)) {
                        if (dst.remaining() < 2) {
                            return CoderResult.OVERFLOW;
                        }
                        put(dst, c);
                        mark++;
                        continue;
                    }
                    if (Character.isLowSurrogate(c)) {
                        return CoderResult.malformedForLength(1);
                    }
                    if (mark + 1 >= limit) {
                        return CoderResult.UNDERFLOW;
                    }
                    char d = src.get(mark + 1);
                    if (!Character.isLowSurrogate(d)) {
                        return CoderResult.malformedForLength(1);
                    }
                    if (dst.remaining() < 4) {
                        return CoderResult.OVERFLOW;
                    }
                    put(dst, c);
                    put(dst, d);
                    mark += 2;
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(mark);
            }
        }
    }
}
