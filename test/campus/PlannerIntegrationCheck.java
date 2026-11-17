package campus;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import campus.PlannerModel.Deadline;
import campus.PlannerModel.Event;
import campus.PlannerModel.Occurrence;

/** Hidden Swing integration checks using the actual campus OSM extract and isolated state. */
public final class PlannerIntegrationCheck {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 16);
    private static final Semester TERM = Semester.forDate(DATE);
    private static int checks;

    private PlannerIntegrationCheck() { }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath();
        MapData data = MapData.load(root.resolve("data/chonnam-campus.osm"));
        Path temporary = Files.createTempDirectory("campus-integration-check-");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                CampusPlanner.configureLookAndFeel();
                runChecks(data, temporary);
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                for (Window window : Window.getWindows()) window.dispose();
            }
        });
        try {
            if (failure.get() != null) throw new AssertionError("Integration check failed", failure.get());
            System.out.println("PASS " + checks + " planner integration checks using " + data.buildings().size() + " OSM buildings");
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void runChecks(MapData data, Path temporary) throws Exception {
        PlannerModel model = new PlannerModel();
        PlaceCatalog places = new PlaceCatalog(data, model);
        Place building = places.namedPlaces().stream().filter(place -> place.name().contains("만들마루")).findFirst().orElseThrow();
        Place other = places.namedPlaces().stream().filter(place -> place.name().contains("G&R")).findFirst().orElseThrow();
        Place unnamed = places.all().stream().filter(place -> !place.named()).findFirst().orElseThrow();
        int namedBefore = places.namedPlaces().size();
        check(places.all().size() > namedBefore, "unnamed buildings remain selectable in catalog");
        check(places.namedPlaces().stream().noneMatch(place -> place.id().equals(unnamed.id())), "unnamed building hidden from listed places");
        places.rename(unnamed, "검증용 건물");
        check(places.namedPlaces().size() == namedBefore + 1 && places.find(unnamed.id()).name().equals("검증용 건물"), "naming adds building to visible places");
        MapData.Route road = places.route(building, other);
        check(road.reachable() && road.points().size() > 1, "real OSM route available");
        Place pin = places.addPin(road.points().get(road.points().size() / 2), "검증용 핀");
        check(pin.pin() && pin.id().startsWith("pin:") && places.namedPlaces().contains(pin), "pin appears as named place");
        check(places.route(building, pin).reachable() && places.route(pin, other).reachable(), "pin routes connect to OSM paths");
        PlacePicker picker = new PlacePicker(places, model);
        JComboBox<?> choices = field(picker, "choices");
        check(choices.getItemCount() == places.namedPlaces().size() + 1, "shared selector lists only named places");
        picker.select(pin);
        check(picker.selected().id().equals(pin.id()), "selector accepts pin");
        JTextField query = field(picker, "query");
        query.setText("검증용 핀");
        check(choices.getItemCount() == 2 && picker.selected().id().equals(pin.id()), "selector searches pin names");

        Event first = new Event("class", "검증 수업", building.id(), LocalTime.of(13, 0), LocalTime.of(15, 0),
                PlannerModel.Category.CLASS, DayOfWeek.MONDAY, null, "강의실 메모");
        Event second = new Event("meeting", "검증 모임", pin.id(), LocalTime.of(16, 0), LocalTime.of(17, 0),
                PlannerModel.Category.GROUP, null, DATE, "");
        model.putEvent(first, TERM);
        model.putEvent(second, TERM);
        model.putDeadline(new Deadline("pending", "미제출 검증 과제", DATE, "마감 메모", false));
        model.putDeadline(new Deadline("submitted", "완료 검증 과제", DATE, "", true, DATE.minusDays(2)));
        CampusMapPanel map = new CampusMapPanel(data, model, places, place -> { }, place -> { }, point -> { });
        map.setOccurrences(model.occurrences(DATE));
        check(map.legs().size() == 1 && map.legs().get(0).route().reachable(), "daily route includes pin event");

        List<ScheduleSlot> requestedSlots = new ArrayList<>();
        List<Event> requestedDeletes = new ArrayList<>();
        SchedulePanel schedule = new SchedulePanel(model, places, new SchedulePanel.Actions() {
            public void add(ScheduleSlot slot) { requestedSlots.add(slot); }
            public void edit(Event event) { }
            public void toggle(Occurrence occurrence) { model.toggleCancellation(occurrence.event().id(), occurrence.date()); }
            public void delete(Event event) { requestedDeletes.add(event); }
            public void select(Place place) { }
            public void addDeadline() { }
            public void editDeadline(Deadline deadline) { }
            public void toggleDeadline(Deadline deadline) { model.toggleDeadline(deadline.id()); }
            public void noteChanged(String text) { model.setDayNote(DATE, text); }
        });
        schedule.refresh(DATE, map.legs());
        List<JButton> addButtons = buttons(schedule).stream().filter(button -> button.getText().equals("+ 일정 추가")).toList();
        check(addButtons.size() == 2, "only top and bottom schedule insertion buttons remain");
        addButtons.forEach(JButton::doClick);
        check(requestedSlots.equals(List.of(ScheduleSlot.before(first.start()),
                ScheduleSlot.after(second.end()))), "top and bottom buttons preserve contextual time defaults");
        check(buttons(schedule).stream().noneMatch(button -> button.getText().contains("13:00")
                || button.getText().contains("15:00")), "time values omitted from insert button captions");
        List<JLabel> walking = labels(schedule).stream().filter(label -> label.getText().startsWith("도보 ")).toList();
        check(walking.size() == 1 && walking.get(0).getText().contains(" m · 약 ")
                && walking.get(0).getText().endsWith("분"), "schedule transfer shows distance and time only");
        check(labels(schedule).stream().noneMatch(label -> label.getText().contains("여유")
                || label.getText().contains("까지 출발") || label.getText().contains("매주 반복")), "unnecessary transfer and category captions removed");
        JLabel pending = labels(schedule).stream().filter(label -> label.getText().equals("11월 16일 마감 · 미완료")).findFirst().orElseThrow();
        JLabel submitted = labels(schedule).stream().filter(label -> label.getText().equals("11월 14일 완료")).findFirst().orElseThrow();
        check(pending.getForeground().getRed() > pending.getForeground().getGreen() * 2, "unsubmitted due date is red");
        check(submitted.getForeground().equals(Theme.MUTED), "submitted actual date is gray");
        check(labels(schedule).stream().anyMatch(label -> label.getText().equals("미제출 검증 과제")), "assignments appear in daily sidebar");
        check(labels(schedule).stream().anyMatch(label -> label.getText().equals("할일"))
                && buttons(schedule).stream().noneMatch(button -> button.getText().contains("제출")), "task UI uses generic completion wording");
        check(SchedulePanel.ended(new Occurrence(first, DATE, false), DATE.atTime(15, 0)), "end boundary dims completed schedule");
        check(!SchedulePanel.ended(new Occurrence(first, DATE, false), DATE.atTime(14, 59)), "ongoing schedule remains active");
        check(SchedulePanel.ended(new Occurrence(first, DATE, false), DATE.plusDays(1).atStartOfDay())
                && !SchedulePanel.ended(new Occurrence(first, DATE, false), DATE.minusDays(1).atTime(23, 59)), "schedule date takes precedence over clock time");
        model.toggleCancellation(second.id(), DATE);
        map.setOccurrences(model.occurrences(DATE));
        schedule.refresh(DATE, map.legs());
        JButton delete = buttons(schedule).stream().filter(button -> button.getText().equals("삭제")).findFirst().orElseThrow();
        check(Boolean.TRUE.equals(delete.getClientProperty("danger")), "excluded one-off offers red deletion action");
        check(((java.awt.BorderLayout) delete.getParent().getLayout()).getConstraints(delete).equals(java.awt.BorderLayout.WEST), "delete action stays at left of card controls");
        delete.doClick();
        check(requestedDeletes.equals(List.of(second)), "excluded one-off delete forwards exact event to caller");
        model.toggleCancellation(second.id(), DATE);
        map.setOccurrences(model.occurrences(DATE));
        schedule.refresh(DATE, map.legs());
        check(buttons(schedule).stream().noneMatch(button -> button.getText().equals("삭제")), "restoring one-off removes deletion action");
        javax.swing.JFrame lifecycle = new javax.swing.JFrame();
        lifecycle.setContentPane(schedule);
        lifecycle.pack();
        javax.swing.Timer clock = field(schedule, "clockTimer");
        check(clock.isRunning(), "displayable schedule starts time-color timer");
        lifecycle.dispose();
        check(!clock.isRunning(), "disposing schedule stops timer");
        lifecycle.pack();
        check(clock.isRunning(), "reattaching schedule restarts timer");
        lifecycle.dispose();
        check(!clock.isRunning(), "second disposal leaves no timer running");
        JTextArea sidebarNote = field(schedule, "note");
        sidebarNote.setText("작성 중인 일일 메모");
        check(model.dayNote(DATE).equals("작성 중인 일일 메모"), "sidebar memo updates model immediately");

        List<CampusMapPanel.Leg> dailyLegs = map.legs();
        int[] before = render(map);
        map.setSearchRoute(pin, building, places.route(pin, building));
        check(map.isSearchRouteActive() && map.legs().equals(dailyLegs), "independent search preserves scheduled legs");
        map.setOccurrences(model.occurrences(DATE));
        check(map.isSearchRouteActive(), "schedule refresh preserves independent search");
        map.clearSearchRoute();
        check(!map.isSearchRouteActive() && Arrays.equals(before, render(map)), "leaving search restores map exactly");
        check(model.events().size() == 2 && model.deadlines().size() == 2, "search never creates schedule or task records");

        checkEventDialog(places, model, pin, first);
        checkOverlapConfirmation(places, model, pin, first);
        checkRouteDialog(places, model, building, pin);
        checkDeadlineDialog(model.deadlines().stream().filter(task -> task.completed()).findFirst().orElseThrow());
        checkAgendaScrolling(data, building);

        WorkspacePanel deck = new WorkspacePanel(new JPanel(), new JPanel(), new JPanel(), new JPanel());
        check(deck.selectedView() == 0, "workspace starts on map");
        deck.showView(3);
        JButton next = field(deck, "next");
        JButton previous = field(deck, "previous");
        next.doClick();
        check(deck.selectedView() == 0, "workspace next wraps cyclically");
        previous.doClick();
        check(deck.selectedView() == 3, "workspace previous wraps cyclically");
        check(!next.isContentAreaFilled() && !previous.isContentAreaFilled(), "navigation arrows use custom translucent paint");

        PlannerFrame frame = new PlannerFrame(data, model, temporary.resolve("state.xml"), temporary);
        try {
            setField(frame, "selectedDate", DATE);
            invoke(frame, "refresh");
            JList<?> listed = field(frame, "placeList");
            check(listed.getModel().getSize() == places.namedPlaces().size(), "frame sidebar uses named catalog entries");
            for (int index = 0; index < listed.getModel().getSize(); index++) {
                check(((Place) listed.getModel().getElementAt(index)).named(), "frame excludes anonymous list rows");
            }
            check(buttons(frame).stream().noneMatch(button -> button.getText().equals("고정 시간표 추가")
                    || button.getText().equals("주간 시간표") || button.getText().equals("맞춤")), "removed redundant header and map fit controls");
            check(buttons(frame).stream().filter(button -> button.getText().equals("최단거리 찾기")).count() == 1,
                    "map contains one independent distance control");
            SchedulePanel frameSchedule = field(frame, "schedule");
            JTextArea note = field(frameSchedule, "note");
            note.setText("날짜 이동 직전에 수정한 메모");
            WorkspacePanel frameDeck = field(frame, "workspace");
            frameDeck.showView(2);
            WeeklyTimetablePanel weekly = field(frame, "timetable");
            check(weekly.isVisible() && weekly.getParent().isVisible(), "third workspace card is embedded weekly timetable");
            JButton previousView = field(frameDeck, "previous");
            previousView.doClick();
            JButton nextView = field(frameDeck, "next");
            check(nextView.getToolTipText().equals("주간 시간표 보기"), "swap navigation names weekly timetable");
            nextView.doClick();
            check(model.dayNote(DATE).equals(note.getText()), "view swap keeps pending memo edit");
            CampusMapPanel frameMap = field(frame, "map");
            RouteDialog.Result result = new RouteDialog.Result(building, pin, places.route(building, pin));
            setField(frame, "searchedRoute", result);
            frameMap.setSearchRoute(building, pin, result.route());
            invoke(frame, "changeDate", new Class<?>[] { LocalDate.class }, DATE.plusDays(1));
            check(PlannerStore.load(temporary.resolve("state.xml")).dayNote(DATE).equals("날짜 이동 직전에 수정한 메모"), "date navigation flushes note to isolated state");
            check(!frameMap.isSearchRouteActive() && field(frame, "searchedRoute") == null, "date navigation clears both search states");
            check(frameDeck.selectedView() == 2, "date change keeps selected workspace view");
            setField(frame, "searchedRoute", result);
            frameMap.setSearchRoute(building, pin, result.route());
            invoke(frame, "showScheduleRoute");
            check(!frameMap.isSearchRouteActive() && field(frame, "searchedRoute") == null, "schedule route button clears independent search");
            check(PlannerStore.load(temporary.resolve("state.xml")).events().equals(model.events()), "integrated persistence retains schedules and pins references");
            Semester vacation = new Semester(2026, Semester.Season.WINTER);
            invoke(frame, "changeSemester", new Class<?>[] { Semester.class }, vacation);
            check(labels(weekly).stream().anyMatch(label -> label.getText().contains(vacation.toString())), "season selector refreshes embedded weekly panel");
            check(frameDeck.selectedView() == 2 && weekly.isVisible(), "season change keeps weekly workspace selected");
        } finally {
            ((javax.swing.Timer) field(frame, "noteTimer")).stop();
            frame.dispose();
        }
    }

    private static void checkEventDialog(PlaceCatalog places, PlannerModel model, Place pin, Event original) throws Exception {
        Constructor<EventDialog> constructor = EventDialog.class.getDeclaredConstructor(javax.swing.JFrame.class,
                PlaceCatalog.class, PlannerModel.class, LocalDate.class, Place.class, Event.class, ScheduleSlot.class,
                Semester.class);
        constructor.setAccessible(true);
        EventDialog adding = constructor.newInstance(null, places, model, DATE, pin, null,
                ScheduleSlot.before(LocalTime.of(13, 0)), TERM);
        check(((TimePicker) field(adding, "start")).time().equals(LocalTime.of(12, 0))
                && ((TimePicker) field(adding, "end")).time().equals(LocalTime.of(13, 0)), "event dialog preloads insertion times");
        ((JTextField) field(adding, "title")).setText("핀 일정 저장 확인");
        invoke(adding, "save");
        EventDialog.Result added = field(adding, "result");
        check(added.event().buildingId().equals(pin.id()) && added.event().date().equals(PlannerTime.forNewItem(DATE)), "new event saves pin and clamps past selected date to today");
        LocalDate future = PlannerTime.today().plusDays(60);
        EventDialog futureDialog = constructor.newInstance(null, places, model, future, pin, null, null, Semester.forDate(future));
        check(((JTextField) field(futureDialog, "date")).getText().equals(future.toString()), "new event keeps future selected date");
        futureDialog.dispose();
        EventDialog editing = constructor.newInstance(null, places, model, DATE, pin, original,
                ScheduleSlot.after(LocalTime.of(18, 0)), TERM);
        check(((TimePicker) field(editing, "start")).time().equals(LocalTime.of(13, 0))
                && ((TimePicker) field(editing, "end")).time().equals(LocalTime.of(15, 0)), "editing keeps original time over insertion defaults");
        check(((JCheckBox) field(editing, "recurring")).isSelected(), "single dialog retains weekly schedule mode");
        check(labels(editing).stream().anyMatch(label -> label.getText().contains("16주")), "teaching term shows sixteen-week repeat range");
        invoke(editing, "save");
        EventDialog.Result saved = field(editing, "result");
        check(saved.event().equals(original), "editing round-trips existing event and note");
        adding.dispose();
        editing.dispose();
        EventDialog earlyAdding = constructor.newInstance(null, places, model, PlannerTime.today(), pin, null,
                new ScheduleSlot(LocalTime.of(7, 0), LocalTime.of(8, 0)), Semester.forDate(PlannerTime.today()));
        check(((TimePicker) field(earlyAdding, "start")).time().equals(LocalTime.of(8, 0))
                && ((TimePicker) field(earlyAdding, "end")).time().equals(LocalTime.of(9, 0)),
                "new event never imports pre08 suggested time into fresh hour choices");
        earlyAdding.dispose();
        Event earlyOriginal = new Event("legacy-early", "기존 이른 일정", pin.id(), LocalTime.of(7, 2), LocalTime.of(7, 50),
                PlannerModel.Category.APPOINTMENT, null, DATE, "");
        EventDialog earlyEditing = constructor.newInstance(null, places, model, DATE, pin, earlyOriginal,
                new ScheduleSlot(LocalTime.of(7, 0), LocalTime.of(8, 0)), TERM);
        check(((TimePicker) field(earlyEditing, "start")).time().equals(LocalTime.of(7, 2))
                && ((TimePicker) field(earlyEditing, "end")).time().equals(LocalTime.of(7, 50)),
                "editing original early schedule preserves its exact times");
        earlyEditing.dispose();
        Semester summer = new Semester(PlannerTime.today().getYear() + 1, Semester.Season.SUMMER);
        EventDialog vacation = constructor.newInstance(null, places, model, summer.start(), pin, null, null, summer);
        JLabel period = labels(vacation).stream().filter(label -> label.getText().startsWith("반복 · ")).findFirst().orElseThrow();
        check(period.getText().contains("여름방학") && !period.getText().contains("16주")
                && period.getText().contains("~"), "vacation repeat label shows actual season date range");
        vacation.dispose();
    }

    private static void checkAgendaScrolling(MapData data, Place building) throws Exception {
        PlannerModel model = new PlannerModel();
        PlaceCatalog places = new PlaceCatalog(data, model);
        for (int index = 0; index < 4; index++) {
            model.putEvent(new Event("scroll-class-" + index, "스크롤 검증 " + index, building.id(),
                    LocalTime.of(8 + index * 2, 0), LocalTime.of(9 + index * 2, 0),
                    PlannerModel.Category.CLASS, DATE.getDayOfWeek(), null, ""), TERM);
        }
        for (LocalDate date : List.of(DATE, DATE.plusWeeks(1))) {
            model.putDeadline(new Deadline("scroll-task-" + date, "할일 검증", date, "", false));
        }
        CampusMapPanel map = new CampusMapPanel(data, model, places, place -> { }, place -> { }, point -> { });
        SchedulePanel schedule = new SchedulePanel(model, places, new SchedulePanel.Actions() {
            public void add(ScheduleSlot slot) { }
            public void edit(Event event) { }
            public void toggle(Occurrence occurrence) { }
            public void select(Place place) { }
            public void addDeadline() { }
            public void editDeadline(Deadline deadline) { }
            public void toggleDeadline(Deadline deadline) { }
            public void noteChanged(String text) { model.setDayNote(DATE, text); }
        });
        javax.swing.JFrame frame = new javax.swing.JFrame();
        try {
            map.setOccurrences(model.occurrences(DATE));
            schedule.refresh(DATE, map.legs());
            frame.setContentPane(schedule);
            frame.pack();
            frame.setSize(360, 600);
            frame.validate();
            flushSwingUpdates();
            javax.swing.JScrollPane agenda = field(schedule, "agenda");
            JPanel items = field(schedule, "items");
            check(agenda.getVerticalScrollBar().getValue() == 0
                    && agenda.getViewport().getViewRect().contains(items.getComponent(0).getBounds())
                    && agenda.getViewport().getViewRect().contains(items.getComponent(1).getBounds()),
                    "initial agenda shows entire top add button and first event even when tasks overflow viewport");
            JButton firstAdd = (JButton) items.getComponent(0);
            check(firstAdd.getText().equals("+ 일정 추가") && firstAdd.getToolTipText().equals("08:00 – 09:00"),
                    "08:00 first event keeps top add action with valid default times");
            agenda.getVerticalScrollBar().setValue(120);
            int position = agenda.getVerticalScrollBar().getValue();
            check(position > 0, "agenda fixture is actually scrollable");
            schedule.refresh(DATE, map.legs());
            frame.validate();
            flushSwingUpdates();
            check(agenda.getVerticalScrollBar().getValue() == position, "same-date refresh preserves agenda scroll position");
            ((JTextArea) field(schedule, "note")).setText("스크롤을 유지하는 메모");
            javax.swing.Timer clock = field(schedule, "clockTimer");
            for (java.awt.event.ActionListener listener : clock.getActionListeners()) {
                listener.actionPerformed(new java.awt.event.ActionEvent(clock, 0, "tick"));
            }
            check(agenda.getVerticalScrollBar().getValue() == position, "memo edits and elapsed-time color updates preserve agenda position");
            map.setOccurrences(model.occurrences(DATE.plusWeeks(1)));
            schedule.refresh(DATE.plusWeeks(1), map.legs());
            frame.validate();
            flushSwingUpdates();
            check(agenda.getVerticalScrollBar().getValue() == 0
                    && agenda.getViewport().getViewRect().contains(items.getComponent(0).getBounds())
                    && agenda.getViewport().getViewRect().contains(items.getComponent(1).getBounds()),
                    "changing date resets overflowing agenda to full top button and first event");
        } finally {
            frame.dispose();
        }
    }

    private static void flushSwingUpdates() {
        java.awt.SecondaryLoop loop = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        SwingUtilities.invokeLater(loop::exit);
        loop.enter();
    }

    private static void checkOverlapConfirmation(PlaceCatalog places, PlannerModel model, Place pin, Event existing) throws Exception {
        model = new PlannerModel();
        existing = new Event("current-overlap", existing.title(), existing.buildingId(), existing.start(), existing.end(),
                existing.category(), PlannerTime.today().getDayOfWeek(), null, "");
        model.putEvent(existing, Semester.forDate(PlannerTime.today()));
        Constructor<EventDialog> constructor = EventDialog.class.getDeclaredConstructor(javax.swing.JFrame.class,
                PlaceCatalog.class, PlannerModel.class, LocalDate.class, Place.class, Event.class, ScheduleSlot.class,
                Semester.class);
        constructor.setAccessible(true);
        EventDialog dialog = constructor.newInstance(null, places, model, DATE, pin, null,
                new ScheduleSlot(LocalTime.of(14, 0), LocalTime.of(15, 0)), TERM);
        ((JTextField) field(dialog, "title")).setText("시간 중복 검증");
        ((JCheckBox) field(dialog, "recurring")).setSelected(true);
        int originalCount = model.events().size();
        try {
            answerOverlap(dialog, JOptionPane.NO_OPTION, existing.title());
            check(field(dialog, "result") == null && model.events().size() == originalCount,
                    "NO keeps overlapping draft and model unsaved");
            check(((JTextField) field(dialog, "title")).getText().equals("시간 중복 검증"),
                    "declining overlap retains draft fields for correction");
            answerOverlap(dialog, JOptionPane.YES_OPTION, existing.title());
            EventDialog.Result result = field(dialog, "result");
            check(result != null && !result.deleted() && result.event().start().equals(LocalTime.of(14, 0)),
                    "YES returns overlapping schedule for caller to save");
            check(model.events().size() == originalCount, "confirmation does not mutate model before caller accepts result");
        } finally {
            dialog.dispose();
        }
    }

    private static void answerOverlap(EventDialog dialog, int answer, String conflictingTitle) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        boolean[] answered = { false };
        int[] ticks = { 0 };
        javax.swing.Timer timer = new javax.swing.Timer(50, event -> {
            ticks[0]++;
            for (Window window : Window.getWindows()) {
                if (!(window instanceof javax.swing.JDialog modal) || !modal.isVisible()
                        || !modal.getTitle().equals("시간 중복")) continue;
                try {
                    JOptionPane pane = components(modal, JOptionPane.class).get(0);
                    check(pane.getOptionType() == JOptionPane.YES_NO_OPTION, "overlap prompt provides YES and NO");
                    check(String.valueOf(pane.getMessage()).contains(conflictingTitle), "overlap prompt names conflicting event");
                    answered[0] = true;
                    pane.setValue(answer);
                } catch (Throwable exception) {
                    failure.set(exception);
                    modal.dispose();
                }
                ((javax.swing.Timer) event.getSource()).stop();
                return;
            }
            if (ticks[0] >= 60) {
                failure.set(new AssertionError("Timed out waiting for overlap confirmation"));
                for (Window window : Window.getWindows()) {
                    if (window instanceof javax.swing.JDialog modal && modal.isVisible()) modal.dispose();
                }
                ((javax.swing.Timer) event.getSource()).stop();
            }
        });
        timer.start();
        try {
            invoke(dialog, "save");
        } finally {
            timer.stop();
        }
        if (failure.get() != null) throw new AssertionError("Overlap confirmation failed", failure.get());
        check(answered[0], "overlapping schedule opens actual modal confirmation");
    }

    private static void checkRouteDialog(PlaceCatalog places, PlannerModel model, Place building, Place pin) throws Exception {
        Constructor<RouteDialog> constructor = RouteDialog.class.getDeclaredConstructor(javax.swing.JFrame.class,
                PlaceCatalog.class, PlannerModel.class, Place.class);
        constructor.setAccessible(true);
        RouteDialog dialog = constructor.newInstance(null, places, model, building);
        dialog.addNotify();
        dialog.validate();
        for (PlacePicker picker : List.of((PlacePicker) field(dialog, "from"), (PlacePicker) field(dialog, "to"))) {
            JComboBox<?> choices = field(picker, "choices");
            JTextField search = field(picker, "query");
            java.awt.Insets insets = choices.getInsets();
            check(choices.getHeight() >= choices.getFontMetrics(choices.getFont()).getHeight() + insets.top + insets.bottom,
                    "route place selector has full text height after font increase");
            check(picker.getHeight() >= picker.getPreferredSize().height
                    && search.getY() + search.getHeight() <= choices.getY()
                    && choices.getY() + choices.getHeight() <= picker.getHeight(),
                    "route place search and selection remain fully visible without overlap");
        }
        invoke(dialog, "findRoute");
        check(field(dialog, "result") == null && !((JLabel) field(dialog, "error")).getText().isBlank(), "route dialog validates missing destination");
        PlacePicker from = field(dialog, "from");
        PlacePicker to = field(dialog, "to");
        to.select(pin);
        invoke(dialog, "swap");
        check(from.selected().id().equals(pin.id()) && to.selected().id().equals(building.id()), "route dialog swaps pin and building");
        invoke(dialog, "findRoute");
        RouteDialog.Result found = field(dialog, "result");
        check(found.route().reachable() && found.from().pin(), "route dialog searches pin to building");
        RouteDialog same = constructor.newInstance(null, places, model, pin);
        ((PlacePicker) field(same, "to")).select(pin);
        invoke(same, "findRoute");
        RouteDialog.Result zero = field(same, "result");
        check(zero.route().reachable() && zero.route().meters() == 0, "same-place search reports zero distance");
        dialog.dispose();
        same.dispose();
    }

    private static void checkDeadlineDialog(Deadline original) throws Exception {
        Constructor<DeadlineDialog> constructor = DeadlineDialog.class.getDeclaredConstructor(javax.swing.JFrame.class,
                LocalDate.class, Deadline.class);
        constructor.setAccessible(true);
        DeadlineDialog dialog = constructor.newInstance(null, DATE, original);
        ((JTextArea) field(dialog, "note")).setText("제출 후 메모 수정");
        invoke(dialog, "save");
        DeadlineDialog.Result result = field(dialog, "result");
        check(result.deadline().completedOn().equals(original.completedOn()), "editing submitted assignment retains actual completion date");
        check(result.deadline().date().equals(original.date()), "editing existing task preserves its historical date");
        dialog.dispose();
        DeadlineDialog adding = constructor.newInstance(null, PlannerTime.today().minusDays(10), null);
        check(((JTextField) field(adding, "dueDate")).getText().equals(PlannerTime.today().toString()), "new task defaults past selection to today");
        ((JTextField) field(adding, "title")).setText("새 할일");
        ((JTextField) field(adding, "dueDate")).setText(PlannerTime.today().plusDays(7).toString());
        invoke(adding, "save");
        DeadlineDialog.Result added = field(adding, "result");
        check(added.deadline().date().equals(PlannerTime.today().plusDays(7)), "new task keeps chosen future date");
        adding.dispose();
    }

    private static int[] render(JComponent component) {
        component.setSize(680, 560);
        component.doLayout();
        BufferedImage image = new BufferedImage(680, 560, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        component.paint(graphics);
        graphics.dispose();
        return image.getRGB(0, 0, 680, 560, null, 0, 680);
    }

    private static List<JLabel> labels(Component component) { return components(component, JLabel.class); }
    private static List<JButton> buttons(Component component) { return components(component, JButton.class); }

    private static <T extends Component> List<T> components(Component component, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(component)) result.add(type.cast(component));
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) result.addAll(components(child, type));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invoke(Object target, String name) throws Exception {
        invoke(target, name, new Class<?>[0]);
    }

    private static void invoke(Object target, String name, Class<?>[] parameters, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        method.invoke(target, args);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
