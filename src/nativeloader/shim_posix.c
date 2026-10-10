/*
 * POSIX and bionic extras for native libraries (WS9), beside shim_libc.c: sockets and name lookup, descriptor
 * control, signals, processes, users, locales, wide characters and a few GNU and fortify entry points. Large C++
 * game clients (libc++ with its locale support, a socket client, a crash reporter) import most of these.
 *
 * Where bionic's ABI equals the 64-bit Linux kernel's, the host build passes calls straight to glibc: socket and
 * fcntl constants, sockaddr, fd_set, pollfd, msghdr, iovec, rusage, sysinfo, utsname, statfs, termios and syscall
 * numbers. Wrappers cover what differs:
 * - struct addrinfo: bionic orders ai_canonname before ai_addr, glibc the other way round, so getaddrinfo returns a
 *   bionic-shaped copy that freeaddrinfo frees;
 * - struct sigaction and sigset_t: bionic's LP64 sigset_t is 8 bytes. Handlers are recorded, never installed: the
 *   VM owns the process's signals, so native crash handlers do not run (sigaltstack is accepted and ignored);
 * - long double: 128-bit IEEE quad in bionic on x86-64 (as on AArch64), so strtold and wcstold go through double;
 * - locales: as in bionic, every *_l function ignores its locale, newlocale hands out a token, and the multibyte
 *   functions are always UTF-8 (glibc's would follow the host locale, newlib has few of them);
 * - processes: fork, exec*, waitpid and ptrace fail with ENOSYS; kill and raise only reach this process as a
 *   no-op; the user is the app's uid with no passwd entry.
 *
 * The Switch build keeps the portable parts (locales, wide characters, strings, logging) and answers ENOSYS for
 * sockets, descriptor control and the rest until newlib/libnx translations exist (TODO WS9/WS11: BSD socket
 * constants differ there, see ARCHITECTURE 6.7).
 */
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include "nativeloader.h"

#include <ctype.h>
#include <errno.h>
#include <limits.h>
#include <locale.h>
#include <math.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <wchar.h>
#include <wctype.h>
#ifndef __SWITCH__
#include <arpa/inet.h>
#include <fcntl.h>
#include <getopt.h>
#include <net/if.h>
#include <netdb.h>
#include <poll.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/random.h>
#include <sys/resource.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <sys/syscall.h>
#include <sys/sysinfo.h>
#include <sys/time.h>
#include <sys/uio.h>
#include <sys/utsname.h>
#include <termios.h>
#endif

#define LOG_TAG "libc"


/* Process.myUid(): FIRST_APPLICATION_UID */
#define APP_UID 10000

static int fail(int err) {
    errno = err;
    return -1;
}

/* ---- logging: syslog, abort messages, vasprintf --------------------------------------------------------- */

static char g_syslog_ident[64] = "native";

static void sh_openlog(const char *ident, int option, int facility) {
    SA_UNUSED(option);
    SA_UNUSED(facility);
    snprintf(g_syslog_ident, sizeof g_syslog_ident, "%s", ident ? ident : "native");
}

static void sh_closelog(void) {}

static void sh_vsyslog(int priority, const char *fmt, va_list ap) {
    char buf[1024];
    vsnprintf(buf, sizeof buf, fmt, ap);
    /* syslog's LOG_EMERG..LOG_DEBUG are 0..7 */
    int level = priority & 7;
    int prio = level <= 3 ? SA_LOG_ERROR : level == 4 ? SA_LOG_WARN : level <= 6 ? SA_LOG_INFO : SA_LOG_DEBUG;
    sa_log(prio, g_syslog_ident, "%s", buf);
}

static void sh_syslog(int priority, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    sh_vsyslog(priority, fmt, ap);
    va_end(ap);
}

static void sh_android_set_abort_message(const char *msg) { LOGE("abort message: %s", msg ? msg : "(null)"); }

static int sh_vasprintf(char **out, const char *fmt, va_list ap) {
    va_list ap2;
    va_copy(ap2, ap);
    int n = vsnprintf(NULL, 0, fmt, ap2);
    va_end(ap2);
    if (n < 0) return -1;
    char *s = malloc((size_t)n + 1);
    if (!s) return -1;
    vsnprintf(s, (size_t)n + 1, fmt, ap);
    *out = s;
    return n;
}

static int sh_asprintf(char **out, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int n = sh_vasprintf(out, fmt, ap);
    va_end(ap);
    return n;
}

/* ---- strings and fortify ---------------------------------------------------------------------------------- */

static void *sh_memrchr(const void *s, int c, size_t n) {
    const unsigned char *p = (const unsigned char *)s + n;
    while (n--) {
        if (*--p == (unsigned char)c) return (void *)p;
    }
    return NULL;
}

static void *sh_memchr_chk(const void *s, int c, size_t n, size_t buf_size) {
    if (n > buf_size) LOGE("__memchr_chk: %zu > %zu", n, buf_size);
    return memchr(s, c, n);
}

/* bionic's basename is POSIX's without modifying its argument: the result lives in a per-thread buffer */
static char *sh_basename(const char *path) {
    static _Thread_local char buf[PATH_MAX];
    if (!path || !*path) return strcpy(buf, ".");
    size_t end = strlen(path);
    while (end > 1 && path[end - 1] == '/') end--;
    size_t start = end;
    while (start > 0 && path[start - 1] != '/') start--;
    size_t len = end - start;
    if (len == 0) return strcpy(buf, "/");
    if (len >= sizeof buf) {
        errno = ENAMETOOLONG;
        return NULL;
    }
    memcpy(buf, path + start, len);
    buf[len] = 0;
    return buf;
}

/* GNU strerror_r: returns the message (maybe a static string) instead of an error code */
static char *sh_gnu_strerror_r(int err, char *buf, size_t n) {
    const char *msg = strerror(err);
    if (n) snprintf(buf, n, "%s", msg);
    return buf;
}

/* ---- math --------------------------------------------------------------------------------------------- */

static void sh_sincos(double x, double *s, double *c) {
    *s = sin(x);
    *c = cos(x);
}

static void sh_sincosf(float x, float *s, float *c) {
    *s = sinf(x);
    *c = cosf(x);
}

/* ---- locales: bionic semantics (one UTF-8 C locale; *_l ignore the locale) ------------------------------------ */

/* Any non-null value works as a locale_t: bionic's own are opaque and every *_l function ignores them. */
static struct {
    int dummy;
} g_locale_token;
static _Thread_local void *tl_uselocale; /* NULL: the global locale */

static void *sh_newlocale(int mask, const char *name, void *base) {
    SA_UNUSED(mask);
    SA_UNUSED(base);
    if (!name) {
        errno = EINVAL;
        return NULL;
    }
    /* bionic accepts "", "C", "POSIX" and the UTF-8 variants of C */
    if (*name && strcmp(name, "C") && strcmp(name, "POSIX") && strcmp(name, "C.UTF-8") && strcmp(name, "en_US.UTF-8")
        && strcmp(name, "C.utf8") && strcmp(name, "en_US.utf8")) {
        errno = ENOENT;
        return NULL;
    }
    return &g_locale_token;
}

static void *sh_duplocale(void *l) {
    SA_UNUSED(l);
    return &g_locale_token;
}

static void sh_freelocale(void *l) { SA_UNUSED(l); }

#define SH_LC_GLOBAL_LOCALE ((void *)-1L)

static void *sh_uselocale(void *l) {
    void *old = tl_uselocale ? tl_uselocale : SH_LC_GLOBAL_LOCALE;
    if (l == SH_LC_GLOBAL_LOCALE) tl_uselocale = NULL;
    else if (l) tl_uselocale = l;
    return old;
}

static char *sh_setlocale(int category, const char *name) {
    SA_UNUSED(category);
    static char current[16] = "C.UTF-8";
    if (!name) return current;
    if (!sh_newlocale(0, name, NULL)) return NULL;
    return current;
}

/* bionic's struct lconv (NetBSD order), filled for the C locale */
struct sh_lconv {
    char *decimal_point, *thousands_sep, *grouping, *int_curr_symbol, *currency_symbol, *mon_decimal_point,
        *mon_thousands_sep, *mon_grouping, *positive_sign, *negative_sign;
    char int_frac_digits, frac_digits, p_cs_precedes, p_sep_by_space, n_cs_precedes, n_sep_by_space, p_sign_posn,
        n_sign_posn, int_p_cs_precedes, int_n_cs_precedes, int_p_sep_by_space, int_n_sep_by_space, int_p_sign_posn,
        int_n_sign_posn;
};

static struct sh_lconv g_lconv = {
    ".", "", "", "", "", "", "", "", "", "", CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX,
    CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX, CHAR_MAX,
};

static struct sh_lconv *sh_localeconv(void) { return &g_lconv; }

static size_t sh_ctype_get_mb_cur_max(void) { return 4; }

#define LOCALE_IGNORED(ret, name, base, params, args)                                                                 \
    static ret sh_##name params {                                                                                    \
        SA_UNUSED(l);                                                                                                \
        return base args;                                                                                            \
    }

LOCALE_IGNORED(int, iswalpha_l, iswalpha, (wint_t c, void *l), (c))
LOCALE_IGNORED(int, iswblank_l, iswblank, (wint_t c, void *l), (c))
LOCALE_IGNORED(int, iswcntrl_l, iswcntrl, (wint_t c, void *l), (c))
LOCALE_IGNORED(int, iswdigit_l, iswdigit, (wint_t c, void *l), (c))
LOCALE_IGNORED(int, iswlower_l, iswlower, (wint_t c, void *l), (c))
LOCALE_IGNORED(int, iswprint_l, iswprint, (wint_t c, void *l), (c))
LOCALE_IGNORED(int, iswpunct_l, iswpunct, (wint_t c, void *l), (c))
LOCALE_IGNORED(int, iswspace_l, iswspace, (wint_t c, void *l), (c))
LOCALE_IGNORED(int, iswupper_l, iswupper, (wint_t c, void *l), (c))
LOCALE_IGNORED(int, iswxdigit_l, iswxdigit, (wint_t c, void *l), (c))
LOCALE_IGNORED(wint_t, towlower_l, towlower, (wint_t c, void *l), (c))
LOCALE_IGNORED(wint_t, towupper_l, towupper, (wint_t c, void *l), (c))
LOCALE_IGNORED(int, isalpha_l, isalpha, (int c, void *l), (c))
LOCALE_IGNORED(int, isdigit_l, isdigit, (int c, void *l), (c))
LOCALE_IGNORED(int, isspace_l, isspace, (int c, void *l), (c))
LOCALE_IGNORED(int, isupper_l, isupper, (int c, void *l), (c))
LOCALE_IGNORED(int, islower_l, islower, (int c, void *l), (c))
LOCALE_IGNORED(int, isxdigit_l, isxdigit, (int c, void *l), (c))
LOCALE_IGNORED(int, toupper_l, toupper, (int c, void *l), (c))
LOCALE_IGNORED(int, tolower_l, tolower, (int c, void *l), (c))
LOCALE_IGNORED(int, strcoll_l, strcmp, (const char *a, const char *b, void *l), (a, b))
LOCALE_IGNORED(long long, strtoll_l, strtoll, (const char *s, char **end, int base, void *l), (s, end, base))
LOCALE_IGNORED(unsigned long long, strtoull_l, strtoull, (const char *s, char **end, int base, void *l),
               (s, end, base))
LOCALE_IGNORED(double, strtod_l, strtod, (const char *s, char **end, void *l), (s, end))
LOCALE_IGNORED(float, strtof_l, strtof, (const char *s, char **end, void *l), (s, end))
LOCALE_IGNORED(size_t, strftime_l, strftime, (char *s, size_t n, const char *fmt, const struct tm *tm, void *l),
               (s, n, fmt, tm))

/* the C locale collates by code point, so the transform is a copy */
static size_t sh_strxfrm_l(char *dst, const char *src, size_t n, void *l) {
    SA_UNUSED(l);
    size_t len = strlen(src);
    if (n) {
        size_t c = len < n - 1 ? len : n - 1;
        memcpy(dst, src, c);
        dst[c] = 0;
    }
    return len;
}

static int sh_wcscoll_l(const wchar_t *a, const wchar_t *b, void *l) {
    SA_UNUSED(l);
    return wcscmp(a, b);
}

static size_t sh_wcsxfrm_l(wchar_t *dst, const wchar_t *src, size_t n, void *l) {
    SA_UNUSED(l);
    size_t len = wcslen(src);
    if (n) {
        size_t c = len < n - 1 ? len : n - 1;
        wmemcpy(dst, src, c);
        dst[c] = 0;
    }
    return len;
}

/* ---- long double: bionic's is IEEE binary128 on both 64-bit ABIs ------------------------------------------ */

#if defined(__x86_64__)
typedef __float128 sh_ldouble; /* glibc's x86-64 long double is the 80-bit x87 type: convert through double */
static sh_ldouble sh_strtold(const char *s, char **end) { return (sh_ldouble)strtod(s, end); }
static sh_ldouble sh_wcstold(const wchar_t *s, wchar_t **end) { return (sh_ldouble)wcstod(s, end); }
#else
typedef long double sh_ldouble;
static sh_ldouble sh_strtold(const char *s, char **end) { return strtold(s, end); }
static sh_ldouble sh_wcstold(const wchar_t *s, wchar_t **end) { return wcstold(s, end); }
#endif

static sh_ldouble sh_strtold_l(const char *s, char **end, void *l) {
    SA_UNUSED(l);
    return sh_strtold(s, end);
}

/* ---- multibyte: always UTF-8, as in bionic ------------------------------------------------------------------ */

/*
 * bionic's mbstate_t (8 bytes on LP64) holds the bytes of a partial sequence: byte 0..2 the bytes seen, byte 3 how
 * many. Zeroed means the initial state.
 */
typedef struct {
    unsigned char seq[3];
    unsigned char count;
    unsigned char reserved[4];
} ShMbState;

static ShMbState g_mbrlen_state, g_mbrtowc_state, g_wcrtomb_state, g_mbsrtowcs_state, g_wcsrtombs_state;

static int utf8_length(unsigned char c) {
    if (c < 0x80) return 1;
    if (c >= 0xc2 && c <= 0xdf) return 2;
    if (c >= 0xe0 && c <= 0xef) return 3;
    if (c >= 0xf0 && c <= 0xf4) return 4;
    return 0;
}

static size_t sh_mbrtowc(wchar_t *pwc, const char *s, size_t n, void *ps_arg) {
    ShMbState *ps = ps_arg ? ps_arg : (void *)&g_mbrtowc_state;
    if (!s) {
        s = "";
        n = 1;
        pwc = NULL;
    }
    if (n == 0) return (size_t)-2;
    unsigned char buf[4];
    int have = ps->count;
    memcpy(buf, ps->seq, (size_t)have);
    size_t used = 0;
    int need = have ? utf8_length(buf[0]) : utf8_length((unsigned char)s[0]);
    if (!have) {
        buf[0] = (unsigned char)s[0];
        have = 1;
        used = 1;
    }
    if (need == 0) {
        memset(ps, 0, sizeof *ps);
        errno = EILSEQ;
        return (size_t)-1;
    }
    while (have < need && used < n) {
        unsigned char c = (unsigned char)s[used];
        if ((c & 0xc0) != 0x80) {
            memset(ps, 0, sizeof *ps);
            errno = EILSEQ;
            return (size_t)-1;
        }
        buf[have++] = c;
        used++;
    }
    if (have < need) {
        memcpy(ps->seq, buf, (size_t)have);
        ps->count = (unsigned char)have;
        return (size_t)-2;
    }
    uint32_t cp;
    if (need == 1) cp = buf[0];
    else if (need == 2) cp = ((buf[0] & 0x1fu) << 6) | (buf[1] & 0x3fu);
    else if (need == 3) cp = ((buf[0] & 0x0fu) << 12) | ((buf[1] & 0x3fu) << 6) | (buf[2] & 0x3fu);
    else cp = ((buf[0] & 0x07u) << 18) | ((buf[1] & 0x3fu) << 12) | ((buf[2] & 0x3fu) << 6) | (buf[3] & 0x3fu);
    memset(ps, 0, sizeof *ps);
    /* overlong forms, surrogates and values past U+10FFFF */
    if ((need == 3 && cp < 0x800) || (need == 4 && (cp < 0x10000 || cp > 0x10ffff)) || (cp >= 0xd800 && cp <= 0xdfff)) {
        errno = EILSEQ;
        return (size_t)-1;
    }
    if (pwc) *pwc = (wchar_t)cp;
    return cp == 0 ? 0 : used;
}

static size_t sh_mbrlen(const char *s, size_t n, void *ps) {
    return sh_mbrtowc(NULL, s, n, ps ? ps : (void *)&g_mbrlen_state);
}

static int sh_mbtowc(wchar_t *pwc, const char *s, size_t n) {
    static ShMbState st;
    if (!s) {
        memset(&st, 0, sizeof st);
        return 0; /* UTF-8 has no shift states */
    }
    size_t r = sh_mbrtowc(pwc, s, n, &st);
    if (r == (size_t)-2) {
        memset(&st, 0, sizeof st);
        errno = EILSEQ;
        return -1;
    }
    return r == (size_t)-1 ? -1 : (int)r;
}

static int sh_mbsinit(const void *ps) { return !ps || ((const ShMbState *)ps)->count == 0; }

static size_t sh_wcrtomb(char *s, wchar_t wc, void *ps_arg) {
    ShMbState *ps = ps_arg ? ps_arg : (void *)&g_wcrtomb_state;
    memset(ps, 0, sizeof *ps);
    char tmp[4];
    if (!s) {
        s = tmp;
        wc = 0;
    }
    uint32_t cp = (uint32_t)wc;
    if (cp < 0x80) {
        s[0] = (char)cp;
        return 1;
    }
    if (cp < 0x800) {
        s[0] = (char)(0xc0 | (cp >> 6));
        s[1] = (char)(0x80 | (cp & 0x3f));
        return 2;
    }
    if (cp >= 0xd800 && cp <= 0xdfff) {
        errno = EILSEQ;
        return (size_t)-1;
    }
    if (cp < 0x10000) {
        s[0] = (char)(0xe0 | (cp >> 12));
        s[1] = (char)(0x80 | ((cp >> 6) & 0x3f));
        s[2] = (char)(0x80 | (cp & 0x3f));
        return 3;
    }
    if (cp <= 0x10ffff) {
        s[0] = (char)(0xf0 | (cp >> 18));
        s[1] = (char)(0x80 | ((cp >> 12) & 0x3f));
        s[2] = (char)(0x80 | ((cp >> 6) & 0x3f));
        s[3] = (char)(0x80 | (cp & 0x3f));
        return 4;
    }
    errno = EILSEQ;
    return (size_t)-1;
}

static int sh_wctomb(char *s, wchar_t wc) {
    if (!s) return 0;
    size_t r = sh_wcrtomb(s, wc, NULL);
    return r == (size_t)-1 ? -1 : (int)r;
}

static wint_t sh_btowc(int c) { return c == EOF || c < 0 || c > 0x7f ? WEOF : (wint_t)c; }
static int sh_wctob(wint_t c) { return c <= 0x7f ? (int)c : EOF; }

/* at most nms input bytes, at most len wide characters; dst NULL counts */
static size_t sh_mbsnrtowcs(wchar_t *dst, const char **src, size_t nms, size_t len, void *ps_arg) {
    ShMbState *ps = ps_arg ? ps_arg : (void *)&g_mbsrtowcs_state;
    const char *s = *src;
    size_t out = 0;
    while ((!dst || out < len) && nms > 0) {
        wchar_t wc;
        size_t r = sh_mbrtowc(&wc, s, nms, ps);
        if (r == (size_t)-1) {
            if (dst) *src = s;
            return (size_t)-1;
        }
        if (r == (size_t)-2) {
            /* the rest is an incomplete sequence, kept in the state */
            s += nms;
            nms = 0;
            break;
        }
        if (dst) dst[out] = wc;
        if (r == 0) {
            if (dst) *src = NULL;
            return out;
        }
        s += r;
        nms -= r;
        out++;
    }
    if (dst) *src = s;
    return out;
}

static size_t sh_mbsrtowcs(wchar_t *dst, const char **src, size_t len, void *ps) {
    return sh_mbsnrtowcs(dst, src, SIZE_MAX, len, ps);
}

static size_t sh_mbstowcs(wchar_t *dst, const char *src, size_t len) {
    ShMbState st = {{0}, 0, {0}};
    return sh_mbsrtowcs(dst, &src, len, &st);
}

/* at most nwc wide characters, at most len output bytes; dst NULL counts */
static size_t sh_wcsnrtombs(char *dst, const wchar_t **src, size_t nwc, size_t len, void *ps_arg) {
    SA_UNUSED(ps_arg);
    const wchar_t *s = *src;
    size_t out = 0;
    while (nwc > 0) {
        char buf[4];
        size_t r = sh_wcrtomb(buf, *s, NULL);
        if (r == (size_t)-1) {
            if (dst) *src = s;
            return (size_t)-1;
        }
        if (dst) {
            if (out + r > len) break;
            memcpy(dst + out, buf, r);
        }
        if (*s == 0) {
            if (dst) *src = NULL;
            return out;
        }
        out += r;
        s++;
        nwc--;
    }
    if (dst) *src = s;
    return out;
}

static size_t sh_wcsrtombs(char *dst, const wchar_t **src, size_t len, void *ps) {
    return sh_wcsnrtombs(dst, src, SIZE_MAX, len, ps ? ps : (void *)&g_wcsrtombs_state);
}

static size_t sh_wcstombs(char *dst, const wchar_t *src, size_t len) { return sh_wcsrtombs(dst, &src, len, NULL); }

/*
 * swprintf: format with the narrow printf (wide conversions %ls and %lc pass through it) after narrowing the
 * format, then widen the UTF-8 result. Formats are ASCII in practice.
 */
static int sh_vswprintf(wchar_t *dst, size_t n, const wchar_t *fmt, va_list ap) {
    size_t flen = wcslen(fmt);
    char *nf = malloc(flen * 4 + 1);
    if (!nf) return -1;
    const wchar_t *fp = fmt;
    if (sh_wcsrtombs(nf, &fp, flen * 4 + 1, NULL) == (size_t)-1) {
        free(nf);
        return -1;
    }
    va_list ap2;
    va_copy(ap2, ap);
    int len = vsnprintf(NULL, 0, nf, ap2);
    va_end(ap2);
    if (len < 0) {
        free(nf);
        return -1;
    }
    char *out = malloc((size_t)len + 1);
    if (!out) {
        free(nf);
        return -1;
    }
    vsnprintf(out, (size_t)len + 1, nf, ap);
    free(nf);
    const char *op = out;
    size_t w = sh_mbsrtowcs(NULL, &op, 0, NULL);
    int ret = -1;
    if (w != (size_t)-1 && w < n) {
        op = out;
        sh_mbsrtowcs(dst, &op, n, NULL);
        dst[w] = 0;
        ret = (int)w;
    } else if (n) {
        dst[0] = 0;
        errno = EOVERFLOW;
    }
    free(out);
    return ret;
}

static int sh_swprintf(wchar_t *dst, size_t n, const wchar_t *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int r = sh_vswprintf(dst, n, fmt, ap);
    va_end(ap);
    return r;
}

/* ---- users ------------------------------------------------------------------------------------------------ */

static unsigned sh_getuid(void) { return APP_UID; }

/* an app's parent is zygote, which native code cannot reach */
static int sh_getppid(void) { return 1; }

/* no passwd database on Android for app uids either: not found, no error */
static int sh_getpwuid_r(unsigned uid, void *pwd, char *buf, size_t n, void **result) {
    SA_UNUSED(uid);
    SA_UNUSED(pwd);
    SA_UNUSED(buf);
    SA_UNUSED(n);
    *result = NULL;
    return 0;
}

static void *sh_getpwuid(unsigned uid) {
    SA_UNUSED(uid);
    errno = ENOENT;
    return NULL;
}

/* ---- processes ---------------------------------------------------------------------------------------------- */

static int sh_fork(void) { return fail(ENOSYS); }

static int sh_execve(const char *path, char *const argv[], char *const envp[]) {
    SA_UNUSED(argv);
    SA_UNUSED(envp);
    LOGW("native code tried to exec %s", path ? path : "(null)");
    return fail(ENOSYS);
}

static int sh_execv(const char *path, char *const argv[]) { return sh_execve(path, argv, NULL); }
static int sh_execvp(const char *file, char *const argv[]) { return sh_execve(file, argv, NULL); }

static int sh_execl(const char *path, const char *arg, ...) {
    SA_UNUSED(arg);
    return sh_execve(path, NULL, NULL);
}

static int sh_waitpid(int pid, int *status, int options) {
    SA_UNUSED(pid);
    SA_UNUSED(status);
    SA_UNUSED(options);
    return fail(ECHILD);
}

static long sh_ptrace(int request, ...) {
    SA_UNUSED(request);
    return fail(EPERM);
}

/* signals to this process are dropped (the VM owns signal handling); other processes do not exist */
static int sh_kill(int pid, int sig) {
    if (pid != getpid() && pid != 0 && pid != -1) return fail(ESRCH);
    if (sig != 0) LOGW("native code sent signal %d to itself; ignored", sig);
    return 0;
}

static int sh_raise(int sig) { return sh_kill(getpid(), sig); }

static int sh_prctl(int option, unsigned long a2, unsigned long a3, unsigned long a4, unsigned long a5) {
    SA_UNUSED(a3);
    SA_UNUSED(a4);
    SA_UNUSED(a5);
    if (option == 15 /* PR_SET_NAME */ || option == 16 /* PR_GET_NAME */) {
        if (option == 16 && a2) strcpy((char *)a2, "native");
        return 0;
    }
    return 0; /* dumpable flags, vma names and the like: accepted */
}

/* ---- signals: recorded, not installed ------------------------------------------------------------------------ */

/* bionic LP64 */
typedef unsigned long sh_sigset_t;
typedef struct {
    int sa_flags;
    void *sa_handler; /* or sa_sigaction */
    sh_sigset_t sa_mask;
    void (*sa_restorer)(void);
} ShSigaction;

#define SH_NSIG 65
static ShSigaction g_sigactions[SH_NSIG];
static pthread_mutex_t g_sig_lock = PTHREAD_MUTEX_INITIALIZER;

static int sh_sigaction(int sig, const ShSigaction *act, ShSigaction *old) {
    if (sig <= 0 || sig >= SH_NSIG || sig == 9 /* SIGKILL */ || sig == 19 /* SIGSTOP */) return fail(EINVAL);
    pthread_mutex_lock(&g_sig_lock);
    if (old) *old = g_sigactions[sig];
    if (act) g_sigactions[sig] = *act;
    pthread_mutex_unlock(&g_sig_lock);
    return 0;
}

static void *sh_signal(int sig, void *handler) {
    ShSigaction act = {0, handler, 0, NULL}, old;
    if (sh_sigaction(sig, &act, &old) != 0) return (void *)-1L; /* SIG_ERR */
    return old.sa_handler;
}

static int sh_sigaltstack(const void *ss, void *old) {
    SA_UNUSED(ss);
    /* stack_t: void *ss_sp; int ss_flags; size_t ss_size (same in bionic and glibc); SS_DISABLE in old */
    if (old) {
        memset(old, 0, 24);
        ((int *)old)[2] = 2; /* SS_DISABLE */
    }
    return 0;
}

static int sh_sigemptyset(sh_sigset_t *s) {
    *s = 0;
    return 0;
}
static int sh_sigfillset(sh_sigset_t *s) {
    *s = ~0UL;
    return 0;
}
static int sh_sigaddset(sh_sigset_t *s, int sig) {
    if (sig <= 0 || sig >= SH_NSIG) return fail(EINVAL);
    *s |= 1UL << (sig - 1);
    return 0;
}
static int sh_sigdelset(sh_sigset_t *s, int sig) {
    if (sig <= 0 || sig >= SH_NSIG) return fail(EINVAL);
    *s &= ~(1UL << (sig - 1));
    return 0;
}
static int sh_sigismember(const sh_sigset_t *s, int sig) {
    if (sig <= 0 || sig >= SH_NSIG) return fail(EINVAL);
    return (*s >> (sig - 1)) & 1;
}

/* the mask is the VM's: report an empty one and change nothing */
static int sh_sigprocmask(int how, const sh_sigset_t *set, sh_sigset_t *old) {
    SA_UNUSED(how);
    SA_UNUSED(set);
    if (old) *old = 0;
    return 0;
}

/* ---- dl_iterate_phdr lives in elf_loader.c (it knows the loaded images) ----------------------------------------- */

int loader_dl_iterate_phdr(int (*cb)(void *info, size_t size, void *data), void *data);

/* ---- host-only: descriptors, sockets, name lookup, the kernel ----------------------------------------------- */

#ifndef __SWITCH__

static int sh_fcntl(int fd, int cmd, ...) {
    va_list ap;
    va_start(ap, cmd);
    long arg = va_arg(ap, long);
    va_end(ap);
    return fcntl(fd, cmd, arg);
}

static int sh_ioctl(int fd, unsigned long request, ...) {
    va_list ap;
    va_start(ap, request);
    void *arg = va_arg(ap, void *);
    va_end(ap);
    return ioctl(fd, request, arg);
}

/* the same numbers on the same architecture: Android's kernel is Linux */
static long sh_syscall(long n, long a, long b, long c, long d, long e, long f) { return syscall(n, a, b, c, d, e, f); }

static void sh_fd_set_chk(int fd, fd_set *set, size_t size) {
    if (fd < 0 || (size_t)fd >= size * 8) {
        LOGE("FD_SET: file descriptor %d >= FD_SETSIZE", fd);
        abort();
    }
    FD_SET(fd, set);
}

static ssize_t sh_pread64_chk(int fd, void *buf, size_t count, off_t offset, size_t buf_size) {
    if (count > buf_size) LOGE("__pread64_chk: %zu > %zu", count, buf_size);
    return pread(fd, buf, count, offset);
}

static int sh_rmdir(const char *path) {
    char *p = shim_map_path(path, true);
    if (!p) return -1;
    int r = rmdir(p);
    free(p);
    return r;
}

static int sh_chmod(const char *path, unsigned mode) {
    char *p = shim_map_path(path, true);
    if (!p) return -1;
    int r = chmod(p, mode);
    free(p);
    return r;
}

static int sh_utimes(const char *path, const struct timeval tv[2]) {
    char *p = shim_map_path(path, true);
    if (!p) return -1;
    int r = utimes(p, tv);
    free(p);
    return r;
}

static int sh_statfs(const char *path, struct statfs *st) {
    char *p = shim_map_path(path, false);
    if (!p) return -1;
    int r = statfs(p, st);
    free(p);
    return r;
}

/* /proc/self/exe and the like answer as app_process would; other links resolve through the path map */
static ssize_t sh_readlink(const char *path, char *buf, size_t n) {
    if (!strcmp(path, "/proc/self/exe")) {
        const char *exe = "/system/bin/app_process64";
        size_t len = strlen(exe) < n ? strlen(exe) : n;
        memcpy(buf, exe, len);
        return (ssize_t)len;
    }
    char *p = shim_map_path(path, false);
    if (!p) return -1;
    ssize_t r = readlink(p, buf, n);
    free(p);
    return r;
}

static ssize_t sh_readlink_chk(const char *path, char *buf, size_t n, size_t buf_size) {
    if (n > buf_size) LOGE("__readlink_chk: %zu > %zu", n, buf_size);
    return sh_readlink(path, buf, n);
}

/* the app's working directory is "/" on Android */
static char *sh_getcwd(char *buf, size_t n) {
    if (!buf) return strdup("/");
    if (n < 2) {
        errno = ERANGE;
        return NULL;
    }
    strcpy(buf, "/");
    return buf;
}

/* bionic's struct addrinfo: ai_canonname comes before ai_addr */
typedef struct ShAddrinfo {
    int ai_flags, ai_family, ai_socktype, ai_protocol;
    socklen_t ai_addrlen;
    char *ai_canonname;
    struct sockaddr *ai_addr;
    struct ShAddrinfo *ai_next;
} ShAddrinfo;

static int sh_getaddrinfo(const char *node, const char *service, const ShAddrinfo *hints, ShAddrinfo **res) {
    struct addrinfo h, *list = NULL;
    if (hints) {
        memset(&h, 0, sizeof h);
        h.ai_flags = hints->ai_flags;
        h.ai_family = hints->ai_family;
        h.ai_socktype = hints->ai_socktype;
        h.ai_protocol = hints->ai_protocol;
    }
    int r = getaddrinfo(node, service, hints ? &h : NULL, &list);
    LOGD("getaddrinfo(%s, %s): %s", node ? node : "NULL", service ? service : "NULL", r ? gai_strerror(r) : "ok");
    if (r != 0) {
        *res = NULL;
        return r; /* EAI_* values: bionic's are the positive BSD numbers, glibc's negative (mapped below) */
    }
    ShAddrinfo *head = NULL, **tail = &head;
    for (struct addrinfo *a = list; a; a = a->ai_next) {
        ShAddrinfo *b = calloc(1, sizeof *b + a->ai_addrlen);
        if (!b) break;
        b->ai_flags = a->ai_flags;
        b->ai_family = a->ai_family;
        b->ai_socktype = a->ai_socktype;
        b->ai_protocol = a->ai_protocol;
        b->ai_addrlen = a->ai_addrlen;
        b->ai_addr = (struct sockaddr *)(b + 1);
        memcpy(b->ai_addr, a->ai_addr, a->ai_addrlen);
        b->ai_canonname = a->ai_canonname ? strdup(a->ai_canonname) : NULL;
        *tail = b;
        tail = &b->ai_next;
    }
    freeaddrinfo(list);
    *res = head;
    return head ? 0 : EAI_MEMORY;
}

static void sh_freeaddrinfo(ShAddrinfo *a) {
    while (a) {
        ShAddrinfo *next = a->ai_next;
        free(a->ai_canonname);
        free(a);
        a = next;
    }
}

/* glibc's EAI_* are negative; bionic's (from BSD) are 1..14 */
static int eai_to_bionic(int r) {
    switch (r) {
    case 0: return 0;
    case EAI_AGAIN: return 2;
    case EAI_BADFLAGS: return 3;
    case EAI_FAIL: return 4;
    case EAI_FAMILY: return 5;
    case EAI_MEMORY: return 6;
    case EAI_NONAME: return 8;
    case EAI_SERVICE: return 9;
    case EAI_SOCKTYPE: return 10;
    case EAI_SYSTEM: return 11;
    case EAI_OVERFLOW: return 14;
    default: return 4;
    }
}

static int sh_getaddrinfo_bionic(const char *node, const char *service, const ShAddrinfo *hints, ShAddrinfo **res) {
    return eai_to_bionic(sh_getaddrinfo(node, service, hints, res));
}

static const char *sh_gai_strerror(int code) {
    static const char *const msgs[] = {
        "Success", "Address family for hostname not supported", "Temporary failure in name resolution",
        "Invalid value for ai_flags", "Non-recoverable failure in name resolution", "ai_family not supported",
        "Memory allocation failure", "No address associated with hostname",
        "hostname nor servname provided, or not known",
        "servname not supported for ai_socktype", "ai_socktype not supported", "System error returned in errno",
        "Invalid value for hints", "Resolved protocol is unknown", "Argument buffer overflow",
    };
    return code >= 0 && code < (int)SA_ARRAY_LEN(msgs) ? msgs[code] : "Unknown error";
}

static int sh_gethostname(char *name, size_t len) {
    if (len < 10) return fail(ENAMETOOLONG);
    strcpy(name, "localhost");
    return 0;
}

/* connect, with the address in the verbose log: native clients talk to their servers from C */
static int sh_connect(int fd, const struct sockaddr *addr, socklen_t len) {
    int r = connect(fd, addr, len);
    if (sa_log_level <= SA_LOG_DEBUG && addr) {
        char host[INET6_ADDRSTRLEN] = "?";
        int port = 0;
        if (addr->sa_family == AF_INET) {
            const struct sockaddr_in *in = (const struct sockaddr_in *)addr;
            inet_ntop(AF_INET, &in->sin_addr, host, sizeof host);
            port = ntohs(in->sin_port);
        } else if (addr->sa_family == AF_INET6) {
            const struct sockaddr_in6 *in = (const struct sockaddr_in6 *)addr;
            inet_ntop(AF_INET6, &in->sin6_addr, host, sizeof host);
            port = ntohs(in->sin6_port);
        }
        LOGD("connect(%d, %s port %d): %s", fd, host, port, r == 0 ? "ok" : strerror(errno));
    }
    return r;
}

/* Android reports the kernel truthfully, and the machine as the ABI's CPU */
static int sh_uname(struct utsname *u) {
    if (uname(u) != 0) return -1;
    snprintf(u->nodename, sizeof u->nodename, "localhost");
    return 0;
}

#endif /* !__SWITCH__ */

/* ---- the Switch: the same names, failing until newlib/libnx translations exist ---------------------------- */

#ifdef __SWITCH__

static int sh_enosys(void) { return fail(ENOSYS); }
static void *sh_enosys_ptr(void) {
    errno = ENOSYS;
    return NULL;
}
static long sh_syscall(long n) {
    LOGW("syscall(%ld) is not available on the Switch", n);
    return fail(ENOSYS);
}
static char *sh_getcwd(char *buf, size_t n) {
    if (!buf) return strdup("/");
    if (n < 2) {
        errno = ERANGE;
        return NULL;
    }
    strcpy(buf, "/");
    return buf;
}
static int sh_gethostname(char *name, size_t len) {
    if (len < 10) return fail(ENAMETOOLONG);
    strcpy(name, "localhost");
    return 0;
}
static const char *sh_gai_strerror(int code) {
    SA_UNUSED(code);
    return "Name resolution is not available";
}

#endif

/* ---- Android's system libstdc++.so: new/delete, pure virtuals, static-local guards ---------------------------- */

/* operator new cannot throw std::bad_alloc without a C++ runtime: allocation failure aborts, as -fno-exceptions does */
static void *sh_new(size_t n) {
    void *p = malloc(n ? n : 1);
    if (!p) {
        LOGE("operator new(%zu) failed", n);
        abort();
    }
    return p;
}
static void *sh_new_nothrow(size_t n, const void *tag) {
    SA_UNUSED(tag);
    return malloc(n ? n : 1);
}
static void sh_delete(void *p) { free(p); }
static void sh_delete_sized(void *p, size_t n) {
    SA_UNUSED(n);
    free(p);
}
static void sh_delete_nothrow(void *p, const void *tag) {
    SA_UNUSED(tag);
    free(p);
}

static void sh_cxa_pure_virtual(void) {
    LOGE("pure virtual function called");
    abort();
}

/* Itanium ABI guards: byte 0 set once the static is initialized; one recursive lock serializes initializers */
static pthread_mutex_t g_guard_lock = PTHREAD_RECURSIVE_MUTEX_INITIALIZER_NP;
static int sh_cxa_guard_acquire(uint64_t *g) {
    if (__atomic_load_n((uint8_t *)g, __ATOMIC_ACQUIRE)) return 0;
    pthread_mutex_lock(&g_guard_lock);
    if (*(uint8_t *)g) {
        pthread_mutex_unlock(&g_guard_lock);
        return 0;
    }
    return 1; /* the caller initializes, then calls release (or abort) */
}
static void sh_cxa_guard_release(uint64_t *g) {
    __atomic_store_n((uint8_t *)g, 1, __ATOMIC_RELEASE);
    pthread_mutex_unlock(&g_guard_lock);
}
static void sh_cxa_guard_abort(uint64_t *g) {
    SA_UNUSED(g);
    pthread_mutex_unlock(&g_guard_lock);
}

/* ---- misc -------------------------------------------------------------------------------------------------------- */

/* bionic's struct mallinfo has size_t fields */
struct sh_mallinfo {
    size_t arena, ordblks, smblks, hblks, hblkhd, usmblks, fsmblks, uordblks, fordblks, keepcost;
};

static struct sh_mallinfo sh_mallinfo(void) {
    struct sh_mallinfo m;
    memset(&m, 0, sizeof m);
    return m;
}

static int sh_dl_iterate_phdr(int (*cb)(void *info, size_t size, void *data), void *data) {
    return loader_dl_iterate_phdr(cb, data);
}

/* ---- the table ---------------------------------------------------------------------------------------- */

#define S(name) {#name, (void *)name}
#define W(name, fn) {#name, (void *)fn}

static const ShimSym g_syms[] = {
    /* logging */
    W(openlog, sh_openlog), W(closelog, sh_closelog), W(syslog, sh_syslog), W(vsyslog, sh_vsyslog),
    W(android_set_abort_message, sh_android_set_abort_message), W(vasprintf, sh_vasprintf),
    W(asprintf, sh_asprintf),
    /* strings, fortify */
    W(memrchr, sh_memrchr), W(__memchr_chk, sh_memchr_chk), W(basename, sh_basename),
    W(__gnu_strerror_r, sh_gnu_strerror_r), S(wcslen), S(wcscmp), S(wcsncmp), S(wcschr), S(wcsrchr), S(wcscpy),
    S(wcsncpy), S(wcscat), S(wcsstr), S(wcsspn), S(wcscspn), S(wcspbrk), S(wcsnlen), S(wmemchr), S(wmemcmp),
    S(wmemcpy), S(wmemmove), S(wmemset), S(wcstol), S(wcstoul), S(wcstoll), S(wcstoull), S(wcstod), S(wcstof),
    W(wcstold, sh_wcstold), S(wcscoll), S(wcsxfrm), S(wcsftime), S(nextafter), S(nextafterf),
    /* math */
    W(sincos, sh_sincos), W(sincosf, sh_sincosf),
    /* locales */
    W(newlocale, sh_newlocale), W(duplocale, sh_duplocale), W(freelocale, sh_freelocale),
    W(uselocale, sh_uselocale), W(setlocale, sh_setlocale), W(localeconv, sh_localeconv),
    W(__ctype_get_mb_cur_max, sh_ctype_get_mb_cur_max), W(iswalpha_l, sh_iswalpha_l), W(iswblank_l, sh_iswblank_l),
    W(iswcntrl_l, sh_iswcntrl_l), W(iswdigit_l, sh_iswdigit_l), W(iswlower_l, sh_iswlower_l),
    W(iswprint_l, sh_iswprint_l), W(iswpunct_l, sh_iswpunct_l), W(iswspace_l, sh_iswspace_l),
    W(iswupper_l, sh_iswupper_l), W(iswxdigit_l, sh_iswxdigit_l), W(towlower_l, sh_towlower_l),
    W(towupper_l, sh_towupper_l), W(isalpha_l, sh_isalpha_l), W(isdigit_l, sh_isdigit_l), W(isspace_l, sh_isspace_l),
    W(isupper_l, sh_isupper_l), W(islower_l, sh_islower_l), W(isxdigit_l, sh_isxdigit_l),
    W(toupper_l, sh_toupper_l), W(tolower_l, sh_tolower_l), W(strcoll_l, sh_strcoll_l), W(strxfrm_l, sh_strxfrm_l),
    W(strtoll_l, sh_strtoll_l), W(strtoull_l, sh_strtoull_l), W(strtod_l, sh_strtod_l), W(strtof_l, sh_strtof_l),
    W(strtold, sh_strtold), W(strtold_l, sh_strtold_l), W(strftime_l, sh_strftime_l), W(wcscoll_l, sh_wcscoll_l),
    W(wcsxfrm_l, sh_wcsxfrm_l), S(iswalpha), S(iswblank), S(iswcntrl), S(iswdigit), S(iswlower), S(iswprint),
    S(iswpunct), S(iswspace), S(iswupper), S(iswxdigit), S(iswalnum), S(towlower), S(towupper),
    /* multibyte (UTF-8) */
    W(mbrtowc, sh_mbrtowc), W(mbrlen, sh_mbrlen), W(mbtowc, sh_mbtowc), W(mbsinit, sh_mbsinit),
    W(wcrtomb, sh_wcrtomb), W(wctomb, sh_wctomb), W(btowc, sh_btowc), W(wctob, sh_wctob),
    W(mbsnrtowcs, sh_mbsnrtowcs), W(mbsrtowcs, sh_mbsrtowcs), W(mbstowcs, sh_mbstowcs),
    W(wcsnrtombs, sh_wcsnrtombs), W(wcsrtombs, sh_wcsrtombs), W(wcstombs, sh_wcstombs), W(swprintf, sh_swprintf),
    W(vswprintf, sh_vswprintf),
    /* users, processes, signals */
    W(getuid, sh_getuid), W(geteuid, sh_getuid), W(getgid, sh_getuid), W(getegid, sh_getuid),
    W(getpwuid_r, sh_getpwuid_r), W(getpwuid, sh_getpwuid), W(getppid, sh_getppid), W(fork, sh_fork), W(execve, sh_execve),
    W(execv, sh_execv), W(execvp, sh_execvp), W(execl, sh_execl), W(waitpid, sh_waitpid), W(ptrace, sh_ptrace),
    W(kill, sh_kill), W(raise, sh_raise), W(prctl, sh_prctl), W(sigaction, sh_sigaction), W(signal, sh_signal),
    W(bsd_signal, sh_signal), W(sigaltstack, sh_sigaltstack), W(sigemptyset, sh_sigemptyset),
    W(sigfillset, sh_sigfillset), W(sigaddset, sh_sigaddset), W(sigdelset, sh_sigdelset),
    W(sigismember, sh_sigismember), W(sigprocmask, sh_sigprocmask), W(pthread_sigmask, sh_sigprocmask),
    /* misc */
    /* libstdc++ */
    W(_Znwm, sh_new), W(_Znam, sh_new), W(_ZnwmRKSt9nothrow_t, sh_new_nothrow), W(_ZnamRKSt9nothrow_t, sh_new_nothrow),
    W(_ZdlPv, sh_delete), W(_ZdaPv, sh_delete), W(_ZdlPvm, sh_delete_sized), W(_ZdaPvm, sh_delete_sized),
    W(_ZdlPvRKSt9nothrow_t, sh_delete_nothrow), W(_ZdaPvRKSt9nothrow_t, sh_delete_nothrow),
    W(__cxa_pure_virtual, sh_cxa_pure_virtual), W(__cxa_guard_acquire, sh_cxa_guard_acquire),
    W(__cxa_guard_release, sh_cxa_guard_release), W(__cxa_guard_abort, sh_cxa_guard_abort),
    W(mallinfo, sh_mallinfo), W(dl_iterate_phdr, sh_dl_iterate_phdr), W(getcwd, sh_getcwd),
    W(gethostname, sh_gethostname), W(gai_strerror, sh_gai_strerror), S(tzset),
#ifndef __SWITCH__
    /* descriptors and files */
    W(fcntl, sh_fcntl), W(ioctl, sh_ioctl), W(syscall, sh_syscall), S(fsync), S(fdatasync), S(ftruncate),
    W(ftruncate64, ftruncate), S(fchmod), S(fchown), W(lseek64, lseek), S(pread), W(pread64, pread), S(pwrite),
    W(pwrite64, pwrite), S(readv), S(writev), W(__FD_SET_chk, sh_fd_set_chk), W(__pread64_chk, sh_pread64_chk),
    W(__readlink_chk, sh_readlink_chk), W(rmdir, sh_rmdir), W(chmod, sh_chmod), W(utimes, sh_utimes),
    W(statfs, sh_statfs), W(readlink, sh_readlink), S(mremap), S(msync), S(mlock), S(munlock), S(poll),
    S(select), S(pselect), S(epoll_create), S(epoll_create1), S(epoll_ctl), S(epoll_wait), S(eventfd),
    /* sockets */
    S(socket), S(socketpair), S(bind), S(listen), S(accept), S(accept4), W(connect, sh_connect), S(shutdown), S(send),
    S(recv), S(sendto), S(recvfrom), S(sendmsg), S(recvmsg), S(setsockopt), S(getsockopt), S(getsockname),
    S(getpeername), S(__cmsg_nxthdr),
    /* name lookup */
    W(getaddrinfo, sh_getaddrinfo_bionic), W(freeaddrinfo, sh_freeaddrinfo), S(getnameinfo), S(gethostbyname),
    S(gethostbyaddr), S(getservbyname), S(getservbyport), S(inet_ntop), S(inet_pton), S(inet_addr), S(inet_aton),
    S(inet_ntoa), S(if_nametoindex), S(if_indextoname), S(htonl), S(htons), S(ntohl), S(ntohs),
    /* the kernel */
    S(getrusage), S(getpriority), S(setpriority), S(sysinfo), W(uname, sh_uname), S(sched_getparam),
    S(sched_getscheduler), S(sched_setscheduler), S(sched_get_priority_max), S(sched_get_priority_min),
    S(tcgetattr), S(tcsetattr), S(getrlimit), S(setrlimit),
    S(getrandom), S(getentropy), S(arc4random), S(arc4random_buf), S(arc4random_uniform),
    /* data: glibc's match bionic's types (char *tzname[2], long timezone, int daylight; getopt's ints) */
    W(tzname, &tzname), W(timezone, &timezone), W(daylight, &daylight), W(optarg, &optarg), W(optind, &optind),
    W(opterr, &opterr), W(optopt, &optopt), S(getopt), S(getopt_long),
#else
    /* sockets, fcntl, ioctl, select, poll and name lookup: shim_bsd.c */
    W(syscall, sh_syscall), W(socketpair, sh_enosys), W(getservbyname, sh_enosys_ptr), W(if_nametoindex, sh_enosys), W(getrusage, sh_enosys), W(sysinfo, sh_enosys),
    W(uname, sh_enosys), W(tcgetattr, sh_enosys), W(tcsetattr, sh_enosys), W(readlink, sh_enosys),
    W(rmdir, sh_enosys), W(chmod, sh_enosys), W(utimes, sh_enosys), W(mremap, sh_enosys_ptr),
    W(getpriority, sh_enosys), W(setpriority, sh_enosys), S(fsync), S(ftruncate), W(lseek64, lseek),
#endif
};

const ShimSym *shim_posix_symbols(size_t *n) {
    *n = SA_ARRAY_LEN(g_syms);
    return g_syms;
}
