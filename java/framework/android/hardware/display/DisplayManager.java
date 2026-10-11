package android.hardware.display;

import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManagerImpl;
import java.util.ArrayList;

/**
 * The display service (DISPLAY_SERVICE). There is one display, DEFAULT_DISPLAY, the
 * screen (720p handheld, 1080p docked on the Switch); a docked/handheld switch reaches
 * the listeners as onDisplayChanged(DEFAULT_DISPLAY). Virtual displays cannot be
 * created: createVirtualDisplay returns null, as AOSP does when the display service
 * refuses one.
 */
public final class DisplayManager {
    public static final String DISPLAY_CATEGORY_PRESENTATION = "android.hardware.display.category.PRESENTATION";
    public static final int MATCH_CONTENT_FRAMERATE_UNKNOWN = -1;
    public static final int MATCH_CONTENT_FRAMERATE_NEVER = 0;
    public static final int MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY = 1;
    public static final int MATCH_CONTENT_FRAMERATE_ALWAYS = 2;
    public static final int VIRTUAL_DISPLAY_FLAG_PUBLIC = 1 << 0;
    public static final int VIRTUAL_DISPLAY_FLAG_PRESENTATION = 1 << 1;
    public static final int VIRTUAL_DISPLAY_FLAG_SECURE = 1 << 2;
    public static final int VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY = 1 << 3;
    public static final int VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR = 1 << 4;

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

    /** Listeners of every DisplayManager instance, as AOSP's process-wide DisplayManagerGlobal. */
    private static final ArrayList<Registration> sListeners = new ArrayList<Registration>();

    DisplayManager() {}

    /** framework-internal: the instance ContextImpl hands out. */
    public static DisplayManager create() { return new DisplayManager(); }

    public Display getDisplay(int displayId) {
        return displayId == Display.DEFAULT_DISPLAY ? WindowManagerImpl.getDefault().getDefaultDisplay() : null;
    }

    public Display[] getDisplays() {
        return new Display[] { WindowManagerImpl.getDefault().getDefaultDisplay() };
    }

    public Display[] getDisplays(String category) {
        // the built-in screen is not a presentation display
        if (DISPLAY_CATEGORY_PRESENTATION.equals(category)) return new Display[0];
        return getDisplays();
    }

    public void registerDisplayListener(DisplayListener listener, Handler handler) {
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        Looper looper = handler != null ? handler.getLooper() : Looper.myLooper();
        if (looper == null) looper = Looper.getMainLooper();
        synchronized (sListeners) {
            for (int i = 0; i < sListeners.size(); i++) {
                if (sListeners.get(i).listener == listener) return;
            }
            sListeners.add(new Registration(listener, new Handler(looper)));
        }
    }

    public void unregisterDisplayListener(DisplayListener listener) {
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        synchronized (sListeners) {
            for (int i = 0; i < sListeners.size(); i++) {
                if (sListeners.get(i).listener == listener) {
                    sListeners.remove(i);
                    return;
                }
            }
        }
    }

    /** framework-internal: ActivityThread calls this after the screen size changed. */
    public static void dispatchDisplayChanged(final int displayId) {
        ArrayList<Registration> copy;
        synchronized (sListeners) {
            copy = new ArrayList<Registration>(sListeners);
        }
        for (int i = 0; i < copy.size(); i++) {
            final Registration r = copy.get(i);
            r.handler.post(new Runnable() {
                public void run() {
                    boolean live;
                    synchronized (sListeners) {
                        live = sListeners.contains(r);
                    }
                    if (live) r.listener.onDisplayChanged(displayId);
                }
            });
        }
    }

    public VirtualDisplay createVirtualDisplay(String name, int width, int height, int densityDpi, Surface surface,
            int flags) {
        return createVirtualDisplay(name, width, height, densityDpi, surface, flags, null, null);
    }

    public VirtualDisplay createVirtualDisplay(String name, int width, int height, int densityDpi, Surface surface,
            int flags, VirtualDisplay.Callback callback, Handler handler) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name must be non-null and non-empty");
        if (width <= 0 || height <= 0 || densityDpi <= 0) {
            throw new IllegalArgumentException("width, height, and densityDpi must be greater than 0");
        }
        return null;
    }

    public VirtualDisplay createVirtualDisplay(VirtualDisplayConfig config) {
        return createVirtualDisplay(config, null, null);
    }

    public VirtualDisplay createVirtualDisplay(VirtualDisplayConfig config, Handler handler,
            VirtualDisplay.Callback callback) {
        if (config == null) throw new NullPointerException("config");
        return null;
    }

    public HdrConversionMode getHdrConversionMode() {
        return new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_UNSUPPORTED);
    }

    public int getMatchContentFrameRateUserPreference() { return MATCH_CONTENT_FRAMERATE_NEVER; }
}
