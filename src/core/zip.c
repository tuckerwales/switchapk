#include "zip.h"

#include <pthread.h>
#include <zlib.h>

#define LOG_TAG "zip"

struct ZipArchive {
    FILE *f;
    const uint8_t *mem;
    size_t mem_len;
    size_t file_len;
    ZipEntry *entries;
    size_t count;
    SaMap index;
    pthread_mutex_t lock;
};

static bool zread(ZipArchive *z, uint64_t off, void *buf, size_t n) {
    if (z->mem) {
        if (off + n > z->mem_len) return false;
        memcpy(buf, z->mem + off, n);
        return true;
    }
    if (fseeko(z->f, (off_t)off, SEEK_SET) != 0) return false;
    return fread(buf, 1, n, z->f) == n;
}

static bool zip_parse(ZipArchive *z) {
    size_t len = z->file_len;
    if (len < 22) return false;
    size_t scan = len < 65557 ? len : 65557;
    uint8_t *tail = sa_malloc(scan);
    if (!zread(z, len - scan, tail, scan)) {
        free(tail);
        return false;
    }
    long eocd = -1;
    for (long i = (long)scan - 22; i >= 0; i--) {
        if (sa_rd32(tail + i) == 0x06054b50) {
            eocd = i;
            break;
        }
    }
    if (eocd < 0) {
        free(tail);
        LOGE("no end of central directory record");
        return false;
    }
    uint16_t total = sa_rd16(tail + eocd + 10);
    uint32_t cd_size = sa_rd32(tail + eocd + 12);
    uint32_t cd_off = sa_rd32(tail + eocd + 16);
    free(tail);
    if ((uint64_t)cd_off + cd_size > len) {
        LOGE("central directory out of range");
        return false;
    }
    uint8_t *cd = sa_malloc(cd_size ? cd_size : 1);
    if (!zread(z, cd_off, cd, cd_size)) {
        free(cd);
        return false;
    }
    z->entries = sa_calloc(total ? total : 1, sizeof(ZipEntry));
    size_t p = 0;
    for (unsigned i = 0; i < total; i++) {
        if (p + 46 > cd_size || sa_rd32(cd + p) != 0x02014b50) {
            LOGW("central directory truncated at entry %u", i);
            break;
        }
        ZipEntry *e = &z->entries[z->count];
        e->flags = sa_rd16(cd + p + 8);
        e->method = sa_rd16(cd + p + 10);
        e->mod_time = sa_rd16(cd + p + 12);
        e->mod_date = sa_rd16(cd + p + 14);
        e->crc = sa_rd32(cd + p + 16);
        e->comp_size = sa_rd32(cd + p + 20);
        e->uncomp_size = sa_rd32(cd + p + 24);
        uint16_t nlen = sa_rd16(cd + p + 28);
        uint16_t xlen = sa_rd16(cd + p + 30);
        uint16_t clen = sa_rd16(cd + p + 32);
        e->local_offset = sa_rd32(cd + p + 42);
        if (p + 46 + nlen > cd_size) break;
        e->name = sa_strndup((const char *)cd + p + 46, nlen);
        sa_map_put(&z->index, e->name, e);
        z->count++;
        p += 46 + nlen + xlen + clen;
    }
    free(cd);
    return true;
}

ZipArchive *zip_open(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    ZipArchive *z = sa_calloc(1, sizeof *z);
    z->f = f;
    fseeko(f, 0, SEEK_END);
    z->file_len = (size_t)ftello(f);
    pthread_mutex_init(&z->lock, NULL);
    if (!zip_parse(z)) {
        zip_close(z);
        return NULL;
    }
    return z;
}

ZipArchive *zip_open_memory(const uint8_t *data, size_t len) {
    ZipArchive *z = sa_calloc(1, sizeof *z);
    z->mem = data;
    z->mem_len = len;
    z->file_len = len;
    pthread_mutex_init(&z->lock, NULL);
    if (!zip_parse(z)) {
        zip_close(z);
        return NULL;
    }
    return z;
}

void zip_close(ZipArchive *z) {
    if (!z) return;
    if (z->f) fclose(z->f);
    for (size_t i = 0; i < z->count; i++) free(z->entries[i].name);
    free(z->entries);
    sa_map_free(&z->index, NULL);
    pthread_mutex_destroy(&z->lock);
    free(z);
}

size_t zip_count(const ZipArchive *z) { return z->count; }
const ZipEntry *zip_entry_at(const ZipArchive *z, size_t i) { return i < z->count ? &z->entries[i] : NULL; }
const ZipEntry *zip_find(const ZipArchive *z, const char *name) {
    if (name[0] == '/') name++;
    return sa_map_get(&z->index, name);
}

static int64_t data_offset_locked(ZipArchive *z, const ZipEntry *e) {
    uint8_t lh[30];
    if (!zread(z, e->local_offset, lh, 30) || sa_rd32(lh) != 0x04034b50) return -1;
    return (int64_t)e->local_offset + 30 + sa_rd16(lh + 26) + sa_rd16(lh + 28);
}

int64_t zip_stored_data_offset(ZipArchive *z, const ZipEntry *e) {
    if (e->method != 0) return -1;
    pthread_mutex_lock(&z->lock);
    int64_t off = data_offset_locked(z, e);
    pthread_mutex_unlock(&z->lock);
    return off;
}

uint8_t *zip_extract(ZipArchive *z, const ZipEntry *e, size_t *out_len) {
    pthread_mutex_lock(&z->lock);
    int64_t off = data_offset_locked(z, e);
    if (off < 0) {
        pthread_mutex_unlock(&z->lock);
        LOGE("bad local header for %s", e->name);
        return NULL;
    }
    uint8_t *comp = sa_malloc((size_t)e->comp_size + 1);
    bool ok = zread(z, (uint64_t)off, comp, e->comp_size);
    pthread_mutex_unlock(&z->lock);
    if (!ok) {
        free(comp);
        return NULL;
    }
    if (e->method == 0) {
        comp[e->comp_size] = 0;
        if (out_len) *out_len = e->comp_size;
        return comp;
    }
    if (e->method != 8) {
        LOGE("unsupported compression method %u for %s", e->method, e->name);
        free(comp);
        return NULL;
    }
    uint8_t *out = sa_malloc((size_t)e->uncomp_size + 1);
    z_stream s;
    memset(&s, 0, sizeof s);
    if (inflateInit2(&s, -MAX_WBITS) != Z_OK) {
        free(comp);
        free(out);
        return NULL;
    }
    s.next_in = comp;
    s.avail_in = e->comp_size;
    s.next_out = out;
    s.avail_out = e->uncomp_size;
    int r = inflate(&s, Z_FINISH);
    inflateEnd(&s);
    free(comp);
    if (r != Z_STREAM_END && !(r == Z_BUF_ERROR && s.avail_out == 0)) {
        LOGE("inflate failed (%d) for %s", r, e->name);
        free(out);
        return NULL;
    }
    out[e->uncomp_size] = 0;
    if (out_len) *out_len = e->uncomp_size;
    return out;
}

uint8_t *zip_extract_name(ZipArchive *z, const char *name, size_t *out_len) {
    const ZipEntry *e = zip_find(z, name);
    return e ? zip_extract(z, e, out_len) : NULL;
}

bool zip_extract_to_file(ZipArchive *z, const ZipEntry *e, FILE *f) {
    if (e->method != 0 && e->method != 8) {
        LOGE("unsupported compression method %u for %s", e->method, e->name);
        return false;
    }
    enum { CHUNK = 1 << 16 };
    uint8_t *in = sa_malloc(CHUNK), *out = sa_malloc(CHUNK);
    z_stream s;
    memset(&s, 0, sizeof s);
    bool ok = e->method == 0 || inflateInit2(&s, -MAX_WBITS) == Z_OK;
    bool inflating = ok && e->method == 8;
    pthread_mutex_lock(&z->lock);
    int64_t off = ok ? data_offset_locked(z, e) : -1;
    pthread_mutex_unlock(&z->lock);
    ok = off >= 0;
    uint64_t done = 0;
    int r = Z_OK;
    while (ok && done < e->comp_size) {
        size_t n = e->comp_size - done < CHUNK ? (size_t)(e->comp_size - done) : CHUNK;
        pthread_mutex_lock(&z->lock);
        ok = zread(z, (uint64_t)off + done, in, n);
        pthread_mutex_unlock(&z->lock);
        if (!ok) break;
        done += n;
        if (!inflating) {
            ok = fwrite(in, 1, n, f) == n;
            continue;
        }
        s.next_in = in;
        s.avail_in = (uInt)n;
        do {
            s.next_out = out;
            s.avail_out = CHUNK;
            r = inflate(&s, Z_NO_FLUSH);
            if (r != Z_OK && r != Z_STREAM_END && r != Z_BUF_ERROR) {
                ok = false;
                break;
            }
            size_t got = CHUNK - s.avail_out;
            if (got && fwrite(out, 1, got, f) != got) ok = false;
        } while (ok && s.avail_out == 0);
    }
    if (inflating) {
        if (ok && r != Z_STREAM_END) ok = false;
        inflateEnd(&s);
    }
    if (!ok) LOGE("cannot extract %s", e->name);
    free(in);
    free(out);
    return ok;
}

typedef struct {
    char *name;
    uint16_t flags, method, mod_time, mod_date;
    uint32_t crc, comp_size, uncomp_size, offset;
} ZipWEntry;

struct ZipWriter {
    FILE *f;
    uint64_t pos;
    ZipWEntry *entries;
    size_t count;
    bool failed;
};

static void put16(uint8_t *p, uint16_t v) {
    p[0] = (uint8_t)v;
    p[1] = (uint8_t)(v >> 8);
}

static void put32(uint8_t *p, uint32_t v) {
    put16(p, (uint16_t)v);
    put16(p + 2, (uint16_t)(v >> 16));
}

static void zw_write(ZipWriter *w, const void *p, size_t n) {
    if (w->failed) return;
    if (n && fwrite(p, 1, n, w->f) != n) w->failed = true;
    w->pos += n;
}

ZipWriter *zipw_create(const char *path) {
    FILE *f = fopen(path, "wb");
    if (!f) return NULL;
    ZipWriter *w = sa_calloc(1, sizeof *w);
    w->f = f;
    return w;
}

bool zipw_copy_entry(ZipWriter *w, ZipArchive *src, const ZipEntry *e) {
    if (w->failed) return false;
    pthread_mutex_lock(&src->lock);
    int64_t off = data_offset_locked(src, e);
    pthread_mutex_unlock(&src->lock);
    size_t nlen = strlen(e->name);
    if (off < 0 || nlen > 0xffff || w->pos + 30 + nlen + 4 + e->comp_size > 0xffffffffull) {
        w->failed = true;
        return false;
    }
    /* Pad stored data to a 4-byte boundary through the extra field, as zipalign does. */
    size_t pad = e->method == 0 ? (size_t)((4 - (w->pos + 30 + nlen) % 4) % 4) : 0;
    ZipWEntry *we;
    w->entries = sa_realloc(w->entries, (w->count + 1) * sizeof *w->entries);
    we = &w->entries[w->count++];
    we->name = sa_strdup(e->name);
    /* Sizes go in the header, so no data descriptor follows (bit 3 cleared). */
    we->flags = e->flags & ~(uint16_t)8;
    we->method = e->method;
    we->mod_time = e->mod_time;
    we->mod_date = e->mod_date;
    we->crc = e->crc;
    we->comp_size = e->comp_size;
    we->uncomp_size = e->uncomp_size;
    we->offset = (uint32_t)w->pos;
    uint8_t lh[30];
    put32(lh, 0x04034b50);
    put16(lh + 4, 20);
    put16(lh + 6, we->flags);
    put16(lh + 8, we->method);
    put16(lh + 10, we->mod_time);
    put16(lh + 12, we->mod_date);
    put32(lh + 14, we->crc);
    put32(lh + 18, we->comp_size);
    put32(lh + 22, we->uncomp_size);
    put16(lh + 26, (uint16_t)nlen);
    put16(lh + 28, (uint16_t)pad);
    zw_write(w, lh, 30);
    zw_write(w, e->name, nlen);
    static const uint8_t zeros[4];
    zw_write(w, zeros, pad);
    enum { CHUNK = 1 << 16 };
    uint8_t *buf = sa_malloc(CHUNK);
    for (uint64_t done = 0; !w->failed && done < e->comp_size;) {
        size_t n = e->comp_size - done < CHUNK ? (size_t)(e->comp_size - done) : CHUNK;
        pthread_mutex_lock(&src->lock);
        bool ok = zread(src, (uint64_t)off + done, buf, n);
        pthread_mutex_unlock(&src->lock);
        if (!ok) w->failed = true;
        else zw_write(w, buf, n);
        done += n;
    }
    free(buf);
    return !w->failed;
}

bool zipw_finish(ZipWriter *w) {
    uint64_t cd_start = w->pos;
    for (size_t i = 0; i < w->count; i++) {
        const ZipWEntry *e = &w->entries[i];
        size_t nlen = strlen(e->name);
        uint8_t ch[46];
        memset(ch, 0, sizeof ch);
        put32(ch, 0x02014b50);
        put16(ch + 4, 20);
        put16(ch + 6, 20);
        put16(ch + 8, e->flags);
        put16(ch + 10, e->method);
        put16(ch + 12, e->mod_time);
        put16(ch + 14, e->mod_date);
        put32(ch + 16, e->crc);
        put32(ch + 20, e->comp_size);
        put32(ch + 24, e->uncomp_size);
        put16(ch + 28, (uint16_t)nlen);
        put32(ch + 42, e->offset);
        zw_write(w, ch, 46);
        zw_write(w, e->name, nlen);
    }
    uint64_t cd_size = w->pos - cd_start;
    uint8_t eocd[22];
    memset(eocd, 0, sizeof eocd);
    put32(eocd, 0x06054b50);
    put16(eocd + 8, (uint16_t)w->count);
    put16(eocd + 10, (uint16_t)w->count);
    put32(eocd + 12, (uint32_t)cd_size);
    put32(eocd + 16, (uint32_t)cd_start);
    if (w->count > 0xffff || w->pos > 0xffffffffull) w->failed = true;
    zw_write(w, eocd, 22);
    if (fclose(w->f) != 0) w->failed = true;
    bool ok = !w->failed;
    for (size_t i = 0; i < w->count; i++) free(w->entries[i].name);
    free(w->entries);
    free(w);
    return ok;
}
