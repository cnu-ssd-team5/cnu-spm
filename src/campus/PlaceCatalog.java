package campus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Combines read-only campus geometry with personal names and pins. */
final class PlaceCatalog {
    private final MapData data;
    private final PlannerModel model;

    PlaceCatalog(MapData data, PlannerModel model) {
        this.data = data;
        this.model = model;
    }

    List<Place> all() {
        List<Place> result = new ArrayList<>();
        for (MapData.Building building : data.buildings()) result.add(buildingPlace(building));
        for (PlannerModel.Pin pin : model.pins()) result.add(pinPlace(pin));
        return List.copyOf(result);
    }

    List<Place> namedPlaces() {
        return all().stream().filter(Place::named).toList();
    }

    Place find(String id) {
        if (id == null) return null;
        MapData.Building building = data.building(id);
        if (building != null) return buildingPlace(building);
        return model.pins().stream().filter(pin -> pin.id().equals(id))
                .findFirst().map(this::pinPlace).orElse(null);
    }

    MapData.Route route(Place from, Place to) {
        if (from == null || to == null) {
            return new MapData.Route(List.of(), Double.NaN, false, "장소를 선택해 주세요");
        }
        MapData.Building fromBuilding = data.building(from.id());
        MapData.Building toBuilding = data.building(to.id());
        if (fromBuilding != null && toBuilding != null) return data.route(fromBuilding, toBuilding);
        if (fromBuilding != null) return data.route(fromBuilding, to.point());
        if (toBuilding != null) return data.route(from.point(), toBuilding);
        return data.route(from.point(), to.point());
    }

    void rename(Place place, String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("이름을 입력해 주세요");
        if (place.pin()) {
            model.putPin(new PlannerModel.Pin(place.id(), name.strip(), place.point().lat(), place.point().lon()));
        } else {
            model.renameBuilding(place.id(), name.strip());
        }
    }

    Place addPin(MapData.GeoPoint point, String name) {
        PlannerModel.Pin pin = new PlannerModel.Pin("pin:" + UUID.randomUUID(), name, point.lat(), point.lon());
        model.putPin(pin);
        return pinPlace(pin);
    }

    private Place buildingPlace(MapData.Building building) {
        String custom = model.customName(building.id());
        String name = custom.isBlank() ? building.name() : custom;
        if (custom.isBlank() && name.startsWith("이름 없는")) name = "";
        return new Place(building.id(), name, building.center(), false);
    }

    private Place pinPlace(PlannerModel.Pin pin) {
        return new Place(pin.id(), pin.name(), new MapData.GeoPoint(pin.lat(), pin.lon()), true);
    }
}
