/*
 * libc/libm shim for native libraries (WS9): bionic's exported names mapped
 * onto the host C library (glibc on the host, newlib on the Switch).
 *
 * Most functions have the same ABI and map directly. Wrappers cover what
 * differs from bionic:
 * - paths: open/fopen/stat/... translate Android paths (platform_map_path);
 * - sysconf constants, which are numbered differently;
 * - pthread mutexes, condition variables and attributes: bionic's objects
 *   are fixed-size and zero-initialized; the wrappers keep a pointer to a
 *   lazily created host object inside them;
 * - stdio: old NDK code reaches stdin/stdout/stderr as &__sF[n];
 * - fortify (_chk) entry points, errno (__errno), stack protector, atexit.
 *
 * Struct layouts are checked for 64-bit Linux hosts (x86-64 and AArch64
 * glibc match bionic for stat, dirent, tm, timespec). The Switch build
 * needs translation wrappers for those (TODO WS9: newlib layouts and
 * O_* / clock ids).
 */
#include "nativeloader.h"

#include <ctype.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#ifdef __SWITCH__
#include <malloc.h>
#endif
#include <math.h>
#include <setjmp.h>
#include <stdarg.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#ifndef __SWITCH__
#include <malloc.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <sys/time.h>
#endif

#define LOG_TAG "libc"

char *platform_map_path(const char *android_path);

/* ---- errno, stack protector, exit hooks ----------------------------------------------------------------- */

static int *sh_errno(void) { return &errno; }

uintptr_t sh_stack_chk_guard = 0x5a5f5c4e4b3a2f1eULL;

static void sh_stack_chk_fail(void) {
    LOGE("stack corruption detected in native code (__stack_chk_fail)");
    abort();
}

static int sh_cxa_atexit(void (*fn)(void *), void *arg, void *dso) {
    /* libraries are never unloaded and the process exits without running native destructors */
    SA_UNUSED(fn);
    SA_UNUSED(arg);
    SA_UNUSED(dso);
    return 0;
}

static void sh_cxa_finalize(void *dso) { SA_UNUSED(dso); }

static int sh_cxa_thread_atexit_impl(void (*fn)(void *), void *obj, void *dso) {
    SA_UNUSED(fn);
    SA_UNUSED(obj);
    SA_UNUSED(dso);
    return 0;
}

static int sh_atexit(void (*fn)(void)) {
    SA_UNUSED(fn);
    return 0;
}

static int sh_register_atfork(void (*prepare)(void), void (*parent)(void), void (*child)(void), void *dso) {
    SA_UNUSED(prepare);
    SA_UNUSED(parent);
    SA_UNUSED(child);
    SA_UNUSED(dso);
    return 0;
}

static void sh_assert2(const char *file, int line, const char *function, const char *expr) {
    LOGE("%s:%d: %s: assertion \"%s\" failed", file, line, function, expr);
    abort();
}

static void sh_assert(const char *file, int line, const char *expr) {
    LOGE("%s:%d: assertion \"%s\" failed", file, line, expr);
    abort();
}

static unsigned long sh_getauxval(unsigned long type) {
    SA_UNUSED(type);
    return 0;
}

static int sh_gettid(void) {
#ifdef SYS_gettid
    return (int)syscall(SYS_gettid);
#else
    return (int)getpid();
#endif
}

/* ---- strings ------------------------------------------------------------------------------------------- */

static size_t sh_strlcpy(char *dst, const char *src, size_t size) {
    size_t n = strlen(src);
    if (size) {
        size_t c = n < size - 1 ? n : size - 1;
        memcpy(dst, src, c);
        dst[c] = 0;
    }
    return n;
}

static size_t sh_strlcat(char *dst, const char *src, size_t size) {
    size_t d = strnlen(dst, size);
    if (d == size) return size + strlen(src);
    return d + sh_strlcpy(dst + d, src, size - d);
}

/* fortify: bionic checks the destination size, we trust the caller like an unfortified build */
static void *sh_memcpy_chk(void *d, const void *s, size_t n, size_t dn) {
    if (n > dn) LOGE("__memcpy_chk: %zu > %zu", n, dn);
    return memcpy(d, s, n);
}
static void *sh_memmove_chk(void *d, const void *s, size_t n, size_t dn) {
    SA_UNUSED(dn);
    return memmove(d, s, n);
}
static void *sh_memset_chk(void *d, int c, size_t n, size_t dn) {
    SA_UNUSED(dn);
    return memset(d, c, n);
}
static size_t sh_strlen_chk(const char *s, size_t n) {
    SA_UNUSED(n);
    return strlen(s);
}
static char *sh_strcpy_chk(char *d, const char *s, size_t n) {
    SA_UNUSED(n);
    return strcpy(d, s);
}
static char *sh_strncpy_chk(char *d, const char *s, size_t len, size_t n) {
    SA_UNUSED(n);
    return strncpy(d, s, len);
}
static char *sh_strncpy_chk2(char *d, const char *s, size_t len, size_t dn, size_t sn) {
    SA_UNUSED(dn);
    SA_UNUSED(sn);
    return strncpy(d, s, len);
}
static char *sh_strcat_chk(char *d, const char *s, size_t n) {
    SA_UNUSED(n);
    return strcat(d, s);
}
static char *sh_strncat_chk(char *d, const char *s, size_t len, size_t n) {
    SA_UNUSED(n);
    return strncat(d, s, len);
}
static char *sh_strchr_chk(const char *s, int c, size_t n) {
    SA_UNUSED(n);
    return strchr(s, c);
}
static char *sh_strrchr_chk(const char *s, int c, size_t n) {
    SA_UNUSED(n);
    return strrchr(s, c);
}
static int sh_vsnprintf_chk(char *buf, size_t len, int flags, size_t blen, const char *fmt, va_list ap) {
    SA_UNUSED(flags);
    SA_UNUSED(blen);
    return vsnprintf(buf, len, fmt, ap);
}
static int sh_snprintf_chk(char *buf, size_t len, int flags, size_t blen, const char *fmt, ...) {
    SA_UNUSED(flags);
    SA_UNUSED(blen);
    va_list ap;
    va_start(ap, fmt);
    int r = vsnprintf(buf, len, fmt, ap);
    va_end(ap);
    return r;
}
static int sh_vsprintf_chk(char *buf, int flags, size_t blen, const char *fmt, va_list ap) {
    SA_UNUSED(flags);
    return vsnprintf(buf, blen, fmt, ap);
}
static int sh_sprintf_chk(char *buf, int flags, size_t blen, const char *fmt, ...) {
    SA_UNUSED(flags);
    va_list ap;
    va_start(ap, fmt);
    int r = vsnprintf(buf, blen, fmt, ap);
    va_end(ap);
    return r;
}
static ssize_t sh_read_chk(int fd, void *buf, size_t count, size_t blen) {
    SA_UNUSED(blen);
    return read(fd, buf, count);
}

/* ---- stdio: bionic's __sF array (stdin, stdout, stderr for code built before API 23) ----------------------- */

#define BIONIC_FILE_SIZE 152
uint8_t sh_sF[3][BIONIC_FILE_SIZE];
FILE *sh_stdin, *sh_stdout, *sh_stderr;

static FILE *XF(FILE *f) {
    uint8_t *p = (uint8_t *)f;
    if (p >= sh_sF[0] && p < sh_sF[0] + sizeof sh_sF) {
        size_t i = (size_t)(p - sh_sF[0]) / BIONIC_FILE_SIZE;
        return i == 0 ? stdin : i == 1 ? stdout : stderr;
    }
    return f;
}

static FILE *sh_fopen(const char *path, const char *mode) {
    char *host = platform_map_path(path);
    FILE *f = fopen(host ? host : path, mode);
    free(host);
    return f;
}
static FILE *sh_fdopen(int fd, const char *mode) { return fdopen(fd, mode); }
static int sh_fclose(FILE *f) { return fclose(XF(f)); }
static size_t sh_fread(void *p, size_t s, size_t n, FILE *f) { return fread(p, s, n, XF(f)); }
static size_t sh_fwrite(const void *p, size_t s, size_t n, FILE *f) { return fwrite(p, s, n, XF(f)); }
static int sh_fseek(FILE *f, long o, int w) { return fseek(XF(f), o, w); }
static int sh_fseeko(FILE *f, off_t o, int w) { return fseeko(XF(f), o, w); }
static long sh_ftell(FILE *f) { return ftell(XF(f)); }
static off_t sh_ftello(FILE *f) { return ftello(XF(f)); }
static int sh_fflush(FILE *f) { return fflush(f ? XF(f) : NULL); }
static char *sh_fgets(char *s, int n, FILE *f) { return fgets(s, n, XF(f)); }
static int sh_fputs(const char *s, FILE *f) { return fputs(s, XF(f)); }
static int sh_fputc(int c, FILE *f) { return fputc(c, XF(f)); }
static int sh_fgetc(FILE *f) { return fgetc(XF(f)); }
static int sh_ungetc(int c, FILE *f) { return ungetc(c, XF(f)); }
static int sh_feof(FILE *f) { return feof(XF(f)); }
static int sh_ferror(FILE *f) { return ferror(XF(f)); }
static void sh_clearerr(FILE *f) { clearerr(XF(f)); }
static void sh_rewind(FILE *f) { rewind(XF(f)); }
static int sh_fileno(FILE *f) { return fileno(XF(f)); }
static int sh_setvbuf(FILE *f, char *b, int m, size_t s) { return setvbuf(XF(f), b, m, s); }
static int sh_vfprintf(FILE *f, const char *fmt, va_list ap) { return vfprintf(XF(f), fmt, ap); }
static int sh_fprintf(FILE *f, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int r = vfprintf(XF(f), fmt, ap);
    va_end(ap);
    return r;
}
static int sh_vfscanf(FILE *f, const char *fmt, va_list ap) { return vfscanf(XF(f), fmt, ap); }
static int sh_fscanf(FILE *f, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int r = vfscanf(XF(f), fmt, ap);
    va_end(ap);
    return r;
}
static char *sh_fgets_chk(char *s, int n, FILE *f, size_t blen) {
    SA_UNUSED(blen);
    return fgets(s, n, XF(f));
}
static int sh_remove(const char *path) {
    char *host = platform_map_path(path);
    int r = remove(host ? host : path);
    free(host);
    return r;
}
static int sh_rename(const char *a, const char *b) {
    char *ha = platform_map_path(a), *hb = platform_map_path(b);
    int r = rename(ha ? ha : a, hb ? hb : b);
    free(ha);
    free(hb);
    return r;
}

/* ---- files ------------------------------------------------------------------------------------------- */

static int sh_open(const char *path, int flags, ...) {
    int mode = 0;
    if (flags & O_CREAT) {
        va_list ap;
        va_start(ap, flags);
        mode = va_arg(ap, int);
        va_end(ap);
    }
    char *host = platform_map_path(path);
    int fd = open(host ? host : path, flags, mode);
    free(host);
    return fd;
}

static int sh_open_2(const char *path, int flags) { return sh_open(path, flags); }

static int sh_access(const char *path, int mode) {
    char *host = platform_map_path(path);
    int r = access(host ? host : path, mode);
    free(host);
    return r;
}

static int sh_unlink(const char *path) {
    char *host = platform_map_path(path);
    int r = unlink(host ? host : path);
    free(host);
    return r;
}

static int sh_mkdir(const char *path, mode_t mode) {
    char *host = platform_map_path(path);
    int r = mkdir(host ? host : path, mode);
    free(host);
    return r;
}

static int sh_stat(const char *path, struct stat *st) {
    char *host = platform_map_path(path);
    int r = stat(host ? host : path, st);
    free(host);
    return r;
}

static int sh_lstat(const char *path, struct stat *st) {
    char *host = platform_map_path(path);
    int r = lstat(host ? host : path, st);
    free(host);
    return r;
}

static DIR *sh_opendir(const char *path) {
    char *host = platform_map_path(path);
    DIR *d = opendir(host ? host : path);
    free(host);
    return d;
}

/* bionic's sysconf numbering */
static long sh_sysconf(int name) {
    switch (name) {
    case 0x0002: return CLOCKS_PER_SEC; /* _SC_CLK_TCK */
    case 0x0007: return 1024;           /* _SC_OPEN_MAX */
    case 0x0027: /* _SC_PAGESIZE / _SC_PAGE_SIZE */
    case 0x0028:
#ifdef __SWITCH__
        return 0x1000; /* newlib has no getpagesize() */
#else
        return (long)getpagesize();
#endif
    case 0x0060:                        /* _SC_NPROCESSORS_CONF */
    case 0x0061:                        /* _SC_NPROCESSORS_ONLN */
#ifdef __SWITCH__
        return 3;
#else
        return sysconf(name == 0x0060 ? _SC_NPROCESSORS_CONF : _SC_NPROCESSORS_ONLN);
#endif
    case 0x0062: return 1L << 18; /* _SC_PHYS_PAGES */
    case 0x0063: return 1L << 17; /* _SC_AVPHYS_PAGES */
    default:
        LOGW("sysconf(%d) not supported", name);
        errno = EINVAL;
        return -1;
    }
}

/* ---- pthreads: bionic objects hold a pointer to a lazily created host object --------------------------------- */

#define SH_MAGIC 0x53414c4bu
typedef struct {
    void *host;
    uint32_t magic;
    int32_t state; /* bionic's initial state word lives here for static initializers we cannot see */
} ShObj;

static pthread_mutex_t g_lazy_lock = PTHREAD_MUTEX_INITIALIZER;

/* bionic 64-bit: pthread_mutex_t and pthread_cond_t are 40 and 48 bytes, zero (or type bits) when static */
static pthread_mutex_t *mutex_of(void *m) {
    ShObj *o = m;
    if (__atomic_load_n(&o->magic, __ATOMIC_ACQUIRE) == SH_MAGIC) return o->host;
    pthread_mutex_lock(&g_lazy_lock);
    if (o->magic != SH_MAGIC) {
        int32_t bionic_state = *(int32_t *)m; /* PTHREAD_RECURSIVE_MUTEX_INITIALIZER_NP sets type bits 14-15 */
        pthread_mutexattr_t a;
        pthread_mutexattr_init(&a);
        int type = (bionic_state >> 14) & 3;
        if (type == 1) pthread_mutexattr_settype(&a, PTHREAD_MUTEX_RECURSIVE);
        else if (type == 2) pthread_mutexattr_settype(&a, PTHREAD_MUTEX_ERRORCHECK);
        pthread_mutex_t *h = sa_malloc(sizeof *h);
        pthread_mutex_init(h, &a);
        pthread_mutexattr_destroy(&a);
        o->host = h;
        __atomic_store_n(&o->magic, SH_MAGIC, __ATOMIC_RELEASE);
    }
    pthread_mutex_unlock(&g_lazy_lock);
    return o->host;
}

/* bionic pthread_mutexattr_t is an int holding the type */
static int sh_mutexattr_init(int *a) {
    *a = 0;
    return 0;
}
static int sh_mutexattr_destroy(int *a) {
    SA_UNUSED(a);
    return 0;
}
static int sh_mutexattr_settype(int *a, int type) {
    if (type < 0 || type > 2) return EINVAL;
    *a = type;
    return 0;
}
static int sh_mutexattr_gettype(const int *a, int *type) {
    *type = *a;
    return 0;
}
static int sh_mutexattr_setpshared(int *a, int v) {
    SA_UNUSED(a);
    SA_UNUSED(v);
    return 0;
}

static int sh_mutex_init(void *m, const int *attr) {
    ShObj *o = m;
    memset(m, 0, 40);
    pthread_mutexattr_t a;
    pthread_mutexattr_init(&a);
    /* bionic PTHREAD_MUTEX_RECURSIVE is 1, ERRORCHECK 2 */
    if (attr && *attr == 1) pthread_mutexattr_settype(&a, PTHREAD_MUTEX_RECURSIVE);
    else if (attr && *attr == 2) pthread_mutexattr_settype(&a, PTHREAD_MUTEX_ERRORCHECK);
    pthread_mutex_t *h = sa_malloc(sizeof *h);
    int r = pthread_mutex_init(h, &a);
    pthread_mutexattr_destroy(&a);
    o->host = h;
    o->magic = SH_MAGIC;
    return r;
}
static int sh_mutex_destroy(void *m) {
    ShObj *o = m;
    if (o->magic == SH_MAGIC) {
        pthread_mutex_destroy(o->host);
        free(o->host);
    }
    memset(m, 0, 40);
    return 0;
}
static int sh_mutex_lock(void *m) { return pthread_mutex_lock(mutex_of(m)); }
static int sh_mutex_trylock(void *m) { return pthread_mutex_trylock(mutex_of(m)); }
static int sh_mutex_unlock(void *m) { return pthread_mutex_unlock(mutex_of(m)); }

static pthread_cond_t *cond_of(void *c) {
    ShObj *o = c;
    if (__atomic_load_n(&o->magic, __ATOMIC_ACQUIRE) == SH_MAGIC) return o->host;
    pthread_mutex_lock(&g_lazy_lock);
    if (o->magic != SH_MAGIC) {
        pthread_cond_t *h = sa_malloc(sizeof *h);
        pthread_cond_init(h, NULL);
        o->host = h;
        __atomic_store_n(&o->magic, SH_MAGIC, __ATOMIC_RELEASE);
    }
    pthread_mutex_unlock(&g_lazy_lock);
    return o->host;
}
static int sh_cond_init(void *c, const void *attr) {
    SA_UNUSED(attr);
    memset(c, 0, 48);
    cond_of(c);
    return 0;
}
static int sh_cond_destroy(void *c) {
    ShObj *o = c;
    if (o->magic == SH_MAGIC) {
        pthread_cond_destroy(o->host);
        free(o->host);
    }
    memset(c, 0, 48);
    return 0;
}
static int sh_cond_wait(void *c, void *m) { return pthread_cond_wait(cond_of(c), mutex_of(m)); }
static int sh_cond_timedwait(void *c, void *m, const struct timespec *ts) {
    return pthread_cond_timedwait(cond_of(c), mutex_of(m), ts);
}
static int sh_cond_signal(void *c) { return pthread_cond_signal(cond_of(c)); }
static int sh_cond_broadcast(void *c) { return pthread_cond_broadcast(cond_of(c)); }
static int sh_condattr_init(void *a) {
    memset(a, 0, sizeof(long));
    return 0;
}
static int sh_condattr_noop(void *a, ...) {
    SA_UNUSED(a);
    return 0;
}

/* bionic pthread_attr_t (64-bit): flags, stack_base, stack_size, guard_size, policy, priority, reserved */
typedef struct {
    uint32_t flags;
    void *stack_base;
    size_t stack_size;
    size_t guard_size;
    int32_t sched_policy;
    int32_t sched_priority;
    char reserved[16];
} BionicAttr;

static int sh_attr_init(BionicAttr *a) {
    memset(a, 0, sizeof *a);
    a->stack_size = 1024 * 1024;
    a->guard_size = 4096;
    return 0;
}
static int sh_attr_destroy(BionicAttr *a) {
    SA_UNUSED(a);
    return 0;
}
static int sh_attr_setdetachstate(BionicAttr *a, int state) {
    if (state) a->flags |= 1;
    else a->flags &= ~1u;
    return 0;
}
static int sh_attr_getdetachstate(const BionicAttr *a, int *state) {
    *state = (int)(a->flags & 1);
    return 0;
}
static int sh_attr_setstacksize(BionicAttr *a, size_t s) {
    a->stack_size = s;
    return 0;
}
static int sh_attr_getstacksize(const BionicAttr *a, size_t *s) {
    *s = a->stack_size;
    return 0;
}
static int sh_attr_noop(void *a, ...) {
    SA_UNUSED(a);
    return 0;
}

static int sh_pthread_create(pthread_t *out, const BionicAttr *attr, void *(*fn)(void *), void *arg) {
    pthread_attr_t a;
    pthread_attr_init(&a);
    size_t stack = attr && attr->stack_size ? attr->stack_size : 1024 * 1024;
    if (stack < 256 * 1024) stack = 256 * 1024; /* our JNI and log paths want some room */
    pthread_attr_setstacksize(&a, stack);
    if (attr && (attr->flags & 1)) pthread_attr_setdetachstate(&a, PTHREAD_CREATE_DETACHED);
    int r = pthread_create(out, &a, fn, arg);
    pthread_attr_destroy(&a);
    return r;
}

static int sh_pthread_setname_np(pthread_t t, const char *name) {
    SA_UNUSED(t);
    SA_UNUSED(name);
    return 0;
}

#ifdef __SWITCH__
/* newlib exports none of these; native code still imports the bionic names. */
static int sh_getpagesize(void) { return 0x1000; }
static int sh_posix_memalign(void **memptr, size_t alignment, size_t size) {
    if (!memptr || alignment < sizeof(void *) || (alignment & (alignment - 1)) != 0) return EINVAL;
    void *p = memalign(alignment, size ? size : 1);
    if (!p) return ENOMEM;
    *memptr = p;
    return 0;
}
static int sh_pipe(int fds[2]) {
    SA_UNUSED(fds);
    errno = ENOSYS;
    return -1;
}
#endif

/* ---- the table ---------------------------------------------------------------------------------------- */

#define S(name) {#name, (void *)name}
#define W(name, fn) {#name, (void *)fn}

static const ShimSym g_syms[] = {
    /* errno, startup, exit, process */
    W(__errno, sh_errno), W(__stack_chk_fail, sh_stack_chk_fail), W(__stack_chk_guard, &sh_stack_chk_guard),
    W(__cxa_atexit, sh_cxa_atexit), W(__cxa_finalize, sh_cxa_finalize),
    W(__cxa_thread_atexit_impl, sh_cxa_thread_atexit_impl), W(atexit, sh_atexit),
    W(__register_atfork, sh_register_atfork), W(__assert2, sh_assert2), W(__assert, sh_assert),
    W(getauxval, sh_getauxval), W(gettid, sh_gettid), S(abort), S(exit), S(_exit), S(getpid), S(getenv),
    S(setenv), S(unsetenv), W(sysconf, sh_sysconf),
#ifdef __SWITCH__
    W(getpagesize, sh_getpagesize),
#else
    S(getpagesize),
#endif
    /* memory */
    S(malloc), S(free), S(calloc), S(realloc), S(aligned_alloc),
#ifdef __SWITCH__
    W(posix_memalign, sh_posix_memalign),
#else
    S(posix_memalign),
#endif
#ifndef __SWITCH__
    S(memalign), S(malloc_usable_size), S(mmap), S(munmap), S(mprotect), S(madvise),
#endif
    /* strings */
    S(memcpy), S(memmove), S(memset), S(memcmp), S(memchr), S(strlen), S(strnlen), S(strcmp), S(strncmp),
    S(strcpy), S(strncpy), S(strcat), S(strncat), S(strchr), S(strrchr), S(strstr), S(strdup), S(strndup),
    S(strcasecmp), S(strncasecmp), S(strtok), S(strtok_r), S(strspn), S(strcspn), S(strpbrk), S(strerror),
    S(strerror_r), S(strcoll), S(strxfrm), W(strlcpy, sh_strlcpy), W(strlcat, sh_strlcat),
    W(__memcpy_chk, sh_memcpy_chk), W(__memmove_chk, sh_memmove_chk), W(__memset_chk, sh_memset_chk),
    W(__strlen_chk, sh_strlen_chk), W(__strcpy_chk, sh_strcpy_chk), W(__strncpy_chk, sh_strncpy_chk),
    W(__strncpy_chk2, sh_strncpy_chk2), W(__strcat_chk, sh_strcat_chk), W(__strncat_chk, sh_strncat_chk),
    W(__strchr_chk, sh_strchr_chk), W(__strrchr_chk, sh_strrchr_chk), W(__vsnprintf_chk, sh_vsnprintf_chk),
    W(__snprintf_chk, sh_snprintf_chk), W(__vsprintf_chk, sh_vsprintf_chk), W(__sprintf_chk, sh_sprintf_chk),
    W(__read_chk, sh_read_chk), W(__fgets_chk, sh_fgets_chk),
    /* conversions */
    S(atoi), S(atol), S(atoll), S(atof), S(strtol), S(strtoul), S(strtoll), S(strtoull), S(strtod), S(strtof),
    S(qsort), S(bsearch), S(abs), S(labs), S(llabs), S(rand), S(srand), S(random), S(srandom), S(rand_r),
    /* ctype */
    S(isalpha), S(isdigit), S(isspace), S(isupper), S(islower), S(isalnum), S(isxdigit), S(ispunct),
    S(isprint), S(isgraph), S(iscntrl), S(toupper), S(tolower),
    /* stdio */
    W(__sF, sh_sF), W(stdin, &sh_stdin), W(stdout, &sh_stdout), W(stderr, &sh_stderr), S(printf), S(sprintf),
    S(snprintf), S(vprintf), S(vsprintf), S(vsnprintf), S(sscanf), S(vsscanf), S(puts), S(putchar),
    S(perror), W(fprintf, sh_fprintf), W(vfprintf, sh_vfprintf), W(fscanf, sh_fscanf), W(vfscanf, sh_vfscanf),
    W(fopen, sh_fopen), W(fdopen, sh_fdopen), W(fclose, sh_fclose), W(fread, sh_fread), W(fwrite, sh_fwrite),
    W(fseek, sh_fseek), W(fseeko, sh_fseeko), W(ftell, sh_ftell), W(ftello, sh_ftello), W(fflush, sh_fflush),
    W(fgets, sh_fgets), W(fputs, sh_fputs), W(fputc, sh_fputc), W(putc, sh_fputc), W(fgetc, sh_fgetc),
    W(getc, sh_fgetc), W(ungetc, sh_ungetc), W(feof, sh_feof), W(ferror, sh_ferror),
    W(clearerr, sh_clearerr), W(rewind, sh_rewind), W(fileno, sh_fileno), W(setvbuf, sh_setvbuf),
    W(remove, sh_remove), W(rename, sh_rename),
    /* files */
    W(open, sh_open), W(__open_2, sh_open_2), S(close), S(read), S(write), S(lseek), W(access, sh_access),
    W(unlink, sh_unlink), W(mkdir, sh_mkdir), W(stat, sh_stat), W(lstat, sh_lstat), S(fstat),
    W(opendir, sh_opendir), S(readdir), S(closedir), S(isatty), S(dup), S(dup2),
#ifdef __SWITCH__
    W(pipe, sh_pipe),
#else
    S(pipe),
#endif
    /* time */
    S(time), S(clock), S(gettimeofday), S(clock_gettime), S(nanosleep), S(usleep), S(sleep), S(localtime),
    S(localtime_r), S(gmtime), S(gmtime_r), S(mktime), S(strftime), S(difftime),
    /* threads */
    W(pthread_create, sh_pthread_create), S(pthread_join), S(pthread_detach), S(pthread_self),
    S(pthread_equal), S(pthread_exit), S(pthread_key_create), S(pthread_key_delete), S(pthread_getspecific),
    S(pthread_setspecific), S(pthread_once), W(pthread_setname_np, sh_pthread_setname_np), S(sched_yield),
    W(pthread_mutex_init, sh_mutex_init), W(pthread_mutex_destroy, sh_mutex_destroy),
    W(pthread_mutex_lock, sh_mutex_lock), W(pthread_mutex_trylock, sh_mutex_trylock),
    W(pthread_mutex_unlock, sh_mutex_unlock), W(pthread_mutexattr_init, sh_mutexattr_init),
    W(pthread_mutexattr_destroy, sh_mutexattr_destroy), W(pthread_mutexattr_settype, sh_mutexattr_settype),
    W(pthread_mutexattr_gettype, sh_mutexattr_gettype), W(pthread_mutexattr_setpshared, sh_mutexattr_setpshared),
    W(pthread_cond_init, sh_cond_init), W(pthread_cond_destroy, sh_cond_destroy),
    W(pthread_cond_wait, sh_cond_wait), W(pthread_cond_timedwait, sh_cond_timedwait),
    W(pthread_cond_signal, sh_cond_signal), W(pthread_cond_broadcast, sh_cond_broadcast),
    W(pthread_condattr_init, sh_condattr_init), W(pthread_condattr_destroy, sh_condattr_noop),
    W(pthread_condattr_setclock, sh_condattr_noop), W(pthread_condattr_setpshared, sh_condattr_noop),
    W(pthread_attr_init, sh_attr_init), W(pthread_attr_destroy, sh_attr_destroy),
    W(pthread_attr_setdetachstate, sh_attr_setdetachstate), W(pthread_attr_getdetachstate, sh_attr_getdetachstate),
    W(pthread_attr_setstacksize, sh_attr_setstacksize), W(pthread_attr_getstacksize, sh_attr_getstacksize),
    W(pthread_attr_setschedparam, sh_attr_noop), W(pthread_attr_setschedpolicy, sh_attr_noop),
    W(pthread_attr_setguardsize, sh_attr_noop),
    /* setjmp: glibc's _setjmp saves no signal mask, so it fits bionic's smaller jmp_buf; newlib's setjmp
     * saves no mask either (176 bytes on AArch64) */
#ifdef __SWITCH__
    W(setjmp, setjmp), W(_setjmp, setjmp), S(longjmp), W(_longjmp, longjmp),
#else
    W(setjmp, _setjmp), S(_setjmp), S(longjmp), W(_longjmp, longjmp),
#endif
    /* math */
    S(sin), S(cos), S(tan), S(asin), S(acos), S(atan), S(atan2), S(sinh), S(cosh), S(tanh), S(exp), S(exp2),
    S(expm1), S(log), S(log2), S(log10), S(log1p), S(pow), S(sqrt), S(cbrt), S(hypot), S(ceil), S(floor),
    S(fabs), S(fmod), S(round), S(lround), S(llround), S(trunc), S(rint), S(lrint), S(nearbyint), S(fmin),
    S(fmax), S(fdim), S(copysign), S(ldexp), S(frexp), S(modf), S(remainder), S(scalbn), S(nan),
    S(sinf), S(cosf), S(tanf), S(asinf), S(acosf), S(atanf), S(atan2f), S(sinhf), S(coshf), S(tanhf),
    S(expf), S(exp2f), S(expm1f), S(logf), S(log2f), S(log10f), S(log1pf), S(powf), S(sqrtf), S(cbrtf),
    S(hypotf), S(ceilf), S(floorf), S(fabsf), S(fmodf), S(roundf), S(lroundf), S(truncf), S(rintf),
    S(lrintf), S(nearbyintf), S(fminf), S(fmaxf), S(copysignf), S(ldexpf), S(frexpf), S(modff),
    S(remainderf), S(scalbnf), S(nanf),
};

const ShimSym *shim_libc_symbols(size_t *n) {
    sh_stdin = stdin;
    sh_stdout = stdout;
    sh_stderr = stderr;
    *n = SA_ARRAY_LEN(g_syms);
    return g_syms;
}
