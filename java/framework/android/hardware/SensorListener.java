package android.hardware;

/** @deprecated Use {@link SensorEventListener} instead. */
@Deprecated
public interface SensorListener {
    void onSensorChanged(int sensor, float[] values);
    void onAccuracyChanged(int sensor, int accuracy);
}
