/*
 * ANativeWindow over android.view.Surface (WS9). The buffer queue is the
 * same one EGL window surfaces post into (ARCHITECTURE 6.6).
 */
#ifndef SWITCHAPK_NATIVE_WINDOW_H
#define SWITCHAPK_NATIVE_WINDOW_H

#include "ndk_android.h"

#include <stdbool.h>

struct VMThread;
struct Object;

/* True when p is one of our windows (magic). Safe on NULL. */
bool anw_is(const void *p);

/* Wraps a Java Surface. The caller holds one reference. Requires the GIL. */
ANativeWindow *anw_create(struct VMThread *t, struct Object *surface);
void anw_acquire(ANativeWindow *w);
void anw_release(ANativeWindow *w);
int anw_width(ANativeWindow *w);
int anw_height(ANativeWindow *w);
bool anw_opaque(ANativeWindow *w);

/* Copies ARGB (0xAARRGGBB) into the surface and posts it. Requires the GIL.
 * Also remembered so the next lock starts from this frame. */
bool anw_post_argb(ANativeWindow *w, const uint32_t *argb, int width, int height);

/* Updates the size we report. Does not reallocate the Java queue (the
 * SurfaceView owns that). Drops a remembered frame of a different size. */
void anw_note_size(ANativeWindow *w, int width, int height);

#endif
