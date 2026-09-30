package android.graphics;

public class ComposePathEffect extends PathEffect {
    private final PathEffect mOuter, mInner;
    public ComposePathEffect(PathEffect outerpe, PathEffect innerpe) { mOuter = outerpe; mInner = innerpe; }
    @Override
    Path apply(Path src, Paint paint) {
        Path p = mInner.apply(src, paint);
        Path q = mOuter.apply(p != null ? p : src, paint);
        return q != null ? q : p;
    }
}
