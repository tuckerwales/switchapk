package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * A group of radio buttons where only one stays checked (AOSP RadioGroup).
 * The group's tracker uses a package-private widget listener so an app
 * listener on each button is left in place.
 */
public class RadioGroup extends LinearLayout {
    private static final int[] ATTRS = {android.R.attr.checkedButton, android.R.attr.orientation};

    private int mCheckedId = -1;
    private boolean mProtectFromCheckedChange;
    private OnCheckedChangeListener mOnCheckedChangeListener;
    private CompoundButton.OnCheckedChangeListener mChildOnCheckedChangeListener;
    private PassThroughHierarchyChangeListener mPassThroughListener;

    public interface OnCheckedChangeListener {
        void onCheckedChanged(RadioGroup group, int checkedId);
    }

    public RadioGroup(Context context) {
        super(context);
        setOrientation(VERTICAL);
        init();
    }

    public RadioGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
        int checked = View.NO_ID;
        int orientation = VERTICAL;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, ATTRS);
            checked = a.getResourceId(0, View.NO_ID);
            orientation = a.getInt(1, VERTICAL);
            a.recycle();
        }
        setOrientation(orientation);
        init();
        mCheckedId = checked;
    }

    private void init() {
        mChildOnCheckedChangeListener = new CheckedStateTracker();
        mPassThroughListener = new PassThroughHierarchyChangeListener();
        super.setOnHierarchyChangeListener(mPassThroughListener);
    }

    @Override
    public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) {
        mPassThroughListener.mOnHierarchyChangeListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (mCheckedId != -1) {
            mProtectFromCheckedChange = true;
            setCheckedStateForView(mCheckedId, true);
            mProtectFromCheckedChange = false;
            setCheckedId(mCheckedId);
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (child instanceof RadioButton) {
            RadioButton button = (RadioButton) child;
            if (button.isChecked()) {
                mProtectFromCheckedChange = true;
                if (mCheckedId != -1) setCheckedStateForView(mCheckedId, false);
                mProtectFromCheckedChange = false;
                setCheckedId(button.getId());
            }
        }
        super.addView(child, index, params);
    }

    public void check(int id) {
        if (id != -1 && id == mCheckedId) return;
        if (mCheckedId != -1) setCheckedStateForView(mCheckedId, false);
        if (id != -1) setCheckedStateForView(id, true);
        setCheckedId(id);
    }

    public int getCheckedRadioButtonId() { return mCheckedId; }

    public void clearCheck() { check(-1); }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) { mOnCheckedChangeListener = listener; }

    private void setCheckedId(int id) {
        mCheckedId = id;
        if (mOnCheckedChangeListener != null) mOnCheckedChangeListener.onCheckedChanged(this, mCheckedId);
    }

    private void setCheckedStateForView(int viewId, boolean checked) {
        View checkedView = findViewById(viewId);
        if (checkedView instanceof RadioButton) ((RadioButton) checkedView).setChecked(checked);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) { return new LayoutParams(getContext(), attrs); }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) { return p instanceof RadioGroup.LayoutParams; }

    @Override
    protected LinearLayout.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public CharSequence getAccessibilityClassName() { return RadioGroup.class.getName(); }

    public static class LayoutParams extends LinearLayout.LayoutParams {
        public LayoutParams(Context c, AttributeSet attrs) { super(c, attrs); }

        public LayoutParams(int w, int h) { super(w, h); }

        public LayoutParams(int w, int h, float initWeight) { super(w, h, initWeight); }

        public LayoutParams(ViewGroup.LayoutParams p) { super(p); }

        public LayoutParams(ViewGroup.MarginLayoutParams source) { super(source); }

        @Override
        protected void setBaseAttributes(TypedArray a, int widthAttr, int heightAttr) {
            if (a.hasValue(widthAttr)) width = a.getLayoutDimension(widthAttr, "layout_width");
            else width = WRAP_CONTENT;
            if (a.hasValue(heightAttr)) height = a.getLayoutDimension(heightAttr, "layout_height");
            else height = WRAP_CONTENT;
        }
    }

    private class CheckedStateTracker implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (mProtectFromCheckedChange) return;
            if (!isChecked) {
                if (mCheckedId == buttonView.getId()) setCheckedId(-1);
                return;
            }
            mProtectFromCheckedChange = true;
            if (mCheckedId != -1) setCheckedStateForView(mCheckedId, false);
            mProtectFromCheckedChange = false;
            setCheckedId(buttonView.getId());
        }
    }

    private class PassThroughHierarchyChangeListener implements OnHierarchyChangeListener {
        private OnHierarchyChangeListener mOnHierarchyChangeListener;

        @Override
        public void onChildViewAdded(View parent, View child) {
            if (parent == RadioGroup.this && child instanceof RadioButton) {
                if (child.getId() == View.NO_ID) child.setId(View.generateViewId());
                ((RadioButton) child).setOnCheckedChangeWidgetListener(mChildOnCheckedChangeListener);
            }
            if (mOnHierarchyChangeListener != null) mOnHierarchyChangeListener.onChildViewAdded(parent, child);
        }

        @Override
        public void onChildViewRemoved(View parent, View child) {
            if (parent == RadioGroup.this && child instanceof RadioButton) {
                ((RadioButton) child).setOnCheckedChangeWidgetListener(null);
            }
            if (mOnHierarchyChangeListener != null) mOnHierarchyChangeListener.onChildViewRemoved(parent, child);
        }
    }
}
