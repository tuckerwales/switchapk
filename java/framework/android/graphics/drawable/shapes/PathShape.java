package android.graphics.drawable.shapes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

public class PathShape extends Shape {
    private final float mStdWidth;
    private final float mStdHeight;
    private Path mPath;
    private float mScaleX;
    private float mScaleY;

    public PathShape(Path path, float stdWidth, float stdHeight) {
        mPath = path;
        mStdWidth = stdWidth;
        mStdHeight = stdHeight;
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        canvas.save();
        canvas.scale(mScaleX, mScaleY);
        canvas.drawPath(mPath, paint);
        canvas.restore();
    }

    @Override
    protected void onResize(float width, float height) {
        mScaleX = width / mStdWidth;
        mScaleY = height / mStdHeight;
    }

    @Override
    public PathShape clone() throws CloneNotSupportedException {
        final PathShape shape = (PathShape) super.clone();
        shape.mPath = new Path(mPath);
        return shape;
    }
}
