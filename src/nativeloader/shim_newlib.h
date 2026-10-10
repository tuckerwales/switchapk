/*
 * bionic (arm64) <-> newlib translations for the Switch shim (shim_newlib.c): open flags, clock ids, struct stat,
 * dirent and tm, plus portable pthread_once and futex implementations. Pure code, built on every target for
 * tests/c/shim_newlib_test.c; the bionic layouts are written out because the host's are x86-64's or glibc's.
 */
#ifndef SWITCHAPK_SHIM_NEWLIB_H
#define SWITCHAPK_SHIM_NEWLIB_H

#include <stdint.h>
#include <time.h>

/* bionic arm64 struct stat (asm-generic), 128 bytes */
typedef struct {
    uint64_t st_dev, st_ino;
    uint32_t st_mode, st_nlink, st_uid, st_gid;
    uint64_t st_rdev, pad1;
    int64_t st_size;
    int32_t st_blksize, pad2;
    int64_t st_blocks;
    int64_t st_atim_sec, st_atim_nsec, st_mtim_sec, st_mtim_nsec, st_ctim_sec, st_ctim_nsec;
    uint32_t unused4, unused5;
} SnlStat;

/* bionic struct dirent (LP64) */
typedef struct {
    uint64_t d_ino;
    int64_t d_off;
    uint16_t d_reclen;
    uint8_t d_type;
    char d_name[256];
} SnlDirent;

/* bionic struct tm: POSIX's nine ints, then tm_gmtoff and tm_zone */
typedef struct {
    int tm_sec, tm_min, tm_hour, tm_mday, tm_mon, tm_year, tm_wday, tm_yday, tm_isdst;
    long tm_gmtoff;
    const char *tm_zone;
} SnlTm;

/* the values a newlib stat call produced, whatever its field types */
typedef struct {
    uint64_t dev, ino, rdev;
    uint32_t mode, nlink, uid, gid;
    int64_t size, blksize, blocks;
    int64_t atime, atime_ns, mtime, mtime_ns, ctime_, ctime_ns;
} SnlStatFields;

/* Linux arm64 open(2) flags to newlib's; -1 for flags newlib cannot express (O_PATH, O_TMPFILE) */
int snl_oflags_from_linux(int flags);
/* Linux clock ids to newlib's; -1 when newlib has no equivalent */
int snl_clock_from_linux(int clk);
void snl_fill_stat(SnlStat *out, const SnlStatFields *in);
void snl_fill_dirent(SnlDirent *out, uint64_t ino, uint8_t type, const char *name, int64_t off);
void snl_tm_to_bionic(SnlTm *out, const int fields[9], long gmtoff, const char *zone);
void snl_tm_from_bionic(int fields[9], const SnlTm *in);

/* pthread_once over bionic's 4-byte pthread_once_t (0 = not run) */
int snl_once(int *once, void (*fn)(void));

/* FUTEX_WAIT / FUTEX_WAKE for one process: 0, or -1 with errno (EAGAIN, ETIMEDOUT, EINVAL) */
int snl_futex_wait(volatile uint32_t *addr, uint32_t expected, const struct timespec *relative);
int snl_futex_wake(volatile uint32_t *addr, int count);

#endif
