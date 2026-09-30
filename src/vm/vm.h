/*
 * The switchapk Dalvik virtual machine.
 *
 * A portable interpreter for Dalvik bytecode with:
 *   - precise heap tracing + conservative stack scanning mark/sweep GC
 *   - real OS threads serialized by a global interpreter lock (GIL)
 *   - JNI for native libraries loaded from the APK
 *
 * Register slots are 64 bits wide so references (host pointers) fit in a
 * single Dalvik register. Wide values (long/double) occupy the first slot of
 * the register pair; the second slot is ignored.
 */
#ifndef SWITCHAPK_VM_H
#define SWITCHAPK_VM_H

#include "../core/common.h"
#include "../core/dex.h"

#include <pthread.h>
#include <setjmp.h>

typedef struct Class Class;
typedef struct Object Object;
typedef struct ArrayObject ArrayObject;
typedef struct Method Method;
typedef struct Field Field;
typedef struct VMThread VMThread;
typedef struct Frame Frame;

typedef union {
    int32_t i;
    int64_t j;
    float f;
    double d;
    Object *l;
    uint64_t raw;
} JValue;

struct Object {
    Class *clazz;
    uint32_t monitor; /* index into monitor table, 0 = none */
    uint32_t gcflags; /* bit 0 = mark */
};

struct ArrayObject {
    Object obj;
    int32_t length;
    int32_t pad;
    void *data;        /* element storage: normally 'storage' below, or external memory */
    uint64_t storage[]; /* 8-aligned inline element storage */
};

#define ARRAY_DATA(a, type) ((type *)((ArrayObject *)(a))->data)

typedef void (*NativeFn)(VMThread *t, uint64_t *args, JValue *ret);

enum { CLASS_LOADED, CLASS_LINKED, CLASS_INITIALIZING, CLASS_INITIALIZED, CLASS_ERROR };

enum {
    CF_REFERENCE = 1 << 0,    /* java.lang.ref.Reference or subclass */
    CF_FINALIZABLE = 1 << 1,
    CF_THROWABLE = 1 << 2,
    CF_BOOT = 1 << 3,         /* loaded from the framework dex */
    CF_STUB = 1 << 4,         /* synthesized placeholder */
    CF_STRING = 1 << 5,
    CF_CLASS = 1 << 6,
};

struct Field {
    Class *clazz;
    const char *name;
    const char *type;
    uint32_t access;
    uint32_t offset; /* instance: byte offset in object; static: byte offset in static_data */
    uint32_t field_idx;
    char kind; /* 'I','J','L','[', ... */
};

struct Method {
    Class *clazz;
    const char *name;
    const char *desc;
    const char *shorty;
    uint32_t access;
    int32_t vtable_index;
    uint16_t arg_slots; /* register slots for arguments including 'this' */
    bool has_code;
    bool stub;
    DexCode code;
    DexFile *dex;
    uint32_t method_idx;
    NativeFn native;  /* internal native implementation */
    void *jni_fn;     /* JNI function pointer from a loaded library */
    Object *reflect;  /* cached java.lang.reflect.Method/Constructor mirror */
};

struct Class {
    Object *mirror; /* java.lang.Class instance */
    const char *descriptor;
    const char *name; /* dotted binary name */
    uint32_t access;
    uint32_t flags;
    int state;
    VMThread *init_thread;
    Class *super;
    Class **interfaces;
    uint32_t ninterfaces;
    Class **all_interfaces;
    uint32_t nall_interfaces;
    DexFile *dex;
    int32_t class_def_idx;
    Field *sfields;
    uint32_t nsfields;
    Field *ifields;
    uint32_t nifields;
    Method *methods;
    uint32_t nmethods;
    Method **vtable;
    uint32_t vtable_len;
    uint32_t instance_size;
    uint32_t *ref_offsets;
    uint32_t nref_offsets;
    uint8_t *static_data;
    uint32_t static_size;
    Class *component;
    Class *array_class; /* cached [ of this */
    char prim;          /* primitive type char, or 0 */
    uint8_t elem_size;  /* array element size */
    bool is_array;
    SaPtrMap itable_cache;
    SaPtrMap method_cache; /* (name^desc) lookups for dynamic dispatch */
    const char *source_file;
    Object *throw_on_init; /* ExceptionInInitializerError cause */
};

struct Frame {
    Frame *prev;
    Method *method;
    uint64_t *regs;
    uint32_t pc;
    Object *caught;      /* for move-exception */
    Object *sync;        /* monitor held by a synchronized method */
    uint64_t *saved_top; /* register stack top to restore on pop */
    bool entry;          /* first frame of a vm_interpret() activation */
};

#define LOCAL_REF_CAPACITY 4096

struct VMThread {
    int id;
    Object *jthread;
    Object *exception;
    JValue retval;
    Frame *frame;
    int depth;
    uint64_t *rstack;       /* register stack */
    uint64_t *rstack_top;
    uint64_t *rstack_end;
    /* conservative scanning */
    uintptr_t cstack_hi;    /* address near the thread entry point */
    uintptr_t cstack_lo;    /* sp recorded when parked */
    uintptr_t saved_regs[16]; /* callee-saved registers captured when parked */
    uint64_t gil_acquired_ns;
    bool has_gil;
    bool daemon;
    bool interrupted;
    bool attached_native;   /* attached via JNI AttachCurrentThread */
    uint32_t safepoint_counter;
    Method *cur_native; /* native method currently executing (for proxies/JNI) */
    pthread_t pthread;
    struct VMThread *next;
    /* JNI */
    void *jni_env;          /* JNIEnv* */
    Object **local_refs;
    uint32_t nlocal_refs;
    uint32_t *local_frames; /* stack of local-frame start indices */
    uint32_t nlocal_frames, cap_local_frames;
    /* monitor wait */
    pthread_cond_t wait_cond;
    int wait_monitor;
    bool notified;
    char name[64];
};

/* ---- VM global state ------------------------------------------------------ */

typedef struct {
    Class *Object, *String, *Class, *Throwable, *Cloneable, *Serializable;
    Class *Thread, *ThreadGroup, *Reference, *StackTraceElement;
    Class *Boolean, *Byte, *Character, *Short, *Integer, *Long, *Float, *Double, *Void;
    Class *prim_Z, *prim_B, *prim_C, *prim_S, *prim_I, *prim_J, *prim_F, *prim_D, *prim_V;
    Class *arr_Z, *arr_B, *arr_C, *arr_S, *arr_I, *arr_J, *arr_F, *arr_D, *arr_Object, *arr_String;
    Class *reflect_Method, *reflect_Constructor, *reflect_Field;
} WellKnownClasses;

typedef struct {
    Field *String_value;
    Field *String_hash;
    Field *Class_vmClass;
    Field *Throwable_detailMessage;
    Field *Throwable_cause;
    Field *Throwable_backtrace;
    Field *Throwable_stackTrace;
    Field *Thread_vmThread;
    Field *Thread_name;
    Field *Thread_daemon;
    Field *Thread_priority;
    Field *Reference_referent;
    Field *Boolean_value, *Byte_value, *Character_value, *Short_value, *Integer_value, *Long_value, *Float_value,
        *Double_value;
    Field *Method_vmMethod;
    Field *Constructor_vmMethod;
    Field *Field_vmField;
} WellKnownFields;

typedef struct {
    SaVec boot_dex;   /* framework dex files */
    SaVec app_dex;    /* application dex files */
    SaMap classes;    /* descriptor -> Class* */
    pthread_mutex_t class_lock;
    WellKnownClasses wk;
    WellKnownFields wf;
    /* threads */
    VMThread *threads;
    int next_thread_id;
    VMThread *main_thread;
    /* GIL */
    pthread_mutex_t gil_mutex;
    pthread_cond_t gil_cond;
    VMThread *gil_owner;
    uint64_t gil_next_ticket, gil_serving;
    int gil_waiters;
    volatile bool safepoint_requested;
    /* heap */
    SaPtrMap objects; /* set of live objects -> size */
    size_t heap_bytes, heap_threshold, heap_live_after_gc;
    uint64_t gc_count;
    SaVec explicit_roots; /* Object** */
    SaMap interned;      /* utf8 -> String */
    Object **global_refs;
    uint32_t nglobal_refs, cap_global_refs;
    SaPtrMap weak_global_refs;
    /* monitors */
    struct Monitor *monitors;
    uint32_t nmonitors, cap_monitors;
    uint32_t free_monitor;
    /* options */
    bool stub_missing_framework; /* synthesize missing android.* methods */
    bool trace;                  /* instruction tracing */
    int verbose_calls;
    const char *app_data_dir;
    const char *app_package;
    void *app_context;            /* platform/app specific */
    int exit_code;
    volatile bool exiting;
} VM;

extern VM g_vm;

/* ---- lifecycle ---------------------------------------------------------------- */

bool vm_init(void);
bool vm_boot(VMThread *t); /* loads core classes once boot dex files are added */
void vm_add_boot_dex(DexFile *d);
void vm_add_app_dex(DexFile *d);
/* Starts the main VM thread context on the calling OS thread. */
VMThread *vm_attach_main_thread(void *stack_hi);
void vm_shutdown(void);

/* ---- classes ------------------------------------------------------------------- */

Class *vm_find_class(VMThread *t, const char *descriptor);           /* throws NoClassDefFoundError */
Class *vm_find_class_noexc(VMThread *t, const char *descriptor);     /* returns NULL silently */
Class *vm_class_from_name(VMThread *t, const char *dotted, bool init); /* Class.forName */
Class *vm_array_class_of(VMThread *t, Class *component);
Class *vm_primitive_class(char prim);
bool vm_init_class(VMThread *t, Class *c);
bool vm_is_assignable(Class *to, Class *from); /* from instanceof to */
bool vm_instance_of(Object *o, Class *c);
Method *vm_find_method(Class *c, const char *name, const char *desc);          /* declared only */
Method *vm_find_method_hier(Class *c, const char *name, const char *desc);     /* incl. supers + ifaces */
Method *vm_find_virtual(Class *c, const char *name, const char *desc);         /* dispatch */
Method *vm_find_interface_impl(Class *c, Method *im);
Field *vm_find_field(Class *c, const char *name, const char *type);            /* instance or static, hier */
Field *vm_find_field_by_name(Class *c, const char *name);
Object *vm_class_mirror(VMThread *t, Class *c);
Class *vm_class_from_mirror(Object *mirror);
const char *vm_class_simple_name(Class *c);

/* resolution from dex indices */
Class *vm_resolve_type(VMThread *t, DexFile *d, uint32_t type_idx);
Method *vm_resolve_method(VMThread *t, DexFile *d, uint32_t method_idx, bool is_static, bool is_interface);
Field *vm_resolve_field(VMThread *t, DexFile *d, uint32_t field_idx, bool is_static);
Object *vm_resolve_string(VMThread *t, DexFile *d, uint32_t string_idx);

/* ---- heap ------------------------------------------------------------------------- */

Object *vm_alloc_object(VMThread *t, Class *c);
ArrayObject *vm_alloc_array(VMThread *t, Class *array_class, int32_t length);
ArrayObject *vm_alloc_prim_array(VMThread *t, char prim, int32_t length);
Object *vm_clone(VMThread *t, Object *o);
bool vm_is_object(const void *p);
void vm_gc(VMThread *t);
void vm_add_root(Object **slot);
void vm_remove_root(Object **slot);
size_t vm_object_size(Object *o);

/* ---- strings ----------------------------------------------------------------------- */

Object *vm_new_string_utf8(VMThread *t, const char *utf8);
Object *vm_new_string_mutf8(VMThread *t, const char *mutf8);
Object *vm_new_string_utf16(VMThread *t, const uint16_t *chars, int32_t len);
Object *vm_intern_string(VMThread *t, Object *str);
Object *vm_intern_utf8(VMThread *t, const char *utf8);
/* returns malloc'd UTF-8 */
char *vm_string_to_utf8(Object *str);
int32_t vm_string_length(Object *str);
const uint16_t *vm_string_chars(Object *str);
bool vm_string_equals_utf8(Object *str, const char *utf8);

/* ---- fields ------------------------------------------------------------------------ */

static inline void *vm_field_ptr(Object *o, Field *f) { return (uint8_t *)o + f->offset; }
static inline void *vm_static_ptr(Field *f) { return f->clazz->static_data + f->offset; }
static inline Object *vm_get_ref(Object *o, Field *f) { return *(Object **)vm_field_ptr(o, f); }
static inline void vm_set_ref(Object *o, Field *f, Object *v) { *(Object **)vm_field_ptr(o, f) = v; }
static inline int32_t vm_get_int(Object *o, Field *f) { return *(int32_t *)vm_field_ptr(o, f); }
static inline void vm_set_int(Object *o, Field *f, int32_t v) { *(int32_t *)vm_field_ptr(o, f) = v; }
static inline int64_t vm_get_long(Object *o, Field *f) { return *(int64_t *)vm_field_ptr(o, f); }
static inline void vm_set_long(Object *o, Field *f, int64_t v) { *(int64_t *)vm_field_ptr(o, f) = v; }
JValue vm_field_get(Object *o, Field *f);          /* works for static if o == NULL */
void vm_field_set(Object *o, Field *f, JValue v);
/* convenience by name (slow; for natives) */
Object *vm_get_ref_by_name(Object *o, const char *name);
int32_t vm_get_int_by_name(Object *o, const char *name);
float vm_get_float_by_name(Object *o, const char *name);
void vm_set_ref_by_name(Object *o, const char *name, Object *v);
void vm_set_int_by_name(Object *o, const char *name, int32_t v);

/* ---- invocation ------------------------------------------------------------------------ */

/* Invokes a method with raw register-slot arguments. Returns result; check t->exception. */
JValue vm_invoke(VMThread *t, Method *m, uint64_t *args);
/* Convenience: arguments given as JValues in shorty order (receiver first if instance). */
JValue vm_call(VMThread *t, Method *m, ...);
JValue vm_callv(VMThread *t, Method *m, const JValue *args);
/* Virtual dispatch by name on receiver. */
JValue vm_call_virtual(VMThread *t, Object *recv, const char *name, const char *desc, ...);
JValue vm_call_static(VMThread *t, const char *cls, const char *name, const char *desc, ...);
Object *vm_new_instance(VMThread *t, const char *cls, const char *ctor_desc, ...);
int vm_shorty_slots(const char *shorty, bool is_static);

/* ---- exceptions --------------------------------------------------------------------------- */

void vm_throw(VMThread *t, Object *exc);
void vm_throw_new(VMThread *t, const char *cls_desc, const char *fmt, ...) SA_PRINTF(3, 4);
void vm_throw_npe(VMThread *t, const char *what);
void vm_throw_oom(VMThread *t);
bool vm_check_exception(VMThread *t);
void vm_print_exception(VMThread *t, Object *exc);
void vm_describe_exception(VMThread *t, Object *exc, SaBuf *out);
void vm_fill_stack_trace(VMThread *t, Object *throwable);
Object *vm_get_stack_trace(VMThread *t, Object *throwable);
void vm_dump_stack(VMThread *t);

/* ---- threads / GIL / monitors ------------------------------------------------------------------ */

VMThread *vm_current_thread(void);
VMThread *vm_thread_create(const char *name);
void vm_thread_register(VMThread *t, void *stack_hi);
void vm_thread_unregister(VMThread *t);
void vm_gil_acquire(VMThread *t);
void vm_gil_release(VMThread *t);
void vm_safepoint(VMThread *t);
void vm_yield(VMThread *t);
/* Release the GIL around a blocking region. */
#define VM_BLOCKING_BEGIN(t) vm_gil_release(t)
#define VM_BLOCKING_END(t) vm_gil_acquire(t)
void vm_monitor_enter(VMThread *t, Object *o);
bool vm_monitor_exit(VMThread *t, Object *o);
void vm_monitor_wait(VMThread *t, Object *o, int64_t ms, int32_t ns);
void vm_monitor_notify(VMThread *t, Object *o, bool all);
bool vm_monitor_holds(VMThread *t, Object *o);
bool vm_thread_start(VMThread *t, Object *jthread, int64_t stack_size);
void vm_thread_sleep(VMThread *t, int64_t ms, int32_t ns);
void vm_thread_interrupt(VMThread *t, Object *jthread);
/* Waits on the global cond using the GIL mutex (used for class init waits). */
void vm_gil_wait_global(VMThread *t);
void vm_gil_broadcast_global(void);

/* ---- natives registry ------------------------------------------------------------------------ */

typedef struct {
    const char *cls;  /* descriptor, e.g. "Ljava/lang/Object;" */
    const char *name;
    const char *desc;
    NativeFn fn;
} NativeMethodReg;

void vm_register_natives(const NativeMethodReg *regs, size_t n);
NativeFn vm_lookup_native(const char *cls, const char *name, const char *desc);
void vm_natives_init(void); /* registers all built-in native tables */

/* JNI */
void *vm_jni_env(VMThread *t);
void *vm_java_vm(void);
bool vm_jni_bind(VMThread *t, Method *m); /* find a JNI implementation in loaded libs */
JValue vm_jni_call(VMThread *t, Method *m, uint64_t *args);
Object *vm_jni_decode(VMThread *t, void *ref);
void *vm_jni_new_local(VMThread *t, Object *o);
void vm_jni_push_frame(VMThread *t);
void vm_jni_pop_frame(VMThread *t);

/* interpreter */
JValue vm_interpret(VMThread *t, Method *m, uint64_t *args);
Method *vm_current_native(VMThread *t);

/* helpers */
const char *vm_desc_to_dotted(const char *desc, char *buf, size_t bufsz);
char *vm_dotted_to_desc(const char *dotted);
Object *vm_box(VMThread *t, char prim, JValue v);
bool vm_unbox(VMThread *t, Object *o, char prim, JValue *out);
void vm_method_pretty(Method *m, char *buf, size_t n);

#define VM_MAX_DEPTH 1800

#endif
