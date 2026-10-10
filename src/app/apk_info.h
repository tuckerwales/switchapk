/*
 * Launcher identity of an APK: the label and bitmap icon, read from the
 * zip with no VM. Shared by the Switch launcher and the host --apk-info check.
 */
#ifndef SWITCHAPK_APK_INFO_H
#define SWITCHAPK_APK_INFO_H

#include "../core/common.h"

/* Handheld density, and the host test screen. Docked 1080p is a later slice. */
#define APK_ICON_DENSITY 240

typedef struct {
    char *label;    /* never NULL after apk_read_identity; owned */
    uint32_t *icon; /* ARGB8888, or NULL; owned */
    int icon_w;
    int icon_h;
    char *package; /* manifest package, or NULL; owned */
    char *version; /* android:versionName, or NULL; owned */
} ApkIdentity;

/* The launcher activity (MAIN + LAUNCHER) wins over <application>. density_dpi
 * selects the drawable bucket; 0 keeps APK_ICON_DENSITY. A missing label becomes
 * the file name without .apk. Returns false only when the file cannot be opened;
 * label is still set in that case. XML icons are not decoded. */
bool apk_read_identity(const char *path, int density_dpi, ApkIdentity *out);
void apk_identity_free(ApkIdentity *id);

#endif
