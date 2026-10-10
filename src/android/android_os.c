/*
 * android.os / android.view natives: the main-thread event pump, logging,
 * presentation and the display. The stashed event is main-thread only:
 * nativePollOnce consumes one platform event and nNextEvent drains it.
 */
#include "android.h"
#include "../platform/platform.h"

#define LOG_TAG "android"

static PlatformEvent g_stashed;
static bool g_have_event;
static char *g_text;

static void drop_event(PlatformEvent *ev) {
    free(ev->text);
    ev->text = NULL;
}

static void fill_event(ArrayObject *iv, ArrayObject *fv, ArrayObject *tv, const PlatformEvent *ev) {
    if (iv && iv->length >= 4) {
        int32_t *p = ARRAY_DATA(iv, int32_t);
        p[0] = ev->a;
        p[1] = ev->b;
        p[2] = ev->c;
        p[3] = ev->d;
    }
    if (fv && fv->length >= 8) memcpy(ARRAY_DATA(fv, float), ev->f, sizeof ev->f);
    if (tv && tv->length >= 1) ARRAY_DATA(tv, int64_t)[0] = ev->time_ns;
}

bool nativeloader_poll_main(int timeout_ms);

/* static native void nativePollOnce(int timeoutMillis) */
NATIVE(MessageQueue_nativePollOnce) {
    int timeout = A_INT(0);
    if (g_have_event) drop_event(&g_stashed);
    PlatformEvent ev;
    memset(&ev, 0, sizeof ev);
    vm_gil_release(t);
    /* With a main ALooper the thread blocks there (native fd callbacks run); platform events wake it. */
    bool got = platform_wait_event(&ev, 0);
    if (!got && nativeloader_poll_main(timeout)) {
        got = platform_wait_event(&ev, 0);
    } else if (!got && timeout != 0) {
        got = platform_wait_event(&ev, timeout);
    }
    vm_gil_acquire(t);
    if (!got) {
        g_have_event = false;
        return;
    }
    g_stashed = ev;
    g_have_event = true;
}

/* static native void nativeWake() */
NATIVE(MessageQueue_nativeWake) {
    UNUSED_ARGS();
    platform_wake();
}

/* static native void native_println(int priority, String tag, String msg) */
NATIVE(Log_native_println) {
    int prio = A_INT(0);
    char *tag = nat_str(A_OBJ(1));
    char *msg = nat_str(A_OBJ(2));
    if (prio < SA_LOG_VERBOSE) prio = SA_LOG_VERBOSE;
    if (prio > SA_LOG_FATAL) prio = SA_LOG_FATAL;
    sa_log(prio, tag ? tag : "java", "%s", msg ? msg : "");
    free(tag);
    free(msg);
}

/* static native void nPresent(int[] pixels, int w, int h) */
NATIVE(WindowManagerGlobal_nPresent) {
    ArrayObject *px = A_ARR(0);
    int w = A_INT(1);
    int h = A_INT(2);
    if (!px || w <= 0 || h <= 0) return;
    size_t n = (size_t)w * (size_t)h;
    if ((size_t)px->length < n) return;
    uint32_t *copy = malloc(n * sizeof(uint32_t));
    if (!copy) return;
    memcpy(copy, ARRAY_DATA(px, uint32_t), n * sizeof(uint32_t));
    vm_gil_release(t);
    platform_present(copy, w, h, w);
    vm_gil_acquire(t);
    free(copy);
}

/* static native int nNextEvent(int[] ints, float[] floats, long[] timeNs) */
NATIVE(PlatformInput_nNextEvent) {
    ArrayObject *iv = A_ARR(0);
    ArrayObject *fv = A_ARR(1);
    ArrayObject *tv = A_ARR(2);
    PlatformEvent ev;
    memset(&ev, 0, sizeof ev);
    if (g_have_event) {
        ev = g_stashed;
        memset(&g_stashed, 0, sizeof g_stashed);
        g_have_event = false;
    } else {
        vm_gil_release(t);
        bool got = platform_wait_event(&ev, 0);
        vm_gil_acquire(t);
        if (!got) {
            R_INT(0);
            return;
        }
    }
    fill_event(iv, fv, tv, &ev);
    if (ev.kind == PEV_TEXT) {
        free(g_text);
        g_text = ev.text;
        ev.text = NULL;
    } else {
        drop_event(&ev);
    }
    R_INT(ev.kind);
}

/* static native String nTakeText() */
NATIVE(PlatformInput_nTakeText) {
    UNUSED_ARGS();
    char *text = g_text;
    g_text = NULL;
    R_OBJ(text ? vm_new_string_utf8(t, text) : NULL);
    free(text);
}

/* static native void nRequestText(int id, String initial, String hint, int inputType, int maxLen) */
NATIVE(InputMethodManager_nRequestText) {
    int id = A_INT(0);
    char *initial = nat_str(A_OBJ(1));
    char *hint = nat_str(A_OBJ(2));
    int input_type = A_INT(3);
    int max_len = A_INT(4);
    vm_gil_release(t);
    platform_request_text(id, initial ? initial : "", hint ? hint : "", input_type, max_len);
    vm_gil_acquire(t);
    free(initial);
    free(hint);
}

/* static native void nativeVibrate(int ms, int amplitude) */
NATIVE(Vibrator_nativeVibrate) {
    platform_vibrate(A_INT(0), A_INT(1));
}

/* static native int nGetSensorMask() */
NATIVE(SystemSensorManager_nGetSensorMask) {
    UNUSED_ARGS();
    R_INT((int32_t)platform_sensor_mask());
}

/* static native void nSetRate(int type, int periodUs) */
NATIVE(SystemSensorManager_nSetRate) {
    platform_sensor_set_rate(A_INT(0), A_INT(1));
}

/* static native void nGetState(int[] out): level, plugged, charging, voltage mV, temperature (0.1 C) */
NATIVE(BatteryManager_nGetState) {
    ArrayObject *out = A_ARR(0);
    if (!out || out->length < 5) return;
    PlatformBattery b;
    memset(&b, 0, sizeof b);
    vm_gil_release(t);
    platform_battery_state(&b);
    vm_gil_acquire(t);
    int32_t *p = ARRAY_DATA(out, int32_t);
    p[0] = b.level;
    p[1] = b.plugged;
    p[2] = b.charging ? 1 : 0;
    p[3] = b.voltage_mv;
    p[4] = b.temperature;
}

/* static native void nGetInfo(int[] out) */
NATIVE(Display_nGetInfo) {
    ArrayObject *out = A_ARR(0);
    if (!out || out->length < 5) return;
    PlatformDisplay d;
    platform_get_display(&d);
    int32_t *p = ARRAY_DATA(out, int32_t);
    p[0] = d.width;
    p[1] = d.height;
    p[2] = d.dpi;
    p[3] = (int32_t)(d.refresh_hz * 1000.f);
    p[4] = d.touch ? 1 : 0;
}

void android_request_quit(void) {
    PlatformEvent ev;
    memset(&ev, 0, sizeof ev);
    ev.kind = PEV_QUIT;
    platform_push_event(&ev);
}

static const NativeMethodReg g_regs[] = {
    {"Landroid/os/MessageQueue;", "nativePollOnce", "(I)V", MessageQueue_nativePollOnce},
    {"Landroid/os/MessageQueue;", "nativeWake", "()V", MessageQueue_nativeWake},
    {"Landroid/util/Log;", "native_println", "(ILjava/lang/String;Ljava/lang/String;)V", Log_native_println},
    {"Landroid/view/WindowManagerGlobal;", "nPresent", "([III)V", WindowManagerGlobal_nPresent},
    {"Landroid/view/PlatformInput;", "nNextEvent", "([I[F[J)I", PlatformInput_nNextEvent},
    {"Landroid/view/PlatformInput;", "nTakeText", "()Ljava/lang/String;", PlatformInput_nTakeText},
    {"Landroid/view/inputmethod/InputMethodManager;", "nRequestText",
     "(ILjava/lang/String;Ljava/lang/String;II)V", InputMethodManager_nRequestText},
    {"Landroid/os/Vibrator$SystemVibrator;", "nativeVibrate", "(II)V", Vibrator_nativeVibrate},
    {"Landroid/hardware/SystemSensorManager;", "nGetSensorMask", "()I", SystemSensorManager_nGetSensorMask},
    {"Landroid/hardware/SystemSensorManager;", "nSetRate", "(II)V", SystemSensorManager_nSetRate},
    {"Landroid/os/BatteryManager;", "nGetState", "([I)V", BatteryManager_nGetState},
    {"Landroid/view/Display;", "nGetInfo", "([I)V", Display_nGetInfo},
};

void android_os_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
