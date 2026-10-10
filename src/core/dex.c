#include "dex.h"

#define LOG_TAG "dex"

uint32_t dex_uleb128(const uint8_t **pp) {
    const uint8_t *p = *pp;
    uint32_t r = 0;
    int shift = 0;
    uint8_t b;
    do {
        b = *p++;
        r |= (uint32_t)(b & 0x7f) << shift;
        shift += 7;
    } while ((b & 0x80) && shift < 35);
    *pp = p;
    return r;
}

int32_t dex_sleb128(const uint8_t **pp) {
    const uint8_t *p = *pp;
    int32_t r = 0;
    int shift = 0;
    uint8_t b;
    do {
        b = *p++;
        r |= (int32_t)((uint32_t)(b & 0x7f) << shift);
        shift += 7;
    } while ((b & 0x80) && shift < 35);
    if (shift < 32 && (b & 0x40)) r |= -(1 << shift);
    *pp = p;
    return r;
}

DexFile *dex_open(const uint8_t *data, size_t len, bool copy, const char *location) {
    if (len < 0x70 || memcmp(data, "dex\n", 4) != 0) {
        LOGE("%s: bad dex magic", location);
        return NULL;
    }
    int ver = atoi((const char *)data + 4);
    if (ver < 35 || ver > 41) LOGW("%s: unusual dex version %03d", location, ver);
    DexFile *d = sa_calloc(1, sizeof *d);
    if (copy) {
        uint8_t *c = sa_malloc(len);
        memcpy(c, data, len);
        d->base = c;
        d->owned = true;
    } else {
        d->base = data;
    }
    d->size = len;
    d->location = sa_strdup(location ? location : "?");
    const uint8_t *h = d->base;
    uint32_t file_size = sa_rd32(h + 0x20);
    if (ver >= 41 && file_size < len) {
        /* container dex (v41): only parse the first dex in the container */
        d->size = file_size;
    }
#define SECT(name, off)                             \
    d->name##_size = sa_rd32(h + off);              \
    d->name = d->base + sa_rd32(h + off + 4);
    SECT(string_ids, 0x38);
    SECT(type_ids, 0x40);
    SECT(proto_ids, 0x48);
    SECT(field_ids, 0x50);
    SECT(method_ids, 0x58);
    SECT(class_defs, 0x60);
#undef SECT
    d->string_cache = sa_calloc(d->string_ids_size + 1, sizeof(char *));
    d->resolved_types = sa_calloc(d->type_ids_size + 1, sizeof(void *));
    d->resolved_methods = sa_calloc(d->method_ids_size + 1, sizeof(void *));
    d->resolved_fields = sa_calloc(d->field_ids_size + 1, sizeof(void *));
    d->resolved_strings = sa_calloc(d->string_ids_size + 1, sizeof(void *));
    for (uint32_t i = 0; i < d->class_defs_size; i++) {
        DexClassDef cd;
        dex_class_def(d, i, &cd);
        const char *desc = dex_type_desc(d, cd.class_idx);
        if (!sa_map_has(&d->class_index, desc)) sa_map_put(&d->class_index, desc, (void *)(uintptr_t)(i + 1));
    }
    LOGD("%s: dex v%03d, %u classes, %u methods", d->location, ver, d->class_defs_size, d->method_ids_size);
    return d;
}

void dex_close(DexFile *d) {
    if (!d) return;
    if (d->owned) free((void *)d->base);
    free(d->location);
    free(d->string_cache);
    free(d->resolved_types);
    free(d->resolved_methods);
    free(d->resolved_fields);
    free(d->resolved_strings);
    sa_map_free(&d->class_index, NULL);
    free(d);
}

const char *dex_string(DexFile *d, uint32_t idx) {
    if (idx >= d->string_ids_size) return "";
    const char *s = d->string_cache[idx];
    if (s) return s;
    uint32_t off = sa_rd32(d->string_ids + idx * 4);
    const uint8_t *p = d->base + off;
    dex_uleb128(&p); /* utf16 length */
    s = sa_intern((const char *)p);
    d->string_cache[idx] = s;
    return s;
}

const char *dex_type_desc(DexFile *d, uint32_t type_idx) {
    if (type_idx >= d->type_ids_size) return "V";
    return dex_string(d, sa_rd32(d->type_ids + type_idx * 4));
}

void dex_proto(const DexFile *d, uint32_t idx, DexProtoId *out) {
    const uint8_t *p = d->proto_ids + idx * 12;
    out->shorty_idx = sa_rd32(p);
    out->return_type_idx = sa_rd32(p + 4);
    out->params_off = sa_rd32(p + 8);
}

void dex_field_id(const DexFile *d, uint32_t idx, DexFieldId *out) {
    const uint8_t *p = d->field_ids + idx * 8;
    out->class_idx = sa_rd16(p);
    out->type_idx = sa_rd16(p + 2);
    out->name_idx = sa_rd32(p + 4);
}

void dex_method_id(const DexFile *d, uint32_t idx, DexMethodId *out) {
    const uint8_t *p = d->method_ids + idx * 8;
    out->class_idx = sa_rd16(p);
    out->proto_idx = sa_rd16(p + 2);
    out->name_idx = sa_rd32(p + 4);
}

void dex_class_def(const DexFile *d, uint32_t idx, DexClassDef *out) {
    const uint8_t *p = d->class_defs + idx * 32;
    out->class_idx = sa_rd32(p);
    out->access_flags = sa_rd32(p + 4);
    out->superclass_idx = sa_rd32(p + 8);
    out->interfaces_off = sa_rd32(p + 12);
    out->source_file_idx = sa_rd32(p + 16);
    out->annotations_off = sa_rd32(p + 20);
    out->class_data_off = sa_rd32(p + 24);
    out->static_values_off = sa_rd32(p + 28);
}

uint32_t dex_type_list_size(const DexFile *d, uint32_t off) { return off ? sa_rd32(d->base + off) : 0; }

uint32_t dex_type_list_item(const DexFile *d, uint32_t off, uint32_t i) { return sa_rd16(d->base + off + 4 + i * 2); }

/* Long descriptors (Kotlin reflection has return types of 100+ characters) are built on the heap. */
static const char *proto_desc_heap(DexFile *d, const DexProtoId *p, uint32_t cnt) {
    SaBuf b = {0};
    sa_buf_putc(&b, '(');
    for (uint32_t k = 0; k < cnt; k++) sa_buf_puts(&b, dex_type_desc(d, dex_type_list_item(d, p->params_off, k)));
    sa_buf_putc(&b, ')');
    sa_buf_puts(&b, dex_type_desc(d, p->return_type_idx));
    const char *r = sa_intern(sa_buf_cstr(&b));
    sa_buf_free(&b);
    return r;
}

const char *dex_proto_desc(DexFile *d, uint32_t proto_idx) {
    DexProtoId p;
    dex_proto(d, proto_idx, &p);
    char buf[512];
    size_t n = 0;
    buf[n++] = '(';
    uint32_t cnt = dex_type_list_size(d, p.params_off);
    for (uint32_t i = 0; i < cnt; i++) {
        const char *t = dex_type_desc(d, dex_type_list_item(d, p.params_off, i));
        size_t tl = strlen(t);
        if (n + tl + 2 > sizeof buf) return proto_desc_heap(d, &p, cnt);
        memcpy(buf + n, t, tl);
        n += tl;
    }
    const char *rt = dex_type_desc(d, p.return_type_idx);
    size_t rl = strlen(rt);
    if (n + 1 + rl + 1 > sizeof buf) return proto_desc_heap(d, &p, cnt);
    buf[n++] = ')';
    memcpy(buf + n, rt, rl);
    n += rl;
    buf[n] = 0;
    return sa_intern(buf);
}

const char *dex_proto_shorty(DexFile *d, uint32_t proto_idx) {
    DexProtoId p;
    dex_proto(d, proto_idx, &p);
    return dex_string(d, p.shorty_idx);
}

int32_t dex_find_class(const DexFile *d, const char *descriptor) {
    uintptr_t v = (uintptr_t)sa_map_get(&d->class_index, descriptor);
    return v ? (int32_t)(v - 1) : -1;
}

bool dex_class_data(const DexFile *d, uint32_t off, DexClassData *out) {
    memset(out, 0, sizeof *out);
    if (!off) return true;
    const uint8_t *p = d->base + off;
    out->static_fields_size = dex_uleb128(&p);
    out->instance_fields_size = dex_uleb128(&p);
    out->direct_methods_size = dex_uleb128(&p);
    out->virtual_methods_size = dex_uleb128(&p);
    out->static_fields = sa_calloc(out->static_fields_size + 1, sizeof(DexFieldEntry));
    out->instance_fields = sa_calloc(out->instance_fields_size + 1, sizeof(DexFieldEntry));
    out->direct_methods = sa_calloc(out->direct_methods_size + 1, sizeof(DexMethodEntry));
    out->virtual_methods = sa_calloc(out->virtual_methods_size + 1, sizeof(DexMethodEntry));
    uint32_t idx = 0;
    for (uint32_t i = 0; i < out->static_fields_size; i++) {
        idx += dex_uleb128(&p);
        out->static_fields[i].field_idx = idx;
        out->static_fields[i].access_flags = dex_uleb128(&p);
    }
    idx = 0;
    for (uint32_t i = 0; i < out->instance_fields_size; i++) {
        idx += dex_uleb128(&p);
        out->instance_fields[i].field_idx = idx;
        out->instance_fields[i].access_flags = dex_uleb128(&p);
    }
    idx = 0;
    for (uint32_t i = 0; i < out->direct_methods_size; i++) {
        idx += dex_uleb128(&p);
        out->direct_methods[i].method_idx = idx;
        out->direct_methods[i].access_flags = dex_uleb128(&p);
        out->direct_methods[i].code_off = dex_uleb128(&p);
    }
    idx = 0;
    for (uint32_t i = 0; i < out->virtual_methods_size; i++) {
        idx += dex_uleb128(&p);
        out->virtual_methods[i].method_idx = idx;
        out->virtual_methods[i].access_flags = dex_uleb128(&p);
        out->virtual_methods[i].code_off = dex_uleb128(&p);
    }
    return true;
}

void dex_class_data_free(DexClassData *cd) {
    free(cd->static_fields);
    free(cd->instance_fields);
    free(cd->direct_methods);
    free(cd->virtual_methods);
    memset(cd, 0, sizeof *cd);
}

bool dex_code(const DexFile *d, uint32_t off, DexCode *out) {
    memset(out, 0, sizeof *out);
    if (!off || off + 16 > d->size) return false;
    const uint8_t *p = d->base + off;
    out->registers_size = sa_rd16(p);
    out->ins_size = sa_rd16(p + 2);
    out->outs_size = sa_rd16(p + 4);
    out->tries_size = sa_rd16(p + 6);
    out->debug_info_off = sa_rd32(p + 8);
    out->insns_size = sa_rd32(p + 12);
    out->insns = (const uint16_t *)(p + 16);
    if (out->tries_size) {
        const uint8_t *t = p + 16 + (size_t)out->insns_size * 2;
        if (out->insns_size & 1) t += 2;
        out->tries = (const DexTry *)t;
        out->handlers = t + (size_t)out->tries_size * 8;
    }
    return true;
}

static int64_t read_signed(const uint8_t **pp, int size) {
    const uint8_t *p = *pp;
    uint64_t v = 0;
    for (int i = 0; i < size; i++) v |= (uint64_t)p[i] << (8 * i);
    int shift = 64 - 8 * size;
    *pp = p + size;
    return (int64_t)(v << shift) >> shift;
}

static uint64_t read_unsigned(const uint8_t **pp, int size, bool right_zero_extend) {
    const uint8_t *p = *pp;
    uint64_t v = 0;
    for (int i = 0; i < size; i++) v |= (uint64_t)p[i] << (8 * i);
    if (right_zero_extend) v <<= (8 - size) * 8;
    *pp = p + size;
    return v;
}

void dex_skip_annotation(const uint8_t **p) {
    dex_uleb128(p); /* type */
    uint32_t n = dex_uleb128(p);
    for (uint32_t i = 0; i < n; i++) {
        dex_uleb128(p);
        dex_skip_encoded_value(p);
    }
}

void dex_read_encoded_value(const uint8_t **pp, DexEncodedValue *out) {
    uint8_t hdr = *(*pp)++;
    uint8_t type = hdr & 0x1f;
    int arg = hdr >> 5;
    int size = arg + 1;
    memset(out, 0, sizeof *out);
    out->type = type;
    switch (type) {
    case DEV_BYTE: out->u.i = (int8_t)read_signed(pp, 1); break;
    case DEV_SHORT: out->u.i = (int16_t)read_signed(pp, size); break;
    case DEV_CHAR: out->u.i = (uint16_t)read_unsigned(pp, size, false); break;
    case DEV_INT: out->u.i = (int32_t)read_signed(pp, size); break;
    case DEV_LONG: out->u.j = read_signed(pp, size); break;
    case DEV_FLOAT: {
        uint32_t bits = (uint32_t)(read_unsigned(pp, size, true) >> 32);
        memcpy(&out->u.f, &bits, 4);
        break;
    }
    case DEV_DOUBLE: {
        uint64_t bits = read_unsigned(pp, size, true);
        memcpy(&out->u.d, &bits, 8);
        break;
    }
    case DEV_METHOD_TYPE:
    case DEV_METHOD_HANDLE:
    case DEV_STRING:
    case DEV_TYPE:
    case DEV_FIELD:
    case DEV_METHOD:
    case DEV_ENUM: out->u.idx = (uint32_t)read_unsigned(pp, size, false); break;
    case DEV_ARRAY: {
        out->array = *pp;
        uint32_t n = dex_uleb128(pp);
        for (uint32_t i = 0; i < n; i++) dex_skip_encoded_value(pp);
        break;
    }
    case DEV_ANNOTATION:
        out->array = *pp;
        dex_skip_annotation(pp);
        break;
    case DEV_NULL: break;
    case DEV_BOOLEAN: out->u.z = arg != 0; break;
    default: LOGW("unknown encoded value type 0x%x", type); break;
    }
}

void dex_skip_encoded_value(const uint8_t **p) {
    DexEncodedValue v;
    dex_read_encoded_value(p, &v);
}

const uint8_t *dex_find_try_handlers(const DexCode *code, uint32_t pc) {
    for (uint32_t i = 0; i < code->tries_size; i++) {
        const uint8_t *t = (const uint8_t *)code->tries + i * 8;
        uint32_t start = sa_rd32(t);
        uint16_t count = sa_rd16(t + 4);
        uint16_t hoff = sa_rd16(t + 6);
        if (pc >= start && pc < start + count) return code->handlers + hoff;
    }
    return NULL;
}

int dex_line_for_pc(DexFile *d, const DexCode *code, uint32_t pc) {
    if (!code->debug_info_off || code->debug_info_off >= d->size) return -1;
    const uint8_t *p = d->base + code->debug_info_off;
    int line = (int)dex_uleb128(&p);
    uint32_t nparams = dex_uleb128(&p);
    for (uint32_t i = 0; i < nparams; i++) dex_uleb128(&p);
    uint32_t addr = 0;
    int best = -1;
    for (;;) {
        uint8_t op = *p++;
        switch (op) {
        case 0x00: return best >= 0 ? best : line;
        case 0x01: addr += dex_uleb128(&p); break;
        case 0x02: line += dex_sleb128(&p); break;
        case 0x03:
            dex_uleb128(&p);
            dex_uleb128(&p);
            dex_uleb128(&p);
            break;
        case 0x04:
            dex_uleb128(&p);
            dex_uleb128(&p);
            dex_uleb128(&p);
            dex_uleb128(&p);
            break;
        case 0x05:
        case 0x06: dex_uleb128(&p); break;
        case 0x07:
        case 0x08: break;
        case 0x09: dex_uleb128(&p); break;
        default: {
            int adj = op - 0x0a;
            addr += (uint32_t)(adj / 15);
            line += -4 + adj % 15;
            if (addr > pc) return best >= 0 ? best : line;
            best = line;
            break;
        }
        }
    }
}

void dex_class_annotations(DexFile *d, const DexClassDef *cd, DexAnnotationFn fn, void *ctx) {
    if (!cd->annotations_off) return;
    const uint8_t *dir = d->base + cd->annotations_off;
    uint32_t class_ann_off = sa_rd32(dir);
    if (!class_ann_off) return;
    const uint8_t *set = d->base + class_ann_off;
    uint32_t n = sa_rd32(set);
    for (uint32_t i = 0; i < n; i++) {
        uint32_t item_off = sa_rd32(set + 4 + i * 4);
        const uint8_t *item = d->base + item_off;
        uint8_t vis = *item++;
        const uint8_t *q = item;
        uint32_t type_idx = dex_uleb128(&q);
        fn(d, dex_type_desc(d, type_idx), vis, q, ctx);
    }
}
