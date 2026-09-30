/* Temporary: replaced by the Android application runner. */
#include "../vm/vm.h"
int app_run_apk(const char *path, const char *data_dir, void *stack_hi) {
    SA_UNUSED(data_dir);
    SA_UNUSED(stack_hi);
    fprintf(stderr, "APK support not built: %s\n", path);
    return 1;
}
