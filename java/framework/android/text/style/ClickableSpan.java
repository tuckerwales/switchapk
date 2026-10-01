package android.text.style;

import android.text.TextPaint;
import android.view.View;

/** A span that reacts to clicks through LinkMovementMethod (AOSP ClickableSpan). */
public abstract class ClickableSpan extends CharacterStyle implements UpdateAppearance {
    private static int sIdCounter = 0;
    private int mId = sIdCounter++;

    public abstract void onClick(View widget);

    @Override
    public void updateDrawState(TextPaint ds) {
        ds.setColor(ds.linkColor);
        ds.setUnderlineText(true);
    }

    /** Hidden AOSP API. */
    public int getId() { return mId; }

    @Override
    public String toString() { return "ClickableSpan{}"; }
}
