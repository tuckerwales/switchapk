package com.example.hello;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;

/** Full-screen custom view: dark background, a gold rectangle, and a label. */
public class HelloActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new HelloView(this));
    }

    static class HelloView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        HelloView(Context context) { super(context); }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(0xFF101820);
            mPaint.setColor(0xFFE8C547);
            canvas.drawRect(80, 80, 520, 280, mPaint);
            mPaint.setColor(0xFFFFFFFF);
            mPaint.setTextSize(64);
            canvas.drawText("Hello Switch", 80, 400, mPaint);
        }
    }
}
