/*
 * Native methods for java.lang.
 */
#include "natives.h"

#include <math.h>
#include <ctype.h>

#define LOG_TAG "java.lang"

/* ---- Object --------------------------------------------------------------------- */

NATIVE(Object_getClass) {
    UNUSED_ARGS();
    R_OBJ(vm_class_mirror(t, A_OBJ(0)->clazz));
}

static int32_t identity_hash(Object *o) {
    uint64_t k = (uint64_t)(uintptr_t)o;
    k ^= k >> 33;
    k *= 0xff51afd7ed558ccdULL;
    k ^= k >> 33;
    return (int32_t)(k & 0x7fffffff);
}

NATIVE(Object_hashCode) {
    UNUSED_ARGS();
    R_INT(identity_hash(A_OBJ(0)));
}

NATIVE(Object_clone) {
    UNUSED_ARGS();
    Object *o = A_OBJ(0);
    if (!o->clazz->is_array && !vm_is_assignable(g_vm.wk.Cloneable, o->clazz)) {
        vm_throw_new(t, "Ljava/lang/CloneNotSupportedException;", "Class %s doesn't implement Cloneable", o->clazz->name);
        return;
    }
    R_OBJ(vm_clone(t, o));
}

NATIVE(Object_notify) {
    UNUSED_ARGS();
    vm_monitor_notify(t, A_OBJ(0), false);
}

NATIVE(Object_notifyAll) {
    UNUSED_ARGS();
    vm_monitor_notify(t, A_OBJ(0), true);
}

NATIVE(Object_wait) {
    UNUSED_ARGS();
    vm_monitor_wait(t, A_OBJ(0), A_LONG(1), A_INT(3));
}

/* ---- Class ------------------------------------------------------------------------ */

static Class *this_class(uint64_t *args) { return vm_class_from_mirror(A_OBJ(0)); }

NATIVE(Class_getNameNative) {
    UNUSED_ARGS();
    R_OBJ(vm_new_string_utf8(t, this_class(args)->name));
}

NATIVE(Class_classForName) {
    UNUSED_ARGS();
    Object *name = A_OBJ(0);
    if (!name) {
        vm_throw_npe(t, "class name");
        return;
    }
    char *n = vm_string_to_utf8(name);
    Class *c = vm_class_from_name(t, n, A_BOOL(1));
    free(n);
    if (c) R_OBJ(vm_class_mirror(t, c));
}

NATIVE(Class_getPrimitiveClass) {
    UNUSED_ARGS();
    char *n = nat_str(A_OBJ(0));
    char *d = vm_dotted_to_desc(n);
    Class *c = vm_primitive_class(d[0]);
    free(n);
    free(d);
    R_OBJ(c ? vm_class_mirror(t, c) : NULL);
}

NATIVE(Class_getSuperclass) {
    UNUSED_ARGS();
    Class *c = this_class(args);
    if (c->access & ACC_INTERFACE || c->prim) {
        R_OBJ(NULL);
        return;
    }
    R_OBJ(c->super ? vm_class_mirror(t, c->super) : NULL);
}

NATIVE(Class_getInterfaces) {
    UNUSED_ARGS();
    Class *c = this_class(args);
    Class *arrc = vm_array_class_of(t, g_vm.wk.Class);
    ArrayObject *a = vm_alloc_array(t, arrc, (int32_t)c->ninterfaces);
    if (!a) return;
    for (uint32_t i = 0; i < c->ninterfaces; i++) ARRAY_DATA(a, Object *)[i] = vm_class_mirror(t, c->interfaces[i]);
    R_OBJ(a);
}

NATIVE(Class_getComponentType) {
    UNUSED_ARGS();
    Class *c = this_class(args);
    R_OBJ(c->component ? vm_class_mirror(t, c->component) : NULL);
}

NATIVE(Class_isArray) {
    UNUSED_ARGS();
    R_BOOL(this_class(args)->is_array);
}

NATIVE(Class_isPrimitive) {
    UNUSED_ARGS();
    R_BOOL(this_class(args)->prim != 0);
}

NATIVE(Class_isInterface) {
    UNUSED_ARGS();
    R_BOOL((this_class(args)->access & ACC_INTERFACE) != 0);
}


NATIVE(Class_isInstance) {
    UNUSED_ARGS();
    R_BOOL(vm_instance_of(A_OBJ(1), this_class(args)));
}

NATIVE(Class_isAssignableFrom) {
    UNUSED_ARGS();
    Object *other = A_OBJ(1);
    if (!other) {
        vm_throw_npe(t, "class");
        return;
    }
    R_BOOL(vm_is_assignable(this_class(args), vm_class_from_mirror(other)));
}

NATIVE(Class_newInstance) {
    UNUSED_ARGS();
    Class *c = this_class(args);
    if (c->access & (ACC_ABSTRACT | ACC_INTERFACE) || c->is_array || c->prim) {
        vm_throw_new(t, "Ljava/lang/InstantiationException;", "%s", c->name);
        return;
    }
    if (!vm_init_class(t, c)) return;
    Method *ctor = vm_find_method(c, "<init>", "()V");
    if (!ctor) {
        vm_throw_new(t, "Ljava/lang/InstantiationException;", "%s has no zero argument constructor", c->name);
        return;
    }
    Object *o = vm_alloc_object(t, c);
    if (!o) return;
    JValue a[1];
    a[0].l = o;
    vm_callv(t, ctor, a);
    if (t->exception) {
        Object *cause = t->exception;
        t->exception = NULL;
        Object *ite = vm_new_instance(t, "Ljava/lang/reflect/InvocationTargetException;", "(Ljava/lang/Throwable;)V", cause);
        t->exception = ite ? ite : cause;
        return;
    }
    R_OBJ(o);
}

NATIVE(Class_desiredAssertionStatus) {
    UNUSED_ARGS();
    R_BOOL(false);
}

typedef struct {
    const char *want;
    Object *result;
    VMThread *t;
    bool found;
    int32_t flags;
} AnnCtx;

static void inner_class_ann(DexFile *d, const char *type, uint32_t vis, const uint8_t *el, void *ctx) {
    SA_UNUSED(vis);
    AnnCtx *c = ctx;
    if (strcmp(type, c->want) != 0) return;
    c->found = true;
    uint32_t n = dex_uleb128(&el);
    for (uint32_t i = 0; i < n; i++) {
        const char *ename = dex_string(d, dex_uleb128(&el));
        DexEncodedValue v;
        dex_read_encoded_value(&el, &v);
        if (!strcmp(ename, "name") && v.type == DEV_STRING) c->result = vm_new_string_mutf8(c->t, dex_string(d, v.u.idx));
        if (!strcmp(ename, "accessFlags")) c->flags = v.u.i;
        if (!strcmp(ename, "value") && v.type == DEV_TYPE) {
            Class *k = vm_resolve_type(c->t, d, v.u.idx);
            if (k) c->result = vm_class_mirror(c->t, k);
        }
        if (!strcmp(ename, "value") && v.type == DEV_METHOD) {
            DexMethodId mid;
            dex_method_id(d, v.u.idx, &mid);
            Class *k = vm_resolve_type(c->t, d, mid.class_idx);
            if (k) c->result = vm_class_mirror(c->t, k);
        }
    }
}

/* Member classes take their modifiers (static, private, ...) from the
 * InnerClass annotation, as ART does; the class_def flags lack them. */
NATIVE(Class_getModifiers) {
    UNUSED_ARGS();
    Class *c = this_class(args);
    int32_t flags = (int32_t)(c->access & 0xffff);
    if (c->dex && !c->is_array && !c->prim) {
        DexClassDef cd;
        dex_class_def(c->dex, (uint32_t)c->class_def_idx, &cd);
        AnnCtx ctx = {"Ldalvik/annotation/InnerClass;", NULL, t, false, -1};
        dex_class_annotations(c->dex, &cd, inner_class_ann, &ctx);
        if (ctx.found && ctx.flags != -1) flags = ctx.flags & 0xffff;
        /* class_def keeps ACC_ANNOTATION / ACC_ENUM / ACC_INTERFACE even when
         * the InnerClass access flags drop them. */
        flags |= (int32_t)(c->access & (ACC_ANNOTATION | ACC_ENUM | ACC_INTERFACE));
    }
    R_INT(flags);
}

/* Returns the InnerClass annotation name; sets *is_inner */
NATIVE(Class_getInnerClassName) {
    UNUSED_ARGS();
    Class *c = this_class(args);
    if (!c->dex) return;
    DexClassDef cd;
    dex_class_def(c->dex, (uint32_t)c->class_def_idx, &cd);
    AnnCtx ctx = {"Ldalvik/annotation/InnerClass;", NULL, t, false, 0};
    dex_class_annotations(c->dex, &cd, inner_class_ann, &ctx);
    R_OBJ(ctx.result);
}

NATIVE(Class_isInnerClassNative) {
    UNUSED_ARGS();
    Class *c = this_class(args);
    if (!c->dex) return;
    DexClassDef cd;
    dex_class_def(c->dex, (uint32_t)c->class_def_idx, &cd);
    AnnCtx ctx = {"Ldalvik/annotation/InnerClass;", NULL, t, false, 0};
    dex_class_annotations(c->dex, &cd, inner_class_ann, &ctx);
    R_BOOL(ctx.found);
}

NATIVE(Class_getEnclosingClassNative) {
    UNUSED_ARGS();
    Class *c = this_class(args);
    if (!c->dex) return;
    DexClassDef cd;
    dex_class_def(c->dex, (uint32_t)c->class_def_idx, &cd);
    AnnCtx ctx = {"Ldalvik/annotation/EnclosingClass;", NULL, t, false, 0};
    dex_class_annotations(c->dex, &cd, inner_class_ann, &ctx);
    if (!ctx.result) {
        ctx.want = "Ldalvik/annotation/EnclosingMethod;";
        dex_class_annotations(c->dex, &cd, inner_class_ann, &ctx);
    }
    R_OBJ(ctx.result);
}

static void enclosing_method_ann(DexFile *d, const char *type, uint32_t vis, const uint8_t *el, void *ctx) {
    SA_UNUSED(vis);
    AnnCtx *c = ctx;
    if (strcmp(type, "Ldalvik/annotation/EnclosingMethod;") != 0) return;
    uint32_t n = dex_uleb128(&el);
    for (uint32_t i = 0; i < n; i++) {
        const char *ename = dex_string(d, dex_uleb128(&el));
        DexEncodedValue v;
        dex_read_encoded_value(&el, &v);
        if (!strcmp(ename, "value") && v.type == DEV_METHOD) {
            DexMethodId mid;
            dex_method_id(d, v.u.idx, &mid);
            const char *name = dex_string(d, mid.name_idx);
            const char *desc = dex_proto_desc(d, mid.proto_idx);
            size_t len = strlen(name) + strlen(desc) + 1;
            char *buf = sa_malloc(len);
            snprintf(buf, len, "%s%s", name, desc);
            c->result = vm_new_string_mutf8(c->t, buf);
            free(buf);
        }
    }
}

/* "name(params)ret" of the method or constructor a local or anonymous class is declared in, or null. */
NATIVE(Class_getEnclosingMethodDesc) {
    UNUSED_ARGS();
    Class *c = this_class(args);
    if (!c->dex) return;
    DexClassDef cd;
    dex_class_def(c->dex, (uint32_t)c->class_def_idx, &cd);
    AnnCtx ctx = {"Ldalvik/annotation/EnclosingMethod;", NULL, t, false, 0};
    dex_class_annotations(c->dex, &cd, enclosing_method_ann, &ctx);
    R_OBJ(ctx.result);
}

/* ---- String -------------------------------------------------------------------------- */

NATIVE(String_intern) {
    UNUSED_ARGS();
    R_OBJ(vm_intern_string(t, A_OBJ(0)));
}

NATIVE(String_equals) {
    UNUSED_ARGS();
    Object *a = A_OBJ(0), *b = A_OBJ(1);
    if (a == b) {
        R_BOOL(true);
        return;
    }
    if (!b || b->clazz != g_vm.wk.String) {
        R_BOOL(false);
        return;
    }
    int32_t la = vm_string_length(a), lb = vm_string_length(b);
    R_BOOL(la == lb && memcmp(vm_string_chars(a), vm_string_chars(b), (size_t)la * 2) == 0);
}

NATIVE(String_hashCode) {
    UNUSED_ARGS();
    Object *s = A_OBJ(0);
    int32_t h = g_vm.wf.String_hash ? vm_get_int(s, g_vm.wf.String_hash) : 0;
    if (h == 0) {
        const uint16_t *c = vm_string_chars(s);
        int32_t n = vm_string_length(s);
        uint32_t hh = 0;
        for (int32_t i = 0; i < n; i++) hh = 31 * hh + c[i];
        h = (int32_t)hh;
        if (g_vm.wf.String_hash) vm_set_int(s, g_vm.wf.String_hash, h);
    }
    R_INT(h);
}

NATIVE(String_indexOfChar) {
    UNUSED_ARGS();
    Object *s = A_OBJ(0);
    int32_t ch = A_INT(1), from = A_INT(2);
    const uint16_t *c = vm_string_chars(s);
    int32_t n = vm_string_length(s);
    if (from < 0) from = 0;
    if (ch < 0x10000) {
        for (int32_t i = from; i < n; i++)
            if (c[i] == ch) {
                R_INT(i);
                return;
            }
    } else {
        uint16_t hi = (uint16_t)(0xd800 + ((ch - 0x10000) >> 10)), lo = (uint16_t)(0xdc00 + ((ch - 0x10000) & 0x3ff));
        for (int32_t i = from; i + 1 < n; i++)
            if (c[i] == hi && c[i + 1] == lo) {
                R_INT(i);
                return;
            }
    }
    R_INT(-1);
}

NATIVE(String_indexOfString) {
    UNUSED_ARGS();
    Object *s = A_OBJ(0), *sub = A_OBJ(1);
    int32_t from = A_INT(2);
    if (!sub) {
        vm_throw_npe(t, "indexOf");
        return;
    }
    const uint16_t *c = vm_string_chars(s), *d = vm_string_chars(sub);
    int32_t n = vm_string_length(s), m = vm_string_length(sub);
    if (from < 0) from = 0;
    if (m == 0) {
        R_INT(from <= n ? from : n);
        return;
    }
    for (int32_t i = from; i + m <= n; i++) {
        if (c[i] == d[0] && memcmp(c + i, d, (size_t)m * 2) == 0) {
            R_INT(i);
            return;
        }
    }
    R_INT(-1);
}

NATIVE(String_compareTo) {
    UNUSED_ARGS();
    Object *a = A_OBJ(0), *b = A_OBJ(1);
    if (!b) {
        vm_throw_npe(t, "compareTo");
        return;
    }
    const uint16_t *x = vm_string_chars(a), *y = vm_string_chars(b);
    int32_t la = vm_string_length(a), lb = vm_string_length(b);
    int32_t n = la < lb ? la : lb;
    for (int32_t i = 0; i < n; i++)
        if (x[i] != y[i]) {
            R_INT((int32_t)x[i] - (int32_t)y[i]);
            return;
        }
    R_INT(la - lb);
}

/* ---- System ------------------------------------------------------------------------------ */

NATIVE(System_arraycopy) {
    UNUSED_ARGS();
    ArrayObject *src = A_ARR(0), *dst = A_ARR(2);
    int32_t sp = A_INT(1), dp = A_INT(3), len = A_INT(4);
    if (!src || !dst) {
        vm_throw_npe(t, "arraycopy");
        return;
    }
    Class *sc = src->obj.clazz, *dc = dst->obj.clazz;
    if (!sc->is_array || !dc->is_array) {
        vm_throw_new(t, "Ljava/lang/ArrayStoreException;", "arraycopy: not an array");
        return;
    }
    if (sp < 0 || dp < 0 || len < 0 || (int64_t)sp + len > src->length || (int64_t)dp + len > dst->length) {
        vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;",
                     "src.length=%d srcPos=%d dst.length=%d dstPos=%d length=%d", src->length, sp, dst->length, dp, len);
        return;
    }
    if (sc->component->prim || dc->component->prim) {
        if (sc->component != dc->component) {
            vm_throw_new(t, "Ljava/lang/ArrayStoreException;", "arraycopy: type mismatch %s -> %s", sc->name, dc->name);
            return;
        }
        size_t es = sc->elem_size;
        memmove((uint8_t *)dst->data + (size_t)dp * es, (uint8_t *)src->data + (size_t)sp * es, (size_t)len * es);
        return;
    }
    Object **s = ARRAY_DATA(src, Object *), **d = ARRAY_DATA(dst, Object *);
    if (vm_is_assignable(dc->component, sc->component)) {
        memmove(d + dp, s + sp, (size_t)len * sizeof(Object *));
        return;
    }
    for (int32_t i = 0; i < len; i++) {
        Object *o = s[sp + i];
        if (o && !vm_is_assignable(dc->component, o->clazz)) {
            vm_throw_new(t, "Ljava/lang/ArrayStoreException;", "%s cannot be stored in %s", o->clazz->name, dc->name);
            return;
        }
        d[dp + i] = o;
    }
}

NATIVE(System_currentTimeMillis) {
    UNUSED_ARGS();
    R_LONG(sa_wall_time_ms());
}

NATIVE(System_nanoTime) {
    UNUSED_ARGS();
    R_LONG((int64_t)sa_time_ns());
}

NATIVE(System_identityHashCode) {
    UNUSED_ARGS();
    Object *o = A_OBJ(0);
    R_INT(o ? identity_hash(o) : 0);
}

NATIVE(System_nativeExit) {
    UNUSED_ARGS();
    LOGI("System.exit(%d)", A_INT(0));
    g_vm.exit_code = A_INT(0);
    g_vm.exiting = true;
    vm_throw_new(t, "Ljava/lang/ThreadDeath;", "System.exit");
}

NATIVE(System_gc) {
    UNUSED_ARGS();
    vm_gc(t);
}

NATIVE(System_logNative) {
    UNUSED_ARGS();
    char *s = nat_str(A_OBJ(1));
    sa_log(A_INT(0), "System", "%s", s ? s : "null");
    free(s);
}

/* static void libcore.io.Logcat.println(int priority, String tag, String msg) */
NATIVE(Logcat_println) {
    UNUSED_ARGS();
    char *tag = nat_str(A_OBJ(1));
    char *msg = nat_str(A_OBJ(2));
    int prio = A_INT(0);
    if (prio < SA_LOG_VERBOSE) prio = SA_LOG_VERBOSE;
    if (prio > SA_LOG_FATAL) prio = SA_LOG_FATAL;
    /* One log line per message line, as logcat shows multi-line messages. */
    char *line = msg ? msg : "null";
    while (line) {
        char *nl = strchr(line, '\n');
        if (nl) *nl = 0;
        if (*line || nl) sa_log(prio, tag ? tag : "?", "%s", line);
        line = nl ? nl + 1 : NULL;
        if (line && !*line) break;
    }
    free(tag);
    free(msg);
}

NATIVE(Runtime_availableProcessors) {
    UNUSED_ARGS();
    R_INT(3);
}

NATIVE(Runtime_freeMemory) {
    UNUSED_ARGS();
    size_t total = g_vm.heap_threshold * 2;
    R_LONG(total > g_vm.heap_bytes ? total - g_vm.heap_bytes : 0);
}

NATIVE(Runtime_totalMemory) {
    UNUSED_ARGS();
    R_LONG(g_vm.heap_threshold * 2);
}

NATIVE(Runtime_maxMemory) {
    UNUSED_ARGS();
    R_LONG(256ll * 1024 * 1024);
}

/* ---- Thread --------------------------------------------------------------------------------- */

NATIVE(Thread_currentThread) {
    UNUSED_ARGS();
    R_OBJ(t->jthread);
}

NATIVE(Thread_nativeStart) {
    UNUSED_ARGS();
    vm_thread_start(t, A_OBJ(0), A_LONG(1));
}

NATIVE(Thread_sleep) {
    UNUSED_ARGS();
    vm_thread_sleep(t, A_LONG(0), A_INT(2));
}

NATIVE(Thread_yield) {
    UNUSED_ARGS();
    vm_yield(t);
}

NATIVE(Thread_interruptNative) {
    UNUSED_ARGS();
    vm_thread_interrupt(t, A_OBJ(0));
}

NATIVE(Thread_interrupted) {
    UNUSED_ARGS();
    bool v = t->interrupted;
    t->interrupted = false;
    R_BOOL(v);
}

NATIVE(Thread_isInterruptedNative) {
    UNUSED_ARGS();
    VMThread *target = (VMThread *)(intptr_t)vm_get_long(A_OBJ(0), g_vm.wf.Thread_vmThread);
    R_BOOL(target && target->interrupted);
}

NATIVE(Thread_holdsLock) {
    UNUSED_ARGS();
    R_BOOL(vm_monitor_holds(t, A_OBJ(0)));
}

/* ---- Throwable -------------------------------------------------------------------------------- */

NATIVE(Throwable_fillInStackTrace) {
    UNUSED_ARGS();
    vm_fill_stack_trace(t, A_OBJ(0));
    R_OBJ(A_OBJ(0));
}

NATIVE(Throwable_getStackTraceNative) {
    UNUSED_ARGS();
    R_OBJ(vm_get_stack_trace(t, A_OBJ(0)));
}

/* ---- Math ------------------------------------------------------------------------------------- */

#define MATH1(name, expr)                \
    NATIVE(Math_##name) {                \
        UNUSED_ARGS();                   \
        double x = A_DOUBLE(0);          \
        R_DOUBLE(expr);                  \
    }
MATH1(sin, sin(x))
MATH1(cos, cos(x))
MATH1(tan, tan(x))
MATH1(asin, asin(x))
MATH1(acos, acos(x))
MATH1(atan, atan(x))
MATH1(exp, exp(x))
MATH1(log, log(x))
MATH1(log10, log10(x))
MATH1(log1p, log1p(x))
MATH1(expm1, expm1(x))
MATH1(sqrt, sqrt(x))
MATH1(cbrt, cbrt(x))
MATH1(floor, floor(x))
MATH1(ceil, ceil(x))
MATH1(rint, rint(x))
MATH1(sinh, sinh(x))
MATH1(cosh, cosh(x))
MATH1(tanh, tanh(x))

NATIVE(Math_atan2) {
    UNUSED_ARGS();
    R_DOUBLE(atan2(A_DOUBLE(0), A_DOUBLE(2)));
}
NATIVE(Math_pow) {
    UNUSED_ARGS();
    R_DOUBLE(pow(A_DOUBLE(0), A_DOUBLE(2)));
}
NATIVE(Math_hypot) {
    UNUSED_ARGS();
    R_DOUBLE(hypot(A_DOUBLE(0), A_DOUBLE(2)));
}
NATIVE(Math_IEEEremainder) {
    UNUSED_ARGS();
    R_DOUBLE(remainder(A_DOUBLE(0), A_DOUBLE(2)));
}

/* ---- Float / Double ----------------------------------------------------------------------------- */

NATIVE(Float_floatToRawIntBits) {
    UNUSED_ARGS();
    R_INT((int32_t)(uint32_t)args[0]);
}
NATIVE(Float_intBitsToFloat) {
    UNUSED_ARGS();
    ret->raw = (uint32_t)args[0];
}
NATIVE(Double_doubleToRawLongBits) {
    UNUSED_ARGS();
    R_LONG((int64_t)args[0]);
}
NATIVE(Double_longBitsToDouble) {
    UNUSED_ARGS();
    ret->raw = args[0];
}

/* Formats a floating point value the way Java's Double.toString/Float.toString do. */
static void java_fp_to_string(double v, bool is_float, char *out, size_t outsz) {
    if (v != v) {
        snprintf(out, outsz, "NaN");
        return;
    }
    if (isinf(v)) {
        snprintf(out, outsz, v > 0 ? "Infinity" : "-Infinity");
        return;
    }
    if (v == 0) {
        snprintf(out, outsz, signbit(v) ? "-0.0" : "0.0");
        return;
    }
    char buf[64];
    int maxp = is_float ? 9 : 17;
    for (int p = 1; p <= maxp; p++) {
        snprintf(buf, sizeof buf, "%.*e", p - 1, v);
        double back = strtod(buf, NULL);
        if (is_float ? ((float)back == (float)v) : (back == v)) {
            /* Like Java, a one-digit result is replaced by the closest two-digit decimal (MIN_VALUE prints
             * 1.4E-45, not 1.0E-45); trailing zeros are dropped below. */
            if (p == 1) snprintf(buf, sizeof buf, "%.1e", v);
            break;
        }
    }
    /* parse "-d.ddde+XX" */
    const char *p = buf;
    bool neg = false;
    if (*p == '-') {
        neg = true;
        p++;
    }
    char digits[40];
    int nd = 0;
    while (*p && *p != 'e') {
        if (isdigit((unsigned char)*p)) digits[nd++] = *p;
        p++;
    }
    while (nd > 1 && digits[nd - 1] == '0') nd--;
    digits[nd] = 0;
    int e = (*p == 'e') ? atoi(p + 1) : 0;
    char res[80];
    size_t r = 0;
    if (neg) res[r++] = '-';
    double av = fabs(v);
    if (av >= 1e-3 && av < 1e7) {
        if (e >= 0) {
            for (int i = 0; i <= e; i++) res[r++] = i < nd ? digits[i] : '0';
            res[r++] = '.';
            if (nd > e + 1)
                for (int i = e + 1; i < nd; i++) res[r++] = digits[i];
            else
                res[r++] = '0';
        } else {
            res[r++] = '0';
            res[r++] = '.';
            for (int i = 0; i < -e - 1; i++) res[r++] = '0';
            for (int i = 0; i < nd; i++) res[r++] = digits[i];
        }
        res[r] = 0;
    } else {
        res[r++] = digits[0];
        res[r++] = '.';
        if (nd > 1)
            for (int i = 1; i < nd; i++) res[r++] = digits[i];
        else
            res[r++] = '0';
        res[r] = 0;
        char eb[16];
        snprintf(eb, sizeof eb, "E%d", e);
        strcat(res, eb);
    }
    snprintf(out, outsz, "%s", res);
}

NATIVE(Double_toStringNative) {
    UNUSED_ARGS();
    char buf[64];
    java_fp_to_string(A_DOUBLE(0), false, buf, sizeof buf);
    R_OBJ(vm_new_string_utf8(t, buf));
}

NATIVE(Float_toStringNative) {
    UNUSED_ARGS();
    char buf[64];
    java_fp_to_string((double)A_FLOAT(0), true, buf, sizeof buf);
    R_OBJ(vm_new_string_utf8(t, buf));
}

static bool parse_java_double(const char *s, double *out) {
    while (*s && isspace((unsigned char)*s)) s++;
    size_t n = strlen(s);
    while (n && isspace((unsigned char)s[n - 1])) n--;
    if (!n) return false;
    char *tmp = sa_strndup(s, n);
    if (n > 1 && strchr("fFdD", tmp[n - 1]) && !strstr(tmp, "Infinity") && !(tmp[0] == '0' && (tmp[1] == 'x' || tmp[1] == 'X')))
        tmp[--n] = 0;
    const char *q = tmp;
    if (*q == '+' || *q == '-') q++;
    bool ok = true;
    if (!strcmp(q, "NaN")) *out = NAN;
    else if (!strcmp(q, "Infinity")) *out = tmp[0] == '-' ? -INFINITY : INFINITY;
    else {
        /* Java does not accept "inf"/"nan" spellings or empty mantissa */
        if (!(isdigit((unsigned char)*q) || *q == '.')) ok = false;
        char *end;
        *out = strtod(tmp, &end);
        if (end == tmp || *end) ok = false;
    }
    free(tmp);
    return ok;
}

NATIVE(Double_parseDoubleNative) {
    UNUSED_ARGS();
    Object *s = A_OBJ(0);
    if (!s) {
        vm_throw_npe(t, "parseDouble");
        return;
    }
    char *u = vm_string_to_utf8(s);
    double d;
    if (!parse_java_double(u, &d)) {
        vm_throw_new(t, "Ljava/lang/NumberFormatException;", "For input string: \"%s\"", u);
        free(u);
        return;
    }
    free(u);
    R_DOUBLE(d);
}

/* Formats a double with printf semantics; used by java.util.Formatter. */
NATIVE(Formatter_formatDouble) {
    UNUSED_ARGS();
    char *spec = nat_str(A_OBJ(0));
    double v = A_DOUBLE(1);
    char buf[512];
    if (!spec) spec = sa_strdup("%f");
    /* only allow safe floating point conversions */
    size_t n = strlen(spec);
    char conv = n ? spec[n - 1] : 'f';
    if (!strchr("eEfgGaA", conv) || strchr(spec, '*') || strchr(spec + 1, '%')) {
        free(spec);
        spec = sa_strdup("%f");
    }
    snprintf(buf, sizeof buf, spec, v);
    free(spec);
    R_OBJ(vm_new_string_utf8(t, buf));
}

/* ---- ref ----------------------------------------------------------------------------------------- */

NATIVE(Reference_get) {
    UNUSED_ARGS();
    R_OBJ(g_vm.wf.Reference_referent ? vm_get_ref(A_OBJ(0), g_vm.wf.Reference_referent) : NULL);
}

/* ---- registration ----------------------------------------------------------------------------------- */

static const NativeMethodReg g_regs[] = {
    {"Ljava/lang/Object;", "getClass", "()Ljava/lang/Class;", Object_getClass},
    {"Ljava/lang/Object;", "hashCode", "()I", Object_hashCode},
    {"Ljava/lang/Object;", "internalClone", "()Ljava/lang/Object;", Object_clone},
    {"Ljava/lang/Object;", "notify", "()V", Object_notify},
    {"Ljava/lang/Object;", "notifyAll", "()V", Object_notifyAll},
    {"Ljava/lang/Object;", "wait", "(JI)V", Object_wait},

    {"Ljava/lang/Class;", "getNameNative", "()Ljava/lang/String;", Class_getNameNative},
    {"Ljava/lang/Class;", "classForName", "(Ljava/lang/String;Z)Ljava/lang/Class;", Class_classForName},
    {"Ljava/lang/Class;", "getPrimitiveClass", "(Ljava/lang/String;)Ljava/lang/Class;", Class_getPrimitiveClass},
    {"Ljava/lang/Class;", "getSuperclass", "()Ljava/lang/Class;", Class_getSuperclass},
    {"Ljava/lang/Class;", "getInterfaces", "()[Ljava/lang/Class;", Class_getInterfaces},
    {"Ljava/lang/Class;", "getComponentType", "()Ljava/lang/Class;", Class_getComponentType},
    {"Ljava/lang/Class;", "isArray", "()Z", Class_isArray},
    {"Ljava/lang/Class;", "isPrimitive", "()Z", Class_isPrimitive},
    {"Ljava/lang/Class;", "isInterface", "()Z", Class_isInterface},
    {"Ljava/lang/Class;", "getModifiers", "()I", Class_getModifiers},
    {"Ljava/lang/Class;", "isInstance", "(Ljava/lang/Object;)Z", Class_isInstance},
    {"Ljava/lang/Class;", "isAssignableFrom", "(Ljava/lang/Class;)Z", Class_isAssignableFrom},
    {"Ljava/lang/Class;", "newInstance", "()Ljava/lang/Object;", Class_newInstance},
    {"Ljava/lang/Class;", "desiredAssertionStatus", "()Z", Class_desiredAssertionStatus},
    {"Ljava/lang/Class;", "getInnerClassName", "()Ljava/lang/String;", Class_getInnerClassName},
    {"Ljava/lang/Class;", "isInnerClassNative", "()Z", Class_isInnerClassNative},
    {"Ljava/lang/Class;", "getEnclosingClassNative", "()Ljava/lang/Class;", Class_getEnclosingClassNative},
    {"Ljava/lang/Class;", "getEnclosingMethodDesc", "()Ljava/lang/String;", Class_getEnclosingMethodDesc},

    {"Ljava/lang/String;", "intern", "()Ljava/lang/String;", String_intern},
    {"Ljava/lang/String;", "equals", "(Ljava/lang/Object;)Z", String_equals},
    {"Ljava/lang/String;", "hashCode", "()I", String_hashCode},
    {"Ljava/lang/String;", "indexOf", "(II)I", String_indexOfChar},
    {"Ljava/lang/String;", "indexOf", "(Ljava/lang/String;I)I", String_indexOfString},
    {"Ljava/lang/String;", "compareTo", "(Ljava/lang/String;)I", String_compareTo},

    {"Ljava/lang/System;", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", System_arraycopy},
    {"Ljava/lang/System;", "currentTimeMillis", "()J", System_currentTimeMillis},
    {"Ljava/lang/System;", "nanoTime", "()J", System_nanoTime},
    {"Ljava/lang/System;", "identityHashCode", "(Ljava/lang/Object;)I", System_identityHashCode},
    {"Ljava/lang/System;", "nativeExit", "(I)V", System_nativeExit},
    {"Ljava/lang/System;", "gc", "()V", System_gc},
    {"Ljava/lang/System;", "logNative", "(ILjava/lang/String;)V", System_logNative},
    {"Llibcore/io/Logcat;", "println", "(ILjava/lang/String;Ljava/lang/String;)V", Logcat_println},
    {"Ljava/lang/Runtime;", "availableProcessors", "()I", Runtime_availableProcessors},
    {"Ljava/lang/Runtime;", "freeMemory", "()J", Runtime_freeMemory},
    {"Ljava/lang/Runtime;", "totalMemory", "()J", Runtime_totalMemory},
    {"Ljava/lang/Runtime;", "maxMemory", "()J", Runtime_maxMemory},

    {"Ljava/lang/Thread;", "currentThread", "()Ljava/lang/Thread;", Thread_currentThread},
    {"Ljava/lang/Thread;", "nativeStart", "(J)V", Thread_nativeStart},
    {"Ljava/lang/Thread;", "sleep", "(JI)V", Thread_sleep},
    {"Ljava/lang/Thread;", "yield", "()V", Thread_yield},
    {"Ljava/lang/Thread;", "interruptNative", "()V", Thread_interruptNative},
    {"Ljava/lang/Thread;", "interrupted", "()Z", Thread_interrupted},
    {"Ljava/lang/Thread;", "isInterruptedNative", "()Z", Thread_isInterruptedNative},
    {"Ljava/lang/Thread;", "holdsLock", "(Ljava/lang/Object;)Z", Thread_holdsLock},

    {"Ljava/lang/Throwable;", "fillInStackTraceNative", "()V", Throwable_fillInStackTrace},
    {"Ljava/lang/Throwable;", "getStackTraceNative", "()[Ljava/lang/StackTraceElement;", Throwable_getStackTraceNative},

    {"Ljava/lang/Math;", "sin", "(D)D", Math_sin},
    {"Ljava/lang/Math;", "cos", "(D)D", Math_cos},
    {"Ljava/lang/Math;", "tan", "(D)D", Math_tan},
    {"Ljava/lang/Math;", "asin", "(D)D", Math_asin},
    {"Ljava/lang/Math;", "acos", "(D)D", Math_acos},
    {"Ljava/lang/Math;", "atan", "(D)D", Math_atan},
    {"Ljava/lang/Math;", "exp", "(D)D", Math_exp},
    {"Ljava/lang/Math;", "log", "(D)D", Math_log},
    {"Ljava/lang/Math;", "log10", "(D)D", Math_log10},
    {"Ljava/lang/Math;", "log1p", "(D)D", Math_log1p},
    {"Ljava/lang/Math;", "expm1", "(D)D", Math_expm1},
    {"Ljava/lang/Math;", "sqrt", "(D)D", Math_sqrt},
    {"Ljava/lang/Math;", "cbrt", "(D)D", Math_cbrt},
    {"Ljava/lang/Math;", "floor", "(D)D", Math_floor},
    {"Ljava/lang/Math;", "ceil", "(D)D", Math_ceil},
    {"Ljava/lang/Math;", "rint", "(D)D", Math_rint},
    {"Ljava/lang/Math;", "sinh", "(D)D", Math_sinh},
    {"Ljava/lang/Math;", "cosh", "(D)D", Math_cosh},
    {"Ljava/lang/Math;", "tanh", "(D)D", Math_tanh},
    {"Ljava/lang/Math;", "atan2", "(DD)D", Math_atan2},
    {"Ljava/lang/Math;", "pow", "(DD)D", Math_pow},
    {"Ljava/lang/Math;", "hypot", "(DD)D", Math_hypot},
    {"Ljava/lang/Math;", "IEEEremainder", "(DD)D", Math_IEEEremainder},

    {"Ljava/lang/Float;", "floatToRawIntBits", "(F)I", Float_floatToRawIntBits},
    {"Ljava/lang/Float;", "intBitsToFloat", "(I)F", Float_intBitsToFloat},
    {"Ljava/lang/Float;", "toStringNative", "(F)Ljava/lang/String;", Float_toStringNative},
    {"Ljava/lang/Double;", "doubleToRawLongBits", "(D)J", Double_doubleToRawLongBits},
    {"Ljava/lang/Double;", "longBitsToDouble", "(J)D", Double_longBitsToDouble},
    {"Ljava/lang/Double;", "toStringNative", "(D)Ljava/lang/String;", Double_toStringNative},
    {"Ljava/lang/Double;", "parseDoubleNative", "(Ljava/lang/String;)D", Double_parseDoubleNative},
    {"Ljava/util/Formatter;", "formatDouble", "(Ljava/lang/String;D)Ljava/lang/String;", Formatter_formatDouble},

    {"Ljava/lang/ref/Reference;", "get", "()Ljava/lang/Object;", Reference_get},
};

void natives_java_lang_register(void) {
    vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs));
    /* StrictMath shares Math's implementations */
    for (size_t i = 0; i < SA_ARRAY_LEN(g_regs); i++) {
        if (strcmp(g_regs[i].cls, "Ljava/lang/Math;") != 0) continue;
        NativeMethodReg r = g_regs[i];
        r.cls = "Ljava/lang/StrictMath;";
        vm_register_natives(&r, 1);
    }
}
