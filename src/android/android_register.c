/*
 * Registers the Android natives that exist. Media has no implementation
 * yet, so it stays unbound.
 */
#include "android.h"

void natives_android_register(void) {
    android_res_register();
    android_graphics_register();
    android_os_register();
    android_opengl_register();
    native_activity_register();
}
