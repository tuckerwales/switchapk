package android.graphics;

/** 3D camera for view transforms: rotations are projected with a simple perspective. */
public class Camera {
    private float mRotX, mRotY, mRotZ, mTx, mTy, mTz;
    private float mLocationZ = -8;
    private final java.util.ArrayList<float[]> mStack = new java.util.ArrayList<float[]>();

    public Camera() {}

    public void save() { mStack.add(new float[] {mRotX, mRotY, mRotZ, mTx, mTy, mTz}); }

    public void restore() {
        float[] s = mStack.remove(mStack.size() - 1);
        mRotX = s[0]; mRotY = s[1]; mRotZ = s[2]; mTx = s[3]; mTy = s[4]; mTz = s[5];
    }

    public void translate(float x, float y, float z) { mTx += x; mTy += y; mTz += z; }
    public void rotateX(float deg) { mRotX += deg; }
    public void rotateY(float deg) { mRotY += deg; }
    public void rotateZ(float deg) { mRotZ += deg; }
    public void rotate(float x, float y, float z) { mRotX += x; mRotY += y; mRotZ += z; }
    public float getLocationX() { return 0; }
    public float getLocationY() { return 0; }
    public float getLocationZ() { return mLocationZ; }
    public void setLocation(float x, float y, float z) { mLocationZ = z; }

    public void getMatrix(Matrix matrix) {
        // affine approximation: x/y rotations shrink the corresponding axis
        matrix.reset();
        float sx = (float) Math.cos(Math.toRadians(mRotY));
        float sy = (float) Math.cos(Math.toRadians(mRotX));
        matrix.setScale(sx, sy);
        matrix.postRotate(-mRotZ);
        float depth = 576f * -mLocationZ / 72f;
        float persp = depth / Math.max(1f, depth + mTz);
        matrix.postScale(persp, persp);
        matrix.postTranslate(mTx, -mTy);
    }

    public void applyToCanvas(Canvas canvas) {
        Matrix m = new Matrix();
        getMatrix(m);
        canvas.concat(m);
    }

    public float dotWithNormal(float dx, float dy, float dz) { return dz; }
}
