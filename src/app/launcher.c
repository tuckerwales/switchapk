/*
 * Home screen, launch splash and error screen (see launcher.h).
 *
 * Layout on the 1280x720 buffer: a status bar (title, clock, Wi-Fi,
 * battery), a carousel of app tiles, a details panel with a Play button for
 * the selected app, and a bar of button hints that also work as touch
 * targets. The background and every icon are rendered once and cached, so
 * a frame is mostly bitmap blits; frames that change nothing are skipped.
 */
#include "launcher.h"
#include "apk_info.h"
#include "../gfx/gfx.h"

#include <dirent.h>
#include <math.h>
#include <strings.h>
#include <sys/stat.h>
#include <time.h>

#define LOG_TAG "launcher"

/* Icons are read from the xxxhdpi bucket (192 px) so they stay sharp on the large tiles. */
#define ICON_DENSITY 640

/* Palette taken from docs/assets/logo.jpg: the console's deep navy outline and screen, Joy-Con blue and red
 * (left and right, as on the hexagon border), and the green of "apk" and the Android robot. */
#define COL_BG_TOP 0xFF06141F
#define COL_BG_BOTTOM 0xFF0C2738
#define COL_CARD 0xFF0F2A3C
#define COL_CARD_HI 0xFF183A50
#define COL_TEXT 0xFFF5F8FA
#define COL_DIM 0xFF9DB3C2
#define COL_FAINT 0xFF5D798C
#define COL_ACCENT 0xFF47C86E    /* "apk" green */
#define COL_ACCENT_HI 0xFF84D056 /* Android green */
#define COL_ON_ACCENT 0xFF06281A
#define COL_BLUE 0xFF09B4E0      /* left Joy-Con */
#define COL_RED 0xFFE8343A       /* right Joy-Con */
#define COL_DANGER 0xFFFF5A5F
#define COL_WARN 0xFFFFD166

#define MARGIN 84
#define TILE 200
#define TILE_GAP 28
#define TILE_STRIDE (TILE + TILE_GAP)
#define TILE_TOP 176
#define TILE_RADIUS 30
#define TILE_GROW 0.07f
#define TILE_ICON 148
#define VISIBLE_TILES 5
#define PANEL_TOP 436
#define PANEL_BOTTOM 632
#define BAR_TOP 662

#define REPEAT_DELAY_NS 380000000LL
#define REPEAT_RATE_NS 70000000LL
#define DRAG_SLOP 22

/* ---- drawing helpers ------------------------------------------------------------- */

static GfxTarget g_t;
static GfxClip g_clip;
static GfxMatrix g_id;

static void ui_begin(uint32_t *px) {
    g_t = (GfxTarget){px, UI_W, UI_H, UI_W};
    g_clip = (GfxClip){0, 0, UI_W, UI_H, NULL, 0};
    gfx_matrix_identity(&g_id);
}

static void ui_set_clip(int l, int t, int r, int b) {
    g_clip.l = l < 0 ? 0 : l;
    g_clip.t = t < 0 ? 0 : t;
    g_clip.r = r > g_t.w ? g_t.w : r;
    g_clip.b = b > g_t.h ? g_t.h : b;
}

static void ui_reset_clip(void) { ui_set_clip(0, 0, g_t.w, g_t.h); }

static GfxPaint fill_paint(uint32_t color) {
    GfxPaint p;
    memset(&p, 0, sizeof p);
    p.color = color;
    p.style = GFX_FILL;
    p.aa = true;
    p.filter = true;
    p.xfer = GFX_XFER_SRC_OVER;
    p.miter = 4;
    return p;
}

static uint32_t alpha(uint32_t color, int a) {
    if (a < 0) a = 0;
    if (a > 255) a = 255;
    return (color & 0x00FFFFFFu) | ((uint32_t)a << 24);
}

static void fill_rect(float l, float t, float r, float b, uint32_t color) {
    GfxPaint p = fill_paint(color);
    gfx_draw_rect(&g_t, &g_id, &g_clip, l, t, r, b, &p);
}

static void fill_rrect(float l, float t, float r, float b, float rad, uint32_t color) {
    GfxPaint p = fill_paint(color);
    gfx_draw_round_rect(&g_t, &g_id, &g_clip, l, t, r, b, rad, rad, &p);
}

static void stroke_rrect(float l, float t, float r, float b, float rad, float width, uint32_t color) {
    GfxPaint p = fill_paint(color);
    p.style = GFX_STROKE;
    p.stroke_width = width;
    gfx_draw_round_rect(&g_t, &g_id, &g_clip, l, t, r, b, rad, rad, &p);
}

static void grad_rrect(float l, float t, float r, float b, float rad, uint32_t top, uint32_t bottom) {
    uint32_t cols[2] = {top, bottom};
    GfxShader sh;
    memset(&sh, 0, sizeof sh);
    sh.type = GFX_SHADER_LINEAR;
    sh.x0 = l;
    sh.y0 = t;
    sh.x1 = l;
    sh.y1 = b;
    sh.colors = cols;
    sh.ncolors = 2;
    gfx_matrix_identity(&sh.local);
    GfxPaint p = fill_paint(0xFF000000);
    p.shader = &sh;
    if (rad > 0) gfx_draw_round_rect(&g_t, &g_id, &g_clip, l, t, r, b, rad, rad, &p);
    else gfx_draw_rect(&g_t, &g_id, &g_clip, l, t, r, b, &p);
}

/* A rounded-rect outline shaded left to right, like the logo's blue-to-red hexagon border. */
static void stroke_rrect_hgrad(float l, float t, float r, float b, float rad, float width, uint32_t left,
                               uint32_t right) {
    uint32_t cols[2] = {left, right};
    GfxShader sh;
    memset(&sh, 0, sizeof sh);
    sh.type = GFX_SHADER_LINEAR;
    sh.x0 = l;
    sh.y0 = t;
    sh.x1 = r;
    sh.y1 = t;
    sh.colors = cols;
    sh.ncolors = 2;
    gfx_matrix_identity(&sh.local);
    GfxPaint p = fill_paint(0xFF000000);
    p.shader = &sh;
    p.style = GFX_STROKE;
    p.stroke_width = width;
    gfx_draw_round_rect(&g_t, &g_id, &g_clip, l, t, r, b, rad, rad, &p);
}

static void radial_glow(float cx, float cy, float radius, uint32_t color) {
    uint32_t cols[2] = {color, color & 0x00FFFFFFu};
    GfxShader sh;
    memset(&sh, 0, sizeof sh);
    sh.type = GFX_SHADER_RADIAL;
    sh.x0 = cx;
    sh.y0 = cy;
    sh.r = radius;
    sh.colors = cols;
    sh.ncolors = 2;
    gfx_matrix_identity(&sh.local);
    GfxPaint p = fill_paint(0xFF000000);
    p.shader = &sh;
    gfx_draw_rect(&g_t, &g_id, &g_clip, cx - radius, cy - radius, cx + radius, cy + radius, &p);
}

static void fill_circle(float cx, float cy, float r, uint32_t color) {
    GfxPaint p = fill_paint(color);
    gfx_draw_oval(&g_t, &g_id, &g_clip, cx - r, cy - r, cx + r, cy + r, &p);
}

static void fill_triangle(float x0, float y0, float x1, float y1, float x2, float y2, uint32_t color) {
    static const uint8_t verbs[] = {GFX_VERB_MOVE, GFX_VERB_LINE, GFX_VERB_LINE, GFX_VERB_CLOSE};
    float pts[6] = {x0, y0, x1, y1, x2, y2};
    GfxPathData path = {verbs, 4, pts, 6, GFX_FILL_WINDING};
    GfxPaint p = fill_paint(color);
    gfx_draw_path(&g_t, &g_id, &g_clip, &path, &p);
}

static void draw_bitmap(const uint32_t *px, int w, int h, float l, float t, float r, float b) {
    if (!px) return;
    GfxTarget src = {(uint32_t *)px, w, h, w};
    GfxPaint p = fill_paint(0xFFFFFFFF);
    gfx_draw_bitmap(&g_t, &g_id, &g_clip, &src, 0, 0, (float)w, (float)h, l, t, r, b, &p);
}

/* UTF-8 to UTF-16 code units (BMP; anything else becomes '?'). Returns the length; *out is malloc'd. */
static int to_utf16(const char *s, uint16_t **out) {
    size_t n = s ? strlen(s) : 0;
    uint16_t *u = sa_malloc((n + 1) * sizeof *u);
    int len = 0;
    for (size_t i = 0; i < n;) {
        unsigned c = (unsigned char)s[i];
        unsigned cp = '?';
        size_t need = c < 0x80 ? 1 : (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : (c & 0xF8) == 0xF0 ? 4 : 0;
        if (need == 0 || i + need > n) {
            i++;
        } else if (need == 1) {
            cp = c;
            i++;
        } else {
            unsigned v = c & (0x7F >> need);
            bool ok = true;
            for (size_t k = 1; k < need; k++) {
                unsigned cc = (unsigned char)s[i + k];
                if ((cc & 0xC0) != 0x80) ok = false;
                v = (v << 6) | (cc & 0x3F);
            }
            if (ok && v >= 0x80 && v <= 0xFFFF && !(v >= 0xD800 && v <= 0xDFFF)) cp = v;
            i += ok ? need : 1;
        }
        u[len++] = (uint16_t)cp;
    }
    *out = u;
    return len;
}

static float text_width(const char *s, float size, bool bold) {
    GfxFont *f = gfx_font_default(bold);
    if (!f || !s) return 0;
    uint16_t *u;
    int n = to_utf16(s, &u);
    float w = gfx_measure_text(f, size, u, n, NULL);
    free(u);
    return w;
}

/* Draws s at (x, baseline). When max_w > 0 the text is cut to fit and ends in an ellipsis. Returns the width. */
static float draw_text_fit(const char *s, float x, float baseline, float size, uint32_t color, bool bold, float max_w) {
    GfxFont *f = gfx_font_default(bold);
    if (!f || !s) return 0;
    uint16_t *u;
    int n = to_utf16(s, &u);
    float *widths = sa_malloc(((size_t)n + 1) * sizeof *widths);
    float w = gfx_measure_text(f, size, u, n, widths);
    if (max_w > 0 && w > max_w && n > 0) {
        uint16_t ell = 0x2026;
        float ew = gfx_measure_text(f, size, &ell, 1, NULL);
        float acc = 0;
        int keep = 0;
        while (keep < n && acc + widths[keep] + ew <= max_w) acc += widths[keep++];
        while (keep > 0 && u[keep - 1] == ' ') acc -= widths[--keep];
        u[keep] = ell;
        n = keep + 1;
        w = acc + ew;
    }
    GfxPaint p = fill_paint(color);
    gfx_draw_text(&g_t, &g_id, &g_clip, f, size, u, n, x, baseline, 0, false, &p);
    free(widths);
    free(u);
    return w;
}

static float draw_text(const char *s, float x, float baseline, float size, uint32_t color, bool bold) {
    return draw_text_fit(s, x, baseline, size, color, bold, 0);
}

static void draw_text_right(const char *s, float right, float baseline, float size, uint32_t color, bool bold) {
    draw_text(s, right - text_width(s, size, bold), baseline, size, color, bold);
}

static void draw_text_center(const char *s, float cx, float baseline, float size, uint32_t color, bool bold,
                             float max_w) {
    float w = text_width(s, size, bold);
    if (max_w > 0 && w > max_w) w = max_w;
    draw_text_fit(s, cx - w / 2, baseline, size, color, bold, max_w);
}

/* ---- cached artwork ----------------------------------------------------------------- */

static uint32_t *new_canvas(int w, int h) { return sa_calloc((size_t)w * (size_t)h, sizeof(uint32_t)); }

/* Runs draw calls against a w x h buffer instead of the screen. */
typedef struct {
    GfxTarget t;
    GfxClip clip;
} SavedTarget;

static SavedTarget push_target(uint32_t *px, int w, int h) {
    SavedTarget s = {g_t, g_clip};
    g_t = (GfxTarget){px, w, h, w};
    g_clip = (GfxClip){0, 0, w, h, NULL, 0};
    gfx_matrix_identity(&g_id);
    return s;
}

static void pop_target(SavedTarget s) {
    g_t = s.t;
    g_clip = s.clip;
}

static uint32_t *render_background(uint32_t glow_a, uint32_t glow_b) {
    uint32_t *bg = new_canvas(UI_W, UI_H);
    SavedTarget s = push_target(bg, UI_W, UI_H);
    grad_rrect(0, 0, UI_W, UI_H, 0, COL_BG_TOP, COL_BG_BOTTOM);
    radial_glow(120, -80, 640, glow_a);
    radial_glow(UI_W - 60, UI_H + 120, 700, glow_b);
    radial_glow(UI_W / 2.0f, UI_H + 260, 560, 0x1447C86E);
    pop_target(s);
    return bg;
}

/* The icon fitted into a size x size square, centered, on transparent pixels. */
static uint32_t *fit_icon(const uint32_t *px, int w, int h, int size) {
    uint32_t *out = new_canvas(size, size);
    int dw = size, dh = size;
    if (w > h) dh = (int)((int64_t)h * size / w);
    else if (h > w) dw = (int)((int64_t)w * size / h);
    if (dw < 1) dw = 1;
    if (dh < 1) dh = 1;
    SavedTarget s = push_target(out, size, size);
    float x = (float)(size - dw) / 2, y = (float)(size - dh) / 2;
    draw_bitmap(px, w, h, x, y, x + (float)dw, y + (float)dh);
    pop_target(s);
    return out;
}

/* A colored rounded square with the label's first letter, for APKs without a bitmap icon. */
static uint32_t *letter_icon(const char *label, int size) {
    /* logo colors: Joy-Con blue, Android green, Joy-Con red, "apk" green, and a teal between blue and green */
    static const struct {
        uint32_t top, bottom;
    } tones[] = {
        {0xFF2CC6EE, 0xFF0787B8}, {0xFF9BDC6A, 0xFF4FA83A}, {0xFFF2585C, 0xFFB81F2A},
        {0xFF5ED884, 0xFF2A9A55}, {0xFF2FD0C4, 0xFF138A93},
    };
    uint32_t hash = 2166136261u;
    for (const char *p = label; p && *p; p++) hash = (hash ^ (unsigned char)*p) * 16777619u;
    const int tone = (int)(hash % SA_ARRAY_LEN(tones));
    uint32_t *out = new_canvas(size, size);
    SavedTarget s = push_target(out, size, size);
    float r = (float)size * 0.22f;
    grad_rrect(0, 0, (float)size, (float)size, r, tones[tone].top, tones[tone].bottom);
    char letter[8] = "?";
    if (label && label[0]) {
        unsigned char c = (unsigned char)label[0];
        size_t n = c < 0x80 ? 1 : (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : 1;
        if (n > strlen(label)) n = 1;
        memcpy(letter, label, n);
        letter[n] = 0;
        if (n == 1 && letter[0] >= 'a' && letter[0] <= 'z') letter[0] = (char)(letter[0] - 'a' + 'A');
    }
    float fs = (float)size * 0.52f;
    draw_text_center(letter, (float)size / 2, (float)size / 2 + fs * 0.36f, fs, 0xF2FFFFFF, true, 0);
    pop_target(s);
    return out;
}

/* ---- shared widgets ----------------------------------------------------------------------- */

enum { HIT_BUTTON = 1, HIT_TILE = 2 };

typedef struct {
    int l, t, r, b;
    int kind;
    uint32_t value; /* button bit, or tile index */
} Hit;

typedef struct {
    Hit h[32];
    int n;
} Hits;

static void add_hit(Hits *hits, float l, float t, float r, float b, int kind, uint32_t value) {
    if (hits->n >= (int)SA_ARRAY_LEN(hits->h)) return;
    hits->h[hits->n++] = (Hit){(int)l, (int)t, (int)r, (int)b, kind, value};
}

static const Hit *hit_at(const Hits *hits, int x, int y) {
    for (int i = hits->n - 1; i >= 0; i--) {
        const Hit *h = &hits->h[i];
        if (x >= h->l && x < h->r && y >= h->t && y < h->b) return h;
    }
    return NULL;
}

/* A round controller-button glyph: light disc, dark letter. */
static void button_glyph(const char *letter, float cx, float cy, float r, uint32_t disc, uint32_t ink) {
    fill_circle(cx, cy, r, disc);
    float fs = r * 1.15f;
    draw_text_center(letter, cx, cy + fs * 0.36f, fs, ink, true, 0);
}

typedef struct {
    const char *glyph;
    const char *label;
    uint32_t btn;
} Hint;

/* Right-aligned button hints in the bottom bar; each one is also a touch target. */
static void draw_hints(Hits *hits, const Hint *hints, int n) {
    float x = UI_W - MARGIN;
    const float cy = BAR_TOP + 29;
    for (int i = n - 1; i >= 0; i--) {
        float lw = text_width(hints[i].label, 21, false);
        float l = x - lw - 38;
        button_glyph(hints[i].glyph, l + 14, cy, 14, COL_TEXT, COL_BG_TOP);
        draw_text(hints[i].label, l + 36, cy + 7, 21, COL_TEXT, false);
        add_hit(hits, l - 10, BAR_TOP + 4, x + 10, UI_H, HIT_BUTTON, hints[i].btn);
        x = l - 34;
    }
}

static void draw_bottom_bar(void) { fill_rect(MARGIN, BAR_TOP, UI_W - MARGIN, BAR_TOP + 1, 0x1FFFFFFF); }

/* A pointy-top hexagon of radius r around (cx, cy), filled with a horizontal two-color shade. */
static void fill_hexagon(float cx, float cy, float r, uint32_t left, uint32_t right) {
    static const uint8_t verbs[] = {GFX_VERB_MOVE, GFX_VERB_LINE, GFX_VERB_LINE, GFX_VERB_LINE,
                                    GFX_VERB_LINE, GFX_VERB_LINE, GFX_VERB_CLOSE};
    float pts[12];
    for (int i = 0; i < 6; i++) {
        float a = (float)(M_PI / 3.0 * i - M_PI / 2.0);
        pts[i * 2] = cx + r * cosf(a);
        pts[i * 2 + 1] = cy + r * sinf(a);
    }
    uint32_t cols[2] = {left, right};
    GfxShader sh;
    memset(&sh, 0, sizeof sh);
    sh.type = GFX_SHADER_LINEAR;
    sh.x0 = cx - r;
    sh.y0 = cy;
    sh.x1 = cx + r;
    sh.y1 = cy;
    sh.colors = cols;
    sh.ncolors = 2;
    gfx_matrix_identity(&sh.local);
    GfxPathData path = {verbs, 7, pts, 12, GFX_FILL_WINDING};
    GfxPaint p = fill_paint(0xFF000000);
    p.shader = &sh;
    gfx_draw_path(&g_t, &g_id, &g_clip, &path, &p);
}

/* The app mark, after the logo: a blue-to-red hexagon rim around a green hexagon with a play triangle. */
static void draw_logo(float x, float y, float size) {
    const float cx = x + size / 2, cy = y + size / 2, r = size * 0.56f;
    fill_hexagon(cx, cy, r, COL_BLUE, COL_RED);
    fill_hexagon(cx, cy, r * 0.80f, 0xFFFFFFFF, 0xFFFFFFFF);
    fill_hexagon(cx, cy, r * 0.70f, COL_ACCENT_HI, COL_ACCENT);
    const float tx = cx + size * 0.03f, s = size * 0.17f;
    fill_triangle(tx - s * 0.8f, cy - s, tx - s * 0.8f, cy + s, tx + s, cy, 0xFF0B2C3F);
}

static void draw_battery(float right, float cy, const UiStatus *st) {
    const float w = 34, h = 18;
    float l = right - w - 4;
    stroke_rrect(l, cy - h / 2, l + w, cy + h / 2, 5, 2, COL_DIM);
    fill_rrect(l + w + 1, cy - 4, l + w + 4, cy + 4, 1.5f, COL_DIM);
    int lvl = st->battery < 0 ? 0 : st->battery > 100 ? 100 : st->battery;
    uint32_t c = st->charging ? COL_ACCENT : lvl <= 15 ? COL_DANGER : COL_TEXT;
    float inner = (w - 6) * (float)lvl / 100.0f;
    if (inner > 0.5f) fill_rrect(l + 3, cy - h / 2 + 3, l + 3 + inner, cy + h / 2 - 3, 2.5f, c);
    char pct[8];
    snprintf(pct, sizeof pct, "%d%%", lvl);
    draw_text_right(pct, l - 8, cy + 7, 20, COL_DIM, false);
    if (st->charging) {
        float bx = l + w / 2;
        fill_triangle(bx + 2, cy - 7, bx - 5, cy + 1, bx, cy + 1, COL_BG_TOP);
        fill_triangle(bx - 2, cy + 7, bx + 5, cy - 1, bx, cy - 1, COL_BG_TOP);
    }
}

/* Returns the left edge of what it drew. */
static float draw_status(const UiStatus *st) {
    float x = UI_W - MARGIN;
    const float cy = 54;
    if (st->clock[0]) {
        float w = text_width(st->clock, 26, true);
        draw_text(st->clock, x - w, cy + 9, 26, COL_TEXT, true);
        x -= w + 26;
    }
    if (st->has_battery) {
        draw_battery(x, cy, st);
        char pct[8];
        snprintf(pct, sizeof pct, "%d%%", st->battery);
        x -= 34 + 4 + 8 + text_width(pct, 20, false) + 22;
    }
    for (int i = 0; i < 4; i++) {
        float bh = 5 + (float)i * 4.5f;
        float l = x - 32 + (float)i * 8;
        uint32_t c = st->wifi >= 0 && i <= st->wifi ? COL_TEXT : 0x40FFFFFF;
        fill_rrect(l, cy + 9 - bh, l + 5, cy + 9, 1.5f, c);
    }
    return x - 32;
}

static void draw_header(const char *subtitle, const UiStatus *st) {
    draw_logo(MARGIN, 32, 44);
    /* "switch" + "apk" in the accent, as in the project logo */
    float w = draw_text("switch", MARGIN + 60, 64, 30, COL_TEXT, true);
    w += draw_text("apk", MARGIN + 60 + w, 64, 30, COL_ACCENT, true);
    if (subtitle) draw_text(subtitle, MARGIN + 60 + w + 16, 64, 21, COL_FAINT, false);
    draw_status(st);
}

static bool status_eq(const UiStatus *a, const UiStatus *b) {
    return !strcmp(a->clock, b->clock) && a->has_battery == b->has_battery && a->battery == b->battery &&
           a->charging == b->charging && a->wifi == b->wifi;
}

static float approach(float v, float target, float dt, float speed, bool *moving) {
    float k = 1.0f - expf(-dt * speed);
    v += (target - v) * k;
    if (fabsf(target - v) < 0.002f) v = target;
    else *moving = true;
    return v;
}

/* Key repeat for held navigation buttons. Returns the presses for this frame. */
typedef struct {
    uint32_t btn;
    int64_t next;
} Repeat;

static uint32_t repeat_presses(Repeat *r, const UiInput *in, uint32_t nav) {
    uint32_t press = in->down;
    if (in->down & nav) {
        r->btn = in->down & nav & (0u - (in->down & nav)); /* lowest set bit */
        r->next = in->now_ns + REPEAT_DELAY_NS;
    } else if (r->btn && (in->held & r->btn) && in->now_ns >= r->next) {
        press |= r->btn;
        r->next = in->now_ns + REPEAT_RATE_NS;
    }
    if (!(in->held & r->btn)) r->btn = 0;
    return press;
}

/* Touch tracking shared by both screens: a tap lands on the same target under the press and the release. */
typedef struct {
    bool down;
    int x0, y0, x, y;
    bool drag;
    bool has_hit;
    Hit hit; /* target under the press */
} Touch;

enum { TOUCH_NONE, TOUCH_PRESS, TOUCH_MOVE, TOUCH_TAP, TOUCH_RELEASE };

static int touch_update(Touch *t, const UiInput *in, const Hits *hits) {
    if (in->touching && !t->down) {
        t->down = true;
        t->drag = false;
        t->x0 = t->x = in->touch_x;
        t->y0 = t->y = in->touch_y;
        const Hit *h = hit_at(hits, t->x0, t->y0);
        t->has_hit = h != NULL;
        if (h) t->hit = *h;
        return TOUCH_PRESS;
    }
    if (in->touching && t->down) {
        if (in->touch_x == t->x && in->touch_y == t->y) return TOUCH_NONE;
        t->x = in->touch_x;
        t->y = in->touch_y;
        if (abs(t->x - t->x0) > DRAG_SLOP || abs(t->y - t->y0) > DRAG_SLOP) t->drag = true;
        return TOUCH_MOVE;
    }
    if (!in->touching && t->down) {
        t->down = false;
        if (t->drag || !t->has_hit) return TOUCH_RELEASE;
        const Hit *h = hit_at(hits, t->x, t->y);
        return h && h->kind == t->hit.kind && h->value == t->hit.value ? TOUCH_TAP : TOUCH_RELEASE;
    }
    return TOUCH_NONE;
}

/* ---- launcher state ------------------------------------------------------------------ */

typedef struct {
    char *file;  /* name inside the APK folder */
    char *path;
    char *label;
    char *package;
    char *version;
    int64_t size;
    int64_t mtime;
    uint32_t *icon; /* TILE_ICON x TILE_ICON */
    bool loaded;
    float hl;       /* 0..1 selection highlight, animated */
} Item;

typedef struct {
    char *file;
    int64_t when; /* unix seconds */
} Played;

enum { SORT_RECENT = 0, SORT_NAME = 1 };

struct Launcher {
    char *dir;
    char *state_path;
    char *cache_dir;
    uint32_t *bg;

    Item *items;
    int count;
    bool loading;
    int loaded;

    Played *played;
    int nplayed;
    int sort;
    char *focus; /* file to select once loaded */

    int sel;
    float cam, cam_target; /* index of the leftmost visible tile */
    int64_t last_ns;
    bool dirty;
    UiStatus status;

    Repeat rep;
    Touch touch;
    float drag_cam;
    Hits hits;

    char toast[96];
    int64_t toast_until;
    bool toast_shown;

    char *chosen;
};

static int64_t played_time(const Launcher *l, const char *file) {
    for (int i = 0; i < l->nplayed; i++)
        if (!strcmp(l->played[i].file, file)) return l->played[i].when;
    return 0;
}

static void load_state(Launcher *l) {
    FILE *f = fopen(l->state_path, "r");
    if (!f) return;
    char line[1024];
    while (fgets(line, sizeof line, f)) {
        line[strcspn(line, "\r\n")] = 0;
        if (!strncmp(line, "sort=", 5)) {
            l->sort = !strcmp(line + 5, "name") ? SORT_NAME : SORT_RECENT;
        } else if (!strncmp(line, "last=", 5) && line[5]) {
            free(l->focus);
            l->focus = sa_strdup(line + 5);
        } else if (!strncmp(line, "played=", 7)) {
            char *end = NULL;
            long long when = strtoll(line + 7, &end, 10);
            if (end && *end == ' ' && end[1] && when > 0) {
                l->played = sa_realloc(l->played, (size_t)(l->nplayed + 1) * sizeof *l->played);
                l->played[l->nplayed++] = (Played){sa_strdup(end + 1), (int64_t)when};
            }
        }
    }
    fclose(f);
}

static void save_state(const Launcher *l, const char *last) {
    char *tmp = sa_sprintf("%s.tmp", l->state_path);
    FILE *f = fopen(tmp, "w");
    if (!f) {
        LOGW("cannot write %s", tmp);
        free(tmp);
        return;
    }
    fprintf(f, "sort=%s\n", l->sort == SORT_NAME ? "name" : "recent");
    if (last) fprintf(f, "last=%s\n", last);
    for (int i = 0; i < l->nplayed; i++) fprintf(f, "played=%lld %s\n", (long long)l->played[i].when, l->played[i].file);
    fclose(f);
    remove(l->state_path);
    if (rename(tmp, l->state_path) != 0) LOGW("cannot replace %s", l->state_path);
    free(tmp);
}

static void free_items(Launcher *l) {
    for (int i = 0; i < l->count; i++) {
        Item *it = &l->items[i];
        free(it->file);
        free(it->path);
        free(it->label);
        free(it->package);
        free(it->version);
        free(it->icon);
    }
    free(l->items);
    l->items = NULL;
    l->count = 0;
}

/* Lists the folder; labels and icons come later, a few per frame (load_some). */
static void scan(Launcher *l) {
    free_items(l);
    DIR *d = opendir(l->dir);
    if (d) {
        struct dirent *e;
        while ((e = readdir(d)) != NULL) {
            size_t n = strlen(e->d_name);
            if (e->d_name[0] == '.' || n <= 4 || strcasecmp(e->d_name + n - 4, ".apk") != 0) continue;
            l->items = sa_realloc(l->items, (size_t)(l->count + 1) * sizeof *l->items);
            Item *it = &l->items[l->count++];
            memset(it, 0, sizeof *it);
            it->file = sa_strdup(e->d_name);
            it->path = sa_sprintf("%s/%s", l->dir, e->d_name);
            struct stat sb;
            if (stat(it->path, &sb) == 0) {
                if (S_ISDIR(sb.st_mode)) {
                    free(it->file);
                    free(it->path);
                    l->count--;
                    continue;
                }
                it->size = (int64_t)sb.st_size;
                it->mtime = (int64_t)sb.st_mtime;
            }
        }
        closedir(d);
    }
    l->loaded = 0;
    l->loading = true;
    l->dirty = true;
}

/* Icon cache: one file per APK in cache_dir, "<file>.cache", so the list opens instantly after an app ends
 * (the NRO restarts each time). Layout: a "switchapk-icon 1" line, size, mtime, label, package, version lines,
 * then a TILE_ICON square PNG. A size or mtime change makes the entry stale. */
#define CACHE_MAGIC "switchapk-icon 1"

static char *cache_path(const Launcher *l, const Item *it) { return sa_sprintf("%s/%s.cache", l->cache_dir, it->file); }

static char *next_line(char **p, char *end) {
    char *s = *p, *nl = s;
    while (nl < end && *nl != '\n') nl++;
    if (nl >= end) return NULL;
    *nl = 0;
    *p = nl + 1;
    return s;
}

static bool load_cached(const Launcher *l, Item *it) {
    char *path = cache_path(l, it);
    size_t len = 0;
    uint8_t *data = sa_read_file(path, &len);
    free(path);
    if (!data) return false;
    char *p = (char *)data, *end = (char *)data + len;
    char *magic = next_line(&p, end), *size = next_line(&p, end), *mtime = next_line(&p, end);
    char *label = next_line(&p, end), *pkg = next_line(&p, end), *ver = next_line(&p, end);
    bool ok = magic && ver && !strcmp(magic, CACHE_MAGIC) && strtoll(size, NULL, 10) == it->size &&
              strtoll(mtime, NULL, 10) == it->mtime && label[0];
    if (ok) {
        int w = 0, h = 0;
        uint32_t *px = gfx_decode_image((uint8_t *)p, (size_t)(end - p), &w, &h, NULL);
        if (px && w == TILE_ICON && h == TILE_ICON) {
            it->icon = px;
            it->label = sa_strdup(label);
            it->package = pkg[0] ? sa_strdup(pkg) : NULL;
            it->version = ver[0] ? sa_strdup(ver) : NULL;
        } else {
            free(px);
            ok = false;
        }
    }
    free(data);
    return ok;
}

static void put_field(FILE *f, const char *s) {
    for (; s && *s; s++) fputc(*s == '\n' || *s == '\r' ? ' ' : *s, f);
    fputc('\n', f);
}

static void save_cached(const Launcher *l, const Item *it) {
    size_t png_len = 0;
    uint8_t *png = gfx_encode_png(it->icon, TILE_ICON, TILE_ICON, TILE_ICON, &png_len);
    if (!png) return;
    char *path = cache_path(l, it);
    FILE *f = fopen(path, "wb");
    if (f) {
        fprintf(f, "%s\n%lld\n%lld\n", CACHE_MAGIC, (long long)it->size, (long long)it->mtime);
        put_field(f, it->label);
        put_field(f, it->package);
        put_field(f, it->version);
        fwrite(png, 1, png_len, f);
        fclose(f);
    }
    free(path);
    free(png);
}

static void load_item(Launcher *l, Item *it) {
    it->loaded = true;
    if (load_cached(l, it)) return;
    ApkIdentity id;
    apk_read_identity(it->path, ICON_DENSITY, &id);
    it->label = id.label;
    it->package = id.package;
    it->version = id.version;
    it->icon = id.icon ? fit_icon(id.icon, id.icon_w, id.icon_h, TILE_ICON) : letter_icon(id.label, TILE_ICON);
    free(id.icon);
    save_cached(l, it);
}

static const Launcher *g_sort_ctx;

static int cmp_items(const void *a, const void *b) {
    const Item *x = a, *y = b;
    if (g_sort_ctx->sort == SORT_RECENT) {
        int64_t px = played_time(g_sort_ctx, x->file), py = played_time(g_sort_ctx, y->file);
        if (px != py) return px > py ? -1 : 1;
    }
    int c = strcasecmp(x->label ? x->label : "", y->label ? y->label : "");
    return c ? c : strcasecmp(x->file, y->file);
}

static void clamp_camera(Launcher *l, bool snap) {
    int max_cam = l->count > VISIBLE_TILES ? l->count - VISIBLE_TILES : 0;
    float t = l->cam_target;
    if ((float)l->sel < t) t = (float)l->sel;
    if ((float)l->sel > t + VISIBLE_TILES - 1) t = (float)(l->sel - VISIBLE_TILES + 1);
    if (t > (float)max_cam) t = (float)max_cam;
    if (t < 0) t = 0;
    l->cam_target = roundf(t);
    if (snap) l->cam = l->cam_target;
}

/* Sorts, keeping the selection on `keep` (a file name) when it is still listed. */
static void sort_items(Launcher *l, const char *keep) {
    g_sort_ctx = l;
    if (l->count > 1) qsort(l->items, (size_t)l->count, sizeof *l->items, cmp_items);
    l->sel = 0;
    for (int i = 0; keep && i < l->count; i++)
        if (!strcmp(l->items[i].file, keep)) l->sel = i;
    for (int i = 0; i < l->count; i++) l->items[i].hl = i == l->sel ? 1.0f : 0.0f;
    clamp_camera(l, true);
}

static void load_some(Launcher *l) {
    /* About two frames of work, at least one APK, so the progress bar moves. */
    int64_t until = (int64_t)sa_time_ns() + 30000000LL;
    do {
        if (l->loaded >= l->count) break;
        load_item(l, &l->items[l->loaded++]);
    } while ((int64_t)sa_time_ns() < until);
    if (l->loaded >= l->count) {
        l->loading = false;
        l->cam_target = 0;
        sort_items(l, l->focus);
    }
    l->dirty = true;
}

static void show_toast(Launcher *l, const char *msg, int64_t now) {
    snprintf(l->toast, sizeof l->toast, "%s", msg);
    l->toast_until = now + 1800000000LL;
    l->dirty = true;
}

Launcher *launcher_create(const char *apk_dir, const char *state_dir) {
    Launcher *l = sa_calloc(1, sizeof *l);
    l->dir = sa_strdup(apk_dir);
    l->state_path = sa_sprintf("%s/launcher.ini", state_dir);
    l->cache_dir = sa_sprintf("%s/icons", state_dir);
    sa_mkdirs(l->cache_dir);
    l->bg = render_background(0x3009B4E0, 0x2AE8343A);
    load_state(l);
    scan(l);
    return l;
}

void launcher_destroy(Launcher *l) {
    if (!l) return;
    free_items(l);
    for (int i = 0; i < l->nplayed; i++) free(l->played[i].file);
    free(l->played);
    free(l->focus);
    free(l->chosen);
    free(l->bg);
    free(l->dir);
    free(l->state_path);
    free(l->cache_dir);
    free(l);
}

const char *launcher_chosen(const Launcher *l) { return l->chosen; }

static void record_launch(Launcher *l, const Item *it) {
    int64_t now = (int64_t)time(NULL);
    if (now <= 0) now = 1;
    int i = 0;
    while (i < l->nplayed && strcmp(l->played[i].file, it->file) != 0) i++;
    if (i == l->nplayed) {
        l->played = sa_realloc(l->played, (size_t)(l->nplayed + 1) * sizeof *l->played);
        l->played[l->nplayed++] = (Played){sa_strdup(it->file), now};
    } else {
        l->played[i].when = now;
    }
    save_state(l, it->file);
}

/* ---- launcher drawing ------------------------------------------------------------------- */

static void format_size(int64_t bytes, char *out, size_t n) {
    if (bytes >= 1024 * 1024) snprintf(out, n, "%.1f MB", (double)bytes / (1024.0 * 1024.0));
    else snprintf(out, n, "%d KB", (int)((bytes + 1023) / 1024));
}

static void format_played(int64_t when, char *out, size_t n) {
    if (when <= 0) {
        snprintf(out, n, "Never played");
        return;
    }
    int64_t ago = (int64_t)time(NULL) - when;
    if (ago < 0) ago = 0;
    if (ago < 60) snprintf(out, n, "Played just now");
    else if (ago < 3600) snprintf(out, n, "Played %d min ago", (int)(ago / 60));
    else if (ago < 86400) snprintf(out, n, "Played %d h ago", (int)(ago / 3600));
    else if (ago < 2 * 86400) snprintf(out, n, "Played yesterday");
    else if (ago < 30 * 86400) snprintf(out, n, "Played %d days ago", (int)(ago / 86400));
    else {
        time_t t = (time_t)when;
        struct tm tm;
        localtime_r(&t, &tm);
        snprintf(out, n, "Played on %04d-%02d-%02d", tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday);
    }
}

static float tile_x(const Launcher *l, int i) { return MARGIN + ((float)i - l->cam) * TILE_STRIDE; }

static void draw_tile(Launcher *l, int i) {
    Item *it = &l->items[i];
    const float hl = it->hl;
    const float cx = tile_x(l, i) + TILE / 2.0f, cy = TILE_TOP + TILE / 2.0f;
    const float half = TILE / 2.0f * (1.0f + TILE_GROW * hl);
    const float L = cx - half, T = cy - half, R = cx + half, B = cy + half;
    const float rad = TILE_RADIUS * (1.0f + TILE_GROW * hl);

    fill_rrect(L + 4, T + 14, R - 4, B + 14, rad, 0x30000000);
    fill_rrect(L, T + 6, R, B + 6, rad, 0x38000000);
    if (hl > 0.01f) {
        for (int k = 4; k >= 1; k--) {
            float e = 7 + (float)k * 4;
            int a = (int)(30 * hl / k);
            stroke_rrect_hgrad(L - e, T - e, R + e, B + e, rad + e, 4, alpha(COL_BLUE, a), alpha(COL_RED, a));
        }
    }
    grad_rrect(L, T, R, B, rad, COL_CARD_HI, COL_CARD);
    stroke_rrect(L + 0.5f, T + 0.5f, R - 0.5f, B - 0.5f, rad, 1, 0x14FFFFFF);
    if (it->icon) {
        float ih = TILE_ICON / 2.0f * (1.0f + TILE_GROW * hl);
        draw_bitmap(it->icon, TILE_ICON, TILE_ICON, cx - ih, cy - ih, cx + ih, cy + ih);
    } else {
        /* still loading: a soft placeholder */
        fill_rrect(cx - 56, cy - 56, cx + 56, cy + 56, 26, 0x14FFFFFF);
    }
    if (hl > 0.01f) {
        const float e = 7;
        int a = (int)(255 * hl);
        stroke_rrect_hgrad(L - e, T - e, R + e, B + e, rad + e, 4.5f, alpha(COL_BLUE, a), alpha(COL_RED, a));
    }
    if (it->loaded && played_time(l, it->file) == 0) {
        const float bw = 50, bh = 24;
        fill_rrect(R - bw - 12, T + 12, R - 12, T + 12 + bh, bh / 2, COL_RED);
        draw_text_center("NEW", R - 12 - bw / 2, T + 12 + 17, 14, 0xFFFFFFFF, true, 0);
    }
    /* name under every tile, brighter for the selected one */
    uint32_t c = hl > 0.5f ? COL_TEXT : COL_DIM;
    draw_text_center(it->label ? it->label : it->file, cx, TILE_TOP + TILE + 38, 19, c, hl > 0.5f, TILE + 12);
    add_hit(&l->hits, cx - TILE / 2.0f, TILE_TOP - 10, cx + TILE / 2.0f, TILE_TOP + TILE + 48, HIT_TILE,
            (uint32_t)i);
}

static void draw_details(Launcher *l) {
    const Item *it = &l->items[l->sel];
    const float L = MARGIN, R = UI_W - MARGIN, T = PANEL_TOP, B = PANEL_BOTTOM;
    grad_rrect(L, T, R, B, 28, 0xC8143448, 0xC80D2636);
    stroke_rrect(L + 0.5f, T + 0.5f, R - 0.5f, B - 0.5f, 28, 1, 0x18FFFFFF);

    /* Play button */
    const float bw = 236, bh = 76, bl = R - 40 - bw, bt = (T + B) / 2 - bh / 2;
    fill_rrect(bl, bt + 6, bl + bw, bt + bh + 6, bh / 2, 0x40000000);
    grad_rrect(bl, bt, bl + bw, bt + bh, bh / 2, COL_ACCENT_HI, COL_ACCENT);
    button_glyph("A", bl + 46, bt + bh / 2, 19, COL_ON_ACCENT, COL_ACCENT);
    draw_text("Play", bl + 82, bt + bh / 2 + 11, 32, COL_ON_ACCENT, true);
    add_hit(&l->hits, bl, bt, bl + bw, bt + bh, HIT_BUTTON, UI_BTN_A);

    const float tx = L + 40, max_w = bl - tx - 32;
    draw_text_fit(it->label ? it->label : it->file, tx, T + 66, 38, COL_TEXT, true, max_w);

    char meta[512], size[32];
    format_size(it->size, size, sizeof size);
    if (it->package && it->version) snprintf(meta, sizeof meta, "%s  \xC2\xB7  v%s  \xC2\xB7  %s", it->package, it->version, size);
    else if (it->package) snprintf(meta, sizeof meta, "%s  \xC2\xB7  %s", it->package, size);
    else snprintf(meta, sizeof meta, "%s  \xC2\xB7  %s", it->file, size);
    draw_text_fit(meta, tx, T + 110, 21, COL_DIM, false, max_w);

    char when[64];
    int64_t played = played_time(l, it->file);
    format_played(played, when, sizeof when);
    fill_circle(tx + 6, T + 146, 5, played ? COL_FAINT : COL_ACCENT);
    draw_text_fit(when, tx + 22, T + 153, 20, played ? COL_DIM : COL_ACCENT, false, max_w - 22);
}

static void draw_empty(Launcher *l) {
    const float cx = UI_W / 2.0f;
    /* an APK card with a plus badge */
    const float s = 132, L = cx - s / 2, T = 176;
    grad_rrect(L, T, L + s, T + s, 34, COL_CARD_HI, COL_CARD);
    stroke_rrect(L + 0.5f, T + 0.5f, L + s - 0.5f, T + s - 0.5f, 34, 1, 0x20FFFFFF);
    draw_text_center("APK", cx, T + s / 2 + 13, 36, COL_FAINT, true, 0);
    fill_circle(L + s - 6, T + s - 6, 24, COL_ACCENT);
    fill_rrect(L + s - 18, T + s - 8, L + s + 6, T + s - 4, 2, COL_ON_ACCENT);
    fill_rrect(L + s - 8, T + s - 18, L + s - 4, T + s + 6, 2, COL_ON_ACCENT);

    draw_text_center("No apps yet", cx, 384, 40, COL_TEXT, true, 0);
    draw_text_center("Copy .apk files into this folder on your SD card:", cx, 428, 23, COL_DIM, false, 0);
    char path[600];
    snprintf(path, sizeof path, "%s/", l->dir);
    float pw = text_width(path, 24, true) + 56;
    if (pw > UI_W - 2 * MARGIN) pw = UI_W - 2 * MARGIN;
    fill_rrect(cx - pw / 2, 452, cx + pw / 2, 504, 26, COL_CARD_HI);
    draw_text_center(path, cx, 486, 24, COL_ACCENT, true, pw - 40);
    draw_text_center("then press X to refresh. Each APK's own icon and name show up here.", cx, 552, 21, COL_FAINT,
                     false, 0);
}

static void draw_loading(Launcher *l) {
    const float cx = UI_W / 2.0f;
    draw_logo(cx - 48, 196, 96);
    draw_text_center("Loading your apps", cx, 360, 32, COL_TEXT, true, 0);
    const float w = 420, L = cx - w / 2, T = 392;
    fill_rrect(L, T, L + w, T + 10, 5, 0x22FFFFFF);
    float frac = l->count ? (float)l->loaded / (float)l->count : 1.0f;
    if (frac > 0) {
        uint32_t cols[2] = {COL_BLUE, COL_ACCENT};
        GfxShader sh;
        memset(&sh, 0, sizeof sh);
        sh.type = GFX_SHADER_LINEAR;
        sh.x0 = L;
        sh.x1 = L + w;
        sh.colors = cols;
        sh.ncolors = 2;
        gfx_matrix_identity(&sh.local);
        GfxPaint p = fill_paint(0xFF000000);
        p.shader = &sh;
        gfx_draw_round_rect(&g_t, &g_id, &g_clip, L, T, L + 10 + (w - 10) * frac, T + 10, 5, 5, &p);
    }
    char msg[64];
    snprintf(msg, sizeof msg, "%d of %d", l->loaded, l->count);
    draw_text_center(msg, cx, T + 46, 20, COL_DIM, false, 0);
    if (l->loaded < l->count) draw_text_center(l->items[l->loaded].file, cx, T + 80, 18, COL_FAINT, false, w);
}

static void draw_toast(Launcher *l, int64_t now) {
    if (!l->toast[0] || now >= l->toast_until) return;
    float w = text_width(l->toast, 21, false) + 48;
    const float cx = UI_W / 2.0f, T = 32;
    fill_rrect(cx - w / 2, T + 4, cx + w / 2, T + 48, 22, 0x50000000);
    fill_rrect(cx - w / 2, T, cx + w / 2, T + 44, 22, 0xF01C445C);
    draw_text_center(l->toast, cx, T + 29, 21, COL_TEXT, false, 0);
}

static void draw_launcher(Launcher *l, uint32_t *screen, int64_t now) {
    ui_begin(screen);
    memcpy(screen, l->bg, (size_t)UI_W * UI_H * sizeof *screen);
    l->hits.n = 0;
    char sub[48] = "";
    if (!l->loading && l->count > 0) snprintf(sub, sizeof sub, "%d app%s", l->count, l->count == 1 ? "" : "s");
    draw_header(sub, &l->status);
    draw_bottom_bar();

    if (l->loading) {
        draw_loading(l);
        return;
    }
    if (l->count == 0) {
        draw_empty(l);
        static const Hint h[] = {{"X", "Refresh", UI_BTN_X}, {"+", "Exit", UI_BTN_PLUS}};
        draw_hints(&l->hits, h, (int)SA_ARRAY_LEN(h));
        draw_toast(l, now);
        return;
    }

    const char *heading = l->sort == SORT_NAME ? "All apps, A to Z" : "Recently played";
    if (l->sort == SORT_RECENT && played_time(l, l->items[0].file) == 0) heading = "All apps";
    draw_text(heading, MARGIN, 140, 22, COL_DIM, true);
    int first = (int)floorf(l->cam) - 1, last = (int)ceilf(l->cam) + VISIBLE_TILES + 1;
    if (first < 0) first = 0;
    if (last > l->count) last = l->count;
    /* the selected tile last, so its glow sits over its neighbours */
    for (int i = first; i < last; i++)
        if (i != l->sel) draw_tile(l, i);
    if (l->sel >= first && l->sel < last) draw_tile(l, l->sel);
    draw_details(l);

    char pos[32];
    snprintf(pos, sizeof pos, "%d / %d", l->sel + 1, l->count);
    draw_text(pos, MARGIN, BAR_TOP + 36, 21, COL_FAINT, false);
    const Hint h[] = {
        {"A", "Play", UI_BTN_A},
        {"Y", l->sort == SORT_NAME ? "Sort: A to Z" : "Sort: Recent", UI_BTN_Y},
        {"X", "Refresh", UI_BTN_X},
        {"+", "Exit", UI_BTN_PLUS},
    };
    draw_hints(&l->hits, h, (int)SA_ARRAY_LEN(h));
    draw_toast(l, now);
}

/* ---- launcher input ------------------------------------------------------------------ */

static void select_index(Launcher *l, int i) {
    if (l->count == 0) return;
    if (i < 0) i = 0;
    if (i >= l->count) i = l->count - 1;
    if (i != l->sel) l->dirty = true;
    l->sel = i;
    clamp_camera(l, false);
}

/* Applies one press set. Returns UI_RUN, UI_EXIT or UI_IDLE. */
static int handle_buttons(Launcher *l, uint32_t press, int64_t now) {
    if (press & UI_BTN_PLUS) return UI_EXIT;
    if (l->loading) return UI_IDLE;
    if (press & UI_BTN_X) {
        const char *keep = l->count ? l->items[l->sel].file : NULL;
        free(l->focus);
        l->focus = keep ? sa_strdup(keep) : NULL;
        scan(l);
        show_toast(l, "Refreshed the app list", now);
        return UI_IDLE;
    }
    if (l->count == 0) return UI_IDLE;
    if (press & UI_BTN_A) {
        Item *it = &l->items[l->sel];
        free(l->chosen);
        l->chosen = sa_strdup(it->path);
        record_launch(l, it);
        return UI_RUN;
    }
    if (press & UI_BTN_Y) {
        char *keep = sa_strdup(l->items[l->sel].file);
        l->sort = l->sort == SORT_NAME ? SORT_RECENT : SORT_NAME;
        sort_items(l, keep);
        save_state(l, keep);
        free(keep);
        show_toast(l, l->sort == SORT_NAME ? "Sorted by name" : "Recently played first", now);
        return UI_IDLE;
    }
    int step = 0;
    if (press & (UI_BTN_RIGHT | UI_BTN_DOWN)) step += 1;
    if (press & (UI_BTN_LEFT | UI_BTN_UP)) step -= 1;
    if (press & UI_BTN_R) step += VISIBLE_TILES;
    if (press & UI_BTN_L) step -= VISIBLE_TILES;
    if (press & UI_BTN_ZR) step = l->count;
    if (press & UI_BTN_ZL) step = -l->count;
    if (step) select_index(l, l->sel + step);
    return UI_IDLE;
}

static int handle_touch(Launcher *l, const UiInput *in, int64_t now) {
    int ev = touch_update(&l->touch, in, &l->hits);
    if (l->loading || ev == TOUCH_NONE) return UI_IDLE;
    const bool in_row = l->touch.y0 >= TILE_TOP - 20 && l->touch.y0 < TILE_TOP + TILE + 50;
    if (ev == TOUCH_PRESS) {
        l->drag_cam = l->cam;
        return UI_IDLE;
    }
    if (ev == TOUCH_MOVE) {
        if (l->touch.drag && in_row && l->count > 0) {
            float max_cam = l->count > VISIBLE_TILES ? (float)(l->count - VISIBLE_TILES) : 0;
            float c = l->drag_cam - (float)(l->touch.x - l->touch.x0) / TILE_STRIDE;
            if (c < -0.4f) c = -0.4f;
            if (c > max_cam + 0.4f) c = max_cam + 0.4f;
            l->cam = l->cam_target = c;
            l->dirty = true;
        }
        return UI_IDLE;
    }
    if (ev == TOUCH_RELEASE) {
        if (l->touch.drag && in_row && l->count > 0) {
            /* settle on whole tiles and keep the selection on screen */
            float max_cam = l->count > VISIBLE_TILES ? (float)(l->count - VISIBLE_TILES) : 0;
            float t = roundf(l->cam);
            if (t < 0) t = 0;
            if (t > max_cam) t = max_cam;
            l->cam_target = t;
            if (l->sel < (int)t) l->sel = (int)t;
            if (l->sel > (int)t + VISIBLE_TILES - 1) l->sel = (int)t + VISIBLE_TILES - 1;
        }
        l->dirty = true;
        return UI_IDLE;
    }
    /* tap */
    const Hit *h = &l->touch.hit;
    if (h->kind == HIT_TILE) {
        if ((int)h->value == l->sel) return handle_buttons(l, UI_BTN_A, now);
        select_index(l, (int)h->value);
        return UI_IDLE;
    }
    l->dirty = true;
    return handle_buttons(l, h->value, now);
}

int launcher_frame(Launcher *l, uint32_t *screen, const UiInput *in, const UiStatus *st) {
    const int64_t now = in->now_ns;
    float dt = l->last_ns ? (float)(now - l->last_ns) / 1e9f : 0.016f;
    if (dt > 0.05f) dt = 0.05f;
    if (dt < 0) dt = 0;
    l->last_ns = now;
    if (!status_eq(st, &l->status)) {
        l->status = *st;
        l->dirty = true;
    }

    uint32_t press = repeat_presses(&l->rep, in, UI_BTN_LEFT | UI_BTN_RIGHT | UI_BTN_UP | UI_BTN_DOWN | UI_BTN_L |
                                                     UI_BTN_R);
    int rc = handle_buttons(l, press, now);
    if (rc == UI_IDLE) rc = handle_touch(l, in, now);
    if (rc != UI_IDLE) return rc;

    if (l->loading) load_some(l);

    bool moving = false;
    if (!l->touch.down || !l->touch.drag) l->cam = approach(l->cam, l->cam_target, dt, 14, &moving);
    for (int i = 0; i < l->count; i++)
        l->items[i].hl = approach(l->items[i].hl, i == l->sel ? 1.0f : 0.0f, dt, 18, &moving);
    bool toast_now = l->toast[0] && now < l->toast_until;
    if (toast_now != l->toast_shown) l->dirty = true;
    l->toast_shown = toast_now;

    if (!l->dirty && !moving) return UI_IDLE;
    l->dirty = false;
    draw_launcher(l, screen, now);
    return UI_REDRAW;
}

/* ---- splash ----------------------------------------------------------------------------- */

void ui_draw_splash(uint32_t *screen, const char *apk_path) {
    ApkIdentity id;
    apk_read_identity(apk_path, ICON_DENSITY, &id);
    uint32_t *bg = render_background(0x3009B4E0, 0x2AE8343A);
    ui_begin(screen);
    memcpy(screen, bg, (size_t)UI_W * UI_H * sizeof *screen);
    free(bg);
    const float cx = UI_W / 2.0f, size = 200, T = 168;
    uint32_t *icon = id.icon ? fit_icon(id.icon, id.icon_w, id.icon_h, 152) : letter_icon(id.label, 152);
    radial_glow(cx, T + size / 2, 260, 0x2847C86E);
    fill_rrect(cx - size / 2, T + 10, cx + size / 2, T + size + 10, 44, 0x40000000);
    grad_rrect(cx - size / 2, T, cx + size / 2, T + size, 44, COL_CARD_HI, COL_CARD);
    stroke_rrect_hgrad(cx - size / 2 - 7, T - 7, cx + size / 2 + 7, T + size + 7, 51, 4, COL_BLUE, COL_RED);
    draw_bitmap(icon, 152, 152, cx - 76, T + size / 2 - 76, cx + 76, T + size / 2 + 76);
    free(icon);
    draw_text_center(id.label, cx, T + size + 76, 40, COL_TEXT, true, UI_W - 2 * MARGIN);
    draw_text_center("Starting\xE2\x80\xA6", cx, T + size + 118, 23, COL_DIM, false, 0);
    static const uint32_t dots[3] = {COL_BLUE, COL_ACCENT, COL_RED};
    for (int i = 0; i < 3; i++) fill_circle(cx - 24 + (float)i * 24, T + size + 156, 5, dots[i]);
    draw_text_center("B works as Android's Back button. Leave the app to come back to your list.", cx, UI_H - 40, 19,
                     COL_FAINT, false, 0);
    apk_identity_free(&id);
}

/* ---- NRO icon ----------------------------------------------------------------------------- */

void ui_draw_app_icon(uint32_t *px, int size, const uint32_t *src, int src_w, int src_h, float crop) {
    SavedTarget s = push_target(px, size, size);
    if (src && src_w > 0 && src_h > 0) {
        /* the central square, crop of the shorter side */
        float side = (float)(src_w < src_h ? src_w : src_h) * (crop > 0 && crop <= 1 ? crop : 1);
        float sl = ((float)src_w - side) / 2, st = ((float)src_h - side) / 2;
        fill_rect(0, 0, (float)size, (float)size, 0xFFFFFFFF);
        GfxTarget img = {(uint32_t *)src, src_w, src_h, src_w};
        GfxPaint p = fill_paint(0xFFFFFFFF);
        gfx_draw_bitmap(&g_t, &g_id, &g_clip, &img, sl, st, sl + side, st + side, 0, 0, (float)size, (float)size, &p);
        pop_target(s);
        return;
    }
    const float z = (float)size;
    grad_rrect(0, 0, z, z, 0, COL_BG_BOTTOM, COL_BG_TOP);
    radial_glow(z * 0.05f, z * 0.05f, z * 0.9f, 0x4809B4E0);
    radial_glow(z * 0.95f, z * 1.05f, z * 0.8f, 0x40E8343A);
    const float t = z * 0.52f, l = (z - t) / 2, top = z * 0.16f;
    fill_rrect(l, top + z * 0.03f, l + t, top + t + z * 0.03f, t * 0.28f, 0x50000000);
    draw_logo(l, top, t);
    const float fs = z * 0.115f, w1 = text_width("switch", fs, true), w2 = text_width("apk", fs, true);
    draw_text("switch", (z - w1 - w2) / 2, z * 0.86f, fs, COL_TEXT, true);
    draw_text("apk", (z - w1 - w2) / 2 + w1, z * 0.86f, fs, COL_ACCENT, true);
    pop_target(s);
}

/* ---- error screen ------------------------------------------------------------------------ */

#define LOG_TOP 188
#define LOG_BOTTOM 640
#define LOG_LINE 21
#define LOG_ROWS ((LOG_BOTTOM - LOG_TOP - 28) / LOG_LINE)

struct ErrorScreen {
    char *label;
    char *log_path;
    uint32_t *icon; /* 88 x 88 */
    uint32_t *bg;
    int rc;
    char **lines;
    int nlines;
    int scroll; /* first visible line */
    bool dirty;
    UiStatus status;
    Repeat rep;
    Touch touch;
    int drag_scroll;
    Hits hits;
};

ErrorScreen *error_screen_create(const char *apk_path, int rc, const char *const *lines, int nlines,
                                 const char *log_path) {
    ErrorScreen *e = sa_calloc(1, sizeof *e);
    ApkIdentity id;
    apk_read_identity(apk_path, ICON_DENSITY, &id);
    e->label = id.label;
    id.label = NULL;
    e->icon = id.icon ? fit_icon(id.icon, id.icon_w, id.icon_h, 88) : letter_icon(e->label, 88);
    apk_identity_free(&id);
    e->log_path = sa_strdup(log_path ? log_path : "");
    e->bg = render_background(0x38E8343A, 0x2409B4E0);
    e->rc = rc;
    e->lines = sa_calloc((size_t)(nlines > 0 ? nlines : 1), sizeof *e->lines);
    for (int i = 0; i < nlines; i++) e->lines[e->nlines++] = sa_strdup(lines[i] ? lines[i] : "");
    e->scroll = e->nlines > LOG_ROWS ? e->nlines - LOG_ROWS : 0;
    e->dirty = true;
    return e;
}

void error_screen_destroy(ErrorScreen *e) {
    if (!e) return;
    for (int i = 0; i < e->nlines; i++) free(e->lines[i]);
    free(e->lines);
    free(e->label);
    free(e->log_path);
    free(e->icon);
    free(e->bg);
    free(e);
}

static void draw_error(ErrorScreen *e, uint32_t *screen) {
    ui_begin(screen);
    memcpy(screen, e->bg, (size_t)UI_W * UI_H * sizeof *screen);
    e->hits.n = 0;
    draw_status(&e->status);

    const float L = MARGIN, T = 40, s = 112;
    grad_rrect(L, T, L + s, T + s, 30, COL_CARD_HI, COL_CARD);
    draw_bitmap(e->icon, 88, 88, L + 12, T + 12, L + 100, T + 100);
    fill_circle(L + s - 4, T + s - 4, 18, COL_DANGER);
    fill_rrect(L + s - 6, T + s - 15, L + s - 2, T + s + 1, 2, 0xFF2A0A0A);
    fill_circle(L + s - 4, T + s + 6, 2.6f, 0xFF2A0A0A);

    char title[300];
    snprintf(title, sizeof title, "%s stopped", e->label);
    draw_text_fit(title, L + s + 32, T + 52, 36, COL_TEXT, true, UI_W - MARGIN - (L + s + 32) - 300);
    char sub[600];
    snprintf(sub, sizeof sub, "Exit code %d. The full log is in %s", e->rc, e->log_path);
    draw_text_fit(sub, L + s + 32, T + 92, 21, COL_DIM, false, UI_W - 2 * MARGIN - s - 32);

    const float R = UI_W - MARGIN;
    fill_rrect(L, LOG_TOP, R, LOG_BOTTOM, 22, 0xD00A0D14);
    stroke_rrect(L + 0.5f, LOG_TOP + 0.5f, R - 0.5f, LOG_BOTTOM - 0.5f, 22, 1, 0x18FFFFFF);
    if (e->nlines == 0) draw_text("No log lines were captured.", L + 28, LOG_TOP + 40, 18, COL_FAINT, false);
    ui_set_clip((int)L + 20, LOG_TOP + 8, (int)R - 28, LOG_BOTTOM - 8);
    for (int row = 0; row < LOG_ROWS && e->scroll + row < e->nlines; row++) {
        const char *s2 = e->lines[e->scroll + row];
        uint32_t c = (s2[0] == 'E' || s2[0] == 'F') ? 0xFFFF8A8A : s2[0] == 'W' ? COL_WARN : 0xFFB8C2D4;
        draw_text(s2, L + 28, (float)(LOG_TOP + 34 + row * LOG_LINE), 16, c, false);
    }
    ui_reset_clip();
    if (e->nlines > LOG_ROWS) {
        const float track_t = LOG_TOP + 16, track_b = LOG_BOTTOM - 16;
        float h = (track_b - track_t) * (float)LOG_ROWS / (float)e->nlines;
        if (h < 24) h = 24;
        float y = track_t + (track_b - track_t - h) * (float)e->scroll / (float)(e->nlines - LOG_ROWS);
        fill_rrect(R - 16, y, R - 10, y + h, 3, 0x40FFFFFF);
    }

    draw_bottom_bar();
    if (e->nlines > LOG_ROWS) draw_text("Up/Down to scroll the log", MARGIN, BAR_TOP + 36, 20, COL_FAINT, false);
    static const Hint h[] = {
        {"A", "Back to apps", UI_BTN_A},
        {"X", "Try again", UI_BTN_X},
        {"+", "Exit", UI_BTN_PLUS},
    };
    draw_hints(&e->hits, h, (int)SA_ARRAY_LEN(h));
}

static void error_scroll(ErrorScreen *e, int to) {
    int max = e->nlines > LOG_ROWS ? e->nlines - LOG_ROWS : 0;
    if (to < 0) to = 0;
    if (to > max) to = max;
    if (to != e->scroll) e->dirty = true;
    e->scroll = to;
}

int error_screen_frame(ErrorScreen *e, uint32_t *screen, const UiInput *in, const UiStatus *st) {
    if (!status_eq(st, &e->status)) {
        e->status = *st;
        e->dirty = true;
    }
    uint32_t press = repeat_presses(&e->rep, in, UI_BTN_UP | UI_BTN_DOWN | UI_BTN_L | UI_BTN_R);
    int ev = touch_update(&e->touch, in, &e->hits);
    if (ev == TOUCH_PRESS) e->drag_scroll = e->scroll;
    if (ev == TOUCH_MOVE && e->touch.drag) error_scroll(e, e->drag_scroll - (e->touch.y - e->touch.y0) / LOG_LINE);
    if (ev == TOUCH_TAP && e->touch.hit.kind == HIT_BUTTON) press |= e->touch.hit.value;

    if (press & UI_BTN_A) return UI_BACK;
    if (press & UI_BTN_B) return UI_BACK;
    if (press & UI_BTN_X) return UI_RETRY;
    if (press & UI_BTN_PLUS) return UI_EXIT;
    if (press & UI_BTN_UP) error_scroll(e, e->scroll - 1);
    if (press & UI_BTN_DOWN) error_scroll(e, e->scroll + 1);
    if (press & UI_BTN_L) error_scroll(e, e->scroll - LOG_ROWS);
    if (press & UI_BTN_R) error_scroll(e, e->scroll + LOG_ROWS);

    if (!e->dirty) return UI_IDLE;
    e->dirty = false;
    draw_error(e, screen);
    return UI_REDRAW;
}
