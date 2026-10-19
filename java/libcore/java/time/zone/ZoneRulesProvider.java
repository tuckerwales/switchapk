package java.time.zone;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Framework-internal (not in Android's public API): the JDK-shaped source of region rules behind ZoneId.of. The
 * default provider (TimeZoneRulesProvider) has no tz database and gives fixed-offset rules; a provider with real
 * transition data can be added with registerProvider and its ids then take precedence over the default ones.
 */
public abstract class ZoneRulesProvider {
    private static final CopyOnWriteArrayList<ZoneRulesProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final ConcurrentMap<String, ZoneRulesProvider> ZONES = new ConcurrentHashMap<>(512, 0.75f, 2);
    private static volatile Set<String> zoneIds;

    static {
        registerProvider(new TimeZoneRulesProvider());
    }

    protected ZoneRulesProvider() {
    }

    public static Set<String> getAvailableZoneIds() {
        Set<String> ids = zoneIds;
        if (ids == null) {
            ids = Collections.unmodifiableSet(new HashSet<String>(ZONES.keySet()));
            zoneIds = ids;
        }
        return ids;
    }

    public static ZoneRules getRules(String zoneId, boolean forCaching) {
        Objects.requireNonNull(zoneId, "zoneId");
        ZoneRulesProvider provider = ZONES.get(zoneId);
        if (provider == null) {
            for (ZoneRulesProvider p : PROVIDERS) {
                if (p.provideUnlistedZone(zoneId)) {
                    return p.provideRules(zoneId, forCaching);
                }
            }
            if (ZONES.isEmpty()) {
                throw new ZoneRulesException("No time-zone data files registered");
            }
            throw new ZoneRulesException("Unknown time-zone ID: " + zoneId);
        }
        return provider.provideRules(zoneId, forCaching);
    }

    public static void registerProvider(ZoneRulesProvider provider) {
        Objects.requireNonNull(provider, "provider");
        for (String zoneId : provider.provideZoneIds()) {
            Objects.requireNonNull(zoneId, "zoneId");
            ZONES.put(zoneId, provider);
        }
        PROVIDERS.add(0, provider);
        zoneIds = null;
    }

    protected abstract Set<String> provideZoneIds();

    protected abstract ZoneRules provideRules(String zoneId, boolean forCaching);

    /** Whether this provider can still supply an id it does not list (the default one asks java.util.TimeZone). */
    boolean provideUnlistedZone(String zoneId) {
        return false;
    }
}
