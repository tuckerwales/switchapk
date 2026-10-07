/*
 * ALooper for native code (WS9): the file descriptor and wake-up half of
 * android/looper.h, which android_native_app_glue builds its event loop on.
 *
 * Semantics follow AOSP's Looper (libutils): a looper belongs to the thread
 * that prepared it. pollOnce polls its descriptors, runs the callbacks of the
 * ones that fired (result ALOOPER_POLL_CALLBACK), and returns the ident of
 * the next ready descriptor that has no callback. Descriptors added or
 * removed from another thread take effect on a poll that is already
 * blocking, as they do with epoll; only ALooper_wake makes pollOnce return
 * ALOOPER_POLL_WAKE. Message queues (Looper::sendMessage) are not part of the
 * NDK API and are not implemented.
 *
 * It is plain C over poll(2) and a pipe: no VM objects, so it works from
 * threads the app created. The Switch has no pipe or poll in newlib; there
 * the calls fail (prepare returns NULL, polls return ALOOPER_POLL_ERROR)
 * until the shim gets virtual descriptors (ARCHITECTURE 6.7).
 */
#include "ndk_android.h"
#include "../core/common.h"

#define LOG_TAG "looper"

#ifdef __SWITCH__

ALooper *ALooper_forThread(void) { return NULL; }
ALooper *ALooper_prepare(int opts) {
    SA_UNUSED(opts);
    LOGW("ALooper is not available on the Switch yet (no pipe/poll)");
    return NULL;
}
void ALooper_acquire(ALooper *looper) { SA_UNUSED(looper); }
void ALooper_release(ALooper *looper) { SA_UNUSED(looper); }
int ALooper_pollOnce(int timeoutMillis, int *outFd, int *outEvents, void **outData) {
    SA_UNUSED(timeoutMillis);
    SA_UNUSED(outFd);
    SA_UNUSED(outEvents);
    SA_UNUSED(outData);
    return ALOOPER_POLL_ERROR;
}
int ALooper_pollAll(int timeoutMillis, int *outFd, int *outEvents, void **outData) {
    return ALooper_pollOnce(timeoutMillis, outFd, outEvents, outData);
}
void ALooper_wake(ALooper *looper) { SA_UNUSED(looper); }
int ALooper_addFd(ALooper *looper, int fd, int ident, int events, ALooper_callbackFunc callback, void *data) {
    SA_UNUSED(looper);
    SA_UNUSED(fd);
    SA_UNUSED(ident);
    SA_UNUSED(events);
    SA_UNUSED(callback);
    SA_UNUSED(data);
    return -1;
}
int ALooper_removeFd(ALooper *looper, int fd) {
    SA_UNUSED(looper);
    SA_UNUSED(fd);
    return -1;
}

#else

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#include <stdatomic.h>
#include <unistd.h>

typedef struct {
    int fd;
    int ident;
    int events;
    ALooper_callbackFunc callback;
    void *data;
} Request;

typedef struct {
    Request request;
    int events;
} Response;

struct ALooper {
    pthread_mutex_t lock;
    int wake_fds[2];
    atomic_int wake_requested; /* ALooper_wake was called (as opposed to an fd list change) */
    atomic_int refs;
    bool allow_non_callbacks;
    Request *requests;
    int nrequests, capacity;
    /* owning thread only */
    Response *responses;
    int nresponses, response_index, response_capacity;
};

static _Thread_local ALooper *tl_looper;

static int64_t now_ms(void) { return sa_time_ns() / 1000000; }

static void set_nonblock_cloexec(int fd) {
    fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) | O_NONBLOCK);
    fcntl(fd, F_SETFD, FD_CLOEXEC);
}

static ALooper *looper_create(bool allow_non_callbacks) {
    ALooper *l = sa_calloc(1, sizeof *l);
    pthread_mutex_init(&l->lock, NULL);
    if (pipe(l->wake_fds) != 0) {
        LOGE("cannot create the wake pipe: %s", strerror(errno));
        free(l);
        return NULL;
    }
    set_nonblock_cloexec(l->wake_fds[0]);
    set_nonblock_cloexec(l->wake_fds[1]);
    atomic_init(&l->refs, 1);
    l->allow_non_callbacks = allow_non_callbacks;
    return l;
}

ALooper *ALooper_forThread(void) { return tl_looper; }

ALooper *ALooper_prepare(int opts) {
    if (tl_looper) return tl_looper;
    tl_looper = looper_create((opts & ALOOPER_PREPARE_ALLOW_NON_CALLBACKS) != 0);
    return tl_looper;
}

void ALooper_acquire(ALooper *l) {
    if (l) atomic_fetch_add(&l->refs, 1);
}

void ALooper_release(ALooper *l) {
    if (!l) return;
    if (atomic_fetch_sub(&l->refs, 1) != 1) return;
    if (tl_looper == l) tl_looper = NULL;
    close(l->wake_fds[0]);
    close(l->wake_fds[1]);
    pthread_mutex_destroy(&l->lock);
    free(l->requests);
    free(l->responses);
    free(l);
}

static void wake_fd(ALooper *l) {
    char c = 1;
    ssize_t r;
    do {
        r = write(l->wake_fds[1], &c, 1);
    } while (r < 0 && errno == EINTR);
    /* EAGAIN: the pipe is full, so a wake-up is already pending */
}

void ALooper_wake(ALooper *l) {
    if (!l) return;
    atomic_store(&l->wake_requested, 1);
    wake_fd(l);
}

static void drain_wake(ALooper *l) {
    char buf[64];
    while (read(l->wake_fds[0], buf, sizeof buf) > 0) {
    }
}

int ALooper_addFd(ALooper *l, int fd, int ident, int events, ALooper_callbackFunc callback, void *data) {
    if (!l || fd < 0) return -1;
    if (!callback) {
        if (!l->allow_non_callbacks) {
            LOGE("Invalid attempt to set NULL callback but not allowed for this looper");
            return -1;
        }
        if (ident < 0) {
            LOGE("Invalid attempt to set NULL callback with ident < 0");
            return -1;
        }
    } else {
        ident = ALOOPER_POLL_CALLBACK;
    }
    Request req = {fd, ident, events, callback, data};
    pthread_mutex_lock(&l->lock);
    int i;
    for (i = 0; i < l->nrequests; i++)
        if (l->requests[i].fd == fd) break;
    if (i == l->nrequests) {
        if (l->nrequests == l->capacity) {
            l->capacity = l->capacity ? l->capacity * 2 : 8;
            l->requests = sa_realloc(l->requests, sizeof(Request) * (size_t)l->capacity);
        }
        l->nrequests++;
    }
    l->requests[i] = req;
    pthread_mutex_unlock(&l->lock);
    wake_fd(l); /* a blocked poll must pick the new descriptor up; it is not reported as a wake */
    return 1;
}

int ALooper_removeFd(ALooper *l, int fd) {
    if (!l) return -1;
    int removed = 0;
    pthread_mutex_lock(&l->lock);
    for (int i = 0; i < l->nrequests; i++) {
        if (l->requests[i].fd != fd) continue;
        memmove(&l->requests[i], &l->requests[i + 1], sizeof(Request) * (size_t)(l->nrequests - i - 1));
        l->nrequests--;
        removed = 1;
        break;
    }
    pthread_mutex_unlock(&l->lock);
    if (removed) wake_fd(l);
    /* a response already collected for this fd is dropped, as Looper::removeFd does */
    for (int i = l->response_index; i < l->nresponses; i++)
        if (l->responses[i].request.fd == fd) l->responses[i].request.ident = ALOOPER_POLL_CALLBACK;
    return removed;
}

static short to_poll_events(int events) {
    short e = 0;
    if (events & ALOOPER_EVENT_INPUT) e |= POLLIN;
    if (events & ALOOPER_EVENT_OUTPUT) e |= POLLOUT;
    return e;
}

static int from_poll_events(short revents) {
    int e = 0;
    if (revents & POLLIN) e |= ALOOPER_EVENT_INPUT;
    if (revents & POLLOUT) e |= ALOOPER_EVENT_OUTPUT;
    if (revents & POLLERR) e |= ALOOPER_EVENT_ERROR;
    if (revents & POLLHUP) e |= ALOOPER_EVENT_HANGUP;
    if (revents & POLLNVAL) e |= ALOOPER_EVENT_INVALID;
    return e;
}

static void push_response(ALooper *l, const Request *req, int events) {
    if (l->nresponses == l->response_capacity) {
        l->response_capacity = l->response_capacity ? l->response_capacity * 2 : 8;
        l->responses = sa_realloc(l->responses, sizeof(Response) * (size_t)l->response_capacity);
    }
    l->responses[l->nresponses].request = *req;
    l->responses[l->nresponses].events = events;
    l->nresponses++;
}

/* One poll round: returns WAKE, TIMEOUT, ERROR, CALLBACK (a callback ran) or 0 (a callback-less response is queued). */
static int poll_inner(ALooper *l, int timeout_ms) {
    int64_t deadline = timeout_ms > 0 ? now_ms() + timeout_ms : 0;
    l->nresponses = 0;
    l->response_index = 0;
    for (;;) {
        pthread_mutex_lock(&l->lock);
        int n = l->nrequests;
        struct pollfd *pfds = sa_malloc(sizeof(struct pollfd) * (size_t)(n + 1));
        Request *snapshot = sa_malloc(sizeof(Request) * (size_t)(n + 1));
        pfds[0].fd = l->wake_fds[0];
        pfds[0].events = POLLIN;
        pfds[0].revents = 0;
        for (int i = 0; i < n; i++) {
            snapshot[i] = l->requests[i];
            pfds[i + 1].fd = l->requests[i].fd;
            pfds[i + 1].events = to_poll_events(l->requests[i].events);
            pfds[i + 1].revents = 0;
        }
        pthread_mutex_unlock(&l->lock);

        int wait = timeout_ms < 0 ? -1 : timeout_ms == 0 ? 0 : (int)(deadline - now_ms());
        if (timeout_ms > 0 && wait < 0) wait = 0;
        int r = poll(pfds, (nfds_t)(n + 1), wait);
        int err = errno;

        int result = 0;
        if (r < 0) {
            result = err == EINTR ? ALOOPER_POLL_WAKE : ALOOPER_POLL_ERROR;
        } else if (r == 0) {
            result = ALOOPER_POLL_TIMEOUT;
        } else {
            for (int i = 0; i < n; i++) {
                short rev = pfds[i + 1].revents;
                if (rev) push_response(l, &snapshot[i], from_poll_events(rev));
            }
            if (pfds[0].revents & POLLIN) {
                drain_wake(l);
                if (atomic_exchange(&l->wake_requested, 0)) {
                    result = ALOOPER_POLL_WAKE;
                } else if (l->nresponses == 0) {
                    /* only the fd list changed: poll again with what is left of the timeout */
                    free(pfds);
                    free(snapshot);
                    if (timeout_ms > 0 && now_ms() >= deadline) return ALOOPER_POLL_TIMEOUT;
                    continue;
                }
            }
        }
        free(pfds);
        free(snapshot);

        /* run the callbacks of the responses that have one */
        for (int i = 0; i < l->nresponses; i++) {
            Response *resp = &l->responses[i];
            if (resp->request.ident != ALOOPER_POLL_CALLBACK) continue;
            Request req = resp->request;
            int keep = req.callback ? req.callback(req.fd, resp->events, req.data) : 0;
            if (!keep) ALooper_removeFd(l, req.fd);
            resp->request.ident = ALOOPER_POLL_CALLBACK - 1; /* handled */
            result = ALOOPER_POLL_CALLBACK;
        }
        return result;
    }
}

int ALooper_pollOnce(int timeout_ms, int *out_fd, int *out_events, void **out_data) {
    ALooper *l = tl_looper;
    if (!l) {
        LOGE("ALooper_pollOnce called on a thread without a looper (call ALooper_prepare first)");
        return ALOOPER_POLL_ERROR;
    }
    int result = 0;
    for (;;) {
        while (l->response_index < l->nresponses) {
            Response *resp = &l->responses[l->response_index++];
            int ident = resp->request.ident;
            if (ident >= 0) {
                if (out_fd) *out_fd = resp->request.fd;
                if (out_events) *out_events = resp->events;
                if (out_data) *out_data = resp->request.data;
                return ident;
            }
        }
        if (result != 0) {
            if (out_fd) *out_fd = 0;
            if (out_events) *out_events = 0;
            if (out_data) *out_data = NULL;
            return result;
        }
        result = poll_inner(l, timeout_ms);
    }
}

int ALooper_pollAll(int timeout_ms, int *out_fd, int *out_events, void **out_data) {
    if (timeout_ms <= 0) {
        int r;
        do {
            r = ALooper_pollOnce(timeout_ms, out_fd, out_events, out_data);
        } while (r == ALOOPER_POLL_CALLBACK);
        return r;
    }
    int64_t end = now_ms() + timeout_ms;
    int remaining = timeout_ms;
    for (;;) {
        int r = ALooper_pollOnce(remaining, out_fd, out_events, out_data);
        if (r != ALOOPER_POLL_CALLBACK) return r;
        remaining = (int)(end - now_ms());
        if (remaining <= 0) return ALOOPER_POLL_TIMEOUT;
    }
}

#endif
