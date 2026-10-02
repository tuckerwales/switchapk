/*
 * Shared state and helpers for the Android framework natives.
 */
#ifndef SWITCHAPK_ANDROID_H
#define SWITCHAPK_ANDROID_H

#include "../native/natives.h"
#include "../core/res.h"
#include "../core/zip.h"

/* Resource table holding the framework (0x01) and app (0x7f) packages. */
extern ResTable *g_res;
extern ZipArchive *g_fw_res_zip;
extern ZipArchive *g_app_zip;
/* Absolute or process-relative path of the running APK. Set by the app runner. */
extern char *g_app_apk_path;

/* Loads framework-res.apk and the app's resources.arsc. */
bool android_res_init(const char *framework_res_path, ZipArchive *app);

/* Looks up (once) and returns a field of the object's class hierarchy. */
static inline Field *nat_field(Field **cache, Object *o, const char *name, const char *type) {
    if (!*cache) {
        *cache = vm_find_field(o->clazz, name, type);
        if (!*cache) sa_fatal("framework field %s.%s (%s) missing", o->clazz->name, name, type);
    }
    return *cache;
}

#define FIELD_INT(o, name) (*(int32_t *)vm_field_ptr((o), ({ static Field *f_; nat_field(&f_, (o), name, "I"); })))
#define FIELD_FLOAT(o, name) (*(float *)vm_field_ptr((o), ({ static Field *f_; nat_field(&f_, (o), name, "F"); })))
#define FIELD_BOOL(o, name) (*(uint8_t *)vm_field_ptr((o), ({ static Field *f_; nat_field(&f_, (o), name, "Z"); })))
#define FIELD_LONG(o, name) (*(int64_t *)vm_field_ptr((o), ({ static Field *f_; nat_field(&f_, (o), name, "J"); })))
#define FIELD_OBJ(o, name, type) (*(Object **)vm_field_ptr((o), ({ static Field *f_; nat_field(&f_, (o), name, type); })))

/* Registration entry points (one per src/android file). */
void android_res_register(void);
void android_graphics_register(void);
void android_os_register(void);
void android_media_register(void);
void android_opengl_register(void);
void native_activity_register(void);

/* Platform event plumbing between android_os.c and the app runner. */
void android_request_quit(void);

#endif
