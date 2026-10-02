package android.media;

/**
 * PCM playback. Static mode plays a clip built at {@link #play}. Stream mode
 * writes float frames into a ring. Channel masks other than mono and stereo
 * are rejected.
 */
public class AudioTrack {
    public static final int ERROR = -1;
    public static final int ERROR_BAD_VALUE = -2;
    public static final int ERROR_INVALID_OPERATION = -3;
    public static final int ERROR_DEAD_OBJECT = -6;
    public static final int MODE_STATIC = 0;
    public static final int MODE_STREAM = 1;
    public static final int PLAYSTATE_STOPPED = 1;
    public static final int PLAYSTATE_PAUSED = 2;
    public static final int PLAYSTATE_PLAYING = 3;
    public static final int STATE_UNINITIALIZED = 0;
    public static final int STATE_INITIALIZED = 1;
    public static final int STATE_NO_STATIC_DATA = 2;
    public static final int SUCCESS = 0;
    public static final int WRITE_BLOCKING = 0;
    public static final int WRITE_NON_BLOCKING = 1;

    private final int mStream;
    private final int mRate;
    private final int mChannels;
    private final int mChannelConfig;
    private final int mEncoding;
    private final int mMode;
    private final int mSession;
    private int mState;
    private int mPlayState = PLAYSTATE_STOPPED;
    private int mVoice;
    private byte[] mStatic;
    private int mStaticLen;
    private boolean mStaticDirty = true;

    public AudioTrack(int streamType, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes,
            int mode) throws IllegalArgumentException {
        this(streamType, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, mode, 0);
    }

    public AudioTrack(int streamType, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes,
            int mode, int sessionId) throws IllegalArgumentException {
        int channels = channelCount(channelConfig);
        int bps = bytesPerSample(audioFormat);
        if (sampleRateInHz <= 0 || channels <= 0 || channels > 2 || bps <= 0 || bufferSizeInBytes <= 0
                || (mode != MODE_STATIC && mode != MODE_STREAM)) {
            throw new IllegalArgumentException("bad AudioTrack params");
        }
        mStream = streamType;
        mRate = sampleRateInHz;
        mChannels = channels;
        mChannelConfig = channelConfig;
        mEncoding = audioFormat == AudioFormat.ENCODING_DEFAULT ? AudioFormat.ENCODING_PCM_16BIT : audioFormat;
        mMode = mode;
        mSession = sessionId > 0 ? sessionId : 1;
        if (mode == MODE_STREAM) {
            mVoice = nativeStream(mRate, mChannels, mStream);
            if (mVoice == 0) throw new IllegalArgumentException("stream open failed");
            mState = STATE_INITIALIZED;
        } else {
            mStatic = new byte[bufferSizeInBytes];
            mState = STATE_NO_STATIC_DATA;
        }
    }

    public AudioTrack(AudioAttributes attributes, AudioFormat format, int bufferSizeInBytes, int mode, int sessionId)
            throws IllegalArgumentException {
        this(attributes == null ? AudioManager.STREAM_MUSIC : attributes.getVolumeControlStream(),
                format == null ? 0 : format.getSampleRate(),
                format == null ? 0 : (format.getChannelMask() != 0 ? format.getChannelMask() : format.getChannelIndexMask()),
                format == null ? 0 : format.getEncoding(), bufferSizeInBytes, mode, sessionId);
    }

    public static int getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat) {
        int channels = channelCount(channelConfig);
        int bps = bytesPerSample(audioFormat);
        if (sampleRateInHz <= 0 || channels <= 0 || bps <= 0) return ERROR_BAD_VALUE;
        return bps * channels * 1024;
    }

    public static int getNativeOutputSampleRate(int streamType) { return 48000; }

    public static float getMinVolume() { return 0f; }

    public static float getMaxVolume() { return 1f; }

    public int getSampleRate() { return mRate; }

    public int getAudioFormat() { return mEncoding; }

    public int getChannelCount() { return mChannels; }

    public int getChannelConfiguration() { return mChannelConfig; }

    public int getStreamType() { return mStream; }

    public int getState() { return mState; }

    public int getPlayState() { return mPlayState; }

    public int getAudioSessionId() { return mSession; }

    public int getPlaybackHeadPosition() {
        if (mState == STATE_UNINITIALIZED || mVoice == 0) return 0;
        return nativeHead(mVoice);
    }

    public void play() throws IllegalStateException {
        if (mState != STATE_INITIALIZED) throw new IllegalStateException();
        if (mMode == MODE_STATIC && (mStaticDirty || mVoice == 0)) {
            if (mVoice != 0) nativeRelease(mVoice);
            mVoice = nativeClip(mStatic, mStaticLen, mRate, mChannels, mEncoding, mStream);
            mStaticDirty = false;
            if (mVoice == 0) throw new IllegalStateException();
        }
        nativePlay(mVoice);
        mPlayState = PLAYSTATE_PLAYING;
    }

    public void stop() throws IllegalStateException {
        if (mState != STATE_INITIALIZED) throw new IllegalStateException();
        if (mVoice != 0) nativeStop(mVoice);
        mPlayState = PLAYSTATE_STOPPED;
    }

    public void pause() throws IllegalStateException {
        if (mState != STATE_INITIALIZED) throw new IllegalStateException();
        if (mVoice != 0) nativePause(mVoice);
        mPlayState = PLAYSTATE_PAUSED;
    }

    public void flush() {
        if (mMode == MODE_STREAM && mVoice != 0) nativeFlush(mVoice);
    }

    public void release() {
        if (mVoice != 0) nativeRelease(mVoice);
        mVoice = 0;
        mState = STATE_UNINITIALIZED;
        mPlayState = PLAYSTATE_STOPPED;
    }

    public int setVolume(float gain) { return setStereoVolume(gain, gain); }

    public int setStereoVolume(float left, float right) {
        if (mState == STATE_UNINITIALIZED) return ERROR_INVALID_OPERATION;
        if (mVoice != 0) nativeGain(mVoice, left, right);
        return SUCCESS;
    }

    public int write(byte[] audioData, int offsetInBytes, int sizeInBytes) {
        return write(audioData, offsetInBytes, sizeInBytes, WRITE_BLOCKING);
    }

    public int write(byte[] audioData, int offsetInBytes, int sizeInBytes, int writeMode) {
        if (audioData == null || offsetInBytes < 0 || sizeInBytes < 0 || offsetInBytes + sizeInBytes > audioData.length) {
            return ERROR_BAD_VALUE;
        }
        if (mState == STATE_UNINITIALIZED) return ERROR_INVALID_OPERATION;
        if (mMode == MODE_STATIC) return writeStatic(audioData, offsetInBytes, sizeInBytes);
        int bpf = bytesPerSample(mEncoding) * mChannels;
        if (bpf <= 0) return ERROR_BAD_VALUE;
        int frames = sizeInBytes / bpf;
        if (frames <= 0) return 0;
        float[] pcm = new float[frames * mChannels];
        decodeBytes(audioData, offsetInBytes, pcm, frames);
        int got = nativeWrite(mVoice, pcm, frames, mChannels, writeMode == WRITE_BLOCKING);
        return got * bpf;
    }

    public int write(short[] audioData, int offsetInShorts, int sizeInShorts) {
        return write(audioData, offsetInShorts, sizeInShorts, WRITE_BLOCKING);
    }

    public int write(short[] audioData, int offsetInShorts, int sizeInShorts, int writeMode) {
        if (audioData == null || offsetInShorts < 0 || sizeInShorts < 0
                || offsetInShorts + sizeInShorts > audioData.length) {
            return ERROR_BAD_VALUE;
        }
        if (mEncoding != AudioFormat.ENCODING_PCM_16BIT) return ERROR_BAD_VALUE;
        if (mState == STATE_UNINITIALIZED) return ERROR_INVALID_OPERATION;
        if (mMode == MODE_STATIC) {
            byte[] raw = new byte[sizeInShorts * 2];
            for (int i = 0; i < sizeInShorts; i++) {
                int v = audioData[offsetInShorts + i];
                raw[i * 2] = (byte) v;
                raw[i * 2 + 1] = (byte) (v >> 8);
            }
            return writeStatic(raw, 0, raw.length) / 2;
        }
        int frames = sizeInShorts / mChannels;
        if (frames <= 0) return 0;
        float[] pcm = new float[frames * mChannels];
        for (int i = 0; i < frames * mChannels; i++) pcm[i] = audioData[offsetInShorts + i] / 32768f;
        int got = nativeWrite(mVoice, pcm, frames, mChannels, writeMode == WRITE_BLOCKING);
        return got * mChannels;
    }

    public int write(float[] audioData, int offsetInFloats, int sizeInFloats, int writeMode) {
        if (audioData == null || offsetInFloats < 0 || sizeInFloats < 0
                || offsetInFloats + sizeInFloats > audioData.length) {
            return ERROR_BAD_VALUE;
        }
        if (mEncoding != AudioFormat.ENCODING_PCM_FLOAT) return ERROR_BAD_VALUE;
        if (mState == STATE_UNINITIALIZED) return ERROR_INVALID_OPERATION;
        if (mMode == MODE_STATIC) {
            byte[] raw = new byte[sizeInFloats * 4];
            for (int i = 0; i < sizeInFloats; i++) {
                int bits = Float.floatToIntBits(audioData[offsetInFloats + i]);
                raw[i * 4] = (byte) bits;
                raw[i * 4 + 1] = (byte) (bits >> 8);
                raw[i * 4 + 2] = (byte) (bits >> 16);
                raw[i * 4 + 3] = (byte) (bits >> 24);
            }
            return writeStatic(raw, 0, raw.length) / 4;
        }
        int frames = sizeInFloats / mChannels;
        if (frames <= 0) return 0;
        float[] pcm = new float[frames * mChannels];
        System.arraycopy(audioData, offsetInFloats, pcm, 0, frames * mChannels);
        int got = nativeWrite(mVoice, pcm, frames, mChannels, writeMode == WRITE_BLOCKING);
        return got * mChannels;
    }

    private int writeStatic(byte[] raw, int off, int len) {
        if (mPlayState == PLAYSTATE_PLAYING) return ERROR_INVALID_OPERATION;
        int room = mStatic.length - mStaticLen;
        if (room <= 0) return 0;
        if (len > room) len = room;
        System.arraycopy(raw, off, mStatic, mStaticLen, len);
        mStaticLen += len;
        mStaticDirty = true;
        if (mStaticLen > 0) mState = STATE_INITIALIZED;
        return len;
    }

    private void decodeBytes(byte[] src, int off, float[] dst, int frames) {
        int bps = bytesPerSample(mEncoding);
        int n = frames * mChannels;
        if (bps == 1) {
            for (int i = 0; i < n; i++) dst[i] = ((src[off + i] & 0xff) - 128) / 128f;
        } else if (bps == 2) {
            for (int i = 0; i < n; i++) {
                int lo = src[off + i * 2] & 0xff;
                int hi = src[off + i * 2 + 1];
                dst[i] = (short) (lo | (hi << 8)) / 32768f;
            }
        } else {
            for (int i = 0; i < n; i++) {
                int b0 = src[off + i * 4] & 0xff;
                int b1 = src[off + i * 4 + 1] & 0xff;
                int b2 = src[off + i * 4 + 2] & 0xff;
                int b3 = src[off + i * 4 + 3] & 0xff;
                dst[i] = Float.intBitsToFloat(b0 | (b1 << 8) | (b2 << 16) | (b3 << 24));
            }
        }
    }

    static int channelCount(int config) {
        if (config == AudioFormat.CHANNEL_OUT_DEFAULT) return 2;
        if (config == 0) return 0;
        return Integer.bitCount(config);
    }

    static int bytesPerSample(int encoding) {
        if (encoding == AudioFormat.ENCODING_PCM_8BIT) return 1;
        if (encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_DEFAULT) return 2;
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT || encoding == AudioFormat.ENCODING_PCM_32BIT) return 4;
        return 0;
    }

    private static native int nativeStream(int rate, int channels, int stream);
    private static native int nativeClip(byte[] pcm, int bytes, int rate, int channels, int encoding, int stream);
    private static native int nativeWrite(int id, float[] interleaved, int frames, int channels, boolean block);
    private static native void nativePlay(int id);
    private static native void nativePause(int id);
    private static native void nativeStop(int id);
    private static native void nativeFlush(int id);
    private static native void nativeRelease(int id);
    private static native void nativeGain(int id, float left, float right);
    private static native int nativeHead(int id);
}
