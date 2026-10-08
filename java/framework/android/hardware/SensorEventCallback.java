package android.hardware;

public abstract class SensorEventCallback implements SensorEventListener2 {
    public SensorEventCallback() {}

    @Override public void onSensorChanged(SensorEvent event) {}
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public void onFlushCompleted(Sensor sensor) {}
    public void onSensorAdditionalInfo(SensorAdditionalInfo info) {}
}
