/*
 * android.net natives: the platform's view of connectivity for ConnectivityManager, and the system browser
 * (android.app.SystemBrowser) that ACTION_VIEW on web URLs opens.
 */
#include "android.h"
#include "../platform/platform.h"

/* static int ConnectivityManager.nGetState(): bit 0 connected, bits 1-3 PLATFORM_NET_*, bits 4-7 signal + 1 */
static void ConnectivityManager_nGetState(VMThread *t, uint64_t *args, JValue *ret) {
    SA_UNUSED(t);
    SA_UNUSED(args);
    PlatformNetwork n;
    platform_network_state(&n);
    int signal = n.signal < 0 ? 0 : n.signal + 1;
    ret->raw = (uint32_t)((n.connected ? 1 : 0) | ((n.transport & 7) << 1) | ((signal & 15) << 4));
}

/* static int SystemBrowser.nOpenUrl(String url, String callback, String[] out): PLATFORM_BROWSER_*; out[0] gets
 * the callback URL. Blocks until the browser closes, with the GIL released. */
static void SystemBrowser_nOpenUrl(VMThread *t, uint64_t *args, JValue *ret) {
    char *url = nat_str((Object *)(uintptr_t)args[0]);
    char *callback = nat_str((Object *)(uintptr_t)args[1]);
    ArrayObject *out = (ArrayObject *)(uintptr_t)args[2];
    char *got = sa_calloc(1, 8192);
    VM_BLOCKING_BEGIN(t);
    int r = platform_open_url(url, callback, got, 8192);
    VM_BLOCKING_END(t);
    if (r == PLATFORM_BROWSER_CALLBACK && out && out->length > 0) {
        ARRAY_DATA(out, Object *)[0] = vm_new_string_utf8(t, got);
    }
    free(got);
    free(url);
    free(callback);
    ret->raw = (uint32_t)r;
}

static const NativeMethodReg g_regs[] = {
    {"Landroid/app/SystemBrowser;", "nOpenUrl", "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)I",
     SystemBrowser_nOpenUrl},
    {"Landroid/net/ConnectivityManager;", "nGetState", "()I", ConnectivityManager_nGetState},
};

void android_net_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
