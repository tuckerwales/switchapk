package android.text.method;

import android.text.Spannable;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

/** Arrow, page and home/end keys (AOSP BaseMovementMethod). Subclasses move the cursor. */
public class BaseMovementMethod implements MovementMethod {
    public BaseMovementMethod() {}

    public boolean canSelectArbitrarily() { return false; }

    public void initialize(TextView widget, Spannable text) {}

    public boolean onKeyDown(TextView widget, Spannable text, int keyCode, KeyEvent event) {
        boolean handled = handleMovementKey(widget, text, keyCode, getMovementMetaState(text, event), event);
        if (handled) MetaKeyKeyListener.adjustMetaAfterKeypress(text);
        return handled;
    }

    public boolean onKeyOther(TextView widget, Spannable text, KeyEvent event) { return false; }

    public boolean onKeyUp(TextView widget, Spannable text, int keyCode, KeyEvent event) { return false; }

    public void onTakeFocus(TextView widget, Spannable text, int direction) {}

    public boolean onTouchEvent(TextView widget, Spannable text, MotionEvent event) { return false; }

    public boolean onTrackballEvent(TextView widget, Spannable text, MotionEvent event) { return false; }

    public boolean onGenericMotionEvent(TextView widget, Spannable text, MotionEvent event) { return false; }

    protected int getMovementMetaState(Spannable buffer, KeyEvent event) {
        return event == null ? 0 : event.getMetaState();
    }

    protected boolean handleMovementKey(TextView widget, Spannable buffer, int keyCode, int movementMetaState,
            KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return left(widget, buffer);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return right(widget, buffer);
            case KeyEvent.KEYCODE_DPAD_UP:
                return up(widget, buffer);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return down(widget, buffer);
            case KeyEvent.KEYCODE_PAGE_UP:
                return pageUp(widget, buffer);
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return pageDown(widget, buffer);
            case KeyEvent.KEYCODE_MOVE_HOME:
                return home(widget, buffer);
            case KeyEvent.KEYCODE_MOVE_END:
                return end(widget, buffer);
            default:
                return false;
        }
    }

    protected boolean left(TextView widget, Spannable buffer) { return false; }

    protected boolean right(TextView widget, Spannable buffer) { return false; }

    protected boolean up(TextView widget, Spannable buffer) { return false; }

    protected boolean down(TextView widget, Spannable buffer) { return false; }

    protected boolean pageUp(TextView widget, Spannable buffer) { return false; }

    protected boolean pageDown(TextView widget, Spannable buffer) { return false; }

    protected boolean top(TextView widget, Spannable buffer) { return false; }

    protected boolean bottom(TextView widget, Spannable buffer) { return false; }

    protected boolean lineStart(TextView widget, Spannable buffer) { return false; }

    protected boolean lineEnd(TextView widget, Spannable buffer) { return false; }

    protected boolean home(TextView widget, Spannable buffer) { return top(widget, buffer); }

    protected boolean end(TextView widget, Spannable buffer) { return bottom(widget, buffer); }

    public boolean previousParagraph(TextView widget, Spannable buffer) { return false; }

    public boolean nextParagraph(TextView widget, Spannable buffer) { return false; }
}
