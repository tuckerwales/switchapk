/*
 * Subset of the NDK OpenSLES.h / OpenSLES_Android.h layouts. The vtable
 * order matches the Khronos headers so (*obj)->Method(obj, ...) works.
 * IID bytes are the Khronos / AOSP UUIDs.
 */
#ifndef SWITCHAPK_SLES_H
#define SWITCHAPK_SLES_H

#include <stdint.h>

typedef uint8_t SLuint8;
typedef int16_t SLint16;
typedef uint16_t SLuint16;
typedef int32_t SLint32;
typedef uint32_t SLuint32;
typedef uint32_t SLboolean;
typedef uint32_t SLresult;
typedef uint32_t SLmillisecond;
typedef int16_t SLmillibel;
typedef int16_t SLpermille;
typedef uint8_t SLchar;

#define SL_BOOLEAN_FALSE ((SLboolean)0)
#define SL_BOOLEAN_TRUE ((SLboolean)1)

#define SL_RESULT_SUCCESS ((SLresult)0)
#define SL_RESULT_PRECONDITIONS_VIOLATED ((SLresult)1)
#define SL_RESULT_PARAMETER_INVALID ((SLresult)2)
#define SL_RESULT_MEMORY_FAILURE ((SLresult)3)
#define SL_RESULT_RESOURCE_ERROR ((SLresult)4)
#define SL_RESULT_FEATURE_UNSUPPORTED ((SLresult)12)
#define SL_RESULT_INTERNAL_ERROR ((SLresult)13)
#define SL_RESULT_BUFFER_INSUFFICIENT ((SLresult)7)

#define SL_OBJECT_STATE_UNREALIZED ((SLuint32)1)
#define SL_OBJECT_STATE_REALIZED ((SLuint32)2)
#define SL_OBJECT_STATE_SUSPENDED ((SLuint32)3)

#define SL_PLAYSTATE_STOPPED ((SLuint32)1)
#define SL_PLAYSTATE_PAUSED ((SLuint32)2)
#define SL_PLAYSTATE_PLAYING ((SLuint32)3)
#define SL_TIME_UNKNOWN ((SLuint32)0xFFFFFFFF)

#define SL_DATALOCATOR_URI ((SLuint32)1)
#define SL_DATALOCATOR_ADDRESS ((SLuint32)2)
#define SL_DATALOCATOR_IODEVICE ((SLuint32)3)
#define SL_DATALOCATOR_OUTPUTMIX ((SLuint32)4)
#define SL_DATALOCATOR_BUFFERQUEUE ((SLuint32)6)
#define SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE ((SLuint32)0x800007BD)

#define SL_DATAFORMAT_MIME ((SLuint32)1)
#define SL_DATAFORMAT_PCM ((SLuint32)2)
#define SL_DATAFORMAT_PCM_EX ((SLuint32)4)

#define SL_PCMSAMPLEFORMAT_FIXED_8 ((SLuint32)8)
#define SL_PCMSAMPLEFORMAT_FIXED_16 ((SLuint32)16)
#define SL_PCMSAMPLEFORMAT_FIXED_32 ((SLuint32)32)
#define SL_BYTEORDER_BIGENDIAN ((SLuint32)1)
#define SL_BYTEORDER_LITTLEENDIAN ((SLuint32)2)

#define SL_SPEAKER_FRONT_LEFT ((SLuint32)0x1)
#define SL_SPEAKER_FRONT_RIGHT ((SLuint32)0x2)
#define SL_SPEAKER_FRONT_CENTER ((SLuint32)0x4)

#define SL_SAMPLINGRATE_8 ((SLuint32)8000000)
#define SL_SAMPLINGRATE_16 ((SLuint32)16000000)
#define SL_SAMPLINGRATE_22_05 ((SLuint32)22050000)
#define SL_SAMPLINGRATE_44_1 ((SLuint32)44100000)
#define SL_SAMPLINGRATE_48 ((SLuint32)48000000)

typedef struct SLInterfaceID_ {
    SLuint32 time_low;
    SLuint16 time_mid;
    SLuint16 time_hi_and_version;
    SLuint16 clock_seq;
    SLuint8 node[6];
} SLInterfaceID_;

typedef const struct SLInterfaceID_ *SLInterfaceID;

typedef struct SLObjectItf_ *const *SLObjectItf;
typedef struct SLEngineItf_ *const *SLEngineItf;
typedef struct SLPlayItf_ *const *SLPlayItf;
typedef struct SLAndroidSimpleBufferQueueItf_ *const *SLAndroidSimpleBufferQueueItf;
typedef struct SLVolumeItf_ *const *SLVolumeItf;

typedef void (*slObjectCallback)(SLObjectItf caller, const void *pContext, SLuint32 event, SLresult result,
                                 SLuint32 param, void *pInterface);
typedef void (*slPlayCallback)(SLPlayItf caller, void *pContext, SLuint32 event);
typedef void (*slAndroidSimpleBufferQueueCallback)(SLAndroidSimpleBufferQueueItf caller, void *pContext);

typedef struct SLEngineOption_ {
    SLuint32 feature;
    SLuint32 data;
} SLEngineOption;

typedef struct SLDataLocator_AndroidSimpleBufferQueue_ {
    SLuint32 locatorType;
    SLuint32 numBuffers;
} SLDataLocator_AndroidSimpleBufferQueue;

typedef struct SLDataLocator_BufferQueue_ {
    SLuint32 locatorType;
    SLuint32 numBuffers;
} SLDataLocator_BufferQueue;

typedef struct SLDataLocator_OutputMix_ {
    SLuint32 locatorType;
    SLObjectItf outputMix;
} SLDataLocator_OutputMix;

typedef struct SLDataFormat_PCM_ {
    SLuint32 formatType;
    SLuint32 numChannels;
    SLuint32 samplesPerSec;
    SLuint32 bitsPerSample;
    SLuint32 containerSize;
    SLuint32 channelMask;
    SLuint32 endianness;
} SLDataFormat_PCM;

typedef struct SLDataSource_ {
    void *pLocator;
    void *pFormat;
} SLDataSource;

typedef struct SLDataSink_ {
    void *pLocator;
    void *pFormat;
} SLDataSink;

typedef struct SLAndroidSimpleBufferQueueState_ {
    SLuint32 count;
    SLuint32 index;
} SLAndroidSimpleBufferQueueState;

struct SLObjectItf_ {
    SLresult (*Realize)(SLObjectItf self, SLboolean async);
    SLresult (*Resume)(SLObjectItf self, SLboolean async);
    SLresult (*GetState)(SLObjectItf self, SLuint32 *pState);
    SLresult (*GetInterface)(SLObjectItf self, const SLInterfaceID iid, void *pInterface);
    SLresult (*RegisterCallback)(SLObjectItf self, slObjectCallback callback, void *pContext);
    void (*AbortAsyncOperation)(SLObjectItf self);
    void (*Destroy)(SLObjectItf self);
    SLresult (*SetPriority)(SLObjectItf self, SLint32 priority, SLboolean preemptable);
    SLresult (*GetPriority)(SLObjectItf self, SLint32 *pPriority, SLboolean *pPreemptable);
    SLresult (*SetLossOfControlInterfaces)(SLObjectItf self, SLint16 numInterfaces, SLInterfaceID *pInterfaceIDs,
                                           SLboolean loseInterface);
};

struct SLEngineItf_ {
    SLresult (*CreateLEDDevice)(SLEngineItf self, SLObjectItf *pDevice, SLuint32 deviceID, SLuint32 numInterfaces,
                                const SLInterfaceID *pInterfaceIds, const SLboolean *pInterfaceRequired);
    SLresult (*CreateVibraDevice)(SLEngineItf self, SLObjectItf *pDevice, SLuint32 deviceID, SLuint32 numInterfaces,
                                  const SLInterfaceID *pInterfaceIds, const SLboolean *pInterfaceRequired);
    SLresult (*CreateAudioPlayer)(SLEngineItf self, SLObjectItf *pPlayer, SLDataSource *pAudioSrc,
                                  SLDataSink *pAudioSnk, SLuint32 numInterfaces, const SLInterfaceID *pInterfaceIds,
                                  const SLboolean *pInterfaceRequired);
    SLresult (*CreateAudioRecorder)(SLEngineItf self, SLObjectItf *pRecorder, SLDataSource *pAudioSrc,
                                    SLDataSink *pAudioSnk, SLuint32 numInterfaces, const SLInterfaceID *pInterfaceIds,
                                    const SLboolean *pInterfaceRequired);
    SLresult (*CreateMidiPlayer)(SLEngineItf self, SLObjectItf *pPlayer, SLDataSource *pMIDISrc, SLDataSource *pBankSrc,
                                 SLDataSink *pAudioOutput, SLDataSink *pVibra, SLDataSink *pLEDArray,
                                 SLuint32 numInterfaces, const SLInterfaceID *pInterfaceIds,
                                 const SLboolean *pInterfaceRequired);
    SLresult (*CreateListener)(SLEngineItf self, SLObjectItf *pListener, SLuint32 numInterfaces,
                               const SLInterfaceID *pInterfaceIds, const SLboolean *pInterfaceRequired);
    SLresult (*Create3DGroup)(SLEngineItf self, SLObjectItf *pGroup, SLuint32 numInterfaces,
                              const SLInterfaceID *pInterfaceIds, const SLboolean *pInterfaceRequired);
    SLresult (*CreateOutputMix)(SLEngineItf self, SLObjectItf *pMix, SLuint32 numInterfaces,
                                const SLInterfaceID *pInterfaceIds, const SLboolean *pInterfaceRequired);
    SLresult (*CreateMetadataExtractor)(SLEngineItf self, SLObjectItf *pMetadataExtractor, SLDataSource *pDataSource,
                                        SLuint32 numInterfaces, const SLInterfaceID *pInterfaceIds,
                                        const SLboolean *pInterfaceRequired);
    SLresult (*CreateExtensionObject)(SLEngineItf self, SLObjectItf *pObject, void *pParameters, SLuint32 objectID,
                                      SLuint32 numInterfaces, const SLInterfaceID *pInterfaceIds,
                                      const SLboolean *pInterfaceRequired);
    SLresult (*QueryNumSupportedInterfaces)(SLEngineItf self, SLuint32 objectID, SLuint32 *pNumSupportedInterfaces);
    SLresult (*QuerySupportedInterfaces)(SLEngineItf self, SLuint32 objectID, SLuint32 index,
                                         SLInterfaceID *pInterfaceId);
    SLresult (*QueryNumSupportedExtensions)(SLEngineItf self, SLuint32 *pNumExtensions);
    SLresult (*QuerySupportedExtension)(SLEngineItf self, SLuint32 index, SLchar *pExtensionName, SLint16 *pNameLength);
    SLresult (*IsExtensionSupported)(SLEngineItf self, const SLchar *pExtensionName, SLboolean *pSupported);
};

struct SLPlayItf_ {
    SLresult (*SetPlayState)(SLPlayItf self, SLuint32 state);
    SLresult (*GetPlayState)(SLPlayItf self, SLuint32 *pState);
    SLresult (*GetDuration)(SLPlayItf self, SLmillisecond *pMsec);
    SLresult (*GetPosition)(SLPlayItf self, SLmillisecond *pMsec);
    SLresult (*RegisterCallback)(SLPlayItf self, slPlayCallback callback, void *pContext);
    SLresult (*SetCallbackEventsMask)(SLPlayItf self, SLuint32 eventFlags);
    SLresult (*GetCallbackEventsMask)(SLPlayItf self, SLuint32 *pEventFlags);
    SLresult (*SetMarkerPosition)(SLPlayItf self, SLmillisecond mSec);
    SLresult (*ClearMarkerPosition)(SLPlayItf self);
    SLresult (*GetMarkerPosition)(SLPlayItf self, SLmillisecond *pMsec);
    SLresult (*SetPositionUpdatePeriod)(SLPlayItf self, SLmillisecond mSec);
    SLresult (*GetPositionUpdatePeriod)(SLPlayItf self, SLmillisecond *pMsec);
};

struct SLAndroidSimpleBufferQueueItf_ {
    SLresult (*Enqueue)(SLAndroidSimpleBufferQueueItf self, const void *pBuffer, SLuint32 size);
    SLresult (*Clear)(SLAndroidSimpleBufferQueueItf self);
    SLresult (*GetState)(SLAndroidSimpleBufferQueueItf self, SLAndroidSimpleBufferQueueState *pState);
    SLresult (*RegisterCallback)(SLAndroidSimpleBufferQueueItf self, slAndroidSimpleBufferQueueCallback callback,
                                 void *pContext);
};

struct SLVolumeItf_ {
    SLresult (*SetVolumeLevel)(SLVolumeItf self, SLmillibel level);
    SLresult (*GetVolumeLevel)(SLVolumeItf self, SLmillibel *pLevel);
    SLresult (*GetMaxVolumeLevel)(SLVolumeItf self, SLmillibel *pMaxLevel);
    SLresult (*SetMute)(SLVolumeItf self, SLboolean mute);
    SLresult (*GetMute)(SLVolumeItf self, SLboolean *pMute);
    SLresult (*EnableStereoPosition)(SLVolumeItf self, SLboolean enable);
    SLresult (*IsEnabledStereoPosition)(SLVolumeItf self, SLboolean *pEnable);
    SLresult (*SetStereoPosition)(SLVolumeItf self, SLpermille stereoPosition);
    SLresult (*GetStereoPosition)(SLVolumeItf self, SLpermille *pStereoPosition);
};

SLresult slCreateEngine(SLObjectItf *pEngine, SLuint32 numOptions, const SLEngineOption *pEngineOptions,
                        SLuint32 numInterfaces, const SLInterfaceID *pInterfaceIds,
                        const SLboolean *pInterfaceRequired);

extern const SLInterfaceID SL_IID_NULL;
extern const SLInterfaceID SL_IID_OBJECT;
extern const SLInterfaceID SL_IID_ENGINE;
extern const SLInterfaceID SL_IID_PLAY;
extern const SLInterfaceID SL_IID_BUFFERQUEUE;
extern const SLInterfaceID SL_IID_VOLUME;
extern const SLInterfaceID SL_IID_OUTPUTMIX;
extern const SLInterfaceID SL_IID_ANDROIDSIMPLEBUFFERQUEUE;

#endif
