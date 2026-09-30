/*
 * The Java Native Interface.
 *
 * References: objects never move, so a local or global reference is simply
 * the object pointer. Local references are recorded in a per-thread table
 * that the GC treats as roots; weak globals are tagged pointers to slots the
 * GC clears. Native code runs without the GIL; each JNI entry point
 * re-acquires it, so native threads can block on each other freely.
 */
#include "vm.h"
#include "jni_types.h"
#include "nativecall.h"

#define LOG_TAG "jni"

void *nativeloader_find_symbol(const char *name);
Object *reflect_method_object(VMThread *t, Method *m);
Object *reflect_field_object(VMThread *t, Field *f);

static const void *g_jni_table[240];
static const void *g_invoke_table[8];
static SaJavaVM g_java_vm = {g_invoke_table};
static bool g_tables_ready;
static void jni_tables_init(void);

/* ---- references ------------------------------------------------------------------------ */

static inline Object *D(jobject r) {
    if (!r) return NULL;
    if ((uintptr_t)r & 1) return *(Object **)((uintptr_t)r & ~(uintptr_t)1);
    return (Object *)r;
}

void *vm_jni_new_local(VMThread *t, Object *o) {
    if (!o) return NULL;
    if (t->nlocal_refs < LOCAL_REF_CAPACITY) {
        t->local_refs[t->nlocal_refs++] = o;
    } else {
        static bool warned;
        if (!warned) {
            warned = true;
            LOGW("local reference table overflow (%d entries)", LOCAL_REF_CAPACITY);
        }
    }
    return o;
}

Object *vm_jni_decode(VMThread *t, void *ref) {
    SA_UNUSED(t);
    return D(ref);
}

void vm_jni_push_frame(VMThread *t) {
    if (t->nlocal_frames == t->cap_local_frames) {
        t->cap_local_frames *= 2;
        t->local_frames = sa_realloc(t->local_frames, t->cap_local_frames * sizeof(uint32_t));
    }
    t->local_frames[t->nlocal_frames++] = t->nlocal_refs;
}

void vm_jni_pop_frame(VMThread *t) {
    if (t->nlocal_frames) t->nlocal_refs = t->local_frames[--t->nlocal_frames];
}

static void *new_global(Object *o) {
    if (!o) return NULL;
    for (uint32_t i = 0; i < g_vm.nglobal_refs; i++) {
        if (!g_vm.global_refs[i]) {
            g_vm.global_refs[i] = o;
            return o;
        }
    }
    if (g_vm.nglobal_refs == g_vm.cap_global_refs) {
        g_vm.cap_global_refs = g_vm.cap_global_refs ? g_vm.cap_global_refs * 2 : 256;
        g_vm.global_refs = sa_realloc(g_vm.global_refs, g_vm.cap_global_refs * sizeof(Object *));
    }
    g_vm.global_refs[g_vm.nglobal_refs++] = o;
    return o;
}

static void delete_global(Object *o) {
    if (!o) return;
    for (uint32_t i = g_vm.nglobal_refs; i-- > 0;) {
        if (g_vm.global_refs[i] == o) {
            g_vm.global_refs[i] = NULL;
            return;
        }
    }
}

/* ---- env plumbing ------------------------------------------------------------------------ */

void *vm_jni_env(VMThread *t) {
    if (!g_tables_ready) jni_tables_init();
    if (t->jni_env) return t->jni_env;
    SaJNIEnv *e = sa_calloc(1, sizeof *e);
    e->functions = g_jni_table;
    e->thread = t;
    t->jni_env = e;
    return e;
}

void *vm_java_vm(void) {
    if (!g_tables_ready) jni_tables_init();
    return &g_java_vm;
}

#define ENV_THREAD(env) (((SaJNIEnv *)(env))->thread)
#define ENTER()                              \
    VMThread *t = ENV_THREAD(env);           \
    bool _had_gil = t->has_gil;              \
    if (!_had_gil) vm_gil_acquire(t)
#define LEAVE()                        \
    do {                               \
        if (!_had_gil) vm_gil_release(t); \
    } while (0)
#define L(o) vm_jni_new_local(t, (o))

/* ---- argument marshalling ---------------------------------------------------------------- */

static int collect_va(Method *m, va_list ap, JValue *out) {
    int n = 0;
    for (const char *p = m->shorty + 1; *p; p++) {
        JValue v;
        v.raw = 0;
        switch (*p) {
        case 'J': v.j = va_arg(ap, jlong); break;
        case 'D': v.d = va_arg(ap, double); break;
        case 'F': v.f = (float)va_arg(ap, double); break;
        case 'L': v.l = D(va_arg(ap, jobject)); break;
        default: v.i = va_arg(ap, int); break;
        }
        out[n++] = v;
    }
    return n;
}

static int collect_a(Method *m, const jvalue *a, JValue *out) {
    int n = 0;
    for (const char *p = m->shorty + 1; *p; p++, a++) {
        JValue v;
        v.raw = 0;
        switch (*p) {
        case 'Z': v.i = a->z; break;
        case 'B': v.i = a->b; break;
        case 'C': v.i = a->c; break;
        case 'S': v.i = a->s; break;
        case 'I': v.i = a->i; break;
        case 'J': v.j = a->j; break;
        case 'F': v.f = a->f; break;
        case 'D': v.d = a->d; break;
        default: v.l = D(a->l); break;
        }
        out[n++] = v;
    }
    return n;
}

enum { CALL_VIRTUAL, CALL_NONVIRTUAL, CALL_STATIC };

static JValue do_call(VMThread *t, int kind, Object *recv, Method *m, JValue *params) {
    JValue args[260];
    JValue z;
    z.raw = 0;
    if (!m) {
        vm_throw_new(t, "Ljava/lang/NoSuchMethodError;", "null jmethodID");
        return z;
    }
    int n = 0;
    if (kind != CALL_STATIC) {
        if (!recv) {
            vm_throw_npe(t, m->name);
            return z;
        }
        if (kind == CALL_VIRTUAL && !(m->access & ACC_PRIVATE) && m->name[0] != '<') {
            Method *impl = vm_find_virtual(recv->clazz, m->name, m->desc);
            if (impl) m = impl;
        }
        args[n++].l = recv;
    } else if (m->clazz->state != CLASS_INITIALIZED && !vm_init_class(t, m->clazz)) {
        return z;
    }
    int np = 0;
    for (const char *p = m->shorty + 1; *p; p++) np++;
    for (int i = 0; i < np; i++) args[n + i] = params[i];
    return vm_callv(t, m, args);
}

/* ---- JNI functions ------------------------------------------------------------------------- */

static jint JNICALL_GetVersion(void *env) {
    SA_UNUSED(env);
    return JNI_VERSION_1_6;
}

static jclass JNICALL_DefineClass(void *env, const char *name, jobject loader, const jbyte *buf, jsize len) {
    SA_UNUSED(loader);
    SA_UNUSED(buf);
    SA_UNUSED(len);
    ENTER();
    vm_throw_new(t, "Ljava/lang/UnsupportedOperationException;", "DefineClass(%s) is not supported", name ? name : "?");
    LEAVE();
    return NULL;
}

static jclass JNICALL_FindClass(void *env, const char *name) {
    ENTER();
    jclass r = NULL;
    char *desc = name[0] == '[' ? sa_strdup(name) : sa_sprintf("L%s;", name);
    for (char *p = desc; *p; p++)
        if (*p == '.') *p = '/';
    Class *c = vm_find_class(t, desc);
    free(desc);
    if (c && vm_init_class(t, c)) r = L(vm_class_mirror(t, c));
    LEAVE();
    return r;
}

static jmethodID JNICALL_FromReflectedMethod(void *env, jobject method) {
    ENTER();
    Object *o = D(method);
    Method *m = NULL;
    if (o) {
        Field *f = vm_find_field(o->clazz, "vmMethod", "J");
        if (f) m = (Method *)(intptr_t)vm_get_long(o, f);
    }
    LEAVE();
    return m;
}

static jfieldID JNICALL_FromReflectedField(void *env, jobject field) {
    ENTER();
    Object *o = D(field);
    Field *r = o && g_vm.wf.Field_vmField ? (Field *)(intptr_t)vm_get_long(o, g_vm.wf.Field_vmField) : NULL;
    LEAVE();
    return r;
}

static jobject JNICALL_ToReflectedMethod(void *env, jclass cls, jmethodID mid, jboolean isStatic) {
    SA_UNUSED(cls);
    SA_UNUSED(isStatic);
    ENTER();
    jobject r = L(reflect_method_object(t, mid));
    LEAVE();
    return r;
}

static jclass JNICALL_GetSuperclass(void *env, jclass cls) {
    ENTER();
    Class *c = vm_class_from_mirror(D(cls));
    jclass r = (c && c->super && !(c->access & ACC_INTERFACE)) ? L(vm_class_mirror(t, c->super)) : NULL;
    LEAVE();
    return r;
}

static jboolean JNICALL_IsAssignableFrom(void *env, jclass a, jclass b) {
    ENTER();
    jboolean r = vm_is_assignable(vm_class_from_mirror(D(b)), vm_class_from_mirror(D(a)));
    LEAVE();
    return r;
}

static jobject JNICALL_ToReflectedField(void *env, jclass cls, jfieldID fid, jboolean isStatic) {
    SA_UNUSED(cls);
    SA_UNUSED(isStatic);
    ENTER();
    jobject r = L(reflect_field_object(t, fid));
    LEAVE();
    return r;
}

static jint JNICALL_Throw(void *env, jthrowable obj) {
    ENTER();
    t->exception = D(obj);
    LEAVE();
    return 0;
}

static jint JNICALL_ThrowNew(void *env, jclass cls, const char *msg) {
    ENTER();
    Class *c = vm_class_from_mirror(D(cls));
    Object *s = msg ? vm_new_string_mutf8(t, msg) : NULL;
    Object *e = vm_new_instance(t, c->descriptor, "(Ljava/lang/String;)V", s);
    if (e) t->exception = e;
    LEAVE();
    return e ? 0 : -1;
}

static jthrowable JNICALL_ExceptionOccurred(void *env) {
    ENTER();
    jthrowable r = L(t->exception);
    LEAVE();
    return r;
}

static void JNICALL_ExceptionDescribe(void *env) {
    ENTER();
    if (t->exception) vm_print_exception(t, t->exception);
    LEAVE();
}

static void JNICALL_ExceptionClear(void *env) {
    ENTER();
    t->exception = NULL;
    LEAVE();
}

static void JNICALL_FatalError(void *env, const char *msg) {
    SA_UNUSED(env);
    sa_fatal("JNI FatalError: %s", msg ? msg : "(null)");
}

static jint JNICALL_PushLocalFrame(void *env, jint cap) {
    SA_UNUSED(cap);
    ENTER();
    vm_jni_push_frame(t);
    LEAVE();
    return 0;
}

static jobject JNICALL_PopLocalFrame(void *env, jobject result) {
    ENTER();
    Object *o = D(result);
    vm_jni_pop_frame(t);
    jobject r = L(o);
    LEAVE();
    return r;
}

static jobject JNICALL_NewGlobalRef(void *env, jobject obj) {
    ENTER();
    jobject r = new_global(D(obj));
    LEAVE();
    return r;
}

static void JNICALL_DeleteGlobalRef(void *env, jobject obj) {
    ENTER();
    delete_global(D(obj));
    LEAVE();
}

static void JNICALL_DeleteLocalRef(void *env, jobject obj) {
    ENTER();
    Object *o = D(obj);
    if (o) {
        uint32_t floor = t->nlocal_frames ? t->local_frames[t->nlocal_frames - 1] : 0;
        for (uint32_t i = t->nlocal_refs; i-- > floor;) {
            if (t->local_refs[i] == o) {
                t->local_refs[i] = NULL;
                if (i == t->nlocal_refs - 1) t->nlocal_refs--;
                break;
            }
        }
    }
    LEAVE();
}

static jboolean JNICALL_IsSameObject(void *env, jobject a, jobject b) {
    ENTER();
    jboolean r = D(a) == D(b);
    LEAVE();
    return r;
}

static jobject JNICALL_NewLocalRef(void *env, jobject obj) {
    ENTER();
    jobject r = L(D(obj));
    LEAVE();
    return r;
}

static jint JNICALL_EnsureLocalCapacity(void *env, jint cap) {
    SA_UNUSED(env);
    SA_UNUSED(cap);
    return 0;
}

static jobject JNICALL_AllocObject(void *env, jclass cls) {
    ENTER();
    jobject r = NULL;
    Class *c = vm_class_from_mirror(D(cls));
    if (c->access & (ACC_ABSTRACT | ACC_INTERFACE)) vm_throw_new(t, "Ljava/lang/InstantiationException;", "%s", c->name);
    else if (vm_init_class(t, c)) r = L(vm_alloc_object(t, c));
    LEAVE();
    return r;
}

static jobject new_object_common(VMThread *t, jclass cls, Method *m, JValue *params) {
    Class *c = vm_class_from_mirror(D(cls));
    if (!vm_init_class(t, c)) return NULL;
    Object *o = vm_alloc_object(t, c);
    if (!o) return NULL;
    do_call(t, CALL_NONVIRTUAL, o, m, params);
    return t->exception ? NULL : L(o);
}

static jobject JNICALL_NewObjectV(void *env, jclass cls, jmethodID mid, va_list ap) {
    ENTER();
    JValue p[256];
    collect_va(mid, ap, p);
    jobject r = new_object_common(t, cls, mid, p);
    LEAVE();
    return r;
}

static jobject JNICALL_NewObject(void *env, jclass cls, jmethodID mid, ...) {
    va_list ap;
    va_start(ap, mid);
    jobject r = JNICALL_NewObjectV(env, cls, mid, ap);
    va_end(ap);
    return r;
}

static jobject JNICALL_NewObjectA(void *env, jclass cls, jmethodID mid, const jvalue *args) {
    ENTER();
    JValue p[256];
    collect_a(mid, args, p);
    jobject r = new_object_common(t, cls, mid, p);
    LEAVE();
    return r;
}

static jclass JNICALL_GetObjectClass(void *env, jobject obj) {
    ENTER();
    Object *o = D(obj);
    jclass r = o ? L(vm_class_mirror(t, o->clazz)) : NULL;
    LEAVE();
    return r;
}

static jboolean JNICALL_IsInstanceOf(void *env, jobject obj, jclass cls) {
    ENTER();
    Object *o = D(obj);
    jboolean r = !o || vm_instance_of(o, vm_class_from_mirror(D(cls)));
    LEAVE();
    return r;
}

static Method *lookup_method(VMThread *t, jclass cls, const char *name, const char *sig, bool is_static) {
    Class *c = vm_class_from_mirror(D(cls));
    if (!c) {
        vm_throw_npe(t, "class");
        return NULL;
    }
    if (!vm_init_class(t, c)) return NULL;
    Method *m = vm_find_method_hier(c, name, sig);
    if (!m || (((m->access & ACC_STATIC) != 0) != is_static)) {
        vm_throw_new(t, "Ljava/lang/NoSuchMethodError;", "no %s method \"%s.%s%s\"", is_static ? "static" : "non-static",
                     c->name, name, sig);
        return NULL;
    }
    return m;
}

static jmethodID JNICALL_GetMethodID(void *env, jclass cls, const char *name, const char *sig) {
    ENTER();
    Method *m = lookup_method(t, cls, name, sig, false);
    LEAVE();
    return m;
}

static jmethodID JNICALL_GetStaticMethodID(void *env, jclass cls, const char *name, const char *sig) {
    ENTER();
    Method *m = lookup_method(t, cls, name, sig, true);
    LEAVE();
    return m;
}

static Field *lookup_field(VMThread *t, jclass cls, const char *name, const char *sig, bool is_static) {
    Class *c = vm_class_from_mirror(D(cls));
    if (!c) {
        vm_throw_npe(t, "class");
        return NULL;
    }
    if (!vm_init_class(t, c)) return NULL;
    Field *f = vm_find_field(c, name, sig);
    if (!f || (((f->access & ACC_STATIC) != 0) != is_static)) {
        vm_throw_new(t, "Ljava/lang/NoSuchFieldError;", "no \"%s\" field \"%s\" in class \"%s\"", sig, name, c->name);
        return NULL;
    }
    return f;
}

static jfieldID JNICALL_GetFieldID(void *env, jclass cls, const char *name, const char *sig) {
    ENTER();
    Field *f = lookup_field(t, cls, name, sig, false);
    LEAVE();
    return f;
}

static jfieldID JNICALL_GetStaticFieldID(void *env, jclass cls, const char *name, const char *sig) {
    ENTER();
    Field *f = lookup_field(t, cls, name, sig, true);
    LEAVE();
    return f;
}

/* ---- Call<Type>Method families ---------------------------------------------------------------- */

#define RET_Object(r) L((r).l)
#define RET_Boolean(r) ((jboolean)((r).i != 0))
#define RET_Byte(r) ((jbyte)(r).i)
#define RET_Char(r) ((jchar)(r).i)
#define RET_Short(r) ((jshort)(r).i)
#define RET_Int(r) ((jint)(r).i)
#define RET_Long(r) ((jlong)(r).j)
#define RET_Float(r) ((jfloat)(r).f)
#define RET_Double(r) ((jdouble)(r).d)

#define DEFINE_CALLS(Name, jtype)                                                                               \
    static jtype JNICALL_Call##Name##MethodV(void *env, jobject obj, jmethodID mid, va_list ap) {               \
        ENTER();                                                                                                \
        JValue p[256];                                                                                          \
        collect_va(mid, ap, p);                                                                                 \
        JValue r = do_call(t, CALL_VIRTUAL, D(obj), mid, p);                                                    \
        jtype v = RET_##Name(r);                                                                                \
        LEAVE();                                                                                                \
        return v;                                                                                               \
    }                                                                                                           \
    static jtype JNICALL_Call##Name##Method(void *env, jobject obj, jmethodID mid, ...) {                       \
        va_list ap;                                                                                             \
        va_start(ap, mid);                                                                                      \
        jtype v = JNICALL_Call##Name##MethodV(env, obj, mid, ap);                                               \
        va_end(ap);                                                                                             \
        return v;                                                                                               \
    }                                                                                                           \
    static jtype JNICALL_Call##Name##MethodA(void *env, jobject obj, jmethodID mid, const jvalue *a) {          \
        ENTER();                                                                                                \
        JValue p[256];                                                                                          \
        collect_a(mid, a, p);                                                                                   \
        JValue r = do_call(t, CALL_VIRTUAL, D(obj), mid, p);                                                    \
        jtype v = RET_##Name(r);                                                                                \
        LEAVE();                                                                                                \
        return v;                                                                                               \
    }                                                                                                           \
    static jtype JNICALL_CallNonvirtual##Name##MethodV(void *env, jobject obj, jclass cls, jmethodID mid,       \
                                                       va_list ap) {                                            \
        SA_UNUSED(cls);                                                                                         \
        ENTER();                                                                                                \
        JValue p[256];                                                                                          \
        collect_va(mid, ap, p);                                                                                 \
        JValue r = do_call(t, CALL_NONVIRTUAL, D(obj), mid, p);                                                 \
        jtype v = RET_##Name(r);                                                                                \
        LEAVE();                                                                                                \
        return v;                                                                                               \
    }                                                                                                           \
    static jtype JNICALL_CallNonvirtual##Name##Method(void *env, jobject obj, jclass cls, jmethodID mid, ...) { \
        va_list ap;                                                                                             \
        va_start(ap, mid);                                                                                      \
        jtype v = JNICALL_CallNonvirtual##Name##MethodV(env, obj, cls, mid, ap);                                \
        va_end(ap);                                                                                             \
        return v;                                                                                               \
    }                                                                                                           \
    static jtype JNICALL_CallNonvirtual##Name##MethodA(void *env, jobject obj, jclass cls, jmethodID mid,       \
                                                       const jvalue *a) {                                       \
        SA_UNUSED(cls);                                                                                         \
        ENTER();                                                                                                \
        JValue p[256];                                                                                          \
        collect_a(mid, a, p);                                                                                   \
        JValue r = do_call(t, CALL_NONVIRTUAL, D(obj), mid, p);                                                 \
        jtype v = RET_##Name(r);                                                                                \
        LEAVE();                                                                                                \
        return v;                                                                                               \
    }                                                                                                           \
    static jtype JNICALL_CallStatic##Name##MethodV(void *env, jclass cls, jmethodID mid, va_list ap) {          \
        SA_UNUSED(cls);                                                                                         \
        ENTER();                                                                                                \
        JValue p[256];                                                                                          \
        collect_va(mid, ap, p);                                                                                 \
        JValue r = do_call(t, CALL_STATIC, NULL, mid, p);                                                       \
        jtype v = RET_##Name(r);                                                                                \
        LEAVE();                                                                                                \
        return v;                                                                                               \
    }                                                                                                           \
    static jtype JNICALL_CallStatic##Name##Method(void *env, jclass cls, jmethodID mid, ...) {                  \
        va_list ap;                                                                                             \
        va_start(ap, mid);                                                                                      \
        jtype v = JNICALL_CallStatic##Name##MethodV(env, cls, mid, ap);                                         \
        va_end(ap);                                                                                             \
        return v;                                                                                               \
    }                                                                                                           \
    static jtype JNICALL_CallStatic##Name##MethodA(void *env, jclass cls, jmethodID mid, const jvalue *a) {     \
        SA_UNUSED(cls);                                                                                         \
        ENTER();                                                                                                \
        JValue p[256];                                                                                          \
        collect_a(mid, a, p);                                                                                   \
        JValue r = do_call(t, CALL_STATIC, NULL, mid, p);                                                       \
        jtype v = RET_##Name(r);                                                                                \
        LEAVE();                                                                                                \
        return v;                                                                                               \
    }

DEFINE_CALLS(Object, jobject)
DEFINE_CALLS(Boolean, jboolean)
DEFINE_CALLS(Byte, jbyte)
DEFINE_CALLS(Char, jchar)
DEFINE_CALLS(Short, jshort)
DEFINE_CALLS(Int, jint)
DEFINE_CALLS(Long, jlong)
DEFINE_CALLS(Float, jfloat)
DEFINE_CALLS(Double, jdouble)

static void JNICALL_CallVoidMethodV(void *env, jobject obj, jmethodID mid, va_list ap) {
    ENTER();
    JValue p[256];
    collect_va(mid, ap, p);
    do_call(t, CALL_VIRTUAL, D(obj), mid, p);
    LEAVE();
}
static void JNICALL_CallVoidMethod(void *env, jobject obj, jmethodID mid, ...) {
    va_list ap;
    va_start(ap, mid);
    JNICALL_CallVoidMethodV(env, obj, mid, ap);
    va_end(ap);
}
static void JNICALL_CallVoidMethodA(void *env, jobject obj, jmethodID mid, const jvalue *a) {
    ENTER();
    JValue p[256];
    collect_a(mid, a, p);
    do_call(t, CALL_VIRTUAL, D(obj), mid, p);
    LEAVE();
}
static void JNICALL_CallNonvirtualVoidMethodV(void *env, jobject obj, jclass cls, jmethodID mid, va_list ap) {
    SA_UNUSED(cls);
    ENTER();
    JValue p[256];
    collect_va(mid, ap, p);
    do_call(t, CALL_NONVIRTUAL, D(obj), mid, p);
    LEAVE();
}
static void JNICALL_CallNonvirtualVoidMethod(void *env, jobject obj, jclass cls, jmethodID mid, ...) {
    va_list ap;
    va_start(ap, mid);
    JNICALL_CallNonvirtualVoidMethodV(env, obj, cls, mid, ap);
    va_end(ap);
}
static void JNICALL_CallNonvirtualVoidMethodA(void *env, jobject obj, jclass cls, jmethodID mid, const jvalue *a) {
    SA_UNUSED(cls);
    ENTER();
    JValue p[256];
    collect_a(mid, a, p);
    do_call(t, CALL_NONVIRTUAL, D(obj), mid, p);
    LEAVE();
}
static void JNICALL_CallStaticVoidMethodV(void *env, jclass cls, jmethodID mid, va_list ap) {
    SA_UNUSED(cls);
    ENTER();
    JValue p[256];
    collect_va(mid, ap, p);
    do_call(t, CALL_STATIC, NULL, mid, p);
    LEAVE();
}
static void JNICALL_CallStaticVoidMethod(void *env, jclass cls, jmethodID mid, ...) {
    va_list ap;
    va_start(ap, mid);
    JNICALL_CallStaticVoidMethodV(env, cls, mid, ap);
    va_end(ap);
}
static void JNICALL_CallStaticVoidMethodA(void *env, jclass cls, jmethodID mid, const jvalue *a) {
    SA_UNUSED(cls);
    ENTER();
    JValue p[256];
    collect_a(mid, a, p);
    do_call(t, CALL_STATIC, NULL, mid, p);
    LEAVE();
}

/* ---- fields ------------------------------------------------------------------------------------ */

#define DEFINE_FIELDS(Name, jtype, member)                                                          \
    static jtype JNICALL_Get##Name##Field(void *env, jobject obj, jfieldID fid) {                   \
        ENTER();                                                                                    \
        JValue v = vm_field_get(D(obj), (Field *)fid);                                              \
        jtype r = RET_##Name(v);                                                                    \
        LEAVE();                                                                                    \
        return r;                                                                                   \
    }                                                                                               \
    static void JNICALL_Set##Name##Field(void *env, jobject obj, jfieldID fid, jtype val) {         \
        ENTER();                                                                                    \
        JValue v;                                                                                   \
        v.raw = 0;                                                                                  \
        member;                                                                                     \
        vm_field_set(D(obj), (Field *)fid, v);                                                      \
        LEAVE();                                                                                    \
    }                                                                                               \
    static jtype JNICALL_GetStatic##Name##Field(void *env, jclass cls, jfieldID fid) {              \
        SA_UNUSED(cls);                                                                             \
        ENTER();                                                                                    \
        Field *f = (Field *)fid;                                                                    \
        jtype r = 0;                                                                                \
        if (vm_init_class(t, f->clazz)) {                                                           \
            JValue v = vm_field_get(NULL, f);                                                       \
            r = RET_##Name(v);                                                                      \
        }                                                                                           \
        LEAVE();                                                                                    \
        return r;                                                                                   \
    }                                                                                               \
    static void JNICALL_SetStatic##Name##Field(void *env, jclass cls, jfieldID fid, jtype val) {    \
        SA_UNUSED(cls);                                                                             \
        ENTER();                                                                                    \
        Field *f = (Field *)fid;                                                                    \
        if (vm_init_class(t, f->clazz)) {                                                           \
            JValue v;                                                                               \
            v.raw = 0;                                                                              \
            member;                                                                                 \
            vm_field_set(NULL, f, v);                                                               \
        }                                                                                           \
        LEAVE();                                                                                    \
    }

DEFINE_FIELDS(Object, jobject, v.l = D(val))
DEFINE_FIELDS(Boolean, jboolean, v.i = val)
DEFINE_FIELDS(Byte, jbyte, v.i = val)
DEFINE_FIELDS(Char, jchar, v.i = val)
DEFINE_FIELDS(Short, jshort, v.i = val)
DEFINE_FIELDS(Int, jint, v.i = val)
DEFINE_FIELDS(Long, jlong, v.j = val)
DEFINE_FIELDS(Float, jfloat, v.f = val)
DEFINE_FIELDS(Double, jdouble, v.d = val)

/* ---- strings -------------------------------------------------------------------------------------- */

static jstring JNICALL_NewString(void *env, const jchar *chars, jsize len) {
    ENTER();
    jstring r = L(vm_new_string_utf16(t, chars, len));
    LEAVE();
    return r;
}

static jsize JNICALL_GetStringLength(void *env, jstring s) {
    ENTER();
    jsize r = vm_string_length(D(s));
    LEAVE();
    return r;
}

static const jchar *JNICALL_GetStringChars(void *env, jstring s, jboolean *isCopy) {
    ENTER();
    if (isCopy) *isCopy = JNI_FALSE;
    const jchar *r = vm_string_chars(D(s));
    LEAVE();
    return r;
}

static void JNICALL_ReleaseStringChars(void *env, jstring s, const jchar *chars) {
    SA_UNUSED(env);
    SA_UNUSED(s);
    SA_UNUSED(chars);
}

static jstring JNICALL_NewStringUTF(void *env, const char *utf) {
    if (!utf) return NULL;
    ENTER();
    jstring r = L(vm_new_string_mutf8(t, utf));
    LEAVE();
    return r;
}

static jsize JNICALL_GetStringUTFLength(void *env, jstring s) {
    ENTER();
    Object *o = D(s);
    char *u = sa_utf16_to_mutf8(vm_string_chars(o), (size_t)vm_string_length(o));
    jsize r = (jsize)strlen(u);
    free(u);
    LEAVE();
    return r;
}

static const char *JNICALL_GetStringUTFChars(void *env, jstring s, jboolean *isCopy) {
    ENTER();
    if (isCopy) *isCopy = JNI_TRUE;
    Object *o = D(s);
    char *r = o ? sa_utf16_to_mutf8(vm_string_chars(o), (size_t)vm_string_length(o)) : NULL;
    LEAVE();
    return r;
}

static void JNICALL_ReleaseStringUTFChars(void *env, jstring s, const char *chars) {
    SA_UNUSED(env);
    SA_UNUSED(s);
    free((void *)chars);
}

static void JNICALL_GetStringRegion(void *env, jstring s, jsize start, jsize len, jchar *buf) {
    ENTER();
    Object *o = D(s);
    int32_t n = vm_string_length(o);
    if (start < 0 || len < 0 || start + len > n)
        vm_throw_new(t, "Ljava/lang/StringIndexOutOfBoundsException;", "length=%d; regionStart=%d; regionLength=%d", n,
                     start, len);
    else
        memcpy(buf, vm_string_chars(o) + start, (size_t)len * 2);
    LEAVE();
}

static void JNICALL_GetStringUTFRegion(void *env, jstring s, jsize start, jsize len, char *buf) {
    ENTER();
    Object *o = D(s);
    int32_t n = vm_string_length(o);
    if (start < 0 || len < 0 || start + len > n) {
        vm_throw_new(t, "Ljava/lang/StringIndexOutOfBoundsException;", "length=%d; regionStart=%d; regionLength=%d", n,
                     start, len);
    } else {
        char *u = sa_utf16_to_mutf8(vm_string_chars(o) + start, (size_t)len);
        strcpy(buf, u);
        free(u);
    }
    LEAVE();
}

static const jchar *JNICALL_GetStringCritical(void *env, jstring s, jboolean *isCopy) {
    return JNICALL_GetStringChars(env, s, isCopy);
}

static void JNICALL_ReleaseStringCritical(void *env, jstring s, const jchar *c) {
    SA_UNUSED(env);
    SA_UNUSED(s);
    SA_UNUSED(c);
}

/* ---- arrays ---------------------------------------------------------------------------------------- */

static jsize JNICALL_GetArrayLength(void *env, jarray arr) {
    ENTER();
    ArrayObject *a = (ArrayObject *)D(arr);
    jsize r = a ? a->length : 0;
    if (!a) vm_throw_npe(t, "array");
    LEAVE();
    return r;
}

static jobjectArray JNICALL_NewObjectArray(void *env, jsize len, jclass elemClass, jobject init) {
    ENTER();
    jobjectArray r = NULL;
    Class *ec = vm_class_from_mirror(D(elemClass));
    Class *ac = ec ? vm_array_class_of(t, ec) : NULL;
    if (ac) {
        ArrayObject *a = vm_alloc_array(t, ac, len);
        if (a) {
            Object *iv = D(init);
            for (jsize i = 0; i < len; i++) ARRAY_DATA(a, Object *)[i] = iv;
            r = L((Object *)a);
        }
    }
    LEAVE();
    return r;
}

static jobject JNICALL_GetObjectArrayElement(void *env, jobjectArray arr, jsize i) {
    ENTER();
    jobject r = NULL;
    ArrayObject *a = (ArrayObject *)D(arr);
    if (i < 0 || i >= a->length)
        vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;", "length=%d; index=%d", a->length, i);
    else
        r = L(ARRAY_DATA(a, Object *)[i]);
    LEAVE();
    return r;
}

static void JNICALL_SetObjectArrayElement(void *env, jobjectArray arr, jsize i, jobject val) {
    ENTER();
    ArrayObject *a = (ArrayObject *)D(arr);
    Object *v = D(val);
    if (i < 0 || i >= a->length)
        vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;", "length=%d; index=%d", a->length, i);
    else if (v && !vm_is_assignable(a->obj.clazz->component, v->clazz))
        vm_throw_new(t, "Ljava/lang/ArrayStoreException;", "%s", v->clazz->name);
    else
        ARRAY_DATA(a, Object *)[i] = v;
    LEAVE();
}

#define DEFINE_ARRAYS(Name, jtype, prim)                                                                         \
    static jarray JNICALL_New##Name##Array(void *env, jsize len) {                                              \
        ENTER();                                                                                                 \
        jarray r = L((Object *)vm_alloc_prim_array(t, prim, len));                                               \
        LEAVE();                                                                                                 \
        return r;                                                                                                \
    }                                                                                                            \
    static jtype *JNICALL_Get##Name##ArrayElements(void *env, jarray arr, jboolean *isCopy) {                   \
        ENTER();                                                                                                 \
        if (isCopy) *isCopy = JNI_FALSE;                                                                         \
        ArrayObject *a = (ArrayObject *)D(arr);                                                                  \
        jtype *r = a ? ARRAY_DATA(a, jtype) : NULL;                                                              \
        LEAVE();                                                                                                 \
        return r;                                                                                                \
    }                                                                                                            \
    static void JNICALL_Release##Name##ArrayElements(void *env, jarray arr, jtype *elems, jint mode) {          \
        SA_UNUSED(env);                                                                                          \
        SA_UNUSED(arr);                                                                                          \
        SA_UNUSED(elems);                                                                                        \
        SA_UNUSED(mode);                                                                                         \
    }                                                                                                            \
    static void JNICALL_Get##Name##ArrayRegion(void *env, jarray arr, jsize start, jsize len, jtype *buf) {     \
        ENTER();                                                                                                 \
        ArrayObject *a = (ArrayObject *)D(arr);                                                                  \
        if (start < 0 || len < 0 || (int64_t)start + len > a->length)                                            \
            vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;", "length=%d; regionStart=%d; regionLength=%d", \
                         a->length, start, len);                                                                 \
        else                                                                                                     \
            memcpy(buf, ARRAY_DATA(a, jtype) + start, (size_t)len * sizeof(jtype));                              \
        LEAVE();                                                                                                 \
    }                                                                                                            \
    static void JNICALL_Set##Name##ArrayRegion(void *env, jarray arr, jsize start, jsize len, const jtype *buf) { \
        ENTER();                                                                                                 \
        ArrayObject *a = (ArrayObject *)D(arr);                                                                  \
        if (start < 0 || len < 0 || (int64_t)start + len > a->length)                                            \
            vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;", "length=%d; regionStart=%d; regionLength=%d", \
                         a->length, start, len);                                                                 \
        else                                                                                                     \
            memcpy(ARRAY_DATA(a, jtype) + start, buf, (size_t)len * sizeof(jtype));                              \
        LEAVE();                                                                                                 \
    }

DEFINE_ARRAYS(Boolean, jboolean, 'Z')
DEFINE_ARRAYS(Byte, jbyte, 'B')
DEFINE_ARRAYS(Char, jchar, 'C')
DEFINE_ARRAYS(Short, jshort, 'S')
DEFINE_ARRAYS(Int, jint, 'I')
DEFINE_ARRAYS(Long, jlong, 'J')
DEFINE_ARRAYS(Float, jfloat, 'F')
DEFINE_ARRAYS(Double, jdouble, 'D')

static void *JNICALL_GetPrimitiveArrayCritical(void *env, jarray arr, jboolean *isCopy) {
    ENTER();
    if (isCopy) *isCopy = JNI_FALSE;
    ArrayObject *a = (ArrayObject *)D(arr);
    void *r = a ? a->data : NULL;
    LEAVE();
    return r;
}

static void JNICALL_ReleasePrimitiveArrayCritical(void *env, jarray arr, void *p, jint mode) {
    SA_UNUSED(env);
    SA_UNUSED(arr);
    SA_UNUSED(p);
    SA_UNUSED(mode);
}

/* ---- natives registration -------------------------------------------------------------------------- */

static jint JNICALL_RegisterNatives(void *env, jclass cls, const JNINativeMethod *methods, jint n) {
    ENTER();
    jint rc = 0;
    Class *c = vm_class_from_mirror(D(cls));
    for (jint i = 0; i < n; i++) {
        const char *name = methods[i].name, *sig = methods[i].signature;
        Method *m = vm_find_method(c, name, sig);
        if (!m) {
            LOGE("RegisterNatives: %s.%s%s not found", c->name, name, sig);
            vm_throw_new(t, "Ljava/lang/NoSuchMethodError;", "no method \"%s.%s%s\"", c->name, name, sig);
            rc = JNI_ERR;
            break;
        }
        m->jni_fn = methods[i].fnPtr;
        m->native = NULL;
        LOGV("registered native %s.%s%s -> %p", c->name, name, sig, methods[i].fnPtr);
    }
    LEAVE();
    return rc;
}

static jint JNICALL_UnregisterNatives(void *env, jclass cls) {
    ENTER();
    Class *c = vm_class_from_mirror(D(cls));
    for (uint32_t i = 0; i < c->nmethods; i++)
        if (c->methods[i].access & ACC_NATIVE) c->methods[i].jni_fn = NULL;
    LEAVE();
    return 0;
}

static jint JNICALL_MonitorEnter(void *env, jobject obj) {
    ENTER();
    vm_monitor_enter(t, D(obj));
    jint r = t->exception ? JNI_ERR : 0;
    LEAVE();
    return r;
}

static jint JNICALL_MonitorExit(void *env, jobject obj) {
    ENTER();
    jint r = vm_monitor_exit(t, D(obj)) ? 0 : JNI_ERR;
    LEAVE();
    return r;
}

static jint JNICALL_GetJavaVM(void *env, void **vm) {
    SA_UNUSED(env);
    *vm = &g_java_vm;
    return 0;
}

static jweak JNICALL_NewWeakGlobalRef(void *env, jobject obj) {
    ENTER();
    Object *o = D(obj);
    jweak r = NULL;
    if (o) {
        Object **slot = sa_malloc(sizeof(Object *));
        *slot = o;
        sa_ptrmap_put(&g_vm.weak_global_refs, slot, slot);
        r = (jweak)((uintptr_t)slot | 1);
    }
    LEAVE();
    return r;
}

static void JNICALL_DeleteWeakGlobalRef(void *env, jweak ref) {
    ENTER();
    if (ref && ((uintptr_t)ref & 1)) {
        Object **slot = (Object **)((uintptr_t)ref & ~(uintptr_t)1);
        sa_ptrmap_remove(&g_vm.weak_global_refs, slot);
        free(slot);
    }
    LEAVE();
}

static jboolean JNICALL_ExceptionCheck(void *env) {
    VMThread *t = ENV_THREAD(env);
    return t->exception != NULL;
}

static jobject JNICALL_NewDirectByteBuffer(void *env, void *address, jlong capacity) {
    ENTER();
    jobject r = NULL;
    /* wrap the native memory in a byte[] whose storage is external */
    ArrayObject *a = vm_alloc_prim_array(t, 'B', 0);
    if (a) {
        a->data = address;
        a->length = (int32_t)capacity;
        JValue bb = vm_call_static(t, "Ljava/nio/ByteBuffer;", "wrap", "([B)Ljava/nio/ByteBuffer;", (Object *)a);
        if (bb.l && !t->exception) {
            vm_set_int_by_name(bb.l, "direct", 1);
            r = L(bb.l);
        }
    }
    LEAVE();
    return r;
}

void *vm_buffer_address(Object *buf) {
    if (!buf) return NULL;
    Object *backing = vm_get_ref_by_name(buf, "backing");
    if (!backing) return NULL;
    int32_t off = vm_get_int_by_name(buf, "byteOffset");
    return (uint8_t *)((ArrayObject *)backing)->data + off;
}

static void *JNICALL_GetDirectBufferAddress(void *env, jobject buf) {
    ENTER();
    void *r = vm_buffer_address(D(buf));
    LEAVE();
    return r;
}

static jlong JNICALL_GetDirectBufferCapacity(void *env, jobject buf) {
    ENTER();
    Object *b = D(buf);
    jlong r = b ? vm_get_int_by_name(b, "capacity") : -1;
    LEAVE();
    return r;
}

static jobjectRefType JNICALL_GetObjectRefType(void *env, jobject ref) {
    ENTER();
    jobjectRefType r = JNIInvalidRefType;
    if ((uintptr_t)ref & 1) {
        r = JNIWeakGlobalRefType;
    } else if (ref) {
        Object *o = (Object *)ref;
        for (uint32_t i = 0; i < t->nlocal_refs && r == JNIInvalidRefType; i++)
            if (t->local_refs[i] == o) r = JNILocalRefType;
        for (uint32_t i = 0; i < g_vm.nglobal_refs && r == JNIInvalidRefType; i++)
            if (g_vm.global_refs[i] == o) r = JNIGlobalRefType;
        if (r == JNIInvalidRefType && vm_is_object(o)) r = JNILocalRefType;
    }
    LEAVE();
    return r;
}

/* ---- JavaVM invoke interface ------------------------------------------------------------------------- */

static jint JNICALL_DestroyJavaVM(void *vm) {
    SA_UNUSED(vm);
    return JNI_ERR;
}

static jint attach_thread(void **penv, void *args, bool daemon) {
    VMThread *cur = vm_current_thread();
    if (cur) {
        *penv = vm_jni_env(cur);
        return JNI_OK;
    }
    const char *name = "NativeThread";
    if (args && ((JavaVMAttachArgs *)args)->name) name = ((JavaVMAttachArgs *)args)->name;
    VMThread *t = vm_thread_create(name);
    t->attached_native = true;
    t->daemon = daemon;
    volatile int marker = 0;
    /* Native threads are scanned from their attach point; frames above are native only. */
    vm_thread_register(t, (void *)((uintptr_t)&marker + 4096));
    vm_gil_acquire(t);
    Object *jname = vm_new_string_utf8(t, name);
    Object *th = vm_new_instance(t, "Ljava/lang/Thread;", "(JLjava/lang/String;)V", (int64_t)(intptr_t)t, jname);
    t->jthread = th;
    t->exception = NULL;
    vm_gil_release(t);
    *penv = vm_jni_env(t);
    LOGD("attached native thread '%s' as thread %d", name, t->id);
    return JNI_OK;
}

static jint JNICALL_AttachCurrentThread(void *vm, void **penv, void *args) {
    SA_UNUSED(vm);
    return attach_thread(penv, args, false);
}

static jint JNICALL_AttachCurrentThreadAsDaemon(void *vm, void **penv, void *args) {
    SA_UNUSED(vm);
    return attach_thread(penv, args, true);
}

static jint JNICALL_DetachCurrentThread(void *vm) {
    SA_UNUSED(vm);
    VMThread *t = vm_current_thread();
    if (!t) return JNI_EDETACHED;
    if (!t->attached_native) return JNI_OK; /* never detach VM-created threads */
    if (t->has_gil) vm_gil_release(t);
    vm_thread_unregister(t);
    return JNI_OK;
}

static jint JNICALL_GetEnv(void *vm, void **penv, jint version) {
    SA_UNUSED(vm);
    SA_UNUSED(version);
    VMThread *t = vm_current_thread();
    if (!t) {
        *penv = NULL;
        return JNI_EDETACHED;
    }
    *penv = vm_jni_env(t);
    return JNI_OK;
}

/* ---- table construction --------------------------------------------------------------------------------- */

static void jni_tables_init(void) {
    const void **T = (const void **)g_jni_table;
    int i = 4;
#define F(fn) T[i++] = (const void *)JNICALL_##fn
    F(GetVersion);
    F(DefineClass);
    F(FindClass);
    F(FromReflectedMethod);
    F(FromReflectedField);
    F(ToReflectedMethod);
    F(GetSuperclass);
    F(IsAssignableFrom);
    F(ToReflectedField);
    F(Throw);
    F(ThrowNew);
    F(ExceptionOccurred);
    F(ExceptionDescribe);
    F(ExceptionClear);
    F(FatalError);
    F(PushLocalFrame);
    F(PopLocalFrame);
    F(NewGlobalRef);
    F(DeleteGlobalRef);
    F(DeleteLocalRef);
    F(IsSameObject);
    F(NewLocalRef);
    F(EnsureLocalCapacity);
    F(AllocObject);
    F(NewObject);
    F(NewObjectV);
    F(NewObjectA);
    F(GetObjectClass);
    F(IsInstanceOf);
    F(GetMethodID);
#define CALLS(N) F(Call##N##Method); F(Call##N##MethodV); F(Call##N##MethodA);
    CALLS(Object) CALLS(Boolean) CALLS(Byte) CALLS(Char) CALLS(Short) CALLS(Int) CALLS(Long) CALLS(Float) CALLS(Double)
    CALLS(Void)
#define NVCALLS(N) F(CallNonvirtual##N##Method); F(CallNonvirtual##N##MethodV); F(CallNonvirtual##N##MethodA);
    NVCALLS(Object) NVCALLS(Boolean) NVCALLS(Byte) NVCALLS(Char) NVCALLS(Short) NVCALLS(Int) NVCALLS(Long)
    NVCALLS(Float) NVCALLS(Double) NVCALLS(Void)
    F(GetFieldID);
    F(GetObjectField);
    F(GetBooleanField);
    F(GetByteField);
    F(GetCharField);
    F(GetShortField);
    F(GetIntField);
    F(GetLongField);
    F(GetFloatField);
    F(GetDoubleField);
    F(SetObjectField);
    F(SetBooleanField);
    F(SetByteField);
    F(SetCharField);
    F(SetShortField);
    F(SetIntField);
    F(SetLongField);
    F(SetFloatField);
    F(SetDoubleField);
    F(GetStaticMethodID);
#define SCALLS(N) F(CallStatic##N##Method); F(CallStatic##N##MethodV); F(CallStatic##N##MethodA);
    SCALLS(Object) SCALLS(Boolean) SCALLS(Byte) SCALLS(Char) SCALLS(Short) SCALLS(Int) SCALLS(Long) SCALLS(Float)
    SCALLS(Double) SCALLS(Void)
    F(GetStaticFieldID);
    F(GetStaticObjectField);
    F(GetStaticBooleanField);
    F(GetStaticByteField);
    F(GetStaticCharField);
    F(GetStaticShortField);
    F(GetStaticIntField);
    F(GetStaticLongField);
    F(GetStaticFloatField);
    F(GetStaticDoubleField);
    F(SetStaticObjectField);
    F(SetStaticBooleanField);
    F(SetStaticByteField);
    F(SetStaticCharField);
    F(SetStaticShortField);
    F(SetStaticIntField);
    F(SetStaticLongField);
    F(SetStaticFloatField);
    F(SetStaticDoubleField);
    F(NewString);
    F(GetStringLength);
    F(GetStringChars);
    F(ReleaseStringChars);
    F(NewStringUTF);
    F(GetStringUTFLength);
    F(GetStringUTFChars);
    F(ReleaseStringUTFChars);
    F(GetArrayLength);
    F(NewObjectArray);
    F(GetObjectArrayElement);
    F(SetObjectArrayElement);
    F(NewBooleanArray);
    F(NewByteArray);
    F(NewCharArray);
    F(NewShortArray);
    F(NewIntArray);
    F(NewLongArray);
    F(NewFloatArray);
    F(NewDoubleArray);
    F(GetBooleanArrayElements);
    F(GetByteArrayElements);
    F(GetCharArrayElements);
    F(GetShortArrayElements);
    F(GetIntArrayElements);
    F(GetLongArrayElements);
    F(GetFloatArrayElements);
    F(GetDoubleArrayElements);
    F(ReleaseBooleanArrayElements);
    F(ReleaseByteArrayElements);
    F(ReleaseCharArrayElements);
    F(ReleaseShortArrayElements);
    F(ReleaseIntArrayElements);
    F(ReleaseLongArrayElements);
    F(ReleaseFloatArrayElements);
    F(ReleaseDoubleArrayElements);
    F(GetBooleanArrayRegion);
    F(GetByteArrayRegion);
    F(GetCharArrayRegion);
    F(GetShortArrayRegion);
    F(GetIntArrayRegion);
    F(GetLongArrayRegion);
    F(GetFloatArrayRegion);
    F(GetDoubleArrayRegion);
    F(SetBooleanArrayRegion);
    F(SetByteArrayRegion);
    F(SetCharArrayRegion);
    F(SetShortArrayRegion);
    F(SetIntArrayRegion);
    F(SetLongArrayRegion);
    F(SetFloatArrayRegion);
    F(SetDoubleArrayRegion);
    F(RegisterNatives);
    F(UnregisterNatives);
    F(MonitorEnter);
    F(MonitorExit);
    F(GetJavaVM);
    F(GetStringRegion);
    F(GetStringUTFRegion);
    F(GetPrimitiveArrayCritical);
    F(ReleasePrimitiveArrayCritical);
    F(GetStringCritical);
    F(ReleaseStringCritical);
    F(NewWeakGlobalRef);
    F(DeleteWeakGlobalRef);
    F(ExceptionCheck);
    F(NewDirectByteBuffer);
    F(GetDirectBufferAddress);
    F(GetDirectBufferCapacity);
    F(GetObjectRefType);
#undef F
    if (i != 233) sa_fatal("JNI table has %d entries, expected 233", i);
    const void **V = (const void **)g_invoke_table;
    V[3] = (const void *)JNICALL_DestroyJavaVM;
    V[4] = (const void *)JNICALL_AttachCurrentThread;
    V[5] = (const void *)JNICALL_DetachCurrentThread;
    V[6] = (const void *)JNICALL_GetEnv;
    V[7] = (const void *)JNICALL_AttachCurrentThreadAsDaemon;
    g_tables_ready = true;
}

/* ---- binding & invocation ------------------------------------------------------------------------------- */

static void mangle(SaBuf *b, const char *s, bool stop_at_paren) {
    /* s is MUTF-8; decode to UTF-16 units for escaping */
    uint16_t tmp[1024];
    size_t n = sa_mutf8_to_utf16(s, NULL);
    uint16_t *u = n < SA_ARRAY_LEN(tmp) ? tmp : sa_malloc(n * 2);
    sa_mutf8_to_utf16(s, u);
    for (size_t i = 0; i < n; i++) {
        uint16_t c = u[i];
        if (stop_at_paren && c == ')') break;
        if (c == '/' || c == '.') sa_buf_putc(b, '_');
        else if (c == '_') sa_buf_puts(b, "_1");
        else if (c == ';') sa_buf_puts(b, "_2");
        else if (c == '[') sa_buf_puts(b, "_3");
        else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) sa_buf_putc(b, (char)c);
        else sa_buf_printf(b, "_0%04x", c);
    }
    if (u != tmp) free(u);
}

bool vm_jni_bind(VMThread *t, Method *m) {
    SA_UNUSED(t);
    SaBuf b = {0};
    sa_buf_puts(&b, "Java_");
    /* class binary name without L...; */
    const char *d = m->clazz->descriptor;
    char *cls = sa_strndup(d + 1, strlen(d) - 2);
    mangle(&b, cls, false);
    free(cls);
    sa_buf_putc(&b, '_');
    mangle(&b, m->name, false);
    void *fn = nativeloader_find_symbol(sa_buf_cstr(&b));
    if (!fn) {
        sa_buf_puts(&b, "__");
        mangle(&b, m->desc + 1, true);
        fn = nativeloader_find_symbol(sa_buf_cstr(&b));
    }
    if (fn) {
        m->jni_fn = fn;
        LOGD("bound %s -> %p", sa_buf_cstr(&b), fn);
    }
    sa_buf_free(&b);
    return fn != NULL;
}

JValue vm_jni_call(VMThread *t, Method *m, uint64_t *args) {
    JValue result;
    result.raw = 0;
    void *env = vm_jni_env(t);
    vm_jni_push_frame(t);
    NativeArgs na;
    nc_init(&na);
    nc_ptr(&na, env);
    int s = 0;
    if (m->access & ACC_STATIC) {
        nc_ptr(&na, vm_jni_new_local(t, vm_class_mirror(t, m->clazz)));
    } else {
        nc_ptr(&na, vm_jni_new_local(t, (Object *)(uintptr_t)args[s++]));
    }
    for (const char *p = m->shorty + 1; *p; p++) {
        uint64_t v = args[s];
        switch (*p) {
        case 'J': nc_int(&na, v); s += 2; break;
        case 'D': nc_double_bits(&na, v); s += 2; break;
        case 'F': nc_float_bits(&na, (uint32_t)v); s++; break;
        case 'L': nc_ptr(&na, vm_jni_new_local(t, (Object *)(uintptr_t)v)); s++; break;
        case 'Z': nc_int(&na, (uint8_t)v); s++; break;
        case 'B': nc_int(&na, (uint64_t)(int64_t)(int8_t)v); s++; break;
        case 'C': nc_int(&na, (uint16_t)v); s++; break;
        case 'S': nc_int(&na, (uint64_t)(int64_t)(int16_t)v); s++; break;
        default: nc_int(&na, (uint64_t)(int64_t)(int32_t)v); s++; break;
        }
    }
    uint64_t ret[2] = {0, 0};
    void *fn = m->jni_fn;
    vm_gil_release(t);
    nc_call(fn, &na, ret);
    vm_gil_acquire(t);
    switch (m->shorty[0]) {
    case 'V': break;
    case 'Z': result.i = (uint8_t)ret[0] != 0; break;
    case 'B': result.i = (int8_t)ret[0]; break;
    case 'C': result.i = (uint16_t)ret[0]; break;
    case 'S': result.i = (int16_t)ret[0]; break;
    case 'I': result.i = (int32_t)ret[0]; break;
    case 'J': result.j = (int64_t)ret[0]; break;
    case 'F': result.raw = (uint32_t)ret[1]; break;
    case 'D': result.raw = ret[1]; break;
    default: result.l = D((jobject)(uintptr_t)ret[0]); break;
    }
    vm_jni_pop_frame(t);
    return result;
}
