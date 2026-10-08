package android.location;

import android.app.PendingIntent;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The console has no GPS or network location. This answers as an Android device with location turned off: the usual
 * providers exist but are disabled, there is no last known fix, and requests are accepted but only hear
 * onProviderDisabled (WS15).
 */
public class LocationManager {
    public static final String ACTION_GNSS_CAPABILITIES_CHANGED = "android.location.action.GNSS_CAPABILITIES_CHANGED";
    public static final String EXTRA_GNSS_CAPABILITIES = "android.location.extra.GNSS_CAPABILITIES";
    public static final String EXTRA_LOCATION_ENABLED = "android.location.extra.LOCATION_ENABLED";
    public static final String EXTRA_PROVIDER_ENABLED = "android.location.extra.PROVIDER_ENABLED";
    public static final String EXTRA_PROVIDER_NAME = "android.location.extra.PROVIDER_NAME";
    public static final String FUSED_PROVIDER = "fused";
    public static final String GPS_PROVIDER = "gps";
    public static final String KEY_FLUSH_COMPLETE = "flushComplete";
    public static final String KEY_LOCATIONS = "locations";
    public static final String KEY_LOCATION_CHANGED = "location";
    public static final String KEY_PROVIDER_ENABLED = "providerEnabled";
    public static final String KEY_PROXIMITY_ENTERING = "entering";
    public static final String KEY_STATUS_CHANGED = "status";
    public static final String MODE_CHANGED_ACTION = "android.location.MODE_CHANGED";
    public static final String NETWORK_PROVIDER = "network";
    public static final String PASSIVE_PROVIDER = "passive";
    public static final String PROVIDERS_CHANGED_ACTION = "android.location.PROVIDERS_CHANGED";

    private static final List<String> PROVIDERS =
            Arrays.asList(PASSIVE_PROVIDER, NETWORK_PROVIDER, FUSED_PROVIDER, GPS_PROVIDER);

    private final ArrayList<LocationListener> mListeners = new ArrayList<LocationListener>();

    /** framework-internal: the instance behind Context.LOCATION_SERVICE. */
    public LocationManager() {}

    public boolean isLocationEnabled() { return false; }

    public boolean isProviderEnabled(String provider) {
        if (provider == null) throw new IllegalArgumentException("invalid null provider");
        return false;
    }

    public Location getLastKnownLocation(String provider) {
        if (provider == null) throw new IllegalArgumentException("invalid null provider");
        return null;
    }

    /** Location is off, so the consumer gets null, as AOSP delivers for a disabled provider. */
    public void getCurrentLocation(String provider, CancellationSignal cancellationSignal, Executor executor,
            final Consumer<Location> consumer) {
        checkProvider(provider);
        if (executor == null) throw new IllegalArgumentException("invalid null executor");
        if (consumer == null) throw new IllegalArgumentException("invalid null callback");
        executor.execute(new Runnable() {
            public void run() {
                consumer.accept(null);
            }
        });
    }

    public void requestSingleUpdate(String provider, LocationListener listener, Looper looper) {
        requestLocationUpdates(provider, 0, 0, listener, looper);
    }

    public void requestSingleUpdate(Criteria criteria, LocationListener listener, Looper looper) {
        requestLocationUpdates(0, 0, criteria, listener, looper);
    }

    public void requestSingleUpdate(String provider, PendingIntent intent) {
        checkProvider(provider);
    }

    public void requestSingleUpdate(Criteria criteria, PendingIntent intent) {}

    public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, LocationListener listener) {
        requestLocationUpdates(provider, minTimeMs, minDistanceM, listener, null);
    }

    public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, LocationListener listener,
            Looper looper) {
        if (looper == null) {
            looper = Looper.myLooper();
            if (looper == null) throw new IllegalArgumentException("invalid null looper");
        }
        final Handler h = new Handler(looper);
        requestLocationUpdates(provider, minTimeMs, minDistanceM, new Executor() {
            public void execute(Runnable r) {
                h.post(r);
            }
        }, listener);
    }

    public void requestLocationUpdates(final String provider, long minTimeMs, float minDistanceM, Executor executor,
            final LocationListener listener) {
        checkProvider(provider);
        if (executor == null) throw new IllegalArgumentException("invalid null executor");
        if (listener == null) throw new IllegalArgumentException("invalid null listener");
        synchronized (mListeners) {
            if (!mListeners.contains(listener)) mListeners.add(listener);
        }
        executor.execute(new Runnable() {
            public void run() {
                boolean live;
                synchronized (mListeners) {
                    live = mListeners.contains(listener);
                }
                if (live) listener.onProviderDisabled(provider);
            }
        });
    }

    public void requestLocationUpdates(long minTimeMs, float minDistanceM, Criteria criteria, LocationListener listener,
            Looper looper) {
        requestLocationUpdates(bestOf(criteria), minTimeMs, minDistanceM, listener, looper);
    }

    public void requestLocationUpdates(long minTimeMs, float minDistanceM, Criteria criteria, Executor executor,
            LocationListener listener) {
        requestLocationUpdates(bestOf(criteria), minTimeMs, minDistanceM, executor, listener);
    }

    public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, PendingIntent intent) {
        checkProvider(provider);
    }

    public void requestLocationUpdates(long minTimeMs, float minDistanceM, Criteria criteria, PendingIntent intent) {}

    public void requestFlush(String provider, final LocationListener listener, final int requestCode) {
        checkProvider(provider);
        synchronized (mListeners) {
            if (!mListeners.contains(listener)) throw new IllegalArgumentException("unregistered listener cannot be flushed");
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                listener.onFlushComplete(requestCode);
            }
        });
    }

    public void requestFlush(String provider, PendingIntent pendingIntent, int requestCode) {
        checkProvider(provider);
    }

    public void removeUpdates(LocationListener listener) {
        if (listener == null) throw new IllegalArgumentException("invalid null listener");
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    public void removeUpdates(PendingIntent pendingIntent) {
        if (pendingIntent == null) throw new IllegalArgumentException("invalid null pending intent");
    }

    public boolean hasProvider(String provider) {
        if (provider == null) throw new IllegalArgumentException("invalid null provider");
        return PROVIDERS.contains(provider);
    }

    public List<String> getAllProviders() { return new ArrayList<String>(PROVIDERS); }

    public List<String> getProviders(boolean enabledOnly) {
        return enabledOnly ? new ArrayList<String>() : getAllProviders();
    }

    public List<String> getProviders(Criteria criteria, boolean enabledOnly) {
        if (criteria == null) throw new IllegalArgumentException("invalid null criteria");
        return getProviders(enabledOnly);
    }

    public String getBestProvider(Criteria criteria, boolean enabledOnly) {
        if (criteria == null) throw new IllegalArgumentException("invalid null criteria");
        return enabledOnly ? null : bestOf(criteria);
    }

    private static String bestOf(Criteria criteria) {
        if (criteria == null) throw new IllegalArgumentException("invalid null criteria");
        return criteria.getAccuracy() == Criteria.ACCURACY_FINE ? GPS_PROVIDER : FUSED_PROVIDER;
    }

    public boolean sendExtraCommand(String provider, String command, android.os.Bundle extras) {
        checkProvider(provider);
        return false;
    }

    public void addProximityAlert(double latitude, double longitude, float radius, long expiration,
            PendingIntent intent) {}

    public void removeProximityAlert(PendingIntent intent) {}

    public int getGnssYearOfHardware() { return 0; }

    public String getGnssHardwareModelName() { return null; }

    /** AOSP throws for a provider that does not exist; the four usual ones all exist here. */
    private static void checkProvider(String provider) {
        if (provider == null) throw new IllegalArgumentException("invalid null provider");
        if (!PROVIDERS.contains(provider)) {
            throw new IllegalArgumentException("provider \"" + provider + "\" does not exist");
        }
    }
}
