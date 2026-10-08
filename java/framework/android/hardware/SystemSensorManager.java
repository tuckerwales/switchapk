package android.hardware;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.PlatformInput;
import java.util.ArrayList;
import java.util.List;

/**
 * framework-internal (hidden in AOSP). The process-wide sensor service behind Context.SENSOR_SERVICE (WS15).
 *
 * The platform samples an accelerometer and a gyroscope (platform_sensor_set_rate) and posts PEV_SENSOR events,
 * which PlatformInput hands to {@link #onSample} on the main thread. Gravity, linear acceleration, the rotation
 * vectors and the legacy orientation sensor are computed here from those two with a Mahony complementary filter:
 * the gyroscope is integrated and the accelerometer pulls the tilt back toward gravity. There is no magnetometer, so
 * the heading of the rotation vector starts at 0 and drifts with the gyroscope; TYPE_ROTATION_VECTOR reports the
 * same orientation as TYPE_GAME_ROTATION_VECTOR with an unknown (-1) heading accuracy.
 */
public final class SystemSensorManager extends SensorManager implements PlatformInput.SensorSink {
    private static final String TAG = "SensorManager";

    /** Platform sensor types (platform.h PLATFORM_SENSOR_*). */
    private static final int BASE_ACCEL = Sensor.TYPE_ACCELEROMETER;
    private static final int BASE_GYRO = Sensor.TYPE_GYROSCOPE;

    private static final int MIN_DELAY_US = 5000;
    private static final int MAX_DELAY_US = 1000000;
    /** Proportional gain of the accelerometer correction (rad/s per unit of tilt error). */
    private static final float KP = 1.0f;

    private static SystemSensorManager sInstance;

    private final List<Sensor> mSensors = new ArrayList<Sensor>();
    private final ArrayList<Entry> mEntries = new ArrayList<Entry>();
    private final ArrayList<LegacyEntry> mLegacy = new ArrayList<LegacyEntry>();
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final int[] mRate = new int[2]; // current platform period per base sensor, 0 = off

    // Fusion state, main thread only.
    private final float[] mAccel = new float[3];
    private boolean mHaveAccel;
    private boolean mFusionReady;
    private long mLastGyroNs;
    // device-to-world rotation (R * v_device = v_world), world z up
    private float mQw = 1, mQx, mQy, mQz;

    private static final class Entry {
        final SensorEventListener listener;
        final Sensor sensor;
        final int periodUs;
        final Handler handler; // null: deliver on the main thread
        long lastNs;
        int accuracy;

        Entry(SensorEventListener listener, Sensor sensor, int periodUs, Handler handler) {
            this.listener = listener;
            this.sensor = sensor;
            this.periodUs = periodUs;
            this.handler = handler;
        }
    }

    /** A SensorListener (deprecated API) registration; it rides on ordinary entries through an adapter. */
    private static final class LegacyEntry implements SensorEventListener {
        final SensorListener listener;
        final int sensors; // SENSOR_ACCELEROMETER and/or SENSOR_ORIENTATION(_RAW)

        LegacyEntry(SensorListener listener, int sensors) {
            this.listener = listener;
            this.sensors = sensors;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            int legacy = legacyType(event.sensor.getType());
            float[] v = new float[6];
            v[0] = event.values[0];
            v[1] = event.values[1];
            v[2] = event.values[2];
            if ((sensors & legacy) != 0) listener.onSensorChanged(legacy, v);
            if (legacy == SENSOR_ORIENTATION && (sensors & SENSOR_ORIENTATION_RAW) != 0) {
                listener.onSensorChanged(SENSOR_ORIENTATION_RAW, v.clone());
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            int legacy = legacyType(sensor.getType());
            if ((sensors & legacy) != 0) listener.onAccuracyChanged(legacy, accuracy);
        }

        private static int legacyType(int type) {
            return type == Sensor.TYPE_ORIENTATION ? SENSOR_ORIENTATION : SENSOR_ACCELEROMETER;
        }
    }

    private SystemSensorManager() {
        int mask = nGetSensorMask();
        boolean accel = (mask & (1 << BASE_ACCEL)) != 0;
        boolean gyro = (mask & (1 << BASE_GYRO)) != 0;
        int handle = 1;
        // The Joy-Con / Pro Controller IMU: +-8 G and +-2000 degrees per second.
        if (accel) {
            add("Accelerometer", "switchapk", Sensor.TYPE_ACCELEROMETER, Sensor.STRING_TYPE_ACCELEROMETER, handle++,
                    78.4532f, 0.0023942f, 0.15f);
        }
        if (gyro) {
            add("Gyroscope", "switchapk", Sensor.TYPE_GYROSCOPE, Sensor.STRING_TYPE_GYROSCOPE, handle++, 34.906586f,
                    0.0010653f, 0.15f);
        }
        if (accel && gyro) {
            add("Gravity Sensor", "AOSP", Sensor.TYPE_GRAVITY, Sensor.STRING_TYPE_GRAVITY, handle++,
                    STANDARD_GRAVITY, 0.0023942f, 0.3f);
            add("Linear Acceleration Sensor", "AOSP", Sensor.TYPE_LINEAR_ACCELERATION,
                    Sensor.STRING_TYPE_LINEAR_ACCELERATION, handle++, 78.4532f, 0.0023942f, 0.3f);
            add("Rotation Vector Sensor", "AOSP", Sensor.TYPE_ROTATION_VECTOR, Sensor.STRING_TYPE_ROTATION_VECTOR,
                    handle++, 1f, 5.9604645e-8f, 0.3f);
            add("Game Rotation Vector Sensor", "AOSP", Sensor.TYPE_GAME_ROTATION_VECTOR,
                    Sensor.STRING_TYPE_GAME_ROTATION_VECTOR, handle++, 1f, 5.9604645e-8f, 0.3f);
            add("Orientation Sensor", "AOSP", Sensor.TYPE_ORIENTATION, Sensor.STRING_TYPE_ORIENTATION, handle++,
                    360f, 0.00390625f, 0.3f);
        }
        if (gyro) {
            add("Gyroscope Uncalibrated", "switchapk", Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
                    Sensor.STRING_TYPE_GYROSCOPE_UNCALIBRATED, handle++, 34.906586f, 0.0010653f, 0.15f);
        }
        if (accel) {
            add("Accelerometer Uncalibrated", "switchapk", Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
                    Sensor.STRING_TYPE_ACCELEROMETER_UNCALIBRATED, handle++, 78.4532f, 0.0023942f, 0.15f);
        }
        PlatformInput.setSensorSink(this);
    }

    private void add(String name, String vendor, int type, String stringType, int handle, float range, float res,
            float power) {
        mSensors.add(new Sensor(name, vendor, type, stringType, 1, handle, range, res, power, MIN_DELAY_US,
                MAX_DELAY_US));
    }

    /** framework-internal: the instance behind Context.SENSOR_SERVICE. */
    public static synchronized SystemSensorManager getInstance() {
        if (sInstance == null) sInstance = new SystemSensorManager();
        return sInstance;
    }

    static native int nGetSensorMask();

    static native void nSetRate(int type, int periodUs);

    @Override
    List<Sensor> getFullSensorList() {
        return mSensors;
    }

    // ---------------------------------------------------------------- registration

    @Override
    boolean registerListenerImpl(SensorEventListener listener, Sensor sensor, int delayUs, Handler handler,
            int maxReportLatencyUs) {
        if (listener == null || sensor == null) {
            Log.e(TAG, "sensor or listener is null");
            return false;
        }
        if (!mSensors.contains(sensor)) return false;
        if (delayUs < 0) throw new IllegalArgumentException("samplingPeriodUs must be >= 0");
        int period = Math.max(MIN_DELAY_US, Math.min(MAX_DELAY_US, delayUs));
        synchronized (mEntries) {
            for (Entry e : mEntries) {
                if (e.listener == listener && e.sensor == sensor) return false;
            }
            Entry e = new Entry(listener, sensor, period, handler);
            e.accuracy = SENSOR_STATUS_UNRELIABLE;
            mEntries.add(e);
        }
        updateRates();
        return true;
    }

    @Override
    void unregisterListenerImpl(SensorEventListener listener, Sensor sensor) {
        boolean changed = false;
        synchronized (mEntries) {
            for (int i = mEntries.size() - 1; i >= 0; i--) {
                Entry e = mEntries.get(i);
                if (e.listener == listener && (sensor == null || e.sensor == sensor)) {
                    mEntries.remove(i);
                    changed = true;
                }
            }
        }
        if (changed) updateRates();
    }

    @Override
    boolean flushImpl(SensorEventListener listener) {
        if (listener == null) throw new IllegalArgumentException("listener cannot be null");
        final ArrayList<Entry> mine = new ArrayList<Entry>();
        synchronized (mEntries) {
            for (Entry e : mEntries) {
                if (e.listener == listener) mine.add(e);
            }
        }
        if (mine.isEmpty()) return false;
        if (!(listener instanceof SensorEventListener2)) return true;
        for (final Entry e : mine) {
            // always later, as AOSP's flush completes through the event queue
            (e.handler != null ? e.handler : mMain).post(new Runnable() {
                public void run() {
                    ((SensorEventListener2) e.listener).onFlushCompleted(e.sensor);
                }
            });
        }
        return true;
    }

    @Override
    boolean registerLegacyListener(SensorListener listener, int sensors, int rate) {
        boolean ok = false;
        LegacyEntry entry = null;
        synchronized (mEntries) {
            for (LegacyEntry l : mLegacy) {
                if (l.listener == listener) return false;
            }
        }
        if ((sensors & SENSOR_ACCELEROMETER) != 0) {
            Sensor s = getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (s != null) {
                entry = new LegacyEntry(listener, sensors);
                ok |= registerListener(entry, s, rate);
            }
        }
        if ((sensors & (SENSOR_ORIENTATION | SENSOR_ORIENTATION_RAW)) != 0) {
            Sensor s = getDefaultSensor(Sensor.TYPE_ORIENTATION);
            if (s != null) {
                if (entry == null) entry = new LegacyEntry(listener, sensors);
                ok |= registerListener(entry, s, rate);
            }
        }
        if (ok) {
            synchronized (mEntries) {
                mLegacy.add(entry);
            }
        }
        return ok;
    }

    @Override
    void unregisterLegacyListener(SensorListener listener, int sensors) {
        LegacyEntry found = null;
        synchronized (mEntries) {
            for (LegacyEntry l : mLegacy) {
                if (l.listener == listener) found = l;
            }
        }
        if (found == null) return;
        if ((sensors & SENSOR_ACCELEROMETER) != 0) {
            unregisterListener(found, getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
        }
        if ((sensors & (SENSOR_ORIENTATION | SENSOR_ORIENTATION_RAW)) != 0) {
            unregisterListener(found, getDefaultSensor(Sensor.TYPE_ORIENTATION));
        }
        synchronized (mEntries) {
            boolean left = false;
            for (Entry e : mEntries) {
                if (e.listener == found) left = true;
            }
            if (!left) mLegacy.remove(found);
        }
    }

    /** Whether events of a sensor type are computed from the given base sensor. */
    private static boolean uses(int type, int base) {
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                return base == BASE_ACCEL;
            case Sensor.TYPE_GYROSCOPE:
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                return base == BASE_GYRO;
            default:
                return true; // fused: both
        }
    }

    /** Runs the platform sensors at the fastest period any listener needs, or stops them. */
    private void updateRates() {
        int[] want = new int[2];
        synchronized (mEntries) {
            for (Entry e : mEntries) {
                for (int b = 0; b < 2; b++) {
                    int base = b == 0 ? BASE_ACCEL : BASE_GYRO;
                    if (uses(e.sensor.getType(), base) && (want[b] == 0 || e.periodUs < want[b])) {
                        want[b] = e.periodUs;
                    }
                }
            }
        }
        for (int b = 0; b < 2; b++) {
            if (want[b] == mRate[b]) continue;
            int base = b == 0 ? BASE_ACCEL : BASE_GYRO;
            if (want[b] == 0 && base == BASE_GYRO) mFusionReady = false;
            if (want[b] == 0 && base == BASE_ACCEL) mHaveAccel = false;
            mRate[b] = want[b];
            nSetRate(base, want[b]);
        }
    }

    // ---------------------------------------------------------------- samples

    @Override
    public void onSensor(int type, float x, float y, float z, long timeNs) {
        onSample(type, x, y, z, timeNs);
    }

    /** One platform sample, on the main thread. Delivers it and whatever is computed from it. */
    void onSample(int type, float x, float y, float z, long timeNs) {
        if (type == BASE_ACCEL) {
            mAccel[0] = x;
            mAccel[1] = y;
            mAccel[2] = z;
            if (!mHaveAccel) {
                mHaveAccel = true;
                if (!mFusionReady) initFromAccel();
            }
            float[] v = {x, y, z};
            deliver(Sensor.TYPE_ACCELEROMETER, v, timeNs);
            deliver(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, new float[] {x, y, z, 0, 0, 0}, timeNs);
        } else if (type == BASE_GYRO) {
            deliver(Sensor.TYPE_GYROSCOPE, new float[] {x, y, z}, timeNs);
            deliver(Sensor.TYPE_GYROSCOPE_UNCALIBRATED, new float[] {x, y, z, 0, 0, 0}, timeNs);
            if (!mHaveAccel) return;
            if (!mFusionReady) {
                mFusionReady = true;
                mLastGyroNs = timeNs;
            } else {
                float dt = (timeNs - mLastGyroNs) / 1e9f;
                mLastGyroNs = timeNs;
                if (dt > 0 && dt < 0.5f) integrate(x, y, z, dt);
            }
            deliverFused(timeNs);
        }
    }

    /** Tilt from the accelerometer alone, heading 0: the shortest rotation taking the measured up to world z. */
    private void initFromAccel() {
        float ax = mAccel[0], ay = mAccel[1], az = mAccel[2];
        float n = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (n < 1e-3f) {
            mQw = 1;
            mQx = mQy = mQz = 0;
            return;
        }
        ax /= n;
        ay /= n;
        az /= n;
        if (az < -0.9999f) {
            mQw = 0;
            mQx = 1;
            mQy = mQz = 0;
            return;
        }
        // q = (1 + u.v, u x v) for u = a, v = (0, 0, 1)
        float w = 1 + az, qx = ay, qy = -ax;
        float len = (float) Math.sqrt(w * w + qx * qx + qy * qy);
        mQw = w / len;
        mQx = qx / len;
        mQy = qy / len;
        mQz = 0;
    }

    /** One Mahony step: correct the body rate toward the measured gravity, then integrate it. */
    private void integrate(float gx, float gy, float gz, float dt) {
        float qw = mQw, qx = mQx, qy = mQy, qz = mQz;
        float ax = mAccel[0], ay = mAccel[1], az = mAccel[2];
        float n = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        // Only trust the accelerometer near 1 G, when it mostly measures gravity.
        if (n > 0.5f * STANDARD_GRAVITY && n < 1.5f * STANDARD_GRAVITY) {
            ax /= n;
            ay /= n;
            az /= n;
            // estimated up in device coordinates: third row of R
            float vx = 2 * (qx * qz - qw * qy);
            float vy = 2 * (qy * qz + qw * qx);
            float vz = 1 - 2 * (qx * qx + qy * qy);
            float ex = ay * vz - az * vy;
            float ey = az * vx - ax * vz;
            float ez = ax * vy - ay * vx;
            gx += KP * ex;
            gy += KP * ey;
            gz += KP * ez;
        }
        // q <- q * exp(w dt / 2)
        float hx = gx * dt * 0.5f, hy = gy * dt * 0.5f, hz = gz * dt * 0.5f;
        float theta = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        float dw, dx, dy, dz;
        if (theta < 1e-6f) {
            dw = 1;
            dx = hx;
            dy = hy;
            dz = hz;
        } else {
            float s = (float) Math.sin(theta) / theta;
            dw = (float) Math.cos(theta);
            dx = hx * s;
            dy = hy * s;
            dz = hz * s;
        }
        float rw = qw * dw - qx * dx - qy * dy - qz * dz;
        float rx = qw * dx + qx * dw + qy * dz - qz * dy;
        float ry = qw * dy - qx * dz + qy * dw + qz * dx;
        float rz = qw * dz + qx * dy - qy * dx + qz * dw;
        float len = (float) Math.sqrt(rw * rw + rx * rx + ry * ry + rz * rz);
        mQw = rw / len;
        mQx = rx / len;
        mQy = ry / len;
        mQz = rz / len;
    }

    private void deliverFused(long timeNs) {
        if (!wants(Sensor.TYPE_GRAVITY) && !wants(Sensor.TYPE_LINEAR_ACCELERATION) && !wants(Sensor.TYPE_ROTATION_VECTOR)
                && !wants(Sensor.TYPE_GAME_ROTATION_VECTOR) && !wants(Sensor.TYPE_ORIENTATION)) {
            return;
        }
        // AOSP rotation vectors keep the scalar part non-negative.
        float s = mQw < 0 ? -1 : 1;
        float qw = mQw * s, qx = mQx * s, qy = mQy * s, qz = mQz * s;
        float g0 = STANDARD_GRAVITY * 2 * (qx * qz - qw * qy);
        float g1 = STANDARD_GRAVITY * 2 * (qy * qz + qw * qx);
        float g2 = STANDARD_GRAVITY * (1 - 2 * (qx * qx + qy * qy));
        deliver(Sensor.TYPE_GRAVITY, new float[] {g0, g1, g2}, timeNs);
        deliver(Sensor.TYPE_LINEAR_ACCELERATION, new float[] {mAccel[0] - g0, mAccel[1] - g1, mAccel[2] - g2},
                timeNs);
        deliver(Sensor.TYPE_ROTATION_VECTOR, new float[] {qx, qy, qz, qw, -1f}, timeNs);
        deliver(Sensor.TYPE_GAME_ROTATION_VECTOR, new float[] {qx, qy, qz, qw}, timeNs);
        if (wants(Sensor.TYPE_ORIENTATION)) {
            float[] r = new float[9];
            getRotationMatrixFromVector(r, new float[] {qx, qy, qz, qw});
            float azimuth = (float) Math.toDegrees(Math.atan2(r[1], r[4]));
            if (azimuth < 0) azimuth += 360;
            float pitch = (float) Math.toDegrees(Math.atan2(-r[7], r[8]));
            float roll = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, r[6]))));
            deliver(Sensor.TYPE_ORIENTATION, new float[] {azimuth, pitch, roll}, timeNs);
        }
    }

    private boolean wants(int type) {
        synchronized (mEntries) {
            for (Entry e : mEntries) {
                if (e.sensor.getType() == type) return true;
            }
        }
        return false;
    }

    private void deliver(int type, float[] values, long timeNs) {
        ArrayList<Entry> targets = null;
        synchronized (mEntries) {
            for (Entry e : mEntries) {
                if (e.sensor.getType() != type) continue;
                // pace each listener at its own rate (with some slack for jitter)
                if (e.lastNs != 0 && timeNs - e.lastNs < e.periodUs * 900L) continue;
                e.lastNs = timeNs;
                if (targets == null) targets = new ArrayList<Entry>();
                targets.add(e);
            }
        }
        if (targets == null) return;
        for (final Entry e : targets) {
            final SensorEvent ev = new SensorEvent(Sensor.valuesLength(type));
            System.arraycopy(values, 0, ev.values, 0, Math.min(values.length, ev.values.length));
            ev.sensor = e.sensor;
            ev.timestamp = timeNs;
            ev.accuracy = SENSOR_STATUS_ACCURACY_HIGH;
            post(e, new Runnable() {
                public void run() {
                    if (!isRegistered(e)) return;
                    if (e.accuracy != ev.accuracy) {
                        e.accuracy = ev.accuracy;
                        e.listener.onAccuracyChanged(e.sensor, ev.accuracy);
                    }
                    e.listener.onSensorChanged(ev);
                }
            });
        }
    }

    private boolean isRegistered(Entry e) {
        synchronized (mEntries) {
            return mEntries.contains(e);
        }
    }

    private void post(Entry e, Runnable r) {
        Handler h = e.handler != null ? e.handler : mMain;
        if (h.getLooper() == Looper.myLooper()) {
            r.run();
        } else {
            h.post(r);
        }
    }

}
