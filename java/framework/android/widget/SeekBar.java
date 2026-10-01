package android.widget;

import android.content.Context;
import android.util.AttributeSet;

public class SeekBar extends AbsSeekBar {
    public interface OnSeekBarChangeListener {
        void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser);

        void onStartTrackingTouch(SeekBar seekBar);

        void onStopTrackingTouch(SeekBar seekBar);
    }

    private OnSeekBarChangeListener mOnSeekBarChangeListener;

    public SeekBar(Context context) { this(context, null); }

    public SeekBar(Context context, AttributeSet attrs) { this(context, attrs, android.R.attr.seekBarStyle); }

    public SeekBar(Context context, AttributeSet attrs, int defStyleAttr) { this(context, attrs, defStyleAttr, 0); }

    public SeekBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    void onProgressRefresh(float scale, boolean fromUser, int progress) {
        super.onProgressRefresh(scale, fromUser, progress);
        if (mOnSeekBarChangeListener != null) mOnSeekBarChangeListener.onProgressChanged(this, progress, fromUser);
    }

    public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) { mOnSeekBarChangeListener = l; }

    @Override
    void onStartTrackingTouch() {
        super.onStartTrackingTouch();
        if (mOnSeekBarChangeListener != null) mOnSeekBarChangeListener.onStartTrackingTouch(this);
    }

    @Override
    void onStopTrackingTouch() {
        super.onStopTrackingTouch();
        if (mOnSeekBarChangeListener != null) mOnSeekBarChangeListener.onStopTrackingTouch(this);
    }

    @Override
    public CharSequence getAccessibilityClassName() { return SeekBar.class.getName(); }
}
