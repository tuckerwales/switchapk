/*
 * TrueType text rendering on top of stb_truetype.
 *
 * Axis-aligned text (the common case) is drawn from a cache of coverage masks
 * rendered at device resolution with quarter-pixel horizontal positioning.
 * Rotated or skewed text goes through the glyph outlines and the path filler,
 * so every Canvas transform works.
 */
#include "gfx.h"
#ifdef __SWITCH__
#include <switch.h>
#endif

#include <math.h>
#include <pthread.h>

#define STB_TRUETYPE_IMPLEMENTATION
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-function"
#define STBTT_STATIC
#include "stb/stb_truetype.h"
#pragma GCC diagnostic pop

#define LOG_TAG "gfx-font"

struct GfxFont {
    stbtt_fontinfo info;
    uint8_t *data;
    bool owned;
    float units_per_em;
    int ascent, descent, line_gap; /* font units */
};

#define MAX_FALLBACKS 8
static GfxFont *g_regular, *g_bold;
static GfxFont *g_fallbacks[MAX_FALLBACKS];
static int g_nfallbacks;
static pthread_mutex_t g_font_lock = PTHREAD_MUTEX_INITIALIZER;

GfxFont *gfx_font_load(const uint8_t *data, size_t len, bool take_ownership) {
    if (!data || len < 12) return NULL;
    int off = stbtt_GetFontOffsetForIndex(data, 0);
    if (off < 0) return NULL;
    GfxFont *f = sa_calloc(1, sizeof *f);
    if (!stbtt_InitFont(&f->info, data, off)) {
        free(f);
        return NULL;
    }
    f->data = (uint8_t *)data;
    f->owned = take_ownership;
    int upem = ttUSHORT((uint8_t *)data + f->info.head + 18);
    f->units_per_em = upem > 0 ? (float)upem : 2048.0f;
    stbtt_GetFontVMetrics(&f->info, &f->ascent, &f->descent, &f->line_gap);
    return f;
}

static GfxFont *load_file(const char *path) {
    size_t len;
    uint8_t *d = sa_read_file(path, &len);
    if (!d) return NULL;
    GfxFont *f = gfx_font_load(d, len, true);
    if (!f) free(d);
    return f;
}

static GfxFont *load_first(const char *const *paths) {
    for (int i = 0; paths[i]; i++) {
        GfxFont *f = load_file(paths[i]);
        if (f) {
            LOGD("loaded font %s", paths[i]);
            return f;
        }
    }
    return NULL;
}

void gfx_font_register_default(GfxFont *regular, GfxFont *bold) {
    g_regular = regular;
    g_bold = bold ? bold : regular;
}

void gfx_font_add_fallback(GfxFont *f) {
    if (f && g_nfallbacks < MAX_FALLBACKS) g_fallbacks[g_nfallbacks++] = f;
}

bool gfx_font_init_default(void) {
    if (g_regular) return true;
    static const char *const regular[] = {
        "romfs:/fonts/Roboto-Regular.ttf",
        "/system/fonts/Roboto-Regular.ttf",
        "/usr/share/fonts/truetype/roboto/unhinted/RobotoTTF/Roboto-Regular.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
        "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
        "/Library/Fonts/Arial.ttf",
        "C:/Windows/Fonts/arial.ttf",
        NULL,
    };
    static const char *const bold[] = {
        "romfs:/fonts/Roboto-Bold.ttf",
        "/system/fonts/Roboto-Bold.ttf",
        "/usr/share/fonts/truetype/roboto/unhinted/RobotoTTF/Roboto-Bold.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
        "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf",
        NULL,
    };
    static const char *const cjk[] = {
        "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
        "/usr/share/fonts/truetype/fonts-japanese-gothic.ttf",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        NULL,
    };
    GfxFont *r = load_first(regular);
#ifdef __SWITCH__
    if (!r) {
        /* The system's shared fonts (pl service): Standard for Latin, then CJK and symbols. */
        PlFontData fd;
        if (R_SUCCEEDED(plGetSharedFontByType(&fd, PlSharedFontType_Standard))) {
            r = gfx_font_load((const uint8_t *)fd.address, fd.size, false);
            static const PlSharedFontType extra[] = {
                PlSharedFontType_ChineseSimplified, PlSharedFontType_ExtChineseSimplified,
                PlSharedFontType_ChineseTraditional, PlSharedFontType_KO, PlSharedFontType_NintendoExt,
            };
            for (size_t i = 0; r && i < sizeof extra / sizeof extra[0]; i++) {
                if (R_SUCCEEDED(plGetSharedFontByType(&fd, extra[i]))) {
                    gfx_font_add_fallback(gfx_font_load((const uint8_t *)fd.address, fd.size, false));
                }
            }
            if (r) {
                gfx_font_register_default(r, load_first(bold));
                return true;
            }
        }
    }
#endif
    if (!r) {
        LOGW("no system font found; text will not render");
        return false;
    }
    gfx_font_register_default(r, load_first(bold));
    gfx_font_add_fallback(load_first(cjk));
    return true;
}

/*
 * Android's monospace family is Droid Sans Mono. The Switch has no monospace shared font, so the NRO
 * bundles it in romfs (fetched by tools/fetch_toolchains.py sdk); the host build reads the same copy
 * from build/toolchains, then system monospace fonts. Without one, monospace is the default font.
 */
GfxFont *gfx_font_monospace(void) {
    static bool tried;
    static GfxFont *mono;
    if (!tried) {
        tried = true;
        static const char *const paths[] = {
            "romfs:/fonts/DroidSansMono.ttf",
            "/system/fonts/DroidSansMono.ttf",
            "build/toolchains/fonts/DroidSansMono.ttf",
            "/usr/share/fonts/truetype/droid/DroidSansMono.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansMono-Regular.ttf",
            "/usr/share/fonts/truetype/freefont/FreeMono.ttf",
            NULL,
        };
        mono = load_first(paths);
        if (!mono) LOGW("no monospace font found; monospace text uses the default font");
    }
    return mono ? mono : gfx_font_default(false);
}

GfxFont *gfx_font_default(bool bold) {
    if (!g_regular) gfx_font_init_default();
    return bold ? g_bold : g_regular;
}

static inline float font_scale(GfxFont *f, float size) { return size / f->units_per_em; }

void gfx_font_metrics(GfxFont *f, float size, float *ascent, float *descent, float *leading) {
    if (!f) f = gfx_font_default(false);
    if (!f) {
        *ascent = -size * 0.93f;
        *descent = size * 0.24f;
        *leading = 0;
        return;
    }
    float s = font_scale(f, size);
    *ascent = -(float)f->ascent * s;
    *descent = -(float)f->descent * s;
    *leading = (float)f->line_gap * s;
}

/* Resolves a code point to (font, glyph), consulting the fallback chain. */
static GfxFont *resolve_glyph(GfxFont *f, uint32_t cp, int *glyph) {
    int g = stbtt_FindGlyphIndex(&f->info, (int)cp);
    if (g || cp < 0x80) {
        *glyph = g;
        return f;
    }
    for (int i = 0; i < g_nfallbacks; i++) {
        int fg = stbtt_FindGlyphIndex(&g_fallbacks[i]->info, (int)cp);
        if (fg) {
            *glyph = fg;
            return g_fallbacks[i];
        }
    }
    *glyph = 0;
    return f;
}

static uint32_t next_cp(const uint16_t *text, int len, int *i) {
    uint32_t c = text[*i];
    (*i)++;
    if (c >= 0xD800 && c < 0xDC00 && *i < len && text[*i] >= 0xDC00 && text[*i] < 0xE000) {
        c = 0x10000 + ((c - 0xD800) << 10) + (text[*i] - 0xDC00);
        (*i)++;
    }
    return c;
}

static bool is_invisible(uint32_t cp) {
    return cp == '\n' || cp == '\r' || cp == 0x200B || cp == 0x200C || cp == 0x200D || cp == 0xFEFF ||
           (cp >= 0xFE00 && cp <= 0xFE0F);
}

/* Advance of a code point in pixels at `size`, plus kerning with the previous glyph. */
static float advance_of(GfxFont *f, float size, uint32_t cp, GfxFont **out_font, int *out_glyph) {
    if (is_invisible(cp)) {
        *out_font = NULL;
        *out_glyph = 0;
        return 0;
    }
    int g;
    GfxFont *gf = resolve_glyph(f, cp, &g);
    int adv, lsb;
    stbtt_GetGlyphHMetrics(&gf->info, g, &adv, &lsb);
    *out_font = gf;
    *out_glyph = g;
    return (float)adv * font_scale(gf, size);
}

float gfx_measure_text(GfxFont *f, float size, const uint16_t *text, int len, float *widths) {
    if (!f) f = gfx_font_default(false);
    if (widths)
        for (int k = 0; k < len; k++) widths[k] = 0;
    if (!f) return (float)len * size * 0.5f;
    float total = 0;
    GfxFont *prev_font = NULL;
    int prev_glyph = 0;
    for (int i = 0; i < len;) {
        int start = i;
        uint32_t cp = next_cp(text, len, &i);
        GfxFont *gf;
        int g;
        float adv = advance_of(f, size, cp, &gf, &g);
        if (gf && gf == prev_font && prev_glyph && g && (gf->info.kern || gf->info.gpos))
            adv += (float)stbtt_GetGlyphKernAdvance(&gf->info, prev_glyph, g) * font_scale(gf, size);
        if (widths) widths[start] = adv;
        total += adv;
        prev_font = gf;
        prev_glyph = g;
    }
    return total;
}

/* ---- glyph mask cache ---------------------------------------------------------- */

typedef struct GlyphEntry {
    GfxFont *font;
    int glyph;
    int size_q;  /* device pixel size * 4 */
    int sub;     /* quarter-pixel x phase */
    bool bold;
    int x0, y0, w, h;
    uint8_t *mask;
    struct GlyphEntry *next;
} GlyphEntry;

#define GLYPH_BUCKETS 1024
#define GLYPH_CACHE_MAX (8u << 20)
static GlyphEntry *g_glyphs[GLYPH_BUCKETS];
static size_t g_glyph_bytes;

static void glyph_cache_clear(void) {
    for (int i = 0; i < GLYPH_BUCKETS; i++) {
        GlyphEntry *e = g_glyphs[i];
        while (e) {
            GlyphEntry *n = e->next;
            free(e->mask);
            free(e);
            e = n;
        }
        g_glyphs[i] = NULL;
    }
    g_glyph_bytes = 0;
}

static GlyphEntry *glyph_get(GfxFont *f, int glyph, int size_q, int sub, bool bold) {
    uint32_t h = ((uint32_t)(uintptr_t)f * 31u) ^ ((uint32_t)glyph * 2654435761u) ^ ((uint32_t)size_q * 40503u) ^
                 ((uint32_t)sub << 7) ^ (bold ? 0x5bd1e995u : 0);
    h %= GLYPH_BUCKETS;
    for (GlyphEntry *e = g_glyphs[h]; e; e = e->next)
        if (e->font == f && e->glyph == glyph && e->size_q == size_q && e->sub == sub && e->bold == bold) return e;

    if (g_glyph_bytes > GLYPH_CACHE_MAX) glyph_cache_clear();
    float px = (float)size_q / 4.0f;
    float scale = px / f->units_per_em;
    float shift = (float)sub / 4.0f;
    int x0, y0, x1, y1;
    stbtt_GetGlyphBitmapBoxSubpixel(&f->info, glyph, scale, scale, shift, 0, &x0, &y0, &x1, &y1);
    GlyphEntry *e = sa_calloc(1, sizeof *e);
    e->font = f;
    e->glyph = glyph;
    e->size_q = size_q;
    e->sub = sub;
    e->bold = bold;
    int w = x1 - x0, hh = y1 - y0;
    if (w > 0 && hh > 0 && w < 4096 && hh < 4096) {
        int extra = bold ? (int)ceilf(px / 24.0f) : 0; /* fake bold: horizontal dilation */
        int bw = w + extra;
        uint8_t *m = sa_calloc((size_t)bw * (size_t)hh, 1);
        stbtt_MakeGlyphBitmapSubpixel(&f->info, m, w, hh, bw, scale, scale, shift, 0, glyph);
        if (extra) {
            for (int y = 0; y < hh; y++) {
                uint8_t *row = m + (size_t)y * (size_t)bw;
                for (int x = bw - 1; x >= 0; x--) {
                    int v = row[x];
                    for (int k = 1; k <= extra && x - k >= 0; k++)
                        if (row[x - k] > v) v = row[x - k];
                    row[x] = (uint8_t)v;
                }
            }
        }
        e->x0 = x0;
        e->y0 = y0;
        e->w = bw;
        e->h = hh;
        e->mask = m;
        g_glyph_bytes += (size_t)bw * (size_t)hh + sizeof *e;
    }
    e->next = g_glyphs[h];
    g_glyphs[h] = e;
    return e;
}

/* ---- outline path for transformed text -------------------------------------------- */

typedef struct {
    uint8_t *verbs;
    float *pts;
    int nv, np, cv, cp;
} PathBuf;

static void pb_verb(PathBuf *pb, int v, const float *p, int n) {
    if (pb->nv == pb->cv) {
        pb->cv = pb->cv ? pb->cv * 2 : 64;
        pb->verbs = sa_realloc(pb->verbs, (size_t)pb->cv);
    }
    if (pb->np + n > pb->cp) {
        while (pb->np + n > pb->cp) pb->cp = pb->cp ? pb->cp * 2 : 256;
        pb->pts = sa_realloc(pb->pts, (size_t)pb->cp * sizeof(float));
    }
    pb->verbs[pb->nv++] = (uint8_t)v;
    memcpy(pb->pts + pb->np, p, (size_t)n * sizeof(float));
    pb->np += n;
}

/* Appends a glyph outline at pen position (ox, oy) in text space (y down). */
static void append_outline(PathBuf *pb, GfxFont *f, int glyph, float size, float ox, float oy, float skew) {
    stbtt_vertex *v;
    int n = stbtt_GetGlyphShape(&f->info, glyph, &v);
    float s = font_scale(f, size);
    bool open = false;
#define TX(X, Y) (ox + (float)(X) * s + skew * (-(float)(Y) * s))
#define TY(Y) (oy - (float)(Y) * s)
    for (int i = 0; i < n; i++) {
        float p[6];
        switch (v[i].type) {
        case STBTT_vmove:
            if (open) pb_verb(pb, GFX_VERB_CLOSE, p, 0);
            p[0] = TX(v[i].x, v[i].y);
            p[1] = TY(v[i].y);
            pb_verb(pb, GFX_VERB_MOVE, p, 2);
            open = true;
            break;
        case STBTT_vline:
            p[0] = TX(v[i].x, v[i].y);
            p[1] = TY(v[i].y);
            pb_verb(pb, GFX_VERB_LINE, p, 2);
            break;
        case STBTT_vcurve:
            p[0] = TX(v[i].cx, v[i].cy);
            p[1] = TY(v[i].cy);
            p[2] = TX(v[i].x, v[i].y);
            p[3] = TY(v[i].y);
            pb_verb(pb, GFX_VERB_QUAD, p, 4);
            break;
        case STBTT_vcubic:
            p[0] = TX(v[i].cx, v[i].cy);
            p[1] = TY(v[i].cy);
            p[2] = TX(v[i].cx1, v[i].cy1);
            p[3] = TY(v[i].cy1);
            p[4] = TX(v[i].x, v[i].y);
            p[5] = TY(v[i].y);
            pb_verb(pb, GFX_VERB_CUBIC, p, 6);
            break;
        }
    }
    if (open) pb_verb(pb, GFX_VERB_CLOSE, NULL, 0);
#undef TX
#undef TY
    stbtt_FreeShape(&f->info, v);
}

void gfx_draw_text(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, GfxFont *f, float size, const uint16_t *text,
                   int len, float x, float y, float skew_x, bool fake_bold, const GfxPaint *p) {
    if (!f) f = gfx_font_default(false);
    if (!f || len <= 0 || size <= 0) return;
    pthread_mutex_lock(&g_font_lock);

    bool axis = fabsf(m->b) < 1e-6f && fabsf(m->c) < 1e-6f && m->a > 0 && m->d > 0 &&
                fabsf(m->a - m->d) < 1e-4f * m->a && skew_x == 0.0f && p->style == GFX_FILL && size * m->a < 256.0f;

    if (axis) {
        float dsize = size * m->a;
        int size_q = (int)lrintf(dsize * 4.0f);
        if (size_q < 1) size_q = 1;
        float pen_x = m->a * x + m->e, pen_y = m->d * y + m->f;
        int base_y = (int)lrintf(pen_y);
        GfxFont *prev_font = NULL;
        int prev_glyph = 0;
        for (int i = 0; i < len;) {
            uint32_t cp = next_cp(text, len, &i);
            GfxFont *gf;
            int g;
            float adv = advance_of(f, dsize, cp, &gf, &g);
            if (!gf) continue;
            if (gf == prev_font && prev_glyph && g && (gf->info.kern || gf->info.gpos))
                pen_x += (float)stbtt_GetGlyphKernAdvance(&gf->info, prev_glyph, g) * font_scale(gf, dsize);
            float fx = floorf(pen_x);
            int sub = (int)((pen_x - fx) * 4.0f) & 3;
            GlyphEntry *e = glyph_get(gf, g, size_q, sub, fake_bold);
            if (e->mask) gfx_blit_mask(t, clip, e->mask, e->w, e->h, e->w, (int)fx + e->x0, base_y + e->y0, p);
            pen_x += adv;
            prev_font = gf;
            prev_glyph = g;
        }
        pthread_mutex_unlock(&g_font_lock);
        return;
    }

    /* General transform: build one path with every glyph outline. */
    PathBuf pb = {0};
    float pen = x;
    GfxFont *prev_font = NULL;
    int prev_glyph = 0;
    GfxFont *path_font = NULL;
    for (int i = 0; i < len;) {
        uint32_t cp = next_cp(text, len, &i);
        GfxFont *gf;
        int g;
        float adv = advance_of(f, size, cp, &gf, &g);
        if (!gf) continue;
        if (gf == prev_font && prev_glyph && g && (gf->info.kern || gf->info.gpos))
            pen += (float)stbtt_GetGlyphKernAdvance(&gf->info, prev_glyph, g) * font_scale(gf, size);
        append_outline(&pb, gf, g, size, pen, y, skew_x);
        path_font = gf;
        pen += adv;
        prev_font = gf;
        prev_glyph = g;
    }
    pthread_mutex_unlock(&g_font_lock);
    (void)path_font;
    if (pb.nv) {
        GfxPathData pd = {pb.verbs, pb.nv, pb.pts, pb.np, GFX_FILL_WINDING};
        GfxPaint pp = *p;
        if (fake_bold) {
            pp.style = pp.style == GFX_STROKE ? GFX_STROKE : GFX_FILL_AND_STROKE;
            pp.stroke_width += size / 24.0f;
        }
        gfx_draw_path(t, m, clip, &pd, &pp);
    }
    free(pb.verbs);
    free(pb.pts);
}

/* Returns the text outline as a path (for Paint.getTextPath). Caller frees verbs/pts. */
void gfx_text_path(GfxFont *f, float size, const uint16_t *text, int len, float x, float y, uint8_t **verbs, int *nverbs,
                   float **pts, int *npts) {
    if (!f) f = gfx_font_default(false);
    PathBuf pb = {0};
    if (f) {
        float pen = x;
        for (int i = 0; i < len;) {
            uint32_t cp = next_cp(text, len, &i);
            GfxFont *gf;
            int g;
            float adv = advance_of(f, size, cp, &gf, &g);
            if (!gf) continue;
            append_outline(&pb, gf, g, size, pen, y, 0);
            pen += adv;
        }
    }
    *verbs = pb.verbs;
    *nverbs = pb.nv;
    *pts = pb.pts;
    *npts = pb.np;
}

/* Tight bounds of the inked area of the text, in text space. */
void gfx_text_bounds(GfxFont *f, float size, const uint16_t *text, int len, float *l, float *t, float *r, float *b) {
    if (!f) f = gfx_font_default(false);
    float minx = 1e30f, miny = 1e30f, maxx = -1e30f, maxy = -1e30f, pen = 0;
    if (f) {
        for (int i = 0; i < len;) {
            uint32_t cp = next_cp(text, len, &i);
            GfxFont *gf;
            int g;
            float adv = advance_of(f, size, cp, &gf, &g);
            if (!gf) continue;
            int x0, y0, x1, y1;
            if (stbtt_GetGlyphBox(&gf->info, g, &x0, &y0, &x1, &y1)) {
                float s = font_scale(gf, size);
                if (pen + x0 * s < minx) minx = pen + x0 * s;
                if (pen + x1 * s > maxx) maxx = pen + x1 * s;
                if (-y1 * s < miny) miny = -y1 * s;
                if (-y0 * s > maxy) maxy = -y0 * s;
            }
            pen += adv;
        }
    }
    if (minx > maxx) minx = maxx = miny = maxy = 0;
    *l = minx;
    *t = miny;
    *r = maxx;
    *b = maxy;
}

bool gfx_font_has_glyph(GfxFont *f, uint32_t cp) {
    if (!f) f = gfx_font_default(false);
    int g;
    return f && resolve_glyph(f, cp, &g) && g != 0;
}
