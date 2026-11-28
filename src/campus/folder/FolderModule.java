package campus.folder;

import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.model.PlannerModel;
import campus.ui.*;
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
            controller.setFolderChanged(this::refreshRecent);
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

    public FolderBrowserPanel openCourseInline(Component owner, PlannerModel.Event event) {
        if (isBusy() || closed) return null;
        try { return controller.openInline(courseDirectory(event), this::refreshRecent); }
        catch (IOException ex) {
            JOptionPane.showMessageDialog(owner, ex.getMessage(), "과목 자료", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
    public void closeInline() { controller.closeInline(); }

    public boolean isBusy() { return controller.isWorking(); }
    public void closePopup() { controller.closePreview(); }

    public void openCourse(JComponent anchor,PlannerModel.Event event) {
        if(isBusy()||closed)return;
        Building building=map.building(event.buildingId());
        if(building==null)return;
        try {
            Path path=catalog.prepareCourse(new FolderService(buildingsRoot),event);
            controller.showFolderPreview(anchor,path,this::refreshRecent);
        } catch(IOException ex) { JOptionPane.showMessageDialog(anchor,ex.getMessage(),"과목 자료",JOptionPane.ERROR_MESSAGE); }
    }

    public Path courseDirectory(PlannerModel.Event event) throws IOException {
        if(closed||map.building(event.buildingId())==null)throw new IOException("과목 장소를 찾을 수 없습니다.");
        return catalog.prepareCourse(new FolderService(buildingsRoot),event);
    }
    public void openCourseDirectory(Component owner,PlannerModel.Event event) {
        if(isBusy()||closed)return;
        try { FolderService.openDirectory(courseDirectory(event)); }
        catch(IOException ex){JOptionPane.showMessageDialog(owner,ex.getMessage(),"원본 폴더 열기",JOptionPane.ERROR_MESSAGE);}
    }

    public void moveIntoCourse(JComponent owner,PlannerModel.Event event,List<File> files) {
        if(isBusy()||closed)return;
        try {
            Path target=catalog.prepareCourse(new FolderService(buildingsRoot),event);
            controller.receiveFilesInFolder(SwingUtilities.getWindowAncestor(owner),target,List.copyOf(files),this::refreshRecent);
        } catch(IOException ex) { JOptionPane.showMessageDialog(owner,ex.getMessage(),"자료 이동",JOptionPane.ERROR_MESSAGE); }
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
        // 버튼 박스 없이 작은 아이콘만 보이고, 건물이나 아이콘에 마우스를 올리면 크게 보인다.
        button.setContentAreaFilled(false); button.setBorderPainted(false); button.setFocusPainted(false);
        button.setOpaque(false); button.setMargin(new Insets(0,0,0,0));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.getModel().addChangeListener(e -> showIcon(button));
        button.getAccessibleContext().setAccessibleName(building.name() + " 최근 파일");
        button.addActionListener(e -> {
            if(controller.latest(id)!=null)controller.openLatest(SwingUtilities.getWindowAncestor(button), id);

        });
        Runnable update = () -> {
            Path file = controller.latest(id);
            button.setVisible(file!=null);
            button.setToolTipText(file!=null?"최근 파일: "+file.getFileName():null);
            String type=file!=null?FolderService.extension(file).toUpperCase(java.util.Locale.ROOT):"";
            boolean show=file!=null;
            button.putClientProperty("icon.small",show?new DocumentIcon(type,file==null,SMALL_ICON):null);
            button.putClientProperty("icon.large",show?new DocumentIcon(type,file==null,LARGE_ICON):null);
            showIcon(button);
            button.getAccessibleContext().setAccessibleName(building.name()+" 최근 파일");
        };
        button.putClientProperty("recent.update",update);
        updates.add(update);
        update.run();
        return button;
    }

    private static final int SMALL_ICON = 12, LARGE_ICON = 24;

    /** 아이콘 위에 마우스가 있거나 지도에서 그 건물 위에 마우스가 있으면 큰 아이콘을 쓴다. */
    private static void showIcon(JButton button) {
        boolean large=button.getModel().isRollover()||Boolean.TRUE.equals(button.getClientProperty("map.hover"));
        Object icon=button.getClientProperty(large?"icon.large":"icon.small");
        Icon next=icon instanceof Icon value?value:null;
        if(button.getIcon()!=next)button.setIcon(next);
    }

    public void refreshRecent() { List.copyOf(updates).forEach(Runnable::run); }

    /**
     * 작은 아이콘이 건물 밖으로 나가지 않는 위치 중 오른쪽 위에 가장 가까운 곳을 고른다.
     * 건물이 아이콘보다 작게 그려질 때는 건물 중심을 쓴다.
     */
    public static Point iconCenter(java.awt.geom.Path2D shape, java.awt.geom.Point2D fallback) {
        java.awt.geom.Rectangle2D bounds = shape.getBounds2D();
        double half = SMALL_ICON / 2.0 + 1;
        Point best = new Point((int)Math.round(fallback.getX()), (int)Math.round(fallback.getY()));
        if (bounds.getWidth() < half * 2 || bounds.getHeight() < half * 2) return best;
        double step = Math.max(1, Math.max(bounds.getWidth(), bounds.getHeight()) / 80), bestScore = Double.NEGATIVE_INFINITY;
        for (double y = bounds.getMinY() + half; y <= bounds.getMaxY() - half; y += step) {
            for (double x = bounds.getMaxX() - half; x >= bounds.getMinX() + half; x -= step) {
                double score = (x - bounds.getMinX()) / bounds.getWidth() - (y - bounds.getMinY()) / bounds.getHeight();
                if (score <= bestScore) break; // 같은 줄에서 더 왼쪽은 점수가 낮아진다.
                if (shape.contains(x - half, y - half, half * 2, half * 2)) {
                    bestScore = score; best = new Point((int)Math.round(x), (int)Math.round(y)); break;
                }
            }
        }
        return best;
    }

    /** 빈 지도 패널용: 최근 파일 버튼을 건물 안쪽 오른쪽 위에 배치하고 확대/이동 시 위치를 갱신한다. */
    public void attach(JComponent canvas, Function<Point, Building> hitTest,
                       Function<CampusMap.GeoPoint, java.awt.geom.Point2D> project) {
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
                if (!button.isVisible()) return;
                java.awt.geom.Path2D shape = new java.awt.geom.Path2D.Double();
                for (CampusMap.GeoPoint corner : building.outline()) {
                    java.awt.geom.Point2D p = project.apply(corner);
                    if (shape.getCurrentPoint() == null) shape.moveTo(p.getX(), p.getY()); else shape.lineTo(p.getX(), p.getY());
                }
                java.awt.geom.Point2D middle = project.apply(building.center());
                // 지도가 움직이지 않았으면 이전 계산을 다시 쓴다(위치 갱신 타이머가 0.1초마다 돈다).
                String key = shape.getBounds2D() + "|" + middle;
                if (!key.equals(button.getClientProperty("icon.key"))) {
                    button.putClientProperty("icon.center", iconCenter(shape, middle));
                    button.putClientProperty("icon.key", key);
                }
                Point center = (Point) button.getClientProperty("icon.center");
                int x=center.x-16, y=center.y-16;
                if(button.getX()!=x || button.getY()!=y || button.getWidth()!=32)
                    button.setBounds(x,y,32,32);
            });
        };
        Building[] hovered = { null };
        java.util.function.Consumer<Building> hover = building -> {
            if (java.util.Objects.equals(building, hovered[0])) return;
            for (Building old : new Building[] { hovered[0], building }) {
                JButton button = old == null ? null : buttons.get(old);
                if (button != null) { button.putClientProperty("map.hover", old.equals(building)); showIcon(button); }
            }
            hovered[0] = building;
        };
        MouseAdapter movement = new MouseAdapter() {
            public void mouseDragged(MouseEvent e) { SwingUtilities.invokeLater(position); }
            public void mouseWheelMoved(MouseWheelEvent e) { SwingUtilities.invokeLater(position); }
            public void mouseMoved(MouseEvent e) { hover.accept(hitTest.apply(e.getPoint())); }
            public void mouseExited(MouseEvent e) { hover.accept(null); }
        };
        ComponentAdapter resize = new ComponentAdapter() {
            public void componentResized(ComponentEvent e) { position.run(); }
        };
        // 키보드/버튼으로 수행된 지도 zoom도 감지한다. 파일 시스템 조회는 하지 않는다.
        Timer positions = new Timer(100, e -> { if(canvas.isShowing())position.run(); });
        positions.start();
        canvas.addMouseListener(movement);
        canvas.addMouseMotionListener(movement);
        canvas.addMouseWheelListener(movement);
        canvas.addComponentListener(resize);
        updates.add(position);
        position.run();
        detachments.add(() -> {
            positions.stop();
            canvas.removeMouseListener(movement);
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
        canvas.setTransferHandler(transfer);
        detachments.add(() -> {
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
