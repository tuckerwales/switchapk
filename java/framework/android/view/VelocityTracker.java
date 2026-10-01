package android.view;

/**
 * Pointer velocity estimation using AOSP's default LSQ2 strategy: a weighted
 * least squares quadratic fit over the last 100 ms of samples (at most 20).
 */
public final class VelocityTracker {
    private static final int HISTORY_SIZE = 20;
    private static final long HORIZON_MS = 100;
    private static final long ASSUME_POINTER_STOPPED_MS = 40;
    private static final int MAX_POINTERS = 32;

    // ring buffer per pointer id: times and positions
    private final long[][] mTimes = new long[MAX_POINTERS][];
    private final float[][] mXs = new float[MAX_POINTERS][];
    private final float[][] mYs = new float[MAX_POINTERS][];
    private final int[] mCount = new int[MAX_POINTERS];
    private final int[] mHead = new int[MAX_POINTERS];
    private long mLastEventTime;
    private int mActivePointerId = -1;

    private final float[] mVx = new float[MAX_POINTERS];
    private final float[] mVy = new float[MAX_POINTERS];

    private VelocityTracker() {}

    public static VelocityTracker obtain() { return new VelocityTracker(); }

    /** framework-internal (hidden in AOSP). */
    public static VelocityTracker obtain(String strategy) { return new VelocityTracker(); }

    public void recycle() { clear(); }

    public boolean isAxisSupported(int axis) { return axis == MotionEvent.AXIS_X || axis == MotionEvent.AXIS_Y; }

    public void clear() {
        for (int i = 0; i < MAX_POINTERS; i++) {
            mCount[i] = 0;
            mVx[i] = 0;
            mVy[i] = 0;
        }
        mActivePointerId = -1;
    }

    private void clearPointer(int id) {
        mCount[id] = 0;
    }

    private void add(int id, long time, float x, float y) {
        if (mTimes[id] == null) {
            mTimes[id] = new long[HISTORY_SIZE];
            mXs[id] = new float[HISTORY_SIZE];
            mYs[id] = new float[HISTORY_SIZE];
        }
        int idx = (mHead[id] + 1) % HISTORY_SIZE;
        if (mCount[id] == 0) idx = 0;
        mHead[id] = idx;
        mTimes[id][idx] = time;
        mXs[id][idx] = x;
        mYs[id][idx] = y;
        if (mCount[id] < HISTORY_SIZE) mCount[id]++;
    }

    public void addMovement(MotionEvent event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_HOVER_ENTER:
                clear();
                break;
            case MotionEvent.ACTION_POINTER_DOWN: {
                int id = event.getPointerId(event.getActionIndex());
                if (id >= 0 && id < MAX_POINTERS) clearPointer(id);
                break;
            }
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE:
                break;
            default:
                // ACTION_UP / ACTION_POINTER_UP repeat the last move position; nothing to add
                return;
        }
        long eventTime = event.getEventTime();
        if (eventTime - mLastEventTime >= ASSUME_POINTER_STOPPED_MS && action != MotionEvent.ACTION_DOWN) {
            for (int i = 0; i < MAX_POINTERS; i++) mCount[i] = 0;
        }
        mLastEventTime = eventTime;
        int n = event.getPointerCount();
        if (mActivePointerId < 0 || event.findPointerIndex(mActivePointerId) < 0) {
            mActivePointerId = event.getPointerId(0);
        }
        int history = event.getHistorySize();
        for (int h = 0; h < history; h++) {
            long t = event.getHistoricalEventTime(h);
            for (int p = 0; p < n; p++) {
                int id = event.getPointerId(p);
                if (id < 0 || id >= MAX_POINTERS) continue;
                add(id, t, event.getHistoricalX(p, h), event.getHistoricalY(p, h));
            }
        }
        for (int p = 0; p < n; p++) {
            int id = event.getPointerId(p);
            if (id < 0 || id >= MAX_POINTERS) continue;
            add(id, eventTime, event.getX(p), event.getY(p));
        }
    }

    public void computeCurrentVelocity(int units) { computeCurrentVelocity(units, Float.MAX_VALUE); }

    public void computeCurrentVelocity(int units, float maxVelocity) {
        for (int id = 0; id < MAX_POINTERS; id++) {
            int count = mCount[id];
            mVx[id] = 0;
            mVy[id] = 0;
            if (count < 2) continue;
            long newest = mTimes[id][mHead[id]];
            float[] t = new float[count];
            float[] xs = new float[count];
            float[] ys = new float[count];
            int m = 0;
            for (int k = 0; k < count; k++) {
                int idx = (mHead[id] - k + HISTORY_SIZE) % HISTORY_SIZE;
                long age = newest - mTimes[id][idx];
                if (age > HORIZON_MS) break;
                t[m] = -age * 0.001f;
                xs[m] = mXs[id][idx];
                ys[m] = mYs[id][idx];
                m++;
            }
            if (m < 2) continue;
            int degree = m >= 3 ? 2 : 1;
            float vx = solveVelocity(t, xs, m, degree);
            float vy = solveVelocity(t, ys, m, degree);
            vx = vx * units / 1000f;
            vy = vy * units / 1000f;
            mVx[id] = Math.max(-maxVelocity, Math.min(maxVelocity, vx));
            mVy[id] = Math.max(-maxVelocity, Math.min(maxVelocity, vy));
        }
    }

    /** Least squares polynomial fit (AOSP solveLeastSquares); returns the first derivative at t = 0 (units/s). */
    private static float solveVelocity(float[] x, float[] y, int m, int degree) {
        int n = degree + 1;
        float[][] a = new float[n][m];
        for (int h = 0; h < m; h++) {
            a[0][h] = 1;
            for (int i = 1; i < n; i++) a[i][h] = a[i - 1][h] * x[h];
        }
        float[][] q = new float[n][m];
        float[][] r = new float[n][n];
        for (int j = 0; j < n; j++) {
            for (int h = 0; h < m; h++) q[j][h] = a[j][h];
            for (int i = 0; i < j; i++) {
                float dot = 0;
                for (int h = 0; h < m; h++) dot += q[j][h] * q[i][h];
                for (int h = 0; h < m; h++) q[j][h] -= dot * q[i][h];
            }
            float norm = 0;
            for (int h = 0; h < m; h++) norm += q[j][h] * q[j][h];
            norm = (float) Math.sqrt(norm);
            if (norm < 0.000001f) return n > 1 && degree > 1 ? solveVelocity(x, y, m, degree - 1) : 0;
            float invNorm = 1.0f / norm;
            for (int h = 0; h < m; h++) q[j][h] *= invNorm;
            for (int i = 0; i < n; i++) {
                float dot = 0;
                for (int h = 0; h < m; h++) dot += q[j][h] * a[i][h];
                r[j][i] = i < j ? 0 : dot;
            }
        }
        float[] b = new float[n];
        for (int i = n - 1; i >= 0; i--) {
            float dot = 0;
            for (int h = 0; h < m; h++) dot += q[i][h] * y[h];
            b[i] = dot;
            for (int j = n - 1; j > i; j--) b[i] -= r[i][j] * b[j];
            b[i] /= r[i][i];
        }
        return b[1];
    }

    public float getXVelocity() { return mActivePointerId >= 0 ? mVx[mActivePointerId] : 0; }
    public float getYVelocity() { return mActivePointerId >= 0 ? mVy[mActivePointerId] : 0; }
    public float getXVelocity(int id) { return id >= 0 && id < MAX_POINTERS ? mVx[id] : 0; }
    public float getYVelocity(int id) { return id >= 0 && id < MAX_POINTERS ? mVy[id] : 0; }

    public float getAxisVelocity(int axis, int id) {
        if (axis == MotionEvent.AXIS_X) return getXVelocity(id);
        if (axis == MotionEvent.AXIS_Y) return getYVelocity(id);
        return 0;
    }

    public float getAxisVelocity(int axis) {
        return getAxisVelocity(axis, mActivePointerId >= 0 ? mActivePointerId : 0);
    }
}
