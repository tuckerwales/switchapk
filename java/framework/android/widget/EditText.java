package android.widget;

import android.content.Context;
import android.text.Editable;
import android.text.Selection;
import android.text.TextUtils;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.MovementMethod;
import android.util.AttributeSet;
import android.view.KeyEvent;

/**
 * Editable {@link TextView}. Hardware keys and {@link android.view.inputmethod.InputConnection}
 * both write into {@link #getText()}.
 */
public class EditText extends TextView {
    private boolean mStyleShortcuts = true;

    public EditText(Context context) { this(context, null); }

    public EditText(Context context, AttributeSet attrs) { this(context, attrs, android.R.attr.editTextStyle); }

    public EditText(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public EditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected boolean getDefaultEditable() { return true; }

    @Override
    protected MovementMethod getDefaultMovementMethod() { return ArrowKeyMovementMethod.getInstance(); }

    @Override
    public boolean getFreezesText() { return true; }

    @Override
    public Editable getText() {
        CharSequence text = super.getText();
        if (text instanceof Editable) return (Editable) text;
        setText(text, BufferType.EDITABLE);
        return (Editable) super.getText();
    }

    @Override
    public void setText(CharSequence text, BufferType type) { super.setText(text, BufferType.EDITABLE); }

    public void setSelection(int start, int stop) { Selection.setSelection(getText(), start, stop); }

    public void setSelection(int index) { Selection.setSelection(getText(), index); }

    public void selectAll() { Selection.selectAll(getText()); }

    public void extendSelection(int index) { Selection.extendSelection(getText(), index); }

    @Override
    public void setEllipsize(TextUtils.TruncateAt ellipsis) {
        if (ellipsis == TextUtils.TruncateAt.MARQUEE) {
            throw new IllegalArgumentException("EditText cannot use the ellipsize mode TextUtils.TruncateAt.MARQUEE");
        }
        super.setEllipsize(ellipsis);
    }

    @Override
    public CharSequence getAccessibilityClassName() { return EditText.class.getName(); }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (mStyleShortcuts && event != null && event.hasModifiers(KeyEvent.META_CTRL_ON)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_A:
                    return onTextContextMenuItem(android.R.id.selectAll);
                case KeyEvent.KEYCODE_X:
                    return onTextContextMenuItem(android.R.id.cut);
                case KeyEvent.KEYCODE_C:
                    return onTextContextMenuItem(android.R.id.copy);
                case KeyEvent.KEYCODE_V:
                    return onTextContextMenuItem(android.R.id.paste);
                default:
                    break;
            }
        }
        return super.onKeyShortcut(keyCode, event);
    }

    public void setStyleShortcutsEnabled(boolean enabled) { mStyleShortcuts = enabled; }

    public boolean isStyleShortcutEnabled() { return mStyleShortcuts; }
}
