package android.widget;

import android.content.Context;

/**
 * framework-internal. Port of AOSP DropDownListView: the list inside a
 * ListPopupWindow. A non-modal popup (AutoCompleteTextView) "hijacks" focus:
 * the list behaves as focused while its window is not, and its selection can
 * be hidden while the user types.
 */
public class DropDownListView extends ListView {
    private boolean mListSelectionHidden;
    private final boolean mHijackFocus;

    public DropDownListView(Context context, boolean hijackFocus) {
        this(context, hijackFocus, android.R.attr.dropDownListViewStyle);
    }

    public DropDownListView(Context context, boolean hijackFocus, int defStyleAttr) {
        super(context, null, defStyleAttr);
        mHijackFocus = hijackFocus;
        setCacheColorHint(0);
    }

    public void setListSelectionHidden(boolean hideListSelection) { mListSelectionHidden = hideListSelection; }

    @Override
    public boolean isInTouchMode() { return (mHijackFocus && mListSelectionHidden) || super.isInTouchMode(); }

    @Override
    public boolean hasWindowFocus() { return mHijackFocus || super.hasWindowFocus(); }

    @Override
    public boolean isFocused() { return mHijackFocus || super.isFocused(); }

    @Override
    public boolean hasFocus() { return mHijackFocus || super.hasFocus(); }
}
