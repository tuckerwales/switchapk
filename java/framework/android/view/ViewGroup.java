package android.view;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import java.util.ArrayList;

public class ViewGroup extends View implements ViewParent {
    private final ArrayList<View> mChildren = new ArrayList<View>();

    public ViewGroup(Context context) { this(context, null, 0, 0); }
    public ViewGroup(Context context, AttributeSet attrs) { this(context, attrs, 0, 0); }
    public ViewGroup(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }
    public ViewGroup(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void addView(View child) { addView(child, -1); }
    public void addView(View child, int index) { addView(child, index, child.getLayoutParams()); }

    public void addView(View child, int width, int height) {
        addView(child, -1, new LayoutParams(width, height));
    }

    public void addView(View child, LayoutParams params) { addView(child, -1, params); }

    public void addView(View child, int index, LayoutParams params) {
        if (child == null) throw new IllegalArgumentException("Cannot add a null child view to a ViewGroup");
        if (child.mParent != null) throw new IllegalStateException("The specified child already has a parent.");
        if (params == null) params = generateDefaultLayoutParams();
        child.setLayoutParams(params);
        child.assignParent(this);
        if (index < 0 || index >= mChildren.size()) mChildren.add(child);
        else mChildren.add(index, child);
        requestLayout();
        invalidate();
    }

    public void updateViewLayout(View view, LayoutParams params) {
        view.setLayoutParams(params);
        requestLayout();
    }

    public void removeView(View view) { removeViewAt(indexOfChild(view)); }

    public void removeViewAt(int index) {
        if (index < 0 || index >= mChildren.size()) return;
        View child = mChildren.remove(index);
        child.assignParent(null);
        child.mViewRoot = null;
        requestLayout();
        invalidate();
    }

    public void removeAllViews() {
        for (int i = mChildren.size() - 1; i >= 0; i--) removeViewAt(i);
    }

    public int getChildCount() { return mChildren.size(); }
    public View getChildAt(int index) { return mChildren.get(index); }
    public int indexOfChild(View child) { return mChildren.indexOf(child); }

    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {}

    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    public LayoutParams generateLayoutParams(AttributeSet attrs) { return new LayoutParams(getContext(), attrs); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        for (int i = 0; i < mChildren.size(); i++) {
            View child = mChildren.get(i);
            if (child.getVisibility() == GONE) continue;
            LayoutParams lp = child.getLayoutParams();
            int cw = lp != null ? lp.width : LayoutParams.MATCH_PARENT;
            int ch = lp != null ? lp.height : LayoutParams.MATCH_PARENT;
            child.measure(getChildMeasureSpec(widthMeasureSpec, 0, cw), getChildMeasureSpec(heightMeasureSpec, 0, ch));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < mChildren.size(); i++) {
            View child = mChildren.get(i);
            if (child.getVisibility() == GONE) continue;
            child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        for (int i = 0; i < mChildren.size(); i++) {
            View child = mChildren.get(i);
            if (child.getVisibility() != VISIBLE) continue;
            int save = canvas.save();
            canvas.translate(child.mLeft, child.mTop);
            child.draw(canvas);
            canvas.restoreToCount(save);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        for (int i = mChildren.size() - 1; i >= 0; i--) {
            View child = mChildren.get(i);
            if (child.getVisibility() != VISIBLE) continue;
            if (x < child.mLeft || x >= child.mRight || y < child.mTop || y >= child.mBottom) continue;
            MotionEvent childEv = MotionEvent.obtain(event.getDownTime(), event.getEventTime(), event.getAction(),
                    x - child.mLeft, y - child.mTop, event.getMetaState());
            boolean handled = child.dispatchTouchEvent(childEv);
            childEv.recycle();
            if (handled) return true;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        for (int i = mChildren.size() - 1; i >= 0; i--) {
            if (mChildren.get(i).dispatchKeyEvent(event)) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    View findViewTraversal(int id) {
        if (id == mId && id != NO_ID) return this;
        for (int i = 0; i < mChildren.size(); i++) {
            View found = mChildren.get(i).findViewById(id);
            if (found != null) return found;
        }
        return null;
    }

    public static int getChildMeasureSpec(int spec, int padding, int childDimension) {
        int specMode = MeasureSpec.getMode(spec);
        int specSize = MeasureSpec.getSize(spec);
        int size = Math.max(0, specSize - padding);
        int resultSize = 0;
        int resultMode = 0;
        if (childDimension >= 0) {
            resultSize = childDimension;
            resultMode = MeasureSpec.EXACTLY;
        } else if (childDimension == LayoutParams.MATCH_PARENT) {
            resultSize = size;
            resultMode = specMode == MeasureSpec.EXACTLY ? MeasureSpec.EXACTLY
                    : specMode == MeasureSpec.AT_MOST ? MeasureSpec.AT_MOST : MeasureSpec.UNSPECIFIED;
        } else if (childDimension == LayoutParams.WRAP_CONTENT) {
            resultSize = size;
            resultMode = specMode == MeasureSpec.UNSPECIFIED ? MeasureSpec.UNSPECIFIED : MeasureSpec.AT_MOST;
        }
        return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
    }

    public static class LayoutParams {
        public static final int FILL_PARENT = -1;
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;
        public int width;
        public int height;

        public LayoutParams(Context c, AttributeSet attrs) {
            width = WRAP_CONTENT;
            height = WRAP_CONTENT;
        }

        public LayoutParams(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public LayoutParams(LayoutParams source) {
            this.width = source.width;
            this.height = source.height;
        }
    }
}
