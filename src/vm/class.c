/*
 * Class loading, linking, initialization and symbolic resolution.
 */
#include "vm.h"

#define LOG_TAG "class"

VM g_vm;

static Class *g_prim_classes[128];

/* ---- descriptor helpers ------------------------------------------------------ */

const char *vm_desc_to_dotted(const char *desc, char *buf, size_t bufsz) {
    size_t n = strlen(desc);
    if (desc[0] == 'L' && desc[n - 1] == ';') {
        size_t len = n - 2 < bufsz - 1 ? n - 2 : bufsz - 1;
        for (size_t i = 0; i < len; i++) buf[i] = desc[1 + i] == '/' ? '.' : desc[1 + i];
        buf[len] = 0;
    } else if (desc[0] == '[') {
        size_t len = n < bufsz - 1 ? n : bufsz - 1;
        for (size_t i = 0; i < len; i++) buf[i] = desc[i] == '/' ? '.' : desc[i];
        buf[len] = 0;
    } else {
        const char *p;
        switch (desc[0]) {
        case 'Z': p = "boolean"; break;
        case 'B': p = "byte"; break;
        case 'C': p = "char"; break;
        case 'S': p = "short"; break;
        case 'I': p = "int"; break;
        case 'J': p = "long"; break;
        case 'F': p = "float"; break;
        case 'D': p = "double"; break;
        case 'V': p = "void"; break;
        default: p = desc; break;
        }
        snprintf(buf, bufsz, "%s", p);
    }
    return buf;
}

char *vm_dotted_to_desc(const char *dotted) {
    if (dotted[0] == '[') {
        char *r = sa_strdup(dotted);
        for (char *p = r; *p; p++)
            if (*p == '.') *p = '/';
        return r;
    }
    static const struct {
        const char *n;
        const char *d;
    } prims[] = {{"boolean", "Z"}, {"byte", "B"}, {"char", "C"}, {"short", "S"}, {"int", "I"},
                 {"long", "J"},    {"float", "F"}, {"double", "D"}, {"void", "V"}};
    for (size_t i = 0; i < SA_ARRAY_LEN(prims); i++)
        if (!strcmp(dotted, prims[i].n)) return sa_strdup(prims[i].d);
    size_t n = strlen(dotted);
    char *r = sa_malloc(n + 3);
    r[0] = 'L';
    for (size_t i = 0; i < n; i++) r[1 + i] = dotted[i] == '.' ? '/' : dotted[i];
    r[n + 1] = ';';
    r[n + 2] = 0;
    return r;
}

int vm_shorty_slots(const char *shorty, bool is_static) {
    int n = is_static ? 0 : 1;
    for (const char *p = shorty + 1; *p; p++) n += (*p == 'J' || *p == 'D') ? 2 : 1;
    return n;
}

static uint8_t prim_size(char c) {
    switch (c) {
    case 'Z':
    case 'B': return 1;
    case 'C':
    case 'S': return 2;
    case 'I':
    case 'F': return 4;
    default: return 8;
    }
}

void vm_method_pretty(Method *m, char *buf, size_t n) {
    snprintf(buf, n, "%s.%s%s", m->clazz ? m->clazz->name : "?", m->name, m->desc);
}

/* ---- primitive & array classes ------------------------------------------------ */

static void register_class(Class *c) { sa_map_put(&g_vm.classes, c->descriptor, c); }

Class *vm_primitive_class(char prim) {
    if ((unsigned char)prim >= 128) return NULL;
    if (g_prim_classes[(int)prim]) return g_prim_classes[(int)prim];
    char desc[2] = {prim, 0};
    char name[16];
    vm_desc_to_dotted(desc, name, sizeof name);
    Class *c = sa_calloc(1, sizeof *c);
    c->descriptor = sa_intern(desc);
    c->name = sa_intern(name);
    c->prim = prim;
    c->access = ACC_PUBLIC | ACC_FINAL | ACC_ABSTRACT;
    c->state = CLASS_INITIALIZED;
    c->flags = CF_BOOT;
    g_prim_classes[(int)prim] = c;
    return c;
}

Class *vm_array_class_of(VMThread *t, Class *component) {
    if (component->array_class) return component->array_class;
    char *desc = sa_sprintf("[%s", component->descriptor);
    Class *c = vm_find_class(t, desc);
    free(desc);
    return c;
}

static Class *create_array_class(VMThread *t, const char *desc) {
    Class *comp = vm_find_class(t, desc + 1);
    if (!comp) return NULL;
    Class *obj = g_vm.wk.Object;
    Class *c = sa_calloc(1, sizeof *c);
    c->descriptor = sa_intern(desc);
    char name[512];
    vm_desc_to_dotted(desc, name, sizeof name);
    c->name = sa_intern(name);
    c->is_array = true;
    c->component = comp;
    c->elem_size = comp->prim ? prim_size(comp->prim) : sizeof(Object *);
    c->access = ACC_PUBLIC | ACC_FINAL | ACC_ABSTRACT;
    c->super = obj;
    c->state = CLASS_INITIALIZED;
    c->flags = comp->flags & CF_BOOT;
    if (obj) {
        c->vtable = obj->vtable;
        c->vtable_len = obj->vtable_len;
        c->instance_size = obj->instance_size;
    }
    Class *ifs[2] = {g_vm.wk.Cloneable, g_vm.wk.Serializable};
    int n = (ifs[0] ? 1 : 0) + (ifs[1] ? 1 : 0);
    c->interfaces = sa_calloc(2, sizeof(Class *));
    c->ninterfaces = 0;
    for (int i = 0; i < 2; i++)
        if (ifs[i]) c->interfaces[c->ninterfaces++] = ifs[i];
    c->all_interfaces = c->interfaces;
    c->nall_interfaces = (uint32_t)n;
    comp->array_class = c;
    register_class(c);
    return c;
}

/* ---- loading from dex ------------------------------------------------------------ */

static bool same_package(const char *a, const char *b) {
    const char *sa = strrchr(a, '/'), *sb = strrchr(b, '/');
    size_t la = sa ? (size_t)(sa - a) : 0, lb = sb ? (size_t)(sb - b) : 0;
    return la == lb && strncmp(a, b, la) == 0;
}

static void collect_interfaces(Class *c, SaVec *out) {
    for (uint32_t i = 0; i < c->ninterfaces; i++) {
        Class *ifc = c->interfaces[i];
        bool dup = false;
        for (size_t k = 0; k < out->len; k++)
            if (out->items[k] == ifc) dup = true;
        if (!dup) sa_vec_push(out, ifc);
        collect_interfaces(ifc, out);
    }
    if (c->super) collect_interfaces(c->super, out);
}

static int field_order(const Field *f) {
    switch (f->kind) {
    case 'L':
    case '[': return 0;
    case 'J':
    case 'D': return 1;
    case 'I':
    case 'F': return 2;
    case 'C':
    case 'S': return 3;
    default: return 4;
    }
}

static void layout_instance_fields(Class *c) {
    uint32_t off = c->super ? c->super->instance_size : sizeof(Object);
    SaVec refs = {0};
    if (c->super)
        for (uint32_t i = 0; i < c->super->nref_offsets; i++)
            sa_vec_push(&refs, (void *)(uintptr_t)c->super->ref_offsets[i]);
    bool is_reference = (c->flags & CF_REFERENCE) != 0;
    for (int pass = 0; pass <= 4; pass++) {
        for (uint32_t i = 0; i < c->nifields; i++) {
            Field *f = &c->ifields[i];
            if (field_order(f) != pass) continue;
            uint32_t sz = (f->kind == 'L' || f->kind == '[') ? 8 : prim_size(f->kind);
            off = SA_ALIGN_UP(off, sz);
            f->offset = off;
            off += sz;
            if (pass == 0) {
                bool skip = is_reference && !strcmp(f->name, "referent") &&
                            !strcmp(c->descriptor, "Ljava/lang/ref/Reference;");
                if (!skip) sa_vec_push(&refs, (void *)(uintptr_t)f->offset);
            }
        }
    }
    c->instance_size = SA_ALIGN_UP(off, 8);
    c->nref_offsets = (uint32_t)refs.len;
    c->ref_offsets = sa_calloc(refs.len + 1, sizeof(uint32_t));
    for (size_t i = 0; i < refs.len; i++) c->ref_offsets[i] = (uint32_t)(uintptr_t)refs.items[i];
    sa_vec_free(&refs);
}

static void build_vtable(Class *c) {
    if (c->access & ACC_INTERFACE) {
        for (uint32_t i = 0; i < c->nmethods; i++) c->methods[i].vtable_index = -1;
        return;
    }
    uint32_t cap = (c->super ? c->super->vtable_len : 0) + c->nmethods + 1;
    Method **vt = sa_calloc(cap, sizeof(Method *));
    uint32_t len = 0;
    if (c->super) {
        memcpy(vt, c->super->vtable, c->super->vtable_len * sizeof(Method *));
        len = c->super->vtable_len;
    }
    for (uint32_t i = 0; i < c->nmethods; i++) {
        Method *m = &c->methods[i];
        m->vtable_index = -1;
        if ((m->access & (ACC_STATIC | ACC_PRIVATE)) || (m->access & ACC_CONSTRUCTOR) || m->name[0] == '<') continue;
        int32_t slot = -1;
        for (uint32_t k = 0; k < len; k++) {
            Method *sm = vt[k];
            if (sm->name != m->name || sm->desc != m->desc) continue;
            if (!(sm->access & (ACC_PUBLIC | ACC_PROTECTED)) &&
                !same_package(sm->clazz->descriptor, c->descriptor))
                continue; /* package-private in another package: no override */
            slot = (int32_t)k;
            break;
        }
        if (slot < 0) slot = (int32_t)len++;
        vt[slot] = m;
        m->vtable_index = slot;
    }
    c->vtable = vt;
    c->vtable_len = len;
}

static void fill_method(Class *c, DexFile *d, Method *m, const DexMethodEntry *e) {
    DexMethodId mid;
    dex_method_id(d, e->method_idx, &mid);
    m->clazz = c;
    m->dex = d;
    m->method_idx = e->method_idx;
    m->name = dex_string(d, mid.name_idx);
    m->desc = dex_proto_desc(d, mid.proto_idx);
    m->shorty = dex_proto_shorty(d, mid.proto_idx);
    m->access = e->access_flags;
    m->arg_slots = (uint16_t)vm_shorty_slots(m->shorty, (m->access & ACC_STATIC) != 0);
    if (e->code_off) m->has_code = dex_code(d, e->code_off, &m->code);
    if (m->access & ACC_NATIVE) m->native = vm_lookup_native(c->descriptor, m->name, m->desc);
    /* internal natives may also override non-native Java methods (intrinsics) */
    else if (c->flags & CF_BOOT) {
        NativeFn fn = vm_lookup_native(c->descriptor, m->name, m->desc);
        if (fn) m->native = fn;
    }
}

static void fill_field(Class *c, DexFile *d, Field *f, const DexFieldEntry *e) {
    DexFieldId fid;
    dex_field_id(d, e->field_idx, &fid);
    f->clazz = c;
    f->name = dex_string(d, fid.name_idx);
    f->type = dex_type_desc(d, fid.type_idx);
    f->kind = f->type[0];
    f->access = e->access_flags;
    f->field_idx = e->field_idx;
}

static Class *define_class(VMThread *t, DexFile *d, uint32_t idx, bool boot) {
    DexClassDef cd;
    dex_class_def(d, idx, &cd);
    const char *desc = dex_type_desc(d, cd.class_idx);
    Class *c = sa_calloc(1, sizeof *c);
    c->descriptor = desc;
    char name[512];
    vm_desc_to_dotted(desc, name, sizeof name);
    c->name = sa_intern(name);
    c->access = cd.access_flags;
    c->dex = d;
    c->class_def_idx = (int32_t)idx;
    c->state = CLASS_LOADED;
    if (boot) c->flags |= CF_BOOT;
    if (cd.source_file_idx != DEX_NO_INDEX) c->source_file = dex_string(d, cd.source_file_idx);
    register_class(c); /* visible early for self-references during linking */

    if (cd.superclass_idx != DEX_NO_INDEX) {
        const char *sdesc = dex_type_desc(d, cd.superclass_idx);
        c->super = vm_find_class(t, sdesc);
        if (!c->super) goto fail;
        if (c->super->access & ACC_INTERFACE) {
            LOGE("%s: superclass %s is an interface", c->name, c->super->name);
            goto fail;
        }
        c->flags |= c->super->flags & (CF_REFERENCE | CF_THROWABLE | CF_FINALIZABLE);
    }
    if (!strcmp(desc, "Ljava/lang/ref/Reference;")) c->flags |= CF_REFERENCE;
    if (!strcmp(desc, "Ljava/lang/Throwable;")) c->flags |= CF_THROWABLE;
    if (!strcmp(desc, "Ljava/lang/String;")) c->flags |= CF_STRING;
    if (!strcmp(desc, "Ljava/lang/Class;")) c->flags |= CF_CLASS;

    uint32_t nif = dex_type_list_size(d, cd.interfaces_off);
    c->interfaces = sa_calloc(nif + 1, sizeof(Class *));
    for (uint32_t i = 0; i < nif; i++) {
        const char *idesc = dex_type_desc(d, dex_type_list_item(d, cd.interfaces_off, i));
        Class *ic = vm_find_class(t, idesc);
        if (!ic) goto fail;
        c->interfaces[c->ninterfaces++] = ic;
    }
    SaVec all = {0};
    collect_interfaces(c, &all);
    c->nall_interfaces = (uint32_t)all.len;
    c->all_interfaces = (Class **)all.items;

    DexClassData data;
    dex_class_data(d, cd.class_data_off, &data);
    c->nsfields = data.static_fields_size;
    c->nifields = data.instance_fields_size;
    c->sfields = sa_calloc(c->nsfields + 1, sizeof(Field));
    c->ifields = sa_calloc(c->nifields + 1, sizeof(Field));
    for (uint32_t i = 0; i < c->nsfields; i++) {
        fill_field(c, d, &c->sfields[i], &data.static_fields[i]);
        c->sfields[i].offset = i * 8;
    }
    for (uint32_t i = 0; i < c->nifields; i++) fill_field(c, d, &c->ifields[i], &data.instance_fields[i]);
    c->static_size = c->nsfields * 8;
    c->static_data = sa_calloc(c->static_size + 8, 1);
    c->nmethods = data.direct_methods_size + data.virtual_methods_size;
    c->methods = sa_calloc(c->nmethods + 1, sizeof(Method));
    uint32_t mi = 0;
    for (uint32_t i = 0; i < data.direct_methods_size; i++) fill_method(c, d, &c->methods[mi++], &data.direct_methods[i]);
    for (uint32_t i = 0; i < data.virtual_methods_size; i++) fill_method(c, d, &c->methods[mi++], &data.virtual_methods[i]);
    dex_class_data_free(&data);

    layout_instance_fields(c);
    build_vtable(c);
    c->state = CLASS_LINKED;
    if (vm_find_method(c, "finalize", "()V") && strcmp(desc, "Ljava/lang/Object;")) c->flags |= CF_FINALIZABLE;
    LOGV("loaded %s (%s)", c->name, d->location);
    return c;
fail:
    sa_map_remove(&g_vm.classes, desc);
    free(c);
    return NULL;
}

static Class *load_from_dex(VMThread *t, const char *desc) {
    for (size_t i = 0; i < g_vm.boot_dex.len; i++) {
        DexFile *d = g_vm.boot_dex.items[i];
        int32_t idx = dex_find_class(d, desc);
        if (idx >= 0) return define_class(t, d, (uint32_t)idx, true);
    }
    for (size_t i = 0; i < g_vm.app_dex.len; i++) {
        DexFile *d = g_vm.app_dex.items[i];
        int32_t idx = dex_find_class(d, desc);
        if (idx >= 0) return define_class(t, d, (uint32_t)idx, false);
    }
    return NULL;
}

Class *vm_find_class_noexc(VMThread *t, const char *desc) {
    Class *c = sa_map_get(&g_vm.classes, desc);
    if (c) return c->state == CLASS_ERROR && !c->dex ? NULL : c;
    if (desc[0] == '[') {
        const char *comp = desc + 1;
        if (comp[0] != '[' && comp[0] != 'L' && comp[1] == 0) {
            if (!vm_primitive_class(comp[0])) return NULL;
            Class *pc = vm_primitive_class(comp[0]);
            if (!sa_map_has(&g_vm.classes, pc->descriptor)) register_class(pc);
        } else if (!vm_find_class_noexc(t, comp)) {
            return NULL;
        }
        return create_array_class(t, desc);
    }
    if (desc[1] == 0) {
        Class *pc = vm_primitive_class(desc[0]);
        return pc;
    }
    if (desc[0] != 'L') return NULL;
    return load_from_dex(t, desc);
}

Class *vm_find_class(VMThread *t, const char *desc) {
    Class *c = vm_find_class_noexc(t, desc);
    if (!c && t && !t->exception) {
        char name[512];
        vm_desc_to_dotted(desc, name, sizeof name);
        LOGW("class not found: %s", name);
        vm_throw_new(t, "Ljava/lang/NoClassDefFoundError;", "%s", name);
    }
    return c;
}

Class *vm_class_from_name(VMThread *t, const char *dotted, bool init) {
    char *desc = vm_dotted_to_desc(dotted);
    Class *c = vm_find_class_noexc(t, desc);
    free(desc);
    if (!c) {
        if (!t->exception) vm_throw_new(t, "Ljava/lang/ClassNotFoundException;", "%s", dotted);
        return NULL;
    }
    if (init && !vm_init_class(t, c)) return NULL;
    return c;
}

/* ---- hierarchy queries ---------------------------------------------------------- */

bool vm_is_assignable(Class *to, Class *from) {
    if (to == from) return true;
    if (!from || !to) return false;
    if (to == g_vm.wk.Object) return !from->prim;
    if (from->is_array) {
        if (to->is_array) {
            Class *tc = to->component, *fc = from->component;
            if (tc->prim || fc->prim) return tc == fc;
            return vm_is_assignable(tc, fc);
        }
        /* arrays implement Cloneable and Serializable */
        return to == g_vm.wk.Cloneable || to == g_vm.wk.Serializable;
    }
    if (to->access & ACC_INTERFACE) {
        for (uint32_t i = 0; i < from->nall_interfaces; i++)
            if (from->all_interfaces[i] == to) return true;
        return false;
    }
    for (Class *c = from->super; c; c = c->super)
        if (c == to) return true;
    return false;
}

bool vm_instance_of(Object *o, Class *c) { return o && vm_is_assignable(c, o->clazz); }

Method *vm_find_method(Class *c, const char *name, const char *desc) {
    name = sa_intern(name);
    desc = sa_intern(desc);
    for (uint32_t i = 0; i < c->nmethods; i++)
        if (c->methods[i].name == name && c->methods[i].desc == desc) return &c->methods[i];
    return NULL;
}

static Method *find_declared_interned(Class *c, const char *name, const char *desc) {
    for (uint32_t i = 0; i < c->nmethods; i++)
        if (c->methods[i].name == name && c->methods[i].desc == desc) return &c->methods[i];
    return NULL;
}

Method *vm_find_method_hier(Class *c, const char *name, const char *desc) {
    name = sa_intern(name);
    desc = sa_intern(desc);
    for (Class *k = c; k; k = k->super) {
        Method *m = find_declared_interned(k, name, desc);
        if (m) return m;
    }
    /* interfaces: prefer non-abstract (default) methods */
    Method *abstract_match = NULL;
    for (uint32_t i = 0; i < c->nall_interfaces; i++) {
        Method *m = find_declared_interned(c->all_interfaces[i], name, desc);
        if (m) {
            if (!(m->access & ACC_ABSTRACT)) return m;
            if (!abstract_match) abstract_match = m;
        }
    }
    if (abstract_match) return abstract_match;
    if ((c->access & ACC_INTERFACE) && g_vm.wk.Object) return find_declared_interned(g_vm.wk.Object, name, desc);
    return NULL;
}

Method *vm_find_virtual(Class *c, const char *name, const char *desc) {
    name = sa_intern(name);
    desc = sa_intern(desc);
    for (Class *k = c; k; k = k->super) {
        Method *m = find_declared_interned(k, name, desc);
        if (m && !(m->access & ACC_STATIC) && !(m->access & ACC_ABSTRACT)) return m;
    }
    for (uint32_t i = 0; i < c->nall_interfaces; i++) {
        Method *m = find_declared_interned(c->all_interfaces[i], name, desc);
        if (m && !(m->access & (ACC_ABSTRACT | ACC_STATIC))) return m;
    }
    return NULL;
}

Method *vm_find_interface_impl(Class *c, Method *im) {
    Method *m = sa_ptrmap_get(&c->itable_cache, im);
    if (m) return m;
    m = vm_find_virtual(c, im->name, im->desc);
    if (m) sa_ptrmap_put(&c->itable_cache, im, m);
    return m;
}

Field *vm_find_field(Class *c, const char *name, const char *type) {
    name = sa_intern(name);
    type = type ? sa_intern(type) : NULL;
    for (Class *k = c; k; k = k->super) {
        for (uint32_t i = 0; i < k->nifields; i++)
            if (k->ifields[i].name == name && (!type || k->ifields[i].type == type)) return &k->ifields[i];
        for (uint32_t i = 0; i < k->nsfields; i++)
            if (k->sfields[i].name == name && (!type || k->sfields[i].type == type)) return &k->sfields[i];
        for (uint32_t j = 0; j < k->ninterfaces; j++) {
            Field *f = vm_find_field(k->interfaces[j], name, type);
            if (f) return f;
        }
    }
    return NULL;
}

Field *vm_find_field_by_name(Class *c, const char *name) { return vm_find_field(c, name, NULL); }

/* ---- mirrors ---------------------------------------------------------------------- */

Object *vm_class_mirror(VMThread *t, Class *c) {
    if (c->mirror) return c->mirror;
    Class *cc = g_vm.wk.Class;
    if (!cc) return NULL;
    Object *m = vm_alloc_object(t, cc);
    if (!m) return NULL;
    vm_set_long(m, g_vm.wf.Class_vmClass, (int64_t)(intptr_t)c);
    c->mirror = m;
    return m;
}

Class *vm_class_from_mirror(Object *mirror) {
    if (!mirror) return NULL;
    return (Class *)(intptr_t)vm_get_long(mirror, g_vm.wf.Class_vmClass);
}

const char *vm_class_simple_name(Class *c) {
    const char *n = c->name;
    const char *dot = strrchr(n, '.');
    const char *s = dot ? dot + 1 : n;
    const char *dollar = strrchr(s, '$');
    return dollar ? dollar + 1 : s;
}

/* ---- initialization ------------------------------------------------------------- */

static bool apply_static_values(VMThread *t, Class *c) {
    DexClassDef cd;
    dex_class_def(c->dex, (uint32_t)c->class_def_idx, &cd);
    if (!cd.static_values_off) return true;
    const uint8_t *p = c->dex->base + cd.static_values_off;
    uint32_t n = dex_uleb128(&p);
    for (uint32_t i = 0; i < n && i < c->nsfields; i++) {
        DexEncodedValue v;
        dex_read_encoded_value(&p, &v);
        Field *f = &c->sfields[i];
        void *dst = vm_static_ptr(f);
        switch (v.type) {
        case DEV_BYTE:
        case DEV_SHORT:
        case DEV_CHAR:
        case DEV_INT: *(int32_t *)dst = v.u.i; break;
        case DEV_LONG: *(int64_t *)dst = v.u.j; break;
        case DEV_FLOAT: *(float *)dst = v.u.f; break;
        case DEV_DOUBLE: *(double *)dst = v.u.d; break;
        case DEV_BOOLEAN: *(int32_t *)dst = v.u.z; break;
        case DEV_NULL: *(Object **)dst = NULL; break;
        case DEV_STRING: {
            Object *s = vm_resolve_string(t, c->dex, v.u.idx);
            if (!s) return false;
            *(Object **)dst = s;
            break;
        }
        case DEV_TYPE: {
            Class *k = vm_resolve_type(t, c->dex, v.u.idx);
            if (!k) return false;
            *(Object **)dst = vm_class_mirror(t, k);
            break;
        }
        default: break;
        }
    }
    return true;
}

bool vm_init_class(VMThread *t, Class *c) {
    if (SA_LIKELY(c->state == CLASS_INITIALIZED)) return true;
    while (c->state == CLASS_INITIALIZING) {
        if (c->init_thread == t) return true;
        vm_gil_wait_global(t);
    }
    if (c->state == CLASS_INITIALIZED) return true;
    if (c->state == CLASS_ERROR) {
        vm_throw_new(t, "Ljava/lang/NoClassDefFoundError;", "Could not initialize class %s", c->name);
        return false;
    }
    c->state = CLASS_INITIALIZING;
    c->init_thread = t;
    bool ok = true;
    if (c->super && !vm_init_class(t, c->super)) ok = false;
    if (ok && c->dex && !apply_static_values(t, c)) ok = false;
    if (ok) {
        Method *clinit = vm_find_method(c, "<clinit>", "()V");
        if (clinit) {
            vm_invoke(t, clinit, NULL);
            if (t->exception) ok = false;
        }
    }
    if (!ok) {
        Object *exc = t->exception;
        LOGW("initialization of %s failed", c->name);
        if (exc) {
            /* Say why: later uses only see "Could not initialize class". */
            SaBuf b = {0};
            vm_describe_exception(t, exc, &b);
            char *s = sa_buf_cstr(&b);
            int lines = 0;
            for (char *line = s; line && *line && lines < 12; lines++) {
                char *nl = strchr(line, '\n');
                if (nl) *nl = 0;
                LOGW("  %s", line);
                line = nl ? nl + 1 : NULL;
            }
            sa_buf_free(&b);
            t->exception = exc;
        }
        c->state = CLASS_ERROR;
        if (exc && !vm_instance_of(exc, vm_find_class_noexc(t, "Ljava/lang/Error;"))) {
            t->exception = NULL;
            Class *eiie = vm_find_class(t, "Ljava/lang/ExceptionInInitializerError;");
            if (eiie) {
                Object *e = vm_new_instance(t, "Ljava/lang/ExceptionInInitializerError;", "(Ljava/lang/Throwable;)V", exc);
                if (e && !t->exception) t->exception = e;
            }
            if (!t->exception) t->exception = exc;
        }
    } else {
        c->state = CLASS_INITIALIZED;
    }
    c->init_thread = NULL;
    vm_gil_broadcast_global();
    return ok;
}

/* ---- resolution ------------------------------------------------------------------- */

Class *vm_resolve_type(VMThread *t, DexFile *d, uint32_t type_idx) {
    Class *c = d->resolved_types[type_idx];
    if (c) return c;
    c = vm_find_class(t, dex_type_desc(d, type_idx));
    if (c) d->resolved_types[type_idx] = c;
    return c;
}

static void stub_native(VMThread *t, uint64_t *args, JValue *ret) {
    SA_UNUSED(t);
    SA_UNUSED(args);
    ret->raw = 0;
}

static Method *make_stub_method(Class *c, const char *name, const char *desc, const char *shorty, bool is_static) {
    Method *m = sa_calloc(1, sizeof *m);
    m->clazz = c;
    m->name = sa_intern(name);
    m->desc = sa_intern(desc);
    m->shorty = sa_intern(shorty);
    m->access = ACC_PUBLIC | ACC_NATIVE | (is_static ? ACC_STATIC : 0);
    m->arg_slots = (uint16_t)vm_shorty_slots(shorty, is_static);
    m->vtable_index = -1;
    m->stub = true;
    m->native = stub_native;
    char pretty[512];
    vm_method_pretty(m, pretty, sizeof pretty);
    LOGW("STUB: missing framework method %s (returns default)", pretty);
    return m;
}

static bool stub_allowed(Class *c) {
    return g_vm.stub_missing_framework && (c->flags & CF_BOOT) &&
           (!strncmp(c->descriptor, "Landroid/", 9) || !strncmp(c->descriptor, "Lcom/android/", 13) ||
            !strncmp(c->descriptor, "Ldalvik/", 8));
}

Method *vm_resolve_method(VMThread *t, DexFile *d, uint32_t method_idx, bool is_static, bool is_interface) {
    Method *m = d->resolved_methods[method_idx];
    if (m) return m;
    DexMethodId mid;
    dex_method_id(d, method_idx, &mid);
    Class *c = vm_resolve_type(t, d, mid.class_idx);
    if (!c) return NULL;
    const char *name = dex_string(d, mid.name_idx);
    const char *desc = dex_proto_desc(d, mid.proto_idx);
    SA_UNUSED(is_interface);
    m = vm_find_method_hier(c, name, desc);
    if (!m) {
        if (stub_allowed(c)) {
            m = make_stub_method(c, name, desc, dex_proto_shorty(d, mid.proto_idx), is_static);
        } else {
            vm_throw_new(t, "Ljava/lang/NoSuchMethodError;", "%s.%s%s", c->name, name, desc);
            return NULL;
        }
    }
    d->resolved_methods[method_idx] = m;
    return m;
}

Field *vm_resolve_field(VMThread *t, DexFile *d, uint32_t field_idx, bool is_static) {
    Field *f = d->resolved_fields[field_idx];
    if (f) return f;
    DexFieldId fid;
    dex_field_id(d, field_idx, &fid);
    Class *c = vm_resolve_type(t, d, fid.class_idx);
    if (!c) return NULL;
    const char *name = dex_string(d, fid.name_idx);
    const char *type = dex_type_desc(d, fid.type_idx);
    f = vm_find_field(c, name, type);
    if (!f && is_static && stub_allowed(c)) {
        /* synthesize a zero-valued static so reads of unknown constants work */
        LOGW("STUB: missing framework field %s.%s (%s)", c->name, name, type);
        Class *holder = sa_calloc(1, sizeof(Class));
        holder->descriptor = c->descriptor;
        holder->name = c->name;
        holder->state = CLASS_INITIALIZED;
        holder->static_data = sa_calloc(1, 8);
        holder->flags = CF_BOOT | CF_STUB;
        f = sa_calloc(1, sizeof *f);
        f->clazz = holder;
        f->name = name;
        f->type = type;
        f->kind = type[0];
        f->access = ACC_PUBLIC | ACC_STATIC;
        f->offset = 0;
    }
    if (!f) {
        vm_throw_new(t, "Ljava/lang/NoSuchFieldError;", "%s.%s:%s", c->name, name, type);
        return NULL;
    }
    if (((f->access & ACC_STATIC) != 0) != is_static) {
        vm_throw_new(t, "Ljava/lang/IncompatibleClassChangeError;", "field %s.%s static mismatch", c->name, name);
        return NULL;
    }
    d->resolved_fields[field_idx] = f;
    return f;
}

Object *vm_resolve_string(VMThread *t, DexFile *d, uint32_t string_idx) {
    Object *s = d->resolved_strings[string_idx];
    if (s) return s;
    const char *utf = dex_string(d, string_idx);
    Object *str = vm_new_string_mutf8(t, utf);
    if (!str) return NULL;
    s = vm_intern_string(t, str);
    d->resolved_strings[string_idx] = s;
    return s;
}

/* ---- field access helpers ---------------------------------------------------------- */

JValue vm_field_get(Object *o, Field *f) {
    JValue v;
    v.raw = 0;
    void *p = (f->access & ACC_STATIC) ? vm_static_ptr(f) : vm_field_ptr(o, f);
    switch (f->kind) {
    case 'Z': v.i = *(uint8_t *)p; break;
    case 'B': v.i = *(int8_t *)p; break;
    case 'C': v.i = *(uint16_t *)p; break;
    case 'S': v.i = *(int16_t *)p; break;
    case 'I':
    case 'F': v.i = *(int32_t *)p; break;
    case 'J':
    case 'D': v.j = *(int64_t *)p; break;
    default: v.l = *(Object **)p; break;
    }
    return v;
}

void vm_field_set(Object *o, Field *f, JValue v) {
    void *p = (f->access & ACC_STATIC) ? vm_static_ptr(f) : vm_field_ptr(o, f);
    switch (f->kind) {
    case 'Z':
    case 'B': *(int8_t *)p = (int8_t)v.i; break;
    case 'C':
    case 'S': *(int16_t *)p = (int16_t)v.i; break;
    case 'I':
    case 'F': *(int32_t *)p = v.i; break;
    case 'J':
    case 'D': *(int64_t *)p = v.j; break;
    default: *(Object **)p = v.l; break;
    }
}

Object *vm_get_ref_by_name(Object *o, const char *name) {
    Field *f = vm_find_field(o->clazz, name, NULL);
    return f ? vm_get_ref(o, f) : NULL;
}

int32_t vm_get_int_by_name(Object *o, const char *name) {
    Field *f = vm_find_field(o->clazz, name, NULL);
    return f ? vm_field_get(o, f).i : 0;
}

float vm_get_float_by_name(Object *o, const char *name) {
    Field *f = vm_find_field(o->clazz, name, NULL);
    return f ? vm_field_get(o, f).f : 0.0f;
}

void vm_set_ref_by_name(Object *o, const char *name, Object *v) {
    Field *f = vm_find_field(o->clazz, name, NULL);
    if (f) vm_set_ref(o, f, v);
}

void vm_set_int_by_name(Object *o, const char *name, int32_t v) {
    Field *f = vm_find_field(o->clazz, name, NULL);
    if (f) {
        JValue jv;
        jv.raw = 0;
        jv.i = v;
        vm_field_set(o, f, jv);
    }
}

void vm_add_boot_dex(DexFile *d) { sa_vec_push(&g_vm.boot_dex, d); }
void vm_add_app_dex(DexFile *d) { sa_vec_push(&g_vm.app_dex, d); }
