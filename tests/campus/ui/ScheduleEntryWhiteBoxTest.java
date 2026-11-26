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
    static PlacePicker place(JDialog dialog) {
        return components(dialog,PlacePicker.class).stream().findFirst().orElseThrow();
    }
    static JTextField date(JDialog dialog) {
        return components(dialog,JTextField.class).stream().filter(f->f.getText().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")).findFirst().orElseThrow();
    }
    static JTextField directPlace(JDialog dialog) {
        return (JTextField) place(dialog).getEditor().getEditorComponent();
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
                    test("removed-branding-not-in-main-ui",()->ok(components(host,JLabel.class).stream().noneMatch(l->l.getText()!=null&&(l.getText().equals("CNU")||l.getText().contains("나의 캠퍼스")||l.getText().contains("수업부터 약속까지"))),"branding remains"));
                    test("removed-footer-text-not-in-main-ui",()->ok(components(host,JLabel.class).stream().noneMatch(l->l.getText()!=null&&l.getText().contains("보행 속도 1.3")),"old footer remains"));
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
                        ok(model.events().get(0).buildingId().equals(building.id()),"selected campus ID lost");
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
                    test("map-route-switch-toggles-schedule-lines",()->{
                        JToggleButton routes=components(host,JToggleButton.class).stream().filter(b->"경로 표시".equals(b.getText())).findFirst().orElseThrow();
                        MapPanel shown=(MapPanel)field(host,"map");JLabel legend=(JLabel)field(host,"mapLegend");
                        ok(routes instanceof ToggleSwitch&&routes.isSelected()&&shown.scheduleRoutesVisible(),"routes hidden by default");
                        routes.doClick();ok(!shown.scheduleRoutesVisible()&&!legend.getText().contains("도보 경로"),"switch did not hide routes");
                        routes.doClick();ok(shown.scheduleRoutesVisible()&&legend.getText().contains("도보 경로"),"switch did not restore routes");
                    });
                    test("cancel-does-not-create-another-event",()->ok(model.events().size()==1,"cancel mutated model"));
                    test("direct-grid-editor-defaults",()->{
                        Method edit=TimetablePanel.class.getDeclaredMethod("edit",Event.class,DayOfWeek.class,LocalTime.class,LocalTime.class);edit.setAccessible(true);
                        dialog(()->{try{edit.invoke(timetable,null,DayOfWeek.FRIDAY,LocalTime.of(14,30),LocalTime.of(16,5));}catch(Exception e){throw new RuntimeException(e);}},d->{
                            ok(radio(d,"매주 반복").isSelected()&&category(d).getSelectedItem()==Category.CLASS,"grid defaults differ");
                            List<TimePicker> times=components(d,TimePicker.class);ok(times.get(0).time().equals(LocalTime.of(14,30))&&times.get(1).time().equals(LocalTime.of(16,5)),"drag times lost");
                        });
                    });
                    test("custom-appointment-saves-and-reopens-in-daily-editor",()->{
                        dialog(()->button(daily.panel,"일정 추가").doClick(),d->{
                            radio(d,"선택한 날짜만").doClick();
                            components(d,JTextField.class).get(0).setText("화상 상담");
                            ((JTextField) category(d).getEditor().getEditorComponent()).setText("상담");
                            directPlace(d).setText("온라인 · 링크는 메모");
                            List<TimePicker> times=components(d,TimePicker.class);
                            times.get(0).setTime(LocalTime.of(11,0));times.get(1).setTime(LocalTime.of(12,0));
                            d.getRootPane().getDefaultButton().doClick();
                        });
                        Event external = model.events().stream().filter(e -> e.title().equals("화상 상담")).findFirst().orElseThrow();
                        ok(external.categoryLabel().equals("상담") && external.category() == Category.OTHER, "custom kind lost");
                        ok(external.customPlace().equals("온라인 · 링크는 메모") && map.building(external.buildingId()) == null,
                                "custom place was routed through the selected campus building");
                        dialog(()->host.edit(external,false),d->{
                            ok(((JTextField) category(d).getEditor().getEditorComponent()).getText().equals("상담"),"custom kind lost on edit");
                            ok(directPlace(d).getText().equals(external.customPlace()),"custom place lost on edit");
                            d.getRootPane().getDefaultButton().doClick();
                        });
                        ok(model.events().contains(external),"saving edit changed the custom event");
                    });
                    test("custom-repeating-event-roundtrips-through-shared-xml",()->{
                        dialog(()->button(daily.panel,"일정 추가").doClick(),d->{
                            components(d,JTextField.class).get(0).setText("학외 회의");
                            ((JTextField) category(d).getEditor().getEditorComponent()).setText("외부 업무");
                            directPlace(d).setText("광주광역시청");
                            List<TimePicker> times=components(d,TimePicker.class);
                            times.get(0).setTime(LocalTime.of(13,0));times.get(1).setTime(LocalTime.of(14,0));
                            d.getRootPane().getDefaultButton().doClick();
                        });
                        Event repeat = model.events().stream().filter(e -> e.title().equals("학외 회의")).findFirst().orElseThrow();
                        ok(repeat.recurring() && repeat.categoryLabel().equals("외부 업무")
                                && repeat.customPlace().equals("광주광역시청"), "custom recurring event lost");
                        PlannerModel saved = PlannerStore.load(temporary.resolve("data/planner-state.xml"));
                        ok(saved.events().contains(repeat),"personal XML lost event details");
                        Path shared = temporary.resolve("share.xml");
                        PlannerStore.saveSharedSchedule(shared,model);
                        PlannerModel recipient = new PlannerModel();
                        recipient.mergeFrom(PlannerStore.load(shared));
                        recipient.mergeFrom(PlannerStore.load(shared));
                        ok(recipient.events().containsAll(model.events()) && recipient.events().size() == model.events().size(),
                                "share changed custom details or duplicated them");
                    });
                    test("place-picker-disambiguates-duplicate-selected-buildings", () -> {
                        CampusMap.Building first = new CampusMap.Building("pin:picker-first","검사 장소",List.of(),building.center());
                        CampusMap.Building second = new CampusMap.Building("pin:picker-second","검사 장소",List.of(),building.center());
                        map.putPlace(first);
                        map.putPlace(second);
                        try {
                            JTextField search = new JTextField();
                            PlacePicker picker = new PlacePicker(map, model, search, first, "");
                            search.setText("검사 장소");
                            ok(picker.selection().buildingId().equals(first.id()), "filter lost the selected location");
                            picker.setSelectedItem(second);
                            ok(picker.selection().buildingId().equals(second.id()), "identical label lost the selected ID");
                            ((JTextField) picker.getEditor().getEditorComponent()).setText("교외 실습실");
                            search.setText("없는 이름");
                            ok(picker.selection().customPlace().equals("교외 실습실"), "filter erased typed location");
                        } finally {
                            map.removePlace(first.id());
                            map.removePlace(second.id());
                        }
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
