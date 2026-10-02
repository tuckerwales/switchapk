package android.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import com.android.internal.util.InternalRes;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * This class is a calendar widget for displaying and selecting dates (AOSP
 * port). The material mode (DayPickerView) is ported; the holo-era week list
 * ({@code calendarViewMode} 0, used only by the pre-Holo {@code Theme}) falls
 * back to it, so the week-list styling setters are accepted and ignored.
 */
public class CalendarView extends FrameLayout {
    private static final String LOG_TAG = "CalendarView";

    private static final int MODE_HOLO = 0;
    private static final int MODE_MATERIAL = 1;

    private final CalendarViewDelegate mDelegate;

    /**
     * The callback used to indicate the user changes the date.
     */
    public interface OnDateChangeListener {

        /**
         * Called upon change of the selected day.
         *
         * @param view The view associated with this listener.
         * @param year The year that was set.
         * @param month The month that was set [0-11].
         * @param dayOfMonth The day of the month that was set.
         */
        void onSelectedDayChange(CalendarView view, int year, int month, int dayOfMonth);
    }

    public CalendarView(Context context) {
        this(context, null);
    }

    public CalendarView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.calendarViewStyle);
    }

    public CalendarView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CalendarView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, InternalRes.attrs("calendarViewMode"), defStyleAttr, defStyleRes);
        final int mode = a.getInt(0, MODE_HOLO);
        a.recycle();

        switch (mode) {
            case MODE_HOLO:
                Log.w(LOG_TAG, "holo CalendarView week list is not ported; using the material calendar");
                mDelegate = new CalendarViewMaterialDelegate(
                        this, context, attrs, defStyleAttr, defStyleRes);
                break;
            case MODE_MATERIAL:
                mDelegate = new CalendarViewMaterialDelegate(
                        this, context, attrs, defStyleAttr, defStyleRes);
                break;
            default:
                throw new IllegalArgumentException("invalid calendarViewMode attribute");
        }
    }

    /**
     * Sets the number of weeks to be shown.
     *
     * @deprecated No longer used by Material-style CalendarView.
     */
    @Deprecated
    public void setShownWeekCount(int count) {
        mDelegate.setShownWeekCount(count);
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public int getShownWeekCount() {
        return mDelegate.getShownWeekCount();
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public void setSelectedWeekBackgroundColor(int color) {
        mDelegate.setSelectedWeekBackgroundColor(color);
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public int getSelectedWeekBackgroundColor() {
        return mDelegate.getSelectedWeekBackgroundColor();
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public void setFocusedMonthDateColor(int color) {
        mDelegate.setFocusedMonthDateColor(color);
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public int getFocusedMonthDateColor() {
        return mDelegate.getFocusedMonthDateColor();
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public void setUnfocusedMonthDateColor(int color) {
        mDelegate.setUnfocusedMonthDateColor(color);
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public int getUnfocusedMonthDateColor() {
        return mDelegate.getUnfocusedMonthDateColor();
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public void setWeekNumberColor(int color) {
        mDelegate.setWeekNumberColor(color);
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public int getWeekNumberColor() {
        return mDelegate.getWeekNumberColor();
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public void setWeekSeparatorLineColor(int color) {
        mDelegate.setWeekSeparatorLineColor(color);
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public int getWeekSeparatorLineColor() {
        return mDelegate.getWeekSeparatorLineColor();
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public void setSelectedDateVerticalBar(int resourceId) {
        mDelegate.setSelectedDateVerticalBar(resourceId);
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public void setSelectedDateVerticalBar(Drawable drawable) {
        mDelegate.setSelectedDateVerticalBar(drawable);
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public Drawable getSelectedDateVerticalBar() {
        return mDelegate.getSelectedDateVerticalBar();
    }

    /**
     * Sets the text appearance for the week day abbreviation of the calendar header.
     *
     * @param resourceId The text appearance resource id.
     */
    public void setWeekDayTextAppearance(int resourceId) {
        mDelegate.setWeekDayTextAppearance(resourceId);
    }

    public int getWeekDayTextAppearance() {
        return mDelegate.getWeekDayTextAppearance();
    }

    /**
     * Sets the text appearance for the calendar dates.
     *
     * @param resourceId The text appearance resource id.
     */
    public void setDateTextAppearance(int resourceId) {
        mDelegate.setDateTextAppearance(resourceId);
    }

    public int getDateTextAppearance() {
        return mDelegate.getDateTextAppearance();
    }

    /**
     * Gets the minimal date supported by this {@link CalendarView} in milliseconds
     * since January 1, 1970 00:00:00 in {@link java.util.TimeZone#getDefault()} time zone.
     */
    public long getMinDate() {
        return mDelegate.getMinDate();
    }

    public void setMinDate(long minDate) {
        mDelegate.setMinDate(minDate);
    }

    public long getMaxDate() {
        return mDelegate.getMaxDate();
    }

    public void setMaxDate(long maxDate) {
        mDelegate.setMaxDate(maxDate);
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public void setShowWeekNumber(boolean showWeekNumber) {
        mDelegate.setShowWeekNumber(showWeekNumber);
    }

    /** @deprecated No longer used by Material-style CalendarView. */
    @Deprecated
    public boolean getShowWeekNumber() {
        return mDelegate.getShowWeekNumber();
    }

    public int getFirstDayOfWeek() {
        return mDelegate.getFirstDayOfWeek();
    }

    public void setFirstDayOfWeek(int firstDayOfWeek) {
        mDelegate.setFirstDayOfWeek(firstDayOfWeek);
    }

    /**
     * Sets the listener to be notified upon selected date change.
     *
     * @param listener The listener to be notified.
     */
    public void setOnDateChangeListener(OnDateChangeListener listener) {
        mDelegate.setOnDateChangeListener(listener);
    }

    /**
     * Gets the selected date in milliseconds since January 1, 1970 00:00:00 in
     * {@link java.util.TimeZone#getDefault()} time zone.
     */
    public long getDate() {
        return mDelegate.getDate();
    }

    /**
     * Sets the selected date in milliseconds since January 1, 1970 00:00:00 in
     * {@link java.util.TimeZone#getDefault()} time zone.
     */
    public void setDate(long date) {
        mDelegate.setDate(date);
    }

    /**
     * Sets the selected date in milliseconds since January 1, 1970 00:00:00 in
     * {@link java.util.TimeZone#getDefault()} time zone.
     *
     * @param date The date.
     * @param animate Whether to animate the scroll to the current date.
     * @param center Whether to center the current date even if it is already visible.
     */
    public void setDate(long date, boolean animate, boolean center) {
        mDelegate.setDate(date, animate, center);
    }

    /** framework-internal (hidden in AOSP). */
    public boolean getBoundsForDate(long date, Rect outBounds) {
        return mDelegate.getBoundsForDate(date, outBounds);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDelegate.onConfigurationChanged(newConfig);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return CalendarView.class.getName();
    }

    /**
     * A delegate interface that defined the public API of the CalendarView. Allows different
     * CalendarView implementations. This would need to be implemented by the CalendarView delegates
     * for the real behavior.
     */
    interface CalendarViewDelegate {
        void setShownWeekCount(int count);
        int getShownWeekCount();

        void setSelectedWeekBackgroundColor(int color);
        int getSelectedWeekBackgroundColor();

        void setFocusedMonthDateColor(int color);
        int getFocusedMonthDateColor();

        void setUnfocusedMonthDateColor(int color);
        int getUnfocusedMonthDateColor();

        void setWeekNumberColor(int color);
        int getWeekNumberColor();

        void setWeekSeparatorLineColor(int color);
        int getWeekSeparatorLineColor();

        void setSelectedDateVerticalBar(int resourceId);
        void setSelectedDateVerticalBar(Drawable drawable);
        Drawable getSelectedDateVerticalBar();

        void setWeekDayTextAppearance(int resourceId);
        int getWeekDayTextAppearance();

        void setDateTextAppearance(int resourceId);
        int getDateTextAppearance();

        void setMinDate(long minDate);
        long getMinDate();

        void setMaxDate(long maxDate);
        long getMaxDate();

        void setShowWeekNumber(boolean showWeekNumber);
        boolean getShowWeekNumber();

        void setFirstDayOfWeek(int firstDayOfWeek);
        int getFirstDayOfWeek();

        void setDate(long date);
        void setDate(long date, boolean animate, boolean center);
        long getDate();

        boolean getBoundsForDate(long date, Rect outBounds);

        void setOnDateChangeListener(OnDateChangeListener listener);

        void onConfigurationChanged(Configuration newConfig);
    }

    /**
     * An abstract class which can be used as a start for CalendarView implementations
     */
    abstract static class AbstractCalendarViewDelegate implements CalendarViewDelegate {
        /** The default minimal date. */
        protected static final String DEFAULT_MIN_DATE = "01/01/1900";

        /** The default maximal date. */
        protected static final String DEFAULT_MAX_DATE = "01/01/2100";

        protected CalendarView mDelegator;
        protected Context mContext;
        protected Locale mCurrentLocale;

        AbstractCalendarViewDelegate(CalendarView delegator, Context context) {
            mDelegator = delegator;
            mContext = context;

            // Initialization based on locale
            setCurrentLocale(Locale.getDefault());
        }

        protected void setCurrentLocale(Locale locale) {
            if (locale.equals(mCurrentLocale)) {
                return;
            }
            mCurrentLocale = locale;
        }

        @Override
        public void setShownWeekCount(int count) {
            // Deprecated.
        }

        @Override
        public int getShownWeekCount() {
            // Deprecated.
            return 0;
        }

        @Override
        public void setSelectedWeekBackgroundColor(int color) {
            // Deprecated.
        }

        @Override
        public int getSelectedWeekBackgroundColor() {
            return 0;
        }

        @Override
        public void setFocusedMonthDateColor(int color) {
            // Deprecated.
        }

        @Override
        public int getFocusedMonthDateColor() {
            return 0;
        }

        @Override
        public void setUnfocusedMonthDateColor(int color) {
            // Deprecated.
        }

        @Override
        public int getUnfocusedMonthDateColor() {
            return 0;
        }

        @Override
        public void setWeekNumberColor(int color) {
            // Deprecated.
        }

        @Override
        public int getWeekNumberColor() {
            // Deprecated.
            return 0;
        }

        @Override
        public void setWeekSeparatorLineColor(int color) {
            // Deprecated.
        }

        @Override
        public int getWeekSeparatorLineColor() {
            // Deprecated.
            return 0;
        }

        @Override
        public void setSelectedDateVerticalBar(int resId) {
            // Deprecated.
        }

        @Override
        public void setSelectedDateVerticalBar(Drawable drawable) {
            // Deprecated.
        }

        @Override
        public Drawable getSelectedDateVerticalBar() {
            // Deprecated.
            return null;
        }

        @Override
        public void setShowWeekNumber(boolean showWeekNumber) {
            // Deprecated.
        }

        @Override
        public boolean getShowWeekNumber() {
            // Deprecated.
            return false;
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            // Nothing to do here, configuration changes are already propagated
            // by ViewGroup.
        }
    }

    /** String for parsing dates. */
    private static final String DATE_FORMAT = "MM/dd/yyyy";

    /** Date format for parsing dates. */
    private static final DateFormat DATE_FORMATTER = new SimpleDateFormat(DATE_FORMAT);

    /**
     * framework-internal (hidden in AOSP). Parses the given {@code date} and in
     * case of success sets the result to the {@code outDate}.
     *
     * @return True if the date was parsed.
     */
    public static boolean parseDate(String date, Calendar outDate) {
        if (date == null || date.isEmpty()) {
            return false;
        }

        try {
            final Date parsedDate = DATE_FORMATTER.parse(date);
            outDate.setTime(parsedDate);
            return true;
        } catch (ParseException e) {
            Log.w(LOG_TAG, "Date: " + date + " not in format: " + DATE_FORMAT);
            return false;
        }
    }
}
