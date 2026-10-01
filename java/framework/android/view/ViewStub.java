package android.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import java.lang.ref.WeakReference;

/** Invisible, zero-sized placeholder that inflates a layout on demand (AOSP ViewStub). */
public final class ViewStub extends View {
    private int mInflatedId;
    private int mLayoutResource;
    private WeakReference<View> mInflatedViewRef;
    private LayoutInflater mInflater;
    private OnInflateListener mInflateListener;

    private static final int[] STUB_ATTRS = {android.R.attr.inflatedId, android.R.attr.layout, android.R.attr.id};

    public ViewStub(Context context) { this(context, 0); }

    public ViewStub(Context context, int layoutResource) {
        this(context, (AttributeSet) null);
        mLayoutResource = layoutResource;
    }

    public ViewStub(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public ViewStub(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public ViewStub(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context);
        if (attrs != null) {
            final TypedArray a = context.obtainStyledAttributes(attrs, STUB_ATTRS, defStyleAttr, defStyleRes);
            mInflatedId = a.getResourceId(0, NO_ID);
            mLayoutResource = a.getResourceId(1, 0);
            mID = a.getResourceId(2, NO_ID);
            a.recycle();
        }
        setVisibility(GONE);
        setWillNotDraw(true);
    }

    public int getInflatedId() { return mInflatedId; }

    public void setInflatedId(int inflatedId) { mInflatedId = inflatedId; }

    public int getLayoutResource() { return mLayoutResource; }

    public void setLayoutResource(int layoutResource) { mLayoutResource = layoutResource; }

    public void setLayoutInflater(LayoutInflater inflater) { mInflater = inflater; }

    public LayoutInflater getLayoutInflater() { return mInflater; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) { setMeasuredDimension(0, 0); }

    @Override
    public void draw(Canvas canvas) {}

    @Override
    protected void dispatchDraw(Canvas canvas) {}

    @Override
    public void setVisibility(int visibility) {
        if (mInflatedViewRef != null) {
            View view = mInflatedViewRef.get();
            if (view != null) view.setVisibility(visibility);
            else throw new IllegalStateException("setVisibility called on un-referenced view");
        } else {
            super.setVisibility(visibility);
            if (visibility == VISIBLE || visibility == INVISIBLE) inflate();
        }
    }

    private View inflateViewNoAdd(ViewGroup parent) {
        final LayoutInflater factory = mInflater != null ? mInflater : LayoutInflater.from(mContext);
        final View view = factory.inflate(mLayoutResource, parent, false);
        if (mInflatedId != NO_ID) view.setId(mInflatedId);
        return view;
    }

    private void replaceSelfWithView(View view, ViewGroup parent) {
        final int index = parent.indexOfChild(this);
        parent.removeViewInLayout(this);
        final ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams != null) parent.addView(view, index, layoutParams);
        else parent.addView(view, index);
    }

    public View inflate() {
        final ViewParent viewParent = getParent();
        if (viewParent != null && viewParent instanceof ViewGroup) {
            if (mLayoutResource != 0) {
                final ViewGroup parent = (ViewGroup) viewParent;
                final View view = inflateViewNoAdd(parent);
                replaceSelfWithView(view, parent);
                mInflatedViewRef = new WeakReference<View>(view);
                if (mInflateListener != null) mInflateListener.onInflate(this, view);
                return view;
            }
            throw new IllegalArgumentException("ViewStub must have a valid layoutResource");
        }
        throw new IllegalStateException("ViewStub must have a non-null ViewGroup viewParent");
    }

    public void setOnInflateListener(OnInflateListener inflateListener) { mInflateListener = inflateListener; }

    public interface OnInflateListener {
        void onInflate(ViewStub stub, View inflated);
    }
}
