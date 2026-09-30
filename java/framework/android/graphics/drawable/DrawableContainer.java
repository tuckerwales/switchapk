package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import java.util.ArrayList;

public class DrawableContainer extends Drawable implements Drawable.Callback {
    private DrawableContainerState mDrawableContainerState;
    private Drawable mCurrDrawable;
    private int mAlpha = 0xFF;
    private boolean mHasAlpha;
    private int mCurIndex = -1;
    private boolean mMutated;
    private ColorFilter mColorFilter;
    private ColorStateList mTintList;
    private PorterDuff.Mode mTintMode;

    @Override
    public void draw(Canvas canvas) { if (mCurrDrawable != null) mCurrDrawable.draw(canvas); }

    @Override
    public int getChangingConfigurations() { return super.getChangingConfigurations(); }

    private boolean needsMirroring() { return false; }

    @Override
    public boolean getPadding(Rect padding) {
        final Rect r = mDrawableContainerState.getConstantPadding();
        boolean result;
        if (r != null) {
            padding.set(r);
            result = (r.left | r.top | r.bottom | r.right) != 0;
        } else {
            if (mCurrDrawable != null) result = mCurrDrawable.getPadding(padding);
            else result = super.getPadding(padding);
        }
        return result;
    }

    @Override
    public Insets getOpticalInsets() { return mCurrDrawable != null ? mCurrDrawable.getOpticalInsets() : Insets.NONE; }

    @Override
    public void getOutline(Outline outline) { if (mCurrDrawable != null) mCurrDrawable.getOutline(outline); }

    @Override
    public void setAlpha(int alpha) {
        if (!mHasAlpha || mAlpha != alpha) {
            mHasAlpha = true;
            mAlpha = alpha;
            if (mCurrDrawable != null) mCurrDrawable.setAlpha(alpha);
        }
    }

    @Override
    public int getAlpha() { return mAlpha; }

    @Override
    public void setDither(boolean dither) {}

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mColorFilter = colorFilter;
        if (mCurrDrawable != null) mCurrDrawable.setColorFilter(colorFilter);
    }

    @Override
    public ColorFilter getColorFilter() { return mColorFilter; }

    @Override
    public void setTintList(ColorStateList tint) {
        mTintList = tint;
        if (mCurrDrawable != null) mCurrDrawable.setTintList(tint);
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        mTintMode = tintMode;
        if (mCurrDrawable != null) mCurrDrawable.setTintMode(tintMode);
    }

    public void setEnterFadeDuration(int ms) {}
    public void setExitFadeDuration(int ms) {}

    @Override
    protected void onBoundsChange(Rect bounds) { if (mCurrDrawable != null) mCurrDrawable.setBounds(bounds); }

    @Override
    public boolean isStateful() { return mDrawableContainerState.isStateful(); }

    @Override
    public boolean hasFocusStateSpecified() { return mCurrDrawable != null && mCurrDrawable.hasFocusStateSpecified(); }

    @Override
    public void setAutoMirrored(boolean mirrored) {
        mDrawableContainerState.mAutoMirrored = mirrored;
        if (mCurrDrawable != null) mCurrDrawable.setAutoMirrored(mirrored);
    }

    @Override
    public boolean isAutoMirrored() { return mDrawableContainerState.mAutoMirrored; }

    @Override
    public void jumpToCurrentState() { if (mCurrDrawable != null) mCurrDrawable.jumpToCurrentState(); }

    @Override
    public void setHotspot(float x, float y) { if (mCurrDrawable != null) mCurrDrawable.setHotspot(x, y); }

    @Override
    public void setHotspotBounds(int left, int top, int right, int bottom) {
        if (mCurrDrawable != null) mCurrDrawable.setHotspotBounds(left, top, right, bottom);
    }

    @Override
    protected boolean onStateChange(int[] state) {
        if (mCurrDrawable != null) return mCurrDrawable.setState(state);
        return false;
    }

    @Override
    protected boolean onLevelChange(int level) {
        if (mCurrDrawable != null) return mCurrDrawable.setLevel(level);
        return false;
    }

    @Override
    public boolean onLayoutDirectionChanged(int layoutDirection) {
        return mDrawableContainerState.setLayoutDirection(layoutDirection, getCurrentIndex());
    }

    @Override
    public int getIntrinsicWidth() {
        if (mDrawableContainerState.isConstantSize()) return mDrawableContainerState.getConstantWidth();
        return mCurrDrawable != null ? mCurrDrawable.getIntrinsicWidth() : -1;
    }

    @Override
    public int getIntrinsicHeight() {
        if (mDrawableContainerState.isConstantSize()) return mDrawableContainerState.getConstantHeight();
        return mCurrDrawable != null ? mCurrDrawable.getIntrinsicHeight() : -1;
    }

    @Override
    public int getMinimumWidth() {
        if (mDrawableContainerState.isConstantSize()) return mDrawableContainerState.getConstantMinimumWidth();
        return mCurrDrawable != null ? mCurrDrawable.getMinimumWidth() : 0;
    }

    @Override
    public int getMinimumHeight() {
        if (mDrawableContainerState.isConstantSize()) return mDrawableContainerState.getConstantMinimumHeight();
        return mCurrDrawable != null ? mCurrDrawable.getMinimumHeight() : 0;
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        if (who == mCurrDrawable && getCallback() != null) getCallback().invalidateDrawable(this);
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        if (who == mCurrDrawable && getCallback() != null) getCallback().scheduleDrawable(this, what, when);
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        if (who == mCurrDrawable && getCallback() != null) getCallback().unscheduleDrawable(this, what);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (mCurrDrawable != null) mCurrDrawable.setVisible(visible, restart);
        return changed;
    }

    @Override
    public int getOpacity() {
        return mCurrDrawable == null || !mCurrDrawable.isVisible() ? PixelFormat.TRANSPARENT : mDrawableContainerState.getOpacity();
    }

    public int getCurrentIndex() { return mCurIndex; }

    public boolean selectDrawable(int index) {
        if (index == mCurIndex) return false;
        if (mCurrDrawable != null) mCurrDrawable.setVisible(false, false);
        if (index >= 0 && index < mDrawableContainerState.mNumChildren) {
            final Drawable d = mDrawableContainerState.getChild(index);
            mCurrDrawable = d;
            mCurIndex = index;
            if (d != null) initializeDrawableForDisplay(d);
        } else {
            mCurrDrawable = null;
            mCurIndex = -1;
        }
        invalidateSelf();
        return true;
    }

    private void initializeDrawableForDisplay(Drawable d) {
        d.mutate();
        if (mHasAlpha) d.setAlpha(mAlpha);
        if (mColorFilter != null) d.setColorFilter(mColorFilter);
        if (mTintList != null) d.setTintList(mTintList);
        if (mTintMode != null) d.setTintMode(mTintMode);
        d.setVisible(isVisible(), true);
        d.setState(getState());
        d.setLevel(getLevel());
        d.setBounds(getBounds());
        d.setLayoutDirection(getLayoutDirection());
        d.setAutoMirrored(mDrawableContainerState.mAutoMirrored);
        d.setCallback(this);
    }

    @Override
    public Drawable getCurrent() { return mCurrDrawable; }

    @Override
    public void applyTheme(Resources.Theme theme) {}

    @Override
    public ConstantState getConstantState() { return mDrawableContainerState; }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mDrawableContainerState.mutate();
            mMutated = true;
        }
        return this;
    }

    public abstract static class DrawableContainerState extends ConstantState {
        final DrawableContainer mOwner;
        Resources mSourceRes;
        int mDensity = 160;
        int mChangingConfigurations;
        Drawable[] mDrawables;
        int mNumChildren;
        boolean mVariablePadding = false;
        Rect mConstantPadding;
        boolean mConstantSize = false;
        boolean mComputedConstantSize;
        int mConstantWidth, mConstantHeight, mConstantMinimumWidth, mConstantMinimumHeight;
        int mLayoutDirection;
        boolean mMutated;
        boolean mAutoMirrored;

        protected DrawableContainerState(DrawableContainerState orig, DrawableContainer owner, Resources res) {
            mOwner = owner;
            mSourceRes = res;
            if (orig != null) {
                mChangingConfigurations = orig.mChangingConfigurations;
                mVariablePadding = orig.mVariablePadding;
                mConstantSize = orig.mConstantSize;
                mAutoMirrored = orig.mAutoMirrored;
                mNumChildren = orig.mNumChildren;
                mDrawables = new Drawable[Math.max(10, orig.mDrawables.length)];
                for (int i = 0; i < mNumChildren; i++) {
                    Drawable d = orig.mDrawables[i];
                    if (d != null) {
                        Drawable.ConstantState cs = d.getConstantState();
                        mDrawables[i] = cs != null ? cs.newDrawable(res) : d;
                        if (mDrawables[i] != null) mDrawables[i].setCallback(owner);
                    }
                }
            } else {
                mDrawables = new Drawable[10];
                mNumChildren = 0;
            }
        }

        @Override
        public int getChangingConfigurations() { return mChangingConfigurations; }

        public final int addChild(Drawable dr) {
            final int pos = mNumChildren;
            if (pos >= mDrawables.length) growArray(pos, pos + 10);
            dr.mutate();
            dr.setVisible(false, true);
            dr.setCallback(mOwner);
            mDrawables[pos] = dr;
            mNumChildren++;
            mComputedConstantSize = false;
            mConstantPadding = null;
            return pos;
        }

        public void growArray(int oldSize, int newSize) {
            Drawable[] newDrawables = new Drawable[newSize];
            System.arraycopy(mDrawables, 0, newDrawables, 0, oldSize);
            mDrawables = newDrawables;
        }

        final int getCapacity() { return mDrawables.length; }
        public final int getChildCount() { return mNumChildren; }
        public final Drawable[] getChildren() { return mDrawables; }
        public final Drawable getChild(int index) { return mDrawables[index]; }

        final boolean setLayoutDirection(int layoutDirection, int currentIndex) {
            boolean changed = false;
            for (int i = 0; i < mNumChildren; i++) {
                if (mDrawables[i] != null) {
                    final boolean childChanged = mDrawables[i].setLayoutDirection(layoutDirection);
                    if (i == currentIndex) changed = childChanged;
                }
            }
            mLayoutDirection = layoutDirection;
            return changed;
        }

        void mutate() {
            for (int i = 0; i < mNumChildren; i++) if (mDrawables[i] != null) mDrawables[i].mutate();
            mMutated = true;
        }

        public final void setVariablePadding(boolean variable) { mVariablePadding = variable; }

        public final Rect getConstantPadding() {
            if (mVariablePadding) return null;
            if (mConstantPadding != null) return mConstantPadding;
            final Rect r = new Rect(0, 0, 0, 0);
            final Rect t = new Rect();
            for (int i = 0; i < mNumChildren; i++) {
                if (mDrawables[i] != null && mDrawables[i].getPadding(t)) {
                    if (t.left > r.left) r.left = t.left;
                    if (t.top > r.top) r.top = t.top;
                    if (t.right > r.right) r.right = t.right;
                    if (t.bottom > r.bottom) r.bottom = t.bottom;
                }
            }
            return (mConstantPadding = r);
        }

        public final void setConstantSize(boolean constant) { mConstantSize = constant; }
        public final boolean isConstantSize() { return mConstantSize; }

        public final int getConstantWidth() {
            if (!mComputedConstantSize) computeConstantSize();
            return mConstantWidth;
        }

        public final int getConstantHeight() {
            if (!mComputedConstantSize) computeConstantSize();
            return mConstantHeight;
        }

        public final int getConstantMinimumWidth() {
            if (!mComputedConstantSize) computeConstantSize();
            return mConstantMinimumWidth;
        }

        public final int getConstantMinimumHeight() {
            if (!mComputedConstantSize) computeConstantSize();
            return mConstantMinimumHeight;
        }

        protected void computeConstantSize() {
            mComputedConstantSize = true;
            mConstantWidth = mConstantHeight = -1;
            mConstantMinimumWidth = mConstantMinimumHeight = 0;
            for (int i = 0; i < mNumChildren; i++) {
                final Drawable dr = mDrawables[i];
                if (dr == null) continue;
                int s = dr.getIntrinsicWidth();
                if (s > mConstantWidth) mConstantWidth = s;
                s = dr.getIntrinsicHeight();
                if (s > mConstantHeight) mConstantHeight = s;
                s = dr.getMinimumWidth();
                if (s > mConstantMinimumWidth) mConstantMinimumWidth = s;
                s = dr.getMinimumHeight();
                if (s > mConstantMinimumHeight) mConstantMinimumHeight = s;
            }
        }

        public final void setEnterFadeDuration(int duration) {}
        public final int getEnterFadeDuration() { return 0; }
        public final void setExitFadeDuration(int duration) {}
        public final int getExitFadeDuration() { return 0; }

        public final int getOpacity() {
            int op = mNumChildren > 0 && mDrawables[0] != null ? mDrawables[0].getOpacity() : PixelFormat.TRANSPARENT;
            for (int i = 1; i < mNumChildren; i++) if (mDrawables[i] != null) op = Drawable.resolveOpacity(op, mDrawables[i].getOpacity());
            return op;
        }

        public final boolean isStateful() {
            for (int i = 0; i < mNumChildren; i++) if (mDrawables[i] != null && mDrawables[i].isStateful()) return true;
            return false;
        }

        public synchronized boolean canConstantState() {
            for (int i = 0; i < mNumChildren; i++) if (mDrawables[i] != null && mDrawables[i].getConstantState() == null) return false;
            return true;
        }
    }

    protected void setConstantState(DrawableContainerState state) {
        mDrawableContainerState = state;
        if (mCurIndex >= 0) {
            mCurrDrawable = state.getChild(mCurIndex);
            if (mCurrDrawable != null) initializeDrawableForDisplay(mCurrDrawable);
        }
    }
}
