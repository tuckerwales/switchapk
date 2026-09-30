package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.AttributeSet;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Touch feedback: content layers plus a highlight in the ripple color while
 * pressed/focused, clipped to the mask layer (or content, or a circle for
 * unbounded ripples). The highlight fades in and out.
 */
public class RippleDrawable extends LayerDrawable {
    public static final int RADIUS_AUTO = -1;
    private static final int MASK_LAYER_ID = android.R.id.mask;
    private static final long FADE_IN_MS = 90;
    private static final long FADE_OUT_MS = 220;

    private ColorStateList mColor = ColorStateList.valueOf(0x1f000000);
    private int mMaxRadius = RADIUS_AUTO;
    private Drawable mMask;
    private boolean mActive;
    private float mLevel; // 0..1 highlight intensity
    private long mAnimStart;
    private float mAnimFrom;
    private boolean mAnimating;
    private float mHotspotX = -1, mHotspotY = -1;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    RippleDrawable() { super(); }

    public RippleDrawable(ColorStateList color, Drawable content, Drawable mask) {
        super(content != null ? new Drawable[] {content} : new Drawable[0]);
        if (color == null) throw new IllegalArgumentException("RippleDrawable requires a non-null color");
        mColor = color;
        if (mask != null) {
            int idx = addLayer(mask);
            setId(idx, MASK_LAYER_ID);
        }
        updateMask();
    }

    private void updateMask() { mMask = findDrawableByLayerId(MASK_LAYER_ID); }

    public void setColor(ColorStateList color) {
        mColor = color;
        invalidateSelf();
    }

    public void setEffectColor(ColorStateList color) { setColor(color); }
    public ColorStateList getEffectColor() { return mColor; }
    public void setRadius(int radius) { mMaxRadius = radius; invalidateSelf(); }
    public int getRadius() { return mMaxRadius; }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final TypedArray a = obtainAttributes(r, theme, attrs, new int[] {android.R.attr.color, android.R.attr.radius});
        ColorStateList c = a.getColorStateList(0);
        if (c != null) mColor = c;
        mMaxRadius = a.getDimensionPixelSize(1, mMaxRadius);
        a.recycle();
        setPaddingMode(PADDING_MODE_STACK);
        super.inflate(r, parser, attrs, theme);
        updateMask();
    }

    @Override
    public boolean setDrawableByLayerId(int id, Drawable drawable) {
        boolean r = super.setDrawableByLayerId(id, drawable);
        updateMask();
        return r;
    }

    @Override
    public boolean isStateful() { return true; }

    @Override
    public boolean hasFocusStateSpecified() { return true; }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        final boolean changed = super.onStateChange(stateSet);
        boolean enabled = false, pressed = false, focused = false, hovered = false;
        for (int state : stateSet) {
            if (state == android.R.attr.state_enabled) enabled = true;
            else if (state == android.R.attr.state_focused) focused = true;
            else if (state == android.R.attr.state_pressed) pressed = true;
            else if (state == android.R.attr.state_hovered) hovered = true;
        }
        boolean active = enabled && (pressed || focused || hovered);
        if (active != mActive) {
            mActive = active;
            mAnimFrom = mLevel;
            mAnimStart = SystemClock.uptimeMillis();
            if (!mAnimating) {
                mAnimating = true;
                scheduleSelf(mTick, mAnimStart + 16);
            }
            invalidateSelf();
            return true;
        }
        return changed;
    }

    private final Runnable mTick = new Runnable() {
        public void run() {
            long now = SystemClock.uptimeMillis();
            float dur = mActive ? FADE_IN_MS : FADE_OUT_MS;
            float t = Math.min(1f, (now - mAnimStart) / dur);
            float target = mActive ? 1f : 0f;
            mLevel = mAnimFrom + (target - mAnimFrom) * t;
            invalidateSelf();
            if (t < 1f) scheduleSelf(this, now + 16);
            else mAnimating = false;
        }
    };

    @Override
    public void jumpToCurrentState() {
        super.jumpToCurrentState();
        mLevel = mActive ? 1f : 0f;
        mAnimating = false;
        unscheduleSelf(mTick);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (!visible) jumpToCurrentState();
        return changed;
    }

    @Override
    public void setHotspot(float x, float y) {
        mHotspotX = x;
        mHotspotY = y;
    }

    @Override
    public void draw(Canvas canvas) {
        // content (everything but the mask)
        for (int i = 0; i < getNumberOfLayers(); i++) {
            Drawable d = getDrawable(i);
            if (d != null && d != mMask) d.draw(canvas);
        }
        if (mLevel <= 0.01f) return;
        int color = mColor.getColorForState(getState(), mColor.getDefaultColor());
        int alpha = (int) ((color >>> 24) * mLevel);
        if (alpha <= 0) return;
        int c = (color & 0xffffff) | (alpha << 24);
        final Rect bounds = getBounds();
        if (mMask != null) {
            mMask.setColorFilter(new PorterDuffColorFilter(c, PorterDuff.Mode.SRC_IN));
            mMask.draw(canvas);
            mMask.setColorFilter(null);
            return;
        }
        boolean hasContent = false;
        for (int i = 0; i < getNumberOfLayers(); i++) if (getDrawable(i) != null) hasContent = true;
        if (hasContent) {
            // content defines the shape
            for (int i = 0; i < getNumberOfLayers(); i++) {
                Drawable d = getDrawable(i);
                if (d == null) continue;
                d.setColorFilter(new PorterDuffColorFilter(c, PorterDuff.Mode.SRC_IN));
                d.draw(canvas);
                d.setColorFilter(null);
            }
            return;
        }
        // unbounded ripple: circle around the hotspot or center
        float cx = bounds.exactCenterX(), cy = bounds.exactCenterY();
        float radius = mMaxRadius > 0 ? mMaxRadius : (float) Math.hypot(bounds.width(), bounds.height()) / 2f;
        mPaint.setColor(c);
        canvas.drawCircle(cx, cy, radius * (0.6f + 0.4f * mLevel), mPaint);
    }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    @Override
    public ConstantState getConstantState() {
        final RippleDrawable self = this;
        return new ConstantState() {
            public Drawable newDrawable() { return self.copy(null); }
            public Drawable newDrawable(Resources res) { return self.copy(res); }
            public int getChangingConfigurations() { return 0; }
        };
    }

    private RippleDrawable copy(Resources res) {
        RippleDrawable r = new RippleDrawable();
        r.mLayerState = new LayerState(mLayerState, r, res);
        r.ensurePadding();
        r.refreshPadding();
        r.mColor = mColor;
        r.mMaxRadius = mMaxRadius;
        r.updateMask();
        return r;
    }
}
