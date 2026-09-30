/*
 * switchapk - Android APK loader for Nintendo Switch homebrew.
 * Common definitions shared by every module.
 */
#ifndef SWITCHAPK_COMMON_H
#define SWITCHAPK_COMMON_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>

#define SA_UNUSED(x) ((void)(x))
#define SA_ARRAY_LEN(a) (sizeof(a) / sizeof((a)[0]))
#define SA_ALIGN_UP(v, a) (((v) + ((a) - 1)) & ~((a) - 1))
#define SA_LIKELY(x) __builtin_expect(!!(x), 1)
#define SA_UNLIKELY(x) __builtin_expect(!!(x), 0)
#define SA_NORETURN __attribute__((noreturn))
#define SA_PRINTF(a, b) __attribute__((format(printf, a, b)))

/* ---- logging ---------------------------------------------------------- */

enum { SA_LOG_VERBOSE = 2, SA_LOG_DEBUG, SA_LOG_INFO, SA_LOG_WARN, SA_LOG_ERROR, SA_LOG_FATAL };

extern int sa_log_level;
void sa_log(int prio, const char *tag, const char *fmt, ...) SA_PRINTF(3, 4);
void sa_vlog(int prio, const char *tag, const char *fmt, va_list ap);
void sa_log_set_file(FILE *f);
SA_NORETURN void sa_fatal(const char *fmt, ...) SA_PRINTF(1, 2);

#define LOGV(...) sa_log(SA_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...) sa_log(SA_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) sa_log(SA_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) sa_log(SA_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) sa_log(SA_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* ---- memory ----------------------------------------------------------- */

void *sa_malloc(size_t n);
void *sa_calloc(size_t n, size_t m);
void *sa_realloc(void *p, size_t n);
char *sa_strdup(const char *s);
char *sa_strndup(const char *s, size_t n);
char *sa_sprintf(const char *fmt, ...) SA_PRINTF(1, 2);

/* ---- growable byte buffer --------------------------------------------- */

typedef struct {
    uint8_t *data;
    size_t len, cap;
} SaBuf;

void sa_buf_reserve(SaBuf *b, size_t extra);
void sa_buf_append(SaBuf *b, const void *p, size_t n);
void sa_buf_putc(SaBuf *b, char c);
void sa_buf_puts(SaBuf *b, const char *s);
void sa_buf_printf(SaBuf *b, const char *fmt, ...) SA_PRINTF(2, 3);
char *sa_buf_cstr(SaBuf *b); /* NUL terminates and returns data (keeps ownership) */
void sa_buf_free(SaBuf *b);

/* ---- growable pointer vector ------------------------------------------ */

typedef struct {
    void **items;
    size_t len, cap;
} SaVec;

void sa_vec_push(SaVec *v, void *p);
void *sa_vec_pop(SaVec *v);
void sa_vec_free(SaVec *v);

/* ---- string-keyed hash map (keys are copied) --------------------------- */

typedef struct SaMapEntry {
    char *key;
    void *value;
    uint32_t hash;
    struct SaMapEntry *next;
} SaMapEntry;

typedef struct {
    SaMapEntry **buckets;
    size_t nbuckets, count;
} SaMap;

uint32_t sa_hash_str(const char *s);
uint32_t sa_hash_bytes(const void *p, size_t n);
void *sa_map_get(const SaMap *m, const char *key);
bool sa_map_has(const SaMap *m, const char *key);
void sa_map_put(SaMap *m, const char *key, void *value);
void *sa_map_remove(SaMap *m, const char *key);
void sa_map_free(SaMap *m, void (*free_value)(void *));
typedef void (*SaMapIter)(const char *key, void *value, void *ctx);
void sa_map_foreach(const SaMap *m, SaMapIter fn, void *ctx);

/* ---- pointer-keyed hash map ------------------------------------------- */

typedef struct {
    uintptr_t *keys;
    void **values;
    size_t cap, count, tombstones;
} SaPtrMap;

void *sa_ptrmap_get(const SaPtrMap *m, const void *key);
bool sa_ptrmap_has(const SaPtrMap *m, const void *key);
void sa_ptrmap_put(SaPtrMap *m, const void *key, void *value);
bool sa_ptrmap_remove(SaPtrMap *m, const void *key);
void sa_ptrmap_free(SaPtrMap *m);

/* ---- string interning (process lifetime) ------------------------------- */

const char *sa_intern(const char *s);
const char *sa_intern_n(const char *s, size_t n);

/* ---- files -------------------------------------------------------------- */

uint8_t *sa_read_file(const char *path, size_t *out_len);
bool sa_write_file(const char *path, const void *data, size_t len);
bool sa_file_exists(const char *path);
bool sa_mkdirs(const char *path);

/* ---- little-endian readers --------------------------------------------- */

static inline uint16_t sa_rd16(const uint8_t *p) { return (uint16_t)(p[0] | (p[1] << 8)); }
static inline uint32_t sa_rd32(const uint8_t *p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}
static inline uint64_t sa_rd64(const uint8_t *p) { return (uint64_t)sa_rd32(p) | ((uint64_t)sa_rd32(p + 4) << 32); }

/* ---- time --------------------------------------------------------------- */

uint64_t sa_time_ns(void);      /* monotonic */
int64_t sa_wall_time_ms(void);  /* epoch millis */
void sa_sleep_ns(uint64_t ns);

/* ---- utf helpers -------------------------------------------------------- */

/* Decodes Modified UTF-8 into UTF-16. Returns number of UTF-16 units written
 * (out may be NULL to count). */
size_t sa_mutf8_to_utf16(const char *in, uint16_t *out);
/* Encodes UTF-16 to Modified UTF-8 (NUL encoded as C0 80). Returned string is malloc'd. */
char *sa_utf16_to_mutf8(const uint16_t *in, size_t len);
/* Encodes UTF-16 to standard UTF-8 (surrogate pairs combined). Malloc'd. */
char *sa_utf16_to_utf8(const uint16_t *in, size_t len, size_t *out_len);
/* Decodes standard UTF-8 into UTF-16. Returns units written (out may be NULL). */
size_t sa_utf8_to_utf16(const char *in, size_t len, uint16_t *out);

#endif
