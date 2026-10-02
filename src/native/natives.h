/*
 * Helpers shared by native method implementations.
 */
#ifndef SWITCHAPK_NATIVES_H
#define SWITCHAPK_NATIVES_H

#include "../vm/vm.h"

static inline float nat_u2f(uint32_t u) {
    float f;
    memcpy(&f, &u, 4);
    return f;
}
static inline double nat_u2d(uint64_t u) {
    double d;
    memcpy(&d, &u, 8);
    return d;
}

/* argument slot accessors (wide values occupy two slots) */
#define A_OBJ(i) ((Object *)(uintptr_t)args[(i)])
#define A_ARR(i) ((ArrayObject *)(uintptr_t)args[(i)])
#define A_INT(i) ((int32_t)(uint32_t)args[(i)])
#define A_BOOL(i) (((uint32_t)args[(i)]) != 0)
#define A_LONG(i) ((int64_t)args[(i)])
#define A_FLOAT(i) (nat_u2f((uint32_t)args[(i)]))
#define A_DOUBLE(i) (nat_u2d(args[(i)]))

#define R_INT(v) (ret->raw = (uint32_t)(int32_t)(v))
#define R_BOOL(v) (ret->raw = (v) ? 1u : 0u)
#define R_LONG(v) (ret->j = (int64_t)(v))
#define R_FLOAT(v) (ret->f = (float)(v), ret->raw &= 0xffffffffu)
#define R_DOUBLE(v) (ret->d = (double)(v))
#define R_OBJ(v) (ret->l = (Object *)(v))

#define NATIVE(fn) static void fn(VMThread *t, uint64_t *args, JValue *ret)
#define UNUSED_ARGS() \
    do {              \
        SA_UNUSED(t); \
        SA_UNUSED(args); \
        SA_UNUSED(ret); \
    } while (0)

/* NULL-safe conversion of a java.lang.String argument to malloc'd UTF-8. */
static inline char *nat_str(Object *s) { return s ? vm_string_to_utf8(s) : NULL; }

void natives_java_lang_register(void);
void natives_java_io_register(void);
void natives_java_reflect_register(void);
void natives_java_annotation_register(void);
void natives_java_misc_register(void);
void natives_android_register(void);

/* Path translation between Android paths and the platform file system. */
char *platform_map_path(const char *android_path);

#endif
