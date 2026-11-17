package campus;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.time.Duration;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.nio.file.Path;
import javax.swing.*;
import campus.MapData.*;
import campus.PlannerModel.Occurrence;

final class CampusMapPanel extends JPanel {
    static final double WALKING_METERS_PER_SECOND = 1.3;
    record Leg(Occurrence from, Occurrence to, Route route) {
        int minutes() {
            return (int) Math.ceil(route.meters() / (WALKING_METERS_PER_SECOND * 60));
        }
        long gapMinutes() {
            return Duration.between(from.event().end(), to.event().start()).toMinutes();
        }
    }

    private final MapData data;
    private final PlannerModel model;
    private final PlaceCatalog places;
    private final Consumer<Place> selection;
    private final Consumer<Place> activation;
    private final Consumer<GeoPoint> pinRequest;
    private final double longitudeScale;
    private final double centerLat;
    private final double centerLon;
    private final Map<String, Route> routeCache = new HashMap<>();
    private List<Occurrence> active = List.of();
    private List<Leg> legs = List.of();
    private Place searchFrom;
    private Place searchTo;
    private Route searchRoute;
    private double beforeSearchZoom, beforeSearchPanX, beforeSearchPanY;
    private String selectedId;
    private double zoom = 1;
    private double panX;
    private double panY;
    private Point dragOrigin;
    private boolean dragging;
    private Function<Place, JPopupMenu> placeMenu;
    private Map<String, Path> recentFiles = Map.of();
    private Consumer<Place> recentOpen = place -> { };
    private Consumer<Occurrence> scheduleOpen;

    void setPlaceMenu(Function<Place, JPopupMenu> menu) { placeMenu = menu; }
    void setScheduleOpen(Consumer<Occurrence> open) { scheduleOpen = open; }
    void setRecentFiles(Map<String, Path> files, Consumer<Place> open) {
        recentFiles = Map.copyOf(files); recentOpen = open; repaint();
    }
    Place placeAt(Point point) { return hit(point); }

    CampusMapPanel(MapData data, PlannerModel model, PlaceCatalog places,
                   Consumer<Place> selection, Consumer<Place> activation, Consumer<GeoPoint> pinRequest) {
        this.data = data;
        this.model = model;
        this.places = places;
        this.selection = selection;
        this.activation = activation;
        this.pinRequest = pinRequest;
        centerLat = (data.minLat() + data.maxLat()) / 2;
        centerLon = (data.minLon() + data.maxLon()) / 2;
        longitudeScale = Math.cos(Math.toRadians(centerLat));
        setBackground(new Color(246, 249, 248));
        setPreferredSize(new Dimension(600, 580));
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragOrigin = SwingUtilities.isLeftMouseButton(e) ? e.getPoint() : null;
                dragging = false;
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragOrigin == null) return;
                double dx = e.getX() - dragOrigin.x;
                double dy = e.getY() - dragOrigin.y;
                if (Math.abs(dx) + Math.abs(dy) > 2) dragging = true;
                panX += dx;
                panY += dy;
                dragOrigin = e.getPoint();
                repaint();
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    Place place = hit(e.getPoint());
                    if (placeMenu != null && place != null) placeMenu.apply(place).show(CampusMapPanel.this, e.getX(), e.getY());
                    else pinRequest.accept(geoPoint(e.getPoint()));
                    dragOrigin = null;
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                if (!dragging) {
                    List<Occurrence> scheduled = scheduleOpen == null ? List.of() : scheduledAt(e.getPoint());
                    if (!scheduled.isEmpty()) {
                        if (e.getClickCount() == 1) openScheduled(scheduled, e.getPoint());
                        dragOrigin = null;
                        return;
                    }
                    Place recent = recentAt(e.getPoint());
                    if (recent != null) {
                        if (e.getClickCount() == 1) recentOpen.accept(recent);
                        dragOrigin = null;
                        return;
                    }
                    Place hit = hit(e.getPoint());
                    if (hit != null) {
                        if (e.getClickCount() == 2) activation.accept(hit);
                        else selection.accept(hit);
                    }
                }
                dragOrigin = null;
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                zoomAt(Math.pow(1.16, -e.getPreciseWheelRotation()), e.getPoint());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    void setOccurrences(List<Occurrence> occurrences) {
        active = occurrences.stream().filter(o -> !o.canceled()).toList();
        List<Leg> updated = new ArrayList<>();
        for (int i = 1; i < active.size(); i++) {
            Occurrence before = active.get(i - 1), after = active.get(i);
            Place from = places.find(before.event().buildingId());
            Place to = places.find(after.event().buildingId());
            if (from == null || to == null) {
                updated.add(new Leg(before, after, new Route(List.of(), 0, false, "OSM에서 장소를 찾을 수 없습니다")));
            } else {
                String key = from.id() + from.point() + ">" + to.id() + to.point();
                Route route = routeCache.computeIfAbsent(key, ignored -> places.route(from, to));
                updated.add(new Leg(before, after, route));
            }
        }
        legs = List.copyOf(updated);
        repaint();
    }

    List<Leg> legs() { return legs; }

    void setSearchRoute(Place from, Place to, Route route) {
        if (!isSearchRouteActive()) {
            beforeSearchZoom = zoom;
            beforeSearchPanX = panX;
            beforeSearchPanY = panY;
        }
        searchFrom = Objects.requireNonNull(from);
        searchTo = Objects.requireNonNull(to);
        searchRoute = Objects.requireNonNull(route);
        fit();
    }

    void clearSearchRoute() {
        if (isSearchRouteActive()) {
            zoom = beforeSearchZoom;
            panX = beforeSearchPanX;
            panY = beforeSearchPanY;
        }
        searchFrom = null;
        searchTo = null;
        searchRoute = null;
        repaint();
    }

    boolean isSearchRouteActive() {
        return searchRoute != null;
    }

    void select(String id) {
        selectedId = id;
        Place selected = places.find(id);
        if (selected != null && selected.busStop()) {
            Point2D location = point(selected.point());
            if (location.getX() < 20 || location.getX() > getWidth() - 20
                    || location.getY() < 20 || location.getY() > getHeight() - 20) {
                panX += getWidth() / 2.0 - location.getX();
                panY += getHeight() / 2.0 - location.getY();
            }
        }
        repaint();
    }

    void fit() {
        zoom = 1;
        panX = panY = 0;
        if (isSearchRouteActive()) {
            List<GeoPoint> points = new ArrayList<>(searchRoute.points());
            points.add(searchFrom.point());
            points.add(searchTo.point());
            double lowX = Double.POSITIVE_INFINITY, lowY = Double.POSITIVE_INFINITY;
            double highX = Double.NEGATIVE_INFINITY, highY = Double.NEGATIVE_INFINITY;
            for (GeoPoint location : points) {
                Point2D projected = point(location);
                lowX = Math.min(lowX, projected.getX()); highX = Math.max(highX, projected.getX());
                lowY = Math.min(lowY, projected.getY()); highY = Math.max(highY, projected.getY());
            }
            zoom = Math.max(0.35, Math.min(4, Math.min(Math.max(1, getWidth() - 100) / Math.max(80, highX - lowX),
                    Math.max(1, getHeight() - 110) / Math.max(80, highY - lowY))));
            panX = (getWidth() / 2.0 - (lowX + highX) / 2) * zoom;
            panY = (getHeight() / 2.0 - (lowY + highY) / 2) * zoom;
        }
        repaint();
    }

    void zoom(double factor) {
        zoomAt(factor, new Point(getWidth() / 2, getHeight() / 2));
    }

    private void zoomAt(double factor, Point anchor) {
        double next = Math.max(0.35, Math.min(zoom * factor, 8));
        double ratio = next / zoom;
        panX = anchor.x - getWidth()/2.0 - (anchor.x - getWidth()/2.0 - panX) * ratio;
        panY = anchor.y - getHeight()/2.0 - (anchor.y - getHeight()/2.0 - panY) * ratio;
        zoom = next;
        repaint();
    }

    private double scale() {
        double width = Math.max(0.00001, (data.maxLon() - data.minLon()) * longitudeScale);
        double height = Math.max(0.00001, data.maxLat() - data.minLat());
        return Math.min(Math.max(1, getWidth() - 70) / width, Math.max(1, getHeight() - 85) / height) * zoom;
    }

    private Point2D point(GeoPoint point) {
        double scale = scale();
        return new Point2D.Double(getWidth()/2.0 + panX + (point.lon() - centerLon) * longitudeScale * scale,
                getHeight()/2.0 + panY - (point.lat() - centerLat) * scale);
    }

    private GeoPoint geoPoint(Point point) {
        double scale = scale();
        return new GeoPoint(centerLat-(point.y-getHeight()/2.0-panY)/scale,
                centerLon+(point.x-getWidth()/2.0-panX)/(longitudeScale*scale));
    }

    private Path2D path(List<GeoPoint> points, boolean close) {
        Path2D path = new Path2D.Double();
        boolean first = true;
        for (GeoPoint point : points) {
            Point2D xy = point(point);
            if (first) path.moveTo(xy.getX(), xy.getY());
            else path.lineTo(xy.getX(), xy.getY());
            first = false;
        }
        if (close && !first) path.closePath();
        return path;
    }

    private Place hit(Point location) {
        for (Place place : places.all()) {
            if ((place.pin() || place.busStop()) && point(place.point()).distance(location) < 13) return place;
        }
        Place near = null;
        double distance = 15;
        for (Building building : data.buildings()) {
            if (path(building.outline(), true).contains(location)) return places.find(building.id());
            double candidate = point(building.center()).distance(location);
            if (candidate < distance) {
                near = places.find(building.id());
                distance = candidate;
            }
        }
        return near;
    }

    @Override public String getToolTipText(MouseEvent event) {
        List<Occurrence> scheduled = scheduleOpen == null ? List.of() : scheduledAt(event.getPoint());
        if (!scheduled.isEmpty()) return scheduled.stream().map(value -> value.event().title())
                .collect(java.util.stream.Collectors.joining(" · ")) + " · 클릭하여 자료함 열기";
        Place recent = recentAt(event.getPoint());
        if (recent != null) return recentFiles.get(recent.id()).getFileName() + " · 최근 파일 열기";
        Place place = hit(event.getPoint());
        if (place == null) return null;
        return place.named() ? place.name() + " · 더블클릭하여 자료함 열기" : "더블클릭하여 건물 이름 지정";
    }

    private Place recentAt(Point cursor) {
        for (String id : recentFiles.keySet()) {
            Place place = places.find(id);
            if (place != null) {
                Point2D xy = point(place.point());
                if (new Rectangle2D.Double(xy.getX() + 17, xy.getY() - 18, 27, 27).contains(cursor)) return place;
            }
        }
        return null;
    }

    private List<Occurrence> scheduledAt(Point cursor) {
        if (isSearchRouteActive()) return List.of();
        Map<String, List<Occurrence>> groups = new LinkedHashMap<>();
        active.forEach(value -> groups.computeIfAbsent(value.event().buildingId(), ignored -> new ArrayList<>()).add(value));
        FontMetrics metrics = getFontMetrics(Theme.font(12, Font.BOLD));
        for (Map.Entry<String, List<Occurrence>> entry : groups.entrySet()) {
            Place place = places.find(entry.getKey());
            if (place == null) continue;
            String numbers = entry.getValue().stream().map(value -> Integer.toString(active.indexOf(value) + 1))
                    .collect(java.util.stream.Collectors.joining("·"));
            Point2D xy = point(place.point());
            int width = Math.max(26, metrics.stringWidth(numbers) + 14);
            if (new Rectangle2D.Double(xy.getX() - width / 2.0 - 2, xy.getY() - 15, width + 4, 30).contains(cursor)) {
                return List.copyOf(entry.getValue());
            }
        }
        return List.of();
    }
    private void openScheduled(List<Occurrence> occurrences, Point at) {
        if (occurrences.size() == 1) { scheduleOpen.accept(occurrences.get(0)); return; }
        scheduleMenu(occurrences).show(this, at.x, at.y);
    }
    JPopupMenu scheduleMenu(List<Occurrence> occurrences) {
        JPopupMenu menu = new JPopupMenu();
        for (Occurrence occurrence : occurrences) {
            JMenuItem item = new JMenuItem((active.indexOf(occurrence) + 1) + ". " + occurrence.event().title());
            item.addActionListener(event -> scheduleOpen.accept(occurrence));
            menu.add(item);
        }
        return menu;
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (MapWay way : data.ways()) {
            Color fill = switch (way.kind()) {
                case "boundary" -> new Color(234, 241, 235);
                case "water" -> new Color(179, 217, 233);
                case "green" -> new Color(215, 232, 207);
                case "pitch" -> new Color(199, 220, 188);
                default -> null;
            };
            if (fill != null) {
                g.setColor(fill);
                g.fill(path(way.points(), true));
            }
        }
        for (MapWay way : data.ways()) {
            boolean road = way.kind().equals("road");
            if (!road && !way.kind().equals("footway") && !way.kind().equals("steps")) continue;
            Path2D geometry = path(way.points(), false);
            float width = (float) ((road ? 5 : 2.2) * Math.sqrt(zoom));
            g.setStroke(new BasicStroke(width + 1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(211, 219, 218));
            g.draw(geometry);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(geometry);
        }
        Set<String> onRoute = new HashSet<>();
        if (isSearchRouteActive()) {
            onRoute.add(searchFrom.id());
            onRoute.add(searchTo.id());
        } else {
            active.forEach(o -> onRoute.add(o.event().buildingId()));
        }
        for (Building building : data.buildings()) {
            boolean selected = building.id().equals(selectedId);
            boolean scheduled = onRoute.contains(building.id());
            Path2D shape = path(building.outline(), true);
            g.setColor(selected ? new Color(134, 174, 252) : scheduled ? new Color(174, 199, 250) : new Color(218, 225, 231));
            g.fill(shape);
            g.setColor(selected || scheduled ? Theme.BLUE : new Color(176, 188, 201));
            g.setStroke(new BasicStroke(selected ? 2 : 0.8f));
            g.draw(shape);
        }
        if (isSearchRouteActive()) {
            drawRoute(g, searchRoute);
        } else {
            for (Leg leg : legs) drawRoute(g, leg.route());
        }
        for (Place place : places.all()) {
            if (place.pin()) drawPin(g,place);
            else if (place.busStop()) drawBusStop(g, place);
        }
        List<Rectangle2D> occupied = new ArrayList<>();
        g.setFont(Theme.font(11, Font.PLAIN));
        for (Place place : places.all()) {
            if (!place.named()) continue;
            boolean important = onRoute.contains(place.id()) || place.id().equals(selectedId)
                    || model.isFavorite(place.id()) || place.pin() || !model.customName(place.id()).isBlank();
            if (!important && zoom < 1.5) continue;
            Point2D xy = point(place.point());
            String text = place.name();
            FontMetrics fm = g.getFontMetrics();
            int labelHeight = fm.getHeight() + 6;
            Rectangle2D box = new Rectangle2D.Double(xy.getX() - fm.stringWidth(text)/2.0 - 4, xy.getY() + 14, fm.stringWidth(text)+8, labelHeight);
            if (important) {
                for (int shift = 0; shift < 5 && occupied.stream().anyMatch(r -> r.intersects(box)); shift++) {
                    box.setRect(box.getX(), box.getY()+labelHeight, box.getWidth(), box.getHeight());
                }
            } else if (occupied.stream().anyMatch(r -> r.intersects(box))) continue;
            if (!box.intersects(0, 0, getWidth(), getHeight())) continue;
            occupied.add(box);
            g.setColor(new Color(255, 255, 255, 230));
            g.fillRoundRect((int)box.getX(), (int)box.getY(), (int)box.getWidth(), (int)box.getHeight(), 6, 6);
            g.setColor(important ? Theme.BLUE : Theme.INK);
            g.drawString(text, (float)box.getX()+4, (float)box.getY()+fm.getAscent()+3);
            if (model.isFavorite(place.id()) && !onRoute.contains(place.id())) {
                g.setColor(new Color(211, 148, 31));
                g.fill(new Ellipse2D.Double(xy.getX()-4, xy.getY()-4, 8, 8));
            }
        }
        Map<String, StringBuilder> numbers = new LinkedHashMap<>();
        if (isSearchRouteActive()) {
            numbers.put(searchFrom.id(), new StringBuilder("출발"));
            if (searchFrom.id().equals(searchTo.id())) numbers.get(searchFrom.id()).append("·도착");
            else numbers.put(searchTo.id(), new StringBuilder("도착"));
        } else {
            for (int i = 0; i < active.size(); i++) {
                StringBuilder number = numbers.computeIfAbsent(active.get(i).event().buildingId(), ignored -> new StringBuilder());
                if (!number.isEmpty()) number.append("·");
                number.append(i + 1);
            }
        }
        g.setFont(Theme.font(12, Font.BOLD));
        for (Map.Entry<String,StringBuilder> entry : numbers.entrySet()) {
            Place place = places.find(entry.getKey());
            if (place == null) continue;
            Point2D xy = point(place.point());
            String text = entry.getValue().toString();
            int width = Math.max(26, g.getFontMetrics().stringWidth(text) + 14);
            g.setColor(Color.WHITE);
            g.fillRoundRect((int)xy.getX()-width/2-2, (int)xy.getY()-15, width+4, 30, 24, 24);
            g.setColor(Theme.BLUE);
            g.fillRoundRect((int)xy.getX()-width/2, (int)xy.getY()-13, width, 26, 22, 22);
            g.setColor(Color.WHITE);
            g.drawString(text, (float)xy.getX()-g.getFontMetrics().stringWidth(text)/2f, (float)xy.getY()+5);
        }
        drawScale(g);
        for (Map.Entry<String, Path> entry : recentFiles.entrySet()) {
            Place place = places.find(entry.getKey());
            if (place == null) continue;
            Point2D xy = point(place.point());
            new DocumentIcon(FolderService.extension(entry.getValue()).toUpperCase(Locale.ROOT), false, 27)
                    .paintIcon(this, g, (int) xy.getX() + 17, (int) xy.getY() - 18);
        }
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(10, Font.PLAIN));
        g.drawString("© OpenStreetMap contributors · ODbL", 14, getHeight()-12);
        g.setFont(Theme.font(12, Font.BOLD));
        g.drawString("N ↑", getWidth()-42, 25);
        g.dispose();
    }

    private void drawRoute(Graphics2D g, Route route) {
        if (!route.reachable()) return;
        Path2D geometry = path(route.points(), false);
        g.setColor(new Color(255, 255, 255, 220));
        g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(geometry);
        g.setColor(Theme.BLUE);
        g.setStroke(new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(geometry);
    }

    private void drawPin(Graphics2D g, Place place) {
        Point2D point = point(place.point());
        double x = point.getX(), y = point.getY();
        g.setColor(place.id().equals(selectedId) ? Theme.BLUE : new Color(209,76,70));
        g.setStroke(new BasicStroke(2.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x,y-9,x,y));
        Path2D head = new Path2D.Double();
        head.moveTo(x-7,y-11);
        head.lineTo(x-4,y-14);
        head.lineTo(x-4,y-22);
        head.lineTo(x+4,y-22);
        head.lineTo(x+4,y-14);
        head.lineTo(x+7,y-11);
        head.closePath();
        g.fill(head);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1));
        g.draw(head);
    }

    private void drawBusStop(Graphics2D g, Place place) {
        Point2D point = point(place.point());
        int x = (int) point.getX(), y = (int) point.getY();
        g.setColor(place.id().equals(selectedId) ? Theme.BLUE : new Color(24, 116, 89));
        g.fillRoundRect(x - 9, y - 11, 18, 22, 5, 5);
        g.setColor(Color.WHITE);
        g.fillRoundRect(x - 5, y - 7, 10, 9, 2, 2);
        g.fillOval(x - 5, y + 5, 3, 3);
        g.fillOval(x + 2, y + 5, 3, 3);
    }

    private void drawScale(Graphics2D g) {
        double pixelsPerMeter = scale()/111_195;
        int meters = zoom >= 3 ? 50 : 100;
        int length = (int)(meters*pixelsPerMeter);
        int x = 17, y = getHeight()-44;
        g.setColor(Theme.MUTED);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(x,y,x+length,y);
        g.drawLine(x,y-4,x,y+3);
        g.drawLine(x+length,y-4,x+length,y+3);
        g.setFont(Theme.font(10,Font.PLAIN));
        g.drawString(meters+" m",x,y-7);
    }
}
