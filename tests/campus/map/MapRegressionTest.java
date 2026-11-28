package campus.map;

import campus.map.CampusMap.Building;
import campus.map.CampusMap.GeoPoint;
import campus.map.RouteService.Route;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Category;
import campus.model.PlannerModel.Event;
import campus.model.PlannerStore;
import campus.places.PlaceService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/** 개인 data를 사용하지 않는 지도 회귀 검사. -ea 옵션으로 실행합니다. */
public final class MapRegressionTest {
    private static int checks;
    private static Path sandbox;

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        sandbox = Files.createTempDirectory(root.resolve("build/map-refactor"), "test-");
        checkRouteCacheKeys();
        checkRoutes();
        checkPlaces();
        checkSelection();
        checkInvalidMaps();
        String snapshots = realMapSnapshots(root.resolve("resources/map"));
        Files.writeString(Path.of(args[1]), snapshots, StandardCharsets.UTF_8);
        System.out.println("PASS " + checks + " checks; " + snapshots.lines().count() + " school snapshots");
    }

    private static void checkRouteCacheKeys() throws Exception {
        CampusMap map = loadFixture("cache-keys", "", "", "");
        PlannerModel model = new PlannerModel();
        model.putPin(new PlannerModel.Pin("pin:a", "A", 35.002, 126.004));
        model.putPin(new PlannerModel.Pin("pin:b:pin:c", "B", 35.002, 126.004));
        model.putPin(new PlannerModel.Pin("pin:a:pin:b", "C", 35.009, 126.009));
        model.putPin(new PlannerModel.Pin("pin:c", "D", 35.002, 126.004));
        PlaceService.applyPlaces(map, model);
        RouteService routes = new RouteService(map);
        check(routes.route(map.building("pin:a"), map.building("pin:b:pin:c")).reachable(), "first pin pair reachable");
        check(!routes.route(map.building("pin:a:pin:b"), map.building("pin:c")).reachable(),
                "distinct pin pairs must not share a route cache entry");
    }

    private static void checkRoutes() throws Exception {
        CampusMap map = loadFixture("routes", "", "", "");
        check(map.campusId().equals("w100"), "campus boundary");
        check(map.building("w200") != null && map.building("r300") != null, "way/relation buildings");
        check(map.building("w900") == null && map.building("w901") == null, "outside/incomplete buildings");
        check(map.buildings().stream().filter(b -> b.id().startsWith("bus:")).count() == 1, "bus deduplication");
        RouteService routes = new RouteService(map);
        Building a = map.building("w200"), b = map.building("w201");
        Route forward = routes.route(a, b);
        check(forward.reachable(), "forward route");
        check(forward.points().contains(new GeoPoint(35.0022, 126.003)), "compressed bend preserved");
        double expected = CampusMap.distance(new GeoPoint(35.002, 126.002), new GeoPoint(35.0022, 126.003))
                + CampusMap.distance(new GeoPoint(35.0022, 126.003), new GeoPoint(35.002, 126.004))
                + CampusMap.distance(new GeoPoint(35.002, 126.004), new GeoPoint(35.002, 126.006));
        check(Math.abs(expected - forward.meters()) < 1e-8, "route length");
        check(routes.route(a, b) == forward, "cached route reused");
        check(Math.abs(routes.route(b, a).meters() - forward.meters()) < 1e-8, "two-way route");
        check(routes.route(a, a).meters() == 0, "same building");
        check(!routes.route(null, b).reachable(), "missing endpoint");
        check(!routes.route(new Building("missing", "Missing", List.of(a.center()), a.center()), b).reachable(), "unknown building");
        routes.refreshPlaces();
        check(routes.route(a, b) != forward, "refresh invalidates cache");

        CampusMap oneWay = loadFixture("one-way", "<tag k='oneway:foot' v='yes'/>", "", "");
        RouteService directed = new RouteService(oneWay);
        check(directed.route(oneWay.building("w200"), oneWay.building("w201")).reachable(), "one-way forward");
        check(!directed.route(oneWay.building("w201"), oneWay.building("w200")).reachable(), "one-way reverse denied");

        CampusMap privateMap = loadFixture("private", "<tag k='access' v='private'/>", "", "");
        check(!new RouteService(privateMap).route(privateMap.building("w200"), privateMap.building("w201")).reachable(), "private road denied");
        CampusMap permitted = loadFixture("permitted", "<tag k='access' v='private'/><tag k='foot' v='yes'/>", "", "");
        check(new RouteService(permitted).route(permitted.building("w200"), permitted.building("w201")).reachable(), "foot permission overrides access");

        String extraRoad = "<way id='405'><nd ref='10'/><nd ref='23'/><nd ref='11'/><tag k='highway' v='footway'/></way>"
                + "<way id='406'><nd ref='12'/><nd ref='32'/><tag k='highway' v='footway'/></way>";
        String multipleDoors = fixture("", extraRoad, "<tag k='entrance' v='yes'/>")
                .replace("<node id='32' lat='35.0018' lon='126.006'/>",
                        "<node id='32' lat='35.0018' lon='126.006'><tag k='entrance' v='yes'/></node>");
        Path doorsFile = sandbox.resolve("multiple-doors.osm");
        Files.writeString(doorsFile, multipleDoors);
        CampusMap doors = CampusMap.load(doorsFile);
        RouteService multiRoutes = new RouteService(doors);
        Route multi = multiRoutes.route(doors.building("w200"), doors.building("w201"));
        check(Math.abs(multi.meters() - forward.meters()) < 1e-8, "best entrance chosen");
        check(Math.abs(multiRoutes.route(doors.building("w201"), doors.building("w200")).meters() - forward.meters()) < 1e-8,
                "multiple destination entrances");
        check(multi.points().equals(forward.points()), "multiple entrances preserve best geometry");

        Path negativeNodeFile = sandbox.resolve("negative-node.osm");
        Files.writeString(negativeNodeFile, fixture("", "", "").replace("id='12'", "id='-1'").replace("ref='12'", "ref='-1'"));
        CampusMap negativeNodeMap = CampusMap.load(negativeNodeFile);
        Route negativeNodeRoute = new RouteService(negativeNodeMap)
                .route(negativeNodeMap.building("w200"), negativeNodeMap.building("w201"));
        check(negativeNodeRoute.reachable() && Math.abs(negativeNodeRoute.meters() - forward.meters()) < 1e-8,
                "negative node ID must not act as end-of-road sentinel");

        String[] deniedTags = {"<tag k='foot' v='no'/>", "<tag k='indoor' v='yes'/>", "<tag k='construction' v='yes'/>"};
        for (int i = 0; i < deniedTags.length; i++) {
            CampusMap denied = loadFixture("denied-" + i, deniedTags[i], "", "");
            check(!new RouteService(denied).route(denied.building("w200"), denied.building("w201")).reachable(),
                    "walking restrictions " + i);
        }

        Path relationFile = sandbox.resolve("relation-campus.osm");
        String relationCampus = fixture("", "", "")
                .replace("<tag k='amenity' v='university'/><tag k='name' v='Test Campus'/>", "")
                .replace("</osm>", "<relation id='101'><member type='way' ref='100' role='outer'/>"
                        + "<tag k='amenity' v='university'/><tag k='name' v='Relation Campus'/></relation></osm>");
        Files.writeString(relationFile, relationCampus);
        check(CampusMap.load(relationFile).campusId().equals("r101"), "relation campus boundary");

        GeoPoint point = new GeoPoint(35.00205, 126.0041);
        Building pin = new Building("pin:test", "Near A", List.of(point), point);
        map.putPlace(pin);
        routes.refreshPlaces();
        Route estimated = routes.route(pin, a);
        check(estimated.reachable() && estimated.message().contains("추정"), "estimated pin connection");
        GeoPoint far = new GeoPoint(35.009, 126.009);
        Building isolated = new Building("pin:far", "Far", List.of(far), far);
        map.putPlace(isolated);
        routes.refreshPlaces();
        check(!routes.route(isolated, a).reachable(), "distant pin not connected");
        map.removePlace("w200");
        check(map.building("w200") != null, "OSM building cannot be deleted as pin");
    }

    private static void checkPlaces() throws Exception {
        CampusMap map = loadFixture("places", "", "", "");
        PlannerModel model = new PlannerModel();
        RouteService routes = new RouteService(map);
        PlaceService places = new PlaceService(map, model, routes);
        String id = places.addPin("Meeting point", new GeoPoint(35.00205, 126.0019));
        check(map.building(id) != null && model.pins().size() == 1, "pin added to map/model");
        places.rename("w200", "Renamed A");
        check(PlaceService.matchingPlaces(map, " renamed A ").size() == 1, "custom name match");
        check(PlaceService.matchingPlaces(map, " A ").size() == 1, "original name match");
        CampusMap copy = map.copyBasePlaces();
        check(copy.building(id) == null && copy.building("w200").name().equals("A"), "base copy resets user places");
        check(copy.originalName(id).isEmpty(), "base copy contains no private pin names");
        places.removePin(id);
        check(map.building(id) == null && model.pins().isEmpty(), "pin removed");
        check(map.originalName(id).isEmpty(), "deleted pin name discarded");
        check(map.originalName("missing").isEmpty(), "unknown place name is safe");
        model.putEvent(customEvent("event", " RENAMED A "));
        check(places.sync() && model.events().get(0).buildingId().equals("w200"), "named event linked");
        places.rename("w201", "Renamed A");
        model.putEvent(customEvent("ambiguous", "Renamed A"));
        places.sync();
        check(model.events().stream().filter(e -> e.id().equals("ambiguous")).findFirst().orElseThrow().buildingId().equals(Event.CUSTOM_PLACE_ID), "ambiguous name not linked");
        check(!PlaceService.hasName(new Building("x", "이름 없는 건물 · x", List.of(), new GeoPoint(0, 0))), "unnamed building hidden");
    }

    private static Event customEvent(String id, String place) {
        return new Event(id, "Class", Event.CUSTOM_PLACE_ID, LocalTime.of(9, 0), LocalTime.of(10, 0),
                Category.CLASS, null, LocalDate.of(2026, 11, 28), "note", "", place);
    }

    private static void checkSelection() throws Exception {
        Path defaultState = sandbox.resolve("folder with spaces 학교/data/planner-state.xml");
        Path source = sandbox.resolve("routes.osm");
        MapSelection.Loaded first = MapSelection.load(defaultState, source);
        check(first.statePath().equals(defaultState.resolveSibling("maps/w100/planner-state.xml")), "school state path");
        first.model().putPin(new PlannerModel.Pin("pin:saved", "Saved", 35.00205, 126.0019));
        PlannerStore.save(first.statePath(), first.model());
        MapSelection.remember(defaultState, first);
        Path selected = MapSelection.savedMap(defaultState);
        check(selected != null && Files.mismatch(source, selected) == -1, "selected OSM copied");
        check(MapSelection.startupMap(defaultState).equals(selected), "saved OSM reused");
        MapSelection.Loaded reloaded = MapSelection.load(defaultState, selected);
        check(reloaded.map().building("pin:saved") != null, "saved pin restored");
        MapSelection.remember(defaultState, reloaded);
        Path otherSource = sandbox.resolve("other-school.osm");
        Files.writeString(otherSource, fixture("", "", "").replace("way id='100'", "way id='101'"));
        MapSelection.Loaded other = MapSelection.load(defaultState, otherSource);
        check(!other.statePath().equals(first.statePath()) && other.model().pins().isEmpty(), "school data isolated");
        Path freshState = sandbox.resolve("first-run/data/planner-state.xml");
        Path initial = MapSelection.startupMap(freshState);
        check(CampusMap.load(initial).campusId().equals("w105125653"), "bundled first-run map");
        try (Stream<Path> files = Files.list(selected.getParent())) {
            check(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")), "no leftover map copy temp files");
        }
    }

    private static void checkInvalidMaps() throws Exception {
        expectFailure(() -> CampusMap.load(sandbox.resolve("missing.osm")), "missing map rejected");
        expectFailure(() -> MapSelection.load(sandbox.resolve("state.xml"), null), "null selection rejected");
        Path invalid = sandbox.resolve("invalid.osm");
        Files.writeString(invalid, "<osm/>");
        expectFailure(() -> CampusMap.load(invalid), "missing university boundary rejected");
        Files.writeString(invalid, "<!DOCTYPE osm [<!ENTITY x SYSTEM 'file:///not-read'>]><osm>&x;</osm>");
        expectFailure(() -> CampusMap.load(invalid), "external entity rejected");
        Files.writeString(invalid, "<osm><node id='1' lat='oops' lon='126'/></osm>");
        expectFailure(() -> CampusMap.load(invalid), "malformed coordinates rejected");
        for (String latitude : List.of("NaN", "Infinity", "91")) {
            Files.writeString(invalid, fixture("", "", "").replace("lat='35.0022'", "lat='" + latitude + "'"));
            expectFailure(() -> CampusMap.load(invalid), "invalid road latitude rejected: " + latitude);
        }
        Files.writeString(invalid, fixture("", "", "").replace("maxlat='35.01'", "maxlat='34'"));
        expectFailure(() -> CampusMap.load(invalid), "inverted extract bounds rejected");
        Files.writeString(invalid, fixture("", "", "").replace("v='footway'", "v='construction'"));
        expectFailure(() -> new RouteService(CampusMap.load(invalid)), "missing walking roads rejected");
    }

    private static String realMapSnapshots(Path directory) throws Exception {
        List<String> lines = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".osm")).sorted().toList()) {
                CampusMap map = CampusMap.load(file);
                RouteService routes = new RouteService(map);
                StringBuilder snapshot = new StringBuilder(map.campusId()).append(map.campusName());
                snapshot.append(map.minLat()).append(map.maxLat()).append(map.minLon()).append(map.maxLon());
                for (Building building : map.buildings()) {
                    snapshot.append(building.id()).append(building.name()).append(building.outline()).append(building.center());
                }
                snapshot.append(map.ways());
                List<Building> places = map.buildings();
                int[] indices = {0, places.size() / 4, places.size() / 2, places.size() * 3 / 4, places.size() - 1};
                for (int from : indices) {
                    for (int to : indices) {
                        snapshot.append(routes.route(places.get(from), places.get(to)));
                    }
                }
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(snapshot.toString().getBytes(StandardCharsets.UTF_8)));
                lines.add(file.getFileName() + " " + map.campusId() + " " + places.size() + " " + digest);
                checks += 25;
            }
        }
        return String.join("\n", lines) + "\n";
    }

    private static CampusMap loadFixture(String name, String roadTags, String extraRoad, String secondDoor) throws Exception {
        Path file = sandbox.resolve(name + ".osm");
        Files.writeString(file, fixture(roadTags, extraRoad, secondDoor), StandardCharsets.UTF_8);
        return CampusMap.load(file);
    }

    private static String fixture(String roadTags, String extraRoad, String secondDoor) {
        return """
                <osm version='0.6'><bounds minlat='35' minlon='126' maxlat='35.01' maxlon='126.01'/>
                <node id='1' lat='35' lon='126'/><node id='2' lat='35' lon='126.01'/>
                <node id='3' lat='35.01' lon='126.01'/><node id='4' lat='35.01' lon='126'/>
                <node id='10' lat='35.002' lon='126.002'><tag k='entrance' v='yes'/></node>
                <node id='11' lat='35.0022' lon='126.003'/><node id='12' lat='35.002' lon='126.004'/>
                <node id='13' lat='35.002' lon='126.006'><tag k='entrance' v='yes'/></node>
                <node id='14' lat='35.004' lon='126.004'><tag k='entrance' v='yes'/></node>
                <node id='15' lat='35.006' lon='126.004'/>
                <node id='20' lat='35.002' lon='126.0018'/><node id='21' lat='35.0018' lon='126.0018'/>
                <node id='23' lat='35.0018' lon='126.002'>%s</node>
                <node id='30' lat='35.002' lon='126.0062'/><node id='31' lat='35.0018' lon='126.0062'/><node id='32' lat='35.0018' lon='126.006'/>
                <node id='40' lat='35.004' lon='126.0042'/><node id='41' lat='35.0042' lon='126.0042'/><node id='42' lat='35.0042' lon='126.004'/>
                <node id='50' lat='35.008' lon='126.008'/><node id='51' lat='35.0081' lon='126.008'/><node id='52' lat='35.008' lon='126.0081'/>
                <node id='60' lat='36' lon='127'/><node id='61' lat='36.001' lon='127'/><node id='62' lat='36' lon='127.001'/>
                <node id='70' lat='35.002' lon='126.0041'><tag k='highway' v='bus_stop'/><tag k='name' v='Bus'/></node>
                <node id='71' lat='35.002' lon='126.0042'><tag k='public_transport' v='stop_position'/><tag k='bus' v='yes'/><tag k='name' v='Bus'/></node>
                <way id='100'><nd ref='1'/><nd ref='2'/><nd ref='3'/><nd ref='4'/><nd ref='1'/><tag k='amenity' v='university'/><tag k='name' v='Test Campus'/></way>
                <way id='200'><nd ref='10'/><nd ref='20'/><nd ref='21'/><nd ref='23'/><nd ref='10'/><tag k='building' v='yes'/><tag k='name' v='A'/></way>
                <way id='201'><nd ref='13'/><nd ref='30'/><nd ref='31'/><nd ref='32'/><nd ref='13'/><tag k='building' v='yes'/><tag k='name' v='B'/></way>
                <way id='301'><nd ref='14'/><nd ref='40'/><nd ref='41'/></way>
                <way id='302'><nd ref='14'/><nd ref='42'/><nd ref='41'/></way>
                <relation id='300'><member type='way' ref='301' role='outer'/><member type='way' ref='302' role='outer'/><tag k='building' v='yes'/><tag k='name' v='C'/></relation>
                <way id='400'><nd ref='10'/><nd ref='11'/><nd ref='12'/><nd ref='13'/><tag k='highway' v='footway'/>%s</way>
                <way id='401'><nd ref='12'/><nd ref='14'/><nd ref='15'/><tag k='highway' v='footway'/></way>
                <way id='402'><nd ref='50'/><nd ref='51'/><nd ref='52'/><nd ref='50'/><tag k='highway' v='footway'/></way>
                <way id='900'><nd ref='60'/><nd ref='61'/><nd ref='62'/><nd ref='60'/><tag k='building' v='yes'/></way>
                <way id='901'><nd ref='10'/><nd ref='9999'/><nd ref='11'/><nd ref='10'/><tag k='building' v='yes'/></way>
                %s</osm>
                """.formatted(secondDoor, roadTags, extraRoad);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private static void expectFailure(IoAction action, String label) throws Exception {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (IOException expected) {
            checks++;
        }
    }

    private interface IoAction { void run() throws Exception; }
}
