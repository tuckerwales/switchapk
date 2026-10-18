/*
 * Split APK bundles: .apkm (APKMirror), .apks (SAI, bundletool) and .xapk (APKPure) are zips holding a base
 * APK and its config splits (ABI, density, language). The VM loads one APK, so a bundle is merged once into
 * a plain APK: the base, plus the lib/ and assets/ entries of the splits (as tools/corpus.py import does).
 * Density and language splits only add resources the base already has at a default configuration, so their
 * res/ and resources.arsc are left out. Compressed data is copied as is, nothing is recompressed.
 */
#ifndef SWITCHAPK_BUNDLE_H
#define SWITCHAPK_BUNDLE_H

#include "../core/common.h"

/* True when path ends in .apkm, .apks or .xapk (any case). */
bool bundle_is_bundle(const char *path);

/* True for anything the launcher can run: .apk or a bundle. */
bool bundle_is_runnable(const char *path);

/* For a bundle, the path of its merged APK, built on first use and cached as
 * <bundle dir>/.switchapk-bundles/<bundle name>.apk (rebuilt when the bundle is newer). For any other path, a
 * copy of path. Returns NULL on failure with a reason in err (encrypted bundle, no base APK, disk full). The
 * result is malloc'd. */
char *bundle_resolve(const char *path, char *err, size_t errlen);

#endif
