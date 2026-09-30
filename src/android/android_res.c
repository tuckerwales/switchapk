/*
 * android.content.res natives: resource table lookups, binary XML and assets.
 *
 * One ResTable holds the framework package (0x01, from framework-res.apk,
 * generated from the SDK) and the application package (0x7f). Java keeps all
 * higher level logic (themes, TypedArray, drawables); these natives only
 * answer questions about the compiled tables and files.
 */
#include "android.h"

#define LOG_TAG "res"

ResTable *g_res;
ZipArchive *g_fw_res_zip;

enum { COOKIE_FRAMEWORK = 1, COOKIE_APP = 2 };

bool android_res_init(const char *framework_res_path, ZipArchive *app) {
    g_fw_res_zip = zip_open(framework_res_path);
    size_t len = 0;
    uint8_t *arsc = g_fw_res_zip ? zip_extract_name(g_fw_res_zip, "resources.arsc", &len) : NULL;
    if (!arsc) {
        LOGW("framework resources not found at %s: framework themes and drawables unavailable", framework_res_path);
    } else {
        g_res = arsc_parse(arsc, len);
        free(arsc);
    }
    uint8_t *app_arsc = app ? zip_extract_name(app, "resources.arsc", &len) : NULL;
    if (app_arsc) {
        if (!g_res) g_res = arsc_parse(app_arsc, len);
        else arsc_add(g_res, app_arsc, len);
        free(app_arsc);
    }
    if (!g_res) {
        /* an empty table keeps every lookup well-defined */
        static const uint8_t empty[12] = {0x02, 0x00, 0x0c, 0x00, 0x0c, 0, 0, 0, 0, 0, 0, 0};
        g_res = arsc_parse(empty, sizeof empty);
    }
    return g_res != NULL;
}

static ZipArchive *zip_for_cookie(int cookie) { return cookie == COOKIE_FRAMEWORK ? g_fw_res_zip : g_app_zip; }

static int cookie_for_id(uint32_t id) { return (id >> 24) == 0x01 ? COOKIE_FRAMEWORK : COOKIE_APP; }

/* Resolves id through references; returns the final value, the id that held it and its entry density. */
static bool resolve_id(uint32_t id, bool follow, ResValue *out, uint32_t *out_id, int *out_density) {
    uint32_t cur = id;
    for (int depth = 0; depth < 20; depth++) {
        const ResEntryDef *e = arsc_get_entry(g_res, cur);
        if (!e) return false;
        if (e->complex) {
            out->type = RV_REFERENCE;
            out->data = cur;
            out->string = NULL;
            *out_id = cur;
            *out_density = e->config.density;
            return true;
        }
        *out = e->value;
        *out_id = cur;
        *out_density = e->config.density;
        if (follow && (out->type == RV_REFERENCE || out->type == RV_DYNAMIC_REFERENCE) && out->data != 0 &&
            out->data != cur) {
            cur = out->data;
            continue;
        }
        if (out->type == RV_DYNAMIC_REFERENCE) out->type = RV_REFERENCE;
        return true;
    }
    return false;
}

static void fill_typed_value(VMThread *t, Object *tv, const ResValue *v, uint32_t res_id, int density) {
    FIELD_INT(tv, "type") = v->type;
    FIELD_INT(tv, "data") = (int32_t)v->data;
    FIELD_INT(tv, "resourceId") = (int32_t)res_id;
    FIELD_INT(tv, "assetCookie") = cookie_for_id(res_id);
    FIELD_INT(tv, "changingConfigurations") = 0;
    FIELD_INT(tv, "density") = density == 0 ? 160 : density;
    Object *s = NULL;
    if (v->type == RV_STRING && v->string) s = vm_new_string_utf8(t, v->string);
    FIELD_OBJ(tv, "string", "Ljava/lang/CharSequence;") = s;
}

/* static native boolean nGetValue(int id, TypedValue out, boolean resolveRefs) */
NATIVE(AssetManager_nGetValue) {
    UNUSED_ARGS();
    uint32_t id = (uint32_t)A_INT(0);
    Object *tv = A_OBJ(1);
    ResValue v;
    uint32_t rid;
    int density;
    if (!tv || !resolve_id(id, A_BOOL(2), &v, &rid, &density)) {
        R_BOOL(false);
        return;
    }
    fill_typed_value(t, tv, &v, rid, density);
    R_BOOL(true);
}

/* static native boolean nResolveValue(int type, int data, TypedValue out): follows a reference value */
NATIVE(AssetManager_nResolveReference) {
    UNUSED_ARGS();
    uint32_t id = (uint32_t)A_INT(0);
    Object *tv = A_OBJ(1);
    ResValue v;
    uint32_t rid;
    int density;
    if (!resolve_id(id, true, &v, &rid, &density)) {
        R_BOOL(false);
        return;
    }
    fill_typed_value(t, tv, &v, rid, density);
    R_BOOL(true);
}

/* static native Object[] nGetBag(int id): {int[] (attr, type, data)*, String[]} with parents merged */
NATIVE(AssetManager_nGetBag) {
    UNUSED_ARGS();
    uint32_t id = (uint32_t)A_INT(0);
    ResBag bag;
    if (!arsc_get_bag(g_res, id, &bag)) return;
    ArrayObject *ints = vm_alloc_prim_array(t, 'I', (int32_t)bag.count * 3);
    if (!ints) {
        free(bag.items);
        return;
    }
    Object *tmp = (Object *)ints;
    vm_add_root(&tmp);
    ArrayObject *strs = vm_alloc_array(t, g_vm.wk.arr_String, (int32_t)bag.count);
    Object *tmp2 = (Object *)strs;
    vm_add_root(&tmp2);
    for (uint32_t i = 0; i < bag.count; i++) {
        ResBagItem *it = &bag.items[i];
        uint8_t type = it->value.type == RV_DYNAMIC_REFERENCE ? RV_REFERENCE : it->value.type;
        ARRAY_DATA(ints, int32_t)[i * 3] = (int32_t)it->name;
        ARRAY_DATA(ints, int32_t)[i * 3 + 1] = type;
        ARRAY_DATA(ints, int32_t)[i * 3 + 2] = (int32_t)it->value.data;
        if (type == RV_STRING && it->value.string && strs)
            ARRAY_DATA(strs, Object *)[i] = vm_new_string_utf8(t, it->value.string);
    }
    free(bag.items);
    ArrayObject *res = vm_alloc_array(t, g_vm.wk.arr_Object, 2);
    if (res) {
        ARRAY_DATA(res, Object *)[0] = (Object *)ints;
        ARRAY_DATA(res, Object *)[1] = (Object *)strs;
    }
    vm_remove_root(&tmp2);
    vm_remove_root(&tmp);
    R_OBJ(res);
}

/* static native int nGetBagParent(int id) */
NATIVE(AssetManager_nGetStyleParent) {
    UNUSED_ARGS();
    const ResEntryDef *e = arsc_get_entry(g_res, (uint32_t)A_INT(0));
    R_INT(e && e->complex ? (int32_t)e->parent : 0);
}

/* static native String nGetResourceName(int id) */
NATIVE(AssetManager_nGetResourceName) {
    UNUSED_ARGS();
    char buf[512];
    const char *n = arsc_id_name(g_res, (uint32_t)A_INT(0), buf, sizeof buf);
    R_OBJ(n ? vm_new_string_utf8(t, n) : NULL);
}

/* static native int nGetIdentifier(String name, String type, String package) */
NATIVE(AssetManager_nGetIdentifier) {
    UNUSED_ARGS();
    char *name = nat_str(A_OBJ(0));
    char *type = nat_str(A_OBJ(1));
    char *pkg = nat_str(A_OBJ(2));
    int32_t id = 0;
    if (name && type) {
        /* restrict to the requested package */
        for (int p = 0; p < g_res->npackages && !id; p++) {
            ResPackage *rp = g_res->packages[p];
            if (pkg && *pkg && strcmp(rp->name, pkg) != 0) continue;
            for (int i = 1; i < 256 && !id; i++) {
                ResType *ty = rp->types[i];
                if (!ty || !ty->type_name || strcmp(ty->type_name, type) != 0) continue;
                for (uint32_t e = 0; e < ty->count; e++) {
                    if (ty->names[e] && strcmp(ty->names[e], name) == 0) {
                        id = (int32_t)((rp->id << 24) | ((uint32_t)i << 16) | e);
                        break;
                    }
                }
            }
        }
    }
    free(name);
    free(type);
    free(pkg);
    R_INT(id);
}

/* static native String nGetPackageName() : the application package */
NATIVE(AssetManager_nGetAppPackageName) {
    UNUSED_ARGS();
    const char *n = NULL;
    for (int i = 0; i < g_res->npackages; i++)
        if (g_res->packages[i]->id != 0x01) n = g_res->packages[i]->name;
    R_OBJ(n ? vm_new_string_utf8(t, n) : NULL);
}

/* static native void nSetConfiguration(int density, int wdp, int hdp, int swdp, int orientation, int night,
 *                                       int sdk, String lang, String country, int uiModeType) */
NATIVE(AssetManager_nSetConfiguration) {
    UNUSED_ARGS();
    ResConfig c;
    res_config_default(&c);
    c.density = (uint16_t)A_INT(0);
    c.screen_width_dp = (uint16_t)A_INT(1);
    c.screen_height_dp = (uint16_t)A_INT(2);
    c.smallest_width_dp = (uint16_t)A_INT(3);
    c.orientation = (uint8_t)A_INT(4);
    c.ui_mode_night = (uint8_t)A_INT(5);
    c.sdk = (uint16_t)A_INT(6);
    char *lang = nat_str(A_OBJ(7));
    char *country = nat_str(A_OBJ(8));
    memset(c.language, 0, sizeof c.language);
    memset(c.country, 0, sizeof c.country);
    if (lang && strlen(lang) >= 2) memcpy(c.language, lang, 2);
    if (country && strlen(country) >= 2) memcpy(c.country, country, 2);
    free(lang);
    free(country);
    c.ui_mode_type = (uint8_t)A_INT(9);
    arsc_set_config(g_res, &c);
}

/* ---- files ---------------------------------------------------------------------------------- */

static ArrayObject *bytes_from_zip(VMThread *t, ZipArchive *z, const char *name) {
    if (!z || !name) return NULL;
    size_t len;
    uint8_t *data = zip_extract_name(z, name, &len);
    if (!data) return NULL;
    ArrayObject *a = vm_alloc_prim_array(t, 'B', (int32_t)len);
    if (a) memcpy(a->data, data, len);
    free(data);
    return a;
}

/* static native byte[] nOpenAsset(String name) : assets/ of the app */
NATIVE(AssetManager_nOpenAsset) {
    UNUSED_ARGS();
    char *name = nat_str(A_OBJ(0));
    if (!name) return;
    const char *n = name;
    while (*n == '/') n++;
    char *full = sa_sprintf("assets/%s", n);
    R_OBJ(bytes_from_zip(t, g_app_zip, full));
    free(full);
    free(name);
}

/* static native byte[] nOpenNonAsset(int cookie, String path) */
NATIVE(AssetManager_nOpenNonAsset) {
    UNUSED_ARGS();
    char *name = nat_str(A_OBJ(1));
    int cookie = A_INT(0);
    ArrayObject *a = NULL;
    if (cookie == 0) {
        a = bytes_from_zip(t, g_app_zip, name);
        if (!a) a = bytes_from_zip(t, g_fw_res_zip, name);
    } else {
        a = bytes_from_zip(t, zip_for_cookie(cookie), name);
    }
    free(name);
    R_OBJ(a);
}

/* static native long nAssetInfo(String name, long[] out): {offset of stored data or -1, length} for fds */
NATIVE(AssetManager_nAssetInfo) {
    UNUSED_ARGS();
    char *name = nat_str(A_OBJ(0));
    ArrayObject *out = A_ARR(1);
    if (!name || !out || out->length < 2 || !g_app_zip) {
        free(name);
        R_BOOL(false);
        return;
    }
    const char *n = name;
    while (*n == '/') n++;
    char *full = sa_sprintf("assets/%s", n);
    const ZipEntry *e = zip_find(g_app_zip, full);
    if (!e) e = zip_find(g_app_zip, n);
    free(full);
    free(name);
    if (!e) {
        R_BOOL(false);
        return;
    }
    ARRAY_DATA(out, int64_t)[0] = e->method == 0 ? zip_stored_data_offset(g_app_zip, e) : -1;
    ARRAY_DATA(out, int64_t)[1] = e->uncomp_size;
    R_BOOL(true);
}

static int cmp_str(const void *a, const void *b) { return strcmp(*(const char *const *)a, *(const char *const *)b); }

/* static native String[] nList(String dir) : entries directly inside assets/<dir> */
NATIVE(AssetManager_nList) {
    UNUSED_ARGS();
    char *dir = nat_str(A_OBJ(0));
    if (!dir) dir = sa_strdup("");
    size_t dl = strlen(dir);
    while (dl && dir[dl - 1] == '/') dir[--dl] = 0;
    char *prefix = dl ? sa_sprintf("assets/%s/", dir) : sa_strdup("assets/");
    size_t pl = strlen(prefix);
    SaVec names = {0};
    size_t n = g_app_zip ? zip_count(g_app_zip) : 0;
    for (size_t i = 0; i < n; i++) {
        const ZipEntry *e = zip_entry_at(g_app_zip, i);
        if (strncmp(e->name, prefix, pl) != 0) continue;
        const char *rest = e->name + pl;
        if (!*rest) continue;
        const char *slash = strchr(rest, '/');
        size_t len = slash ? (size_t)(slash - rest) : strlen(rest);
        bool dup = false;
        for (size_t k = 0; k < names.len; k++)
            if (strlen(names.items[k]) == len && !strncmp(names.items[k], rest, len)) dup = true;
        if (!dup) sa_vec_push(&names, sa_strndup(rest, len));
    }
    qsort(names.items, names.len, sizeof(void *), cmp_str);
    ArrayObject *arr = vm_alloc_array(t, g_vm.wk.arr_String, (int32_t)names.len);
    Object *tmp = (Object *)arr;
    vm_add_root(&tmp);
    for (size_t k = 0; k < names.len; k++) {
        if (arr) ARRAY_DATA(arr, Object *)[k] = vm_new_string_utf8(t, names.items[k]);
        free(names.items[k]);
    }
    vm_remove_root(&tmp);
    sa_vec_free(&names);
    free(prefix);
    free(dir);
    R_OBJ(arr);
}

/* ---- binary XML -------------------------------------------------------------------------------- */

typedef struct {
    SaBuf ev;    /* int32 records */
    SaBuf attrs; /* int32 records */
    SaMap index;
    SaVec strings;
} Flat;

static int32_t flat_str(Flat *f, const char *s) {
    if (!s) return -1;
    void *v = sa_map_get(&f->index, s);
    if (v) return (int32_t)(intptr_t)v - 1;
    int32_t idx = (int32_t)f->strings.len;
    sa_vec_push(&f->strings, (void *)s);
    sa_map_put(&f->index, s, (void *)(intptr_t)(idx + 1));
    return idx;
}

static void put_ints(SaBuf *b, const int32_t *v, int n) { sa_buf_append(b, v, (size_t)n * 4); }

enum { EV_START = 2, EV_END = 3, EV_TEXT = 4 };

static void flatten(Flat *f, const XmlNode *n) {
    int32_t attr_start = (int32_t)(f->attrs.len / 4 / 6);
    for (uint32_t i = 0; i < n->nattrs; i++) {
        const XmlAttr *a = &n->attrs[i];
        uint8_t type = a->value.type;
        if (type == RV_DYNAMIC_REFERENCE) type = RV_REFERENCE;
        int32_t data = (int32_t)a->value.data;
        if (type == RV_STRING) data = flat_str(f, a->value.string ? a->value.string : (a->raw ? a->raw : ""));
        int32_t rec[6] = {flat_str(f, a->ns), flat_str(f, a->name), (int32_t)a->res_id, flat_str(f, a->raw), type, data};
        put_ints(&f->attrs, rec, 6);
    }
    int32_t start[6] = {EV_START, (int32_t)n->line, flat_str(f, n->name), flat_str(f, n->ns), attr_start, (int32_t)n->nattrs};
    put_ints(&f->ev, start, 6);
    if (n->text && *n->text) {
        int32_t txt[6] = {EV_TEXT, (int32_t)n->line, flat_str(f, n->text), -1, 0, 0};
        put_ints(&f->ev, txt, 6);
    }
    for (uint32_t i = 0; i < n->nchildren; i++) flatten(f, n->children[i]);
    int32_t end[6] = {EV_END, (int32_t)n->line, flat_str(f, n->name), flat_str(f, n->ns), 0, 0};
    put_ints(&f->ev, end, 6);
}

static Object *xml_to_java(VMThread *t, const uint8_t *data, size_t len) {
    XmlDoc *doc = axml_parse(data, len);
    if (!doc || !doc->root) {
        axml_free(doc);
        return NULL;
    }
    Flat f;
    memset(&f, 0, sizeof f);
    flatten(&f, doc->root);
    ArrayObject *ev = vm_alloc_prim_array(t, 'I', (int32_t)(f.ev.len / 4));
    Object *r1 = (Object *)ev;
    vm_add_root(&r1);
    ArrayObject *at = vm_alloc_prim_array(t, 'I', (int32_t)(f.attrs.len / 4));
    Object *r2 = (Object *)at;
    vm_add_root(&r2);
    ArrayObject *st = vm_alloc_array(t, g_vm.wk.arr_String, (int32_t)f.strings.len);
    Object *r3 = (Object *)st;
    vm_add_root(&r3);
    ArrayObject *res = NULL;
    if (ev && at && st) {
        memcpy(ev->data, f.ev.data, f.ev.len);
        memcpy(at->data, f.attrs.data, f.attrs.len);
        for (size_t i = 0; i < f.strings.len; i++) ARRAY_DATA(st, Object *)[i] = vm_new_string_utf8(t, f.strings.items[i]);
        res = vm_alloc_array(t, g_vm.wk.arr_Object, 3);
        if (res) {
            ARRAY_DATA(res, Object *)[0] = (Object *)ev;
            ARRAY_DATA(res, Object *)[1] = (Object *)at;
            ARRAY_DATA(res, Object *)[2] = (Object *)st;
        }
    }
    vm_remove_root(&r3);
    vm_remove_root(&r2);
    vm_remove_root(&r1);
    sa_buf_free(&f.ev);
    sa_buf_free(&f.attrs);
    sa_vec_free(&f.strings);
    sa_map_free(&f.index, NULL);
    axml_free(doc);
    return (Object *)res;
}

/* static native Object[] nOpenXml(int cookie, String path): {int[] events, int[] attrs, String[] strings} */
NATIVE(AssetManager_nOpenXml) {
    UNUSED_ARGS();
    char *name = nat_str(A_OBJ(1));
    int cookie = A_INT(0);
    if (!name) return;
    size_t len = 0;
    uint8_t *data = NULL;
    if (cookie == 0) {
        if (g_app_zip) data = zip_extract_name(g_app_zip, name, &len);
        if (!data && g_fw_res_zip) data = zip_extract_name(g_fw_res_zip, name, &len);
    } else {
        ZipArchive *z = zip_for_cookie(cookie);
        if (z) data = zip_extract_name(z, name, &len);
    }
    free(name);
    if (!data) return;
    R_OBJ(xml_to_java(t, data, len));
    free(data);
}

/* static native Object[] nParseXmlBytes(byte[] data) */
NATIVE(AssetManager_nParseXmlBytes) {
    UNUSED_ARGS();
    ArrayObject *a = A_ARR(0);
    if (!a) return;
    R_OBJ(xml_to_java(t, ARRAY_DATA(a, uint8_t), (size_t)a->length));
}

/* static native String[] nGetLocales() */
NATIVE(AssetManager_nGetLocales) {
    UNUSED_ARGS();
    ArrayObject *arr = vm_alloc_array(t, g_vm.wk.arr_String, 1);
    Object *tmp = (Object *)arr;
    vm_add_root(&tmp);
    if (arr) ARRAY_DATA(arr, Object *)[0] = vm_new_string_utf8(t, "en-US");
    vm_remove_root(&tmp);
    R_OBJ(arr);
}

/* static native String nApkPath() */
NATIVE(AssetManager_nApkPath) {
    UNUSED_ARGS();
    extern char *g_app_apk_path;
    R_OBJ(g_app_apk_path ? vm_new_string_utf8(t, g_app_apk_path) : NULL);
}

#define AM "Landroid/content/res/AssetManager;"
static const NativeMethodReg g_regs[] = {
    {AM, "nGetValue", "(ILandroid/util/TypedValue;Z)Z", AssetManager_nGetValue},
    {AM, "nResolveReference", "(ILandroid/util/TypedValue;)Z", AssetManager_nResolveReference},
    {AM, "nGetBag", "(I)[Ljava/lang/Object;", AssetManager_nGetBag},
    {AM, "nGetStyleParent", "(I)I", AssetManager_nGetStyleParent},
    {AM, "nGetResourceName", "(I)Ljava/lang/String;", AssetManager_nGetResourceName},
    {AM, "nGetIdentifier", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I", AssetManager_nGetIdentifier},
    {AM, "nGetAppPackageName", "()Ljava/lang/String;", AssetManager_nGetAppPackageName},
    {AM, "nSetConfiguration", "(IIIIIIILjava/lang/String;Ljava/lang/String;I)V", AssetManager_nSetConfiguration},
    {AM, "nOpenAsset", "(Ljava/lang/String;)[B", AssetManager_nOpenAsset},
    {AM, "nOpenNonAsset", "(ILjava/lang/String;)[B", AssetManager_nOpenNonAsset},
    {AM, "nAssetInfo", "(Ljava/lang/String;[J)Z", AssetManager_nAssetInfo},
    {AM, "nList", "(Ljava/lang/String;)[Ljava/lang/String;", AssetManager_nList},
    {AM, "nOpenXml", "(ILjava/lang/String;)[Ljava/lang/Object;", AssetManager_nOpenXml},
    {AM, "nParseXmlBytes", "([B)[Ljava/lang/Object;", AssetManager_nParseXmlBytes},
    {AM, "nGetLocales", "()[Ljava/lang/String;", AssetManager_nGetLocales},
    {AM, "nApkPath", "()Ljava/lang/String;", AssetManager_nApkPath},
};

void android_res_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
