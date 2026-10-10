/*
 * libcore.io.Os natives: file system access for java.io / java.nio.
 * Android paths are translated to the platform file system by platform_map_path().
 */
#include "natives.h"

#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <dirent.h>
#include <unistd.h>
#include <time.h>
/* devkitA64's newlib declares statvfs; libnx implements it for sdmc: through its fs devoptab. */
#include <sys/statvfs.h>
#ifndef __SWITCH__
#include <utime.h>
#endif

#define LOG_TAG "io"

/* ---- path translation ---------------------------------------------------------- */

static char g_data_root[512] = "./data";
static char g_package[256] = "app";

void platform_set_data_root(const char *root, const char *package) {
    snprintf(g_data_root, sizeof g_data_root, "%s", root);
    if (package) snprintf(g_package, sizeof g_package, "%s", package);
}

const char *platform_data_root(void) { return g_data_root; }

static bool starts_with(const char *s, const char *p) { return strncmp(s, p, strlen(p)) == 0; }

char *platform_map_path(const char *p) {
    if (!p) return NULL;
    const char *rest;
    if (starts_with(p, "/data/data/") || starts_with(p, "/data/user/0/")) {
        rest = p + (starts_with(p, "/data/data/") ? 11 : 13);
        return sa_sprintf("%s/apps/%s", g_data_root, rest);
    }
    if (starts_with(p, "/sdcard") || starts_with(p, "/storage/emulated/0") || starts_with(p, "/mnt/sdcard") ||
        starts_with(p, "/storage/self/primary")) {
        rest = strchr(p + 1, '/');
        if (starts_with(p, "/storage/emulated/0")) rest = p + 19;
        else if (starts_with(p, "/storage/self/primary")) rest = p + 21;
        else if (starts_with(p, "/mnt/sdcard")) rest = p + 11;
        else rest = p + 7;
        return sa_sprintf("%s/sdcard%s", g_data_root, rest ? rest : "");
    }
    if (starts_with(p, "/data/local/tmp")) return sa_sprintf("%s/tmp%s", g_data_root, p + 15);
#ifdef __SWITCH__
    if (p[0] == '/') return sa_sprintf("sdmc:%s", p);
#endif
    return sa_strdup(p);
}

/* ---- helpers ---------------------------------------------------------------------- */

static void throw_io(VMThread *t, const char *what, int err) {
    vm_throw_new(t, "Ljava/io/IOException;", "%s: %s", what, strerror(err));
}

static char *mapped_arg(VMThread *t, Object *s) {
    if (!s) {
        vm_throw_npe(t, "path");
        return NULL;
    }
    char *u = vm_string_to_utf8(s);
    char *m = platform_map_path(u);
    free(u);
    return m;
}

static void ensure_parent_dirs(const char *path) {
    char *d = sa_strdup(path);
    char *slash = strrchr(d, '/');
    if (slash && slash != d) {
        *slash = 0;
        sa_mkdirs(d);
    }
    free(d);
}

/* ---- natives ------------------------------------------------------------------------- */

NATIVE(Os_open) {
    UNUSED_ARGS();
    Object *jpath = A_OBJ(0);
    int32_t jflags = A_INT(1), mode = A_INT(2);
    char *path = mapped_arg(t, jpath);
    if (!path) return;
    int flags = 0;
    switch (jflags & 3) {
    case 0: flags = O_RDONLY; break;
    case 1: flags = O_WRONLY; break;
    default: flags = O_RDWR; break;
    }
    if (jflags & 0x40) flags |= O_CREAT;
    if (jflags & 0x200) flags |= O_TRUNC;
    if (jflags & 0x400) flags |= O_APPEND;
    /* Android apps expect their private data directories to exist */
    if (flags & O_CREAT) ensure_parent_dirs(path);
    int fd = open(path, flags, mode ? mode : 0666);
    if (fd < 0) {
        int e = errno;
        char *orig = vm_string_to_utf8(jpath);
        LOGD("open(%s -> %s) failed: %s", orig, path, strerror(e));
        vm_throw_new(t, "Ljava/io/FileNotFoundException;", "%s: open failed: %s (%s)", orig,
                     e == ENOENT ? "ENOENT" : e == EACCES ? "EACCES" : "EIO", strerror(e));
        free(orig);
        free(path);
        return;
    }
    free(path);
    R_INT(fd);
}

bool g_raw_stdio;

static void write_std(int fd, const uint8_t *b, size_t n) {
    if (g_raw_stdio) {
        fwrite(b, 1, n, fd == 2 ? stderr : stdout);
        fflush(fd == 2 ? stderr : stdout);
        return;
    }
    /* stdout/stderr go to the log, line buffered per stream */
    static SaBuf lines[3];
    SaBuf *lb = &lines[fd];
    for (size_t i = 0; i < n; i++) {
        if (b[i] == '\n') {
            sa_log(fd == 2 ? SA_LOG_WARN : SA_LOG_INFO, fd == 2 ? "System.err" : "System.out", "%s", sa_buf_cstr(lb));
            lb->len = 0;
        } else {
            sa_buf_putc(lb, (char)b[i]);
        }
    }
}

NATIVE(Os_read) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    ArrayObject *b = A_ARR(1);
    int32_t off = A_INT(2), len = A_INT(3);
    if (!b) {
        vm_throw_npe(t, "buffer");
        return;
    }
    if (off < 0 || len < 0 || (int64_t)off + len > b->length) {
        vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;", "off=%d len=%d length=%d", off, len, b->length);
        return;
    }
    if (fd == 0) {
        R_INT(-1); /* no console input */
        return;
    }
    uint8_t *dst = ARRAY_DATA(b, uint8_t) + off;
    ssize_t n;
    /* regular file reads are fast; don't release the GIL for small reads */
    if (len > 65536) {
        VM_BLOCKING_BEGIN(t);
        n = read(fd, dst, (size_t)len);
        VM_BLOCKING_END(t);
    } else {
        n = read(fd, dst, (size_t)len);
    }
    if (n < 0) {
        throw_io(t, "read", errno);
        return;
    }
    R_INT(n == 0 ? -1 : (int32_t)n);
}

NATIVE(Os_write) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    ArrayObject *b = A_ARR(1);
    int32_t off = A_INT(2), len = A_INT(3);
    if (!b) {
        vm_throw_npe(t, "buffer");
        return;
    }
    if (off < 0 || len < 0 || (int64_t)off + len > b->length) {
        vm_throw_new(t, "Ljava/lang/ArrayIndexOutOfBoundsException;", "off=%d len=%d length=%d", off, len, b->length);
        return;
    }
    const uint8_t *src = ARRAY_DATA(b, uint8_t) + off;
    if (fd == 1 || fd == 2) {
        write_std(fd, src, (size_t)len);
        return;
    }
    while (len > 0) {
        ssize_t n = write(fd, src, (size_t)len);
        if (n < 0) {
            if (errno == EINTR) continue;
            throw_io(t, "write", errno);
            return;
        }
        src += n;
        len -= (int32_t)n;
    }
}

NATIVE(Os_close) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    if (fd > 2) close(fd);
}

NATIVE(Os_available) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    if (fd <= 2) {
        R_INT(0);
        return;
    }
    off_t cur = lseek(fd, 0, SEEK_CUR);
    struct stat st;
    if (cur < 0 || fstat(fd, &st) != 0 || !S_ISREG(st.st_mode)) {
        R_INT(0);
        return;
    }
    off_t avail = st.st_size - cur;
    R_INT(avail > INT32_MAX ? INT32_MAX : (avail < 0 ? 0 : (int32_t)avail));
}

NATIVE(Os_seek) {
    UNUSED_ARGS();
    int fd = A_INT(0);
    int64_t off = A_LONG(1);
    int whence = A_INT(3);
    off_t r = lseek(fd, (off_t)off, whence == 0 ? SEEK_SET : whence == 1 ? SEEK_CUR : SEEK_END);
    if (r < 0) {
        throw_io(t, "lseek", errno);
        return;
    }
    R_LONG(r);
}

NATIVE(Os_fsync) {
    UNUSED_ARGS();
    fsync(A_INT(0));
}

NATIVE(Os_ftruncate) {
    UNUSED_ARGS();
    if (ftruncate(A_INT(0), (off_t)A_LONG(1)) != 0) throw_io(t, "ftruncate", errno);
}

NATIVE(Os_stat) {
    UNUSED_ARGS();
    char *path = mapped_arg(t, A_OBJ(0));
    if (!path) return;
    ArrayObject *r = vm_alloc_prim_array(t, 'J', 7);
    if (!r) {
        free(path);
        return;
    }
    struct stat st;
    int64_t *d = ARRAY_DATA(r, int64_t);
    if (stat(path, &st) == 0) {
        d[0] = 1;
        d[1] = S_ISDIR(st.st_mode) ? 1 : 0;
        d[2] = S_ISREG(st.st_mode) ? 1 : 0;
        d[3] = S_ISREG(st.st_mode) ? (int64_t)st.st_size : 0;
        d[4] = (int64_t)st.st_mtime * 1000;
        d[5] = 1;
        d[6] = 1;
    }
    free(path);
    R_OBJ(r);
}

NATIVE(Os_list) {
    UNUSED_ARGS();
    char *path = mapped_arg(t, A_OBJ(0));
    if (!path) return;
    DIR *dir = opendir(path);
    free(path);
    if (!dir) {
        R_OBJ(NULL);
        return;
    }
    SaVec names = {0};
    struct dirent *de;
    while ((de = readdir(dir)) != NULL) {
        if (!strcmp(de->d_name, ".") || !strcmp(de->d_name, "..")) continue;
        sa_vec_push(&names, sa_strdup(de->d_name));
    }
    closedir(dir);
    ArrayObject *arr = vm_alloc_array(t, g_vm.wk.arr_String, (int32_t)names.len);
    for (size_t i = 0; arr && i < names.len; i++) {
        Object *s = vm_new_string_utf8(t, names.items[i]);
        ARRAY_DATA(arr, Object *)[i] = s;
    }
    for (size_t i = 0; i < names.len; i++) free(names.items[i]);
    sa_vec_free(&names);
    R_OBJ(arr);
}

NATIVE(Os_mkdir) {
    UNUSED_ARGS();
    char *path = mapped_arg(t, A_OBJ(0));
    if (!path) return;
    /* create missing intermediate host directories that map to Android's always-present dirs */
    ensure_parent_dirs(path);
    bool ok = mkdir(path, 0777) == 0;
    free(path);
    R_BOOL(ok);
}

NATIVE(Os_remove) {
    UNUSED_ARGS();
    char *path = mapped_arg(t, A_OBJ(0));
    if (!path) return;
    bool ok = unlink(path) == 0 || rmdir(path) == 0;
    free(path);
    R_BOOL(ok);
}

NATIVE(Os_rename) {
    UNUSED_ARGS();
    char *a = mapped_arg(t, A_OBJ(0));
    char *b = a ? mapped_arg(t, A_OBJ(1)) : NULL;
    bool ok = a && b && rename(a, b) == 0;
    free(a);
    free(b);
    R_BOOL(ok);
}

NATIVE(Os_setLastModified) {
    UNUSED_ARGS();
    char *path = mapped_arg(t, A_OBJ(0));
    if (!path) return;
#ifdef __SWITCH__
    bool ok = sa_file_exists(path);
#else
    struct utimbuf ub;
    ub.actime = ub.modtime = (time_t)(A_LONG(1) / 1000);
    bool ok = utime(path, &ub) == 0;
#endif
    free(path);
    R_BOOL(ok);
}

NATIVE(Os_hostPath) {
    UNUSED_ARGS();
    char *path = mapped_arg(t, A_OBJ(0));
    if (!path) return;
    R_OBJ(vm_new_string_utf8(t, path));
    free(path);
}

/* static long[] Os.statvfs(String path): {fragment size, blocks, free blocks, available blocks}, or null. */
NATIVE(Os_statvfs) {
    char *path = mapped_arg(t, A_OBJ(0));
    if (!path) return;
    struct statvfs sv;
    int rc = statvfs(path, &sv);
    free(path);
    if (rc != 0) {
        R_OBJ(NULL);
        return;
    }
    ArrayObject *r = vm_alloc_prim_array(t, 'J', 4);
    if (!r) return;
    int64_t *v = ARRAY_DATA(r, int64_t);
    v[0] = (int64_t)(sv.f_frsize ? sv.f_frsize : sv.f_bsize);
    v[1] = (int64_t)sv.f_blocks;
    v[2] = (int64_t)sv.f_bfree;
    v[3] = (int64_t)sv.f_bavail;
    R_OBJ(r);
}

NATIVE(Os_freeSpace) {
    char *path = mapped_arg(t, A_OBJ(0));
    if (!path) return;
    struct statvfs sv;
    int64_t v = 0;
    if (statvfs(path, &sv) == 0) v = (int64_t)sv.f_bavail * (int64_t)(sv.f_frsize ? sv.f_frsize : sv.f_bsize);
    free(path);
    R_LONG(v);
}

static const NativeMethodReg g_regs[] = {
    {"Llibcore/io/Os;", "open", "(Ljava/lang/String;II)I", Os_open},
    {"Llibcore/io/Os;", "read", "(I[BII)I", Os_read},
    {"Llibcore/io/Os;", "write", "(I[BII)V", Os_write},
    {"Llibcore/io/Os;", "close", "(I)V", Os_close},
    {"Llibcore/io/Os;", "available", "(I)I", Os_available},
    {"Llibcore/io/Os;", "seek", "(IJI)J", Os_seek},
    {"Llibcore/io/Os;", "fsync", "(I)V", Os_fsync},
    {"Llibcore/io/Os;", "ftruncate", "(IJ)V", Os_ftruncate},
    {"Llibcore/io/Os;", "stat", "(Ljava/lang/String;)[J", Os_stat},
    {"Llibcore/io/Os;", "list", "(Ljava/lang/String;)[Ljava/lang/String;", Os_list},
    {"Llibcore/io/Os;", "mkdir", "(Ljava/lang/String;)Z", Os_mkdir},
    {"Llibcore/io/Os;", "remove", "(Ljava/lang/String;)Z", Os_remove},
    {"Llibcore/io/Os;", "rename", "(Ljava/lang/String;Ljava/lang/String;)Z", Os_rename},
    {"Llibcore/io/Os;", "setLastModified", "(Ljava/lang/String;J)Z", Os_setLastModified},
    {"Llibcore/io/Os;", "hostPath", "(Ljava/lang/String;)Ljava/lang/String;", Os_hostPath},
    {"Llibcore/io/Os;", "freeSpace", "(Ljava/lang/String;)J", Os_freeSpace},
    {"Llibcore/io/Os;", "statvfs", "(Ljava/lang/String;)[J", Os_statvfs},
};

void natives_java_io_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
