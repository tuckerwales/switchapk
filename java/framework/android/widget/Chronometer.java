package android.widget;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import com.android.internal.util.InternalRes;
import java.util.Formatter;
import java.util.IllegalFormatException;
import java.util.Locale;

/**
 * Port of AOSP Chronometer: a TextView showing the time elapsed since (or, counting down, left until)
 * {@link #setBase} on the {@link SystemClock#elapsedRealtime} clock. It ticks on each second boundary
 * while started, attached and visible.
 */
public class Chronometer extends TextView {
    private static final String TAG = "Chronometer";

    /** Told on every tick. */
    public interface OnChronometerTickListener {
        void onChronometerTick(Chronometer chronometer);
    }

    private long mBase;
    private long mNow;
    private boolean mVisible;
    private boolean mStarted;
    private boolean mRunning;
    private boolean mLogged;
    private String mFormat;
    private Formatter mFormatter;
    private Locale mFormatterLocale;
    private Object[] mFormatterArgs = new Object[1];
    private StringBuilder mFormatBuilder;
    private OnChronometerTickListener mOnChronometerTickListener;
    private StringBuilder mRecycle = new StringBuilder(8);
    private boolean mCountDown;

    public Chronometer(Context context) { this(context, null, 0); }

    public Chronometer(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public Chronometer(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public Chronometer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TypedArray a = context.obtainStyledAttributes(attrs,
                new int[] {android.R.attr.format, android.R.attr.countDown}, defStyleAttr, defStyleRes);
        setFormat(a.getString(0));
        setCountDown(a.getBoolean(1, false));
        a.recycle();
        init();
    }

    private void init() {
        mBase = SystemClock.elapsedRealtime();
        updateText(mBase);
    }

    public void setCountDown(boolean countDown) {
        mCountDown = countDown;
        updateText(SystemClock.elapsedRealtime());
    }

    public boolean isCountDown() { return mCountDown; }

    public boolean isTheFinalCountDown() {
        try {
            getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/9jK-NcRmVcw"))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void setBase(long base) {
        mBase = base;
        dispatchChronometerTick();
        updateText(SystemClock.elapsedRealtime());
    }

    public long getBase() { return mBase; }

    public void setFormat(String format) {
        mFormat = format;
        if (format != null && mFormatBuilder == null) mFormatBuilder = new StringBuilder(format.length() * 2);
    }

    public String getFormat() { return mFormat; }

    public void setOnChronometerTickListener(OnChronometerTickListener listener) {
        mOnChronometerTickListener = listener;
    }

    public OnChronometerTickListener getOnChronometerTickListener() { return mOnChronometerTickListener; }

    public void start() {
        mStarted = true;
        updateRunning();
    }

    public void stop() {
        mStarted = false;
        updateRunning();
    }

    /** framework-internal (hidden in AOSP). */
    public void setStarted(boolean started) {
        mStarted = started;
        updateRunning();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisible = false;
        updateRunning();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mVisible = visibility == VISIBLE;
        updateRunning();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateRunning();
    }

    private synchronized void updateText(long now) {
        mNow = now;
        long seconds = mCountDown ? mBase - now : now - mBase;
        seconds /= 1000;
        boolean negative = false;
        if (seconds < 0) {
            seconds = -seconds;
            negative = true;
        }
        String text = DateUtils.formatElapsedTime(mRecycle, seconds);
        if (negative) {
            final int res = InternalRes.id("string", "negative_duration");
            text = res != 0 ? getResources().getString(res, text) : "−" + text;
        }
        if (mFormat != null) {
            Locale loc = Locale.getDefault();
            if (mFormatter == null || !loc.equals(mFormatterLocale)) {
                mFormatterLocale = loc;
                mFormatter = new Formatter(mFormatBuilder, loc);
            }
            mFormatBuilder.setLength(0);
            mFormatterArgs[0] = text;
            try {
                mFormatter.format(mFormat, mFormatterArgs);
                text = mFormatBuilder.toString();
            } catch (IllegalFormatException ex) {
                if (!mLogged) {
                    Log.w(TAG, "Illegal format string: " + mFormat);
                    mLogged = true;
                }
            }
        }
        setText(text);
    }

    private void updateRunning() {
        boolean running = mVisible && mStarted && isShown();
        if (running != mRunning) {
            if (running) {
                updateText(SystemClock.elapsedRealtime());
                dispatchChronometerTick();
                postTickOnNextSecond();
            } else {
                removeCallbacks(mTickRunnable);
            }
            mRunning = running;
        }
    }

    private final Runnable mTickRunnable = new Runnable() {
        public void run() {
            if (mRunning) {
                updateText(SystemClock.elapsedRealtime());
                dispatchChronometerTick();
                postTickOnNextSecond();
            }
        }
    };

    private void postTickOnNextSecond() {
        long nowMillis = SystemClock.elapsedRealtime();
        int millis = (int) ((nowMillis - mBase) % 1000);
        postDelayed(mTickRunnable, 1000 - millis);
    }

    void dispatchChronometerTick() {
        if (mOnChronometerTickListener != null) mOnChronometerTickListener.onChronometerTick(this);
    }

    @Override
    public CharSequence getContentDescription() {
        CharSequence description = super.getContentDescription();
        return description != null ? description : getText();
    }

    @Override
    public CharSequence getAccessibilityClassName() { return Chronometer.class.getName(); }
}
