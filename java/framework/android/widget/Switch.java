package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.text.TextPaint;
import android.util.AttributeSet;

/**
 * A thumb that slides between off and on (AOSP Switch). There is no animation
 * yet: setChecked jumps the thumb to the end. With no track drawable, a flat
 * rounded track is drawn so the control is visible.
 */
public class Switch extends CompoundButton {
    private static final int[] ATTRS = {
        android.R.attr.thumb, android.R.attr.track, android.R.attr.switchMinWidth, android.R.attr.switchPadding,
        android.R.attr.thumbTextPadding, android.R.attr.showText, android.R.attr.textOn, android.R.attr.textOff,
        android.R.attr.thumbTint, android.R.attr.trackTint, android.R.attr.thumbTintMode, android.R.attr.trackTintMode,
    };
    private static final int[] TEXT_ATTRS = {
        android.R.attr.textSize, android.R.attr.textColor, android.R.attr.typeface, android.R.attr.textStyle,
    };

    private Drawable mThumbDrawable;
    private Drawable mTrackDrawable;
    private ColorStateList mThumbTintList;
    private ColorStateList mTrackTintList;
    private PorterDuff.Mode mThumbTintMode;
    private PorterDuff.Mode mTrackTintMode;
    private BlendMode mThumbBlendMode;
    private BlendMode mTrackBlendMode;
    private boolean mHasThumbTint;
    private boolean mHasThumbTintMode;
    private boolean mHasTrackTint;
    private boolean mHasTrackTintMode;
    private int mSwitchMinWidth;
    private int mSwitchPadding;
    private int mThumbTextPadding;
    private boolean mShowText;
    private boolean mSplitTrack;
    private CharSequence mTextOn = "";
    private CharSequence mTextOff = "";
    private int mSwitchWidth;
    private int mSwitchHeight;
    private int mThumbWidth;
    private TextPaint mTextPaint;
    private int mTextColor = 0xFF000000;
    private Paint mFallbackPaint;

    public Switch(Context context) { this(context, null); }

    public Switch(Context context, AttributeSet attrs) { this(context, attrs, android.R.attr.switchStyle); }

    public Switch(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public Switch(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        float density = getResources().getDisplayMetrics().density;
        mTextPaint.density = density;
        mTextPaint.setTextSize(14f * getResources().getDisplayMetrics().scaledDensity);
        if (attrs == null && defStyleAttr == 0 && defStyleRes == 0) return;
        TypedArray a = context.obtainStyledAttributes(attrs, ATTRS, defStyleAttr, defStyleRes);
        Drawable thumb = a.getDrawable(0);
        Drawable track = a.getDrawable(1);
        mSwitchMinWidth = a.getDimensionPixelSize(2, 0);
        mSwitchPadding = a.getDimensionPixelSize(3, 0);
        mThumbTextPadding = a.getDimensionPixelSize(4, 0);
        mShowText = a.getBoolean(5, false);
        CharSequence on = a.getText(6);
        CharSequence off = a.getText(7);
        ColorStateList thumbTint = a.getColorStateList(8);
        ColorStateList trackTint = a.getColorStateList(9);
        int thumbMode = a.getInt(10, -1);
        int trackMode = a.getInt(11, -1);
        a.recycle();
        if (on != null) mTextOn = on;
        if (off != null) mTextOff = off;
        if (thumb != null) setThumbDrawable(thumb);
        if (track != null) setTrackDrawable(track);
        if (thumbTint != null) setThumbTintList(thumbTint);
        if (trackTint != null) setTrackTintList(trackTint);
        if (thumbMode != -1) setThumbTintMode(Drawable.parseTintMode(thumbMode, null));
        if (trackMode != -1) setTrackTintMode(Drawable.parseTintMode(trackMode, null));
    }

    public void setSwitchTextAppearance(Context context, int resid) {
        if (resid == 0) return;
        TypedArray a = context.obtainStyledAttributes(resid, TEXT_ATTRS);
        int size = a.getDimensionPixelSize(0, 0);
        if (size != 0) mTextPaint.setTextSize(size);
        ColorStateList colors = a.getColorStateList(1);
        if (colors != null) mTextColor = colors.getDefaultColor();
        int typefaceIndex = a.getInt(2, -1);
        int styleIndex = a.getInt(3, -1);
        a.recycle();
        Typeface tf = null;
        if (typefaceIndex == 1) tf = Typeface.SANS_SERIF;
        else if (typefaceIndex == 2) tf = Typeface.SERIF;
        else if (typefaceIndex == 3) tf = Typeface.MONOSPACE;
        if (typefaceIndex != -1 || styleIndex != -1) setSwitchTypeface(tf, styleIndex);
        requestLayout();
    }

    public void setSwitchTypeface(Typeface tf, int style) {
        if (style > 0) {
            tf = tf == null ? Typeface.defaultFromStyle(style) : Typeface.create(tf, style);
            setSwitchTypeface(tf);
            int fake = (~tf.getStyle()) & style;
            mTextPaint.setFakeBoldText((fake & Typeface.BOLD) != 0);
            mTextPaint.setTextSkewX((fake & Typeface.ITALIC) != 0 ? -0.25f : 0);
        } else {
            mTextPaint.setFakeBoldText(false);
            mTextPaint.setTextSkewX(0);
            setSwitchTypeface(tf);
        }
    }

    public void setSwitchTypeface(Typeface tf) {
        if (mTextPaint.getTypeface() != tf) {
            mTextPaint.setTypeface(tf);
            requestLayout();
            invalidate();
        }
    }

    public void setSwitchPadding(int pixels) {
        mSwitchPadding = pixels;
        requestLayout();
    }

    public int getSwitchPadding() { return mSwitchPadding; }

    public void setSwitchMinWidth(int pixels) {
        mSwitchMinWidth = pixels;
        requestLayout();
    }

    public int getSwitchMinWidth() { return mSwitchMinWidth; }

    public void setThumbTextPadding(int pixels) {
        mThumbTextPadding = pixels;
        requestLayout();
    }

    public int getThumbTextPadding() { return mThumbTextPadding; }

    public void setTrackDrawable(Drawable track) { setDrawable(false, track); }

    public void setTrackResource(int resId) { setTrackDrawable(resId == 0 ? null : getContext().getDrawable(resId)); }

    public Drawable getTrackDrawable() { return mTrackDrawable; }

    public void setTrackIcon(Icon icon) { setTrackDrawable(icon == null ? null : icon.loadDrawable(getContext())); }

    public void setTrackTintList(ColorStateList tint) {
        mTrackTintList = tint;
        mHasTrackTint = true;
        applyTint(mTrackDrawable, true);
    }

    public ColorStateList getTrackTintList() { return mTrackTintList; }

    public void setTrackTintMode(PorterDuff.Mode tintMode) {
        mTrackTintMode = tintMode;
        mTrackBlendMode = null;
        mHasTrackTintMode = true;
        applyTint(mTrackDrawable, true);
    }

    public void setTrackTintBlendMode(BlendMode blendMode) {
        mTrackBlendMode = blendMode;
        mTrackTintMode = ImageView.modeOf(blendMode);
        mHasTrackTintMode = true;
        applyTint(mTrackDrawable, true);
    }

    public PorterDuff.Mode getTrackTintMode() { return mTrackTintMode; }

    public BlendMode getTrackTintBlendMode() { return mTrackBlendMode; }

    public void setThumbDrawable(Drawable thumb) { setDrawable(true, thumb); }

    public void setThumbResource(int resId) { setThumbDrawable(resId == 0 ? null : getContext().getDrawable(resId)); }

    public Drawable getThumbDrawable() { return mThumbDrawable; }

    public void setThumbIcon(Icon icon) { setThumbDrawable(icon == null ? null : icon.loadDrawable(getContext())); }

    public void setThumbTintList(ColorStateList tint) {
        mThumbTintList = tint;
        mHasThumbTint = true;
        applyTint(mThumbDrawable, false);
    }

    public ColorStateList getThumbTintList() { return mThumbTintList; }

    public void setThumbTintMode(PorterDuff.Mode tintMode) {
        mThumbTintMode = tintMode;
        mThumbBlendMode = null;
        mHasThumbTintMode = true;
        applyTint(mThumbDrawable, false);
    }

    public void setThumbTintBlendMode(BlendMode blendMode) {
        mThumbBlendMode = blendMode;
        mThumbTintMode = ImageView.modeOf(blendMode);
        mHasThumbTintMode = true;
        applyTint(mThumbDrawable, false);
    }

    public PorterDuff.Mode getThumbTintMode() { return mThumbTintMode; }

    public BlendMode getThumbTintBlendMode() { return mThumbBlendMode; }

    public void setSplitTrack(boolean splitTrack) {
        mSplitTrack = splitTrack;
        invalidate();
    }

    public boolean getSplitTrack() { return mSplitTrack; }

    public CharSequence getTextOn() { return mTextOn; }

    public void setTextOn(CharSequence textOn) {
        mTextOn = textOn == null ? "" : textOn;
        requestLayout();
    }

    public CharSequence getTextOff() { return mTextOff; }

    public void setTextOff(CharSequence textOff) {
        mTextOff = textOff == null ? "" : textOff;
        requestLayout();
    }

    public void setShowText(boolean showText) {
        if (mShowText == showText) return;
        mShowText = showText;
        requestLayout();
    }

    public boolean getShowText() { return mShowText; }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int thumbW = positive(mThumbDrawable == null ? -1 : mThumbDrawable.getIntrinsicWidth(), 20);
        int thumbH = positive(mThumbDrawable == null ? -1 : mThumbDrawable.getIntrinsicHeight(), 20);
        int trackH = positive(mTrackDrawable == null ? -1 : mTrackDrawable.getIntrinsicHeight(), 0);
        if (trackH <= 0) trackH = thumbH;
        int textW = 0;
        if (mShowText) {
            textW = Math.max(textWidth(mTextOn), textWidth(mTextOff)) + mThumbTextPadding * 2;
        }
        mThumbWidth = Math.max(thumbW, textW);
        mSwitchWidth = Math.max(mSwitchMinWidth, mThumbWidth * 2);
        mSwitchHeight = Math.max(trackH, thumbH);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getMeasuredHeight() < mSwitchHeight) setMeasuredDimension(getMeasuredWidthAndState(), mSwitchHeight);
    }

    @Override
    public int getCompoundPaddingLeft() {
        int padding = super.getCompoundPaddingLeft();
        if (isLayoutRtl()) padding += mSwitchWidth + mSwitchPadding;
        return padding;
    }

    @Override
    public int getCompoundPaddingRight() {
        int padding = super.getCompoundPaddingRight();
        if (!isLayoutRtl()) padding += mSwitchWidth + mSwitchPadding;
        return padding;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mSwitchWidth <= 0 || mSwitchHeight <= 0 || getWidth() <= 0) return;
        int top = getPaddingTop() + Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom() - mSwitchHeight) / 2;
        int left = isLayoutRtl() ? getPaddingLeft() : getWidth() - getPaddingRight() - mSwitchWidth;
        int right = left + mSwitchWidth;
        int bottom = top + mSwitchHeight;
        int travel = Math.max(0, mSwitchWidth - mThumbWidth);
        int thumbPos = isChecked() ? travel : 0;
        if (isLayoutRtl()) thumbPos = travel - thumbPos;
        int thumbLeft = left + thumbPos;
        if (mSplitTrack) {
            canvas.save();
            canvas.clipOutRect(thumbLeft, top, thumbLeft + mThumbWidth, bottom);
        }
        if (mTrackDrawable != null) {
            mTrackDrawable.setBounds(left, top, right, bottom);
            mTrackDrawable.draw(canvas);
        } else {
            Paint paint = fallbackPaint();
            paint.setColor(isChecked() ? 0xFF4CAF50 : 0xFFBDBDBD);
            float radius = mSwitchHeight / 2f;
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
        }
        if (mSplitTrack) canvas.restore();
        int thumbTop = top;
        int thumbBottom = bottom;
        if (mThumbDrawable != null && mThumbDrawable.getIntrinsicHeight() > 0) {
            int th = mThumbDrawable.getIntrinsicHeight();
            thumbTop = top + (mSwitchHeight - th) / 2;
            thumbBottom = thumbTop + th;
        }
        if (mThumbDrawable != null) {
            mThumbDrawable.setBounds(thumbLeft, thumbTop, thumbLeft + mThumbWidth, thumbBottom);
            mThumbDrawable.draw(canvas);
        } else {
            Paint paint = fallbackPaint();
            paint.setColor(0xFFFFFFFF);
            float radius = (thumbBottom - thumbTop) / 2f;
            canvas.drawRoundRect(thumbLeft, thumbTop, thumbLeft + mThumbWidth, thumbBottom, radius, radius, paint);
        }
        if (mShowText) {
            CharSequence label = isChecked() ? mTextOn : mTextOff;
            String text = label == null ? "" : label.toString();
            mTextPaint.setColor(mTextColor);
            float tw = mTextPaint.measureText(text);
            float x = thumbLeft + (mThumbWidth - tw) / 2f;
            float y = top + mSwitchHeight / 2f - (mTextPaint.ascent() + mTextPaint.descent()) / 2f;
            canvas.drawText(text, x, y, mTextPaint);
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        int[] state = getDrawableState();
        boolean changed = false;
        if (mThumbDrawable != null && mThumbDrawable.isStateful()) changed |= mThumbDrawable.setState(state);
        if (mTrackDrawable != null && mTrackDrawable.isStateful()) changed |= mTrackDrawable.setState(state);
        if (changed) invalidate();
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);
        if (mThumbDrawable != null) mThumbDrawable.setHotspot(x, y);
        if (mTrackDrawable != null) mTrackDrawable.setHotspot(x, y);
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == mThumbDrawable || who == mTrackDrawable;
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mThumbDrawable != null) mThumbDrawable.jumpToCurrentState();
        if (mTrackDrawable != null) mTrackDrawable.jumpToCurrentState();
    }

    @Override
    public CharSequence getAccessibilityClassName() { return Switch.class.getName(); }

    private void setDrawable(boolean thumb, Drawable drawable) {
        Drawable old = thumb ? mThumbDrawable : mTrackDrawable;
        if (old == drawable) return;
        if (old != null) {
            old.setCallback(null);
            unscheduleDrawable(old);
        }
        if (thumb) mThumbDrawable = drawable;
        else mTrackDrawable = drawable;
        if (drawable != null) {
            drawable.setCallback(this);
            drawable.setLayoutDirection(getLayoutDirection());
            if (drawable.isStateful()) drawable.setState(getDrawableState());
            drawable.setVisible(getVisibility() == VISIBLE, false);
            applyTint(drawable, !thumb);
        }
        requestLayout();
        invalidate();
    }

    private void applyTint(Drawable drawable, boolean track) {
        if (drawable == null) return;
        boolean has = track ? (mHasTrackTint || mHasTrackTintMode) : (mHasThumbTint || mHasThumbTintMode);
        if (!has) return;
        drawable.mutate();
        if (track) {
            if (mHasTrackTint) drawable.setTintList(mTrackTintList);
            if (mHasTrackTintMode) {
                drawable.setTintMode(mTrackTintMode);
                if (mTrackBlendMode != null) drawable.setTintBlendMode(mTrackBlendMode);
            }
        } else {
            if (mHasThumbTint) drawable.setTintList(mThumbTintList);
            if (mHasThumbTintMode) {
                drawable.setTintMode(mThumbTintMode);
                if (mThumbBlendMode != null) drawable.setTintBlendMode(mThumbBlendMode);
            }
        }
        if (drawable.isStateful()) drawable.setState(getDrawableState());
    }

    private int positive(int intrinsic, int fallbackDp) {
        if (intrinsic > 0) return intrinsic;
        if (fallbackDp <= 0) return 0;
        return (int) (fallbackDp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int textWidth(CharSequence text) {
        if (text == null || text.length() == 0) return 0;
        return (int) Math.ceil(mTextPaint.measureText(text, 0, text.length()));
    }

    private Paint fallbackPaint() {
        if (mFallbackPaint == null) mFallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        return mFallbackPaint;
    }
}
