package android.media;

import android.os.Handler;

/** Focus request parameters (API 26). AudioManager grants every request at once. */
public final class AudioFocusRequest {
    private final int mFocusGain;
    private final AudioManager.OnAudioFocusChangeListener mListener;
    private final Handler mHandler;
    private final AudioAttributes mAttributes;
    private final boolean mPauseWhenDucked;
    private final boolean mDelayedFocus;
    private final boolean mForceDucking;

    private AudioFocusRequest(Builder b) {
        mFocusGain = b.mFocusGain;
        mListener = b.mListener;
        mHandler = b.mHandler;
        mAttributes = b.mAttributes;
        mPauseWhenDucked = b.mPauseWhenDucked;
        mDelayedFocus = b.mDelayedFocus;
        mForceDucking = b.mForceDucking;
    }

    public AudioAttributes getAudioAttributes() { return mAttributes; }
    public int getFocusGain() { return mFocusGain; }
    public boolean willPauseWhenDucked() { return mPauseWhenDucked; }
    public boolean acceptsDelayedFocusGain() { return mDelayedFocus; }

    public static final class Builder {
        private int mFocusGain;
        private AudioManager.OnAudioFocusChangeListener mListener;
        private Handler mHandler;
        private AudioAttributes mAttributes = new AudioAttributes.Builder().build();
        private boolean mPauseWhenDucked;
        private boolean mDelayedFocus;
        private boolean mForceDucking;

        public Builder(int focusGain) { setFocusGain(focusGain); }

        public Builder(AudioFocusRequest requestToCopy) {
            if (requestToCopy == null) throw new IllegalArgumentException("Illegal null AudioFocusRequest");
            mFocusGain = requestToCopy.mFocusGain;
            mListener = requestToCopy.mListener;
            mHandler = requestToCopy.mHandler;
            mAttributes = requestToCopy.mAttributes;
            mPauseWhenDucked = requestToCopy.mPauseWhenDucked;
            mDelayedFocus = requestToCopy.mDelayedFocus;
            mForceDucking = requestToCopy.mForceDucking;
        }

        public Builder setFocusGain(int focusGain) {
            if (focusGain != AudioManager.AUDIOFOCUS_GAIN && focusGain != AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                    && focusGain != AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    && focusGain != AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE) {
                throw new IllegalArgumentException("Illegal audio focus gain type " + focusGain);
            }
            mFocusGain = focusGain;
            return this;
        }

        public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener listener) {
            if (listener == null) throw new NullPointerException("Illegal null focus listener");
            mListener = listener;
            mHandler = null;
            return this;
        }

        public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener listener, Handler handler) {
            if (listener == null || handler == null) throw new NullPointerException("Illegal null focus listener or handler");
            mListener = listener;
            mHandler = handler;
            return this;
        }

        public Builder setAudioAttributes(AudioAttributes attributes) {
            if (attributes == null) throw new NullPointerException("Illegal null AudioAttributes");
            mAttributes = attributes;
            return this;
        }

        public Builder setWillPauseWhenDucked(boolean pauseOnDuck) {
            mPauseWhenDucked = pauseOnDuck;
            return this;
        }

        public Builder setAcceptsDelayedFocusGain(boolean acceptsDelayedFocusGain) {
            mDelayedFocus = acceptsDelayedFocusGain;
            return this;
        }

        public Builder setForceDucking(boolean forceDucking) {
            mForceDucking = forceDucking;
            return this;
        }

        public AudioFocusRequest build() {
            if ((mDelayedFocus || mPauseWhenDucked) && mListener == null) {
                throw new IllegalStateException("Can't use delayed focus or pause on duck without a listener");
            }
            return new AudioFocusRequest(this);
        }
    }
}
