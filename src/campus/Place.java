package campus;

import java.util.Objects;

/** A selectable OSM building, bus stop, or a personal map pin. */
record Place(String id, String name, MapData.GeoPoint point, boolean pin) {
    Place {
        Objects.requireNonNull(id, "장소 ID");
        Objects.requireNonNull(name, "장소 이름");
        Objects.requireNonNull(point, "장소 좌표");
    }

    boolean named() {
        return !name.isBlank();
    }

    boolean busStop() { return id.startsWith("bus:"); }

    @Override public String toString() {
        return named() ? name : "이름 없는 건물";
    }
}
