/* Link-time stand-in for bionic's libc.so: only its soname and symbol names matter (switchapk's shim provides
 * the real functions at load time). */
typedef unsigned long size_t;
struct FILE;
struct FILE *stdin, *stdout, *stderr;
void __libc_init(void *a, void *b, void *c, void *d) {}
void exit(int c) {}
int printf(const char *f, ...) { return 0; }
int fprintf(struct FILE *f, const char *fmt, ...) { return 0; }
char *fgets(char *s, int n, struct FILE *f) { return 0; }
int fputs(const char *s, struct FILE *f) { return 0; }
int fflush(struct FILE *f) { return 0; }
long write(int fd, const void *b, size_t n) { return 0; }
long read(int fd, void *b, size_t n) { return 0; }
int puts(const char *s) { return 0; }
struct FILE *fopen(const char *p, const char *m) { return 0; }
int fclose(struct FILE *f) { return 0; }
int pthread_create(void *t, const void *a, void *(*fn)(void *), void *arg) { return 0; }
int pthread_join(unsigned long t, void **r) { return 0; }
size_t strlen(const char *s) { return 0; }
int strcmp(const char *a, const char *b) { return 0; }
char *getenv(const char *n) { return 0; }
