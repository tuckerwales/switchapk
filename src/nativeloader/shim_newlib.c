/*
 * bionic's file, time and thread ABI on newlib (WS9, the Switch). Native code built for arm64 Android passes Linux
 * open flags and clock ids, and lays out struct stat, dirent, tm and pthread_once_t as bionic does; newlib numbers
 * and lays them out differently (O_CREAT is 0x200, CLOCK_MONOTONIC 4, stat has no padding words, tm has no
 * tm_gmtoff, pthread_once_t is 8 bytes). The pure translations and the portable futex and once implementations
 * (snl_*) are compiled on every target and checked on the host by tests/c/shim_newlib_test.c; the wrappers that call
 * newlib and libnx are Switch-only, where their table replaces the earlier ones (shim_lookup joins it last).
 *
 * Also Switch-only: mmap over the heap (anonymous memory, and private copies of files), the common syscall()
 * numbers (gettid, futex, getrandom, clocks, membarrier, ...), per-thread ids, and arc4random/getrandom from libnx.
 */
#include "nativeloader.h"
#include "shim_newlib.h"

#include <errno.h>
#include <pthread.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>

/* ---- open flags and clock ids ------------------------------------------------------------------------------- */

/* Linux arm64 (asm-generic, with arm64's O_DIRECTORY/O_NOFOLLOW/O_DIRECT/O_LARGEFILE) */
#define L_O_ACCMODE 3
#define L_O_CREAT 0x40
#define L_O_EXCL 0x80
#define L_O_NOCTTY 0x100
#define L_O_TRUNC 0x200
#define L_O_APPEND 0x400
#define L_O_NONBLOCK 0x800
#define L_O_DSYNC 0x1000
#define L_O_DIRECTORY 0x4000
#define L_O_NOFOLLOW 0x8000
#define L_O_DIRECT 0x10000
#define L_O_LARGEFILE 0x20000
#define L_O_CLOEXEC 0x80000
#define L_O_PATH 0x200000
#define L_O_TMPFILE_BIT 0x400000
/* newlib (sys/_default_fcntl.h) */
#define N_O_APPEND 0x0008
#define N_O_CREAT 0x0200
#define N_O_TRUNC 0x0400
#define N_O_EXCL 0x0800
#define N_O_SYNC 0x2000
#define N_O_NONBLOCK 0x4000
#define N_O_NOCTTY 0x8000
#define N_O_CLOEXEC 0x40000
#define N_O_DIRECT 0x80000
#define N_O_NOFOLLOW 0x100000
#define N_O_DIRECTORY 0x200000

int snl_oflags_from_linux(int f) {
    if (f & (L_O_PATH | L_O_TMPFILE_BIT)) return -1;
    static const int map[][2] = {
        {L_O_CREAT, N_O_CREAT},         {L_O_EXCL, N_O_EXCL},           {L_O_NOCTTY, N_O_NOCTTY},
        {L_O_TRUNC, N_O_TRUNC},         {L_O_APPEND, N_O_APPEND},       {L_O_NONBLOCK, N_O_NONBLOCK},
        {L_O_DSYNC, N_O_SYNC},          {L_O_DIRECTORY, N_O_DIRECTORY}, {L_O_NOFOLLOW, N_O_NOFOLLOW},
        {L_O_DIRECT, N_O_DIRECT},       {L_O_CLOEXEC, N_O_CLOEXEC},
    };
    int out = f & L_O_ACCMODE; /* O_RDONLY/O_WRONLY/O_RDWR are 0/1/2 on both; O_LARGEFILE has no meaning */
    for (size_t i = 0; i < SA_ARRAY_LEN(map); i++)
        if (f & map[i][0]) out |= map[i][1];
    return out;
}

int snl_clock_from_linux(int clk) {
    switch (clk) {
    case 0: return 1; /* CLOCK_REALTIME */
    case 1: return 4; /* CLOCK_MONOTONIC */
    case 2: return 2; /* CLOCK_PROCESS_CPUTIME_ID */
    case 3: return 3; /* CLOCK_THREAD_CPUTIME_ID */
    case 4: return 4; /* CLOCK_MONOTONIC_RAW */
    case 5: return 1; /* CLOCK_REALTIME_COARSE */
    case 6: return 4; /* CLOCK_MONOTONIC_COARSE */
    case 7: return 4; /* CLOCK_BOOTTIME: the console does not suspend the app's clock */
    default: return -1;
    }
}

/* ---- structs ------------------------------------------------------------------------------------------------ */

void snl_fill_stat(SnlStat *o, const SnlStatFields *in) {
    memset(o, 0, sizeof *o);
    o->st_dev = in->dev;
    o->st_ino = in->ino;
    o->st_mode = in->mode;
    o->st_nlink = in->nlink;
    o->st_uid = in->uid;
    o->st_gid = in->gid;
    o->st_rdev = in->rdev;
    o->st_size = in->size;
    o->st_blksize = (int32_t)in->blksize;
    o->st_blocks = in->blocks;
    o->st_atim_sec = in->atime;
    o->st_atim_nsec = in->atime_ns;
    o->st_mtim_sec = in->mtime;
    o->st_mtim_nsec = in->mtime_ns;
    o->st_ctim_sec = in->ctime_;
    o->st_ctim_nsec = in->ctime_ns;
}

void snl_fill_dirent(SnlDirent *o, uint64_t ino, uint8_t type, const char *name, int64_t off) {
    memset(o, 0, sizeof *o);
    o->d_ino = ino ? ino : 1; /* some code skips entries with inode 0 */
    o->d_off = off;
    o->d_type = type;
    size_t n = strlen(name);
    if (n > sizeof o->d_name - 1) n = sizeof o->d_name - 1;
    memcpy(o->d_name, name, n);
    o->d_reclen = (uint16_t)((offsetof(SnlDirent, d_name) + n + 1 + 7) & ~(size_t)7);
}

void snl_tm_to_bionic(SnlTm *o, const int f[9], long gmtoff, const char *zone) {
    o->tm_sec = f[0];
    o->tm_min = f[1];
    o->tm_hour = f[2];
    o->tm_mday = f[3];
    o->tm_mon = f[4];
    o->tm_year = f[5];
    o->tm_wday = f[6];
    o->tm_yday = f[7];
    o->tm_isdst = f[8];
    o->tm_gmtoff = gmtoff;
    o->tm_zone = zone;
}

void snl_tm_from_bionic(int f[9], const SnlTm *in) {
    f[0] = in->tm_sec;
    f[1] = in->tm_min;
    f[2] = in->tm_hour;
    f[3] = in->tm_mday;
    f[4] = in->tm_mon;
    f[5] = in->tm_year;
    f[6] = in->tm_wday;
    f[7] = in->tm_yday;
    f[8] = in->tm_isdst;
}

/* ---- futex and once ------------------------------------------------------------------------------------------- */

/* Waiters hash by address into buckets; a wake broadcasts its bucket and waiters recheck their word. */
#define FUTEX_BUCKETS 64
static struct {
    pthread_mutex_t lock;
    pthread_cond_t cond;
    int waiters;
} g_futex[FUTEX_BUCKETS];
static pthread_once_t g_futex_once = PTHREAD_ONCE_INIT;

static void futex_init(void) {
    for (int i = 0; i < FUTEX_BUCKETS; i++) {
        pthread_mutex_init(&g_futex[i].lock, NULL);
        pthread_cond_init(&g_futex[i].cond, NULL);
    }
}

static int bucket_of(volatile uint32_t *addr) { return (int)(((uintptr_t)addr >> 2) % FUTEX_BUCKETS); }

int snl_futex_wait(volatile uint32_t *addr, uint32_t expected, const struct timespec *rel) {
    pthread_once(&g_futex_once, futex_init);
    int b = bucket_of(addr);
    pthread_mutex_lock(&g_futex[b].lock);
    if (__atomic_load_n(addr, __ATOMIC_SEQ_CST) != expected) {
        pthread_mutex_unlock(&g_futex[b].lock);
        errno = EAGAIN;
        return -1;
    }
    g_futex[b].waiters++;
    int r = 0;
    if (rel) {
        struct timespec ts;
        clock_gettime(CLOCK_REALTIME, &ts);
        ts.tv_sec += rel->tv_sec;
        ts.tv_nsec += rel->tv_nsec;
        while (ts.tv_nsec >= 1000000000L) {
            ts.tv_sec++;
            ts.tv_nsec -= 1000000000L;
        }
        r = pthread_cond_timedwait(&g_futex[b].cond, &g_futex[b].lock, &ts);
    } else {
        r = pthread_cond_wait(&g_futex[b].cond, &g_futex[b].lock);
    }
    g_futex[b].waiters--;
    pthread_mutex_unlock(&g_futex[b].lock);
    if (r == ETIMEDOUT) {
        errno = ETIMEDOUT;
        return -1;
    }
    return 0; /* woken, or spuriously: callers recheck, as with the kernel */
}

int snl_futex_wake(volatile uint32_t *addr, int count) {
    pthread_once(&g_futex_once, futex_init);
    int b = bucket_of(addr);
    pthread_mutex_lock(&g_futex[b].lock);
    int n = g_futex[b].waiters < count ? g_futex[b].waiters : count;
    if (g_futex[b].waiters) pthread_cond_broadcast(&g_futex[b].cond);
    pthread_mutex_unlock(&g_futex[b].lock);
    return n;
}

/* 0 not run, 1 running, 2 done */
int snl_once(int *once, void (*fn)(void)) {
    volatile uint32_t *w = (volatile uint32_t *)once;
    if (__atomic_load_n(w, __ATOMIC_ACQUIRE) == 2) return 0;
    uint32_t expected = 0;
    if (__atomic_compare_exchange_n(w, &expected, 1, false, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE)) {
        fn();
        __atomic_store_n(w, 2, __ATOMIC_RELEASE);
        snl_futex_wake(w, 0x7fffffff);
        return 0;
    }
    while (__atomic_load_n(w, __ATOMIC_ACQUIRE) != 2) snl_futex_wait(w, 1, NULL);
    return 0;
}

/* ---- the Switch: newlib and libnx wrappers ------------------------------------------------------------------- */

#ifdef __SWITCH__

#include <dirent.h>
#include <fcntl.h>
#include <malloc.h>
#include <stdarg.h>
#include <sys/stat.h>
#include <switch.h>
#include <unistd.h>

#define LOG_TAG "libc"

_Static_assert(sizeof(SnlStat) == 128 && sizeof(SnlTm) == 56 && offsetof(SnlDirent, d_name) == 19, "bionic layouts");
_Static_assert(O_CREAT == N_O_CREAT && O_TRUNC == N_O_TRUNC && O_EXCL == N_O_EXCL && O_APPEND == N_O_APPEND &&
                   O_NONBLOCK == N_O_NONBLOCK && O_CLOEXEC == N_O_CLOEXEC && O_DIRECTORY == N_O_DIRECTORY &&
                   O_NOFOLLOW == N_O_NOFOLLOW && O_SYNC == N_O_SYNC && O_NOCTTY == N_O_NOCTTY,
               "newlib open flags");
_Static_assert(CLOCK_REALTIME == 1 && CLOCK_MONOTONIC == 4, "newlib clock ids");
_Static_assert(sizeof(struct tm) == 9 * sizeof(int), "newlib struct tm has no tm_gmtoff");

static int fail(int err) {
    errno = err;
    return -1;
}

static int sh_open(const char *path, int flags, ...) {
    int mode = 0;
    if (flags & L_O_CREAT) {
        va_list ap;
        va_start(ap, flags);
        mode = va_arg(ap, int);
        va_end(ap);
    }
    int nf = snl_oflags_from_linux(flags);
    if (nf < 0) return fail(EINVAL);
    char *host = shim_map_path(path, (flags & (1 | 2 | L_O_CREAT | L_O_TRUNC | L_O_APPEND)) != 0);
    if (!host) return -1;
    int fd = open(host, nf, mode);
    free(host);
    return fd;
}

static int sh_open_2(const char *path, int flags) { return sh_open(path, flags); }

#define L_AT_FDCWD (-100)

static int sh_openat(int dirfd, const char *path, int flags, ...) {
    int mode = 0;
    if (flags & L_O_CREAT) {
        va_list ap;
        va_start(ap, flags);
        mode = va_arg(ap, int);
        va_end(ap);
    }
    if (dirfd != L_AT_FDCWD && path[0] != '/') return fail(ENOSYS); /* no directory descriptors in newlib */
    return sh_open(path, flags, mode);
}

static void from_newlib_stat(SnlStat *out, const struct stat *st) {
    SnlStatFields f = {
        .dev = (uint64_t)st->st_dev,
        .ino = (uint64_t)st->st_ino,
        .rdev = (uint64_t)st->st_rdev,
        .mode = (uint32_t)st->st_mode,
        .nlink = (uint32_t)st->st_nlink,
        .uid = (uint32_t)st->st_uid,
        .gid = (uint32_t)st->st_gid,
        .size = (int64_t)st->st_size,
        .blksize = (int64_t)st->st_blksize,
        .blocks = (int64_t)st->st_blocks,
        .atime = (int64_t)st->st_atim.tv_sec,
        .atime_ns = st->st_atim.tv_nsec,
        .mtime = (int64_t)st->st_mtim.tv_sec,
        .mtime_ns = st->st_mtim.tv_nsec,
        .ctime_ = (int64_t)st->st_ctim.tv_sec,
        .ctime_ns = st->st_ctim.tv_nsec,
    };
    snl_fill_stat(out, &f);
}

static int sh_stat(const char *path, SnlStat *out) {
    char *host = shim_map_path(path, false);
    if (!host) return -1;
    struct stat st;
    int r = stat(host, &st);
    free(host);
    if (r == 0) from_newlib_stat(out, &st);
    return r;
}

static int sh_fstat(int fd, SnlStat *out) {
    struct stat st;
    int r = fstat(fd, &st);
    if (r == 0) from_newlib_stat(out, &st);
    return r;
}

static int sh_fstatat(int dirfd, const char *path, SnlStat *out, int flags) {
    SA_UNUSED(flags);
    if (dirfd != L_AT_FDCWD && path[0] != '/') return fail(ENOSYS);
    return sh_stat(path, out);
}

/* one bionic dirent per thread, as glibc and bionic keep one per DIR */
static SnlDirent *sh_readdir(DIR *d) {
    static _Thread_local SnlDirent out;
    static _Thread_local int64_t pos;
    struct dirent *e = readdir(d);
    if (!e) return NULL;
    snl_fill_dirent(&out, (uint64_t)e->d_ino, e->d_type, e->d_name, ++pos);
    return &out;
}

static int sh_readdir_r(DIR *d, SnlDirent *entry, SnlDirent **result) {
    struct dirent *e = readdir(d);
    if (!e) {
        *result = NULL;
        return 0;
    }
    snl_fill_dirent(entry, (uint64_t)e->d_ino, e->d_type, e->d_name, 0);
    *result = entry;
    return 0;
}

static int sh_clock_gettime(int clk, struct timespec *ts) {
    int c = snl_clock_from_linux(clk);
    if (c < 0) return fail(EINVAL);
    return clock_gettime((clockid_t)c, ts);
}

static int sh_clock_getres(int clk, struct timespec *ts) {
    int c = snl_clock_from_linux(clk);
    if (c < 0) return fail(EINVAL);
    if (ts) {
        ts->tv_sec = 0;
        ts->tv_nsec = 1; /* armGetSystemTick is 19.2 MHz; report nanoseconds like Linux's high-resolution clocks */
    }
    return 0;
}

/* returns an error number, as clock_nanosleep does */
static int sh_clock_nanosleep(int clk, int flags, const struct timespec *req, struct timespec *rem) {
    int c = snl_clock_from_linux(clk);
    if (c < 0) return EINVAL;
    struct timespec rel = *req;
    if (flags & 1 /* TIMER_ABSTIME */) {
        struct timespec now;
        clock_gettime((clockid_t)c, &now);
        int64_t ns = ((int64_t)req->tv_sec - now.tv_sec) * 1000000000LL + (req->tv_nsec - now.tv_nsec);
        if (ns <= 0) return 0;
        rel.tv_sec = ns / 1000000000LL;
        rel.tv_nsec = ns % 1000000000LL;
    }
    return nanosleep(&rel, rem) == 0 ? 0 : errno;
}

static int sh_pthread_once(int *once, void (*fn)(void)) { return snl_once(once, fn); }

/* newlib fills nine ints; bionic's tm_gmtoff and tm_zone come from the time zone tzset() read */
static SnlTm *to_bionic_tm(const struct tm *t, SnlTm *out, bool local) {
    if (!t) return NULL;
    int f[9] = {t->tm_sec, t->tm_min, t->tm_hour, t->tm_mday, t->tm_mon, t->tm_year, t->tm_wday, t->tm_yday,
                t->tm_isdst};
    long off = local ? -_timezone + (t->tm_isdst > 0 ? 3600 : 0) : 0;
    const char *zone = local ? _tzname[t->tm_isdst > 0 ? 1 : 0] : "GMT";
    snl_tm_to_bionic(out, f, off, zone ? zone : "UTC");
    return out;
}

static SnlTm *sh_localtime_r(const time_t *t, SnlTm *out) {
    struct tm tmp;
    return to_bionic_tm(localtime_r(t, &tmp), out, true);
}

static SnlTm *sh_gmtime_r(const time_t *t, SnlTm *out) {
    struct tm tmp;
    return to_bionic_tm(gmtime_r(t, &tmp), out, false);
}

static SnlTm *sh_localtime(const time_t *t) {
    static _Thread_local SnlTm out;
    return sh_localtime_r(t, &out);
}

static SnlTm *sh_gmtime(const time_t *t) {
    static _Thread_local SnlTm out;
    return sh_gmtime_r(t, &out);
}

/* ---- mmap over the heap ---- */

#define L_MAP_SHARED 0x01
#define L_MAP_FIXED 0x10
#define L_MAP_ANONYMOUS 0x20
#define L_PROT_WRITE 0x2

typedef struct Mapping {
    void *addr;
    size_t len;
    struct Mapping *next;
} Mapping;
static Mapping *g_maps;
static pthread_mutex_t g_maps_lock = PTHREAD_MUTEX_INITIALIZER;

/*
 * Anonymous memory is zeroed heap pages; a file mapping is a private copy read at map time (a MAP_SHARED writable
 * mapping would not reach the file, so it is refused). MAP_FIXED cannot be honoured.
 */
static void *sh_mmap(void *addr, size_t len, int prot, int flags, int fd, long off) {
    SA_UNUSED(addr);
    if (len == 0) {
        errno = EINVAL;
        return (void *)-1L;
    }
    if (flags & L_MAP_FIXED) {
        LOGW("mmap(MAP_FIXED) is not supported on the Switch");
        errno = ENOMEM;
        return (void *)-1L;
    }
    if (!(flags & L_MAP_ANONYMOUS) && (flags & L_MAP_SHARED) && (prot & L_PROT_WRITE)) {
        LOGW("mmap of a file with MAP_SHARED and PROT_WRITE is not supported on the Switch");
        errno = ENODEV;
        return (void *)-1L;
    }
    size_t rounded = (len + 0xfff) & ~(size_t)0xfff;
    void *p = memalign(0x1000, rounded);
    if (!p) {
        errno = ENOMEM;
        return (void *)-1L;
    }
    memset(p, 0, rounded);
    if (!(flags & L_MAP_ANONYMOUS)) {
        off_t saved = lseek(fd, 0, SEEK_CUR);
        if (lseek(fd, (off_t)off, SEEK_SET) < 0) {
            free(p);
            return (void *)-1L;
        }
        size_t got = 0;
        while (got < len) {
            ssize_t n = read(fd, (char *)p + got, len - got);
            if (n <= 0) break;
            got += (size_t)n;
        }
        lseek(fd, saved, SEEK_SET);
    }
    Mapping *m = malloc(sizeof *m);
    if (!m) {
        free(p);
        errno = ENOMEM;
        return (void *)-1L;
    }
    m->addr = p;
    m->len = rounded;
    pthread_mutex_lock(&g_maps_lock);
    m->next = g_maps;
    g_maps = m;
    pthread_mutex_unlock(&g_maps_lock);
    return p;
}

/* whole mappings only; a partial unmap keeps the memory (it is reused when the rest goes) */
static int sh_munmap(void *addr, size_t len) {
    SA_UNUSED(len);
    pthread_mutex_lock(&g_maps_lock);
    for (Mapping **pp = &g_maps; *pp; pp = &(*pp)->next) {
        Mapping *m = *pp;
        if (m->addr == addr) {
            *pp = m->next;
            pthread_mutex_unlock(&g_maps_lock);
            free(m->addr);
            free(m);
            return 0;
        }
    }
    pthread_mutex_unlock(&g_maps_lock);
    return 0;
}

static int sh_mem_ok(void *addr, size_t len, int arg) {
    SA_UNUSED(addr);
    SA_UNUSED(len);
    SA_UNUSED(arg);
    return 0; /* mprotect, madvise, msync: heap pages stay read-write */
}

static int sh_mem_ok2(const void *addr, size_t len) {
    SA_UNUSED(addr);
    SA_UNUSED(len);
    return 0; /* mlock, munlock */
}

/* ---- thread ids, random bytes, syscall() ---- */

static _Thread_local int tl_tid;
static int g_next_tid = 10001;

static int sh_gettid(void) {
    if (!tl_tid) tl_tid = __atomic_fetch_add(&g_next_tid, 1, __ATOMIC_RELAXED);
    return tl_tid;
}

static long sh_getrandom(void *buf, size_t n, unsigned flags) {
    SA_UNUSED(flags);
    randomGet(buf, n);
    return (long)n;
}

static int sh_getentropy(void *buf, size_t n) {
    if (n > 256) return fail(EIO);
    randomGet(buf, n);
    return 0;
}

static uint32_t sh_arc4random(void) {
    uint32_t v;
    randomGet(&v, sizeof v);
    return v;
}

static void sh_arc4random_buf(void *buf, size_t n) { randomGet(buf, n); }

static uint32_t sh_arc4random_uniform(uint32_t bound) {
    if (bound < 2) return 0;
    uint32_t min = -bound % bound, r; /* reject the low values that bias the modulo */
    do r = sh_arc4random();
    while (r < min);
    return r % bound;
}

/* Linux AArch64 syscall numbers (asm-generic) */
enum {
    NR_close = 57, NR_read = 63, NR_write = 64, NR_futex = 98, NR_nanosleep = 101, NR_clock_gettime = 113,
    NR_clock_getres = 114, NR_clock_nanosleep = 115, NR_sched_getaffinity = 123, NR_sched_yield = 124,
    NR_tkill = 130, NR_tgkill = 131, NR_sigaltstack = 132, NR_rt_sigprocmask = 135, NR_prctl = 167,
    NR_getcpu = 168, NR_gettimeofday = 169, NR_getpid = 172, NR_getppid = 173, NR_getuid = 174, NR_geteuid = 175,
    NR_gettid = 178, NR_madvise = 233, NR_getrandom = 278, NR_membarrier = 283,
};

static long sh_syscall(long n, long a, long b, long c, long d, long e, long f) {
    SA_UNUSED(e);
    SA_UNUSED(f);
    switch (n) {
    case NR_gettid: return sh_gettid();
    case NR_getpid: return getpid();
    case NR_getppid: return 1;
    case NR_getuid: case NR_geteuid: return 10000;
    case NR_futex: {
        int op = (int)b & 0x7f; /* drop FUTEX_PRIVATE_FLAG and FUTEX_CLOCK_REALTIME */
        if (op == 0 /* FUTEX_WAIT */) return snl_futex_wait((volatile uint32_t *)a, (uint32_t)c,
                                                            (const struct timespec *)d);
        if (op == 1 /* FUTEX_WAKE */) return snl_futex_wake((volatile uint32_t *)a, (int)c);
        LOGW("futex op %d is not supported on the Switch", op);
        return fail(ENOSYS);
    }
    case NR_getrandom: return sh_getrandom((void *)a, (size_t)b, (unsigned)c);
    case NR_clock_gettime: return sh_clock_gettime((int)a, (struct timespec *)b);
    case NR_clock_getres: return sh_clock_getres((int)a, (struct timespec *)b);
    case NR_clock_nanosleep: {
        int r = sh_clock_nanosleep((int)a, (int)b, (const struct timespec *)c, (struct timespec *)d);
        return r ? fail(r) : 0;
    }
    case NR_nanosleep: return nanosleep((const struct timespec *)a, (struct timespec *)b);
    case NR_gettimeofday: return gettimeofday((struct timeval *)a, NULL);
    case NR_sched_yield: sched_yield(); return 0;
    case NR_membarrier: __atomic_thread_fence(__ATOMIC_SEQ_CST); return 0;
    case NR_madvise: case NR_prctl: case NR_sigaltstack: case NR_rt_sigprocmask: return 0;
    case NR_tkill: case NR_tgkill: return 0; /* signals are not delivered (see shim_posix.c) */
    case NR_getcpu:
        if (a) *(unsigned *)a = (unsigned)svcGetCurrentProcessorNumber();
        if (b) *(unsigned *)b = 0;
        return 0;
    case NR_sched_getaffinity:
        if (b < 8) return fail(EINVAL);
        memset((void *)c, 0, (size_t)b);
        *(uint64_t *)c = 0x7; /* the three cores an application gets */
        return 8;
    case NR_read: return read((int)a, (void *)b, (size_t)c);
    case NR_write: return write((int)a, (const void *)b, (size_t)c);
    case NR_close: return close((int)a);
    default: {
        static uint64_t warned[8];
        if (n >= 0 && n < 512 && !(warned[n / 64] & (1ULL << (n % 64)))) {
            warned[n / 64] |= 1ULL << (n % 64);
            LOGW("syscall(%ld) is not available on the Switch", n);
        }
        return fail(ENOSYS);
    }
    }
}

#define W(name, fn) {#name, (void *)fn}

static const ShimSym g_syms[] = {
    W(open, sh_open), W(open64, sh_open), W(__open_2, sh_open_2), W(openat, sh_openat), W(stat, sh_stat),
    W(stat64, sh_stat), W(lstat, sh_stat), W(lstat64, sh_stat), W(fstat, sh_fstat), W(fstat64, sh_fstat),
    W(fstatat, sh_fstatat), W(fstatat64, sh_fstatat), W(readdir, sh_readdir), W(readdir64, sh_readdir),
    W(readdir_r, sh_readdir_r), W(clock_gettime, sh_clock_gettime), W(clock_getres, sh_clock_getres),
    W(clock_nanosleep, sh_clock_nanosleep), W(pthread_once, sh_pthread_once), W(localtime_r, sh_localtime_r),
    W(gmtime_r, sh_gmtime_r), W(localtime, sh_localtime), W(gmtime, sh_gmtime), W(mmap, sh_mmap),
    W(mmap64, sh_mmap), W(munmap, sh_munmap), W(mprotect, sh_mem_ok), W(madvise, sh_mem_ok), W(msync, sh_mem_ok),
    W(mlock, sh_mem_ok2), W(munlock, sh_mem_ok2), W(gettid, sh_gettid), W(getrandom, sh_getrandom),
    W(getentropy, sh_getentropy), W(arc4random, sh_arc4random), W(arc4random_buf, sh_arc4random_buf),
    W(arc4random_uniform, sh_arc4random_uniform), W(syscall, sh_syscall),
};

const ShimSym *shim_newlib_symbols(size_t *n) {
    *n = SA_ARRAY_LEN(g_syms);
    return g_syms;
}

#else

const ShimSym *shim_newlib_symbols(size_t *n) {
    *n = 0;
    return NULL;
}

#endif
