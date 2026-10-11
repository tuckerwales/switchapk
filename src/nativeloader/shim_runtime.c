/*
 * Runtime pieces of bionic that native libraries import and the host C library either lacks or
 * shapes differently (WS9): C++ operator new/delete and __cxa_pure_virtual for code linked against
 * the system libstdc++, sincos, vasprintf, syslog, semaphores and rwlocks (bionic objects hold a
 * pointer to our own implementation), dl_iterate_phdr over the libraries we loaded, a UTF-8 C.UTF-8
 * locale with bionic's multibyte functions, and the wide-character calls libc++ uses.
 *
 * Android's only locales are C and C.UTF-8 (MB_CUR_MAX 4), so the multibyte conversions here are
 * plain UTF-8 and do not depend on the host locale.
 */
#include "nativeloader.h"

#include <ctype.h>
#include <errno.h>
#include <limits.h>
#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <wchar.h>
#include <wctype.h>
#ifndef __SWITCH__
#include <sys/syscall.h>
#include <unistd.h>
#endif

#define LOG_TAG "libc"

char *platform_map_path(const char *android_path);

/* ---- C++ runtime entry points ------------------------------------------------------------------------- */

static void *sh_operator_new(size_t n) {
    void *p = malloc(n ? n : 1);
    if (!p) {
        /* Without libstdc++ there is no std::bad_alloc to throw. */
        LOGE("operator new(%zu) failed", n);
        abort();
    }
    return p;
}

static void *sh_operator_new_nothrow(size_t n, const void *tag) {
    SA_UNUSED(tag);
    return malloc(n ? n : 1);
}

static void *sh_operator_new_aligned(size_t n, size_t align) {
    if (align < sizeof(void *)) align = sizeof(void *);
    void *p = aligned_alloc(align, (n + align - 1) / align * align);
    if (!p) {
        LOGE("operator new(%zu, align %zu) failed", n, align);
        abort();
    }
    return p;
}

static void sh_operator_delete(void *p) { free(p); }
static void sh_operator_delete_sized(void *p, size_t n) {
    SA_UNUSED(n);
    free(p);
}
static void sh_operator_delete_nothrow(void *p, const void *tag) {
    SA_UNUSED(tag);
    free(p);
}
static void sh_operator_delete_aligned(void *p, size_t align) {
    SA_UNUSED(align);
    free(p);
}
static void sh_operator_delete_sized_aligned(void *p, size_t n, size_t align) {
    SA_UNUSED(n);
    SA_UNUSED(align);
    free(p);
}

static void sh_cxa_pure_virtual(void) {
    LOGE("pure virtual method called");
    abort();
}

static void sh_android_set_abort_message(const char *msg) { LOGE("abort message: %s", msg ? msg : "(null)"); }

/* ---- math --------------------------------------------------------------------------------------------- */

static void sh_sincos(double x, double *s, double *c) {
    *s = sin(x);
    *c = cos(x);
}

static void sh_sincosf(float x, float *s, float *c) {
    *s = sinf(x);
    *c = cosf(x);
}

/* ---- strings and stdio -------------------------------------------------------------------------------- */

static int sh_vasprintf(char **out, const char *fmt, va_list ap) {
    va_list ap2;
    va_copy(ap2, ap);
    int n = vsnprintf(NULL, 0, fmt, ap2);
    va_end(ap2);
    if (n < 0) {
        *out = NULL;
        return -1;
    }
    char *buf = malloc((size_t)n + 1);
    if (!buf) {
        *out = NULL;
        return -1;
    }
    vsnprintf(buf, (size_t)n + 1, fmt, ap);
    *out = buf;
    return n;
}

static int sh_asprintf(char **out, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int n = sh_vasprintf(out, fmt, ap);
    va_end(ap);
    return n;
}

static char *sh_stpcpy(char *dst, const char *src) {
    size_t n = strlen(src);
    memcpy(dst, src, n + 1);
    return dst + n;
}

static int sh_isblank(int c) { return c == ' ' || c == '\t'; }

/* ---- syslog: to the switchapk log ---------------------------------------------------------------------- */

static char g_syslog_ident[64] = "syslog";

static void sh_openlog(const char *ident, int option, int facility) {
    SA_UNUSED(option);
    SA_UNUSED(facility);
    snprintf(g_syslog_ident, sizeof g_syslog_ident, "%s", ident ? ident : "syslog");
}

static void sh_closelog(void) {}

static void sh_vsyslog(int priority, const char *fmt, va_list ap) {
    char buf[1024];
    vsnprintf(buf, sizeof buf, fmt, ap);
    int level = priority & 7; /* LOG_EMERG 0 ... LOG_DEBUG 7 */
    int prio = level <= 3 ? SA_LOG_ERROR : level == 4 ? SA_LOG_WARN : level <= 6 ? SA_LOG_INFO : SA_LOG_DEBUG;
    sa_log(prio, g_syslog_ident, "%s", buf);
}

static void sh_syslog(int priority, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    sh_vsyslog(priority, fmt, ap);
    va_end(ap);
}

/* ---- processes and files ------------------------------------------------------------------------------ */

/* bionic struct rlimit on 64-bit: two 64-bit values */
typedef struct {
    uint64_t cur, max;
} ShRlimit;

static int sh_getrlimit(int resource, ShRlimit *rl) {
    if (!rl) {
        errno = EFAULT;
        return -1;
    }
    rl->cur = rl->max = ~(uint64_t)0; /* RLIM_INFINITY */
    if (resource == 3) rl->cur = 8u << 20; /* RLIMIT_STACK */
    if (resource == 7) rl->cur = rl->max = 1024; /* RLIMIT_NOFILE */
    return 0;
}

static int sh_setrlimit(int resource, const ShRlimit *rl) {
    SA_UNUSED(resource);
    SA_UNUSED(rl);
    return 0;
}

static int sh_setpriority(int which, int who, int prio) {
    SA_UNUSED(which);
    SA_UNUSED(who);
    SA_UNUSED(prio);
    return 0;
}

static int sh_getpriority(int which, int who) {
    SA_UNUSED(which);
    SA_UNUSED(who);
    errno = 0;
    return 0;
}

static int sh_chmod(const char *path, unsigned mode) {
    char *p = platform_map_path(path);
#ifdef __SWITCH__
    SA_UNUSED(mode);
    struct stat st;
    int r = stat(p, &st); /* no permissions on the SD card: succeed if the file is there */
#else
    int r = chmod(p, (mode_t)mode);
#endif
    free(p);
    return r;
}

/* syscall(2) with the caller's numbering: the library is built for this machine's ABI. The Switch
 * has no Linux syscalls, so only gettid is answered there. */
static long sh_syscall(long number, long a, long b, long c, long d, long e, long f) {
#ifdef __SWITCH__
    SA_UNUSED(a);
    SA_UNUSED(b);
    SA_UNUSED(c);
    SA_UNUSED(d);
    SA_UNUSED(e);
    SA_UNUSED(f);
    if (number == 178) return (long)(uintptr_t)pthread_self() & 0x7fffffff; /* __NR_gettid on arm64 */
    errno = ENOSYS;
    return -1;
#else
    return syscall(number, a, b, c, d, e, f);
#endif
}

static int sh_dl_iterate_phdr(int (*cb)(void *info, size_t size, void *data), void *data) {
    return loader_iterate_phdr(cb, data);
}

/* ---- semaphores and rwlocks: bionic objects hold a pointer to ours ------------------------------------- */

#define SH_SEM_MAGIC 0x53454d31u
#define SH_RW_MAGIC 0x52574c31u

typedef struct {
    pthread_mutex_t m;
    pthread_cond_t c;
    unsigned count;
} ShSem;

typedef struct {
    void *impl;
    uint32_t magic;
} ShHandle; /* fits bionic's 16-byte sem_t and 56-byte pthread_rwlock_t */

static pthread_mutex_t g_init_lock = PTHREAD_MUTEX_INITIALIZER;

static int sh_sem_init(void *sem, int pshared, unsigned value) {
    SA_UNUSED(pshared);
    ShSem *s = sa_calloc(1, sizeof *s);
    pthread_mutex_init(&s->m, NULL);
    pthread_cond_init(&s->c, NULL);
    s->count = value;
    ShHandle *h = sem;
    h->impl = s;
    h->magic = SH_SEM_MAGIC;
    return 0;
}

static ShSem *sem_of(void *sem) {
    ShHandle *h = sem;
    if (!h || h->magic != SH_SEM_MAGIC) {
        errno = EINVAL;
        return NULL;
    }
    return h->impl;
}

static int sh_sem_destroy(void *sem) {
    ShSem *s = sem_of(sem);
    if (!s) return -1;
    pthread_mutex_destroy(&s->m);
    pthread_cond_destroy(&s->c);
    free(s);
    memset(sem, 0, sizeof(ShHandle));
    return 0;
}

static int sh_sem_post(void *sem) {
    ShSem *s = sem_of(sem);
    if (!s) return -1;
    pthread_mutex_lock(&s->m);
    s->count++;
    pthread_cond_signal(&s->c);
    pthread_mutex_unlock(&s->m);
    return 0;
}

static int sh_sem_wait(void *sem) {
    ShSem *s = sem_of(sem);
    if (!s) return -1;
    pthread_mutex_lock(&s->m);
    while (s->count == 0) pthread_cond_wait(&s->c, &s->m);
    s->count--;
    pthread_mutex_unlock(&s->m);
    return 0;
}

static int sh_sem_trywait(void *sem) {
    ShSem *s = sem_of(sem);
    if (!s) return -1;
    pthread_mutex_lock(&s->m);
    int ok = s->count > 0;
    if (ok) s->count--;
    pthread_mutex_unlock(&s->m);
    if (!ok) {
        errno = EAGAIN;
        return -1;
    }
    return 0;
}

static int sh_sem_timedwait(void *sem, const struct timespec *abstime) {
    ShSem *s = sem_of(sem);
    if (!s) return -1;
    pthread_mutex_lock(&s->m);
    int r = 0;
    while (s->count == 0 && r == 0) r = pthread_cond_timedwait(&s->c, &s->m, abstime);
    if (s->count > 0) {
        s->count--;
        r = 0;
    }
    pthread_mutex_unlock(&s->m);
    if (r) {
        errno = r == ETIMEDOUT ? ETIMEDOUT : EINVAL;
        return -1;
    }
    return 0;
}

static int sh_sem_getvalue(void *sem, int *value) {
    ShSem *s = sem_of(sem);
    if (!s) return -1;
    pthread_mutex_lock(&s->m);
    *value = (int)s->count;
    pthread_mutex_unlock(&s->m);
    return 0;
}

/* Writer-preferring rwlock; zeroed (PTHREAD_RWLOCK_INITIALIZER) locks are created on first use. */
typedef struct {
    pthread_mutex_t m;
    pthread_cond_t readers_ok, writer_ok;
    int readers, writer, waiting_writers;
    pthread_t owner;
} ShRw;

static ShRw *rw_of(void *l) {
    ShHandle *h = l;
    if (__atomic_load_n(&h->magic, __ATOMIC_ACQUIRE) == SH_RW_MAGIC) return h->impl;
    pthread_mutex_lock(&g_init_lock);
    if (h->magic != SH_RW_MAGIC) {
        ShRw *rw = sa_calloc(1, sizeof *rw);
        pthread_mutex_init(&rw->m, NULL);
        pthread_cond_init(&rw->readers_ok, NULL);
        pthread_cond_init(&rw->writer_ok, NULL);
        h->impl = rw;
        __atomic_store_n(&h->magic, SH_RW_MAGIC, __ATOMIC_RELEASE);
    }
    pthread_mutex_unlock(&g_init_lock);
    return h->impl;
}

static int sh_rwlock_init(void *l, const void *attr) {
    SA_UNUSED(attr);
    memset(l, 0, sizeof(ShHandle));
    rw_of(l);
    return 0;
}

static int sh_rwlock_destroy(void *l) {
    ShHandle *h = l;
    if (h->magic == SH_RW_MAGIC) {
        ShRw *rw = h->impl;
        pthread_mutex_destroy(&rw->m);
        pthread_cond_destroy(&rw->readers_ok);
        pthread_cond_destroy(&rw->writer_ok);
        free(rw);
    }
    memset(l, 0, sizeof(ShHandle));
    return 0;
}

static int sh_rwlock_rdlock(void *l) {
    ShRw *rw = rw_of(l);
    pthread_mutex_lock(&rw->m);
    while (rw->writer || rw->waiting_writers) pthread_cond_wait(&rw->readers_ok, &rw->m);
    rw->readers++;
    pthread_mutex_unlock(&rw->m);
    return 0;
}

static int sh_rwlock_tryrdlock(void *l) {
    ShRw *rw = rw_of(l);
    pthread_mutex_lock(&rw->m);
    int ok = !rw->writer && !rw->waiting_writers;
    if (ok) rw->readers++;
    pthread_mutex_unlock(&rw->m);
    return ok ? 0 : EBUSY;
}

static int sh_rwlock_wrlock(void *l) {
    ShRw *rw = rw_of(l);
    pthread_mutex_lock(&rw->m);
    rw->waiting_writers++;
    while (rw->writer || rw->readers) pthread_cond_wait(&rw->writer_ok, &rw->m);
    rw->waiting_writers--;
    rw->writer = 1;
    rw->owner = pthread_self();
    pthread_mutex_unlock(&rw->m);
    return 0;
}

static int sh_rwlock_trywrlock(void *l) {
    ShRw *rw = rw_of(l);
    pthread_mutex_lock(&rw->m);
    int ok = !rw->writer && !rw->readers;
    if (ok) {
        rw->writer = 1;
        rw->owner = pthread_self();
    }
    pthread_mutex_unlock(&rw->m);
    return ok ? 0 : EBUSY;
}

static int sh_rwlock_timedrdlock(void *l, const struct timespec *abstime) {
    SA_UNUSED(abstime);
    return sh_rwlock_rdlock(l);
}

static int sh_rwlock_timedwrlock(void *l, const struct timespec *abstime) {
    SA_UNUSED(abstime);
    return sh_rwlock_wrlock(l);
}

static int sh_rwlock_unlock(void *l) {
    ShRw *rw = rw_of(l);
    pthread_mutex_lock(&rw->m);
    if (rw->writer) {
        rw->writer = 0;
    } else if (rw->readers > 0) {
        rw->readers--;
    }
    if (rw->waiting_writers) {
        if (rw->readers == 0) pthread_cond_signal(&rw->writer_ok);
    } else {
        pthread_cond_broadcast(&rw->readers_ok);
    }
    pthread_mutex_unlock(&rw->m);
    return 0;
}

static int sh_rwlockattr_noop(void *a) {
    SA_UNUSED(a);
    return 0;
}

static int sh_rwlockattr_set(void *a, int v) {
    SA_UNUSED(a);
    SA_UNUSED(v);
    return 0;
}

/* ---- locale: C and C.UTF-8 only, as on Android ------------------------------------------------------- */

/* bionic's struct lconv */
typedef struct {
    const char *decimal_point, *thousands_sep, *grouping, *int_curr_symbol, *currency_symbol, *mon_decimal_point,
        *mon_thousands_sep, *mon_grouping, *positive_sign, *negative_sign;
    char int_frac_digits, frac_digits, p_cs_precedes, p_sep_by_space, n_cs_precedes, n_sep_by_space, p_sign_posn,
        n_sign_posn, int_p_cs_precedes, int_p_sep_by_space, int_n_cs_precedes, int_n_sep_by_space, int_p_sign_posn,
        int_n_sign_posn;
} ShLconv;

static ShLconv g_lconv = {".", "", "", "", "", "", "", "", "", "",
                          CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX,
                          CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX};

static int g_locale_obj; /* the one locale_t: its address */

static int locale_name_ok(const char *name) {
    return !name || !*name || !strcmp(name, "C") || !strcmp(name, "POSIX") || !strcmp(name, "C.UTF-8")
        || !strcmp(name, "C.utf8") || !strcmp(name, "en_US.UTF-8");
}

static char *sh_setlocale(int category, const char *name) {
    SA_UNUSED(category);
    if (!locale_name_ok(name)) return NULL;
    return (char *)"C.UTF-8";
}

static void *sh_newlocale(int mask, const char *name, void *base) {
    SA_UNUSED(mask);
    SA_UNUSED(base);
    if (!locale_name_ok(name)) {
        errno = ENOENT;
        return NULL;
    }
    return &g_locale_obj;
}

static void *sh_duplocale(void *l) { return l; }
static void sh_freelocale(void *l) { SA_UNUSED(l); }

static void *sh_uselocale(void *l) {
    SA_UNUSED(l);
    return (void *)-1L; /* LC_GLOBAL_LOCALE */
}

static ShLconv *sh_localeconv(void) { return &g_lconv; }

static size_t sh_ctype_get_mb_cur_max(void) { return 4; }

static long double sh_strtold_l(const char *s, char **end, void *l) {
    SA_UNUSED(l);
    return strtold(s, end);
}
static double sh_strtod_l(const char *s, char **end, void *l) {
    SA_UNUSED(l);
    return strtod(s, end);
}
static float sh_strtof_l(const char *s, char **end, void *l) {
    SA_UNUSED(l);
    return strtof(s, end);
}
static long long sh_strtoll_l(const char *s, char **end, int base, void *l) {
    SA_UNUSED(l);
    return strtoll(s, end, base);
}
static unsigned long long sh_strtoull_l(const char *s, char **end, int base, void *l) {
    SA_UNUSED(l);
    return strtoull(s, end, base);
}

/* ---- UTF-8 multibyte conversions (bionic mbstate_t: 4 pending bytes, then their count) ----------------- */

typedef struct {
    unsigned char seq[4];
    unsigned char count;
    unsigned char pad[3];
} ShMbstate;

static ShMbstate g_mbrtowc_state, g_mbrlen_state, g_wcrtomb_state, g_mbsrtowcs_state, g_wcsrtombs_state;

#define MB_ERR ((size_t)-1)
#define MB_PARTIAL ((size_t)-2)

/* Decodes one UTF-8 character from the pending bytes plus s[0..n). Returns bytes taken from s. */
static size_t utf8_decode(uint32_t *out, const unsigned char *s, size_t n, ShMbstate *st) {
    unsigned char buf[4];
    size_t have = st->count;
    if (have > 3) {
        st->count = 0;
        errno = EILSEQ;
        return MB_ERR;
    }
    memcpy(buf, st->seq, have);
    size_t taken = 0;
    if (have == 0) {
        if (n == 0) return MB_PARTIAL;
        buf[0] = s[0];
        have = 1;
        taken = 1;
    }
    unsigned char c0 = buf[0];
    size_t need;
    uint32_t min;
    if (c0 < 0x80) {
        *out = c0;
        st->count = 0;
        return taken;
    } else if (c0 >= 0xc2 && c0 <= 0xdf) {
        need = 2;
        min = 0x80;
    } else if (c0 >= 0xe0 && c0 <= 0xef) {
        need = 3;
        min = 0x800;
    } else if (c0 >= 0xf0 && c0 <= 0xf4) {
        need = 4;
        min = 0x10000;
    } else {
        st->count = 0;
        errno = EILSEQ;
        return MB_ERR;
    }
    while (have < need && taken < n) {
        unsigned char c = s[taken];
        if ((c & 0xc0) != 0x80) {
            st->count = 0;
            errno = EILSEQ;
            return MB_ERR;
        }
        buf[have++] = c;
        taken++;
    }
    if (have < need) {
        memcpy(st->seq, buf, have);
        st->count = (unsigned char)have;
        return MB_PARTIAL;
    }
    uint32_t wc = c0 & (0xff >> (need + 1));
    for (size_t i = 1; i < need; i++) wc = (wc << 6) | (buf[i] & 0x3f);
    st->count = 0;
    if (wc < min || wc > 0x10ffff || (wc >= 0xd800 && wc <= 0xdfff)) {
        errno = EILSEQ;
        return MB_ERR;
    }
    *out = wc;
    return taken;
}

static size_t utf8_encode(char *s, uint32_t wc) {
    if (wc < 0x80) {
        s[0] = (char)wc;
        return 1;
    }
    if (wc < 0x800) {
        s[0] = (char)(0xc0 | (wc >> 6));
        s[1] = (char)(0x80 | (wc & 0x3f));
        return 2;
    }
    if (wc >= 0xd800 && wc <= 0xdfff) {
        errno = EILSEQ;
        return MB_ERR;
    }
    if (wc < 0x10000) {
        s[0] = (char)(0xe0 | (wc >> 12));
        s[1] = (char)(0x80 | ((wc >> 6) & 0x3f));
        s[2] = (char)(0x80 | (wc & 0x3f));
        return 3;
    }
    if (wc <= 0x10ffff) {
        s[0] = (char)(0xf0 | (wc >> 18));
        s[1] = (char)(0x80 | ((wc >> 12) & 0x3f));
        s[2] = (char)(0x80 | ((wc >> 6) & 0x3f));
        s[3] = (char)(0x80 | (wc & 0x3f));
        return 4;
    }
    errno = EILSEQ;
    return MB_ERR;
}

static size_t sh_mbrtowc(wchar_t *pwc, const char *s, size_t n, void *ps) {
    ShMbstate *st = ps ? ps : &g_mbrtowc_state;
    if (!s) {
        st->count = 0;
        return 0;
    }
    if (n == 0) return MB_PARTIAL;
    uint32_t wc = 0;
    size_t r = utf8_decode(&wc, (const unsigned char *)s, n, st);
    if (r == MB_ERR || r == MB_PARTIAL) return r;
    if (pwc) *pwc = (wchar_t)wc;
    return wc == 0 ? 0 : r;
}

static size_t sh_mbrlen(const char *s, size_t n, void *ps) {
    return sh_mbrtowc(NULL, s, n, ps ? ps : &g_mbrlen_state);
}

static int sh_mbtowc(wchar_t *pwc, const char *s, size_t n) {
    if (!s) return 0;
    ShMbstate st = {{0}, 0, {0}};
    size_t r = sh_mbrtowc(pwc, s, n, &st);
    if (r == MB_ERR || r == MB_PARTIAL) {
        errno = EILSEQ;
        return -1;
    }
    return (int)r;
}

static int sh_mblen(const char *s, size_t n) { return sh_mbtowc(NULL, s, n); }

static size_t sh_wcrtomb(char *s, wchar_t wc, void *ps) {
    ShMbstate *st = ps ? ps : &g_wcrtomb_state;
    st->count = 0;
    if (!s) return 1;
    return utf8_encode(s, (uint32_t)wc);
}

static int sh_wctomb(char *s, wchar_t wc) {
    if (!s) return 0;
    size_t r = utf8_encode(s, (uint32_t)wc);
    return r == MB_ERR ? -1 : (int)r;
}

static size_t sh_mbsnrtowcs(wchar_t *dst, const char **src, size_t nms, size_t len, void *ps) {
    ShMbstate *st = ps ? ps : &g_mbsrtowcs_state;
    const char *s = *src;
    size_t out = 0;
    while (nms > 0 && (!dst || out < len)) {
        uint32_t wc = 0;
        size_t r = utf8_decode(&wc, (const unsigned char *)s, nms, st);
        if (r == MB_ERR) {
            if (dst) *src = s;
            return MB_ERR;
        }
        if (r == MB_PARTIAL) {
            s += nms;
            nms = 0;
            break;
        }
        if (wc == 0) {
            if (dst) {
                dst[out] = 0;
                *src = NULL;
            }
            return out;
        }
        if (dst) dst[out] = (wchar_t)wc;
        out++;
        s += r;
        nms -= r;
    }
    if (dst) *src = s;
    return out;
}

static size_t sh_mbsrtowcs(wchar_t *dst, const char **src, size_t len, void *ps) {
    return sh_mbsnrtowcs(dst, src, SIZE_MAX / 2, len, ps);
}

static size_t sh_mbstowcs(wchar_t *dst, const char *src, size_t len) {
    ShMbstate st = {{0}, 0, {0}};
    return sh_mbsrtowcs(dst, &src, len, &st);
}

static size_t sh_wcsnrtombs(char *dst, const wchar_t **src, size_t nwc, size_t len, void *ps) {
    ShMbstate *st = ps ? ps : &g_wcsrtombs_state;
    st->count = 0;
    const wchar_t *s = *src;
    size_t out = 0;
    char tmp[4];
    for (; nwc > 0; nwc--, s++) {
        uint32_t wc = (uint32_t)*s;
        if (wc == 0) {
            if (dst) {
                if (out >= len) break;
                dst[out] = 0;
                *src = NULL;
            }
            return out;
        }
        size_t r = utf8_encode(tmp, wc);
        if (r == MB_ERR) {
            if (dst) *src = s;
            return MB_ERR;
        }
        if (dst) {
            if (out + r > len) break;
            memcpy(dst + out, tmp, r);
        }
        out += r;
    }
    if (dst) *src = s;
    return out;
}

static size_t sh_wcsrtombs(char *dst, const wchar_t **src, size_t len, void *ps) {
    return sh_wcsnrtombs(dst, src, SIZE_MAX / 2, len, ps);
}

static size_t sh_wcstombs(char *dst, const wchar_t *src, size_t len) {
    ShMbstate st = {{0}, 0, {0}};
    return sh_wcsrtombs(dst, &src, len, &st);
}

static wint_t sh_btowc(int c) { return (c >= 0 && c < 0x80) ? (wint_t)c : WEOF; }
static int sh_wctob(wint_t c) { return c < 0x80 ? (int)c : EOF; }

/* ---- the table ---------------------------------------------------------------------------------------- */

#define S(name) {#name, (void *)name}
#define W(name, fn) {#name, (void *)fn}

static const ShimSym g_syms[] = {
    /* C++ runtime (code linked against the platform libstdc++) */
    W(_Znwm, sh_operator_new), W(_Znam, sh_operator_new),
    W(_ZnwmRKSt9nothrow_t, sh_operator_new_nothrow), W(_ZnamRKSt9nothrow_t, sh_operator_new_nothrow),
    W(_ZnwmSt11align_val_t, sh_operator_new_aligned), W(_ZnamSt11align_val_t, sh_operator_new_aligned),
    W(_ZdlPv, sh_operator_delete), W(_ZdaPv, sh_operator_delete),
    W(_ZdlPvm, sh_operator_delete_sized), W(_ZdaPvm, sh_operator_delete_sized),
    W(_ZdlPvRKSt9nothrow_t, sh_operator_delete_nothrow), W(_ZdaPvRKSt9nothrow_t, sh_operator_delete_nothrow),
    W(_ZdlPvSt11align_val_t, sh_operator_delete_aligned), W(_ZdaPvSt11align_val_t, sh_operator_delete_aligned),
    W(_ZdlPvmSt11align_val_t, sh_operator_delete_sized_aligned),
    W(_ZdaPvmSt11align_val_t, sh_operator_delete_sized_aligned),
    W(__cxa_pure_virtual, sh_cxa_pure_virtual), W(__cxa_deleted_virtual, sh_cxa_pure_virtual),
    W(android_set_abort_message, sh_android_set_abort_message),
    W(dl_iterate_phdr, sh_dl_iterate_phdr),
    /* math */
    W(sincos, sh_sincos), W(sincosf, sh_sincosf),
    /* strings, stdio */
    W(vasprintf, sh_vasprintf), W(asprintf, sh_asprintf), W(stpcpy, sh_stpcpy), W(isblank, sh_isblank),
    /* syslog */
    W(openlog, sh_openlog), W(closelog, sh_closelog), W(syslog, sh_syslog), W(vsyslog, sh_vsyslog),
    /* processes, files */
    W(getrlimit, sh_getrlimit), W(setrlimit, sh_setrlimit), W(getpriority, sh_getpriority),
    W(setpriority, sh_setpriority), W(chmod, sh_chmod), W(syscall, sh_syscall),
    /* semaphores, rwlocks */
    W(sem_init, sh_sem_init), W(sem_destroy, sh_sem_destroy), W(sem_post, sh_sem_post), W(sem_wait, sh_sem_wait),
    W(sem_trywait, sh_sem_trywait), W(sem_timedwait, sh_sem_timedwait), W(sem_getvalue, sh_sem_getvalue),
    W(pthread_rwlock_init, sh_rwlock_init), W(pthread_rwlock_destroy, sh_rwlock_destroy),
    W(pthread_rwlock_rdlock, sh_rwlock_rdlock), W(pthread_rwlock_tryrdlock, sh_rwlock_tryrdlock),
    W(pthread_rwlock_wrlock, sh_rwlock_wrlock), W(pthread_rwlock_trywrlock, sh_rwlock_trywrlock),
    W(pthread_rwlock_timedrdlock, sh_rwlock_timedrdlock), W(pthread_rwlock_timedwrlock, sh_rwlock_timedwrlock),
    W(pthread_rwlock_unlock, sh_rwlock_unlock), W(pthread_rwlockattr_init, sh_rwlockattr_noop),
    W(pthread_rwlockattr_destroy, sh_rwlockattr_noop), W(pthread_rwlockattr_setpshared, sh_rwlockattr_set),
    W(pthread_rwlockattr_setkind_np, sh_rwlockattr_set),
    /* locale */
    W(setlocale, sh_setlocale), W(newlocale, sh_newlocale), W(duplocale, sh_duplocale), W(freelocale, sh_freelocale),
    W(uselocale, sh_uselocale), W(localeconv, sh_localeconv), W(__ctype_get_mb_cur_max, sh_ctype_get_mb_cur_max),
    W(strtold_l, sh_strtold_l), W(strtod_l, sh_strtod_l), W(strtof_l, sh_strtof_l), W(strtoll_l, sh_strtoll_l),
    W(strtoull_l, sh_strtoull_l), S(strtold),
    /* multibyte (UTF-8) */
    W(mbrtowc, sh_mbrtowc), W(mbrlen, sh_mbrlen), W(mbtowc, sh_mbtowc), W(mblen, sh_mblen),
    W(wcrtomb, sh_wcrtomb), W(wctomb, sh_wctomb), W(mbsrtowcs, sh_mbsrtowcs), W(mbsnrtowcs, sh_mbsnrtowcs),
    W(mbstowcs, sh_mbstowcs), W(wcsrtombs, sh_wcsrtombs), W(wcsnrtombs, sh_wcsnrtombs), W(wcstombs, sh_wcstombs),
    W(btowc, sh_btowc), W(wctob, sh_wctob),
    /* wide characters: same ABI (32-bit wchar_t) on every target */
    S(wcslen), S(wcscmp), S(wcsncmp), S(wcscpy), S(wcsncpy), S(wcscat), S(wcschr), S(wcsrchr), S(wcsstr),
    S(wcscoll), S(wcsxfrm), S(wmemchr), S(wmemcmp), S(wmemcpy), S(wmemmove), S(wmemset), S(wcstol), S(wcstoul),
    S(wcstoll), S(wcstoull), S(wcstod), S(wcstof), S(wcstold), S(swprintf), S(vswprintf), S(iswalpha),
    S(iswblank), S(iswcntrl), S(iswdigit), S(iswlower), S(iswprint), S(iswpunct), S(iswspace), S(iswupper),
    S(iswxdigit), S(iswalnum), S(iswgraph), S(towlower), S(towupper),
};

const ShimSym *shim_runtime_symbols(size_t *n) {
    *n = SA_ARRAY_LEN(g_syms);
    return g_syms;
}
