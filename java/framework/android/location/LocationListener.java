package android.location;

import android.os.Bundle;
import java.util.List;

public interface LocationListener {
    void onLocationChanged(Location location);

    default void onLocationChanged(List<Location> locations) {
        for (Location l : locations) onLocationChanged(l);
    }

    default void onFlushComplete(int requestCode) {}

    default void onStatusChanged(String provider, int status, Bundle extras) {}

    default void onProviderEnabled(String provider) {}

    default void onProviderDisabled(String provider) {}
}
