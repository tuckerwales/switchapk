package android.view.animation;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.InflateException;

/**
 * Maps time through a path from (0, 0) to (1, 1) whose x never decreases.
 * The path is sampled with {@link Path#approximate}; y is linearly interpolated between samples.
 */
public class PathInterpolator extends BaseInterpolator {
    private static final float PRECISION = 0.002f;
    private float[] mX;
    private float[] mY;

    public PathInterpolator(Path path) {
        initPath(path);
    }

    public PathInterpolator(float controlX, float controlY) {
        initQuad(controlX, controlY);
    }

    public PathInterpolator(float controlX1, float controlY1, float controlX2, float controlY2) {
        initCubic(controlX1, controlY1, controlX2, controlY2);
    }

    public PathInterpolator(Context context, AttributeSet attrs) {
        this(context.getResources(), context.getTheme(), attrs);
    }

    /** framework-internal. AnimationUtils loads interpolator XML without a Context. */
    PathInterpolator(Resources res, Resources.Theme theme, AttributeSet attrs) {
        int[] style = new int[] {android.R.attr.pathData, android.R.attr.controlX1, android.R.attr.controlY1,
                android.R.attr.controlX2, android.R.attr.controlY2};
        TypedArray a = theme != null ? theme.obtainStyledAttributes(attrs, style, 0, 0)
                : res.obtainAttributes(attrs, style);
        try {
            parseInterpolator(a);
        } finally {
            a.recycle();
        }
    }

    private void parseInterpolator(TypedArray a) {
        if (a.hasValue(0)) {
            String pathData = a.getString(0);
            Path path = android.util.PathParser.createPathFromPathData(pathData);
            if (path == null) throw new InflateException("The path is null, which is created from " + pathData);
            initPath(path);
            return;
        }
        if (!a.hasValue(1)) throw new InflateException("pathInterpolator requires the controlX1 attribute");
        if (!a.hasValue(2)) throw new InflateException("pathInterpolator requires the controlY1 attribute");
        float x1 = a.getFloat(1, 0f);
        float y1 = a.getFloat(2, 0f);
        boolean hasX2 = a.hasValue(3);
        if (hasX2 != a.hasValue(4)) {
            throw new InflateException("pathInterpolator requires both controlX2 and controlY2 for cubic Beziers.");
        }
        if (!hasX2) initQuad(x1, y1);
        else initCubic(x1, y1, a.getFloat(3, 0f), a.getFloat(4, 0f));
    }

    private void initQuad(float controlX, float controlY) {
        Path path = new Path();
        path.moveTo(0, 0);
        path.quadTo(controlX, controlY, 1f, 1f);
        initPath(path);
    }

    private void initCubic(float x1, float y1, float x2, float y2) {
        Path path = new Path();
        path.moveTo(0, 0);
        path.cubicTo(x1, y1, x2, y2, 1f, 1f);
        initPath(path);
    }

    private void initPath(Path path) {
        float[] points = path == null ? new float[0] : path.approximate(PRECISION);
        int numPoints = points.length / 3;
        if (numPoints < 2 || points[1] != 0 || points[2] != 0 || points[points.length - 2] != 1
                || points[points.length - 1] != 1) {
            throw new IllegalArgumentException("The Path must start at (0,0) and end at (1,1)");
        }
        mX = new float[numPoints];
        mY = new float[numPoints];
        float prevX = 0;
        float prevFraction = 0;
        for (int i = 0; i < numPoints; i++) {
            float fraction = points[i * 3];
            float x = points[i * 3 + 1];
            float y = points[i * 3 + 2];
            if (fraction == prevFraction && x != prevX) {
                throw new IllegalArgumentException("The Path cannot have discontinuity in the X axis.");
            }
            if (x < prevX) throw new IllegalArgumentException("The Path cannot loop back on itself.");
            mX[i] = x;
            mY[i] = y;
            prevX = x;
            prevFraction = fraction;
        }
    }

    public float getInterpolation(float t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        int start = 0;
        int end = mX.length - 1;
        while (end - start > 1) {
            int mid = (start + end) / 2;
            if (t < mX[mid]) end = mid;
            else start = mid;
        }
        float xRange = mX[end] - mX[start];
        if (xRange == 0) return mY[start];
        float fraction = (t - mX[start]) / xRange;
        return mY[start] + fraction * (mY[end] - mY[start]);
    }
}
