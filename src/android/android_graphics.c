/*
 * android.graphics natives: Canvas drawing, Paint text metrics, Typeface,
 * Bitmap codecs. Canvas/Paint/Shader state lives in Java objects; the
 * natives read their fields and call the stateless gfx renderer.
 */
#include "android.h"
#include "../gfx/gfx.h"

#include <math.h>

#define LOG_TAG "graphics"

/* ---- Java object -> gfx structures ------------------------------------------------------------ */

typedef struct {
    GfxTarget target;
    GfxMatrix matrix;
    GfxClip clip;
} CanvasState;

static bool bitmap_target(Object *bmp, GfxTarget *t) {
    if (!bmp) return false;
    ArrayObject *px = (ArrayObject *)FIELD_OBJ(bmp, "mPixels", "[I");
    int w = FIELD_INT(bmp, "mWidth"), h = FIELD_INT(bmp, "mHeight");
    if (!px || w <= 0 || h <= 0 || px->length < w * h) return false;
    t->px = ARRAY_DATA(px, uint32_t);
    t->w = w;
    t->h = h;
    t->stride = w;
    return true;
}

static bool canvas_state(Object *c, CanvasState *s) {
    if (!c) return false;
    ArrayObject *px = (ArrayObject *)FIELD_OBJ(c, "mPixels", "[I");
    int w = FIELD_INT(c, "mWidth"), h = FIELD_INT(c, "mHeight");
    if (!px || w <= 0 || h <= 0 || px->length < w * h) return false;
    s->target.px = ARRAY_DATA(px, uint32_t);
    s->target.w = w;
    s->target.h = h;
    s->target.stride = w;
    ArrayObject *m = (ArrayObject *)FIELD_OBJ(c, "mMatrix", "[F");
    const float *mv = ARRAY_DATA(m, float);
    s->matrix = (GfxMatrix){mv[0], mv[1], mv[2], mv[3], mv[4], mv[5]};
    ArrayObject *cl = (ArrayObject *)FIELD_OBJ(c, "mClip", "[I");
    const int32_t *cv = ARRAY_DATA(cl, int32_t);
    s->clip.l = cv[0] < 0 ? 0 : cv[0];
    s->clip.t = cv[1] < 0 ? 0 : cv[1];
    s->clip.r = cv[2] > w ? w : cv[2];
    s->clip.b = cv[3] > h ? h : cv[3];
    ArrayObject *mask = (ArrayObject *)FIELD_OBJ(c, "mClipMask", "[B");
    s->clip.mask = mask && mask->length >= w * h ? ARRAY_DATA(mask, uint8_t) : NULL;
    s->clip.mask_stride = w;
    return s->clip.l < s->clip.r && s->clip.t < s->clip.b;
}

typedef struct {
    GfxPaint paint;
    GfxShader shader;
    GfxShader compose[2];
    float local[6];
} PaintState;

enum { PAINT_ANTI_ALIAS = 0x01, PAINT_FILTER_BITMAP = 0x02, PAINT_FAKE_BOLD_TEXT = 0x20 };

static void shader_from_java(Object *js, GfxShader *s) {
    memset(s, 0, sizeof *s);
    s->type = FIELD_INT(js, "mType");
    ArrayObject *geom = (ArrayObject *)FIELD_OBJ(js, "mGeom", "[F");
    if (geom && geom->length >= 5) {
        const float *g = ARRAY_DATA(geom, float);
        s->x0 = g[0];
        s->y0 = g[1];
        s->x1 = g[2];
        s->y1 = g[3];
        s->r = g[4];
    }
    ArrayObject *colors = (ArrayObject *)FIELD_OBJ(js, "mColors", "[I");
    if (colors) {
        s->colors = ARRAY_DATA(colors, uint32_t);
        s->ncolors = colors->length;
    }
    ArrayObject *pos = (ArrayObject *)FIELD_OBJ(js, "mPositions", "[F");
    s->positions = pos && colors && pos->length == colors->length ? ARRAY_DATA(pos, float) : NULL;
    s->tile_x = FIELD_INT(js, "mTileX");
    s->tile_y = FIELD_INT(js, "mTileY");
    ArrayObject *lm = (ArrayObject *)FIELD_OBJ(js, "mLocal", "[F");
    if (lm && lm->length >= 6) {
        const float *l = ARRAY_DATA(lm, float);
        s->local = (GfxMatrix){l[0], l[1], l[2], l[3], l[4], l[5]};
    } else {
        gfx_matrix_identity(&s->local);
    }
    if (s->type == GFX_SHADER_BITMAP) {
        Object *bmp = FIELD_OBJ(js, "mBitmap", "Landroid/graphics/Bitmap;");
        if (!bitmap_target(bmp, &s->bitmap)) s->type = GFX_SHADER_NONE;
        s->filter = true;
    }
}

static void paint_from_java(Object *jp, PaintState *ps) {
    GfxPaint *p = &ps->paint;
    memset(ps, 0, sizeof *ps);
    if (!jp) {
        p->color = 0xFF000000u;
        p->aa = true;
        p->filter = true;
        p->xfer = GFX_XFER_SRC_OVER;
        p->stroke_width = 0;
        p->miter = 4;
        return;
    }
    p->color = (uint32_t)FIELD_INT(jp, "mColor");
    int flags = FIELD_INT(jp, "mFlags");
    p->aa = (flags & PAINT_ANTI_ALIAS) != 0;
    p->filter = (flags & PAINT_FILTER_BITMAP) != 0;
    p->style = FIELD_INT(jp, "mStyle");
    p->stroke_width = FIELD_FLOAT(jp, "mStrokeWidth");
    p->cap = FIELD_INT(jp, "mCap");
    p->join = FIELD_INT(jp, "mJoin");
    p->miter = FIELD_FLOAT(jp, "mMiter");
    p->xfer = FIELD_INT(jp, "mXfer");
    int cf_mode = FIELD_INT(jp, "mCfMode");
    if (cf_mode >= 0) {
        p->has_color_filter = true;
        p->cf_mode = cf_mode;
        p->cf_color = (uint32_t)FIELD_INT(jp, "mCfColor");
    }
    Object *js = FIELD_OBJ(jp, "mShader", "Landroid/graphics/Shader;");
    if (js) {
        shader_from_java(js, &ps->shader);
        if (ps->shader.type != GFX_SHADER_NONE) p->shader = &ps->shader;
    }
}

/* ---- Canvas natives ------------------------------------------------------------------------------ */

#define CANVAS_BEGIN(idx)                  \
    CanvasState cs;                        \
    if (!canvas_state(A_OBJ(idx), &cs)) return;

NATIVE(Canvas_nDrawColor) {
    UNUSED_ARGS();
    CANVAS_BEGIN(0);
    gfx_draw_color(&cs.target, &cs.clip, (uint32_t)A_INT(1), A_INT(2));
}

NATIVE(Canvas_nDrawRect) {
    UNUSED_ARGS();
    CANVAS_BEGIN(0);
    PaintState ps;
    paint_from_java(A_OBJ(5), &ps);
    gfx_draw_rect(&cs.target, &cs.matrix, &cs.clip, A_FLOAT(1), A_FLOAT(2), A_FLOAT(3), A_FLOAT(4), &ps.paint);
}

NATIVE(Canvas_nDrawRoundRect) {
    UNUSED_ARGS();
    CANVAS_BEGIN(0);
    PaintState ps;
    paint_from_java(A_OBJ(7), &ps);
    gfx_draw_round_rect(&cs.target, &cs.matrix, &cs.clip, A_FLOAT(1), A_FLOAT(2), A_FLOAT(3), A_FLOAT(4), A_FLOAT(5),
                        A_FLOAT(6), &ps.paint);
}

NATIVE(Canvas_nDrawOval) {
    UNUSED_ARGS();
    CANVAS_BEGIN(0);
    PaintState ps;
    paint_from_java(A_OBJ(5), &ps);
    gfx_draw_oval(&cs.target, &cs.matrix, &cs.clip, A_FLOAT(1), A_FLOAT(2), A_FLOAT(3), A_FLOAT(4), &ps.paint);
}

NATIVE(Canvas_nDrawArc) {
    UNUSED_ARGS();
    CANVAS_BEGIN(0);
    PaintState ps;
    paint_from_java(A_OBJ(8), &ps);
    gfx_draw_arc(&cs.target, &cs.matrix, &cs.clip, A_FLOAT(1), A_FLOAT(2), A_FLOAT(3), A_FLOAT(4), A_FLOAT(5), A_FLOAT(6),
                 A_BOOL(7), &ps.paint);
}

NATIVE(Canvas_nDrawLine) {
    UNUSED_ARGS();
    CANVAS_BEGIN(0);
    PaintState ps;
    paint_from_java(A_OBJ(5), &ps);
    gfx_draw_line(&cs.target, &cs.matrix, &cs.clip, A_FLOAT(1), A_FLOAT(2), A_FLOAT(3), A_FLOAT(4), &ps.paint);
}

/* nDrawPoints(Canvas, float[] pts, int offset, int count, Paint) */
NATIVE(Canvas_nDrawPoints) {
    UNUSED_ARGS();
    CANVAS_BEGIN(0);
    ArrayObject *pts = A_ARR(1);
    int off = A_INT(2), count = A_INT(3);
    if (!pts || off < 0 || count < 2 || off + count > pts->length) return;
    PaintState ps;
    paint_from_java(A_OBJ(4), &ps);
    gfx_draw_points(&cs.target, &cs.matrix, &cs.clip, ARRAY_DATA(pts, float) + off, count / 2, &ps.paint);
}

/* nDrawPath(Canvas, byte[] verbs, int nverbs, float[] pts, int npts, int fillType, Paint) */
NATIVE(Canvas_nDrawPath) {
    UNUSED_ARGS();
    CANVAS_BEGIN(0);
    ArrayObject *verbs = A_ARR(1);
    ArrayObject *pts = A_ARR(3);
    int nv = A_INT(2), np = A_INT(4);
    if (!verbs || !pts || nv <= 0 || nv > verbs->length || np > pts->length) return;
    PaintState ps;
    paint_from_java(A_OBJ(6), &ps);
    GfxPathData pd = {ARRAY_DATA(verbs, uint8_t), nv, ARRAY_DATA(pts, float), np, A_INT(5)};
    gfx_draw_path(&cs.target, &cs.matrix, &cs.clip, &pd, &ps.paint);
}

/* nDrawBitmapRect(Canvas, Bitmap, float sl, st, sr, sb, float l, t, r, b, Paint) */
NATIVE(Canvas_nDrawBitmapRect) {
    UNUSED_ARGS();
    CANVAS_BEGIN(0);
    GfxTarget src;
    if (!bitmap_target(A_OBJ(1), &src)) return;
    PaintState ps;
    paint_from_java(A_OBJ(10), &ps);
    if (!A_OBJ(10)) ps.paint.filter = true;
    ps.paint.shader = NULL;
    gfx_draw_bitmap(&cs.target, &cs.matrix, &cs.clip, &src, A_FLOAT(2), A_FLOAT(3), A_FLOAT(4), A_FLOAT(5), A_FLOAT(6),
                    A_FLOAT(7), A_FLOAT(8), A_FLOAT(9), &ps.paint);
}

/* nDrawBitmapMatrix(Canvas, Bitmap, float[] m6, Paint) */
NATIVE(Canvas_nDrawBitmapMatrix) {
    UNUSED_ARGS();
    CANVAS_BEGIN(0);
    GfxTarget src;
    if (!bitmap_target(A_OBJ(1), &src)) return;
    ArrayObject *m = A_ARR(2);
    if (!m || m->length < 6) return;
    const float *v = ARRAY_DATA(m, float);
    GfxMatrix bm = {v[0], v[1], v[2], v[3], v[4], v[5]};
    PaintState ps;
    paint_from_java(A_OBJ(3), &ps);
    if (!A_OBJ(3)) ps.paint.filter = true;
    ps.paint.shader = NULL;
    gfx_draw_bitmap_matrix(&cs.target, &cs.matrix, &cs.clip, &src, &bm, &ps.paint);
}

/* nDrawPixels(Canvas, int[] colors, int offset, int stride, float x, float y, int w, int h, boolean alpha, Paint) */
NATIVE(Canvas_nDrawPixels) {
    UNUSED_ARGS();
    CANVAS_BEGIN(0);
    ArrayObject *colors = A_ARR(1);
    int off = A_INT(2), stride = A_INT(3), w = A_INT(6), h = A_INT(7);
    if (!colors || w <= 0 || h <= 0 || off < 0 || stride < w || off + (h - 1) * stride + w > colors->length) return;
    GfxTarget src = {ARRAY_DATA(colors, uint32_t) + off, w, h, stride};
    PaintState ps;
    paint_from_java(A_OBJ(9), &ps);
    ps.paint.shader = NULL;
    GfxMatrix bm = {1, 0, 0, 1, A_FLOAT(4), A_FLOAT(5)};
    gfx_draw_bitmap_matrix(&cs.target, &cs.matrix, &cs.clip, &src, &bm, &ps.paint);
}

static GfxFont *font_of(Object *paint, bool *fake_bold) {
    Object *tf = paint ? FIELD_OBJ(paint, "mTypeface", "Landroid/graphics/Typeface;") : NULL;
    GfxFont *f = NULL;
    *fake_bold = false;
    if (tf) {
        f = (GfxFont *)(uintptr_t)FIELD_LONG(tf, "mNative");
        if (FIELD_BOOL(tf, "mFakeBold")) *fake_bold = true;
    }
    if (paint && (FIELD_INT(paint, "mFlags") & PAINT_FAKE_BOLD_TEXT)) *fake_bold = true;
    if (!f) f = gfx_font_default(false);
    return f;
}

/* nDrawText(Canvas, char[] text, int start, int count, float x, float y, Paint) */
NATIVE(Canvas_nDrawText) {
    UNUSED_ARGS();
    CANVAS_BEGIN(0);
    ArrayObject *text = A_ARR(1);
    int start = A_INT(2), count = A_INT(3);
    if (!text || start < 0 || count <= 0 || start + count > text->length) return;
    Object *jp = A_OBJ(6);
    PaintState ps;
    paint_from_java(jp, &ps);
    bool fake_bold;
    GfxFont *font = font_of(jp, &fake_bold);
    float size = jp ? FIELD_FLOAT(jp, "mTextSize") : 12.0f;
    float skew = jp ? FIELD_FLOAT(jp, "mTextSkewX") : 0.0f;
    float scale_x = jp ? FIELD_FLOAT(jp, "mTextScaleX") : 1.0f;
    GfxMatrix m = cs.matrix;
    float x = A_FLOAT(4), y = A_FLOAT(5);
    if (scale_x != 1.0f && scale_x > 0) {
        /* horizontal text scaling around the origin x */
        GfxMatrix sc = {scale_x, 0, 0, 1, x - x * scale_x, 0};
        GfxMatrix tmp;
        gfx_matrix_mul(&tmp, &m, &sc);
        m = tmp;
    }
    gfx_draw_text(&cs.target, &m, &cs.clip, font, size, ARRAY_DATA(text, uint16_t) + start, count, x, y, skew, fake_bold,
                  &ps.paint);
}

/* ---- Paint natives -------------------------------------------------------------------------------- */

static GfxFont *font_handle(int64_t h) { return h ? (GfxFont *)(uintptr_t)h : gfx_font_default(false); }

/* static float nMeasureText(long font, float size, char[] text, int start, int count, float[] widths, int widthsOff) */
NATIVE(Paint_nMeasureText) {
    UNUSED_ARGS();
    GfxFont *f = font_handle(A_LONG(0));
    float size = A_FLOAT(2);
    ArrayObject *text = A_ARR(3);
    int start = A_INT(4), count = A_INT(5);
    ArrayObject *widths = A_ARR(6);
    int woff = A_INT(7);
    if (!text || start < 0 || count < 0 || start + count > text->length) {
        R_FLOAT(0);
        return;
    }
    float *w = NULL;
    if (widths && woff >= 0 && woff + count <= widths->length) w = ARRAY_DATA(widths, float) + woff;
    R_FLOAT(gfx_measure_text(f, size, ARRAY_DATA(text, uint16_t) + start, count, w));
}

/* static void nGetFontMetrics(long font, float size, float[] out) : ascent, descent, leading, top, bottom */
NATIVE(Paint_nGetFontMetrics) {
    UNUSED_ARGS();
    GfxFont *f = font_handle(A_LONG(0));
    float size = A_FLOAT(2);
    ArrayObject *out = A_ARR(3);
    if (!out || out->length < 5) return;
    float a, d, l;
    gfx_font_metrics(f, size, &a, &d, &l);
    float *o = ARRAY_DATA(out, float);
    o[0] = a;
    o[1] = d;
    o[2] = l;
    o[3] = a * 1.1f;
    o[4] = d * 1.1f;
}

/* static void nGetTextBounds(long font, float size, char[] text, int start, int count, float[] out4) */
NATIVE(Paint_nGetTextBounds) {
    UNUSED_ARGS();
    GfxFont *f = font_handle(A_LONG(0));
    float size = A_FLOAT(2);
    ArrayObject *text = A_ARR(3);
    int start = A_INT(4), count = A_INT(5);
    ArrayObject *out = A_ARR(6);
    if (!text || !out || out->length < 4 || start < 0 || count < 0 || start + count > text->length) return;
    float *o = ARRAY_DATA(out, float);
    gfx_text_bounds(f, size, ARRAY_DATA(text, uint16_t) + start, count, &o[0], &o[1], &o[2], &o[3]);
}

/* static Object[] nGetTextPath(long font, float size, char[] text, int start, int count, float x, float y) */
NATIVE(Paint_nGetTextPath) {
    UNUSED_ARGS();
    GfxFont *f = font_handle(A_LONG(0));
    float size = A_FLOAT(2);
    ArrayObject *text = A_ARR(3);
    int start = A_INT(4), count = A_INT(5);
    if (!text || start < 0 || count < 0 || start + count > text->length) return;
    uint8_t *verbs;
    float *pts;
    int nv, np;
    gfx_text_path(f, size, ARRAY_DATA(text, uint16_t) + start, count, A_FLOAT(6), A_FLOAT(7), &verbs, &nv, &pts, &np);
    ArrayObject *jv = vm_alloc_prim_array(t, 'B', nv);
    Object *r1 = (Object *)jv;
    vm_add_root(&r1);
    ArrayObject *jpts = vm_alloc_prim_array(t, 'F', np);
    Object *r2 = (Object *)jpts;
    vm_add_root(&r2);
    ArrayObject *res = NULL;
    if (jv && jpts) {
        if (nv) memcpy(jv->data, verbs, (size_t)nv);
        if (np) memcpy(jpts->data, pts, (size_t)np * sizeof(float));
        res = vm_alloc_array(t, g_vm.wk.arr_Object, 2);
        if (res) {
            ARRAY_DATA(res, Object *)[0] = (Object *)jv;
            ARRAY_DATA(res, Object *)[1] = (Object *)jpts;
        }
    }
    vm_remove_root(&r2);
    vm_remove_root(&r1);
    free(verbs);
    free(pts);
    R_OBJ(res);
}

/* static boolean nHasGlyph(long font, int codepoint) */
NATIVE(Paint_nHasGlyph) {
    UNUSED_ARGS();
    R_BOOL(gfx_font_has_glyph(font_handle(A_LONG(0)), (uint32_t)A_INT(2)));
}

/* ---- Typeface natives ------------------------------------------------------------------------------ */

NATIVE(Typeface_nDefault) {
    UNUSED_ARGS();
    R_LONG((int64_t)(uintptr_t)gfx_font_default(A_BOOL(0)));
}

NATIVE(Typeface_nLoad) {
    UNUSED_ARGS();
    ArrayObject *a = A_ARR(0);
    if (!a || a->length <= 0) {
        R_LONG(0);
        return;
    }
    uint8_t *copy = sa_malloc((size_t)a->length);
    memcpy(copy, a->data, (size_t)a->length);
    GfxFont *f = gfx_font_load(copy, (size_t)a->length, true);
    if (!f) free(copy);
    R_LONG((int64_t)(uintptr_t)f);
}

NATIVE(Typeface_nLoadFile) {
    UNUSED_ARGS();
    char *path = nat_str(A_OBJ(0));
    if (!path) {
        R_LONG(0);
        return;
    }
    char *mapped = platform_map_path(path);
    size_t len;
    uint8_t *data = sa_read_file(mapped ? mapped : path, &len);
    free(mapped);
    free(path);
    GfxFont *f = data ? gfx_font_load(data, len, true) : NULL;
    if (!f) free(data);
    R_LONG((int64_t)(uintptr_t)f);
}

/* ---- Bitmap codecs ----------------------------------------------------------------------------------- */

/* static int[] nDecode(byte[] data, int off, int len, int[] info{w,h,alpha}, boolean boundsOnly, int sample) */
NATIVE(BitmapFactory_nDecode) {
    UNUSED_ARGS();
    ArrayObject *data = A_ARR(0);
    int off = A_INT(1), len = A_INT(2);
    ArrayObject *info = A_ARR(3);
    bool bounds_only = A_BOOL(4);
    int sample = A_INT(5);
    if (!data || !info || info->length < 3 || off < 0 || len <= 0 || off + len > data->length) return;
    const uint8_t *bytes = ARRAY_DATA(data, uint8_t) + off;
    int w, h;
    if (bounds_only) {
        if (gfx_image_info(bytes, (size_t)len, &w, &h)) {
            if (sample > 1) {
                w = (w + sample - 1) / sample;
                h = (h + sample - 1) / sample;
            }
            ARRAY_DATA(info, int32_t)[0] = w;
            ARRAY_DATA(info, int32_t)[1] = h;
            ARRAY_DATA(info, int32_t)[2] = 1;
        }
        return;
    }
    bool alpha;
    vm_gil_release(t);
    uint32_t *px = gfx_decode_image(bytes, (size_t)len, &w, &h, &alpha);
    if (px && sample > 1) {
        int nw = (w + sample - 1) / sample, nh = (h + sample - 1) / sample;
        uint32_t *s = sa_malloc((size_t)nw * (size_t)nh * 4);
        for (int y = 0; y < nh; y++)
            for (int x = 0; x < nw; x++) s[(size_t)y * nw + x] = px[(size_t)(y * sample) * w + x * sample];
        free(px);
        px = s;
        w = nw;
        h = nh;
    }
    vm_gil_acquire(t);
    if (!px) return;
    ArrayObject *out = vm_alloc_prim_array(t, 'I', w * h);
    if (out) memcpy(out->data, px, (size_t)w * (size_t)h * 4);
    free(px);
    ARRAY_DATA(info, int32_t)[0] = w;
    ARRAY_DATA(info, int32_t)[1] = h;
    ARRAY_DATA(info, int32_t)[2] = alpha ? 1 : 0;
    R_OBJ(out);
}

/* static byte[] nCompress(int[] px, int w, int h, int format, int quality) */
NATIVE(Bitmap_nCompress) {
    UNUSED_ARGS();
    ArrayObject *px = A_ARR(0);
    int w = A_INT(1), h = A_INT(2);
    if (!px || w <= 0 || h <= 0 || px->length < w * h) return;
    size_t len = 0;
    vm_gil_release(t);
    uint8_t *png = gfx_encode_png(ARRAY_DATA(px, uint32_t), w, h, w, &len);
    vm_gil_acquire(t);
    if (!png) return;
    ArrayObject *out = vm_alloc_prim_array(t, 'B', (int32_t)len);
    if (out) memcpy(out->data, png, len);
    free(png);
    R_OBJ(out);
}

/* static void nScale(int[] src, int sw, int sh, int[] dst, int dw, int dh, boolean filter) */
NATIVE(Bitmap_nScale) {
    UNUSED_ARGS();
    ArrayObject *src = A_ARR(0), *dst = A_ARR(3);
    int sw = A_INT(1), sh = A_INT(2), dw = A_INT(4), dh = A_INT(5);
    if (!src || !dst || sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0 || src->length < sw * sh || dst->length < dw * dh) return;
    GfxTarget s = {ARRAY_DATA(src, uint32_t), sw, sh, sw};
    GfxTarget d = {ARRAY_DATA(dst, uint32_t), dw, dh, dw};
    memset(d.px, 0, (size_t)dw * (size_t)dh * 4);
    GfxMatrix id;
    gfx_matrix_identity(&id);
    GfxClip clip = {0, 0, dw, dh, NULL, 0};
    GfxPaint p;
    memset(&p, 0, sizeof p);
    p.color = 0xFF000000u;
    p.filter = A_BOOL(6);
    p.xfer = GFX_XFER_SRC;
    gfx_draw_bitmap(&d, &id, &clip, &s, 0, 0, (float)sw, (float)sh, 0, 0, (float)dw, (float)dh, &p);
}

/* static void nFillMask(Canvas c, byte[] mask, byte[] verbs, int nverbs, float[] pts, int npts, int fillType, boolean aa, boolean intersect)
 * Renders a path coverage mask (device space, using the canvas matrix) and intersects/subtracts it with mask. */
NATIVE(Canvas_nClipPathMask) {
    UNUSED_ARGS();
    Object *c = A_OBJ(0);
    ArrayObject *mask = A_ARR(1);
    ArrayObject *verbs = A_ARR(2);
    int nv = A_INT(3);
    ArrayObject *pts = A_ARR(4);
    int np = A_INT(5);
    int fill = A_INT(6);
    bool intersect = A_BOOL(7);
    if (!c || !mask || !verbs || !pts) return;
    int w = FIELD_INT(c, "mWidth"), h = FIELD_INT(c, "mHeight");
    if (w <= 0 || h <= 0 || mask->length < w * h) return;
    ArrayObject *m = (ArrayObject *)FIELD_OBJ(c, "mMatrix", "[F");
    const float *mv = ARRAY_DATA(m, float);
    GfxMatrix gm = {mv[0], mv[1], mv[2], mv[3], mv[4], mv[5]};
    /* render the path as white coverage into a temporary ARGB target */
    uint32_t *tmp = sa_calloc((size_t)w * (size_t)h, 4);
    GfxTarget tt = {tmp, w, h, w};
    GfxClip clip = {0, 0, w, h, NULL, 0};
    GfxPaint p;
    memset(&p, 0, sizeof p);
    p.color = 0xFFFFFFFFu;
    p.aa = true;
    p.xfer = GFX_XFER_SRC_OVER;
    GfxPathData pd = {ARRAY_DATA(verbs, uint8_t), nv, ARRAY_DATA(pts, float), np, fill};
    gfx_draw_path(&tt, &gm, &clip, &pd, &p);
    uint8_t *mk = ARRAY_DATA(mask, uint8_t);
    for (size_t i = 0; i < (size_t)w * (size_t)h; i++) {
        int cov = (int)(tmp[i] >> 24);
        if (!intersect) cov = 255 - cov;
        mk[i] = (uint8_t)((mk[i] * (cov + 1)) >> 8);
    }
    free(tmp);
}

#define CV "Landroid/graphics/Canvas;"
#define PT "Landroid/graphics/Paint;"
#define BM "Landroid/graphics/Bitmap;"
static const NativeMethodReg g_regs[] = {
    {CV, "nDrawColor", "(" CV "II)V", Canvas_nDrawColor},
    {CV, "nDrawRect", "(" CV "FFFF" PT ")V", Canvas_nDrawRect},
    {CV, "nDrawRoundRect", "(" CV "FFFFFF" PT ")V", Canvas_nDrawRoundRect},
    {CV, "nDrawOval", "(" CV "FFFF" PT ")V", Canvas_nDrawOval},
    {CV, "nDrawArc", "(" CV "FFFFFFZ" PT ")V", Canvas_nDrawArc},
    {CV, "nDrawLine", "(" CV "FFFF" PT ")V", Canvas_nDrawLine},
    {CV, "nDrawPoints", "(" CV "[FII" PT ")V", Canvas_nDrawPoints},
    {CV, "nDrawPath", "(" CV "[BI[FII" PT ")V", Canvas_nDrawPath},
    {CV, "nDrawBitmapRect", "(" CV BM "FFFFFFFF" PT ")V", Canvas_nDrawBitmapRect},
    {CV, "nDrawBitmapMatrix", "(" CV BM "[F" PT ")V", Canvas_nDrawBitmapMatrix},
    {CV, "nDrawPixels", "(" CV "[IIIFFIIZ" PT ")V", Canvas_nDrawPixels},
    {CV, "nDrawText", "(" CV "[CIIFF" PT ")V", Canvas_nDrawText},
    {CV, "nClipPathMask", "(" CV "[B[BI[FIIZ)V", Canvas_nClipPathMask},
    {PT, "nMeasureText", "(JF[CII[FI)F", Paint_nMeasureText},
    {PT, "nGetFontMetrics", "(JF[F)V", Paint_nGetFontMetrics},
    {PT, "nGetTextBounds", "(JF[CII[F)V", Paint_nGetTextBounds},
    {PT, "nGetTextPath", "(JF[CIIFF)[Ljava/lang/Object;", Paint_nGetTextPath},
    {PT, "nHasGlyph", "(JI)Z", Paint_nHasGlyph},
    {"Landroid/graphics/Typeface;", "nDefault", "(Z)J", Typeface_nDefault},
    {"Landroid/graphics/Typeface;", "nLoad", "([B)J", Typeface_nLoad},
    {"Landroid/graphics/Typeface;", "nLoadFile", "(Ljava/lang/String;)J", Typeface_nLoadFile},
    {"Landroid/graphics/BitmapFactory;", "nDecode", "([BII[IZI)[I", BitmapFactory_nDecode},
    {BM, "nCompress", "([IIIII)[B", Bitmap_nCompress},
    {BM, "nScale", "([III[IIIZ)V", Bitmap_nScale},
};

void android_graphics_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
