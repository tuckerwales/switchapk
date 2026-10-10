/*
 * Virtual file descriptors: see vfd.h. One lock and one condition variable cover every pipe; pipes in apps carry a
 * byte per event or wake-up, so contention does not matter. A pipe holds 64 KiB like Linux's default.
 */
#include "vfd.h"

#include <errno.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define PIPE_CAP 65536

typedef struct {
    uint8_t *buf;
    size_t head, len;
    int readers, writers; /* open descriptors for each end */
} Pipe;

typedef struct {
    Pipe *pipe; /* NULL: free slot */
    bool write_end;
    int flags;
} Slot;

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_cond = PTHREAD_COND_INITIALIZER;
static Slot g_slots[VFD_MAX];

static Slot *slot_of(int fd) {
    if (fd < VFD_BASE || fd >= VFD_BASE + VFD_MAX) return NULL;
    Slot *s = &g_slots[fd - VFD_BASE];
    return s->pipe ? s : NULL;
}

bool vfd_is(int fd) { return fd >= VFD_BASE && fd < VFD_BASE + VFD_MAX; }

static int alloc_slot(void) {
    for (int i = 0; i < VFD_MAX; i++)
        if (!g_slots[i].pipe) return i;
    return -1;
}

int vfd_pipe(int fds[2], int flags) {
    Pipe *p = calloc(1, sizeof *p);
    uint8_t *buf = malloc(PIPE_CAP);
    if (!p || !buf) {
        free(p);
        free(buf);
        errno = ENOMEM;
        return -1;
    }
    p->buf = buf;
    p->readers = p->writers = 1;
    pthread_mutex_lock(&g_lock);
    int r = alloc_slot();
    if (r >= 0) g_slots[r].pipe = p; /* reserve before looking for the second */
    int w = r >= 0 ? alloc_slot() : -1;
    if (w < 0) {
        if (r >= 0) g_slots[r].pipe = NULL;
        pthread_mutex_unlock(&g_lock);
        free(buf);
        free(p);
        errno = EMFILE;
        return -1;
    }
    g_slots[r] = (Slot){p, false, flags & VFD_NONBLOCK};
    g_slots[w] = (Slot){p, true, flags & VFD_NONBLOCK};
    pthread_mutex_unlock(&g_lock);
    fds[0] = VFD_BASE + r;
    fds[1] = VFD_BASE + w;
    return 0;
}

ssize_t vfd_read(int fd, void *buf, size_t n) {
    pthread_mutex_lock(&g_lock);
    Slot *s = slot_of(fd);
    if (!s || s->write_end) {
        pthread_mutex_unlock(&g_lock);
        errno = EBADF;
        return -1;
    }
    Pipe *p = s->pipe;
    while (p->len == 0 && p->writers > 0) {
        if (s->flags & VFD_NONBLOCK) {
            pthread_mutex_unlock(&g_lock);
            errno = EAGAIN;
            return -1;
        }
        pthread_cond_wait(&g_cond, &g_lock);
        s = slot_of(fd);
        if (!s || s->pipe != p) { /* closed under us */
            pthread_mutex_unlock(&g_lock);
            errno = EBADF;
            return -1;
        }
    }
    size_t got = 0;
    uint8_t *out = buf;
    while (got < n && p->len > 0) {
        size_t chunk = PIPE_CAP - p->head;
        if (chunk > p->len) chunk = p->len;
        if (chunk > n - got) chunk = n - got;
        memcpy(out + got, p->buf + p->head, chunk);
        p->head = (p->head + chunk) % PIPE_CAP;
        p->len -= chunk;
        got += chunk;
    }
    pthread_cond_broadcast(&g_cond);
    pthread_mutex_unlock(&g_lock);
    return (ssize_t)got; /* 0: every writer closed (end of file) */
}

ssize_t vfd_write(int fd, const void *buf, size_t n) {
    pthread_mutex_lock(&g_lock);
    Slot *s = slot_of(fd);
    if (!s || !s->write_end) {
        pthread_mutex_unlock(&g_lock);
        errno = EBADF;
        return -1;
    }
    Pipe *p = s->pipe;
    const uint8_t *in = buf;
    size_t done = 0;
    while (done < n) {
        if (p->readers == 0) {
            pthread_mutex_unlock(&g_lock);
            if (done) return (ssize_t)done;
            errno = EPIPE;
            return -1;
        }
        size_t space = PIPE_CAP - p->len;
        if (space == 0) {
            if (s->flags & VFD_NONBLOCK) {
                pthread_mutex_unlock(&g_lock);
                if (done) return (ssize_t)done;
                errno = EAGAIN;
                return -1;
            }
            pthread_cond_wait(&g_cond, &g_lock);
            s = slot_of(fd);
            if (!s || s->pipe != p) {
                pthread_mutex_unlock(&g_lock);
                errno = EBADF;
                return -1;
            }
            continue;
        }
        size_t tail = (p->head + p->len) % PIPE_CAP;
        size_t chunk = PIPE_CAP - tail;
        if (chunk > space) chunk = space;
        if (chunk > n - done) chunk = n - done;
        memcpy(p->buf + tail, in + done, chunk);
        p->len += chunk;
        done += chunk;
        pthread_cond_broadcast(&g_cond);
    }
    pthread_mutex_unlock(&g_lock);
    return (ssize_t)done;
}

int vfd_close(int fd) {
    pthread_mutex_lock(&g_lock);
    Slot *s = slot_of(fd);
    if (!s) {
        pthread_mutex_unlock(&g_lock);
        errno = EBADF;
        return -1;
    }
    Pipe *p = s->pipe;
    if (s->write_end) p->writers--;
    else p->readers--;
    s->pipe = NULL;
    bool last = p->readers == 0 && p->writers == 0;
    pthread_cond_broadcast(&g_cond);
    pthread_mutex_unlock(&g_lock);
    if (last) {
        free(p->buf);
        free(p);
    }
    return 0;
}

int vfd_get_flags(int fd) {
    pthread_mutex_lock(&g_lock);
    Slot *s = slot_of(fd);
    int f = s ? s->flags : -1;
    pthread_mutex_unlock(&g_lock);
    if (!s) errno = EBADF;
    return f;
}

int vfd_set_flags(int fd, int flags) {
    pthread_mutex_lock(&g_lock);
    Slot *s = slot_of(fd);
    if (s) s->flags = flags & VFD_NONBLOCK;
    pthread_mutex_unlock(&g_lock);
    if (!s) {
        errno = EBADF;
        return -1;
    }
    return 0;
}

int vfd_readable(int fd) {
    pthread_mutex_lock(&g_lock);
    Slot *s = slot_of(fd);
    int n = s && !s->write_end ? (int)s->pipe->len : -1;
    pthread_mutex_unlock(&g_lock);
    if (n < 0) errno = EBADF;
    return n;
}

/* revents of one virtual descriptor; the lock is held */
static short virtual_revents(const VfdPollfd *pf) {
    Slot *s = slot_of(pf->fd);
    if (!s) return VFD_POLLNVAL;
    Pipe *p = s->pipe;
    short r = 0;
    if (!s->write_end) {
        if (p->len > 0) r |= VFD_POLLIN;
        if (p->writers == 0) r |= VFD_POLLHUP;
    } else {
        if (p->readers == 0) r |= VFD_POLLERR;
        else if (p->len < PIPE_CAP) r |= VFD_POLLOUT;
    }
    return (short)(r & (pf->events | VFD_POLLERR | VFD_POLLHUP | VFD_POLLNVAL));
}

static int64_t now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

int vfd_poll(VfdPollfd *fds, unsigned long n, int timeout_ms, VfdRealPoll real_poll) {
    unsigned long nreal = 0;
    for (unsigned long i = 0; i < n; i++) {
        fds[i].revents = 0;
        if (fds[i].fd >= 0 && !vfd_is(fds[i].fd)) nreal++;
    }
    VfdPollfd *real = NULL;
    unsigned long *real_index = NULL;
    if (nreal) {
        if (!real_poll) {
            errno = EINVAL;
            return -1;
        }
        real = malloc(sizeof *real * nreal);
        real_index = malloc(sizeof *real_index * nreal);
        if (!real || !real_index) {
            free(real);
            free(real_index);
            errno = ENOMEM;
            return -1;
        }
        unsigned long k = 0;
        for (unsigned long i = 0; i < n; i++) {
            if (fds[i].fd >= 0 && !vfd_is(fds[i].fd)) {
                real[k] = fds[i];
                real_index[k++] = i;
            }
        }
    }
    int64_t deadline = timeout_ms > 0 ? now_ms() + timeout_ms : 0;
    int ready = 0;
    for (;;) {
        pthread_mutex_lock(&g_lock);
        ready = 0;
        for (unsigned long i = 0; i < n; i++) {
            if (fds[i].fd < 0 || !vfd_is(fds[i].fd)) continue;
            fds[i].revents = virtual_revents(&fds[i]);
            if (fds[i].revents) ready++;
        }
        int remaining = timeout_ms < 0 ? -1 : 0;
        if (timeout_ms > 0) {
            int64_t left = deadline - now_ms();
            remaining = left > 0 ? (int)left : 0;
        }
        if (!nreal) {
            if (ready || remaining == 0) {
                pthread_mutex_unlock(&g_lock);
                break;
            }
            if (remaining < 0) {
                pthread_cond_wait(&g_cond, &g_lock);
            } else {
                struct timespec ts;
                clock_gettime(CLOCK_REALTIME, &ts);
                ts.tv_sec += remaining / 1000;
                ts.tv_nsec += (long)(remaining % 1000) * 1000000L;
                if (ts.tv_nsec >= 1000000000L) {
                    ts.tv_sec++;
                    ts.tv_nsec -= 1000000000L;
                }
                pthread_cond_timedwait(&g_cond, &g_lock, &ts);
            }
            pthread_mutex_unlock(&g_lock);
            continue;
        }
        pthread_mutex_unlock(&g_lock);
        /* real descriptors cannot signal our condition variable: poll them in slices */
        int slice = ready || remaining == 0 ? 0 : remaining < 0 || remaining > 10 ? 10 : remaining;
        int r = real_poll(real, nreal, slice);
        if (r < 0) {
            free(real);
            free(real_index);
            return -1;
        }
        for (unsigned long k = 0; k < nreal; k++) {
            fds[real_index[k]].revents = real[k].revents;
            if (real[k].revents) ready++;
        }
        if (ready || remaining == 0 || (timeout_ms > 0 && now_ms() >= deadline)) break;
    }
    free(real);
    free(real_index);
    return ready;
}
