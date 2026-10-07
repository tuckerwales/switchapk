/*
 * Registers the Android natives that exist.
 */
#include "android.h"

void natives_android_register(void) {
    android_res_register();
    android_graphics_register();
    android_os_register();
    android_media_register();
    android_opengl_register();
    android_sqlite_register();
    android_net_register();
    native_activity_register();
}
