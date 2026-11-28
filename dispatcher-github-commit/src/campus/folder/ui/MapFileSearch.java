package campus.folder.ui;
import campus.folder.storage.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
/** 지도 검색 결과에는 건물/강의 경로를 함께 표시한다. */
public final class MapFileSearch extends JDialog {
    public MapFileSearch(Window owner,FolderService service,String keyword,Runnable updated,
                         java.util.function.BiConsumer<Path,JComponent> openFolder, java.util.function.Function<Path,String> displayPath) {
        super(owner,"파일 검색 · "+keyword,ModalityType.MODELESS);
        DefaultListModel<Path> model=new DefaultListModel<>();
        JList<Path> results=new JList<>(model);
        JLabel status=new JLabel("검색 중...");
        results.setFixedCellHeight(36);
        results.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f) {
                super.getListCellRendererComponent(l,v,i,s,f);
                Path path=(Path)v;
                setText(displayPath.apply(path));
                String type=FolderService.extension(path).toUpperCase(Locale.ROOT);
                setIcon(new DocumentIcon(type.isEmpty()?"FILE":type,Files.isDirectory(path),26));
                setIconTextGap(8);
                setToolTipText(path.toString());
                return this;
            }
        });
        Runnable open=()-> {
            Path p=results.getSelectedValue();
            if(p==null)return;
            try {
                service.checked(p);
                if(Files.isDirectory(p)) {
                    openFolder.accept(p,results);
                    return;
                }
                if(!Files.isRegularFile(p))throw new java.io.IOException("파일이 이동되거나 삭제되었습니다. 다시 검색해 주세요.");
                Desktop.getDesktop().open(p.toFile());
                Path building=service.getRoot().resolve(service.getRoot().relativize(p).getName(0));
                new RecentFiles(service,building).record(p);
                updated.run();
            }
            catch(Exception ex) {
                JOptionPane.showMessageDialog(this,ex.getMessage());
            }
        };
        results.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int i=results.locationToIndex(e.getPoint());
                if(SwingUtilities.isLeftMouseButton(e)&&e.getClickCount()==2&&i>=0&&results.getCellBounds(i,i).contains(e.getPoint()))open.run();
            }
        });
        results.getInputMap().put(KeyStroke.getKeyStroke("ENTER"),"open");
        results.getActionMap().put("open",new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                open.run();
            }
        });
        JPanel panel=new JPanel(new BorderLayout(8,8));
        panel.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        panel.add(status,BorderLayout.NORTH);
        panel.add(new JScrollPane(results));
        setContentPane(panel);
        setSize(620,400);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        SwingWorker<List<Path>,Void> worker=new SwingWorker<List<Path>,Void>() {
            protected List<Path> doInBackground()throws Exception {
                List<Path> found=new ArrayList<>();
                String q=keyword.toLowerCase(Locale.ROOT);
                try(java.util.stream.Stream<Path> paths=Files.walk(service.getRoot())) {
                    Iterator<Path> it=paths.iterator();
                    while(it.hasNext()&&!isCancelled()) {
                        Path p=it.next();
                        if(matches(service.getRoot(),p,q))found.add(p);
                    }
                }
                found.sort(Comparator.comparing((Path p)->!Files.isDirectory(p))
                        .thenComparing(p->service.getRoot().relativize(p).toString()));
                return found;
            }
            protected void done() {
                if(isCancelled()||!isDisplayable())return;
                try {
                    for(Path p:get())model.addElement(p);
                    status.setText(model.size()+"개");
                }
                catch(Exception e) {
                    status.setText("검색 실패: "+e.getMessage());
                }
            }
        };
        addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                worker.cancel(true);
            }
        });
        setVisible(true);
        worker.execute();
    }
    public static boolean matches(Path root,Path path,String query) {
        return !path.equals(root)&&!Files.isSymbolicLink(path)
                &&(Files.isDirectory(path)||Files.isRegularFile(path))
                &&!path.getFileName().toString().startsWith(".")
                &&path.getFileName().toString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
}
