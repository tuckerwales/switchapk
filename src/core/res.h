/*
 * Android compiled resource formats: string pools, binary XML (AXML) and
 * the resource table (resources.arsc).
 */
#ifndef SWITCHAPK_RES_H
#define SWITCHAPK_RES_H

#include "common.h"

/* Res_value data types */
enum {
    RV_NULL = 0x00,
    RV_REFERENCE = 0x01,
    RV_ATTRIBUTE = 0x02,
    RV_STRING = 0x03,
    RV_FLOAT = 0x04,
    RV_DIMENSION = 0x05,
    RV_FRACTION = 0x06,
    RV_DYNAMIC_REFERENCE = 0x07,
    RV_DYNAMIC_ATTRIBUTE = 0x08,
    RV_INT_DEC = 0x10,
    RV_INT_HEX = 0x11,
    RV_INT_BOOLEAN = 0x12,
    RV_INT_COLOR_ARGB8 = 0x1c,
    RV_INT_COLOR_RGB8 = 0x1d,
    RV_INT_COLOR_ARGB4 = 0x1e,
    RV_INT_COLOR_RGB4 = 0x1f,
};

typedef struct {
    uint8_t type;
    uint32_t data;
    const char *string; /* for RV_STRING (UTF-8, owned by the pool) */
} ResValue;

/* ---- string pool ------------------------------------------------------ */

typedef struct {
    uint32_t count;
    char **strings; /* decoded to UTF-8 lazily */
    const uint8_t *base;
    const uint32_t *offsets;
    uint32_t strings_start;
    size_t size;
    bool utf8;
} ResStringPool;

bool res_pool_init(ResStringPool *p, const uint8_t *chunk, size_t avail);
const char *res_pool_get(ResStringPool *p, uint32_t idx);
void res_pool_free(ResStringPool *p);

/* ---- binary XML ------------------------------------------------------- */

typedef struct {
    const char *ns;   /* namespace URI or NULL */
    const char *name;
    uint32_t res_id;  /* attribute resource id from resource map, or 0 */
    const char *raw;  /* raw string value or NULL */
    ResValue value;
} XmlAttr;

typedef struct XmlNode {
    const char *name;
    const char *ns;
    XmlAttr *attrs;
    uint32_t nattrs;
    struct XmlNode **children;
    uint32_t nchildren;
    struct XmlNode *parent;
    char *text;
    uint32_t line;
} XmlNode;

typedef struct {
    ResStringPool pool;
    XmlNode *root;
    uint8_t *data; /* owned copy */
} XmlDoc;

/* Parses a binary XML blob. The data is copied. Also accepts plain text XML
 * (a minimal parser handles uncompiled XML found in some assets). */
XmlDoc *axml_parse(const uint8_t *data, size_t len);
void axml_free(XmlDoc *doc);

#define ANDROID_NS "http://schemas.android.com/apk/res/android"

const XmlAttr *xml_attr(const XmlNode *n, const char *ns, const char *name);
/* Finds an android: attribute by name (falls back to matching the resource id). */
const XmlAttr *xml_android_attr(const XmlNode *n, const char *name);
const char *xml_attr_str(const XmlNode *n, const char *ns, const char *name);
XmlNode *xml_child(const XmlNode *n, const char *name);
void xml_dump(const XmlNode *n, int depth, FILE *out);

/* ---- resource table ------------------------------------------------------ */

/* Device configuration used to select resources. */
typedef struct {
    char language[3];
    char country[3];
    uint8_t orientation; /* 1 = port, 2 = land */
    uint16_t density;    /* dpi */
    uint16_t sdk;
    uint16_t screen_width_dp;
    uint16_t screen_height_dp;
    uint16_t smallest_width_dp;
    uint8_t ui_mode_night; /* 1 = notnight, 2 = night */
    uint8_t ui_mode_type;  /* 1 = normal, 4 = television ... */
} ResConfig;

typedef struct {
    uint32_t size;
    uint16_t mcc, mnc;
    char language[2], country[2];
    uint8_t orientation, touchscreen;
    uint16_t density;
    uint8_t keyboard, navigation, input_flags, pad0;
    uint16_t screen_width, screen_height;
    uint16_t sdk_version, minor_version;
    uint8_t screen_layout, ui_mode;
    uint16_t smallest_width_dp;
    uint16_t screen_width_dp, screen_height_dp;
} ResTableConfig;

typedef struct {
    uint32_t name;    /* attribute id */
    ResValue value;
} ResBagItem;

typedef struct ResEntryDef {
    ResTableConfig config;
    bool complex;
    ResValue value;      /* simple */
    uint32_t parent;     /* complex */
    ResBagItem *items;
    uint32_t nitems;
    struct ResEntryDef *next; /* other configurations */
} ResEntryDef;

typedef struct {
    uint32_t count;
    ResEntryDef **entries; /* per entry index, linked list of configs */
    const char **names;     /* key names per entry index */
    const char *type_name;
} ResType;

typedef struct {
    uint32_t id;
    char name[256];
    ResType *types[256];
    ResStringPool type_strings;
    ResStringPool key_strings;
} ResPackage;

typedef struct {
    ResStringPool values;
    ResPackage *packages[4];
    int npackages;
    uint8_t *data;
    ResConfig config;
} ResTable;

typedef struct {
    ResBagItem *items;
    uint32_t count;
} ResBag;

ResTable *arsc_parse(const uint8_t *data, size_t len);
void arsc_free(ResTable *t);
void arsc_set_config(ResTable *t, const ResConfig *c);
void res_config_default(ResConfig *c);

/* Selects the best entry for id; returns NULL if missing. */
const ResEntryDef *arsc_get_entry(ResTable *t, uint32_t id);
/* Resolves a simple value, following references (depth-limited). */
bool arsc_get_value(ResTable *t, uint32_t id, ResValue *out);
/* Resolves a value that may itself be a reference. */
bool arsc_resolve(ResTable *t, const ResValue *in, ResValue *out);
/* Builds a flattened bag (style / array) including parents. Caller frees items. */
bool arsc_get_bag(ResTable *t, uint32_t id, ResBag *out);
uint32_t arsc_find_id(ResTable *t, const char *type, const char *name);
/* Returns "package:type/name" into buf or NULL. */
const char *arsc_id_name(ResTable *t, uint32_t id, char *buf, size_t bufsz);
const char *arsc_package_name(ResTable *t);

/* Converts a dimension value to pixels given display density (dpi) and scaled density. */
float res_dimension_to_px(uint32_t data, float density, float scaled_density);
float res_complex_to_float(uint32_t data);
float res_fraction(uint32_t data, float base, float pbase);
/* Produces a human-readable string for any value (malloc'd). */
char *res_value_to_string(const ResValue *v);

#endif
