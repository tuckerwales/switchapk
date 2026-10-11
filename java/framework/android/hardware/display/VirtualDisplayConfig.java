package android.hardware.display;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.Surface;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Parameters for DisplayManager.createVirtualDisplay (AOSP VirtualDisplayConfig). */
public final class VirtualDisplayConfig implements Parcelable {
    private String mName;
    private int mWidth;
    private int mHeight;
    private int mDensityDpi;
    private int mFlags;
    private Surface mSurface;
    private Set<String> mDisplayCategories = Collections.emptySet();
    private float mRequestedRefreshRate;

    VirtualDisplayConfig() {}

    public String getName() { return mName; }
    public int getWidth() { return mWidth; }
    public int getHeight() { return mHeight; }
    public int getDensityDpi() { return mDensityDpi; }
    public int getFlags() { return mFlags; }
    public Surface getSurface() { return mSurface; }
    public Set<String> getDisplayCategories() { return Collections.unmodifiableSet(mDisplayCategories); }
    public float getRequestedRefreshRate() { return mRequestedRefreshRate; }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeInt(mWidth);
        dest.writeInt(mHeight);
        dest.writeInt(mDensityDpi);
        dest.writeInt(mFlags);
        dest.writeInt(mDisplayCategories.size());
        for (String c : mDisplayCategories) dest.writeString(c);
        dest.writeFloat(mRequestedRefreshRate);
    }

    public int describeContents() { return 0; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VirtualDisplayConfig)) return false;
        VirtualDisplayConfig that = (VirtualDisplayConfig) o;
        return Objects.equals(mName, that.mName) && mWidth == that.mWidth && mHeight == that.mHeight
                && mDensityDpi == that.mDensityDpi && mFlags == that.mFlags
                && Objects.equals(mSurface, that.mSurface)
                && Objects.equals(mDisplayCategories, that.mDisplayCategories)
                && mRequestedRefreshRate == that.mRequestedRefreshRate;
    }

    public int hashCode() {
        return Objects.hash(mName, mWidth, mHeight, mDensityDpi, mFlags, mSurface, mDisplayCategories,
                mRequestedRefreshRate);
    }

    public String toString() {
        return "VirtualDisplayConfig(" + " mName=" + mName + " mHeight=" + mHeight + " mWidth=" + mWidth
                + " mDensityDpi=" + mDensityDpi + " mFlags=" + mFlags + " mSurface=" + mSurface
                + " mDisplayCategories=" + mDisplayCategories + " mRequestedRefreshRate=" + mRequestedRefreshRate + ")";
    }

    public static final Parcelable.Creator<VirtualDisplayConfig> CREATOR = new Parcelable.Creator<VirtualDisplayConfig>() {
        public VirtualDisplayConfig createFromParcel(Parcel in) {
            VirtualDisplayConfig c = new VirtualDisplayConfig();
            c.mName = in.readString();
            c.mWidth = in.readInt();
            c.mHeight = in.readInt();
            c.mDensityDpi = in.readInt();
            c.mFlags = in.readInt();
            int n = in.readInt();
            Set<String> categories = new HashSet<String>();
            for (int i = 0; i < n; i++) categories.add(in.readString());
            c.mDisplayCategories = categories;
            c.mRequestedRefreshRate = in.readFloat();
            return c;
        }

        public VirtualDisplayConfig[] newArray(int size) { return new VirtualDisplayConfig[size]; }
    };

    public static final class Builder {
        private final String mName;
        private final int mWidth;
        private final int mHeight;
        private final int mDensityDpi;
        private int mFlags;
        private Surface mSurface;
        private final Set<String> mDisplayCategories = new HashSet<String>();
        private float mRequestedRefreshRate;

        public Builder(String name, int width, int height, int densityDpi) {
            if (name == null) throw new NullPointerException("Virtual display name is required");
            if (width <= 0) throw new IllegalArgumentException("Virtual display width must be positive");
            if (height <= 0) throw new IllegalArgumentException("Virtual display height must be positive");
            if (densityDpi <= 0) throw new IllegalArgumentException("Virtual display density must be positive");
            mName = name;
            mWidth = width;
            mHeight = height;
            mDensityDpi = densityDpi;
        }

        public Builder setFlags(int flags) { mFlags = flags; return this; }
        public Builder setSurface(Surface surface) { mSurface = surface; return this; }

        public Builder setDisplayCategories(Set<String> displayCategories) {
            mDisplayCategories.clear();
            mDisplayCategories.addAll(Objects.requireNonNull(displayCategories));
            return this;
        }

        public Builder addDisplayCategory(String displayCategory) {
            mDisplayCategories.add(Objects.requireNonNull(displayCategory));
            return this;
        }

        public Builder setRequestedRefreshRate(float requestedRefreshRate) {
            if (requestedRefreshRate < 0.0f) throw new IllegalArgumentException("Refresh rate must be non-negative");
            mRequestedRefreshRate = requestedRefreshRate;
            return this;
        }

        public VirtualDisplayConfig build() {
            VirtualDisplayConfig c = new VirtualDisplayConfig();
            c.mName = mName;
            c.mWidth = mWidth;
            c.mHeight = mHeight;
            c.mDensityDpi = mDensityDpi;
            c.mFlags = mFlags;
            c.mSurface = mSurface;
            c.mDisplayCategories = new HashSet<String>(mDisplayCategories);
            c.mRequestedRefreshRate = mRequestedRefreshRate;
            return c;
        }
    }
}
