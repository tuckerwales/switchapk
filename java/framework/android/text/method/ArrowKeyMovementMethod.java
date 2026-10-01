package android.text.method;

import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

/** Moves the cursor and extends the selection for editable text (port of AOSP ArrowKeyMovementMethod). */
public class ArrowKeyMovementMethod extends BaseMovementMethod implements MovementMethod {
    private static final Object LAST_TAP_DOWN = new Object();
    private static ArrowKeyMovementMethod sInstance;

    public ArrowKeyMovementMethod() {}

    private static boolean isSelecting(Spannable buffer) {
        return ((MetaKeyKeyListener.getMetaState(buffer, MetaKeyKeyListener.META_SHIFT_ON) == 1)
                || (MetaKeyKeyListener.getMetaState(buffer, MetaKeyKeyListener.META_SELECTING) != 0));
    }

    private static int getCurrentLineTop(Spannable buffer, Layout layout) {
        return layout.getLineTop(layout.getLineForOffset(Selection.getSelectionEnd(buffer)));
    }

    private static int getPageHeight(TextView widget) {
        final Rect rect = new Rect();
        return widget.getGlobalVisibleRect(rect) ? rect.height() : 0;
    }

    @Override
    protected boolean handleMovementKey(TextView widget, Spannable buffer, int keyCode, int movementMetaState,
            KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                            && MetaKeyKeyListener.getMetaState(buffer, MetaKeyKeyListener.META_SELECTING, event) != 0) {
                        return widget.showContextMenu();
                    }
                }
                break;
            default:
                break;
        }
        return super.handleMovementKey(widget, buffer, keyCode, movementMetaState, event);
    }

    @Override
    protected boolean left(TextView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) return Selection.extendLeft(buffer, layout);
        return Selection.moveLeft(buffer, layout);
    }

    @Override
    protected boolean right(TextView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) return Selection.extendRight(buffer, layout);
        return Selection.moveRight(buffer, layout);
    }

    @Override
    protected boolean up(TextView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) return Selection.extendUp(buffer, layout);
        return Selection.moveUp(buffer, layout);
    }

    @Override
    protected boolean down(TextView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) return Selection.extendDown(buffer, layout);
        return Selection.moveDown(buffer, layout);
    }

    @Override
    protected boolean pageUp(TextView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        final boolean selecting = isSelecting(buffer);
        final int targetY = getCurrentLineTop(buffer, layout) - getPageHeight(widget);
        boolean handled = false;
        for (;;) {
            final int previousSelectionEnd = Selection.getSelectionEnd(buffer);
            if (selecting) Selection.extendUp(buffer, layout);
            else Selection.moveUp(buffer, layout);
            if (Selection.getSelectionEnd(buffer) == previousSelectionEnd) break;
            handled = true;
            if (getCurrentLineTop(buffer, layout) <= targetY) break;
        }
        return handled;
    }

    @Override
    protected boolean pageDown(TextView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        final boolean selecting = isSelecting(buffer);
        final int targetY = getCurrentLineTop(buffer, layout) + getPageHeight(widget);
        boolean handled = false;
        for (;;) {
            final int previousSelectionEnd = Selection.getSelectionEnd(buffer);
            if (selecting) Selection.extendDown(buffer, layout);
            else Selection.moveDown(buffer, layout);
            if (Selection.getSelectionEnd(buffer) == previousSelectionEnd) break;
            handled = true;
            if (getCurrentLineTop(buffer, layout) >= targetY) break;
        }
        return handled;
    }

    @Override
    protected boolean top(TextView widget, Spannable buffer) {
        if (isSelecting(buffer)) Selection.extendSelection(buffer, 0);
        else Selection.setSelection(buffer, 0);
        return true;
    }

    @Override
    protected boolean bottom(TextView widget, Spannable buffer) {
        if (isSelecting(buffer)) Selection.extendSelection(buffer, buffer.length());
        else Selection.setSelection(buffer, buffer.length());
        return true;
    }

    @Override
    protected boolean lineStart(TextView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) return Selection.extendToLeftEdge(buffer, layout);
        return Selection.moveToLeftEdge(buffer, layout);
    }

    @Override
    protected boolean lineEnd(TextView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) return Selection.extendToRightEdge(buffer, layout);
        return Selection.moveToRightEdge(buffer, layout);
    }

    @Override
    protected boolean leftWord(TextView widget, Spannable buffer) {
        int pos = Selection.getSelectionEnd(buffer);
        while (pos > 0 && !Character.isLetterOrDigit(buffer.charAt(pos - 1))) pos--;
        while (pos > 0 && Character.isLetterOrDigit(buffer.charAt(pos - 1))) pos--;
        return moveTo(buffer, pos);
    }

    @Override
    protected boolean rightWord(TextView widget, Spannable buffer) {
        int pos = Selection.getSelectionEnd(buffer);
        final int len = buffer.length();
        while (pos < len && !Character.isLetterOrDigit(buffer.charAt(pos))) pos++;
        while (pos < len && Character.isLetterOrDigit(buffer.charAt(pos))) pos++;
        return moveTo(buffer, pos);
    }

    private static boolean moveTo(Spannable buffer, int pos) {
        if (isSelecting(buffer)) Selection.extendSelection(buffer, pos);
        else Selection.setSelection(buffer, pos);
        return true;
    }

    @Override
    protected boolean home(TextView widget, Spannable buffer) { return lineStart(widget, buffer); }

    @Override
    protected boolean end(TextView widget, Spannable buffer) { return lineEnd(widget, buffer); }

    @Override
    public boolean previousParagraph(TextView widget, Spannable buffer) {
        int pos = Selection.getSelectionEnd(buffer);
        if (pos > 0 && buffer.charAt(pos - 1) == '\n') pos--;
        while (pos > 0 && buffer.charAt(pos - 1) != '\n') pos--;
        return moveTo(buffer, pos);
    }

    @Override
    public boolean nextParagraph(TextView widget, Spannable buffer) {
        int pos = Selection.getSelectionEnd(buffer);
        final int len = buffer.length();
        if (pos < len && buffer.charAt(pos) == '\n') pos++;
        while (pos < len && buffer.charAt(pos) != '\n') pos++;
        return moveTo(buffer, pos);
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int initialScrollX = -1;
        int initialScrollY = -1;
        final int action = event.getAction();
        if (action == MotionEvent.ACTION_UP) {
            initialScrollX = Touch.getInitialScrollX(widget, buffer);
            initialScrollY = Touch.getInitialScrollY(widget, buffer);
        }
        boolean wasTouchSelecting = isSelecting(buffer);
        boolean handled = Touch.onTouchEvent(widget, buffer, event);
        if (widget.didTouchFocusSelect()) return handled;
        if (action == MotionEvent.ACTION_DOWN) {
            if (isSelecting(buffer)) {
                if (!widget.isFocused()) {
                    if (!widget.requestFocus()) return handled;
                }
                int offset = widget.getOffsetForPosition(event.getX(), event.getY());
                buffer.setSpan(LAST_TAP_DOWN, offset, offset, Spannable.SPAN_POINT_POINT);
                if (widget.getParent() != null) widget.getParent().requestDisallowInterceptTouchEvent(true);
            }
        } else if (widget.isFocused()) {
            if (action == MotionEvent.ACTION_MOVE) {
                if (isSelecting(buffer) && handled) {
                    final int startOffset = buffer.getSpanStart(LAST_TAP_DOWN);
                    widget.cancelLongPress();
                    int offset = widget.getOffsetForPosition(event.getX(), event.getY());
                    Selection.setSelection(buffer, Math.min(startOffset, offset), Math.max(startOffset, offset));
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP) {
                if ((initialScrollY >= 0 && initialScrollY != widget.getScrollY())
                        || (initialScrollX >= 0 && initialScrollX != widget.getScrollX())) {
                    widget.moveCursorToVisibleOffset();
                    return true;
                }
                if (wasTouchSelecting) {
                    final int startOffset = buffer.getSpanStart(LAST_TAP_DOWN);
                    final int endOffset = widget.getOffsetForPosition(event.getX(), event.getY());
                    Selection.setSelection(buffer, Math.min(startOffset, endOffset), Math.max(startOffset, endOffset));
                    buffer.removeSpan(LAST_TAP_DOWN);
                }
                MetaKeyKeyListener.adjustMetaAfterKeypress(buffer);
                MetaKeyKeyListener.resetLockedMeta(buffer);
                return true;
            }
        }
        return handled;
    }

    @Override
    public boolean canSelectArbitrarily() { return true; }

    @Override
    public void initialize(TextView widget, Spannable text) { Selection.setSelection(text, 0); }

    @Override
    public void onTakeFocus(TextView view, Spannable text, int dir) {
        if ((dir & (View.FOCUS_FORWARD | View.FOCUS_DOWN)) != 0) {
            if (view.getLayout() == null) Selection.setSelection(text, text.length());
        } else {
            Selection.setSelection(text, text.length());
        }
    }

    public static MovementMethod getInstance() {
        if (sInstance == null) sInstance = new ArrowKeyMovementMethod();
        return sInstance;
    }
}
