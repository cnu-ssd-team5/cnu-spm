package campus.map;

import campus.map.CampusMap.Building;
import campus.map.CampusMap.GeoPoint;
import campus.map.CampusMap.Node;
import campus.map.CampusMap.Way;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The pedestrian graph: walkable OSM ways, building connections, and degree-2 vertices compressed. */
final class WalkGraph {
    record Access(long nodeId, GeoPoint door, double length, boolean estimated) { }
    static final class Vertex {
        final long id;
        final GeoPoint point;
        final Map<Long, Double> edges = new LinkedHashMap<>();
        Vertex(long id, GeoPoint point) { this.id = id; this.point = point; }
    }

    private final CampusMap map;
    private final Map<Long, Vertex> rawGraph = new LinkedHashMap<>();
    final List<Vertex> graph = new ArrayList<>();
    final Map<Long, Integer> graphIndex = new HashMap<>();
    final Map<String, List<Access>> access = new HashMap<>();
    private final Map<Long, List<GeoPoint>> geometry = new HashMap<>();

    WalkGraph(CampusMap map) throws IOException {
        this.map = map;
        for (Way way : map.osmWays.values()) {
            if (CampusMap.points(way.refs(), map.nodes).size() < 2) continue;
            if (walkable(way.tags())) addRoad(way);
        }
        if (rawGraph.isEmpty()) throw new IOException("캠퍼스 보행 도로가 없습니다.");
        // 직접 조사한 지름길(건물 통과 등)은 이 위치에서 rawGraph에 간선으로 추가하면 됩니다.
        for (Building b : map.buildings) access.put(b.id(), findAccess(b, map.buildingRefs.get(b.id())));
        compressGraph();
    }

    int osmNodeCount() { return rawGraph.size(); }

    List<GeoPoint> geometry(int a, int b) { return geometry.get(edgeKey(a,b)); }

    private void addRoad(Way way) {
        for (int i = 1; i < way.refs().size(); i++) {
            Node a = map.nodes.get(way.refs().get(i-1)), b = map.nodes.get(way.refs().get(i));
            if (a == null || b == null || blocked(a.tags()) || blocked(b.tags())) continue;
            GeoPoint middle = new GeoPoint((a.point().lat()+b.point().lat())/2,(a.point().lon()+b.point().lon())/2);
            if (!map.nearCampus(middle)) continue;
            Vertex va = rawGraph.computeIfAbsent(a.id(), id -> new Vertex(id,a.point()));
            Vertex vb = rawGraph.computeIfAbsent(b.id(), id -> new Vertex(id,b.point()));
            double length = CampusMap.distance(a.point(),b.point());
            if (length < 0.01) continue;
            // Ordinary vehicle one-way restrictions do not apply to pedestrians.
            String direction = way.tags().getOrDefault("oneway:foot", "no");
            boolean forward = !"-1".equals(direction) && !"no".equals(way.tags().get("foot:forward"));
            boolean backward = !Set.of("yes","1","true").contains(direction)
                    && !"no".equals(way.tags().get("foot:backward"));
            if (forward) va.edges.merge(b.id(),length,Math::min);
            if (backward) vb.edges.merge(a.id(),length,Math::min);
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

    private List<Access> findAccess(Building building, List<Long> refs) {
        List<Access> candidates = new ArrayList<>();
        if (refs != null) {
            for (long ref : refs) {
                Node node = map.nodes.get(ref);
                if (node == null || !node.tags().containsKey("entrance") || "no".equals(node.tags().get("entrance")) || blocked(node.tags())) continue;
                if (rawGraph.containsKey(ref) && !rawGraph.get(ref).edges.isEmpty()) {
                    candidates.add(new Access(ref,node.point(),0,false));
                } else {
                    Access a = nearestAccess(building,List.of(node.point()),55);
                    if (a != null) candidates.add(a);
                }
            }
        }
        if (candidates.isEmpty()) {
            Access a = nearestAccess(building,building.outline(),75);
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
                double meters = CampusMap.distance(door,vertex.point);
                if (meters >= best || !connectorClear(door,vertex.point,building)) continue;
                best = meters;
                result = new Access(vertex.id,door,meters,meters > 0.1);
            }
        }
        return result;
    }

    private boolean connectorClear(GeoPoint a, GeoPoint b, Building target) {
        if (CampusMap.distance(a,b) < 0.1) return true;
        for (Building other : map.buildings) {
            if (other != target && CampusMap.intersectsPolygon(a,b,other.outline())) return false;
        }
        for (List<GeoPoint> water : map.obstacles) if (CampusMap.intersectsPolygon(a,b,water)) return false;
        for (int i = 1; i < 10; i++) {
            double f = i/10.0;
            GeoPoint p = new GeoPoint(a.lat()+(b.lat()-a.lat())*f,a.lon()+(b.lon()-a.lon())*f);
            if (CampusMap.inside(p,target.outline())) return false;
            for (Building other : map.buildings) {
                if (other != target && Math.abs(other.center().lat()-p.lat()) < 0.0015
                        && Math.abs(other.center().lon()-p.lon()) < 0.0018 && CampusMap.inside(p,other.outline())) return false;
            }
            for (List<GeoPoint> water : map.obstacles) if (CampusMap.inside(p,water)) return false;
        }
        return true;
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

    private static long edgeKey(int a,int b) { return ((long)a<<32)|(b&0xffffffffL); }
}
