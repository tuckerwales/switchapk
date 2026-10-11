package android.hardware.display;

import android.os.Parcel;
import android.os.Parcelable;

/** How HDR content is converted for the display (AOSP HdrConversionMode). */
public final class HdrConversionMode implements Parcelable {
    public static final int HDR_CONVERSION_UNSUPPORTED = 0;
    public static final int HDR_CONVERSION_PASSTHROUGH = 1;
    public static final int HDR_CONVERSION_SYSTEM = 2;
    public static final int HDR_CONVERSION_FORCE = 3;

    private final int mConversionMode;
    private final int mPreferredHdrOutputType;

    public HdrConversionMode(int conversionMode, int preferredHdrOutputType) {
        if ((conversionMode == HDR_CONVERSION_PASSTHROUGH || conversionMode == HDR_CONVERSION_UNSUPPORTED)
                && preferredHdrOutputType != -1) {
            throw new IllegalArgumentException("preferredHdrOutputType must not be set if"
                    + " the conversion mode is " + conversionMode);
        }
        mConversionMode = conversionMode;
        mPreferredHdrOutputType = preferredHdrOutputType;
    }

    public HdrConversionMode(int conversionMode) {
        mConversionMode = conversionMode;
        mPreferredHdrOutputType = conversionMode == HDR_CONVERSION_SYSTEM ? 0 : -1;
    }

    private HdrConversionMode(Parcel source) {
        this(source.readInt(), source.readInt());
    }

    public int getConversionMode() { return mConversionMode; }
    public int getPreferredHdrOutputType() { return mPreferredHdrOutputType; }
    public int describeContents() { return 0; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mConversionMode);
        dest.writeInt(mPreferredHdrOutputType);
    }

    public boolean equals(Object o) {
        return o instanceof HdrConversionMode && mConversionMode == ((HdrConversionMode) o).mConversionMode
                && mPreferredHdrOutputType == ((HdrConversionMode) o).mPreferredHdrOutputType;
    }

    public int hashCode() { return 31 * mConversionMode + mPreferredHdrOutputType; }

    public String toString() {
        return "HdrConversionMode{ConversionMode=" + mConversionMode
                + ", PreferredHdrOutputType=" + mPreferredHdrOutputType + "}";
    }

    public static final Parcelable.Creator<HdrConversionMode> CREATOR = new Parcelable.Creator<HdrConversionMode>() {
        public HdrConversionMode createFromParcel(Parcel source) { return new HdrConversionMode(source); }
        public HdrConversionMode[] newArray(int size) { return new HdrConversionMode[size]; }
    };
}
