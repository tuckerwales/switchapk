/*
 * The host side of AInputQueue (WS9): android.app.NativeActivity feeds
 * events in, the app's thread reads them through the NDK API (native_input.c).
 */
#ifndef SWITCHAPK_NATIVE_INPUT_H
#define SWITCHAPK_NATIVE_INPUT_H

#include "ndk_android.h"

#include <stdbool.h>

/* unhandled is called from finishEvent(handled = 0) on the reading thread. */
AInputQueue *native_input_queue_new(void (*unhandled)(void *user, const AInputEvent *event), void *user);
void native_input_queue_free(AInputQueue *queue);

/* True once the app attached the queue to a looper. Events are only accepted then. */
bool native_input_queue_attached(AInputQueue *queue);

/* Copy an event into the queue. They return false when the queue is not attached (the caller then
 * dispatches the event to views as usual). Safe from any thread. */
bool native_input_enqueue_key(AInputQueue *queue, int32_t action, int32_t key_code, int32_t scan_code,
                              int32_t meta_state, int32_t repeat, int32_t flags, int32_t source, int32_t device_id,
                              int64_t down_time, int64_t event_time);

/* axes holds 24 floats per pointer, indexed by AMOTION_EVENT_AXIS_*. */
bool native_input_enqueue_motion(AInputQueue *queue, int32_t action, int32_t source, int32_t device_id, int32_t flags,
                                 int32_t meta_state, int32_t button_state, int32_t edge_flags, int64_t down_time,
                                 int64_t event_time, int32_t pointer_count, const int32_t *ids, const float *axes);

#endif
