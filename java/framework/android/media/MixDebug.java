package android.media;

/**
 * framework-internal. Host tests read which sources have contributed a
 * non-silent sample and how many output frames were above the noise floor.
 * The mask sticks for the process; it is not cleared between reads.
 */
public final class MixDebug {
    public static final int SRC_MEDIA = 1;
    public static final int SRC_POOL = 2;
    public static final int SRC_TRACK = 4;
    public static final int SRC_OPENSL = 8;
    public static final int SRC_TONE = 16;

    private MixDebug() {}

    public static native int getSourceMask();
    public static native int getNonZeroFrames();
}
