/*
 * Native input sample (WS9). Follows the structure of android_native_app_glue
 * (the NDK's static glue library): ANativeActivity_onCreate starts an app
 * thread; that thread prepares an ALooper, watches a command pipe the activity
 * callbacks write to, attaches the AInputQueue when it is created, and polls.
 * Everything the app sees is drawn into the window from that thread, which
 * exercises native-thread access to the VM through ANativeWindow.
 *
 * It is a re-implementation of the pattern, not the NDK's file, which is not
 * available here. Calls and call order are the glue's.
 */
#include "ndk_android.h"

int __android_log_print(int prio, const char *tag, const char *fmt, ...);
int pipe(int fds[2]);
long read(int fd, void *buf, unsigned long n);
long write(int fd, const void *buf, unsigned long n);
int close(int fd);
int usleep(unsigned int usec);
int pthread_create(unsigned long *thread, const void *attr, void *(*fn)(void *), void *arg);
int pthread_detach(unsigned long thread);

#define LOG(...) __android_log_print(4, "inputglue", __VA_ARGS__)

enum { LOOPER_ID_MAIN = 1, LOOPER_ID_INPUT = 2, LOOPER_ID_USER = 3 };
enum { CMD_INIT_WINDOW = 1, CMD_TERM_WINDOW = 2, CMD_INPUT_CREATED = 3, CMD_INPUT_DESTROYED = 4, CMD_DESTROY = 5 };

static ANativeActivity *g_activity;
static int g_msgread, g_msgwrite;
static int g_cbpipe[2];
static ALooper *g_looper;
static volatile int g_looper_ready;

/* written by the activity's thread, read by the app thread (the glue uses a mutex; a pipe carries the order) */
static ANativeWindow *g_pending_window;
static AInputQueue *g_pending_queue;

static ANativeWindow *g_window;
static AInputQueue *g_queue;

static int g_wake_ok, g_cb_ok, g_config_ok;
static int g_touch_seen, g_touch_x, g_touch_y;
static int g_key_down, g_key_up;
static int g_dirty = 1;

static void post_cmd(int8_t cmd) { write(g_msgwrite, &cmd, 1); }

/* ---- activity callbacks (VM main thread) ---------------------------------------------------------------- */

static void on_window_created(ANativeActivity *a, ANativeWindow *w) {
    (void)a;
    ANativeWindow_acquire(w);
    g_pending_window = w;
    post_cmd(CMD_INIT_WINDOW);
}

static void on_window_destroyed(ANativeActivity *a, ANativeWindow *w) {
    (void)a;
    (void)w;
    post_cmd(CMD_TERM_WINDOW);
}

static void on_input_queue_created(ANativeActivity *a, AInputQueue *q) {
    (void)a;
    g_pending_queue = q;
    post_cmd(CMD_INPUT_CREATED);
}

static void on_input_queue_destroyed(ANativeActivity *a, AInputQueue *q) {
    (void)a;
    (void)q;
    post_cmd(CMD_INPUT_DESTROYED);
    /* the glue waits until the app thread has detached the queue: it frees it right after this returns */
    for (int i = 0; i < 200 && g_queue; i++) usleep(5000);
}

static void on_destroy(ANativeActivity *a) {
    (void)a;
    LOG("INPUT onDestroy");
    post_cmd(CMD_DESTROY);
}

/* ---- drawing ---------------------------------------------------------------------------------------------- */

static void draw(void) {
    if (!g_window) return;
    ANativeWindow_Buffer buf;
    if (ANativeWindow_lock(g_window, &buf, 0) != 0) return;
    int ok = g_wake_ok && g_cb_ok && g_config_ok;
    for (int y = 0; y < buf.height; y++) {
        unsigned char *row = (unsigned char *)buf.bits + (size_t)y * (size_t)buf.stride * 4;
        for (int x = 0; x < buf.width; x++) {
            unsigned char r = 0x10, g = 0x20, b = 0x40;
            if (y < 40) {
                r = ok ? 0 : 198;
                g = ok ? 200 : 40;
                b = ok ? 83 : 40;
            } else if (y >= buf.height - 40 && g_key_down && g_key_up) {
                r = 233;
                g = 30;
                b = 99;
            } else if (g_touch_seen && x >= g_touch_x - 20 && x < g_touch_x + 20 && y >= g_touch_y - 20 &&
                       y < g_touch_y + 20) {
                r = 255;
                g = 193;
                b = 7;
            }
            unsigned char *p = row + x * 4;
            p[0] = r;
            p[1] = g;
            p[2] = b;
            p[3] = 255;
        }
    }
    ANativeWindow_unlockAndPost(g_window);
    g_dirty = 0;
}

/* ---- app thread -------------------------------------------------------------------------------------------- */

static void process_input(void) {
    AInputEvent *ev = 0;
    while (AInputQueue_getEvent(g_queue, &ev) >= 0) {
        if (AInputQueue_preDispatchEvent(g_queue, ev)) continue;
        int handled = 0;
        if (AInputEvent_getType(ev) == AINPUT_EVENT_TYPE_MOTION) {
            int action = AMotionEvent_getAction(ev) & AMOTION_EVENT_ACTION_MASK;
            if (action == AMOTION_EVENT_ACTION_DOWN) {
                g_touch_x = (int)AMotionEvent_getX(ev, 0);
                g_touch_y = (int)AMotionEvent_getY(ev, 0);
                g_touch_seen = 1;
                g_dirty = 1;
                LOG("INPUT touch down %d %d (pointers %d, id %d, source 0x%x)", g_touch_x, g_touch_y,
                    (int)AMotionEvent_getPointerCount(ev), AMotionEvent_getPointerId(ev, 0),
                    AInputEvent_getSource(ev));
            }
            handled = 1;
        } else if (AInputEvent_getType(ev) == AINPUT_EVENT_TYPE_KEY) {
            int code = AKeyEvent_getKeyCode(ev);
            int action = AKeyEvent_getAction(ev);
            if (code == 23) {
                LOG("INPUT key %d %s", code, action == AKEY_EVENT_ACTION_DOWN ? "down" : "up");
                if (action == AKEY_EVENT_ACTION_DOWN) g_key_down = 1;
                else g_key_up = g_key_down;
                g_dirty = 1;
                handled = 1;
            } else if (code == 4) {
                LOG("INPUT back unhandled");
            }
        }
        AInputQueue_finishEvent(g_queue, ev, handled);
    }
}

static int on_cb_fd(int fd, int events, void *data) {
    char c;
    (void)events;
    (void)data;
    read(fd, &c, 1);
    g_cb_ok = 1;
    g_dirty = 1;
    LOG("INPUT callback");
    return 1;
}

static void *helper_thread(void *arg) {
    (void)arg;
    usleep(150000);
    char c = 1;
    write(g_cbpipe[1], &c, 1); /* an fd with a callback becomes readable */
    usleep(100000);
    ALooper_wake(g_looper); /* from another thread */
    return 0;
}

static void handle_cmd(int8_t cmd) {
    switch (cmd) {
    case CMD_INIT_WINDOW:
        g_window = g_pending_window;
        g_dirty = 1;
        break;
    case CMD_TERM_WINDOW:
        if (g_window) ANativeWindow_release(g_window);
        g_window = 0;
        break;
    case CMD_INPUT_CREATED:
        g_queue = g_pending_queue;
        AInputQueue_attachLooper(g_queue, g_looper, LOOPER_ID_INPUT, 0, 0);
        LOG("INPUT queue attached");
        break;
    case CMD_INPUT_DESTROYED:
        if (g_queue) AInputQueue_detachLooper(g_queue);
        g_queue = 0;
        break;
    default:
        break;
    }
}

static void *app_main(void *arg) {
    (void)arg;
    g_looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
    ALooper_addFd(g_looper, g_msgread, LOOPER_ID_MAIN, ALOOPER_EVENT_INPUT, 0, 0);
    ALooper_addFd(g_looper, g_cbpipe[0], LOOPER_ID_USER, ALOOPER_EVENT_INPUT, on_cb_fd, 0);
    /* hand the user-ident fd to the callback: addFd with a callback ignores ident, like the NDK */

    AConfiguration *cfg = AConfiguration_new();
    AConfiguration_fromAssetManager(cfg, g_activity->assetManager);
    g_config_ok = AConfiguration_getOrientation(cfg) == 2 && AConfiguration_getDensity(cfg) == 240 &&
                  AConfiguration_getScreenWidthDp(cfg) == 853 && AConfiguration_getSdkVersion(cfg) == 29 &&
                  AConfiguration_getTouchscreen(cfg) == 3;
    LOG("INPUT config orientation %d density %d widthDp %d heightDp %d ok %d", AConfiguration_getOrientation(cfg),
        AConfiguration_getDensity(cfg), AConfiguration_getScreenWidthDp(cfg), AConfiguration_getScreenHeightDp(cfg),
        g_config_ok);
    AConfiguration_delete(cfg);

    g_looper_ready = 1;
    LOG("INPUT looper ready");
    unsigned long helper;
    pthread_create(&helper, 0, helper_thread, 0);
    pthread_detach(helper);

    for (;;) {
        int fd, events;
        void *data;
        int ident = ALooper_pollAll(g_dirty && g_window ? 0 : -1, &fd, &events, &data);
        if (ident == ALOOPER_POLL_WAKE) {
            g_wake_ok = 1;
            g_dirty = 1;
            LOG("INPUT wake");
        } else if (ident == LOOPER_ID_MAIN) {
            int8_t cmd;
            if (read(g_msgread, &cmd, 1) == 1) {
                if (cmd == CMD_DESTROY) break;
                handle_cmd(cmd);
            }
        } else if (ident == LOOPER_ID_INPUT) {
            process_input();
        }
        if (g_dirty && g_window) draw();
    }
    return 0;
}

void ANativeActivity_onCreate(ANativeActivity *activity, void *saved_state, size_t saved_state_size) {
    (void)saved_state;
    (void)saved_state_size;
    g_activity = activity;
    activity->callbacks->onDestroy = on_destroy;
    activity->callbacks->onNativeWindowCreated = on_window_created;
    activity->callbacks->onNativeWindowDestroyed = on_window_destroyed;
    activity->callbacks->onInputQueueCreated = on_input_queue_created;
    activity->callbacks->onInputQueueDestroyed = on_input_queue_destroyed;
    int fds[2];
    pipe(fds);
    g_msgread = fds[0];
    g_msgwrite = fds[1];
    pipe(g_cbpipe);
    unsigned long t;
    pthread_create(&t, 0, app_main, 0);
    pthread_detach(t);
}
