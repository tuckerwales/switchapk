package android.media;

import android.content.Context;
import android.net.Uri;

/** Plays its URI with a MediaPlayer; a default URI plays whatever the app stored for that type. */
public class Ringtone {
    private final Context mContext;
    private final Uri mUri;
    private AudioAttributes mAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    private int mStreamType = AudioManager.STREAM_RING;
    private boolean mLooping;
    private float mVolume = 1f;
    private MediaPlayer mPlayer;

    Ringtone() {
        this(null, null);
    }

    Ringtone(Context context, Uri uri) {
        mContext = context;
        mUri = uri;
    }

    @Deprecated
    public void setStreamType(int streamType) {
        mStreamType = streamType;
    }

    @Deprecated
    public int getStreamType() {
        return mStreamType;
    }

    public void setAudioAttributes(AudioAttributes attributes) throws IllegalArgumentException {
        if (attributes == null) {
            throw new IllegalArgumentException("Invalid null AudioAttributes for Ringtone");
        }
        mAttributes = attributes;
    }

    public AudioAttributes getAudioAttributes() {
        return mAttributes;
    }

    public void setLooping(boolean looping) {
        mLooping = looping;
        if (mPlayer != null) {
            mPlayer.setLooping(looping);
        }
    }

    public boolean isLooping() {
        return mLooping;
    }

    public void setVolume(float volume) {
        mVolume = Math.max(0f, Math.min(1f, volume));
        if (mPlayer != null) {
            mPlayer.setVolume(mVolume, mVolume);
        }
    }

    public float getVolume() {
        return mVolume;
    }

    public boolean setHapticGeneratorEnabled(boolean enabled) {
        return false;
    }

    public boolean isHapticGeneratorEnabled() {
        return false;
    }

    public String getTitle(Context context) {
        if (mUri == null) {
            return "None";
        }
        int type = RingtoneManager.getDefaultType(mUri);
        if (type != -1) {
            return "Default";
        }
        String last = mUri.getLastPathSegment();
        return last != null ? last : mUri.toString();
    }

    public void play() {
        stop();
        Uri uri = mUri;
        if (uri != null && RingtoneManager.isDefault(uri) && mContext != null) {
            uri = RingtoneManager.getActualDefaultRingtoneUri(mContext, RingtoneManager.getDefaultType(uri));
        }
        if (uri == null || mContext == null) {
            return;
        }
        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(mContext, uri);
            player.setAudioAttributes(mAttributes);
            player.setLooping(mLooping);
            player.setVolume(mVolume, mVolume);
            player.prepare();
            player.start();
            mPlayer = player;
        } catch (Exception e) {
            player.release();
        }
    }

    public void stop() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }

    public boolean isPlaying() {
        return mPlayer != null && mPlayer.isPlaying();
    }

    protected void finalize() {
        stop();
    }
}
