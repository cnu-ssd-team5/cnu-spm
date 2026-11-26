package campus.ui;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import campus.map.CampusMap;
import campus.map.CampusMap.*;
import campus.map.RouteService.Route;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Occurrence;
import campus.service.DayPlanService.Leg;

final class MapPanel extends JPanel {
    private final CampusMap data;
    private java.util.function.Function<Building,JPopupMenu> routeMenu;
    void setRouteMenu(java.util.function.Function<Building,JPopupMenu> menu){routeMenu=menu;}
    private Consumer<GeoPoint> pinCreator;
    private Consumer<Occurrence> occurrenceOpener;
    void setOccurrenceOpener(Consumer<Occurrence> action) { occurrenceOpener=action; }
    void setPinCreator(Consumer<GeoPoint> action) { pinCreator=action; }
    private GeoPoint geo(Point point) {
        return new GeoPoint(centerLat-(point.y-getHeight()/2.0-panY)/scale(),
                centerLon+(point.x-getWidth()/2.0-panX)/(longitudeScale*scale()));
    }
    private campus.folder.FolderModule folders;
    private final PlannerModel model;
    private final Consumer<Building> selection;
    private final double longitudeScale;
    private final double centerLat;
    private final double centerLon;
    private List<Occurrence> active = List.of();
    private List<Leg> legs = List.of();
    private Building searchFrom;
    private Building searchTo;
    private Route searchRoute;
    private boolean scheduleRoutesVisible = true;
    private String selectedId;
    private double zoom = 1;
    private double panX;
    private double panY;
    private Point dragOrigin;
    private boolean dragging;

    MapPanel(CampusMap data, PlannerModel model, Consumer<Building> selection) {
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
                showMenu(e);
                dragOrigin = SwingUtilities.isLeftMouseButton(e)?e.getPoint():null;
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
                showMenu(e);
                if (SwingUtilities.isLeftMouseButton(e) && !dragging) {
                    List<Occurrence> scheduled=scheduledAt(e.getPoint());
                    if(occurrenceOpener!=null&&!scheduled.isEmpty()) {
                        e.consume();dragOrigin=null;
                        if(e.getClickCount()==1)openScheduled(scheduled,e.getPoint());
                        return;
                    }
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

    private void showMenu(MouseEvent e) {
        if(!e.isPopupTrigger()||routeMenu==null)return;
        Building building=hit(e.getPoint());
        if(building!=null)routeMenu.apply(building).show(this,e.getX(),e.getY());
        else if(pinCreator!=null) {
            JPopupMenu menu=new JPopupMenu();JMenuItem add=new JMenuItem("핀 추가");
            GeoPoint point=geo(e.getPoint());add.addActionListener(action->pinCreator.accept(point));
            menu.add(add);menu.show(this,e.getX(),e.getY());
        }
    }

    List<Occurrence> scheduledAt(Point location) {
        if(isSearchRouteActive())return List.of();
        Map<String,List<Occurrence>> groups=new LinkedHashMap<>();
        active.forEach(o->groups.computeIfAbsent(o.event().buildingId(),id->new ArrayList<>()).add(o));
        List<Occurrence> hit=List.of();
        FontMetrics metrics=getFontMetrics(Theme.font(12,Font.BOLD));
        for(var entry:groups.entrySet()) {
            Building building=data.building(entry.getKey());if(building==null)continue;
            String numbers=entry.getValue().stream().map(o->Integer.toString(active.indexOf(o)+1))
                    .collect(java.util.stream.Collectors.joining("·"));
            int width=Math.max(26,metrics.stringWidth(numbers)+14);
            Point2D center=point(building.center());
            if(new Rectangle2D.Double((int)center.getX()-width/2,(int)center.getY()-13,width,26).contains(location))hit=entry.getValue();
        }
        return List.copyOf(hit);
    }

    private void openScheduled(List<Occurrence> scheduled,Point point) {
        if(scheduled.size()==1) { occurrenceOpener.accept(scheduled.get(0));return; }
        JPopupMenu menu=new JPopupMenu();
        for(Occurrence item:scheduled) {
            JMenuItem choice=new JMenuItem((active.indexOf(item)+1)+". "+item.event().title()+" · "+
                    campus.timetable.TimePicker.display(item.event().start()));
            choice.addActionListener(e->occurrenceOpener.accept(item));menu.add(choice);
        }
        menu.show(this,point.x,point.y);
    }

    void setOccurrences(List<Occurrence> occurrences, List<Leg> legs) {
        active = occurrences.stream().filter(o -> !o.canceled()).toList();
        this.legs = legs;
        repaint();
    }

    List<Leg> legs() { return legs; }
    void connectFolders(campus.folder.FolderModule folders) {
        this.folders=folders;
        folders.attach(this, this::hit, this::point);
    }


    void setSearchRoute(Building from, Building to, Route route) {
        searchFrom = Objects.requireNonNull(from);
        searchTo = Objects.requireNonNull(to);
        searchRoute = Objects.requireNonNull(route);
        repaint();
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

    /** 수업 사이 도보 경로 선만 숨긴다. 일정 번호와 최단거리 찾기 결과는 그대로 표시한다. */
    void setScheduleRoutesVisible(boolean visible) {
        scheduleRoutesVisible = visible;
        repaint();
    }

    boolean scheduleRoutesVisible() {
        return scheduleRoutesVisible;
    }

    void select(String id) {
        selectedId = id;
        Building building=data.building(id);
        if(building!=null) {
            Point2D p=point(building.center());
            if(p.getX()<20||p.getX()>getWidth()-20||p.getY()<20||p.getY()>getHeight()-20) {
                panX+=getWidth()/2.0-p.getX();panY+=getHeight()/2.0-p.getY();
            }
        }
        repaint();
    }

    void zoom(double factor) {
        zoomAt(factor, new Point(getWidth() / 2, getHeight() / 2));
    }

    private void zoomAt(double factor, Point anchor) {
        double next = Math.max(0.25, Math.min(zoom * factor, 8));
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
        // 점 장소가 건물 외곽 안에 놓여 있어도 먼저 선택할 수 있어야 한다.
        Building pointPlace=data.buildings().stream().filter(b->b.outline().size()==1)
                .filter(b->point(b.center()).distance(location)<12)
                .min(Comparator.comparingDouble(b->point(b.center()).distance(location))).orElse(null);
        if(pointPlace!=null)return pointPlace;
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
        List<Occurrence> scheduled=scheduledAt(event.getPoint());
        if(!scheduled.isEmpty())return (occurrenceOpener == null ? "" : "자료함 열기 · ")+scheduled.stream().map(o->o.event().title()).collect(java.util.stream.Collectors.joining(" / "));
        Building building = hit(event.getPoint());
        return building==null?null:folders==null?building.name():folders.folderTooltip(building);
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
            if(building.outline().size()==1) {
                Point2D xy=point(building.center());
                g.setColor(selected?Theme.BLUE:building.id().startsWith("pin:")?new Color(194,85,65):new Color(48,145,100));
                g.fill(new Ellipse2D.Double(xy.getX()-6,xy.getY()-6,12,12));
                g.setColor(Color.WHITE);g.draw(new Ellipse2D.Double(xy.getX()-6,xy.getY()-6,12,12));continue;
            }
            Path2D shape = path(building.outline(), true);
            g.setColor(selected ? new Color(134, 174, 252) : scheduled ? new Color(174, 199, 250) : new Color(218, 225, 231));
            g.fill(shape);
            g.setColor(selected || scheduled ? Theme.BLUE : new Color(176, 188, 201));
            g.setStroke(new BasicStroke(selected ? 2 : 0.8f));
            g.draw(shape);
        }
        if (isSearchRouteActive()) {
            drawRoute(g, searchRoute);
        } else if (scheduleRoutesVisible) {
            for (Leg leg : legs) drawRoute(g, leg.route());
        }
        List<Rectangle2D> occupied = new ArrayList<>();
        g.setFont(Theme.font(11, Font.PLAIN));
        for (Building building : data.buildings()) {
            boolean important = onRoute.contains(building.id()) || building.id().equals(selectedId) || model.isFavorite(building.id());
            if (!important && (zoom < 1.8 || building.name().replace(" ","").startsWith("이름없는"))) continue;
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
            // 배경 박스 대신 글자에 얇은 흰 테두리를 둘러 지도 위에서도 읽히게 한다.
            Shape outline = g.getFont().createGlyphVector(g.getFontRenderContext(), text)
                    .getOutline((float)box.getX()+4, (float)box.getY()+14);
            Stroke stroke = g.getStroke();
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 255, 255, 220));
            g.draw(outline);
            g.setStroke(stroke);
            g.setColor(important ? Theme.BLUE : Theme.INK);
            g.fill(outline);
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
