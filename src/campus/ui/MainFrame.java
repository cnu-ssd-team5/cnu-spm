package campus.ui;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.plaf.LayerUI;
import campus.friends.FriendsDialog;
import campus.friends.FriendSchedules;
import campus.friends.FriendSchedules.Friend;
import campus.friends.ScheduleComparison;
import campus.map.CampusMap;
import campus.map.CampusMap.Building;
import campus.map.RouteService;
import campus.model.PlannerModel;
import campus.model.PlannerModel.Event;
import campus.model.PlannerModel.Occurrence;
import campus.model.PlannerStore;
import campus.service.DayPlanService;
import campus.service.DayPlanService.Leg;
import campus.service.DeadlineReminderService;
import campus.calendar.*;
import campus.places.PlaceService;
import campus.sharing.ScheduleSharing;
import campus.timetable.TimetablePanel;
import campus.timetable.TimetableService;
import campus.materials.CourseMaterialsPanel;
import campus.weather.WeatherForecast;

/** 메인 창. 화면 배치와 선택한 날짜·건물을 관리하고, 변경이 생기면 저장합니다. */
public final class MainFrame extends JFrame {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final String PLANNER_VIEW = "플래너";
    private static final String MATERIALS_VIEW = "과목자료";
    private static final List<String> VIEW_ORDER = List.of("지도", "달력", "주간시간표", PLANNER_VIEW, MATERIALS_VIEW);
    private final CampusMap data;
    private final RouteService routes;
    private final DayPlanService plans;
    private final PlannerModel model;
    private final Path statePath;
    private final MapPanel map;
    private final PlaceService placeService;
    private final FriendSchedules friendSchedules;
    private FriendsDialog friendsDialog;
    private Friend activeFriend;
    private Friend comparedFriend;
    private CampusMap friendPlaces;
    private RouteService friendRoutes;
    private DayPlanService friendPlans;
    private MapPanel friendMap;
    private final JPanel mapCanvas = Theme.transparent(new BorderLayout());
    private final JLabel friendStatus = Theme.heading("", 13);
    private final JPanel friendBanner = Theme.transparent(new BorderLayout(8, 0));
    private final JButton shareButton = Theme.quietButton("일정 공유", "share", this::showSharingDialog);
    private JButton fileSearch;
    private String lastScheduleView = "지도";
    private final CardLayout viewLayout=new CardLayout();
    private final JPanel views=new JPanel(viewLayout);
    private final TimetablePanel timetable;
    private final CalendarPanel calendar;
    private final TimetablePanel ownTimetableComparison, friendTimetableComparison;
    private final CalendarPanel ownCalendarComparison, friendCalendarComparison;
    private final ScheduleComparison timetableView, calendarView;
    private final CourseMaterialsPanel materials;
    private final DailyPlannerPanel dailyPlanner;
    private final JButton previousView = viewArrow(-1);
    private final JButton nextView = viewArrow(1);
    private String activeView="지도";
    private LocalDate plannerDate = PlannerTime.today();
    private boolean workspaceHovered;
    private final java.util.Map<String,JButton> viewButtons=new java.util.LinkedHashMap<>();
    private final campus.folder.FolderModule folders;
    private final WeekBar week;
    private final PlacesPanel places;
    private final SchedulePanel daily;
    private LocalDate selectedDate = PlannerTime.today();
    private Building selectedBuilding;
    private Building routeFrom,routeTo;
    private final JLabel mapSubtitle = Theme.label(" ", 12, Theme.MUTED);
    private final JLabel mapLegend = Theme.label("● 일정 순서   ━ 도보 경로   ● 즐겨찾기", 11, Theme.MUTED);
    private static final String ROUTE_SWITCH_TIP = "수업 사이 도보 경로 선을 켜고 끕니다. 일정 번호와 이동 시간 요약은 그대로 보입니다.";
    private final ToggleSwitch routeSwitch = new ToggleSwitch("경로 표시", true);
    private RouteDialog.Result searchedRoute;
    private final javax.swing.Timer noteTimer;
    private boolean unsaved;
    private final NotificationCenter notifications;
    private final DeadlineReminderService reminders;
    private final javax.swing.Timer reminderTimer;
    private final TimeToolsPanel timeTools;
    private boolean workspaceExpanded;
    private boolean navigationCollapsed, detailsCollapsed;
    private final JPanel navigationPanel = Theme.transparent(new BorderLayout(0, 16));
    private final JPanel detailsPanel = Theme.transparent(new BorderLayout());
    private final JPanel navigationShell = Theme.transparent(new BorderLayout());
    private final JPanel detailsShell = Theme.transparent(new BorderLayout());
    private final JPanel navigationControl = Theme.transparent(new BorderLayout());
    private final JPanel navigationHandle = Theme.transparent(new BorderLayout());
    private final JPanel detailsHandle = Theme.transparent(new BorderLayout());
    private final JButton navigationToggle = Theme.button("메뉴 접기", false, this::toggleNavigation);
    private final JButton detailsToggle = Theme.button("세부 기능 접기", false, this::toggleDetails);
    private final JButton workspaceButton = Theme.button("집중 모드", false, this::toggleWorkspace);
    private MapWorkspace mapWorkspace;
    private final JTextField globalSearch = Theme.searchField("건물 · 장소 · 메모 검색");
    private final JComboBox<Semester> termChoice = new JComboBox<>();
    private final JButton dateButton = Theme.button("", false, this::showWeekPicker);
    private final JButton previousDate = Theme.quietButton("‹", "chevron", () -> selectWeekDate(selectedDate.minusDays(1)));
    private final JButton nextDate = Theme.quietButton("›", "chevron", () -> selectWeekDate(selectedDate.plusDays(1)));
    private final JPopupMenu weekPicker = new JPopupMenu();
    private final JPanel dateNavigation = createDateNavigation();
    private final JPanel weekNavigation = createWeekNavigation();
    private boolean updatingHeader;
    private final JButton quickEvent = Theme.quietButton("일정 추가", "calendar", () -> edit(null));
    private final JButton quickTask = Theme.quietButton("할일 입력", "planner", () -> editDeadline(null, selectedDate, false));
    private final JButton quickMaterials = Theme.quietButton("과목자료", "folder", () -> showView(MATERIALS_VIEW));
    private final JButton quickFiles = Theme.quietButton("파일 검색", "search", this::searchFiles);
    private final javax.swing.Timer weatherTimer = new javax.swing.Timer(3_600_000, e -> refreshWeather());
    private SwingWorker<Map<LocalDate, WeatherForecast.Day>, Void> weatherRequest;
    private Map<LocalDate, WeatherForecast.Day> forecast = Map.of();

    public MainFrame(CampusMap data, RouteService routes, PlannerModel model, Path statePath) throws IOException {
        super("Dispatcher");
        this.data = data;
        this.routes = routes;
        this.plans = new DayPlanService(data, routes);
        this.model = model;
        this.statePath = statePath;
        friendSchedules = new FriendSchedules(statePath.resolveSibling("friends"));
        placeService=new PlaceService(data,model,routes);
        unsaved = placeService.sync();
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
        calendar=new CalendarPanel(model,this::changeDate,this::openPlanner);
        ownTimetableComparison = new TimetablePanel(data, model, () -> false);
        ownTimetableComparison.setScheduleContext(data, model, true);
        friendTimetableComparison = new TimetablePanel(data, model, () -> false);
        friendTimetableComparison.linkVerticalScroll(ownTimetableComparison);
        ownCalendarComparison = new CalendarPanel(model, this::changeDate, date -> openComparedPlanner(null, date));
        ownCalendarComparison.setScheduleContext(model, true);
        friendCalendarComparison = new CalendarPanel(model, this::changeDate, date -> openComparedPlanner(comparedFriend, date));
        timetableView = new ScheduleComparison(timetable, ownTimetableComparison, friendTimetableComparison,
                this::chooseComparison, () -> setComparison(null));
        timetableView.installNavigation(weekNavigation);
        calendarView = new ScheduleComparison(calendar, ownCalendarComparison, friendCalendarComparison,
                this::chooseComparison, () -> setComparison(null));
        materials=new CourseMaterialsPanel(model,data,folders);
        dailyPlanner=new DailyPlannerPanel(this,model);
        week = new WeekBar(this, model);
        places = new PlacesPanel(this, data, model);
        daily = new SchedulePanel(this, data, model);
        noteTimer = new javax.swing.Timer(600, e -> persist());
        noteTimer.setRepeats(false);
        notifications=new NotificationCenter(this::showNotification);
        reminders=new DeadlineReminderService(statePath.toAbsolutePath().resolveSibling(statePath.getFileName()+".reminders.properties"));
        timeTools=new TimeToolsPanel(model, message -> notifications.show("타이머", message));
        reminderTimer=new javax.swing.Timer(60000,e->checkReminders());
        reminderTimer.start();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                refreshWeather();
                weatherTimer.start();
            }
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
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(Theme.BACKGROUND);
        page.add(header(), BorderLayout.NORTH);
        JPanel body = Theme.transparent(new BorderLayout(16, 0));
        body.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 16));
        navigationShell.setName("navigation-shell");
        navigationHandle.setName("navigation-handle");
        navigationHandle.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        navigationShell.add(navigation(), BorderLayout.CENTER);
        navigationShell.add(navigationHandle, BorderLayout.WEST);
        body.add(navigationShell, BorderLayout.WEST);
        weekPicker.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        week.panel.setPreferredSize(new Dimension(650, 150));
        weekPicker.add(week.panel);
        views.setOpaque(false);
        views.add(mapCard(),"지도");
        views.add(viewCard(timetableView),"주간시간표");
        views.add(viewCard(calendarView),"달력");
        views.add(viewCard(materials),MATERIALS_VIEW);
        views.add(viewCard(dailyPlanner),PLANNER_VIEW);
        body.add(viewSwitcher(), BorderLayout.CENTER);
        detailsPanel.setPreferredSize(new Dimension(300, 0));
        detailsPanel.add(daily.panel, BorderLayout.CENTER);
        configurePanelToggle(detailsToggle, "details-toggle", "panel");
        detailsShell.setName("details-shell");
        detailsHandle.setName("details-handle");
        detailsHandle.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        detailsShell.add(detailsPanel, BorderLayout.CENTER);
        detailsShell.add(detailsHandle, BorderLayout.EAST);
        body.add(detailsShell, BorderLayout.EAST);
        page.add(body, BorderLayout.CENTER);
        page.add(footer(), BorderLayout.SOUTH);
        setContentPane(page);
        installWorkspaceShortcuts();
        updateViewButtons();
        updateWorkspaceVisibility();
        setSize(1600, 980);
        setMinimumSize(new Dimension(1130, 680));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(getWidth(), screen.width-60), Math.min(getHeight(), screen.height-70));
        setLocationRelativeTo(null);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { updateWorkspaceVisibility(); revalidate(); }
        });
        updateWorkspaceVisibility();
        refresh();
        if (unsaved) noteTimer.restart();
    }

    LocalDate selectedDate() { return selectedDate; }
    Building selectedBuilding() { return selectedBuilding; }

    private JPanel header() {
        JPanel header=new JPanel(new BorderLayout());header.setBackground(Color.WHITE);header.setName("app-header");
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,Theme.LINE),
                BorderFactory.createEmptyBorder(12,16,12,16)));
        JPanel top=Theme.transparent(new GridBagLayout());GridBagConstraints c=new GridBagConstraints();
        c.gridy=0;c.gridx=0;c.insets=new Insets(0,0,0,16);
        JPanel brand=Theme.transparent(new FlowLayout(FlowLayout.LEFT,12,0));
        JLabel mark=Theme.label("",20,Theme.BLUE);mark.setIcon(UiIcons.of("cap",30));brand.add(mark);brand.add(Theme.heading("Dispatcher",22));top.add(brand,c);
        c.gridx++;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;
        JPanel searchSlot=new JPanel(null){
            @Override public void doLayout(){globalSearch.setBounds(0,0,Math.min(480,getWidth()),44);}
            @Override public Dimension getPreferredSize(){return new Dimension(340,44);}
            @Override public Dimension getMinimumSize(){return new Dimension(140,44);}
        };
        searchSlot.setOpaque(false);globalSearch.setPreferredSize(new Dimension(340,44));globalSearch.setMinimumSize(new Dimension(140,44));searchSlot.add(globalSearch);
        globalSearch.addActionListener(e->{
            if(folders.isBusy())return;showView("지도");navigationCollapsed=false;updateWorkspaceVisibility();revalidate();
            places.searchFor(globalSearch.getText(),globalSearch);
        });top.add(searchSlot,c);
        c.gridx++;c.weightx=0;c.fill=GridBagConstraints.NONE;c.insets=new Insets(0,0,0,12);
        termChoice.setFont(Theme.font(13,Font.BOLD));termChoice.setBackground(Color.WHITE);termChoice.setOpaque(false);
        termChoice.setBorder(BorderFactory.createEmptyBorder(4,8,4,4));
        Theme.comboBox(termChoice);
        termChoice.setRenderer(new DefaultListCellRenderer(){
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
                String text=value instanceof Semester term?term.year()+"년 "+term.season().label():"학기 선택";
                return super.getListCellRendererComponent(list,text,index,selected,focus);
            }
        });
        termChoice.getAccessibleContext().setAccessibleName("학기 이동");
        termChoice.addActionListener(e->{if(!updatingHeader&&termChoice.getSelectedItem() instanceof Semester term){
            if(folders.isBusy()){refreshHeader();return;}
            selectWeekDate(term.start());
        }});
        JPanel termControl=Theme.rounded(new BorderLayout(),16);termControl.setPreferredSize(new Dimension(152,44));
        termControl.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));termControl.add(termChoice);top.add(termControl,c);
        c.gridx++;c.insets=new Insets(0,0,0,0);top.add(timeTools,c);
        header.add(top,BorderLayout.NORTH);
        friendStatus.putClientProperty("html.disable",true);friendStatus.setForeground(Theme.BLUE);
        friendBanner.setBorder(BorderFactory.createEmptyBorder(12,0,0,0));friendBanner.add(friendStatus,BorderLayout.CENTER);
        friendBanner.add(Theme.button("내 일정으로 돌아가기",false,()->applyFriend(null)),BorderLayout.EAST);
        friendBanner.setVisible(false);header.add(friendBanner,BorderLayout.SOUTH);return header;
    }

    private void showWeekPicker(){
        if(folders.isBusy()||workspaceExpanded||!dateButton.isShowing())return;
        weekPicker.show(dateButton,Math.min(0,dateButton.getWidth()-650),dateButton.getHeight()+8);
    }

    /** 날짜 상태는 하나로 유지하고, 평소에는 오른쪽 일정 패널, 비교 중에는 비교 패널에 배치한다. */
    private JPanel createDateNavigation() {
        JPanel dates = Theme.transparent(new BorderLayout(4, 0));
        dates.setName("panel-date-navigation");
        previousDate.setName("panel-date-previous"); nextDate.setName("panel-date-next");
        previousDate.setToolTipText("이전 날짜"); nextDate.setToolTipText("다음 날짜");
        previousDate.getAccessibleContext().setAccessibleName("이전 날짜");
        nextDate.getAccessibleContext().setAccessibleName("다음 날짜");
        for (JButton arrow : List.of(previousDate, nextDate)) {
            arrow.setIcon(null);
            arrow.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            arrow.setPreferredSize(new Dimension(28, 32));
        }
        dateButton.setName("panel-date-picker");
        dateButton.putClientProperty("button.flat", true);
        dateButton.setIcon(UiIcons.of("calendar", 16));
        dateButton.setIconTextGap(8);
        dateButton.setFont(Theme.font(12, Font.BOLD));
        dateButton.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        dateButton.setToolTipText("선택 날짜 · 주간 날짜 열기");
        dates.add(previousDate, BorderLayout.WEST);
        dates.add(dateButton, BorderLayout.CENTER);
        dates.add(nextDate, BorderLayout.EAST);
        return dates;
    }

    /** 시간표 상단의 주 이동은 오른쪽 일정 패널의 일 이동과 별도 조작한다. */
    private JPanel createWeekNavigation() {
        JPanel weeks = Theme.transparent(new GridLayout(1, 2, 8, 0));
        weeks.setName("timetable-week-navigation");
        for (int direction : new int[]{-1, 1}) {
            boolean previous = direction < 0;
            JButton button = Theme.button(previous ? "‹ 이전 주" : "다음 주 ›", false,
                    () -> selectWeekDate(selectedDate.plusWeeks(direction)));
            button.setName(previous ? "timetable-week-previous" : "timetable-week-next");
            button.setPreferredSize(new Dimension(88, 32));
            button.setToolTipText(previous ? "이전 주" : "다음 주");
            button.getAccessibleContext().setAccessibleName(button.getToolTipText());
            weeks.add(button);
        }
        return weeks;
    }

    private void refreshHeader(){
        updatingHeader=true;
        try{
            Semester term=Semester.forDate(selectedDate);termChoice.removeAllItems();
            for(int year=Math.max(1,term.year()-1);year<=Math.min(9999,term.year()+1);year++)
                for(Semester.Season season:Semester.Season.values())termChoice.addItem(new Semester(year,season));
            termChoice.setSelectedItem(term);
            dateButton.setText(selectedDate.format(DateTimeFormatter.ofPattern("M월 d일 (E)",Locale.KOREAN)));
            dateButton.getAccessibleContext().setAccessibleName("선택 날짜 · 주간 날짜 열기");
        }finally{updatingHeader=false;}
    }

    private JPanel navigation() {
        navigationPanel.setName("navigation-panel");
        navigationPanel.setLayout(new BorderLayout(16,0));
        JPanel rail=Theme.transparent(new BorderLayout());rail.setPreferredSize(new Dimension(88,0));
        rail.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,0,1,Theme.LINE),
                BorderFactory.createEmptyBorder(4,12,16,12)));
        JPanel menu=Theme.transparent(new GridLayout(0,1,0,4));
        for(String view:VIEW_ORDER){
            String caption=switch(view){case "주간시간표"->"<html><center>주간<br>시간표</center></html>";case "과목자료"->"과목자료";default->view;};
            JButton button=Theme.navigationButton(caption,()->showView(view));button.setIcon(UiIcons.of(view,24));
            button.setPreferredSize(new Dimension(68,76));button.setName("view:"+view);
            button.getAccessibleContext().setAccessibleName(viewLabel(view));viewButtons.put(view,button);menu.add(button);
        }
        configurePanelToggle(navigationToggle, "navigation-toggle", "menu");
        mountControl(navigationControl, navigationToggle);
        JPanel railTop = Theme.transparent(new BorderLayout(0, 8));
        railTop.add(navigationControl, BorderLayout.NORTH);
        railTop.add(menu, BorderLayout.CENTER);
        rail.add(railTop, BorderLayout.NORTH);
        JButton friends=Theme.quietButton("친구","share",this::showFriendsDialog);friends.setFont(Theme.font(11,Font.PLAIN));
        friends.setVerticalTextPosition(SwingConstants.BOTTOM);friends.setHorizontalTextPosition(SwingConstants.CENTER);
        rail.add(friends,BorderLayout.SOUTH);navigationPanel.add(rail,BorderLayout.WEST);
        fileSearch=folders.searchButton();places.installFileSearch(fileSearch);
        places.panel.setPreferredSize(new Dimension(224,0));navigationPanel.add(places.panel,BorderLayout.CENTER);
        return navigationPanel;
    }

    private static String viewLabel(String view) {
        return switch (view) {
            case "달력" -> "달력 (월)";
            case "주간시간표" -> "주간단위시간표";
            default -> view;
        };
    }

    private static void configurePanelToggle(JButton button, String name, String icon) {
        button.setName(name);
        button.setText("");
        button.setIcon(UiIcons.of(icon, 18));
        button.putClientProperty("button.flat", true);
        button.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        button.setPreferredSize(new Dimension(32, 32));
    }

    private static void mountControl(JPanel slot, JButton button) {
        if (button.getParent() != slot) slot.add(button, BorderLayout.NORTH);
    }

    private JPanel footer() {
        JPanel footer=Theme.transparent(new BorderLayout(0,4));footer.setBorder(BorderFactory.createEmptyBorder(8,16,8,16));
        JPanel wrap=Theme.transparent(new FlowLayout(FlowLayout.CENTER,0,0));
        JPanel dock=Theme.rounded(new FlowLayout(FlowLayout.CENTER,4,4),52);dock.setBorder(BorderFactory.createEmptyBorder(4,12,4,12));dock.setName("quick-actions");
        quickEvent.setName("quick-event");quickTask.setName("quick-task");
        dock.add(quickTask);dock.add(Theme.quietButton("주간시간표","week",()->showView("주간시간표")));
        dock.add(quickMaterials);
        dock.add(quickEvent);dock.add(shareButton);dock.add(Theme.quietButton("가져오기","import",()->share(true)));dock.add(quickFiles);
        dock.add(Theme.quietButton("길찾기","route",()->{showView("지도");if("지도".equals(activeView)&&!folders.isBusy())findRoute();}));
        configurePanelToggle(workspaceButton, "workspace-focus", "expand");
        workspaceButton.setPreferredSize(new Dimension(32, 32));
        dock.add(workspaceButton);
        wrap.add(dock);footer.add(wrap,BorderLayout.CENTER);
        return footer;
    }

    private void installWorkspaceShortcuts() {
        for (int i = 0; i < VIEW_ORDER.size(); i++) {
            String view = VIEW_ORDER.get(i);
            bindWorkspaceKey("control " + (i + 1), "view:" + view, () -> showView(view));
        }
        bindWorkspaceKey("F11", "focus-workspace", this::toggleWorkspace);
        bindWorkspaceKey("ESCAPE", "restore-workspace", () -> { if (workspaceExpanded) toggleWorkspace(); });
    }

    private void searchFiles(){
        if(activeFriend==null&&!folders.isBusy()&&fileSearch!=null)fileSearch.doClick();
    }

    private void bindWorkspaceKey(String key, String name, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private void checkReminders() {
        java.util.List<DeadlineReminderService.Reminder> due=new java.util.ArrayList<>();
        reminders.check(model,PlannerTime.today(),due::add);
        if(!due.isEmpty()) {
            String summary=String.join(" / ",due.stream().limit(3).map(DeadlineReminderService.Reminder::message).toList());
            if(due.size()>3)summary+=" 외 "+(due.size()-3)+"개";
            notifications.show("마감 임박 "+due.size()+"개",summary);
        }
        if(!reminders.lastError().isEmpty())showNotification(reminders.lastError());
    }

    private void showNotification(String text) { timeTools.showNotice(text); }
    @Override public void dispose() {
        if (noteTimer != null) noteTimer.stop();
        weatherTimer.stop();
        if (weatherRequest != null) weatherRequest.cancel(true);
        if(reminderTimer!=null)reminderTimer.stop();
        if(timeTools!=null)timeTools.close();
        if (friendsDialog != null) friendsDialog.dispose();
        if(notifications!=null)notifications.close();
        super.dispose();
    }

    private void refreshWeather() {
        if (weatherRequest != null) return;
        double latitude = (data.minLat() + data.maxLat()) / 2;
        double longitude = (data.minLon() + data.maxLon()) / 2;
        showCalendarWeather("광주 예보 · Open-Meteo 조회 중…");
        weatherRequest = new SwingWorker<>() {
            @Override protected Map<LocalDate, WeatherForecast.Day> doInBackground() throws IOException {
                return WeatherForecast.fetch(latitude, longitude);
            }

            @Override protected void done() {
                weatherRequest = null;
                if (isCancelled() || !isDisplayable()) return;
                String status;
                try {
                    forecast = get();
                    status = "광주 예보 · Open-Meteo · " + PlannerTime.now().format(CLOCK) + " 갱신";
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException ex) {
                    status = forecast.isEmpty() ? "광주 예보 · 조회 실패" : "광주 예보 · 이전 자료 표시 중";
                }
                showCalendarWeather(status);
                week.showWeather(selectedDate, forecast);
                dailyPlanner.showWeather(forecast.get(plannerDate));
            }
        };
        weatherRequest.execute();
    }

    private void toggleWorkspace() {
        if(folders.isBusy())return;
        folders.closePopup();places.closePopup();
        workspaceExpanded=!workspaceExpanded;
        updateWorkspaceVisibility();
        workspaceButton.requestFocusInWindow();
        revalidate();repaint();
    }

    private void toggleNavigation() {
        if (folders.isBusy() || workspaceExpanded) return;
        folders.closePopup();
        places.closePopup();
        navigationCollapsed = !navigationCollapsed;
        updateWorkspaceVisibility();
        navigationToggle.requestFocusInWindow();
        revalidate(); repaint();
    }

    private void toggleDetails() {
        if (folders.isBusy() || !detailsToggle.isEnabled()) return;
        folders.closePopup();
        detailsCollapsed = !detailsCollapsed;
        updateWorkspaceVisibility();
        detailsToggle.requestFocusInWindow();
        revalidate(); repaint();
    }

    private void updateWorkspaceVisibility() {
        boolean comparing=comparedFriend!=null&&("주간시간표".equals(activeView)||"달력".equals(activeView));
        boolean context=!workspaceExpanded&&!navigationCollapsed&&!comparing&&"지도".equals(activeView)&&getWidth()>=1320;
        boolean navigationVisible = !workspaceExpanded && !navigationCollapsed;
        boolean detailsVisible = !workspaceExpanded && !detailsCollapsed && !comparing;
        navigationPanel.setVisible(navigationVisible);
        navigationShell.setVisible(!workspaceExpanded);
        navigationHandle.setVisible(!workspaceExpanded && navigationCollapsed);
        mountControl(navigationVisible ? navigationControl : navigationHandle, navigationToggle);
        places.panel.setVisible(context);
        navigationPanel.setPreferredSize(new Dimension(context?328:88,0));
        navigationShell.setPreferredSize(new Dimension(navigationCollapsed ? 40 : context ? 328 : 88, 0));
        boolean compact = getHeight() < 800;
        int itemHeight = compact ? 64 : 76;
        for (JButton button : viewButtons.values()) {
            if (button.getPreferredSize().height == itemHeight) continue;
            button.setPreferredSize(new Dimension(68, itemHeight));
            button.setBorder(BorderFactory.createEmptyBorder(compact ? 4 : 8, 4, compact ? 4 : 8, 4));
            button.setFont(Theme.font(compact ? 11 : 12, Font.BOLD));
            button.setIconTextGap(compact ? 4 : 8);
            button.getParent().invalidate();
        }
        detailsPanel.setPreferredSize(new Dimension(getWidth()>=1440?304:288,0));
        detailsPanel.setVisible(detailsVisible);daily.panel.setVisible(detailsVisible);
        detailsShell.setVisible(!workspaceExpanded && !comparing);
        detailsHandle.setVisible(!workspaceExpanded && !comparing && detailsCollapsed);
        detailsShell.setPreferredSize(new Dimension(detailsCollapsed ? 40 : getWidth() >= 1440 ? 304 : 288, 0));
        if (detailsVisible) daily.installCollapseControl(detailsToggle);
        else mountControl(detailsHandle, detailsToggle);
        if (comparing && "달력".equals(activeView)) {
            calendarView.installNavigation(dateNavigation);
        } else daily.installDateNavigation(dateNavigation);
        week.panel.setVisible(!workspaceExpanded||comparing);
        navigationToggle.setText("");navigationToggle.setEnabled(!workspaceExpanded);
        navigationToggle.setToolTipText(workspaceExpanded?"집중 모드를 먼저 해제하세요":"메뉴와 지도 검색 패널 접기·펼치기");
        detailsToggle.setText("");detailsToggle.setEnabled(!workspaceExpanded&&!comparing);
        detailsToggle.setToolTipText(comparing?"비교 중에는 요약을 숨깁니다":detailsCollapsed?"오른쪽 요약 펼치기":"오른쪽 요약 접기");
        workspaceButton.setText("");
        workspaceButton.setToolTipText("중앙 화면 집중 · F11 전환 / Esc 해제");
        navigationToggle.getAccessibleContext().setAccessibleName(navigationCollapsed?"메뉴 펼치기":"메뉴 접기");
        detailsToggle.getAccessibleContext().setAccessibleName(detailsCollapsed?"세부 기능 펼치기":"세부 기능 접기");
        workspaceButton.getAccessibleContext().setAccessibleName(workspaceExpanded?"확대 해제":"확대 보기");
        if(workspaceExpanded)weekPicker.setVisible(false);
    }

    private JPanel viewCard(JComponent content) {
        JPanel card=Theme.card(new BorderLayout());card.add(content);return card;
    }

    private JLayer<JLayeredPane> viewSwitcher() {
        JLayeredPane switcher = new JLayeredPane() {
            @Override public void doLayout() {
                int width = getWidth();
                int height = getHeight();
                views.setBounds(0,0,width,height);
                int y = Math.max(0, (height - 48) / 2);
                previousView.setBounds(0, y, 28, 48);
                nextView.setBounds(Math.max(0, width - 28), y, 28, 48);
            }
        };
        switcher.add(views, JLayeredPane.DEFAULT_LAYER);
        switcher.add(previousView, JLayeredPane.PALETTE_LAYER);
        switcher.add(nextView, JLayeredPane.PALETTE_LAYER);
        return new JLayer<>(switcher, new LayerUI<>() {
            @Override public void installUI(JComponent component) {
                super.installUI(component);
                ((JLayer<?>) component).setLayerEventMask(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
            }

            @Override public void uninstallUI(JComponent component) {
                ((JLayer<?>) component).setLayerEventMask(0);
                super.uninstallUI(component);
            }

            @Override public void eventDispatched(AWTEvent event, JLayer<? extends JLayeredPane> layer) {
                if (!(event instanceof MouseEvent mouse)) return;
                Point position = SwingUtilities.convertPoint(mouse.getComponent(), mouse.getPoint(), layer);
                boolean inside = layer.contains(position);
                if (inside != workspaceHovered) {
                    workspaceHovered = inside;
                    previousView.repaint();
                    nextView.repaint();
                }
            }
        });
    }

    private JButton viewArrow(int direction) {
        JButton arrow = new JButton() {
            @Override protected void paintComponent(Graphics graphics) {
                if (!workspaceHovered && !isFocusOwner()) return;
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean emphasized = getModel().isRollover() || isFocusOwner();
                g.setColor(new Color(255, 255, 255, emphasized ? 245 : 193));
                g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 12, 12);
                g.setColor(emphasized ? Theme.BLUE : Theme.LINE);
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
                g.setColor(emphasized ? Theme.BLUE : Theme.MUTED);
                g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int x = getWidth() / 2;
                int y = getHeight() / 2;
                g.drawPolyline(new int[]{x - direction * 3, x + direction * 4, x - direction * 3},
                        new int[]{y - 8, y, y + 8}, 3);
                g.dispose();
            }
            @Override public boolean contains(int x, int y) {
                return workspaceHovered && super.contains(x, y);
            }
        };
        arrow.setContentAreaFilled(false);
        arrow.setBorderPainted(false);
        arrow.setRequestFocusEnabled(false);
        arrow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        arrow.addActionListener(e -> showView(adjacentView(direction)));
        return arrow;
    }

    private void showCalendarWeather(String status) {
        calendar.showWeather(forecast, status);
        ownCalendarComparison.showWeather(forecast, status);
        friendCalendarComparison.showWeather(forecast, status);
    }

    private String adjacentView(int direction) {
        int next = Math.floorMod(VIEW_ORDER.indexOf(activeView) + direction, VIEW_ORDER.size());
        if (activeFriend != null && MATERIALS_VIEW.equals(VIEW_ORDER.get(next))) {
            next = Math.floorMod(next + direction, VIEW_ORDER.size());
        }
        return VIEW_ORDER.get(next);
    }

    private void showView(String name) {
        showView(name, null);
    }

    void openPlanner(LocalDate date) {
        showView(PLANNER_VIEW, date);
        if (activeFriend == null && PLANNER_VIEW.equals(activeView) && plannerDate.equals(date)) {
            SwingUtilities.invokeLater(dailyPlanner::focusAddTask);
        }
    }

    private void showView(String name, LocalDate targetPlannerDate) {
        if (folders.isBusy() || (activeFriend != null && MATERIALS_VIEW.equals(name))) return;
        noteTimer.stop(); if(unsaved&&!persist())return;
        if (PLANNER_VIEW.equals(name)) {
            plannerDate = targetPlannerDate == null ? PlannerTime.today() : targetPlannerDate;
            if (targetPlannerDate != null && !selectedDate.equals(targetPlannerDate)) selectDate(targetPlannerDate);
        }
        if (!MATERIALS_VIEW.equals(name)) lastScheduleView = name;
        folders.closePopup();
        activeView = name;
        viewLayout.show(views, name);
        updateViewButtons();
        updateWorkspaceVisibility();
        refresh();
    }

    void selectWeekDate(LocalDate date) {
        if (!PLANNER_VIEW.equals(activeView)) changeDate(date);
        else if (!date.equals(plannerDate) || !date.equals(selectedDate)) showView(PLANNER_VIEW, date);
    }
    private void updateViewButtons() {
        quickEvent.setEnabled(activeFriend == null);
        quickTask.setEnabled(activeFriend == null);
        quickMaterials.setEnabled(activeFriend == null);
        quickFiles.setEnabled(activeFriend == null);
        String readOnlyTip = activeFriend == null ? null : "친구 일정은 보기 전용입니다. 내 일정으로 돌아가면 추가할 수 있습니다.";
        quickEvent.setToolTipText(readOnlyTip);
        quickTask.setToolTipText(readOnlyTip);
        previousView.setToolTipText(adjacentView(-1) + " 보기");
        nextView.setToolTipText(adjacentView(1) + " 보기");
        previousView.getAccessibleContext().setAccessibleName(previousView.getToolTipText());
        nextView.getAccessibleContext().setAccessibleName(nextView.getToolTipText());
        viewButtons.forEach((name,button)->{
            boolean available = !MATERIALS_VIEW.equals(name) || activeFriend == null;
            button.setEnabled(available);
            button.setToolTipText(available ? null : "내 일정에서 과목자료를 볼 수 있습니다");
            boolean selected=name.equals(activeView);
            button.putClientProperty("navigation.selected", selected);
            button.putClientProperty("calendar.fill",selected?Theme.PALE_BLUE:null);
            button.setForeground(selected?Theme.BLUE:Theme.INK);button.repaint();
        });
    }
    private boolean saveChanged() {
        unsaved=true;boolean saved=persist();refresh();return saved;
    }
    void editDeadline(PlannerModel.Deadline item,LocalDate date,boolean linked) {
        if (activeFriend != null) return;
        var result=DeadlineDialog.open(this,date,item,linked);if(result==null)return;
        if(result.deleted())model.removeDeadline(result.deadline().id());else model.putDeadline(result.deadline());
        saveChanged();
    }
    void toggleDailyTask(PlannerModel.Deadline task) {
        if (activeFriend != null) return;
        model.toggleDeadline(task.id());
        saveChanged();
    }
    void deleteDailyTask(PlannerModel.Deadline task) {
        if (activeFriend != null) return;
        if (JOptionPane.showConfirmDialog(this, "이 체크리스트 항목을 삭제할까요?", "항목 삭제",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        model.removeDeadline(task.id());
        saveChanged();
    }
    void showSharingDialog() {
        if (folders.isBusy()) return;
        JOptionPane options = new JOptionPane("일정 파일", JOptionPane.PLAIN_MESSAGE);
        JButton export = Theme.button("내보내기", false, () -> options.setValue(Boolean.FALSE));
        JButton importButton = Theme.button("가져오기", false, () -> options.setValue(Boolean.TRUE));
        export.setEnabled(activeFriend == null);
        if (!export.isEnabled()) export.setToolTipText("내 일정으로 돌아가면 내보낼 수 있습니다.");
        options.setOptions(new Object[]{export, importButton});
        JDialog dialog = options.createDialog(this, "일정 공유");
        dialog.getRootPane().setDefaultButton(export.isEnabled() ? export : importButton);
        try {
            dialog.setVisible(true);
        } finally {
            dialog.dispose();
        }

        if (options.getValue() instanceof Boolean importing) share(importing);
    }

    void share(boolean importing) {
        if (folders.isBusy()) return;
        noteTimer.stop();
        if (unsaved && !persist()) return;
        try {
            if (importing) importSchedule(this);
            else if (activeFriend == null) ScheduleSharing.exportFile(this, model);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), importing ? "일정 가져오기" : "일정 내보내기",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importSchedule(Component owner) throws IOException {
        PlannerModel incoming = ScheduleSharing.readFile(owner);
        if (incoming == null) return;
        String[] choices = {"내 일정에 합치기", "친구로 가져오기", "취소"};
        int destination = JOptionPane.showOptionDialog(owner, "일정을 어디에 추가할까요?", "일정 가져오기",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[1]);
        if (destination == 0) {
            model.mergeFrom(incoming);
            unsaved = true;
            placeService.sync();
            saveChanged();
            return;
        }
        if (destination != 1) return;
        String name = JOptionPane.showInputDialog(owner, "친구 이름 (같은 이름을 입력하면 기존 친구에 추가됩니다)");
        if (name != null && friendSchedules.find(name) != null
                && JOptionPane.showConfirmDialog(owner, "이 친구의 일정에 추가할까요?", "친구 일정 가져오기",
                        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        if (name == null) return;
        try {
            Friend saved = friendSchedules.importSchedule(name, incoming);
            if (comparedFriend != null && comparedFriend.id().equals(saved.id())) setComparison(saved);
            if (activeFriend != null && activeFriend.id().equals(saved.id()) && changeScheduleContext(saved)) refresh();
            showFriendsDialog();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(owner, ex.getMessage(), "친구 이름", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showFriendsDialog() {
        if (friendsDialog == null) {
            friendsDialog = new FriendsDialog(this, this::applyFriend);
        }
        friendsDialog.refresh(friendSchedules, activeFriend);
        friendsDialog.setVisible(true);
        friendsDialog.toFront();
    }

    private void applyFriend(Friend friend) {
        if (changeScheduleContext(friend)) {
            if (friendsDialog != null) friendsDialog.setVisible(false);
            String next = MATERIALS_VIEW.equals(activeView) ? lastScheduleView : activeView;
            showView(next, PLANNER_VIEW.equals(next) ? plannerDate : null);
        }
    }

    private boolean changeScheduleContext(Friend friend) {
        if (folders.isBusy()) return false;
        noteTimer.stop();
        if (unsaved && !persist()) return false;
        if (activeFriend == friend) return true;
        try {
            CampusMap nextPlaces = friend == null ? null : placesFor(friend);
            RouteService nextRoutes = friend == null ? null : new RouteService(nextPlaces);
            MapPanel nextMap = friend == null ? null : new MapPanel(nextPlaces, friend.model(), this::selectBuilding);
            if (nextMap != null) {
                nextMap.setRouteMenu(this::routeMenu);
                nextMap.setScheduleRoutesVisible(routeSwitch.isSelected());
            }
            folders.closePopup();
            activeFriend = friend;
            friendPlaces = nextPlaces;
            friendRoutes = nextRoutes;
            friendPlans = friend == null ? null : new DayPlanService(nextPlaces, nextRoutes);
            friendMap = nextMap;
            searchedRoute = null;
            routeFrom = routeTo = selectedBuilding = null;
            map.clearSearchRoute();
            map.select(null);
            mapCanvas.removeAll();
            mapCanvas.add(visibleMap(), BorderLayout.CENTER);
            updateMapSelection();daily.showSearchedRoute(null);

            boolean readOnly = friend != null;
            PlannerModel current = displayedModel();
            CampusMap currentPlaces = displayedPlaces();
            timetable.setScheduleContext(currentPlaces, current, readOnly);
            calendar.setScheduleContext(current, readOnly);
            week.setScheduleModel(current);
            timeTools.setScheduleModel(current);
            daily.setScheduleContext(currentPlaces, current, readOnly);
            dailyPlanner.setScheduleContext(current, readOnly);
            places.setScheduleContext(currentPlaces, current, readOnly);
            fileSearch.setVisible(!readOnly);
            friendStatus.setText(readOnly ? friend.name() + "의 일정을 적용중" : "");
            friendStatus.setToolTipText(readOnly ? friendStatus.getText() + " · 보기 전용" : null);
            friendBanner.setVisible(readOnly);
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "친구 일정 열기", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private CampusMap placesFor(Friend friend) {
        CampusMap copy = data.copyBasePlaces();
        PlaceService.applyPlaces(copy, friend.model());
        return copy;
    }

    private PlannerModel displayedModel() { return activeFriend == null ? model : activeFriend.model(); }
    private CampusMap displayedPlaces() { return activeFriend == null ? data : friendPlaces; }
    private MapPanel visibleMap() { return activeFriend == null ? map : friendMap; }
    private RouteService displayedRoutes() { return activeFriend == null ? routes : friendRoutes; }

    private void chooseComparison() {
        Friend selected = comparedFriend == null ? activeFriend : comparedFriend;
        FriendsDialog.chooseForComparison(this, friendSchedules, selected, this::setComparison);
    }

    private void setComparison(Friend friend) {
        comparedFriend = friend;
        if (friend != null) {
            friendTimetableComparison.setScheduleContext(placesFor(friend), friend.model(), true);
            friendCalendarComparison.setScheduleContext(friend.model(), true);
            refreshComparison();
        }
        String name = friend == null ? null : friend.name();
        timetableView.showComparison(name);
        calendarView.showComparison(name);
        updateWorkspaceVisibility();
        revalidate();
    }

    private void refreshComparison() {
        if (comparedFriend == null) return;
        LocalDate start = TimetableService.sunday(selectedDate);
        int first = 8;
        for (int day = 0; day < 7; day++) {
            LocalDate date = start.plusDays(day);
            for (PlannerModel schedule : List.of(model, comparedFriend.model())) {
                for (Occurrence occurrence : schedule.occurrences(date)) {
                    first = Math.min(first, occurrence.event().start().getHour());
                }
            }
        }
        ownTimetableComparison.refresh(selectedDate, first);
        friendTimetableComparison.refresh(selectedDate, first);
        ownCalendarComparison.refresh(selectedDate);
        friendCalendarComparison.refresh(selectedDate);
    }

    private void openComparedPlanner(Friend friend, LocalDate date) {
        if (changeScheduleContext(friend)) openPlanner(date);
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

    JMenuItem buildingNoteItem(Building building) {
        if (activeFriend != null || building.outline().size() < 3) return null;
        String title = model.buildingNote(building.id()).isBlank() ? "메모 추가" : "메모 보기·수정";
        JMenuItem item = new JMenuItem(title);
        item.setEnabled(!folders.isBusy());
        item.addActionListener(e -> editBuildingNote(building, title));
        return item;
    }

    private void editBuildingNote(Building building, String title) {
        if (folders.isBusy()) return;
        String previous = model.buildingNote(building.id());
        JTextArea editor = new JTextArea(previous, 7, 32);
        editor.setFont(Theme.font(13, Font.PLAIN));
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);
        editor.getAccessibleContext().setAccessibleName("건물 메모");
        JPanel form = Theme.transparent(new BorderLayout(0, 8));
        form.add(Theme.heading(building.name(), 14), BorderLayout.NORTH);
        form.add(new JScrollPane(editor), BorderLayout.CENTER);
        Object[] buttons = {"저장", "취소"};
        int choice = JOptionPane.showOptionDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, buttons, buttons[0]);
        String note = editor.getText().strip();
        if (choice != 0 || note.equals(previous)) return;
        model.setBuildingNote(building.id(), note);
        saveChanged();
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
        mapCanvas.add(map,BorderLayout.CENTER);
        routeSwitch.setToolTipText(ROUTE_SWITCH_TIP);
        routeSwitch.addActionListener(e->setScheduleRoutesVisible(routeSwitch.isSelected()));
        mapWorkspace=new MapWorkspace(mapCanvas,routeSwitch,mapSubtitle,mapLegend,
                ()->visibleMap().resetView(),this::showScheduleMap,()->visibleMap().zoom(1.3),()->visibleMap().zoom(1/1.3));
        return mapWorkspace;
    }

    void findInlineRoute(Building source,Building destination){
        if(folders.isBusy())return;
        if(source==null||destination==null){
            RouteDialog.Result chosen=RouteDialog.open(this,displayedPlaces(),displayedRoutes(),displayedModel(),source,destination);
            if(chosen!=null)showRoute(chosen);
            return;
        }
        showRoute(new RouteDialog.Result(source,destination,displayedRoutes().route(source,destination)));
    }

    void openMaterials(){showView(MATERIALS_VIEW);}
    void openMaterials(Occurrence occurrence){if(activeFriend==null&&!folders.isBusy())folders.openOccurrence(getRootPane(),occurrence);}

    private void updateMapSelection(){
        if(mapWorkspace==null)return;
        Building building=selectedBuilding;
        mapWorkspace.showPlace(building,activeFriend!=null,building!=null&&displayedModel().isFavorite(building.id()),
                this::findRoute,()->edit(null),()->{if(building!=null&&activeFriend==null)folders.openAt(mapCanvas,new Point(mapCanvas.getWidth()/2,80),building);},this::toggleFavorite);
    }

    void changeDate(LocalDate date) {
        noteTimer.stop();
        if (unsaved && !persist()) return;
        selectDate(date);
        refresh();
    }

    private void selectDate(LocalDate date) {
        selectedDate = date;
        searchedRoute = null;
        daily.showSearchedRoute(null);
        visibleMap().clearSearchRoute();
    }

    private void refresh() {
        refreshHeader();
        timeTools.refreshSchedule(selectedDate);
        week.refresh(selectedDate);
        Semester semester=Semester.forDate(selectedDate);
        timetable.refresh(selectedDate);
        calendar.refresh(selectedDate, semester);
        if (activeFriend == null) materials.refresh(semester);
        refreshComparison();
        List<Occurrence> occurrences = displayedModel().occurrences(selectedDate);
        List<Leg> legs = (activeFriend == null ? plans : friendPlans).legs(occurrences);
        visibleMap().setOccurrences(occurrences, legs);
        daily.refresh(selectedDate, occurrences, legs);
        dailyPlanner.refresh(plannerDate, forecast.get(plannerDate));
        updateMapSummary();
        places.refresh();
        updateMapSelection();
        checkReminders();
        revalidate();
        repaint();
    }

    private void updateMapSummary() {
        boolean searching = searchedRoute != null;
        // 최단거리 결과를 보는 동안에는 일정 경로를 그리지 않으므로 스위치를 잠근다.
        routeSwitch.setEnabled(!searching);
        routeSwitch.setToolTipText(searching ? "일정 경로 버튼으로 돌아가면 경로 표시를 켜고 끌 수 있습니다." : ROUTE_SWITCH_TIP);
        mapLegend.setText(searching ? "출발 → 도착   ━ 최단 도보 경로"
                : routeSwitch.isSelected() ? "● 일정 순서   ━ 도보 경로   ● 즐겨찾기" : "● 일정 순서   ● 즐겨찾기");
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
        long count = displayedModel().occurrences(selectedDate).stream().filter(o -> !o.canceled()).count();
        double meters = visibleMap().legs().stream().filter(l -> l.route().reachable()).mapToDouble(l -> l.route().meters()).sum();
        long unavailable = visibleMap().legs().stream().filter(SchedulePanel::showsWalkingLeg).filter(l -> !l.route().reachable()).count();
        int minutes = visibleMap().legs().stream().filter(l -> l.route().reachable()).mapToInt(Leg::minutes).sum();
        mapSubtitle.setText(count == 0 ? (activeFriend == null ? "날짜를 고르고 일정을 추가해 보세요" : "등록된 일정 없음") :
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
            to.addActionListener(e->{routeTo=building;folders.closePopup();showRoute(new RouteDialog.Result(routeFrom,routeTo,displayedRoutes().route(routeFrom,routeTo)));});menu.add(to);
        }
        if(routeFrom!=null) {
            JMenuItem reset=new JMenuItem("출발·도착 선택 해제");reset.addActionListener(e->{routeFrom=null;routeTo=null;showScheduleMap();});menu.add(reset);
        }
        if (activeFriend != null) return menu;
        menu.addSeparator();
        JMenuItem create=new JMenuItem("폴더 생성");
        create.setEnabled(!folders.hasFolder(building)&&!folders.isBusy());
        create.addActionListener(e->folders.createFolder(this,building));
        menu.add(create);
        JMenuItem add=new JMenuItem("일정 추가");add.addActionListener(e->{selectBuilding(building);edit(null);});menu.add(add);
        JMenuItem rename=new JMenuItem("이름 변경");rename.addActionListener(e->renamePlace(building));menu.add(rename);
        JMenuItem note = buildingNoteItem(building);
        if (note != null) menu.add(note);
        if(building.id().startsWith("pin:")) menu.add(Theme.deleteMenuItem("핀 삭제", () -> deletePin(building)));
        return menu;
    }

    private void findRoute() {
        RouteDialog.Result result = RouteDialog.open(this, displayedPlaces(), displayedRoutes(), displayedModel(), routeFrom==null?selectedBuilding:routeFrom, routeTo);
        if (result == null) return;
        showRoute(result);
    }

    private void showRoute(RouteDialog.Result result) {
        routeFrom=result.from();routeTo=result.to();
        searchedRoute = result;
        daily.showSearchedRoute(result);
        visibleMap().setSearchRoute(result.from(), result.to(), result.route());
        updateMapSummary();
        revalidate();
        repaint();
    }

    private void showScheduleMap() {
        searchedRoute = null;
        daily.showSearchedRoute(null);
        visibleMap().clearSearchRoute();
        updateMapSummary();
        revalidate();
        repaint();
    }

    private void setScheduleRoutesVisible(boolean visible) {
        map.setScheduleRoutesVisible(visible);
        if (friendMap != null) friendMap.setScheduleRoutesVisible(visible);
        updateMapSummary();
    }

    /** 모든 등록·편집 진입점은 시간표의 공통 편집창으로 연결한다. */
    void edit(Event event) {
        if (activeFriend != null) return;
        noteTimer.stop();
        if (unsaved && !persist()) return;
        if (folders.isBusy()) return;
        if (event == null) timetable.addEvent(selectedDate, selectedBuilding);
        else {
            timetable.refresh(selectedDate);
            timetable.editEvent(event);
        }
    }

    void toggleCancellation(Event event) {
        if (activeFriend != null) return;
        model.toggleCancellation(event.id(),selectedDate);
        unsaved=true;
        persist();
        refresh();
    }

    void selectBuilding(Building building) {
        selectedBuilding=building;
        visibleMap().select(building.id());
        places.showSelected(building);
        updateMapSelection();
    }

    void toggleFavorite() {
        if (activeFriend != null) return;
        if(selectedBuilding==null)return;
        model.toggleFavorite(selectedBuilding.id());
        unsaved=true;
        persist();
        places.refresh();
        updateMapSelection();
        map.repaint();
    }

    void noteChanged() {
        if (activeFriend != null) return;
        unsaved = true;
        noteTimer.restart();
    }

    private boolean persist() {
        noteTimer.stop();
        try {
            folders.syncSchedules();
            PlannerStore.save(statePath,model);
            unsaved=false;
            return true;
        } catch(IOException ex) {
            unsaved=true;
            JOptionPane.showMessageDialog(this,"일정을 저장하지 못했습니다.\n"+statePath+"\n"+ex.getMessage()+"\n경로를 확인한 뒤 다시 변경하거나 창을 닫아 저장을 재시도하세요.","저장 실패",JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
}
