package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Region;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.MathUtils;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import com.android.internal.util.InternalRes;
import java.util.Calendar;
import java.util.Locale;

/**
 * framework-internal. View to show a clock circle picker (with one or two
 * picking circles) (AOSP port). The hours-to-minutes crossfade runs on a
 * frame callback with an accelerate/decelerate curve. The accessibility
 * touch helper is not ported.
 */
public class RadialTimePickerView extends View {
    private static final String TAG = "RadialTimePickerView";

    public static final int HOURS = 0;
    public static final int MINUTES = 1;

    private static final int HOURS_INNER = 2;

    private static final int SELECTOR_CIRCLE = 0;
    private static final int SELECTOR_DOT = 1;
    private static final int SELECTOR_LINE = 2;

    private static final int AM = 0;
    private static final int PM = 1;

    private static final int HOURS_IN_CIRCLE = 12;
    private static final int MINUTES_IN_CIRCLE = 60;
    private static final int DEGREES_FOR_ONE_HOUR = 360 / HOURS_IN_CIRCLE;
    private static final int DEGREES_FOR_ONE_MINUTE = 360 / MINUTES_IN_CIRCLE;

    private static final int[] HOURS_NUMBERS = {12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    private static final int[] HOURS_NUMBERS_24 = {0, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23};
    private static final int[] MINUTES_NUMBERS = {0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55};

    private static final int ANIM_DURATION_NORMAL = 500;
    private static final int ANIM_DURATION_TOUCH = 60;

    private static final int[] SNAP_PREFER_30S_MAP = new int[361];

    private static final int NUM_POSITIONS = 12;
    private static final float[] COS_30 = new float[NUM_POSITIONS];
    private static final float[] SIN_30 = new float[NUM_POSITIONS];

    /** "Something is wrong" color used when a color attribute is missing. */
    private static final int MISSING_COLOR = Color.MAGENTA;

    static {
        // Prepare mapping to snap touchable degrees to selectable degrees.
        preparePrefer30sMap();

        final double increment = 2.0 * Math.PI / NUM_POSITIONS;
        double angle = Math.PI / 2.0;
        for (int i = 0; i < NUM_POSITIONS; i++) {
            COS_30[i] = (float) Math.cos(angle);
            SIN_30[i] = (float) Math.sin(angle);
            angle += increment;
        }
    }

    private final String[] mHours12Texts = new String[12];
    private final String[] mOuterHours24Texts = new String[12];
    private final String[] mInnerHours24Texts = new String[12];
    private final String[] mMinutesTexts = new String[12];

    private final Paint[] mPaint = new Paint[2];
    private final Paint mPaintCenter = new Paint();
    private final Paint[] mPaintSelector = new Paint[3];
    private final Paint mPaintBackground = new Paint();

    private final Typeface mTypeface;

    private final ColorStateList[] mTextColor = new ColorStateList[3];
    private final int[] mTextSize = new int[3];
    private final int[] mTextInset = new int[3];

    private final float[][] mOuterTextX = new float[2][12];
    private final float[][] mOuterTextY = new float[2][12];

    private final float[] mInnerTextX = new float[12];
    private final float[] mInnerTextY = new float[12];

    private final int[] mSelectionDegrees = new int[2];

    private final Path mSelectorPath = new Path();

    private boolean mIs24HourMode;
    private boolean mShowHours;

    /** The running hours/minutes crossfade, in place of AOSP's ObjectAnimator. */
    private boolean mAnimating;
    private long mAnimStart;
    private long mAnimDuration;
    private float mAnimFrom;
    private float mAnimTo;
    private float mHoursToMinutes;

    /**
     * When in 24-hour mode, indicates that the current hour is between
     * 1 and 12 (inclusive).
     */
    private boolean mIsOnInnerCircle;

    private int mSelectorRadius;
    private int mSelectorStroke;
    private int mSelectorDotRadius;
    private int mCenterDotRadius;

    private int mSelectorColor;
    private int mSelectorDotColor;

    private int mXCenter;
    private int mYCenter;
    private int mCircleRadius;

    private int mMinDistForInnerNumber;
    private int mMaxDistForOuterNumber;
    private int mHalfwayDist;

    private String[] mOuterTextHours;
    private String[] mInnerTextHours;
    private String[] mMinutesText;

    private int mAmOrPm;

    private float mDisabledAlpha;

    private OnValueSelectedListener mListener;

    private boolean mInputEnabled = true;

    interface OnValueSelectedListener {
        /**
         * Called when the selected value at a given picker index has changed.
         *
         * @param pickerType the type of value that has changed, one of {@link #MINUTES} or
         *                   {@link #HOURS}
         * @param newValue the new value as minute in hour (0-59) or hour in
         *                 day (0-23)
         * @param autoAdvance when the picker type is {@link #HOURS},
         *                    {@code true} to switch to the {@link #MINUTES}
         *                    picker or {@code false} to stay on the current
         *                    picker. No effect when picker type is
         *                    {@link #MINUTES}.
         */
        void onValueSelected(int pickerType, int newValue, boolean autoAdvance);
    }

    /**
     * Split up the 360 degrees of the circle among the 60 selectable values. Assigns a larger
     * selectable area to each of the 12 visible values, such that the ratio of space apportioned
     * to a visible value : space apportioned to a non-visible value will be 14 : 4.
     * E.g. the output of 30 degrees should have a higher range of input associated with it than
     * the output of 24 degrees, because 30 degrees corresponds to a visible number on the clock
     * circle (5 on the minutes, 1 or 13 on the hours).
     */
    private static void preparePrefer30sMap() {
        // We'll split up the visible output and the non-visible output such that each visible
        // output will correspond to a range of 14 associated input degrees, and each non-visible
        // output will correspond to a range of 4 associate input degrees, so visible numbers
        // are more than 3 times easier to get than non-visible numbers:
        // {354-359,0-7}:0, {8-11}:6, {12-15}:12, {16-19}:18, {20-23}:24, {24-37}:30, etc.
        //
        // If an output of 30 degrees should correspond to a range of 14 associated degrees, then
        // we'll need any input between 24 - 37 to snap to 30. Working out from there, 20-23 should
        // snap to 24, while 38-41 should snap to 36. This is somewhat counter-intuitive, that you
        // can be touching 36 degrees but have the selection snapped to 30 degrees; however, this
        // inconsistency isn't noticeable at such fine-grained degrees, and it affords us the
        // ability to aggressively prefer the visible values by a factor of more than 3:1, which
        // greatly contributes to the selectability of these values.

        // The first output is 0, and each following output will increment by 6 {0, 6, 12, ...}.
        int snappedOutputDegrees = 0;
        // Count of how many inputs we've designated to the specified output.
        int count = 1;
        // How many input we expect for a specified output. This will be 14 for output divisible
        // by 30, and 4 for the remaining output. We'll special case the outputs of 0 and 360, so
        // the caller can decide which they need.
        int expectedCount = 8;
        // Iterate through the input.
        for (int degrees = 0; degrees < 361; degrees++) {
            // Save the input-output mapping.
            SNAP_PREFER_30S_MAP[degrees] = snappedOutputDegrees;
            // If this is the last input for the specified output, calculate the next output and
            // the next expected count.
            if (count == expectedCount) {
                snappedOutputDegrees += 6;
                if (snappedOutputDegrees == 360) {
                    expectedCount = 7;
                } else if (snappedOutputDegrees % 30 == 0) {
                    expectedCount = 14;
                } else {
                    expectedCount = 4;
                }
                count = 1;
            } else {
                count++;
            }
        }
    }

    /**
     * Returns mapping of any input degrees (0 to 360) to one of 60 selectable output degrees,
     * where the degrees corresponding to visible numbers (i.e. those divisible by 30) will be
     * weighted heavier than the degrees corresponding to non-visible numbers.
     */
    private static int snapPrefer30s(int degrees) {
        if (SNAP_PREFER_30S_MAP == null) {
            return -1;
        }
        return SNAP_PREFER_30S_MAP[degrees];
    }

    /**
     * Returns mapping of any input degrees (0 to 360) to one of 12 visible output degrees (all
     * multiples of 30), where the input will be "snapped" to the closest visible degrees.
     *
     * @param degrees The input degrees
     * @param forceHigherOrLower The output may be forced to either the higher or lower step, or may
     * be allowed to snap to whichever is closer. Use 1 to force strictly higher, -1 to force
     * strictly lower, and 0 to snap to the closer one.
     * @return output degrees, will be a multiple of 30
     */
    private static int snapOnly30s(int degrees, int forceHigherOrLower) {
        final int stepSize = DEGREES_FOR_ONE_HOUR;
        int floor = (degrees / stepSize) * stepSize;
        final int ceiling = floor + stepSize;
        if (forceHigherOrLower == 1) {
            degrees = ceiling;
        } else if (forceHigherOrLower == -1) {
            if (degrees == floor) {
                floor -= stepSize;
            }
            degrees = floor;
        } else {
            if ((degrees - floor) < (ceiling - degrees)) {
                degrees = floor;
            } else {
                degrees = ceiling;
            }
        }
        return degrees;
    }

    public RadialTimePickerView(Context context) {
        this(context, null);
    }

    public RadialTimePickerView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.timePickerStyle);
    }

    public RadialTimePickerView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RadialTimePickerView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs);

        applyAttributes(attrs, defStyleAttr, defStyleRes);

        // Pull disabled alpha from theme.
        final TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.disabledAlpha, outValue, true);
        mDisabledAlpha = outValue.getFloat();

        mTypeface = Typeface.create("sans-serif", Typeface.NORMAL);

        mPaint[HOURS] = new Paint();
        mPaint[HOURS].setAntiAlias(true);
        mPaint[HOURS].setTextAlign(Paint.Align.CENTER);

        mPaint[MINUTES] = new Paint();
        mPaint[MINUTES].setAntiAlias(true);
        mPaint[MINUTES].setTextAlign(Paint.Align.CENTER);

        mPaintCenter.setAntiAlias(true);

        mPaintSelector[SELECTOR_CIRCLE] = new Paint();
        mPaintSelector[SELECTOR_CIRCLE].setAntiAlias(true);

        mPaintSelector[SELECTOR_DOT] = new Paint();
        mPaintSelector[SELECTOR_DOT].setAntiAlias(true);

        mPaintSelector[SELECTOR_LINE] = new Paint();
        mPaintSelector[SELECTOR_LINE].setAntiAlias(true);
        mPaintSelector[SELECTOR_LINE].setStrokeWidth(2);

        mPaintBackground.setAntiAlias(true);

        final Resources res = getResources();
        mSelectorRadius = res.getDimensionPixelSize(InternalRes.dimen("timepicker_selector_radius"));
        mSelectorStroke = res.getDimensionPixelSize(InternalRes.dimen("timepicker_selector_stroke"));
        mSelectorDotRadius = res.getDimensionPixelSize(InternalRes.dimen("timepicker_selector_dot_radius"));
        mCenterDotRadius = res.getDimensionPixelSize(InternalRes.dimen("timepicker_center_dot_radius"));

        mTextSize[HOURS] = res.getDimensionPixelSize(InternalRes.dimen("timepicker_text_size_normal"));
        mTextSize[MINUTES] = res.getDimensionPixelSize(InternalRes.dimen("timepicker_text_size_normal"));
        mTextSize[HOURS_INNER] = res.getDimensionPixelSize(InternalRes.dimen("timepicker_text_size_inner"));

        mTextInset[HOURS] = res.getDimensionPixelSize(InternalRes.dimen("timepicker_text_inset_normal"));
        mTextInset[MINUTES] = res.getDimensionPixelSize(InternalRes.dimen("timepicker_text_inset_normal"));
        mTextInset[HOURS_INNER] = res.getDimensionPixelSize(InternalRes.dimen("timepicker_text_inset_inner"));

        mShowHours = true;
        mHoursToMinutes = HOURS;
        mIs24HourMode = false;
        mAmOrPm = AM;

        if (getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        initHoursAndMinutesText();
        initData();

        // Initial values
        final Calendar calendar = Calendar.getInstance(Locale.getDefault());
        final int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        final int currentMinute = calendar.get(Calendar.MINUTE);

        setCurrentHourInternal(currentHour, false, false);
        setCurrentMinuteInternal(currentMinute, false);

        setHapticFeedbackEnabled(true);
    }

    void applyAttributes(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        final Context context = getContext();
        final TypedArray a = getContext().obtainStyledAttributes(attrs, InternalRes.attrs(
                "android:numbersTextColor", "android:numbersInnerTextColor",
                "android:numbersSelectorColor", "android:numbersBackgroundColor"),
                defStyleAttr, defStyleRes);

        final ColorStateList numbersTextColor = a.getColorStateList(0);
        final ColorStateList numbersInnerTextColor = a.getColorStateList(1);
        mTextColor[HOURS] = numbersTextColor == null
                ? ColorStateList.valueOf(MISSING_COLOR) : numbersTextColor;
        mTextColor[HOURS_INNER] = numbersInnerTextColor == null
                ? ColorStateList.valueOf(MISSING_COLOR) : numbersInnerTextColor;
        mTextColor[MINUTES] = mTextColor[HOURS];

        // Set up various colors derived from the selector "activated" state.
        final ColorStateList selectorColors = a.getColorStateList(2);
        final int selectorActivatedColor;
        if (selectorColors != null) {
            final int[] stateSetEnabledActivated = StateSet.get(
                    StateSet.VIEW_STATE_ENABLED | StateSet.VIEW_STATE_ACTIVATED);
            selectorActivatedColor = selectorColors.getColorForState(
                    stateSetEnabledActivated, 0);
        } else {
            selectorActivatedColor = MISSING_COLOR;
        }

        mPaintCenter.setColor(selectorActivatedColor);

        final int[] stateSetActivated = StateSet.get(
                StateSet.VIEW_STATE_ENABLED | StateSet.VIEW_STATE_ACTIVATED);

        mSelectorColor = selectorActivatedColor;
        mSelectorDotColor = mTextColor[HOURS].getColorForState(stateSetActivated, 0);

        mPaintBackground.setColor(a.getColor(3, context.getColor(
                InternalRes.id("color", "timepicker_default_numbers_background_color_material"))));

        a.recycle();
    }

    public void initialize(int hour, int minute, boolean is24HourMode) {
        if (mIs24HourMode != is24HourMode) {
            mIs24HourMode = is24HourMode;
            initData();
        }

        setCurrentHourInternal(hour, false, false);
        setCurrentMinuteInternal(minute, false);
    }

    public void setCurrentItemShowing(int item, boolean animate) {
        switch (item) {
            case HOURS:
                showHours(animate);
                break;
            case MINUTES:
                showMinutes(animate);
                break;
            default:
                Log.e(TAG, "ClockView does not support showing item " + item);
        }
    }

    public int getCurrentItemShowing() {
        return mShowHours ? HOURS : MINUTES;
    }

    public void setOnValueSelectedListener(OnValueSelectedListener listener) {
        mListener = listener;
    }

    /**
     * Sets the current hour in 24-hour time.
     *
     * @param hour the current hour between 0 and 23 (inclusive)
     */
    public void setCurrentHour(int hour) {
        setCurrentHourInternal(hour, true, false);
    }

    /**
     * Sets the current hour.
     *
     * @param hour The current hour
     * @param callback Whether the value listener should be invoked
     * @param autoAdvance Whether the listener should auto-advance to the next
     *                    selection mode, e.g. hour to minutes
     */
    private void setCurrentHourInternal(int hour, boolean callback, boolean autoAdvance) {
        final int degrees = (hour % 12) * DEGREES_FOR_ONE_HOUR;
        mSelectionDegrees[HOURS] = degrees;

        // 0 is 12 AM (midnight) and 12 is 12 PM (noon).
        final int amOrPm = (hour == 0 || (hour % 24) < 12) ? AM : PM;
        final boolean isOnInnerCircle = getInnerCircleForHour(hour);
        if (mAmOrPm != amOrPm || mIsOnInnerCircle != isOnInnerCircle) {
            mAmOrPm = amOrPm;
            mIsOnInnerCircle = isOnInnerCircle;

            initData();
        }

        invalidate();

        if (callback && mListener != null) {
            mListener.onValueSelected(HOURS, hour, autoAdvance);
        }
    }

    /**
     * Returns the current hour in 24-hour time.
     *
     * @return the current hour between 0 and 23 (inclusive)
     */
    public int getCurrentHour() {
        return getHourForDegrees(mSelectionDegrees[HOURS], mIsOnInnerCircle);
    }

    private int getHourForDegrees(int degrees, boolean innerCircle) {
        int hour = (degrees / DEGREES_FOR_ONE_HOUR) % 12;
        if (mIs24HourMode) {
            // Convert the 12-hour value into 24-hour time based on where the
            // selector is positioned.
            if (!innerCircle && hour == 0) {
                // Outer circle is 1 through 12.
                hour = 12;
            } else if (innerCircle && hour != 0) {
                // Inner circle is 13 through 23 and 0.
                hour += 12;
            }
        } else if (mAmOrPm == PM) {
            hour += 12;
        }
        return hour;
    }

    /**
     * @param hour the hour in 24-hour time or 12-hour time
     */
    private int getDegreesForHour(int hour) {
        // Convert to be 0-11.
        if (mIs24HourMode) {
            if (hour >= 12) {
                hour -= 12;
            }
        } else if (hour == 12) {
            hour = 0;
        }
        return hour * DEGREES_FOR_ONE_HOUR;
    }

    /**
     * @param hour the hour in 24-hour time or 12-hour time
     */
    private boolean getInnerCircleForHour(int hour) {
        return mIs24HourMode && (hour == 0 || hour > 12);
    }

    public void setCurrentMinute(int minute) {
        setCurrentMinuteInternal(minute, true);
    }

    private void setCurrentMinuteInternal(int minute, boolean callback) {
        mSelectionDegrees[MINUTES] = (minute % MINUTES_IN_CIRCLE) * DEGREES_FOR_ONE_MINUTE;

        invalidate();

        if (callback && mListener != null) {
            mListener.onValueSelected(MINUTES, minute, false);
        }
    }

    // Returns minutes in 0-59 range
    public int getCurrentMinute() {
        return getMinuteForDegrees(mSelectionDegrees[MINUTES]);
    }

    private int getMinuteForDegrees(int degrees) {
        return degrees / DEGREES_FOR_ONE_MINUTE;
    }

    private int getDegreesForMinute(int minute) {
        return minute * DEGREES_FOR_ONE_MINUTE;
    }

    /**
     * Sets whether the picker is showing AM or PM hours. Has no effect when
     * in 24-hour mode.
     *
     * @param amOrPm {@link #AM} or {@link #PM}
     * @return {@code true} if the value changed from what was previously set,
     *         or {@code false} otherwise
     */
    public boolean setAmOrPm(int amOrPm) {
        if (mAmOrPm == amOrPm || mIs24HourMode) {
            return false;
        }

        mAmOrPm = amOrPm;
        invalidate();
        return true;
    }

    public int getAmOrPm() {
        return mAmOrPm;
    }

    public void showHours(boolean animate) {
        showPicker(true, animate);
    }

    public void showMinutes(boolean animate) {
        showPicker(false, animate);
    }

    private void initHoursAndMinutesText() {
        // Initialize the hours and minutes numbers.
        for (int i = 0; i < 12; i++) {
            mHours12Texts[i] = String.format("%d", HOURS_NUMBERS[i]);
            mInnerHours24Texts[i] = String.format("%02d", HOURS_NUMBERS_24[i]);
            mOuterHours24Texts[i] = String.format("%d", HOURS_NUMBERS[i]);
            mMinutesTexts[i] = String.format("%02d", MINUTES_NUMBERS[i]);
        }
    }

    private void initData() {
        if (mIs24HourMode) {
            mOuterTextHours = mOuterHours24Texts;
            mInnerTextHours = mInnerHours24Texts;
        } else {
            mOuterTextHours = mHours12Texts;
            mInnerTextHours = mHours12Texts;
        }

        mMinutesText = mMinutesTexts;
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!changed) {
            return;
        }

        mXCenter = getWidth() / 2;
        mYCenter = getHeight() / 2;
        mCircleRadius = Math.min(mXCenter, mYCenter);

        mMinDistForInnerNumber = mCircleRadius - mTextInset[HOURS_INNER] - mSelectorRadius;
        mMaxDistForOuterNumber = mCircleRadius - mTextInset[HOURS] + mSelectorRadius;
        mHalfwayDist = mCircleRadius - (mTextInset[HOURS] + mTextInset[HOURS_INNER]) / 2;

        calculatePositionsHours();
        calculatePositionsMinutes();
    }

    @Override
    public void onDraw(Canvas canvas) {
        final float alphaMod = mInputEnabled ? 1 : mDisabledAlpha;

        drawCircleBackground(canvas);

        final Path selectorPath = mSelectorPath;
        drawSelector(canvas, selectorPath);
        drawHours(canvas, selectorPath, alphaMod);
        drawMinutes(canvas, selectorPath, alphaMod);
        drawCenter(canvas, alphaMod);
    }

    private void showPicker(boolean hours, boolean animate) {
        if (mShowHours == hours) {
            return;
        }

        mShowHours = hours;

        if (animate) {
            animatePicker(hours, ANIM_DURATION_NORMAL);
        } else {
            // If we have a pending or running animator, cancel it.
            cancelPickerAnimation();
            mHoursToMinutes = hours ? 0.0f : 1.0f;
        }

        initData();
        invalidate();
    }

    private void animatePicker(boolean hoursToMinutes, long duration) {
        final float target = hoursToMinutes ? HOURS : MINUTES;
        if (mHoursToMinutes == target) {
            // If we have a pending or running animator, cancel it.
            cancelPickerAnimation();

            // We're already showing the correct picker.
            return;
        }

        // Auto-cancels a running animation towards the other picker.
        mAnimFrom = mHoursToMinutes;
        mAnimTo = target;
        mAnimDuration = duration;
        mAnimStart = SystemClock.uptimeMillis();
        if (!mAnimating) {
            mAnimating = true;
            postOnAnimation(mAnimationStep);
        }
    }

    private void cancelPickerAnimation() {
        if (mAnimating) {
            mAnimating = false;
            removeCallbacks(mAnimationStep);
        }
    }

    private final Runnable mAnimationStep = new Runnable() {
        @Override
        public void run() {
            if (!mAnimating) {
                return;
            }
            final float t = Math.min(1f,
                    (SystemClock.uptimeMillis() - mAnimStart) / (float) Math.max(1, mAnimDuration));
            // AccelerateDecelerateInterpolator, the ObjectAnimator default.
            final float eased = (float) (Math.cos((t + 1) * Math.PI) / 2.0f) + 0.5f;
            mHoursToMinutes = mAnimFrom + (mAnimTo - mAnimFrom) * eased;
            invalidate();
            if (t < 1f) {
                postOnAnimation(this);
            } else {
                mHoursToMinutes = mAnimTo;
                mAnimating = false;
            }
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAnimating) {
            cancelPickerAnimation();
            mHoursToMinutes = mAnimTo;
        }
    }

    private void drawCircleBackground(Canvas canvas) {
        canvas.drawCircle(mXCenter, mYCenter, mCircleRadius, mPaintBackground);
    }

    private void drawHours(Canvas canvas, Path selectorPath, float alphaMod) {
        final int hoursAlpha = (int) (255f * (1f - mHoursToMinutes) * alphaMod + 0.5f);
        if (hoursAlpha > 0) {
            // Exclude the selector region, then draw inner/outer hours with no
            // activated states.
            canvas.save();
            canvas.clipPath(selectorPath, Region.Op.DIFFERENCE);
            drawHoursClipped(canvas, hoursAlpha, false);
            canvas.restore();

            // Intersect the selector region, then draw minutes with only
            // activated states.
            canvas.save();
            canvas.clipPath(selectorPath, Region.Op.INTERSECT);
            drawHoursClipped(canvas, hoursAlpha, true);
            canvas.restore();
        }
    }

    private void drawHoursClipped(Canvas canvas, int hoursAlpha, boolean showActivated) {
        // Draw outer hours.
        drawTextElements(canvas, mTextSize[HOURS], mTypeface, mTextColor[HOURS], mOuterTextHours,
                mOuterTextX[HOURS], mOuterTextY[HOURS], mPaint[HOURS], hoursAlpha,
                showActivated && !mIsOnInnerCircle, mSelectionDegrees[HOURS], showActivated);

        // Draw inner hours (13-00) for 24-hour time.
        if (mIs24HourMode && mInnerTextHours != null) {
            drawTextElements(canvas, mTextSize[HOURS_INNER], mTypeface, mTextColor[HOURS_INNER],
                    mInnerTextHours, mInnerTextX, mInnerTextY, mPaint[HOURS], hoursAlpha,
                    showActivated && mIsOnInnerCircle, mSelectionDegrees[HOURS], showActivated);
        }
    }

    private void drawMinutes(Canvas canvas, Path selectorPath, float alphaMod) {
        final int minutesAlpha = (int) (255f * mHoursToMinutes * alphaMod + 0.5f);
        if (minutesAlpha > 0) {
            // Exclude the selector region, then draw minutes with no
            // activated states.
            canvas.save();
            canvas.clipPath(selectorPath, Region.Op.DIFFERENCE);
            drawMinutesClipped(canvas, minutesAlpha, false);
            canvas.restore();

            // Intersect the selector region, then draw minutes with only
            // activated states.
            canvas.save();
            canvas.clipPath(selectorPath, Region.Op.INTERSECT);
            drawMinutesClipped(canvas, minutesAlpha, true);
            canvas.restore();
        }
    }

    private void drawMinutesClipped(Canvas canvas, int minutesAlpha, boolean showActivated) {
        drawTextElements(canvas, mTextSize[MINUTES], mTypeface, mTextColor[MINUTES], mMinutesText,
                mOuterTextX[MINUTES], mOuterTextY[MINUTES], mPaint[MINUTES], minutesAlpha,
                showActivated, mSelectionDegrees[MINUTES], showActivated);
    }

    private void drawCenter(Canvas canvas, float alphaMod) {
        mPaintCenter.setAlpha((int) (255 * alphaMod + 0.5f));
        canvas.drawCircle(mXCenter, mYCenter, mCenterDotRadius, mPaintCenter);
    }

    private int getMultipliedAlpha(int argb, int alpha) {
        return (int) (Color.alpha(argb) * (alpha / 255.0) + 0.5);
    }

    private void drawSelector(Canvas canvas, Path selectorPath) {
        // Determine the current length, angle, and dot scaling factor.
        final int hoursIndex = mIsOnInnerCircle ? HOURS_INNER : HOURS;
        final int hoursInset = mTextInset[hoursIndex];
        final int hoursAngleDeg = mSelectionDegrees[hoursIndex % 2];
        final float hoursDotScale = mSelectionDegrees[hoursIndex % 2] % 30 != 0 ? 1 : 0;

        final int minutesIndex = MINUTES;
        final int minutesInset = mTextInset[minutesIndex];
        final int minutesAngleDeg = mSelectionDegrees[minutesIndex];
        final float minutesDotScale = mSelectionDegrees[minutesIndex] % 30 != 0 ? 1 : 0;

        // Calculate the current radius at which to place the selection circle.
        final int selRadius = mSelectorRadius;
        final float selLength =
                mCircleRadius - MathUtils.lerp(hoursInset, minutesInset, mHoursToMinutes);
        final double selAngleRad =
                Math.toRadians(MathUtils.lerpDeg(hoursAngleDeg, minutesAngleDeg, mHoursToMinutes));
        final float selCenterX = mXCenter + selLength * (float) Math.sin(selAngleRad);
        final float selCenterY = mYCenter - selLength * (float) Math.cos(selAngleRad);

        // Draw the selection circle.
        final Paint paint = mPaintSelector[SELECTOR_CIRCLE];
        paint.setColor(mSelectorColor);
        canvas.drawCircle(selCenterX, selCenterY, selRadius, paint);

        // If needed, set up the clip path for later.
        if (selectorPath != null) {
            selectorPath.reset();
            selectorPath.addCircle(selCenterX, selCenterY, selRadius, Path.Direction.CCW);
        }

        // Draw the dot if we're between two items.
        final float dotScale = MathUtils.lerp(hoursDotScale, minutesDotScale, mHoursToMinutes);
        if (dotScale > 0) {
            final Paint dotPaint = mPaintSelector[SELECTOR_DOT];
            dotPaint.setColor(mSelectorDotColor);
            canvas.drawCircle(selCenterX, selCenterY, mSelectorDotRadius * dotScale, dotPaint);
        }

        // Shorten the line to only go from the edge of the center dot to the
        // edge of the selection circle.
        final double sin = Math.sin(selAngleRad);
        final double cos = Math.cos(selAngleRad);
        final float lineLength = selLength - selRadius;
        final int centerX = mXCenter + (int) (mCenterDotRadius * sin);
        final int centerY = mYCenter - (int) (mCenterDotRadius * cos);
        final float linePointX = centerX + (int) (lineLength * sin);
        final float linePointY = centerY - (int) (lineLength * cos);

        // Draw the line.
        final Paint linePaint = mPaintSelector[SELECTOR_LINE];
        linePaint.setColor(mSelectorColor);
        linePaint.setStrokeWidth(mSelectorStroke);
        canvas.drawLine(mXCenter, mYCenter, linePointX, linePointY, linePaint);
    }

    private void calculatePositionsHours() {
        // Calculate the text positions
        final float numbersRadius = mCircleRadius - mTextInset[HOURS];

        // Calculate the positions for the 12 numbers in the main circle.
        calculatePositions(mPaint[HOURS], numbersRadius, mXCenter, mYCenter,
                mTextSize[HOURS], mOuterTextX[HOURS], mOuterTextY[HOURS]);

        // If we have an inner circle, calculate those positions too.
        if (mIs24HourMode) {
            final int innerNumbersRadius = mCircleRadius - mTextInset[HOURS_INNER];
            calculatePositions(mPaint[HOURS], innerNumbersRadius, mXCenter, mYCenter,
                    mTextSize[HOURS_INNER], mInnerTextX, mInnerTextY);
        }
    }

    private void calculatePositionsMinutes() {
        // Calculate the text positions
        final float numbersRadius = mCircleRadius - mTextInset[MINUTES];

        // Calculate the positions for the 12 numbers in the main circle.
        calculatePositions(mPaint[MINUTES], numbersRadius, mXCenter, mYCenter,
                mTextSize[MINUTES], mOuterTextX[MINUTES], mOuterTextY[MINUTES]);
    }

    /**
     * Using the trigonometric Unit Circle, calculate the positions that the text will need to be
     * drawn at based on the specified circle radius. Place the values in the textGridHeights and
     * textGridWidths parameters.
     */
    private static void calculatePositions(Paint paint, float radius, float xCenter, float yCenter,
            float textSize, float[] x, float[] y) {
        // Adjust yCenter to account for the text's baseline.
        paint.setTextSize(textSize);
        yCenter -= (paint.descent() + paint.ascent()) / 2;

        for (int i = 0; i < NUM_POSITIONS; i++) {
            x[i] = xCenter - radius * COS_30[i];
            y[i] = yCenter - radius * SIN_30[i];
        }
    }

    /**
     * Draw the 12 text values at the positions specified by the textGrid parameters.
     */
    private void drawTextElements(Canvas canvas, float textSize, Typeface typeface,
            ColorStateList textColor, String[] texts, float[] textX, float[] textY, Paint paint,
            int alpha, boolean showActivated, int activatedDegrees, boolean activatedOnly) {
        paint.setTextSize(textSize);
        paint.setTypeface(typeface);

        // The activated index can touch a range of elements.
        final float activatedIndex = activatedDegrees / (360.0f / NUM_POSITIONS);
        final int activatedFloor = (int) activatedIndex;
        final int activatedCeil = ((int) Math.ceil(activatedIndex)) % NUM_POSITIONS;

        for (int i = 0; i < 12; i++) {
            final boolean activated = (activatedFloor == i || activatedCeil == i);
            if (activatedOnly && !activated) {
                continue;
            }

            final int stateMask = StateSet.VIEW_STATE_ENABLED
                    | (showActivated && activated ? StateSet.VIEW_STATE_ACTIVATED : 0);
            final int color = textColor.getColorForState(StateSet.get(stateMask), 0);
            paint.setColor(color);
            paint.setAlpha(getMultipliedAlpha(color, alpha));

            canvas.drawText(texts[i], textX[i], textY[i], paint);
        }
    }

    private int getDegreesFromXY(float x, float y, boolean constrainOutside) {
        // Ensure the point is inside the touchable area.
        final int innerBound;
        final int outerBound;
        if (mIs24HourMode && mShowHours) {
            innerBound = mMinDistForInnerNumber;
            outerBound = mMaxDistForOuterNumber;
        } else {
            final int index = mShowHours ? HOURS : MINUTES;
            final int center = mCircleRadius - mTextInset[index];
            innerBound = center - mSelectorRadius;
            outerBound = center + mSelectorRadius;
        }

        final double dX = x - mXCenter;
        final double dY = y - mYCenter;
        final double distFromCenter = Math.sqrt(dX * dX + dY * dY);
        if (distFromCenter < innerBound || constrainOutside && distFromCenter > outerBound) {
            return -1;
        }

        // Convert to degrees.
        final int degrees = (int) (Math.toDegrees(Math.atan2(dY, dX) + Math.PI / 2) + 0.5);
        if (degrees < 0) {
            return degrees + 360;
        } else {
            return degrees;
        }
    }

    private boolean getInnerCircleFromXY(float x, float y) {
        if (mIs24HourMode && mShowHours) {
            final double dX = x - mXCenter;
            final double dY = y - mYCenter;
            final double distFromCenter = Math.sqrt(dX * dX + dY * dY);
            return distFromCenter <= mHalfwayDist;
        }
        return false;
    }

    boolean mChangedDuringTouch = false;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mInputEnabled) {
            return true;
        }

        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_DOWN) {
            boolean forceSelection = false;
            boolean autoAdvance = false;

            if (action == MotionEvent.ACTION_DOWN) {
                // This is a new event stream, reset whether the value changed.
                mChangedDuringTouch = false;
            } else if (action == MotionEvent.ACTION_UP) {
                autoAdvance = true;

                // If we saw a down/up pair without the value changing, assume
                // this is a single-tap selection and force a change.
                if (!mChangedDuringTouch) {
                    forceSelection = true;
                }
            }

            mChangedDuringTouch |= handleTouchInput(
                    event.getX(), event.getY(), forceSelection, autoAdvance);
        }

        return true;
    }

    private boolean handleTouchInput(
            float x, float y, boolean forceSelection, boolean autoAdvance) {
        final boolean isOnInnerCircle = getInnerCircleFromXY(x, y);
        final int degrees = getDegreesFromXY(x, y, false);
        if (degrees == -1) {
            return false;
        }

        // Ensure we're showing the correct picker.
        animatePicker(mShowHours, ANIM_DURATION_TOUCH);

        final int type;
        final int newValue;
        final boolean valueChanged;

        if (mShowHours) {
            final int snapDegrees = snapOnly30s(degrees, 0) % 360;
            valueChanged = mIsOnInnerCircle != isOnInnerCircle
                    || mSelectionDegrees[HOURS] != snapDegrees;
            mIsOnInnerCircle = isOnInnerCircle;
            mSelectionDegrees[HOURS] = snapDegrees;
            type = HOURS;
            newValue = getCurrentHour();
        } else {
            final int snapDegrees = snapPrefer30s(degrees) % 360;
            valueChanged = mSelectionDegrees[MINUTES] != snapDegrees;
            mSelectionDegrees[MINUTES] = snapDegrees;
            type = MINUTES;
            newValue = getCurrentMinute();
        }

        if (valueChanged || forceSelection || autoAdvance) {
            // Fire the listener even if we just need to auto-advance.
            if (mListener != null) {
                mListener.onValueSelected(type, newValue, autoAdvance);
            }

            // Only provide feedback if the value actually changed.
            if (valueChanged || forceSelection) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                invalidate();
            }
            return true;
        }

        return false;
    }

    public void setInputEnabled(boolean inputEnabled) {
        mInputEnabled = inputEnabled;
        invalidate();
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
        if (!isEnabled()) {
            return null;
        }
        final int degrees = getDegreesFromXY(event.getX(), event.getY(), false);
        if (degrees != -1) {
            return PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_HAND);
        }
        return super.onResolvePointerIcon(event, pointerIndex);
    }
}
