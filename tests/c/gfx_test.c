/* Renders a gallery of primitives to a PNG for visual inspection. */
#include "gfx/gfx.h"
#include <math.h>

int main(int argc, char **argv) {
    int W = 480, H = 360;
    uint32_t *px = calloc((size_t)W * H, 4);
    GfxTarget t = {px, W, H, W};
    GfxClip clip = {0, 0, W, H};
    GfxMatrix m;
    gfx_matrix_identity(&m);
    gfx_draw_color(&t, &clip, 0xFFFFFFFF, GFX_XFER_SRC);
    GfxPaint p = {0};
    p.color = 0xFF2196F3; p.aa = true; p.xfer = GFX_XFER_SRC_OVER; p.stroke_width = 1; p.miter = 4;
    gfx_draw_rect(&t, &m, &clip, 10, 10, 110, 60, &p);
    p.color = 0xFFE91E63;
    gfx_draw_round_rect(&t, &m, &clip, 120, 10, 230, 60, 12, 12, &p);
    p.color = 0x8000C853;
    gfx_draw_oval(&t, &m, &clip, 240, 10, 330, 90, &p);
    p.style = GFX_STROKE; p.stroke_width = 6; p.color = 0xFF673AB7; p.cap = GFX_CAP_ROUND; p.join = GFX_JOIN_ROUND;
    gfx_draw_arc(&t, &m, &clip, 340, 10, 440, 110, -90, 270, false, &p);
    p.stroke_width = 3; p.cap = GFX_CAP_BUTT;
    for (int i = 0; i < 8; i++) gfx_draw_line(&t, &m, &clip, 10 + i * 12, 80, 50 + i * 12, 150, &p);
    uint32_t cols[3] = {0xFFFF5722, 0xFFFFEB3B, 0xFF4CAF50};
    GfxShader sh = {0};
    sh.type = GFX_SHADER_LINEAR; sh.x0 = 150; sh.y0 = 0; sh.x1 = 330; sh.y1 = 0; sh.colors = cols; sh.ncolors = 3;
    gfx_matrix_identity(&sh.local);
    GfxPaint g = p; g.style = GFX_FILL; g.shader = &sh; g.color = 0xFF000000;
    gfx_draw_rect(&t, &m, &clip, 150, 100, 330, 150, &g);
    GfxPaint tp = {0};
    tp.color = 0xFF212121; tp.aa = true; tp.xfer = GFX_XFER_SRC_OVER;
    uint16_t txt[64]; const char *s = "Hello, Switch! Android on Horizon";
    int n = 0; while (s[n]) { txt[n] = (uint16_t)s[n]; n++; }
    gfx_draw_text(&t, &m, &clip, NULL, 22, txt, n, 10, 190, 0, false, &tp);
    gfx_draw_text(&t, &m, &clip, NULL, 14, txt, n, 10, 212, 0, true, &tp);
    GfxMatrix rot = {cosf(-0.3f), sinf(-0.3f), -sinf(-0.3f), cosf(-0.3f), 60, 320};
    tp.color = 0xFFD32F2F;
    gfx_draw_text(&t, &rot, &clip, NULL, 26, txt, 13, 0, 0, -0.25f, false, &tp);
    uint16_t cjk[] = {0x4F60, 0x597D, 0x4E16, 0x754C};
    gfx_draw_text(&t, &m, &clip, NULL, 28, cjk, 4, 300, 250, 0, false, &tp);
    size_t len; uint8_t *png = gfx_encode_png(px, W, H, W, &len);
    FILE *f = fopen(argc > 1 ? argv[1] : "gfx_test.png", "wb"); fwrite(png, 1, len, f); fclose(f);
    return 0;
}
