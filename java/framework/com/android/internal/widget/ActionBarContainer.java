package com.android.internal.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import com.android.internal.util.InternalRes;

/**
 * framework-internal. Port of AOSP ActionBarContainer: holds the action bar
 * (a Toolbar in the Material decor) and the action mode bar, and draws the
 * action bar background behind whichever is visible. Tabs are not ported.
 */
public class ActionBarContainer extends FrameLayout {
    private boolean mIsTransitioning;
    private View mTabContainer;
    private View mActionBarView;
    private View mActionContextView;
    private Drawable mBackground;
    private Drawable mStackedBackground;
    private Drawable mSplitBackground;
    private boolean mIsSplit;
    private boolean mIsStacked;
    private int mHeight;

    public ActionBarContainer(Context context) { this(context, null); }

    public ActionBarContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackground(new ActionBarBackgroundDrawable());
        final TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.background, android.R.attr.backgroundStacked, android.R.attr.height,
                android.R.attr.backgroundSplit});
        mBackground = a.getDrawable(0);
        mStackedBackground = a.getDrawable(1);
        mHeight = a.getDimensionPixelSize(2, -1);
        if (getId() != NO_ID && getId() == InternalRes.viewId("split_action_bar")) {
            mIsSplit = true;
            mSplitBackground = a.getDrawable(3);
        }
        a.recycle();
        setWillNotDraw(mIsSplit ? mSplitBackground == null : mBackground == null && mStackedBackground == null);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mActionBarView = findViewById(InternalRes.viewId("action_bar"));
        mActionContextView = findViewById(InternalRes.viewId("action_context_bar"));
    }

    public void setPrimaryBackground(Drawable bg) {
        if (mBackground != null) {
            mBackground.setCallback(null);
            unscheduleDrawable(mBackground);
        }
        mBackground = bg;
        if (bg != null) {
            bg.setCallback(this);
            if (mActionBarView != null) {
                mBackground.setBounds(mActionBarView.getLeft(), mActionBarView.getTop(), mActionBarView.getRight(),
                        mActionBarView.getBottom());
            }
        }
        setWillNotDraw(mIsSplit ? mSplitBackground == null : mBackground == null && mStackedBackground == null);
        invalidate();
    }

    public void setStackedBackground(Drawable bg) {
        if (mStackedBackground != null) {
            mStackedBackground.setCallback(null);
            unscheduleDrawable(mStackedBackground);
        }
        mStackedBackground = bg;
        if (bg != null) {
            bg.setCallback(this);
            if (mIsStacked && mTabContainer != null) {
                mStackedBackground.setBounds(mTabContainer.getLeft(), mTabContainer.getTop(), mTabContainer.getRight(),
                        mTabContainer.getBottom());
            }
        }
        setWillNotDraw(mIsSplit ? mSplitBackground == null : mBackground == null && mStackedBackground == null);
        invalidate();
    }

    public void setSplitBackground(Drawable bg) {
        if (mSplitBackground != null) {
            mSplitBackground.setCallback(null);
            unscheduleDrawable(mSplitBackground);
        }
        mSplitBackground = bg;
        if (bg != null) {
            bg.setCallback(this);
            if (mIsSplit) mSplitBackground.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }
        setWillNotDraw(mIsSplit ? mSplitBackground == null : mBackground == null && mStackedBackground == null);
        invalidate();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        final boolean isVisible = visibility == VISIBLE;
        if (mBackground != null) mBackground.setVisible(isVisible, false);
        if (mStackedBackground != null) mStackedBackground.setVisible(isVisible, false);
        if (mSplitBackground != null) mSplitBackground.setVisible(isVisible, false);
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return (who == mBackground && !mIsSplit) || (who == mStackedBackground && mIsStacked)
                || (who == mSplitBackground && mIsSplit) || super.verifyDrawable(who);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        final int[] state = getDrawableState();
        if (mBackground != null && mBackground.isStateful()) mBackground.setState(state);
        if (mStackedBackground != null && mStackedBackground.isStateful()) mStackedBackground.setState(state);
        if (mSplitBackground != null && mSplitBackground.isStateful()) mSplitBackground.setState(state);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mBackground != null) mBackground.jumpToCurrentState();
        if (mStackedBackground != null) mStackedBackground.jumpToCurrentState();
        if (mSplitBackground != null) mSplitBackground.jumpToCurrentState();
    }

    /** Blocks touches and focus to the bar while it animates in or out. */
    public void setTransitioning(boolean isTransitioning) {
        mIsTransitioning = isTransitioning;
        setDescendantFocusability(isTransitioning ? FOCUS_BLOCK_DESCENDANTS : FOCUS_AFTER_DESCENDANTS);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) { return mIsTransitioning || super.onInterceptTouchEvent(ev); }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        super.onTouchEvent(ev);
        // An action bar always eats touch events.
        return true;
    }

    @Override
    public boolean onHoverEvent(MotionEvent ev) {
        super.onHoverEvent(ev);
        return true;
    }

    public View getTabContainer() { return mTabContainer; }

    @Override
    public ActionMode startActionModeForChild(View child, ActionMode.Callback callback, int type) {
        if (type != ActionMode.TYPE_PRIMARY) return super.startActionModeForChild(child, callback, type);
        return null;
    }

    private static boolean isCollapsed(View view) {
        return view == null || view.getVisibility() == GONE || view.getMeasuredHeight() == 0;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mActionBarView == null && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST && mHeight >= 0) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                    Math.min(mHeight, MeasureSpec.getSize(heightMeasureSpec)), MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        boolean needsInvalidate = false;
        if (mIsSplit) {
            if (mSplitBackground != null) {
                mSplitBackground.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                needsInvalidate = true;
            }
        } else {
            if (mBackground != null) {
                if (mActionBarView != null && mActionBarView.getVisibility() == VISIBLE) {
                    mBackground.setBounds(mActionBarView.getLeft(), mActionBarView.getTop(), mActionBarView.getRight(),
                            mActionBarView.getBottom());
                } else if (mActionContextView != null && mActionContextView.getVisibility() == VISIBLE) {
                    mBackground.setBounds(mActionContextView.getLeft(), mActionContextView.getTop(),
                            mActionContextView.getRight(), mActionContextView.getBottom());
                } else {
                    mBackground.setBounds(0, 0, 0, 0);
                }
                needsInvalidate = true;
            }
            mIsStacked = false;
        }
        if (needsInvalidate) invalidate();
    }

    /** Draws the primary, stacked or split background (AOSP ActionBarBackgroundDrawable). */
    private class ActionBarBackgroundDrawable extends Drawable {
        @Override
        public void draw(Canvas canvas) {
            if (mIsSplit) {
                if (mSplitBackground != null) mSplitBackground.draw(canvas);
            } else {
                if (mBackground != null) mBackground.draw(canvas);
                if (mStackedBackground != null && mIsStacked) mStackedBackground.draw(canvas);
            }
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            if (mIsSplit) {
                if (mSplitBackground != null && mSplitBackground.getOpacity() == PixelFormat.OPAQUE) {
                    return PixelFormat.OPAQUE;
                }
            } else {
                if (mIsStacked && (mStackedBackground == null || mStackedBackground.getOpacity() != PixelFormat.OPAQUE)) {
                    return PixelFormat.UNKNOWN;
                }
                if (!isCollapsed(mActionBarView) && mBackground != null && mBackground.getOpacity() == PixelFormat.OPAQUE) {
                    return PixelFormat.OPAQUE;
                }
            }
            return PixelFormat.UNKNOWN;
        }
    }
}
