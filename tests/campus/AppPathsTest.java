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
            check(!CampusMap.loadBundled().buildings().isEmpty(),"bundled-map-loads");
        }finally{
            if(home==null)System.clearProperty("campus.home");else System.setProperty("campus.home",home);
            if(launcher==null)System.clearProperty("jpackage.app-path");else System.setProperty("jpackage.app-path",launcher);
        }
        System.out.println("APP PATHS RESULT: 4 passed, 0 failed");
    }
    static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);System.out.println("PASS "+name);}
}
