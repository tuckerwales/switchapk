/*
 * Checks the Linux <-> FreeBSD/newlib socket translations of src/nativeloader/shim_bsd.c on the host: the Linux side
 * comes from the host's (glibc) headers, the Switch side is the libnx/newlib header values written out.
 *   make && cc -Isrc tests/c/shim_bsd_test.c build/host/src/nativeloader/shim_bsd.o -o build/shim_bsd_test \
 *     && build/shim_bsd_test
 */
#include "nativeloader/shim_bsd.h"

#include <errno.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/select.h>
#include <sys/socket.h>

static int g_fail;

#define EXPECT(cond)                                                                                                  \
    do {                                                                                                              \
        if (!(cond)) {                                                                                                \
            printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);                                                   \
            g_fail = 1;                                                                                               \
        }                                                                                                             \
    } while (0)

static void errnos(void) {
    /* newlib numbers (devkitA64 sys/errno.h) to the host's Linux ones */
    EXPECT(sbsd_errno_to_linux(11) == EAGAIN);
    EXPECT(sbsd_errno_to_linux(119) == EINPROGRESS);
    EXPECT(sbsd_errno_to_linux(120) == EALREADY);
    EXPECT(sbsd_errno_to_linux(116) == ETIMEDOUT);
    EXPECT(sbsd_errno_to_linux(111) == ECONNREFUSED);
    EXPECT(sbsd_errno_to_linux(104) == ECONNRESET);
    EXPECT(sbsd_errno_to_linux(128) == ENOTCONN);
    EXPECT(sbsd_errno_to_linux(88) == ENOSYS);
    EXPECT(sbsd_errno_to_linux(91) == ENAMETOOLONG);
    EXPECT(sbsd_errno_to_linux(0) == 0);
    EXPECT(sbsd_errno_from_linux(EINPROGRESS) == 119);
    EXPECT(sbsd_errno_from_linux(EPIPE) == 32);
    for (int e = 1; e <= 34; e++) EXPECT(sbsd_errno_to_linux(e) == e && sbsd_errno_from_linux(e) == e);
    /* codes both sides have survive the round trip */
    static const int both[] = {EAGAIN, EINPROGRESS, EALREADY, ENOTSOCK, EDESTADDRREQ, EMSGSIZE, EPROTOTYPE,
                               ENOPROTOOPT, EPROTONOSUPPORT, EOPNOTSUPP, EAFNOSUPPORT, EADDRINUSE, EADDRNOTAVAIL,
                               ENETDOWN, ENETUNREACH, ECONNABORTED, ECONNRESET, ENOBUFS, EISCONN, ENOTCONN, ETIMEDOUT,
                               ECONNREFUSED, EHOSTUNREACH, ENOSYS, ENAMETOOLONG, ENOTEMPTY, ELOOP, EOVERFLOW};
    for (size_t i = 0; i < sizeof both / sizeof both[0]; i++)
        EXPECT(sbsd_errno_to_linux(sbsd_errno_from_linux(both[i])) == both[i]);
}

static void addresses(void) {
    EXPECT(sbsd_af_to_bsd(AF_INET) == 2 && sbsd_af_to_bsd(AF_INET6) == 28 && sbsd_af_from_bsd(28) == AF_INET6);
    struct sockaddr_in in;
    memset(&in, 0, sizeof in);
    in.sin_family = AF_INET;
    in.sin_port = htons(43594);
    in.sin_addr.s_addr = htonl(0x5beb8c94);
    unsigned char b[SBSD_SOCKADDR_MAX];
    uint32_t blen = 0;
    EXPECT(sbsd_sockaddr_to_bsd(&in, sizeof in, b, &blen) == 0);
    EXPECT(blen == 16 && b[0] == 16 && b[1] == 2 && memcmp(b + 2, (char *)&in + 2, 14) == 0);
    struct sockaddr_in back;
    uint32_t len = sizeof back;
    sbsd_sockaddr_from_bsd(b, blen, &back, &len);
    EXPECT(len == sizeof back && memcmp(&back, &in, sizeof in) == 0);

    struct sockaddr_in6 in6;
    memset(&in6, 0, sizeof in6);
    in6.sin6_family = AF_INET6;
    in6.sin6_port = htons(443);
    in6.sin6_addr.s6_addr[15] = 1;
    in6.sin6_scope_id = 3;
    EXPECT(sbsd_sockaddr_to_bsd(&in6, sizeof in6, b, &blen) == 0);
    EXPECT(blen == 28 && b[0] == 28 && b[1] == 28);
    /* a short buffer gets a truncated copy and the full length */
    unsigned char small[8];
    len = sizeof small;
    sbsd_sockaddr_from_bsd(b, blen, small, &len);
    EXPECT(len == 28 && (small[0] | small[1] << 8) == AF_INET6);
    EXPECT(sbsd_sockaddr_to_bsd(&in, 1, b, &blen) == -1);
}

static void options(void) {
    EXPECT(sbsd_socktype_to_bsd(SOCK_STREAM) == 1);
    EXPECT(sbsd_socktype_to_bsd(SOCK_DGRAM | SOCK_NONBLOCK | SOCK_CLOEXEC) == (2 | 0x20000000 | 0x10000000));
    int l, o;
    EXPECT(sbsd_sockopt_to_bsd(SOL_SOCKET, SO_REUSEADDR, &l, &o) == 0 && l == 0xffff && o == 0x0004);
    EXPECT(sbsd_sockopt_to_bsd(SOL_SOCKET, SO_ERROR, &l, &o) == 0 && o == 0x1007);
    EXPECT(sbsd_sockopt_to_bsd(SOL_SOCKET, SO_RCVTIMEO, &l, &o) == 0 && o == 0x1006);
    EXPECT(sbsd_sockopt_to_bsd(SOL_SOCKET, SO_SNDBUF, &l, &o) == 0 && o == 0x1001);
    EXPECT(sbsd_sockopt_to_bsd(SOL_SOCKET, SO_KEEPALIVE, &l, &o) == 0 && o == 0x0008);
    EXPECT(sbsd_sockopt_to_bsd(IPPROTO_TCP, TCP_NODELAY, &l, &o) == 0 && l == 6 && o == 1);
    EXPECT(sbsd_sockopt_to_bsd(IPPROTO_TCP, TCP_KEEPIDLE, &l, &o) == 0 && o == 256);
    EXPECT(sbsd_sockopt_to_bsd(IPPROTO_IP, IP_TOS, &l, &o) == 0 && l == 0 && o == 3);
    EXPECT(sbsd_sockopt_to_bsd(IPPROTO_IPV6, IPV6_V6ONLY, &l, &o) == 0 && l == 41 && o == 27);
    EXPECT(sbsd_sockopt_to_bsd(SOL_SOCKET, SO_PRIORITY, &l, &o) == -1);
    EXPECT(sbsd_msgflags_to_bsd(MSG_DONTWAIT | MSG_NOSIGNAL) == (0x80 | 0x20000));
    EXPECT(sbsd_msgflags_to_bsd(MSG_PEEK | MSG_WAITALL) == (0x2 | 0x40));
    EXPECT(sbsd_oflags_to_newlib(O_RDWR | O_NONBLOCK) == (2 | 0x4000));
    EXPECT(sbsd_oflags_from_newlib(0x4000 | 0x0008) == (O_NONBLOCK | O_APPEND));
    EXPECT(sbsd_ioctl_to_bsd(FIONBIO) == 0x8004667eUL && sbsd_ioctl_to_bsd(FIONREAD) == 0x4004667fUL);
    EXPECT(sbsd_ioctl_to_bsd(TIOCGWINSZ) == 0);
}

static void fdsets(void) {
    fd_set s;
    FD_ZERO(&s);
    FD_SET(3, &s);
    FD_SET(700, &s);
    EXPECT(sbsd_fdset_isset(&s, 3) && sbsd_fdset_isset(&s, 700) && !sbsd_fdset_isset(&s, 4));
    fd_set t;
    FD_ZERO(&t);
    sbsd_fdset_set(&t, 65);
    EXPECT(FD_ISSET(65, &t) && !FD_ISSET(64, &t));
}

int main(void) {
    errnos();
    addresses();
    options();
    fdsets();
    puts(g_fail ? "FAIL shim_bsd_test" : "PASS shim_bsd_test");
    return g_fail;
}
