/*
 * libcore.io.Net natives: BSD sockets and name resolution for java.net.
 * Sockets are plain int descriptors held by the Java objects. Every call that can wait (resolve, connect, accept,
 * receive, send) releases the GIL. Addresses cross the boundary as 4- or 16-byte arrays in network order.
 * Errors become the java.net exception Android throws for the same errno.
 */
#include "natives.h"

#include <errno.h>
#include <fcntl.h>
#include <netdb.h>
#include <poll.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/ioctl.h>
#include <sys/socket.h>

#define LOG_TAG "net"

#ifndef MSG_NOSIGNAL
#define MSG_NOSIGNAL 0
#endif

/* java.net.SocketOptions ids (the Java side passes these) */
#define OPT_TCP_NODELAY 0x0001
#define OPT_IP_TOS 0x0003
#define OPT_SO_REUSEADDR 0x0004
#define OPT_SO_KEEPALIVE 0x0008
#define OPT_SO_BROADCAST 0x0020
#define OPT_SO_LINGER 0x0080
#define OPT_SO_SNDBUF 0x1001
#define OPT_SO_RCVBUF 0x1002
#define OPT_SO_OOBINLINE 0x1003

static const char *errno_class(int err) {
    switch (err) {
    case ECONNREFUSED:
    case ECONNRESET:
    case ECONNABORTED:
        return err == ECONNREFUSED ? "Ljava/net/ConnectException;" : "Ljava/net/SocketException;";
    case EHOSTUNREACH:
    case ENETUNREACH:
        return "Ljava/net/NoRouteToHostException;";
    case EADDRINUSE:
    case EADDRNOTAVAIL:
        return "Ljava/net/BindException;";
    case ETIMEDOUT:
        return "Ljava/net/SocketTimeoutException;";
    default:
        return "Ljava/net/SocketException;";
    }
}

static void throw_errno(VMThread *t, const char *what, int err) {
    vm_throw_new(t, errno_class(err), "%s failed: %s", what, strerror(err));
}

static bool check_range(VMThread *t, ArrayObject *b, int32_t off, int32_t len) {
    if (!b) {
        vm_throw_npe(t, "buffer");
        return false;
    }
    if (off < 0 || len < 0 || (int64_t)off + len > b->length) {
        vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;", "off=%d len=%d length=%d", off, len, b->length);
        return false;
    }
    return true;
}

/* Fills a sockaddr from a Java address array (4 or 16 bytes, or null for the wildcard of `family`). */
static socklen_t to_sockaddr(ArrayObject *addr, int port, int family, struct sockaddr_storage *ss) {
    memset(ss, 0, sizeof *ss);
    int len = addr ? addr->length : (family == AF_INET6 ? 16 : 4);
    if (len == 16) {
        struct sockaddr_in6 *s6 = (struct sockaddr_in6 *)ss;
        s6->sin6_family = AF_INET6;
        s6->sin6_port = htons((uint16_t)port);
        if (addr) memcpy(&s6->sin6_addr, ARRAY_DATA(addr, uint8_t), 16);
        return sizeof *s6;
    }
    struct sockaddr_in *s4 = (struct sockaddr_in *)ss;
    s4->sin_family = AF_INET;
    s4->sin_port = htons((uint16_t)port);
    if (addr && len == 4) memcpy(&s4->sin_addr, ARRAY_DATA(addr, uint8_t), 4);
    return sizeof *s4;
}

/* Writes a sockaddr into out (16 bytes) and info = {port, address length}. Returns the port. */
static int from_sockaddr(const struct sockaddr_storage *ss, ArrayObject *out, ArrayObject *info) {
    int port = 0, len = 0;
    const void *src = NULL;
    if (ss->ss_family == AF_INET6) {
        const struct sockaddr_in6 *s6 = (const struct sockaddr_in6 *)ss;
        port = ntohs(s6->sin6_port);
        src = &s6->sin6_addr;
        len = 16;
    } else if (ss->ss_family == AF_INET) {
        const struct sockaddr_in *s4 = (const struct sockaddr_in *)ss;
        port = ntohs(s4->sin_port);
        src = &s4->sin_addr;
        len = 4;
    }
    if (out && src && out->length >= len) memcpy(ARRAY_DATA(out, uint8_t), src, (size_t)len);
    if (info && info->length >= 2) {
        ARRAY_DATA(info, int32_t)[0] = port;
        ARRAY_DATA(info, int32_t)[1] = len;
    }
    return port;
}

/*
 * Waits for `events` on fd with the GIL released. Returns 1 when ready, 0 on timeout, -1 with errno set.
 * Polls in short slices: on the Switch (a BSD stack) closing or shutting down a socket from another thread does not
 * wake a poll already waiting on it, but the next poll of the closed descriptor fails at once.
 */
#define WAIT_SLICE_MS 250
static int wait_fd(VMThread *t, int fd, short events, int timeout_ms) {
    struct pollfd p = {.fd = fd, .events = events};
    int r;
    const int64_t end = timeout_ms > 0 ? (int64_t)sa_time_ns() + (int64_t)timeout_ms * 1000000 : 0;
    VM_BLOCKING_BEGIN(t);
    for (;;) {
        int slice = WAIT_SLICE_MS;
        if (end) {
            int64_t left = (end - (int64_t)sa_time_ns() + 999999) / 1000000;
            if (left <= 0) {
                r = 0;
                break;
            }
            if (left < slice) slice = (int)left;
        }
        p.revents = 0;
        r = poll(&p, 1, slice);
        if (r != 0 && !(r < 0 && errno == EINTR)) break;
    }
    VM_BLOCKING_END(t);
    if (r > 0 && (p.revents & POLLNVAL)) {
        errno = EBADF;
        return -1;
    }
    return r;
}

/* static byte[] getaddrinfo(String host): entries of [length byte][address bytes], IPv4 first. */
NATIVE(Net_getaddrinfo) {
    UNUSED_ARGS();
    Object *jhost = A_OBJ(0);
    if (!jhost) {
        vm_throw_npe(t, "host");
        return;
    }
    char *host = vm_string_to_utf8(jhost);
    struct addrinfo hints, *res = NULL;
    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    int rc;
    VM_BLOCKING_BEGIN(t);
    rc = getaddrinfo(host, NULL, &hints, &res);
    VM_BLOCKING_END(t);
    if (rc != 0 || !res) {
        vm_throw_new(t, "Ljava/net/UnknownHostException;", "Unable to resolve host \"%s\": No address associated with hostname",
                     host);
        free(host);
        if (res) freeaddrinfo(res);
        return;
    }
    free(host);
    uint8_t buf[17 * 32];
    int n = 0, count = 0;
    for (int pass = 0; pass < 2; pass++) {
        for (struct addrinfo *ai = res; ai && count < 32; ai = ai->ai_next) {
            const void *src;
            int len;
            if (pass == 0 && ai->ai_family == AF_INET) {
                src = &((struct sockaddr_in *)ai->ai_addr)->sin_addr;
                len = 4;
            } else if (pass == 1 && ai->ai_family == AF_INET6) {
                src = &((struct sockaddr_in6 *)ai->ai_addr)->sin6_addr;
                len = 16;
            } else {
                continue;
            }
            bool dup = false;
            for (int i = 0; i < n;) {
                if (buf[i] == len && !memcmp(buf + i + 1, src, (size_t)len)) dup = true;
                i += 1 + buf[i];
            }
            if (dup) continue;
            buf[n++] = (uint8_t)len;
            memcpy(buf + n, src, (size_t)len);
            n += len;
            count++;
        }
    }
    freeaddrinfo(res);
    ArrayObject *out = vm_alloc_prim_array(t, 'B', n);
    if (!out) return;
    memcpy(ARRAY_DATA(out, uint8_t), buf, (size_t)n);
    R_OBJ(out);
}

/* static String getnameinfo(byte[] addr): reverse lookup, null when there is no name. */
NATIVE(Net_getnameinfo) {
    UNUSED_ARGS();
    ArrayObject *addr = A_ARR(0);
    if (!addr || (addr->length != 4 && addr->length != 16)) {
        R_OBJ(NULL);
        return;
    }
    struct sockaddr_storage ss;
    socklen_t sl = to_sockaddr(addr, 0, AF_INET, &ss);
    char name[NI_MAXHOST];
    int rc;
    VM_BLOCKING_BEGIN(t);
    rc = getnameinfo((struct sockaddr *)&ss, sl, name, sizeof name, NULL, 0, NI_NAMEREQD);
    VM_BLOCKING_END(t);
    R_OBJ(rc == 0 ? vm_new_string_utf8(t, name) : NULL);
}

/* static int socket(boolean ipv6, boolean stream) */
NATIVE(Net_socket) {
    UNUSED_ARGS();
    int fd = socket(A_BOOL(0) ? AF_INET6 : AF_INET, A_BOOL(1) ? SOCK_STREAM : SOCK_DGRAM, 0);
    if (fd < 0) {
        throw_errno(t, "socket", errno);
        return;
    }
#ifdef FD_CLOEXEC
    fcntl(fd, F_SETFD, FD_CLOEXEC);
#endif
    R_INT(fd);
}

/* static void connect(int fd, byte[] addr, int port, int timeoutMs) */
NATIVE(Net_connect) {
    UNUSED_ARGS();
    int fd = A_INT(0), port = A_INT(2), timeout = A_INT(3);
    struct sockaddr_storage ss;
    socklen_t sl = to_sockaddr(A_ARR(1), port, AF_INET, &ss);
    int flags = fcntl(fd, F_GETFL, 0);
    fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    int rc = connect(fd, (struct sockaddr *)&ss, sl);
    int err = rc < 0 ? errno : 0;
    if (rc < 0 && (err == EINPROGRESS || err == EINTR)) {
        int w = wait_fd(t, fd, POLLOUT, timeout);
        if (w == 0) {
            fcntl(fd, F_SETFL, flags);
            vm_throw_new(t, "Ljava/net/SocketTimeoutException;", "connect timed out");
            return;
        }
        if (w < 0) {
            err = errno;
        } else {
            socklen_t el = sizeof err;
            if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &err, &el) < 0) err = errno;
        }
    }
    fcntl(fd, F_SETFL, flags);
    if (err) throw_errno(t, "connect", err);
}

/* static void bind(int fd, byte[] addr, int port) */
NATIVE(Net_bind) {
    UNUSED_ARGS();
    struct sockaddr_storage ss;
    int fd = A_INT(0);
    struct sockaddr_storage self;
    socklen_t selflen = sizeof self;
    int family = getsockname(fd, (struct sockaddr *)&self, &selflen) == 0 ? self.ss_family : AF_INET;
    socklen_t sl = to_sockaddr(A_ARR(1), A_INT(2), family, &ss);
    if (bind(fd, (struct sockaddr *)&ss, sl) < 0) throw_errno(t, "bind", errno);
}

/* static void listen(int fd, int backlog) */
NATIVE(Net_listen) {
    UNUSED_ARGS();
    if (listen(A_INT(0), A_INT(1) > 0 ? A_INT(1) : 50) < 0) throw_errno(t, "listen", errno);
}

/* static int accept(int fd, int timeoutMs, byte[] peer, int[] info) */
NATIVE(Net_accept) {
    UNUSED_ARGS();
    int fd = A_INT(0), timeout = A_INT(1);
    for (;;) {
        int w = wait_fd(t, fd, POLLIN, timeout);
        if (w == 0) {
            vm_throw_new(t, "Ljava/net/SocketTimeoutException;", "Accept timed out");
            return;
        }
        if (w < 0) {
            vm_throw_new(t, "Ljava/net/SocketException;", "Socket closed");
            return;
        }
        struct sockaddr_storage ss;
        socklen_t sl = sizeof ss;
        int flags = fcntl(fd, F_GETFL, 0);
        fcntl(fd, F_SETFL, flags | O_NONBLOCK);
        int c = accept(fd, (struct sockaddr *)&ss, &sl);
        int err = errno;
        fcntl(fd, F_SETFL, flags);
        if (c < 0) {
            if (err == EAGAIN || err == EWOULDBLOCK || err == EINTR || err == ECONNABORTED) continue;
            if (err == EINVAL || err == EBADF) vm_throw_new(t, "Ljava/net/SocketException;", "Socket closed");
            else throw_errno(t, "accept", err);
            return;
        }
        int cflags = fcntl(c, F_GETFL, 0);
        if (cflags & O_NONBLOCK) fcntl(c, F_SETFL, cflags & ~O_NONBLOCK);
#ifdef FD_CLOEXEC
        fcntl(c, F_SETFD, FD_CLOEXEC);
#endif
        from_sockaddr(&ss, A_ARR(2), A_ARR(3));
        R_INT(c);
        return;
    }
}

/* static int recv(int fd, byte[] b, int off, int len, int timeoutMs): -1 at end of stream */
NATIVE(Net_recv) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    ArrayObject *b = A_ARR(1);
    int32_t off = A_INT(2), len = A_INT(3), timeout = A_INT(4);
    if (!check_range(t, b, off, len)) return;
    if (len == 0) {
        R_INT(0);
        return;
    }
    int w = wait_fd(t, fd, POLLIN, timeout);
    if (w == 0) {
        vm_throw_new(t, "Ljava/net/SocketTimeoutException;", "Read timed out");
        return;
    }
    if (w < 0) {
        vm_throw_new(t, "Ljava/net/SocketException;", "Socket closed");
        return;
    }
    ssize_t n;
    VM_BLOCKING_BEGIN(t);
    do {
        n = recv(fd, ARRAY_DATA(b, uint8_t) + off, (size_t)len, 0);
    } while (n < 0 && errno == EINTR);
    VM_BLOCKING_END(t);
    if (n < 0) {
        if (errno == EBADF || errno == ENOTSOCK) vm_throw_new(t, "Ljava/net/SocketException;", "Socket closed");
        else throw_errno(t, "recv", errno);
        return;
    }
    R_INT(n == 0 ? -1 : (int32_t)n);
}

/* static void send(int fd, byte[] b, int off, int len) */
NATIVE(Net_send) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    ArrayObject *b = A_ARR(1);
    int32_t off = A_INT(2), len = A_INT(3);
    if (!check_range(t, b, off, len)) return;
    const uint8_t *src = ARRAY_DATA(b, uint8_t) + off;
    int err = 0;
    VM_BLOCKING_BEGIN(t);
    while (len > 0) {
        ssize_t n = send(fd, src, (size_t)len, MSG_NOSIGNAL);
        if (n < 0) {
            if (errno == EINTR) continue;
            err = errno;
            break;
        }
        src += n;
        len -= (int32_t)n;
    }
    VM_BLOCKING_END(t);
    if (err == EPIPE) vm_throw_new(t, "Ljava/net/SocketException;", "Broken pipe");
    else if (err) throw_errno(t, "send", err);
}

/* static int recvfrom(int fd, byte[] b, int off, int len, int timeoutMs, byte[] peer, int[] info) */
NATIVE(Net_recvfrom) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    ArrayObject *b = A_ARR(1);
    int32_t off = A_INT(2), len = A_INT(3), timeout = A_INT(4);
    if (!check_range(t, b, off, len)) return;
    int w = wait_fd(t, fd, POLLIN, timeout);
    if (w == 0) {
        vm_throw_new(t, "Ljava/net/SocketTimeoutException;", "Receive timed out");
        return;
    }
    if (w < 0) {
        vm_throw_new(t, "Ljava/net/SocketException;", "Socket closed");
        return;
    }
    struct sockaddr_storage ss;
    socklen_t sl = sizeof ss;
    ssize_t n;
    VM_BLOCKING_BEGIN(t);
    do {
        n = recvfrom(fd, ARRAY_DATA(b, uint8_t) + off, (size_t)len, 0, (struct sockaddr *)&ss, &sl);
    } while (n < 0 && errno == EINTR);
    VM_BLOCKING_END(t);
    if (n < 0) {
        throw_errno(t, "recvfrom", errno);
        return;
    }
    from_sockaddr(&ss, A_ARR(5), A_ARR(6));
    R_INT((int32_t)n);
}

/* static void sendto(int fd, byte[] b, int off, int len, byte[] addr, int port) */
NATIVE(Net_sendto) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    ArrayObject *b = A_ARR(1);
    int32_t off = A_INT(2), len = A_INT(3);
    if (!check_range(t, b, off, len)) return;
    struct sockaddr_storage ss;
    socklen_t sl = to_sockaddr(A_ARR(4), A_INT(5), AF_INET, &ss);
    ssize_t n;
    VM_BLOCKING_BEGIN(t);
    do {
        n = sendto(fd, ARRAY_DATA(b, uint8_t) + off, (size_t)len, MSG_NOSIGNAL, (struct sockaddr *)&ss, sl);
    } while (n < 0 && errno == EINTR);
    VM_BLOCKING_END(t);
    if (n < 0) throw_errno(t, "sendto", errno);
}

/* static int getsockname(int fd, byte[] addr, int[] info) / getpeername: return the port */
NATIVE(Net_getsockname) {
    UNUSED_ARGS();
    struct sockaddr_storage ss;
    socklen_t sl = sizeof ss;
    memset(&ss, 0, sizeof ss);
    if (getsockname(A_INT(0), (struct sockaddr *)&ss, &sl) < 0) {
        throw_errno(t, "getsockname", errno);
        return;
    }
    R_INT(from_sockaddr(&ss, A_ARR(1), A_ARR(2)));
}

NATIVE(Net_getpeername) {
    UNUSED_ARGS();
    struct sockaddr_storage ss;
    socklen_t sl = sizeof ss;
    memset(&ss, 0, sizeof ss);
    if (getpeername(A_INT(0), (struct sockaddr *)&ss, &sl) < 0) {
        throw_errno(t, "getpeername", errno);
        return;
    }
    R_INT(from_sockaddr(&ss, A_ARR(1), A_ARR(2)));
}

static bool map_option(int opt, int *level, int *name) {
    switch (opt) {
    case OPT_TCP_NODELAY: *level = IPPROTO_TCP, *name = TCP_NODELAY; return true;
    case OPT_IP_TOS: *level = IPPROTO_IP, *name = IP_TOS; return true;
    case OPT_SO_REUSEADDR: *level = SOL_SOCKET, *name = SO_REUSEADDR; return true;
    case OPT_SO_KEEPALIVE: *level = SOL_SOCKET, *name = SO_KEEPALIVE; return true;
    case OPT_SO_BROADCAST: *level = SOL_SOCKET, *name = SO_BROADCAST; return true;
    case OPT_SO_SNDBUF: *level = SOL_SOCKET, *name = SO_SNDBUF; return true;
    case OPT_SO_RCVBUF: *level = SOL_SOCKET, *name = SO_RCVBUF; return true;
    case OPT_SO_OOBINLINE: *level = SOL_SOCKET, *name = SO_OOBINLINE; return true;
    default: return false;
    }
}

/* static void setOption(int fd, int opt, int value): for SO_LINGER, value < 0 turns lingering off */
NATIVE(Net_setOption) {
    UNUSED_ARGS();
    int fd = A_INT(0), opt = A_INT(1), value = A_INT(2), rc;
    if (opt == OPT_SO_LINGER) {
        struct linger l = {.l_onoff = value >= 0, .l_linger = value >= 0 ? value : 0};
        rc = setsockopt(fd, SOL_SOCKET, SO_LINGER, &l, sizeof l);
    } else {
        int level, name;
        if (!map_option(opt, &level, &name)) {
            vm_throw_new(t, "Ljava/net/SocketException;", "unknown socket option %d", opt);
            return;
        }
        rc = setsockopt(fd, level, name, &value, sizeof value);
    }
    if (rc < 0) throw_errno(t, "setsockopt", errno);
}

/* static int getOption(int fd, int opt) */
NATIVE(Net_getOption) {
    UNUSED_ARGS();
    int fd = A_INT(0), opt = A_INT(1), rc, value = 0;
    if (opt == OPT_SO_LINGER) {
        struct linger l;
        socklen_t sl = sizeof l;
        rc = getsockopt(fd, SOL_SOCKET, SO_LINGER, &l, &sl);
        value = l.l_onoff ? l.l_linger : -1;
    } else {
        int level, name;
        if (!map_option(opt, &level, &name)) {
            vm_throw_new(t, "Ljava/net/SocketException;", "unknown socket option %d", opt);
            return;
        }
        socklen_t sl = sizeof value;
        rc = getsockopt(fd, level, name, &value, &sl);
    }
    if (rc < 0) {
        throw_errno(t, "getsockopt", errno);
        return;
    }
    R_INT(value);
}

/* static int available(int fd) */
NATIVE(Net_available) {
    UNUSED_ARGS();
    int n = 0;
#ifdef FIONREAD
    if (ioctl(A_INT(0), FIONREAD, &n) < 0) n = 0;
#endif
    R_INT(n);
}

/* static void shutdown(int fd, int how): 0 input, 1 output, 2 both; errors are ignored like on close */
NATIVE(Net_shutdown) {
    UNUSED_ARGS();
    int how = A_INT(1);
    shutdown(A_INT(0), how == 0 ? SHUT_RD : how == 1 ? SHUT_WR : SHUT_RDWR);
}

/* static void close(int fd): also wakes threads blocked on fd */
NATIVE(Net_close) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    if (fd < 0) return;
    shutdown(fd, SHUT_RDWR);
    close(fd);
}

/* ---- non-blocking calls for java.nio channels: one system call, never waits ---------------------------- */

static bool would_block(int err) { return err == EAGAIN || err == EWOULDBLOCK || err == EINTR; }

/* static void setNonBlocking(int fd, boolean on) */
NATIVE(Net_setNonBlocking) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) {
        throw_errno(t, "fcntl", errno);
        return;
    }
    flags = A_BOOL(1) ? (flags | O_NONBLOCK) : (flags & ~O_NONBLOCK);
    if (fcntl(fd, F_SETFL, flags) < 0) throw_errno(t, "fcntl", errno);
}

/*
 * static int poll(int[] fds, int[] events, int[] revents, int n, int timeoutMs)
 * events: 1 readable, 2 writable. revents adds 4 for error or hang-up and 8 for a bad descriptor.
 * Waits at most timeoutMs (0: just look), GIL released. Returns the number of ready descriptors.
 */
NATIVE(Net_poll) {
    UNUSED_ARGS();
    ArrayObject *fds = A_ARR(0), *ev = A_ARR(1), *rev = A_ARR(2);
    int n = A_INT(3), timeout = A_INT(4);
    if (!fds || !ev || !rev || n < 0 || n > fds->length || n > ev->length || n > rev->length) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "poll arrays");
        return;
    }
    struct pollfd *p = n ? sa_calloc((size_t)n, sizeof *p) : NULL;
    for (int i = 0; i < n; i++) {
        int e = ARRAY_DATA(ev, int32_t)[i];
        p[i].fd = ARRAY_DATA(fds, int32_t)[i];
        p[i].events = (short)(((e & 1) ? POLLIN : 0) | ((e & 2) ? POLLOUT : 0));
    }
    int r;
    VM_BLOCKING_BEGIN(t);
    if (n == 0) {
        if (timeout > 0) {
            struct timespec ts = {timeout / 1000, (long)(timeout % 1000) * 1000000L};
            nanosleep(&ts, NULL);
        }
        r = 0;
    } else {
        do {
            r = poll(p, (nfds_t)n, timeout);
        } while (r < 0 && errno == EINTR);
    }
    VM_BLOCKING_END(t);
    if (r < 0) {
        int err = errno;
        free(p);
        throw_errno(t, "poll", err);
        return;
    }
    for (int i = 0; i < n; i++) {
        short re = p[i].revents;
        int o = ((re & POLLIN) ? 1 : 0) | ((re & POLLOUT) ? 2 : 0) | ((re & (POLLERR | POLLHUP)) ? 4 : 0)
            | ((re & POLLNVAL) ? 8 : 0);
        ARRAY_DATA(rev, int32_t)[i] = o;
    }
    free(p);
    R_INT(r);
}

/* static boolean connectNow(int fd, byte[] addr, int port): true if connected, false if in progress */
NATIVE(Net_connectNow) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    struct sockaddr_storage ss;
    socklen_t sl = to_sockaddr(A_ARR(1), A_INT(2), AF_INET, &ss);
    int rc = connect(fd, (struct sockaddr *)&ss, sl);
    if (rc == 0) {
        R_BOOL(true);
        return;
    }
    if (errno == EINPROGRESS || errno == EINTR || errno == EALREADY) {
        R_BOOL(false);
        return;
    }
    throw_errno(t, "connect", errno);
}

/* static boolean finishConnectNow(int fd): true once connected, false while in progress */
NATIVE(Net_finishConnectNow) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    struct pollfd p = {.fd = fd, .events = POLLOUT};
    int r = poll(&p, 1, 0);
    if (r == 0) {
        R_BOOL(false);
        return;
    }
    int err = 0;
    socklen_t el = sizeof err;
    if (r < 0) err = errno;
    else if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &err, &el) < 0) err = errno;
    if (err) {
        throw_errno(t, "connect", err);
        return;
    }
    R_BOOL(true);
}

/* static int recvNow(int fd, byte[] b, int off, int len): bytes read, 0 if it would block, -1 at end */
NATIVE(Net_recvNow) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    ArrayObject *b = A_ARR(1);
    int32_t off = A_INT(2), len = A_INT(3);
    if (!check_range(t, b, off, len)) return;
    if (len == 0) {
        R_INT(0);
        return;
    }
    ssize_t n = recv(fd, ARRAY_DATA(b, uint8_t) + off, (size_t)len, MSG_DONTWAIT);
    if (n < 0) {
        if (would_block(errno)) {
            R_INT(0);
            return;
        }
        if (errno == ECONNRESET) {
            vm_throw_new(t, "Ljava/net/SocketException;", "Connection reset");
            return;
        }
        throw_errno(t, "recv", errno);
        return;
    }
    R_INT(n == 0 ? -1 : (int32_t)n);
}

/* static int sendNow(int fd, byte[] b, int off, int len): bytes written, 0 if it would block */
NATIVE(Net_sendNow) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    ArrayObject *b = A_ARR(1);
    int32_t off = A_INT(2), len = A_INT(3);
    if (!check_range(t, b, off, len)) return;
    ssize_t n = send(fd, ARRAY_DATA(b, uint8_t) + off, (size_t)len, MSG_NOSIGNAL | MSG_DONTWAIT);
    if (n < 0) {
        if (would_block(errno)) {
            R_INT(0);
            return;
        }
        if (errno == EPIPE) vm_throw_new(t, "Ljava/io/IOException;", "Broken pipe");
        else throw_errno(t, "send", errno);
        return;
    }
    R_INT((int32_t)n);
}

/* static int acceptNow(int fd, byte[] peer, int[] info): the new (blocking) descriptor, or -1 */
NATIVE(Net_acceptNow) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    struct sockaddr_storage ss;
    socklen_t sl = sizeof ss;
    int flags = fcntl(fd, F_GETFL, 0);
    if (!(flags & O_NONBLOCK)) fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    int c = accept(fd, (struct sockaddr *)&ss, &sl);
    int err = errno;
    if (!(flags & O_NONBLOCK)) fcntl(fd, F_SETFL, flags);
    if (c < 0) {
        if (would_block(err) || err == ECONNABORTED) {
            R_INT(-1);
            return;
        }
        throw_errno(t, "accept", err);
        return;
    }
    int cflags = fcntl(c, F_GETFL, 0);
    if (cflags & O_NONBLOCK) fcntl(c, F_SETFL, cflags & ~O_NONBLOCK);
#ifdef FD_CLOEXEC
    fcntl(c, F_SETFD, FD_CLOEXEC);
#endif
    from_sockaddr(&ss, A_ARR(1), A_ARR(2));
    R_INT(c);
}

/* static int recvfromNow(int fd, byte[] b, int off, int len, byte[] peer, int[] info): length, or -1 */
NATIVE(Net_recvfromNow) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    ArrayObject *b = A_ARR(1);
    int32_t off = A_INT(2), len = A_INT(3);
    if (!check_range(t, b, off, len)) return;
    struct sockaddr_storage ss;
    socklen_t sl = sizeof ss;
    ssize_t n = recvfrom(fd, ARRAY_DATA(b, uint8_t) + off, (size_t)len, MSG_DONTWAIT, (struct sockaddr *)&ss, &sl);
    if (n < 0) {
        if (would_block(errno)) {
            R_INT(-1);
            return;
        }
        throw_errno(t, "recvfrom", errno);
        return;
    }
    from_sockaddr(&ss, A_ARR(4), A_ARR(5));
    R_INT((int32_t)n);
}

/* static int sendtoNow(int fd, byte[] b, int off, int len, byte[] addr, int port): bytes sent, 0 if it would block;
 * addr null sends to the connected peer */
NATIVE(Net_sendtoNow) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    ArrayObject *b = A_ARR(1);
    int32_t off = A_INT(2), len = A_INT(3);
    if (!check_range(t, b, off, len)) return;
    ssize_t n;
    if (A_ARR(4)) {
        struct sockaddr_storage ss;
        socklen_t sl = to_sockaddr(A_ARR(4), A_INT(5), AF_INET, &ss);
        n = sendto(fd, ARRAY_DATA(b, uint8_t) + off, (size_t)len, MSG_NOSIGNAL | MSG_DONTWAIT,
                   (struct sockaddr *)&ss, sl);
    } else {
        n = send(fd, ARRAY_DATA(b, uint8_t) + off, (size_t)len, MSG_NOSIGNAL | MSG_DONTWAIT);
    }
    if (n < 0) {
        if (would_block(errno)) {
            R_INT(0);
            return;
        }
        throw_errno(t, "sendto", errno);
        return;
    }
    R_INT((int32_t)n);
}

/* static void connectDatagram(int fd, byte[] addr, int port): addr null dissolves the association */
NATIVE(Net_connectDatagram) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    struct sockaddr_storage ss;
    socklen_t sl;
    if (A_ARR(1)) {
        sl = to_sockaddr(A_ARR(1), A_INT(2), AF_INET, &ss);
    } else {
        memset(&ss, 0, sizeof ss);
        ss.ss_family = AF_UNSPEC;
        sl = sizeof(struct sockaddr_in);
    }
    if (connect(fd, (struct sockaddr *)&ss, sl) < 0 && A_ARR(1)) throw_errno(t, "connect", errno);
}

static const NativeMethodReg g_regs[] = {
    {"Llibcore/io/Net;", "getaddrinfo", "(Ljava/lang/String;)[B", Net_getaddrinfo},
    {"Llibcore/io/Net;", "getnameinfo", "([B)Ljava/lang/String;", Net_getnameinfo},
    {"Llibcore/io/Net;", "socket", "(ZZ)I", Net_socket},
    {"Llibcore/io/Net;", "connect", "(I[BII)V", Net_connect},
    {"Llibcore/io/Net;", "bind", "(I[BI)V", Net_bind},
    {"Llibcore/io/Net;", "listen", "(II)V", Net_listen},
    {"Llibcore/io/Net;", "accept", "(II[B[I)I", Net_accept},
    {"Llibcore/io/Net;", "recv", "(I[BIII)I", Net_recv},
    {"Llibcore/io/Net;", "send", "(I[BII)V", Net_send},
    {"Llibcore/io/Net;", "recvfrom", "(I[BIII[B[I)I", Net_recvfrom},
    {"Llibcore/io/Net;", "sendto", "(I[BII[BI)V", Net_sendto},
    {"Llibcore/io/Net;", "getsockname", "(I[B[I)I", Net_getsockname},
    {"Llibcore/io/Net;", "getpeername", "(I[B[I)I", Net_getpeername},
    {"Llibcore/io/Net;", "setOption", "(III)V", Net_setOption},
    {"Llibcore/io/Net;", "getOption", "(II)I", Net_getOption},
    {"Llibcore/io/Net;", "available", "(I)I", Net_available},
    {"Llibcore/io/Net;", "shutdown", "(II)V", Net_shutdown},
    {"Llibcore/io/Net;", "close", "(I)V", Net_close},
    {"Llibcore/io/Net;", "setNonBlocking", "(IZ)V", Net_setNonBlocking},
    {"Llibcore/io/Net;", "poll", "([I[I[III)I", Net_poll},
    {"Llibcore/io/Net;", "connectNow", "(I[BI)Z", Net_connectNow},
    {"Llibcore/io/Net;", "finishConnectNow", "(I)Z", Net_finishConnectNow},
    {"Llibcore/io/Net;", "recvNow", "(I[BII)I", Net_recvNow},
    {"Llibcore/io/Net;", "sendNow", "(I[BII)I", Net_sendNow},
    {"Llibcore/io/Net;", "acceptNow", "(I[B[I)I", Net_acceptNow},
    {"Llibcore/io/Net;", "recvfromNow", "(I[BII[B[I)I", Net_recvfromNow},
    {"Llibcore/io/Net;", "sendtoNow", "(I[BII[BI)I", Net_sendtoNow},
    {"Llibcore/io/Net;", "connectDatagram", "(I[BI)V", Net_connectDatagram},
};

void natives_java_net_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
