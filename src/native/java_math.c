/*
 * java.math.BigInteger magnitude arithmetic in C: multiply, divide (Knuth algorithm D) and modPow
 * (Montgomery with a 4-bit window for odd moduli, square-and-multiply with division otherwise).
 * Magnitudes are int[] of 32-bit words, least significant first, as BigInteger.mag stores them; leading
 * zero words are allowed on input. Results are new int[] arrays that BigInteger normalizes.
 * The interpreted versions took minutes for one 2048-bit modPow; public-key crypto (WS17) needs these.
 * Large modPow calls copy their inputs, release the GIL for the computation and reacquire it to allocate
 * the result, so other threads keep running during RSA or Diffie-Hellman.
 */
#include "natives.h"

#include <stdlib.h>
#include <string.h>

#define LOG_TAG "math"

typedef uint32_t limb;
typedef uint64_t dlimb;

static int norm_len(const limb *a, int n) {
    while (n > 0 && a[n - 1] == 0) n--;
    return n;
}

static ArrayObject *new_int_array(VMThread *t, const limb *src, int n) {
    ArrayObject *r = vm_alloc_prim_array(t, 'I', n);
    if (!r) return NULL;
    if (n) memcpy(ARRAY_DATA(r, limb), src, (size_t)n * sizeof(limb));
    return r;
}

/* r[0..an+bn) = a * b; r must not alias a or b. */
static void mul(limb *r, const limb *a, int an, const limb *b, int bn) {
    memset(r, 0, (size_t)(an + bn) * sizeof(limb));
    for (int i = 0; i < an; i++) {
        dlimb carry = 0, ai = a[i];
        if (!ai) continue;
        for (int j = 0; j < bn; j++) {
            dlimb t = ai * b[j] + r[i + j] + carry;
            r[i + j] = (limb)t;
            carry = t >> 32;
        }
        r[i + bn] = (limb)carry;
    }
}

static int clz32(limb x) {
    int n = 0;
    if (!x) return 32;
    while (!(x & 0x80000000u)) {
        x <<= 1;
        n++;
    }
    return n;
}

/*
 * Knuth algorithm D. u has un words, v has vn >= 1 words with v[vn-1] != 0, un >= vn.
 * q gets un - vn + 1 words, r gets vn words. Scratch is malloc'd. Returns false on allocation failure.
 */
static bool divmod(const limb *u, int un, const limb *v, int vn, limb *q, limb *r) {
    if (vn == 1) {
        dlimb rem = 0, d = v[0];
        for (int i = un - 1; i >= 0; i--) {
            dlimb cur = (rem << 32) | u[i];
            q[i] = (limb)(cur / d);
            rem = cur % d;
        }
        r[0] = (limb)rem;
        return true;
    }
    int s = clz32(v[vn - 1]);
    limb *vn_ = malloc((size_t)vn * sizeof(limb));
    limb *un_ = malloc((size_t)(un + 1) * sizeof(limb));
    if (!vn_ || !un_) {
        free(vn_);
        free(un_);
        return false;
    }
    for (int i = vn - 1; i > 0; i--) vn_[i] = (v[i] << s) | (s ? (limb)((dlimb)v[i - 1] >> (32 - s)) : 0);
    vn_[0] = v[0] << s;
    un_[un] = s ? (limb)((dlimb)u[un - 1] >> (32 - s)) : 0;
    for (int i = un - 1; i > 0; i--) un_[i] = (u[i] << s) | (s ? (limb)((dlimb)u[i - 1] >> (32 - s)) : 0);
    un_[0] = u[0] << s;
    const dlimb b = (dlimb)1 << 32;
    for (int j = un - vn; j >= 0; j--) {
        dlimb num = ((dlimb)un_[j + vn] << 32) | un_[j + vn - 1];
        dlimb qhat = num / vn_[vn - 1];
        dlimb rhat = num % vn_[vn - 1];
        while (qhat >= b || qhat * vn_[vn - 2] > ((rhat << 32) | un_[j + vn - 2])) {
            qhat--;
            rhat += vn_[vn - 1];
            if (rhat >= b) break;
        }
        int64_t borrow = 0;
        dlimb carry = 0;
        for (int i = 0; i < vn; i++) {
            dlimb p = qhat * vn_[i] + carry;
            carry = p >> 32;
            int64_t t = (int64_t)un_[i + j] - (int64_t)(limb)p - borrow;
            un_[i + j] = (limb)t;
            borrow = t < 0 ? 1 : 0;
        }
        int64_t t = (int64_t)un_[j + vn] - (int64_t)carry - borrow;
        un_[j + vn] = (limb)t;
        if (t < 0) {
            /* qhat was one too large: add v back. */
            qhat--;
            dlimb c = 0;
            for (int i = 0; i < vn; i++) {
                dlimb sum = (dlimb)un_[i + j] + vn_[i] + c;
                un_[i + j] = (limb)sum;
                c = sum >> 32;
            }
            un_[j + vn] += (limb)c;
        }
        q[j] = (limb)qhat;
    }
    for (int i = 0; i < vn; i++) r[i] = (un_[i] >> s) | (s ? (limb)((dlimb)un_[i + 1] << (32 - s)) : 0);
    free(vn_);
    free(un_);
    return true;
}

/* a mod m into r (mn words), a has an words. m normalized, mn >= 1. */
static bool modred(const limb *a, int an, const limb *m, int mn, limb *r) {
    an = norm_len(a, an);
    if (an < mn) {
        memset(r, 0, (size_t)mn * sizeof(limb));
        memcpy(r, a, (size_t)an * sizeof(limb));
        return true;
    }
    limb *q = malloc((size_t)(an - mn + 1) * sizeof(limb));
    if (!q) return false;
    bool ok = divmod(a, an, m, mn, q, r);
    free(q);
    return ok;
}

/* -m^-1 mod 2^32 for odd m0. */
static limb mont_inv(limb m0) {
    limb x = 1;
    for (int i = 0; i < 5; i++) x *= 2 - m0 * x;
    return (limb)(0u - x);
}

/* r = a * b * R^-1 mod m (CIOS). a, b < m, n words; t is scratch of n + 2 words. r may alias a or b. */
static void mont_mul(limb *r, const limb *a, const limb *b, const limb *m, int n, limb minv, limb *t) {
    memset(t, 0, (size_t)(n + 2) * sizeof(limb));
    for (int i = 0; i < n; i++) {
        dlimb c = 0, ai = a[i];
        for (int j = 0; j < n; j++) {
            dlimb s = (dlimb)t[j] + ai * b[j] + c;
            t[j] = (limb)s;
            c = s >> 32;
        }
        dlimb s = (dlimb)t[n] + c;
        t[n] = (limb)s;
        t[n + 1] = (limb)(s >> 32);
        limb u = t[0] * minv;
        c = ((dlimb)t[0] + (dlimb)u * m[0]) >> 32;
        for (int j = 1; j < n; j++) {
            s = (dlimb)t[j] + (dlimb)u * m[j] + c;
            t[j - 1] = (limb)s;
            c = s >> 32;
        }
        s = (dlimb)t[n] + c;
        t[n - 1] = (limb)s;
        t[n] = t[n + 1] + (limb)(s >> 32);
    }
    /* Conditional subtraction. */
    bool ge = t[n] != 0;
    if (!ge) {
        ge = true;
        for (int i = n - 1; i >= 0; i--) {
            if (t[i] != m[i]) {
                ge = t[i] > m[i];
                break;
            }
        }
    }
    if (ge) {
        int64_t borrow = 0;
        for (int i = 0; i < n; i++) {
            int64_t d = (int64_t)t[i] - m[i] - borrow;
            r[i] = (limb)d;
            borrow = d < 0;
        }
    } else {
        memcpy(r, t, (size_t)n * sizeof(limb));
    }
}

/* result (n words) = base^exp mod m for odd m. base already reduced. */
static bool modpow_mont(const limb *base, const limb *e, int en, const limb *m, int n, limb *result) {
    limb minv = mont_inv(m[0]);
    /* R^2 mod m, R = 2^(32n). */
    int r2n = 2 * n + 1;
    limb *r2src = calloc((size_t)r2n, sizeof(limb));
    limb *r2 = malloc((size_t)n * sizeof(limb));
    limb *table = malloc((size_t)16 * n * sizeof(limb));
    limb *acc = malloc((size_t)n * sizeof(limb));
    limb *t = malloc((size_t)(n + 2) * sizeof(limb));
    limb *one = calloc((size_t)n, sizeof(limb));
    bool ok = r2src && r2 && table && acc && t && one;
    if (ok) {
        r2src[2 * n] = 1;
        ok = modred(r2src, r2n, m, n, r2);
    }
    if (ok) {
        one[0] = 1;
        /* table[0] = R mod m (Montgomery 1), table[1] = base * R mod m, table[k] = base^k. */
        mont_mul(table, one, r2, m, n, minv, t);
        mont_mul(table + n, base, r2, m, n, minv, t);
        for (int k = 2; k < 16; k++) mont_mul(table + (size_t)k * n, table + (size_t)(k - 1) * n, table + n, m, n, minv, t);
        memcpy(acc, table, (size_t)n * sizeof(limb));
        int bits = en * 32;
        for (int i = (bits - 1) / 4 * 4; i >= 0; i -= 4) {
            for (int k = 0; k < 4; k++) mont_mul(acc, acc, acc, m, n, minv, t);
            int w = (int)((e[i >> 5] >> (i & 31)) & 0xf);
            /* A window can straddle a word boundary only if i & 31 > 28; i is a multiple of 4, so it cannot. */
            mont_mul(acc, acc, table + (size_t)w * n, m, n, minv, t);
        }
        /* Leave Montgomery form. */
        mont_mul(result, acc, one, m, n, minv, t);
    }
    free(r2src);
    free(r2);
    free(table);
    free(acc);
    free(t);
    free(one);
    return ok;
}

/* result (n words) = base^exp mod m for any m >= 1, by square-and-multiply with division. */
static bool modpow_plain(const limb *base, const limb *e, int en, const limb *m, int n, limb *result) {
    limb *acc = calloc((size_t)n, sizeof(limb));
    limb *b = malloc((size_t)n * sizeof(limb));
    limb *prod = malloc((size_t)2 * n * sizeof(limb));
    bool ok = acc && b && prod;
    if (ok) {
        limb one = 1;
        ok = modred(&one, 1, m, n, acc);
        memcpy(b, base, (size_t)n * sizeof(limb));
    }
    for (int i = 0; ok && i < en * 32; i++) {
        if ((e[i >> 5] >> (i & 31)) & 1) {
            mul(prod, acc, n, b, n);
            ok = modred(prod, 2 * n, m, n, acc);
        }
        if (!ok) break;
        mul(prod, b, n, b, n);
        ok = modred(prod, 2 * n, m, n, b);
    }
    if (ok) memcpy(result, acc, (size_t)n * sizeof(limb));
    free(acc);
    free(b);
    free(prod);
    return ok;
}

/* static int[] BigInteger.nMul(int[] a, int[] b) */
NATIVE(BigInteger_nMul) {
    ArrayObject *a = A_ARR(0), *b = A_ARR(1);
    if (!a || !b) {
        vm_throw_npe(t, "magnitude");
        return;
    }
    int an = norm_len(ARRAY_DATA(a, limb), a->length), bn = norm_len(ARRAY_DATA(b, limb), b->length);
    ArrayObject *r = vm_alloc_prim_array(t, 'I', an + bn);
    if (!r) return;
    if (an && bn) mul(ARRAY_DATA(r, limb), ARRAY_DATA(a, limb), an, ARRAY_DATA(b, limb), bn);
    R_OBJ(r);
}

/* static int[] BigInteger.nDivRem(int[] a, int[] b, int[] rem): quotient; rem (b.length words) gets a mod b. */
NATIVE(BigInteger_nDivRem) {
    ArrayObject *a = A_ARR(0), *b = A_ARR(1), *rem = A_ARR(2);
    if (!a || !b || !rem) {
        vm_throw_npe(t, "magnitude");
        return;
    }
    int an = norm_len(ARRAY_DATA(a, limb), a->length), bn = norm_len(ARRAY_DATA(b, limb), b->length);
    if (bn == 0) {
        vm_throw_new(t, "Ljava/lang/ArithmeticException;", "BigInteger divide by zero");
        return;
    }
    if (rem->length < bn) {
        vm_throw_new(t, "Ljava/lang/IllegalArgumentException;", "remainder array too short");
        return;
    }
    memset(ARRAY_DATA(rem, limb), 0, (size_t)rem->length * sizeof(limb));
    if (an < bn) {
        memcpy(ARRAY_DATA(rem, limb), ARRAY_DATA(a, limb), (size_t)an * sizeof(limb));
        ArrayObject *q = vm_alloc_prim_array(t, 'I', 0);
        if (q) R_OBJ(q);
        return;
    }
    ArrayObject *q = vm_alloc_prim_array(t, 'I', an - bn + 1);
    if (!q) return;
    if (!divmod(ARRAY_DATA(a, limb), an, ARRAY_DATA(b, limb), bn, ARRAY_DATA(q, limb), ARRAY_DATA(rem, limb))) {
        vm_throw_new(t, "Ljava/lang/OutOfMemoryError;", "BigInteger division");
        return;
    }
    R_OBJ(q);
}

/* static int[] BigInteger.nModPow(int[] base, int[] exp, int[] mod): base^exp mod mod, all non-negative, mod > 0. */
NATIVE(BigInteger_nModPow) {
    ArrayObject *ba = A_ARR(0), *ea = A_ARR(1), *ma = A_ARR(2);
    if (!ba || !ea || !ma) {
        vm_throw_npe(t, "magnitude");
        return;
    }
    int bn = norm_len(ARRAY_DATA(ba, limb), ba->length);
    int en = norm_len(ARRAY_DATA(ea, limb), ea->length);
    int n = norm_len(ARRAY_DATA(ma, limb), ma->length);
    if (n == 0) {
        vm_throw_new(t, "Ljava/lang/ArithmeticException;", "BigInteger: modulus not positive");
        return;
    }
    /* Copies, so the computation can run without the GIL. */
    limb *m = malloc((size_t)n * sizeof(limb));
    limb *e = malloc((size_t)(en ? en : 1) * sizeof(limb));
    limb *base = malloc((size_t)n * sizeof(limb));
    limb *out = malloc((size_t)n * sizeof(limb));
    bool ok = m && e && base && out;
    if (ok) {
        memcpy(m, ARRAY_DATA(ma, limb), (size_t)n * sizeof(limb));
        if (en) memcpy(e, ARRAY_DATA(ea, limb), (size_t)en * sizeof(limb));
        ok = modred(ARRAY_DATA(ba, limb), bn, m, n, base);
    }
    if (ok) {
        bool release = (dlimb)n * n * (dlimb)(en ? en : 1) > 4096; /* skip the GIL dance for tiny numbers */
        if (release) VM_BLOCKING_BEGIN(t);
        if (en == 0) {
            limb one = 1;
            ok = modred(&one, 1, m, n, out);
        } else if (m[0] & 1) {
            ok = modpow_mont(base, e, en, m, n, out);
        } else {
            ok = modpow_plain(base, e, en, m, n, out);
        }
        if (release) VM_BLOCKING_END(t);
    }
    ArrayObject *r = NULL;
    if (ok) r = new_int_array(t, out, n);
    else vm_throw_new(t, "Ljava/lang/OutOfMemoryError;", "BigInteger.modPow");
    free(m);
    free(e);
    free(base);
    free(out);
    if (r) R_OBJ(r);
}

static const NativeMethodReg g_regs[] = {
    {"Ljava/math/BigInteger;", "nMul", "([I[I)[I", BigInteger_nMul},
    {"Ljava/math/BigInteger;", "nDivRem", "([I[I[I)[I", BigInteger_nDivRem},
    {"Ljava/math/BigInteger;", "nModPow", "([I[I[I)[I", BigInteger_nModPow},
};

void natives_java_math_register(void) { vm_register_natives(g_regs, SA_ARRAY_LEN(g_regs)); }
