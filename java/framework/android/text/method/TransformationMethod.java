package android.text.method;

import android.graphics.Rect;
import android.view.View;

/** Maps stored text to the characters that are drawn (AOSP TransformationMethod). */
public interface TransformationMethod {
    CharSequence getTransformation(CharSequence source, View view);

    void onFocusChanged(View view, CharSequence sourceText, boolean focused, int direction,
            Rect previouslyFocusedRect);
}
