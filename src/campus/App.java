package campus;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import javax.swing.*;
import campus.map.CampusMap;
import campus.map.RouteService;
import campus.model.PlannerModel;
import campus.model.PlannerStore;
import campus.ui.MainFrame;
import campus.ui.Theme;

public final class App {
    private App() { }

    public static void main(String[] args) {
        Path root=Path.of(System.getProperty("campus.home",".")).toAbsolutePath().normalize();
        Path osm=null;
        // 일정은 사용자 홈 폴더에 저장합니다. 프로그램 폴더에 쓰기 권한이 없어도 동작합니다.
        Path state=Path.of(System.getProperty("user.home"),".cnu-planner","planner-state.xml");
        boolean defaultState=true;
        for(int i=0;i<args.length;i++) {
            if(i+1<args.length && args[i].equals("--osm"))osm=root.resolve(args[++i]).normalize();
            else if(i+1<args.length && args[i].equals("--state")) {
                state=root.resolve(args[++i]).normalize();
                defaultState=false;
            } else {
                System.err.println("Usage: campus.App [--osm file.osm] [--state planner-state.xml]");
                return;
            }
        }
        Theme.configureLookAndFeel();
        try {
            Files.createDirectories(state.getParent());
            if(defaultState)copyOldState(root.resolve("data/planner-state.xml"),state);
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
            CampusMap map=osm==null ? CampusMap.loadBundled() : CampusMap.load(osm);
            RouteService routes=new RouteService(map);
            PlannerModel model=PlannerStore.load(state);
            Path statePath=state;
            SwingUtilities.invokeLater(() -> new MainFrame(map,routes,model,statePath).setVisible(true));
        } catch(Exception ex) {
            Path statePath=state;
            System.err.println("Campus planner startup failed: "+ex.getMessage());
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "캠퍼스 플래너를 열지 못했습니다.\n"+ex.getMessage()+"\n\nOSM 파일과 저장 파일을 확인해 주세요.\n저장 파일: "+statePath,
                    "시작 오류",JOptionPane.ERROR_MESSAGE));
        }
    }

    // 예전 버전은 프로젝트의 data 폴더에 저장했습니다. 처음 한 번만 새 위치로 복사합니다.
    private static void copyOldState(Path old, Path state) throws IOException {
        if(Files.isRegularFile(old) && !Files.exists(state))Files.copy(old,state);
    }
}
