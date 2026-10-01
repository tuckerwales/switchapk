package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Port of AOSP TextSwitcher: a ViewSwitcher of two TextViews. */
public class TextSwitcher extends ViewSwitcher {
    public TextSwitcher(Context context) { super(context); }

    public TextSwitcher(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (!(child instanceof TextView)) {
            throw new IllegalArgumentException("TextSwitcher children must be instances of TextView");
        }
        super.addView(child, index, params);
    }

    /** Sets the text of the next view and switches to it. */
    public void setText(CharSequence text) {
        final TextView t = (TextView) getNextView();
        t.setText(text);
        showNext();
    }

    /** Sets the text of the shown view without switching. */
    public void setCurrentText(CharSequence text) { ((TextView) getCurrentView()).setText(text); }

    @Override
    public CharSequence getAccessibilityClassName() { return TextSwitcher.class.getName(); }
}
