package android.view.contentcapture;

import android.os.ParcelFileDescriptor;

/** Adapter class used by apps to share data with the content capture service. */
public interface DataShareWriteAdapter {
    void onWrite(ParcelFileDescriptor destination);

    void onRejected();

    default void onError(int errorCode) {}
}
