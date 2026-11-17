package campus.materials;

import campus.calendar.Semester;
import campus.folder.FolderModule;
import campus.map.CampusMap;
import campus.model.PlannerModel;
import campus.ui.Theme;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import javax.swing.*;

/** 학기별 과목 목록. 실제 파일 관리와 저장 위치는 기존 자료함 모듈에 맡긴다. */
public final class CourseMaterialsPanel extends JPanel {
    private final PlannerModel model;
    private final CampusMap map;
    private final FolderModule folders;
    private final JPanel courses=Theme.transparent(new GridLayout(0,2,12,12));
    private final JLabel title=Theme.heading("과목 자료",17);
    public CourseMaterialsPanel(PlannerModel model,CampusMap map,FolderModule folders) {
        super(new BorderLayout(0,12));setOpaque(false);
        this.model=model;this.map=map;this.folders=folders;
        add(title,BorderLayout.NORTH);
        JPanel top=Theme.transparent(new BorderLayout());top.add(courses,BorderLayout.NORTH);
        add(Theme.scroll(top),BorderLayout.CENTER);
    }
    public void refresh(Semester semester) {
        title.setText("과목 자료 · "+semester); courses.removeAll();
        var seen=new HashSet<String>();
        for(var event:model.events(semester)) {
            if(!event.recurring()||event.category()!=PlannerModel.Category.CLASS
                    ||!seen.add(event.buildingId()+"\n"+event.title()))continue;
            var building=map.building(event.buildingId());
            JButton card=Theme.button("<html>"+Theme.html(event.title())+"<br><small>"+
                    Theme.html(building==null?"장소 없음":building.name())+"</small></html>",false,()->{});
            card.setPreferredSize(new Dimension(180,85));
            card.setToolTipText("더블클릭하여 과목 자료 열기");
            card.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){
                if(SwingUtilities.isLeftMouseButton(e)&&e.getClickCount()==2)folders.openCourse(card,event);
            }});
            card.getInputMap().put(KeyStroke.getKeyStroke("ENTER"),"openCourse");
            card.getActionMap().put("openCourse",new AbstractAction(){public void actionPerformed(ActionEvent e){folders.openCourse(card,event);}});
            courses.add(card);
        }
        if(courses.getComponentCount()==0)courses.add(Theme.label("이 학기에 등록된 수업이 없습니다.",13,Theme.MUTED));
        revalidate();repaint();
    }
}
