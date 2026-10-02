package android.view.animation;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

/**
 * Staggers a grid by column and row. ViewGroup's default parameters are a flat index; a grid
 * uses {@link AnimationParameters} with column and row counts.
 */
public class GridLayoutAnimationController extends LayoutAnimationController {
    public static final int DIRECTION_LEFT_TO_RIGHT = 0x0;
    public static final int DIRECTION_RIGHT_TO_LEFT = 0x1;
    public static final int DIRECTION_TOP_TO_BOTTOM = 0x0;
    public static final int DIRECTION_BOTTOM_TO_TOP = 0x2;
    public static final int DIRECTION_HORIZONTAL_MASK = 0x1;
    public static final int DIRECTION_VERTICAL_MASK = 0x2;
    public static final int PRIORITY_NONE = 0;
    public static final int PRIORITY_COLUMN = 1;
    public static final int PRIORITY_ROW = 2;

    private float mColumnDelay;
    private float mRowDelay;
    private int mDirection;
    private int mDirectionPriority;

    public GridLayoutAnimationController(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.columnDelay, android.R.attr.rowDelay, android.R.attr.direction,
                android.R.attr.directionPriority});
        mColumnDelay = a.getFloat(0, getDelay());
        mRowDelay = a.getFloat(1, getDelay());
        mDirection = a.getInt(2, DIRECTION_LEFT_TO_RIGHT | DIRECTION_TOP_TO_BOTTOM);
        mDirectionPriority = a.getInt(3, PRIORITY_NONE);
        a.recycle();
    }

    public GridLayoutAnimationController(Animation animation) { this(animation, 0.5f, 0.5f); }

    public GridLayoutAnimationController(Animation animation, float columnDelay, float rowDelay) {
        super(animation);
        mColumnDelay = columnDelay;
        mRowDelay = rowDelay;
    }

    public float getColumnDelay() { return mColumnDelay; }

    public void setColumnDelay(float columnDelay) { mColumnDelay = columnDelay; }

    public float getRowDelay() { return mRowDelay; }

    public void setRowDelay(float rowDelay) { mRowDelay = rowDelay; }

    public int getDirection() { return mDirection; }

    public void setDirection(int direction) { mDirection = direction; }

    public int getDirectionPriority() { return mDirectionPriority; }

    public void setDirectionPriority(int directionPriority) { mDirectionPriority = directionPriority; }

    @Override
    public boolean willOverlap() { return mColumnDelay < 1.0f || mRowDelay < 1.0f; }

    @Override
    protected long getDelayForView(View view) {
        android.view.ViewGroup.LayoutParams lp = view.getLayoutParams();
        AnimationParameters params = lp != null && lp.layoutAnimationParameters instanceof AnimationParameters
                ? (AnimationParameters) lp.layoutAnimationParameters : null;
        if (params == null || mAnimation == null) return super.getDelayForView(view);
        int column = transformedColumn(params);
        int row = transformedRow(params);
        float columnDelay = mColumnDelay * mAnimation.getDuration();
        float rowDelay = mRowDelay * mAnimation.getDuration();
        long viewDelay;
        float totalDelay;
        if (mDirectionPriority == PRIORITY_COLUMN) {
            viewDelay = (long) (row * rowDelay + column * params.rowsCount * rowDelay);
            totalDelay = params.rowsCount * rowDelay + params.columnsCount * params.rowsCount * rowDelay;
        } else if (mDirectionPriority == PRIORITY_ROW) {
            viewDelay = (long) (column * columnDelay + row * params.columnsCount * columnDelay);
            totalDelay = params.columnsCount * columnDelay + params.rowsCount * params.columnsCount * columnDelay;
        } else {
            viewDelay = (long) (column * columnDelay + row * rowDelay);
            totalDelay = params.columnsCount * columnDelay + params.rowsCount * rowDelay;
        }
        if (totalDelay <= 0f) return viewDelay;
        Interpolator interp = mInterpolator != null ? mInterpolator : new LinearInterpolator();
        float normalized = interp.getInterpolation(viewDelay / totalDelay);
        return (long) (normalized * totalDelay);
    }

    private int transformedColumn(AnimationParameters params) {
        int index = params.column;
        if ((mDirection & DIRECTION_HORIZONTAL_MASK) == DIRECTION_RIGHT_TO_LEFT) {
            index = params.columnsCount - 1 - index;
        }
        return index;
    }

    private int transformedRow(AnimationParameters params) {
        int index = params.row;
        if ((mDirection & DIRECTION_VERTICAL_MASK) == DIRECTION_BOTTOM_TO_TOP) {
            index = params.rowsCount - 1 - index;
        }
        return index;
    }

    public static class AnimationParameters extends LayoutAnimationController.AnimationParameters {
        public int column;
        public int row;
        public int columnsCount;
        public int rowsCount;

        public AnimationParameters() {}
    }
}
