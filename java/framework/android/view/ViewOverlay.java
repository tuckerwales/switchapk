package android.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;

/** Extra layer drawn on top of a view after its content (AOSP ViewOverlay). */
public class ViewOverlay {
    OverlayViewGroup mOverlayViewGroup;

    ViewOverlay(Context context, View hostView) { mOverlayViewGroup = new OverlayViewGroup(context, hostView); }

    ViewGroup getOverlayView() { return mOverlayViewGroup; }

    public void add(Drawable drawable) { mOverlayViewGroup.add(drawable); }

    public void remove(Drawable drawable) { mOverlayViewGroup.remove(drawable); }

    public void clear() { mOverlayViewGroup.clear(); }

    boolean isEmpty() { return mOverlayViewGroup.isEmpty(); }

    /** The view group holding overlay drawables and views; sized like the host and drawn by it. */
    static class OverlayViewGroup extends ViewGroup {
        final View mHostView;
        ArrayList<Drawable> mDrawables;

        OverlayViewGroup(Context context, View hostView) {
            super(context);
            mHostView = hostView;
            mAttachInfo = mHostView.mAttachInfo;
            mRight = hostView.getWidth();
            mBottom = hostView.getHeight();
        }

        void add(Drawable drawable) {
            if (drawable == null) throw new IllegalArgumentException("drawable must be non-null");
            if (mDrawables == null) mDrawables = new ArrayList<Drawable>();
            if (!mDrawables.contains(drawable)) {
                mDrawables.add(drawable);
                invalidate(drawable.getBounds());
                drawable.setCallback(this);
            }
        }

        void remove(Drawable drawable) {
            if (drawable == null) throw new IllegalArgumentException("drawable must be non-null");
            if (mDrawables != null) {
                mDrawables.remove(drawable);
                invalidate(drawable.getBounds());
                drawable.setCallback(null);
            }
        }

        @Override
        protected boolean verifyDrawable(Drawable who) {
            return super.verifyDrawable(who) || (mDrawables != null && mDrawables.contains(who));
        }

        void add(View child) {
            if (child == null) throw new IllegalArgumentException("view must be non-null");
            if (child.getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) child.getParent();
                if (parent != mHostView && parent.getParent() != null && parent.mAttachInfo != null) {
                    int[] parentLocation = new int[2];
                    int[] hostViewLocation = new int[2];
                    parent.getLocationOnScreen(parentLocation);
                    mHostView.getLocationOnScreen(hostViewLocation);
                    child.offsetLeftAndRight(parentLocation[0] - hostViewLocation[0]);
                    child.offsetTopAndBottom(parentLocation[1] - hostViewLocation[1]);
                }
                parent.removeView(child);
            }
            super.addView(child);
        }

        void remove(View view) { super.removeView(view); }

        void clear() {
            removeAllViews();
            if (mDrawables != null) {
                for (Drawable drawable : mDrawables) drawable.setCallback(null);
                mDrawables.clear();
            }
        }

        boolean isEmpty() { return getChildCount() == 0 && (mDrawables == null || mDrawables.size() == 0); }

        @Override
        public void invalidateDrawable(Drawable drawable) { invalidate(drawable.getBounds()); }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            mRight = mHostView.getWidth();
            mBottom = mHostView.getHeight();
            super.dispatchDraw(canvas);
            final int numDrawables = (mDrawables == null) ? 0 : mDrawables.size();
            for (int i = 0; i < numDrawables; ++i) mDrawables.get(i).draw(canvas);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {}

        @Override
        public void invalidate() {
            super.invalidate();
            if (mHostView != null) mHostView.invalidate();
        }

        @Override
        public void invalidate(boolean invalidateCache) {
            super.invalidate(invalidateCache);
            if (mHostView != null) mHostView.invalidate(invalidateCache);
        }

        @Override
        public ViewParent invalidateChildInParent(int[] location, Rect dirty) {
            if (mHostView != null) mHostView.invalidate(true);
            dirty.setEmpty();
            return null;
        }
    }
}
