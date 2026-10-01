package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.PointerIcon;

/** An image that acts as a button (AOSP ImageButton). */
public class ImageButton extends ImageView {
    public ImageButton(Context context) { this(context, null); }

    public ImageButton(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.imageButtonStyle);
    }

    public ImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ImageButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setFocusable(true);
    }

    @Override
    protected boolean onSetAlpha(int alpha) { return false; }

    @Override
    public CharSequence getAccessibilityClassName() { return ImageButton.class.getName(); }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
        if (getPointerIcon() == null && isClickable() && isEnabled()) {
            return PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_HAND);
        }
        return super.onResolvePointerIcon(event, pointerIndex);
    }
}
