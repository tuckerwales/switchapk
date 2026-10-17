/*
 * Checks bionic libc shims (shim_runtime.c, shim_libc.c) through shim_lookup: sem_* (counting, trywait,
 * timedwait timeouts, a cross-thread post) and fortified sprintf with an unknown object size.
 *   make && cc -Isrc -pthread tests/c/shim_sem_test.c $(find build/host -name '*.o' ! -name 'main_host.o') \
 *     -lmbedtls -lmbedx509 -lmbedcrypto -lz -lm -ldl -o build/shim_sem_test \
 *     && build/shim_sem_test
 */
#include "nativeloader/nativeloader.h"

#include <errno.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

static int g_fail;

#define EXPECT(cond)                                                                                                  \
    do {                                                                                                              \
        if (!(cond)) {                                                                                                \
            printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);                                                   \
            g_fail = 1;                                                                                               \
        }                                                                                                             \
    } while (0)

typedef struct {
    uint32_t words[4]; /* bionic LP64 sem_t */
} BionicSem;

static int (*p_init)(void *, int, unsigned);
static int (*p_destroy)(void *);
static int (*p_post)(void *);
static int (*p_wait)(void *);
static int (*p_trywait)(void *);
static int (*p_timedwait)(void *, const struct timespec *);
static int (*p_getvalue)(void *, int *);

static BionicSem g_sem;

static void *poster(void *arg) {
    (void)arg;
    usleep(20000);
    p_post(&g_sem);
    return NULL;
}

int main(void) {
    p_init = shim_lookup("sem_init");
    p_destroy = shim_lookup("sem_destroy");
    p_post = shim_lookup("sem_post");
    p_wait = shim_lookup("sem_wait");
    p_trywait = shim_lookup("sem_trywait");
    p_timedwait = shim_lookup("sem_timedwait");
    p_getvalue = shim_lookup("sem_getvalue");
    EXPECT(p_init && p_destroy && p_post && p_wait && p_trywait && p_timedwait && p_getvalue);
    if (g_fail) return 1;

    BionicSem s;
    int v = -1;
    EXPECT(p_init(&s, 0, 2) == 0);
    EXPECT(p_getvalue(&s, &v) == 0 && v == 2);
    EXPECT(p_wait(&s) == 0);
    EXPECT(p_trywait(&s) == 0);
    errno = 0;
    EXPECT(p_trywait(&s) == -1 && errno == EAGAIN);
    EXPECT(p_post(&s) == 0);
    EXPECT(p_getvalue(&s, &v) == 0 && v == 1);
    EXPECT(p_wait(&s) == 0);

    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    ts.tv_nsec += 10000000;
    if (ts.tv_nsec >= 1000000000L) {
        ts.tv_sec++;
        ts.tv_nsec -= 1000000000L;
    }
    errno = 0;
    EXPECT(p_timedwait(&s, &ts) == -1 && errno == ETIMEDOUT);
    EXPECT(p_destroy(&s) == 0);

    /* a semaphore at count 0 woken from another thread */
    EXPECT(p_init(&g_sem, 0, 0) == 0);
    pthread_t t;
    pthread_create(&t, NULL, poster, NULL);
    EXPECT(p_wait(&g_sem) == 0);
    pthread_join(t, NULL);
    EXPECT(p_getvalue(&g_sem, &v) == 0 && v == 0);
    EXPECT(p_destroy(&g_sem) == 0);

    /* __sprintf_chk(buf, 0, (size_t)-1, ...): what fortified code passes when the size is unknown */
    int (*p_sprintf_chk)(char *, int, size_t, const char *, ...) = shim_lookup("__sprintf_chk");
    int (*p_snprintf_chk)(char *, size_t, int, size_t, const char *, ...) = shim_lookup("__snprintf_chk");
    char buf[32];
    EXPECT(p_sprintf_chk && p_sprintf_chk(buf, 0, (size_t)-1, "%d-%s", 4242, "ab") == 7 && strcmp(buf, "4242-ab") == 0);
    EXPECT(p_snprintf_chk && p_snprintf_chk(buf, 5, 0, sizeof buf, "%d", 123456) == 6 && strcmp(buf, "1234") == 0);

    if (!g_fail) printf("PASS shim_sem_test\n");
    return g_fail;
}
