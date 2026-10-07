/*
 * NDK layouts and constants for ANativeActivity, ANativeWindow, ALooper,
 * AInputQueue/AInputEvent and AConfiguration (android/native_activity.h,
 * native_window.h, looper.h, input.h, configuration.h). Field order matches
 * the NDK on 64-bit ABIs. Everything but ANativeActivity and the window
 * buffer struct is opaque to apps.
 */
#ifndef SWITCHAPK_NDK_ANDROID_H
#define SWITCHAPK_NDK_ANDROID_H

#include <stddef.h>
#include <stdint.h>

typedef struct ANativeActivity ANativeActivity;
typedef struct ANativeWindow ANativeWindow;
typedef struct AInputQueue AInputQueue;

typedef struct ARect {
    int32_t left;
    int32_t top;
    int32_t right;
    int32_t bottom;
} ARect;

typedef struct ANativeActivityCallbacks {
    void (*onStart)(ANativeActivity *activity);
    void (*onResume)(ANativeActivity *activity);
    void *(*onSaveInstanceState)(ANativeActivity *activity, size_t *out_size);
    void (*onPause)(ANativeActivity *activity);
    void (*onStop)(ANativeActivity *activity);
    void (*onDestroy)(ANativeActivity *activity);
    void (*onWindowFocusChanged)(ANativeActivity *activity, int focused);
    void (*onNativeWindowCreated)(ANativeActivity *activity, ANativeWindow *window);
    void (*onNativeWindowResized)(ANativeActivity *activity, ANativeWindow *window);
    void (*onNativeWindowRedrawNeeded)(ANativeActivity *activity, ANativeWindow *window);
    void (*onNativeWindowDestroyed)(ANativeActivity *activity, ANativeWindow *window);
    void (*onInputQueueCreated)(ANativeActivity *activity, AInputQueue *queue);
    void (*onInputQueueDestroyed)(ANativeActivity *activity, AInputQueue *queue);
    void (*onContentRectChanged)(ANativeActivity *activity, const ARect *rect);
    void (*onConfigurationChanged)(ANativeActivity *activity);
    void (*onLowMemory)(ANativeActivity *activity);
} ANativeActivityCallbacks;

struct ANativeActivity {
    ANativeActivityCallbacks *callbacks;
    void *vm;             /* JavaVM * */
    void *env;            /* JNIEnv * */
    void *clazz;          /* the NativeActivity instance, a JNI global ref */
    const char *internalDataPath;
    const char *externalDataPath;
    int32_t sdkVersion;
    void *instance;       /* the app's pointer */
    void *assetManager;   /* AAssetManager * */
    const char *obbPath;
};

typedef struct ANativeWindow_Buffer {
    void *bits;
    int32_t width;
    int32_t height;
    int32_t stride; /* pixels */
    int32_t format;
    uint32_t reserved[6];
} ANativeWindow_Buffer;

enum {
    WINDOW_FORMAT_RGBA_8888 = 1,
    WINDOW_FORMAT_RGBX_8888 = 2,
    WINDOW_FORMAT_RGB_565 = 4,
};

void ANativeWindow_acquire(ANativeWindow *window);
void ANativeWindow_release(ANativeWindow *window);
int32_t ANativeWindow_getWidth(ANativeWindow *window);
int32_t ANativeWindow_getHeight(ANativeWindow *window);
int32_t ANativeWindow_getFormat(ANativeWindow *window);
int32_t ANativeWindow_setBuffersGeometry(ANativeWindow *window, int32_t width, int32_t height, int32_t format);
int32_t ANativeWindow_lock(ANativeWindow *window, ANativeWindow_Buffer *out_buffer, ARect *in_out_dirty);
int32_t ANativeWindow_unlockAndPost(ANativeWindow *window);
ANativeWindow *ANativeWindow_fromSurface(void *env, void *surface);

void ANativeActivity_finish(ANativeActivity *activity);
void ANativeActivity_setWindowFormat(ANativeActivity *activity, int32_t format);
void ANativeActivity_setWindowFlags(ANativeActivity *activity, uint32_t add_flags, uint32_t remove_flags);
void ANativeActivity_showSoftInput(ANativeActivity *activity, uint32_t flags);
void ANativeActivity_hideSoftInput(ANativeActivity *activity, uint32_t flags);

/* ---- looper.h ---- */

typedef struct ALooper ALooper;

enum {
    ALOOPER_PREPARE_ALLOW_NON_CALLBACKS = 1 << 0,
};

enum {
    ALOOPER_POLL_WAKE = -1,
    ALOOPER_POLL_CALLBACK = -2,
    ALOOPER_POLL_TIMEOUT = -3,
    ALOOPER_POLL_ERROR = -4,
};

enum {
    ALOOPER_EVENT_INPUT = 1 << 0,
    ALOOPER_EVENT_OUTPUT = 1 << 1,
    ALOOPER_EVENT_ERROR = 1 << 2,
    ALOOPER_EVENT_HANGUP = 1 << 3,
    ALOOPER_EVENT_INVALID = 1 << 4,
};

typedef int (*ALooper_callbackFunc)(int fd, int events, void *data);

ALooper *ALooper_forThread(void);
ALooper *ALooper_prepare(int opts);
void ALooper_acquire(ALooper *looper);
void ALooper_release(ALooper *looper);
int ALooper_pollOnce(int timeoutMillis, int *outFd, int *outEvents, void **outData);
int ALooper_pollAll(int timeoutMillis, int *outFd, int *outEvents, void **outData);
void ALooper_wake(ALooper *looper);
int ALooper_addFd(ALooper *looper, int fd, int ident, int events, ALooper_callbackFunc callback, void *data);
int ALooper_removeFd(ALooper *looper, int fd);

/* ---- input.h ---- */

typedef struct AInputEvent AInputEvent;

enum {
    AINPUT_EVENT_TYPE_KEY = 1,
    AINPUT_EVENT_TYPE_MOTION = 2,
};

enum {
    AKEY_EVENT_ACTION_DOWN = 0,
    AKEY_EVENT_ACTION_UP = 1,
    AKEY_EVENT_ACTION_MULTIPLE = 2,
};

enum {
    AMOTION_EVENT_ACTION_MASK = 0xff,
    AMOTION_EVENT_ACTION_POINTER_INDEX_MASK = 0xff00,
    AMOTION_EVENT_ACTION_POINTER_INDEX_SHIFT = 8,
    AMOTION_EVENT_ACTION_DOWN = 0,
    AMOTION_EVENT_ACTION_UP = 1,
    AMOTION_EVENT_ACTION_MOVE = 2,
    AMOTION_EVENT_ACTION_CANCEL = 3,
    AMOTION_EVENT_ACTION_OUTSIDE = 4,
    AMOTION_EVENT_ACTION_POINTER_DOWN = 5,
    AMOTION_EVENT_ACTION_POINTER_UP = 6,
    AMOTION_EVENT_ACTION_HOVER_MOVE = 7,
    AMOTION_EVENT_ACTION_SCROLL = 8,
};

/* AMOTION_EVENT_AXIS_*: the values android.view.MotionEvent uses. */
enum {
    AMOTION_EVENT_AXIS_X = 0,
    AMOTION_EVENT_AXIS_Y = 1,
    AMOTION_EVENT_AXIS_PRESSURE = 2,
    AMOTION_EVENT_AXIS_SIZE = 3,
    AMOTION_EVENT_AXIS_TOUCH_MAJOR = 4,
    AMOTION_EVENT_AXIS_TOUCH_MINOR = 5,
    AMOTION_EVENT_AXIS_TOOL_MAJOR = 6,
    AMOTION_EVENT_AXIS_TOOL_MINOR = 7,
    AMOTION_EVENT_AXIS_ORIENTATION = 8,
    AMOTION_EVENT_AXIS_VSCROLL = 9,
    AMOTION_EVENT_AXIS_HSCROLL = 10,
    AMOTION_EVENT_AXIS_Z = 11,
    AMOTION_EVENT_AXIS_RX = 12,
    AMOTION_EVENT_AXIS_RY = 13,
    AMOTION_EVENT_AXIS_RZ = 14,
    AMOTION_EVENT_AXIS_HAT_X = 15,
    AMOTION_EVENT_AXIS_HAT_Y = 16,
    AMOTION_EVENT_AXIS_LTRIGGER = 17,
    AMOTION_EVENT_AXIS_RTRIGGER = 18,
    AMOTION_EVENT_AXIS_THROTTLE = 19,
    AMOTION_EVENT_AXIS_RUDDER = 20,
    AMOTION_EVENT_AXIS_WHEEL = 21,
    AMOTION_EVENT_AXIS_GAS = 22,
    AMOTION_EVENT_AXIS_BRAKE = 23,
};

int32_t AInputEvent_getType(const AInputEvent *event);
int32_t AInputEvent_getDeviceId(const AInputEvent *event);
int32_t AInputEvent_getSource(const AInputEvent *event);

int32_t AKeyEvent_getAction(const AInputEvent *key_event);
int32_t AKeyEvent_getFlags(const AInputEvent *key_event);
int32_t AKeyEvent_getKeyCode(const AInputEvent *key_event);
int32_t AKeyEvent_getScanCode(const AInputEvent *key_event);
int32_t AKeyEvent_getMetaState(const AInputEvent *key_event);
int32_t AKeyEvent_getRepeatCount(const AInputEvent *key_event);
int64_t AKeyEvent_getDownTime(const AInputEvent *key_event);
int64_t AKeyEvent_getEventTime(const AInputEvent *key_event);

int32_t AMotionEvent_getAction(const AInputEvent *motion_event);
int32_t AMotionEvent_getFlags(const AInputEvent *motion_event);
int32_t AMotionEvent_getMetaState(const AInputEvent *motion_event);
int32_t AMotionEvent_getButtonState(const AInputEvent *motion_event);
int32_t AMotionEvent_getEdgeFlags(const AInputEvent *motion_event);
int64_t AMotionEvent_getDownTime(const AInputEvent *motion_event);
int64_t AMotionEvent_getEventTime(const AInputEvent *motion_event);
float AMotionEvent_getXOffset(const AInputEvent *motion_event);
float AMotionEvent_getYOffset(const AInputEvent *motion_event);
float AMotionEvent_getXPrecision(const AInputEvent *motion_event);
float AMotionEvent_getYPrecision(const AInputEvent *motion_event);
size_t AMotionEvent_getPointerCount(const AInputEvent *motion_event);
int32_t AMotionEvent_getPointerId(const AInputEvent *motion_event, size_t pointer_index);
int32_t AMotionEvent_getToolType(const AInputEvent *motion_event, size_t pointer_index);
float AMotionEvent_getRawX(const AInputEvent *motion_event, size_t pointer_index);
float AMotionEvent_getRawY(const AInputEvent *motion_event, size_t pointer_index);
float AMotionEvent_getX(const AInputEvent *motion_event, size_t pointer_index);
float AMotionEvent_getY(const AInputEvent *motion_event, size_t pointer_index);
float AMotionEvent_getPressure(const AInputEvent *motion_event, size_t pointer_index);
float AMotionEvent_getSize(const AInputEvent *motion_event, size_t pointer_index);
float AMotionEvent_getTouchMajor(const AInputEvent *motion_event, size_t pointer_index);
float AMotionEvent_getTouchMinor(const AInputEvent *motion_event, size_t pointer_index);
float AMotionEvent_getToolMajor(const AInputEvent *motion_event, size_t pointer_index);
float AMotionEvent_getToolMinor(const AInputEvent *motion_event, size_t pointer_index);
float AMotionEvent_getOrientation(const AInputEvent *motion_event, size_t pointer_index);
float AMotionEvent_getAxisValue(const AInputEvent *motion_event, int32_t axis, size_t pointer_index);
size_t AMotionEvent_getHistorySize(const AInputEvent *motion_event);
int32_t AMotionEvent_findPointerIndex(const AInputEvent *motion_event, int32_t pointer_id);

typedef struct AInputQueue AInputQueue;

void AInputQueue_attachLooper(AInputQueue *queue, ALooper *looper, int ident, ALooper_callbackFunc callback,
                              void *data);
void AInputQueue_detachLooper(AInputQueue *queue);
int32_t AInputQueue_hasEvents(AInputQueue *queue);
int32_t AInputQueue_getEvent(AInputQueue *queue, AInputEvent **outEvent);
int32_t AInputQueue_preDispatchEvent(AInputQueue *queue, AInputEvent *event);
void AInputQueue_finishEvent(AInputQueue *queue, AInputEvent *event, int handled);

/* ---- configuration.h ---- */

typedef struct AConfiguration AConfiguration;

AConfiguration *AConfiguration_new(void);
void AConfiguration_delete(AConfiguration *config);
void AConfiguration_fromAssetManager(AConfiguration *out, void *am);
void AConfiguration_copy(AConfiguration *dest, AConfiguration *src);
int32_t AConfiguration_diff(AConfiguration *config1, AConfiguration *config2);
int32_t AConfiguration_match(AConfiguration *base, AConfiguration *requested);
int32_t AConfiguration_isBetterThan(AConfiguration *base, AConfiguration *test, AConfiguration *requested);
void AConfiguration_getLanguage(AConfiguration *config, char *outLanguage);
void AConfiguration_getCountry(AConfiguration *config, char *outCountry);
void AConfiguration_setLanguage(AConfiguration *config, const char *language);
void AConfiguration_setCountry(AConfiguration *config, const char *country);
int32_t AConfiguration_getMcc(AConfiguration *config);
int32_t AConfiguration_getMnc(AConfiguration *config);
int32_t AConfiguration_getOrientation(AConfiguration *config);
int32_t AConfiguration_getTouchscreen(AConfiguration *config);
int32_t AConfiguration_getDensity(AConfiguration *config);
int32_t AConfiguration_getKeyboard(AConfiguration *config);
int32_t AConfiguration_getNavigation(AConfiguration *config);
int32_t AConfiguration_getKeysHidden(AConfiguration *config);
int32_t AConfiguration_getNavHidden(AConfiguration *config);
int32_t AConfiguration_getSdkVersion(AConfiguration *config);
int32_t AConfiguration_getScreenSize(AConfiguration *config);
int32_t AConfiguration_getScreenLong(AConfiguration *config);
int32_t AConfiguration_getUiModeType(AConfiguration *config);
int32_t AConfiguration_getUiModeNight(AConfiguration *config);
int32_t AConfiguration_getScreenWidthDp(AConfiguration *config);
int32_t AConfiguration_getScreenHeightDp(AConfiguration *config);
int32_t AConfiguration_getSmallestScreenWidthDp(AConfiguration *config);
int32_t AConfiguration_getLayoutDirection(AConfiguration *config);
void AConfiguration_setMcc(AConfiguration *config, int32_t value);
void AConfiguration_setMnc(AConfiguration *config, int32_t value);
void AConfiguration_setOrientation(AConfiguration *config, int32_t value);
void AConfiguration_setTouchscreen(AConfiguration *config, int32_t value);
void AConfiguration_setDensity(AConfiguration *config, int32_t value);
void AConfiguration_setKeyboard(AConfiguration *config, int32_t value);
void AConfiguration_setNavigation(AConfiguration *config, int32_t value);
void AConfiguration_setKeysHidden(AConfiguration *config, int32_t value);
void AConfiguration_setNavHidden(AConfiguration *config, int32_t value);
void AConfiguration_setSdkVersion(AConfiguration *config, int32_t value);
void AConfiguration_setScreenSize(AConfiguration *config, int32_t value);
void AConfiguration_setScreenLong(AConfiguration *config, int32_t value);
void AConfiguration_setUiModeType(AConfiguration *config, int32_t value);
void AConfiguration_setUiModeNight(AConfiguration *config, int32_t value);
void AConfiguration_setScreenWidthDp(AConfiguration *config, int32_t value);
void AConfiguration_setScreenHeightDp(AConfiguration *config, int32_t value);
void AConfiguration_setSmallestScreenWidthDp(AConfiguration *config, int32_t value);
void AConfiguration_setLayoutDirection(AConfiguration *config, int32_t value);

#endif
