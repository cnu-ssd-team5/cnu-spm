package campus;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import javax.swing.*;
import campus.map.MapSelection;
import campus.map.MapSelection.Loaded;
import campus.ui.MainFrame;
import campus.ui.Theme;

public final class App {
    private App() { }

    static Path applicationRoot() {
        String configured=System.getProperty("campus.home");
        if(configured!=null&&!configured.isBlank())return Path.of(configured).toAbsolutePath().normalize();
        String launcher=System.getProperty("jpackage.app-path");
        if(launcher!=null&&!launcher.isBlank())return Path.of(launcher).toAbsolutePath().normalize().getParent();
        return Path.of(".").toAbsolutePath().normalize();
    }

    public static void main(String[] args) {
        Path root=applicationRoot();
        System.setProperty("campus.home",root.toString());
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
            Path source = osm == null ? MapSelection.startupMap(state) : osm;
            Loaded selected = MapSelection.load(state, source);
            MapSelection.remember(state, selected);
            Path statePath=state;
            SwingUtilities.invokeLater(() -> {
                try { createWindow(selected, statePath).setVisible(true); }
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

    private static MainFrame createWindow(Loaded selected, Path defaultState) throws IOException {
        MainFrame frame = new MainFrame(selected.map(), selected.routes(), selected.model(), selected.statePath(), selected.linked());
        frame.setMapReplacement(file -> changeMap(frame, selected, defaultState, file));
        return frame;
    }

    private static void changeMap(MainFrame current, Loaded previous, Path defaultState, Path file) {
        if (!current.prepareMapChange()) return;
        current.setEnabled(false);
        new SwingWorker<Loaded, Void>() {
            @Override protected Loaded doInBackground() throws IOException {
                return MapSelection.load(defaultState, file);
            }
            @Override protected void done() {
                try { replaceWindow(current, previous, get(), defaultState); }
                catch (Exception ex) {
                    if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                    Throwable cause = ex instanceof java.util.concurrent.ExecutionException ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(null, "지도를 교체하지 못했습니다.\n" + cause.getMessage(),
                            "지도 교체", JOptionPane.ERROR_MESSAGE);
                } finally { if (current.isDisplayable()) current.setEnabled(true); }
            }
        }.execute();
    }

    private static void replaceWindow(MainFrame current, Loaded previous, Loaded next, Path defaultState) throws IOException {
        current.closeMapWorkspace();
        MainFrame replacement = null;
        try {
            replacement = createWindow(next, defaultState);
            MapSelection.remember(defaultState, next);
        } catch (IOException | RuntimeException failure) {
            if (replacement != null) {
                try { replacement.closeMapWorkspace(); } catch (IOException ex) { failure.addSuppressed(ex); }
                replacement.dispose();
            }
            // 자료함을 다시 여는 데 실패해도 저장된 기존 지도로 복구를 시도합니다.
            try { showReplacement(current, createWindow(previous, defaultState)); }
            catch (IOException | RuntimeException ex) { failure.addSuppressed(ex); current.dispose(); }
            throw failure;
        }
        showReplacement(current, replacement);
    }

    private static void showReplacement(MainFrame current, MainFrame replacement) {
        replacement.setBounds(current.getBounds());
        replacement.setExtendedState(current.getExtendedState());
        replacement.setVisible(true);
        current.dispose();
    }
}
