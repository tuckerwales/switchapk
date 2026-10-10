package android.hardware.display;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.WindowManagerImpl;
import java.util.ArrayList;

/**
 * One display, the console screen (built in or the TV when docked). Docking and undocking change it,
 * and listeners hear onDisplayChanged. No virtual displays.
 */
public final class DisplayManager {
    public static final String DISPLAY_CATEGORY_PRESENTATION = "android.hardware.display.category.PRESENTATION";
    public static final int MATCH_CONTENT_FRAMERATE_UNKNOWN = -1;
    public static final int MATCH_CONTENT_FRAMERATE_NEVER = 0;
    public static final int MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY = 1;
    public static final int MATCH_CONTENT_FRAMERATE_ALWAYS = 2;
    public static final int VIRTUAL_DISPLAY_FLAG_PUBLIC = 1;
    public static final int VIRTUAL_DISPLAY_FLAG_PRESENTATION = 2;
    public static final int VIRTUAL_DISPLAY_FLAG_SECURE = 4;
    public static final int VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY = 8;
    public static final int VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR = 16;

    public interface DisplayListener {
        void onDisplayAdded(int displayId);

        void onDisplayRemoved(int displayId);

        void onDisplayChanged(int displayId);
    }

    private static final class Registration {
        final DisplayListener listener;
        final Handler handler;

        Registration(DisplayListener listener, Handler handler) {
            this.listener = listener;
            this.handler = handler;
        }
    }

    private static final ArrayList<Registration> sListeners = new ArrayList<Registration>();

    private final Context mContext;

    DisplayManager() {
        this(null);
    }

    /** @hide */
    public DisplayManager(Context context) {
        mContext = context;
    }

    public Display getDisplay(int displayId) {
        return displayId == Display.DEFAULT_DISPLAY ? WindowManagerImpl.getDefault().getDefaultDisplay() : null;
    }

    public Display[] getDisplays() {
        return new Display[] {getDisplay(Display.DEFAULT_DISPLAY)};
    }

    public Display[] getDisplays(String category) {
        return category == null ? getDisplays() : new Display[0];
    }

    public void registerDisplayListener(DisplayListener listener, Handler handler) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        Looper looper = handler != null ? handler.getLooper() : Looper.myLooper();
        if (looper == null) {
            looper = Looper.getMainLooper();
        }
        synchronized (sListeners) {
            for (Registration r : sListeners) {
                if (r.listener == listener) {
                    return;
                }
            }
            sListeners.add(new Registration(listener, new Handler(looper)));
        }
    }

    public void unregisterDisplayListener(DisplayListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        synchronized (sListeners) {
            for (int i = sListeners.size() - 1; i >= 0; i--) {
                if (sListeners.get(i).listener == listener) {
                    sListeners.remove(i);
                }
            }
        }
    }

    public int getMatchContentFrameRateUserPreference() {
        return MATCH_CONTENT_FRAMERATE_NEVER;
    }

    /** @hide ActivityThread calls this when the screen changes size or density (dock, undock). */
    public static void notifyDisplayChanged() {
        ArrayList<Registration> copy;
        synchronized (sListeners) {
            copy = new ArrayList<Registration>(sListeners);
        }
        for (final Registration r : copy) {
            r.handler.post(new Runnable() {
                public void run() {
                    r.listener.onDisplayChanged(Display.DEFAULT_DISPLAY);
                }
            });
        }
    }
}
