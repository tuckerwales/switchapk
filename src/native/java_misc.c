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
    {"Ljava/lang/Runtime;", "nativeLoad", "(Ljava/lang/String;Z)Ljava/lang/String;", Runtime_nativeLoad},
    {"Ljava/lang/System;", "nativeArch", "()Ljava/lang/String;", System_nativeArch},
};

void natives_java_misc_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
