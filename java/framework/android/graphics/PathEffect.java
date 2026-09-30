package android.graphics;

public class PathEffect {
    /** Returns the path to render instead of src (null = unchanged). */
    Path apply(Path src, Paint paint) { return null; }
}
