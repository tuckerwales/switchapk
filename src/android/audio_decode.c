/*
 * WAV (PCM 8/16 and float), Ogg Vorbis (stb_vorbis) and MP3 (minimp3)
 * into a MixClip of interleaved float frames at the source rate.
 */
#include "audio_mixer.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

extern char *platform_map_path(const char *android_path);

#define DECODE_MAX_BYTES (16u * 1024u * 1024u)
#define DECODE_MAX_FRAMES (48000 * 180)

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wall"
#pragma GCC diagnostic ignored "-Wextra"
#pragma GCC diagnostic ignored "-Wunused-function"
#pragma GCC diagnostic ignored "-Wunused-parameter"
#pragma GCC diagnostic ignored "-Wunused-variable"
#pragma GCC diagnostic ignored "-Wsign-compare"
#pragma GCC diagnostic ignored "-Wdouble-promotion"
#pragma GCC diagnostic ignored "-Wmaybe-uninitialized"
#pragma GCC diagnostic ignored "-Wunused-but-set-variable"
#pragma GCC diagnostic ignored "-Wmisleading-indentation"
#define STB_VORBIS_NO_STDIO
#define STB_VORBIS_NO_PUSHDATA_API
#define get_bits stb_vorbis_get_bits
#include "stb/stb_vorbis.c"
#undef get_bits
#define MINIMP3_IMPLEMENTATION
#define MINIMP3_NO_SIMD
#define MINIMP3_NO_STDIO
#include "minimp3/minimp3.h"
#include "minimp3/minimp3_ex.h"
#pragma GCC diagnostic pop

static uint16_t ru16(const uint8_t *p) { return (uint16_t)(p[0] | (p[1] << 8)); }

static uint32_t ru32(const uint8_t *p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

static MixClip *clip_new(int rate, int channels, int frames) {
    if (rate <= 0 || (channels != 1 && channels != 2) || frames <= 0 || frames > DECODE_MAX_FRAMES) return NULL;
    MixClip *c = calloc(1, sizeof *c);
    if (!c) return NULL;
    c->pcm = calloc((size_t)frames * (size_t)channels, sizeof(float));
    if (!c->pcm) {
        free(c);
        return NULL;
    }
    c->refs = 1;
    c->rate = rate;
    c->channels = channels;
    c->frames = frames;
    return c;
}

int mix_clip_duration_ms(const MixClip *c) {
    if (!c || c->rate <= 0) return 0;
    return (int)((int64_t)c->frames * 1000 / c->rate);
}

static MixClip *from_s16(const int16_t *src, int frames, int channels, int rate) {
    MixClip *c = clip_new(rate, channels, frames);
    if (!c) return NULL;
    int n = frames * channels;
    for (int i = 0; i < n; i++) c->pcm[i] = src[i] / 32768.f;
    return c;
}

static MixClip *decode_wav(const uint8_t *data, size_t len) {
    if (len < 12 || memcmp(data, "RIFF", 4) != 0 || memcmp(data + 8, "WAVE", 4) != 0) return NULL;
    const uint8_t *fmt = NULL;
    uint32_t fmt_len = 0;
    const uint8_t *pcm = NULL;
    uint32_t pcm_len = 0;
    size_t off = 12;
    while (off + 8 <= len) {
        uint32_t sz = ru32(data + off + 4);
        if (off + 8 + sz > len) break;
        if (memcmp(data + off, "fmt ", 4) == 0) {
            fmt = data + off + 8;
            fmt_len = sz;
        } else if (memcmp(data + off, "data", 4) == 0) {
            pcm = data + off + 8;
            pcm_len = sz;
        }
        off += 8 + sz + (sz & 1);
    }
    if (!fmt || fmt_len < 16 || !pcm || pcm_len == 0) return NULL;
    int format = ru16(fmt);
    int channels = ru16(fmt + 2);
    int rate = (int)ru32(fmt + 4);
    int bits = ru16(fmt + 14);
    if (channels != 1 && channels != 2) return NULL;
    int bpf = channels * (bits / 8);
    if (bpf <= 0) return NULL;
    int frames = (int)(pcm_len / (uint32_t)bpf);
    if (format == 1 && bits == 16) {
        return from_s16((const int16_t *)pcm, frames, channels, rate);
    }
    if (format == 1 && bits == 8) {
        MixClip *c = clip_new(rate, channels, frames);
        if (!c) return NULL;
        int n = frames * channels;
        for (int i = 0; i < n; i++) c->pcm[i] = ((int)pcm[i] - 128) / 128.f;
        return c;
    }
    if (format == 3 && bits == 32) {
        MixClip *c = clip_new(rate, channels, frames);
        if (!c) return NULL;
        memcpy(c->pcm, pcm, (size_t)frames * (size_t)channels * sizeof(float));
        return c;
    }
    return NULL;
}

static MixClip *decode_vorbis(const uint8_t *data, size_t len) {
    if (len < 4 || memcmp(data, "OggS", 4) != 0) return NULL;
    if (len > 0x7fffffff) return NULL;
    int channels = 0, rate = 0;
    short *output = NULL;
    int frames = stb_vorbis_decode_memory(data, (int)len, &channels, &rate, &output);
    if (frames <= 0 || !output) {
        free(output);
        return NULL;
    }
    int keep = channels >= 2 ? 2 : 1;
    if (channels < 1) {
        free(output);
        return NULL;
    }
    MixClip *c = clip_new(rate, keep, frames);
    if (!c) {
        free(output);
        return NULL;
    }
    for (int i = 0; i < frames; i++) {
        c->pcm[i * keep] = output[i * channels] / 32768.f;
        if (keep == 2) c->pcm[i * 2 + 1] = output[i * channels + 1] / 32768.f;
    }
    free(output);
    return c;
}

static MixClip *decode_mp3(const uint8_t *data, size_t len) {
    if (mp3dec_detect_buf(data, len) != 0) return NULL;
    mp3dec_t dec;
    mp3dec_file_info_t info;
    memset(&info, 0, sizeof info);
    int rc = mp3dec_load_buf(&dec, data, len, &info, NULL, NULL);
    if (rc != 0 || !info.buffer || info.samples == 0 || info.channels < 1 || info.hz <= 0) {
        free(info.buffer);
        return NULL;
    }
    int frames = (int)(info.samples / (size_t)info.channels);
    int keep = info.channels >= 2 ? 2 : 1;
    MixClip *c;
    if (info.channels <= 2) {
        c = from_s16(info.buffer, frames, info.channels, info.hz);
    } else {
        c = clip_new(info.hz, keep, frames);
        if (c) {
            for (int i = 0; i < frames; i++) {
                c->pcm[i * 2] = info.buffer[i * info.channels] / 32768.f;
                c->pcm[i * 2 + 1] = info.buffer[i * info.channels + 1] / 32768.f;
            }
        }
    }
    free(info.buffer);
    return c;
}

MixClip *mix_decode(const void *data, size_t len) {
    if (!data || len < 4 || len > DECODE_MAX_BYTES) return NULL;
    const uint8_t *b = data;
    MixClip *c = decode_wav(b, len);
    if (c) return c;
    c = decode_vorbis(b, len);
    if (c) return c;
    return decode_mp3(b, len);
}

MixClip *mix_pcm_clip(const void *data, size_t bytes, int rate, int channels, int pcm_fmt) {
    if (!data || (channels != 1 && channels != 2) || rate <= 0) return NULL;
    int bps = pcm_fmt == MIX_PCM_U8 ? 1 : pcm_fmt == MIX_PCM_S16 ? 2 : pcm_fmt == MIX_PCM_F32 ? 4 : 0;
    if (bps == 0) return NULL;
    int bpf = bps * channels;
    if (bytes < (size_t)bpf) return NULL;
    int frames = (int)(bytes / (size_t)bpf);
    const uint8_t *p = data;
    if (pcm_fmt == MIX_PCM_S16) return from_s16((const int16_t *)p, frames, channels, rate);
    MixClip *c = clip_new(rate, channels, frames);
    if (!c) return NULL;
    int n = frames * channels;
    if (pcm_fmt == MIX_PCM_U8) {
        for (int i = 0; i < n; i++) c->pcm[i] = ((int)p[i] - 128) / 128.f;
    } else {
        memcpy(c->pcm, p, (size_t)n * sizeof(float));
    }
    return c;
}

static uint8_t *slurp(const char *android_path, size_t *out_len) {
    char *host = platform_map_path(android_path);
    if (!host) return NULL;
    FILE *f = fopen(host, "rb");
    free(host);
    if (!f) return NULL;
    if (fseek(f, 0, SEEK_END) != 0) {
        fclose(f);
        return NULL;
    }
    long sz = ftell(f);
    if (sz <= 0 || (unsigned long)sz > DECODE_MAX_BYTES) {
        fclose(f);
        return NULL;
    }
    rewind(f);
    uint8_t *buf = malloc((size_t)sz);
    if (!buf) {
        fclose(f);
        return NULL;
    }
    size_t n = fread(buf, 1, (size_t)sz, f);
    fclose(f);
    if (n != (size_t)sz) {
        free(buf);
        return NULL;
    }
    *out_len = n;
    return buf;
}

MixClip *mix_decode_file(const char *android_path) {
    if (!android_path || !android_path[0]) return NULL;
    size_t len = 0;
    uint8_t *buf = slurp(android_path, &len);
    if (!buf) return NULL;
    MixClip *c = mix_decode(buf, len);
    free(buf);
    return c;
}
