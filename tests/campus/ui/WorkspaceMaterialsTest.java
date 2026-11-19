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
     test("folder-overlay-refresh-safe-after-pin-removal",()->{var pin=new CampusMap.Building("pin:qa","Pin",List.of(building.center()),building.center());map.putPlace(pin);folders.recentButton(pin);map.removePlace(pin.id());folders.refreshRecent();});
     test("course-directory-resolves-selected-semester",()->{Path a=folders.courseDirectory(course),b=folders.courseDirectory(other);ok(!a.equals(b)&&a.toString().contains(fall.folderName())&&b.toString().contains(spring.folderName()),"wrong semester");destination.set(a);});
     test("materials-original-directory-buttons-and-drop-target",()->{CourseMaterialsPanel materials=new CourseMaterialsPanel(model,map,folders);materials.refresh(fall);ok(ScheduleEntryWhiteBoxTest.components(materials,JButton.class).stream().anyMatch(b->"원본 디렉터리 열기".equals(b.getText())&&b.isEnabled()),"directory button missing");ok(ScheduleEntryWhiteBoxTest.components(materials,JButton.class).stream().anyMatch(b->b.getTransferHandler() instanceof CourseFileTransferHandler),"drop missing");});
     test("folder-menu-has-original-folder-action",()->{FolderService service=new FolderService(temp.resolve("data/buildings"));CourseCatalog catalog=new CourseCatalog(map,model);BuildingFolderController controller=new BuildingFolderController(service,catalog);Path path=service.getRoot().resolve(catalog.storageKey(building.id()));Files.createDirectories(path);MiniFolderPopup popup=new MiniFolderPopup(null,controller,service,path,()->{});try{var method=MiniFolderPopup.class.getDeclaredMethod("contextMenu");method.setAccessible(true);JPopupMenu menu=(JPopupMenu)method.invoke(popup);ok(Arrays.stream(menu.getComponents()).anyMatch(c->c instanceof JMenuItem i&&"원본 폴더 열기".equals(i.getText())),"menu missing");}finally{popup.dispose();controller.close();}});
     test("original-folder-rejects-missing-directory",()->{try{FolderService.openDirectory(temp.resolve("missing"));throw new AssertionError("missing accepted");}catch(IOException expected){}});
    }catch(Exception e){failed++;e.printStackTrace(System.out);}finally{if(frame!=null){try{((FolderModule)f(frame,"folders")).close();}catch(Exception e){failed++;}frame.dispose();}}
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
