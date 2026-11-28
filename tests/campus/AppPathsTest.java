package campus;
import campus.map.CampusMap;
import java.nio.file.Path;
public final class AppPathsTest {
    public static void main(String[] args)throws Exception {
        String home=System.getProperty("campus.home"),launcher=System.getProperty("jpackage.app-path");
        try {
            System.clearProperty("campus.home");System.clearProperty("jpackage.app-path");
            check(App.applicationRoot().equals(Path.of(".").toAbsolutePath().normalize()),"source-default-root");
            Path executable=Path.of("build/배포 폴더/CNU-Schedule.exe").toAbsolutePath().normalize();System.setProperty("jpackage.app-path",executable.toString());
            check(App.applicationRoot().equals(executable.getParent()),"packaged-root-beside-exe");
            System.setProperty("campus.home","build/custom-state");check(App.applicationRoot().equals(Path.of("build/custom-state").toAbsolutePath().normalize()),"explicit-home-overrides-launcher");
            CampusMap map=CampusMap.load(Path.of(args[0]));
            check(!map.buildings().isEmpty(),"bundled-map-loads");
            CampusMap copy=map.copyBasePlaces();
            check(map.buildings().stream().allMatch(b->copy.building(b.id())==null||copy.building(b.id()).name().equals(b.name())),"friend-map-matches-base-names");
        }finally{
            if(home==null)System.clearProperty("campus.home");else System.setProperty("campus.home",home);
            if(launcher==null)System.clearProperty("jpackage.app-path");else System.setProperty("jpackage.app-path",launcher);
        }
        System.out.println("APP PATHS RESULT: 5 passed, 0 failed");
    }
    static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);System.out.println("PASS "+name);}
}
