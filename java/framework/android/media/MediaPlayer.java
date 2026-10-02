package android.media;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Audio playback. Video is not decoded: a source that is not WAV, Ogg Vorbis
 * or MP3 fails prepare with {@link #MEDIA_ERROR_UNKNOWN} /
 * {@link #MEDIA_ERROR_UNSUPPORTED}. {@code setDataSource} only remembers the
 * source. Listeners run on the main looper.
 */
public class MediaPlayer {
    public static final int MEDIA_ERROR_UNKNOWN = 1;
    public static final int MEDIA_ERROR_SERVER_DIED = 100;
    public static final int MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = 200;
    public static final int MEDIA_ERROR_IO = -1004;
    public static final int MEDIA_ERROR_MALFORMED = -1007;
    public static final int MEDIA_ERROR_UNSUPPORTED = -1010;
    public static final int MEDIA_ERROR_TIMED_OUT = -110;

    public static final int MEDIA_INFO_UNKNOWN = 1;
    public static final int MEDIA_INFO_STARTED_AS_NEXT = 2;
    public static final int MEDIA_INFO_VIDEO_RENDERING_START = 3;
    public static final int MEDIA_INFO_VIDEO_TRACK_LAGGING = 700;
    public static final int MEDIA_INFO_BUFFERING_START = 701;
    public static final int MEDIA_INFO_BUFFERING_END = 702;
    public static final int MEDIA_INFO_BAD_INTERLEAVING = 800;
    public static final int MEDIA_INFO_NOT_SEEKABLE = 801;
    public static final int MEDIA_INFO_METADATA_UPDATE = 802;
    public static final int MEDIA_INFO_AUDIO_NOT_PLAYING = 804;
    public static final int MEDIA_INFO_VIDEO_NOT_PLAYING = 805;
    public static final int MEDIA_INFO_UNSUPPORTED_SUBTITLE = 901;
    public static final int MEDIA_INFO_SUBTITLE_TIMED_OUT = 902;

    private static final int IDLE = 0;
    private static final int INITIALIZED = 1;
    private static final int PREPARING = 2;
    private static final int PREPARED = 3;
    private static final int STARTED = 4;
    private static final int PAUSED = 5;
    private static final int STOPPED = 6;
    private static final int PLAYBACK_COMPLETED = 7;
    private static final int ERROR = 8;
    private static final int END = 9;

    /** Not a real audio session. Stable so VideoView can cache it. */
    private static final int FAKE_SESSION = 1;

    public interface OnPreparedListener {
        void onPrepared(MediaPlayer mp);
    }

    public interface OnCompletionListener {
        void onCompletion(MediaPlayer mp);
    }

    public interface OnErrorListener {
        boolean onError(MediaPlayer mp, int what, int extra);
    }

    public interface OnInfoListener {
        boolean onInfo(MediaPlayer mp, int what, int extra);
    }

    public interface OnBufferingUpdateListener {
        void onBufferingUpdate(MediaPlayer mp, int percent);
    }

    public interface OnVideoSizeChangedListener {
        void onVideoSizeChanged(MediaPlayer mp, int width, int height);
    }

    private int mState = IDLE;
    private int mPrepareGeneration;
    private int mAudioSession = FAKE_SESSION;
    private int mVoice;
    private int mStream = AudioManager.STREAM_MUSIC;
    private boolean mLooping;
    private float mLeft = 1f;
    private float mRight = 1f;
    private String mPath;
    private Uri mUri;
    private byte[] mBytes;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private OnPreparedListener mOnPrepared;
    private OnCompletionListener mOnCompletion;
    private OnErrorListener mOnError;
    private OnInfoListener mOnInfo;
    private OnBufferingUpdateListener mOnBuffering;
    private OnVideoSizeChangedListener mOnVideoSize;
    private SurfaceHolder mHolder;
    private boolean mScreenOn;

    private final Runnable mPoll = new Runnable() {
        public void run() {
            if (mState != STARTED || mVoice == 0) return;
            if (nativeEnded(mVoice)) {
                mState = PLAYBACK_COMPLETED;
                if (mOnCompletion != null) mOnCompletion.onCompletion(MediaPlayer.this);
                return;
            }
            mHandler.postDelayed(this, 40);
        }
    };

    public MediaPlayer() {}

    public MediaPlayer(Context context) {}

    public static MediaPlayer create(Context context, Uri uri) {
        return create(context, uri, null);
    }

    public static MediaPlayer create(Context context, Uri uri, SurfaceHolder holder) {
        return create(context, uri, holder, null, 0);
    }

    public static MediaPlayer create(Context context, Uri uri, SurfaceHolder holder, AudioAttributes attributes,
            int audioSessionId) {
        try {
            MediaPlayer mp = new MediaPlayer();
            if (attributes != null) mp.setAudioAttributes(attributes);
            if (audioSessionId > 0) mp.setAudioSessionId(audioSessionId);
            mp.setDataSource(context, uri);
            if (holder != null) mp.setDisplay(holder);
            mp.prepare();
            return mp;
        } catch (Exception e) {
            return null;
        }
    }

    public static MediaPlayer create(Context context, int resid) {
        return create(context, resid, null, 0);
    }

    public static MediaPlayer create(Context context, int resid, AudioAttributes attributes, int audioSessionId) {
        AssetFileDescriptor afd = null;
        try {
            afd = context.getResources().openRawResourceFd(resid);
            MediaPlayer mp = new MediaPlayer();
            if (attributes != null) mp.setAudioAttributes(attributes);
            if (audioSessionId > 0) mp.setAudioSessionId(audioSessionId);
            mp.setDataSource(afd);
            afd.close();
            afd = null;
            mp.prepare();
            return mp;
        } catch (Exception e) {
            if (afd != null) try { afd.close(); } catch (IOException ignored) {}
            return null;
        }
    }

    public void setOnPreparedListener(OnPreparedListener listener) { mOnPrepared = listener; }

    public void setOnCompletionListener(OnCompletionListener listener) { mOnCompletion = listener; }

    public void setOnErrorListener(OnErrorListener listener) { mOnError = listener; }

    public void setOnInfoListener(OnInfoListener listener) { mOnInfo = listener; }

    public void setOnBufferingUpdateListener(OnBufferingUpdateListener listener) { mOnBuffering = listener; }

    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener listener) { mOnVideoSize = listener; }

    public void setDataSource(String path) throws IOException, IllegalArgumentException, IllegalStateException,
            SecurityException {
        if (path == null) throw new IllegalArgumentException("path is null");
        checkIdle();
        mPath = path;
        mUri = null;
        mBytes = null;
        mState = INITIALIZED;
    }

    public void setDataSource(Context context, Uri uri) throws IOException, IllegalArgumentException,
            IllegalStateException, SecurityException {
        setDataSource(context, uri, null);
    }

    public void setDataSource(Context context, Uri uri, Map<String, String> headers) throws IOException,
            IllegalArgumentException, IllegalStateException, SecurityException {
        if (uri == null) throw new IllegalArgumentException("uri is null");
        checkIdle();
        mUri = uri;
        mPath = null;
        mBytes = null;
        mState = INITIALIZED;
    }

    public void setDataSource(AssetFileDescriptor afd) throws IOException, IllegalArgumentException,
            IllegalStateException {
        if (afd == null) throw new IllegalArgumentException("afd is null");
        checkIdle();
        InputStream in = afd.createInputStream();
        try {
            mBytes = readAll(in, afd.getLength());
        } finally {
            in.close();
        }
        if (mBytes.length == 0) throw new IOException("empty data source");
        mPath = null;
        mUri = null;
        mState = INITIALIZED;
    }

    public void setDataSource(FileDescriptor fd) throws IOException, IllegalArgumentException, IllegalStateException {
        setDataSource(fd, 0, 0x7fffffffffffffffL);
    }

    public void setDataSource(FileDescriptor fd, long offset, long length) throws IOException,
            IllegalArgumentException, IllegalStateException {
        if (fd == null || !fd.valid()) throw new IOException("invalid fd");
        checkIdle();
        FileInputStream in = new FileInputStream(fd);
        try {
            if (offset > 0) in.skip(offset);
            mBytes = readAll(in, length);
        } finally {
            in.close();
        }
        if (mBytes.length == 0) throw new IOException("empty data source");
        mPath = null;
        mUri = null;
        mState = INITIALIZED;
    }

    public void setDisplay(SurfaceHolder sh) {
        mHolder = sh;
        applyScreenOn();
    }

    public void setAudioAttributes(AudioAttributes attributes) throws IllegalArgumentException {
        if (attributes == null) throw new IllegalArgumentException("Illegal null AudioAttributes");
        mStream = attributes.getVolumeControlStream();
    }

    public void setAudioStreamType(int streamtype) { mStream = streamtype; }

    public void setScreenOnWhilePlaying(boolean screenOn) {
        mScreenOn = screenOn;
        applyScreenOn();
    }

    public void setAudioSessionId(int sessionId) throws IllegalArgumentException, IllegalStateException {
        if (sessionId <= 0) throw new IllegalArgumentException("session id " + sessionId);
        if (mState != IDLE) throw new IllegalStateException();
        mAudioSession = sessionId;
    }

    public int getAudioSessionId() { return mAudioSession; }

    public int getVideoWidth() { return 0; }

    public int getVideoHeight() { return 0; }

    public void setLooping(boolean looping) {
        mLooping = looping;
        if (mVoice != 0) nativeLoop(mVoice, looping);
    }

    public boolean isLooping() { return mLooping; }

    public void setVolume(float leftVolume, float rightVolume) {
        mLeft = leftVolume;
        mRight = rightVolume;
        if (mVoice != 0) nativeGain(mVoice, leftVolume, rightVolume);
    }

    /** framework-internal (hidden in AOSP). Subtitles are not decoded. */
    public void addSubtitleSource(InputStream is, MediaFormat format) throws IllegalStateException {
        if (mState == END || mState == ERROR) throw new IllegalStateException();
        if (mOnInfo != null) mOnInfo.onInfo(this, MEDIA_INFO_UNSUPPORTED_SUBTITLE, 0);
    }

    public void prepare() throws IOException, IllegalStateException {
        if (mState != INITIALIZED && mState != STOPPED) throw new IllegalStateException();
        if (mState == STOPPED && mVoice != 0) {
            nativeSeek(mVoice, 0);
            mState = PREPARED;
            if (mOnPrepared != null) mOnPrepared.onPrepared(this);
            return;
        }
        mState = PREPARING;
        if (!openSource()) {
            mState = ERROR;
            throw new IOException("Prepare failed");
        }
        mState = PREPARED;
        if (mOnPrepared != null) mOnPrepared.onPrepared(this);
    }

    public void prepareAsync() throws IllegalStateException {
        if (mState != INITIALIZED && mState != STOPPED) throw new IllegalStateException();
        final int generation = ++mPrepareGeneration;
        final boolean rewind = mState == STOPPED && mVoice != 0;
        mState = PREPARING;
        mHandler.post(new Runnable() {
            public void run() {
                if (generation != mPrepareGeneration || mState != PREPARING) return;
                if (rewind) {
                    nativeSeek(mVoice, 0);
                    mState = PREPARED;
                    if (mOnPrepared != null) mOnPrepared.onPrepared(MediaPlayer.this);
                    return;
                }
                if (!openSource()) {
                    mState = ERROR;
                    if (mOnError != null) {
                        mOnError.onError(MediaPlayer.this, MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_UNSUPPORTED);
                    }
                    return;
                }
                mState = PREPARED;
                if (mOnPrepared != null) mOnPrepared.onPrepared(MediaPlayer.this);
            }
        });
    }

    public void start() throws IllegalStateException {
        if (mState != PREPARED && mState != STARTED && mState != PAUSED && mState != PLAYBACK_COMPLETED) {
            throw new IllegalStateException();
        }
        if (mState == PLAYBACK_COMPLETED && mVoice != 0) nativeSeek(mVoice, 0);
        if (mVoice != 0) nativePlay(mVoice);
        mState = STARTED;
        mHandler.removeCallbacks(mPoll);
        mHandler.postDelayed(mPoll, 40);
    }

    public void pause() throws IllegalStateException {
        if (mState != STARTED && mState != PAUSED && mState != PLAYBACK_COMPLETED) throw new IllegalStateException();
        if (mState == STARTED && mVoice != 0) nativePause(mVoice);
        mHandler.removeCallbacks(mPoll);
        mState = PAUSED;
    }

    public void seekTo(int msec) throws IllegalStateException {
        if (mState != PREPARED && mState != STARTED && mState != PAUSED && mState != PLAYBACK_COMPLETED
                && mState != STOPPED) {
            throw new IllegalStateException();
        }
        if (mVoice != 0) nativeSeek(mVoice, msec);
        if (mState == PLAYBACK_COMPLETED) mState = PREPARED;
    }

    public int getDuration() {
        if (!preparedFamily()) throw new IllegalStateException();
        return mVoice == 0 ? 0 : nativeDuration(mVoice);
    }

    public int getCurrentPosition() {
        if (!preparedFamily()) throw new IllegalStateException();
        return mVoice == 0 ? 0 : nativePosition(mVoice);
    }

    public boolean isPlaying() {
        if (mState == IDLE || mState == INITIALIZED || mState == ERROR || mState == END) {
            throw new IllegalStateException();
        }
        return mState == STARTED;
    }

    public void stop() throws IllegalStateException {
        if (mState == END) throw new IllegalStateException();
        mHandler.removeCallbacks(mPoll);
        // Error and the idle states accept stop so VideoView.stopPlayback can release the player.
        if (mState == INITIALIZED || mState == PREPARING || mState == ERROR) {
            mState = IDLE;
            return;
        }
        if (mVoice != 0) nativePause(mVoice);
        if (mState != IDLE) mState = STOPPED;
    }

    public void reset() {
        if (mState == END) throw new IllegalStateException();
        mPrepareGeneration++;
        mHandler.removeCallbacks(mPoll);
        dropVoice();
        mPath = null;
        mUri = null;
        mBytes = null;
        mState = IDLE;
        mHolder = null;
    }

    public void release() {
        mPrepareGeneration++;
        mHandler.removeCallbacks(mPoll);
        dropVoice();
        mState = END;
        mOnPrepared = null;
        mOnCompletion = null;
        mOnError = null;
        mOnInfo = null;
        mOnBuffering = null;
        mOnVideoSize = null;
        mHolder = null;
    }

    private void checkIdle() {
        if (mState == END || mState != IDLE) throw new IllegalStateException();
    }

    private boolean preparedFamily() {
        return mState == PREPARED || mState == STARTED || mState == PAUSED || mState == STOPPED
                || mState == PLAYBACK_COMPLETED;
    }

    private void dropVoice() {
        if (mVoice != 0) {
            nativeRelease(mVoice);
            mVoice = 0;
        }
    }

    private boolean openSource() {
        dropVoice();
        int id = 0;
        if (mBytes != null) {
            id = nativeOpenBytes(mBytes, mStream);
        } else if (mPath != null) {
            id = nativeOpenPath(mPath, mStream);
        } else if (mUri != null) {
            String scheme = mUri.getScheme();
            if (scheme != null && !"file".equals(scheme)) return false;
            String path = mUri.getPath();
            if (path == null) return false;
            id = nativeOpenPath(path, mStream);
        } else {
            return false;
        }
        if (id == 0) return false;
        mVoice = id;
        nativeGain(mVoice, mLeft, mRight);
        nativeLoop(mVoice, mLooping);
        return true;
    }

    private static byte[] readAll(InputStream in, long declared) throws IOException {
        int cap = 16 * 1024 * 1024;
        int limit = cap;
        if (declared > 0 && declared < cap) limit = (int) declared;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while (out.size() < limit && (n = in.read(buf)) >= 0) {
            int room = limit - out.size();
            if (n > room) n = room;
            if (n > 0) out.write(buf, 0, n);
            if (declared > 0 && out.size() >= declared) break;
        }
        return out.toByteArray();
    }

    private void applyScreenOn() {
        if (mHolder != null) mHolder.setKeepScreenOn(mScreenOn);
    }

    private static native int nativeOpenBytes(byte[] data, int stream);
    private static native int nativeOpenPath(String path, int stream);
    private static native void nativePlay(int id);
    private static native void nativePause(int id);
    private static native void nativeSeek(int id, int ms);
    private static native void nativeGain(int id, float left, float right);
    private static native void nativeLoop(int id, boolean loop);
    private static native void nativeRelease(int id);
    private static native int nativePosition(int id);
    private static native int nativeDuration(int id);
    private static native boolean nativeEnded(int id);
}
