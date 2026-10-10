/*
 * BSD sockets for native libraries on the Switch (WS9/WS11). libnx implements sockets with FreeBSD's ABI under
 * newlib, while bionic code is built against Linux's. The pure translations (sbsd_*) are compiled on every target so
 * tests/c/shim_bsd_test.c checks them on the host; the wrappers that call libnx are Switch-only, and on the host
 * shim_posix.c passes the same names to glibc.
 *
 * What differs and is translated:
 * - errno: newlib numbers errors differently from Linux past ERANGE (EINPROGRESS is 119, not 115). On the Switch
 *   `__errno` hands bionic code a per-thread Linux copy of errno (see sbsd_errno_location);
 * - sockaddr: BSD's starts with a length byte and an 8-bit family; AF_INET6 is 28, not 10;
 * - SOCK_NONBLOCK/SOCK_CLOEXEC, SOL_SOCKET and the SO_*, IP_*, IPV6_* and TCP_KEEP* option numbers, MSG_* flags,
 *   O_NONBLOCK/O_APPEND for fcntl, FIONBIO/FIONREAD for ioctl;
 * - select: bionic's fd_set is 1024 bits; it is answered with poll.
 * struct addrinfo has bionic's field order already (both come from BSD); its addresses are converted. sendmsg,
 * recvmsg and socketpair are not translated (ENOSYS).
 */
#include "nativeloader.h"
#include "shim_bsd.h"

#include <errno.h>
#include <stdint.h>
#include <string.h>

/* ---- errno: newlib -> Linux (entries that differ; generated from both errno.h files) ----------------- */

static const struct {
    int16_t newlib, linux_;
} g_errnos[] = {
    {35, 42} /* ENOMSG */, {36, 43} /* EIDRM */, {37, 44} /* ECHRNG */, {38, 45} /* EL2NSYNC */,
    {39, 46} /* EL3HLT */, {40, 47} /* EL3RST */, {41, 48} /* ELNRNG */, {42, 49} /* EUNATCH */,
    {43, 50} /* ENOCSI */, {44, 51} /* EL2HLT */, {45, 35} /* EDEADLK */, {46, 37} /* ENOLCK */,
    {50, 52} /* EBADE */, {51, 53} /* EBADR */, {52, 54} /* EXFULL */, {53, 55} /* ENOANO */,
    {54, 56} /* EBADRQC */, {55, 57} /* EBADSLT */, {56, 35} /* EDEADLOCK */, {57, 59} /* EBFONT */,
    {74, 72} /* EMULTIHOP */, {76, 73} /* EDOTDOT */, {77, 74} /* EBADMSG */, {80, 76} /* ENOTUNIQ */,
    {81, 77} /* EBADFD */, {82, 78} /* EREMCHG */, {83, 79} /* ELIBACC */, {84, 80} /* ELIBBAD */,
    {85, 81} /* ELIBSCN */, {86, 82} /* ELIBMAX */, {87, 83} /* ELIBEXEC */, {88, 38} /* ENOSYS */,
    {90, 39} /* ENOTEMPTY */, {91, 36} /* ENAMETOOLONG */, {92, 40} /* ELOOP */, {106, 97} /* EAFNOSUPPORT */,
    {107, 91} /* EPROTOTYPE */, {108, 88} /* ENOTSOCK */, {109, 92} /* ENOPROTOOPT */, {110, 108} /* ESHUTDOWN */,
    {112, 98} /* EADDRINUSE */, {113, 103} /* ECONNABORTED */, {114, 101} /* ENETUNREACH */,
    {115, 100} /* ENETDOWN */, {116, 110} /* ETIMEDOUT */, {117, 112} /* EHOSTDOWN */,
    {118, 113} /* EHOSTUNREACH */, {119, 115} /* EINPROGRESS */, {120, 114} /* EALREADY */,
    {121, 89} /* EDESTADDRREQ */, {122, 90} /* EMSGSIZE */, {123, 93} /* EPROTONOSUPPORT */,
    {124, 94} /* ESOCKTNOSUPPORT */, {125, 99} /* EADDRNOTAVAIL */, {126, 102} /* ENETRESET */,
    {127, 106} /* EISCONN */, {128, 107} /* ENOTCONN */, {129, 109} /* ETOOMANYREFS */, {131, 87} /* EUSERS */,
    {132, 122} /* EDQUOT */, {133, 116} /* ESTALE */, {134, 95} /* ENOTSUP */, {135, 123} /* ENOMEDIUM */,
    {138, 84} /* EILSEQ */, {139, 75} /* EOVERFLOW */, {140, 125} /* ECANCELED */, {141, 131} /* ENOTRECOVERABLE */,
    {142, 130} /* EOWNERDEAD */, {143, 86} /* ESTRPIPE */,
};

int sbsd_errno_to_linux(int e) {
    for (size_t i = 0; i < SA_ARRAY_LEN(g_errnos); i++)
        if (g_errnos[i].newlib == e) return g_errnos[i].linux_;
    return e;
}

int sbsd_errno_from_linux(int e) {
    for (size_t i = 0; i < SA_ARRAY_LEN(g_errnos); i++)
        if (g_errnos[i].linux_ == e) return g_errnos[i].newlib;
    return e;
}

/* ---- address families and socket addresses ----------------------------------------------------------------- */

#define L_AF_INET6 10
#define B_AF_INET6 28

int sbsd_af_to_bsd(int af) { return af == L_AF_INET6 ? B_AF_INET6 : af; }
int sbsd_af_from_bsd(int af) { return af == B_AF_INET6 ? L_AF_INET6 : af; }

/* Both layouts are the same size per family: only the first two bytes differ (u16 family vs u8 len + u8 family). */
int sbsd_sockaddr_to_bsd(const void *lin, uint32_t len, void *out, uint32_t *out_len) {
    if (!lin || len < 2 || len > SBSD_SOCKADDR_MAX) return -1;
    const uint8_t *s = lin;
    uint8_t *d = out;
    int family = s[0] | (s[1] << 8);
    memcpy(d, s, len);
    d[0] = (uint8_t)len;
    d[1] = (uint8_t)sbsd_af_to_bsd(family);
    *out_len = len;
    return 0;
}

/* Writes at most *lin_len bytes (truncating, as the kernel does) and sets *lin_len to the address's full length. */
void sbsd_sockaddr_from_bsd(const void *bsd, uint32_t blen, void *lin, uint32_t *lin_len) {
    if (!bsd || !lin || !lin_len) return;
    uint8_t tmp[SBSD_SOCKADDR_MAX];
    if (blen > sizeof tmp) blen = sizeof tmp;
    memcpy(tmp, bsd, blen);
    if (blen >= 2) {
        int family = sbsd_af_from_bsd(tmp[1]);
        tmp[0] = (uint8_t)(family & 0xff);
        tmp[1] = (uint8_t)(family >> 8);
    }
    uint32_t n = blen < *lin_len ? blen : *lin_len;
    memcpy(lin, tmp, n);
    *lin_len = blen;
}

/* ---- socket types, options and flags ------------------------------------------------------------------------- */

#define L_SOCK_NONBLOCK 0x800
#define L_SOCK_CLOEXEC 0x80000
#define B_SOCK_NONBLOCK 0x20000000
#define B_SOCK_CLOEXEC 0x10000000

int sbsd_socktype_to_bsd(int type) {
    int t = type & ~(L_SOCK_NONBLOCK | L_SOCK_CLOEXEC);
    if (type & L_SOCK_NONBLOCK) t |= B_SOCK_NONBLOCK;
    if (type & L_SOCK_CLOEXEC) t |= B_SOCK_CLOEXEC;
    return t;
}

static const struct {
    int16_t level;
    int16_t lin;
    int bsd;
} g_opts[] = {
    /* SOL_SOCKET (Linux 1, BSD 0xffff), asm-generic numbering on both 64-bit ABIs */
    {1, 1, 0x0001} /* SO_DEBUG */, {1, 2, 0x0004} /* SO_REUSEADDR */, {1, 3, 0x1008} /* SO_TYPE */,
    {1, 4, 0x1007} /* SO_ERROR */, {1, 5, 0x0010} /* SO_DONTROUTE */, {1, 6, 0x0020} /* SO_BROADCAST */,
    {1, 7, 0x1001} /* SO_SNDBUF */, {1, 8, 0x1002} /* SO_RCVBUF */, {1, 9, 0x0008} /* SO_KEEPALIVE */,
    {1, 10, 0x0100} /* SO_OOBINLINE */, {1, 13, 0x0080} /* SO_LINGER */, {1, 15, 0x0200} /* SO_REUSEPORT */,
    {1, 18, 0x1004} /* SO_RCVLOWAT */, {1, 19, 0x1003} /* SO_SNDLOWAT */, {1, 20, 0x1006} /* SO_RCVTIMEO */,
    {1, 21, 0x1005} /* SO_SNDTIMEO */, {1, 30, 0x0002} /* SO_ACCEPTCONN */, {1, 38, 0x1016} /* SO_PROTOCOL */,
    /* IPPROTO_IP */
    {0, 1, 3} /* IP_TOS */, {0, 2, 4} /* IP_TTL */, {0, 3, 2} /* IP_HDRINCL */, {0, 32, 9} /* IP_MULTICAST_IF */,
    {0, 33, 10} /* IP_MULTICAST_TTL */, {0, 34, 11} /* IP_MULTICAST_LOOP */, {0, 35, 12} /* IP_ADD_MEMBERSHIP */,
    {0, 36, 13} /* IP_DROP_MEMBERSHIP */,
    /* IPPROTO_TCP */
    {6, 1, 1} /* TCP_NODELAY */, {6, 2, 2} /* TCP_MAXSEG */, {6, 4, 0x100} /* TCP_KEEPIDLE */,
    {6, 5, 0x200} /* TCP_KEEPINTVL */, {6, 6, 0x400} /* TCP_KEEPCNT */,
    /* IPPROTO_IPV6 */
    {41, 16, 4} /* IPV6_UNICAST_HOPS */, {41, 17, 9} /* IPV6_MULTICAST_IF */, {41, 18, 10} /* IPV6_MULTICAST_HOPS */,
    {41, 19, 11} /* IPV6_MULTICAST_LOOP */, {41, 20, 12} /* IPV6_JOIN_GROUP */, {41, 21, 13} /* IPV6_LEAVE_GROUP */,
    {41, 26, 27} /* IPV6_V6ONLY */,
};

int sbsd_sockopt_to_bsd(int level, int opt, int *blevel, int *bopt) {
    for (size_t i = 0; i < SA_ARRAY_LEN(g_opts); i++) {
        if (g_opts[i].level == level && g_opts[i].lin == opt) {
            *blevel = level == 1 ? 0xffff : level;
            *bopt = g_opts[i].bsd;
            return 0;
        }
    }
    return -1;
}

int sbsd_msgflags_to_bsd(int f) {
    static const int map[][2] = {
        {0x1, 0x1} /* OOB */, {0x2, 0x2} /* PEEK */, {0x4, 0x4} /* DONTROUTE */, {0x8, 0x20} /* CTRUNC */,
        {0x20, 0x10} /* TRUNC */, {0x40, 0x80} /* DONTWAIT */, {0x80, 0x8} /* EOR */, {0x100, 0x40} /* WAITALL */,
        {0x4000, 0x20000} /* NOSIGNAL */,
    };
    int out = 0;
    for (size_t i = 0; i < SA_ARRAY_LEN(map); i++)
        if (f & map[i][0]) out |= map[i][1];
    return out;
}

/* fcntl F_GETFL/F_SETFL status flags: only O_NONBLOCK and O_APPEND matter for descriptors here */
#define L_O_APPEND 0x400
#define L_O_NONBLOCK 0x800
#define N_O_APPEND 0x0008
#define N_O_NONBLOCK 0x4000

int sbsd_oflags_to_newlib(int f) {
    return (f & L_O_NONBLOCK ? N_O_NONBLOCK : 0) | (f & L_O_APPEND ? N_O_APPEND : 0) | (f & 3);
}

int sbsd_oflags_from_newlib(int f) {
    return (f & N_O_NONBLOCK ? L_O_NONBLOCK : 0) | (f & N_O_APPEND ? L_O_APPEND : 0) | (f & 3);
}

unsigned long sbsd_ioctl_to_bsd(unsigned long req) {
    if (req == 0x5421) return 0x8004667eUL; /* FIONBIO: _IOW('f', 126, int) */
    if (req == 0x541b) return 0x4004667fUL; /* FIONREAD: _IOR('f', 127, int) */
    return 0;
}

/* bionic's fd_set: 1024 bits in unsigned longs */
int sbsd_fdset_isset(const void *set, int fd) {
    const unsigned long *w = set;
    return set && fd >= 0 && fd < 1024 && ((w[fd / (8 * sizeof(long))] >> (fd % (8 * sizeof(long)))) & 1);
}

void sbsd_fdset_set(void *set, int fd) {
    unsigned long *w = set;
    if (set && fd >= 0 && fd < 1024) w[fd / (8 * sizeof(long))] |= 1UL << (fd % (8 * sizeof(long)));
}

/* ---- the Switch: libnx wrappers ----------------------------------------------------------------------------------- */

#ifdef __SWITCH__

#include <arpa/inet.h>
#include <fcntl.h>
#include <netdb.h>
#include <poll.h>
#include <stdarg.h>
#include <stdlib.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <unistd.h>

#define LOG_TAG "libc"

/* the BSD-side numbers in the tables above, checked against libnx's and newlib's headers */
_Static_assert(AF_INET6 == B_AF_INET6 && SOCK_NONBLOCK == B_SOCK_NONBLOCK && SOCK_CLOEXEC == B_SOCK_CLOEXEC, "af");
_Static_assert(SOL_SOCKET == 0xffff && SO_REUSEADDR == 0x0004 && SO_TYPE == 0x1008 && SO_ERROR == 0x1007 &&
                   SO_KEEPALIVE == 0x0008 && SO_LINGER == 0x0080 && SO_REUSEPORT == 0x0200 && SO_SNDBUF == 0x1001 &&
                   SO_RCVBUF == 0x1002 && SO_RCVTIMEO == 0x1006 && SO_SNDTIMEO == 0x1005 && SO_BROADCAST == 0x0020,
               "SO_*");
_Static_assert(IP_TOS == 3 && IP_TTL == 4 && IP_MULTICAST_TTL == 10 && IP_ADD_MEMBERSHIP == 12 && TCP_KEEPIDLE == 256 &&
                   TCP_KEEPINTVL == 512 && TCP_KEEPCNT == 1024 && IPV6_V6ONLY == 27 && IPV6_JOIN_GROUP == 12,
               "IP/TCP/IPV6 options");
_Static_assert(MSG_DONTWAIT == 0x80 && MSG_NOSIGNAL == 0x20000 && MSG_WAITALL == 0x40 && MSG_TRUNC == 0x10 &&
                   MSG_CTRUNC == 0x20 && MSG_EOR == 0x8,
               "MSG_*");
_Static_assert(O_NONBLOCK == N_O_NONBLOCK && O_APPEND == N_O_APPEND && (uint32_t)FIONBIO == 0x8004667eu &&
                   (uint32_t)FIONREAD == 0x4004667fu,
               "fcntl/ioctl");
_Static_assert(EINPROGRESS == 119 && ETIMEDOUT == 116 && ECONNREFUSED == 111 && ENOSYS == 88, "newlib errno");

/*
 * errno for bionic code: a per-thread Linux-numbered copy of newlib's errno, refreshed by every __errno() call (the
 * errno macro calls it on each access). Whichever side changed since the last call wins: a call that failed in
 * between changed newlib's errno, so its error is newer than a value the app stored (errno = 0 before a call);
 * otherwise the app's store is carried into newlib's errno. One case is ambiguous: the app stores a value and the
 * next failure sets the same newlib errno as before; the app's value is kept.
 */
static _Thread_local int tl_errno, tl_errno_seen, tl_newlib_seen;

int *sbsd_errno_location(void) {
    if (errno == tl_newlib_seen && tl_errno != tl_errno_seen) errno = sbsd_errno_from_linux(tl_errno);
    tl_newlib_seen = errno;
    tl_errno = tl_errno_seen = sbsd_errno_to_linux(errno);
    return &tl_errno;
}

static int fail(int err) {
    errno = err;
    return -1;
}

typedef struct {
    struct sockaddr_storage ss;
    socklen_t len;
} BsdAddr;

static bool to_bsd(const void *addr, socklen_t len, BsdAddr *out) {
    uint32_t n = 0;
    if (sbsd_sockaddr_to_bsd(addr, (uint32_t)len, &out->ss, &n) != 0) return false;
    out->len = (socklen_t)n;
    return true;
}

static void from_bsd(const BsdAddr *in, void *addr, socklen_t *len) {
    if (!addr || !len) return;
    uint32_t n = (uint32_t)*len;
    sbsd_sockaddr_from_bsd(&in->ss, (uint32_t)in->len, addr, &n);
    *len = (socklen_t)n;
}

static int sh_socket(int domain, int type, int protocol) {
    return socket(sbsd_af_to_bsd(domain), sbsd_socktype_to_bsd(type), protocol);
}

static int sh_connect(int fd, const void *addr, socklen_t len) {
    BsdAddr b;
    if (!to_bsd(addr, len, &b)) return fail(EINVAL);
    return connect(fd, (struct sockaddr *)&b.ss, b.len);
}

static int sh_bind(int fd, const void *addr, socklen_t len) {
    BsdAddr b;
    if (!to_bsd(addr, len, &b)) return fail(EINVAL);
    return bind(fd, (struct sockaddr *)&b.ss, b.len);
}

static int sh_accept4(int fd, void *addr, socklen_t *len, int flags) {
    BsdAddr b;
    b.len = sizeof b.ss;
    int r = accept(fd, (struct sockaddr *)&b.ss, &b.len);
    if (r < 0) return r;
    from_bsd(&b, addr, len);
    if (flags & 0x800 /* SOCK_NONBLOCK */) fcntl(r, F_SETFL, fcntl(r, F_GETFL, 0) | O_NONBLOCK);
    return r;
}

static int sh_accept(int fd, void *addr, socklen_t *len) { return sh_accept4(fd, addr, len, 0); }

static int sh_getsockname(int fd, void *addr, socklen_t *len) {
    BsdAddr b;
    b.len = sizeof b.ss;
    int r = getsockname(fd, (struct sockaddr *)&b.ss, &b.len);
    if (r == 0) from_bsd(&b, addr, len);
    return r;
}

static int sh_getpeername(int fd, void *addr, socklen_t *len) {
    BsdAddr b;
    b.len = sizeof b.ss;
    int r = getpeername(fd, (struct sockaddr *)&b.ss, &b.len);
    if (r == 0) from_bsd(&b, addr, len);
    return r;
}

static ssize_t sh_send(int fd, const void *buf, size_t n, int flags) {
    return send(fd, buf, n, sbsd_msgflags_to_bsd(flags));
}

static ssize_t sh_recv(int fd, void *buf, size_t n, int flags) {
    return recv(fd, buf, n, sbsd_msgflags_to_bsd(flags));
}

static ssize_t sh_sendto(int fd, const void *buf, size_t n, int flags, const void *addr, socklen_t len) {
    if (!addr) return sendto(fd, buf, n, sbsd_msgflags_to_bsd(flags), NULL, 0);
    BsdAddr b;
    if (!to_bsd(addr, len, &b)) return fail(EINVAL);
    return sendto(fd, buf, n, sbsd_msgflags_to_bsd(flags), (struct sockaddr *)&b.ss, b.len);
}

static ssize_t sh_recvfrom(int fd, void *buf, size_t n, int flags, void *addr, socklen_t *len) {
    BsdAddr b;
    b.len = sizeof b.ss;
    ssize_t r = recvfrom(fd, buf, n, sbsd_msgflags_to_bsd(flags), addr ? (struct sockaddr *)&b.ss : NULL,
                         addr ? &b.len : NULL);
    if (r >= 0 && addr) from_bsd(&b, addr, len);
    return r;
}

static int sh_setsockopt(int fd, int level, int opt, const void *val, socklen_t len) {
    int bl, bo;
    if (sbsd_sockopt_to_bsd(level, opt, &bl, &bo) != 0) {
        LOGW("setsockopt(level %d, option %d) has no Switch equivalent; ignored", level, opt);
        return 0;
    }
    return setsockopt(fd, bl, bo, val, len);
}

/* SO_ERROR's value needs no change: Horizon's bsd service reports Linux-numbered errors */
static int sh_getsockopt(int fd, int level, int opt, void *val, socklen_t *len) {
    int bl, bo;
    if (sbsd_sockopt_to_bsd(level, opt, &bl, &bo) != 0) return fail(ENOPROTOOPT);
    return getsockopt(fd, bl, bo, val, len);
}

static int sh_fcntl(int fd, int cmd, ...) {
    va_list ap;
    va_start(ap, cmd);
    long arg = va_arg(ap, long);
    va_end(ap);
    if (cmd == F_GETFL) {
        int r = fcntl(fd, F_GETFL, 0);
        return r < 0 ? r : sbsd_oflags_from_newlib(r);
    }
    if (cmd == F_SETFL) return fcntl(fd, F_SETFL, sbsd_oflags_to_newlib((int)arg));
    if (cmd == F_GETFD || cmd == F_SETFD) return 0; /* no exec: close-on-exec has no meaning */
    return fail(EINVAL);
}

static int sh_ioctl(int fd, unsigned long req, ...) {
    va_list ap;
    va_start(ap, req);
    void *arg = va_arg(ap, void *);
    va_end(ap);
    unsigned long b = sbsd_ioctl_to_bsd(req);
    if (!b) return fail(ENOTTY);
    return ioctl(fd, b, arg);
}

/* bionic's fd_set is 1024 bits: answered with poll over the descriptors that are set */
static int sh_select(int nfds, void *rd, void *wr, void *ex, struct timeval *tv) {
    if (nfds < 0 || nfds > 1024) return fail(EINVAL);
    struct pollfd *p = calloc((size_t)(nfds ? nfds : 1), sizeof *p);
    if (!p) return fail(ENOMEM);
    int n = 0;
    for (int fd = 0; fd < nfds; fd++) {
        short ev = 0;
        if (sbsd_fdset_isset(rd, fd)) ev |= POLLIN;
        if (sbsd_fdset_isset(wr, fd)) ev |= POLLOUT;
        if (sbsd_fdset_isset(ex, fd)) ev |= POLLPRI;
        if (!ev) continue;
        p[n].fd = fd;
        p[n].events = ev;
        n++;
    }
    int timeout = tv ? (int)(tv->tv_sec * 1000 + tv->tv_usec / 1000) : -1;
    int r = 0;
    if (n) r = poll(p, (nfds_t)n, timeout);
    else if (timeout > 0) usleep((useconds_t)timeout * 1000); /* select(0, ...) as a sleep */
    if (r < 0) {
        free(p);
        return r;
    }
    size_t words = (size_t)(nfds + 63) / 64 * sizeof(unsigned long);
    if (rd) memset(rd, 0, words);
    if (wr) memset(wr, 0, words);
    if (ex) memset(ex, 0, words);
    int count = 0;
    for (int i = 0; i < n; i++) {
        short re = p[i].revents;
        if (rd && (re & (POLLIN | POLLHUP | POLLERR)) && (p[i].events & POLLIN)) {
            sbsd_fdset_set(rd, p[i].fd);
            count++;
        }
        if (wr && (re & (POLLOUT | POLLERR)) && (p[i].events & POLLOUT)) {
            sbsd_fdset_set(wr, p[i].fd);
            count++;
        }
        if (ex && (re & POLLPRI) && (p[i].events & POLLPRI)) {
            sbsd_fdset_set(ex, p[i].fd);
            count++;
        }
    }
    free(p);
    return count;
}

/* bionic's struct addrinfo has BSD's field order; only the addresses and the family change */
typedef struct ShAddrinfo {
    int ai_flags, ai_family, ai_socktype, ai_protocol;
    socklen_t ai_addrlen;
    char *ai_canonname;
    void *ai_addr;
    struct ShAddrinfo *ai_next;
} ShAddrinfo;

static int sh_getaddrinfo(const char *node, const char *service, const ShAddrinfo *hints, ShAddrinfo **res) {
    struct addrinfo h, *list = NULL;
    if (hints) {
        memset(&h, 0, sizeof h);
        h.ai_flags = hints->ai_flags;
        h.ai_family = sbsd_af_to_bsd(hints->ai_family);
        h.ai_socktype = hints->ai_socktype;
        h.ai_protocol = hints->ai_protocol;
    }
    int r = getaddrinfo(node, service, hints ? &h : NULL, &list);
    LOGD("getaddrinfo(%s, %s): %d", node ? node : "NULL", service ? service : "NULL", r);
    *res = NULL;
    if (r != 0) return r; /* EAI_* numbers are BSD's on both sides */
    ShAddrinfo *head = NULL, **tail = &head;
    for (struct addrinfo *a = list; a; a = a->ai_next) {
        ShAddrinfo *b = calloc(1, sizeof *b + SBSD_SOCKADDR_MAX);
        if (!b) break;
        b->ai_flags = a->ai_flags;
        b->ai_family = sbsd_af_from_bsd(a->ai_family);
        b->ai_socktype = a->ai_socktype;
        b->ai_protocol = a->ai_protocol;
        b->ai_addr = b + 1;
        uint32_t n = SBSD_SOCKADDR_MAX;
        sbsd_sockaddr_from_bsd(a->ai_addr, (uint32_t)a->ai_addrlen, b->ai_addr, &n);
        b->ai_addrlen = (socklen_t)n;
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

/* struct hostent has the same layout; the address type is a family */
static struct hostent *sh_gethostbyname(const char *name) {
    static _Thread_local struct hostent h;
    struct hostent *r = gethostbyname(name);
    if (!r) return NULL;
    h = *r;
    h.h_addrtype = sbsd_af_from_bsd(r->h_addrtype);
    return &h;
}

static const char *sh_inet_ntop(int af, const void *src, char *dst, socklen_t n) {
    return inet_ntop(sbsd_af_to_bsd(af), src, dst, n);
}

static int sh_inet_pton(int af, const char *src, void *dst) { return inet_pton(sbsd_af_to_bsd(af), src, dst); }

static int sh_poll(struct pollfd *fds, nfds_t n, int timeout) { return poll(fds, n, timeout); }

#define S(name) {#name, (void *)name}
#define W(name, fn) {#name, (void *)fn}

static const ShimSym g_syms[] = {
    W(__errno, sbsd_errno_location), W(socket, sh_socket), W(connect, sh_connect), W(bind, sh_bind), S(listen),
    W(accept, sh_accept), W(accept4, sh_accept4), S(shutdown), W(getsockname, sh_getsockname),
    W(getpeername, sh_getpeername), W(send, sh_send), W(recv, sh_recv), W(sendto, sh_sendto),
    W(recvfrom, sh_recvfrom), W(setsockopt, sh_setsockopt), W(getsockopt, sh_getsockopt), W(fcntl, sh_fcntl),
    W(ioctl, sh_ioctl), W(select, sh_select), W(poll, sh_poll), W(getaddrinfo, sh_getaddrinfo),
    W(freeaddrinfo, sh_freeaddrinfo), W(gethostbyname, sh_gethostbyname), W(inet_ntop, sh_inet_ntop),
    W(inet_pton, sh_inet_pton), S(htonl), S(htons), S(ntohl), S(ntohs),
};

#else

static const ShimSym g_syms[1];

#endif

/* Joined after the other tables, so on the Switch these replace shim_posix.c's ENOSYS entries and libc's __errno. */
const ShimSym *shim_bsd_symbols(size_t *n) {
#ifdef __SWITCH__
    *n = SA_ARRAY_LEN(g_syms);
#else
    *n = 0;
#endif
    return g_syms;
}
