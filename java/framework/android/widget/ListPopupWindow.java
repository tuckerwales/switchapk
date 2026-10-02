package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;

/**
 * Port of AOSP ListPopupWindow: an adapter's rows in a DropDownListView inside
 * a PopupWindow anchored to a view, sized to its content up to the space
 * available below or above the anchor. Drag-to-open forwarding is not ported.
 */
public class ListPopupWindow {
    private static final int EXPAND_LIST_TIMEOUT = 250;

    public static final int POSITION_PROMPT_ABOVE = 0;
    public static final int POSITION_PROMPT_BELOW = 1;
    public static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;
    public static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
    public static final int INPUT_METHOD_FROM_FOCUSABLE = PopupWindow.INPUT_METHOD_FROM_FOCUSABLE;
    public static final int INPUT_METHOD_NEEDED = PopupWindow.INPUT_METHOD_NEEDED;
    public static final int INPUT_METHOD_NOT_NEEDED = PopupWindow.INPUT_METHOD_NOT_NEEDED;

    private final Context mContext;
    private ListAdapter mAdapter;
    DropDownListView mDropDownList;

    private int mDropDownHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
    private int mDropDownWidth = ViewGroup.LayoutParams.WRAP_CONTENT;
    private int mDropDownHorizontalOffset;
    private int mDropDownVerticalOffset;
    private int mDropDownWindowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;
    private boolean mDropDownVerticalOffsetSet;
    private boolean mOverlapAnchor;
    private boolean mOverlapAnchorSet;
    private int mDropDownGravity = Gravity.NO_GRAVITY;
    private boolean mDropDownAlwaysVisible = false;
    private boolean mForceIgnoreOutsideTouch = false;
    int mListItemExpandMaximum = Integer.MAX_VALUE;

    private View mPromptView;
    private int mPromptPosition = POSITION_PROMPT_ABOVE;
    private DataSetObserver mObserver;
    private View mDropDownAnchorView;
    private Drawable mDropDownListHighlight;
    private AdapterView.OnItemClickListener mItemClickListener;
    private AdapterView.OnItemSelectedListener mItemSelectedListener;

    private final ResizePopupRunnable mResizePopupRunnable = new ResizePopupRunnable();
    private final PopupTouchInterceptor mTouchInterceptor = new PopupTouchInterceptor();
    private final ListSelectorHider mHideSelector = new ListSelectorHider();
    private final Handler mHandler;
    private final Rect mTempRect = new Rect();
    private Rect mEpicenterBounds;
    private boolean mModal;

    PopupWindow mPopup;

    public ListPopupWindow(Context context) { this(context, null, android.R.attr.listPopupWindowStyle, 0); }

    public ListPopupWindow(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.listPopupWindowStyle, 0);
    }

    public ListPopupWindow(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public ListPopupWindow(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        mContext = context;
        mHandler = new Handler(context.getMainLooper());
        final TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.dropDownHorizontalOffset, android.R.attr.dropDownVerticalOffset}, defStyleAttr,
                defStyleRes);
        mDropDownHorizontalOffset = a.getDimensionPixelOffset(0, 0);
        mDropDownVerticalOffset = a.getDimensionPixelOffset(1, 0);
        if (mDropDownVerticalOffset != 0) mDropDownVerticalOffsetSet = true;
        a.recycle();
        mPopup = new PopupWindow(context, attrs, defStyleAttr, defStyleRes);
        mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
    }

    public void setAdapter(ListAdapter adapter) {
        if (mObserver == null) mObserver = new PopupDataSetObserver();
        else if (mAdapter != null) mAdapter.unregisterDataSetObserver(mObserver);
        mAdapter = adapter;
        if (mAdapter != null) adapter.registerDataSetObserver(mObserver);
        if (mDropDownList != null) mDropDownList.setAdapter(mAdapter);
    }

    public void setPromptPosition(int position) { mPromptPosition = position; }

    public int getPromptPosition() { return mPromptPosition; }

    public void setModal(boolean modal) {
        mModal = modal;
        mPopup.setFocusable(modal);
    }

    public boolean isModal() { return mModal; }

    /** framework-internal (hidden in AOSP). */
    public void setForceIgnoreOutsideTouch(boolean forceIgnoreOutsideTouch) {
        mForceIgnoreOutsideTouch = forceIgnoreOutsideTouch;
    }

    /** framework-internal (hidden in AOSP). */
    public void setDropDownAlwaysVisible(boolean dropDownAlwaysVisible) {
        mDropDownAlwaysVisible = dropDownAlwaysVisible;
    }

    /** framework-internal (hidden in AOSP). */
    public boolean isDropDownAlwaysVisible() { return mDropDownAlwaysVisible; }

    public void setSoftInputMode(int mode) { mPopup.setSoftInputMode(mode); }

    public int getSoftInputMode() { return mPopup.getSoftInputMode(); }

    public void setListSelector(Drawable selector) { mDropDownListHighlight = selector; }

    public Drawable getBackground() { return mPopup.getBackground(); }

    public void setBackgroundDrawable(Drawable d) { mPopup.setBackgroundDrawable(d); }

    public void setAnimationStyle(int animationStyle) { mPopup.setAnimationStyle(animationStyle); }

    public int getAnimationStyle() { return mPopup.getAnimationStyle(); }

    public View getAnchorView() { return mDropDownAnchorView; }

    public void setAnchorView(View anchor) { mDropDownAnchorView = anchor; }

    public int getHorizontalOffset() { return mDropDownHorizontalOffset; }

    public void setHorizontalOffset(int offset) { mDropDownHorizontalOffset = offset; }

    public int getVerticalOffset() {
        if (!mDropDownVerticalOffsetSet) return 0;
        return mDropDownVerticalOffset;
    }

    public void setVerticalOffset(int offset) {
        mDropDownVerticalOffset = offset;
        mDropDownVerticalOffsetSet = true;
    }

    public void setEpicenterBounds(Rect bounds) { mEpicenterBounds = bounds != null ? new Rect(bounds) : null; }

    public Rect getEpicenterBounds() { return mEpicenterBounds != null ? new Rect(mEpicenterBounds) : null; }

    public void setDropDownGravity(int gravity) { mDropDownGravity = gravity; }

    public int getWidth() { return mDropDownWidth; }

    public void setWidth(int width) { mDropDownWidth = width; }

    public void setContentWidth(int width) {
        Drawable popupBackground = mPopup.getBackground();
        if (popupBackground != null) {
            popupBackground.getPadding(mTempRect);
            mDropDownWidth = mTempRect.left + mTempRect.right + width;
        } else {
            setWidth(width);
        }
    }

    public int getHeight() { return mDropDownHeight; }

    public void setHeight(int height) {
        if (height < 0 && ViewGroup.LayoutParams.WRAP_CONTENT != height
                && ViewGroup.LayoutParams.MATCH_PARENT != height) {
            if (mContext.getApplicationInfo().targetSdkVersion < 26) {
                height = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else {
                throw new IllegalArgumentException("Invalid height. Must be a positive value, MATCH_PARENT, or"
                        + " WRAP_CONTENT.");
            }
        }
        mDropDownHeight = height;
    }

    public void setWindowLayoutType(int layoutType) { mDropDownWindowLayoutType = layoutType; }

    public void setOnItemClickListener(AdapterView.OnItemClickListener clickListener) {
        mItemClickListener = clickListener;
    }

    public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener selectedListener) {
        mItemSelectedListener = selectedListener;
    }

    public void setPromptView(View prompt) {
        boolean showing = isShowing();
        if (showing) removePromptView();
        mPromptView = prompt;
        if (showing) show();
    }

    public void postShow() { mHandler.post(new Runnable() { public void run() { show(); } }); }

    public void show() {
        // A posted show (AutoCompleteTextView.showDropDownAfterLayout) can run before any anchor
        // was set; there is nothing to position against, so stay hidden instead of crashing.
        if (getAnchorView() == null) return;
        int height = buildDropDown();
        final boolean noInputMethod = isInputMethodNotNeeded();
        mPopup.setAllowScrollingAnchorParent(!noInputMethod);
        mPopup.setWindowLayoutType(mDropDownWindowLayoutType);
        if (mPopup.isShowing()) {
            if (!getAnchorView().isAttachedToWindow()) return;
            final int widthSpec;
            if (mDropDownWidth == ViewGroup.LayoutParams.MATCH_PARENT) widthSpec = -1;
            else if (mDropDownWidth == ViewGroup.LayoutParams.WRAP_CONTENT) widthSpec = getAnchorView().getWidth();
            else widthSpec = mDropDownWidth;
            final int heightSpec;
            if (mDropDownHeight == ViewGroup.LayoutParams.MATCH_PARENT) {
                heightSpec = noInputMethod ? height : ViewGroup.LayoutParams.MATCH_PARENT;
                if (noInputMethod) {
                    mPopup.setWidth(mDropDownWidth == ViewGroup.LayoutParams.MATCH_PARENT
                            ? ViewGroup.LayoutParams.MATCH_PARENT : 0);
                    mPopup.setHeight(0);
                } else {
                    mPopup.setWidth(mDropDownWidth == ViewGroup.LayoutParams.MATCH_PARENT
                            ? ViewGroup.LayoutParams.MATCH_PARENT : 0);
                    mPopup.setHeight(ViewGroup.LayoutParams.MATCH_PARENT);
                }
            } else if (mDropDownHeight == ViewGroup.LayoutParams.WRAP_CONTENT) {
                heightSpec = height;
            } else {
                heightSpec = mDropDownHeight;
            }
            mPopup.setOutsideTouchable(!mForceIgnoreOutsideTouch && !mDropDownAlwaysVisible);
            mPopup.update(getAnchorView(), mDropDownHorizontalOffset, mDropDownVerticalOffset,
                    (widthSpec < 0) ? -1 : widthSpec, (heightSpec < 0) ? -1 : heightSpec);
            mPopup.getContentView().restoreDefaultFocus();
        } else {
            final int widthSpec;
            if (mDropDownWidth == ViewGroup.LayoutParams.MATCH_PARENT) widthSpec = ViewGroup.LayoutParams.MATCH_PARENT;
            else if (mDropDownWidth == ViewGroup.LayoutParams.WRAP_CONTENT) widthSpec = getAnchorView().getWidth();
            else widthSpec = mDropDownWidth;
            final int heightSpec;
            if (mDropDownHeight == ViewGroup.LayoutParams.MATCH_PARENT) heightSpec = ViewGroup.LayoutParams.MATCH_PARENT;
            else if (mDropDownHeight == ViewGroup.LayoutParams.WRAP_CONTENT) heightSpec = height;
            else heightSpec = mDropDownHeight;
            mPopup.setWidth(widthSpec);
            mPopup.setHeight(heightSpec);
            mPopup.setIsClippedToScreen(true);
            mPopup.setOutsideTouchable(!mForceIgnoreOutsideTouch && !mDropDownAlwaysVisible);
            mPopup.setTouchInterceptor(mTouchInterceptor);
            mPopup.setEpicenterBounds(mEpicenterBounds);
            if (mOverlapAnchorSet) mPopup.setOverlapAnchor(mOverlapAnchor);
            mPopup.showAsDropDown(getAnchorView(), mDropDownHorizontalOffset, mDropDownVerticalOffset,
                    mDropDownGravity);
            mDropDownList.setSelection(ListView.INVALID_POSITION);
            if (!mModal || mDropDownList.isInTouchMode()) clearListSelection();
            if (!mModal) mHandler.post(mHideSelector);
        }
    }

    public void dismiss() {
        mPopup.dismiss();
        removePromptView();
        mPopup.setContentView(null);
        mDropDownList = null;
        mHandler.removeCallbacks(mResizePopupRunnable);
    }

    public void setOnDismissListener(PopupWindow.OnDismissListener listener) { mPopup.setOnDismissListener(listener); }

    private void removePromptView() {
        if (mPromptView != null) {
            final ViewParent parent = mPromptView.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(mPromptView);
        }
    }

    public void setInputMethodMode(int mode) { mPopup.setInputMethodMode(mode); }

    public int getInputMethodMode() { return mPopup.getInputMethodMode(); }

    public void setSelection(int position) {
        DropDownListView list = mDropDownList;
        if (isShowing() && list != null) {
            list.setListSelectionHidden(false);
            list.setSelection(position);
            if (list.getChoiceMode() != ListView.CHOICE_MODE_NONE) list.setItemChecked(position, true);
        }
    }

    public void clearListSelection() {
        final DropDownListView list = mDropDownList;
        if (list != null) {
            list.setListSelectionHidden(true);
            list.hideSelector();
            list.requestLayout();
        }
    }

    public boolean isShowing() { return mPopup.isShowing(); }

    public boolean isInputMethodNotNeeded() { return mPopup.getInputMethodMode() == INPUT_METHOD_NOT_NEEDED; }

    public boolean performItemClick(int position) {
        if (isShowing()) {
            if (mItemClickListener != null) {
                final DropDownListView list = mDropDownList;
                final View child = list.getChildAt(position - list.getFirstVisiblePosition());
                final ListAdapter adapter = list.getAdapter();
                mItemClickListener.onItemClick(list, child, position, adapter.getItemId(position));
            }
            return true;
        }
        return false;
    }

    public Object getSelectedItem() { return isShowing() ? mDropDownList.getSelectedItem() : null; }

    public int getSelectedItemPosition() {
        return isShowing() ? mDropDownList.getSelectedItemPosition() : ListView.INVALID_POSITION;
    }

    public long getSelectedItemId() { return isShowing() ? mDropDownList.getSelectedItemId() : ListView.INVALID_ROW_ID; }

    public View getSelectedView() { return isShowing() ? mDropDownList.getSelectedView() : null; }

    public ListView getListView() { return mDropDownList; }

    /** framework-internal (hidden in AOSP). */
    public void setListItemExpandMax(int max) { mListItemExpandMaximum = max; }

    /** framework-internal (hidden in AOSP). */
    public void setOverlapAnchor(boolean overlap) {
        mOverlapAnchorSet = true;
        mOverlapAnchor = overlap;
    }

    DropDownListView createDropDownListView(Context context, boolean hijackFocus) {
        return new DropDownListView(context, hijackFocus);
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isShowing()) {
            if (keyCode != KeyEvent.KEYCODE_SPACE
                    && (mDropDownList.getSelectedItemPosition() >= 0 || !KeyEvent.isConfirmKey(keyCode))) {
                int curIndex = mDropDownList.getSelectedItemPosition();
                final boolean below = !mPopup.isAboveAnchor();
                final ListAdapter adapter = mAdapter;
                int firstItem = Integer.MAX_VALUE;
                int lastItem = Integer.MIN_VALUE;
                if (adapter != null) {
                    firstItem = 0;
                    lastItem = adapter.getCount() - 1;
                }
                if ((below && keyCode == KeyEvent.KEYCODE_DPAD_UP && curIndex <= firstItem)
                        || (!below && keyCode == KeyEvent.KEYCODE_DPAD_DOWN && curIndex >= lastItem)) {
                    clearListSelection();
                    mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
                    show();
                    return true;
                } else {
                    mDropDownList.setListSelectionHidden(false);
                }
                boolean consumed = mDropDownList.onKeyDown(keyCode, event);
                if (consumed) {
                    mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
                    mDropDownList.requestFocusFromTouch();
                    show();
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                        case KeyEvent.KEYCODE_ENTER:
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_DPAD_UP:
                            return true;
                        default:
                            break;
                    }
                } else {
                    if (below && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (curIndex == lastItem) return true;
                    } else if (!below && keyCode == KeyEvent.KEYCODE_DPAD_UP && curIndex == firstItem) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isShowing() && mDropDownList.getSelectedItemPosition() >= 0) {
            boolean consumed = mDropDownList.onKeyUp(keyCode, event);
            if (consumed && KeyEvent.isConfirmKey(keyCode)) dismiss();
            return consumed;
        }
        return false;
    }

    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && isShowing()) {
            final View anchorView = mDropDownAnchorView;
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                KeyEvent.DispatcherState state = anchorView.getKeyDispatcherState();
                if (state != null) state.startTracking(event, this);
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                KeyEvent.DispatcherState state = anchorView.getKeyDispatcherState();
                if (state != null) state.handleUpEvent(event);
                if (event.isTracking() && !event.isCanceled()) {
                    dismiss();
                    return true;
                }
            }
        }
        return false;
    }

    /** TODO drag-to-open forwarding (ForwardingListener) is not ported. */
    public OnTouchListener createDragToOpenListener(View src) { return null; }

    private int buildDropDown() {
        ViewGroup dropDownView;
        int otherHeights = 0;
        if (mDropDownList == null) {
            Context context = mContext;
            mDropDownList = createDropDownListView(context, !mModal);
            if (mDropDownListHighlight != null) mDropDownList.setSelector(mDropDownListHighlight);
            mDropDownList.setAdapter(mAdapter);
            mDropDownList.setOnItemClickListener(mItemClickListener);
            mDropDownList.setFocusable(true);
            mDropDownList.setFocusableInTouchMode(true);
            mDropDownList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position != -1) {
                        DropDownListView dropDownList = mDropDownList;
                        if (dropDownList != null) dropDownList.setListSelectionHidden(false);
                    }
                    if (mItemSelectedListener != null) mItemSelectedListener.onItemSelected(parent, view, position, id);
                }

                public void onNothingSelected(AdapterView<?> parent) {
                    if (mItemSelectedListener != null) mItemSelectedListener.onNothingSelected(parent);
                }
            });
            dropDownView = mDropDownList;
            View hintView = mPromptView;
            if (hintView != null) {
                LinearLayout hintContainer = new LinearLayout(context);
                hintContainer.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
                switch (mPromptPosition) {
                    case POSITION_PROMPT_BELOW:
                        hintContainer.addView(dropDownView, hintParams);
                        hintContainer.addView(hintView);
                        break;
                    case POSITION_PROMPT_ABOVE:
                    default:
                        hintContainer.addView(hintView);
                        hintContainer.addView(dropDownView, hintParams);
                        break;
                }
                final int widthSize;
                final int widthMode;
                if (mDropDownWidth >= 0) {
                    widthMode = MeasureSpec.AT_MOST;
                    widthSize = mDropDownWidth;
                } else {
                    widthMode = MeasureSpec.UNSPECIFIED;
                    widthSize = 0;
                }
                final int widthSpec = MeasureSpec.makeMeasureSpec(widthSize, widthMode);
                hintView.measure(widthSpec, MeasureSpec.UNSPECIFIED);
                hintParams = (LinearLayout.LayoutParams) hintView.getLayoutParams();
                otherHeights = hintView.getMeasuredHeight() + hintParams.topMargin + hintParams.bottomMargin;
                dropDownView = hintContainer;
            }
            mPopup.setContentView(dropDownView);
        } else {
            final View view = mPromptView;
            if (view != null) {
                LinearLayout.LayoutParams hintParams = (LinearLayout.LayoutParams) view.getLayoutParams();
                otherHeights = view.getMeasuredHeight() + hintParams.topMargin + hintParams.bottomMargin;
            }
        }

        int padding;
        final Drawable background = mPopup.getBackground();
        if (background != null) {
            background.getPadding(mTempRect);
            padding = mTempRect.top + mTempRect.bottom;
            if (!mDropDownVerticalOffsetSet) mDropDownVerticalOffset = -mTempRect.top;
        } else {
            mTempRect.setEmpty();
            padding = 0;
        }
        final boolean ignoreBottomDecorations = mPopup.getInputMethodMode() == PopupWindow.INPUT_METHOD_NOT_NEEDED;
        final int maxHeight = mPopup.getMaxAvailableHeight(getAnchorView(), mDropDownVerticalOffset,
                ignoreBottomDecorations);
        if (mDropDownAlwaysVisible || mDropDownHeight == ViewGroup.LayoutParams.MATCH_PARENT) {
            return maxHeight + padding;
        }
        final int childWidthSpec;
        switch (mDropDownWidth) {
            case ViewGroup.LayoutParams.WRAP_CONTENT:
                childWidthSpec = MeasureSpec.makeMeasureSpec(mContext.getResources().getDisplayMetrics().widthPixels
                        - (mTempRect.left + mTempRect.right), MeasureSpec.AT_MOST);
                break;
            case ViewGroup.LayoutParams.MATCH_PARENT:
                childWidthSpec = MeasureSpec.makeMeasureSpec(mContext.getResources().getDisplayMetrics().widthPixels
                        - (mTempRect.left + mTempRect.right), MeasureSpec.EXACTLY);
                break;
            default:
                childWidthSpec = MeasureSpec.makeMeasureSpec(mDropDownWidth, MeasureSpec.EXACTLY);
                break;
        }
        final int listContent = mDropDownList.measureHeightOfChildren(childWidthSpec, 0, ListView.NO_POSITION,
                maxHeight - otherHeights, -1);
        if (listContent > 0) {
            final int listPadding = mDropDownList.getPaddingTop() + mDropDownList.getPaddingBottom();
            otherHeights += padding + listPadding;
        }
        return listContent + otherHeights;
    }

    private class PopupDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            if (isShowing()) show();
        }

        @Override
        public void onInvalidated() { dismiss(); }
    }

    private class ListSelectorHider implements Runnable {
        public void run() { clearListSelection(); }
    }

    private class ResizePopupRunnable implements Runnable {
        public void run() {
            if (mDropDownList != null && mDropDownList.isAttachedToWindow()
                    && mDropDownList.getCount() > mDropDownList.getChildCount()
                    && mDropDownList.getChildCount() <= mListItemExpandMaximum) {
                mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
                show();
            }
        }
    }

    private class PopupTouchInterceptor implements OnTouchListener {
        public boolean onTouch(View v, MotionEvent event) {
            final int action = event.getAction();
            final int x = (int) event.getX();
            final int y = (int) event.getY();
            if (action == MotionEvent.ACTION_DOWN && mPopup != null && mPopup.isShowing()
                    && (x >= 0 && x < mPopup.getWidth() && y >= 0 && y < mPopup.getHeight())) {
                mHandler.postDelayed(mResizePopupRunnable, EXPAND_LIST_TIMEOUT);
            } else if (action == MotionEvent.ACTION_UP) {
                mHandler.removeCallbacks(mResizePopupRunnable);
            }
            return false;
        }
    }
}
