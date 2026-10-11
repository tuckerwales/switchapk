package android.hardware.display;

import android.view.Display;
import android.view.Surface;

/** A virtual display. DisplayManager never creates one here (see DisplayManager). */
public final class VirtualDisplay {
    private final Display mDisplay;
    private Surface mSurface;

    VirtualDisplay(Display display, Surface surface) {
        mDisplay = display;
        mSurface = surface;
    }

    public Display getDisplay() { return mDisplay; }
    public Surface getSurface() { return mSurface; }
    public void setSurface(Surface surface) { mSurface = surface; }
    public void resize(int width, int height, int densityDpi) {}
    public void release() {}

    public String toString() {
        return "VirtualDisplay{display=" + mDisplay + ", surface=" + mSurface + "}";
    }

    public static abstract class Callback {
        public Callback() {}
        public void onPaused() {}
        public void onResumed() {}
        public void onStopped() {}
    }
}
