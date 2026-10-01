package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Port of AOSP TextClock: shows the current time with the 12- or 24-hour format (picked by
 * {@link #is24HourModeEnabled}, falling back to the other one and then to the defaults). There are
 * no system time broadcasts here, so the clock schedules its own tick on the next second (formats
 * with seconds) or minute boundary while it is attached and visible.
 */
public class TextClock extends TextView {
    public static final CharSequence DEFAULT_FORMAT_12_HOUR = "h:mm a";
    public static final CharSequence DEFAULT_FORMAT_24_HOUR = "H:mm";

    private CharSequence mFormat12;
    private CharSequence mFormat24;
    private CharSequence mFormat;
    private boolean mHasSeconds;
    private boolean mRegistered;
    private boolean mShouldRunTicker;
    private Calendar mTime;
    private String mTimeZone;

    private final Runnable mTicker = new Runnable() {
        public void run() {
            removeCallbacks(this);
            onTimeChanged();
            final long now = System.currentTimeMillis();
            final long unit = mHasSeconds ? 1000L : 60000L;
            long millisUntilNextTick = unit - now % unit;
            if (millisUntilNextTick <= 0) millisUntilNextTick = 1;
            postDelayed(this, millisUntilNextTick);
        }
    };

    public TextClock(Context context) {
        super(context);
        init();
    }

    public TextClock(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public TextClock(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public TextClock(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TypedArray a = context.obtainStyledAttributes(attrs, new int[] {
                android.R.attr.format12Hour, android.R.attr.format24Hour, android.R.attr.timeZone},
                defStyleAttr, defStyleRes);
        try {
            mFormat12 = a.getText(0);
            mFormat24 = a.getText(1);
            mTimeZone = a.getString(2);
        } finally {
            a.recycle();
        }
        init();
    }

    private void init() {
        if (mFormat12 == null) mFormat12 = DEFAULT_FORMAT_12_HOUR;
        if (mFormat24 == null) mFormat24 = DEFAULT_FORMAT_24_HOUR;
        createTime(mTimeZone);
        chooseFormat();
    }

    private void createTime(String timeZone) {
        if (timeZone != null) mTime = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        else mTime = Calendar.getInstance();
    }

    public CharSequence getFormat12Hour() { return mFormat12; }

    public void setFormat12Hour(CharSequence format) {
        mFormat12 = format;
        chooseFormat();
        onTimeChanged();
    }

    public CharSequence getFormat24Hour() { return mFormat24; }

    public void setFormat24Hour(CharSequence format) {
        mFormat24 = format;
        chooseFormat();
        onTimeChanged();
    }

    public void refreshTime() {
        onTimeChanged();
        invalidate();
    }

    public boolean is24HourModeEnabled() { return DateFormat.is24HourFormat(getContext()); }

    public String getTimeZone() { return mTimeZone; }

    public void setTimeZone(String timeZone) {
        mTimeZone = timeZone;
        createTime(timeZone);
        onTimeChanged();
    }

    /** framework-internal (hidden in AOSP): the format in use. */
    public CharSequence getFormat() { return mFormat; }

    private void chooseFormat() {
        final boolean format24Requested = is24HourModeEnabled();
        if (format24Requested) mFormat = abc(mFormat24, mFormat12, DEFAULT_FORMAT_24_HOUR);
        else mFormat = abc(mFormat12, mFormat24, DEFAULT_FORMAT_12_HOUR);
        boolean hadSeconds = mHasSeconds;
        mHasSeconds = DateFormat.hasSeconds(mFormat);
        if (mShouldRunTicker && hadSeconds != mHasSeconds) mTicker.run();
    }

    private static CharSequence abc(CharSequence a, CharSequence b, CharSequence c) {
        return a == null ? (b == null ? c : b) : a;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mRegistered) {
            mRegistered = true;
            createTime(mTimeZone);
        }
        onTimeChanged();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (!mShouldRunTicker && isVisible) {
            mShouldRunTicker = true;
            mTicker.run();
        } else if (mShouldRunTicker && !isVisible) {
            mShouldRunTicker = false;
            removeCallbacks(mTicker);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mRegistered) mRegistered = false;
        mShouldRunTicker = false;
        removeCallbacks(mTicker);
    }

    private void onTimeChanged() {
        mTime.setTimeInMillis(System.currentTimeMillis());
        setText(DateFormat.format(mFormat, mTime));
    }
}
