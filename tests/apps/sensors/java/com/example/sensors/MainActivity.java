package com.example.sensors;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventCallback;
import android.hardware.SensorEventListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * System services on the host: the motion sensors and their fusion, the battery broadcasts, rumble and power. The
 * script drives the headless sensors and battery (sensor/battery commands); the app logs what it observes and turns
 * the swatch green once every expected event arrived. On a console nothing drives the script-only checks (the exact
 * turn and tilt, the battery dropping and charging), so the screen shows live readings and what is still pending.
 */
public class MainActivity extends Activity {
    private static final String TAG = "SENSORS";

    private static final String[] EXPECTED = {
        "rest", "yaw", "tilt", "legacy", "flush", "trigger", "low", "connected", "okay", "charging", "vibrate", "power",
        "location", "absent",
    };

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Set<String> mDone = new LinkedHashSet<String>();
    private SensorManager mSm;
    private FrameLayout mRoot;
    private TextView mText;
    private TextView mStatus;
    private boolean mAllOk;

    private final float[] mAccel = new float[3];
    private final float[] mGravity = new float[3];
    private final float[] mGrv = new float[4];
    private final float[] mOrientation = new float[3];
    private final float[] mLinear = new float[3];
    private final float[] mGyro = new float[3];
    private long mStart;
    private boolean mSawSpin;
    private long mSpinStopped;
    private long mTiltSeen;

    private static String f1(float v) {
        return String.format(Locale.US, "%.1f", v + 0.0f);
    }

    private static String f2(float v) {
        return String.format(Locale.US, "%.2f", v + 0.0f);
    }

    private void log(String s) {
        Log.i(TAG, s);
    }

    private void done(String what) {
        if (!mDone.add(what)) return;
        for (String e : EXPECTED) {
            if (!mDone.contains(e)) return;
        }
        mAllOk = true;
        log("all ok");
        mText.setText("all ok");
        mRoot.setBackgroundColor(0xFF43A047);
        showStatus();
    }

    /** Live readings and the pending checks, so a run without the script does not look hung. */
    private void showStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("accel ").append(f1(mAccel[0])).append(' ').append(f1(mAccel[1])).append(' ').append(f1(mAccel[2]));
        sb.append("   gyro ").append(f1(mGyro[0])).append(' ').append(f1(mGyro[1])).append(' ').append(f1(mGyro[2]));
        sb.append("\ngravity ").append(f1(mGravity[0])).append(' ').append(f1(mGravity[1])).append(' ')
                .append(f1(mGravity[2]));
        sb.append("   azimuth ").append(Math.round(mOrientation[0])).append(" pitch ")
                .append(Math.round(mOrientation[1])).append(" roll ").append(Math.round(mOrientation[2]));
        StringBuilder pending = new StringBuilder();
        for (String e : EXPECTED) {
            if (!mDone.contains(e)) pending.append(' ').append(e);
        }
        sb.append("\ndone ").append(mDone.size()).append('/').append(EXPECTED.length);
        if (pending.length() > 0) {
            sb.append(", waiting for").append(pending);
            sb.append("\n(yaw, tilt and the battery checks are driven by the host test script)");
        }
        mStatus.setText(sb.toString());
    }

    private final Runnable mTick = new Runnable() {
        public void run() {
            showStatus();
            if (!mAllOk) mMain.postDelayed(this, 250);
        }
    };

    private final SensorEventListener mListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            switch (e.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    System.arraycopy(e.values, 0, mAccel, 0, 3);
                    if (mTiltSeen == 0 && e.values[1] > 5) mTiltSeen = e.timestamp;
                    break;
                case Sensor.TYPE_GRAVITY:
                    System.arraycopy(e.values, 0, mGravity, 0, 3);
                    break;
                case Sensor.TYPE_LINEAR_ACCELERATION:
                    System.arraycopy(e.values, 0, mLinear, 0, 3);
                    break;
                case Sensor.TYPE_GAME_ROTATION_VECTOR:
                    System.arraycopy(e.values, 0, mGrv, 0, 4);
                    break;
                case Sensor.TYPE_ORIENTATION:
                    System.arraycopy(e.values, 0, mOrientation, 0, 3);
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    System.arraycopy(e.values, 0, mGyro, 0, 3);
                    if (e.values[2] > 1) {
                        mSawSpin = true;
                    } else if (mSawSpin && mSpinStopped == 0) {
                        mSpinStopped = e.timestamp;
                    }
                    break;
                default:
                    break;
            }
            check(e.timestamp);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) log("accuracy accel " + accuracy);
        }
    };

    private void check(long now) {
        if (!mDone.contains("rest") && now - mStart > 800000000L) {
            log("rest accel " + f1(mAccel[0]) + " " + f1(mAccel[1]) + " " + f1(mAccel[2]) + " gravity " + f1(mGravity[0])
                    + " " + f1(mGravity[1]) + " " + f1(mGravity[2]) + " linear " + f1(mLinear[0]) + " " + f1(mLinear[1])
                    + " " + f1(mLinear[2]) + " grv " + f2(mGrv[0]) + " " + f2(mGrv[1]) + " " + f2(mGrv[2]) + " "
                    + f2(mGrv[3]));
            done("rest");
        }
        if (!mDone.contains("yaw") && mSpinStopped != 0 && now - mSpinStopped > 200000000L) {
            float az = mOrientation[0];
            float[] r = new float[9];
            float[] o = new float[3];
            SensorManager.getRotationMatrixFromVector(r, mGrv);
            SensorManager.getOrientation(r, o);
            log("yaw " + (Math.abs(az - 270) < 4 ? "ok" : "bad azimuth=" + az) + " grv " + f2(mGrv[0]) + " "
                    + f2(mGrv[1]) + " " + f2(mGrv[2]) + " " + f2(mGrv[3]) + " getOrientation "
                    + Math.round(Math.toDegrees(o[0])));
            done("yaw");
        }
        if (!mDone.contains("tilt") && mTiltSeen != 0) {
            boolean close = Math.abs(mGravity[0]) < 0.05f && Math.abs(mGravity[1] - 6.934f) < 0.05f
                    && Math.abs(mGravity[2] - 6.934f) < 0.05f;
            if (close || now - mTiltSeen > 8000000000L) {
                log("tilt " + (close ? "ok" : "timeout") + " pitch=" + Math.round(mOrientation[1]) + " roll="
                        + Math.round(mOrientation[2]) + " gravity " + f1(mGravity[0]) + " " + f1(mGravity[1]) + " "
                        + f1(mGravity[2]));
                if (close) done("tilt");
                mSm.unregisterListener(mListener);
                log("sensors off");
            }
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        mRoot = new FrameLayout(this);
        mRoot.setBackgroundColor(Color.GRAY);
        mText = new TextView(this);
        mText.setTextColor(Color.WHITE);
        mText.setTextSize(24);
        mText.setText("sensing...");
        mStatus = new TextView(this);
        mStatus.setTextColor(Color.WHITE);
        mStatus.setTextSize(14);
        mStatus.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.addView(mText, new LinearLayout.LayoutParams(-2, -2));
        column.addView(mStatus, new LinearLayout.LayoutParams(-2, -2));
        mRoot.addView(column, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setContentView(mRoot);
        mStart = System.nanoTime();

        mSm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        int[] types = {Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GRAVITY,
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_ORIENTATION, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_LIGHT};
        StringBuilder sb = new StringBuilder("have");
        for (int t : types) sb.append(' ').append(t).append('=').append(mSm.getDefaultSensor(t) != null);
        log(sb.toString());
        log("list " + mSm.getSensorList(Sensor.TYPE_ALL).size() + " accel " + mSm.getDefaultSensor(1).getStringType()
                + " minDelay " + mSm.getDefaultSensor(1).getMinDelay());

        mSm.registerListener(mListener, mSm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
        mSm.registerListener(mListener, mSm.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
        mSm.registerListener(mListener, mSm.getDefaultSensor(Sensor.TYPE_GRAVITY), SensorManager.SENSOR_DELAY_GAME);
        mSm.registerListener(mListener, mSm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
                SensorManager.SENSOR_DELAY_GAME);
        mSm.registerListener(mListener, mSm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_GAME);
        mSm.registerListener(mListener, mSm.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_UI);
        boolean twice = mSm.registerListener(mListener, mSm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);
        log("register twice " + twice + " null " + mSm.registerListener(mListener, null, 0));

        legacyAndFlush();
        battery();
        vibrate();
        power();
        absent();
        mMain.post(mTick);
    }

    /** Location (off), telephony and cameras (none) answer without crashing. */
    private void absent() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        log("location enabled=" + lm.isLocationEnabled() + " gps=" + lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                + " all=" + lm.getAllProviders() + " enabled=" + lm.getProviders(true) + " last="
                + lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                log("unexpected fix " + location);
            }

            @Override
            public void onProviderDisabled(String provider) {
                log("location disabled " + provider);
                done("location");
            }
        });
        try {
            lm.requestLocationUpdates("moon", 1000, 0, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {}
            });
        } catch (IllegalArgumentException e) {
            log("location " + e.getMessage());
        }
        float[] d = new float[3];
        Location.distanceBetween(51.5007, -0.1246, 48.8584, 2.2945, d);
        Location a = new Location("test");
        a.setLatitude(37.4220);
        a.setLongitude(-122.0841);
        a.setBearing(-90);
        log("distance " + Math.round(d[0] / 1000) + " km bearing " + Math.round(d[1]) + " convert "
                + Location.convert(-122.0841, Location.FORMAT_SECONDS) + " back "
                + String.format(Locale.US, "%.4f", Location.convert("-122:5:2.76")) + " set bearing " + a.getBearing()
                + " hasAccuracy " + a.hasAccuracy());

        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        log("phone type=" + tm.getPhoneType() + " sim=" + tm.getSimState() + " operator='" + tm.getNetworkOperatorName()
                + "' iso='" + tm.getSimCountryIso().toUpperCase(Locale.US) + "' id=" + tm.getDeviceId() + " net="
                + tm.getNetworkType() + " sms=" + tm.isSmsCapable());

        CameraManager cm = getSystemService(CameraManager.class);
        int ids = -1;
        try {
            ids = cm.getCameraIdList().length;
            cm.setTorchMode("0", true);
        } catch (CameraAccessException e) {
            log("camera access " + e.getReason());
        } catch (IllegalArgumentException e) {
            log("torch " + e.getMessage());
        }
        Camera c = Camera.open();
        boolean threw = false;
        try {
            Camera.open(0);
        } catch (RuntimeException e) {
            threw = true;
        }
        log("camera n=" + Camera.getNumberOfCameras() + " open=" + c + " open0threw=" + threw + " ids="
                + ids + " feature=" + getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_CAMERA_ANY) + " accel="
                + getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER));
        done("absent");
    }

    @SuppressWarnings("deprecation")
    private void legacyAndFlush() {
        final SensorListener legacy = new SensorListener() {
            @Override
            public void onSensorChanged(int sensor, float[] values) {
                if (sensor == SensorManager.SENSOR_ACCELEROMETER && !mDone.contains("legacy")) {
                    log("legacy accel " + f1(values[2]) + " n=" + values.length);
                    done("legacy");
                    mSm.unregisterListener(this);
                }
            }

            @Override
            public void onAccuracyChanged(int sensor, int accuracy) {}
        };
        log("legacy sensors " + mSm.getSensors() + " register " + mSm.registerListener(legacy,
                SensorManager.SENSOR_ACCELEROMETER));

        final SensorEventCallback cb = new SensorEventCallback() {
            @Override
            public void onFlushCompleted(Sensor sensor) {
                log("flush " + sensor.getName());
                done("flush");
                mSm.unregisterListener(this);
            }
        };
        mSm.registerListener(cb, mSm.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_NORMAL);
        log("flush started " + mSm.flush(cb) + " unknown " + mSm.flush(new SensorEventCallback() {}));

        try {
            mSm.requestTriggerSensor(new TriggerEventListener() {
                @Override
                public void onTrigger(TriggerEvent event) {}
            }, mSm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION));
            log("trigger accepted");
        } catch (IllegalArgumentException e) {
            log("trigger " + e.getClass().getSimpleName());
            done("trigger");
        }

        float[] r = new float[9];
        float[] i = new float[9];
        boolean ok = SensorManager.getRotationMatrix(r, i, new float[] {0, 0, 9.81f}, new float[] {0, 22, -40});
        float[] o = SensorManager.getOrientation(r, new float[3]);
        float[] out = new float[9];
        SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, out);
        log("math " + ok + " azimuth " + Math.round(Math.toDegrees(o[0])) + " inclination "
                + Math.round(Math.toDegrees(SensorManager.getInclination(i))) + " remap " + f1(out[0]) + " " + f1(out[1])
                + " altitude " + Math.round(SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, 900)));
    }

    private void battery() {
        Intent sticky = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        log("battery level=" + sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) + " scale="
                + sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1) + " plugged="
                + sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) + " status="
                + sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1) + " capacity="
                + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + " charging=" + bm.isCharging());
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        f.addAction(Intent.ACTION_BATTERY_LOW);
        f.addAction(Intent.ACTION_BATTERY_OKAY);
        final BatteryManager fbm = bm;
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String a = intent.getAction();
                if (Intent.ACTION_BATTERY_CHANGED.equals(a)) {
                    log("changed level=" + intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) + " plugged="
                            + intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) + " status="
                            + intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) + " low="
                            + intent.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false) + " initial="
                            + isInitialStickyBroadcast());
                    if (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
                            && fbm.isCharging()) {
                        log("charging level " + fbm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
                        done("charging");
                    }
                } else if (Intent.ACTION_BATTERY_LOW.equals(a)) {
                    log("BATTERY_LOW");
                    done("low");
                } else if (Intent.ACTION_BATTERY_OKAY.equals(a)) {
                    log("BATTERY_OKAY");
                    done("okay");
                } else if (Intent.ACTION_POWER_CONNECTED.equals(a)) {
                    log("POWER_CONNECTED");
                    done("connected");
                } else {
                    log("other " + a);
                }
            }
        }, f);
    }

    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        VibratorManager vm = getSystemService(VibratorManager.class);
        log("vibrator has=" + v.hasVibrator() + " amplitude=" + v.hasAmplitudeControl() + " ids="
                + vm.getVibratorIds().length + " click="
                + v.areAllEffectsSupported(VibrationEffect.EFFECT_CLICK));
        // off 0, on 40, off 60, on 40 with amplitude 200
        v.vibrate(VibrationEffect.createWaveform(new long[] {0, 40, 60, 40}, new int[] {0, 255, 0, 200}, -1));
        mMain.postDelayed(new Runnable() {
            public void run() {
                try {
                    VibrationEffect.createOneShot(0, 10);
                } catch (IllegalArgumentException e) {
                    log("oneshot rejects 0");
                    done("vibrate");
                }
            }
        }, 500);
    }

    private void power() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sensors:test");
        wl.acquire();
        wl.acquire();
        wl.release();
        boolean held = wl.isHeld();
        wl.release();
        log("power interactive=" + pm.isInteractive() + " save=" + pm.isPowerSaveMode() + " held " + held + " then "
                + wl.isHeld());
        pm.addThermalStatusListener(new PowerManager.OnThermalStatusChangedListener() {
            @Override
            public void onThermalStatusChanged(int status) {
                log("thermal " + status);
                done("power");
            }
        });
    }
}
