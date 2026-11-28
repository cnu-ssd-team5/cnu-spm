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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** 다익스트라로 두 장소 사이의 최단 보행 경로를 구하고, 조회 결과를 재사용합니다. */
public final class RouteService {
    public static final double WALKING_METERS_PER_SECOND = 1.3;

    public record Route(List<GeoPoint> points, double meters, boolean reachable, String message) {
        public Route {
            points = List.copyOf(points);
        }
    }

    private record QueueItem(int nodeIndex, double distance) { }
    private record ShortestPaths(double[] distances, int[] previousNode) { }
    private record RouteKey(String fromId, String toId) { }

    private final CampusMap map;
    private WalkGraph walk;
    private final Map<RouteKey, Route> routeCache = new HashMap<>();

    public RouteService(CampusMap map) throws IOException {
        this.map = map;
        walk = new WalkGraph(map);
    }

    public synchronized void refreshPlaces() throws IOException {
        walk = new WalkGraph(map);
        routeCache.clear();
    }

    public synchronized Route route(Building from, Building to) {
        if (from == null || to == null) {
            return unavailable("일정의 건물 정보가 없습니다.");
        }
        if (map.building(from.id()) == null || map.building(to.id()) == null) {
            return unavailable("현재 OSM에 없는 건물입니다.");
        }
        if (from.id().equals(to.id())) {
            return new Route(List.of(from.center()), 0, true, "같은 건물 · 실내 이동 시간 별도");
        }

        RouteKey key = new RouteKey(from.id(), to.id());
        Route cached = routeCache.get(key);
        if (cached != null) {
            return cached;
        }

        Route result = findRoute(from, to);
        routeCache.put(key, result);
        return result;
    }

    /** 출입구가 여러 개면 모든 출입구 조합 중 이동 거리가 가장 짧은 것을 고릅니다. */
    private Route findRoute(Building from, Building to) {
        List<Access> entries = walk.entrances(from.id());
        List<Access> exits = walk.entrances(to.id());
        if (entries.isEmpty() || exits.isEmpty()) {
            return unavailable("OSM에 연결된 보행 경로가 없습니다.");
        }
        Set<Integer> destinations = new HashSet<>();
        for (Access exit : exits) {
            destinations.add(walk.indexOf(exit.nodeId()));
        }
        double bestDistance = Double.POSITIVE_INFINITY;
        List<Integer> bestPath = List.of();
        Access entry = null;
        Access exit = null;

        for (Access fromAccess : entries) {
            // 같은 출발 출입구의 탐색 결과를 모든 도착 출입구가 함께 사용합니다.
            ShortestPaths paths = findShortestPaths(walk.indexOf(fromAccess.nodeId()), destinations);
            for (Access toAccess : exits) {
                int destination = walk.indexOf(toAccess.nodeId());
                if (!Double.isFinite(paths.distances()[destination])) {
                    continue;
                }
                List<Integer> path = restorePath(destination, paths.previousNode());
                double distance = pathLength(path, fromAccess, toAccess);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestPath = path;
                    entry = fromAccess;
                    exit = toAccess;
                }
            }
        }

        if (entry == null) {
            return unavailable("OSM에 연결된 보행 경로가 없습니다.");
        }
        String message = entry.estimated() || exit.estimated()
                ? "OSM 보행 경로 · 건물과 길 사이의 짧은 연결 구간은 추정"
                : "OSM 보행 경로 · 실내 이동 시간 별도";
        return new Route(routePoints(bestPath, entry, exit), bestDistance, true, message);
    }

    private double pathLength(List<Integer> path, Access entry, Access exit) {
        double length = entry.length() + exit.length();
        for (int i = 1; i < path.size(); i++) {
            length += walk.edgeLength(path.get(i - 1), path.get(i));
        }
        return length;
    }

    /** 압축 간선에 보관한 원래 좌표를 이어 지도에 그릴 경로를 만듭니다. */
    private List<GeoPoint> routePoints(List<Integer> path, Access entry, Access exit) {
        List<GeoPoint> points = new ArrayList<>();
        points.add(entry.door());
        points.add(walk.point(path.get(0)));
        for (int i = 1; i < path.size(); i++) {
            List<GeoPoint> segment = walk.geometry(path.get(i - 1), path.get(i));
            if (segment != null) {
                points.addAll(segment.subList(1, segment.size()));
            }
        }
        points.add(exit.door());
        return points;
    }

    private ShortestPaths findShortestPaths(int source, Set<Integer> destinations) {
        double[] distances = new double[walk.size()];
        Arrays.fill(distances, Double.POSITIVE_INFINITY);
        int[] previousNode = new int[walk.size()];
        Arrays.fill(previousNode, -1);

        PriorityQueue<QueueItem> queue = new PriorityQueue<>(Comparator.comparingDouble(QueueItem::distance));
        Set<Integer> remaining = new HashSet<>(destinations);
        distances[source] = 0;
        queue.add(new QueueItem(source, 0));

        while (!queue.isEmpty()) {
            QueueItem current = queue.remove();
            if (current.distance() > distances[current.nodeIndex()]) {
                continue;
            }
            remaining.remove(current.nodeIndex());
            if (remaining.isEmpty()) {
                break;
            }
            for (var edge : walk.edgesFrom(current.nodeIndex()).entrySet()) {
                int nextIndex = walk.indexOf(edge.getKey());
                double candidate = current.distance() + edge.getValue();
                if (candidate < distances[nextIndex]) {
                    distances[nextIndex] = candidate;
                    previousNode[nextIndex] = current.nodeIndex();
                    queue.add(new QueueItem(nextIndex, candidate));
                }
            }
        }

        return new ShortestPaths(distances, previousNode);
    }

    /** 도착지부터 직전 노드를 따라간 뒤, 출발지 순서로 뒤집습니다. */
    private List<Integer> restorePath(int destination, int[] previousNode) {
        List<Integer> path = new ArrayList<>();
        for (int node = destination; node != -1; node = previousNode[node]) {
            path.add(node);
        }
        Collections.reverse(path);
        return path;
    }

    private static Route unavailable(String message) {
        return new Route(List.of(), Double.NaN, false, message);
    }
}
