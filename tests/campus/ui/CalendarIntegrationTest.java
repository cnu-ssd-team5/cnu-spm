package campus.ui;

import campus.calendar.Semester;
import campus.folder.FolderModule;
import campus.folder.storage.*;
import campus.map.CampusMap;
import campus.materials.*;
import campus.model.*;
import campus.model.PlannerModel.*;
import campus.model.PlannerModel.Event;
import campus.timetable.TimePicker;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import javax.swing.*;

/** Branch-oriented integration checks. Every file operation uses an isolated temporary directory. */
public final class CalendarIntegrationTest {
    interface Checked { void run() throws Exception; }
    static int passed,failed;
    static CampusMap map;
    static String building,otherBuilding;
    static final Semester SPRING=new Semester(2026,Semester.Season.SPRING);
    static final Semester FALL=new Semester(2026,Semester.Season.FALL);
    static final LocalDate MONDAY=LocalDate.of(2026,11,16);
    static void test(String name,Checked task) {
        try {task.run();passed++;System.out.println("PASS "+name);}
        catch(Throwable error){failed++;System.out.println("FAIL "+name);error.printStackTrace(System.out);}
    }
    static void ok(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    static Event course(String id,String title,String place) {
        return new Event(id,title,place,LocalTime.of(9,0),LocalTime.of(10,0),Category.CLASS,DayOfWeek.MONDAY,null,"");
    }
    static final class Fixture {
        final Path temp=Files.createTempDirectory("cnu-whitebox-");
        final PlannerModel model=new PlannerModel();
        final FolderService files=new FolderService(temp.resolve("data/buildings"));
        final CourseCatalog catalog=new CourseCatalog(map,model);
        CourseFolderSync sync;
        Fixture()throws IOException { }
        void sync()throws IOException {if(sync==null)sync=new CourseFolderSync(files,catalog,model);sync.sync();}
        Path folder(Event event){return files.getRoot().resolve(catalog.relativeCoursePath(event));}
        Path legacy(Event event){return files.getRoot().resolve(catalog.storageKey(event.buildingId())).resolve(CourseCatalog.folderName(event.title()));}
        void write(Path file,String text)throws IOException {Files.createDirectories(file.getParent());Files.writeString(file,text);}
    }
    static void onEdt(Checked run)throws Exception {
        AtomicReference<Throwable> error=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{try{run.run();}catch(Throwable e){error.set(e);}});
        if(error.get()!=null)throw new AssertionError("EDT check failed",error.get());
    }
    static Object field(Object object,String name)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    static Point center(MapPanel panel,String id)throws Exception {
        Method project=MapPanel.class.getDeclaredMethod("point",CampusMap.GeoPoint.class);project.setAccessible(true);
        Point2D point=(Point2D)project.invoke(panel,map.building(id).center());
        return new Point((int)point.getX(),(int)point.getY());
    }
    static Transferable fileTransfer(Object payload) {
        return new Transferable() {
            public DataFlavor[] getTransferDataFlavors(){return new DataFlavor[]{DataFlavor.javaFileListFlavor};}
            public boolean isDataFlavorSupported(DataFlavor flavor){return flavor.equals(DataFlavor.javaFileListFlavor);}
            public Object getTransferData(DataFlavor flavor){return payload;}
        };
    }
    public static void main(String[] args)throws Exception {
        map=CampusMap.load(Path.of(args[0]));
        var named=map.buildings().stream().filter(b->!b.name().replace(" ","").startsWith("이름없는")).toList();
        building=named.get(0).id();otherBuilding=named.get(1).id();
        test("same-course-separated-by-semester",()->{
            Fixture f=new Fixture();Event a=course("spring","Algorithms",building),b=course("fall","Algorithms",building);
            f.model.putEvent(a,SPRING);f.model.putEvent(b,FALL);f.sync();
            ok(!f.folder(a).equals(f.folder(b)),"semester collision");
            f.write(f.folder(a).resolve("notes.txt"),"spring");f.write(f.folder(b).resolve("notes.txt"),"fall");
            ok(Files.readString(f.folder(a).resolve("notes.txt")).equals("spring"),"spring overwritten");
        });
        test("same-semester-multiple-days-share-folder",()->{
            Fixture f=new Fixture();Event a=course("tt:group:MONDAY","Algorithms",building);
            Event b=new Event("tt:group:WEDNESDAY",a.title(),building,a.start(),a.end(),a.category(),DayOfWeek.WEDNESDAY,null,"");
            f.model.putEvent(a,FALL);f.model.putEvent(b,FALL);f.sync();ok(f.folder(a).equals(f.folder(b)),"duplicate course folders");
        });
        test("legacy-without-links-copied-to-both-terms",()->{
            Fixture f=new Fixture();Event a=course("s","Math",building),b=course("f","Math",building);
            f.model.putEvent(a,SPRING);f.model.putEvent(b,FALL);Path old=f.legacy(a).resolve("chapter/notes.txt");f.write(old,"shared");f.sync();
            ok(Files.readString(old).equals("shared"),"original removed");
            ok(Files.readString(f.folder(a).resolve("chapter/notes.txt")).equals("shared"),"spring missing");
            ok(Files.readString(f.folder(b).resolve("chapter/notes.txt")).equals("shared"),"fall missing");
            f.sync=new CourseFolderSync(f.files,f.catalog,f.model);f.sync();
            try(var paths=Files.list(f.folder(a).resolve("chapter"))){ok(paths.count()==1,"duplicate migration");}
        });
        test("legacy-with-links-preserves-existing-conflicting-file",()->{
            Fixture f=new Fixture();Event a=course("a","Physics",building);f.model.putEvent(a,FALL);
            f.write(f.legacy(a).resolve("notes.txt"),"old");f.write(f.folder(a).resolve("notes.txt"),"new");
            Properties links=new Properties();links.setProperty(a.id(),f.files.getRoot().relativize(f.legacy(a)).toString());
            try(var out=Files.newOutputStream(f.temp.resolve("data/.folder-course-links.properties"))){links.store(out,"");}
            f.sync();ok(Files.readString(f.folder(a).resolve("notes.txt")).equals("new"),"new overwritten");
            ok(Files.readString(f.folder(a).resolve("notes (1).txt")).equals("old"),"old lost");
        });
        test("legacy-course-named-like-semester-no-recursive-copy",()->{
            Fixture f=new Fixture();Event a=course("a",FALL.folderName(),building);f.model.putEvent(a,FALL);
            f.write(f.legacy(a).resolve("notes.txt"),"keep");f.sync();
            ok(Files.readString(f.folder(a).resolve("notes.txt")).equals("keep"),"nested migration missing");
            ok(Files.exists(f.legacy(a).resolve("notes.txt")),"original removed");
        });
        test("rename-and-building-change-only-move-selected-semester",()->{
            Fixture f=new Fixture();Event a=course("a","Math",building),b=course("b","Math",building);
            f.model.putEvent(a,SPRING);f.model.putEvent(b,FALL);f.sync();
            Path spring=f.folder(a),oldFall=f.folder(b);f.write(spring.resolve("s.txt"),"s");f.write(oldFall.resolve("f.txt"),"f");
            Event renamed=course("b","New Math",otherBuilding);f.model.putEvent(renamed,FALL);f.sync();
            ok(Files.exists(spring.resolve("s.txt")),"other semester moved");
            ok(Files.readString(f.folder(renamed).resolve("f.txt")).equals("f"),"renamed data missing");
            ok(!Files.exists(oldFall),"old course directory left");
        });
        test("shared-folder-retained-when-one-event-moves",()->{
            Fixture f=new Fixture();Event a=course("a","Shared",building),b=course("b","Shared",building);
            f.model.putEvent(a,FALL);f.model.putEvent(b,FALL);f.sync();f.write(f.folder(a).resolve("x.txt"),"x");
            Event moved=course("b","Shared",otherBuilding);f.model.putEvent(moved,FALL);f.sync();
            ok(Files.exists(f.folder(a).resolve("x.txt"))&&Files.exists(f.folder(moved).resolve("x.txt")),"shared files lost");
        });
        test("course-deletion-retains-materials",()->{
            Fixture f=new Fixture();Event a=course("a","Keep",building);f.model.putEvent(a,FALL);f.sync();Path saved=f.folder(a).resolve("x.txt");f.write(saved,"x");
            f.model.removeEvent(a.id());f.sync();ok(Files.exists(saved),"materials deleted with course");
        });
        test("winter-year-and-path-invalid-characters",()->{
            Fixture f=new Fixture();Event a=new Event("a","../bad:name",building,LocalTime.of(9,0),LocalTime.of(10,0),Category.CLASS,null,LocalDate.of(2027,1,10),"");
            f.model.putEvent(a);f.sync();Path p=f.folder(a);ok(p.startsWith(f.files.getRoot()),"escaped root");
            ok(p.toString().contains("2026-겨울방학"),"wrong academic year");
        });
        test("xml-roundtrip-retains-material-path-and-midnight",()->{
            Fixture f=new Fixture();Event a=new Event("a","Late",building,LocalTime.of(23,0),LocalTime.MAX,Category.CLASS,DayOfWeek.MONDAY,null,"memo");
            f.model.putEvent(a,FALL);f.model.toggleCancellation(a.id(),MONDAY);f.sync();Path state=f.temp.resolve("state.xml");PlannerStore.save(state,f.model);
            PlannerModel back=PlannerStore.load(state);CourseCatalog restored=new CourseCatalog(map,back);
            ok(restored.relativeCoursePath(back.events().get(0)).equals(f.catalog.relativeCoursePath(a)),"folder changed after restart");
            ok(back.events().get(0).end().equals(LocalTime.MAX)&&back.occurrences(MONDAY).get(0).canceled(),"midnight or cancellation lost");
        });
        test("file-drop-conflict-and-undo-preserve-both-files",()->{
            Fixture f=new Fixture();Event a=course("a","Drop",building);f.model.putEvent(a,FALL);f.sync();Path source=f.temp.resolve("notes.txt");f.write(source,"incoming");f.write(f.folder(a).resolve("notes.txt"),"existing");
            FileOperations ops=new FileOperations(f.files);ops.paste(List.of(source),f.folder(a),true);
            ok(!Files.exists(source)&&Files.readString(f.folder(a).resolve("notes (1).txt")).equals("incoming"),"move failed");
            ops.undo();ok(Files.readString(source).equals("incoming")&&Files.readString(f.folder(a).resolve("notes.txt")).equals("existing"),"undo destroyed files");
        });
        test("unsafe-target-and-recursive-paste-rejected",()->{
            Fixture f=new Fixture();boolean blocked=false;try{f.files.checked(f.temp.resolve("outside"));}catch(IOException expected){blocked=true;}ok(blocked,"outside accepted");
            Path parent=f.files.getRoot().resolve("source");Files.createDirectories(parent.resolve("child"));blocked=false;
            try{new FileOperations(f.files).paste(List.of(parent),parent.resolve("child"),true);}catch(IOException expected){blocked=true;}ok(blocked&&Files.exists(parent),"recursive move accepted");
        });
        test("course-card-drop-routes-to-selected-semester",()->{
            Fixture f=new Fixture();Event a=course("a","Drop",building),b=course("b","Drop",building);f.model.putEvent(a,SPRING);f.model.putEvent(b,FALL);
            Path incoming=f.temp.resolve("drop.txt");f.write(incoming,"content");AtomicReference<FolderModule> module=new AtomicReference<>();
            try {
                onEdt(()->{
                    FolderModule folders=new FolderModule(f.temp.resolve("data"),map,f.model);module.set(folders);folders.syncSchedules();
                    CourseMaterialsPanel panel=new CourseMaterialsPanel(f.model,map,folders);panel.refresh(FALL);
                    JPanel courses=(JPanel)field(panel,"courses");JButton card=ScheduleEntryWhiteBoxTest.components(courses,JButton.class).stream().filter(button->button.getText()!=null&&button.getText().startsWith("<html>")).findFirst().orElseThrow();
                    ok(card.getTransferHandler().importData(new TransferHandler.TransferSupport(card,fileTransfer(List.of(incoming.toFile())))),"drop rejected");
                });
                long deadline=System.nanoTime()+10_000_000_000L;
                while(Files.exists(incoming)&&System.nanoTime()<deadline)Thread.sleep(20);
                ok(Files.exists(f.folder(b).resolve("drop.txt"))&&!Files.exists(f.folder(a).resolve("drop.txt")),"wrong semester target");
            } finally {
                if(module.get()!=null){
                    for(int i=0;i<100;i++){AtomicBoolean busy=new AtomicBoolean();onEdt(()->busy.set(module.get().isBusy()));if(!busy.get())break;Thread.sleep(20);}
                    onEdt(()->module.get().close());
                }
            }
        });
        test("transfer-rejects-text-malformed-and-busy",()->onEdt(()->{
            AtomicInteger calls=new AtomicInteger();CourseFileTransferHandler handler=new CourseFileTransferHandler(()->true,files->calls.incrementAndGet());JPanel panel=new JPanel();
            ok(!handler.importData(new TransferHandler.TransferSupport(panel,new StringSelection("text"))),"text accepted");
            ok(!handler.importData(new TransferHandler.TransferSupport(panel,fileTransfer(List.of("bad")))),"malformed accepted");
            ok(!handler.importData(new TransferHandler.TransferSupport(panel,fileTransfer(List.of()))),"empty accepted");
            CourseFileTransferHandler busy=new CourseFileTransferHandler(()->false,files->calls.incrementAndGet());
            ok(!busy.importData(new TransferHandler.TransferSupport(panel,fileTransfer(List.of(new File("x")))))&&calls.get()==0,"busy accepted");
        }));
        test("map-marker-click-opens-exact-occurrence",()->onEdt(()->{
            PlannerModel model=new PlannerModel();Event event=course("map","Map",building);Occurrence occurrence=new Occurrence(event,MONDAY,false);
            MapPanel panel=new MapPanel(map,model,b->{});panel.setSize(800,600);panel.setOccurrences(List.of(occurrence),List.of());
            AtomicReference<Occurrence> opened=new AtomicReference<>();panel.setOccurrenceOpener(opened::set);Point p=center(panel,building);
            MouseEvent release=new MouseEvent(panel,MouseEvent.MOUSE_RELEASED,0,0,p.x,p.y,1,false,MouseEvent.BUTTON1);panel.dispatchEvent(release);
            ok(occurrence.equals(opened.get())&&release.isConsumed(),"wrong occurrence or event not consumed");
        }));
        test("map-markers-group-same-building-and-ignore-cancellation",()->onEdt(()->{
            Event a=course("a","A",building),b=course("b","B",building),c=course("c","Canceled",building);
            MapPanel panel=new MapPanel(map,new PlannerModel(),v->{});panel.setSize(800,600);
            panel.setOccurrences(List.of(new Occurrence(a,MONDAY,false),new Occurrence(b,MONDAY,false),new Occurrence(c,MONDAY,true)),List.of());
            ok(panel.scheduledAt(center(panel,building)).stream().map(o->o.event().id()).toList().equals(List.of("a","b")),"group mismatch");
            panel.zoom(2);ok(panel.scheduledAt(center(panel,building)).size()==2,"zoom changed hit target");
            panel.setSearchRoute(map.building(building),map.building(otherBuilding),new campus.map.RouteService.Route(List.of(),0,true,""));
            ok(panel.scheduledAt(center(panel,building)).isEmpty(),"route search label opens schedule");
        }));
        test("dragging-map-does-not-open-materials",()->onEdt(()->{
            Event a=course("a","Map",building);MapPanel panel=new MapPanel(map,new PlannerModel(),v->{});panel.setSize(800,600);panel.setOccurrences(List.of(new Occurrence(a,MONDAY,false)),List.of());AtomicInteger opened=new AtomicInteger();panel.setOccurrenceOpener(o->opened.incrementAndGet());Point p=center(panel,building);
            panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_PRESSED,0,0,p.x,p.y,1,false,1));
            panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON1_DOWN_MASK,p.x+30,p.y+30,0,false,0));
            panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_RELEASED,0,0,p.x+30,p.y+30,1,false,1));ok(opened.get()==0,"drag opened folder");
        }));
        test("time-picker-preserves-off-grid-imported-time",()->onEdt(()->{
            TimePicker picker=new TimePicker(false);LocalTime imported=LocalTime.of(7,3,14,123000000);picker.setTime(imported);ok(picker.time().equals(imported),"import rounded");
        }));
        test("time-picker-midnight-and-hour-change",()->onEdt(()->{
            TimePicker end=new TimePicker(true);end.setTime(LocalTime.MAX);ok(end.time().equals(LocalTime.MAX)&&TimePicker.display(end.time()).equals("24:00"),"midnight lost");
            JComboBox<?> minute=(JComboBox<?>)field(end,"minute");ok(!minute.isEnabled(),"minute editable at 24");
            ((JComboBox<?>)field(end,"hour")).setSelectedItem(23);ok(minute.isEnabled()&&end.time().equals(LocalTime.of(23,0)),"hour change failed");
            boolean rejected=false;try{new TimePicker(false).setTime(LocalTime.MAX);}catch(IllegalArgumentException expected){rejected=true;}ok(rejected,"24 start allowed");
        }));
        test("same-or-reversed-times-rejected",()->{
            for(LocalTime end:List.of(LocalTime.of(9,0),LocalTime.of(8,0))){boolean rejected=false;try{new Event("a","bad",building,LocalTime.of(9,0),end,Category.CLASS,DayOfWeek.MONDAY,null,"");}catch(IllegalArgumentException expected){rejected=true;}ok(rejected,"invalid duration accepted");}
        });
        System.out.println("INTEGRATION RESULT: "+passed+" passed, "+failed+" failed");
        if(failed>0)System.exit(1);
    }
}
