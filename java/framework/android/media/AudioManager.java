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

    private static final int STREAM_SLOTS = 11;
    private static final int STREAM_MAX = 15;

    public interface OnAudioFocusChangeListener {
        void onAudioFocusChange(int focusChange);
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
