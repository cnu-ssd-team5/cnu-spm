package campus;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.stream.Stream;
import javax.swing.*;
import campus.map.MapData;
import campus.map.RouteService;
import campus.model.PlannerModel;
import campus.model.PlannerStore;
import campus.ui.PlannerFrame;
import campus.ui.Theme;

public final class CampusPlanner {
    private CampusPlanner() { }

    public static void main(String[] args) {
        Path root=Path.of(System.getProperty("campus.home",".")).toAbsolutePath().normalize();
        // 일정과 과목 자료는 사용자 홈 폴더에 둡니다. 프로그램 폴더에 쓰기 권한이 없어도 동작합니다.
        Path home=Path.of(System.getProperty("user.home"),".cnu-planner");
        Path osm=null;
        Path state=home.resolve("planner-state.xml");
        boolean defaultState=true;
        for(int i=0;i<args.length;i++) {
            if(i+1<args.length && args[i].equals("--osm"))osm=root.resolve(args[++i]).normalize();
            else if(i+1<args.length && args[i].equals("--state")) {
                state=root.resolve(args[++i]).normalize();
                defaultState=false;
            } else {
                System.err.println("Usage: campus.CampusPlanner [--osm file.osm] [--state planner-state.xml]");
                return;
            }
        }
        Theme.configureLookAndFeel();
        try {
            Files.createDirectories(state.getParent());
            if(defaultState)copyOldFiles(root,home);
            FileChannel channel = FileChannel.open(state.resolveSibling(state.getFileName()+".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if(lock == null) {
                channel.close();
                throw new IOException("같은 저장 파일을 사용하는 플래너가 이미 실행 중입니다.");
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { lock.release(); channel.close(); }
                catch(IOException ignored) { }
            },"planner-state-lock"));
            MapData map=osm==null ? MapData.loadBundled() : MapData.load(osm);
            RouteService routes=new RouteService(map);
            PlannerModel model=PlannerStore.load(state);
            Path statePath=state;
            SwingUtilities.invokeLater(() -> new PlannerFrame(map,routes,model,statePath,home).setVisible(true));
        } catch(Exception ex) {
            Path statePath=state;
            System.err.println("Campus planner startup failed: "+ex.getMessage());
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "캠퍼스 플래너를 열지 못했습니다.\n"+ex.getMessage()+"\n\nOSM 파일과 저장 파일을 확인해 주세요.\n저장 파일: "+statePath,
                    "시작 오류",JOptionPane.ERROR_MESSAGE));
        }
    }

    // 예전 버전은 프로젝트의 data, materials 폴더에 저장했습니다. 새 위치에 없을 때 한 번만 복사합니다.
    private static void copyOldFiles(Path root, Path home) throws IOException {
        Path oldState=root.resolve("data/planner-state.xml"), state=home.resolve("planner-state.xml");
        if(Files.isRegularFile(oldState) && !Files.exists(state))Files.copy(oldState,state);
        Path oldMaterials=root.resolve("materials"), materials=home.resolve("materials");
        if(!Files.isDirectory(oldMaterials) || Files.exists(materials))return;
        try(Stream<Path> files=Files.walk(oldMaterials)) {
            for(Path source : files.toList()) {
                Path target=materials.resolve(oldMaterials.relativize(source).toString());
                if(Files.isDirectory(source))Files.createDirectories(target);
                else Files.copy(source,target);
            }
        }
    }
}
