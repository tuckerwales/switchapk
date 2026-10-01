package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.PointerIcon;

/** A clickable text button (AOSP Button). The theme's {@code buttonStyle} supplies the background. */
public class Button extends TextView {
    public Button(Context context) { this(context, null); }

    public Button(Context context, AttributeSet attrs) { this(context, attrs, android.R.attr.buttonStyle); }

    public Button(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public Button(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public CharSequence getAccessibilityClassName() { return Button.class.getName(); }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
        if (getPointerIcon() == null && isClickable() && isEnabled()) {
            return PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_HAND);
        }
        return super.onResolvePointerIcon(event, pointerIndex);
    }
}
