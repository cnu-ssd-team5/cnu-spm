package campus.timetable;

import campus.map.CampusMap;
import campus.model.PlannerModel.Event;
import campus.model.PlannerModel.Occurrence;
import campus.ui.Theme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.util.*;
import java.util.List;

/** 선택한 주의 반복/일회성 일정과 드래그로 선택한 시간 범위를 표시한다. */
final class WeekGrid extends JPanel implements Scrollable {
    interface RangeEditor { void open(Event event, DayOfWeek day, LocalTime start, LocalTime end); }
    record Selection(DayOfWeek day, LocalTime start, LocalTime end) { }
    private final TimetableService service;
    private CampusMap map;

    private final RangeEditor editor;
    private final Map<Rectangle,Event> boxes = new LinkedHashMap<>();
    private int first = 8, last = 24;
    private static final int LEFT = 56, TOP = 8, HEADER_HEIGHT = 38, HOUR = 72, START_SNAP = 30, END_SNAP = 5;
    private static final String[] DAY_LABELS = {"일", "월", "화", "수", "목", "금", "토"};
    private final JComponent dateHeader = new JComponent() {
        @Override public Dimension getPreferredSize() {
            return new Dimension(0, HEADER_HEIGHT);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(Theme.font(11, Font.PLAIN));
            g.setColor(new Color(245, 247, 251));
            g.fillRect(0, 0, getWidth(), getHeight());
            int width = columnWidth(getWidth());
            for (int column = 0; column < DAY_LABELS.length; column++) {
                LocalDate date = service.dateFor(dayOf(column));
                String label = DAY_LABELS[column] + " " + date.getMonthValue() + "/" + date.getDayOfMonth();
                int x = LEFT + column * width;
                Graphics2D cell = (Graphics2D) g.create(x, 0, width, getHeight());
                cell.setColor(new Color(45, 55, 75));
                cell.drawString(label, Math.max(2, (width - cell.getFontMetrics().stringWidth(label)) / 2), 24);
                cell.dispose();
            }
            drawColumnLines(g, width, getHeight());
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g.dispose();
        }
    };
    private Point anchor;
    private DayOfWeek dragDay;
    private boolean dragging, suppressClick;
    private Selection selection;

    WeekGrid(TimetableService service, CampusMap map, RangeEditor editor, java.util.function.BooleanSupplier save) {
        this.service=service; this.map=map; this.editor=editor;
        this.save = save;
        setBackground(Color.WHITE); setToolTipText(""); setFocusable(true); setAutoscrolls(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {

                if (suppressClick) { suppressClick=false; return; }
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount()==2) {
                    JPopupMenu menu=menuAt(e.getPoint());
                    if(menu!=null)((JMenuItem)menu.getComponent(0)).doClick();
                }
            }
            @Override public void mousePressed(MouseEvent e) {

                if (e.isPopupTrigger()) { popup(e); return; }
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                suppressClick=false;
                requestFocusInWindow();
                if (hit(e.getPoint())!=null || !inGrid(e.getPoint())) return;
                anchor=e.getPoint(); dragDay=dayAt(anchor); dragging=false;
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (anchor==null) return;
                if (!dragging && Math.abs(e.getY()-anchor.y)<4) return;
                dragging=true;
                selection=selectionBetween(dragDay, anchor.y, e.getY());
                scrollRectToVisible(new Rectangle(LEFT, Math.max(TOP, Math.min(e.getY(),y(LocalTime.MAX))), 1, 20));
                repaint();
            }
            @Override public void mouseReleased(MouseEvent e) {

                if (e.isPopupTrigger()) { cancelSelection(); popup(e); return; }
                if (!SwingUtilities.isLeftMouseButton(e) || anchor==null) return;
                Selection chosen=dragging?selectionBetween(dragDay,anchor.y,e.getY()):null;
                anchor=null; dragDay=null; dragging=false;
                if (chosen==null) { selection=null; repaint(); return; }
                selection=chosen; suppressClick=true; repaint();
                try { editor.open(null,chosen.day(),chosen.start(),chosen.end()); }
                finally { selection=null; repaint(); }
            }
            private void popup(MouseEvent e) {
                JPopupMenu menu=menuAt(e.getPoint());
                if(menu!=null)menu.show(WeekGrid.this,e.getX(),e.getY());
            }
        };
        addMouseListener(mouse); addMouseMotionListener(mouse);
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"),"cancelSelection");
        getActionMap().put("cancelSelection",new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { cancelSelection(); }
        });
        refresh();
    }

    private final java.util.function.BooleanSupplier save;

    boolean confirmDelete(Component owner, Event event) {
        String message = event.recurring() ? "이 반복 일정의 모든 요일을 삭제할까요?" : "이 일정을 삭제할까요?";
        if (JOptionPane.showConfirmDialog(owner, message, "일정 삭제", JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) return false;
        service.delete(event);
        save.getAsBoolean();
        refresh();
        return true;
    }
    private void cancelSelection() {
        anchor=null; dragDay=null; dragging=false; selection=null; repaint();
    }

    JScrollPane scrollPane() {
        JScrollPane scroll = Theme.scroll(this);
        scroll.setColumnHeaderView(dateHeader);
        return scroll;
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 24; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(24, visible.height - 24);
    }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
    private Event hit(Point point) {
        for(var box:boxes.entrySet()) if(box.getKey().contains(point)) return box.getValue();
        return null;
    }
    private boolean inGrid(Point point) {
        return point.x>=LEFT && point.x<LEFT+7*columnWidth(getWidth()) && point.y>=TOP && point.y<y(LocalTime.MAX);
    }
    private DayOfWeek dayAt(Point point) { return dayOf((point.x-LEFT)/columnWidth(getWidth())); }
    // 열 순서는 일~토. 0번 열이 일요일이다.
    private static DayOfWeek dayOf(int column) { return DayOfWeek.SUNDAY.plus(column); }
    private static int columnOf(DayOfWeek day) { return day.getValue()%7; }
    Selection selectionBetween(DayOfWeek day, int fromY, int toY) {
        double a=first*60+(fromY-TOP)*60.0/HOUR;
        double b=first*60+(toY-TOP)*60.0/HOUR;
        int start=Math.max(first*60,Math.min(1440-START_SNAP,(int)Math.floor(Math.min(a,b)/START_SNAP)*START_SNAP));
        int end=Math.max(start+END_SNAP,Math.min(1440,(int)Math.ceil(Math.max(a,b)/END_SNAP)*END_SNAP));
        return new Selection(day,LocalTime.of(start/60,start%60),end==1440?LocalTime.MAX:LocalTime.of(end/60,end%60));
    }
    JPopupMenu menuAt(Point point) {
        Event selected=hit(point);
        if(selected==null && !inGrid(point)) return null;
        DayOfWeek day=selected==null?dayAt(point):selected.recurring()?selected.weekday():selected.date().getDayOfWeek();
        int minute=first*60+(point.y-TOP)*60/HOUR;
        int startMinute=Math.max(0,Math.min(1440-START_SNAP,minute/START_SNAP*START_SNAP));
        LocalTime start=selected==null?LocalTime.of(startMinute/60,startMinute%60):selected.start();
        LocalTime end=selected==null?(startMinute+60>=1440?LocalTime.MAX:start.plusHours(1)):selected.end();
        JPopupMenu menu=new JPopupMenu();
        JMenuItem item=new JMenuItem(selected==null?"일정 추가":"일정 수정");
        item.addActionListener(e->editor.open(selected,day,start,end));
        menu.add(item);
        if (selected != null) {
            menu.add(Theme.deleteMenuItem("일정 삭제", () -> confirmDelete(this, selected)));
        }
        return menu;
    }
    void setScheduleContext(CampusMap map) {
        this.map = map;
    }

    void refresh() { refresh(8); }

    void refresh(int earliestHour) {
        first = Math.min(earliestHour, service.visibleEvents().stream().mapToInt(e -> e.start().getHour()).min().orElse(8));
        last=24;
        boxes.clear();
        cancelSelection();
        setPreferredSize(new Dimension(480,TOP+(last-first)*HOUR+12)); revalidate(); repaint();
        dateHeader.repaint();
    }
    static List<String> wrap(FontMetrics fm,String value,int width,int maxLines) {
        List<String> lines=new ArrayList<>(); String rest=value.strip();
        while(!rest.isEmpty()&&lines.size()<maxLines) {
            if(lines.size()==maxLines-1||fm.stringWidth(rest)<=width) { lines.add(fit(fm,rest,width)); break; }
            int end=1; while(end<rest.length()&&fm.stringWidth(rest.substring(0,end+1))<=width)end++;
            int space=rest.lastIndexOf(' ',end);
            if(rest.charAt(end)!=' '&&space>0)end=space; // 가능하면 띄어쓰기에서 줄을 나눈다.
            lines.add(rest.substring(0,end).strip()); rest=rest.substring(end).strip();
        }
        return lines;
    }
    static String fit(FontMetrics fm,String value,int width) {
        if(fm.stringWidth(value)<=width)return value;
        for(int end=value.length()-1;end>0;end--) {
            String cut=value.substring(0,end).strip()+"…";
            if(fm.stringWidth(cut)<=width)return cut;
        }
        return "…";
    }
    private static void drawTime(Graphics2D graphics, String label, int x, int baseline, int availableWidth) {
        Font original = graphics.getFont();
        int width = graphics.getFontMetrics().stringWidth(label);
        if (width > availableWidth && availableWidth > 0) {
            float size = Math.max(8f, original.getSize2D() * availableWidth / width);
            graphics.setFont(original.deriveFont(size));
        }
        graphics.drawString(fit(graphics.getFontMetrics(), label, availableWidth), x, baseline);
        graphics.setFont(original);
    }

    private static int columnWidth(int width) { return Math.max(1,(width-LEFT)/7); }
    private static void drawColumnLines(Graphics2D g, int width, int height) {
        g.setColor(new Color(225,230,237));
        for (int column = 0; column <= 7; column++) {
            int x = LEFT + column * width;
            g.drawLine(x, 0, x, height);
        }
    }
    private int y(LocalTime time) { return TOP+((time.equals(LocalTime.MAX)?1440:time.toSecondOfDay()/60)-first*60)*HOUR/60; }

    private String placeName(Event event) {
        if (!event.customPlace().isEmpty()) return event.customPlace();
        var building = map.building(event.buildingId());
        return building == null ? "" : building.name();
    }

    @Override public String getToolTipText(MouseEvent e) {
        Event v=hit(e.getPoint());
        if(v==null)return "빈 시간대를 위아래로 드래그하여 일정을 추가하세요 (시작 30분·종료 5분 단위, Esc로 취소)";
        String place = placeName(v);
        String details = v.title()+" · "+TimePicker.display(v.start())+"–"+TimePicker.display(v.end())+" · "
                +(place.isEmpty()?"장소 없음":place)+(v.recurring()?"":" · "+v.date());
        return "<html>" + Theme.html(details) + (v.note().isBlank() ? "" : "<br>" + Theme.html(v.note().strip()).replaceAll("\\R", "<br>")) + "</html>";
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics); boxes.clear();
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        int width=columnWidth(getWidth());
        for(int hour=first;hour<=last;hour++) {
            int row=TOP+(hour-first)*HOUR;
            g.setColor(new Color(100,110,125)); g.drawString(String.format("%02d:00",hour),5,row+5);
            g.setColor(new Color(220,225,233)); g.drawLine(LEFT,row,getWidth(),row);
            g.setColor(new Color(240,242,246)); g.drawLine(LEFT,row+HOUR/2,getWidth(),row+HOUR/2);
        }
        drawColumnLines(g, width, getHeight());
        for(int d=0;d<7;d++) {
            List<Occurrence> occurrences=service.occurrences(dayOf(d));
            List<LocalTime> ends=new ArrayList<>(); Map<Occurrence,Integer> lanes=new LinkedHashMap<>();
            for(Occurrence occurrence:occurrences) {
                Event e=occurrence.event();
                int lane=0; while(lane<ends.size()&&ends.get(lane).isAfter(e.start()))lane++;
                if(lane==ends.size())ends.add(e.end());else ends.set(lane,e.end()); lanes.put(occurrence,lane);
            }
            int laneWidth=width/Math.max(1,ends.size());
            for(var entry:lanes.entrySet()) {
                Occurrence occurrence=entry.getKey(); Event e=occurrence.event();
                Rectangle r=new Rectangle(LEFT+d*width+entry.getValue()*laneWidth+2,y(e.start())+1,
                        Math.max(4,laneWidth-4),Math.max(8,y(e.end())-y(e.start())-2));
                boxes.put(r,e);
                g.setColor(occurrence.canceled()?new Color(232,234,238):Color.getHSBColor(Math.floorMod(e.title().hashCode(),360)/360f,.18f,.97f));
                g.fillRoundRect(r.x,r.y,r.width,r.height,8,8);
                if(!e.recurring()) {
                    g.setColor(new Color(70,110,190));g.drawRoundRect(r.x,r.y,r.width,r.height,8,8);
                }
                Graphics2D text=(Graphics2D)g.create(); text.clip(r);
                text.setColor(occurrence.canceled()?Color.GRAY:new Color(40,50,70));
                // 시간, 장소, 메모 공간을 남긴 뒤 제목을 줄바꿈한다.
                FontMetrics fm=text.getFontMetrics(); int textWidth=r.width-8, rows=Math.max(1,(r.height-3)/16);
                String place=placeName(e);
                String times = TimePicker.display(e.start()) + "–" + TimePicker.display(e.end());
                boolean hasNote = !e.note().isBlank();
                int titleRows = Math.max(1, rows - 1 - (place.isEmpty() ? 0 : 1) - (hasNote ? 1 : 0));
                List<String> lines=wrap(fm,(occurrence.canceled()?"[제외] ":"")+e.title(),textWidth,titleRows);
                int timeRow = lines.size();
                if (timeRow < rows) lines.add(times);
                if (!place.isEmpty() && lines.size() < rows) lines.add(fit(fm, place, textWidth));
                if(hasNote && lines.size()<rows) {
                    lines.addAll(wrap(fm,e.note().replaceAll("\\s+", " "),textWidth,rows-lines.size()));
                }
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    int baseline = r.y + 16 + i * 16;
                    if (i == timeRow) drawTime(text, line, r.x + 5, baseline, textWidth);
                    else text.drawString(line, r.x + 5, baseline);
                }
                text.dispose();
            }
        }
        if(selection!=null) {
            int x=LEFT+columnOf(selection.day())*width+1, top=y(selection.start()), height=Math.max(6,y(selection.end())-top);
            g.setColor(new Color(40,100,230,55));g.fillRect(x,top,width-2,height);
            g.setColor(new Color(40,100,230));g.drawRect(x,top,width-2,height);
            String label=TimePicker.display(selection.start())+"–"+TimePicker.display(selection.end());
            g.drawString(label,x+4,Math.max(TOP+14,top-4));
        }
        g.dispose();
    }
}
