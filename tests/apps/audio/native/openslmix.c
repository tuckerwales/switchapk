/*
 * OpenSL ES buffer-queue player for tests/apps/audio. The IIDs are loaded
 * inside the function, the way an NDK app loads SL_IID_ENGINE. The callback
 * only re-enqueues a static buffer and does not call into Java.
 */
#include <jni.h>
#include <stdint.h>

#include "sles.h"

enum { FRAMES = 9600 };

static int16_t g_pcm[FRAMES * 2];
static int g_filled;

static void fill_pcm(void) {
    if (g_filled) return;
    for (int i = 0; i < FRAMES; i++) {
        int16_t s = (i & 32) ? 12000 : -12000;
        g_pcm[i * 2] = s;
        g_pcm[i * 2 + 1] = s;
    }
    g_filled = 1;
}

static void queue_cb(SLAndroidSimpleBufferQueueItf bq, void *ctx) {
    (void)ctx;
    (*bq)->Enqueue(bq, g_pcm, (SLuint32)sizeof g_pcm);
}

static int fail(SLObjectItf engine) {
    if (engine) (*engine)->Destroy(engine);
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_example_audiomix_NativeMix_start(JNIEnv *env, jclass cls) {
    (void)env;
    (void)cls;
    fill_pcm();
    /* Load inside the function so the compiler emits a GOT load plus a dereference. */
    const SLInterfaceID engine_id = SL_IID_ENGINE;
    const SLInterfaceID queue_id = SL_IID_ANDROIDSIMPLEBUFFERQUEUE;

    SLObjectItf engine = NULL;
    if (slCreateEngine(&engine, 0, NULL, 0, NULL, NULL) != SL_RESULT_SUCCESS || !engine) return JNI_FALSE;
    if ((*engine)->Realize(engine, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS) return fail(engine);

    SLEngineItf eng = NULL;
    if ((*engine)->GetInterface(engine, engine_id, &eng) != SL_RESULT_SUCCESS || !eng) return fail(engine);

    SLObjectItf mix = NULL;
    if ((*eng)->CreateOutputMix(eng, &mix, 0, NULL, NULL) != SL_RESULT_SUCCESS || !mix) return fail(engine);
    if ((*mix)->Realize(mix, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS) return fail(engine);

    SLDataLocator_AndroidSimpleBufferQueue loc;
    loc.locatorType = SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE;
    loc.numBuffers = 2;
    SLDataFormat_PCM fmt;
    fmt.formatType = SL_DATAFORMAT_PCM;
    fmt.numChannels = 2;
    fmt.samplesPerSec = SL_SAMPLINGRATE_48;
    fmt.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_16;
    fmt.containerSize = 16;
    fmt.channelMask = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
    fmt.endianness = SL_BYTEORDER_LITTLEENDIAN;
    SLDataSource src;
    src.pLocator = &loc;
    src.pFormat = &fmt;
    SLDataLocator_OutputMix sink_loc;
    sink_loc.locatorType = SL_DATALOCATOR_OUTPUTMIX;
    sink_loc.outputMix = mix;
    SLDataSink sink;
    sink.pLocator = &sink_loc;
    sink.pFormat = NULL;

    const SLInterfaceID ids[] = {queue_id};
    const SLboolean req[] = {SL_BOOLEAN_TRUE};
    SLObjectItf player = NULL;
    if ((*eng)->CreateAudioPlayer(eng, &player, &src, &sink, 1, ids, req) != SL_RESULT_SUCCESS || !player) {
        return fail(engine);
    }
    if ((*player)->Realize(player, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS) return fail(engine);

    const SLInterfaceID play_id = SL_IID_PLAY;
    SLPlayItf play = NULL;
    SLAndroidSimpleBufferQueueItf bq = NULL;
    if ((*player)->GetInterface(player, play_id, &play) != SL_RESULT_SUCCESS) return fail(engine);
    if ((*player)->GetInterface(player, queue_id, &bq) != SL_RESULT_SUCCESS) return fail(engine);
    if ((*bq)->RegisterCallback(bq, queue_cb, NULL) != SL_RESULT_SUCCESS) return fail(engine);
    if ((*bq)->Enqueue(bq, g_pcm, (SLuint32)sizeof g_pcm) != SL_RESULT_SUCCESS) return fail(engine);
    if ((*play)->SetPlayState(play, SL_PLAYSTATE_PLAYING) != SL_RESULT_SUCCESS) return fail(engine);
    return JNI_TRUE;
}
