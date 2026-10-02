/*
 * Registration of all built-in native method tables.
 */
#include "natives.h"

void vm_natives_init(void) {
    natives_java_lang_register();
    natives_java_io_register();
    natives_java_reflect_register();
    natives_java_annotation_register();
    natives_java_misc_register();
    natives_android_register();
}
