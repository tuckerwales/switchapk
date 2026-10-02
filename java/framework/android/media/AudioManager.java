package android.media;

/**
 * Audio routing and focus (placeholder until WS7). Focus requests are granted
 * and never taken back, because nothing else is playing. There is no mixer.
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

    public interface OnAudioFocusChangeListener {
        void onAudioFocusChange(int focusChange);
    }

    private static AudioManager sInstance;

    private AudioManager() {}

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

    private static boolean isGain(int durationHint) {
        return durationHint == AUDIOFOCUS_GAIN || durationHint == AUDIOFOCUS_GAIN_TRANSIENT
                || durationHint == AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                || durationHint == AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
    }
}
