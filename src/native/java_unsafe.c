/*
 * sun.misc.Unsafe. Instance field offsets are byte offsets in the object (class.c packs fields at
 * their natural size). Arrays keep elements out of line, so array offsets are
 * UNSAFE_ARRAY_BASE + index * scale and are rebased onto ArrayObject.data. Java threads run under the
 * GIL, so plain loads and stores here are atomic with respect to each other.
 */
#include "natives.h"

#define UNSAFE_ARRAY_BASE 16 /* must match Unsafe.ARRAY_BASE_OFFSET */

static void *unsafe_addr(VMThread *t, Object *o, int64_t off, size_t size) {
    if (!o) {
        vm_throw_npe(t, "Unsafe access on null");
        return NULL;
    }
    if (o->clazz->is_array) {
        ArrayObject *a = (ArrayObject *)o;
        int64_t pos = off - UNSAFE_ARRAY_BASE;
        if (pos < 0 || pos + (int64_t)size > (int64_t)a->length * o->clazz->elem_size) {
            vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;", "Unsafe offset %lld", (long long)off);
            return NULL;
        }
        return (uint8_t *)a->data + pos;
    }
    if (off < (int64_t)sizeof(Object) || off + (int64_t)size > (int64_t)o->clazz->instance_size) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "Unsafe offset %lld", (long long)off);
        return NULL;
    }
    return (uint8_t *)o + off;
}

NATIVE(Unsafe_objectFieldOffset0) {
    UNUSED_ARGS();
    Object *field = A_OBJ(0);
    if (!field) {
        vm_throw_npe(t, "field");
        return;
    }
    Field *f = (Field *)(intptr_t)vm_get_long(field, g_vm.wf.Field_vmField);
    R_LONG(f ? (int64_t)f->offset : -1);
}

NATIVE(Unsafe_arrayIndexScale0) {
    UNUSED_ARGS();
    Class *c = vm_class_from_mirror(A_OBJ(0));
    R_INT(c && c->is_array ? c->elem_size : 0);
}

NATIVE(Unsafe_compareAndSwapInt) {
    UNUSED_ARGS();
    int32_t *p = unsafe_addr(t, A_OBJ(1), A_LONG(2), 4);
    if (!p) return;
    bool ok = *p == A_INT(4);
    if (ok) *p = A_INT(5);
    R_BOOL(ok);
}

NATIVE(Unsafe_compareAndSwapLong) {
    UNUSED_ARGS();
    int64_t *p = unsafe_addr(t, A_OBJ(1), A_LONG(2), 8);
    if (!p) return;
    bool ok = *p == A_LONG(4);
    if (ok) *p = A_LONG(6);
    R_BOOL(ok);
}

NATIVE(Unsafe_compareAndSwapObject) {
    UNUSED_ARGS();
    Object **p = unsafe_addr(t, A_OBJ(1), A_LONG(2), sizeof(Object *));
    if (!p) return;
    bool ok = *p == A_OBJ(4);
    if (ok) *p = A_OBJ(5);
    R_BOOL(ok);
}

/* get<K>(Object, long) and put<K>(Object, long, value): value slot 4 */
#define UNSAFE_OBJ_ACCESS(K, ctype, getret, argget)                     \
    NATIVE(Unsafe_get##K) {                                             \
        UNUSED_ARGS();                                                  \
        ctype *p = unsafe_addr(t, A_OBJ(1), A_LONG(2), sizeof(ctype)); \
        if (!p) return;                                                 \
        getret(*p);                                                     \
    }                                                                   \
    NATIVE(Unsafe_put##K) {                                             \
        UNUSED_ARGS();                                                  \
        ctype *p = unsafe_addr(t, A_OBJ(1), A_LONG(2), sizeof(ctype)); \
        if (!p) return;                                                 \
        *p = (ctype)argget(4);                                          \
    }

UNSAFE_OBJ_ACCESS(Int, int32_t, R_INT, A_INT)
UNSAFE_OBJ_ACCESS(Long, int64_t, R_LONG, A_LONG)
UNSAFE_OBJ_ACCESS(Object, Object *, R_OBJ, A_OBJ)
UNSAFE_OBJ_ACCESS(Boolean, uint8_t, R_BOOL, A_BOOL)
UNSAFE_OBJ_ACCESS(Byte, int8_t, R_INT, A_INT)
UNSAFE_OBJ_ACCESS(Short, int16_t, R_INT, A_INT)
UNSAFE_OBJ_ACCESS(Char, uint16_t, R_INT, A_INT)
UNSAFE_OBJ_ACCESS(Float, float, R_FLOAT, A_FLOAT)
UNSAFE_OBJ_ACCESS(Double, double, R_DOUBLE, A_DOUBLE)

NATIVE(Unsafe_allocateInstance) {
    UNUSED_ARGS();
    Class *c = vm_class_from_mirror(A_OBJ(1));
    if (!c) {
        vm_throw_npe(t, "class");
        return;
    }
    if (c->is_array || (c->access & (ACC_INTERFACE | ACC_ABSTRACT))) {
        vm_throw_new(t, "Ljava/lang/InstantiationException;", "%s", c->descriptor);
        return;
    }
    if (!vm_init_class(t, c)) return;
    R_OBJ(vm_alloc_object(t, c));
}

/* Off-heap memory: absolute addresses are malloc'd pointers. */
NATIVE(Unsafe_allocateMemory) {
    UNUSED_ARGS();
    int64_t n = A_LONG(1);
    if (n < 0) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "size %lld", (long long)n);
        return;
    }
    void *p = n ? malloc((size_t)n) : NULL;
    if (n && !p) {
        vm_throw_new(t, "Ljava/lang/OutOfMemoryError;", "Unsafe.allocateMemory(%lld)", (long long)n);
        return;
    }
    R_LONG((int64_t)(intptr_t)p);
}

NATIVE(Unsafe_freeMemory) {
    UNUSED_ARGS();
    free((void *)(intptr_t)A_LONG(1));
}

NATIVE(Unsafe_setMemory) {
    UNUSED_ARGS();
    memset((void *)(intptr_t)A_LONG(1), A_INT(5), (size_t)A_LONG(3));
}

NATIVE(Unsafe_copyMemory) {
    UNUSED_ARGS();
    memmove((void *)(intptr_t)A_LONG(3), (void *)(intptr_t)A_LONG(1), (size_t)A_LONG(5));
}

#define UNSAFE_ADDR_ACCESS(K, ctype, getret, argget)        \
    NATIVE(Unsafe_get##K##Addr) {                           \
        UNUSED_ARGS();                                      \
        ctype v;                                            \
        memcpy(&v, (void *)(intptr_t)A_LONG(1), sizeof v);  \
        getret(v);                                          \
    }                                                       \
    NATIVE(Unsafe_put##K##Addr) {                           \
        UNUSED_ARGS();                                      \
        ctype v = (ctype)argget(3);                         \
        memcpy((void *)(intptr_t)A_LONG(1), &v, sizeof v);  \
    }

UNSAFE_ADDR_ACCESS(Byte, int8_t, R_INT, A_INT)
UNSAFE_ADDR_ACCESS(Int, int32_t, R_INT, A_INT)
UNSAFE_ADDR_ACCESS(Long, int64_t, R_LONG, A_LONG)
UNSAFE_ADDR_ACCESS(Float, float, R_FLOAT, A_FLOAT)
UNSAFE_ADDR_ACCESS(Double, double, R_DOUBLE, A_DOUBLE)
UNSAFE_ADDR_ACCESS(Short, int16_t, R_INT, A_INT)
UNSAFE_ADDR_ACCESS(Char, uint16_t, R_INT, A_INT)

#define U "Lsun/misc/Unsafe;"
#define OBJ_ACCESS_REGS(K, sig)                                    \
    {U, "get" #K, "(Ljava/lang/Object;J)" sig, Unsafe_get##K},     \
    {U, "put" #K, "(Ljava/lang/Object;J" sig ")V", Unsafe_put##K}
#define ADDR_ACCESS_REGS(K, sig)                       \
    {U, "get" #K, "(J)" sig, Unsafe_get##K##Addr},     \
    {U, "put" #K, "(J" sig ")V", Unsafe_put##K##Addr}

static const NativeMethodReg g_regs[] = {
    {U, "objectFieldOffset0", "(Ljava/lang/reflect/Field;)J", Unsafe_objectFieldOffset0},
    {U, "arrayIndexScale0", "(Ljava/lang/Class;)I", Unsafe_arrayIndexScale0},
    {U, "compareAndSwapInt", "(Ljava/lang/Object;JII)Z", Unsafe_compareAndSwapInt},
    {U, "compareAndSwapLong", "(Ljava/lang/Object;JJJ)Z", Unsafe_compareAndSwapLong},
    {U, "compareAndSwapObject", "(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
     Unsafe_compareAndSwapObject},
    OBJ_ACCESS_REGS(Int, "I"),
    OBJ_ACCESS_REGS(Long, "J"),
    OBJ_ACCESS_REGS(Object, "Ljava/lang/Object;"),
    OBJ_ACCESS_REGS(Boolean, "Z"),
    OBJ_ACCESS_REGS(Byte, "B"),
    OBJ_ACCESS_REGS(Short, "S"),
    OBJ_ACCESS_REGS(Char, "C"),
    OBJ_ACCESS_REGS(Float, "F"),
    OBJ_ACCESS_REGS(Double, "D"),
    {U, "allocateInstance", "(Ljava/lang/Class;)Ljava/lang/Object;", Unsafe_allocateInstance},
    {U, "allocateMemory", "(J)J", Unsafe_allocateMemory},
    {U, "freeMemory", "(J)V", Unsafe_freeMemory},
    {U, "setMemory", "(JJB)V", Unsafe_setMemory},
    {U, "copyMemory", "(JJJ)V", Unsafe_copyMemory},
    ADDR_ACCESS_REGS(Byte, "B"),
    ADDR_ACCESS_REGS(Int, "I"),
    ADDR_ACCESS_REGS(Long, "J"),
    ADDR_ACCESS_REGS(Float, "F"),
    ADDR_ACCESS_REGS(Double, "D"),
    ADDR_ACCESS_REGS(Short, "S"),
    ADDR_ACCESS_REGS(Char, "C"),
};

void natives_java_unsafe_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
