#ifndef SWITCHAPK_ZIP_H
#define SWITCHAPK_ZIP_H

#include "common.h"

typedef struct {
    char *name;
    uint32_t local_offset;
    uint32_t comp_size;
    uint32_t uncomp_size;
    uint16_t method; /* 0 = stored, 8 = deflate */
    uint32_t crc;
    uint16_t flags;            /* general purpose bits: bit 0 = encrypted, bit 3 = sizes in a data descriptor */
    uint16_t mod_time, mod_date; /* DOS format */
} ZipEntry;

typedef struct ZipArchive ZipArchive;

/* Opens a zip archive backed by a file (read lazily). */
ZipArchive *zip_open(const char *path);
/* Opens a zip archive backed by a memory buffer (not copied, must outlive the archive). */
ZipArchive *zip_open_memory(const uint8_t *data, size_t len);
void zip_close(ZipArchive *z);

size_t zip_count(const ZipArchive *z);
const ZipEntry *zip_entry_at(const ZipArchive *z, size_t i);
const ZipEntry *zip_find(const ZipArchive *z, const char *name);

/* Extracts an entry into a freshly malloc'd buffer (NUL terminated for convenience). */
uint8_t *zip_extract(ZipArchive *z, const ZipEntry *e, size_t *out_len);
uint8_t *zip_extract_name(ZipArchive *z, const char *name, size_t *out_len);
/* Returns absolute offset of the entry data within the archive file if stored
 * uncompressed (used for mmap-like direct access), or -1. */
int64_t zip_stored_data_offset(ZipArchive *z, const ZipEntry *e);

/* Inflates (or copies) an entry into f in chunks, so large entries need no buffer of their size. */
bool zip_extract_to_file(ZipArchive *z, const ZipEntry *e, FILE *f);

/* Writes a new archive from entries of existing ones, copying their compressed data as is (no recompression).
 * Stored entries are 4-byte aligned like zipalign. Archives over 4 GiB are refused. */
typedef struct ZipWriter ZipWriter;
ZipWriter *zipw_create(const char *path);
bool zipw_copy_entry(ZipWriter *w, ZipArchive *src, const ZipEntry *e);
/* Writes the central directory and closes the file; returns false if any step failed. Frees w. */
bool zipw_finish(ZipWriter *w);

#endif
