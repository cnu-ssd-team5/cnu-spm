package campus.map;

import campus.map.MapData.Building;
import campus.map.MapData.GeoPoint;
import campus.map.WalkGraph.Access;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Shortest walking routes between buildings and pins, using Dijkstra on the compressed pedestrian graph. */
public final class RouteService {
    public static final double WALKING_METERS_PER_SECOND = 1.3;

    public record Route(List<GeoPoint> points, double meters, boolean reachable, String message) {
        public Route { points = List.copyOf(points); }
    }

    private record QueueItem(int node, double distance) { }

    private final MapData data;
    private final WalkGraph walk;
    private final Map<String, Route> routeCache = new HashMap<>();

    public RouteService(MapData data) throws IOException {
        this.data = data;
        this.walk = new WalkGraph(data);
    }

    public MapData data() { return data; }
    public int routingNodeCount() { return walk.graph.size(); }
    public int osmRoutingNodeCount() { return walk.osmNodeCount(); }

    public synchronized Route route(Building from, Building to) {
        if (from == null || to == null) return unavailable("일정의 건물 정보가 없습니다.");
        if (data.building(from.id()) == null || data.building(to.id()) == null) return unavailable("현재 OSM에 없는 건물입니다.");
        if (from.id().equals(to.id())) return new Route(List.of(from.center()),0,true,"같은 건물 · 실내 이동 시간 별도");
        String key = from.id()+":"+to.id();
        Route cached = routeCache.get(key);
        if (cached != null) return cached;
        Route route = routeBetween(walk.access.getOrDefault(from.id(),List.of()), walk.access.getOrDefault(to.id(),List.of()));
        routeCache.put(key,route);
        return route;
    }

    public synchronized Route route(GeoPoint from, GeoPoint to) {
        if (!validPoint(from) || !validPoint(to)) return unavailable("핀 좌표가 올바르지 않습니다.");
        if (MapData.distance(from,to) < 0.1) return new Route(List.of(from),0,true,"");
        return routeBetween(walk.pinAccess(from,true),walk.pinAccess(to,false));
    }

    public synchronized Route route(Building from, GeoPoint to) {
        if (from == null || !validPoint(to)) return unavailable("장소 정보가 없습니다.");
        return routeBetween(walk.access.getOrDefault(from.id(),List.of()),walk.pinAccess(to,false));
    }

    public synchronized Route route(GeoPoint from, Building to) {
        if (!validPoint(from) || to == null) return unavailable("장소 정보가 없습니다.");
        return routeBetween(walk.pinAccess(from,true),walk.access.getOrDefault(to.id(),List.of()));
    }

    private static boolean validPoint(GeoPoint point) {
        return point != null && Double.isFinite(point.lat()) && Double.isFinite(point.lon())
                && Math.abs(point.lat()) <= 90 && Math.abs(point.lon()) <= 180;
    }

    private Route routeBetween(List<Access> sources, List<Access> destinations) {
        double best = Double.POSITIVE_INFINITY;
        List<GeoPoint> bestPoints = List.of();
        boolean estimated = false;
        for (Access a : sources) {
            for (Access b : destinations) {
                if (a.edgeFrom() >= 0 && a.edgeFrom() == b.edgeFrom() && a.edgeTo() == b.edgeTo() && a.offset() <= b.offset()) {
                    double direct = MapData.distance(a.door(),a.link().get(1)) + b.offset()-a.offset() + MapData.distance(b.link().get(1),b.door());
                    if (direct < best) {
                        best = direct;
                        bestPoints = sameEdgePoints(a,b);
                        estimated = a.estimated() || b.estimated();
                    }
                }
                int s = walk.graphIndex.get(a.nodeId()), t = walk.graphIndex.get(b.nodeId());
                List<Integer> path = path(s,t);
                if (path.isEmpty()) continue;
                double length = a.length()+b.length();
                for (int i = 1; i < path.size(); i++) length += walk.graph.get(path.get(i-1)).edges.get(walk.graph.get(path.get(i)).id);
                if (length < best) {
                    best = length;
                    bestPoints = routePoints(a,b,path);
                    estimated = a.estimated() || b.estimated();
                }
            }
        }
        if (!Double.isFinite(best)) return unavailable("OSM에 연결된 보행 경로가 없습니다.");
        return new Route(bestPoints,best,true,estimated ? "장소와 보행로 사이의 연결 일부 추정" : "");
    }

    private List<GeoPoint> routePoints(Access from, Access to, List<Integer> path) {
        List<GeoPoint> points = new ArrayList<>();
        if (from.link().isEmpty()) {
            points.add(from.door());
            points.add(walk.graph.get(path.get(0)).point);
        } else points.addAll(from.link());
        for (int i = 1; i < path.size(); i++) {
            List<GeoPoint> leg = walk.geometry(path.get(i-1),path.get(i));
            if (leg != null) points.addAll(leg.subList(1,leg.size()));
        }
        if (to.link().isEmpty()) points.add(to.door());
        else for (int i = to.link().size()-2; i >= 0; i--) points.add(to.link().get(i));
        return points;
    }

    private List<GeoPoint> sameEdgePoints(Access from, Access to) {
        List<GeoPoint> points = new ArrayList<>(List.of(from.door(),from.link().get(1)));
        List<GeoPoint> line = walk.geometry(walk.graphIndex.get(from.edgeFrom()),walk.graphIndex.get(from.edgeTo()));
        double offset = 0;
        for (int i = 1; i < line.size(); i++) {
            offset += MapData.distance(line.get(i-1),line.get(i));
            if (offset > from.offset() && offset < to.offset()) points.add(line.get(i));
        }
        points.add(to.link().get(1));
        points.add(to.door());
        return points;
    }

    private List<Integer> path(int source, int target) {
        double[] distance = new double[walk.graph.size()];
        Arrays.fill(distance,Double.POSITIVE_INFINITY);
        int[] previous = new int[walk.graph.size()];
        Arrays.fill(previous,-1);
        PriorityQueue<QueueItem> queue = new PriorityQueue<>(Comparator.comparingDouble(QueueItem::distance));
        distance[source] = 0; queue.add(new QueueItem(source,0));
        while (!queue.isEmpty()) {
            QueueItem item = queue.remove();
            if (item.distance > distance[item.node]) continue;
            if (item.node == target) break;
            for (var edge : walk.graph.get(item.node).edges.entrySet()) {
                int other = walk.graphIndex.get(edge.getKey());
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
}
