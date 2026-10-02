package android.widget;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Selection;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;

/**
 * An editable text view that shows completion suggestions in a drop-down while the user types
 * (AOSP AutoCompleteTextView). The adapter's {@link Filter} runs once the text reaches the
 * threshold; picking a row (touch, or D-pad then A/Enter) replaces the text.
 */
public class AutoCompleteTextView extends EditText implements Filter.FilterListener {
    static final boolean DEBUG = false;
    static final String TAG = "AutoCompleteTextView";

    static final int EXPAND_MAX = 3;

    private final ListPopupWindow mPopup;
    private final Context mPopupContext;

    private CharSequence mHintText;
    private TextView mHintView;
    private int mHintResource;

    private ListAdapter mAdapter;
    private Filter mFilter;
    private int mThreshold;

    private int mDropDownAnchorId;

    private AdapterView.OnItemClickListener mItemClickListener;
    private AdapterView.OnItemSelectedListener mItemSelectedListener;

    private boolean mDropDownDismissedOnCompletion = true;

    private int mLastKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private boolean mOpenBefore;

    private Validator mValidator = null;

    // Set to true when text is set directly and no filtering shall be performed
    private boolean mBlockCompletion;

    // When set, an update in the underlying adapter will update the result list popup.
    // Set to false when the list is hidden to prevent asynchronous updates to popup the list again.
    private boolean mPopupCanBeUpdated = true;

    private PassThroughClickListener mPassThroughClickListener;
    private PopupDataSetObserver mObserver;

    public AutoCompleteTextView(Context context) { this(context, null); }

    public AutoCompleteTextView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.autoCompleteTextViewStyle);
    }

    public AutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this(context, attrs, defStyleAttr, defStyleRes, null);
    }

    public AutoCompleteTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes,
            Resources.Theme popupTheme) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.completionHint, android.R.attr.completionHintView,
                android.R.attr.completionThreshold, android.R.attr.dropDownSelector,
                android.R.attr.dropDownAnchor, android.R.attr.dropDownWidth, android.R.attr.dropDownHeight,
                android.R.attr.popupTheme}, defStyleAttr, defStyleRes);
        if (popupTheme != null) {
            mPopupContext = new ContextThemeWrapper(context, popupTheme);
        } else {
            final int popupThemeResId = a.getResourceId(7, 0);
            if (popupThemeResId != 0) mPopupContext = new ContextThemeWrapper(context, popupThemeResId);
            else mPopupContext = context;
        }
        mPopup = new ListPopupWindow(mPopupContext, attrs, defStyleAttr, defStyleRes);
        mPopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        mPopup.setPromptPosition(ListPopupWindow.POSITION_PROMPT_BELOW);
        Drawable selector = a.getDrawable(3);
        if (selector != null) mPopup.setListSelector(selector);
        mThreshold = a.getInt(2, 2);
        mDropDownAnchorId = a.getResourceId(4, View.NO_ID);
        // For dropdown width, the developer can specify a specific width, or MATCH_PARENT
        // (for full screen width) or WRAP_CONTENT (to match the width of the anchored view).
        mPopup.setWidth(a.getLayoutDimension(5, ViewGroup.LayoutParams.WRAP_CONTENT));
        mPopup.setHeight(a.getLayoutDimension(6, ViewGroup.LayoutParams.WRAP_CONTENT));
        mHintResource = a.getResourceId(1, 0);
        mPopup.setOnItemClickListener(new DropDownItemClickListener());
        setCompletionHint(a.getText(0));
        a.recycle();
        // Always turn on the auto complete input type flag, since it makes no sense to use this
        // widget without it.
        int inputType = getInputType();
        if ((inputType & android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_TEXT) {
            inputType |= android.text.InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE;
            setRawInputType(inputType);
        }
        setFocusable(true);
        addTextChangedListener(new MyWatcher());
        mPassThroughClickListener = new PassThroughClickListener();
        super.setOnClickListener(mPassThroughClickListener);
    }

    @Override
    public void setOnClickListener(OnClickListener listener) { mPassThroughClickListener.mWrapped = listener; }

    private void onClickImpl() {
        // If the dropdown is showing, bring the keyboard to the front when the user touches the text field.
        if (isPopupShowing()) ensureImeVisible(true);
    }

    public void setCompletionHint(CharSequence hint) {
        mHintText = hint;
        if (hint != null) {
            if (mHintView == null) {
                final TextView hintView = (TextView) LayoutInflater.from(mPopupContext).inflate(mHintResource, null)
                        .findViewById(android.R.id.text1);
                hintView.setText(mHintText);
                mHintView = hintView;
                mPopup.setPromptView(hintView);
            } else {
                mHintView.setText(hint);
            }
        } else {
            mPopup.setPromptView(null);
            mHintView = null;
        }
    }

    public CharSequence getCompletionHint() { return mHintText; }

    public int getDropDownWidth() { return mPopup.getWidth(); }

    public void setDropDownWidth(int width) { mPopup.setWidth(width); }

    public int getDropDownHeight() { return mPopup.getHeight(); }

    public void setDropDownHeight(int height) { mPopup.setHeight(height); }

    public int getDropDownAnchor() { return mDropDownAnchorId; }

    public void setDropDownAnchor(int id) {
        mDropDownAnchorId = id;
        mPopup.setAnchorView(null);
    }

    public Drawable getDropDownBackground() { return mPopup.getBackground(); }

    public void setDropDownBackgroundDrawable(Drawable d) { mPopup.setBackgroundDrawable(d); }

    public void setDropDownBackgroundResource(int id) {
        mPopup.setBackgroundDrawable(getContext().getDrawable(id));
    }

    public void setDropDownVerticalOffset(int offset) { mPopup.setVerticalOffset(offset); }

    public int getDropDownVerticalOffset() { return mPopup.getVerticalOffset(); }

    public void setDropDownHorizontalOffset(int offset) { mPopup.setHorizontalOffset(offset); }

    public int getDropDownHorizontalOffset() { return mPopup.getHorizontalOffset(); }

    /** framework-internal (AOSP, hidden). */
    public void setDropDownAnimationStyle(int animationStyle) { mPopup.setAnimationStyle(animationStyle); }

    /** framework-internal (AOSP, hidden). */
    public int getDropDownAnimationStyle() { return mPopup.getAnimationStyle(); }

    /** framework-internal (AOSP, hidden). */
    public boolean isDropDownAlwaysVisible() { return mPopup.isDropDownAlwaysVisible(); }

    /** framework-internal (AOSP, hidden). */
    public void setDropDownAlwaysVisible(boolean dropDownAlwaysVisible) {
        mPopup.setDropDownAlwaysVisible(dropDownAlwaysVisible);
    }

    /** framework-internal (AOSP, hidden). */
    public boolean isDropDownDismissedOnCompletion() { return mDropDownDismissedOnCompletion; }

    /** framework-internal (AOSP, hidden). */
    public void setDropDownDismissedOnCompletion(boolean dropDownDismissedOnCompletion) {
        mDropDownDismissedOnCompletion = dropDownDismissedOnCompletion;
    }

    public int getThreshold() { return mThreshold; }

    public void setThreshold(int threshold) {
        if (threshold <= 0) threshold = 1;
        mThreshold = threshold;
    }

    public void setOnItemClickListener(AdapterView.OnItemClickListener l) { mItemClickListener = l; }

    public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener l) { mItemSelectedListener = l; }

    @Deprecated
    public AdapterView.OnItemClickListener getItemClickListener() { return mItemClickListener; }

    @Deprecated
    public AdapterView.OnItemSelectedListener getItemSelectedListener() { return mItemSelectedListener; }

    public AdapterView.OnItemClickListener getOnItemClickListener() { return mItemClickListener; }

    public AdapterView.OnItemSelectedListener getOnItemSelectedListener() { return mItemSelectedListener; }

    public void setOnDismissListener(final OnDismissListener dismissListener) {
        PopupWindow.OnDismissListener wrappedListener = null;
        if (dismissListener != null) {
            wrappedListener = new PopupWindow.OnDismissListener() {
                @Override
                public void onDismiss() { dismissListener.onDismiss(); }
            };
        }
        mPopup.setOnDismissListener(wrappedListener);
    }

    public ListAdapter getAdapter() { return mAdapter; }

    public <T extends ListAdapter & Filterable> void setAdapter(T adapter) {
        if (mObserver == null) {
            mObserver = new PopupDataSetObserver(this);
        } else if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mObserver);
        }
        mAdapter = adapter;
        if (mAdapter != null) {
            mFilter = ((Filterable) mAdapter).getFilter();
            adapter.registerDataSetObserver(mObserver);
        } else {
            mFilter = null;
        }
        mPopup.setAdapter(mAdapter);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && isPopupShowing() && !mPopup.isDropDownAlwaysVisible()) {
            // special case for the back key, we do not even try to send it
            // to the drop down list but instead, consume it immediately
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                KeyEvent.DispatcherState state = getKeyDispatcherState();
                if (state != null) state.startTracking(event, this);
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                KeyEvent.DispatcherState state = getKeyDispatcherState();
                if (state != null) state.handleUpEvent(event);
                if (event.isTracking() && !event.isCanceled()) {
                    dismissDropDown();
                    return true;
                }
            }
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean consumed = mPopup.onKeyUp(keyCode, event);
        if (consumed) {
            switch (keyCode) {
                // if the list accepts the key events and the key event
                // was a click, the text view gets the selected item
                // from the drop down as its content
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_TAB:
                    if (event.hasNoModifiers()) performCompletion();
                    return true;
                default:
                    break;
            }
        }
        if (isPopupShowing() && keyCode == KeyEvent.KEYCODE_TAB && event.hasNoModifiers()) {
            performCompletion();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mPopup.onKeyDown(keyCode, event)) return true;
        if (!isPopupShowing()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.hasNoModifiers()) performValidation();
        }
        if (isPopupShowing() && keyCode == KeyEvent.KEYCODE_TAB && event.hasNoModifiers()) return true;
        mLastKeyCode = keyCode;
        boolean handled = super.onKeyDown(keyCode, event);
        mLastKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        if (handled && isPopupShowing()) clearListSelection();
        return handled;
    }

    public boolean enoughToFilter() {
        if (DEBUG) Log.v(TAG, "Enough to filter: len=" + getText().length() + " threshold=" + mThreshold);
        return getText().length() >= mThreshold;
    }

    /** Watches the text and starts filtering, unless the change came from a completion. */
    private class MyWatcher implements TextWatcher {
        @Override
        public void afterTextChanged(Editable s) { doAfterTextChanged(); }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { doBeforeTextChanged(); }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    /** framework-internal (AOSP, hidden). */
    void doBeforeTextChanged() {
        if (mBlockCompletion) return;
        mOpenBefore = isPopupShowing();
    }

    /** framework-internal (AOSP, hidden). */
    void doAfterTextChanged() {
        if (mBlockCompletion) return;
        // if the list was open before the keystroke, but closed afterwards,
        // then something in the keystroke processing (an input filter perhaps)
        // called performCompletion() and we shouldn't do any more processing.
        if (mOpenBefore && !isPopupShowing()) return;
        refreshAutoCompleteResults();
    }

    public final void refreshAutoCompleteResults() {
        // the drop down is shown only when a minimum number of characters
        // was typed in the text view
        if (enoughToFilter()) {
            if (mFilter != null) {
                mPopupCanBeUpdated = true;
                performFiltering(getText(), mLastKeyCode);
            }
        } else {
            // drop down is automatically dismissed when enough characters
            // are deleted from the text view
            if (!mPopup.isDropDownAlwaysVisible()) dismissDropDown();
            if (mFilter != null) mFilter.filter(null);
        }
    }

    public boolean isPopupShowing() { return mPopup.isShowing(); }

    protected CharSequence convertSelectionToString(Object selectedItem) {
        return mFilter.convertResultToString(selectedItem);
    }

    public void clearListSelection() { mPopup.clearListSelection(); }

    public void setListSelection(int position) { mPopup.setSelection(position); }

    public int getListSelection() { return mPopup.getSelectedItemPosition(); }

    protected void performFiltering(CharSequence text, int keyCode) { mFilter.filter(text, this); }

    public void performCompletion() { performCompletion(null, -1, -1); }

    @Override
    public void onCommitCompletion(CompletionInfo completion) {
        if (isPopupShowing()) {
            mPopup.performItemClick(completion.getPosition());
        }
    }

    private void performCompletion(View selectedView, int position, long id) {
        if (isPopupShowing()) {
            Object selectedItem;
            if (position < 0) selectedItem = mPopup.getSelectedItem();
            else selectedItem = mAdapter.getItem(position);
            if (selectedItem == null) {
                Log.w(TAG, "performCompletion: no selected item");
                return;
            }
            mBlockCompletion = true;
            replaceText(convertSelectionToString(selectedItem));
            mBlockCompletion = false;
            if (mItemClickListener != null) {
                final ListPopupWindow list = mPopup;
                if (selectedView == null || position < 0) {
                    selectedView = list.getSelectedView();
                    position = list.getSelectedItemPosition();
                    id = list.getSelectedItemId();
                }
                mItemClickListener.onItemClick(list.getListView(), selectedView, position, id);
            }
        }
        if (mDropDownDismissedOnCompletion && !mPopup.isDropDownAlwaysVisible()) dismissDropDown();
    }

    public boolean isPerformingCompletion() { return mBlockCompletion; }

    public void setText(CharSequence text, boolean filter) {
        if (filter) {
            setText(text);
        } else {
            mBlockCompletion = true;
            setText(text);
            mBlockCompletion = false;
        }
    }

    protected void replaceText(CharSequence text) {
        clearComposingText();
        setText(text);
        // make sure we keep the caret at the end of the text view
        Editable spannable = getText();
        Selection.setSelection(spannable, spannable.length());
    }

    public void onFilterComplete(int count) { updateDropDownForFilter(count); }

    private void updateDropDownForFilter(int count) {
        // Not attached to window, don't update drop-down
        if (getWindowVisibility() == View.GONE) return;
        // Show the results only if the filter is still wanted and the popup can be shown: the
        // filter may be done after the user moved focus away.
        final boolean dropDownAlwaysVisible = mPopup.isDropDownAlwaysVisible();
        final boolean enoughToFilter = enoughToFilter();
        if ((count > 0 || dropDownAlwaysVisible) && enoughToFilter) {
            if (hasFocus() && hasWindowFocus() && mPopupCanBeUpdated) showDropDown();
        } else if (!dropDownAlwaysVisible && isPopupShowing()) {
            dismissDropDown();
            // When the filter text is changed, the first update from the adapter may show an empty
            // count (when the query is being performed on the network). Future updates when some
            // content has been retrieved should still be able to update the list.
            mPopupCanBeUpdated = true;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus && !mPopup.isDropDownAlwaysVisible()) dismissDropDown();
    }

    @Override
    protected void onDisplayHint(int hint) {
        super.onDisplayHint(hint);
        if (hint == INVISIBLE && !mPopup.isDropDownAlwaysVisible()) dismissDropDown();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        // Perform validation if the view is losing focus.
        if (!focused) performValidation();
        if (!focused && !mPopup.isDropDownAlwaysVisible()) dismissDropDown();
    }

    @Override
    protected void onAttachedToWindow() { super.onAttachedToWindow(); }

    @Override
    protected void onDetachedFromWindow() {
        dismissDropDown();
        super.onDetachedFromWindow();
    }

    public void dismissDropDown() {
        mPopup.dismiss();
        mPopupCanBeUpdated = false;
    }

    @Override
    protected boolean setFrame(final int l, int t, final int r, int b) {
        boolean result = super.setFrame(l, t, r, b);
        if (isPopupShowing()) showDropDown();
        return result;
    }

    /** framework-internal (AOSP, hidden). Shows the drop-down as long as there are items. */
    public void showDropDownAfterLayout() { mPopup.postShow(); }

    /** framework-internal (AOSP, hidden). */
    public void ensureImeVisible(boolean visible) {
        mPopup.setInputMethodMode(visible ? ListPopupWindow.INPUT_METHOD_NEEDED
                : ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        if (mPopup.isDropDownAlwaysVisible() || (mFilter != null && enoughToFilter())) showDropDown();
    }

    /** framework-internal (AOSP, hidden). */
    public boolean isInputMethodNotNeeded() { return mPopup.isInputMethodNotNeeded(); }

    public int getInputMethodMode() { return mPopup.getInputMethodMode(); }

    public void setInputMethodMode(int mode) { mPopup.setInputMethodMode(mode); }

    public void showDropDown() {
        if (mPopup.getAnchorView() == null) {
            if (mDropDownAnchorId != View.NO_ID) {
                mPopup.setAnchorView(getRootView().findViewById(mDropDownAnchorId));
            } else {
                mPopup.setAnchorView(this);
            }
        }
        if (!isPopupShowing()) {
            // Make sure the list does not obscure the IME when shown for the first time.
            mPopup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NEEDED);
            mPopup.setListItemExpandMax(EXPAND_MAX);
        }
        mPopup.show();
        mPopup.getListView().setOverScrollMode(View.OVER_SCROLL_ALWAYS);
    }

    /** framework-internal (AOSP, hidden). */
    public void setForceIgnoreOutsideTouch(boolean forceIgnoreOutsideTouch) {
        mPopup.setForceIgnoreOutsideTouch(forceIgnoreOutsideTouch);
    }

    public void setValidator(Validator validator) { mValidator = validator; }

    public Validator getValidator() { return mValidator; }

    public void performValidation() {
        if (mValidator == null) return;
        CharSequence text = getText();
        if (!TextUtils.isEmpty(text) && !mValidator.isValid(text)) setText(mValidator.fixText(text));
    }

    protected Filter getFilter() { return mFilter; }

    @Override
    public CharSequence getAccessibilityClassName() { return AutoCompleteTextView.class.getName(); }

    private class DropDownItemClickListener implements AdapterView.OnItemClickListener {
        public void onItemClick(AdapterView parent, View v, int position, long id) {
            performCompletion(v, position, id);
        }
    }

    /** Checks and fixes the text when the view loses focus. */
    public interface Validator {
        boolean isValid(CharSequence text);

        CharSequence fixText(CharSequence invalidText);
    }

    /** Told when the drop-down closes. */
    public interface OnDismissListener {
        void onDismiss();
    }

    /** Runs the view's own click handling, then the listener the app set. */
    private class PassThroughClickListener implements OnClickListener {
        private View.OnClickListener mWrapped;

        @Override
        public void onClick(View v) {
            onClickImpl();
            if (mWrapped != null) mWrapped.onClick(v);
        }
    }

    /** Updates the drop-down when the adapter changes (posted, the adapter may be mid-update). */
    private static class PopupDataSetObserver extends DataSetObserver {
        // AOSP holds a WeakReference; a strong one is fine here (adapter and view form a cycle the GC traces).
        private final AutoCompleteTextView mView;

        private PopupDataSetObserver(AutoCompleteTextView view) { mView = view; }

        @Override
        public void onChanged() {
            final AutoCompleteTextView textView = mView;
            if (textView != null && textView.mAdapter != null) {
                // This will re-layout, thus resetting mDataChanged, so that the listView click
                // listener can be safely called.
                textView.post(updateRunnable);
            }
        }

        private final Runnable updateRunnable = new Runnable() {
            @Override
            public void run() {
                final AutoCompleteTextView textView = mView;
                if (textView == null) return;
                final ListAdapter adapter = textView.mAdapter;
                if (adapter == null) return;
                textView.updateDropDownForFilter(adapter.getCount());
            }
        };
    }
}
