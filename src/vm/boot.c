/*
 * VM initialization, bootstrap of core classes and the native method registry.
 */
#include "vm.h"

#define LOG_TAG "vm"

static SaMap g_natives;

static char *native_key(const char *cls, const char *name, const char *desc) {
    return sa_sprintf("%s|%s|%s", cls, name, desc);
}

void vm_register_natives(const NativeMethodReg *regs, size_t n) {
    for (size_t i = 0; i < n; i++) {
        char *k = native_key(regs[i].cls, regs[i].name, regs[i].desc);
        sa_map_put(&g_natives, k, (void *)regs[i].fn);
        free(k);
    }
}

NativeFn vm_lookup_native(const char *cls, const char *name, const char *desc) {
    char buf[1024];
    int n = snprintf(buf, sizeof buf, "%s|%s|%s", cls, name, desc);
    if (n < 0 || (size_t)n >= sizeof buf) return NULL;
    return (NativeFn)sa_map_get(&g_natives, buf);
}

bool vm_init(void) {
    memset(&g_vm, 0, sizeof g_vm);
    pthread_mutex_init(&g_vm.class_lock, NULL);
    pthread_mutex_init(&g_vm.gil_mutex, NULL);
    pthread_cond_init(&g_vm.gil_cond, NULL);
    g_vm.heap_threshold = 24u * 1024u * 1024u;
    g_vm.stub_missing_framework = true;
    vm_natives_init();
    return true;
}

static Class *need(VMThread *t, const char *desc) {
    Class *c = vm_find_class(t, desc);
    if (!c) sa_fatal("bootstrap class %s missing from framework dex", desc);
    return c;
}

static Field *need_field(Class *c, const char *name, const char *type) {
    Field *f = vm_find_field(c, name, type);
    if (!f) sa_fatal("bootstrap field %s.%s:%s missing", c->name, name, type);
    return f;
}

static Field *opt_field(Class *c, const char *name, const char *type) {
    return c ? vm_find_field(c, name, type) : NULL;
}

bool vm_boot(VMThread *t) {
    WellKnownClasses *wk = &g_vm.wk;
    WellKnownFields *wf = &g_vm.wf;
    wk->Object = need(t, "Ljava/lang/Object;");
    wk->Cloneable = need(t, "Ljava/lang/Cloneable;");
    wk->Serializable = need(t, "Ljava/io/Serializable;");
    wk->Class = need(t, "Ljava/lang/Class;");
    wf->Class_vmClass = need_field(wk->Class, "vmClass", "J");
    wk->String = need(t, "Ljava/lang/String;");
    wf->String_value = need_field(wk->String, "value", "[C");
    wf->String_hash = opt_field(wk->String, "hash", "I");

    wk->prim_Z = vm_primitive_class('Z');
    wk->prim_B = vm_primitive_class('B');
    wk->prim_C = vm_primitive_class('C');
    wk->prim_S = vm_primitive_class('S');
    wk->prim_I = vm_primitive_class('I');
    wk->prim_J = vm_primitive_class('J');
    wk->prim_F = vm_primitive_class('F');
    wk->prim_D = vm_primitive_class('D');
    wk->prim_V = vm_primitive_class('V');
    wk->arr_Z = need(t, "[Z");
    wk->arr_B = need(t, "[B");
    wk->arr_C = need(t, "[C");
    wk->arr_S = need(t, "[S");
    wk->arr_I = need(t, "[I");
    wk->arr_J = need(t, "[J");
    wk->arr_F = need(t, "[F");
    wk->arr_D = need(t, "[D");
    wk->arr_Object = need(t, "[Ljava/lang/Object;");
    wk->arr_String = need(t, "[Ljava/lang/String;");

    /* Mirrors for classes loaded before java.lang.Class existed are created lazily. */
    wk->Throwable = need(t, "Ljava/lang/Throwable;");
    wf->Throwable_detailMessage = need_field(wk->Throwable, "detailMessage", "Ljava/lang/String;");
    wf->Throwable_cause = need_field(wk->Throwable, "cause", "Ljava/lang/Throwable;");
    wf->Throwable_backtrace = need_field(wk->Throwable, "backtrace", "Ljava/lang/Object;");
    wf->Throwable_stackTrace = opt_field(wk->Throwable, "stackTrace", "[Ljava/lang/StackTraceElement;");
    wk->Thread = need(t, "Ljava/lang/Thread;");
    wf->Thread_vmThread = need_field(wk->Thread, "vmThread", "J");
    wf->Thread_name = opt_field(wk->Thread, "name", "Ljava/lang/String;");
    wf->Thread_daemon = opt_field(wk->Thread, "daemon", "Z");
    wf->Thread_priority = opt_field(wk->Thread, "priority", "I");
    wk->Reference = vm_find_class_noexc(t, "Ljava/lang/ref/Reference;");
    wf->Reference_referent = opt_field(wk->Reference, "referent", "Ljava/lang/Object;");
    wk->StackTraceElement = vm_find_class_noexc(t, "Ljava/lang/StackTraceElement;");

    wk->reflect_Method = vm_find_class_noexc(t, "Ljava/lang/reflect/Method;");
    wk->reflect_Constructor = vm_find_class_noexc(t, "Ljava/lang/reflect/Constructor;");
    wk->reflect_Field = vm_find_class_noexc(t, "Ljava/lang/reflect/Field;");
    wf->Method_vmMethod = opt_field(wk->reflect_Method, "vmMethod", "J");
    wf->Constructor_vmMethod = opt_field(wk->reflect_Constructor, "vmMethod", "J");
    wf->Field_vmField = opt_field(wk->reflect_Field, "vmField", "J");

    const char *boxes[][2] = {{"Ljava/lang/Boolean;", "Z"}, {"Ljava/lang/Byte;", "B"},   {"Ljava/lang/Character;", "C"},
                              {"Ljava/lang/Short;", "S"},   {"Ljava/lang/Integer;", "I"}, {"Ljava/lang/Long;", "J"},
                              {"Ljava/lang/Float;", "F"},   {"Ljava/lang/Double;", "D"}};
    Class **bc[] = {&wk->Boolean, &wk->Byte, &wk->Character, &wk->Short, &wk->Integer, &wk->Long, &wk->Float, &wk->Double};
    Field **bf[] = {&wf->Boolean_value, &wf->Byte_value,    &wf->Character_value, &wf->Short_value,
                    &wf->Integer_value, &wf->Long_value, &wf->Float_value,     &wf->Double_value};
    for (int i = 0; i < 8; i++) {
        *bc[i] = need(t, boxes[i][0]);
        *bf[i] = need_field(*bc[i], "value", boxes[i][1]);
    }
    if (!vm_init_class(t, wk->String) || !vm_init_class(t, wk->Class) || !vm_init_class(t, wk->Thread)) {
        if (t->exception) vm_print_exception(t, t->exception);
        return false;
    }
    /* make the main thread's java.lang.Thread */
    Object *th = vm_new_instance(t, "Ljava/lang/Thread;", "(JLjava/lang/String;)V", (int64_t)(intptr_t)t,
                                 vm_new_string_utf8(t, t->name));
    if (!th) {
        if (t->exception) vm_print_exception(t, t->exception);
        return false;
    }
    t->jthread = th;
    /* initialize System (sets up System.out etc.) */
    Class *sys = need(t, "Ljava/lang/System;");
    if (!vm_init_class(t, sys)) {
        if (t->exception) vm_print_exception(t, t->exception);
        return false;
    }
    return true;
}

void vm_shutdown(void) {}
