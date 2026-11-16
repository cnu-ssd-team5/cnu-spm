package campus.service;

import java.util.Objects;
import campus.map.MapData;

/** A selectable OSM building or a personal map pin. */
public record Place(String id, String name, MapData.GeoPoint point, boolean pin) {
    public Place {
        Objects.requireNonNull(id, "장소 ID");
        Objects.requireNonNull(name, "장소 이름");
        Objects.requireNonNull(point, "장소 좌표");
    }

    public boolean named() {
        return !name.isBlank();
    }

    @Override public String toString() {
        return named() ? name : "이름 없는 건물";
    }
}
