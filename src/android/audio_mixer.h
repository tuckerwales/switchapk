/*
 * One 48 kHz stereo float mixer for SoundPool, MediaPlayer, AudioTrack,
 * OpenSL ES buffer queues and ToneGenerator. The platform callback never
 * takes the VM lock. Java natives may hold that lock and then this mutex,
 * so the callback must not call back into Java.
 */
#ifndef SWITCHAPK_AUDIO_MIXER_H
#define SWITCHAPK_AUDIO_MIXER_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

#define MIX_RATE 48000

enum {
    MIX_SRC_MEDIA = 1,
    MIX_SRC_POOL = 2,
    MIX_SRC_TRACK = 4,
    MIX_SRC_OPENSL = 8,
    MIX_SRC_TONE = 16,
};

enum {
    MIX_PCM_U8 = 1,
    MIX_PCM_S16 = 2,
    MIX_PCM_F32 = 3,
};

typedef struct MixClip {
    int refs;
    int rate;
    int channels; /* 1 or 2 */
    int frames;
    float *pcm; /* interleaved, at `rate` */
} MixClip;

/* NULL if the bytes are not WAV, Ogg Vorbis or MP3. */
MixClip *mix_decode(const void *data, size_t len);
MixClip *mix_decode_file(const char *android_path);
/* Raw PCM, not a container. */
MixClip *mix_pcm_clip(const void *data, size_t bytes, int rate, int channels, int pcm_fmt);
void mix_clip_ref(MixClip *c);
void mix_clip_unref(MixClip *c);
int mix_clip_duration_ms(const MixClip *c);

/* Clip ids and voice ids are separate. 0 is failure. */
/* Takes ownership of one reference. */
int mix_clip_add(MixClip *c);
void mix_clip_drop(int clip_id);

/* loop: 0 play once, -1 forever, >0 extra repeats after the first play.
 * mix_clip_voice adds its own reference. mix_play_clip looks the id up. */
int mix_clip_voice(MixClip *clip, int src, int stream, float gain_l, float gain_r, float rate, int loop);
int mix_play_clip(int clip_id, int src, int stream, float gain_l, float gain_r, float rate, int loop);

/* Float ring, `channels` wide, consumed at `rate`. */
int mix_stream_open(int src, int stream, int rate, int channels);
/* Interleaved float frames. Blocks only while the voice is playing. */
int mix_stream_write(int id, const float *interleaved, int frames, bool block);
/* Drops queued stream frames. The playback head stays where it is. */
void mix_stream_flush(int id);

/*
 * OpenSL buffer queue. `on_done` runs on the audio thread after the mixer
 * lock is released, once per buffer that finished playing. `bits` is 8 or 16.
 */
typedef void (*MixQueueDone)(void *user);
int mix_queue_open(int src, int rate, int channels, int bits, int max_buffers, MixQueueDone on_done, void *user);
/* 0 queued, 1 full, -1 rejected. Copies the PCM. */
int mix_queue_enqueue(int id, const void *data, size_t bytes);
void mix_queue_clear(int id);
int mix_queue_count(int id);

/* duration_ms < 0 plays until mix_voice_release. freq_b 0 is a single sine. */
int mix_tone_start(int stream, float gain, float freq_a, float freq_b, int duration_ms);

void mix_voice_play(int id);
void mix_voice_pause(int id);
void mix_voice_stop(int id); /* pause, rewind, drop queued samples */
void mix_voice_release(int id);
void mix_voice_seek_ms(int id, int ms);
void mix_voice_gain(int id, float l, float r);
void mix_voice_rate(int id, float rate);
void mix_voice_loop(int id, int loop);
int mix_voice_position_ms(int id);
int mix_voice_duration_ms(int id);
int mix_voice_head(int id); /* source frames consumed */
bool mix_voice_ended(int id);

/* index/max are the AudioManager stream steps. max <= 0 leaves the gain at 1. */
void mix_stream_volume(int stream, int index, int max_index, bool mute);

void mix_debug(int *mask, int *nonzero_frames);

#endif
