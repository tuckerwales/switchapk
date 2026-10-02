package android.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewStructure;
import android.view.accessibility.AccessibilityEvent;
import com.android.internal.util.InternalRes;
import java.util.Calendar;
import java.util.Locale;

/**
 * Provides a widget for selecting a date (AOSP port). Calendar mode shows a
 * header with the selected date over a month calendar (or a year list);
 * spinner mode shows day/month/year NumberPickers with an optional
 * CalendarView. Autofill values are not supported (there is no AutofillValue).
 */
public class DatePicker extends FrameLayout {
    private static final String LOG_TAG = DatePicker.class.getSimpleName();

    /** framework-internal (hidden in AOSP): presentation mode for the spinner-style date picker. */
    public static final int MODE_SPINNER = 1;

    /** framework-internal (hidden in AOSP): presentation mode for the calendar-style date picker. */
    public static final int MODE_CALENDAR = 2;

    private final DatePickerDelegate mDelegate;

    private final int mMode;

    /**
     * The callback used to indicate the user changed the date.
     */
    public interface OnDateChangedListener {

        /**
         * Called upon a date change.
         *
         * @param view The view associated with this listener.
         * @param year The year that was set.
         * @param monthOfYear The month that was set (0-11) for compatibility
         *            with {@link java.util.Calendar}.
         * @param dayOfMonth The day of the month that was set.
         */
        void onDateChanged(DatePicker view, int year, int monthOfYear, int dayOfMonth);
    }

    public DatePicker(Context context) {
        this(context, null);
    }

    public DatePicker(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.datePickerStyle);
    }

    public DatePicker(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public DatePicker(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        // DatePicker is important by default, unless app developer overrode attribute.
        if (getImportantForAutofill() == IMPORTANT_FOR_AUTOFILL_AUTO) {
            setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_YES);
        }

        final TypedArray a = context.obtainStyledAttributes(attrs, InternalRes.attrs(
                "dialogMode", "android:datePickerMode", "android:firstDayOfWeek"),
                defStyleAttr, defStyleRes);
        final boolean isDialogMode = a.getBoolean(0, false);
        final int requestedMode = a.getInt(1, MODE_SPINNER);
        final int firstDayOfWeek = a.getInt(2, 0);
        a.recycle();

        if (requestedMode == MODE_CALENDAR && isDialogMode) {
            // You want MODE_CALENDAR? YOU CAN'T HANDLE MODE_CALENDAR! Well,
            // maybe you can depending on your screen size. Let's check...
            mMode = context.getResources().getInteger(InternalRes.id("integer", "date_picker_mode"));
        } else {
            mMode = requestedMode;
        }

        switch (mMode) {
            case MODE_CALENDAR:
                mDelegate = createCalendarUIDelegate(context, attrs, defStyleAttr, defStyleRes);
                break;
            case MODE_SPINNER:
            default:
                mDelegate = createSpinnerUIDelegate(context, attrs, defStyleAttr, defStyleRes);
                break;
        }

        if (firstDayOfWeek != 0) {
            setFirstDayOfWeek(firstDayOfWeek);
        }
    }

    private DatePickerDelegate createSpinnerUIDelegate(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        return new DatePickerSpinnerDelegate(this, context, attrs, defStyleAttr, defStyleRes);
    }

    private DatePickerDelegate createCalendarUIDelegate(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        return new DatePickerCalendarDelegate(this, context, attrs, defStyleAttr,
                defStyleRes);
    }

    /**
     * framework-internal (hidden in AOSP).
     *
     * @return the picker's presentation mode, one of {@link #MODE_SPINNER} or {@link #MODE_CALENDAR}
     */
    public int getMode() {
        return mMode;
    }

    /**
     * Initialize the state. If the provided values designate an inconsistent
     * date the values are normalized before updating the spinners.
     *
     * @param year The initial year.
     * @param monthOfYear The initial month <strong>starting from zero</strong>.
     * @param dayOfMonth The initial day of the month.
     * @param onDateChangedListener How user is notified date is changed by
     *            user, can be null.
     */
    public void init(int year, int monthOfYear, int dayOfMonth,
            OnDateChangedListener onDateChangedListener) {
        mDelegate.init(year, monthOfYear, dayOfMonth, onDateChangedListener);
    }

    /**
     * Set the callback that indicates the date has been adjusted by the user.
     *
     * @param onDateChangedListener How user is notified date is changed by
     *            user, can be null.
     */
    public void setOnDateChangedListener(OnDateChangedListener onDateChangedListener) {
        mDelegate.setOnDateChangedListener(onDateChangedListener);
    }

    /**
     * Update the current date.
     *
     * @param year The year.
     * @param month The month which is <strong>starting from zero</strong>.
     * @param dayOfMonth The day of the month.
     */
    public void updateDate(int year, int month, int dayOfMonth) {
        mDelegate.updateDate(year, month, dayOfMonth);
    }

    /**
     * @return The selected year.
     */
    public int getYear() {
        return mDelegate.getYear();
    }

    /**
     * @return The selected month.
     */
    public int getMonth() {
        return mDelegate.getMonth();
    }

    /**
     * @return The selected day of month.
     */
    public int getDayOfMonth() {
        return mDelegate.getDayOfMonth();
    }

    /**
     * Gets the minimal date supported by this {@link DatePicker} in
     * milliseconds since January 1, 1970 00:00:00 in
     * {@link java.util.TimeZone#getDefault()} time zone.
     */
    public long getMinDate() {
        return mDelegate.getMinDate().getTimeInMillis();
    }

    /**
     * Sets the minimal date supported by this {@link NumberPicker} in
     * milliseconds since January 1, 1970 00:00:00 in
     * {@link java.util.TimeZone#getDefault()} time zone.
     *
     * @param minDate The minimal supported date.
     */
    public void setMinDate(long minDate) {
        mDelegate.setMinDate(minDate);
    }

    /**
     * Gets the maximal date supported by this {@link DatePicker} in
     * milliseconds since January 1, 1970 00:00:00 in
     * {@link java.util.TimeZone#getDefault()} time zone.
     */
    public long getMaxDate() {
        return mDelegate.getMaxDate().getTimeInMillis();
    }

    /**
     * Sets the maximal date supported by this {@link DatePicker} in
     * milliseconds since January 1, 1970 00:00:00 in
     * {@link java.util.TimeZone#getDefault()} time zone.
     *
     * @param maxDate The maximal supported date.
     */
    public void setMaxDate(long maxDate) {
        mDelegate.setMaxDate(maxDate);
    }

    /**
     * framework-internal (hidden in AOSP). Sets the callback that indicates
     * the current date is valid.
     */
    public void setValidationCallback(ValidationCallback callback) {
        mDelegate.setValidationCallback(callback);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (mDelegate.isEnabled() == enabled) {
            return;
        }
        super.setEnabled(enabled);
        mDelegate.setEnabled(enabled);
    }

    @Override
    public boolean isEnabled() {
        return mDelegate.isEnabled();
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);
        mDelegate.onPopulateAccessibilityEvent(event);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return DatePicker.class.getName();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDelegate.onConfigurationChanged(newConfig);
    }

    /**
     * Sets the first day of week.
     *
     * @param firstDayOfWeek The first day of the week conforming to the
     *            {@link CalendarView} APIs.
     * @see Calendar#SUNDAY
     * @see Calendar#SATURDAY
     */
    public void setFirstDayOfWeek(int firstDayOfWeek) {
        if (firstDayOfWeek < Calendar.SUNDAY || firstDayOfWeek > Calendar.SATURDAY) {
            throw new IllegalArgumentException("firstDayOfWeek must be between 1 and 7");
        }
        mDelegate.setFirstDayOfWeek(firstDayOfWeek);
    }

    /**
     * Gets the first day of week.
     *
     * @return The first day of the week conforming to the {@link CalendarView}
     *         APIs.
     */
    public int getFirstDayOfWeek() {
        return mDelegate.getFirstDayOfWeek();
    }

    /**
     * Returns whether the {@link CalendarView} is shown.
     *
     * @deprecated Not supported by Material-style {@code calendar} mode
     */
    @Deprecated
    public boolean getCalendarViewShown() {
        return mDelegate.getCalendarViewShown();
    }

    /**
     * Returns the {@link CalendarView} used by this picker.
     *
     * @deprecated Not supported by Material-style {@code calendar} mode
     * @throws UnsupportedOperationException if called when the picker is
     *         displayed in {@code calendar} mode
     */
    @Deprecated
    public CalendarView getCalendarView() {
        return mDelegate.getCalendarView();
    }

    /**
     * Sets whether the {@link CalendarView} is shown.
     *
     * @deprecated Not supported by Material-style {@code calendar} mode
     */
    @Deprecated
    public void setCalendarViewShown(boolean shown) {
        mDelegate.setCalendarViewShown(shown);
    }

    /**
     * Returns whether the spinners are shown.
     *
     * @deprecated Not supported by Material-style {@code calendar} mode
     */
    @Deprecated
    public boolean getSpinnersShown() {
        return mDelegate.getSpinnersShown();
    }

    /**
     * Sets whether the spinners are shown.
     *
     * @deprecated Not supported by Material-style {@code calendar} mode
     */
    @Deprecated
    public void setSpinnersShown(boolean shown) {
        mDelegate.setSpinnersShown(shown);
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        dispatchThawSelfOnly(container);
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
    public void dispatchProvideAutofillStructure(ViewStructure structure, int flags) {
        // No autofill service on this platform.
    }

    @Override
    public int getAutofillType() {
        return isEnabled() ? AUTOFILL_TYPE_DATE : AUTOFILL_TYPE_NONE;
    }

    /**
     * A delegate interface that defined the public API of the DatePicker. Allows different
     * DatePicker implementations. This would need to be implemented by the DatePicker delegates
     * for the real behavior.
     */
    interface DatePickerDelegate {
        void init(int year, int monthOfYear, int dayOfMonth,
                OnDateChangedListener onDateChangedListener);

        void setOnDateChangedListener(OnDateChangedListener onDateChangedListener);

        void updateDate(int year, int month, int dayOfMonth);

        int getYear();
        int getMonth();
        int getDayOfMonth();

        void setFirstDayOfWeek(int firstDayOfWeek);
        int getFirstDayOfWeek();

        void setMinDate(long minDate);
        Calendar getMinDate();

        void setMaxDate(long maxDate);
        Calendar getMaxDate();

        void setEnabled(boolean enabled);
        boolean isEnabled();

        CalendarView getCalendarView();

        void setCalendarViewShown(boolean shown);
        boolean getCalendarViewShown();

        void setSpinnersShown(boolean shown);
        boolean getSpinnersShown();

        void setValidationCallback(ValidationCallback callback);

        void onConfigurationChanged(Configuration newConfig);

        Parcelable onSaveInstanceState(Parcelable superState);
        void onRestoreInstanceState(Parcelable state);

        boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event);
        void onPopulateAccessibilityEvent(AccessibilityEvent event);
    }

    /**
     * An abstract class which can be used as a start for DatePicker implementations
     */
    abstract static class AbstractDatePickerDelegate implements DatePickerDelegate {
        // The delegator
        protected DatePicker mDelegator;

        // The context
        protected Context mContext;

        // NOTE: when subclasses change this variable, they must call resetAutofilledValue().
        protected Calendar mCurrentDate;

        // The current locale
        protected Locale mCurrentLocale;

        // Callbacks
        protected OnDateChangedListener mOnDateChangedListener;
        protected ValidationCallback mValidationCallback;

        public AbstractDatePickerDelegate(DatePicker delegator, Context context) {
            mDelegator = delegator;
            mContext = context;

            setCurrentLocale(Locale.getDefault());
        }

        protected void setCurrentLocale(Locale locale) {
            if (!locale.equals(mCurrentLocale)) {
                mCurrentLocale = locale;
                onLocaleChanged(locale);
            }
        }

        @Override
        public void setOnDateChangedListener(OnDateChangedListener callback) {
            mOnDateChangedListener = callback;
        }

        @Override
        public void setValidationCallback(ValidationCallback callback) {
            mValidationCallback = callback;
        }

        /**
         * This method must be called every time the value of the year, month, and/or day is
         * changed by a subclass method (autofill values are not tracked on this platform).
         */
        protected void resetAutofilledValue() {
        }

        protected void onValidationChanged(boolean valid) {
            if (mValidationCallback != null) {
                mValidationCallback.onValidationChanged(valid);
            }
        }

        protected void onLocaleChanged(Locale locale) {
            // Stub.
        }

        @Override
        public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
            event.getText().add(getFormattedCurrentDate());
        }

        protected String getFormattedCurrentDate() {
            return DateUtils.formatDateTime(mContext, mCurrentDate.getTimeInMillis(),
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR
                            | DateUtils.FORMAT_SHOW_WEEKDAY);
        }

        /**
         * Class for managing state storing/restoring.
         */
        static class SavedState extends View.BaseSavedState {
            private final int mSelectedYear;
            private final int mSelectedMonth;
            private final int mSelectedDay;
            private final long mMinDate;
            private final long mMaxDate;
            private final int mCurrentView;
            private final int mListPosition;
            private final int mListPositionOffset;

            public SavedState(Parcelable superState, int year, int month, int day, long minDate,
                    long maxDate) {
                this(superState, year, month, day, minDate, maxDate, 0, 0, 0);
            }

            /**
             * Constructor called from {@link DatePicker#onSaveInstanceState()}
             */
            public SavedState(Parcelable superState, int year, int month, int day, long minDate,
                    long maxDate, int currentView, int listPosition, int listPositionOffset) {
                super(superState);
                mSelectedYear = year;
                mSelectedMonth = month;
                mSelectedDay = day;
                mMinDate = minDate;
                mMaxDate = maxDate;
                mCurrentView = currentView;
                mListPosition = listPosition;
                mListPositionOffset = listPositionOffset;
            }

            /**
             * Constructor called from {@link #CREATOR}
             */
            private SavedState(Parcel in) {
                super(in);
                mSelectedYear = in.readInt();
                mSelectedMonth = in.readInt();
                mSelectedDay = in.readInt();
                mMinDate = in.readLong();
                mMaxDate = in.readLong();
                mCurrentView = in.readInt();
                mListPosition = in.readInt();
                mListPositionOffset = in.readInt();
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
                super.writeToParcel(dest, flags);
                dest.writeInt(mSelectedYear);
                dest.writeInt(mSelectedMonth);
                dest.writeInt(mSelectedDay);
                dest.writeLong(mMinDate);
                dest.writeLong(mMaxDate);
                dest.writeInt(mCurrentView);
                dest.writeInt(mListPosition);
                dest.writeInt(mListPositionOffset);
            }

            public int getSelectedDay() {
                return mSelectedDay;
            }

            public int getSelectedMonth() {
                return mSelectedMonth;
            }

            public int getSelectedYear() {
                return mSelectedYear;
            }

            public long getMinDate() {
                return mMinDate;
            }

            public long getMaxDate() {
                return mMaxDate;
            }

            public int getCurrentView() {
                return mCurrentView;
            }

            public int getListPosition() {
                return mListPosition;
            }

            public int getListPositionOffset() {
                return mListPositionOffset;
            }

            @SuppressWarnings("all")
            // suppress unused and hiding
            public static final Parcelable.Creator<SavedState> CREATOR = new Creator<SavedState>() {

                public SavedState createFromParcel(Parcel in) {
                    return new SavedState(in);
                }

                public SavedState[] newArray(int size) {
                    return new SavedState[size];
                }
            };
        }
    }

    /**
     * framework-internal (hidden in AOSP). A callback interface for updating
     * input validity when the date picker when included into a dialog.
     */
    public interface ValidationCallback {
        void onValidationChanged(boolean valid);
    }
}
