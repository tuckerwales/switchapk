package android.hardware;

import android.os.Handler;
import java.util.ArrayList;
import java.util.List;

/**
 * Sensor access. The public methods and the static math follow AOSP; SystemSensorManager implements the rest
 * (WS15).
 */
public abstract class SensorManager {
    public static final int AXIS_MINUS_X = 129;
    public static final int AXIS_MINUS_Y = 130;
    public static final int AXIS_MINUS_Z = 131;
    public static final int AXIS_X = 1;
    public static final int AXIS_Y = 2;
    public static final int AXIS_Z = 3;
    public static final int DATA_X = 0;
    public static final int DATA_Y = 1;
    public static final int DATA_Z = 2;
    public static final float GRAVITY_DEATH_STAR_I = 3.5303614E-7f;
    public static final float GRAVITY_EARTH = 9.80665f;
    public static final float GRAVITY_JUPITER = 23.12f;
    public static final float GRAVITY_MARS = 3.71f;
    public static final float GRAVITY_MERCURY = 3.7f;
    public static final float GRAVITY_MOON = 1.6f;
    public static final float GRAVITY_NEPTUNE = 11.0f;
    public static final float GRAVITY_PLUTO = 0.6f;
    public static final float GRAVITY_SATURN = 8.96f;
    public static final float GRAVITY_SUN = 275.0f;
    public static final float GRAVITY_THE_ISLAND = 4.815162f;
    public static final float GRAVITY_URANUS = 8.69f;
    public static final float GRAVITY_VENUS = 8.87f;
    public static final float LIGHT_CLOUDY = 100.0f;
    public static final float LIGHT_FULLMOON = 0.25f;
    public static final float LIGHT_NO_MOON = 0.001f;
    public static final float LIGHT_OVERCAST = 10000.0f;
    public static final float LIGHT_SHADE = 20000.0f;
    public static final float LIGHT_SUNLIGHT = 110000.0f;
    public static final float LIGHT_SUNLIGHT_MAX = 120000.0f;
    public static final float LIGHT_SUNRISE = 400.0f;
    public static final float MAGNETIC_FIELD_EARTH_MAX = 60.0f;
    public static final float MAGNETIC_FIELD_EARTH_MIN = 30.0f;
    public static final float PRESSURE_STANDARD_ATMOSPHERE = 1013.25f;
    public static final int RAW_DATA_INDEX = 3;
    public static final int RAW_DATA_X = 3;
    public static final int RAW_DATA_Y = 4;
    public static final int RAW_DATA_Z = 5;
    public static final int SENSOR_ACCELEROMETER = 2;
    public static final int SENSOR_ALL = 127;
    public static final int SENSOR_DELAY_FASTEST = 0;
    public static final int SENSOR_DELAY_GAME = 1;
    public static final int SENSOR_DELAY_NORMAL = 3;
    public static final int SENSOR_DELAY_UI = 2;
    public static final int SENSOR_LIGHT = 16;
    public static final int SENSOR_MAGNETIC_FIELD = 8;
    public static final int SENSOR_MAX = 64;
    public static final int SENSOR_MIN = 1;
    public static final int SENSOR_ORIENTATION = 1;
    public static final int SENSOR_ORIENTATION_RAW = 128;
    public static final int SENSOR_PROXIMITY = 32;
    public static final int SENSOR_STATUS_ACCURACY_HIGH = 3;
    public static final int SENSOR_STATUS_ACCURACY_LOW = 1;
    public static final int SENSOR_STATUS_ACCURACY_MEDIUM = 2;
    public static final int SENSOR_STATUS_NO_CONTACT = -1;
    public static final int SENSOR_STATUS_UNRELIABLE = 0;
    public static final int SENSOR_TEMPERATURE = 4;
    public static final int SENSOR_TRICORDER = 64;
    public static final float STANDARD_GRAVITY = 9.80665f;

    SensorManager() {}

    /** All sensors of the device, in handle order. */
    abstract List<Sensor> getFullSensorList();

    abstract boolean registerListenerImpl(SensorEventListener listener, Sensor sensor, int delayUs, Handler handler,
            int maxReportLatencyUs);

    abstract void unregisterListenerImpl(SensorEventListener listener, Sensor sensor);

    abstract boolean flushImpl(SensorEventListener listener);

    abstract boolean registerLegacyListener(SensorListener listener, int sensors, int rate);

    abstract void unregisterLegacyListener(SensorListener listener, int sensors);

    /** @deprecated use {@link #getSensorList(int)}. */
    @Deprecated
    public int getSensors() {
        int result = 0;
        for (Sensor s : getFullSensorList()) {
            switch (s.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    result |= SENSOR_ACCELEROMETER;
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    result |= SENSOR_MAGNETIC_FIELD;
                    break;
                case Sensor.TYPE_ORIENTATION:
                    result |= SENSOR_ORIENTATION | SENSOR_ORIENTATION_RAW;
                    break;
                default:
                    break;
            }
        }
        return result;
    }

    public List<Sensor> getSensorList(int type) {
        List<Sensor> full = getFullSensorList();
        if (type == Sensor.TYPE_ALL) return java.util.Collections.unmodifiableList(new ArrayList<Sensor>(full));
        List<Sensor> list = new ArrayList<Sensor>();
        for (Sensor s : full) {
            if (s.getType() == type) list.add(s);
        }
        return java.util.Collections.unmodifiableList(list);
    }

    public List<Sensor> getDynamicSensorList(int type) {
        return java.util.Collections.unmodifiableList(new ArrayList<Sensor>());
    }

    public Sensor getDefaultSensor(int type) {
        List<Sensor> l = getSensorList(type);
        return l.isEmpty() ? null : l.get(0);
    }

    public Sensor getDefaultSensor(int type, boolean wakeUp) {
        for (Sensor s : getSensorList(type)) {
            if (s.isWakeUpSensor() == wakeUp) return s;
        }
        return null;
    }

    /** @deprecated use {@link #registerListener(SensorEventListener, Sensor, int)}. */
    @Deprecated
    public boolean registerListener(SensorListener listener, int sensors) {
        return registerListener(listener, sensors, SENSOR_DELAY_NORMAL);
    }

    /** @deprecated use {@link #registerListener(SensorEventListener, Sensor, int)}. */
    @Deprecated
    public boolean registerListener(SensorListener listener, int sensors, int rate) {
        if (listener == null) return false;
        return registerLegacyListener(listener, sensors, rate);
    }

    /** @deprecated use {@link #unregisterListener(SensorEventListener)}. */
    @Deprecated
    public void unregisterListener(SensorListener listener) {
        unregisterListener(listener, SENSOR_ALL | SENSOR_ORIENTATION_RAW);
    }

    /** @deprecated use {@link #unregisterListener(SensorEventListener, Sensor)}. */
    @Deprecated
    public void unregisterListener(SensorListener listener, int sensors) {
        if (listener == null) return;
        unregisterLegacyListener(listener, sensors);
    }

    public void unregisterListener(SensorEventListener listener, Sensor sensor) {
        if (listener == null || sensor == null) return;
        unregisterListenerImpl(listener, sensor);
    }

    public void unregisterListener(SensorEventListener listener) {
        if (listener == null) return;
        unregisterListenerImpl(listener, null);
    }

    public boolean registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs) {
        return registerListener(listener, sensor, samplingPeriodUs, null);
    }

    public boolean registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs,
            int maxReportLatencyUs) {
        return registerListenerImpl(listener, sensor, getDelay(samplingPeriodUs), null, maxReportLatencyUs);
    }

    public boolean registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs,
            Handler handler) {
        return registerListenerImpl(listener, sensor, getDelay(samplingPeriodUs), handler, 0);
    }

    public boolean registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs,
            int maxReportLatencyUs, Handler handler) {
        return registerListenerImpl(listener, sensor, getDelay(samplingPeriodUs), handler, maxReportLatencyUs);
    }

    public boolean flush(SensorEventListener listener) {
        return flushImpl(listener);
    }

    public void registerDynamicSensorCallback(DynamicSensorCallback callback) {
        registerDynamicSensorCallback(callback, null);
    }

    public void registerDynamicSensorCallback(DynamicSensorCallback callback, Handler handler) {
        if (callback == null) throw new IllegalArgumentException("callback cannot be null");
    }

    public void unregisterDynamicSensorCallback(DynamicSensorCallback callback) {}

    public boolean isDynamicSensorDiscoverySupported() { return false; }

    public boolean requestTriggerSensor(TriggerEventListener listener, Sensor sensor) {
        if (sensor == null) throw new IllegalArgumentException("sensor cannot be null");
        if (listener == null) throw new IllegalArgumentException("listener cannot be null");
        // No one-shot sensor exists here, so whatever was passed cannot trigger.
        return false;
    }

    public boolean cancelTriggerSensor(TriggerEventListener listener, Sensor sensor) {
        if (listener == null) throw new IllegalArgumentException("listener cannot be null");
        return false;
    }

    /** Sampling period in microseconds for a SENSOR_DELAY_* constant or a period, as AOSP getDelay. */
    static int getDelay(int rate) {
        switch (rate) {
            case SENSOR_DELAY_FASTEST:
                return 0;
            case SENSOR_DELAY_GAME:
                return 20000;
            case SENSOR_DELAY_UI:
                return 66667;
            case SENSOR_DELAY_NORMAL:
                return 200000;
            default:
                return rate;
        }
    }

    // ---------------------------------------------------------------- static math (AOSP)

    public static boolean getRotationMatrix(float[] R, float[] I, float[] gravity, float[] geomagnetic) {
        float Ax = gravity[0];
        float Ay = gravity[1];
        float Az = gravity[2];
        final float normsqA = (Ax * Ax + Ay * Ay + Az * Az);
        final float g = 9.81f;
        final float freeFallGravitySquared = 0.01f * g * g;
        if (normsqA < freeFallGravitySquared) {
            // gravity less than 10% of normal value
            return false;
        }
        final float Ex = geomagnetic[0];
        final float Ey = geomagnetic[1];
        final float Ez = geomagnetic[2];
        float Hx = Ey * Az - Ez * Ay;
        float Hy = Ez * Ax - Ex * Az;
        float Hz = Ex * Ay - Ey * Ax;
        final float normH = (float) Math.sqrt(Hx * Hx + Hy * Hy + Hz * Hz);
        if (normH < 0.1f) {
            // device is close to free fall (or in space?), or close to magnetic north pole.
            return false;
        }
        final float invH = 1.0f / normH;
        Hx *= invH;
        Hy *= invH;
        Hz *= invH;
        final float invA = 1.0f / (float) Math.sqrt(Ax * Ax + Ay * Ay + Az * Az);
        Ax *= invA;
        Ay *= invA;
        Az *= invA;
        final float Mx = Ay * Hz - Az * Hy;
        final float My = Az * Hx - Ax * Hz;
        final float Mz = Ax * Hy - Ay * Hx;
        if (R != null) {
            if (R.length == 9) {
                R[0] = Hx;     R[1] = Hy;     R[2] = Hz;
                R[3] = Mx;     R[4] = My;     R[5] = Mz;
                R[6] = Ax;     R[7] = Ay;     R[8] = Az;
            } else if (R.length == 16) {
                R[0]  = Hx;    R[1]  = Hy;    R[2]  = Hz;   R[3]  = 0;
                R[4]  = Mx;    R[5]  = My;    R[6]  = Mz;   R[7]  = 0;
                R[8]  = Ax;    R[9]  = Ay;    R[10] = Az;   R[11] = 0;
                R[12] = 0;     R[13] = 0;     R[14] = 0;    R[15] = 1;
            }
        }
        if (I != null) {
            // compute the inclination matrix by projecting the geomagnetic vector onto the Z (gravity) and X
            // (horizontal component of geomagnetic vector) axes.
            final float invE = 1.0f / (float) Math.sqrt(Ex * Ex + Ey * Ey + Ez * Ez);
            final float c = (Ex * Mx + Ey * My + Ez * Mz) * invE;
            final float s = (Ex * Ax + Ey * Ay + Ez * Az) * invE;
            if (I.length == 9) {
                I[0] = 1;     I[1] = 0;     I[2] = 0;
                I[3] = 0;     I[4] = c;     I[5] = s;
                I[6] = 0;     I[7] = -s;    I[8] = c;
            } else if (I.length == 16) {
                I[0] = 1;     I[1] = 0;     I[2] = 0;
                I[4] = 0;     I[5] = c;     I[6] = s;
                I[8] = 0;     I[9] = -s;    I[10] = c;
                I[3] = I[7] = I[11] = I[12] = I[13] = I[14] = 0;
                I[15] = 1;
            }
        }
        return true;
    }

    public static float getInclination(float[] I) {
        if (I.length == 9) {
            return (float) Math.atan2(I[5], I[4]);
        } else {
            return (float) Math.atan2(I[6], I[5]);
        }
    }

    public static boolean remapCoordinateSystem(float[] inR, int X, int Y, float[] outR) {
        if (inR == outR) {
            final float[] temp = new float[16];
            synchronized (temp) {
                // we don't expect to have a lot of contention
                if (remapCoordinateSystemImpl(inR, X, Y, temp)) {
                    final int size = outR.length;
                    for (int i = 0; i < size; i++) {
                        outR[i] = temp[i];
                    }
                    return true;
                }
            }
        }
        return remapCoordinateSystemImpl(inR, X, Y, outR);
    }

    private static boolean remapCoordinateSystemImpl(float[] inR, int X, int Y, float[] outR) {
        /*
         * X and Y define a rotation matrix 'r':
         *
         *  (X==1)?((X&0x80)?-1:1):0    (X==2)?((X&0x80)?-1:1):0    (X==3)?((X&0x80)?-1:1):0
         *  (Y==1)?((Y&0x80)?-1:1):0    (Y==2)?((Y&0x80)?-1:1):0    (Y==3)?((X&0x80)?-1:1):0
         *                              r[0] ^ r[1]
         *
         * where the 3rd line is the vector product of the first 2 lines
         */
        final int length = outR.length;
        if (inR.length != length) {
            return false; // invalid parameter
        }
        if ((X & 0x7C) != 0 || (Y & 0x7C) != 0) {
            return false; // invalid parameter
        }
        if (((X & 0x3) == 0) || ((Y & 0x3) == 0)) {
            return false; // no axis specified
        }
        if ((X & 0x3) == (Y & 0x3)) {
            return false; // same axis specified
        }

        // Z is "the other" axis, its sign is either +/- sign(X)*sign(Y)
        // this can be calculated by exclusive-or'ing X and Y; except for
        // the sign inversion (+/-) which is calculated below.
        int Z = X ^ Y;

        // extract the axis (remove the sign), offset in the range 0 to 2.
        final int x = (X & 0x3) - 1;
        final int y = (Y & 0x3) - 1;
        final int z = (Z & 0x3) - 1;

        // compute the sign of Z (whether it needs to be inverted)
        final int axisY = (z + 1) % 3;
        final int axisZ = (z + 2) % 3;
        if (((x ^ axisY) | (y ^ axisZ)) != 0) {
            Z ^= 0x80;
        }

        final boolean sx = (X >= 0x80);
        final boolean sy = (Y >= 0x80);
        final boolean sz = (Z >= 0x80);

        // Perform R * r, in avoiding actual muls and adds.
        final int rowLength = ((length == 16) ? 4 : 3);
        for (int j = 0; j < 3; j++) {
            final int offset = j * rowLength;
            for (int i = 0; i < 3; i++) {
                if (x == i) outR[offset + i] = sx ? -inR[offset + 0] : inR[offset + 0];
                if (y == i) outR[offset + i] = sy ? -inR[offset + 1] : inR[offset + 1];
                if (z == i) outR[offset + i] = sz ? -inR[offset + 2] : inR[offset + 2];
            }
        }
        if (length == 16) {
            outR[3] = outR[7] = outR[11] = outR[12] = outR[13] = outR[14] = 0;
            outR[15] = 1;
        }
        return true;
    }

    public static float[] getOrientation(float[] R, float[] values) {
        /*
         * 4x4 (length=16) case:
         *   /  R[ 0]   R[ 1]   R[ 2]   0  \
         *   |  R[ 4]   R[ 5]   R[ 6]   0  |
         *   |  R[ 8]   R[ 9]   R[10]   0  |
         *   \      0       0       0   1  /
         *
         * 3x3 (length=9) case:
         *   /  R[ 0]   R[ 1]   R[ 2]  \
         *   |  R[ 3]   R[ 4]   R[ 5]  |
         *   \  R[ 6]   R[ 7]   R[ 8]  /
         */
        if (R.length == 9) {
            values[0] = (float) Math.atan2(R[1], R[4]);
            values[1] = (float) Math.asin(-R[7]);
            values[2] = (float) Math.atan2(-R[6], R[8]);
        } else {
            values[0] = (float) Math.atan2(R[1], R[5]);
            values[1] = (float) Math.asin(-R[9]);
            values[2] = (float) Math.atan2(-R[8], R[10]);
        }
        return values;
    }

    public static float getAltitude(float p0, float p) {
        final float coef = 1.0f / 5.255f;
        return 44330.0f * (1.0f - (float) Math.pow(p / p0, coef));
    }

    public static void getAngleChange(float[] angleChange, float[] R, float[] prevR) {
        float rd1 = 0, rd4 = 0, rd6 = 0, rd7 = 0, rd8 = 0;
        float ri0 = 0, ri1 = 0, ri2 = 0, ri3 = 0, ri4 = 0, ri5 = 0, ri6 = 0, ri7 = 0, ri8 = 0;
        float pri0 = 0, pri1 = 0, pri2 = 0, pri3 = 0, pri4 = 0, pri5 = 0, pri6 = 0, pri7 = 0, pri8 = 0;

        if (R.length == 9) {
            ri0 = R[0]; ri1 = R[1]; ri2 = R[2];
            ri3 = R[3]; ri4 = R[4]; ri5 = R[5];
            ri6 = R[6]; ri7 = R[7]; ri8 = R[8];
        } else if (R.length == 16) {
            ri0 = R[0]; ri1 = R[1]; ri2 = R[2];
            ri3 = R[4]; ri4 = R[5]; ri5 = R[6];
            ri6 = R[8]; ri7 = R[9]; ri8 = R[10];
        }

        if (prevR.length == 9) {
            pri0 = prevR[0]; pri1 = prevR[1]; pri2 = prevR[2];
            pri3 = prevR[3]; pri4 = prevR[4]; pri5 = prevR[5];
            pri6 = prevR[6]; pri7 = prevR[7]; pri8 = prevR[8];
        } else if (prevR.length == 16) {
            pri0 = prevR[0]; pri1 = prevR[1]; pri2 = prevR[2];
            pri3 = prevR[4]; pri4 = prevR[5]; pri5 = prevR[6];
            pri6 = prevR[8]; pri7 = prevR[9]; pri8 = prevR[10];
        }

        // calculate the parts of the rotation difference matrix we need
        // rd[i][j] = pri[0][i] * ri[0][j] + pri[1][i] * ri[1][j] + pri[2][i] * ri[2][j];
        rd1 = pri0 * ri1 + pri3 * ri4 + pri6 * ri7; // rd[0][1]
        rd4 = pri1 * ri1 + pri4 * ri4 + pri7 * ri7; // rd[1][1]
        rd6 = pri2 * ri0 + pri5 * ri3 + pri8 * ri6; // rd[2][0]
        rd7 = pri2 * ri1 + pri5 * ri4 + pri8 * ri7; // rd[2][1]
        rd8 = pri2 * ri2 + pri5 * ri5 + pri8 * ri8; // rd[2][2]

        angleChange[0] = (float) Math.atan2(rd1, rd4);
        angleChange[1] = (float) Math.asin(-rd7);
        angleChange[2] = (float) Math.atan2(-rd6, rd8);
    }

    public static void getRotationMatrixFromVector(float[] R, float[] rotationVector) {
        float q0;
        float q1 = rotationVector[0];
        float q2 = rotationVector[1];
        float q3 = rotationVector[2];

        if (rotationVector.length >= 4) {
            q0 = rotationVector[3];
        } else {
            q0 = 1 - q1 * q1 - q2 * q2 - q3 * q3;
            q0 = (q0 > 0) ? (float) Math.sqrt(q0) : 0;
        }

        float sqQ1 = 2 * q1 * q1;
        float sqQ2 = 2 * q2 * q2;
        float sqQ3 = 2 * q3 * q3;
        float q1Q2 = 2 * q1 * q2;
        float q3Q0 = 2 * q3 * q0;
        float q1Q3 = 2 * q1 * q3;
        float q2Q0 = 2 * q2 * q0;
        float q2Q3 = 2 * q2 * q3;
        float q1Q0 = 2 * q1 * q0;

        if (R.length == 9) {
            R[0] = 1 - sqQ2 - sqQ3;
            R[1] = q1Q2 - q3Q0;
            R[2] = q1Q3 + q2Q0;

            R[3] = q1Q2 + q3Q0;
            R[4] = 1 - sqQ1 - sqQ3;
            R[5] = q2Q3 - q1Q0;

            R[6] = q1Q3 - q2Q0;
            R[7] = q2Q3 + q1Q0;
            R[8] = 1 - sqQ1 - sqQ2;
        } else if (R.length == 16) {
            R[0] = 1 - sqQ2 - sqQ3;
            R[1] = q1Q2 - q3Q0;
            R[2] = q1Q3 + q2Q0;
            R[3] = 0.0f;

            R[4] = q1Q2 + q3Q0;
            R[5] = 1 - sqQ1 - sqQ3;
            R[6] = q2Q3 - q1Q0;
            R[7] = 0.0f;

            R[8] = q1Q3 - q2Q0;
            R[9] = q2Q3 + q1Q0;
            R[10] = 1 - sqQ1 - sqQ2;
            R[11] = 0.0f;

            R[12] = R[13] = R[14] = 0.0f;
            R[15] = 1.0f;
        }
    }

    public static void getQuaternionFromVector(float[] Q, float[] rv) {
        if (rv.length >= 4) {
            Q[0] = rv[3];
        } else {
            Q[0] = 1 - rv[0] * rv[0] - rv[1] * rv[1] - rv[2] * rv[2];
            Q[0] = (Q[0] > 0) ? (float) Math.sqrt(Q[0]) : 0;
        }
        Q[1] = rv[0];
        Q[2] = rv[1];
        Q[3] = rv[2];
    }

    public abstract static class DynamicSensorCallback {
        public DynamicSensorCallback() {}

        public void onDynamicSensorConnected(Sensor sensor) {}

        public void onDynamicSensorDisconnected(Sensor sensor) {}
    }
}
