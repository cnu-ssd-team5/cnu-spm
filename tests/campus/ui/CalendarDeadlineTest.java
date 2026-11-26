package campus.ui;

import campus.calendar.*;
import campus.map.*;
import campus.model.*;
import campus.model.PlannerModel.*;
import campus.model.PlannerModel.Event;
import campus.service.DayPlanService.Leg;
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
        final AtomicReference<LocalDate> selected=new AtomicReference<>(),opened=new AtomicReference<>();
        final AtomicInteger openCount=new AtomicInteger();
        final CalendarPanel panel;
        CalendarFixture(LocalDate date){
            CalendarPanel[] holder=new CalendarPanel[1];
            panel=new CalendarPanel(model,d->{selected.set(d);holder[0].refresh(d);},d->{opened.set(d);openCount.incrementAndGet();});
            holder[0]=panel;selected.set(date);panel.refresh(date);
        }
        JButton day(LocalDate date){return all(panel,JButton.class).stream().filter(b->date.toString().equals(b.getName())).findFirst().orElseThrow();}
        void click(LocalDate date,int count,long when){JButton button=day(date);button.dispatchEvent(new MouseEvent(button,MouseEvent.MOUSE_CLICKED,when,0,4,4,count,false,MouseEvent.BUTTON1));}
        void key(String key)throws Exception{JPanel days=(JPanel)field(panel,"days");days.getActionMap().get(key).actionPerformed(new ActionEvent(days,0,key));}
    }
    static Deadline deadline(String id,LocalDate date,boolean done){return new Deadline(id,id,date,"memo",done,done?date:null);}
    static List<JButton> deadlineCards(DailyPlannerPanel planner) {
        return all(planner, JButton.class).stream()
                .filter(button -> button.getName() != null && button.getName().startsWith("deadline:")).toList();
    }
    public static void main(String[]args)throws Exception{
        CampusMap map=CampusMap.load(Path.of(args[0]));LocalDate today=PlannerTime.today();
        SwingUtilities.invokeAndWait(()->{
            test("calendar-add-toolbar-button-removed",()->{CalendarFixture f=new CalendarFixture(today);ok(all(f.panel,JButton.class).stream().noneMatch(b->b.getText().contains("할일 추가")),"old button remains");});
            test("single-date-click-selects-without-opening",()->{CalendarFixture f=new CalendarFixture(today);LocalDate d=today.plusDays(1);f.click(d,1,1000);ok(d.equals(f.selected.get())&&f.openCount.get()==0,"single click opened planner");});
            test("double-click-opens-exact-date-once",()->{CalendarFixture f=new CalendarFixture(today);f.click(today,1,1000);f.click(today,2,1100);ok(today.equals(f.opened.get())&&f.openCount.get()==1,"wrong double click");});
            test("double-click-survives-cell-rebuild",()->{CalendarFixture f=new CalendarFixture(today);JButton first=f.day(today);f.click(today,1,1000);ok(first!=f.day(today),"test did not rebuild");f.click(today,1,1100);ok(f.openCount.get()==1,"lost double click after rebuild");});
            test("different-dates-not-a-double-click",()->{CalendarFixture f=new CalendarFixture(today);f.click(today,1,1000);f.click(today.plusDays(1),1,1100);ok(f.openCount.get()==0,"different dates opened planner");});
            test("slow-clicks-not-a-double-click",()->{CalendarFixture f=new CalendarFixture(today);f.click(today,1,1000);f.click(today,1,4000);ok(f.openCount.get()==0,"slow click opened planner");});
            test("far-apart-clicks-not-a-double-click",()->{CalendarFixture f=new CalendarFixture(today);f.click(today,1,1000);JButton day=f.day(today);day.dispatchEvent(new MouseEvent(day,MouseEvent.MOUSE_CLICKED,1100,0,40,40,1,false,1));ok(f.openCount.get()==0,"far clicks opened planner");});
            test("blank-date-cell-double-click",()->{CalendarFixture f=new CalendarFixture(today);JPanel cell=(JPanel)f.day(today).getParent().getParent();cell.dispatchEvent(new MouseEvent(cell,MouseEvent.MOUSE_CLICKED,1000,0,8,8,2,false,1));ok(f.openCount.get()==1,"cell did not open planner");});
            test("calendar-deadline-label-opens-planner-without-editing", () -> {
                CalendarFixture f = new CalendarFixture(today);
                f.model.putDeadline(deadline("task", today, false));
                f.panel.refresh(today);
                ok(all(f.panel, JButton.class).stream().noneMatch(button -> button.getText().equals("task")),
                        "calendar still has a task edit button");
                JLabel label = all(f.panel, JLabel.class).stream().filter(item -> item.getText().equals("task"))
                        .findFirst().orElseThrow();
                label.dispatchEvent(new MouseEvent(label, MouseEvent.MOUSE_CLICKED, 1000, 0, 4, 4, 2, false, MouseEvent.BUTTON1));
                ok(today.equals(f.opened.get()) && f.openCount.get() == 1, "deadline label failed to open its date");
            });
            test("keyboard-left-crosses-year",()->{CalendarFixture f=new CalendarFixture(LocalDate.of(2027,1,1));f.key("LEFT");ok(f.selected.get().equals(LocalDate.of(2026,12,31)),"year boundary");});
            test("keyboard-right-crosses-month",()->{CalendarFixture f=new CalendarFixture(LocalDate.of(2026,11,30));f.key("RIGHT");ok(f.selected.get().equals(LocalDate.of(2026,12,1)),"month boundary");});
            test("keyboard-up-down-one-week",()->{CalendarFixture f=new CalendarFixture(today);f.key("UP");ok(f.selected.get().equals(today.minusWeeks(1)),"up");f.key("DOWN");ok(f.selected.get().equals(today),"down");});
            test("keyboard-leap-day",()->{CalendarFixture f=new CalendarFixture(LocalDate.of(2028,2,28));f.key("RIGHT");ok(f.selected.get().equals(LocalDate.of(2028,2,29)),"leap missing");});
            test("keyboard-enter-opens-selected-date",()->{CalendarFixture f=new CalendarFixture(today);f.key("RIGHT");f.key("openPlanner");ok(f.openCount.get()==1&&f.opened.get().equals(today.plusDays(1)),"enter date");});
            test("keyboard-enter-opens-focused-calendar-date", () -> {
                CalendarFixture f = new CalendarFixture(today);
                LocalDate focused = today.plusDays(1);
                JButton day = f.day(focused);
                Object action = day.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER"));
                day.getActionMap().get(action).actionPerformed(new ActionEvent(day, 0, "enter"));
                ok(focused.equals(f.opened.get()) && f.openCount.get() == 1, "focused day did not open");
            });
            test("keyboard-navigation-breaks-double-click-sequence",()->{CalendarFixture f=new CalendarFixture(today);f.click(today,1,1000);f.key("RIGHT");f.key("LEFT");f.click(today,1,1100);ok(f.openCount.get()==0,"stale click sequence");});
            test("keyboard-bindings-scoped-to-date-grid",()->{CalendarFixture f=new CalendarFixture(today);ok(f.panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke("RIGHT"))==null,"global key binding");JPanel days=(JPanel)field(f.panel,"days");ok(days.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("RIGHT")).equals("RIGHT"),"missing date scope");});
            test("keyboard-space-selects-date",()->{CalendarFixture f=new CalendarFixture(today);f.day(today.plusDays(1)).doClick();ok(f.selected.get().equals(today.plusDays(1))&&f.openCount.get()==0,"button action");});
            test("calendar-month-navigation-clamps-and-crosses-year",()->{
                CalendarFixture f=new CalendarFixture(LocalDate.of(2026,12,31));
                JButton next=all(f.panel,JButton.class).stream().filter(b->"다음 달".equals(b.getToolTipText())).findFirst().orElseThrow();
                next.doClick();
                ok(f.selected.get().equals(LocalDate.of(2027,1,31)),"next month didn't cross the year");
                next.doClick();
                ok(f.selected.get().equals(LocalDate.of(2027,2,28)),"next month didn't clamp the date");
                ok(((JButton)field(f.panel,"monthSelector")).getText().equals("2027년 2월"),"month label contains unrendered glyph or did not update");
            });
            test("calendar-month-picker-applies-only-valid-year",()->{
                CalendarFixture f=new CalendarFixture(LocalDate.of(2026,1,31));
                JTextField year=(JTextField)field(f.panel,"year");
                JPopupMenu picker=(JPopupMenu)field(f.panel,"monthPicker");
                JButton february=all(picker,JButton.class).stream().filter(b->"2월".equals(b.getText())).findFirst().orElseThrow();
                year.setText("10000");
                february.doClick();
                ok(f.selected.get().equals(LocalDate.of(2026,1,31)),"invalid year changed the date");
                year.setText("2028");
                february.doClick();
                ok(f.selected.get().equals(LocalDate.of(2028,2,29)),"year and month selection lost leap day");
                year.setText("2029");
                year.postActionEvent();
                ok(f.selected.get().equals(LocalDate.of(2029,2,28)),"Enter didn't apply the year to the current month");
            });
            test("checklist-xml-and-order-with-optional-deadlines", () -> {
                LocalDate due = today.plusDays(5);
                PlannerModel original = new PlannerModel();
                original.putDeadline(new Deadline("first", "산책", today, "산책로 한 바퀴", false, null,
                        TaskPriority.LOW, today, false));
                original.putDeadline(new Deadline("second", "과제", due, "", false, null, TaskPriority.HIGH, today, true));
                original.putDeadline(deadline("legacy", today, false));
                original.toggleDeadline("first");
                List<Deadline> tasks = original.plannerTasks(today);
                ok(tasks.stream().map(Deadline::id).toList().equals(List.of("first", "second", "legacy")),
                        "toggling completion changed planner insertion order");
                ok(tasks.get(0).completed() && !tasks.get(0).deadlineLinked(), "unlinked task lost completion");
                ok(original.deadlines(today).stream().map(Deadline::id).toList().equals(List.of("legacy"))
                        && original.deadlines(due).stream().map(Deadline::id).toList().equals(List.of("second")),
                        "deadline list must include only linked tasks on their due dates");
                Path file = Files.createTempFile("cnu-checklist-", ".xml");
                try {
                    PlannerStore.save(file, original);
                    PlannerModel restored = PlannerStore.load(file);
                    ok(restored.plannerTasks(today).equals(tasks), "saved checklist lost order, date, or linkage");
                    PlannerModel imported = new PlannerModel();
                    imported.mergeFrom(restored);
                    imported.mergeFrom(restored);
                    ok(imported.plannerTasks(today).equals(tasks), "merging checklist lost or duplicated tasks");
                } finally {
                    Files.deleteIfExists(file);
                }
            });
            test("single-panel-without-memo-input",()->{SchedulePanel p=new SchedulePanel(null,map,new PlannerModel());p.refresh(today,List.of(),List.of());ok(all(p.panel,JTextArea.class).isEmpty(),"memo editor remains");ok(p.panel.getComponentCount()==1,"separate bottom panel remains");});
            test("deadline-list-moves-to-planner-and-leaves-sidebar", () -> {
                PlannerModel model = new PlannerModel();
                model.putDeadline(deadline("item", today, false));
                DailyPlannerPanel planner = new DailyPlannerPanel(null, model);
                planner.refresh(today);
                ok(all(planner, JLabel.class).stream().anyMatch(label -> label.getText().equals("과제·할일 마감"))
                        && deadlineCards(planner).size() == 1, "planner deadline area missing");
                SchedulePanel sidebar = new SchedulePanel(null, map, model);
                sidebar.refresh(today, List.of(), List.of());
                ok(all(sidebar.panel, JLabel.class).stream().noneMatch(label -> label.getText().contains("할일"))
                        && all(sidebar.panel, JButton.class).stream().noneMatch(button -> "deadline:item".equals(button.getName())),
                        "deadline content remains in schedule sidebar");
            });
            test("linked-deadline-visible-from-planner-day-through-due-date", () -> {
                LocalDate due = today.plusDays(3);
                PlannerModel model = new PlannerModel();
                model.putDeadline(new Deadline("item", "과제", due, "", false, null, TaskPriority.NORMAL, today, true));
                DailyPlannerPanel planner = new DailyPlannerPanel(null, model);
                planner.refresh(today.minusDays(1));
                ok(deadlineCards(planner).isEmpty(), "deadline shown before its planner day");
                planner.refresh(today);
                ok(deadlineCards(planner).get(0).getText().contains("D-3"), "deadline missing on its planner day");
                planner.refresh(due.minusDays(1));
                ok(deadlineCards(planner).get(0).getText().contains("D-1"), "countdown ignored the displayed date");
                planner.refresh(due);
                ok(deadlineCards(planner).get(0).getText().contains("D-day"), "deadline missing on its due date");
                planner.refresh(due.plusDays(1));
                ok(deadlineCards(planner).isEmpty() && all(planner, JLabel.class).stream()
                        .anyMatch(label -> label.getText().equals("표시할 마감이 없습니다.")), "expired deadline still shown");
            });
            test("planner-deadlines-sort-by-due-date-and-exclude-unlinked-tasks", () -> {
                PlannerModel model = new PlannerModel();
                model.putDeadline(new Deadline("later", "나중 항목", today.plusDays(3), "", false, null, TaskPriority.HIGH, today, true));
                model.putDeadline(new Deadline("unlinked", "체크리스트 전용", today, "", false, null, TaskPriority.NORMAL, today, false));
                model.putDeadline(deadline("soon", today, false));
                DailyPlannerPanel planner = new DailyPlannerPanel(null, model);
                planner.refresh(today);
                ok(deadlineCards(planner).stream().map(JButton::getName).toList().equals(List.of("deadline:soon", "deadline:later")),
                        "deadline rows include unlinked tasks or are out of order");
            });
            test("refresh-preserves-stored-day-notes",()->{PlannerModel m=new PlannerModel();m.setDayNote(today,"existing note");SchedulePanel p=new SchedulePanel(null,map,m);p.refresh(today,List.of(),List.of());p.refresh(today.plusDays(1),List.of(),List.of());ok(m.dayNote(today).equals("existing note"),"note deleted");});
            test("sidebar-walking-summary-and-unmapped-appointments", () -> {
                PlannerModel model = new PlannerModel();
                String building = map.buildings().get(0).id();
                Event a = new Event("a", "첫 일정", building, LocalTime.of(9,0), LocalTime.of(10,0), Category.CLASS, null, today, "");
                Event b = new Event("b", "두 번째 일정", building, LocalTime.of(11,0), LocalTime.of(12,0), Category.CLASS, null, today, "");
                Event remote = new Event("c", "화상 일정", "", LocalTime.of(13,0), LocalTime.of(14,0),
                        Category.OTHER, null, today, "", "화상 회의", "온라인");
                Occurrence first = new Occurrence(a, today, false);
                Occurrence second = new Occurrence(b, today, false);
                Occurrence third = new Occurrence(remote, today, false);
                Leg walk = new Leg(first, second, new RouteService.Route(List.of(), 173, true, "일부 연결 추정"));
                Leg noWalk = new Leg(second, third, new RouteService.Route(List.of(), 0, false, "장소 없음"));
                SchedulePanel sidebar = new SchedulePanel(null, map, model);
                sidebar.refresh(today, List.of(first, second, third), List.of(walk, noWalk));
                List<String> labels = all(sidebar.panel, JLabel.class).stream().map(JLabel::getText).toList();
                ok(labels.contains("↓  도보 약 3분 · 173 m"), "incorrect walking summary");
                ok(labels.stream().noneMatch(text -> text.contains("여유") || text.contains("추정") || text.contains("경로를 찾지")), "extra route lines");
                ok(labels.contains("온라인") && labels.contains("화상 회의 · 이 날짜만"), "direct input missing from sidebar");
            });
            test("sidebar-shows-midnight-end-as-24", () -> {
                Event late = new Event("late", "야간 일정", map.buildings().get(0).id(), LocalTime.of(23,0), LocalTime.MAX,
                        Category.OTHER, null, today, "");
                SchedulePanel sidebar = new SchedulePanel(null, map, new PlannerModel());
                sidebar.refresh(today, List.of(new Occurrence(late, today, false)), List.of());
                List<String> labels = all(sidebar.panel, JLabel.class).stream().map(JLabel::getText).toList();
                ok(labels.contains("1  23:00 – 24:00") && labels.stream().noneMatch(text -> text.contains("23:59")),
                        "midnight end shown as " + labels);
            });
            test("completed-deadline-keeps-its-due-day-status-only", () -> {
                LocalDate due = today.plusDays(1);
                PlannerModel model = new PlannerModel();
                model.putDeadline(new Deadline("item", "완료 확인", due, "", false, null, TaskPriority.NORMAL, today, true));
                DailyPlannerPanel planner = new DailyPlannerPanel(null, model);
                planner.refresh(today);
                ok(deadlineCards(planner).size() == 1, "pending item absent");
                model.toggleDeadline("item");
                planner.refresh(today);
                ok(deadlineCards(planner).isEmpty(), "completed item remains pending");
                planner.refresh(due);
                ok(deadlineCards(planner).get(0).getText().contains("완료"), "completed due-day status lost");
                planner.refresh(due.plusDays(1));
                ok(deadlineCards(planner).isEmpty(), "completed item remains after due date");
            });
        });
        System.out.println("CALENDAR DEADLINE RESULT: "+passed+" passed, "+failed+" failed");System.exit(failed==0?0:1);
    }
}
