package com.example.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** A focusable tile; draws a white dot for every click it received. */
public class TileView extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    int clicks;

    public TileView(Context context) { this(context, null); }

    public TileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint.setColor(0xFFFFFFFF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = getHeight() / 10f;
        for (int i = 0; i < clicks; i++) canvas.drawCircle(r * 2 + i * r * 3, r * 2, r, mPaint);
    }
}
