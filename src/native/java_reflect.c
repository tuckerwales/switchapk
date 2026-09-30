/*
 * Reflection natives: java.lang.reflect.{Method,Constructor,Field,Array,Proxy}
 * and the Class.getDeclared*() helpers.
 */
#include "natives.h"

#define LOG_TAG "reflect"

/* Splits a method descriptor into parameter descriptors. Returns count; out[i] are interned. */
int reflect_parse_params(const char *desc, const char **out, int max) {
    int n = 0;
    const char *p = desc + 1;
    while (*p && *p != ')') {
        const char *start = p;
        while (*p == '[') p++;
        if (*p == 'L') {
            while (*p && *p != ';') p++;
        }
        p++;
        if (n < max) out[n] = sa_intern_n(start, (size_t)(p - start));
        n++;
    }
    return n;
}

static const char *return_desc(const char *desc) {
    const char *r = strchr(desc, ')');
    return r ? r + 1 : "V";
}

static Object *class_array(VMThread *t, const char **descs, int n) {
    Class *arrc = vm_array_class_of(t, g_vm.wk.Class);
    if (!arrc) return NULL;
    ArrayObject *a = vm_alloc_array(t, arrc, n);
    if (!a) return NULL;
    for (int i = 0; i < n; i++) {
        Class *c = vm_find_class(t, descs[i]);
        if (!c) return NULL;
        ARRAY_DATA(a, Object *)[i] = vm_class_mirror(t, c);
    }
    return (Object *)a;
}

static void set_by_name(Object *o, const char *name, JValue v) {
    Field *f = vm_find_field(o->clazz, name, NULL);
    if (f) vm_field_set(o, f, v);
}

static Object *make_executable(VMThread *t, Method *m, bool ctor) {
    if (m->reflect) return m->reflect;
    Class *rc = ctor ? g_vm.wk.reflect_Constructor : g_vm.wk.reflect_Method;
    if (!rc || !vm_init_class(t, rc)) return NULL;
    const char *params[256];
    int np = reflect_parse_params(m->desc, params, 256);
    if (np > 256) np = 256;
    Object *ptypes = class_array(t, params, np);
    if (!ptypes) return NULL;
    Class *ret = vm_find_class(t, return_desc(m->desc));
    if (!ret) return NULL;
    Object *o = vm_alloc_object(t, rc);
    if (!o) return NULL;
    JValue v;
    v.raw = 0;
    v.j = (int64_t)(intptr_t)m;
    set_by_name(o, "vmMethod", v);
    v.raw = 0;
    v.l = vm_class_mirror(t, m->clazz);
    set_by_name(o, "declaringClass", v);
    v.l = vm_new_string_mutf8(t, m->name);
    set_by_name(o, "name", v);
    v.l = ptypes;
    set_by_name(o, "parameterTypes", v);
    v.l = vm_class_mirror(t, ret);
    set_by_name(o, "returnType", v);
    v.raw = 0;
    v.i = (int32_t)(m->access & 0xffff);
    set_by_name(o, "modifiers", v);
    m->reflect = o;
    return o;
}

static Object *make_field(VMThread *t, Field *f) {
    Class *fc = g_vm.wk.reflect_Field;
    if (!fc || !vm_init_class(t, fc)) return NULL;
    Class *type = vm_find_class(t, f->type);
    if (!type) return NULL;
    Object *o = vm_alloc_object(t, fc);
    if (!o) return NULL;
    JValue v;
    v.raw = 0;
    v.j = (int64_t)(intptr_t)f;
    set_by_name(o, "vmField", v);
    v.raw = 0;
    v.l = vm_class_mirror(t, f->clazz);
    set_by_name(o, "declaringClass", v);
    v.l = vm_new_string_mutf8(t, f->name);
    set_by_name(o, "name", v);
    v.l = vm_class_mirror(t, type);
    set_by_name(o, "type", v);
    v.raw = 0;
    v.i = (int32_t)(f->access & 0xffff);
    set_by_name(o, "modifiers", v);
    return o;
}

NATIVE(Class_getDeclaredMethodsNative) {
    UNUSED_ARGS();
    Class *c = vm_class_from_mirror(A_OBJ(0));
    uint32_t n = 0;
    for (uint32_t i = 0; i < c->nmethods; i++)
        if (c->methods[i].name[0] != '<') n++;
    Class *arrc = vm_array_class_of(t, g_vm.wk.reflect_Method);
    ArrayObject *a = vm_alloc_array(t, arrc, (int32_t)n);
    if (!a) return;
    uint32_t k = 0;
    for (uint32_t i = 0; i < c->nmethods; i++) {
        Method *m = &c->methods[i];
        if (m->name[0] == '<') continue;
        Object *o = make_executable(t, m, false);
        if (!o) return;
        ARRAY_DATA(a, Object *)[k++] = o;
    }
    R_OBJ(a);
}

NATIVE(Class_getDeclaredConstructorsNative) {
    UNUSED_ARGS();
    Class *c = vm_class_from_mirror(A_OBJ(0));
    uint32_t n = 0;
    for (uint32_t i = 0; i < c->nmethods; i++)
        if (!strcmp(c->methods[i].name, "<init>")) n++;
    Class *arrc = vm_array_class_of(t, g_vm.wk.reflect_Constructor);
    ArrayObject *a = vm_alloc_array(t, arrc, (int32_t)n);
    if (!a) return;
    uint32_t k = 0;
    for (uint32_t i = 0; i < c->nmethods; i++) {
        Method *m = &c->methods[i];
        if (strcmp(m->name, "<init>")) continue;
        Object *o = make_executable(t, m, true);
        if (!o) return;
        ARRAY_DATA(a, Object *)[k++] = o;
    }
    R_OBJ(a);
}

NATIVE(Class_getDeclaredFieldsNative) {
    UNUSED_ARGS();
    Class *c = vm_class_from_mirror(A_OBJ(0));
    uint32_t n = c->nsfields + c->nifields;
    Class *arrc = vm_array_class_of(t, g_vm.wk.reflect_Field);
    ArrayObject *a = vm_alloc_array(t, arrc, (int32_t)n);
    if (!a) return;
    uint32_t k = 0;
    for (uint32_t i = 0; i < c->nifields; i++) {
        Object *o = make_field(t, &c->ifields[i]);
        if (!o) return;
        ARRAY_DATA(a, Object *)[k++] = o;
    }
    for (uint32_t i = 0; i < c->nsfields; i++) {
        Object *o = make_field(t, &c->sfields[i]);
        if (!o) return;
        ARRAY_DATA(a, Object *)[k++] = o;
    }
    R_OBJ(a);
}

/* Converts a reflection argument array into register slots. Returns slot count or -1. */
static int unpack_args(VMThread *t, Method *m, Object *recv, ArrayObject *jargs, uint64_t *slots) {
    int s = 0;
    if (!(m->access & ACC_STATIC)) slots[s++] = (uint64_t)(uintptr_t)recv;
    int32_t nargs = jargs ? jargs->length : 0;
    int ai = 0;
    for (const char *p = m->shorty + 1; *p; p++, ai++) {
        if (ai >= nargs) {
            vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "wrong number of arguments");
            return -1;
        }
        Object *arg = ARRAY_DATA(jargs, Object *)[ai];
        JValue v;
        if (*p == 'L') {
            /* type check against declared parameter type */
            const char *params[256];
            int np = reflect_parse_params(m->desc, params, 256);
            if (arg && ai < np) {
                Class *pc = vm_find_class_noexc(t, params[ai]);
                if (pc && !vm_instance_of(arg, pc)) {
                    vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "argument %d type mismatch: %s", ai,
                                 arg->clazz->name);
                    return -1;
                }
            }
            slots[s++] = (uint64_t)(uintptr_t)arg;
            continue;
        }
        if (!vm_unbox(t, arg, *p, &v)) return -1;
        if (*p == 'J' || *p == 'D') {
            slots[s++] = (uint64_t)v.j;
            slots[s++] = 0;
        } else {
            slots[s++] = (uint32_t)v.i;
        }
    }
    if (ai != nargs) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "wrong number of arguments");
        return -1;
    }
    return s;
}

static void wrap_invocation_exception(VMThread *t) {
    Object *cause = t->exception;
    t->exception = NULL;
    Object *ite = vm_new_instance(t, "Ljava/lang/reflect/InvocationTargetException;", "(Ljava/lang/Throwable;)V", cause);
    t->exception = ite ? ite : cause;
}

NATIVE(Method_invokeNative) {
    UNUSED_ARGS();
    Object *self = A_OBJ(0);
    Object *recv = A_OBJ(1);
    ArrayObject *jargs = A_ARR(2);
    Method *m = (Method *)(intptr_t)vm_get_long(self, g_vm.wf.Method_vmMethod);
    if (m->access & ACC_STATIC) {
        if (!vm_init_class(t, m->clazz)) return;
    } else if (!(m->access & ACC_PRIVATE)) {
        Method *impl = vm_find_virtual(recv->clazz, m->name, m->desc);
        if (impl) m = impl;
    }
    uint64_t slots[260];
    if (unpack_args(t, m, recv, jargs, slots) < 0) return;
    JValue r = vm_invoke(t, m, slots);
    if (t->exception) {
        wrap_invocation_exception(t);
        return;
    }
    char rt = m->shorty[0];
    if (rt == 'V') R_OBJ(NULL);
    else if (rt == 'L') R_OBJ(r.l);
    else R_OBJ(vm_box(t, rt, r));
}

NATIVE(Constructor_newInstanceNative) {
    UNUSED_ARGS();
    Object *self = A_OBJ(0);
    ArrayObject *jargs = A_ARR(1);
    Method *m = (Method *)(intptr_t)vm_get_long(self, g_vm.wf.Constructor_vmMethod);
    Class *c = m->clazz;
    if (c->access & (ACC_ABSTRACT | ACC_INTERFACE)) {
        vm_throw_new(t, "Ljava/lang/InstantiationException;", "%s", c->name);
        return;
    }
    if (!vm_init_class(t, c)) return;
    Object *o = vm_alloc_object(t, c);
    if (!o) return;
    uint64_t slots[260];
    if (unpack_args(t, m, o, jargs, slots) < 0) return;
    vm_invoke(t, m, slots);
    if (t->exception) {
        wrap_invocation_exception(t);
        return;
    }
    R_OBJ(o);
}

static Field *field_of(Object *self) { return (Field *)(intptr_t)vm_get_long(self, g_vm.wf.Field_vmField); }

NATIVE(Field_getNative) {
    UNUSED_ARGS();
    Field *f = field_of(A_OBJ(0));
    Object *obj = A_OBJ(1);
    if (f->access & ACC_STATIC) {
        if (!vm_init_class(t, f->clazz)) return;
        obj = NULL;
    }
    JValue v = vm_field_get(obj, f);
    if (f->kind == 'L' || f->kind == '[') R_OBJ(v.l);
    else R_OBJ(vm_box(t, f->kind, v));
}

NATIVE(Field_setNative) {
    UNUSED_ARGS();
    Field *f = field_of(A_OBJ(0));
    Object *obj = A_OBJ(1);
    Object *val = A_OBJ(2);
    if (f->access & ACC_STATIC) {
        if (!vm_init_class(t, f->clazz)) return;
        obj = NULL;
    }
    JValue v;
    if (f->kind == 'L' || f->kind == '[') {
        Class *fc = vm_find_class_noexc(t, f->type);
        if (val && fc && !vm_instance_of(val, fc)) {
            vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "field %s.%s of type %s cannot be set to %s",
                         f->clazz->name, f->name, f->type, val->clazz->name);
            return;
        }
        v.raw = 0;
        v.l = val;
    } else if (!vm_unbox(t, val, f->kind, &v)) {
        return;
    }
    vm_field_set(obj, f, v);
}

/* ---- java.lang.reflect.Array ---------------------------------------------------------- */

NATIVE(Array_newArray) {
    UNUSED_ARGS();
    Object *cm = A_OBJ(0);
    if (!cm) {
        vm_throw_npe(t, "componentType");
        return;
    }
    Class *comp = vm_class_from_mirror(cm);
    if (comp->prim == 'V') {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "void");
        return;
    }
    Class *ac = vm_array_class_of(t, comp);
    if (!ac) return;
    R_OBJ(vm_alloc_array(t, ac, A_INT(1)));
}

static ArrayObject *check_array(VMThread *t, Object *o) {
    if (!o) {
        vm_throw_npe(t, "array");
        return NULL;
    }
    if (!o->clazz->is_array) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "Argument is not an array");
        return NULL;
    }
    return (ArrayObject *)o;
}

NATIVE(Array_getLength) {
    UNUSED_ARGS();
    ArrayObject *a = check_array(t, A_OBJ(0));
    if (a) R_INT(a->length);
}

NATIVE(Array_get) {
    UNUSED_ARGS();
    ArrayObject *a = check_array(t, A_OBJ(0));
    if (!a) return;
    int32_t i = A_INT(1);
    if (i < 0 || i >= a->length) {
        vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;", "length=%d; index=%d", a->length, i);
        return;
    }
    Class *comp = a->obj.clazz->component;
    if (!comp->prim) {
        R_OBJ(ARRAY_DATA(a, Object *)[i]);
        return;
    }
    JValue v;
    v.raw = 0;
    switch (comp->prim) {
    case 'Z': v.i = ARRAY_DATA(a, uint8_t)[i]; break;
    case 'B': v.i = ARRAY_DATA(a, int8_t)[i]; break;
    case 'C': v.i = ARRAY_DATA(a, uint16_t)[i]; break;
    case 'S': v.i = ARRAY_DATA(a, int16_t)[i]; break;
    case 'I':
    case 'F': v.i = ARRAY_DATA(a, int32_t)[i]; break;
    default: v.j = ARRAY_DATA(a, int64_t)[i]; break;
    }
    R_OBJ(vm_box(t, comp->prim, v));
}

NATIVE(Array_set) {
    UNUSED_ARGS();
    ArrayObject *a = check_array(t, A_OBJ(0));
    if (!a) return;
    int32_t i = A_INT(1);
    Object *val = A_OBJ(2);
    if (i < 0 || i >= a->length) {
        vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;", "length=%d; index=%d", a->length, i);
        return;
    }
    Class *comp = a->obj.clazz->component;
    if (!comp->prim) {
        if (val && !vm_instance_of(val, comp)) {
            vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "array element type mismatch");
            return;
        }
        ARRAY_DATA(a, Object *)[i] = val;
        return;
    }
    JValue v;
    if (!vm_unbox(t, val, comp->prim, &v)) return;
    switch (comp->prim) {
    case 'Z':
    case 'B': ARRAY_DATA(a, int8_t)[i] = (int8_t)v.i; break;
    case 'C':
    case 'S': ARRAY_DATA(a, int16_t)[i] = (int16_t)v.i; break;
    case 'I':
    case 'F': ARRAY_DATA(a, int32_t)[i] = v.i; break;
    default: ARRAY_DATA(a, int64_t)[i] = v.j; break;
    }
}

/* ---- java.lang.reflect.Proxy ------------------------------------------------------------ */
/*
 * Proxy classes are synthesized in memory: a Class that extends
 * java.lang.reflect.Proxy, implements the requested interfaces and whose
 * methods are all natives routing to Proxy.invokeHandler().
 */

static void proxy_dispatch(VMThread *t, uint64_t *args, JValue *ret);

static int g_proxy_counter;

NATIVE(Proxy_generateProxy) {
    UNUSED_ARGS();
    ArrayObject *ifaces = A_ARR(0);
    Class *proxy_base = vm_find_class(t, "Ljava/lang/reflect/Proxy;");
    if (!proxy_base || !ifaces) return;
    Class *c = sa_calloc(1, sizeof *c);
    char name[64];
    snprintf(name, sizeof name, "$Proxy%d", g_proxy_counter++);
    char *desc = sa_sprintf("L%s;", name);
    c->descriptor = sa_intern(desc);
    free(desc);
    c->name = sa_intern(name);
    c->access = ACC_PUBLIC | ACC_FINAL;
    c->super = proxy_base;
    c->state = CLASS_INITIALIZED;
    c->class_def_idx = -1;
    c->ninterfaces = (uint32_t)ifaces->length;
    c->interfaces = sa_calloc(c->ninterfaces + 1, sizeof(Class *));
    SaVec all = {0};
    for (uint32_t i = 0; i < c->ninterfaces; i++) {
        Class *ic = vm_class_from_mirror(ARRAY_DATA(ifaces, Object *)[i]);
        c->interfaces[i] = ic;
        sa_vec_push(&all, ic);
        for (uint32_t k = 0; k < ic->nall_interfaces; k++) sa_vec_push(&all, ic->all_interfaces[k]);
    }
    c->all_interfaces = (Class **)all.items;
    c->nall_interfaces = (uint32_t)all.len;
    /* one native method per interface method (plus Object's equals/hashCode/toString) */
    SaVec ms = {0};
    for (size_t i = 0; i < all.len; i++) {
        Class *ic = all.items[i];
        for (uint32_t k = 0; k < ic->nmethods; k++) {
            Method *im = &ic->methods[k];
            if (im->access & ACC_STATIC || im->name[0] == '<') continue;
            bool dup = false;
            for (size_t j = 0; j < ms.len; j++) {
                Method *o = ms.items[j];
                if (o->name == im->name && o->desc == im->desc) dup = true;
            }
            if (!dup) sa_vec_push(&ms, im);
        }
    }
    const char *obj_methods[][2] = {{"equals", "(Ljava/lang/Object;)Z"}, {"hashCode", "()I"}, {"toString", "()Ljava/lang/String;"}};
    c->nmethods = (uint32_t)ms.len + 3;
    c->methods = sa_calloc(c->nmethods, sizeof(Method));
    for (uint32_t i = 0; i < c->nmethods; i++) {
        Method *m = &c->methods[i];
        if (i < ms.len) {
            Method *im = ms.items[i];
            *m = *im;
            m->reflect = NULL;
            m->jni_fn = im; /* remember the interface method for the handler */
        } else {
            Method *om = vm_find_method(g_vm.wk.Object, obj_methods[i - ms.len][0], obj_methods[i - ms.len][1]);
            *m = *om;
            m->reflect = NULL;
            m->jni_fn = om;
        }
        m->clazz = c;
        m->access = ACC_PUBLIC | ACC_FINAL | ACC_NATIVE;
        m->has_code = false;
        m->native = proxy_dispatch;
    }
    sa_vec_free(&ms);
    c->instance_size = proxy_base->instance_size;
    c->ref_offsets = proxy_base->ref_offsets;
    c->nref_offsets = proxy_base->nref_offsets;
    /* vtable: inherit Proxy's, override by name/desc */
    c->vtable_len = proxy_base->vtable_len;
    c->vtable = sa_calloc(c->vtable_len + c->nmethods + 1, sizeof(Method *));
    memcpy(c->vtable, proxy_base->vtable, proxy_base->vtable_len * sizeof(Method *));
    for (uint32_t i = 0; i < c->nmethods; i++) {
        Method *m = &c->methods[i];
        m->vtable_index = -1;
        for (uint32_t k = 0; k < c->vtable_len; k++) {
            if (c->vtable[k]->name == m->name && c->vtable[k]->desc == m->desc) {
                c->vtable[k] = m;
                m->vtable_index = (int32_t)k;
            }
        }
        if (m->vtable_index < 0) {
            m->vtable_index = (int32_t)c->vtable_len;
            c->vtable[c->vtable_len++] = m;
        }
    }
    c->static_data = sa_calloc(1, 8);
    sa_map_put(&g_vm.classes, c->descriptor, c);
    R_OBJ(vm_class_mirror(t, c));
}

NATIVE(Proxy_newProxyNative) {
    UNUSED_ARGS();
    Class *c = vm_class_from_mirror(A_OBJ(0));
    Object *o = vm_alloc_object(t, c);
    if (!o) return;
    vm_set_ref_by_name(o, "h", A_OBJ(1));
    R_OBJ(o);
}

static void proxy_dispatch(VMThread *t, uint64_t *args, JValue *ret) {
    Object *self = A_OBJ(0);
    /* the executing proxy method remembers its interface method in jni_fn */
    Method *pm = vm_current_native(t);
    Method *im = pm ? (Method *)pm->jni_fn : NULL;
    if (!im) {
        vm_throw_new(t, "Ljava/lang/InternalError;", "proxy dispatch failed");
        return;
    }
    Object *jm = make_executable(t, im, false);
    if (!jm) return;
    /* box arguments */
    int n = 0;
    for (const char *p = im->shorty + 1; *p; p++) n++;
    ArrayObject *arr = NULL;
    if (n) {
        arr = vm_alloc_array(t, g_vm.wk.arr_Object, n);
        if (!arr) return;
        int s = 1, i = 0;
        for (const char *p = im->shorty + 1; *p; p++, i++) {
            JValue v;
            v.raw = args[s];
            if (*p == 'L') {
                ARRAY_DATA(arr, Object *)[i] = v.l;
                s++;
            } else {
                if (*p != 'J' && *p != 'D') v.raw = (uint32_t)v.raw;
                Object *b = vm_box(t, *p, v);
                if (!b) return;
                ARRAY_DATA(arr, Object *)[i] = b;
                s += (*p == 'J' || *p == 'D') ? 2 : 1;
            }
        }
    }
    JValue r = vm_call_static(t, "Ljava/lang/reflect/Proxy;", "invokeHandler",
                              "(Ljava/lang/reflect/Proxy;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;",
                              self, jm, (Object *)arr);
    if (t->exception) {
        Object *e = t->exception;
        bool unchecked = vm_instance_of(e, vm_find_class_noexc(t, "Ljava/lang/RuntimeException;")) ||
                         vm_instance_of(e, vm_find_class_noexc(t, "Ljava/lang/Error;"));
        if (!unchecked) {
            t->exception = NULL;
            Object *w = vm_new_instance(t, "Ljava/lang/reflect/UndeclaredThrowableException;", "(Ljava/lang/Throwable;)V", e);
            t->exception = w ? w : e;
        }
        return;
    }
    char rt = im->shorty[0];
    if (rt == 'V') return;
    if (rt == 'L') {
        R_OBJ(r.l);
        return;
    }
    JValue u;
    if (!vm_unbox(t, r.l, rt, &u)) return;
    *ret = u;
}

static const NativeMethodReg g_regs[] = {
    {"Ljava/lang/Class;", "getDeclaredMethodsNative", "()[Ljava/lang/reflect/Method;", Class_getDeclaredMethodsNative},
    {"Ljava/lang/Class;", "getDeclaredConstructorsNative", "()[Ljava/lang/reflect/Constructor;",
     Class_getDeclaredConstructorsNative},
    {"Ljava/lang/Class;", "getDeclaredFieldsNative", "()[Ljava/lang/reflect/Field;", Class_getDeclaredFieldsNative},
    {"Ljava/lang/reflect/Method;", "invokeNative", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
     Method_invokeNative},
    {"Ljava/lang/reflect/Constructor;", "newInstanceNative", "([Ljava/lang/Object;)Ljava/lang/Object;",
     Constructor_newInstanceNative},
    {"Ljava/lang/reflect/Field;", "getNative", "(Ljava/lang/Object;)Ljava/lang/Object;", Field_getNative},
    {"Ljava/lang/reflect/Field;", "setNative", "(Ljava/lang/Object;Ljava/lang/Object;)V", Field_setNative},
    {"Ljava/lang/reflect/Array;", "newArray", "(Ljava/lang/Class;I)Ljava/lang/Object;", Array_newArray},
    {"Ljava/lang/reflect/Array;", "getLength", "(Ljava/lang/Object;)I", Array_getLength},
    {"Ljava/lang/reflect/Array;", "get", "(Ljava/lang/Object;I)Ljava/lang/Object;", Array_get},
    {"Ljava/lang/reflect/Array;", "set", "(Ljava/lang/Object;ILjava/lang/Object;)V", Array_set},
    {"Ljava/lang/reflect/Proxy;", "generateProxy", "([Ljava/lang/Class;)Ljava/lang/Class;", Proxy_generateProxy},
    {"Ljava/lang/reflect/Proxy;", "newProxyNative",
     "(Ljava/lang/Class;Ljava/lang/reflect/InvocationHandler;)Ljava/lang/Object;", Proxy_newProxyNative},
};

void natives_java_reflect_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }

Object *reflect_method_object(VMThread *t, Method *m) {
    return make_executable(t, m, !strcmp(m->name, "<init>"));
}

Object *reflect_field_object(VMThread *t, Field *f) { return make_field(t, f); }
