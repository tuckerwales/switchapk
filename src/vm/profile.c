/*
 * Sampling profiler for Java code (SWITCHAPK_PROFILE=<file>).
 *
 * A timer thread asks for a sample about 1000 times a second by raising
 * safepoint_requested; the thread holding the GIL records its own Java stack
 * at its next safepoint (a backward branch or an invoke), so no thread ever
 * walks another thread's frames. Time a native spends holding the GIL is
 * charged to the Java method that called it. The report (self and inclusive
 * sample counts per method) is written at exit.
 */
#include "vm.h"

#include <pthread.h>

#define LOG_TAG "profile"

#define PROFILE_SLOTS (1u << 14) /* open addressing; methods beyond this are counted as dropped */
#define PROFILE_DEPTH 256

typedef struct {
    Method *m;
    uint32_t self, total;
} ProfileEntry;

static ProfileEntry *g_entries;
static const char *g_path;
static uint64_t g_samples, g_dropped;
static volatile bool g_stopped;

static ProfileEntry *entry_for(Method *m) {
    uint32_t h = (uint32_t)(((uintptr_t)m >> 4) * 2654435761u) & (PROFILE_SLOTS - 1);
    for (uint32_t i = 0; i < PROFILE_SLOTS; i++) {
        ProfileEntry *e = &g_entries[(h + i) & (PROFILE_SLOTS - 1)];
        if (e->m == m) return e;
        if (!e->m) {
            e->m = m;
            return e;
        }
    }
    return NULL;
}

void vm_profile_sample(VMThread *t) {
    g_vm.profile_pending = false;
    if (g_stopped || !t->frame) return;
    g_samples++;
    Method *seen[PROFILE_DEPTH];
    int nseen = 0;
    bool top = true;
    for (Frame *f = t->frame; f && nseen < PROFILE_DEPTH; f = f->prev) {
        Method *m = f->method;
        if (!m) continue;
        bool dup = false;
        for (int i = 0; i < nseen && !dup; i++) dup = seen[i] == m;
        ProfileEntry *e = entry_for(m);
        if (!e) {
            g_dropped++;
            break;
        }
        if (top) e->self++;
        top = false;
        if (!dup) {
            e->total++;
            seen[nseen++] = m;
        }
    }
}

static void *timer_thread(void *arg) {
    (void)arg;
    while (!g_stopped) {
        sa_sleep_ns(1000000);
        g_vm.profile_pending = true;
        g_vm.safepoint_requested = true;
    }
    return NULL;
}

static int by_self(const void *a, const void *b) {
    const ProfileEntry *x = a, *y = b;
    return x->self != y->self ? (x->self < y->self ? 1 : -1) : (x->total < y->total) - (x->total > y->total);
}

static int by_total(const void *a, const void *b) {
    const ProfileEntry *x = a, *y = b;
    return x->total != y->total ? (x->total < y->total ? 1 : -1) : (x->self < y->self) - (x->self > y->self);
}

static void print_table(FILE *f, ProfileEntry *v, size_t n, bool self) {
    fprintf(f, "%8s %6s %8s %6s  method\n", "self", "%", "total", "%");
    double s = g_samples ? (double)g_samples : 1.0;
    for (size_t i = 0; i < n && i < 80; i++) {
        if (!(self ? v[i].self : v[i].total)) break;
        Method *m = v[i].m;
        fprintf(f, "%8u %5.1f%% %8u %5.1f%%  %s.%s%s\n", v[i].self, 100.0 * v[i].self / s, v[i].total,
                100.0 * v[i].total / s, m->clazz ? m->clazz->name : "?", m->name, m->desc);
    }
}

static void write_report(void) {
    g_stopped = true;
    FILE *f = fopen(g_path, "w");
    if (!f) {
        LOGW("cannot write %s", g_path);
        return;
    }
    size_t n = 0;
    ProfileEntry *v = sa_malloc(PROFILE_SLOTS * sizeof *v);
    for (uint32_t i = 0; i < PROFILE_SLOTS; i++)
        if (g_entries[i].m) v[n++] = g_entries[i];
    fprintf(f, "%llu samples (about 1 ms each while some thread holds the GIL), %zu methods, %llu dropped\n\n",
            (unsigned long long)g_samples, n, (unsigned long long)g_dropped);
    fprintf(f, "== by self samples (the method was running)\n");
    qsort(v, n, sizeof *v, by_self);
    print_table(f, v, n, true);
    fprintf(f, "\n== by inclusive samples (the method was on the stack)\n");
    qsort(v, n, sizeof *v, by_total);
    print_table(f, v, n, false);
    fclose(f);
    free(v);
    LOGI("profile written to %s (%llu samples)", g_path, (unsigned long long)g_samples);
}

void vm_profile_init(void) {
    const char *p = getenv("SWITCHAPK_PROFILE");
    if (!p || !*p) return;
    g_path = sa_strdup(p);
    g_entries = sa_calloc(PROFILE_SLOTS, sizeof *g_entries);
    pthread_t th;
    if (pthread_create(&th, NULL, timer_thread, NULL) != 0) return;
    pthread_detach(th);
    atexit(write_report);
    LOGI("sampling Java stacks into %s", g_path);
}
