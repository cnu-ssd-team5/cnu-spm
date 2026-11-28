package campus.places;

import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.map.CampusMap.GeoPoint;
import campus.map.RouteService;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Event;
import campus.model.PlannerModel.Pin;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** 사용자 핀과 이름을 지도에 반영하고, 일정의 장소를 연결합니다. */
public final class PlaceService {
    private static final Pattern SPACES = Pattern.compile("[\\p{javaWhitespace}\\p{Zs}]+");

    private final CampusMap map;
    private final PlannerModel model;
    private final RouteService routes;

    public PlaceService(CampusMap map, PlannerModel model, RouteService routes) {
        this.map = map;
        this.model = model;
        this.routes = routes;
    }

    public boolean sync() throws IOException {
        boolean linked = applyPlaces(map, model);
        routes.refreshPlaces();
        return linked;
    }

    public static boolean applyPlaces(CampusMap map, PlannerModel model) {
        applyPins(map, model.pins());
        applyBuildingNames(map, model);
        return linkNamedEvents(map, model);
    }

    private static void applyPins(CampusMap map, List<Pin> pins) {
        Set<String> savedIds = new HashSet<>();
        for (Pin pin : pins) {
            savedIds.add(pin.id());
        }

        // 지도에서 삭제할 때 목록이 변하므로 사본을 순회합니다.
        for (Building place : new ArrayList<>(map.buildings())) {
            if (place.id().startsWith("pin:") && !savedIds.contains(place.id())) {
                map.removePlace(place.id());
            }
        }
        for (Pin pin : pins) {
            GeoPoint point = new GeoPoint(pin.lat(), pin.lon());
            map.putPlace(new Building(pin.id(), pin.name(), List.of(point), point));
        }
    }

    private static void applyBuildingNames(CampusMap map, PlannerModel model) {
        for (var entry : model.buildingNames().entrySet()) {
            Building building = map.building(entry.getKey());
            if (building != null) {
                map.putPlace(new Building(building.id(), entry.getValue(), building.outline(), building.center()));
            }
        }
    }

    private static boolean linkNamedEvents(CampusMap map, PlannerModel model) {
        Map<String, List<Building>> placesByName = indexPlacesByName(map);
        boolean linked = false;
        synchronized (model) {
            for (Event event : model.events()) {
                if (event.customPlace().isBlank()) {
                    continue;
                }
                List<Building> matches = placesByName.getOrDefault(nameKey(event.customPlace()), List.of());
                // 같은 이름의 장소가 여럿이면 자동 연결하지 않습니다.
                if (matches.size() != 1) {
                    continue;
                }
                model.putEvent(linkToBuilding(event, matches.get(0)));
                linked = true;
            }
        }
        return linked;
    }

    private static Event linkToBuilding(Event event, Building building) {
        return new Event(event.id(), event.title(), building.id(), event.start(), event.end(),
                event.category(), event.weekday(), event.date(), event.note(), event.customCategory(), "");
    }

    private static Map<String, List<Building>> indexPlacesByName(CampusMap map) {
        Map<String, List<Building>> index = new LinkedHashMap<>();
        for (Building place : map.buildings()) {
            if (!hasName(place)) {
                continue;
            }
            for (String key : placeNameKeys(map, place)) {
                index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(place);
            }
        }
        return index;
    }

    private static List<String> placeNameKeys(CampusMap map, Building place) {
        String displayed = nameKey(place.name());
        String original = nameKey(map.originalName(place.id()));
        return displayed.equals(original) ? List.of(displayed) : List.of(displayed, original);
    }

    /** 이름을 비교할 때 공백과 대소문자 차이를 없앱니다. */
    public static String nameKey(String name) {
        return SPACES.matcher(Objects.requireNonNullElse(name, "")).replaceAll("").toLowerCase(Locale.ROOT);
    }

    public static boolean hasName(Building building) {
        if (building == null) {
            return false;
        }
        String name = nameKey(building.name());
        return !name.isEmpty() && !name.equals("이름없는건물") && !name.startsWith("이름없는건물·");
    }

    /** 표시 이름 또는 원래 OSM 이름과 정확히 일치하는 장소를 찾습니다. */
    public static List<Building> matchingPlaces(CampusMap map, String text) {
        String key = nameKey(text);
        if (key.isEmpty()) {
            return List.of();
        }
        List<Building> matches = new ArrayList<>();
        for (Building place : map.buildings()) {
            if (hasName(place) && placeNameKeys(map, place).contains(key)) {
                matches.add(place);
            }
        }
        return List.copyOf(matches);
    }

    public String addPin(String name, GeoPoint point) throws IOException {
        String id = "pin:" + UUID.randomUUID();
        model.putPin(new Pin(id, name, point.lat(), point.lon()));
        sync();
        return id;
    }

    public void rename(String id, String name) throws IOException {
        model.renameBuilding(id, name);
        sync();
    }

    public void removePin(String id) throws IOException {
        model.removePin(id);
        sync();
    }
}
