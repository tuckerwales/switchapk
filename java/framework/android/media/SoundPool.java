package android.media;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Short clips mixed over the process stream. {@link #load} decodes before it
 * returns, so {@link #play} can run immediately. The load listener is posted
 * to the main looper and runs after load returns.
 */
public class SoundPool {
    public interface OnLoadCompleteListener {
        void onLoadComplete(SoundPool soundPool, int sampleId, int status);
    }

    private static final int MAX_SOUNDS = 64;

    private final int mMax;
    private final int mStreamType;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private OnLoadCompleteListener mListener;
    private int mNextSound = 1;
    private int mNextStream = 1;
    private final int[] mSoundIds = new int[MAX_SOUNDS];
    private final int[] mSoundClips = new int[MAX_SOUNDS];
    private final int[] mStreamIds;
    private final int[] mStreamVoices;
    private final int[] mStreamPrio;
    private final int[] mStreamSounds;
    private final boolean[] mStreamPlay;
    private final boolean[] mStreamAuto;

    public SoundPool(int maxStreams, int streamType, int srcQuality) {
        if (maxStreams <= 0) throw new IllegalArgumentException("maxStreams");
        mMax = maxStreams;
        mStreamType = streamType;
        mStreamIds = new int[maxStreams];
        mStreamVoices = new int[maxStreams];
        mStreamPrio = new int[maxStreams];
        mStreamSounds = new int[maxStreams];
        mStreamPlay = new boolean[maxStreams];
        mStreamAuto = new boolean[maxStreams];
    }

    public static class Builder {
        private int mMax;
        private AudioAttributes mAttr;
        private int mSession;
        private Context mContext;

        public Builder setMaxStreams(int maxStreams) {
            if (maxStreams <= 0) throw new IllegalArgumentException("maxStreams");
            mMax = maxStreams;
            return this;
        }

        public Builder setAudioAttributes(AudioAttributes attributes) {
            if (attributes == null) throw new IllegalArgumentException("attributes");
            mAttr = attributes;
            return this;
        }

        public Builder setAudioSessionId(int sessionId) {
            mSession = sessionId;
            return this;
        }

        public Builder setContext(Context context) {
            mContext = context;
            return this;
        }

        public SoundPool build() {
            if (mMax <= 0) throw new UnsupportedOperationException("maxStreams not set");
            int stream = mAttr != null ? mAttr.getVolumeControlStream() : AudioManager.STREAM_MUSIC;
            return new SoundPool(mMax, stream, 0);
        }
    }

    public void setOnLoadCompleteListener(OnLoadCompleteListener listener) { mListener = listener; }

    public int load(String path, int priority) {
        if (path == null) return 0;
        int clip = nativeLoadPath(path);
        return finishLoad(clip);
    }

    public int load(Context context, int resId, int priority) {
        try {
            AssetFileDescriptor afd = context.getResources().openRawResourceFd(resId);
            try {
                return load(afd, priority);
            } finally {
                afd.close();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    public int load(AssetFileDescriptor afd, int priority) {
        if (afd == null) return 0;
        try {
            InputStream in = afd.createInputStream();
            try {
                return finishLoad(nativeLoad(readAll(in, afd.getLength())));
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return 0;
        }
    }

    public int load(FileDescriptor fd, long offset, long length, int priority) {
        if (fd == null || !fd.valid()) return 0;
        try {
            FileInputStream in = new FileInputStream(fd);
            try {
                if (offset > 0) in.skip(offset);
                return finishLoad(nativeLoad(readAll(in, length)));
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return 0;
        }
    }

    public boolean unload(int soundID) {
        for (int i = 0; i < mSoundIds.length; i++) {
            if (mSoundIds[i] != soundID) continue;
            if (mSoundClips[i] != 0) nativeUnload(mSoundClips[i]);
            mSoundIds[i] = 0;
            mSoundClips[i] = 0;
            return true;
        }
        return false;
    }

    public int play(int soundID, float leftVolume, float rightVolume, int priority, int loop, float rate) {
        int clip = clipFor(soundID);
        if (clip == 0) return 0;
        int slot = slotFor(priority);
        if (slot < 0) return 0;
        if (mStreamPlay[slot] && mStreamVoices[slot] != 0) nativeStop(mStreamVoices[slot]);
        if (rate < 0.5f) rate = 0.5f;
        if (rate > 2f) rate = 2f;
        int voice = nativePlay(clip, leftVolume, rightVolume, rate, loop, mStreamType);
        if (voice == 0) return 0;
        int id = mNextStream++;
        mStreamIds[slot] = id;
        mStreamVoices[slot] = voice;
        mStreamPrio[slot] = priority;
        mStreamSounds[slot] = soundID;
        mStreamPlay[slot] = true;
        mStreamAuto[slot] = false;
        return id;
    }

    public void pause(int streamID) {
        int slot = streamSlot(streamID);
        if (slot < 0 || !mStreamPlay[slot]) return;
        nativePause(mStreamVoices[slot]);
        mStreamPlay[slot] = false;
        mStreamAuto[slot] = false;
    }

    public void resume(int streamID) {
        int slot = streamSlot(streamID);
        if (slot < 0 || mStreamPlay[slot] || mStreamVoices[slot] == 0) return;
        nativeResume(mStreamVoices[slot]);
        mStreamPlay[slot] = true;
    }

    public void autoPause() {
        for (int i = 0; i < mMax; i++) {
            if (!mStreamPlay[i]) continue;
            nativePause(mStreamVoices[i]);
            mStreamPlay[i] = false;
            mStreamAuto[i] = true;
        }
    }

    public void autoResume() {
        for (int i = 0; i < mMax; i++) {
            if (!mStreamAuto[i]) continue;
            nativeResume(mStreamVoices[i]);
            mStreamPlay[i] = true;
            mStreamAuto[i] = false;
        }
    }

    public void stop(int streamID) {
        int slot = streamSlot(streamID);
        if (slot < 0) return;
        if (mStreamVoices[slot] != 0) nativeStop(mStreamVoices[slot]);
        mStreamPlay[slot] = false;
        mStreamAuto[slot] = false;
        mStreamVoices[slot] = 0;
        mStreamIds[slot] = 0;
    }

    public void setVolume(int streamID, float leftVolume, float rightVolume) {
        int slot = streamSlot(streamID);
        if (slot >= 0 && mStreamVoices[slot] != 0) nativeGain(mStreamVoices[slot], leftVolume, rightVolume);
    }

    public void setPriority(int streamID, int priority) {
        int slot = streamSlot(streamID);
        if (slot >= 0) mStreamPrio[slot] = priority;
    }

    public void setLoop(int streamID, int loop) {
        int slot = streamSlot(streamID);
        if (slot >= 0 && mStreamVoices[slot] != 0) nativeLoop(mStreamVoices[slot], loop);
    }

    public void setRate(int streamID, float rate) {
        int slot = streamSlot(streamID);
        if (slot < 0 || mStreamVoices[slot] == 0) return;
        if (rate < 0.5f) rate = 0.5f;
        if (rate > 2f) rate = 2f;
        nativeRate(mStreamVoices[slot], rate);
    }

    public void release() {
        for (int i = 0; i < mMax; i++) {
            if (mStreamVoices[i] != 0) nativeStop(mStreamVoices[i]);
            mStreamVoices[i] = 0;
            mStreamPlay[i] = false;
        }
        for (int i = 0; i < mSoundIds.length; i++) {
            if (mSoundClips[i] != 0) nativeUnload(mSoundClips[i]);
            mSoundClips[i] = 0;
            mSoundIds[i] = 0;
        }
        mListener = null;
    }

    private int finishLoad(int clip) {
        if (clip == 0) return 0;
        int slot = -1;
        for (int i = 0; i < mSoundIds.length; i++) {
            if (mSoundIds[i] == 0) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            nativeUnload(clip);
            return 0;
        }
        final int id = mNextSound++;
        mSoundIds[slot] = id;
        mSoundClips[slot] = clip;
        mHandler.post(new Runnable() {
            public void run() {
                if (mListener != null) mListener.onLoadComplete(SoundPool.this, id, 0);
            }
        });
        return id;
    }

    private int clipFor(int soundID) {
        for (int i = 0; i < mSoundIds.length; i++) {
            if (mSoundIds[i] == soundID) return mSoundClips[i];
        }
        return 0;
    }

    private int slotFor(int priority) {
        int active = 0;
        int lowest = Integer.MAX_VALUE;
        int lowestSlot = -1;
        int free = -1;
        for (int i = 0; i < mMax; i++) {
            if (!mStreamPlay[i]) {
                if (free < 0) free = i;
                continue;
            }
            active++;
            if (mStreamPrio[i] < lowest) {
                lowest = mStreamPrio[i];
                lowestSlot = i;
            }
        }
        if (active < mMax) return free;
        if (lowestSlot >= 0 && priority >= lowest) return lowestSlot;
        return -1;
    }

    private int streamSlot(int streamID) {
        if (streamID == 0) return -1;
        for (int i = 0; i < mMax; i++) {
            if (mStreamIds[i] == streamID) return i;
        }
        return -1;
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
        }
        return out.toByteArray();
    }

    private static native int nativeLoad(byte[] data);
    private static native int nativeLoadPath(String path);
    private static native void nativeUnload(int clip);
    private static native int nativePlay(int clip, float left, float right, float rate, int loop, int stream);
    private static native void nativePause(int voice);
    private static native void nativeResume(int voice);
    private static native void nativeStop(int voice);
    private static native void nativeGain(int voice, float left, float right);
    private static native void nativeLoop(int voice, int loop);
    private static native void nativeRate(int voice, float rate);
}
