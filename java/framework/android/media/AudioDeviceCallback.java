package android.media;

/** Devices never change here (one built-in speaker), so neither method is called. */
public abstract class AudioDeviceCallback {
    public AudioDeviceCallback() {}
    public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {}
    public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {}
}
