package campus.folder;

import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.model.PlannerModel;
import campus.folder.storage.*;
import campus.folder.ui.*;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** 호스트 지도는 건물 판정과 좌표 변환을 제공하고, 자료함은 파일/UI 작업을 담당한다.
 * 생성 및 UI API는 Swing EDT에서 호출한다. 저장 경로는 다른 앱과 공유하지 않는다. */
public final class FolderModule implements AutoCloseable {
    private final CampusMap map;
    private final CourseCatalog catalog;
    private final PlannerModel model;
    private CourseFolderSync scheduleSync;
    private final Path buildingsRoot;
    private final BuildingFolderController controller;
    private final FileChannel channel;
    private final FileLock lock;
    private final List<Runnable> updates = new ArrayList<>();
    private final List<Runnable> detachments = new ArrayList<>();
    private boolean closed;
    private Function<Building,Building> beforeOpen=Function.identity();
    public void setBeforeOpen(Function<Building,Building> action) { beforeOpen=java.util.Objects.requireNonNull(action); }

    public FolderModule(Path dataDirectory, CampusMap map, PlannerModel model) throws IOException {
        this.map = map;
        this.model=model;
        buildingsRoot=dataDirectory.resolve("buildings");
        catalog = new CourseCatalog(map, model);
        Files.createDirectories(dataDirectory);
        channel = FileChannel.open(dataDirectory.resolve(".folder.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = channel.tryLock(); }
        catch (IOException | RuntimeException e) { channel.close(); throw new IOException("자료함이 이미 사용 중입니다.", e); }
        if (acquired == null) { channel.close(); throw new IOException("자료함이 이미 사용 중입니다."); }
        lock = acquired;
        try {
            FolderService service = new FolderService(dataDirectory.resolve("buildings"));
            catalog.migrate(service);
            controller = new BuildingFolderController(service, catalog);
        } catch (IOException | RuntimeException e) { lock.release(); channel.close(); throw e; }
    }

    public static FolderModule openProject(Path project,CampusMap map,PlannerModel model)throws IOException {
        Path target=project.resolve("data");
        FolderModule module=new FolderModule(target,map,model);
        Path legacy=project.resolve("Folder/data");
        try {
            if(Files.isDirectory(legacy))try(FileChannel old=FileChannel.open(legacy.resolve(".folder.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)) {
                FileLock held=old.tryLock();
                if(held==null)throw new IOException("이전 자료함을 종료한 뒤 다시 실행하세요.");
                try(held;var entries=Files.list(legacy)) {
                    List<Path> paths=entries.filter(p->!p.getFileName().toString().equals(".folder.lock")).toList();
                    for(Path p:paths) {
                        Path destination=target.resolve(p.getFileName());
                        if(Files.exists(destination)) {
                            // 생성자가 준비한 빈 buildings 폴더만 제거할 수 있다.
                            if(destination.getFileName().toString().equals("buildings")&&Files.isDirectory(destination)) {
                                try(var children=Files.list(destination)) { if(children.findAny().isPresent())throw new IOException("자료 경로 충돌: "+destination); }
                            } else throw new IOException("자료 경로 충돌: "+destination);
                        }
                    }
                    for(Path p:paths) {
                        Path destination=target.resolve(p.getFileName());
                        if(Files.isDirectory(destination))Files.delete(destination);
                        Files.move(p,destination);
                    }
                }
            }
            return module;
        } catch(IOException|RuntimeException e) { module.close();throw e; }
    }

    private String key(Building building) {
        if (closed) throw new IllegalStateException("닫힌 자료함입니다.");
        if (building == null || map.building(building.id()) == null) throw new IllegalArgumentException("지도에 없는 건물입니다.");
        return catalog.storageKey(building.id());
    }

    public String folderTooltip(Building building) {
        String name=building.name().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
        Path directory=buildingsRoot.resolve(key(building));
        List<String> names=new ArrayList<>();
        try {
            if(Files.isDirectory(directory))try(var entries=Files.list(directory)) {
                entries.filter(p->Files.isDirectory(p)&&!Files.isSymbolicLink(p)).map(p->p.getFileName().toString()).sorted().forEach(names::add);
            }
        } catch(IOException e) { return "<html>"+name+"<br>폴더 목록을 읽을 수 없습니다.</html>"; }
        String items=names.isEmpty()?"수업 폴더 없음":names.stream().map(n->n.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")).collect(java.util.stream.Collectors.joining("<br>"));
        return "<html><b>"+name+"</b><br>"+items+"</html>";
    }

    public void syncSchedules()throws IOException {
        if(isBusy())throw new IOException("파일 작업이 끝난 뒤 일정을 저장해 주세요.");
        if(scheduleSync==null)scheduleSync=new CourseFolderSync(new FolderService(buildingsRoot),catalog,model);
        if(scheduleSync.sync()) { controller.scheduleChanged(); refreshRecent(); }
    }

    public boolean isBusy() { return controller.isWorking(); }
    public void closePopup() { controller.closePreview(); }

    /** point는 지도 컴포넌트 내부 좌표다. */
    public void openAt(JComponent anchor, Point point, Building building) {
        if(isBusy())return;
        building=beforeOpen.apply(building);
        if(building==null)return;
        controller.showPreview(anchor, new Point(point), key(building), this::refreshRecent);
    }

    public void openCourse(JComponent anchor,PlannerModel.Event event) {
        if(isBusy())return;
        Building building=map.building(event.buildingId());
        if(building==null)return;
        Path path=buildingsRoot.resolve(key(building)).resolve(CourseCatalog.folderName(event.title()));
        controller.showFolderPreview(anchor,path,this::refreshRecent);
    }

    public boolean hasFolder(Building building) {
        return Files.isDirectory(buildingsRoot.resolve(key(building)),LinkOption.NOFOLLOW_LINKS);
    }

    public void createFolder(Component owner,Building building) {
        if(isBusy())return;
        try {
            FolderService service=new FolderService(buildingsRoot);
            Path path=service.checked(buildingsRoot.resolve(key(building)));
            Files.createDirectories(path);
            catalog.prepare(service,key(building));
        } catch(IOException ex) {
            JOptionPane.showMessageDialog(owner,ex.getMessage(),"폴더 생성 실패",JOptionPane.ERROR_MESSAGE);
        }
    }

    public void moveInto(JComponent owner, Building building, List<File> files) {
        controller.receiveFiles(SwingUtilities.getWindowAncestor(owner), key(building), List.copyOf(files), this::refreshRecent);
    }

    public JButton searchButton() {
        JButton button=campus.ui.Theme.button("파일 검색",false,()->{});
        button.addActionListener(e -> {
            if (isBusy() || closed) return;
            String query = JOptionPane.showInputDialog(SwingUtilities.getWindowAncestor(button), "파일·폴더 이름");
            if (query != null && !query.isBlank()) controller.searchFiles(SwingUtilities.getWindowAncestor(button), query.strip(), this::refreshRecent);
        });
        return button;
    }

    /** 최근 파일이 없으면 숨겨진 버튼을 반환한다. 배치는 호스트의 건물 좌표에 맞춘다. */
    public JButton recentButton(Building building) {
        String id = key(building);
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(32, 32));
        button.getAccessibleContext().setAccessibleName(building.name() + " 최근 파일");
        button.addActionListener(e -> controller.openLatest(SwingUtilities.getWindowAncestor(button), id));
        Runnable update = () -> {
            Path file = controller.latest(id);
            button.setVisible(file != null);
            button.setToolTipText(file == null ? null : file.getFileName().toString());
            button.setIcon(file == null ? null : new DocumentIcon(FolderService.extension(file).toUpperCase(java.util.Locale.ROOT), false, 24));
        };
        button.putClientProperty("recent.update",update);
        updates.add(update);
        update.run();
        return button;
    }

    public void refreshRecent() { List.copyOf(updates).forEach(Runnable::run); }

    /** 빈 지도 패널용: 최근 파일 버튼을 건물 옆에 배치하고 확대/이동 시 위치를 갱신한다. */
    public void attach(JComponent canvas, Function<Point, Building> hitTest,
                       Function<Building, java.awt.geom.Point2D> project) {
        if (canvas.getComponentCount() != 0) throw new IllegalArgumentException("최근 파일 오버레이는 자식 컴포넌트가 없는 지도에 연결하세요.");
        attach(canvas, hitTest);
        LayoutManager previousLayout = canvas.getLayout();
        canvas.setLayout(null);
        java.util.Map<Building,JButton> buttons = new java.util.LinkedHashMap<>();
        for (Building building : map.buildings()) {
            JButton button = recentButton(building);
            buttons.put(building, button);
            canvas.add(button);
        }
        Runnable position = () -> {
            if (closed) return;
            var existing=new ArrayList<>(buttons.keySet());
            for(Building building:existing)if(!building.equals(map.building(building.id()))) {
                JButton button=buttons.remove(building);canvas.remove(button);updates.remove(button.getClientProperty("recent.update"));
            }
            for(Building building:map.buildings())if(!buttons.containsKey(building)) {
                JButton button=recentButton(building);buttons.put(building,button);canvas.add(button);
            }
            buttons.forEach((building,button) -> {
                java.awt.geom.Point2D p = project.apply(building);
                int x=(int)p.getX()+18, y=(int)p.getY()-16;
                if(button.getX()!=x || button.getY()!=y || button.getWidth()!=32)
                    button.setBounds(x,y,32,32);
            });
        };
        MouseAdapter movement = new MouseAdapter() {
            public void mouseDragged(MouseEvent e) { SwingUtilities.invokeLater(position); }
            public void mouseWheelMoved(MouseWheelEvent e) { SwingUtilities.invokeLater(position); }
        };
        ComponentAdapter resize = new ComponentAdapter() {
            public void componentResized(ComponentEvent e) { position.run(); }
        };
        // 키보드/버튼으로 수행된 지도 zoom도 감지한다. 파일 시스템 조회는 하지 않는다.
        Timer positions = new Timer(100, e -> { if(canvas.isShowing())position.run(); });
        positions.start();
        canvas.addMouseMotionListener(movement);
        canvas.addMouseWheelListener(movement);
        canvas.addComponentListener(resize);
        updates.add(position);
        position.run();
        detachments.add(() -> {
            positions.stop();
            canvas.removeMouseMotionListener(movement);
            canvas.removeMouseWheelListener(movement);
            canvas.removeComponentListener(resize);
            buttons.values().forEach(canvas::remove);
            canvas.setLayout(previousLayout);
            canvas.revalidate();
            canvas.repaint();
        });
    }

    /** 기존 지도 선택/패닝 리스너를 유지한다. hitTest는 확대·이동이 반영된 현재 좌표를 판정해야 한다. */
    public void attach(JComponent canvas, Function<Point, Building> hitTest) {
        if (closed) throw new IllegalStateException("닫힌 자료함입니다.");
        MouseAdapter mouse = new MouseAdapter() {
            private Point press;
            private boolean dragged;
            public void mousePressed(MouseEvent e) {
                press = SwingUtilities.isLeftMouseButton(e) ? e.getPoint() : null;
                dragged = false;
            }
            public void mouseDragged(MouseEvent e) {
                if (press != null && press.distance(e.getPoint()) > 2) dragged = true;
            }
            public void mouseReleased(MouseEvent e) {
                if (press != null && !dragged && SwingUtilities.isLeftMouseButton(e)) {
                    Building building = hitTest.apply(e.getPoint());
                    if (building == null) closePopup();
                    else if (e.getClickCount() == 2) openAt(canvas, e.getPoint(), building);
                }
                press = null;
            }
        };
        TransferHandler previous = canvas.getTransferHandler();
        TransferHandler transfer = new TransferHandler() {
            public boolean canImport(TransferSupport s) {
                if (closed || isBusy() || !s.isDrop() || !s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false;
                if (hitTest.apply(s.getDropLocation().getDropPoint()) == null || (s.getSourceDropActions() & COPY) == 0) return false;
                // 실제 파일 이동은 비동기 작업이 담당하며, 소스 앱의 추가 삭제를 방지한다.
                s.setDropAction(COPY);
                return true;
            }
            public boolean importData(TransferSupport s) {
                if (!canImport(s)) return false;
                try {
                    Object data = s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    List<File> files = new ArrayList<>();
                    for (Object value : (List<?>) data) files.add((File) value);
                    Building building = hitTest.apply(s.getDropLocation().getDropPoint());
                    // 드롭 콜백 반환 후 선택 대화상자를 연다.
                    SwingUtilities.invokeLater(() -> { if (!closed) moveInto(canvas, building, files); });
                    return true;
                } catch (Exception e) { return false; }
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        canvas.setTransferHandler(transfer);
        detachments.add(() -> {
            canvas.removeMouseListener(mouse);
            canvas.removeMouseMotionListener(mouse);
            if (canvas.getTransferHandler() == transfer) canvas.setTransferHandler(previous);
        });
    }

    public void close() throws IOException {
        if (closed) return;
        if (isBusy()) throw new IOException("파일 작업이 끝난 뒤 닫아 주세요.");
        controller.close();
        detachments.forEach(Runnable::run);
        detachments.clear();
        updates.clear();
        closed = true;
        try { lock.release(); } finally { channel.close(); }
    }
}
