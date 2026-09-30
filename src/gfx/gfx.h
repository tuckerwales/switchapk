/*
 * Software 2D renderer backing android.graphics.Canvas.
 *
 * Pixels are 32-bit ints in Java's ARGB layout (0xAARRGGBB), not
 * premultiplied, exactly as Android's Bitmap.getPixel() exposes them.
 * All drawing calls are stateless: the caller passes the current matrix,
 * clip rectangle and paint.
 */
#ifndef SWITCHAPK_GFX_H
#define SWITCHAPK_GFX_H

#include "../core/common.h"

typedef struct {
    uint32_t *px;
    int w, h;
    int stride; /* in pixels */
} GfxTarget;

/* Affine matrix: x' = a*x + c*y + e ; y' = b*x + d*y + f */
typedef struct {
    float a, b, c, d, e, f;
} GfxMatrix;

enum { GFX_FILL = 0, GFX_STROKE = 1, GFX_FILL_AND_STROKE = 2 };
enum { GFX_CAP_BUTT = 0, GFX_CAP_ROUND = 1, GFX_CAP_SQUARE = 2 };
enum { GFX_JOIN_MITER = 0, GFX_JOIN_ROUND = 1, GFX_JOIN_BEVEL = 2 };

/* Porter-Duff modes (subset of android.graphics.PorterDuff.Mode ordinal values) */
enum {
    GFX_XFER_CLEAR = 0,
    GFX_XFER_SRC = 1,
    GFX_XFER_DST = 2,
    GFX_XFER_SRC_OVER = 3,
    GFX_XFER_DST_OVER = 4,
    GFX_XFER_SRC_IN = 5,
    GFX_XFER_DST_IN = 6,
    GFX_XFER_SRC_OUT = 7,
    GFX_XFER_DST_OUT = 8,
    GFX_XFER_SRC_ATOP = 9,
    GFX_XFER_DST_ATOP = 10,
    GFX_XFER_XOR = 11,
    GFX_XFER_DARKEN = 16,
    GFX_XFER_LIGHTEN = 17,
    GFX_XFER_MULTIPLY = 13,
    GFX_XFER_SCREEN = 14,
    GFX_XFER_ADD = 12,
};

enum { GFX_SHADER_NONE = 0, GFX_SHADER_LINEAR, GFX_SHADER_RADIAL, GFX_SHADER_SWEEP, GFX_SHADER_BITMAP };
enum { GFX_TILE_CLAMP = 0, GFX_TILE_REPEAT = 1, GFX_TILE_MIRROR = 2 };

typedef struct {
    int type;
    float x0, y0, x1, y1, r; /* geometry in shader-local space */
    const uint32_t *colors;
    const float *positions; /* may be NULL (evenly spaced) */
    int ncolors;
    int tile_x, tile_y;
    GfxMatrix local;        /* shader local matrix */
    GfxTarget bitmap;       /* for bitmap shaders */
    bool filter;
} GfxShader;

typedef struct {
    uint32_t color;
    int style;
    float stroke_width;
    int cap, join;
    float miter;
    bool aa;
    bool filter;
    int xfer;
    const GfxShader *shader;
    /* color filter: multiply then add (per channel, 0..255 domain), used for tints */
    bool has_color_filter;
    uint32_t cf_color;
    int cf_mode;
} GfxPaint;

typedef struct {
    int l, t, r, b;
    /* optional anti-aliased clip mask covering the whole target (0..255), for non-rectangular clips */
    const uint8_t *mask;
    int mask_stride;
} GfxClip;

static inline int gfx_clip_cov(const GfxClip *c, int x, int y, int cov) {
    return c->mask ? (cov * (c->mask[(size_t)y * (size_t)c->mask_stride + x] + 1)) >> 8 : cov;
}

/* ---- matrix ---------------------------------------------------------------------- */

void gfx_matrix_identity(GfxMatrix *m);
void gfx_matrix_mul(GfxMatrix *out, const GfxMatrix *a, const GfxMatrix *b); /* out = a * b */
bool gfx_matrix_invert(const GfxMatrix *m, GfxMatrix *out);
static inline void gfx_map(const GfxMatrix *m, float x, float y, float *ox, float *oy) {
    *ox = m->a * x + m->c * y + m->e;
    *oy = m->b * x + m->d * y + m->f;
}
/* Android Matrix float[9] (row-major MSCALE_X, MSKEW_X, MTRANS_X, MSKEW_Y, MSCALE_Y, MTRANS_Y, ...) */
void gfx_matrix_from_android(GfxMatrix *m, const float v[9]);

/* ---- paths ---------------------------------------------------------------------------- */

enum { GFX_VERB_MOVE = 0, GFX_VERB_LINE = 1, GFX_VERB_QUAD = 2, GFX_VERB_CUBIC = 3, GFX_VERB_CLOSE = 4 };
enum { GFX_FILL_WINDING = 0, GFX_FILL_EVEN_ODD = 1, GFX_FILL_INVERSE_WINDING = 2, GFX_FILL_INVERSE_EVEN_ODD = 3 };

typedef struct {
    const uint8_t *verbs;
    int nverbs;
    const float *pts;
    int npts; /* number of floats */
    int fill_type;
} GfxPathData;

/* ---- drawing ----------------------------------------------------------------------------- */

void gfx_draw_color(GfxTarget *t, const GfxClip *clip, uint32_t color, int xfer);
void gfx_draw_rect(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, float l, float tp, float r, float b,
                   const GfxPaint *p);
void gfx_draw_round_rect(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, float l, float tp, float r, float b,
                         float rx, float ry, const GfxPaint *p);
void gfx_draw_oval(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, float l, float tp, float r, float b,
                   const GfxPaint *p);
void gfx_draw_arc(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, float l, float tp, float r, float b,
                  float start, float sweep, bool use_center, const GfxPaint *p);
void gfx_draw_line(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, float x0, float y0, float x1, float y1,
                   const GfxPaint *p);
void gfx_draw_points(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, const float *pts, int n,
                     const GfxPaint *p);
void gfx_draw_path(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, const GfxPathData *path, const GfxPaint *p);
/* Draws src[sl,st,sr,sb) into dst rect (l,t,r,b) transformed by m. */
void gfx_draw_bitmap(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, const GfxTarget *src, float sl, float st,
                     float sr, float sb, float l, float tp, float r, float b, const GfxPaint *p);
/* Draws a bitmap with an arbitrary matrix (bitmap space -> canvas space). */
void gfx_draw_bitmap_matrix(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, const GfxTarget *src,
                            const GfxMatrix *bm, const GfxPaint *p);
/* Blends an 8-bit coverage mask (w x h at device position x,y) with the paint. */
void gfx_blit_mask(GfxTarget *t, const GfxClip *clip, const uint8_t *mask, int mw, int mh, int mstride, int x, int y,
                   const GfxPaint *p);

/* ---- text ------------------------------------------------------------------------------------ */

typedef struct GfxFont GfxFont;

bool gfx_font_init_default(void);           /* finds a system font */
GfxFont *gfx_font_load(const uint8_t *data, size_t len, bool take_ownership);
GfxFont *gfx_font_default(bool bold);
void gfx_font_register_default(GfxFont *regular, GfxFont *bold);
/* Metrics at a given pixel size: ascent (negative, Android convention), descent, leading. */
void gfx_font_metrics(GfxFont *f, float size, float *ascent, float *descent, float *leading);
float gfx_measure_text(GfxFont *f, float size, const uint16_t *text, int len, float *widths /* optional */);
void gfx_draw_text(GfxTarget *t, const GfxMatrix *m, const GfxClip *clip, GfxFont *f, float size, const uint16_t *text,
                   int len, float x, float y, float skew_x, bool fake_bold, const GfxPaint *p);
void gfx_font_add_fallback(GfxFont *f);
bool gfx_font_has_glyph(GfxFont *f, uint32_t cp);
void gfx_text_path(GfxFont *f, float size, const uint16_t *text, int len, float x, float y, uint8_t **verbs, int *nverbs,
                   float **pts, int *npts);
void gfx_text_bounds(GfxFont *f, float size, const uint16_t *text, int len, float *l, float *t, float *r, float *b);

/* ---- images ------------------------------------------------------------------------------------ */

/* Decodes PNG/JPEG/GIF/BMP into ARGB ints (malloc'd). */
uint32_t *gfx_decode_image(const uint8_t *data, size_t len, int *w, int *h, bool *has_alpha);
/* Encodes ARGB to PNG (malloc'd). */
bool gfx_image_info(const uint8_t *data, size_t len, int *w, int *h);
uint8_t *gfx_encode_png(const uint32_t *px, int w, int h, int stride, size_t *out_len);

/* ---- helpers ----------------------------------------------------------------------------------- */

static inline uint32_t gfx_argb(int a, int r, int g, int b) {
    return ((uint32_t)a << 24) | ((uint32_t)r << 16) | ((uint32_t)g << 8) | (uint32_t)b;
}

#endif
