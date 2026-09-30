#include "res.h"

#include <math.h>

#define LOG_TAG "res"

enum {
    CHUNK_NULL = 0x0000,
    CHUNK_STRING_POOL = 0x0001,
    CHUNK_TABLE = 0x0002,
    CHUNK_XML = 0x0003,
    CHUNK_XML_START_NS = 0x0100,
    CHUNK_XML_END_NS = 0x0101,
    CHUNK_XML_START_ELEM = 0x0102,
    CHUNK_XML_END_ELEM = 0x0103,
    CHUNK_XML_CDATA = 0x0104,
    CHUNK_XML_RESMAP = 0x0180,
    CHUNK_TABLE_PACKAGE = 0x0200,
    CHUNK_TABLE_TYPE = 0x0201,
    CHUNK_TABLE_TYPE_SPEC = 0x0202,
    CHUNK_TABLE_LIBRARY = 0x0203,
    CHUNK_TABLE_OVERLAYABLE = 0x0204,
    CHUNK_TABLE_STAGED_ALIAS = 0x0206,
};

/* ======================================================================
 * String pool
 * ====================================================================== */

bool res_pool_init(ResStringPool *p, const uint8_t *chunk, size_t avail) {
    memset(p, 0, sizeof *p);
    if (avail < 28 || sa_rd16(chunk) != CHUNK_STRING_POOL) return false;
    uint32_t size = sa_rd32(chunk + 4);
    if (size > avail) return false;
    uint16_t hsize = sa_rd16(chunk + 2);
    p->count = sa_rd32(chunk + 8);
    uint32_t flags = sa_rd32(chunk + 16);
    p->strings_start = sa_rd32(chunk + 20);
    p->utf8 = (flags & (1 << 8)) != 0;
    p->base = chunk;
    p->size = size;
    if ((uint64_t)hsize + (uint64_t)p->count * 4 > size) return false;
    p->offsets = (const uint32_t *)(chunk + hsize);
    p->strings = sa_calloc(p->count ? p->count : 1, sizeof(char *));
    return true;
}

static uint32_t pool_len8(const uint8_t **pp) {
    const uint8_t *p = *pp;
    uint32_t len = p[0];
    if (len & 0x80) {
        len = ((len & 0x7f) << 8) | p[1];
        p += 2;
    } else {
        p += 1;
    }
    *pp = p;
    return len;
}

static uint32_t pool_len16(const uint8_t **pp) {
    const uint8_t *p = *pp;
    uint32_t len = sa_rd16(p);
    if (len & 0x8000) {
        len = ((len & 0x7fff) << 16) | sa_rd16(p + 2);
        p += 4;
    } else {
        p += 2;
    }
    *pp = p;
    return len;
}

const char *res_pool_get(ResStringPool *p, uint32_t idx) {
    if (idx >= p->count) return NULL;
    if (p->strings[idx]) return p->strings[idx];
    uint32_t off = sa_rd32((const uint8_t *)&p->offsets[idx]);
    const uint8_t *s = p->base + p->strings_start + off;
    const uint8_t *end = p->base + p->size;
    if (s >= end) return NULL;
    char *out;
    if (p->utf8) {
        pool_len8(&s); /* utf16 length */
        uint32_t n = pool_len8(&s);
        if (s + n > end) n = (uint32_t)(end - s);
        out = sa_strndup((const char *)s, n);
    } else {
        uint32_t n = pool_len16(&s);
        if (s + n * 2 > end) n = (uint32_t)((end - s) / 2);
        uint16_t *u = sa_malloc(n * 2 + 2);
        for (uint32_t i = 0; i < n; i++) u[i] = sa_rd16(s + i * 2);
        out = sa_utf16_to_utf8(u, n, NULL);
        free(u);
    }
    p->strings[idx] = out;
    return out;
}

void res_pool_free(ResStringPool *p) {
    if (p->strings) {
        for (uint32_t i = 0; i < p->count; i++) free(p->strings[i]);
        free(p->strings);
    }
    memset(p, 0, sizeof *p);
}

static void read_value(ResStringPool *pool, const uint8_t *v, ResValue *out) {
    out->type = v[3];
    out->data = sa_rd32(v + 4);
    out->string = (out->type == RV_STRING && pool) ? res_pool_get(pool, out->data) : NULL;
}

/* ======================================================================
 * Binary XML
 * ====================================================================== */

static XmlNode *node_new(void) { return sa_calloc(1, sizeof(XmlNode)); }

static void node_add_child(XmlNode *parent, XmlNode *child) {
    parent->children = sa_realloc(parent->children, (parent->nchildren + 1) * sizeof(XmlNode *));
    parent->children[parent->nchildren++] = child;
    child->parent = parent;
}

static void node_free(XmlNode *n) {
    if (!n) return;
    for (uint32_t i = 0; i < n->nchildren; i++) node_free(n->children[i]);
    free(n->children);
    free(n->attrs);
    free(n->text);
    free(n);
}

static XmlDoc *text_xml_parse(const uint8_t *data, size_t len);

XmlDoc *axml_parse(const uint8_t *data, size_t len) {
    if (len >= 1 && data[0] == '<') return text_xml_parse(data, len);
    if (len < 8 || sa_rd16(data) != CHUNK_XML) {
        LOGE("not a binary XML document");
        return NULL;
    }
    XmlDoc *doc = sa_calloc(1, sizeof *doc);
    doc->data = sa_malloc(len);
    memcpy(doc->data, data, len);
    const uint8_t *d = doc->data;
    uint32_t total = sa_rd32(d + 4);
    if (total > len) total = (uint32_t)len;
    size_t pos = sa_rd16(d + 2);
    const uint32_t *resmap = NULL;
    uint32_t resmap_count = 0;
    XmlNode *root = node_new();
    root->name = "#document";
    XmlNode *cur = root;
    while (pos + 8 <= total) {
        const uint8_t *c = d + pos;
        uint16_t type = sa_rd16(c);
        uint16_t hsize = sa_rd16(c + 2);
        uint32_t csize = sa_rd32(c + 4);
        if (csize < 8 || pos + csize > total) break;
        switch (type) {
        case CHUNK_STRING_POOL:
            if (!res_pool_init(&doc->pool, c, total - pos)) LOGW("bad string pool");
            break;
        case CHUNK_XML_RESMAP:
            resmap = (const uint32_t *)(c + hsize);
            resmap_count = (csize - hsize) / 4;
            break;
        case CHUNK_XML_START_ELEM: {
            const uint8_t *ext = c + hsize;
            XmlNode *n = node_new();
            n->line = sa_rd32(c + 8);
            uint32_t ns = sa_rd32(ext);
            n->ns = ns == 0xffffffff ? NULL : res_pool_get(&doc->pool, ns);
            n->name = res_pool_get(&doc->pool, sa_rd32(ext + 4));
            if (!n->name) n->name = "";
            uint16_t astart = sa_rd16(ext + 8);
            uint16_t asize = sa_rd16(ext + 10);
            uint16_t acount = sa_rd16(ext + 12);
            n->nattrs = acount;
            n->attrs = sa_calloc(acount ? acount : 1, sizeof(XmlAttr));
            for (uint16_t i = 0; i < acount; i++) {
                const uint8_t *a = ext + astart + (size_t)i * asize;
                if (a + 20 > d + total) break;
                XmlAttr *at = &n->attrs[i];
                uint32_t ans = sa_rd32(a);
                uint32_t aname = sa_rd32(a + 4);
                uint32_t araw = sa_rd32(a + 8);
                at->ns = ans == 0xffffffff ? NULL : res_pool_get(&doc->pool, ans);
                at->name = res_pool_get(&doc->pool, aname);
                if (!at->name) at->name = "";
                at->res_id = (resmap && aname < resmap_count) ? sa_rd32((const uint8_t *)&resmap[aname]) : 0;
                at->raw = araw == 0xffffffff ? NULL : res_pool_get(&doc->pool, araw);
                read_value(&doc->pool, a + 12, &at->value);
            }
            node_add_child(cur, n);
            cur = n;
            break;
        }
        case CHUNK_XML_END_ELEM:
            if (cur->parent) cur = cur->parent;
            break;
        case CHUNK_XML_CDATA: {
            const char *s = res_pool_get(&doc->pool, sa_rd32(c + hsize));
            if (s) {
                size_t ol = cur->text ? strlen(cur->text) : 0;
                cur->text = sa_realloc(cur->text, ol + strlen(s) + 1);
                strcpy(cur->text + ol, s);
            }
            break;
        }
        default:
            break;
        }
        pos += csize;
    }
    doc->root = root->nchildren ? root->children[0] : NULL;
    if (doc->root) {
        doc->root->parent = NULL;
        root->nchildren = 0;
    }
    node_free(root);
    if (!doc->root) {
        axml_free(doc);
        return NULL;
    }
    return doc;
}

void axml_free(XmlDoc *doc) {
    if (!doc) return;
    node_free(doc->root);
    res_pool_free(&doc->pool);
    free(doc->data);
    free(doc);
}

/* ---- minimal text XML parser (for uncompiled XML assets) ------------------- */

typedef struct {
    const char *p, *end;
    SaVec owned; /* strings owned by the doc; stored in pool.strings */
} TxtParser;

static const char *txt_own(TxtParser *tp, char *s) {
    sa_vec_push(&tp->owned, s);
    return s;
}

static void txt_skip_ws(TxtParser *tp) {
    while (tp->p < tp->end && (*tp->p == ' ' || *tp->p == '\t' || *tp->p == '\n' || *tp->p == '\r')) tp->p++;
}

static char *txt_unescape(const char *s, size_t n) {
    SaBuf b = {0};
    for (size_t i = 0; i < n; i++) {
        if (s[i] == '&') {
            const char *semi = memchr(s + i, ';', n - i);
            if (semi) {
                size_t el = (size_t)(semi - (s + i));
                const char *ent = s + i + 1;
                if (el == 3 && !strncmp(ent, "lt", 2)) sa_buf_putc(&b, '<');
                else if (el == 3 && !strncmp(ent, "gt", 2)) sa_buf_putc(&b, '>');
                else if (el == 4 && !strncmp(ent, "amp", 3)) sa_buf_putc(&b, '&');
                else if (el == 5 && !strncmp(ent, "quot", 4)) sa_buf_putc(&b, '"');
                else if (el == 5 && !strncmp(ent, "apos", 4)) sa_buf_putc(&b, '\'');
                else if (ent[0] == '#') {
                    unsigned long cp = ent[1] == 'x' ? strtoul(ent + 2, NULL, 16) : strtoul(ent + 1, NULL, 10);
                    uint16_t u = (uint16_t)cp;
                    char *u8 = sa_utf16_to_utf8(&u, 1, NULL);
                    sa_buf_puts(&b, u8);
                    free(u8);
                } else {
                    sa_buf_append(&b, s + i, el + 1);
                }
                i += el;
                continue;
            }
        }
        sa_buf_putc(&b, s[i]);
    }
    return sa_buf_cstr(&b);
}

static XmlNode *txt_parse_element(TxtParser *tp, XmlNode *parent) {
    /* at '<' */
    tp->p++;
    const char *ns = tp->p;
    while (tp->p < tp->end && !strchr(" \t\r\n/>", *tp->p)) tp->p++;
    XmlNode *n = node_new();
    n->name = txt_own(tp, sa_strndup(ns, (size_t)(tp->p - ns)));
    SaVec attrs = {0};
    for (;;) {
        txt_skip_ws(tp);
        if (tp->p >= tp->end) break;
        if (*tp->p == '/') {
            tp->p += 2;
            goto done_selfclose;
        }
        if (*tp->p == '>') {
            tp->p++;
            break;
        }
        const char *an = tp->p;
        while (tp->p < tp->end && !strchr(" \t\r\n=", *tp->p)) tp->p++;
        char *aname = sa_strndup(an, (size_t)(tp->p - an));
        txt_skip_ws(tp);
        if (tp->p < tp->end && *tp->p == '=') tp->p++;
        txt_skip_ws(tp);
        char q = tp->p < tp->end ? *tp->p++ : '"';
        const char *av = tp->p;
        while (tp->p < tp->end && *tp->p != q) tp->p++;
        char *aval = txt_unescape(av, (size_t)(tp->p - av));
        tp->p++;
        XmlAttr *at = sa_calloc(1, sizeof *at);
        char *colon = strchr(aname, ':');
        if (colon) {
            *colon = 0;
            at->ns = strcmp(aname, "android") == 0 ? ANDROID_NS : txt_own(tp, sa_strdup(aname));
            at->name = txt_own(tp, sa_strdup(colon + 1));
            free(aname);
        } else {
            at->name = txt_own(tp, aname);
        }
        at->raw = txt_own(tp, aval);
        at->value.type = RV_STRING;
        at->value.string = at->raw;
        sa_vec_push(&attrs, at);
    }
    /* children */
    for (;;) {
        const char *t = tp->p;
        while (tp->p < tp->end && *tp->p != '<') tp->p++;
        if (tp->p > t) {
            char *txt = txt_unescape(t, (size_t)(tp->p - t));
            bool blank = true;
            for (char *c = txt; *c; c++)
                if (!strchr(" \t\r\n", *c)) blank = false;
            if (!blank) {
                size_t ol = n->text ? strlen(n->text) : 0;
                n->text = sa_realloc(n->text, ol + strlen(txt) + 1);
                strcpy(n->text + ol, txt);
            }
            free(txt);
        }
        if (tp->p >= tp->end) break;
        if (tp->p[1] == '/') {
            while (tp->p < tp->end && *tp->p != '>') tp->p++;
            tp->p++;
            break;
        }
        if (tp->p[1] == '!') {
            const char *e = strstr(tp->p, "-->");
            tp->p = e ? e + 3 : tp->end;
            continue;
        }
        node_add_child(n, txt_parse_element(tp, n));
    }
    goto finish;
done_selfclose:
finish:
    n->nattrs = (uint32_t)attrs.len;
    n->attrs = sa_calloc(attrs.len ? attrs.len : 1, sizeof(XmlAttr));
    for (size_t i = 0; i < attrs.len; i++) {
        n->attrs[i] = *(XmlAttr *)attrs.items[i];
        free(attrs.items[i]);
    }
    sa_vec_free(&attrs);
    SA_UNUSED(parent);
    return n;
}

static XmlDoc *text_xml_parse(const uint8_t *data, size_t len) {
    TxtParser tp = {(const char *)data, (const char *)data + len, {0}};
    for (;;) {
        while (tp.p < tp.end && *tp.p != '<') tp.p++;
        if (tp.p + 1 >= tp.end) return NULL;
        if (tp.p[1] == '?' || tp.p[1] == '!') {
            const char *e = memchr(tp.p, '>', (size_t)(tp.end - tp.p));
            tp.p = e ? e + 1 : tp.end;
            continue;
        }
        break;
    }
    XmlDoc *doc = sa_calloc(1, sizeof *doc);
    doc->root = txt_parse_element(&tp, NULL);
    /* keep owned strings alive through the pool's string array */
    doc->pool.count = (uint32_t)tp.owned.len;
    doc->pool.strings = (char **)tp.owned.items;
    return doc;
}

const XmlAttr *xml_attr(const XmlNode *n, const char *ns, const char *name) {
    for (uint32_t i = 0; i < n->nattrs; i++) {
        const XmlAttr *a = &n->attrs[i];
        if (strcmp(a->name, name) != 0) continue;
        if (ns == NULL || (a->ns && strcmp(a->ns, ns) == 0)) return a;
    }
    return NULL;
}

const XmlAttr *xml_android_attr(const XmlNode *n, const char *name) {
    const XmlAttr *a = xml_attr(n, ANDROID_NS, name);
    if (a) return a;
    /* Some obfuscated APKs strip attribute names; nothing more we can do
     * without the framework attribute table. */
    return NULL;
}

const char *xml_attr_str(const XmlNode *n, const char *ns, const char *name) {
    const XmlAttr *a = xml_attr(n, ns, name);
    if (!a) return NULL;
    if (a->raw) return a->raw;
    if (a->value.type == RV_STRING) return a->value.string;
    return NULL;
}

XmlNode *xml_child(const XmlNode *n, const char *name) {
    for (uint32_t i = 0; i < n->nchildren; i++)
        if (strcmp(n->children[i]->name, name) == 0) return n->children[i];
    return NULL;
}

void xml_dump(const XmlNode *n, int depth, FILE *out) {
    fprintf(out, "%*s<%s", depth * 2, "", n->name);
    for (uint32_t i = 0; i < n->nattrs; i++) {
        const XmlAttr *a = &n->attrs[i];
        char *v = res_value_to_string(&a->value);
        fprintf(out, " %s%s=\"%s\"", a->ns && strcmp(a->ns, ANDROID_NS) == 0 ? "android:" : "", a->name,
                a->raw ? a->raw : v);
        free(v);
    }
    if (!n->nchildren && !n->text) {
        fprintf(out, "/>\n");
        return;
    }
    fprintf(out, ">\n");
    if (n->text) fprintf(out, "%*s%s\n", depth * 2 + 2, "", n->text);
    for (uint32_t i = 0; i < n->nchildren; i++) xml_dump(n->children[i], depth + 1, out);
    fprintf(out, "%*s</%s>\n", depth * 2, "", n->name);
}

/* ======================================================================
 * Resource table
 * ====================================================================== */

void res_config_default(ResConfig *c) {
    memset(c, 0, sizeof *c);
    strcpy(c->language, "en");
    strcpy(c->country, "US");
    c->orientation = 2;
    c->density = 240;
    c->sdk = 29;
    c->screen_width_dp = 853;
    c->screen_height_dp = 480;
    c->smallest_width_dp = 480;
    c->ui_mode_night = 1;
    c->ui_mode_type = 1;
}

static void read_config(const uint8_t *p, size_t avail, ResTableConfig *c) {
    uint8_t buf[64];
    memset(buf, 0, sizeof buf);
    uint32_t sz = sa_rd32(p);
    size_t n = sz < sizeof buf ? sz : sizeof buf;
    if (n > avail) n = avail;
    memcpy(buf, p, n);
    memset(c, 0, sizeof *c);
    c->size = sz;
    c->mcc = sa_rd16(buf + 4);
    c->mnc = sa_rd16(buf + 6);
    memcpy(c->language, buf + 8, 2);
    memcpy(c->country, buf + 10, 2);
    c->orientation = buf[12];
    c->touchscreen = buf[13];
    c->density = sa_rd16(buf + 14);
    c->keyboard = buf[16];
    c->navigation = buf[17];
    c->input_flags = buf[18];
    c->screen_width = sa_rd16(buf + 20);
    c->screen_height = sa_rd16(buf + 22);
    c->sdk_version = sa_rd16(buf + 24);
    c->minor_version = sa_rd16(buf + 26);
    c->screen_layout = buf[28];
    c->ui_mode = buf[29];
    c->smallest_width_dp = sa_rd16(buf + 30);
    c->screen_width_dp = sa_rd16(buf + 32);
    c->screen_height_dp = sa_rd16(buf + 34);
}

static ResType *pkg_type(ResPackage *pkg, uint8_t id, uint32_t count) {
    ResType *t = pkg->types[id];
    if (!t) {
        t = sa_calloc(1, sizeof *t);
        pkg->types[id] = t;
        t->type_name = res_pool_get(&pkg->type_strings, (uint32_t)id - 1);
    }
    if (count > t->count) {
        t->entries = sa_realloc(t->entries, count * sizeof(ResEntryDef *));
        t->names = sa_realloc(t->names, count * sizeof(char *));
        for (uint32_t i = t->count; i < count; i++) {
            t->entries[i] = NULL;
            t->names[i] = NULL;
        }
        t->count = count;
    }
    return t;
}

static void parse_type_chunk(ResStringPool *values, ResPackage *pkg, const uint8_t *c, size_t csize) {
    uint16_t hsize = sa_rd16(c + 2);
    uint8_t id = c[8];
    uint8_t flags = c[9];
    uint32_t count = sa_rd32(c + 12);
    uint32_t entries_start = sa_rd32(c + 16);
    if (id == 0 || hsize > csize) return;
    ResTableConfig cfg;
    read_config(c + 20, csize - 20, &cfg);
    bool sparse = flags & 0x01;
    bool off16 = flags & 0x02;
    const uint8_t *offs = c + hsize;
    const uint8_t *ents = c + entries_start;
    const uint8_t *end = c + csize;
    ResType *type = NULL;
    for (uint32_t i = 0; i < count; i++) {
        uint32_t idx, off;
        if (sparse) {
            idx = sa_rd16(offs + i * 4);
            off = (uint32_t)sa_rd16(offs + i * 4 + 2) * 4;
        } else if (off16) {
            idx = i;
            uint16_t o = sa_rd16(offs + i * 2);
            if (o == 0xffff) continue;
            off = (uint32_t)o * 4;
        } else {
            idx = i;
            off = sa_rd32(offs + i * 4);
            if (off == 0xffffffff) continue;
        }
        const uint8_t *e = ents + off;
        if (e + 8 > end) continue;
        if (!type || idx >= type->count) type = pkg_type(pkg, id, sparse ? idx + 1 : count);
        ResEntryDef *def = sa_calloc(1, sizeof *def);
        def->config = cfg;
        uint16_t esize = sa_rd16(e);
        uint16_t eflags = sa_rd16(e + 2);
        uint32_t key;
        if (eflags & 0x0008) { /* FLAG_COMPACT */
            key = esize;       /* in compact entries the first u16 is the key index */
            def->value.type = (uint8_t)(eflags >> 8);
            def->value.data = sa_rd32(e + 4);
            if (def->value.type == RV_STRING) def->value.string = res_pool_get(values, def->value.data);
        } else {
            key = sa_rd32(e + 4);
            if (eflags & 0x0001) {
                def->complex = true;
                def->parent = sa_rd32(e + 8);
                def->nitems = sa_rd32(e + 12);
                const uint8_t *m = e + esize;
                if (m + (size_t)def->nitems * 12 > end) def->nitems = 0;
                def->items = sa_calloc(def->nitems ? def->nitems : 1, sizeof(ResBagItem));
                for (uint32_t k = 0; k < def->nitems; k++) {
                    def->items[k].name = sa_rd32(m + k * 12);
                    read_value(values, m + k * 12 + 4, &def->items[k].value);
                }
            } else {
                read_value(values, e + esize, &def->value);
            }
        }
        if (!type->names[idx]) type->names[idx] = res_pool_get(&pkg->key_strings, key);
        def->next = type->entries[idx];
        type->entries[idx] = def;
    }
}

static void parse_package(ResTable *tab, ResStringPool *values, const uint8_t *c, size_t csize) {
    if (tab->npackages >= (int)SA_ARRAY_LEN(tab->packages)) return;
    ResPackage *pkg = sa_calloc(1, sizeof *pkg);
    uint16_t hsize = sa_rd16(c + 2);
    pkg->id = sa_rd32(c + 8);
    uint16_t name16[128];
    for (int i = 0; i < 128; i++) name16[i] = sa_rd16(c + 12 + i * 2);
    size_t nl = 0;
    while (nl < 128 && name16[nl]) nl++;
    char *nm = sa_utf16_to_utf8(name16, nl, NULL);
    snprintf(pkg->name, sizeof pkg->name, "%s", nm);
    free(nm);
    uint32_t type_strings = sa_rd32(c + 268);
    uint32_t key_strings = sa_rd32(c + 276);
    if (type_strings && type_strings < csize) res_pool_init(&pkg->type_strings, c + type_strings, csize - type_strings);
    if (key_strings && key_strings < csize) res_pool_init(&pkg->key_strings, c + key_strings, csize - key_strings);
    size_t pos = hsize;
    while (pos + 8 <= csize) {
        const uint8_t *ch = c + pos;
        uint16_t type = sa_rd16(ch);
        uint32_t sz = sa_rd32(ch + 4);
        if (sz < 8 || pos + sz > csize) break;
        if (type == CHUNK_TABLE_TYPE) parse_type_chunk(values, pkg, ch, sz);
        pos += sz;
    }
    tab->packages[tab->npackages++] = pkg;
}

static bool arsc_parse_into(ResTable *t, const uint8_t *data, size_t len, ResStringPool *values, uint8_t **copy) {
    if (len < 12 || sa_rd16(data) != CHUNK_TABLE) {
        LOGE("not a resource table");
        return false;
    }
    *copy = sa_malloc(len);
    memcpy(*copy, data, len);
    const uint8_t *d = *copy;
    uint32_t total = sa_rd32(d + 4);
    if (total > len) total = (uint32_t)len;
    size_t pos = sa_rd16(d + 2);
    while (pos + 8 <= total) {
        const uint8_t *c = d + pos;
        uint16_t type = sa_rd16(c);
        uint32_t sz = sa_rd32(c + 4);
        if (sz < 8 || pos + sz > total) break;
        if (type == CHUNK_STRING_POOL && !values->base) res_pool_init(values, c, total - pos);
        else if (type == CHUNK_TABLE_PACKAGE) parse_package(t, values, c, sz);
        pos += sz;
    }
    return true;
}

ResTable *arsc_parse(const uint8_t *data, size_t len) {
    ResTable *t = sa_calloc(1, sizeof *t);
    res_config_default(&t->config);
    if (!arsc_parse_into(t, data, len, &t->values, &t->data)) {
        free(t);
        return NULL;
    }
    return t;
}

bool arsc_add(ResTable *t, const uint8_t *data, size_t len) {
    if (t->nextra >= (int)SA_ARRAY_LEN(t->extra_values)) return false;
    int i = t->nextra;
    t->extra_values[i] = sa_calloc(1, sizeof(ResStringPool));
    if (!arsc_parse_into(t, data, len, t->extra_values[i], &t->extra_data[i])) {
        free(t->extra_values[i]);
        return false;
    }
    t->nextra++;
    return true;
}

void arsc_free(ResTable *t) {
    if (!t) return;
    for (int p = 0; p < t->npackages; p++) {
        ResPackage *pkg = t->packages[p];
        for (int i = 0; i < 256; i++) {
            ResType *ty = pkg->types[i];
            if (!ty) continue;
            for (uint32_t e = 0; e < ty->count; e++) {
                ResEntryDef *d = ty->entries[e];
                while (d) {
                    ResEntryDef *n = d->next;
                    free(d->items);
                    free(d);
                    d = n;
                }
            }
            free(ty->entries);
            free(ty->names);
            free(ty);
        }
        res_pool_free(&pkg->type_strings);
        res_pool_free(&pkg->key_strings);
        free(pkg);
    }
    res_pool_free(&t->values);
    free(t->data);
    for (int i = 0; i < t->nextra; i++) {
        res_pool_free(t->extra_values[i]);
        free(t->extra_values[i]);
        free(t->extra_data[i]);
    }
    free(t);
}

void arsc_set_config(ResTable *t, const ResConfig *c) { t->config = *c; }

const char *arsc_package_name(ResTable *t) {
    for (int i = 0; i < t->npackages; i++)
        if (t->packages[i]->id != 0x01) return t->packages[i]->name;
    return t->npackages ? t->packages[0]->name : NULL;
}

static ResPackage *find_pkg(ResTable *t, uint32_t id) {
    uint32_t pid = id >> 24;
    for (int i = 0; i < t->npackages; i++)
        if (t->packages[i]->id == pid) return t->packages[i];
    return NULL;
}

/* Returns false if the entry's configuration cannot be used on this device. */
static bool config_matches(const ResConfig *dev, const ResTableConfig *c) {
    if (c->language[0] && (c->language[0] != dev->language[0] || c->language[1] != dev->language[1])) return false;
    if (c->country[0] && (c->country[0] != dev->country[0] || c->country[1] != dev->country[1])) return false;
    if (c->orientation && c->orientation != dev->orientation) return false;
    if (c->sdk_version && c->sdk_version > dev->sdk) return false;
    if (c->smallest_width_dp && c->smallest_width_dp > dev->smallest_width_dp) return false;
    if (c->screen_width_dp && c->screen_width_dp > dev->screen_width_dp) return false;
    if (c->screen_height_dp && c->screen_height_dp > dev->screen_height_dp) return false;
    uint8_t night = c->ui_mode & 0x30;
    if (night && (night >> 4) != dev->ui_mode_night) return false;
    uint8_t mtype = c->ui_mode & 0x0f;
    if (mtype && mtype != dev->ui_mode_type) return false;
    if (c->mcc || c->mnc) return false;
    if (c->touchscreen && c->touchscreen != 3 /* finger */) return false;
    if (c->keyboard && c->keyboard != 1 /* nokeys */) return false;
    uint8_t layout_size = c->screen_layout & 0x0f;
    if (layout_size && layout_size > 2 /* normal */) return false;
    return true;
}

/* true if a is a better match than b for the device (both already matching). */
static bool config_better(const ResConfig *dev, const ResTableConfig *a, const ResTableConfig *b) {
#define PREFER_SET(field)                                   \
    if ((a->field != 0) != (b->field != 0)) return a->field != 0;
    PREFER_SET(language[0]);
    PREFER_SET(country[0]);
    if (a->smallest_width_dp != b->smallest_width_dp) return a->smallest_width_dp > b->smallest_width_dp;
    if (a->screen_width_dp != b->screen_width_dp) return a->screen_width_dp > b->screen_width_dp;
    if (a->screen_height_dp != b->screen_height_dp) return a->screen_height_dp > b->screen_height_dp;
    PREFER_SET(orientation);
    if ((a->ui_mode & 0x30) != (b->ui_mode & 0x30)) return (a->ui_mode & 0x30) != 0;
    if ((a->ui_mode & 0x0f) != (b->ui_mode & 0x0f)) return (a->ui_mode & 0x0f) != 0;
    if (a->density != b->density) {
        /* 0 = default (160), 0xfffe = anydpi, 0xffff = nodpi */
        int want = dev->density;
        int ad = a->density == 0 ? 160 : a->density;
        int bd = b->density == 0 ? 160 : b->density;
        if (a->density == 0xfffe) return true;
        if (b->density == 0xfffe) return false;
        if (a->density == 0xffff) ad = want;
        if (b->density == 0xffff) bd = want;
        /* prefer the closest density, scaling down preferred over up */
        int da = ad >= want ? (ad - want) : (want - ad) * 2;
        int db = bd >= want ? (bd - want) : (want - bd) * 2;
        if (da != db) return da < db;
    }
    if (a->sdk_version != b->sdk_version) return a->sdk_version > b->sdk_version;
    return false;
#undef PREFER_SET
}

const ResEntryDef *arsc_get_entry(ResTable *t, uint32_t id) {
    ResPackage *pkg = find_pkg(t, id);
    if (!pkg) return NULL;
    uint32_t tid = (id >> 16) & 0xff, eid = id & 0xffff;
    ResType *ty = pkg->types[tid];
    if (!ty || eid >= ty->count) return NULL;
    const ResEntryDef *best = NULL;
    for (const ResEntryDef *d = ty->entries[eid]; d; d = d->next) {
        if (!config_matches(&t->config, &d->config)) continue;
        if (!best || config_better(&t->config, &d->config, &best->config)) best = d;
    }
    if (!best) {
        /* No matching configuration: fall back to anything so we render something. */
        best = ty->entries[eid];
    }
    return best;
}

bool arsc_resolve(ResTable *t, const ResValue *in, ResValue *out) {
    ResValue v = *in;
    for (int depth = 0; depth < 16; depth++) {
        if ((v.type == RV_REFERENCE || v.type == RV_DYNAMIC_REFERENCE) && v.data != 0) {
            const ResEntryDef *e = arsc_get_entry(t, v.data);
            if (!e || e->complex) {
                *out = v; /* leave as reference (e.g. to a style or framework id) */
                return e != NULL;
            }
            v = e->value;
            continue;
        }
        *out = v;
        return true;
    }
    *out = v;
    return false;
}

bool arsc_get_value(ResTable *t, uint32_t id, ResValue *out) {
    ResValue r = {RV_REFERENCE, id, NULL};
    if (!arsc_resolve(t, &r, out)) return false;
    return !(out->type == RV_REFERENCE && out->data == id);
}

static void bag_merge(ResTable *t, uint32_t id, ResBag *out, int depth) {
    if (depth > 20) return;
    const ResEntryDef *e = arsc_get_entry(t, id);
    if (!e || !e->complex) return;
    if (e->parent) bag_merge(t, e->parent, out, depth + 1);
    for (uint32_t i = 0; i < e->nitems; i++) {
        uint32_t k;
        for (k = 0; k < out->count; k++)
            if (out->items[k].name == e->items[i].name) break;
        if (k == out->count) {
            out->items = sa_realloc(out->items, (out->count + 1) * sizeof(ResBagItem));
            out->count++;
        }
        out->items[k] = e->items[i];
    }
}

bool arsc_get_bag(ResTable *t, uint32_t id, ResBag *out) {
    memset(out, 0, sizeof *out);
    const ResEntryDef *e = arsc_get_entry(t, id);
    if (!e || !e->complex) return false;
    bag_merge(t, id, out, 0);
    return true;
}

uint32_t arsc_find_id(ResTable *t, const char *type, const char *name) {
    for (int p = 0; p < t->npackages; p++) {
        ResPackage *pkg = t->packages[p];
        for (int i = 1; i < 256; i++) {
            ResType *ty = pkg->types[i];
            if (!ty || !ty->type_name || strcmp(ty->type_name, type) != 0) continue;
            for (uint32_t e = 0; e < ty->count; e++)
                if (ty->names[e] && strcmp(ty->names[e], name) == 0) return (pkg->id << 24) | ((uint32_t)i << 16) | e;
        }
    }
    return 0;
}

const char *arsc_id_name(ResTable *t, uint32_t id, char *buf, size_t bufsz) {
    ResPackage *pkg = find_pkg(t, id);
    if (!pkg) return NULL;
    uint32_t tid = (id >> 16) & 0xff, eid = id & 0xffff;
    ResType *ty = pkg->types[tid];
    if (!ty || eid >= ty->count || !ty->names[eid]) return NULL;
    snprintf(buf, bufsz, "%s:%s/%s", pkg->name, ty->type_name ? ty->type_name : "?", ty->names[eid]);
    return buf;
}

/* ---- value helpers ---------------------------------------------------------- */

static const float RADIX_MULTS[4] = {1.0f / (1 << 8), 1.0f / (1 << 15), 1.0f / (1 << 23), 1.0f / (1u << 31)};

float res_complex_to_float(uint32_t data) {
    int32_t mantissa = (int32_t)(data & 0xffffff00);
    return (float)mantissa * RADIX_MULTS[(data >> 4) & 3];
}

float res_dimension_to_px(uint32_t data, float density, float scaled_density) {
    float v = res_complex_to_float(data);
    switch (data & 0xf) {
    case 0: return v;                           /* px */
    case 1: return v * density;                 /* dip */
    case 2: return v * scaled_density;          /* sp */
    case 3: return v * density * 160.0f / 72.0f; /* pt */
    case 4: return v * density * 160.0f;        /* in */
    case 5: return v * density * 160.0f / 25.4f; /* mm */
    default: return v;
    }
}

float res_fraction(uint32_t data, float base, float pbase) {
    float v = res_complex_to_float(data);
    return (data & 0xf) == 0 ? v * base : v * pbase;
}

char *res_value_to_string(const ResValue *v) {
    switch (v->type) {
    case RV_NULL: return sa_strdup("");
    case RV_STRING: return sa_strdup(v->string ? v->string : "");
    case RV_REFERENCE: return sa_sprintf("@0x%08x", v->data);
    case RV_ATTRIBUTE: return sa_sprintf("?0x%08x", v->data);
    case RV_FLOAT: {
        float f;
        memcpy(&f, &v->data, 4);
        return sa_sprintf("%g", (double)f);
    }
    case RV_DIMENSION: {
        static const char *units[] = {"px", "dip", "sp", "pt", "in", "mm"};
        unsigned u = v->data & 0xf;
        return sa_sprintf("%g%s", (double)res_complex_to_float(v->data), u < 6 ? units[u] : "?");
    }
    case RV_FRACTION: return sa_sprintf("%g%%", (double)res_complex_to_float(v->data) * 100.0);
    case RV_INT_BOOLEAN: return sa_strdup(v->data ? "true" : "false");
    case RV_INT_HEX: return sa_sprintf("0x%x", v->data);
    default:
        if (v->type >= RV_INT_COLOR_ARGB8 && v->type <= RV_INT_COLOR_RGB4) return sa_sprintf("#%08x", v->data);
        return sa_sprintf("%d", (int32_t)v->data);
    }
}
