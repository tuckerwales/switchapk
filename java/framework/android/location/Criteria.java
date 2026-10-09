package android.location;

import android.os.Parcel;
import android.os.Parcelable;

public class Criteria implements Parcelable {
    public static final int NO_REQUIREMENT = 0;
    public static final int POWER_LOW = 1;
    public static final int POWER_MEDIUM = 2;
    public static final int POWER_HIGH = 3;
    public static final int ACCURACY_FINE = 1;
    public static final int ACCURACY_COARSE = 2;
    public static final int ACCURACY_LOW = 1;
    public static final int ACCURACY_MEDIUM = 2;
    public static final int ACCURACY_HIGH = 3;

    private int mHorizontalAccuracy = NO_REQUIREMENT;
    private int mVerticalAccuracy = NO_REQUIREMENT;
    private int mSpeedAccuracy = NO_REQUIREMENT;
    private int mBearingAccuracy = NO_REQUIREMENT;
    private int mPowerRequirement = NO_REQUIREMENT;
    private boolean mAltitudeRequired;
    private boolean mBearingRequired;
    private boolean mSpeedRequired;
    private boolean mCostAllowed;

    public Criteria() {}

    public Criteria(Criteria criteria) {
        mHorizontalAccuracy = criteria.mHorizontalAccuracy;
        mVerticalAccuracy = criteria.mVerticalAccuracy;
        mSpeedAccuracy = criteria.mSpeedAccuracy;
        mBearingAccuracy = criteria.mBearingAccuracy;
        mPowerRequirement = criteria.mPowerRequirement;
        mAltitudeRequired = criteria.mAltitudeRequired;
        mBearingRequired = criteria.mBearingRequired;
        mSpeedRequired = criteria.mSpeedRequired;
        mCostAllowed = criteria.mCostAllowed;
    }

    private static int check(int v, int max, String what) {
        if (v < NO_REQUIREMENT || v > max) throw new IllegalArgumentException("accuracy=" + v);
        return v;
    }

    public void setHorizontalAccuracy(int accuracy) { mHorizontalAccuracy = check(accuracy, ACCURACY_HIGH, "h"); }
    public int getHorizontalAccuracy() { return mHorizontalAccuracy; }
    public void setVerticalAccuracy(int accuracy) { mVerticalAccuracy = check(accuracy, ACCURACY_HIGH, "v"); }
    public int getVerticalAccuracy() { return mVerticalAccuracy; }
    public void setSpeedAccuracy(int accuracy) { mSpeedAccuracy = check(accuracy, ACCURACY_HIGH, "s"); }
    public int getSpeedAccuracy() { return mSpeedAccuracy; }
    public void setBearingAccuracy(int accuracy) { mBearingAccuracy = check(accuracy, ACCURACY_HIGH, "b"); }
    public int getBearingAccuracy() { return mBearingAccuracy; }

    /** ACCURACY_FINE maps to a high horizontal accuracy and ACCURACY_COARSE to a low one, as in AOSP. */
    public void setAccuracy(int accuracy) {
        check(accuracy, ACCURACY_COARSE, "a");
        mHorizontalAccuracy = accuracy == ACCURACY_FINE ? ACCURACY_HIGH : accuracy == ACCURACY_COARSE ? ACCURACY_LOW
                : NO_REQUIREMENT;
    }

    public int getAccuracy() {
        return mHorizontalAccuracy >= ACCURACY_HIGH ? ACCURACY_FINE : mHorizontalAccuracy == NO_REQUIREMENT
                ? NO_REQUIREMENT : ACCURACY_COARSE;
    }

    public void setPowerRequirement(int level) { mPowerRequirement = check(level, POWER_HIGH, "p"); }
    public int getPowerRequirement() { return mPowerRequirement; }
    public void setCostAllowed(boolean costAllowed) { mCostAllowed = costAllowed; }
    public boolean isCostAllowed() { return mCostAllowed; }
    public void setAltitudeRequired(boolean altitudeRequired) { mAltitudeRequired = altitudeRequired; }
    public boolean isAltitudeRequired() { return mAltitudeRequired; }
    public void setSpeedRequired(boolean speedRequired) { mSpeedRequired = speedRequired; }
    public boolean isSpeedRequired() { return mSpeedRequired; }
    public void setBearingRequired(boolean bearingRequired) { mBearingRequired = bearingRequired; }
    public boolean isBearingRequired() { return mBearingRequired; }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mHorizontalAccuracy);
        parcel.writeInt(mVerticalAccuracy);
        parcel.writeInt(mSpeedAccuracy);
        parcel.writeInt(mBearingAccuracy);
        parcel.writeInt(mPowerRequirement);
        parcel.writeInt(mAltitudeRequired ? 1 : 0);
        parcel.writeInt(mBearingRequired ? 1 : 0);
        parcel.writeInt(mSpeedRequired ? 1 : 0);
        parcel.writeInt(mCostAllowed ? 1 : 0);
    }

    public static final Parcelable.Creator<Criteria> CREATOR = new Parcelable.Creator<Criteria>() {
        public Criteria createFromParcel(Parcel in) {
            Criteria c = new Criteria();
            c.mHorizontalAccuracy = in.readInt();
            c.mVerticalAccuracy = in.readInt();
            c.mSpeedAccuracy = in.readInt();
            c.mBearingAccuracy = in.readInt();
            c.mPowerRequirement = in.readInt();
            c.mAltitudeRequired = in.readInt() != 0;
            c.mBearingRequired = in.readInt() != 0;
            c.mSpeedRequired = in.readInt() != 0;
            c.mCostAllowed = in.readInt() != 0;
            return c;
        }

        public Criteria[] newArray(int size) { return new Criteria[size]; }
    };

    @Override
    public String toString() {
        return "Criteria[power=" + mPowerRequirement + ", accuracy=" + mHorizontalAccuracy + "]";
    }
}
