package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.drawable.Drawable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.NumberKeyListener;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import com.android.internal.util.InternalRes;
import java.util.Locale;

/**
 * Port of AOSP NumberPicker. With the Material style (internalLayout number_picker_material) it is
 * the selector wheel: three values drawn by the picker, the middle one covered by an EditText, two
 * selection dividers, fling and adjust scrollers, taps above or below the dividers stepping by one,
 * and D-pad up/down stepping when focused. The legacy layout (number_picker) with increment and
 * decrement buttons is supported too. Accessibility virtual views are not.
 */
public class NumberPicker extends LinearLayout {
    private static final int SELECTOR_WHEEL_ITEM_COUNT = 3;
    private static final long DEFAULT_LONG_PRESS_UPDATE_INTERVAL = 300;
    private static final int SELECTOR_MIDDLE_ITEM_INDEX = SELECTOR_WHEEL_ITEM_COUNT / 2;
    private static final int SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT = 8;
    private static final int SELECTOR_ADJUSTMENT_DURATION_MILLIS = 800;
    private static final int SNAP_SCROLL_DURATION = 300;
    private static final float TOP_AND_BOTTOM_FADING_EDGE_STRENGTH = 0.9f;
    private static final int UNSCALED_DEFAULT_SELECTION_DIVIDER_HEIGHT = 2;
    private static final int UNSCALED_DEFAULT_SELECTION_DIVIDERS_DISTANCE = 48;
    private static final int SIZE_UNSPECIFIED = -1;

    /** Formats the value at an index for display. */
    public interface Formatter {
        public String format(int value);
    }

    /** Told when the value changes. */
    public interface OnValueChangeListener {
        void onValueChange(NumberPicker picker, int oldVal, int newVal);
    }

    /** Told when the scroll state changes. */
    public interface OnScrollListener {
        public static int SCROLL_STATE_IDLE = 0;
        public static int SCROLL_STATE_TOUCH_SCROLL = 1;
        public static int SCROLL_STATE_FLING = 2;

        public void onScrollStateChange(NumberPicker view, int scrollState);
    }

    private final ImageButton mIncrementButton;
    private final ImageButton mDecrementButton;
    private final EditText mInputText;
    private final int mSelectionDividersDistance;
    private final int mMinHeight;
    private final int mMaxHeight;
    private final int mMinWidth;
    private int mMaxWidth;
    private final boolean mComputeMaxWidth;
    private int mTextSize;
    private int mSelectorTextGapHeight;
    private String[] mDisplayedValues;
    private int mMinValue;
    private int mMaxValue;
    private int mValue;
    private OnValueChangeListener mOnValueChangeListener;
    private OnScrollListener mOnScrollListener;
    private Formatter mFormatter;
    private long mLongPressUpdateInterval = DEFAULT_LONG_PRESS_UPDATE_INTERVAL;
    private final SparseArray<String> mSelectorIndexToStringCache = new SparseArray<String>();
    private final int[] mSelectorIndices = new int[SELECTOR_WHEEL_ITEM_COUNT];
    private final Paint mSelectorWheelPaint;
    private int mSelectorElementHeight;
    private int mInitialScrollOffset = Integer.MIN_VALUE;
    private int mCurrentScrollOffset;
    private final Scroller mFlingScroller;
    private final Scroller mAdjustScroller;
    private int mPreviousScrollerY;
    private SetSelectionCommand mSetSelectionCommand;
    private ChangeCurrentByOneFromLongPressCommand mChangeCurrentByOneFromLongPressCommand;
    private BeginSoftInputOnLongPressCommand mBeginSoftInputOnLongPressCommand;
    private float mLastDownEventY;
    private long mLastDownEventTime;
    private float mLastDownOrMoveEventY;
    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;
    private int mMinimumFlingVelocity;
    private int mMaximumFlingVelocity;
    private boolean mWrapSelectorWheel;
    private boolean mWrapSelectorWheelPreferred = true;
    private final int mSolidColor;
    private final boolean mHasSelectorWheel;
    private final Drawable mSelectionDivider;
    private final Drawable mVirtualButtonPressedDrawable;
    private int mSelectionDividerHeight;
    private int mScrollState = OnScrollListener.SCROLL_STATE_IDLE;
    private boolean mIgnoreMoveEvents;
    private boolean mPerformClickOnTap;
    private int mTopSelectionDividerTop;
    private int mBottomSelectionDividerBottom;
    private int mLastHoveredChildVirtualViewId;
    private boolean mIncrementVirtualButtonPressed;
    private boolean mDecrementVirtualButtonPressed;
    private final PressedStateHelper mPressedStateHelper;
    private int mLastHandledDownDpadKeyCode = -1;
    private final boolean mHideWheelUntilFocused;

    /** The EditText inside the picker: it confirms its value when an IME action arrives. */
    public static class CustomEditText extends EditText {
        public CustomEditText(Context context, AttributeSet attrs) { super(context, attrs); }

        @Override
        public void onEditorAction(int actionCode) {
            super.onEditorAction(actionCode);
            if (actionCode == EditorInfo.IME_ACTION_DONE) clearFocus();
        }
    }

    public NumberPicker(Context context) { this(context, null); }

    public NumberPicker(Context context, AttributeSet attrs) { this(context, attrs, android.R.attr.numberPickerStyle); }

    public NumberPicker(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public NumberPicker(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final int defaultLayout = InternalRes.layout("number_picker");
        final TypedArray a = context.obtainStyledAttributes(attrs, InternalRes.attrs(
                "internalLayout", "android:solidColor", "selectionDivider", "android:selectionDividerHeight",
                "selectionDividersDistance", "internalMinHeight", "internalMaxHeight", "internalMinWidth",
                "internalMaxWidth", "virtualButtonPressedDrawable", "hideWheelUntilFocused"),
                defStyleAttr, defStyleRes);
        final int layoutResId = a.getResourceId(0, defaultLayout);
        mHasSelectorWheel = layoutResId != defaultLayout;
        mHideWheelUntilFocused = a.getBoolean(10, false);
        mSolidColor = a.getColor(1, 0);
        final Drawable selectionDivider = a.getDrawable(2);
        if (selectionDivider != null) {
            selectionDivider.setCallback(this);
            selectionDivider.setLayoutDirection(getLayoutDirection());
            if (selectionDivider.isStateful()) selectionDivider.setState(getDrawableState());
        }
        mSelectionDivider = selectionDivider;
        final int defSelectionDividerHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                UNSCALED_DEFAULT_SELECTION_DIVIDER_HEIGHT, getResources().getDisplayMetrics());
        mSelectionDividerHeight = a.getDimensionPixelSize(3, defSelectionDividerHeight);
        final int defSelectionDividerDistance = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                UNSCALED_DEFAULT_SELECTION_DIVIDERS_DISTANCE, getResources().getDisplayMetrics());
        mSelectionDividersDistance = a.getDimensionPixelSize(4, defSelectionDividerDistance);
        mMinHeight = a.getDimensionPixelSize(5, SIZE_UNSPECIFIED);
        mMaxHeight = a.getDimensionPixelSize(6, SIZE_UNSPECIFIED);
        if (mMinHeight != SIZE_UNSPECIFIED && mMaxHeight != SIZE_UNSPECIFIED && mMinHeight > mMaxHeight) {
            throw new IllegalArgumentException("minHeight > maxHeight");
        }
        mMinWidth = a.getDimensionPixelSize(7, SIZE_UNSPECIFIED);
        mMaxWidth = a.getDimensionPixelSize(8, SIZE_UNSPECIFIED);
        if (mMinWidth != SIZE_UNSPECIFIED && mMaxWidth != SIZE_UNSPECIFIED && mMinWidth > mMaxWidth) {
            throw new IllegalArgumentException("minWidth > maxWidth");
        }
        mComputeMaxWidth = (mMaxWidth == SIZE_UNSPECIFIED);
        mVirtualButtonPressedDrawable = a.getDrawable(9);
        a.recycle();

        mPressedStateHelper = new PressedStateHelper();
        // By default LinearLayout does not draw; the selector wheel does.
        setWillNotDraw(!mHasSelectorWheel);

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(layoutResId, this, true);

        OnClickListener onClickListener = new OnClickListener() {
            public void onClick(View v) {
                hideSoftInput();
                mInputText.clearFocus();
                if (v.getId() == InternalRes.viewId("increment")) changeValueByOne(true);
                else changeValueByOne(false);
            }
        };
        OnLongClickListener onLongClickListener = new OnLongClickListener() {
            public boolean onLongClick(View v) {
                mInputText.clearFocus();
                if (v.getId() == InternalRes.viewId("increment")) postChangeCurrentByOneFromLongPress(true, 0);
                else postChangeCurrentByOneFromLongPress(false, 0);
                return true;
            }
        };

        if (!mHasSelectorWheel) {
            mIncrementButton = (ImageButton) findViewById(InternalRes.viewId("increment"));
            mDecrementButton = (ImageButton) findViewById(InternalRes.viewId("decrement"));
        } else {
            mIncrementButton = null;
            mDecrementButton = null;
        }
        if (mIncrementButton != null) {
            mIncrementButton.setOnClickListener(onClickListener);
            mIncrementButton.setOnLongClickListener(onLongClickListener);
        }
        if (mDecrementButton != null) {
            mDecrementButton.setOnClickListener(onClickListener);
            mDecrementButton.setOnLongClickListener(onLongClickListener);
        }

        mInputText = (EditText) findViewById(InternalRes.viewId("numberpicker_input"));
        mInputText.setOnFocusChangeListener(new OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    mInputText.selectAll();
                } else {
                    mInputText.setSelection(0, 0);
                    validateInputTextView(v);
                }
            }
        });
        mInputText.setFilters(new InputFilter[] {new InputTextFilter()});
        mInputText.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        mInputText.setImeOptions(EditorInfo.IME_ACTION_DONE);

        ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = configuration.getScaledMaximumFlingVelocity() / SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT;
        mTextSize = (int) mInputText.getTextSize();

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextAlign(Align.CENTER);
        paint.setTextSize(mTextSize);
        paint.setTypeface(mInputText.getTypeface());
        ColorStateList colors = mInputText.getTextColors();
        int color = colors.getColorForState(ENABLED_STATE_SET, Color.WHITE);
        paint.setColor(color);
        mSelectorWheelPaint = paint;

        mFlingScroller = new Scroller(getContext(), null, true);
        mAdjustScroller = new Scroller(getContext(), new DecelerateInterpolator(2.5f));

        updateInputTextView();

        if (getFocusable() == View.FOCUSABLE_AUTO) {
            setFocusable(View.FOCUSABLE);
            setFocusableInTouchMode(true);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!mHasSelectorWheel) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }
        final int msrdWdth = getMeasuredWidth();
        final int msrdHght = getMeasuredHeight();
        // Input text centered horizontally and vertically.
        final int inptTxtMsrdWdth = mInputText.getMeasuredWidth();
        final int inptTxtMsrdHght = mInputText.getMeasuredHeight();
        final int inptTxtLeft = (msrdWdth - inptTxtMsrdWdth) / 2;
        final int inptTxtTop = (msrdHght - inptTxtMsrdHght) / 2;
        final int inptTxtRight = inptTxtLeft + inptTxtMsrdWdth;
        final int inptTxtBottom = inptTxtTop + inptTxtMsrdHght;
        mInputText.layout(inptTxtLeft, inptTxtTop, inptTxtRight, inptTxtBottom);
        if (changed) {
            initializeSelectorWheel();
            initializeFadingEdges();
            mTopSelectionDividerTop = (getHeight() - mSelectionDividersDistance) / 2 - mSelectionDividerHeight;
            mBottomSelectionDividerBottom = mTopSelectionDividerTop + 2 * mSelectionDividerHeight
                    + mSelectionDividersDistance;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!mHasSelectorWheel) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        // Try greedily to fit the max width and height.
        final int newWidthMeasureSpec = makeMeasureSpec(widthMeasureSpec, mMaxWidth);
        final int newHeightMeasureSpec = makeMeasureSpec(heightMeasureSpec, mMaxHeight);
        super.onMeasure(newWidthMeasureSpec, newHeightMeasureSpec);
        // Flag if we are measured with width or height less than the respective min.
        final int widthSize = resolveSizeAndStateRespectingMinSize(mMinWidth, getMeasuredWidth(), widthMeasureSpec);
        final int heightSize = resolveSizeAndStateRespectingMinSize(mMinHeight, getMeasuredHeight(),
                heightMeasureSpec);
        setMeasuredDimension(widthSize, heightSize);
    }

    /** Moves to the final position of a scroller and snaps the wheel; true if it scrolled. */
    private boolean moveToFinalScrollerPosition(Scroller scroller) {
        scroller.forceFinished(true);
        int amountToScroll = scroller.getFinalY() - scroller.getCurrY();
        int futureScrollOffset = (mCurrentScrollOffset + amountToScroll) % mSelectorElementHeight;
        int overshootAdjustment = mInitialScrollOffset - futureScrollOffset;
        if (overshootAdjustment != 0) {
            if (Math.abs(overshootAdjustment) > mSelectorElementHeight / 2) {
                if (overshootAdjustment > 0) overshootAdjustment -= mSelectorElementHeight;
                else overshootAdjustment += mSelectorElementHeight;
            }
            amountToScroll += overshootAdjustment;
            scrollBy(0, amountToScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!mHasSelectorWheel || !isEnabled()) return false;
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                removeAllCallbacks();
                hideSoftInput();
                mLastDownOrMoveEventY = mLastDownEventY = event.getY();
                mLastDownEventTime = event.getEventTime();
                mIgnoreMoveEvents = false;
                mPerformClickOnTap = false;
                // Handle pressed state before any state change.
                if (mLastDownEventY < mTopSelectionDividerTop) {
                    if (mScrollState == OnScrollListener.SCROLL_STATE_IDLE) {
                        mPressedStateHelper.buttonPressDelayed(PressedStateHelper.BUTTON_DECREMENT);
                    }
                } else if (mLastDownEventY > mBottomSelectionDividerBottom) {
                    if (mScrollState == OnScrollListener.SCROLL_STATE_IDLE) {
                        mPressedStateHelper.buttonPressDelayed(PressedStateHelper.BUTTON_INCREMENT);
                    }
                }
                // Make sure we support flinging inside scrollables.
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (!mFlingScroller.isFinished()) {
                    mFlingScroller.forceFinished(true);
                    mAdjustScroller.forceFinished(true);
                    onScrollerFinished(mFlingScroller);
                    onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                } else if (!mAdjustScroller.isFinished()) {
                    mFlingScroller.forceFinished(true);
                    mAdjustScroller.forceFinished(true);
                    onScrollerFinished(mAdjustScroller);
                } else if (mLastDownEventY < mTopSelectionDividerTop) {
                    postChangeCurrentByOneFromLongPress(false, ViewConfiguration.getLongPressTimeout());
                } else if (mLastDownEventY > mBottomSelectionDividerBottom) {
                    postChangeCurrentByOneFromLongPress(true, ViewConfiguration.getLongPressTimeout());
                } else {
                    mPerformClickOnTap = true;
                    postBeginSoftInputOnLongPressCommand();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || !mHasSelectorWheel) return false;
        if (mVelocityTracker == null) mVelocityTracker = VelocityTracker.obtain();
        mVelocityTracker.addMovement(event);
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                if (mIgnoreMoveEvents) break;
                float currentMoveY = event.getY();
                if (mScrollState != OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    int deltaDownY = (int) Math.abs(currentMoveY - mLastDownEventY);
                    if (deltaDownY > mTouchSlop) {
                        removeAllCallbacks();
                        onScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
                    }
                } else {
                    int deltaMoveY = (int) ((currentMoveY - mLastDownOrMoveEventY));
                    scrollBy(0, deltaMoveY);
                    invalidate();
                }
                mLastDownOrMoveEventY = currentMoveY;
                break;
            }
            case MotionEvent.ACTION_UP: {
                removeBeginSoftInputCommand();
                removeChangeCurrentByOneFromLongPress();
                mPressedStateHelper.cancel();
                VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
                int initialVelocity = (int) velocityTracker.getYVelocity();
                if (Math.abs(initialVelocity) > mMinimumFlingVelocity) {
                    fling(initialVelocity);
                    onScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
                } else {
                    int eventY = (int) event.getY();
                    int deltaMoveY = (int) Math.abs(eventY - mLastDownEventY);
                    long deltaTime = event.getEventTime() - mLastDownEventTime;
                    if (deltaMoveY <= mTouchSlop && deltaTime < ViewConfiguration.getTapTimeout()) {
                        if (mPerformClickOnTap) {
                            mPerformClickOnTap = false;
                            performClick();
                        } else {
                            int selectorIndexOffset = (eventY / mSelectorElementHeight) - SELECTOR_MIDDLE_ITEM_INDEX;
                            if (selectorIndexOffset > 0) {
                                changeValueByOne(true);
                                mPressedStateHelper.buttonTapped(PressedStateHelper.BUTTON_INCREMENT);
                            } else if (selectorIndexOffset < 0) {
                                changeValueByOne(false);
                                mPressedStateHelper.buttonTapped(PressedStateHelper.BUTTON_DECREMENT);
                            }
                        }
                    } else {
                        ensureScrollWheelAdjusted();
                    }
                    onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                }
                mVelocityTracker.recycle();
                mVelocityTracker = null;
                break;
            }
        }
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                removeAllCallbacks();
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                removeAllCallbacks();
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
                if (!mHasSelectorWheel) break;
                switch (event.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        if (mWrapSelectorWheel || ((keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                                ? getValue() < getMaxValue() : getValue() > getMinValue())) {
                            requestFocus();
                            mLastHandledDownDpadKeyCode = keyCode;
                            removeAllCallbacks();
                            if (mFlingScroller.isFinished()) {
                                changeValueByOne(keyCode == KeyEvent.KEYCODE_DPAD_DOWN);
                            }
                            return true;
                        }
                        break;
                    case KeyEvent.ACTION_UP:
                        if (mLastHandledDownDpadKeyCode == keyCode) {
                            mLastHandledDownDpadKeyCode = -1;
                            return true;
                        }
                        break;
                }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                removeAllCallbacks();
                break;
        }
        return super.dispatchTrackballEvent(event);
    }

    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) { return super.dispatchHoverEvent(event); }

    @Override
    public void computeScroll() {
        Scroller scroller = mFlingScroller;
        if (scroller.isFinished()) {
            scroller = mAdjustScroller;
            if (scroller.isFinished()) return;
        }
        scroller.computeScrollOffset();
        int currentScrollerY = scroller.getCurrY();
        if (mPreviousScrollerY == 0) mPreviousScrollerY = scroller.getStartY();
        scrollBy(0, currentScrollerY - mPreviousScrollerY);
        mPreviousScrollerY = currentScrollerY;
        if (scroller.isFinished()) onScrollerFinished(scroller);
        else invalidate();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!mHasSelectorWheel) {
            if (mIncrementButton != null) mIncrementButton.setEnabled(enabled);
            if (mDecrementButton != null) mDecrementButton.setEnabled(enabled);
        }
        mInputText.setEnabled(enabled);
    }

    @Override
    public void scrollBy(int x, int y) {
        int[] selectorIndices = mSelectorIndices;
        int startScrollOffset = mCurrentScrollOffset;
        if (!mWrapSelectorWheel && y > 0 && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] <= mMinValue) {
            mCurrentScrollOffset = mInitialScrollOffset;
            return;
        }
        if (!mWrapSelectorWheel && y < 0 && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] >= mMaxValue) {
            mCurrentScrollOffset = mInitialScrollOffset;
            return;
        }
        mCurrentScrollOffset += y;
        while (mCurrentScrollOffset - mInitialScrollOffset > mSelectorTextGapHeight) {
            mCurrentScrollOffset -= mSelectorElementHeight;
            decrementSelectorIndices(selectorIndices);
            setValueInternal(selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX], true);
            if (!mWrapSelectorWheel && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] <= mMinValue) {
                mCurrentScrollOffset = mInitialScrollOffset;
            }
        }
        while (mCurrentScrollOffset - mInitialScrollOffset < -mSelectorTextGapHeight) {
            mCurrentScrollOffset += mSelectorElementHeight;
            incrementSelectorIndices(selectorIndices);
            setValueInternal(selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX], true);
            if (!mWrapSelectorWheel && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] >= mMaxValue) {
                mCurrentScrollOffset = mInitialScrollOffset;
            }
        }
        if (startScrollOffset != mCurrentScrollOffset) {
            onScrollChanged(0, mCurrentScrollOffset, 0, startScrollOffset);
        }
    }

    @Override
    protected int computeVerticalScrollOffset() { return mCurrentScrollOffset; }

    @Override
    protected int computeVerticalScrollRange() {
        return (mMaxValue - mMinValue + 1) * mSelectorElementHeight;
    }

    @Override
    protected int computeVerticalScrollExtent() { return getHeight(); }

    @Override
    public int getSolidColor() { return mSolidColor; }

    public void setOnValueChangedListener(OnValueChangeListener onValueChangedListener) {
        mOnValueChangeListener = onValueChangedListener;
    }

    public void setOnScrollListener(OnScrollListener onScrollListener) { mOnScrollListener = onScrollListener; }

    public void setFormatter(Formatter formatter) {
        if (formatter == mFormatter) return;
        mFormatter = formatter;
        initializeSelectorWheelIndices();
        updateInputTextView();
    }

    public void setValue(int value) { setValueInternal(value, false); }

    @Override
    public boolean performClick() {
        if (!mHasSelectorWheel) return super.performClick();
        else if (!super.performClick()) showSoftInput();
        return true;
    }

    @Override
    public boolean performLongClick() {
        if (!mHasSelectorWheel) return super.performLongClick();
        else if (!super.performLongClick()) {
            showSoftInput();
            mIgnoreMoveEvents = true;
        }
        return true;
    }

    private void showSoftInput() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            if (mHasSelectorWheel) mInputText.setVisibility(View.VISIBLE);
            mInputText.requestFocus();
            imm.showSoftInput(mInputText, 0);
        }
    }

    private void hideSoftInput() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && imm.isActive(mInputText)) imm.hideSoftInputFromWindow(getWindowToken(), 0);
        if (mHasSelectorWheel) mInputText.setVisibility(View.INVISIBLE);
    }

    private void tryComputeMaxWidth() {
        if (!mComputeMaxWidth) return;
        int maxTextWidth = 0;
        if (mDisplayedValues == null) {
            float maxDigitWidth = 0;
            for (int i = 0; i <= 9; i++) {
                final float digitWidth = mSelectorWheelPaint.measureText(formatNumberWithLocale(i));
                if (digitWidth > maxDigitWidth) maxDigitWidth = digitWidth;
            }
            int numberOfDigits = 0;
            int current = mMaxValue;
            while (current > 0) {
                numberOfDigits++;
                current = current / 10;
            }
            maxTextWidth = (int) (numberOfDigits * maxDigitWidth);
        } else {
            final int valueCount = mDisplayedValues.length;
            for (int i = 0; i < valueCount; i++) {
                final float textWidth = mSelectorWheelPaint.measureText(mDisplayedValues[i]);
                if (textWidth > maxTextWidth) maxTextWidth = (int) textWidth;
            }
        }
        maxTextWidth += mInputText.getPaddingLeft() + mInputText.getPaddingRight();
        if (mMaxWidth != maxTextWidth) {
            if (maxTextWidth > mMinWidth) mMaxWidth = maxTextWidth;
            else mMaxWidth = mMinWidth;
            invalidate();
        }
    }

    public boolean getWrapSelectorWheel() { return mWrapSelectorWheel; }

    public void setWrapSelectorWheel(boolean wrapSelectorWheel) {
        mWrapSelectorWheelPreferred = wrapSelectorWheel;
        updateWrapSelectorWheel();
    }

    /** Wrapping needs more values than wheel slots, whatever was asked for. */
    private void updateWrapSelectorWheel() {
        final boolean wrappingAllowed = (mMaxValue - mMinValue) >= mSelectorIndices.length;
        mWrapSelectorWheel = wrappingAllowed && mWrapSelectorWheelPreferred;
    }

    public void setOnLongPressUpdateInterval(long intervalMillis) { mLongPressUpdateInterval = intervalMillis; }

    public int getValue() { return mValue; }

    public int getMinValue() { return mMinValue; }

    public void setMinValue(int minValue) {
        if (mMinValue == minValue) return;
        if (minValue < 0) throw new IllegalArgumentException("minValue must be >= 0");
        mMinValue = minValue;
        if (mMinValue > mValue) mValue = mMinValue;
        updateWrapSelectorWheel();
        initializeSelectorWheelIndices();
        updateInputTextView();
        tryComputeMaxWidth();
        invalidate();
    }

    public int getMaxValue() { return mMaxValue; }

    public void setMaxValue(int maxValue) {
        if (mMaxValue == maxValue) return;
        if (maxValue < 0) throw new IllegalArgumentException("maxValue must be >= 0");
        mMaxValue = maxValue;
        if (mMaxValue < mValue) mValue = mMaxValue;
        updateWrapSelectorWheel();
        initializeSelectorWheelIndices();
        updateInputTextView();
        tryComputeMaxWidth();
        invalidate();
    }

    public String[] getDisplayedValues() { return mDisplayedValues; }

    public void setDisplayedValues(String[] displayedValues) {
        if (mDisplayedValues == displayedValues) return;
        mDisplayedValues = displayedValues;
        if (mDisplayedValues != null) {
            // Allow text entry rather than strictly numeric entry.
            mInputText.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        } else {
            mInputText.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        }
        updateInputTextView();
        initializeSelectorWheelIndices();
        tryComputeMaxWidth();
    }

    public void setSelectionDividerHeight(int height) {
        mSelectionDividerHeight = height;
        invalidate();
    }

    public int getSelectionDividerHeight() { return mSelectionDividerHeight; }

    @Override
    protected float getTopFadingEdgeStrength() { return TOP_AND_BOTTOM_FADING_EDGE_STRENGTH; }

    @Override
    protected float getBottomFadingEdgeStrength() { return TOP_AND_BOTTOM_FADING_EDGE_STRENGTH; }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeAllCallbacks();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        final Drawable selectionDivider = mSelectionDivider;
        if (selectionDivider != null && selectionDivider.isStateful()
                && selectionDivider.setState(getDrawableState())) {
            invalidateDrawable(selectionDivider);
        }
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mSelectionDivider != null) mSelectionDivider.jumpToCurrentState();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mHasSelectorWheel) {
            super.onDraw(canvas);
            return;
        }
        final boolean showSelectorWheel = mHideWheelUntilFocused ? hasFocus() : true;
        float x = (mRight - mLeft) / 2;
        float y = mCurrentScrollOffset;

        // Draw the virtual buttons' pressed state if needed.
        if (showSelectorWheel && mVirtualButtonPressedDrawable != null
                && mScrollState == OnScrollListener.SCROLL_STATE_IDLE) {
            if (mDecrementVirtualButtonPressed) {
                mVirtualButtonPressedDrawable.setState(PRESSED_STATE_SET);
                mVirtualButtonPressedDrawable.setBounds(0, 0, mRight, mTopSelectionDividerTop);
                mVirtualButtonPressedDrawable.draw(canvas);
            }
            if (mIncrementVirtualButtonPressed) {
                mVirtualButtonPressedDrawable.setState(PRESSED_STATE_SET);
                mVirtualButtonPressedDrawable.setBounds(0, mBottomSelectionDividerBottom, mRight, mBottom);
                mVirtualButtonPressedDrawable.draw(canvas);
            }
        }

        // Draw the selector wheel.
        int[] selectorIndices = mSelectorIndices;
        for (int i = 0; i < selectorIndices.length; i++) {
            int selectorIndex = selectorIndices[i];
            String scrollSelectorValue = mSelectorIndexToStringCache.get(selectorIndex);
            // The middle item is not drawn while the input text covers it, so an IME edit does not show
            // a dimmed old value under the new one.
            if ((showSelectorWheel && i != SELECTOR_MIDDLE_ITEM_INDEX)
                    || (i == SELECTOR_MIDDLE_ITEM_INDEX && mInputText.getVisibility() != VISIBLE)) {
                canvas.drawText(scrollSelectorValue, x, y, mSelectorWheelPaint);
            }
            y += mSelectorElementHeight;
        }

        // Draw the selection dividers.
        if (showSelectorWheel && mSelectionDivider != null) {
            int topOfTopDivider = mTopSelectionDividerTop;
            int bottomOfTopDivider = topOfTopDivider + mSelectionDividerHeight;
            mSelectionDivider.setBounds(0, topOfTopDivider, mRight, bottomOfTopDivider);
            mSelectionDivider.draw(canvas);
            int bottomOfBottomDivider = mBottomSelectionDividerBottom;
            int topOfBottomDivider = bottomOfBottomDivider - mSelectionDividerHeight;
            mSelectionDivider.setBounds(0, topOfBottomDivider, mRight, bottomOfBottomDivider);
            mSelectionDivider.draw(canvas);
        }
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() { return super.getAccessibilityNodeProvider(); }

    public void setTextColor(int color) {
        mSelectorWheelPaint.setColor(color);
        mInputText.setTextColor(color);
        invalidate();
    }

    public int getTextColor() { return mSelectorWheelPaint.getColor(); }

    public void setTextSize(float size) {
        mSelectorWheelPaint.setTextSize(size);
        mInputText.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        invalidate();
    }

    public float getTextSize() { return mSelectorWheelPaint.getTextSize(); }

    // ---------------------------------------------------------------- internals

    private int makeMeasureSpec(int measureSpec, int maxSize) {
        if (maxSize == SIZE_UNSPECIFIED) return measureSpec;
        final int size = MeasureSpec.getSize(measureSpec);
        final int mode = MeasureSpec.getMode(measureSpec);
        switch (mode) {
            case MeasureSpec.EXACTLY:
                return measureSpec;
            case MeasureSpec.AT_MOST:
                return MeasureSpec.makeMeasureSpec(Math.min(size, maxSize), MeasureSpec.EXACTLY);
            case MeasureSpec.UNSPECIFIED:
                return MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.EXACTLY);
            default:
                throw new IllegalArgumentException("Unknown measure mode: " + mode);
        }
    }

    private int resolveSizeAndStateRespectingMinSize(int minSize, int measuredSize, int measureSpec) {
        if (minSize != SIZE_UNSPECIFIED) {
            final int desiredWidth = Math.max(minSize, measuredSize);
            return resolveSizeAndState(desiredWidth, measureSpec, 0);
        }
        return measuredSize;
    }

    private void initializeSelectorWheelIndices() {
        mSelectorIndexToStringCache.clear();
        int[] selectorIndices = mSelectorIndices;
        int current = getValue();
        for (int i = 0; i < mSelectorIndices.length; i++) {
            int selectorIndex = current + (i - SELECTOR_MIDDLE_ITEM_INDEX);
            if (mWrapSelectorWheel) selectorIndex = getWrappedSelectorIndex(selectorIndex);
            selectorIndices[i] = selectorIndex;
            ensureCachedScrollSelectorValue(selectorIndices[i]);
        }
    }

    private void setValueInternal(int current, boolean notifyChange) {
        if (mValue == current) return;
        // Wrap around the values if we go past the start or end.
        if (mWrapSelectorWheel) current = getWrappedSelectorIndex(current);
        else current = Math.max(current, mMinValue);
        current = Math.min(current, mMaxValue);
        int previous = mValue;
        mValue = current;
        // While flinging the text is updated when the wheel stops.
        if (mScrollState != OnScrollListener.SCROLL_STATE_FLING) updateInputTextView();
        if (notifyChange) notifyChange(previous, current);
        initializeSelectorWheelIndices();
        invalidate();
    }

    private void changeValueByOne(boolean increment) {
        if (mHasSelectorWheel) {
            hideSoftInput();
            if (!moveToFinalScrollerPosition(mFlingScroller)) moveToFinalScrollerPosition(mAdjustScroller);
            mPreviousScrollerY = 0;
            if (increment) mFlingScroller.startScroll(0, 0, 0, -mSelectorElementHeight, SNAP_SCROLL_DURATION);
            else mFlingScroller.startScroll(0, 0, 0, mSelectorElementHeight, SNAP_SCROLL_DURATION);
            invalidate();
        } else {
            if (increment) setValueInternal(mValue + 1, true);
            else setValueInternal(mValue - 1, true);
        }
    }

    private void initializeSelectorWheel() {
        initializeSelectorWheelIndices();
        int[] selectorIndices = mSelectorIndices;
        int totalTextHeight = selectorIndices.length * mTextSize;
        float totalTextGapHeight = (mBottom - mTop) - totalTextHeight;
        float textGapCount = selectorIndices.length;
        mSelectorTextGapHeight = (int) (totalTextGapHeight / textGapCount + 0.5f);
        mSelectorElementHeight = mTextSize + mSelectorTextGapHeight;
        // The middle item sits exactly where the input text draws its text.
        int editTextTextPosition = mInputText.getBaseline() + mInputText.getTop();
        mInitialScrollOffset = editTextTextPosition - (mSelectorElementHeight * SELECTOR_MIDDLE_ITEM_INDEX);
        mCurrentScrollOffset = mInitialScrollOffset;
        updateInputTextView();
    }

    private void initializeFadingEdges() {
        setVerticalFadingEdgeEnabled(true);
        setFadingEdgeLength((mBottom - mTop - mTextSize) / 2);
    }

    private void onScrollerFinished(Scroller scroller) {
        if (scroller == mFlingScroller) {
            ensureScrollWheelAdjusted();
            updateInputTextView();
            onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
        } else {
            if (mScrollState != OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) updateInputTextView();
        }
    }

    private void onScrollStateChange(int scrollState) {
        if (mScrollState == scrollState) return;
        mScrollState = scrollState;
        if (mOnScrollListener != null) mOnScrollListener.onScrollStateChange(this, scrollState);
    }

    private void fling(int velocityY) {
        mPreviousScrollerY = 0;
        if (velocityY > 0) mFlingScroller.fling(0, 0, 0, velocityY, 0, 0, 0, Integer.MAX_VALUE);
        else mFlingScroller.fling(0, Integer.MAX_VALUE, 0, velocityY, 0, 0, 0, Integer.MAX_VALUE);
        invalidate();
    }

    private int getWrappedSelectorIndex(int selectorIndex) {
        if (selectorIndex > mMaxValue) {
            return mMinValue + (selectorIndex - mMaxValue) % (mMaxValue - mMinValue) - 1;
        } else if (selectorIndex < mMinValue) {
            return mMaxValue - (mMinValue - selectorIndex) % (mMaxValue - mMinValue) + 1;
        }
        return selectorIndex;
    }

    private void incrementSelectorIndices(int[] selectorIndices) {
        for (int i = 0; i < selectorIndices.length - 1; i++) selectorIndices[i] = selectorIndices[i + 1];
        int nextScrollSelectorIndex = selectorIndices[selectorIndices.length - 2] + 1;
        if (mWrapSelectorWheel && nextScrollSelectorIndex > mMaxValue) nextScrollSelectorIndex = mMinValue;
        selectorIndices[selectorIndices.length - 1] = nextScrollSelectorIndex;
        ensureCachedScrollSelectorValue(nextScrollSelectorIndex);
    }

    private void decrementSelectorIndices(int[] selectorIndices) {
        for (int i = selectorIndices.length - 1; i > 0; i--) selectorIndices[i] = selectorIndices[i - 1];
        int nextScrollSelectorIndex = selectorIndices[1] - 1;
        if (mWrapSelectorWheel && nextScrollSelectorIndex < mMinValue) nextScrollSelectorIndex = mMaxValue;
        selectorIndices[0] = nextScrollSelectorIndex;
        ensureCachedScrollSelectorValue(nextScrollSelectorIndex);
    }

    private void ensureCachedScrollSelectorValue(int selectorIndex) {
        SparseArray<String> cache = mSelectorIndexToStringCache;
        String scrollSelectorValue = cache.get(selectorIndex);
        if (scrollSelectorValue != null) return;
        if (selectorIndex < mMinValue || selectorIndex > mMaxValue) {
            scrollSelectorValue = "";
        } else if (mDisplayedValues != null) {
            int displayedValueIndex = selectorIndex - mMinValue;
            scrollSelectorValue = mDisplayedValues[displayedValueIndex];
        } else {
            scrollSelectorValue = formatNumber(selectorIndex);
        }
        cache.put(selectorIndex, scrollSelectorValue);
    }

    private String formatNumber(int value) {
        return (mFormatter != null) ? mFormatter.format(value) : formatNumberWithLocale(value);
    }

    private void validateInputTextView(View v) {
        String str = String.valueOf(((TextView) v).getText());
        if (TextUtils.isEmpty(str)) {
            // Restore to the old value as we don't allow empty values.
            updateInputTextView();
        } else {
            // Check the new value and ensure it's in range.
            int current = getSelectedPos(str.toString());
            setValueInternal(current, true);
        }
    }

    private boolean updateInputTextView() {
        // Display the displayed value when there is one, else the formatted number.
        String text = (mDisplayedValues == null) ? formatNumber(mValue) : mDisplayedValues[mValue - mMinValue];
        if (!TextUtils.isEmpty(text)) {
            CharSequence beforeText = mInputText.getText();
            if (!text.equals(beforeText.toString())) {
                mInputText.setText(text);
                return true;
            }
        }
        return false;
    }

    private void notifyChange(int previous, int current) {
        if (mOnValueChangeListener != null) mOnValueChangeListener.onValueChange(this, previous, mValue);
    }

    private void postChangeCurrentByOneFromLongPress(boolean increment, long delayMillis) {
        if (mChangeCurrentByOneFromLongPressCommand == null) {
            mChangeCurrentByOneFromLongPressCommand = new ChangeCurrentByOneFromLongPressCommand();
        } else {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand);
        }
        mChangeCurrentByOneFromLongPressCommand.setStep(increment);
        postDelayed(mChangeCurrentByOneFromLongPressCommand, delayMillis);
    }

    private void removeChangeCurrentByOneFromLongPress() {
        if (mChangeCurrentByOneFromLongPressCommand != null) removeCallbacks(mChangeCurrentByOneFromLongPressCommand);
    }

    private void postBeginSoftInputOnLongPressCommand() {
        if (mBeginSoftInputOnLongPressCommand == null) {
            mBeginSoftInputOnLongPressCommand = new BeginSoftInputOnLongPressCommand();
        } else {
            removeCallbacks(mBeginSoftInputOnLongPressCommand);
        }
        postDelayed(mBeginSoftInputOnLongPressCommand, ViewConfiguration.getLongPressTimeout());
    }

    private void removeBeginSoftInputCommand() {
        if (mBeginSoftInputOnLongPressCommand != null) removeCallbacks(mBeginSoftInputOnLongPressCommand);
    }

    private void removeAllCallbacks() {
        if (mChangeCurrentByOneFromLongPressCommand != null) removeCallbacks(mChangeCurrentByOneFromLongPressCommand);
        if (mSetSelectionCommand != null) mSetSelectionCommand.cancel();
        if (mBeginSoftInputOnLongPressCommand != null) removeCallbacks(mBeginSoftInputOnLongPressCommand);
        mPressedStateHelper.cancel();
    }

    private int getSelectedPos(String value) {
        if (mDisplayedValues == null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Ignore as if it's not a number we don't care.
            }
        } else {
            for (int i = 0; i < mDisplayedValues.length; i++) {
                // Don't force the user to type in jan when ja will do.
                value = value.toLowerCase();
                if (mDisplayedValues[i].toLowerCase().startsWith(value)) return mMinValue + i;
            }
            // The user might have typed in a number into the month field i.e. 10 instead of OCT.
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Ignore as if it's not a number we don't care.
            }
        }
        return mMinValue;
    }

    private void postSetSelectionCommand(int selectionStart, int selectionEnd) {
        if (mSetSelectionCommand == null) mSetSelectionCommand = new SetSelectionCommand(mInputText);
        mSetSelectionCommand.post(selectionStart, selectionEnd);
    }

    private boolean ensureScrollWheelAdjusted() {
        // Adjust to the closest value.
        int deltaY = mInitialScrollOffset - mCurrentScrollOffset;
        if (deltaY != 0) {
            mPreviousScrollerY = 0;
            if (Math.abs(deltaY) > mSelectorElementHeight / 2) {
                deltaY += (deltaY > 0) ? -mSelectorElementHeight : mSelectorElementHeight;
            }
            mAdjustScroller.startScroll(0, 0, 0, deltaY, SELECTOR_ADJUSTMENT_DURATION_MILLIS);
            invalidate();
            return true;
        }
        return false;
    }

    private static String formatNumberWithLocale(int value) {
        return String.format(Locale.getDefault(), "%d", value);
    }

    /** Filters typed input to digits, or to prefixes of the displayed values. */
    class InputTextFilter extends NumberKeyListener {
        public int getInputType() { return InputType.TYPE_CLASS_TEXT; }

        @Override
        protected char[] getAcceptedChars() { return DIGIT_CHARACTERS; }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            // We don't know what the output will be, so always cancel any pending set selection command.
            if (mSetSelectionCommand != null) mSetSelectionCommand.cancel();
            if (mDisplayedValues == null) {
                CharSequence filtered = super.filter(source, start, end, dest, dstart, dend);
                if (filtered == null) filtered = source.subSequence(start, end);
                String result = String.valueOf(dest.subSequence(0, dstart)) + filtered
                        + dest.subSequence(dend, dest.length());
                if ("".equals(result)) return result;
                int val;
                try {
                    val = Integer.parseInt(result);
                } catch (NumberFormatException e) {
                    return "";
                }
                // Ensure the user can't type in a value greater than the max allowed.
                if (val > mMaxValue || result.length() > String.valueOf(mMaxValue).length()) return "";
                return filtered;
            } else {
                CharSequence filtered = String.valueOf(source.subSequence(start, end));
                if (TextUtils.isEmpty(filtered)) return "";
                String result = String.valueOf(dest.subSequence(0, dstart)) + filtered
                        + dest.subSequence(dend, dest.length());
                String str = String.valueOf(result).toLowerCase();
                for (String val : mDisplayedValues) {
                    String valLowerCase = val.toLowerCase();
                    if (valLowerCase.startsWith(str)) {
                        postSetSelectionCommand(result.length(), val.length());
                        return val.subSequence(dstart, val.length());
                    }
                }
                return "";
            }
        }
    }

    private static final char[] DIGIT_CHARACTERS = new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

    /** Shows the pressed state of the virtual increment and decrement areas. */
    class PressedStateHelper implements Runnable {
        public static final int BUTTON_INCREMENT = 1;
        public static final int BUTTON_DECREMENT = 2;
        private final int MODE_PRESS = 1;
        private final int MODE_TAPPED = 2;
        private int mManagedButton;
        private int mMode;

        public void cancel() {
            mMode = 0;
            mManagedButton = 0;
            NumberPicker.this.removeCallbacks(this);
            if (mIncrementVirtualButtonPressed) {
                mIncrementVirtualButtonPressed = false;
                invalidate(0, mBottomSelectionDividerBottom, mRight, mBottom);
            }
            if (mDecrementVirtualButtonPressed) {
                mDecrementVirtualButtonPressed = false;
                invalidate(0, 0, mRight, mTopSelectionDividerTop);
            }
        }

        public void buttonPressDelayed(int button) {
            cancel();
            mMode = MODE_PRESS;
            mManagedButton = button;
            NumberPicker.this.postDelayed(this, ViewConfiguration.getTapTimeout());
        }

        public void buttonTapped(int button) {
            cancel();
            mMode = MODE_TAPPED;
            mManagedButton = button;
            NumberPicker.this.post(this);
        }

        public void run() {
            switch (mMode) {
                case MODE_PRESS:
                    switch (mManagedButton) {
                        case BUTTON_INCREMENT:
                            mIncrementVirtualButtonPressed = true;
                            invalidate(0, mBottomSelectionDividerBottom, mRight, mBottom);
                            break;
                        case BUTTON_DECREMENT:
                            mDecrementVirtualButtonPressed = true;
                            invalidate(0, 0, mRight, mTopSelectionDividerTop);
                            break;
                    }
                    break;
                case MODE_TAPPED:
                    switch (mManagedButton) {
                        case BUTTON_INCREMENT:
                            if (!mIncrementVirtualButtonPressed) {
                                NumberPicker.this.postDelayed(this, ViewConfiguration.getPressedStateDuration());
                            }
                            mIncrementVirtualButtonPressed ^= true;
                            invalidate(0, mBottomSelectionDividerBottom, mRight, mBottom);
                            break;
                        case BUTTON_DECREMENT:
                            if (!mDecrementVirtualButtonPressed) {
                                NumberPicker.this.postDelayed(this, ViewConfiguration.getPressedStateDuration());
                            }
                            mDecrementVirtualButtonPressed ^= true;
                            invalidate(0, 0, mRight, mTopSelectionDividerTop);
                            break;
                    }
                    break;
            }
        }
    }

    /** Sets the input text selection once the filter has replaced the text. */
    private static class SetSelectionCommand implements Runnable {
        private final EditText mInputText;
        private int mSelectionStart;
        private int mSelectionEnd;
        private boolean mPosted;

        public SetSelectionCommand(EditText inputText) { mInputText = inputText; }

        public void post(int selectionStart, int selectionEnd) {
            mSelectionStart = selectionStart;
            mSelectionEnd = selectionEnd;
            if (!mPosted) {
                mInputText.post(this);
                mPosted = true;
            }
        }

        public void cancel() {
            if (mPosted) {
                mInputText.removeCallbacks(this);
                mPosted = false;
            }
        }

        public void run() {
            mPosted = false;
            int length = mInputText.getText().length();
            mInputText.setSelection(Math.min(mSelectionStart, length), Math.min(mSelectionEnd, length));
        }
    }

    /** Repeats the step while a long press holds. */
    class ChangeCurrentByOneFromLongPressCommand implements Runnable {
        private boolean mIncrement;

        private void setStep(boolean increment) { mIncrement = increment; }

        public void run() {
            changeValueByOne(mIncrement);
            postDelayed(this, mLongPressUpdateInterval);
        }
    }

    /** Opens the soft input when the middle value is long pressed. */
    class BeginSoftInputOnLongPressCommand implements Runnable {
        public void run() {
            performLongClick();
        }
    }
}
