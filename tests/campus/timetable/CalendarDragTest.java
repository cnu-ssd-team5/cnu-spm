package campus.timetable;
import campus.calendar.*;
import campus.map.*;
import campus.model.*;
import campus.model.PlannerModel.*;
import campus.model.PlannerModel.Event;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import java.util.List;

public class CalendarDragTest {
 static int passed, failed; static LocalDate monday=LocalDate.of(2026,11,16);
 interface Checked { void run() throws Exception; }
 static void test(String name, Checked run) {try{run.run();passed++;System.out.println("PASS "+name);}catch(Throwable e){failed++;System.out.println("FAIL "+name+" "+e);}}
 static void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static Event once(TimetableService s,String title,LocalDate date){return s.prepareOnce(null,title,"w1",LocalTime.of(14,0),LocalTime.of(16,0),Category.GROUP,date,"memo");}
 static void paint(WeekGrid grid){grid.setSize(756,1250);var image=new BufferedImage(756,1250,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();grid.paint(g);g.dispose();}
 static int y(int hour){return 8+(hour-8)*72;}
 static void mouse(WeekGrid grid,int id,int x,int y,int count,int button){grid.dispatchEvent(new MouseEvent(grid,id,System.currentTimeMillis(),id==MouseEvent.MOUSE_DRAGGED?InputEvent.BUTTON1_DOWN_MASK:0,x,y,count,false,button));}
 static void drag(WeekGrid grid,int x,int start,int end){mouse(grid,MouseEvent.MOUSE_PRESSED,x,start,1,1);mouse(grid,MouseEvent.MOUSE_DRAGGED,x,end,0,0);mouse(grid,MouseEvent.MOUSE_RELEASED,x,end,1,1);}
 public static void main(String[] args)throws Exception {
 CampusMap map=CampusMap.load(Path.of(args[0]));
 PlannerModel model=new PlannerModel();TimetableService service=new TimetableService(model);service.weekOf(monday);
 test("week-start-from-sunday",()->{service.weekOf(monday.minusDays(1));ok(service.dateFor(DayOfWeek.SUNDAY).equals(monday.minusDays(1))&&service.dateFor(DayOfWeek.MONDAY).equals(monday),"wrong week");});
 test("saturday-ends-week",()->{service.weekOf(monday.plusDays(5));ok(service.dateFor(DayOfWeek.SUNDAY).equals(monday.minusDays(1))&&service.dateFor(DayOfWeek.SATURDAY).equals(monday.plusDays(5)),"wrong week end");service.weekOf(monday);});
 test("next-sunday-starts-new-week",()->{service.weekOf(monday.plusDays(6));ok(service.dateFor(DayOfWeek.MONDAY).equals(monday.plusWeeks(1)),"sunday kept old week");service.weekOf(monday);});
 Event single=once(service,"study",monday);service.apply(null,List.of(single));
 test("one-off-visible-in-selected-week",()->ok(service.occurrences(DayOfWeek.MONDAY).size()==1,"missing"));
 test("one-off-not-visible-next-week",()->{service.weekOf(monday.plusWeeks(1));ok(service.visibleEvents().isEmpty(),"leaked");service.weekOf(monday);});
 test("one-off-category-preserved",()->ok(service.visibleEvents().get(0).category()==Category.GROUP,"category"));
 test("one-off-date-weekday",()->{Event v=once(service,"sat",monday.plusDays(5));service.apply(null,List.of(v));ok(service.occurrences(DayOfWeek.SATURDAY).get(0).event().id().equals(v.id()),"wrong column");service.delete(v);});
 test("one-off-edit-stable-id",()->{Event v=service.prepareOnce(single,"edited","w1",single.start(),single.end(),single.category(),monday.plusDays(1),single.note());ok(v.id().equals(single.id()),"id changed");service.apply(single,List.of(v));ok(service.occurrences(DayOfWeek.MONDAY).isEmpty()&&service.occurrences(DayOfWeek.TUESDAY).size()==1,"move date");service.delete(v);});
 var recurring=service.prepare(null,"club","w1",LocalTime.of(9,0),LocalTime.of(10,0),Category.CLUB,EnumSet.of(DayOfWeek.MONDAY,DayOfWeek.WEDNESDAY),"note");service.apply(null,recurring);
 test("recurring-multiple-days",()->ok(service.occurrences(DayOfWeek.MONDAY).size()==1&&service.occurrences(DayOfWeek.WEDNESDAY).size()==1,"missing days"));
 test("recurring-next-week",()->{service.weekOf(monday.plusWeeks(1));ok(service.visibleEvents().size()==2,"missing repeat");service.weekOf(monday);});
 model.toggleCancellation(recurring.get(0).id(),monday);
 test("cancellation-visible-only-on-date",()->{ok(service.occurrences(DayOfWeek.MONDAY).get(0).canceled(),"cancel missing");service.weekOf(monday.plusWeeks(1));ok(!service.occurrences(DayOfWeek.MONDAY).get(0).canceled(),"cancel leaked");service.weekOf(monday);});
 test("recurring-edit-preserves-cancellation",()->{var edit=service.prepare(recurring.get(0),"club edited","w1",LocalTime.of(9,0),LocalTime.of(10,30),Category.CLUB,EnumSet.of(DayOfWeek.MONDAY,DayOfWeek.WEDNESDAY),"note");service.apply(recurring.get(0),edit);ok(service.occurrences(DayOfWeek.MONDAY).get(0).canceled(),"lost exception");});
 test("group-independent-of-selected-term",()->{service.weekOf(LocalDate.of(2027,3,10));ok(service.group(recurring.get(0)).size()==2,"wrong group");service.weekOf(monday);});
 test("recurring-to-one-off-conversion",()->{Event v=once(service,"converted",monday);service.apply(recurring.get(0),List.of(v));ok(model.events().size()==1&&!model.events().get(0).recurring(),"old repeat left");});
 Event converted=model.events().get(0);
 test("one-off-to-recurring-conversion",()->{var group=service.prepare(converted,"repeat","w1",LocalTime.of(11,0),LocalTime.NOON,Category.OTHER,EnumSet.of(DayOfWeek.FRIDAY),"");service.apply(converted,group);ok(model.events().size()==1&&model.events().get(0).recurring(),"old once left");});
 test("one-off-conflicts-with-recurring",()->{Event v=service.prepareOnce(null,"overlap","w1",LocalTime.of(11,30),LocalTime.of(12,30),Category.GROUP,monday.plusDays(4),"");ok(service.overlaps(null,List.of(v)),"missed conflict");});
 test("same-time-other-date-no-conflict",()->{Event v=service.prepareOnce(null,"other date","w1",LocalTime.of(11,30),LocalTime.of(12,30),Category.GROUP,monday.plusDays(3),"");ok(!service.overlaps(null,List.of(v)),"false conflict");});
 test("roundtrip-mixed-events",()->{service.apply(null,List.of(once(service,"one-off",monday)));Path p=Path.of(args[1]);PlannerStore.save(p,model);PlannerModel back=PlannerStore.load(p);ok(back.events().equals(model.events()),"save differs");});
 test("recurring-class-visible-in-late-august-week",()->{PlannerModel m=new PlannerModel();TimetableService s=new TimetableService(m);s.weekOf(LocalDate.of(2027,8,30));Event v=new Event("fall","fall","w1",LocalTime.of(9,0),LocalTime.of(10,0),Category.CLASS,DayOfWeek.WEDNESDAY,null,"");m.putEvent(v);ok(s.occurrences(DayOfWeek.WEDNESDAY).size()==1,"recurring class lost");});
 SwingUtilities.invokeAndWait(()->{
 PlannerModel empty=new PlannerModel();TimetableService s=new TimetableService(empty);s.weekOf(monday);
 List<WeekGrid.Selection> opened=new ArrayList<>();List<Event> edited=new ArrayList<>();
 WeekGrid grid=new WeekGrid(s,map,(event,day,start,end)->{opened.add(new WeekGrid.Selection(day,start,end));edited.add(event);},()->true);paint(grid);
 test("forward-drag-fills-two-hour-range",()->{drag(grid,200,y(14),y(16));ok(opened.size()==1&&opened.get(0).start().equals(LocalTime.of(14,0))&&opened.get(0).end().equals(LocalTime.of(16,0)),"bad selection");});
 test("reverse-drag-normalized",()->{opened.clear();drag(grid,200,y(16),y(14));ok(opened.get(0).start().equals(LocalTime.of(14,0))&&opened.get(0).end().equals(LocalTime.of(16,0)),"bad reverse");});
 test("drag-locked-to-start-day",()->{opened.clear();mouse(grid,MouseEvent.MOUSE_PRESSED,200,y(14),1,1);mouse(grid,MouseEvent.MOUSE_DRAGGED,550,y(16),0,0);mouse(grid,MouseEvent.MOUSE_RELEASED,550,y(16),1,1);ok(opened.get(0).day()==DayOfWeek.MONDAY,"day changed");});
 test("single-click-does-not-open",()->{opened.clear();mouse(grid,MouseEvent.MOUSE_PRESSED,200,y(14),1,1);mouse(grid,MouseEvent.MOUSE_RELEASED,200,y(14),1,1);ok(opened.isEmpty(),"single opened");});
 test("double-click-retained",()->{opened.clear();mouse(grid,MouseEvent.MOUSE_CLICKED,200,y(14),2,1);ok(opened.size()==1,"double missing");});
 test("top-padding-does-not-start-selection",()->{opened.clear();drag(grid,200,4,y(10));ok(opened.isEmpty(),"padding selected");});
 test("time-axis-does-not-start-selection",()->{opened.clear();drag(grid,30,y(14),y(16));ok(opened.isEmpty(),"axis selected");});
 test("escape-cancels-drag",()->{opened.clear();mouse(grid,MouseEvent.MOUSE_PRESSED,200,y(14),1,1);mouse(grid,MouseEvent.MOUSE_DRAGGED,200,y(16),0,0);grid.getActionMap().get("cancelSelection").actionPerformed(new ActionEvent(grid,0,""));mouse(grid,MouseEvent.MOUSE_RELEASED,200,y(16),1,1);ok(opened.isEmpty(),"escape opened");});
 test("end-of-day-clamped-to-24",()->{var v=grid.selectionBetween(DayOfWeek.MONDAY,y(23),y(25));ok(v.end().equals(LocalTime.MAX),"not 24");});
 test("reverse-outside-top-clamped",()->{var v=grid.selectionBetween(DayOfWeek.MONDAY,y(10),-100);ok(v.start().equals(LocalTime.of(8,0)),"past top");});
 test("half-hour-start-five-minute-end",()->{var v=grid.selectionBetween(DayOfWeek.MONDAY,y(14)+2,y(14)+10);ok(v.start().equals(LocalTime.of(14,0))&&v.end().equals(LocalTime.of(14,10)),"snapping");});
 test("drag-start-before-half-hour-rounds-down",()->{var v=grid.selectionBetween(DayOfWeek.MONDAY,y(14)+24,y(15)+8);ok(v.start().equals(LocalTime.of(14,0))&&v.end().equals(LocalTime.of(15,10)),"start/end snapping");});
 test("drag-start-after-half-hour-rounds-down",()->{var v=grid.selectionBetween(DayOfWeek.MONDAY,y(14)+54,y(16));ok(v.start().equals(LocalTime.of(14,30)),"second half");});
 test("reverse-drag-uses-same-half-hour-start",()->{var v=grid.selectionBetween(DayOfWeek.MONDAY,y(16),y(14)+54);ok(v.start().equals(LocalTime.of(14,30))&&v.end().equals(LocalTime.of(16,0)),"reverse snap");});
 test("last-range-starts-23-30-and-ends-24",()->{var v=grid.selectionBetween(DayOfWeek.MONDAY,y(24)-4,y(24)+20);ok(v.start().equals(LocalTime.of(23,30))&&v.end().equals(LocalTime.MAX),"end boundary");});
 test("context-menu-starts-on-half-hour",()->{opened.clear();((JMenuItem)grid.menuAt(new Point(200,y(14)+54)).getComponent(0)).doClick();ok(opened.get(0).start().equals(LocalTime.of(14,30)),"context snap");});
 test("right-click-add-range",()->{opened.clear();grid.menuAt(new Point(300,y(14))).getComponent(0);((JMenuItem)grid.menuAt(new Point(300,y(14))).getComponent(0)).doClick();ok(opened.get(0).day()==DayOfWeek.TUESDAY,"wrong context day");});
 test("first-column-is-sunday",()->{opened.clear();((JMenuItem)grid.menuAt(new Point(100,y(14))).getComponent(0)).doClick();ok(opened.get(0).day()==DayOfWeek.SUNDAY,"first column not sunday");});
 test("last-column-is-saturday",()->{opened.clear();((JMenuItem)grid.menuAt(new Point(700,y(14))).getComponent(0)).doClick();ok(opened.get(0).day()==DayOfWeek.SATURDAY,"last column not saturday");});
 test("existing-one-off-double-click-edit",()->{opened.clear();edited.clear();Event event=s.prepareOnce(null,"once",null,LocalTime.of(9,0),LocalTime.of(10,0),Category.GROUP,monday,"준비물 <A>\n회의실 B","","온라인 회의실");s.apply(null,List.of(event));grid.refresh();paint(grid);mouse(grid,MouseEvent.MOUSE_CLICKED,200,y(9)+25,2,1);ok(edited.size()==1&&edited.get(0).id().equals(event.id()),"once not editable");});
 test("event-menu-offers-edit-and-delete",()->{JPopupMenu menu=grid.menuAt(new Point(200,y(9)+25));ok(menu.getComponentCount()==2&&((JMenuItem)menu.getComponent(0)).getText().equals("일정 수정")&&((JMenuItem)menu.getComponent(1)).getText().equals("일정 삭제"),"wrong event menu");});
 test("one-off-tooltip-shows-actual-date-custom-place-and-full-memo",()->{
     String tooltip=grid.getToolTipText(new MouseEvent(grid,MouseEvent.MOUSE_MOVED,0,0,200,y(9)+25,0,false));
     ok(tooltip.contains(monday.toString())&&tooltip.contains("온라인 회의실")
             &&tooltip.contains("준비물 &lt;A&gt;<br>회의실 B"),"missing date, location or memo: "+tooltip);
 });
 test("drag-on-existing-event-does-not-create",()->{opened.clear();drag(grid,200,y(9)+25,y(11));ok(opened.isEmpty(),"event drag created");});
 test("canceled-event-render",()->{empty.toggleCancellation(empty.events().get(0).id(),monday);paint(grid);});
 test("next-week-hides-one-off",()->{s.weekOf(monday.plusWeeks(1));grid.refresh();paint(grid);ok(s.visibleEvents().isEmpty(),"leak");});
 test("date-row-side-alignment-and-vertical-scroll",()->{
     JScrollPane scroll=grid.scrollPane();scroll.setSize(450,300);scroll.doLayout();
     scroll.getViewport().doLayout();scroll.getColumnHeader().doLayout();
     JViewport dates=scroll.getColumnHeader();JViewport times=scroll.getViewport();
     ok(dates.getWidth()==times.getWidth()&&grid.getWidth()==times.getWidth()
             &&dates.getView().getWidth()==grid.getWidth(),"weekday columns clipped or misaligned");
     int headerY=dates.getY();scroll.getVerticalScrollBar().setValue(200);
     ok(times.getViewPosition().y==200&&dates.getViewPosition().y==0&&dates.getY()==headerY,"date row scrolled with the grid");
 });
 test("long-title-wraps-within-box",()->{var fm=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB).createGraphics().getFontMetrics(new Font(Font.DIALOG,Font.PLAIN,12));var lines=WeekGrid.wrap(fm,"소프트웨어시스템설계 팀 프로젝트 회의",90,2);ok(lines.size()==2&&lines.get(1).endsWith("…")&&lines.stream().allMatch(l->fm.stringWidth(l)<=90),"long title not wrapped "+lines);ok(WeekGrid.wrap(fm,"짧은 제목",90,3).equals(List.of("짧은 제목")),"short title changed");ok(WeekGrid.fit(fm,"아주아주아주 긴 건물 이름입니다",40).endsWith("…"),"no ellipsis");});
 });
 System.out.println("SUMMARY passed="+passed+" failed="+failed);System.exit(failed==0?0:1);
 }
}
