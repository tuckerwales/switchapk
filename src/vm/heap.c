/*
 * Object allocation, garbage collection and strings.
 *
 * GC design: non-moving mark/sweep. Heap objects are traced precisely using
 * per-class reference offset tables. Roots on native stacks and Dalvik
 * register stacks are scanned conservatively (any word that equals the
 * address of a live object keeps it alive). Collection only happens at
 * interpreter safepoints while holding the GIL, so allocation itself never
 * moves or frees memory.
 */
#include "vm.h"

#define LOG_TAG "heap"

#define MIN_HEAP_THRESHOLD (24u * 1024u * 1024u)

typedef struct Monitor Monitor;
void vm_monitor_free_index(uint32_t idx);

/* ---- allocation ----------------------------------------------------------------- */

static Object *raw_alloc(VMThread *t, Class *c, size_t size) {
    Object *o = calloc(1, size);
    if (!o) {
        LOGW("allocation of %zu bytes failed, collecting", size);
        if (t && t->has_gil) vm_gc(t);
        o = calloc(1, size);
        if (!o) {
            if (t) vm_throw_oom(t);
            return NULL;
        }
    }
    o->clazz = c;
    sa_ptrmap_put(&g_vm.objects, o, (void *)(uintptr_t)size);
    g_vm.heap_bytes += size;
    if (g_vm.heap_bytes > g_vm.heap_threshold) g_vm.safepoint_requested = true;
    return o;
}

Object *vm_alloc_object(VMThread *t, Class *c) {
    if (c->state < CLASS_LINKED) {
        vm_throw_new(t, "Ljava/lang/InstantiationError;", "%s", c->name);
        return NULL;
    }
    return raw_alloc(t, c, c->instance_size ? c->instance_size : sizeof(Object));
}

ArrayObject *vm_alloc_array(VMThread *t, Class *ac, int32_t length) {
    if (length < 0) {
        vm_throw_new(t, "Ljava/lang/NegativeArraySizeException;", "%d", length);
        return NULL;
    }
    size_t size = sizeof(ArrayObject) + (size_t)length * ac->elem_size;
    if (size > (size_t)1 << 31) {
        vm_throw_oom(t);
        return NULL;
    }
    ArrayObject *a = (ArrayObject *)raw_alloc(t, ac, size);
    if (a) {
        a->length = length;
        a->data = a->storage;
    }
    return a;
}

ArrayObject *vm_alloc_prim_array(VMThread *t, char prim, int32_t length) {
    Class *ac;
    switch (prim) {
    case 'Z': ac = g_vm.wk.arr_Z; break;
    case 'B': ac = g_vm.wk.arr_B; break;
    case 'C': ac = g_vm.wk.arr_C; break;
    case 'S': ac = g_vm.wk.arr_S; break;
    case 'I': ac = g_vm.wk.arr_I; break;
    case 'J': ac = g_vm.wk.arr_J; break;
    case 'F': ac = g_vm.wk.arr_F; break;
    case 'D': ac = g_vm.wk.arr_D; break;
    default: return NULL;
    }
    return vm_alloc_array(t, ac, length);
}

size_t vm_object_size(Object *o) { return (size_t)(uintptr_t)sa_ptrmap_get(&g_vm.objects, o); }

Object *vm_clone(VMThread *t, Object *o) {
    size_t size = vm_object_size(o);
    Object *c = raw_alloc(t, o->clazz, size);
    if (!c) return NULL;
    memcpy((uint8_t *)c + sizeof(Object), (uint8_t *)o + sizeof(Object), size - sizeof(Object));
    if (o->clazz->is_array) {
        ArrayObject *src = (ArrayObject *)o, *dst = (ArrayObject *)c;
        dst->data = dst->storage;
        if (src->data != src->storage) {
            /* cloning an array that wraps external memory: copy into a fresh inline array */
            size_t bytes = (size_t)src->length * o->clazz->elem_size;
            ArrayObject *n = vm_alloc_array(t, o->clazz, src->length);
            if (!n) return NULL;
            memcpy(n->data, src->data, bytes);
            return (Object *)n;
        }
    }
    return c;
}

bool vm_is_object(const void *p) {
    if (!p || ((uintptr_t)p & 7)) return false;
    return sa_ptrmap_has(&g_vm.objects, p);
}

void vm_add_root(Object **slot) { sa_vec_push(&g_vm.explicit_roots, slot); }

void vm_remove_root(Object **slot) {
    for (size_t i = 0; i < g_vm.explicit_roots.len; i++) {
        if (g_vm.explicit_roots.items[i] == slot) {
            g_vm.explicit_roots.items[i] = g_vm.explicit_roots.items[--g_vm.explicit_roots.len];
            return;
        }
    }
}

/* ---- marking -------------------------------------------------------------------------- */

static SaVec g_mark_stack;
static SaVec g_found_refs; /* java.lang.ref.Reference instances */

#define GC_MARK 1u

static inline void mark(Object *o) {
    if (!o || (o->gcflags & GC_MARK)) return;
    o->gcflags |= GC_MARK;
    sa_vec_push(&g_mark_stack, o);
}

static inline void mark_conservative(uintptr_t w) {
    if (w && !(w & 7) && sa_ptrmap_has(&g_vm.objects, (void *)w)) mark((Object *)w);
}

static void scan_range(const void *lo, const void *hi) {
    uintptr_t a = SA_ALIGN_UP((uintptr_t)lo, sizeof(uintptr_t));
    for (; a + sizeof(uintptr_t) <= (uintptr_t)hi; a += sizeof(uintptr_t)) mark_conservative(*(const uintptr_t *)a);
}

static void trace(Object *o) {
    Class *c = o->clazz;
    if (!c) return;
    if (c->is_array) {
        if (!c->component->prim) {
            ArrayObject *a = (ArrayObject *)o;
            Object **d = ARRAY_DATA(a, Object *);
            for (int32_t i = 0; i < a->length; i++) mark(d[i]);
        }
        return;
    }
    for (uint32_t i = 0; i < c->nref_offsets; i++) mark(*(Object **)((uint8_t *)o + c->ref_offsets[i]));
    if (c->flags & CF_REFERENCE) sa_vec_push(&g_found_refs, o);
}

static void drain(void) {
    while (g_mark_stack.len) trace(sa_vec_pop(&g_mark_stack));
}

static void mark_class(const char *key, void *value, void *ctx) {
    SA_UNUSED(key);
    SA_UNUSED(ctx);
    Class *c = value;
    mark(c->mirror);
    mark(c->throw_on_init);
    for (uint32_t i = 0; i < c->nsfields; i++) {
        Field *f = &c->sfields[i];
        if (f->kind == 'L' || f->kind == '[') mark(*(Object **)vm_static_ptr(f));
    }
    for (uint32_t i = 0; i < c->nmethods; i++) mark(c->methods[i].reflect);
}

static void mark_interned(const char *key, void *value, void *ctx) {
    SA_UNUSED(key);
    SA_UNUSED(ctx);
    mark(value);
}

/* Captures callee-saved registers so values that only live in registers are scanned. */
typedef struct {
    uintptr_t r[16];
} RegSnapshot;

static void __attribute__((noinline)) capture_regs(RegSnapshot *s) {
#if defined(__aarch64__)
    __asm__ volatile("stp x19, x20, [%0, #0]\n"
                     "stp x21, x22, [%0, #16]\n"
                     "stp x23, x24, [%0, #32]\n"
                     "stp x25, x26, [%0, #48]\n"
                     "stp x27, x28, [%0, #64]\n"
                     "stp x29, x30, [%0, #80]\n"
                     :
                     : "r"(s->r)
                     : "memory");
#elif defined(__x86_64__)
    __asm__ volatile("mov %%rbx, 0(%0)\n"
                     "mov %%rbp, 8(%0)\n"
                     "mov %%r12, 16(%0)\n"
                     "mov %%r13, 24(%0)\n"
                     "mov %%r14, 32(%0)\n"
                     "mov %%r15, 40(%0)\n"
                     :
                     : "r"(s->r)
                     : "memory");
#else
    jmp_buf jb;
    setjmp(jb);
    memcpy(s->r, &jb, sizeof(s->r) < sizeof(jb) ? sizeof(s->r) : sizeof(jb));
#endif
}

void vm_capture_thread_regs(VMThread *t) {
    RegSnapshot s;
    capture_regs(&s);
    memcpy(t->saved_regs, s.r, sizeof(t->saved_regs));
    volatile uintptr_t marker = 0;
    t->cstack_lo = (uintptr_t)&marker;
}

static void mark_thread(VMThread *t, VMThread *self, uintptr_t self_sp) {
    mark(t->jthread);
    mark(t->exception);
    mark_conservative(t->retval.raw);
    for (Frame *f = t->frame; f; f = f->prev) mark(f->caught);
    if (t->rstack) scan_range(t->rstack, t->rstack_top);
    for (uint32_t i = 0; i < t->nlocal_refs; i++) mark(t->local_refs[i]);
    if (t == self) {
        if (t->cstack_hi > self_sp) scan_range((void *)self_sp, (void *)t->cstack_hi);
    } else if (t->cstack_hi && t->cstack_lo && t->cstack_hi > t->cstack_lo) {
        scan_range((void *)t->cstack_lo, (void *)t->cstack_hi);
        scan_range(&t->saved_regs, (uint8_t *)&t->saved_regs + sizeof(t->saved_regs));
    }
}

static void __attribute__((noinline)) mark_roots(VMThread *self) {
    RegSnapshot regs;
    capture_regs(&regs);
    scan_range(&regs, (uint8_t *)&regs + sizeof regs);
    volatile uintptr_t marker = 0;
    uintptr_t sp = (uintptr_t)&marker;
    if (sp > (uintptr_t)&regs) sp = (uintptr_t)&regs;

    sa_map_foreach(&g_vm.classes, mark_class, NULL);
    drain();
    sa_map_foreach(&g_vm.interned, mark_interned, NULL);
    drain();
    for (VMThread *t = g_vm.threads; t; t = t->next) {
        mark_thread(t, self, sp);
        drain();
    }
    for (uint32_t i = 0; i < g_vm.nglobal_refs; i++) mark(g_vm.global_refs[i]);
    for (size_t i = 0; i < g_vm.explicit_roots.len; i++) mark(*(Object **)g_vm.explicit_roots.items[i]);
    drain();
}

/* ---- collection ---------------------------------------------------------------------------- */

void vm_gc(VMThread *t) {
    uint64_t start = sa_time_ns();
    size_t before = g_vm.heap_bytes, count_before = g_vm.objects.count;
    g_found_refs.len = 0;
    mark_roots(t);
    drain();
    /* clear weak references whose referents died */
    Field *ref_field = g_vm.wf.Reference_referent;
    if (ref_field) {
        for (size_t i = 0; i < g_found_refs.len; i++) {
            Object *r = g_found_refs.items[i];
            Object *referent = vm_get_ref(r, ref_field);
            if (referent && !(referent->gcflags & GC_MARK)) vm_set_ref(r, ref_field, NULL);
        }
    }
    /* weak JNI globals */
    SaPtrMap *wg = &g_vm.weak_global_refs;
    for (size_t i = 0; i < wg->cap; i++) {
        if (wg->keys[i] > 1) {
            Object **slot = (Object **)wg->keys[i];
            if (*slot && !((*slot)->gcflags & GC_MARK)) *slot = NULL;
        }
    }
    /* sweep into a fresh object set */
    SaPtrMap live = {0};
    size_t live_bytes = 0;
    SaPtrMap *old = &g_vm.objects;
    for (size_t i = 0; i < old->cap; i++) {
        uintptr_t k = old->keys[i];
        if (k <= 1) continue;
        Object *o = (Object *)k;
        size_t size = (size_t)(uintptr_t)old->values[i];
        if (o->gcflags & GC_MARK) {
            o->gcflags &= ~GC_MARK;
            sa_ptrmap_put(&live, o, (void *)(uintptr_t)size);
            live_bytes += size;
        } else {
            if (o->monitor) vm_monitor_free_index(o->monitor);
            free(o);
        }
    }
    sa_ptrmap_free(old);
    g_vm.objects = live;
    g_vm.heap_bytes = live_bytes;
    g_vm.heap_live_after_gc = live_bytes;
    g_vm.heap_threshold = live_bytes * 2 > MIN_HEAP_THRESHOLD ? live_bytes * 2 : MIN_HEAP_THRESHOLD;
    g_vm.gc_count++;
    LOGD("GC #%llu: %zu -> %zu objects, %zu KB -> %zu KB in %.2f ms", (unsigned long long)g_vm.gc_count, count_before,
         g_vm.objects.count, before / 1024, live_bytes / 1024, (double)(sa_time_ns() - start) / 1e6);
}

/* ---- strings -------------------------------------------------------------------------------- */

Object *vm_new_string_utf16(VMThread *t, const uint16_t *chars, int32_t len) {
    ArrayObject *arr = vm_alloc_prim_array(t, 'C', len);
    if (!arr) return NULL;
    if (len) memcpy(arr->data, chars, (size_t)len * 2);
    Object *s = vm_alloc_object(t, g_vm.wk.String);
    if (!s) return NULL;
    vm_set_ref(s, g_vm.wf.String_value, (Object *)arr);
    return s;
}

Object *vm_new_string_mutf8(VMThread *t, const char *mutf8) {
    size_t n = sa_mutf8_to_utf16(mutf8, NULL);
    ArrayObject *arr = vm_alloc_prim_array(t, 'C', (int32_t)n);
    if (!arr) return NULL;
    sa_mutf8_to_utf16(mutf8, ARRAY_DATA(arr, uint16_t));
    Object *s = vm_alloc_object(t, g_vm.wk.String);
    if (!s) return NULL;
    vm_set_ref(s, g_vm.wf.String_value, (Object *)arr);
    return s;
}

Object *vm_new_string_utf8(VMThread *t, const char *utf8) {
    if (!utf8) return NULL;
    size_t len = strlen(utf8);
    size_t n = sa_utf8_to_utf16(utf8, len, NULL);
    ArrayObject *arr = vm_alloc_prim_array(t, 'C', (int32_t)n);
    if (!arr) return NULL;
    sa_utf8_to_utf16(utf8, len, ARRAY_DATA(arr, uint16_t));
    Object *s = vm_alloc_object(t, g_vm.wk.String);
    if (!s) return NULL;
    vm_set_ref(s, g_vm.wf.String_value, (Object *)arr);
    return s;
}

int32_t vm_string_length(Object *str) {
    ArrayObject *v = (ArrayObject *)vm_get_ref(str, g_vm.wf.String_value);
    return v ? v->length : 0;
}

const uint16_t *vm_string_chars(Object *str) {
    ArrayObject *v = (ArrayObject *)vm_get_ref(str, g_vm.wf.String_value);
    return v ? ARRAY_DATA(v, uint16_t) : (const uint16_t *)"";
}

char *vm_string_to_utf8(Object *str) {
    if (!str) return sa_strdup("null");
    return sa_utf16_to_utf8(vm_string_chars(str), (size_t)vm_string_length(str), NULL);
}

bool vm_string_equals_utf8(Object *str, const char *utf8) {
    char *s = vm_string_to_utf8(str);
    bool eq = strcmp(s, utf8) == 0;
    free(s);
    return eq;
}

Object *vm_intern_string(VMThread *t, Object *str) {
    SA_UNUSED(t);
    char *key = sa_utf16_to_mutf8(vm_string_chars(str), (size_t)vm_string_length(str));
    Object *existing = sa_map_get(&g_vm.interned, key);
    if (existing) {
        free(key);
        return existing;
    }
    sa_map_put(&g_vm.interned, key, str);
    free(key);
    return str;
}

Object *vm_intern_utf8(VMThread *t, const char *utf8) {
    Object *s = vm_new_string_utf8(t, utf8);
    return s ? vm_intern_string(t, s) : NULL;
}

/* ---- boxing --------------------------------------------------------------------------------- */

Object *vm_box(VMThread *t, char prim, JValue v) {
    const char *cls, *desc;
    JValue arg;
    arg.raw = 0;
    switch (prim) {
    case 'Z': cls = "Ljava/lang/Boolean;"; desc = "(Z)Ljava/lang/Boolean;"; arg.i = v.i & 1; break;
    case 'B': cls = "Ljava/lang/Byte;"; desc = "(B)Ljava/lang/Byte;"; arg.i = (int8_t)v.i; break;
    case 'C': cls = "Ljava/lang/Character;"; desc = "(C)Ljava/lang/Character;"; arg.i = (uint16_t)v.i; break;
    case 'S': cls = "Ljava/lang/Short;"; desc = "(S)Ljava/lang/Short;"; arg.i = (int16_t)v.i; break;
    case 'I': cls = "Ljava/lang/Integer;"; desc = "(I)Ljava/lang/Integer;"; arg.i = v.i; break;
    case 'J': cls = "Ljava/lang/Long;"; desc = "(J)Ljava/lang/Long;"; arg.j = v.j; break;
    case 'F': cls = "Ljava/lang/Float;"; desc = "(F)Ljava/lang/Float;"; arg.f = v.f; break;
    case 'D': cls = "Ljava/lang/Double;"; desc = "(D)Ljava/lang/Double;"; arg.d = v.d; break;
    default: return v.l;
    }
    Class *c = vm_find_class(t, cls);
    if (!c || !vm_init_class(t, c)) return NULL;
    Method *m = vm_find_method(c, "valueOf", desc);
    if (!m) return NULL;
    return vm_callv(t, m, &arg).l;
}

bool vm_unbox(VMThread *t, Object *o, char prim, JValue *out) {
    out->raw = 0;
    if (prim == 'L' || prim == '[') {
        out->l = o;
        return true;
    }
    if (!o) {
        vm_throw_npe(t, "unboxing null");
        return false;
    }
    const char *d = o->clazz->descriptor;
    char src;
    if (!strcmp(d, "Ljava/lang/Boolean;")) src = 'Z';
    else if (!strcmp(d, "Ljava/lang/Byte;")) src = 'B';
    else if (!strcmp(d, "Ljava/lang/Character;")) src = 'C';
    else if (!strcmp(d, "Ljava/lang/Short;")) src = 'S';
    else if (!strcmp(d, "Ljava/lang/Integer;")) src = 'I';
    else if (!strcmp(d, "Ljava/lang/Long;")) src = 'J';
    else if (!strcmp(d, "Ljava/lang/Float;")) src = 'F';
    else if (!strcmp(d, "Ljava/lang/Double;")) src = 'D';
    else {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "cannot unbox %s", o->clazz->name);
        return false;
    }
    JValue v = vm_field_get(o, vm_find_field(o->clazz, "value", NULL));
    /* widening conversions */
    double dv;
    int64_t lv;
    switch (src) {
    case 'J': lv = v.j; dv = (double)v.j; break;
    case 'F': lv = (int64_t)v.f; dv = v.f; break;
    case 'D': lv = (int64_t)v.d; dv = v.d; break;
    default: lv = v.i; dv = v.i; break;
    }
    if (src == prim) {
        *out = v;
        return true;
    }
    if (src == 'Z' || prim == 'Z') goto bad;
    switch (prim) {
    case 'S':
        if (src != 'B') goto bad;
        out->i = (int32_t)lv;
        return true;
    case 'I':
        if (src != 'B' && src != 'S' && src != 'C') goto bad;
        out->i = (int32_t)lv;
        return true;
    case 'J':
        if (src == 'F' || src == 'D') goto bad;
        out->j = lv;
        return true;
    case 'F':
        if (src == 'D') goto bad;
        out->f = (src == 'J') ? (float)lv : (float)dv;
        return true;
    case 'D': out->d = (src == 'J') ? (double)lv : dv; return true;
    default: break;
    }
bad:
    vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "argument type mismatch");
    return false;
}
