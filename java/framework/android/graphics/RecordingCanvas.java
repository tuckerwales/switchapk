package android.graphics;

/** Canvas returned by RenderNode.beginRecording (draws into the node's picture bitmap). */
public final class RecordingCanvas extends Canvas {
    RecordingCanvas(Canvas target) {
        super();
        mPixels = target.mPixels;
        mWidth = target.mWidth;
        mHeight = target.mHeight;
        mClip = new int[] {0, 0, mWidth, mHeight};
    }
}
