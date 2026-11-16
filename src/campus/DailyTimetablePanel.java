package campus;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import campus.PlannerModel.Event;
import campus.PlannerModel.Occurrence;

final class DailyTimetablePanel extends JPanel {
    private static final int HOUR_HEIGHT = 54;
    private static final int TIME_COLUMN = 57;
    private static final int TOP = 12;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private final PlannerModel model;
    private final PlaceCatalog places;
    private final Consumer<Event> onEditEvent;
    private final Consumer<LocalTime> onAddEvent;
    private final JLabel dateLabel = Theme.heading(" ", 16);
    private final TimeGrid grid = new TimeGrid();
    private final JScrollPane scroll;
    private LocalDate selectedDate;
    private List<Block> blocks = List.of();

    private record Block(Event event, int lane, int lanes) { }

    DailyTimetablePanel(PlannerModel model, PlaceCatalog places, Consumer<Event> onEditEvent,
                        Consumer<LocalTime> onAddEvent) {
        super(new BorderLayout(0, 10));
        this.model = model;
        this.places = places;
        this.onEditEvent = onEditEvent;
        this.onAddEvent = onAddEvent;
        setOpaque(false);
        JPanel heading = Theme.transparent(new BorderLayout());
        heading.add(dateLabel, BorderLayout.WEST);
        heading.add(Theme.button("+ 일정", true, () -> onAddEvent.accept(LocalTime.of(9, 0))), BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);
        scroll = Theme.scroll(grid);
        add(scroll, BorderLayout.CENTER);
    }

    void refresh(LocalDate date) {
        boolean changed = !date.equals(selectedDate);
        selectedDate = date;
        dateLabel.setText(date.format(DateTimeFormatter.ofPattern("M월 d일")));
        List<Event> events = model.occurrences(date).stream().filter(o -> !o.canceled()).map(Occurrence::event).toList();
        blocks = assignLanes(events);
        grid.revalidate();
        grid.repaint();
        if (changed) {
            int firstMinute = events.isEmpty() ? 8 * 60 : Math.max(0, minute(events.get(0).start()) - 60);
            SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(yForMinute(firstMinute)));
        }
    }

    private static List<Block> assignLanes(List<Event> events) {
        List<Block> result = new ArrayList<>();
        int clusterStart = 0;
        while (clusterStart < events.size()) {
            int clusterEnd = clusterStart + 1;
            LocalTime occupiedUntil = events.get(clusterStart).end();
            while (clusterEnd < events.size() && events.get(clusterEnd).start().isBefore(occupiedUntil)) {
                if (events.get(clusterEnd).end().isAfter(occupiedUntil)) occupiedUntil = events.get(clusterEnd).end();
                clusterEnd++;
            }
            List<LocalTime> laneEnds = new ArrayList<>();
            List<Integer> lanes = new ArrayList<>();
            for (int index = clusterStart; index < clusterEnd; index++) {
                Event event = events.get(index);
                int lane = 0;
                while (lane < laneEnds.size() && laneEnds.get(lane).isAfter(event.start())) lane++;
                if (lane == laneEnds.size()) laneEnds.add(event.end());
                else laneEnds.set(lane, event.end());
                lanes.add(lane);
            }
            for (int index = clusterStart; index < clusterEnd; index++) {
                result.add(new Block(events.get(index), lanes.get(index - clusterStart), laneEnds.size()));
            }
            clusterStart = clusterEnd;
        }
        return List.copyOf(result);
    }

    private static int minute(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static int yForMinute(int minute) {
        return TOP + minute * HOUR_HEIGHT / 60;
    }

    private Rectangle bounds(Block block) {
        int available = Math.max(1, grid.getWidth() - TIME_COLUMN - 9);
        int laneWidth = Math.max(1, available / block.lanes());
        int top = yForMinute(minute(block.event().start()));
        int bottom = yForMinute(minute(block.event().end()));
        return new Rectangle(TIME_COLUMN + block.lane() * laneWidth + 2, top + 1,
                Math.max(1, laneWidth - 5), Math.max(3, bottom - top - 2));
    }

    private Block hit(Point point) {
        for (Block block : blocks) if (bounds(block).contains(point)) return block;
        return null;
    }

    private final class TimeGrid extends JPanel {
        TimeGrid() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(400, 24 * HOUR_HEIGHT + TOP + 18));
            ToolTipManager.sharedInstance().registerComponent(this);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    Block block = hit(e.getPoint());
                    if (block != null) {
                        onEditEvent.accept(block.event());
                    } else if (e.getX() >= TIME_COLUMN) {
                        int minutes = Math.max(0, Math.min(1439, (e.getY() - TOP) * 60 / HOUR_HEIGHT));
                        minutes = minutes / 30 * 30;
                        onAddEvent.accept(LocalTime.of(minutes / 60, minutes % 60));
                    }
                }
            });
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override public String getToolTipText(MouseEvent e) {
            Block block = hit(e.getPoint());
            if (block == null) return "눌러서 일정 추가";
            Place place = places.find(block.event().buildingId());
            return block.event().title() + " · " + block.event().start().format(CLOCK) + "–"
                    + block.event().end().format(CLOCK) + (place == null ? "" : " · " + place.name());
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(Theme.font(11, Font.PLAIN));
            for (int hour = 0; hour <= 24; hour++) {
                int y = yForMinute(hour * 60);
                g.setColor(Theme.LINE);
                g.drawLine(TIME_COLUMN, y, getWidth(), y);
                if (hour < 24) {
                    g.setColor(new Color(241, 244, 248));
                    g.drawLine(TIME_COLUMN, y + HOUR_HEIGHT / 2, getWidth(), y + HOUR_HEIGHT / 2);
                }
                g.setColor(Theme.MUTED);
                g.drawString(String.format("%02d:00", hour), 8, y + 5);
            }
            Color[] colors = {new Color(222, 234, 255), new Color(226, 239, 221),
                    new Color(250, 230, 216), new Color(238, 226, 250), new Color(219, 240, 241)};
            for (Block block : blocks) {
                Rectangle box = DailyTimetablePanel.this.bounds(block);
                g.setColor(colors[Math.floorMod(block.event().title().hashCode(), colors.length)]);
                g.fill(new RoundRectangle2D.Double(box.x, box.y, box.width, box.height, 9, 9));
                Graphics2D text = (Graphics2D) g.create();
                text.clipRect(box.x + 6, box.y + 2, Math.max(0, box.width - 12), Math.max(0, box.height - 4));
                text.setColor(Theme.INK);
                text.setFont(Theme.font(12, Font.BOLD));
                text.drawString(block.event().title(), box.x + 8, box.y + 18);
                text.setFont(Theme.font(11, Font.PLAIN));
                text.setColor(Theme.MUTED);
                Place place = places.find(block.event().buildingId());
                if (box.height >= 39 && place != null) text.drawString(place.name(), box.x + 8, box.y + 36);
                if (box.height >= 56) text.drawString(block.event().start().format(CLOCK) + " – " + block.event().end().format(CLOCK), box.x + 8, box.y + 53);
                text.dispose();
            }
            if (selectedDate != null && selectedDate.equals(LocalDate.now())) {
                int y = yForMinute(minute(LocalTime.now()));
                g.setColor(new Color(211, 75, 75));
                g.drawLine(TIME_COLUMN, y, getWidth(), y);
                g.fillOval(TIME_COLUMN - 3, y - 3, 6, 6);
            }
            g.dispose();
        }
    }
}
