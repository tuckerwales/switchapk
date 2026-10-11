/*
 * In-process "child processes" for bionic executables (ProcessBuilder.start, Runtime.exec).
 *
 * There is no fork on the Switch, so an executable (a PIE ELF such as a chess engine an app
 * ships as lib<name>.so) is loaded by the ELF loader into a fresh image and run on its own
 * thread. Its _start gets a kernel-style stack (argc, argv, envp, auxv) through
 * nl_enter_entry (spawn_entry.S); bionic's _start_main hands main to __libc_init, which this
 * shim provides: it calls main and exit. Each such thread, and every thread it creates, has a
 * process context (thread-local) that:
 *   - redirects stdin/stdout/stderr (bionic's stdin/stdout/stderr, __sF, fds 0-2, printf and
 *     friends) to in-memory pipes the Java Process streams read and write;
 *   - turns exit/_exit/abort into the end of the "process" (a longjmp back to its main thread's
 *     start, or the end of a secondary thread) instead of the end of switchapk;
 *   - resolves relative paths against the process's working directory;
 *   - answers getenv from the environment it was started with.
 * Limits: the image stays mapped after exit (no unloading), a destroyed process is only told
 * through its pipes (threads cannot be killed), environment changes are not isolated, and
 * signals are not delivered (setenv and environ still see switchapk's own environment).
 */
#define _GNU_SOURCE
#include "nativeloader.h"
#include "spawn.h"

#include <errno.h>
#include <pthread.h>
#include <setjmp.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define LOG_TAG "spawn"

/* ---- pipes ----------------------------------------------------------------------------------------------------- */

#define PIPE_CAP (64 * 1024)

struct NlPipe {
    pthread_mutex_t m;
    pthread_cond_t c;
    uint8_t *buf;
    size_t r, n;
    bool wclosed, rclosed;
};

static NlPipe *pipe_new(void) {
    NlPipe *p = sa_calloc(1, sizeof *p);
    pthread_mutex_init(&p->m, NULL);
    pthread_cond_init(&p->c, NULL);
    return p; /* the buffer comes with the first write and goes when the stream is done */
}

static void pipe_free(NlPipe *p) {
    if (!p) return;
    pthread_cond_destroy(&p->c);
    pthread_mutex_destroy(&p->m);
    free(p->buf);
    free(p);
}

/* Blocks until everything is written; -1 (EPIPE) once the reader is gone. */
ssize_t nl_pipe_write(NlPipe *p, const void *data, size_t len) {
    const uint8_t *d = data;
    size_t done = 0;
    pthread_mutex_lock(&p->m);
    if (!p->buf && !p->rclosed && len) p->buf = sa_malloc(PIPE_CAP);
    while (done < len) {
        while (p->n == PIPE_CAP && !p->rclosed) pthread_cond_wait(&p->c, &p->m);
        if (p->rclosed) {
            pthread_mutex_unlock(&p->m);
            if (done) return (ssize_t)done;
            errno = EPIPE;
            return -1;
        }
        size_t w = (p->r + p->n) % PIPE_CAP;
        size_t room = PIPE_CAP - p->n;
        size_t chunk = len - done < room ? len - done : room;
        if (chunk > PIPE_CAP - w) chunk = PIPE_CAP - w;
        memcpy(p->buf + w, d + done, chunk);
        p->n += chunk;
        done += chunk;
        pthread_cond_broadcast(&p->c);
    }
    pthread_mutex_unlock(&p->m);
    return (ssize_t)done;
}

/* Blocks until some data or end of stream (0). */
ssize_t nl_pipe_read(NlPipe *p, void *out, size_t len) {
    if (len == 0) return 0;
    pthread_mutex_lock(&p->m);
    while (p->n == 0 && !p->wclosed && !p->rclosed) pthread_cond_wait(&p->c, &p->m);
    size_t got = 0;
    while (got < len && p->n > 0) {
        size_t chunk = len - got < p->n ? len - got : p->n;
        if (chunk > PIPE_CAP - p->r) chunk = PIPE_CAP - p->r;
        memcpy((uint8_t *)out + got, p->buf + p->r, chunk);
        p->r = (p->r + chunk) % PIPE_CAP;
        p->n -= chunk;
        got += chunk;
    }
    if (got) pthread_cond_broadcast(&p->c);
    if (p->wclosed && p->n == 0 && p->buf) {
        free(p->buf);
        p->buf = NULL;
    }
    pthread_mutex_unlock(&p->m);
    return (ssize_t)got;
}

size_t nl_pipe_available(NlPipe *p) {
    pthread_mutex_lock(&p->m);
    size_t n = p->n;
    pthread_mutex_unlock(&p->m);
    return n;
}

void nl_pipe_close_write(NlPipe *p) {
    pthread_mutex_lock(&p->m);
    p->wclosed = true;
    pthread_cond_broadcast(&p->c);
    pthread_mutex_unlock(&p->m);
}

void nl_pipe_close_read(NlPipe *p) {
    pthread_mutex_lock(&p->m);
    p->rclosed = true;
    p->n = 0;
    free(p->buf);
    p->buf = NULL;
    pthread_cond_broadcast(&p->c);
    pthread_mutex_unlock(&p->m);
}

/* ---- processes ------------------------------------------------------------------------------------------------- */

struct NlProc {
    NlPipe *in, *out, *err; /* err == out when stderr is merged */
    FILE *fin, *fout, *ferr;
    pthread_t main;
    void *entry;
    char **argv;
    int argc;
    char **envp;
    int envc;
    char *cwd; /* host path, or NULL */
    jmp_buf jb;
    pthread_mutex_t m;
    pthread_cond_t c;
    bool exited;
    int exit_code;
    int refs; /* the Java handle and the process itself */
};

static __thread NlProc *t_proc;

NlProc *nl_proc_current(void) { return t_proc; }

static ssize_t cookie_read(void *c, char *buf, size_t n) { return nl_pipe_read((NlPipe *)c, buf, n); }
static ssize_t cookie_write(void *c, const char *buf, size_t n) { return nl_pipe_write((NlPipe *)c, buf, n); }

static FILE *pipe_file(NlPipe *p, bool reading) {
    cookie_io_functions_t io = {0};
    if (reading) io.read = cookie_read;
    else io.write = cookie_write;
    FILE *f = fopencookie(p, reading ? "r" : "w", io);
    if (f && !reading) setvbuf(f, NULL, _IOLBF, BUFSIZ);
    return f;
}

FILE *nl_proc_stdio(int which) {
    NlProc *p = t_proc;
    if (!p) return NULL;
    return which == 0 ? p->fin : which == 1 ? p->fout : p->ferr;
}

NlPipe *nl_proc_fd_pipe(int fd) {
    NlProc *p = t_proc;
    if (!p || fd < 0 || fd > 2) return NULL;
    return fd == 0 ? p->in : fd == 1 ? p->out : p->err;
}

const char *nl_proc_cwd(void) { return t_proc ? t_proc->cwd : NULL; }

static void proc_release(NlProc *p) {
    pthread_mutex_lock(&p->m);
    int left = --p->refs;
    pthread_mutex_unlock(&p->m);
    if (left > 0) return;
    if (p->fin) fclose(p->fin);
    if (p->fout) fclose(p->fout);
    if (p->ferr) fclose(p->ferr);
    pipe_free(p->in);
    if (p->err != p->out) pipe_free(p->err);
    pipe_free(p->out);
    for (int i = 0; i < p->argc; i++) free(p->argv[i]);
    for (int i = 0; i < p->envc; i++) free(p->envp[i]);
    free(p->argv);
    free(p->envp);
    free(p->cwd);
    pthread_cond_destroy(&p->c);
    pthread_mutex_destroy(&p->m);
    free(p);
}

/* The process is over: flush its output, end its streams, wake waiters. Idempotent. */
static void proc_finish(NlProc *p, int code, bool flush) {
    pthread_mutex_lock(&p->m);
    bool first = !p->exited;
    if (first) {
        p->exited = true;
        p->exit_code = code & 0xff;
    }
    pthread_mutex_unlock(&p->m);
    if (!first) return;
    if (flush) {
        if (p->fout) fflush(p->fout);
        if (p->ferr) fflush(p->ferr);
    }
    nl_pipe_close_write(p->out);
    nl_pipe_close_write(p->err);
    nl_pipe_close_read(p->in);
    pthread_mutex_lock(&p->m);
    pthread_cond_broadcast(&p->c);
    pthread_mutex_unlock(&p->m);
}

/* exit/_exit/abort from code running in a process context. */
static _Noreturn void proc_exit(NlProc *p, int code, bool flush) {
    proc_finish(p, code, flush);
    if (pthread_equal(pthread_self(), p->main)) longjmp(p->jb, 1);
    t_proc = NULL;
    pthread_exit(NULL);
}

void nl_enter_entry(void *entry, void *sp); /* spawn_entry.S */

static void *proc_main(void *arg) {
    NlProc *p = arg;
    t_proc = p;
    if (setjmp(p->jb) == 0) {
        /* argc, argv..., NULL, envp..., NULL, AT_NULL auxv pair: the kernel's initial stack */
        size_t words = 1 + (size_t)p->argc + 1 + (size_t)p->envc + 1 + 2;
        uintptr_t *raw = __builtin_alloca((words + 2) * sizeof(uintptr_t));
        raw = (uintptr_t *)(((uintptr_t)raw + 15) & ~(uintptr_t)15);
        size_t k = 0;
        raw[k++] = (uintptr_t)p->argc;
        for (int i = 0; i < p->argc; i++) raw[k++] = (uintptr_t)p->argv[i];
        raw[k++] = 0;
        for (int i = 0; i < p->envc; i++) raw[k++] = (uintptr_t)p->envp[i];
        raw[k++] = 0;
        raw[k++] = 0; /* AT_NULL */
        raw[k++] = 0;
        nl_enter_entry(p->entry, raw);
        /* not reached: __libc_init exits */
    }
    t_proc = NULL;
    proc_release(p);
    return NULL;
}

static char **copy_strings(const char *const *src, int *count) {
    int n = 0;
    while (src && src[n]) n++;
    char **out = sa_calloc((size_t)n + 1, sizeof(char *));
    for (int i = 0; i < n; i++) out[i] = sa_strdup(src[i]);
    *count = n;
    return out;
}

NlProc *nl_spawn(const char *android_path, const char *const *argv, const char *const *envp, const char *cwd,
                 bool merge_stderr, int *err_no, char *err, size_t errlen) {
    char *host = platform_map_path(android_path);
    FILE *f = host ? fopen(host, "rb") : NULL;
    uint8_t hdr[20] = {0};
    size_t got = f ? fread(hdr, 1, sizeof hdr, f) : 0;
    if (f) fclose(f);
    free(host);
    if (!f) {
        *err_no = ENOENT;
        snprintf(err, errlen, "No such file or directory");
        return NULL;
    }
    /* an ELF shared object (PIE executable) for this machine */
    if (got < sizeof hdr || memcmp(hdr, "\x7f" "ELF", 4) != 0 || hdr[16] != 3) {
        *err_no = ENOEXEC;
        snprintf(err, errlen, "Exec format error");
        return NULL;
    }
    void *entry = loader_load_executable(android_path, err, errlen);
    if (!entry) {
        *err_no = ENOEXEC;
        return NULL;
    }
    NlProc *p = sa_calloc(1, sizeof *p);
    pthread_mutex_init(&p->m, NULL);
    pthread_cond_init(&p->c, NULL);
    p->in = pipe_new();
    p->out = pipe_new();
    p->err = merge_stderr ? p->out : pipe_new();
    p->fin = pipe_file(p->in, true);
    p->fout = pipe_file(p->out, false);
    p->ferr = pipe_file(p->err, false); /* its own FILE even when merged into the stdout pipe */
    if (p->ferr) setvbuf(p->ferr, NULL, _IONBF, 0);
    p->argv = copy_strings(argv, &p->argc);
    p->envp = copy_strings(envp, &p->envc);
    p->cwd = cwd ? platform_map_path(cwd) : NULL;
    p->entry = entry;
    p->refs = 2;
    pthread_attr_t a;
    pthread_attr_init(&a);
    pthread_attr_setstacksize(&a, 8 * 1024 * 1024);
    pthread_attr_setdetachstate(&a, PTHREAD_CREATE_DETACHED);
    int r = pthread_create(&p->main, &a, proc_main, p);
    pthread_attr_destroy(&a);
    if (r != 0) {
        *err_no = r;
        snprintf(err, errlen, "%s", strerror(r));
        p->refs = 1;
        proc_release(p);
        return NULL;
    }
    LOGI("started %s (%d args)", android_path, p->argc);
    return p;
}

NlPipe *nl_proc_pipe(NlProc *p, int which) { return which == 0 ? p->in : which == 1 ? p->out : p->err; }

int nl_proc_wait(NlProc *p) {
    pthread_mutex_lock(&p->m);
    while (!p->exited) pthread_cond_wait(&p->c, &p->m);
    int code = p->exit_code;
    pthread_mutex_unlock(&p->m);
    return code;
}

bool nl_proc_exit_value(NlProc *p, int *code) {
    pthread_mutex_lock(&p->m);
    bool done = p->exited;
    if (done) *code = p->exit_code;
    pthread_mutex_unlock(&p->m);
    return done;
}

void nl_proc_destroy(NlProc *p) {
    /* threads cannot be killed: end its streams so reads see EOF and writes fail, as after SIGKILL */
    nl_pipe_close_write(p->in);
    nl_pipe_close_read(p->out);
    nl_pipe_close_read(p->err);
}

void nl_proc_release(NlProc *p) { proc_release(p); }

/* ---- thread inheritance -------------------------------------------------------------------------------------- */

typedef struct {
    void *(*fn)(void *);
    void *arg;
    NlProc *proc;
} ThreadStart;

static void *thread_trampoline(void *a) {
    ThreadStart s = *(ThreadStart *)a;
    free(a);
    t_proc = s.proc;
    void *r = s.fn(s.arg);
    t_proc = NULL;
    return r;
}

bool nl_proc_wrap_thread(void *(**fn)(void *), void **arg) {
    NlProc *p = t_proc;
    if (!p) return false;
    ThreadStart *s = sa_malloc(sizeof *s);
    s->fn = *fn;
    s->arg = *arg;
    s->proc = p;
    *fn = thread_trampoline;
    *arg = s;
    return true;
}

/* ---- shim entries (joined last: they wrap the entries the other tables provide) ---------------------------- */

static ssize_t (*prev_read)(int, void *, size_t);
static ssize_t (*prev_write)(int, const void *, size_t);
static int (*prev_isatty)(int);

static void sp_exit(int code);

/* bionic's __libc_init for dynamic executables: constructors already ran when the loader mapped the image */
typedef struct {
    void (**preinit_array)(void);
    void (**init_array)(void);
    void (**fini_array)(void);
} BionicStructors;

static void sp_libc_init(void *raw_args, void (*onexit)(void), int (*slingshot)(int, char **, char **),
                         const BionicStructors *structors) {
    SA_UNUSED(onexit);
    SA_UNUSED(structors);
    uintptr_t *raw = raw_args;
    int argc = (int)raw[0];
    char **argv = (char **)(raw + 1);
    char **envp = argv + argc + 1;
    sp_exit(slingshot(argc, argv, envp));
}

static void sp_exit(int code) {
    NlProc *p = t_proc;
    if (p) proc_exit(p, code, true);
    exit(code);
}

static void sp__exit(int code) {
    NlProc *p = t_proc;
    if (p) proc_exit(p, code, false);
    _exit(code);
}

static void sp_abort(void) {
    nl_proc_abort(); /* status 134, as a shell reports SIGABRT */
    abort();
}

void nl_proc_abort(void) {
    NlProc *p = t_proc;
    if (p) {
        LOGW("process aborted");
        proc_exit(p, 128 + 6, true);
    }
}

static ssize_t sp_read(int fd, void *buf, size_t n) {
    NlPipe *pp = nl_proc_fd_pipe(fd);
    if (pp && fd == 0) return nl_pipe_read(pp, buf, n);
    return prev_read(fd, buf, n);
}

static ssize_t sp_write(int fd, const void *buf, size_t n) {
    NlPipe *pp = nl_proc_fd_pipe(fd);
    if (pp && fd > 0) {
        /* keep order with anything already buffered in the FILE */
        FILE *f = nl_proc_stdio(fd);
        if (f) fflush(f);
        return nl_pipe_write(pp, buf, n);
    }
    return prev_write(fd, buf, n);
}

/* a process sees the environment it was started with */
static char *sp_getenv(const char *name) {
    NlProc *p = t_proc;
    if (!p) return getenv(name);
    size_t n = strlen(name);
    for (int i = 0; i < p->envc; i++)
        if (strncmp(p->envp[i], name, n) == 0 && p->envp[i][n] == '=') return p->envp[i] + n + 1;
    return NULL;
}

static int sp_isatty(int fd) {
    if (nl_proc_fd_pipe(fd)) return 0;
    return prev_isatty(fd);
}

static FILE *out_file(void) {
    FILE *f = nl_proc_stdio(1);
    return f ? f : stdout;
}

static FILE *in_file(void) {
    FILE *f = nl_proc_stdio(0);
    return f ? f : stdin;
}

static int sp_vprintf(const char *fmt, va_list ap) { return vfprintf(out_file(), fmt, ap); }

static int sp_printf(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int r = vfprintf(out_file(), fmt, ap);
    va_end(ap);
    return r;
}

static int sp_puts(const char *s) {
    FILE *f = out_file();
    if (fputs(s, f) < 0) return EOF;
    return fputc('\n', f) == EOF ? EOF : 1;
}

static int sp_putchar(int c) { return fputc(c, out_file()); }
static int sp_getchar(void) { return fgetc(in_file()); }

static int sp_vscanf(const char *fmt, va_list ap) { return vfscanf(in_file(), fmt, ap); }

static int sp_scanf(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int r = vfscanf(in_file(), fmt, ap);
    va_end(ap);
    return r;
}

static void sp_perror(const char *s) {
    FILE *f = nl_proc_stdio(2);
    if (!f) f = stderr;
    if (s && *s) fprintf(f, "%s: %s\n", s, strerror(errno));
    else fprintf(f, "%s\n", strerror(errno));
}

void shim_spawn_bind(void *(*prev)(const char *name)) {
    prev_read = (ssize_t(*)(int, void *, size_t))prev("read");
    prev_write = (ssize_t(*)(int, const void *, size_t))prev("write");
    prev_isatty = (int (*)(int))prev("isatty");
    if (!prev_read) prev_read = read;
    if (!prev_write) prev_write = write;
    if (!prev_isatty) prev_isatty = isatty;
}

#define W(name, fn) {#name, (void *)fn}
static const ShimSym g_syms[] = {
    W(__libc_init, sp_libc_init), W(exit, sp_exit), W(_exit, sp__exit), W(_Exit, sp__exit), W(abort, sp_abort),
    W(read, sp_read), W(write, sp_write), W(isatty, sp_isatty), W(printf, sp_printf), W(vprintf, sp_vprintf),
    W(puts, sp_puts), W(putchar, sp_putchar), W(getchar, sp_getchar), W(scanf, sp_scanf), W(vscanf, sp_vscanf),
    W(perror, sp_perror), W(getenv, sp_getenv),
};

const ShimSym *shim_spawn_symbols(size_t *n) {
    *n = SA_ARRAY_LEN(g_syms);
    return g_syms;
}
