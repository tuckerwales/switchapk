package java.time;

import java.time.zone.ZoneRules;
import java.time.zone.ZoneRulesException;
import java.time.zone.ZoneRulesProvider;
import java.util.Objects;

/** A region-based zone id ("Europe/Paris", "UTC", "GMT+02:00"); rules are looked up lazily if not known yet. */
final class ZoneRegion extends ZoneId implements java.io.Serializable {
    private static final long serialVersionUID = 8386373296231747096L;

    private final String id;
    private final transient ZoneRules rules;

    static ZoneRegion ofId(String zoneId, boolean checkAvailable) {
        Objects.requireNonNull(zoneId, "zoneId");
        checkName(zoneId);
        ZoneRules rules = null;
        try {
            rules = ZoneRulesProvider.getRules(zoneId, true);
        } catch (ZoneRulesException ex) {
            if (checkAvailable) {
                throw ex;
            }
        }
        return new ZoneRegion(zoneId, rules);
    }

    private static void checkName(String zoneId) {
        int n = zoneId.length();
        if (n < 2) {
            throw new DateTimeException("Invalid ID for region-based ZoneId, invalid format: " + zoneId);
        }
        for (int i = 0; i < n; i++) {
            char c = zoneId.charAt(i);
            if (c >= 'a' && c <= 'z') {
                continue;
            }
            if (c >= 'A' && c <= 'Z') {
                continue;
            }
            if (i != 0 && ((c >= '0' && c <= '9') || c == '/' || c == '~' || c == '.' || c == '_' || c == '+'
                    || c == '-')) {
                continue;
            }
            throw new DateTimeException("Invalid ID for region-based ZoneId, invalid format: " + zoneId);
        }
    }

    ZoneRegion(String id, ZoneRules rules) {
        this.id = id;
        this.rules = rules;
    }

    public String getId() {
        return id;
    }

    public ZoneRules getRules() {
        return (rules != null ? rules : ZoneRulesProvider.getRules(id, false));
    }
}
