/*
 * Exceptions, stack traces and helpers for calling Java from C.
 */
#include "vm.h"

#define LOG_TAG "exception"

/* ---- calling helpers ------------------------------------------------------------- */

static void pack_args(Method *m, const JValue *in, uint64_t *out, bool with_this) {
    size_t slot = 0, arg = 0;
    if (with_this) out[slot++] = (uint64_t)(uintptr_t)in[arg++].l;
    for (const char *p = m->shorty + 1; *p; p++) {
        JValue v = in[arg++];
        switch (*p) {
        case 'J':
        case 'D':
            out[slot] = (uint64_t)v.j;
            out[slot + 1] = 0;
            slot += 2;
            break;
        case 'L': out[slot++] = (uint64_t)(uintptr_t)v.l; break;
        case 'F': out[slot++] = (uint32_t)v.i; break;
        case 'Z': out[slot++] = (uint32_t)(v.i & 1); break;
        case 'B': out[slot++] = (uint32_t)(int32_t)(int8_t)v.i; break;
        case 'C': out[slot++] = (uint32_t)(uint16_t)v.i; break;
        case 'S': out[slot++] = (uint32_t)(int32_t)(int16_t)v.i; break;
        default: out[slot++] = (uint32_t)v.i; break;
        }
    }
}

JValue vm_callv(VMThread *t, Method *m, const JValue *args) {
    uint64_t slots[260];
    bool is_static = (m->access & ACC_STATIC) != 0;
    pack_args(m, args, slots, !is_static);
    if (is_static && m->clazz->state != CLASS_INITIALIZED && !vm_init_class(t, m->clazz)) {
        JValue z;
        z.raw = 0;
        return z;
    }
    JValue r = vm_invoke(t, m, slots);
    char rt = m->shorty[0];
    /* normalise sub-word results */
    if (rt == 'Z' || rt == 'B' || rt == 'C' || rt == 'S' || rt == 'I' || rt == 'F') r.raw = (uint32_t)r.raw;
    else if (rt == 'V') r.raw = 0;
    return r;
}

static void collect_varargs(Method *m, bool with_this, va_list ap, JValue *out) {
    size_t n = 0;
    if (with_this) out[n++].l = va_arg(ap, Object *);
    for (const char *p = m->shorty + 1; *p; p++) {
        JValue v;
        v.raw = 0;
        switch (*p) {
        case 'J': v.j = va_arg(ap, int64_t); break;
        case 'D': v.d = va_arg(ap, double); break;
        case 'F': v.f = (float)va_arg(ap, double); break;
        case 'L': v.l = va_arg(ap, Object *); break;
        default: v.i = va_arg(ap, int32_t); break;
        }
        out[n++] = v;
    }
}

JValue vm_call(VMThread *t, Method *m, ...) {
    JValue args[260];
    va_list ap;
    va_start(ap, m);
    collect_varargs(m, !(m->access & ACC_STATIC), ap, args);
    va_end(ap);
    return vm_callv(t, m, args);
}

JValue vm_call_virtual(VMThread *t, Object *recv, const char *name, const char *desc, ...) {
    JValue z;
    z.raw = 0;
    if (!recv) {
        vm_throw_npe(t, name);
        return z;
    }
    Method *m = vm_find_virtual(recv->clazz, name, desc);
    if (!m) {
        vm_throw_new(t, "Ljava/lang/NoSuchMethodError;", "%s.%s%s", recv->clazz->name, name, desc);
        return z;
    }
    JValue args[260];
    va_list ap;
    va_start(ap, desc);
    args[0].l = recv;
    Method tmp = *m;
    tmp.access &= ~ACC_STATIC;
    collect_varargs(&tmp, false, ap, args + 1);
    va_end(ap);
    return vm_callv(t, m, args);
}

JValue vm_call_static(VMThread *t, const char *cls, const char *name, const char *desc, ...) {
    JValue z;
    z.raw = 0;
    Class *c = vm_find_class(t, cls);
    if (!c) return z;
    if (!vm_init_class(t, c)) return z;
    Method *m = vm_find_method_hier(c, name, desc);
    if (!m) {
        vm_throw_new(t, "Ljava/lang/NoSuchMethodError;", "%s.%s%s", c->name, name, desc);
        return z;
    }
    JValue args[260];
    va_list ap;
    va_start(ap, desc);
    collect_varargs(m, false, ap, args);
    va_end(ap);
    return vm_callv(t, m, args);
}

Object *vm_new_instance(VMThread *t, const char *cls, const char *ctor_desc, ...) {
    Class *c = vm_find_class(t, cls);
    if (!c || !vm_init_class(t, c)) return NULL;
    Method *ctor = vm_find_method(c, "<init>", ctor_desc);
    if (!ctor) {
        vm_throw_new(t, "Ljava/lang/NoSuchMethodError;", "%s.<init>%s", c->name, ctor_desc);
        return NULL;
    }
    Object *o = vm_alloc_object(t, c);
    if (!o) return NULL;
    JValue args[260];
    va_list ap;
    va_start(ap, ctor_desc);
    args[0].l = o;
    Method tmp = *ctor;
    collect_varargs(&tmp, false, ap, args + 1);
    va_end(ap);
    vm_callv(t, ctor, args);
    if (t->exception) return NULL;
    return o;
}

/* ---- throwing ------------------------------------------------------------------------- */

void vm_throw(VMThread *t, Object *exc) { t->exception = exc; }

void vm_throw_new(VMThread *t, const char *cls_desc, const char *fmt, ...) {
    char msg[1024];
    bool has_msg = fmt != NULL;
    if (fmt) {
        va_list ap;
        va_start(ap, fmt);
        vsnprintf(msg, sizeof msg, fmt, ap);
        va_end(ap);
    }
    Object *prev = t->exception;
    t->exception = NULL;
    Class *c = vm_find_class_noexc(t, cls_desc);
    if (!c) {
        LOGE("cannot find exception class %s (message: %s)", cls_desc, has_msg ? msg : "");
        c = vm_find_class_noexc(t, "Ljava/lang/RuntimeException;");
        if (!c) sa_fatal("exception classes unavailable: %s: %s", cls_desc, has_msg ? msg : "");
    }
    SA_UNUSED(prev);
    Object *s = has_msg ? vm_new_string_utf8(t, msg) : NULL;
    Object *e = vm_new_instance(t, c->descriptor, "(Ljava/lang/String;)V", s);
    if (!e) {
        if (!t->exception) {
            /* no (String) constructor: try default */
            e = vm_new_instance(t, c->descriptor, "()V");
        }
        if (!e) {
            LOGE("failed to construct %s: %s", c->name, has_msg ? msg : "");
            if (!t->exception) sa_fatal("cannot construct exception %s", c->name);
            return;
        }
    }
    t->exception = e;
}

void vm_throw_npe(VMThread *t, const char *what) {
    if (what) vm_throw_new(t, "Ljava/lang/NullPointerException;", "Attempt to use null: %s", what);
    else vm_throw_new(t, "Ljava/lang/NullPointerException;", NULL);
}

void vm_throw_oom(VMThread *t) {
    static Object *preallocated;
    if (!preallocated) {
        LOGE("out of memory");
        vm_throw_new(t, "Ljava/lang/OutOfMemoryError;", "out of memory");
        return;
    }
    t->exception = preallocated;
}

bool vm_check_exception(VMThread *t) { return t->exception != NULL; }

/* ---- stack traces ---------------------------------------------------------------------- */

void vm_fill_stack_trace(VMThread *t, Object *throwable) {
    Field *bf = g_vm.wf.Throwable_backtrace;
    if (!bf) return;
    /* skip frames that are constructing this throwable */
    Frame *f = t->frame;
    while (f && f->method && (!strcmp(f->method->name, "fillInStackTrace") ||
                              (!strcmp(f->method->name, "<init>") && vm_is_assignable(f->method->clazz, throwable->clazz) &&
                               (f->method->clazz->flags & CF_THROWABLE))))
        f = f->prev;
    int32_t n = 0;
    for (Frame *g = f; g && n < 256; g = g->prev) n++;
    ArrayObject *arr = vm_alloc_prim_array(t, 'J', n * 2);
    if (!arr) return;
    int64_t *d = ARRAY_DATA(arr, int64_t);
    int32_t i = 0;
    for (Frame *g = f; g && i < n; g = g->prev, i++) {
        d[i * 2] = (int64_t)(intptr_t)g->method;
        d[i * 2 + 1] = g->pc;
    }
    vm_set_ref(throwable, bf, (Object *)arr);
    if (g_vm.wf.Throwable_stackTrace) vm_set_ref(throwable, g_vm.wf.Throwable_stackTrace, NULL);
}

Object *vm_get_stack_trace(VMThread *t, Object *throwable) {
    Class *ste = g_vm.wk.StackTraceElement;
    if (!ste) ste = g_vm.wk.StackTraceElement = vm_find_class(t, "Ljava/lang/StackTraceElement;");
    if (!ste || !vm_init_class(t, ste)) return NULL;
    ArrayObject *bt = (ArrayObject *)vm_get_ref(throwable, g_vm.wf.Throwable_backtrace);
    int32_t n = bt ? bt->length / 2 : 0;
    Class *arrc = vm_array_class_of(t, ste);
    ArrayObject *out = vm_alloc_array(t, arrc, n);
    if (!out) return NULL;
    Method *ctor = vm_find_method(ste, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V");
    for (int32_t i = 0; i < n; i++) {
        Method *m = (Method *)(intptr_t)ARRAY_DATA(bt, int64_t)[i * 2];
        uint32_t pc = (uint32_t)ARRAY_DATA(bt, int64_t)[i * 2 + 1];
        int line = -1;
        if (m->has_code && !m->native) line = dex_line_for_pc(m->dex, &m->code, pc);
        if (m->native || (m->access & ACC_NATIVE)) line = -2;
        Object *cls = vm_new_string_utf8(t, m->clazz->name);
        Object *mn = vm_new_string_utf8(t, m->name);
        Object *file = m->clazz->source_file ? vm_new_string_mutf8(t, m->clazz->source_file) : NULL;
        Object *e = vm_alloc_object(t, ste);
        if (!e) return NULL;
        ARRAY_DATA(out, Object *)[i] = e;
        if (ctor) {
            JValue args[5];
            args[0].l = e;
            args[1].l = cls;
            args[2].l = mn;
            args[3].l = file;
            args[4].raw = 0;
            args[4].i = line;
            vm_callv(t, ctor, args);
            if (t->exception) return NULL;
        }
    }
    return (Object *)out;
}

void vm_describe_exception(VMThread *t, Object *exc, SaBuf *out) {
    int depth = 0;
    Object *saved = t->exception;
    t->exception = NULL;
    while (exc && depth < 8) {
        if (depth) sa_buf_puts(out, "Caused by: ");
        sa_buf_puts(out, exc->clazz->name);
        Object *msg = g_vm.wf.Throwable_detailMessage ? vm_get_ref(exc, g_vm.wf.Throwable_detailMessage) : NULL;
        if (msg) {
            char *m = vm_string_to_utf8(msg);
            sa_buf_printf(out, ": %s", m);
            free(m);
        }
        sa_buf_putc(out, '\n');
        ArrayObject *bt = g_vm.wf.Throwable_backtrace ? (ArrayObject *)vm_get_ref(exc, g_vm.wf.Throwable_backtrace) : NULL;
        int32_t n = bt ? bt->length / 2 : 0;
        for (int32_t i = 0; i < n && i < 40; i++) {
            Method *m = (Method *)(intptr_t)ARRAY_DATA(bt, int64_t)[i * 2];
            uint32_t pc = (uint32_t)ARRAY_DATA(bt, int64_t)[i * 2 + 1];
            int line = (m->has_code && !m->native) ? dex_line_for_pc(m->dex, &m->code, pc) : -1;
            if (line >= 0)
                sa_buf_printf(out, "    at %s.%s(%s:%d)\n", m->clazz->name, m->name,
                              m->clazz->source_file ? m->clazz->source_file : "Unknown Source", line);
            else
                sa_buf_printf(out, "    at %s.%s(%s)\n", m->clazz->name, m->name,
                              (m->access & ACC_NATIVE) ? "Native Method" : "Unknown Source");
        }
        if (n > 40) sa_buf_printf(out, "    ... %d more\n", n - 40);
        Object *cause = g_vm.wf.Throwable_cause ? vm_get_ref(exc, g_vm.wf.Throwable_cause) : NULL;
        if (cause == exc) break;
        exc = cause;
        depth++;
    }
    t->exception = saved;
}

void vm_print_exception(VMThread *t, Object *exc) {
    SaBuf b = {0};
    vm_describe_exception(t, exc, &b);
    char *s = sa_buf_cstr(&b);
    char *line = s;
    while (line && *line) {
        char *nl = strchr(line, '\n');
        if (nl) *nl = 0;
        sa_log(SA_LOG_ERROR, "AndroidRuntime", "%s", line);
        line = nl ? nl + 1 : NULL;
    }
    sa_buf_free(&b);
}

void vm_dump_stack(VMThread *t) {
    for (Frame *f = t->frame; f; f = f->prev) {
        char buf[512];
        vm_method_pretty(f->method, buf, sizeof buf);
        int line = f->method->has_code ? dex_line_for_pc(f->method->dex, &f->method->code, f->pc) : -1;
        LOGI("  at %s (pc %u, line %d)", buf, f->pc, line);
    }
}
