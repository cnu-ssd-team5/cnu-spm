package campus.timetable;

import campus.map.CampusMap;
import campus.model.PlannerModel.Event;
import campus.model.PlannerModel.Occurrence;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.util.*;
import java.util.List;

/** 선택한 주의 반복/일회성 일정과 드래그로 선택한 시간 범위를 표시한다. */
final class WeekGrid extends JPanel {
    interface Editor { void open(Event event, DayOfWeek day, LocalTime time); }
    interface RangeEditor { void open(Event event, DayOfWeek day, LocalTime start, LocalTime end); }
    record Selection(DayOfWeek day, LocalTime start, LocalTime end) { }
    private final TimetableService service;
    private final CampusMap map;
    private final RangeEditor editor;
    private final Map<Rectangle,Event> boxes = new LinkedHashMap<>();
    private int first = 8, last = 24;
    private static final int LEFT = 56, TOP = 38, HOUR = 72, SNAP = 5;
    private Point anchor;
    private DayOfWeek dragDay;
    private boolean dragging, suppressClick;
    private Selection selection;

    WeekGrid(TimetableService service, CampusMap map, Editor editor) {
        this(service, map, (RangeEditor)(event, day, start, end) -> editor.open(event, day, start));
    }
    WeekGrid(TimetableService service, CampusMap map, RangeEditor editor) {
        this.service=service; this.map=map; this.editor=editor;
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
    private void cancelSelection() {
        anchor=null; dragDay=null; dragging=false; selection=null; repaint();
    }
    private Event hit(Point point) {
        for(var box:boxes.entrySet()) if(box.getKey().contains(point)) return box.getValue();
        return null;
    }
    private boolean inGrid(Point point) {
        return point.x>=LEFT && point.x<LEFT+7*columnWidth() && point.y>=TOP && point.y<y(LocalTime.MAX);
    }
    private DayOfWeek dayAt(Point point) { return DayOfWeek.of((point.x-LEFT)/columnWidth()+1); }
    Selection selectionBetween(DayOfWeek day, int fromY, int toY) {
        double a=first*60+(fromY-TOP)*60.0/HOUR;
        double b=first*60+(toY-TOP)*60.0/HOUR;
        int start=Math.max(first*60,Math.min(1440-SNAP,(int)Math.floor(Math.min(a,b)/SNAP)*SNAP));
        int end=Math.max(start+SNAP,Math.min(1440,(int)Math.ceil(Math.max(a,b)/SNAP)*SNAP));
        return new Selection(day,LocalTime.of(start/60,start%60),end==1440?LocalTime.MAX:LocalTime.of(end/60,end%60));
    }
    JPopupMenu menuAt(Point point) {
        Event selected=hit(point);
        if(selected==null && !inGrid(point)) return null;
        DayOfWeek day=selected==null?dayAt(point):selected.recurring()?selected.weekday():selected.date().getDayOfWeek();
        int minute=first*60+(point.y-TOP)*60/HOUR;
        int startMinute=Math.max(0,Math.min(1440-SNAP,minute/SNAP*SNAP));
        LocalTime start=selected==null?LocalTime.of(startMinute/60,startMinute%60):selected.start();
        LocalTime end=selected==null?(startMinute+60>=1440?LocalTime.MAX:start.plusHours(1)):selected.end();
        JPopupMenu menu=new JPopupMenu();
        JMenuItem item=new JMenuItem(selected==null?"일정 추가":"일정 수정");
        item.addActionListener(e->editor.open(selected,day,start,end));
        menu.add(item); return menu;
    }
    void refresh() {
        first=Math.min(8,service.visibleEvents().stream().mapToInt(e->e.start().getHour()).min().orElse(8));
        last=24;
        boxes.clear();
        cancelSelection();
        setPreferredSize(new Dimension(480,TOP+(last-first)*HOUR+12)); revalidate(); repaint();
    }
    private int columnWidth() { return Math.max(1,(getWidth()-LEFT)/7); }
    private int y(LocalTime time) { return TOP+((time.equals(LocalTime.MAX)?1440:time.toSecondOfDay()/60)-first*60)*HOUR/60; }
    @Override public String getToolTipText(MouseEvent e) {
        Event v=hit(e.getPoint());
        if(v==null)return "빈 시간대를 위아래로 드래그하여 일정을 추가하세요 (5분 단위, Esc로 취소)";
        var b=map.building(v.buildingId());
        return v.title()+" · "+TimePicker.display(v.start())+"–"+TimePicker.display(v.end())+" · "
                +(b==null?"장소 없음":b.name())+" · "+(v.recurring()?"매주 반복":v.date().toString());
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics); boxes.clear();
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        int width=columnWidth(); String[] labels={"월","화","수","목","금","토","일"};
        g.setColor(new Color(245,247,251)); g.fillRect(0,0,getWidth(),TOP);
        g.setColor(new Color(45,55,75));
        for(int d=0;d<7;d++) {
            LocalDate date=service.dateFor(DayOfWeek.of(d+1));
            String label=labels[d]+" "+date.getMonthValue()+"/"+date.getDayOfMonth();
            g.drawString(label,LEFT+d*width+Math.max(2,(width-g.getFontMetrics().stringWidth(label))/2),24);
        }
        for(int hour=first;hour<=last;hour++) {
            int row=TOP+(hour-first)*HOUR;
            g.setColor(new Color(100,110,125)); g.drawString(String.format("%02d:00",hour),5,row+5);
            g.setColor(new Color(220,225,233)); g.drawLine(LEFT,row,getWidth(),row);
            g.setColor(new Color(240,242,246)); g.drawLine(LEFT,row+HOUR/2,getWidth(),row+HOUR/2);
        }
        for(int d=0;d<=7;d++) {g.setColor(new Color(225,230,237));g.drawLine(LEFT+d*width,0,LEFT+d*width,getHeight());}
        for(int d=1;d<=7;d++) {
            List<Occurrence> occurrences=service.occurrences(DayOfWeek.of(d));
            List<LocalTime> ends=new ArrayList<>(); Map<Occurrence,Integer> lanes=new LinkedHashMap<>();
            for(Occurrence occurrence:occurrences) {
                Event e=occurrence.event();
                int lane=0; while(lane<ends.size()&&ends.get(lane).isAfter(e.start()))lane++;
                if(lane==ends.size())ends.add(e.end());else ends.set(lane,e.end()); lanes.put(occurrence,lane);
            }
            int laneWidth=width/Math.max(1,ends.size());
            for(var entry:lanes.entrySet()) {
                Occurrence occurrence=entry.getKey(); Event e=occurrence.event();
                Rectangle r=new Rectangle(LEFT+(d-1)*width+entry.getValue()*laneWidth+2,y(e.start())+1,
                        Math.max(4,laneWidth-4),Math.max(8,y(e.end())-y(e.start())-2));
                boxes.put(r,e);
                g.setColor(occurrence.canceled()?new Color(232,234,238):Color.getHSBColor(Math.floorMod(e.title().hashCode(),360)/360f,.18f,.97f));
                g.fillRoundRect(r.x,r.y,r.width,r.height,8,8);
                if(!e.recurring()) {
                    g.setColor(new Color(70,110,190));g.drawRoundRect(r.x,r.y,r.width,r.height,8,8);
                }
                Graphics2D text=(Graphics2D)g.create(); text.clip(r);
                text.setColor(occurrence.canceled()?Color.GRAY:new Color(40,50,70));
                text.drawString((occurrence.canceled()?"[제외] ":"")+e.title(),r.x+5,r.y+16);
                if(r.height>34)text.drawString(TimePicker.display(e.start())+"–"+TimePicker.display(e.end()),r.x+5,r.y+32);
                var b=map.building(e.buildingId()); if(r.height>50&&b!=null)text.drawString(b.name(),r.x+5,r.y+48);
                if(r.height>66)text.drawString(e.recurring()?"매주 반복":"이 날짜만",r.x+5,r.y+64);
                text.dispose();
            }
        }
        if(selection!=null) {
            int x=LEFT+(selection.day().getValue()-1)*width+1, top=y(selection.start()), height=Math.max(6,y(selection.end())-top);
            g.setColor(new Color(40,100,230,55));g.fillRect(x,top,width-2,height);
            g.setColor(new Color(40,100,230));g.drawRect(x,top,width-2,height);
            String label=TimePicker.display(selection.start())+"–"+TimePicker.display(selection.end());
            g.drawString(label,x+4,Math.max(TOP+14,top-4));
        }
        g.dispose();
    }
}
