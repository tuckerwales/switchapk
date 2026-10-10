/*
 * Checks the virtual pipes of src/nativeloader/vfd.c on the host, including poll across a virtual pipe and a real
 * one (standing in for a socket).
 *   make && cc -Isrc -pthread tests/c/vfd_test.c build/host/src/nativeloader/vfd.o -o build/vfd_test \
 *     && build/vfd_test
 */
#include "nativeloader/vfd.h"

#include <errno.h>
#include <poll.h>
#include <pthread.h>
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

static int real_poll(VfdPollfd *fds, unsigned long n, int timeout) {
    return poll((struct pollfd *)fds, (nfds_t)n, timeout);
}

static long ms_since(const struct timespec *t0) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return (t.tv_sec - t0->tv_sec) * 1000 + (t.tv_nsec - t0->tv_nsec) / 1000000;
}

static int g_wfd;

static void *late_writer(void *arg) {
    (void)arg;
    usleep(50 * 1000);
    vfd_write(g_wfd, "x", 1);
    return NULL;
}

static void *late_closer(void *arg) {
    (void)arg;
    usleep(50 * 1000);
    vfd_close(g_wfd);
    return NULL;
}

int main(void) {
    _Static_assert(sizeof(VfdPollfd) == sizeof(struct pollfd), "pollfd layout");
    EXPECT(VFD_POLLIN == POLLIN && VFD_POLLOUT == POLLOUT && VFD_POLLHUP == POLLHUP && VFD_POLLNVAL == POLLNVAL);

    int p[2];
    EXPECT(vfd_pipe(p, VFD_NONBLOCK) == 0);
    EXPECT(vfd_is(p[0]) && vfd_is(p[1]) && !vfd_is(3));
    char buf[16];
    EXPECT(vfd_read(p[0], buf, sizeof buf) == -1 && errno == EAGAIN);
    EXPECT(vfd_write(p[1], "hello", 5) == 5);
    EXPECT(vfd_readable(p[0]) == 5);
    EXPECT(vfd_read(p[0], buf, 3) == 3 && memcmp(buf, "hel", 3) == 0);
    EXPECT(vfd_read(p[0], buf, sizeof buf) == 2 && memcmp(buf, "lo", 2) == 0);
    EXPECT(vfd_read(p[1], buf, 1) == -1 && errno == EBADF);
    EXPECT(vfd_write(p[0], "x", 1) == -1 && errno == EBADF);

    /* the ring buffer wraps; a full pipe is EAGAIN for a non-blocking writer */
    static char big[70000];
    memset(big, 'a', sizeof big);
    EXPECT(vfd_write(p[1], big, sizeof big) == 65536);
    EXPECT(vfd_write(p[1], "x", 1) == -1 && errno == EAGAIN);
    static char back[70000];
    EXPECT(vfd_read(p[0], back, 1000) == 1000);
    EXPECT(vfd_write(p[1], "bcd", 3) == 3);
    EXPECT(vfd_read(p[0], back, sizeof back) == 64539 && back[64535] == 'a' && memcmp(back + 64536, "bcd", 3) == 0);

    /* poll: nothing ready times out; a write from another thread wakes a blocking poll */
    VfdPollfd pf[2] = {{p[0], VFD_POLLIN, 0}, {p[1], VFD_POLLOUT, 0}};
    EXPECT(vfd_poll(pf, 2, 0, NULL) == 1 && pf[0].revents == 0 && pf[1].revents == VFD_POLLOUT);
    struct timespec t0;
    clock_gettime(CLOCK_MONOTONIC, &t0);
    EXPECT(vfd_poll(pf, 1, 30, NULL) == 0);
    EXPECT(ms_since(&t0) >= 25);
    g_wfd = p[1];
    pthread_t th;
    pthread_create(&th, NULL, late_writer, NULL);
    clock_gettime(CLOCK_MONOTONIC, &t0);
    EXPECT(vfd_poll(pf, 1, -1, NULL) == 1 && pf[0].revents == VFD_POLLIN);
    EXPECT(ms_since(&t0) < 1000);
    pthread_join(th, NULL);
    EXPECT(vfd_read(p[0], buf, sizeof buf) == 1);

    /* mixed with a real descriptor: either side wakes the wait */
    int rp[2];
    EXPECT(pipe(rp) == 0);
    VfdPollfd mix[2] = {{p[0], VFD_POLLIN, 0}, {rp[0], POLLIN, 0}};
    EXPECT(write(rp[1], "r", 1) == 1);
    EXPECT(vfd_poll(mix, 2, 1000, real_poll) == 1 && mix[0].revents == 0 && mix[1].revents == POLLIN);
    EXPECT(read(rp[0], buf, 1) == 1);
    pthread_create(&th, NULL, late_writer, NULL);
    EXPECT(vfd_poll(mix, 2, 2000, real_poll) == 1 && mix[0].revents == VFD_POLLIN && mix[1].revents == 0);
    pthread_join(th, NULL);
    EXPECT(vfd_read(p[0], buf, sizeof buf) == 1);
    clock_gettime(CLOCK_MONOTONIC, &t0);
    EXPECT(vfd_poll(mix, 2, 40, real_poll) == 0 && ms_since(&t0) >= 35);

    /* closing the write end: a blocked reader sees end of file, poll sees HUP */
    EXPECT(vfd_set_flags(p[0], 0) == 0 && vfd_get_flags(p[0]) == 0);
    pthread_create(&th, NULL, late_closer, NULL);
    EXPECT(vfd_read(p[0], buf, sizeof buf) == 0);
    pthread_join(th, NULL);
    EXPECT(vfd_poll(pf, 1, 0, NULL) == 1 && (pf[0].revents & VFD_POLLHUP));
    EXPECT(vfd_close(p[0]) == 0 && vfd_close(p[0]) == -1 && errno == EBADF);
    pf[0].fd = p[0];
    EXPECT(vfd_poll(pf, 1, 0, NULL) == 1 && pf[0].revents == VFD_POLLNVAL);

    /* closing the read end: writes fail with EPIPE */
    int q[2];
    EXPECT(vfd_pipe(q, 0) == 0);
    EXPECT(vfd_close(q[0]) == 0);
    EXPECT(vfd_write(q[1], "x", 1) == -1 && errno == EPIPE);
    VfdPollfd w = {q[1], VFD_POLLOUT, 0};
    EXPECT(vfd_poll(&w, 1, 0, NULL) == 1 && w.revents == VFD_POLLERR);
    EXPECT(vfd_close(q[1]) == 0);

    /* every slot can be used and slots are reused */
    int all[VFD_MAX];
    int made = 0;
    while (made + 2 <= VFD_MAX && vfd_pipe(all + made, 0) == 0) made += 2;
    EXPECT(made == VFD_MAX);
    int extra[2];
    EXPECT(vfd_pipe(extra, 0) == -1 && errno == EMFILE);
    for (int i = 0; i < made; i++) vfd_close(all[i]);
    EXPECT(vfd_pipe(extra, 0) == 0);

    puts(g_fail ? "FAIL vfd_test" : "PASS vfd_test");
    return g_fail;
}
