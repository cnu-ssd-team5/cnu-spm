package campus.ui;
import campus.calendar.*;
import campus.folder.*;
import campus.folder.storage.*;
import campus.folder.ui.*;
import campus.map.*;
import campus.materials.*;
import campus.model.*;
import campus.model.PlannerModel.*;
import campus.model.PlannerModel.Event;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import javax.swing.*;

public final class WorkspaceMaterialsTest {
 interface Checked{void run()throws Exception;}
 static int passed,failed;
 static void test(String n,Checked c){try{c.run();passed++;System.out.println("PASS "+n);}catch(Throwable e){failed++;System.out.println("FAIL "+n);e.printStackTrace(System.out);}}
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 static Object f(Object o,String n)throws Exception{return ScheduleEntryWhiteBoxTest.field(o,n);}
 public static void main(String[]args)throws Exception{
  CampusMap map=CampusMap.load(Path.of(args[0]));var building=map.buildings().stream().filter(b->!b.name().replace(" ","").startsWith("이름없는")).findFirst().orElseThrow();
  Path temp=Files.createTempDirectory("cnu-workspace-");String old=System.getProperty("campus.home");System.setProperty("campus.home",temp.toString());
  PlannerModel model=new PlannerModel();Semester fall=new Semester(2026,Semester.Season.FALL),spring=new Semester(2026,Semester.Season.SPRING);
  Event course=new Event("fall","Test course",building.id(),LocalTime.of(9,0),LocalTime.of(10,0),Category.CLASS,DayOfWeek.MONDAY,null,"");model.putEvent(course,fall);
  Event other=new Event("spring",course.title(),building.id(),course.start(),course.end(),course.category(),course.weekday(),null,"");model.putEvent(other,spring);
  AtomicReference<FolderModule> module=new AtomicReference<>();AtomicReference<Path> destination=new AtomicReference<>();
  try{
   SwingUtilities.invokeAndWait(()->{
    MainFrame frame=null;
    try{
     frame=new MainFrame(map,new RouteService(map),model,temp.resolve("data/state.xml"));MainFrame host=frame;FolderModule folders=(FolderModule)f(host,"folders");module.set(folders);
     test("workspace-expand-hides-only-side-and-week-panels",()->{LocalDate date=host.selectedDate();var toggle=MainFrame.class.getDeclaredMethod("toggleWorkspace");toggle.setAccessible(true);toggle.invoke(host);ok(!((PlacesPanel)f(host,"places")).panel.isVisible()&&!((SchedulePanel)f(host,"daily")).panel.isVisible()&&!((WeekBar)f(host,"week")).panel.isVisible(),"panels visible");ok(host.selectedDate().equals(date),"date changed");});
     test("workspace-persists-through-view-switch",()->{var show=MainFrame.class.getDeclaredMethod("showView",String.class);show.setAccessible(true);for(String view:List.of("지도","주간시간표","달력","과목자료")){show.invoke(host,view);ok((boolean)f(host,"workspaceExpanded"),"lost expansion");}host.setSize(1130,680);host.validate();});
     test("workspace-repeat-toggle-restores-state",()->{var toggle=MainFrame.class.getDeclaredMethod("toggleWorkspace");toggle.setAccessible(true);toggle.invoke(host);for(int i=0;i<40;i++)toggle.invoke(host);ok(((PlacesPanel)f(host,"places")).panel.isVisible()&&((SchedulePanel)f(host,"daily")).panel.isVisible()&&((WeekBar)f(host,"week")).panel.isVisible(),"not restored");});
     test("folder-existence-icon-visible-without-recent-file",()->{JButton icon=folders.recentButton(building);ok(icon.isVisible()&&icon.getIcon()!=null&&icon.getToolTipText().contains("자료함"),"folder icon missing");});
     test("folder-icon-small-until-hover",()->{JButton icon=folders.recentButton(building);ok(icon.getIcon().getIconWidth()==12&&!icon.isBorderPainted()&&!icon.isContentAreaFilled(),"icon not small and flat");icon.getModel().setRollover(true);ok(icon.getIcon().getIconWidth()==24,"icon not enlarged on hover");icon.getModel().setRollover(false);ok(icon.getIcon().getIconWidth()==12,"icon not restored");});
     test("folder-icon-stays-inside-building-top-right",()->{var l=new java.awt.geom.Path2D.Double();l.moveTo(0,0);l.lineTo(40,0);l.lineTo(40,20);l.lineTo(100,20);l.lineTo(100,100);l.lineTo(0,100);l.closePath();var c=FolderModule.iconCenter(l,new java.awt.geom.Point2D.Double(50,50));ok(l.contains(c.x-7,c.y-7,14,14),"icon outside building "+c);ok(c.x>=90&&c.y<=30,"not near top-right "+c);var tiny=new java.awt.geom.Path2D.Double();tiny.moveTo(0,0);tiny.lineTo(5,0);tiny.lineTo(5,5);tiny.closePath();ok(FolderModule.iconCenter(tiny,new java.awt.geom.Point2D.Double(3,2)).equals(new Point(3,2)),"tiny building should use center");});
     test("folder-overlay-refresh-safe-after-pin-removal",()->{var pin=new CampusMap.Building("pin:qa","Pin",List.of(building.center()),building.center());map.putPlace(pin);folders.recentButton(pin);map.removePlace(pin.id());folders.refreshRecent();});
     test("course-directory-resolves-selected-semester",()->{Path a=folders.courseDirectory(course),b=folders.courseDirectory(other);ok(!a.equals(b)&&a.toString().contains(fall.folderName())&&b.toString().contains(spring.folderName()),"wrong semester");destination.set(a);});
     test("materials-original-directory-buttons-and-drop-target",()->{CourseMaterialsPanel materials=new CourseMaterialsPanel(model,map,folders);materials.refresh(fall);ok(ScheduleEntryWhiteBoxTest.components(materials,JButton.class).stream().anyMatch(b->"원본 디렉터리 열기".equals(b.getText())&&b.isEnabled()),"directory button missing");ok(ScheduleEntryWhiteBoxTest.components(materials,JButton.class).stream().anyMatch(b->b.getTransferHandler() instanceof CourseFileTransferHandler),"drop missing");});
     test("folder-menu-has-original-folder-action",()->{FolderService service=new FolderService(temp.resolve("data/buildings"));CourseCatalog catalog=new CourseCatalog(map,model);BuildingFolderController controller=new BuildingFolderController(service,catalog);Path path=service.getRoot().resolve(catalog.storageKey(building.id()));Files.createDirectories(path);MiniFolderPopup popup=new MiniFolderPopup(null,controller,service,path,()->{});try{var method=MiniFolderPopup.class.getDeclaredMethod("contextMenu");method.setAccessible(true);JPopupMenu menu=(JPopupMenu)method.invoke(popup);ok(Arrays.stream(menu.getComponents()).anyMatch(c->c instanceof JMenuItem i&&"원본 폴더 열기".equals(i.getText())),"menu missing");}finally{popup.dispose();controller.close();}});
     test("original-folder-rejects-missing-directory",()->{try{FolderService.openDirectory(temp.resolve("missing"));throw new AssertionError("missing accepted");}catch(IOException expected){}});
     test("arrows-cycle-workspace-with-planner-after-map", () -> {
      var show=MainFrame.class.getDeclaredMethod("showView",String.class);show.setAccessible(true);show.invoke(host,"지도");
      JButton next=(JButton)f(host,"nextView"), previous=(JButton)f(host,"previousView");
      for(String expected:List.of("플래너","주간시간표","달력","과목자료","지도")) {
       next.doClick(0);ok(expected.equals(f(host,"activeView")),"wrong next page");
      }
      previous.doClick(0);ok("과목자료".equals(f(host,"activeView")),"reverse wrap missed materials");
     });
     test("calendar-opens-its-date-and-planner-shortcuts-open-today", () -> {
      LocalDate today=PlannerTime.today(), plannerDay=today.minusDays(9);
      var tabs=(java.util.Map<?,?>)f(host,"viewButtons");
      var planner=(JButton)tabs.get("플래너");
      var calendar=(JButton)tabs.get("달력");
      host.changeDate(plannerDay);
      planner.doClick(0);
      ok(today.equals(f(host,"plannerDate")),"planner tab should open today");
      calendar.doClick(0);
      CalendarPanel calendarPanel=(CalendarPanel)f(host,"calendar");
      var days=(JPanel)f(calendarPanel,"days");
      Object key=days.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER"));
      days.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(days,0,""));
      ok("플래너".equals(f(host,"activeView"))&&plannerDay.equals(f(host,"plannerDate")),"calendar Enter should open its date");
      host.changeDate(plannerDay.minusDays(1));
      ok(plannerDay.equals(f(host,"plannerDate")),"browsing another week should not change planner date");
      var previous=(JButton)f(host,"previousView");
      var next=(JButton)f(host,"nextView");
      previous.doClick(0);
      next.doClick(0);
      ok(today.equals(f(host,"plannerDate")),"arrows should open today's planner");
     });
     test("top-date-click-double-click-and-enter-open-the-right-view", () -> {
      LocalDate sunday=LocalDate.of(2026,11,15);
      var tabs=(java.util.Map<?,?>)f(host,"viewButtons");
      ((JButton)tabs.get("지도")).doClick(0);
      host.changeDate(sunday);
      JPanel days=(JPanel)f(f(host,"week"),"weekDays");
      JButton monday=(JButton)days.getComponent(1);
      monday.doClick(0);
      ok("지도".equals(f(host,"activeView"))&&host.selectedDate().equals(sunday.plusDays(1)),
              "single click outside planner should only select the date");
      ok(days.getComponent(1)==monday,"refresh replaced the date and broke double-click/focus");
      monday.dispatchEvent(new java.awt.event.MouseEvent(monday,java.awt.event.MouseEvent.MOUSE_CLICKED,
              200,0,3,3,2,false,java.awt.event.MouseEvent.BUTTON1));
      ok("플래너".equals(f(host,"activeView"))&&sunday.plusDays(1).equals(f(host,"plannerDate")),
              "double-click did not open the date's planner");
      JButton tuesday=(JButton)days.getComponent(2);
      tuesday.doClick(0);
      ok(sunday.plusDays(2).equals(f(host,"plannerDate"))&&host.selectedDate().equals(sunday.plusDays(2)),
              "single click in planner did not update the planner date");
      ((JButton)tabs.get("지도")).doClick(0);
      JButton wednesday=(JButton)days.getComponent(3);
      Object key=wednesday.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ENTER"));
      wednesday.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(wednesday,0,""));
      ok("플래너".equals(f(host,"activeView"))&&sunday.plusDays(3).equals(f(host,"plannerDate"))
              &&host.selectedDate().equals(sunday.plusDays(3)),"Enter did not select and open the exact date");
     });
     test("planner-checklist-keeps-insertion-order-and-dated-notes", () -> {
      LocalDate date=PlannerTime.today();
      var openPlanner=MainFrame.class.getDeclaredMethod("openPlanner",LocalDate.class);openPlanner.setAccessible(true);
      model.putDeadline(new Deadline("daily-low","일반 읽기",date,"",false,null,TaskPriority.LOW,date,false));
      model.putDeadline(new Deadline("daily-high","먼저 읽기",date,"",false,null,TaskPriority.HIGH,date,false));
      model.putDeadline(new Deadline("deadline-today","마감 항목",date,"",false));
      model.putDeadline(new Deadline("daily-future","다른 날짜",date.plusDays(1),"",false));
      DailyPlannerPanel panel=(DailyPlannerPanel)f(host,"dailyPlanner");
      openPlanner.invoke(host,date);
      var tasks=ScheduleEntryWhiteBoxTest.components(panel,JCheckBox.class).stream().filter(b->b.getName()!=null).toList();
      ok(tasks.stream().map(JCheckBox::getName).toList().equals(List.of("daily-task:daily-low","daily-task:daily-high")),
              "checklist did not preserve insertion order or date scope");
      JTextArea memo=(JTextArea)f(panel,"memo");
      memo.setText("오늘 메모");
      tasks.get(1).doClick(0);
      ok(ScheduleEntryWhiteBoxTest.components(panel,JCheckBox.class).get(1).isSelected(),"completion moved the task");
      openPlanner.invoke(host,date.plusDays(1));
      ok(memo.getText().isEmpty(),"previous note leaked to next day");
      openPlanner.invoke(host,date);
      ok(memo.getText().equals("오늘 메모"),"dated memo not restored");
      PlannerModel saved=PlannerStore.load(temp.resolve("data/state.xml"));
      Deadline completed=saved.plannerTasks(date).stream().filter(d->d.id().equals("daily-high")).findFirst().orElseThrow();
      ok(completed.completed() && completed.priority()==TaskPriority.HIGH && saved.dayNote(date).equals("오늘 메모"),
              "completion, priority or note did not survive save/reopen");
     });
    }catch(Exception e){failed++;e.printStackTrace(System.out);}finally{if(frame!=null){try{((FolderModule)f(frame,"folders")).close();}catch(Exception e){failed++;}frame.dispose();MainFrame disposed=frame;test("main-dispose-stops-clock-and-reminder-timers",()->{ok(!((javax.swing.Timer)f(disposed,"reminderTimer")).isRunning(),"reminder timer leaked");Object tools=f(disposed,"timeTools");ok(!((javax.swing.Timer)f(tools,"ticker")).isRunning(),"clock ticker leaked");});}}
   });
   // A separate module exercises the real asynchronous move path without any user data.
   SwingUtilities.invokeAndWait(()->{try{module.set(new FolderModule(temp.resolve("drop-data"),map,model));}catch(Exception e){throw new RuntimeException(e);}});
   Path left=temp.resolve("external-a/notes.txt"),right=temp.resolve("external-b/notes.txt");Files.createDirectories(left.getParent());Files.createDirectories(right.getParent());Files.writeString(left,"first");Files.writeString(right,"second");
   SwingUtilities.invokeAndWait(()->module.get().moveIntoCourse(new JPanel(),course,List.of(left.toFile(),right.toFile())));
   long limit=System.nanoTime()+8_000_000_000L;AtomicBoolean busy=new AtomicBoolean(true);while(busy.get()&&System.nanoTime()<limit){SwingUtilities.invokeAndWait(()->busy.set(module.get().isBusy()));if(busy.get())Thread.sleep(20);}
   test("course-drop-completes-without-stuck-busy-state",()->ok(!busy.get(),"move stuck"));
   test("course-drop-preserves-both-same-name-files",()->{Path dest=module.get().courseDirectory(course);ok(Files.readString(dest.resolve("notes.txt")).equals("first")&&Files.readString(dest.resolve("notes (1).txt")).equals("second"),"data lost");});
   test("course-drop-moves-source-and-keeps-other-semester-empty",()->{ok(!Files.exists(left)&&!Files.exists(right),"source not moved");try(var stream=Files.list(module.get().courseDirectory(other))){ok(stream.findAny().isEmpty(),"wrong semester got files");}});
  }finally{SwingUtilities.invokeAndWait(()->{try{if(module.get()!=null)module.get().close();}catch(Exception e){failed++;}});if(old==null)System.clearProperty("campus.home");else System.setProperty("campus.home",old);}
  System.out.println("WORKSPACE MATERIALS RESULT: "+passed+" passed, "+failed+" failed");System.exit(failed==0?0:1);
 }
}
