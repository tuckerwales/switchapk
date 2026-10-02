/*
 * Runtime annotation parsing for java.lang.reflect.AnnotationParser.
 *
 * Dex visibility: 0 = build (CLASS retention), 1 = runtime, 2 = system
 * (InnerClass, Signature, AnnotationDefault). getAnnotation returns only
 * visibility 1. d8 stores defaults as one class-level
 * Ldalvik/annotation/AnnotationDefault; whose value is an annotation of
 * this type. Older dx stores that annotation on each member method.
 * Either way the element named "value" holds the default.
 *
 * Each runtime annotation is returned as a HashMap. The "@type" entry is
 * the annotation class. Other entries are member values. Nested
 * annotations are maps, and arrays of annotations are Object[] of maps.
 * Java wraps those maps in proxies.
 */
#include "natives.h"

#define VIS_RUNTIME 1
#define ANN_DEFAULT "Ldalvik/annotation/AnnotationDefault;"

static const char *ret_desc(const char *desc) {
    const char *r = strchr(desc, ')');
    return r ? r + 1 : "V";
}

static Method *ann_member(Class *ann, const char *name) {
    for (uint32_t i = 0; i < ann->nmethods; i++) {
        Method *m = &ann->methods[i];
        if (m->desc[0] == '(' && m->desc[1] == ')' && !strcmp(m->name, name)) return m;
    }
    return NULL;
}

static const uint8_t *member_set(DexFile *d, const DexClassDef *cd, int kind, uint32_t member_idx) {
    if (!cd->annotations_off || cd->annotations_off >= d->size) return NULL;
    const uint8_t *dir = d->base + cd->annotations_off;
    if ((size_t)(dir - d->base) + 16 > d->size) return NULL;
    if (kind == 0) {
        uint32_t off = sa_rd32(dir);
        if (!off || off >= d->size) return NULL;
        return d->base + off;
    }
    uint32_t nfields = sa_rd32(dir + 4);
    uint32_t nmethods = sa_rd32(dir + 8);
    const uint8_t *p = dir + 16;
    uint32_t n = kind == 1 ? nfields : nmethods;
    if (kind == 2) p += (size_t)nfields * 8;
    if ((size_t)(p - d->base) + (size_t)n * 8 > d->size) return NULL;
    for (uint32_t i = 0; i < n; i++) {
        uint32_t idx = sa_rd32(p);
        uint32_t off = sa_rd32(p + 4);
        p += 8;
        if (idx == member_idx && off && off < d->size) return d->base + off;
    }
    return NULL;
}

static int count_visible(DexFile *d, const uint8_t *set, uint8_t vis) {
    if (!set) return 0;
    uint32_t n = sa_rd32(set);
    int c = 0;
    for (uint32_t i = 0; i < n; i++) {
        uint32_t off = sa_rd32(set + 4 + i * 4);
        if (off && off < d->size && d->base[off] == vis) c++;
    }
    return c;
}

static int32_t ev_small(const DexEncodedValue *ev) {
    if (ev->type == DEV_BOOLEAN) return ev->u.z ? 1 : 0;
    if (ev->type == DEV_LONG) return (int32_t)ev->u.j;
    return ev->u.i;
}

static Object *box_kind(VMThread *t, char kind, int32_t narrow, int64_t wide, float f, double dbl) {
    JValue v;
    v.raw = 0;
    if (kind == 'J') v.j = wide;
    else if (kind == 'F') v.f = f;
    else if (kind == 'D') v.d = dbl;
    else v.i = narrow;
    return vm_box(t, kind, v);
}

static bool map_put(VMThread *t, Object *map, const char *key, Object *val) {
    Object *k = vm_intern_utf8(t, key);
    if (!k || t->exception) return false;
    vm_call_virtual(t, map, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", k, val);
    return !t->exception;
}

static Object *encoded_to_java(VMThread *t, DexFile *d, const uint8_t **pp, const char *expect);
static Object *parse_annotation_map(VMThread *t, DexFile *d, const uint8_t **pp);

static Object *build_array(VMThread *t, DexFile *d, const uint8_t *p, uint32_t n, const char *expect) {
    char prim = 0;
    const char *comp = NULL;
    Class *ac = g_vm.wk.arr_Object;
    if (expect && expect[0] == '[') {
        if (expect[1] == 'L' || expect[1] == '[') {
            comp = expect + 1;
            Class *cc = vm_find_class(t, comp);
            if (t->exception) return NULL;
            if (cc && !(cc->access & ACC_ANNOTATION)) {
                ac = vm_array_class_of(t, cc);
                if (!ac) return NULL;
            }
        } else {
            prim = expect[1];
            switch (prim) {
            case 'Z': ac = g_vm.wk.arr_Z; break;
            case 'B': ac = g_vm.wk.arr_B; break;
            case 'C': ac = g_vm.wk.arr_C; break;
            case 'S': ac = g_vm.wk.arr_S; break;
            case 'I': ac = g_vm.wk.arr_I; break;
            case 'J': ac = g_vm.wk.arr_J; break;
            case 'F': ac = g_vm.wk.arr_F; break;
            case 'D': ac = g_vm.wk.arr_D; break;
            default: prim = 0; break;
            }
        }
    }
    ArrayObject *a = vm_alloc_array(t, ac, (int32_t)n);
    if (!a) return NULL;
    for (uint32_t i = 0; i < n; i++) {
        if (prim) {
            DexEncodedValue ev;
            dex_read_encoded_value(&p, &ev);
            switch (prim) {
            case 'Z':
            case 'B': ARRAY_DATA(a, uint8_t)[i] = (uint8_t)ev_small(&ev); break;
            case 'C':
            case 'S': ARRAY_DATA(a, uint16_t)[i] = (uint16_t)ev_small(&ev); break;
            case 'I': ARRAY_DATA(a, int32_t)[i] = ev_small(&ev); break;
            case 'J': ARRAY_DATA(a, int64_t)[i] = ev.type == DEV_LONG ? ev.u.j : ev_small(&ev); break;
            case 'F': ARRAY_DATA(a, float)[i] = ev.u.f; break;
            case 'D': ARRAY_DATA(a, double)[i] = ev.type == DEV_DOUBLE ? ev.u.d : (double)ev.u.f; break;
            }
        } else {
            Object *el = encoded_to_java(t, d, &p, comp ? comp : "Ljava/lang/Object;");
            if (t->exception) return NULL;
            ARRAY_DATA(a, Object *)[i] = el;
        }
    }
    return (Object *)a;
}

static Object *encoded_to_java(VMThread *t, DexFile *d, const uint8_t **pp, const char *expect) {
    DexEncodedValue v;
    dex_read_encoded_value(pp, &v);
    char pk = (expect && expect[0] && !expect[1]) ? expect[0] : 0;
    switch (v.type) {
    case DEV_BOOLEAN: return box_kind(t, pk ? pk : 'Z', v.u.z ? 1 : 0, 0, 0, 0);
    case DEV_BYTE:
    case DEV_SHORT:
    case DEV_CHAR:
    case DEV_INT: {
        char k = pk ? pk : 'I';
        if (k == 'J') return box_kind(t, 'J', 0, v.u.i, 0, 0);
        if (k == 'F') return box_kind(t, 'F', 0, 0, (float)v.u.i, 0);
        if (k == 'D') return box_kind(t, 'D', 0, 0, 0, (double)v.u.i);
        return box_kind(t, k, v.u.i, 0, 0, 0);
    }
    case DEV_LONG: return box_kind(t, 'J', 0, v.u.j, 0, 0);
    case DEV_FLOAT: return box_kind(t, pk == 'D' ? 'D' : 'F', 0, 0, v.u.f, v.u.f);
    case DEV_DOUBLE: return box_kind(t, 'D', 0, 0, 0, v.u.d);
    case DEV_STRING: return vm_resolve_string(t, d, v.u.idx);
    case DEV_TYPE: {
        Class *c = vm_resolve_type(t, d, v.u.idx);
        return c ? vm_class_mirror(t, c) : NULL;
    }
    case DEV_ENUM: {
        DexFieldId fid;
        dex_field_id(d, v.u.idx, &fid);
        Class *ec = vm_resolve_type(t, d, fid.class_idx);
        if (!ec || !vm_init_class(t, ec)) return NULL;
        const char *fname = dex_string(d, fid.name_idx);
        const char *ftype = dex_type_desc(d, fid.type_idx);
        Field *f = vm_find_field(ec, fname, ftype);
        if (!f) {
            vm_throw_new(t, "Ljava/lang/RuntimeException;", "enum constant %s", fname);
            return NULL;
        }
        return vm_field_get(NULL, f).l;
    }
    case DEV_ARRAY: {
        const uint8_t *ap = v.array;
        uint32_t n = dex_uleb128(&ap);
        return build_array(t, d, ap, n, expect);
    }
    case DEV_ANNOTATION: {
        const uint8_t *ap = v.array;
        return parse_annotation_map(t, d, &ap);
    }
    case DEV_NULL: return NULL;
    default:
        vm_throw_new(t, "Ljava/lang/RuntimeException;", "annotation value type 0x%x", v.type);
        return NULL;
    }
}

static Object *parse_annotation_map(VMThread *t, DexFile *d, const uint8_t **pp) {
    uint32_t type_idx = dex_uleb128(pp);
    Class *ann = vm_find_class(t, dex_type_desc(d, type_idx));
    if (!ann || t->exception || !vm_init_class(t, ann)) return NULL;
    Object *map = vm_new_instance(t, "Ljava/util/HashMap;", "()V");
    if (!map || !map_put(t, map, "@type", vm_class_mirror(t, ann))) return NULL;
    uint32_t n = dex_uleb128(pp);
    for (uint32_t i = 0; i < n; i++) {
        const char *ename = dex_string(d, dex_uleb128(pp));
        Method *mem = ann_member(ann, ename);
        const char *expect = mem ? ret_desc(mem->desc) : "Ljava/lang/Object;";
        Object *val = encoded_to_java(t, d, pp, expect);
        if (t->exception) return NULL;
        if (mem && val && !map_put(t, map, ename, val)) return NULL;
    }
    return map;
}

static const uint8_t *set_for(int kind, Class *owner, int64_t token, Class **out_c) {
    Class *c = NULL;
    uint32_t idx = 0;
    if (kind == 0) {
        c = owner;
    } else if (kind == 1) {
        Field *f = (Field *)(intptr_t)token;
        if (!f) return NULL;
        c = f->clazz;
        idx = f->field_idx;
    } else {
        Method *m = (Method *)(intptr_t)token;
        if (!m) return NULL;
        c = m->clazz;
        idx = m->method_idx;
    }
    *out_c = c;
    if (!c || !c->dex || c->class_def_idx < 0) return NULL;
    DexClassDef cd;
    dex_class_def(c->dex, (uint32_t)c->class_def_idx, &cd);
    return member_set(c->dex, &cd, kind, idx);
}

NATIVE(AnnotationParser_readNative) {
    Object *owner = A_OBJ(0);
    int kind = A_INT(1);
    int64_t token = A_LONG(2);
    Class *c = NULL;
    Class *mirror = (kind == 0 && owner) ? vm_class_from_mirror(owner) : NULL;
    const uint8_t *set = set_for(kind, mirror, token, &c);
    if (!c || !c->dex) {
        R_OBJ(vm_alloc_array(t, g_vm.wk.arr_Object, 0));
        return;
    }
    int nrun = count_visible(c->dex, set, VIS_RUNTIME);
    ArrayObject *arr = vm_alloc_array(t, g_vm.wk.arr_Object, nrun);
    if (!arr) return;
    if (!set || nrun == 0) {
        R_OBJ(arr);
        return;
    }
    uint32_t n = sa_rd32(set);
    int k = 0;
    for (uint32_t i = 0; i < n && k < nrun; i++) {
        uint32_t off = sa_rd32(set + 4 + i * 4);
        if (!off || off >= c->dex->size) continue;
        const uint8_t *item = c->dex->base + off;
        if (*item != VIS_RUNTIME) continue;
        const uint8_t *q = item + 1;
        Object *map = parse_annotation_map(t, c->dex, &q);
        if (!map) return;
        ARRAY_DATA(arr, Object *)[k++] = map;
    }
    R_OBJ(arr);
}

/* pp is an encoded_annotation. Returns the element named want. */
static Object *ann_element(VMThread *t, DexFile *d, const uint8_t **pp, const char *want, const char *expect) {
    dex_uleb128(pp); /* annotation type */
    uint32_t n = dex_uleb128(pp);
    for (uint32_t i = 0; i < n; i++) {
        const char *ename = dex_string(d, dex_uleb128(pp));
        if (!strcmp(ename, want)) return encoded_to_java(t, d, pp, expect);
        dex_skip_encoded_value(pp);
    }
    return NULL;
}

/* One AnnotationDefault item. Class form wraps every member default in an
 * annotation; method form stores this member's default directly. */
static Object *default_item(VMThread *t, DexFile *d, const uint8_t *item, const char *member, const char *expect,
                             bool class_form) {
    const uint8_t *q = item + 1;
    uint32_t type_idx = dex_uleb128(&q);
    if (strcmp(dex_type_desc(d, type_idx), ANN_DEFAULT) != 0) return NULL;
    uint32_t ne = dex_uleb128(&q);
    for (uint32_t e = 0; e < ne; e++) {
        const char *ename = dex_string(d, dex_uleb128(&q));
        if (strcmp(ename, "value") != 0) {
            dex_skip_encoded_value(&q);
            continue;
        }
        if (!class_form) return encoded_to_java(t, d, &q, expect);
        DexEncodedValue v;
        dex_read_encoded_value(&q, &v);
        if (v.type != DEV_ANNOTATION || !v.array) return NULL;
        const uint8_t *ap = v.array;
        return ann_element(t, d, &ap, member, expect);
    }
    return NULL;
}

static Object *default_in_set(VMThread *t, DexFile *d, const uint8_t *set, const char *member, const char *expect,
                               bool class_form) {
    if (!set) return NULL;
    uint32_t n = sa_rd32(set);
    for (uint32_t i = 0; i < n; i++) {
        uint32_t off = sa_rd32(set + 4 + i * 4);
        if (!off || off >= d->size) continue;
        Object *v = default_item(t, d, d->base + off, member, expect, class_form);
        if (v || t->exception) return v;
    }
    return NULL;
}

static Object *read_default_value(VMThread *t, Method *m) {
    if (!m || !m->dex || !m->clazz || m->clazz->class_def_idx < 0) return NULL;
    DexClassDef cd;
    dex_class_def(m->dex, (uint32_t)m->clazz->class_def_idx, &cd);
    const char *expect = ret_desc(m->desc);
    Object *v = default_in_set(t, m->dex, member_set(m->dex, &cd, 0, 0), m->name, expect, true);
    if (v || t->exception) return v;
    return default_in_set(t, m->dex, member_set(m->dex, &cd, 2, m->method_idx), m->name, expect, false);
}

NATIVE(AnnotationParser_readDefault) {
    Method *m = (Method *)(intptr_t)A_LONG(0);
    if (!m) return;
    Object *v = read_default_value(t, m);
    if (t->exception) return;
    R_OBJ(v);
}

static const NativeMethodReg g_ann_regs[] = {
    {"Ljava/lang/reflect/AnnotationParser;", "readNative", "(Ljava/lang/Class;IJ)[Ljava/lang/Object;",
     AnnotationParser_readNative},
    {"Ljava/lang/reflect/AnnotationParser;", "readDefaultNative", "(J)Ljava/lang/Object;",
     AnnotationParser_readDefault},
};

void natives_java_annotation_register(void) { vm_register_natives(g_ann_regs, SA_ARRAY_LEN(g_ann_regs)); }
