package campus.map;

import campus.map.OsmReader.Member;
import campus.map.OsmReader.Relation;
import campus.map.OsmReader.Bounds;
import campus.map.OsmReader.Data;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 한 학교의 지도 데이터: 경계, 건물, 도로와 사용자 장소를 관리합니다. */
public final class CampusMap {
    public record GeoPoint(double lat, double lon) { }
    public record Building(String id, String name, List<GeoPoint> outline, GeoPoint center) {
        public Building { outline = List.copyOf(outline); }
        @Override public String toString() { return name; }
    }
    public record MapWay(String id, String kind, List<GeoPoint> points) {
        public MapWay { points = List.copyOf(points); }
    }

    record Node(long id, GeoPoint point, Map<String,String> tags) { }
    record Way(long id, List<Long> refs, Map<String,String> tags) { }

    static final String BUNDLED_OSM = "/map/chonnam-campus.osm";
    private final List<Building> buildings = new ArrayList<>();
    private final Map<String, List<Long>> buildingRefs = new HashMap<>();
    private final List<List<GeoPoint>> obstacles = new ArrayList<>();
    private final Map<Long, Node> nodes;
    private final Map<Long, Way> osmWays;
    private final List<MapWay> ways = new ArrayList<>();
    private final Map<String, String> originalNames = new HashMap<>();
    /** 사용자 이름 변경 전 OSM 이름. 시간표별 지도 사본이 이 이름으로 시작한다. */
    private final Map<String, String> baseNames = new HashMap<>();
    private final Map<String, Building> byId = new LinkedHashMap<>();
    private List<GeoPoint> boundary = List.of();
    private String campusId;
    private String campusName;
    private double minLat;
    private double maxLat;
    private double minLon;
    private double maxLon;
    private Bounds extractBounds;

    private CampusMap(Data source) {
        nodes = source.nodes();
        osmWays = source.ways();
    }

    private CampusMap(CampusMap source) {
        nodes = source.nodes;
        osmWays = source.osmWays;
        ways.addAll(source.ways);
        buildingRefs.putAll(source.buildingRefs);
        obstacles.addAll(source.obstacles);
        baseNames.putAll(source.baseNames);
        boundary = source.boundary;
        campusId = source.campusId;
        campusName = source.campusName;
        minLat = source.minLat;
        maxLat = source.maxLat;
        minLon = source.minLon;
        maxLon = source.maxLon;
        extractBounds = source.extractBounds;
        for (Building place : source.buildings) {
            if (!place.id().startsWith("pin:")) {
                String originalName = source.originalName(place.id());
                originalNames.put(place.id(), originalName);
                String name = baseNames.getOrDefault(place.id(), originalName);
                putPlace(new Building(place.id(), name, place.outline(), place.center()));
            }
        }
    }

    /** 사용자 핀과 이름 변경을 제외한 장소 사본. OSM 원본 데이터는 공유한다. */
    public CampusMap copyBasePlaces() { return new CampusMap(this); }

    public static CampusMap load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("OSM 파일을 찾을 수 없습니다: " + file);
        try (InputStream input = Files.newInputStream(file)) {
            return load(input);
        }
    }

    private static CampusMap load(InputStream input) throws IOException {
        Data source = OsmReader.read(input);
        CampusMap result = new CampusMap(source);
        result.initialize(source);
        return result;
    }

    public List<Building> buildings() { return Collections.unmodifiableList(buildings); }
    public List<MapWay> ways() { return Collections.unmodifiableList(ways); }
    public Building building(String id) { return byId.get(id); }
    Node node(long id) { return nodes.get(id); }
    Iterable<Way> osmWays() { return Collections.unmodifiableCollection(osmWays.values()); }
    List<List<GeoPoint>> waterObstacles() { return Collections.unmodifiableList(obstacles); }
    List<Long> buildingNodeIds(String id) {
        List<Long> refs = buildingRefs.get(id);
        return refs == null ? List.of() : Collections.unmodifiableList(refs);
    }
    public String originalName(String id) {
        String original = originalNames.get(id);
        if (original != null) {
            return original;
        }
        Building place = building(id);
        return place == null ? "" : place.name();
    }
    public void putPlace(Building place) {
        originalNames.putIfAbsent(place.id(), place.name());
        buildings.removeIf(b -> b.id().equals(place.id()));
        buildings.add(place);
        byId.put(place.id(), place);
    }
    public void removePlace(String id) {
        if (!id.startsWith("pin:")) {
            return;
        }
        buildings.removeIf(b -> b.id().equals(id));
        byId.remove(id);
        originalNames.remove(id);
    }

    public double minLat() { return minLat; }
    public double maxLat() { return maxLat; }
    public double minLon() { return minLon; }
    public double maxLon() { return maxLon; }
    public String campusId() { return campusId; }
    public String campusName() { return campusName; }

    private void initialize(Data source) throws IOException {
        setCampusBoundary(source);
        Set<Long> relationMembers = addRelationBuildings(source);
        addWays(relationMembers);
        addBusStops();
        rememberOriginalNames();
        if (buildings.isEmpty()) {
            throw new IOException("캠퍼스 건물이 없습니다.");
        }
        buildings.sort(Comparator.comparing(Building::name, Collator.getInstance(Locale.KOREAN)));
    }

    private void setCampusBoundary(Data source) throws IOException {
        CampusBoundary campus = findCampusBoundary(source);
        campusId = campus.id();
        campusName = campus.name().isBlank() ? "캠퍼스" : campus.name();
        extractBounds = source.bounds();
        boundary = campus.points();
        minLat = boundary.stream().mapToDouble(GeoPoint::lat).min().orElseThrow();
        maxLat = boundary.stream().mapToDouble(GeoPoint::lat).max().orElseThrow();
        minLon = boundary.stream().mapToDouble(GeoPoint::lon).min().orElseThrow();
        maxLon = boundary.stream().mapToDouble(GeoPoint::lon).max().orElseThrow();
        ways.add(new MapWay(campusId, "boundary", boundary));
    }

    private static CampusBoundary findCampusBoundary(Data source) throws IOException {
        List<CampusBoundary> campuses = new ArrayList<>();
        for (Way way : source.ways().values()) {
            if (OsmTags.isUniversity(way.tags())) {
                addCampus(campuses, "w" + way.id(), way.tags(), points(way.refs(), source.nodes()));
            }
        }
        // shortcut: 관계는 하나의 외곽선만 지원합니다. 분리된 캠퍼스까지 필요하면 다중 외곽선 처리를 추가합니다.
        for (Relation relation : source.relations()) {
            if (OsmTags.isUniversity(relation.tags())) {
                List<Long> refs = outerRing(relation, source.ways());
                addCampus(campuses, "r" + relation.id(), relation.tags(), points(refs, source.nodes()));
            }
        }
        // 주변 대학이 함께 들어온 파일은 면적이 가장 큰 캠퍼스를 표시합니다.
        return largestCampus(campuses);
    }

    private static CampusBoundary largestCampus(List<CampusBoundary> campuses) throws IOException {
        CampusBoundary largest = null;
        double largestArea = -1;
        for (CampusBoundary candidate : campuses) {
            double area = MapGeometry.area(candidate.points());
            int comparison = Double.compare(area, largestArea);
            if (largest == null || comparison > 0
                    || comparison == 0 && candidate.id().compareTo(largest.id()) > 0) {
                largest = candidate;
                largestArea = area;
            }
        }
        if (largest == null) {
            throw new IOException("amenity=university인 닫힌 캠퍼스 경계와 경계 노드가 포함된 OSM 파일이 필요합니다.");
        }
        return largest;
    }

    private Set<Long> addRelationBuildings(Data source) {
        Set<Long> relationMembers = new HashSet<>();
        for (Relation relation : source.relations()) {
            if (!OsmTags.isBuilding(relation.tags())) {
                continue;
            }
            List<Long> refs = outerRing(relation, source.ways());
            List<GeoPoint> outline = points(refs, source.nodes());
            if (!addBuilding("r" + relation.id(), relation.tags(), outline)) {
                continue;
            }
            buildingRefs.put("r" + relation.id(), refs);
            for (Member member : relation.members()) {
                relationMembers.add(member.ref());
            }
        }
        return relationMembers;
    }

    private void addWays(Set<Long> relationMembers) {
        for (Way way : osmWays.values()) {
            List<GeoPoint> outline = points(way.refs(), nodes);
            if (outline.size() < 2) {
                continue;
            }
            addWayBuilding(way, outline, relationMembers);
            addDisplayWay(way, outline);
        }
    }

    private void addWayBuilding(Way way, List<GeoPoint> outline, Set<Long> relationMembers) {
        if (OsmTags.isBuilding(way.tags()) && !relationMembers.contains(way.id())) {
            String id = "w" + way.id();
            if (addBuilding(id, way.tags(), outline)) {
                buildingRefs.put(id, way.refs());
            }
        }
    }

    private void addDisplayWay(Way way, List<GeoPoint> outline) {
        String kind = OsmTags.displayKind(way.tags());
        if (kind == null || outline.stream().noneMatch(this::inExtractBounds)) {
            return;
        }
        ways.add(new MapWay("w" + way.id(), kind, outline));
        if ("water".equals(kind)) {
            obstacles.add(outline);
        }
    }

    private void addBusStops() {
        addBusPlatforms();
        addBusStopPositions();
    }

    private void addBusPlatforms() {
        for (Node node : nodes.values()) {
            if (OsmTags.isBusPlatform(node.tags())) {
                addBusStop("bus:n" + node.id(), node.tags(), node.point());
            }
        }
        for (Way way : osmWays.values()) {
            if (OsmTags.isBusPlatform(way.tags())) {
                List<GeoPoint> shape = points(way.refs(), nodes);
                if (!shape.isEmpty()) {
                    addBusStop("bus:w" + way.id(), way.tags(), MapGeometry.center(shape));
                }
            }
        }
    }

    private void addBusStopPositions() {
        for (Node node : nodes.values()) {
            if (!OsmTags.isBusStopPosition(node.tags())) {
                continue;
            }
            if (!hasNearbyBusStop(OsmTags.name(node.tags()), node.point())) {
                addBusStop("bus:n" + node.id(), node.tags(), node.point());
            }
        }
    }

    private boolean hasNearbyBusStop(String title, GeoPoint point) {
        for (Building place : buildings) {
            if (place.id().startsWith("bus:") && originalNames.get(place.id()).equals(title)
                    && distance(place.center(), point) < 75) {
                return true;
            }
        }
        return false;
    }

    private void rememberOriginalNames() {
        for (Building building : buildings) {
            String name = building.name().equals("이름없는 건물")
                    ? "이름 없는 건물 · " + building.id() : building.name();
            originalNames.putIfAbsent(building.id(), name);
        }
    }

    private record CampusBoundary(String id, String name, List<GeoPoint> points) { }

    private static void addCampus(List<CampusBoundary> campuses, String id, Map<String,String> tags, List<GeoPoint> outline) {
        if (!OsmTags.isUniversity(tags) || !MapGeometry.isClosedPolygon(outline)) {
            return;
        }
        for (GeoPoint point : outline) {
            if (!MapGeometry.isValid(point)) {
                return;
            }
        }
        if (MapGeometry.area(outline) > 0) {
            campuses.add(new CampusBoundary(id, OsmTags.name(tags), outline));
        }
    }

    private boolean addBuilding(String id, Map<String,String> tags, List<GeoPoint> outline) {
        if (!MapGeometry.isClosedPolygon(outline)) {
            return false;
        }
        GeoPoint center = MapGeometry.center(outline);
        if (!MapGeometry.contains(center, boundary) && outline.stream().noneMatch(p -> MapGeometry.contains(p, boundary))) {
            return false;
        }
        String title = OsmTags.name(tags);
        if (title.isBlank()) {
            title = "이름없는 건물";
        }
        baseNames.put(id, title);
        Building building = new Building(id, title, outline, center);
        buildings.add(building);
        byId.put(id, building);
        return true;
    }

    private boolean inDisplayBounds(GeoPoint p) {
        return p.lat >= minLat-0.00045 && p.lat <= maxLat+0.00045
                && p.lon >= minLon-0.00055 && p.lon <= maxLon+0.00055;
    }

    private void addBusStop(String id, Map<String,String> tags, GeoPoint point) {
        String title = OsmTags.name(tags);
        if (title.isBlank() || !inExtractBounds(point)) {
            return;
        }
        putPlace(new Building(id, title, List.of(point), point));
        originalNames.put(id, title);
        baseNames.put(id, title);
    }
    private boolean inExtractBounds(GeoPoint p) {
        if (extractBounds == null) {
            return inDisplayBounds(p);
        }
        return extractBounds.contains(p);
    }
    boolean nearCampus(GeoPoint p) { return inExtractBounds(p); }

    static List<GeoPoint> points(List<Long> refs, Map<Long,Node> nodes) {
        List<GeoPoint> result = new ArrayList<>();
        for (long ref : refs) {
            Node node = nodes.get(ref);
            if (node == null) return List.of();
            result.add(node.point);
        }
        return result;
    }

    /** 여러 way의 끝점 ID를 맞춰 하나의 외곽선으로 연결합니다. */
    private static List<Long> outerRing(Relation relation, Map<Long,Way> ways) {
        List<List<Long>> pieces = new ArrayList<>();
        for (Member member : relation.members()) {
            if (!member.role().isEmpty() && !member.role().equals("outer")) {
                continue;
            }
            Way way = ways.get(member.ref());
            if (way != null && !way.refs.isEmpty()) {
                pieces.add(new ArrayList<>(way.refs));
            }
        }
        if (pieces.isEmpty()) {
            return List.of();
        }
        List<Long> ring = new ArrayList<>(pieces.remove(0));
        while (!pieces.isEmpty() && !ring.get(0).equals(ring.get(ring.size()-1))) {
            boolean joined = false;
            for (int i = 0; i < pieces.size(); i++) {
                List<Long> piece = pieces.get(i);
                long last = ring.get(ring.size()-1);
                if (piece.get(piece.size()-1) == last) {
                    Collections.reverse(piece);
                }
                if (piece.get(0) == last) {
                    ring.addAll(piece.subList(1,piece.size()));
                    pieces.remove(i);
                    joined = true;
                    break;
                }
            }
            if (!joined) {
                return List.of();
            }
        }
        return ring;
    }

    public static double distance(GeoPoint a, GeoPoint b) {
        return MapGeometry.distance(a, b);
    }
}
