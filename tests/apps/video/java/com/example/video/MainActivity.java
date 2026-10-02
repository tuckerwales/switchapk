package com.example.video;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

/**
 * VideoView placeholder. A MediaController anchored to the stage shows a
 * paused position of 1:05 in a 2:05 clip. Play asks VideoView to open a
 * missing file; prepare fails and the framework error dialog appears.
 */
public class MainActivity extends Activity {
    private static final String TAG = "Video";
    private boolean mPrepared;

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "VVCHECK ok " + name);
        else Log.e(TAG, "VVCHECK FAIL " + name + " " + detail);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final TextView status = findViewById(R.id.status);
        final VideoView video = findViewById(R.id.video);
        final FrameLayout stage = findViewById(R.id.stage);
        final Button play = findViewById(R.id.play);

        check("duration idle", video.getDuration() == -1, video.getDuration());
        check("position idle", video.getCurrentPosition() == 0, video.getCurrentPosition());
        check("not playing", !video.isPlaying(), null);
        check("session", video.getAudioSessionId() != 0, video.getAudioSessionId());
        check("cannot pause yet", !video.canPause(), null);
        check("buffer empty", video.getBufferPercentage() == 0, video.getBufferPercentage());
        check("class", "android.widget.VideoView".equals(video.getAccessibilityClassName()),
                video.getAccessibilityClassName());
        boolean badFocus = false;
        try {
            video.setAudioFocusRequest(99);
        } catch (IllegalArgumentException e) {
            badFocus = true;
        }
        check("bad focus", badFocus, null);
        video.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE);
        boolean nullAttr = false;
        try {
            video.setAudioAttributes(null);
        } catch (IllegalArgumentException e) {
            nullAttr = true;
        }
        check("null attributes", nullAttr, null);
        video.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build());

        final MediaController controls = new MediaController(this);
        controls.setMediaPlayer(new PausedClip());
        controls.setAnchorView(stage);
        stage.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (stage.getWidth() < 100 || stage.getHeight() < 100) return;
                stage.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                controls.show(0);
                check("controller showing", controls.isShowing(), null);
                check("video sized", video.getWidth() > 100 && video.getHeight() > 100,
                        video.getWidth() + "x" + video.getHeight());
                status.setText("controls");
            }
        });

        play.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                controls.hide();
                check("controller hidden", !controls.isShowing(), null);
                video.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
                    public void onPrepared(android.media.MediaPlayer mp) { mPrepared = true; }
                });
                video.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
                    public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
                        check("error codes", what == 1 && extra == -1010, what + "," + extra);
                        check("duration after error", video.getDuration() == -1, video.getDuration());
                        check("still stopped", !video.isPlaying(), null);
                        check("prepare skipped", !mPrepared, null);
                        status.setText("error");
                        return false;
                    }
                });
                video.setVideoURI(Uri.parse("file:///no/such/video.mp4"));
            }
        });
    }

    /** A paused 2:05 clip sitting at 1:05, so the controller can draw without a decoder. */
    static final class PausedClip implements MediaController.MediaPlayerControl {
        public void start() {}
        public void pause() {}
        public int getDuration() { return 125000; }
        public int getCurrentPosition() { return 65000; }
        public void seekTo(int pos) {}
        public boolean isPlaying() { return false; }
        public int getBufferPercentage() { return 0; }
        public boolean canPause() { return true; }
        public boolean canSeekBackward() { return true; }
        public boolean canSeekForward() { return true; }
        public int getAudioSessionId() { return 1; }
    }
}
