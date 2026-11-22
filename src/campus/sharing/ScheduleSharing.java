package campus.sharing;

import campus.model.*;
import java.awt.Component;
import java.io.IOException;
import java.nio.file.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** 일정 XML만 공유한다. 로컬 자료 파일과 자료함 연결 정보는 포함하지 않는다. */
public final class ScheduleSharing {
    private ScheduleSharing() { }
    public static void exportFile(Component owner,PlannerModel model) throws IOException {
        JFileChooser chooser=chooser();chooser.setSelectedFile(new java.io.File("campus-schedules.xml"));
        if(chooser.showSaveDialog(owner)!=JFileChooser.APPROVE_OPTION)return;
        Path path=chooser.getSelectedFile().toPath().toAbsolutePath();
        if(!path.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".xml"))path=path.resolveSibling(path.getFileName()+".xml");
        if(Files.exists(path)&&JOptionPane.showConfirmDialog(owner,"기존 파일을 덮어쓸까요?","일정 공유",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        PlannerStore.saveSharedSchedule(path,model);
    }
    public static boolean importFile(Component owner,PlannerModel model) throws IOException {
        PlannerModel incoming = readFile(owner);
        if (incoming == null) return false;
        model.mergeFrom(incoming);
        return true;
    }
    public static PlannerModel readFile(Component owner) throws IOException {
        JFileChooser chooser = chooser();
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return null;
        return PlannerStore.load(chooser.getSelectedFile().toPath());
    }
    private static JFileChooser chooser() {
        JFileChooser chooser=new JFileChooser();chooser.setFileFilter(new FileNameExtensionFilter("일정 XML","xml"));return chooser;
    }
}
