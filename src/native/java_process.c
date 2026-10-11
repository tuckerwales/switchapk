/*
 * java.lang.ProcessImpl natives over the in-process executables of src/nativeloader/spawn.c.
 * Reads, writes and waits block on the process, so they run with the GIL released and copy
 * through native buffers (Java arrays are only touched while holding it).
 */
#include "natives.h"
#include "../nativeloader/spawn.h"

#include <errno.h>

#define LOG_TAG "process"

static NlProc *proc_of(int64_t h) { return (NlProc *)(intptr_t)h; }

static char **string_array(ArrayObject *a) {
    int32_t n = a ? a->length : 0;
    char **out = sa_calloc((size_t)n + 1, sizeof(char *));
    for (int32_t i = 0; i < n; i++) {
        char *s = nat_str(ARRAY_DATA(a, Object *)[i]);
        out[i] = s ? s : sa_strdup("");
    }
    return out;
}

static void free_strings(char **v) {
    for (char **p = v; p && *p; p++) free(*p);
    free(v);
}

/* static native long nSpawn(String path, String[] argv, String[] envp, String dir, boolean mergeErr) */
NATIVE(ProcessImpl_nSpawn) {
    UNUSED_ARGS();
    char *path = nat_str(A_OBJ(0));
    char **argv = string_array(A_ARR(1));
    char **envp = string_array(A_ARR(2));
    char *dir = nat_str(A_OBJ(3));
    bool merge = A_BOOL(4);
    int err_no = 0;
    char err[256] = "";
    VM_BLOCKING_BEGIN(t); /* loading the image reads the file and runs its constructors */
    NlProc *p = path ? nl_spawn(path, (const char *const *)argv, (const char *const *)envp, dir, merge, &err_no, err,
                                sizeof err)
                     : NULL;
    VM_BLOCKING_END(t);
    free(path);
    free(dir);
    free_strings(argv);
    free_strings(envp);
    if (!p) {
        vm_throw_new(t, "Ljava/io/IOException;", "error=%d, %s", err_no ? err_no : ENOENT, err[0] ? err : "spawn failed");
        return;
    }
    R_LONG((int64_t)(intptr_t)p);
}

/* static native int nRead(long handle, int which, byte[] b, int off, int len) */
NATIVE(ProcessImpl_nRead) {
    UNUSED_ARGS();
    NlProc *p = proc_of(A_LONG(0));
    int which = A_INT(2);
    ArrayObject *b = A_ARR(3);
    int32_t off = A_INT(4), len = A_INT(5);
    if (len > 65536) len = 65536;
    uint8_t *tmp = sa_malloc((size_t)len);
    VM_BLOCKING_BEGIN(t);
    ssize_t n = nl_pipe_read(nl_proc_pipe(p, which), tmp, (size_t)len);
    VM_BLOCKING_END(t);
    if (n > 0) memcpy(ARRAY_DATA(b, uint8_t) + off, tmp, (size_t)n);
    free(tmp);
    R_INT(n > 0 ? (int32_t)n : -1);
}

/* static native void nWrite(long handle, byte[] b, int off, int len) */
NATIVE(ProcessImpl_nWrite) {
    UNUSED_ARGS();
    NlProc *p = proc_of(A_LONG(0));
    ArrayObject *b = A_ARR(2);
    int32_t off = A_INT(3), len = A_INT(4);
    uint8_t *tmp = sa_malloc((size_t)len);
    memcpy(tmp, ARRAY_DATA(b, uint8_t) + off, (size_t)len);
    VM_BLOCKING_BEGIN(t);
    ssize_t n = nl_pipe_write(nl_proc_pipe(p, 0), tmp, (size_t)len);
    VM_BLOCKING_END(t);
    free(tmp);
    if (n < 0) vm_throw_new(t, "Ljava/io/IOException;", "write failed: EPIPE (Broken pipe)");
}

NATIVE(ProcessImpl_nAvailable) {
    UNUSED_ARGS();
    R_INT((int32_t)nl_pipe_available(nl_proc_pipe(proc_of(A_LONG(0)), A_INT(2))));
}

NATIVE(ProcessImpl_nCloseRead) {
    UNUSED_ARGS();
    nl_pipe_close_read(nl_proc_pipe(proc_of(A_LONG(0)), A_INT(2)));
}

NATIVE(ProcessImpl_nCloseWrite) {
    UNUSED_ARGS();
    nl_pipe_close_write(nl_proc_pipe(proc_of(A_LONG(0)), 0));
}

NATIVE(ProcessImpl_nWaitFor) {
    UNUSED_ARGS();
    NlProc *p = proc_of(A_LONG(0));
    VM_BLOCKING_BEGIN(t);
    int code = nl_proc_wait(p);
    VM_BLOCKING_END(t);
    R_INT(code);
}

NATIVE(ProcessImpl_nExitValue) {
    UNUSED_ARGS();
    int code = -1;
    if (!nl_proc_exit_value(proc_of(A_LONG(0)), &code)) code = -1;
    R_INT(code);
}

NATIVE(ProcessImpl_nDestroy) {
    UNUSED_ARGS();
    nl_proc_destroy(proc_of(A_LONG(0)));
}

#define P "Ljava/lang/ProcessImpl;"
static const NativeMethodReg g_regs[] = {
    {P, "nSpawn", "(Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;Z)J", ProcessImpl_nSpawn},
    {P, "nRead", "(JI[BII)I", ProcessImpl_nRead},
    {P, "nWrite", "(J[BII)V", ProcessImpl_nWrite},
    {P, "nAvailable", "(JI)I", ProcessImpl_nAvailable},
    {P, "nCloseRead", "(JI)V", ProcessImpl_nCloseRead},
    {P, "nCloseWrite", "(J)V", ProcessImpl_nCloseWrite},
    {P, "nWaitFor", "(J)I", ProcessImpl_nWaitFor},
    {P, "nExitValue", "(J)I", ProcessImpl_nExitValue},
    {P, "nDestroy", "(J)V", ProcessImpl_nDestroy},
};

void natives_java_process_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
