/*
 * sun.misc.Unsafe (ART's subset): field and array element access by offset, compare-and-swap.
 * Natives run holding the GIL, so each one is atomic with respect to other Java threads.
 * Instance offsets are byte offsets from the object header (Field.offset). Array offsets are
 * UNSAFE_ARRAY_BASE plus index * element size, resolved against the array's data pointer
 * (element storage may be outside the object) and bounds-checked.
 */
#include "natives.h"

#define LOG_TAG "unsafe"

#define UNSAFE_ARRAY_BASE 16

/* Address of size bytes at offset in o, or NULL with an exception pending. */
static void *unsafe_addr(VMThread *t, Object *o, int64_t offset, int size) {
    if (!o) {
        vm_throw_npe(t, "Unsafe access to null object");
        return NULL;
    }
    Class *c = o->clazz;
    if (c->is_array) {
        ArrayObject *a = (ArrayObject *)o;
        int64_t rel = offset - UNSAFE_ARRAY_BASE;
        if (rel < 0 || rel + size > (int64_t)a->length * c->elem_size) {
            vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;", "Unsafe offset %lld in %s of length %d",
                         (long long)offset, c->name, a->length);
            return NULL;
        }
        return (uint8_t *)a->data + rel;
    }
    if (offset < (int64_t)sizeof(Object) || offset + size > (int64_t)c->instance_size) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "Unsafe offset %lld outside %s",
                     (long long)offset, c->name);
        return NULL;
    }
    return (uint8_t *)o + offset;
}

NATIVE(Unsafe_objectFieldOffset0) {
    UNUSED_ARGS();
    Object *fm = A_OBJ(0);
    if (!fm) {
        vm_throw_npe(t, "field");
        return;
    }
    Field *f = (Field *)(intptr_t)vm_get_long(fm, g_vm.wf.Field_vmField);
    if (f->access & ACC_STATIC) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "valid for instance fields only");
        return;
    }
    R_LONG(f->offset);
}

NATIVE(Unsafe_arrayIndexScale0) {
    UNUSED_ARGS();
    Object *m = A_OBJ(0);
    Class *c = m ? vm_class_from_mirror(m) : NULL;
    if (!c || !c->is_array) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "valid for array classes only");
        return;
    }
    R_INT(c->elem_size);
}

#define GETTER(name, type, R)                                                                                         \
    NATIVE(Unsafe_##name) {                                                                                           \
        UNUSED_ARGS();                                                                                                \
        type *p = unsafe_addr(t, A_OBJ(1), A_LONG(2), (int)sizeof(type));                                             \
        if (p) R(*p);                                                                                                 \
    }
#define SETTER(name, type, A)                                                                                         \
    NATIVE(Unsafe_##name) {                                                                                           \
        UNUSED_ARGS();                                                                                                \
        type *p = unsafe_addr(t, A_OBJ(1), A_LONG(2), (int)sizeof(type));                                             \
        if (p) *p = (type)A(4);                                                                                       \
    }

/* args: 0 = this, 1 = object, 2-3 = offset (wide), 4 = value */
GETTER(getInt, int32_t, R_INT)
GETTER(getLong, int64_t, R_LONG)
GETTER(getObject, Object *, R_OBJ)
GETTER(getByte, int8_t, R_INT)
GETTER(getShort, int16_t, R_INT)
GETTER(getChar, uint16_t, R_INT)
GETTER(getBoolean, uint8_t, R_BOOL)
SETTER(putInt, int32_t, A_INT)
SETTER(putLong, int64_t, A_LONG)
SETTER(putObject, Object *, A_OBJ)
SETTER(putByte, int8_t, A_INT)
SETTER(putShort, int16_t, A_INT)
SETTER(putChar, uint16_t, A_INT)
SETTER(putBoolean, uint8_t, A_BOOL)

/* args: 0 = this, 1 = object, 2-3 = offset, then expected and new values (wide values take two slots) */
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
    Object **p = unsafe_addr(t, A_OBJ(1), A_LONG(2), (int)sizeof(Object *));
    if (!p) return;
    bool ok = *p == A_OBJ(4);
    if (ok) *p = A_OBJ(5);
    R_BOOL(ok);
}

NATIVE(Unsafe_allocateInstance) {
    UNUSED_ARGS();
    Object *m = A_OBJ(1);
    Class *c = m ? vm_class_from_mirror(m) : NULL;
    if (!c) {
        vm_throw_npe(t, "class");
        return;
    }
    if (c->is_array || c->prim || (c->access & (ACC_ABSTRACT | ACC_INTERFACE))) {
        vm_throw_new(t, "Ljava/lang/InstantiationException;", "%s", c->name);
        return;
    }
    if (!vm_init_class(t, c)) return;
    R_OBJ(vm_alloc_object(t, c));
}

#define U "Lsun/misc/Unsafe;"
static const NativeMethodReg g_regs[] = {
    {U, "objectFieldOffset0", "(Ljava/lang/reflect/Field;)J", Unsafe_objectFieldOffset0},
    {U, "arrayIndexScale0", "(Ljava/lang/Class;)I", Unsafe_arrayIndexScale0},
    {U, "getInt", "(Ljava/lang/Object;J)I", Unsafe_getInt},
    {U, "getLong", "(Ljava/lang/Object;J)J", Unsafe_getLong},
    {U, "getObject", "(Ljava/lang/Object;J)Ljava/lang/Object;", Unsafe_getObject},
    {U, "getByte", "(Ljava/lang/Object;J)B", Unsafe_getByte},
    {U, "getShort", "(Ljava/lang/Object;J)S", Unsafe_getShort},
    {U, "getChar", "(Ljava/lang/Object;J)C", Unsafe_getChar},
    {U, "getBoolean", "(Ljava/lang/Object;J)Z", Unsafe_getBoolean},
    {U, "putInt", "(Ljava/lang/Object;JI)V", Unsafe_putInt},
    {U, "putLong", "(Ljava/lang/Object;JJ)V", Unsafe_putLong},
    {U, "putObject", "(Ljava/lang/Object;JLjava/lang/Object;)V", Unsafe_putObject},
    {U, "putByte", "(Ljava/lang/Object;JB)V", Unsafe_putByte},
    {U, "putShort", "(Ljava/lang/Object;JS)V", Unsafe_putShort},
    {U, "putChar", "(Ljava/lang/Object;JC)V", Unsafe_putChar},
    {U, "putBoolean", "(Ljava/lang/Object;JZ)V", Unsafe_putBoolean},
    {U, "compareAndSwapInt", "(Ljava/lang/Object;JII)Z", Unsafe_compareAndSwapInt},
    {U, "compareAndSwapLong", "(Ljava/lang/Object;JJJ)Z", Unsafe_compareAndSwapLong},
    {U, "compareAndSwapObject", "(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
     Unsafe_compareAndSwapObject},
    {U, "allocateInstance", "(Ljava/lang/Class;)Ljava/lang/Object;", Unsafe_allocateInstance},
};

void natives_java_unsafe_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
