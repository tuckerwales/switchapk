/*
 * libcore.ssl.Tls natives: TLS 1.2 client sessions over mbedtls 2.28 (WS11).
 *
 * A session never touches a socket. Its record layer reads and writes two memory buffers: Java feeds the bytes it
 * read from the transport (nFeed) and sends whatever the session produced (nPending/nDrain). That keeps socket I/O,
 * timeouts and layered sockets on the Java side, and the same session can serve SSLEngine later.
 *
 * Certificates are checked by Java TrustManagers, as on Android: the handshake runs with MBEDTLS_SSL_VERIFY_NONE and
 * stops (TLS_NEED_VERIFY) right after the server's Certificate message, before the client sends its key exchange.
 * Java checks the chain and calls nVerified, or sends an alert and gives up. mbedtls still checks the server's key
 * exchange signature against the certificate's key. The default TrustManager verifies chains with nVerifyChain
 * against anchors built once by nAnchors.
 *
 * Every call on a session is serialized by its Java owner. Calls that compute (handshake steps, record crypto, chain
 * verification) release the GIL; Java arrays stay put while released (the heap does not move objects).
 */
#include "natives.h"

#include "../platform/platform.h"

#include <mbedtls/ctr_drbg.h>
#include <mbedtls/error.h>
#include <mbedtls/oid.h>
#include <mbedtls/ssl.h>
#include <mbedtls/x509_crt.h>

#define LOG_TAG "ssl"

/* nHandshake / nRead results besides byte counts (match libcore.ssl.Tls) */
#define TLS_EOF (-1)
#define TLS_WANT_READ (-2)
#define TLS_NEED_VERIFY (-3)

typedef struct {
    uint8_t *data;
    size_t start, len, cap;
} Buf;

typedef struct {
    mbedtls_ssl_context ssl;
    mbedtls_ssl_config conf;
    mbedtls_ctr_drbg_context drbg;
    Buf in, out;
    char **alpn; /* NULL-terminated, kept for the config's lifetime */
    int *suites; /* 0-terminated ciphersuite ids, likewise */
    bool verified;
} Tls;

static void buf_append(Buf *b, const uint8_t *p, size_t n) {
    if (b->start > 0 && b->start + b->len + n > b->cap) {
        memmove(b->data, b->data + b->start, b->len);
        b->start = 0;
    }
    if (b->len + n > b->cap) {
        size_t cap = b->cap ? b->cap : 16384;
        while (cap < b->len + n) cap *= 2;
        b->data = sa_realloc(b->data, cap);
        b->cap = cap;
    }
    memcpy(b->data + b->start + b->len, p, n);
    b->len += n;
}

static size_t buf_take(Buf *b, uint8_t *p, size_t n) {
    if (n > b->len) n = b->len;
    memcpy(p, b->data + b->start, n);
    b->start += n;
    b->len -= n;
    if (b->len == 0) b->start = 0;
    return n;
}

static int bio_send(void *ctx, const unsigned char *p, size_t n) {
    buf_append(&((Tls *)ctx)->out, p, n);
    return (int)n;
}

static int bio_recv(void *ctx, unsigned char *p, size_t n) {
    Tls *s = ctx;
    if (s->in.len == 0) return MBEDTLS_ERR_SSL_WANT_READ;
    return (int)buf_take(&s->in, p, n);
}

static int entropy(void *ctx, unsigned char *p, size_t n) {
    SA_UNUSED(ctx);
    return platform_random_bytes(p, n) ? 0 : MBEDTLS_ERR_CTR_DRBG_ENTROPY_SOURCE_FAILED;
}

/* The server's chain: in the session being negotiated during the handshake, in the current session after it */
static const mbedtls_x509_crt *peer_chain(Tls *s) {
    if (s->ssl.session_negotiate && s->ssl.session_negotiate->peer_cert) return s->ssl.session_negotiate->peer_cert;
    return mbedtls_ssl_get_peer_cert(&s->ssl);
}

static Tls *tls_of(VMThread *t, int64_t h) {
    if (!h) vm_throw_new(t, "Ljava/net/SocketException;", "Socket is closed");
    return (Tls *)(intptr_t)h;
}

static void throw_mbed(VMThread *t, const char *cls, const char *what, int err) {
    char msg[200];
    mbedtls_strerror(err, msg, sizeof msg);
    vm_throw_new(t, cls, "%s: %s (-0x%04x)", what, msg, -err);
}

static bool range_ok(VMThread *t, ArrayObject *b, int32_t off, int32_t len) {
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

static void tls_free(Tls *s) {
    mbedtls_ssl_free(&s->ssl);
    mbedtls_ssl_config_free(&s->conf);
    mbedtls_ctr_drbg_free(&s->drbg);
    for (char **p = s->alpn; p && *p; p++) free(*p);
    free(s->alpn);
    free(s->suites);
    free(s->in.data);
    free(s->out.data);
    free(s);
}

/* static long nNew(String sniHost, String[] alpn, String[] suites): a client session. sniHost null sends no server
 * name; suites are mbedtls names (TLS-ECDHE-RSA-WITH-AES-128-GCM-SHA256), unknown ones are skipped */
NATIVE(Tls_nNew) {
    UNUSED_ARGS();
    Tls *s = sa_calloc(1, sizeof *s);
    mbedtls_ssl_init(&s->ssl);
    mbedtls_ssl_config_init(&s->conf);
    mbedtls_ctr_drbg_init(&s->drbg);
    int r = mbedtls_ctr_drbg_seed(&s->drbg, entropy, NULL, (const unsigned char *)"switchapk tls", 13);
    if (!r) r = mbedtls_ssl_config_defaults(&s->conf, MBEDTLS_SSL_IS_CLIENT, MBEDTLS_SSL_TRANSPORT_STREAM,
                                            MBEDTLS_SSL_PRESET_DEFAULT);
    if (r) {
        tls_free(s);
        throw_mbed(t, "Ljavax/net/ssl/SSLException;", "TLS setup", r);
        return;
    }
    mbedtls_ssl_conf_authmode(&s->conf, MBEDTLS_SSL_VERIFY_NONE); /* Java TrustManagers decide, see the top */
    mbedtls_ssl_conf_rng(&s->conf, mbedtls_ctr_drbg_random, &s->drbg);
    mbedtls_ssl_conf_min_version(&s->conf, MBEDTLS_SSL_MAJOR_VERSION_3, MBEDTLS_SSL_MINOR_VERSION_3);
    ArrayObject *alpn = A_ARR(1);
    if (alpn && alpn->length > 0) {
        s->alpn = sa_calloc((size_t)alpn->length + 1, sizeof(char *));
        int n = 0;
        for (int32_t i = 0; i < alpn->length; i++) {
            char *p = nat_str(ARRAY_DATA(alpn, Object *)[i]);
            if (p && *p) s->alpn[n++] = p;
            else free(p);
        }
        if (n) mbedtls_ssl_conf_alpn_protocols(&s->conf, (const char **)s->alpn);
    }
    ArrayObject *suites = A_ARR(2);
    if (suites && suites->length > 0) {
        s->suites = sa_calloc((size_t)suites->length + 1, sizeof(int));
        int n = 0;
        for (int32_t i = 0; i < suites->length; i++) {
            char *name = nat_str(ARRAY_DATA(suites, Object *)[i]);
            int id = name ? mbedtls_ssl_get_ciphersuite_id(name) : 0;
            free(name);
            if (id) s->suites[n++] = id;
        }
        if (!n) {
            tls_free(s);
            vm_throw_new(t, "Ljavax/net/ssl/SSLHandshakeException;", "No enabled cipher suite is supported");
            return;
        }
        mbedtls_ssl_conf_ciphersuites(&s->conf, s->suites);
    }
    r = mbedtls_ssl_setup(&s->ssl, &s->conf);
    char *host = nat_str(A_OBJ(0));
    if (!r && host) r = mbedtls_ssl_set_hostname(&s->ssl, host);
    free(host);
    if (r) {
        tls_free(s);
        throw_mbed(t, "Ljavax/net/ssl/SSLException;", "TLS setup", r);
        return;
    }
    mbedtls_ssl_set_bio(&s->ssl, s, bio_send, bio_recv, NULL);
    R_LONG((int64_t)(intptr_t)s);
}

/* static int nHandshake(long h): 0 when done, TLS_WANT_READ, or TLS_NEED_VERIFY (see the top) */
NATIVE(Tls_nHandshake) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    if (!s) return;
    int r = 0, result;
    VM_BLOCKING_BEGIN(t);
    for (;;) {
        if (!s->verified && peer_chain(s) &&
            (s->ssl.state == MBEDTLS_SSL_SERVER_KEY_EXCHANGE || s->ssl.state == MBEDTLS_SSL_HANDSHAKE_OVER)) {
            result = TLS_NEED_VERIFY;
            break;
        }
        if (s->ssl.state == MBEDTLS_SSL_HANDSHAKE_OVER) {
            result = 0;
            break;
        }
        r = mbedtls_ssl_handshake_step(&s->ssl);
        if (r == MBEDTLS_ERR_SSL_WANT_READ) {
            result = TLS_WANT_READ;
            break;
        }
        if (r) {
            result = 1;
            break;
        }
    }
    VM_BLOCKING_END(t);
    if (result == 1) {
        throw_mbed(t, "Ljavax/net/ssl/SSLHandshakeException;", "Handshake failed", r);
        return;
    }
    if (result == 0 && !s->verified) {
        /* a handshake without a server certificate (anonymous or PSK suites are not offered): refuse it */
        vm_throw_new(t, "Ljavax/net/ssl/SSLPeerUnverifiedException;", "No peer certificate");
        return;
    }
    R_INT(result);
}

/* static void nVerified(long h): the TrustManager accepted the chain; the handshake may go on */
NATIVE(Tls_nVerified) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    if (s) s->verified = true;
}

/* static void nFeed(long h, byte[] b, int off, int len): bytes read from the transport */
NATIVE(Tls_nFeed) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    ArrayObject *b = A_ARR(2);
    int32_t off = A_INT(3), len = A_INT(4);
    if (!s || !range_ok(t, b, off, len)) return;
    buf_append(&s->in, ARRAY_DATA(b, uint8_t) + off, (size_t)len);
}

/* static int nPending(long h): bytes waiting to be sent to the transport */
NATIVE(Tls_nPending) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    if (s) R_INT((int32_t)s->out.len);
}

/* static int nDrain(long h, byte[] b, int off, int len) */
NATIVE(Tls_nDrain) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    ArrayObject *b = A_ARR(2);
    int32_t off = A_INT(3), len = A_INT(4);
    if (!s || !range_ok(t, b, off, len)) return;
    R_INT((int32_t)buf_take(&s->out, ARRAY_DATA(b, uint8_t) + off, (size_t)len));
}

/* static int nRead(long h, byte[] b, int off, int len): plaintext bytes, TLS_EOF after close_notify, TLS_WANT_READ */
NATIVE(Tls_nRead) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    ArrayObject *b = A_ARR(2);
    int32_t off = A_INT(3), len = A_INT(4);
    if (!s || !range_ok(t, b, off, len)) return;
    if (len == 0) {
        R_INT(0);
        return;
    }
    int r;
    VM_BLOCKING_BEGIN(t);
    do {
        r = mbedtls_ssl_read(&s->ssl, ARRAY_DATA(b, uint8_t) + off, (size_t)len);
        /* a record with no application data (an empty fragment) reads as 0: keep going while input lasts */
    } while (r == 0 && s->in.len > 0);
    VM_BLOCKING_END(t);
    if (r > 0) R_INT(r);
    else if (r == MBEDTLS_ERR_SSL_PEER_CLOSE_NOTIFY) R_INT(TLS_EOF);
    else if (r == 0 || r == MBEDTLS_ERR_SSL_WANT_READ || r == MBEDTLS_ERR_SSL_WANT_WRITE) R_INT(TLS_WANT_READ);
    else throw_mbed(t, "Ljavax/net/ssl/SSLException;", "Read error", r);
}

/* static int nWrite(long h, byte[] b, int off, int len): plaintext bytes taken (at most one record) */
NATIVE(Tls_nWrite) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    ArrayObject *b = A_ARR(2);
    int32_t off = A_INT(3), len = A_INT(4);
    if (!s || !range_ok(t, b, off, len)) return;
    int r;
    VM_BLOCKING_BEGIN(t);
    r = mbedtls_ssl_write(&s->ssl, ARRAY_DATA(b, uint8_t) + off, (size_t)len);
    VM_BLOCKING_END(t);
    if (r >= 0) R_INT(r);
    else throw_mbed(t, "Ljavax/net/ssl/SSLException;", "Write error", r);
}

/* static int nAvailable(long h): decrypted bytes ready to read without touching the transport */
NATIVE(Tls_nAvailable) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    if (s) R_INT((int32_t)mbedtls_ssl_get_bytes_avail(&s->ssl));
}

/* static byte[] nSessionId(long h) */
NATIVE(Tls_nSessionId) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    if (!s) return;
    const mbedtls_ssl_session *ss = s->ssl.session ? s->ssl.session : s->ssl.session_negotiate;
    size_t n = ss ? ss->id_len : 0;
    ArrayObject *a = vm_alloc_array(t, g_vm.wk.arr_B, (int32_t)n);
    if (a && n) memcpy(ARRAY_DATA(a, uint8_t), ss->id, n);
    R_OBJ(a);
}

/* static void nCloseNotify(long h) */
NATIVE(Tls_nCloseNotify) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    if (s) mbedtls_ssl_close_notify(&s->ssl);
}

/* static void nAlert(long h, int description): a fatal alert, e.g. bad_certificate (42) */
NATIVE(Tls_nAlert) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    if (s) mbedtls_ssl_send_alert_message(&s->ssl, MBEDTLS_SSL_ALERT_LEVEL_FATAL, (unsigned char)A_INT(2));
}

static ArrayObject *der_array(VMThread *t, const mbedtls_x509_crt *c) {
    int n = 0;
    for (const mbedtls_x509_crt *p = c; p && p->raw.len; p = p->next) n++;
    ArrayObject *a = vm_alloc_array(t, g_vm.wk.arr_Object, n);
    if (!a) return NULL;
    int i = 0;
    for (const mbedtls_x509_crt *p = c; p && p->raw.len; p = p->next) {
        ArrayObject *d = vm_alloc_array(t, g_vm.wk.arr_B, (int32_t)p->raw.len);
        if (!d) return NULL;
        memcpy(ARRAY_DATA(d, uint8_t), p->raw.p, p->raw.len);
        ARRAY_DATA(a, Object *)[i++] = (Object *)d;
    }
    return a;
}

/* static Object[] nPeerCerts(long h): the server's chain as DER byte arrays, leaf first */
NATIVE(Tls_nPeerCerts) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    if (s) R_OBJ(der_array(t, peer_chain(s)));
}

/* static String nCipherSuite(long h): mbedtls' name, e.g. TLS-ECDHE-RSA-WITH-AES-128-GCM-SHA256 */
NATIVE(Tls_nCipherSuite) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    const char *c = NULL;
    if (s) {
        /* during the handshake the suite is in the session being negotiated */
        c = mbedtls_ssl_get_ciphersuite(&s->ssl);
        if (!c && s->ssl.session_negotiate) c = mbedtls_ssl_get_ciphersuite_name(s->ssl.session_negotiate->ciphersuite);
    }
    if (s) R_OBJ(c ? vm_new_string_utf8(t, c) : NULL);
}

/* static String nProtocol(long h): "TLSv1.2" */
NATIVE(Tls_nProtocol) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    if (s) R_OBJ(vm_new_string_utf8(t, mbedtls_ssl_get_version(&s->ssl)));
}

/* static String nAlpn(long h): the negotiated application protocol, or null */
NATIVE(Tls_nAlpn) {
    UNUSED_ARGS();
    Tls *s = tls_of(t, A_LONG(0));
    const char *p = s ? mbedtls_ssl_get_alpn_protocol(&s->ssl) : NULL;
    if (s) R_OBJ(p ? vm_new_string_utf8(t, p) : NULL);
}

/* static void nFree(long h) */
NATIVE(Tls_nFree) {
    UNUSED_ARGS();
    Tls *s = (Tls *)(intptr_t)A_LONG(0);
    if (s) tls_free(s);
}

/* ---- certificate chains ------------------------------------------------------------------------------------- */

/* static long nAnchors(Object[] ders): parsed trust anchors (unparsable ones are skipped), kept for the process */
NATIVE(Tls_nAnchors) {
    UNUSED_ARGS();
    ArrayObject *ders = A_ARR(0);
    mbedtls_x509_crt *ca = sa_calloc(1, sizeof *ca);
    mbedtls_x509_crt_init(ca);
    int32_t n = ders ? ders->length : 0, bad = 0;
    for (int32_t i = 0; i < n; i++) {
        ArrayObject *d = (ArrayObject *)ARRAY_DATA(ders, Object *)[i];
        if (!d || mbedtls_x509_crt_parse_der(ca, ARRAY_DATA(d, uint8_t), (size_t)d->length)) bad++;
    }
    if (bad) LOGW("%d of %d trust anchors could not be parsed", bad, n);
    R_LONG((int64_t)(intptr_t)ca);
}

typedef struct {
    const mbedtls_x509_crt *path[MBEDTLS_X509_MAX_VERIFY_CHAIN_SIZE + 2];
    int top;
} VerifyPath;

/* mbedtls calls this for each certificate of the path it built, from the trust anchor down to the leaf (depth 0) */
static int collect_path(void *ctx, mbedtls_x509_crt *crt, int depth, uint32_t *flags) {
    SA_UNUSED(flags);
    VerifyPath *p = ctx;
    if (depth >= 0 && depth < (int)SA_ARRAY_LEN(p->path)) {
        p->path[depth] = crt;
        if (depth + 1 > p->top) p->top = depth + 1;
    }
    return 0;
}

/* static int nVerifyChain(long anchors, Object[] chain, boolean serverAuth, Object[] pathOut): 0 or mbedtls X509
 * BADCERT_* flags, -1 when the chain cannot be parsed. On success pathOut[0] (when given) receives the verified path
 * as DER byte arrays, leaf first, ending with the trust anchor. */
NATIVE(Tls_nVerifyChain) {
    UNUSED_ARGS();
    mbedtls_x509_crt *ca = (mbedtls_x509_crt *)(intptr_t)A_LONG(0);
    ArrayObject *ders = A_ARR(2);
    bool server = A_BOOL(3);
    ArrayObject *out = A_ARR(4);
    if (!ders || ders->length == 0) {
        R_INT(-1);
        return;
    }
    mbedtls_x509_crt chain;
    mbedtls_x509_crt_init(&chain);
    int r = 0;
    for (int32_t i = 0; i < ders->length && !r; i++) {
        ArrayObject *d = (ArrayObject *)ARRAY_DATA(ders, Object *)[i];
        r = d ? mbedtls_x509_crt_parse_der(&chain, ARRAY_DATA(d, uint8_t), (size_t)d->length) : -1;
    }
    uint32_t flags = 0;
    VerifyPath path = {.top = 0};
    if (!r) {
        VM_BLOCKING_BEGIN(t);
        r = mbedtls_x509_crt_verify(&chain, ca, NULL, NULL, &flags, collect_path, &path);
        /* the leaf must be usable for TLS when it says what it is for: digitalSignature (ECDHE) or
         * keyEncipherment (RSA key exchange), and serverAuth or clientAuth */
        if (mbedtls_x509_crt_check_key_usage(&chain, MBEDTLS_X509_KU_DIGITAL_SIGNATURE) &&
            mbedtls_x509_crt_check_key_usage(&chain, MBEDTLS_X509_KU_KEY_ENCIPHERMENT))
            flags |= MBEDTLS_X509_BADCERT_KEY_USAGE;
        int eku = server ? mbedtls_x509_crt_check_extended_key_usage(&chain, MBEDTLS_OID_SERVER_AUTH,
                                                                     MBEDTLS_OID_SIZE(MBEDTLS_OID_SERVER_AUTH))
                         : mbedtls_x509_crt_check_extended_key_usage(&chain, MBEDTLS_OID_CLIENT_AUTH,
                                                                     MBEDTLS_OID_SIZE(MBEDTLS_OID_CLIENT_AUTH));
        if (eku) flags |= MBEDTLS_X509_BADCERT_EXT_KEY_USAGE;
        VM_BLOCKING_END(t);
    }
    if (!r && !flags && out && out->length > 0) {
        ArrayObject *a = vm_alloc_array(t, g_vm.wk.arr_Object, path.top);
        for (int i = 0; a && i < path.top; i++) {
            const mbedtls_x509_crt *c = path.path[i];
            ArrayObject *d = c ? vm_alloc_array(t, g_vm.wk.arr_B, (int32_t)c->raw.len) : NULL;
            if (d) memcpy(ARRAY_DATA(d, uint8_t), c->raw.p, c->raw.len);
            ARRAY_DATA(a, Object *)[i] = (Object *)d;
        }
        ARRAY_DATA(out, Object *)[0] = (Object *)a;
    }
    mbedtls_x509_crt_free(&chain);
    if (r && !flags) R_INT(-1);
    else R_INT((int32_t)flags);
}

/* static String nVerifyText(int flags): mbedtls' description of verification flags */
NATIVE(Tls_nVerifyText) {
    UNUSED_ARGS();
    char buf[512];
    int n = mbedtls_x509_crt_verify_info(buf, sizeof buf, "", (uint32_t)A_INT(0));
    while (n > 0 && buf[n - 1] == '\n') buf[--n] = 0;
    for (char *p = buf; *p; p++)
        if (*p == '\n') *p = ';';
    R_OBJ(vm_new_string_utf8(t, n > 0 ? buf : "unknown verification failure"));
}

#define C "Llibcore/ssl/Tls;"
static const NativeMethodReg g_regs[] = {
    {C, "nNew", "(Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)J", Tls_nNew},
    {C, "nHandshake", "(J)I", Tls_nHandshake},
    {C, "nVerified", "(J)V", Tls_nVerified},
    {C, "nFeed", "(J[BII)V", Tls_nFeed},
    {C, "nPending", "(J)I", Tls_nPending},
    {C, "nDrain", "(J[BII)I", Tls_nDrain},
    {C, "nRead", "(J[BII)I", Tls_nRead},
    {C, "nWrite", "(J[BII)I", Tls_nWrite},
    {C, "nAvailable", "(J)I", Tls_nAvailable},
    {C, "nSessionId", "(J)[B", Tls_nSessionId},
    {C, "nCloseNotify", "(J)V", Tls_nCloseNotify},
    {C, "nAlert", "(JI)V", Tls_nAlert},
    {C, "nPeerCerts", "(J)[Ljava/lang/Object;", Tls_nPeerCerts},
    {C, "nCipherSuite", "(J)Ljava/lang/String;", Tls_nCipherSuite},
    {C, "nProtocol", "(J)Ljava/lang/String;", Tls_nProtocol},
    {C, "nAlpn", "(J)Ljava/lang/String;", Tls_nAlpn},
    {C, "nFree", "(J)V", Tls_nFree},
    {C, "nAnchors", "([Ljava/lang/Object;)J", Tls_nAnchors},
    {C, "nVerifyChain", "(J[Ljava/lang/Object;Z[Ljava/lang/Object;)I", Tls_nVerifyChain},
    {C, "nVerifyText", "(I)Ljava/lang/String;", Tls_nVerifyText},
};

void natives_java_ssl_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
