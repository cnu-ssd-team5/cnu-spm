package campus.materials;

import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.*;

/** Accepts OS files without asking the source application to delete them again. */
public final class CourseFileTransferHandler extends TransferHandler {
    private final BooleanSupplier available;
    private final Consumer<List<File>> receiver;
    public CourseFileTransferHandler(BooleanSupplier available,Consumer<List<File>> receiver) {
        this.available=available;this.receiver=receiver;
    }
    @Override public boolean canImport(TransferSupport support) {
        if(!available.getAsBoolean()||!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor))return false;
        if(support.isDrop()) {
            if((support.getSourceDropActions()&COPY)==0)return false;
            support.setDropAction(COPY);
        }
        return true;
    }
    @Override public boolean importData(TransferSupport support) {
        if(!canImport(support))return false;
        try {
            Object value=support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            if(!(value instanceof List<?> list)||list.isEmpty())return false;
            List<File> files=new ArrayList<>();
            for(Object item:list) { if(!(item instanceof File file))return false;files.add(file); }
            List<File> selected=List.copyOf(files);
            SwingUtilities.invokeLater(()->{if(available.getAsBoolean())receiver.accept(selected);});
            return true;
        } catch(Exception failure) { return false; }
    }
}
