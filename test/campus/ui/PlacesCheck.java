package campus.ui;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import campus.map.MapData;
import campus.map.RouteService;
import campus.model.PlannerModel;
import campus.model.PlannerStore;
import campus.service.Place;
import campus.service.PlaceCatalog;

/** Actual OSM place and pin regression checks; never loads personal state. */
public final class PlacesCheck {
    private static int checks;

    private PlacesCheck() { }

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args.length == 0 ? "." : args[0]);
        MapData data = MapData.load(project.resolve("resources/map/chonnam-campus.osm"));
        RouteService routes = new RouteService(data);
        PlannerModel model = new PlannerModel();
        PlaceCatalog places = new PlaceCatalog(routes, model);
        int namedCount = places.namedPlaces().size();
        Place unnamed = places.all().stream().filter(place -> !place.named()).findFirst().orElseThrow();
        check(places.all().size() == data.buildings().size(), "all buildings remain selectable");
        check(namedCount < places.all().size(), "unnamed places are omitted from named list");
        places.rename(unnamed, "개인 건물 이름");
        check(places.find(unnamed.id()).named(), "renamed building becomes named");
        check(places.namedPlaces().size() == namedCount + 1, "renamed building appears once");
        places.rename(places.find(unnamed.id()), "이름 없는 나의 실험실");
        check(places.find(unnamed.id()).named(), "user name prefix remains valid");

        Place building = places.find("w1125544293");
        Place otherBuilding = places.find("w1125468849");
        check(building != null && otherBuilding != null, "actual campus buildings loaded");
        RouteService.Route buildingRoute = places.route(building, otherBuilding);
        check(buildingRoute.reachable(), "existing building routing preserved");
        check(Math.abs(length(buildingRoute.points()) - buildingRoute.meters()) < 0.05,
                "building route length matches geometry");

        MapData.GeoPoint[] road = actualRoadSegment(routes);
        Place pin = places.addPin(road[0], "집합 장소");
        Place otherPin = places.addPin(road[1], "과제 모임");
        check(pin.pin() && pin.named() && pin.id().startsWith("pin:"), "named pin created");
        check(places.namedPlaces().size() == namedCount + 3, "pins appear in named list");
        RouteService.Route sameEdge = places.route(pin, otherPin);
        check(sameEdge.reachable(), "pin to pin route exists");
        check(Math.abs(sameEdge.meters() - MapData.distance(road[0], road[1])) < 0.05,
                "same-edge pins avoid a detour through a junction");
        check(Math.abs(length(sameEdge.points()) - sameEdge.meters()) < 0.05,
                "pin route length matches geometry");
        check(sameEdge.points().get(0).equals(road[0])
                && sameEdge.points().get(sameEdge.points().size() - 1).equals(road[1]),
                "pin route endpoints match selected coordinates");
        RouteService.Route toBuilding = places.route(pin, building);
        check(toBuilding.reachable() && places.route(building, pin).reachable(),
                "pin and building routes work in both directions");
        check(Math.abs(length(toBuilding.points()) - toBuilding.meters()) < 0.05,
                "pin to building geometry length");
        check(places.route(pin, pin).meters() == 0, "same pin has zero distance");
        Place distant = places.addPin(new MapData.GeoPoint(35.4, 126.9), "먼 장소");
        check(!places.route(distant, building).reachable(), "distant pin has no fabricated route");

        model.putEvent(event("pin-event", pin.id(), 9));
        model.putEvent(event("building-event", building.id(), 11));
        Path temporary = Files.createTempDirectory("campus-places-check-");
        Path state = temporary.resolve("planner.xml");
        try {
            PlannerStore.save(state, model);
            PlannerModel loaded = PlannerStore.load(state);
            PlaceCatalog restored = new PlaceCatalog(routes, loaded);
            check(restored.find(pin.id()) != null, "pin persistence round-trip");
            check(restored.find(unnamed.id()).name().equals("이름 없는 나의 실험실"),
                    "building name persistence round-trip");
            check(loaded.events().get(0).buildingId().equals(pin.id()), "pin schedule persistence");
        } finally {
            Files.deleteIfExists(state);
            Files.deleteIfExists(temporary);
        }

        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                checkMapInput(data, model, places, building, pin, toBuilding);
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) throw new RuntimeException(failure.get());

        PlannerModel incoming = new PlannerModel();
        incoming.putPin(new PlannerModel.Pin(pin.id(), "가져온 다른 핀", road[1].lat(), road[1].lon()));
        incoming.putEvent(event("imported-event", pin.id(), 15));
        model.mergeFrom(incoming);
        Place imported = places.namedPlaces().stream().filter(place -> place.name().equals("가져온 다른 핀"))
                .findFirst().orElseThrow();
        check(!imported.id().equals(pin.id()), "conflicting imported pin ID remapped");
        check(places.find(pin.id()).point().equals(road[0]), "original pin position preserved after import");
        check(model.events().stream().filter(event -> event.id().equals("imported-event"))
                .findFirst().orElseThrow().buildingId().equals(imported.id()), "imported event uses remapped pin");
        check(places.route(imported, otherPin).meters() == 0, "imported pin route uses new coordinates");
        System.out.println("PASS " + checks + " actual OSM place, pin, route and input checks");
    }

    private static void checkMapInput(MapData data, PlannerModel model, PlaceCatalog places,
                                     Place building, Place pin, RouteService.Route toBuilding) throws Exception {
        AtomicReference<Place> selected = new AtomicReference<>();
        AtomicReference<Place> activated = new AtomicReference<>();
        AtomicReference<MapData.GeoPoint> requested = new AtomicReference<>();
        AtomicInteger activations = new AtomicInteger();
        CampusMapPanel panel = new CampusMapPanel(data, model, places, selected::set, place -> {
            activated.set(place);
            activations.incrementAndGet();
        }, requested::set);
        panel.setSize(800, 600);
        panel.setOccurrences(model.occurrences(LocalDate.now()));
        check(panel.legs().size() == 1 && panel.legs().get(0).route().reachable(), "pin schedule routing");
        Method project = CampusMapPanel.class.getDeclaredMethod("point", MapData.GeoPoint.class);
        project.setAccessible(true);
        Point2D projected = (Point2D) project.invoke(panel, building.point());
        Point location = new Point((int) Math.round(projected.getX()), (int) Math.round(projected.getY()));
        click(panel, location, 1);
        check(selected.get() != null && selected.get().id().equals(building.id()), "single click selects place");
        click(panel, location, 2);
        check(activated.get() != null && activated.get().id().equals(building.id()), "double click activates place");
        click(panel, location, 3);
        click(panel, location, 4);
        check(activations.get() == 1, "only exact double click opens an action");

        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, 400, 300, 1, true, MouseEvent.BUTTON3));
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                MouseEvent.BUTTON3_DOWN_MASK, 450, 350, 0, false, MouseEvent.NOBUTTON));
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                0, 450, 350, 1, true, MouseEvent.BUTTON3));
        check(requested.get() != null, "right click requests a pin");
        Point2D converted = (Point2D) project.invoke(panel, requested.get());
        check(converted.distance(450, 350) < 0.001, "screen-coordinate conversion round-trip");
        Point2D afterRightDrag = (Point2D) project.invoke(panel, building.point());
        check(afterRightDrag.distance(projected) < 0.001, "right drag does not pan the map");

        Place unnamed = places.all().stream().filter(place -> !place.named()).findFirst().orElseThrow();
        DefaultListModel<Place> items = new DefaultListModel<>();
        places.namedPlaces().forEach(items::addElement);
        JList<Place> list = new JList<>(items);
        int count = items.size();
        list.setSelectedValue(unnamed, true);
        check(items.size() == count && list.getSelectedValue() == null,
                "selecting unnamed map place does not insert it in left list");
        PlacePicker picker = new PlacePicker(places, model);
        picker.select(unnamed);
        picker.select(unnamed);
        Field choicesField = PlacePicker.class.getDeclaredField("choices");
        choicesField.setAccessible(true);
        JComboBox<?> choices = (JComboBox<?>) choicesField.get(picker);
        int copies = 0;
        for (int index = 0; index < choices.getItemCount(); index++) {
            if (choices.getItemAt(index) instanceof Place place && place.id().equals(unnamed.id())) copies++;
        }
        check(copies == 1, "reselecting unnamed place does not duplicate picker entry");
        check(picker.selected().id().equals(unnamed.id()), "unnamed selected place remains usable in editor");

        panel.setSearchRoute(pin, building, toBuilding);
        check(panel.isSearchRouteActive(), "independent pin route search active");
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try { panel.paint(graphics); }
        finally { graphics.dispose(); }
        panel.clearSearchRoute();
        check(!panel.isSearchRouteActive() && panel.legs().size() == 1, "restore daily route after search");
    }

    private static void click(CampusMapPanel panel, Point point, int count) {
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                0, point.x, point.y, count, false, MouseEvent.BUTTON1));
    }

    private static PlannerModel.Event event(String id, String place, int hour) {
        return new PlannerModel.Event(id, id, place, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0),
                PlannerModel.Category.APPOINTMENT, null, LocalDate.now(), "");
    }

    @SuppressWarnings("unchecked")
    private static MapData.GeoPoint[] actualRoadSegment(RouteService routes) throws Exception {
        Field walkField = RouteService.class.getDeclaredField("walk");
        walkField.setAccessible(true);
        Object walk = walkField.get(routes);
        Field geometryField = walk.getClass().getDeclaredField("geometry");
        geometryField.setAccessible(true);
        Map<Long, List<MapData.GeoPoint>> geometry =
                (Map<Long, List<MapData.GeoPoint>>) geometryField.get(walk);
        for (List<MapData.GeoPoint> line : geometry.values()) {
            for (int index = 1; index < line.size(); index++) {
                MapData.GeoPoint start = line.get(index - 1), end = line.get(index);
                if (MapData.distance(start, end) < 25) continue;
                MapData.GeoPoint from = interpolate(start, end, 0.35);
                MapData.GeoPoint to = interpolate(start, end, 0.65);
                if (routes.route(from, to).reachable()) return new MapData.GeoPoint[]{from, to};
            }
        }
        throw new AssertionError("Actual OSM road segment was not found");
    }

    private static MapData.GeoPoint interpolate(MapData.GeoPoint from, MapData.GeoPoint to, double fraction) {
        return new MapData.GeoPoint(from.lat() + (to.lat() - from.lat()) * fraction,
                from.lon() + (to.lon() - from.lon()) * fraction);
    }

    private static double length(List<MapData.GeoPoint> points) {
        double result = 0;
        for (int index = 1; index < points.size(); index++) {
            result += MapData.distance(points.get(index - 1), points.get(index));
        }
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
