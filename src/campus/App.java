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
        // 자료함과 일정 데이터를 프로젝트 data 폴더에 모아 보관합니다.
        Path state=root.resolve("data/planner-state.xml");
        for(int i=0;i<args.length;i++) {
            if(i+1<args.length && args[i].equals("--osm"))osm=root.resolve(args[++i]).normalize();
            else if(i+1<args.length && args[i].equals("--state")) {
                state=root.resolve(args[++i]).normalize();
            } else {
                System.err.println("Usage: campus.App [--osm file.osm] [--state planner-state.xml]");
                return;
            }
        }
        Theme.configureLookAndFeel();
        try {
            Files.createDirectories(state.getParent());
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
            SwingUtilities.invokeLater(() -> {
                try { new MainFrame(map,routes,model,statePath).setVisible(true); }
                catch(IOException ex) {
                    try { lock.release();channel.close(); } catch(IOException ignored) { }
                    JOptionPane.showMessageDialog(null,ex.getMessage(),"자료함 시작 오류",JOptionPane.ERROR_MESSAGE);
                }
            });
        } catch(Exception ex) {
            Path statePath=state;
            System.err.println("Campus planner startup failed: "+ex.getMessage());
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "캠퍼스 플래너를 열지 못했습니다.\n"+ex.getMessage()+"\n\nOSM 파일과 저장 파일을 확인해 주세요.\n저장 파일: "+statePath,
                    "시작 오류",JOptionPane.ERROR_MESSAGE));
        }
    }

}
