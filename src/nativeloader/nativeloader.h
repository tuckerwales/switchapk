/*
 * Native library loader (WS9): an ELF64 loader for the APK's lib/<abi>/lib*.so
 * files and the bionic/NDK shim they link against. See ARCHITECTURE 6.7.
 *
 * The VM-facing contract (nativeloader_load_library, nativeloader_find_symbol)
 * is declared where it is used (src/vm/jni.c, src/native/java_misc.c); this
 * header is for the loader's own files.
 */
#ifndef SWITCHAPK_NATIVELOADER_H
#define SWITCHAPK_NATIVELOADER_H

#include "../vm/vm.h"

/* The ABI directory of the APK this build loads from ("x86_64" on the host, "arm64-v8a" on the Switch). */
#if defined(__x86_64__)
#define SA_NATIVE_ABI "x86_64"
#elif defined(__aarch64__)
#define SA_NATIVE_ABI "arm64-v8a"
#else
#define SA_NATIVE_ABI "unsupported"
#endif

typedef struct SaLib SaLib;

/* dlopen/dlsym for the shim's libdl: names without a slash are looked up in the APK, shim names are virtual. */
void *loader_dlopen(const char *name, char *err, size_t errlen);
void *loader_dlsym(void *handle, const char *name); /* handle NULL or RTLD_DEFAULT: everything */
int loader_dlclose(void *handle);
/* Finds the library containing addr; fills the dladdr fields. */
bool loader_dladdr(const void *addr, const char **fname, void **fbase, const char **sname, void **saddr);

/* ---- shim (shim_*.c) ---- */

typedef struct {
    const char *name;
    void *addr;
} ShimSym;

/* True for the system libraries the shim provides (libc.so, liblog.so, libEGL.so, ...). */
bool shim_is_library(const char *soname);
/* Address of a shim symbol, or NULL. */
void *shim_lookup(const char *name);

/* Per-file symbol tables, joined by shim_lookup. */
const ShimSym *shim_libc_symbols(size_t *n);
const ShimSym *shim_android_symbols(size_t *n);

/* Called by native code through a stub when it reaches an import nothing provides. */
uintptr_t loader_unresolved_trap(const char *name);

#endif
