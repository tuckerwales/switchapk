package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Port of AOSP ViewSwitcher: a ViewAnimator with exactly two children, made by a factory. */
public class ViewSwitcher extends ViewAnimator {
    /** Creates the views to switch between. */
    public interface ViewFactory {
        View makeView();
    }

    ViewFactory mFactory;

    public ViewSwitcher(Context context) { super(context); }

    public ViewSwitcher(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() >= 2) throw new IllegalStateException("Can't add more than 2 views to a ViewSwitcher");
        super.addView(child, index, params);
    }

    @Override
    public CharSequence getAccessibilityClassName() { return ViewSwitcher.class.getName(); }

    public View getNextView() {
        int which = mWhichChild == 0 ? 1 : 0;
        return getChildAt(which);
    }

    private View obtainView() {
        View child = mFactory.makeView();
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (lp == null) lp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        addView(child, lp);
        return child;
    }

    public void setFactory(ViewFactory factory) {
        mFactory = factory;
        obtainView();
        obtainView();
    }

    public void reset() {
        mFirstTime = true;
        View v;
        v = getChildAt(0);
        if (v != null) v.setVisibility(View.GONE);
        v = getChildAt(1);
        if (v != null) v.setVisibility(View.GONE);
    }
}
