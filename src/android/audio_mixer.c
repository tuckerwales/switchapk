/*
 * 48 kHz stereo mixer. The platform callback takes only the mixer mutex.
 * It never takes the VM lock. Blocking writers drop the VM lock first.
 */
#include "audio_mixer.h"

#include "../platform/platform.h"

#include <math.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#define MAX_VOICES 48
#define MAX_CLIPS 64
#define MAX_Q 8
#define DONE_MAX 64
#define NZ_CAP 1000000000

enum { V_CLIP = 1, V_STREAM = 2, V_QUEUE = 3, V_TONE = 4 };

typedef struct {
    float *pcm;
    int frames;
    double pos;
} QBuf;

typedef struct {
    int used;
    int gen;
    int kind;
    int src;
    int stream;
    float gain_l, gain_r, rate;
    int playing;
    int ended;
    int loop;
    uint32_t head;
    MixClip *clip;
    double pos;
    double frac;
    float *ring;
    int ring_cap, ring_r, ring_w, ring_count;
    int channels;
    int src_rate;
    QBuf q[MAX_Q];
    int q_cap, q_head, q_count;
    uint32_t q_seq;
    int bits;
    MixQueueDone on_done;
    void *on_user;
    float freq_a, freq_b, phase_a, phase_b;
    int tone_left;
} Voice;

typedef struct {
    MixQueueDone fn;
    void *user;
} Done;

typedef struct {
    int inited;
    int index;
    int max;
    int mute;
} StreamVol;

static pthread_mutex_t g_mu = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_cv = PTHREAD_COND_INITIALIZER;
static Voice g_voices[MAX_VOICES];
static struct {
    int used;
    int gen;
    MixClip *clip;
} g_clips[MAX_CLIPS];
static StreamVol g_vol[16];
static int g_started;
static int g_mask;
static int g_nz;

static const float MIX_PI = 3.14159265f;

static void mix_cb(float *out, int frames, void *user);

static int make_id(int index, int gen) { return (gen << 8) | (index + 1); }

static int slot_of(int id, int nslots) {
    int slot = (id & 0xff) - 1;
    if (id <= 0 || slot < 0 || slot >= nslots) return -1;
    return slot;
}

static int bump_gen(int gen) {
    gen++;
    if (gen <= 0 || gen > 0x7fffff) gen = 1;
    return gen;
}

static float clamp_rate(float rate) {
    if (!(rate > 0.f)) return 1.f;
    if (rate < 0.25f) return 0.25f;
    if (rate > 4.f) return 4.f;
    return rate;
}

static float stream_gain(int stream) {
    if (stream < 0 || stream >= 16) stream = 3;
    StreamVol *s = &g_vol[stream];
    if (!s->inited || s->max <= 0) return 1.f;
    if (s->mute || s->index <= 0) return 0.f;
    return (float)s->index / (float)s->max;
}

static Voice *voice_of(int id) {
    int slot = slot_of(id, MAX_VOICES);
    if (slot < 0) return NULL;
    Voice *v = &g_voices[slot];
    if (!v->used || v->gen != (id >> 8)) return NULL;
    return v;
}

static void clip_ref_locked(MixClip *c) {
    if (c) c->refs++;
}

static void clip_unref_locked(MixClip *c) {
    if (!c) return;
    if (--c->refs > 0) return;
    free(c->pcm);
    free(c);
}

void mix_clip_ref(MixClip *c) {
    if (!c) return;
    pthread_mutex_lock(&g_mu);
    c->refs++;
    pthread_mutex_unlock(&g_mu);
}

void mix_clip_unref(MixClip *c) {
    if (!c) return;
    pthread_mutex_lock(&g_mu);
    int left = --c->refs;
    MixClip doomed = *c;
    pthread_mutex_unlock(&g_mu);
    if (left > 0) return;
    free(doomed.pcm);
    free(c);
}

static int alloc_voice(void) {
    for (int i = 0; i < MAX_VOICES; i++) {
        if (g_voices[i].used) continue;
        int gen = bump_gen(g_voices[i].gen);
        memset(&g_voices[i], 0, sizeof g_voices[i]);
        g_voices[i].used = 1;
        g_voices[i].gen = gen;
        g_voices[i].gain_l = 1.f;
        g_voices[i].gain_r = 1.f;
        g_voices[i].rate = 1.f;
        return i;
    }
    return -1;
}

static void ensure_audio(void) {
    if (g_started) return;
    if (platform_audio_start(MIX_RATE, mix_cb, NULL)) g_started = 1;
}

static void contribute(Voice *v, float sL, float sR, float gL, float gR, float *ol, float *or) {
    float l = sL * gL;
    float r = sR * gR;
    *ol += l;
    *or += r;
    if (l > 1e-4f || l < -1e-4f || r > 1e-4f || r < -1e-4f) g_mask |= v->src;
}

static void sample_at(const MixClip *c, int i, float *l, float *r) {
    if (i < 0) i = 0;
    if (i >= c->frames) i = c->frames - 1;
    if (c->channels == 1) {
        *l = *r = c->pcm[i];
    } else {
        *l = c->pcm[i * 2];
        *r = c->pcm[i * 2 + 1];
    }
}

static void render_clip(Voice *v, float *ol, float *or, float sg) {
    MixClip *c = v->clip;
    if (!c || c->frames <= 0 || !c->pcm) {
        v->playing = 0;
        v->ended = 1;
        return;
    }
    double step = ((double)c->rate / (double)MIX_RATE) * (double)v->rate;
    if (step < 0) step = 0;
    if (step > c->frames) step = c->frames;
    if (v->pos >= c->frames) {
        v->playing = 0;
        v->ended = 1;
        return;
    }
    int i0 = (int)v->pos;
    float frac = (float)(v->pos - i0);
    float aL, aR, bL, bR;
    sample_at(c, i0, &aL, &aR);
    sample_at(c, i0 + 1 < c->frames ? i0 + 1 : i0, &bL, &bR);
    contribute(v, aL + (bL - aL) * frac, aR + (bR - aR) * frac, v->gain_l * sg, v->gain_r * sg, ol, or);
    v->pos += step;
    while (v->pos >= c->frames) {
        if (v->loop < 0) {
            v->pos -= c->frames;
        } else if (v->loop > 0) {
            v->loop--;
            v->pos -= c->frames;
        } else {
            v->pos = c->frames;
            v->playing = 0;
            v->ended = 1;
            break;
        }
    }
    v->head = (uint32_t)v->pos;
}

static void ring_peek(const Voice *v, int ahead, float *l, float *r) {
    int idx = (v->ring_r + ahead) % v->ring_cap;
    const float *s = v->ring + idx * v->channels;
    *l = s[0];
    *r = v->channels == 1 ? s[0] : s[1];
}

static void ring_pop(Voice *v, int n) {
    if (n > v->ring_count) n = v->ring_count;
    v->ring_r = (v->ring_r + n) % v->ring_cap;
    v->ring_count -= n;
    v->head += (uint32_t)n;
}

static int render_stream(Voice *v, float *ol, float *or, float sg) {
    if (v->ring_count <= 0 || !v->ring) return 0;
    float aL, aR, bL, bR;
    ring_peek(v, 0, &aL, &aR);
    if (v->ring_count >= 2) ring_peek(v, 1, &bL, &bR);
    else {
        bL = aL;
        bR = aR;
    }
    float frac = (float)v->frac;
    contribute(v, aL + (bL - aL) * frac, aR + (bR - aR) * frac, v->gain_l * sg, v->gain_r * sg, ol, or);
    double step = ((double)v->src_rate / (double)MIX_RATE) * (double)v->rate;
    if (step < 0) step = 0;
    v->frac += step;
    int adv = (int)v->frac;
    if (adv > 0) {
        if (adv > v->ring_count) adv = v->ring_count;
        ring_pop(v, adv);
        v->frac -= adv;
        if (v->frac < 0) v->frac = 0;
    }
    return 1;
}

static void queue_finish(Voice *v, Done *done, int *nd) {
    QBuf *b = &v->q[v->q_head];
    free(b->pcm);
    b->pcm = NULL;
    b->frames = 0;
    b->pos = 0;
    if (v->q_cap > 0) v->q_head = (v->q_head + 1) % v->q_cap;
    if (v->q_count > 0) v->q_count--;
    if (v->on_done && done && nd && *nd < DONE_MAX) {
        done[*nd].fn = v->on_done;
        done[*nd].user = v->on_user;
        (*nd)++;
    }
}

static int render_queue(Voice *v, float *ol, float *or, float sg, Done *done, int *nd) {
    if (v->q_count <= 0 || v->q_cap <= 0) return 0;
    QBuf *b = &v->q[v->q_head];
    if (!b->pcm || b->frames <= 0 || b->pos >= b->frames) {
        queue_finish(v, done, nd);
        return 1;
    }
    int i0 = (int)b->pos;
    if (i0 < 0) i0 = 0;
    if (i0 >= b->frames) i0 = b->frames - 1;
    int i1 = i0 + 1 < b->frames ? i0 + 1 : i0;
    float frac = (float)(b->pos - i0);
    float aL, aR, bL, bR;
    if (v->channels == 1) {
        aL = aR = b->pcm[i0];
        bL = bR = b->pcm[i1];
    } else {
        aL = b->pcm[i0 * 2];
        aR = b->pcm[i0 * 2 + 1];
        bL = b->pcm[i1 * 2];
        bR = b->pcm[i1 * 2 + 1];
    }
    contribute(v, aL + (bL - aL) * frac, aR + (bR - aR) * frac, v->gain_l * sg, v->gain_r * sg, ol, or);
    double step = ((double)v->src_rate / (double)MIX_RATE) * (double)v->rate;
    if (step < 1e-6) step = 1e-6;
    if (step > b->frames) step = b->frames;
    b->pos += step;
    v->head = (uint32_t)b->pos;
    if (b->pos >= b->frames) queue_finish(v, done, nd);
    return 1;
}

static int render_tone(Voice *v, float *ol, float *or, float sg) {
    if (v->tone_left == 0) {
        v->playing = 0;
        v->ended = 1;
        return 0;
    }
    float s = sinf(v->phase_a);
    if (v->freq_b > 1.f) s = 0.5f * (s + sinf(v->phase_b));
    s *= 0.35f;
    v->phase_a += 2.f * MIX_PI * v->freq_a / (float)MIX_RATE;
    if (v->freq_b > 1.f) v->phase_b += 2.f * MIX_PI * v->freq_b / (float)MIX_RATE;
    if (v->phase_a > 64.f) v->phase_a = fmodf(v->phase_a, 2.f * MIX_PI);
    if (v->phase_b > 64.f) v->phase_b = fmodf(v->phase_b, 2.f * MIX_PI);
    contribute(v, s, s, v->gain_l * sg, v->gain_r * sg, ol, or);
    if (v->tone_left > 0 && --v->tone_left == 0) {
        v->playing = 0;
        v->ended = 1;
    }
    return 1;
}

static void fire_done(Done *done, int n) {
    for (int i = 0; i < n; i++) {
        if (done[i].fn) done[i].fn(done[i].user);
    }
}

static void mix_cb(float *out, int frames, void *user) {
    (void)user;
    Done done[DONE_MAX];
    int nd = 0;
    pthread_mutex_lock(&g_mu);
    memset(out, 0, (size_t)frames * 2 * sizeof(float));
    for (int vi = 0; vi < MAX_VOICES; vi++) {
        Voice *v = &g_voices[vi];
        if (!v->used || !v->playing) continue;
        int gen = v->gen;
        float sg = stream_gain(v->stream);
        for (int i = 0; i < frames; i++) {
            if (!v->used || v->gen != gen || !v->playing) break;
            float l = 0.f, r = 0.f;
            int produced = 1;
            if (v->kind == V_CLIP) render_clip(v, &l, &r, sg);
            else if (v->kind == V_STREAM) produced = render_stream(v, &l, &r, sg);
            else if (v->kind == V_QUEUE) produced = render_queue(v, &l, &r, sg, done, &nd);
            else if (v->kind == V_TONE) produced = render_tone(v, &l, &r, sg);
            out[i * 2] += l;
            out[i * 2 + 1] += r;
            if (!produced) break;
            if (nd == DONE_MAX) {
                pthread_mutex_unlock(&g_mu);
                fire_done(done, nd);
                nd = 0;
                pthread_mutex_lock(&g_mu);
            }
        }
    }
    for (int i = 0; i < frames; i++) {
        float l = out[i * 2];
        float r = out[i * 2 + 1];
        if (l > 1.f) l = 1.f;
        if (l < -1.f) l = -1.f;
        if (r > 1.f) r = 1.f;
        if (r < -1.f) r = -1.f;
        if (l > 0.001f || l < -0.001f || r > 0.001f || r < -0.001f) {
            if (g_nz < NZ_CAP) g_nz++;
        }
        out[i * 2] = l;
        out[i * 2 + 1] = r;
    }
    pthread_cond_broadcast(&g_cv);
    pthread_mutex_unlock(&g_mu);
    fire_done(done, nd);
}

static void clear_queue(Voice *v) {
    for (int i = 0; i < v->q_count; i++) {
        int idx = (v->q_head + i) % (v->q_cap > 0 ? v->q_cap : 1);
        free(v->q[idx].pcm);
        v->q[idx].pcm = NULL;
        v->q[idx].frames = 0;
        v->q[idx].pos = 0;
    }
    v->q_count = 0;
    v->q_head = 0;
}

static void release_locked(Voice *v) {
    clip_unref_locked(v->clip);
    v->clip = NULL;
    free(v->ring);
    v->ring = NULL;
    clear_queue(v);
    v->on_done = NULL;
    v->playing = 0;
    v->used = 0;
    pthread_cond_broadcast(&g_cv);
}

int mix_clip_add(MixClip *c) {
    if (!c) return 0;
    pthread_mutex_lock(&g_mu);
    int id = 0;
    for (int i = 0; i < MAX_CLIPS; i++) {
        if (g_clips[i].used) continue;
        int gen = bump_gen(g_clips[i].gen);
        g_clips[i].used = 1;
        g_clips[i].gen = gen;
        g_clips[i].clip = c;
        id = make_id(i, gen);
        break;
    }
    pthread_mutex_unlock(&g_mu);
    if (!id) mix_clip_unref(c);
    return id;
}

void mix_clip_drop(int clip_id) {
    int slot = slot_of(clip_id, MAX_CLIPS);
    if (slot < 0) return;
    pthread_mutex_lock(&g_mu);
    MixClip *c = NULL;
    if (g_clips[slot].used && g_clips[slot].gen == (clip_id >> 8)) {
        c = g_clips[slot].clip;
        g_clips[slot].clip = NULL;
        g_clips[slot].used = 0;
    }
    if (c) clip_unref_locked(c);
    pthread_mutex_unlock(&g_mu);
}

static int voice_from_clip(MixClip *clip, int src, int stream, float gain_l, float gain_r, float rate, int loop,
                           int play) {
    if (!clip) return 0;
    pthread_mutex_lock(&g_mu);
    int idx = alloc_voice();
    int id = 0;
    if (idx >= 0) {
        Voice *v = &g_voices[idx];
        v->kind = V_CLIP;
        v->src = src;
        v->stream = stream;
        v->gain_l = gain_l;
        v->gain_r = gain_r;
        v->rate = clamp_rate(rate);
        v->loop = loop;
        v->clip = clip;
        clip_ref_locked(clip);
        v->playing = play ? 1 : 0;
        if (play) ensure_audio();
        id = make_id(idx, v->gen);
    }
    pthread_mutex_unlock(&g_mu);
    return id;
}

int mix_clip_voice(MixClip *clip, int src, int stream, float gain_l, float gain_r, float rate, int loop) {
    return voice_from_clip(clip, src, stream, gain_l, gain_r, rate, loop, 0);
}

int mix_play_clip(int clip_id, int src, int stream, float gain_l, float gain_r, float rate, int loop) {
    int slot = slot_of(clip_id, MAX_CLIPS);
    if (slot < 0) return 0;
    pthread_mutex_lock(&g_mu);
    MixClip *c = NULL;
    if (g_clips[slot].used && g_clips[slot].gen == (clip_id >> 8)) c = g_clips[slot].clip;
    if (c) clip_ref_locked(c);
    pthread_mutex_unlock(&g_mu);
    if (!c) return 0;
    int id = voice_from_clip(c, src, stream, gain_l, gain_r, rate, loop, 1);
    mix_clip_unref(c);
    return id;
}

int mix_stream_open(int src, int stream, int rate, int channels) {
    if (rate <= 0 || (channels != 1 && channels != 2)) return 0;
    int cap = rate;
    if (cap < 2048) cap = 2048;
    if (cap > MIX_RATE) cap = MIX_RATE;
    float *ring = calloc((size_t)cap * (size_t)channels, sizeof(float));
    if (!ring) return 0;
    pthread_mutex_lock(&g_mu);
    int idx = alloc_voice();
    int id = 0;
    if (idx < 0) {
        free(ring);
    } else {
        Voice *v = &g_voices[idx];
        v->kind = V_STREAM;
        v->src = src;
        v->stream = stream;
        v->channels = channels;
        v->src_rate = rate;
        v->ring = ring;
        v->ring_cap = cap;
        id = make_id(idx, v->gen);
    }
    pthread_mutex_unlock(&g_mu);
    return id;
}

int mix_stream_write(int id, const float *interleaved, int frames, bool block) {
    if (!interleaved || frames <= 0) return 0;
    pthread_mutex_lock(&g_mu);
    int written = 0;
    while (written < frames) {
        Voice *v = voice_of(id);
        if (!v || v->kind != V_STREAM || !v->ring) break;
        int space = v->ring_cap - v->ring_count;
        if (space <= 0) {
            if (!block || !v->playing) break;
            pthread_cond_wait(&g_cv, &g_mu);
            continue;
        }
        int n = frames - written;
        if (n > space) n = space;
        for (int i = 0; i < n; i++) {
            float *dst = v->ring + v->ring_w * v->channels;
            memcpy(dst, interleaved + (written + i) * v->channels, (size_t)v->channels * sizeof(float));
            v->ring_w = (v->ring_w + 1) % v->ring_cap;
        }
        v->ring_count += n;
        written += n;
    }
    pthread_mutex_unlock(&g_mu);
    return written;
}

void mix_stream_flush(int id) {
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    if (v && v->kind == V_STREAM && v->ring) {
        v->ring_r = 0;
        v->ring_w = 0;
        v->ring_count = 0;
        v->frac = 0;
        pthread_cond_broadcast(&g_cv);
    }
    pthread_mutex_unlock(&g_mu);
}

int mix_queue_open(int src, int rate, int channels, int bits, int max_buffers, MixQueueDone on_done, void *user) {
    if (rate <= 0 || (channels != 1 && channels != 2) || (bits != 8 && bits != 16)) return 0;
    if (max_buffers < 1) max_buffers = 1;
    if (max_buffers > MAX_Q) max_buffers = MAX_Q;
    pthread_mutex_lock(&g_mu);
    int idx = alloc_voice();
    int id = 0;
    if (idx >= 0) {
        Voice *v = &g_voices[idx];
        v->kind = V_QUEUE;
        v->src = src;
        v->src_rate = rate;
        v->channels = channels;
        v->bits = bits;
        v->q_cap = max_buffers;
        v->on_done = on_done;
        v->on_user = user;
        id = make_id(idx, v->gen);
    }
    pthread_mutex_unlock(&g_mu);
    return id;
}

int mix_queue_enqueue(int id, const void *data, size_t bytes) {
    if (!data) return -1;
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    if (!v || v->kind != V_QUEUE) {
        pthread_mutex_unlock(&g_mu);
        return -1;
    }
    if (v->q_count >= v->q_cap) {
        pthread_mutex_unlock(&g_mu);
        return 1;
    }
    int bpf = v->channels * (v->bits / 8);
    if (bpf <= 0 || bytes < (size_t)bpf) {
        pthread_mutex_unlock(&g_mu);
        return -1;
    }
    int frames = (int)(bytes / (size_t)bpf);
    if (frames > MIX_RATE * 4) frames = MIX_RATE * 4;
    float *pcm = malloc((size_t)frames * (size_t)v->channels * sizeof(float));
    if (!pcm) {
        pthread_mutex_unlock(&g_mu);
        return -1;
    }
    const uint8_t *p = data;
    int n = frames * v->channels;
    for (int i = 0; i < n; i++) {
        if (v->bits == 16) {
            int16_t s = (int16_t)(p[0] | (p[1] << 8));
            pcm[i] = s / 32768.f;
            p += 2;
        } else {
            pcm[i] = ((int)p[i] - 128) / 128.f;
        }
    }
    int slot = (v->q_head + v->q_count) % v->q_cap;
    v->q[slot].pcm = pcm;
    v->q[slot].frames = frames;
    v->q[slot].pos = 0;
    v->q_count++;
    v->q_seq++;
    pthread_mutex_unlock(&g_mu);
    return 0;
}

void mix_queue_clear(int id) {
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    if (v && v->kind == V_QUEUE) clear_queue(v);
    pthread_mutex_unlock(&g_mu);
}

int mix_queue_count(int id) {
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    int n = v && v->kind == V_QUEUE ? v->q_count : 0;
    pthread_mutex_unlock(&g_mu);
    return n;
}

int mix_tone_start(int stream, float gain, float freq_a, float freq_b, int duration_ms) {
    int left = duration_ms < 0 ? -1 : (int)((int64_t)duration_ms * MIX_RATE / 1000);
    if (left == 0) return 0;
    pthread_mutex_lock(&g_mu);
    int idx = alloc_voice();
    int id = 0;
    if (idx >= 0) {
        Voice *v = &g_voices[idx];
        v->kind = V_TONE;
        v->src = MIX_SRC_TONE;
        v->stream = stream;
        v->gain_l = gain;
        v->gain_r = gain;
        v->freq_a = freq_a;
        v->freq_b = freq_b;
        v->tone_left = left;
        v->playing = 1;
        ensure_audio();
        id = make_id(idx, v->gen);
    }
    pthread_mutex_unlock(&g_mu);
    return id;
}

void mix_voice_play(int id) {
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    if (v) {
        v->playing = 1;
        v->ended = 0;
        if (v->kind == V_CLIP && v->clip && v->pos >= v->clip->frames) v->pos = 0;
        ensure_audio();
    }
    pthread_mutex_unlock(&g_mu);
}

void mix_voice_pause(int id) {
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    if (v) {
        v->playing = 0;
        pthread_cond_broadcast(&g_cv);
    }
    pthread_mutex_unlock(&g_mu);
}

void mix_voice_stop(int id) {
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    if (v) {
        v->playing = 0;
        v->ended = v->kind == V_TONE;
        v->pos = 0;
        v->frac = 0;
        v->head = 0;
        if (v->kind == V_STREAM && v->ring) {
            v->ring_r = 0;
            v->ring_w = 0;
            v->ring_count = 0;
        }
        if (v->kind == V_QUEUE) clear_queue(v);
        if (v->kind == V_TONE) v->tone_left = 0;
        pthread_cond_broadcast(&g_cv);
    }
    pthread_mutex_unlock(&g_mu);
}

void mix_voice_release(int id) {
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    if (v) release_locked(v);
    pthread_mutex_unlock(&g_mu);
}

void mix_voice_seek_ms(int id, int ms) {
    if (ms < 0) ms = 0;
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    if (v && v->kind == V_CLIP && v->clip && v->clip->rate > 0) {
        double pos = (double)ms * (double)v->clip->rate / 1000.0;
        if (pos >= v->clip->frames) {
            v->pos = v->clip->frames;
            v->ended = 1;
            v->playing = 0;
        } else {
            v->pos = pos;
            v->ended = 0;
        }
        v->head = (uint32_t)v->pos;
    }
    pthread_mutex_unlock(&g_mu);
}

void mix_voice_gain(int id, float l, float r) {
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    if (v) {
        v->gain_l = l;
        v->gain_r = r;
    }
    pthread_mutex_unlock(&g_mu);
}

void mix_voice_rate(int id, float rate) {
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    if (v) v->rate = clamp_rate(rate);
    pthread_mutex_unlock(&g_mu);
}

void mix_voice_loop(int id, int loop) {
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    if (v) v->loop = loop;
    pthread_mutex_unlock(&g_mu);
}

int mix_voice_position_ms(int id) {
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    int ms = 0;
    if (v && v->kind == V_CLIP && v->clip && v->clip->rate > 0) {
        ms = (int)(v->pos * 1000.0 / v->clip->rate);
    } else if (v && v->src_rate > 0 && (v->kind == V_STREAM || v->kind == V_QUEUE)) {
        ms = (int)((int64_t)v->head * 1000 / v->src_rate);
    }
    pthread_mutex_unlock(&g_mu);
    return ms;
}

int mix_voice_duration_ms(int id) {
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    int ms = 0;
    if (v && v->kind == V_CLIP) ms = mix_clip_duration_ms(v->clip);
    pthread_mutex_unlock(&g_mu);
    return ms;
}

int mix_voice_head(int id) {
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    int head = 0;
    if (v && v->kind == V_CLIP) head = (int)v->pos;
    else if (v) head = (int)v->head;
    pthread_mutex_unlock(&g_mu);
    return head;
}

bool mix_voice_ended(int id) {
    pthread_mutex_lock(&g_mu);
    Voice *v = voice_of(id);
    bool ended = v && v->ended;
    pthread_mutex_unlock(&g_mu);
    return ended;
}

void mix_stream_volume(int stream, int index, int max_index, bool mute) {
    if (stream < 0 || stream >= 16) return;
    pthread_mutex_lock(&g_mu);
    if (max_index <= 0) {
        g_vol[stream].inited = 0;
    } else {
        g_vol[stream].inited = 1;
        g_vol[stream].index = index;
        g_vol[stream].max = max_index;
        g_vol[stream].mute = mute ? 1 : 0;
    }
    pthread_mutex_unlock(&g_mu);
}

void mix_debug(int *mask, int *nonzero_frames) {
    pthread_mutex_lock(&g_mu);
    if (mask) *mask = g_mask;
    if (nonzero_frames) *nonzero_frames = g_nz;
    pthread_mutex_unlock(&g_mu);
}
