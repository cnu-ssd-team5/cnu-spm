package campus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** An offline OSM extract, its display geometry, and its pedestrian routing graph. */
public final class MapData {
    public record GeoPoint(double lat, double lon) { }
    public record Building(String id, String name, List<GeoPoint> outline, GeoPoint center) {
        public Building { outline = List.copyOf(outline); }
        @Override public String toString() { return name; }
    }
    public record MapWay(String id, String kind, List<GeoPoint> points) {
        public MapWay { points = List.copyOf(points); }
    }
    public record Route(List<GeoPoint> points, double meters, boolean reachable, String message) {
        public Route { points = List.copyOf(points); }
    }

    private static final double EARTH_RADIUS = 6_371_008.8;
    private static final int FLOYD_LIMIT = 900;
    private final List<Building> buildings = new ArrayList<>();
    private final List<MapWay> ways = new ArrayList<>();
    private final Map<String, Building> byId = new LinkedHashMap<>();
    private final Map<String, List<Access>> access = new HashMap<>();
    private final Map<Long, Vertex> rawGraph = new LinkedHashMap<>();
    private final List<Vertex> graph = new ArrayList<>();
    private final Map<Long, Integer> graphIndex = new HashMap<>();
    private final Map<Long, List<GeoPoint>> geometry = new HashMap<>();
    private final Map<String, Route> routeCache = new HashMap<>();
    private final List<List<GeoPoint>> obstacles = new ArrayList<>();
    private List<GeoPoint> boundary = List.of();
    private double minLat, maxLat, minLon, maxLon;
    private double[][] distances;
    private int[][] next;

    private record Node(long id, GeoPoint point, Map<String,String> tags) { }
    private record Way(long id, List<Long> refs, Map<String,String> tags) { }
    private record Member(long ref, String role) { }
    private record Relation(long id, List<Member> members, Map<String,String> tags) { }
    private record Access(long nodeId, GeoPoint door, double length, boolean estimated) { }
    private record QueueItem(int node, double distance) { }
    private static final class Vertex {
        final long id;
        final GeoPoint point;
        final Map<Long, Double> edges = new LinkedHashMap<>();
        Vertex(long id, GeoPoint point) { this.id = id; this.point = point; }
    }

    private MapData() { }

    public static MapData load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("OSM 파일을 찾을 수 없습니다: " + file);
        Reader reader = new Reader();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.newSAXParser().parse(file.toFile(), reader);
        } catch (Exception ex) {
            throw new IOException("OSM XML을 읽을 수 없습니다.", ex);
        }
        MapData result = new MapData();
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
    public int routingNodeCount() { return graph.size(); }
    public int osmRoutingNodeCount() { return rawGraph.size(); }
    public String routingAlgorithm() { return distances == null ? "Dijkstra" : "Floyd–Warshall"; }
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

        Map<String, List<Long>> buildingRefs = new HashMap<>();
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
            if (walkable(way.tags)) addRoad(way, source.nodes);
        }
        if (buildings.isEmpty() || rawGraph.isEmpty()) throw new IOException("캠퍼스 건물 또는 보행 도로가 없습니다.");
        buildings.sort(Comparator.comparing(Building::name, Collator.getInstance(Locale.KOREAN)));
        for (Building b : buildings) access.put(b.id, findAccess(b, buildingRefs.get(b.id), source.nodes));
        compressGraph();
        if (graph.size() <= FLOYD_LIMIT) computeFloyd();
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

    private boolean nearCampus(GeoPoint p) {
        if (!inDisplayBounds(p)) return false;
        if (inside(p, boundary)) return true;
        for (int i = 1; i < boundary.size(); i++) {
            if (segmentDistance(p, boundary.get(i-1), boundary.get(i)) < 42) return true;
        }
        return false;
    }

    private void addRoad(Way way, Map<Long,Node> nodes) {
        for (int i = 1; i < way.refs.size(); i++) {
            Node a = nodes.get(way.refs.get(i-1)), b = nodes.get(way.refs.get(i));
            if (a == null || b == null || blocked(a.tags) || blocked(b.tags)) continue;
            GeoPoint middle = new GeoPoint((a.point.lat+b.point.lat)/2,(a.point.lon+b.point.lon)/2);
            if (!nearCampus(middle)) continue;
            Vertex va = rawGraph.computeIfAbsent(a.id, id -> new Vertex(id,a.point));
            Vertex vb = rawGraph.computeIfAbsent(b.id, id -> new Vertex(id,b.point));
            double length = distance(a.point,b.point);
            if (length < 0.01) continue;
            // Ordinary vehicle one-way restrictions do not apply to pedestrians.
            String direction = way.tags.getOrDefault("oneway:foot", "no");
            boolean forward = !"-1".equals(direction) && !"no".equals(way.tags.get("foot:forward"));
            boolean backward = !Set.of("yes","1","true").contains(direction)
                    && !"no".equals(way.tags.get("foot:backward"));
            if (forward) va.edges.merge(b.id,length,Math::min);
            if (backward) vb.edges.merge(a.id,length,Math::min);
        }
    }

    private static boolean blocked(Map<String,String> tags) {
        String foot = tags.getOrDefault("foot", "");
        if (Set.of("yes","designated","permissive","official").contains(foot)) return false;
        return Set.of("no","private","use_sidepath").contains(foot)
                || Set.of("no","private").contains(tags.getOrDefault("access",""))
                || Set.of("wall","fence","retaining_wall").contains(tags.getOrDefault("barrier",""));
    }

    private static boolean walkable(Map<String,String> tags) {
        if (blocked(tags) || "yes".equals(tags.get("indoor")) || tags.containsKey("construction")) return false;
        String highway = tags.getOrDefault("highway", "");
        if (highway.equals("cycleway")) return Set.of("yes","designated","permissive").contains(tags.getOrDefault("foot",""));
        return Set.of("footway","pedestrian","path","steps","service","residential","living_street",
                "track","unclassified","tertiary","tertiary_link","secondary","secondary_link","primary","primary_link")
                .contains(highway);
    }

    private List<Access> findAccess(Building building, List<Long> refs, Map<Long,Node> nodes) {
        List<Access> candidates = new ArrayList<>();
        if (refs != null) {
            for (long ref : refs) {
                Node node = nodes.get(ref);
                if (node == null || !node.tags.containsKey("entrance") || "no".equals(node.tags.get("entrance")) || blocked(node.tags)) continue;
                if (rawGraph.containsKey(ref) && !rawGraph.get(ref).edges.isEmpty()) {
                    candidates.add(new Access(ref,node.point,0,false));
                } else {
                    Access a = nearestAccess(building,List.of(node.point),55);
                    if (a != null) candidates.add(a);
                }
            }
        }
        if (candidates.isEmpty()) {
            Access a = nearestAccess(building,building.outline,75);
            if (a != null) candidates.add(a);
        }
        return List.copyOf(candidates);
    }

    private Access nearestAccess(Building building, List<GeoPoint> doors, double maximum) {
        double best = maximum;
        Access result = null;
        for (GeoPoint door : doors) {
            for (Vertex vertex : rawGraph.values()) {
                if (vertex.edges.isEmpty()) continue;
                double meters = distance(door,vertex.point);
                if (meters >= best || !connectorClear(door,vertex.point,building)) continue;
                best = meters;
                result = new Access(vertex.id,door,meters,meters > 0.1);
            }
        }
        return result;
    }

    private boolean connectorClear(GeoPoint a, GeoPoint b, Building target) {
        if (distance(a,b) < 0.1) return true;
        for (Building other : buildings) {
            if (other != target && intersectsPolygon(a,b,other.outline)) return false;
        }
        for (List<GeoPoint> water : obstacles) if (intersectsPolygon(a,b,water)) return false;
        for (int i = 1; i < 10; i++) {
            double f = i/10.0;
            GeoPoint p = new GeoPoint(a.lat+(b.lat-a.lat)*f,a.lon+(b.lon-a.lon)*f);
            if (inside(p,target.outline)) return false;
            for (Building other : buildings) {
                if (other != target && Math.abs(other.center.lat-p.lat) < 0.0015
                        && Math.abs(other.center.lon-p.lon) < 0.0018 && inside(p,other.outline)) return false;
            }
            for (List<GeoPoint> water : obstacles) if (inside(p,water)) return false;
        }
        return true;
    }

    private static boolean intersectsPolygon(GeoPoint a, GeoPoint b, List<GeoPoint> polygon) {
        if (inside(a,polygon) || inside(b,polygon)) return true;
        for (int i = 1; i < polygon.size(); i++) {
            GeoPoint c = polygon.get(i-1), d = polygon.get(i);
            if (java.awt.geom.Line2D.linesIntersect(a.lon,a.lat,b.lon,b.lat,c.lon,c.lat,d.lon,d.lat)) return true;
        }
        return false;
    }

    private void compressGraph() {
        Map<Long,Set<Long>> neighbors = new HashMap<>();
        for (Vertex v : rawGraph.values()) {
            neighbors.computeIfAbsent(v.id,k -> new HashSet<>());
            for (long other : v.edges.keySet()) {
                neighbors.get(v.id).add(other);
                neighbors.computeIfAbsent(other,k -> new HashSet<>()).add(v.id);
            }
        }
        Set<Long> keep = new HashSet<>();
        for (var entry : neighbors.entrySet()) if (entry.getValue().size() != 2) keep.add(entry.getKey());
        for (List<Access> list : access.values()) for (Access a : list) keep.add(a.nodeId);
        // Any component consisting solely of a closed loop still needs a retained vertex.
        Set<Long> seen = new HashSet<>();
        for (long start : neighbors.keySet()) {
            if (!seen.add(start)) continue;
            boolean retained = keep.contains(start);
            List<Long> component = new ArrayList<>(List.of(start));
            for (int i = 0; i < component.size(); i++) {
                for (long n : neighbors.get(component.get(i))) if (seen.add(n)) {
                    component.add(n); retained |= keep.contains(n);
                }
            }
            if (!retained) keep.add(start);
        }
        for (Vertex original : rawGraph.values()) if (keep.contains(original.id)) {
            graphIndex.put(original.id,graph.size());
            graph.add(new Vertex(original.id,original.point));
        }
        for (Vertex start : graph) {
            for (long neighbor : rawGraph.get(start.id).edges.keySet()) {
                List<GeoPoint> line = new ArrayList<>(List.of(start.point));
                long previous = start.id, current = neighbor;
                double length = rawGraph.get(previous).edges.get(current);
                Set<Long> visited = new HashSet<>(List.of(start.id));
                while (visited.add(current)) {
                    line.add(rawGraph.get(current).point);
                    if (keep.contains(current)) {
                        Double old = start.edges.get(current);
                        if (old == null || length < old) {
                            start.edges.put(current,length);
                            geometry.put(edgeKey(graphIndex.get(start.id),graphIndex.get(current)),List.copyOf(line));
                        }
                        break;
                    }
                    long target = -1;
                    for (long n : rawGraph.get(current).edges.keySet()) if (n != previous) { target = n; break; }
                    if (target == -1) break;
                    length += rawGraph.get(current).edges.get(target);
                    previous = current; current = target;
                }
            }
        }
    }

    private void computeFloyd() {
        int n = graph.size();
        distances = new double[n][n];
        next = new int[n][n];
        for (int i = 0; i < n; i++) {
            Arrays.fill(distances[i],Double.POSITIVE_INFINITY);
            Arrays.fill(next[i],-1);
            distances[i][i] = 0;
            next[i][i] = i;
            for (var edge : graph.get(i).edges.entrySet()) {
                int j = graphIndex.get(edge.getKey());
                distances[i][j] = edge.getValue(); next[i][j] = j;
            }
        }
        for (int k = 0; k < n; k++) {
            double[] via = distances[k];
            for (int i = 0; i < n; i++) {
                double ik = distances[i][k];
                if (!Double.isFinite(ik)) continue;
                double[] row = distances[i];
                for (int j = 0; j < n; j++) {
                    double candidate = ik+via[j];
                    if (candidate < row[j]) { row[j] = candidate; next[i][j] = next[i][k]; }
                }
            }
        }
    }

    public synchronized Route route(Building from, Building to) {
        if (from == null || to == null) return unavailable("일정의 건물 정보가 없습니다.");
        if (!byId.containsKey(from.id) || !byId.containsKey(to.id)) return unavailable("현재 OSM에 없는 건물입니다.");
        if (from.id.equals(to.id)) return new Route(List.of(from.center),0,true,"같은 건물 · 실내 이동 시간 별도");
        String key = from.id+":"+to.id;
        Route cached = routeCache.get(key);
        if (cached != null) return cached;
        double best = Double.POSITIVE_INFINITY;
        List<Integer> bestPath = List.of();
        Access entry = null, exit = null;
        for (Access a : access.getOrDefault(from.id,List.of())) {
            for (Access b : access.getOrDefault(to.id,List.of())) {
                int s = graphIndex.get(a.nodeId), t = graphIndex.get(b.nodeId);
                List<Integer> path = path(s,t);
                if (path.isEmpty()) continue;
                double length = a.length+b.length;
                for (int i = 1; i < path.size(); i++) length += graph.get(path.get(i-1)).edges.get(graph.get(path.get(i)).id);
                if (length < best) { best = length; bestPath = path; entry = a; exit = b; }
            }
        }
        Route route;
        if (entry == null) {
            route = unavailable("OSM에 연결된 보행 경로가 없습니다.");
        } else {
            List<GeoPoint> points = new ArrayList<>();
            points.add(entry.door);
            points.add(graph.get(bestPath.get(0)).point);
            for (int i = 1; i < bestPath.size(); i++) {
                List<GeoPoint> leg = geometry.get(edgeKey(bestPath.get(i-1),bestPath.get(i)));
                if (leg != null) points.addAll(leg.subList(1,leg.size()));
            }
            points.add(exit.door);
            String note = entry.estimated || exit.estimated
                    ? "OSM 보행 경로 · 건물과 길 사이의 짧은 연결 구간은 추정"
                    : "OSM 보행 경로 · 실내 이동 시간 별도";
            route = new Route(points,best,true,note);
        }
        routeCache.put(key,route);
        return route;
    }

    private List<Integer> path(int source, int target) {
        if (distances != null) {
            if (next[source][target] < 0) return List.of();
            List<Integer> result = new ArrayList<>(List.of(source));
            int current = source;
            while (current != target && result.size() <= graph.size()) {
                current = next[current][target];
                if (current < 0) return List.of();
                result.add(current);
            }
            return current == target ? result : List.of();
        }
        double[] distance = new double[graph.size()];
        Arrays.fill(distance,Double.POSITIVE_INFINITY);
        int[] previous = new int[graph.size()];
        Arrays.fill(previous,-1);
        PriorityQueue<QueueItem> queue = new PriorityQueue<>(Comparator.comparingDouble(QueueItem::distance));
        distance[source] = 0; queue.add(new QueueItem(source,0));
        while (!queue.isEmpty()) {
            QueueItem item = queue.remove();
            if (item.distance > distance[item.node]) continue;
            if (item.node == target) break;
            for (var edge : graph.get(item.node).edges.entrySet()) {
                int other = graphIndex.get(edge.getKey());
                double candidate = item.distance+edge.getValue();
                if (candidate < distance[other]) {
                    distance[other] = candidate; previous[other] = item.node;
                    queue.add(new QueueItem(other,candidate));
                }
            }
        }
        if (!Double.isFinite(distance[target])) return List.of();
        List<Integer> result = new ArrayList<>();
        for (int at = target; at != -1; at = previous[at]) result.add(at);
        Collections.reverse(result);
        return result;
    }

    private static Route unavailable(String message) { return new Route(List.of(),Double.NaN,false,message); }
    private static long edgeKey(int a,int b) { return ((long)a<<32)|(b&0xffffffffL); }

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

    private static List<GeoPoint> points(List<Long> refs, Map<Long,Node> nodes) {
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

    private static boolean inside(GeoPoint p, List<GeoPoint> polygon) {
        boolean inside = false;
        for (int i = 0,j = polygon.size()-1; i < polygon.size(); j = i++) {
            GeoPoint a = polygon.get(i), b = polygon.get(j);
            if ((a.lat>p.lat) != (b.lat>p.lat) && p.lon < (b.lon-a.lon)*(p.lat-a.lat)/(b.lat-a.lat)+a.lon) inside = !inside;
        }
        return inside;
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
