package android.graphics;

public class PorterDuffXfermode extends Xfermode {
    public PorterDuffXfermode(PorterDuff.Mode mode) { porterDuffMode = mode.nativeInt; }
}
