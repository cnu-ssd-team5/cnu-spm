package campus.folder.ui;
import javax.swing.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
/** 외부 드롭은 복사, 자료함에서 시작한 드롭은 기본 이동(Ctrl이면 복사). */
public final class FolderTransferHandler extends TransferHandler {
    private static final DataFlavor INTERNAL=new DataFlavor("application/x-test-drag;class=java.lang.String","Test drag");
    public interface Receiver {
        void receive(List<Path> sources,Path destination,boolean move);
    }
    private final Supplier<Path> current;
    private final Receiver receiver;
    private final BooleanSupplier busy;
    public FolderTransferHandler(Supplier<Path> current,Receiver receiver,BooleanSupplier busy) {
        this.current=current;
        this.receiver=receiver;
        this.busy=busy;
    }
    public int getSourceActions(JComponent component) {
        return busy.getAsBoolean()?NONE:COPY_OR_MOVE;
    }
    protected Transferable createTransferable(JComponent component) {
        if(busy.getAsBoolean())return null;
        JList<?> list=(JList<?>)component;
        List<File> selected=new ArrayList<>();
        for (Object p:list.getSelectedValuesList())selected.add(((Path)p).toFile()); if(selected.isEmpty())return null; return new Transferable() {
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] {
                    DataFlavor.javaFileListFlavor,INTERNAL
                };
            }
            public boolean isDataFlavorSupported(DataFlavor f) {
                return f.equals(INTERNAL)||f.equals(DataFlavor.javaFileListFlavor);
            }
            public Object getTransferData(DataFlavor f)throws UnsupportedFlavorException {
                if(f.equals(INTERNAL))return "internal";
                if(f.equals(DataFlavor.javaFileListFlavor))return selected;
                throw new UnsupportedFlavorException(f);
            }
        };
    }
    private Path destination(TransferSupport support) {
        Path target=current.get();
        if(support.isDrop()&&support.getDropLocation() instanceof JList.DropLocation) {
            JList.DropLocation drop=(JList.DropLocation)support.getDropLocation();
            JList<?> list=(JList<?>)support.getComponent();
            if(list instanceof MarqueeFileList && !((MarqueeFileList)list).itemAt(drop.getDropPoint()))return target;
            int i=drop.getIndex();
            if(i>=0&&i<list.getModel().getSize()) {
                Object p=list.getModel().getElementAt(i);
                if(p instanceof Path&&Files.isDirectory((Path)p))target=(Path)p;
            }
        }
        return target;
    }
    public boolean canImport(TransferSupport support) {
        if(busy.getAsBoolean()||!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor))return false;
        if(support.isDrop()&&!support.isDataFlavorSupported(INTERNAL))support.setDropAction(COPY);
        return true;
    }
    public boolean importData(TransferSupport support) {
        if(!canImport(support))return false;
        try {
            List<?> incoming=(List<?>)support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            List<Path> paths=new ArrayList<>();
            for (Object f:incoming)paths.add(((File)f).toPath()); boolean move=support.isDrop()&&(!support.isDataFlavorSupported(INTERNAL)||support.getDropAction()==MOVE); receiver.receive(paths,destination(support),move);
            return true;
        }
        catch(Exception e) {
            return false;
        }
    }
}
