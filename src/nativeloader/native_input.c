/*
 * AInputQueue and AInputEvent for native code (WS9). Events come from
 * android.app.NativeActivity, which copies each KeyEvent and MotionEvent
 * into a C record (native_input_enqueue_*) instead of letting views see it.
 * The record carries up to NI_MAX_POINTERS pointers with all
 * NI_AXES MotionEvent axes each, so AMotionEvent_getAxisValue answers
 * joystick and trigger axes too. Motion history is not kept (history size 0).
 *
 * The queue is read from a thread of the app's choosing (the glue thread):
 * a pipe holds one byte per queued event, and attachLooper registers its
 * read end with the app's ALooper. Plain C over pthread and pipe, so it needs
 * no VM access. On the Switch the pipe is virtual (vfd.c).
 */
#include "native_input.h"
#include "../core/common.h"

#include <pthread.h>
#include <stdatomic.h>

#define LOG_TAG "input"

#include <errno.h>
#ifdef __SWITCH__
/* no pipe in newlib: the shim's virtual pipes, which the Switch ALooper polls */
#include "vfd.h"
#define in_pipe(fds) vfd_pipe(fds, VFD_NONBLOCK)
#define in_read vfd_read
#define in_write vfd_write
#define in_close vfd_close
#else
#include <fcntl.h>
#include <unistd.h>
static int in_pipe(int fds[2]) {
    if (pipe(fds) != 0) return -1;
    for (int i = 0; i < 2; i++) {
        fcntl(fds[i], F_SETFL, fcntl(fds[i], F_GETFL) | O_NONBLOCK);
        fcntl(fds[i], F_SETFD, FD_CLOEXEC);
    }
    return 0;
}
#define in_read read
#define in_write write
#define in_close close
#endif

#define NI_MAX_POINTERS 10
#define NI_AXES 24
#define NI_MAX_QUEUED 1024

struct AInputEvent {
    struct AInputEvent *next;
    int32_t type;
    int32_t action, source, device_id, flags, meta_state;
    /* key */
    int32_t key_code, scan_code, repeat;
    /* motion */
    int32_t button_state, edge_flags, pointer_count;
    int64_t down_time, event_time;
    int32_t ids[NI_MAX_POINTERS];
    float axes[NI_MAX_POINTERS * NI_AXES];
};

struct AInputQueue {
    pthread_mutex_t lock;
    AInputEvent *head, *tail;
    int count;
    int fds[2]; /* one byte per queued event */
    ALooper *looper;
    bool attached;
    void (*unhandled)(void *user, const AInputEvent *event);
    void *user;
};

/* ---- queue ------------------------------------------------------------------------------------------------ */


AInputQueue *native_input_queue_new(void (*unhandled)(void *, const AInputEvent *), void *user) {
    AInputQueue *q = sa_calloc(1, sizeof *q);
    pthread_mutex_init(&q->lock, NULL);
    if (in_pipe(q->fds) != 0) {
        LOGE("cannot create the input pipe: %s", strerror(errno));
        free(q);
        return NULL;
    }
    q->unhandled = unhandled;
    q->user = user;
    return q;
}

void native_input_queue_free(AInputQueue *q) {
    if (!q) return;
    if (q->attached) AInputQueue_detachLooper(q);
    pthread_mutex_lock(&q->lock);
    while (q->head) {
        AInputEvent *e = q->head;
        q->head = e->next;
        free(e);
    }
    pthread_mutex_unlock(&q->lock);
    in_close(q->fds[0]);
    in_close(q->fds[1]);
    pthread_mutex_destroy(&q->lock);
    free(q);
}

bool native_input_queue_attached(AInputQueue *q) {
    if (!q) return false;
    pthread_mutex_lock(&q->lock);
    bool a = q->attached;
    pthread_mutex_unlock(&q->lock);
    return a;
}

static bool enqueue(AInputQueue *q, AInputEvent *e) {
    pthread_mutex_lock(&q->lock);
    if (!q->attached || q->count >= NI_MAX_QUEUED) {
        pthread_mutex_unlock(&q->lock);
        free(e);
        return q->attached; /* dropped when full; not attached means the caller keeps the event */
    }
    e->next = NULL;
    if (q->tail) q->tail->next = e;
    else q->head = e;
    q->tail = e;
    q->count++;
    pthread_mutex_unlock(&q->lock);
    char c = 1;
    ssize_t r;
    do {
        r = in_write(q->fds[1], &c, 1);
    } while (r < 0 && errno == EINTR);
    return true;
}

bool native_input_enqueue_key(AInputQueue *q, int32_t action, int32_t key_code, int32_t scan_code, int32_t meta_state,
                              int32_t repeat, int32_t flags, int32_t source, int32_t device_id, int64_t down_time,
                              int64_t event_time) {
    if (!native_input_queue_attached(q)) return false;
    AInputEvent *e = sa_calloc(1, sizeof *e);
    e->type = AINPUT_EVENT_TYPE_KEY;
    e->action = action;
    e->key_code = key_code;
    e->scan_code = scan_code;
    e->meta_state = meta_state;
    e->repeat = repeat;
    e->flags = flags;
    e->source = source;
    e->device_id = device_id;
    e->down_time = down_time;
    e->event_time = event_time;
    return enqueue(q, e);
}

bool native_input_enqueue_motion(AInputQueue *q, int32_t action, int32_t source, int32_t device_id, int32_t flags,
                                 int32_t meta_state, int32_t button_state, int32_t edge_flags, int64_t down_time,
                                 int64_t event_time, int32_t pointer_count, const int32_t *ids, const float *axes) {
    if (!native_input_queue_attached(q)) return false;
    if (pointer_count < 1) return true;
    if (pointer_count > NI_MAX_POINTERS) pointer_count = NI_MAX_POINTERS;
    AInputEvent *e = sa_calloc(1, sizeof *e);
    e->type = AINPUT_EVENT_TYPE_MOTION;
    e->action = action;
    e->source = source;
    e->device_id = device_id;
    e->flags = flags;
    e->meta_state = meta_state;
    e->button_state = button_state;
    e->edge_flags = edge_flags;
    e->down_time = down_time;
    e->event_time = event_time;
    e->pointer_count = pointer_count;
    memcpy(e->ids, ids, sizeof(int32_t) * (size_t)pointer_count);
    memcpy(e->axes, axes, sizeof(float) * (size_t)pointer_count * NI_AXES);
    return enqueue(q, e);
}

void AInputQueue_attachLooper(AInputQueue *q, ALooper *looper, int ident, ALooper_callbackFunc cb, void *data) {
    if (!q || !looper) return;
    pthread_mutex_lock(&q->lock);
    q->looper = looper;
    q->attached = true;
    pthread_mutex_unlock(&q->lock);
    ALooper_addFd(looper, q->fds[0], ident, ALOOPER_EVENT_INPUT, cb, data);
}

void AInputQueue_detachLooper(AInputQueue *q) {
    if (!q) return;
    pthread_mutex_lock(&q->lock);
    ALooper *l = q->looper;
    q->looper = NULL;
    q->attached = false;
    pthread_mutex_unlock(&q->lock);
    if (l) ALooper_removeFd(l, q->fds[0]);
}

int32_t AInputQueue_hasEvents(AInputQueue *q) {
    if (!q) return -1;
    pthread_mutex_lock(&q->lock);
    int32_t has = q->head != NULL;
    pthread_mutex_unlock(&q->lock);
    return has;
}

int32_t AInputQueue_getEvent(AInputQueue *q, AInputEvent **out) {
    if (!q || !out) return -1;
    pthread_mutex_lock(&q->lock);
    AInputEvent *e = q->head;
    if (e) {
        q->head = e->next;
        if (!q->head) q->tail = NULL;
        q->count--;
    }
    pthread_mutex_unlock(&q->lock);
    if (!e) {
        *out = NULL;
        return -1; /* -EAGAIN in AOSP: nothing queued */
    }
    char c;
    ssize_t r;
    do {
        r = in_read(q->fds[0], &c, 1);
    } while (r < 0 && errno == EINTR);
    *out = e;
    return 0;
}

int32_t AInputQueue_preDispatchEvent(AInputQueue *q, AInputEvent *e) {
    /* no input method filters events here */
    SA_UNUSED(q);
    SA_UNUSED(e);
    return 0;
}

void AInputQueue_finishEvent(AInputQueue *q, AInputEvent *e, int handled) {
    if (!e) return;
    if (!handled && q && q->unhandled) q->unhandled(q->user, e);
    free(e);
}
/* ---- events ----------------------------------------------------------------------------------------------- */

int32_t AInputEvent_getType(const AInputEvent *e) { return e->type; }
int32_t AInputEvent_getDeviceId(const AInputEvent *e) { return e->device_id; }
int32_t AInputEvent_getSource(const AInputEvent *e) { return e->source; }

int32_t AKeyEvent_getAction(const AInputEvent *e) { return e->action; }
int32_t AKeyEvent_getFlags(const AInputEvent *e) { return e->flags; }
int32_t AKeyEvent_getKeyCode(const AInputEvent *e) { return e->key_code; }
int32_t AKeyEvent_getScanCode(const AInputEvent *e) { return e->scan_code; }
int32_t AKeyEvent_getMetaState(const AInputEvent *e) { return e->meta_state; }
int32_t AKeyEvent_getRepeatCount(const AInputEvent *e) { return e->repeat; }
int64_t AKeyEvent_getDownTime(const AInputEvent *e) { return e->down_time; }
int64_t AKeyEvent_getEventTime(const AInputEvent *e) { return e->event_time; }

int32_t AMotionEvent_getAction(const AInputEvent *e) { return e->action; }
int32_t AMotionEvent_getFlags(const AInputEvent *e) { return e->flags; }
int32_t AMotionEvent_getMetaState(const AInputEvent *e) { return e->meta_state; }
int32_t AMotionEvent_getButtonState(const AInputEvent *e) { return e->button_state; }
int32_t AMotionEvent_getEdgeFlags(const AInputEvent *e) { return e->edge_flags; }
int64_t AMotionEvent_getDownTime(const AInputEvent *e) { return e->down_time; }
int64_t AMotionEvent_getEventTime(const AInputEvent *e) { return e->event_time; }
float AMotionEvent_getXOffset(const AInputEvent *e) {
    SA_UNUSED(e);
    return 0.0f;
}
float AMotionEvent_getYOffset(const AInputEvent *e) {
    SA_UNUSED(e);
    return 0.0f;
}
float AMotionEvent_getXPrecision(const AInputEvent *e) {
    SA_UNUSED(e);
    return 1.0f;
}
float AMotionEvent_getYPrecision(const AInputEvent *e) {
    SA_UNUSED(e);
    return 1.0f;
}
size_t AMotionEvent_getPointerCount(const AInputEvent *e) { return (size_t)e->pointer_count; }

int32_t AMotionEvent_getPointerId(const AInputEvent *e, size_t i) { return i < (size_t)e->pointer_count ? e->ids[i] : -1; }

int32_t AMotionEvent_getToolType(const AInputEvent *e, size_t i) {
    SA_UNUSED(i);
    /* AMOTION_EVENT_TOOL_TYPE_FINGER for touch, UNKNOWN (0) for sticks and pads */
    return (e->source & 0x2) ? 1 : 0;
}

float AMotionEvent_getAxisValue(const AInputEvent *e, int32_t axis, size_t i) {
    if (i >= (size_t)e->pointer_count || axis < 0 || axis >= NI_AXES) return 0.0f;
    return e->axes[i * NI_AXES + (size_t)axis];
}

float AMotionEvent_getRawX(const AInputEvent *e, size_t i) { return AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_X, i); }
float AMotionEvent_getRawY(const AInputEvent *e, size_t i) { return AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_Y, i); }
float AMotionEvent_getX(const AInputEvent *e, size_t i) { return AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_X, i); }
float AMotionEvent_getY(const AInputEvent *e, size_t i) { return AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_Y, i); }
float AMotionEvent_getPressure(const AInputEvent *e, size_t i) {
    return AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_PRESSURE, i);
}
float AMotionEvent_getSize(const AInputEvent *e, size_t i) { return AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_SIZE, i); }
float AMotionEvent_getTouchMajor(const AInputEvent *e, size_t i) {
    return AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_TOUCH_MAJOR, i);
}
float AMotionEvent_getTouchMinor(const AInputEvent *e, size_t i) {
    return AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_TOUCH_MINOR, i);
}
float AMotionEvent_getToolMajor(const AInputEvent *e, size_t i) {
    return AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_TOOL_MAJOR, i);
}
float AMotionEvent_getToolMinor(const AInputEvent *e, size_t i) {
    return AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_TOOL_MINOR, i);
}
float AMotionEvent_getOrientation(const AInputEvent *e, size_t i) {
    return AMotionEvent_getAxisValue(e, AMOTION_EVENT_AXIS_ORIENTATION, i);
}
size_t AMotionEvent_getHistorySize(const AInputEvent *e) {
    SA_UNUSED(e);
    return 0;
}
int32_t AMotionEvent_findPointerIndex(const AInputEvent *e, int32_t pointer_id) {
    for (int32_t i = 0; i < e->pointer_count; i++)
        if (e->ids[i] == pointer_id) return i;
    return -1;
}
