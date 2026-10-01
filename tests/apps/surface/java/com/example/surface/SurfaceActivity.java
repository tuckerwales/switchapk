package com.example.surface;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.LinearLayout;

/** A render thread draws into a SurfaceView; a TextureView is drawn with lockCanvas from the UI thread. */
public class SurfaceActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = "SurfaceTest";
    private RenderThread mThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        SurfaceView surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        root.addView(surfaceView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        final TextureView textureView = new TextureView(this);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.i(TAG, "texture available " + width + "x" + height);
                Canvas c = textureView.lockCanvas();
                c.drawColor(0xFF4CAF50);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Color.WHITE);
                c.drawCircle(width / 2f, height / 2f, height / 6f, p);
                textureView.unlockCanvasAndPost(c);
            }

            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return true; }

            public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
        });
        root.addView(textureView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        setContentView(root);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "surface created");
        mThread = new RenderThread(holder);
        mThread.start();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "surface changed " + width + "x" + height);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "surface destroyed");
        mThread.running = false;
        try {
            mThread.join();
        } catch (InterruptedException e) {
        }
    }

    static class RenderThread extends Thread {
        final SurfaceHolder holder;
        volatile boolean running = true;

        RenderThread(SurfaceHolder holder) { this.holder = holder; }

        @Override
        public void run() {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.YELLOW);
            long start = SystemClock.uptimeMillis();
            int frames = 0;
            boolean reported = false;
            while (running) {
                Canvas c = holder.lockCanvas();
                if (c == null) break;
                c.drawColor(0xFFF44336);
                float x = (frames % 60) * 4;
                c.drawRect(20 + x, 20, 120 + x, 120, p);
                holder.unlockCanvasAndPost(c);
                frames++;
                long elapsed = SystemClock.uptimeMillis() - start;
                if (!reported && elapsed >= 1000) {
                    reported = true;
                    Log.i(TAG, "frames in first second: " + frames);
                }
            }
            Log.i(TAG, "render thread done after " + frames + " frames");
        }
    }
}
