package campus.map;

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
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** An offline OSM extract of the campus: boundary, buildings and the shapes drawn on the map. */
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
    private record Member(long ref, String role) { }
    private record Relation(long id, List<Member> members, Map<String,String> tags) { }

    static final String BUNDLED_OSM = "/map/chonnam-campus.osm";
    private static final double EARTH_RADIUS = 6_371_008.8;
    final List<Building> buildings = new ArrayList<>();
    final Map<String, List<Long>> buildingRefs = new HashMap<>();
    final List<List<GeoPoint>> obstacles = new ArrayList<>();
    final Map<Long, Node> nodes;
    final Map<Long, Way> osmWays;
    private final List<MapWay> ways = new ArrayList<>();
    private final Map<String, Building> byId = new LinkedHashMap<>();
    private List<GeoPoint> boundary = List.of();
    private double minLat, maxLat, minLon, maxLon;

    private CampusMap(Reader source) {
        nodes = source.nodes;
        osmWays = source.ways;
    }

    /** 프로그램에 포함된 전남대 지도(resources/map)를 읽습니다. 실행 위치와 상관없이 동작합니다. */
    public static CampusMap loadBundled() throws IOException {
        try (InputStream input = CampusMap.class.getResourceAsStream(BUNDLED_OSM)) {
            if (input == null) throw new IOException("프로그램에 포함된 지도 데이터를 찾을 수 없습니다: " + BUNDLED_OSM);
            return load(input);
        }
    }

    public static CampusMap load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("OSM 파일을 찾을 수 없습니다: " + file);
        try (InputStream input = Files.newInputStream(file)) {
            return load(input);
        }
    }

    private static CampusMap load(InputStream input) throws IOException {
        Reader reader = new Reader();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.newSAXParser().parse(input, reader);
        } catch (Exception ex) {
            throw new IOException("OSM XML을 읽을 수 없습니다.", ex);
        }
        CampusMap result = new CampusMap(reader);
        result.initialize(reader);
        return result;
    }

    public List<Building> buildings() { return Collections.unmodifiableList(buildings); }
    public List<MapWay> ways() { return Collections.unmodifiableList(ways); }
    public Building building(String id) { return byId.get(id); }
    public double minLat() { return minLat; }
    public double maxLat() { return maxLat; }
    public double minLon() { return minLon; }
    public double maxLon() { return maxLon; }
    public String attribution() { return "© OpenStreetMap contributors · ODbL 1.0"; }

    private void initialize(Reader source) throws IOException {
        Way campus = source.ways.values().stream()
                .filter(w -> "university".equals(w.tags.get("amenity")))
                .filter(w -> name(w.tags).contains("전남대학교") || name(w.tags).contains("Chonnam"))
                .max(Comparator.comparingInt(w -> w.refs.size())).orElse(null);
        if (campus == null) throw new IOException("전남대학교 캠퍼스 경계가 포함된 OSM 파일이 필요합니다.");
        boundary = points(campus.refs, source.nodes);
        if (boundary.size() < 4) throw new IOException("캠퍼스 경계 노드가 누락되었습니다.");
        minLat = boundary.stream().mapToDouble(GeoPoint::lat).min().orElseThrow();
        maxLat = boundary.stream().mapToDouble(GeoPoint::lat).max().orElseThrow();
        minLon = boundary.stream().mapToDouble(GeoPoint::lon).min().orElseThrow();
        maxLon = boundary.stream().mapToDouble(GeoPoint::lon).max().orElseThrow();
        ways.add(new MapWay("w" + campus.id, "boundary", boundary));

        Set<Long> relationMembers = new HashSet<>();
        for (Relation relation : source.relations) {
            if (!relation.tags.containsKey("building") || "no".equals(relation.tags.get("building"))) continue;
            List<Long> refs = outerRing(relation, source.ways);
            List<GeoPoint> outline = points(refs, source.nodes);
            if (!addBuilding("r" + relation.id, relation.tags, outline)) continue;
            buildingRefs.put("r" + relation.id, refs);
            relation.members.forEach(m -> relationMembers.add(m.ref));
        }
        for (Way way : source.ways.values()) {
            List<GeoPoint> outline = points(way.refs, source.nodes);
            if (outline.size() < 2) continue;
            if (way.tags.containsKey("building") && !"no".equals(way.tags.get("building"))
                    && !relationMembers.contains(way.id)) {
                if (addBuilding("w" + way.id, way.tags, outline)) buildingRefs.put("w" + way.id, way.refs);
            }
            String kind = kind(way.tags);
            if (kind != null && outline.stream().anyMatch(this::inDisplayBounds)) {
                ways.add(new MapWay("w" + way.id, kind, outline));
                if ("water".equals(kind)) obstacles.add(outline);
            }
        }
        if (buildings.isEmpty()) throw new IOException("캠퍼스 건물이 없습니다.");
        buildings.sort(Comparator.comparing(Building::name, Collator.getInstance(Locale.KOREAN)));
    }

    private boolean addBuilding(String id, Map<String,String> tags, List<GeoPoint> outline) {
        if (outline.size() < 4 || !outline.get(0).equals(outline.get(outline.size()-1))) return false;
        GeoPoint center = centroid(outline);
        if (!inside(center, boundary) && outline.stream().noneMatch(p -> inside(p, boundary))) return false;
        String title = name(tags);
        if (title.isBlank()) title = "이름 없는 건물 · " + id;
        Building building = new Building(id, title, outline, center);
        buildings.add(building);
        byId.put(id, building);
        return true;
    }

    private boolean inDisplayBounds(GeoPoint p) {
        return p.lat >= minLat-0.00045 && p.lat <= maxLat+0.00045
                && p.lon >= minLon-0.00055 && p.lon <= maxLon+0.00055;
    }

    boolean nearCampus(GeoPoint p) {
        if (!inDisplayBounds(p)) return false;
        if (inside(p, boundary)) return true;
        for (int i = 1; i < boundary.size(); i++) {
            if (segmentDistance(p, boundary.get(i-1), boundary.get(i)) < 42) return true;
        }
        return false;
    }

    private static String kind(Map<String,String> tags) {
        if ("water".equals(tags.get("natural")) || "riverbank".equals(tags.get("waterway")) || tags.containsKey("water")) return "water";
        if (Set.of("pitch","track").contains(tags.getOrDefault("leisure",""))) return "pitch";
        if (Set.of("park","garden").contains(tags.getOrDefault("leisure",""))
                || Set.of("grass","forest","meadow","recreation_ground").contains(tags.getOrDefault("landuse",""))
                || Set.of("wood","scrub","grassland").contains(tags.getOrDefault("natural",""))) return "green";
        String highway = tags.getOrDefault("highway", "");
        if (highway.equals("steps")) return "steps";
        if (Set.of("footway","pedestrian","path","cycleway").contains(highway)) return "footway";
        if (!highway.isEmpty() && !Set.of("construction","proposed","corridor").contains(highway)) return "road";
        return null;
    }

    private static String name(Map<String,String> tags) {
        for (String key : List.of("name:ko","name","official_name","name:en","ref")) {
            String value = tags.getOrDefault(key,"");
            if (!value.isBlank()) return value;
        }
        return "";
    }

    static List<GeoPoint> points(List<Long> refs, Map<Long,Node> nodes) {
        List<GeoPoint> result = new ArrayList<>();
        for (long ref : refs) {
            Node node = nodes.get(ref);
            if (node == null) return List.of();
            result.add(node.point);
        }
        return result;
    }

    private static List<Long> outerRing(Relation relation, Map<Long,Way> ways) {
        List<List<Long>> pieces = new ArrayList<>();
        for (Member member : relation.members) {
            if (!member.role.isEmpty() && !member.role.equals("outer")) continue;
            Way way = ways.get(member.ref);
            if (way != null && !way.refs.isEmpty()) pieces.add(new ArrayList<>(way.refs));
        }
        if (pieces.isEmpty()) return List.of();
        List<Long> ring = new ArrayList<>(pieces.remove(0));
        while (!pieces.isEmpty() && !ring.get(0).equals(ring.get(ring.size()-1))) {
            boolean joined = false;
            for (int i = 0; i < pieces.size(); i++) {
                List<Long> piece = pieces.get(i);
                long last = ring.get(ring.size()-1);
                if (piece.get(piece.size()-1) == last) Collections.reverse(piece);
                if (piece.get(0) == last) {
                    ring.addAll(piece.subList(1,piece.size())); pieces.remove(i); joined = true; break;
                }
            }
            if (!joined) return List.of();
        }
        return ring;
    }

    public static double distance(GeoPoint a, GeoPoint b) {
        double dLat = Math.toRadians(b.lat-a.lat), dLon = Math.toRadians(b.lon-a.lon);
        double h = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(a.lat))*Math.cos(Math.toRadians(b.lat))*Math.sin(dLon/2)*Math.sin(dLon/2);
        return 2*EARTH_RADIUS*Math.asin(Math.min(1,Math.sqrt(h)));
    }

    private static GeoPoint centroid(List<GeoPoint> polygon) {
        double x = 0, y = 0, area = 0;
        GeoPoint origin = polygon.get(0);
        for (int i = 1; i < polygon.size(); i++) {
            GeoPoint a = polygon.get(i-1), b = polygon.get(i);
            double cross = (a.lon-origin.lon)*(b.lat-origin.lat)-(b.lon-origin.lon)*(a.lat-origin.lat);
            area += cross;
            x += (a.lon+b.lon-2*origin.lon)*cross;
            y += (a.lat+b.lat-2*origin.lat)*cross;
        }
        if (Math.abs(area) < 1e-16) return new GeoPoint(polygon.stream().mapToDouble(GeoPoint::lat).average().orElseThrow(),polygon.stream().mapToDouble(GeoPoint::lon).average().orElseThrow());
        return new GeoPoint(origin.lat+y/(3*area),origin.lon+x/(3*area));
    }

    static boolean inside(GeoPoint p, List<GeoPoint> polygon) {
        boolean inside = false;
        for (int i = 0,j = polygon.size()-1; i < polygon.size(); j = i++) {
            GeoPoint a = polygon.get(i), b = polygon.get(j);
            if ((a.lat>p.lat) != (b.lat>p.lat) && p.lon < (b.lon-a.lon)*(p.lat-a.lat)/(b.lat-a.lat)+a.lon) inside = !inside;
        }
        return inside;
    }

    static boolean intersectsPolygon(GeoPoint a, GeoPoint b, List<GeoPoint> polygon) {
        if (inside(a,polygon) || inside(b,polygon)) return true;
        for (int i = 1; i < polygon.size(); i++) {
            GeoPoint c = polygon.get(i-1), d = polygon.get(i);
            if (java.awt.geom.Line2D.linesIntersect(a.lon,a.lat,b.lon,b.lat,c.lon,c.lat,d.lon,d.lat)) return true;
        }
        return false;
    }

    private static double segmentDistance(GeoPoint p, GeoPoint a, GeoPoint b) {
        double cos = Math.cos(Math.toRadians(p.lat));
        double dx = (b.lon-a.lon)*cos, dy = b.lat-a.lat;
        double px = (p.lon-a.lon)*cos, py = p.lat-a.lat;
        double denominator = dx*dx+dy*dy;
        double f = denominator == 0 ? 0 : Math.max(0,Math.min(1,(px*dx+py*dy)/denominator));
        return distance(p,new GeoPoint(a.lat+(b.lat-a.lat)*f,a.lon+(b.lon-a.lon)*f));
    }

    private static final class Reader extends DefaultHandler {
        final Map<Long,Node> nodes = new LinkedHashMap<>();
        final Map<Long,Way> ways = new LinkedHashMap<>();
        final List<Relation> relations = new ArrayList<>();
        String type;
        long id;
        GeoPoint point;
        Map<String,String> tags;
        List<Long> refs;
        List<Member> members;
        @Override public void startElement(String uri,String local,String q,Attributes a) throws SAXException {
            try {
                if (Set.of("node","way","relation").contains(q)) {
                    type = q; id = Long.parseLong(a.getValue("id")); tags = new HashMap<>();
                    if (q.equals("node")) point = new GeoPoint(Double.parseDouble(a.getValue("lat")),Double.parseDouble(a.getValue("lon")));
                    if (q.equals("way")) refs = new ArrayList<>();
                    if (q.equals("relation")) members = new ArrayList<>();
                } else if (q.equals("tag") && tags != null) {
                    tags.put(a.getValue("k"),a.getValue("v"));
                } else if (q.equals("nd") && "way".equals(type)) {
                    refs.add(Long.parseLong(a.getValue("ref")));
                } else if (q.equals("member") && "relation".equals(type) && "way".equals(a.getValue("type"))) {
                    members.add(new Member(Long.parseLong(a.getValue("ref")),a.getValue("role")));
                }
            } catch (RuntimeException ex) { throw new SAXException("잘못된 OSM 요소",ex); }
        }
        @Override public void endElement(String uri,String local,String q) {
            if (!q.equals(type)) return;
            if (q.equals("node")) nodes.put(id,new Node(id,point,tags));
            if (q.equals("way")) ways.put(id,new Way(id,refs,tags));
            if (q.equals("relation")) relations.add(new Relation(id,members,tags));
            type = null; tags = null;
        }
    }
}
