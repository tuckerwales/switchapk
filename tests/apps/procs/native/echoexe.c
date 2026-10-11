/*
 * A bionic PIE executable for tests/apps/procs, started by ProcessBuilder: echoes stdin
 * upper-cased, reports its argv, environment and working directory, writes from a second
 * thread and to stderr, and ends through return, exit() or exit() from a thread.
 * Built without the NDK: _start below does what crtbegin_dynamic.o does.
 */
typedef unsigned long size_t;
typedef struct FILE FILE;
extern FILE *stdin, *stdout, *stderr;
void __libc_init(void *raw_args, void (*onexit)(void), int (*slingshot)(int, char **, char **), void *structors);
void exit(int code) __attribute__((noreturn));
int printf(const char *fmt, ...);
int fprintf(FILE *f, const char *fmt, ...);
char *fgets(char *s, int n, FILE *f);
int fputs(const char *s, FILE *f);
int fflush(FILE *f);
long write(int fd, const void *buf, size_t n);
int puts(const char *s);
FILE *fopen(const char *path, const char *mode);
int fclose(FILE *f);
int pthread_create(unsigned long *t, const void *attr, void *(*fn)(void *), void *arg);
int pthread_join(unsigned long t, void **ret);
size_t strlen(const char *s);
int strcmp(const char *a, const char *b);
char *getenv(const char *name);

int main(int argc, char **argv, char **envp);

__attribute__((used)) static void _start_main(void *raw_args) { __libc_init(raw_args, 0, main, 0); }

#if defined(__x86_64__)
__asm__(".text\n.globl _start\n_start:\n  mov %rsp, %rdi\n  and $~0xf, %rsp\n  call _start_main\n  hlt\n");
#elif defined(__aarch64__)
__asm__(".text\n.globl _start\n_start:\n  mov x0, sp\n  b _start_main\n");
#endif

static int g_counter = 10; /* a fresh image per start: always 10 at entry */

static void *worker(void *arg) {
    const char *msg = arg;
    write(1, msg, strlen(msg));
    return 0;
}

static void *exiter(void *arg) {
    fflush(stdout);
    exit(9);
}

int main(int argc, char **argv, char **envp) {
    printf("argc=%d argv1=%s counter=%d env=%s\n", argc, argc > 1 ? argv[1] : "-", g_counter, getenv("PROCS_VAR"));
    g_counter++;
    fflush(stdout);
    if (argc > 1 && strcmp(argv[1], "thread-exit") == 0) {
        unsigned long t;
        pthread_create(&t, 0, exiter, 0);
        pthread_join(t, 0);
        return 1; /* not reached: the thread's exit ends the process */
    }
    char line[256];
    while (fgets(line, sizeof line, stdin)) {
        if (strcmp(line, "quit\n") == 0) break;
        for (char *p = line; *p; p++)
            if (*p >= 'a' && *p <= 'z') *p -= 32;
        fputs(line, stdout);
        fflush(stdout);
    }
    unsigned long t;
    pthread_create(&t, 0, worker, "from thread\n");
    pthread_join(t, 0);
    fprintf(stderr, "to stderr\n");
    FILE *f = fopen("rel.txt", "w");
    if (f) {
        fputs("relative", f);
        fclose(f);
        puts("wrote rel.txt");
    }
    fflush(stdout);
    if (argc > 1 && strcmp(argv[1], "exit") == 0) exit(5);
    return 3;
}
