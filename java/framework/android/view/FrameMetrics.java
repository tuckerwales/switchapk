package android.view;

/** Per-frame timing. switchapk does not report frame metrics; values are zero. */
public final class FrameMetrics {
    public static final int ANIMATION_DURATION = 2;
    public static final int COMMAND_ISSUE_DURATION = 6;
    public static final int DEADLINE = 13;
    public static final int DRAW_DURATION = 4;
    public static final int FIRST_DRAW_FRAME = 9;
    public static final int GPU_DURATION = 12;
    public static final int INPUT_HANDLING_DURATION = 1;
    public static final int INTENDED_VSYNC_TIMESTAMP = 10;
    public static final int LAYOUT_MEASURE_DURATION = 3;
    public static final int SWAP_BUFFERS_DURATION = 7;
    public static final int SYNC_DURATION = 5;
    public static final int TOTAL_DURATION = 8;
    public static final int UNKNOWN_DELAY_DURATION = 0;
    public static final int VSYNC_TIMESTAMP = 11;

    private final long[] mMetrics = new long[16];

    FrameMetrics() {}

    public FrameMetrics(FrameMetrics other) { System.arraycopy(other.mMetrics, 0, mMetrics, 0, mMetrics.length); }

    public long getMetric(int id) { return id >= 0 && id < mMetrics.length ? mMetrics[id] : -1; }
}
