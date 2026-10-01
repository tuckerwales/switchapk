package android.text.style;

import android.graphics.MaskFilter;
import android.text.TextPaint;

/** Applies a MaskFilter (such as blur) to the spanned run (AOSP MaskFilterSpan). */
public class MaskFilterSpan extends CharacterStyle implements UpdateAppearance {
    private MaskFilter mFilter;

    public MaskFilterSpan(MaskFilter filter) { mFilter = filter; }

    public MaskFilter getMaskFilter() { return mFilter; }

    @Override
    public void updateDrawState(TextPaint ds) { ds.setMaskFilter(mFilter); }

    @Override
    public String toString() { return "MaskFilterSpan{filter=" + getMaskFilter() + "}"; }
}
