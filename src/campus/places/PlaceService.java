package campus.places;

import campus.map.CampusMap;
import campus.map.CampusMap.*;
import campus.map.RouteService;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Event;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** 사용자 장소는 모델에 저장하고 기존 지도·일정·자료함의 장소 ID를 공유한다. */
public final class PlaceService {
    private static final Pattern SPACES = Pattern.compile("[\\p{javaWhitespace}\\p{Zs}]+");
    private final CampusMap map;
    private final PlannerModel model;
    private final RouteService routes;
    public PlaceService(CampusMap map, PlannerModel model, RouteService routes) {
        this.map=map; this.model=model; this.routes=routes;
    }
    public boolean sync() throws IOException {
        boolean linked = applyPlaces(map, model);
        routes.refreshPlaces();
        return linked;
    }

    public static boolean applyPlaces(CampusMap map, PlannerModel model) {
        Set<String> ids=new HashSet<>(); model.pins().forEach(p->ids.add(p.id()));
        new ArrayList<>(map.buildings()).stream().filter(b->b.id().startsWith("pin:")&&!ids.contains(b.id()))
                .forEach(b->map.removePlace(b.id()));
        for(var pin:model.pins()) {
            GeoPoint point=new GeoPoint(pin.lat(),pin.lon());
            map.putPlace(new Building(pin.id(),pin.name(),List.of(point),point));
        }
        for(var entry:model.buildingNames().entrySet()) {
            Building old=map.building(entry.getKey());
            if(old!=null)map.putPlace(new Building(old.id(),entry.getValue(),old.outline(),old.center()));
        }
        boolean linked = false;
        synchronized (model) {
            for (Event event : model.events()) {
                if (event.customPlace().isBlank()) continue;
                List<Building> matches = matchingPlaces(map, event.customPlace());
                if (matches.size() != 1) continue;
                model.putEvent(new Event(event.id(), event.title(), matches.get(0).id(), event.start(), event.end(),
                        event.category(), event.weekday(), event.date(), event.note(), event.customCategory(), ""));
                linked = true;
            }
        }
        return linked;
    }

    public static String nameKey(String name) {
        return SPACES.matcher(Objects.requireNonNullElse(name, "")).replaceAll("").toLowerCase(Locale.ROOT);
    }

    public static boolean hasName(Building building) {
        if (building == null) return false;
        String name = nameKey(building.name());
        return !name.isEmpty() && !name.equals("이름없는건물") && !name.startsWith("이름없는건물·");
    }

    /** 부분 이름으로 위치를 추측하지 않는다. 표시 이름 또는 원래 지도 이름이 정확히 같아야 한다. */
    public static List<Building> matchingPlaces(CampusMap map, String text) {
        String key = nameKey(text);
        if (key.isEmpty()) return List.of();
        return map.buildings().stream().filter(PlaceService::hasName)
                .filter(place -> nameKey(place.name()).equals(key) || nameKey(map.originalName(place.id())).equals(key))
                .toList();
    }
    public String addPin(String name, GeoPoint point) throws IOException {
        String id="pin:"+UUID.randomUUID();
        model.putPin(new PlannerModel.Pin(id,name,point.lat(),point.lon())); sync(); return id;
    }
    public void rename(String id,String name) throws IOException { model.renameBuilding(id,name); sync(); }
    public void removePin(String id) throws IOException { model.removePin(id); sync(); }
}
