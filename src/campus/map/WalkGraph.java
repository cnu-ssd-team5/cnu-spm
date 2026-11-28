package campus.map;

import campus.map.CampusMap.Building;
import campus.map.CampusMap.GeoPoint;
import campus.map.CampusMap.Node;
import campus.map.CampusMap.Way;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 보행 도로를 그래프로 만들고, 건물 출입구를 연결한 뒤 중간 노드를 압축합니다. */
final class WalkGraph {
    private static final double MIN_ROAD_METERS = 0.01;
    private static final double ENTRANCE_CONNECTION_METERS = 55;
    private static final double BUILDING_CONNECTION_METERS = 75;
    private static final double EXACT_CONNECTION_METERS = 0.1;

    record Access(long nodeId, GeoPoint door, double length, boolean estimated) { }
    private record EdgeKey(int from, int to) { }

    private static final class Vertex {
        final long id;
        final GeoPoint point;
        final Map<Long, Double> edges = new LinkedHashMap<>();

        Vertex(long id, GeoPoint point) {
            this.id = id;
            this.point = point;
        }
    }

    private final CampusMap map;
    private final Map<Long, Vertex> rawGraph = new LinkedHashMap<>();
    private final Set<Long> connectedNodes = new HashSet<>();
    private final Map<EdgeKey, List<GeoPoint>> geometry = new HashMap<>();

    private final List<Vertex> graph = new ArrayList<>();
    private final Map<Long, Integer> graphIndex = new HashMap<>();
    private final Map<String, List<Access>> access = new HashMap<>();

    WalkGraph(CampusMap map) throws IOException {
        this.map = map;
        addWalkingRoads();
        findConnectedNodes();
        connectBuildings();
        compressGraph();
        assert hasAllEntrances() : "압축 과정에서 건물 출입구가 빠졌습니다.";
        // 조회에는 압축 그래프만 필요하므로 구성에 쓴 간선 자료는 남기지 않습니다.
        rawGraph.clear();
        connectedNodes.clear();
    }

    int size() { return graph.size(); }

    int indexOf(long nodeId) { return graphIndex.get(nodeId); }

    GeoPoint point(int index) { return graph.get(index).point; }

    List<Access> entrances(String placeId) { return access.getOrDefault(placeId, List.of()); }

    Map<Long, Double> edgesFrom(int index) {
        return Collections.unmodifiableMap(graph.get(index).edges);
    }

    double edgeLength(int from, int to) {
        return graph.get(from).edges.get(graph.get(to).id);
    }

    private boolean hasAllEntrances() {
        for (List<Access> entrances : access.values()) {
            for (Access entrance : entrances) {
                if (!graphIndex.containsKey(entrance.nodeId())) {
                    return false;
                }
            }
        }
        return true;
    }

    private void addWalkingRoads() throws IOException {
        for (Way way : map.osmWays()) {
            if (hasCompleteNodes(way) && OsmTags.isWalkableRoad(way.tags())) {
                addRoad(way);
            }
        }
        if (rawGraph.isEmpty()) {
            throw new IOException("캠퍼스 보행 도로가 없습니다.");
        }
    }

    private boolean hasCompleteNodes(Way way) {
        if (way.refs().size() < 2) {
            return false;
        }
        for (long nodeId : way.refs()) {
            if (map.node(nodeId) == null) {
                return false;
            }
        }
        return true;
    }

    private void findConnectedNodes() {
        for (Vertex vertex : rawGraph.values()) {
            if (!vertex.edges.isEmpty()) {
                connectedNodes.add(vertex.id);
            }
            connectedNodes.addAll(vertex.edges.keySet());
        }
    }

    private void connectBuildings() {
        for (Building building : map.buildings()) {
            access.put(building.id(), findAccess(building, map.buildingNodeIds(building.id())));
        }
    }

    List<GeoPoint> geometry(int from, int to) {
        return geometry.get(new EdgeKey(from, to));
    }

    private void addRoad(Way way) {
        for (int i = 1; i < way.refs().size(); i++) {
            Node from = map.node(way.refs().get(i - 1));
            Node to = map.node(way.refs().get(i));
            addRoadSegment(from, to, way.tags());
        }
    }

    private void addRoadSegment(Node from, Node to, Map<String, String> tags) {
        if (from == null || to == null || OsmTags.blocksWalking(from.tags()) || OsmTags.blocksWalking(to.tags())) {
            return;
        }
        GeoPoint middle = new GeoPoint((from.point().lat() + to.point().lat()) / 2,
                (from.point().lon() + to.point().lon()) / 2);
        if (!map.nearCampus(middle)) {
            return;
        }
        Vertex fromVertex = rawGraph.computeIfAbsent(from.id(), id -> new Vertex(id, from.point()));
        Vertex toVertex = rawGraph.computeIfAbsent(to.id(), id -> new Vertex(id, to.point()));
        double length = CampusMap.distance(from.point(), to.point());
        if (length < MIN_ROAD_METERS) {
            return;
        }
        if (OsmTags.allowsForwardWalking(tags)) {
            fromVertex.edges.merge(to.id(), length, Math::min);
        }
        if (OsmTags.allowsBackwardWalking(tags)) {
            toVertex.edges.merge(from.id(), length, Math::min);
        }
    }

    private List<Access> findAccess(Building building, List<Long> nodeIds) {
        List<Access> candidates = new ArrayList<>();
        for (long nodeId : nodeIds) {
            Node node = map.node(nodeId);
            if (node == null || !node.tags().containsKey("entrance")
                    || "no".equals(node.tags().get("entrance")) || OsmTags.blocksWalking(node.tags())) {
                continue;
            }
            if (connectedNodes.contains(nodeId)) {
                candidates.add(new Access(nodeId, node.point(), 0, false));
            } else {
                Access nearest = nearestAccess(building, List.of(node.point()), ENTRANCE_CONNECTION_METERS);
                if (nearest != null) {
                    candidates.add(nearest);
                }
            }
        }
        if (candidates.isEmpty()) {
            Access nearest = nearestAccess(building, building.outline(), BUILDING_CONNECTION_METERS);
            if (nearest != null) {
                candidates.add(nearest);
            }
        }
        return List.copyOf(candidates);
    }

    private Access nearestAccess(Building building, List<GeoPoint> doors, double maximum) {
        double bestDistance = maximum;
        Access result = null;
        for (GeoPoint door : doors) {
            for (Vertex vertex : rawGraph.values()) {
                if (!connectedNodes.contains(vertex.id)) {
                    continue;
                }
                double meters = CampusMap.distance(door, vertex.point);
                if (meters >= bestDistance || !connectorClear(door, vertex.point, building)) {
                    continue;
                }
                bestDistance = meters;
                result = new Access(vertex.id, door, meters, meters > EXACT_CONNECTION_METERS);
            }
        }
        return result;
    }

    private boolean connectorClear(GeoPoint from, GeoPoint to, Building target) {
        if (CampusMap.distance(from, to) < EXACT_CONNECTION_METERS) {
            return true;
        }
        if (crossesObstacle(from, to, target)) {
            return false;
        }
        for (int i = 1; i < 10; i++) {
            double fraction = i / 10.0;
            GeoPoint point = new GeoPoint(from.lat() + (to.lat() - from.lat()) * fraction,
                    from.lon() + (to.lon() - from.lon()) * fraction);
            if (insideObstacle(point, target)) {
                return false;
            }
        }
        return true;
    }

    private boolean crossesObstacle(GeoPoint from, GeoPoint to, Building target) {
        for (Building other : map.buildings()) {
            if (other != target && MapGeometry.intersectsPolygon(from, to, other.outline())) {
                return true;
            }
        }
        for (List<GeoPoint> water : map.waterObstacles()) {
            if (MapGeometry.intersectsPolygon(from, to, water)) {
                return true;
            }
        }
        return false;
    }

    private boolean insideObstacle(GeoPoint point, Building target) {
        if (MapGeometry.contains(point, target.outline())) {
            return true;
        }
        for (Building other : map.buildings()) {
            if (other != target && Math.abs(other.center().lat() - point.lat()) < 0.0015
                    && Math.abs(other.center().lon() - point.lon()) < 0.0018
                    && MapGeometry.contains(point, other.outline())) {
                return true;
            }
        }
        for (List<GeoPoint> water : map.waterObstacles()) {
            if (MapGeometry.contains(point, water)) {
                return true;
            }
        }
        return false;
    }

    private void compressGraph() {
        Map<Long, Set<Long>> neighbors = findUndirectedNeighbors();
        Set<Long> keptNodes = chooseNodesToKeep(neighbors);
        keepOneNodePerLoop(neighbors, keptNodes);
        createCompressedVertices(keptNodes);
        connectCompressedEdges(keptNodes);
    }

    private Map<Long, Set<Long>> findUndirectedNeighbors() {
        Map<Long, Set<Long>> neighbors = new HashMap<>();
        for (Vertex vertex : rawGraph.values()) {
            neighbors.computeIfAbsent(vertex.id, id -> new HashSet<>());
            for (long other : vertex.edges.keySet()) {
                neighbors.get(vertex.id).add(other);
                neighbors.computeIfAbsent(other, id -> new HashSet<>()).add(vertex.id);
            }
        }
        return neighbors;
    }

    private Set<Long> chooseNodesToKeep(Map<Long, Set<Long>> neighbors) {
        Set<Long> keptNodes = new HashSet<>();
        for (var entry : neighbors.entrySet()) {
            if (entry.getValue().size() != 2) {
                keptNodes.add(entry.getKey());
            }
        }
        for (List<Access> entrances : access.values()) {
            for (Access entrance : entrances) {
                keptNodes.add(entrance.nodeId());
            }
        }
        return keptNodes;
    }

    /** 갈림길이 없는 순환 도로도 노드 하나는 남겨야 통째로 사라지지 않습니다. */
    private void keepOneNodePerLoop(Map<Long, Set<Long>> neighbors, Set<Long> keptNodes) {
        Set<Long> visited = new HashSet<>();
        for (long start : neighbors.keySet()) {
            if (!visited.add(start)) {
                continue;
            }
            boolean hasKeptNode = keptNodes.contains(start);
            List<Long> component = new ArrayList<>(List.of(start));
            for (int i = 0; i < component.size(); i++) {
                for (long neighbor : neighbors.get(component.get(i))) {
                    if (visited.add(neighbor)) {
                        component.add(neighbor);
                        if (keptNodes.contains(neighbor)) {
                            hasKeptNode = true;
                        }
                    }
                }
            }
            if (!hasKeptNode) {
                keptNodes.add(start);
            }
        }
    }

    private void createCompressedVertices(Set<Long> keptNodes) {
        for (Vertex original : rawGraph.values()) {
            if (keptNodes.contains(original.id)) {
                graphIndex.put(original.id, graph.size());
                graph.add(new Vertex(original.id, original.point));
            }
        }
    }

    private void connectCompressedEdges(Set<Long> keptNodes) {
        for (Vertex start : graph) {
            for (long neighbor : rawGraph.get(start.id).edges.keySet()) {
                followRoad(start, neighbor, keptNodes);
            }
        }
    }

    private void followRoad(Vertex start, long neighbor, Set<Long> keptNodes) {
        List<GeoPoint> line = new ArrayList<>(List.of(start.point));
        long previous = start.id;
        long current = neighbor;
        double length = rawGraph.get(previous).edges.get(current);
        Set<Long> visited = new HashSet<>(List.of(start.id));
        while (visited.add(current)) {
            line.add(rawGraph.get(current).point);
            if (keptNodes.contains(current)) {
                saveCompressedEdge(start, current, length, line);
                break;
            }
            Long next = nextRoadNode(previous, current);
            if (next == null) {
                break;
            }
            length += rawGraph.get(current).edges.get(next);
            previous = current;
            current = next;
        }
    }

    private Long nextRoadNode(long previous, long current) {
        for (long candidate : rawGraph.get(current).edges.keySet()) {
            if (candidate != previous) {
                return candidate;
            }
        }
        return null;
    }

    private void saveCompressedEdge(Vertex start, long end, double length, List<GeoPoint> line) {
        Double oldLength = start.edges.get(end);
        if (oldLength == null || length < oldLength) {
            start.edges.put(end, length);
            EdgeKey key = new EdgeKey(graphIndex.get(start.id), graphIndex.get(end));
            geometry.put(key, List.copyOf(line));
        }
    }
}
