/*
 * ELF64 loader for the APK's native libraries (WS9, ARCHITECTURE 6.7).
 *
 * Loads lib/<abi>/ libraries straight from the APK: maps the PT_LOAD segments,
 * loads DT_NEEDED libraries (from the APK, or the shim for system
 * libraries), applies RELA, Android packed (APS2) and RELR relocations,
 * protects segments, runs DT_INIT/DT_INIT_ARRAY, then JNI_OnLoad.
 *
 * Symbols resolve like Android's linker: the shim (standing in for the
 * global group: libc, libm, liblog, ...) first, then the library's own
 * dependency tree breadth-first. An import nothing provides is bound to a
 * small generated stub that logs the symbol name once and returns 0, and the
 * load goes on (Android would refuse to load; we prefer partial coverage,
 * as with framework auto-stubbing). Weak imports stay 0.
 *
 * Relocations: x86-64 (host tests) and AArch64. ELF TLS (TPREL/DTPMOD/TLSDESC)
 * is not supported: the NDK uses emulated TLS for minSdk < 29.
 *
 * Code memory: mmap/mprotect on the host. On the Switch, heap pages are
 * mirrored into the code (alias) region with svcMapProcessCodeMemory and
 * svcSetProcessMemoryPermission. Horizon rejects write+execute, so a
 * segment that asks for both keeps execute.
 */
#include "nativeloader.h"
#include "../core/zip.h"

#ifndef __SWITCH__
#include <sys/mman.h>
#include <unistd.h>
#else
#include <malloc.h>
#include <switch.h>
#endif

#define LOG_TAG "linker"

/* ---- ELF definitions (no <elf.h> on newlib) --------------------------------------------------------------- */

typedef struct {
    uint8_t e_ident[16];
    uint16_t e_type, e_machine;
    uint32_t e_version;
    uint64_t e_entry, e_phoff, e_shoff;
    uint32_t e_flags;
    uint16_t e_ehsize, e_phentsize, e_phnum, e_shentsize, e_shnum, e_shstrndx;
} Elf64Ehdr;

typedef struct {
    uint32_t p_type, p_flags;
    uint64_t p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align;
} Elf64Phdr;

typedef struct {
    int64_t d_tag;
    uint64_t d_val;
} Elf64Dyn;

typedef struct {
    uint32_t st_name;
    uint8_t st_info, st_other;
    uint16_t st_shndx;
    uint64_t st_value, st_size;
} Elf64Sym;

typedef struct {
    uint64_t r_offset, r_info;
    int64_t r_addend;
} Elf64Rela;

enum {
    ET_DYN = 3,
    EM_X86_64 = 62,
    EM_AARCH64 = 183,
    PT_LOAD = 1,
    PT_DYNAMIC = 2,
    PT_TLS = 7,
    PT_GNU_RELRO = 0x6474e552,
    PF_X = 1,
    PF_W = 2,
    PF_R = 4,
    DT_NULL = 0,
    DT_NEEDED = 1,
    DT_PLTRELSZ = 2,
    DT_HASH = 4,
    DT_STRTAB = 5,
    DT_SYMTAB = 6,
    DT_RELA = 7,
    DT_RELASZ = 8,
    DT_INIT = 12,
    DT_FINI = 13,
    DT_SONAME = 14,
    DT_PLTREL = 20,
    DT_JMPREL = 23,
    DT_INIT_ARRAY = 25,
    DT_FINI_ARRAY = 26,
    DT_INIT_ARRAYSZ = 27,
    DT_FINI_ARRAYSZ = 28,
    DT_RELRSZ = 35,
    DT_RELR = 36,
    DT_ANDROID_RELA = 0x60000011,
    DT_ANDROID_RELASZ = 0x60000012,
    DT_GNU_HASH = 0x6ffffef5,
    DT_ANDROID_RELR = 0x6fffe000,
    DT_ANDROID_RELRSZ = 0x6fffe001,
    SHN_UNDEF = 0,
    STB_LOCAL = 0,
    STB_GLOBAL = 1,
    STB_WEAK = 2,
    STT_TLS = 6,
};

#define ELF_ST_BIND(i) ((i) >> 4)
#define ELF_ST_TYPE(i) ((i)&0xf)
#define ELF_R_SYM(i) ((uint32_t)((i) >> 32))
#define ELF_R_TYPE(i) ((uint32_t)(i))

#if defined(__x86_64__)
#define SA_EM EM_X86_64
enum { R_NONE = 0, R_ABS64 = 1, R_GLOB_DAT = 6, R_JUMP_SLOT = 7, R_RELATIVE = 8, R_IRELATIVE = 37 };
#elif defined(__aarch64__)
#define SA_EM EM_AARCH64
enum { R_NONE = 0, R_ABS64 = 257, R_GLOB_DAT = 1025, R_JUMP_SLOT = 1026, R_RELATIVE = 1027, R_IRELATIVE = 1032 };
#else
#define SA_EM 0
enum { R_NONE = 0, R_ABS64 = -1, R_GLOB_DAT = -2, R_JUMP_SLOT = -3, R_RELATIVE = -4, R_IRELATIVE = -5 };
#endif

/* ---- loaded libraries ------------------------------------------------------------------------------------ */

struct SaLib {
    char *name; /* file name, e.g. "libfoo.so" */
    uint8_t *map;
    size_t map_size;
    uint8_t *bias; /* address of vaddr 0 */
    const char *strtab;
    const Elf64Sym *symtab;
    const uint32_t *sysv_hash;
    const uint32_t *gnu_hash;
    SaLib **needed;
    int nneeded;
    uint8_t *stubs; /* unresolved-import stubs */
    size_t stubs_size, stubs_used;
    int refcount;
    bool constructed;
    SaLib *next;
};

static pthread_mutex_t g_lock;
static pthread_once_t g_lock_once = PTHREAD_ONCE_INIT;
static SaLib *g_libs;

static void init_lock(void) {
    pthread_mutexattr_t a;
    pthread_mutexattr_init(&a);
    pthread_mutexattr_settype(&a, PTHREAD_MUTEX_RECURSIVE); /* constructors may dlopen */
    pthread_mutex_init(&g_lock, &a);
    pthread_mutexattr_destroy(&a);
}

static void lock(void) {
    pthread_once(&g_lock_once, init_lock);
    pthread_mutex_lock(&g_lock);
}

static void unlock(void) { pthread_mutex_unlock(&g_lock); }

/* ---- code memory ---------------------------------------------------------------------------------------- */

#ifndef __SWITCH__
static size_t page_size(void) {
    static size_t ps;
    if (!ps) ps = (size_t)sysconf(_SC_PAGESIZE);
    return ps;
}

static uint8_t *map_rw(size_t size) {
    void *p = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    return p == MAP_FAILED ? NULL : p;
}

static bool protect(void *addr, size_t len, int pflags) {
    int prot = 0;
    if (pflags & PF_R) prot |= PROT_READ;
    if (pflags & PF_W) prot |= PROT_WRITE;
    if (pflags & PF_X) prot |= PROT_EXEC;
    return mprotect(addr, len, prot) == 0;
}

static void unmap(void *addr, size_t len) { munmap(addr, len); }
#else
/*
 * Horizon will not mprotect a heap page to executable. An application (not
 * an applet) may mirror heap pages into the code region:
 *
 *   svcMapProcessCodeMemory(own process, dst, src, size)
 *   svcSetProcessMemoryPermission(own process, dst, size, perm)
 *
 * dst comes from virtmemFindCodeMemory (the alias region). The process
 * handle must be envGetOwnProcessHandle(); the current-process pseudo
 * handle is rejected. hbloader hints syscalls 0x73, 0x77 and 0x78 only for
 * an application launch. After the map, the loader writes at dst (the whole
 * region is read-write), then code pages become read-execute and data pages
 * stay read-write. Unmap gives the pages back to src so they can be freed.
 */
typedef struct CodeRegion {
    uint8_t *dst;
    uint8_t *src;
    size_t size;
    VirtmemReservation *rv;
    struct CodeRegion *next;
} CodeRegion;

static CodeRegion *g_regions;

static bool code_memory_available(void) {
    return envIsSyscallHinted(0x73) && envIsSyscallHinted(0x77) && envIsSyscallHinted(0x78) &&
           envGetOwnProcessHandle() != INVALID_HANDLE;
}

static size_t page_size(void) { return 0x1000; }

static uint8_t *map_rw(size_t size) {
    if (size == 0) return NULL;
    size = (size + 0xFFF) & ~(size_t)0xFFF;
    if (!code_memory_available()) {
        LOGE("code memory is unavailable (svcMapProcessCodeMemory is not hinted). Launch switchapk as an "
             "application, not an applet");
        return NULL;
    }
    void *src = memalign(0x1000, size);
    if (!src) return NULL;
    memset(src, 0, size); /* mmap zeroes; memalign does not, and BSS must be zero */

    virtmemLock();
    void *dst = virtmemFindCodeMemory(size, 0x1000);
    VirtmemReservation *rv = dst ? virtmemAddReservation(dst, size) : NULL;
    virtmemUnlock();
    if (!rv) {
        free(src);
        LOGE("code memory: no alias-region address for %zu bytes", size);
        return NULL;
    }

    Handle self = envGetOwnProcessHandle();
    Result rc = svcMapProcessCodeMemory(self, (u64)dst, (u64)src, size);
    if (R_SUCCEEDED(rc)) {
        rc = svcSetProcessMemoryPermission(self, (u64)dst, size, Perm_Rw);
        if (R_FAILED(rc)) svcUnmapProcessCodeMemory(self, (u64)dst, (u64)src, size);
    }
    if (R_FAILED(rc)) {
        LOGE("svcMapProcessCodeMemory(%zu bytes) failed: 0x%08x", size, (unsigned)rc);
        virtmemLock();
        virtmemRemoveReservation(rv);
        virtmemUnlock();
        free(src);
        return NULL;
    }

    CodeRegion *r = sa_calloc(1, sizeof *r);
    r->dst = dst;
    r->src = src;
    r->size = size;
    r->rv = rv;
    r->next = g_regions;
    g_regions = r;
    return dst;
}

static bool protect(void *addr, size_t len, int pflags) {
    if (len == 0) return true;
    u32 perm = 0;
    if (pflags & PF_R) perm |= Perm_R;
    if (pflags & PF_W) perm |= Perm_W;
    if (pflags & PF_X) perm |= Perm_X;
    /* perm > 5 (write+execute, or anything with both W and X) is rejected. */
    if ((perm & Perm_W) && (perm & Perm_X)) {
        LOGW("code page at %p asks for write and execute; keeping execute", addr);
        perm &= (u32)~Perm_W;
    }
    if (perm == 0) perm = Perm_R;
    Result rc = svcSetProcessMemoryPermission(envGetOwnProcessHandle(), (u64)addr, len, perm);
    if (R_FAILED(rc)) {
        LOGE("svcSetProcessMemoryPermission(%p, %zu, %u) failed: 0x%08x", addr, len, perm, (unsigned)rc);
        return false;
    }
    return true;
}

static void unmap(void *addr, size_t len) {
    SA_UNUSED(len);
    CodeRegion **pp = &g_regions;
    while (*pp && (*pp)->dst != addr) pp = &(*pp)->next;
    CodeRegion *r = *pp;
    if (!r) {
        LOGE("code memory unmap of unknown region %p", addr);
        return;
    }
    *pp = r->next;
    Result rc = svcUnmapProcessCodeMemory(envGetOwnProcessHandle(), (u64)r->dst, (u64)r->src, r->size);
    if (R_FAILED(rc)) LOGE("svcUnmapProcessCodeMemory failed: 0x%08x", (unsigned)rc);
    virtmemLock();
    virtmemRemoveReservation(r->rv);
    virtmemUnlock();
    free(r->src);
    free(r);
}
#endif

static void flush_icache(void *start, size_t len) {
#if defined(__SWITCH__)
    armDCacheFlush(start, len);
    armICacheInvalidate(start, len);
#elif defined(__aarch64__)
    __builtin___clear_cache((char *)start, (char *)start + len);
#else
    SA_UNUSED(start);
    SA_UNUSED(len);
#endif
}

/* ---- unresolved import stubs ------------------------------------------------------------------------------- */

static SaMap g_trapped;
static pthread_mutex_t g_trap_lock = PTHREAD_MUTEX_INITIALIZER;

uintptr_t loader_unresolved_trap(const char *name) {
    pthread_mutex_lock(&g_trap_lock);
    bool first = !sa_map_get(&g_trapped, name);
    if (first) sa_map_put(&g_trapped, name, (void *)1);
    pthread_mutex_unlock(&g_trap_lock);
    if (first) LOGW("native code called %s, which no library provides; returning 0", name);
    return 0;
}

#define STUB_SIZE 32

/* Writes a stub that calls loader_unresolved_trap(name). Returns its address (in the stub page). */
static void *make_stub(SaLib *lib, const char *name) {
    if (!lib->stubs || lib->stubs_used + STUB_SIZE > lib->stubs_size) return NULL;
    uint8_t *p = lib->stubs + lib->stubs_used;
    lib->stubs_used += STUB_SIZE;
    uint64_t n = (uint64_t)(uintptr_t)name, f = (uint64_t)(uintptr_t)loader_unresolved_trap;
#if defined(__x86_64__)
    /* movabs rdi, name; movabs rax, trap; jmp rax */
    p[0] = 0x48, p[1] = 0xbf;
    memcpy(p + 2, &n, 8);
    p[10] = 0x48, p[11] = 0xb8;
    memcpy(p + 12, &f, 8);
    p[20] = 0xff, p[21] = 0xe0;
#elif defined(__aarch64__)
    /* ldr x0, #16; ldr x17, #20; br x17; nop; .quad name; .quad trap */
    uint32_t code[4] = {0x58000080, 0x580000B1, 0xD61F0220, 0xD503201F};
    memcpy(p, code, 16);
    memcpy(p + 16, &n, 8);
    memcpy(p + 24, &f, 8);
#else
    return NULL;
#endif
    return p;
}

/* ---- symbol lookup ------------------------------------------------------------------------------------------ */

static uint32_t gnu_hash(const char *s) {
    uint32_t h = 5381;
    for (; *s; s++) h = h * 33 + (uint8_t)*s;
    return h;
}

static uint32_t sysv_hash(const char *s) {
    uint32_t h = 0, g;
    for (; *s; s++) {
        h = (h << 4) + (uint8_t)*s;
        g = h & 0xf0000000;
        if (g) h ^= g >> 24;
        h &= ~g;
    }
    return h;
}

static bool sym_exported(const Elf64Sym *s) {
    int bind = ELF_ST_BIND(s->st_info);
    return s->st_shndx != SHN_UNDEF && (bind == STB_GLOBAL || bind == STB_WEAK) && ELF_ST_TYPE(s->st_info) != STT_TLS &&
           (s->st_other & 3) != 2 /* hidden */;
}

/* A symbol defined by lib, or NULL. */
static const Elf64Sym *lib_find(const SaLib *lib, const char *name) {
    if (lib->gnu_hash) {
        const uint32_t *h = lib->gnu_hash;
        uint32_t nbuckets = h[0], symoffset = h[1], bloom_size = h[2];
        const uint64_t *bloom = (const uint64_t *)(h + 4);
        const uint32_t *buckets = (const uint32_t *)(bloom + bloom_size);
        const uint32_t *chain = buckets + nbuckets;
        uint32_t hash = gnu_hash(name);
        uint32_t i = buckets[hash % nbuckets];
        if (i < symoffset) return NULL;
        for (;; i++) {
            const Elf64Sym *s = &lib->symtab[i];
            uint32_t ch = chain[i - symoffset];
            if ((hash | 1) == (ch | 1) && strcmp(lib->strtab + s->st_name, name) == 0 && sym_exported(s)) return s;
            if (ch & 1) break;
        }
        return NULL;
    }
    if (lib->sysv_hash) {
        uint32_t nbucket = lib->sysv_hash[0];
        const uint32_t *bucket = lib->sysv_hash + 2;
        const uint32_t *chain = bucket + nbucket;
        for (uint32_t i = bucket[sysv_hash(name) % nbucket]; i; i = chain[i]) {
            const Elf64Sym *s = &lib->symtab[i];
            if (strcmp(lib->strtab + s->st_name, name) == 0 && sym_exported(s)) return s;
        }
    }
    return NULL;
}

static void *lib_sym_addr(const SaLib *lib, const char *name) {
    const Elf64Sym *s = lib_find(lib, name);
    return s ? lib->bias + s->st_value : NULL;
}

/* Breadth-first over lib and its dependencies (the local group). */
static void *group_find(SaLib *root, const char *name) {
    SaLib *queue[256];
    int head = 0, tail = 0;
    queue[tail++] = root;
    while (head < tail) {
        SaLib *l = queue[head++];
        void *p = lib_sym_addr(l, name);
        if (p) return p;
        for (int i = 0; i < l->nneeded && tail < (int)SA_ARRAY_LEN(queue); i++) {
            bool seen = false;
            for (int j = 0; j < tail; j++) seen |= queue[j] == l->needed[i];
            if (!seen) queue[tail++] = l->needed[i];
        }
    }
    return NULL;
}

/* ---- relocation ----------------------------------------------------------------------------------------------- */

typedef struct {
    SaLib *lib;
    char *err;
    size_t errlen;
    int unresolved;
} RelocCtx;

static bool resolve(RelocCtx *c, uint32_t symidx, uint32_t type, uintptr_t *out) {
    SaLib *lib = c->lib;
    if (symidx == 0) {
        *out = 0;
        return true;
    }
    const Elf64Sym *s = &lib->symtab[symidx];
    const char *name = lib->strtab + s->st_name;
    if (ELF_ST_BIND(s->st_info) == STB_LOCAL) {
        *out = (uintptr_t)(lib->bias + s->st_value);
        return true;
    }
    void *p = shim_lookup(name);
    if (!p) p = group_find(lib, name);
    if (p) {
        *out = (uintptr_t)p;
        return true;
    }
    if (ELF_ST_BIND(s->st_info) == STB_WEAK) {
        *out = 0;
        return true;
    }
    c->unresolved++;
    p = make_stub(lib, name);
    LOGW("%s: unresolved %s %s, bound to a stub", lib->name, type == (uint32_t)R_JUMP_SLOT ? "function" : "symbol",
         name);
    *out = (uintptr_t)p;
    return true;
}

static bool apply_rela(RelocCtx *c, const Elf64Rela *r) {
    uint32_t type = ELF_R_TYPE(r->r_info);
    uint32_t sym = ELF_R_SYM(r->r_info);
    uint64_t *where = (uint64_t *)(c->lib->bias + r->r_offset);
    uintptr_t s = 0;
    switch ((int)type) {
    case R_NONE:
        return true;
    case R_RELATIVE:
        *where = (uint64_t)(uintptr_t)(c->lib->bias + r->r_addend);
        return true;
    case R_IRELATIVE: {
        uintptr_t (*resolver)(void) = (uintptr_t(*)(void))(void *)(c->lib->bias + r->r_addend);
        *where = resolver();
        return true;
    }
    case R_ABS64:
    case R_GLOB_DAT:
    case R_JUMP_SLOT:
        if (!resolve(c, sym, type, &s)) return false;
#if defined(__x86_64__)
        /* x86-64 GLOB_DAT/JUMP_SLOT take no addend */
        *where = (type == (uint32_t)R_ABS64) ? s + (uint64_t)r->r_addend : s;
#else
        *where = s + (uint64_t)r->r_addend;
#endif
        return true;
    default:
        snprintf(c->err, c->errlen, "dlopen failed: unknown reloc type %u @ %p (%s)", type, (void *)where,
                 c->lib->name);
        return false;
    }
}

/* Android packed relocations ("APS2", sleb128 groups), as in AOSP linker/linker_reloc_iterators.h. */
typedef struct {
    const uint8_t *p, *end;
} Sleb;

static bool sleb(Sleb *d, int64_t *out) {
    int64_t v = 0;
    unsigned shift = 0;
    uint8_t byte;
    do {
        if (d->p >= d->end) return false;
        byte = *d->p++;
        v |= (int64_t)(byte & 0x7f) << shift;
        shift += 7;
    } while (byte & 0x80);
    if (shift < 64 && (byte & 0x40)) v |= -((int64_t)1 << shift);
    *out = v;
    return true;
}

enum { GROUPED_BY_INFO = 1, GROUPED_BY_OFFSET_DELTA = 2, GROUPED_BY_ADDEND = 4, GROUP_HAS_ADDEND = 8 };

static bool apply_aps2(RelocCtx *c, const uint8_t *data, size_t size) {
    if (size < 4 || memcmp(data, "APS2", 4) != 0) {
        snprintf(c->err, c->errlen, "dlopen failed: bad packed relocation header in %s", c->lib->name);
        return false;
    }
    Sleb d = {data + 4, data + size};
    int64_t count, offset;
    if (!sleb(&d, &count) || !sleb(&d, &offset)) goto bad;
    Elf64Rela r = {(uint64_t)offset, 0, 0};
    while (count > 0) {
        int64_t group_size, flags, delta = 0, v;
        if (!sleb(&d, &group_size) || !sleb(&d, &flags)) goto bad;
        if (flags & GROUPED_BY_OFFSET_DELTA && !sleb(&d, &delta)) goto bad;
        if (flags & GROUPED_BY_INFO) {
            if (!sleb(&d, &v)) goto bad;
            r.r_info = (uint64_t)v;
        }
        if ((flags & GROUP_HAS_ADDEND) && (flags & GROUPED_BY_ADDEND)) {
            if (!sleb(&d, &v)) goto bad;
            r.r_addend += v;
        } else if (!(flags & GROUP_HAS_ADDEND)) {
            r.r_addend = 0;
        }
        for (int64_t i = 0; i < group_size; i++) {
            if (flags & GROUPED_BY_OFFSET_DELTA) {
                r.r_offset += (uint64_t)delta;
            } else {
                if (!sleb(&d, &v)) goto bad;
                r.r_offset += (uint64_t)v;
            }
            if (!(flags & GROUPED_BY_INFO)) {
                if (!sleb(&d, &v)) goto bad;
                r.r_info = (uint64_t)v;
            }
            if ((flags & GROUP_HAS_ADDEND) && !(flags & GROUPED_BY_ADDEND)) {
                if (!sleb(&d, &v)) goto bad;
                r.r_addend += v;
            }
            if (!apply_rela(c, &r)) return false;
        }
        count -= group_size;
    }
    return true;
bad:
    snprintf(c->err, c->errlen, "dlopen failed: truncated packed relocations in %s", c->lib->name);
    return false;
}

/* RELR: relative relocations as an address followed by bitmaps of the next 63 words. */
static void apply_relr(SaLib *lib, const uint64_t *relr, size_t size) {
    uint64_t *where = NULL;
    for (size_t i = 0; i < size / 8; i++) {
        uint64_t e = relr[i];
        if ((e & 1) == 0) {
            where = (uint64_t *)(lib->bias + e);
            *where++ += (uint64_t)(uintptr_t)lib->bias;
        } else {
            uint64_t *w = where;
            for (e >>= 1; e; e >>= 1, w++)
                if (e & 1) *w += (uint64_t)(uintptr_t)lib->bias;
            where += 63;
        }
    }
}

/* ---- loading -------------------------------------------------------------------------------------------------- */

static SaLib *find_loaded(const char *name) {
    for (SaLib *l = g_libs; l; l = l->next)
        if (strcmp(l->name, name) == 0) return l;
    return NULL;
}

static const char *base_name(const char *path) {
    const char *s = strrchr(path, '/');
    return s ? s + 1 : path;
}

extern ZipArchive *g_app_zip;
char *platform_map_path(const char *android_path);

/* Reads a library: an absolute path from the file system, else lib/<abi>/<name> in the APK. */
static uint8_t *read_library(const char *name, size_t *len) {
    if (name[0] == '/') {
        char *host = platform_map_path(name);
        FILE *f = host ? fopen(host, "rb") : NULL;
        free(host);
        if (f) {
            fseek(f, 0, SEEK_END);
            long n = ftell(f);
            fseek(f, 0, SEEK_SET);
            uint8_t *data = n > 0 ? malloc((size_t)n) : NULL;
            if (data && fread(data, 1, (size_t)n, f) == (size_t)n) {
                fclose(f);
                *len = (size_t)n;
                return data;
            }
            free(data);
            fclose(f);
        }
        /* nativeLibraryDir paths and the like: fall back to the APK by file name */
    }
    if (!g_app_zip) return NULL;
    char path[512];
    snprintf(path, sizeof path, "lib/%s/%s", SA_NATIVE_ABI, base_name(name));
    return zip_extract_name(g_app_zip, path, len);
}

static SaLib *load_locked(const char *name, char *err, size_t errlen, int depth);

static void free_lib(SaLib *lib) {
    if (lib->map) unmap(lib->map, lib->map_size);
    if (lib->stubs) unmap(lib->stubs, lib->stubs_size);
    free(lib->needed);
    free(lib->name);
    free(lib);
}

static SaLib *load_image(const char *name, const uint8_t *data, size_t len, char *err, size_t errlen, int depth) {
    const Elf64Ehdr *eh = (const Elf64Ehdr *)data;
    if (len < sizeof *eh || memcmp(eh->e_ident, "\x7f" "ELF", 4) != 0) {
        snprintf(err, errlen, "dlopen failed: \"%s\" has bad ELF magic", name);
        return NULL;
    }
    if (eh->e_ident[4] != 2 /* ELFCLASS64 */ || eh->e_machine != SA_EM) {
        snprintf(err, errlen, "dlopen failed: \"%s\" is for another ABI (e_machine %u, want %u: %s)", name,
                 eh->e_machine, SA_EM, SA_NATIVE_ABI);
        return NULL;
    }
    if (eh->e_type != ET_DYN || eh->e_phoff + (uint64_t)eh->e_phnum * sizeof(Elf64Phdr) > len) {
        snprintf(err, errlen, "dlopen failed: \"%s\" is not a shared object", name);
        return NULL;
    }
    const Elf64Phdr *ph = (const Elf64Phdr *)(data + eh->e_phoff);
    size_t ps = page_size();
    uint64_t lo = UINT64_MAX, hi = 0;
    const Elf64Phdr *dyn_ph = NULL;
    for (int i = 0; i < eh->e_phnum; i++) {
        if (ph[i].p_type == PT_LOAD) {
            if (ph[i].p_vaddr < lo) lo = ph[i].p_vaddr;
            if (ph[i].p_vaddr + ph[i].p_memsz > hi) hi = ph[i].p_vaddr + ph[i].p_memsz;
            if (ph[i].p_offset + ph[i].p_filesz > len) {
                snprintf(err, errlen, "dlopen failed: \"%s\" segment beyond the file", name);
                return NULL;
            }
        } else if (ph[i].p_type == PT_DYNAMIC) {
            dyn_ph = &ph[i];
        } else if (ph[i].p_type == PT_TLS) {
            LOGW("%s uses ELF TLS, which is not supported (build with emulated TLS / minSdk < 29)", name);
        }
    }
    if (lo == UINT64_MAX || !dyn_ph) {
        snprintf(err, errlen, "dlopen failed: \"%s\" has no loadable segments or no dynamic section", name);
        return NULL;
    }
    lo &= ~(uint64_t)(ps - 1);
    hi = (hi + ps - 1) & ~(uint64_t)(ps - 1);

    SaLib *lib = sa_calloc(1, sizeof *lib);
    lib->name = sa_strdup(base_name(name));
    lib->map_size = (size_t)(hi - lo);
    lib->map = map_rw(lib->map_size);
    if (!lib->map) {
        snprintf(err, errlen, "dlopen failed: cannot map \"%s\" (%zu bytes)%s", name, lib->map_size,
#ifdef __SWITCH__
                 " (code memory unavailable: launch switchapk as an application, not an applet)"
#else
                 ""
#endif
        );
        free_lib(lib);
        return NULL;
    }
    lib->bias = lib->map - lo;
    for (int i = 0; i < eh->e_phnum; i++)
        if (ph[i].p_type == PT_LOAD) memcpy(lib->bias + ph[i].p_vaddr, data + ph[i].p_offset, ph[i].p_filesz);

    /* dynamic section */
    const Elf64Dyn *dyn = (const Elf64Dyn *)(lib->bias + dyn_ph->p_vaddr);
    uint64_t rela = 0, relasz = 0, jmprel = 0, pltrelsz = 0, arela = 0, arelasz = 0, relr = 0, relrsz = 0;
    uint64_t init = 0, init_array = 0, init_arraysz = 0;
    int nneeded = 0;
    for (const Elf64Dyn *d = dyn; d->d_tag != DT_NULL; d++) {
        switch (d->d_tag) {
        case DT_NEEDED: nneeded++; break;
        case DT_STRTAB: lib->strtab = (const char *)(lib->bias + d->d_val); break;
        case DT_SYMTAB: lib->symtab = (const Elf64Sym *)(lib->bias + d->d_val); break;
        case DT_HASH: lib->sysv_hash = (const uint32_t *)(lib->bias + d->d_val); break;
        case DT_GNU_HASH: lib->gnu_hash = (const uint32_t *)(lib->bias + d->d_val); break;
        case DT_RELA: rela = d->d_val; break;
        case DT_RELASZ: relasz = d->d_val; break;
        case DT_JMPREL: jmprel = d->d_val; break;
        case DT_PLTRELSZ: pltrelsz = d->d_val; break;
        case DT_ANDROID_RELA: arela = d->d_val; break;
        case DT_ANDROID_RELASZ: arelasz = d->d_val; break;
        case DT_RELR: case DT_ANDROID_RELR: relr = d->d_val; break;
        case DT_RELRSZ: case DT_ANDROID_RELRSZ: relrsz = d->d_val; break;
        case DT_INIT: init = d->d_val; break;
        case DT_INIT_ARRAY: init_array = d->d_val; break;
        case DT_INIT_ARRAYSZ: init_arraysz = d->d_val; break;
        default: break;
        }
    }
    if (!lib->strtab || !lib->symtab || (!lib->gnu_hash && !lib->sysv_hash)) {
        snprintf(err, errlen, "dlopen failed: \"%s\" has no symbol tables", name);
        free_lib(lib);
        return NULL;
    }

    /* register before loading dependencies so cycles terminate */
    lib->refcount = 1;
    lib->next = g_libs;
    g_libs = lib;

    lib->needed = sa_calloc((size_t)nneeded + 1, sizeof(SaLib *));
    for (const Elf64Dyn *d = dyn; d->d_tag != DT_NULL; d++) {
        if (d->d_tag != DT_NEEDED) continue;
        const char *dep = lib->strtab + d->d_val;
        if (shim_is_library(dep)) continue;
        SaLib *l = load_locked(dep, err, errlen, depth + 1);
        if (!l) {
            char inner[256];
            snprintf(inner, sizeof inner, "%s", err);
            snprintf(err, errlen, "dlopen failed: library \"%s\" needed by \"%s\": %s", dep, lib->name, inner);
            g_libs = lib->next;
            free_lib(lib);
            return NULL;
        }
        lib->needed[lib->nneeded++] = l;
    }

    /* one stub page per library for imports nobody provides */
    size_t nsyms_upper = (relasz + pltrelsz) / sizeof(Elf64Rela) + arelasz / 2 + 16;
    lib->stubs_size = (nsyms_upper * STUB_SIZE + ps - 1) & ~(ps - 1);
    lib->stubs = map_rw(lib->stubs_size);

    RelocCtx c = {lib, err, errlen, 0};
    bool ok = true;
    if (relr) apply_relr(lib, (const uint64_t *)(lib->bias + relr), relrsz);
    if (ok && arela) ok = apply_aps2(&c, lib->bias + arela, arelasz);
    for (uint64_t off = 0; ok && rela && off < relasz; off += sizeof(Elf64Rela))
        ok = apply_rela(&c, (const Elf64Rela *)(lib->bias + rela + off));
    for (uint64_t off = 0; ok && jmprel && off < pltrelsz; off += sizeof(Elf64Rela))
        ok = apply_rela(&c, (const Elf64Rela *)(lib->bias + jmprel + off));
    if (!ok) {
        g_libs = lib->next;
        free_lib(lib);
        return NULL;
    }
    if (c.unresolved) LOGW("%s: %d imports unresolved (see above)", lib->name, c.unresolved);

    /* final protections: segment flags, then RELRO read-only */
    bool prot_ok = true;
    for (int i = 0; prot_ok && i < eh->e_phnum; i++) {
        if (ph[i].p_type != PT_LOAD) continue;
        uint64_t s = ph[i].p_vaddr & ~(uint64_t)(ps - 1);
        uint64_t e = (ph[i].p_vaddr + ph[i].p_memsz + ps - 1) & ~(uint64_t)(ps - 1);
        prot_ok = protect(lib->bias + s, (size_t)(e - s), (int)ph[i].p_flags);
        if (prot_ok && (ph[i].p_flags & PF_X)) flush_icache(lib->bias + s, (size_t)(e - s));
    }
    for (int i = 0; prot_ok && i < eh->e_phnum; i++) {
        if (ph[i].p_type != PT_GNU_RELRO) continue;
        uint64_t s = ph[i].p_vaddr & ~(uint64_t)(ps - 1);
        uint64_t e = (ph[i].p_vaddr + ph[i].p_memsz) & ~(uint64_t)(ps - 1);
        if (e > s) prot_ok = protect(lib->bias + s, (size_t)(e - s), PF_R);
    }
    if (prot_ok && lib->stubs) {
        prot_ok = protect(lib->stubs, lib->stubs_size, PF_R | PF_X);
        if (prot_ok) flush_icache(lib->stubs, lib->stubs_size);
    }
    if (!prot_ok) {
        snprintf(err, errlen, "dlopen failed: cannot set memory permissions for \"%s\"", name);
        g_libs = lib->next;
        free_lib(lib);
        return NULL;
    }

    /* constructors: dependencies ran theirs when they loaded */
    LOGI("loaded %s at %p", lib->name, (void *)lib->bias);
    if (init) ((void (*)(void))(void *)(lib->bias + init))();
    if (init_array) {
        void (**fns)(void) = (void (**)(void))(void *)(lib->bias + init_array);
        for (size_t i = 0; i < init_arraysz / sizeof(void *); i++)
            if (fns[i] && (uintptr_t)fns[i] != (uintptr_t)-1) fns[i]();
    }
    lib->constructed = true;
    return lib;
}

static SaLib *load_locked(const char *name, char *err, size_t errlen, int depth) {
    SaLib *l = find_loaded(base_name(name));
    if (l) {
        l->refcount++;
        return l;
    }
    if (depth > 32) {
        snprintf(err, errlen, "dlopen failed: dependency chain too deep at \"%s\"", name);
        return NULL;
    }
    size_t len = 0;
    uint8_t *data = read_library(name, &len);
    if (!data) {
        snprintf(err, errlen, "dlopen failed: library \"%s\" not found", base_name(name));
        return NULL;
    }
    l = load_image(name, data, len, err, errlen, depth);
    free(data);
    return l;
}

/* ---- dl* for the shim -------------------------------------------------------------------------------------- */

/* Handle for a shim library: dlsym on it searches the shim tables. */
static char g_shim_handle;
#define SA_RTLD_DEFAULT ((void *)0)

void *loader_dlopen(const char *name, char *err, size_t errlen) {
    if (!name) return &g_shim_handle; /* dlopen(NULL): the "main program" */
    if (shim_is_library(base_name(name))) return &g_shim_handle;
    lock();
    SaLib *l = load_locked(name, err, errlen, 0);
    unlock();
    return l;
}

void *loader_dlsym(void *handle, const char *name) {
    if (!name) return NULL;
    if (handle == &g_shim_handle) return shim_lookup(name);
    lock();
    void *p = NULL;
    if (handle == SA_RTLD_DEFAULT || (uintptr_t)handle == (uintptr_t)-1 /* RTLD_NEXT */) {
        p = shim_lookup(name);
        for (SaLib *l = g_libs; l && !p; l = l->next) p = lib_sym_addr(l, name);
    } else {
        p = group_find((SaLib *)handle, name);
    }
    unlock();
    return p;
}

int loader_dlclose(void *handle) {
    /* libraries stay loaded (Android rarely unloads either; static destructors are not run) */
    SA_UNUSED(handle);
    return 0;
}

bool loader_dladdr(const void *addr, const char **fname, void **fbase, const char **sname, void **saddr) {
    lock();
    bool found = false;
    for (SaLib *l = g_libs; l && !found; l = l->next) {
        if ((const uint8_t *)addr < l->map || (const uint8_t *)addr >= l->map + l->map_size) continue;
        found = true;
        *fname = l->name;
        *fbase = l->map;
        *sname = NULL;
        *saddr = NULL;
        /* nearest exported symbol at or below addr */
        uint32_t nsyms = 0;
        if (l->sysv_hash) {
            nsyms = l->sysv_hash[1];
        } else if (l->gnu_hash) {
            const uint32_t *h = l->gnu_hash;
            const uint32_t *buckets = (const uint32_t *)((const uint64_t *)(h + 4) + h[2]);
            const uint32_t *chain = buckets + h[0];
            uint32_t maxb = 0;
            for (uint32_t b = 0; b < h[0]; b++)
                if (buckets[b] > maxb) maxb = buckets[b];
            if (maxb >= h[1]) {
                while (!(chain[maxb - h[1]] & 1)) maxb++;
                nsyms = maxb + 1;
            }
        }
        uintptr_t best = 0;
        for (uint32_t i = 1; i < nsyms; i++) {
            const Elf64Sym *s = &l->symtab[i];
            if (!sym_exported(s)) continue;
            uintptr_t a = (uintptr_t)(l->bias + s->st_value);
            if (a <= (uintptr_t)addr && a + (s->st_size ? s->st_size : 1) > (uintptr_t)addr && a >= best) {
                best = a;
                *sname = l->strtab + s->st_name;
                *saddr = (void *)a;
            }
        }
    }
    unlock();
    return found;
}

/* ---- VM contract ----------------------------------------------------------------------------------------------- */

const char *nativeloader_load_library(VMThread *t, const char *name, bool is_libname) {
    static _Thread_local char err[512];
    char file[256];
    if (is_libname) {
        snprintf(file, sizeof file, "lib%s.so", name);
        name = file;
    }
    err[0] = 0;
    lock();
    SaLib *existing = find_loaded(base_name(name));
    if (existing) {
        unlock();
        return NULL;
    }
    /* constructors and JNI_OnLoad run native code: drop the GIL like JNI calls do */
    vm_gil_release(t);
    SaLib *lib = load_locked(name, err, sizeof err, 0);
    unlock();
    if (!lib) {
        vm_gil_acquire(t);
        if (!err[0]) snprintf(err, sizeof err, "dlopen failed: library \"%s\" not found", name);
        LOGW("%s", err);
        return err;
    }
    typedef int32_t (*OnLoad)(void *vm, void *reserved);
    OnLoad onload = (OnLoad)lib_sym_addr(lib, "JNI_OnLoad");
    int32_t version = 0x00010006;
    if (onload) {
        vm_gil_acquire(t);
        vm_jni_push_frame(t);
        vm_gil_release(t);
        version = onload(vm_java_vm(), NULL);
        vm_gil_acquire(t);
        vm_jni_pop_frame(t);
    } else {
        vm_gil_acquire(t);
    }
    if (t->exception) return NULL;
    if (version != 0x00010002 && version != 0x00010004 && version != 0x00010006 && version != 0x00010008 &&
        version != 0x00090000 && version != 0x000a0000 && version != 0x00130000 && version != 0x00140000 &&
        version != 0x00150000) {
        snprintf(err, sizeof err, "JNI_ERR returned from JNI_OnLoad in \"%s\"", lib->name);
        return err;
    }
    return NULL;
}

void *nativeloader_find_symbol(const char *name) {
    lock();
    void *p = NULL;
    for (SaLib *l = g_libs; l && !p; l = l->next) p = lib_sym_addr(l, name);
    unlock();
    return p;
}
