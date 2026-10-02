/*
 * NDK layouts for ANativeActivity and ANativeWindow (android/native_activity.h
 * and android/native_window.h). Field order matches the NDK on 64-bit ABIs.
 * ANativeWindow itself is opaque; apps only see the buffer struct.
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

#endif
