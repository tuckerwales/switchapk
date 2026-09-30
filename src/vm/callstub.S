/*
 * Generic native call trampoline used by JNI and the Android native layer.
 *
 *   void sa_native_call(void *fn, const uint64_t *iregs, const uint64_t *fregs,
 *                       const uint64_t *stack, size_t nstack, uint64_t *ret);
 *
 * iregs: integer argument registers (8 on AArch64, 6 used on x86-64)
 * fregs: 8 floating point argument registers (raw bit patterns; floats in the low 32 bits)
 * stack: nstack 8-byte stack argument slots, in order
 * ret:   ret[0] = integer result register, ret[1] = floating point result register bits
 */

#if defined(__aarch64__)

    .text
    .align 4
    .global sa_native_call
    .type sa_native_call, %function
sa_native_call:
    stp x29, x30, [sp, #-48]!
    mov x29, sp
    stp x19, x20, [sp, #16]
    stp x21, x22, [sp, #32]
    mov x19, x0            /* fn */
    mov x20, x1            /* iregs */
    mov x21, x2            /* fregs */
    mov x22, x5            /* ret */
    /* reserve 16-byte aligned stack for the outgoing arguments */
    lsl x9, x4, #3
    add x9, x9, #15
    and x9, x9, #~15
    sub sp, sp, x9
    mov x10, #0
1:  cmp x10, x4
    b.ge 2f
    ldr x11, [x3, x10, lsl #3]
    str x11, [sp, x10, lsl #3]
    add x10, x10, #1
    b 1b
2:
    ldp d0, d1, [x21, #0]
    ldp d2, d3, [x21, #16]
    ldp d4, d5, [x21, #32]
    ldp d6, d7, [x21, #48]
    ldp x0, x1, [x20, #0]
    ldp x2, x3, [x20, #16]
    ldp x4, x5, [x20, #32]
    ldp x6, x7, [x20, #48]
    blr x19
    str x0, [x22, #0]
    str d0, [x22, #8]
    mov sp, x29
    ldp x19, x20, [sp, #16]
    ldp x21, x22, [sp, #32]
    ldp x29, x30, [sp], #48
    ret
    .size sa_native_call, .-sa_native_call

#elif defined(__x86_64__)

    .text
    .align 16
    .globl sa_native_call
    .type sa_native_call, @function
sa_native_call:
    pushq %rbp
    movq %rsp, %rbp
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    movq %rdi, %rbx        /* fn */
    movq %rsi, %r12        /* iregs */
    movq %rdx, %r13        /* fregs */
    movq %r9, %r14         /* ret */
    /* outgoing stack args: nstack (r8) slots from rcx, keep 16 byte alignment */
    movq %r8, %rax
    shlq $3, %rax
    addq $15, %rax
    andq $-16, %rax
    subq %rax, %rsp
    andq $-16, %rsp
    xorq %r10, %r10
1:  cmpq %r8, %r10
    jge 2f
    movq (%rcx,%r10,8), %r11
    movq %r11, (%rsp,%r10,8)
    incq %r10
    jmp 1b
2:
    movsd 0(%r13), %xmm0
    movsd 8(%r13), %xmm1
    movsd 16(%r13), %xmm2
    movsd 24(%r13), %xmm3
    movsd 32(%r13), %xmm4
    movsd 40(%r13), %xmm5
    movsd 48(%r13), %xmm6
    movsd 56(%r13), %xmm7
    movq 0(%r12), %rdi
    movq 8(%r12), %rsi
    movq 16(%r12), %rdx
    movq 24(%r12), %rcx
    movq 32(%r12), %r8
    movq 40(%r12), %r9
    movl $8, %eax
    call *%rbx
    movq %rax, 0(%r14)
    movsd %xmm0, 8(%r14)
    leaq -32(%rbp), %rsp
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    popq %rbp
    ret
    .size sa_native_call, .-sa_native_call

#else
#error "sa_native_call: unsupported architecture"
#endif

#if defined(__linux__) || defined(__SWITCH__)
    .section .note.GNU-stack,"",%progbits
#endif
