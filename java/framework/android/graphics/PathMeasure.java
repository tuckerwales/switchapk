package android.graphics;

import java.util.ArrayList;

public class PathMeasure {
    public static final int POSITION_MATRIX_FLAG = 0x01;
    public static final int TANGENT_MATRIX_FLAG = 0x02;

    private ArrayList<float[]> mContours = new ArrayList<float[]>();
    private ArrayList<float[]> mLengths = new ArrayList<float[]>();
    private ArrayList<Boolean> mClosed = new ArrayList<Boolean>();
    private int mIndex;

    public PathMeasure() {}
    public PathMeasure(Path path, boolean forceClosed) { setPath(path, forceClosed); }

    public void setPath(Path path, boolean forceClosed) {
        mContours.clear();
        mLengths.clear();
        mClosed.clear();
        mIndex = 0;
        if (path == null) return;
        ArrayList<float[]> cs = path.flatten(0.25f);
        for (float[] c : cs) {
            int n = c.length / 2;
            boolean closed = n > 1 && c[0] == c[c.length - 2] && c[1] == c[c.length - 1];
            if (forceClosed && !closed && n > 1) {
                float[] cc = java.util.Arrays.copyOf(c, c.length + 2);
                cc[c.length] = c[0];
                cc[c.length + 1] = c[1];
                c = cc;
                n++;
                closed = true;
            }
            float[] len = new float[n];
            for (int i = 1; i < n; i++) {
                float dx = c[i * 2] - c[i * 2 - 2], dy = c[i * 2 + 1] - c[i * 2 - 1];
                len[i] = len[i - 1] + (float) Math.sqrt(dx * dx + dy * dy);
            }
            mContours.add(c);
            mLengths.add(len);
            mClosed.add(closed);
        }
    }

    public float getLength() {
        if (mIndex >= mLengths.size()) return 0;
        float[] l = mLengths.get(mIndex);
        return l.length == 0 ? 0 : l[l.length - 1];
    }

    public boolean getPosTan(float distance, float[] pos, float[] tan) {
        if (mIndex >= mContours.size()) return false;
        float[] c = mContours.get(mIndex);
        float[] l = mLengths.get(mIndex);
        int n = l.length;
        if (n < 2) return false;
        float total = l[n - 1];
        if (distance < 0) distance = 0;
        if (distance > total) distance = total;
        int i = 1;
        while (i < n - 1 && l[i] < distance) i++;
        float seg = l[i] - l[i - 1];
        float t = seg > 0 ? (distance - l[i - 1]) / seg : 0;
        float x0 = c[i * 2 - 2], y0 = c[i * 2 - 1], x1 = c[i * 2], y1 = c[i * 2 + 1];
        if (pos != null) {
            pos[0] = x0 + (x1 - x0) * t;
            pos[1] = y0 + (y1 - y0) * t;
        }
        if (tan != null) {
            float dx = x1 - x0, dy = y1 - y0;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            tan[0] = d > 0 ? dx / d : 1;
            tan[1] = d > 0 ? dy / d : 0;
        }
        return true;
    }

    public boolean getMatrix(float distance, Matrix matrix, int flags) {
        float[] pos = new float[2], tan = new float[2];
        if (!getPosTan(distance, pos, tan)) return false;
        matrix.reset();
        if ((flags & TANGENT_MATRIX_FLAG) != 0) matrix.setSinCos(tan[1], tan[0]);
        if ((flags & POSITION_MATRIX_FLAG) != 0) matrix.postTranslate(pos[0], pos[1]);
        return true;
    }

    public boolean getSegment(float startD, float stopD, Path dst, boolean startWithMoveTo) {
        if (mIndex >= mContours.size()) return false;
        float total = getLength();
        if (startD < 0) startD = 0;
        if (stopD > total) stopD = total;
        if (startD >= stopD) return false;
        float[] c = mContours.get(mIndex);
        float[] l = mLengths.get(mIndex);
        float[] p = new float[2];
        getPosTan(startD, p, null);
        if (startWithMoveTo) dst.moveTo(p[0], p[1]);
        else dst.lineTo(p[0], p[1]);
        for (int i = 1; i < l.length; i++) {
            if (l[i] > startD && l[i] < stopD) dst.lineTo(c[i * 2], c[i * 2 + 1]);
        }
        getPosTan(stopD, p, null);
        dst.lineTo(p[0], p[1]);
        return true;
    }

    public boolean isClosed() { return mIndex < mClosed.size() && mClosed.get(mIndex); }

    public boolean nextContour() {
        if (mIndex + 1 < mContours.size()) {
            mIndex++;
            return true;
        }
        mIndex = mContours.size();
        return false;
    }
}
