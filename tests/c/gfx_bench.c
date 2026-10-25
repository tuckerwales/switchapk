/* Times the bitmap drawing and fill cases games hit, in ms per 1280x720 frame (build line in docs/DEV_SETUP.md).
 * With out-prefix, each case's result is written to <out-prefix>.<case>.png, for comparing two renderers. */
#include "gfx/gfx.h"
#include <math.h>
#include <stdio.h>
#include <time.h>

#define W 1280
#define H 720
#define SPRITES 200

static double now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double)ts.tv_sec * 1e3 + (double)ts.tv_nsec / 1e6;
}

static uint32_t hash32(uint32_t x) {
    x ^= x >> 16;
    x *= 0x7feb352du;
    x ^= x >> 15;
    x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}

enum { SCALE_FILTER, SCALE_NEAREST, BLIT, SPRITES_1X, SPRITES_2X_FILTER, SPRITES_ALPHA, SPRITES_ROTATED, FILL, NCASES };

static const char *const g_names[NCASES] = {
    "full screen scale, filtered", "full screen scale, nearest", "1:1 opaque blit", "64x64 sprites 1:1",
    "64x64 sprites 2x, filtered", "64x64 sprites, 50% alpha", "rotated sprites, filtered", "translucent fill",
};

static void write_png(const char *prefix, int c, const uint32_t *px) {
    char path[512];
    snprintf(path, sizeof path, "%s.%d.png", prefix, c);
    size_t n;
    uint8_t *png = gfx_encode_png(px, W, H, W, &n);
    FILE *f = fopen(path, "wb");
    if (f && png) fwrite(png, 1, n, f);
    if (f) fclose(f);
    free(png);
}

int main(int argc, char **argv) {
    uint32_t *px = calloc((size_t)W * H, 4);
    GfxTarget t = {px, W, H, W};
    GfxClip clip = {0, 0, W, H, NULL, 0};
    GfxMatrix id;
    gfx_matrix_identity(&id);
    /* a 480x320 opaque game frame and a round sprite with a translucent rim */
    int fw = 480, fh = 320;
    uint32_t *frame = malloc((size_t)fw * fh * 4), *sprite = malloc(64 * 64 * 4);
    for (int i = 0; i < fw * fh; i++) frame[i] = hash32((uint32_t)i) | 0xff000000u;
    for (int i = 0; i < 64 * 64; i++) {
        int x = i % 64 - 32, y = i / 64 - 32, d = x * x + y * y;
        sprite[i] = d < 900 ? (hash32((uint32_t)i) | 0xff000000u) : d < 1024 ? (hash32((uint32_t)i) & 0x7fffffffu) : 0;
    }
    GfxTarget src_frame = {frame, fw, fh, fw}, src_sprite = {sprite, 64, 64, 64};
    GfxPaint p = {0};
    p.xfer = GFX_XFER_SRC_OVER;
    double total = 0;
    for (int c = 0; c < NCASES; c++) {
        gfx_draw_color(&t, &clip, 0xff808080u, GFX_XFER_SRC);
        p.filter = c == SCALE_FILTER || c == SPRITES_2X_FILTER || c == SPRITES_ROTATED;
        p.color = c == SPRITES_ALPHA ? 0x80000000u : 0xff000000u;
        int reps = c == FILL ? 1 : 40; /* repeated fades would only leave black */
        double t0 = now_ms();
        for (int r = 0; r < reps; r++) {
            if (c == FILL) {
                gfx_draw_color(&t, &clip, 0x99000000u, GFX_XFER_SRC_OVER);
            } else if (c == SCALE_FILTER || c == SCALE_NEAREST) {
                gfx_draw_bitmap(&t, &id, &clip, &src_frame, 0, 0, (float)fw, (float)fh, 0, 0, W, H, &p);
            } else if (c == BLIT) {
                gfx_draw_bitmap(&t, &id, &clip, &src_frame, 0, 0, (float)fw, (float)fh, 100, 100, 100.0f + (float)fw,
                                100.0f + (float)fh, &p);
            } else {
                for (int k = 0; k < SPRITES; k++) {
                    float x = (float)(hash32((uint32_t)(k * 3 + r)) % 1200), y = (float)(hash32((uint32_t)(k * 7 + r)) % 660);
                    if (c == SPRITES_ROTATED) {
                        float a = (float)k * 0.1f;
                        GfxMatrix bm = {cosf(a), sinf(a), -sinf(a), cosf(a), x, y};
                        gfx_draw_bitmap_matrix(&t, &id, &clip, &src_sprite, &bm, &p);
                    } else {
                        float s = c == SPRITES_2X_FILTER ? 128.0f : 64.0f;
                        gfx_draw_bitmap(&t, &id, &clip, &src_sprite, 0, 0, 64, 64, x, y, x + s, y + s, &p);
                    }
                }
            }
        }
        double ms = (now_ms() - t0) / reps;
        total += ms;
        printf("%-30s %8.3f ms\n", g_names[c], ms);
        if (argc > 1) write_png(argv[1], c, px);
    }
    printf("%-30s %8.3f ms\n", "total", total);
    free(px);
    free(frame);
    free(sprite);
    return 0;
}
