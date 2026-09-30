/*
 * Threads, the global interpreter lock and Java monitors.
 *
 * Every Java thread is a real OS thread. Only the thread that owns the GIL may
 * touch the managed heap or run bytecode. The GIL is FIFO (ticket based) and
 * the owner yields it at safepoints after its time slice expires, so threads
 * such as a game loop and the UI thread interleave fairly. Blocking
 * operations (sleep, wait, I/O, JNI calls) release it.
 */
#include "vm.h"

#include <errno.h>
#include <time.h>

#define LOG_TAG "thread"

#define TIME_SLICE_NS (4 * 1000 * 1000)
#define RSTACK_SLOTS (96 * 1024)

void vm_capture_thread_regs(VMThread *t);

static __thread VMThread *tls_thread;

VMThread *vm_current_thread(void) { return tls_thread; }

/* ---- GIL -------------------------------------------------------------------------- */

static void gil_take_locked(VMThread *t) {
    uint64_t ticket = g_vm.gil_next_ticket++;
    g_vm.gil_waiters++;
    if (g_vm.gil_owner) g_vm.safepoint_requested = true;
    while (g_vm.gil_owner != NULL || g_vm.gil_serving != ticket) pthread_cond_wait(&g_vm.gil_cond, &g_vm.gil_mutex);
    g_vm.gil_waiters--;
    g_vm.gil_serving++;
    g_vm.gil_owner = t;
    t->has_gil = true;
    t->gil_acquired_ns = sa_time_ns();
    /* wake the next ticket holder so it can wait for the owner to release */
    if (g_vm.gil_waiters) pthread_cond_broadcast(&g_vm.gil_cond);
}

static void gil_drop_locked(VMThread *t) {
    g_vm.gil_owner = NULL;
    t->has_gil = false;
    pthread_cond_broadcast(&g_vm.gil_cond);
}

void vm_gil_acquire(VMThread *t) {
    pthread_mutex_lock(&g_vm.gil_mutex);
    gil_take_locked(t);
    pthread_mutex_unlock(&g_vm.gil_mutex);
}

void vm_gil_release(VMThread *t) {
    vm_capture_thread_regs(t);
    pthread_mutex_lock(&g_vm.gil_mutex);
    gil_drop_locked(t);
    pthread_mutex_unlock(&g_vm.gil_mutex);
}

/* Waits on cond, releasing the GIL while blocked. timeout_ns < 0 = forever.
 * Returns false on timeout. */
static bool gil_cond_wait(VMThread *t, pthread_cond_t *cond, int64_t timeout_ns) {
    vm_capture_thread_regs(t);
    pthread_mutex_lock(&g_vm.gil_mutex);
    gil_drop_locked(t);
    bool ok = true;
    if (timeout_ns < 0) {
        pthread_cond_wait(cond, &g_vm.gil_mutex);
    } else {
        struct timespec ts;
        clock_gettime(CLOCK_REALTIME, &ts);
        int64_t ns = ts.tv_nsec + timeout_ns;
        ts.tv_sec += (time_t)(ns / 1000000000);
        ts.tv_nsec = (long)(ns % 1000000000);
        ok = pthread_cond_timedwait(cond, &g_vm.gil_mutex, &ts) != ETIMEDOUT;
    }
    gil_take_locked(t);
    pthread_mutex_unlock(&g_vm.gil_mutex);
    return ok;
}

static pthread_cond_t g_global_cond = PTHREAD_COND_INITIALIZER;

void vm_gil_wait_global(VMThread *t) { gil_cond_wait(t, &g_global_cond, 50 * 1000 * 1000); }

void vm_gil_broadcast_global(void) {
    pthread_mutex_lock(&g_vm.gil_mutex);
    pthread_cond_broadcast(&g_global_cond);
    pthread_mutex_unlock(&g_vm.gil_mutex);
}

void vm_yield(VMThread *t) {
    vm_capture_thread_regs(t);
    pthread_mutex_lock(&g_vm.gil_mutex);
    if (g_vm.gil_waiters > 0) {
        gil_drop_locked(t);
        gil_take_locked(t);
    } else {
        t->gil_acquired_ns = sa_time_ns();
    }
    pthread_mutex_unlock(&g_vm.gil_mutex);
}

void vm_safepoint(VMThread *t) {
    if (g_vm.heap_bytes > g_vm.heap_threshold) vm_gc(t);
    if (g_vm.gil_waiters > 0) {
        if (sa_time_ns() - t->gil_acquired_ns >= TIME_SLICE_NS) vm_yield(t);
    }
    g_vm.safepoint_requested = g_vm.gil_waiters > 0 || g_vm.heap_bytes > g_vm.heap_threshold;
}

/* ---- thread registry --------------------------------------------------------------- */

VMThread *vm_thread_create(const char *name) {
    VMThread *t = sa_calloc(1, sizeof *t);
    t->rstack = sa_calloc(RSTACK_SLOTS, sizeof(uint64_t));
    t->rstack_top = t->rstack;
    t->rstack_end = t->rstack + RSTACK_SLOTS;
    t->local_refs = sa_calloc(LOCAL_REF_CAPACITY, sizeof(Object *));
    t->cap_local_frames = 64;
    t->local_frames = sa_calloc(t->cap_local_frames, sizeof(uint32_t));
    pthread_cond_init(&t->wait_cond, NULL);
    snprintf(t->name, sizeof t->name, "%s", name ? name : "Thread");
    t->jni_env = vm_jni_env(t);
    return t;
}

void vm_thread_register(VMThread *t, void *stack_hi) {
    tls_thread = t;
    t->cstack_hi = (uintptr_t)stack_hi;
    t->pthread = pthread_self();
    pthread_mutex_lock(&g_vm.gil_mutex);
    t->id = ++g_vm.next_thread_id;
    t->next = g_vm.threads;
    g_vm.threads = t;
    pthread_mutex_unlock(&g_vm.gil_mutex);
}

void vm_thread_unregister(VMThread *t) {
    pthread_mutex_lock(&g_vm.gil_mutex);
    for (VMThread **pp = &g_vm.threads; *pp; pp = &(*pp)->next) {
        if (*pp == t) {
            *pp = t->next;
            break;
        }
    }
    pthread_mutex_unlock(&g_vm.gil_mutex);
    tls_thread = NULL;
}

VMThread *vm_attach_main_thread(void *stack_hi) {
    VMThread *t = vm_thread_create("main");
    vm_thread_register(t, stack_hi);
    g_vm.main_thread = t;
    vm_gil_acquire(t);
    return t;
}

/* ---- monitors ------------------------------------------------------------------------ */

typedef struct Monitor {
    VMThread *owner;
    uint32_t count;
    uint32_t waiters;
    pthread_cond_t enter_cond;
    pthread_cond_t wait_cond;
    uint32_t next_free;
} Monitor;

static Monitor **g_monitors;

static uint32_t monitor_alloc(void) {
    uint32_t idx;
    if (g_vm.free_monitor) {
        idx = g_vm.free_monitor;
        g_vm.free_monitor = g_monitors[idx]->next_free;
        Monitor *m = g_monitors[idx];
        m->owner = NULL;
        m->count = m->waiters = 0;
        m->next_free = 0;
        return idx;
    }
    if (g_vm.nmonitors + 1 >= g_vm.cap_monitors) {
        g_vm.cap_monitors = g_vm.cap_monitors ? g_vm.cap_monitors * 2 : 256;
        g_monitors = sa_realloc(g_monitors, g_vm.cap_monitors * sizeof(Monitor *));
    }
    idx = ++g_vm.nmonitors; /* index 0 means "no monitor" */
    Monitor *m = sa_calloc(1, sizeof *m);
    pthread_cond_init(&m->enter_cond, NULL);
    pthread_cond_init(&m->wait_cond, NULL);
    g_monitors[idx] = m;
    return idx;
}

void vm_monitor_free_index(uint32_t idx) {
    if (!idx || idx > g_vm.nmonitors) return;
    g_monitors[idx]->next_free = g_vm.free_monitor;
    g_vm.free_monitor = idx;
}

static Monitor *monitor_of(Object *o) {
    if (!o->monitor) o->monitor = monitor_alloc();
    return g_monitors[o->monitor];
}

void vm_monitor_enter(VMThread *t, Object *o) {
    if (!o) {
        vm_throw_npe(t, "monitor-enter on null");
        return;
    }
    Monitor *m = monitor_of(o);
    if (m->owner == t) {
        m->count++;
        return;
    }
    while (m->owner != NULL) {
        gil_cond_wait(t, &m->enter_cond, -1);
        m = monitor_of(o);
    }
    m->owner = t;
    m->count = 1;
}

bool vm_monitor_exit(VMThread *t, Object *o) {
    if (!o) {
        vm_throw_npe(t, "monitor-exit on null");
        return false;
    }
    if (!o->monitor || g_monitors[o->monitor]->owner != t) {
        vm_throw_new(t, "Ljava/lang/IllegalMonitorStateException;", "not owner");
        return false;
    }
    Monitor *m = g_monitors[o->monitor];
    if (--m->count == 0) {
        m->owner = NULL;
        pthread_mutex_lock(&g_vm.gil_mutex);
        pthread_cond_broadcast(&m->enter_cond);
        pthread_mutex_unlock(&g_vm.gil_mutex);
    }
    return true;
}

bool vm_monitor_holds(VMThread *t, Object *o) { return o && o->monitor && g_monitors[o->monitor]->owner == t; }

void vm_monitor_wait(VMThread *t, Object *o, int64_t ms, int32_t ns) {
    if (!vm_monitor_holds(t, o)) {
        vm_throw_new(t, "Ljava/lang/IllegalMonitorStateException;", "object not locked by thread before wait()");
        return;
    }
    if (t->interrupted) {
        t->interrupted = false;
        vm_throw_new(t, "Ljava/lang/InterruptedException;", NULL);
        return;
    }
    Monitor *m = g_monitors[o->monitor];
    uint32_t saved = m->count;
    m->owner = NULL;
    m->count = 0;
    m->waiters++;
    t->wait_monitor = (int)o->monitor;
    pthread_mutex_lock(&g_vm.gil_mutex);
    pthread_cond_broadcast(&m->enter_cond);
    pthread_mutex_unlock(&g_vm.gil_mutex);
    int64_t timeout = (ms == 0 && ns == 0) ? -1 : ms * 1000000 + ns;
    gil_cond_wait(t, &m->wait_cond, timeout);
    t->wait_monitor = 0;
    m = g_monitors[o->monitor];
    m->waiters--;
    while (m->owner != NULL) {
        gil_cond_wait(t, &m->enter_cond, -1);
        m = g_monitors[o->monitor];
    }
    m->owner = t;
    m->count = saved;
    if (t->interrupted) {
        t->interrupted = false;
        vm_throw_new(t, "Ljava/lang/InterruptedException;", NULL);
    }
}

void vm_monitor_notify(VMThread *t, Object *o, bool all) {
    if (!vm_monitor_holds(t, o)) {
        vm_throw_new(t, "Ljava/lang/IllegalMonitorStateException;", "object not locked by thread before notify()");
        return;
    }
    Monitor *m = g_monitors[o->monitor];
    if (!m->waiters) return;
    pthread_mutex_lock(&g_vm.gil_mutex);
    if (all) pthread_cond_broadcast(&m->wait_cond);
    else pthread_cond_signal(&m->wait_cond);
    pthread_mutex_unlock(&g_vm.gil_mutex);
}

/* ---- sleeping / interruption ------------------------------------------------------------ */

void vm_thread_sleep(VMThread *t, int64_t ms, int32_t ns) {
    if (t->interrupted) {
        t->interrupted = false;
        vm_throw_new(t, "Ljava/lang/InterruptedException;", "sleep interrupted");
        return;
    }
    int64_t total = ms * 1000000 + ns;
    if (total <= 0) {
        vm_yield(t);
        return;
    }
    uint64_t deadline = sa_time_ns() + (uint64_t)total;
    for (;;) {
        uint64_t now = sa_time_ns();
        if (now >= deadline) break;
        gil_cond_wait(t, &t->wait_cond, (int64_t)(deadline - now));
        if (t->interrupted) {
            t->interrupted = false;
            vm_throw_new(t, "Ljava/lang/InterruptedException;", "sleep interrupted");
            return;
        }
    }
}

void vm_thread_interrupt(VMThread *self, Object *jthread) {
    SA_UNUSED(self);
    VMThread *target = (VMThread *)(intptr_t)vm_get_long(jthread, g_vm.wf.Thread_vmThread);
    if (!target) return;
    target->interrupted = true;
    pthread_mutex_lock(&g_vm.gil_mutex);
    pthread_cond_broadcast(&target->wait_cond);
    if (target->wait_monitor) pthread_cond_broadcast(&g_monitors[target->wait_monitor]->wait_cond);
    pthread_mutex_unlock(&g_vm.gil_mutex);
}

/* ---- starting Java threads -------------------------------------------------------------- */

static void *thread_entry(void *arg) {
    VMThread *t = arg;
    volatile int stack_marker = 0;
    vm_thread_register(t, (void *)((uintptr_t)&stack_marker + 256));
    vm_gil_acquire(t);
    Object *jthread = t->jthread;
    LOGD("thread %d (%s) started", t->id, t->name);
    vm_call_virtual(t, jthread, "run", "()V");
    if (t->exception) {
        Object *exc = t->exception;
        t->exception = NULL;
        Class *tc = jthread->clazz;
        Method *m = vm_find_method_hier(tc, "dispatchUncaughtException", "(Ljava/lang/Throwable;)V");
        if (m) {
            JValue a[2];
            a[0].l = jthread;
            a[1].l = exc;
            vm_callv(t, m, a);
        }
        if (t->exception) {
            vm_print_exception(t, t->exception);
            t->exception = NULL;
        }
    }
    /* mark dead and wake joiners */
    vm_set_long(jthread, g_vm.wf.Thread_vmThread, 0);
    vm_monitor_enter(t, jthread);
    vm_monitor_notify(t, jthread, true);
    vm_monitor_exit(t, jthread);
    t->exception = NULL;
    LOGD("thread %d (%s) finished", t->id, t->name);
    vm_gil_release(t);
    vm_thread_unregister(t);
    free(t->rstack);
    t->rstack = NULL;
    free(t->local_refs);
    free(t->local_frames);
    /* the VMThread struct itself is intentionally leaked: other threads may hold stale pointers */
    return NULL;
}

bool vm_thread_start(VMThread *parent, Object *jthread, int64_t stack_size) {
    Object *jname = g_vm.wf.Thread_name ? vm_get_ref(jthread, g_vm.wf.Thread_name) : NULL;
    char *name = jname ? vm_string_to_utf8(jname) : sa_strdup("Thread");
    VMThread *t = vm_thread_create(name);
    free(name);
    t->jthread = jthread;
    t->daemon = g_vm.wf.Thread_daemon ? vm_field_get(jthread, g_vm.wf.Thread_daemon).i != 0 : false;
    vm_set_long(jthread, g_vm.wf.Thread_vmThread, (int64_t)(intptr_t)t);
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    size_t ss = stack_size > 0 ? (size_t)stack_size : 0;
    if (ss < 2u * 1024 * 1024) ss = 2u * 1024 * 1024;
    pthread_attr_setstacksize(&attr, ss);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    /* The new thread's jthread must stay reachable until it registers: it is
     * referenced from the Java Thread object held by the caller's frame. We
     * also register it immediately so GC sees it. */
    pthread_t pt;
    int r = pthread_create(&pt, &attr, thread_entry, t);
    pthread_attr_destroy(&attr);
    if (r != 0) {
        vm_set_long(jthread, g_vm.wf.Thread_vmThread, 0);
        vm_throw_new(parent, "Ljava/lang/OutOfMemoryError;", "pthread_create failed: %d", r);
        return false;
    }
    return true;
}
