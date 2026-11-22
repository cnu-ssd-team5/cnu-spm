package campus.places;

import campus.map.CampusMap;
import campus.map.CampusMap.*;
import campus.map.RouteService;
import campus.model.PlannerModel;
import java.io.IOException;
import java.util.*;

/** 사용자 장소는 모델에 저장하고 기존 지도·일정·자료함의 장소 ID를 공유한다. */
public final class PlaceService {
    private final CampusMap map;
    private final PlannerModel model;
    private final RouteService routes;
    public PlaceService(CampusMap map, PlannerModel model, RouteService routes) {
        this.map=map; this.model=model; this.routes=routes;
    }
    public void sync() throws IOException {
        applyPlaces(map, model);
        routes.refreshPlaces();
    }

    public static void applyPlaces(CampusMap map, PlannerModel model) {
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
    }
    public String addPin(String name, GeoPoint point) throws IOException {
        String id="pin:"+UUID.randomUUID();
        model.putPin(new PlannerModel.Pin(id,name,point.lat(),point.lon())); sync(); return id;
    }
    public void rename(String id,String name) throws IOException { model.renameBuilding(id,name); sync(); }
    public void removePin(String id) throws IOException { model.removePin(id); sync(); }
}
