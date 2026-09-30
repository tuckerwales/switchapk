package android.graphics;

public class DashPathEffect extends PathEffect {
    private final float[] mIntervals;
    private final float mPhase;

    public DashPathEffect(float[] intervals, float phase) {
        if (intervals.length < 2) throw new ArrayIndexOutOfBoundsException();
        mIntervals = intervals.clone();
        mPhase = phase;
    }

    @Override
    Path apply(Path src, Paint paint) {
        float total = 0;
        for (float f : mIntervals) total += f;
        if (total <= 0) return null;
        Path dst = new Path();
        PathMeasure pm = new PathMeasure(src, false);
        do {
            float len = pm.getLength();
            float d = -(mPhase % total);
            int idx = 0;
            while (d < len) {
                float seg = mIntervals[idx];
                if (idx % 2 == 0) {
                    float a = Math.max(d, 0), b = Math.min(d + seg, len);
                    if (b > a) pm.getSegment(a, b, dst, true);
                }
                d += seg;
                idx = (idx + 1) % mIntervals.length;
            }
        } while (pm.nextContour());
        return dst;
    }
}
