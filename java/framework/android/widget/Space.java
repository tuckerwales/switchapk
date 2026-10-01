package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

/**
 * A lightweight blank gap (AOSP Space). It takes room in the layout and draws nothing.
 * A visible Space is made invisible so a background does not show.
 */
public final class Space extends View {
    public Space(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (getVisibility() == VISIBLE) setVisibility(INVISIBLE);
    }

    public Space(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public Space(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public Space(Context context) { this(context, null); }

    @Override
    public void draw(Canvas canvas) {}

    private static int defaultSize(int size, int measureSpec) {
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.AT_MOST:
                return Math.min(size, specSize);
            case MeasureSpec.EXACTLY:
                return specSize;
            case MeasureSpec.UNSPECIFIED:
            default:
                return size;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(defaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                defaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    }
}
