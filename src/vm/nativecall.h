/*
 * Building native calls with arbitrary signatures (see callstub.S).
 */
#ifndef SWITCHAPK_NATIVECALL_H
#define SWITCHAPK_NATIVECALL_H

#include "../core/common.h"

#if defined(__x86_64__)
#define NC_INT_REGS 6
#else
#define NC_INT_REGS 8
#endif
#define NC_FP_REGS 8
#define NC_MAX_STACK 128

typedef struct {
    uint64_t iregs[8];
    uint64_t fregs[8];
    uint64_t stack[NC_MAX_STACK];
    int ni, nf, ns;
} NativeArgs;

void sa_native_call(void *fn, const uint64_t *iregs, const uint64_t *fregs, const uint64_t *stack, size_t nstack,
                    uint64_t *ret);

static inline void nc_init(NativeArgs *a) {
    a->ni = a->nf = a->ns = 0;
    memset(a->iregs, 0, sizeof a->iregs);
    memset(a->fregs, 0, sizeof a->fregs);
}

static inline void nc_int(NativeArgs *a, uint64_t v) {
    if (a->ni < NC_INT_REGS) a->iregs[a->ni++] = v;
    else if (a->ns < NC_MAX_STACK) a->stack[a->ns++] = v;
}

static inline void nc_ptr(NativeArgs *a, const void *p) { nc_int(a, (uint64_t)(uintptr_t)p); }

static inline void nc_float_bits(NativeArgs *a, uint32_t bits) {
    if (a->nf < NC_FP_REGS) a->fregs[a->nf++] = bits;
    else if (a->ns < NC_MAX_STACK) a->stack[a->ns++] = bits;
}

static inline void nc_float(NativeArgs *a, float f) {
    uint32_t b;
    memcpy(&b, &f, 4);
    nc_float_bits(a, b);
}

static inline void nc_double_bits(NativeArgs *a, uint64_t bits) {
    if (a->nf < NC_FP_REGS) a->fregs[a->nf++] = bits;
    else if (a->ns < NC_MAX_STACK) a->stack[a->ns++] = bits;
}

static inline void nc_double(NativeArgs *a, double d) {
    uint64_t b;
    memcpy(&b, &d, 8);
    nc_double_bits(a, b);
}

/* Performs the call; ret[0] = integer result, ret[1] = fp result bits. */
static inline void nc_call(void *fn, NativeArgs *a, uint64_t ret[2]) {
    sa_native_call(fn, a->iregs, a->fregs, a->stack, (size_t)a->ns, ret);
}

#endif
