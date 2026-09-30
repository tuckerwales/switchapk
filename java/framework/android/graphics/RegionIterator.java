package android.graphics;

public class RegionIterator {
    private final Rect mBounds;
    private boolean mDone;
    public RegionIterator(Region region) { mBounds = region.getBounds(); mDone = mBounds.isEmpty(); }
    public final boolean next(Rect r) {
        if (mDone) return false;
        r.set(mBounds);
        mDone = true;
        return true;
    }
}
