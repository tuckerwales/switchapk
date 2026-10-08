/*
 * android.net natives: the platform's view of connectivity for ConnectivityManager.
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

static const NativeMethodReg g_regs[] = {
    {"Landroid/net/ConnectivityManager;", "nGetState", "()I", ConnectivityManager_nGetState},
};

void android_net_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
