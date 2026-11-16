package campus;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import campus.MapData.Building;
import campus.PlannerModel.Event;
import campus.PlannerModel.Occurrence;

final class PlannerFrame extends JFrame {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN);
    private final MapData data;
    private final PlannerModel model;
    private final Path statePath;
    private final CampusMapPanel map;
    private LocalDate selectedDate = LocalDate.now();
    private Building selectedBuilding;
    private final JLabel weekTitle = Theme.heading(" ", 19);
    private final JLabel dayTitle = Theme.heading(" ", 18);
    private final JLabel daySummary = Theme.label(" ", 12, Theme.MUTED);
    private final JLabel mapTitle = Theme.heading("하루의 지도", 17);
    private final JLabel mapSubtitle = Theme.label(" ", 12, Theme.MUTED);
    private final JPanel searchRouteInfo = Theme.transparent(new BorderLayout(10, 5));
    private final JLabel searchFrom = Theme.label(" ", 11, Theme.INK);
    private final JLabel searchTo = Theme.label(" ", 11, Theme.INK);
    private final JLabel searchRouteNote = Theme.label(" ", 10, Theme.MUTED);
    private final JLabel mapLegend = Theme.label("● 일정 순서   ━ 도보 경로   ● 즐겨찾기", 11, Theme.MUTED);
    private RouteDialog.Result searchedRoute;
    private final JLabel saveStatus = Theme.label("기기에 자동 저장", 11, Theme.MUTED);
    private final JPanel weekDays = Theme.transparent(new GridLayout(1, 7, 8, 0));
    private final JPanel schedule = Theme.transparent(new GridBagLayout());
    private final JTextField search = new JTextField();
    private final JCheckBox favoritesOnly = new JCheckBox("즐겨찾기만 보기");
    private final DefaultListModel<Building> buildingItems = new DefaultListModel<>();
    private final JList<Building> buildings = new JList<>(buildingItems);
    private final JLabel buildingCount = Theme.label(" ", 11, Theme.MUTED);
    private final JLabel selectedName = Theme.heading("지도에서 건물을 선택하세요", 13);
    private final JLabel selectedDetail = Theme.label("건물 검색과 지도 클릭을 지원합니다", 11, Theme.MUTED);
    private final JButton favoriteButton = Theme.button("☆ 즐겨찾기", false, this::toggleFavorite);
    private final JButton buildingEventButton = Theme.button("이곳에 일정 추가", true, () -> edit(null, false));
    private final JTextArea dayNote = new JTextArea(3, 20);
    private final javax.swing.Timer noteTimer;
    private boolean refreshing;
    private boolean unsaved;

    PlannerFrame(MapData data, PlannerModel model, Path statePath) {
        super("CNU 캠퍼스 플래너 · 전남대학교 광주캠퍼스");
        this.data = data;
        this.model = model;
        this.statePath = statePath;
        map = new CampusMapPanel(data, model, this::selectBuilding);
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
        JPanel places = places();
        places.setPreferredSize(new Dimension(235, 0));
        body.add(places, BorderLayout.WEST);

        JPanel center = Theme.transparent(new BorderLayout(0, 15));
        center.add(week(), BorderLayout.NORTH);
        JPanel workspace = Theme.transparent(new BorderLayout(16, 0));
        workspace.add(mapCard(), BorderLayout.CENTER);
        JPanel daily = daily();
        daily.setPreferredSize(new Dimension(335, 0));
        workspace.add(daily, BorderLayout.EAST);
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

    private JPanel week() {
        JPanel panel = Theme.card(new BorderLayout(0, 13));
        JPanel navigation = Theme.transparent(new BorderLayout());
        JPanel previous = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        previous.add(Theme.button("‹", false, () -> changeDate(selectedDate.minusWeeks(1))));
        previous.add(weekTitle);
        previous.add(Theme.button("›", false, () -> changeDate(selectedDate.plusWeeks(1))));
        navigation.add(previous, BorderLayout.WEST);
        navigation.add(Theme.button("오늘", false, () -> changeDate(LocalDate.now())), BorderLayout.EAST);
        panel.add(navigation, BorderLayout.NORTH);
        weekDays.setPreferredSize(new Dimension(0, 70));
        panel.add(weekDays, BorderLayout.CENTER);
        return panel;
    }

    private JPanel places() {
        JPanel panel = Theme.card(new BorderLayout(0, 12));
        JPanel top = Theme.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 0;
        c.insets = new Insets(0,0,12,0);
        top.add(Theme.heading("캠퍼스 장소", 17),c);
        Theme.field(search);
        search.setToolTipText("건물명 검색");
        search.putClientProperty("JTextField.placeholderText", "건물명 검색");
        c.gridy++;
        c.insets = new Insets(0,0,7,0);
        top.add(Theme.label("OSM 건물명 검색",11,Theme.MUTED),c);
        c.gridy++;
        top.add(search,c);
        favoritesOnly.setFont(Theme.font(12,Font.PLAIN));
        favoritesOnly.setForeground(Theme.INK);
        favoritesOnly.setOpaque(false);
        c.gridy++;
        top.add(favoritesOnly,c);
        c.gridy++;
        c.insets = new Insets(0,0,0,0);
        top.add(buildingCount,c);
        panel.add(top,BorderLayout.NORTH);
        buildings.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        buildings.setBackground(Color.WHITE);
        buildings.setFixedCellHeight(44);
        buildings.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus) {
                JLabel label = (JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
                Building b = (Building)value;
                label.setText((model.isFavorite(b.id()) ? "★  " : "    ") + b.name());
                label.setFont(Theme.font(12,Font.PLAIN));
                label.setBorder(BorderFactory.createEmptyBorder(0,6,0,4));
                label.setBackground(selected ? Theme.PALE_BLUE : Color.WHITE);
                label.setForeground(selected ? Theme.BLUE : Theme.INK);
                label.setToolTipText(b.name());
                return label;
            }
        });
        buildings.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !refreshing && buildings.getSelectedValue()!=null) selectBuilding(buildings.getSelectedValue());
        });
        panel.add(Theme.scroll(buildings),BorderLayout.CENTER);
        JPanel bottom = Theme.transparent(new GridBagLayout());
        c.gridy = 0;
        c.insets = new Insets(8,0,7,0);
        bottom.add(selectedName,c);
        c.gridy++;
        bottom.add(selectedDetail,c);
        c.gridy++;
        bottom.add(favoriteButton,c);
        c.gridy++;
        bottom.add(buildingEventButton,c);
        panel.add(bottom,BorderLayout.SOUTH);
        favoritesOnly.addActionListener(e -> refreshBuildings());
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshBuildings(); }
            public void removeUpdate(DocumentEvent e) { refreshBuildings(); }
            public void changedUpdate(DocumentEvent e) { refreshBuildings(); }
        });
        return panel;
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

    private JPanel daily() {
        JPanel panel = Theme.transparent(new BorderLayout(0,14));
        JPanel eventsCard = Theme.card(new BorderLayout(0,12));
        JPanel title = Theme.transparent(new GridLayout(2,1,0,5));
        title.add(dayTitle);
        title.add(daySummary);
        eventsCard.add(title,BorderLayout.NORTH);
        eventsCard.add(Theme.scroll(schedule),BorderLayout.CENTER);
        panel.add(eventsCard,BorderLayout.CENTER);
        JPanel memo = Theme.card(new BorderLayout(0,10));
        JPanel memoTitle = Theme.transparent(new BorderLayout());
        memoTitle.add(Theme.heading("오늘의 메모",14),BorderLayout.WEST);
        memoTitle.add(Theme.label("자동 저장",10,Theme.MUTED),BorderLayout.EAST);
        memo.add(memoTitle,BorderLayout.NORTH);
        dayNote.setLineWrap(true);
        dayNote.setWrapStyleWord(true);
        Theme.field(dayNote);
        dayNote.setToolTipText("선택한 날짜의 준비물이나 할 일을 적어보세요");
        JScrollPane memoScroll = Theme.scroll(dayNote);
        memoScroll.setPreferredSize(new Dimension(0,86));
        memo.add(memoScroll,BorderLayout.CENTER);
        dayNote.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (refreshing) return;
                model.setDayNote(selectedDate,dayNote.getText());
                unsaved = true;
                saveStatus.setText("메모 저장 중…");
                noteTimer.restart();
            }
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
        });
        panel.add(memo,BorderLayout.SOUTH);
        return panel;
    }

    private void changeDate(LocalDate date) {
        noteTimer.stop();
        if (unsaved && !persist()) return;
        selectedDate = date;
        searchedRoute = null;
        map.clearSearchRoute();
        refresh();
    }

    private void refresh() {
        refreshing = true;
        try {
            LocalDate monday = selectedDate.with(DayOfWeek.MONDAY);
            weekTitle.setText(monday.getYear()+"년 "+monday.getMonthValue()+"월 "+monday.getDayOfMonth()+"일 — "+monday.plusDays(6).getMonthValue()+"월 "+monday.plusDays(6).getDayOfMonth()+"일");
            weekDays.removeAll();
            for (int i = 0; i < 7; i++) {
                LocalDate date = monday.plusDays(i);
                List<Occurrence> daily = model.occurrences(date);
                long count = daily.stream().filter(o -> !o.canceled()).count();
                String day = date.getDayOfWeek().getDisplayName(TextStyle.SHORT,Locale.KOREAN);
                boolean chosen = date.equals(selectedDate);
                JButton button = Theme.button("<html><center>"+day+"  "+date.getDayOfMonth()+"<br><font size='2'>"+(count==0 ? "일정 없음" : count+"개 일정")+"</font></center></html>",chosen,() -> changeDate(date));
                if (date.equals(LocalDate.now())) button.setToolTipText("오늘");
                weekDays.add(button);
            }
            List<Occurrence> occurrences = model.occurrences(selectedDate);
            map.setOccurrences(occurrences);
            refreshSchedule(occurrences);
            refreshBuildings();
            dayTitle.setText(selectedDate.format(DAY));
            dayNote.setText(model.dayNote(selectedDate));
            dayNote.setCaretPosition(0);
            updateSelectedBuilding();
        } finally {
            refreshing = false;
        }
        revalidate();
        repaint();
    }

    private void refreshSchedule(List<Occurrence> occurrences) {
        schedule.removeAll();
        long count = occurrences.stream().filter(o -> !o.canceled()).count();
        long canceled = occurrences.size()-count;
        daySummary.setText("진행 "+count+"개"+(canceled==0 ? "" : " · 휴강/제외 "+canceled+"개"));
        updateMapSummary();
        GridBagConstraints c = new GridBagConstraints();
        c.gridx=0;
        c.gridy=0;
        c.weightx=1;
        c.fill=GridBagConstraints.HORIZONTAL;
        c.anchor=GridBagConstraints.NORTH;
        c.insets=new Insets(0,0,10,0);
        if (occurrences.isEmpty()) {
            JPanel empty = Theme.transparent(new GridLayout(0,1,0,12));
            empty.setBorder(BorderFactory.createEmptyBorder(35,4,25,4));
            empty.add(Theme.heading("오늘의 일정을 채워보세요",15));
            empty.add(Theme.label("고정 수업은 매주 자동으로 표시됩니다.",12,Theme.MUTED));
            empty.add(Theme.label("동아리와 약속은 하루 일정으로 추가하세요.",11,Theme.MUTED));
            empty.add(Theme.button("+ 이 날짜에 일정 추가",true,() -> edit(null,false)));
            schedule.add(empty,c);
            c.gridy++;
        }
        int routeIndex=0;
        int activeIndex=0;
        List<Occurrence> activeOccurrences = occurrences.stream().filter(o -> !o.canceled()).toList();
        LocalTime occupiedUntil = null;
        if (!activeOccurrences.isEmpty()) {
            addSlotButton(ScheduleSlot.before(activeOccurrences.get(0).event().start()), c);
        } else if (!occurrences.isEmpty()) {
            addSlotButton(new ScheduleSlot(LocalTime.of(9, 0), LocalTime.of(10, 0)), c);
        }
        for (Occurrence occurrence : occurrences) {
            if (!occurrence.canceled() && activeIndex>0) {
                addSlotButton(ScheduleSlot.between(occupiedUntil, occurrence.event().start()), c);
                schedule.add(legCard(map.legs().get(routeIndex++)),c);
                c.gridy++;
            }
            schedule.add(eventCard(occurrence,occurrence.canceled() ? 0 : ++activeIndex),c);
            c.gridy++;
            if (!occurrence.canceled() && (occupiedUntil == null || occurrence.event().end().isAfter(occupiedUntil))) {
                occupiedUntil = occurrence.event().end();
            }
        }
        if (occupiedUntil != null) addSlotButton(ScheduleSlot.after(occupiedUntil), c);
        c.weighty=1;
        c.fill=GridBagConstraints.BOTH;
        schedule.add(Theme.transparent(new BorderLayout()),c);
    }

    private void addSlotButton(ScheduleSlot slot, GridBagConstraints constraints) {
        if (slot == null) return;
        String text = "+  " + slot.start().format(CLOCK) + " – " + slot.end().format(CLOCK) + " 일정 추가";
        JButton button = Theme.button(text, false, () -> edit(null, false, slot));
        button.setForeground(Theme.BLUE);
        button.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
        button.setToolTipText("선택한 날짜의 새 일정에 이 시간대를 미리 채웁니다");
        schedule.add(button, constraints);
        constraints.gridy++;
    }

    private void updateMapSummary() {
        boolean searching = searchedRoute != null;
        searchRouteInfo.setVisible(searching);
        mapTitle.setText(searching ? "최단거리 검색" : "하루의 지도");
        mapLegend.setText(searching ? "출발 → 도착   ━ 최단 도보 경로" : "● 일정 순서   ━ 도보 경로   ● 즐겨찾기");
        mapSubtitle.setForeground(Theme.MUTED);
        if (searching) {
            MapData.Route route = searchedRoute.route();
            searchFrom.setText("출발  " + searchedRoute.from().name());
            searchTo.setText("도착  " + searchedRoute.to().name());
            searchFrom.setToolTipText(searchedRoute.from().name());
            searchTo.setToolTipText(searchedRoute.to().name());
            searchRouteNote.setText(route.message());
            searchRouteNote.setToolTipText(route.message());
            if (route.reachable()) {
                int minutes = (int) Math.ceil(route.meters() / (CampusMapPanel.WALKING_METERS_PER_SECOND * 60));
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
        int minutes = map.legs().stream().filter(l -> l.route().reachable()).mapToInt(CampusMapPanel.Leg::minutes).sum();
        mapSubtitle.setText(count == 0 ? "날짜를 고르고 일정을 추가해 보세요" :
                count + "개 일정 · " + String.format(Locale.ROOT, "%,.0f m", meters) + " · 약 " + minutes + "분"
                        + (unavailable > 0 ? " · 일부 경로 없음" : ""));
    }

    private void findRoute() {
        RouteDialog.Result result = RouteDialog.open(this, data, model, selectedBuilding);
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

    private JPanel eventCard(Occurrence occurrence,int index) {
        Event event=occurrence.event();
        boolean canceled=occurrence.canceled();
        JPanel card=Theme.card(new BorderLayout(0,9));
        card.setBackground(canceled ? new Color(247,248,250) : Color.WHITE);
        card.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel details=Theme.transparent(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();
        c.gridx=0;
        c.weightx=1;
        c.fill=GridBagConstraints.HORIZONTAL;
        c.insets=new Insets(0,0,5,0);
        c.gridy=0;
        JLabel time=Theme.heading((canceled ? "" : index+"  ")+event.start().format(CLOCK)+" – "+event.end().format(CLOCK),13);
        time.setForeground(canceled ? Theme.MUTED : Theme.BLUE);
        details.add(time,c);
        c.gridy++;
        JLabel title=Theme.heading(event.title(),15);
        title.setToolTipText(event.title());
        if(canceled) title.setForeground(Theme.MUTED);
        details.add(title,c);
        c.gridy++;
        Building place=data.building(event.buildingId());
        JLabel location=Theme.label(place==null ? "OSM에서 장소를 찾을 수 없습니다" : place.name(),12,Theme.MUTED);
        location.setToolTipText(location.getText());
        location.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        location.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){if(place!=null)selectBuilding(place);}
        });
        details.add(location,c);
        c.gridy++;
        String state=canceled ? "휴강/제외 · 이 날짜에만 적용" : event.category()+" · "+(event.recurring() ? "매주 반복" : "하루 일정");
        details.add(Theme.label(state,11,canceled ? Theme.WARNING : Theme.MUTED),c);
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
            JLabel note=Theme.label("<html><div style='width:225px'>"+Theme.html(memo).replace("\n","<br>")+"</div></html>",11,Theme.MUTED);
            note.setToolTipText(event.note());
            details.add(note,c);
        }
        card.add(details,BorderLayout.CENTER);
        JPanel actions=Theme.transparent(new FlowLayout(FlowLayout.RIGHT,6,0));
        actions.add(Theme.button("편집",false,() -> edit(event,event.recurring())));
        String cancelLabel=canceled ? "복원" : event.category()==PlannerModel.Category.CLASS ? "휴강" : "오늘 제외";
        actions.add(Theme.button(cancelLabel,canceled,() -> {
            model.toggleCancellation(event.id(),selectedDate);
            unsaved=true;
            persist();
            refresh();
        }));
        card.add(actions,BorderLayout.SOUTH);
        return card;
    }

    private JPanel legCard(CampusMapPanel.Leg leg) {
        JPanel panel=Theme.transparent(new BorderLayout(0,5));
        panel.setBorder(BorderFactory.createEmptyBorder(1,9,4,4));
        String travel;
        Color color=Theme.MUTED;
        if(!leg.route().reachable()) {
            travel="도보 경로를 찾지 못했습니다";
            color=Theme.WARNING;
            JLabel details=Theme.label("OSM 보행로 연결을 확인해 주세요",10,Theme.MUTED);
            details.setToolTipText(leg.route().message());
            panel.add(details,BorderLayout.CENTER);
        } else {
            travel=String.format(Locale.ROOT,"↓  도보 약 %d분 · %,.0f m",leg.minutes(),leg.route().meters());
            long slack=leg.gapMinutes()-leg.minutes();
            String timing;
            if(leg.gapMinutes()<0) {
                timing="일정 시간이 "+(-leg.gapMinutes())+"분 겹칩니다";
                color=Theme.WARNING;
            } else if(slack<0) {
                timing="다음 일정까지 이동 시간이 "+(-slack)+"분 부족합니다";
                color=Theme.WARNING;
            } else {
                timing="이동 후 "+slack+"분 여유 · "+leg.to().event().start().minusMinutes(leg.minutes()).format(CLOCK)+"까지 출발";
            }
            panel.add(Theme.label(timing,10,color),BorderLayout.CENTER);
            if(leg.route().message().contains("추정")) {
                JLabel estimate = Theme.label("출입구와 보행로 사이 연결 일부 추정",10,Theme.MUTED);
                estimate.setToolTipText(leg.route().message());
                panel.add(estimate,BorderLayout.SOUTH);
            }
        }
        panel.add(Theme.label(travel,11,color),BorderLayout.NORTH);
        return panel;
    }

    private void edit(Event event,boolean repeat) {
        edit(event, repeat, null);
    }

    private void edit(Event event, boolean repeat, ScheduleSlot slot) {
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

    private void selectBuilding(Building building) {
        selectedBuilding=building;
        map.select(building.id());
        updateSelectedBuilding();
        if(!Objects.equals(buildings.getSelectedValue(),building)) {
            boolean old=refreshing;
            refreshing=true;
            buildings.setSelectedValue(building,true);
            refreshing=old;
        }
    }

    private void updateSelectedBuilding() {
        boolean selected=selectedBuilding!=null;
        favoriteButton.setEnabled(selected);
        buildingEventButton.setEnabled(selected);
        if(!selected)return;
        selectedName.setText(selectedBuilding.name());
        selectedName.setToolTipText(selectedBuilding.name());
        selectedDetail.setText("OSM "+selectedBuilding.id());
        favoriteButton.setText(model.isFavorite(selectedBuilding.id()) ? "★ 즐겨찾기 해제" : "☆ 즐겨찾기 추가");
    }

    private void toggleFavorite() {
        if(selectedBuilding==null)return;
        model.toggleFavorite(selectedBuilding.id());
        unsaved=true;
        persist();
        refreshBuildings();
        updateSelectedBuilding();
        map.repaint();
    }

    private void refreshBuildings() {
        boolean old=refreshing;
        refreshing=true;
        String query=search.getText().strip().toLowerCase(Locale.ROOT);
        buildingItems.clear();
        data.buildings().stream()
                .filter(b -> b.name().toLowerCase(Locale.ROOT).contains(query))
                .filter(b -> !favoritesOnly.isSelected() || model.isFavorite(b.id()))
                .sorted(Comparator.comparing((Building b) -> !model.isFavorite(b.id()))
                        .thenComparing(b -> b.name().startsWith("이름 없는"))
                        .thenComparing(Building::name))
                .forEach(buildingItems::addElement);
        if(selectedBuilding!=null)buildings.setSelectedValue(selectedBuilding,true);
        buildingCount.setText(buildingItems.size()+"개 장소 · 즐겨찾기 "+model.favorites().size()+"개");
        refreshing=old;
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
