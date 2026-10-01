package android.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import com.android.internal.util.InternalRes;
import java.util.ArrayList;

/**
 * Port of AOSP Toast on the framework transient_notification layout. Toasts
 * of the process queue and show one at a time for 2 s (short) or 3.5 s (long)
 * in a TYPE_TOAST window, as NotificationManagerService schedules them.
 */
public class Toast {
    static final String TAG = "Toast";
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;
    private static final long SHORT_DURATION_TIMEOUT = 2000;
    private static final long LONG_DURATION_TIMEOUT = 3500;

    private static final ArrayList<Toast> sQueue = new ArrayList<Toast>();
    private static Toast sShowing;
    private static Handler sHandler;

    final Context mContext;
    private final Handler mHandler;
    int mDuration;
    View mNextView;
    private View mView;
    private int mGravity;
    private int mX;
    private int mY;
    private float mHorizontalMargin;
    private float mVerticalMargin;
    private final ArrayList<Callback> mCallbacks = new ArrayList<Callback>();
    private final Runnable mHide = new Runnable() {
        public void run() { hideCurrent(); }
    };

    public abstract static class Callback {
        public void onToastShown() {}

        public void onToastHidden() {}
    }

    public Toast(Context context) { this(context, null); }

    /** framework-internal (hidden in AOSP). */
    public Toast(Context context, Looper looper) {
        mContext = context;
        if (looper == null) {
            looper = Looper.myLooper();
            if (looper == null) {
                throw new RuntimeException("Can't toast on a thread that has not called Looper.prepare()");
            }
        }
        mHandler = new Handler(looper);
        final Resources res = context.getResources();
        int yOffset = InternalRes.dimen("toast_y_offset");
        mY = yOffset != 0 ? res.getDimensionPixelSize(yOffset) : (int) (24 * res.getDisplayMetrics().density);
        int gravityRes = InternalRes.id("integer", "config_toastDefaultGravity");
        mGravity = gravityRes != 0 ? res.getInteger(gravityRes) : Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
    }

    public void show() {
        if (mNextView == null) throw new RuntimeException("setView must have been called");
        final View text = mNextView.findViewById(android.R.id.message);
        if (text instanceof TextView) Log.i(TAG, "show: " + ((TextView) text).getText());
        synchronized (sQueue) {
            if (sHandler == null) sHandler = new Handler(Looper.getMainLooper());
            if (!sQueue.contains(this) && sShowing != this) sQueue.add(this);
        }
        sHandler.post(new Runnable() {
            public void run() { showNext(); }
        });
    }

    public void cancel() {
        synchronized (sQueue) { sQueue.remove(this); }
        if (sHandler != null) {
            sHandler.post(new Runnable() {
                public void run() {
                    if (sShowing == Toast.this) {
                        sHandler.removeCallbacks(mHide);
                        hideCurrent();
                    }
                }
            });
        }
    }

    private static void showNext() {
        Toast next;
        synchronized (sQueue) {
            if (sShowing != null || sQueue.isEmpty()) return;
            next = sQueue.remove(0);
            sShowing = next;
        }
        next.handleShow();
        sHandler.postDelayed(next.mHide, next.mDuration == LENGTH_LONG ? LONG_DURATION_TIMEOUT : SHORT_DURATION_TIMEOUT);
    }

    private void hideCurrent() {
        handleHide();
        synchronized (sQueue) {
            if (sShowing == this) sShowing = null;
        }
        showNext();
    }

    private void handleShow() {
        if (mView != mNextView) {
            handleHide();
            mView = mNextView;
            final WindowManager wm = (WindowManager) mView.getContext().getSystemService(Context.WINDOW_SERVICE);
            final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.format = PixelFormat.TRANSLUCENT;
            params.windowAnimations = InternalRes.style("Animation.Toast");
            params.type = WindowManager.LayoutParams.TYPE_TOAST;
            params.setTitle("Toast");
            params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            final int gravity = Gravity.getAbsoluteGravity(mGravity,
                    mView.getContext().getResources().getConfiguration().getLayoutDirection());
            params.gravity = gravity;
            if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.FILL_HORIZONTAL) params.horizontalWeight = 1.0f;
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.FILL_VERTICAL) params.verticalWeight = 1.0f;
            params.x = mX;
            params.y = mY;
            params.verticalMargin = mVerticalMargin;
            params.horizontalMargin = mHorizontalMargin;
            params.packageName = mContext.getPackageName();
            if (mView.getParent() != null) wm.removeView(mView);
            wm.addView(mView, params);
            for (Callback cb : new ArrayList<Callback>(mCallbacks)) cb.onToastShown();
        }
    }

    private void handleHide() {
        if (mView != null) {
            if (mView.getParent() != null || android.view.WindowManagerGlobal.getInstance().isAdded(mView)) {
                final WindowManager wm = (WindowManager) mView.getContext().getSystemService(Context.WINDOW_SERVICE);
                try {
                    wm.removeViewImmediate(mView);
                } catch (IllegalArgumentException ignored) {
                    // Already gone with its activity.
                }
            }
            mView = null;
            for (Callback cb : new ArrayList<Callback>(mCallbacks)) cb.onToastHidden();
        }
    }

    public void setView(View view) { mNextView = view; }

    public View getView() { return mNextView; }

    public void setDuration(int duration) { mDuration = duration; }

    public int getDuration() { return mDuration; }

    public void setMargin(float horizontalMargin, float verticalMargin) {
        mHorizontalMargin = horizontalMargin;
        mVerticalMargin = verticalMargin;
    }

    public float getHorizontalMargin() { return mHorizontalMargin; }

    public float getVerticalMargin() { return mVerticalMargin; }

    public void setGravity(int gravity, int xOffset, int yOffset) {
        mGravity = gravity;
        mX = xOffset;
        mY = yOffset;
    }

    public int getGravity() { return mGravity; }

    public int getXOffset() { return mX; }

    public int getYOffset() { return mY; }

    public void addCallback(Callback callback) {
        if (callback == null) throw new NullPointerException("callback");
        mCallbacks.add(callback);
    }

    public void removeCallback(Callback callback) { mCallbacks.remove(callback); }

    public static Toast makeText(Context context, CharSequence text, int duration) {
        Toast result = new Toast(context, null);
        LayoutInflater inflate = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflate.inflate(InternalRes.layout("transient_notification"), null);
        TextView tv = (TextView) v.findViewById(android.R.id.message);
        tv.setText(text);
        result.mNextView = v;
        result.mDuration = duration;
        return result;
    }

    public static Toast makeText(Context context, int resId, int duration) throws Resources.NotFoundException {
        return makeText(context, context.getResources().getText(resId), duration);
    }

    public void setText(int resId) { setText(mContext.getText(resId)); }

    public void setText(CharSequence s) {
        if (mNextView == null) throw new RuntimeException("This Toast was not created with Toast.makeText()");
        TextView tv = (TextView) mNextView.findViewById(android.R.id.message);
        if (tv == null) throw new RuntimeException("This Toast was not created with Toast.makeText()");
        tv.setText(s);
    }
}
