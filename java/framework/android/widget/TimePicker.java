package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.MathUtils;
import android.view.View;
import android.view.ViewStructure;
import android.view.accessibility.AccessibilityEvent;
import com.android.internal.util.InternalRes;
import java.text.DateFormatSymbols;
import java.util.Locale;

/**
 * A widget for selecting the time of day, in either 24-hour or AM/PM mode
 * (AOSP port). Clock mode shows a header over a radial picker (or text
 * inputs); spinner mode shows hour, minute and AM/PM NumberPickers.
 * Autofill values are not supported (there is no AutofillValue).
 */
public class TimePicker extends FrameLayout {
    private static final String LOG_TAG = TimePicker.class.getSimpleName();

    /** framework-internal (hidden in AOSP): presentation mode for the spinner-style time picker. */
    public static final int MODE_SPINNER = 1;

    /** framework-internal (hidden in AOSP): presentation mode for the clock-style time picker. */
    public static final int MODE_CLOCK = 2;

    private final TimePickerDelegate mDelegate;

    private final int mMode;

    /**
     * The callback interface used to indicate the time has been adjusted.
     */
    public interface OnTimeChangedListener {

        /**
         * @param view The view associated with this listener.
         * @param hourOfDay The current hour.
         * @param minute The current minute.
         */
        void onTimeChanged(TimePicker view, int hourOfDay, int minute);
    }

    public TimePicker(Context context) {
        this(context, null);
    }

    public TimePicker(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.timePickerStyle);
    }

    public TimePicker(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TimePicker(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        // DatePicker is important by default, unless app developer overrode attribute.
        if (getImportantForAutofill() == IMPORTANT_FOR_AUTOFILL_AUTO) {
            setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_YES);
        }

        final TypedArray a = context.obtainStyledAttributes(attrs, InternalRes.attrs(
                "dialogMode", "android:timePickerMode"), defStyleAttr, defStyleRes);
        final boolean isDialogMode = a.getBoolean(0, false);
        final int requestedMode = a.getInt(1, MODE_SPINNER);
        a.recycle();

        if (requestedMode == MODE_CLOCK && isDialogMode) {
            // You want MODE_CLOCK? YOU CAN'T HANDLE MODE_CLOCK! Well, maybe
            // you can depending on your screen size. Let's check...
            mMode = context.getResources().getInteger(InternalRes.id("integer", "time_picker_mode"));
        } else {
            mMode = requestedMode;
        }

        switch (mMode) {
            case MODE_CLOCK:
                mDelegate = new TimePickerClockDelegate(
                        this, context, attrs, defStyleAttr, defStyleRes);
                break;
            case MODE_SPINNER:
            default:
                mDelegate = new TimePickerSpinnerDelegate(
                        this, context, attrs, defStyleAttr, defStyleRes);
                break;
        }
    }

    /**
     * framework-internal (hidden in AOSP).
     *
     * @return the picker's presentation mode, one of {@link #MODE_CLOCK} or {@link #MODE_SPINNER}
     */
    public int getMode() {
        return mMode;
    }

    /**
     * Sets the currently selected hour using 24-hour time.
     *
     * @param hour the hour to set, in the range (0-23)
     * @see #getHour()
     */
    public void setHour(int hour) {
        mDelegate.setHour(MathUtils.constrain(hour, 0, 23));
    }

    /**
     * Returns the currently selected hour using 24-hour time.
     *
     * @return the currently selected hour, in the range (0-23)
     * @see #setHour(int)
     */
    public int getHour() {
        return mDelegate.getHour();
    }

    /**
     * Sets the currently selected minute.
     *
     * @param minute the minute to set, in the range (0-59)
     * @see #getMinute()
     */
    public void setMinute(int minute) {
        mDelegate.setMinute(MathUtils.constrain(minute, 0, 59));
    }

    /**
     * Returns the currently selected minute.
     *
     * @return the currently selected minute, in the range (0-59)
     * @see #setMinute(int)
     */
    public int getMinute() {
        return mDelegate.getMinute();
    }

    /**
     * Sets the current hour.
     *
     * @deprecated Use {@link #setHour(int)}
     */
    @Deprecated
    public void setCurrentHour(Integer currentHour) {
        setHour(currentHour);
    }

    /**
     * @return the current hour in the range (0-23)
     * @deprecated Use {@link #getHour()}
     */
    @Deprecated
    public Integer getCurrentHour() {
        return getHour();
    }

    /**
     * Sets the current minute (0-59).
     *
     * @deprecated Use {@link #setMinute(int)}
     */
    @Deprecated
    public void setCurrentMinute(Integer currentMinute) {
        setMinute(currentMinute);
    }

    /**
     * @return the current minute
     * @deprecated Use {@link #getMinute()}
     */
    @Deprecated
    public Integer getCurrentMinute() {
        return getMinute();
    }

    /**
     * Sets whether this widget displays time in 24-hour mode or 12-hour mode
     * with an AM/PM picker.
     *
     * @param is24HourView {@code true} to display in 24-hour mode,
     *                     {@code false} for 12-hour mode with AM/PM
     * @see #is24HourView()
     */
    public void setIs24HourView(Boolean is24HourView) {
        if (is24HourView == null) {
            return;
        }

        mDelegate.setIs24Hour(is24HourView);
    }

    /**
     * @return {@code true} if this widget displays time in 24-hour mode,
     *         {@code false} otherwise}
     * @see #setIs24HourView(Boolean)
     */
    public boolean is24HourView() {
        return mDelegate.is24Hour();
    }

    /**
     * Set the callback that indicates the time has been adjusted by the user.
     *
     * @param onTimeChangedListener the callback, should not be null.
     */
    public void setOnTimeChangedListener(OnTimeChangedListener onTimeChangedListener) {
        mDelegate.setOnTimeChangedListener(onTimeChangedListener);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mDelegate.setEnabled(enabled);
    }

    @Override
    public boolean isEnabled() {
        return mDelegate.isEnabled();
    }

    @Override
    public int getBaseline() {
        return mDelegate.getBaseline();
    }

    /**
     * Validates whether current input by the user is a valid time based on the locale. TimePicker
     * will show an error message to the user if the time is not valid.
     *
     * @return {@code true} if the input is valid, {@code false} otherwise
     */
    public boolean validateInput() {
        return mDelegate.validateInput();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return mDelegate.onSaveInstanceState(superState);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        BaseSavedState ss = (BaseSavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mDelegate.onRestoreInstanceState(ss);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return TimePicker.class.getName();
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);
        mDelegate.onPopulateAccessibilityEvent(event);
    }

    /** framework-internal (hidden in AOSP, for testing). */
    public View getHourView() {
        return mDelegate.getHourView();
    }

    /** framework-internal (hidden in AOSP, for testing). */
    public View getMinuteView() {
        return mDelegate.getMinuteView();
    }

    /** framework-internal (hidden in AOSP, for testing). */
    public View getAmView() {
        return mDelegate.getAmView();
    }

    /** framework-internal (hidden in AOSP, for testing). */
    public View getPmView() {
        return mDelegate.getPmView();
    }

    @Override
    public void dispatchProvideAutofillStructure(ViewStructure structure, int flags) {
        // No autofill service on this platform.
    }

    @Override
    public int getAutofillType() {
        return isEnabled() ? AUTOFILL_TYPE_DATE : AUTOFILL_TYPE_NONE;
    }

    /**
     * Returns the AM/PM strings for the locale, using the short forms (the
     * narrow ones on Android when the full ones are longer than 4 characters).
     */
    static String[] getAmPmStrings(Context context) {
        final Locale locale = context.getResources().getConfiguration().locale;
        final String[] amPm = DateFormatSymbols.getInstance(locale).getAmPmStrings();
        final String[] result = new String[2];
        result[0] = amPm[0].length() > 4 ? amPm[0].substring(0, 1) : amPm[0];
        result[1] = amPm[1].length() > 4 ? amPm[1].substring(0, 1) : amPm[1];
        return result;
    }

    /**
     * A delegate interface that defined the public API of the TimePicker. Allows different
     * TimePicker implementations. This would need to be implemented by the TimePicker delegates
     * for the real behavior.
     */
    interface TimePickerDelegate {
        void setHour(int hour);
        int getHour();

        void setMinute(int minute);
        int getMinute();

        void setDate(int hour, int minute);

        void setIs24Hour(boolean is24Hour);
        boolean is24Hour();

        boolean validateInput();

        void setOnTimeChangedListener(OnTimeChangedListener onTimeChangedListener);

        void setEnabled(boolean enabled);
        boolean isEnabled();

        int getBaseline();

        Parcelable onSaveInstanceState(Parcelable superState);
        void onRestoreInstanceState(Parcelable state);

        boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event);
        void onPopulateAccessibilityEvent(AccessibilityEvent event);

        View getHourView();
        View getMinuteView();
        View getAmView();
        View getPmView();
    }

    /**
     * An abstract class which can be used as a start for TimePicker implementations
     */
    abstract static class AbstractTimePickerDelegate implements TimePickerDelegate {
        protected final TimePicker mDelegator;
        protected final Context mContext;
        protected final Locale mLocale;

        protected OnTimeChangedListener mOnTimeChangedListener;

        public AbstractTimePickerDelegate(TimePicker delegator, Context context) {
            mDelegator = delegator;
            mContext = context;
            mLocale = context.getResources().getConfiguration().locale;
        }

        @Override
        public void setOnTimeChangedListener(OnTimeChangedListener callback) {
            mOnTimeChangedListener = callback;
        }

        /**
         * This method must be called every time the value of the hour and/or minute is changed by
         * a subclass method (autofill values are not tracked on this platform).
         */
        protected void resetAutofilledValue() {
        }

        protected static class SavedState extends View.BaseSavedState {
            private final int mHour;
            private final int mMinute;
            private final boolean mIs24HourMode;
            private final int mCurrentItemShowing;

            public SavedState(Parcelable superState, int hour, int minute, boolean is24HourMode) {
                this(superState, hour, minute, is24HourMode, 0);
            }

            public SavedState(Parcelable superState, int hour, int minute, boolean is24HourMode,
                    int currentItemShowing) {
                super(superState);
                mHour = hour;
                mMinute = minute;
                mIs24HourMode = is24HourMode;
                mCurrentItemShowing = currentItemShowing;
            }

            protected SavedState(Parcel in) {
                super(in);
                mHour = in.readInt();
                mMinute = in.readInt();
                mIs24HourMode = (in.readInt() == 1);
                mCurrentItemShowing = in.readInt();
            }

            public int getHour() {
                return mHour;
            }

            public int getMinute() {
                return mMinute;
            }

            public boolean is24HourMode() {
                return mIs24HourMode;
            }

            public int getCurrentItemShowing() {
                return mCurrentItemShowing;
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
                super.writeToParcel(dest, flags);
                dest.writeInt(mHour);
                dest.writeInt(mMinute);
                dest.writeInt(mIs24HourMode ? 1 : 0);
                dest.writeInt(mCurrentItemShowing);
            }

            @SuppressWarnings({"unused", "hiding"})
            public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
                public SavedState createFromParcel(Parcel in) {
                    return new SavedState(in);
                }

                public SavedState[] newArray(int size) {
                    return new SavedState[size];
                }
            };
        }
    }
}
