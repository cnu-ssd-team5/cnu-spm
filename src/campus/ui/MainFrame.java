package campus.ui;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.map.RouteService;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Event;
import campus.model.PlannerModel.Occurrence;
import campus.model.PlannerStore;
import campus.service.DayPlanService;
import campus.service.DayPlanService.Leg;
import campus.service.ScheduleSlot;
import campus.calendar.*;
import campus.places.PlaceService;
import campus.sharing.ScheduleSharing;
import campus.timetable.TimetablePanel;
import campus.materials.CourseMaterialsPanel;

/** 메인 창. 화면 배치와 선택한 날짜·건물을 관리하고, 변경이 생기면 저장합니다. */
public final class MainFrame extends JFrame {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private final CampusMap data;
    private final RouteService routes;
    private final DayPlanService plans;
    private final PlannerModel model;
    private final Path statePath;
    private final MapPanel map;
    private final PlaceService placeService;
    private final CardLayout viewLayout=new CardLayout();
    private final JPanel views=new JPanel(viewLayout);
    private final TimetablePanel timetable;
    private final CalendarPanel calendar;
    private final CourseMaterialsPanel materials;
    private String activeView="지도";
    private final java.util.Map<String,JButton> viewButtons=new java.util.LinkedHashMap<>();
    private final campus.folder.FolderModule folders;
    private final WeekBar week;
    private final PlacesPanel places;
    private final SchedulePanel daily;
    private LocalDate selectedDate = PlannerTime.today();
    private Building selectedBuilding;
    private Building routeFrom,routeTo;
    private final JLabel mapTitle = Theme.heading("하루의 지도", 17);
    private final JLabel mapSubtitle = Theme.label(" ", 12, Theme.MUTED);
    private final JLabel mapLegend = Theme.label("● 일정 순서   ━ 도보 경로   ● 즐겨찾기", 11, Theme.MUTED);
    private RouteDialog.Result searchedRoute;
    private final JLabel saveStatus = Theme.label("기기에 자동 저장", 11, Theme.MUTED);
    private final javax.swing.Timer noteTimer;
    private boolean unsaved;
    private boolean workspaceExpanded;
    private final JButton workspaceButton=Theme.button("중앙 확대",false,this::toggleWorkspace);

    public MainFrame(CampusMap data, RouteService routes, PlannerModel model, Path statePath) throws IOException {
        super("CNU 캠퍼스 플래너 · 전남대학교 광주캠퍼스");
        this.data = data;
        this.routes = routes;
        this.plans = new DayPlanService(data, routes);
        this.model = model;
        this.statePath = statePath;
        placeService=new PlaceService(data,model,routes);
        placeService.sync();
        map = new MapPanel(data, model, this::selectBuilding);
        folders = campus.folder.FolderModule.openProject(Path.of(System.getProperty("campus.home", ".")), data, model);
        try { folders.syncSchedules(); } catch(IOException ex) { folders.close();throw ex; }
        folders.setBeforeOpen(building->{
            if(building.name().replace(" ","").startsWith("이름없는")) {
                renamePlace(building);
                Building renamed=data.building(building.id());
                return renamed==null||renamed.name().replace(" ","").startsWith("이름없는")?null:renamed;
            }
            return building;
        });
        map.connectFolders(folders);
        map.setOccurrenceOpener(occurrence->folders.openOccurrence(map,occurrence));
        map.setRouteMenu(this::routeMenu);
        map.setPinCreator(this::addPin);
        timetable=new TimetablePanel(data,model,this::saveChanged);
        calendar=new CalendarPanel(model,this::changeDate,date->editDeadline(null,date),
                item->editDeadline(item,item.date()));
        materials=new CourseMaterialsPanel(model,data,folders);
        week = new WeekBar(this, model);
        places = new PlacesPanel(this, data, model);
        daily = new SchedulePanel(this, data, model);
        noteTimer = new javax.swing.Timer(600, e -> persist());
        noteTimer.setRepeats(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowActivated(WindowEvent e) {if(!folders.isBusy())folders.refreshRecent();}
            @Override public void windowClosing(WindowEvent e) {
                if(folders.isBusy()) { JOptionPane.showMessageDialog(MainFrame.this,"파일 작업이 끝난 뒤 닫아 주세요.");return; }
                noteTimer.stop();
                if (unsaved && !persist()) return;
                try { folders.close(); }
                catch(IOException ex) { JOptionPane.showMessageDialog(MainFrame.this,ex.getMessage());return; }
                dispose();
            }
        });
        JPanel page = new JPanel(new BorderLayout(0, 12));
        page.setBackground(Theme.BACKGROUND);
        page.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));
        page.add(header(), BorderLayout.NORTH);
        JPanel body = Theme.transparent(new BorderLayout(10, 0));
        places.panel.setPreferredSize(new Dimension(205, 0));
        body.add(places.panel, BorderLayout.WEST);

        JPanel center = Theme.transparent(new BorderLayout(0, 10));
        center.add(week.panel, BorderLayout.NORTH);
        JPanel workspace = Theme.transparent(new BorderLayout(10, 0));
        views.setOpaque(false);
        views.add(mapCard(),"지도");
        views.add(viewCard(timetable),"주간시간표");
        views.add(viewCard(calendar),"달력");
        views.add(viewCard(materials),"과목자료");
        workspace.add(views, BorderLayout.CENTER);
        daily.panel.setPreferredSize(new Dimension(285, 0));
        workspace.add(daily.panel, BorderLayout.EAST);
        center.add(workspace, BorderLayout.CENTER);
        body.add(center, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        JPanel footer = Theme.transparent(new BorderLayout());
        JPanel storage=Theme.transparent(new FlowLayout(FlowLayout.RIGHT,6,0));
        storage.add(Theme.button("일정 공유",false,()->share(false)));
        storage.add(Theme.button("일정 가져오기",false,()->share(true)));
        storage.add(saveStatus);
        footer.add(storage, BorderLayout.EAST);
        page.add(footer, BorderLayout.SOUTH);
        setContentPane(page);
        setSize(1420, 900);
        setMinimumSize(new Dimension(1130, 680));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(getWidth(), screen.width-60), Math.min(getHeight(), screen.height-70));
        setLocationRelativeTo(null);
        refresh();
    }

    LocalDate selectedDate() { return selectedDate; }
    Building selectedBuilding() { return selectedBuilding; }

    private JPanel header() {
        JPanel header = Theme.transparent(new BorderLayout(20, 0));
        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 9, 6));
        for(String view:List.of("지도","주간시간표","달력","과목자료")) {
            JButton button=Theme.button(view,false,()->showView(view));viewButtons.put(view,button);actions.add(button);
        }
        actions.add(workspaceButton);
        updateViewButtons();
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private void toggleWorkspace() {
        if(folders.isBusy())return;
        folders.closePopup();
        workspaceExpanded=!workspaceExpanded;
        places.panel.setVisible(!workspaceExpanded);
        daily.panel.setVisible(!workspaceExpanded);
        week.panel.setVisible(!workspaceExpanded);
        workspaceButton.setText(workspaceExpanded?"원래 크기":"중앙 확대");
        revalidate();repaint();
    }

    private JPanel viewCard(JComponent content) {
        JPanel card=Theme.card(new BorderLayout());card.add(content);return card;
    }
    private void showView(String name) {
        if(folders.isBusy())return;
        noteTimer.stop(); if(unsaved&&!persist())return;
        folders.closePopup();activeView=name;viewLayout.show(views,name);updateViewButtons();refresh();
    }
    private void updateViewButtons() {
        viewButtons.forEach((name,button)->{
            boolean selected=name.equals(activeView);
            button.putClientProperty("calendar.fill",selected?Theme.PALE_BLUE:null);
            button.setForeground(selected?Theme.BLUE:Theme.INK);button.repaint();
        });
    }
    private boolean saveChanged() {
        unsaved=true;boolean saved=persist();refresh();return saved;
    }
    void editDeadline(PlannerModel.Deadline item,LocalDate date) {
        var result=DeadlineDialog.open(this,date,item);if(result==null)return;
        if(result.deleted())model.removeDeadline(result.deadline().id());else model.putDeadline(result.deadline());
        saveChanged();
    }
    private void share(boolean importing) {
        if(folders.isBusy())return;
        noteTimer.stop();if(unsaved&&!persist())return;
        try {
            if(importing) {
                if(ScheduleSharing.importFile(this,model)) {unsaved=true;placeService.sync();saveChanged();}
            } else ScheduleSharing.exportFile(this,model);
        } catch(IOException ex) {JOptionPane.showMessageDialog(this,ex.getMessage(),"일정 공유",JOptionPane.ERROR_MESSAGE);}
    }
    private void addPin(CampusMap.GeoPoint point) {
        if(folders.isBusy())return;
        String name=JOptionPane.showInputDialog(this,"핀 이름");if(name==null||name.isBlank())return;
        try {String id=placeService.addPin(name,point);selectBuilding(data.building(id));saveChanged();}
        catch(IOException|IllegalArgumentException ex){JOptionPane.showMessageDialog(this,ex.getMessage());}
    }
    void renamePlace(Building building) {
        if(building==null)return;
        if(folders.isBusy())return;
        String name=JOptionPane.showInputDialog(this,"장소 이름",building.name());if(name==null||name.isBlank())return;
        try {placeService.rename(building.id(),name);selectBuilding(data.building(building.id()));saveChanged();}
        catch(IOException|IllegalArgumentException ex){JOptionPane.showMessageDialog(this,ex.getMessage());}
    }
    private void deletePin(Building building) {
        if(folders.isBusy())return;
        if(JOptionPane.showConfirmDialog(this,"핀과 연결된 일정을 삭제할까요? 자료 파일은 보관됩니다.","핀 삭제",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try {
            placeService.removePin(building.id());selectedBuilding=null;map.select(null);
            routeFrom=routeTo=null;showScheduleMap();saveChanged();
        } catch(IOException ex){JOptionPane.showMessageDialog(this,ex.getMessage());}
    }

    private JPanel mapCard() {
        JPanel panel = Theme.card(new BorderLayout(0, 10));
        JPanel top = Theme.transparent(new BorderLayout());
        JPanel titles = Theme.transparent(new GridLayout(2,1,0,5));
        titles.add(mapTitle);
        titles.add(mapSubtitle);
        top.add(titles,BorderLayout.WEST);
        JPanel controls = Theme.transparent(new FlowLayout(FlowLayout.RIGHT,5,0));
        JButton routeSearch=Theme.button("최단거리 찾기",false,this::findRoute);
        JButton fileSearch=folders.searchButton();
        Dimension searchSize=new Dimension(Math.max(routeSearch.getPreferredSize().width,fileSearch.getPreferredSize().width),
                Math.max(routeSearch.getPreferredSize().height,fileSearch.getPreferredSize().height));
        routeSearch.setPreferredSize(searchSize);fileSearch.setPreferredSize(searchSize);
        controls.add(routeSearch);controls.add(fileSearch);
        controls.add(Theme.button("-",false,() -> map.zoom(1/1.3)));
        controls.add(Theme.button("+",false,() -> map.zoom(1.3)));
        top.add(controls,BorderLayout.EAST);
        JPanel mapHeader = Theme.transparent(new BorderLayout(0, 11));
        mapHeader.add(top, BorderLayout.NORTH);
        panel.add(mapHeader,BorderLayout.NORTH);
        panel.add(map,BorderLayout.CENTER);
        JPanel legend = Theme.transparent(new BorderLayout());
        legend.add(mapLegend,BorderLayout.WEST);
        legend.add(Theme.button("일정 경로",false,this::showScheduleMap),BorderLayout.EAST);
        panel.add(legend,BorderLayout.SOUTH);
        return panel;
    }

    void changeDate(LocalDate date) {
        noteTimer.stop();
        if (unsaved && !persist()) return;
        selectedDate = date;
        searchedRoute = null;
        map.clearSearchRoute();
        refresh();
    }

    private void refresh() {
        week.refresh(selectedDate);
        Semester semester=Semester.forDate(selectedDate);
        timetable.refresh(selectedDate);calendar.refresh(selectedDate,semester);materials.refresh(semester);
        List<Occurrence> occurrences = model.occurrences(selectedDate);
        List<Leg> legs = plans.legs(occurrences);
        map.setOccurrences(occurrences, legs);
        daily.refresh(selectedDate, occurrences, legs);
        updateMapSummary();
        places.refresh();
        revalidate();
        repaint();
    }

    private void updateMapSummary() {
        boolean searching = searchedRoute != null;
        mapTitle.setText(searching ? "최단거리 검색" : "하루의 지도");
        mapLegend.setText(searching ? "출발 → 도착   ━ 최단 도보 경로" : "● 일정 순서   ━ 도보 경로   ● 즐겨찾기");
        mapSubtitle.setForeground(Theme.MUTED);
        if (searching) {
            RouteService.Route route = searchedRoute.route();
            mapLegend.setText(searchedRoute.from().name()+" → "+searchedRoute.to().name());
            mapLegend.setToolTipText(route.message());
            if (route.reachable()) {
                int minutes = (int) Math.ceil(route.meters() / (RouteService.WALKING_METERS_PER_SECOND * 60));
                mapSubtitle.setText(String.format(Locale.ROOT, "최단거리 %,.0f m · 도보 약 %d분", route.meters(), minutes));
            } else {
                mapSubtitle.setText("연결된 보행 경로를 찾지 못했습니다");
                mapSubtitle.setForeground(Theme.WARNING);
            }
            return;
        }
        long count = model.occurrences(selectedDate).stream().filter(o -> !o.canceled()).count();
        double meters = map.legs().stream().filter(l -> l.route().reachable()).mapToDouble(l -> l.route().meters()).sum();
        long unavailable = map.legs().stream().filter(l -> !l.route().reachable()).count();
        int minutes = map.legs().stream().filter(l -> l.route().reachable()).mapToInt(Leg::minutes).sum();
        mapSubtitle.setText(count == 0 ? "날짜를 고르고 일정을 추가해 보세요" :
                count + "개 일정 · " + String.format(Locale.ROOT, "%,.0f m", meters) + " · 약 " + minutes + "분"
                        + (unavailable > 0 ? " · 일부 경로 없음" : ""));
    }

    JPopupMenu routeMenu(Building building) {
        JPopupMenu menu=new JPopupMenu();
        JMenuItem from=new JMenuItem("출발지로 설정");
        from.addActionListener(e->{routeFrom=building;routeTo=null;showScheduleMap();folders.closePopup();selectBuilding(building);mapSubtitle.setText("출발: "+building.name()+" · 도착 건물을 우클릭하세요");});
        menu.add(from);
        if(routeFrom!=null&&!routeFrom.id().equals(building.id())) {
            JMenuItem to=new JMenuItem("도착지로 설정");
            to.addActionListener(e->{routeTo=building;folders.closePopup();showRoute(new RouteDialog.Result(routeFrom,routeTo,routes.route(routeFrom,routeTo)));});menu.add(to);
        }
        if(routeFrom!=null) {
            JMenuItem reset=new JMenuItem("출발·도착 선택 해제");reset.addActionListener(e->{routeFrom=null;routeTo=null;showScheduleMap();});menu.add(reset);
        }
        menu.addSeparator();
        JMenuItem create=new JMenuItem("폴더 생성");
        create.setEnabled(!folders.hasFolder(building)&&!folders.isBusy());
        create.addActionListener(e->folders.createFolder(this,building));
        menu.add(create);
        JMenuItem add=new JMenuItem("일정 추가");add.addActionListener(e->{selectBuilding(building);edit(null,false);});menu.add(add);
        JMenuItem rename=new JMenuItem("이름 변경");rename.addActionListener(e->renamePlace(building));menu.add(rename);
        if(building.id().startsWith("pin:")) {JMenuItem delete=new JMenuItem("핀 삭제");delete.addActionListener(e->deletePin(building));menu.add(delete);}
        return menu;
    }

    private void findRoute() {
        RouteDialog.Result result = RouteDialog.open(this, data, routes, model, routeFrom==null?selectedBuilding:routeFrom, routeTo);
        if (result == null) return;
        showRoute(result);
    }

    private void showRoute(RouteDialog.Result result) {
        routeFrom=result.from();routeTo=result.to();
        searchedRoute = result;
        map.setSearchRoute(result.from(), result.to(), result.route());
        updateMapSummary();
        revalidate();
        repaint();
    }

    private void showScheduleMap() {
        searchedRoute = null;
        map.clearSearchRoute();
        updateMapSummary();
        revalidate();
        repaint();
    }

    void edit(Event event,boolean repeat) {
        edit(event, repeat, null);
    }

    void edit(Event event, boolean repeat, ScheduleSlot slot) {
        noteTimer.stop();
        if(unsaved && !persist())return;
        if(folders.isBusy())return;
        if(event==null) {
            timetable.addEvent(selectedDate,selectedBuilding);
            return;
        }
        if(event.recurring()) {
            timetable.refresh(selectedDate);
            timetable.editEvent(event);return;
        }
        EventDialog.Result result=EventDialog.open(this,data,model,selectedDate,selectedBuilding,event,repeat,slot);
        if(result==null)return;
        if(result.deleted())model.removeEvent(result.event().id());
        else {
            Semester term=event==null?Semester.forDate(selectedDate):model.semesterOf(event.id());
            if(result.event().recurring())model.putEvent(result.event(),term==null?Semester.forDate(selectedDate):term);
            else model.putEvent(result.event());
        }
        unsaved=true;
        persist();
        refresh();
    }

    void toggleCancellation(Event event) {
        model.toggleCancellation(event.id(),selectedDate);
        unsaved=true;
        persist();
        refresh();
    }

    void selectBuilding(Building building) {
        selectedBuilding=building;
        map.select(building.id());
        places.showSelected(building);
    }

    void toggleFavorite() {
        if(selectedBuilding==null)return;
        model.toggleFavorite(selectedBuilding.id());
        unsaved=true;
        persist();
        places.refresh();
        map.repaint();
    }

    void noteChanged() {
        unsaved = true;
        saveStatus.setText("메모 저장 중…");
        noteTimer.restart();
    }

    private boolean persist() {
        noteTimer.stop();
        try {
            folders.syncSchedules();
            PlannerStore.save(statePath,model);
            unsaved=false;
            saveStatus.setForeground(Theme.MUTED);
            saveStatus.setText("저장됨 · "+LocalTime.now().format(CLOCK));
            return true;
        } catch(IOException ex) {
            unsaved=true;
            saveStatus.setForeground(Theme.WARNING);
            saveStatus.setText("저장 실패 · 변경 내용은 현재 창에 유지됩니다");
            JOptionPane.showMessageDialog(this,"일정을 저장하지 못했습니다.\n"+statePath+"\n"+ex.getMessage()+"\n경로를 확인한 뒤 다시 변경하거나 창을 닫아 저장을 재시도하세요.","저장 실패",JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
}
