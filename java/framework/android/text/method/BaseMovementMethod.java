package android.text.method;

import android.text.Layout;
import android.text.Spannable;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

/** Key to movement dispatch plus scrolling helpers (port of AOSP BaseMovementMethod). */
public class BaseMovementMethod implements MovementMethod {
    public BaseMovementMethod() {}

    public boolean canSelectArbitrarily() { return false; }

    public void initialize(TextView widget, Spannable text) {}

    public boolean onKeyDown(TextView widget, Spannable text, int keyCode, KeyEvent event) {
        final int movementMetaState = getMovementMetaState(text, event);
        boolean handled = handleMovementKey(widget, text, keyCode, movementMetaState, event);
        if (handled) {
            MetaKeyKeyListener.adjustMetaAfterKeypress(text);
            MetaKeyKeyListener.resetLockedMeta(text);
        }
        return handled;
    }

    public boolean onKeyOther(TextView widget, Spannable text, KeyEvent event) {
        final int movementMetaState = getMovementMetaState(text, event);
        final int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN && event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            final int repeat = event.getRepeatCount();
            boolean handled = false;
            for (int i = 0; i < repeat; i++) {
                if (!handleMovementKey(widget, text, keyCode, movementMetaState, event)) break;
                handled = true;
            }
            if (handled) {
                MetaKeyKeyListener.adjustMetaAfterKeypress(text);
                MetaKeyKeyListener.resetLockedMeta(text);
            }
            return handled;
        }
        return false;
    }

    public boolean onKeyUp(TextView widget, Spannable text, int keyCode, KeyEvent event) { return false; }

    public void onTakeFocus(TextView widget, Spannable text, int direction) {}

    public boolean onTouchEvent(TextView widget, Spannable text, MotionEvent event) { return false; }

    public boolean onTrackballEvent(TextView widget, Spannable text, MotionEvent event) { return false; }

    public boolean onGenericMotionEvent(TextView widget, Spannable text, MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            if (event.getAction() == MotionEvent.ACTION_SCROLL) {
                final float vscroll;
                final float hscroll;
                if ((event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0) {
                    vscroll = 0;
                    hscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                } else {
                    vscroll = -event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                }
                boolean handled = false;
                if (hscroll < 0) handled |= scrollLeft(widget, text, (int) Math.ceil(-hscroll));
                else if (hscroll > 0) handled |= scrollRight(widget, text, (int) Math.ceil(hscroll));
                if (vscroll < 0) handled |= scrollUp(widget, text, (int) Math.ceil(-vscroll));
                else if (vscroll > 0) handled |= scrollDown(widget, text, (int) Math.ceil(vscroll));
                return handled;
            }
        }
        return false;
    }

    protected int getMovementMetaState(Spannable buffer, KeyEvent event) {
        int metaState = MetaKeyKeyListener.getMetaState(buffer, event)
                & ~(MetaKeyKeyListener.META_ALT_LOCKED | MetaKeyKeyListener.META_SYM_LOCKED);
        return KeyEvent.normalizeMetaState(metaState) & ~KeyEvent.META_SHIFT_MASK;
    }

    protected boolean handleMovementKey(TextView widget, Spannable buffer, int keyCode, int movementMetaState,
            KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) return left(widget, buffer);
                else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_CTRL_ON)) return leftWord(widget, buffer);
                else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_ALT_ON)) return lineStart(widget, buffer);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) return right(widget, buffer);
                else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_CTRL_ON)) return rightWord(widget, buffer);
                else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_ALT_ON)) return lineEnd(widget, buffer);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) return up(widget, buffer);
                else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_ALT_ON)) return top(widget, buffer);
                else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_CTRL_ON)) return previousParagraph(widget, buffer);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) return down(widget, buffer);
                else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_ALT_ON)) return bottom(widget, buffer);
                else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_CTRL_ON)) return nextParagraph(widget, buffer);
                break;
            case KeyEvent.KEYCODE_PAGE_UP:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) return pageUp(widget, buffer);
                else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_ALT_ON)) return top(widget, buffer);
                break;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) return pageDown(widget, buffer);
                else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_ALT_ON)) return bottom(widget, buffer);
                break;
            case KeyEvent.KEYCODE_MOVE_HOME:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) return home(widget, buffer);
                else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_CTRL_ON)) return top(widget, buffer);
                break;
            case KeyEvent.KEYCODE_MOVE_END:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) return end(widget, buffer);
                else if (KeyEvent.metaStateHasModifiers(movementMetaState, KeyEvent.META_CTRL_ON)) return bottom(widget, buffer);
                break;
            default:
                break;
        }
        return false;
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

    /** Hidden AOSP API. */
    protected boolean leftWord(TextView widget, Spannable buffer) { return false; }

    /** Hidden AOSP API. */
    protected boolean rightWord(TextView widget, Spannable buffer) { return false; }

    protected boolean home(TextView widget, Spannable buffer) { return false; }

    protected boolean end(TextView widget, Spannable buffer) { return false; }

    public boolean previousParagraph(TextView widget, Spannable buffer) { return false; }

    public boolean nextParagraph(TextView widget, Spannable buffer) { return false; }

    private int getTopLine(TextView widget) { return widget.getLayout().getLineForVertical(widget.getScrollY()); }

    private int getBottomLine(TextView widget) {
        return widget.getLayout().getLineForVertical(widget.getScrollY() + getInnerHeight(widget));
    }

    private int getInnerWidth(TextView widget) {
        return widget.getWidth() - widget.getTotalPaddingLeft() - widget.getTotalPaddingRight();
    }

    private int getInnerHeight(TextView widget) {
        return widget.getHeight() - widget.getTotalPaddingTop() - widget.getTotalPaddingBottom();
    }

    private int getCharacterWidth(TextView widget) { return (int) Math.ceil(widget.getPaint().getFontSpacing()); }

    private int getScrollBoundsLeft(TextView widget) {
        final Layout layout = widget.getLayout();
        final int topLine = getTopLine(widget);
        final int bottomLine = getBottomLine(widget);
        if (topLine > bottomLine) return 0;
        int left = Integer.MAX_VALUE;
        for (int line = topLine; line <= bottomLine; line++) {
            final int lineLeft = (int) Math.floor(layout.getLineLeft(line));
            if (lineLeft < left) left = lineLeft;
        }
        return left;
    }

    private int getScrollBoundsRight(TextView widget) {
        final Layout layout = widget.getLayout();
        final int topLine = getTopLine(widget);
        final int bottomLine = getBottomLine(widget);
        if (topLine > bottomLine) return 0;
        int right = Integer.MIN_VALUE;
        for (int line = topLine; line <= bottomLine; line++) {
            final int lineRight = (int) Math.ceil(layout.getLineRight(line));
            if (lineRight > right) right = lineRight;
        }
        return right;
    }

    /** Hidden AOSP API. */
    protected boolean scrollLeft(TextView widget, Spannable buffer, int amount) {
        final int minScrollX = getScrollBoundsLeft(widget);
        int scrollX = widget.getScrollX();
        if (scrollX > minScrollX) {
            scrollX = Math.max(scrollX - getCharacterWidth(widget) * amount, minScrollX);
            widget.scrollTo(scrollX, widget.getScrollY());
            return true;
        }
        return false;
    }

    /** Hidden AOSP API. */
    protected boolean scrollRight(TextView widget, Spannable buffer, int amount) {
        final int maxScrollX = getScrollBoundsRight(widget) - getInnerWidth(widget);
        int scrollX = widget.getScrollX();
        if (scrollX < maxScrollX) {
            scrollX = Math.min(scrollX + getCharacterWidth(widget) * amount, maxScrollX);
            widget.scrollTo(scrollX, widget.getScrollY());
            return true;
        }
        return false;
    }

    /** Hidden AOSP API. */
    protected boolean scrollUp(TextView widget, Spannable buffer, int amount) {
        final Layout layout = widget.getLayout();
        final int top = widget.getScrollY();
        int topLine = layout.getLineForVertical(top);
        if (layout.getLineTop(topLine) == top) topLine -= 1;
        if (topLine >= 0) {
            topLine = Math.max(topLine - amount + 1, 0);
            Touch.scrollTo(widget, layout, widget.getScrollX(), layout.getLineTop(topLine));
            return true;
        }
        return false;
    }

    /** Hidden AOSP API. */
    protected boolean scrollDown(TextView widget, Spannable buffer, int amount) {
        final Layout layout = widget.getLayout();
        final int innerHeight = getInnerHeight(widget);
        final int bottom = widget.getScrollY() + innerHeight;
        int bottomLine = layout.getLineForVertical(bottom);
        if (layout.getLineTop(bottomLine + 1) < bottom + 1) bottomLine += 1;
        final int limit = layout.getLineCount() - 1;
        if (bottomLine <= limit) {
            bottomLine = Math.min(bottomLine + amount - 1, limit);
            Touch.scrollTo(widget, layout, widget.getScrollX(), layout.getLineTop(bottomLine + 1) - innerHeight);
            return true;
        }
        return false;
    }

    /** Hidden AOSP API. */
    protected boolean scrollPageUp(TextView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        final int top = widget.getScrollY() - getInnerHeight(widget);
        int topLine = layout.getLineForVertical(top);
        if (topLine >= 0) {
            Touch.scrollTo(widget, layout, widget.getScrollX(), layout.getLineTop(topLine));
            return true;
        }
        return false;
    }

    /** Hidden AOSP API. */
    protected boolean scrollPageDown(TextView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        final int innerHeight = getInnerHeight(widget);
        final int bottom = widget.getScrollY() + innerHeight + innerHeight;
        int bottomLine = layout.getLineForVertical(bottom);
        if (bottomLine <= layout.getLineCount() - 1) {
            Touch.scrollTo(widget, layout, widget.getScrollX(), layout.getLineTop(bottomLine + 1) - innerHeight);
            return true;
        }
        return false;
    }

    /** Hidden AOSP API. */
    protected boolean scrollTop(TextView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (getTopLine(widget) >= 0) {
            Touch.scrollTo(widget, layout, widget.getScrollX(), layout.getLineTop(0));
            return true;
        }
        return false;
    }

    /** Hidden AOSP API. */
    protected boolean scrollBottom(TextView widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        final int lineCount = layout.getLineCount();
        if (getBottomLine(widget) <= lineCount - 1) {
            Touch.scrollTo(widget, layout, widget.getScrollX(), layout.getLineTop(lineCount) - getInnerHeight(widget));
            return true;
        }
        return false;
    }

    /** Hidden AOSP API. */
    protected boolean scrollLineStart(TextView widget, Spannable buffer) {
        final int minScrollX = getScrollBoundsLeft(widget);
        int scrollX = widget.getScrollX();
        if (scrollX > minScrollX) {
            widget.scrollTo(minScrollX, widget.getScrollY());
            return true;
        }
        return false;
    }

    /** Hidden AOSP API. */
    protected boolean scrollLineEnd(TextView widget, Spannable buffer) {
        final int maxScrollX = getScrollBoundsRight(widget) - getInnerWidth(widget);
        int scrollX = widget.getScrollX();
        if (scrollX < maxScrollX) {
            widget.scrollTo(maxScrollX, widget.getScrollY());
            return true;
        }
        return false;
    }
}
