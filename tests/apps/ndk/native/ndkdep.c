/* A dependency of libndktest.so (DT_NEEDED): exports a function and runs a constructor. */
static int g_ctor_ran;

__attribute__((constructor)) static void dep_init(void) { g_ctor_ran = 1; }

int dep_value(void) { return 42; }

int dep_ctor_state(void) { return g_ctor_ran; }
