/*
 * In-process child processes for bionic executables (spawn.c). The Java Process natives
 * (src/native/java_process.c) drive these; the shim consults the process context.
 */
#ifndef SWITCHAPK_SPAWN_H
#define SWITCHAPK_SPAWN_H

#include <stdbool.h>
#include <stdio.h>
#include <sys/types.h>

typedef struct NlPipe NlPipe;
typedef struct NlProc NlProc;

/* Starts android_path (a PIE executable) with argv/envp (NULL-terminated). cwd may be NULL. On failure returns
 * NULL with *err_no an errno value and err a message. */
NlProc *nl_spawn(const char *android_path, const char *const *argv, const char *const *envp, const char *cwd,
                 bool merge_stderr, int *err_no, char *err, size_t errlen);
/* 0 = the process's stdin (we write), 1 = stdout, 2 = stderr (we read; same as 1 when merged) */
NlPipe *nl_proc_pipe(NlProc *p, int which);
int nl_proc_wait(NlProc *p);
bool nl_proc_exit_value(NlProc *p, int *code);
void nl_proc_destroy(NlProc *p);
void nl_proc_release(NlProc *p);

ssize_t nl_pipe_read(NlPipe *p, void *out, size_t len);
ssize_t nl_pipe_write(NlPipe *p, const void *data, size_t len);
size_t nl_pipe_available(NlPipe *p);
void nl_pipe_close_read(NlPipe *p);
void nl_pipe_close_write(NlPipe *p);

/* The calling thread's process context, or NULL. */
NlProc *nl_proc_current(void);
FILE *nl_proc_stdio(int which);
NlPipe *nl_proc_fd_pipe(int fd);
const char *nl_proc_cwd(void);
/* Ends the calling thread's process as abort() would (exit status 134); returns when there is none. */
void nl_proc_abort(void);
/* pthread_create: when the creator runs in a process, the new thread joins it. */
bool nl_proc_wrap_thread(void *(**fn)(void *), void **arg);

#endif
