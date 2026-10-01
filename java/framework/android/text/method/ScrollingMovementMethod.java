package android.text.method;

import android.text.Layout;
import android.text.Spannable;
import android.view.MotionEvent;
import android.widget.TextView;

/** Scrolls a TextView with the D-pad and drags (AOSP ScrollingMovementMethod). */
public class ScrollingMovementMethod extends BaseMovementMethod implements MovementMethod {
    private static ScrollingMovementMethod sInstance;
    private float mLastY;

    public ScrollingMovementMethod() {}

    public static MovementMethod getInstance() {
        if (sInstance == null) sInstance = new ScrollingMovementMethod();
        return sInstance;
    }

    @Override
    protected boolean up(TextView widget, Spannable buffer) { return scrollBy(widget, -widget.getLineHeight()); }

    @Override
    protected boolean down(TextView widget, Spannable buffer) { return scrollBy(widget, widget.getLineHeight()); }

    @Override
    protected boolean pageUp(TextView widget, Spannable buffer) {
        return scrollBy(widget, -widget.getHeight());
    }

    @Override
    protected boolean pageDown(TextView widget, Spannable buffer) {
        return scrollBy(widget, widget.getHeight());
    }

    @Override
    protected boolean top(TextView widget, Spannable buffer) {
        widget.scrollTo(widget.getScrollX(), 0);
        return true;
    }

    @Override
    protected boolean bottom(TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();
        int y = layout == null ? 0 : Math.max(0, layout.getHeight() - widget.getHeight());
        widget.scrollTo(widget.getScrollX(), y);
        return true;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mLastY = event.getY();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            float y = event.getY();
            scrollBy(widget, (int) (mLastY - y));
            mLastY = y;
            return true;
        }
        return action == MotionEvent.ACTION_UP;
    }

    private static boolean scrollBy(TextView widget, int dy) {
        Layout layout = widget.getLayout();
        int max = layout == null ? 0 : layout.getHeight() - (widget.getHeight() - widget.getTotalPaddingTop()
                - widget.getTotalPaddingBottom());
        if (max < 0) max = 0;
        int y = widget.getScrollY() + dy;
        if (y < 0) y = 0;
        if (y > max) y = max;
        widget.scrollTo(widget.getScrollX(), y);
        return true;
    }
}
