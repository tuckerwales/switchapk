package android.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import com.android.internal.util.InternalRes;
import java.util.Calendar;
import java.util.Locale;

/**
 * framework-internal. The Holo CalendarView: a header and a list of week rows
 * (AOSP CalendarViewLegacyDelegate). {@code calendarViewMode} holo, which is
 * what {@code Widget.CalendarView} and the Holo themes select.
 */
class CalendarViewLegacyDelegate extends CalendarView.AbstractCalendarViewDelegate {
    private static final boolean DEFAULT_SHOW_WEEK_NUMBER = true;
    private static final long MILLIS_IN_DAY = 86400000L;
    private static final int DAYS_PER_WEEK = 7;
    private static final long MILLIS_IN_WEEK = DAYS_PER_WEEK * MILLIS_IN_DAY;
    /** Weeks past the first visible row before a scroll changes the focused month. */
    private static final int SCROLL_HYST_WEEKS = 2;
    private static final int GOTO_SCROLL_DURATION = 1000;
    private static final int ADJUSTMENT_SCROLL_DURATION = 500;
    private static final int SCROLL_CHANGE_DELAY = 40;
    private static final int DEFAULT_SHOWN_WEEK_COUNT = 6;
    private static final int DEFAULT_DATE_TEXT_SIZE = 14;
    private static final int UNSCALED_SELECTED_DATE_VERTICAL_BAR_WIDTH = 6;
    private static final int UNSCALED_WEEK_MIN_VISIBLE_HEIGHT = 12;
    private static final int UNSCALED_LIST_SCROLL_TOP_OFFSET = 2;
    private static final int UNSCALED_BOTTOM_BUFFER = 20;
    private static final int UNSCALED_WEEK_SEPARATOR_LINE_WIDTH = 1;
    private static final int DEFAULT_WEEK_DAY_TEXT_APPEARANCE_RES_ID = -1;

    private static final int[] CALENDAR_ATTRS = {
        android.R.attr.showWeekNumber,
        android.R.attr.firstDayOfWeek,
        android.R.attr.minDate,
        android.R.attr.maxDate,
        android.R.attr.shownWeekCount,
        android.R.attr.selectedWeekBackgroundColor,
        android.R.attr.focusedMonthDateColor,
        android.R.attr.unfocusedMonthDateColor,
        android.R.attr.weekSeparatorLineColor,
        android.R.attr.weekNumberColor,
        android.R.attr.selectedDateVerticalBar,
        android.R.attr.dateTextAppearance,
        android.R.attr.weekDayTextAppearance,
    };

    private final int mWeekSeparatorLineWidth;
    private int mDateTextSize;
    private Drawable mSelectedDateVerticalBar;
    private final int mSelectedDateVerticalBarWidth;
    private int mSelectedWeekBackgroundColor;
    private int mFocusedMonthDateColor;
    private int mUnfocusedMonthDateColor;
    private int mWeekSeparatorLineColor;
    private int mWeekNumberColor;
    private int mWeekDayTextAppearanceResId;
    private int mDateTextAppearanceResId;
    private int mListScrollTopOffset = 2;
    private int mWeekMinVisibleHeight = 12;
    private int mBottomBuffer = 20;
    private int mShownWeekCount;
    private boolean mShowWeekNumber;
    private int mDaysPerWeek = 7;
    private float mFriction = 0.05f;
    private float mVelocityScale = 0.333f;
    private WeeksAdapter mAdapter;
    private ListView mListView;
    private TextView mMonthName;
    private ViewGroup mDayNamesHeader;
    private String[] mDayNamesShort;
    private String[] mDayNamesLong;
    private int mFirstDayOfWeek;
    private int mCurrentMonthDisplayed = -1;
    private long mPreviousScrollPosition;
    private boolean mIsScrollingUp = false;
    private int mPreviousScrollState = AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
    private int mCurrentScrollState = AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
    private CalendarView.OnDateChangeListener mOnDateChangeListener;
    private final ScrollStateRunnable mScrollStateChangedRunnable = new ScrollStateRunnable();
    private Calendar mTempDate;
    private Calendar mFirstDayOfMonth;
    private Calendar mMinDate;
    private Calendar mMaxDate;

    CalendarViewLegacyDelegate(CalendarView delegator, Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(delegator, context);

        final TypedArray a = context.obtainStyledAttributes(attrs, CALENDAR_ATTRS, defStyleAttr, defStyleRes);
        mShowWeekNumber = a.getBoolean(0, DEFAULT_SHOW_WEEK_NUMBER);
        mFirstDayOfWeek = a.getInt(1, Calendar.getInstance().getFirstDayOfWeek());
        if (!CalendarView.parseDate(a.getString(2), mMinDate)) {
            CalendarView.parseDate(DEFAULT_MIN_DATE, mMinDate);
        }
        if (!CalendarView.parseDate(a.getString(3), mMaxDate)) {
            CalendarView.parseDate(DEFAULT_MAX_DATE, mMaxDate);
        }
        if (mMaxDate.before(mMinDate)) {
            throw new IllegalArgumentException("Max date cannot be before min date.");
        }
        mShownWeekCount = a.getInt(4, DEFAULT_SHOWN_WEEK_COUNT);
        mSelectedWeekBackgroundColor = a.getColor(5, 0);
        mFocusedMonthDateColor = a.getColor(6, 0);
        mUnfocusedMonthDateColor = a.getColor(7, 0);
        mWeekSeparatorLineColor = a.getColor(8, 0);
        mWeekNumberColor = a.getColor(9, 0);
        mSelectedDateVerticalBar = a.getDrawable(10);
        if (mSelectedDateVerticalBar != null) mSelectedDateVerticalBar = mSelectedDateVerticalBar.mutate();
        mDateTextAppearanceResId = a.getResourceId(11, android.R.style.TextAppearance_Small);
        updateDateTextSize();
        mWeekDayTextAppearanceResId = a.getResourceId(12, DEFAULT_WEEK_DAY_TEXT_APPEARANCE_RES_ID);
        a.recycle();

        DisplayMetrics displayMetrics = mDelegator.getResources().getDisplayMetrics();
        mWeekMinVisibleHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                UNSCALED_WEEK_MIN_VISIBLE_HEIGHT, displayMetrics);
        mListScrollTopOffset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                UNSCALED_LIST_SCROLL_TOP_OFFSET, displayMetrics);
        mBottomBuffer = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                UNSCALED_BOTTOM_BUFFER, displayMetrics);
        mSelectedDateVerticalBarWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                UNSCALED_SELECTED_DATE_VERTICAL_BAR_WIDTH, displayMetrics);
        mWeekSeparatorLineWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                UNSCALED_WEEK_SEPARATOR_LINE_WIDTH, displayMetrics);

        LayoutInflater layoutInflater = LayoutInflater.from(mContext);
        View content = layoutInflater.inflate(InternalRes.layout("calendar_view"), null, false);
        mDelegator.addView(content);

        mListView = (ListView) mDelegator.findViewById(android.R.id.list);
        mDayNamesHeader = (ViewGroup) content.findViewById(InternalRes.viewId("day_names"));
        mMonthName = (TextView) content.findViewById(InternalRes.viewId("month_name"));

        setUpHeader();
        setUpListView();
        setUpAdapter();

        // Today, or the nearest end of the range when today is outside it.
        mTempDate.setTimeInMillis(System.currentTimeMillis());
        if (mTempDate.before(mMinDate)) {
            goTo(mMinDate, false, true, true);
        } else if (mMaxDate.before(mTempDate)) {
            goTo(mMaxDate, false, true, true);
        } else {
            goTo(mTempDate, false, true, true);
        }
        mDelegator.invalidate();
    }

    @Override
    public void setShownWeekCount(int count) {
        if (mShownWeekCount != count) {
            mShownWeekCount = count;
            mDelegator.invalidate();
        }
    }

    @Override
    public int getShownWeekCount() {
        return mShownWeekCount;
    }

    @Override
    public void setSelectedWeekBackgroundColor(int color) {
        if (mSelectedWeekBackgroundColor != color) {
            mSelectedWeekBackgroundColor = color;
            invalidateSelectedWeeks();
        }
    }

    @Override
    public int getSelectedWeekBackgroundColor() {
        return mSelectedWeekBackgroundColor;
    }

    @Override
    public void setFocusedMonthDateColor(int color) {
        if (mFocusedMonthDateColor != color) {
            mFocusedMonthDateColor = color;
            invalidateFocusedWeeks();
        }
    }

    @Override
    public int getFocusedMonthDateColor() {
        return mFocusedMonthDateColor;
    }

    @Override
    public void setUnfocusedMonthDateColor(int color) {
        if (mUnfocusedMonthDateColor != color) {
            mUnfocusedMonthDateColor = color;
            final int childCount = mListView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                WeekView weekView = (WeekView) mListView.getChildAt(i);
                if (weekView.mHasUnfocusedDay) weekView.invalidate();
            }
        }
    }

    @Override
    public int getUnfocusedMonthDateColor() {
        return mUnfocusedMonthDateColor;
    }

    @Override
    public void setWeekNumberColor(int color) {
        if (mWeekNumberColor != color) {
            mWeekNumberColor = color;
            if (mShowWeekNumber) invalidateAllWeekViews();
        }
    }

    @Override
    public int getWeekNumberColor() {
        return mWeekNumberColor;
    }

    @Override
    public void setWeekSeparatorLineColor(int color) {
        if (mWeekSeparatorLineColor != color) {
            mWeekSeparatorLineColor = color;
            invalidateAllWeekViews();
        }
    }

    @Override
    public int getWeekSeparatorLineColor() {
        return mWeekSeparatorLineColor;
    }

    @Override
    public void setSelectedDateVerticalBar(int resourceId) {
        setSelectedDateVerticalBar(mDelegator.getContext().getDrawable(resourceId));
    }

    @Override
    public void setSelectedDateVerticalBar(Drawable drawable) {
        if (mSelectedDateVerticalBar != drawable) {
            mSelectedDateVerticalBar = drawable == null ? null : drawable.mutate();
            invalidateSelectedWeeks();
        }
    }

    @Override
    public Drawable getSelectedDateVerticalBar() {
        return mSelectedDateVerticalBar;
    }

    @Override
    public void setWeekDayTextAppearance(int resourceId) {
        if (mWeekDayTextAppearanceResId != resourceId) {
            mWeekDayTextAppearanceResId = resourceId;
            setUpHeader();
        }
    }

    @Override
    public int getWeekDayTextAppearance() {
        return mWeekDayTextAppearanceResId;
    }

    @Override
    public void setDateTextAppearance(int resourceId) {
        if (mDateTextAppearanceResId != resourceId) {
            mDateTextAppearanceResId = resourceId;
            updateDateTextSize();
            invalidateAllWeekViews();
        }
    }

    @Override
    public int getDateTextAppearance() {
        return mDateTextAppearanceResId;
    }

    @Override
    public void setMinDate(long minDate) {
        mTempDate.setTimeInMillis(minDate);
        if (isSameDate(mTempDate, mMinDate)) return;
        mMinDate.setTimeInMillis(minDate);
        // The adapter indexes weeks from the min date, so the selected day has to stay inside it.
        Calendar date = mAdapter.mSelectedDate;
        if (date.before(mMinDate)) mAdapter.setSelectedDay(mMinDate);
        mAdapter.init();
        if (date.before(mMinDate)) {
            setDate(mTempDate.getTimeInMillis());
        } else {
            // setDate would no-op (the day did not change) and the list would keep stale positions.
            goTo(date, false, true, false);
        }
    }

    @Override
    public long getMinDate() {
        return mMinDate.getTimeInMillis();
    }

    @Override
    public void setMaxDate(long maxDate) {
        mTempDate.setTimeInMillis(maxDate);
        if (isSameDate(mTempDate, mMaxDate)) return;
        mMaxDate.setTimeInMillis(maxDate);
        mAdapter.init();
        Calendar date = mAdapter.mSelectedDate;
        if (date.after(mMaxDate)) setDate(mMaxDate.getTimeInMillis());
        else goTo(date, false, true, false);
    }

    @Override
    public long getMaxDate() {
        return mMaxDate.getTimeInMillis();
    }

    @Override
    public void setShowWeekNumber(boolean showWeekNumber) {
        if (mShowWeekNumber == showWeekNumber) return;
        mShowWeekNumber = showWeekNumber;
        mAdapter.notifyDataSetChanged();
        setUpHeader();
    }

    @Override
    public boolean getShowWeekNumber() {
        return mShowWeekNumber;
    }

    @Override
    public void setFirstDayOfWeek(int firstDayOfWeek) {
        if (mFirstDayOfWeek == firstDayOfWeek) return;
        mFirstDayOfWeek = firstDayOfWeek;
        mAdapter.init();
        mAdapter.notifyDataSetChanged();
        setUpHeader();
    }

    @Override
    public int getFirstDayOfWeek() {
        return mFirstDayOfWeek;
    }

    @Override
    public void setDate(long date) {
        setDate(date, false, false);
    }

    @Override
    public void setDate(long date, boolean animate, boolean center) {
        mTempDate.setTimeInMillis(date);
        if (isSameDate(mTempDate, mAdapter.mSelectedDate)) return;
        goTo(mTempDate, animate, true, center);
    }

    @Override
    public long getDate() {
        return mAdapter.mSelectedDate.getTimeInMillis();
    }

    @Override
    public void setOnDateChangeListener(CalendarView.OnDateChangeListener listener) {
        mOnDateChangeListener = listener;
    }

    @Override
    public boolean getBoundsForDate(long date, Rect outBounds) {
        Calendar calendarDate = Calendar.getInstance();
        calendarDate.setTimeInMillis(date);
        // getCount() is the adapter size (every week back to 1900). Only attached rows have bounds.
        final int childCount = mListView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            WeekView currWeekView = (WeekView) mListView.getChildAt(i);
            if (currWeekView != null && currWeekView.getBoundsForDate(calendarDate, outBounds)) {
                final int[] weekViewPositionOnScreen = new int[2];
                final int[] delegatorPositionOnScreen = new int[2];
                currWeekView.getLocationOnScreen(weekViewPositionOnScreen);
                mDelegator.getLocationOnScreen(delegatorPositionOnScreen);
                final int extraVerticalOffset = weekViewPositionOnScreen[1] - delegatorPositionOnScreen[1];
                outBounds.top += extraVerticalOffset;
                outBounds.bottom += extraVerticalOffset;
                return true;
            }
        }
        return false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (newConfig.locale != null) setCurrentLocale(newConfig.locale);
    }

    @Override
    protected void setCurrentLocale(Locale locale) {
        super.setCurrentLocale(locale);
        mTempDate = getCalendarForLocale(mTempDate, locale);
        mFirstDayOfMonth = getCalendarForLocale(mFirstDayOfMonth, locale);
        mMinDate = getCalendarForLocale(mMinDate, locale);
        mMaxDate = getCalendarForLocale(mMaxDate, locale);
    }

    private void updateDateTextSize() {
        TypedArray dateTextAppearance = mDelegator.getContext().obtainStyledAttributes(
                mDateTextAppearanceResId, new int[] {android.R.attr.textSize});
        mDateTextSize = dateTextAppearance.getDimensionPixelSize(0, DEFAULT_DATE_TEXT_SIZE);
        dateTextAppearance.recycle();
    }

    private void invalidateAllWeekViews() {
        final int childCount = mListView.getChildCount();
        for (int i = 0; i < childCount; i++) mListView.getChildAt(i).invalidate();
    }

    private void invalidateSelectedWeeks() {
        final int childCount = mListView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            WeekView weekView = (WeekView) mListView.getChildAt(i);
            if (weekView.mHasSelectedDay) weekView.invalidate();
        }
    }

    private void invalidateFocusedWeeks() {
        final int childCount = mListView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            WeekView weekView = (WeekView) mListView.getChildAt(i);
            if (weekView.mHasFocusedDay) weekView.invalidate();
        }
    }

    private static Calendar getCalendarForLocale(Calendar oldCalendar, Locale locale) {
        if (oldCalendar == null) return Calendar.getInstance(locale);
        final long currentTimeMillis = oldCalendar.getTimeInMillis();
        Calendar newCalendar = Calendar.getInstance(locale);
        newCalendar.setTimeInMillis(currentTimeMillis);
        return newCalendar;
    }

    private static boolean isSameDate(Calendar firstDate, Calendar secondDate) {
        return firstDate.get(Calendar.DAY_OF_YEAR) == secondDate.get(Calendar.DAY_OF_YEAR)
                && firstDate.get(Calendar.YEAR) == secondDate.get(Calendar.YEAR);
    }

    private void setUpAdapter() {
        if (mAdapter == null) {
            mAdapter = new WeeksAdapter(mContext);
            mAdapter.registerDataSetObserver(new DataSetObserver() {
                @Override
                public void onChanged() {
                    if (mOnDateChangeListener != null) {
                        Calendar selectedDay = mAdapter.getSelectedDay();
                        mOnDateChangeListener.onSelectedDayChange(mDelegator,
                                selectedDay.get(Calendar.YEAR),
                                selectedDay.get(Calendar.MONTH),
                                selectedDay.get(Calendar.DAY_OF_MONTH));
                    }
                }
            });
            mListView.setAdapter(mAdapter);
        }
        mAdapter.notifyDataSetChanged();
    }

    private void setUpHeader() {
        mDayNamesShort = new String[mDaysPerWeek];
        mDayNamesLong = new String[mDaysPerWeek];
        for (int i = mFirstDayOfWeek, count = mFirstDayOfWeek + mDaysPerWeek; i < count; i++) {
            int calendarDay = (i > Calendar.SATURDAY) ? i - Calendar.SATURDAY : i;
            mDayNamesShort[i - mFirstDayOfWeek] = DateUtils.getDayOfWeekString(calendarDay,
                    DateUtils.LENGTH_SHORTEST);
            mDayNamesLong[i - mFirstDayOfWeek] = DateUtils.getDayOfWeekString(calendarDay,
                    DateUtils.LENGTH_LONG);
        }

        TextView label = (TextView) mDayNamesHeader.getChildAt(0);
        label.setVisibility(mShowWeekNumber ? View.VISIBLE : View.GONE);
        for (int i = 1, count = mDayNamesHeader.getChildCount(); i < count; i++) {
            label = (TextView) mDayNamesHeader.getChildAt(i);
            if (mWeekDayTextAppearanceResId > -1) label.setTextAppearance(mWeekDayTextAppearanceResId);
            if (i < mDaysPerWeek + 1) {
                label.setText(mDayNamesShort[i - 1]);
                label.setContentDescription(mDayNamesLong[i - 1]);
                label.setVisibility(View.VISIBLE);
            } else {
                label.setVisibility(View.GONE);
            }
        }
        mDayNamesHeader.invalidate();
    }

    private void setUpListView() {
        mListView.setDivider(null);
        mListView.setItemsCanFocus(true);
        mListView.setVerticalScrollBarEnabled(false);
        // The list style's selector would paint the positioned week, which is the first week of
        // the month, on top of the week view's own selected-week highlight.
        mListView.setSelector(android.R.color.transparent);
        mListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                CalendarViewLegacyDelegate.this.onScrollStateChanged(view, scrollState);
            }

            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                    int totalItemCount) {
                CalendarViewLegacyDelegate.this.onScroll(view, firstVisibleItem, visibleItemCount,
                        totalItemCount);
            }
        });
        mListView.setFriction(mFriction);
        mListView.setVelocityScale(mVelocityScale);
    }

    /**
     * Moves the list so {@code date} is selected. When it is outside the visible weeks (or
     * {@code forceScroll} is set) the first of its month is placed at the top.
     */
    private void goTo(Calendar date, boolean animate, boolean setSelected, boolean forceScroll) {
        if (date.before(mMinDate) || date.after(mMaxDate)) {
            throw new IllegalArgumentException("Time must be between min and max date");
        }
        int firstFullyVisiblePosition = mListView.getFirstVisiblePosition();
        View firstChild = mListView.getChildAt(0);
        if (firstChild != null && firstChild.getTop() < 0) firstFullyVisiblePosition++;
        int lastFullyVisiblePosition = firstFullyVisiblePosition + mShownWeekCount - 1;
        if (firstChild != null && firstChild.getTop() > mBottomBuffer) lastFullyVisiblePosition--;
        if (setSelected) mAdapter.setSelectedDay(date);
        int position = getWeeksSinceMinDate(date);
        if (position < firstFullyVisiblePosition || position > lastFullyVisiblePosition || forceScroll) {
            mFirstDayOfMonth.setTimeInMillis(date.getTimeInMillis());
            mFirstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1);
            setMonthDisplayed(mFirstDayOfMonth);
            if (mFirstDayOfMonth.before(mMinDate)) position = 0;
            else position = getWeeksSinceMinDate(mFirstDayOfMonth);
            mPreviousScrollState = AbsListView.OnScrollListener.SCROLL_STATE_FLING;
            if (animate) {
                mListView.smoothScrollToPositionFromTop(position, mListScrollTopOffset, GOTO_SCROLL_DURATION);
            } else {
                mListView.setSelectionFromTop(position, mListScrollTopOffset);
                onScrollStateChanged(mListView, AbsListView.OnScrollListener.SCROLL_STATE_IDLE);
            }
        } else if (setSelected) {
            setMonthDisplayed(date);
        }
    }

    private void onScrollStateChanged(AbsListView view, int scrollState) {
        mScrollStateChangedRunnable.doScrollStateChange(view, scrollState);
    }

    private void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        WeekView child = (WeekView) view.getChildAt(0);
        if (child == null) return;
        long currScroll = view.getFirstVisiblePosition() * (long) child.getHeight() - child.getBottom();
        if (currScroll < mPreviousScrollPosition) mIsScrollingUp = true;
        else if (currScroll > mPreviousScrollPosition) mIsScrollingUp = false;
        else return;

        // Two full weeks of the incoming month when scrolling toward earlier dates, and the
        // first day of the month reaching the top when scrolling toward later dates.
        int offset = child.getBottom() < mWeekMinVisibleHeight ? 1 : 0;
        if (mIsScrollingUp) child = (WeekView) view.getChildAt(SCROLL_HYST_WEEKS + offset);
        else if (offset != 0) child = (WeekView) view.getChildAt(offset);
        if (child != null) {
            int month = mIsScrollingUp ? child.getMonthOfFirstWeekDay() : child.getMonthOfLastWeekDay();
            int monthDiff;
            if (mCurrentMonthDisplayed == 11 && month == 0) monthDiff = 1;
            else if (mCurrentMonthDisplayed == 0 && month == 11) monthDiff = -1;
            else monthDiff = month - mCurrentMonthDisplayed;
            if ((!mIsScrollingUp && monthDiff > 0) || (mIsScrollingUp && monthDiff < 0)) {
                Calendar firstDay = child.getFirstDay();
                firstDay.add(Calendar.DAY_OF_MONTH, mIsScrollingUp ? -DAYS_PER_WEEK : DAYS_PER_WEEK);
                setMonthDisplayed(firstDay);
            }
        }
        mPreviousScrollPosition = currScroll;
        mPreviousScrollState = mCurrentScrollState;
    }

    private void setMonthDisplayed(Calendar calendar) {
        mCurrentMonthDisplayed = calendar.get(Calendar.MONTH);
        mAdapter.setFocusMonth(mCurrentMonthDisplayed);
        final int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_MONTH_DAY
                | DateUtils.FORMAT_SHOW_YEAR;
        final long millis = calendar.getTimeInMillis();
        mMonthName.setText(DateUtils.formatDateRange(mContext, millis, millis, flags));
        mMonthName.invalidate();
    }

    /** Weeks from the min date's week to {@code date}, using local midnights so DST does not drop a day. */
    private int getWeeksSinceMinDate(Calendar date) {
        if (date.before(mMinDate)) {
            throw new IllegalArgumentException("fromDate: " + mMinDate.getTime()
                    + " does not precede toDate: " + date.getTime());
        }
        long endTimeMillis = date.getTimeInMillis() + date.getTimeZone().getOffset(date.getTimeInMillis());
        long startTimeMillis = mMinDate.getTimeInMillis()
                + mMinDate.getTimeZone().getOffset(mMinDate.getTimeInMillis());
        long dayOffsetMillis = (mMinDate.get(Calendar.DAY_OF_WEEK) - mFirstDayOfWeek) * MILLIS_IN_DAY;
        return (int) ((endTimeMillis - startTimeMillis + dayOffsetMillis) / MILLIS_IN_WEEK);
    }

    /** Snaps a partial week to a row boundary once scrolling settles. */
    private class ScrollStateRunnable implements Runnable {
        private AbsListView mView;
        private int mNewState;

        public void doScrollStateChange(AbsListView view, int scrollState) {
            mView = view;
            mNewState = scrollState;
            mDelegator.removeCallbacks(this);
            mDelegator.postDelayed(this, SCROLL_CHANGE_DELAY);
        }

        public void run() {
            mCurrentScrollState = mNewState;
            if (mNewState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE
                    && mPreviousScrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                View child = mView.getChildAt(0);
                if (child == null) return;
                int dist = child.getBottom() - mListScrollTopOffset;
                if (dist > mListScrollTopOffset) {
                    if (mIsScrollingUp) {
                        mView.smoothScrollBy(dist - child.getHeight(), ADJUSTMENT_SCROLL_DURATION);
                    } else {
                        mView.smoothScrollBy(dist, ADJUSTMENT_SCROLL_DURATION);
                    }
                }
            }
            mPreviousScrollState = mNewState;
        }
    }

    /** One row per week from the min date through the max date. */
    private class WeeksAdapter extends BaseAdapter implements View.OnTouchListener {
        private int mSelectedWeek;
        private final GestureDetector mGestureDetector;
        private int mFocusedMonth;
        private final Calendar mSelectedDate = Calendar.getInstance();
        private int mTotalWeekCount;

        public WeeksAdapter(Context context) {
            mGestureDetector = new GestureDetector(context, new CalendarGestureListener());
            init();
        }

        private void init() {
            mSelectedWeek = getWeeksSinceMinDate(mSelectedDate);
            mTotalWeekCount = getWeeksSinceMinDate(mMaxDate);
            if (mMinDate.get(Calendar.DAY_OF_WEEK) != mFirstDayOfWeek
                    || mMaxDate.get(Calendar.DAY_OF_WEEK) != mFirstDayOfWeek) {
                mTotalWeekCount++;
            }
            notifyDataSetChanged();
        }

        public void setSelectedDay(Calendar selectedDay) {
            if (selectedDay.get(Calendar.DAY_OF_YEAR) == mSelectedDate.get(Calendar.DAY_OF_YEAR)
                    && selectedDay.get(Calendar.YEAR) == mSelectedDate.get(Calendar.YEAR)) {
                return;
            }
            mSelectedDate.setTimeInMillis(selectedDay.getTimeInMillis());
            mSelectedWeek = getWeeksSinceMinDate(mSelectedDate);
            mFocusedMonth = mSelectedDate.get(Calendar.MONTH);
            notifyDataSetChanged();
        }

        public Calendar getSelectedDay() {
            return mSelectedDate;
        }

        @Override
        public int getCount() {
            return mTotalWeekCount;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            WeekView weekView;
            if (convertView instanceof WeekView) {
                weekView = (WeekView) convertView;
            } else {
                weekView = new WeekView(mContext);
                weekView.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                weekView.setClickable(true);
                weekView.setOnTouchListener(this);
            }
            int selectedWeekDay = (mSelectedWeek == position) ? mSelectedDate.get(Calendar.DAY_OF_WEEK) : -1;
            weekView.init(position, selectedWeekDay, mFocusedMonth);
            return weekView;
        }

        /**
         * The focused month changed. Visible rows are rebound here because a fling drops
         * {@code requestLayout}, so {@code notifyDataSetChanged} would not redraw them until
         * the list next laid out.
         */
        public void setFocusMonth(int month) {
            if (mFocusedMonth == month) return;
            mFocusedMonth = month;
            final int count = mListView.getChildCount();
            final int first = mListView.getFirstVisiblePosition();
            for (int i = 0; i < count; i++) {
                View child = mListView.getChildAt(i);
                if (!(child instanceof WeekView)) continue;
                int position = first + i;
                int selectedWeekDay = (mSelectedWeek == position)
                        ? mSelectedDate.get(Calendar.DAY_OF_WEEK) : -1;
                ((WeekView) child).init(position, selectedWeekDay, mFocusedMonth);
                child.invalidate();
            }
            notifyDataSetChanged();
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mListView.isEnabled() && mGestureDetector.onTouchEvent(event)) {
                WeekView weekView = (WeekView) v;
                if (!weekView.getDayFromLocation(event.getX(), mTempDate)) return true;
                // Whole weeks are drawn, so a tap can land on a day outside the min/max range.
                if (mTempDate.before(mMinDate) || mTempDate.after(mMaxDate)) return true;
                onDateTapped(mTempDate);
                return true;
            }
            return false;
        }

        private void onDateTapped(Calendar day) {
            setSelectedDay(day);
            setMonthDisplayed(day);
        }

        class CalendarGestureListener extends GestureDetector.SimpleOnGestureListener {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true;
            }
        }
    }

    /** Draws one week: the week number, the day numbers, separators and the selected-day bars. */
    private class WeekView extends View {
        private final Rect mTempRect = new Rect();
        private final Paint mDrawPaint = new Paint();
        private final Paint mMonthNumDrawPaint = new Paint();
        private String[] mDayNumbers;
        private boolean[] mFocusDay;
        private boolean mHasFocusedDay;
        private boolean mHasUnfocusedDay;
        private Calendar mFirstDay;
        private int mMonthOfFirstWeekDay = -1;
        private int mLastWeekDayMonth = -1;
        private int mWeek = -1;
        private int mWidth;
        private int mHeight;
        private boolean mHasSelectedDay = false;
        private int mSelectedDay = -1;
        private int mNumCells;
        private int mSelectedLeft = -1;
        private int mSelectedRight = -1;

        public WeekView(Context context) {
            super(context);
            initializePaints();
        }

        /**
         * @param weekNumber weeks since the min date
         * @param selectedWeekDay {@link Calendar#DAY_OF_WEEK} of the selected day, or -1
         * @param focusedMonth the month whose days draw in the focused color [0-11]
         */
        public void init(int weekNumber, int selectedWeekDay, int focusedMonth) {
            mSelectedDay = selectedWeekDay;
            mHasSelectedDay = mSelectedDay != -1;
            mNumCells = mShowWeekNumber ? mDaysPerWeek + 1 : mDaysPerWeek;
            mWeek = weekNumber;
            mTempDate.setTimeInMillis(mMinDate.getTimeInMillis());
            mTempDate.add(Calendar.WEEK_OF_YEAR, mWeek);
            mTempDate.setFirstDayOfWeek(mFirstDayOfWeek);

            mDayNumbers = new String[mNumCells];
            mFocusDay = new boolean[mNumCells];

            int i = 0;
            if (mShowWeekNumber) {
                mDayNumbers[0] = String.format(Locale.getDefault(), "%d", mTempDate.get(Calendar.WEEK_OF_YEAR));
                i++;
            }
            int diff = mFirstDayOfWeek - mTempDate.get(Calendar.DAY_OF_WEEK);
            mTempDate.add(Calendar.DAY_OF_MONTH, diff);
            mFirstDay = (Calendar) mTempDate.clone();
            mMonthOfFirstWeekDay = mTempDate.get(Calendar.MONTH);

            mHasFocusedDay = false;
            // True only when every day of the week is outside the focused month (AOSP).
            mHasUnfocusedDay = true;
            for (; i < mNumCells; i++) {
                final boolean isFocusedDay = mTempDate.get(Calendar.MONTH) == focusedMonth;
                mFocusDay[i] = isFocusedDay;
                mHasFocusedDay |= isFocusedDay;
                mHasUnfocusedDay &= !isFocusedDay;
                if (mTempDate.before(mMinDate) || mTempDate.after(mMaxDate)) mDayNumbers[i] = "";
                else {
                    mDayNumbers[i] = String.format(Locale.getDefault(), "%d",
                            mTempDate.get(Calendar.DAY_OF_MONTH));
                }
                mTempDate.add(Calendar.DAY_OF_MONTH, 1);
            }
            if (mTempDate.get(Calendar.DAY_OF_MONTH) == 1) mTempDate.add(Calendar.DAY_OF_MONTH, -1);
            mLastWeekDayMonth = mTempDate.get(Calendar.MONTH);
            updateSelectionPositions();
        }

        private void initializePaints() {
            mDrawPaint.setFakeBoldText(false);
            mDrawPaint.setAntiAlias(true);
            mDrawPaint.setStyle(Paint.Style.FILL);
            mMonthNumDrawPaint.setFakeBoldText(true);
            mMonthNumDrawPaint.setAntiAlias(true);
            mMonthNumDrawPaint.setStyle(Paint.Style.FILL);
            mMonthNumDrawPaint.setTextAlign(Paint.Align.CENTER);
            mMonthNumDrawPaint.setTextSize(mDateTextSize);
        }

        public int getMonthOfFirstWeekDay() {
            return mMonthOfFirstWeekDay;
        }

        public int getMonthOfLastWeekDay() {
            return mLastWeekDayMonth;
        }

        public Calendar getFirstDay() {
            return mFirstDay;
        }

        public boolean getDayFromLocation(float x, Calendar outCalendar) {
            final boolean isLayoutRtl = isLayoutRtl();
            int start;
            int end;
            if (isLayoutRtl) {
                start = 0;
                end = mShowWeekNumber ? mWidth - mWidth / mNumCells : mWidth;
            } else {
                start = mShowWeekNumber ? mWidth / mNumCells : 0;
                end = mWidth;
            }
            if (x < start || x > end || end == start) return false;
            int dayPosition = (int) ((x - start) * mDaysPerWeek / (end - start));
            if (isLayoutRtl) dayPosition = mDaysPerWeek - 1 - dayPosition;
            if (dayPosition < 0 || dayPosition >= mDaysPerWeek) return false;
            outCalendar.setTimeInMillis(mFirstDay.getTimeInMillis());
            outCalendar.add(Calendar.DAY_OF_MONTH, dayPosition);
            return true;
        }

        public boolean getBoundsForDate(Calendar date, Rect outBounds) {
            if (mFirstDay == null || mWidth <= 0) return false;
            Calendar currDay = Calendar.getInstance();
            currDay.setTimeInMillis(mFirstDay.getTimeInMillis());
            for (int i = 0; i < mDaysPerWeek; i++) {
                if (date.get(Calendar.YEAR) == currDay.get(Calendar.YEAR)
                        && date.get(Calendar.MONTH) == currDay.get(Calendar.MONTH)
                        && date.get(Calendar.DAY_OF_MONTH) == currDay.get(Calendar.DAY_OF_MONTH)) {
                    int cellSize = mWidth / mNumCells;
                    if (isLayoutRtl()) {
                        outBounds.left = cellSize * (mShowWeekNumber ? (mNumCells - i - 2) : (mNumCells - i - 1));
                    } else {
                        outBounds.left = cellSize * (mShowWeekNumber ? i + 1 : i);
                    }
                    outBounds.top = 0;
                    outBounds.right = outBounds.left + cellSize;
                    outBounds.bottom = getHeight();
                    return true;
                }
                currDay.add(Calendar.DAY_OF_MONTH, 1);
            }
            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawBackground(canvas);
            drawWeekNumbersAndDates(canvas);
            drawWeekSeparators(canvas);
            drawSelectedDateVerticalBars(canvas);
        }

        private void drawBackground(Canvas canvas) {
            if (!mHasSelectedDay) return;
            mDrawPaint.setColor(mSelectedWeekBackgroundColor);
            mTempRect.top = mWeekSeparatorLineWidth;
            mTempRect.bottom = mHeight;
            final boolean isLayoutRtl = isLayoutRtl();
            if (isLayoutRtl) {
                mTempRect.left = 0;
                mTempRect.right = mSelectedLeft - 2;
            } else {
                mTempRect.left = mShowWeekNumber ? mWidth / mNumCells : 0;
                mTempRect.right = mSelectedLeft - 2;
            }
            canvas.drawRect(mTempRect, mDrawPaint);
            if (isLayoutRtl) {
                mTempRect.left = mSelectedRight + 3;
                mTempRect.right = mShowWeekNumber ? mWidth - mWidth / mNumCells : mWidth;
            } else {
                mTempRect.left = mSelectedRight + 3;
                mTempRect.right = mWidth;
            }
            canvas.drawRect(mTempRect, mDrawPaint);
        }

        private void drawWeekNumbersAndDates(Canvas canvas) {
            // AOSP reads the paint size before assigning the date size, which is the default 12px
            // and sits the digits high in the row. The date size is what was measured for.
            final float textHeight = mDateTextSize;
            final int y = (int) ((mHeight + textHeight) / 2) - mWeekSeparatorLineWidth;
            final int nDays = mNumCells;
            final int divisor = 2 * nDays;
            mDrawPaint.setTextAlign(Paint.Align.CENTER);
            mDrawPaint.setTextSize(mDateTextSize);
            mMonthNumDrawPaint.setTextSize(mDateTextSize);
            int i = 0;
            if (isLayoutRtl()) {
                for (; i < nDays - 1; i++) {
                    mMonthNumDrawPaint.setColor(mFocusDay[i] ? mFocusedMonthDateColor : mUnfocusedMonthDateColor);
                    int x = (2 * i + 1) * mWidth / divisor;
                    canvas.drawText(mDayNumbers[nDays - 1 - i], x, y, mMonthNumDrawPaint);
                }
                if (mShowWeekNumber) {
                    mDrawPaint.setColor(mWeekNumberColor);
                    canvas.drawText(mDayNumbers[0], mWidth - mWidth / divisor, y, mDrawPaint);
                }
            } else {
                if (mShowWeekNumber) {
                    mDrawPaint.setColor(mWeekNumberColor);
                    canvas.drawText(mDayNumbers[0], mWidth / divisor, y, mDrawPaint);
                    i++;
                }
                for (; i < nDays; i++) {
                    mMonthNumDrawPaint.setColor(mFocusDay[i] ? mFocusedMonthDateColor : mUnfocusedMonthDateColor);
                    int x = (2 * i + 1) * mWidth / divisor;
                    canvas.drawText(mDayNumbers[i], x, y, mMonthNumDrawPaint);
                }
            }
        }

        private void drawWeekSeparators(Canvas canvas) {
            View first = mListView.getChildAt(0);
            if (first == null) return;
            int firstFullyVisiblePosition = mListView.getFirstVisiblePosition();
            if (first.getTop() < 0) firstFullyVisiblePosition++;
            if (firstFullyVisiblePosition == mWeek) return;
            mDrawPaint.setColor(mWeekSeparatorLineColor);
            mDrawPaint.setStrokeWidth(mWeekSeparatorLineWidth);
            float startX;
            float stopX;
            if (isLayoutRtl()) {
                startX = 0;
                stopX = mShowWeekNumber ? mWidth - mWidth / mNumCells : mWidth;
            } else {
                startX = mShowWeekNumber ? mWidth / mNumCells : 0;
                stopX = mWidth;
            }
            canvas.drawLine(startX, 0, stopX, 0, mDrawPaint);
        }

        private void drawSelectedDateVerticalBars(Canvas canvas) {
            if (!mHasSelectedDay || mSelectedDateVerticalBar == null) return;
            mSelectedDateVerticalBar.setBounds(mSelectedLeft - mSelectedDateVerticalBarWidth / 2,
                    mWeekSeparatorLineWidth, mSelectedLeft + mSelectedDateVerticalBarWidth / 2, mHeight);
            mSelectedDateVerticalBar.draw(canvas);
            mSelectedDateVerticalBar.setBounds(mSelectedRight - mSelectedDateVerticalBarWidth / 2,
                    mWeekSeparatorLineWidth, mSelectedRight + mSelectedDateVerticalBarWidth / 2, mHeight);
            mSelectedDateVerticalBar.draw(canvas);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            mWidth = w;
            updateSelectionPositions();
        }

        private void updateSelectionPositions() {
            if (!mHasSelectedDay || mNumCells <= 0) return;
            final boolean isLayoutRtl = isLayoutRtl();
            int selectedPosition = mSelectedDay - mFirstDayOfWeek;
            if (selectedPosition < 0) selectedPosition += 7;
            if (mShowWeekNumber && !isLayoutRtl) selectedPosition++;
            if (isLayoutRtl) mSelectedLeft = (mDaysPerWeek - 1 - selectedPosition) * mWidth / mNumCells;
            else mSelectedLeft = selectedPosition * mWidth / mNumCells;
            mSelectedRight = mSelectedLeft + mWidth / mNumCells;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int weeks = mShownWeekCount > 0 ? mShownWeekCount : 1;
            mHeight = (mListView.getHeight() - mListView.getPaddingTop() - mListView.getPaddingBottom()) / weeks;
            if (mHeight < 1) mHeight = 1;
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mHeight);
        }
    }
}
