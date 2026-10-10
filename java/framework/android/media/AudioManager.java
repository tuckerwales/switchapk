package android.media;

/**
 * Audio routing, stream volume and focus. Focus requests are granted and
 * never taken back. Stream volume scales the mixer. The default index is
 * the maximum, so a stream that nobody touches plays at full gain.
 */
public class AudioManager {
    public static final int STREAM_VOICE_CALL = 0;
    public static final int STREAM_SYSTEM = 1;
    public static final int STREAM_RING = 2;
    public static final int STREAM_MUSIC = 3;
    public static final int STREAM_ALARM = 4;
    public static final int STREAM_NOTIFICATION = 5;
    public static final int STREAM_DTMF = 8;
    public static final int STREAM_ACCESSIBILITY = 10;

    public static final int AUDIOFOCUS_NONE = 0;
    public static final int AUDIOFOCUS_GAIN = 1;
    public static final int AUDIOFOCUS_GAIN_TRANSIENT = 2;
    public static final int AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK = 3;
    public static final int AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE = 4;
    public static final int AUDIOFOCUS_LOSS = -1;
    public static final int AUDIOFOCUS_LOSS_TRANSIENT = -2;
    public static final int AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK = -3;

    public static final int AUDIOFOCUS_REQUEST_FAILED = 0;
    public static final int AUDIOFOCUS_REQUEST_GRANTED = 1;
    public static final int AUDIOFOCUS_REQUEST_DELAYED = 2;

    public static final int ADJUST_LOWER = -1;
    public static final int ADJUST_SAME = 0;
    public static final int ADJUST_RAISE = 1;
    public static final int ADJUST_MUTE = -100;
    public static final int ADJUST_UNMUTE = 100;
    public static final int ADJUST_TOGGLE_MUTE = 101;

    public static final String ACTION_AUDIO_BECOMING_NOISY = "android.media.AUDIO_BECOMING_NOISY";
    public static final String ACTION_HDMI_AUDIO_PLUG = "android.media.action.HDMI_AUDIO_PLUG";
    public static final String ACTION_HEADSET_PLUG = "android.intent.action.HEADSET_PLUG";
    public static final String ACTION_MICROPHONE_MUTE_CHANGED = "android.media.action.MICROPHONE_MUTE_CHANGED";
    public static final String ACTION_SCO_AUDIO_STATE_CHANGED = "android.media.SCO_AUDIO_STATE_CHANGED";
    public static final String ACTION_SCO_AUDIO_STATE_UPDATED = "android.media.ACTION_SCO_AUDIO_STATE_UPDATED";
    public static final String ACTION_SPEAKERPHONE_STATE_CHANGED = "android.media.action.SPEAKERPHONE_STATE_CHANGED";
    public static final int AUDIO_SESSION_ID_GENERATE = 0;
    public static final int DIRECT_PLAYBACK_BITSTREAM_SUPPORTED = 4;
    public static final int DIRECT_PLAYBACK_NOT_SUPPORTED = 0;
    public static final int DIRECT_PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED = 3;
    public static final int DIRECT_PLAYBACK_OFFLOAD_SUPPORTED = 1;
    public static final int ENCODED_SURROUND_OUTPUT_ALWAYS = 2;
    public static final int ENCODED_SURROUND_OUTPUT_AUTO = 0;
    public static final int ENCODED_SURROUND_OUTPUT_MANUAL = 3;
    public static final int ENCODED_SURROUND_OUTPUT_NEVER = 1;
    public static final int ENCODED_SURROUND_OUTPUT_UNKNOWN = -1;
    public static final int ERROR = -1;
    public static final int ERROR_DEAD_OBJECT = -6;
    public static final String EXTRA_AUDIO_PLUG_STATE = "android.media.extra.AUDIO_PLUG_STATE";
    public static final String EXTRA_ENCODINGS = "android.media.extra.ENCODINGS";
    public static final String EXTRA_MAX_CHANNEL_COUNT = "android.media.extra.MAX_CHANNEL_COUNT";
    public static final String EXTRA_RINGER_MODE = "android.media.EXTRA_RINGER_MODE";
    public static final String EXTRA_SCO_AUDIO_PREVIOUS_STATE = "android.media.extra.SCO_AUDIO_PREVIOUS_STATE";
    public static final String EXTRA_SCO_AUDIO_STATE = "android.media.extra.SCO_AUDIO_STATE";
    public static final String EXTRA_VIBRATE_SETTING = "android.media.EXTRA_VIBRATE_SETTING";
    public static final String EXTRA_VIBRATE_TYPE = "android.media.EXTRA_VIBRATE_TYPE";
    public static final int FLAG_ALLOW_RINGER_MODES = 2;
    public static final int FLAG_PLAY_SOUND = 4;
    public static final int FLAG_REMOVE_SOUND_AND_VIBRATE = 8;
    public static final int FLAG_SHOW_UI = 1;
    public static final int FLAG_VIBRATE = 16;
    public static final int FX_BACK = 10;
    public static final int FX_FOCUS_NAVIGATION_DOWN = 2;
    public static final int FX_FOCUS_NAVIGATION_LEFT = 3;
    public static final int FX_FOCUS_NAVIGATION_RIGHT = 4;
    public static final int FX_FOCUS_NAVIGATION_UP = 1;
    public static final int FX_KEYPRESS_DELETE = 7;
    public static final int FX_KEYPRESS_INVALID = 9;
    public static final int FX_KEYPRESS_RETURN = 8;
    public static final int FX_KEYPRESS_SPACEBAR = 6;
    public static final int FX_KEYPRESS_STANDARD = 5;
    public static final int FX_KEY_CLICK = 0;
    public static final int GET_DEVICES_ALL = 3;
    public static final int GET_DEVICES_INPUTS = 1;
    public static final int GET_DEVICES_OUTPUTS = 2;
    public static final int MODE_CALL_REDIRECT = 5;
    public static final int MODE_CALL_SCREENING = 4;
    public static final int MODE_COMMUNICATION_REDIRECT = 6;
    public static final int MODE_CURRENT = -1;
    public static final int MODE_INVALID = -2;
    public static final int MODE_IN_CALL = 2;
    public static final int MODE_IN_COMMUNICATION = 3;
    public static final int MODE_NORMAL = 0;
    public static final int MODE_RINGTONE = 1;
    public static final int NUM_STREAMS = 5;
    public static final int PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED = 2;
    public static final int PLAYBACK_OFFLOAD_NOT_SUPPORTED = 0;
    public static final int PLAYBACK_OFFLOAD_SUPPORTED = 1;
    public static final String PROPERTY_OUTPUT_FRAMES_PER_BUFFER = "android.media.property.OUTPUT_FRAMES_PER_BUFFER";
    public static final String PROPERTY_OUTPUT_SAMPLE_RATE = "android.media.property.OUTPUT_SAMPLE_RATE";
    public static final String PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED = "android.media.property.SUPPORT_AUDIO_SOURCE_UNPROCESSED";
    public static final String PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND = "android.media.property.SUPPORT_MIC_NEAR_ULTRASOUND";
    public static final String PROPERTY_SUPPORT_SPEAKER_NEAR_ULTRASOUND = "android.media.property.SUPPORT_SPEAKER_NEAR_ULTRASOUND";
    public static final String RINGER_MODE_CHANGED_ACTION = "android.media.RINGER_MODE_CHANGED";
    public static final int RINGER_MODE_NORMAL = 2;
    public static final int RINGER_MODE_SILENT = 0;
    public static final int RINGER_MODE_VIBRATE = 1;
    public static final int ROUTE_ALL = -1;
    public static final int ROUTE_BLUETOOTH = 4;
    public static final int ROUTE_BLUETOOTH_A2DP = 16;
    public static final int ROUTE_BLUETOOTH_SCO = 4;
    public static final int ROUTE_EARPIECE = 1;
    public static final int ROUTE_HEADSET = 8;
    public static final int ROUTE_SPEAKER = 2;
    public static final int SCO_AUDIO_STATE_CONNECTED = 1;
    public static final int SCO_AUDIO_STATE_CONNECTING = 2;
    public static final int SCO_AUDIO_STATE_DISCONNECTED = 0;
    public static final int SCO_AUDIO_STATE_ERROR = -1;
    public static final int USE_DEFAULT_STREAM_TYPE = -2147483648;
    public static final String VIBRATE_SETTING_CHANGED_ACTION = "android.media.VIBRATE_SETTING_CHANGED";
    public static final int VIBRATE_SETTING_OFF = 0;
    public static final int VIBRATE_SETTING_ON = 1;
    public static final int VIBRATE_SETTING_ONLY_SILENT = 2;
    public static final int VIBRATE_TYPE_NOTIFICATION = 1;
    public static final int VIBRATE_TYPE_RINGER = 0;

    private static final int STREAM_SLOTS = 11;
    private static final int STREAM_MAX = 15;

    public interface OnAudioFocusChangeListener {
        void onAudioFocusChange(int focusChange);
    }

    public interface OnModeChangedListener {
        void onModeChanged(int mode);
    }

    public interface OnCommunicationDeviceChangedListener {
        void onCommunicationDeviceChanged(AudioDeviceInfo device);
    }

    public abstract static class AudioPlaybackCallback {
        public AudioPlaybackCallback() {}
        public void onPlaybackConfigChanged(java.util.List<?> configs) {}
    }

    public abstract static class AudioRecordingCallback {
        public AudioRecordingCallback() {}
        public void onRecordingConfigChanged(java.util.List<?> configs) {}
    }

    private static AudioManager sInstance;
    private final int[] mIndex = new int[STREAM_SLOTS];
    private final boolean[] mMute = new boolean[STREAM_SLOTS];

    private AudioManager() {
        for (int i = 0; i < STREAM_SLOTS; i++) mIndex[i] = STREAM_MAX;
    }

    /** framework-internal. One manager for the process. */
    public static AudioManager getInstance() {
        if (sInstance == null) sInstance = new AudioManager();
        return sInstance;
    }

    public int requestAudioFocus(OnAudioFocusChangeListener l, int streamType, int durationHint) {
        if (!isGain(durationHint)) return AUDIOFOCUS_REQUEST_FAILED;
        return AUDIOFOCUS_REQUEST_GRANTED;
    }

    /**
     * framework-internal (hidden in AOSP). VideoView requests focus with the
     * playback {@link AudioAttributes}. The request is granted immediately.
     */
    public int requestAudioFocus(OnAudioFocusChangeListener l, AudioAttributes attributes, int durationHint,
            int flags) {
        if (attributes == null) throw new IllegalArgumentException("AudioAttributes must not be null");
        if (!isGain(durationHint)) return AUDIOFOCUS_REQUEST_FAILED;
        return AUDIOFOCUS_REQUEST_GRANTED;
    }

    public int abandonAudioFocus(OnAudioFocusChangeListener l) {
        return AUDIOFOCUS_REQUEST_GRANTED;
    }

    public int getStreamMaxVolume(int streamType) { return STREAM_MAX; }

    public int getStreamMinVolume(int streamType) { return 0; }

    public int getStreamVolume(int streamType) {
        int slot = slot(streamType);
        return slot < 0 ? 0 : mIndex[slot];
    }

    public void setStreamVolume(int streamType, int index, int flags) {
        int slot = slot(streamType);
        if (slot < 0) return;
        if (index < 0) index = 0;
        if (index > STREAM_MAX) index = STREAM_MAX;
        mIndex[slot] = index;
        nativeVolume(streamType, index, STREAM_MAX, mMute[slot]);
    }

    public void setStreamMute(int streamType, boolean state) {
        int slot = slot(streamType);
        if (slot < 0) return;
        mMute[slot] = state;
        nativeVolume(streamType, mIndex[slot], STREAM_MAX, state);
    }

    public boolean isStreamMute(int streamType) {
        int slot = slot(streamType);
        return slot >= 0 && mMute[slot];
    }

    public void adjustStreamVolume(int streamType, int direction, int flags) {
        if (direction == ADJUST_RAISE) setStreamVolume(streamType, getStreamVolume(streamType) + 1, flags);
        else if (direction == ADJUST_LOWER) setStreamVolume(streamType, getStreamVolume(streamType) - 1, flags);
        else if (direction == ADJUST_MUTE) setStreamMute(streamType, true);
        else if (direction == ADJUST_UNMUTE) setStreamMute(streamType, false);
        else if (direction == ADJUST_TOGGLE_MUTE) setStreamMute(streamType, !isStreamMute(streamType));
    }

    // ---- focus requests (granted at once, never taken back)

    public int requestAudioFocus(AudioFocusRequest request) {
        if (request == null) throw new NullPointerException("Illegal null AudioFocusRequest");
        return AUDIOFOCUS_REQUEST_GRANTED;
    }

    public int abandonAudioFocusRequest(AudioFocusRequest request) {
        if (request == null) throw new IllegalArgumentException("Illegal null AudioFocusRequest");
        return AUDIOFOCUS_REQUEST_GRANTED;
    }

    // ---- ringer, mode and routing: a console with one speaker output, no phone and no microphone

    private int mRingerMode = RINGER_MODE_NORMAL;
    private int mMode = MODE_NORMAL;
    private int mAllowedCapturePolicy = AudioAttributes.ALLOW_CAPTURE_BY_ALL;
    private int mNextSession = 1;
    private final java.util.ArrayList<OnModeChangedListener> mModeListeners = new java.util.ArrayList<OnModeChangedListener>();

    public int getRingerMode() { return mRingerMode; }

    public void setRingerMode(int ringerMode) {
        if (ringerMode == RINGER_MODE_NORMAL || ringerMode == RINGER_MODE_SILENT || ringerMode == RINGER_MODE_VIBRATE) {
            mRingerMode = ringerMode;
        }
    }

    public int getMode() { return mMode; }

    public void setMode(int mode) {
        if (mode < MODE_NORMAL || mode == mMode) return;
        mMode = mode;
        java.util.ArrayList<OnModeChangedListener> ls = new java.util.ArrayList<OnModeChangedListener>(mModeListeners);
        for (int i = 0; i < ls.size(); i++) ls.get(i).onModeChanged(mode);
    }

    public void addOnModeChangedListener(java.util.concurrent.Executor executor, OnModeChangedListener listener) {
        if (executor == null || listener == null) throw new NullPointerException();
        mModeListeners.add(listener);
    }

    public void removeOnModeChangedListener(OnModeChangedListener listener) { mModeListeners.remove(listener); }

    public void addOnCommunicationDeviceChangedListener(java.util.concurrent.Executor executor,
            OnCommunicationDeviceChangedListener listener) {}
    public void removeOnCommunicationDeviceChangedListener(OnCommunicationDeviceChangedListener listener) {}

    public AudioDeviceInfo[] getDevices(int flags) {
        if ((flags & ~GET_DEVICES_ALL) != 0) throw new IllegalArgumentException("Bad flags " + flags);
        if ((flags & GET_DEVICES_OUTPUTS) == 0) return new AudioDeviceInfo[0];
        return new AudioDeviceInfo[] {AudioDeviceInfo.SPEAKER};
    }

    public java.util.List<AudioDeviceInfo> getAudioDevicesForAttributes(AudioAttributes attributes) {
        java.util.ArrayList<AudioDeviceInfo> out = new java.util.ArrayList<AudioDeviceInfo>();
        out.add(AudioDeviceInfo.SPEAKER);
        return out;
    }

    public java.util.List<AudioDeviceInfo> getAvailableCommunicationDevices() { return new java.util.ArrayList<AudioDeviceInfo>(); }
    public AudioDeviceInfo getCommunicationDevice() { return null; }
    public boolean setCommunicationDevice(AudioDeviceInfo device) { return false; }
    public void clearCommunicationDevice() {}
    public void registerAudioDeviceCallback(AudioDeviceCallback callback, android.os.Handler handler) {}
    public void unregisterAudioDeviceCallback(AudioDeviceCallback callback) {}
    public void registerAudioPlaybackCallback(AudioPlaybackCallback cb, android.os.Handler handler) {}
    public void unregisterAudioPlaybackCallback(AudioPlaybackCallback cb) {}
    public void registerAudioRecordingCallback(AudioRecordingCallback cb, android.os.Handler handler) {}
    public void unregisterAudioRecordingCallback(AudioRecordingCallback cb) {}
    public java.util.List getActivePlaybackConfigurations() { return new java.util.ArrayList<Object>(); }
    public java.util.List getActiveRecordingConfigurations() { return new java.util.ArrayList<Object>(); }
    public java.util.List getMicrophones() { return new java.util.ArrayList<Object>(); }

    public java.util.Set<Integer> getSupportedDeviceTypes(int direction) {
        java.util.HashSet<Integer> out = new java.util.HashSet<Integer>();
        if (direction == GET_DEVICES_OUTPUTS) out.add(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        return out;
    }

    /** Other apps never play here, so music is never active behind the app. */
    public boolean isMusicActive() { return false; }
    public boolean isSpeakerphoneOn() { return false; }
    public void setSpeakerphoneOn(boolean on) {}
    public boolean isMicrophoneMute() { return true; }
    public void setMicrophoneMute(boolean on) {}
    public boolean isWiredHeadsetOn() { return false; }
    public void setWiredHeadsetOn(boolean on) {}
    public boolean isBluetoothA2dpOn() { return false; }
    public void setBluetoothA2dpOn(boolean on) {}
    public boolean isBluetoothScoOn() { return false; }
    public void setBluetoothScoOn(boolean on) {}
    public boolean isBluetoothScoAvailableOffCall() { return false; }
    public void startBluetoothSco() {}
    public void stopBluetoothSco() {}
    public boolean isVolumeFixed() { return false; }
    public boolean isCallScreeningModeSupported() { return false; }
    public boolean isRampingRingerEnabled() { return false; }
    public boolean shouldVibrate(int vibrateType) { return false; }
    public int getVibrateSetting(int vibrateType) { return VIBRATE_SETTING_OFF; }
    public void setVibrateSetting(int vibrateType, int vibrateSetting) {}
    public void setRouting(int mode, int routes, int mask) {}
    public int getRouting(int mode) { return ROUTE_SPEAKER; }
    public void setStreamSolo(int streamType, boolean state) {}
    public void setParameters(String keyValuePairs) {}
    public String getParameters(String keys) { return ""; }

    public String getProperty(String key) {
        if (PROPERTY_OUTPUT_SAMPLE_RATE.equals(key)) return "48000";
        if (PROPERTY_OUTPUT_FRAMES_PER_BUFFER.equals(key)) return "256";
        if (PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND.equals(key) || PROPERTY_SUPPORT_SPEAKER_NEAR_ULTRASOUND.equals(key)
                || PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED.equals(key)) {
            return "false";
        }
        return null;
    }

    public int generateAudioSessionId() { return ++mNextSession; }
    public int getAudioHwSyncForSession(int sessionId) { return ERROR; }
    public int getAllowedCapturePolicy() { return mAllowedCapturePolicy; }
    public void setAllowedCapturePolicy(int capturePolicy) { mAllowedCapturePolicy = capturePolicy; }
    public float getStreamVolumeDb(int streamType, int index, int deviceType) {
        if (index <= 0) return Float.NEGATIVE_INFINITY;
        return (float) (20.0 * Math.log10((double) index / STREAM_MAX));
    }

    /** Key click and navigation sounds are off, as with "Touch sounds" disabled. */
    public void playSoundEffect(int effectType) {}
    public void playSoundEffect(int effectType, float volume) {}
    public void loadSoundEffects() {}
    public void unloadSoundEffects() {}

    public void adjustVolume(int direction, int flags) { adjustStreamVolume(STREAM_MUSIC, direction, flags); }
    public void adjustSuggestedStreamVolume(int direction, int suggestedStreamType, int flags) {
        adjustStreamVolume(suggestedStreamType == USE_DEFAULT_STREAM_TYPE ? STREAM_MUSIC : suggestedStreamType,
                direction, flags);
    }

    public void dispatchMediaKeyEvent(android.view.KeyEvent keyEvent) {
        if (keyEvent == null) throw new NullPointerException("keyEvent");
    }

    public void registerMediaButtonEventReceiver(android.content.ComponentName eventReceiver) {}
    public void unregisterMediaButtonEventReceiver(android.content.ComponentName eventReceiver) {}
    public void registerMediaButtonEventReceiver(android.app.PendingIntent eventReceiver) {}
    public void unregisterMediaButtonEventReceiver(android.app.PendingIntent eventReceiver) {}

    public static boolean isHapticPlaybackSupported() { return false; }
    public static boolean isOffloadedPlaybackSupported(AudioFormat format, AudioAttributes attributes) { return false; }
    public static int getPlaybackOffloadSupport(AudioFormat format, AudioAttributes attributes) {
        return PLAYBACK_OFFLOAD_NOT_SUPPORTED;
    }
    public static int getDirectPlaybackSupport(AudioFormat format, AudioAttributes attributes) {
        return DIRECT_PLAYBACK_NOT_SUPPORTED;
    }

    private static int slot(int streamType) {
        if (streamType < 0 || streamType >= STREAM_SLOTS) return -1;
        return streamType;
    }

    private static native void nativeVolume(int stream, int index, int max, boolean mute);

    private static boolean isGain(int durationHint) {
        return durationHint == AUDIOFOCUS_GAIN || durationHint == AUDIOFOCUS_GAIN_TRANSIENT
                || durationHint == AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                || durationHint == AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
    }
}
