package android.graphics;

public class Interpolator {
    public enum Result { NORMAL, FREEZE_START, FREEZE_END }
    private final int mValueCount;
    public Interpolator(int valueCount) { mValueCount = valueCount; }
    public Interpolator(int valueCount, int frameCount) { mValueCount = valueCount; }
    public void reset(int valueCount) {}
    public void reset(int valueCount, int frameCount) {}
    public final int getKeyFrameCount() { return 0; }
    public final int getValueCount() { return mValueCount; }
    public void setKeyFrame(int index, int msec, float[] values) {}
    public void setKeyFrame(int index, int msec, float[] values, float[] blend) {}
    public void setRepeatMirror(float repeatCount, boolean mirror) {}
    public Result timeToValues(float[] values) { return Result.NORMAL; }
    public Result timeToValues(int msec, float[] values) { return Result.NORMAL; }
}
