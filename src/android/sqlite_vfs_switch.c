/*
 * Switch SQLite VFS. The amalgamation is compiled SQLITE_OS_OTHER, so it does
 * not bring the unix VFS (mmap, fcntl locks, host headers). This file is the
 * file layer: POSIX read and write, which libnx routes through the sdmc
 * devoptab. Locks succeed inside one process. sqlite_switch_prepare installs
 * pthread mutexes before the first open, because OS_OTHER otherwise selects
 * the noop mutex and natives drop the GIL around step.
 *
 * The host build leaves this file empty. android_sqlite.c calls
 * sqlite_switch_prepare only under __SWITCH__.
 */
#ifdef __SWITCH__

#include "sqlite/sqlite3.h"
#include "../platform/platform.h"

#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

const char *platform_data_root(void);

/* ---- mutexes ------------------------------------------------------------------ */

typedef struct sqlite3_mutex SaMutex;
struct sqlite3_mutex {
    pthread_mutex_t mu;
    int id;
    int dynamic;
};

#define SA_STATIC_MUTEXES 14

static SaMutex g_static_mu[SA_STATIC_MUTEXES];
static int g_mu_ready;

static void mu_init_one(SaMutex *m, int id, int dynamic) {
    pthread_mutexattr_t attr;
    pthread_mutexattr_init(&attr);
    pthread_mutexattr_settype(&attr, PTHREAD_MUTEX_RECURSIVE);
    pthread_mutex_init(&m->mu, &attr);
    pthread_mutexattr_destroy(&attr);
    m->id = id;
    m->dynamic = dynamic;
}

static int mx_init(void) {
    if (g_mu_ready) return SQLITE_OK;
    for (int i = 0; i < SA_STATIC_MUTEXES; i++) mu_init_one(&g_static_mu[i], i, 0);
    g_mu_ready = 1;
    return SQLITE_OK;
}

static int mx_end(void) { return SQLITE_OK; }

static sqlite3_mutex *mx_alloc(int id) {
    if (id == SQLITE_MUTEX_FAST || id == SQLITE_MUTEX_RECURSIVE) {
        SaMutex *m = (SaMutex *)calloc(1, sizeof *m);
        if (!m) return NULL;
        mu_init_one(m, id, 1);
        return m;
    }
    if (id >= 0 && id < SA_STATIC_MUTEXES) return &g_static_mu[id];
    return &g_static_mu[SQLITE_MUTEX_STATIC_MAIN];
}

static void mx_free(sqlite3_mutex *m) {
    if (m && m->dynamic) {
        pthread_mutex_destroy(&m->mu);
        free(m);
    }
}

static void mx_enter(sqlite3_mutex *m) {
    if (m) pthread_mutex_lock(&m->mu);
}

static int mx_try(sqlite3_mutex *m) {
    if (!m) return SQLITE_OK;
    return pthread_mutex_trylock(&m->mu) == 0 ? SQLITE_OK : SQLITE_BUSY;
}

static void mx_leave(sqlite3_mutex *m) {
    if (m) pthread_mutex_unlock(&m->mu);
}

static int mx_held(sqlite3_mutex *m) {
    (void)m;
    return 1;
}

static const sqlite3_mutex_methods g_mutex_methods = {
    mx_init, mx_end, mx_alloc, mx_free, mx_enter, mx_try, mx_leave, mx_held, mx_held,
};

void sqlite_switch_prepare(void) {
    static int once;
    if (once) return;
    once = 1;
    sqlite3_config(SQLITE_CONFIG_MUTEX, &g_mutex_methods);
}

/* ---- files -------------------------------------------------------------------- */

typedef struct {
    sqlite3_file base;
    int fd;
    int delete_on_close;
    char *path;
} SaFile;

static int file_close(sqlite3_file *fp) {
    SaFile *f = (SaFile *)fp;
    if (f->fd >= 0) {
        close(f->fd);
        f->fd = -1;
    }
    if (f->delete_on_close && f->path) unlink(f->path);
    free(f->path);
    f->path = NULL;
    return SQLITE_OK;
}

static int seek_read(int fd, void *dst, int n, sqlite3_int64 offset) {
    unsigned char *p = (unsigned char *)dst;
    int got = 0;
    while (got < n) {
        if (lseek(fd, (off_t)(offset + got), SEEK_SET) < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        ssize_t r = read(fd, p + got, (size_t)(n - got));
        if (r < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (r == 0) break;
        got += (int)r;
    }
    return got;
}

static int seek_write(int fd, const void *src, int n, sqlite3_int64 offset) {
    const unsigned char *p = (const unsigned char *)src;
    int put = 0;
    while (put < n) {
        if (lseek(fd, (off_t)(offset + put), SEEK_SET) < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        ssize_t w = write(fd, p + put, (size_t)(n - put));
        if (w < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (w == 0) return -1;
        put += (int)w;
    }
    return put;
}

static int file_read(sqlite3_file *fp, void *buf, int n, sqlite3_int64 offset) {
    int got = seek_read(((SaFile *)fp)->fd, buf, n, offset);
    if (got < 0) return SQLITE_IOERR_READ;
    if (got < n) {
        memset((unsigned char *)buf + got, 0, (size_t)(n - got));
        return SQLITE_IOERR_SHORT_READ;
    }
    return SQLITE_OK;
}

static int file_write(sqlite3_file *fp, const void *buf, int n, sqlite3_int64 offset) {
    int put = seek_write(((SaFile *)fp)->fd, buf, n, offset);
    return put == n ? SQLITE_OK : SQLITE_IOERR_WRITE;
}

static int file_truncate(sqlite3_file *fp, sqlite3_int64 size) {
    if (ftruncate(((SaFile *)fp)->fd, (off_t)size) != 0) return SQLITE_IOERR_TRUNCATE;
    return SQLITE_OK;
}

static int file_sync(sqlite3_file *fp, int flags) {
    (void)flags;
    if (fsync(((SaFile *)fp)->fd) != 0) return SQLITE_IOERR_FSYNC;
    return SQLITE_OK;
}

static int file_size(sqlite3_file *fp, sqlite3_int64 *out) {
    struct stat st;
    if (fstat(((SaFile *)fp)->fd, &st) != 0) return SQLITE_IOERR_FSTAT;
    *out = (sqlite3_int64)st.st_size;
    return SQLITE_OK;
}

static int file_lock(sqlite3_file *fp, int level) {
    (void)fp;
    (void)level;
    return SQLITE_OK;
}

static int file_unlock(sqlite3_file *fp, int level) {
    (void)fp;
    (void)level;
    return SQLITE_OK;
}

static int file_check_reserved(sqlite3_file *fp, int *out) {
    (void)fp;
    *out = 0;
    return SQLITE_OK;
}

static int file_control(sqlite3_file *fp, int op, void *arg) {
    (void)fp;
    (void)op;
    (void)arg;
    return SQLITE_NOTFOUND;
}

static int file_sector_size(sqlite3_file *fp) {
    (void)fp;
    return 512;
}

static int file_device(sqlite3_file *fp) {
    (void)fp;
    return 0;
}

static const sqlite3_io_methods g_io = {
    1,
    file_close,
    file_read,
    file_write,
    file_truncate,
    file_sync,
    file_size,
    file_lock,
    file_unlock,
    file_check_reserved,
    file_control,
    file_sector_size,
    file_device,
};

static void parent_mkdir(const char *path) {
    char tmp[512];
    snprintf(tmp, sizeof tmp, "%s", path);
    size_t len = strlen(tmp);
    for (size_t i = 1; i < len; i++) {
        if (tmp[i] != '/' || tmp[i - 1] == ':') continue;
        tmp[i] = 0;
        mkdir(tmp, 0777);
        tmp[i] = '/';
    }
}

static int vfs_open(sqlite3_vfs *vfs, sqlite3_filename name, sqlite3_file *fp, int flags, int *out_flags) {
    (void)vfs;
    SaFile *f = (SaFile *)fp;
    memset(f, 0, sizeof *f);
    f->fd = -1;
    static int seq;
    char tmp_name[512];
    const char *path = name;
    if (!path || !path[0]) {
        int n = seq++;
        snprintf(tmp_name, sizeof tmp_name, "%s/sqlite-tmp-%d-%d", platform_data_root(), (int)getpid(), n);
        path = tmp_name;
        flags |= SQLITE_OPEN_DELETEONCLOSE;
    }
    int oflags = (flags & SQLITE_OPEN_READONLY) ? O_RDONLY : O_RDWR;
    if (flags & SQLITE_OPEN_CREATE) oflags |= O_CREAT;
    if ((flags & SQLITE_OPEN_EXCLUSIVE) && (flags & SQLITE_OPEN_CREATE)) oflags |= O_EXCL;
    if (flags & SQLITE_OPEN_CREATE) parent_mkdir(path);
    int fd = open(path, oflags, 0644);
    if (fd < 0) return SQLITE_CANTOPEN;
    f->fd = fd;
    f->delete_on_close = (flags & SQLITE_OPEN_DELETEONCLOSE) != 0;
    f->path = strdup(path);
    f->base.pMethods = &g_io;
    if (out_flags) {
        *out_flags = (flags & SQLITE_OPEN_READONLY) ? SQLITE_OPEN_READONLY : SQLITE_OPEN_READWRITE;
    }
    return SQLITE_OK;
}

static int vfs_delete(sqlite3_vfs *vfs, const char *name, int sync_dir) {
    (void)vfs;
    (void)sync_dir;
    if (!name) return SQLITE_OK;
    if (unlink(name) != 0 && errno != ENOENT) return SQLITE_IOERR_DELETE;
    return SQLITE_OK;
}

static int vfs_access(sqlite3_vfs *vfs, const char *name, int flags, int *out) {
    (void)vfs;
    int mode = F_OK;
    if (flags == SQLITE_ACCESS_READWRITE) mode = W_OK;
    else if (flags == SQLITE_ACCESS_READ) mode = R_OK;
    *out = name && access(name, mode) == 0;
    return SQLITE_OK;
}

static int vfs_full_path(sqlite3_vfs *vfs, const char *name, int n_out, char *out) {
    (void)vfs;
    if (!name || n_out <= 0) return SQLITE_CANTOPEN;
    if (name[0] == '/' || strchr(name, ':')) {
        if ((int)strlen(name) >= n_out) return SQLITE_CANTOPEN;
        memcpy(out, name, strlen(name) + 1);
        return SQLITE_OK;
    }
    char cwd[512];
    if (!getcwd(cwd, sizeof cwd)) return SQLITE_CANTOPEN;
    int n = snprintf(out, (size_t)n_out, "%s/%s", cwd, name);
    if (n < 0 || n >= n_out) return SQLITE_CANTOPEN;
    return SQLITE_OK;
}

static int vfs_random(sqlite3_vfs *vfs, int n, char *out) {
    (void)vfs;
    if (n <= 0) return 0;
    return platform_random_bytes(out, (size_t)n) ? n : 0;
}

static int vfs_sleep(sqlite3_vfs *vfs, int microseconds) {
    (void)vfs;
    usleep((useconds_t)(microseconds > 0 ? microseconds : 0));
    return microseconds;
}

static int vfs_time(sqlite3_vfs *vfs, double *out) {
    (void)vfs;
    *out = 2440587.5 + (double)time(NULL) / 86400.0;
    return SQLITE_OK;
}

static int vfs_last_error(sqlite3_vfs *vfs, int n, char *buf) {
    (void)vfs;
    (void)n;
    (void)buf;
    return 0;
}

static sqlite3_vfs g_vfs = {
    1,
    (int)sizeof(SaFile),
    512,
    NULL,
    "switch",
    NULL,
    vfs_open,
    vfs_delete,
    vfs_access,
    vfs_full_path,
    NULL,
    NULL,
    NULL,
    NULL,
    vfs_random,
    vfs_sleep,
    vfs_time,
    vfs_last_error,
    NULL,
};

int sqlite3_os_init(void) { return sqlite3_vfs_register(&g_vfs, 1); }

int sqlite3_os_end(void) { return SQLITE_OK; }

#endif /* __SWITCH__ */
