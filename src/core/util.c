#include "common.h"

#include <errno.h>
#include <sys/stat.h>
#include <time.h>
#include <pthread.h>

#ifdef __SWITCH__
#include <switch.h>
#else
#include <unistd.h>
#endif

#define LOG_TAG "util"

/* ---- logging ------------------------------------------------------------ */

int sa_log_level = SA_LOG_INFO;
static FILE *g_log_file;
static pthread_mutex_t g_log_lock = PTHREAD_MUTEX_INITIALIZER;

void sa_log_set_file(FILE *f) { g_log_file = f; }

/* The last lines logged at INFO or above, for on-device error screens. */
#define RECENT_LINES 48
static char g_recent[RECENT_LINES][200];
static int g_recent_next, g_recent_count;

int sa_log_recent(const char **lines, int max) {
    pthread_mutex_lock(&g_log_lock);
    int n = g_recent_count < max ? g_recent_count : max;
    for (int i = 0; i < n; i++) {
        int idx = (g_recent_next - n + i + RECENT_LINES) % RECENT_LINES;
        lines[i] = g_recent[idx];
    }
    pthread_mutex_unlock(&g_log_lock);
    return n;
}

void sa_vlog(int prio, const char *tag, const char *fmt, va_list ap) {
    static const char prio_chars[] = "??VDIWEF";
    if (prio < sa_log_level) return;
    char buf[2048];
    vsnprintf(buf, sizeof buf, fmt, ap);
    char pc = (prio >= 0 && prio < 8) ? prio_chars[prio] : '?';
    pthread_mutex_lock(&g_log_lock);
    fprintf(stderr, "%c/%s: %s\n", pc, tag ? tag : "", buf);
    if (prio >= SA_LOG_INFO) {
        snprintf(g_recent[g_recent_next], sizeof g_recent[0], "%c/%.24s: %.160s", pc, tag ? tag : "", buf);
        g_recent_next = (g_recent_next + 1) % RECENT_LINES;
        if (g_recent_count < RECENT_LINES) g_recent_count++;
    }
    if (g_log_file) {
        fprintf(g_log_file, "%c/%s: %s\n", pc, tag ? tag : "", buf);
        /* Flushing every line is slow on an SD card; warnings and errors are flushed at once. */
        if (prio >= SA_LOG_WARN) fflush(g_log_file);
    }
    pthread_mutex_unlock(&g_log_lock);
}

void sa_log(int prio, const char *tag, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    sa_vlog(prio, tag, fmt, ap);
    va_end(ap);
}

void sa_fatal(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    sa_vlog(SA_LOG_FATAL, "FATAL", fmt, ap);
    va_end(ap);
    if (g_log_file) fflush(g_log_file);
    fflush(stderr);
    abort();
}

/* ---- memory ------------------------------------------------------------- */

void *sa_malloc(size_t n) {
    void *p = malloc(n ? n : 1);
    if (!p) sa_fatal("out of memory allocating %zu bytes", n);
    return p;
}

void *sa_calloc(size_t n, size_t m) {
    void *p = calloc(n ? n : 1, m ? m : 1);
    if (!p) sa_fatal("out of memory allocating %zu x %zu bytes", n, m);
    return p;
}

void *sa_realloc(void *p, size_t n) {
    void *q = realloc(p, n ? n : 1);
    if (!q) sa_fatal("out of memory reallocating %zu bytes", n);
    return q;
}

char *sa_strdup(const char *s) { return sa_strndup(s, strlen(s)); }

char *sa_strndup(const char *s, size_t n) {
    char *r = sa_malloc(n + 1);
    memcpy(r, s, n);
    r[n] = 0;
    return r;
}

char *sa_sprintf(const char *fmt, ...) {
    va_list ap, ap2;
    va_start(ap, fmt);
    va_copy(ap2, ap);
    int n = vsnprintf(NULL, 0, fmt, ap);
    va_end(ap);
    char *r = sa_malloc((size_t)n + 1);
    vsnprintf(r, (size_t)n + 1, fmt, ap2);
    va_end(ap2);
    return r;
}

/* ---- SaBuf ---------------------------------------------------------------- */

void sa_buf_reserve(SaBuf *b, size_t extra) {
    if (b->len + extra + 1 <= b->cap) return;
    size_t nc = b->cap ? b->cap * 2 : 64;
    while (nc < b->len + extra + 1) nc *= 2;
    b->data = sa_realloc(b->data, nc);
    b->cap = nc;
}

void sa_buf_append(SaBuf *b, const void *p, size_t n) {
    sa_buf_reserve(b, n);
    memcpy(b->data + b->len, p, n);
    b->len += n;
}

void sa_buf_putc(SaBuf *b, char c) { sa_buf_append(b, &c, 1); }
void sa_buf_puts(SaBuf *b, const char *s) { sa_buf_append(b, s, strlen(s)); }

void sa_buf_printf(SaBuf *b, const char *fmt, ...) {
    va_list ap, ap2;
    va_start(ap, fmt);
    va_copy(ap2, ap);
    int n = vsnprintf(NULL, 0, fmt, ap);
    va_end(ap);
    sa_buf_reserve(b, (size_t)n + 1);
    vsnprintf((char *)b->data + b->len, (size_t)n + 1, fmt, ap2);
    va_end(ap2);
    b->len += (size_t)n;
}

char *sa_buf_cstr(SaBuf *b) {
    sa_buf_reserve(b, 1);
    b->data[b->len] = 0;
    return (char *)b->data;
}

void sa_buf_free(SaBuf *b) {
    free(b->data);
    b->data = NULL;
    b->len = b->cap = 0;
}

/* ---- SaVec ---------------------------------------------------------------- */

void sa_vec_push(SaVec *v, void *p) {
    if (v->len == v->cap) {
        v->cap = v->cap ? v->cap * 2 : 8;
        v->items = sa_realloc(v->items, v->cap * sizeof(void *));
    }
    v->items[v->len++] = p;
}

void *sa_vec_pop(SaVec *v) { return v->len ? v->items[--v->len] : NULL; }

void sa_vec_free(SaVec *v) {
    free(v->items);
    v->items = NULL;
    v->len = v->cap = 0;
}

/* ---- SaMap ---------------------------------------------------------------- */

uint32_t sa_hash_str(const char *s) {
    uint32_t h = 2166136261u;
    while (*s) {
        h ^= (uint8_t)*s++;
        h *= 16777619u;
    }
    return h;
}

uint32_t sa_hash_bytes(const void *p, size_t n) {
    const uint8_t *b = p;
    uint32_t h = 2166136261u;
    for (size_t i = 0; i < n; i++) {
        h ^= b[i];
        h *= 16777619u;
    }
    return h;
}

static SaMapEntry *map_find(const SaMap *m, const char *key, uint32_t h) {
    if (!m->nbuckets) return NULL;
    for (SaMapEntry *e = m->buckets[h % m->nbuckets]; e; e = e->next)
        if (e->hash == h && strcmp(e->key, key) == 0) return e;
    return NULL;
}

void *sa_map_get(const SaMap *m, const char *key) {
    SaMapEntry *e = map_find(m, key, sa_hash_str(key));
    return e ? e->value : NULL;
}

bool sa_map_has(const SaMap *m, const char *key) { return map_find(m, key, sa_hash_str(key)) != NULL; }

static void map_grow(SaMap *m) {
    size_t nb = m->nbuckets ? m->nbuckets * 2 : 64;
    SaMapEntry **nbk = sa_calloc(nb, sizeof(*nbk));
    for (size_t i = 0; i < m->nbuckets; i++) {
        SaMapEntry *e = m->buckets[i];
        while (e) {
            SaMapEntry *n = e->next;
            e->next = nbk[e->hash % nb];
            nbk[e->hash % nb] = e;
            e = n;
        }
    }
    free(m->buckets);
    m->buckets = nbk;
    m->nbuckets = nb;
}

void sa_map_put(SaMap *m, const char *key, void *value) {
    uint32_t h = sa_hash_str(key);
    SaMapEntry *e = map_find(m, key, h);
    if (e) {
        e->value = value;
        return;
    }
    if (m->count + 1 > m->nbuckets * 3 / 4) map_grow(m);
    e = sa_malloc(sizeof *e);
    e->key = sa_strdup(key);
    e->value = value;
    e->hash = h;
    e->next = m->buckets[h % m->nbuckets];
    m->buckets[h % m->nbuckets] = e;
    m->count++;
}

void *sa_map_remove(SaMap *m, const char *key) {
    if (!m->nbuckets) return NULL;
    uint32_t h = sa_hash_str(key);
    SaMapEntry **pp = &m->buckets[h % m->nbuckets];
    while (*pp) {
        SaMapEntry *e = *pp;
        if (e->hash == h && strcmp(e->key, key) == 0) {
            void *v = e->value;
            *pp = e->next;
            free(e->key);
            free(e);
            m->count--;
            return v;
        }
        pp = &e->next;
    }
    return NULL;
}

void sa_map_foreach(const SaMap *m, SaMapIter fn, void *ctx) {
    for (size_t i = 0; i < m->nbuckets; i++)
        for (SaMapEntry *e = m->buckets[i]; e; e = e->next) fn(e->key, e->value, ctx);
}

void sa_map_free(SaMap *m, void (*free_value)(void *)) {
    for (size_t i = 0; i < m->nbuckets; i++) {
        SaMapEntry *e = m->buckets[i];
        while (e) {
            SaMapEntry *n = e->next;
            if (free_value) free_value(e->value);
            free(e->key);
            free(e);
            e = n;
        }
    }
    free(m->buckets);
    m->buckets = NULL;
    m->nbuckets = m->count = 0;
}

/* ---- SaPtrMap (open addressing, linear probing) -------------------------- */

#define PTRMAP_EMPTY ((uintptr_t)0)
#define PTRMAP_TOMB ((uintptr_t)1)

static inline size_t ptr_hash(uintptr_t k) {
    k ^= k >> 33;
    k *= 0xff51afd7ed558ccdULL;
    k ^= k >> 33;
    return (size_t)k;
}

static void ptrmap_rehash(SaPtrMap *m, size_t ncap) {
    uintptr_t *ok = m->keys;
    void **ov = m->values;
    size_t oc = m->cap;
    m->keys = sa_calloc(ncap, sizeof(uintptr_t));
    m->values = sa_calloc(ncap, sizeof(void *));
    m->cap = ncap;
    m->count = 0;
    m->tombstones = 0;
    for (size_t i = 0; i < oc; i++) {
        if (ok[i] > PTRMAP_TOMB) sa_ptrmap_put(m, (void *)ok[i], ov[i]);
    }
    free(ok);
    free(ov);
}

void *sa_ptrmap_get(const SaPtrMap *m, const void *key) {
    if (!m->cap) return NULL;
    uintptr_t k = (uintptr_t)key;
    size_t mask = m->cap - 1, i = ptr_hash(k) & mask;
    for (;;) {
        if (m->keys[i] == k) return m->values[i];
        if (m->keys[i] == PTRMAP_EMPTY) return NULL;
        i = (i + 1) & mask;
    }
}

bool sa_ptrmap_has(const SaPtrMap *m, const void *key) {
    if (!m->cap) return false;
    uintptr_t k = (uintptr_t)key;
    size_t mask = m->cap - 1, i = ptr_hash(k) & mask;
    for (;;) {
        if (m->keys[i] == k) return true;
        if (m->keys[i] == PTRMAP_EMPTY) return false;
        i = (i + 1) & mask;
    }
}

void sa_ptrmap_put(SaPtrMap *m, const void *key, void *value) {
    uintptr_t k = (uintptr_t)key;
    if (k <= PTRMAP_TOMB) sa_fatal("ptrmap: invalid key %p", key);
    if ((m->count + m->tombstones + 1) * 4 >= m->cap * 3)
        ptrmap_rehash(m, m->cap ? (m->count * 4 >= m->cap ? m->cap * 2 : m->cap) : 64);
    size_t mask = m->cap - 1, i = ptr_hash(k) & mask;
    size_t tomb = (size_t)-1;
    for (;;) {
        if (m->keys[i] == k) {
            m->values[i] = value;
            return;
        }
        if (m->keys[i] == PTRMAP_TOMB && tomb == (size_t)-1) tomb = i;
        if (m->keys[i] == PTRMAP_EMPTY) break;
        i = (i + 1) & mask;
    }
    if (tomb != (size_t)-1) {
        i = tomb;
        m->tombstones--;
    }
    m->keys[i] = k;
    m->values[i] = value;
    m->count++;
}

bool sa_ptrmap_remove(SaPtrMap *m, const void *key) {
    if (!m->cap) return false;
    uintptr_t k = (uintptr_t)key;
    size_t mask = m->cap - 1, i = ptr_hash(k) & mask;
    for (;;) {
        if (m->keys[i] == k) {
            m->keys[i] = PTRMAP_TOMB;
            m->values[i] = NULL;
            m->count--;
            m->tombstones++;
            return true;
        }
        if (m->keys[i] == PTRMAP_EMPTY) return false;
        i = (i + 1) & mask;
    }
}

void sa_ptrmap_free(SaPtrMap *m) {
    free(m->keys);
    free(m->values);
    memset(m, 0, sizeof *m);
}

/* ---- interning ------------------------------------------------------------ */

static SaMap g_intern;
static pthread_mutex_t g_intern_lock = PTHREAD_MUTEX_INITIALIZER;

const char *sa_intern(const char *s) {
    pthread_mutex_lock(&g_intern_lock);
    uint32_t h = sa_hash_str(s);
    SaMapEntry *e = map_find(&g_intern, s, h);
    const char *r;
    if (e) {
        r = e->key;
    } else {
        sa_map_put(&g_intern, s, NULL);
        r = map_find(&g_intern, s, h)->key;
    }
    pthread_mutex_unlock(&g_intern_lock);
    return r;
}

const char *sa_intern_n(const char *s, size_t n) {
    char tmp[256];
    if (n < sizeof tmp) {
        memcpy(tmp, s, n);
        tmp[n] = 0;
        return sa_intern(tmp);
    }
    char *d = sa_strndup(s, n);
    const char *r = sa_intern(d);
    free(d);
    return r;
}

/* ---- files ------------------------------------------------------------------ */

uint8_t *sa_read_file(const char *path, size_t *out_len) {
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    SaBuf b = {0};
    uint8_t tmp[65536];
    size_t n;
    while ((n = fread(tmp, 1, sizeof tmp, f)) > 0) sa_buf_append(&b, tmp, n);
    fclose(f);
    if (out_len) *out_len = b.len;
    sa_buf_reserve(&b, 1);
    b.data[b.len] = 0;
    return b.data;
}

bool sa_write_file(const char *path, const void *data, size_t len) {
    FILE *f = fopen(path, "wb");
    if (!f) return false;
    bool ok = fwrite(data, 1, len, f) == len;
    ok = (fclose(f) == 0) && ok;
    return ok;
}

bool sa_file_exists(const char *path) {
    struct stat st;
    return stat(path, &st) == 0;
}

bool sa_mkdirs(const char *path) {
    char tmp[1024];
    snprintf(tmp, sizeof tmp, "%s", path);
    size_t len = strlen(tmp);
    if (len && tmp[len - 1] == '/') tmp[len - 1] = 0;
    for (char *p = tmp + 1; *p; p++) {
        if (*p == '/' && p[-1] != ':') {
            *p = 0;
            mkdir(tmp, 0777);
            *p = '/';
        }
    }
    return mkdir(tmp, 0777) == 0 || errno == EEXIST;
}

/* ---- time --------------------------------------------------------------------- */

uint64_t sa_time_ns(void) {
#ifdef __SWITCH__
    return armTicksToNs(armGetSystemTick());
#else
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
#endif
}

int64_t sa_wall_time_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

void sa_sleep_ns(uint64_t ns) {
#ifdef __SWITCH__
    svcSleepThread((int64_t)ns);
#else
    struct timespec ts = {(time_t)(ns / 1000000000ull), (long)(ns % 1000000000ull)};
    while (nanosleep(&ts, &ts) != 0 && errno == EINTR) {
    }
#endif
}

/* ---- UTF conversions ------------------------------------------------------------ */

size_t sa_mutf8_to_utf16(const char *in, uint16_t *out) {
    const uint8_t *p = (const uint8_t *)in;
    size_t n = 0;
    while (*p) {
        uint32_t c = *p++;
        if (c < 0x80) {
        } else if ((c & 0xe0) == 0xc0) {
            c = ((c & 0x1f) << 6) | (*p ? (*p++ & 0x3f) : 0);
        } else if ((c & 0xf0) == 0xe0) {
            uint32_t b1 = *p ? (*p++ & 0x3f) : 0;
            uint32_t b2 = *p ? (*p++ & 0x3f) : 0;
            c = ((c & 0x0f) << 12) | (b1 << 6) | b2;
        } else if ((c & 0xf8) == 0xf0) {
            /* Not valid MUTF-8, but tolerate standard 4-byte UTF-8 by emitting a pair. */
            uint32_t b1 = *p ? (*p++ & 0x3f) : 0;
            uint32_t b2 = *p ? (*p++ & 0x3f) : 0;
            uint32_t b3 = *p ? (*p++ & 0x3f) : 0;
            c = ((c & 0x07) << 18) | (b1 << 12) | (b2 << 6) | b3;
            c -= 0x10000;
            if (out) {
                out[n] = (uint16_t)(0xd800 | (c >> 10));
                out[n + 1] = (uint16_t)(0xdc00 | (c & 0x3ff));
            }
            n += 2;
            continue;
        }
        if (out) out[n] = (uint16_t)c;
        n++;
    }
    return n;
}

char *sa_utf16_to_mutf8(const uint16_t *in, size_t len) {
    SaBuf b = {0};
    for (size_t i = 0; i < len; i++) {
        uint16_t c = in[i];
        if (c != 0 && c < 0x80) {
            sa_buf_putc(&b, (char)c);
        } else if (c < 0x800) {
            sa_buf_putc(&b, (char)(0xc0 | (c >> 6)));
            sa_buf_putc(&b, (char)(0x80 | (c & 0x3f)));
        } else {
            sa_buf_putc(&b, (char)(0xe0 | (c >> 12)));
            sa_buf_putc(&b, (char)(0x80 | ((c >> 6) & 0x3f)));
            sa_buf_putc(&b, (char)(0x80 | (c & 0x3f)));
        }
    }
    sa_buf_reserve(&b, 1);
    b.data[b.len] = 0;
    return (char *)b.data;
}

char *sa_utf16_to_utf8(const uint16_t *in, size_t len, size_t *out_len) {
    SaBuf b = {0};
    for (size_t i = 0; i < len; i++) {
        uint32_t c = in[i];
        if (c >= 0xd800 && c < 0xdc00 && i + 1 < len && in[i + 1] >= 0xdc00 && in[i + 1] < 0xe000) {
            c = 0x10000 + ((c - 0xd800) << 10) + (in[i + 1] - 0xdc00);
            i++;
        }
        if (c < 0x80) {
            sa_buf_putc(&b, (char)c);
        } else if (c < 0x800) {
            sa_buf_putc(&b, (char)(0xc0 | (c >> 6)));
            sa_buf_putc(&b, (char)(0x80 | (c & 0x3f)));
        } else if (c < 0x10000) {
            sa_buf_putc(&b, (char)(0xe0 | (c >> 12)));
            sa_buf_putc(&b, (char)(0x80 | ((c >> 6) & 0x3f)));
            sa_buf_putc(&b, (char)(0x80 | (c & 0x3f)));
        } else {
            sa_buf_putc(&b, (char)(0xf0 | (c >> 18)));
            sa_buf_putc(&b, (char)(0x80 | ((c >> 12) & 0x3f)));
            sa_buf_putc(&b, (char)(0x80 | ((c >> 6) & 0x3f)));
            sa_buf_putc(&b, (char)(0x80 | (c & 0x3f)));
        }
    }
    if (out_len) *out_len = b.len;
    sa_buf_reserve(&b, 1);
    b.data[b.len] = 0;
    return (char *)b.data;
}

size_t sa_utf8_to_utf16(const char *in, size_t len, uint16_t *out) {
    const uint8_t *p = (const uint8_t *)in, *end = p + len;
    size_t n = 0;
    while (p < end) {
        uint32_t c = *p++;
        int extra = 0;
        if (c >= 0xf0) {
            c &= 0x07;
            extra = 3;
        } else if (c >= 0xe0) {
            c &= 0x0f;
            extra = 2;
        } else if (c >= 0xc0) {
            c &= 0x1f;
            extra = 1;
        } else if (c >= 0x80) {
            c = 0xfffd;
        }
        while (extra-- > 0 && p < end) c = (c << 6) | (*p++ & 0x3f);
        if (c >= 0x10000) {
            c -= 0x10000;
            if (out) {
                out[n] = (uint16_t)(0xd800 | (c >> 10));
                out[n + 1] = (uint16_t)(0xdc00 | (c & 0x3ff));
            }
            n += 2;
        } else {
            if (out) out[n] = (uint16_t)c;
            n++;
        }
    }
    return n;
}
