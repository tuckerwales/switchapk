/*
 * java.util.zip natives over zlib: CRC32 and Adler32 updates, and Inflater/Deflater streams.
 * A stream is a malloc'd z_stream whose address the Java object keeps in a long (`address`, 0 once ended).
 * Input and output stay in Java arrays: each call points zlib at them for that call only, so nothing in C
 * refers to the Java heap between calls. Inflate and deflate return one packed long (see PACK below) and the
 * Java side keeps the byte counts and the remaining input. The work is CPU bound and runs with the GIL held.
 */
#include "natives.h"

#include <zlib.h>

#define LOG_TAG "zip"

/* Packed result: bits 0-30 bytes written, 31-61 bytes read, 62 stream end, 63 dictionary needed. */
#define PACK(written, read, end, dict)                                                                     \
    ((int64_t)((uint64_t)(uint32_t)(written) | ((uint64_t)(uint32_t)(read) << 31) | ((uint64_t)!!(end) << 62) | \
               ((uint64_t)!!(dict) << 63)))

static bool check_range(VMThread *t, ArrayObject *b, int32_t off, int32_t len) {
    if (!b) {
        vm_throw_npe(t, "buffer");
        return false;
    }
    if (off < 0 || len < 0 || (int64_t)off + len > b->length) {
        vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;", "off=%d len=%d length=%d", off, len, b->length);
        return false;
    }
    return true;
}

static z_stream *stream_of(VMThread *t, int64_t address) {
    if (!address) {
        vm_throw_npe(t, "zlib stream has been ended");
        return NULL;
    }
    return (z_stream *)(uintptr_t)address;
}

/* static int CRC32.update(int crc, int b) */
NATIVE(CRC32_update) {
    UNUSED_ARGS();
    uint8_t b = (uint8_t)A_INT(1);
    R_INT((int32_t)crc32((uLong)(uint32_t)A_INT(0), &b, 1));
}

/* static int CRC32.updateBytes(int crc, byte[] b, int off, int len) */
NATIVE(CRC32_updateBytes) {
    ArrayObject *b = A_ARR(1);
    int32_t off = A_INT(2), len = A_INT(3);
    if (!check_range(t, b, off, len)) return;
    R_INT((int32_t)crc32((uLong)(uint32_t)A_INT(0), ARRAY_DATA(b, uint8_t) + off, (uInt)len));
}

/* static int Adler32.update(int adler, int b) */
NATIVE(Adler32_update) {
    UNUSED_ARGS();
    uint8_t b = (uint8_t)A_INT(1);
    R_INT((int32_t)adler32((uLong)(uint32_t)A_INT(0), &b, 1));
}

/* static int Adler32.updateBytes(int adler, byte[] b, int off, int len) */
NATIVE(Adler32_updateBytes) {
    ArrayObject *b = A_ARR(1);
    int32_t off = A_INT(2), len = A_INT(3);
    if (!check_range(t, b, off, len)) return;
    R_INT((int32_t)adler32((uLong)(uint32_t)A_INT(0), ARRAY_DATA(b, uint8_t) + off, (uInt)len));
}

static void throw_zlib(VMThread *t, const char *cls, z_stream *s, int r) {
    vm_throw_new(t, cls, "%s", s->msg ? s->msg : zError(r));
}

/* static long Inflater.init(boolean nowrap) */
NATIVE(Inflater_init) {
    z_stream *s = sa_calloc(1, sizeof *s);
    int r = inflateInit2(s, A_BOOL(0) ? -MAX_WBITS : MAX_WBITS);
    if (r != Z_OK) {
        free(s);
        if (r == Z_MEM_ERROR) vm_throw_new(t, "Ljava/lang/OutOfMemoryError;", "inflateInit2");
        else vm_throw_new(t, "Ljava/lang/InternalError;", "inflateInit2: %s", zError(r));
        return;
    }
    R_LONG((int64_t)(uintptr_t)s);
}

/* static long Inflater.inflateBytes(long addr, byte[] in, int inOff, int inLen, byte[] out, int off, int len) */
NATIVE(Inflater_inflateBytes) {
    z_stream *s = stream_of(t, A_LONG(0));
    if (!s) return;
    ArrayObject *in = A_ARR(2), *out = A_ARR(5);
    int32_t in_off = A_INT(3), in_len = A_INT(4), off = A_INT(6), len = A_INT(7);
    if (in_len > 0 && !check_range(t, in, in_off, in_len)) return;
    if (!check_range(t, out, off, len)) return;
    s->next_in = in_len > 0 ? ARRAY_DATA(in, uint8_t) + in_off : NULL;
    s->avail_in = (uInt)in_len;
    s->next_out = ARRAY_DATA(out, uint8_t) + off;
    s->avail_out = (uInt)len;
    int r = inflate(s, Z_PARTIAL_FLUSH);
    int32_t read = in_len - (int32_t)s->avail_in, written = len - (int32_t)s->avail_out;
    s->next_in = s->next_out = NULL;
    switch (r) {
    case Z_OK:
    case Z_BUF_ERROR: /* no progress possible: needs input or output space */
        R_LONG(PACK(written, read, 0, 0));
        return;
    case Z_STREAM_END:
        R_LONG(PACK(written, read, 1, 0));
        return;
    case Z_NEED_DICT:
        R_LONG(PACK(written, read, 0, 1));
        return;
    case Z_MEM_ERROR:
        vm_throw_new(t, "Ljava/lang/OutOfMemoryError;", "inflate");
        return;
    default:
        throw_zlib(t, "Ljava/util/zip/DataFormatException;", s, r);
        return;
    }
}

/* static void Inflater.setDictionary(long addr, byte[] b, int off, int len) */
NATIVE(Inflater_setDictionary) {
    z_stream *s = stream_of(t, A_LONG(0));
    ArrayObject *b = A_ARR(2);
    int32_t off = A_INT(3), len = A_INT(4);
    if (!s || !check_range(t, b, off, len)) return;
    int r = inflateSetDictionary(s, ARRAY_DATA(b, uint8_t) + off, (uInt)len);
    if (r != Z_OK) throw_zlib(t, "Ljava/lang/IllegalArgumentException;", s, r);
}

/* static int Inflater.getAdler(long addr) */
NATIVE(Inflater_getAdler) {
    z_stream *s = stream_of(t, A_LONG(0));
    if (s) R_INT((int32_t)s->adler);
}

/* static void Inflater.reset(long addr) */
NATIVE(Inflater_reset) {
    z_stream *s = stream_of(t, A_LONG(0));
    if (s) inflateReset(s);
}

/* static void Inflater.end(long addr) */
NATIVE(Inflater_end) {
    UNUSED_ARGS();
    z_stream *s = (z_stream *)(uintptr_t)A_LONG(0);
    if (!s) return;
    inflateEnd(s);
    free(s);
}

/* static long Deflater.init(int level, int strategy, boolean nowrap) */
NATIVE(Deflater_init) {
    z_stream *s = sa_calloc(1, sizeof *s);
    int r = deflateInit2(s, A_INT(0), Z_DEFLATED, A_BOOL(2) ? -MAX_WBITS : MAX_WBITS, 8, A_INT(1));
    if (r != Z_OK) {
        free(s);
        if (r == Z_MEM_ERROR) vm_throw_new(t, "Ljava/lang/OutOfMemoryError;", "deflateInit2");
        else vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "deflateInit2: %s", zError(r));
        return;
    }
    R_LONG((int64_t)(uintptr_t)s);
}

/*
 * static long Deflater.deflateBytes(long addr, byte[] in, int inOff, int inLen, byte[] out, int off, int len,
 *                                   int flush, int params)
 * flush is a zlib flush mode (Z_FINISH once finish() was called). params is -1, or level << 8 | strategy to
 * apply first with deflateParams; bit 62 of the result is then set when the new parameters took effect,
 * instead of meaning stream end.
 */
NATIVE(Deflater_deflateBytes) {
    z_stream *s = stream_of(t, A_LONG(0));
    if (!s) return;
    ArrayObject *in = A_ARR(2), *out = A_ARR(5);
    int32_t in_off = A_INT(3), in_len = A_INT(4), off = A_INT(6), len = A_INT(7), flush = A_INT(8);
    int32_t params = A_INT(9);
    if (in_len > 0 && !check_range(t, in, in_off, in_len)) return;
    if (!check_range(t, out, off, len)) return;
    s->next_in = in_len > 0 ? ARRAY_DATA(in, uint8_t) + in_off : NULL;
    s->avail_in = (uInt)in_len;
    s->next_out = ARRAY_DATA(out, uint8_t) + off;
    s->avail_out = (uInt)len;
    int r;
    bool flag;
    if (params >= 0) {
        int level = (int8_t)(params >> 8);
        r = deflateParams(s, level, params & 0xff);
        flag = r == Z_OK;
        if (r == Z_BUF_ERROR) r = Z_OK;
    } else {
        r = deflate(s, flush);
        flag = r == Z_STREAM_END;
        if (r == Z_BUF_ERROR || r == Z_STREAM_END) r = Z_OK;
    }
    int32_t read = in_len - (int32_t)s->avail_in, written = len - (int32_t)s->avail_out;
    s->next_in = s->next_out = NULL;
    if (r != Z_OK) {
        throw_zlib(t, "Ljava/lang/InternalError;", s, r);
        return;
    }
    R_LONG(PACK(written, read, flag, 0));
}

/* static void Deflater.setDictionary(long addr, byte[] b, int off, int len) */
NATIVE(Deflater_setDictionary) {
    z_stream *s = stream_of(t, A_LONG(0));
    ArrayObject *b = A_ARR(2);
    int32_t off = A_INT(3), len = A_INT(4);
    if (!s || !check_range(t, b, off, len)) return;
    int r = deflateSetDictionary(s, ARRAY_DATA(b, uint8_t) + off, (uInt)len);
    if (r != Z_OK) throw_zlib(t, "Ljava/lang/IllegalArgumentException;", s, r);
}

/* static int Deflater.getAdler(long addr) */
NATIVE(Deflater_getAdler) {
    z_stream *s = stream_of(t, A_LONG(0));
    if (s) R_INT((int32_t)s->adler);
}

/* static void Deflater.reset(long addr) */
NATIVE(Deflater_reset) {
    z_stream *s = stream_of(t, A_LONG(0));
    if (s) deflateReset(s);
}

/* static void Deflater.end(long addr) */
NATIVE(Deflater_end) {
    UNUSED_ARGS();
    z_stream *s = (z_stream *)(uintptr_t)A_LONG(0);
    if (!s) return;
    deflateEnd(s);
    free(s);
}

static const NativeMethodReg g_regs[] = {
    {"Ljava/util/zip/CRC32;", "update", "(II)I", CRC32_update},
    {"Ljava/util/zip/CRC32;", "updateBytes", "(I[BII)I", CRC32_updateBytes},
    {"Ljava/util/zip/Adler32;", "update", "(II)I", Adler32_update},
    {"Ljava/util/zip/Adler32;", "updateBytes", "(I[BII)I", Adler32_updateBytes},
    {"Ljava/util/zip/Inflater;", "init", "(Z)J", Inflater_init},
    {"Ljava/util/zip/Inflater;", "inflateBytes", "(J[BII[BII)J", Inflater_inflateBytes},
    {"Ljava/util/zip/Inflater;", "setDictionary", "(J[BII)V", Inflater_setDictionary},
    {"Ljava/util/zip/Inflater;", "getAdler", "(J)I", Inflater_getAdler},
    {"Ljava/util/zip/Inflater;", "reset", "(J)V", Inflater_reset},
    {"Ljava/util/zip/Inflater;", "end", "(J)V", Inflater_end},
    {"Ljava/util/zip/Deflater;", "init", "(IIZ)J", Deflater_init},
    {"Ljava/util/zip/Deflater;", "deflateBytes", "(J[BII[BIIII)J", Deflater_deflateBytes},
    {"Ljava/util/zip/Deflater;", "setDictionary", "(J[BII)V", Deflater_setDictionary},
    {"Ljava/util/zip/Deflater;", "getAdler", "(J)I", Deflater_getAdler},
    {"Ljava/util/zip/Deflater;", "reset", "(J)V", Deflater_reset},
    {"Ljava/util/zip/Deflater;", "end", "(J)V", Deflater_end},
};

void natives_java_zip_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
