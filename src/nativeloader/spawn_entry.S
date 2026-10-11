/*
 * nl_enter_entry(entry, sp): enters an executable's _start with sp pointing at a kernel-style
 * initial stack (argc, argv, envp, auxv), as the kernel does after execve. Never returns.
 */
    .text
    .globl nl_enter_entry
#if defined(__x86_64__)
    .type nl_enter_entry, @function
nl_enter_entry:
    mov %rsi, %rsp
    xor %ebp, %ebp
    xor %edx, %edx      /* no rtld_fini */
    jmp *%rdi
#elif defined(__aarch64__)
#ifdef __ELF__
    .type nl_enter_entry, %function
#endif
nl_enter_entry:
    mov sp, x1
    mov x29, xzr
    mov x30, xzr
    br x0
#endif

#if defined(__linux__) && defined(__ELF__)
    .section .note.GNU-stack, "", %progbits
#endif
