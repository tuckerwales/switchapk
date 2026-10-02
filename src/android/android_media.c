/*
 * JNI for SoundPool, MediaPlayer, AudioTrack, ToneGenerator and
 * AudioManager. Decode and a blocking stream write drop the VM lock first.
 * The mixer callback never takes that lock.
 */
#include "android.h"
#include "audio_mixer.h"

#include <stdlib.h>
#include <string.h>

static void *dup_mem(const void *src, size_t n) {
    if (!src || n == 0) return NULL;
    void *p = malloc(n);
    if (!p) return NULL;
    memcpy(p, src, n);
    return p;
}

static void *dup_bytes(ArrayObject *a, int n) {
    if (!a || n <= 0) return NULL;
    if (n > a->length) n = a->length;
    return dup_mem(ARRAY_DATA(a, uint8_t), (size_t)n);
}

/* ENCODING_PCM_8BIT is 3, PCM_FLOAT is 4, PCM_32BIT is 22. Everything else is s16. */
static int pcm_fmt(int encoding) {
    if (encoding == 3) return MIX_PCM_U8;
    if (encoding == 4 || encoding == 22) return MIX_PCM_F32;
    return MIX_PCM_S16;
}

static int voice_from_new_clip(MixClip *c, int src, int stream) {
    if (!c) return 0;
    int id = mix_clip_voice(c, src, stream, 1.f, 1.f, 1.f, 0);
    mix_clip_unref(c);
    return id;
}

/* static native int nativeOpenBytes(byte[] data, int stream) */
NATIVE(MediaPlayer_nativeOpenBytes) {
    ArrayObject *a = A_ARR(0);
    int stream = A_INT(1);
    int n = a ? a->length : 0;
    void *copy = dup_bytes(a, n);
    if (!copy) {
        R_INT(0);
        return;
    }
    vm_gil_release(t);
    MixClip *c = mix_decode(copy, (size_t)n);
    free(copy);
    int id = voice_from_new_clip(c, MIX_SRC_MEDIA, stream);
    vm_gil_acquire(t);
    R_INT(id);
}

/* static native int nativeOpenPath(String path, int stream) */
NATIVE(MediaPlayer_nativeOpenPath) {
    int stream = A_INT(1);
    char *path = nat_str(A_OBJ(0));
    if (!path) {
        R_INT(0);
        return;
    }
    vm_gil_release(t);
    MixClip *c = mix_decode_file(path);
    free(path);
    int id = voice_from_new_clip(c, MIX_SRC_MEDIA, stream);
    vm_gil_acquire(t);
    R_INT(id);
}

NATIVE(MediaPlayer_nativePlay) { mix_voice_play(A_INT(0)); }
NATIVE(MediaPlayer_nativePause) { mix_voice_pause(A_INT(0)); }
NATIVE(MediaPlayer_nativeSeek) { mix_voice_seek_ms(A_INT(0), A_INT(1)); }
NATIVE(MediaPlayer_nativeGain) { mix_voice_gain(A_INT(0), A_FLOAT(1), A_FLOAT(2)); }
NATIVE(MediaPlayer_nativeLoop) { mix_voice_loop(A_INT(0), A_BOOL(1) ? -1 : 0); }
NATIVE(MediaPlayer_nativeRelease) { mix_voice_release(A_INT(0)); }
NATIVE(MediaPlayer_nativePosition) { R_INT(mix_voice_position_ms(A_INT(0))); }
NATIVE(MediaPlayer_nativeDuration) { R_INT(mix_voice_duration_ms(A_INT(0))); }
NATIVE(MediaPlayer_nativeEnded) { R_BOOL(mix_voice_ended(A_INT(0))); }

/* static native int nativeLoad(byte[] data) */
NATIVE(SoundPool_nativeLoad) {
    ArrayObject *a = A_ARR(0);
    int n = a ? a->length : 0;
    void *copy = dup_bytes(a, n);
    if (!copy) {
        R_INT(0);
        return;
    }
    vm_gil_release(t);
    MixClip *c = mix_decode(copy, (size_t)n);
    free(copy);
    int id = mix_clip_add(c);
    vm_gil_acquire(t);
    R_INT(id);
}

/* static native int nativeLoadPath(String path) */
NATIVE(SoundPool_nativeLoadPath) {
    char *path = nat_str(A_OBJ(0));
    if (!path) {
        R_INT(0);
        return;
    }
    vm_gil_release(t);
    MixClip *c = mix_decode_file(path);
    free(path);
    int id = mix_clip_add(c);
    vm_gil_acquire(t);
    R_INT(id);
}

NATIVE(SoundPool_nativeUnload) { mix_clip_drop(A_INT(0)); }

/* static native int nativePlay(int clip, float left, float right, float rate, int loop, int stream) */
NATIVE(SoundPool_nativePlay) {
    int id = mix_play_clip(A_INT(0), MIX_SRC_POOL, A_INT(5), A_FLOAT(1), A_FLOAT(2), A_FLOAT(3), A_INT(4));
    R_INT(id);
}

NATIVE(SoundPool_nativePause) { mix_voice_pause(A_INT(0)); }
NATIVE(SoundPool_nativeResume) { mix_voice_play(A_INT(0)); }
/* stop drops the voice. Java forgets the id, so pause would leak the slot. */
NATIVE(SoundPool_nativeStop) { mix_voice_release(A_INT(0)); }
NATIVE(SoundPool_nativeGain) { mix_voice_gain(A_INT(0), A_FLOAT(1), A_FLOAT(2)); }
NATIVE(SoundPool_nativeLoop) { mix_voice_loop(A_INT(0), A_INT(1)); }
NATIVE(SoundPool_nativeRate) { mix_voice_rate(A_INT(0), A_FLOAT(1)); }

/* static native int nativeStream(int rate, int channels, int stream) */
NATIVE(AudioTrack_nativeStream) {
    R_INT(mix_stream_open(MIX_SRC_TRACK, A_INT(2), A_INT(0), A_INT(1)));
}

/* static native int nativeClip(byte[] pcm, int bytes, int rate, int channels, int encoding, int stream) */
NATIVE(AudioTrack_nativeClip) {
    ArrayObject *a = A_ARR(0);
    int nbytes = A_INT(1);
    int rate = A_INT(2);
    int channels = A_INT(3);
    int encoding = A_INT(4);
    int stream = A_INT(5);
    void *copy = dup_bytes(a, nbytes);
    if (!copy) {
        R_INT(0);
        return;
    }
    if (a && nbytes > a->length) nbytes = a->length;
    vm_gil_release(t);
    MixClip *c = mix_pcm_clip(copy, (size_t)nbytes, rate, channels, pcm_fmt(encoding));
    free(copy);
    int id = voice_from_new_clip(c, MIX_SRC_TRACK, stream);
    vm_gil_acquire(t);
    R_INT(id);
}

/* static native int nativeWrite(int id, float[] interleaved, int frames, int channels, boolean block) */
NATIVE(AudioTrack_nativeWrite) {
    int id = A_INT(0);
    ArrayObject *a = A_ARR(1);
    int frames = A_INT(2);
    int channels = A_INT(3);
    bool block = A_BOOL(4);
    if (!a || frames <= 0 || (channels != 1 && channels != 2)) {
        R_INT(0);
        return;
    }
    if (frames > a->length / channels) frames = a->length / channels;
    size_t n = (size_t)frames * (size_t)channels;
    float *copy = dup_mem(ARRAY_DATA(a, float), n * sizeof(float));
    if (!copy) {
        R_INT(0);
        return;
    }
    vm_gil_release(t);
    int wrote = mix_stream_write(id, copy, frames, block);
    vm_gil_acquire(t);
    free(copy);
    R_INT(wrote);
}

NATIVE(AudioTrack_nativePlay) { mix_voice_play(A_INT(0)); }
NATIVE(AudioTrack_nativePause) { mix_voice_pause(A_INT(0)); }
NATIVE(AudioTrack_nativeStop) { mix_voice_stop(A_INT(0)); }
NATIVE(AudioTrack_nativeFlush) { mix_stream_flush(A_INT(0)); }
NATIVE(AudioTrack_nativeRelease) { mix_voice_release(A_INT(0)); }
NATIVE(AudioTrack_nativeGain) { mix_voice_gain(A_INT(0), A_FLOAT(1), A_FLOAT(2)); }
NATIVE(AudioTrack_nativeHead) { R_INT(mix_voice_head(A_INT(0))); }

/* static native int nativeTone(int stream, float gain, float freqA, float freqB, int durationMs) */
NATIVE(ToneGenerator_nativeTone) {
    int id = mix_tone_start(A_INT(0), A_FLOAT(1), A_FLOAT(2), A_FLOAT(3), A_INT(4));
    R_INT(id);
}

NATIVE(ToneGenerator_nativeRelease) { mix_voice_release(A_INT(0)); }

/* static native void nativeVolume(int stream, int index, int max, boolean mute) */
NATIVE(AudioManager_nativeVolume) {
    mix_stream_volume(A_INT(0), A_INT(1), A_INT(2), A_BOOL(3));
}

NATIVE(MixDebug_getSourceMask) {
    int mask = 0;
    mix_debug(&mask, NULL);
    R_INT(mask);
}

NATIVE(MixDebug_getNonZeroFrames) {
    int nz = 0;
    mix_debug(NULL, &nz);
    R_INT(nz);
}

static const NativeMethodReg g_regs[] = {
    {"Landroid/media/MediaPlayer;", "nativeOpenBytes", "([BI)I", MediaPlayer_nativeOpenBytes},
    {"Landroid/media/MediaPlayer;", "nativeOpenPath", "(Ljava/lang/String;I)I", MediaPlayer_nativeOpenPath},
    {"Landroid/media/MediaPlayer;", "nativePlay", "(I)V", MediaPlayer_nativePlay},
    {"Landroid/media/MediaPlayer;", "nativePause", "(I)V", MediaPlayer_nativePause},
    {"Landroid/media/MediaPlayer;", "nativeSeek", "(II)V", MediaPlayer_nativeSeek},
    {"Landroid/media/MediaPlayer;", "nativeGain", "(IFF)V", MediaPlayer_nativeGain},
    {"Landroid/media/MediaPlayer;", "nativeLoop", "(IZ)V", MediaPlayer_nativeLoop},
    {"Landroid/media/MediaPlayer;", "nativeRelease", "(I)V", MediaPlayer_nativeRelease},
    {"Landroid/media/MediaPlayer;", "nativePosition", "(I)I", MediaPlayer_nativePosition},
    {"Landroid/media/MediaPlayer;", "nativeDuration", "(I)I", MediaPlayer_nativeDuration},
    {"Landroid/media/MediaPlayer;", "nativeEnded", "(I)Z", MediaPlayer_nativeEnded},
    {"Landroid/media/SoundPool;", "nativeLoad", "([B)I", SoundPool_nativeLoad},
    {"Landroid/media/SoundPool;", "nativeLoadPath", "(Ljava/lang/String;)I", SoundPool_nativeLoadPath},
    {"Landroid/media/SoundPool;", "nativeUnload", "(I)V", SoundPool_nativeUnload},
    {"Landroid/media/SoundPool;", "nativePlay", "(IFFFII)I", SoundPool_nativePlay},
    {"Landroid/media/SoundPool;", "nativePause", "(I)V", SoundPool_nativePause},
    {"Landroid/media/SoundPool;", "nativeResume", "(I)V", SoundPool_nativeResume},
    {"Landroid/media/SoundPool;", "nativeStop", "(I)V", SoundPool_nativeStop},
    {"Landroid/media/SoundPool;", "nativeGain", "(IFF)V", SoundPool_nativeGain},
    {"Landroid/media/SoundPool;", "nativeLoop", "(II)V", SoundPool_nativeLoop},
    {"Landroid/media/SoundPool;", "nativeRate", "(IF)V", SoundPool_nativeRate},
    {"Landroid/media/AudioTrack;", "nativeStream", "(III)I", AudioTrack_nativeStream},
    {"Landroid/media/AudioTrack;", "nativeClip", "([BIIIII)I", AudioTrack_nativeClip},
    {"Landroid/media/AudioTrack;", "nativeWrite", "(I[FIIZ)I", AudioTrack_nativeWrite},
    {"Landroid/media/AudioTrack;", "nativePlay", "(I)V", AudioTrack_nativePlay},
    {"Landroid/media/AudioTrack;", "nativePause", "(I)V", AudioTrack_nativePause},
    {"Landroid/media/AudioTrack;", "nativeStop", "(I)V", AudioTrack_nativeStop},
    {"Landroid/media/AudioTrack;", "nativeFlush", "(I)V", AudioTrack_nativeFlush},
    {"Landroid/media/AudioTrack;", "nativeRelease", "(I)V", AudioTrack_nativeRelease},
    {"Landroid/media/AudioTrack;", "nativeGain", "(IFF)V", AudioTrack_nativeGain},
    {"Landroid/media/AudioTrack;", "nativeHead", "(I)I", AudioTrack_nativeHead},
    {"Landroid/media/ToneGenerator;", "nativeTone", "(IFFFI)I", ToneGenerator_nativeTone},
    {"Landroid/media/ToneGenerator;", "nativeRelease", "(I)V", ToneGenerator_nativeRelease},
    {"Landroid/media/AudioManager;", "nativeVolume", "(IIIZ)V", AudioManager_nativeVolume},
    {"Landroid/media/MixDebug;", "getSourceMask", "()I", MixDebug_getSourceMask},
    {"Landroid/media/MixDebug;", "getNonZeroFrames", "()I", MixDebug_getNonZeroFrames},
};

void android_media_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
