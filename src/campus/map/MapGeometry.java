package campus.map;

import campus.map.CampusMap.GeoPoint;
import java.awt.geom.Line2D;
import java.util.List;

/** 지도 좌표의 거리, 중심, 포함 여부를 계산합니다. 화면이나 OSM 태그는 다루지 않습니다. */
final class MapGeometry {
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private MapGeometry() { }

    static double distance(GeoPoint from, GeoPoint to) {
        double latitudeDifference = Math.toRadians(to.lat() - from.lat());
        double longitudeDifference = Math.toRadians(to.lon() - from.lon());
        double h = Math.sin(latitudeDifference / 2) * Math.sin(latitudeDifference / 2)
                + Math.cos(Math.toRadians(from.lat())) * Math.cos(Math.toRadians(to.lat()))
                * Math.sin(longitudeDifference / 2) * Math.sin(longitudeDifference / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    static boolean isClosedPolygon(List<GeoPoint> points) {
        return points.size() >= 4 && points.get(0).equals(points.get(points.size() - 1));
    }

    static boolean isValid(GeoPoint point) {
        return Double.isFinite(point.lat()) && Double.isFinite(point.lon())
                && Math.abs(point.lat()) <= 90 && Math.abs(point.lon()) <= 180;
    }

    /** 학교 경계의 크기를 비교하기 위한 좌표 면적입니다. 제곱미터 단위가 아닙니다. */
    static double area(List<GeoPoint> polygon) {
        GeoPoint origin = polygon.get(0);
        double area = 0;
        for (int i = 1; i < polygon.size(); i++) {
            GeoPoint a = polygon.get(i - 1);
            GeoPoint b = polygon.get(i);
            area += crossProduct(a, b, origin);
        }
        return Math.abs(area) / 2;
    }

    static GeoPoint center(List<GeoPoint> polygon) {
        GeoPoint origin = polygon.get(0);
        double x = 0;
        double y = 0;
        double area = 0;
        for (int i = 1; i < polygon.size(); i++) {
            GeoPoint a = polygon.get(i - 1);
            GeoPoint b = polygon.get(i);
            double cross = crossProduct(a, b, origin);
            area += cross;
            x += (a.lon() + b.lon() - 2 * origin.lon()) * cross;
            y += (a.lat() + b.lat() - 2 * origin.lat()) * cross;
        }
        if (Math.abs(area) < 1e-16) {
            double latitude = polygon.stream().mapToDouble(GeoPoint::lat).average().orElseThrow();
            double longitude = polygon.stream().mapToDouble(GeoPoint::lon).average().orElseThrow();
            return new GeoPoint(latitude, longitude);
        }
        return new GeoPoint(origin.lat() + y / (3 * area), origin.lon() + x / (3 * area));
    }

    private static double crossProduct(GeoPoint a, GeoPoint b, GeoPoint origin) {
        return (a.lon() - origin.lon()) * (b.lat() - origin.lat())
                - (b.lon() - origin.lon()) * (a.lat() - origin.lat());
    }

    static boolean contains(GeoPoint point, List<GeoPoint> polygon) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            GeoPoint a = polygon.get(i);
            GeoPoint b = polygon.get(j);
            boolean crossesLatitude = (a.lat() > point.lat()) != (b.lat() > point.lat());
            if (crossesLatitude) {
                double crossingLongitude = (b.lon() - a.lon()) * (point.lat() - a.lat())
                        / (b.lat() - a.lat()) + a.lon();
                if (point.lon() < crossingLongitude) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    static boolean intersectsPolygon(GeoPoint from, GeoPoint to, List<GeoPoint> polygon) {
        if (contains(from, polygon) || contains(to, polygon)) {
            return true;
        }
        for (int i = 1; i < polygon.size(); i++) {
            GeoPoint a = polygon.get(i - 1);
            GeoPoint b = polygon.get(i);
            if (Line2D.linesIntersect(from.lon(), from.lat(), to.lon(), to.lat(),
                    a.lon(), a.lat(), b.lon(), b.lat())) {
                return true;
            }
        }
        return false;
    }
}
