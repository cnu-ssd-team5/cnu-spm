package campus;


import campus.PlannerModel.Event;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.util.*;
import java.util.List;

/** 주간 시간 격자. 겹치는 일정도 별도 열에 표시한다. */
final class WeeklyGrid extends JPanel implements Scrollable {
    private final WeeklyScheduleService service;
    private final PlaceCatalog map;
    private final WeeklyTimetablePanel.Editor editor;
    private final Map<Rectangle,Event> boxes=new LinkedHashMap<>();
    private int first=8,last=24;
    private static final int LEFT=56,TOP=38,HOUR=72;
    WeeklyGrid(WeeklyScheduleService service,PlaceCatalog map,WeeklyTimetablePanel.Editor editor) {
        this.service=service;this.map=map;this.editor=editor;
        setBackground(Color.WHITE);
        setToolTipText("");
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount()==2) {
                    JPopupMenu menu=menuAt(e.getPoint());
                    if(menu!=null)((JMenuItem)menu.getComponent(0)).doClick();
                }
            }
            public void mousePressed(MouseEvent e) { popup(e); }
            public void mouseReleased(MouseEvent e) { popup(e); }
            private void popup(MouseEvent e) {
                if(!e.isPopupTrigger())return;
                JPopupMenu menu=menuAt(e.getPoint());
                if(menu!=null)menu.show(WeeklyGrid.this,e.getX(),e.getY());
            }
        });
        refresh();
    }
    JPopupMenu menuAt(Point point) {
        Event hit=null;
        for(var box:boxes.entrySet())if(box.getKey().contains(point)){hit=box.getValue();break;}
        int day=(point.x-LEFT)/columnWidth();
        int minute=first*60+(point.y-TOP)*60/HOUR;
        if(hit==null&&(point.x<LEFT||day<0||day>6||point.y<TOP||minute>=last*60))return null;
        Event selected=hit;
        int startMinute=Math.min(24*60-5,Math.max(first*60,minute/5*5));
        JPopupMenu menu=new JPopupMenu();
        JMenuItem item=new JMenuItem(hit==null?"수업 추가":"수업 수정");
        item.addActionListener(e->editor.open(selected,selected==null?DayOfWeek.of(day+1):selected.weekday(),
                selected==null?LocalTime.of(startMinute/60,startMinute%60):selected.start()));
        menu.add(item);return menu;
    }
    void refresh() {
        first=8;last=24;
        boxes.clear();
        setPreferredSize(new Dimension(980,TOP+(last-first)*HOUR+12));revalidate();repaint();
    }
    @Override public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
    @Override public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 24;}
    @Override public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(24,visible.height-24);}
    @Override public boolean getScrollableTracksViewportWidth(){return true;}
    @Override public boolean getScrollableTracksViewportHeight(){return false;}
    private int columnWidth(){return Math.max(1,(getWidth()-LEFT)/7);}
    private int y(LocalTime time){return TOP+((time.equals(LocalTime.MAX)?1440:time.toSecondOfDay()/60)-first*60)*HOUR/60;}
    public String getToolTipText(MouseEvent e) {
        for(var box:boxes.entrySet())if(box.getKey().contains(e.getPoint())) {
            Event v=box.getValue();var b=map.find(v.buildingId());
            return v.title()+" · "+TimePicker.display(v.start())+"–"+TimePicker.display(v.end())+" · "+(b==null?"장소 없음":b.name());
        }
        return null;
    }
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);boxes.clear();
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setFont(Theme.font(11,Font.PLAIN));
        int width=columnWidth();String[] days={"월","화","수","목","금","토","일"};
        g.setColor(new Color(245,247,251));g.fillRect(0,0,getWidth(),TOP);
        for(int d=0;d<7;d++) {
            if(d>=5){g.setColor(d==5?Theme.SATURDAY:Theme.SUNDAY);g.fillRect(LEFT+d*width,0,width,TOP);}
            g.setColor(d==5?Theme.BLUE:d==6?new Color(196,54,54):Theme.INK);
            g.drawString(days[d],LEFT+d*width+width/2-5,24);
        }
        for(int hour=first;hour<=last;hour++) {
            int y=TOP+(hour-first)*HOUR;
            g.setColor(new Color(100,110,125));g.drawString(String.format("%02d:00",hour),5,y+5);
            g.setColor(new Color(220,225,233));g.drawLine(LEFT,y,getWidth(),y);
            g.setColor(new Color(240,242,246));g.drawLine(LEFT,y+HOUR/2,getWidth(),y+HOUR/2);
        }
        for(int d=0;d<=7;d++){g.setColor(new Color(225,230,237));g.drawLine(LEFT+d*width,0,LEFT+d*width,getHeight());}
        for(int d=1;d<=7;d++) {
            int day=d;
            List<Event> events=service.events().stream().filter(e->e.weekday().getValue()==day && e.end().isAfter(LocalTime.of(8,0))).sorted(Comparator.comparing(Event::start)).toList();
            List<LocalTime> ends=new ArrayList<>();Map<Event,Integer> lanes=new LinkedHashMap<>();
            for(Event e:events) {
                int lane=0;while(lane<ends.size()&&ends.get(lane).isAfter(e.start()))lane++;
                if(lane==ends.size())ends.add(e.end());else ends.set(lane,e.end());lanes.put(e,lane);
            }
            int laneWidth=width/Math.max(1,ends.size());
            for(var entry:lanes.entrySet()) {
                Event e=entry.getKey();int top=Math.max(TOP,y(e.start()));Rectangle r=new Rectangle(LEFT+(d-1)*width+entry.getValue()*laneWidth+2,top+1,Math.max(4,laneWidth-4),Math.max(1,y(e.end())-top-2));
                boxes.put(r,e);g.setColor(Theme.courseColor(e.title()));g.fillRoundRect(r.x,r.y,r.width,r.height,8,8);
                Graphics2D text=(Graphics2D)g.create();text.clip(r);text.setColor(new Color(40,50,70));
                text.drawString(e.title(),r.x+5,r.y+16);
                if(r.height>34)text.drawString(TimePicker.display(e.start())+"–"+TimePicker.display(e.end()),r.x+5,r.y+32);
                var b=map.find(e.buildingId());if(r.height>50&&b!=null)text.drawString(b.name(),r.x+5,r.y+48);
                text.dispose();
            }
        }
        g.dispose();
    }
}
