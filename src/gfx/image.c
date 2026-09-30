/*
 * Image decoding (stb_image) and a small PNG encoder (zlib) used for
 * Bitmap.compress() and headless screenshots.
 */
#include "gfx.h"

#include <zlib.h>

#define STB_IMAGE_IMPLEMENTATION
#define STBI_NO_STDIO
#define STBI_NO_HDR
#define STBI_NO_LINEAR
#define STBI_NO_PSD
#define STBI_NO_PIC
#define STBI_NO_PNM
#define STBI_NO_TGA
#include "stb/stb_image.h"

uint32_t *gfx_decode_image(const uint8_t *data, size_t len, int *w, int *h, bool *has_alpha) {
    int iw, ih, comp;
    if (len > 0x7fffffff) return NULL;
    uint8_t *rgba = stbi_load_from_memory(data, (int)len, &iw, &ih, &comp, 4);
    if (!rgba) return NULL;
    size_t n = (size_t)iw * (size_t)ih;
    uint32_t *out = malloc(n * 4);
    if (!out) {
        stbi_image_free(rgba);
        return NULL;
    }
    bool alpha = false;
    for (size_t i = 0; i < n; i++) {
        const uint8_t *s = rgba + i * 4;
        out[i] = gfx_argb(s[3], s[0], s[1], s[2]);
        if (s[3] != 255) alpha = true;
    }
    stbi_image_free(rgba);
    *w = iw;
    *h = ih;
    if (has_alpha) *has_alpha = alpha;
    return out;
}

bool gfx_image_info(const uint8_t *data, size_t len, int *w, int *h) {
    int comp;
    if (len > 0x7fffffff) return false;
    return stbi_info_from_memory(data, (int)len, w, h, &comp) != 0;
}

static void put32be(SaBuf *b, uint32_t v) {
    uint8_t x[4] = {(uint8_t)(v >> 24), (uint8_t)(v >> 16), (uint8_t)(v >> 8), (uint8_t)v};
    sa_buf_append(b, x, 4);
}

static void chunk(SaBuf *b, const char *type, const uint8_t *data, size_t len) {
    put32be(b, (uint32_t)len);
    size_t start = b->len;
    sa_buf_append(b, type, 4);
    if (len) sa_buf_append(b, data, len);
    uLong crc = crc32(0, b->data + start, (uInt)(len + 4));
    put32be(b, (uint32_t)crc);
}

uint8_t *gfx_encode_png(const uint32_t *px, int w, int h, int stride, size_t *out_len) {
    size_t row = (size_t)w * 4 + 1;
    size_t raw_len = row * (size_t)h;
    uint8_t *raw = malloc(raw_len);
    if (!raw) return NULL;
    for (int y = 0; y < h; y++) {
        uint8_t *d = raw + (size_t)y * row;
        const uint32_t *s = px + (size_t)y * (size_t)stride;
        *d++ = 0; /* filter: none */
        for (int x = 0; x < w; x++) {
            uint32_t c = s[x];
            *d++ = (uint8_t)(c >> 16);
            *d++ = (uint8_t)(c >> 8);
            *d++ = (uint8_t)c;
            *d++ = (uint8_t)(c >> 24);
        }
    }
    uLongf zlen = compressBound((uLong)raw_len);
    uint8_t *z = malloc(zlen);
    if (!z || compress2(z, &zlen, raw, (uLong)raw_len, 6) != Z_OK) {
        free(raw);
        free(z);
        return NULL;
    }
    free(raw);
    SaBuf b = {0};
    static const uint8_t sig[8] = {0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    sa_buf_append(&b, sig, 8);
    uint8_t ihdr[13];
    ihdr[0] = (uint8_t)(w >> 24);
    ihdr[1] = (uint8_t)(w >> 16);
    ihdr[2] = (uint8_t)(w >> 8);
    ihdr[3] = (uint8_t)w;
    ihdr[4] = (uint8_t)(h >> 24);
    ihdr[5] = (uint8_t)(h >> 16);
    ihdr[6] = (uint8_t)(h >> 8);
    ihdr[7] = (uint8_t)h;
    ihdr[8] = 8;  /* bit depth */
    ihdr[9] = 6;  /* RGBA */
    ihdr[10] = 0;
    ihdr[11] = 0;
    ihdr[12] = 0;
    chunk(&b, "IHDR", ihdr, 13);
    chunk(&b, "IDAT", z, zlen);
    chunk(&b, "IEND", NULL, 0);
    free(z);
    *out_len = b.len;
    return b.data;
}
