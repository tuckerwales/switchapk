/*
 * libcore.crypto natives: operating system entropy for SecureRandom (platform_random_bytes:
 * getrandom(2) on the host, libnx randomGet on the Switch). The bytes go straight into the Java
 * array; the call is short and runs with the GIL held.
 */
#include "natives.h"

#include "../platform/platform.h"

#define LOG_TAG "security"

/* static void NativePrng.nativeRandomBytes(byte[] b, int off, int len) */
NATIVE(NativePrng_nativeRandomBytes) {
    SA_UNUSED(ret);
    ArrayObject *b = A_ARR(0);
    int32_t off = A_INT(1), len = A_INT(2);
    if (!b) {
        vm_throw_npe(t, "buffer");
        return;
    }
    if (off < 0 || len < 0 || (int64_t)off + len > b->length) {
        vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;", "off=%d len=%d length=%d", off, len, b->length);
        return;
    }
    if (len > 0 && !platform_random_bytes(ARRAY_DATA(b, uint8_t) + off, (size_t)len)) {
        vm_throw_new(t, "Ljava/security/ProviderException;", "no entropy source available");
    }
}

static const NativeMethodReg g_regs[] = {
    {"Llibcore/crypto/NativePrng;", "nativeRandomBytes", "([BII)V", NativePrng_nativeRandomBytes},
};

void natives_java_security_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
