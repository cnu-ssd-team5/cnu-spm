package campus.ui;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import campus.map.MapData;
import campus.map.RouteService;
import campus.model.PlannerModel.Deadline;
import campus.model.PlannerModel.Event;
import campus.model.PlannerModel.Occurrence;
import campus.model.PlannerModel;
import campus.model.PlannerStore;
import campus.service.Place;
import campus.service.PlaceCatalog;
import campus.service.ScheduleSlot;

/** Coordinates views and user actions; map data and persisted state stay separate. */
public final class PlannerFrame extends JFrame {
    private final MapData data;
    private final PlannerModel model;
    private final PlaceCatalog places;
    private final Path statePath;
    private final CampusMapPanel map;
    private final SchedulePanel schedule;
    private final CalendarPanel calendar;
    private final DailyTimetablePanel timetable;
    private final CourseMaterialsPanel materials;
    private final WorkspacePanel workspace;
    private final javax.swing.Timer noteTimer;
    private LocalDate selectedDate = LocalDate.now();
    private Place selectedPlace;
    private RouteDialog.Result searchedRoute;
    private boolean refreshing;
    private boolean unsaved;

    private final JLabel weekTitle = Theme.heading(" ", 18);
    private final JPanel weekDays = Theme.transparent(new GridLayout(1, 7, 7, 0));
    private final JTextField search = new JTextField();
    private final JCheckBox favoritesOnly = new JCheckBox("즐겨찾기");
    private final DefaultListModel<Place> placeItems = new DefaultListModel<>();
    private final JList<Place> placeList = new JList<>(placeItems);
    private final JLabel selectedName = Theme.heading("장소 선택", 13);
    private final JButton favorite = Theme.button("☆ 즐겨찾기", false, this::toggleFavorite);
    private final JButton rename = Theme.button("이름 변경", false, this::renameSelected);
    private final JButton placeEvent = Theme.button("일정 추가", false, () -> editEvent(null, null));
    private final JLabel mapSummary = Theme.label(" ", 12, Theme.MUTED);
    private final JPanel routeDetails = Theme.transparent(new BorderLayout(8, 4));
    private final JLabel routeFrom = Theme.label(" ", 11, Theme.INK);
    private final JLabel routeTo = Theme.label(" ", 11, Theme.INK);
    private final JLabel routeNote = Theme.label(" ", 10, Theme.MUTED);
    private final JLabel saveStatus = Theme.label(" ", 10, Theme.MUTED);

    public PlannerFrame(MapData data, RouteService routes, PlannerModel model, Path statePath, Path homeDir) {
        super("전남대학교 일정");
        this.data = data;
        this.model = model;
        this.statePath = statePath;
        places = new PlaceCatalog(routes, model);
        noteTimer = new javax.swing.Timer(600, event -> persist());
        noteTimer.setRepeats(false);
        map = new CampusMapPanel(data, model, places, this::selectPlace, this::activatePlace, this::createPin);
        calendar = new CalendarPanel(model, this::changeDate,
                date -> { changeDate(date); editDeadline(null); }, this::editDeadline);
        timetable = new DailyTimetablePanel(model, places, event -> editEvent(event, null),
                time -> editEvent(null, ScheduleSlot.after(time)));
        materials = new CourseMaterialsPanel(model, homeDir.resolve("materials"), event -> editEvent(event, null));
        schedule = new SchedulePanel(model, places, new SchedulePanel.Actions() {
            public void add(ScheduleSlot slot) { editEvent(null, slot); }
            public void edit(Event event) { editEvent(event, null); }
            public void toggle(Occurrence occurrence) {
                model.toggleCancellation(occurrence.event().id(), occurrence.date());
                changed();
            }
            public void select(Place place) { selectPlace(place); workspace.showView(0); }
            public void addDeadline() { editDeadline(null); }
            public void editDeadline(Deadline task) { PlannerFrame.this.editDeadline(task); }
            public void toggleDeadline(Deadline task) {
                model.toggleDeadline(task.id());
                changed();
            }
            public void noteChanged(String text) {
                model.setDayNote(selectedDate, text);
                unsaved = true;
                noteTimer.restart();
            }
        });
        workspace = new WorkspacePanel(mapCard(), calendar, timetable, materials);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                noteTimer.stop();
                if (unsaved && !persist()) return;
                dispose();
            }
        });
        JPanel page = new JPanel(new BorderLayout(0, 14));
        page.setBackground(Theme.BACKGROUND);
        page.setBorder(BorderFactory.createEmptyBorder(18, 18, 10, 18));
        page.add(header(), BorderLayout.NORTH);
        JPanel body = Theme.transparent(new BorderLayout(14, 0));
        JPanel placePanel = placesPanel();
        placePanel.setPreferredSize(new Dimension(220, 0));
        body.add(placePanel, BorderLayout.WEST);
        JPanel center = Theme.transparent(new BorderLayout(0, 13));
        center.add(weekPanel(), BorderLayout.NORTH);
        JPanel main = Theme.transparent(new BorderLayout(14, 0));
        main.add(workspace, BorderLayout.CENTER);
        schedule.setPreferredSize(new Dimension(320, 0));
        main.add(schedule, BorderLayout.EAST);
        center.add(main, BorderLayout.CENTER);
        body.add(center, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        JPanel footer = Theme.transparent(new BorderLayout());
        footer.add(Theme.label("© OpenStreetMap contributors", 10, Theme.MUTED), BorderLayout.WEST);
        footer.add(saveStatus, BorderLayout.EAST);
        page.add(footer, BorderLayout.SOUTH);
        setContentPane(page);
        setMinimumSize(new Dimension(1110, 680));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1450, screen.width - 40), Math.min(930, screen.height - 65));
        setLocationRelativeTo(null);
        refresh();
    }

    private JPanel header() {
        JPanel panel = Theme.transparent(new BorderLayout());
        panel.add(Theme.heading("전남대학교", 22), BorderLayout.WEST);
        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(Theme.button("가져오기", false, this::importData));
        actions.add(Theme.button("공유하기", false, this::exportData));
        actions.add(Theme.button("+ 일일 일정 추가", true, () -> editEvent(null, null)));
        panel.add(actions, BorderLayout.EAST);
        return panel;
    }

    private JPanel weekPanel() {
        JPanel panel = Theme.card(new BorderLayout(0, 10));
        JPanel top = Theme.transparent(new BorderLayout());
        JPanel navigation = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 7, 0));
        navigation.add(Theme.button("‹", false, () -> changeDate(selectedDate.minusWeeks(1))));
        navigation.add(weekTitle);
        navigation.add(Theme.button("›", false, () -> changeDate(selectedDate.plusWeeks(1))));
        top.add(navigation, BorderLayout.WEST);
        top.add(Theme.button("오늘", false, () -> changeDate(LocalDate.now())), BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        weekDays.setPreferredSize(new Dimension(0, 58));
        panel.add(weekDays, BorderLayout.CENTER);
        return panel;
    }

    private JPanel placesPanel() {
        JPanel panel = Theme.card(new BorderLayout(0, 10));
        JPanel heading = Theme.transparent(new GridLayout(0, 1, 0, 8));
        heading.add(Theme.heading("장소", 17));
        Theme.field(search);
        search.setToolTipText("장소 검색");
        search.getAccessibleContext().setAccessibleName("장소 검색");
        heading.add(search);
        favoritesOnly.setOpaque(false);
        favoritesOnly.setFont(Theme.font(12, Font.PLAIN));
        heading.add(favoritesOnly);
        panel.add(heading, BorderLayout.NORTH);
        placeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        placeList.setBackground(Color.WHITE);
        placeList.setFixedCellHeight(40);
        placeList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                Place place = (Place) value;
                label.setText((model.isFavorite(place.id()) ? "★ " : "") + place.name());
                label.setToolTipText(place.name() + (place.pin() ? " · 핀" : ""));
                label.setFont(Theme.font(12, Font.PLAIN));
                label.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 4));
                label.setBackground(selected ? Theme.PALE_BLUE : Color.WHITE);
                label.setForeground(selected ? Theme.BLUE : Theme.INK);
                return label;
            }
        });
        placeList.addListSelectionListener(event -> {
            if (!refreshing && !event.getValueIsAdjusting() && placeList.getSelectedValue() != null) {
                selectPlace(placeList.getSelectedValue());
            }
        });
        placeList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int index = placeList.locationToIndex(event.getPoint());
                if (event.getClickCount() == 2 && index >= 0
                        && placeList.getCellBounds(index, index).contains(event.getPoint())) {
                    activatePlace(placeItems.get(index));
                }
            }
        });
        panel.add(Theme.scroll(placeList), BorderLayout.CENTER);
        JPanel bottom = Theme.transparent(new GridLayout(0, 1, 0, 8));
        bottom.add(selectedName);
        bottom.add(favorite);
        bottom.add(rename);
        bottom.add(placeEvent);
        panel.add(bottom, BorderLayout.SOUTH);
        favoritesOnly.addActionListener(event -> refreshPlaces());
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { refreshPlaces(); }
            public void removeUpdate(DocumentEvent event) { refreshPlaces(); }
            public void changedUpdate(DocumentEvent event) { refreshPlaces(); }
        });
        return panel;
    }

    private JPanel mapCard() {
        JPanel panel = Theme.card(new BorderLayout(0, 10));
        JPanel heading = Theme.transparent(new BorderLayout(0, 8));
        JPanel title = Theme.transparent(new BorderLayout());
        title.add(Theme.heading("지도", 17), BorderLayout.WEST);
        JPanel controls = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        controls.add(Theme.button("최단거리 찾기", false, this::findRoute));
        controls.add(Theme.button("-", false, () -> map.zoom(1 / 1.3)));
        controls.add(Theme.button("+", false, () -> map.zoom(1.3)));
        title.add(controls, BorderLayout.EAST);
        heading.add(title, BorderLayout.NORTH);
        heading.add(mapSummary, BorderLayout.CENTER);
        JPanel names = Theme.transparent(new GridLayout(2, 1, 0, 4));
        names.add(routeFrom);
        names.add(routeTo);
        routeDetails.add(names, BorderLayout.CENTER);
        routeDetails.add(Theme.button("일정 경로", false, this::showScheduleRoute), BorderLayout.EAST);
        routeDetails.add(routeNote, BorderLayout.SOUTH);
        routeDetails.setVisible(false);
        heading.add(routeDetails, BorderLayout.SOUTH);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(map, BorderLayout.CENTER);
        return panel;
    }

    private void changeDate(LocalDate date) {
        if (!flushNotes()) return;
        selectedDate = date;
        searchedRoute = null;
        map.clearSearchRoute();
        refresh();
    }

    private void refresh() {
        refreshing = true;
        try {
            LocalDate monday = selectedDate.with(DayOfWeek.MONDAY);
            weekTitle.setText(monday.getYear() + "년 " + monday.getMonthValue() + "월 "
                    + monday.getDayOfMonth() + "일 – " + monday.plusDays(6).getMonthValue() + "월 "
                    + monday.plusDays(6).getDayOfMonth() + "일");
            weekDays.removeAll();
            for (int index = 0; index < 7; index++) {
                LocalDate date = monday.plusDays(index);
                String day = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
                JButton button = Theme.button(day + " " + date.getDayOfMonth(), date.equals(selectedDate),
                        () -> changeDate(date));
                if (date.equals(LocalDate.now())) button.setToolTipText("오늘");
                weekDays.add(button);
            }
            map.setOccurrences(model.occurrences(selectedDate));
            schedule.refresh(selectedDate, map.legs());
            calendar.refresh(selectedDate);
            timetable.refresh(selectedDate);
            materials.refresh();
            refreshPlaces();
            if (selectedPlace != null) selectedPlace = places.find(selectedPlace.id());
            updateSelection();
            updateMapSummary();
        } finally {
            refreshing = false;
        }
        revalidate();
        repaint();
    }

    private void refreshPlaces() {
        boolean previous = refreshing;
        refreshing = true;
        String filter = search.getText().strip().toLowerCase(Locale.ROOT);
        placeItems.clear();
        places.namedPlaces().stream()
                .filter(place -> place.name().toLowerCase(Locale.ROOT).contains(filter))
                .filter(place -> !favoritesOnly.isSelected() || model.isFavorite(place.id()))
                .sorted(Comparator.comparing((Place place) -> !model.isFavorite(place.id()))
                        .thenComparing(Place::name))
                .forEach(placeItems::addElement);
        if (selectedPlace != null) placeList.setSelectedValue(places.find(selectedPlace.id()), true);
        refreshing = previous;
    }

    private void selectPlace(Place place) {
        selectedPlace = places.find(place.id());
        map.select(place.id());
        updateSelection();
        if (!Objects.equals(placeList.getSelectedValue(), selectedPlace)) {
            boolean previous = refreshing;
            refreshing = true;
            placeList.setSelectedValue(selectedPlace, true);
            refreshing = previous;
        }
    }

    private void updateSelection() {
        boolean selected = selectedPlace != null;
        selectedName.setText(selected ? selectedPlace.toString() : "장소 선택");
        selectedName.setToolTipText(selected ? selectedPlace.toString() : null);
        favorite.setEnabled(selected);
        rename.setEnabled(selected);
        placeEvent.setEnabled(selected);
        favorite.setText(selected && model.isFavorite(selectedPlace.id()) ? "★ 즐겨찾기 해제" : "☆ 즐겨찾기");
    }

    private void activatePlace(Place place) {
        selectPlace(place);
        if (!place.named() && !renameSelected()) return;
        editEvent(null, null);
    }

    private boolean renameSelected() {
        if (selectedPlace == null || !flushNotes()) return false;
        String entered = (String) JOptionPane.showInputDialog(this, "장소 이름", "이름 지정",
                JOptionPane.PLAIN_MESSAGE, null, null, selectedPlace.name());
        if (entered == null) return false;
        if (entered.isBlank()) {
            JOptionPane.showMessageDialog(this, "이름을 입력해 주세요.");
            return false;
        }
        places.rename(selectedPlace, entered.strip());
        String id = selectedPlace.id();
        changed();
        selectPlace(places.find(id));
        return true;
    }

    private void createPin(MapData.GeoPoint point) {
        if (!flushNotes()) return;
        String name = JOptionPane.showInputDialog(this, "핀 이름", "핀 추가", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "이름을 입력해 주세요.");
            return;
        }
        Place pin = places.addPin(point, name.strip());
        changed();
        selectPlace(pin);
    }

    private void toggleFavorite() {
        if (selectedPlace == null) return;
        model.toggleFavorite(selectedPlace.id());
        changed();
    }

    private void editEvent(Event event, ScheduleSlot slot) {
        if (!flushNotes()) return;
        EventDialog.Result result = EventDialog.open(this, places, model, selectedDate, selectedPlace, event, slot);
        if (result == null) return;
        if (result.deleted()) model.removeEvent(result.event().id());
        else model.putEvent(result.event());
        changed();
    }

    private void editDeadline(Deadline task) {
        if (!flushNotes()) return;
        DeadlineDialog.Result result = DeadlineDialog.open(this, selectedDate, task);
        if (result == null) return;
        if (result.deleted()) model.removeDeadline(result.deadline().id());
        else {
            model.putDeadline(result.deadline());
            selectedDate = result.deadline().date();
        }
        changed();
    }

    private void findRoute() {
        RouteDialog.Result result = RouteDialog.open(this, places, model, selectedPlace);
        if (result == null) return;
        searchedRoute = result;
        map.setSearchRoute(result.from(), result.to(), result.route());
        updateMapSummary();
        revalidate();
        repaint();
    }

    private void showScheduleRoute() {
        searchedRoute = null;
        map.clearSearchRoute();
        updateMapSummary();
        revalidate();
        repaint();
    }

    private void updateMapSummary() {
        routeDetails.setVisible(searchedRoute != null);
        mapSummary.setForeground(Theme.MUTED);
        if (searchedRoute != null) {
            RouteService.Route route = searchedRoute.route();
            routeFrom.setText("출발  " + searchedRoute.from().name());
            routeTo.setText("도착  " + searchedRoute.to().name());
            routeNote.setText(route.message().contains("추정") ? "보행로까지의 연결 일부 추정" : " ");
            routeNote.setToolTipText(route.message());
            if (route.reachable()) mapSummary.setText(distanceText(route.meters()));
            else {
                mapSummary.setText("경로 없음");
                mapSummary.setForeground(Theme.WARNING);
            }
        } else {
            double meters = map.legs().stream().filter(leg -> leg.route().reachable())
                    .mapToDouble(leg -> leg.route().meters()).sum();
            mapSummary.setText(map.legs().isEmpty() ? " " : distanceText(meters));
        }
    }

    private String distanceText(double meters) {
        int minutes = (int) Math.ceil(meters / (RouteService.WALKING_METERS_PER_SECOND * 60));
        return String.format(Locale.ROOT, "%,.0f m · 약 %d분", meters, minutes);
    }

    private void exportData() {
        if (!flushNotes()) return;
        JFileChooser chooser = shareChooser();
        chooser.setDialogTitle("공유 파일 저장");
        chooser.setSelectedFile(new java.io.File("campus-share-" + LocalDate.now() + ".xml"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath().toAbsolutePath();
        if (!path.toString().toLowerCase(Locale.ROOT).endsWith(".xml")) path = Path.of(path + ".xml");
        if (Files.exists(path) && JOptionPane.showConfirmDialog(this, "같은 이름의 파일을 덮어쓸까요?",
                "공유 파일 저장", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            PlannerStore.save(path, model);
            JOptionPane.showMessageDialog(this, "공유 파일을 저장했습니다.\n일정, 과제, 장소 이름, 핀, 즐겨찾기와 메모가 포함됩니다.\n" + path);
        } catch (IOException failure) {
            showError("공유 파일을 저장하지 못했습니다.", failure);
        }
    }

    private void importData() {
        if (!flushNotes()) return;
        JFileChooser chooser = shareChooser();
        chooser.setDialogTitle("공유 파일 가져오기");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            PlannerModel incoming = PlannerStore.load(chooser.getSelectedFile().toPath());
            String summary = "일정 " + incoming.events().size() + "개, 과제 " + incoming.deadlines().size()
                    + "개, 핀 " + incoming.pins().size() + "개를 현재 데이터에 합칩니다.";
            if (JOptionPane.showConfirmDialog(this, summary, "가져오기",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            model.mergeFrom(incoming);
            changed();
        } catch (IOException failure) {
            showError("공유 파일을 읽지 못했습니다.", failure);
        }
    }

    private JFileChooser shareChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("일정 공유 파일 (*.xml)", "xml"));
        return chooser;
    }

    private void changed() {
        unsaved = true;
        persist();
        refresh();
    }

    private boolean flushNotes() {
        noteTimer.stop();
        return !unsaved || persist();
    }

    private boolean persist() {
        noteTimer.stop();
        try {
            PlannerStore.save(statePath, model);
            unsaved = false;
            saveStatus.setText("저장됨");
            return true;
        } catch (IOException failure) {
            unsaved = true;
            saveStatus.setText("저장 실패");
            showError("변경 내용은 현재 창에 있습니다. 저장 경로를 확인해 주세요.", failure);
            return false;
        }
    }

    private void showError(String message, Exception failure) {
        JOptionPane.showMessageDialog(this, message + "\n" + failure.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
    }
}

