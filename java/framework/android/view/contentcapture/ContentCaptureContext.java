package android.view.contentcapture;

import android.content.LocusId;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** Context associated with a {@link ContentCaptureSession}. Client-side part ported from AOSP. */
public final class ContentCaptureContext implements Parcelable {
    private final LocusId mId;
    private final Bundle mExtras;

    private ContentCaptureContext(Builder builder) {
        mId = builder.mId;
        mExtras = builder.mExtras;
    }

    public Bundle getExtras() { return mExtras; }

    public LocusId getLocusId() { return mId; }

    public static ContentCaptureContext forLocusId(String id) {
        return new Builder(new LocusId(id)).build();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Context[");
        builder.append("id=").append(mId);
        // AOSP does not print the extras because they could contain PII.
        if (mExtras != null) builder.append(", hasExtras");
        return builder.append(']').toString();
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeParcelable(mId, flags);
        parcel.writeBundle(mExtras);
    }

    public static final Parcelable.Creator<ContentCaptureContext> CREATOR =
            new Parcelable.Creator<ContentCaptureContext>() {
        @Override
        public ContentCaptureContext createFromParcel(Parcel parcel) {
            LocusId id = parcel.readParcelable(null);
            Bundle extras = parcel.readBundle();
            Builder builder = new Builder(id);
            if (extras != null) builder.setExtras(extras);
            return builder.build();
        }

        @Override
        public ContentCaptureContext[] newArray(int size) { return new ContentCaptureContext[size]; }
    };

    /** Builder for {@link ContentCaptureContext}. */
    public static final class Builder {
        private Bundle mExtras;
        private final LocusId mId;
        private boolean mDestroyed;

        public Builder(LocusId id) {
            if (id == null) throw new NullPointerException("id");
            mId = id;
        }

        public Builder setExtras(Bundle extras) {
            if (extras == null) throw new NullPointerException("extras");
            throwIfDestroyed();
            mExtras = extras;
            return this;
        }

        public ContentCaptureContext build() {
            throwIfDestroyed();
            mDestroyed = true;
            return new ContentCaptureContext(this);
        }

        private void throwIfDestroyed() {
            if (mDestroyed) throw new IllegalStateException("Already called #build()");
        }
    }
}
