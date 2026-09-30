/*
 * Dalvik bytecode interpreter.
 *
 * Calls between interpreted methods do not recurse on the C stack: frames
 * live on the thread's register stack and the loop switches between them.
 * Only calls that originate from C (natives, class initializers) start a new
 * activation of the loop.
 */
#include "vm.h"

#include <math.h>

#define LOG_TAG "interp"

static inline float u2f(uint32_t u) {
    float f;
    memcpy(&f, &u, 4);
    return f;
}
static inline uint64_t f2u(float f) {
    uint32_t u;
    memcpy(&u, &f, 4);
    return u;
}
static inline double u2d(uint64_t u) {
    double d;
    memcpy(&d, &u, 8);
    return d;
}
static inline uint64_t d2u(double d) {
    uint64_t u;
    memcpy(&u, &d, 8);
    return u;
}

static inline int32_t f2i(float f) {
    if (f != f) return 0;
    if (f >= 2147483648.0f) return INT32_MAX;
    if (f <= -2147483648.0f) return INT32_MIN;
    return (int32_t)f;
}
static inline int64_t f2l(double f) {
    if (f != f) return 0;
    if (f >= 9223372036854775808.0) return INT64_MAX;
    if (f <= -9223372036854775808.0) return INT64_MIN;
    return (int64_t)f;
}
static inline int32_t d2i(double d) {
    if (d != d) return 0;
    if (d >= 2147483648.0) return INT32_MAX;
    if (d <= -2147483648.0) return INT32_MIN;
    return (int32_t)d;
}

#define RI(r) ((int32_t)(uint32_t)regs[(r)])
#define RU(r) ((uint32_t)regs[(r)])
#define SETI(r, v) (regs[(r)] = (uint64_t)(uint32_t)(int32_t)(v))
#define RJ(r) ((int64_t)regs[(r)])
#define SETJ(r, v) (regs[(r)] = (uint64_t)(int64_t)(v))
#define RF(r) (u2f((uint32_t)regs[(r)]))
#define SETF(r, v) (regs[(r)] = f2u((v)))
#define RD(r) (u2d(regs[(r)]))
#define SETD(r, v) (regs[(r)] = d2u((v)))
#define RL(r) ((Object *)(uintptr_t)regs[(r)])
#define SETL(r, v) (regs[(r)] = (uint64_t)(uintptr_t)(v))

#define INST (insns[pc])
#define OP (INST & 0xff)
#define A4 ((INST >> 8) & 0xf)
#define B4 (INST >> 12)
#define AA (INST >> 8)
#define W1 (insns[pc + 1])
#define W2 (insns[pc + 2])
#define S16(x) ((int32_t)(int16_t)(x))
#define I32AT(o) ((int32_t)((uint32_t)insns[pc + (o)] | ((uint32_t)insns[pc + (o) + 1] << 16)))

#define EXPORT_PC() (fr->pc = pc)

static inline uint32_t invoke_width(uint16_t inst) {
    uint8_t op = inst & 0xff;
    return (op == 0xfa || op == 0xfb) ? 4 : 3;
}

/* Throws StackOverflowError using a reserved stack zone so the error itself can be built. */
static void throw_soe(VMThread *t) {
    if (t->handling_soe) {
        sa_fatal("stack overflow while constructing StackOverflowError");
    }
    t->handling_soe = true;
    vm_throw_new(t, "Ljava/lang/StackOverflowError;", "stack size %d frames", t->depth);
    t->handling_soe = false;
}

static void throw_aioobe(VMThread *t, int32_t len, int32_t idx) {
    vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;", "length=%d; index=%d", len, idx);
}

static void throw_cce(VMThread *t, Class *from, Class *to) {
    vm_throw_new(t, "Ljava/lang/ClassCastException;", "%s cannot be cast to %s", from->name, to->name);
}

/* Executes a native (or intrinsic) method with arguments in args. */
static void call_native(VMThread *t, Method *m, uint64_t *args) {
    JValue ret;
    ret.raw = 0;
    Object *sync = NULL;
    if (m->access & ACC_SYNCHRONIZED) {
        sync = (m->access & ACC_STATIC) ? vm_class_mirror(t, m->clazz) : (Object *)(uintptr_t)args[0];
        vm_monitor_enter(t, sync);
        if (t->exception) return;
    }
    Method *saved_native = t->cur_native;
    t->cur_native = m;
    if (m->native) {
        m->native(t, args, &ret);
    } else if (m->jni_fn || ((m->access & ACC_NATIVE) && vm_jni_bind(t, m))) {
        ret = vm_jni_call(t, m, args);
    } else if (m->access & ACC_ABSTRACT) {
        char buf[512];
        vm_method_pretty(m, buf, sizeof buf);
        vm_throw_new(t, "Ljava/lang/AbstractMethodError;", "%s", buf);
    } else if (!t->exception) {
        char buf[512];
        vm_method_pretty(m, buf, sizeof buf);
        LOGE("no implementation for native method %s", buf);
        vm_throw_new(t, "Ljava/lang/UnsatisfiedLinkError;", "No implementation found for %s", buf);
    }
    t->cur_native = saved_native;
    if (sync) {
        Object *exc = t->exception;
        t->exception = NULL;
        vm_monitor_exit(t, sync);
        if (exc) t->exception = exc;
    }
    t->retval = ret;
}

Method *vm_current_native(VMThread *t) { return t->cur_native; }

JValue vm_invoke(VMThread *t, Method *m, uint64_t *args) {
    if (m->native || !m->has_code) {
        if (!m->native && !(m->access & ACC_NATIVE) && !(m->access & ACC_ABSTRACT) && !m->has_code) {
            char buf[512];
            vm_method_pretty(m, buf, sizeof buf);
            vm_throw_new(t, "Ljava/lang/AbstractMethodError;", "%s", buf);
            JValue z;
            z.raw = 0;
            return z;
        }
        /* keep args alive / stable across the call */
        uint64_t *saved_top = t->rstack_top;
        uint64_t *copy = t->rstack_top;
        if (m->arg_slots && args) {
            if (t->rstack_top + m->arg_slots > t->rstack_end) {
                vm_throw_new(t, "Ljava/lang/StackOverflowError;", NULL);
                JValue z;
                z.raw = 0;
                return z;
            }
            memcpy(copy, args, m->arg_slots * sizeof(uint64_t));
            t->rstack_top += m->arg_slots;
        }
        call_native(t, m, copy);
        t->rstack_top = saved_top;
        return t->retval;
    }
    return vm_interpret(t, m, args);
}

#define SOE_RESERVE_SLOTS 16384
#define SOE_RESERVE_DEPTH 200

static Frame *push_frame(VMThread *t, Method *m) {
    const size_t hdr = (sizeof(Frame) + 7) / 8;
    uint64_t *base = t->rstack_top;
    size_t need = hdr + m->code.registers_size;
    uint64_t *limit = t->handling_soe ? t->rstack_end : t->rstack_end - SOE_RESERVE_SLOTS;
    int max_depth = t->handling_soe ? VM_MAX_DEPTH + SOE_RESERVE_DEPTH : VM_MAX_DEPTH;
    if (base + need > limit || t->depth >= max_depth) return NULL;
    Frame *f = (Frame *)base;
    f->regs = base + hdr;
    memset(f->regs, 0, m->code.registers_size * sizeof(uint64_t));
    f->method = m;
    f->prev = t->frame;
    f->pc = 0;
    f->caught = NULL;
    f->sync = NULL;
    f->saved_top = base;
    f->entry = false;
    t->rstack_top = base + need;
    t->frame = f;
    t->depth++;
    return f;
}

static void pop_frame(VMThread *t, Frame *f) {
    t->rstack_top = f->saved_top;
    t->frame = f->prev;
    t->depth--;
}

/* Finds a catch handler in the frame for the pending exception. Returns handler pc or -1. */
static int64_t find_handler(VMThread *t, Frame *fr, Object *exc) {
    Method *m = fr->method;
    const uint8_t *h = dex_find_try_handlers(&m->code, fr->pc);
    if (!h) return -1;
    int32_t size = dex_sleb128(&h);
    int32_t n = size < 0 ? -size : size;
    for (int32_t i = 0; i < n; i++) {
        uint32_t type_idx = dex_uleb128(&h);
        uint32_t addr = dex_uleb128(&h);
        Class *c = m->dex->resolved_types[type_idx];
        if (!c) {
            c = vm_find_class_noexc(t, dex_type_desc(m->dex, type_idx));
            if (c) m->dex->resolved_types[type_idx] = c;
        }
        if (c && vm_instance_of(exc, c)) return addr;
    }
    if (size <= 0) return dex_uleb128(&h);
    return -1;
}

JValue vm_interpret(VMThread *t, Method *entry_m, uint64_t *args) {
    JValue zero;
    zero.raw = 0;
    Frame *fr = push_frame(t, entry_m);
    if (!fr) {
        throw_soe(t);
        return zero;
    }
    fr->entry = true;
    {
        uint16_t ins = entry_m->code.ins_size;
        if (ins && args) memcpy(fr->regs + entry_m->code.registers_size - ins, args, ins * sizeof(uint64_t));
    }

    Method *m;
    const uint16_t *insns;
    uint64_t *regs;
    uint32_t pc;
    uint64_t argbuf[256];

#define LOAD_FRAME()          \
    do {                      \
        m = fr->method;       \
        insns = m->code.insns; \
        regs = fr->regs;      \
    } while (0)

    LOAD_FRAME();
    pc = 0;

    /* synchronized entry */
    if (m->access & ACC_SYNCHRONIZED) {
        Object *so = (m->access & ACC_STATIC) ? vm_class_mirror(t, m->clazz)
                                               : RL(m->code.registers_size - m->code.ins_size);
        vm_monitor_enter(t, so);
        fr->sync = so;
        if (t->exception) goto handle_exception;
    }

#define THROW()                   \
    do {                          \
        EXPORT_PC();              \
        goto handle_exception;    \
    } while (0)
#define CHECK_EXC()                    \
    do {                               \
        if (SA_UNLIKELY(t->exception)) \
            goto handle_exception;     \
    } while (0)
#define NULL_CHECK(o)                  \
    do {                               \
        if (SA_UNLIKELY(!(o))) {       \
            EXPORT_PC();               \
            vm_throw_npe(t, NULL);     \
            goto handle_exception;     \
        }                              \
    } while (0)
#define BRANCH(off)                                          \
    do {                                                     \
        int32_t _o = (off);                                  \
        if (_o <= 0 && SA_UNLIKELY(g_vm.safepoint_requested)) { \
            EXPORT_PC();                                     \
            vm_safepoint(t);                                 \
        }                                                    \
        pc += (uint32_t)_o;                                  \
        goto dispatch;                                       \
    } while (0)

    for (;;) {
    dispatch:;
        uint16_t inst = insns[pc];
        if (SA_UNLIKELY(g_vm.trace)) {
            char buf[256];
            vm_method_pretty(m, buf, sizeof buf);
            LOGD("%*s%s @%04x op=%02x", t->depth, "", buf, pc, inst & 0xff);
        }
        switch (inst & 0xff) {
        case 0x00: /* nop (or payload pseudo-instruction, never executed) */
            pc += 1;
            continue;
        case 0x01: /* move vA, vB */
        case 0x04: /* move-wide */
        case 0x07: /* move-object */
            regs[A4] = regs[B4];
            pc += 1;
            continue;
        case 0x02:
        case 0x05:
        case 0x08: /* move/from16 vAA, vBBBB */
            regs[AA] = regs[W1];
            pc += 2;
            continue;
        case 0x03:
        case 0x06:
        case 0x09: /* move/16 vAAAA, vBBBB */
            regs[W1] = regs[W2];
            pc += 3;
            continue;
        case 0x0a: /* move-result */
            SETI(AA, t->retval.i);
            pc += 1;
            continue;
        case 0x0b: /* move-result-wide */
            SETJ(AA, t->retval.j);
            pc += 1;
            continue;
        case 0x0c: /* move-result-object */
            SETL(AA, t->retval.l);
            pc += 1;
            continue;
        case 0x0d: /* move-exception */
            SETL(AA, fr->caught);
            fr->caught = NULL;
            pc += 1;
            continue;
        case 0x0e: /* return-void */
        case 0x73:
            t->retval.raw = 0;
            goto do_return;
        case 0x0f: /* return */
            t->retval.raw = RU(AA);
            goto do_return;
        case 0x10: /* return-wide */
        case 0x11: /* return-object */
            t->retval.raw = regs[AA];
            goto do_return;
        case 0x12: /* const/4 vA, #+B */
            SETI(A4, ((int32_t)(inst << 16)) >> 28);
            pc += 1;
            continue;
        case 0x13: /* const/16 */
            SETI(AA, S16(W1));
            pc += 2;
            continue;
        case 0x14: /* const */
            SETI(AA, I32AT(1));
            pc += 3;
            continue;
        case 0x15: /* const/high16 */
            SETI(AA, (int32_t)((uint32_t)W1 << 16));
            pc += 2;
            continue;
        case 0x16: /* const-wide/16 */
            SETJ(AA, (int64_t)S16(W1));
            pc += 2;
            continue;
        case 0x17: /* const-wide/32 */
            SETJ(AA, (int64_t)I32AT(1));
            pc += 3;
            continue;
        case 0x18: /* const-wide */
            SETJ(AA, (int64_t)((uint64_t)insns[pc + 1] | ((uint64_t)insns[pc + 2] << 16) |
                               ((uint64_t)insns[pc + 3] << 32) | ((uint64_t)insns[pc + 4] << 48)));
            pc += 5;
            continue;
        case 0x19: /* const-wide/high16 */
            SETJ(AA, (int64_t)((uint64_t)W1 << 48));
            pc += 2;
            continue;
        case 0x1a: /* const-string */
        case 0x1b: { /* const-string/jumbo */
            uint32_t idx = (inst & 0xff) == 0x1a ? W1 : (uint32_t)I32AT(1);
            Object *s = m->dex->resolved_strings[idx];
            if (!s) {
                EXPORT_PC();
                s = vm_resolve_string(t, m->dex, idx);
                if (!s) goto handle_exception;
            }
            SETL(AA, s);
            pc += (inst & 0xff) == 0x1a ? 2 : 3;
            continue;
        }
        case 0x1c: { /* const-class */
            EXPORT_PC();
            Class *c = vm_resolve_type(t, m->dex, W1);
            if (!c) goto handle_exception;
            SETL(AA, vm_class_mirror(t, c));
            pc += 2;
            continue;
        }
        case 0x1d: /* monitor-enter */
            NULL_CHECK(RL(AA));
            EXPORT_PC();
            vm_monitor_enter(t, RL(AA));
            CHECK_EXC();
            pc += 1;
            continue;
        case 0x1e: /* monitor-exit */
            EXPORT_PC();
            vm_monitor_exit(t, RL(AA));
            CHECK_EXC();
            pc += 1;
            continue;
        case 0x1f: { /* check-cast */
            Object *o = RL(AA);
            if (o) {
                Class *c = m->dex->resolved_types[W1];
                if (!c) {
                    EXPORT_PC();
                    c = vm_resolve_type(t, m->dex, W1);
                    if (!c) goto handle_exception;
                }
                if (!vm_is_assignable(c, o->clazz)) {
                    EXPORT_PC();
                    throw_cce(t, o->clazz, c);
                    goto handle_exception;
                }
            }
            pc += 2;
            continue;
        }
        case 0x20: { /* instance-of vA, vB, type */
            Object *o = RL(B4);
            int32_t r = 0;
            if (o) {
                Class *c = m->dex->resolved_types[W1];
                if (!c) {
                    EXPORT_PC();
                    c = vm_resolve_type(t, m->dex, W1);
                    if (!c) goto handle_exception;
                }
                r = vm_is_assignable(c, o->clazz);
            }
            SETI(A4, r);
            pc += 2;
            continue;
        }
        case 0x21: { /* array-length */
            ArrayObject *a = (ArrayObject *)RL(B4);
            NULL_CHECK(a);
            SETI(A4, a->length);
            pc += 1;
            continue;
        }
        case 0x22: { /* new-instance */
            EXPORT_PC();
            Class *c = vm_resolve_type(t, m->dex, W1);
            if (!c) goto handle_exception;
            if (c->access & (ACC_ABSTRACT | ACC_INTERFACE)) {
                vm_throw_new(t, "Ljava/lang/InstantiationError;", "%s", c->name);
                goto handle_exception;
            }
            if (!vm_init_class(t, c)) goto handle_exception;
            Object *o = vm_alloc_object(t, c);
            if (!o) goto handle_exception;
            SETL(AA, o);
            pc += 2;
            continue;
        }
        case 0x23: { /* new-array vA, vB, type */
            EXPORT_PC();
            Class *c = vm_resolve_type(t, m->dex, W1);
            if (!c) goto handle_exception;
            ArrayObject *a = vm_alloc_array(t, c, RI(B4));
            if (!a) goto handle_exception;
            SETL(A4, a);
            pc += 2;
            continue;
        }
        case 0x24:   /* filled-new-array {vC..vG}, type */
        case 0x25: { /* filled-new-array/range */
            EXPORT_PC();
            bool range = (inst & 0xff) == 0x25;
            Class *c = vm_resolve_type(t, m->dex, W1);
            if (!c) goto handle_exception;
            uint32_t count = range ? AA : B4;
            ArrayObject *a = vm_alloc_array(t, c, (int32_t)count);
            if (!a) goto handle_exception;
            for (uint32_t i = 0; i < count; i++) {
                uint32_t r;
                if (range) r = W2 + i;
                else r = i < 4 ? (W2 >> (i * 4)) & 0xf : A4;
                if (c->component->prim) {
                    if (c->elem_size == 4) ARRAY_DATA(a, int32_t)[i] = RI(r);
                    else ARRAY_DATA(a, int64_t)[i] = RJ(r); /* not generated by compilers */
                } else {
                    ARRAY_DATA(a, Object *)[i] = RL(r);
                }
            }
            t->retval.l = (Object *)a;
            pc += 3;
            continue;
        }
        case 0x26: { /* fill-array-data vAA, +BBBBBBBB */
            ArrayObject *a = (ArrayObject *)RL(AA);
            NULL_CHECK(a);
            const uint16_t *p = insns + pc + I32AT(1);
            uint16_t width = p[1];
            uint32_t size = (uint32_t)p[2] | ((uint32_t)p[3] << 16);
            if ((int64_t)size > a->length) {
                EXPORT_PC();
                throw_aioobe(t, a->length, (int32_t)size - 1);
                goto handle_exception;
            }
            memcpy(a->data, p + 4, (size_t)width * size);
            pc += 3;
            continue;
        }
        case 0x27: { /* throw */
            Object *e = RL(AA);
            EXPORT_PC();
            if (!e) vm_throw_npe(t, "throw with null exception");
            else t->exception = e;
            goto handle_exception;
        }
        case 0x28: /* goto */
            BRANCH((int8_t)(inst >> 8));
        case 0x29: /* goto/16 */
            BRANCH(S16(W1));
        case 0x2a: /* goto/32 */
            BRANCH(I32AT(1));
        case 0x2b: { /* packed-switch */
            const uint16_t *p = insns + pc + I32AT(1);
            uint16_t size = p[1];
            int32_t first = (int32_t)((uint32_t)p[2] | ((uint32_t)p[3] << 16));
            int32_t v = RI(AA);
            int64_t idx = (int64_t)v - first;
            if (idx >= 0 && idx < size) {
                const uint16_t *tp = p + 4 + idx * 2;
                BRANCH((int32_t)((uint32_t)tp[0] | ((uint32_t)tp[1] << 16)));
            }
            pc += 3;
            continue;
        }
        case 0x2c: { /* sparse-switch */
            const uint16_t *p = insns + pc + I32AT(1);
            uint16_t size = p[1];
            const uint16_t *keys = p + 2;
            const uint16_t *targets = keys + size * 2;
            int32_t v = RI(AA);
            int lo = 0, hi = size - 1;
            while (lo <= hi) {
                int mid = (lo + hi) / 2;
                int32_t k = (int32_t)((uint32_t)keys[mid * 2] | ((uint32_t)keys[mid * 2 + 1] << 16));
                if (v < k) hi = mid - 1;
                else if (v > k) lo = mid + 1;
                else BRANCH((int32_t)((uint32_t)targets[mid * 2] | ((uint32_t)targets[mid * 2 + 1] << 16)));
            }
            pc += 3;
            continue;
        }
        case 0x2d: /* cmpl-float */
        case 0x2e: { /* cmpg-float */
            float a = RF(W1 & 0xff), b = RF(W1 >> 8);
            int32_t r;
            if (a < b) r = -1;
            else if (a > b) r = 1;
            else if (a == b) r = 0;
            else r = (inst & 0xff) == 0x2e ? 1 : -1;
            SETI(AA, r);
            pc += 2;
            continue;
        }
        case 0x2f:
        case 0x30: {
            double a = RD(W1 & 0xff), b = RD(W1 >> 8);
            int32_t r;
            if (a < b) r = -1;
            else if (a > b) r = 1;
            else if (a == b) r = 0;
            else r = (inst & 0xff) == 0x30 ? 1 : -1;
            SETI(AA, r);
            pc += 2;
            continue;
        }
        case 0x31: { /* cmp-long */
            int64_t a = RJ(W1 & 0xff), b = RJ(W1 >> 8);
            SETI(AA, a < b ? -1 : (a > b ? 1 : 0));
            pc += 2;
            continue;
        }
        case 0x32: /* if-eq */
            if (regs[A4] == regs[B4]) BRANCH(S16(W1));
            pc += 2;
            continue;
        case 0x33: /* if-ne */
            if (regs[A4] != regs[B4]) BRANCH(S16(W1));
            pc += 2;
            continue;
        case 0x34:
            if (RI(A4) < RI(B4)) BRANCH(S16(W1));
            pc += 2;
            continue;
        case 0x35:
            if (RI(A4) >= RI(B4)) BRANCH(S16(W1));
            pc += 2;
            continue;
        case 0x36:
            if (RI(A4) > RI(B4)) BRANCH(S16(W1));
            pc += 2;
            continue;
        case 0x37:
            if (RI(A4) <= RI(B4)) BRANCH(S16(W1));
            pc += 2;
            continue;
        case 0x38: /* if-eqz */
            if (regs[AA] == 0) BRANCH(S16(W1));
            pc += 2;
            continue;
        case 0x39: /* if-nez */
            if (regs[AA] != 0) BRANCH(S16(W1));
            pc += 2;
            continue;
        case 0x3a:
            if (RI(AA) < 0) BRANCH(S16(W1));
            pc += 2;
            continue;
        case 0x3b:
            if (RI(AA) >= 0) BRANCH(S16(W1));
            pc += 2;
            continue;
        case 0x3c:
            if (RI(AA) > 0) BRANCH(S16(W1));
            pc += 2;
            continue;
        case 0x3d:
            if (RI(AA) <= 0) BRANCH(S16(W1));
            pc += 2;
            continue;

        /* ---- arrays ---- */
#define ARRAY_PRE()                                             \
    ArrayObject *a = (ArrayObject *)RL(W1 & 0xff);              \
    int32_t idx = RI(W1 >> 8);                                  \
    NULL_CHECK(a);                                              \
    if (SA_UNLIKELY((uint32_t)idx >= (uint32_t)a->length)) {    \
        EXPORT_PC();                                            \
        throw_aioobe(t, a->length, idx);                        \
        goto handle_exception;                                  \
    }
        case 0x44: { ARRAY_PRE(); SETI(AA, ARRAY_DATA(a, int32_t)[idx]); pc += 2; continue; }
        case 0x45: { ARRAY_PRE(); SETJ(AA, ARRAY_DATA(a, int64_t)[idx]); pc += 2; continue; }
        case 0x46: { ARRAY_PRE(); SETL(AA, ARRAY_DATA(a, Object *)[idx]); pc += 2; continue; }
        case 0x47: { ARRAY_PRE(); SETI(AA, ARRAY_DATA(a, uint8_t)[idx]); pc += 2; continue; }
        case 0x48: { ARRAY_PRE(); SETI(AA, ARRAY_DATA(a, int8_t)[idx]); pc += 2; continue; }
        case 0x49: { ARRAY_PRE(); SETI(AA, ARRAY_DATA(a, uint16_t)[idx]); pc += 2; continue; }
        case 0x4a: { ARRAY_PRE(); SETI(AA, ARRAY_DATA(a, int16_t)[idx]); pc += 2; continue; }
        case 0x4b: { ARRAY_PRE(); ARRAY_DATA(a, int32_t)[idx] = RI(AA); pc += 2; continue; }
        case 0x4c: { ARRAY_PRE(); ARRAY_DATA(a, int64_t)[idx] = RJ(AA); pc += 2; continue; }
        case 0x4d: {
            ARRAY_PRE();
            Object *v = RL(AA);
            if (v && !vm_is_assignable(a->obj.clazz->component, v->clazz)) {
                EXPORT_PC();
                vm_throw_new(t, "Ljava/lang/ArrayStoreException;", "%s cannot be stored in an array of type %s",
                             v->clazz->name, a->obj.clazz->name);
                goto handle_exception;
            }
            ARRAY_DATA(a, Object *)[idx] = v;
            pc += 2;
            continue;
        }
        case 0x4e: { ARRAY_PRE(); ARRAY_DATA(a, uint8_t)[idx] = (uint8_t)RI(AA); pc += 2; continue; }
        case 0x4f: { ARRAY_PRE(); ARRAY_DATA(a, int8_t)[idx] = (int8_t)RI(AA); pc += 2; continue; }
        case 0x50: { ARRAY_PRE(); ARRAY_DATA(a, uint16_t)[idx] = (uint16_t)RI(AA); pc += 2; continue; }
        case 0x51: { ARRAY_PRE(); ARRAY_DATA(a, int16_t)[idx] = (int16_t)RI(AA); pc += 2; continue; }

        /* ---- instance fields ---- */
#define IFIELD_PRE()                                               \
    Field *f = (Field *)m->dex->resolved_fields[W1];               \
    if (SA_UNLIKELY(!f)) {                                         \
        EXPORT_PC();                                               \
        f = vm_resolve_field(t, m->dex, W1, false);                \
        if (!f) goto handle_exception;                             \
    }                                                              \
    Object *o = RL(B4);                                            \
    NULL_CHECK(o);                                                 \
    uint8_t *fp = (uint8_t *)o + f->offset;
        case 0x52: { IFIELD_PRE(); SETI(A4, *(int32_t *)fp); pc += 2; continue; }
        case 0x53: { IFIELD_PRE(); SETJ(A4, *(int64_t *)fp); pc += 2; continue; }
        case 0x54: { IFIELD_PRE(); SETL(A4, *(Object **)fp); pc += 2; continue; }
        case 0x55: { IFIELD_PRE(); SETI(A4, *(uint8_t *)fp); pc += 2; continue; }
        case 0x56: { IFIELD_PRE(); SETI(A4, *(int8_t *)fp); pc += 2; continue; }
        case 0x57: { IFIELD_PRE(); SETI(A4, *(uint16_t *)fp); pc += 2; continue; }
        case 0x58: { IFIELD_PRE(); SETI(A4, *(int16_t *)fp); pc += 2; continue; }
        case 0x59: { IFIELD_PRE(); *(int32_t *)fp = RI(A4); pc += 2; continue; }
        case 0x5a: { IFIELD_PRE(); *(int64_t *)fp = RJ(A4); pc += 2; continue; }
        case 0x5b: { IFIELD_PRE(); *(Object **)fp = RL(A4); pc += 2; continue; }
        case 0x5c: { IFIELD_PRE(); *(uint8_t *)fp = (uint8_t)RI(A4); pc += 2; continue; }
        case 0x5d: { IFIELD_PRE(); *(int8_t *)fp = (int8_t)RI(A4); pc += 2; continue; }
        case 0x5e: { IFIELD_PRE(); *(uint16_t *)fp = (uint16_t)RI(A4); pc += 2; continue; }
        case 0x5f: { IFIELD_PRE(); *(int16_t *)fp = (int16_t)RI(A4); pc += 2; continue; }

        /* ---- static fields ---- */
#define SFIELD_PRE()                                               \
    Field *f = (Field *)m->dex->resolved_fields[W1];               \
    if (SA_UNLIKELY(!f)) {                                         \
        EXPORT_PC();                                               \
        f = vm_resolve_field(t, m->dex, W1, true);                 \
        if (!f) goto handle_exception;                             \
    }                                                              \
    if (SA_UNLIKELY(f->clazz->state != CLASS_INITIALIZED)) {       \
        EXPORT_PC();                                               \
        if (!vm_init_class(t, f->clazz)) goto handle_exception;    \
    }                                                              \
    uint8_t *fp = f->clazz->static_data + f->offset;
        case 0x60: { SFIELD_PRE(); SETI(AA, *(int32_t *)fp); pc += 2; continue; }
        case 0x61: { SFIELD_PRE(); SETJ(AA, *(int64_t *)fp); pc += 2; continue; }
        case 0x62: { SFIELD_PRE(); SETL(AA, *(Object **)fp); pc += 2; continue; }
        case 0x63: { SFIELD_PRE(); SETI(AA, *(uint8_t *)fp); pc += 2; continue; }
        case 0x64: { SFIELD_PRE(); SETI(AA, *(int8_t *)fp); pc += 2; continue; }
        case 0x65: { SFIELD_PRE(); SETI(AA, *(uint16_t *)fp); pc += 2; continue; }
        case 0x66: { SFIELD_PRE(); SETI(AA, *(int16_t *)fp); pc += 2; continue; }
        case 0x67: { SFIELD_PRE(); *(int32_t *)fp = RI(AA); pc += 2; continue; }
        case 0x68: { SFIELD_PRE(); *(int64_t *)fp = RJ(AA); pc += 2; continue; }
        case 0x69: { SFIELD_PRE(); *(Object **)fp = RL(AA); pc += 2; continue; }
        case 0x6a: { SFIELD_PRE(); *(uint8_t *)fp = (uint8_t)RI(AA); pc += 2; continue; }
        case 0x6b: { SFIELD_PRE(); *(int8_t *)fp = (int8_t)RI(AA); pc += 2; continue; }
        case 0x6c: { SFIELD_PRE(); *(uint16_t *)fp = (uint16_t)RI(AA); pc += 2; continue; }
        case 0x6d: { SFIELD_PRE(); *(int16_t *)fp = (int16_t)RI(AA); pc += 2; continue; }

        /* ---- invocations ---- */
        case 0x6e: case 0x6f: case 0x70: case 0x71: case 0x72:
        case 0x74: case 0x75: case 0x76: case 0x77: case 0x78: {
            uint8_t op = inst & 0xff;
            bool range = op >= 0x74;
            int kind = range ? op - 0x74 : op - 0x6e; /* 0 virtual 1 super 2 direct 3 static 4 interface */
            uint32_t midx = W1;
            uint32_t count;
            EXPORT_PC();
            if (range) {
                count = AA;
                uint32_t first = W2;
                for (uint32_t i = 0; i < count; i++) argbuf[i] = regs[first + i];
            } else {
                count = B4;
                uint16_t w2 = W2;
                if (count > 0) argbuf[0] = regs[w2 & 0xf];
                if (count > 1) argbuf[1] = regs[(w2 >> 4) & 0xf];
                if (count > 2) argbuf[2] = regs[(w2 >> 8) & 0xf];
                if (count > 3) argbuf[3] = regs[(w2 >> 12) & 0xf];
                if (count > 4) argbuf[4] = regs[A4];
            }
            Method *callee = (Method *)m->dex->resolved_methods[midx];
            if (SA_UNLIKELY(!callee)) {
                callee = vm_resolve_method(t, m->dex, midx, kind == 3, kind == 4);
                if (!callee) goto handle_exception;
            }
            if (g_vm.safepoint_requested) {
                vm_safepoint(t);
            }
            switch (kind) {
            case 0: { /* virtual */
                Object *recv = (Object *)(uintptr_t)argbuf[0];
                NULL_CHECK(recv);
                if (SA_LIKELY(callee->vtable_index >= 0 && !(callee->clazz->access & ACC_INTERFACE))) {
                    Class *rc = recv->clazz;
                    if (SA_LIKELY((uint32_t)callee->vtable_index < rc->vtable_len)) callee = rc->vtable[callee->vtable_index];
                } else {
                    Method *impl = vm_find_interface_impl(recv->clazz, callee);
                    if (impl) callee = impl;
                }
                break;
            }
            case 1: { /* super */
                if (!(callee->clazz->access & ACC_INTERFACE) && callee->vtable_index >= 0) {
                    Class *sup = m->clazz->super;
                    if (sup && (uint32_t)callee->vtable_index < sup->vtable_len) callee = sup->vtable[callee->vtable_index];
                }
                break;
            }
            case 2: /* direct */
                NULL_CHECK((Object *)(uintptr_t)argbuf[0]);
                break;
            case 3: /* static */
                if (SA_UNLIKELY(callee->clazz->state != CLASS_INITIALIZED)) {
                    if (!vm_init_class(t, callee->clazz)) goto handle_exception;
                }
                break;
            case 4: { /* interface */
                Object *recv = (Object *)(uintptr_t)argbuf[0];
                NULL_CHECK(recv);
                Method *impl = vm_find_interface_impl(recv->clazz, callee);
                if (!impl) {
                    if (callee->stub || !(callee->access & ACC_ABSTRACT)) {
                        impl = callee;
                    } else {
                        char buf[512];
                        vm_method_pretty(callee, buf, sizeof buf);
                        vm_throw_new(t, "Ljava/lang/AbstractMethodError;", "%s not implemented by %s", buf,
                                     recv->clazz->name);
                        goto handle_exception;
                    }
                }
                callee = impl;
                break;
            }
            }
            if (callee->native || !callee->has_code) {
                uint64_t *saved_top = t->rstack_top;
                if (t->rstack_top + count > t->rstack_end) {
                    vm_throw_new(t, "Ljava/lang/StackOverflowError;", NULL);
                    goto handle_exception;
                }
                uint64_t *nargs = t->rstack_top;
                memcpy(nargs, argbuf, count * sizeof(uint64_t));
                t->rstack_top += count;
                call_native(t, callee, nargs);
                t->rstack_top = saved_top;
                CHECK_EXC();
                pc += 3;
                continue;
            }
            Frame *nf = push_frame(t, callee);
            if (!nf) {
                throw_soe(t);
                goto handle_exception;
            }
            {
                uint16_t ins = callee->code.ins_size;
                uint32_t n = count < ins ? count : ins;
                memcpy(nf->regs + callee->code.registers_size - ins, argbuf, n * sizeof(uint64_t));
            }
            fr = nf;
            LOAD_FRAME();
            pc = 0;
            if (m->access & ACC_SYNCHRONIZED) {
                Object *so = (m->access & ACC_STATIC) ? vm_class_mirror(t, m->clazz)
                                                       : RL(m->code.registers_size - m->code.ins_size);
                vm_monitor_enter(t, so);
                fr->sync = so;
                if (t->exception) goto handle_exception;
            }
            continue;
        }

        /* ---- unary ops ---- */
        case 0x7b: SETI(A4, (int32_t)(0u - RU(B4))); pc += 1; continue;
        case 0x7c: SETI(A4, ~RI(B4)); pc += 1; continue;
        case 0x7d: SETJ(A4, (int64_t)(0ull - (uint64_t)RJ(B4))); pc += 1; continue;
        case 0x7e: SETJ(A4, ~RJ(B4)); pc += 1; continue;
        case 0x7f: SETF(A4, -RF(B4)); pc += 1; continue;
        case 0x80: SETD(A4, -RD(B4)); pc += 1; continue;
        case 0x81: SETJ(A4, (int64_t)RI(B4)); pc += 1; continue;
        case 0x82: SETF(A4, (float)RI(B4)); pc += 1; continue;
        case 0x83: SETD(A4, (double)RI(B4)); pc += 1; continue;
        case 0x84: SETI(A4, (int32_t)RJ(B4)); pc += 1; continue;
        case 0x85: SETF(A4, (float)RJ(B4)); pc += 1; continue;
        case 0x86: SETD(A4, (double)RJ(B4)); pc += 1; continue;
        case 0x87: SETI(A4, f2i(RF(B4))); pc += 1; continue;
        case 0x88: SETJ(A4, f2l(RF(B4))); pc += 1; continue;
        case 0x89: SETD(A4, (double)RF(B4)); pc += 1; continue;
        case 0x8a: SETI(A4, d2i(RD(B4))); pc += 1; continue;
        case 0x8b: SETJ(A4, f2l(RD(B4))); pc += 1; continue;
        case 0x8c: SETF(A4, (float)RD(B4)); pc += 1; continue;
        case 0x8d: SETI(A4, (int8_t)RI(B4)); pc += 1; continue;
        case 0x8e: SETI(A4, (uint16_t)RI(B4)); pc += 1; continue;
        case 0x8f: SETI(A4, (int16_t)RI(B4)); pc += 1; continue;

        /* ---- binary ops ---- */
#define BIN_OPERANDS_23X() uint32_t rd = AA, rb = W1 & 0xff, rc = W1 >> 8; pc += 2
#define BIN_OPERANDS_2ADDR() uint32_t rd = A4, rb = A4, rc = B4; pc += 1
#define INT_BINOPS(BASE, OPERANDS)                                                         \
        case BASE + 0: { OPERANDS; SETI(rd, (int32_t)(RU(rb) + RU(rc))); continue; }        \
        case BASE + 1: { OPERANDS; SETI(rd, (int32_t)(RU(rb) - RU(rc))); continue; }        \
        case BASE + 2: { OPERANDS; SETI(rd, (int32_t)(RU(rb) * RU(rc))); continue; }        \
        case BASE + 3: case BASE + 4: {                                                     \
            uint32_t _op = inst & 0xff;                                                     \
            OPERANDS;                                                                       \
            int32_t x = RI(rb), y = RI(rc);                                                 \
            if (y == 0) {                                                                   \
                vm_throw_new(t, "Ljava/lang/ArithmeticException;", "/ by zero");       \
                THROW();                                                                    \
            }                                                                               \
            if (_op == BASE + 3) SETI(rd, (x == INT32_MIN && y == -1) ? x : x / y);         \
            else SETI(rd, (x == INT32_MIN && y == -1) ? 0 : x % y);                         \
            continue;                                                                       \
        }                                                                                   \
        case BASE + 5: { OPERANDS; SETI(rd, RI(rb) & RI(rc)); continue; }                    \
        case BASE + 6: { OPERANDS; SETI(rd, RI(rb) | RI(rc)); continue; }                    \
        case BASE + 7: { OPERANDS; SETI(rd, RI(rb) ^ RI(rc)); continue; }                    \
        case BASE + 8: { OPERANDS; SETI(rd, (int32_t)(RU(rb) << (RU(rc) & 31))); continue; } \
        case BASE + 9: { OPERANDS; SETI(rd, RI(rb) >> (RU(rc) & 31)); continue; }            \
        case BASE + 10: { OPERANDS; SETI(rd, (int32_t)(RU(rb) >> (RU(rc) & 31))); continue; }
#define LONG_BINOPS(BASE, OPERANDS)                                                                         \
        case BASE + 0: { OPERANDS; SETJ(rd, (int64_t)((uint64_t)RJ(rb) + (uint64_t)RJ(rc))); continue; }     \
        case BASE + 1: { OPERANDS; SETJ(rd, (int64_t)((uint64_t)RJ(rb) - (uint64_t)RJ(rc))); continue; }     \
        case BASE + 2: { OPERANDS; SETJ(rd, (int64_t)((uint64_t)RJ(rb) * (uint64_t)RJ(rc))); continue; }     \
        case BASE + 3: case BASE + 4: {                                                                     \
            uint32_t _op = inst & 0xff;                                                                     \
            OPERANDS;                                                                                       \
            int64_t x = RJ(rb), y = RJ(rc);                                                                 \
            if (y == 0) {                                                                                   \
                vm_throw_new(t, "Ljava/lang/ArithmeticException;", "/ by zero");                       \
                THROW();                                                                                    \
            }                                                                                               \
            if (_op == BASE + 3) SETJ(rd, (x == INT64_MIN && y == -1) ? x : x / y);                         \
            else SETJ(rd, (x == INT64_MIN && y == -1) ? 0 : x % y);                                         \
            continue;                                                                                       \
        }                                                                                                   \
        case BASE + 5: { OPERANDS; SETJ(rd, RJ(rb) & RJ(rc)); continue; }                                    \
        case BASE + 6: { OPERANDS; SETJ(rd, RJ(rb) | RJ(rc)); continue; }                                    \
        case BASE + 7: { OPERANDS; SETJ(rd, RJ(rb) ^ RJ(rc)); continue; }                                    \
        case BASE + 8: { OPERANDS; SETJ(rd, (int64_t)((uint64_t)RJ(rb) << (RU(rc) & 63))); continue; }       \
        case BASE + 9: { OPERANDS; SETJ(rd, RJ(rb) >> (RU(rc) & 63)); continue; }                            \
        case BASE + 10: { OPERANDS; SETJ(rd, (int64_t)((uint64_t)RJ(rb) >> (RU(rc) & 63))); continue; }
#define FLOAT_BINOPS(BASE, OPERANDS)                                           \
        case BASE + 0: { OPERANDS; SETF(rd, RF(rb) + RF(rc)); continue; }       \
        case BASE + 1: { OPERANDS; SETF(rd, RF(rb) - RF(rc)); continue; }       \
        case BASE + 2: { OPERANDS; SETF(rd, RF(rb) * RF(rc)); continue; }       \
        case BASE + 3: { OPERANDS; SETF(rd, RF(rb) / RF(rc)); continue; }       \
        case BASE + 4: { OPERANDS; SETF(rd, fmodf(RF(rb), RF(rc))); continue; }
#define DOUBLE_BINOPS(BASE, OPERANDS)                                          \
        case BASE + 0: { OPERANDS; SETD(rd, RD(rb) + RD(rc)); continue; }       \
        case BASE + 1: { OPERANDS; SETD(rd, RD(rb) - RD(rc)); continue; }       \
        case BASE + 2: { OPERANDS; SETD(rd, RD(rb) * RD(rc)); continue; }       \
        case BASE + 3: { OPERANDS; SETD(rd, RD(rb) / RD(rc)); continue; }       \
        case BASE + 4: { OPERANDS; SETD(rd, fmod(RD(rb), RD(rc))); continue; }

        INT_BINOPS(0x90, BIN_OPERANDS_23X())
        LONG_BINOPS(0x9b, BIN_OPERANDS_23X())
        FLOAT_BINOPS(0xa6, BIN_OPERANDS_23X())
        DOUBLE_BINOPS(0xab, BIN_OPERANDS_23X())
        INT_BINOPS(0xb0, BIN_OPERANDS_2ADDR())
        LONG_BINOPS(0xbb, BIN_OPERANDS_2ADDR())
        FLOAT_BINOPS(0xc6, BIN_OPERANDS_2ADDR())
        DOUBLE_BINOPS(0xcb, BIN_OPERANDS_2ADDR())

        /* ---- literal ops ---- */
        case 0xd0: case 0xd1: case 0xd2: case 0xd3: case 0xd4: case 0xd5: case 0xd6: case 0xd7:
        case 0xd8: case 0xd9: case 0xda: case 0xdb: case 0xdc: case 0xdd: case 0xde: case 0xdf:
        case 0xe0: case 0xe1: case 0xe2: {
            uint8_t op = inst & 0xff;
            uint32_t rd;
            int32_t x, lit;
            int which;
            if (op <= 0xd7) {
                rd = A4;
                x = RI(B4);
                lit = S16(W1);
                which = op - 0xd0;
            } else {
                rd = AA;
                x = RI(W1 & 0xff);
                lit = (int8_t)(W1 >> 8);
                which = op - 0xd8;
            }
            pc += 2;
            switch (which) {
            case 0: SETI(rd, (int32_t)((uint32_t)x + (uint32_t)lit)); break;
            case 1: SETI(rd, (int32_t)((uint32_t)lit - (uint32_t)x)); break;
            case 2: SETI(rd, (int32_t)((uint32_t)x * (uint32_t)lit)); break;
            case 3:
            case 4:
                if (lit == 0) {
                    pc -= 2;
                    vm_throw_new(t, "Ljava/lang/ArithmeticException;", "/ by zero");
                    THROW();
                }
                if (which == 3) SETI(rd, (x == INT32_MIN && lit == -1) ? x : x / lit);
                else SETI(rd, (x == INT32_MIN && lit == -1) ? 0 : x % lit);
                break;
            case 5: SETI(rd, x & lit); break;
            case 6: SETI(rd, x | lit); break;
            case 7: SETI(rd, x ^ lit); break;
            case 8: SETI(rd, (int32_t)((uint32_t)x << (lit & 31))); break;
            case 9: SETI(rd, x >> (lit & 31)); break;
            case 10: SETI(rd, (int32_t)((uint32_t)x >> (lit & 31))); break;
            }
            continue;
        }

        case 0xfa:
        case 0xfb:
            EXPORT_PC();
            vm_throw_new(t, "Ljava/lang/UnsupportedOperationException;", "invoke-polymorphic is not supported");
            goto handle_exception;
        case 0xfc:
        case 0xfd:
            EXPORT_PC();
            vm_throw_new(t, "Ljava/lang/UnsupportedOperationException;",
                         "invoke-custom is not supported (compile with desugaring)");
            goto handle_exception;
        case 0xfe:
        case 0xff:
            EXPORT_PC();
            vm_throw_new(t, "Ljava/lang/UnsupportedOperationException;", "method handles are not supported");
            goto handle_exception;
        default: {
            EXPORT_PC();
            char buf[512];
            vm_method_pretty(m, buf, sizeof buf);
            LOGE("invalid opcode 0x%02x at %s @%u", inst & 0xff, buf, pc);
            vm_throw_new(t, "Ljava/lang/VerifyError;", "invalid opcode 0x%02x", inst & 0xff);
            goto handle_exception;
        }
        }

    do_return: {
        if (fr->sync) {
            JValue rv = t->retval;
            vm_monitor_exit(t, fr->sync);
            fr->sync = NULL;
            t->retval = rv;
            if (t->exception) {
                EXPORT_PC();
                goto handle_exception;
            }
        }
        Frame *done = fr;
        pop_frame(t, done);
        if (done->entry) return t->retval;
        fr = t->frame;
        LOAD_FRAME();
        pc = fr->pc + invoke_width(insns[fr->pc]);
        continue;
    }

    handle_exception: {
        for (;;) {
            Object *exc = t->exception;
            if (!exc) {
                /* defensive: resume if exception was cleared */
                break;
            }
            int64_t h = find_handler(t, fr, exc);
            if (h >= 0) {
                t->exception = NULL;
                fr->caught = exc;
                pc = (uint32_t)h;
                goto resume;
            }
            if (fr->sync) {
                t->exception = NULL;
                vm_monitor_exit(t, fr->sync);
                fr->sync = NULL;
                t->exception = exc;
            }
            Frame *done = fr;
            pop_frame(t, done);
            if (done->entry) return zero;
            fr = t->frame;
            LOAD_FRAME();
            /* fr->pc is the invoke instruction; search handlers there */
        }
        pc = fr->pc;
    resume:
        LOAD_FRAME();
        continue;
    }
    }
}
