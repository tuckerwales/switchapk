package android.text.method;

import android.text.Layout;
import android.text.NoCopySpan;
import android.text.Selection;
import android.text.Spannable;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

/** Moves between and clicks the ClickableSpans of a TextView (port of AOSP LinkMovementMethod). */
public class LinkMovementMethod extends ScrollingMovementMethod {
    private static final int CLICK = 1;
    private static final int UP = 2;
    private static final int DOWN = 3;
    private static final Object FROM_BELOW = new NoCopySpan.Concrete();
    private static LinkMovementMethod sInstance;

    public LinkMovementMethod() {}

    @Override
    public boolean canSelectArbitrarily() { return true; }

    @Override
    protected boolean handleMovementKey(TextView widget, Spannable buffer, int keyCode, int movementMetaState,
            KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                            && action(CLICK, widget, buffer)) {
                        return true;
                    }
                }
                break;
            default:
                break;
        }
        return super.handleMovementKey(widget, buffer, keyCode, movementMetaState, event);
    }

    @Override
    protected boolean up(TextView widget, Spannable buffer) {
        if (action(UP, widget, buffer)) return true;
        return super.up(widget, buffer);
    }

    @Override
    protected boolean down(TextView widget, Spannable buffer) {
        if (action(DOWN, widget, buffer)) return true;
        return super.down(widget, buffer);
    }

    @Override
    protected boolean left(TextView widget, Spannable buffer) {
        if (action(UP, widget, buffer)) return true;
        return super.left(widget, buffer);
    }

    @Override
    protected boolean right(TextView widget, Spannable buffer) {
        if (action(DOWN, widget, buffer)) return true;
        return super.right(widget, buffer);
    }

    private boolean action(int what, TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();
        if (layout == null) return false;
        int padding = widget.getTotalPaddingTop() + widget.getTotalPaddingBottom();
        int areaTop = widget.getScrollY();
        int areaBot = areaTop + widget.getHeight() - padding;
        int lineTop = layout.getLineForVertical(areaTop);
        int lineBot = layout.getLineForVertical(areaBot);
        int first = layout.getLineStart(lineTop);
        int last = layout.getLineEnd(lineBot);
        ClickableSpan[] candidates = buffer.getSpans(first, last, ClickableSpan.class);
        int a = Selection.getSelectionStart(buffer);
        int b = Selection.getSelectionEnd(buffer);
        int selStart = Math.min(a, b);
        int selEnd = Math.max(a, b);
        if (selStart < 0) {
            if (buffer.getSpanStart(FROM_BELOW) >= 0) selStart = selEnd = buffer.length();
        }
        if (selStart > last) selStart = selEnd = Integer.MAX_VALUE;
        if (selEnd < first) selStart = selEnd = -1;
        switch (what) {
            case CLICK: {
                if (selStart == selEnd) return false;
                ClickableSpan[] links = buffer.getSpans(selStart, selEnd, ClickableSpan.class);
                if (links.length != 1) return false;
                links[0].onClick(widget);
                break;
            }
            case UP: {
                int bestStart = -1;
                int bestEnd = -1;
                for (int i = 0; i < candidates.length; i++) {
                    int end = buffer.getSpanEnd(candidates[i]);
                    if (end < selEnd || selStart == selEnd) {
                        if (end > bestEnd) {
                            bestStart = buffer.getSpanStart(candidates[i]);
                            bestEnd = end;
                        }
                    }
                }
                if (bestStart >= 0) {
                    Selection.setSelection(buffer, bestEnd, bestStart);
                    return true;
                }
                break;
            }
            case DOWN: {
                int bestStart = Integer.MAX_VALUE;
                int bestEnd = Integer.MAX_VALUE;
                for (int i = 0; i < candidates.length; i++) {
                    int start = buffer.getSpanStart(candidates[i]);
                    if (start > selStart || selStart == selEnd) {
                        if (start < bestStart) {
                            bestStart = start;
                            bestEnd = buffer.getSpanEnd(candidates[i]);
                        }
                    }
                }
                if (bestEnd < Integer.MAX_VALUE) {
                    Selection.setSelection(buffer, bestStart, bestEnd);
                    return true;
                }
                break;
            }
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();
            x += widget.getScrollX();
            y += widget.getScrollY();
            Layout layout = widget.getLayout();
            ClickableSpan[] links;
            if (y < 0 || y > layout.getHeight()) {
                links = null;
            } else {
                int line = layout.getLineForVertical(y);
                if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) {
                    links = null;
                } else {
                    int off = layout.getOffsetForHorizontal(line, x);
                    links = buffer.getSpans(off, off, ClickableSpan.class);
                }
            }
            if (links != null && links.length != 0) {
                ClickableSpan link = links[0];
                if (action == MotionEvent.ACTION_UP) {
                    link.onClick(widget);
                } else {
                    Selection.setSelection(buffer, buffer.getSpanStart(link), buffer.getSpanEnd(link));
                }
                return true;
            } else {
                Selection.removeSelection(buffer);
            }
        }
        return super.onTouchEvent(widget, buffer, event);
    }

    @Override
    public void initialize(TextView widget, Spannable text) {
        Selection.removeSelection(text);
        text.removeSpan(FROM_BELOW);
    }

    @Override
    public void onTakeFocus(TextView view, Spannable text, int dir) {
        Selection.removeSelection(text);
        if ((dir & View.FOCUS_BACKWARD) != 0) text.setSpan(FROM_BELOW, 0, 0, Spannable.SPAN_POINT_POINT);
        else text.removeSpan(FROM_BELOW);
    }

    public static MovementMethod getInstance() {
        if (sInstance == null) sInstance = new LinkMovementMethod();
        return sInstance;
    }
}
