package campus.ui;

import campus.calendar.*;
import campus.map.*;
import campus.model.*;
import campus.model.PlannerModel.*;
import campus.model.PlannerModel.Event;
import campus.timetable.*;
import java.awt.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import javax.swing.*;

/** Common schedule entry regression tests; all state and folders are isolated. */
public final class ScheduleEntryWhiteBoxTest {
    interface Checked { void run() throws Exception; }
    static int passed, failed;
    static final LocalDate DATE = LocalDate.of(2026,11,22);
    static void ok(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    static void test(String name,Checked run) {
        try { run.run(); passed++; System.out.println("PASS "+name); }
        catch(Throwable e) { failed++; System.out.println("FAIL "+name); e.printStackTrace(System.out); }
    }
    static Object field(Object object,String name)throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    static <T> List<T> components(Container root,Class<T> type) {
        List<T> result=new ArrayList<>();
        for(Component child:root.getComponents()) {
            if(type.isInstance(child))result.add(type.cast(child));
            if(child instanceof Container container)result.addAll(components(container,type));
        }
        return result;
    }
    static JButton button(Container root,String text) {
        return components(root,JButton.class).stream().filter(b->text.equals(b.getText())).findFirst().orElseThrow();
    }
    static JRadioButton radio(JDialog dialog,String text) {
        return components(dialog,JRadioButton.class).stream().filter(b->text.equals(b.getText())).findFirst().orElseThrow();
    }
    static JComboBox<?> category(JDialog dialog) {
        return components(dialog,JComboBox.class).stream().filter(c->c.getItemCount()>0&&c.getItemAt(0) instanceof Category).findFirst().orElseThrow();
    }
    static JComboBox<?> place(JDialog dialog) {
        return components(dialog,JComboBox.class).stream().filter(c->{for(int i=0;i<c.getItemCount();i++)if(c.getItemAt(i) instanceof CampusMap.Building)return true;return false;}).findFirst().orElseThrow();
    }
    static JTextField date(JDialog dialog) {
        return components(dialog,JTextField.class).stream().filter(f->f.getText().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")).findFirst().orElseThrow();
    }
    static void dialog(Runnable open,Consumer<JDialog> check) throws Exception {
        AtomicReference<Throwable> failure=new AtomicReference<>();
        javax.swing.Timer timer=new javax.swing.Timer(120,e->{
            JDialog active=Arrays.stream(Window.getWindows()).filter(w->w instanceof JDialog&&w.isShowing()).map(w->(JDialog)w).findFirst().orElse(null);
            if(active==null) {failure.set(new AssertionError("registration dialog missing"));return;}
            try {check.accept(active);}catch(Throwable error){failure.set(error);}finally{active.dispose();}
        });
        timer.setRepeats(false);timer.start();
        try {open.run();}finally{timer.stop();}
        if(failure.get()!=null)throw new AssertionError("dialog assertion",failure.get());
    }
    public static void main(String[]args)throws Exception {
        CampusMap map=CampusMap.load(Path.of(args[0]));
        CampusMap.Building building=map.buildings().stream().filter(b->!b.name().replace(" ","").startsWith("이름없는")).findFirst().orElseThrow();
        Path temporary=Files.createTempDirectory("cnu-entry-check-");
        String previousHome=System.getProperty("campus.home");
        System.setProperty("campus.home",temporary.toString());
        AtomicReference<Throwable> fatal=new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(()->{
                MainFrame frame=null;
                try {
                    Theme.configureLookAndFeel();
                    PlannerModel model=new PlannerModel();
                    frame=new MainFrame(map,new RouteService(map),model,temporary.resolve("data/planner-state.xml"));
                    MainFrame host=frame;host.changeDate(DATE);host.selectBuilding(building);
                    SchedulePanel daily=(SchedulePanel)field(host,"daily");
                    PlacesPanel places=(PlacesPanel)field(host,"places");
                    TimetablePanel timetable=(TimetablePanel)field(host,"timetable");
                    test("one-add-button-next-to-date-even-when-empty",()->{
                        List<JButton> adds=components(daily.panel,JButton.class).stream().filter(b->b.getText()!=null&&b.getText().contains("일정 추가")).toList();
                        ok(adds.size()==1,"duplicate/absent add buttons");
                        Container row=adds.get(0).getParent();ok(components(row,JLabel.class).stream().anyMatch(l->l.getText().contains("11월 22일")),"not beside date");
                    });
                    test("left-actions-removed-favorite-preserved",()->{
                        List<String> labels=components(places.panel,JButton.class).stream().map(JButton::getText).toList();
                        ok(!labels.contains("이곳에 일정 추가")&&!labels.contains("이름 변경"),"old left actions remain");
                        ok(labels.stream().anyMatch(t->t.contains("즐겨찾기")),"favorite removed");
                    });
                    test("no-timetable-toolbar-add-button",()->ok(components(timetable,JButton.class).stream().noneMatch(b->"+ 일정 추가".equals(b.getText())),"old toolbar button"));
                    test("right-add-defaults-and-selected-place",()->dialog(()->button(daily.panel,"일정 추가").doClick(),d->{
                        ok(radio(d,"매주 반복").isSelected()&&!radio(d,"선택한 날짜만").isSelected(),"wrong repeat default");
                        ok(category(d).getSelectedItem()==Category.CLASS,"wrong category default");
                        ok(DATE.toString().equals(date(d).getText()),"wrong date");
                        ok(!date(d).isEnabled(),"repeat date field enabled");
                        ok(building.equals(place(d).getSelectedItem()),"selected place lost");
                    }));
                    test("common-entry-saves-one-off-and-restores-ui",()->dialog(()->button(daily.panel,"일정 추가").doClick(),d->{
                        radio(d,"선택한 날짜만").doClick();category(d).setSelectedItem(Category.GROUP);
                        components(d,JTextField.class).get(0).setText("Entry test");
                        ok(date(d).isEnabled(),"date not enabled");d.getRootPane().getDefaultButton().doClick();
                        ok(model.events().size()==1&&!model.events().get(0).recurring(),"once not saved");
                        ok(model.events().get(0).date().equals(DATE),"saved wrong date");
                    }));
                    test("no-extra-add-buttons-after-population",()->ok(components(daily.panel,JButton.class).stream().filter(b->b.getText()!=null&&b.getText().contains("일정 추가")).count()==1,"slot buttons remain"));
                    test("existing-one-off-retains-mode-category-date-time",()->dialog(()->timetable.editEvent(model.events().get(0)),d->{
                        ok(radio(d,"선택한 날짜만").isSelected()&&category(d).getSelectedItem()==Category.GROUP,"edit overwritten");
                        ok(date(d).getText().equals(DATE.toString()),"edit date changed");
                        List<TimePicker> times=components(d,TimePicker.class);ok(times.get(0).time().equals(LocalTime.of(9,0))&&times.get(1).time().equals(LocalTime.of(10,0)),"edit time changed");
                    }));
                    test("map-context-uses-common-entry",()->{
                        JPopupMenu menu=host.routeMenu(building);
                        JMenuItem add=Arrays.stream(menu.getComponents()).filter(c->c instanceof JMenuItem i&&"일정 추가".equals(i.getText())).map(c->(JMenuItem)c).findFirst().orElseThrow();
                        ok(Arrays.stream(menu.getComponents()).anyMatch(c->c instanceof JMenuItem i&&"이름 변경".equals(i.getText())),"map rename removed");
                        dialog(add::doClick,d->ok(radio(d,"매주 반복").isSelected()&&category(d).getSelectedItem()==Category.CLASS,"context defaults differ"));
                    });
                    test("cancel-does-not-create-another-event",()->ok(model.events().size()==1,"cancel mutated model"));
                    test("direct-grid-editor-defaults",()->{
                        Method edit=TimetablePanel.class.getDeclaredMethod("edit",Event.class,DayOfWeek.class,LocalTime.class,LocalTime.class);edit.setAccessible(true);
                        dialog(()->{try{edit.invoke(timetable,null,DayOfWeek.FRIDAY,LocalTime.of(14,30),LocalTime.of(16,5));}catch(Exception e){throw new RuntimeException(e);}},d->{
                            ok(radio(d,"매주 반복").isSelected()&&category(d).getSelectedItem()==Category.CLASS,"grid defaults differ");
                            List<TimePicker> times=components(d,TimePicker.class);ok(times.get(0).time().equals(LocalTime.of(14,30))&&times.get(1).time().equals(LocalTime.of(16,5)),"drag times lost");
                        });
                    });
                }catch(Throwable error){fatal.set(error);}
                finally {if(frame!=null) {try {((campus.folder.FolderModule)field(frame,"folders")).close();}catch(Exception e){fatal.set(e);}frame.dispose();}}
            });
        } finally {
            if(previousHome==null)System.clearProperty("campus.home");else System.setProperty("campus.home",previousHome);
        }
        if(fatal.get()!=null){failed++;fatal.get().printStackTrace(System.out);}
        System.out.println("ENTRY RESULT: "+passed+" passed, "+failed+" failed");System.exit(failed==0?0:1);
    }
}
