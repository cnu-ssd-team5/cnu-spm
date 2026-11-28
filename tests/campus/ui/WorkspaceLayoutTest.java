package campus.ui;

import campus.calendar.PlannerTime;
import campus.folder.FolderModule;
import campus.map.CampusMap;
import campus.map.RouteService;
import campus.model.PlannerModel;
import java.awt.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

/** UI state transitions use isolated data and never open the user's planner. */
public final class WorkspaceLayoutTest {
    private static int passed, failed;
    private static final List<String> ORDER = List.of("지도", "달력", "주간시간표", "플래너", "과목자료");
    private interface Check { void run() throws Exception; }
    private static void test(String name, Check check) {
        try { check.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable ex) { failed++; System.out.println("FAIL " + name); ex.printStackTrace(System.out); }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static Object field(Object target, String name) throws Exception {
        return ScheduleEntryWhiteBoxTest.field(target, name);
    }
    private static Object call(MainFrame frame, String name, Class<?> type, Object value) throws Exception {
        Method method = MainFrame.class.getDeclaredMethod(name, type);
        method.setAccessible(true);
        return method.invoke(frame, value);
    }
    private static JButton button(MainFrame frame, String name) throws Exception {
        return (JButton) field(frame, name);
    }
    private static JPanel panel(MainFrame frame, String name) throws Exception {
        return (JPanel) field(frame, name);
    }
    private static JButton namedButton(Container root, String name) {
        return ScheduleEntryWhiteBoxTest.components(root, JButton.class).stream()
                .filter(button -> name.equals(button.getName())).findFirst().orElseThrow();
    }
    private static void show(MainFrame frame, String view) throws Exception {
        call(frame, "showView", String.class, view);
        frame.validate();
    }
    private static void checkDialog(MainFrame frame, JButton trigger, String title) {
        AtomicBoolean opened = new AtomicBoolean();
        Timer closer = new Timer(80, event -> {
            for (Window window : frame.getOwnedWindows()) {
                if (window instanceof JDialog dialog && dialog.isShowing()) {
                    opened.set(title.equals(dialog.getTitle()));
                    dialog.dispose();
                }
            }
        });
        Timer timeout = new Timer(4000, event -> {
            for (Window window : Window.getWindows())
                if (window instanceof JDialog dialog && dialog.isShowing()) dialog.dispose();
        });
        timeout.setRepeats(false);
        closer.start(); timeout.start();
        try { trigger.doClick(0); }
        finally { closer.stop(); timeout.stop(); }
        require(opened.get(), "wrong or missing dialog: " + title);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("dispatcher-layout-");
        String previousHome = System.getProperty("campus.home");
        System.setProperty("campus.home", home.toString());
        CampusMap map = CampusMap.load(Path.of(args[0]));
        PlannerModel model = new PlannerModel();
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = null;
            try {
                Theme.configureLookAndFeel();
                frame = new MainFrame(map, new RouteService(map), model, home.resolve("data/planner.xml"));
                MainFrame host = frame;
                // A hidden peer exercises actual layout without launching network weather requests.
                host.addNotify(); host.validate();
                Map<String, JButton> views = (Map<String, JButton>) field(host, "viewButtons");
                PlacesPanel places = (PlacesPanel) field(host, "places");
                JTextField search = (JTextField) field(places, "search");
                JCheckBox favorites = (JCheckBox) field(places, "favoritesOnly");
                LocalDate date = PlannerTime.today().plusDays(8);
                host.changeDate(date);

                test("navigation-order-and-dispatcher-brand", () -> {
                    require(List.copyOf(views.keySet()).equals(ORDER), "wrong navigation order");
                    require(views.values().stream().map(item -> item.getAccessibleContext().getAccessibleName()).toList().equals(
                            List.of("지도", "달력 (월)", "주간단위시간표", "플래너", "과목자료")), "wrong labels");
                    require(host.getTitle().startsWith("Dispatcher"), "wrong window title");
                    require(ScheduleEntryWhiteBoxTest.components(host, JLabel.class).stream()
                            .anyMatch(label -> "Dispatcher".equals(label.getText())), "brand missing");
                    int y = -1;
                    for (JButton view : views.values()) {
                        require(view.getY() > y, "navigation is not vertical"); y = view.getY();
                    }
                });
                test("left-toggle-keeps-right-date-and-search-state", () -> {
                    search.setText("공대"); favorites.setSelected(true);
                    button(host, "navigationToggle").doClick(0); host.validate();
                    require(!panel(host, "navigationPanel").isVisible(), "left not collapsed");
                    require(panel(host, "detailsPanel").isVisible(), "right was also collapsed");
                    require(host.selectedDate().equals(date), "date changed");
                    button(host, "navigationToggle").doClick(0); host.validate();
                    require(panel(host, "navigationPanel").isVisible() && search.getText().equals("공대")
                            && favorites.isSelected(), "left state lost");
                });
                test("right-toggle-is-independent-of-left", () -> {
                    button(host, "detailsToggle").doClick(0);
                    require(!panel(host, "detailsPanel").isVisible()
                            && panel(host, "navigationPanel").isVisible(), "right toggle affected left");
                    button(host, "detailsToggle").doClick(0);
                });
                test("focus-restores-all-four-sidebar-combinations-on-all-five-views", () -> {
                    for (boolean left : List.of(false, true)) for (boolean right : List.of(false, true)) {
                        if ((boolean) field(host, "navigationCollapsed") != left) button(host, "navigationToggle").doClick(0);
                        if ((boolean) field(host, "detailsCollapsed") != right) button(host, "detailsToggle").doClick(0);
                        for (String view : ORDER) {
                            show(host, view);
                            button(host, "workspaceButton").doClick(0);
                            require(!panel(host, "navigationPanel").isVisible() && !panel(host, "detailsPanel").isVisible(), "focus left sides visible");
                            require(button(host, "workspaceButton").isShowing() == host.isShowing(), "restore control hidden");
                            button(host, "workspaceButton").doClick(0);
                            require(panel(host, "navigationPanel").isVisible() == !left
                                    && panel(host, "detailsPanel").isVisible() == !right, "focus lost sidebar choices");
                        }
                    }
                    button(host, "navigationToggle").doClick(0);
                    button(host, "detailsToggle").doClick(0);
                });
                test("collapsed-panels-stay-collapsed-through-navigation", () -> {
                    button(host, "navigationToggle").doClick(0);
                    button(host, "detailsToggle").doClick(0);
                    for (String view : ORDER) {
                        show(host, view);
                        require(!panel(host, "navigationPanel").isVisible() && !panel(host, "detailsPanel").isVisible(), "view reset collapse");
                    }
                    button(host, "navigationToggle").doClick(0);
                    button(host, "detailsToggle").doClick(0);
                });
                test("keyboard-navigation-and-focus-escape-work-with-hidden-menu", () -> {
                    button(host, "navigationToggle").doClick(0);
                    for (int i = 0; i < ORDER.size(); i++) {
                        Object action = host.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                                .get(KeyStroke.getKeyStroke("control " + (i + 1)));
                        host.getRootPane().getActionMap().get(action).actionPerformed(null);
                        require(field(host, "activeView").equals(ORDER.get(i)), "shortcut chose wrong page");
                    }
                    host.getRootPane().getActionMap().get("focus-workspace").actionPerformed(null);
                    host.getRootPane().getActionMap().get("restore-workspace").actionPerformed(null);
                    require(!(boolean) field(host, "workspaceExpanded") && !panel(host, "navigationPanel").isVisible(), "Escape reset collapsed menu");
                    button(host, "navigationToggle").doClick(0);
                });
                test("new-route-switch-and-clock-tools-survive-layout-change", () -> {
                    ToggleSwitch toggle = (ToggleSwitch) field(host, "routeSwitch");
                    toggle.doClick(0);
                    require(!((MapPanel) field(host, "map")).scheduleRoutesVisible(), "route switch disconnected");
                    button(host, "navigationToggle").doClick(0);
                    button(host, "workspaceButton").doClick(0);
                    button(host, "workspaceButton").doClick(0);
                    require(!toggle.isSelected(), "layout reset route switch");
                    require(((JPanel) field(host, "timeTools")).isVisible(), "clock tools missing");
                    button(host, "navigationToggle").doClick(0);
                    toggle.doClick(0);
                });
                test("quick-event-opens-existing-editor-without-creating-on-cancel", () -> {
                    show(host, "지도"); host.changeDate(date);
                    checkDialog(host, button(host, "quickEvent"), "일정 추가");
                    require(model.events().isEmpty(), "cancel created event");
                });
                test("quick-task-opens-existing-checklist-without-creating-on-cancel", () -> {
                    checkDialog(host, button(host, "quickTask"), "체크리스트 추가");
                    require(model.plannerTasks(date).isEmpty(), "cancel created task");
                });
                test("header-clock-opens-all-three-tools-after-reparenting", () -> {
                    TimeToolsPanel tools = (TimeToolsPanel) field(host, "timeTools");
                    JButton open = ScheduleEntryWhiteBoxTest.components(tools, JButton.class).get(0);
                    open.doClick(0);
                    JDialog window = (JDialog) field(tools, "window");
                    require(window.isVisible(), "clock window did not open");
                    require(((JTabbedPane) window.getContentPane()).getTabCount() == 3, "clock/timer/stopwatch missing");
                    window.setVisible(false);
                });
                test("minimum-window-size-keeps-all-pages-and-pills-usable", () -> {
                    host.setSize(1130, 680);
                    for (String view : ORDER) {
                        show(host, view);
                        require(panel(host, "views").getWidth() > 450, "central page too narrow");
                        for (JButton item : views.values()) require(item.getWidth() >= 60 && item.getHeight() >= 64 && item.getIcon()!=null, "icon navigation clipped");
                        JButton lastView = views.get("과목자료");
                        JButton listButton = ScheduleEntryWhiteBoxTest.components(panel(host, "navigationPanel"), JButton.class).stream()
                                .filter(item -> "시간표 목록".equals(item.getText())).findFirst().orElseThrow();
                        Rectangle lastBounds = SwingUtilities.convertRectangle(lastView.getParent(), lastView.getBounds(), panel(host, "navigationPanel"));
                        Rectangle listBounds = SwingUtilities.convertRectangle(listButton.getParent(), listButton.getBounds(), panel(host, "navigationPanel"));
                        require(lastBounds.y + lastBounds.height <= listBounds.y, "schedule list button overlaps the last navigation item: " + lastBounds + " / " + listBounds);
                        for (JButton quick : List.of(button(host, "quickEvent"), button(host, "quickTask")))
                            require(quick.getWidth() >= quick.getPreferredSize().width, "pill clipped");
                    }
                });
                test("compact-header-search-opens-map-and-keeps-query", () -> {
                    host.setSize(1130,680);show(host,"플래너");
                    JTextField global=(JTextField)field(host,"globalSearch");global.setText("공과");global.postActionEvent();
                    host.validate();
                    require(field(host,"activeView").equals("지도")&&search.getText().equals("공과"),"compact search disconnected");
                    require(((DefaultListModel<?>)field(places,"buildingItems")).size()>0,"place results missing");
                    require(button(host,"quickFiles").isEnabled()&&button(host,"quickFiles").getParent().isVisible(),"file search lost in compact mode");
                });
                test("campus-map-control-restores-real-map-transform", () -> {
                    show(host,"지도");MapPanel visible=(MapPanel)field(host,"map");visible.zoom(2);
                    JButton reset=ScheduleEntryWhiteBoxTest.components((Container)field(host,"mapWorkspace"),JButton.class).stream()
                            .filter(item->"캠퍼스 지도".equals(item.getText())).findFirst().orElseThrow();reset.doClick(0);
                    require((double)field(visible,"zoom")==1&&(double)field(visible,"panX")==0&&(double)field(visible,"panY")==0,"map reset disconnected");
                });
                test("inline-route-dialog-preserves-a-partial-destination", () -> {
                    CampusMap.Building destination=map.buildings().stream().filter(item->!item.name().replace(" ","").startsWith("이름없는")).findFirst().orElseThrow();
                    ScheduleEntryWhiteBoxTest.dialog(()->host.findInlineRoute(null,destination),dialog->{
                        try{
                            Object picker=field(dialog,"to");Method selected=picker.getClass().getDeclaredMethod("selected");selected.setAccessible(true);
                            require(destination.equals(selected.invoke(picker)),"route destination lost when opening full picker");
                        }catch(Exception error){throw new RuntimeException(error);}
                    });
                });
                test("panel-controls-live-with-content-without-weekly-summary-or-top-gap", () -> {
                    SchedulePanel sidebar = (SchedulePanel) field(host, "daily");
                    require(SwingUtilities.isDescendingFrom(button(host, "navigationToggle"), panel(host, "navigationPanel")), "left toggle outside panel");
                    require(SwingUtilities.isDescendingFrom(button(host, "detailsToggle"), sidebar.panel), "right toggle outside panel");
                    require(SwingUtilities.isDescendingFrom(button(host, "dateButton"), sidebar.panel), "date control still outside schedule panel");
                    require(SwingUtilities.isDescendingFrom(button(host, "workspaceButton"), button(host, "quickEvent").getParent()), "focus control outside quick actions");
                    List<String> labels = ScheduleEntryWhiteBoxTest.components(sidebar.panel, JLabel.class).stream().map(JLabel::getText).toList();
                    require(!labels.contains("이번 주 요약") && labels.containsAll(List.of("과제·할일", "이동 정보", "수강 과목 / 자료")), "summary removal lost other sections");
                    for (String view : ORDER) {
                        show(host, view);
                        require(panel(host, "views").getY() == 0, "old toolbar gap remains");
                    }
                });
                test("collapsed-panel-handles-stay-visible-and-reopen-original-controls", () -> {
                    show(host, "주간시간표");
                    for (String side : List.of("navigation", "details")) {
                        JButton toggle = button(host, side + "Toggle");
                        toggle.doClick(0); host.validate();
                        JPanel handle = panel(host, side + "Handle");
                        require(handle.isVisible() && SwingUtilities.isDescendingFrom(toggle, handle), "collapsed reopen control missing");
                        require(toggle.isEnabled() && toggle.getWidth() >= 30 && toggle.getHeight() >= 30, "reopen handle unusable");
                        toggle.doClick(0); host.validate();
                        require(!handle.isVisible() && panel(host, side + "Panel").isVisible(), "original panel not restored");
                        require(!SwingUtilities.isDescendingFrom(toggle, handle), "control left in hidden handle");
                    }
                });
                test("panel-date-navigation-updates-all-views-and-crosses-year-boundary", () -> {
                    for (String view : ORDER) {
                        show(host, view); host.changeDate(date);
                        namedButton(host, "panel-date-next").doClick(0);
                        require(host.selectedDate().equals(date.plusDays(1)), "day control did not move one day in " + view);
                        require("다음 날짜".equals(namedButton(host, "panel-date-next").getToolTipText()), "day control mislabeled in " + view);
                        if (view.equals("플래너")) require(field(host, "plannerDate").equals(date.plusDays(1)), "planner date disconnected");
                        namedButton(host, "panel-date-previous").doClick(0);
                        require(host.selectedDate().equals(date), "previous date disconnected in " + view);
                    }
                    host.changeDate(LocalDate.of(2026, 12, 31));
                    namedButton(host, "panel-date-next").doClick(0);
                    require(host.selectedDate().equals(LocalDate.of(2027, 1, 1)), "year boundary failed");
                    require(button(host, "dateButton").getText().contains("1월 1일"), "date label stale after navigation");
                    host.changeDate(date);
                });
                test("weekly-navigation-preserves-weekday-across-year-and-leap-month-boundaries", () -> {
                    show(host, "주간시간표");
                    JButton previous = namedButton(host, "timetable-week-previous");
                    JButton next = namedButton(host, "timetable-week-next");
                    require("이전 주".equals(previous.getToolTipText()) && "다음 주".equals(next.getAccessibleContext().getAccessibleName()), "weekly actions mislabeled");
                    for (LocalDate start : List.of(LocalDate.of(2026, 12, 31), LocalDate.of(2028, 2, 26), LocalDate.of(2028, 3, 2))) {
                        host.changeDate(start);
                        next.doClick(0);
                        require(host.selectedDate().equals(start.plusWeeks(1)), "forward week boundary failed: " + start);
                        require(host.selectedDate().getDayOfWeek() == start.getDayOfWeek(), "selected weekday changed");
                        previous.doClick(0);
                        require(host.selectedDate().equals(start), "week navigation not reversible");
                        previous.doClick(0);
                        require(host.selectedDate().equals(start.minusWeeks(1)), "backward week boundary failed: " + start);
                    }
                    show(host, "지도");
                    require("다음 날짜".equals(namedButton(host, "panel-date-next").getToolTipText()), "day action retained weekly label");
                    host.changeDate(date);
                });
                test("day-and-week-controls-occupy-separate-panels-and-work-together", () -> {
                    show(host, "주간시간표"); host.changeDate(date);
                    JPanel weeks = panel(host, "weekNavigation");
                    JPanel dates = panel(host, "dateNavigation");
                    SchedulePanel sidebar = (SchedulePanel) field(host, "daily");
                    require(weeks.getParent()==((Component) field(host, "timetable")).getParent(), "week controls outside timetable header");
                    require(SwingUtilities.isDescendingFrom(dates, sidebar.panel), "day controls outside daily panel");
                    require(!SwingUtilities.isDescendingFrom(weeks, sidebar.panel), "week controls placed in daily panel");
                    namedButton(host, "panel-date-next").doClick(0);
                    require(host.selectedDate().equals(date.plusDays(1)), "daily next skipped a week");
                    namedButton(host, "timetable-week-next").doClick(0);
                    require(host.selectedDate().equals(date.plusDays(8)), "weekly next did not preserve selected weekday");
                    namedButton(host, "panel-date-previous").doClick(0);
                    require(host.selectedDate().equals(date.plusWeeks(1)), "daily previous skipped a week");
                    button(host, "detailsToggle").doClick(0); host.validate();
                    require(!panel(host, "detailsPanel").isVisible() && weeks.getWidth() > 0, "closing daily panel hides week controls");
                    namedButton(host, "timetable-week-previous").doClick(0);
                    require(host.selectedDate().equals(date), "week navigation blocked by collapsed daily panel");
                    button(host, "detailsToggle").doClick(0); host.validate();
                });
                test("marked-captions-removed-with-calendar-weather-and-holidays-preserved", () -> {
                    List<String> labels = ScheduleEntryWhiteBoxTest.components(host, JLabel.class).stream().map(JLabel::getText).toList();
                    for (String text : labels) {
                        if (text == null) continue;
                        require(!text.contains("빈 시간대 드래그로 추가") && !text.contains("캠퍼스의 장소를 찾고")
                                && !text.contains("하루의 동선을 이어 보세요") && !text.contains("마감 알림:")
                                && !text.contains("기기에 자동 저장") && !text.contains("공휴일: 기본 목록")
                                && !text.contains("날짜 더블클릭 또는 Enter로 플래너 열기"), "removed caption remains: " + text);
                    }
                    var calendar = (campus.calendar.CalendarPanel) field(host, "calendar");
                    require(((BorderLayout) calendar.getLayout()).getLayoutComponent(BorderLayout.SOUTH) == null, "calendar footer still occupies space");
                    LocalDate holiday = LocalDate.of(2026, 12, 25);
                    var forecast = Map.of(holiday, new campus.weather.WeatherForecast.Day(0, 8, -2));
                    calendar.showWeather(forecast, "예보 갱신 확인");
                    calendar.refresh(holiday);
                    require(field(calendar, "weather").equals(forecast), "forecast data removed with caption");
                    require(ScheduleEntryWhiteBoxTest.components(calendar, JLabel.class).stream().anyMatch(label -> "성탄절".equals(label.getText())), "holiday no longer rendered");
                    require("예보 갱신 확인".equals(calendar.getToolTipText()), "forecast status unavailable on hover");
                    calendar.showWeather(Map.of(), null);
                    host.changeDate(date);
                });
            } catch (Exception ex) { failed++; ex.printStackTrace(System.out); }
            finally {
                if (frame != null) {
                    try { ((FolderModule) field(frame, "folders")).close(); }
                    catch (Exception ex) { failed++; ex.printStackTrace(System.out); }
                    frame.dispose();
                }
            }
        });
        if (previousHome == null) System.clearProperty("campus.home");
        else System.setProperty("campus.home", previousHome);
        System.out.println("WORKSPACE LAYOUT RESULT: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
