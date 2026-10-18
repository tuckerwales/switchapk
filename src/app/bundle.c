#include "bundle.h"

#include <strings.h>
#include <sys/stat.h>
#include <unistd.h>

#include "../core/zip.h"

#define LOG_TAG "bundle"
#define CACHE_DIR ".switchapk-bundles"

static bool has_suffix_ci(const char *s, const char *suf) {
    size_t n = strlen(s), m = strlen(suf);
    return n > m && !strcasecmp(s + n - m, suf);
}

bool bundle_is_bundle(const char *path) {
    return has_suffix_ci(path, ".apkm") || has_suffix_ci(path, ".apks") || has_suffix_ci(path, ".xapk");
}

bool bundle_is_runnable(const char *path) { return has_suffix_ci(path, ".apk") || bundle_is_bundle(path); }

static const char *base_name(const char *path) {
    const char *s = strrchr(path, '/');
    return s ? s + 1 : path;
}

typedef struct {
    const ZipEntry *entry; /* in the bundle */
    char *tmp;             /* extracted copy */
    ZipArchive *zip;
    bool has_dex;
} Inner;

/* The base APK: the only one with classes.dex, else the one named base.apk (APKM, SAI), else the only dex
 * holder whose name is not a split's (XAPK names it <package>.apk; feature splits also carry dex). */
static int pick_base(const Inner *in, int n) {
    int dex = 0, last = -1;
    for (int i = 0; i < n; i++)
        if (in[i].has_dex) dex++, last = i;
    if (dex == 1) return last;
    for (int i = 0; i < n; i++)
        if (in[i].has_dex && !strcasecmp(base_name(in[i].entry->name), "base.apk")) return i;
    int found = -1;
    for (int i = 0; i < n; i++) {
        const char *b = base_name(in[i].entry->name);
        if (!in[i].has_dex || !strncasecmp(b, "split", 5) || !strncasecmp(b, "config.", 7)) continue;
        if (found >= 0) return -1;
        found = i;
    }
    return found;
}

static bool merge(const char *bundle, const char *out, const char *dir, char *err, size_t errlen) {
    ZipArchive *z = zip_open(bundle);
    if (!z) {
        snprintf(err, errlen, "not a zip archive");
        return false;
    }
    Inner *in = NULL;
    int n = 0;
    bool ok = true;
    for (size_t i = 0; ok && i < zip_count(z); i++) {
        const ZipEntry *e = zip_entry_at(z, i);
        if (!has_suffix_ci(e->name, ".apk")) continue;
        if (e->flags & 1) {
            snprintf(err, errlen, "the bundle is encrypted (export it unencrypted, for example as .apks)");
            ok = false;
            break;
        }
        in = sa_realloc(in, (size_t)(n + 1) * sizeof *in);
        Inner *x = &in[n];
        memset(x, 0, sizeof *x);
        x->entry = e;
        x->tmp = sa_sprintf("%s/part%d.tmp", dir, n);
        n++;
        FILE *f = fopen(x->tmp, "wb");
        ok = f && zip_extract_to_file(z, e, f);
        if (f && fclose(f) != 0) ok = false;
        if (ok) x->zip = zip_open(x->tmp);
        if (!ok || !x->zip) {
            snprintf(err, errlen, "cannot extract %s (is the card full?)", e->name);
            ok = false;
            break;
        }
        x->has_dex = zip_find(x->zip, "classes.dex") != NULL;
    }
    int base = -1;
    if (ok && n == 0) {
        snprintf(err, errlen, "no APKs inside");
        ok = false;
    }
    if (ok && (base = pick_base(in, n)) < 0) {
        snprintf(err, errlen, "cannot tell which of its %d APKs is the base", n);
        ok = false;
    }
    if (ok) {
        char *part = sa_sprintf("%s.part", out);
        ZipWriter *w = zipw_create(part);
        ok = w != NULL;
        SaMap names = {0};
        ZipArchive *b = ok ? in[base].zip : NULL;
        /* The base whole, minus its signature: nothing here verifies it, and it would not cover the merge. */
        for (size_t i = 0; ok && i < zip_count(b); i++) {
            const ZipEntry *e = zip_entry_at(b, i);
            if (!strncmp(e->name, "META-INF/", 9) || sa_map_has(&names, e->name)) continue;
            sa_map_put(&names, e->name, NULL);
            ok = zipw_copy_entry(w, b, e);
        }
        int merged = 0;
        for (int k = 0; ok && k < n; k++) {
            if (k == base) continue;
            int before = (int)names.count;
            for (size_t i = 0; ok && i < zip_count(in[k].zip); i++) {
                const ZipEntry *e = zip_entry_at(in[k].zip, i);
                if ((strncmp(e->name, "lib/", 4) && strncmp(e->name, "assets/", 7)) || sa_map_has(&names, e->name))
                    continue;
                sa_map_put(&names, e->name, NULL);
                ok = zipw_copy_entry(w, in[k].zip, e);
            }
            if ((int)names.count > before) merged++;
        }
        if (w && !zipw_finish(w)) ok = false;
        sa_map_free(&names, NULL);
        if (ok) {
            remove(out);
            ok = rename(part, out) == 0;
        }
        if (!ok) {
            remove(part);
            snprintf(err, errlen, "cannot write the merged APK (is the card full?)");
        } else {
            LOGI("%s: base %s, lib/ and assets/ from %d of %d splits -> %s", base_name(bundle), in[base].entry->name,
                 merged, n - 1, out);
        }
        free(part);
    }
    for (int i = 0; i < n; i++) {
        zip_close(in[i].zip);
        remove(in[i].tmp);
        free(in[i].tmp);
    }
    free(in);
    zip_close(z);
    return ok;
}

char *bundle_resolve(const char *path, char *err, size_t errlen) {
    if (errlen) err[0] = 0;
    if (!bundle_is_bundle(path)) return sa_strdup(path);
    struct stat src;
    if (stat(path, &src) != 0) {
        snprintf(err, errlen, "cannot read %s", path);
        return NULL;
    }
    const char *slash = strrchr(path, '/');
    char *dir = slash ? sa_sprintf("%.*s/" CACHE_DIR, (int)(slash - path), path) : sa_strdup(CACHE_DIR);
    char *out = sa_sprintf("%s/%s.apk", dir, base_name(path));
    /* The sidecar names the bundle the merge came from (size and mtime), so a replaced bundle is merged again. */
    char *stamp_path = sa_sprintf("%s.src", out);
    char *stamp = sa_sprintf("%lld %lld\n", (long long)src.st_size, (long long)src.st_mtime);
    size_t len = 0;
    char *old = (char *)sa_read_file(stamp_path, &len);
    struct stat st;
    bool fresh = old && len == strlen(stamp) && !memcmp(old, stamp, len) && stat(out, &st) == 0 && st.st_size > 0;
    free(old);
    if (fresh) {
        free(stamp_path);
        free(stamp);
        free(dir);
        return out;
    }
    sa_mkdirs(dir);
    remove(stamp_path);
    LOGI("merging %s", path);
    uint64_t t0 = sa_time_ns();
    bool ok = merge(path, out, dir, err, errlen);
    free(dir);
    if (ok) {
        FILE *f = fopen(stamp_path, "wb");
        if (f) {
            fputs(stamp, f);
            fclose(f);
        }
    }
    free(stamp_path);
    free(stamp);
    if (!ok) {
        LOGE("%s: %s", path, err);
        free(out);
        return NULL;
    }
    LOGI("merged in %lld ms", (long long)((sa_time_ns() - t0) / 1000000));
    return out;
}
