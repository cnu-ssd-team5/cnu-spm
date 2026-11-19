package campus.folder.ui;
import campus.folder.storage.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
/** 지도 위에 유지되는 작은 비모달 작업 창. 메뉴/입력창을 열어도 사라지지 않는다. */
public final class MiniFolderPopup extends JDialog {
    private final FolderService service;
    private final BuildingFolderController controller;
    private final Path building;
    private final FileOperations operations;
    private final Runnable updated;
    private Path current;
    private boolean working;
    private final Deque<Path> back=new ArrayDeque<>(),forward=new ArrayDeque<>();
    private final DefaultListModel<Path> model=new DefaultListModel<>();
    private final MarqueeFileList list=new MarqueeFileList(model);
    private final JLabel status=new JLabel(),location=new JLabel();
    private final JButton previous=new JButton("←"),next=new JButton("→");
    private static List<Path> clipboard=new ArrayList<>();
    private static boolean cut;
    private int loadVersion;
    private volatile boolean disposed;
    private final AWTEventListener side=event-> {
        if(event instanceof MouseEvent) {
            MouseEvent e=(MouseEvent)event;
            if(e.getID()==MouseEvent.MOUSE_PRESSED&&e.getSource() instanceof Component&&SwingUtilities.getWindowAncestor((Component)e.getSource())==this) {
                if(e.getButton()==4)travel(back,forward);
                if(e.getButton()==5)travel(forward,back);
            }
        }
    };
    public MiniFolderPopup(Window owner,BuildingFolderController controller,FolderService service,Path building,Runnable updated)throws Exception {
        super(owner);
        setUndecorated(true);
        setModal(false);
        this.service=service;
        this.controller=controller;
        this.building=building;
        this.updated=updated;
        current=controller.initial(building);
        operations=controller.operationsFor(building);
        JPanel panel=new JPanel(new BorderLayout(6,8));
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(174,194,222)),BorderFactory.createEmptyBorder(8,8,8,8)));
        JPanel header=new JPanel(new BorderLayout(6,7));
        JPanel navigation=new JPanel(new FlowLayout(FlowLayout.LEFT,3,0));
        previous.setToolTipText("뒤로");
        next.setToolTipText("앞으로");
        previous.addActionListener(e->travel(back,forward));
        next.addActionListener(e->travel(forward,back));
        navigation.add(previous);
        navigation.add(next);

        JPanel top=new JPanel(new BorderLayout());
        top.add(navigation,BorderLayout.WEST);
        JPanel right=new JPanel(new FlowLayout(FlowLayout.RIGHT,3,0));
        right.add(button("×",this::close));
        top.add(right,BorderLayout.EAST);
        header.add(top,BorderLayout.NORTH);
        location.setForeground(new Color(40,77,124));
        header.add(location,BorderLayout.CENTER);
        panel.add(header,BorderLayout.NORTH);
        list.setFixedCellHeight(36);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f) {
                super.getListCellRendererComponent(l,v,i,s,f);
                Path p=(Path)v;
                String label=p.getFileName().toString();
                setText(label);
                setIcon(new DocumentIcon(FolderService.extension(p).toUpperCase(),Files.isDirectory(p),28));
                setToolTipText(p.toString());
                setBorder(BorderFactory.createEmptyBorder(7,8,7,8));
                return this;
            }
        });
        list.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                popup(e);
            }
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }
            public void mouseClicked(MouseEvent e) {
                if(SwingUtilities.isLeftMouseButton(e)&&e.getClickCount()==2&&list.itemAt(e.getPoint()))open();
            }
        });
        list.setDragEnabled(true);
        list.setDropMode(DropMode.ON);
        list.setTransferHandler(new FolderTransferHandler(()->current,(sources,target,move)->run(()->operations.paste(sources,target,move)),()->working));
        list.addListSelectionListener(e-> {
            if(!working)status.setText(model.size()+"개 · "+list.getSelectedIndices().length+"개 선택");
        });
        panel.add(new JScrollPane(list),BorderLayout.CENTER);
        panel.add(status,BorderLayout.SOUTH);
        bind("ENTER",this::open);
        bind("control C",()->copy(false));
        bind("control X",()->copy(true));
        bind("control V",this::paste);
        bind("control Z",()->run(()->operations.undo()));
        bind("DELETE",this::delete);
        bind("F2",this::rename);
        bind("control A",()-> {
            if(!model.isEmpty())list.setSelectionInterval(0,model.size()-1);
        });
        getRootPane().registerKeyboardAction(e->close(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e->travel(back,forward),KeyStroke.getKeyStroke("alt LEFT"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e->travel(forward,back),KeyStroke.getKeyStroke("alt RIGHT"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e-> {
            if(!working)run(()->operations.undo());
        }
        ,KeyStroke.getKeyStroke("control Z"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        setContentPane(panel);
        UiTheme.style(panel);
        setSize(365,300);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                close();
            }
        });
        reload();
    }
    public boolean isWorking() {
        return working;
    }
    public void startAt(Path folder) throws java.io.IOException {
        Path target=service.checked(folder);
        if(!target.startsWith(building)||!Files.isDirectory(target))
            throw new java.io.IOException("이 건물에 속하는 폴더를 선택해 주세요.");
        current=target;
        back.clear();
        forward.clear();
        reload();
    }
    public void showBeside(JComponent anchor) {

        showAt(anchor, new Point(anchor.getWidth(), 0));
    }
    public void showAt(JComponent anchor, Point localPoint) {
        Point p=new Point(localPoint);
        SwingUtilities.convertPointToScreen(p,anchor);
        Rectangle screen=anchor.getGraphicsConfiguration().getBounds();
        int left=p.x+6;
        if(left+getWidth()>screen.x+screen.width)left=p.x-getWidth()-6;
        setLocation(Math.max(screen.x,left),Math.max(screen.y,Math.min(p.y,screen.y+screen.height-getHeight())));
        setVisible(true);
        list.requestFocusInWindow();

    }
    public void addNotify() {
        super.addNotify();
        Toolkit.getDefaultToolkit().addAWTEventListener(side,AWTEvent.MOUSE_EVENT_MASK);
    }
    public void removeNotify() {

        Toolkit.getDefaultToolkit().removeAWTEventListener(side);
        loadVersion++;
        super.removeNotify();
    }
    private JButton button(String text,Runnable task) {
        JButton b=new JButton(text);
        b.addActionListener(e-> {
            if(!working)task.run();
        });
        return b;
    }
    private void bind(String key,Runnable task) {
        list.getInputMap().put(KeyStroke.getKeyStroke(key),key);
        list.getActionMap().put(key,new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if(!working)task.run();
            }
        });
    }
    @Override public void dispose() {
        if(working)return;
        boolean wasOpen=isDisplayable();
        disposed=true;
        super.dispose();
        if(wasOpen)controller.previewClosed(building);
    }
    private void close() {
        if(!working) {
            dispose();
            updated.run();
        }
        else status.setText("작업이 끝나면 닫을 수 있습니다.");
    }
    private void error(Exception e) {
        JOptionPane.showMessageDialog(this,e.getMessage(),"처리 실패",JOptionPane.ERROR_MESSAGE);
    }
    private interface Task {
        void execute()throws Exception;
    }
    private void run(Task task) {
        if(working)return;
        working=true;
        status.setText("처리 중...");
        new SwingWorker<Void,Void>() {
            protected Void doInBackground()throws Exception {
                task.execute();
                return null;
            }
            protected void done() {
                working=false;
                try {
                    get();
                }
                catch(Exception e) {
                    error(e);
                }
                reload();
                list.requestFocusInWindow();
                updated.run();
            }
        }
        .execute();
    }
    private void navigate(Path p) {
        if(working||p.equals(current)||!p.startsWith(building)||!Files.isDirectory(p))return;
        back.push(current);
        forward.clear();
        current=p;
        reload();
    }
    private void travel(Deque<Path> from,Deque<Path> to) {
        if(working)return;
        while(!from.isEmpty()) {
            Path p=from.pop();
            if(Files.isDirectory(p)) {
                to.push(current);
                current=p;
                break;
            }
        }
        reload();
    }
    private void reload() {
        if(working)return;
        while(!Files.isDirectory(current)&&!current.equals(building))current=current.getParent();
        final int version=++loadVersion;
        final Path folder=current;
        previous.setEnabled(!back.isEmpty());
        next.setEnabled(!forward.isEmpty());
        location.setText(controller.displayName(building)+" / "+building.relativize(current));
        new SwingWorker<List<Path>,Void>() {
            protected List<Path> doInBackground()throws Exception {
                return !disposed&&Files.exists(folder)?service.list(folder):java.util.List.of();
            }
            protected void done() {
                if(version!=loadVersion)return;
                try {
                    List<Path> paths=get();
                    paths.sort(Comparator.comparing((Path p)->!Files.isDirectory(p)).thenComparing(p->p.getFileName().toString()));
                    model.clear();
                    for(Path p:paths)model.addElement(p);
                    status.setText(model.size()+"개");
                }
                catch(Exception e) {
                    error(e);
                }
            }
        }
        .execute();
    }
    private void open() {
        if(working||list.getSelectedValue()==null)return;
        Path p=list.getSelectedValue();
        try {
            service.checked(p);
            if(Files.isDirectory(p))navigate(p);
            else {
                Desktop.getDesktop().open(p.toFile());
                new RecentFiles(service,building).record(p);
                updated.run();
            }
        }
        catch(Exception e) {
            error(e);
        }
    }
    private void copy(boolean moving) {
        clipboard=new ArrayList<>(list.getSelectedValuesList());
        cut=moving;
        status.setText(clipboard.size()+"개 "+(moving?"잘라내기 대기":"복사됨"));
    }
    private void paste() {
        if(clipboard.isEmpty())return;
        List<Path> sources=new ArrayList<>(clipboard);
        boolean move=cut;
        Path target=current;
        run(()-> {
            operations.paste(sources,target,move);
            if(move)clipboard=new ArrayList<>();
        });
    }
    private void delete() {
        List<Path> selected=new ArrayList<>(list.getSelectedValuesList());
        if(!selected.isEmpty()&&JOptionPane.showConfirmDialog(this,selected.size()+"개를 삭제할까요? Ctrl+Z로 복원할 수 있습니다.","삭제",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)run(()->operations.delete(selected));
    }
    private void rename() {
        Path p=list.getSelectedValue();
        if(p==null)return;
        String name=JOptionPane.showInputDialog(this,"새 이름",p.getFileName().toString());
        if(name!=null)run(()->operations.rename(p,service.checked(p.resolveSibling(FolderService.validName(name)))));
    }
    private void create(boolean note) {
        final List<Path> selected=new ArrayList<>(list.getSelectedValuesList());
        String name=JOptionPane.showInputDialog(this,note?"메모 파일 이름":selected.isEmpty()?"새 폴더 이름":"선택한 "+selected.size()+"개 항목을 담을 새 폴더 이름",note?"새 메모.txt":"새 폴더");
        if(name==null)return;
        if(!note){Path parent=current;run(()->operations.createFolderWithSelection(parent,name,selected));return;}
        try {
            Path p=service.saveNote(current,null,name,"");
            operations.created(p);
            reload();
            Desktop.getDesktop().open(p.toFile());
            new RecentFiles(service,building).record(p);
            updated.run();
        }
        catch(Exception e) {
            error(e);
        }
    }
    private void popup(MouseEvent e) {
        if(!e.isPopupTrigger()||working)return;
        int i=list.locationToIndex(e.getPoint());
        if(list.itemAt(e.getPoint())) {
            if(!list.isSelectedIndex(i))list.setSelectedIndex(i);
        }
        else list.clearSelection();
        menu(e);
    }
    private void item(JPopupMenu m,String text,Runnable task) {
        JMenuItem i=new JMenuItem(text);
        i.addActionListener(e-> {
            if(!working)task.run();
        });
        m.add(i);
    }
    private void menu(MouseEvent e) { contextMenu().show(list,e.getX(),e.getY()); }
    private JPopupMenu contextMenu() {
        JPopupMenu m=new JPopupMenu();
        if(!list.isSelectionEmpty()) {
            item(m,"열기",this::open);
            item(m,"복사 · Ctrl+C",()->copy(false));
            item(m,"잘라내기 · Ctrl+X",()->copy(true));
            item(m,"이름 변경 · F2",this::rename);
            item(m,"삭제 · Delete",this::delete);
            m.addSeparator();
        }
        item(m,"붙여넣기 · Ctrl+V",this::paste);
        item(m,"실행 취소 · Ctrl+Z",()->run(()->operations.undo()));
        item(m,list.isSelectionEmpty()?"새 폴더":"선택 항목을 새 폴더에 담기",()->create(false));
        item(m,"새 메모",()->create(true));
        m.addSeparator();
        item(m,"원본 폴더 열기",()->{
            try { FolderService.openDirectory(service.checked(current)); }
            catch(Exception error) { error(error); }
        });

        return m;
    }
}
