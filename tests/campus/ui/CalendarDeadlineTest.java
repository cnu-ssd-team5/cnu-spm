package campus.ui;

import campus.calendar.*;
import campus.map.*;
import campus.model.*;
import campus.model.PlannerModel.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import javax.swing.*;

/** Calendar mouse/keyboard and deadline overview checks with isolated models. */
public final class CalendarDeadlineTest {
    interface Checked {void run()throws Exception;}
    static int passed,failed;
    static void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void test(String name,Checked run){try{run.run();passed++;System.out.println("PASS "+name);}catch(Throwable e){failed++;System.out.println("FAIL "+name);e.printStackTrace(System.out);}}
    static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static <T> List<T> all(Container root,Class<T> type){List<T> result=new ArrayList<>();for(Component c:root.getComponents()){if(type.isInstance(c))result.add(type.cast(c));if(c instanceof Container cc)result.addAll(all(cc,type));}return result;}
    static final class CalendarFixture {
        final PlannerModel model=new PlannerModel();
        final AtomicReference<LocalDate> selected=new AtomicReference<>(),added=new AtomicReference<>();
        final AtomicInteger addCount=new AtomicInteger(),editCount=new AtomicInteger();
        final CalendarPanel panel;
        CalendarFixture(LocalDate date){
            CalendarPanel[] holder=new CalendarPanel[1];
            panel=new CalendarPanel(model,d->{selected.set(d);holder[0].refresh(d);},d->{added.set(d);addCount.incrementAndGet();},d->editCount.incrementAndGet());
            holder[0]=panel;selected.set(date);panel.refresh(date);
        }
        JButton day(LocalDate date){return all(panel,JButton.class).stream().filter(b->date.toString().equals(b.getName())).findFirst().orElseThrow();}
        void click(LocalDate date,int count,long when){JButton button=day(date);button.dispatchEvent(new MouseEvent(button,MouseEvent.MOUSE_CLICKED,when,0,4,4,count,false,MouseEvent.BUTTON1));}
        void key(String key)throws Exception{JPanel days=(JPanel)field(panel,"days");days.getActionMap().get(key).actionPerformed(new ActionEvent(days,0,key));}
    }
    static Deadline deadline(String id,LocalDate date,boolean done){return new Deadline(id,id,date,"memo",done,done?date:null);}
    public static void main(String[]args)throws Exception{
        CampusMap map=CampusMap.load(Path.of(args[0]));LocalDate today=PlannerTime.today();
        SwingUtilities.invokeAndWait(()->{
            test("calendar-add-toolbar-button-removed",()->{CalendarFixture f=new CalendarFixture(today);ok(all(f.panel,JButton.class).stream().noneMatch(b->b.getText().contains("할일 추가")),"old button remains");});
            test("single-date-click-selects-without-adding",()->{CalendarFixture f=new CalendarFixture(today);LocalDate d=today.plusDays(1);f.click(d,1,1000);ok(d.equals(f.selected.get())&&f.addCount.get()==0,"single click added");});
            test("double-click-opens-exact-date-once",()->{CalendarFixture f=new CalendarFixture(today);f.click(today,1,1000);f.click(today,2,1100);ok(today.equals(f.added.get())&&f.addCount.get()==1,"wrong double click");});
            test("double-click-survives-cell-rebuild",()->{CalendarFixture f=new CalendarFixture(today);JButton first=f.day(today);f.click(today,1,1000);ok(first!=f.day(today),"test did not rebuild");f.click(today,1,1100);ok(f.addCount.get()==1,"lost double click after rebuild");});
            test("different-dates-not-a-double-click",()->{CalendarFixture f=new CalendarFixture(today);f.click(today,1,1000);f.click(today.plusDays(1),1,1100);ok(f.addCount.get()==0,"different dates added");});
            test("slow-clicks-not-a-double-click",()->{CalendarFixture f=new CalendarFixture(today);f.click(today,1,1000);f.click(today,1,4000);ok(f.addCount.get()==0,"slow click added");});
            test("far-apart-clicks-not-a-double-click",()->{CalendarFixture f=new CalendarFixture(today);f.click(today,1,1000);JButton day=f.day(today);day.dispatchEvent(new MouseEvent(day,MouseEvent.MOUSE_CLICKED,1100,0,40,40,1,false,1));ok(f.addCount.get()==0,"far clicks added");});
            test("blank-date-cell-double-click",()->{CalendarFixture f=new CalendarFixture(today);JPanel cell=(JPanel)f.day(today).getParent().getParent();cell.dispatchEvent(new MouseEvent(cell,MouseEvent.MOUSE_CLICKED,1000,0,8,8,2,false,1));ok(f.addCount.get()==1,"cell did not add");});
            test("task-button-edits-without-adding",()->{CalendarFixture f=new CalendarFixture(today);f.model.putDeadline(deadline("task",today,false));f.panel.refresh(today);all(f.panel,JButton.class).stream().filter(b->b.getText().equals("task")).findFirst().orElseThrow().doClick();ok(f.editCount.get()==1&&f.addCount.get()==0,"task click wrong action");});
            test("keyboard-left-crosses-year",()->{CalendarFixture f=new CalendarFixture(LocalDate.of(2027,1,1));f.key("LEFT");ok(f.selected.get().equals(LocalDate.of(2026,12,31)),"year boundary");});
            test("keyboard-right-crosses-month",()->{CalendarFixture f=new CalendarFixture(LocalDate.of(2026,11,30));f.key("RIGHT");ok(f.selected.get().equals(LocalDate.of(2026,12,1)),"month boundary");});
            test("keyboard-up-down-one-week",()->{CalendarFixture f=new CalendarFixture(today);f.key("UP");ok(f.selected.get().equals(today.minusWeeks(1)),"up");f.key("DOWN");ok(f.selected.get().equals(today),"down");});
            test("keyboard-leap-day",()->{CalendarFixture f=new CalendarFixture(LocalDate.of(2028,2,28));f.key("RIGHT");ok(f.selected.get().equals(LocalDate.of(2028,2,29)),"leap missing");});
            test("keyboard-enter-adds-selected-date",()->{CalendarFixture f=new CalendarFixture(today);f.key("RIGHT");f.key("addDeadline");ok(f.addCount.get()==1&&f.added.get().equals(today.plusDays(1)),"enter date");});
            test("keyboard-navigation-breaks-double-click-sequence",()->{CalendarFixture f=new CalendarFixture(today);f.click(today,1,1000);f.key("RIGHT");f.key("LEFT");f.click(today,1,1100);ok(f.addCount.get()==0,"stale click sequence");});
            test("keyboard-bindings-scoped-to-date-grid",()->{CalendarFixture f=new CalendarFixture(today);ok(f.panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("RIGHT"))==null,"global key binding");JPanel days=(JPanel)field(f.panel,"days");ok(days.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("RIGHT")).equals("RIGHT"),"missing date scope");});
            test("keyboard-space-selects-date",()->{CalendarFixture f=new CalendarFixture(today);f.day(today.plusDays(1)).doClick();ok(f.selected.get().equals(today.plusDays(1))&&f.addCount.get()==0,"button action");});
            test("deadline-d-day",()->ok(SchedulePanel.deadlineStatus(deadline("today",today,false),today).contains("D-day"),"today status"));
            test("deadline-future-count",()->ok(SchedulePanel.deadlineStatus(deadline("later",today.plusDays(3),false),today).contains("D-3"),"future status"));
            test("deadline-overdue-count",()->ok(SchedulePanel.deadlineStatus(deadline("late",today.minusDays(2),false),today).equals("마감 2일 지남"),"past status"));
            test("deadline-completed-status",()->ok(SchedulePanel.deadlineStatus(deadline("done",today,true),today).equals("완료"),"done status"));
            test("single-panel-without-memo-input",()->{SchedulePanel p=new SchedulePanel(null,map,new PlannerModel());p.refresh(today,List.of(),List.of());ok(all(p.panel,JTextArea.class).isEmpty(),"memo editor remains");ok(p.panel.getComponentCount()==1,"separate bottom panel remains");});
            test("all-pending-deadlines-date-order-and-selected-completed",()->{
                PlannerModel m=new PlannerModel();m.putDeadline(deadline("future",today.plusDays(3),false));m.putDeadline(deadline("past",today.minusDays(2),false));m.putDeadline(deadline("today",today,false));m.putDeadline(deadline("done",today,true));m.putDeadline(deadline("other-done",today.plusDays(1),true));
                SchedulePanel p=new SchedulePanel(null,map,m);p.refresh(today,List.of(),List.of());
                List<String> names=all(p.panel,JButton.class).stream().map(JButton::getName).filter(Objects::nonNull).filter(n->n.startsWith("deadline:")).toList();
                ok(names.equals(List.of("deadline:past","deadline:today","deadline:future","deadline:done")),"wrong deadline order/filter: "+names);
            });
            test("countdown-based-on-today-not-selected-day",()->{PlannerModel m=new PlannerModel();m.putDeadline(deadline("next",today.plusDays(3),false));SchedulePanel p=new SchedulePanel(null,map,m);p.refresh(today.plusYears(1),List.of(),List.of());ok(all(p.panel,JButton.class).stream().anyMatch(b->b.getText().contains("D-3")),"countdown uses selected day");});
            test("empty-deadline-message",()->{SchedulePanel p=new SchedulePanel(null,map,new PlannerModel());p.refresh(today,List.of(),List.of());ok(all(p.panel,JLabel.class).stream().anyMatch(l->l.getText().contains("남은 과제·할일이 없습니다")),"no empty state");});
            test("refresh-preserves-stored-day-notes",()->{PlannerModel m=new PlannerModel();m.setDayNote(today,"existing note");SchedulePanel p=new SchedulePanel(null,map,m);p.refresh(today,List.of(),List.of());p.refresh(today.plusDays(1),List.of(),List.of());ok(m.dayNote(today).equals("existing note"),"note deleted");});
            test("completed-item-removed-from-pending-after-refresh",()->{PlannerModel m=new PlannerModel();m.putDeadline(deadline("item",today.plusDays(1),false));SchedulePanel p=new SchedulePanel(null,map,m);p.refresh(today,List.of(),List.of());m.putDeadline(deadline("item",today.plusDays(1),true));p.refresh(today,List.of(),List.of());ok(all(p.panel,JButton.class).stream().noneMatch(b->"deadline:item".equals(b.getName())),"completed remains pending");});
        });
        System.out.println("CALENDAR DEADLINE RESULT: "+passed+" passed, "+failed+" failed");System.exit(failed==0?0:1);
    }
}
