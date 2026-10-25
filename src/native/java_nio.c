/*
 * java.nio buffer access (package-private natives of java.nio.Buffer).
 *
 * Buffers keep their data in a primitive array ("backing") at a byte offset
 * (see Buffer.java), so multi-byte reads and writes through a ByteBuffer or a
 * view like asFloatBuffer() are one native call here instead of a byte loop
 * in bytecode, and bulk get/put is one copy. "big" says the bytes are stored
 * big-endian; values are swapped when that differs from the platform.
 */
#include "natives.h"

#if defined(__BYTE_ORDER__) && __BYTE_ORDER__ == __ORDER_BIG_ENDIAN__
#define HOST_BIG true
#else
#define HOST_BIG false
#endif

/* Returns the address of bytes [off, off + n) of primitive array a, or NULL after throwing. */
static uint8_t *array_bytes(VMThread *t, Object *a, int32_t off, int64_t n) {
    if (!a) {
        vm_throw_npe(t, "buffer array");
        return NULL;
    }
    Class *c = a->clazz;
    if (!c->is_array || !c->component->prim) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "not a primitive array: %s", c->name);
        return NULL;
    }
    int64_t size = (int64_t)((ArrayObject *)a)->length * c->elem_size;
    if (off < 0 || n < 0 || off + n > size) {
        vm_throw_new(t, "Ljava/lang/IndexOutOfBoundsException;", "offset %d length %lld in %lld bytes", off,
                     (long long)n, (long long)size);
        return NULL;
    }
    return (uint8_t *)((ArrayObject *)a)->data + off;
}

static inline uint64_t load(const uint8_t *p, int n, bool big) {
    uint64_t v = 0;
    memcpy(&v, p, (size_t)n);
    if (big != HOST_BIG) {
        if (n == 2) v = __builtin_bswap16((uint16_t)v);
        else if (n == 4) v = __builtin_bswap32((uint32_t)v);
        else v = __builtin_bswap64(v);
    }
    return v;
}

static inline void store(uint8_t *p, int n, uint64_t v, bool big) {
    if (big != HOST_BIG) {
        if (n == 2) v = __builtin_bswap16((uint16_t)v);
        else if (n == 4) v = __builtin_bswap32((uint32_t)v);
        else v = __builtin_bswap64(v);
    }
    memcpy(p, &v, (size_t)n); /* little-endian hosts keep the low bytes first */
}

#define PEEK(n)                                              \
    uint8_t *p = array_bytes(t, A_OBJ(0), A_INT(1), (n));    \
    if (!p) return;                                          \
    uint64_t v = load(p, (n), A_BOOL(2))

NATIVE(Buffer_peekShort) {
    PEEK(2);
    R_INT((int16_t)(uint16_t)v);
}

NATIVE(Buffer_peekInt) {
    PEEK(4);
    R_INT((int32_t)(uint32_t)v);
}

NATIVE(Buffer_peekLong) {
    PEEK(8);
    R_LONG((int64_t)v);
}

NATIVE(Buffer_peekFloat) {
    PEEK(4);
    ret->raw = (uint32_t)v;
}

NATIVE(Buffer_peekDouble) {
    PEEK(8);
    ret->raw = v;
}

/* poke*(Object a, int off, value, boolean big): the value takes one slot, or two for long and double */
NATIVE(Buffer_pokeShort) {
    uint8_t *p = array_bytes(t, A_OBJ(0), A_INT(1), 2);
    if (p) store(p, 2, (uint16_t)A_INT(2), A_BOOL(3));
}

NATIVE(Buffer_pokeInt) {
    uint8_t *p = array_bytes(t, A_OBJ(0), A_INT(1), 4);
    if (p) store(p, 4, (uint32_t)args[2], A_BOOL(3));
}

NATIVE(Buffer_pokeLong) {
    uint8_t *p = array_bytes(t, A_OBJ(0), A_INT(1), 8);
    if (p) store(p, 8, args[2], A_BOOL(4));
}

/* copyMemory(Object src, int srcOff, Object dst, int dstOff, int bytes, int swapSize): byte offsets; swapSize 0
 * copies as is, 2/4/8 reverses each element of that size */
NATIVE(Buffer_copyMemory) {
    int32_t n = A_INT(4), swap = A_INT(5);
    uint8_t *s = array_bytes(t, A_OBJ(0), A_INT(1), n);
    if (!s) return;
    uint8_t *d = array_bytes(t, A_OBJ(2), A_INT(3), n);
    if (!d) return;
    if (swap != 2 && swap != 4 && swap != 8) {
        memmove(d, s, (size_t)n);
        return;
    }
    uint8_t *tmp = NULL;
    if (s < d + n && d < s + n) {
        /* overlapping views of one array */
        tmp = sa_malloc((size_t)n);
        memcpy(tmp, s, (size_t)n);
        s = tmp;
    }
    for (int32_t i = 0; i + swap <= n; i += swap) store(d + i, swap, load(s + i, swap, false), true);
    free(tmp);
}

static const NativeMethodReg g_regs[] = {
    {"Ljava/nio/Buffer;", "peekShort", "(Ljava/lang/Object;IZ)S", Buffer_peekShort},
    {"Ljava/nio/Buffer;", "peekInt", "(Ljava/lang/Object;IZ)I", Buffer_peekInt},
    {"Ljava/nio/Buffer;", "peekLong", "(Ljava/lang/Object;IZ)J", Buffer_peekLong},
    {"Ljava/nio/Buffer;", "peekFloat", "(Ljava/lang/Object;IZ)F", Buffer_peekFloat},
    {"Ljava/nio/Buffer;", "peekDouble", "(Ljava/lang/Object;IZ)D", Buffer_peekDouble},
    {"Ljava/nio/Buffer;", "pokeShort", "(Ljava/lang/Object;ISZ)V", Buffer_pokeShort},
    {"Ljava/nio/Buffer;", "pokeInt", "(Ljava/lang/Object;IIZ)V", Buffer_pokeInt},
    {"Ljava/nio/Buffer;", "pokeFloat", "(Ljava/lang/Object;IFZ)V", Buffer_pokeInt},
    {"Ljava/nio/Buffer;", "pokeLong", "(Ljava/lang/Object;IJZ)V", Buffer_pokeLong},
    {"Ljava/nio/Buffer;", "pokeDouble", "(Ljava/lang/Object;IDZ)V", Buffer_pokeLong},
    {"Ljava/nio/Buffer;", "copyMemory", "(Ljava/lang/Object;ILjava/lang/Object;III)V", Buffer_copyMemory},
};

void natives_java_nio_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
