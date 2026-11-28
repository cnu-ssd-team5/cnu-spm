package campus.ui;

import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Event;
import campus.model.PlannerModel.Deadline;
import java.util.LinkedHashMap;
import campus.model.PlannerModel.Occurrence;
import campus.service.DayPlanService.Leg;
import campus.timetable.TimePicker;

/** 오른쪽의 하루 일정 카드와 일정 사이 이동 시간. */
final class SchedulePanel {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN);
    final JPanel panel = Theme.transparent(new BorderLayout(0,16));
    private final MainFrame frame;
    private CampusMap data;

    private final JButton addEvent;
    private PlannerModel model;
    private final JLabel dayTitle = Theme.heading("오늘 일정", 17);
    private LocalDate date = campus.calendar.PlannerTime.today();
    private String expandedEvent;
    private String expandedCourse;
    private FolderBrowserPanel courseFiles;
    private List<Leg> displayedLegs = List.of();
    private RouteDialog.Result searchedRoute;
    private final JPanel dateBar = Theme.transparent(new BorderLayout(8, 0));
    private final JPanel dateSlot = Theme.transparent(new BorderLayout());
    private final JPanel collapseSlot = Theme.transparent(new BorderLayout());
    private final JPanel tasks = Theme.transparent(new GridLayout(0,1,0,8));
    private final JPanel movement = Theme.transparent(new GridLayout(0,1,0,8));
    private final JPanel courses = Theme.transparent(new GridBagLayout());
    private final JButton addTask;
    private final JButton allMaterials;
    private final JPanel schedule = new JPanel(new GridBagLayout()) {
        @Override public Dimension getPreferredSize() {
            return new Dimension(0, super.getPreferredSize().height);
        }
    };

    SchedulePanel(MainFrame frame, CampusMap data, PlannerModel model) {
        this.frame=frame;this.data=data;this.model=model;
        panel.setOpaque(true);panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(0,12,0,12));
        addEvent=Theme.quietButton("","plus",()->{if(frame!=null)frame.edit(null);});
        addEvent.setName("schedule-add");addEvent.getAccessibleContext().setAccessibleName("일정 추가");
        addEvent.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));addEvent.setToolTipText("일정 추가");
        addTask=Theme.quietButton("+ 추가","planner",()->{if(frame!=null)frame.editDeadline(null,date,true);});
        addTask.setIcon(null);addTask.setForeground(Theme.BLUE);addTask.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
        JPanel stack=new JPanel(new GridBagLayout()){
            @Override public Dimension getPreferredSize(){return new Dimension(0,super.getPreferredSize().height);}
        };
        stack.setOpaque(false);GridBagConstraints c=new GridBagConstraints();
        c.gridx=0;c.gridy=0;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.anchor=GridBagConstraints.NORTH;
        schedule.setOpaque(false);
        JPanel dayCard = section("calendar", dayTitle, addEvent, schedule);
        Component dayHeading = ((BorderLayout) dayCard.getLayout()).getLayoutComponent(BorderLayout.NORTH);
        dateBar.add(dateSlot, BorderLayout.CENTER);
        dateBar.add(collapseSlot, BorderLayout.EAST);
        dateBar.setVisible(false);
        JPanel dayHeader = Theme.transparent(new BorderLayout(0, 12));
        dayHeader.add(dateBar, BorderLayout.NORTH);
        dayHeader.add(dayHeading, BorderLayout.CENTER);
        dayCard.add(dayHeader, BorderLayout.NORTH);
        stack.add(dayCard,c);
        c.gridy++;stack.add(section("planner",Theme.heading("과제·할일",17),addTask,tasks),c);
        c.gridy++;stack.add(section("pin",Theme.heading("이동 정보",17),null,movement),c);
        allMaterials=Theme.quietButton("전체보기","chevron",()->{if(frame!=null)frame.openMaterials();});
        allMaterials.setFont(Theme.font(11,Font.PLAIN));allMaterials.setBorder(BorderFactory.createEmptyBorder(4,0,4,0));
        allMaterials.setHorizontalTextPosition(SwingConstants.LEFT);
        c.gridy++;stack.add(section("folder",Theme.heading("수강 과목 / 자료",16),allMaterials,courses),c);
        c.gridy++;c.weighty=1;c.fill=GridBagConstraints.BOTH;stack.add(Theme.transparent(new BorderLayout()),c);
        panel.add(Theme.scroll(stack),BorderLayout.CENTER);
    }

    void installDateNavigation(JComponent navigation) {
        if (navigation.getParent() != dateSlot) dateSlot.add(navigation, BorderLayout.CENTER);
        dateBar.setVisible(true);
    }

    void installCollapseControl(JButton button) {
        if (button.getParent() != collapseSlot) collapseSlot.add(button, BorderLayout.CENTER);
    }

    private JPanel section(String icon,JLabel title,JComponent action,JComponent content){
        JPanel card=Theme.transparent(new BorderLayout(0,8));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(icon.equals("calendar")?0:1,0,0,0,Theme.LINE),
                BorderFactory.createEmptyBorder(12,0,12,0)));
        JPanel head=Theme.transparent(new BorderLayout(8,0));
        JPanel caption=Theme.transparent(new FlowLayout(FlowLayout.LEFT,8,0));
        JLabel symbol=Theme.label("",14,Theme.INK);symbol.setIcon(UiIcons.of(icon,20));caption.add(symbol);caption.add(title);head.add(caption,BorderLayout.WEST);
        if(action!=null)head.add(action,BorderLayout.EAST);card.add(head,BorderLayout.NORTH);card.add(content,BorderLayout.CENTER);return card;
    }

    void setScheduleContext(CampusMap data, PlannerModel model) {
        this.data = data;
        this.model = model;

        allMaterials.setEnabled(frame != null && frame.hasCourseMaterials());expandedEvent=null;
        collapseCourse();
    }

    void refresh(LocalDate date, List<Occurrence> occurrences, List<Leg> legs) {
        if (!this.date.equals(date)) collapseCourse();
        this.date=date;
        dayTitle.setText(date.equals(campus.calendar.PlannerTime.today())?"오늘 일정":"선택일 일정");
        dayTitle.setToolTipText(date.format(DAY));
        refreshSchedule(occurrences, legs);
    }

    private void refreshSchedule(List<Occurrence> occurrences,List<Leg> legs) {
        displayedLegs=List.copyOf(legs);schedule.removeAll();
        GridBagConstraints c=new GridBagConstraints();c.gridx=0;c.gridy=0;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.insets=new Insets(0,0,8,0);
        if(occurrences.isEmpty())schedule.add(Theme.label("등록된 일정이 없습니다.",12,Theme.MUTED),c);
        int index=0;
        for(Occurrence item:occurrences){
            int displayIndex=item.canceled()?0:++index;
            JPanel row=compactEvent(item);
            schedule.add(row,c);c.gridy++;
            if(item.event().id().equals(expandedEvent)){schedule.add(eventCard(item,displayIndex),c);c.gridy++;}
        }
        refreshTasks();refreshMovement();refreshCourses(occurrences);
    }

    private JPanel compactEvent(Occurrence occurrence){
        Event event=occurrence.event();JPanel row=Theme.transparent(new BorderLayout(8,0));
        JLabel time=Theme.label(TimePicker.display(event.start())+" – "+TimePicker.display(event.end()),11,Theme.INK);
        time.setToolTipText(time.getText());
        row.add(time,BorderLayout.WEST);
        JPanel tile=Theme.rounded(new BorderLayout(0,4),8);tile.setBackground(occurrence.canceled()?new Color(245,246,249):Theme.PALE_BLUE);
        tile.setBorder(BorderFactory.createMatteBorder(0,3,0,0,occurrence.canceled()?Theme.MUTED:Theme.BLUE));
        JPanel text=Theme.transparent(new GridLayout(0,1,0,4));text.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        JLabel title=Theme.heading(event.title(),13);title.putClientProperty("html.disable",true);title.setToolTipText(event.title());text.add(title);
        Building building=data.building(event.buildingId());
        String place=!event.customPlace().isBlank()?event.customPlace():building==null?"장소 확인 필요":building.name();
        JLabel location=Theme.label(place,10,Theme.MUTED);location.putClientProperty("html.disable",true);location.setToolTipText(place);
        if(!event.customPlace().isBlank())text.add(location);
        if(!event.customCategory().isBlank()){
            JLabel category=Theme.label(event.categoryLabel()+(event.recurring()?"":" · 이 날짜만"),10,Theme.MUTED);
            category.putClientProperty("html.disable",true);text.add(category);
        }
        title.setToolTipText(event.title()+" · "+place);
        tile.add(text);row.add(tile,BorderLayout.CENTER);row.setName("schedule-summary:"+event.id());
        Runnable expand=()->{expandedEvent=event.id().equals(expandedEvent)?null:event.id();refreshSchedule(model.occurrences(date),displayedLegs);panel.revalidate();panel.repaint();};
        MouseAdapter click=new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e))expand.run();}};
        for(Component component:List.of(row,tile,text,title,location,time)){component.addMouseListener(click);component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));}
        row.setFocusable(true);row.getAccessibleContext().setAccessibleName(event.title()+" · Enter로 상세 펼치기");
        row.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"),"expand");
        row.getActionMap().put("expand",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){expand.run();}});
        return row;
    }

    private List<Deadline> visibleTasks(LocalDate day){
        LinkedHashMap<String,Deadline> all=new LinkedHashMap<>();
        model.plannerTasks(day).forEach(task->all.put(task.id(),task));
        model.deadlines().stream().filter(task->!day.isBefore(task.plannerDate())&&!day.isAfter(task.date())).forEach(task->all.put(task.id(),task));
        return List.copyOf(all.values());
    }
    private void refreshTasks(){
        tasks.removeAll();List<Deadline> items=visibleTasks(date);
        if(items.isEmpty())tasks.add(Theme.label("예정된 할 일이 없습니다.",12,Theme.MUTED));
        for(Deadline item:items){
            JPanel row=Theme.transparent(new BorderLayout(4,0));
            JCheckBox done=Theme.checkBox(item.title(),item.completed());done.setName("summary-task:"+item.id());
            done.setOpaque(false);done.setFont(Theme.font(12,Font.PLAIN));done.setForeground(item.completed()?Theme.MUTED:Theme.INK);
            done.setToolTipText(item.title());done.setEnabled(frame!=null);
            done.addActionListener(e->frame.toggleDailyTask(item));row.add(done,BorderLayout.CENTER);
            JLabel due=Theme.label(item.deadlineLinked()?"D"+(java.time.temporal.ChronoUnit.DAYS.between(date,item.date())==0?"-day":"-"+java.time.temporal.ChronoUnit.DAYS.between(date,item.date())):"",10,Theme.TODAY_TEXT);
            row.add(due,BorderLayout.EAST);tasks.add(row);
        }
    }
    void showSearchedRoute(RouteDialog.Result result){searchedRoute=result;refreshMovement();panel.revalidate();panel.repaint();}
    private void refreshMovement(){
        movement.removeAll();
        if(searchedRoute!=null){
            movement.add(Theme.label(searchedRoute.from().name()+" → "+searchedRoute.to().name(),12,Theme.INK));
            var route=searchedRoute.route();
            movement.add(Theme.label(route.reachable()?String.format(Locale.ROOT,"도보 약 %d분 · %,.0f m",(int)Math.ceil(route.meters()/(campus.map.RouteService.WALKING_METERS_PER_SECOND*60)),route.meters()):"연결된 보행 경로가 없습니다",12,route.reachable()?Theme.BLUE:Theme.WARNING));
        }else{
            for(Leg leg:displayedLegs)if(showsWalkingLeg(leg))movement.add(legCard(leg));
            if(movement.getComponentCount()==0)movement.add(Theme.label("일정 사이의 도보 경로를 안내합니다.",11,Theme.MUTED));
        }
    }
    private void refreshCourses(List<Occurrence> occurrences) {
        if (courseFiles != null && courseFiles.isReleased()) {
            courseFiles = null;
            expandedCourse = null;
        }
        var coursesForDay = new LinkedHashMap<String, Event>();
        for (Occurrence occurrence : occurrences) {
            Event event = occurrence.event();
            if (event.category() == PlannerModel.Category.CLASS) coursesForDay.putIfAbsent(event.recurringGroup(), event);
        }
        if (expandedCourse != null && !coursesForDay.containsKey(expandedCourse)) collapseCourse();

        courses.removeAll();
        GridBagConstraints row = new GridBagConstraints();
        row.gridx = 0;
        row.gridy = 0;
        row.weightx = 1;
        row.fill = GridBagConstraints.HORIZONTAL;
        row.insets = new Insets(0, 0, 4, 0);
        for (var entry : coursesForDay.entrySet()) {
            String id = entry.getKey();
            Event event = entry.getValue();
            boolean expanded = id.equals(expandedCourse);
            JButton course = Theme.actionRow(event.title(), "folder", () -> toggleCourse(event));
            course.setEnabled(frame != null && frame.hasCourseMaterials() && data.building(event.buildingId()) != null);
            course.putClientProperty("html.disable", true);
            course.setToolTipText(expanded ? "자료함 접기" : "자료함 펼치기");
            course.getAccessibleContext().setAccessibleDescription(course.getToolTipText());
            course.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            courses.add(course, row);
            row.gridy++;
            if (expanded && courseFiles != null) {
                courses.add(courseFiles, row);
                row.gridy++;
            }
        }
        if (coursesForDay.isEmpty()) courses.add(Theme.label("이날 등록된 수업이 없습니다.", 12, Theme.MUTED), row);
    }

    private void toggleCourse(Event event) {
        boolean wasExpanded = event.recurringGroup().equals(expandedCourse);
        if (!collapseCourse()) return;
        if (!wasExpanded) {
            courseFiles = frame.openCourseInline(courses, event);
            if (courseFiles != null) expandedCourse = event.recurringGroup();
        }
        refreshCourses(model.occurrences(date));
        courses.revalidate();
        courses.repaint();
        if (courseFiles != null) {
            FolderBrowserPanel opened = courseFiles;
            SwingUtilities.invokeLater(() -> {
                if (courseFiles == opened && opened.isShowing()) {
                    JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, opened);
                    if (viewport != null && viewport.getView() instanceof JComponent view) {
                        view.scrollRectToVisible(SwingUtilities.convertRectangle(opened.getParent(), opened.getBounds(), view));
                    }
                }
            });
        }
    }

    private boolean collapseCourse() {
        if (courseFiles != null && !frame.closeCourseInline()) return false;
        expandedCourse = null;
        courseFiles = null;
        return true;
    }

    private JPanel eventCard(Occurrence occurrence,int index) {
        Event event=occurrence.event();
        boolean canceled=occurrence.canceled();
        JPanel card=Theme.card(new BorderLayout(0,8));
        card.setBackground(canceled ? new Color(247,248,250) : Color.WHITE);
        card.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel details=Theme.transparent(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();
        c.gridx=0;
        c.weightx=1;
        c.fill=GridBagConstraints.HORIZONTAL;
        c.insets=new Insets(0,0,4,0);
        c.gridy=0;
        JLabel time=Theme.heading((canceled ? "" : index+"  ")+TimePicker.display(event.start())+" – "+TimePicker.display(event.end()),13);
        time.setForeground(canceled ? Theme.MUTED : Theme.BLUE);
        details.add(time,c);
        c.gridy++;
        JLabel title=Theme.heading(event.title(),15);
        title.setToolTipText(event.title());
        if(canceled) title.setForeground(Theme.MUTED);
        details.add(title,c);
        c.gridy++;
        Building place=data.building(event.buildingId());
        String placeName = !event.customPlace().isBlank() ? event.customPlace()
                : place == null ? "OSM에서 장소를 찾을 수 없습니다" : place.name();
        JLabel location=Theme.label("",12,Theme.MUTED);
        location.putClientProperty("html.disable", true);
        location.setText(placeName);
        location.setToolTipText("<html>" + Theme.html(placeName) + "</html>");
        if (place != null) {
            location.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            location.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { frame.selectBuilding(place); }
            });
        }
        details.add(location,c);
        c.gridy++;
        String state=canceled ? "휴강/제외 · 이 날짜에만 적용"
                : event.categoryLabel() + (event.recurring() ? "" : " · 이 날짜만");
        JLabel stateLabel = Theme.label("", 11, canceled ? Theme.WARNING : Theme.MUTED);
        stateLabel.putClientProperty("html.disable", true);
        stateLabel.setText(state);
        details.add(stateLabel,c);
        if(!canceled && model.occurrences(occurrence.date()).stream().anyMatch(other ->
                !other.canceled() && !other.event().id().equals(event.id())
                        && other.event().start().isBefore(event.end())
                        && event.start().isBefore(other.event().end()))) {
            c.gridy++;
            details.add(Theme.label("다른 일정과 시간이 겹칩니다",11,Theme.WARNING),c);
        }
        if(!event.note().isBlank()) {
            c.gridy++;
            String memo=event.note().length()>130 ? event.note().substring(0,130)+"…" : event.note();
            JLabel note=Theme.label("<html><div style='width:185px'>"+Theme.html(memo).replace("\n","<br>")+"</div></html>",11,Theme.MUTED);
            note.setToolTipText(event.note());
            details.add(note,c);
        }
        card.add(details,BorderLayout.CENTER);

        JPanel actions=Theme.transparent(new FlowLayout(FlowLayout.RIGHT,8,0));
        actions.add(Theme.button("편집",false,() -> frame.edit(event)));
        String cancelLabel=canceled ? "복원" : event.category()==PlannerModel.Category.CLASS ? "휴강" : "오늘 제외";
        actions.add(Theme.button(cancelLabel,canceled,() -> frame.toggleCancellation(event)));
        card.add(actions,BorderLayout.SOUTH);
        return card;
    }

    static boolean showsWalkingLeg(Leg leg) {
        return leg.from().event().customPlace().isBlank() && leg.to().event().customPlace().isBlank();
    }

    private JLabel legCard(Leg leg) {
        boolean reachable = leg.route().reachable();
        String travel = reachable ? String.format(Locale.ROOT, "↓  도보 약 %d분 · %,.0f m", leg.minutes(), leg.route().meters())
                : "도보 경로를 찾지 못했습니다";
        JLabel label = Theme.label(travel, 11, reachable ? Theme.MUTED : Theme.WARNING);
        label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));
        return label;
    }
}
