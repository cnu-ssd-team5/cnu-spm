package campus.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import campus.map.RouteService.Route;
import campus.map.RouteService;
import campus.model.PlannerModel.Occurrence;

/** Walking legs between consecutive schedules of one day. */
public final class DayPlanService {
    public record Leg(Occurrence from, Occurrence to, Route route) {
        public int minutes() {
            return (int) Math.ceil(route.meters() / (RouteService.WALKING_METERS_PER_SECOND * 60));
        }
        public long gapMinutes() {
            return Duration.between(from.event().end(), to.event().start()).toMinutes();
        }
    }

    private final PlaceCatalog places;
    private final Map<String, Route> routeCache = new HashMap<>();

    public DayPlanService(PlaceCatalog places) {
        this.places = places;
    }

    public List<Leg> legs(List<Occurrence> occurrences) {
        List<Occurrence> active = occurrences.stream().filter(o -> !o.canceled()).toList();
        List<Leg> updated = new ArrayList<>();
        for (int i = 1; i < active.size(); i++) {
            Occurrence before = active.get(i - 1), after = active.get(i);
            Place from = places.find(before.event().buildingId());
            Place to = places.find(after.event().buildingId());
            if (from == null || to == null) {
                updated.add(new Leg(before, after, new Route(List.of(), 0, false, "OSM에서 장소를 찾을 수 없습니다")));
            } else {
                String key = from.id() + from.point() + ">" + to.id() + to.point();
                Route route = routeCache.computeIfAbsent(key, ignored -> places.route(from, to));
                updated.add(new Leg(before, after, route));
            }
        }
        return List.copyOf(updated);
    }
}
