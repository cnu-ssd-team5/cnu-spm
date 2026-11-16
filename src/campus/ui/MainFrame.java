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

/** 메인 창. 화면 배치와 선택한 날짜·건물을 관리하고, 변경이 생기면 저장합니다. */
public final class MainFrame extends JFrame {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private final CampusMap data;
    private final RouteService routes;
    private final DayPlanService plans;
    private final PlannerModel model;
    private final Path statePath;
    private final MapPanel map;
    private final WeekBar week;
    private final PlacesPanel places;
    private final SchedulePanel daily;
    private LocalDate selectedDate = LocalDate.now();
    private Building selectedBuilding;
    private final JLabel mapTitle = Theme.heading("하루의 지도", 17);
    private final JLabel mapSubtitle = Theme.label(" ", 12, Theme.MUTED);
    private final JPanel searchRouteInfo = Theme.transparent(new BorderLayout(10, 5));
    private final JLabel searchFrom = Theme.label(" ", 11, Theme.INK);
    private final JLabel searchTo = Theme.label(" ", 11, Theme.INK);
    private final JLabel searchRouteNote = Theme.label(" ", 10, Theme.MUTED);
    private final JLabel mapLegend = Theme.label("● 일정 순서   ━ 도보 경로   ● 즐겨찾기", 11, Theme.MUTED);
    private RouteDialog.Result searchedRoute;
    private final JLabel saveStatus = Theme.label("기기에 자동 저장", 11, Theme.MUTED);
    private final javax.swing.Timer noteTimer;
    private boolean unsaved;

    public MainFrame(CampusMap data, RouteService routes, PlannerModel model, Path statePath) {
        super("CNU 캠퍼스 플래너 · 전남대학교 광주캠퍼스");
        this.data = data;
        this.routes = routes;
        this.plans = new DayPlanService(data, routes);
        this.model = model;
        this.statePath = statePath;
        map = new MapPanel(data, model, this::selectBuilding);
        week = new WeekBar(this, model);
        places = new PlacesPanel(this, data, model);
        daily = new SchedulePanel(this, data, model);
        noteTimer = new javax.swing.Timer(600, e -> persist());
        noteTimer.setRepeats(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                noteTimer.stop();
                if (unsaved && !persist()) return;
                dispose();
            }
        });
        JPanel page = new JPanel(new BorderLayout(0, 17));
        page.setBackground(Theme.BACKGROUND);
        page.setBorder(BorderFactory.createEmptyBorder(22, 22, 14, 22));
        page.add(header(), BorderLayout.NORTH);
        JPanel body = Theme.transparent(new BorderLayout(16, 0));
        places.panel.setPreferredSize(new Dimension(235, 0));
        body.add(places.panel, BorderLayout.WEST);

        JPanel center = Theme.transparent(new BorderLayout(0, 15));
        center.add(week.panel, BorderLayout.NORTH);
        JPanel workspace = Theme.transparent(new BorderLayout(16, 0));
        workspace.add(mapCard(), BorderLayout.CENTER);
        daily.panel.setPreferredSize(new Dimension(335, 0));
        workspace.add(daily.panel, BorderLayout.EAST);
        center.add(workspace, BorderLayout.CENTER);
        body.add(center, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        JPanel footer = Theme.transparent(new BorderLayout());
        footer.add(Theme.label("전남대학교 광주캠퍼스  ·  OSM 지도  ·  보행 속도 1.3 m/s 기준", 11, Theme.MUTED), BorderLayout.WEST);
        footer.add(saveStatus, BorderLayout.EAST);
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
        JPanel brand = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel logo = Theme.heading("CNU", 25);
        logo.setForeground(Theme.BLUE);
        brand.add(logo);
        brand.add(Box.createHorizontalStrut(15));
        JPanel text = Theme.transparent(new GridLayout(2,1,0,4));
        text.add(Theme.heading("나의 캠퍼스, 나의 한 주", 21));
        text.add(Theme.label("수업부터 약속까지 · 하루의 일정을 지도에서 이어보세요", 12, Theme.MUTED));
        brand.add(text);
        header.add(brand, BorderLayout.WEST);
        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 9, 6));
        actions.add(Theme.button("최단거리 찾기", false, this::findRoute));
        actions.add(Theme.button("고정 시간표 추가", false, () -> edit(null, true)));
        actions.add(Theme.button("+ 일일 일정 추가", true, () -> edit(null, false)));
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel mapCard() {
        JPanel panel = Theme.card(new BorderLayout(0, 10));
        JPanel top = Theme.transparent(new BorderLayout());
        JPanel titles = Theme.transparent(new GridLayout(2,1,0,5));
        titles.add(mapTitle);
        titles.add(mapSubtitle);
        top.add(titles,BorderLayout.WEST);
        JPanel controls = Theme.transparent(new FlowLayout(FlowLayout.RIGHT,5,0));
        controls.add(Theme.button("-",false,() -> map.zoom(1/1.3)));
        controls.add(Theme.button("+",false,() -> map.zoom(1.3)));
        controls.add(Theme.button("맞춤",false,map::fit));
        top.add(controls,BorderLayout.EAST);
        JPanel mapHeader = Theme.transparent(new BorderLayout(0, 11));
        mapHeader.add(top, BorderLayout.NORTH);
        JPanel endpoints = Theme.transparent(new GridLayout(2, 1, 0, 4));
        endpoints.add(searchFrom);
        endpoints.add(searchTo);
        searchRouteInfo.add(endpoints, BorderLayout.CENTER);
        searchRouteInfo.add(Theme.button("일정 지도로", false, this::showScheduleMap), BorderLayout.EAST);
        searchRouteInfo.add(searchRouteNote, BorderLayout.SOUTH);
        searchRouteInfo.setVisible(false);
        mapHeader.add(searchRouteInfo, BorderLayout.CENTER);
        panel.add(mapHeader,BorderLayout.NORTH);
        panel.add(map,BorderLayout.CENTER);
        JPanel legend = Theme.transparent(new BorderLayout());
        legend.add(mapLegend,BorderLayout.WEST);
        legend.add(Theme.label("휠 확대 · 드래그 이동",11,Theme.MUTED),BorderLayout.EAST);
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
        searchRouteInfo.setVisible(searching);
        mapTitle.setText(searching ? "최단거리 검색" : "하루의 지도");
        mapLegend.setText(searching ? "출발 → 도착   ━ 최단 도보 경로" : "● 일정 순서   ━ 도보 경로   ● 즐겨찾기");
        mapSubtitle.setForeground(Theme.MUTED);
        if (searching) {
            RouteService.Route route = searchedRoute.route();
            searchFrom.setText("출발  " + searchedRoute.from().name());
            searchTo.setText("도착  " + searchedRoute.to().name());
            searchFrom.setToolTipText(searchedRoute.from().name());
            searchTo.setToolTipText(searchedRoute.to().name());
            searchRouteNote.setText(route.message());
            searchRouteNote.setToolTipText(route.message());
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

    private void findRoute() {
        RouteDialog.Result result = RouteDialog.open(this, data, routes, model, selectedBuilding);
        if (result == null) return;
        searchedRoute = result;
        map.setSearchRoute(result.from(), result.to(), result.route());
        map.fit();
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
        EventDialog.Result result=EventDialog.open(this,data,model,selectedDate,selectedBuilding,event,repeat,slot);
        if(result==null)return;
        if(result.deleted())model.removeEvent(result.event().id());
        else model.putEvent(result.event());
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
