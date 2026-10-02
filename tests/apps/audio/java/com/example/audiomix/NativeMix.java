package com.example.audiomix;

/** OpenSL ES buffer-queue player. Implemented in libopenslmix.so. */
public final class NativeMix {
    private NativeMix() {}

    public static native boolean start();
}
