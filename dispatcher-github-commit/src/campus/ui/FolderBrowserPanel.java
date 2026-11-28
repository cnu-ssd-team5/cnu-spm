package campus.ui;
import campus.folder.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
/** 인라인 자료함과 별도 창이 공유하는 파일 브라우저. */
public final class FolderBrowserPanel extends JPanel {
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
    private final JLabel status=Theme.label("",11,Theme.MUTED),location=Theme.heading("",13);
    private final JButton previous=control("뒤로","back",()->travel(back,forward)),
            next=control("앞으로","chevron",()->travel(forward,back));
    private static List<Path> clipboard=new ArrayList<>();
    private static boolean cut;
    private int loadVersion;
    private volatile boolean disposed;
    private final AWTEventListener side=event-> {
        if(event instanceof MouseEvent) {
            MouseEvent e=(MouseEvent)event;
            if(e.getID()==MouseEvent.MOUSE_PRESSED&&e.getSource() instanceof Component&&SwingUtilities.isDescendingFrom((Component)e.getSource(),this)) {
                if(e.getButton()==4)travel(back,forward);
                if(e.getButton()==5)travel(forward,back);
            }
        }
    };
    FolderBrowserPanel(BuildingFolderController controller,FolderService service,Path building,Runnable updated,Runnable closeAction)throws java.io.IOException {
        super(new BorderLayout());
        this.service=service;
        this.controller=controller;
        this.building=building;
        this.updated=updated;
        current=controller.initial(building);
        operations=controller.operationsFor(building);
        setOpaque(false);
        JPanel panel=Theme.card(new BorderLayout(0,8));
        panel.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));
        JPanel header=Theme.transparent(new BorderLayout(6,0));
        JPanel actions=Theme.transparent(new FlowLayout(FlowLayout.RIGHT,1,0));
        actions.add(previous);
        actions.add(next);
        if (closeAction != null) actions.add(control("닫기","close",closeAction));
        location.putClientProperty("html.disable",true);
        header.add(location,BorderLayout.CENTER);
        header.add(actions,BorderLayout.EAST);
        panel.add(header,BorderLayout.NORTH);
        list.setFont(Theme.font(13,Font.PLAIN));
        list.setForeground(Theme.INK);
        list.setSelectionForeground(Theme.BLUE);
        list.setSelectionBackground(Theme.PALE_BLUE);
        list.setFixedCellHeight(36);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f) {
                super.getListCellRendererComponent(l,v,i,s,f);
                Path p=(Path)v;
                String label=p.getFileName().toString();
                putClientProperty("html.disable",true);
                setText(label);
                setIconTextGap(8);
                setIcon(new DocumentIcon(FolderService.extension(p).toUpperCase(),Files.isDirectory(p),22));
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
        panel.add(Theme.scroll(list),BorderLayout.CENTER);
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
        bind("alt LEFT",()->travel(back,forward));
        bind("alt RIGHT",()->travel(forward,back));
        add(panel,BorderLayout.CENTER);
        reload();
    }
    public boolean isReleased() { return disposed; }

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
    @Override public void addNotify() {
        super.addNotify();
        Toolkit.getDefaultToolkit().addAWTEventListener(side,AWTEvent.MOUSE_EVENT_MASK);
    }
    @Override public void removeNotify() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(side);
        super.removeNotify();
    }
    void focusFiles() { list.requestFocusInWindow(); }
    private JButton control(String label,String icon,Runnable task) {
        JButton button=Theme.quietButton("",icon,()->{ if(!working) task.run(); });
        button.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
        button.setToolTipText(label);
        button.getAccessibleContext().setAccessibleName(label);
        return button;
    }
    private void bind(String key,Runnable task) {
        list.getInputMap().put(KeyStroke.getKeyStroke(key),key);
        list.getActionMap().put(key,new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if(!working)task.run();
            }
        });
    }
    void release() {
        disposed=true;
        loadVersion++;
    }

    private void error(Exception e) {
        JOptionPane.showMessageDialog(this,e.getMessage(),"처리 실패",JOptionPane.ERROR_MESSAGE);
    }
    private interface Task {
        void execute()throws Exception;
    }
    private void run(Task task) {
        if(controller.isWorking())return;
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
        location.setText(current.equals(building) ? controller.displayName(building) : current.getFileName().toString());
        location.setToolTipText(current.toString());
        new SwingWorker<List<Path>,Void>() {
            protected List<Path> doInBackground()throws Exception {
                return !disposed&&Files.exists(folder)?service.list(folder):java.util.List.of();
            }
            protected void done() {
                if(version!=loadVersion)return;
                try {
                    List<Path> paths=get();
                    paths = new ArrayList<>(paths);
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
        if (controller.isWorking()) return;
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
    JPopupMenu contextMenu() {
        JPopupMenu m=new JPopupMenu();
        if(!list.isSelectionEmpty()) {
            item(m,"열기",this::open);
            item(m,"복사 · Ctrl+C",()->copy(false));
            item(m,"잘라내기 · Ctrl+X",()->copy(true));
            item(m,"이름 변경 · F2",this::rename);
            m.add(Theme.deleteMenuItem("삭제 · Delete", () -> { if (!working) delete(); }));
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
