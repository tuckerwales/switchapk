package android.text.method;

import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

/** Moves and extends the cursor with the D-pad (AOSP ArrowKeyMovementMethod). */
public class ArrowKeyMovementMethod extends BaseMovementMethod implements MovementMethod {
    private static ArrowKeyMovementMethod sInstance;

    public ArrowKeyMovementMethod() {}

    public static MovementMethod getInstance() {
        if (sInstance == null) sInstance = new ArrowKeyMovementMethod();
        return sInstance;
    }

    @Override
    public boolean canSelectArbitrarily() { return true; }

    @Override
    public void initialize(TextView widget, Spannable text) { Selection.setSelection(text, 0); }

    @Override
    public void onTakeFocus(TextView widget, Spannable text, int direction) {
        if (Selection.getSelectionStart(text) < 0) Selection.setSelection(text, text.length());
    }

    @Override
    protected boolean handleMovementKey(TextView widget, Spannable buffer, int keyCode, int movementMetaState,
            KeyEvent event) {
        Layout layout = widget.getLayout();
        if (layout == null) return false;
        boolean select = (movementMetaState & KeyEvent.META_SHIFT_ON) != 0;
        boolean handled;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                handled = select ? Selection.extendLeft(buffer, layout) : Selection.moveLeft(buffer, layout);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                handled = select ? Selection.extendRight(buffer, layout) : Selection.moveRight(buffer, layout);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                handled = select ? Selection.extendUp(buffer, layout) : Selection.moveUp(buffer, layout);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                handled = select ? Selection.extendDown(buffer, layout) : Selection.moveDown(buffer, layout);
                break;
            case KeyEvent.KEYCODE_MOVE_HOME:
            case KeyEvent.KEYCODE_PAGE_UP:
                if (select) Selection.extendSelection(buffer, 0);
                else Selection.setSelection(buffer, 0);
                handled = true;
                break;
            case KeyEvent.KEYCODE_MOVE_END:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (select) Selection.extendSelection(buffer, buffer.length());
                else Selection.setSelection(buffer, buffer.length());
                handled = true;
                break;
            default:
                return false;
        }
        if (handled) widget.bringPointIntoView(Selection.getSelectionEnd(buffer));
        return handled;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_UP) return false;
        int off = widget.getOffsetForPosition(event.getX(), event.getY());
        if (off < 0) off = 0;
        if (off > buffer.length()) off = buffer.length();
        Selection.setSelection(buffer, off);
        return true;
    }

    public boolean previousParagraph(TextView widget, Spannable buffer) { return false; }

    public boolean nextParagraph(TextView widget, Spannable buffer) { return false; }
}
