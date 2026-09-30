package android.graphics.drawable;

import android.graphics.drawable.shapes.RoundRectShape;

public class PaintDrawable extends ShapeDrawable {
    public PaintDrawable() {}

    public PaintDrawable(int color) { getPaint().setColor(color); }

    public void setCornerRadius(float radius) {
        float[] radii = null;
        if (radius > 0) {
            radii = new float[8];
            for (int i = 0; i < 8; i++) radii[i] = radius;
        }
        setCornerRadii(radii);
    }

    public void setCornerRadii(float[] radii) {
        if (radii == null) {
            if (getShape() != null) setShape(null);
        } else {
            setShape(new RoundRectShape(radii, null, null));
        }
        invalidateSelf();
    }
}
