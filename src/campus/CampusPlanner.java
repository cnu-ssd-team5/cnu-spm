package campus;

import java.awt.Font;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.Enumeration;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;

public final class CampusPlanner {
    private CampusPlanner() { }

    public static void main(String[] args) {
        Path root=Path.of(System.getProperty("campus.home",".")).toAbsolutePath().normalize();
        Path osm=root.resolve("data/chonnam-campus.osm");
        Path state=root.resolve("data/planner-state.xml");
        for(int i=0;i<args.length;i++) {
            if(i+1<args.length && args[i].equals("--osm"))osm=root.resolve(args[++i]).normalize();
            else if(i+1<args.length && args[i].equals("--state"))state=root.resolve(args[++i]).normalize();
            else {
                System.err.println("Usage: campus.CampusPlanner [--osm file.osm] [--state planner-state.xml]");
                return;
            }
        }
        configureLookAndFeel();
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
            MapData map=MapData.load(osm);
            PlannerModel model=PlannerStore.load(state);
            Path statePath=state;
            SwingUtilities.invokeLater(() -> new PlannerFrame(map,model,statePath).setVisible(true));
        } catch(Exception ex) {
            Path statePath=state;
            System.err.println("Campus planner startup failed: "+ex.getMessage());
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "캠퍼스 플래너를 열지 못했습니다.\n"+ex.getMessage()+"\n\nOSM 파일과 저장 파일을 확인해 주세요.\n저장 파일: "+statePath,
                    "시작 오류",JOptionPane.ERROR_MESSAGE));
        }
    }

    static void configureLookAndFeel() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch(Exception ignored) { /* Swing 기본 LookAndFeel로 실행합니다. */ }
        FontUIResource font=new FontUIResource(Theme.font(13,Font.PLAIN));
        Enumeration<Object> keys=UIManager.getDefaults().keys();
        while(keys.hasMoreElements()) {
            Object key=keys.nextElement();
            if(UIManager.get(key) instanceof FontUIResource)UIManager.put(key,font);
        }
        UIManager.put("ToolTip.font",Theme.font(12,Font.PLAIN));
        UIManager.put("OptionPane.messageFont",Theme.font(13,Font.PLAIN));
        UIManager.put("Button.font",Theme.font(12,Font.PLAIN));
    }
}
