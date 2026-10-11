/*
 * Linux (bionic) <-> FreeBSD/newlib socket ABI translations for the Switch shim (shim_bsd.c). Pure functions, built
 * on every target for tests/c/shim_bsd_test.c.
 */
#ifndef SWITCHAPK_SHIM_BSD_H
#define SWITCHAPK_SHIM_BSD_H

#include <stdint.h>

#define SBSD_SOCKADDR_MAX 128 /* sockaddr_storage */

int sbsd_errno_to_linux(int newlib_errno);
int sbsd_errno_from_linux(int linux_errno);
int sbsd_af_to_bsd(int af);
int sbsd_af_from_bsd(int af);
/* 0, or -1 for a length out of range */
int sbsd_sockaddr_to_bsd(const void *lin, uint32_t len, void *out, uint32_t *out_len);
void sbsd_sockaddr_from_bsd(const void *bsd, uint32_t blen, void *lin, uint32_t *lin_len);
int sbsd_socktype_to_bsd(int type);
/* 0 with the BSD level and option, or -1 when there is no equivalent */
int sbsd_sockopt_to_bsd(int level, int opt, int *blevel, int *bopt);
int sbsd_msgflags_to_bsd(int flags);
int sbsd_oflags_to_newlib(int flags);
int sbsd_oflags_from_newlib(int flags);
/* 0 when there is no equivalent */
unsigned long sbsd_ioctl_to_bsd(unsigned long req);
int sbsd_fdset_isset(const void *set, int fd);
void sbsd_fdset_set(void *set, int fd);

#endif
