package campus.ui;

import campus.model.PlannerModel.Occurrence;
import campus.timetable.TimePicker;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.HierarchyEvent;
import java.awt.geom.Arc2D;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.Timer;

/** A twelve-hour clock with schedule arcs on the outside. */
final class ScheduleClock extends JPanel {
    private static final double HALF_DAY = 43_200;
    private static final Color[] COLORS = {
            new Color(0x2665CB), new Color(0x8054A5), new Color(0x067565),
            new Color(0xA45B04), new Color(0xB64260), new Color(0x526785)
    };

    record Segment(double start, double end, int lane, Color color, Occurrence occurrence) { }

    private LocalTime time = LocalTime.MIDNIGHT;
    private List<Segment> segments = List.of();
    private final Timer hoverDelay = new Timer(150, e -> showScheduleTip());
    private Segment hovered;
    private Point pointer;
    private Popup scheduleTip;

    ScheduleClock() {
        setOpaque(false);
        setPreferredSize(new Dimension(240, 240));
        getAccessibleContext().setAccessibleName("아날로그 시계와 일정");
        hoverDelay.setRepeats(false);
        MouseAdapter hover = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { updateHover(e); }
            @Override public void mouseMoved(MouseEvent e) { updateHover(e); }
            @Override public void mouseExited(MouseEvent e) { clearHover(); }
            @Override public void mousePressed(MouseEvent e) { clearHover(); }
        };
        addMouseListener(hover);
        addMouseMotionListener(hover);
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) clearHover();
        });
    }

    void updateTime(LocalTime current) {
        if (time.toSecondOfDay() == current.toSecondOfDay()) return;
        time = current;
        repaint();
    }

    void showSchedule(List<Occurrence> occurrences, boolean afternoon) {
        List<Segment> updated = segments(occurrences, afternoon);
        if (segments.equals(updated)) return;
        clearHover();
        segments = updated;
        repaint();
    }

    static List<Segment> segments(List<Occurrence> occurrences, boolean afternoon) {
        double offset = afternoon ? HALF_DAY : 0;
        List<Segment> result = new ArrayList<>();
        List<Double> laneEnds = new ArrayList<>();
        int colorIndex = 0;
        for (Occurrence occurrence : occurrences.stream()
                .sorted(Comparator.comparing((Occurrence o) -> o.event().start())
                        .thenComparing(o -> o.event().id())).toList()) {
            if (occurrence.canceled()) continue;
            Color color = scheduleColor(colorIndex++);
            var event = occurrence.event();
            double start = Math.max(0, event.start().toSecondOfDay() - offset);
            double end = Math.min(HALF_DAY, (event.end().equals(LocalTime.MAX) ? 86_400
                    : event.end().toSecondOfDay()) - offset);
            if (start >= end) continue;
            int lane = 0;
            while (lane < laneEnds.size() && laneEnds.get(lane) > start) lane++;
            if (lane == laneEnds.size()) laneEnds.add(end);
            else laneEnds.set(lane, end);
            result.add(new Segment(start, end, lane, color, occurrence));
        }
        return List.copyOf(result);
    }

    private static Color scheduleColor(int index) {
        if (index < COLORS.length) return COLORS[index];
        return Color.getHSBColor((float) ((index * 0.61803398875) % 1), 0.8f, 0.62f);
    }

    private double scale() { return Math.min(getWidth(), getHeight()) / 240.0; }

    private double laneWidth() {
        int count = segments.stream().mapToInt(Segment::lane).max().orElse(0) + 1;
        return Math.min(7.0, 19.0 / count);
    }

    private double arcRadius(Segment segment) { return 112 - (segment.lane() + 0.5) * laneWidth(); }

    private Arc2D arc(Segment segment) {
        double radius = arcRadius(segment);
        return new Arc2D.Double(-radius, -radius, radius * 2, radius * 2,
                90 - segment.start() * 360 / HALF_DAY,
                -(segment.end() - segment.start()) * 360 / HALF_DAY, Arc2D.OPEN);
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(getWidth() / 2.0, getHeight() / 2.0);
            g.scale(scale(), scale());
            g.setColor(Theme.LINE);
            g.fillOval(-115, -115, 230, 230);
            g.setColor(Color.WHITE);
            g.fillOval(-91, -91, 182, 182);
            g.setStroke(new BasicStroke((float) (laneWidth() * 0.8), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (Segment segment : segments) {
                g.setColor(segment.color());
                g.draw(arc(segment));
            }
            g.setFont(Theme.font(14, Font.BOLD));
            g.setColor(Theme.INK);
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI / 6;
                String number = Integer.toString(i == 0 ? 12 : i);
                g.drawString(number, (float) (74 * Math.sin(angle) - g.getFontMetrics().stringWidth(number) / 2.0),
                        (float) (-74 * Math.cos(angle) + 5));
            }
            hand(g, time.toSecondOfDay() / HALF_DAY, 44, 3, Theme.INK);
            hand(g, (time.getMinute() * 60 + time.getSecond()) / 3600.0, 62, 2, Theme.INK);
            hand(g, time.getSecond() / 60.0, 68, 1, Theme.TODAY_TEXT);
            g.setColor(Theme.INK);
            g.fillOval(-3, -3, 6, 6);
        } finally {
            g.dispose();
        }
    }

    private static void hand(Graphics2D g, double revolutions, int length, int width, Color color) {
        double angle = revolutions * 2 * Math.PI;
        g.setColor(color);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(0, 0, (int) (length * Math.sin(angle)), (int) (-length * Math.cos(angle)));
    }

    private Segment segmentAt(Point point) {
        if (scale() == 0) return null;
        double x = (point.x - getWidth() / 2.0) / scale();
        double y = (point.y - getHeight() / 2.0) / scale();
        // Match the painted arcs, including their round ends. Prefer a visible stroke
        // over the hover margin so crowded lanes consistently identify the right event.
        for (double width : new double[] {laneWidth() * 0.8, laneWidth() + 2 / scale()}) {
            BasicStroke hit = new BasicStroke((float) width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            for (int i = segments.size() - 1; i >= 0; i--) {
                Segment segment = segments.get(i);
                if (hit.createStrokedShape(arc(segment)).contains(x, y)) return segment;
            }
        }
        return null;
    }

    @Override public String getToolTipText(MouseEvent e) {
        Segment segment = segmentAt(e.getPoint());
        return segment == null ? null : scheduleText(segment);
    }

    private static String scheduleText(Segment segment) {
        var event = segment.occurrence().event();
        return "<html>" + Theme.html(event.title()) + " · " + TimePicker.display(event.start())
                + "–" + TimePicker.display(event.end()) + "</html>";
    }

    private void updateHover(MouseEvent event) {
        Segment target = segmentAt(event.getPoint());
        if (target == null) {
            clearHover();
            return;
        }
        if (target == hovered) {
            pointer = event.getLocationOnScreen();
            return;
        }
        clearHover();
        hovered = target;
        pointer = event.getLocationOnScreen();
        hoverDelay.start();
    }

    private void clearHover() {
        hoverDelay.stop();
        hovered = null;
        if (scheduleTip != null) {
            scheduleTip.hide();
            scheduleTip = null;
        }
    }

    private void showScheduleTip() {
        if (hovered == null || !isShowing()) return;
        JToolTip tip = createToolTip();
        tip.setTipText(scheduleText(hovered));
        Dimension size = tip.getPreferredSize();
        GraphicsConfiguration screen = getGraphicsConfiguration();
        Rectangle bounds = new Rectangle(screen.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(screen);
        int right = bounds.x + bounds.width - insets.right;
        int bottom = bounds.y + bounds.height - insets.bottom;
        int x = Math.max(bounds.x + insets.left, Math.min(pointer.x + 12, right - size.width));
        int y = pointer.y + 16;
        if (y + size.height > bottom) y = pointer.y - size.height - 8;
        y = Math.max(bounds.y + insets.top, y);
        scheduleTip = PopupFactory.getSharedInstance().getPopup(this, tip, x, y);
        scheduleTip.show();
    }
}
