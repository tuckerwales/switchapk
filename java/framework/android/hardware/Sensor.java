package android.hardware;

/**
 * A sensor of the device. The Switch has the accelerometer and gyroscope of the controller in use; the other
 * motion sensors are computed from them by SystemSensorManager (WS15).
 */
public final class Sensor {
    public static final int REPORTING_MODE_CONTINUOUS = 0;
    public static final int REPORTING_MODE_ONE_SHOT = 2;
    public static final int REPORTING_MODE_ON_CHANGE = 1;
    public static final int REPORTING_MODE_SPECIAL_TRIGGER = 3;
    public static final String STRING_TYPE_ACCELEROMETER = "android.sensor.accelerometer";
    public static final String STRING_TYPE_ACCELEROMETER_LIMITED_AXES = "android.sensor.accelerometer_limited_axes";
    public static final String STRING_TYPE_ACCELEROMETER_LIMITED_AXES_UNCALIBRATED = "android.sensor.accelerometer_limited_axes_uncalibrated";
    public static final String STRING_TYPE_ACCELEROMETER_UNCALIBRATED = "android.sensor.accelerometer_uncalibrated";
    public static final String STRING_TYPE_AMBIENT_TEMPERATURE = "android.sensor.ambient_temperature";
    public static final String STRING_TYPE_GAME_ROTATION_VECTOR = "android.sensor.game_rotation_vector";
    public static final String STRING_TYPE_GEOMAGNETIC_ROTATION_VECTOR = "android.sensor.geomagnetic_rotation_vector";
    public static final String STRING_TYPE_GRAVITY = "android.sensor.gravity";
    public static final String STRING_TYPE_GYROSCOPE = "android.sensor.gyroscope";
    public static final String STRING_TYPE_GYROSCOPE_LIMITED_AXES = "android.sensor.gyroscope_limited_axes";
    public static final String STRING_TYPE_GYROSCOPE_LIMITED_AXES_UNCALIBRATED = "android.sensor.gyroscope_limited_axes_uncalibrated";
    public static final String STRING_TYPE_GYROSCOPE_UNCALIBRATED = "android.sensor.gyroscope_uncalibrated";
    public static final String STRING_TYPE_HEADING = "android.sensor.heading";
    public static final String STRING_TYPE_HEAD_TRACKER = "android.sensor.head_tracker";
    public static final String STRING_TYPE_HEART_BEAT = "android.sensor.heart_beat";
    public static final String STRING_TYPE_HEART_RATE = "android.sensor.heart_rate";
    public static final String STRING_TYPE_HINGE_ANGLE = "android.sensor.hinge_angle";
    public static final String STRING_TYPE_LIGHT = "android.sensor.light";
    public static final String STRING_TYPE_LINEAR_ACCELERATION = "android.sensor.linear_acceleration";
    public static final String STRING_TYPE_LOW_LATENCY_OFFBODY_DETECT = "android.sensor.low_latency_offbody_detect";
    public static final String STRING_TYPE_MAGNETIC_FIELD = "android.sensor.magnetic_field";
    public static final String STRING_TYPE_MAGNETIC_FIELD_UNCALIBRATED = "android.sensor.magnetic_field_uncalibrated";
    public static final String STRING_TYPE_MOTION_DETECT = "android.sensor.motion_detect";
    public static final String STRING_TYPE_ORIENTATION = "android.sensor.orientation";
    public static final String STRING_TYPE_POSE_6DOF = "android.sensor.pose_6dof";
    public static final String STRING_TYPE_PRESSURE = "android.sensor.pressure";
    public static final String STRING_TYPE_PROXIMITY = "android.sensor.proximity";
    public static final String STRING_TYPE_RELATIVE_HUMIDITY = "android.sensor.relative_humidity";
    public static final String STRING_TYPE_ROTATION_VECTOR = "android.sensor.rotation_vector";
    public static final String STRING_TYPE_SIGNIFICANT_MOTION = "android.sensor.significant_motion";
    public static final String STRING_TYPE_STATIONARY_DETECT = "android.sensor.stationary_detect";
    public static final String STRING_TYPE_STEP_COUNTER = "android.sensor.step_counter";
    public static final String STRING_TYPE_STEP_DETECTOR = "android.sensor.step_detector";
    public static final String STRING_TYPE_TEMPERATURE = "android.sensor.temperature";
    public static final int TYPE_ACCELEROMETER = 1;
    public static final int TYPE_ACCELEROMETER_LIMITED_AXES = 38;
    public static final int TYPE_ACCELEROMETER_LIMITED_AXES_UNCALIBRATED = 40;
    public static final int TYPE_ACCELEROMETER_UNCALIBRATED = 35;
    public static final int TYPE_ALL = -1;
    public static final int TYPE_AMBIENT_TEMPERATURE = 13;
    public static final int TYPE_DEVICE_PRIVATE_BASE = 65536;
    public static final int TYPE_GAME_ROTATION_VECTOR = 15;
    public static final int TYPE_GEOMAGNETIC_ROTATION_VECTOR = 20;
    public static final int TYPE_GRAVITY = 9;
    public static final int TYPE_GYROSCOPE = 4;
    public static final int TYPE_GYROSCOPE_LIMITED_AXES = 39;
    public static final int TYPE_GYROSCOPE_LIMITED_AXES_UNCALIBRATED = 41;
    public static final int TYPE_GYROSCOPE_UNCALIBRATED = 16;
    public static final int TYPE_HEADING = 42;
    public static final int TYPE_HEAD_TRACKER = 37;
    public static final int TYPE_HEART_BEAT = 31;
    public static final int TYPE_HEART_RATE = 21;
    public static final int TYPE_HINGE_ANGLE = 36;
    public static final int TYPE_LIGHT = 5;
    public static final int TYPE_LINEAR_ACCELERATION = 10;
    public static final int TYPE_LOW_LATENCY_OFFBODY_DETECT = 34;
    public static final int TYPE_MAGNETIC_FIELD = 2;
    public static final int TYPE_MAGNETIC_FIELD_UNCALIBRATED = 14;
    public static final int TYPE_MOTION_DETECT = 30;
    public static final int TYPE_ORIENTATION = 3;
    public static final int TYPE_POSE_6DOF = 28;
    public static final int TYPE_PRESSURE = 6;
    public static final int TYPE_PROXIMITY = 8;
    public static final int TYPE_RELATIVE_HUMIDITY = 12;
    public static final int TYPE_ROTATION_VECTOR = 11;
    public static final int TYPE_SIGNIFICANT_MOTION = 17;
    public static final int TYPE_STATIONARY_DETECT = 29;
    public static final int TYPE_STEP_COUNTER = 19;
    public static final int TYPE_STEP_DETECTOR = 18;
    public static final int TYPE_TEMPERATURE = 7;

    private final String mName;
    private final String mVendor;
    private final int mType;
    private final String mStringType;
    private final int mVersion;
    private final int mHandle;
    private final float mMaxRange;
    private final float mResolution;
    private final float mPower;
    private final int mMinDelay;
    private final int mMaxDelay;

    /** framework-internal. */
    Sensor(String name, String vendor, int type, String stringType, int version, int handle, float maxRange,
            float resolution, float power, int minDelay, int maxDelay) {
        mName = name;
        mVendor = vendor;
        mType = type;
        mStringType = stringType;
        mVersion = version;
        mHandle = handle;
        mMaxRange = maxRange;
        mResolution = resolution;
        mPower = power;
        mMinDelay = minDelay;
        mMaxDelay = maxDelay;
    }

    public int getReportingMode() {
        switch (mType) {
            case TYPE_SIGNIFICANT_MOTION:
                return REPORTING_MODE_ONE_SHOT;
            case TYPE_STEP_DETECTOR:
                return REPORTING_MODE_SPECIAL_TRIGGER;
            case TYPE_LIGHT:
            case TYPE_PROXIMITY:
            case TYPE_STEP_COUNTER:
                return REPORTING_MODE_ON_CHANGE;
            default:
                return REPORTING_MODE_CONTINUOUS;
        }
    }

    public int getHighestDirectReportRateLevel() { return 0; } // SensorDirectChannel.RATE_STOP
    public boolean isDirectChannelTypeSupported(int sharedMemType) { return false; }
    public String getName() { return mName; }
    public String getVendor() { return mVendor; }
    public int getType() { return mType; }
    public int getVersion() { return mVersion; }
    public float getMaximumRange() { return mMaxRange; }
    public float getResolution() { return mResolution; }
    public float getPower() { return mPower; }
    public int getMinDelay() { return mMinDelay; }
    public int getFifoReservedEventCount() { return 0; }
    public int getFifoMaxEventCount() { return 0; }
    public String getStringType() { return mStringType; }
    public int getId() { return 0; }
    public int getMaxDelay() { return mMaxDelay; }
    public boolean isWakeUpSensor() { return false; }
    public boolean isDynamicSensor() { return false; }
    public boolean isAdditionalInfoSupported() { return false; }

    /** framework-internal (hidden in AOSP). */
    public int getHandle() { return mHandle; }

    /** Length of SensorEvent.values for a sensor type, as AOSP's Sensor.getMaxLengthValuesArray. */
    static int valuesLength(int type) {
        switch (type) {
            case TYPE_ROTATION_VECTOR:
            case TYPE_GEOMAGNETIC_ROTATION_VECTOR:
                return 5;
            case TYPE_GAME_ROTATION_VECTOR:
                return 4;
            case TYPE_MAGNETIC_FIELD_UNCALIBRATED:
            case TYPE_GYROSCOPE_UNCALIBRATED:
            case TYPE_ACCELEROMETER_UNCALIBRATED:
                return 6;
            case TYPE_LIGHT:
            case TYPE_PRESSURE:
            case TYPE_TEMPERATURE:
            case TYPE_PROXIMITY:
            case TYPE_RELATIVE_HUMIDITY:
            case TYPE_AMBIENT_TEMPERATURE:
            case TYPE_SIGNIFICANT_MOTION:
            case TYPE_STEP_DETECTOR:
            case TYPE_STEP_COUNTER:
                return 1;
            default:
                return 3;
        }
    }

    @Override
    public String toString() {
        return "{Sensor name=\"" + mName + "\", vendor=\"" + mVendor + "\", version=" + mVersion + ", type=" + mType
                + ", maxRange=" + mMaxRange + ", resolution=" + mResolution + ", power=" + mPower + ", minDelay="
                + mMinDelay + "}";
    }
}
