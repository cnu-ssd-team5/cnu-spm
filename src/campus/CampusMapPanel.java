package campus;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.time.Duration;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
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
    private final Consumer<Building> selection;
    private final double longitudeScale;
    private final double centerLat;
    private final double centerLon;
    private final Map<String, Route> routeCache = new HashMap<>();
    private List<Occurrence> active = List.of();
    private List<Leg> legs = List.of();
    private Building searchFrom;
    private Building searchTo;
    private Route searchRoute;
    private String selectedId;
    private double zoom = 1;
    private double panX;
    private double panY;
    private Point dragOrigin;
    private boolean dragging;

    CampusMapPanel(MapData data, PlannerModel model, Consumer<Building> selection) {
        this.data = data;
        this.model = model;
        this.selection = selection;
        centerLat = (data.minLat() + data.maxLat()) / 2;
        centerLon = (data.minLon() + data.maxLon()) / 2;
        longitudeScale = Math.cos(Math.toRadians(centerLat));
        setBackground(new Color(246, 249, 248));
        setPreferredSize(new Dimension(600, 580));
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragOrigin = e.getPoint();
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
                if (!dragging) {
                    Building hit = hit(e.getPoint());
                    if (hit != null) selection.accept(hit);
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
            Building from = data.building(before.event().buildingId());
            Building to = data.building(after.event().buildingId());
            if (from == null || to == null) {
                updated.add(new Leg(before, after, new Route(List.of(), 0, false, "OSM에서 장소를 찾을 수 없습니다")));
            } else {
                String key = from.id() + ">" + to.id();
                Route route = routeCache.computeIfAbsent(key, ignored -> data.route(from, to));
                updated.add(new Leg(before, after, route));
            }
        }
        legs = List.copyOf(updated);
        repaint();
    }

    List<Leg> legs() { return legs; }

    void setSearchRoute(Building from, Building to, Route route) {
        searchFrom = Objects.requireNonNull(from);
        searchTo = Objects.requireNonNull(to);
        searchRoute = Objects.requireNonNull(route);
        fit();
    }

    void clearSearchRoute() {
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
        repaint();
    }

    void fit() {
        zoom = 1;
        panX = panY = 0;
        repaint();
    }

    void zoom(double factor) {
        zoomAt(factor, new Point(getWidth() / 2, getHeight() / 2));
    }

    private void zoomAt(double factor, Point anchor) {
        double next = Math.max(0.65, Math.min(zoom * factor, 8));
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

    private Building hit(Point location) {
        Building near = null;
        double distance = 15;
        for (Building building : data.buildings()) {
            if (path(building.outline(), true).contains(location)) return building;
            double candidate = point(building.center()).distance(location);
            if (candidate < distance) {
                near = building;
                distance = candidate;
            }
        }
        return near;
    }

    @Override public String getToolTipText(MouseEvent event) {
        Building building = hit(event.getPoint());
        return building == null ? null : building.name() + (model.isFavorite(building.id()) ? " · 즐겨찾기" : " · 클릭하여 선택");
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
        List<Rectangle2D> occupied = new ArrayList<>();
        g.setFont(Theme.font(11, Font.PLAIN));
        for (Building building : data.buildings()) {
            boolean important = onRoute.contains(building.id()) || building.id().equals(selectedId) || model.isFavorite(building.id());
            if (!important && (zoom < 1.8 || building.name().startsWith("이름 없는"))) continue;
            Point2D xy = point(building.center());
            String text = building.name();
            FontMetrics fm = g.getFontMetrics();
            Rectangle2D box = new Rectangle2D.Double(xy.getX() - fm.stringWidth(text)/2.0 - 4, xy.getY() + 14, fm.stringWidth(text)+8, 19);
            if (important) {
                for (int shift = 0; shift < 5 && occupied.stream().anyMatch(r -> r.intersects(box)); shift++) {
                    box.setRect(box.getX(), box.getY()+19, box.getWidth(), box.getHeight());
                }
            } else if (occupied.stream().anyMatch(r -> r.intersects(box))) continue;
            if (!box.intersects(0, 0, getWidth(), getHeight())) continue;
            occupied.add(box);
            g.setColor(new Color(255, 255, 255, 230));
            g.fillRoundRect((int)box.getX(), (int)box.getY(), (int)box.getWidth(), (int)box.getHeight(), 6, 6);
            g.setColor(important ? Theme.BLUE : Theme.INK);
            g.drawString(text, (float)box.getX()+4, (float)box.getY()+14);
            if (model.isFavorite(building.id()) && !onRoute.contains(building.id())) {
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
            Building building = data.building(entry.getKey());
            if (building == null) continue;
            Point2D xy = point(building.center());
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
