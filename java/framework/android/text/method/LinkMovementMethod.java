package android.text.method;

import android.text.Selection;
import android.text.Spannable;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.widget.TextView;

/** Follows {@link ClickableSpan}s (AOSP LinkMovementMethod). */
public class LinkMovementMethod extends ScrollingMovementMethod {
    private static LinkMovementMethod sInstance;

    public LinkMovementMethod() {}

    public static MovementMethod getInstance() {
        if (sInstance == null) sInstance = new LinkMovementMethod();
        return sInstance;
    }

    @Override
    public boolean canSelectArbitrarily() { return false; }

    @Override
    public void initialize(TextView widget, Spannable text) { Selection.setSelection(text, 0); }

    @Override
    public void onTakeFocus(TextView widget, Spannable text, int direction) { Selection.setSelection(text, 0); }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            int off = widget.getOffsetForPosition(event.getX(), event.getY());
            if (off >= 0) {
                ClickableSpan[] links = buffer.getSpans(off, off, ClickableSpan.class);
                if (links.length != 0) {
                    if (action == MotionEvent.ACTION_UP) links[0].onClick(widget);
                    else Selection.setSelection(buffer, buffer.getSpanStart(links[0]), buffer.getSpanEnd(links[0]));
                    return true;
                }
            }
        }
        return super.onTouchEvent(widget, buffer, event);
    }
}
