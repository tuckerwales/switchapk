package android.location;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Printer;
import java.util.Locale;

/** A geographic fix. Plain data, as in AOSP; the distance math is AOSP's Vincenty inverse formula. */
public class Location implements Parcelable {
    public static final int FORMAT_DEGREES = 0;
    public static final int FORMAT_MINUTES = 1;
    public static final int FORMAT_SECONDS = 2;

    private static final int HAS_ALTITUDE = 1;
    private static final int HAS_SPEED = 1 << 1;
    private static final int HAS_BEARING = 1 << 2;
    private static final int HAS_HORIZONTAL_ACCURACY = 1 << 3;
    private static final int HAS_MOCK = 1 << 4;
    private static final int HAS_VERTICAL_ACCURACY = 1 << 5;
    private static final int HAS_SPEED_ACCURACY = 1 << 6;
    private static final int HAS_BEARING_ACCURACY = 1 << 7;
    private static final int HAS_ELAPSED_REALTIME_UNCERTAINTY = 1 << 8;
    private static final int HAS_MSL_ALTITUDE = 1 << 9;
    private static final int HAS_MSL_ALTITUDE_ACCURACY = 1 << 10;

    private int mFields;
    private String mProvider;
    private long mTimeMs;
    private long mElapsedRealtimeNs;
    private double mElapsedRealtimeUncertaintyNs;
    private double mLatitudeDegrees;
    private double mLongitudeDegrees;
    private float mHorizontalAccuracyMeters;
    private double mAltitudeMeters;
    private float mAltitudeAccuracyMeters;
    private float mSpeedMetersPerSecond;
    private float mSpeedAccuracyMetersPerSecond;
    private float mBearingDegrees;
    private float mBearingAccuracyDegrees;
    private double mMslAltitudeMeters;
    private float mMslAltitudeAccuracyMeters;
    private Bundle mExtras;

    public Location(String provider) {
        mProvider = provider;
    }

    public Location(Location location) {
        set(location);
    }

    public void set(Location location) {
        mFields = location.mFields;
        mProvider = location.mProvider;
        mTimeMs = location.mTimeMs;
        mElapsedRealtimeNs = location.mElapsedRealtimeNs;
        mElapsedRealtimeUncertaintyNs = location.mElapsedRealtimeUncertaintyNs;
        mLatitudeDegrees = location.mLatitudeDegrees;
        mLongitudeDegrees = location.mLongitudeDegrees;
        mHorizontalAccuracyMeters = location.mHorizontalAccuracyMeters;
        mAltitudeMeters = location.mAltitudeMeters;
        mAltitudeAccuracyMeters = location.mAltitudeAccuracyMeters;
        mSpeedMetersPerSecond = location.mSpeedMetersPerSecond;
        mSpeedAccuracyMetersPerSecond = location.mSpeedAccuracyMetersPerSecond;
        mBearingDegrees = location.mBearingDegrees;
        mBearingAccuracyDegrees = location.mBearingAccuracyDegrees;
        mMslAltitudeMeters = location.mMslAltitudeMeters;
        mMslAltitudeAccuracyMeters = location.mMslAltitudeAccuracyMeters;
        mExtras = location.mExtras == null ? null : new Bundle(location.mExtras);
    }

    public void reset() {
        mFields = 0;
        mTimeMs = 0;
        mElapsedRealtimeNs = 0;
        mElapsedRealtimeUncertaintyNs = 0;
        mLatitudeDegrees = 0;
        mLongitudeDegrees = 0;
        mHorizontalAccuracyMeters = 0;
        mAltitudeMeters = 0;
        mAltitudeAccuracyMeters = 0;
        mSpeedMetersPerSecond = 0;
        mSpeedAccuracyMetersPerSecond = 0;
        mBearingDegrees = 0;
        mBearingAccuracyDegrees = 0;
        mMslAltitudeMeters = 0;
        mMslAltitudeAccuracyMeters = 0;
        mExtras = null;
    }

    public float distanceTo(Location dest) {
        float[] r = new float[2];
        computeDistanceAndBearing(mLatitudeDegrees, mLongitudeDegrees, dest.mLatitudeDegrees, dest.mLongitudeDegrees, r);
        return r[0];
    }

    public float bearingTo(Location dest) {
        float[] r = new float[2];
        computeDistanceAndBearing(mLatitudeDegrees, mLongitudeDegrees, dest.mLatitudeDegrees, dest.mLongitudeDegrees, r);
        return r[1];
    }

    public static void distanceBetween(double startLatitude, double startLongitude, double endLatitude,
            double endLongitude, float[] results) {
        if (results == null || results.length < 1) {
            throw new IllegalArgumentException("results is null or has length < 1");
        }
        float[] r = new float[3];
        computeDistanceAndBearing(startLatitude, startLongitude, endLatitude, endLongitude, r);
        results[0] = r[0];
        if (results.length > 1) results[1] = r[1];
        if (results.length > 2) results[2] = r[2];
    }

    /** distance, initial bearing and (if out has 3 slots) final bearing. */
    private static void computeDistanceAndBearing(double lat1, double lon1, double lat2, double lon2, float[] out) {
        // Based on http://www.ngs.noaa.gov/PC_PROD/Inv_Fwd/ using the "Inverse Formula" (section 4)
        final int maxIters = 20;
        // Convert lat/long to radians
        lat1 *= Math.PI / 180.0;
        lat2 *= Math.PI / 180.0;
        lon1 *= Math.PI / 180.0;
        lon2 *= Math.PI / 180.0;

        double a = 6378137.0; // WGS84 major axis
        double b = 6356752.3142; // WGS84 semi-major axis
        double f = (a - b) / a;
        double aSqMinusBSqOverBSq = (a * a - b * b) / (b * b);

        double l = lon2 - lon1;
        double aA = 0.0;
        double u1 = Math.atan((1.0 - f) * Math.tan(lat1));
        double u2 = Math.atan((1.0 - f) * Math.tan(lat2));

        double cosU1 = Math.cos(u1);
        double cosU2 = Math.cos(u2);
        double sinU1 = Math.sin(u1);
        double sinU2 = Math.sin(u2);
        double cosU1cosU2 = cosU1 * cosU2;
        double sinU1sinU2 = sinU1 * sinU2;

        double sigma = 0.0;
        double deltaSigma = 0.0;
        double cosSqAlpha;
        double cos2SM;
        double cosSigma;
        double sinSigma;
        double cosLambda = 0.0;
        double sinLambda = 0.0;

        double lambda = l; // initial guess
        for (int iter = 0; iter < maxIters; iter++) {
            double lambdaOrig = lambda;
            cosLambda = Math.cos(lambda);
            sinLambda = Math.sin(lambda);
            double t1 = cosU2 * sinLambda;
            double t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda;
            double sinSqSigma = t1 * t1 + t2 * t2;
            sinSigma = Math.sqrt(sinSqSigma);
            cosSigma = sinU1sinU2 + cosU1cosU2 * cosLambda;
            sigma = Math.atan2(sinSigma, cosSigma);
            double sinAlpha = (sinSigma == 0) ? 0.0 : cosU1cosU2 * sinLambda / sinSigma;
            cosSqAlpha = 1.0 - sinAlpha * sinAlpha;
            cos2SM = (cosSqAlpha == 0) ? 0.0 : cosSigma - 2.0 * sinU1sinU2 / cosSqAlpha;

            double uSquared = cosSqAlpha * aSqMinusBSqOverBSq;
            aA = 1 + (uSquared / 16384.0) * (4096.0 + uSquared * (-768 + uSquared * (320.0 - 175.0 * uSquared)));
            double bB = (uSquared / 1024.0) * (256.0 + uSquared * (-128.0 + uSquared * (74.0 - 47.0 * uSquared)));
            double cC = (f / 16.0) * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha));
            double cos2SMSq = cos2SM * cos2SM;
            deltaSigma = bB * sinSigma * (cos2SM + (bB / 4.0) * (cosSigma * (-1.0 + 2.0 * cos2SMSq)
                    - (bB / 6.0) * cos2SM * (-3.0 + 4.0 * sinSigma * sinSigma) * (-3.0 + 4.0 * cos2SMSq)));

            lambda = l + (1.0 - cC) * f * sinAlpha
                    * (sigma + cC * sinSigma * (cos2SM + cC * cosSigma * (-1.0 + 2.0 * cos2SM * cos2SM)));

            double delta = (lambda - lambdaOrig) / lambda;
            if (Math.abs(delta) < 1.0e-12) {
                break;
            }
        }

        out[0] = (float) (b * aA * (sigma - deltaSigma));
        float initialBearing = (float) Math.atan2(cosU2 * sinLambda, cosU1 * sinU2 - sinU1 * cosU2 * cosLambda);
        out[1] = (float) (initialBearing * (180.0 / Math.PI));
        if (out.length > 2) {
            float finalBearing = (float) Math.atan2(cosU1 * sinLambda, -sinU1 * cosU2 + cosU1 * sinU2 * cosLambda);
            out[2] = (float) (finalBearing * (180.0 / Math.PI));
        }
    }

    public String getProvider() { return mProvider; }
    public void setProvider(String provider) { mProvider = provider; }
    public long getTime() { return mTimeMs; }
    public void setTime(long timeMs) { mTimeMs = timeMs; }
    public long getElapsedRealtimeNanos() { return mElapsedRealtimeNs; }
    public long getElapsedRealtimeMillis() { return mElapsedRealtimeNs / 1000000L; }
    public long getElapsedRealtimeAgeMillis() { return getElapsedRealtimeAgeMillis(SystemClock.elapsedRealtime()); }
    public long getElapsedRealtimeAgeMillis(long referenceRealtimeMs) { return referenceRealtimeMs - getElapsedRealtimeMillis(); }
    public void setElapsedRealtimeNanos(long elapsedRealtimeNs) { mElapsedRealtimeNs = elapsedRealtimeNs; }
    public double getElapsedRealtimeUncertaintyNanos() { return mElapsedRealtimeUncertaintyNs; }

    public void setElapsedRealtimeUncertaintyNanos(double nanos) {
        mElapsedRealtimeUncertaintyNs = nanos;
        mFields |= HAS_ELAPSED_REALTIME_UNCERTAINTY;
    }

    public boolean hasElapsedRealtimeUncertaintyNanos() { return (mFields & HAS_ELAPSED_REALTIME_UNCERTAINTY) != 0; }

    public void removeElapsedRealtimeUncertaintyNanos() { mFields &= ~HAS_ELAPSED_REALTIME_UNCERTAINTY; }

    public double getLatitude() { return mLatitudeDegrees; }
    public void setLatitude(double latitudeDegrees) { mLatitudeDegrees = latitudeDegrees; }
    public double getLongitude() { return mLongitudeDegrees; }
    public void setLongitude(double longitudeDegrees) { mLongitudeDegrees = longitudeDegrees; }
    public float getAccuracy() { return mHorizontalAccuracyMeters; }

    public void setAccuracy(float horizontalAccuracyMeters) {
        mHorizontalAccuracyMeters = horizontalAccuracyMeters;
        mFields |= HAS_HORIZONTAL_ACCURACY;
    }

    public boolean hasAccuracy() { return (mFields & HAS_HORIZONTAL_ACCURACY) != 0; }
    public void removeAccuracy() { mFields &= ~HAS_HORIZONTAL_ACCURACY; }
    public double getAltitude() { return mAltitudeMeters; }

    public void setAltitude(double altitudeMeters) {
        mAltitudeMeters = altitudeMeters;
        mFields |= HAS_ALTITUDE;
    }

    public boolean hasAltitude() { return (mFields & HAS_ALTITUDE) != 0; }
    public void removeAltitude() { mFields &= ~HAS_ALTITUDE; }
    public float getVerticalAccuracyMeters() { return mAltitudeAccuracyMeters; }

    public void setVerticalAccuracyMeters(float altitudeAccuracyMeters) {
        mAltitudeAccuracyMeters = altitudeAccuracyMeters;
        mFields |= HAS_VERTICAL_ACCURACY;
    }

    public boolean hasVerticalAccuracy() { return (mFields & HAS_VERTICAL_ACCURACY) != 0; }
    public void removeVerticalAccuracy() { mFields &= ~HAS_VERTICAL_ACCURACY; }
    public float getSpeed() { return mSpeedMetersPerSecond; }

    public void setSpeed(float speedMetersPerSecond) {
        mSpeedMetersPerSecond = speedMetersPerSecond;
        mFields |= HAS_SPEED;
    }

    public boolean hasSpeed() { return (mFields & HAS_SPEED) != 0; }
    public void removeSpeed() { mFields &= ~HAS_SPEED; }
    public float getSpeedAccuracyMetersPerSecond() { return mSpeedAccuracyMetersPerSecond; }

    public void setSpeedAccuracyMetersPerSecond(float speedAccuracyMeterPerSecond) {
        mSpeedAccuracyMetersPerSecond = speedAccuracyMeterPerSecond;
        mFields |= HAS_SPEED_ACCURACY;
    }

    public boolean hasSpeedAccuracy() { return (mFields & HAS_SPEED_ACCURACY) != 0; }
    public void removeSpeedAccuracy() { mFields &= ~HAS_SPEED_ACCURACY; }
    public float getBearing() { return mBearingDegrees; }

    /** Normalized to [0, 360), as AOSP does. */
    public void setBearing(float bearingDegrees) {
        if (Float.isFinite(bearingDegrees)) {
            float modBearing = bearingDegrees % 360f + 0f;
            if (modBearing < 0) modBearing += 360f;
            mBearingDegrees = modBearing;
        } else {
            mBearingDegrees = bearingDegrees;
        }
        mFields |= HAS_BEARING;
    }

    public boolean hasBearing() { return (mFields & HAS_BEARING) != 0; }
    public void removeBearing() { mFields &= ~HAS_BEARING; }
    public float getBearingAccuracyDegrees() { return mBearingAccuracyDegrees; }

    public void setBearingAccuracyDegrees(float bearingAccuracyDegrees) {
        mBearingAccuracyDegrees = bearingAccuracyDegrees;
        mFields |= HAS_BEARING_ACCURACY;
    }

    public boolean hasBearingAccuracy() { return (mFields & HAS_BEARING_ACCURACY) != 0; }
    public void removeBearingAccuracy() { mFields &= ~HAS_BEARING_ACCURACY; }
    public double getMslAltitudeMeters() { return mMslAltitudeMeters; }

    public void setMslAltitudeMeters(double mslAltitudeMeters) {
        mMslAltitudeMeters = mslAltitudeMeters;
        mFields |= HAS_MSL_ALTITUDE;
    }

    public boolean hasMslAltitude() { return (mFields & HAS_MSL_ALTITUDE) != 0; }
    public void removeMslAltitude() { mFields &= ~HAS_MSL_ALTITUDE; }
    public float getMslAltitudeAccuracyMeters() { return mMslAltitudeAccuracyMeters; }

    public void setMslAltitudeAccuracyMeters(float mslAltitudeAccuracyMeters) {
        mMslAltitudeAccuracyMeters = mslAltitudeAccuracyMeters;
        mFields |= HAS_MSL_ALTITUDE_ACCURACY;
    }

    public boolean hasMslAltitudeAccuracy() { return (mFields & HAS_MSL_ALTITUDE_ACCURACY) != 0; }
    public void removeMslAltitudeAccuracy() { mFields &= ~HAS_MSL_ALTITUDE_ACCURACY; }

    @Deprecated
    public boolean isFromMockProvider() { return isMock(); }

    public boolean isMock() { return (mFields & HAS_MOCK) != 0; }

    public void setMock(boolean mock) {
        if (mock) {
            mFields |= HAS_MOCK;
        } else {
            mFields &= ~HAS_MOCK;
        }
    }

    public Bundle getExtras() { return mExtras; }
    public void setExtras(Bundle extras) { mExtras = extras == null ? null : new Bundle(extras); }

    public boolean isComplete() {
        return mProvider != null && hasAccuracy() && mTimeMs != 0 && mElapsedRealtimeNs != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location)) return false;
        Location l = (Location) o;
        return mFields == l.mFields && mTimeMs == l.mTimeMs && mElapsedRealtimeNs == l.mElapsedRealtimeNs
                && Double.compare(l.mLatitudeDegrees, mLatitudeDegrees) == 0
                && Double.compare(l.mLongitudeDegrees, mLongitudeDegrees) == 0
                && Double.compare(l.mAltitudeMeters, mAltitudeMeters) == 0
                && Float.compare(l.mHorizontalAccuracyMeters, mHorizontalAccuracyMeters) == 0
                && Float.compare(l.mSpeedMetersPerSecond, mSpeedMetersPerSecond) == 0
                && Float.compare(l.mBearingDegrees, mBearingDegrees) == 0
                && (mProvider == null ? l.mProvider == null : mProvider.equals(l.mProvider));
    }

    @Override
    public int hashCode() {
        long lat = Double.doubleToLongBits(mLatitudeDegrees);
        long lon = Double.doubleToLongBits(mLongitudeDegrees);
        int h = (mProvider == null ? 0 : mProvider.hashCode());
        h = 31 * h + (int) (mElapsedRealtimeNs ^ (mElapsedRealtimeNs >>> 32));
        h = 31 * h + (int) (lat ^ (lat >>> 32));
        h = 31 * h + (int) (lon ^ (lon >>> 32));
        return h;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("Location[");
        s.append(mProvider);
        s.append(String.format(Locale.ROOT, " %.6f,%.6f", mLatitudeDegrees, mLongitudeDegrees));
        if (hasAccuracy()) s.append(String.format(Locale.ROOT, " hAcc=%.0f", mHorizontalAccuracyMeters));
        else s.append(" hAcc=???");
        if (mElapsedRealtimeNs != 0) s.append(" et=").append(mElapsedRealtimeNs / 1000000L).append("ms");
        if (hasAltitude()) s.append(" alt=").append(mAltitudeMeters);
        if (hasSpeed()) s.append(" vel=").append(mSpeedMetersPerSecond);
        if (hasBearing()) s.append(" bear=").append(mBearingDegrees);
        if (isMock()) s.append(" mock");
        if (mExtras != null) s.append(" {").append(mExtras).append('}');
        s.append(']');
        return s.toString();
    }

    public void dump(Printer pw, String prefix) {
        pw.println(prefix + this);
    }

    /** "ddd.ddddd", "ddd:mm.mmmmm" or "ddd:mm:ss.sssss", as AOSP formats with "###.#####". */
    public static String convert(double coordinate, int outputType) {
        if (coordinate < -180.0 || coordinate > 180.0 || Double.isNaN(coordinate)) {
            throw new IllegalArgumentException("coordinate=" + coordinate);
        }
        if ((outputType != FORMAT_DEGREES) && (outputType != FORMAT_MINUTES) && (outputType != FORMAT_SECONDS)) {
            throw new IllegalArgumentException("outputType=" + outputType);
        }
        StringBuilder sb = new StringBuilder();
        if (coordinate < 0) {
            sb.append('-');
            coordinate = -coordinate;
        }
        if (outputType == FORMAT_MINUTES || outputType == FORMAT_SECONDS) {
            int degrees = (int) Math.floor(coordinate);
            sb.append(degrees);
            sb.append(':');
            coordinate -= degrees;
            coordinate *= 60.0;
            if (outputType == FORMAT_SECONDS) {
                int minutes = (int) Math.floor(coordinate);
                sb.append(minutes);
                sb.append(':');
                coordinate -= minutes;
                coordinate *= 60.0;
            }
        }
        sb.append(fraction5(coordinate));
        return sb.toString();
    }

    private static String fraction5(double v) {
        String s = String.format(Locale.ROOT, "%.5f", v);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') end--;
        if (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }

    public static double convert(String coordinate) {
        if (coordinate == null) throw new NullPointerException("coordinate");
        boolean negative = false;
        if (coordinate.charAt(0) == '-') {
            coordinate = coordinate.substring(1);
            negative = true;
        }
        String[] parts = coordinate.split(":", -1);
        if (parts.length > 3) throw new IllegalArgumentException("coordinate=" + coordinate);
        try {
            double val;
            if (parts.length == 1) {
                val = Double.parseDouble(parts[0]);
                return negative ? -val : val;
            }
            int deg = Integer.parseInt(parts[0]);
            double min;
            double sec = 0.0;
            if (parts.length == 3) {
                min = Integer.parseInt(parts[1]);
                sec = Double.parseDouble(parts[2]);
            } else {
                min = Double.parseDouble(parts[1]);
            }
            boolean isNegative180 = negative && (deg == 180) && (min == 0) && (sec == 0);
            if ((deg < 0.0) || (deg > 179 && !isNegative180)) {
                throw new IllegalArgumentException("coordinate=" + coordinate);
            }
            if (min < 0 || min >= 60 || (parts.length == 3 && min > 59)) {
                throw new IllegalArgumentException("coordinate=" + coordinate);
            }
            if (sec < 0 || sec >= 60) throw new IllegalArgumentException("coordinate=" + coordinate);
            val = deg * 3600.0 + min * 60.0 + sec;
            val /= 3600.0;
            return negative ? -val : val;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("coordinate=" + coordinate);
        }
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mProvider);
        parcel.writeInt(mFields);
        parcel.writeLong(mTimeMs);
        parcel.writeLong(mElapsedRealtimeNs);
        parcel.writeDouble(mElapsedRealtimeUncertaintyNs);
        parcel.writeDouble(mLatitudeDegrees);
        parcel.writeDouble(mLongitudeDegrees);
        parcel.writeFloat(mHorizontalAccuracyMeters);
        parcel.writeDouble(mAltitudeMeters);
        parcel.writeFloat(mAltitudeAccuracyMeters);
        parcel.writeFloat(mSpeedMetersPerSecond);
        parcel.writeFloat(mSpeedAccuracyMetersPerSecond);
        parcel.writeFloat(mBearingDegrees);
        parcel.writeFloat(mBearingAccuracyDegrees);
        parcel.writeDouble(mMslAltitudeMeters);
        parcel.writeFloat(mMslAltitudeAccuracyMeters);
        parcel.writeBundle(mExtras);
    }

    public static final Parcelable.Creator<Location> CREATOR = new Parcelable.Creator<Location>() {
        public Location createFromParcel(Parcel in) {
            Location l = new Location(in.readString());
            l.mFields = in.readInt();
            l.mTimeMs = in.readLong();
            l.mElapsedRealtimeNs = in.readLong();
            l.mElapsedRealtimeUncertaintyNs = in.readDouble();
            l.mLatitudeDegrees = in.readDouble();
            l.mLongitudeDegrees = in.readDouble();
            l.mHorizontalAccuracyMeters = in.readFloat();
            l.mAltitudeMeters = in.readDouble();
            l.mAltitudeAccuracyMeters = in.readFloat();
            l.mSpeedMetersPerSecond = in.readFloat();
            l.mSpeedAccuracyMetersPerSecond = in.readFloat();
            l.mBearingDegrees = in.readFloat();
            l.mBearingAccuracyDegrees = in.readFloat();
            l.mMslAltitudeMeters = in.readDouble();
            l.mMslAltitudeAccuracyMeters = in.readFloat();
            l.mExtras = in.readBundle();
            return l;
        }

        public Location[] newArray(int size) { return new Location[size]; }
    };
}
