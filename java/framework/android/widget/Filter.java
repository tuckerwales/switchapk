package android.widget;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * Constrains an adapter's data (AOSP Filter). As on Android, {@link #performFiltering} runs on a
 * worker thread ("Filter") and {@link #publishResults} on the thread that created the filter; a new
 * request cancels a pending one. The worker quits after three idle seconds.
 */
public abstract class Filter {
    private static final String LOG_TAG = "Filter";
    private static final String THREAD_NAME = "Filter";
    private static final int FILTER_TOKEN = 0xD0D0F00D;
    private static final int FINISH_TOKEN = 0xDEADBEEF;

    private Handler mThreadHandler;
    private final Handler mResultHandler;
    private final Object mLock = new Object();

    public Filter() {
        Looper looper = Looper.myLooper();
        if (looper == null) looper = Looper.getMainLooper();
        mResultHandler = new ResultsHandler(looper);
    }

    public final void filter(CharSequence constraint) { filter(constraint, null); }

    public final void filter(CharSequence constraint, FilterListener listener) {
        synchronized (mLock) {
            if (mThreadHandler == null) {
                HandlerThread thread = new HandlerThread(THREAD_NAME, android.os.Process.THREAD_PRIORITY_BACKGROUND);
                thread.start();
                mThreadHandler = new RequestHandler(thread.getLooper());
            }
            Message message = mThreadHandler.obtainMessage(FILTER_TOKEN);
            RequestArguments args = new RequestArguments();
            // Copy the constraint: the caller may change a mutable CharSequence while the worker runs.
            args.constraint = constraint != null ? constraint.toString() : null;
            args.listener = listener;
            message.obj = args;
            mThreadHandler.removeMessages(FILTER_TOKEN);
            mThreadHandler.removeMessages(FINISH_TOKEN);
            mThreadHandler.sendMessage(message);
        }
    }

    protected abstract FilterResults performFiltering(CharSequence constraint);

    protected abstract void publishResults(CharSequence constraint, FilterResults results);

    public CharSequence convertResultToString(Object resultValue) {
        return resultValue == null ? "" : resultValue.toString();
    }

    /** Values published by {@link #performFiltering}. */
    public static class FilterResults {
        public int count;
        public Object values;

        public FilterResults() {}
    }

    /** Told how many values survived a filter pass. */
    public interface FilterListener {
        void onFilterComplete(int count);
    }

    private class RequestHandler extends Handler {
        RequestHandler(Looper looper) { super(looper); }

        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            if (what == FILTER_TOKEN) {
                RequestArguments args = (RequestArguments) msg.obj;
                try {
                    args.results = performFiltering(args.constraint);
                } catch (Exception e) {
                    args.results = new FilterResults();
                    Log.w(LOG_TAG, "An exception occured during performFiltering()!", e);
                } finally {
                    Message message = mResultHandler.obtainMessage(what);
                    message.obj = args;
                    message.sendToTarget();
                }
                synchronized (mLock) {
                    if (mThreadHandler != null) {
                        mThreadHandler.sendMessageDelayed(mThreadHandler.obtainMessage(FINISH_TOKEN), 3000);
                    }
                }
            } else if (what == FINISH_TOKEN) {
                synchronized (mLock) {
                    if (mThreadHandler != null) {
                        mThreadHandler.getLooper().quit();
                        mThreadHandler = null;
                    }
                }
            }
        }
    }

    private class ResultsHandler extends Handler {
        ResultsHandler(Looper looper) { super(looper); }

        @Override
        public void handleMessage(Message msg) {
            RequestArguments args = (RequestArguments) msg.obj;
            publishResults(args.constraint, args.results);
            if (args.listener != null) {
                int count = args.results != null ? args.results.count : -1;
                args.listener.onFilterComplete(count);
            }
        }
    }

    private static class RequestArguments {
        CharSequence constraint;
        FilterListener listener;
        FilterResults results;
    }
}
