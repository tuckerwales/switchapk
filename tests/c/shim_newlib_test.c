/*
 * Checks the bionic <-> newlib translations and the futex and once implementations of src/nativeloader/shim_newlib.c
 * on the host. The newlib side is devkitA64's header values written out (shim_newlib.c static-asserts them there).
 *   make && cc -Isrc -pthread tests/c/shim_newlib_test.c build/host/src/nativeloader/shim_newlib.o -o \
 *     build/shim_newlib_test && build/shim_newlib_test
 */
#include "nativeloader/shim_newlib.h"

#include <errno.h>
#include <pthread.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

static int g_fail;

#define EXPECT(cond)                                                                                                  \
    do {                                                                                                              \
        if (!(cond)) {                                                                                                \
            printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);                                                   \
            g_fail = 1;                                                                                               \
        }                                                                                                             \
    } while (0)

static int g_once_runs;
static void once_fn(void) {
    usleep(20000);
    __atomic_fetch_add(&g_once_runs, 1, __ATOMIC_SEQ_CST);
}
static int g_once;
static void *once_thread(void *arg) {
    (void)arg;
    snl_once(&g_once, once_fn);
    return (void *)(long)__atomic_load_n(&g_once_runs, __ATOMIC_SEQ_CST);
}

static volatile uint32_t g_word;
static void *waker(void *arg) {
    (void)arg;
    usleep(30000);
    __atomic_store_n(&g_word, 1, __ATOMIC_SEQ_CST);
    snl_futex_wake(&g_word, 1);
    return NULL;
}

int main(void) {
    /* bionic arm64 layouts */
    EXPECT(sizeof(SnlStat) == 128 && offsetof(SnlStat, st_size) == 48 && offsetof(SnlStat, st_blksize) == 56);
    EXPECT(offsetof(SnlStat, st_atim_sec) == 72 && offsetof(SnlStat, st_ctim_nsec) == 112);
    EXPECT(offsetof(SnlDirent, d_reclen) == 16 && offsetof(SnlDirent, d_type) == 18 && offsetof(SnlDirent, d_name) == 19);
    EXPECT(sizeof(SnlTm) == 56 && offsetof(SnlTm, tm_gmtoff) == 40 && offsetof(SnlTm, tm_zone) == 48);

    /* Linux arm64 open flags to newlib's */
    EXPECT(snl_oflags_from_linux(0) == 0 && snl_oflags_from_linux(2) == 2);
    EXPECT(snl_oflags_from_linux(1 | 0x40 | 0x200) == (1 | 0x0200 | 0x0400));   /* O_WRONLY|O_CREAT|O_TRUNC */
    EXPECT(snl_oflags_from_linux(2 | 0x400 | 0x800) == (2 | 0x0008 | 0x4000));  /* O_RDWR|O_APPEND|O_NONBLOCK */
    EXPECT(snl_oflags_from_linux(0x80 | 0x40) == (0x0800 | 0x0200));            /* O_EXCL|O_CREAT */
    EXPECT(snl_oflags_from_linux(0x4000 | 0x80000) == (0x200000 | 0x40000));    /* O_DIRECTORY|O_CLOEXEC */
    EXPECT(snl_oflags_from_linux(0x20000) == 0);                                /* O_LARGEFILE */
    EXPECT(snl_oflags_from_linux(0x200000) == -1);                              /* O_PATH */

    /* clock ids */
    EXPECT(snl_clock_from_linux(0) == 1 && snl_clock_from_linux(1) == 4 && snl_clock_from_linux(7) == 4);
    EXPECT(snl_clock_from_linux(2) == 2 && snl_clock_from_linux(3) == 3 && snl_clock_from_linux(42) == -1);

    /* struct fills */
    SnlStatFields f = {.dev = 1, .ino = 2, .rdev = 3, .mode = 0100644, .nlink = 1, .uid = 10000, .gid = 10000,
                       .size = 1234567890123LL, .blksize = 4096, .blocks = 8, .atime = 100, .atime_ns = 5,
                       .mtime = 200, .mtime_ns = 6, .ctime_ = 300, .ctime_ns = 7};
    SnlStat st;
    memset(&st, 0xff, sizeof st);
    snl_fill_stat(&st, &f);
    EXPECT(st.st_mode == 0100644 && st.st_size == 1234567890123LL && st.st_blksize == 4096 && st.pad1 == 0);
    EXPECT(st.st_mtim_sec == 200 && st.st_mtim_nsec == 6 && st.st_ctim_sec == 300 && st.unused5 == 0);
    SnlDirent d;
    snl_fill_dirent(&d, 0, 4, "saves", 3);
    EXPECT(d.d_ino == 1 && d.d_type == 4 && strcmp(d.d_name, "saves") == 0 && d.d_off == 3);
    EXPECT(d.d_reclen == 32 && d.d_reclen % 8 == 0);
    SnlTm tm;
    int fields[9] = {1, 2, 3, 4, 5, 126, 6, 7, 0}, back[9];
    snl_tm_to_bionic(&tm, fields, 3600, "BST");
    EXPECT(tm.tm_year == 126 && tm.tm_gmtoff == 3600 && strcmp(tm.tm_zone, "BST") == 0);
    snl_tm_from_bionic(back, &tm);
    EXPECT(memcmp(back, fields, sizeof fields) == 0);

    /* futex: value mismatch, timeout, wake */
    g_word = 0;
    EXPECT(snl_futex_wait(&g_word, 5, NULL) == -1 && errno == EAGAIN);
    struct timespec rel = {0, 20 * 1000000};
    EXPECT(snl_futex_wait(&g_word, 0, &rel) == -1 && errno == ETIMEDOUT);
    EXPECT(snl_futex_wake(&g_word, 1) == 0);
    pthread_t th;
    pthread_create(&th, NULL, waker, NULL);
    while (__atomic_load_n(&g_word, __ATOMIC_SEQ_CST) == 0) snl_futex_wait(&g_word, 0, NULL);
    pthread_join(th, NULL);
    EXPECT(g_word == 1);

    /* once: one run, everyone returns after it */
    pthread_t ts[8];
    for (int i = 0; i < 8; i++) pthread_create(&ts[i], NULL, once_thread, NULL);
    for (int i = 0; i < 8; i++) {
        void *r;
        pthread_join(ts[i], &r);
        EXPECT((long)r == 1);
    }
    EXPECT(g_once_runs == 1 && g_once == 2);
    snl_once(&g_once, once_fn);
    EXPECT(g_once_runs == 1);

    puts(g_fail ? "FAIL shim_newlib_test" : "PASS shim_newlib_test");
    return g_fail;
}
