package android.view;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/** Software surfaces are not backed by a buffer yet. TODO(WS8) */
public class Surface implements Parcelable {
    private static final String TAG = "Surface";
    private static boolean sLoggedLock;
    private boolean mReleased;

    public Surface(SurfaceTexture surfaceTexture) {}

    public void release() { mReleased = true; }
    public boolean isValid() { return false; }

    public Canvas lockCanvas(Rect dirty) throws OutOfResourcesException {
        if (!sLoggedLock) {
            sLoggedLock = true;
            Log.w(TAG, "lockCanvas is not implemented");
        }
        return null;
    }

    public void unlockCanvasAndPost(Canvas canvas) {}
    public void unlockCanvas(Canvas canvas) {}

    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(mReleased ? 1 : 0); }

    public static final Creator<Surface> CREATOR = new Creator<Surface>() {
        public Surface createFromParcel(Parcel source) {
            Surface s = new Surface(null);
            if (source.readInt() != 0) s.mReleased = true;
            return s;
        }
        public Surface[] newArray(int size) { return new Surface[size]; }
    };

    public static class OutOfResourcesException extends RuntimeException {
        public OutOfResourcesException() {}
        public OutOfResourcesException(String name) { super(name); }
    }
}
