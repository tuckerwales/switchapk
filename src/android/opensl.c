/*
 * OpenSL ES engine, output mix and buffer-queue player. Players are a fixed
 * table so a callback racing Destroy still has a slot to look at. Destroy
 * bumps the generation; the callback returns when it does not match.
 * The app callback runs without the mixer lock.
 */
#include "../core/common.h"
#include "../nativeloader/sles.h"
#include "audio_mixer.h"

#include <math.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "opensl"
#define MAX_PLAYERS 16

enum { KIND_ENGINE = 1, KIND_MIX = 2, KIND_PLAYER = 3 };

typedef struct SlEngine {
    const struct SLObjectItf_ *obj_vt;
    int kind;
    const struct SLEngineItf_ *eng_vt;
    int realized;
} SlEngine;

typedef struct SlMix {
    const struct SLObjectItf_ *obj_vt;
    int kind;
    const struct SLVolumeItf_ *vol_vt;
    int realized;
    SLmillibel level;
    SLboolean mute;
    int stereo;
    SLpermille pan;
} SlMix;

typedef struct SlPlayer {
    const struct SLObjectItf_ *obj_vt;
    int kind;
    const struct SLPlayItf_ *play_vt;
    const struct SLAndroidSimpleBufferQueueItf_ *bq_vt;
    const struct SLVolumeItf_ *vol_vt;
    int used;
    int gen;
    int realized;
    int voice;
    SLuint32 play_state;
    SLmillibel level;
    SLboolean mute;
    int stereo;
    SLpermille pan;
    slAndroidSimpleBufferQueueCallback bq_cb;
    void *bq_ctx;
    SLuint32 index;
} SlPlayer;

static pthread_mutex_t g_sl = PTHREAD_MUTEX_INITIALIZER;
static SlPlayer g_players[MAX_PLAYERS];

static const struct SLObjectItf_ g_engine_obj_vt;
static const struct SLObjectItf_ g_mix_obj_vt;
static const struct SLObjectItf_ g_player_obj_vt;
static const struct SLEngineItf_ g_eng_vt;
static const struct SLPlayItf_ g_play_vt;
static const struct SLAndroidSimpleBufferQueueItf_ g_bq_vt;
static const struct SLVolumeItf_ g_player_vol_vt;
static const struct SLVolumeItf_ g_mix_vol_vt;

static const SLInterfaceID_ UUID_NULL = {0xec7178ec, 0xe5e1, 0x4432, 0xa3f4, {0x46, 0x57, 0xe6, 0x79, 0x52, 0x10}};
static const SLInterfaceID_ UUID_OBJECT = {0x79216360, 0xddd7, 0x11db, 0xac16, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
static const SLInterfaceID_ UUID_ENGINE = {0x8d97c260, 0xddd4, 0x11db, 0x958f, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
static const SLInterfaceID_ UUID_PLAY = {0xef0bd9c0, 0xddd7, 0x11db, 0xbf49, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
static const SLInterfaceID_ UUID_VOLUME = {0x09e8ede0, 0xddde, 0x11db, 0xb4f6, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
static const SLInterfaceID_ UUID_BUFFERQUEUE = {
    0x2bc99cc0, 0xddd4, 0x11db, 0x8d99, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
static const SLInterfaceID_ UUID_ANDROIDSIMPLEBUFFERQUEUE = {
    0x198e4940, 0xc5d7, 0x11df, 0xa2a6, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};
static const SLInterfaceID_ UUID_OUTPUTMIX = {0x97750f60, 0xddd7, 0x11db, 0x92b1, {0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}};

const SLInterfaceID SL_IID_NULL = &UUID_NULL;
const SLInterfaceID SL_IID_OBJECT = &UUID_OBJECT;
const SLInterfaceID SL_IID_ENGINE = &UUID_ENGINE;
const SLInterfaceID SL_IID_PLAY = &UUID_PLAY;
const SLInterfaceID SL_IID_VOLUME = &UUID_VOLUME;
const SLInterfaceID SL_IID_BUFFERQUEUE = &UUID_BUFFERQUEUE;
const SLInterfaceID SL_IID_ANDROIDSIMPLEBUFFERQUEUE = &UUID_ANDROIDSIMPLEBUFFERQUEUE;
const SLInterfaceID SL_IID_OUTPUTMIX = &UUID_OUTPUTMIX;

static int iid_eq(SLInterfaceID a, SLInterfaceID b) {
    if (!a || !b) return 0;
    if (a == b) return 1;
    return memcmp(a, b, sizeof(SLInterfaceID_)) == 0;
}

static int obj_kind(SLObjectItf self) { return *(int *)((char *)self + sizeof(void *)); }

static SlEngine *engine_from_obj(SLObjectItf self) { return (SlEngine *)self; }
static SlEngine *engine_from_itf(SLEngineItf self) { return (SlEngine *)((char *)self - offsetof(SlEngine, eng_vt)); }
static SlMix *mix_from_obj(SLObjectItf self) { return (SlMix *)self; }
static SlMix *mix_from_vol(SLVolumeItf self) { return (SlMix *)((char *)self - offsetof(SlMix, vol_vt)); }
static SlPlayer *player_from_obj(SLObjectItf self) { return (SlPlayer *)self; }
static SlPlayer *player_from_play(SLPlayItf self) { return (SlPlayer *)((char *)self - offsetof(SlPlayer, play_vt)); }
static SlPlayer *player_from_bq(SLAndroidSimpleBufferQueueItf self) {
    return (SlPlayer *)((char *)self - offsetof(SlPlayer, bq_vt));
}
static SlPlayer *player_from_vol(SLVolumeItf self) { return (SlPlayer *)((char *)self - offsetof(SlPlayer, vol_vt)); }

static int bump_gen(int gen) {
    gen++;
    if (gen <= 0 || gen > 0x7fffff) gen = 1;
    return gen;
}

static void *pack_user(int index, int gen) {
    return (void *)(((uintptr_t)gen << 8) | (uintptr_t)(index + 1));
}

static void apply_player_gain(SlPlayer *p) {
    float amp = 0.f;
    if (!p->mute) {
        SLmillibel level = p->level > 0 ? 0 : p->level;
        if (level < -12000) level = -12000;
        amp = powf(10.f, (float)level / 2000.f);
    }
    float l = amp, r = amp;
    if (p->stereo) {
        float pan = p->pan / 1000.f;
        if (pan < -1.f) pan = -1.f;
        if (pan > 1.f) pan = 1.f;
        if (pan <= 0.f) r *= 1.f + pan;
        else l *= 1.f - pan;
    }
    if (p->voice) mix_voice_gain(p->voice, l, r);
}

static void opensl_on_done(void *user) {
    uintptr_t packed = (uintptr_t)user;
    int index = (int)(packed & 0xff) - 1;
    int gen = (int)(packed >> 8);
    if (index < 0 || index >= MAX_PLAYERS) return;
    pthread_mutex_lock(&g_sl);
    SlPlayer *p = &g_players[index];
    if (!p->used || p->gen != gen || !p->bq_cb) {
        pthread_mutex_unlock(&g_sl);
        return;
    }
    slAndroidSimpleBufferQueueCallback cb = p->bq_cb;
    void *ctx = p->bq_ctx;
    SLAndroidSimpleBufferQueueItf itf = (SLAndroidSimpleBufferQueueItf)&p->bq_vt;
    pthread_mutex_unlock(&g_sl);
    cb(itf, ctx);
}

static SlPlayer *alloc_player(void) {
    pthread_mutex_lock(&g_sl);
    for (int i = 0; i < MAX_PLAYERS; i++) {
        if (g_players[i].used) continue;
        int gen = bump_gen(g_players[i].gen);
        memset(&g_players[i], 0, sizeof g_players[i]);
        SlPlayer *p = &g_players[i];
        p->used = 1;
        p->gen = gen;
        p->kind = KIND_PLAYER;
        p->obj_vt = &g_player_obj_vt;
        p->play_vt = &g_play_vt;
        p->bq_vt = &g_bq_vt;
        p->vol_vt = &g_player_vol_vt;
        p->play_state = SL_PLAYSTATE_STOPPED;
        pthread_mutex_unlock(&g_sl);
        return p;
    }
    pthread_mutex_unlock(&g_sl);
    return NULL;
}

static void player_destroy(SlPlayer *p) {
    pthread_mutex_lock(&g_sl);
    if (!p->used) {
        pthread_mutex_unlock(&g_sl);
        return;
    }
    int voice = p->voice;
    p->voice = 0;
    p->bq_cb = NULL;
    p->used = 0;
    p->realized = 0;
    p->gen = bump_gen(p->gen);
    pthread_mutex_unlock(&g_sl);
    if (voice) mix_voice_release(voice);
}

static int required_ok(SLuint32 n, const SLInterfaceID *ids, const SLboolean *req, int (*ok)(SLInterfaceID)) {
    for (SLuint32 i = 0; i < n; i++) {
        if (req && req[i] && !ok(ids ? ids[i] : NULL)) return 0;
    }
    return 1;
}

static int engine_iid(SLInterfaceID id) { return iid_eq(id, SL_IID_OBJECT) || iid_eq(id, SL_IID_ENGINE); }

static int player_iid(SLInterfaceID id) {
    return iid_eq(id, SL_IID_OBJECT) || iid_eq(id, SL_IID_PLAY) || iid_eq(id, SL_IID_VOLUME) ||
           iid_eq(id, SL_IID_BUFFERQUEUE) || iid_eq(id, SL_IID_ANDROIDSIMPLEBUFFERQUEUE);
}

static int mix_iid(SLInterfaceID id) {
    return iid_eq(id, SL_IID_OBJECT) || iid_eq(id, SL_IID_OUTPUTMIX) || iid_eq(id, SL_IID_VOLUME);
}

/* ---- object ---- */

static SLresult eng_realize(SLObjectItf self, SLboolean async) {
    (void)async;
    engine_from_obj(self)->realized = 1;
    return SL_RESULT_SUCCESS;
}

static SLresult mix_realize(SLObjectItf self, SLboolean async) {
    (void)async;
    mix_from_obj(self)->realized = 1;
    return SL_RESULT_SUCCESS;
}

static SLresult player_realize(SLObjectItf self, SLboolean async) {
    (void)async;
    SlPlayer *p = player_from_obj(self);
    if (!p->used) return SL_RESULT_PRECONDITIONS_VIOLATED;
    p->realized = 1;
    return SL_RESULT_SUCCESS;
}

static SLresult obj_resume(SLObjectItf self, SLboolean async) {
    (void)self;
    (void)async;
    return SL_RESULT_SUCCESS;
}

static SLresult obj_get_state(SLObjectItf self, SLuint32 *pState) {
    if (!pState) return SL_RESULT_PARAMETER_INVALID;
    int realized = 0;
    int kind = obj_kind(self);
    if (kind == KIND_ENGINE) realized = engine_from_obj(self)->realized;
    else if (kind == KIND_MIX) realized = mix_from_obj(self)->realized;
    else realized = player_from_obj(self)->realized;
    *pState = realized ? SL_OBJECT_STATE_REALIZED : SL_OBJECT_STATE_UNREALIZED;
    return SL_RESULT_SUCCESS;
}

static SLresult obj_get_interface(SLObjectItf self, const SLInterfaceID iid, void *pInterface) {
    if (!iid || !pInterface) return SL_RESULT_PARAMETER_INVALID;
    int kind = obj_kind(self);
    if (kind == KIND_ENGINE) {
        SlEngine *e = engine_from_obj(self);
        if (!e->realized && !iid_eq(iid, SL_IID_OBJECT)) return SL_RESULT_PRECONDITIONS_VIOLATED;
        if (iid_eq(iid, SL_IID_OBJECT)) {
            *(SLObjectItf *)pInterface = (SLObjectItf)&e->obj_vt;
            return SL_RESULT_SUCCESS;
        }
        if (iid_eq(iid, SL_IID_ENGINE)) {
            *(SLEngineItf *)pInterface = (SLEngineItf)&e->eng_vt;
            return SL_RESULT_SUCCESS;
        }
        return SL_RESULT_FEATURE_UNSUPPORTED;
    }
    if (kind == KIND_MIX) {
        SlMix *m = mix_from_obj(self);
        if (!m->realized && !iid_eq(iid, SL_IID_OBJECT)) return SL_RESULT_PRECONDITIONS_VIOLATED;
        if (iid_eq(iid, SL_IID_OBJECT)) {
            *(SLObjectItf *)pInterface = (SLObjectItf)&m->obj_vt;
            return SL_RESULT_SUCCESS;
        }
        if (iid_eq(iid, SL_IID_VOLUME)) {
            *(SLVolumeItf *)pInterface = (SLVolumeItf)&m->vol_vt;
            return SL_RESULT_SUCCESS;
        }
        if (iid_eq(iid, SL_IID_OUTPUTMIX)) {
            *(SLObjectItf *)pInterface = (SLObjectItf)&m->obj_vt;
            return SL_RESULT_SUCCESS;
        }
        return SL_RESULT_FEATURE_UNSUPPORTED;
    }
    SlPlayer *p = player_from_obj(self);
    if (!p->used) return SL_RESULT_PRECONDITIONS_VIOLATED;
    if (!p->realized && !iid_eq(iid, SL_IID_OBJECT)) return SL_RESULT_PRECONDITIONS_VIOLATED;
    if (iid_eq(iid, SL_IID_OBJECT)) {
        *(SLObjectItf *)pInterface = (SLObjectItf)&p->obj_vt;
        return SL_RESULT_SUCCESS;
    }
    if (iid_eq(iid, SL_IID_PLAY)) {
        *(SLPlayItf *)pInterface = (SLPlayItf)&p->play_vt;
        return SL_RESULT_SUCCESS;
    }
    if (iid_eq(iid, SL_IID_BUFFERQUEUE) || iid_eq(iid, SL_IID_ANDROIDSIMPLEBUFFERQUEUE)) {
        *(SLAndroidSimpleBufferQueueItf *)pInterface = (SLAndroidSimpleBufferQueueItf)&p->bq_vt;
        return SL_RESULT_SUCCESS;
    }
    if (iid_eq(iid, SL_IID_VOLUME)) {
        *(SLVolumeItf *)pInterface = (SLVolumeItf)&p->vol_vt;
        return SL_RESULT_SUCCESS;
    }
    return SL_RESULT_FEATURE_UNSUPPORTED;
}

static SLresult obj_register_cb(SLObjectItf self, slObjectCallback callback, void *pContext) {
    (void)self;
    (void)callback;
    (void)pContext;
    return SL_RESULT_SUCCESS;
}

static void obj_abort(SLObjectItf self) { (void)self; }

static void eng_destroy(SLObjectItf self) { free(engine_from_obj(self)); }
static void mix_destroy(SLObjectItf self) { free(mix_from_obj(self)); }
static void player_obj_destroy(SLObjectItf self) { player_destroy(player_from_obj(self)); }

static SLresult obj_set_priority(SLObjectItf self, SLint32 priority, SLboolean preemptable) {
    (void)self;
    (void)priority;
    (void)preemptable;
    return SL_RESULT_SUCCESS;
}

static SLresult obj_get_priority(SLObjectItf self, SLint32 *pPriority, SLboolean *pPreemptable) {
    (void)self;
    if (pPriority) *pPriority = 0;
    if (pPreemptable) *pPreemptable = SL_BOOLEAN_FALSE;
    return SL_RESULT_SUCCESS;
}

static SLresult obj_set_loss(SLObjectItf self, SLint16 numInterfaces, SLInterfaceID *pInterfaceIDs, SLboolean lose) {
    (void)self;
    (void)numInterfaces;
    (void)pInterfaceIDs;
    (void)lose;
    return SL_RESULT_SUCCESS;
}

/* ---- engine ---- */

static SLresult eng_unsup_dev(SLEngineItf self, SLObjectItf *pDevice, SLuint32 deviceID, SLuint32 numInterfaces,
                              const SLInterfaceID *pInterfaceIds, const SLboolean *pInterfaceRequired) {
    (void)self;
    (void)pDevice;
    (void)deviceID;
    (void)numInterfaces;
    (void)pInterfaceIds;
    (void)pInterfaceRequired;
    return SL_RESULT_FEATURE_UNSUPPORTED;
}

static SLresult eng_create_player(SLEngineItf self, SLObjectItf *pPlayer, SLDataSource *src, SLDataSink *snk,
                                  SLuint32 numInterfaces, const SLInterfaceID *pInterfaceIds,
                                  const SLboolean *pInterfaceRequired) {
    (void)snk;
    SLDataSource *pAudioSrc = src;
    if (!pPlayer || !pAudioSrc || !pAudioSrc->pLocator || !pAudioSrc->pFormat) return SL_RESULT_PARAMETER_INVALID;
    SlEngine *e = engine_from_itf(self);
    if (!e->realized) return SL_RESULT_PRECONDITIONS_VIOLATED;
    if (!required_ok(numInterfaces, pInterfaceIds, pInterfaceRequired, player_iid)) {
        return SL_RESULT_FEATURE_UNSUPPORTED;
    }
    SLuint32 loc = *(SLuint32 *)pAudioSrc->pLocator;
    if (loc != SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE && loc != SL_DATALOCATOR_BUFFERQUEUE) {
        return SL_RESULT_PARAMETER_INVALID;
    }
    SLDataLocator_AndroidSimpleBufferQueue *bq = pAudioSrc->pLocator;
    SLDataFormat_PCM *fmt = pAudioSrc->pFormat;
    if (fmt->formatType != SL_DATAFORMAT_PCM && fmt->formatType != SL_DATAFORMAT_PCM_EX) {
        return SL_RESULT_PARAMETER_INVALID;
    }
    if ((fmt->numChannels != 1 && fmt->numChannels != 2) || fmt->samplesPerSec < 1000) {
        return SL_RESULT_PARAMETER_INVALID;
    }
    if (fmt->endianness != SL_BYTEORDER_LITTLEENDIAN) return SL_RESULT_PARAMETER_INVALID;
    int bits = 0;
    if (fmt->bitsPerSample == SL_PCMSAMPLEFORMAT_FIXED_16) bits = 16;
    else if (fmt->bitsPerSample == SL_PCMSAMPLEFORMAT_FIXED_8) bits = 8;
    else return SL_RESULT_PARAMETER_INVALID;
    int rate = (int)(fmt->samplesPerSec / 1000);
    int num_buffers = (int)bq->numBuffers;
    SlPlayer *p = alloc_player();
    if (!p) return SL_RESULT_MEMORY_FAILURE;
    int index = (int)(p - g_players);
    int voice = mix_queue_open(MIX_SRC_OPENSL, rate, (int)fmt->numChannels, bits, num_buffers, opensl_on_done,
                               pack_user(index, p->gen));
    if (!voice) {
        player_destroy(p);
        return SL_RESULT_MEMORY_FAILURE;
    }
    p->voice = voice;
    *pPlayer = (SLObjectItf)&p->obj_vt;
    return SL_RESULT_SUCCESS;
}

static SLresult eng_create_recorder(SLEngineItf self, SLObjectItf *pRecorder, SLDataSource *pAudioSrc,
                                    SLDataSink *pAudioSnk, SLuint32 numInterfaces, const SLInterfaceID *pInterfaceIds,
                                    const SLboolean *pInterfaceRequired) {
    (void)self;
    (void)pRecorder;
    (void)pAudioSrc;
    (void)pAudioSnk;
    (void)numInterfaces;
    (void)pInterfaceIds;
    (void)pInterfaceRequired;
    return SL_RESULT_FEATURE_UNSUPPORTED;
}

static SLresult eng_create_midi(SLEngineItf self, SLObjectItf *pPlayer, SLDataSource *pMIDISrc, SLDataSource *pBankSrc,
                                SLDataSink *pAudioOutput, SLDataSink *pVibra, SLDataSink *pLEDArray,
                                SLuint32 numInterfaces, const SLInterfaceID *pInterfaceIds,
                                const SLboolean *pInterfaceRequired) {
    (void)self;
    (void)pPlayer;
    (void)pMIDISrc;
    (void)pBankSrc;
    (void)pAudioOutput;
    (void)pVibra;
    (void)pLEDArray;
    (void)numInterfaces;
    (void)pInterfaceIds;
    (void)pInterfaceRequired;
    return SL_RESULT_FEATURE_UNSUPPORTED;
}

static SLresult eng_create_listener(SLEngineItf self, SLObjectItf *pListener, SLuint32 numInterfaces,
                                    const SLInterfaceID *pInterfaceIds, const SLboolean *pInterfaceRequired) {
    (void)self;
    (void)pListener;
    (void)numInterfaces;
    (void)pInterfaceIds;
    (void)pInterfaceRequired;
    return SL_RESULT_FEATURE_UNSUPPORTED;
}

static SLresult eng_create_3d(SLEngineItf self, SLObjectItf *pGroup, SLuint32 numInterfaces,
                              const SLInterfaceID *pInterfaceIds, const SLboolean *pInterfaceRequired) {
    (void)self;
    (void)pGroup;
    (void)numInterfaces;
    (void)pInterfaceIds;
    (void)pInterfaceRequired;
    return SL_RESULT_FEATURE_UNSUPPORTED;
}

static SLresult eng_create_mix(SLEngineItf self, SLObjectItf *pMix, SLuint32 numInterfaces,
                               const SLInterfaceID *pInterfaceIds, const SLboolean *pInterfaceRequired) {
    SlEngine *e = engine_from_itf(self);
    if (!e->realized) return SL_RESULT_PRECONDITIONS_VIOLATED;
    if (!pMix) return SL_RESULT_PARAMETER_INVALID;
    if (!required_ok(numInterfaces, pInterfaceIds, pInterfaceRequired, mix_iid)) return SL_RESULT_FEATURE_UNSUPPORTED;
    SlMix *m = calloc(1, sizeof *m);
    if (!m) return SL_RESULT_MEMORY_FAILURE;
    m->obj_vt = &g_mix_obj_vt;
    m->kind = KIND_MIX;
    m->vol_vt = &g_mix_vol_vt;
    *pMix = (SLObjectItf)&m->obj_vt;
    return SL_RESULT_SUCCESS;
}

static SLresult eng_create_meta(SLEngineItf self, SLObjectItf *pMetadataExtractor, SLDataSource *pDataSource,
                                SLuint32 numInterfaces, const SLInterfaceID *pInterfaceIds,
                                const SLboolean *pInterfaceRequired) {
    (void)self;
    (void)pMetadataExtractor;
    (void)pDataSource;
    (void)numInterfaces;
    (void)pInterfaceIds;
    (void)pInterfaceRequired;
    return SL_RESULT_FEATURE_UNSUPPORTED;
}

static SLresult eng_create_ext(SLEngineItf self, SLObjectItf *pObject, void *pParameters, SLuint32 objectID,
                               SLuint32 numInterfaces, const SLInterfaceID *pInterfaceIds,
                               const SLboolean *pInterfaceRequired) {
    (void)self;
    (void)pObject;
    (void)pParameters;
    (void)objectID;
    (void)numInterfaces;
    (void)pInterfaceIds;
    (void)pInterfaceRequired;
    return SL_RESULT_FEATURE_UNSUPPORTED;
}

static SLresult eng_query_num_itf(SLEngineItf self, SLuint32 objectID, SLuint32 *pNumSupportedInterfaces) {
    (void)self;
    (void)objectID;
    if (!pNumSupportedInterfaces) return SL_RESULT_PARAMETER_INVALID;
    *pNumSupportedInterfaces = 0;
    return SL_RESULT_SUCCESS;
}

static SLresult eng_query_itf(SLEngineItf self, SLuint32 objectID, SLuint32 index, SLInterfaceID *pInterfaceId) {
    (void)self;
    (void)objectID;
    (void)index;
    (void)pInterfaceId;
    return SL_RESULT_FEATURE_UNSUPPORTED;
}

static SLresult eng_query_num_ext(SLEngineItf self, SLuint32 *pNumExtensions) {
    (void)self;
    if (!pNumExtensions) return SL_RESULT_PARAMETER_INVALID;
    *pNumExtensions = 0;
    return SL_RESULT_SUCCESS;
}

static SLresult eng_query_ext(SLEngineItf self, SLuint32 index, SLchar *pExtensionName, SLint16 *pNameLength) {
    (void)self;
    (void)index;
    (void)pExtensionName;
    (void)pNameLength;
    return SL_RESULT_FEATURE_UNSUPPORTED;
}

static SLresult eng_ext_supported(SLEngineItf self, const SLchar *pExtensionName, SLboolean *pSupported) {
    (void)self;
    (void)pExtensionName;
    if (!pSupported) return SL_RESULT_PARAMETER_INVALID;
    *pSupported = SL_BOOLEAN_FALSE;
    return SL_RESULT_SUCCESS;
}

/* ---- play / buffer queue ---- */

static SLresult play_set_state(SLPlayItf self, SLuint32 state) {
    SlPlayer *p = player_from_play(self);
    if (!p->used || !p->realized || !p->voice) return SL_RESULT_PRECONDITIONS_VIOLATED;
    if (state == SL_PLAYSTATE_PLAYING) mix_voice_play(p->voice);
    else if (state == SL_PLAYSTATE_PAUSED || state == SL_PLAYSTATE_STOPPED) mix_voice_pause(p->voice);
    else return SL_RESULT_PARAMETER_INVALID;
    p->play_state = state;
    return SL_RESULT_SUCCESS;
}

static SLresult play_get_state(SLPlayItf self, SLuint32 *pState) {
    if (!pState) return SL_RESULT_PARAMETER_INVALID;
    *pState = player_from_play(self)->play_state;
    return SL_RESULT_SUCCESS;
}

static SLresult play_get_duration(SLPlayItf self, SLmillisecond *pMsec) {
    (void)self;
    if (!pMsec) return SL_RESULT_PARAMETER_INVALID;
    *pMsec = SL_TIME_UNKNOWN;
    return SL_RESULT_SUCCESS;
}

static SLresult play_get_position(SLPlayItf self, SLmillisecond *pMsec) {
    if (!pMsec) return SL_RESULT_PARAMETER_INVALID;
    SlPlayer *p = player_from_play(self);
    *pMsec = p->voice ? (SLmillisecond)mix_voice_position_ms(p->voice) : 0;
    return SL_RESULT_SUCCESS;
}

static SLresult play_reg_cb(SLPlayItf self, slPlayCallback callback, void *pContext) {
    (void)self;
    (void)callback;
    (void)pContext;
    return SL_RESULT_SUCCESS;
}

static SLresult play_set_mask(SLPlayItf self, SLuint32 eventFlags) {
    (void)self;
    (void)eventFlags;
    return SL_RESULT_SUCCESS;
}

static SLresult play_get_mask(SLPlayItf self, SLuint32 *pEventFlags) {
    (void)self;
    if (!pEventFlags) return SL_RESULT_PARAMETER_INVALID;
    *pEventFlags = 0;
    return SL_RESULT_SUCCESS;
}

static SLresult play_set_marker(SLPlayItf self, SLmillisecond mSec) {
    (void)self;
    (void)mSec;
    return SL_RESULT_FEATURE_UNSUPPORTED;
}

static SLresult play_clear_marker(SLPlayItf self) {
    (void)self;
    return SL_RESULT_SUCCESS;
}

static SLresult play_get_marker(SLPlayItf self, SLmillisecond *pMsec) {
    (void)self;
    if (pMsec) *pMsec = SL_TIME_UNKNOWN;
    return SL_RESULT_SUCCESS;
}

static SLresult play_set_period(SLPlayItf self, SLmillisecond mSec) {
    (void)self;
    (void)mSec;
    return SL_RESULT_SUCCESS;
}

static SLresult play_get_period(SLPlayItf self, SLmillisecond *pMsec) {
    (void)self;
    if (pMsec) *pMsec = 0;
    return SL_RESULT_SUCCESS;
}

static SLresult bq_enqueue(SLAndroidSimpleBufferQueueItf self, const void *pBuffer, SLuint32 size) {
    SlPlayer *p = player_from_bq(self);
    if (!pBuffer || size == 0) return SL_RESULT_PARAMETER_INVALID;
    pthread_mutex_lock(&g_sl);
    if (!p->used || !p->realized || !p->voice) {
        pthread_mutex_unlock(&g_sl);
        return SL_RESULT_PRECONDITIONS_VIOLATED;
    }
    int voice = p->voice;
    pthread_mutex_unlock(&g_sl);
    int rc = mix_queue_enqueue(voice, pBuffer, size);
    if (rc > 0) return SL_RESULT_BUFFER_INSUFFICIENT;
    if (rc < 0) return SL_RESULT_PARAMETER_INVALID;
    pthread_mutex_lock(&g_sl);
    if (p->used) p->index++;
    pthread_mutex_unlock(&g_sl);
    return SL_RESULT_SUCCESS;
}

static SLresult bq_clear(SLAndroidSimpleBufferQueueItf self) {
    SlPlayer *p = player_from_bq(self);
    if (p->voice) mix_queue_clear(p->voice);
    return SL_RESULT_SUCCESS;
}

static SLresult bq_get_state(SLAndroidSimpleBufferQueueItf self, SLAndroidSimpleBufferQueueState *pState) {
    if (!pState) return SL_RESULT_PARAMETER_INVALID;
    SlPlayer *p = player_from_bq(self);
    pState->count = p->voice ? (SLuint32)mix_queue_count(p->voice) : 0;
    pState->index = p->index;
    return SL_RESULT_SUCCESS;
}

static SLresult bq_reg_cb(SLAndroidSimpleBufferQueueItf self, slAndroidSimpleBufferQueueCallback callback,
                          void *pContext) {
    SlPlayer *p = player_from_bq(self);
    pthread_mutex_lock(&g_sl);
    if (!p->used) {
        pthread_mutex_unlock(&g_sl);
        return SL_RESULT_PRECONDITIONS_VIOLATED;
    }
    p->bq_cb = callback;
    p->bq_ctx = pContext;
    pthread_mutex_unlock(&g_sl);
    return SL_RESULT_SUCCESS;
}

/* ---- volume ---- */

static SLresult vol_set_level_player(SLVolumeItf self, SLmillibel level) {
    if (level > 0) return SL_RESULT_PARAMETER_INVALID;
    SlPlayer *p = player_from_vol(self);
    if (!p->used) return SL_RESULT_PRECONDITIONS_VIOLATED;
    p->level = level;
    apply_player_gain(p);
    return SL_RESULT_SUCCESS;
}

static SLresult vol_get_level_player(SLVolumeItf self, SLmillibel *pLevel) {
    if (!pLevel) return SL_RESULT_PARAMETER_INVALID;
    *pLevel = player_from_vol(self)->level;
    return SL_RESULT_SUCCESS;
}

static SLresult vol_get_max(SLVolumeItf self, SLmillibel *pMaxLevel) {
    (void)self;
    if (!pMaxLevel) return SL_RESULT_PARAMETER_INVALID;
    *pMaxLevel = 0;
    return SL_RESULT_SUCCESS;
}

static SLresult vol_set_mute_player(SLVolumeItf self, SLboolean mute) {
    SlPlayer *p = player_from_vol(self);
    if (!p->used) return SL_RESULT_PRECONDITIONS_VIOLATED;
    p->mute = mute ? SL_BOOLEAN_TRUE : SL_BOOLEAN_FALSE;
    apply_player_gain(p);
    return SL_RESULT_SUCCESS;
}

static SLresult vol_get_mute_player(SLVolumeItf self, SLboolean *pMute) {
    if (!pMute) return SL_RESULT_PARAMETER_INVALID;
    *pMute = player_from_vol(self)->mute;
    return SL_RESULT_SUCCESS;
}

static SLresult vol_enable_stereo_player(SLVolumeItf self, SLboolean enable) {
    SlPlayer *p = player_from_vol(self);
    p->stereo = enable ? 1 : 0;
    apply_player_gain(p);
    return SL_RESULT_SUCCESS;
}

static SLresult vol_stereo_enabled_player(SLVolumeItf self, SLboolean *pEnable) {
    if (!pEnable) return SL_RESULT_PARAMETER_INVALID;
    *pEnable = player_from_vol(self)->stereo ? SL_BOOLEAN_TRUE : SL_BOOLEAN_FALSE;
    return SL_RESULT_SUCCESS;
}

static SLresult vol_set_pan_player(SLVolumeItf self, SLpermille stereoPosition) {
    if (stereoPosition < -1000 || stereoPosition > 1000) return SL_RESULT_PARAMETER_INVALID;
    SlPlayer *p = player_from_vol(self);
    p->pan = stereoPosition;
    apply_player_gain(p);
    return SL_RESULT_SUCCESS;
}

static SLresult vol_get_pan_player(SLVolumeItf self, SLpermille *pStereoPosition) {
    if (!pStereoPosition) return SL_RESULT_PARAMETER_INVALID;
    *pStereoPosition = player_from_vol(self)->pan;
    return SL_RESULT_SUCCESS;
}

static SLresult vol_set_level_mix(SLVolumeItf self, SLmillibel level) {
    if (level > 0) return SL_RESULT_PARAMETER_INVALID;
    mix_from_vol(self)->level = level;
    return SL_RESULT_SUCCESS;
}

static SLresult vol_get_level_mix(SLVolumeItf self, SLmillibel *pLevel) {
    if (!pLevel) return SL_RESULT_PARAMETER_INVALID;
    *pLevel = mix_from_vol(self)->level;
    return SL_RESULT_SUCCESS;
}

static SLresult vol_set_mute_mix(SLVolumeItf self, SLboolean mute) {
    mix_from_vol(self)->mute = mute ? SL_BOOLEAN_TRUE : SL_BOOLEAN_FALSE;
    return SL_RESULT_SUCCESS;
}

static SLresult vol_get_mute_mix(SLVolumeItf self, SLboolean *pMute) {
    if (!pMute) return SL_RESULT_PARAMETER_INVALID;
    *pMute = mix_from_vol(self)->mute;
    return SL_RESULT_SUCCESS;
}

static SLresult vol_enable_stereo_mix(SLVolumeItf self, SLboolean enable) {
    mix_from_vol(self)->stereo = enable ? 1 : 0;
    return SL_RESULT_SUCCESS;
}

static SLresult vol_stereo_enabled_mix(SLVolumeItf self, SLboolean *pEnable) {
    if (!pEnable) return SL_RESULT_PARAMETER_INVALID;
    *pEnable = mix_from_vol(self)->stereo ? SL_BOOLEAN_TRUE : SL_BOOLEAN_FALSE;
    return SL_RESULT_SUCCESS;
}

static SLresult vol_set_pan_mix(SLVolumeItf self, SLpermille stereoPosition) {
    if (stereoPosition < -1000 || stereoPosition > 1000) return SL_RESULT_PARAMETER_INVALID;
    mix_from_vol(self)->pan = stereoPosition;
    return SL_RESULT_SUCCESS;
}

static SLresult vol_get_pan_mix(SLVolumeItf self, SLpermille *pStereoPosition) {
    if (!pStereoPosition) return SL_RESULT_PARAMETER_INVALID;
    *pStereoPosition = mix_from_vol(self)->pan;
    return SL_RESULT_SUCCESS;
}

static const struct SLObjectItf_ g_engine_obj_vt = {
    eng_realize, obj_resume, obj_get_state, obj_get_interface, obj_register_cb, obj_abort, eng_destroy,
    obj_set_priority, obj_get_priority, obj_set_loss,
};

static const struct SLObjectItf_ g_mix_obj_vt = {
    mix_realize, obj_resume, obj_get_state, obj_get_interface, obj_register_cb, obj_abort, mix_destroy,
    obj_set_priority, obj_get_priority, obj_set_loss,
};

static const struct SLObjectItf_ g_player_obj_vt = {
    player_realize, obj_resume, obj_get_state, obj_get_interface, obj_register_cb, obj_abort, player_obj_destroy,
    obj_set_priority, obj_get_priority, obj_set_loss,
};

static const struct SLEngineItf_ g_eng_vt = {
    eng_unsup_dev, eng_unsup_dev, eng_create_player, eng_create_recorder, eng_create_midi, eng_create_listener,
    eng_create_3d, eng_create_mix, eng_create_meta, eng_create_ext, eng_query_num_itf, eng_query_itf,
    eng_query_num_ext, eng_query_ext, eng_ext_supported,
};

static const struct SLPlayItf_ g_play_vt = {
    play_set_state, play_get_state, play_get_duration, play_get_position, play_reg_cb, play_set_mask, play_get_mask,
    play_set_marker, play_clear_marker, play_get_marker, play_set_period, play_get_period,
};

static const struct SLAndroidSimpleBufferQueueItf_ g_bq_vt = {bq_enqueue, bq_clear, bq_get_state, bq_reg_cb};

static const struct SLVolumeItf_ g_player_vol_vt = {
    vol_set_level_player, vol_get_level_player, vol_get_max, vol_set_mute_player, vol_get_mute_player,
    vol_enable_stereo_player, vol_stereo_enabled_player, vol_set_pan_player, vol_get_pan_player,
};

static const struct SLVolumeItf_ g_mix_vol_vt = {
    vol_set_level_mix, vol_get_level_mix, vol_get_max, vol_set_mute_mix, vol_get_mute_mix, vol_enable_stereo_mix,
    vol_stereo_enabled_mix, vol_set_pan_mix, vol_get_pan_mix,
};

SLresult slCreateEngine(SLObjectItf *pEngine, SLuint32 numOptions, const SLEngineOption *pEngineOptions,
                        SLuint32 numInterfaces, const SLInterfaceID *pInterfaceIds,
                        const SLboolean *pInterfaceRequired) {
    (void)numOptions;
    (void)pEngineOptions;
    if (!pEngine) return SL_RESULT_PARAMETER_INVALID;
    if (!required_ok(numInterfaces, pInterfaceIds, pInterfaceRequired, engine_iid)) {
        return SL_RESULT_FEATURE_UNSUPPORTED;
    }
    SlEngine *e = calloc(1, sizeof *e);
    if (!e) return SL_RESULT_MEMORY_FAILURE;
    e->obj_vt = &g_engine_obj_vt;
    e->kind = KIND_ENGINE;
    e->eng_vt = &g_eng_vt;
    *pEngine = (SLObjectItf)&e->obj_vt;
    return SL_RESULT_SUCCESS;
}
