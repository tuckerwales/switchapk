/*
 * Assorted natives: class loader resources, native library loading hooks.
 */
#include "natives.h"
#include "../core/zip.h"

#define LOG_TAG "misc"

/* The application package, used for resources on the class path. Set by the app loader. */
ZipArchive *g_app_zip;

/* Implemented by the native library loader (src/nativeloader). Returns NULL on success or an error message. */
const char *nativeloader_load_library(VMThread *t, const char *name, bool is_libname);

NATIVE(ClassLoader_getResourceBytes) {
    UNUSED_ARGS();
    char *name = nat_str(A_OBJ(0));
    if (!name || !g_app_zip) {
        free(name);
        return;
    }
    size_t len = 0;
    uint8_t *data = zip_extract_name(g_app_zip, name, &len);
    free(name);
    if (!data) return;
    ArrayObject *a = vm_alloc_prim_array(t, 'B', (int32_t)len);
    if (a) memcpy(a->data, data, len);
    free(data);
    R_OBJ(a);
}

extern char *g_app_apk_path;

/* ClassLoader.appApkPath(): the APK whose entries are the class path's resources, or null. */
NATIVE(ClassLoader_appApkPath) {
    UNUSED_ARGS();
    R_OBJ(g_app_apk_path ? vm_new_string_utf8(t, g_app_apk_path) : NULL);
}

/* ClassLoader.hasAppResource(name): whether the APK has that entry, without extracting it. */
NATIVE(ClassLoader_hasAppResource) {
    UNUSED_ARGS();
    char *name = nat_str(A_OBJ(0));
    bool found = name && g_app_zip && zip_find(g_app_zip, name) != NULL;
    free(name);
    R_BOOL(found);
}

NATIVE(Runtime_nativeLoad) {
    UNUSED_ARGS();
    char *name = nat_str(A_OBJ(0));
    if (!name) {
        vm_throw_npe(t, "library name");
        return;
    }
    const char *err = nativeloader_load_library(t, name, A_BOOL(1));
    free(name);
    if (t->exception) return;
    R_OBJ(err ? vm_new_string_utf8(t, err) : NULL);
}

NATIVE(System_nativeArch) {
    UNUSED_ARGS();
#if defined(__x86_64__)
    R_OBJ(vm_new_string_utf8(t, "x86_64"));
#else
    R_OBJ(vm_new_string_utf8(t, "aarch64"));
#endif
}

static const NativeMethodReg g_regs[] = {
    {"Ljava/lang/ClassLoader;", "getResourceBytes", "(Ljava/lang/String;)[B", ClassLoader_getResourceBytes},
    {"Ljava/lang/ClassLoader;", "appApkPath", "()Ljava/lang/String;", ClassLoader_appApkPath},
    {"Ldalvik/system/BaseDexClassLoader;", "appApkPath", "()Ljava/lang/String;", ClassLoader_appApkPath},
    {"Ldalvik/system/BaseDexClassLoader;", "hasAppResource", "(Ljava/lang/String;)Z", ClassLoader_hasAppResource},
    {"Ljava/lang/Runtime;", "nativeLoad", "(Ljava/lang/String;Z)Ljava/lang/String;", Runtime_nativeLoad},
    {"Ljava/lang/System;", "nativeArch", "()Ljava/lang/String;", System_nativeArch},
};

void natives_java_misc_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
