package com.example.audiomix;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;

/**
 * Plays MediaPlayer, SoundPool, AudioTrack, ToneGenerator and an OpenSL
 * buffer queue together. After a short delay the window is green when every
 * source has contributed a non-silent sample, and magenta otherwise.
 */
public class AudioActivity extends Activity {
    private static final String TAG = "Audio";
    private static final int GREEN = 0xFF2E7D32;
    private static final int MAGENTA = 0xFFFF00FF;
    /** MixDebug.SRC_MEDIA | POOL | TRACK | OPENSL | TONE. */
    private static final int WANT = 1 | 2 | 4 | 8 | 16;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final View view = new View(this);
        view.setBackgroundColor(MAGENTA);
        setContentView(view);
        boolean opensl = false;
        try {
            System.loadLibrary("openslmix");
            opensl = NativeMix.start();
        } catch (Throwable t) {
            Log.i(TAG, "MIXCHECK opensl " + t);
        }
        boolean started = opensl;
        try {
            File dir = getFilesDir();
            File music = new File(dir, "music.wav");
            File beep = new File(dir, "beep.wav");
            writeWav(music, 109);
            writeWav(beep, 27);
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(music.getAbsolutePath());
            mp.setLooping(true);
            mp.prepare();
            mp.start();
            SoundPool pool = new SoundPool(4, AudioManager.STREAM_MUSIC, 0);
            int sound = pool.load(beep.getAbsolutePath(), 1);
            if (pool.play(sound, 1f, 1f, 1, -1, 1f) == 0) started = false;
            int frames = 48000;
            short[] pcm = new short[frames];
            for (int i = 0; i < frames; i++) {
                pcm[i] = ((i / 36) & 1) == 0 ? (short) 14000 : (short) -14000;
            }
            int bytes = pcm.length * 2;
            AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, 48000, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bytes, AudioTrack.MODE_STATIC);
            if (track.write(pcm, 0, pcm.length) != pcm.length) started = false;
            track.play();
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
            if (!tone.startTone(ToneGenerator.TONE_PROP_BEEP, 2000)) started = false;
        } catch (Throwable t) {
            started = false;
            Log.i(TAG, "MIXCHECK setup " + t);
        }
        final boolean armed = started;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                int mask = 0;
                int nz = 0;
                boolean reflected = false;
                try {
                    Class<?> dbg = Class.forName("android.media.MixDebug");
                    Method maskM = dbg.getMethod("getSourceMask");
                    Method nzM = dbg.getMethod("getNonZeroFrames");
                    mask = ((Integer) maskM.invoke(null)).intValue();
                    nz = ((Integer) nzM.invoke(null)).intValue();
                    reflected = true;
                } catch (Throwable t) {
                    Log.i(TAG, "MIXCHECK reflect " + t);
                }
                boolean ok = armed && reflected && (mask & WANT) == WANT && nz > 0;
                view.setBackgroundColor(ok ? GREEN : MAGENTA);
                Log.i(TAG, "MIXCHECK mask=" + mask + " nz=" + nz + " " + (ok ? "ok" : "FAIL"));
            }
        }, 600);
    }

    /** 16-bit mono WAV, half a second, square wave with the given half-period in samples. */
    private static void writeWav(File file, int halfPeriod) throws Exception {
        int rate = 48000;
        int frames = rate / 2;
        int dataBytes = frames * 2;
        byte[] wav = new byte[44 + dataBytes];
        wav[0] = 'R';
        wav[1] = 'I';
        wav[2] = 'F';
        wav[3] = 'F';
        put32(wav, 4, 36 + dataBytes);
        wav[8] = 'W';
        wav[9] = 'A';
        wav[10] = 'V';
        wav[11] = 'E';
        wav[12] = 'f';
        wav[13] = 'm';
        wav[14] = 't';
        wav[15] = ' ';
        put32(wav, 16, 16);
        put16(wav, 20, 1);
        put16(wav, 22, 1);
        put32(wav, 24, rate);
        put32(wav, 28, rate * 2);
        put16(wav, 32, 2);
        put16(wav, 34, 16);
        wav[36] = 'd';
        wav[37] = 'a';
        wav[38] = 't';
        wav[39] = 'a';
        put32(wav, 40, dataBytes);
        for (int i = 0; i < frames; i++) {
            int v = ((i / halfPeriod) & 1) == 0 ? 12000 : -12000;
            wav[44 + i * 2] = (byte) v;
            wav[45 + i * 2] = (byte) (v >> 8);
        }
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(wav);
        } finally {
            out.close();
        }
    }

    private static void put16(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >> 8);
    }

    private static void put32(byte[] b, int off, int v) {
        put16(b, off, v);
        put16(b, off + 2, v >> 16);
    }
}
