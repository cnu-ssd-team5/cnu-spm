package campus.folder.ui;
import campus.folder.storage.FolderService;
import java.awt.Window;
import java.nio.file.*;
import javax.swing.JOptionPane;
/** 지도와 미니 자료함을 연결하고 건물별 실행 취소 기록을 유지한다. */
public final class BuildingFolderController {
    private final FolderService service;
    private MiniFolderPopup preview;
    private final campus.folder.storage.CourseCatalog catalog;
    private boolean moving;
    private final java.util.List<MapFileSearch> searches=new java.util.ArrayList<>();
    public void close() {
        if(isWorking())return;
        closePreview();
        searches.forEach(java.awt.Window::dispose);
        searches.clear();
    }
    public boolean isWorking() { return moving || preview!=null && preview.isWorking(); }
    public Path initial(Path building) { return catalog.initial(building); }
    public String displayName(Path building) { return catalog.displayName(building.getFileName().toString()); }
    private final java.util.Map<Path,campus.folder.storage.FileOperations> operations=new java.util.HashMap<>();
    public campus.folder.storage.FileOperations operationsFor(Path building)throws java.io.IOException {
        Path key=service.checked(building);
        if(!operations.containsKey(key))operations.put(key,new campus.folder.storage.FileOperations(service));
        return operations.get(key);
    }
    public BuildingFolderController(FolderService service, campus.folder.storage.CourseCatalog catalog) {
        this.service = service;
        this.catalog = catalog;
    }
    public void scheduleChanged() {
        closePreview();
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
            java.util.List<Path> recent=new campus.folder.storage.RecentFiles(service,building).list();
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
            new campus.folder.storage.RecentFiles(service,service.getRoot().resolve(buildingName)).record(path);
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
            campus.folder.storage.FileOperations ops=operationsFor(building);
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
    public void showPreview(javax.swing.JComponent anchor,java.awt.Point point,String name,Runnable updated) {
        if(isWorking())return;
        try {
            if(preview!=null&&preview.isDisplayable()) {
                if(preview.isWorking())return;
                preview.dispose();
            }
            Path building=catalog.prepare(service,name);
            preview=new MiniFolderPopup(javax.swing.SwingUtilities.getWindowAncestor(anchor),this,service,building,updated);
            preview.showAt(anchor,point);
        }
        catch(Exception e) {
            JOptionPane.showMessageDialog(anchor,e.getMessage());
        }
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
            preview=new MiniFolderPopup(javax.swing.SwingUtilities.getWindowAncestor(anchor),this,service,building,updated);
            preview.startAt(target);
            preview.showBeside(anchor);
        } catch(Exception e) {
            JOptionPane.showMessageDialog(anchor,e.getMessage());
        }
    }
}
