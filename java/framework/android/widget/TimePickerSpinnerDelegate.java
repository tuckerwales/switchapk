package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcelable;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import com.android.internal.util.InternalRes;
import java.util.Calendar;

/**
 * framework-internal. A delegate implementing the basic spinner-based
 * TimePicker (AOSP port).
 */
class TimePickerSpinnerDelegate extends TimePicker.AbstractTimePickerDelegate {
    private static final boolean DEFAULT_ENABLED_STATE = true;
    private static final int HOURS_IN_HALF_DAY = 12;

    private final NumberPicker mHourSpinner;
    private final NumberPicker mMinuteSpinner;
    private final NumberPicker mAmPmSpinner;
    private final EditText mHourSpinnerInput;
    private final EditText mMinuteSpinnerInput;
    private final EditText mAmPmSpinnerInput;
    private final TextView mDivider;

    // Note that the legacy implementation of the TimePicker is
    // using a button for toggling between AM/PM while the new
    // version uses a NumberPicker spinner. Therefore the code
    // accommodates these two cases to be backwards compatible.
    private final Button mAmPmButton;

    private final String[] mAmPmStrings;

    private final Calendar mTempCalendar;

    private boolean mIsEnabled = DEFAULT_ENABLED_STATE;
    private boolean mHourWithTwoDigit;
    private char mHourFormat;

    private boolean mIs24HourView;
    private boolean mIsAm;

    public TimePickerSpinnerDelegate(TimePicker delegator, Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(delegator, context);

        // process style attributes
        final TypedArray a = mContext.obtainStyledAttributes(attrs, InternalRes.attrs("legacyLayout"),
                defStyleAttr, defStyleRes);
        final int layoutResourceId = a.getResourceId(0, InternalRes.layout("time_picker_legacy"));
        a.recycle();

        final LayoutInflater inflater = LayoutInflater.from(mContext);
        final View view = inflater.inflate(layoutResourceId, mDelegator, true);
        view.setSaveFromParentEnabled(false);

        // hour
        mHourSpinner = (NumberPicker) delegator.findViewById(InternalRes.viewId("hour"));
        mHourSpinner.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            public void onValueChange(NumberPicker spinner, int oldVal, int newVal) {
                updateInputState();
                if (!is24Hour()) {
                    if ((oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY)
                            || (oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1)) {
                        mIsAm = !mIsAm;
                        updateAmPmControl();
                    }
                }
                onTimeChanged();
            }
        });
        mHourSpinnerInput = (EditText) mHourSpinner.findViewById(InternalRes.viewId("numberpicker_input"));
        mHourSpinnerInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);

        // divider (only for the new widget style)
        mDivider = (TextView) mDelegator.findViewById(InternalRes.viewId("divider"));
        if (mDivider != null) {
            setDividerText();
        }

        // minute
        mMinuteSpinner = (NumberPicker) mDelegator.findViewById(InternalRes.viewId("minute"));
        mMinuteSpinner.setMinValue(0);
        mMinuteSpinner.setMaxValue(59);
        mMinuteSpinner.setOnLongPressUpdateInterval(100);
        mMinuteSpinner.setFormatter(NumberPicker.getTwoDigitFormatter());
        mMinuteSpinner.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            public void onValueChange(NumberPicker spinner, int oldVal, int newVal) {
                updateInputState();
                int minValue = mMinuteSpinner.getMinValue();
                int maxValue = mMinuteSpinner.getMaxValue();
                if (oldVal == maxValue && newVal == minValue) {
                    int newHour = mHourSpinner.getValue() + 1;
                    if (!is24Hour() && newHour == HOURS_IN_HALF_DAY) {
                        mIsAm = !mIsAm;
                        updateAmPmControl();
                    }
                    mHourSpinner.setValue(newHour);
                } else if (oldVal == minValue && newVal == maxValue) {
                    int newHour = mHourSpinner.getValue() - 1;
                    if (!is24Hour() && newHour == HOURS_IN_HALF_DAY - 1) {
                        mIsAm = !mIsAm;
                        updateAmPmControl();
                    }
                    mHourSpinner.setValue(newHour);
                }
                onTimeChanged();
            }
        });
        mMinuteSpinnerInput = (EditText) mMinuteSpinner.findViewById(InternalRes.viewId("numberpicker_input"));
        mMinuteSpinnerInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);

        // Get the localized am/pm strings and use them in the spinner.
        mAmPmStrings = TimePicker.getAmPmStrings(context);

        // am/pm
        final View amPmView = mDelegator.findViewById(InternalRes.viewId("amPm"));
        if (amPmView instanceof Button) {
            mAmPmSpinner = null;
            mAmPmSpinnerInput = null;
            mAmPmButton = (Button) amPmView;
            mAmPmButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View button) {
                    button.requestFocus();
                    mIsAm = !mIsAm;
                    updateAmPmControl();
                    onTimeChanged();
                }
            });
        } else {
            mAmPmButton = null;
            mAmPmSpinner = (NumberPicker) amPmView;
            mAmPmSpinner.setMinValue(0);
            mAmPmSpinner.setMaxValue(1);
            mAmPmSpinner.setDisplayedValues(mAmPmStrings);
            mAmPmSpinner.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
                public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                    updateInputState();
                    picker.requestFocus();
                    mIsAm = !mIsAm;
                    updateAmPmControl();
                    onTimeChanged();
                }
            });
            mAmPmSpinnerInput = (EditText) mAmPmSpinner.findViewById(InternalRes.viewId("numberpicker_input"));
            mAmPmSpinnerInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        }

        if (isAmPmAtStart()) {
            // Move the am/pm view to the beginning
            ViewGroup amPmParent = (ViewGroup) delegator.findViewById(InternalRes.viewId("timePickerLayout"));
            amPmParent.removeView(amPmView);
            amPmParent.addView(amPmView, 0);
            // Swap layout margins if needed. They may be not symmetrical (Old Standard Theme
            // for example and not for Holo Theme)
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) amPmView.getLayoutParams();
            final int startMargin = lp.getMarginStart();
            final int endMargin = lp.getMarginEnd();
            if (startMargin != endMargin) {
                lp.setMarginStart(endMargin);
                lp.setMarginEnd(startMargin);
            }
        }

        getHourFormatData();

        // update controls to initial state
        updateHourControl();
        updateMinuteControl();
        updateAmPmControl();

        // set to current time
        mTempCalendar = Calendar.getInstance(mLocale);
        setHour(mTempCalendar.get(Calendar.HOUR_OF_DAY));
        setMinute(mTempCalendar.get(Calendar.MINUTE));

        if (!isEnabled()) {
            setEnabled(false);
        }

        // set the content descriptions
        setContentDescriptions();

        // If not explicitly specified this view is important for accessibility.
        if (mDelegator.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            mDelegator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
    }

    @Override
    public boolean validateInput() {
        return true;
    }

    private void getHourFormatData() {
        final String bestDateTimePattern = DateFormat.getBestDateTimePattern(mLocale,
                (mIs24HourView) ? "Hm" : "hm");
        final int lengthPattern = bestDateTimePattern.length();
        mHourWithTwoDigit = false;
        // Check if the returned pattern is single or double 'H', 'h', 'K', 'k'. We also save
        // the hour format that we found.
        for (int i = 0; i < lengthPattern; i++) {
            final char c = bestDateTimePattern.charAt(i);
            if (c == 'H' || c == 'h' || c == 'K' || c == 'k') {
                mHourFormat = c;
                if (i + 1 < lengthPattern && c == bestDateTimePattern.charAt(i + 1)) {
                    mHourWithTwoDigit = true;
                }
                break;
            }
        }
    }

    private boolean isAmPmAtStart() {
        final String bestDateTimePattern = DateFormat.getBestDateTimePattern(mLocale,
                "hm" /* skeleton */);

        return bestDateTimePattern.startsWith("a");
    }

    /**
     * The time separator is defined in the Unicode CLDR and cannot be supposed to be ":".
     *
     * We pass the correct "skeleton" depending on 12 or 24 hours view and then extract the
     * separator as the character which is just after the hour marker in the returned pattern.
     */
    private void setDividerText() {
        final String skeleton = (mIs24HourView) ? "Hm" : "hm";
        final String bestDateTimePattern = DateFormat.getBestDateTimePattern(mLocale,
                skeleton);
        final String separatorText;
        int hourIndex = bestDateTimePattern.lastIndexOf('H');
        if (hourIndex == -1) {
            hourIndex = bestDateTimePattern.lastIndexOf('h');
        }
        if (hourIndex == -1) {
            // Default case
            separatorText = ":";
        } else {
            int minuteIndex = bestDateTimePattern.indexOf('m', hourIndex + 1);
            if (minuteIndex == -1) {
                separatorText = Character.toString(bestDateTimePattern.charAt(hourIndex + 1));
            } else {
                separatorText = bestDateTimePattern.substring(hourIndex + 1, minuteIndex);
            }
        }
        mDivider.setText(separatorText);
    }

    @Override
    public void setDate(int hour, int minute) {
        setCurrentHour(hour, false);
        setCurrentMinute(minute, false);

        onTimeChanged();
    }

    @Override
    public void setHour(int hour) {
        setCurrentHour(hour, true);
    }

    private void setCurrentHour(int currentHour, boolean notifyTimeChanged) {
        // why was Integer used in the first place?
        if (currentHour == getHour()) {
            return;
        }
        resetAutofilledValue();
        if (!is24Hour()) {
            // convert [0,23] ordinal to wall clock display
            if (currentHour >= HOURS_IN_HALF_DAY) {
                mIsAm = false;
                if (currentHour > HOURS_IN_HALF_DAY) {
                    currentHour = currentHour - HOURS_IN_HALF_DAY;
                }
            } else {
                mIsAm = true;
                if (currentHour == 0) {
                    currentHour = HOURS_IN_HALF_DAY;
                }
            }
            updateAmPmControl();
        }
        mHourSpinner.setValue(currentHour);
        if (notifyTimeChanged) {
            onTimeChanged();
        }
    }

    @Override
    public int getHour() {
        int currentHour = mHourSpinner.getValue();
        if (is24Hour()) {
            return currentHour;
        } else if (mIsAm) {
            return currentHour % HOURS_IN_HALF_DAY;
        } else {
            return (currentHour % HOURS_IN_HALF_DAY) + HOURS_IN_HALF_DAY;
        }
    }

    @Override
    public void setMinute(int minute) {
        setCurrentMinute(minute, true);
    }

    private void setCurrentMinute(int minute, boolean notifyTimeChanged) {
        if (minute == getMinute()) {
            return;
        }
        resetAutofilledValue();
        mMinuteSpinner.setValue(minute);
        if (notifyTimeChanged) {
            onTimeChanged();
        }
    }

    @Override
    public int getMinute() {
        return mMinuteSpinner.getValue();
    }

    public void setIs24Hour(boolean is24Hour) {
        if (mIs24HourView == is24Hour) {
            return;
        }
        // cache the current hour since spinner range changes and BEFORE changing mIs24HourView!!
        int currentHour = getHour();
        // Order is important here.
        mIs24HourView = is24Hour;
        getHourFormatData();
        updateHourControl();
        // set value after spinner range is updated
        setCurrentHour(currentHour, false);
        updateMinuteControl();
        updateAmPmControl();
    }

    @Override
    public boolean is24Hour() {
        return mIs24HourView;
    }

    @Override
    public void setEnabled(boolean enabled) {
        mMinuteSpinner.setEnabled(enabled);
        if (mDivider != null) {
            mDivider.setEnabled(enabled);
        }
        mHourSpinner.setEnabled(enabled);
        if (mAmPmSpinner != null) {
            mAmPmSpinner.setEnabled(enabled);
        } else {
            mAmPmButton.setEnabled(enabled);
        }
        mIsEnabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    @Override
    public int getBaseline() {
        return mHourSpinner.getBaseline();
    }

    @Override
    public Parcelable onSaveInstanceState(Parcelable superState) {
        return new SavedState(superState, getHour(), getMinute(), is24Hour());
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            final SavedState ss = (SavedState) state;
            setHour(ss.getHour());
            setMinute(ss.getMinute());
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        onPopulateAccessibilityEvent(event);
        return true;
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        int flags = DateUtils.FORMAT_SHOW_TIME;
        if (mIs24HourView) {
            flags |= DateUtils.FORMAT_24HOUR;
        } else {
            flags |= DateUtils.FORMAT_12HOUR;
        }
        mTempCalendar.set(Calendar.HOUR_OF_DAY, getHour());
        mTempCalendar.set(Calendar.MINUTE, getMinute());
        String selectedDateUtterance = DateUtils.formatDateTime(mContext,
                mTempCalendar.getTimeInMillis(), flags);
        event.getText().add(selectedDateUtterance);
    }

    @Override
    public View getHourView() {
        return mHourSpinnerInput;
    }

    @Override
    public View getMinuteView() {
        return mMinuteSpinnerInput;
    }

    @Override
    public View getAmView() {
        return mAmPmSpinnerInput;
    }

    @Override
    public View getPmView() {
        return mAmPmSpinnerInput;
    }

    private void updateInputState() {
        // Make sure that if the user changes the value and the IME is active
        // for one of the inputs if this widget, the IME is closed. If the user
        // changed the value via the IME and there is a next input the IME will
        // be shown, otherwise the user chose another means of changing the
        // value and having the IME up makes no sense.
        InputMethodManager inputMethodManager = (InputMethodManager) mContext.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            if (mHourSpinnerInput.hasFocus()) {
                inputMethodManager.hideSoftInputFromWindow(mHourSpinnerInput.getWindowToken(), 0);
                mHourSpinnerInput.clearFocus();
            } else if (mMinuteSpinnerInput.hasFocus()) {
                inputMethodManager.hideSoftInputFromWindow(mMinuteSpinnerInput.getWindowToken(), 0);
                mMinuteSpinnerInput.clearFocus();
            } else if (mAmPmSpinnerInput != null && mAmPmSpinnerInput.hasFocus()) {
                inputMethodManager.hideSoftInputFromWindow(mAmPmSpinnerInput.getWindowToken(), 0);
                mAmPmSpinnerInput.clearFocus();
            }
        }
    }

    private void updateAmPmControl() {
        if (is24Hour()) {
            if (mAmPmSpinner != null) {
                mAmPmSpinner.setVisibility(View.GONE);
            } else {
                mAmPmButton.setVisibility(View.GONE);
            }
        } else {
            int index = mIsAm ? Calendar.AM : Calendar.PM;
            if (mAmPmSpinner != null) {
                mAmPmSpinner.setValue(index);
                mAmPmSpinner.setVisibility(View.VISIBLE);
            } else {
                mAmPmButton.setText(mAmPmStrings[index]);
                mAmPmButton.setVisibility(View.VISIBLE);
            }
        }
        mDelegator.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    private void onTimeChanged() {
        mDelegator.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        if (mOnTimeChangedListener != null) {
            mOnTimeChangedListener.onTimeChanged(mDelegator, getHour(), getMinute());
        }
    }

    private void updateHourControl() {
        if (is24Hour()) {
            // 'k' means 1-24 hour
            if (mHourFormat == 'k') {
                mHourSpinner.setMinValue(1);
                mHourSpinner.setMaxValue(24);
            } else {
                mHourSpinner.setMinValue(0);
                mHourSpinner.setMaxValue(23);
            }
        } else {
            // 'K' means 0-11 hour
            if (mHourFormat == 'K') {
                mHourSpinner.setMinValue(0);
                mHourSpinner.setMaxValue(11);
            } else {
                mHourSpinner.setMinValue(1);
                mHourSpinner.setMaxValue(12);
            }
        }
        mHourSpinner.setFormatter(mHourWithTwoDigit ? NumberPicker.getTwoDigitFormatter() : null);
    }

    private void updateMinuteControl() {
        if (is24Hour()) {
            mMinuteSpinnerInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        } else {
            mMinuteSpinnerInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        }
    }

    private void setContentDescriptions() {
        // Minute
        trySetContentDescription(mMinuteSpinner, "increment", "time_picker_increment_minute_button");
        trySetContentDescription(mMinuteSpinner, "decrement", "time_picker_decrement_minute_button");
        // Hour
        trySetContentDescription(mHourSpinner, "increment", "time_picker_increment_hour_button");
        trySetContentDescription(mHourSpinner, "decrement", "time_picker_decrement_hour_button");
        // AM/PM
        if (mAmPmSpinner != null) {
            trySetContentDescription(mAmPmSpinner, "increment", "time_picker_increment_set_pm_button");
            trySetContentDescription(mAmPmSpinner, "decrement", "time_picker_decrement_set_am_button");
        }
    }

    private void trySetContentDescription(View root, String viewId, String contDescRes) {
        View target = root.findViewById(InternalRes.viewId(viewId));
        if (target != null) {
            target.setContentDescription(mContext.getString(InternalRes.id("string", contDescRes)));
        }
    }
}
