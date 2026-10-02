package android.media;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Playback engine (placeholder until WS7). Data sources are remembered and
 * {@link #prepareAsync} always fails with {@link #MEDIA_ERROR_UNKNOWN} /
 * {@link #MEDIA_ERROR_UNSUPPORTED}, because there is no decoder. Listeners
 * run on the main looper, as on Android.
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
    private static final int ERROR = 3;
    private static final int END = 4;

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
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private OnPreparedListener mOnPrepared;
    private OnCompletionListener mOnCompletion;
    private OnErrorListener mOnError;
    private OnInfoListener mOnInfo;
    private OnBufferingUpdateListener mOnBuffering;
    private OnVideoSizeChangedListener mOnVideoSize;
    private SurfaceHolder mHolder;
    private boolean mScreenOn;

    public MediaPlayer() {}

    public MediaPlayer(Context context) {}

    public void setOnPreparedListener(OnPreparedListener listener) { mOnPrepared = listener; }

    public void setOnCompletionListener(OnCompletionListener listener) { mOnCompletion = listener; }

    public void setOnErrorListener(OnErrorListener listener) { mOnError = listener; }

    public void setOnInfoListener(OnInfoListener listener) { mOnInfo = listener; }

    public void setOnBufferingUpdateListener(OnBufferingUpdateListener listener) { mOnBuffering = listener; }

    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener listener) { mOnVideoSize = listener; }

    public void setDataSource(String path) throws IOException, IllegalArgumentException, IllegalStateException,
            SecurityException {
        if (path == null) throw new IllegalArgumentException("path is null");
        setDataSource(null, Uri.parse(path), null);
    }

    public void setDataSource(Context context, Uri uri) throws IOException, IllegalArgumentException,
            IllegalStateException, SecurityException {
        setDataSource(context, uri, null);
    }

    public void setDataSource(Context context, Uri uri, Map<String, String> headers) throws IOException,
            IllegalArgumentException, IllegalStateException, SecurityException {
        if (mState == END) throw new IllegalStateException();
        if (mState != IDLE) throw new IllegalStateException();
        if (uri == null) throw new IllegalArgumentException("uri is null");
        mState = INITIALIZED;
    }

    public void setDisplay(SurfaceHolder sh) {
        mHolder = sh;
        applyScreenOn();
    }

    public void setAudioAttributes(AudioAttributes attributes) throws IllegalArgumentException {
        if (attributes == null) throw new IllegalArgumentException("Illegal null AudioAttributes");
    }

    public void setAudioStreamType(int streamtype) {}

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

    /** framework-internal (hidden in AOSP). Subtitles are not decoded. */
    public void addSubtitleSource(InputStream is, MediaFormat format) throws IllegalStateException {
        if (mState == END || mState == ERROR) throw new IllegalStateException();
        if (mOnInfo != null) mOnInfo.onInfo(this, MEDIA_INFO_UNSUPPORTED_SUBTITLE, 0);
    }

    public void prepareAsync() throws IllegalStateException {
        if (mState != INITIALIZED) throw new IllegalStateException();
        mState = PREPARING;
        final int generation = ++mPrepareGeneration;
        mHandler.post(new Runnable() {
            public void run() {
                if (generation != mPrepareGeneration || mState == END) return;
                mState = ERROR;
                // TODO(WS7): decode and call mOnPrepared instead.
                if (mOnError != null) mOnError.onError(MediaPlayer.this, MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_UNSUPPORTED);
            }
        });
    }

    public void start() throws IllegalStateException { throw new IllegalStateException(); }

    public void pause() throws IllegalStateException { throw new IllegalStateException(); }

    public void seekTo(int msec) throws IllegalStateException { throw new IllegalStateException(); }

    public int getDuration() { throw new IllegalStateException(); }

    public int getCurrentPosition() { throw new IllegalStateException(); }

    public boolean isPlaying() { throw new IllegalStateException(); }

    public void stop() throws IllegalStateException {
        if (mState == END) throw new IllegalStateException();
        // Error and the idle states accept stop so VideoView.stopPlayback can release the player.
        if (mState == INITIALIZED || mState == PREPARING || mState == ERROR) mState = IDLE;
    }

    public void reset() {
        if (mState == END) throw new IllegalStateException();
        mPrepareGeneration++;
        mState = IDLE;
        mHolder = null;
    }

    public void release() {
        mPrepareGeneration++;
        mState = END;
        mOnPrepared = null;
        mOnCompletion = null;
        mOnError = null;
        mOnInfo = null;
        mOnBuffering = null;
        mOnVideoSize = null;
        mHolder = null;
    }

    private void applyScreenOn() {
        if (mHolder != null) mHolder.setKeepScreenOn(mScreenOn);
    }
}
