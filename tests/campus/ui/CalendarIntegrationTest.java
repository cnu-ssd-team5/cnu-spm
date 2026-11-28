package campus.ui;

import campus.folder.FolderModule;
import campus.folder.*;
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
    /** 학기 기능을 없애기 전에 쓰던 학기 폴더 이름 */
    static final String FORMER_TERM="2026-2학기";
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
        /** 학기 기능을 없애기 전의 폴더 구조: 건물/2026-2학기/과목 */
        Path legacy(Event event){return files.getRoot().resolve(catalog.storageKey(event.buildingId())).resolve(FORMER_TERM).resolve(CourseCatalog.folderName(event.title()));}
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
    static int bluePixels(MapPanel panel) {
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(panel.getWidth(),panel.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();panel.paint(g);g.dispose();
        int count=0,blue=Theme.BLUE.getRGB();
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(image.getRGB(x,y)==blue)count++;
        return count;
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
        test("same-course-multiple-days-share-folder",()->{
            Fixture f=new Fixture();Event a=course("tt:group:MONDAY","Algorithms",building);
            Event b=new Event("tt:group:WEDNESDAY",a.title(),building,a.start(),a.end(),a.category(),DayOfWeek.WEDNESDAY,null,"");
            f.model.putEvent(a);f.model.putEvent(b);f.sync();ok(f.folder(a).equals(f.folder(b)),"duplicate course folders");
        });
        test("former-semester-folder-migrates-into-course-folder",()->{
            Fixture f=new Fixture();Event a=course("a","Math",building);f.model.putEvent(a);
            Path old=f.legacy(a).resolve("chapter/notes.txt");f.write(old,"kept");f.sync();
            ok(Files.readString(f.folder(a).resolve("chapter/notes.txt")).equals("kept"),"semester folder not migrated");
            ok(Files.readString(old).equals("kept"),"original semester folder removed");
            f.sync=new CourseFolderSync(f.files,f.catalog,f.model);f.sync();
            try(var paths=Files.list(f.folder(a).resolve("chapter"))){ok(paths.count()==1,"duplicate migration");}
        });
        test("legacy-with-links-preserves-existing-conflicting-file",()->{
            Fixture f=new Fixture();Event a=course("a","Physics",building);f.model.putEvent(a);
            f.write(f.legacy(a).resolve("notes.txt"),"old");f.write(f.folder(a).resolve("notes.txt"),"new");
            Properties links=new Properties();links.setProperty(a.id(),f.files.getRoot().relativize(f.legacy(a)).toString());
            try(var out=Files.newOutputStream(f.temp.resolve("data/.folder-course-links.properties"))){links.store(out,"");}
            f.sync();ok(Files.readString(f.folder(a).resolve("notes.txt")).equals("new"),"new overwritten");
            ok(Files.readString(f.folder(a).resolve("notes (1).txt")).equals("old"),"old lost");
        });
        test("legacy-course-named-like-semester-no-recursive-copy",()->{
            Fixture f=new Fixture();Event a=course("a",FORMER_TERM,building);f.model.putEvent(a);
            f.write(f.legacy(a).resolve("notes.txt"),"keep");f.sync();
            ok(Files.readString(f.folder(a).resolve("notes.txt")).equals("keep"),"nested migration missing");
            ok(Files.exists(f.legacy(a).resolve("notes.txt")),"original removed");
        });
        test("rename-and-building-change-move-course-folder",()->{
            Fixture f=new Fixture();Event a=course("a","Math",building);f.model.putEvent(a);f.sync();
            Path oldFolder=f.folder(a);f.write(oldFolder.resolve("f.txt"),"f");
            Event renamed=course("a","New Math",otherBuilding);f.model.putEvent(renamed);f.sync();
            ok(Files.readString(f.folder(renamed).resolve("f.txt")).equals("f"),"renamed data missing");
            ok(!Files.exists(oldFolder),"old course directory left");
        });
        test("shared-folder-retained-when-one-event-moves",()->{
            Fixture f=new Fixture();Event a=course("a","Shared",building),b=course("b","Shared",building);
            f.model.putEvent(a);f.model.putEvent(b);f.sync();f.write(f.folder(a).resolve("x.txt"),"x");
            Event moved=course("b","Shared",otherBuilding);f.model.putEvent(moved);f.sync();
            ok(Files.exists(f.folder(a).resolve("x.txt"))&&Files.exists(f.folder(moved).resolve("x.txt")),"shared files lost");
        });
        test("course-deletion-retains-materials",()->{
            Fixture f=new Fixture();Event a=course("a","Keep",building);f.model.putEvent(a);f.sync();Path saved=f.folder(a).resolve("x.txt");f.write(saved,"x");
            f.model.removeEvent(a.id());f.sync();ok(Files.exists(saved),"materials deleted with course");
        });
        test("path-invalid-characters-stay-inside-root",()->{
            Fixture f=new Fixture();Event a=new Event("a","../bad:name",building,LocalTime.of(9,0),LocalTime.of(10,0),Category.CLASS,null,LocalDate.of(2027,1,10),"");
            f.model.putEvent(a);f.sync();Path p=f.folder(a);ok(p.startsWith(f.files.getRoot()),"escaped root");
        });
        test("xml-roundtrip-retains-material-path-and-midnight",()->{
            Fixture f=new Fixture();Event a=new Event("a","Late",building,LocalTime.of(23,0),LocalTime.MAX,Category.CLASS,DayOfWeek.MONDAY,null,"memo");
            f.model.putEvent(a);f.model.toggleCancellation(a.id(),MONDAY);f.sync();Path state=f.temp.resolve("state.xml");PlannerStore.save(state,f.model);
            PlannerModel back=PlannerStore.load(state);CourseCatalog restored=new CourseCatalog(map,back);
            ok(restored.relativeCoursePath(back.events().get(0)).equals(f.catalog.relativeCoursePath(a)),"folder changed after restart");
            ok(back.events().get(0).end().equals(LocalTime.MAX)&&back.occurrences(MONDAY).get(0).canceled(),"midnight or cancellation lost");
        });
        test("file-drop-conflict-and-undo-preserve-both-files",()->{
            Fixture f=new Fixture();Event a=course("a","Drop",building);f.model.putEvent(a);f.sync();Path source=f.temp.resolve("notes.txt");f.write(source,"incoming");f.write(f.folder(a).resolve("notes.txt"),"existing");
            FileOperations ops=new FileOperations(f.files);ops.paste(List.of(source),f.folder(a),true);
            ok(!Files.exists(source)&&Files.readString(f.folder(a).resolve("notes (1).txt")).equals("incoming"),"move failed");
            ops.undo();ok(Files.readString(source).equals("incoming")&&Files.readString(f.folder(a).resolve("notes.txt")).equals("existing"),"undo destroyed files");
        });
        test("unsafe-target-and-recursive-paste-rejected",()->{
            Fixture f=new Fixture();boolean blocked=false;try{f.files.checked(f.temp.resolve("outside"));}catch(IOException expected){blocked=true;}ok(blocked,"outside accepted");
            Path parent=f.files.getRoot().resolve("source");Files.createDirectories(parent.resolve("child"));blocked=false;
            try{new FileOperations(f.files).paste(List.of(parent),parent.resolve("child"),true);}catch(IOException expected){blocked=true;}ok(blocked&&Files.exists(parent),"recursive move accepted");
        });
        test("course-card-drop-moves-into-course-folder",()->{
            Fixture f=new Fixture();Event a=course("a","Drop",building);f.model.putEvent(a);
            Path incoming=f.temp.resolve("drop.txt");f.write(incoming,"content");AtomicReference<FolderModule> module=new AtomicReference<>();
            try {
                onEdt(()->{
                    FolderModule folders=new FolderModule(f.temp.resolve("data"),map,f.model);module.set(folders);folders.syncSchedules();
                    CourseMaterialsPanel panel=new CourseMaterialsPanel(f.model,map,folders);panel.refresh();
                    JPanel courses=(JPanel)field(panel,"courses");JButton card=ScheduleEntryWhiteBoxTest.components(courses,JButton.class).stream().filter(button->button.getText()!=null&&button.getText().startsWith("<html>")).findFirst().orElseThrow();
                    ok(card.getTransferHandler().importData(new TransferHandler.TransferSupport(card,fileTransfer(List.of(incoming.toFile())))),"drop rejected");
                });
                long deadline=System.nanoTime()+10_000_000_000L;
                while(Files.exists(incoming)&&System.nanoTime()<deadline)Thread.sleep(20);
                ok(Files.exists(f.folder(a).resolve("drop.txt"))&&!Files.exists(incoming),"drop not moved into course folder");
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
        test("map-marker-click-selects-scheduled-building",()->onEdt(()->{
            Event event=course("map","Map",building);AtomicReference<CampusMap.Building> selected=new AtomicReference<>();
            MapPanel panel=new MapPanel(map,new PlannerModel(),selected::set);panel.setSize(800,600);panel.setOccurrences(List.of(new Occurrence(event,MONDAY,false)),List.of());
            Point p=center(panel,building);panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_RELEASED,0,0,p.x,p.y,1,false,MouseEvent.BUTTON1));
            ok(selected.get()!=null&&selected.get().id().equals(building),"scheduled building not selected");
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
        test("dragging-map-does-not-select-building",()->onEdt(()->{
            Event a=course("a","Map",building);AtomicInteger selected=new AtomicInteger();
            MapPanel panel=new MapPanel(map,new PlannerModel(),v->selected.incrementAndGet());panel.setSize(800,600);panel.setOccurrences(List.of(new Occurrence(a,MONDAY,false)),List.of());Point p=center(panel,building);
            panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_PRESSED,0,0,p.x,p.y,1,false,1));
            panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON1_DOWN_MASK,p.x+30,p.y+30,0,false,0));
            panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_RELEASED,0,0,p.x+30,p.y+30,1,false,1));ok(selected.get()==0,"drag selected a building");
        }));
        test("map-route-switch-hides-schedule-legs-only",()->onEdt(()->{
            Occurrence first=new Occurrence(course("a","A",building),MONDAY,false),second=new Occurrence(course("b","B",otherBuilding),MONDAY,false);
            double lat=map.maxLat()-map.minLat(),lon=map.maxLon()-map.minLon();
            var line=new campus.map.RouteService.Route(List.of(new CampusMap.GeoPoint(map.minLat()+lat*.2,map.minLon()+lon*.2),
                    new CampusMap.GeoPoint(map.minLat()+lat*.8,map.minLon()+lon*.8)),500,true,"");
            MapPanel panel=new MapPanel(map,new PlannerModel(),v->{});panel.setSize(800,600);
            panel.setOccurrences(List.of(first,second),List.of(new campus.service.DayPlanService.Leg(first,second,line)));
            int shown=bluePixels(panel);panel.setScheduleRoutesVisible(false);int hidden=bluePixels(panel);
            ok(!panel.scheduleRoutesVisible()&&shown>hidden+200,"schedule route still drawn");
            panel.setSearchRoute(map.building(building),map.building(otherBuilding),new campus.map.RouteService.Route(List.of(),0,true,""));int noSearchLine=bluePixels(panel);
            panel.setSearchRoute(map.building(building),map.building(otherBuilding),line);int searchHidden=bluePixels(panel);
            panel.setScheduleRoutesVisible(true);
            ok(searchHidden>noSearchLine+200&&bluePixels(panel)==searchHidden,"search route follows schedule switch");
            panel.clearSearchRoute();ok(bluePixels(panel)==shown,"switch did not restore schedule route");
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
