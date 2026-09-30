package android.text;

/** Parent of android.content.ClipboardManager. The modern API lives on the subclass. */
public abstract class ClipboardManager {
    public abstract CharSequence getText();
    public abstract void setText(CharSequence text);
    public abstract boolean hasText();
}
