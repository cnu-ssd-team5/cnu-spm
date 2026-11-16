package campus.map;

import campus.map.CampusMap.Building;
import campus.map.CampusMap.GeoPoint;
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

/** Shortest walking routes between buildings, using Dijkstra on the compressed pedestrian graph. */
public final class RouteService {
    public static final double WALKING_METERS_PER_SECOND = 1.3;

    public record Route(List<GeoPoint> points, double meters, boolean reachable, String message) {
        public Route { points = List.copyOf(points); }
    }

    private record QueueItem(int node, double distance) { }

    private final CampusMap map;
    private final WalkGraph walk;
    private final Map<String, Route> routeCache = new HashMap<>();

    public RouteService(CampusMap map) throws IOException {
        this.map = map;
        this.walk = new WalkGraph(map);
    }

    public int routingNodeCount() { return walk.graph.size(); }
    public int osmRoutingNodeCount() { return walk.osmNodeCount(); }

    public synchronized Route route(Building from, Building to) {
        if (from == null || to == null) return unavailable("일정의 건물 정보가 없습니다.");
        if (map.building(from.id()) == null || map.building(to.id()) == null) return unavailable("현재 OSM에 없는 건물입니다.");
        if (from.id().equals(to.id())) return new Route(List.of(from.center()),0,true,"같은 건물 · 실내 이동 시간 별도");
        String key = from.id()+":"+to.id();
        Route cached = routeCache.get(key);
        if (cached != null) return cached;
        double best = Double.POSITIVE_INFINITY;
        List<Integer> bestPath = List.of();
        Access entry = null, exit = null;
        for (Access a : walk.access.getOrDefault(from.id(),List.of())) {
            for (Access b : walk.access.getOrDefault(to.id(),List.of())) {
                int s = walk.graphIndex.get(a.nodeId()), t = walk.graphIndex.get(b.nodeId());
                List<Integer> path = path(s,t);
                if (path.isEmpty()) continue;
                double length = a.length()+b.length();
                for (int i = 1; i < path.size(); i++) length += walk.graph.get(path.get(i-1)).edges.get(walk.graph.get(path.get(i)).id);
                if (length < best) { best = length; bestPath = path; entry = a; exit = b; }
            }
        }
        Route route;
        if (entry == null) {
            route = unavailable("OSM에 연결된 보행 경로가 없습니다.");
        } else {
            List<GeoPoint> points = new ArrayList<>();
            points.add(entry.door());
            points.add(walk.graph.get(bestPath.get(0)).point);
            for (int i = 1; i < bestPath.size(); i++) {
                List<GeoPoint> leg = walk.geometry(bestPath.get(i-1),bestPath.get(i));
                if (leg != null) points.addAll(leg.subList(1,leg.size()));
            }
            points.add(exit.door());
            String note = entry.estimated() || exit.estimated()
                    ? "OSM 보행 경로 · 건물과 길 사이의 짧은 연결 구간은 추정"
                    : "OSM 보행 경로 · 실내 이동 시간 별도";
            route = new Route(points,best,true,note);
        }
        routeCache.put(key,route);
        return route;
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
