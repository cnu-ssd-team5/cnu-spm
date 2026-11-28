package campus.ui;
import campus.folder.FolderService;
import java.awt.Window;
import java.nio.file.*;
import javax.swing.JOptionPane;
/** 지도와 미니 자료함을 연결하고 건물별 실행 취소 기록을 유지한다. */
public final class BuildingFolderController {
    private final FolderService service;
    private MiniFolderPopup preview;
    private FolderBrowserPanel inline;
    private final campus.folder.CourseCatalog catalog;
    private boolean moving;
    private Runnable folderChanged=()->{};
    public void setFolderChanged(Runnable listener){folderChanged=java.util.Objects.requireNonNull(listener);}
    private Path previewBuilding;
    private final java.util.Map<Path,javax.swing.Timer> cleanupTimers=new java.util.HashMap<>();
    private final java.util.List<MapFileSearch> searches=new java.util.ArrayList<>();
    public void close() {
        if(isWorking())return;
        closePreview();
        closeInline();
        searches.forEach(java.awt.Window::dispose);
        searches.clear();
        cleanupTimers.values().forEach(javax.swing.Timer::stop);cleanupTimers.clear();
    }
    public boolean isWorking() { return moving || preview!=null && preview.isWorking() || inline!=null && inline.isWorking(); }
    public Path initial(Path building) { return catalog.initial(building); }
    public String displayName(Path building) { return catalog.displayName(building.getFileName().toString()); }
    private final java.util.Map<Path,campus.folder.FileOperations> operations=new java.util.HashMap<>();
    public campus.folder.FileOperations operationsFor(Path building)throws java.io.IOException {
        Path key=service.checked(building);
        if(!operations.containsKey(key))operations.put(key,new campus.folder.FileOperations(service));
        return operations.get(key);
    }
    public BuildingFolderController(FolderService service, campus.folder.CourseCatalog catalog) {
        this.service = service;
        this.catalog = catalog;
    }
    public void scheduleChanged() {
        closePreview();
        closeInline();
        operations.clear(); // 이전 경로를 가리키는 실행 취소 기록은 사용할 수 없다.
    }
    public void closePreview() {
        if(preview!=null && !isWorking()) {
            preview.dispose();
            preview=null;
        }
    }
    public void searchFiles(Window owner,String query,Runnable updated) {
        if(isWorking())return;
        closePreview();
        searches.removeIf(dialog->!dialog.isDisplayable());
        searches.add(new MapFileSearch(owner,service,query,updated,(folder,anchor)->showFolderPreview(anchor,folder,updated),path->{
            Path relative=service.getRoot().relativize(path);
            return catalog.displayName(relative.getName(0).toString())+(relative.getNameCount()>1?" / "+relative.subpath(1,relative.getNameCount()):"");
        }));
    }
    public Path latest(String buildingName) {
        try {
            Path building=service.getRoot().resolve(buildingName);
            if(!Files.isDirectory(building))return null;
            java.util.List<Path> recent=new campus.folder.RecentFiles(service,building).list();
            return recent.isEmpty()?null:recent.get(0);
        }
        catch(Exception e) {
            return null;
        }
    }
    public void openLatest(Window owner,String buildingName) {
        try {
            Path path=latest(buildingName);
            if(path==null)return;
            java.awt.Desktop.getDesktop().open(path.toFile());
            new campus.folder.RecentFiles(service,service.getRoot().resolve(buildingName)).record(path);
        }
        catch(Exception e) {
            JOptionPane.showMessageDialog(owner,e.getMessage());
        }
    }
    public void receiveFiles(Window owner,String buildingName,java.util.List<java.io.File> files,Runnable updated) {
        if(isWorking())return;
        try {
            Path building=catalog.prepare(service,buildingName);
            java.util.List<String> courses=catalog.names(buildingName);
            String selected=courses.isEmpty()?null:courses.size()==1?courses.get(0):(String)JOptionPane.showInputDialog(owner,"강의 선택",catalog.displayName(buildingName),JOptionPane.PLAIN_MESSAGE,null,courses.toArray(),courses.get(0));
            if(!courses.isEmpty()&&selected==null)return;
            Path target=selected==null?building:building.resolve(selected);
            receiveFilesInFolder(owner,target,files,updated);
        } catch(Exception e) { JOptionPane.showMessageDialog(owner,e.getMessage()); }
    }
    public void receiveFilesInFolder(Window owner,Path destination,java.util.List<java.io.File> files,Runnable updated) {
        if(isWorking())return;
        try {
            Path target=service.checked(destination);
            if(target.equals(service.getRoot()))throw new java.io.IOException("자료를 넣을 폴더를 선택하세요.");
            Path building=service.getRoot().resolve(service.getRoot().relativize(target).getName(0));
            campus.folder.FileOperations ops=operationsFor(building);
            closePreview();
            moving=true;
            new javax.swing.SwingWorker<Void,Void>() {
                protected Void doInBackground()throws Exception {
                    ops.paste(files.stream().map(java.io.File::toPath).toList(),target,true);
                    return null;
                }
                protected void done() {
                    moving=false;
                    try { get(); } catch(Exception e) { JOptionPane.showMessageDialog(owner,e.getCause()==null?e.getMessage():e.getCause().getMessage()); }
                    updated.run();
                }
            }.execute();
        } catch(Exception e) { JOptionPane.showMessageDialog(owner,e.getMessage()); }
    }
    /** 목록 조회나 OneDrive 등의 일시 점유가 끝난 뒤에도 빈 폴더를 정리할 수 있도록 재시도한다. */
    public void previewClosed(Path building) {
        cancelCleanup(building);
        cleanup(building,0);
    }
    private void cancelCleanup(Path building) {
        javax.swing.Timer timer=cleanupTimers.remove(building);
        if(timer!=null)timer.stop();
    }
    private void cleanup(Path building,int attempt) {
        if(building.equals(previewBuilding)&&preview!=null&&preview.isDisplayable())return;
        if(!catalog.names(building.getFileName().toString()).isEmpty())return;
        try {
            if(isWorking()) { retryCleanup(building,attempt,null);return; }
            if(campus.folder.EmptyFolderCleanup.remove(service,building)) {
                // 폴더를 다시 열어 Ctrl+Z로 삭제 파일을 복구할 수 있도록 실행 취소 기록은 유지한다.
                if(attempt>0)cleanupLog(building,attempt,"정리 완료",null);
                folderChanged.run();
            } else cleanupLog(building,attempt,"보존: 파일·링크 또는 새 항목이 있음",null);
        } catch(java.io.IOException ex) { retryCleanup(building,attempt,ex); }
    }
    private void retryCleanup(Path building,int attempt,java.io.IOException failure) {
        if(failure!=null)cleanupLog(building,attempt,attempt<4?"정리 재시도 예정":"정리 실패 (폴더 보존)",failure);
        if(attempt>=4)return;
        int[] delays={250,750,1500,3000};
        javax.swing.Timer timer=new javax.swing.Timer(delays[attempt],e->{cleanupTimers.remove(building);cleanup(building,attempt+1);});
        timer.setRepeats(false);cleanupTimers.put(building,timer);timer.start();
    }
    private void cleanupLog(Path building,int attempt,String state,java.io.IOException failure) {
        String detail=failure==null?"":failure.getClass().getName()+": "+failure.getMessage();
        if(failure instanceof java.nio.file.FileSystemException fs)detail+="; reason="+fs.getReason();
        String attributes="";
        try { if(Files.exists(building))attributes="; dos="+Files.readAttributes(building,"dos:readonly,hidden,system,archive",LinkOption.NOFOLLOW_LINKS); }
        catch(Exception ignored) { }
        String message=java.time.OffsetDateTime.now()+" ["+state+"] attempt="+(attempt+1)+" path="+building+" "+detail+attributes+System.lineSeparator();
        try { Files.writeString(service.getRoot().getParent().resolve("folder-cleanup.log"),message,
                java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND); }
        catch(java.io.IOException logFailure) {System.err.print(message);}
    }
    public FolderBrowserPanel openInline(Path folder, Runnable updated) throws java.io.IOException {
        Path target = service.checked(folder);
        Path building = service.getRoot().resolve(service.getRoot().relativize(target).getName(0));
        closePreview();
        closeInline();
        cancelCleanup(building);
        FolderBrowserPanel browser = new FolderBrowserPanel(this, service, building, updated, null);
        browser.setName("inline-course-files");
        browser.setPreferredSize(new java.awt.Dimension(0, 265));
        browser.startAt(target);
        inline = browser;
        return browser;
    }
    public void closeInline() {
        if (inline == null || inline.isWorking()) return;
        var parent = inline.getParent();
        if (parent != null) {
            parent.remove(inline);
            parent.revalidate();
            parent.repaint();
        }
        inline.release();
        inline = null;
    }
    public void showFolderPreview(javax.swing.JComponent anchor,Path folder,Runnable updated) {
        if(isWorking())return;
        try {
            Path target=service.checked(folder);
            if(target.equals(service.getRoot())||!Files.isDirectory(target))
                throw new java.io.IOException("폴더를 찾을 수 없습니다. 다시 검색해 주세요.");
            if(preview!=null&&preview.isDisplayable()) {
                if(preview.isWorking())return;
                preview.dispose();
            }
            Path building=service.getRoot().resolve(service.getRoot().relativize(target).getName(0));
            cancelCleanup(building);previewBuilding=building;
            Files.createDirectories(target);
            preview=new MiniFolderPopup(javax.swing.SwingUtilities.getWindowAncestor(anchor),this,service,building,updated);
            preview.startAt(target);
            preview.showBeside(anchor);
            updated.run();
        } catch(Exception e) {
            JOptionPane.showMessageDialog(anchor,e.getMessage());
        }
    }
}
