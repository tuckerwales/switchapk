/*
 * Reads an APK's launcher label and icon without starting the VM.
 * The launcher activity (MAIN + LAUNCHER) wins over the application
 * element. A missing label becomes the file name without .apk.
 * Icons are bitmap files chosen at the caller's density. XML drawables
 * (adaptive icons, vectors) are skipped.
 */
#include "apk_info.h"

#include "../core/res.h"
#include "../core/zip.h"
#include "../gfx/gfx.h"

#define LOG_TAG "apk"

/* android.R.attr values. Used when a manifest strips attribute names and keeps the resource map. */
#define ATTR_LABEL 0x01010001
#define ATTR_ICON 0x01010002
#define ATTR_NAME 0x01010003

#define ICON_MAX 1024

void apk_identity_free(ApkIdentity *id) {
    if (!id) return;
    free(id->label);
    free(id->icon);
    memset(id, 0, sizeof *id);
}

static bool ends_with_ci(const char *s, const char *suf) {
    size_t n = strlen(s), m = strlen(suf);
    if (n < m) return false;
    for (size_t i = 0; i < m; i++) {
        unsigned char a = (unsigned char)s[n - m + i];
        unsigned char b = (unsigned char)suf[i];
        if (a >= 'A' && a <= 'Z') a = (unsigned char)(a - 'A' + 'a');
        if (b >= 'A' && b <= 'Z') b = (unsigned char)(b - 'A' + 'a');
        if (a != b) return false;
    }
    return true;
}

static char *fallback_label(const char *path) {
    const char *base = path ? strrchr(path, '/') : NULL;
    base = base ? base + 1 : (path && path[0] ? path : "app");
    char *s = sa_strdup(base);
    if (ends_with_ci(s, ".apk") && strlen(s) > 4) s[strlen(s) - 4] = 0;
    if (!s[0]) {
        free(s);
        return sa_strdup("app");
    }
    return s;
}

static const XmlAttr *android_attr(const XmlNode *n, const char *name, uint32_t res_id) {
    const XmlAttr *a = n ? xml_android_attr(n, name) : NULL;
    if (a || !n) return a;
    for (uint32_t i = 0; i < n->nattrs; i++)
        if (n->attrs[i].res_id == res_id) return &n->attrs[i];
    return NULL;
}

/* Literal text, or the string a resource reference resolves to. Not a path. */
static char *resolve_string(ResTable *table, const XmlAttr *a) {
    if (!a) return NULL;
    if ((a->value.type == RV_REFERENCE || a->value.type == RV_DYNAMIC_REFERENCE) && a->value.data != 0) {
        ResValue v;
        if (!table || !arsc_get_value(table, a->value.data, &v)) return NULL;
        if (v.type == RV_STRING && v.string && v.string[0]) return sa_strdup(v.string);
        return NULL;
    }
    if (a->value.type == RV_STRING && a->value.string && a->value.string[0]) return sa_strdup(a->value.string);
    if (a->raw && a->raw[0] && a->raw[0] != '@') return sa_strdup(a->raw);
    return NULL;
}

static const char *attr_name(const XmlNode *n) {
    const XmlAttr *a = android_attr(n, "name", ATTR_NAME);
    if (!a) return NULL;
    if (a->value.type == RV_STRING && a->value.string) return a->value.string;
    return a->raw;
}

static bool is_launcher_filter(const XmlNode *filter) {
    bool main = false, launcher = false;
    for (uint32_t i = 0; i < filter->nchildren; i++) {
        const XmlNode *c = filter->children[i];
        if (!c || !c->name) continue;
        const char *n = attr_name(c);
        if (!n) continue;
        if (strcmp(c->name, "action") == 0 && strcmp(n, "android.intent.action.MAIN") == 0) main = true;
        if (strcmp(c->name, "category") == 0 && strcmp(n, "android.intent.category.LAUNCHER") == 0) launcher = true;
    }
    return main && launcher;
}

static bool is_launcher_component(const XmlNode *node) {
    if (!node || !node->name) return false;
    if (strcmp(node->name, "activity") != 0 && strcmp(node->name, "activity-alias") != 0) return false;
    for (uint32_t i = 0; i < node->nchildren; i++) {
        const XmlNode *c = node->children[i];
        if (c && c->name && strcmp(c->name, "intent-filter") == 0 && is_launcher_filter(c)) return true;
    }
    return false;
}

static uint32_t *decode_icon(const uint8_t *data, size_t len, int *w, int *h) {
    int iw = 0, ih = 0;
    uint32_t *px = gfx_decode_image(data, len, &iw, &ih, NULL);
    if (!px) return NULL;
    if (iw <= 0 || ih <= 0 || iw > ICON_MAX || ih > ICON_MAX) {
        free(px);
        return NULL;
    }
    *w = iw;
    *h = ih;
    return px;
}

bool apk_read_identity(const char *path, int density_dpi, ApkIdentity *out) {
    memset(out, 0, sizeof *out);
    out->label = fallback_label(path);
    ZipArchive *z = zip_open(path);
    if (!z) return false;

    size_t mlen = 0;
    uint8_t *manifest = zip_extract_name(z, "AndroidManifest.xml", &mlen);
    XmlDoc *doc = manifest ? axml_parse(manifest, mlen) : NULL;
    free(manifest);

    size_t alen = 0;
    uint8_t *arsc_bytes = zip_extract_name(z, "resources.arsc", &alen);
    ResTable *table = (arsc_bytes && alen) ? arsc_parse(arsc_bytes, alen) : NULL;
    free(arsc_bytes);
    if (table) {
        ResConfig cfg;
        res_config_default(&cfg);
        if (density_dpi > 0 && density_dpi <= 65535) cfg.density = (uint16_t)density_dpi;
        arsc_set_config(table, &cfg);
    }

    const XmlAttr *app_label = NULL, *app_icon = NULL;
    const XmlAttr *act_label = NULL, *act_icon = NULL;
    XmlNode *root = doc ? doc->root : NULL;
    XmlNode *app = root ? xml_child(root, "application") : NULL;
    if (!app && root && root->name && strcmp(root->name, "application") == 0) app = root;
    if (app) {
        app_label = android_attr(app, "label", ATTR_LABEL);
        app_icon = android_attr(app, "icon", ATTR_ICON);
        for (uint32_t i = 0; i < app->nchildren; i++) {
            XmlNode *c = app->children[i];
            if (!is_launcher_component(c)) continue;
            act_label = android_attr(c, "label", ATTR_LABEL);
            act_icon = android_attr(c, "icon", ATTR_ICON);
            break;
        }
    }

    char *label = act_label ? resolve_string(table, act_label) : resolve_string(table, app_label);
    const XmlAttr *icon_attr = act_icon ? act_icon : app_icon;
    char *icon_path = resolve_string(table, icon_attr);
    if (label) {
        free(out->label);
        out->label = label;
    }

    uint8_t *pixels = NULL;
    size_t plen = 0;
    if (icon_path) {
        const char *zp = icon_path[0] == '/' ? icon_path + 1 : icon_path;
        if (strchr(zp, '/') && !ends_with_ci(zp, ".xml")) {
            pixels = zip_extract_name(z, zp, &plen);
            if (!pixels) LOGW("no icon file %s in %s", zp, path);
        }
    }
    free(icon_path);
    arsc_free(table);
    axml_free(doc);
    zip_close(z);

    if (pixels) {
        out->icon = decode_icon(pixels, plen, &out->icon_w, &out->icon_h);
        free(pixels);
        if (!out->icon) LOGW("could not decode icon in %s", path);
    }
    return true;
}
