package campus.map;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** OSM 태그를 읽는 규칙을 모읍니다. 그릴 도로와 실제 걸을 수 있는 도로는 구분합니다. */
final class OsmTags {
    private static final List<String> NAME_KEYS = List.of("name:ko", "name", "official_name", "name:en", "ref");
    private static final Set<String> FOOT_ALLOWED = Set.of("yes", "designated", "permissive", "official");
    private static final Set<String> WALKING_ROADS = Set.of(
            "footway", "pedestrian", "path", "steps", "service", "residential", "living_street",
            "track", "unclassified", "tertiary", "tertiary_link", "secondary", "secondary_link",
            "primary", "primary_link");

    private OsmTags() { }

    static String name(Map<String, String> tags) {
        for (String key : NAME_KEYS) {
            String value = tags.getOrDefault(key, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    static boolean isUniversity(Map<String, String> tags) {
        return "university".equals(tags.get("amenity"));
    }

    static boolean isBuilding(Map<String, String> tags) {
        return tags.containsKey("building") && !"no".equals(tags.get("building"));
    }

    static boolean isBusPlatform(Map<String, String> tags) {
        return "bus_stop".equals(tags.get("highway"))
                || "platform".equals(tags.get("public_transport")) && "yes".equals(tags.get("bus"));
    }

    static boolean isBusStopPosition(Map<String, String> tags) {
        return "stop_position".equals(tags.get("public_transport")) && "yes".equals(tags.get("bus"));
    }

    static String displayKind(Map<String, String> tags) {
        if ("water".equals(tags.get("natural")) || "riverbank".equals(tags.get("waterway")) || tags.containsKey("water")) {
            return "water";
        }
        if (Set.of("pitch", "track").contains(tags.getOrDefault("leisure", ""))) {
            return "pitch";
        }
        if (Set.of("park", "garden").contains(tags.getOrDefault("leisure", ""))
                || Set.of("grass", "forest", "meadow", "recreation_ground").contains(tags.getOrDefault("landuse", ""))
                || Set.of("wood", "scrub", "grassland").contains(tags.getOrDefault("natural", ""))) {
            return "green";
        }
        String highway = tags.getOrDefault("highway", "");
        if (highway.equals("steps")) {
            return "steps";
        }
        if (Set.of("footway", "pedestrian", "path", "cycleway").contains(highway)) {
            return "footway";
        }
        if (!highway.isEmpty() && !Set.of("construction", "proposed", "corridor").contains(highway)) {
            return "road";
        }
        return null;
    }

    static boolean blocksWalking(Map<String, String> tags) {
        String foot = tags.getOrDefault("foot", "");
        if (FOOT_ALLOWED.contains(foot)) {
            return false;
        }
        return Set.of("no", "private", "use_sidepath").contains(foot)
                || Set.of("no", "private").contains(tags.getOrDefault("access", ""))
                || Set.of("wall", "fence", "retaining_wall").contains(tags.getOrDefault("barrier", ""));
    }

    static boolean isWalkableRoad(Map<String, String> tags) {
        if (blocksWalking(tags) || "yes".equals(tags.get("indoor")) || tags.containsKey("construction")) {
            return false;
        }
        String highway = tags.getOrDefault("highway", "");
        if ("cycleway".equals(highway)) {
            return Set.of("yes", "designated", "permissive").contains(tags.getOrDefault("foot", ""));
        }
        return WALKING_ROADS.contains(highway);
    }

    static boolean allowsForwardWalking(Map<String, String> tags) {
        return !"-1".equals(tags.getOrDefault("oneway:foot", "no"))
                && !"no".equals(tags.get("foot:forward"));
    }

    static boolean allowsBackwardWalking(Map<String, String> tags) {
        return !Set.of("yes", "1", "true").contains(tags.getOrDefault("oneway:foot", "no"))
                && !"no".equals(tags.get("foot:backward"));
    }
}
