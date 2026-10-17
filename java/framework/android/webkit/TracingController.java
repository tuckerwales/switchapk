package android.webkit;

import java.io.OutputStream;
import java.util.concurrent.Executor;

/** No tracing backend: start/stop only track the state, and stop closes the output stream with no data. */
public abstract class TracingController {
    private static TracingController sInstance;

    public TracingController() {
    }

    public static synchronized TracingController getInstance() {
        if (sInstance == null) sInstance = new Impl();
        return sInstance;
    }

    public abstract void start(TracingConfig tracingConfig);

    public abstract boolean stop(OutputStream outputStream, Executor executor);

    public abstract boolean isTracing();

    private static final class Impl extends TracingController {
        private boolean mTracing;

        @Override
        public synchronized void start(TracingConfig tracingConfig) {
            if (tracingConfig == null) throw new IllegalArgumentException("tracingConfig cannot be null");
            if (mTracing) throw new IllegalStateException("cannot start tracing: tracing is already enabled");
            mTracing = true;
        }

        @Override
        public boolean stop(final OutputStream outputStream, Executor executor) {
            synchronized (this) {
                if (!mTracing) return false;
                mTracing = false;
            }
            if (outputStream != null) {
                Runnable close = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            outputStream.close();
                        } catch (java.io.IOException ignored) {
                        }
                    }
                };
                if (executor != null) executor.execute(close);
                else close.run();
            }
            return true;
        }

        @Override
        public synchronized boolean isTracing() {
            return mTracing;
        }
    }
}
