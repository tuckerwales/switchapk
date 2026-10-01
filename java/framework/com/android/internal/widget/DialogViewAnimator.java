package com.android.internal.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ViewAnimator;

/**
 * framework-internal. AOSP's DialogViewAnimator: the ViewAnimator the Material date picker switches
 * between its day and year views with. It measures all children so switching does not resize it.
 */
public class DialogViewAnimator extends ViewAnimator {
    public DialogViewAnimator(Context context) { super(context); }

    public DialogViewAnimator(Context context, AttributeSet attrs) { super(context, attrs); }
}
