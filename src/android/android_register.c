/*
 * Registers the Android natives that exist. Media and GL have no
 * implementation yet, so they stay unbound.
 */
#include "android.h"

void natives_android_register(void) {
    android_res_register();
    android_graphics_register();
    android_os_register();
}
