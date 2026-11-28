package campus.map;

import campus.map.CampusMap.GeoPoint;
import campus.map.CampusMap.Node;
import campus.map.CampusMap.Way;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** OSM XML의 node, way, relation 태그를 Java 객체로 읽습니다. */
final class OsmReader extends DefaultHandler {
    record Member(long ref, String role) { }
    record Relation(long id, List<Member> members, Map<String, String> tags) { }
    record Bounds(double minLat, double minLon, double maxLat, double maxLon) {
        Bounds {
            if (!MapGeometry.isValid(new GeoPoint(minLat, minLon))
                    || !MapGeometry.isValid(new GeoPoint(maxLat, maxLon))
                    || minLat > maxLat || minLon > maxLon) {
                throw new IllegalArgumentException("OSM 추출 범위가 올바르지 않습니다.");
            }
        }

        boolean contains(GeoPoint point) {
            return point.lat() >= minLat && point.lon() >= minLon
                    && point.lat() <= maxLat && point.lon() <= maxLon;
        }
    }
    record Data(Map<Long, Node> nodes, Map<Long, Way> ways, List<Relation> relations, Bounds bounds) { }

    private final Map<Long, Node> nodes = new LinkedHashMap<>();
    private final Map<Long, Way> ways = new LinkedHashMap<>();
    private final List<Relation> relations = new ArrayList<>();
    private Bounds bounds;

    private String currentType;
    private long currentId;
    private GeoPoint currentPoint;
    private Map<String, String> currentTags;
    private List<Long> currentRefs;
    private List<Member> currentMembers;

    static Data read(InputStream input) throws IOException {
        OsmReader reader = new OsmReader();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.newSAXParser().parse(input, reader);
        } catch (Exception error) {
            throw new IOException("OSM XML을 읽을 수 없습니다.", error);
        }
        return new Data(Collections.unmodifiableMap(reader.nodes), Collections.unmodifiableMap(reader.ways),
                List.copyOf(reader.relations), reader.bounds);
    }

    @Override
    public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
        try {
            switch (name) {
                case "bounds" -> readBounds(attributes);
                case "node", "way", "relation" -> startObject(name, attributes);
                case "tag" -> readTag(attributes);
                case "nd" -> readNodeReference(attributes);
                case "member" -> readMember(attributes);
            }
        } catch (RuntimeException error) {
            throw new SAXException("잘못된 OSM 요소", error);
        }
    }

    private void readBounds(Attributes attributes) {
        bounds = new Bounds(
                Double.parseDouble(attributes.getValue("minlat")),
                Double.parseDouble(attributes.getValue("minlon")),
                Double.parseDouble(attributes.getValue("maxlat")),
                Double.parseDouble(attributes.getValue("maxlon"))
        );
    }

    private void readTag(Attributes attributes) {
        if (currentTags != null) {
            currentTags.put(attributes.getValue("k"), attributes.getValue("v"));
        }
    }

    private void readNodeReference(Attributes attributes) {
        if ("way".equals(currentType)) {
            currentRefs.add(Long.parseLong(attributes.getValue("ref")));
        }
    }

    private void readMember(Attributes attributes) {
        if ("relation".equals(currentType) && "way".equals(attributes.getValue("type"))) {
            long ref = Long.parseLong(attributes.getValue("ref"));
            currentMembers.add(new Member(ref, attributes.getValue("role")));
        }
    }

    private void startObject(String type, Attributes attributes) {
        currentType = type;
        currentId = Long.parseLong(attributes.getValue("id"));
        currentTags = new HashMap<>();
        switch (type) {
            case "node" -> {
                currentPoint = new GeoPoint(Double.parseDouble(attributes.getValue("lat")),
                        Double.parseDouble(attributes.getValue("lon")));
                if (!MapGeometry.isValid(currentPoint)) {
                    throw new IllegalArgumentException("OSM 노드 좌표가 올바르지 않습니다.");
                }
            }
            case "way" -> currentRefs = new ArrayList<>();
            case "relation" -> currentMembers = new ArrayList<>();
        }
    }

    @Override
    public void endElement(String uri, String localName, String name) {
        if (!name.equals(currentType)) {
            return;
        }
        switch (name) {
            case "node" -> nodes.put(currentId, new Node(currentId, currentPoint, currentTags));
            case "way" -> ways.put(currentId, new Way(currentId, currentRefs, currentTags));
            case "relation" -> relations.add(new Relation(currentId, currentMembers, currentTags));
        }
        currentType = null;
        currentTags = null;
        currentPoint = null;
        currentRefs = null;
        currentMembers = null;
    }
}
