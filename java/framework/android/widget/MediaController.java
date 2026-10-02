package android.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.SeekBar.OnSeekBarChangeListener;
import com.android.internal.policy.PhoneWindow;
import com.android.internal.util.InternalRes;
import java.util.Formatter;
import java.util.Locale;

/**
 * Transport controls for a {@link MediaPlayerControl} (AOSP MediaController).
 * The floating window anchors to a view and hides itself after three seconds.
 * Previous and next stay hidden until {@link #setPrevNextListeners}.
 */
public class MediaController extends FrameLayout {
    private static final int sDefaultTimeout = 3000;

    private MediaPlayerControl mPlayer;
    private final Context mContext;
    private View mAnchor;
    private View mRoot;
    private WindowManager mWindowManager;
    private Window mWindow;
    private View mDecor;
    private WindowManager.LayoutParams mDecorLayoutParams;
    private ProgressBar mProgress;
    private TextView mEndTime;
    private TextView mCurrentTime;
    private boolean mShowing;
    private boolean mDragging;
    private final boolean mUseFastForward;
    private boolean mFromXml;
    private boolean mListenersSet;
    private OnClickListener mNextListener;
    private OnClickListener mPrevListener;
    private final StringBuilder mFormatBuilder = new StringBuilder();
    private final Formatter mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
    private ImageButton mPauseButton;
    private ImageButton mFfwdButton;
    private ImageButton mRewButton;
    private ImageButton mNextButton;
    private ImageButton mPrevButton;
    private CharSequence mPlayDescription = "";
    private CharSequence mPauseDescription = "";
    private final AccessibilityManager mAccessibilityManager;

    public MediaController(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRoot = this;
        mContext = context;
        mUseFastForward = true;
        mFromXml = true;
        mAccessibilityManager = AccessibilityManager.getInstance(context);
    }

    @Override
    public void onFinishInflate() {
        if (mRoot != null) initControllerView(mRoot);
    }

    public MediaController(Context context, boolean useFastForward) {
        super(context);
        mContext = context;
        mUseFastForward = useFastForward;
        initFloatingWindowLayout();
        initFloatingWindow();
        mAccessibilityManager = AccessibilityManager.getInstance(context);
    }

    public MediaController(Context context) { this(context, true); }

    private void initFloatingWindow() {
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mWindow = new PhoneWindow(mContext);
        mWindow.setWindowManager(mWindowManager, null, null);
        mWindow.requestFeature(Window.FEATURE_NO_TITLE);
        mDecor = mWindow.getDecorView();
        mDecor.setOnTouchListener(mTouchListener);
        mWindow.setContentView(this);
        mWindow.setBackgroundDrawableResource(android.R.color.transparent);
        mWindow.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        requestFocus();
    }

    private void initFloatingWindowLayout() {
        mDecorLayoutParams = new WindowManager.LayoutParams();
        WindowManager.LayoutParams p = mDecorLayoutParams;
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.height = LayoutParams.WRAP_CONTENT;
        p.x = 0;
        p.format = PixelFormat.TRANSLUCENT;
        p.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        p.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;
        p.token = null;
        p.windowAnimations = 0;
    }

    private void updateFloatingWindowLayout() {
        int[] anchorPos = new int[2];
        mAnchor.getLocationOnScreen(anchorPos);
        mDecor.measure(MeasureSpec.makeMeasureSpec(mAnchor.getWidth(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(mAnchor.getHeight(), MeasureSpec.AT_MOST));
        WindowManager.LayoutParams p = mDecorLayoutParams;
        p.width = mAnchor.getWidth();
        p.x = anchorPos[0] + (mAnchor.getWidth() - p.width) / 2;
        p.y = anchorPos[1] + mAnchor.getHeight() - mDecor.getMeasuredHeight();
    }

    private final OnLayoutChangeListener mLayoutChangeListener = new OnLayoutChangeListener() {
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop,
                int oldRight, int oldBottom) {
            updateFloatingWindowLayout();
            if (mShowing) mWindowManager.updateViewLayout(mDecor, mDecorLayoutParams);
        }
    };

    private final OnTouchListener mTouchListener = new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && mShowing) hide();
            return false;
        }
    };

    public void setMediaPlayer(MediaPlayerControl player) {
        mPlayer = player;
        updatePausePlay();
    }

    public void setAnchorView(View view) {
        if (mAnchor != null) mAnchor.removeOnLayoutChangeListener(mLayoutChangeListener);
        mAnchor = view;
        if (mAnchor != null) mAnchor.addOnLayoutChangeListener(mLayoutChangeListener);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        removeAllViews();
        View v = makeControllerView();
        addView(v, frameParams);
    }

    /** framework-internal (hidden in AOSP). */
    protected View makeControllerView() {
        LayoutInflater inflate = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mRoot = inflate.inflate(InternalRes.layout("media_controller"), null);
        initControllerView(mRoot);
        return mRoot;
    }

    private void initControllerView(View v) {
        Resources res = mContext.getResources();
        int play = InternalRes.id("string", "lockscreen_transport_play_description");
        int pause = InternalRes.id("string", "lockscreen_transport_pause_description");
        if (play != 0) mPlayDescription = res.getText(play);
        if (pause != 0) mPauseDescription = res.getText(pause);
        mPauseButton = v.findViewById(InternalRes.viewId("pause"));
        if (mPauseButton != null) {
            mPauseButton.requestFocus();
            mPauseButton.setOnClickListener(mPauseListener);
        }
        mFfwdButton = v.findViewById(InternalRes.viewId("ffwd"));
        if (mFfwdButton != null) {
            mFfwdButton.setOnClickListener(mFfwdListener);
            if (!mFromXml) mFfwdButton.setVisibility(mUseFastForward ? View.VISIBLE : View.GONE);
        }
        mRewButton = v.findViewById(InternalRes.viewId("rew"));
        if (mRewButton != null) {
            mRewButton.setOnClickListener(mRewListener);
            if (!mFromXml) mRewButton.setVisibility(mUseFastForward ? View.VISIBLE : View.GONE);
        }
        mNextButton = v.findViewById(InternalRes.viewId("next"));
        if (mNextButton != null && !mFromXml && !mListenersSet) mNextButton.setVisibility(View.GONE);
        mPrevButton = v.findViewById(InternalRes.viewId("prev"));
        if (mPrevButton != null && !mFromXml && !mListenersSet) mPrevButton.setVisibility(View.GONE);
        mProgress = v.findViewById(InternalRes.viewId("mediacontroller_progress"));
        if (mProgress != null) {
            if (mProgress instanceof SeekBar) ((SeekBar) mProgress).setOnSeekBarChangeListener(mSeekListener);
            mProgress.setMax(1000);
        }
        mEndTime = v.findViewById(InternalRes.viewId("time"));
        mCurrentTime = v.findViewById(InternalRes.viewId("time_current"));
        installPrevNextListeners();
    }

    public void show() { show(sDefaultTimeout); }

    private void disableUnsupportedButtons() {
        try {
            if (mPauseButton != null && !mPlayer.canPause()) mPauseButton.setEnabled(false);
            if (mRewButton != null && !mPlayer.canSeekBackward()) mRewButton.setEnabled(false);
            if (mFfwdButton != null && !mPlayer.canSeekForward()) mFfwdButton.setEnabled(false);
            if (mProgress != null && !mPlayer.canSeekBackward() && !mPlayer.canSeekForward()) {
                mProgress.setEnabled(false);
            }
        } catch (IncompatibleClassChangeError ex) {
            // An old MediaPlayerControl without canPause. Leave the buttons enabled.
        }
    }

    public void show(int timeout) {
        if (!mShowing && mAnchor != null) {
            setProgress();
            if (mPauseButton != null) mPauseButton.requestFocus();
            disableUnsupportedButtons();
            updateFloatingWindowLayout();
            mWindowManager.addView(mDecor, mDecorLayoutParams);
            mShowing = true;
        }
        updatePausePlay();
        post(mShowProgress);
        if (timeout != 0 && !mAccessibilityManager.isTouchExplorationEnabled()) {
            removeCallbacks(mFadeOut);
            postDelayed(mFadeOut, timeout);
        }
    }

    public boolean isShowing() { return mShowing; }

    public void hide() {
        if (mAnchor == null || !mShowing) return;
        try {
            removeCallbacks(mShowProgress);
            mWindowManager.removeView(mDecor);
        } catch (IllegalArgumentException ex) {
            Log.w("MediaController", "already removed");
        }
        mShowing = false;
    }

    private final Runnable mFadeOut = new Runnable() {
        public void run() { hide(); }
    };

    private final Runnable mShowProgress = new Runnable() {
        public void run() {
            int pos = setProgress();
            if (!mDragging && mShowing && mPlayer != null && mPlayer.isPlaying()) {
                postDelayed(mShowProgress, 1000 - (pos % 1000));
            }
        }
    };

    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        mFormatBuilder.setLength(0);
        if (hours > 0) return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        return mFormatter.format("%02d:%02d", minutes, seconds).toString();
    }

    private int setProgress() {
        if (mPlayer == null || mDragging) return 0;
        int position = mPlayer.getCurrentPosition();
        int duration = mPlayer.getDuration();
        if (mProgress != null) {
            if (duration > 0) mProgress.setProgress((int) (1000L * position / duration));
            mProgress.setSecondaryProgress(mPlayer.getBufferPercentage() * 10);
        }
        if (mEndTime != null) mEndTime.setText(stringForTime(duration));
        if (mCurrentTime != null) mCurrentTime.setText(stringForTime(position));
        return position;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                show(0);
                break;
            case MotionEvent.ACTION_UP:
                show(sDefaultTimeout);
                break;
            case MotionEvent.ACTION_CANCEL:
                hide();
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        show(sDefaultTimeout);
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        final boolean uniqueDown = event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_DOWN;
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_SPACE) {
            if (uniqueDown) {
                doPauseResume();
                show(sDefaultTimeout);
                if (mPauseButton != null) mPauseButton.requestFocus();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            if (uniqueDown && !mPlayer.isPlaying()) {
                mPlayer.start();
                updatePausePlay();
                show(sDefaultTimeout);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (uniqueDown && mPlayer.isPlaying()) {
                mPlayer.pause();
                updatePausePlay();
                show(sDefaultTimeout);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE || keyCode == KeyEvent.KEYCODE_CAMERA) {
            return super.dispatchKeyEvent(event);
        } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (uniqueDown) hide();
            return true;
        }
        show(sDefaultTimeout);
        return super.dispatchKeyEvent(event);
    }

    private final OnClickListener mPauseListener = new OnClickListener() {
        public void onClick(View v) {
            doPauseResume();
            show(sDefaultTimeout);
        }
    };

    private void updatePausePlay() {
        if (mRoot == null || mPauseButton == null || mPlayer == null) return;
        if (mPlayer.isPlaying()) {
            mPauseButton.setImageResource(android.R.drawable.ic_media_pause);
            mPauseButton.setContentDescription(mPauseDescription);
        } else {
            mPauseButton.setImageResource(android.R.drawable.ic_media_play);
            mPauseButton.setContentDescription(mPlayDescription);
        }
    }

    private void doPauseResume() {
        if (mPlayer == null) return;
        if (mPlayer.isPlaying()) mPlayer.pause();
        else mPlayer.start();
        updatePausePlay();
    }

    private final OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
            show(3600000);
            mDragging = true;
            removeCallbacks(mShowProgress);
        }

        public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            if (!fromUser || mPlayer == null) return;
            long duration = mPlayer.getDuration();
            long newPosition = (duration * progress) / 1000L;
            mPlayer.seekTo((int) newPosition);
            if (mCurrentTime != null) mCurrentTime.setText(stringForTime((int) newPosition));
        }

        public void onStopTrackingTouch(SeekBar bar) {
            mDragging = false;
            setProgress();
            updatePausePlay();
            show(sDefaultTimeout);
            post(mShowProgress);
        }
    };

    @Override
    public void setEnabled(boolean enabled) {
        if (mPauseButton != null) mPauseButton.setEnabled(enabled);
        if (mFfwdButton != null) mFfwdButton.setEnabled(enabled);
        if (mRewButton != null) mRewButton.setEnabled(enabled);
        if (mNextButton != null) mNextButton.setEnabled(enabled && mNextListener != null);
        if (mPrevButton != null) mPrevButton.setEnabled(enabled && mPrevListener != null);
        if (mProgress != null) mProgress.setEnabled(enabled);
        disableUnsupportedButtons();
        super.setEnabled(enabled);
    }

    @Override
    public CharSequence getAccessibilityClassName() { return MediaController.class.getName(); }

    private final OnClickListener mRewListener = new OnClickListener() {
        public void onClick(View v) {
            int pos = mPlayer.getCurrentPosition();
            mPlayer.seekTo(pos - 5000);
            setProgress();
            show(sDefaultTimeout);
        }
    };

    private final OnClickListener mFfwdListener = new OnClickListener() {
        public void onClick(View v) {
            int pos = mPlayer.getCurrentPosition();
            mPlayer.seekTo(pos + 15000);
            setProgress();
            show(sDefaultTimeout);
        }
    };

    private void installPrevNextListeners() {
        if (mNextButton != null) {
            mNextButton.setOnClickListener(mNextListener);
            mNextButton.setEnabled(mNextListener != null);
        }
        if (mPrevButton != null) {
            mPrevButton.setOnClickListener(mPrevListener);
            mPrevButton.setEnabled(mPrevListener != null);
        }
    }

    public void setPrevNextListeners(OnClickListener next, OnClickListener prev) {
        mNextListener = next;
        mPrevListener = prev;
        mListenersSet = true;
        if (mRoot != null) {
            installPrevNextListeners();
            if (mNextButton != null && !mFromXml) mNextButton.setVisibility(View.VISIBLE);
            if (mPrevButton != null && !mFromXml) mPrevButton.setVisibility(View.VISIBLE);
        }
    }

    public interface MediaPlayerControl {
        void start();
        void pause();
        int getDuration();
        int getCurrentPosition();
        void seekTo(int pos);
        boolean isPlaying();
        int getBufferPercentage();
        boolean canPause();
        boolean canSeekBackward();
        boolean canSeekForward();
        int getAudioSessionId();
    }
}
