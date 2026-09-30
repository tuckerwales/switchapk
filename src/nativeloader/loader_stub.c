/* Temporary: replaced by the ELF loader. */
#include "../vm/vm.h"

const char *nativeloader_load_library(VMThread *t, const char *name, bool is_libname) {
    SA_UNUSED(t);
    SA_UNUSED(is_libname);
    static char err[256];
    snprintf(err, sizeof err, "dlopen failed: library \"%s\" not found", name);
    return err;
}

void *nativeloader_find_symbol(const char *name) {
    SA_UNUSED(name);
    return NULL;
}
