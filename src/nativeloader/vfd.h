/*
 * Virtual file descriptors (WS9): in-process pipes for targets whose C library has none (newlib on the Switch).
 * ALooper, AInputQueue and native code's pipe()/pipe2() use them there; poll and select wait on them together with
 * real descriptors (sockets). Portable C over pthreads, so tests/c/vfd_test.c checks it on the host.
 */
#ifndef SWITCHAPK_VFD_H
#define SWITCHAPK_VFD_H

#include <stdbool.h>
#include <stddef.h>
#include <sys/types.h>

/* Virtual descriptors are numbered from here, far above newlib's and libnx's socket descriptors. */
#define VFD_BASE 0x30000000
#define VFD_MAX 1024

#define VFD_NONBLOCK 1

/* struct pollfd's layout; the event bits are POSIX's (the same in Linux, BSD and newlib) */
typedef struct {
    int fd;
    short events;
    short revents;
} VfdPollfd;

#define VFD_POLLIN 0x001
#define VFD_POLLPRI 0x002
#define VFD_POLLOUT 0x004
#define VFD_POLLERR 0x008
#define VFD_POLLHUP 0x010
#define VFD_POLLNVAL 0x020

bool vfd_is(int fd);
/* 0, or -1 with errno (EMFILE) */
int vfd_pipe(int fds[2], int flags);
ssize_t vfd_read(int fd, void *buf, size_t n);
/* EPIPE when the read end is closed (no SIGPIPE) */
ssize_t vfd_write(int fd, const void *buf, size_t n);
int vfd_close(int fd);
/* the descriptor's VFD_NONBLOCK flag, or -1 with EBADF */
int vfd_get_flags(int fd);
int vfd_set_flags(int fd, int flags);
/* bytes ready to read (FIONREAD), or -1 with EBADF */
int vfd_readable(int fd);

/*
 * poll over virtual and real descriptors. real_poll (may be NULL when there are none) polls the real ones with the
 * same struct layout. With real descriptors present the wait is sliced (10 ms) so neither side starves.
 */
typedef int (*VfdRealPoll)(VfdPollfd *fds, unsigned long n, int timeout_ms);
int vfd_poll(VfdPollfd *fds, unsigned long n, int timeout_ms, VfdRealPoll real_poll);

#endif
