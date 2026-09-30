/*
 * Anti-aliased scanline rasterizer, stroker, shaders and compositing.
 */
#include "gfx.h"

#include <math.h>

#define SS 4 /* vertical sub-scanlines per pixel for anti-aliasing */
#define PI_F 3.14159265358979f

/* ======================================================================
 * Matrix
 * ====================================================================== */

void gfx_matrix_identity(GfxMatrix *m) {
    m->a = 1;
    m->b = 0;
    m->c = 0;
    m->d = 1;
    m->e = 0;
    m->f = 0;
}

void gfx_matrix_mul(GfxMatrix *out, const GfxMatrix *x, const GfxMatrix *y) {
    GfxMatrix r;
    r.a = x->a * y->a + x->c * y->b;
    r.b = x->b * y->a + x->d * y->b;
    r.c = x->a * y->c + x->c * y->d;
    r.d = x->b * y->c + x->d * y->d;
    r.e = x->a * y->e + x->c * y->f + x->e;
    r.f = x->b * y->e + x->d * y->f + x->f;
    *out = r;
}

bool gfx_matrix_invert(const GfxMatrix *m, GfxMatrix *out) {
    float det = m->a * m->d - m->b * m->c;
    if (fabsf(det) < 1e-12f) return false;
    float id = 1.0f / det;
    GfxMatrix r;
    r.a = m->d * id;
    r.b = -m->b * id;
    r.c = -m->c * id;
    r.d = m->a * id;
    r.e = (m->c * m->f - m->d * m->e) * id;
    r.f = (m->b * m->e - m->a * m->f) * id;
    *out = r;
    return true;
}

void gfx_matrix_from_android(GfxMatrix *m, const float v[9]) {
    m->a = v[0];
    m->c = v[1];
    m->e = v[2];
    m->b = v[3];
    m->d = v[4];
    m->f = v[5];
}

static float matrix_scale(const GfxMatrix *m) {
    float det = fabsf(m->a * m->d - m->b * m->c);
    float s = sqrtf(det);
    return s > 1e-6f ? s : 1e-6f;
}

/* ======================================================================
 * Polygon accumulation (device space)
 * ====================================================================== */

typedef struct {
    float x0, y0, x1, y1;
    float slope; /* dx/dy */
    int dir;
} Edge;

typedef struct {
    Edge *edges;
    int n, cap;
    float cx, cy;   /* current point */
    float sx, sy;   /* contour start */
    bool open;
    float minx, miny, maxx, maxy;
} Poly;

static void poly_init(Poly *p) {
    memset(p, 0, sizeof *p);
    p->minx = p->miny = 1e30f;
    p->maxx = p->maxy = -1e30f;
}

static void poly_free(Poly *p) { free(p->edges); }

static void poly_edge(Poly *p, float x0, float y0, float x1, float y1) {
    if (x0 < p->minx) p->minx = x0;
    if (x1 < p->minx) p->minx = x1;
    if (x0 > p->maxx) p->maxx = x0;
    if (x1 > p->maxx) p->maxx = x1;
    if (y0 < p->miny) p->miny = y0;
    if (y1 < p->miny) p->miny = y1;
    if (y0 > p->maxy) p->maxy = y0;
    if (y1 > p->maxy) p->maxy = y1;
    if (y0 == y1) return;
    if (p->n == p->cap) {
        p->cap = p->cap ? p->cap * 2 : 64;
        p->edges = sa_realloc(p->edges, (size_t)p->cap * sizeof(Edge));
    }
    Edge *e = &p->edges[p->n++];
    if (y0 < y1) {
        e->x0 = x0;
        e->y0 = y0;
        e->x1 = x1;
        e->y1 = y1;
        e->dir = 1;
    } else {
        e->x0 = x1;
        e->y0 = y1;
        e->x1 = x0;
        e->y1 = y0;
        e->dir = -1;
    }
    e->slope = (e->x1 - e->x0) / (e->y1 - e->y0);
}

static void poly_close(Poly *p) {
    if (p->open) {
        poly_edge(p, p->cx, p->cy, p->sx, p->sy);
        p->open = false;
    }
}

static void poly_move(Poly *p, float x, float y) {
    poly_close(p);
    p->cx = p->sx = x;
    p->cy = p->sy = y;
    p->open = true;
}

static void poly_line(Poly *p, float x, float y) {
    if (!p->open) poly_move(p, p->cx, p->cy);
    poly_edge(p, p->cx, p->cy, x, y);
    p->cx = x;
    p->cy = y;
}

/* ======================================================================
 * Pixel compositing
 * ====================================================================== */

typedef struct {
    const GfxPaint *paint;
    GfxMatrix inv;     /* device -> shader space */
    bool has_inv;
    uint32_t color;    /* solid color (after color filter) */
    float alpha_mul;   /* paint alpha multiplier for shaders */
} Shade;

static inline uint32_t apply_color_filter(const GfxPaint *p, uint32_t c) {
    if (!p->has_color_filter) return c;
    uint32_t f = p->cf_color;
    int sa = c >> 24, sr = (c >> 16) & 255, sg = (c >> 8) & 255, sb = c & 255;
    int fa = f >> 24, fr = (f >> 16) & 255, fg = (f >> 8) & 255, fb = f & 255;
    switch (p->cf_mode) {
    case GFX_XFER_SRC_IN: /* tint: take filter color, keep source alpha */
        return gfx_argb(sa * fa / 255, fr, fg, fb);
    case GFX_XFER_SRC_ATOP:
    case GFX_XFER_SRC_OVER:
        return gfx_argb(sa, sr + (fr - sr) * fa / 255, sg + (fg - sg) * fa / 255, sb + (fb - sb) * fa / 255);
    case GFX_XFER_MULTIPLY:
        return gfx_argb(sa * fa / 255, sr * fr / 255, sg * fg / 255, sb * fb / 255);
    case GFX_XFER_SRC:
        return f;
    case GFX_XFER_DST:
        return c;
    default:
        return gfx_argb(sa, sr + (fr - sr) * fa / 255, sg + (fg - sg) * fa / 255, sb + (fb - sb) * fa / 255);
    }
}

static inline uint32_t lerp_color(uint32_t a, uint32_t b, float t) {
    int aa = a >> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
    int ba = b >> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
    return gfx_argb((int)(aa + (ba - aa) * t + 0.5f), (int)(ar + (br - ar) * t + 0.5f), (int)(ag + (bg - ag) * t + 0.5f),
                    (int)(ab + (bb - ab) * t + 0.5f));
}

static float tile_coord(float t, int mode) {
    switch (mode) {
    case GFX_TILE_REPEAT: return t - floorf(t);
    case GFX_TILE_MIRROR: {
        float f = fmodf(fabsf(t), 2.0f);
        return f > 1 ? 2 - f : f;
    }
    default: return t < 0 ? 0 : (t > 1 ? 1 : t);
    }
}

static uint32_t gradient_at(const GfxShader *s, float t) {
    t = tile_coord(t, s->tile_x);
    int n = s->ncolors;
    if (n <= 0) return 0;
    if (n == 1) return s->colors[0];
    if (!s->positions) {
        float ft = t * (float)(n - 1);
        int i = (int)ft;
        if (i >= n - 1) return s->colors[n - 1];
        return lerp_color(s->colors[i], s->colors[i + 1], ft - (float)i);
    }
    if (t <= s->positions[0]) return s->colors[0];
    for (int i = 0; i < n - 1; i++) {
        float p0 = s->positions[i], p1 = s->positions[i + 1];
        if (t <= p1) {
            float d = p1 - p0;
            return lerp_color(s->colors[i], s->colors[i + 1], d > 0 ? (t - p0) / d : 0);
        }
    }
    return s->colors[n - 1];
}

static uint32_t sample_bitmap(const GfxTarget *b, float x, float y, bool filter, int tx, int ty) {
    if (b->w <= 0 || b->h <= 0) return 0;
    if (!filter) {
        float u = tile_coord(x / (float)b->w, tx) * (float)b->w;
        float v = tile_coord(y / (float)b->h, ty) * (float)b->h;
        int ix = (int)u, iy = (int)v;
        if (ix >= b->w) ix = b->w - 1;
        if (iy >= b->h) iy = b->h - 1;
        if (ix < 0) ix = 0;
        if (iy < 0) iy = 0;
        return b->px[iy * b->stride + ix];
    }
    float fx = x - 0.5f, fy = y - 0.5f;
    int x0 = (int)floorf(fx), y0 = (int)floorf(fy);
    float ax = fx - (float)x0, ay = fy - (float)y0;
    int xs[2] = {x0, x0 + 1}, ys[2] = {y0, y0 + 1};
    for (int i = 0; i < 2; i++) {
        if (tx == GFX_TILE_REPEAT) xs[i] = ((xs[i] % b->w) + b->w) % b->w;
        else xs[i] = xs[i] < 0 ? 0 : (xs[i] >= b->w ? b->w - 1 : xs[i]);
        if (ty == GFX_TILE_REPEAT) ys[i] = ((ys[i] % b->h) + b->h) % b->h;
        else ys[i] = ys[i] < 0 ? 0 : (ys[i] >= b->h ? b->h - 1 : ys[i]);
    }
    uint32_t c00 = b->px[ys[0] * b->stride + xs[0]], c10 = b->px[ys[0] * b->stride + xs[1]];
    uint32_t c01 = b->px[ys[1] * b->stride + xs[0]], c11 = b->px[ys[1] * b->stride + xs[1]];
    /* interpolate in premultiplied space to avoid dark fringes */
    float acc[4] = {0, 0, 0, 0};
    uint32_t cs[4] = {c00, c10, c01, c11};
    float ws[4] = {(1 - ax) * (1 - ay), ax * (1 - ay), (1 - ax) * ay, ax * ay};
    for (int i = 0; i < 4; i++) {
        float a = (float)(cs[i] >> 24) * ws[i];
        acc[0] += a;
        acc[1] += (float)((cs[i] >> 16) & 255) * a;
        acc[2] += (float)((cs[i] >> 8) & 255) * a;
        acc[3] += (float)(cs[i] & 255) * a;
    }
    if (acc[0] < 0.5f) return 0;
    return gfx_argb((int)(acc[0] + 0.5f), (int)(acc[1] / acc[0] + 0.5f), (int)(acc[2] / acc[0] + 0.5f),
                    (int)(acc[3] / acc[0] + 0.5f));
}

static uint32_t shader_color(const Shade *sh, int x, int y) {
    const GfxShader *s = sh->paint->shader;
    float px = (float)x + 0.5f, py = (float)y + 0.5f;
    float lx, ly;
    gfx_map(&sh->inv, px, py, &lx, &ly);
    uint32_t c;
    switch (s->type) {
    case GFX_SHADER_LINEAR: {
        float dx = s->x1 - s->x0, dy = s->y1 - s->y0;
        float len2 = dx * dx + dy * dy;
        float t = len2 > 0 ? ((lx - s->x0) * dx + (ly - s->y0) * dy) / len2 : 0;
        c = gradient_at(s, t);
        break;
    }
    case GFX_SHADER_RADIAL: {
        float dx = lx - s->x0, dy = ly - s->y0;
        c = gradient_at(s, s->r > 0 ? sqrtf(dx * dx + dy * dy) / s->r : 0);
        break;
    }
    case GFX_SHADER_SWEEP: {
        float ang = atan2f(ly - s->y0, lx - s->x0);
        if (ang < 0) ang += 2 * PI_F;
        GfxShader tmp = *s;
        tmp.tile_x = GFX_TILE_CLAMP;
        c = gradient_at(&tmp, ang / (2 * PI_F));
        break;
    }
    case GFX_SHADER_BITMAP:
        c = sample_bitmap(&s->bitmap, lx, ly, s->filter || sh->paint->filter, s->tile_x, s->tile_y);
        break;
    default: c = sh->color; break;
    }
    /* paint alpha modulates shader output */
    uint32_t a = (uint32_t)((float)(c >> 24) * sh->alpha_mul + 0.5f);
    c = (c & 0xffffff) | (a << 24);
    return apply_color_filter(sh->paint, c);
}

/* Composites source color s with coverage cov (0..256) onto *d using xfer mode. */
static inline void blend(uint32_t *d, uint32_t s, int cov, int xfer) {
    if (xfer == GFX_XFER_SRC_OVER) {
        int sa = (int)(s >> 24) * cov >> 8;
        if (sa <= 0) return;
        uint32_t dc = *d;
        int da = (int)(dc >> 24);
        if (sa >= 255) {
            *d = s | 0xff000000u;
            return;
        }
        if (da == 255) {
            int inv = 255 - sa;
            int r = (int)(((s >> 16) & 255) * sa + ((dc >> 16) & 255) * inv) / 255;
            int g = (int)(((s >> 8) & 255) * sa + ((dc >> 8) & 255) * inv) / 255;
            int b = (int)((s & 255) * sa + (dc & 255) * inv) / 255;
            *d = 0xff000000u | ((uint32_t)r << 16) | ((uint32_t)g << 8) | (uint32_t)b;
            return;
        }
        /* general non-premultiplied source-over */
        int oa = sa + da * (255 - sa) / 255;
        if (oa <= 0) {
            *d = 0;
            return;
        }
        int wd = da * (255 - sa) / 255;
        int r = (int)(((s >> 16) & 255) * sa + ((dc >> 16) & 255) * wd) / oa;
        int g = (int)(((s >> 8) & 255) * sa + ((dc >> 8) & 255) * wd) / oa;
        int b = (int)((s & 255) * sa + (dc & 255) * wd) / oa;
        *d = ((uint32_t)oa << 24) | ((uint32_t)r << 16) | ((uint32_t)g << 8) | (uint32_t)b;
        return;
    }
    /* general Porter-Duff in premultiplied floats */
    float sa = (float)(s >> 24) / 255.0f;
    float sr = (float)((s >> 16) & 255) / 255.0f * sa, sg = (float)((s >> 8) & 255) / 255.0f * sa,
          sb = (float)(s & 255) / 255.0f * sa;
    uint32_t dc = *d;
    float da = (float)(dc >> 24) / 255.0f;
    float dr = (float)((dc >> 16) & 255) / 255.0f * da, dg = (float)((dc >> 8) & 255) / 255.0f * da,
          db = (float)(dc & 255) / 255.0f * da;
    float ra, rr, rg, rb;
#define PD(fa_, fb_)                  \
    do {                              \
        float Fa = (fa_), Fb = (fb_); \
        ra = sa * Fa + da * Fb;       \
        rr = sr * Fa + dr * Fb;       \
        rg = sg * Fa + dg * Fb;       \
        rb = sb * Fa + db * Fb;       \
    } while (0)
    switch (xfer) {
    case GFX_XFER_CLEAR: PD(0, 0); break;
    case GFX_XFER_SRC: PD(1, 0); break;
    case GFX_XFER_DST: PD(0, 1); break;
    case GFX_XFER_DST_OVER: PD(1 - da, 1); break;
    case GFX_XFER_SRC_IN: PD(da, 0); break;
    case GFX_XFER_DST_IN: PD(0, sa); break;
    case GFX_XFER_SRC_OUT: PD(1 - da, 0); break;
    case GFX_XFER_DST_OUT: PD(0, 1 - sa); break;
    case GFX_XFER_SRC_ATOP: PD(da, 1 - sa); break;
    case GFX_XFER_DST_ATOP: PD(1 - da, sa); break;
    case GFX_XFER_XOR: PD(1 - da, 1 - sa); break;
    case GFX_XFER_ADD:
        ra = fminf(1, sa + da);
        rr = fminf(1, sr + dr);
        rg = fminf(1, sg + dg);
        rb = fminf(1, sb + db);
        break;
    case GFX_XFER_MULTIPLY:
        ra = sa + da - sa * da;
        rr = sr * dr + sr * (1 - da) + dr * (1 - sa);
        rg = sg * dg + sg * (1 - da) + dg * (1 - sa);
        rb = sb * db + sb * (1 - da) + db * (1 - sa);
        break;
    case GFX_XFER_SCREEN:
        ra = sa + da - sa * da;
        rr = sr + dr - sr * dr;
        rg = sg + dg - sg * dg;
        rb = sb + db - sb * db;
        break;
    case GFX_XFER_DARKEN:
        ra = sa + da - sa * da;
        rr = sr + dr - fmaxf(sr * da, dr * sa);
        rg = sg + dg - fmaxf(sg * da, dg * sa);
        rb = sb + db - fmaxf(sb * da, db * sa);
        break;
    case GFX_XFER_LIGHTEN:
        ra = sa + da - sa * da;
        rr = sr + dr - fminf(sr * da, dr * sa);
        rg = sg + dg - fminf(sg * da, dg * sa);
        rb = sb + db - fminf(sb * da, db * sa);
        break;
    default: PD(1, 1 - sa); break;
    }
#undef PD
    float c = (float)cov / 256.0f;
    ra = da + (ra - da) * c;
    rr = dr + (rr - dr) * c;
    rg = dg + (rg - dg) * c;
    rb = db + (rb - db) * c;
    if (ra <= 0.0005f) {
        *d = 0;
        return;
    }
    int A = (int)(ra * 255.0f + 0.5f);
    int R = (int)(rr / ra * 255.0f + 0.5f), G = (int)(rg / ra * 255.0f + 0.5f), B = (int)(rb / ra * 255.0f + 0.5f);
    if (A > 255) A = 255;
    if (R > 255) R = 255;
    if (G > 255) G = 255;
    if (B > 255) B = 255;
    *d = gfx_argb(A, R < 0 ? 0 : R, G < 0 ? 0 : G, B < 0 ? 0 : B);
}

static void shade_init(Shade *sh, const GfxPaint *p, const GfxMatrix *m) {
    sh->paint = p;
    sh->color = apply_color_filter(p, p->color);
    sh->alpha_mul = (float)(p->color >> 24) / 255.0f;
    sh->has_inv = false;
    if (p->shader) {
        GfxMatrix full;
        gfx_matrix_mul(&full, m, &p->shader->local);
        sh->has_inv = gfx_matrix_invert(&full, &sh->inv);
        if (!sh->has_inv) gfx_matrix_identity(&sh->inv);
    }
}

/* Composites a run of pixels with float coverage. */
static void composite_row(GfxTarget *t, const GfxClip *clip, const Shade *sh, int y, int x0, int x1, const float *cov) {
    uint32_t *row = t->px + (size_t)y * (size_t)t->stride;
    int xfer = sh->paint->xfer;
    bool shader = sh->paint->shader != NULL;
    for (int x = x0; x < x1; x++) {
        float c = cov[x - x0];
        if (c <= 0.002f) {
            continue;
        }
        int ci = c >= 1.0f ? 256 : (int)(c * 256.0f + 0.5f);
        if (clip->mask) {
            ci = gfx_clip_cov(clip, x, y, ci);
            if (!ci) continue;
        }
        uint32_t s = shader ? shader_color(sh, x, y) : sh->color;
        blend(&row[x], s, ci, xfer);
    }
}

/* ======================================================================
 * Scanline fill
 * ====================================================================== */

static int cmp_float(const void *a, const void *b) {
    float x = ((const float *)a)[0], y = ((const float *)b)[0];
    return x < y ? -1 : x > y ? 1 : 0;
}

static int cmp_edge_y(const void *a, const void *b) {
    float x = ((const Edge *)a)->y0, y = ((const Edge *)b)->y0;
    return x < y ? -1 : x > y ? 1 : 0;
}

static void add_span(float *cov, int base, int width, float xa, float xb, float w) {
    if (xb <= xa) return;
    float lo = (float)base, hi = (float)(base + width);
    if (xa < lo) xa = lo;
    if (xb > hi) xb = hi;
    if (xb <= xa) return;
    int ia = (int)floorf(xa), ib = (int)floorf(xb);
    if (ia == ib) {
        cov[ia - base] += (xb - xa) * w;
        return;
    }
    cov[ia - base] += ((float)(ia + 1) - xa) * w;
    for (int i = ia + 1; i < ib; i++) cov[i - base] += w;
    if (ib < base + width) cov[ib - base] += (xb - (float)ib) * w;
}

static void fill_poly(GfxTarget *t, const GfxClip *clip, Poly *p, int fill_type, bool aa, const Shade *sh) {
    poly_close(p);
    int cl = clip->l < 0 ? 0 : clip->l, ct = clip->t < 0 ? 0 : clip->t;
    int cr = clip->r > t->w ? t->w : clip->r, cb = clip->b > t->h ? t->h : clip->b;
    bool inverse = fill_type >= GFX_FILL_INVERSE_WINDING;
    bool evenodd = (fill_type & 1) != 0;
    int x0, x1, y0, y1;
    if (inverse) {
        x0 = cl;
        x1 = cr;
        y0 = ct;
        y1 = cb;
    } else {
        if (p->n == 0) return;
        x0 = (int)floorf(p->minx);
        x1 = (int)ceilf(p->maxx) + 1;
        y0 = (int)floorf(p->miny);
        y1 = (int)ceilf(p->maxy) + 1;
        if (x0 < cl) x0 = cl;
        if (x1 > cr) x1 = cr;
        if (y0 < ct) y0 = ct;
        if (y1 > cb) y1 = cb;
    }
    if (x0 >= x1 || y0 >= y1) return;
    int width = x1 - x0;
    float *cov = sa_malloc((size_t)width * sizeof(float));
    float *xs = sa_malloc((size_t)(p->n + 1) * 2 * sizeof(float));
    Edge **active = sa_malloc((size_t)(p->n + 1) * sizeof(Edge *));
    int nactive = 0, next_edge = 0;
    qsort(p->edges, (size_t)p->n, sizeof(Edge), cmp_edge_y);
    int subs = aa ? SS : 1;
    float w = 1.0f / (float)subs;
    for (int y = y0; y < y1; y++) {
        memset(cov, 0, (size_t)width * sizeof(float));
        for (int s = 0; s < subs; s++) {
            float sy = (float)y + ((float)s + 0.5f) / (float)subs;
            /* add newly active edges */
            while (next_edge < p->n && p->edges[next_edge].y0 <= sy) active[nactive++] = &p->edges[next_edge++];
            int nx = 0;
            for (int i = 0; i < nactive;) {
                Edge *e = active[i];
                if (e->y1 <= sy) {
                    active[i] = active[--nactive];
                    continue;
                }
                if (e->y0 <= sy) {
                    xs[nx * 2] = e->x0 + (sy - e->y0) * e->slope;
                    xs[nx * 2 + 1] = (float)e->dir;
                    nx++;
                }
                i++;
            }
            if (nx > 1) qsort(xs, (size_t)nx, 2 * sizeof(float), cmp_float);
            int wind = 0;
            float span_start = 0;
            bool inside = false;
            if (inverse) {
                /* coverage everywhere except inside spans */
                float prev = (float)x0;
                for (int i = 0; i < nx; i++) {
                    bool was = evenodd ? (wind & 1) : wind != 0;
                    wind += (int)xs[i * 2 + 1];
                    bool now = evenodd ? (wind & 1) : wind != 0;
                    if (!was && now) {
                        add_span(cov, x0, width, aa ? prev : floorf(prev + 0.5f), aa ? xs[i * 2] : floorf(xs[i * 2] + 0.5f), w);
                    } else if (was && !now) {
                        prev = xs[i * 2];
                    }
                }
                add_span(cov, x0, width, aa ? prev : floorf(prev + 0.5f), (float)x1, w);
                continue;
            }
            for (int i = 0; i < nx; i++) {
                wind += (int)xs[i * 2 + 1];
                bool now = evenodd ? (wind & 1) : wind != 0;
                if (now && !inside) {
                    span_start = xs[i * 2];
                    inside = true;
                } else if (!now && inside) {
                    inside = false;
                    float xa = span_start, xb = xs[i * 2];
                    if (!aa) {
                        xa = floorf(xa + 0.5f);
                        xb = floorf(xb + 0.5f);
                    }
                    add_span(cov, x0, width, xa, xb, w);
                }
            }
        }
        composite_row(t, clip, sh, y, x0, x1, cov);
    }
    free(cov);
    free(xs);
    free(active);
}

/* ======================================================================
 * Path construction helpers (local space) and flattening
 * ====================================================================== */

typedef struct {
    float *pts;   /* x,y pairs */
    int n, cap;
    int *starts;  /* contour start indices */
    bool *closed;
    int ncont, capc;
} Polyline;

static void pl_init(Polyline *pl) { memset(pl, 0, sizeof *pl); }

static void pl_free(Polyline *pl) {
    free(pl->pts);
    free(pl->starts);
    free(pl->closed);
}

static void pl_start(Polyline *pl) {
    if (pl->ncont == pl->capc) {
        pl->capc = pl->capc ? pl->capc * 2 : 8;
        pl->starts = sa_realloc(pl->starts, (size_t)pl->capc * sizeof(int));
        pl->closed = sa_realloc(pl->closed, (size_t)pl->capc * sizeof(bool));
    }
    pl->starts[pl->ncont] = pl->n;
    pl->closed[pl->ncont] = false;
    pl->ncont++;
}

static void pl_add(Polyline *pl, float x, float y) {
    if (pl->n == pl->cap) {
        pl->cap = pl->cap ? pl->cap * 2 : 64;
        pl->pts = sa_realloc(pl->pts, (size_t)pl->cap * 2 * sizeof(float));
    }
    pl->pts[pl->n * 2] = x;
    pl->pts[pl->n * 2 + 1] = y;
    pl->n++;
}

static int contour_len(const Polyline *pl, int c) {
    int end = c + 1 < pl->ncont ? pl->starts[c + 1] : pl->n;
    return end - pl->starts[c];
}

/* Flattens path data into polylines in local space with tolerance tol. */
static void flatten(const GfxPathData *pd, float tol, Polyline *pl) {
    float cx = 0, cy = 0, sx = 0, sy = 0;
    bool have = false;
    int pi = 0;
    for (int i = 0; i < pd->nverbs; i++) {
        int v = pd->verbs[i];
        switch (v) {
        case GFX_VERB_MOVE:
            if (pi + 2 > pd->npts) return;
            cx = sx = pd->pts[pi];
            cy = sy = pd->pts[pi + 1];
            pi += 2;
            pl_start(pl);
            pl_add(pl, cx, cy);
            have = true;
            break;
        case GFX_VERB_LINE:
            if (pi + 2 > pd->npts) return;
            if (!have) {
                pl_start(pl);
                pl_add(pl, cx, cy);
                have = true;
            }
            cx = pd->pts[pi];
            cy = pd->pts[pi + 1];
            pi += 2;
            pl_add(pl, cx, cy);
            break;
        case GFX_VERB_QUAD: {
            if (pi + 4 > pd->npts) return;
            if (!have) {
                pl_start(pl);
                pl_add(pl, cx, cy);
                have = true;
            }
            float x1 = pd->pts[pi], y1 = pd->pts[pi + 1], x2 = pd->pts[pi + 2], y2 = pd->pts[pi + 3];
            pi += 4;
            float dd = fabsf(cx - 2 * x1 + x2) + fabsf(cy - 2 * y1 + y2);
            int n = (int)ceilf(sqrtf(dd / tol) * 0.7f);
            if (n < 1) n = 1;
            if (n > 200) n = 200;
            for (int k = 1; k <= n; k++) {
                float tt = (float)k / (float)n, u = 1 - tt;
                pl_add(pl, u * u * cx + 2 * u * tt * x1 + tt * tt * x2, u * u * cy + 2 * u * tt * y1 + tt * tt * y2);
            }
            cx = x2;
            cy = y2;
            break;
        }
        case GFX_VERB_CUBIC: {
            if (pi + 6 > pd->npts) return;
            if (!have) {
                pl_start(pl);
                pl_add(pl, cx, cy);
                have = true;
            }
            float x1 = pd->pts[pi], y1 = pd->pts[pi + 1], x2 = pd->pts[pi + 2], y2 = pd->pts[pi + 3];
            float x3 = pd->pts[pi + 4], y3 = pd->pts[pi + 5];
            pi += 6;
            float dd = fabsf(cx - 2 * x1 + x2) + fabsf(cy - 2 * y1 + y2) + fabsf(x1 - 2 * x2 + x3) + fabsf(y1 - 2 * y2 + y3);
            int n = (int)ceilf(sqrtf(dd / tol) * 0.8f);
            if (n < 1) n = 1;
            if (n > 300) n = 300;
            for (int k = 1; k <= n; k++) {
                float tt = (float)k / (float)n, u = 1 - tt;
                float a = u * u * u, b = 3 * u * u * tt, c = 3 * u * tt * tt, d = tt * tt * tt;
                pl_add(pl, a * cx + b * x1 + c * x2 + d * x3, a * cy + b * y1 + c * y2 + d * y3);
            }
            cx = x3;
            cy = y3;
            break;
        }
        case GFX_VERB_CLOSE:
            if (have && pl->ncont) pl->closed[pl->ncont - 1] = true;
            cx = sx;
            cy = sy;
            have = false;
            break;
        default: return;
        }
    }
}

static void poly_from_polyline(Poly *p, const Polyline *pl, const GfxMatrix *m) {
    for (int c = 0; c < pl->ncont; c++) {
        int s = pl->starts[c], n = contour_len(pl, c);
        if (n < 2) continue;
        for (int i = 0; i < n; i++) {
            float x, y;
            gfx_map(m, pl->pts[(s + i) * 2], pl->pts[(s + i) * 2 + 1], &x, &y);
            if (i == 0) poly_move(p, x, y);
            else poly_line(p, x, y);
        }
        poly_close(p);
    }
}

/* ---- stroking --------------------------------------------------------------------------- */

/* Adds a convex polygon (local coords) to p, forcing a consistent (positive) orientation. */
static void add_convex(Poly *p, const GfxMatrix *m, const float *xy, int n) {
    float area = 0;
    for (int i = 0; i < n; i++) {
        int j = (i + 1) % n;
        area += xy[i * 2] * xy[j * 2 + 1] - xy[j * 2] * xy[i * 2 + 1];
    }
    float det = m->a * m->d - m->b * m->c;
    bool rev = (area * det) < 0;
    for (int k = 0; k < n; k++) {
        int i = rev ? n - 1 - k : k;
        float x, y;
        gfx_map(m, xy[i * 2], xy[i * 2 + 1], &x, &y);
        if (k == 0) poly_move(p, x, y);
        else poly_line(p, x, y);
    }
    poly_close(p);
}

static void add_circle(Poly *p, const GfxMatrix *m, float cx, float cy, float r, float scale) {
    int n = (int)ceilf(2 * PI_F * r * scale / 3.0f);
    if (n < 8) n = 8;
    if (n > 256) n = 256;
    float *xy = sa_malloc((size_t)n * 2 * sizeof(float));
    for (int i = 0; i < n; i++) {
        float a = 2 * PI_F * (float)i / (float)n;
        xy[i * 2] = cx + cosf(a) * r;
        xy[i * 2 + 1] = cy + sinf(a) * r;
    }
    add_convex(p, m, xy, n);
    free(xy);
}

static void stroke_polyline(Poly *p, const GfxMatrix *m, const Polyline *pl, const GfxPaint *paint, float scale) {
    float hw = paint->stroke_width * 0.5f;
    if (paint->stroke_width <= 0) hw = 0.5f / scale; /* hairline: one device pixel */
    for (int c = 0; c < pl->ncont; c++) {
        int s = pl->starts[c], n = contour_len(pl, c);
        const float *pt = pl->pts + s * 2;
        bool closed = pl->closed[c];
        /* drop duplicate consecutive points */
        float *q = sa_malloc((size_t)(n + 1) * 2 * sizeof(float));
        int qn = 0;
        for (int i = 0; i < n; i++) {
            if (qn && fabsf(q[(qn - 1) * 2] - pt[i * 2]) < 1e-5f && fabsf(q[(qn - 1) * 2 + 1] - pt[i * 2 + 1]) < 1e-5f)
                continue;
            q[qn * 2] = pt[i * 2];
            q[qn * 2 + 1] = pt[i * 2 + 1];
            qn++;
        }
        if (closed && qn > 2 && fabsf(q[0] - q[(qn - 1) * 2]) < 1e-5f && fabsf(q[1] - q[(qn - 1) * 2 + 1]) < 1e-5f) qn--;
        if (qn == 1) {
            /* zero-length: round/square caps draw a dot */
            if (paint->cap == GFX_CAP_ROUND) add_circle(p, m, q[0], q[1], hw, scale);
            else if (paint->cap == GFX_CAP_SQUARE) {
                float r[8] = {q[0] - hw, q[1] - hw, q[0] + hw, q[1] - hw, q[0] + hw, q[1] + hw, q[0] - hw, q[1] + hw};
                add_convex(p, m, r, 4);
            }
            free(q);
            continue;
        }
        int nseg = closed ? qn : qn - 1;
        for (int i = 0; i < nseg; i++) {
            float x0 = q[i * 2], y0 = q[i * 2 + 1];
            float x1 = q[((i + 1) % qn) * 2], y1 = q[((i + 1) % qn) * 2 + 1];
            float dx = x1 - x0, dy = y1 - y0;
            float len = sqrtf(dx * dx + dy * dy);
            if (len <= 0) continue;
            float ux = dx / len, uy = dy / len;
            float nx = -uy * hw, ny = ux * hw;
            if (!closed && paint->cap == GFX_CAP_SQUARE) {
                if (i == 0) {
                    x0 -= ux * hw;
                    y0 -= uy * hw;
                }
                if (i == nseg - 1) {
                    x1 += ux * hw;
                    y1 += uy * hw;
                }
            }
            float quad[8] = {x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny};
            add_convex(p, m, quad, 4);
        }
        /* joins */
        int njoin = closed ? qn : qn - 2;
        for (int k = 0; k < njoin; k++) {
            int i = closed ? k : k + 1;
            int ip = (i - 1 + qn) % qn, in = (i + 1) % qn;
            float vx = q[i * 2], vy = q[i * 2 + 1];
            float d0x = vx - q[ip * 2], d0y = vy - q[ip * 2 + 1];
            float d1x = q[in * 2] - vx, d1y = q[in * 2 + 1] - vy;
            float l0 = sqrtf(d0x * d0x + d0y * d0y), l1 = sqrtf(d1x * d1x + d1y * d1y);
            if (l0 <= 0 || l1 <= 0) continue;
            d0x /= l0;
            d0y /= l0;
            d1x /= l1;
            d1y /= l1;
            float cross = d0x * d1y - d0y * d1x;
            if (fabsf(cross) < 1e-4f && d0x * d1x + d0y * d1y > 0) continue; /* straight */
            if (paint->join == GFX_JOIN_ROUND) {
                add_circle(p, m, vx, vy, hw, scale);
                continue;
            }
            float side = cross > 0 ? -1.0f : 1.0f; /* outer side normal sign */
            float n0x = -d0y * hw * side, n0y = d0x * hw * side;
            float n1x = -d1y * hw * side, n1y = d1x * hw * side;
            float tri[8] = {vx, vy, vx + n0x, vy + n0y, 0, 0, vx + n1x, vy + n1y};
            int npts = 3;
            if (paint->join == GFX_JOIN_MITER) {
                float bx = n0x + n1x, by = n0y + n1y;
                float bl = sqrtf(bx * bx + by * by);
                float cosh = bl / (2 * hw); /* cos of half angle between normals */
                float limit = paint->miter > 0 ? paint->miter : 4.0f;
                if (cosh > 1e-4f && 1.0f / cosh <= limit) {
                    float ml = hw / cosh;
                    tri[4] = vx + bx / bl * ml;
                    tri[5] = vy + by / bl * ml;
                    tri[6] = vx + n1x;
                    tri[7] = vy + n1y;
                    npts = 4;
                }
            }
            if (npts == 3) {
                float t3[6] = {vx, vy, vx + n0x, vy + n0y, vx + n1x, vy + n1y};
                add_convex(p, m, t3, 3);
            } else {
                add_convex(p, m, tri, 4);
            }
        }
        /* round caps */
        if (!closed && paint->cap == GFX_CAP_ROUND) {
            add_circle(p, m, q[0], q[1], hw, scale);
            add_circle(p, m, q[(qn - 1) * 2], q[(qn - 1) * 2 + 1], hw, scale);
        }
        free(q);
    }
}

/* ======================================================================
 * Public drawing entry points
 * ====================================================================== */

static void draw_path_internal(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, const GfxPathData *pd,
                               const GfxPaint *paint, bool closed_shape) {
    SA_UNUSED(closed_shape);
    float scale = matrix_scale(m);
    Polyline pl;
    pl_init(&pl);
    flatten(pd, 0.2f / scale, &pl);
    Shade sh;
    shade_init(&sh, paint, m);
    if (paint->style == GFX_FILL || paint->style == GFX_FILL_AND_STROKE) {
        Poly p;
        poly_init(&p);
        poly_from_polyline(&p, &pl, m);
        fill_poly(t, clip, &p, pd->fill_type, paint->aa, &sh);
        poly_free(&p);
    }
    if (paint->style == GFX_STROKE || paint->style == GFX_FILL_AND_STROKE) {
        Poly p;
        poly_init(&p);
        stroke_polyline(&p, m, &pl, paint, scale);
        fill_poly(t, clip, &p, GFX_FILL_WINDING, paint->aa || paint->stroke_width > 0, &sh);
        poly_free(&p);
    }
    pl_free(&pl);
}

void gfx_draw_path(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, const GfxPathData *path, const GfxPaint *p) {
    draw_path_internal(t, m, clip, path, p, false);
}

void gfx_draw_color(GfxTarget *t, const GfxClip *clip, uint32_t color, int xfer) {
    int l = clip->l < 0 ? 0 : clip->l, tp = clip->t < 0 ? 0 : clip->t;
    int r = clip->r > t->w ? t->w : clip->r, b = clip->b > t->h ? t->h : clip->b;
    if (!clip->mask && (xfer == GFX_XFER_SRC || xfer == GFX_XFER_CLEAR || (xfer == GFX_XFER_SRC_OVER && (color >> 24) == 255))) {
        uint32_t c = xfer == GFX_XFER_CLEAR ? 0 : color;
        for (int y = tp; y < b; y++) {
            uint32_t *row = t->px + (size_t)y * (size_t)t->stride;
            for (int x = l; x < r; x++) row[x] = c;
        }
        return;
    }
    for (int y = tp; y < b; y++) {
        uint32_t *row = t->px + (size_t)y * (size_t)t->stride;
        for (int x = l; x < r; x++) {
            int cov = gfx_clip_cov(clip, x, y, 256);
            if (cov) blend(&row[x], color, cov, xfer);
        }
    }
}

/* Builds rectangle / oval / rounded rect / arc path data into small local buffers. */
typedef struct {
    uint8_t verbs[64];
    float pts[256];
    int nv, np;
} TmpPath;

static void tp_move(TmpPath *tp, float x, float y) {
    tp->verbs[tp->nv++] = GFX_VERB_MOVE;
    tp->pts[tp->np++] = x;
    tp->pts[tp->np++] = y;
}
static void tp_line(TmpPath *tp, float x, float y) {
    tp->verbs[tp->nv++] = GFX_VERB_LINE;
    tp->pts[tp->np++] = x;
    tp->pts[tp->np++] = y;
}
static void tp_cubic(TmpPath *tp, float x1, float y1, float x2, float y2, float x3, float y3) {
    tp->verbs[tp->nv++] = GFX_VERB_CUBIC;
    tp->pts[tp->np++] = x1;
    tp->pts[tp->np++] = y1;
    tp->pts[tp->np++] = x2;
    tp->pts[tp->np++] = y2;
    tp->pts[tp->np++] = x3;
    tp->pts[tp->np++] = y3;
}
static void tp_close(TmpPath *tp) { tp->verbs[tp->nv++] = GFX_VERB_CLOSE; }

static GfxPathData tp_data(TmpPath *tp) {
    GfxPathData d = {tp->verbs, tp->nv, tp->pts, tp->np, GFX_FILL_WINDING};
    return d;
}

#define KAPPA 0.5522847498f

static void tp_rrect(TmpPath *tp, float l, float t, float r, float b, float rx, float ry) {
    float w = r - l, h = b - t;
    if (rx > w / 2) rx = w / 2;
    if (ry > h / 2) ry = h / 2;
    if (rx <= 0 || ry <= 0) {
        tp_move(tp, l, t);
        tp_line(tp, r, t);
        tp_line(tp, r, b);
        tp_line(tp, l, b);
        tp_close(tp);
        return;
    }
    float kx = rx * KAPPA, ky = ry * KAPPA;
    tp_move(tp, l + rx, t);
    tp_line(tp, r - rx, t);
    tp_cubic(tp, r - rx + kx, t, r, t + ry - ky, r, t + ry);
    tp_line(tp, r, b - ry);
    tp_cubic(tp, r, b - ry + ky, r - rx + kx, b, r - rx, b);
    tp_line(tp, l + rx, b);
    tp_cubic(tp, l + rx - kx, b, l, b - ry + ky, l, b - ry);
    tp_line(tp, l, t + ry);
    tp_cubic(tp, l, t + ry - ky, l + rx - kx, t, l + rx, t);
    tp_close(tp);
}

void gfx_draw_rect(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, float l, float tp, float r, float b,
                   const GfxPaint *p) {
    if (r < l) {
        float s = l;
        l = r;
        r = s;
    }
    if (b < tp) {
        float s = tp;
        tp = b;
        b = s;
    }
    /* fast path: axis aligned solid fill */
    if (p->style == GFX_FILL && !p->shader && m->b == 0 && m->c == 0 && m->a > 0 && m->d > 0) {
        float x0 = l * m->a + m->e, x1 = r * m->a + m->e, y0 = tp * m->d + m->f, y1 = b * m->d + m->f;
        if (!p->aa || (x0 == floorf(x0) && x1 == floorf(x1) && y0 == floorf(y0) && y1 == floorf(y1))) {
            GfxClip c2 = *clip;
            int ix0 = (int)floorf(x0 + 0.5f), ix1 = (int)floorf(x1 + 0.5f), iy0 = (int)floorf(y0 + 0.5f),
                iy1 = (int)floorf(y1 + 0.5f);
            if (ix0 > c2.l) c2.l = ix0;
            if (ix1 < c2.r) c2.r = ix1;
            if (iy0 > c2.t) c2.t = iy0;
            if (iy1 < c2.b) c2.b = iy1;
            if (c2.l >= c2.r || c2.t >= c2.b) return;
            gfx_draw_color(t, &c2, apply_color_filter(p, p->color), p->xfer);
            return;
        }
    }
    TmpPath tpth = {{0}, {0}, 0, 0};
    tp_rrect(&tpth, l, tp, r, b, 0, 0);
    GfxPathData d = tp_data(&tpth);
    GfxPaint pp = *p;
    if (pp.style != GFX_FILL && pp.join == GFX_JOIN_MITER) pp.miter = 10;
    draw_path_internal(t, m, clip, &d, &pp, true);
}

void gfx_draw_round_rect(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, float l, float tp, float r, float b,
                         float rx, float ry, const GfxPaint *p) {
    TmpPath tpth = {{0}, {0}, 0, 0};
    tp_rrect(&tpth, fminf(l, r), fminf(tp, b), fmaxf(l, r), fmaxf(tp, b), rx, ry);
    GfxPathData d = tp_data(&tpth);
    draw_path_internal(t, m, clip, &d, p, true);
}

void gfx_draw_oval(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, float l, float tp, float r, float b,
                   const GfxPaint *p) {
    float rx = fabsf(r - l) / 2, ry = fabsf(b - tp) / 2;
    gfx_draw_round_rect(t, m, clip, l, tp, r, b, rx, ry, p);
}

void gfx_draw_arc(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, float l, float tp, float r, float b,
                  float start, float sweep, bool use_center, const GfxPaint *p) {
    if (sweep >= 360 || sweep <= -360) {
        gfx_draw_oval(t, m, clip, l, tp, r, b, p);
        return;
    }
    float cx = (l + r) / 2, cy = (tp + b) / 2, rx = fabsf(r - l) / 2, ry = fabsf(b - tp) / 2;
    int n = (int)ceilf(fabsf(sweep) / 3.0f) + 1;
    if (n < 2) n = 2;
    uint8_t *verbs = sa_malloc((size_t)n + 4);
    float *pts = sa_malloc(((size_t)n + 4) * 2 * sizeof(float));
    int nv = 0, np = 0;
    if (use_center) {
        verbs[nv++] = GFX_VERB_MOVE;
        pts[np++] = cx;
        pts[np++] = cy;
    }
    for (int i = 0; i <= n - 1; i++) {
        float a = (start + sweep * (float)i / (float)(n - 1)) * PI_F / 180.0f;
        verbs[nv++] = (i == 0 && !use_center) ? GFX_VERB_MOVE : GFX_VERB_LINE;
        pts[np++] = cx + cosf(a) * rx;
        pts[np++] = cy + sinf(a) * ry;
    }
    if (use_center || p->style == GFX_FILL) verbs[nv++] = GFX_VERB_CLOSE;
    GfxPathData d = {verbs, nv, pts, np, GFX_FILL_WINDING};
    draw_path_internal(t, m, clip, &d, p, use_center);
    free(verbs);
    free(pts);
}

void gfx_draw_line(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, float x0, float y0, float x1, float y1,
                   const GfxPaint *p) {
    uint8_t verbs[2] = {GFX_VERB_MOVE, GFX_VERB_LINE};
    float pts[4] = {x0, y0, x1, y1};
    GfxPathData d = {verbs, 2, pts, 4, GFX_FILL_WINDING};
    GfxPaint pp = *p;
    pp.style = GFX_STROKE;
    draw_path_internal(t, m, clip, &d, &pp, false);
}

void gfx_draw_points(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, const float *pts, int n, const GfxPaint *p) {
    GfxPaint pp = *p;
    pp.style = GFX_FILL;
    float r = p->stroke_width > 0 ? p->stroke_width / 2 : 0.5f;
    for (int i = 0; i + 1 < n; i += 2) {
        if (p->cap == GFX_CAP_ROUND) gfx_draw_oval(t, m, clip, pts[i] - r, pts[i + 1] - r, pts[i] + r, pts[i + 1] + r, &pp);
        else gfx_draw_rect(t, m, clip, pts[i] - r, pts[i + 1] - r, pts[i] + r, pts[i + 1] + r, &pp);
    }
}

/* ---- bitmaps --------------------------------------------------------------------------------------- */

void gfx_draw_bitmap_matrix(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, const GfxTarget *src,
                            const GfxMatrix *bm, const GfxPaint *p) {
    GfxMatrix full, inv;
    gfx_matrix_mul(&full, m, bm);
    if (!gfx_matrix_invert(&full, &inv)) return;
    /* device bounds of the bitmap */
    float xs[4], ys[4];
    gfx_map(&full, 0, 0, &xs[0], &ys[0]);
    gfx_map(&full, (float)src->w, 0, &xs[1], &ys[1]);
    gfx_map(&full, (float)src->w, (float)src->h, &xs[2], &ys[2]);
    gfx_map(&full, 0, (float)src->h, &xs[3], &ys[3]);
    float minx = xs[0], maxx = xs[0], miny = ys[0], maxy = ys[0];
    for (int i = 1; i < 4; i++) {
        minx = fminf(minx, xs[i]);
        maxx = fmaxf(maxx, xs[i]);
        miny = fminf(miny, ys[i]);
        maxy = fmaxf(maxy, ys[i]);
    }
    int x0 = (int)floorf(minx), x1 = (int)ceilf(maxx), y0 = (int)floorf(miny), y1 = (int)ceilf(maxy);
    int cl = clip->l < 0 ? 0 : clip->l, ct = clip->t < 0 ? 0 : clip->t;
    int cr = clip->r > t->w ? t->w : clip->r, cb = clip->b > t->h ? t->h : clip->b;
    if (x0 < cl) x0 = cl;
    if (y0 < ct) y0 = ct;
    if (x1 > cr) x1 = cr;
    if (y1 > cb) y1 = cb;
    if (x0 >= x1 || y0 >= y1) return;
    int alpha = (int)(p->color >> 24);
    bool axis = full.b == 0 && full.c == 0;
    bool filter = p->filter && !(axis && fabsf(full.a - 1) < 1e-4f && fabsf(full.d - 1) < 1e-4f);
    int xfer = p->xfer;
    for (int y = y0; y < y1; y++) {
        uint32_t *row = t->px + (size_t)y * (size_t)t->stride;
        float py = (float)y + 0.5f;
        for (int x = x0; x < x1; x++) {
            float px = (float)x + 0.5f;
            float u = inv.a * px + inv.c * py + inv.e;
            float v = inv.b * px + inv.d * py + inv.f;
            if (u < 0 || v < 0 || u >= (float)src->w || v >= (float)src->h) continue;
            uint32_t c;
            if (filter) {
                c = sample_bitmap(src, u, v, true, GFX_TILE_CLAMP, GFX_TILE_CLAMP);
            } else {
                c = src->px[(int)v * src->stride + (int)u];
            }
            if (alpha != 255) c = (c & 0xffffff) | ((uint32_t)((int)(c >> 24) * alpha / 255) << 24);
            if (p->has_color_filter) c = apply_color_filter(p, c);
            int cov = gfx_clip_cov(clip, x, y, 256);
            if (cov) blend(&row[x], c, cov, xfer);
        }
    }
}

void gfx_draw_bitmap(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, const GfxTarget *src, float sl, float st,
                     float sr, float sb, float l, float tp, float r, float b, const GfxPaint *p) {
    if (sr <= sl || sb <= st || r == l || b == tp) return;
    /* restrict the sampled source to the src rect */
    int isl = (int)floorf(sl), ist = (int)floorf(st), isr = (int)ceilf(sr), isb = (int)ceilf(sb);
    if (isl < 0) isl = 0;
    if (ist < 0) ist = 0;
    if (isr > src->w) isr = src->w;
    if (isb > src->h) isb = src->h;
    if (isr <= isl || isb <= ist) return;
    GfxTarget sub = {src->px + (size_t)ist * (size_t)src->stride + isl, isr - isl, isb - ist, src->stride};
    float sx = (r - l) / (sr - sl), sy = (b - tp) / (sb - st);
    GfxMatrix bm = {sx, 0, 0, sy, l - (sl - (float)isl) * sx, tp - (st - (float)ist) * sy};
    gfx_draw_bitmap_matrix(t, m, clip, &sub, &bm, p);
}

void gfx_blit_mask(GfxTarget *t, const GfxClip *clip, const uint8_t *mask, int mw, int mh, int mstride, int x, int y,
                   const GfxPaint *p) {
    int cl = clip->l < 0 ? 0 : clip->l, ct = clip->t < 0 ? 0 : clip->t;
    int cr = clip->r > t->w ? t->w : clip->r, cb = clip->b > t->h ? t->h : clip->b;
    int x0 = x < cl ? cl : x, y0 = y < ct ? ct : y;
    int x1 = x + mw > cr ? cr : x + mw, y1 = y + mh > cb ? cb : y + mh;
    if (x0 >= x1 || y0 >= y1) return;
    Shade sh;
    GfxMatrix id;
    gfx_matrix_identity(&id);
    shade_init(&sh, p, &id);
    for (int yy = y0; yy < y1; yy++) {
        uint32_t *row = t->px + (size_t)yy * (size_t)t->stride;
        const uint8_t *mr = mask + (size_t)(yy - y) * (size_t)mstride;
        for (int xx = x0; xx < x1; xx++) {
            int a = mr[xx - x];
            if (!a) continue;
            uint32_t s = p->shader ? shader_color(&sh, xx, yy) : sh.color;
            int cov = gfx_clip_cov(clip, xx, yy, a + (a >> 7));
            if (cov) blend(&row[xx], s, cov, p->xfer);
        }
    }
}
