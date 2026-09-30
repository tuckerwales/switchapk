/*
 * Dalvik Executable (.dex) file parser.
 */
#ifndef SWITCHAPK_DEX_H
#define SWITCHAPK_DEX_H

#include "common.h"

#define DEX_NO_INDEX 0xffffffffu

/* access flags */
enum {
    ACC_PUBLIC = 0x0001,
    ACC_PRIVATE = 0x0002,
    ACC_PROTECTED = 0x0004,
    ACC_STATIC = 0x0008,
    ACC_FINAL = 0x0010,
    ACC_SYNCHRONIZED = 0x0020,
    ACC_VOLATILE = 0x0040,
    ACC_BRIDGE = 0x0040,
    ACC_TRANSIENT = 0x0080,
    ACC_VARARGS = 0x0080,
    ACC_NATIVE = 0x0100,
    ACC_INTERFACE = 0x0200,
    ACC_ABSTRACT = 0x0400,
    ACC_STRICT = 0x0800,
    ACC_SYNTHETIC = 0x1000,
    ACC_ANNOTATION = 0x2000,
    ACC_ENUM = 0x4000,
    ACC_CONSTRUCTOR = 0x10000,
    ACC_DECLARED_SYNCHRONIZED = 0x20000,
};

typedef struct {
    uint32_t shorty_idx;
    uint32_t return_type_idx;
    uint32_t params_off;
} DexProtoId;

typedef struct {
    uint16_t class_idx;
    uint16_t type_idx;
    uint32_t name_idx;
} DexFieldId;

typedef struct {
    uint16_t class_idx;
    uint16_t proto_idx;
    uint32_t name_idx;
} DexMethodId;

typedef struct {
    uint32_t class_idx;
    uint32_t access_flags;
    uint32_t superclass_idx;
    uint32_t interfaces_off;
    uint32_t source_file_idx;
    uint32_t annotations_off;
    uint32_t class_data_off;
    uint32_t static_values_off;
} DexClassDef;

typedef struct {
    uint32_t start_addr;
    uint16_t insn_count;
    uint16_t handler_off;
} DexTry;

typedef struct {
    uint16_t registers_size;
    uint16_t ins_size;
    uint16_t outs_size;
    uint16_t tries_size;
    uint32_t debug_info_off;
    uint32_t insns_size;
    const uint16_t *insns;
    const DexTry *tries;        /* unaligned-safe copy */
    const uint8_t *handlers;    /* encoded_catch_handler_list */
} DexCode;

typedef struct {
    uint32_t field_idx;
    uint32_t access_flags;
} DexFieldEntry;

typedef struct {
    uint32_t method_idx;
    uint32_t access_flags;
    uint32_t code_off;
} DexMethodEntry;

typedef struct {
    uint32_t static_fields_size, instance_fields_size, direct_methods_size, virtual_methods_size;
    DexFieldEntry *static_fields, *instance_fields;
    DexMethodEntry *direct_methods, *virtual_methods;
} DexClassData;

typedef struct DexFile {
    const uint8_t *base;
    size_t size;
    bool owned;
    char *location; /* for diagnostics */
    uint32_t string_ids_size, type_ids_size, proto_ids_size, field_ids_size, method_ids_size, class_defs_size;
    const uint8_t *string_ids, *type_ids, *proto_ids, *field_ids, *method_ids, *class_defs;
    const char **string_cache; /* interned C strings, lazily populated */
    SaMap class_index;         /* descriptor -> (class_def index + 1) */
    /* per-file resolution caches used by the VM */
    void **resolved_types;
    void **resolved_methods;
    void **resolved_fields;
    void **resolved_strings;
} DexFile;

/* data is copied if copy=true, otherwise must outlive the DexFile. */
DexFile *dex_open(const uint8_t *data, size_t len, bool copy, const char *location);
void dex_close(DexFile *d);

uint32_t dex_uleb128(const uint8_t **p);
int32_t dex_sleb128(const uint8_t **p);

/* Returns an interned MUTF-8 string. */
const char *dex_string(DexFile *d, uint32_t idx);
const char *dex_type_desc(DexFile *d, uint32_t type_idx);
void dex_proto(const DexFile *d, uint32_t idx, DexProtoId *out);
void dex_field_id(const DexFile *d, uint32_t idx, DexFieldId *out);
void dex_method_id(const DexFile *d, uint32_t idx, DexMethodId *out);
void dex_class_def(const DexFile *d, uint32_t idx, DexClassDef *out);
/* Builds a method descriptor "(II)V" as an interned string. */
const char *dex_proto_desc(DexFile *d, uint32_t proto_idx);
const char *dex_proto_shorty(DexFile *d, uint32_t proto_idx);
/* Parameter type list: count + type index accessor */
uint32_t dex_type_list_size(const DexFile *d, uint32_t off);
uint32_t dex_type_list_item(const DexFile *d, uint32_t off, uint32_t i);

int32_t dex_find_class(const DexFile *d, const char *descriptor);
bool dex_class_data(const DexFile *d, uint32_t off, DexClassData *out);
void dex_class_data_free(DexClassData *cd);
bool dex_code(const DexFile *d, uint32_t off, DexCode *out);

/* Encoded value reading (static initial values, annotations). */
typedef struct {
    uint8_t type;
    union {
        int32_t i;
        int64_t j;
        float f;
        double d;
        uint32_t idx; /* string/type/field/method index */
        bool z;
    } u;
    const uint8_t *array; /* for arrays/annotations: position of payload */
} DexEncodedValue;

enum {
    DEV_BYTE = 0x00, DEV_SHORT = 0x02, DEV_CHAR = 0x03, DEV_INT = 0x04, DEV_LONG = 0x06,
    DEV_FLOAT = 0x10, DEV_DOUBLE = 0x11, DEV_METHOD_TYPE = 0x15, DEV_METHOD_HANDLE = 0x16,
    DEV_STRING = 0x17, DEV_TYPE = 0x18, DEV_FIELD = 0x19, DEV_METHOD = 0x1a, DEV_ENUM = 0x1b,
    DEV_ARRAY = 0x1c, DEV_ANNOTATION = 0x1d, DEV_NULL = 0x1e, DEV_BOOLEAN = 0x1f,
};

/* Reads one encoded value and advances *p. */
void dex_read_encoded_value(const uint8_t **p, DexEncodedValue *out);
void dex_skip_encoded_value(const uint8_t **p);
void dex_skip_annotation(const uint8_t **p);

/* Finds the catch handler for an exception at dex pc; iterates handlers of the try block.
 * Returns pointer to encoded_catch_handler or NULL. */
const uint8_t *dex_find_try_handlers(const DexCode *code, uint32_t pc);

/* Debug info: map dex pc to source line (returns -1 if unknown). */
int dex_line_for_pc(DexFile *d, const DexCode *code, uint32_t pc);

/* Annotations: returns the class-level annotation set; used for
 * dalvik/annotation/Signature, InnerClass, etc. The callback receives the
 * annotation type descriptor and a pointer to the encoded annotation elements. */
typedef void (*DexAnnotationFn)(DexFile *d, const char *type, uint32_t visibility, const uint8_t *elements,
                                void *ctx);
void dex_class_annotations(DexFile *d, const DexClassDef *cd, DexAnnotationFn fn, void *ctx);

#endif
