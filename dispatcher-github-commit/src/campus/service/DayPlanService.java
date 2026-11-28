package campus.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.map.RouteService;
import campus.map.RouteService.Route;
import campus.model.PlannerModel.Occurrence;

/** Walking legs between consecutive schedules of one day. */
public final class DayPlanService {
    public record Leg(Occurrence from, Occurrence to, Route route) {
        public int minutes() {
            return (int) Math.ceil(route.meters() / (RouteService.WALKING_METERS_PER_SECOND * 60));
        }
        public long gapMinutes() {
            // Keep a sub-minute overlap negative instead of truncating it to zero.
            return Math.floorDiv(Duration.between(from.event().end(), to.event().start()).getSeconds(),60);
        }
    }

    private final CampusMap map;
    private final RouteService routes;

    public DayPlanService(CampusMap map, RouteService routes) {
        this.map = map;
        this.routes = routes;
    }

    public List<Leg> legs(List<Occurrence> occurrences) {
        List<Occurrence> active = occurrences.stream().filter(o -> !o.canceled()).toList();
        List<Leg> legs = new ArrayList<>();
        for (int i = 1; i < active.size(); i++) {
            Occurrence before = active.get(i - 1), after = active.get(i);
            Building from = map.building(before.event().buildingId());
            Building to = map.building(after.event().buildingId());
            if (from == null || to == null) {
                legs.add(new Leg(before, after, new Route(List.of(), 0, false, "OSM에서 장소를 찾을 수 없습니다")));
            } else {
                legs.add(new Leg(before, after, routes.route(from, to)));
            }
        }
        return List.copyOf(legs);
    }
}
